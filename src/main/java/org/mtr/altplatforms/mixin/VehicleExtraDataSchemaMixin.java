package org.mtr.altplatforms.mixin;

import org.mtr.altplatforms.duck.IVehicleExtraData;
import org.mtr.core.generated.data.VehicleExtraDataSchema;
import org.mtr.core.serializer.WriterBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = VehicleExtraDataSchema.class, remap = false)
public abstract class VehicleExtraDataSchemaMixin {

	@Inject(method = "serializeData", at = @At("TAIL"))
	private void altplatforms$onSerializeData(WriterBase writerBase, CallbackInfo ci) {
		if ((Object) this instanceof IVehicleExtraData) {
			final IVehicleExtraData duck = (IVehicleExtraData) (Object) this;
			final long r1 = duck.altplatforms$getDynamicRepeatIndex1();
			final long r2 = duck.altplatforms$getDynamicRepeatIndex2();
			final double td = duck.altplatforms$getDynamicTotalDistance();
			if (r1 >= 0) {
				writerBase.writeLong("repeatIndex1", r1);
			}
			if (r2 >= 0) {
				writerBase.writeLong("repeatIndex2", r2);
			}
			if (td >= 0) {
				writerBase.writeDouble("totalDistance", td);
			}
		}
	}
}
