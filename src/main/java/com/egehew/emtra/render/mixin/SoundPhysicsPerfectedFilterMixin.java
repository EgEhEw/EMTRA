package com.egehew.emtra.render.mixin;

import com.egehew.emtra.sound.SoundFilters;
import com.egehew.emtra.sound.SoundPhysicsCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Records the muffle Sound Physics Perfected puts on a source, so EMTRA's filters stack on top of it, and applies
 * EMTRA's occlusion/muffle to the reverb send SPP sets on every playing source.
 */
@Pseudo
@Mixin(targets = "com.redsmods.sound_physics_perfected.OpenALEffectsHandler", remap = false)
public abstract class SoundPhysicsPerfectedFilterMixin {

	@Inject(method = "applyMuffleToSource(IF)V", at = @At("TAIL"), require = 0, remap = false)
	private void emtra$afterMuffle(int sourceId, float muffleStrength, CallbackInfo ci) {
		SoundFilters.onExternalMuffle(sourceId, muffleStrength);
	}

	@Inject(method = {"applyReverbToSource(I)V", "applyLegacyReverbToSource(I)V", "applyInitalLegacyReverbToSource(I)V"}, at = @At("TAIL"), require = 0, remap = false)
	private void emtra$afterReverb(int sourceId, CallbackInfo ci) {
		SoundPhysicsCompat.afterReverb(this, sourceId);
	}
}
