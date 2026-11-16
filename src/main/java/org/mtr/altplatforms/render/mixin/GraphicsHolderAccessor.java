package org.mtr.altplatforms.render.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = GraphicsHolder.class, remap = false)
public interface GraphicsHolderAccessor {

	@Accessor(value = "matrixStack", remap = false)
	PoseStack emtra$getMatrixStack();
}
