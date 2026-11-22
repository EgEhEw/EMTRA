package com.egehew.emtra.render;

import com.egehew.emtra.EmtraMod;
import org.mtr.mapping.holder.Camera;
import org.mtr.mapping.holder.ClientPlayerEntity;
import org.mtr.mapping.holder.Entity;
import org.mtr.mapping.holder.MinecraftClient;
import org.mtr.mapping.holder.Vector3d;
import org.mtr.mapping.mapper.EntityHelper;
import org.mtr.mod.render.PositionAndRotation;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Advanced SWAY debug log ({@link EmtraClientConfig#SWAY_DEBUG}): while the player rides a vehicle, writes one line per
 * rendered frame to logs/emtra-sway.log with the rail pose, the swayed pose, MTR's rider offset, the camera, the player
 * and where the ridden car was actually drawn. Frame-to-frame jumps in these values locate visible shaking.
 * Render thread only.
 */
public final class SwayDebug {

	private static final Path FILE = Paths.get("logs", "emtra-sway.log");
	private static final long MAX_BYTES = 40L * 1024 * 1024;
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

	private static BufferedWriter writer;
	private static long written;
	private static boolean broken;
	private static long lastFrame;
	private static final StringBuilder LINE = new StringBuilder(512);

	private static PositionAndRotation ridingCar;
	private static PositionAndRotation renderedCar;
	private static Vector3d renderOffset;
	private static Double renderRotation;
	private static long renderedAt;

	private SwayDebug() {
	}

	public static boolean enabled() {
		return EmtraClientConfig.SWAY_DEBUG && !broken;
	}

	/** RenderVehicles.getRenderPositionAndRotation for the ridden car's own body. */
	public static void rendered(Vector3d offset, Double rotation, PositionAndRotation riding, PositionAndRotation rendering, PositionAndRotation result) {
		if (!enabled() || offset == null || riding == null || rendering != riding) return;
		ridingCar = riding;
		renderedCar = result;
		renderOffset = offset;
		renderRotation = rotation;
		renderedAt = System.nanoTime();
	}

	/** VehicleRidingMovement.movePlayer for the ridden car, before MTR moves the player. */
	public static void frame(long vehicleId, int car, PositionAndRotation pose, boolean gangway) {
		if (!enabled()) return;
		try {
			MinecraftClient client = MinecraftClient.getInstance();
			ClientPlayerEntity player = client.getPlayerMapped();
			if (player == null) return;
			Camera camera = client.getGameRendererMapped().getCamera();
			Vector3d cameraPos = camera.getPos();
			Vector3d playerPos = player.getPos();
			VehicleMotion.Frame frame = VehicleMotion.currentFrame();
			PositionAndRotation original = frame == null ? null : frame.originalBody(car);
			long now = System.nanoTime();
			double dt = lastFrame == 0 ? 0 : (now - lastFrame) / 1e6;
			lastFrame = now;

			StringBuilder b = LINE;
			b.setLength(0);
			b.append(LocalTime.now().format(TIME)).append(" dt=").append(f(dt, 2))
					.append(" v=").append(vehicleId).append(" car=").append(car).append(gangway ? " GANGWAY" : "");
			if (original != null) {
				b.append(" rail=").append(xyz(original.position.x, original.position.y, original.position.z))
						.append(" yaw=").append(f(Math.toDegrees(original.yaw), 5))
						.append(" pitch=").append(f(Math.toDegrees(original.pitch), 5))
						.append(" Lrail=").append(Integer.toHexString(original.light));
			}
			b.append(" sway=");
			if (original != null) {
				b.append(xyz(pose.position.x - original.position.x, pose.position.y - original.position.y, pose.position.z - original.position.z))
						.append(" dyaw=").append(f(Math.toDegrees(pose.yaw - original.yaw), 5))
						.append(" dpitch=").append(f(Math.toDegrees(pose.pitch - original.pitch), 5));
			} else {
				b.append(xyz(pose.position.x, pose.position.y, pose.position.z)).append(" yaw=").append(f(Math.toDegrees(pose.yaw), 5));
			}
			b.append(" L=").append(Integer.toHexString(pose.light));
			if (renderedCar != null && ridingCar == pose && now - renderedAt < 200_000_000L) {
				// Rider-relative rendering is camera-relative: add the camera to get the drawn world position.
				b.append(" off=").append(xyz(renderOffset.getXMapped(), renderOffset.getYMapped(), renderOffset.getZMapped()))
						.append(renderRotation == null ? " rot=-" : " rot=" + f(Math.toDegrees(renderRotation), 4))
						.append(" drawErr=").append(xyz(cameraPos.getXMapped() + renderedCar.position.x - pose.position.x,
								cameraPos.getYMapped() + renderedCar.position.y - pose.position.y,
								cameraPos.getZMapped() + renderedCar.position.z - pose.position.z))
						.append(" drawYawErr=").append(f(Math.toDegrees(Math.IEEEremainder(renderedCar.yaw - pose.yaw, 2 * Math.PI)), 5));
			} else {
				b.append(" off=none");
			}
			b.append(" cam=").append(xyz(cameraPos.getXMapped(), cameraPos.getYMapped(), cameraPos.getZMapped()))
					.append(" camYaw=").append(f(camera.getYaw(), 4)).append(" camPitch=").append(f(camera.getPitch(), 3))
					.append(" ply=").append(xyz(playerPos.getXMapped(), playerPos.getYMapped(), playerPos.getZMapped()))
					.append(" plyYaw=").append(f(EntityHelper.getYaw(new Entity(player.data)), 4))
					.append('\n');
			write(b);
		} catch (Throwable t) {
			broken = true;
			EmtraMod.LOGGER.warn("[EMTRA] Sway debug log disabled", t);
		}
	}

	private static void write(CharSequence text) throws IOException {
		if (writer == null) {
			Files.createDirectories(FILE.getParent());
			writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
			written = 0;
			EmtraMod.LOGGER.info("[EMTRA] Sway debug log: {}", FILE.toAbsolutePath());
		}
		if (written > MAX_BYTES) return;
		writer.append(text);
		written += text.length();
		writer.flush();
	}

	private static String xyz(double x, double y, double z) {
		return f(x, 5) + "," + f(y, 5) + "," + f(z, 5);
	}

	private static String f(double value, int digits) {
		return String.format(Locale.ROOT, "%." + digits + "f", value);
	}
}
