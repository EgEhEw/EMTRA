package com.egehew.emtra.forge.mixin;

import com.egehew.emtra.render.LightEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ClientChunkCache.onLightUpdate(LightLayer, SectionPos) (SRG m_6506_, 1.20.1 and 1.20.4): every client light change.
 * Named by its runtime SRG name with remap = false and no Minecraft member access, so it works from both the
 * Gradle and the offline build.
 */
@Pseudo
@Mixin(targets = "net.minecraft.client.multiplayer.ClientChunkCache", remap = false)
public abstract class ClientChunkCacheLightMixin {

	@Inject(method = "m_6506_(Lnet/minecraft/world/level/LightLayer;Lnet/minecraft/core/SectionPos;)V", at = @At("HEAD"), require = 0, remap = false)
	private void emtra$lightChanged(@Coerce Object layer, @Coerce Object sectionPos, CallbackInfo ci) {
		LightEvents.onSection(sectionPos);
	}
}
