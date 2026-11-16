package com.egehew.emtra.render.mixin;

import com.egehew.emtra.render.CatenaryCache;
import com.egehew.emtra.render.RailRenderCache;
import org.mtr.mapping.holder.Direction;
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
	private void emtra$drawTextureInWorld(float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3,
										  float x4, float y4, float z4, float u1, float v1, float u2, float v2, Direction facing,
										  int color, int light, CallbackInfo ci) {
		RailRenderCache.onDirectDraw();
		CatenaryCache.onQuad((GraphicsHolder) (Object) this, x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4, u1, v1, u2, v2, facing, color, light);
	}
}
