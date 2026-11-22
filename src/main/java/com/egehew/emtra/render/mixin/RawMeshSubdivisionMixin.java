package com.egehew.emtra.render.mixin;

import com.egehew.emtra.render.EmtraClientConfig;
import com.egehew.emtra.render.MeshSubdivision;
import org.mtr.mapping.render.model.Face;
import org.mtr.mapping.render.model.Mesh;
import org.mtr.mapping.render.model.RawMesh;
import org.mtr.mapping.render.vertex.Vertex;
import org.mtr.mapping.render.vertex.VertexAttributeMapping;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Splits long triangles before MTR uploads a baked model (see MeshSubdivision). */
@Mixin(value = RawMesh.class, remap = false)
public abstract class RawMeshSubdivisionMixin {
	@Shadow @Final public List<Vertex> vertices;
	@Shadow @Final public List<Face> faces;

	@Inject(method = "upload(Lorg/mtr/mapping/render/model/Mesh;Lorg/mtr/mapping/render/vertex/VertexAttributeMapping;)V",
			at = @At("HEAD"), require = 0, remap = false)
	private void emtra$subdivide(Mesh mesh, VertexAttributeMapping mapping, CallbackInfo ci) {
		if (EmtraClientConfig.SUBDIVIDE_MODELS) {
			MeshSubdivision.subdivide(vertices, faces);
		}
	}
}
