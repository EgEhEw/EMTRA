package com.egehew.emtra.fabric;

import net.fabricmc.api.ModInitializer;
import com.egehew.emtra.EmtraMod;

public class EmtraFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		EmtraMod.init();
	}
}
