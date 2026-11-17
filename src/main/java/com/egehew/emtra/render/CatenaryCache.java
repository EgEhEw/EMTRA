package com.egehew.emtra.render;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.ClientWorld;
import org.mtr.mapping.holder.Direction;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.holder.LightType;
import org.mtr.mapping.holder.LightmapTextureManager;
import org.mtr.mapping.holder.MinecraftClient;
import org.mtr.mapping.holder.Vector3d;
import org.mtr.mapping.holder.Vector3f;
import org.mtr.mapping.holder.Vector3i;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mapping.mapper.OptimizedRenderer;
import org.mtr.mapping.render.batch.MaterialProperties;
import org.mtr.mapping.render.model.Face;
import org.mtr.mapping.render.model.RawMesh;
import org.mtr.mapping.render.model.RawModel;
import org.mtr.mapping.render.object.VertexArray;
import org.mtr.mapping.render.tool.GlStateTracker;
import org.mtr.mapping.render.vertex.Vertex;
import org.mtr.mapping.render.vertex.VertexAttributeMapping;
import org.mtr.mod.client.CustomResourceLoader;
import org.mtr.mod.render.MainRenderer;
import org.mtr.mod.render.QueuedRenderLayer;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * The rail cache for MSD (MTR Station Decoration) rigid catenary. MSD draws every segment of every rigid catenary as
 * seven textured quads each frame, writing them vertex by vertex (18-28% of a frame at a station). When a catenary is
 * first seen, MSD draws it normally and the quads are captured; they are then uploaded once as MTR optimized models
 * (one per light value, since MTR models take their light per draw) and drawn like rails from then on. MTR's shader
 * handles fog, diffuse lighting and shader packs. Light is re-read when its section changes (LightEvents) and the
 * catenary is recaptured every few seconds for edits. Render thread only: MSD's math is also called from other
 * threads (e.g. JCM scripts reading the geometry) and those calls pass through untouched.
 */
public final class CatenaryCache {

	private static final long RECAPTURE_NANOS = 5_000_000_000L;
	private static final long FORGET_NANOS = 10_000_000_000L;
	private static final long BAN_NANOS = 30_000_000_000L;
	/** Fallback poll; light changes normally arrive through LightEvents within a frame. */
	private static final long LIGHT_CHECK_NANOS = 2_000_000_000L;
	/** A rise in sky light is accepted only once it stayed this long (missing light data briefly reads as 15). */
	private static final long SKY_RAISE_NANOS = 3_000_000_000L;

	private static final class Capture {
		final long started = System.nanoTime();
		/** x, y, z ×4 in world coordinates, then u1, v1, u2, v2, nx, ny, nz. */
		final List<double[]> quads = new ArrayList<>();
		/** color, MSD's light, light position index. */
		final List<int[]> attributes = new ArrayList<>();
		final List<int[]> lightPositions = new ArrayList<>();
		final Map<Long, Integer> lightIndex = new HashMap<>();
		Identifier texture;
		int scheduled, executed;
		boolean failed;
	}

	private static final class Entry {
		final List<OptimizedModel> models = new ArrayList<>();
		final List<int[]> modelAttributes = new ArrayList<>(); // color, light per model
		Capture pending, built;
		int[] lights;
		long[] lightSections = new long[0];
		long lightEpoch;
		double originX, originY, originZ;
		long nextCapture, lastSeen, bannedUntil, lightChecked;
		final Map<Long, long[]> skyState = new HashMap<>();

		boolean ready() {
			return !models.isEmpty();
		}

		void release() {
			if (!models.isEmpty()) {
				final boolean protect = !GlStateTracker_isCaptured();
				if (protect) {
					GlStateTracker.capture();
				}
				try {
					for (final OptimizedModel model : models) {
						try {
							model.close();
						} catch (Throwable ignored) {
						}
					}
				} finally {
					if (protect) {
						GlStateTracker.restore();
					}
				}
			}
			models.clear();
			modelAttributes.clear();
		}

		boolean lightChanged() {
			final long current = LightEvents.epoch();
			if (current == lightEpoch) {
				return false;
			}
			for (final long section : lightSections) {
				if (LightEvents.changedSince(section, lightEpoch)) {
					return true;
				}
			}
			lightEpoch = current;
			return false;
		}
	}

