package org.mtr.altplatforms.render.mixin;

import org.mtr.altplatforms.render.RailRenderCache;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mapping.mapper.OptimizedRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = OptimizedRenderer.class, remap = false)
public abstract class OptimizedRendererMixin {

	@Inject(method = "queue(Lorg/mtr/mapping/mapper/OptimizedModel;Lorg/mtr/mapping/mapper/GraphicsHolder;II)V", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
	private void emtra$captureQueue(OptimizedModel model, GraphicsHolder graphicsHolder, int color, int light, CallbackInfo ci) {
		if (RailRenderCache.onQueue((OptimizedRenderer) (Object) this, model, graphicsHolder, color, light)) {
			ci.cancel();
		}
	}
}
