package org.mtr.altplatforms.render;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

/**
 * Reads the live pose matrix of a PoseStack without referencing obfuscated member names:
 * PoseStack.last() and PoseStack.Pose.pose() are located by signature once.
 */
public final class PoseAccess {

	private static MethodHandle last;
	private static MethodHandle pose;
	private static boolean failed;

	public static Matrix4f pose(PoseStack poseStack) {
		if (failed || poseStack == null) {
			return null;
		}
		try {
			if (last == null) {
				resolve();
			}
			return (Matrix4f) pose.invoke(last.invoke(poseStack));
		} catch (Throwable e) {
			failed = true;
			System.err.println("[EMTRA] Pose access unavailable: " + e);
			return null;
		}
	}

	private static void resolve() throws Exception {
		Class<?> poseClass = null;
		for (final Method method : PoseStack.class.getMethods()) {
			if (method.getParameterCount() == 0 && method.getReturnType().getEnclosingClass() == PoseStack.class) {
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
