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
	/** Replace stock vehicle oscillation with EMTRA's sway and brake pitch. */
	public static volatile boolean ADVANCED_SWAY;
	public static volatile int SWAY_HORIZONTAL;
	public static volatile int SWAY_VERTICAL;
	public static volatile int SWAY_BRAKE_PITCH;
	public static final int SWAY_MAX_PERCENT = 200;
	/** Log draw call statistics every 10 seconds. */
	public static volatile boolean RENDER_STATS;
	/** Write vehicle sound and OpenAL details to logs/emtra-sound.log once a second (for debugging sound problems). */
	public static volatile boolean SOUND_DEBUG;
	/** Write the ridden car's pose, rider offset and camera to logs/emtra-sway.log every frame (for debugging shaking). */
	public static volatile boolean SWAY_DEBUG;
	/**
	 * Split MTR model triangles longer than a block when they are loaded, so shader packs' distorted shadow maps do not
	 * bend long faces (a roof shadowing itself near the player). Takes effect on resource reload (F3+T).
	 */
	public static volatile boolean SUBDIVIDE_MODELS;

	/** Smooth MTR vehicle loop volume and pitch, so flickering motor output does not stop and restart loops (clicks). */
	public static final boolean SMOOTH_VEHICLE_SOUNDS = true;
	/**
	 * Advance MTR vehicles by the exact frame time. MTR truncates each frame to whole milliseconds (at 240 fps, 4.15 ms
	 * becomes 4) and, once the lost time passes 50 ms, catches up in one frame: a visible one-tick jump of the train.
	 */
	public static final boolean EXACT_VEHICLE_TIMING = true;
	/** Glide Sound Physics Remastered's moving-sound filter updates instead of stepping them (clicks). */
	public static final boolean SMOOTH_SPR_UPDATES = true;
	/** Play MTR vehicle loops at the car's exact position, 1.5 blocks above the rail, instead of a block corner at rail level. */
	public static final boolean EXACT_VEHICLE_SOUND_POSITION = true;
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
			+ "advancedSway: replaces MTR sway. advancedSwayHorizontal/Vertical/BrakePitch: strength in percent. "
			+ "trainMuffle* (experimental): muffles sounds while riding a train (volume/treble/doorOpen in percent, fades in ms). "
			+ "Train sound fixes are always on.";

	static {
		final Properties defaults = new Properties();
		defaults.setProperty("renderInstancing", "true");
		defaults.setProperty("renderRailCache", "true");
		defaults.setProperty("renderCatenaryCache", "true");
		defaults.setProperty("advancedSway", "false");
		defaults.setProperty("advancedSwayHorizontal", "100");
		defaults.setProperty("advancedSwayVertical", "100");
		defaults.setProperty("advancedSwayBrakePitch", "100");
		defaults.setProperty("renderStats", "false");
		defaults.setProperty("soundDebug", "false");
		defaults.setProperty("swayDebug", "false");
		defaults.setProperty("subdivideModels", "true");
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
				migrateSwayOptions(properties, saved);
				// Rewrite when keys are missing or left over from older versions (obsolete settings).
				missing = !saved.stringPropertyNames().equals(defaults.stringPropertyNames());
			}
		} catch (Exception e) {
			System.err.println("[EMTRA] Could not read " + FILE + ": " + e);
		}
		RENDER_INSTANCING = bool(properties, "renderInstancing", true);
		RENDER_RAIL_CACHE = bool(properties, "renderRailCache", true);
		RENDER_CATENARY_CACHE = bool(properties, "renderCatenaryCache", true);
		ADVANCED_SWAY = bool(properties, "advancedSway", false);
		SWAY_HORIZONTAL = integer(properties, "advancedSwayHorizontal", 100, 0, SWAY_MAX_PERCENT);
		SWAY_VERTICAL = integer(properties, "advancedSwayVertical", 100, 0, SWAY_MAX_PERCENT);
		SWAY_BRAKE_PITCH = integer(properties, "advancedSwayBrakePitch", 100, 0, SWAY_MAX_PERCENT);
		RENDER_STATS = bool(properties, "renderStats", false);
		SOUND_DEBUG = bool(properties, "soundDebug", false);
		SWAY_DEBUG = bool(properties, "swayDebug", false);
		SUBDIVIDE_MODELS = bool(properties, "subdivideModels", true);
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
		properties.setProperty("advancedSway", String.valueOf(ADVANCED_SWAY));
		properties.setProperty("advancedSwayHorizontal", String.valueOf(SWAY_HORIZONTAL));
		properties.setProperty("advancedSwayVertical", String.valueOf(SWAY_VERTICAL));
		properties.setProperty("advancedSwayBrakePitch", String.valueOf(SWAY_BRAKE_PITCH));
		properties.setProperty("renderStats", String.valueOf(RENDER_STATS));
		properties.setProperty("soundDebug", String.valueOf(SOUND_DEBUG));
		properties.setProperty("swayDebug", String.valueOf(SWAY_DEBUG));
		properties.setProperty("subdivideModels", String.valueOf(SUBDIVIDE_MODELS));
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

	private static void migrateSwayOptions(Properties properties, Properties saved) {
		if (saved.containsKey("advancedSway") || (!saved.containsKey("vehicleSway") && !saved.containsKey("trainStopSettle"))) return;

		boolean sway = bool(saved, "vehicleSway", true);
		boolean brake = bool(saved, "trainStopSettle", false);
		if (sway || brake) {
			properties.setProperty("advancedSway", "true");
			if (!sway) {
				properties.setProperty("advancedSwayHorizontal", "0");
				properties.setProperty("advancedSwayVertical", "0");
			}
			if (!brake) properties.setProperty("advancedSwayBrakePitch", "0");
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
