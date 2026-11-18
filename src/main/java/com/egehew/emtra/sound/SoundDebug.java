package com.egehew.emtra.sound;

import com.egehew.emtra.EmtraMod;
import com.egehew.emtra.render.EmtraClientConfig;
import org.lwjgl.openal.AL10;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.ClientWorld;
import org.mtr.mapping.holder.MinecraftClient;
import org.mtr.mapping.holder.MovingSoundInstanceAbstractMapping;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Sound debug log ({@link EmtraClientConfig#SOUND_DEBUG}): once a second, writes to logs/emtra-sound.log where the
 * listener is and, for every vehicle sound EMTRA tracks, what MTR requested, what EMTRA applied and what OpenAL is actually playing, plus every playing OpenAL source near the listener. Meant to
 * be read after reproducing a sound problem. Runs on the thread that updates EMTRA's sound filters.
 */
public final class SoundDebug {

	private static final Path FILE = Paths.get("logs", "emtra-sound.log");
	private static final long MAX_BYTES = 20L * 1024 * 1024;
	private static final long INTERVAL_NANOS = 1_000_000_000L;
	private static final int MAX_SOURCE = 512;
	private static final double SOURCE_LIST_DISTANCE = 96;
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

	private static long lastPass;
	private static boolean inPass;
	private static double listenerX, listenerY, listenerZ;
	private static final StringBuilder BUILDER = new StringBuilder();
	private static final List<Integer> VEHICLE_SOURCES = new ArrayList<>();
	private static boolean broken;
	private static boolean announced;

	private static final float[] AX = new float[1], AY = new float[1], AZ = new float[1];

	private SoundDebug() {
	}

	/** Start of MtrSounds.collect: whether this update writes a debug pass. */
	static boolean beginPass(double x, double y, double z, boolean occlusion) {
		if (!EmtraClientConfig.SOUND_DEBUG || broken) {
			inPass = false;
			return false;
		}
		final long now = System.nanoTime();
		if (now - lastPass < INTERVAL_NANOS) {
			inPass = false;
			return false;
		}
		lastPass = now;
		inPass = true;
		listenerX = x;
		listenerY = y;
		listenerZ = z;
		BUILDER.setLength(0);
		VEHICLE_SOURCES.clear();
		BUILDER.append("==== ").append(LocalTime.now().format(TIME))
				.append(" listener ").append(xyz(x, y, z))
				.append(" listenerTunnel=").append(f2(MtrSounds.tunnelFactor(x, y - 1, z)))
				.append(" mode=").append(occlusion ? "EMTRA-occlusion" : "none")
				.append(" muffle=").append(EmtraClientConfig.TRAIN_MUFFLE)
				.append('\n');
		return true;
	}

	/** One vehicle sound as processed by MtrSounds.collect. */
	static void sound(MovingSoundInstanceAbstractMapping sound, double x, double y, double z, int source,
					  float targetGain, float gain) {
		if (!inPass) {
			return;
		}
		try {
			String id;
			try {
				id = sound.getId2().getNamespace() + ":" + sound.getId2().getPath();
			} catch (Throwable t) {
				id = "?";
			}
			final double distance = MtrSounds.distanceTo(x - listenerX, y - listenerY, z - listenerZ);
			BUILDER.append("  ").append(id)
					.append(" at ").append(xyz(x, y, z))
					.append(" dist=").append(f1(distance))
					.append(" mtrVol=").append(f3(sound.getVolume2())).append(" pitch=").append(f3(sound.getPitch2()))
					.append(" trainTunnel=").append(f2(MtrSounds.tunnelFactor(x, y, z)))
					.append(" blocks=").append(blocksLine(x, y, z))
					.append(" emtraGain=").append(f3(targetGain)).append("->").append(f3(gain));
			if (source > 0) {
				VEHICLE_SOURCES.add(source);
				BUILDER.append(" src=").append(source);
				appendSource(source);
			} else {
				BUILDER.append(" src=?");
			}
			BUILDER.append('\n');
		} catch (Throwable t) {
			BUILDER.append("  (error: ").append(t).append(")\n");
		}
	}

	/** End of SoundFilters' update: lists the playing OpenAL sources near the listener and writes the pass. */
	static void finishPass() {
		if (!inPass) {
			return;
		}
		inPass = false;
		try {
			final List<String> lines = new ArrayList<>();
			int playing = 0;
			for (int source = 1; source <= MAX_SOURCE; source++) {
				if (!AL10.alIsSource(source) || AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
					continue;
				}
				playing++;
				final boolean relative = AL10.alGetSourcei(source, AL10.AL_SOURCE_RELATIVE) == AL10.AL_TRUE;
				AL10.alGetSource3f(source, AL10.AL_POSITION, AX, AY, AZ);
				final double distance = relative ? MtrSounds.distanceTo(AX[0], AY[0], AZ[0]) : MtrSounds.distanceTo(AX[0] - listenerX, AY[0] - listenerY, AZ[0] - listenerZ);
				if (distance > SOURCE_LIST_DISTANCE) {
					continue;
				}
				lines.add(String.format(Locale.ROOT, "    src=%d%s at %s dist=%.1f gain=%.3f pitch=%.3f%s", source, VEHICLE_SOURCES.contains(source) ? " [vehicle]" : "",
						relative ? "relative" : xyz(AX[0], AY[0], AZ[0]), distance, AL10.alGetSourcef(source, AL10.AL_GAIN), AL10.alGetSourcef(source, AL10.AL_PITCH),
						AL10.alGetSourcei(source, AL10.AL_LOOPING) == AL10.AL_TRUE ? " loop" : ""));
			}
			AL10.alGetError();
			BUILDER.append("  OpenAL: ").append(playing).append(" playing, within ").append((int) SOURCE_LIST_DISTANCE).append(" blocks:\n");
			for (final String line : lines) {
				BUILDER.append(line).append('\n');
			}
			write(BUILDER.toString());
		} catch (Throwable t) {
			broken = true;
			EmtraMod.LOGGER.warn("EMTRA sound debug log disabled: {}", t.toString());
		}
	}

	private static void appendSource(int source) {
		if (!AL10.alIsSource(source)) {
			BUILDER.append(" (not a source)");
			return;
		}
		final int state = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
		AL10.alGetSource3f(source, AL10.AL_POSITION, AX, AY, AZ);
		BUILDER.append(" al[").append(state == AL10.AL_PLAYING ? "playing" : state == AL10.AL_PAUSED ? "paused" : state == AL10.AL_STOPPED ? "stopped" : "initial")
				.append(" gain=").append(f3(AL10.alGetSourcef(source, AL10.AL_GAIN)))
				.append(" at ").append(xyz(AX[0], AY[0], AZ[0]))
				.append(" refDist=").append(f1(AL10.alGetSourcef(source, AL10.AL_REFERENCE_DISTANCE)))
				.append(" maxDist=").append(f1(AL10.alGetSourcef(source, AL10.AL_MAX_DISTANCE)))
				.append(']');
		AL10.alGetError();
	}

	/** Opaque blocks on the straight line from the listener to the sound, and to 1 and 2 blocks above it. */
	private static String blocksLine(double x, double y, double z) {
		final ClientWorld world = MinecraftClient.getInstance().getWorldMapped();
		if (world == null) {
			return "?";
		}
		return count(world, x, y, z) + "/" + count(world, x, y + 1, z) + "/" + count(world, x, y + 2, z);
	}

	private static int count(ClientWorld world, double x2, double y2, double z2) {
		final double x1 = listenerX, y1 = listenerY, z1 = listenerZ;
		final double dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
		final int endX = (int) Math.floor(x2), endY = (int) Math.floor(y2), endZ = (int) Math.floor(z2);
		int x = (int) Math.floor(x1), y = (int) Math.floor(y1), z = (int) Math.floor(z1);
		final int stepX = dx > 0 ? 1 : -1, stepY = dy > 0 ? 1 : -1, stepZ = dz > 0 ? 1 : -1;
		final double deltaX = dx == 0 ? Double.MAX_VALUE : Math.abs(1 / dx);
		final double deltaY = dy == 0 ? Double.MAX_VALUE : Math.abs(1 / dy);
		final double deltaZ = dz == 0 ? Double.MAX_VALUE : Math.abs(1 / dz);
		double maxX = dx == 0 ? Double.MAX_VALUE : (dx > 0 ? Math.floor(x1) + 1 - x1 : x1 - Math.floor(x1)) * deltaX;
		double maxY = dy == 0 ? Double.MAX_VALUE : (dy > 0 ? Math.floor(y1) + 1 - y1 : y1 - Math.floor(y1)) * deltaY;
		double maxZ = dz == 0 ? Double.MAX_VALUE : (dz > 0 ? Math.floor(z1) + 1 - z1 : z1 - Math.floor(z1)) * deltaZ;
		int blocks = 0;
		for (int steps = 0; steps < 1024; steps++) {
			if (x == endX && y == endY && z == endZ) {
				break;
			}
			if (world.getBlockState(new BlockPos(x, y, z)).isOpaque()) {
				blocks++;
			}
			final double t;
			if (maxX < maxY && maxX < maxZ) {
				t = maxX;
				maxX += deltaX;
				x += stepX;
			} else if (maxY < maxZ) {
				t = maxY;
				maxY += deltaY;
				y += stepY;
			} else {
				t = maxZ;
				maxZ += deltaZ;
				z += stepZ;
			}
			if (t > 1) {
				break;
			}
		}
		return blocks;
	}

	private static void write(String text) throws IOException {
		if (Files.exists(FILE) && Files.size(FILE) > MAX_BYTES) {
			Files.move(FILE, FILE.resolveSibling("emtra-sound.1.log"), StandardCopyOption.REPLACE_EXISTING);
		}
		Files.createDirectories(FILE.getParent());
		try (BufferedWriter writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
			writer.write(text);
		}
		if (!announced) {
			announced = true;
			EmtraMod.LOGGER.info("EMTRA sound debug log: {}", FILE.toAbsolutePath());
		}
	}

	private static String xyz(double x, double y, double z) {
		return String.format(Locale.ROOT, "(%.1f %.1f %.1f)", x, y, z);
	}

	private static String f1(double value) {
		return String.format(Locale.ROOT, "%.1f", value);
	}

	private static String f2(double value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}

	private static String f3(double value) {
		return String.format(Locale.ROOT, "%.3f", value);
	}
}
