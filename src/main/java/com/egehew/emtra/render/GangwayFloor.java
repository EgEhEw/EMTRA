package com.egehew.emtra.render;

import org.mtr.core.tool.Vector;
import org.mtr.mod.render.PositionAndRotation;

/** Walkable height of a native Jacobs attachment in world coordinates. */
public final class GangwayFloor {
	private final Vector midpoint;
	private final double directionX;
	private final double directionZ;
	private final double slope;
	private final double halfLength;

	GangwayFloor(PositionAndRotation attachment, BogieArticulation.Seams seams) {
		midpoint = attachment.position.add(new Vector(0, seams.height(), 0).rotateX(attachment.pitch).rotateY(attachment.yaw));
		directionX = Math.sin(attachment.yaw);
		directionZ = Math.cos(attachment.yaw);
		slope = Math.tan(attachment.pitch);
		halfLength = seams.halfLength() * Math.cos(attachment.pitch);
	}

	public double heightAt(double firstX, double firstZ, double secondX, double secondZ, double progress, double fallback) {
		if (progress <= 0 || progress >= 1 || halfLength <= 0) return fallback;

		double first = depthAt(firstX, firstZ);
		double second = depthAt(secondX, secondZ);
		double near = Math.min(first, second);
		double far = Math.max(first, second);
		if (far <= -halfLength || near >= halfLength) return fallback;

		double depth = first + (second - first) * progress;
		double weight = 1;
		// The stock gangway can extend under the adjoining floors. Meet those
		// floors smoothly outside the modeled bridge's contact points.
		if (depth < -halfLength) {
			weight = (depth - near) / (-halfLength - near);
		} else if (depth > halfLength) {
			weight = (far - depth) / (far - halfLength);
		}
		if (!Double.isFinite(weight)) return fallback;
		return fallback + (midpoint.y + depth * slope - fallback) * VehicleSuspensionPhysics.smoothStep(weight);
	}

	private double depthAt(double x, double z) {
		return (x - midpoint.x) * directionX + (z - midpoint.z) * directionZ;
	}
}
