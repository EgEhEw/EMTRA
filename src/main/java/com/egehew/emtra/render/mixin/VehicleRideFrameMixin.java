package com.egehew.emtra.render.mixin;

import com.egehew.emtra.render.SwayDebug;
import com.egehew.emtra.render.VehicleMotion;
import org.mtr.libraries.it.unimi.dsi.fastutil.ints.IntObjectImmutablePair;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import org.mtr.mapping.holder.Box;
import org.mtr.mapping.holder.Vector3d;
import org.mtr.mapping.mapper.OptimizedRenderer;
import org.mtr.mod.client.GangwayMovementPositions;
import org.mtr.mod.client.VehicleRidingMovement;
import org.mtr.mod.render.PositionAndRotation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = VehicleRidingMovement.class, remap = false)
public abstract class VehicleRideFrameMixin {
	@Shadow private static long ridingVehicleId;
	@Shadow private static int ridingVehicleCarNumber;
	@Shadow private static double ridingVehicleX;
	@Shadow private static double ridingVehicleY;
	@Shadow private static double ridingVehicleZ;
	@Shadow private static boolean isOnGangway;
	@Shadow private static Vector3d ridingPositionCache;
	@Shadow private static Double ridingYawDifference;
	@Unique private static long emtra$headingVehicle;
	@Unique private static double emtra$previousTrackYaw;

	@Unique
	private static final String EMTRA_MOVE_PLAYER = "movePlayer(JJILorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectArrayList;"
			+ "Lorg/mtr/mod/client/GangwayMovementPositions;Lorg/mtr/mod/client/GangwayMovementPositions;"
			+ "Lorg/mtr/mod/client/GangwayMovementPositions;Lorg/mtr/mod/render/PositionAndRotation;)V";

	@Inject(method = "startRiding", at = @At("HEAD"), cancellable = true)
	private static void emtra$keepRideAcrossFrames(ObjectArrayList<Box> floors, long depot, long siding, long vehicle, int car,
			double x, double y, double z, double yaw, CallbackInfo ci) {
		if (!emtra$advancedRideFrame()) return;
		if (OptimizedRenderer.renderingShadows() || emtra$isAlreadyRiding(vehicle)) {
			// startRiding clears MTR's current and previous camera offsets even for a passenger
			// stepping inside an open doorway. movePlayer handles an existing rider's floor transitions.
			ci.cancel();
		} else if (ridingVehicleId == 0 && !floors.isEmpty()) {
			emtra$headingVehicle = 0;
		}
	}

	@Inject(method = EMTRA_MOVE_PLAYER, at = @At("HEAD"), cancellable = true)
	private static void emtra$moveRiderOnlyInMainPass(CallbackInfo ci) {
		if (emtra$advancedRideFrame() && OptimizedRenderer.renderingShadows()) ci.cancel();
	}

	@Inject(method = EMTRA_MOVE_PLAYER, at = @At("HEAD"))
	private static void emtra$logRiderFrame(long elapsed, long vehicle, int car, ObjectArrayList<?> floors,
			GangwayMovementPositions previous, GangwayMovementPositions front, GangwayMovementPositions rear,
			PositionAndRotation body, CallbackInfo ci) {
		if (SwayDebug.enabled() && !OptimizedRenderer.renderingShadows() && emtra$isAlreadyRiding(vehicle)
				&& ridingVehicleCarNumber == car) {
			SwayDebug.frame(vehicle, car, body, isOnGangway);
		}
	}

	@Redirect(method = EMTRA_MOVE_PLAYER, at = @At(value = "INVOKE", target = "Ljava/lang/Math;toDegrees(D)D"))
	private static double emtra$turnRiderAlongShortestArc(double yawDelta) {
		return Math.toDegrees(emtra$advancedRideFrame() ? emtra$shortestArc(yawDelta) : yawDelta);
	}

	// MTR's final absolute delta decides whether to use its turning-camera path.
	// The visual sway may cross this threshold on straight track, and atan2 can wrap at either side of pi.
	@Redirect(method = EMTRA_MOVE_PLAYER, at = @At(value = "INVOKE", ordinal = 4, target = "Ljava/lang/Math;abs(D)D"))
	private static double emtra$cameraTurnFromTrack(double yawDelta, long elapsed, long vehicle, int car, ObjectArrayList<?> floors,
			GangwayMovementPositions previous, GangwayMovementPositions front, GangwayMovementPositions rear,
			PositionAndRotation body) {
		VehicleMotion.Frame frame = VehicleMotion.currentFrame();
		if (frame == null || !frame.usesAdvancedSway()) {
			emtra$headingVehicle = 0;
			return Math.abs(yawDelta);
		}

		double trackYaw = frame.trackYaw(car);
		double trackDelta = emtra$headingVehicle == vehicle ? emtra$shortestArc(trackYaw - emtra$previousTrackYaw) : 0;
		emtra$headingVehicle = vehicle;
		emtra$previousTrackYaw = trackYaw;
		return Math.abs(trackDelta);
	}

	@Unique
	private static double emtra$shortestArc(double radians) {
		return Math.IEEEremainder(radians, 2 * Math.PI);
	}

	@Inject(method = "getRidingVehicleCarNumberAndOffset", at = @At("RETURN"), cancellable = true)
	private static void emtra$keepRiderRelativeRender(long vehicle,
			CallbackInfoReturnable<IntObjectImmutablePair<ObjectObjectImmutablePair<Vector3d, Double>>> ci) {
		if (!emtra$advancedRideFrame() || !emtra$isAlreadyRiding(vehicle)) return;

		var stock = ci.getReturnValue();
		Vector3d offset = ridingPositionCache;
		if (offset == null) {
			// A gangway transition briefly clears the live cache. Its stored coordinates
			// are percentages at that point, so keep MTR's last real camera offset.
			if (isOnGangway || (stock != null && stock.right().left() != null)) return;
			offset = new Vector3d(ridingVehicleX, ridingVehicleY, ridingVehicleZ);
		}
		ci.setReturnValue(new IntObjectImmutablePair<>(ridingVehicleCarNumber,
				new ObjectObjectImmutablePair<>(offset, ridingYawDifference)));
	}

	@Unique
	private static boolean emtra$isAlreadyRiding(long vehicle) {
		return ridingVehicleId != 0 && ridingVehicleId == vehicle;
	}

	@Unique
	private static boolean emtra$advancedRideFrame() {
		VehicleMotion.Frame frame = VehicleMotion.currentFrame();
		return frame != null && frame.usesAdvancedSway();
	}
}
