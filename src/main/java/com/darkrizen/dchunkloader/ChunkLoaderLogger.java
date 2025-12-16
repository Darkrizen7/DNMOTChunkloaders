package com.darkrizen.dchunkloader;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ChunkLoaderLogger {

  private static final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

  public static void start(MinecraftServer server) {
    executor.scheduleAtFixedRate(() -> {
      for (ServerLevel world : server.getAllLevels()) {
        Set<Long> forcedChunks = world.getForcedChunks();
        DChunkLoader.LOGGER.debug("Forced chunks in dimension {}:", world.dimension().location());

        for (long chunkLong : forcedChunks) {
          int x = ChunkPos.getX(chunkLong);
          int z = ChunkPos.getZ(chunkLong);
          DChunkLoader.LOGGER.debug("       - Chunk at {}, {}", x, z);
        }
      }
    }, 0, 5, TimeUnit.SECONDS);
  }

  public static void stop() {
    executor.shutdownNow();
  }
}
