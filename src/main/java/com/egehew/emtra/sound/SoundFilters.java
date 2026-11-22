package com.egehew.emtra.sound;

import com.egehew.emtra.EmtraMod;
import com.egehew.emtra.render.EmtraClientConfig;
import com.egehew.emtra.render.mixin.VehicleRidingMovementAccessor;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;
import org.mtr.core.data.VehicleCar;
import org.mtr.core.tool.Vector;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.ClientWorld;
import org.mtr.mapping.holder.MinecraftClient;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.data.VehicleExtension;

import java.util.Arrays;

/**
 * EFX low-pass filters on OpenAL sources for two effects that are multiplied together.
 * <ul>
 * <li><b>Occlusion of MTR vehicle sounds</b>: blocks between the listener and the sound absorb it. A vehicle
 * sound with no path to the listener through the air or through a thin wall ({@link SoundPaths}) is silent.
 * Sound Physics Remastered handles occlusion when installed.</li>
 * <li><b>Train muffle</b>: while riding, sounds are muffled as if heard through the car body. It fades in when
 * boarding, out when alighting, and is reduced while the doors are open (following MTR's door value).
 * The riding train's other sounds get a mild filter, while its motor, run and air sounds use the full filter.
 * </li>
 * </ul>
 * Only valid OpenAL queries are used (a source's direct filter cannot be read back in OpenAL Soft, and an invalid
 * query leaves an error that Minecraft's sound thread would report as its own and then drop the sound), and any
 * error left by these calls is cleared. Runs on the render thread every 50 ms and does nothing while there is nothing
 * to filter.
 */
public final class SoundFilters {

	private static final int MAX_SOURCE_ID = 384;
	private static final long UPDATE_INTERVAL_NANOS = 50_000_000L;
	private static final float EPSILON = 0.002F;
	private static final float OWN_SOUND_MUFFLE_SCALE = 0.15F;
	private static final int MAX_OCCLUDING_BLOCKS = 12;

	private static final int[] OUR_FILTER = new int[MAX_SOURCE_ID + 1];
	private static final boolean[] ATTACHED = new boolean[MAX_SOURCE_ID + 1];

	private static final boolean REMASTERED_LOADED = remasteredLoaded();

	private static final float[] X = new float[1];
	private static final float[] Y = new float[1];
	private static final float[] Z = new float[1];
	private static final float[][] MTR_SOUNDS = {new float[0]};
	private static final int[][] MTR_SOURCES = {new int[0]};
	private static final int[] VEHICLE_ENTRY = new int[MAX_SOURCE_ID + 1];
	private static final float[] OCCLUSION_RESULT = new float[3];

	/** Vehicle cars as capsules: x1, y1, z1, x2, y2, z2, radius. */
	private static double[] capsules = new double[0];
	private static int capsuleCount;
	private static long cabinVehicleId;

	private static float muffle;
	/** Riding (0..1, fades with the boarding fade time) and door openness (0..1, fades with the door fade time). */
	private static float riding, door;
	private static float doorTarget;
	private static boolean anyAttached;
	private static long lastNanos;
	private static long context;
	private static boolean efx;
	private static boolean broken;

	private SoundFilters() {
	}

	/** Called once per frame from the render thread. */
	public static void update() {
		if (broken) {
			return;
		}
		final long now = System.nanoTime();
		if (REMASTERED_LOADED) {
			smoothRemastered(now);
		}
		if (now - lastNanos < UPDATE_INTERVAL_NANOS) {
			return;
		}
		final float seconds = lastNanos == 0 ? 0 : Math.min(0.25F, (now - lastNanos) / 1E9F);
		lastNanos = now;
		try {
			// Boarding/alighting and the doors fade separately: the doors with their own, longer fade, so opening and
			// closing them lets the outside in gradually instead of at once.
			riding = approach(riding, findMuffleTarget(), EmtraClientConfig.TRAIN_MUFFLE_FADE_MS / 1000F, seconds);
			if (riding == 0) {
				cabinVehicleId = 0;
				capsuleCount = 0;
			}
			door = approach(door, doorTarget, EmtraClientConfig.TRAIN_MUFFLE_DOOR_FADE_MS / 1000F, seconds);
			muffle = riding * (1 - door * EmtraClientConfig.TRAIN_MUFFLE_DOOR_OPEN / 100F);
			final boolean vehicleSounds = !MtrSounds.isEmpty() && (occlusionActive() || EmtraClientConfig.SOUND_DEBUG || muffle > EPSILON);
			if (muffle <= EPSILON && !vehicleSounds && !anyAttached) {
				return;
			}
			if (!checkContext()) {
				return;
			}
			try {
				apply(vehicleSounds, seconds);
				SoundDebug.finishPass();
			} finally {
				AL10.alGetError();
			}
		} catch (Throwable t) {
			broken = true;
			EmtraMod.LOGGER.error("EMTRA sound filters disabled after an error", t);
		}
	}

