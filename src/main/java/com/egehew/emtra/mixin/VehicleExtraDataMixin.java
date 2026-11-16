package com.egehew.emtra.mixin;

import com.egehew.emtra.duck.IVehicleExtraData;
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
	private double emtra$dynamicTotalDistance = -1;
	@Unique
	private long emtra$dynamicRepeatIndex1 = -1;
	@Unique
	private long emtra$dynamicRepeatIndex2 = -1;
	@Unique
	private boolean emtra$pathChanged = false;

	@Override
	public void emtra$setPath(ObjectArrayList<PathData> newPath, long newRepeatIndex1, long newRepeatIndex2, double newTotalDistance) {
		this.path.clear();
		this.path.addAll(newPath);
		this.immutablePath = new ObjectImmutableList<>(this.path);
		this.emtra$dynamicRepeatIndex1 = newRepeatIndex1;
		this.emtra$dynamicRepeatIndex2 = newRepeatIndex2;
		this.emtra$dynamicTotalDistance = newTotalDistance;
		this.emtra$pathChanged = true;
	}

	@Override
	public long emtra$getRepeatIndex1() {
		return emtra$dynamicRepeatIndex1 >= 0 ? emtra$dynamicRepeatIndex1 : repeatIndex1;
	}

	@Override
	public long emtra$getRepeatIndex2() {
		return emtra$dynamicRepeatIndex2 >= 0 ? emtra$dynamicRepeatIndex2 : repeatIndex2;
	}

	@Override
	public double emtra$getTotalDistance() {
		return emtra$dynamicTotalDistance >= 0 ? emtra$dynamicTotalDistance : totalDistance;
	}

	@Override
	public void emtra$setDynamicValues(long repeatIndex1, long repeatIndex2, double totalDistance) {
		this.emtra$dynamicRepeatIndex1 = repeatIndex1;
		this.emtra$dynamicRepeatIndex2 = repeatIndex2;
		this.emtra$dynamicTotalDistance = totalDistance;
	}

	@Override
	public long emtra$getDynamicRepeatIndex1() {
		return emtra$dynamicRepeatIndex1;
	}

	@Override
	public long emtra$getDynamicRepeatIndex2() {
		return emtra$dynamicRepeatIndex2;
	}

	@Override
	public double emtra$getDynamicTotalDistance() {
		return emtra$dynamicTotalDistance;
	}

	@Inject(method = "copy", at = @At("RETURN"))
	private void emtra$onCopy(int pathUpdateIndex, CallbackInfoReturnable<VehicleExtraData> cir) {
		final VehicleExtraData copy = cir.getReturnValue();
		if (copy != null && (Object) copy instanceof IVehicleExtraData) {
			((IVehicleExtraData) (Object) copy).emtra$setDynamicValues(
				emtra$dynamicRepeatIndex1,
				emtra$dynamicRepeatIndex2,
				emtra$dynamicTotalDistance
			);
		}
	}

	@Inject(method = "getRepeatIndex1", at = @At("HEAD"), cancellable = true)
	private void emtra$onGetRepeatIndex1(CallbackInfoReturnable<Integer> cir) {
		if (emtra$dynamicRepeatIndex1 >= 0) {
			cir.setReturnValue((int) emtra$dynamicRepeatIndex1);
		}
	}

	@Inject(method = "getRepeatIndex2", at = @At("HEAD"), cancellable = true)
	private void emtra$onGetRepeatIndex2(CallbackInfoReturnable<Integer> cir) {
		if (emtra$dynamicRepeatIndex2 >= 0) {
			cir.setReturnValue((int) emtra$dynamicRepeatIndex2);
		}
	}

	@Inject(method = "getTotalDistance", at = @At("HEAD"), cancellable = true)
	private void emtra$onGetTotalDistance(CallbackInfoReturnable<Double> cir) {
		if (emtra$dynamicTotalDistance >= 0) {
			cir.setReturnValue(emtra$dynamicTotalDistance);
		}
	}

	@Inject(method = "checkForUpdate", at = @At("RETURN"), cancellable = true)
	private void emtra$onCheckForUpdate(CallbackInfoReturnable<Boolean> cir) {
		if (emtra$pathChanged) {
			emtra$pathChanged = false;
			cir.setReturnValue(true);
		}
	}
}
