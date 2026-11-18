package com.egehew.emtra.sound;

import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.ClientWorld;
import org.mtr.mapping.holder.MinecraftClient;

import java.util.Arrays;

/**
 * How a vehicle sound reaches the listener: {gain, gainHF}.
 * <ul>
 * <li>Clear straight line (to rail level or 1 or 2 blocks above it, where the car body is): unchanged.</li>
 * <li>Otherwise the sound has to travel through the air. An air distance map is grown from the listener through
 * non-opaque blocks (tunnels, stations, stairwells); a vehicle the map reaches is heard, quieter and duller the
 * longer the detour is compared with the straight line, as sound bending around corners. Trains behind a tunnel bend
 * are heard; trains under solid ground are not, however close.</li>
 * <li>Thin walls (1-2 opaque blocks on the straight line) still let some sound through.</li>
 * </ul>
 * The map is one breadth-first search from the listener shared by every vehicle sound, spread over several updates
 * (a fixed number of blocks per update, no frame spikes) and redone once the listener moved or a second passed; the
 * previous map stays in use meanwhile. Render thread only.
 */
final class SoundPaths {

	private static final int MAX_PATH = 96;
	private static final int MAX_NODES = 24000;
	private static final int NODES_PER_UPDATE = 2000;
	/** The map is only kept up to date while some vehicle sound is blocked in a straight line. */
	private static final long NEEDED_NANOS = 3_000_000_000L;
	private static final long REFRESH_NANOS = 1_000_000_000L;
	private static final double DETOUR_GAIN_BLOCKS = 24;
	private static final double DETOUR_HF_BLOCKS = 10;
	private static final double WALL_GAIN = 0.55;
	private static final double WALL_HF = 0.35;

	private static final int SIZE = 2 * MAX_PATH + 1;

	interface Solid {
		boolean at(int x, int y, int z);
	}

	/** A distance map from an origin block: cell index -> steps through air (open addressing, -1 = empty). */
	static final class Field {
		final int originX, originY, originZ;
		int[] keys = new int[1 << 15];
		short[] values = new short[1 << 15];
		int size;
		int[] queue = new int[4096];
		int head, tail;
		boolean complete;
		final long started = System.nanoTime();

		Field(int originX, int originY, int originZ) {
			this.originX = originX;
			this.originY = originY;
			this.originZ = originZ;
			Arrays.fill(keys, -1);
			put(index(MAX_PATH, MAX_PATH, MAX_PATH), 0);
			enqueue(index(MAX_PATH, MAX_PATH, MAX_PATH));
		}

		/** Grows the map by up to {@code budget} blocks. */
		void grow(Solid solid, int budget) {
			while (head < tail && budget-- > 0) {
				final int node = queue[head++];
				final int x = node / (SIZE * SIZE), y = node / SIZE % SIZE, z = node % SIZE;
				final int distance = get(node);
				if (distance >= MAX_PATH) {
					continue;
				}
				for (int direction = 0; direction < 6; direction++) {
					final int ax = x + (direction == 0 ? 1 : direction == 1 ? -1 : 0);
					final int ay = y + (direction == 2 ? 1 : direction == 3 ? -1 : 0);
					final int az = z + (direction == 4 ? 1 : direction == 5 ? -1 : 0);
					if (ax < 0 || ay < 0 || az < 0 || ax >= SIZE || ay >= SIZE || az >= SIZE) {
						continue;
					}
					final int next = index(ax, ay, az);
					if (get(next) != -1) {
						continue;
					}
					if (solid.at(ax - MAX_PATH + originX, ay - MAX_PATH + originY, az - MAX_PATH + originZ)) {
						put(next, Short.MAX_VALUE); // solid: visited, never a path
						continue;
					}
					put(next, distance + 1);
					if (size >= MAX_NODES) {
						head = tail;
						break;
					}
					enqueue(next);
				}
			}
			if (head >= tail) {
				complete = true;
			}
		}

		/** Steps through air from the origin to the block, or -1 if not reached. */
		int distance(int x, int y, int z) {
			final int rx = x - originX + MAX_PATH, ry = y - originY + MAX_PATH, rz = z - originZ + MAX_PATH;
			if (rx < 0 || ry < 0 || rz < 0 || rx >= SIZE || ry >= SIZE || rz >= SIZE) {
				return -1;
			}
			final int value = get(index(rx, ry, rz));
			return value == Short.MAX_VALUE ? -1 : value;
		}

