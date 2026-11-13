package org.mtr.altplatforms.compat;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Disables only Magic's competing routing hooks, leaving its UI and timetable intact. */
public final class AltPlatformsMixinPlugin implements IMixinConfigPlugin {

	private static final String MAGIC_PACKAGE = "org.justnoone.jme.mixin.";
	private static final Map<String, List<String>> DISABLED_CALLBACKS = Map.of(
			"VehicleAlternativePlatformRerouteMixin", List.of("jme$assignPlatformEarly", "jme$rerouteIfStuckBeforePlatform"),
			"DepotAlternativePlatformMixin", List.of("jme$applyAlternativePlatforms"),
			"SidingDivertedArrivalsMixin", List.of("jme$rememberListSize", "jme$dropDivertedArrivals"),
			"ArrivalsRequestAlternativePlatformMixin", List.of("jme$expandRequestedPlatforms", "jme$mirrorDivertedPlatforms"));
	private boolean magicPresent;

	@Override
	public void onLoad(String mixinPackage) {
		// Inspect the resource without loading a game class before Mixin transforms it.
		try (InputStream stream = MixinService.getService().getResourceAsStream("org/justnoone/jme/rail/AlternativePlatformRegistry.class")) {
			magicPresent = stream != null;
		} catch (IOException exception) {
			throw new IllegalStateException("Could not check for MAGIC MTR", exception);
		}
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		if (mixinClassName.endsWith(".DashboardScreenMixin")) {
			return !magicPresent;
		}
		return !mixinClassName.endsWith(".ArrivalsRequestCompatMixin") || magicPresent;
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
		if (!magicPresent) {
			return;
		}
		// Mixin has already merged Magic's higher-priority hooks. Identify their owner
		// from MixinMerged, rather than depending on generated handler name prefixes.
		for (final MethodNode method : targetClass.methods) {
			final String owner = getMixinOwner(method);
			if (!owner.startsWith(MAGIC_PACKAGE)) {
				continue;
			}
			final String magicMixin = owner.substring(MAGIC_PACKAGE.length());
			final List<String> callbacks = DISABLED_CALLBACKS.get(magicMixin);
			if (callbacks != null && callbacks.stream().anyMatch(method.name::endsWith) && method.desc.endsWith(")V")) {
				method.instructions.clear();
				method.instructions.add(new InsnNode(Opcodes.RETURN));
				method.tryCatchBlocks.clear();
				if (method.localVariables != null) {
					method.localVariables.clear();
				}
				method.visibleLocalVariableAnnotations = null;
				method.invisibleLocalVariableAnnotations = null;
			} else if (magicMixin.equals("SidingDynamicPlatformRerouteMixin")) {
				// Keep its startUp redirect's normal fallback. A singleton selection
				// prevents only Magic's dynamic first-platform replacement.
				for (final AbstractInsnNode instruction : method.instructions) {
					if (instruction instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESTATIC
							&& call.owner.equals("org/justnoone/jme/rail/AlternativePlatformRegistry")
							&& call.name.equals("getCandidatePlatformIds")) {
						call.owner = "org/mtr/altplatforms/compat/MagicMtrCompat";
						call.name = "getPrimaryPlatformIds";
					}
				}
			}
		}
	}

	private static String getMixinOwner(MethodNode method) {
		if (method.visibleAnnotations != null) {
			for (final AnnotationNode annotation : method.visibleAnnotations) {
				if (annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;") && annotation.values != null) {
					for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
						if (annotation.values.get(i).equals("mixin")) {
							return (String) annotation.values.get(i + 1);
						}
					}
				}
			}
		}
		return "";
	}

	@Override public String getRefMapperConfig() { return null; }
	@Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
	@Override public List<String> getMixins() { return null; }
	@Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
}
