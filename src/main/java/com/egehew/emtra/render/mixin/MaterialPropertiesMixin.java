package com.egehew.emtra.render.mixin;

import com.egehew.emtra.render.EmtraClientConfig;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mapping.render.batch.MaterialProperties;
import org.mtr.mapping.render.vertex.VertexAttributeState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

/**
 * BatchManager.queue looks every model part up in a HashMap keyed by MaterialProperties, and MTR recomputes
 * Objects.hash(...) with boxing on every lookup. The only mutable field is the texture, so the hash is cached and
 * dropped whenever setTexture is called. Same value as MTR's hashCode.
 */
@Mixin(value = MaterialProperties.class, remap = false)
public abstract class MaterialPropertiesMixin {

	@Shadow(remap = false)
	private Identifier texture;
	@Shadow(remap = false)
	@Final
	public OptimizedModel.ShaderType shaderType;
	@Shadow(remap = false)
	@Final
	public VertexAttributeState vertexAttributeState;
	@Shadow(remap = false)
	@Final
	public boolean translucent;
	@Shadow(remap = false)
	@Final
	public boolean writeDepthBuf;
	@Shadow(remap = false)
	@Final
	public boolean cutoutHack;

	@Unique
	private int emtra$hash;
	@Unique
	private boolean emtra$hashed;

	/**
	 * @author EgEhEw
	 * @reason Cache the hash of an effectively immutable map key (see class comment).
	 */
	@Overwrite(remap = false)
	public int hashCode() {
		if (emtra$hashed) {
			return emtra$hash;
		}
		final int hash = Objects.hash(shaderType, texture, vertexAttributeState, translucent, writeDepthBuf, cutoutHack);
		if (EmtraClientConfig.RENDER_INSTANCING) {
			emtra$hash = hash;
			emtra$hashed = true;
		}
		return hash;
	}

	@Inject(method = "setTexture", at = @At("TAIL"), require = 0, remap = false)
	private void emtra$textureChanged(Identifier newTexture, CallbackInfo ci) {
		emtra$hashed = false;
	}
}
