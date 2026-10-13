package org.mtr.altplatforms.duck;

import org.mtr.core.data.PathData;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;

public interface IVehicleExtraData {
	void altplatforms$setPath(ObjectArrayList<PathData> newPath, long newRepeatIndex1, long newRepeatIndex2, double newTotalDistance);
	long altplatforms$getRepeatIndex1();
	long altplatforms$getRepeatIndex2();
	double altplatforms$getTotalDistance();
	long altplatforms$getDynamicRepeatIndex1();
	long altplatforms$getDynamicRepeatIndex2();
	double altplatforms$getDynamicTotalDistance();
	void altplatforms$setDynamicValues(long repeatIndex1, long repeatIndex2, double totalDistance);
}
