package com.egehew.emtra.render.mixin;

import com.egehew.emtra.render.VehicleMotion;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mod.data.VehicleExtension;
import org.mtr.mod.render.PositionAndRotation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * JCM rebuilds the car poses from the rail for vehicle scripts (train.lastCarPosition/lastCarRotation and the
 * door checks). With Advanced SWAY the bodies are drawn moved, so scripts that aim parts at the world, such as
 * pantographs reaching for the catenary, missed by the sway. Give them the pose the car is drawn with.
 */
@Pseudo
@Mixin(targets = "com.lx862.mtrscripting.mod.impl.mtr.vehicle.VehicleWrapper", remap = false)
public abstract class JcmVehicleWrapperSwayMixin {
	@Shadow @Final protected VehicleExtension vehicleExtension;
	@Shadow @Final protected ObjectArrayList<PositionAndRotation> posAndRotations;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void emtra$useDrawnSway(CallbackInfo ci) {
		VehicleMotion.applyDrawnSway(vehicleExtension, posAndRotations);
	}
}
