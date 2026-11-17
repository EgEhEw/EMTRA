package com.egehew.emtra.render;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Rewrites the lift model creation in MTR's RenderLifts so it goes through {@link LiftModelCache}. Runs from the mixin
 * plugin's postApply, i.e. after every mod's mixins (fangsu redirects the same constructor call to its own model class),
 * so it handles both the plain {@code new ModelLift1(h, w, d, doubleSided)} and a redirect to a static factory.
 */
public final class LiftModelCachePatch {

	private static final String MODEL = "org/mtr/mod/model/ModelLift1";
	private static final String FACTORY_DESC = "(IIIZ)L" + MODEL + ";";
	private static final String CACHE = Type.getInternalName(LiftModelCache.class);
	private static final String PREFIX = "emtra$cachedLiftModel";

	private LiftModelCachePatch() {
	}

	public static void apply(ClassNode classNode) {
		if (!LiftModelCache.ENABLED) {
			return;
		}
		int patched = 0;
		final List<MethodNode> added = new ArrayList<>();
		for (final MethodNode method : classNode.methods) {
			if (method.name.startsWith(PREFIX) || method.instructions == null) {
				continue;
			}
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (!(insn instanceof MethodInsnNode call)) {
					continue;
				}
				if (call.getOpcode() == Opcodes.INVOKESTATIC && call.owner.equals(classNode.name) && call.desc.equals(FACTORY_DESC)) {
					// A mod redirected the constructor to a static factory merged into this class.
					final String name = PREFIX + "$" + added.size();
					added.add(factory(name, call.owner, call.name, false, call.itf));
					call.name = name;
					call.itf = false;
					patched++;
				} else if (call.getOpcode() == Opcodes.INVOKESPECIAL && call.owner.equals(MODEL) && call.name.equals("<init>") && call.desc.equals("(IIIZ)V")) {
					final AbstractInsnNode[] newDup = findNewDup(method, call);
					if (newDup == null) {
						continue;
					}
					method.instructions.remove(newDup[0]);
					method.instructions.remove(newDup[1]);
					final String name = PREFIX + "$" + added.size();
					added.add(factory(name, null, null, true, false));
					final MethodInsnNode replacement = new MethodInsnNode(Opcodes.INVOKESTATIC, classNode.name, name, FACTORY_DESC, false);
					method.instructions.set(call, replacement);
					insn = replacement;
					patched++;
				}
			}
		}
		classNode.methods.addAll(added);
		System.out.println("[EMTRA] Lift model cache: " + (patched > 0 ? patched + " lift model creation(s) now reuse cached models" : "no lift model creation found in RenderLifts, cache inactive"));
	}

	/** The NEW ModelLift1 + DUP that belong to the given constructor call; null unless it is the only one in the method. */
	private static AbstractInsnNode[] findNewDup(MethodNode method, MethodInsnNode init) {
		AbstractInsnNode found = null;
		int count = 0;
		for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn.getOpcode() == Opcodes.NEW && ((TypeInsnNode) insn).desc.equals(MODEL)) {
				found = insn;
				count++;
			}
		}
		if (count != 1 || method.instructions.indexOf(found) > method.instructions.indexOf(init)) {
			return null;
		}
		final AbstractInsnNode dup = found.getNext();
		return dup != null && dup.getOpcode() == Opcodes.DUP ? new AbstractInsnNode[]{found, dup} : null;
	}

	/** static ModelLift1 name(int h, int w, int d, boolean doubleSided): cached model, else build, store and return it. */
	private static MethodNode factory(String name, String owner, String original, boolean construct, boolean itf) {
		final MethodNode method = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, name, FACTORY_DESC, null, null);
		final InsnList code = method.instructions;
		final LabelNode build = new LabelNode();
		loadArgs(code);
		code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, CACHE, "get", "(IIIZ)Ljava/lang/Object;", false));
		code.add(new InsnNode(Opcodes.DUP));
		code.add(new JumpInsnNode(Opcodes.IFNULL, build));
		code.add(new TypeInsnNode(Opcodes.CHECKCAST, MODEL));
		code.add(new InsnNode(Opcodes.ARETURN));
		code.add(build);
		code.add(new FrameNode(Opcodes.F_FULL, 4, new Object[]{Opcodes.INTEGER, Opcodes.INTEGER, Opcodes.INTEGER, Opcodes.INTEGER}, 1, new Object[]{"java/lang/Object"}));
		code.add(new InsnNode(Opcodes.POP));
		if (construct) {
			code.add(new TypeInsnNode(Opcodes.NEW, MODEL));
			code.add(new InsnNode(Opcodes.DUP));
			loadArgs(code);
			code.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, MODEL, "<init>", "(IIIZ)V", false));
		} else {
			loadArgs(code);
			code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, original, FACTORY_DESC, itf));
		}
		code.add(new VarInsnNode(Opcodes.ASTORE, 4));
		loadArgs(code);
		code.add(new VarInsnNode(Opcodes.ALOAD, 4));
		code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, CACHE, "put", "(IIIZLjava/lang/Object;)V", false));
		code.add(new VarInsnNode(Opcodes.ALOAD, 4));
		code.add(new InsnNode(Opcodes.ARETURN));
		method.maxStack = 8;
		method.maxLocals = 5;
		return method;
	}

	private static void loadArgs(InsnList code) {
		for (int i = 0; i < 4; i++) {
			code.add(new VarInsnNode(Opcodes.ILOAD, i));
		}
	}
}
