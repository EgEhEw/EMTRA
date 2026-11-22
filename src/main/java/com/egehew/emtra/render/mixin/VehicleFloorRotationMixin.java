package com.egehew.emtra.render.mixin;

import com.egehew.emtra.render.VehicleMotion;
import org.mtr.mapping.holder.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Vector3d.class, remap = false)
public abstract class VehicleFloorRotationMixin {
	@Shadow public abstract double getXMapped();
	@Shadow public abstract double getYMapped();
	@Shadow public abstract double getZMapped();

	@Inject(method = "rotateX", at = @At("HEAD"), cancellable = true)
	private void emtra$preciseSuspensionPitch(float pitch, CallbackInfoReturnable<Vector3d> ci) {
		if (pitch == 0 || !emtra$useExactFloorRotation()) return;

		// Vanilla's float sine lookup can round out the small suspension angles.
		// Match the precision of the rendered body for floors and moving riders.
		double sin = Math.sin(pitch);
		double cos = Math.cos(pitch);
		double y = getYMapped();
		double z = getZMapped();
		ci.setReturnValue(new Vector3d(getXMapped(), y * cos + z * sin, z * cos - y * sin));
	}

	@Inject(method = "rotateY", at = @At("HEAD"), cancellable = true)
	private void emtra$preciseSuspensionYaw(float yaw, CallbackInfoReturnable<Vector3d> ci) {
		if (yaw == 0 || !emtra$useExactFloorRotation()) return;

		double sin = Math.sin(yaw);
		double cos = Math.cos(yaw);
		double x = getXMapped();
		double z = getZMapped();
		ci.setReturnValue(new Vector3d(x * cos + z * sin, getYMapped(), z * cos - x * sin));
	}

	@Unique
	private static boolean emtra$useExactFloorRotation() {
		VehicleMotion.Frame frame = VehicleMotion.currentFrame();
		return frame != null && frame.usesAdvancedSway();
	}
}
