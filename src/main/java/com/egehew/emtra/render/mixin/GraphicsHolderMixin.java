package com.egehew.emtra.render.mixin;

import com.egehew.emtra.render.CatenaryCache;
import com.egehew.emtra.render.RailRenderCache;
import com.egehew.emtra.render.PidsTextGraphics;
import org.mtr.mapping.holder.Direction;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Unique;

/** Direct (non-optimized) drawing while a rail is being captured makes that rail uncacheable. */
@Mixin(value = GraphicsHolder.class, remap = false)
public abstract class GraphicsHolderMixin implements PidsTextGraphics {

	/** The render mixin plugin replaces this bridge when it recognises MTR's font call. */
	@Override
	public void emtra$drawPidsText(String text, int x, int y, int color, boolean shadow, int light) {
		((GraphicsHolder) (Object) this).drawText(text, x, y, color, shadow, light);
	}

	/**
	 * Minecraft builds rotations with JOML, which derives the quaternion's cosine as sqrt(1 - sin^2) in float. Near a
	 * half turn (MTR draws every car with rotateX(pitch + PI)) sin rounds to 1 and small angles snap to steps:
	 * 0.005-0.02 degrees become 0, 0.03 becomes 0.04. On level track the suspension pitch crosses this every cycle.
	 * Turning by two halves keeps each half far from a half turn, with the same result and full precision.
	 */
	@Inject(method = "rotateXRadians", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
	private void emtra$preciseRotateX(float angle, CallbackInfo ci) {
		final float half = emtra$half(angle);
		if (half != 0) {
			ci.cancel();
			((GraphicsHolder) (Object) this).rotateXRadians(half);
			((GraphicsHolder) (Object) this).rotateXRadians(half);
		}
	}

	@Inject(method = "rotateYRadians", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
	private void emtra$preciseRotateY(float angle, CallbackInfo ci) {
		final float half = emtra$half(angle);
		if (half != 0) {
			ci.cancel();
			((GraphicsHolder) (Object) this).rotateYRadians(half);
			((GraphicsHolder) (Object) this).rotateYRadians(half);
		}
	}

	/** Half of the angle wrapped to (-PI, PI] when it is closer to a half turn than a quarter turn; 0 otherwise. */
	@Unique
	private static float emtra$half(float angle) {
		final double wrapped = Math.IEEEremainder(angle, 2 * Math.PI);
		return Math.abs(wrapped) > Math.PI / 2 ? (float) (wrapped / 2) : 0;
	}

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
