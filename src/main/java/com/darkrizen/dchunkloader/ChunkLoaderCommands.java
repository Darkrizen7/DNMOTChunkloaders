package com.darkrizen.dchunkloader;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
      source.sendFailure(Component.literal("This command can only be used by players."));
      return 0;
    }

    Set<ChunkLoaderSavedData.ChunkLoader> playerLoaders = ChunkLoaderManager.getPlayerChunkLoaders(player.getUUID()
                                                                                                   .toString());
    int current = playerLoaders.size();
    int max = DChunkLoaderConfig.MAX_LOADERS_PER_PLAYER.get();

    MutableComponent message = Component.literal("Chunk Loaders: ")
    .withStyle(ChatFormatting.GRAY)
    .append(Component.literal(String.valueOf(current))
            .withStyle(current >= max ? ChatFormatting.RED : ChatFormatting.GREEN))
    .append(Component.literal("/")
            .withStyle(ChatFormatting.GRAY))
    .append(Component.literal(String.valueOf(max))
            .withStyle(ChatFormatting.AQUA))
    .append(Component.literal(" used").withStyle(ChatFormatting.GRAY));

    source.sendSystemMessage(message);

    return Command.SINGLE_SUCCESS;
  }

  private static int listActives(CommandSourceStack source) {
    if ((source.getEntity() instanceof ServerPlayer) && !source.hasPermission(2)) {
      source.sendFailure(Component.literal("You must be an admin to use this command."));
      return 0;
    }

    Map<String, Map<BlockPos, ChunkLoaderSavedData.ChunkLoader>> allLoaders = ChunkLoaderManager.getAllChunkLoaders();
    Map<String, Map<BlockPos, ChunkLoaderSavedData.ChunkLoader>> activeLoaders = ChunkLoaderManager.getActiveChunkLoaders();

    source.sendSystemMessage(Component.literal("\n--- GLOBAL CHUNK LOADERS ---")
                             .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
    source.sendSystemMessage(Component.literal("====================").withStyle(ChatFormatting.GRAY));

    for (Map.Entry<String, Map<BlockPos, ChunkLoaderSavedData.ChunkLoader>> dimEntry : allLoaders.entrySet()) {
      String dimString = dimEntry.getKey();
      Map<BlockPos, ChunkLoaderSavedData.ChunkLoader> dimLoaders = dimEntry.getValue();
      if (dimLoaders.isEmpty()) continue;

      Map<BlockPos, ChunkLoaderSavedData.ChunkLoader> activeInDimMap = activeLoaders.getOrDefault(dimString, Collections.emptyMap());

      int totalInDim = dimLoaders.size();
      int activeInDim = activeInDimMap.size();

      MutableComponent dimHeader = Component.literal("Dimension: ")
      .withStyle(ChatFormatting.GRAY)
      .append(Component.literal(dimString)
              .withStyle(ChatFormatting.AQUA))
      .append(Component.literal(" [")
              .withStyle(ChatFormatting.GRAY))
      .append(Component.literal(activeInDim + "/" + totalInDim)
              .withStyle(activeInDim > 0 ? ChatFormatting.GREEN : ChatFormatting.RED))
      .append(Component.literal(" active]").withStyle(ChatFormatting.GRAY));

      source.sendSystemMessage(dimHeader);

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

        MutableComponent playerLine = Component.literal("  └─ ")
        .withStyle(ChatFormatting.DARK_GRAY)
        .append(Component.literal(playerName)
                .withStyle(ChatFormatting.WHITE))
        .append(Component.literal(" : ")
                .withStyle(ChatFormatting.GRAY))
        .append(Component.literal(playerActiveCount + "/" + playerTotalCount)
                .withStyle(playerActiveCount > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));

        source.sendSystemMessage(playerLine);
      }
    }

    source.sendSystemMessage(Component.literal("====================").withStyle(ChatFormatting.GRAY));

    return Command.SINGLE_SUCCESS;
  }

  private static int listChunkLoader(CommandSourceStack source, ServerPlayer target) {
    if ((source.getEntity() instanceof ServerPlayer) && !source.hasPermission(2)) {
      source.sendFailure(Component.literal("You must be an admin to use this command."));
      return 0;
    }

    Set<ChunkLoaderSavedData.ChunkLoader> playerLoaders = ChunkLoaderManager.getPlayerChunkLoaders(target.getUUID()
                                                                                                   .toString());
    int current = playerLoaders.size();
    int max = DChunkLoaderConfig.MAX_LOADERS_PER_PLAYER.get();
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    source.sendSystemMessage(Component.literal("\n--- ADMIN INSPECTION ---")
                             .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
    source.sendSystemMessage(Component.literal("Player: ").withStyle(ChatFormatting.GRAY)
                             .append(target.getDisplayName().copy().withStyle(ChatFormatting.WHITE)));

    MutableComponent statusLine = Component.literal("Loaders: ").withStyle(ChatFormatting.GRAY)
    .append(Component.literal(current + "/" + max)
            .withStyle(current >= max ? ChatFormatting.GOLD : ChatFormatting.GREEN));
    source.sendSystemMessage(statusLine);

    source.sendSystemMessage(Component.literal("--------------------").withStyle(ChatFormatting.DARK_GRAY));

    int i = 1;
    for (ChunkLoaderSavedData.ChunkLoader chunkLoader : playerLoaders) {
      long lastActivatedMillis = chunkLoader.getLastActivated();
      String lastActivatedReadable = LocalDateTime.ofInstant(
      Instant.ofEpochMilli(lastActivatedMillis),
      ZoneId.systemDefault()
      ).format(formatter);

      String posString = chunkLoader.getPos().getX() + ", " + chunkLoader.getPos().getY() + ", " + chunkLoader.getPos()
      .getZ();
      String tpCommand = "/tp " + chunkLoader.getPos().getX() + " " + chunkLoader.getPos()
      .getY() + " " + chunkLoader.getPos()
      .getZ();

      MutableComponent loaderLine = Component.literal(i + ". ")
      .withStyle(ChatFormatting.YELLOW)
      .append(Component.literal(chunkLoader.getDimString().split(":")[1].toUpperCase())
              .withStyle(ChatFormatting.AQUA))
      .append(Component.literal(" at ").withStyle(ChatFormatting.GRAY))
      .append(Component.literal("[" + posString + "]")
              .withStyle(style -> style
              .withColor(ChatFormatting.WHITE)
              .withUnderlined(true)
              .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, tpCommand))
              .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to teleport")))));

      source.sendSystemMessage(loaderLine);
      source.sendSystemMessage(Component.literal("   └─ Last Seen: ").withStyle(ChatFormatting.DARK_GRAY)
                               .append(Component.literal(lastActivatedReadable).withStyle(ChatFormatting.GRAY)));

      i++;
    }

    source.sendSystemMessage(Component.literal("--------------------").withStyle(ChatFormatting.DARK_GRAY));

    return Command.SINGLE_SUCCESS;
  }

  private static int debugClean(CommandSourceStack source) {
    if ((source.getEntity() instanceof ServerPlayer) && !source.hasPermission(2)) {
      source.sendFailure(Component.literal("You must be an admin to use this command."));
      return 0;
    }

    MinecraftServer server = source.getServer();
    Map<String, Map<BlockPos, ChunkLoaderSavedData.ChunkLoader>> modActiveLoaders = ChunkLoaderManager.getActiveChunkLoaders();

    source.sendSystemMessage(Component.literal("\n--- CHUNK LOADER CLEAN-UP ---")
                             .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
    source.sendSystemMessage(Component.literal("Starting process...")
                             .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));

    int totalChunksUnforced = 0;

    for (ServerLevel world : server.getAllLevels()) {
      String dimString = world.dimension().location().toString();

      Set<Long> mcForcedChunksLong = world.getForcedChunks();
      Set<Long> mcForcedChunks = new HashSet<>(mcForcedChunksLong);

      Map<BlockPos, ChunkLoaderSavedData.ChunkLoader> modLoadersInDim = modActiveLoaders.getOrDefault(dimString, Collections.emptyMap());

      Set<Long> modExpectedChunks = modLoadersInDim.values().stream()
      .map(loader -> new ChunkPos(loader.getPos()).toLong())
      .collect(Collectors.toSet());

      Set<Long> unexpectedForced = new HashSet<>(mcForcedChunks);
      unexpectedForced.removeAll(modExpectedChunks);

      if (!unexpectedForced.isEmpty()) {
        MutableComponent dimLog = Component.literal("  > ")
        .withStyle(ChatFormatting.DARK_GRAY)
        .append(Component.literal(dimString.split(":")[1].toUpperCase())
                .withStyle(ChatFormatting.AQUA))
        .append(Component.literal(": Unforcing ")
                .withStyle(ChatFormatting.GRAY))
        .append(Component.literal(String.valueOf(unexpectedForced.size()))
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
        .append(Component.literal(" rogue chunks").withStyle(ChatFormatting.GRAY));

        source.sendSystemMessage(dimLog);

        for (long chunkLong : unexpectedForced) {
          int x = ChunkPos.getX(chunkLong);
          int z = ChunkPos.getZ(chunkLong);

          world.setChunkForced(x, z, false);
          totalChunksUnforced++;
        }
      }
    }

    MutableComponent footer = Component.literal("====================").withStyle(ChatFormatting.GRAY)
    .append(Component.literal("\nTotal cleaned: ").withStyle(ChatFormatting.WHITE))
    .append(Component.literal(String.valueOf(totalChunksUnforced))
            .withStyle(totalChunksUnforced > 0 ? ChatFormatting.GREEN : ChatFormatting.GOLD, ChatFormatting.BOLD));

    source.sendSystemMessage(footer);

    return Command.SINGLE_SUCCESS;
  }

  private static int debug(CommandSourceStack source) {
    if ((source.getEntity() instanceof ServerPlayer) && !source.hasPermission(2)) {
      source.sendFailure(Component.literal("You must be an admin to use this command."));
      return 0;
    }

    MinecraftServer server = source.getServer();
    Map<String, Map<BlockPos, ChunkLoaderSavedData.ChunkLoader>> modActiveLoaders = ChunkLoaderManager.getActiveChunkLoaders();

    source.sendSystemMessage(Component.literal("\n--- CHUNK LOADER DEBUG ---")
                             .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
    source.sendSystemMessage(Component.literal("Checking consistency...")
                             .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));

    boolean errorsFound = false;

    for (ServerLevel world : server.getAllLevels()) {
      String dimString = world.dimension().location().toString();
      Set<Long> mcForcedChunks = new HashSet<>(world.getForcedChunks());
      Map<BlockPos, ChunkLoaderSavedData.ChunkLoader> modLoadersInDim = modActiveLoaders.getOrDefault(dimString, Collections.emptyMap());

      Set<Long> modExpectedChunks = modLoadersInDim.values().stream()
      .map(loader -> new ChunkPos(loader.getPos()).toLong())
      .collect(Collectors.toSet());

      Set<Long> unexpectedForced = new HashSet<>(mcForcedChunks);
      unexpectedForced.removeAll(modExpectedChunks);

      Set<Long> missingForced = new HashSet<>(modExpectedChunks);
      missingForced.removeAll(mcForcedChunks);

      if (!unexpectedForced.isEmpty() || !missingForced.isEmpty()) {
        errorsFound = true;
        source.sendSystemMessage(Component.literal("\nDimension: ").withStyle(ChatFormatting.GRAY)
                                 .append(Component.literal(dimString).withStyle(ChatFormatting.AQUA)));

        if (!missingForced.isEmpty()) {
          source.sendSystemMessage(Component.literal("  [!] ").withStyle(ChatFormatting.RED)
                                   .append(Component.literal(missingForced.size() + " chunks should be forced but are NOT:")
                                           .withStyle(ChatFormatting.WHITE)));
          for (long chunkLong : missingForced) {
            source.sendSystemMessage(Component.literal("    -> X=" + ChunkPos.getX(chunkLong) + ", Z=" + ChunkPos.getZ(chunkLong))
                                     .withStyle(ChatFormatting.DARK_RED));
          }
        }

        if (!unexpectedForced.isEmpty()) {
          source.sendSystemMessage(Component.literal("  [?] ").withStyle(ChatFormatting.YELLOW)
                                   .append(Component.literal(unexpectedForced.size() + " chunks are forced but should NOT be:")
                                           .withStyle(ChatFormatting.WHITE)));
          for (long chunkLong : unexpectedForced) {
            source.sendSystemMessage(Component.literal("    -> X=" + ChunkPos.getX(chunkLong) + ", Z=" + ChunkPos.getZ(chunkLong))
                                     .withStyle(ChatFormatting.GOLD));
          }
        }
      }
    }

    source.sendSystemMessage(Component.literal("\n====================").withStyle(ChatFormatting.GRAY));
    if (!errorsFound) {
      source.sendSystemMessage(Component.literal("STATUS: OK").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
    } else {
      source.sendSystemMessage(Component.literal("STATUS: INCONSISTENCIES FOUND")
                               .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
    }

    return Command.SINGLE_SUCCESS;
  }

  // Player version
  private static int clean(CommandSourceStack source) {
    if (!(source.getEntity() instanceof ServerPlayer player)) {
      source.sendFailure(Component.literal("This command can only be used by players."));
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

    MutableComponent successMessage = Component.literal("Success! ")
    .withStyle(ChatFormatting.GREEN)
    .append(Component.literal("You have successfully cleaned ")
            .withStyle(ChatFormatting.WHITE))
    .append(Component.literal(String.valueOf(i))
            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
    .append(Component.literal(" chunk loaders.")
            .withStyle(ChatFormatting.WHITE));

    source.sendSystemMessage(successMessage);

    return Command.SINGLE_SUCCESS;
  }

  // Admin version
  private static int clean(CommandSourceStack source, ServerPlayer target) {
    if ((source.getEntity() instanceof ServerPlayer) && !source.hasPermission(2)) {
      source.sendFailure(Component.literal("You must be an admin to use this command."));
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

    MutableComponent successMessage = Component.literal("Admin Success: ")
    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
    .append(Component.literal("Removed ")
            .withStyle(ChatFormatting.WHITE))
    .append(Component.literal(String.valueOf(i))
            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
    .append(Component.literal(" chunk loaders from ")
            .withStyle(ChatFormatting.WHITE))
    .append(target.getDisplayName().copy().withStyle(ChatFormatting.YELLOW));

    source.sendSystemMessage(successMessage);

    return Command.SINGLE_SUCCESS;
  }
}
