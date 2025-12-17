package com.darkrizen.dchunkloader;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TeamManager {
  private static final String DATA_NAME = "dchunkloader_teams";
  private static final Map<String, Set<String>> pendingInvites = new HashMap<>();
  private static Map<String, Set<TeamSavedData.TeamMember>> teams = new HashMap<>();

  public static TeamCreationResult createTeam(ServerLevel world, String teamName, ServerPlayer player) {
    if (teams.containsKey(teamName))
      return TeamCreationResult.TEAM_ALREADY_EXISTS;
    if (getTeamForPlayer(player) != null)
      return TeamCreationResult.PLAYER_ALREADY_HAS_TEAM;
    Set<TeamSavedData.TeamMember> teamMembers = new HashSet<>();
    teamMembers.add(new TeamSavedData.TeamMember(player));
    teams.put(teamName, teamMembers);
    save(world);
    return TeamCreationResult.SUCCESS;
  }

  public static TeamInvitationResult inviteToTeam(ServerLevel world, String teamName, ServerPlayer player) {
    if (!teams.containsKey(teamName))
      return TeamInvitationResult.TEAM_NAME_DOES_NOT_EXISTS;
    Set<String> pendingTeamInvites = pendingInvites.computeIfAbsent(teamName, k -> new HashSet<>());
    String uuid = player.getUUID().toString();
    if (pendingTeamInvites.contains(uuid))
      return TeamInvitationResult.PLAYER_ALREADY_INVITED;
    pendingTeamInvites.add(uuid);
    return TeamInvitationResult.SUCCESS;
  }

  public static boolean isInvited(ServerLevel world, String teamName, ServerPlayer player) {
    if (!teams.containsKey(teamName)) return false;
    Set<String> pendingTeamInvites = pendingInvites.computeIfAbsent(teamName, k -> new HashSet<>());
    return pendingTeamInvites.contains(player.getUUID().toString());
  }

  public static boolean addTeamMember(ServerLevel world, String teamName, ServerPlayer player) {
    Set<TeamSavedData.TeamMember> members = teams.get(teamName);
    if (members == null) return false;

    boolean added = members.add(new TeamSavedData.TeamMember(player));
    if (added) save(world);
    return added;
  }

  public static boolean removeTeamMember(ServerLevel world, String teamName, ServerPlayer player) {
    Set<TeamSavedData.TeamMember> members = teams.get(teamName);
    if (members == null) return false; // Team does not exist

    boolean added = members.remove(new TeamSavedData.TeamMember(player));
    if (members.isEmpty()) {
      deleteTeam(world, teamName); // Delete empty teams
    }
    if (added) save(world);
    return added;
  }

  public static boolean deleteTeam(ServerLevel world, String teamName) {
    teams.remove(teamName);
    save(world);
    return true;
  }

  public static Set<String> getTeamNames() {
    return teams.keySet();
  }

  public static Set<TeamSavedData.TeamMember> getTeamMembers(String teamName) {
    return teams.getOrDefault(teamName, new HashSet<>());
  }

  public static Set<TeamSavedData.TeamMember> getOnlineTeamMembers(ServerLevel world, String teamName) {
    Set<TeamSavedData.TeamMember> teamMembers = getTeamMembers(teamName);
    Set<TeamSavedData.TeamMember> onlineTeamMembers = new HashSet<>();
    for (TeamSavedData.TeamMember teamMember : teamMembers) {
      if (Util.isPlayerOnline(world, teamMember.getUuid())) {
        onlineTeamMembers.add(teamMember);
      }
    }
    return onlineTeamMembers;
  }

  public static String getTeamForPlayer(ServerPlayer player) {
    for (Map.Entry<String, Set<TeamSavedData.TeamMember>> team : teams.entrySet()) {
      Set<TeamSavedData.TeamMember> members = team.getValue();
      for (TeamSavedData.TeamMember member : members) {
        if (player.getUUID().toString().equals(member.getUuid())) {
          return team.getKey();
        }
      }
    }
    return null;
  }

  private static void save(ServerLevel world) {
    ServerLevel overworld = world.getServer().getLevel(Level.OVERWORLD);
    if (overworld == null) {
      DChunkLoader.LOGGER.error("Error overworld does not exists");
      DChunkLoader.LOGGER.error("Existing worlds :");
      for (ServerLevel level : world.getServer().getAllLevels()) {
        DChunkLoader.LOGGER.error(level.dimension().location().toString());
      }
      return;
    }
    TeamSavedData data = overworld.getDataStorage().computeIfAbsent(
    TeamSavedData::load, TeamSavedData::new, DATA_NAME
    );
    data.setTeams(teams);
    data.setDirty();
  }

  public static void load(ServerLevel world) {
    ServerLevel overworld = world.getServer().getLevel(Level.OVERWORLD);
    if (overworld == null) {
      DChunkLoader.LOGGER.error("Error overworld does not exists");
      DChunkLoader.LOGGER.error("Existing worlds :");
      for (ServerLevel level : world.getServer().getAllLevels()) {
        DChunkLoader.LOGGER.error(level.dimension().location().toString());
      }
      return;
    }
    TeamSavedData data = overworld.getDataStorage().computeIfAbsent(
    TeamSavedData::load, TeamSavedData::new, DATA_NAME
    );
    teams = data.getTeams();
  }

  public enum TeamInvitationResult {
    SUCCESS,
    TEAM_NAME_DOES_NOT_EXISTS,
    PLAYER_ALREADY_INVITED,
  }

  public enum TeamCreationResult {
    SUCCESS,
    TEAM_ALREADY_EXISTS,
    PLAYER_ALREADY_HAS_TEAM
  }

}
