package com.egehew.emtra.forge;

import com.egehew.emtra.sound.SoundSources;
import org.mtr.mapping.holder.MinecraftClient;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * SoundManager.soundEngine -> SoundEngine.instanceToChannel -> ChannelHandle.channel -> Channel.source.
 * Read by reflection with the production (SRG) field names, falling back to the development (Mojang) names, so no
 * remapping is needed. Render thread only.
 */
final class ForgeSoundSources implements SoundSources.Provider {

	private Field soundEngineField, instanceToChannelField, channelField, sourceField;

	@Override
	public int sourceOf(Object minecraftSoundInstance) throws IllegalAccessException {
		final Object soundManager = MinecraftClient.getInstance().getSoundManager().data;
		if (soundEngineField == null) {
			soundEngineField = field(soundManager.getClass(), "f_120349_", "soundEngine");
		}
		final Object soundEngine = soundEngineField.get(soundManager);
		if (soundEngine == null) {
			return 0;
		}
		if (instanceToChannelField == null) {
			instanceToChannelField = field(soundEngine.getClass(), "f_120226_", "instanceToChannel");
		}
		final Map<?, ?> instanceToChannel = (Map<?, ?>) instanceToChannelField.get(soundEngine);
		final Object handle = instanceToChannel == null ? null : instanceToChannel.get(minecraftSoundInstance);
		if (handle == null) {
			return 0;
		}
		if (channelField == null) {
			channelField = field(handle.getClass(), "f_120146_", "channel");
		}
		final Object channel = channelField.get(handle);
		if (channel == null) {
			return 0;
		}
		if (sourceField == null) {
			sourceField = field(channel.getClass(), "f_83642_", "source");
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
