package org.mtr.altplatforms.render;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryUtil;
import org.mtr.mapping.render.batch.MaterialProperties;
import org.mtr.mapping.render.object.VertexArray;
import org.mtr.mapping.render.tool.GlStateTracker;
import org.mtr.mapping.render.tool.Utilities;
import org.mtr.mapping.render.vertex.VertexAttributeSource;
import org.mtr.mapping.render.vertex.VertexAttributeState;
import org.mtr.mapping.render.vertex.VertexAttributeType;


import org.mtr.altplatforms.render.mixin.RenderCallAccessor;

/**
 * MTR's OptimizedRenderer draws every model instance separately:
 *   vao.bind(); state.apply() (matrix as 4x glVertexAttrib4f + color + light); material.apply(); glDrawElements.
 * For instances that share a vertex array, the constant (GLOBAL) attributes are written to an
 * instance buffer with divisor 1 and drawn with a single glDrawElementsInstanced call.
 * The encoding matches VertexAttributeState.apply(): color (c>>>24..c)/255 as vec4, light/overlay as
 * ivec2((short)(v>>>16), (short)v), matrix columns in four consecutive locations.
 * Render thread only.
 */
public final class MtrInstancer {
	private static int vbo;
	private static ByteBuffer buf;
	private static boolean broken;
	private static final float[] MAT = new float[16];
	private static final java.nio.FloatBuffer MATBUF = java.nio.FloatBuffer.wrap(MAT);
	private static long statCalls, statDraws, lastLog = System.nanoTime();

	/** @return true if the whole list was drawn; false to let the original loop run. */
	public static boolean drawAll(List<?> calls) {
		if (!EmtraClientConfig.RENDER_INSTANCING || broken || calls == null || calls.size() < 2) return false;
		try {
			if (!Utilities.canUseCustomShader() || GlStateTracker.isGl4ES() || !GlStateTracker.supportVertexAttributeDivisor()) return false;
			Object first = calls.get(0);
			if (!(first instanceof RenderCallAccessor)) return false;
			MaterialProperties mp0 = ((RenderCallAccessor) first).emtra$vertexArray().materialProperties;
			if (mp0 == null || mp0.translucent) return false; // draw order matters for translucent batches
			IdentityHashMap<VertexArray, List<RenderCallAccessor>> groups = new IdentityHashMap<>();
			List<VertexArray> order = new ArrayList<>();
			for (Object o : calls) {
				RenderCallAccessor c = (RenderCallAccessor) o;
				VertexArray va = c.emtra$vertexArray();
				List<RenderCallAccessor> g = groups.get(va);
				if (g == null) {
					g = new ArrayList<>();
					groups.put(va, g);
					order.add(va);
				}
				g.add(c);
			}
			for (VertexArray va : order) {
				List<RenderCallAccessor> g = groups.get(va);
				if (g.size() < 2 || !drawInstanced(va, g)) {
					for (RenderCallAccessor c : g) c.emtra$draw();
					statDraws += g.size();
				} else {
					statDraws++;
				}
				statCalls += g.size();
			}
			log();
			return true;
		} catch (Throwable t) {
			broken = true; // never retry, fall back to MTR's own path
			System.err.println("[EMTRA] Instanced rendering disabled after an error: " + t);
			t.printStackTrace();
			return false;
		}
	}

