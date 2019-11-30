package ch.kalunight.zoe.command;

import com.jagrosh.jdautilities.command.CommandEvent;
import ch.kalunight.zoe.ServerData;
import ch.kalunight.zoe.model.Server;
import ch.kalunight.zoe.translation.LanguageManager;
import ch.kalunight.zoe.util.CommandUtil;

public class SetupCommand extends ZoeCommand {

  public SetupCommand() {
    this.name = "setup";
    this.help = "setupHelpMessage";
    this.ownerCommand = false;
    this.guildOnly = false;
    this.helpBiConsumer = CommandUtil.getHelpMethod(name, help);
  }

  @Override
  protected void executeCommand(CommandEvent event) {
    CommandUtil.sendTypingInFonctionOfChannelType(event);
    
    Server server = ServerData.getServers().get(event.getGuild().getId());
    
    event.reply(LanguageManager.getText(server.getLangage(), "setupMessage"));
  }
}
