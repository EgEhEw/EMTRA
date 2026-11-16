package com.egehew.emtra.render;

import org.joml.Matrix4f;
import org.mtr.core.data.Rail;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.Camera;
import org.mtr.mapping.holder.ClientWorld;
import org.mtr.mapping.holder.LightType;
import org.mtr.mapping.holder.LightmapTextureManager;
import org.mtr.mapping.holder.MinecraftClient;
import org.mtr.mapping.holder.Vector3d;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.MinecraftClientHelper;
import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mapping.mapper.OptimizedRenderer;
import org.mtr.mod.render.MainRenderer;
import org.mtr.mod.render.QueuedRenderLayer;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Caches the optimized model instances that MTR queues for each rail.
 * <p>
 * Every frame MTR recomputes rail curves (RailMath), builds a StoredMatrixTransformations per segment and
 * schedules a closure that applies it and queues the model. The resulting pose is
 * {@code P = base * T(initial - offset) * R}, where base and offset are the GraphicsHolder pose and camera offset
 * passed to the closure. When a rail is first rendered, all of its segments are captured (bypassing the render
 * distance filter) as {@code M = base^-1 * P} together with the offset of that frame. Later frames skip MTR's
 * computation and queue {@code base' * T(offset - offset') * M} directly, applying the same distance and
 * behind-camera filter as MTR per instance. Rails that draw anything other than optimized models (flat textured
 * rails, previews) are never cached. Rails are recaptured every few seconds so light and edits stay current.
 * Render thread only.
 */
public final class RailRenderCache {

	private static final long RECAPTURE_NANOS = 5_000_000_000L;
	private static final long BAN_NANOS = 30_000_000_000L;
	private static final long FORGET_NANOS = 10_000_000_000L;

	private static final class Instance {
		final OptimizedRenderer renderer;
		final OptimizedModel model;
		final Matrix4f matrix;
		final double offsetX, offsetY, offsetZ;
		final double worldX, worldY, worldZ;
		final int color;
		final ClientWorld world;
		final BlockPos lightPos;
		int light;
		/** False until the light was read for this frame's world state (MTR's value, or a refresh by position). */
		boolean lightValid;
		long lightChecked;

		Instance(OptimizedRenderer renderer, OptimizedModel model, Matrix4f matrix, double offsetX, double offsetY, double offsetZ, int color, int light, ClientWorld world, BlockPos lightPos) {
			this.renderer = renderer;
			this.model = model;
			this.matrix = matrix;
			this.offsetX = offsetX;
			this.offsetY = offsetY;
			this.offsetZ = offsetZ;
			worldX = offsetX + matrix.m30();
			worldY = offsetY + matrix.m31();
			worldZ = offsetZ + matrix.m32();
			this.color = color;
			this.light = light;
			this.world = world;
			this.lightPos = lightPos;
		}

		/** Same light MTR computes for the segment every frame: packed block and sky light at the segment start. */
		void refreshLight(long now) {
			lightChecked = now + (System.identityHashCode(this) & 0x3F) * 1_000_000L;
			if (world == null || lightPos == null) {
				return;
			}
			try {
				light = LightmapTextureManager.pack(world.getLightLevel(LightType.getBlockMapped(), lightPos), world.getLightLevel(LightType.getSkyMapped(), lightPos));
				lightValid = true;
			} catch (Throwable ignored) {
			}
		}
	}

	private static final class Group {
		final QueuedRenderLayer layer;
		final ClientWorld world;
		final BlockPos lightPos;
		final List<Instance> instances = new ArrayList<>();

		Group(QueuedRenderLayer layer, ClientWorld world, BlockPos lightPos) {
			this.layer = layer;
			this.world = world;
			this.lightPos = lightPos;
		}
	}

	private static final class Capture {
		final List<Group> groups = new ArrayList<>();
		final long started = System.nanoTime();
		int scheduled, executed;
		boolean failed;
	}

	/** All cached instances of one rail in one render layer, replayed by a single scheduled closure. */
	private static final class Batch {
		final QueuedRenderLayer layer;
		final List<Instance> instances = new ArrayList<>();

