package com.darkrizen.dchunkloader;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(DChunkLoader.MODID)
public class DChunkLoader {
  public static final String MODID = "dchunkloader";
  public static final Logger LOGGER = LogManager.getLogger();

  public DChunkLoader() {
    ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, DChunkLoaderConfig.SPEC);
  }
}
