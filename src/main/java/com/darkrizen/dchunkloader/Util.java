package com.darkrizen.dchunkloader;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class Util {
  public static ServerPlayer getServerPlayer(ServerLevel world, String uuid) {
    return getServerPlayer(world, UUID.fromString(uuid));
  }

  public static ServerPlayer getServerPlayer(ServerLevel world, UUID uuid) {
    return world.getServer()
    .getPlayerList()
    .getPlayer(uuid);
  }

  public static boolean isPlayerOnline(ServerLevel world, String uuid) {
    return getServerPlayer(world, uuid) != null;
  }
}
