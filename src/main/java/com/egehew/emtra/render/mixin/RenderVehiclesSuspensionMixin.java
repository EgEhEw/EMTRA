package com.egehew.emtra.render.mixin;

import com.egehew.emtra.render.BogieArticulation;
import com.egehew.emtra.render.SwayDebug;
import com.egehew.emtra.render.VehicleMotion;
import org.mtr.core.data.VehicleCar;
import org.mtr.libraries.com.logisticscraft.occlusionculling.util.Vec3d;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import org.mtr.mapping.holder.ClientPlayerEntity;
import org.mtr.mapping.holder.ClientWorld;
import org.mtr.mapping.holder.MinecraftClient;
import org.mtr.mapping.holder.Vector3d;
import org.mtr.mapping.mapper.OptimizedRenderer;
import org.mtr.mod.config.Client;
import org.mtr.mod.data.VehicleExtension;
import org.mtr.mod.render.PositionAndRotation;
import org.mtr.mod.render.RenderVehicles;
import org.mtr.mod.render.StoredMatrixTransformations;
import org.mtr.mod.resource.VehicleResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RenderVehicles.class, remap = false)
public abstract class RenderVehiclesSuspensionMixin {
	@Redirect(
		method = "lambda$render$14",
		at = @At(
			value = "INVOKE",
			target = "Lorg/mtr/mod/config/Client;getVehicleOscillationMultiplier()D"
		)
	)
	private static double emtra$replaceStockOscillation(
		Client config, VehicleExtension vehicle, ObjectObjectImmutablePair<?, ?> carDetails,
		Vector3d offset, Double rotation, PositionAndRotation ridingCar,
		Vector3d cameraShake, int carIndex
	) {
		VehicleMotion.Frame frame = VehicleMotion.currentFrame();
		return frame != null && frame.replaceStockOscillation(carIndex) ? 0 : config.getVehicleOscillationMultiplier();
	}

	// Shift the car origins before MTR reads them for rendering, floors, gangways and riders.
	@ModifyVariable(method = "lambda$render$16", at = @At("STORE"), index = 13)
	private static ObjectArrayList<ObjectObjectImmutablePair<VehicleCar,
		ObjectObjectImmutablePair<ObjectArrayList<PositionAndRotation>, PositionAndRotation>>> emtra$shiftCars(
		ObjectArrayList<ObjectObjectImmutablePair<VehicleCar,
			ObjectObjectImmutablePair<ObjectArrayList<PositionAndRotation>, PositionAndRotation>>> cars,
		long elapsed, ClientPlayerEntity player, MinecraftClient client, ObjectArrayList<?> cullingTasks,
		Vec3d cameraPosition, Vector3d cameraShake, boolean canRide, ClientWorld world, VehicleExtension vehicle
	) {
		VehicleMotion.Frame frame = VehicleMotion.begin(vehicle, cars);
		if (frame != null) {
			for (int car = 0; car < cars.size(); car++) {
				var original = cars.get(car);
				var anchors = original.right();
				PositionAndRotation shifted = frame.shiftBody(anchors.right(), car);
				if (shifted != anchors.right()) {
					cars.set(car, new ObjectObjectImmutablePair<>(original.left(),
						new ObjectObjectImmutablePair<>(anchors.left(), shifted)));
				}
			}
		}
		return cars;
	}

	@Inject(method = "getRenderPositionAndRotation", at = @At("RETURN"))
	private static void emtra$logRiddenCar(Vector3d offset, Double rotation, PositionAndRotation ridingCar,
			PositionAndRotation rendering, Vector3d shake, CallbackInfoReturnable<PositionAndRotation> ci) {
		if (SwayDebug.enabled() && !OptimizedRenderer.renderingShadows()) {
			SwayDebug.rendered(offset, rotation, ridingCar, rendering, ci.getReturnValue());
		}
	}

	@Inject(method = "lambda$render$16", at = @At("RETURN"))
	private static void emtra$endSuspensionFrame(CallbackInfo ci) {
		VehicleMotion.end();
	}

