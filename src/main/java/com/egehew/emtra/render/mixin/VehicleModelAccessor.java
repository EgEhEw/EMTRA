package com.egehew.emtra.render.mixin;

import org.mtr.core.serializer.JsonReader;
import org.mtr.mod.resource.CachedResource;
import org.mtr.mod.resource.VehicleModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = VehicleModel.class, remap = false)
public interface VehicleModelAccessor {
	@Accessor(value = "cachedModel", remap = false)
	CachedResource<?> emtra$cachedModel();

	@Accessor(value = "modelPropertiesJsonReader", remap = false)
	JsonReader emtra$modelPropertiesJsonReader();

	@Accessor(value = "positionDefinitionsJsonReader", remap = false)
	JsonReader emtra$positionDefinitionsJsonReader();
}
