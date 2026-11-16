package com.egehew.emtra.duck;

import org.mtr.core.data.PathData;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;

public interface IVehicleExtraData {
	void emtra$setPath(ObjectArrayList<PathData> newPath, long newRepeatIndex1, long newRepeatIndex2, double newTotalDistance);
	long emtra$getRepeatIndex1();
	long emtra$getRepeatIndex2();
	double emtra$getTotalDistance();
	long emtra$getDynamicRepeatIndex1();
	long emtra$getDynamicRepeatIndex2();
	double emtra$getDynamicTotalDistance();
	void emtra$setDynamicValues(long repeatIndex1, long repeatIndex2, double totalDistance);
}