	private static final Map<Object, Entry> CACHE = new IdentityHashMap<>();
	private static Capture scheduling, executing;
	private static Matrix4f executingPose;
	private static double executingOffsetX, executingOffsetY, executingOffsetZ;
	private static boolean segmentKnown, executingKnown, wrapping, broken;
	private static int segmentX, segmentY, segmentZ, executingX, executingY, executingZ;
	private static long lastSweep = System.nanoTime(), lastLog = System.nanoTime();
	private static long statCached, statCaptured;
	private static VertexAttributeMapping mapping;
	private static OptimizedRenderer renderer;
	private static final Matrix4f SAVED = new Matrix4f();

	// ---- MSD RigidCatenaryMath.render(callback) ----

	/** @return true to cancel MSD's drawing this frame (drawn from the cache instead) */
	public static boolean beforeRender(Object math) {
		scheduling = null;
		segmentKnown = false;
		if (!EmtraClientConfig.RENDER_CATENARY_CACHE || broken || !RenderSystem.isOnRenderThread() || !OptimizedRenderer.hasOptimizedRendering()) {
			return false;
		}
		final long now = System.nanoTime();
		sweep(now);
		Entry entry = CACHE.get(math);
		if (entry == null) {
			entry = new Entry();
			CACHE.put(math, entry);
		}
		entry.lastSeen = now;
		if (entry.bannedUntil > now) {
			return false;
		}
		final Capture pending = entry.pending;
		if (pending != null && pending.executed < pending.scheduled && now - pending.started > 2_000_000_000L) {
			entry.pending = null; // the scheduled draws never ran; try again later
		} else if (pending != null && pending.executed >= pending.scheduled) {
			entry.pending = null;
			if (pending.failed || pending.scheduled == 0 || pending.quads.isEmpty() || !build(entry, pending, now)) {
				entry.release();
				entry.bannedUntil = now + BAN_NANOS;
				return false;
			}
			entry.nextCapture = now + RECAPTURE_NANOS + (System.identityHashCode(math) & 0x7FF) * 1_000_000L;
			entry.lightChecked = now;
		} else if (entry.ready() && entry.built != null && (now - entry.lightChecked > LIGHT_CHECK_NANOS || entry.lightChanged())) {
			entry.lightChecked = now + (System.identityHashCode(math) & 0xFF) * 1_000_000L;
			entry.lightEpoch = LightEvents.current();
			if (!Arrays.equals(filteredLights(entry, entry.built, now), entry.lights) && !build(entry, entry.built, now)) {
				entry.release();
				entry.bannedUntil = now + BAN_NANOS;
				return false;
			}
		}
		if (entry.ready() && now < entry.nextCapture) {
			final Entry drawn = entry;
			wrapping = true;
			try {
				MainRenderer.scheduleRender(QueuedRenderLayer.TEXT, (graphicsHolder, offset) -> draw(drawn, graphicsHolder, offset));
			} finally {
				wrapping = false;
			}
			statCached++;
			return true;
		}
		if (entry.pending == null) {
			entry.pending = new Capture();
			scheduling = entry.pending;
			statCaptured++;
		}
		return false;
	}

	public static void afterRender() {
		if (RenderSystem.isOnRenderThread()) {
			scheduling = null;
			segmentKnown = false;
		}
	}

	/** MTR Init.newBlockPos(x, y, z) HEAD: MSD computes each segment's light at this position right before scheduling it. */
	public static void onBlockPos(double x, double y, double z) {
		if (scheduling != null && RenderSystem.isOnRenderThread()) {
			segmentX = (int) Math.floor(x);
			segmentY = (int) Math.floor(y);
			segmentZ = (int) Math.floor(z);
			segmentKnown = true;
		}
	}

	// ---- MTR MainRenderer.scheduleRender(texture, translucent, layer, consumer) ----

