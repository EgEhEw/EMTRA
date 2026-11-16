package com.egehew.emtra.render.mixin;

import com.egehew.emtra.render.CatenaryCache;
import org.mtr.mod.Init;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** MSD reads each catenary segment's light at Init.newBlockPos(x1, y1, z1) right before scheduling it. */
@Mixin(value = Init.class, remap = false)
public abstract class InitBlockPosMixin {

	@Inject(method = "newBlockPos(DDD)Lorg/mtr/mapping/holder/BlockPos;", at = @At("HEAD"), require = 0, remap = false)
	private static void emtra$segmentLightPos(double x, double y, double z, CallbackInfoReturnable<?> cir) {
		CatenaryCache.onBlockPos(x, y, z);
	}
}
