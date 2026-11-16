package com.egehew.emtra.render;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client light changes, reported per 16³ section by a platform mixin on ClientChunkCache.onLightUpdate (every client
 * light change, block placement or light packet goes through it). Caches that bake light compare a section's last
 * change against the epoch of their last read, so they can re-read within a frame instead of polling.
 */
public final class LightEvents {

	private static final int MAX_TRACKED = 16384;
	private static final ConcurrentHashMap<Long, Long> CHANGED = new ConcurrentHashMap<>();
	/** Incremented on every reported change. */
	private static volatile long epoch;
	/** Everything read before this epoch is stale (the change map was cleared). */
	private static volatile long clearedAt;
	private static Field[] coordinates;
	private static boolean reflectionFailed;

	public static long epoch() {
		return epoch;
	}

	/** Section coordinates (block coordinates >> 4). */
	public static void onSection(int x, int y, int z) {
		final long current = ++epoch;
		if (CHANGED.size() >= MAX_TRACKED) {
			CHANGED.clear();
			clearedAt = current;
		}
		CHANGED.put(key(x, y, z), current);
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

	/** True if the section of this block changed after the given epoch. */
	public static boolean changedSince(long sectionKey, long since) {
		if (since < clearedAt) {
			return true;
		}
		final Long changed = CHANGED.get(sectionKey);
		return changed != null && changed > since;
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