	/** @return the consumer to schedule instead, or null */
	public static BiConsumer<GraphicsHolder, Vector3d> wrap(Identifier texture, boolean translucent, QueuedRenderLayer layer, BiConsumer<GraphicsHolder, Vector3d> consumer) {
		final Capture capture = scheduling;
		if (capture == null || wrapping || !RenderSystem.isOnRenderThread()) {
			return null;
		}
		capture.scheduled++;
		if (translucent || layer != QueuedRenderLayer.EXTERIOR || (capture.texture != null && !capture.texture.equals(texture))) {
			capture.failed = true;
		}
		capture.texture = texture;
		final boolean known = segmentKnown;
		final int sx = segmentX, sy = segmentY, sz = segmentZ;
		segmentKnown = false;
		return (graphicsHolder, offset) -> {
			final Matrix4f pose = PoseAccess.pose(graphicsHolder);
			if (pose == null) {
				capture.failed = true;
			}
			executing = capture;
			executingPose = pose == null ? null : new Matrix4f(pose);
			executingOffsetX = offset.getXMapped();
			executingOffsetY = offset.getYMapped();
			executingOffsetZ = offset.getZMapped();
			executingKnown = known;
			executingX = sx;
			executingY = sy;
			executingZ = sz;
			try {
				consumer.accept(graphicsHolder, offset);
			} finally {
				executing = null;
				executingPose = null;
				capture.executed++;
			}
		};
	}

	public static boolean isWrapping() {
		return wrapping;
	}

	public static void setWrapping(boolean value) {
		wrapping = value;
	}

	// ---- MTR GraphicsHolder.drawTextureInWorld ----

	public static void onQuad(GraphicsHolder graphicsHolder, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3,
							  float x4, float y4, float z4, float u1, float v1, float u2, float v2, Direction facing, int color, int light) {
		final Capture capture = executing;
		if (capture == null || capture.failed || !RenderSystem.isOnRenderThread()) {
			return;
		}
		// MSD draws the quad directly in the scheduled closure: the pose must still be the closure's (camera rotation).
		final Matrix4f pose = PoseAccess.pose(graphicsHolder);
		if (pose == null || executingPose == null || !pose.equals(executingPose)) {
			capture.failed = true;
			return;
		}
		final Vector3i normal = facing.getVector();
		final double ox = executingOffsetX, oy = executingOffsetY, oz = executingOffsetZ;
		capture.quads.add(new double[]{x1 + ox, y1 + oy, z1 + oz, x2 + ox, y2 + oy, z2 + oz, x3 + ox, y3 + oy, z3 + oz, x4 + ox, y4 + oy, z4 + oz,
				u1, v1, u2, v2, normal.getX(), normal.getY(), normal.getZ()});
		int index = -1;
		if (executingKnown) {
			final int px = executingX, py = executingY, pz = executingZ;
			index = capture.lightIndex.computeIfAbsent(positionKey(px, py, pz), key -> {
				capture.lightPositions.add(new int[]{px, py, pz});
				return capture.lightPositions.size() - 1;
			});
		}
		capture.attributes.add(new int[]{color, light, index});
	}

	// ---- models ----

