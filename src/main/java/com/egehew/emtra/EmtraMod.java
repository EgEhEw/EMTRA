package com.egehew.emtra;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class EmtraMod {
	public static final String MOD_ID = "emtra";
	public static final Logger LOGGER = LogManager.getLogger("EMTRA");

	public static void init() {
		LOGGER.info("EMTRA loaded");
	}
}
