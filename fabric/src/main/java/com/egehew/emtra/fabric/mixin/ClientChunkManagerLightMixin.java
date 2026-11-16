package com.egehew.emtra.fabric.mixin;

import com.egehew.emtra.render.LightEvents;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.LightType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ClientChunkManager.onLightUpdate: every client light change (see LightEvents). */
@Mixin(ClientChunkManager.class)
public abstract class ClientChunkManagerLightMixin {

	@Inject(method = "onLightUpdate", at = @At("HEAD"), require = 0)
	private void emtra$lightChanged(LightType type, ChunkSectionPos pos, CallbackInfo ci) {
		LightEvents.onSection(pos.getSectionX(), pos.getSectionY(), pos.getSectionZ());
	}
}
