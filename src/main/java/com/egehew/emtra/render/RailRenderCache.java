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
import org.mtr.mod.resource.RailResource;

import org.mtr.libraries.it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Arrays;
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
 * rails, previews) are never cached. Rails are recaptured every few seconds so light and edits stay current, and
 * shortly after a capture that found a rail style's model still loading.
 * Render thread only.
 */
public final class RailRenderCache {

	private static final long RECAPTURE_NANOS = 5_000_000_000L;
	private static final long BAN_NANOS = 30_000_000_000L;
	private static final long FORGET_NANOS = 10_000_000_000L;
	/** Retry interval while some of a rail's models are still loading. */
	private static final long INCOMPLETE_RETRY_NANOS = 100_000_000L;

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
		/** Section of lightPos (see LightEvents). */
		final long sectionKey;

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
			sectionKey = lightPos == null ? Long.MIN_VALUE : LightEvents.blockSectionKey(lightPos.getX(), lightPos.getY(), lightPos.getZ());
		}

		/** Same light MTR computes for the segment every frame: packed block and sky light at the segment start. */
		void refreshLight() {
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
		/** A rail style's model was not loaded yet (MTR loads one resource per tick and drops unused ones after 60 s). */
		boolean incomplete;
	}

	/** All cached instances of one rail in one render layer, replayed by a single scheduled closure. */
	private static final class Batch {
		final QueuedRenderLayer layer;
		final List<Instance> instances = new ArrayList<>();
		/** Bounds of the instances' world positions, for whole-batch visibility. */
		double minX, minY, minZ, maxX, maxY, maxZ;
		/** Light sections of its instances, for {@link #SECTION_BATCHES}. */
		long[] sections = new long[0];
		/** Sections whose light changed since the last replay (pushed by LightEvents). */
		long[] dirty = new long[8];
		int dirtyCount;
		/** Re-read the light of every visible instance at the next replay. */
		boolean refreshAll;
		/** Not drawn at all last time: its instances' light may be stale. */
		boolean hidden;
		long nextLightRefresh;

		Batch(QueuedRenderLayer layer) {
			this.layer = layer;
		}

		void finish() {
			minX = minY = minZ = Double.MAX_VALUE;
			maxX = maxY = maxZ = -Double.MAX_VALUE;
			final LongOpenHashSet keys = new LongOpenHashSet();
			for (final Instance instance : instances) {
				minX = Math.min(minX, instance.worldX);
				minY = Math.min(minY, instance.worldY);
				minZ = Math.min(minZ, instance.worldZ);
				maxX = Math.max(maxX, instance.worldX);
				maxY = Math.max(maxY, instance.worldY);
				maxZ = Math.max(maxZ, instance.worldZ);
				if (instance.sectionKey != Long.MIN_VALUE) {
					keys.add(instance.sectionKey);
				}
			}
			sections = keys.toLongArray();
			nextLightRefresh = System.nanoTime() + LIGHT_REFRESH_NANOS + (System.identityHashCode(this) & 0x3FF) * 1_000_000L;
		}

		void markDirty(long section) {
			if (refreshAll) {
				return;
			}
			for (int i = 0; i < dirtyCount; i++) {
				if (dirty[i] == section) {
					return;
				}
			}
			if (dirtyCount == dirty.length) {
				if (dirtyCount >= 64) {
					refreshAll = true; // many sections changed: just re-read everything visible once
					dirtyCount = 0;
					return;
				}
				dirty = Arrays.copyOf(dirty, dirty.length * 2);
			}
			dirty[dirtyCount++] = section;
		}

		boolean isDirty(long section) {
			for (int i = 0; i < dirtyCount; i++) {
				if (dirty[i] == section) {
					return true;
				}
			}
			return false;
		}
	}

	/** Light section -> batches with instances in it, so light changes reach only the rails they affect. */
	private static final Long2ObjectOpenHashMap<List<Batch>> SECTION_BATCHES = new Long2ObjectOpenHashMap<>();

	private static void register(List<Batch> batches) {
		for (final Batch batch : batches) {
			for (final long section : batch.sections) {
				List<Batch> list = SECTION_BATCHES.get(section);
				if (list == null) {
					list = new ArrayList<>(2);
					SECTION_BATCHES.put(section, list);
				}
				list.add(batch);
			}
		}
	}

	private static void unregister(List<Batch> batches) {
		if (batches == null) {
			return;
		}
		for (final Batch batch : batches) {
			for (final long section : batch.sections) {
				final List<Batch> list = SECTION_BATCHES.get(section);
				if (list != null) {
					list.remove(batch);
					if (list.isEmpty()) {
						SECTION_BATCHES.remove(section);
					}
				}
			}
		}
	}

	/** Called by LightEvents (render thread) with the (section, epoch) pairs it just applied. */
	static void onLightChanges(long[] pairs, int count) {
		for (int i = 0; i < count; i += 2) {
			final List<Batch> list = SECTION_BATCHES.get(pairs[i]);
			if (list != null) {
				for (int j = 0, n = list.size(); j < n; j++) {
					list.get(j).markDirty(pairs[i]);
				}
			}
		}
	}

	/** Called by LightEvents when it lost track of changes: every rail re-reads its light. */
	static void onLightReset() {
		for (final Entry entry : CACHE.values()) {
			if (entry.batches != null) {
				for (final Batch batch : entry.batches) {
					batch.refreshAll = true;
				}
			}
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
	/** Fallback poll; light changes normally arrive through LightEvents within a frame. */
	private static final long LIGHT_REFRESH_NANOS = 2_000_000_000L;

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
				unregister(entry.batches);
				entry.batches = null;
				if (!capture.incomplete || capture.failed) {
					entry.bannedUntil = now + BAN_NANOS;
				}
				return false; // incomplete and empty: MTR draws it and the next frame captures again
			}
			unregister(entry.batches);
			entry.batches = batches(capture.groups);
			register(entry.batches);
			// Models still loading: replay what we have, but capture again shortly instead of in a few seconds.
			entry.nextCapture = now + (capture.incomplete ? INCOMPLETE_RETRY_NANOS : RECAPTURE_NANOS + (System.identityHashCode(rail) & 0x7FF) * 1_000_000L);
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
		for (final Batch batch : batches) {
			batch.finish();
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
	public static void onSegment(ClientWorld world, BlockPos pos, RailResource railResource) {
		if (scheduling != null) {
			segmentWorld = world;
			segmentPos = pos;
			if (!scheduling.incomplete && railResource != null) {
				try {
					// Same call MTR makes right after; null means the model is not loaded yet and draws nothing.
					if (railResource.getOptimizedModel() == null && railResource.getDynamicVehicleModel() == null) {
						scheduling.incomplete = true;
					}
				} catch (Throwable ignored) {
				}
			}
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

	private static final int HIDDEN = 0, PARTIAL = 1, ALL = 2;
	private static long lightFrame = -1;

	private static void replay(Batch batch, GraphicsHolder graphicsHolder, Vector3d offset) {
		final Matrix4f pose = pose(graphicsHolder);
		if (pose == null) {
			return;
		}
		updateCamera();
		if (lightFrame != frameId || !frameHook) {
			lightFrame = frameId;
			LightEvents.epoch(); // applies pending light changes: marks the affected batches dirty
		}
		final int visibility = visibility(batch);
		if (visibility == HIDDEN) {
			batch.hidden = true;
			return;
		}
		// Light: re-read everything visible after the batch was hidden, on a forced refresh and every few seconds
		// (fallback); otherwise only instances in sections whose light changed, and instances never read.
		final long now = now();
		final boolean refreshAll = batch.hidden || batch.refreshAll || now >= batch.nextLightRefresh;
		if (refreshAll) {
			batch.nextLightRefresh = now + LIGHT_REFRESH_NANOS;
			batch.refreshAll = false;
		}
		batch.hidden = false;
		final boolean dirty = !refreshAll && batch.dirtyCount > 0;
		SAVED.set(pose);
		final double ox = offset.getXMapped(), oy = offset.getYMapped(), oz = offset.getZMapped();
		final List<Instance> instances = batch.instances;
		try {
			for (int i = 0, n = instances.size(); i < n; i++) {
				final Instance instance = instances.get(i);
				if (visibility == PARTIAL && !visible(instance)) {
					instance.lightValid = false; // re-read when it comes back into view
					continue;
				}
				if (refreshAll || !instance.lightValid || dirty && batch.isDirty(instance.sectionKey)) {
					instance.refreshLight();
				}
				TEMP.set(SAVED).translate((float) (instance.offsetX - ox), (float) (instance.offsetY - oy), (float) (instance.offsetZ - oz)).mul(instance.matrix);
				pose.set(TEMP);
				instance.renderer.queue(instance.model, graphicsHolder, instance.color, instance.light);
			}
		} finally {
			pose.set(SAVED);
			// Only now: isDirty reads the list while the instances are checked.
			batch.dirtyCount = 0;
		}
	}

	/**
	 * {@link #visible} for the whole batch at once from its bounds: HIDDEN or ALL when every instance gives the same
	 * answer, PARTIAL otherwise. The forward test is linear in the position, so the box corners bound it.
	 */
	private static int visibility(Batch batch) {
		final double nearX = Math.max(0, Math.max(batch.minX - camX, camX - batch.maxX));
		final double nearZ = Math.max(0, Math.max(batch.minZ - camZ, camZ - batch.maxZ));
		final double nearHorizontal = Math.sqrt(nearX * nearX + nearZ * nearZ);
		if (nearHorizontal > renderDistance) {
			return HIDDEN;
		}
		final double farX = Math.max(Math.abs(batch.minX - camX), Math.abs(batch.maxX - camX));
		final double farZ = Math.max(Math.abs(batch.minZ - camZ), Math.abs(batch.maxZ - camZ));
		final double farHorizontal = Math.sqrt(farX * farX + farZ * farZ);
		if (farHorizontal < 32) {
			return ALL;
		}
		final double ax = (batch.minX - camX) * fwdX, bx = (batch.maxX - camX) * fwdX;
		final double ay = (batch.minY - camY) * fwdY, by = (batch.maxY - camY) * fwdY;
		final double az = (batch.minZ - camZ) * fwdZ, bz = (batch.maxZ - camZ) * fwdZ;
		final double minForward = Math.min(ax, bx) + Math.min(ay, by) + Math.min(az, bz);
		final double maxForward = Math.max(ax, bx) + Math.max(ay, by) + Math.max(az, bz);
		if (nearHorizontal >= 32 && maxForward <= 0) {
			return HIDDEN;
		}
		if (farHorizontal <= renderDistance && minForward > 0) {
			return ALL;
		}
		return PARTIAL;
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
			final Entry entry = iterator.next();
			if (now - entry.lastSeen > FORGET_NANOS) {
				unregister(entry.batches);
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
