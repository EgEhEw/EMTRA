package com.egehew.emtra.render.mixin;

import com.egehew.emtra.sound.MtrSounds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marks the motor and run loops of each BVE vehicle sound set: so they get the full cabin filter even with custom sound IDs. */
@Mixin(targets = "org.mtr.mod.sound.BveVehicleSound", remap = false)
public abstract class BveVehicleSoundMixin {

	@Inject(method = "<init>*", at = @At("TAIL"), require = 0, remap = false)
	private void emtra$tagMotorAndRun(CallbackInfo ci) {
		MtrSounds.tagBveMotorAndRun(this);
	}
}
