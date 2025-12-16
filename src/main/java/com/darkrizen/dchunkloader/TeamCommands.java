package com.darkrizen.dchunkloader;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;

public class TeamCommands {
  public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(
    Commands.literal("dcl")
    .then(Commands.literal("teams")
          .then(Commands.literal("create")
                .then(Commands.argument("name", StringArgumentType.string())
                      .executes(context -> createTeam(context.getSource(), StringArgumentType.getString(context, "name"))))
          )
          .then(Commands.literal("delete").executes(context -> deleteTeam(context.getSource())))
          .then(Commands.literal("join")
                .then(Commands.argument("name", StringArgumentType.string())
                      .executes(context -> joinTeam(context.getSource(), StringArgumentType.getString(context, "name"))))
          )
          .then(Commands.literal("invite")
                .then(Commands.argument("player", EntityArgument.player())
                      .executes(context -> invite(context.getSource(), EntityArgument.getPlayer(context, "player"))))
          )
          .then(Commands.literal("leave").executes(context -> leaveTeam(context.getSource())))
          .then(Commands.literal("info").executes(context -> teamInfo(context.getSource())))
    )
    );
  }

  private static int createTeam(CommandSourceStack source, String teamName) {
    if (!(source.getEntity() instanceof ServerPlayer player)) {
      source.sendFailure(Component.literal("Player command only"));
      return 0;
    }
    String actualTeamName = TeamManager.getTeamForPlayer(player);
    if (actualTeamName != null) {
      source.sendFailure(Component.literal("You are already in a team do /dcl teams leave"));
      return 0;
    }
    switch (TeamManager.createTeam((ServerLevel) player.level(), teamName, player)) {
      case PLAYER_ALREADY_HAS_TEAM -> {
        source.sendFailure(Component.literal("You are already in a team do /dcl teams leave"));
        return 0;
      }
      case TEAM_ALREADY_EXISTS -> {
        source.sendSystemMessage(Component.literal("A team with name " + teamName + " already exists."));
        return 0;
      }
      case SUCCESS -> {
        source.sendSystemMessage(Component.literal("You successfully created " + teamName));
        return Command.SINGLE_SUCCESS;
      }
    }
    return 0;
  }

  private static int deleteTeam(CommandSourceStack source) {
    if (!(source.getEntity() instanceof ServerPlayer player)) {
      source.sendFailure(Component.literal("Player command only"));
      return 0;
    }
    String teamName = TeamManager.getTeamForPlayer(player);
    if (teamName == null) {
      source.sendFailure(Component.literal("You are not in a team"));
      return 0;
    }
    if (TeamManager.deleteTeam((ServerLevel) player.level(), teamName)) {
      source.sendSystemMessage(Component.literal("You successfully deleted " + teamName));
      return Command.SINGLE_SUCCESS;
    }
    return 0; // Should never happen
  }

  private static int leaveTeam(CommandSourceStack source) {
    if (!(source.getEntity() instanceof ServerPlayer player)) {
      source.sendFailure(Component.literal("Player command only"));
      return 0;
    }
    String teamName = TeamManager.getTeamForPlayer(player);
    if (teamName == null) {
      source.sendFailure(Component.literal("You are not in a team"));
      return 0;
    }
    if (TeamManager.removeTeamMember((ServerLevel) player.level(), teamName, player)) {
      Set<TeamSavedData.TeamMember> teamMembers = TeamManager.getOnlineTeamMembers(source.getLevel(), teamName);
      teamMembers.remove(new TeamSavedData.TeamMember(player));
      if (teamMembers.isEmpty()) {
        DChunkLoader.LOGGER.debug("Should unload all chunk loaders for team {}", teamName);
        ChunkLoaderManager.toggleAllChunkLoadersForTeam(source.getLevel(), teamName, false);
      }
      source.sendSystemMessage(Component.literal("You successfully leaved " + teamName));
      return Command.SINGLE_SUCCESS;
    }
    source.sendFailure(Component.literal("Error leaving team"));
    return 0; // Should never happen
  }

  private static int teamInfo(CommandSourceStack source) {
    if (!(source.getEntity() instanceof ServerPlayer player)) {
      source.sendFailure(Component.literal("Player command only"));
      return 0;
    }
    String teamName = TeamManager.getTeamForPlayer(player);
    if (teamName == null) {
      source.sendFailure(Component.literal("You are not in a team"));
      return 0;
    }
    Set<TeamSavedData.TeamMember> members = TeamManager.getTeamMembers(teamName);
    source.sendSystemMessage(Component.literal("Team " + teamName));
    source.sendSystemMessage(Component.literal("--------------------"));
    source.sendSystemMessage(Component.literal("Members"));
    int i = 1;
    for (TeamSavedData.TeamMember member : members) {
      source.sendSystemMessage(Component.literal(i + ". " + member.getDisplayName()));
      i++;
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int invite(CommandSourceStack source, ServerPlayer target) {
    if (!(source.getEntity() instanceof ServerPlayer player)) {
      source.sendFailure(Component.literal("Player command only"));
      return 0;
    }
    String teamName = TeamManager.getTeamForPlayer(player);
    if (teamName == null) {
      source.sendFailure(Component.literal("You are not in a team"));
      return 0;
    }
    if (player.equals(target)) {
      source.sendSystemMessage(Component.literal("You cannot invite yourself to a team"));
      return 0;
    }
    switch (TeamManager.inviteToTeam((ServerLevel) player.level(), teamName, target)) {
      case SUCCESS -> {
        source.sendSystemMessage(Component.literal("You successfully invited ").append(target.getDisplayName()));
        target.sendSystemMessage(Component.literal("You were invited to " + teamName + " type /dcl teams join " + teamName));
        return Command.SINGLE_SUCCESS;
      }
      case PLAYER_ALREADY_INVITED -> source.sendFailure(Component.literal("Someone has already invited ")
                                                        .append(target.getDisplayName()));
      case TEAM_NAME_DOES_NOT_EXISTS -> source.sendFailure(Component.literal("Error inviting ")
                                                           .append(target.getDisplayName()));
    }
    return 0;
  }

  private static int joinTeam(CommandSourceStack source, String teamName) {
    if (!(source.getEntity() instanceof ServerPlayer player)) {
      source.sendFailure(Component.literal("Player command only"));
      return 0;
    }
    String actualTeamName = TeamManager.getTeamForPlayer(player);
    if (actualTeamName != null) {
      source.sendFailure(Component.literal("You are already in a team do /dcl teams leave"));
      return 0;
    }
    if (TeamManager.isInvited((ServerLevel) player.level(), teamName, player)) {
      if (TeamManager.addTeamMember((ServerLevel) player.level(), teamName, player)) {
        source.sendSystemMessage(Component.literal("You successfully joined " + teamName));
        return Command.SINGLE_SUCCESS;
      } else {
        source.sendFailure(Component.literal("Error joining " + teamName));
        return 0;
      }
    }
    source.sendFailure(Component.literal("You were not invited to " + teamName));
    return 0;
  }
}
