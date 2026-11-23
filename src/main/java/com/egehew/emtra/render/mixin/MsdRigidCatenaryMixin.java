package com.egehew.emtra.render.mixin;

import com.egehew.emtra.render.CatenaryCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** MSD (MTR Station Decoration) rigid catenary: drawn from CatenaryCache when cached. Skipped when MSD is absent. */
@Pseudo
@Mixin(targets = "top.mcmtr.core.data.RigidCatenaryMath", remap = false)
public abstract class MsdRigidCatenaryMixin {

	private static final String RENDER = "render(Ltop/mcmtr/core/data/RigidCatenaryMath$RenderRigidCatenary;)V";

	@Inject(method = RENDER, at = @At("HEAD"), cancellable = true, require = 0, remap = false)
	private void emtra$cachedCatenary(@Coerce Object callback, CallbackInfo ci) {
		if (CatenaryCache.beforeRender(this, callback)) {
			ci.cancel();
		}
	}

	@Inject(method = RENDER, at = @At("RETURN"), require = 0, remap = false)
	private void emtra$afterCatenary(@Coerce Object callback, CallbackInfo ci) {
		CatenaryCache.afterRender(callback);
	}
}
