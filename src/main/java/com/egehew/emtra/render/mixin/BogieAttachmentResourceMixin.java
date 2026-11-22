package com.egehew.emtra.render.mixin;

import com.egehew.emtra.render.BogieArticulation;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mapping.holder.Box;
import org.mtr.mod.data.VehicleExtension;
import org.mtr.mod.render.DynamicVehicleModel;
import org.mtr.mod.render.StoredMatrixTransformations;
import org.mtr.mod.resource.OptimizedModelWrapper;
import org.mtr.mod.resource.PartCondition;
import org.mtr.mod.resource.VehicleResource;
import org.mtr.mod.resource.VehicleResourceCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

@Mixin(value = VehicleResource.class, remap = false)
public abstract class BogieAttachmentResourceMixin implements BogieArticulation {
	@Shadow public abstract VehicleResourceCache getCachedVehicleResource(int car, int totalCars, boolean force);

	@Shadow
	private static Object2ObjectOpenHashMap<PartCondition, OptimizedModelWrapper> lambda$writeToOptimizedModels$24(
			Object2ObjectOpenHashMap<PartCondition, ObjectArrayList<OptimizedModelWrapper.MaterialGroupWrapper>> materials,
			Object2ObjectOpenHashMap<PartCondition, ObjectArrayList<OptimizedModelWrapper.ObjModelWrapper>> objects
	) {
		throw new AssertionError();
	}

	@Shadow
	private static void queue(Object2ObjectOpenHashMap<PartCondition, OptimizedModelWrapper> models,
			StoredMatrixTransformations matrices, VehicleExtension vehicle, int light, boolean noOpenDoorways) {
		throw new AssertionError();
	}

	// Carry the trim alongside the wheel materials through MTR's own cache construction.
	@ModifyVariable(method = "lambda$cachedVehicleResourceInitializer$18", at = @At("STORE"), index = 8)
	private Object2ObjectOpenHashMap<PartCondition, ObjectArrayList<OptimizedModelWrapper.MaterialGroupWrapper>> emtra$allocateBogieGroups1(
			Object2ObjectOpenHashMap<PartCondition, ObjectArrayList<OptimizedModelWrapper.MaterialGroupWrapper>> original) {
		return new BogieArticulation.MaterialGroups();
	}

	@ModifyVariable(method = "lambda$cachedVehicleResourceInitializer$18", at = @At("STORE"), index = 9)
	private Object2ObjectOpenHashMap<PartCondition, ObjectArrayList<OptimizedModelWrapper.MaterialGroupWrapper>> emtra$allocateBogieGroups2(
			Object2ObjectOpenHashMap<PartCondition, ObjectArrayList<OptimizedModelWrapper.MaterialGroupWrapper>> original) {
		return new BogieArticulation.MaterialGroups();
	}

	@Redirect(method = {"lambda$cachedVehicleResourceInitializer$15", "lambda$cachedVehicleResourceInitializer$16"},
			at = @At(value = "INVOKE", target = "Lorg/mtr/mod/render/DynamicVehicleModel;writeFloorsAndDoorways("
					+ "Lorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectArrayList;"
					+ "Lorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectArrayList;"
					+ "Lorg/mtr/libraries/it/unimi/dsi/fastutil/objects/Object2ObjectOpenHashMap;"
					+ "Lorg/mtr/libraries/it/unimi/dsi/fastutil/objects/Object2ObjectOpenHashMap;"
					+ "Lorg/mtr/libraries/it/unimi/dsi/fastutil/objects/Object2ObjectOpenHashMap;"
					+ "Lorg/mtr/libraries/it/unimi/dsi/fastutil/objects/Object2ObjectOpenHashMap;)V"))
	private static void emtra$cacheBogieAttachment(DynamicVehicleModel model, ObjectArrayList<Box> floors, ObjectArrayList<Box> doors,
			Object2ObjectOpenHashMap<PartCondition, ObjectArrayList<OptimizedModelWrapper.MaterialGroupWrapper>> materials,
			Object2ObjectOpenHashMap<PartCondition, ObjectArrayList<OptimizedModelWrapper.MaterialGroupWrapper>> closedMaterials,
			Object2ObjectOpenHashMap<PartCondition, ObjectArrayList<OptimizedModelWrapper.ObjModelWrapper>> objects,
			Object2ObjectOpenHashMap<PartCondition, ObjectArrayList<OptimizedModelWrapper.ObjModelWrapper>> closedObjects) {
		var seams = BogieArticulation.seams(model);
		if (closedMaterials instanceof BogieArticulation.MaterialGroups groups && seams != null) {
			if (groups.seams == null) groups.seams = seams;
			model.writeFloorsAndDoorways(floors, doors, materials, groups.attachments, objects, groups.attachmentObjects);
		} else {
			model.writeFloorsAndDoorways(floors, doors, materials, closedMaterials, objects, closedObjects);
		}
	}

	@Inject(method = "lambda$writeToOptimizedModels$24", at = @At("RETURN"), cancellable = true)
	private static void emtra$compileWithBogieAttachments(
			Object2ObjectOpenHashMap<PartCondition, ObjectArrayList<OptimizedModelWrapper.MaterialGroupWrapper>> materials,
			Object2ObjectOpenHashMap<PartCondition, ObjectArrayList<OptimizedModelWrapper.ObjModelWrapper>> objects,
			CallbackInfoReturnable<Object2ObjectOpenHashMap<PartCondition, OptimizedModelWrapper>> ci
	) {
		if (materials instanceof BogieArticulation.MaterialGroups groups
				&& (!groups.attachments.isEmpty() || !groups.attachmentObjects.isEmpty())) {
			// Build both batches in the same cache fetch. A separate fetch can be deferred by MTR's global budget.
			var attachments = lambda$writeToOptimizedModels$24(groups.attachments, groups.attachmentObjects);
			ci.setReturnValue(new BogieArticulation.OptimizedGroups(ci.getReturnValue(), attachments, groups.seams));
		}
	}

	@Override
	public void emtra$queueBogieAttachment(int bogie, Function<BogieArticulation.Seams, StoredMatrixTransformations> matrices, VehicleExtension vehicle, int light) {
		VehicleResourceCache cache = getCachedVehicleResource(0, 1, false);
		if (cache == null) return;
		var wheels = bogie == 0 ? cache.optimizedModelsBogie1 : cache.optimizedModelsBogie2;
		if (wheels instanceof BogieArticulation.OptimizedGroups groups) {
			queue(groups.attachments, matrices.apply(groups.seams), vehicle, light, true);
		}
	}
}
