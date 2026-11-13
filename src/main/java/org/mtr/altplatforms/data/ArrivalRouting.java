package org.mtr.altplatforms.data;

import org.mtr.altplatforms.duck.IVehicle;
import org.mtr.core.data.*;

/** Resolves each arrival from its scheduled stop, independently of path direction. */
public final class ArrivalRouting {

	private ArrivalRouting() {
	}

	public static long getTargetPlatform(Data data, Depot depot, Iterable<Vehicle> vehicles, Trip.StopTime stopTime, boolean realtime, int departureIndex) {
		if (realtime) {
			final int stopIndex = getStopIndex(depot, stopTime);
			for (final Vehicle vehicle : vehicles) {
				if (!vehicle.getIsOnRoute() || vehicle.getDepartureIndex() != departureIndex || vehicle.vehicleExtraData == null) {
					continue;
				}
				final double progress = ((IVehicle) vehicle).altplatforms$getRailProgress();
				for (final PathData pd : vehicle.vehicleExtraData.immutablePath) {
					if (pd.getStopIndex() == stopIndex && data.platformIdMap.containsKey(pd.getSavedRailBaseId()) && progress <= pd.getEndDistance() + 1) {
						return pd.getSavedRailBaseId();
					}
				}
				break;
			}
		}
		return stopTime.platformId;
	}

	public static int getStopIndex(Depot depot, Trip.StopTime stopTime) {
		int stopIndex = -1;
		long previousPlatformId = 0;
		for (int routeIndex = 0; routeIndex < depot.routes.size(); routeIndex++) {
			final Route route = depot.routes.get(routeIndex);
			if (route == null) {
				continue;
			}
			for (int i = 0; i < route.getRoutePlatforms().size(); i++) {
				final Platform platform = route.getRoutePlatforms().get(i).platform;
				if (platform == null) {
					continue;
				}
				if (platform.getId() != previousPlatformId) {
					stopIndex++;
					previousPlatformId = platform.getId();
				}
				if (routeIndex == stopTime.trip.routeIndex && i == stopTime.tripStopIndex) {
					return stopIndex;
				}
			}
		}
		return -1;
	}
}
