package com.egehew.emtra.sound;

import com.egehew.emtra.render.EmtraClientConfig;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.MinecraftClient;
import org.mtr.mapping.holder.MovingSoundInstanceAbstractMapping;
import org.mtr.mapping.holder.SoundCategory;
import org.mtr.mapping.holder.SoundEvent;
import org.mtr.mapping.holder.SoundInstance;
import org.mtr.mapping.mapper.MovingSoundInstanceExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Vehicle sounds: MTR's looping motor/running sounds and one-shot door/brake/compressor sounds, and the sounds of
 * vehicle scripts (JCM scripting, FangSu).
 * <ul>
 * <li>Their positions are tracked so {@link SoundFilters} can find their OpenAL sources and occlude them.</li>
 * <li>Their toString carries {@link #SPP_MARKER}, which EMTRA adds to Sound Physics Perfected's sound blacklist, so
 * SPP leaves them alone (it moved underground trains to the surface and played door sounds almost silently).</li>
 * <li>One-shot sounds: MTR's ScheduledSound played every scheduled sound once per scheduled entry (n entries in a tick
 * = each sound n times on top of itself, clipping). Each entry now plays once.</li>
 * </ul>
 */
public final class MtrSounds {

	public static final String SPP_MARKER = "emtra:mtr_vehicle_sound";

	private static final Set<MovingSoundInstanceAbstractMapping> SOUNDS = Collections.newSetFromMap(new WeakHashMap<>());
	private static final Map<Object, Boolean> PLAYED_THIS_FLUSH = new IdentityHashMap<>();
	private static final Map<Object, float[]> SMOOTHED = new WeakHashMap<>();
	/** BVE motor and run loops: inside the riding train these are muffled, its other sounds are not. */
	private static final Set<Object> MOTOR_AND_RUN = Collections.newSetFromMap(new WeakHashMap<>());
	private static final Map<Object, Boolean> MOTOR_OR_RUN_BY_ID = new WeakHashMap<>();
	/**
	 * Last distinct positions of each sound (x, y, z triples, newest first). Minecraft copies an instance's position to
	 * its OpenAL source once per tick while MTR moves it every frame, so a moving train's source sits at one of these.
	 */
	private static final Map<Object, float[]> HISTORY = new WeakHashMap<>();
	private static final int HISTORY_LENGTH = 8;
	/** Sound id paths of motor/run tracks in any numbering: r142bve_motor0, r142bve_run3, pack/run12, motor... */
	private static final java.util.regex.Pattern MOTOR_OR_RUN_ID = java.util.regex.Pattern.compile("(^|[_/.-])(motor|run)\\d*$", java.util.regex.Pattern.CASE_INSENSITIVE);
	private static java.lang.reflect.Field holderField, motorField, runField;
	private static boolean tagFailed;
	private static final Map<Long, Integer> OCCLUSION_CACHE = new java.util.HashMap<>();
	private static final double MAX_DISTANCE = 128;
	private static final float OCCLUSION_FADE_SECONDS = 0.3F;
	private static final double[] COPY_POSITION = new double[3];

	private MtrSounds() {
	}

	public static void register(Object sound) {
		if (sound instanceof MovingSoundInstanceAbstractMapping) {
			synchronized (SOUNDS) {
				SOUNDS.add((MovingSoundInstanceAbstractMapping) sound);
			}
		}
	}

	/** Output of {@link #collect}: per vehicle sound, x, y, z (where its OpenAL source is), gain, gainHF, own vehicle. */
	static final int STRIDE = 6;

