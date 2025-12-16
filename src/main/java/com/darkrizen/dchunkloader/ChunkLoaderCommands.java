package com.darkrizen.dchunkloader;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;

public class ChunkLoaderCommands {
  public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(
    Commands.literal("dcl")
    .then(Commands.literal("chunkloader")
          .then(Commands.literal("list")
                .executes(context -> listChunkLoader(context.getSource()))
                //TODO : Add list active
                .then(Commands.argument("player", EntityArgument.player())
                      .requires(src -> src.hasPermission(2))
                      .executes(context -> listChunkLoader(context.getSource(), EntityArgument.getPlayer(context, "player"))))
          )
          .then(Commands.literal("debug")
                .requires(src -> src.hasPermission(2))
                .executes(context -> debug(context.getSource()))
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

  private static int debug(CommandSourceStack source) {
    if ((source.getEntity() instanceof ServerPlayer) && !source.hasPermission(2)) {
      source.sendFailure(Component.literal("You must be an admin to use this command"));
      return 0;
    }
    // TODO : Show all in dimensions
    return Command.SINGLE_SUCCESS; // Should never happen
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
