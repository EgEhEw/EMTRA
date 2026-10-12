package org.mtr.altplatforms;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AltPlatformsMod {
	public static final String MOD_ID = "mtr_alt_platforms";
	public static final Logger LOGGER = LogManager.getLogger("MTR-AltPlatforms");

	public static void init() {
		LOGGER.info("MTR Alternate Platforms addon loaded successfully!");
	}
}
