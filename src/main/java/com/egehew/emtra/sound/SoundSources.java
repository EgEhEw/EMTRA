package com.egehew.emtra.sound;

/**
 * Finds the OpenAL source Minecraft plays a sound instance on. Loader-specific (Minecraft's sound engine fields have
 * different names per loader): Forge installs a provider; without one, sources are found by position.
 */
public final class SoundSources {

	public interface Provider {
		/** @return the OpenAL source of a playing Minecraft sound instance, or 0 if unknown */
		int sourceOf(Object minecraftSoundInstance) throws Exception;
	}

	private static Provider provider;
	private static boolean failed;
	private static boolean logged;

	private SoundSources() {
	}

	public static void setProvider(Provider newProvider) {
		provider = newProvider;
	}

	static int sourceOf(Object minecraftSoundInstance) {
		if (provider == null || failed || minecraftSoundInstance == null) {
			return 0;
		}
		try {
			final int source = provider.sourceOf(minecraftSoundInstance);
			if (source > 0 && !logged) {
				logged = true;
				com.egehew.emtra.EmtraMod.LOGGER.info("Vehicle sound sources are read from Minecraft's sound engine (first: OpenAL source {})", source);
			}
			return source;
		} catch (Throwable t) {
			failed = true;
			com.egehew.emtra.EmtraMod.LOGGER.warn("Could not read Minecraft's sound sources, using positions instead: {}", t.toString());
			return 0;
		}
	}
}
