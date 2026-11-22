package com.egehew.emtra.render.mixin;

import org.mtr.mod.render.PositionAndRotation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = PositionAndRotation.class, remap = false)
public interface VehiclePoseLightAccessor {
	@Mutable
	@Accessor("light")
	void emtra$setLight(int light);
}
