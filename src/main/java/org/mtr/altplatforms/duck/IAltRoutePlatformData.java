package org.mtr.altplatforms.duck;

import org.mtr.core.data.Platform;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;

public interface IAltRoutePlatformData {
	LongArrayList altplatforms$getAlternatePlatformIds();
	ObjectArrayList<Platform> altplatforms$getAlternatePlatforms();
}
