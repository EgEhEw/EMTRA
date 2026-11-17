package com.egehew.emtra.render.mixin;

import com.egehew.emtra.render.RailRenderCache;
import org.mtr.core.data.Rail;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.ClientWorld;
import org.mtr.mapping.holder.Vector3d;
import org.mtr.mod.render.RenderRails;
import org.mtr.mod.resource.RailResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenderRails.class, remap = false)
public abstract class RenderRailsMixin {

	private static final String RENDER_RAIL_STANDARD = "renderRailStandard(Lorg/mtr/mapping/holder/ClientWorld;Lorg/mtr/core/data/Rail;Lorg/mtr/mod/render/RenderRails$RenderState;F)V";

	@Inject(method = RENDER_RAIL_STANDARD, at = @At("HEAD"), cancellable = true, require = 0, remap = false)
	private static void emtra$cachedRail(ClientWorld clientWorld, Rail rail, @Coerce Object renderState, float lastFrameDuration, CallbackInfo ci) {
		if (RailRenderCache.beforeRail(rail, renderState instanceof Enum<?> && "NORMAL".equals(((Enum<?>) renderState).name()))) {
			ci.cancel();
		}
	}

	@Inject(method = RENDER_RAIL_STANDARD, at = @At("RETURN"), require = 0, remap = false)
	private static void emtra$afterRail(ClientWorld clientWorld, Rail rail, @Coerce Object renderState, float lastFrameDuration, CallbackInfo ci) {
		RailRenderCache.afterRail();
	}

	/** Remembers the block position each segment samples its light at, so cached segments can refresh their light. */
	@Inject(method = "lambda$renderRailStandard$16", at = @At("HEAD"), require = 0, remap = false)
	private static void emtra$segment(ClientWorld clientWorld, RailResource railResource, boolean flag, boolean[] flags, BlockPos blockPos,
									  double x1, double z1, double x2, double z2, double x3, double z3, double x4, double z4, double y1, double y2, CallbackInfo ci) {
		RailRenderCache.onSegment(clientWorld, blockPos, railResource);
	}

	/** While a rail is captured, every segment counts as close to the camera so the whole rail is recorded. */
	@Redirect(method = "lambda$renderWithinRenderDistance$22", at = @At(value = "INVOKE", target = "Lorg/mtr/mapping/holder/Vector3d;distanceTo(Lorg/mtr/mapping/holder/Vector3d;)D", remap = false), require = 0, remap = false)
	private static double emtra$captureDistance(Vector3d from, Vector3d to) {
		return RailRenderCache.capturingAllSegments() ? 0 : from.distanceTo(to);
	}
}
