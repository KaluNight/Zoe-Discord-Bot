package ch.kalunight.zoe.command.remove;

import java.sql.SQLException;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;

import ch.kalunight.zoe.Zoe;
import ch.kalunight.zoe.command.ZoeCommand;
import ch.kalunight.zoe.command.create.CreatePlayerCommand;
import ch.kalunight.zoe.model.config.ServerConfiguration;
import ch.kalunight.zoe.model.dto.DTO;
import ch.kalunight.zoe.repositories.ConfigRepository;
import ch.kalunight.zoe.repositories.LeagueAccountRepository;
import ch.kalunight.zoe.repositories.PlayerRepository;
import ch.kalunight.zoe.translation.LanguageManager;
import ch.kalunight.zoe.util.CommandUtil;
import ch.kalunight.zoe.util.RiotApiUtil;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.rithms.riot.api.RiotApiException;
import net.rithms.riot.api.endpoints.summoner.dto.Summoner;
import net.rithms.riot.constant.Platform;

public class RemoveAccountCommand extends ZoeCommand {

  public static final Pattern PARENTHESES_PATTERN = Pattern.compile("\\(([^)]+)\\)");
  public static final String USAGE_NAME = "account";

  public RemoveAccountCommand() {
    this.name = USAGE_NAME;
    String[] aliases = {"accountToPlayers", "accountsToPlayers", "accountToPlayers", "accountToPlayer", "accounts"};
    this.aliases = aliases;
    this.help = "removeAccountHelpMessage";
    this.arguments = "@MentionOfPlayer (Region) (SummonerName)";
    this.helpBiConsumer = CommandUtil.getHelpMethodIsChildren(RemoveCommand.USAGE_NAME, name, arguments, help);
  }

  @Override
  protected void executeCommand(CommandEvent event) throws SQLException {
    event.getTextChannel().sendTyping().complete();

    DTO.Server server = getServer(event.getGuild().getIdLong());

    ServerConfiguration config = ConfigRepository.getServerConfiguration(server.serv_guildId, event.getJDA());

    if(!config.getUserSelfAdding().isOptionActivated() &&
        !event.getMember().getPermissions().contains(Permission.MANAGE_CHANNEL)) {
      event.reply(String.format(LanguageManager.getText(server.getLanguage(), "deletePlayerMissingPermission"),
          Permission.MANAGE_CHANNEL.getName()));
      return;
    }

    User user = CreatePlayerCommand.getMentionedUser(event.getMessage().getMentionedMembers());
    if(user == null) {
      event.reply(String.format(LanguageManager.getText(server.getLanguage(), "removeAccountMissingMention"),
          event.getMember().getUser().getName()));
      return;
    } else if(!user.equals(event.getAuthor()) && !event.getMember().getPermissions().contains(Permission.MANAGE_CHANNEL)) {
      event.reply(String.format(LanguageManager.getText(server.getLanguage(), "removeAccountMissingRight"),
          Permission.MANAGE_CHANNEL.getName()));
      return;
    }

    DTO.Player player = PlayerRepository.getPlayer(server.serv_guildId, user.getIdLong());
    if(player == null) {
      event.reply(LanguageManager.getText(server.getLanguage(), "removeAccountUserNotRegistered"));
      return;
    }

    List<String> listArgs = CreatePlayerCommand.getParameterInParenteses(event.getArgs());
    if(listArgs.size() != 2) {
      event.reply(LanguageManager.getText(server.getLanguage(), "removeAccountMalformed"));
      return;
    }

    String regionName = listArgs.get(0);
    String summonerName = listArgs.get(1);

    Platform region = CreatePlayerCommand.getPlatform(regionName);
    if(region == null) {
      event.reply(LanguageManager.getText(server.getLanguage(), "regionTagInvalid"));
      return;
    }

    Message loadingMessage = event.getTextChannel().sendMessage(LanguageManager.getText(server.getLanguage(), "loadingSummoner")).complete();
    DTO.LeagueAccount account;
    try {

      Summoner summoner = Zoe.getRiotApi().getSummonerByNameWithRateLimit(region, summonerName);
      
      account = LeagueAccountRepository
          .getLeagueAccountWithSummonerId(server.serv_guildId, summoner.getId(), region);
    } catch(RiotApiException e) {
      RiotApiUtil.handleRiotApi(loadingMessage, e, server.getLanguage());
      return;
    }

    if(account == null) {
      loadingMessage.editMessage(LanguageManager.getText(server.getLanguage(), "removeAccountNotLinkedToPlayer")).queue();
      return;
    }
    
    LeagueAccountRepository.deleteAccountWithId(account.leagueAccount_id);
    loadingMessage.editMessage(String.format(LanguageManager.getText(server.getLanguage(), "removeAccountDoneMessage"),
        summonerName, user.getName())).queue();
  }

  @Override
  public BiConsumer<CommandEvent, Command> getHelpBiConsumer(CommandEvent event) {
    return helpBiConsumer;
  }
}
