package com.egehew.emtra.render;

import com.egehew.emtra.EmtraMod;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.BlockRenderView;
import org.mtr.mapping.holder.Box;
import org.mtr.mapping.holder.ClientChunkManager;
import org.mtr.mapping.holder.ClientWorld;
import org.mtr.mapping.holder.Frustum;
import org.mtr.mapping.holder.MinecraftClient;
import org.mtr.mapping.holder.Vector3d;
import org.mtr.mapping.holder.World;
import org.mtr.mapping.holder.WorldRenderer;
import org.mtr.mapping.mapper.BlockEntityExtension;
import org.mtr.mapping.mapper.BlockEntityRenderer;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.OptimizedRenderer;
import org.mtr.mapping.tool.HolderBase;
import org.mtr.mod.block.BlockRailwaySign;
import org.mtr.mod.block.BlockStationNameBase;
import org.mtr.mod.render.RenderRailwaySign;
import org.mtr.mod.render.RenderStationNameTall;
import org.mtr.mod.render.RenderStationNameTiled;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;

/** Schedules the original MTR sign draw calls independently of the block entity render distance. */
public final class StationSignRendering {

	private static final Map<BlockEntityExtension, Sign> SIGNS = new WeakHashMap<>();
	private static WeakReference<Object> activeWorld = new WeakReference<>(null);
	private static Method dispatcherGetter, rendererGetter;
	private static Field cullingFrustum, capturedFrustum;
	private static SignDepth signDepth;
	private static boolean drawing;
	private static boolean failed;

	private StationSignRendering() {
	}

	public static void onLoad(Object entity) {
		if (entity instanceof BlockRailwaySign.BlockEntity || entity instanceof BlockStationNameBase.BlockEntityBase) {
			final BlockEntityExtension sign = (BlockEntityExtension) entity;
			final World world = sign.getWorld2();
			if (world != null && world.isClient()) {
				synchronized (SIGNS) {
					SIGNS.computeIfAbsent(sign, key -> new Sign(key.getPos2()));
				}
			}
		}
	}

	public static void onRemove(Object entity) {
		if (entity instanceof BlockRailwaySign.BlockEntity || entity instanceof BlockStationNameBase.BlockEntityBase) {
			synchronized (SIGNS) {
				SIGNS.remove(entity);
			}
		}
	}

	public static boolean skipBlockEntityDraw(Object renderer, BlockEntityExtension entity) {
		if (drawing || activeWorld.get() == null || !supported(renderer)) {
			return false;
		}
		final World world = entity.getWorld2();
		synchronized (SIGNS) {
			return world != null && world.data == activeWorld.get() && SIGNS.containsKey(entity);
		}
	}