	private static boolean build(Entry entry, Capture capture, long now) {
		try {
			if (mapping == null) {
				final Field field = OptimizedModel.class.getDeclaredField("DEFAULT_MAPPING");
				field.setAccessible(true);
				mapping = (VertexAttributeMapping) field.get(null);
			}
			final int[] lights = filteredLights(entry, capture, now);
			final double[] first = capture.quads.get(0);
			final double originX = Math.floor(first[0]), originY = Math.floor(first[1]), originZ = Math.floor(first[2]);
			// One mesh per (color, light): MTR models take color and light per draw.
			final Map<Long, RawMesh> meshes = new LinkedHashMap<>();
			for (int i = 0; i < capture.quads.size(); i++) {
				final double[] q = capture.quads.get(i);
				final int[] a = capture.attributes.get(i);
				final int light = a[2] >= 0 && lights[a[2]] != Integer.MIN_VALUE ? lights[a[2]] : a[1];
				final int color = a[0];
				final RawMesh mesh = meshes.computeIfAbsent(((long) color << 32) | (light & 0xFFFFFFFFL),
						key -> new RawMesh(new MaterialProperties(OptimizedModel.ShaderType.CUTOUT, capture.texture, null)));
				final float nx = (float) q[16], ny = (float) q[17], nz = (float) q[18];
				final int base = mesh.vertices.size();
				// Same corner/UV order as MTR GraphicsHolder.drawTextureInWorld. vertices.add, not addVertex: addVertex also
				// appends an OBJ-style quad face every four vertices, which upload() cannot index (it expects triangles).
				mesh.vertices.add(vertex(q[0] - originX, q[1] - originY, q[2] - originZ, (float) q[12], (float) q[15], nx, ny, nz));
				mesh.vertices.add(vertex(q[3] - originX, q[4] - originY, q[5] - originZ, (float) q[14], (float) q[15], nx, ny, nz));
				mesh.vertices.add(vertex(q[6] - originX, q[7] - originY, q[8] - originZ, (float) q[14], (float) q[13], nx, ny, nz));
				mesh.vertices.add(vertex(q[9] - originX, q[10] - originY, q[11] - originZ, (float) q[12], (float) q[13], nx, ny, nz));
				mesh.faces.add(new Face(new int[]{base, base + 1, base + 2}));
				mesh.faces.add(new Face(new int[]{base, base + 2, base + 3}));
			}
			final List<OptimizedModel> models = new ArrayList<>();
			final List<int[]> attributes = new ArrayList<>();
			// MTR requires GL work on its models inside a GlStateTracker capture (it restores the GL state after).
			GlStateTracker.capture();
			try {
				for (final Map.Entry<Long, RawMesh> mesh : meshes.entrySet()) {
					final RawModel rawModel = new RawModel();
					rawModel.append(mesh.getValue());
					final List<VertexArray> parts = rawModel.upload(mapping);
					models.add(new OptimizedModel(parts));
					attributes.add(new int[]{(int) (mesh.getKey() >> 32), (int) (long) mesh.getKey()});
				}
				entry.release();
			} finally {
				GlStateTracker.restore();
			}
			entry.models.addAll(models);
			entry.modelAttributes.addAll(attributes);
			entry.built = capture;
			entry.lights = lights;
			entry.originX = originX;
			entry.originY = originY;
			entry.originZ = originZ;
			final Set<Long> sections = new HashSet<>();
			for (final int[] p : capture.lightPositions) {
				sections.add(LightEvents.blockSectionKey(p[0], p[1], p[2]));
			}
			entry.lightSections = sections.stream().mapToLong(Long::longValue).toArray();
			entry.lightEpoch = LightEvents.current();
			return true;
		} catch (Throwable t) {
			disable(t);
			return false;
		}
	}

	private static Vertex vertex(double x, double y, double z, float u, float v, float nx, float ny, float nz) {
		final Vertex vertex = new Vertex();
		vertex.position = new Vector3f((float) x, (float) y, (float) z);
		vertex.normal = new Vector3f(nx, ny, nz);
		vertex.u = u;
		vertex.v = v;
		return vertex;
	}

	private static void draw(Entry entry, GraphicsHolder graphicsHolder, Vector3d offset) {
		if (broken || !entry.ready()) {
			return;
		}
		final Matrix4f pose = PoseAccess.pose(graphicsHolder);
		if (pose == null) {
			return;
		}
		try {
			if (renderer == null) {
				final Field field = CustomResourceLoader.OPTIMIZED_RENDERER_WRAPPER.getClass().getDeclaredField("optimizedRenderer");
				field.setAccessible(true);
				renderer = (OptimizedRenderer) field.get(CustomResourceLoader.OPTIMIZED_RENDERER_WRAPPER);
			}
			SAVED.set(pose);
			pose.translate((float) (entry.originX - offset.getXMapped()), (float) (entry.originY - offset.getYMapped()), (float) (entry.originZ - offset.getZMapped()));
			for (int i = 0; i < entry.models.size(); i++) {
				final int[] a = entry.modelAttributes.get(i);
				renderer.queue(entry.models.get(i), graphicsHolder, a[0], a[1]);
			}
		} catch (Throwable t) {
			disable(t);
		} finally {
			pose.set(SAVED);
		}
	}

	// ---- light ----

