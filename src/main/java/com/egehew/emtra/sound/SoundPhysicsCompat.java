package com.egehew.emtra.sound;

import com.egehew.emtra.EmtraMod;
import com.egehew.emtra.render.EmtraClientConfig;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;

/**
 * Sound Physics Perfected (com.redsmods.sound_physics_perfected) cancels SoundEngine.play for every positional
 * sound and plays its own ray-traced copy a tick later. The original instance therefore never becomes "playing".
 * MTR's looping vehicle sounds check SoundManager.isPlaying every frame and call play again when it is false, so
 * SPP queued a new copy every frame: copies stacked up (loud, smeared trains, sound leaking far from tunnels) and
 * MTR could never stop them, because it only calls stop on sounds it thinks are playing.
 * <p>
 * {@link #isHandled} reports an MTR sound as playing while SPP holds it (queued or replaced by its copy). MTR then
 * plays it once, and stopping it reaches SPP's stop hook, which stops the copies. Without SPP this is a no-op.
 */
public final class SoundPhysicsCompat {

	private static final String HELPER = "com.redsmods.sound_physics_perfected.RaycastingHelper";
	private static boolean initialized;
	private static boolean available;
	private static Map<?, ?> replaced;
	private static Map<?, ?> replacedPermeated;
	private static Collection<?> pending;
	private static java.lang.reflect.Method getConfigInstance;
	private static Field blacklistField;
	private static Boolean remastered;

	private SoundPhysicsCompat() {
	}

	public static boolean isHandled(Object soundInstance) {
		if (!EmtraClientConfig.SOUND_PHYSICS_COMPAT || soundInstance == null || !init()) {
			return false;
		}
		try {
			return replaced.containsKey(soundInstance) || replacedPermeated.containsKey(soundInstance) || pending.contains(soundInstance);
		} catch (Throwable t) {
			// pending is a plain ArrayList owned by the client thread; a concurrent change only costs one frame.
			return false;
		}
	}

	private static Field copyIdField, copySourceSetField, copyXField, copyYField, copyZField;
	private static boolean copyFieldsFailed;

	/** The copy Sound Physics Perfected plays in place of this sound, or null. */
	static Object copyOf(Object original) {
		if (!init()) {
			return null;
		}
		try {
			final Object permeated = replacedPermeated.get(original);
			return permeated != null ? permeated : replaced.get(original);
		} catch (Throwable t) {
			return null;
		}
	}

	/** OpenAL source of an SPP copy (known for its permeated copies), or 0. */
	static int copySource(Object copy) {
		if (!copyFields(copy)) {
			return 0;
		}
		try {
			return copyIdField != null && copySourceSetField != null && copyIdField.getDeclaringClass().isInstance(copy) && copySourceSetField.getBoolean(copy) ? copyIdField.getInt(copy) : 0;
		} catch (Throwable t) {
			return 0;
		}
	}

	/** Current position of an SPP copy (where SPP moved the sound to), or false if unknown. */
	static boolean copyPosition(Object copy, double[] position) {
		if (!copyFields(copy) || copyXField == null) {
			return false;
		}
		try {
			position[0] = copyXField.getDouble(copy);
			position[1] = copyYField.getDouble(copy);
			position[2] = copyZField.getDouble(copy);
			return true;
		} catch (Throwable t) {
			return false;
		}
	}

	private static boolean copyFields(Object copy) {
		if (copyFieldsFailed) {
			return false;
		}
		if (copyXField == null) {
			try {
				final ClassLoader loader = SoundPhysicsCompat.class.getClassLoader();
				final Class<?> tickable = Class.forName("com.redsmods.sound_physics_perfected.wrappers.RedTickableInstance", false, loader);
				final Class<?> permeated = Class.forName("com.redsmods.sound_physics_perfected.wrappers.RedPermeatedSoundInstance", false, loader);
				copyIdField = accessible(permeated.getDeclaredField("id"));
				copySourceSetField = accessible(permeated.getDeclaredField("sourceSet"));
				copyYField = accessible(tickable.getDeclaredField("y"));
				copyZField = accessible(tickable.getDeclaredField("z"));
				copyXField = accessible(tickable.getDeclaredField("x"));
			} catch (Throwable t) {
				copyFieldsFailed = true;
				EmtraMod.LOGGER.warn("Could not read Sound Physics Perfected's sound copies: {}", t.toString());
				return false;
			}
		}
		return true;
	}

	private static Field accessible(Field field) {
		field.setAccessible(true);
		return field;
	}

	private static Field auxSlotField;
	private static Field sendFilterField;
	private static boolean reverbFieldsFailed;