	@Redirect(method = "lambda$render$5", at = @At(value = "INVOKE", target =
		"Lorg/mtr/mod/render/RenderVehicles;getRenderPositionAndRotation(Lorg/mtr/mapping/holder/Vector3d;Ljava/lang/Double;Lorg/mtr/mod/render/PositionAndRotation;Lorg/mtr/mod/render/PositionAndRotation;Lorg/mtr/mapping/holder/Vector3d;)Lorg/mtr/mod/render/PositionAndRotation;"))
	private static PositionAndRotation emtra$shiftBogieAttachment(
		Vector3d cameraOffset, Double cameraRotation, PositionAndRotation rider,
		PositionAndRotation bogie, Vector3d shake,
		Vector3d offset, Double rotation, PositionAndRotation ridingCar, Vector3d cameraShake,
		VehicleResource resource, VehicleExtension vehicle, PositionAndRotation body, int carIndex,
		int[] displayIndex, boolean fromPackCreator, int bogieIndex
	) {
		PositionAndRotation unshifted = RenderVehicles.getRenderPositionAndRotation(cameraOffset, cameraRotation, rider, bogie, shake);
		VehicleMotion.Frame frame = VehicleMotion.currentFrame();
		if (frame == null) return unshifted;
		frame.wheelPosition = null;
		if (!OptimizedRenderer.hasOptimizedRendering()) return unshifted;
		PositionAndRotation attachment = frame.shiftAttachment(bogie, carIndex, bogieIndex);
		if (attachment == bogie) return unshifted;

		// JCM renders the scripted attachments with the displaced anchor. Only the native wheels use the original pose.
		frame.wheelPosition = unshifted;
		frame.useWorldOffset = cameraOffset == null;
		return RenderVehicles.getRenderPositionAndRotation(cameraOffset, cameraRotation, rider, attachment, shake);
	}

	@ModifyArg(method = "lambda$render$5", index = 1, at = @At(value = "INVOKE", target =
		"Lorg/mtr/mod/resource/VehicleResource;queueBogie(ILorg/mtr/mod/render/StoredMatrixTransformations;Lorg/mtr/mod/data/VehicleExtension;I)V"))
	private static StoredMatrixTransformations emtra$keepWheelPose(StoredMatrixTransformations jointMatrices) {
		VehicleMotion.Frame frame = VehicleMotion.currentFrame();
		if (frame == null || frame.wheelPosition == null) return jointMatrices;
		PositionAndRotation wheelPosition = frame.wheelPosition;
		frame.wheelPosition = null;
		return RenderVehicles.getStoredMatrixTransformations(frame.useWorldOffset, wheelPosition, 0);
	}

	@Inject(method = "lambda$render$5", at = @At(value = "INVOKE", shift = At.Shift.AFTER, target =
		"Lorg/mtr/mod/resource/VehicleResource;queueBogie(ILorg/mtr/mod/render/StoredMatrixTransformations;Lorg/mtr/mod/data/VehicleExtension;I)V"))
	private static void emtra$queueNativeArticulation(
		Vector3d offset, Double rotation, PositionAndRotation ridingCar, Vector3d shake,
		VehicleResource resource, VehicleExtension vehicle, PositionAndRotation body, int car,
		int[] displayIndex, boolean fromPackCreator, int bogie, PositionAndRotation absoluteBogie, CallbackInfo ci
	) {
		if ((Object) resource instanceof BogieArticulation articulation) {
			articulation.emtra$queueBogieAttachment(bogie, seams -> emtra$articulationMatrices(
					offset, rotation, ridingCar, shake, car, bogie, absoluteBogie, seams), vehicle, body.light);
		}
	}

	@Redirect(method = "lambda$render$5", at = @At(value = "INVOKE", target =
		"Lorg/mtr/mod/resource/VehicleResource;iterateBogieModels(ILorg/mtr/mod/resource/VehicleResource$ModelConsumer;)V"))
	private static void emtra$renderNativeArticulation(
		VehicleResource resource, int index, VehicleResource.ModelConsumer original,
		Vector3d offset, Double rotation, PositionAndRotation ridingCar, Vector3d shake,
		VehicleResource target, VehicleExtension vehicle, PositionAndRotation body, int car,
		int[] displayIndex, boolean fromPackCreator, int bogie, PositionAndRotation absoluteBogie
	) {
		resource.iterateBogieModels(index, (modelIndex, model) -> {
			var seams = BogieArticulation.seams(model);
			if (seams != null) {
				model.render(emtra$articulationMatrices(offset, rotation, ridingCar, shake, car, bogie, absoluteBogie, seams),
						vehicle, car, displayIndex, body.light, new ObjectArrayList<>(), fromPackCreator);
			} else {
				original.accept(modelIndex, model);
			}
		});
	}

	@Unique
	private static StoredMatrixTransformations emtra$articulationMatrices(
		Vector3d offset, Double rotation, PositionAndRotation ridingCar, Vector3d shake,
		int car, int bogie, PositionAndRotation absoluteBogie, BogieArticulation.Seams seams
	) {
		VehicleMotion.Frame frame = VehicleMotion.currentFrame();
		PositionAndRotation attachment = frame == null ? absoluteBogie : frame.shiftArticulation(absoluteBogie, car, bogie, seams);
		return RenderVehicles.getStoredMatrixTransformations(offset == null,
				RenderVehicles.getRenderPositionAndRotation(offset, rotation, ridingCar, attachment, shake), 0);
	}
}
