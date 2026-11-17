package com.egehew.emtra.render.mixin;

import com.egehew.emtra.sound.SppPermeationFix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** See {@link SppPermeationFix}. */
@Pseudo
@Mixin(targets = "com.redsmods.sound_physics_perfected.RaycastingHelper", remap = false)
public abstract class SppRaycastingMixin {

	@Inject(method = "countBlocksBetween", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
	private static void emtra$realBlockCount(@Coerce Object world, @Coerce Object start, @Coerce Object end, @Coerce Object player, CallbackInfoReturnable<Double> cir) {
		final double count = cir.getReturnValueD();
		final double adjusted = SppPermeationFix.adjust(world, start, end, count);
		if (adjusted != count) {
			cir.setReturnValue(adjusted);
		}
	}
}
