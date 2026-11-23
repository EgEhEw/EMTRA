package com.egehew.emtra.render;

import com.egehew.emtra.render.mixin.VehiclePoseLightAccessor;
import org.mtr.core.data.TransportMode;
import org.mtr.core.data.VehicleCar;
import org.mtr.core.tool.Vector;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import org.mtr.mod.data.PersistentVehicleData;
import org.mtr.mod.data.VehicleExtension;
import org.mtr.mod.render.PositionAndRotation;

import java.util.WeakHashMap;

import static com.egehew.emtra.render.VehicleSuspensionPhysics.ZERO;
import static com.egehew.emtra.render.VehicleSuspensionPhysics.difference;
import static com.egehew.emtra.render.VehicleSuspensionPhysics.pitchDisplacement;
import static com.egehew.emtra.render.VehicleSuspensionPhysics.smoothStep;

/** Computes suspension on MTR's render thread and accepts optional script poses from JCM workers. */
public final class VehicleMotion {

	private static final long SAMPLE_LIFETIME_NANOS = 250_000_000L;
	private static final double KMH_PER_BLOCK_PER_MILLISECOND = 3600;
	private static final double ARTICULATED_YAW_AMPLITUDE = Math.toRadians(0.04);
	private static final double ARTICULATED_PITCH_AMPLITUDE = Math.toRadians(0.02);
	// MTR replaces VehicleExtension on network updates, while JCM retains the original script context.
	private static final WeakHashMap<PersistentVehicleData, CarPose[]> SAMPLES = new WeakHashMap<>();
	private static final WeakHashMap<PersistentVehicleData, VehicleSuspensionPhysics.BrakePitch> BRAKES = new WeakHashMap<>();
	private static final ThreadLocal<Frame> FRAME = new ThreadLocal<>();
	// Body sway drawn in the last frame, so that scripts (JCM pantographs) see the car where it is drawn.
	private static final WeakHashMap<PersistentVehicleData, DrawnSway> DRAWN = new WeakHashMap<>();

	private VehicleMotion() {
	}

	public interface ScriptContext {
		boolean emtra$submitSuspension(double[] bodies, double[] bogieAttachments, boolean replaceStockOscillation);
	}

	public static synchronized boolean publish(VehicleExtension vehicle, int[] cars, double[] bodies, double[] attachments, boolean replaceStockOscillation) {
		if (!valid(bodies, cars.length * 3) || !valid(attachments, cars.length * 6)) {
			return false;
		}

		int carCount = vehicle.vehicleExtraData.immutableVehicleCars.size();
		for (int car : cars) {
			if (car < 0 || car >= carCount) {
				return false;
			}
		}

		PersistentVehicleData vehicleData = vehicle.persistentVehicleData;
		CarPose[] poses = SAMPLES.get(vehicleData);
		if (poses == null || poses.length != carCount) {
			poses = new CarPose[carCount];
			SAMPLES.put(vehicleData, poses);
		}
		long now = System.nanoTime();
		for (int i = 0; i < cars.length; i++) {
			poses[cars[i]] = new CarPose(offset(bodies, i * 3), offset(attachments, i * 6),
					offset(attachments, i * 6 + 3), replaceStockOscillation, now);
		}
		return true;
	}

	public static Frame begin(VehicleExtension vehicle, ObjectArrayList<ObjectObjectImmutablePair<VehicleCar,
			ObjectObjectImmutablePair<ObjectArrayList<PositionAndRotation>, PositionAndRotation>>> cars) {
		FRAME.remove();
		long now = System.nanoTime();
		CarPose[] active = getSamples(vehicle, now);
		boolean sway = EmtraClientConfig.ADVANCED_SWAY;
		double pitch = brakePitch(vehicle, now);
		if (!sway && pitch == 0 && active == null) {
			return null;
		}
		Frame frame = new Frame(active, cars.size(), pitch, sway, vehicle.persistentVehicleData, now);
		frame.computeSuspension(vehicle, cars, now);
		FRAME.set(frame);
		return frame;
	}