	private static long lastSmoothNanos;
	private static boolean smoothBroken;

	/** Every frame: glide SPR's moving-sound filters (see RemasteredSoundFilters.smooth). */
	private static void smoothRemastered(long now) {
		final float seconds = lastSmoothNanos == 0 ? 0 : Math.min(0.25F, (now - lastSmoothNanos) / 1E9F);
		lastSmoothNanos = now;
		if (smoothBroken) {
			return;
		}
		try {
			RemasteredSoundFilters.smooth(seconds);
			AL10.alGetError();
		} catch (Throwable t) {
			// SPR keeps working unsmoothed.
			smoothBroken = true;
			EmtraMod.LOGGER.error("EMTRA: Sound Physics Remastered smoothing disabled after an error", t);
		}
	}

	private static boolean remasteredLoaded() {
		try {
			Class.forName("com.sonicether.soundphysics.SoundPhysics", false, SoundFilters.class.getClassLoader());
			return true;
		} catch (ClassNotFoundException | LinkageError e) {
			return false;
		}
	}

	static boolean occlusionActive() {
		return EmtraClientConfig.MTR_SOUND_OCCLUSION && !REMASTERED_LOADED;
	}

	/**
	 * Occlusion between the listener and a point, for one-shot sounds that start before the next filter update:
	 * returns {gain, gainHF, 1 if it does not reach the listener at all}.
	 */
	public static float[] occlusionAt(double x, double y, double z) {
		OCCLUSION_RESULT[0] = 1;
		OCCLUSION_RESULT[1] = 1;
		OCCLUSION_RESULT[2] = 0;
		if (broken || !occlusionActive()) {
			return OCCLUSION_RESULT;
		}
		try {
			if (ALC10.alcGetCurrentContext() == 0) {
				return OCCLUSION_RESULT;
			}
			AL10.alGetListener3f(AL10.AL_POSITION, X, Y, Z);
			AL10.alGetError();
			SoundPaths.evaluate(X[0], Y[0], Z[0], x, y, z, OCCLUSION_RESULT, null);
			OCCLUSION_RESULT[2] = OCCLUSION_RESULT[0] < 0.01F ? 1 : 0;
		} catch (Throwable ignored) {
		}
		return OCCLUSION_RESULT;
	}

	/** Use the vehicle ID when MTR provides it. Script sounds can fall back to their position. */
	static boolean belongsToRidingVehicle(Long owner, double x, double y, double z) {
		return owner == null ? capsuleCount > 0 && insideVehicle(x, y, z) : owner == cabinVehicleId;
	}

	static float cabinMuffleAmount(float fullAmount, boolean relative, boolean mild) {
		return relative ? 0 : fullAmount * (mild ? OWN_SOUND_MUFFLE_SCALE : 1);
	}

	static float approach(float value, float target, float fadeSeconds, float seconds) {
		if (fadeSeconds <= 0.001F || seconds == 0) {
			return target;
		}
		// Exponential approach: about 95% of the way after the fade time.
		final float result = value + (target - value) * (1 - (float) Math.exp(-3 * seconds / fadeSeconds));
		return Math.abs(target - result) < EPSILON ? target : result;
	}

