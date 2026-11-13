package org.mtr.altplatforms.compat;

import org.mtr.altplatforms.AltPlatformsMod;
import org.mtr.core.data.Platform;
import org.mtr.core.data.PathData;
import org.mtr.core.data.Route;
import org.mtr.core.data.Vehicle;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

/** Optional bridge: Magic keeps its timetable, EMTRA owns platform routing. */
public final class MagicMtrCompat {

	private static Method candidatePlatforms;
	private static Method alignTimetable;
	private static boolean initialized;

	private MagicMtrCompat() {
	}

	public static List<Long> getPrimaryPlatformIds(Route route, Platform primary) {
		return primary == null ? Collections.emptyList() : Collections.singletonList(primary.getId());
	}

	public static synchronized boolean isPresent() {
		if (!initialized) {
			initialized = true;
			try {
				final Class<?> registry = Class.forName("org.justnoone.jme.rail.AlternativePlatformRegistry");
				candidatePlatforms = registry.getMethod("getCandidatePlatformIds", Route.class, Platform.class);
				alignTimetable = Class.forName("org.justnoone.jme.rail.TimetableAligned").getMethod("jme$alignTimetable", List.class, List.class, int.class);
				AltPlatformsMod.LOGGER.info("MAGIC MTR detected: EMTRA now handles alternate platforms, including skipped stops");
			} catch (ClassNotFoundException ignored) {
				// Magic is optional on both loaders (including Fabric through Connector).
			} catch (ReflectiveOperationException | LinkageError exception) {
				AltPlatformsMod.LOGGER.warn("Could not read MAGIC MTR alternate platform configuration", exception);
			}
		}
		return candidatePlatforms != null;
	}

	public static void alignTimetable(Vehicle vehicle, List<PathData> oldPath, List<PathData> newPath, int divergenceIndex) {
		if (isPresent() && alignTimetable != null) {
			try {
				alignTimetable.invoke(vehicle, oldPath, newPath, divergenceIndex);
			} catch (ReflectiveOperationException exception) {
				AltPlatformsMod.LOGGER.warn("Could not align MAGIC MTR timetable after diversion of vehicle {}", vehicle.getId(), exception);
			}
		}
	}

	@SuppressWarnings("unchecked")
	public static List<Long> getAlternatePlatformIds(Route route, Platform primary) {
		if (!isPresent()) {
			return Collections.emptyList();
		}
		try {
			return (List<Long>) candidatePlatforms.invoke(null, route, primary);
		} catch (ReflectiveOperationException | LinkageError exception) {
			AltPlatformsMod.LOGGER.warn("Could not read MAGIC MTR alternate platforms for route {}", route.getId(), exception);
			return Collections.emptyList();
		}
	}
}