		private void enqueue(int node) {
			if (tail == queue.length) {
				queue = Arrays.copyOf(queue, queue.length * 2);
			}
			queue[tail++] = node;
		}

		private int get(int key) {
			int slot = mix(key) & keys.length - 1;
			while (keys[slot] != -1) {
				if (keys[slot] == key) {
					return values[slot];
				}
				slot = slot + 1 & keys.length - 1;
			}
			return -1;
		}

		private void put(int key, int value) {
			if (size * 2 >= keys.length) {
				final int[] oldKeys = keys;
				final short[] oldValues = values;
				keys = new int[oldKeys.length * 2];
				values = new short[oldKeys.length * 2];
				Arrays.fill(keys, -1);
				size = 0;
				for (int i = 0; i < oldKeys.length; i++) {
					if (oldKeys[i] != -1) {
						put(oldKeys[i], oldValues[i]);
					}
				}
			}
			int slot = mix(key) & keys.length - 1;
			while (keys[slot] != -1 && keys[slot] != key) {
				slot = slot + 1 & keys.length - 1;
			}
			if (keys[slot] == -1) {
				size++;
			}
			keys[slot] = key;
			values[slot] = (short) value;
		}

		private static int mix(int key) {
			final int h = key * 0x9E3779B1;
			return h ^ h >>> 16;
		}
	}

	/** The last finished map (used for queries) and the one being grown. */
	private static Field current, growing;
	private static long lastNeeded;

	private SoundPaths() {
	}

	/** Once per filter update: grows the next map a little; starts a new one when the listener moved or time passed. */
	static void beginUpdate(double listenerX, double listenerY, double listenerZ) {
		final ClientWorld world = MinecraftClient.getInstance().getWorldMapped();
		if (world == null) {
			current = null;
			growing = null;
			return;
		}
		if (System.nanoTime() - lastNeeded > NEEDED_NANOS) {
			growing = null;
			return;
		}
		final int x = floor(listenerX), y = floor(listenerY), z = floor(listenerZ);
		if (growing == null) {
			final Field reference = current;
			if (reference == null || Math.abs(reference.originX - x) + Math.abs(reference.originY - y) + Math.abs(reference.originZ - z) >= 2 || System.nanoTime() - reference.started > REFRESH_NANOS) {
				growing = new Field(x, y, z);
			}
		}
		if (growing != null) {
			growing.grow((bx, by, bz) -> world.getBlockState(new BlockPos(bx, by, bz)).isOpaque(), NODES_PER_UPDATE);
			if (growing.complete) {
				current = growing;
				growing = null;
			}
		}
	}

