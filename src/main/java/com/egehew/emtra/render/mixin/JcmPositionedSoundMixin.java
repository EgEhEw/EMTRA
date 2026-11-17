package com.egehew.emtra.render.mixin;

import com.egehew.emtra.sound.MtrSounds;
import org.mtr.mapping.holder.SoundCategory;
import org.mtr.mapping.holder.SoundEvent;
import org.mtr.mapping.holder.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** One-shot sounds of JCM vehicle scripts (see {@link MtrSounds#playOneShot}). */
@Pseudo
@Mixin(targets = "com.lx862.mtrscripting.core.util.sound.PositionedSoundCall", remap = false)
public abstract class JcmPositionedSoundMixin {

	@Redirect(method = "*", at = @At(value = "INVOKE", target = "Lorg/mtr/mapping/holder/World;playSound(DDDLorg/mtr/mapping/holder/SoundEvent;Lorg/mtr/mapping/holder/SoundCategory;FFZ)V"), require = 0, remap = false)
	private void emtra$play(World world, double x, double y, double z, SoundEvent soundEvent, SoundCategory category, float volume, float pitch, boolean useDistance) {
		MtrSounds.playOneShot(x, y, z, soundEvent, category, volume, pitch, () -> world.playSound(x, y, z, soundEvent, category, volume, pitch, useDistance));
	}
}
