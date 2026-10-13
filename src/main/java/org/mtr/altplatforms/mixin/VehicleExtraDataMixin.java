package org.mtr.altplatforms.mixin;

import org.mtr.altplatforms.duck.IVehicleExtraData;
import org.mtr.core.data.PathData;
import org.mtr.core.data.VehicleExtraData;
import org.mtr.core.generated.data.VehicleExtraDataSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.serializer.WriterBase;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = VehicleExtraData.class, remap = false)
public abstract class VehicleExtraDataMixin extends VehicleExtraDataSchema implements IVehicleExtraData {

	protected VehicleExtraDataMixin(ReaderBase readerBase) {
		super(readerBase);
	}

	@Shadow @Mutable
	public ObjectImmutableList<PathData> immutablePath;

	@Unique
	private double altplatforms$dynamicTotalDistance = -1;
	@Unique
	private long altplatforms$dynamicRepeatIndex1 = -1;
	@Unique
	private long altplatforms$dynamicRepeatIndex2 = -1;
	@Unique
	private boolean altplatforms$pathChanged = false;

	@Override
	public void altplatforms$setPath(ObjectArrayList<PathData> newPath, long newRepeatIndex1, long newRepeatIndex2, double newTotalDistance) {
		this.path.clear();
		this.path.addAll(newPath);
		this.immutablePath = new ObjectImmutableList<>(this.path);
		this.altplatforms$dynamicRepeatIndex1 = newRepeatIndex1;
		this.altplatforms$dynamicRepeatIndex2 = newRepeatIndex2;
		this.altplatforms$dynamicTotalDistance = newTotalDistance;
		this.altplatforms$pathChanged = true;
	}

	@Override
	public long altplatforms$getRepeatIndex1() {
		return altplatforms$dynamicRepeatIndex1 >= 0 ? altplatforms$dynamicRepeatIndex1 : repeatIndex1;
	}

	@Override
	public long altplatforms$getRepeatIndex2() {
		return altplatforms$dynamicRepeatIndex2 >= 0 ? altplatforms$dynamicRepeatIndex2 : repeatIndex2;
	}

	@Override
	public double altplatforms$getTotalDistance() {
		return altplatforms$dynamicTotalDistance >= 0 ? altplatforms$dynamicTotalDistance : totalDistance;
	}

	@Override
	public void altplatforms$setDynamicValues(long repeatIndex1, long repeatIndex2, double totalDistance) {
		this.altplatforms$dynamicRepeatIndex1 = repeatIndex1;
		this.altplatforms$dynamicRepeatIndex2 = repeatIndex2;
		this.altplatforms$dynamicTotalDistance = totalDistance;
	}

	@Override
	public long altplatforms$getDynamicRepeatIndex1() {
		return altplatforms$dynamicRepeatIndex1;
	}

	@Override
	public long altplatforms$getDynamicRepeatIndex2() {
		return altplatforms$dynamicRepeatIndex2;
	}

	@Override
	public double altplatforms$getDynamicTotalDistance() {
		return altplatforms$dynamicTotalDistance;
	}

	@Inject(method = "copy", at = @At("RETURN"))
	private void altplatforms$onCopy(int pathUpdateIndex, CallbackInfoReturnable<VehicleExtraData> cir) {
		final VehicleExtraData copy = cir.getReturnValue();
		if (copy != null && (Object) copy instanceof IVehicleExtraData) {
			((IVehicleExtraData) (Object) copy).altplatforms$setDynamicValues(
				altplatforms$dynamicRepeatIndex1,
				altplatforms$dynamicRepeatIndex2,
				altplatforms$dynamicTotalDistance
			);
		}
	}

	@Inject(method = "getRepeatIndex1", at = @At("HEAD"), cancellable = true)
	private void altplatforms$onGetRepeatIndex1(CallbackInfoReturnable<Integer> cir) {
		if (altplatforms$dynamicRepeatIndex1 >= 0) {
			cir.setReturnValue((int) altplatforms$dynamicRepeatIndex1);
		}
	}

	@Inject(method = "getRepeatIndex2", at = @At("HEAD"), cancellable = true)
	private void altplatforms$onGetRepeatIndex2(CallbackInfoReturnable<Integer> cir) {
		if (altplatforms$dynamicRepeatIndex2 >= 0) {
			cir.setReturnValue((int) altplatforms$dynamicRepeatIndex2);
		}
	}

	@Inject(method = "getTotalDistance", at = @At("HEAD"), cancellable = true)
	private void altplatforms$onGetTotalDistance(CallbackInfoReturnable<Double> cir) {
		if (altplatforms$dynamicTotalDistance >= 0) {
			cir.setReturnValue(altplatforms$dynamicTotalDistance);
		}
	}

	@Inject(method = "checkForUpdate", at = @At("RETURN"), cancellable = true)
	private void altplatforms$onCheckForUpdate(CallbackInfoReturnable<Boolean> cir) {
		if (altplatforms$pathChanged) {
			altplatforms$pathChanged = false;
			cir.setReturnValue(true);
		}
	}
}
