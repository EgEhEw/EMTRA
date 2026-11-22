package com.egehew.emtra.render;

import com.egehew.emtra.EmtraMod;
import org.mtr.mapping.holder.Vector3f;
import org.mtr.mapping.render.model.Face;
import org.mtr.mapping.render.vertex.Vertex;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * Splits MTR's baked model triangles along a block grid before upload. Shader packs distort the shadow map around
 * the player per vertex and fill each triangle linearly in the distorted space; Minecraft blocks are at most a block
 * wide, but a train roof can be one 14-block quad, which then bends in the shadow map and shadows itself, worst near
 * the player.
 * <p>
 * Every triangle that crosses a plane x = k, y = k or z = k (k an integer, model space in blocks) is cut there and the
 * convex pieces are fanned back into triangles. The planes are shared by the whole mesh and each cut point is computed
 * from the edge's ends in a fixed order, so two triangles sharing an edge cut it at bit-identical points: the surface
 * stays watertight without any adjacency lookup. One pass, cost proportional to the output.
 * Runs on the render thread when MTR builds a vehicle model, so it must stay fast.
 */
public final class MeshSubdivision {

	/** Grid spacing in blocks: the size of the geometry shader packs are made for. */
	public static final float GRID = 1.0F;
	private static final float EPSILON = 1e-4F;
	/** Never let one mesh grow past this many times its triangles (pathological models stay as they are). */
	private static final int MAX_GROWTH = 32;

	private static long statBefore, statAfter, statNanos;
	private static long lastLog;

	private MeshSubdivision() {
	}

	public static void subdivide(List<Vertex> vertices, List<Face> faces) {
		if (faces.isEmpty()) return;
		for (final Face face : faces) {
			if (face.vertices.length != 3) return; // only triangulated meshes
		}
		final long start = System.nanoTime();
		final int before = faces.size();
		final List<Face> output = new ArrayList<>(before * 2);
		final List<Vertex[]> polygons = new ArrayList<>();
		final List<Vertex[]> scratch = new ArrayList<>();
		final int vertexCountBefore = vertices.size();
		final IdentityHashMap<Vertex, Integer> added = new IdentityHashMap<>();

		for (final Face face : faces) {
			final Vertex a = vertices.get(face.vertices[0]), b = vertices.get(face.vertices[1]), c = vertices.get(face.vertices[2]);
			if (!crossesGrid(a, b, c)) {
				output.add(face);
				continue;
			}
			polygons.clear();
			polygons.add(new Vertex[]{a, b, c});
			for (int axis = 0; axis < 3; axis++) {
				final float min = Math.min(get(a, axis), Math.min(get(b, axis), get(c, axis)));
				final float max = Math.max(get(a, axis), Math.max(get(b, axis), get(c, axis)));
				final int first = (int) Math.floor((min + EPSILON) / GRID) + 1;
				final int last = (int) Math.ceil((max - EPSILON) / GRID) - 1;
				for (int k = first; k <= last; k++) {
					final float plane = k * GRID;
					scratch.clear();
					for (final Vertex[] polygon : polygons) split(polygon, axis, plane, scratch);
					polygons.clear();
					polygons.addAll(scratch);
				}
			}
			added.clear();
			for (final Vertex[] polygon : polygons) {
				final int[] indices = new int[polygon.length];
				for (int i = 0; i < polygon.length; i++) indices[i] = indexOf(vertices, added, polygon[i], face, a, b, c);
				// Convex pieces: a fan keeps every cut point on the piece's boundary, so neighbours still match.
				for (int i = 1; i + 1 < indices.length; i++) {
					output.add(new Face(new int[]{indices[0], indices[i], indices[i + 1]}));
				}
			}
			if (output.size() > (long) before * MAX_GROWTH) {
				// Give up on this mesh rather than stall: restore it untouched.
				while (vertices.size() > vertexCountBefore) vertices.remove(vertices.size() - 1);
				EmtraMod.LOGGER.warn("[EMTRA] Model mesh of {} triangles left unsplit (too many grid cuts)", before);
				return;
			}
		}
		faces.clear();
		faces.addAll(output);

		statBefore += before;
		statAfter += output.size();
		statNanos += System.nanoTime() - start;
		final long now = System.nanoTime();
		if (now - lastLog > 10_000_000_000L) {
			lastLog = now;
			EmtraMod.LOGGER.info("[EMTRA] Shadow-safe model split: {} -> {} triangles in {} ms so far", statBefore, statAfter, statNanos / 1_000_000);
		}
	}