	/** 1 while riding (and the muffle is enabled), else 0; also sets {@link #doorTarget}. */
	private static float findMuffleTarget() {
		// Retain the last cabin during the alighting fade.
		if (!EmtraClientConfig.TRAIN_MUFFLE) {
			return 0;
		}
		final long ridingVehicleId = VehicleRidingMovementAccessor.emtra$getRidingVehicleId();
		if (ridingVehicleId == 0) {
			return 0;
		}
		final MinecraftClientData data = MinecraftClientData.getInstance();
		if (data == null || data.vehicles == null) {
			return 0;
		}
		VehicleExtension vehicle = null;
		for (final VehicleExtension checkVehicle : data.vehicles) {
			if (checkVehicle.getId() == ridingVehicleId) {
				vehicle = checkVehicle;
				break;
			}
		}
		if (vehicle == null) {
			return 0;
		}
		cabinVehicleId = ridingVehicleId;
		capsuleCount = 0;
		buildCapsules(vehicle);
		final double doorValue = Math.max(0, Math.min(1, vehicle.persistentVehicleData.getDoorValue()));
		// MTR's door value runs 0..1 over the whole door cycle, but every door animation type moves the doors while it
		// is below 0.5 (fully open from 0.5 on, closing between 0.5 and 0). Follow that movement, with an S-curve.
		final double moving = Math.min(1, doorValue * 2);
		doorTarget = (float) (moving * moving * (3 - 2 * moving));
		return 1;
	}

	private static void buildCapsules(VehicleExtension vehicle) {
		final ObjectArrayList<ObjectObjectImmutablePair<VehicleCar, ObjectArrayList<ObjectObjectImmutablePair<Vector, Vector>>>> cars = vehicle.getVehicleCarsAndPositions();
		if (capsules.length < cars.size() * 7) {
			capsules = new double[cars.size() * 7];
		}
		for (final ObjectObjectImmutablePair<VehicleCar, ObjectArrayList<ObjectObjectImmutablePair<Vector, Vector>>> car : cars) {
			final ObjectArrayList<ObjectObjectImmutablePair<Vector, Vector>> bogies = car.right();
			if (bogies.isEmpty()) {
				continue;
			}
			final Vector start = bogies.get(0).left();
			final Vector end = bogies.get(bogies.size() - 1).right();
			final double dx = end.x - start.x;
			final double dy = end.y - start.y;
			final double dz = end.z - start.z;
			final double span = Math.sqrt(dx * dx + dy * dy + dz * dz);
			final double extra = span < 1E-3 ? 0 : Math.max(0, (car.left().getLength() - span) / 2) / span;
			final int i = capsuleCount * 7;
			capsules[i] = start.x - dx * extra;
			capsules[i + 1] = start.y - dy * extra;
			capsules[i + 2] = start.z - dz * extra;
			capsules[i + 3] = end.x + dx * extra;
			capsules[i + 4] = end.y + dy * extra;
			capsules[i + 5] = end.z + dz * extra;
			capsules[i + 6] = car.left().getWidth() / 2 + 2;
			capsuleCount++;
		}
	}

	private static boolean insideVehicle(double x, double y, double z) {
		for (int c = 0; c < capsuleCount; c++) {
			final int i = c * 7;
			final double ax = capsules[i], ay = capsules[i + 1], az = capsules[i + 2];
			final double bx = capsules[i + 3] - ax, by = capsules[i + 4] - ay, bz = capsules[i + 5] - az;
			final double px = x - ax, py = y - ay, pz = z - az;
			final double lengthSquared = bx * bx + by * by + bz * bz;
			final double t = lengthSquared < 1E-6 ? 0 : Math.max(0, Math.min(1, (px * bx + py * by + pz * bz) / lengthSquared));
			final double qx = px - bx * t, qy = py - by * t, qz = pz - bz * t;
			final double radius = capsules[i + 6];
			if (qx * qx + qy * qy + qz * qz <= radius * radius) {
				return true;
			}
		}
		return false;
	}

	private static boolean checkContext() {
		final long currentContext = ALC10.alcGetCurrentContext();
		if (currentContext == 0) {
			return false;
		}
		if (currentContext != context) {
			// New sound engine (F3+T, device change): old filter names belong to the old context.
			context = currentContext;
			Arrays.fill(OUR_FILTER, 0);
			Arrays.fill(ATTACHED, false);
			anyAttached = false;
			efx = ALC10.alcIsExtensionPresent(ALC10.alcGetContextsDevice(currentContext), "ALC_EXT_EFX");
			if (!efx) {
				EmtraMod.LOGGER.warn("OpenAL EFX is not available, EMTRA sound filters disabled");
			}
		}
		return efx;
	}

