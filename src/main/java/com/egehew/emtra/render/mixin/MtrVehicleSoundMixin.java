package com.egehew.emtra.render.mixin;

import com.egehew.emtra.render.EmtraClientConfig;
import com.egehew.emtra.sound.ApparentPosition;
import com.egehew.emtra.sound.MtrSounds;
import com.egehew.emtra.sound.ExactSoundPosition;
import org.mtr.core.tool.Vector;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.SoundCategory;
import org.mtr.mapping.holder.SoundEvent;
import org.mtr.mapping.mapper.MovingSoundInstanceExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MTR's looping vehicle sounds (BVE motor, run, flange, noise, shoe, compressor loops), set by MTR every frame with
 * setData(volume, pitch, blockPos), which stops the loop when the volume is 0 and starts it again when it is above 0.
 * <ul>
 * <li>Tracked by {@link MtrSounds}; their recent positions are
 * recorded so their OpenAL sources can be found while the train moves.</li>
 * <li>Volume above 1 only multiplies the audible range in Minecraft (BVE motors x5 = 640 blocks): capped at 1.</li>
 * <li>Volume and pitch are smoothed. MTR's motor output follows the sign of the client's speed change, which flickers
 * between power, coast and brake from frame to frame (packs with BreakerDelay = 0 have no delay at all); a single
 * frame at volume 0 stopped the loop and the next frame restarted it from the beginning: a click each time. Smoothed,
 * a one-frame drop does not stop the loop, loops start from silence and fade to silence before they stop.</li>
 * <li>Apparent position ({@link ApparentPosition}): the position Minecraft plays the loop at can be moved towards the
 * opening the sound comes through, smoothly; EMTRA's own calculations keep using the vehicle's position.</li>
 * </ul>
 */
@Mixin(targets = "org.mtr.mod.sound.VehicleLoopingSoundInstance", remap = false)
public abstract class MtrVehicleSoundMixin extends MovingSoundInstanceExtension implements ApparentPosition {

	@Unique
	private static final float EMTRA$MOVE_SECONDS = 0.25F;

	@Unique
	private boolean emtra$apparent;

	@Unique
	private double emtra$targetX = Double.NaN, emtra$targetY, emtra$targetZ;
	@Unique
	private double emtra$apparentX, emtra$apparentY, emtra$apparentZ;

	protected MtrVehicleSoundMixin(SoundEvent soundEvent, SoundCategory soundCategory) {
		super(soundEvent, soundCategory);
	}

	@Override
	public void emtra$setApparentTarget(double x, double y, double z) {
		emtra$targetX = x;
		emtra$targetY = y;
		emtra$targetZ = z;
	}

	@Unique
	private double emtra$exactX = Double.NaN, emtra$exactY, emtra$exactZ;

	/** The car's exact position (see ExactSoundPosition), or MTR's block position before the first update. */
	@Unique
	private double emtra$baseX() {
		return Double.isNaN(emtra$exactX) ? super.getX2() : emtra$exactX;
	}

	@Unique
	private double emtra$baseY() {
		return Double.isNaN(emtra$exactX) ? super.getY2() : emtra$exactY;
	}

	@Unique
	private double emtra$baseZ() {
		return Double.isNaN(emtra$exactX) ? super.getZ2() : emtra$exactZ;
	}

	@Inject(method = "setData", at = @At("HEAD"), require = 0, remap = false)
	private void emtra$exactPosition(float volume, float pitch, BlockPos blockPos, CallbackInfo ci) {
		final Vector exact = ExactSoundPosition.matching(blockPos.getX(), blockPos.getY(), blockPos.getZ());
		if (exact != null) {
			emtra$exactX = exact.x;
			emtra$exactY = exact.y + ExactSoundPosition.BODY_HEIGHT;
			emtra$exactZ = exact.z;
		}
	}

	@Override
	public double emtra$trueX() {
		return emtra$baseX();
	}

	@Override
	public double emtra$trueY() {
		return emtra$baseY();
	}

	@Override
	public double emtra$trueZ() {
		return emtra$baseZ();
	}

	/** Position Minecraft plays the sound at (getX() is final and calls this). */
	@Override
	public double getX2() {
		return emtra$apparent ? emtra$apparentX : emtra$baseX();
	}

