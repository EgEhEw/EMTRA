package org.mtr.altplatforms.mixin;

import org.mtr.altplatforms.data.AlternatePath;
import org.mtr.altplatforms.duck.IDepotAlternatePaths;
import org.mtr.altplatforms.duck.IVehicle;
import org.mtr.core.data.*;
import org.mtr.core.generated.data.SidingSchema;
import org.mtr.core.operation.ArrivalResponse;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.Long2ObjectAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;

@Mixin(value = Siding.class, remap = false)
public abstract class SidingMixin extends SidingSchema {

	protected SidingMixin(double railLength, Position position1, Position position2, TransportMode transportMode, Data data) {
		super(railLength, position1, position2, transportMode, data);
	}

	protected SidingMixin(ReaderBase readerBase, Data data) {
		super(readerBase, data);
	}

	@Shadow
	@Final
	private ObjectArraySet<Vehicle> vehicles;

	@Shadow
	@Final
	private Long2ObjectAVLTreeMap<ObjectArraySet<Trip.StopTime>> platformTripStopTimes;

	@Shadow
	public abstract ObjectArrayList<VehicleCar> getVehicleCars();

	@Inject(method = "generatePathDistancesAndTimeSegments", at = @At("TAIL"))
	private void altplatforms$onGeneratePathDistancesAndTimeSegments(CallbackInfo ci) {
		if ((Object) area instanceof IDepotAlternatePaths) {
			for (final AlternatePath altPath : ((IDepotAlternatePaths) (Object) area).altplatforms$getAlternatePaths()) {
				final ObjectArraySet<Trip.StopTime> primaryTimes = platformTripStopTimes.get(altPath.primaryPlatformId);
				if (primaryTimes != null && !platformTripStopTimes.containsKey(altPath.alternatePlatformId)) {
					platformTripStopTimes.put(altPath.alternatePlatformId, new ObjectArraySet<>(primaryTimes));
				}
			}
		}
	}

	@Inject(method = "getArrivals", at = @At("HEAD"))
	private void altplatforms$onGetArrivalsHead(long currentMillis, Platform platform, long count, ObjectArrayList<ArrivalResponse> arrivalResponseList, CallbackInfo ci) {
		final long platformId = platform.getId();
		if (!platformTripStopTimes.containsKey(platformId) && (Object) area instanceof IDepotAlternatePaths) {
			for (final AlternatePath altPath : ((IDepotAlternatePaths) (Object) area).altplatforms$getAlternatePaths()) {
				if (altPath.alternatePlatformId == platformId) {
					final ObjectArraySet<Trip.StopTime> primaryTimes = platformTripStopTimes.get(altPath.primaryPlatformId);
					if (primaryTimes != null) {
						platformTripStopTimes.put(platformId, new ObjectArraySet<>(primaryTimes));
						break;
					}
				}
			}
		}
	}

