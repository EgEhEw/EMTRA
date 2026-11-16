package com.egehew.emtra.render.mixin;

import com.egehew.emtra.render.RailRenderCache;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.holder.Vector3d;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mod.entity.EntityRendering;
import org.mtr.mod.render.MainRenderer;
import org.mtr.mod.render.QueuedRenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BiConsumer;

/** Wraps the closures scheduled while a rail is being captured, and marks the start of each frame. */
@Mixin(value = MainRenderer.class, remap = false)
public abstract class MainRendererMixin {

	/** Start of MTR's world rendering for this frame: lets the rail cache read the camera and time once. */
	@Inject(method = "render(Lorg/mtr/mod/entity/EntityRendering;FFLorg/mtr/mapping/mapper/GraphicsHolder;I)V", at = @At("HEAD"), require = 0, remap = false)
	private void emtra$beginFrame(EntityRendering entity, float yaw, float tickDelta, GraphicsHolder graphicsHolder, int light, CallbackInfo ci) {
		RailRenderCache.beginFrame();
	}

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
