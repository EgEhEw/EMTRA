package org.mtr.altplatforms.fabric;

import net.fabricmc.api.ModInitializer;
import org.mtr.altplatforms.AltPlatformsMod;

public class AltPlatformsFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		AltPlatformsMod.init();
	}
}
