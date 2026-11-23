package com.egehew.emtra.render;

import com.egehew.emtra.EmtraMod;

import java.lang.reflect.Method;

/**
 * swayDebug only: logs whether JCM's script clock (TimingJS, behind Timing.delta and RateLimit) advances. It is driven
 * by a hook on MTR's MainRenderer.getMillisElapsed return; a frozen clock stops every vehicle script. Render thread.
 */
public final class JcmClockProbe {
	private static final long INTERVAL_NANOS = 5_000_000_000L;
	private static Method globalElapsed;
	private static boolean unavailable;
	private static long lastLog;
	private static double lastClock = Double.NaN;

	private JcmClockProbe() {
	}

	public static void frame() {
		if (!EmtraClientConfig.SWAY_DEBUG || unavailable) return;
		final long now = System.nanoTime();
		if (now - lastLog < INTERVAL_NANOS) return;
		final double seconds = (now - lastLog) / 1e9;
		lastLog = now;
		try {
			if (globalElapsed == null) {
				globalElapsed = Class.forName("com.lx862.mtrscripting.core.util.TimingJS").getMethod("globalElapsed");
			}
			final double clock = (Double) globalElapsed.invoke(null);
			if (!Double.isNaN(lastClock)) {
				EmtraMod.LOGGER.info("[EMTRA] JCM script clock: {} s, advanced {} s in the last {} s",
						String.format("%.2f", clock), String.format("%.2f", clock - lastClock), String.format("%.1f", seconds));
			}
			lastClock = clock;
		} catch (Throwable t) {
			unavailable = true; // JCM absent or changed
		}
	}
}
