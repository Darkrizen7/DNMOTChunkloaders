package com.darkrizen.dchunkloader;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ChunkLoaderCommands {
  public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(
    Commands.literal("dcl")
    .then(Commands.literal("chunkloader")
          .then(Commands.literal("list")
                .executes(context -> listChunkLoader(context.getSource()))
                .then(Commands.literal("actives")
                      .requires(src -> src.hasPermission(2))
                      .executes(context -> listActives(context.getSource())))
                .then(Commands.argument("player", EntityArgument.player())
                      .requires(src -> src.hasPermission(2))
                      .executes(context -> listChunkLoader(context.getSource(), EntityArgument.getPlayer(context, "player"))))
          )
          .then(Commands.literal("debug")
                .requires(src -> src.hasPermission(2))
                .executes(context -> debug(context.getSource()))
                .then(Commands.literal("clean")
                      .executes(context -> debugClean(context.getSource())))
          )
          .then(Commands.literal("clean")
                .executes(context -> clean(context.getSource()))
                .then(Commands.argument("player", EntityArgument.player())
                      .requires(src -> src.hasPermission(2))
                      .executes(context -> clean(context.getSource(), EntityArgument.getPlayer(context, "player"))))
          )
    )
    );
  }

  private static int listChunkLoader(CommandSourceStack source) {
    if (!(source.getEntity() instanceof ServerPlayer player)) {
      source.sendFailure(Component.literal("Player command only"));
      return 0;
    }
    Set<ChunkLoaderSavedData.ChunkLoader> playerLoaders = ChunkLoaderManager.getPlayerChunkLoaders(player.getUUID()
                                                                                                   .toString());
    source.sendSystemMessage(Component.literal("Chunk Loaders " + playerLoaders.size() + "/" + DChunkLoaderConfig.MAX_LOADERS_PER_PLAYER.get()));
    return Command.SINGLE_SUCCESS;
  }

  private static int listActives(CommandSourceStack source) {
    if ((source.getEntity() instanceof ServerPlayer) && !source.hasPermission(2)) {
      source.sendFailure(Component.literal("You must be an admin to use this command"));
      return 0;
    }

    Map<String, Map<BlockPos, ChunkLoaderSavedData.ChunkLoader>> allLoaders = ChunkLoaderManager.getAllChunkLoaders();
    Map<String, Map<BlockPos, ChunkLoaderSavedData.ChunkLoader>> activeLoaders = ChunkLoaderManager.getActiveChunkLoaders();

    source.sendSystemMessage(Component.literal("Chunk Loaders"));
    source.sendSystemMessage(Component.literal("===================="));

    for (Map.Entry<String, Map<BlockPos, ChunkLoaderSavedData.ChunkLoader>> dimEntry : allLoaders.entrySet()) {
      String dimString = dimEntry.getKey();
      Map<BlockPos, ChunkLoaderSavedData.ChunkLoader> dimLoaders = dimEntry.getValue();

      Map<BlockPos, ChunkLoaderSavedData.ChunkLoader> activeInDimMap =
      activeLoaders.getOrDefault(dimString, Collections.emptyMap());

      int totalInDim = dimLoaders.size();
      int activeCount = activeInDimMap.size();

      source.sendSystemMessage(Component.literal(dimString +
                                                 " " + activeCount + " / " + totalInDim));

      Map<String, Set<ChunkLoaderSavedData.ChunkLoader>> loadersByPlayer = dimLoaders.values().stream()
      .collect(Collectors.groupingBy(
      ChunkLoaderSavedData.ChunkLoader::getOwnerDisplayName,
      Collectors.toSet()
      ));

      for (Map.Entry<String, Set<ChunkLoaderSavedData.ChunkLoader>> playerEntry : loadersByPlayer.entrySet()) {
        String playerName = playerEntry.getKey();
        Set<ChunkLoaderSavedData.ChunkLoader> playerDimLoaders = playerEntry.getValue();

        long playerActiveCount = playerDimLoaders.stream()
        .filter(ChunkLoaderSavedData.ChunkLoader::isActivated)
        .count();

        long playerTotalCount = playerDimLoaders.size();

        source.sendSystemMessage(Component.literal("  - " + playerName +
                                                   " : " + playerActiveCount + " / " + playerTotalCount));
      }
    }

    source.sendSystemMessage(Component.literal("===================="));

    return Command.SINGLE_SUCCESS;
  }

  private static int listChunkLoader(CommandSourceStack source, ServerPlayer target) {
    if ((source.getEntity() instanceof ServerPlayer) && !source.hasPermission(2)) {
      source.sendFailure(Component.literal("You must be an admin to use this command"));
      return 0;
    }
    Set<ChunkLoaderSavedData.ChunkLoader> playerLoaders = ChunkLoaderManager.getPlayerChunkLoaders(target.getUUID()
                                                                                                   .toString());
    source.sendSystemMessage(Component.literal("Chunk Loaders " + playerLoaders.size() + "/" + DChunkLoaderConfig.MAX_LOADERS_PER_PLAYER.get()));
    source.sendSystemMessage(Component.literal("--------------------"));
    int i = 1;
    for (ChunkLoaderSavedData.ChunkLoader chunkLoader : playerLoaders) {
      source.sendSystemMessage(Component.literal(i + ". " + chunkLoader.getDimString() + " : " + chunkLoader.getPos()));
      i++;
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int debugClean(CommandSourceStack source) {
    if ((source.getEntity() instanceof ServerPlayer) && !source.hasPermission(2)) {
      source.sendFailure(Component.literal("You must be an admin to use this command"));
      return 0;
    }
    MinecraftServer server = source.getServer();

    Map<String, Map<BlockPos, ChunkLoaderSavedData.ChunkLoader>> modActiveLoaders =
    ChunkLoaderManager.getActiveChunkLoaders();

    source.sendSystemMessage(Component.literal("--- Starting Chunk Loader Clean-up ---"));

    int totalChunksUnforced = 0;

    for (ServerLevel world : server.getAllLevels()) {
      String dimString = world.dimension().location().toString();

      Set<Long> mcForcedChunksLong = world.getForcedChunks();
      Set<Long> mcForcedChunks = new HashSet<>(mcForcedChunksLong);

      Map<BlockPos, ChunkLoaderSavedData.ChunkLoader> modLoadersInDim =
      modActiveLoaders.getOrDefault(dimString, Collections.emptyMap());

      Set<Long> modExpectedChunks = modLoadersInDim.values().stream()
      .map(loader -> new ChunkPos(loader.getPos()).toLong())
      .collect(Collectors.toSet());

      Set<Long> unexpectedForced = new HashSet<>(mcForcedChunks);
      unexpectedForced.removeAll(modExpectedChunks);

      if (!unexpectedForced.isEmpty()) {
        source.sendSystemMessage(Component.literal("Dimension " + dimString + ": Unforcing " + unexpectedForced.size()));

        for (long chunkLong : unexpectedForced) {
          int x = ChunkPos.getX(chunkLong);
          int z = ChunkPos.getZ(chunkLong);

          world.setChunkForced(x, z, false);
          totalChunksUnforced++;
        }
      }
    }

    source.sendSystemMessage(Component.literal("Total cleaned: " + totalChunksUnforced));

    return Command.SINGLE_SUCCESS;
  }

  private static int debug(CommandSourceStack source) {
    if ((source.getEntity() instanceof ServerPlayer) && !source.hasPermission(2)) {
      source.sendFailure(Component.literal("You must be an admin to use this command"));
      return 0;
    }
    MinecraftServer server = source.getServer();

    Map<String, Map<BlockPos, ChunkLoaderSavedData.ChunkLoader>> modActiveLoaders =
    ChunkLoaderManager.getActiveChunkLoaders();

    source.sendSystemMessage(Component.literal("--- Chunk Loader Debug ---"));

    boolean errorsFound = false;

    for (ServerLevel world : server.getAllLevels()) {
      String dimString = world.dimension().location().toString();

      Set<Long> mcForcedChunksLong = world.getForcedChunks();
      Set<Long> mcForcedChunks = new HashSet<>(mcForcedChunksLong);

      Map<BlockPos, ChunkLoaderSavedData.ChunkLoader> modLoadersInDim =
      modActiveLoaders.getOrDefault(dimString, Collections.emptyMap());

      Set<Long> modExpectedChunks = modLoadersInDim.values().stream()
      .map(loader -> new ChunkPos(loader.getPos()).toLong())
      .collect(Collectors.toSet());

      Set<Long> unexpectedForced = new HashSet<>(mcForcedChunks);
      unexpectedForced.removeAll(modExpectedChunks);

      Set<Long> missingForced = new HashSet<>(modExpectedChunks);
      missingForced.removeAll(mcForcedChunks);

      if (!unexpectedForced.isEmpty() || !missingForced.isEmpty()) {
        errorsFound = true;
        source.sendSystemMessage(Component.literal("Dimension: " + dimString));

        if (!missingForced.isEmpty()) {
          source.sendSystemMessage(Component.literal("  [WEIRD] " + missingForced.size() + " chunks should be forced but are not:"));
          for (long chunkLong : missingForced) {
            source.sendSystemMessage(Component.literal("    -> Chunk at X=" + ChunkPos.getX(chunkLong) + ", Z=" + ChunkPos.getZ(chunkLong)));
          }
        }

        if (!unexpectedForced.isEmpty()) {
          source.sendSystemMessage(Component.literal("  [WEIRD] " + unexpectedForced.size() + " chunks are forced but should NOT be (Source unknown):"));
          for (long chunkLong : unexpectedForced) {
            source.sendSystemMessage(Component.literal("    -> Chunk at X=" + ChunkPos.getX(chunkLong) + ", Z=" + ChunkPos.getZ(chunkLong)));
          }
        }
      }
    }

    if (!errorsFound) {
      source.sendSystemMessage(Component.literal("--- OK ---"));
    } else {
      source.sendSystemMessage(Component.literal("--- NOT OK ---"));
    }

    return Command.SINGLE_SUCCESS;
  }

  // Player version
  private static int clean(CommandSourceStack source) {
    if (!(source.getEntity() instanceof ServerPlayer player)) {
      source.sendFailure(Component.literal("Player command only"));
      return 0;
    }
    Set<ChunkLoaderSavedData.ChunkLoader> playerLoaders = ChunkLoaderManager.getPlayerChunkLoaders(player.getUUID()
                                                                                                   .toString());
    int i = 0;
    for (ChunkLoaderSavedData.ChunkLoader chunkLoader : playerLoaders) {
      if (ChunkLoaderManager.removeChunkLoader((ServerLevel) player.level(), chunkLoader)) {
        i++;
      }
    }
    source.sendSystemMessage(Component.literal("You have successfully clean " + i + " chunk loaders."));
    return Command.SINGLE_SUCCESS;
  }

  // Admin version
  private static int clean(CommandSourceStack source, ServerPlayer target) {
    if ((source.getEntity() instanceof ServerPlayer) && !source.hasPermission(2)) {
      source.sendFailure(Component.literal("You must be an admin to use this command"));
      return 0;
    }
    Set<ChunkLoaderSavedData.ChunkLoader> playerLoaders = ChunkLoaderManager.getPlayerChunkLoaders(target.getUUID()
                                                                                                   .toString());
    int i = 0;
    for (ChunkLoaderSavedData.ChunkLoader chunkLoader : playerLoaders) {
      if (ChunkLoaderManager.removeChunkLoader((ServerLevel) target.level(), chunkLoader)) {
        i++;
      }
    }
    source.sendSystemMessage(Component.literal("You have successfully clean " + i + " chunk loaders from " + target.getGameProfile()
    .getName()));
    return Command.SINGLE_SUCCESS;
  }
}
