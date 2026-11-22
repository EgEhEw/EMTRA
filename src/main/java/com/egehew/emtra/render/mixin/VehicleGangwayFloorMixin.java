package com.egehew.emtra.render.mixin;

import com.egehew.emtra.render.GangwayFloor;
import com.egehew.emtra.render.VehicleMotion;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mapping.holder.Vector3d;
import org.mtr.mod.client.GangwayMovementPositions;
import org.mtr.mod.client.VehicleRidingMovement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = VehicleRidingMovement.class, remap = false)
public abstract class VehicleGangwayFloorMixin {
	@Shadow private static double ridingVehicleX;

	@Redirect(method = "movePlayer(JJILorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectArrayList;"
			+ "Lorg/mtr/mod/client/GangwayMovementPositions;Lorg/mtr/mod/client/GangwayMovementPositions;"
			+ "Lorg/mtr/mod/client/GangwayMovementPositions;Lorg/mtr/mod/render/PositionAndRotation;)V",
			at = @At(value = "INVOKE", ordinal = 5, target = "Lorg/mtr/mod/client/VehicleRidingMovement;getFromScale(DDD)D"))
	private static double emtra$followJacobsFloor(double firstY, double secondY, double progress,
			long elapsed, long vehicleId, int car, ObjectArrayList<?> floors,
			GangwayMovementPositions first, GangwayMovementPositions second) {
		double stockHeight = emtra$interpolate(firstY, secondY, progress);
		VehicleMotion.Frame frame = VehicleMotion.currentFrame();
		GangwayFloor floor = frame == null ? null : frame.gangwayFloor(car);
		if (floor == null || first == null || second == null) return stockHeight;

		Vector3d firstMin = first.getMinWorldPosition();
		Vector3d firstMax = first.getMaxWorldPosition();
		Vector3d secondMin = second.getMinWorldPosition();
		Vector3d secondMax = second.getMaxWorldPosition();
		return floor.heightAt(emtra$interpolate(firstMin.getXMapped(), firstMax.getXMapped(), ridingVehicleX),
				emtra$interpolate(firstMin.getZMapped(), firstMax.getZMapped(), ridingVehicleX),
				emtra$interpolate(secondMin.getXMapped(), secondMax.getXMapped(), ridingVehicleX),
				emtra$interpolate(secondMin.getZMapped(), secondMax.getZMapped(), ridingVehicleX), progress, stockHeight);
	}

	@Unique
	private static double emtra$interpolate(double from, double to, double progress) {
		return from + (to - from) * progress;
	}
}
