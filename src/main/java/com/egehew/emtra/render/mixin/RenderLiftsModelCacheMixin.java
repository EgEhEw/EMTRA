package com.egehew.emtra.render.mixin;

import org.mtr.mod.render.RenderLifts;
import org.spongepowered.asm.mixin.Mixin;

/** Marker so the mixin plugin's postApply runs for RenderLifts; the rewrite itself is LiftModelCachePatch. */
@Mixin(value = RenderLifts.class, remap = false)
public abstract class RenderLiftsModelCacheMixin {
}
