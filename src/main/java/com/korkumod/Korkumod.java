package com.korkumod;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Korkumod implements ModInitializer {
    public static final String MOD_ID = "korkumod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Korkumod basariyla yuklendi!");
    }
}