	/** After SPP set a source's reverb send: reads SPP's aux slot and send filter, see {@link SoundFilters#onSppReverbSend}. */
	public static void afterReverb(Object effectsHandler, int source) {
		if (reverbFieldsFailed) {
			return;
		}
		try {
			if (auxSlotField == null) {
				auxSlotField = effectsHandler.getClass().getDeclaredField("auxFXSlot");
				auxSlotField.setAccessible(true);
				sendFilterField = effectsHandler.getClass().getDeclaredField("sendFilter");
				sendFilterField.setAccessible(true);
			}
			SoundFilters.onSppReverbSend(source, auxSlotField.getInt(effectsHandler), sendFilterField.getInt(effectsHandler));
		} catch (Throwable t) {
			reverbFieldsFailed = true;
			EmtraMod.LOGGER.warn("Could not read Sound Physics Perfected's reverb send, occluded trains may still be heard in reverb: {}", t.toString());
		}
	}

	/** True when Sound Physics Perfected is installed (its ray-traced copies replace sounds). */
	public static boolean perfectedLoaded() {
		return init();
	}

	/** True when Sound Physics Remastered is installed (it occludes every sound by itself). */
	public static boolean remasteredLoaded() {
		if (remastered == null) {
			boolean found;
			try {
				Class.forName("com.sonicether.soundphysics.SoundPhysics", false, SoundPhysicsCompat.class.getClassLoader());
				found = true;
			} catch (Throwable t) {
				found = false;
			}
			remastered = found;
		}
		return remastered;
	}

	/**
	 * Keeps {@link MtrSounds#SPP_MARKER} in SPP's sound blacklist while the compatibility option is on, so SPP does not
	 * replace MTR vehicle sounds (EMTRA occludes them instead), and removes it when the option is off.
	 */
	private static boolean markerChecked;

	@SuppressWarnings("unchecked")
	static void syncBlacklist() {
		if (!markerChecked && perfectedLoaded()) {
			final String sample = MtrSounds.sample();
			if (sample != null) {
				markerChecked = true;
				EmtraMod.LOGGER.info("Vehicle sound marker check: {} ({})", sample, sample.contains(MtrSounds.SPP_MARKER) ? "ok" : "MISSING");
			}
		}
		if (!init() || getConfigInstance == null) {
			return;
		}
		try {
			final Object config = getConfigInstance.invoke(null);
			if (config == null) {
				return;
			}
			java.util.List<String> list = (java.util.List<String>) blacklistField.get(config);
			final boolean wanted = EmtraClientConfig.SPP_BYPASS_VEHICLE_SOUNDS;
			if (list == null) {
				if (!wanted) {
					return;
				}
				list = new java.util.ArrayList<>();
				blacklistField.set(config, list);
			}
			if (wanted && !list.contains(MtrSounds.SPP_MARKER)) {
				list.add(MtrSounds.SPP_MARKER);
				EmtraMod.LOGGER.info("Vehicle sounds now bypass Sound Physics Perfected's copies (added {} to its sound blacklist)", MtrSounds.SPP_MARKER);
			} else if (!wanted) {
				list.remove(MtrSounds.SPP_MARKER);
			}
		} catch (Throwable t) {
			getConfigInstance = null;
			EmtraMod.LOGGER.warn("Could not update Sound Physics Perfected's sound blacklist: {}", t.toString());
		}
	}

	private static boolean init() {
		if (!initialized) {
			initialized = true;
			try {
				final Class<?> helper = Class.forName(HELPER, false, SoundPhysicsCompat.class.getClassLoader());
				replaced = (Map<?, ?>) field(helper, "soundInstanceMap");
				replacedPermeated = (Map<?, ?>) field(helper, "soundPermInstanceMap");
				pending = (Collection<?>) field(helper, "notProcd");
				available = replaced != null && replacedPermeated != null && pending != null;
				try {
					final Class<?> config = Class.forName("com.redsmods.sound_physics_perfected.config.Config", false, SoundPhysicsCompat.class.getClassLoader());
					getConfigInstance = config.getMethod("getInstance");
					blacklistField = config.getField("soundBlacklist");
				} catch (Throwable t) {
					EmtraMod.LOGGER.warn("Sound Physics Perfected config not found, MTR sounds stay processed by SPP: {}", t.toString());
				}
				if (available) {
					EmtraMod.LOGGER.info("Sound Physics Perfected found, MTR vehicle sound compatibility enabled");
				}
			} catch (ClassNotFoundException e) {
				available = false;
			} catch (Throwable t) {
				available = false;
				EmtraMod.LOGGER.warn("Sound Physics Perfected found but not compatible with EMTRA's sound fix: {}", t.toString());
			}
		}
		return available;
	}

	private static Object field(Class<?> owner, String name) throws ReflectiveOperationException {
		final Field field = owner.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(null);
	}
}