	private static synchronized CarPose[] getSamples(VehicleExtension vehicle, long now) {
		PersistentVehicleData vehicleData = vehicle.persistentVehicleData;
		CarPose[] published = SAMPLES.get(vehicleData);
		CarPose[] active = null;
		if (published != null && published.length == vehicle.vehicleExtraData.immutableVehicleCars.size()) {
			for (int car = 0; car < published.length; car++) {
				CarPose pose = published[car];
				if (pose == null) {
					continue;
				}
				if (now - pose.timestamp > SAMPLE_LIFETIME_NANOS) {
					published[car] = null;
				} else {
					if (active == null) {
						active = new CarPose[published.length];
					}
					active[car] = pose;
				}
			}
		}
		if (active == null) {
			SAMPLES.remove(vehicleData);
		}
		return active;
	}

	private static double brakePitch(VehicleExtension vehicle, long now) {
		if (!EmtraClientConfig.ADVANCED_SWAY) {
			BRAKES.clear();
			return 0;
		}
		if (vehicle.getTransportMode() != TransportMode.TRAIN) {
			return 0;
		}
		var brake = BRAKES.computeIfAbsent(vehicle.persistentVehicleData, data -> new VehicleSuspensionPhysics.BrakePitch());
		return brake.update(now, vehicle.getSpeed(), vehicle.vehicleExtraData.getSpeedTarget(),
				vehicle.vehicleExtraData.getPowerLevel(), vehicle.getReversed()) * (EmtraClientConfig.SWAY_BRAKE_PITCH / 100.0);
	}

	public static Frame currentFrame() {
		return FRAME.get();
	}

	public static void end() {
		FRAME.remove();
	}

	private static boolean valid(double[] offsets, int length) {
		if (offsets == null) {
			return true;
		}
		if (offsets.length != length) {
			return false;
		}
		for (double value : offsets) {
			if (!Double.isFinite(value)) {
				return false;
			}
		}
		return true;
	}

	private static Vector offset(double[] offsets, int start) {
		return offsets == null || offsets[start] == 0 && offsets[start + 1] == 0 && offsets[start + 2] == 0
				? null : new Vector(offsets[start], offsets[start + 1], offsets[start + 2]);
	}

	/**
	 * Adds the body sway of the last drawn frame to car poses that JCM rebuilt from the rail for its scripts.
	 * Sway changes by well under a millimetre per frame, so the one frame delay is not visible.
	 */
	public static synchronized void applyDrawnSway(VehicleExtension vehicle, ObjectArrayList<PositionAndRotation> poses) {
		if (vehicle == null || poses == null) return;
		DrawnSway drawn = DRAWN.get(vehicle.persistentVehicleData);
		if (drawn == null) return;
		long now = System.nanoTime();
		int count = Math.min(poses.size(), drawn.time.length);
		for (int car = 0; car < count; car++) {
			PositionAndRotation pose = poses.get(car);
			if (pose == null || drawn.time[car] == 0 || now - drawn.time[car] > SAMPLE_LIFETIME_NANOS) continue;
			poses.set(car, Frame.withPose(pose, pose.position.add(drawn.x[car], drawn.y[car], drawn.z[car]),
					pose.yaw + drawn.yaw[car], pose.pitch + drawn.pitch[car]));
		}
	}

	private static synchronized void recordDrawnSway(PersistentVehicleData vehicleData, int carCount, int car,
			Vector offset, double yaw, double pitch, long now) {
		DrawnSway drawn = DRAWN.get(vehicleData);
		if (drawn == null || drawn.time.length != carCount) {
			drawn = new DrawnSway(carCount);
			DRAWN.put(vehicleData, drawn);
		}
		drawn.x[car] = offset == null ? 0 : offset.x;
		drawn.y[car] = offset == null ? 0 : offset.y;
		drawn.z[car] = offset == null ? 0 : offset.z;
		drawn.yaw[car] = yaw;
		drawn.pitch[car] = pitch;
		drawn.time[car] = now;
	}

	private static final class DrawnSway {
		final double[] x, y, z, yaw, pitch;
		final long[] time;

		DrawnSway(int cars) {
			x = new double[cars];
			y = new double[cars];
			z = new double[cars];
			yaw = new double[cars];
			pitch = new double[cars];
			time = new long[cars];
		}
	}

	private record CarPose(Vector body, Vector firstAttachment, Vector secondAttachment,
						   boolean replaceStockOscillation, long timestamp) {
	}

	private record Articulation(int rearBogie, int frontBogie, Vector pivot, int firstCar,
								PositionAndRotation firstBody, PositionAndRotation secondBody) {
	}

