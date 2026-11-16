package com.egehew.emtra.render.mixin;

import com.egehew.emtra.render.RailRenderCache;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Direct (non-optimized) drawing while a rail is being captured makes that rail uncacheable. */
@Mixin(value = GraphicsHolder.class, remap = false)
public abstract class GraphicsHolderMixin {

	@Inject(method = "createVertexConsumer", at = @At("HEAD"), require = 0, remap = false)
	private void emtra$createVertexConsumer(CallbackInfo ci) {
		RailRenderCache.onDirectDraw();
	}

	@Inject(method = "drawTextureInWorld", at = @At("HEAD"), require = 0, remap = false)
	private void emtra$drawTextureInWorld(CallbackInfo ci) {
		RailRenderCache.onDirectDraw();
	}
}
