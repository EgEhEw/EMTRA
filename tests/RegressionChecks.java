import org.mtr.altplatforms.compat.AltPlatformsMixinPlugin;
import org.mtr.altplatforms.compat.MagicMtrCompat;
import org.mtr.altplatforms.data.ArrivalRouting;
import org.mtr.altplatforms.data.RoutingPathData;
import org.mtr.altplatforms.duck.IVehicle;
import org.mtr.core.data.*;
import org.mtr.core.serializer.JsonReader;
import org.mtr.core.serializer.JsonWriter;
import org.mtr.core.tool.Angle;
import org.mtr.libraries.com.google.gson.JsonArray;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;

public final class RegressionChecks {
    private static int checks;
    private static final Data DATA = new Data() {};
    private static final Platform PRIMARY = platform(10);
    private static final Platform ALTERNATE = platform(20);
    private static final Platform NEXT = platform(30);
    private static final Siding SIDING = new Siding(new Position(0, 0, 0), new Position(10, 0, 0), 10, TransportMode.TRAIN, DATA);

    public static void main(String[] args) throws Exception {
        check(!MagicMtrCompat.isPresent(), "Magic must remain optional");
        testArrivals();
        testRouting();
        testMixinTargets();
        if (args.length > 0) testMagicBytecode(Path.of(args[0]));
        System.out.println("PASS: " + checks + " regression checks");
    }

    private static Platform platform(long id) {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        Platform platform = new Platform(new JsonReader(json), DATA);
        DATA.platformIdMap.put(platform.getId(), platform);
        return platform;
    }

    private static PathData path(long platform, long dwell, int stop, double start) {
        return new PathData(null, platform, dwell, stop, start, start + 10,
                new Position(10, 0, 0), Angle.W, new Position(0, 0, 0), Angle.E);
    }

    private static void addStop(Route route, Platform platform) {
        RoutePlatformData stop = new RoutePlatformData(platform.getId());
        stop.platform = platform;
        route.getRoutePlatforms().add(stop);
    }

    private static void testArrivals() {
        Depot depot = new Depot(TransportMode.TRAIN, DATA);
        Route route = new Route(TransportMode.TRAIN, DATA);
        addStop(route, PRIMARY); addStop(route, NEXT); addStop(route, PRIMARY);
        depot.routes.add(route);
        Trip trip = new Trip(route, 0, 0, SIDING);
        Trip.StopTime first = trip.addStopTime(100, 110, PRIMARY.getId(), 0, "");
        Trip.StopTime repeated = trip.addStopTime(300, 310, PRIMARY.getId(), 2, "");
        List<Vehicle> vehicles = List.of(new TestVehicle(1, path(PRIMARY.getId(), 1000, 0, 100)),
                new TestVehicle(2, path(ALTERNATE.getId(), 1000, 0, 100)));
        check(ArrivalRouting.getTargetPlatform(DATA, depot, vehicles, first, false, 2) == PRIMARY.getId(),
                "Scheduled arrival stays at its primary despite reverse alternative paths");
        check(ArrivalRouting.getTargetPlatform(DATA, depot, vehicles, first, true, 2) == ALTERNATE.getId(),
                "Diverted realtime arrival belongs at the alternate");
        check(ArrivalRouting.getTargetPlatform(DATA, depot, vehicles, first, true, 1) == PRIMARY.getId(),
                "Nondiverted realtime arrival stays at the primary");
        check(ArrivalRouting.getTargetPlatform(DATA, depot, vehicles, repeated, true, 2) == PRIMARY.getId(),
                "A diversion at the first visit must not move a later visit to that station");
        check(ArrivalRouting.getTargetPlatform(DATA, depot, List.of(), first, false, 1) != ALTERNATE.getId(),
                "Unstarted timetable train must not appear at an alternate");

        // Exercise MTR's actual candidate consumer and count logic, with the production
        // filter applied before invoking it (the same position as our cancellable HEAD).
        try {
            Siding siding = new Siding(new Position(0, 0, 0), new Position(10, 0, 0), 10, TransportMode.TRAIN, DATA);
            var lambda = Arrays.stream(Siding.class.getDeclaredMethods()).filter(m -> m.getName().equals("lambda$getArrivals$13")).findFirst().orElseThrow();
            lambda.setAccessible(true);
            ObjectArrayList<org.mtr.core.operation.ArrivalResponse> shared = new ObjectArrayList<>();
            for (int call = 0; call < 2; call++) {
                ObjectArrayList<org.mtr.core.operation.ArrivalResponse> candidates = new ObjectArrayList<>();
                long[] counter = new long[2];
                for (int departureIndex : new int[]{1, 2}) {
                    if (ArrivalRouting.getTargetPlatform(DATA, depot, vehicles, first, true, departureIndex) == ALTERNATE.getId()) {
                        lambda.invoke(siding, counter, 1L, ALTERNATE, candidates, trip, 0, first,
                                (long) departureIndex * 100, (long) departureIndex * 100 + 10, true, 0L, departureIndex, 0L);
                    }
                }
                check(candidates.size() == 1 && candidates.get(0).getDepartureIndex() == 2,
                        "count=1 evaluates the later diverted candidate after rejecting the first");
                shared.addAll(candidates);
            }
            check(shared.size() == 2, "Shared accumulator retains both sidings even with overlapping departure indices and count=1");
        } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }

