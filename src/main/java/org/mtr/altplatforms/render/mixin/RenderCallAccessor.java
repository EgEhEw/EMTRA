package org.mtr.altplatforms.render.mixin;

import org.mtr.mapping.render.object.VertexArray;
import org.mtr.mapping.render.vertex.VertexAttributeState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** BatchManager$RenderCall is package-private; exposes its fields and draw(). */
@Mixin(targets = "org.mtr.mapping.render.batch.BatchManager$RenderCall", remap = false)
public interface RenderCallAccessor {
	@Accessor(value = "vertexArray", remap = false)
	VertexArray emtra$vertexArray();

	@Accessor(value = "vertexAttributeState", remap = false)
	VertexAttributeState emtra$state();

	@Invoker(value = "draw", remap = false)
	void emtra$draw();
}
