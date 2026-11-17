package com.egehew.emtra.render;

import java.util.HashMap;
import java.util.Map;

/**
 * MTR builds a brand new lift model (dozens of model parts and cuboids) for every lift on every frame, and fangsu
 * replaces that constructor call with its own model class. The model only depends on the lift's size and whether it is
 * double sided; everything else (doors, lights, textures) is passed to render() on each call. Lifts are rendered on the
 * render thread only, so one model per size is reused.
 */
public final class LiftModelCache {

	public static final boolean ENABLED = !"false".equals(System.getProperty("emtra.liftModelCache"));
	private static final int MAX_MODELS = 64;
	private static final Map<Long, Object> MODELS = new HashMap<>();
	private static long statBuilt, statReused, lastLog;

	private LiftModelCache() {
	}

	private static long key(int height, int width, int depth, boolean doubleSided) {
		return ((long) (height & 0xFFFF) << 33) | ((long) (width & 0xFFFF) << 17) | ((long) (depth & 0xFFFF) << 1) | (doubleSided ? 1 : 0);
	}

	/** Returns the cached model, or null when it has to be built (then {@link #put} is called with the new model). */
	public static Object get(int height, int width, int depth, boolean doubleSided) {
		if (!ENABLED) {
			return null;
		}
		final Object model = MODELS.get(key(height, width, depth, doubleSided));
		if (model != null) {
			statReused++;
			logStats();
		}
		return model;
	}

	public static void put(int height, int width, int depth, boolean doubleSided, Object model) {
		if (!ENABLED || model == null) {
			return;
		}
		if (MODELS.size() >= MAX_MODELS) {
			MODELS.clear();
		}
		MODELS.put(key(height, width, depth, doubleSided), model);
		statBuilt++;
	}

	private static void logStats() {
		if (EmtraClientConfig.RENDER_STATS) {
			final long now = System.nanoTime();
			if (now - lastLog > 10_000_000_000L) {
				lastLog = now;
				System.out.println("[EMTRA] Lift model cache (10 s): reused=" + statReused + ", built=" + statBuilt + ", models=" + MODELS.size());
				statReused = statBuilt = 0;
			}
		}
	}
}
