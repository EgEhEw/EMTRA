package com.egehew.emtra.sound;

import com.egehew.emtra.EmtraMod;
import com.egehew.emtra.render.EmtraClientConfig;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.Vector3d;
import org.mtr.mapping.holder.World;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/**
 * Sound Physics Perfected's RaycastingHelper.countBlocksBetween stops counting once it reaches maxBlocksPermeated
 * (default 3) and returns that limit. A sound behind 30 blocks of ground was therefore as audible as one behind 3
 * (permeationAbsorption^3, about 13%): trains deep in tunnels were heard on the surface, placed at the surface points
 * the rays came from. When SPP's count reached its limit, the real number of opaque blocks is checked, and if it is
 * above the limit no sound passes along that ray.
 * <p>
 * Runs on SPP's ray threads. Stops counting as soon as the limit is exceeded, so the extra cost is a few block lookups
 * per capped ray. Loader-independent: the Minecraft world and vectors are wrapped in MTR holders found by reflection.
 */
public final class SppPermeationFix {

	private static final double BLOCKED = 1000;
	private static volatile boolean broken;
	private static Constructor<?> worldConstructor;
	private static Constructor<?> vectorConstructor;
	private static Field maxBlocksField;
	private static Object configInstance;

	private SppPermeationFix() {
	}

	public static double adjust(Object level, Object start, Object end, double sppCount) {
		if (broken || !EmtraClientConfig.SOUND_PHYSICS_COMPAT) {
			return sppCount;
		}
		try {
			final double limit = maxBlocksPermeated();
			if (sppCount < limit - 1E-6) {
				return sppCount;
			}
			final World world = (World) construct(level, true);
			final Vector3d from = (Vector3d) construct(start, false);
			final Vector3d to = (Vector3d) construct(end, false);
			return blocksExceed(world, from.getXMapped(), from.getYMapped(), from.getZMapped(), to.getXMapped(), to.getYMapped(), to.getZMapped(), (int) Math.floor(limit)) ? BLOCKED : sppCount;
		} catch (Throwable t) {
			broken = true;
			EmtraMod.LOGGER.warn("Sound Physics Perfected permeation fix disabled: {}", t.toString());
			return sppCount;
		}
	}

	private static double maxBlocksPermeated() throws ReflectiveOperationException {
		if (maxBlocksField == null) {
			final Class<?> config = Class.forName("com.redsmods.sound_physics_perfected.config.Config", false, SppPermeationFix.class.getClassLoader());
			configInstance = config.getMethod("getInstance").invoke(null);
			maxBlocksField = config.getField("maxBlocksPermeated");
		}
		return maxBlocksField.getDouble(configInstance);
	}

	private static Object construct(Object minecraftObject, boolean world) throws ReflectiveOperationException {
		Constructor<?> constructor = world ? worldConstructor : vectorConstructor;
		if (constructor == null) {
			for (final Constructor<?> candidate : (world ? World.class : Vector3d.class).getConstructors()) {
				if (candidate.getParameterCount() == 1 && candidate.getParameterTypes()[0].isInstance(minecraftObject)) {
					constructor = candidate;
					break;
				}
			}
			if (constructor == null) {
				throw new NoSuchMethodException("holder constructor for " + minecraftObject.getClass());
			}
			if (world) {
				worldConstructor = constructor;
			} else {
				vectorConstructor = constructor;
			}
		}
		return constructor.newInstance(minecraftObject);
	}

	/** Whether more than {@code limit} opaque blocks lie on the line, not counting the block containing the end. */
	private static boolean blocksExceed(World world, double x1, double y1, double z1, double x2, double y2, double z2, int limit) {
		final double dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
		final int endX = (int) Math.floor(x2), endY = (int) Math.floor(y2), endZ = (int) Math.floor(z2);
		int x = (int) Math.floor(x1), y = (int) Math.floor(y1), z = (int) Math.floor(z1);
		final int stepX = dx > 0 ? 1 : -1, stepY = dy > 0 ? 1 : -1, stepZ = dz > 0 ? 1 : -1;
		final double deltaX = dx == 0 ? Double.MAX_VALUE : Math.abs(1 / dx);
		final double deltaY = dy == 0 ? Double.MAX_VALUE : Math.abs(1 / dy);
		final double deltaZ = dz == 0 ? Double.MAX_VALUE : Math.abs(1 / dz);
		double maxX = dx == 0 ? Double.MAX_VALUE : (dx > 0 ? Math.floor(x1) + 1 - x1 : x1 - Math.floor(x1)) * deltaX;
		double maxY = dy == 0 ? Double.MAX_VALUE : (dy > 0 ? Math.floor(y1) + 1 - y1 : y1 - Math.floor(y1)) * deltaY;
		double maxZ = dz == 0 ? Double.MAX_VALUE : (dz > 0 ? Math.floor(z1) + 1 - z1 : z1 - Math.floor(z1)) * deltaZ;
		int blocks = 0;
		for (int steps = 0; steps < 512; steps++) {
			if (x == endX && y == endY && z == endZ) {
				return false;
			}
			if (world.getBlockState(new BlockPos(x, y, z)).isOpaque() && ++blocks > limit) {
				return true;
			}
			final double t;
			if (maxX < maxY && maxX < maxZ) {
				t = maxX;
				maxX += deltaX;
				x += stepX;
			} else if (maxY < maxZ) {
				t = maxY;
				maxY += deltaY;
				y += stepY;
			} else {
				t = maxZ;
				maxZ += deltaZ;
				z += stepZ;
			}
			if (t > 1) {
				return false;
			}
		}
		return false;
	}
}
