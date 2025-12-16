package com.darkrizen.dchunkloader;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public class TeamSavedData extends SavedData {

  private Map<String, Set<TeamMember>> teams = new HashMap<>();

  public TeamSavedData() {
  }

  public static TeamSavedData load(CompoundTag tag) {
    TeamSavedData data = new TeamSavedData();

    for (String teamName : tag.getAllKeys()) {
      ListTag list = tag.getList(teamName, 10); // 10 = CompoundTag
      Set<TeamMember> members = new HashSet<>();
      for (int i = 0; i < list.size(); i++) {
        CompoundTag memberTag = list.getCompound(i);
        String uuid = memberTag.getString("uuid");
        String displayName = memberTag.getString("displayName");
        members.add(new TeamMember(uuid, displayName));
      }
      data.teams.put(teamName, members);
    }

    return data;
  }

  @Override
  public CompoundTag save(CompoundTag tag) {
    for (Map.Entry<String, Set<TeamMember>> entry : teams.entrySet()) {
      ListTag list = new ListTag();
      for (TeamMember member : entry.getValue()) {
        CompoundTag memberTag = new CompoundTag();
        memberTag.putString("uuid", member.getUuid());
        memberTag.putString("displayName", member.getDisplayName());
        list.add(memberTag);
      }
      tag.put(entry.getKey(), list);
    }
    return tag;
  }

  public Map<String, Set<TeamMember>> getTeams() {
    return teams;
  }

  public void setTeams(Map<String, Set<TeamMember>> teams) {
    this.teams = teams;
  }

  public static class TeamMember {
    private final String uuid;
    private final String displayName;

    public TeamMember(ServerPlayer player) {
      this.uuid = player.getUUID().toString();
      this.displayName = player.getGameProfile().getName();
    }

    public TeamMember(String uuid, String displayName) {
      this.uuid = uuid;
      this.displayName = displayName;
    }

    public String getUuid() {
      return uuid;
    }

    public String getDisplayName() {
      return displayName;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof TeamMember that)) return false;
      return uuid.equals(that.uuid);
    }

    @Override
    public int hashCode() {
      return uuid.hashCode();
    }
  }
}
