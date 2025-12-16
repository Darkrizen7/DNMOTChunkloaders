package com.darkrizen.dchunkloader;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ChunkLoaderSavedData extends SavedData {
  // MAP <Dimension, Map<PlayerUUID, Set<ChunkLoader>>
  private Map<String, Map<String, Set<ChunkLoader>>> chunkLoaders = new HashMap<>();

  public ChunkLoaderSavedData() {
  }

  public static ChunkLoaderSavedData load(CompoundTag tag) {
    ChunkLoaderSavedData data = new ChunkLoaderSavedData();
    Map<String, Map<String, Set<ChunkLoader>>> dimMap = new HashMap<>();

    for (String dimKey : tag.getAllKeys()) {
      CompoundTag dimTag = tag.getCompound(dimKey);
      Map<String, Set<ChunkLoader>> playerMap = new HashMap<>();

      for (String playerKey : dimTag.getAllKeys()) {
        ListTag list = dimTag.getList(playerKey, 10); // 10 = CompoundTag
        Set<ChunkLoader> chunkloaders = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
          CompoundTag chunkLoaderTag = list.getCompound(i);
          BlockPos pos = new BlockPos(chunkLoaderTag.getInt("x"), chunkLoaderTag.getInt("y"), chunkLoaderTag.getInt("z"));
          String ownerDisplayName = chunkLoaderTag.getString("ownerDisplayName");
          long lastActivated = chunkLoaderTag.getLong("lastActivated");
          ChunkLoader chunkloader = new ChunkLoader(dimKey, pos, playerKey, ownerDisplayName, lastActivated);
          chunkloaders.add(chunkloader);
        }
        if (!chunkloaders.isEmpty()) {
          playerMap.put(playerKey, chunkloaders);
        }
      }
      dimMap.put(dimKey, playerMap);
    }

    data.chunkLoaders = dimMap;
    return data;
  }

  @Override
  public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
    for (Map.Entry<String, Map<String, Set<ChunkLoader>>> dimEntry : chunkLoaders.entrySet()) {
      CompoundTag dimTag = new CompoundTag();

      for (Map.Entry<String, Set<ChunkLoader>> playerEntry : dimEntry.getValue().entrySet()) {
        ListTag list = new ListTag();
        for (ChunkLoader chunkLoader : playerEntry.getValue()) {
          BlockPos pos = chunkLoader.getPos();
          String ownerDisplayName = chunkLoader.getOwnerDisplayName();
          long lastActivated = chunkLoader.getLastActivated();
          CompoundTag chunkLoaderTag = new CompoundTag();
          chunkLoaderTag.putInt("x", pos.getX());
          chunkLoaderTag.putInt("y", pos.getY());
          chunkLoaderTag.putInt("z", pos.getZ());
          chunkLoaderTag.putString("ownerDisplayName", ownerDisplayName);
          chunkLoaderTag.putLong("lastActivated", lastActivated);
          list.add(chunkLoaderTag);
        }
        if (!list.isEmpty()) {
          dimTag.put(playerEntry.getKey(), list);
        }
      }
      if (!dimTag.isEmpty()) {
        tag.put(dimEntry.getKey(), dimTag);
      }
    }

    return tag;
  }

  public Map<String, Map<String, Set<ChunkLoader>>> getChunkLoaders() {
    return chunkLoaders;
  }

  public void setChunkLoaders(Map<String, Map<String, Set<ChunkLoader>>> chunkLoaders) {
    this.chunkLoaders = chunkLoaders;
  }

  public static class ChunkLoader {
    private final BlockPos pos;
    private final String ownerDisplayName;
    private final String ownerUUID;
    private final String dimString;
    private long lastActivated;
    private boolean activated = false;

    public ChunkLoader(String dimString, BlockPos pos, ServerPlayer owner) {
      this.dimString = dimString;
      this.pos = pos;
      this.ownerDisplayName = owner.getGameProfile().getName();
      this.ownerUUID = owner.getUUID().toString();
      this.lastActivated = System.currentTimeMillis();
    }

    public ChunkLoader(String dimString, BlockPos pos, String ownerUUID, String ownerDisplayName, long lastActivated) {
      this.dimString = dimString;
      this.pos = pos;
      this.ownerUUID = ownerUUID;
      this.ownerDisplayName = ownerDisplayName;
      this.lastActivated = lastActivated;
    }

    public boolean isActivated() {
      return activated;
    }

    public void setActivated(boolean activated) {
      this.activated = activated;
    }

    public BlockPos getPos() {
      return pos;
    }

    public String getOwnerUUID() {
      return ownerUUID;
    }

    public String getOwnerDisplayName() {
      return ownerDisplayName;
    }

    public long getLastActivated() {
      return lastActivated;
    }

    public void setLastActivated(long lastActivated) {
      this.lastActivated = lastActivated;
    }

    public String getDimString() {
      return dimString;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof ChunkLoader other)) return false;
      if (!dimString.equals(other.dimString)) return false;
      return pos.equals(other.pos);
    }

    @Override
    public int hashCode() {
      int result = dimString.hashCode();
      result = 31 * result + pos.hashCode();
      return result;
    }
  }
}
