package com.egehew.emtra.render.mixin;

import com.egehew.emtra.render.PidsTextGraphics;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mod.render.RenderPIDS;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = RenderPIDS.class, remap = false)
public abstract class RenderPidsMixin {

	@Redirect(method = "renderText(Lorg/mtr/mapping/mapper/GraphicsHolder;Ljava/lang/String;III)V", at = @At(value = "INVOKE", target = "Lorg/mtr/mapping/mapper/GraphicsHolder;drawText(Ljava/lang/String;IIIZI)V"), remap = false)
	private void emtra$drawWorldText(GraphicsHolder graphics, String text, int x, int y, int color, boolean shadow, int light) {
		if ((Object) graphics instanceof PidsTextGraphics batched) {
			batched.emtra$drawPidsText(text, x, y, color, shadow, light);
		} else {
			graphics.drawText(text, x, y, color, shadow, light);
		}
	}
}