	private static boolean crossesGrid(Vertex a, Vertex b, Vertex c) {
		for (int axis = 0; axis < 3; axis++) {
			final float min = Math.min(get(a, axis), Math.min(get(b, axis), get(c, axis)));
			final float max = Math.max(get(a, axis), Math.max(get(b, axis), get(c, axis)));
			if (Math.floor((min + EPSILON) / GRID) + 1 <= Math.ceil((max - EPSILON) / GRID) - 1) return true;
		}
		return false;
	}

	/** Sutherland-Hodgman split of a convex polygon by axis = plane; vertices on the plane go to both sides. */
	private static void split(Vertex[] polygon, int axis, float plane, List<Vertex[]> out) {
		boolean below = false, above = false;
		for (final Vertex v : polygon) {
			final float d = get(v, axis) - plane;
			if (d < -EPSILON) below = true;
			else if (d > EPSILON) above = true;
		}
		if (!below || !above) {
			out.add(polygon);
			return;
		}
		final List<Vertex> low = new ArrayList<>(polygon.length + 2), high = new ArrayList<>(polygon.length + 2);
		for (int i = 0; i < polygon.length; i++) {
			final Vertex p = polygon[i], q = polygon[(i + 1) % polygon.length];
			final int sp = side(get(p, axis) - plane), sq = side(get(q, axis) - plane);
			if (sp <= 0) low.add(p);
			if (sp >= 0) high.add(p);
			if (sp * sq < 0) {
				final Vertex cut = intersect(p, q, axis, plane);
				low.add(cut);
				high.add(cut);
			}
		}
		if (low.size() >= 3) out.add(low.toArray(new Vertex[0]));
		if (high.size() >= 3) out.add(high.toArray(new Vertex[0]));
	}

	private static int side(float d) {
		return d < -EPSILON ? -1 : d > EPSILON ? 1 : 0;
	}

	/** Cut point of edge p-q, computed from the ends in a canonical order so both neighbours get identical bits. */
	private static Vertex intersect(Vertex p, Vertex q, int axis, float plane) {
		if (compare(p, q) > 0) {
			final Vertex swap = p;
			p = q;
			q = swap;
		}
		final float t = (plane - get(p, axis)) / (get(q, axis) - get(p, axis));
		final Vertex m = new Vertex(p);
		final float x = p.position.getX() + (q.position.getX() - p.position.getX()) * t;
		final float y = p.position.getY() + (q.position.getY() - p.position.getY()) * t;
		final float z = p.position.getZ() + (q.position.getZ() - p.position.getZ()) * t;
		// Exactly on the plane, so the next cuts classify it as "on" for both neighbours.
		m.position = new Vector3f(axis == 0 ? plane : x, axis == 1 ? plane : y, axis == 2 ? plane : z);
		if (p.normal != null && q.normal != null) {
			final float nx = p.normal.getX() + (q.normal.getX() - p.normal.getX()) * t;
			final float ny = p.normal.getY() + (q.normal.getY() - p.normal.getY()) * t;
			final float nz = p.normal.getZ() + (q.normal.getZ() - p.normal.getZ()) * t;
			final float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
			m.normal = length > 1e-6F ? new Vector3f(nx / length, ny / length, nz / length) : p.normal;
		}
		m.u = p.u + (q.u - p.u) * t;
		m.v = p.v + (q.v - p.v) * t;
		m.color = lerpColor(p.color, q.color, t);
		return m;
	}

	private static int compare(Vertex p, Vertex q) {
		int c = Float.compare(p.position.getX(), q.position.getX());
		if (c == 0) c = Float.compare(p.position.getY(), q.position.getY());
		if (c == 0) c = Float.compare(p.position.getZ(), q.position.getZ());
		if (c == 0) c = Float.compare(p.u, q.u);
		if (c == 0) c = Float.compare(p.v, q.v);
		return c;
	}

	private static int indexOf(List<Vertex> vertices, IdentityHashMap<Vertex, Integer> added, Vertex vertex, Face face, Vertex a, Vertex b, Vertex c) {
		if (vertex == a) return face.vertices[0];
		if (vertex == b) return face.vertices[1];
		if (vertex == c) return face.vertices[2];
		return added.computeIfAbsent(vertex, key -> {
			vertices.add(key);
			return vertices.size() - 1;
		});
	}

	private static float get(Vertex vertex, int axis) {
		return axis == 0 ? vertex.position.getX() : axis == 1 ? vertex.position.getY() : vertex.position.getZ();
	}

	private static int lerpColor(int a, int b, float t) {
		int result = 0;
		for (int shift = 0; shift < 32; shift += 8) {
			final int ca = (a >>> shift) & 0xFF, cb = (b >>> shift) & 0xFF;
			result |= (Math.round(ca + (cb - ca) * t) & 0xFF) << shift;
		}
		return result;
	}
}
