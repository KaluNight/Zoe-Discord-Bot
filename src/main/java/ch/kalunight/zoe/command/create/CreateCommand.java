package ch.kalunight.zoe.command.create;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import ch.kalunight.zoe.command.CommandUtil;
import net.dv8tion.jda.api.Permission;

public class CreateCommand extends Command {

  public static final String USAGE_NAME = "create";
  
  public CreateCommand() {
    this.name = USAGE_NAME;
    this.aliases = new String[] {"c"};
    Permission[] permissionRequired = {Permission.MANAGE_CHANNEL};
    this.userPermissions = permissionRequired;
    Command[] commandsChildren = {new CreateInfoChannelCommand(), new CreatePlayerCommand(), new CreateTeamCommand()};
    this.children = commandsChildren;
    this.helpBiConsumer = CommandUtil.getHelpMethodHasChildren(USAGE_NAME, commandsChildren);
  }

  @Override
  protected void execute(CommandEvent event) {
    event.reply("If you need help for create commands, type `>create help`");
  }
}
