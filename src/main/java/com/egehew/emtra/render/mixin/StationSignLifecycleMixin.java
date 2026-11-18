package com.egehew.emtra.render.mixin;

import com.egehew.emtra.render.StationSignRendering;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tracks loaded signs even if the block entity renderer has never drawn them. */
@Pseudo
@Mixin(targets = {"net.minecraft.world.level.block.entity.BlockEntity", "net.minecraft.class_2586", "net.minecraft.block.entity.BlockEntity"}, remap = false)
public abstract class StationSignLifecycleMixin {

	@Inject(method = {"m_142339_(Lnet/minecraft/world/level/Level;)V", "setLevel(Lnet/minecraft/world/level/Level;)V",
			"method_31662(Lnet/minecraft/class_1937;)V", "setWorld(Lnet/minecraft/world/World;)V"}, at = @At("RETURN"), remap = false)
	private void emtra$indexStationSign(CallbackInfo ci) {
		StationSignRendering.onLoad(this);
	}

	@Inject(method = {"m_7651_()V", "setRemoved()V", "method_11012()V", "markRemoved()V"}, at = @At("RETURN"), remap = false)
	private void emtra$removeStationSign(CallbackInfo ci) {
		StationSignRendering.onRemove(this);
	}
}
