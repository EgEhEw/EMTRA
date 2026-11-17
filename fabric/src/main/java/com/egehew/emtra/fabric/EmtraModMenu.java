package com.egehew.emtra.fabric;

import com.egehew.emtra.screen.EmtraConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Mod Menu entry point: opens the EMTRA config screen. Only loaded when Mod Menu is installed. */
public class EmtraModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> new EmtraConfigScreen(new org.mtr.mapping.holder.Screen(parent));
	}
}
