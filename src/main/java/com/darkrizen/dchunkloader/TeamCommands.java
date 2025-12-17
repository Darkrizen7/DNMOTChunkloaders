package com.darkrizen.dchunkloader;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
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
          .then(Commands.literal("info")
                .executes(context -> teamInfo(context.getSource()))
                .then(Commands.argument("name", StringArgumentType.string())
                      .requires(src -> src.hasPermission(2))
                      .executes(context -> teamInfo(context.getSource(), StringArgumentType.getString(context, "name")))))

          .then(Commands.literal("list")
                .requires(src -> src.hasPermission(2))
                .executes(context -> listTeams(context.getSource())))

    )
    );
  }

  private static int createTeam(CommandSourceStack source, String teamName) {
    if (!(source.getEntity() instanceof ServerPlayer player)) {
      source.sendFailure(Component.literal("This command can only be used by players."));
      return 0;
    }

    String actualTeamName = TeamManager.getTeamForPlayer(player);
    if (actualTeamName != null) {
      MutableComponent leaveCommand = Component.literal("/dcl teams leave")
      .withStyle(style -> style
      .withColor(ChatFormatting.YELLOW)
      .withUnderlined(true)
      .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/dcl teams leave"))
      .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to leave your current team"))));

      source.sendFailure(Component.literal("You are already in a team. Please use ")
                         .append(leaveCommand)
                         .append(Component.literal(" first.").withStyle(ChatFormatting.RED)));
      return 0;
    }

    switch (TeamManager.createTeam((ServerLevel) player.level(), teamName, player)) {
      case PLAYER_ALREADY_HAS_TEAM -> {
        source.sendFailure(Component.literal("You are already in a team."));
        return 0;
      }
      case TEAM_ALREADY_EXISTS -> {
        source.sendFailure(Component.literal("A team named ")
                           .append(Component.literal(teamName).withStyle(ChatFormatting.WHITE))
                           .append(" already exists."));
        return 0;
      }
      case SUCCESS -> {
        source.sendSystemMessage(Component.literal("Success! ")
                                 .withStyle(ChatFormatting.GREEN)
                                 .append(Component.literal("You created team ")
                                         .withStyle(ChatFormatting.WHITE))
                                 .append(Component.literal(teamName)
                                         .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
        return Command.SINGLE_SUCCESS;
      }
    }
    return 0;
  }

  private static int deleteTeam(CommandSourceStack source) {
    if (!(source.getEntity() instanceof ServerPlayer player)) {
      source.sendFailure(Component.literal("This command can only be used by players."));
      return 0;
    }

    String teamName = TeamManager.getTeamForPlayer(player);
    if (teamName == null) {
      source.sendFailure(Component.literal("You are not currently in a team."));
      return 0;
    }

    if (TeamManager.deleteTeam((ServerLevel) player.level(), teamName)) {
      source.sendSystemMessage(Component.literal("Success! ")
                               .withStyle(ChatFormatting.GREEN)
                               .append(Component.literal("The team ")
                                       .withStyle(ChatFormatting.WHITE))
                               .append(Component.literal(teamName)
                                       .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                               .append(Component.literal(" has been deleted.")
                                       .withStyle(ChatFormatting.WHITE)));

      return Command.SINGLE_SUCCESS;
    }

    source.sendFailure(Component.literal("An internal error occurred while deleting ")
                       .append(Component.literal(teamName).withStyle(ChatFormatting.WHITE)));
    return 0;
  }

  private static int leaveTeam(CommandSourceStack source) {
    if (!(source.getEntity() instanceof ServerPlayer player)) {
      source.sendFailure(Component.literal("This command can only be used by players."));
      return 0;
    }

    String teamName = TeamManager.getTeamForPlayer(player);
    if (teamName == null) {
      source.sendFailure(Component.literal("You are not currently in a team."));
      return 0;
    }

    if (TeamManager.removeTeamMember((ServerLevel) player.level(), teamName, player)) {
      Set<TeamSavedData.TeamMember> teamMembers = TeamManager.getOnlineTeamMembers(source.getLevel(), teamName);
      teamMembers.remove(new TeamSavedData.TeamMember(player));

      if (teamMembers.isEmpty()) {
        DChunkLoader.LOGGER.debug("Should unload all chunk loaders for team {}", teamName);
        ChunkLoaderManager.toggleAllChunkLoadersForTeam(source.getLevel(), teamName, false);
      }

      source.sendSystemMessage(Component.literal("Success! ")
                               .withStyle(ChatFormatting.GREEN)
                               .append(Component.literal("You have left team ")
                                       .withStyle(ChatFormatting.WHITE))
                               .append(Component.literal(teamName)
                                       .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));

      return Command.SINGLE_SUCCESS;
    }

    source.sendFailure(Component.literal("An internal error occurred while leaving ")
                       .append(Component.literal(teamName).withStyle(ChatFormatting.WHITE)));
    return 0;
  }

  private static int teamInfo(CommandSourceStack source) {
    if (!(source.getEntity() instanceof ServerPlayer player)) {
      source.sendFailure(Component.literal("This command can only be used by players."));
      return 0;
    }
    String teamName = TeamManager.getTeamForPlayer(player);
    return teamInfo(source, teamName);
  }

  private static int teamInfo(CommandSourceStack source, String teamName) {
    if (teamName == null) {
      source.sendFailure(Component.literal("You are not currently in a team."));
      return 0;
    }
    Set<TeamSavedData.TeamMember> teamMembers = TeamManager.getTeamMembers(teamName);
    Set<TeamSavedData.TeamMember> onlineMembers = TeamManager.getOnlineTeamMembers(source.getLevel(), teamName);

    source.sendSystemMessage(Component.literal("\n--- TEAM INFO ---")
                             .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
    source.sendSystemMessage(Component.literal("Team: ").withStyle(ChatFormatting.GRAY)
                             .append(Component.literal(teamName)
                                     .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)));

    source.sendSystemMessage(Component.literal("Status: ").withStyle(ChatFormatting.GRAY)
                             .append(Component.literal(onlineMembers.size() + "/" + teamMembers.size() + " players online")
                                     .withStyle(onlineMembers.isEmpty() ? ChatFormatting.RED : ChatFormatting.GREEN)));

    source.sendSystemMessage(Component.literal("--------------------").withStyle(ChatFormatting.DARK_GRAY));
    source.sendSystemMessage(Component.literal("Members:").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));

    for (TeamSavedData.TeamMember member : teamMembers) {
      boolean isOnline = Util.isPlayerOnline(source.getLevel(), member.getUuid());

      MutableComponent memberLine = Component.literal("  └─ ").withStyle(ChatFormatting.DARK_GRAY)
      .append(Component.literal(member.getDisplayName())
              .withStyle(isOnline ? ChatFormatting.GREEN : ChatFormatting.DARK_RED));

      if (isOnline) {
        memberLine.append(Component.literal(" ●").withStyle(ChatFormatting.GREEN));
      }

      source.sendSystemMessage(memberLine);
    }

    source.sendSystemMessage(Component.literal("--------------------").withStyle(ChatFormatting.DARK_GRAY));

    return Command.SINGLE_SUCCESS;
  }

  private static int invite(CommandSourceStack source, ServerPlayer target) {
    if (!(source.getEntity() instanceof ServerPlayer player)) {
      source.sendFailure(Component.literal("This command can only be used by players."));
      return 0;
    }

    String teamName = TeamManager.getTeamForPlayer(player);
    if (teamName == null) {
      source.sendFailure(Component.literal("You are not currently in a team."));
      return 0;
    }

    if (player.equals(target)) {
      source.sendFailure(Component.literal("You cannot invite yourself to a team."));
      return 0;
    }

    switch (TeamManager.inviteToTeam((ServerLevel) player.level(), teamName, target)) {
      case SUCCESS -> {
        source.sendSystemMessage(Component.literal("Invitation sent to ")
                                 .withStyle(ChatFormatting.GREEN)
                                 .append(target.getDisplayName().copy().withStyle(ChatFormatting.WHITE)));

        MutableComponent joinCommand = Component.literal("/dcl teams join " + teamName)
        .withStyle(style -> style
        .withColor(ChatFormatting.AQUA)
        .withUnderlined(true)
        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/dcl teams join " + teamName))
        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to join " + teamName))));

        target.sendSystemMessage(Component.literal("\n--- TEAM INVITATION ---")
                                 .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        target.sendSystemMessage(Component.literal("You have been invited to join ")
                                 .append(Component.literal(teamName).withStyle(ChatFormatting.YELLOW))
                                 .append("\nClick here to join: ")
                                 .append(joinCommand));

        return Command.SINGLE_SUCCESS;
      }
      case PLAYER_ALREADY_INVITED -> source.sendFailure(Component.literal("A pending invitation already exists for ")
                                                        .append(target.getDisplayName()));
      case TEAM_NAME_DOES_NOT_EXISTS -> source.sendFailure(Component.literal("The team ")
                                                           .append(Component.literal(teamName)
                                                                   .withStyle(ChatFormatting.WHITE))
                                                           .append(" no longer exists."));
    }
    return 0;
  }

  private static int joinTeam(CommandSourceStack source, String teamName) {
    if (!(source.getEntity() instanceof ServerPlayer player)) {
      source.sendFailure(Component.literal("This command can only be used by players."));
      return 0;
    }

    String actualTeamName = TeamManager.getTeamForPlayer(player);
    if (actualTeamName != null) {
      MutableComponent commandComponent = Component.literal("/dcl teams leave")
      .withStyle(style -> style
      .withColor(ChatFormatting.YELLOW)
      .withUnderlined(true)
      .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/dcl teams leave"))
      .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to leave your current team"))));

      source.sendFailure(Component.literal("You are already in a team. Please use ")
                         .append(commandComponent)
                         .append(Component.literal(" first.").withStyle(ChatFormatting.RED)));
      return 0;
    }

    if (TeamManager.isInvited((ServerLevel) player.level(), teamName, player)) {
      if (TeamManager.addTeamMember((ServerLevel) player.level(), teamName, player)) {
        source.sendSystemMessage(Component.literal("Success! ")
                                 .withStyle(ChatFormatting.GREEN)
                                 .append(Component.literal("You joined team ")
                                         .withStyle(ChatFormatting.WHITE))
                                 .append(Component.literal(teamName)
                                         .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
        return Command.SINGLE_SUCCESS;
      } else {
        source.sendFailure(Component.literal("An internal error occurred while joining ")
                           .append(Component.literal(teamName).withStyle(ChatFormatting.WHITE)));
        return 0;
      }
    }

    source.sendFailure(Component.literal("You have not been invited to join ")
                       .append(Component.literal(teamName).withStyle(ChatFormatting.WHITE)));
    return 0;
  }

  private static int listTeams(CommandSourceStack source) {
    if (!(source.getEntity() instanceof ServerPlayer player)) {
      source.sendFailure(Component.literal("This command can only be used by players."));
      return 0;
    }

    source.sendSystemMessage(Component.literal("\n--- TEAMS ---")
                             .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
    source.sendSystemMessage(Component.literal("====================").withStyle(ChatFormatting.GRAY));

    int teamCount = 1;
    for (String teamName : TeamManager.getTeamNames()) {
      Set<TeamSavedData.TeamMember> teamMembers = TeamManager.getTeamMembers(teamName);
      Set<TeamSavedData.TeamMember> onlineMembers = TeamManager.getOnlineTeamMembers(source.getLevel(), teamName);
      int chunkLoadersAmount = ChunkLoaderManager.getTeamChunkLoaders(teamName).size();

      ChatFormatting statusColor = onlineMembers.isEmpty() ? ChatFormatting.RED : ChatFormatting.GREEN;

      MutableComponent teamLine = Component.literal(teamCount + ". " + teamName)
      .withStyle(style -> style
      .withColor(ChatFormatting.YELLOW)
      .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/dcl teams info " + teamName))
      .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to view details for " + teamName))))
      .append(Component.literal(" [").withStyle(ChatFormatting.GRAY))
      .append(Component.literal(onlineMembers.size() + "/" + teamMembers.size())
              .withStyle(statusColor))
      .append(Component.literal(" online]").withStyle(ChatFormatting.GRAY))
      .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
      .append(Component.literal(chunkLoadersAmount + " Loaders").withStyle(ChatFormatting.AQUA));

      source.sendSystemMessage(teamLine);
      teamCount++;
    }

    source.sendSystemMessage(Component.literal("====================").withStyle(ChatFormatting.GRAY));
    return Command.SINGLE_SUCCESS;
  }
}