	public static final class Frame {
		private final CarPose[] poses;
		private final Vector[] bodies;
		private final Vector[][] joints;
		private final Articulation[][] jointLinks;
		private final GangwayFloor[] gangwayFloors;
		private final double[] bodyYaw;
		private final double[] trackYaw;
		private final PositionAndRotation[] originalBodies;
		private final double[] bodySwayPitch;
		private final double pitch;
		private final boolean sway;
		private final PersistentVehicleData vehicleData;
		private final long now;
		public PositionAndRotation wheelPosition;
		public boolean useWorldOffset;

		private Frame(CarPose[] poses, int carCount, double pitch, boolean sway, PersistentVehicleData vehicleData, long now) {
			this.poses = poses;
			this.vehicleData = vehicleData;
			this.now = now;
			bodies = new Vector[carCount];
			joints = new Vector[carCount][2];
			jointLinks = new Articulation[carCount][2];
			gangwayFloors = new GangwayFloor[carCount];
			bodyYaw = new double[carCount];
			trackYaw = new double[carCount];
			originalBodies = new PositionAndRotation[carCount];
			bodySwayPitch = new double[carCount];
			this.pitch = pitch;
			this.sway = sway;
		}

		private void computeSuspension(VehicleExtension vehicle,
				ObjectArrayList<ObjectObjectImmutablePair<VehicleCar,
						ObjectObjectImmutablePair<ObjectArrayList<PositionAndRotation>, PositionAndRotation>>> cars,
				long now) {
			double speedKmh = Math.abs(vehicle.getSpeed()) * KMH_PER_BLOCK_PER_MILLISECOND;
			double amplitude = sway ? smoothStep(Math.min(speedKmh / 24, 1)) : 0;
			double horizontal = amplitude * EmtraClientConfig.SWAY_HORIZONTAL / 100;
			double vertical = amplitude * EmtraClientConfig.SWAY_VERTICAL / 100;
			boolean movingSway = horizontal != 0 || vertical != 0;
			if (pitch == 0 && !movingSway) return;

			Articulation[] links = new Articulation[cars.size()];
			for (int car = 1; car < cars.size(); car++) {
				var first = cars.get(car - 1);
				var second = cars.get(car);
				int rear = bogieTowardEnd(first.left(), first.right().left().size(), true);
				int front = bogieTowardEnd(second.left(), second.right().left().size(), false);
				if (rear >= 0 && front >= 0 && VehicleSuspensionPhysics.sharesBogie(
						first.left(), bogieOffset(first.left(), rear), second.left(), bogieOffset(second.left(), front))) {
					Vector rearPoint = first.right().left().get(rear).position;
					Vector frontPoint = second.right().left().get(front).position;
					Articulation link = new Articulation(rear, front, Vector.getAverage(rearPoint, frontPoint),
							car - 1, first.right().right(), second.right().right());
					links[car] = link;
					jointLinks[car - 1][rear] = link;
					jointLinks[car][front] = link;
				}
			}

			// Rotate around each coach's lateral axis. At a shared pivot, carry the exact end
			// displacement into the next coach, then center the assembly's total movement.
			for (int first = 0; first < cars.size(); ) {
				int end = first + 1;
				while (end < cars.size() && links[end] != null) end++;
				double yaw = end - first > 1
						? cars.get(first).right().left().get(links[first + 1].rearBogie).yaw
						: cars.get(first).right().right().yaw;
				Vector swayOffset = swayOffset(now, horizontal, vertical, yaw, vehicle.getId(), first);
				bodies[first] = ZERO;
				Vector sum = ZERO;
				for (int car = first + 1; car < end; car++) {
					Articulation link = links[car];
					Vector displacement = bodies[car - 1].add(pitchAt(link.pivot, link.firstBody));
					bodies[car] = difference(displacement, pitchAt(link.pivot, link.secondBody));
					joints[car - 1][link.rearBogie] = displacement;
					joints[car][link.frontBogie] = displacement;
					sum = sum.add(bodies[car]);
				}
				double average = 1.0 / (end - first);
				boolean replaceSway = false;
				for (int car = first; car < end; car++) {
					CarPose pose = pose(car);
					replaceSway |= pose != null && pose.replaceStockOscillation;
				}
				Vector shift = difference(replaceSway ? ZERO : swayOffset, sum.multiply(average, average, average));
				for (int car = first; car < end; car++) {
					bodies[car] = bodies[car].add(shift);
					for (int bogie = 0; bogie < joints[car].length; bogie++) {
						if (joints[car][bogie] != null) joints[car][bogie] = joints[car][bogie].add(shift);
					}
				}
				if (!replaceSway && movingSway && end - first > 1) {
					applyArticulatedSway(links, first, end, now, horizontal, vertical, vehicle.getId());
				}
				first = end;
			}
		}

