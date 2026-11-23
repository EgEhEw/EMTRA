package com.egehew.emtra.render.mixin;

import com.egehew.emtra.render.CatenaryCache;
import com.egehew.emtra.render.RailRenderCache;
import com.egehew.emtra.render.StationSignRendering;
import com.egehew.emtra.render.EmtraClientConfig;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.holder.MinecraftClient;
import org.mtr.mapping.holder.Vector3d;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mod.entity.EntityRendering;
import org.mtr.mod.render.MainRenderer;
import org.mtr.mod.render.QueuedRenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BiConsumer;

/** Wraps the closures scheduled while a rail is being captured, and marks the start of each frame. */
@Mixin(value = MainRenderer.class, remap = false)
public abstract class MainRendererMixin {

	@Unique
	private static long emtra$lastFrameNanos;
	@Unique
	private static long emtra$carryNanos;

	@Unique
	private static long emtra$exactMillis = -1;

	/**
	 * MTR advances vehicles by (long) (lastFrameDuration * 50) per frame, dropping the fraction of a millisecond, and
	 * falls back to its 50 ms tick clock once the drift reaches a tick, which jumps the train forward in one frame.
	 * Measure the frame with nanoTime instead and carry the remainder, so the vehicles follow real time smoothly.
	 * <p>
	 * The method must not be cancelled (no setReturnValue, not even at RETURN): other mods hook its return. JCM
	 * advances its script clock there (TimingJS, Timing.delta, RateLimit); a cancelled call skipped it and froze every
	 * vehicle script, e.g. pantographs never searched for the catenary. Instead both locals MTR picks the result from
	 * are replaced, so the method returns the exact value by itself and every return hook still runs.
	 */
	@ModifyVariable(method = "getMillisElapsed", at = @At("STORE"), index = 0, require = 0, remap = false)
	private static long emtra$exactMillisElapsed(long millisElapsed) {
		emtra$exactMillis = emtra$measureFrame();
		return emtra$exactMillis >= 0 ? emtra$exactMillis : millisElapsed;
	}

	@ModifyVariable(method = "getMillisElapsed", at = @At("STORE"), index = 2, require = 0, remap = false)
	private static long emtra$exactGameMillisElapsed(long gameMillisElapsed) {
		return emtra$exactMillis >= 0 ? emtra$exactMillis : gameMillisElapsed;
	}

	/** Exact milliseconds of this frame, or -1 to keep MTR's own value. */
	@Unique
	private static long emtra$measureFrame() {
		final long now = System.nanoTime();
		final long last = emtra$lastFrameNanos;
		emtra$lastFrameNanos = now;
		if (!EmtraClientConfig.EXACT_VEHICLE_TIMING || last == 0 || MinecraftClient.getInstance().isPaused()) {
			emtra$carryNanos = 0;
			return -1;
		}
		final long nanos = now - last + emtra$carryNanos;
		if (nanos < 0 || nanos > 1_000_000_000L) {
			// A stall of over a second (loading, alt-tab): keep MTR's own handling.
			emtra$carryNanos = 0;
			return -1;
		}
		final long millis = nanos / 1_000_000L;
		emtra$carryNanos = nanos - millis * 1_000_000L;
		return millis;
	}

	/** Start of MTR's world rendering for this frame: lets the rail cache read the camera and time once. */
	@Inject(method = "render(Lorg/mtr/mod/entity/EntityRendering;FFLorg/mtr/mapping/mapper/GraphicsHolder;I)V", at = @At("HEAD"), require = 0, remap = false)
	private void emtra$beginFrame(EntityRendering entity, float yaw, float tickDelta, GraphicsHolder graphicsHolder, int light, CallbackInfo ci) {
		RailRenderCache.beginFrame();
		com.egehew.emtra.sound.SoundFilters.update();
		com.egehew.emtra.render.JcmClockProbe.frame();
	}

	@Inject(method = "render(Lorg/mtr/mapping/mapper/GraphicsHolder;Lorg/mtr/mapping/holder/Vector3d;)V", at = @At(value = "INVOKE", target = "Lorg/mtr/mod/render/RenderRails;render()V"), remap = false)
	private static void emtra$enqueueStationSigns(GraphicsHolder graphics, Vector3d offset, CallbackInfo ci) {
		StationSignRendering.enqueue(graphics, offset);
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
	private static void emtra$wrapTextured(Identifier texture, boolean priority, QueuedRenderLayer layer, BiConsumer<GraphicsHolder, Vector3d> consumer, CallbackInfo ci) {
		final BiConsumer<GraphicsHolder, Vector3d> signLayer = StationSignRendering.separateSignLayers(priority, consumer);
		if (signLayer != consumer) {
			MainRenderer.scheduleRender(texture, priority, layer, signLayer);
			ci.cancel();
			return;
		}
		final BiConsumer<GraphicsHolder, Vector3d> catenary = CatenaryCache.wrap(texture, priority, layer, consumer);
		if (catenary != null) {
			CatenaryCache.setWrapping(true);
			try {
				MainRenderer.scheduleRender(texture, priority, layer, catenary);
			} finally {
				CatenaryCache.setWrapping(false);
			}
			ci.cancel();
			return;
		}
		final BiConsumer<GraphicsHolder, Vector3d> wrapped = RailRenderCache.wrap(layer, consumer, true);
		if (wrapped != null) {
			RailRenderCache.setWrapping(true);
			try {
				MainRenderer.scheduleRender(texture, priority, layer, wrapped);
			} finally {
				RailRenderCache.setWrapping(false);
			}
			ci.cancel();
		}
	}
}