		Batch(QueuedRenderLayer layer) {
			this.layer = layer;
		}
	}

	private static final class Entry {
		List<Batch> batches;
		Capture pending;
		long nextCapture;
		long bannedUntil;
		long lastSeen;
	}

	private static final Map<Rail, Entry> CACHE = new IdentityHashMap<>();
	private static final Matrix4f SAVED = new Matrix4f();
	private static final Matrix4f TEMP = new Matrix4f();


	private static Capture scheduling;
	private static ClientWorld segmentWorld;
	private static BlockPos segmentPos;
	private static boolean wrapping;
	private static Capture executing;
	private static Group executingGroup;
	private static Matrix4f executingBaseInverse;
	private static double executingOffsetX, executingOffsetY, executingOffsetZ;

	private static long frameStamp = -1;
	/** Set by MainRenderer.render HEAD once per frame; without that hook, time-based fallbacks are used. */
	private static boolean frameHook;
	private static long frameId, cameraFrame = -1, frameNanos;
	private static double camX, camY, camZ;
	private static double fwdX, fwdY, fwdZ;
	private static int renderDistance;
	private static long lastSweep = System.nanoTime(), lastLog = System.nanoTime();
	/** MTR re-reads light every frame; a quarter second keeps cached segments within a few frames of it. */
	private static final long LIGHT_REFRESH_NANOS = 250_000_000L;

	private static long statCached, statCaptured, statLightChecked, statLightMismatch;
	private static int mismatchSamples;

	/**
	 * RenderRails.renderRailStandard(world, rail, state, lastFrameDuration) HEAD.
	 *
	 * @return true if the call was served from the cache and must be cancelled
	 */
	public static boolean beforeRail(Rail rail, boolean normalState) {
		scheduling = null;
		segmentWorld = null;
		segmentPos = null;
		if (!EmtraClientConfig.RENDER_RAIL_CACHE || rail == null || !normalState) {
			return false;
		}
		final long now = now();
		sweep(now);
		Entry entry = CACHE.get(rail);
		if (entry == null) {
			entry = new Entry();
			CACHE.put(rail, entry);
		}
		entry.lastSeen = now;
		if (entry.bannedUntil > now) {
			return false;
		}
		if (entry.pending != null && entry.pending.executed < entry.pending.scheduled && now - entry.pending.started > 2_000_000_000L) {
			entry.pending = null; // closures were never executed (not rendered); try again later
		}
		if (entry.pending != null && entry.pending.executed >= entry.pending.scheduled) {
			final Capture capture = entry.pending;
			entry.pending = null;
			if (capture.failed || capture.scheduled == 0) {
				entry.batches = null;
				entry.bannedUntil = now + BAN_NANOS;
				return false;
			}
			entry.batches = batches(capture.groups);
			entry.nextCapture = now + RECAPTURE_NANOS + (System.identityHashCode(rail) & 0x7FF) * 1_000_000L;
		}
		if (entry.batches != null && now < entry.nextCapture) {
			for (final Batch batch : entry.batches) {
				MainRenderer.scheduleRender(batch.layer, (graphicsHolder, offset) -> replay(batch, graphicsHolder, offset));
			}
			statCached++;
			return true;
		}
		if (entry.pending == null) {
			entry.pending = new Capture();
			scheduling = entry.pending;
			statCaptured++;
		}
		return false;
	}

	/** MainRenderer.render HEAD: start of MTR's rendering for this frame. */
	public static void beginFrame() {
		frameHook = true;
		frameId++;
		frameNanos = System.nanoTime();
	}

	private static long now() {
		return frameHook ? frameNanos : System.nanoTime();
	}

	/** Merges the captured per-segment groups into one batch per render layer, keeping the original order. */
	private static List<Batch> batches(List<Group> groups) {
		final List<Batch> batches = new ArrayList<>(2);
		for (final Group group : groups) {
			Batch batch = null;
			for (final Batch existing : batches) {
				if (existing.layer == group.layer) {
					batch = existing;
					break;
				}
			}
			if (batch == null) {
				batch = new Batch(group.layer);
				batches.add(batch);
			}
			batch.instances.addAll(group.instances);
		}
		return batches;
	}

