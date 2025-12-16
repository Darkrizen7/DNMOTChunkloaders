package com.darkrizen.dchunkloader;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.*;

public class ChunkLoaderManager {

  private static final String DATA_NAME = "dchunkloader_data";

  // Dimension -> BlockPos -> Set<BlockPos>, warning not saved like that
  private static final Map<String, Map<BlockPos, ChunkLoaderSavedData.ChunkLoader>> chunkLoaders = new HashMap<>();

  public static Map<String, Map<BlockPos, ChunkLoaderSavedData.ChunkLoader>> getAllChunkLoaders() {
    return chunkLoaders;
  }

  public static String clickChunkLoader(ServerLevel world, ServerPlayer player, BlockPos pos) {
    String dim = world.dimension().location().toString();
    Map<BlockPos, ChunkLoaderSavedData.ChunkLoader> worldLoaders = chunkLoaders.computeIfAbsent(dim, k -> new HashMap<>());

    ChunkLoaderSavedData.ChunkLoader newChunkLoader = new ChunkLoaderSavedData.ChunkLoader(dim, pos, player);
    ChunkPos chunkPos = new ChunkPos(pos);

    ChunkLoaderSavedData.ChunkLoader actualChunkLoader = worldLoaders.get(pos);
    boolean isActive = actualChunkLoader != null;

    if (!isActive) {
      for (ChunkLoaderSavedData.ChunkLoader chunkLoader : worldLoaders.values()) {
        if (chunkPos.equals(new ChunkPos(chunkLoader.getPos()))) {
          return "A chunk loader from " + chunkLoader.getOwnerDisplayName() + " is already present in this chunk.";
        }
      }
    }

    if (isActive) {
      worldLoaders.remove(pos);
      toggleChunkLoader(world, actualChunkLoader, false);
    } else {
      int maxLoaders = DChunkLoaderConfig.MAX_LOADERS_PER_PLAYER.get();
      if (getPlayerChunkLoaders(player.getUUID().toString()).size() >= maxLoaders) {
        return "You have reached the max amount of chunkloader : " + maxLoaders;
      }
      worldLoaders.put(pos, newChunkLoader);
      toggleChunkLoader(world, newChunkLoader, true);
    }

    save(world);
    if (isActive) {
      return "Chunk loader turned off, your current limit : " + getPlayerChunkLoaders(player.getUUID()
                                                                                      .toString()).size() + "/" + DChunkLoaderConfig.MAX_LOADERS_PER_PLAYER.get();
    } else {
      return "Chunk loader turned on, your current limit : " + getPlayerChunkLoaders(player.getUUID()
                                                                                     .toString()).size() + "/" + DChunkLoaderConfig.MAX_LOADERS_PER_PLAYER.get();
    }
  }

  public static void breakBlock(ServerLevel world, BlockPos pos) {
    String dim = world.dimension().location().toString();
    Map<BlockPos, ChunkLoaderSavedData.ChunkLoader> worldLoaders = chunkLoaders.computeIfAbsent(dim, k -> new HashMap<>());

    ChunkLoaderSavedData.ChunkLoader actualChunkLoader = worldLoaders.get(pos);
    if (actualChunkLoader == null) return; // No chunk loader on this pos

    worldLoaders.remove(pos);
    toggleChunkLoader(world, actualChunkLoader, false);
    save(world);
  }

  public static Set<ChunkLoaderSavedData.ChunkLoader> getPlayerChunkLoaders(String playerUUID) {
    Set<ChunkLoaderSavedData.ChunkLoader> playerChunkLoaders = new HashSet<>();
    for (Map<BlockPos, ChunkLoaderSavedData.ChunkLoader> worldLoaders : chunkLoaders.values()) {
      for (ChunkLoaderSavedData.ChunkLoader chunkLoader : worldLoaders.values()) {
        DChunkLoader.LOGGER.debug("getOwnerUUID {}", chunkLoader.getOwnerUUID());
        DChunkLoader.LOGGER.debug("playerUUID {}", playerUUID);
        if (Objects.equals(chunkLoader.getOwnerUUID(), playerUUID)) {
          playerChunkLoaders.add(chunkLoader);
        }
      }
    }
    return playerChunkLoaders;
  }

  public static Set<ChunkLoaderSavedData.ChunkLoader> getTeamChunkLoaders(String teamName) {
    Set<TeamSavedData.TeamMember> teamMembers = TeamManager.getTeamMembers(teamName);

    Set<ChunkLoaderSavedData.ChunkLoader> teamChunkLoaders = new HashSet<>();
    if (teamMembers == null || teamMembers.isEmpty()) {
      return teamChunkLoaders;
    }
    for (TeamSavedData.TeamMember teamMember : teamMembers) {
      Set<ChunkLoaderSavedData.ChunkLoader> playerChunkLoaders = getPlayerChunkLoaders(teamMember.getUuid());
      teamChunkLoaders.addAll(playerChunkLoaders);
    }
    return teamChunkLoaders;
  }

