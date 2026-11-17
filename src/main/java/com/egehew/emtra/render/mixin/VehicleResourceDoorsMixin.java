package com.egehew.emtra.render.mixin;

import com.egehew.emtra.render.DoorMapping;
import com.egehew.emtra.render.EmtraClientConfig;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mapping.holder.Box;
import org.mtr.mod.render.DynamicVehicleModel;
import org.mtr.mod.resource.VehicleModel;
import org.mtr.mod.resource.VehicleResource;
import org.mtr.mod.resource.VehicleResourceCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MTR fix: train doors stuck closed (no animation) while the doorway is open and walkable.
 * <p>
 * MTR maps each door part to its doorway (ModelPropertiesPart.mapDoors) only while building the per-car
 * VehicleResourceCache. The DynamicVehicleModel (VehicleModel.cachedModel) is a separate CachedResource that
 * expires 60 s after it was last rendered. When something keeps the car cache alive without rendering the car
 * (for example FangSu's door side observer calls getCachedVehicleResource for every vehicle, visible or not),
 * the model expires and is rebuilt on its own: its door parts then have no doorway, ModelPropertiesPart.renderNormal
 * never matches them against the open doorways and draws them at the closed position. Only F3+T fixed it.
 * <p>
 * Before the models of a car are drawn, any model whose doors were not mapped to the current doorway list is
 * mapped again, exactly as MTR does it. Normally this is one reference comparison per model.
 */
@Mixin(value = VehicleResource.class, remap = false)
public abstract class VehicleResourceDoorsMixin {

	@Shadow(remap = false)
	public abstract VehicleResourceCache getCachedVehicleResource(int carNumber, int totalCars, boolean force);

	@Shadow(remap = false)
	private ObjectArrayList<VehicleModel> getAllModels(int carNumber, int totalCars) {
		throw new AssertionError();
	}

	@Unique
	private static boolean emtra$doorFixBroken;

	@Inject(method = "iterateModels(IILorg/mtr/mod/resource/VehicleResource$ModelConsumer;)V", at = @At("HEAD"), require = 0, remap = false)
	private void emtra$remapDoors(int carNumber, int totalCars, VehicleResource.ModelConsumer modelConsumer, CallbackInfo ci) {
		if (emtra$doorFixBroken) {
			return;
		}
		try {
			final VehicleResourceCache cache = getCachedVehicleResource(carNumber, totalCars, false);
			if (cache == null || cache.doorways == null) {
				return;
			}
			final ObjectArrayList<VehicleModel> models = getAllModels(carNumber, totalCars);
			for (int i = 0; i < models.size(); i++) {
				final VehicleModel vehicleModel = models.get(i);
				if (vehicleModel == null) {
					continue;
				}
				final Object data = ((VehicleModelAccessor) (Object) vehicleModel).emtra$cachedModel().getData(false);
				if (!(data instanceof DynamicVehicleModel) || !(data instanceof DoorMapping)) {
					continue;
				}
				final DoorMapping mapping = (DoorMapping) data;
				if (mapping.emtra$getMappedDoorways() == cache.doorways) {
					continue;
				}
				final ObjectArrayList<Box> doorways = new ObjectArrayList<>(cache.doorways);
				((DynamicVehicleModel) data).modelProperties.iterateParts(part -> ((ModelPropertiesPartInvoker) (Object) part).emtra$mapDoors(doorways));
				mapping.emtra$setMappedDoorways(cache.doorways);
				if (EmtraClientConfig.RENDER_STATS) {
					System.out.println("[EMTRA] Remapped train doors for car " + carNumber + "/" + totalCars + " model " + i);
				}
			}
		} catch (Throwable t) {
			emtra$doorFixBroken = true;
			System.err.println("[EMTRA] Train door remap disabled: " + t);
		}
	}
}
