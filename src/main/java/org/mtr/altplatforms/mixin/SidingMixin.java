package org.mtr.altplatforms.mixin;

import org.mtr.altplatforms.data.AlternatePath;
import org.mtr.altplatforms.data.ArrivalRouting;
import org.mtr.altplatforms.duck.IDepotAlternatePaths;
import org.mtr.altplatforms.duck.ISidingVehicles;
import org.mtr.core.data.*;
import org.mtr.core.generated.data.SidingSchema;
import org.mtr.core.operation.ArrivalResponse;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.Long2ObjectAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Siding.class, remap = false)
public abstract class SidingMixin extends SidingSchema implements ISidingVehicles {

	protected SidingMixin(double railLength, Position position1, Position position2, TransportMode transportMode, Data data) {
		super(railLength, position1, position2, transportMode, data);
	}

	protected SidingMixin(ReaderBase readerBase, Data data) {
		super(readerBase, data);
	}

	@Shadow
	@Final
	private ObjectArraySet<Vehicle> vehicles;

	@Override
	public ObjectArraySet<Vehicle> altplatforms$getVehicles() {
		return vehicles;
	}

	@Shadow
	@Final
	private Long2ObjectAVLTreeMap<ObjectArraySet<Trip.StopTime>> platformTripStopTimes;

	@Inject(method = "getArrivals", at = @At("HEAD"))
	private void altplatforms$onGetArrivalsHead(long currentMillis, Platform platform, long count, ObjectArrayList<ArrivalResponse> arrivalResponseList, CallbackInfo ci) {
		if (!((Object) area instanceof IDepotAlternatePaths)) {
			return;
		}
		// StopTime retains its scheduled platform even when indexed under an alternate.
		// Filter by that identity so reverse A<->B paths never propagate unrelated trips.
		final ObjectArraySet<Trip.StopTime> additionalTimes = new ObjectArraySet<>();
		for (final AlternatePath altPath : ((IDepotAlternatePaths) (Object) area).altplatforms$getAlternatePaths()) {
			if (altPath.alternatePlatformId != platform.getId()) {
				continue;
			}
			final ObjectArraySet<Trip.StopTime> primaryTimes = platformTripStopTimes.get(altPath.primaryPlatformId);
			if (primaryTimes != null) {
				for (final Trip.StopTime stopTime : primaryTimes) {
					if (stopTime.platformId == altPath.primaryPlatformId && ArrivalRouting.getStopIndex((Depot) area, stopTime) == altPath.stopIndex) {
						additionalTimes.add(stopTime);
					}
				}
			}
		}
		if (!additionalTimes.isEmpty()) {
			platformTripStopTimes.computeIfAbsent(platform.getId(), key -> new ObjectArraySet<>()).addAll(additionalTimes);
		}
	}

	// Reject candidates BEFORE MTR increments its accepted-arrival counter and applies
	// count. The shared response accumulator is never filtered, sorted or truncated.
	@Inject(method = "lambda$getArrivals$13", at = @At("HEAD"), cancellable = true)
	private void altplatforms$filterArrival(long[] counter, long count, Platform platform, ObjectArrayList<ArrivalResponse> candidates,
			Trip trip, int tripStopIndex, Trip.StopTime stopTime, long arrival, long departure, boolean realtime,
			long deviation, int departureIndex, long departureOffset, CallbackInfo ci) {
		if (!((Object) area instanceof IDepotAlternatePaths)) {
			return;
		}

		final long targetPlatformId = ArrivalRouting.getTargetPlatform(data, (Depot) area, vehicles, stopTime, realtime, departureIndex);
		if (targetPlatformId != platform.getId()) {
			ci.cancel();
		}
	}
}