	/**
	 * Every playing vehicle sound near the listener. A sound is either played directly (its source is found by
	 * position) or replaced by a Sound Physics Perfected copy (its source id is known, or found by the copy's
	 * position). gain/gainHF: EMTRA's smoothed factor for it, from the blocks on the straight line between the
	 * listener and the vehicle: silent above the configured block limit, EMTRA occlusion when SPP does not handle it.
	 * Returns the count; sources[i] is the OpenAL source or 0.
	 */
	static int collect(float[][] holder, int[][] sourceHolder, double listenerX, double listenerY, double listenerZ, float seconds) {
		synchronized (SOUNDS) {
			float[] array = holder[0];
			if (array.length < SOUNDS.size() * STRIDE) {
				array = new float[SOUNDS.size() * STRIDE + 96];
				holder[0] = array;
			}
			int[] sources = sourceHolder[0];
			if (sources.length < SOUNDS.size()) {
				sources = new int[SOUNDS.size() + 16];
				sourceHolder[0] = sources;
			}
			OCCLUSION_CACHE.clear();
			final org.mtr.mapping.holder.SoundManager soundManager = MinecraftClient.getInstance().getSoundManager();
			final boolean occlusion = SoundFilters.occlusionActive();
			final int limit = EmtraClientConfig.VEHICLE_SOUND_BLOCK_LIMIT;
			int count = 0;
			for (final MovingSoundInstanceAbstractMapping sound : SOUNDS) {
				try {
					final double x = sound.getX2(), y = sound.getY2(), z = sound.getZ2();
					final double dx = x - listenerX, dy = y - listenerY, dz = z - listenerZ;
					if (dx * dx + dy * dy + dz * dz > MAX_DISTANCE * MAX_DISTANCE) {
						continue;
					}
					// Where is it playing?
					// The exact OpenAL source from Minecraft's sound engine where possible: all loops of a car play at the
					// same position, so positions alone cannot tell the motor from the noise loop.
					int source = 0;
					double sourceX = x, sourceY = y, sourceZ = z;
					if (soundManager.isPlaying(new SoundInstance(sound))) {
						source = SoundSources.sourceOf(sound);
					} else {
						final Object copy = SoundPhysicsCompat.copyOf(sound);
						if (copy == null) {
							continue;
						}
						source = SoundSources.sourceOf(copy);
						if (source == 0) {
							source = SoundPhysicsCompat.copySource(copy);
						}
						if (source == 0) {
							if (!SoundPhysicsCompat.copyPosition(copy, COPY_POSITION)) {
								continue;
							}
							sourceX = COPY_POSITION[0];
							sourceY = COPY_POSITION[1];
							sourceZ = COPY_POSITION[2];
						}
					}
					// Blocks between the listener and the vehicle itself (not where SPP moved the sound)
					final long key = (long) Math.floor(x) * 31L * 31L * 1024L + (long) Math.floor(y) * 31L * 1024L + (long) Math.floor(z);
					int blocks = OCCLUSION_CACHE.getOrDefault(key, -1);
					if (blocks < 0) {
						blocks = SoundFilters.countOccludingBlocks(listenerX, listenerY, listenerZ, x, y, z);
						OCCLUSION_CACHE.put(key, blocks);
					}
					float targetGain = 1, targetGainHF = 1;
					if (occlusion) {
						targetGain = sound instanceof OneShotSound ? 1 : SoundFilters.occlusionGain(blocks);
						targetGainHF = SoundFilters.occlusionGainHF(blocks);
					}
					if (limit > 0 && blocks > limit) {
						targetGain = 0;
						targetGainHF = 0;
					}
					float[] smoothed = SMOOTHED.get(sound);
					if (smoothed == null) {
						smoothed = new float[]{targetGain, targetGainHF};
						SMOOTHED.put(sound, smoothed);
					} else {
						smoothed[0] = SoundFilters.approach(smoothed[0], targetGain, OCCLUSION_FADE_SECONDS, seconds);
						smoothed[1] = SoundFilters.approach(smoothed[1], targetGainHF, OCCLUSION_FADE_SECONDS, seconds);
					}
					// 1: a sound of the riding train that stays clear of the muffle; motor and run loops are muffled like outside.
					final float own = SoundFilters.insideRidingVehicle(x, y, z) && !isMotorOrRun(sound) ? 1 : 0;
					final float[] history = source == 0 && sourceX == x && sourceY == y && sourceZ == z ? HISTORY.get(sound) : null;
					final int rows = 1 + (history == null ? 0 : HISTORY_LENGTH);
					if (array.length < (count + rows) * STRIDE || sources.length < count + rows) {
						array = Arrays.copyOf(array, (count + rows) * STRIDE * 2);
						holder[0] = array;
						sources = Arrays.copyOf(sources, (count + rows) * 2);
						sourceHolder[0] = sources;
					}
					for (int row = 0; row < rows; row++) {
						final float rowX, rowY, rowZ;
						if (row == 0) {
							rowX = (float) sourceX;
							rowY = (float) sourceY;
							rowZ = (float) sourceZ;
						} else {
							rowX = history[(row - 1) * 3];
							rowY = history[(row - 1) * 3 + 1];
							rowZ = history[(row - 1) * 3 + 2];
							if (Float.isNaN(rowX)) {
								break;
							}
						}
						final int i = count * STRIDE;
						array[i] = rowX;
						array[i + 1] = rowY;
						array[i + 2] = rowZ;
						array[i + 3] = smoothed[0];
						array[i + 4] = smoothed[1];
						array[i + 5] = own;
						sources[count] = source;
						count++;
					}
				} catch (RuntimeException ignored) {
					// A sound another mod left half-initialized: skip it this time.
				}
			}
			return count;
		}
	}

	/** Called whenever a vehicle sound was moved (MTR setData, JCM setPos). */
	public static void recordPosition(Object sound) {
		if (!(sound instanceof MovingSoundInstanceAbstractMapping)) {
			return;
		}
		final MovingSoundInstanceAbstractMapping instance = (MovingSoundInstanceAbstractMapping) sound;
		final float x = (float) instance.getX2(), y = (float) instance.getY2(), z = (float) instance.getZ2();
		synchronized (SOUNDS) {
			float[] history = HISTORY.get(sound);
			if (history == null) {
				history = new float[HISTORY_LENGTH * 3];
				Arrays.fill(history, Float.NaN);
				HISTORY.put(sound, history);
			} else if (history[0] == x && history[1] == y && history[2] == z) {
				return;
			}
			System.arraycopy(history, 0, history, 3, history.length - 3);
			history[0] = x;
			history[1] = y;
			history[2] = z;
		}
	}

