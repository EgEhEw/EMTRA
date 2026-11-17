package com.egehew.emtra.render.mixin;

import org.mtr.mod.client.VehicleRidingMovement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = VehicleRidingMovement.class, remap = false)
public interface VehicleRidingMovementAccessor {
	@Accessor(value = "ridingVehicleId", remap = false)
	static long emtra$getRidingVehicleId() {
		throw new AssertionError();
	}
}