	/** RenderRails.renderRailStandard RETURN. */
	public static void afterRail() {
		scheduling = null;
		segmentWorld = null;
		segmentPos = null;
	}

	/** Start of a rail segment callback while capturing: remembers the block position MTR samples light at. */
	public static void onSegment(ClientWorld world, BlockPos pos) {
		if (scheduling != null) {
			segmentWorld = world;
			segmentPos = pos;
		}
	}

	/** While capturing, report every segment as close to the camera so the whole rail is recorded. */
	public static boolean capturingAllSegments() {
		return scheduling != null;
	}

	/**
	 * MainRenderer.scheduleRender HEAD while a rail is being captured.
	 *
	 * @return the consumer to schedule instead, or null to keep the original
	 */
	public static BiConsumer<GraphicsHolder, Vector3d> wrap(QueuedRenderLayer layer, BiConsumer<GraphicsHolder, Vector3d> consumer, boolean textured) {
		final Capture capture = scheduling;
		if (capture == null || wrapping) {
			return null;
		}
		capture.scheduled++;
		if (textured) {
			capture.failed = true;
		}
		final Group group = new Group(layer, segmentWorld, segmentPos);
		capture.groups.add(group);
		return (graphicsHolder, offset) -> {
			final Matrix4f pose = pose(graphicsHolder);
			if (pose == null) {
				capture.failed = true;
			} else {
				executingBaseInverse = new Matrix4f(pose).invert();
				executingOffsetX = offset.getXMapped();
				executingOffsetY = offset.getYMapped();
				executingOffsetZ = offset.getZMapped();
			}
			executing = capture;
			executingGroup = group;
			try {
				consumer.accept(graphicsHolder, offset);
			} finally {
				executing = null;
				executingGroup = null;
				executingBaseInverse = null;
				capture.executed++;
			}
		};
	}

	public static boolean isWrapping() {
		return wrapping;
	}

	public static void setWrapping(boolean value) {
		wrapping = value;
	}

	/**
	 * OptimizedRenderer.queue HEAD.
	 *
	 * @return true to cancel (captured instance outside of MTR's render filter)
	 */
	public static boolean onQueue(OptimizedRenderer renderer, OptimizedModel model, GraphicsHolder graphicsHolder, int color, int light) {
		final Capture capture = executing;
		if (capture == null || capture.failed || executingBaseInverse == null) {
			return false;
		}
		final Matrix4f pose = pose(graphicsHolder);
		if (pose == null) {
			capture.failed = true;
			return false;
		}
		final Instance instance = new Instance(renderer, model, new Matrix4f(executingBaseInverse).mul(pose), executingOffsetX, executingOffsetY, executingOffsetZ, color, light, executingGroup.world, executingGroup.lightPos);
		executingGroup.instances.add(instance);
		updateCamera();
		final boolean visible = visible(instance);
		// Only segments MTR actually draws (inside its render filter) got a light value from loaded chunks; the
		// others are read again when they come into view.
		instance.lightValid = visible;
		instance.lightChecked = now();
		if (visible && EmtraClientConfig.RENDER_STATS && instance.world != null && instance.lightPos != null) {
			try {
				final int read = LightmapTextureManager.pack(instance.world.getLightLevel(LightType.getBlockMapped(), instance.lightPos), instance.world.getLightLevel(LightType.getSkyMapped(), instance.lightPos));
				statLightChecked++;
				if (read != light) {
					statLightMismatch++;
					if (mismatchSamples++ < 3) {
						System.out.println("[EMTRA] Light mismatch at " + instance.lightPos.getX() + " " + instance.lightPos.getY() + " " + instance.lightPos.getZ() + ": MTR=" + Integer.toHexString(light) + " read=" + Integer.toHexString(read));
					}
				}
			} catch (Throwable ignored) {
			}
		}
		return !visible;
	}

	/** Any direct (non-optimized) drawing while capturing makes the rail uncacheable. */
	public static void onDirectDraw() {
		if (executing != null) {
			executing.failed = true;
		}
	}

