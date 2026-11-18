package com.egehew.emtra.sound;

import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.ClientWorld;
import org.mtr.mapping.holder.LightType;
import org.mtr.mapping.holder.MinecraftClient;
import org.mtr.mapping.holder.MovingSoundInstanceAbstractMapping;
import org.mtr.mapping.holder.SoundCategory;
import org.mtr.mapping.holder.SoundEvent;
import org.mtr.mapping.holder.SoundInstance;
import org.mtr.mapping.holder.SoundManager;
import org.mtr.mapping.mapper.MovingSoundInstanceExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.regex.Pattern;

/**
 * Vehicle sounds: MTR's looping motor/running sounds and one-shot door/brake/compressor sounds, and the sounds of
 * vehicle scripts (JCM scripting, FangSu).
 * <ul>
 * <li>Their positions are tracked so {@link SoundFilters} can find their OpenAL sources and occlude them.</li>
 * <li>One-shot sounds: MTR's ScheduledSound played every scheduled sound once per scheduled entry (n entries in a tick
 * = each sound n times on top of itself, clipping). Each entry now plays once.</li>
 * </ul>
 */
public final class MtrSounds {

	private static final Set<MovingSoundInstanceAbstractMapping> SOUNDS = Collections.newSetFromMap(new WeakHashMap<>());
	private static final ThreadLocal<Long> UPDATING_VEHICLE = new ThreadLocal<>();
	private static final Map<Object, Long> OWNERS = new WeakHashMap<>();
	private static final Map<Object, Boolean> PLAYED_THIS_FLUSH = new IdentityHashMap<>();
	private static final Map<Object, float[]> SMOOTHED = new WeakHashMap<>();
	/** BVE metadata identifies motor and run loops even when a pack uses custom sound IDs. */
	private static final Set<Object> MOTOR_AND_RUN = Collections.newSetFromMap(new WeakHashMap<>());
	private static final Map<Object, Boolean> FULL_MUFFLE_BY_ID = new WeakHashMap<>();
	/**
	 * Last distinct positions of each sound (x, y, z triples, newest first). Minecraft copies an instance's position to
	 * its OpenAL source once per tick while MTR moves it every frame, so a moving train's source sits at one of these.
	 */
	private static final Map<Object, float[]> HISTORY = new WeakHashMap<>();
	private static final int HISTORY_LENGTH = 8;
	/** Examples include pack_motor2, pack/run_fast and pack_airzero. */
	private static final Pattern FULL_MUFFLE_ID = Pattern.compile("(^|[_/.-])(?:(?:motor|run)[^/]*|airhigh|airzero)$", Pattern.CASE_INSENSITIVE);
	private static java.lang.reflect.Field holderField, motorField, runField;
	private static boolean tagFailed;
	private static final Map<Long, double[]> OCCLUSION_CACHE = new java.util.HashMap<>();
	private static final float[] REACH = new float[2];
	private static final double[] APPARENT = new double[3];
	/** Unmodified gain and position when occlusion is disabled. */
	private static final float[] REACH_CLEAR_GAIN = {1, 1};
	private static final double[] CLEAR_REACH = {1, 1, Double.NaN, 0, 0};
	private static final double MAX_DISTANCE = 128;
	private static final float OCCLUSION_FADE_SECONDS = 0.3F;
	private MtrSounds() {
	}

	public static void withVehicle(long vehicleId, Runnable update) {
		final Long previous = UPDATING_VEHICLE.get();
		UPDATING_VEHICLE.set(vehicleId);
		try {
			update.run();
		} finally {
			if (previous == null) {
				UPDATING_VEHICLE.remove();
			} else {
				UPDATING_VEHICLE.set(previous);
			}
		}
	}

	public static Long updatingVehicle() {
		return UPDATING_VEHICLE.get();
	}

	public static void register(Object sound) {
		if (sound instanceof MovingSoundInstanceAbstractMapping) {
			synchronized (SOUNDS) {
				SOUNDS.add((MovingSoundInstanceAbstractMapping) sound);
				recordOwner(sound);
			}
		}
	}

	private static void recordOwner(Object sound) {
		final Long owner = updatingVehicle();
		if (owner != null) {
			OWNERS.put(sound, owner);
		}
	}

