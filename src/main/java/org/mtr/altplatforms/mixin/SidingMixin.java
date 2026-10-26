package org.mtr.altplatforms.mixin;

import org.mtr.altplatforms.data.AlternatePath;
import org.mtr.altplatforms.duck.IDepotAlternatePaths;
import org.mtr.altplatforms.duck.ISidingVehicles;
import org.mtr.altplatforms.duck.IVehicle;
import org.mtr.core.data.*;
import org.mtr.core.generated.data.SidingSchema;
import org.mtr.core.operation.ArrivalResponse;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.Long2ObjectAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;

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

	@Inject(method = "generatePathDistancesAndTimeSegments", at = @At("TAIL"))
	private void altplatforms$onGeneratePathDistancesAndTimeSegments(CallbackInfo ci) {
		if ((Object) area instanceof IDepotAlternatePaths) {
			for (final AlternatePath altPath : ((IDepotAlternatePaths) (Object) area).altplatforms$getAlternatePaths()) {
				final ObjectArraySet<Trip.StopTime> primaryTimes = platformTripStopTimes.get(altPath.primaryPlatformId);
				if (primaryTimes != null) {
					ObjectArraySet<Trip.StopTime> altTimes = platformTripStopTimes.get(altPath.alternatePlatformId);
					if (altTimes == null) {
						altTimes = new ObjectArraySet<>();
						platformTripStopTimes.put(altPath.alternatePlatformId, altTimes);
					}
					altTimes.addAll(primaryTimes);
				}
			}
		}
	}

	@Inject(method = "getArrivals", at = @At("HEAD"))
	private void altplatforms$onGetArrivalsHead(long currentMillis, Platform platform, long count, ObjectArrayList<ArrivalResponse> arrivalResponseList, CallbackInfo ci) {
		final long platformId = platform.getId();
		if ((Object) area instanceof IDepotAlternatePaths) {
			for (final AlternatePath altPath : ((IDepotAlternatePaths) (Object) area).altplatforms$getAlternatePaths()) {
				if (altPath.alternatePlatformId == platformId) {
					final ObjectArraySet<Trip.StopTime> primaryTimes = platformTripStopTimes.get(altPath.primaryPlatformId);
					if (primaryTimes != null) {
						ObjectArraySet<Trip.StopTime> altTimes = platformTripStopTimes.get(platformId);
						if (altTimes == null) {
							altTimes = new ObjectArraySet<>();
							platformTripStopTimes.put(platformId, altTimes);
						}
						altTimes.addAll(primaryTimes);
					}
				}
			}
		}
	}

	@Inject(method = "getArrivals", at = @At("TAIL"))
	private void altplatforms$onGetArrivalsTail(long currentMillis, Platform platform, long count, ObjectArrayList<ArrivalResponse> arrivalResponseList, CallbackInfo ci) {
		if (!((Object) area instanceof IDepotAlternatePaths)) {
			return;
		}

		final long queriedPlatformId = platform.getId();
		final IDepotAlternatePaths depot = (IDepotAlternatePaths) (Object) area;
		final ObjectArrayList<AlternatePath> allAltPaths = depot.altplatforms$getAlternatePaths();
		if (allAltPaths.isEmpty()) {
			return;
		}

		// 1. Determine if queriedPlatformId is a primary platform, an alternate platform, or neither.
		final LongOpenHashSet associatedAlternatePlatformIds = new LongOpenHashSet();
		final LongOpenHashSet associatedPrimaryPlatformIds = new LongOpenHashSet();

		for (final AlternatePath altPath : allAltPaths) {
			if (altPath.primaryPlatformId == queriedPlatformId) {
				associatedAlternatePlatformIds.add(altPath.alternatePlatformId);
			}
			if (altPath.alternatePlatformId == queriedPlatformId) {
				associatedPrimaryPlatformIds.add(altPath.primaryPlatformId);
			}
		}

		final boolean isPrimary = !associatedAlternatePlatformIds.isEmpty();
		final boolean isAlternate = !associatedPrimaryPlatformIds.isEmpty();

		if (!isPrimary && !isAlternate) {
			return;
		}

		// 2. Identify the active target platform for each active vehicle on route.
		// Key: departureIndex -> target platform ID for this station
		final Long2LongOpenHashMap vehicleTargetPlatformForThisStation = new Long2LongOpenHashMap();

		for (final Vehicle vehicle : vehicles) {
			if (!vehicle.getIsOnRoute() || vehicle.vehicleExtraData == null || vehicle.vehicleExtraData.immutablePath.isEmpty()) {
				continue;
			}

			final double railProgress = ((IVehicle) vehicle).altplatforms$getRailProgress();
			final ObjectImmutableList<PathData> path = vehicle.vehicleExtraData.immutablePath;

			// Search forward from current rail progress to find the upcoming platform for this station
			for (int i = 0; i < path.size(); i++) {
				final PathData pd = path.get(i);
				if (pd.getSavedRailBaseId() != 0 && pd.getDwellTime() > 0 && railProgress <= pd.getEndDistance() + 1.0) {
					final long pid = pd.getSavedRailBaseId();
					if (pid == queriedPlatformId || associatedAlternatePlatformIds.contains(pid) || associatedPrimaryPlatformIds.contains(pid)) {
						vehicleTargetPlatformForThisStation.put(vehicle.getDepartureIndex(), pid);
						break;
					}
				}
			}
		}

		// 3. Filter arrivalResponseList according to platform role:
		if (isAlternate) {
			// On an ALTERNATE platform:
			// - Timetable departures (!resp.getRealtime()) DO NOT belong here (timetable trains are scheduled to the primary platform).
			// - Active trains ONLY belong here if they have actually diverted to this alternate platform.
			arrivalResponseList.removeIf(resp -> {
				if (!resp.getRealtime()) {
					return true;
				}
				final long targetPlatform = vehicleTargetPlatformForThisStation.getOrDefault(resp.getDepartureIndex(), 0L);
				return targetPlatform != queriedPlatformId;
			});
		} else if (isPrimary) {
			// On a PRIMARY platform:
			// - Timetable departures (!resp.getRealtime()) stay (they are scheduled here).
			// - Active trains stay UNLESS they have diverted away to an associated alternate platform.
			arrivalResponseList.removeIf(resp -> {
				if (!resp.getRealtime()) {
					return false;
				}
				final long targetPlatform = vehicleTargetPlatformForThisStation.getOrDefault(resp.getDepartureIndex(), 0L);
				return targetPlatform != 0 && targetPlatform != queriedPlatformId && associatedAlternatePlatformIds.contains(targetPlatform);
			});
		}

		// 4. Ensure list stays sorted and limited to count
		Collections.sort(arrivalResponseList);
		while (arrivalResponseList.size() > count) {
			arrivalResponseList.remove(arrivalResponseList.size() - 1);
		}
	}
}