	private static void apply(boolean vehicleSounds, float seconds) {
		final float muffleVolume = EmtraClientConfig.TRAIN_MUFFLE_VOLUME / 100F;
		final float muffleTreble = EmtraClientConfig.TRAIN_MUFFLE_TREBLE / 100F;
		AL10.alGetListener3f(AL10.AL_POSITION, X, Y, Z);
		final double listenerX = X[0], listenerY = Y[0], listenerZ = Z[0];
		if (vehicleSounds && occlusionActive()) {
			SoundPaths.beginUpdate(listenerX, listenerY, listenerZ);
		}
		final int mtrSounds = vehicleSounds ? MtrSounds.collect(MTR_SOUNDS, MTR_SOURCES, listenerX, listenerY, listenerZ, seconds, muffle > EPSILON) : 0;
		if (mtrSounds == 0 && muffle <= EPSILON && !anyAttached) {
			return;
		}
		final float[] mtr = MTR_SOUNDS[0];
		final int[] mtrSources = MTR_SOURCES[0];
		// Vehicle sounds whose OpenAL source is known.
		Arrays.fill(VEHICLE_ENTRY, -1);
		for (int i = 0; i < mtrSounds; i++) {
			final int source = mtrSources[i];
			if (source > 0 && source <= MAX_SOURCE_ID) {
				VEHICLE_ENTRY[source] = i;
			}
		}
		boolean stillAttached = false;

		for (int source = 1; source <= MAX_SOURCE_ID; source++) {
			if (!AL10.alIsSource(source)) {
				ATTACHED[source] = false;
				continue;
			}
			final boolean relative = AL10.alGetSourcei(source, AL10.AL_SOURCE_RELATIVE) == AL10.AL_TRUE;
			AL10.alGetSource3f(source, AL10.AL_POSITION, X, Y, Z);
			final float sx = X[0], sy = Y[0], sz = Z[0];

			int entry = VEHICLE_ENTRY[source];
			if (entry < 0 && !relative) {
				for (int i = 0; i < mtrSounds; i++) {
					final int j = i * MtrSounds.STRIDE;
					if (mtrSources[i] == 0 && Math.abs(mtr[j] - sx) < 0.01F && Math.abs(mtr[j + 1] - sy) < 0.01F && Math.abs(mtr[j + 2] - sz) < 0.01F) {
						entry = i;
						break;
					}
				}
			}
			float occlusionGain = 1, occlusionGainHF = 1;
			boolean mildCabinFilter = false;
			if (entry >= 0) {
				statMatched++;
				final int j = entry * MtrSounds.STRIDE;
				occlusionGain = mtr[j + 3];
				occlusionGainHF = mtr[j + 4];
				mildCabinFilter = mtr[j + 5] > 0;
				if (occlusionGain < 0.01F) {
					statSilenced++;
				}
			}

			final float amount = cabinMuffleAmount(muffle, relative, mildCabinFilter);

			final boolean filterNeeded = occlusionGain < 0.999F || occlusionGainHF < 0.999F || amount > EPSILON;
			final float gain = filterNeeded ? occlusionGain * (1 - amount * (1 - muffleVolume)) : 1;
			final float gainHF = filterNeeded ? occlusionGainHF * (1 - amount * (1 - muffleTreble)) : 1;
			// Serialize the fallback with SPR's first attachment and Minecraft's source deletion.
			if (REMASTERED_LOADED) {
				synchronized (RemasteredSoundFilters.class) {
					if (!AL10.alIsSource(source)) {
						ATTACHED[source] = false;
						continue;
					}
					if (RemasteredSoundFilters.update(source, gain, gainHF)) {
						ATTACHED[source] = false;
					} else {
						applyDirectFilter(source, filterNeeded, gain, gainHF);
					}
				}
			} else {
				applyDirectFilter(source, filterNeeded, gain, gainHF);
			}
			stillAttached |= filterNeeded;
		}
		anyAttached = stillAttached;
		if (EmtraClientConfig.RENDER_STATS) {
			statUpdates++;
			if (stillAttached) {
				statFiltered++;
			}
			final long now = System.nanoTime();
			if (now - statLast > 10_000_000_000L) {
				statLast = now;
				EmtraMod.LOGGER.info("Sound filters (10 s): updates={}, updatesWithFilters={}, vehicleSoundRows={}, vehicleSourcesMatched={}, silencedByBlocks={}, muffle={}, occlusion={}",
						statUpdates, statFiltered, mtrSounds, statMatched, statSilenced, String.format("%.2f", muffle), occlusionActive());
				statUpdates = 0;
				statFiltered = 0;
				statMatched = 0;
				statSilenced = 0;
			}
		}
	}

