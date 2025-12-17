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

  public static Map<String, Map<BlockPos, ChunkLoaderSavedData.ChunkLoader>> getActiveChunkLoaders() {
    Map<String, Map<BlockPos, ChunkLoaderSavedData.ChunkLoader>> activeChunkLoaders = new HashMap<>();

    for (Map.Entry<String, Map<BlockPos, ChunkLoaderSavedData.ChunkLoader>> dimEntry : chunkLoaders.entrySet()) {
      String dim = dimEntry.getKey();
      Map<BlockPos, ChunkLoaderSavedData.ChunkLoader> allLoadersInDim = dimEntry.getValue();
      Map<BlockPos, ChunkLoaderSavedData.ChunkLoader> activeLoadersInDim = new HashMap<>();

      for (Map.Entry<BlockPos, ChunkLoaderSavedData.ChunkLoader> loaderEntry : allLoadersInDim.entrySet()) {
        ChunkLoaderSavedData.ChunkLoader loader = loaderEntry.getValue();
        if (loader.isActivated()) {
          activeLoadersInDim.put(loaderEntry.getKey(), loader);
        }
      }
      if (!activeLoadersInDim.isEmpty()) {
        activeChunkLoaders.put(dim, activeLoadersInDim);
      }
    }
    return activeChunkLoaders;
  }

  public static ActivateChunkLoaderResult clickChunkLoader(ServerLevel world, ServerPlayer player, BlockPos pos) {
    String dim = world.dimension().location().toString();
    Map<BlockPos, ChunkLoaderSavedData.ChunkLoader> worldLoaders = chunkLoaders.computeIfAbsent(dim, k -> new HashMap<>());

    ChunkLoaderSavedData.ChunkLoader actualChunkLoader = worldLoaders.get(pos);
    boolean isActive = actualChunkLoader != null;

    if (isActive) {
      if (removeChunkLoader(world, actualChunkLoader)) {
        return ActivateChunkLoaderResult.SUCCESS_OFF;
      }
    } else {
      ActivateChunkLoaderResult result = canActivateChunkLoader(world, player, pos);
      if (result != ActivateChunkLoaderResult.SUCCESS) {
        return result;
      }
      if (activateChunkLoader(world, player, pos)) {
        return ActivateChunkLoaderResult.SUCCESS_ON;
      }
    }
    return ActivateChunkLoaderResult.INTERNAL_ERROR;
  }

  public static boolean activateChunkLoader(ServerLevel world, ServerPlayer player, BlockPos pos) {
    String dim = world.dimension().location().toString();
    Map<BlockPos, ChunkLoaderSavedData.ChunkLoader> worldLoaders = chunkLoaders.computeIfAbsent(dim, k -> new HashMap<>());

    ChunkLoaderSavedData.ChunkLoader newChunkLoader = new ChunkLoaderSavedData.ChunkLoader(dim, pos, player);

    worldLoaders.put(pos, newChunkLoader);
    toggleChunkLoader(world, newChunkLoader, true);
    save(world);

    return true;
  }

  public static boolean removeChunkLoader(ServerLevel world, ChunkLoaderSavedData.ChunkLoader actualChunkLoader) {
    if (actualChunkLoader == null) {
      return false;
    }

    BlockPos pos = actualChunkLoader.getPos();
    String dim = actualChunkLoader.getDimString();

    Map<BlockPos, ChunkLoaderSavedData.ChunkLoader> worldLoaders = chunkLoaders.computeIfAbsent(dim, k -> new HashMap<>());

    worldLoaders.remove(pos);
    toggleChunkLoader(world, actualChunkLoader, false);
    save(world);

    return true;
  }

  public static ActivateChunkLoaderResult canActivateChunkLoader(ServerLevel world, ServerPlayer player, BlockPos pos) {
    String dim = world.dimension().location().toString();
    Map<BlockPos, ChunkLoaderSavedData.ChunkLoader> worldLoaders = chunkLoaders.computeIfAbsent(dim, k -> new HashMap<>());
    ChunkPos chunkPos = new ChunkPos(pos);

    for (ChunkLoaderSavedData.ChunkLoader chunkLoader : worldLoaders.values()) {
      if (chunkPos.equals(new ChunkPos(chunkLoader.getPos()))) {
        return ActivateChunkLoaderResult.CHUNK_LOADER_ALREADY_PRESENT;
      }
    }

    int maxLoaders = DChunkLoaderConfig.MAX_LOADERS_PER_PLAYER.get();
    if (getPlayerChunkLoadersCount(player) >= maxLoaders) {
      return ActivateChunkLoaderResult.MAX_AMOUNT_REACHED;
    }

    return ActivateChunkLoaderResult.SUCCESS;
  }

  public static int getPlayerChunkLoadersCount(ServerPlayer player) {
    return getPlayerChunkLoaders(player.getUUID().toString()).size();
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

    MinecraftServer server = world.getServer();
    ServerLevel overworld = server.getLevel(Level.OVERWORLD);
    if (overworld == null) {
      DChunkLoader.LOGGER.error("Error overworld does not exists");
      DChunkLoader.LOGGER.error("Existing worlds :");
      for (ServerLevel level : world.getServer().getAllLevels()) {
        DChunkLoader.LOGGER.error(level.dimension().location().toString());
      }
      return;
    }

    ChunkLoaderSavedData data = overworld.getDataStorage().computeIfAbsent(
    ChunkLoaderSavedData::load, ChunkLoaderSavedData::new, DATA_NAME
    );

    Map<String, Map<String, Set<ChunkLoaderSavedData.ChunkLoader>>> allData = data.getChunkLoaders();
    Map<String, Set<ChunkLoaderSavedData.ChunkLoader>> worldSavedLoaders =
    allData.computeIfAbsent(dim, k -> new HashMap<>());

    Map<BlockPos, ChunkLoaderSavedData.ChunkLoader> worldLoaders = new HashMap<>();
    long now = System.currentTimeMillis();
    long maxAgeMillis = DChunkLoaderConfig.MAX_CHUNKLOADER_AGE_DAYS.get() * 24L * 60L * 60L * 1000L;
    for (Set<ChunkLoaderSavedData.ChunkLoader> playerSavedLoaders : worldSavedLoaders.values()) {
      Iterator<ChunkLoaderSavedData.ChunkLoader> iter = playerSavedLoaders.iterator();
      while (iter.hasNext()) {
        ChunkLoaderSavedData.ChunkLoader chunkLoader = iter.next();
        long age = now - chunkLoader.getLastActivated();

        if (age > maxAgeMillis) {
          iter.remove();
          DChunkLoader.LOGGER.debug("Removed chunk loader at {} from player {} (last activated {} ms ago)",
                                    chunkLoader.getPos(), chunkLoader.getOwnerUUID(), age);
        } else {
          worldLoaders.put(chunkLoader.getPos(), chunkLoader);
        }
      }
    }

    chunkLoaders.put(dim, worldLoaders);
    for (ChunkLoaderSavedData.ChunkLoader chunkLoader : worldLoaders.values()) {
      toggleChunkLoader(world, chunkLoader, false);
    }

    int total = worldLoaders.size();
    DChunkLoader.LOGGER.debug("Loaded {} chunk loaders for dimension {}", total, dim);
  }

  public static void save(ServerLevel world) {

    MinecraftServer server = world.getServer();
    ServerLevel overworld = server.getLevel(Level.OVERWORLD);
    if (overworld == null) {
      DChunkLoader.LOGGER.error("Error overworld does not exists");
      DChunkLoader.LOGGER.error("Existing worlds :");
      for (ServerLevel level : world.getServer().getAllLevels()) {
        DChunkLoader.LOGGER.error(level.dimension().location().toString());
      }
      return;
    }

    ChunkLoaderSavedData data = overworld.getDataStorage().computeIfAbsent(
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
    MinecraftServer server = world.getServer();
    ResourceLocation dimRL = ResourceLocation.parse(chunkLoader.getDimString());
    ResourceKey<Level> resKey = ResourceKey.create(Registries.DIMENSION, dimRL);
    ServerLevel clWorld = server.getLevel(resKey);
    if (clWorld != null) {
      ChunkPos chunkPos = new ChunkPos(chunkLoader.getPos());
      clWorld.setChunkForced(chunkPos.x, chunkPos.z, toggle);
      chunkLoader.setActivated(toggle);
    }
  }

  public static void toggleAllChunkLoadersForTeam(ServerLevel world, String teamName, boolean toggle) {
    Set<ChunkLoaderSavedData.ChunkLoader> teamChunkLoaders = getTeamChunkLoaders(teamName);
    for (ChunkLoaderSavedData.ChunkLoader chunkLoader : teamChunkLoaders) {
      toggleChunkLoader(world, chunkLoader, toggle);
    }
  }

  private static void toggleAllChunkLoadersForPlayer(ServerLevel world, ServerPlayer player, boolean toggle) {
    Set<ChunkLoaderSavedData.ChunkLoader> playerChunkLoaders = getPlayerChunkLoaders(player.getUUID().toString());
    for (ChunkLoaderSavedData.ChunkLoader chunkLoader : playerChunkLoaders) {
      toggleChunkLoader(world, chunkLoader, toggle);
    }
  }

  public static void onPlayerConnected(ServerPlayer player) {
    ServerLevel world = (ServerLevel) player.level();
    String teamName = TeamManager.getTeamForPlayer(player);
    resetLastActivated(player);
    if (teamName == null) {
      toggleAllChunkLoadersForPlayer(world, player, true);
      return;
    }
    toggleAllChunkLoadersForTeam(world, teamName, true);
  }

  private static void resetLastActivated(ServerPlayer player) {
    for (Map<BlockPos, ChunkLoaderSavedData.ChunkLoader> worldLoaders : chunkLoaders.values()) {
      for (ChunkLoaderSavedData.ChunkLoader chunkLoader : worldLoaders.values()) {
        if (Objects.equals(chunkLoader.getOwnerUUID(), player.getUUID().toString())) {
          chunkLoader.setLastActivated(System.currentTimeMillis());
        }
      }
    }
    MinecraftServer server = player.getServer();
    if (server != null) {
      ServerLevel overworld = server.getLevel(net.minecraft.world.level.Level.OVERWORLD);
      if (overworld != null) {
        save(overworld);
      }
    }
  }

  public static void onPlayerDisconnected(ServerPlayer player) {
    ServerLevel world = (ServerLevel) player.level();
    String teamName = TeamManager.getTeamForPlayer(player);

    if (teamName == null) {
      toggleAllChunkLoadersForPlayer(world, player, false);
      return;
    }
    Set<TeamSavedData.TeamMember> teamMembers = TeamManager.getOnlineTeamMembers(world, teamName);
    teamMembers.remove(new TeamSavedData.TeamMember(player));
    if (teamMembers.isEmpty()) {
      toggleAllChunkLoadersForTeam(world, teamName, false);
    }
  }

  public enum ActivateChunkLoaderResult {
    SUCCESS,
    CHUNK_LOADER_ALREADY_PRESENT,
    MAX_AMOUNT_REACHED,
    SUCCESS_ON,
    SUCCESS_OFF,
    INTERNAL_ERROR
  }
}
