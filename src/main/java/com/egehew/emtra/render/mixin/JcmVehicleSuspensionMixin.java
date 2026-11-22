package com.egehew.emtra.render.mixin;

import com.egehew.emtra.render.VehicleMotion;
import org.mtr.mod.data.VehicleExtension;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Arrays;

@Pseudo
@Mixin(targets = "com.lx862.mtrscripting.mod.impl.mtr.vehicle.VehicleScriptContext", remap = false)
public abstract class JcmVehicleSuspensionMixin implements VehicleMotion.ScriptContext {

	@Shadow @Final private VehicleExtension vehicleExtension;
	@Shadow @Final private int[] myCars;
	@Unique private int[] emtra$assignedCars;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void emtra$captureAssignedCars(CallbackInfo ci) {
		emtra$assignedCars = Arrays.copyOf(myCars, myCars.length);
	}

	@Override
	public boolean emtra$submitSuspension(double[] bodies, double[] attachments, boolean replaceStockOscillation) {
		return emtra$assignedCars != null &&
				VehicleMotion.publish(vehicleExtension, emtra$assignedCars, bodies, attachments, replaceStockOscillation);
	}
}