	/**
	 * {gain, gainHF} for a vehicle sound at x, y, z (rail level) heard at the listener. If apparent is not null, it
	 * receives where the sound should seem to come from when it reaches the listener around obstacles: in the direction
	 * of the last point of its path the listener can see (the opening or corner), at the length of the path. NaN when
	 * the vehicle's own position is right (clear line, or not heard at all).
	 */
	static void evaluate(double listenerX, double listenerY, double listenerZ, double x, double y, double z, float[] out, double[] apparent) {
		if (apparent != null) {
			apparent[0] = Double.NaN;
		}
		final int wallBlocks = SoundFilters.countVehicleOcclusion(listenerX, listenerY, listenerZ, x, y, z);
		if (wallBlocks == 0) {
			out[0] = 1;
			out[1] = 1;
			return;
		}
		lastNeeded = System.nanoTime();
		// Through a thin wall
		float gain = wallBlocks <= 2 ? (float) Math.pow(WALL_GAIN, wallBlocks) : 0;
		float gainHF = wallBlocks <= 2 ? (float) Math.pow(WALL_HF, wallBlocks) : 0;

		// Around through the air: the nearest mapped block of the car body (rail level up to 2 above) or next to it
		final Field field = current;
		// The previous map remains available while the new one grows. After the listener moves
		// to the other side of a sealed roof it no longer describes a path from the listener.
		if (field != null && field.distance(floor(listenerX), floor(listenerY), floor(listenerZ)) >= 0) {
			final int targetX = floor(x), targetY = floor(y), targetZ = floor(z);
			int best = Integer.MAX_VALUE;
			int bestX = 0, bestY = 0, bestZ = 0;
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					for (int dy = -1; dy <= 3; dy++) {
						final int distance = field.distance(targetX + dx, targetY + dy, targetZ + dz);
						if (distance >= 0) {
							final int total = distance + Math.abs(dx) + Math.abs(dz) + Math.max(0, -dy) + Math.max(0, dy - 2);
							if (total < best) {
								best = total;
								bestX = targetX + dx;
								bestY = targetY + dy;
								bestZ = targetZ + dz;
							}
						}
					}
				}
			}
			if (best != Integer.MAX_VALUE) {
				// The map may be from a block or two away while the listener moves.
				final double path = best + Math.abs(floor(listenerX) - field.originX) + Math.abs(floor(listenerY) - field.originY) + Math.abs(floor(listenerZ) - field.originZ);
				final double dx = x - listenerX, dy = y - listenerY, dz = z - listenerZ;
				final double detour = Math.max(0, path - Math.sqrt(dx * dx + dy * dy + dz * dz));
				if (apparent != null && findCorner(field, bestX, bestY, bestZ, listenerX, listenerY, listenerZ, CORNER)) {
					// Heard from the opening, as far away as the path is long: the distance falloff then already
					// follows the path, so the detour only takes away loudness slowly, and treble as before.
					final double cx = CORNER[0] - listenerX, cy = CORNER[1] - listenerY, cz = CORNER[2] - listenerZ;
					final double length = Math.sqrt(cx * cx + cy * cy + cz * cz);
					if (length > 1E-3) {
						apparent[0] = listenerX + cx / length * path;
						apparent[1] = listenerY + cy / length * path;
						apparent[2] = listenerZ + cz / length * path;
					}
					gain = Math.max(gain, (float) Math.exp(-detour / (DETOUR_GAIN_BLOCKS * 2)));
				} else {
					gain = Math.max(gain, (float) Math.exp(-detour / DETOUR_GAIN_BLOCKS));
				}
				gainHF = Math.max(gainHF, (float) Math.exp(-detour / DETOUR_HF_BLOCKS));
			}
		}
		out[0] = gain;
		out[1] = gainHF;
	}

	private static final double[] CORNER = new double[3];
	private static final int MAX_CORNER_CHECKS = 24;

	/**
	 * Walks the map's path from the vehicle back to the listener (always to a neighbour one step closer) and finds the
	 * last block of it, seen from the vehicle's side, that the listener can see in a straight line: where the sound
	 * comes round the corner. Checks at most {@link #MAX_CORNER_CHECKS} blocks, nearest to the listener first.
	 */
	private static boolean findCorner(Field field, int startX, int startY, int startZ, double listenerX, double listenerY, double listenerZ, double[] out) {
		int length = field.distance(startX, startY, startZ);
		if (length <= 0) {
			return false;
		}
		final int[] path = new int[(length + 1) * 3];
		int x = startX, y = startY, z = startZ, count = 0;
		while (length > 0) {
			path[count * 3] = x;
			path[count * 3 + 1] = y;
			path[count * 3 + 2] = z;
			count++;
			boolean moved = false;
			for (int direction = 0; direction < 6 && !moved; direction++) {
				final int ax = x + (direction == 0 ? 1 : direction == 1 ? -1 : 0);
				final int ay = y + (direction == 2 ? 1 : direction == 3 ? -1 : 0);
				final int az = z + (direction == 4 ? 1 : direction == 5 ? -1 : 0);
				if (field.distance(ax, ay, az) == length - 1) {
					x = ax;
					y = ay;
					z = az;
					length--;
					moved = true;
				}
			}
			if (!moved) {
				return false;
			}
		}
		// path[0] is next to the vehicle, path[count - 1] next to the listener: from the listener outwards, the last
		// block still in sight.
		boolean found = false;
		final int step = Math.max(1, count / MAX_CORNER_CHECKS);
		for (int i = count - 1; i >= 0; i -= step) {
			final double px = path[i * 3] + 0.5, py = path[i * 3 + 1] + 0.5, pz = path[i * 3 + 2] + 0.5;
			if (SoundFilters.countOccludingBlocks(listenerX, listenerY, listenerZ, px, py, pz) > 0) {
				break;
			}
			out[0] = px;
			out[1] = py;
			out[2] = pz;
			found = true;
		}
		return found;
	}

	static int index(int x, int y, int z) {
		return (x * SIZE + y) * SIZE + z;
	}

	private static int floor(double value) {
		return (int) Math.floor(value);
	}
}
