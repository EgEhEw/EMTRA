package org.mtr.altplatforms.data;

import org.mtr.core.data.Platform;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class PlatformStop {
	public final Platform platform;
	public final ObjectArrayList<Platform> alternatePlatforms;

	public PlatformStop(Platform platform, ObjectArrayList<Platform> alternatePlatforms) {
		this.platform = platform;
		this.alternatePlatforms = alternatePlatforms;
	}
}