	/**
	 * MSD's per-frame light read at each segment position, filtered: while a section's light data is not ready the
	 * client briefly reads sky light 15. MSD re-reads every frame so that shows for one frame; baked into a model it
	 * would stay. Drops in sky light apply at once, rises only after staying for SKY_RAISE_NANOS. MSD's own value at
	 * capture counts as a sample. Block light is used as read.
	 */
	private static int[] filteredLights(Entry entry, Capture capture, long now) {
		final int[] raw = readLights(capture);
		final int[] captured = new int[raw.length];
		Arrays.fill(captured, Integer.MIN_VALUE);
		for (final int[] a : capture.attributes) {
			if (a[2] >= 0 && captured[a[2]] == Integer.MIN_VALUE) {
				captured[a[2]] = a[1];
			}
		}
		final int[] result = new int[raw.length];
		for (int i = 0; i < raw.length; i++) {
			final int value = raw[i] != Integer.MIN_VALUE ? raw[i] : captured[i];
			if (value == Integer.MIN_VALUE) {
				result[i] = Integer.MIN_VALUE;
				continue;
			}
			final int block = (value >> 4) & 0xF, sky = (value >> 20) & 0xF;
			final int[] p = capture.lightPositions.get(i);
			final long key = positionKey(p[0], p[1], p[2]);
			long[] state = entry.skyState.get(key);
			if (state == null) {
				int initial = sky;
				if (captured[i] != Integer.MIN_VALUE) {
					initial = Math.min(initial, (captured[i] >> 20) & 0xF);
				}
				state = new long[]{initial, -1, 0};
				entry.skyState.put(key, state);
			} else if (sky <= state[0]) {
				state[0] = sky;
				state[1] = -1;
			} else if (state[1] != sky) {
				state[1] = sky;
				state[2] = now;
			} else if (now - state[2] >= SKY_RAISE_NANOS) {
				state[0] = sky;
				state[1] = -1;
			}
			result[i] = (block << 4) | ((int) state[0] << 20);
		}
		return result;
	}

	private static int[] readLights(Capture capture) {
		final int[] lights = new int[capture.lightPositions.size()];
		Arrays.fill(lights, Integer.MIN_VALUE);
		try {
			final ClientWorld world = MinecraftClient.getInstance().getWorldMapped();
			if (world == null) {
				return lights;
			}
			for (int i = 0; i < lights.length; i++) {
				final int[] p = capture.lightPositions.get(i);
				final BlockPos pos = new BlockPos(p[0], p[1], p[2]);
				lights[i] = LightmapTextureManager.pack(world.getLightLevel(LightType.getBlockMapped(), pos), world.getLightLevel(LightType.getSkyMapped(), pos));
			}
		} catch (Throwable ignored) {
		}
		return lights;
	}

	private static long positionKey(int x, int y, int z) {
		return ((long) x & 0x3FFFFFFL) << 38 | ((long) y & 0xFFFL) << 26 | ((long) z & 0x3FFFFFFL);
	}

	// ---- housekeeping ----

	private static void sweep(long now) {
		if (now - lastSweep < 2_000_000_000L) {
			return;
		}
		lastSweep = now;
		final Iterator<Entry> iterator = CACHE.values().iterator();
		while (iterator.hasNext()) {
			final Entry entry = iterator.next();
			if (now - entry.lastSeen > FORGET_NANOS) {
				entry.release();
				iterator.remove();
			}
		}
		if (EmtraClientConfig.RENDER_STATS && now - lastLog > 10_000_000_000L) {
			lastLog = now;
			System.out.println("[EMTRA] Catenary cache (10 s): served=" + statCached + ", captured=" + statCaptured + ", catenaries=" + CACHE.size());
			statCached = statCaptured = 0;
		}
	}

	private static void disable(Throwable t) {
		if (!broken) {
			broken = true;
			System.err.println("[EMTRA] Catenary cache disabled after an error: " + t);
			t.printStackTrace();
			for (final Entry entry : CACHE.values()) {
				entry.release();
			}
			CACHE.clear();
		}
	}

	/** GlStateTracker.assertProtected() throws when no capture is active. */
	private static boolean GlStateTracker_isCaptured() {
		try {
			GlStateTracker.assertProtected();
			return true;
		} catch (IllegalStateException e) {
			return false;
		}
	}

	private CatenaryCache() {
	}
}
