package com.egehew.emtra.render;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

import org.mtr.libraries.it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;

/**
 * Client light changes, reported per 16³ section by a platform mixin on ClientChunkCache.onLightUpdate (every client
 * light change, block placement or light packet goes through it). Caches that bake light compare a section's last
 * change against the epoch of their last read, so they can re-read within a frame instead of polling.
 */
public final class LightEvents {

	private static final int MAX_TRACKED = 16384;
	/**
	 * Section key -> epoch of its last change. Primitive map (no boxing), read and written on the render thread only:
	 * the per-frame lookups of every cached rail made a boxed ConcurrentHashMap a few percent of the frame.
	 */
	private static final Long2LongOpenHashMap CHANGED = new Long2LongOpenHashMap();
	/** Changes reported since the last {@link #epoch()} call, as (key, epoch) pairs; any thread may report. */
	private static final Object PENDING_LOCK = new Object();
	private static long[] pending = new long[256];
	private static int pendingCount;
	/**
	 * The changes applied by the last {@link #epoch()} call that applied any, as (key, epoch) pairs, and the epoch
	 * before them. A cache that read its light at or after {@code recentFrom} only has to look at these few, instead
	 * of the whole map (the common case: everything visible re-checks every frame).
	 */
	private static long[] recent = new long[64];
	private static int recentCount;
	private static long recentFrom = Long.MAX_VALUE;
	private static final int RECENT_SCAN_LIMIT = 32;
	/** Too many changes piled up before the render thread applied them: it forgets everything instead. */
	private static boolean pendingOverflow;
	/** Incremented on every reported change. */
	private static volatile long epoch;
	/** Everything read before this epoch is stale (the change map was cleared). */
	private static long clearedAt;
	private static Field[] coordinates;
	private static boolean reflectionFailed;

	static {
		CHANGED.defaultReturnValue(Long.MIN_VALUE);
	}

	/** Current epoch, without applying pending changes: for stamping a light value that was just read. */
	public static long current() {
		return epoch;
	}

	/** Current epoch. Render thread: also applies the changes reported since the last call. */
	public static long epoch() {
		synchronized (PENDING_LOCK) {
			if (pendingOverflow) {
				pendingOverflow = false;
				pendingCount = 0;
				CHANGED.clear();
				clearedAt = epoch;
				recentCount = 0;
				recentFrom = Long.MAX_VALUE;
				RailRenderCache.onLightReset();
			}
			if (pendingCount > 0) {
				recentFrom = pending[1] - 1;
				if (recent.length < pendingCount) {
					recent = new long[pendingCount];
				}
				System.arraycopy(pending, 0, recent, 0, pendingCount);
				recentCount = pendingCount;
				if (CHANGED.size() + pendingCount / 2 >= MAX_TRACKED) {
					CHANGED.clear();
					clearedAt = pending[pendingCount - 1];
				}
				for (int i = 0; i < pendingCount; i += 2) {
					CHANGED.put(pending[i], pending[i + 1]);
				}
				// Push the changes to the rail cache, so its rails do not have to ask every frame.
				RailRenderCache.onLightChanges(pending, pendingCount);
				pendingCount = 0;
			}
		}
		return epoch;
	}

	/** Section coordinates (block coordinates >> 4). */
	public static void onSection(int x, int y, int z) {
		synchronized (PENDING_LOCK) {
			final long current = ++epoch;
			if (pendingCount + 2 > pending.length) {
				if (pending.length >= MAX_TRACKED * 2) {
					// Far too many changes between frames: forget them all, everything re-reads.
					pendingCount = 0;
					pendingOverflow = true;
					return;
				}
				pending = Arrays.copyOf(pending, pending.length * 2);
			}
			pending[pendingCount++] = key(x, y, z);
			pending[pendingCount++] = current;
		}
	}

	/** For loaders whose mixin cannot name SectionPos members: reads the three Vec3i int fields reflectively. */
	public static void onSection(Object sectionPos) {
		if (sectionPos == null || reflectionFailed) {
			return;
		}
		try {
			if (coordinates == null) {
				coordinates = vec3iFields(sectionPos.getClass());
			}
			onSection(coordinates[0].getInt(sectionPos), coordinates[1].getInt(sectionPos), coordinates[2].getInt(sectionPos));
		} catch (Throwable t) {
			reflectionFailed = true;
			System.err.println("[EMTRA] Light change events unavailable: " + t);
		}
	}

	/** True if the section changed after the given epoch. Render thread, after {@link #epoch()} this frame. */
	public static boolean changedSince(long sectionKey, long since) {
		if (since < clearedAt) {
			return true;
		}
		if (since >= recentFrom && recentCount <= RECENT_SCAN_LIMIT * 2) {
			for (int i = 0; i < recentCount; i += 2) {
				if (recent[i] == sectionKey && recent[i + 1] > since) {
					return true;
				}
			}
			return false;
		}
		return CHANGED.get(sectionKey) > since;
	}

	public static long blockSectionKey(int blockX, int blockY, int blockZ) {
		return key(blockX >> 4, blockY >> 4, blockZ >> 4);
	}

	private static long key(int x, int y, int z) {
		return ((long) x & 0x3FFFFF) << 42 | ((long) y & 0xFFFFF) << 22 | ((long) z & 0x3FFFFF);
	}

	/** Vec3i declares x, y, z as its first three instance int fields. */
	private static Field[] vec3iFields(Class<?> type) {
		for (Class<?> c = type; c != null; c = c.getSuperclass()) {
			final List<Field> ints = new ArrayList<>();
			for (final Field field : c.getDeclaredFields()) {
				if (field.getType() == int.class && !Modifier.isStatic(field.getModifiers())) {
					ints.add(field);
				}
			}
			if (ints.size() == 3) {
				for (final Field field : ints) {
					field.setAccessible(true);
				}
				return ints.toArray(new Field[0]);
			}
		}
		throw new IllegalStateException("Vec3i coordinates not found in " + type);
	}

	private LightEvents() {
	}
}
