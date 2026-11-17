package com.egehew.emtra.render.mixin;

import com.egehew.emtra.render.EmtraClientConfig;
import com.egehew.emtra.sound.MtrSounds;
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
 * <li>Tracked and marked for Sound Physics Perfected's blacklist (see {@link MtrSounds}); their recent positions are
 * recorded so their OpenAL sources can be found while the train moves.</li>
 * <li>Volume above 1 only multiplies the audible range in Minecraft (BVE motors x5 = 640 blocks): capped at 1.</li>
 * <li>Volume and pitch are smoothed. MTR's motor output follows the sign of the client's speed change, which flickers
 * between power, coast and brake from frame to frame (packs with BreakerDelay = 0 have no delay at all); a single
 * frame at volume 0 stopped the loop and the next frame restarted it from the beginning: a click each time. Smoothed,
 * a one-frame drop does not stop the loop, loops start from silence and fade to silence before they stop.</li>
 * </ul>
 */
@Mixin(targets = "org.mtr.mod.sound.VehicleLoopingSoundInstance", remap = false)
public abstract class MtrVehicleSoundMixin {

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
			return target;
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
		MtrSounds.recordPosition(this);
	}

	@Override
	public String toString() {
		return MtrSounds.SPP_MARKER + "@" + Integer.toHexString(System.identityHashCode(this));
	}
}
