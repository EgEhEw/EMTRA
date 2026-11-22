package com.egehew.emtra.sound;

import org.mtr.core.tool.Vector;

/**
 * MTR places vehicle loops at Init.newBlockPos(car position): the corner of a block at rail level, jumping a whole
 * block at a time. At a platform that corner sits on the platform edge, so sound physics sees the source go in and out
 * of the blocks and the train's sound wobbles. VehicleExtension.playMotorSound passes the exact position here, and the
 * loops it updates use it, raised into the car body.
 * Render thread only.
 */
public final class ExactSoundPosition {

	/** Above rail level: clear of a platform (one block above the rail), inside the car body. */
	public static final double BODY_HEIGHT = 1.5;

	private static Vector pending;

	private ExactSoundPosition() {
	}

	public static void begin(Vector position) {
		pending = position;
	}

	public static void end() {
		pending = null;
	}

	/** The exact position for a loop updated at this block, or null if MTR is not inside playMotorSound. */
	public static Vector matching(int blockX, int blockY, int blockZ) {
		final Vector position = pending;
		return position != null && (int) Math.floor(position.x) == blockX && (int) Math.floor(position.y) == blockY
				&& (int) Math.floor(position.z) == blockZ ? position : null;
	}
}
