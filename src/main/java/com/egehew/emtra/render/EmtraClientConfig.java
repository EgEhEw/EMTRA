package com.egehew.emtra.render;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Client-side render options, stored in config/emtra-client.properties.
 * Read once on first use; loader-independent (Forge and Fabric both run with the game directory as cwd).
 */
public final class EmtraClientConfig {

	/** Draw repeated MTR model parts (rails, etc.) that share a mesh with one instanced draw call. */
	public static final boolean RENDER_INSTANCING;
	/** Cache the model instances MTR queues for each rail instead of recomputing rail curves every frame. */
	public static final boolean RENDER_RAIL_CACHE;
	/** Draw MSD rigid catenary from cached MTR models instead of rebuilding its quads every frame. */
	public static final boolean RENDER_CATENARY_CACHE;
	/** Log draw call statistics every 10 seconds. */
	public static final boolean RENDER_STATS;

	static {
		final Properties defaults = new Properties();
		defaults.setProperty("renderInstancing", "false");
		defaults.setProperty("renderRailCache", "false");
		defaults.setProperty("renderCatenaryCache", "false");
		defaults.setProperty("renderStats", "false");
		final Properties properties = new Properties();
		properties.putAll(defaults);
		final Path file = Paths.get("config", "emtra-client.properties");
		try {
			if (Files.exists(file)) {
				try (InputStream inputStream = Files.newInputStream(file)) {
					properties.load(inputStream);
				}
			}
			if (!properties.stringPropertyNames().containsAll(defaults.stringPropertyNames()) || !Files.exists(file)) {
				Files.createDirectories(file.getParent());
				final Properties merged = new Properties();
				merged.putAll(defaults);
				merged.putAll(properties);
				try (OutputStream outputStream = Files.newOutputStream(file)) {
					merged.store(outputStream, "EMTRA client options. renderInstancing=true draws repeated MTR model parts (rails) with GPU instancing; renderRailCache=true reuses rail model placements instead of recomputing them every frame; renderCatenaryCache=true draws MSD rigid catenary from cached models (all experimental).");
				}
			}
		} catch (Exception e) {
			System.err.println("[EMTRA] Could not read " + file + ": " + e);
		}
		RENDER_INSTANCING = Boolean.parseBoolean(properties.getProperty("renderInstancing", "false").trim());
		RENDER_RAIL_CACHE = Boolean.parseBoolean(properties.getProperty("renderRailCache", "false").trim());
		RENDER_CATENARY_CACHE = Boolean.parseBoolean(properties.getProperty("renderCatenaryCache", "false").trim());
		RENDER_STATS = Boolean.parseBoolean(properties.getProperty("renderStats", "false").trim());
	}

	private EmtraClientConfig() {
	}
}
