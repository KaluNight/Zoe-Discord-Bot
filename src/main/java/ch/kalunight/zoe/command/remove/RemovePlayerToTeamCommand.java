package ch.kalunight.zoe.command.remove;

import java.util.function.BiConsumer;
import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import ch.kalunight.zoe.ServerData;
import ch.kalunight.zoe.command.ZoeCommand;
import ch.kalunight.zoe.model.Server;
import ch.kalunight.zoe.model.player_data.Player;
import ch.kalunight.zoe.model.player_data.Team;
import ch.kalunight.zoe.util.CommandUtil;
import net.dv8tion.jda.api.Permission;

public class RemovePlayerToTeamCommand extends ZoeCommand {

  public static final String USAGE_NAME = "playerToTeam";

  public RemovePlayerToTeamCommand() {
    this.name = USAGE_NAME;
    this.help = "Delete the given player from his team. Manage Channel permission needed.";
    this.arguments = "@MentionOfPlayer";
    Permission[] permissionRequired = {Permission.MANAGE_CHANNEL};
    this.userPermissions = permissionRequired;
    this.helpBiConsumer = getHelpMethod();
  }

  @Override
  protected void executeCommand(CommandEvent event) {
    event.getTextChannel().sendTyping().complete();
    Server server = ServerData.getServers().get(event.getGuild().getId());

    if(event.getMessage().getMentionedMembers().size() != 1) {
      event.reply("Please mentions one people !");
      return;
    }

    Player player = server.getPlayerByDiscordId(event.getMessage().getMentionedMembers().get(0).getUser().getId());

    if(player == null) {
      event.reply("The mentioned people is not a registed player !");
      return;
    }

    Team teamWhereRemove = server.getTeamByPlayer(player);
    
    if(teamWhereRemove == null) {
      event.reply("This player is not in a team");
      return;
    }

    teamWhereRemove.getPlayers().remove(player);
    event.reply(player.getDiscordUser().getName() + " has been deleted from the team " + teamWhereRemove.getName() + " !");
  }

  private BiConsumer<CommandEvent, Command> getHelpMethod() {
    return new BiConsumer<CommandEvent, Command>() {
      @Override
      public void accept(CommandEvent event, Command command) {
        CommandUtil.sendTypingInFonctionOfChannelType(event);
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Remove playerToTeam command :\n");
        stringBuilder.append("--> `>remove " + name + " " + arguments + "` : " + help);

        event.reply(stringBuilder.toString());
      }
    };
  }

}
