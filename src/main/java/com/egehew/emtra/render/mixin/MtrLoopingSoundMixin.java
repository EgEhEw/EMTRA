package com.egehew.emtra.render.mixin;

import com.egehew.emtra.sound.SoundPhysicsCompat;
import org.mtr.mapping.holder.SoundInstance;
import org.mtr.mapping.holder.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * MTR's looping sounds (vehicle motor/running sounds and looping block sounds) restart themselves whenever the sound
 * manager says they are not playing. See {@link SoundPhysicsCompat}.
 */
@Mixin(targets = {"org.mtr.mod.sound.VehicleLoopingSoundInstance", "org.mtr.mod.sound.LoopingSoundInstance"}, remap = false)
public abstract class MtrLoopingSoundMixin {

	@Redirect(method = "*", at = @At(value = "INVOKE", target = "Lorg/mtr/mapping/holder/SoundManager;isPlaying(Lorg/mtr/mapping/holder/SoundInstance;)Z"), require = 0, remap = false)
	private boolean emtra$isPlaying(SoundManager soundManager, SoundInstance soundInstance) {
		return soundManager.isPlaying(soundInstance) || SoundPhysicsCompat.isHandled(this);
	}
}
