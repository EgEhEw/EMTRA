package com.egehew.emtra.forge.mixin;

import com.egehew.emtra.sound.MtrSounds;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.mtr.mapping.holder.SoundCategory;
import org.mtr.mapping.holder.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** One-shot sounds of FangSu vehicle scripts (see {@link MtrSounds#playOneShot}). FangSu always plays them as BLOCKS. */
@Pseudo
@Mixin(targets = "com.fangsu.render.scripting.AbstractDrawCalls$PlaySoundCall", remap = false)
public abstract class FangsuPlaySoundMixin {

	@Redirect(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;m_7785_(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V", remap = false), require = 0, remap = false)
	private void emtra$play(ClientLevel level, double x, double y, double z, SoundEvent soundEvent, SoundSource source, float volume, float pitch, boolean useDistance) {
		final org.mtr.mapping.holder.SoundEvent event = new org.mtr.mapping.holder.SoundEvent(soundEvent);
		MtrSounds.playOneShot(x, y, z, event, SoundCategory.getBlocksMapped(), volume, pitch, () -> new World(level).playSound(x, y, z, event, SoundCategory.getBlocksMapped(), volume, pitch, useDistance));
	}
}
