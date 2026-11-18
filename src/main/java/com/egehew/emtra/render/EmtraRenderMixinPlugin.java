package com.egehew.emtra.render;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class EmtraRenderMixinPlugin implements IMixinConfigPlugin {

	@Override
	public void onLoad(String mixinPackage) {
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return true;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
		if (mixinClassName.endsWith(".GraphicsHolderMixin")) {
			try {
				PidsTextPatch.apply(targetClass);
			} catch (RuntimeException e) {
				System.err.println("[EMTRA] Could not batch PIDS text: " + e);
			}
		}
		// Called after all mods' mixins were applied to the class (see LiftModelCachePatch).
		if (mixinClassName.endsWith(".RenderLiftsModelCacheMixin")) {
			try {
				LiftModelCachePatch.apply(targetClass);
			} catch (Throwable t) {
				System.err.println("[EMTRA] Lift model cache patch failed, lifts render as before: " + t);
			}
		}
	}
}
