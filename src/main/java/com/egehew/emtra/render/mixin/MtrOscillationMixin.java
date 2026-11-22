package com.egehew.emtra.render.mixin;

import com.egehew.emtra.render.EmtraClientConfig;
import org.mtr.mod.client.Oscillation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Oscillation.class, remap = false)
public abstract class MtrOscillationMixin {
	@Shadow private double amount;
	@Shadow private double amplitude;
	@Shadow private double scheduledAmplitude;
	@Shadow private long time;

	@Inject(method = {"tick", "startOscillation"}, at = @At("HEAD"), cancellable = true)
	private void emtra$stopStockOscillation(CallbackInfo ci) {
		if (EmtraClientConfig.ADVANCED_SWAY) {
			amount = 0;
			amplitude = 0;
			scheduledAmplitude = 0;
			time = 0;
			ci.cancel();
		}
	}

	@Inject(method = "getAmount", at = @At("HEAD"), cancellable = true)
	private void emtra$hideStockOscillation(CallbackInfoReturnable<Double> ci) {
		if (EmtraClientConfig.ADVANCED_SWAY) ci.setReturnValue(0.0);
	}
}
