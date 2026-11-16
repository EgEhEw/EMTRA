package org.mtr.altplatforms.render.mixin;

import java.util.List;
import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import org.mtr.altplatforms.render.MtrInstancer;

/**
 * BatchManager.drawBatch runs list.forEach(RenderCall::draw) for every material batch.
 * Calls that share a vertex array (the same rail/model part) are collapsed into one instanced draw.
 */
@Mixin(targets = "org.mtr.mapping.render.batch.BatchManager", remap = false)
public abstract class BatchDrawMixin {
	@Redirect(method = "lambda$drawBatch$2",
			  at = @At(value = "INVOKE", target = "Ljava/util/List;forEach(Ljava/util/function/Consumer;)V", remap = false),
			  require = 0, remap = false)
	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void emtra$instanced(List list, Consumer action) {
		if (!MtrInstancer.drawAll(list)) list.forEach(action);
	}
}
