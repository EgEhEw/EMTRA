package com.egehew.emtra.render.mixin;

import com.egehew.emtra.sound.MtrSounds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Looping sounds of JCM vehicle scripts: tracked for occlusion and cabin muffle. */
@Pseudo
@Mixin(targets = "com.lx862.mtrscripting.core.util.sound.TickableSoundInstanceJS", remap = false)
public abstract class JcmScriptSoundMixin {

	@Inject(method = "<init>*", at = @At("TAIL"), require = 0, remap = false)
	private void emtra$register(CallbackInfo ci) {
		MtrSounds.register(this);
	}

	@Inject(method = "setPos", at = @At("TAIL"), require = 0, remap = false)
	private void emtra$recordPosition(CallbackInfoReturnable<?> cir) {
		MtrSounds.recordPosition(this);
	}
}
