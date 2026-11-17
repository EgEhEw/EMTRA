package com.egehew.emtra.render.mixin;

import com.egehew.emtra.sound.MtrSounds;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.ClientWorld;
import org.mtr.mapping.holder.SoundCategory;
import org.mtr.mapping.holder.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** MTR's one-shot vehicle sounds (doors, brakes, compressor). See {@link MtrSounds}. */
@Mixin(targets = "org.mtr.mod.sound.ScheduledSound", remap = false)
public abstract class ScheduledSoundMixin {

	@Inject(method = "playScheduledSounds()V", at = @At("HEAD"), require = 0, remap = false)
	private static void emtra$beginFlush(CallbackInfo ci) {
		MtrSounds.beginScheduledFlush();
	}

	@Redirect(method = "*", at = @At(value = "INVOKE", target = "Lorg/mtr/mapping/holder/ClientWorld;playSoundAtBlockCenter(Lorg/mtr/mapping/holder/BlockPos;Lorg/mtr/mapping/holder/SoundEvent;Lorg/mtr/mapping/holder/SoundCategory;FFZ)V"), require = 0, remap = false)
	private void emtra$play(ClientWorld clientWorld, BlockPos blockPos, SoundEvent soundEvent, SoundCategory category, float volume, float pitch, boolean useDistance) {
		MtrSounds.playScheduled(this, blockPos, soundEvent, category, volume, pitch, () -> clientWorld.playSoundAtBlockCenter(blockPos, soundEvent, category, volume, pitch, useDistance));
	}
}
