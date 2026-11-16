package com.egehew.emtra.duck;

import com.egehew.emtra.data.AlternatePath;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;

public interface IDepotAlternatePaths {
	ObjectArrayList<AlternatePath> emtra$getAlternatePaths();
	ObjectArrayList<AlternatePath> emtra$getAlternatePathsForPlatform(long platformId);
	ObjectArrayList<AlternatePath> emtra$getAlternatePathsInvolvingPlatform(long platformId);
}
