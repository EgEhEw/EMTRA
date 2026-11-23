package com.egehew.emtra.render.mixin;

import org.mtr.mod.generated.resource.VehicleModelSchema;
import org.mtr.mod.resource.ResourceProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = VehicleModelSchema.class, remap = false)
public interface VehicleModelSchemaAccessor {
	@Accessor(value = "modelResource", remap = false)
	String emtra$modelResource();

	@Accessor(value = "textureResource", remap = false)
	String emtra$textureResource();

	@Accessor(value = "modelPropertiesResource", remap = false)
	String emtra$modelPropertiesResource();

	@Accessor(value = "positionDefinitionsResource", remap = false)
	String emtra$positionDefinitionsResource();

	@Accessor(value = "flipTextureV", remap = false)
	boolean emtra$flipTextureV();

	@Accessor(value = "resourceProvider", remap = false)
	ResourceProvider emtra$resourceProvider();
}
