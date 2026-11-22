package com.egehew.emtra.render.mixin;

import com.egehew.emtra.EmtraMod;
import com.egehew.emtra.api.VehicleSuspension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Array;

@Pseudo
@Mixin(targets = "com.lx862.mtrscripting.core.api.MTRClassShutter", remap = false)
public abstract class JcmClassShutterMixin {

	@Inject(method = "<init>", at = @At("RETURN"))
	private void emtra$registerScriptApi(CallbackInfo ci) {
		// JCM is optional. Call its public rule registration API without a hard dependency.
		try {
			Class<?> shutter = getClass();
			Class<?> rule = Class.forName("com.lx862.mtrscripting.core.api.ClassRule", false, shutter.getClassLoader());
			Object rules = Array.newInstance(rule, 1);
			Array.set(rules, 0, rule.getMethod("parse", String.class).invoke(null, VehicleSuspension.class.getName()));
			shutter.getMethod("allowClass", rules.getClass()).invoke(this, rules);
		} catch (ReflectiveOperationException | LinkageError exception) {
			EmtraMod.LOGGER.warn("Could not register the EMTRA vehicle suspension API with JCM", exception);
		}
	}
}
