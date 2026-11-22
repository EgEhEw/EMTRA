package com.egehew.emtra.render.mixin;

import com.egehew.emtra.render.EmtraClientConfig;
import com.egehew.emtra.sound.ExactSoundPosition;
import org.mtr.core.tool.Vector;
import org.mtr.mod.data.VehicleExtension;
import org.mtr.mod.resource.VehicleResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hands the exact car position to the vehicle loops MTR updates (see ExactSoundPosition). */
@Mixin(value = VehicleExtension.class, remap = false)
public abstract class VehicleSoundPositionMixin {

	@Inject(method = "playMotorSound", at = @At("HEAD"), require = 0, remap = false)
	private void emtra$exactPosition(VehicleResource resource, int car, Vector position, CallbackInfo ci) {
		if (EmtraClientConfig.EXACT_VEHICLE_SOUND_POSITION) {
			ExactSoundPosition.begin(position);
		}
	}

	@Inject(method = "playMotorSound", at = @At("RETURN"), require = 0, remap = false)
	private void emtra$endExactPosition(VehicleResource resource, int car, Vector position, CallbackInfo ci) {
		ExactSoundPosition.end();
	}
}
