package com.egehew.emtra.render;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.Map;

/**
 * Uses the runtime Minecraft names from MTR's string drawing method. The font call and its arguments
 * are preserved. PIDS gives it the world buffer so Minecraft and shader mods can flush the text with
 * other world geometry.
 */
public final class PidsTextPatch {

	private static final String DRAW_DESC = "(Ljava/lang/String;IIIZI)V";
	private static final String BRIDGE = "emtra$drawPidsText";

	private PidsTextPatch() {
	}

	public static boolean apply(ClassNode holder) {
		final MethodNode draw = method(holder, "drawText");
		final MethodNode bridge = method(holder, BRIDGE);
		final FieldNode matrix = field(holder, "matrixStack");
		final FieldNode gui = field(holder, "drawContext");
		final FieldNode buffer = field(holder, "vertexConsumerProvider");
		if (draw == null || bridge == null || matrix == null || gui == null || buffer == null) {
			return false;
		}

		final MethodInsnNode font = findFontCall(draw, buffer.desc);
		if (font == null || font.getNext() == null || font.getNext().getOpcode() != Opcodes.POP) {
			return false;
		}
		final VarInsnNode store = findBufferStore(font);
		if (store == null || store.var != 7) {
			return false;
		}

		final InsnList code = new InsnList();
		final LabelNode fallback = new LabelNode();
		checkField(code, holder, matrix, Opcodes.IFNULL, fallback);
		checkField(code, holder, gui, Opcodes.IFNONNULL, fallback);
		checkField(code, holder, buffer, Opcodes.IFNULL, fallback);
		readField(code, holder, buffer);
		code.add(new VarInsnNode(Opcodes.ASTORE, store.var));

		boolean loadsBuffer = false;
		for (AbstractInsnNode insn = store.getNext(); insn != font.getNext(); insn = insn.getNext()) {
			// Only copy the straight-line font call and its arguments. Preserve the bridge on other layouts.
			if (insn.getOpcode() < 0) {
				continue;
			}
			if (insn instanceof JumpInsnNode) {
				return false;
			}
			if (insn instanceof VarInsnNode local) {
				if (local.getOpcode() < Opcodes.ILOAD || local.getOpcode() > Opcodes.ALOAD || local.var > store.var) {
					return false;
				}
				loadsBuffer |= local.var == store.var && local.getOpcode() == Opcodes.ALOAD;
			}
			code.add(insn.clone(Map.of()));
		}
		if (!loadsBuffer) {
			return false;
		}
		code.add(new InsnNode(Opcodes.POP));
		code.add(new InsnNode(Opcodes.RETURN));

		code.add(fallback);
		code.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
		code.add(new VarInsnNode(Opcodes.ALOAD, 0));
		code.add(new VarInsnNode(Opcodes.ALOAD, 1));
		for (int i = 2; i <= 6; i++) {
			code.add(new VarInsnNode(Opcodes.ILOAD, i));
		}
		code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, holder.name, draw.name, draw.desc, false));
		code.add(new InsnNode(Opcodes.RETURN));

		bridge.instructions = code;
		bridge.tryCatchBlocks.clear();
		bridge.localVariables = null;
		bridge.maxStack = Math.max(draw.maxStack, 7);
		bridge.maxLocals = store.var + 1;
		return true;
	}

	private static MethodInsnNode findFontCall(MethodNode draw, String bufferDesc) {
		MethodInsnNode result = null;
		for (final AbstractInsnNode insn : draw.instructions) {
			if (insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKEVIRTUAL
					&& call.desc.startsWith("(Ljava/lang/String;FFIZLorg/joml/Matrix4f;" + bufferDesc)
					&& Type.getReturnType(call.desc).equals(Type.INT_TYPE)) {
				if (result != null) {
					return null;
				}
				result = call;
			}
		}
		return result;
	}

	private static VarInsnNode findBufferStore(MethodInsnNode font) {
		for (AbstractInsnNode insn = font.getPrevious(); insn != null; insn = insn.getPrevious()) {
			if (insn instanceof VarInsnNode local && local.getOpcode() == Opcodes.ASTORE) {
				return local;
			}
			if (insn instanceof JumpInsnNode) {
				return null;
			}
		}
		return null;
	}

	private static FieldNode field(ClassNode holder, String name) {
		return holder.fields.stream().filter(field -> field.name.equals(name) && field.desc.startsWith("L")).findFirst().orElse(null);
	}

	private static MethodNode method(ClassNode holder, String name) {
		return holder.methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(DRAW_DESC)).findFirst().orElse(null);
	}

	private static void checkField(InsnList code, ClassNode holder, FieldNode field, int opcode, LabelNode fallback) {
		readField(code, holder, field);
		code.add(new JumpInsnNode(opcode, fallback));
	}

	private static void readField(InsnList code, ClassNode holder, FieldNode field) {
		code.add(new VarInsnNode(Opcodes.ALOAD, 0));
		code.add(new FieldInsnNode(Opcodes.GETFIELD, holder.name, field.name, field.desc));
	}
}
