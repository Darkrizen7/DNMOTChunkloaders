package com.darkrizen.dchunkloader;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
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
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
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

    String outputMessage = ChunkLoaderManager.clickChunkLoader((ServerLevel) world, player, pos);
    player.sendSystemMessage(Component.literal(outputMessage));
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
  }

  @SubscribeEvent
  public static void onServerStarting(ServerStartedEvent event) {
    // TODO : Remove when dev done
    MinecraftServer server = event.getServer();
    ChunkLoaderLogger.start(server);
  }

  @SubscribeEvent
  public static void onServerStopping(ServerStoppingEvent _event) {
    // TODO : Remove when dev done
    ChunkLoaderLogger.stop();
  }
}