        Route second = new Route(TransportMode.TRAIN, DATA);
        addStop(second, PRIMARY); addStop(second, ALTERNATE);
        depot.routes.add(second);
        Trip trip2 = new Trip(second, 1, 1, SIDING);
        check(ArrivalRouting.getStopIndex(depot, trip2.addStopTime(400, 410, ALTERNATE.getId(), 1, "")) == 3,
                "Shared terminus is counted once across consecutive routes");
    }

    private static void testRouting() {
        List<PathData> detour = List.of(path(0, 0, 0, 0), path(0, 1, 0, 10),
                path(0, 0, 0, 20), path(ALTERNATE.getId(), 1000, 1, 30), path(0, 0, 1, 40));
        int platformIndex = RoutingPathData.findPlatformIndex(detour, 0, ALTERNATE.getId());
        check(platformIndex == 3 && RoutingPathData.findPlatformEnd(detour, platformIndex) == 3,
                "Occupancy scan extends beyond a turnback to the actual alternate platform");
        check(RoutingPathData.findPlatformIndex(detour, 0, 999) == -1, "Incomplete detours without the target are rejected");
        List<PathData> terminal = List.of(path(PRIMARY.getId(), 1000, 1, 0), path(0, 0, 1, 10), path(99, 1, 2, 20));
        check(!RoutingPathData.hasPlatformAfter(terminal, 0, DATA), "Return siding is not a subsequent passenger stop");
        check(RoutingPathData.hasPlatformAfter(List.of(terminal.get(0), path(NEXT.getId(), 0, 2, 10)), 0, DATA),
                "A skipped passenger platform still counts as a subsequent route stop");
        for (long dwell : new long[]{0, 1, 12500}) {
            PathData copied = RoutingPathData.copyForService(detour.get(3), 50, 60,
                    List.of(path(PRIMARY.getId(), dwell, 1, 0)), DATA);
            check(copied.getDwellTime() == dwell && copied.getSavedRailBaseId() == ALTERNATE.getId()
                    && copied.getStopIndex() == 1 && copied.reversePositions && copied.getStartDistance() == 50,
                    "Diversion preserves skip/stop pattern and direction, dwell=" + dwell);
            check(detour.get(3).getDwellTime() == 1000, "Copy must not mutate cached detours");
        }
        PathData turnback = RoutingPathData.copyForService(detour.get(1), 10, 20,
                List.of(path(PRIMARY.getId(), 0, 0, 0)), DATA);
        check(turnback.getDwellTime() == 1, "Turnback dwell is preserved for skipped services");
    }

    private static void testMixinTargets() throws Exception {
        ClassNode core = readClass(Siding.class);
        ClassNode mixin = readClass(org.mtr.altplatforms.mixin.SidingMixin.class);
        MethodNode handler = mixin.methods.stream().filter(m -> m.name.equals("altplatforms$filterArrival")).findFirst().orElseThrow();
        String targetDescriptor = handler.desc.replace("Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;", "");
        check(core.methods.stream().anyMatch(m -> m.name.equals("lambda$getArrivals$13") && m.desc.equals(targetDescriptor)),
                "Cancellable arrival filter descriptor matches MTR 4.0.5's candidate consumer");
        ClassNode vehicle = readClass(org.mtr.altplatforms.mixin.VehicleMixin.class);
        check(vehicle.methods.stream().noneMatch(m -> m.name.contains("RailBlockedDistance")),
                "Vehicle mixin cannot override MTR signal reservations");
        AltPlatformsMixinPlugin plugin = new AltPlatformsMixinPlugin();
        check(plugin.shouldApplyMixin("", "org.mtr.altplatforms.mixin.DashboardScreenMixin"), "EMTRA menu remains without Magic");
        check(!plugin.shouldApplyMixin("", "org.mtr.altplatforms.mixin.ArrivalsRequestCompatMixin"), "No Magic request patch without Magic");
    }

    private static ClassNode readClass(Class<?> type) throws Exception {
        try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            ClassNode node = new ClassNode(); new ClassReader(stream).accept(node, 0); return node;
        }
    }

    private static byte[] writeClass(ClassNode node) {
        ClassWriter writer = new ClassWriter(0); node.accept(writer); return writer.toByteArray();
    }

    private static void testMagicBytecode(Path magicJar) throws Exception {
        AltPlatformsMixinPlugin plugin = new AltPlatformsMixinPlugin();
        Field present = AltPlatformsMixinPlugin.class.getDeclaredField("magicPresent"); present.setAccessible(true); present.set(plugin, true);
        check(!plugin.shouldApplyMixin("", "org.mtr.altplatforms.mixin.DashboardScreenMixin"), "EMTRA menu is hidden when Magic supplies the UI");
        int disabled = 0, redirected = 0;
        try (JarFile jar = new JarFile(magicJar.toFile())) {
            for (String name : List.of("VehicleAlternativePlatformRerouteMixin", "DepotAlternativePlatformMixin", "SidingDivertedArrivalsMixin",
                    "ArrivalsRequestAlternativePlatformMixin", "SidingDynamicPlatformRerouteMixin", "DashboardScreenAlternativePlatformMixin",
                    "SidingFlexibleTimetableMixin", "VehicleExtraDataAlternativePlatformMixin", "VehicleMinimumDwellMixin")) {
                String owner = "org.justnoone.jme.mixin." + name;
                ClassNode node = new ClassNode();
                try (var stream = jar.getInputStream(jar.getJarEntry(owner.replace('.', '/') + ".class"))) {
                    new ClassReader(stream).accept(node, 0);
                }
                // Emulate Mixin's owner metadata and generated handler naming, using the actual installed Magic bytecode.
                for (MethodNode method : node.methods) {
                    if (method.name.startsWith("<")) continue;
                    AnnotationNode annotation = new AnnotationNode("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;");
                    annotation.values = new ArrayList<>(List.of("mixin", owner));
                    if (method.visibleAnnotations == null) method.visibleAnnotations = new ArrayList<>();
                    method.visibleAnnotations.add(annotation);
                    if (method.name.startsWith("jme$")) method.name = "handler$test$" + method.name;
                }
                byte[] before = writeClass(node);
                plugin.postApply("test", node, "test", null);
                boolean unchanged = Arrays.equals(before, writeClass(node));
                if (name.contains("Dashboard") || name.contains("Timetable") || name.contains("ExtraData") || name.contains("MinimumDwell")) {
                    check(unchanged, "Magic UI, flexible timetable and unrelated behavior stay intact: " + name);
                }
                for (MethodNode method : node.methods) {
                    if (method.name.startsWith("handler$") && method.instructions.size() == 1 && method.instructions.getFirst().getOpcode() == Opcodes.RETURN) {
                        disabled++;
                        new Analyzer<>(new BasicVerifier()).analyze(node.name, method);
                    }
                    for (AbstractInsnNode instruction : method.instructions) {
                        if (instruction instanceof MethodInsnNode call && call.owner.equals("org/mtr/altplatforms/compat/MagicMtrCompat")) {
                            redirected++;
                            check(call.name.equals("getPrimaryPlatformIds"), "Magic siding redirect retains normal startup with a singleton platform");
                            new Analyzer<>(new BasicVerifier()).analyze(node.name, method);
                        }
                    }
                }
            }
        }
        check(disabled == 7, "All seven competing Magic callbacks are disabled (found " + disabled + ")");
        check(redirected == 1, "Magic first-platform selector is replaced exactly once");
    }

    private static void check(boolean ok, String description) {
        checks++;
        if (!ok) throw new AssertionError(description);
    }

    private static final class TestVehicle extends Vehicle implements IVehicle {
        private final int departure;
        TestVehicle(int departure, PathData path) {
            super(extra(path), SIDING, TransportMode.TRAIN, DATA);
            this.departure = departure;
        }
        private static VehicleExtraData extra(PathData path) {
            JsonObject json = new JsonObject(), pd = new JsonObject();
            path.serializeData(new JsonWriter(pd));
            JsonArray paths = new JsonArray(); paths.add(pd); json.add("path", paths);
            return new VehicleExtraData(new JsonReader(json));
        }
        @Override public boolean getIsOnRoute() { return true; }
        @Override public long getDepartureIndex() { return departure; }
        @Override public double altplatforms$getRailProgress() { return 0; }
        @Override public double altplatforms$getSpeed() { return 0; }
        @Override public long altplatforms$getElapsedDwellTime() { return 0; }
        @Override public long altplatforms$getUpcomingPlatformId() { return 0; }
        @Override public double altplatforms$getDistanceToUpcomingPlatform() { return 0; }
    }
}