	public static void enqueue(GraphicsHolder graphics, Vector3d offset) {
		if (failed) {
			return;
		}
		final MinecraftClient client = MinecraftClient.getInstance();
		final ClientWorld world = client.getWorldMapped();
		if (world == null) {
			activeWorld.clear();
			return;
		}

		synchronized (SIGNS) {
			if (SIGNS.isEmpty()) {
				return;
			}
			try {
				final Object dispatcher = dispatcher(client);
				final Frustum frustum = frustum(client);
				if (dispatcher == null || frustum == null) {
					activeWorld.clear();
					return;
				}
				if (activeWorld.get() != world.data) {
					// Drain sign calls already queued by the original renderer on the first pass.
					activeWorld = new WeakReference<>(world.data);
					return;
				}
				final ClientChunkManager chunks = world.getChunkManager();
				final BlockRenderView lightView = BlockRenderView.cast(world);
				final Vector3d camera = OptimizedRenderer.renderingShadows() ? null : client.getGameRendererMapped().getCamera().getPos();
				drawing = true;
				try {
					for (final Map.Entry<BlockEntityExtension, Sign> entry : SIGNS.entrySet()) {
						final BlockEntityExtension entity = entry.getKey();
						final Sign sign = entry.getValue();
						final World entityWorld = entity.getWorld2();
						if (entityWorld != null && entityWorld.data == world.data && !entity.isRemoved2()
								&& chunks.isChunkLoaded(sign.x >> 4, sign.z >> 4) && frustum.isVisible(sign.bounds)) {
							draw(dispatcher, entity, sign, lightView, graphics, offset, camera);
						}
					}
				} finally {
					drawing = false;
				}
			} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
				activeWorld.clear();
				failed = true;
				EmtraMod.LOGGER.warn("Using MTR's normal station sign rendering: {}", e.toString());
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static void draw(Object dispatcher, BlockEntityExtension entity, Sign sign, BlockRenderView lightView,
							 GraphicsHolder graphics, Vector3d offset, Vector3d camera) throws ReflectiveOperationException {
		if (rendererGetter == null) {
			rendererGetter = method(dispatcher.getClass(), 1, "m_112265_", "method_3550", "getRenderer", "get");
		}
		final Object renderer = rendererGetter.invoke(dispatcher, entity);
		if (!supported(renderer)) {
			return;
		}
		graphics.push();
		try {
			if (camera != null && renderer.getClass() == RenderRailwaySign.class) {
				final double dx = sign.x + 0.5 - camera.getXMapped();
				final double dy = sign.y + 0.5 - camera.getYMapped();
				final double dz = sign.z + 0.5 - camera.getZMapped();
				final float bias = depthBias(Math.sqrt(dx * dx + dy * dy + dz * dz));
				if (bias > 0) {
					signDepth = new SignDepth(Thread.currentThread(), camera, bias);
				}
			}
			graphics.translate(sign.x - offset.getXMapped(), sign.y - offset.getYMapped(), sign.z - offset.getZMapped());
			((BlockEntityRenderer<BlockEntityExtension>) renderer).render(entity, 0, graphics,
					WorldRenderer.getLightmapCoordinates(lightView, sign.pos), 0);
		} finally {
			signDepth = null;
			graphics.pop();
		}
	}

	static float depthBias(double distance) {
		if (distance <= 32) {
			return 0;
		}
		// Bound each layer's movement so distant lettering stays close to the sign and its surroundings.
		return (float) Math.min(0.2 / distance, (distance - 32) * 0.000005);
	}

	/** MTR schedules the backing plate without priority and its route icons and lettering with priority. */
	public static BiConsumer<GraphicsHolder, Vector3d> separateSignLayers(boolean priority, BiConsumer<GraphicsHolder, Vector3d> draw) {
		final SignDepth depth = signDepth;
		if (depth == null || depth.thread() != Thread.currentThread() || draw instanceof BiasedSignLayer) {
			return draw;
		}
		return new BiasedSignLayer(draw, depth.camera(), 1 - depth.bias() * (priority ? 2 : 1));
	}

	private record SignDepth(Thread thread, Vector3d camera, float bias) {
	}

	private record BiasedSignLayer(BiConsumer<GraphicsHolder, Vector3d> draw, Vector3d camera, float scale)
			implements BiConsumer<GraphicsHolder, Vector3d> {
		@Override
		public void accept(GraphicsHolder graphics, Vector3d offset) {
			final double x = camera.getXMapped() - offset.getXMapped();
			final double y = camera.getYMapped() - offset.getYMapped();
			final double z = camera.getZMapped() - offset.getZMapped();
			graphics.push();
			try {
				// Scaling around the camera changes depth and preserves the perspective projection.
				graphics.translate(x, y, z);
				graphics.scale(scale, scale, scale);
				graphics.translate(-x, -y, -z);
				draw.accept(graphics, offset);
			} finally {
				graphics.pop();
			}
		}
	}

	private static boolean supported(Object renderer) {
		if (renderer == null) {
			return false;
		}
		final Class<?> type = renderer.getClass();
		return type == RenderRailwaySign.class || type == RenderStationNameTiled.class || type == RenderStationNameTall.class;
	}

	private static Object dispatcher(MinecraftClient client) throws ReflectiveOperationException {
		if (dispatcherGetter == null) {
			dispatcherGetter = method(client.data.getClass(), 0, "m_167982_", "method_31975", "getBlockEntityRenderDispatcher");
		}
		return dispatcherGetter.invoke(client.data);
	}

	private static Frustum frustum(MinecraftClient client) throws ReflectiveOperationException {
		final Object worldRenderer = client.getWorldRendererMapped().data;
		if (cullingFrustum == null) {
			cullingFrustum = field(worldRenderer.getClass(), "f_172938_", "field_27740", "cullingFrustum", "frustum");
			capturedFrustum = field(worldRenderer.getClass(), "f_109442_", "field_4056", "capturedFrustum");
		}
		final Object captured = capturedFrustum.get(worldRenderer);
		final Object current = captured == null ? cullingFrustum.get(worldRenderer) : captured;
		return current == null ? null : Frustum.cast(new HolderBase<>(current) {});
	}

	private static Field field(Class<?> type, String... names) throws NoSuchFieldException {
		for (final String name : names) {
			try {
				final Field field = type.getDeclaredField(name);
				field.setAccessible(true);
				return field;
			} catch (NoSuchFieldException ignored) {
			}
		}
		throw new NoSuchFieldException(type.getName() + "." + names[0]);
	}

	private static Method method(Class<?> type, int params, String... names) throws NoSuchMethodException {
		for (final String name : names) {
			for (final Method method : type.getMethods()) {
				if (method.getName().equals(name) && method.getParameterCount() == params) {
					return method;
				}
			}
		}
		throw new NoSuchMethodException(type.getName() + "." + names[0]);
	}

	private static final class Sign {
		private final BlockPos pos;
		private final int x, y, z;
		private final Box bounds;

		private Sign(BlockPos pos) {
			this.pos = pos;
			x = pos.getX();
			y = pos.getY();
			z = pos.getZ();
			// A railway sign's anchor can be at either end of a multiple-block sign.
			bounds = new Box(x - 7, y - 3, z - 7, x + 8, y + 4, z + 8);
		}
	}
}
