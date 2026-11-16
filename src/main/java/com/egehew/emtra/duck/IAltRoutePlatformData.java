package com.egehew.emtra.duck;

import org.mtr.core.data.Platform;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;

public interface IAltRoutePlatformData {
	LongArrayList emtra$getAlternatePlatformIds();
	ObjectArrayList<Platform> emtra$getAlternatePlatforms();
	long emtra$getPlatformId();
}
