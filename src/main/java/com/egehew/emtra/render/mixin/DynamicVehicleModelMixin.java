package com.egehew.emtra.render.mixin;

import com.egehew.emtra.render.DoorMapping;
import org.mtr.mod.render.DynamicVehicleModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = DynamicVehicleModel.class, remap = false)
public abstract class DynamicVehicleModelMixin implements DoorMapping {

	@Unique
	private Object emtra$mappedDoorways;

	@Override
	public Object emtra$getMappedDoorways() {
		return emtra$mappedDoorways;
	}

	@Override
	public void emtra$setMappedDoorways(Object doorways) {
		emtra$mappedDoorways = doorways;
	}
}
