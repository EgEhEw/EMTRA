package com.egehew.emtra.forge;

import com.egehew.emtra.screen.EmtraConfigScreen;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;

/** Client-only setup: the "Config" button in Forge's mod list. */
final class EmtraForgeClient {

	private EmtraForgeClient() {
	}

	static void init() {
		ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
				() -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new EmtraConfigScreen(new org.mtr.mapping.holder.Screen(parent))));
	}
}
