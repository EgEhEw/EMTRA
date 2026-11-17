package com.egehew.emtra.render.mixin;

import com.egehew.emtra.sound.MtrSounds;
import com.egehew.emtra.sound.SoundPhysicsCompat;
import org.mtr.mapping.holder.SoundInstance;
import org.mtr.mapping.holder.SoundManager;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Looping sounds of JCM vehicle scripts: tracked and kept away from Sound Physics Perfected like MTR's (see {@link MtrSounds}). */
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

	/** Same as MTR's looping sounds: a sound SPP replaced by its copy still counts as playing. */
	@Redirect(method = "*", at = @At(value = "INVOKE", target = "Lorg/mtr/mapping/holder/SoundManager;isPlaying(Lorg/mtr/mapping/holder/SoundInstance;)Z"), require = 0, remap = false)
	private boolean emtra$isPlaying(SoundManager soundManager, SoundInstance soundInstance) {
		return soundManager.isPlaying(soundInstance) || SoundPhysicsCompat.isHandled(this);
	}

	@Override
	public String toString() {
		return MtrSounds.SPP_MARKER + "@" + Integer.toHexString(System.identityHashCode(this));
	}
}
