package com.darkrizen.dchunkloader;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.*;


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

    start = System.nanoTime();
    ChunkLoaderManager.getPlayerChunkLoaders(UUID.randomUUID().toString());
    end = System.nanoTime();
    duration = (end - start);
    System.out.println("Search player chunkloaders: " + duration + " ns (" + (duration / 1_000_000.0) + " ms)");

    start = System.nanoTime();
    ChunkLoaderManager.getTeamChunkLoaders("Test");
    end = System.nanoTime();
    duration = (end - start);
    System.out.println("Search team chunkloaders: " + duration + " ns (" + (duration / 1_000_000.0) + " ms)");
  }

  public static void runTeamStressTest(ServerLevel world) {
    long startTime = System.nanoTime();

    TeamManager.getTeams().clear();
    int teamCount = 100;
    int playersPerTeam = 10;

    DChunkLoader.LOGGER.info("--- STARTING TEAM STRESS TEST ---");
    DChunkLoader.LOGGER.info("Target: {} teams with {} members each (Total: {} players)",
                             teamCount, playersPerTeam, (teamCount * playersPerTeam));

    for (int i = 0; i < teamCount; i++) {
      String teamName = "StressTeam_" + i;
      Set<TeamSavedData.TeamMember> members = new HashSet<>();

      for (int j = 0; j < playersPerTeam; j++) {
        String fakeUUID = UUID.randomUUID().toString();
        String fakeName = "Bot_" + i + "_" + j;
        members.add(new TeamSavedData.TeamMember(fakeUUID, fakeName));
      }
      TeamManager.getTeams().put(teamName, members);
    }

    long injectedTime = System.nanoTime();
    DChunkLoader.LOGGER.info("1. Injection: Done in {} ms", (injectedTime - startTime) / 1_000_000.0);

    String targetUUID = "definitely-not-here";

    DChunkLoader.LOGGER.info("2. Testing REAL getTeamForPlayer performance (Average over 100 runs)...");

    long searchStart = System.nanoTime();
    for (int k = 0; k < 100; k++) {
      fakeInternalCallForTest(targetUUID);
    }
    long searchEnd = System.nanoTime();

    double totalMs = (searchEnd - searchStart) / 1_000_000.0;
    double avgMs = totalMs / 100.0;

    DChunkLoader.LOGGER.info("   -> Total time for 100 searches: {} ms", String.format("%.3f", totalMs));
    DChunkLoader.LOGGER.info("   -> Average time per search: {} ms", String.format("%.4f", avgMs));

    long saveStart = System.nanoTime();
    TeamManager.save(world);
    long saveEnd = System.nanoTime();
    DChunkLoader.LOGGER.info("3. Real save() call: {} ms", (saveEnd - saveStart) / 1_000_000.0);

    DChunkLoader.LOGGER.info("--- END OF STRESS TEST ---");
  }

  private static String fakeInternalCallForTest(String uuidToFind) {
    for (Map.Entry<String, Set<TeamSavedData.TeamMember>> team : TeamManager.getTeams().entrySet()) {
      Set<TeamSavedData.TeamMember> members = team.getValue();
      for (TeamSavedData.TeamMember member : members) {
        if (uuidToFind.equals(member.getUuid())) {
          return team.getKey();
        }
      }
    }
    return null;
  }
}