	@Override
	public double getY2() {
		return emtra$apparent ? emtra$apparentY : emtra$baseY();
	}

	@Override
	public double getZ2() {
		return emtra$apparent ? emtra$apparentZ : emtra$baseZ();
	}

	@Unique
	private static final float EMTRA$ATTACK_SECONDS = 0.08F;
	@Unique
	private static final float EMTRA$RELEASE_SECONDS = 0.15F;
	@Unique
	private static final float EMTRA$PITCH_SECONDS = 0.08F;

	@Unique
	private float emtra$volume;
	@Unique
	private float emtra$pitch;
	@Unique
	private long emtra$lastNanos;
	@Unique
	private float emtra$seconds;

	@Inject(method = "<init>*", at = @At("TAIL"), require = 0, remap = false)
	private void emtra$register(CallbackInfo ci) {
		MtrSounds.register(this);
	}

	@ModifyVariable(method = "setData", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0, remap = false)
	private float emtra$volume(float volume) {
		final long now = System.nanoTime();
		emtra$seconds = emtra$lastNanos == 0 ? 0 : Math.min(0.25F, (now - emtra$lastNanos) / 1E9F);
		emtra$lastNanos = now;
		float target = EmtraClientConfig.LIMIT_VEHICLE_SOUND_RANGE ? Math.min(volume, 1) : volume;
		if (!EmtraClientConfig.SMOOTH_VEHICLE_SOUNDS) {
			emtra$volume = target;
			return emtra$volume;
		}
		target = Math.max(0, target);
		final float time = target > emtra$volume ? EMTRA$ATTACK_SECONDS : EMTRA$RELEASE_SECONDS;
		emtra$volume += (target - emtra$volume) * (1 - (float) Math.exp(-emtra$seconds / time));
		if (target <= 0 && emtra$volume < 0.003F) {
			emtra$volume = 0;
		}
		return emtra$volume;
	}

	@ModifyVariable(method = "setData", at = @At("HEAD"), argsOnly = true, ordinal = 1, require = 0, remap = false)
	private float emtra$pitch(float pitch) {
		if (!EmtraClientConfig.SMOOTH_VEHICLE_SOUNDS || pitch <= 0) {
			return pitch;
		}
		if (emtra$pitch <= 0 || emtra$volume <= 0.003F) {
			// Silent or just starting: take the pitch directly, nothing audible to glide from.
			emtra$pitch = pitch;
		} else {
			emtra$pitch += (pitch - emtra$pitch) * (1 - (float) Math.exp(-emtra$seconds / EMTRA$PITCH_SECONDS));
		}
		return emtra$pitch;
	}

	@Inject(method = "setData", at = @At("TAIL"), require = 0, remap = false)
	private void emtra$recordPosition(CallbackInfo ci) {
		// Glide towards the apparent position, or back to the vehicle, instead of jumping.
		final boolean hasTarget = !Double.isNaN(emtra$targetX);
		final double goalX = hasTarget ? emtra$targetX : emtra$baseX();
		final double goalY = hasTarget ? emtra$targetY : emtra$baseY();
		final double goalZ = hasTarget ? emtra$targetZ : emtra$baseZ();
		if (!emtra$apparent) {
			if (hasTarget) {
				emtra$apparent = true;
				emtra$apparentX = emtra$baseX();
				emtra$apparentY = emtra$baseY();
				emtra$apparentZ = emtra$baseZ();
			}
		}
		if (emtra$apparent) {
			final double step = 1 - Math.exp(-emtra$seconds / EMTRA$MOVE_SECONDS);
			emtra$apparentX += (goalX - emtra$apparentX) * step;
			emtra$apparentY += (goalY - emtra$apparentY) * step;
			emtra$apparentZ += (goalZ - emtra$apparentZ) * step;
			if (!hasTarget) {
				final double dx = goalX - emtra$apparentX, dy = goalY - emtra$apparentY, dz = goalZ - emtra$apparentZ;
				if (dx * dx + dy * dy + dz * dz < 0.04) {
					emtra$apparent = false;
				}
			}
		}
		MtrSounds.recordPosition(this);
	}
}