	private static double[] reachFromVehicle(double listenerX, double listenerY, double listenerZ, double x, double y, double z) {
		final long key = ((long) Math.floor(x) & 0x3FFFFFF) << 38 | ((long) Math.floor(z) & 0x3FFFFFF) << 12 | ((long) Math.floor(y) & 0xFFF);
		double[] reach = OCCLUSION_CACHE.get(key);
		if (reach == null) {
			SoundPaths.evaluate(listenerX, listenerY, listenerZ, x, y, z, REACH, APPARENT);
			reach = new double[]{REACH[0], REACH[1], APPARENT[0], APPARENT[1], APPARENT[2]};
			OCCLUSION_CACHE.put(key, reach);
		}
		return reach;
	}

	/** Output of {@link #collect}: per vehicle sound, x, y, z (where its OpenAL source is), gain, gainHF, mild cabin filter flag. */
	static final int STRIDE = 6;

	/**
	 * Collects playing vehicle sounds near the listener for occlusion and cabin muffle.
	 * Returns the count. sources[i] is the OpenAL source, or 0 when it must be matched by position.
	 */
	static int collect(float[][] holder, int[][] sourceHolder, double listenerX, double listenerY, double listenerZ, float seconds, boolean cabinFilter) {
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
			final SoundManager soundManager = MinecraftClient.getInstance().getSoundManager();
			final boolean occlusion = SoundFilters.occlusionActive();
			final boolean debug = SoundDebug.beginPass(listenerX, listenerY, listenerZ, occlusion);
			int count = 0;
			for (final MovingSoundInstanceAbstractMapping sound : SOUNDS) {
				try {
					// The vehicle's own position (getX2 may be the apparent position Minecraft plays the sound at)
					final ApparentPosition apparentPosition = sound instanceof ApparentPosition ? (ApparentPosition) sound : null;
					final double x = apparentPosition == null ? sound.getX2() : apparentPosition.emtra$trueX();
					final double y = apparentPosition == null ? sound.getY2() : apparentPosition.emtra$trueY();
					final double z = apparentPosition == null ? sound.getZ2() : apparentPosition.emtra$trueZ();
					final double dx = x - listenerX, dy = y - listenerY, dz = z - listenerZ;
					if (dx * dx + dy * dy + dz * dz > MAX_DISTANCE * MAX_DISTANCE || !soundManager.isPlaying(new SoundInstance(sound))) {
						continue;
					}
					final int source = SoundSources.sourceOf(sound);
					final double sourceX = sound.getX2(), sourceY = sound.getY2(), sourceZ = sound.getZ2();
					final double[] reach = occlusion ? reachFromVehicle(listenerX, listenerY, listenerZ, x, y, z) : CLEAR_REACH;
					if (apparentPosition != null) {
						if (occlusion && !Double.isNaN(reach[2])) {
							apparentPosition.emtra$setApparentTarget(reach[2], reach[3], reach[4]);
						} else {
							apparentPosition.emtra$setApparentTarget(Double.NaN, 0, 0);
						}
					}
					float targetGain = 1, targetGainHF = 1;
					if (occlusion) {
						// One-shot sounds already had the gain applied to their volume when they started.
						targetGain = sound instanceof OneShotSound && reach[0] > 0 ? 1 : (float) reach[0];
						targetGainHF = (float) reach[1];
					}
					float[] smoothed = SMOOTHED.get(sound);
					if (!occlusion) {
						smoothed = REACH_CLEAR_GAIN;
					} else if (smoothed == null) {
						smoothed = new float[]{targetGain, targetGainHF};
						SMOOTHED.put(sound, smoothed);
					} else {
						smoothed[0] = SoundFilters.approach(smoothed[0], targetGain, OCCLUSION_FADE_SECONDS, seconds);
						smoothed[1] = SoundFilters.approach(smoothed[1], targetGainHF, OCCLUSION_FADE_SECONDS, seconds);
					}
					if (debug) {
						SoundDebug.sound(sound, x, y, z, source, targetGain, smoothed[0]);
					}
					if (!cabinFilter && smoothed[0] >= 0.999F && smoothed[1] >= 0.999F) {
						continue;
					}
					final boolean mildCabinFilter = cabinFilter && !needsFullCabinMuffle(sound)
							&& SoundFilters.belongsToRidingVehicle(OWNERS.get(sound), x, y, z);
					final float[] history = source == 0 ? HISTORY.get(sound) : null;
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
						array[i + 5] = mildCabinFilter ? 1 : 0;
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

	/** Per block: {tunnel factor, nanoTime read}. */
	private static final Map<Long, double[]> TUNNEL_CACHE = new java.util.HashMap<>();
	private static final long TUNNEL_REFRESH_NANOS = 1_000_000_000L;

	/**
	 * For the sound debug log. 0 under the open sky, 1 deep in a tunnel: from the sky light 1 to 3 blocks above the sound (the car body), which
	 * fades over the first blocks inside a tunnel mouth, so the change is gradual. Sky light the client has not computed
	 * yet reads as 15 (outside) for a moment; the result is smoothed with the gain anyway. Render thread.
	 */
	static double tunnelFactor(double x, double y, double z) {
		final int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
		final long key = ((long) bx & 0x3FFFFFF) << 38 | ((long) bz & 0x3FFFFFF) << 12 | (by & 0xFFF);
		final long now = System.nanoTime();
		double[] cached = TUNNEL_CACHE.get(key);
		if (cached != null && now - (long) cached[1] < TUNNEL_REFRESH_NANOS) {
			return cached[0];
		}
		final ClientWorld world = MinecraftClient.getInstance().getWorldMapped();
		if (world == null) {
			return 0;
		}
		int sky = 0;
		for (int up = 1; up <= 3; up++) {
			sky = Math.max(sky, world.getLightLevel(LightType.getSkyMapped(), new BlockPos(bx, by + up, bz)));
		}
		final double factor = Math.max(0, Math.min(1, (12 - sky) / 10.0));
		if (TUNNEL_CACHE.size() > 4096) {
			TUNNEL_CACHE.clear();
		}
		TUNNEL_CACHE.put(key, new double[]{factor, now});
		return factor;
	}

	static double distanceTo(double dx, double dy, double dz) {
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	/** Called whenever a vehicle sound was moved (MTR setData, JCM setPos). */
	public static void recordPosition(Object sound) {
		if (!(sound instanceof MovingSoundInstanceAbstractMapping)) {
			return;
		}
		final MovingSoundInstanceAbstractMapping instance = (MovingSoundInstanceAbstractMapping) sound;
		final float x = (float) instance.getX2(), y = (float) instance.getY2(), z = (float) instance.getZ2();
		synchronized (SOUNDS) {
			recordOwner(sound);
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
			com.egehew.emtra.EmtraMod.LOGGER.warn("Could not read MTR's BVE motor/run sounds, using sound IDs to classify motor/run sounds: {}", t.toString());
		}
	}

	/** Own-train sounds that should get the same cabin muffle as exterior sounds. */
	private static boolean needsFullCabinMuffle(MovingSoundInstanceAbstractMapping sound) {
		if (MOTOR_AND_RUN.contains(sound)) {
			return true;
		}
		Boolean byId = FULL_MUFFLE_BY_ID.get(sound);
		if (byId == null) {
			try {
				byId = FULL_MUFFLE_ID.matcher(sound.getId2().getPath()).find();
			} catch (Throwable t) {
				byId = false;
			}
			FULL_MUFFLE_BY_ID.put(sound, byId);
		}
		return byId;
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
	public static void playScheduled(Object scheduledSound, Long owner, BlockPos blockPos, SoundEvent soundEvent, SoundCategory category, float volume, float pitch, Runnable vanilla) {
		if (PLAYED_THIS_FLUSH.put(scheduledSound, Boolean.TRUE) != null) {
			return;
		}
		playOneShot(owner, blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5, soundEvent, category, volume, pitch, vanilla);
	}

	/**
	 * Plays a one-shot vehicle sound (MTR, JCM scripts, FangSu scripts) as an EMTRA sound instance, so it is known as a
	 * vehicle sound for occlusion and cabin muffle. It starts before the next filter update, so whether
	 * and how loud it reaches the listener ({@link SoundPaths}) is applied right away.
	 */
	public static void playOneShot(double x, double y, double z, SoundEvent soundEvent, SoundCategory category, float volume, float pitch, Runnable vanilla) {
		playOneShot(updatingVehicle(), x, y, z, soundEvent, category, volume, pitch, vanilla);
	}

	private static void playOneShot(Long owner, double x, double y, double z, SoundEvent soundEvent, SoundCategory category, float volume, float pitch, Runnable vanilla) {
		try {
			final float[] occlusion = SoundFilters.occlusionAt(x, y, z);
			if (occlusion[2] > 0) {
				// More blocks than the limit between the listener and the vehicle: not heard at all.
				return;
			}
			final float occlusionGain = occlusion[0];
			if (occlusionGain < 0.01F) {
				return;
			}
			final OneShotSound sound = new OneShotSound(soundEvent, category, volume * occlusionGain, pitch, x, y, z);
			register(sound);
			if (owner != null) {
				synchronized (SOUNDS) {
					OWNERS.put(sound, owner);
				}
			}
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
	}
}
