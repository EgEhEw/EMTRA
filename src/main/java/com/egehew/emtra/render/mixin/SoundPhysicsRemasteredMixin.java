package com.egehew.emtra.render.mixin;

import com.egehew.emtra.sound.RemasteredSoundFilters;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Attaches the combined coefficients in the same OpenAL call where SPR attaches its filter. */
@Pseudo
@Mixin(targets = "com.sonicether.soundphysics.SoundPhysics", remap = false)
public abstract class SoundPhysicsRemasteredMixin {

	@Redirect(method = "setEnvironment(IFFFFFFFFFF)V", at = @At(value = "INVOKE", target = "Lorg/lwjgl/openal/AL11;alSourcei(III)V"), remap = false)
	private static void emtra$direct(int source, int parameter, int filter, int sourceId,
									float send0, float send1, float send2, float send3,
									float cut0, float cut1, float cut2, float cut3, float directHF, float directGain) {
		RemasteredSoundFilters.attachDirect(source, parameter, filter, directGain, directHF);
	}

	@Redirect(method = "setEnvironment(IFFFFFFFFFF)V", at = @At(value = "INVOKE", target = "Lorg/lwjgl/openal/AL11;alSource3i(IIIII)V"), remap = false)
	private static void emtra$send(int source, int parameter, int slot, int index, int filter, int sourceId,
								  float send0, float send1, float send2, float send3,
								  float cut0, float cut1, float cut2, float cut3, float directHF, float directGain) {
		// SPR wires its four reverb filters to OpenAL sends in reverse order.
		switch (index) {
			case 3 -> RemasteredSoundFilters.attachSend(source, parameter, slot, index, filter, send0, cut0);
			case 2 -> RemasteredSoundFilters.attachSend(source, parameter, slot, index, filter, send1, cut1);
			case 1 -> RemasteredSoundFilters.attachSend(source, parameter, slot, index, filter, send2, cut2);
			case 0 -> RemasteredSoundFilters.attachSend(source, parameter, slot, index, filter, send3, cut3);
			default -> org.lwjgl.openal.AL11.alSource3i(source, parameter, slot, index, filter);
		}
	}
}
