package org.mtr.altplatforms.data;

import org.mtr.core.data.Data;
import org.mtr.core.data.PathData;

import java.util.List;

/** Copies a generated detour without changing the service's stopping pattern. */
public final class RoutingPathData {

	private RoutingPathData() {
	}

	public static int findPlatformIndex(List<PathData> path, int fromIndex, long platformId) {
		for (int i = fromIndex; i < path.size(); i++) {
			if (path.get(i).getSavedRailBaseId() == platformId) {
				return i;
			}
		}
		return -1;
	}

	public static int findPlatformEnd(List<PathData> path, int platformIndex) {
		int endIndex = platformIndex;
		while (endIndex + 1 < path.size() && path.get(endIndex + 1).getSavedRailBaseId() == path.get(platformIndex).getSavedRailBaseId()) {
			endIndex++;
		}
		return endIndex;
	}

	public static boolean hasPlatformAfter(List<PathData> path, int index, Data data) {
		for (int i = index + 1; i < path.size(); i++) {
			if (data.platformIdMap.containsKey(path.get(i).getSavedRailBaseId())) {
				return true;
			}
		}
		return false;
	}

	public static PathData copyForService(PathData original, double start, double end, List<PathData> servicePath, Data data) {
		if (data.platformIdMap.containsKey(original.getSavedRailBaseId())) {
			for (final PathData scheduled : servicePath) {
				if (scheduled.getStopIndex() == original.getStopIndex() && data.platformIdMap.containsKey(scheduled.getSavedRailBaseId())) {
					return new PathData(original.getRail(), original.getSavedRailBaseId(), scheduled.getDwellTime(), original.getStopIndex(), start, end,
							original.reversePositions ? original.getOrderedPosition2() : original.getOrderedPosition1(), original.getRail().getStartAngle(original.reversePositions),
							original.reversePositions ? original.getOrderedPosition1() : original.getOrderedPosition2(), original.getRail().getStartAngle(!original.reversePositions));
				}
			}
		}
		return new PathData(original, start, end);
	}
}