		private void applyArticulatedSway(Articulation[] links, int first, int end, long now, double horizontal, double vertical, long vehicleId) {
			double time = animationSeconds(now);
			for (int car = first; car < end; car++) {
				double phase = swayPhase(vehicleId, car);
				bodyYaw[car] = ARTICULATED_YAW_AMPLITUDE * horizontal * Math.sin(2 * Math.PI * 0.83 * time + phase);
				bodySwayPitch[car] = ARTICULATED_PITCH_AMPLITUDE * vertical * Math.sin(2 * Math.PI * 0.91 * time + phase + 0.4);
			}

			// Start at the already displaced first joint to preserve its common sway and brake pose.
			// For a chain, pass each later joint's additional displacement to both connected coaches.
			Vector correction = ZERO;
			for (int car = first + 1; car < end; car++) {
				Articulation link = links[car];
				Vector pivot = link.pivot.add(joints[car][link.frontBogie]);
				Vector previousTurn = swayDisplacement(pivot, link.firstBody, car - 1);
				Vector currentTurn = swayDisplacement(pivot, link.secondBody, car);
				if (car == first + 1) correction = difference(ZERO, previousTurn);
				bodies[car - 1] = bodies[car - 1].add(correction);
				Vector sharedShift = correction.add(previousTurn);
				Vector sharedJoint = joints[car][link.frontBogie].add(sharedShift);
				joints[car - 1][link.rearBogie] = sharedJoint;
				joints[car][link.frontBogie] = sharedJoint;
				correction = difference(sharedShift, currentTurn);
			}
			bodies[end - 1] = bodies[end - 1].add(correction);
		}

		private Vector swayDisplacement(Vector pivot, PositionAndRotation body, int car) {
			Vector relative = difference(pivot, body.position.add(bodies[car]));
			Vector turned = relative.rotateY(-body.yaw).rotateX(bodySwayPitch[car]).rotateY(body.yaw + bodyYaw[car]);
			return difference(turned, relative);
		}

		private Vector pitchAt(Vector pivot, PositionAndRotation body) {
			return pitchDisplacement(pivot, body.position, body.yaw, pitch);
		}

		private static double bogieOffset(VehicleCar car, int index) {
			return index == 0 ? car.getBogie1Position() : car.getBogie2Position();
		}

		private static int bogieTowardEnd(VehicleCar car, int count, boolean rear) {
			if (count == 0) return -1;
			if (count == 1) return 0;
			return (car.getBogie2Position() > car.getBogie1Position()) == rear ? 1 : 0;
		}

		private static Vector swayOffset(long now, double horizontal, double vertical, double yaw, long vehicleId, int unit) {
			if (horizontal == 0 && vertical == 0) return ZERO;
			double time = animationSeconds(now);
			double phase = swayPhase(vehicleId, unit);
			double side = -0.0043 * horizontal * Math.sin(2 * Math.PI * 0.64 * time + phase);
			double heave = 0.0019 * vertical * Math.sin(2 * Math.PI * 1.17 * time + phase + 0.4);
			return new Vector(side, heave, 0).rotateY(yaw);
		}

		private static double animationSeconds(long now) {
			return (now % 100_000_000_000L) / 1e9;
		}

		private static double swayPhase(long vehicleId, int car) {
			return (Math.floorMod(vehicleId, 997) + car) * Math.PI * (3 - Math.sqrt(5));
		}

		public boolean replaceStockOscillation(int car) {
			CarPose pose = pose(car);
			return sway || (pose != null && pose.replaceStockOscillation);
		}

		public PositionAndRotation shiftBody(PositionAndRotation original, int car) {
			trackYaw[car] = original.yaw;
			originalBodies[car] = original;
			CarPose pose = pose(car);
			Vector nativeOffset = bodies[car];
			Vector offset = combine(nativeOffset, pose == null ? null : pose.body);
			recordDrawnSway(vehicleData, originalBodies.length, car, offset, bodyYaw[car], pitch + bodySwayPitch[car], now);
			return shift(original, offset, bodyYaw[car], pitch + bodySwayPitch[car]);
		}

