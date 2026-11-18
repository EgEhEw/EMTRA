package com.egehew.emtra.render;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Client-side options, stored in config/emtra-client.properties and editable from the EMTRA config screen.
 * Loader-independent (Forge and Fabric both run with the game directory as cwd). Values are read by the render and
 * sound code every time they are used, so changes from the config screen apply immediately.
 */
public final class EmtraClientConfig {

	/** Draw repeated MTR model parts (rails, etc.) that share a mesh with one instanced draw call. */
	public static volatile boolean RENDER_INSTANCING;
	/** Cache the model instances MTR queues for each rail instead of recomputing rail curves every frame. */
	public static volatile boolean RENDER_RAIL_CACHE;
	/** Draw MSD rigid catenary from cached MTR models instead of rebuilding its quads every frame. */
	public static volatile boolean RENDER_CATENARY_CACHE;
	/** Log draw call statistics every 10 seconds. */
	public static volatile boolean RENDER_STATS;
	/** Write vehicle sound and OpenAL details to logs/emtra-sound.log once a second (for debugging sound problems). */
	public static volatile boolean SOUND_DEBUG;

	/** Smooth MTR vehicle loop volume and pitch, so flickering motor output does not stop and restart loops (clicks). */
	public static final boolean SMOOTH_VEHICLE_SOUNDS = true;
	/**
	 * Cap MTR vehicle loop volume at 1: above 1 Minecraft only multiplies the audible range (BVE motors x5 = 640 blocks).
	 */
	public static final boolean LIMIT_VEHICLE_SOUND_RANGE = true;
	/** Occlude MTR vehicle sounds by the blocks between them and the listener (trains in tunnels). */
	public static final boolean MTR_SOUND_OCCLUSION = true;
	/** Muffle sounds while riding a train (experimental, off by default). */
	public static volatile boolean TRAIN_MUFFLE;
	/** Volume of outside sounds at full muffle, in percent. */
	public static volatile int TRAIN_MUFFLE_VOLUME;
	/** High frequencies left in outside sounds at full muffle, in percent (lower = more muffled). */
	public static volatile int TRAIN_MUFFLE_TREBLE;
	/** How much of the muffle is removed while the doors are fully open, in percent. */
	public static volatile int TRAIN_MUFFLE_DOOR_OPEN;
	/** Time for the muffle to fade in or out when boarding, alighting or when the doors move, in milliseconds. */
	public static volatile int TRAIN_MUFFLE_FADE_MS;
	/** Extra smoothing of the muffle following the doors (the doors themselves move over about 1.6 s), in milliseconds. */
	public static volatile int TRAIN_MUFFLE_DOOR_FADE_MS;

	private static final Path FILE = Paths.get("config", "emtra-client.properties");
	private static final String COMMENT = "EMTRA client options (also editable in game: Mods > EMTRA > Config). "
			+ "renderInstancing, renderRailCache, renderCatenaryCache: render optimizations for MTR models, rails and MSD catenary. "
			+ "trainMuffle* (experimental): muffles sounds while riding a train (volume/treble/doorOpen in percent, fades in ms). "
			+ "Train sound fixes are always on.";

	static {
		final Properties defaults = new Properties();
		defaults.setProperty("renderInstancing", "true");
		defaults.setProperty("renderRailCache", "true");
		defaults.setProperty("renderCatenaryCache", "true");
		defaults.setProperty("renderStats", "false");
		defaults.setProperty("soundDebug", "false");
		defaults.setProperty("trainMuffle", "false");
		defaults.setProperty("trainMuffleVolume", "55");
		defaults.setProperty("trainMuffleTreble", "10");
		defaults.setProperty("trainMuffleDoorOpen", "75");
		defaults.setProperty("trainMuffleFadeMs", "700");
		defaults.setProperty("trainMuffleDoorFadeMs", "250");
		final Properties properties = new Properties();
		properties.putAll(defaults);
		boolean missing = true;
		try {
			if (Files.exists(FILE)) {
				final Properties saved = new Properties();
				try (InputStream inputStream = Files.newInputStream(FILE)) {
					saved.load(inputStream);
				}
				properties.putAll(saved);
				// Rewrite when keys are missing or left over from older versions (obsolete settings).
				missing = !saved.stringPropertyNames().equals(defaults.stringPropertyNames());
			}
		} catch (Exception e) {
			System.err.println("[EMTRA] Could not read " + FILE + ": " + e);
		}
		RENDER_INSTANCING = bool(properties, "renderInstancing", true);
		RENDER_RAIL_CACHE = bool(properties, "renderRailCache", true);
		RENDER_CATENARY_CACHE = bool(properties, "renderCatenaryCache", true);
		RENDER_STATS = bool(properties, "renderStats", false);
		SOUND_DEBUG = bool(properties, "soundDebug", false);
		TRAIN_MUFFLE = bool(properties, "trainMuffle", false);
		TRAIN_MUFFLE_VOLUME = integer(properties, "trainMuffleVolume", 55, 0, 100);
		TRAIN_MUFFLE_TREBLE = integer(properties, "trainMuffleTreble", 10, 0, 100);
		TRAIN_MUFFLE_DOOR_OPEN = integer(properties, "trainMuffleDoorOpen", 75, 0, 100);
		TRAIN_MUFFLE_FADE_MS = integer(properties, "trainMuffleFadeMs", 700, 0, 5000);
		TRAIN_MUFFLE_DOOR_FADE_MS = integer(properties, "trainMuffleDoorFadeMs", 250, 0, 5000);
		if (missing) {
			save();
		}
	}

	private EmtraClientConfig() {
	}

	public static synchronized void save() {
		final Properties properties = new Properties();
		properties.setProperty("renderInstancing", String.valueOf(RENDER_INSTANCING));
		properties.setProperty("renderRailCache", String.valueOf(RENDER_RAIL_CACHE));
		properties.setProperty("renderCatenaryCache", String.valueOf(RENDER_CATENARY_CACHE));
		properties.setProperty("renderStats", String.valueOf(RENDER_STATS));
		properties.setProperty("soundDebug", String.valueOf(SOUND_DEBUG));
		properties.setProperty("trainMuffle", String.valueOf(TRAIN_MUFFLE));
		properties.setProperty("trainMuffleVolume", String.valueOf(TRAIN_MUFFLE_VOLUME));
		properties.setProperty("trainMuffleTreble", String.valueOf(TRAIN_MUFFLE_TREBLE));
		properties.setProperty("trainMuffleDoorOpen", String.valueOf(TRAIN_MUFFLE_DOOR_OPEN));
		properties.setProperty("trainMuffleFadeMs", String.valueOf(TRAIN_MUFFLE_FADE_MS));
		properties.setProperty("trainMuffleDoorFadeMs", String.valueOf(TRAIN_MUFFLE_DOOR_FADE_MS));
		try {
			Files.createDirectories(FILE.getParent());
			try (OutputStream outputStream = Files.newOutputStream(FILE)) {
				properties.store(outputStream, COMMENT);
			}
		} catch (Exception e) {
			System.err.println("[EMTRA] Could not write " + FILE + ": " + e);
		}
	}

	private static boolean bool(Properties properties, String key, boolean fallback) {
		final String value = properties.getProperty(key);
		return value == null ? fallback : Boolean.parseBoolean(value.trim());
	}

	private static int integer(Properties properties, String key, int fallback, int min, int max) {
		try {
			return Math.max(min, Math.min(max, Integer.parseInt(properties.getProperty(key, String.valueOf(fallback)).trim())));
		} catch (NumberFormatException e) {
			return fallback;
		}
	}
}
