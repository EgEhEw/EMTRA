package com.egehew.emtra.render.mixin;

import com.egehew.emtra.sound.RemasteredSoundFilters;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** OpenAL can reuse a source ID as soon as Minecraft destroys its channel. */
@Pseudo
@Mixin(targets = {"com.mojang.blaze3d.audio.Channel", "net.minecraft.class_4224", "net.minecraft.client.sound.Source"}, remap = false)
public abstract class SoundChannelMixin {

	@Inject(method = "<init>(I)V", at = @At("RETURN"), remap = false)
	private void emtra$registerSource(int source, CallbackInfo ci) {
		RemasteredSoundFilters.register(source);
	}

	@Redirect(method = {"destroy()V", "m_83665_()V", "close()V", "method_19646()V"}, at = @At(value = "INVOKE", target = "Lorg/lwjgl/openal/AL10;alDeleteSources([I)V"), remap = false)
	private void emtra$deleteSources(int[] sources) {
		RemasteredSoundFilters.delete(sources);
	}
}