  public static void loadForDimension(ServerLevel world) {
    String dim = world.dimension().location().toString();

    ChunkLoaderSavedData data = world.getDataStorage().computeIfAbsent(
    ChunkLoaderSavedData::load, ChunkLoaderSavedData::new, DATA_NAME
    );

    Map<String, Map<String, Set<ChunkLoaderSavedData.ChunkLoader>>> allData = data.getChunkLoaders();
    Map<String, Set<ChunkLoaderSavedData.ChunkLoader>> worldSavedLoaders = allData.computeIfAbsent(dim, k -> new HashMap<>());
    Map<BlockPos, ChunkLoaderSavedData.ChunkLoader> worldLoaders = new HashMap<>();
    for (Set<ChunkLoaderSavedData.ChunkLoader> playerSavedLoaders : worldSavedLoaders.values()) {
      for (ChunkLoaderSavedData.ChunkLoader chunkLoader : playerSavedLoaders) {
        worldLoaders.put(chunkLoader.getPos(), chunkLoader);
      }
    }
    chunkLoaders.put(dim, worldLoaders);

    // Cleanup chunks
    for (ChunkLoaderSavedData.ChunkLoader chunkLoader : worldLoaders.values()) {
      toggleChunkLoader(world, chunkLoader, false);
    }

    int total = worldLoaders.size();
    DChunkLoader.LOGGER.debug("Loaded {} chunk loaders for dimension {}", total, dim);
  }

  public static void save(ServerLevel world) {
    ChunkLoaderSavedData data = world.getDataStorage().computeIfAbsent(
    ChunkLoaderSavedData::load, ChunkLoaderSavedData::new, DATA_NAME
    );

    Map<String, Map<String, Set<ChunkLoaderSavedData.ChunkLoader>>> chunkLoadersSaved = new HashMap<>();

    for (Map.Entry<String, Map<BlockPos, ChunkLoaderSavedData.ChunkLoader>> worldLoader : chunkLoaders.entrySet()) {
      String dim = worldLoader.getKey();
      Map<BlockPos, ChunkLoaderSavedData.ChunkLoader> worldLoadersSaved = worldLoader.getValue();

      Map<String, Set<ChunkLoaderSavedData.ChunkLoader>> playerLoadersSaved = new HashMap<>();
      for (ChunkLoaderSavedData.ChunkLoader chunkLoader : worldLoadersSaved.values()) {
        String playerUUID = chunkLoader.getOwnerUUID();
        playerLoadersSaved.computeIfAbsent(playerUUID, k -> new HashSet<>()).add(chunkLoader);
      }

      chunkLoadersSaved.put(dim, playerLoadersSaved);
    }

    data.setChunkLoaders(chunkLoadersSaved);
    data.setDirty();

    int total = chunkLoaders.values().stream().mapToInt(Map::size).sum();
    DChunkLoader.LOGGER.debug("Saved {} chunk loaders across {} dimensions", total, chunkLoaders.size());
  }

  public static void toggleChunkLoader(ServerLevel world, ChunkLoaderSavedData.ChunkLoader chunkLoader, boolean toggle) {
    DChunkLoader.LOGGER.debug("Setting chunk to forced : here");
    MinecraftServer server = world.getServer();
    ResourceLocation dimRL = ResourceLocation.parse(chunkLoader.getDimString());
    ResourceKey<Level> resKey = ResourceKey.create(Registries.DIMENSION, dimRL);
    ServerLevel clWorld = server.getLevel(resKey);
    if (clWorld != null) {
      ChunkPos chunkPos = new ChunkPos(chunkLoader.getPos());
      clWorld.setChunkForced(chunkPos.x, chunkPos.z, toggle);
    }
  }

  private static void toggleAllChunkLoadersForTeam(ServerLevel world, String teamName, boolean toggle) {
    Set<ChunkLoaderSavedData.ChunkLoader> teamChunkLoaders = getTeamChunkLoaders(teamName);
    for (ChunkLoaderSavedData.ChunkLoader chunkLoader : teamChunkLoaders) {
      toggleChunkLoader(world, chunkLoader, toggle);
    }
  }

  private static void toggleAllChunkLoadersForPlayer(ServerLevel world, ServerPlayer player, boolean toggle) {
    Set<ChunkLoaderSavedData.ChunkLoader> playerChunkLoaders = getPlayerChunkLoaders(player.getUUID().toString());
    DChunkLoader.LOGGER.debug("Player has {}", playerChunkLoaders.size());
    for (ChunkLoaderSavedData.ChunkLoader chunkLoader : playerChunkLoaders) {
      toggleChunkLoader(world, chunkLoader, toggle);
    }
  }

  public static void onPlayerConnected(ServerPlayer player) {
    ServerLevel world = (ServerLevel) player.level();
    String teamName = TeamManager.getTeamForPlayer(player);
    if (teamName == null) {
      toggleAllChunkLoadersForPlayer(world, player, true);
      return;
    }
    toggleAllChunkLoadersForTeam(world, teamName, true);
  }

  public static void onPlayerDisconnected(ServerPlayer player) {
    ServerLevel world = (ServerLevel) player.level();
    String teamName = TeamManager.getTeamForPlayer(player);
    DChunkLoader.LOGGER.debug("disco");

    if (teamName == null) {
      DChunkLoader.LOGGER.debug("no team");
      toggleAllChunkLoadersForPlayer(world, player, false);
      return;
    }
    Set<TeamSavedData.TeamMember> teamMembers = TeamManager.getOnlineTeamMembers(world, teamName);
    teamMembers.remove(new TeamSavedData.TeamMember(player));
    if (teamMembers.isEmpty()) {
      DChunkLoader.LOGGER.debug("Should unload all chunk loaders for team {}", teamName);
      toggleAllChunkLoadersForTeam(world, teamName, false);
    }
  }

}
