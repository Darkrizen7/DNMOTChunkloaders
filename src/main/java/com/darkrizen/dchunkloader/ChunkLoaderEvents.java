package com.darkrizen.dchunkloader;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = DChunkLoader.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ChunkLoaderEvents {

  @SubscribeEvent
  public static void onBlockRightClick(PlayerInteractEvent.RightClickBlock event) {
    if (event.getLevel().isClientSide()) return;
    if (event.getHand() != InteractionHand.MAIN_HAND) return;

    ServerPlayer player = (ServerPlayer) event.getEntity();
    Level world = player.level();
    BlockPos pos = event.getPos();
    Block clickedBlock = world.getBlockState(pos).getBlock();

    Block activationBlock = ForgeRegistries.BLOCKS.getValue(ResourceLocation.parse(DChunkLoaderConfig.ACTIVATION_BLOCK.get()));
    if (activationBlock == null) activationBlock = Blocks.IRON_BLOCK;
    if (clickedBlock != activationBlock) return;

    Item activationItem = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(DChunkLoaderConfig.ACTIVATION_ITEM.get()));
    if (activationItem == null) activationItem = Items.STICK;
    if (player.getItemInHand(InteractionHand.MAIN_HAND).getItem() != activationItem) return;

    ChunkLoaderManager.ActivateChunkLoaderResult result = ChunkLoaderManager.clickChunkLoader((ServerLevel) world, player, pos);
    MutableComponent message = Component.literal("");

    int current = ChunkLoaderManager.getPlayerChunkLoaders(player.getUUID().toString()).size();
    int max = DChunkLoaderConfig.MAX_LOADERS_PER_PLAYER.get();

    switch (result) {
      case SUCCESS_ON -> {
        message = Component.literal("✔ ").withStyle(ChatFormatting.GREEN)
        .append(Component.literal("Chunk loader ").withStyle(ChatFormatting.WHITE))
        .append(Component.literal("ENABLED").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD))
        .append(Component.literal(" (").withStyle(ChatFormatting.GRAY))
        .append(Component.literal(current + "/" + max).withStyle(ChatFormatting.AQUA))
        .append(Component.literal(")").withStyle(ChatFormatting.GRAY));
      }
      case SUCCESS_OFF -> {
        message = Component.literal("✘ ").withStyle(ChatFormatting.RED)
        .append(Component.literal("Chunk loader ").withStyle(ChatFormatting.WHITE))
        .append(Component.literal("DISABLED").withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
        .append(Component.literal(" (").withStyle(ChatFormatting.GRAY))
        .append(Component.literal(current + "/" + max).withStyle(ChatFormatting.AQUA))
        .append(Component.literal(")").withStyle(ChatFormatting.GRAY));
      }
      case INTERNAL_ERROR -> {
        message = Component.literal("⚠ ").withStyle(ChatFormatting.DARK_RED)
        .append(Component.literal("Internal error. Please contact an administrator.").withStyle(ChatFormatting.RED));
      }
      case MAX_AMOUNT_REACHED -> {
        message = Component.literal("⚠ ").withStyle(ChatFormatting.GOLD)
        .append(Component.literal("Limit reached! ").withStyle(ChatFormatting.RED))
        .append(Component.literal("You are at ").withStyle(ChatFormatting.WHITE))
        .append(Component.literal(current + "/" + max).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
        .append(Component.literal(" loaders.").withStyle(ChatFormatting.WHITE));
      }
      case CHUNK_LOADER_ALREADY_PRESENT -> {
        message = Component.literal("⚠ ").withStyle(ChatFormatting.YELLOW)
        .append(Component.literal("A chunk loader is already active in this chunk.").withStyle(ChatFormatting.WHITE));
      }
    }

    // Cancel the event (bc stick is placing ... thanks TFG)
    if (event.isCancelable()) {
      event.setCanceled(true);
    }
    event.setUseBlock(Event.Result.DENY);
    event.setUseItem(Event.Result.DENY);

    event.setCancellationResult(InteractionResult.SUCCESS);

    player.sendSystemMessage(message);
  }

  @SubscribeEvent
  public static void onBreakEvent(BlockEvent.BreakEvent event) {
    ChunkLoaderManager.breakBlock((ServerLevel) event.getLevel(), event.getPos());
  }

  @SubscribeEvent
  public static void onWorldLoad(LevelEvent.Load event) {
    if (event.getLevel() instanceof ServerLevel world) {
      ChunkLoaderManager.loadForDimension(world);
      if (world == world.getServer().getLevel(Level.OVERWORLD)) {
        TeamManager.load(world);
      }
    }
  }

  @SubscribeEvent
  public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
    if (!(event.getEntity() instanceof ServerPlayer player)) return;
    ChunkLoaderManager.onPlayerConnected(player);
  }

  @SubscribeEvent
  public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
    if (!(event.getEntity() instanceof ServerPlayer player)) return;
    ChunkLoaderManager.onPlayerDisconnected(player);
  }

  @SubscribeEvent
  public static void onRegisterCommands(RegisterCommandsEvent event) {
    TeamCommands.registerCommands(event.getDispatcher());
    ChunkLoaderCommands.registerCommands(event.getDispatcher());
  }
}
