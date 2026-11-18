package com.egehew.emtra.render.mixin;

import com.egehew.emtra.render.StationSignRendering;
import org.mtr.mapping.mapper.BlockEntityExtension;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mod.render.RenderRailwaySign;
import org.mtr.mod.render.RenderStationNameBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = {RenderRailwaySign.class, RenderStationNameBase.class}, remap = false)
public abstract class StationSignDrawMixin {

	@Inject(method = {
			"render(Lorg/mtr/mod/block/BlockRailwaySign$BlockEntity;FLorg/mtr/mapping/mapper/GraphicsHolder;II)V",
			"render(Lorg/mtr/mod/block/BlockStationNameBase$BlockEntityBase;FLorg/mtr/mapping/mapper/GraphicsHolder;II)V"
	}, at = @At("HEAD"), cancellable = true, remap = false)
	private void emtra$avoidDuplicateSign(@Coerce Object entity, float tickDelta, GraphicsHolder graphics, int light, int overlay, CallbackInfo ci) {
		if (entity instanceof BlockEntityExtension sign && StationSignRendering.skipBlockEntityDraw(this, sign)) {
			ci.cancel();
		}
	}
}
