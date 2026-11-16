package org.mtr.altplatforms.render.mixin;

import org.mtr.altplatforms.render.RailRenderCache;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.holder.Vector3d;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mod.render.MainRenderer;
import org.mtr.mod.render.QueuedRenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BiConsumer;

/** Wraps the closures scheduled while a rail is being captured. */
@Mixin(value = MainRenderer.class, remap = false)
public abstract class MainRendererMixin {

	@Inject(method = "scheduleRender(Lorg/mtr/mod/render/QueuedRenderLayer;Ljava/util/function/BiConsumer;)V", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
	private static void emtra$wrapModel(QueuedRenderLayer layer, BiConsumer<GraphicsHolder, Vector3d> consumer, CallbackInfo ci) {
		final BiConsumer<GraphicsHolder, Vector3d> wrapped = RailRenderCache.wrap(layer, consumer, false);
		if (wrapped != null) {
			RailRenderCache.setWrapping(true);
			try {
				MainRenderer.scheduleRender(layer, wrapped);
			} finally {
				RailRenderCache.setWrapping(false);
			}
			ci.cancel();
		}
	}

	@Inject(method = "scheduleRender(Lorg/mtr/mapping/holder/Identifier;ZLorg/mtr/mod/render/QueuedRenderLayer;Ljava/util/function/BiConsumer;)V", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
	private static void emtra$wrapTextured(Identifier texture, boolean translucent, QueuedRenderLayer layer, BiConsumer<GraphicsHolder, Vector3d> consumer, CallbackInfo ci) {
		final BiConsumer<GraphicsHolder, Vector3d> wrapped = RailRenderCache.wrap(layer, consumer, true);
		if (wrapped != null) {
			RailRenderCache.setWrapping(true);
			try {
				MainRenderer.scheduleRender(texture, translucent, layer, wrapped);
			} finally {
				RailRenderCache.setWrapping(false);
			}
			ci.cancel();
		}
	}
}