	/** Called when MTR creates a BVE sound set (one per car): remembers its motor loops and its run loop. */
	public static void tagBveMotorAndRun(Object bveVehicleSound) {
		if (tagFailed) {
			return;
		}
		try {
			if (holderField == null) {
				holderField = bveVehicleSound.getClass().getDeclaredField("vehicleLoopingSoundHolder");
				holderField.setAccessible(true);
				final Class<?> holderClass = holderField.getType();
				motorField = holderClass.getDeclaredField("soundLoopMotor");
				motorField.setAccessible(true);
				runField = holderClass.getDeclaredField("soundLoopRun");
				runField.setAccessible(true);
			}
			final Object holder = holderField.get(bveVehicleSound);
			if (holder == null) {
				return;
			}
			synchronized (SOUNDS) {
				final Object motors = motorField.get(holder);
				if (motors instanceof Object[]) {
					for (final Object motor : (Object[]) motors) {
						if (motor != null) {
							MOTOR_AND_RUN.add(motor);
						}
					}
				}
				final Object run = runField.get(holder);
				if (run != null) {
					MOTOR_AND_RUN.add(run);
				}
			}
		} catch (Throwable t) {
			tagFailed = true;
			com.egehew.emtra.EmtraMod.LOGGER.warn("Could not read MTR's BVE motor/run sounds, the whole riding train stays clear of the muffle: {}", t.toString());
		}
	}

	/** Motor or run track: tagged from MTR's BVE sound set, or recognised by its sound id (run0, run1, run2...). */
	private static boolean isMotorOrRun(MovingSoundInstanceAbstractMapping sound) {
		if (MOTOR_AND_RUN.contains(sound)) {
			return true;
		}
		Boolean byId = MOTOR_OR_RUN_BY_ID.get(sound);
		if (byId == null) {
			try {
				byId = MOTOR_OR_RUN_ID.matcher(sound.getId2().getPath()).find();
			} catch (Throwable t) {
				byId = false;
			}
			MOTOR_OR_RUN_BY_ID.put(sound, byId);
		}
		return byId;
	}

	/** toString of one tracked sound, to check that the Sound Physics Perfected marker is applied. */
	static String sample() {
		synchronized (SOUNDS) {
			for (final MovingSoundInstanceAbstractMapping sound : SOUNDS) {
				return sound.getClass().getName() + " -> " + sound;
			}
			return null;
		}
	}

	static boolean isEmpty() {
		synchronized (SOUNDS) {
			return SOUNDS.isEmpty();
		}
	}

	public static void beginScheduledFlush() {
		PLAYED_THIS_FLUSH.clear();
	}

	/** Replaces ScheduledSound's ClientWorld.playSoundAtBlockCenter call (one play per scheduled entry). */
	public static void playScheduled(Object scheduledSound, BlockPos blockPos, SoundEvent soundEvent, SoundCategory category, float volume, float pitch, Runnable vanilla) {
		if (PLAYED_THIS_FLUSH.put(scheduledSound, Boolean.TRUE) != null) {
			return;
		}
		playOneShot(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5, soundEvent, category, volume, pitch, vanilla);
	}

	/**
	 * Plays a one-shot vehicle sound (MTR, JCM scripts, FangSu scripts) as an EMTRA sound instance, so it is known as a
	 * vehicle sound (block limit, kept clear of the riding muffle). It starts before the next filter update, so the
	 * block limit and EMTRA's occlusion gain are applied right away.
	 */
	public static void playOneShot(double x, double y, double z, SoundEvent soundEvent, SoundCategory category, float volume, float pitch, Runnable vanilla) {
		try {
			final float[] occlusion = SoundFilters.occlusionAt(x, y, z);
			if (occlusion[2] > 0) {
				// More blocks than the limit between the listener and the vehicle: not heard at all.
				return;
			}
			final float occlusionGain = SoundFilters.occlusionActive() ? occlusion[0] : 1;
			if (occlusionGain < 0.01F) {
				return;
			}
			final OneShotSound sound = new OneShotSound(soundEvent, category, volume * occlusionGain, pitch, x, y, z);
			register(sound);
			MinecraftClient.getInstance().getSoundManager().play(new SoundInstance(sound));
		} catch (Throwable t) {
			vanilla.run();
		}
	}

	private static final class OneShotSound extends MovingSoundInstanceExtension {

		private OneShotSound(SoundEvent soundEvent, SoundCategory category, float volume, float pitch, double x, double y, double z) {
			super(soundEvent, category);
			setIsRepeatableMapped(false);
			setVolume(volume);
			setPitch(pitch);
			setX(x);
			setY(y);
			setZ(z);
		}

		@Override
		public void tick2() {
		}

		@Override
		public String toString() {
			return SPP_MARKER + "@" + Integer.toHexString(System.identityHashCode(this));
		}
	}
}
