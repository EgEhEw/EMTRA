package org.mtr.altplatforms.render;

import org.joml.Matrix4f;
import org.mtr.mapping.mapper.GraphicsHolder;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Reads the live pose matrix of a GraphicsHolder's PoseStack/MatrixStack without referencing Minecraft class or
 * member names, so the same code compiles against Mojang (Forge) and Yarn (Fabric) mappings:
 * GraphicsHolder.matrixStack, PoseStack.last() and PoseStack.Pose.pose() are located by name/signature once.
 */
public final class PoseAccess {

	private static MethodHandle matrixStack;
	private static MethodHandle last;
	private static MethodHandle pose;
	private static boolean failed;

	public static Matrix4f pose(GraphicsHolder graphicsHolder) {
		if (failed || graphicsHolder == null) {
			return null;
		}
		try {
			if (pose == null) {
				resolve();
			}
			final Object stack = matrixStack.invoke(graphicsHolder);
			return stack == null ? null : (Matrix4f) pose.invoke(last.invoke(stack));
		} catch (Throwable e) {
			failed = true;
			System.err.println("[EMTRA] Pose access unavailable: " + e);
			return null;
		}
	}

	private static void resolve() throws Exception {
		final Field field = GraphicsHolder.class.getDeclaredField("matrixStack");
		field.setAccessible(true);
		matrixStack = MethodHandles.lookup().unreflectGetter(field);
		final Class<?> stackClass = field.getType();
		Class<?> poseClass = null;
		for (final Method method : stackClass.getMethods()) {
			if (method.getParameterCount() == 0 && method.getReturnType().getEnclosingClass() == stackClass) {
				poseClass = method.getReturnType();
				last = MethodHandles.publicLookup().unreflect(method);
				break;
			}
		}
		if (poseClass == null) {
			throw new IllegalStateException("PoseStack.last() not found");
		}
		for (final Method method : poseClass.getMethods()) {
			if (method.getParameterCount() == 0 && method.getReturnType() == Matrix4f.class) {
				pose = MethodHandles.publicLookup().unreflect(method);
				return;
			}
		}
		throw new IllegalStateException("PoseStack.Pose.pose() not found");
	}

	private PoseAccess() {
	}
}
