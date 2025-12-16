package com.darkrizen.dchunkloader;

import net.minecraftforge.common.ForgeConfigSpec;

public class DChunkLoaderConfig {
  public static final ForgeConfigSpec SPEC;

  public static final ForgeConfigSpec.ConfigValue<String> ACTIVATION_BLOCK;
  public static final ForgeConfigSpec.ConfigValue<String> ACTIVATION_ITEM;
  public static final ForgeConfigSpec.IntValue MAX_LOADERS_PER_PLAYER;
  public static final ForgeConfigSpec.IntValue MAX_CHUNKLOADER_AGE_DAYS;

  static {
    ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

    builder.push("chunkloader");

    ACTIVATION_BLOCK = builder
    .comment("Block used as chunkloader")
    .define("activationBlock", "minecraft:iron_block");

    ACTIVATION_ITEM = builder
    .comment("Item used to activate the chunkloader")
    .define("activationItem", "minecraft:stick");

    MAX_LOADERS_PER_PLAYER = builder
    .comment("Max amounts of chunkloader per player")
    .defineInRange("maxLoadersPerPlayer", 4, 2, 100);

    MAX_CHUNKLOADER_AGE_DAYS = builder
    .comment("Max days after removing a player chunk loaders")
    .defineInRange("maxLoadersPerPlayer", 4, 2, 100);

    builder.pop();

    SPEC = builder.build();
  }
}
