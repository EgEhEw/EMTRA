package com.egehew.emtra.sound;

import org.mtr.core.data.VehicleCar;
import org.mtr.core.tool.Vector;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import org.mtr.mapping.holder.MinecraftClient;
import org.mtr.mapping.holder.Vector3d;
import org.mtr.mod.client.CustomResourceLoader;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.client.VehicleRidingMovement;
import org.mtr.mod.data.VehicleExtension;

/**
 * MTR updates a car's sounds (BVE motor/run loops, doors) only while the car is drawn: a car behind a tunnel bend or
 * a wall kept its loops at their last volume and position, frozen. After MTR's vehicle rendering, the sounds of
 * cars within hearing range that were not drawn are updated the same way MTR does it; whether they are heard is then
 * up to {@link SoundPaths} (through the tunnel, yes; through the ground, no). Render thread, once per frame.
 */
public final class HiddenVehicleSounds {

	private static final double RANGE = 128;
	private static boolean broken;

	private HiddenVehicleSounds() {
	}

	public static void afterRenderVehicles() {
		if (broken) {
			return;
		}
		try {
			final MinecraftClientData data = MinecraftClientData.getInstance();
			if (data == null || data.vehicles == null) {
				return;
			}
			final Vector3d camera = MinecraftClient.getInstance().getGameRendererMapped().getCamera().getPos();
			final double cameraX = camera.getXMapped(), cameraY = camera.getYMapped(), cameraZ = camera.getZMapped();
			for (final VehicleExtension vehicle : data.vehicles) {
				if (VehicleRidingMovement.isRiding(vehicle.getId())) {
					continue; // MTR updates every car of the vehicle being ridden
				}
				final boolean[] drawn = vehicle.persistentVehicleData.rayTracing;
				final ObjectArrayList<ObjectObjectImmutablePair<VehicleCar, ObjectArrayList<ObjectObjectImmutablePair<Vector, Vector>>>> cars = vehicle.getVehicleCarsAndPositions();
				for (int carNumber = 0; carNumber < cars.size(); carNumber++) {
					if (drawn == null || carNumber >= drawn.length || drawn[carNumber]) {
						continue;
					}
					final ObjectObjectImmutablePair<VehicleCar, ObjectArrayList<ObjectObjectImmutablePair<Vector, Vector>>> car = cars.get(carNumber);
					final Vector position = center(car.right());
					if (position == null) {
						continue;
					}
					final double dx = position.x - cameraX, dy = position.y - cameraY, dz = position.z - cameraZ;
					if (dx * dx + dy * dy + dz * dz > RANGE * RANGE) {
						continue;
					}
					final int finalCarNumber = carNumber;
					CustomResourceLoader.getVehicleById(vehicle.getTransportMode(), car.left().getVehicleId(), resourceDetails -> {
						vehicle.playMotorSound(resourceDetails.left(), finalCarNumber, position);
						vehicle.playDoorSound(resourceDetails.left(), finalCarNumber, position);
					});
				}
			}
		} catch (Throwable t) {
			broken = true;
			com.egehew.emtra.EmtraMod.LOGGER.warn("Sounds of hidden train cars are no longer updated: {}", t.toString());
		}
	}

	private static Vector center(ObjectArrayList<ObjectObjectImmutablePair<Vector, Vector>> bogies) {
		if (bogies.isEmpty()) {
			return null;
		}
		double x = 0, y = 0, z = 0;
		for (final ObjectObjectImmutablePair<Vector, Vector> bogie : bogies) {
			x += bogie.left().x + bogie.right().x;
			y += bogie.left().y + bogie.right().y;
			z += bogie.left().z + bogie.right().z;
		}
		final int count = bogies.size() * 2;
		return new Vector(x / count, y / count, z / count);
	}
}
