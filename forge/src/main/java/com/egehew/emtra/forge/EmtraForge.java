package com.egehew.emtra.forge;

import com.egehew.emtra.EmtraMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(EmtraMod.MOD_ID)
public class EmtraForge {
	public EmtraForge() {
		EmtraMod.init();
		if (FMLEnvironment.dist == Dist.CLIENT) {
			EmtraForgeClient.init();
		}
	}
}
