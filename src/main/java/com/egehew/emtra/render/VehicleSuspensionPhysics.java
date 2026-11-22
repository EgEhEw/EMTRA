package com.egehew.emtra.render;

import org.mtr.core.data.Vehicle;
import org.mtr.core.data.VehicleCar;
import org.mtr.core.tool.Vector;

final class VehicleSuspensionPhysics {
	static final Vector ZERO = new Vector(0, 0, 0);

	private VehicleSuspensionPhysics() {
	}

	static boolean sharesBogie(VehicleCar first, double rearBogie, VehicleCar second, double frontBogie) {
		double centerDistance = first.getTotalLength(true, false) - first.getLength() / 2
				+ second.getTotalLength(false, true) - second.getLength() / 2;
		return Math.abs(centerDistance + frontBogie - rearBogie) <= 0.02;
	}

	static Vector pitchDisplacement(Vector point, Vector center, double yaw, double pitch) {
		if (pitch == 0) return ZERO;
		Vector relative = difference(point, center);
		return difference(relative.rotateY(-yaw).rotateX(pitch).rotateY(yaw), relative);
	}

	static Vector difference(Vector first, Vector second) {
		return first.add(-second.x, -second.y, -second.z);
	}

	static double smoothStep(double value) {
		return value * value * (3 - 2 * value);
	}

	static final class BrakePitch {
		private static final long SPEED_SAMPLE_NANOS = 75_000_000L;
		private static final long MAX_FRAME_GAP_NANOS = 500_000_000L;
		private static final double SETTLE_SECONDS = 0.85;
		private static final double MAX_PITCH = Math.toRadians(0.05);
		private static final double PITCH_RESPONSE_SECONDS = 0.26;

		private boolean initialized;
		private long lastFrame;
		private long sampleTime;
		private double sampleSpeed;
		private double measuredDeceleration;
		private double pitch;
		private boolean wasMoving;
		private double stopTime;
		private double stopPitch;
		private boolean reversed;

		double update(long now, double speed, double speedTarget, int power, boolean currentReversed) {
			double dt = (now - lastFrame) / 1e9;
			lastFrame = now;
			boolean moving = speed > 0;
			if (!initialized || dt < 0 || dt > MAX_FRAME_GAP_NANOS / 1e9) {
				initialized = true;
				dt = 0;
				sampleTime = now;
				sampleSpeed = speed;
				measuredDeceleration = 0;
				pitch = 0;
				wasMoving = false;
				stopPitch = 0;
			}
			if (moving) {
				if (currentReversed != reversed) {
					sampleTime = now;
					sampleSpeed = speed;
					measuredDeceleration = 0;
				}
				reversed = currentReversed;
				if (now - sampleTime >= SPEED_SAMPLE_NANOS) {
					// MTR speed is measured in blocks per millisecond.
					measuredDeceleration = Math.max(0, (sampleSpeed - speed) * 1e12 / (now - sampleTime));
					sampleTime = now;
					sampleSpeed = speed;
				}
				double braking = speedTarget < speed && power < 0
						? Math.min(1, -power / (double) Vehicle.POWER_LEVEL_RATIO) : 0;
				double target = MAX_PITCH * Math.min(1, Math.max(braking, measuredDeceleration / 1.2));
				double signedTarget = reversed ? -target : target;
				pitch += (signedTarget - pitch) * -Math.expm1(-Math.min(dt, 0.25) / PITCH_RESPONSE_SECONDS);
			} else {
				if (wasMoving) {
					stopPitch = pitch;
					stopTime = 0;
				} else {
					stopTime += Math.max(0, dt);
				}
				pitch = stopPitch * (1 - smoothStep(Math.min(1, stopTime / SETTLE_SECONDS)));
				sampleTime = now;
				sampleSpeed = 0;
				measuredDeceleration = 0;
			}
			wasMoving = moving;
			return pitch;
		}
	}
}