		/** The rail pose of a car before suspension, or null before {@link #shiftBody}. */
		public PositionAndRotation originalBody(int car) {
			return car >= 0 && car < originalBodies.length ? originalBodies[car] : null;
		}

		public double trackYaw(int car) {
			return trackYaw[car];
		}

		public boolean usesAdvancedSway() {
			return sway;
		}

		/** The walking segment from the previous car into this one. */
		public GangwayFloor gangwayFloor(int car) {
			return car > 0 && car < gangwayFloors.length ? gangwayFloors[car] : null;
		}

		public PositionAndRotation shiftArticulation(PositionAndRotation original, int car, int bogie,
				BogieArticulation.Seams seams) {
			if (bogie < 0 || bogie >= joints[car].length) return original;
			Vector offset = joints[car][bogie];
			Articulation link = jointLinks[car][bogie];
			if (link == null) return shift(original, offset, 0, 0);

			int first = link.firstCar;
			int second = first + 1;
			if (pitch == 0 && bodySwayPitch[first] == 0 && bodySwayPitch[second] == 0) {
				return shift(original, offset, 0, 0);
			}

			// The gangway spans two contact planes above the bogie. Match their heights
			// while retaining the shared yaw and the horizontal sway of the gangway center.
			double halfLength = seams.halfLength();
			double height = seams.height();
			Vector firstSeam = attachmentPoint(original, height, -halfLength);
			Vector secondSeam = attachmentPoint(original, height, halfLength);
			double firstY = seamHeight(firstSeam, link.firstBody, first);
			double secondY = seamHeight(secondSeam, link.secondBody, second);
			double sine = (secondY - firstY) / (2 * halfLength);
			double attachmentPitch = Math.asin(Math.max(-1, Math.min(1, sine)));
			Vector reference = attachmentPoint(original, height, 0).add(offset);
			Vector center = new Vector(reference.x, (firstY + secondY) / 2, reference.z);
			Vector heightOffset = new Vector(0, height, 0).rotateX(attachmentPitch).rotateY(original.yaw);
			PositionAndRotation attachment = withPose(original, difference(center, heightOffset), original.yaw, attachmentPitch);
			gangwayFloors[second] = new GangwayFloor(attachment, seams);
			return attachment;
		}

		private static Vector attachmentPoint(PositionAndRotation attachment, double height, double depth) {
			return attachment.position.add(new Vector(0, height, depth).rotateX(attachment.pitch).rotateY(attachment.yaw));
		}

		private double seamHeight(Vector seam, PositionAndRotation body, int car) {
			return seam.y + bodies[car].y + pitchDisplacement(seam, body.position, body.yaw, pitch + bodySwayPitch[car]).y;
		}

		public PositionAndRotation shiftAttachment(PositionAndRotation original, int car, int bogie) {
			CarPose pose = pose(car);
			if (pose == null || bogie < 0 || bogie > 1) return original;
			return shift(original, bogie == 0 ? pose.firstAttachment : pose.secondAttachment, 0, 0);
		}

		private CarPose pose(int car) {
			return poses != null && car >= 0 && car < poses.length ? poses[car] : null;
		}

		private static Vector combine(Vector first, Vector second) {
			return first == null ? second : second == null ? first : first.add(second);
		}

		private static PositionAndRotation shift(PositionAndRotation original, Vector offset, double yaw, double pitch) {
			if (yaw == 0 && pitch == 0 && (offset == null || offset.x == 0 && offset.y == 0 && offset.z == 0)) return original;
			return withPose(original, offset == null ? original.position : original.position.add(offset),
					original.yaw + yaw, original.pitch + pitch);
		}

		static PositionAndRotation withPose(PositionAndRotation original, Vector position, double yaw, double pitch) {
			PositionAndRotation shifted = new PositionAndRotation(position, yaw, pitch);
			// Sway can repeatedly cross a block boundary while the underlying rail pose stays level.
			if ((Object) shifted instanceof VehiclePoseLightAccessor access) access.emtra$setLight(original.light);
			return shifted;
		}
	}
}
