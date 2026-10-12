package org.mtr.altplatforms.duck;

import org.mtr.altplatforms.data.AlternatePath;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;

public interface IDepotAlternatePaths {
	ObjectArrayList<AlternatePath> altplatforms$getAlternatePaths();
	ObjectArrayList<AlternatePath> altplatforms$getAlternatePathsForPlatform(long platformId);
}