	private static long statLast, statUpdates, statFiltered, statMatched, statSilenced;

	private static void applyDirectFilter(int source, boolean filterNeeded, float gain, float gainHF) {
		if (filterNeeded) {
			setFilter(source, gain, gainHF);
		} else if (ATTACHED[source]) {
			AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
		}
		ATTACHED[source] = filterNeeded;
	}

	private static void setFilter(int source, float gain, float gainHF) {
		int filter = OUR_FILTER[source];
		if (filter == 0) {
			filter = EXTEfx.alGenFilters();
			EXTEfx.alFilteri(filter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
			OUR_FILTER[source] = filter;
		}
		EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAIN, Math.max(0, Math.min(1, gain)));
		EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAINHF, Math.max(0, Math.min(1, gainHF)));
		AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, filter);
	}

	/**
	 * Occlusion of a vehicle sound. MTR places vehicle sounds at rail level, but the sound comes from the whole car body
	 * above the rails: seen from a platform, the line to rail level runs through the platform edge while the train is in
	 * plain view. Lines are checked to rail level and 1 and 2 blocks above it (not higher: a low tunnel roof would let a train be heard through the ground); the most open one counts.
	 */
	static int countVehicleOcclusion(double listenerX, double listenerY, double listenerZ, double x, double y, double z) {
		int best = Integer.MAX_VALUE;
		for (int height = 0; height <= 2 && best > 0; height++) {
			best = Math.min(best, countOccludingBlocks(listenerX, listenerY, listenerZ, x, y + height, z));
		}
		return best;
	}

	/**
	 * Opaque blocks crossed by the straight line from the listener to the sound (voxel traversal), ignoring the block
	 * the listener is in and the last 1.5 blocks before the sound (track bed under the train).
	 */
	static int countOccludingBlocks(double x1, double y1, double z1, double x2, double y2, double z2) {
		final ClientWorld world = MinecraftClient.getInstance().getWorldMapped();
		if (world == null) {
			return 0;
		}
		final double dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
		final double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (length < 1.5 || length > 160) {
			return 0;
		}
		final double stopAt = (length - 1.5) / length;
		int x = (int) Math.floor(x1), y = (int) Math.floor(y1), z = (int) Math.floor(z1);
		final int stepX = dx > 0 ? 1 : -1, stepY = dy > 0 ? 1 : -1, stepZ = dz > 0 ? 1 : -1;
		final double deltaX = dx == 0 ? Double.MAX_VALUE : Math.abs(1 / dx);
		final double deltaY = dy == 0 ? Double.MAX_VALUE : Math.abs(1 / dy);
		final double deltaZ = dz == 0 ? Double.MAX_VALUE : Math.abs(1 / dz);
		double maxX = dx == 0 ? Double.MAX_VALUE : (dx > 0 ? Math.floor(x1) + 1 - x1 : x1 - Math.floor(x1)) * deltaX;
		double maxY = dy == 0 ? Double.MAX_VALUE : (dy > 0 ? Math.floor(y1) + 1 - y1 : y1 - Math.floor(y1)) * deltaY;
		double maxZ = dz == 0 ? Double.MAX_VALUE : (dz > 0 ? Math.floor(z1) + 1 - z1 : z1 - Math.floor(z1)) * deltaZ;
		int blocks = 0;
		while (true) {
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
			if (t >= stopAt) {
				return blocks;
			}
			if (world.getBlockState(new BlockPos(x, y, z)).isOpaque() && ++blocks >= MAX_OCCLUDING_BLOCKS) {
				return blocks;
			}
		}
	}
}