	private static boolean drawInstanced(VertexArray va, List<RenderCallAccessor> g) {
		MaterialProperties mp = va.materialProperties;
		VertexAttributeState ms = mp == null ? null : mp.vertexAttributeState;
		VertexAttributeState s0 = g.get(0).emtra$state();
		if (s0 == null || s0.position != null || s0.normal != null || s0.textureU != null || s0.textureV != null) return false;
		boolean mat = s0.matrix4f != null && (ms == null || ms.matrix4f == null);
		boolean col = s0.color != null && (ms == null || ms.color == null);
		boolean lit = s0.lightmapUV != null && (ms == null || ms.lightmapUV == null);
		boolean ovl = s0.overlayUV != null && (ms == null || ms.overlayUV == null);
		if (!mat) return false;
		Map<VertexAttributeType, VertexAttributeSource> src = va.mapping.sources;
		if (!global(src, VertexAttributeType.MATRIX_MODEL) || (col && !global(src, VertexAttributeType.COLOR))
				|| (lit && !global(src, VertexAttributeType.UV_LIGHTMAP)) || (ovl && !global(src, VertexAttributeType.UV_OVERLAY))) {
			return false;
		}
		int n = g.size();
		for (int i = 1; i < n; i++) { // all states must use the same attribute layout
			VertexAttributeState s = g.get(i).emtra$state();
			if (s == null || s.position != null || s.normal != null || s.textureU != null || s.textureV != null
					|| (s.matrix4f != null) != (s0.matrix4f != null) || (s.color != null) != (s0.color != null)
					|| (s.lightmapUV != null) != (s0.lightmapUV != null) || (s.overlayUV != null) != (s0.overlayUV != null)) {
				return false;
			}
		}
		int stride = 64 + (col ? 16 : 0) + (lit ? 8 : 0) + (ovl ? 8 : 0);
		ByteBuffer b = ensure(stride * n);
		for (int i = 0; i < n; i++) {
			VertexAttributeState s = g.get(i).emtra$state();
			Utilities.store(s.matrix4f, MATBUF);
			for (int k = 0; k < 16; k++) b.putFloat(MAT[k]);
			if (col) {
				int c = s.color;
				b.putFloat(((c >>> 24) & 255) / 255f).putFloat(((c >>> 16) & 255) / 255f)
						.putFloat(((c >>> 8) & 255) / 255f).putFloat((c & 255) / 255f);
			}
			if (lit) {
				int v = s.lightmapUV;
				b.putInt((short) (v >>> 16)).putInt((short) v);
			}
			if (ovl) {
				int v = s.overlayUV;
				b.putInt((short) (v >>> 16)).putInt((short) v);
			}
		}
		b.flip();

		va.bind();
		if (vbo == 0) vbo = GL33.glGenBuffers();
		GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, vbo);
		GL33.glBufferData(GL33.GL_ARRAY_BUFFER, b, GL33.GL_STREAM_DRAW);
		int off = 0;
		int mloc = VertexAttributeType.MATRIX_MODEL.location;
		for (int k = 0; k < 4; k++) {
			GL33.glEnableVertexAttribArray(mloc + k);
			GL33.glVertexAttribPointer(mloc + k, 4, GL33.GL_FLOAT, false, stride, off + 16L * k);
			GL33.glVertexAttribDivisor(mloc + k, 1);
		}
		off += 64;
		int cloc = VertexAttributeType.COLOR.location, lloc = VertexAttributeType.UV_LIGHTMAP.location,
				oloc = VertexAttributeType.UV_OVERLAY.location;
		if (col) {
			GL33.glEnableVertexAttribArray(cloc);
			GL33.glVertexAttribPointer(cloc, 4, GL33.GL_FLOAT, false, stride, off);
			GL33.glVertexAttribDivisor(cloc, 1);
			off += 16;
		}
		if (lit) {
			GL33.glEnableVertexAttribArray(lloc);
			GL33.glVertexAttribIPointer(lloc, 2, GL33.GL_INT, stride, off);
			GL33.glVertexAttribDivisor(lloc, 1);
			off += 8;
		}
		if (ovl) {
			GL33.glEnableVertexAttribArray(oloc);
			GL33.glVertexAttribIPointer(oloc, 2, GL33.GL_INT, stride, off);
			GL33.glVertexAttribDivisor(oloc, 1);
		}
		if (ms != null) ms.apply(); // material constants (attributes that are not instanced)
		GL33.glDrawElementsInstanced(GL33.GL_TRIANGLES, va.indexBuffer.getVertexCount(), va.indexBuffer.indexType, 0L, n);
		// restore VAO state: these locations are normally constant (GLOBAL)
		for (int k = 0; k < 4; k++) {
			GL33.glVertexAttribDivisor(mloc + k, 0);
			GL33.glDisableVertexAttribArray(mloc + k);
		}
		if (col) { GL33.glVertexAttribDivisor(cloc, 0); GL33.glDisableVertexAttribArray(cloc); }
		if (lit) { GL33.glVertexAttribDivisor(lloc, 0); GL33.glDisableVertexAttribArray(lloc); }
		if (ovl) { GL33.glVertexAttribDivisor(oloc, 0); GL33.glDisableVertexAttribArray(oloc); }
		GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, 0);
		return true;
	}

	private static boolean global(Map<VertexAttributeType, VertexAttributeSource> src, VertexAttributeType t) {
		VertexAttributeSource s = src == null ? null : src.get(t);
		return s == VertexAttributeSource.GLOBAL;
	}

	private static ByteBuffer ensure(int bytes) {
		if (buf == null || buf.capacity() < bytes) {
			if (buf != null) MemoryUtil.memFree(buf);
			buf = MemoryUtil.memAlloc(Math.max(bytes, 1 << 16) * 3 / 2);
		}
		buf.clear();
		return buf;
	}

	private static void log() {
		long now = System.nanoTime();
		if (!EmtraClientConfig.RENDER_STATS || now - lastLog < 10_000_000_000L) return;
		lastLog = now;
		System.out.println("[EMTRA] Instanced rendering (10 s): model calls=" + statCalls + ", draw calls=" + statDraws);
		statCalls = statDraws = 0;
	}

	private MtrInstancer() {}
}