	@Inject(method = "getArrivals", at = @At("TAIL"))
	private void altplatforms$onGetArrivalsTail(long currentMillis, Platform platform, long count, ObjectArrayList<ArrivalResponse> arrivalResponseList, CallbackInfo ci) {
		final long queriedPlatformId = platform.getId();
		final LongOpenHashSet activeDepartureIndicesForThisPlatform = new LongOpenHashSet();
		final LongOpenHashSet activeDepartureIndicesDivertedAway = new LongOpenHashSet();
		final ObjectArrayList<ArrivalResponse> realtimeArrivals = new ObjectArrayList<>();

		for (final Vehicle vehicle : vehicles) {
			if (!vehicle.getIsOnRoute()) {
				continue;
			}
			final VehicleExtraData extraData = vehicle.vehicleExtraData;
			if (extraData == null || extraData.immutablePath.isEmpty()) {
				continue;
			}

			final double railProgress = ((IVehicle) vehicle).altplatforms$getRailProgress();
			final double speed = ((IVehicle) vehicle).altplatforms$getSpeed();
			final long elapsedDwellTime = ((IVehicle) vehicle).altplatforms$getElapsedDwellTime();

			int platformIndex = -1;
			PathData platformPathData = null;
			for (int i = 0; i < extraData.immutablePath.size(); i++) {
				final PathData pd = extraData.immutablePath.get(i);
				if (pd.getSavedRailBaseId() == queriedPlatformId && pd.getDwellTime() > 0) {
					if (railProgress <= pd.getEndDistance() + 1.0) {
						platformIndex = i;
						platformPathData = pd;
						break;
					}
				}
			}

			if (platformIndex >= 0 && platformPathData != null) {
				final boolean atPlatform = railProgress >= platformPathData.getStartDistance() - 1.0
						&& railProgress <= platformPathData.getEndDistance() + 1.0;
				final long arrivalTime;
				final long departureTime;

				if (atPlatform && speed < 0.0001) {
					arrivalTime = currentMillis;
					final long remainingDwell = Math.max(0, platformPathData.getDwellTime() - elapsedDwellTime);
					departureTime = currentMillis + remainingDwell;
				} else {
					final double distanceToPlatform = Math.max(0, platformPathData.getStartDistance() - railProgress);
					if (distanceToPlatform < 0.5) {
						// Train is essentially at the platform, just use dwell time
						arrivalTime = currentMillis;
						final long remainingDwell = Math.max(0, platformPathData.getDwellTime() - elapsedDwellTime);
						departureTime = currentMillis + remainingDwell;
					} else {
						// Use a smooth ETA calculation based on current speed and distance
						// Accounts for acceleration and braking phases
						final double currentSpeedMpMs = speed; // metres per millisecond
						final double maxSpeedMpMs = Utilities.kilometersPerHourToMetersPerMillisecond(80);
						final double cruiseSpeedMpMs = Utilities.kilometersPerHourToMetersPerMillisecond(60);

						final long travelMillis;
						if (currentSpeedMpMs > Utilities.kilometersPerHourToMetersPerMillisecond(3)) {
							// Train is moving — use current speed as baseline
							// Estimate: average of current speed and a typical cruise speed
							// This smooths out the countdown as speed changes
							final double avgSpeed = (currentSpeedMpMs + cruiseSpeedMpMs) / 2.0;
							final double rawTime = distanceToPlatform / avgSpeed;
							// Add braking compensation: longer distances need less % overhead
							final double brakingFactor = distanceToPlatform > 200 ? 1.15 : 1.25;
							travelMillis = (long) (rawTime * brakingFactor);
						} else {
							// Train is stopped or barely moving — use conservative estimate
							final double estSpeed = cruiseSpeedMpMs * 0.6;
							travelMillis = (long) (distanceToPlatform / estSpeed);
						}

						arrivalTime = currentMillis + travelMillis;
						departureTime = arrivalTime + platformPathData.getDwellTime();
					}
				}

				Route route = null;
				if (area != null) {
					for (final Route r : area.routes) {
						if (r.getId() == extraData.getThisRouteId()) {
							route = r;
							break;
						}
					}
					if (route == null && !area.routes.isEmpty()) {
						route = area.routes.get(0);
					}
				}
				if (route == null && data != null && !data.routes.isEmpty()) {
					route = data.routeIdMap.get(extraData.getThisRouteId());
				}

				if (route != null) {
					final String destination = !extraData.getThisRouteDestination().isEmpty() ? extraData.getThisRouteDestination() : route.getDestination(extraData.getStopIndex());
					final ArrivalResponse resp = new ArrivalResponse(
							destination,
							arrivalTime,
							departureTime,
							0,
							true,
							vehicle.getDepartureIndex(),
							extraData.getStopIndex(),
							route,
							platform
					);
					resp.setCarDetails(getVehicleCars());
					realtimeArrivals.add(resp);
					activeDepartureIndicesForThisPlatform.add(vehicle.getDepartureIndex());
				}
			} else if ((Object) area instanceof IDepotAlternatePaths) {
				// Check if this vehicle has been diverted to an alternate platform
				// If querying primary platform -> mark diverted vehicle for removal
				// If querying alternate platform -> mark diverted vehicle for removal from primary schedule
				for (int i = 0; i < extraData.immutablePath.size(); i++) {
					final PathData pd = extraData.immutablePath.get(i);
					if (pd.getSavedRailBaseId() != 0 && pd.getDwellTime() > 0 && railProgress <= pd.getEndDistance() + 1.0) {
						final long vehicleNextPlatformId = pd.getSavedRailBaseId();
						for (final AlternatePath altPath : ((IDepotAlternatePaths) (Object) area).altplatforms$getAlternatePaths()) {
							// Vehicle heading to alternate, but we're querying primary -> remove from primary
							if (altPath.primaryPlatformId == queriedPlatformId && altPath.alternatePlatformId == vehicleNextPlatformId) {
								activeDepartureIndicesDivertedAway.add(vehicle.getDepartureIndex());
								break;
							}
							// Vehicle heading to primary, but we're querying alternate -> remove from alternate
							if (altPath.alternatePlatformId == queriedPlatformId && altPath.primaryPlatformId == vehicleNextPlatformId) {
								activeDepartureIndicesDivertedAway.add(vehicle.getDepartureIndex());
								break;
							}
						}
						break;
					}
				}
			}
		}

		arrivalResponseList.removeIf(resp -> activeDepartureIndicesDivertedAway.contains(resp.getDepartureIndex()) || activeDepartureIndicesForThisPlatform.contains(resp.getDepartureIndex()));
		arrivalResponseList.addAll(realtimeArrivals);
		Collections.sort(arrivalResponseList);

		while (arrivalResponseList.size() > count) {
			arrivalResponseList.remove(arrivalResponseList.size() - 1);
		}
	}
}
