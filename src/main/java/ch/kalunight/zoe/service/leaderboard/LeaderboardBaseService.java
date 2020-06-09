package ch.kalunight.zoe.service.leaderboard;

import java.sql.SQLException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ch.kalunight.zoe.Zoe;
import ch.kalunight.zoe.model.dto.DTO.Leaderboard;
import ch.kalunight.zoe.model.dto.DTO.Server;
import ch.kalunight.zoe.repositories.LeaderboardRepository;
import ch.kalunight.zoe.repositories.ServerRepository;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed.Field;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.rithms.riot.api.RiotApiException;

public abstract class LeaderboardBaseService implements Runnable {

  private static Logger logger = LoggerFactory.getLogger(LeaderboardBaseService.class);

  private static int MAX_PLAYERS_IN_LEADERBOARD = 10;
  
  private long guildId;

  private long channelId;

  private long leaderboardId;

  public LeaderboardBaseService(long guildId, long channelId, long leaderboardId) {
    this.guildId = guildId;
    this.channelId = channelId;
    this.leaderboardId = leaderboardId;
  }

  @Override
  public void run() {
    try {

      Server server = ServerRepository.getServer(guildId);

      Guild guild = Zoe.getJda().getGuildById(guildId);

      TextChannel channel = guild.getTextChannelById(channelId);

      Leaderboard leaderboard = LeaderboardRepository.getLeaderboardWithId(leaderboardId);

      Message message = channel.retrieveMessageById(leaderboard.lead_message_id).complete();

      message.addReaction("U+23F3").complete();

      runLeaderboardRefresh(server, guild, channel, leaderboard, message);

      message.clearReactions("U+23F3").queue();

    }catch(ErrorResponseException e) {      
      logger.error("Error while getting discord data", e);
    }catch(SQLException e) {
      logger.error("Error while accessing to the DB", e);
    }catch(Exception e) {
      logger.error("Unexpected error when refreshing leaderboard", e);
    }
  }

  protected abstract void runLeaderboardRefresh(Server server, Guild guild, TextChannel channel,
      Leaderboard leaderboard, Message message) throws SQLException, RiotApiException;

  protected EmbedBuilder buildBaseLeaderboardList(String playerTitle, List<String> playersName, String dataName, List<String> dataList) {

    EmbedBuilder builder = new EmbedBuilder();

    StringBuilder stringListPlayer = new StringBuilder();

    for(int i = 0; i < playersName.size(); i++) {
      if(i == 0) {
        stringListPlayer.append("🥇 " + playersName.get(i));
      }else if(i == 1) {
        stringListPlayer.append("🥈 " + playersName.get(i));
      }else if (i == 2) {
        stringListPlayer.append("🥉 " + playersName.get(i));
      }else if (i < MAX_PLAYERS_IN_LEADERBOARD) {
        stringListPlayer.append((i + 1) + ". " + playersName.get(i));
      }else {
        break;
      }

      if((i + 1) > playersName.size()) {
        stringListPlayer.append("\n");
      }
    }

    Field field = new Field(playerTitle, stringListPlayer.toString(), true);
    builder.addField(field);

    StringBuilder stringData = new StringBuilder();

    for(int i = 0; i < dataList.size(); i++) {
      if(i < MAX_PLAYERS_IN_LEADERBOARD) {
        stringData.append(dataList.get(i));
      }else {
        break;
      }
      
      if((i + 1) > playersName.size()) {
        stringData.append("\n");
      }
    }

    field = new Field(dataName, stringData.toString(), true);
    builder.addField(field);

    return builder;
  }
}
