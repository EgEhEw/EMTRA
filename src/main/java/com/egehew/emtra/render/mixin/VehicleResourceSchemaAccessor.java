package com.egehew.emtra.render.mixin;

import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mod.generated.resource.VehicleResourceSchema;
import org.mtr.mod.resource.VehicleModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = VehicleResourceSchema.class, remap = false)
public interface VehicleResourceSchemaAccessor {
	@Accessor(value = "id", remap = false)
	String emtra$id();

	@Accessor(value = "bogie1Models", remap = false)
	ObjectArrayList<VehicleModel> emtra$bogie1Models();

	@Accessor(value = "bogie2Models", remap = false)
	ObjectArrayList<VehicleModel> emtra$bogie2Models();
}
