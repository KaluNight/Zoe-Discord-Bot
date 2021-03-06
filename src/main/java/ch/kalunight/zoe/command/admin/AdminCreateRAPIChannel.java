package ch.kalunight.zoe.command.admin;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.function.BiConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import ch.kalunight.zoe.Zoe;
import ch.kalunight.zoe.command.ZoeCommand;
import ch.kalunight.zoe.service.RiotApiUsageChannelRefresh;
import ch.kalunight.zoe.util.CommandUtil;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;

public class AdminCreateRAPIChannel extends ZoeCommand {

  private static final Logger logger = LoggerFactory.getLogger(AdminCreateRAPIChannel.class);

  public AdminCreateRAPIChannel() {
    this.name = "createRAPIChannel";
    this.arguments = "NameOfChannel";
    this.help = "Create a new channel where Stats about Riot API Usage is sended, onl";
    this.ownerCommand = true;
    this.hidden = true;
    this.helpBiConsumer = CommandUtil.getHelpMethodIsChildrenNoTranslation(AdminCommand.USAGE_NAME, name, arguments, help);
  }

  @Override
  protected void executeCommand(CommandEvent event) {
    CommandUtil.sendTypingInFonctionOfChannelType(event);

    if(RiotApiUsageChannelRefresh.getRapiInfoChannel() != null) {
      TextChannel textChannel = RiotApiUsageChannelRefresh.getRapiInfoChannel().getGuild()
          .getTextChannelById(RiotApiUsageChannelRefresh.getRapiInfoChannel().getId());
      if(textChannel != null) {
        event.reply("The Riot Api InfoChannel Usage is already defined, please delete it first.");
        return;
      }
    }

    if(event.getArgs().isEmpty()) {
      event.reply("Please send the name of the channel in args (E.g. : `>admin createRAPIChannel RAPIChannel`)");
      return;
    }
    TextChannel rapiTextChannel;
    try {
      TextChannel rapiChannel = event.getGuild().createTextChannel(event.getArgs()).complete();
      rapiTextChannel = event.getGuild().getTextChannelById(rapiChannel.getId());
    }catch(InsufficientPermissionException e) {
      event.reply("I don't have the right to create a channel in this guild !");
      return;
    }

    try(PrintWriter writer = new PrintWriter(Zoe.RAPI_SAVE_TXT_FILE, "UTF-8");) {
      writer.write(rapiTextChannel.getGuild().getId() + "\n" + rapiTextChannel.getId());
    } catch(FileNotFoundException | UnsupportedEncodingException e) {
      event.reply("Error when saving the channel. Please retry.");
      logger.warn("Error when saving rapi InfoChannel : ", e);
      RiotApiUsageChannelRefresh.setTextChannelId(0);
      rapiTextChannel.delete().queue();
      return;
    }
    RiotApiUsageChannelRefresh.setTextChannelId(rapiTextChannel.getIdLong());
    RiotApiUsageChannelRefresh.setGuildId(rapiTextChannel.getGuild().getIdLong());

    event.reply("Correctly created, will be refreshed in less than 2 minutes. "
        + "Please don't use this channel, all messages in it will be cleaned every 2 minutes.");
  }

  @Override
  public BiConsumer<CommandEvent, Command> getHelpBiConsumer(CommandEvent event) {
    return helpBiConsumer;
  }

}
