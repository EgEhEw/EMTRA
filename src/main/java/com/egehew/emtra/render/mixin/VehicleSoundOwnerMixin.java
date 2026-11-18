package com.egehew.emtra.render.mixin;

import com.egehew.emtra.sound.MtrSounds;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mod.data.PersistentVehicleData;
import org.mtr.mod.data.VehicleExtension;
import org.mtr.mod.resource.VehicleResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Carries the vehicle ID through sound updates, even when another train passes within the cabin bounds. */
@Mixin(value = VehicleExtension.class, remap = false)
public abstract class VehicleSoundOwnerMixin {

	@Redirect(method = "playMotorSound", at = @At(value = "INVOKE", target = "Lorg/mtr/mod/data/PersistentVehicleData;playMotorSound(Lorg/mtr/mod/resource/VehicleResource;ILorg/mtr/mapping/holder/BlockPos;FFFZ)V"), remap = false)
	private void emtra$motorSound(PersistentVehicleData data, VehicleResource resource, int car, BlockPos pos,
								  float speed, float speedChange, float acceleration, boolean onRoute) {
		MtrSounds.withVehicle(((VehicleExtension) (Object) this).getId(),
				() -> data.playMotorSound(resource, car, pos, speed, speedChange, acceleration, onRoute));
	}

	@Redirect(method = "playDoorSound", at = @At(value = "INVOKE", target = "Lorg/mtr/mod/data/PersistentVehicleData;playDoorSound(Lorg/mtr/mod/resource/VehicleResource;ILorg/mtr/mapping/holder/BlockPos;)V"), remap = false)
	private void emtra$doorSound(PersistentVehicleData data, VehicleResource resource, int car, BlockPos pos) {
		MtrSounds.withVehicle(((VehicleExtension) (Object) this).getId(), () -> data.playDoorSound(resource, car, pos));
	}
}
