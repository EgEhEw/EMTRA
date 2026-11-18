package com.egehew.emtra.sound;

import org.mtr.mapping.holder.MinecraftClient;

import java.lang.reflect.Field;
import java.util.Map;

/** Looks up OpenAL sources by sound instance across Forge and Fabric mappings. Render thread only. */
public final class SoundSources {

	private static boolean failed;
	private static boolean logged;

	private SoundSources() {
	}

	static int sourceOf(Object minecraftSoundInstance) {
		if (failed || minecraftSoundInstance == null) {
			return 0;
		}
		try {
			final int source = readSource(minecraftSoundInstance);
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

	private static Field soundEngineField, instanceToChannelField, channelField, sourceField;

	private static int readSource(Object minecraftSoundInstance) throws IllegalAccessException {
		final Object soundManager = MinecraftClient.getInstance().getSoundManager().data;
		if (soundEngineField == null) {
			soundEngineField = field(soundManager.getClass(), "f_120349_", "soundEngine", "field_5590", "soundSystem");
		}
		final Object soundEngine = soundEngineField.get(soundManager);
		if (soundEngine == null) {
			return 0;
		}
		if (instanceToChannelField == null) {
			instanceToChannelField = field(soundEngine.getClass(), "f_120226_", "instanceToChannel", "field_18950", "sources");
		}
		final Map<?, ?> instanceToChannel = (Map<?, ?>) instanceToChannelField.get(soundEngine);
		final Object handle = instanceToChannel == null ? null : instanceToChannel.get(minecraftSoundInstance);
		if (handle == null) {
			return 0;
		}
		if (channelField == null) {
			channelField = field(handle.getClass(), "f_120146_", "channel", "field_18941", "source");
		}
		final Object channel = channelField.get(handle);
		if (channel == null) {
			return 0;
		}
		if (sourceField == null) {
			sourceField = field(channel.getClass(), "f_83642_", "source", "field_18893", "pointer");
		}
		return sourceField.getInt(channel);
	}

	private static Field field(Class<?> type, String... names) {
		for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
			for (final String name : names) {
				try {
					final Field field = c.getDeclaredField(name);
					field.setAccessible(true);
					return field;
				} catch (NoSuchFieldException ignored) {
				}
			}
		}
		throw new IllegalStateException("No field " + String.join("/", names) + " in " + type.getName());
	}
}
