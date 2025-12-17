package com.darkrizen.dchunkloader;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;


public class StressTestInjector {
  public static void debugInjectFakeLoaders(ServerLevel world) {
    Map<String, Map<BlockPos, ChunkLoaderSavedData.ChunkLoader>> chunkLoaders = ChunkLoaderManager.getAllChunkLoaders();
    String dim = world.dimension().location().toString();
    Map<BlockPos, ChunkLoaderSavedData.ChunkLoader> worldLoaders = chunkLoaders.computeIfAbsent(dim, k -> new HashMap<>());

    for (int p = 0; p < 10; p++) {
      String fakeUUID = "fake-uuid-" + p;
      String fakeName = "Player" + p;

      for (int l = 0; l < 100; l++) {
        BlockPos fakePos = new BlockPos(p * 100, 64, l * 16);

        ChunkLoaderSavedData.ChunkLoader fakeLoader = new ChunkLoaderSavedData.ChunkLoader(
        dim,
        fakePos,
        fakeUUID,
        fakeName,
        System.currentTimeMillis()
        );

        worldLoaders.put(fakePos, fakeLoader);
      }
    }

    DChunkLoader.LOGGER.info("Successfully injected 1000 fake loaders for testing.");
    long start = System.nanoTime();
    ChunkLoaderManager.save(world);
    long end = System.nanoTime();
    long duration = (end - start);
    System.out.println("Save duration: " + duration + " ns (" + (duration / 1_000_000.0) + " ms)");
  }
}
