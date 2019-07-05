package ch.kalunight.zoe.service;

import java.util.TimerTask;
import org.joda.time.DateTime;
import ch.kalunight.zoe.ServerData;
import ch.kalunight.zoe.Zoe;
import ch.kalunight.zoe.model.Server;
import ch.kalunight.zoe.model.ServerConfiguration;
import ch.kalunight.zoe.model.SpellingLangage;
import net.dv8tion.jda.core.OnlineStatus;
import net.dv8tion.jda.core.entities.Game;
import net.dv8tion.jda.core.entities.Guild;

public class GameChecker extends TimerTask {

  private static final int TIME_BETWEEN_EACH_SAVE_IN_MINUTES = 10;

  private static final int TIME_BETWEEN_EACH_STATUS_REFRESH_IN_HOURS = 1;
  
  private static final int TIME_BETWEEN_EACH_RAPI_CHANNEL_REFRESH_IN_MINUTES = 2;

  private static DateTime nextSaveTime = DateTime.now().plusSeconds(TIME_BETWEEN_EACH_SAVE_IN_MINUTES);

  private static DateTime nextStatusRefresh = DateTime.now();
  
  private static DateTime nextRAPIChannelRefresh = DateTime.now().plusMinutes(TIME_BETWEEN_EACH_RAPI_CHANNEL_REFRESH_IN_MINUTES);

  @Override
  public void run() {
    for(Guild guild : Zoe.getJda().getGuilds()) {
      if(guild.getOwnerId().equals(Zoe.getJda().getSelfUser().getId())) {
        continue;
      }
      Server server = ServerData.getServers().get(guild.getId());

      if(server == null) {
        server = new Server(guild, SpellingLangage.EN, new ServerConfiguration());
        ServerData.getServers().put(guild.getId(), server);
      }

      if(ServerData.getServersIsInTreatment().get(guild.getId()) == null) {
        ServerData.getServersIsInTreatment().put(guild.getId(), false);
      }
      
      if(ServerData.getServersAskedTreatment().get(guild.getId()) == null) {
        ServerData.getServersAskedTreatment().put(guild.getId(), false);
      }
      
      if(ServerData.getServersAskedTreatment().get(server.getGuild().getId()) && !ServerData.getServersIsInTreatment().get(server.getGuild().getId())) {
        ServerData.getServersAskedTreatment().put(server.getGuild().getId(), false);
        ServerData.getServersIsInTreatment().put(guild.getId(), true);
        server.setLastRefresh(DateTime.now());
        Runnable task = new InfoPanelRefresher(server);
        ServerData.getTaskExecutor().execute(task);
      }

      if(server.isNeedToBeRefreshed() && server.getInfoChannel() != null && !ServerData.getServersIsInTreatment().get(guild.getId())) {

        Runnable task = new InfoPanelRefresher(server);
        ServerData.getServersIsInTreatment().put(guild.getId(), true);
        ServerData.getTaskExecutor().execute(task);
      }
    }
    
    if(nextRAPIChannelRefresh.isBeforeNow() && RiotApiUsageChannelRefresh.getRapiInfoChannel() != null) {
      ServerData.getTaskExecutor().execute(new RiotApiUsageChannelRefresh());
      
      setNextRAPIChannelRefresh(DateTime.now().plusMinutes(TIME_BETWEEN_EACH_RAPI_CHANNEL_REFRESH_IN_MINUTES));
    }

    if(nextSaveTime.isBeforeNow()) {
      // Save data
      ServerData.getTaskExecutor().execute(new DataSaver());

      if(Zoe.getBotListApi() != null) {
        // Discord bot list status
        Zoe.getBotListApi().setStats(Zoe.getJda().getGuilds().size());
      }

      setNextSaveTime(DateTime.now().plusMinutes(TIME_BETWEEN_EACH_SAVE_IN_MINUTES));
    }

    if(nextStatusRefresh.isBeforeNow()) {
      // Discord status
      Zoe.getJda().getPresence().setStatus(OnlineStatus.ONLINE);
      Zoe.getJda().getPresence().setGame(Game.playing("type \">help\""));

      setNextStatusRefresh(nextStatusRefresh.plusHours(TIME_BETWEEN_EACH_STATUS_REFRESH_IN_HOURS));
    }
  }

  public static void setNextStatusRefresh(DateTime nextStatusRefresh) {
    GameChecker.nextStatusRefresh = nextStatusRefresh;
  }
  
  public static void setNextSaveTime(DateTime nextRefreshDate) {
    GameChecker.nextSaveTime = nextRefreshDate;
  }

  private static void setNextRAPIChannelRefresh(DateTime nextRAPIChannelRefresh) {
    GameChecker.nextRAPIChannelRefresh = nextRAPIChannelRefresh;
  }
}
