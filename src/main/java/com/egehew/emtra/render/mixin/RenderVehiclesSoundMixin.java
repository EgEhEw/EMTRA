package com.egehew.emtra.render.mixin;

import com.egehew.emtra.sound.HiddenVehicleSounds;
import org.mtr.mapping.holder.Vector3d;
import org.mtr.mod.render.RenderVehicles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** See {@link HiddenVehicleSounds}. */
@Mixin(value = RenderVehicles.class, remap = false)
public abstract class RenderVehiclesSoundMixin {

	@Inject(method = "render(JLorg/mtr/mapping/holder/Vector3d;)V", at = @At("TAIL"), require = 0, remap = false)
	private static void emtra$hiddenCarSounds(long millisElapsed, Vector3d cameraShakeOffset, CallbackInfo ci) {
		HiddenVehicleSounds.afterRenderVehicles();
	}
}