	private static void replay(Batch batch, GraphicsHolder graphicsHolder, Vector3d offset) {
		final Matrix4f pose = pose(graphicsHolder);
		if (pose == null) {
			return;
		}
		updateCamera();
		SAVED.set(pose);
		final double ox = offset.getXMapped(), oy = offset.getYMapped(), oz = offset.getZMapped();
		final long now = now();
		final List<Instance> instances = batch.instances;
		try {
			for (int i = 0, n = instances.size(); i < n; i++) {
				final Instance instance = instances.get(i);
				if (!visible(instance)) {
					instance.lightValid = false; // re-read when it comes back into view
					continue;
				}
				if (!instance.lightValid || now - instance.lightChecked > LIGHT_REFRESH_NANOS) {
					instance.refreshLight(now);
				}
				TEMP.set(SAVED).translate((float) (instance.offsetX - ox), (float) (instance.offsetY - oy), (float) (instance.offsetZ - oz)).mul(instance.matrix);
				pose.set(TEMP);
				instance.renderer.queue(instance.model, graphicsHolder, instance.color, instance.light);
			}
		} finally {
			pose.set(SAVED);
		}
	}

	/** Same filter as RenderRails.renderWithinRenderDistance: horizontal render distance, and in front of the camera beyond 32 blocks. */
	private static boolean visible(Instance instance) {
		final double dx = instance.worldX - camX, dz = instance.worldZ - camZ;
		final double horizontal = Math.sqrt(dx * dx + dz * dz);
		if (horizontal > renderDistance) {
			return false;
		}
		if (horizontal < 32) {
			return true;
		}
		final double dy = instance.worldY - camY;
		return dx * fwdX + dy * fwdY + dz * fwdZ > 0;
	}

	private static void updateCamera() {
		if (frameHook) {
			if (cameraFrame == frameId) {
				return;
			}
			cameraFrame = frameId;
		} else {
			final long stamp = System.nanoTime() / 2_000_000L; // no frame hook: refresh at most every 2 ms
			if (stamp == frameStamp) {
				return;
			}
			frameStamp = stamp;
		}
		final Camera camera = MinecraftClient.getInstance().getGameRendererMapped().getCamera();
		final Vector3d position = camera.getPos();
		camX = position.getXMapped();
		camY = position.getYMapped();
		camZ = position.getZMapped();
		renderDistance = MinecraftClientHelper.getRenderDistance() * 16;
		// MTR rotates (d) by yaw then pitch and keeps z > 0; that z is linear in d, so precompute its coefficients.
		final float yaw = (float) Math.toRadians(camera.getYaw());
		final float pitch = (float) Math.toRadians(camera.getPitch());
		fwdX = new Vector3d(1, 0, 0).rotateY(yaw).rotateX(pitch).getZMapped();
		fwdY = new Vector3d(0, 1, 0).rotateY(yaw).rotateX(pitch).getZMapped();
		fwdZ = new Vector3d(0, 0, 1).rotateY(yaw).rotateX(pitch).getZMapped();
	}

	private static Matrix4f pose(GraphicsHolder graphicsHolder) {
		return PoseAccess.pose(graphicsHolder);
	}

	private static void sweep(long now) {
		if (now - lastSweep < 2_000_000_000L) {
			return;
		}
		lastSweep = now;
		final Iterator<Entry> iterator = CACHE.values().iterator();
		while (iterator.hasNext()) {
			if (now - iterator.next().lastSeen > FORGET_NANOS) {
				iterator.remove();
			}
		}
		if (EmtraClientConfig.RENDER_STATS && now - lastLog > 10_000_000_000L) {
			lastLog = now;
			System.out.println("[EMTRA] Rail cache (10 s): served=" + statCached + ", captured=" + statCaptured + ", rails=" + CACHE.size() + ", lightChecked=" + statLightChecked + ", lightMismatch=" + statLightMismatch);
			statCached = statCaptured = statLightChecked = statLightMismatch = 0;
			mismatchSamples = 0;
		}
	}

	private RailRenderCache() {
	}
}
