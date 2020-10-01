package ch.kalunight.zoe.service.infochannel;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ch.kalunight.zoe.ServerData;
import ch.kalunight.zoe.Zoe;
import ch.kalunight.zoe.model.GameQueueConfigId;
import ch.kalunight.zoe.model.config.ServerConfiguration;
import ch.kalunight.zoe.model.dto.DTO;
import ch.kalunight.zoe.model.dto.DTO.LastRank;
import ch.kalunight.zoe.model.dto.DTO.LeagueAccount;
import ch.kalunight.zoe.model.dto.DTO.Player;
import ch.kalunight.zoe.model.dto.DTO.RankHistoryChannel;
import ch.kalunight.zoe.model.dto.DTO.Server;
import ch.kalunight.zoe.model.player_data.FullTier;
import ch.kalunight.zoe.repositories.CurrentGameInfoRepository;
import ch.kalunight.zoe.repositories.LastRankRepository;
import ch.kalunight.zoe.repositories.LeagueAccountRepository;
import ch.kalunight.zoe.repositories.PlayerRepository;
import ch.kalunight.zoe.repositories.TeamRepository;
import ch.kalunight.zoe.riotapi.CachedRiotApi;
import ch.kalunight.zoe.service.rankchannel.RankedChannelLoLRefresher;
import ch.kalunight.zoe.translation.LanguageManager;
import ch.kalunight.zoe.util.FullTierUtil;
import ch.kalunight.zoe.util.InfoPanelRefresherUtil;
import ch.kalunight.zoe.util.LastRankUtil;
import ch.kalunight.zoe.util.Ressources;
import ch.kalunight.zoe.util.TreatedPlayer;
import net.rithms.riot.api.RiotApiException;
import net.rithms.riot.api.endpoints.league.dto.LeagueEntry;
import net.rithms.riot.api.endpoints.spectator.dto.CurrentGameInfo;
import net.rithms.riot.api.endpoints.tft_league.dto.TFTLeagueEntry;

public class TreatPlayerWorker implements Runnable {

  protected static final List<TreatPlayerWorker> playersInWork = Collections.synchronizedList(new ArrayList<>());

  protected static final Logger logger = LoggerFactory.getLogger(TreatPlayerWorker.class);

  protected static final CachedRiotApi riotApi = Zoe.getRiotApi();

  private Player player;

  private DTO.Team team;
  
  private Server server;

  private ServerConfiguration serverConfig;

  private StringBuilder infochannelMessage;

  private RankHistoryChannel rankChannel;

  private List<FullTier> soloqRank = new ArrayList<>();
  
  private Map<DTO.CurrentGameInfo, LeagueAccount> gamesToDelete = Collections.synchronizedMap(new HashMap<>());
  
  private Map<CurrentGameInfo, LeagueAccount> gamesToCreate = Collections.synchronizedMap(new HashMap<>());
  
  private TreatedPlayer treatedPlayer = null;
  
  private class LastRankQueue {
    public LeagueEntry leagueEntry;
    public LeagueEntry leagueEntrySecond;
    public LocalDateTime lastRefresh;
    public GameQueueConfigId queue;

    public LastRankQueue(LeagueEntry leagueEntry, LeagueEntry leagueEntrySecond, LocalDateTime lastRefresh, GameQueueConfigId gameQueueConfigId) {
      this.leagueEntry = leagueEntry;
      this.leagueEntrySecond = leagueEntrySecond;
      this.lastRefresh = lastRefresh;
      this.queue = gameQueueConfigId;
    }
  }

  public TreatPlayerWorker(Server server, Player player, ServerConfiguration configuration) {
    this.player = player;
    this.server = server;
    this.infochannelMessage = new StringBuilder();
    this.serverConfig = configuration;
    playersInWork.add(this);
  }


  @Override
  public void run() {
    try {
      Map<LeagueAccount, CurrentGameInfo> accountsInGame = Collections.synchronizedMap(new HashMap<>());
      List<LeagueAccount> accountNotInGame = new ArrayList<>();
      
      refreshPlayer(accountsInGame, accountNotInGame);
      generateText(accountsInGame, accountNotInGame); 
      createOutputObject();
    }catch(SQLException e) {
      logger.error("Unexpected SQLException when threathing text", e);
    }catch(Exception e) {
      logger.error("Unexpected exception when threathing text", e);
    }finally {
      playersInWork.remove(this);
    }
  }


  private void createOutputObject() {
    treatedPlayer = new TreatedPlayer(player, team, infochannelMessage.toString(), soloqRank, gamesToDelete, gamesToCreate);
  }

  private void refreshPlayer(Map<LeagueAccount, CurrentGameInfo> accountsInGame, List<LeagueAccount> accountNotInGame) throws SQLException {
    team = TeamRepository.getTeamByPlayerAndGuild(server.serv_guildId, player.player_discordId);
    
    List<LeagueAccount> leaguesAccount = LeagueAccountRepository.getLeaguesAccountsWithPlayerID(server.serv_guildId, player.player_id);

    for(LeagueAccount leagueAccount : leaguesAccount) {
      LastRank lastRank = getLastRank(leagueAccount);

      refreshLoL(leagueAccount, lastRank, accountsInGame, accountNotInGame);
      refreshTFT(leagueAccount, lastRank);
    }
  }


  private LastRank getLastRank(LeagueAccount leagueAccount) throws SQLException {
    LastRank lastRank = LastRankRepository.getLastRankWithLeagueAccountId(leagueAccount.leagueAccount_id);
    if(lastRank == null) {
      LastRankRepository.createLastRank(leagueAccount.leagueAccount_id);
      lastRank = LastRankRepository.getLastRankWithLeagueAccountId(leagueAccount.leagueAccount_id);
    }
    return lastRank;
  }


  private void refreshTFT(LeagueAccount leagueAccount, LastRank lastRank) throws SQLException {
    Set<TFTLeagueEntry> tftLeagueEntries = Zoe.getRiotApi().
        getTFTLeagueEntriesWithRateLimit(leagueAccount.leagueAccount_server, leagueAccount.leagueAccount_tftSummonerId);

    LastRankUtil.updateTFTLastRank(leagueAccount, lastRank, tftLeagueEntries);
  }

  private void refreshLoL(LeagueAccount leagueAccount, LastRank lastRank,
      Map<LeagueAccount, CurrentGameInfo> accountsInGame, List<LeagueAccount> accountNotInGame) throws SQLException {
    DTO.CurrentGameInfo currentGameDb = CurrentGameInfoRepository.getCurrentGameWithLeagueAccountID(leagueAccount.leagueAccount_id);

    CurrentGameInfo currentGame;
    try {
      currentGame = Zoe.getRiotApi().getActiveGameBySummoner(
          leagueAccount.leagueAccount_server, leagueAccount.leagueAccount_summonerId);
      accountsInGame.put(leagueAccount, currentGame);
    } catch(RiotApiException e) {
      accountNotInGame.add(leagueAccount);
      if(e.getErrorCode() == RiotApiException.DATA_NOT_FOUND) {
        currentGame = null;
      }else {
        return;
      }
    }

    if(currentGameDb == null && currentGame != null) {
      manageNewGame(leagueAccount, currentGame);
    }else if(currentGameDb != null && currentGame != null) {
      if(currentGame.getGameId() != currentGameDb.currentgame_currentgame.getGameId()) {
        manageChangeGame(leagueAccount, currentGameDb, currentGame, lastRank);
      }
    }else if(currentGameDb != null) {
      manageDeleteGame(leagueAccount, currentGameDb, lastRank);
    }
    
    if(lastRank.lastRank_soloq != null) {
      soloqRank.add(new FullTier(lastRank.lastRank_soloq));
    }
  }


  private void manageDeleteGame(LeagueAccount leagueAccount, DTO.CurrentGameInfo currentGameDb, LastRank lastRank) throws SQLException {

    gamesToDelete.put(currentGameDb, leagueAccount);

    updateLoLLastRankIfGivenGameIsARanked(leagueAccount, currentGameDb, lastRank);
    searchForRefreshRankChannel(currentGameDb, leagueAccount, lastRank);
  }


  private void manageChangeGame(LeagueAccount leagueAccount, DTO.CurrentGameInfo currentGameDb, CurrentGameInfo currentGame,
      LastRank lastRank) throws SQLException {

    gamesToDelete.put(currentGameDb, leagueAccount);
    gamesToCreate.put(currentGame, leagueAccount);

    if(updateLoLLastRankIfGivenGameIsARanked(leagueAccount, currentGameDb, lastRank)) {
      searchForRefreshRankChannel(currentGameDb, leagueAccount, lastRank);
    }
  }

  /**
   * @return true is the update has been done correctly, false otherwise.
   */
  private boolean updateLoLLastRankIfGivenGameIsARanked(LeagueAccount leagueAccount, DTO.CurrentGameInfo currentGameDone, LastRank lastRank) throws SQLException {

    CurrentGameInfo currentGameData = currentGameDone.currentgame_currentgame;

    if(currentGameData.getGameQueueConfigId() == GameQueueConfigId.SOLOQ.getId() 
        || currentGameData.getGameQueueConfigId() == GameQueueConfigId.FLEX.getId()) {

      Set<LeagueEntry> leagueEntries;
      try {
        leagueEntries = Zoe.getRiotApi().
            getLeagueEntriesBySummonerIdWithRateLimit(leagueAccount.leagueAccount_server, leagueAccount.leagueAccount_summonerId);
      } catch(RiotApiException e) {
        logger.info("Error while getting leagueEntries in updateLoLLastRank.");
        return false;
      }
      
      return LastRankUtil.updateLoLLastRank(leagueAccount, lastRank, leagueEntries);
    }
    return false;
  }


  private void manageNewGame(LeagueAccount leagueAccount, CurrentGameInfo currentGame) {
    gamesToCreate.put(currentGame, leagueAccount);
  }

  private void searchForRefreshRankChannel(DTO.CurrentGameInfo currentGameDb, LeagueAccount leagueAccount, LastRank lastRank) throws SQLException {

    if(currentGameDb.currentgame_currentgame.getParticipantByParticipantId(leagueAccount.leagueAccount_summonerId) != null) {
      Player playerToUpdate = PlayerRepository.getPlayerByLeagueAccountAndGuild(server.serv_guildId,
          leagueAccount.leagueAccount_summonerId, leagueAccount.leagueAccount_server);
      updateRankChannelMessage(playerToUpdate, leagueAccount, currentGameDb, lastRank);
    }
  }

  private void updateRankChannelMessage(DTO.Player player, DTO.LeagueAccount leagueAccount, DTO.CurrentGameInfo currentGameDb,
      LastRank lastRank) {
    CurrentGameInfo gameOfTheChange = currentGameDb.currentgame_currentgame;

    if(gameOfTheChange.getGameQueueConfigId() == GameQueueConfigId.SOLOQ.getId()) {

      RankedChannelLoLRefresher rankedRefresher = 
          new RankedChannelLoLRefresher(rankChannel, lastRank.lastRank_soloqSecond, lastRank.lastRank_soloq,
              gameOfTheChange, player, leagueAccount, server);
      ServerData.getRankedMessageGenerator().execute(rankedRefresher);

    }else if(gameOfTheChange.getGameQueueConfigId() == GameQueueConfigId.FLEX.getId()) {

      RankedChannelLoLRefresher rankedRefresher = 
          new RankedChannelLoLRefresher(rankChannel, lastRank.lastRank_flexSecond, lastRank.lastRank_flex,
              gameOfTheChange, player, leagueAccount, server);
      ServerData.getRankedMessageGenerator().execute(rankedRefresher);
    }

  }


  private void generateText(Map<DTO.LeagueAccount, CurrentGameInfo> accountsWithGame, List<DTO.LeagueAccount> accountNotInGame) throws SQLException {

    
    if(accountsWithGame.isEmpty()) {

      if(serverConfig.getInfopanelRankedOption().isOptionActivated()) {

        if(accountNotInGame.size() == 1) {

          LeagueAccount leagueAccount = accountNotInGame.get(0);

          getTextInformationPanelRankOption(infochannelMessage, player, leagueAccount, false);
        }else if (accountNotInGame.size() > 1){

          infochannelMessage.append(String.format(LanguageManager.getText(server.serv_language, "infoPanelRankedTitleMultipleAccount"), player.getUser().getAsMention()) + "\n");

          for(DTO.LeagueAccount leagueAccount : accountNotInGame) {

            getTextInformationPanelRankOption(infochannelMessage, player, leagueAccount, true);
          }
        }

      } else {
        notInGameWithoutRankInfo(infochannelMessage, player);
      }
    }else if (accountsWithGame.size() == 1) {
      Entry<DTO.LeagueAccount, CurrentGameInfo> entry = accountsWithGame.entrySet().iterator().next();
      infochannelMessage.append(player.getUser().getAsMention() + " : " 
          + InfoPanelRefresherUtil.getCurrentGameInfoStringForOneAccount(entry.getKey(), entry.getValue(), server.serv_language) + "\n");
    }else {
      infochannelMessage.append(player.getUser().getAsMention() + " : " 
          + LanguageManager.getText(server.serv_language, "informationPanelMultipleAccountInGame") + "\n"
          + InfoPanelRefresherUtil.getCurrentGameInfoStringForMultipleAccounts(accountsWithGame, server.serv_language));
    }
  }

  private void getTextInformationPanelRankOption(final StringBuilder stringMessage, DTO.Player player,
      DTO.LeagueAccount leagueAccount, boolean mutlipleAccount) throws SQLException {
    LastRank lastRank = LastRankRepository.getLastRankWithLeagueAccountId(leagueAccount.leagueAccount_id);

    LeagueEntry soloq = lastRank.lastRank_soloq;
    LeagueEntry flex = lastRank.lastRank_flex;
    TFTLeagueEntry tft = lastRank.lastRank_tft;

    if(soloq == null && flex == null && tft == null) {
      if(mutlipleAccount) {
        notInGameUnranked(stringMessage, leagueAccount);
      }else {
        notInGameWithoutRankInfo(stringMessage, player);
      }
      return;
    }

    String accountString;
    String baseText;
    if(mutlipleAccount) {
      baseText = "infoPanelRankedTextMultipleAccount";
      accountString = leagueAccount.leagueAccount_name;
    }else {
      baseText = "infoPanelRankedTextOneAccount";
      accountString = player.getUser().getAsMention();
    }

    List<LastRankQueue> lastRanksByQueue = new ArrayList<>();

    if(lastRank.lastRank_soloqLastRefresh != null) {
      lastRanksByQueue.add(new LastRankQueue(lastRank.lastRank_soloq, lastRank.lastRank_soloqSecond,
          lastRank.lastRank_soloqLastRefresh, GameQueueConfigId.SOLOQ));
    }

    if(lastRank.lastRank_flexLastRefresh != null) {
      lastRanksByQueue.add(new LastRankQueue(lastRank.lastRank_flex, lastRank.lastRank_flexSecond,
          lastRank.lastRank_flexLastRefresh, GameQueueConfigId.FLEX));
    }

    if(lastRank.lastRank_tftLastRefresh != null) {
      lastRanksByQueue.add(new LastRankQueue(lastRank.lastRank_tft, lastRank.lastRank_tftSecond,
          lastRank.lastRank_tftLastRefresh, GameQueueConfigId.RANKED_TFT));
    }


    LastRankQueue rankQueueToShow = null;
    for(LastRankQueue lastRankToCheck : lastRanksByQueue) {
      if(rankQueueToShow == null || rankQueueToShow.lastRefresh.isAfter(lastRankToCheck.lastRefresh)) {
        rankQueueToShow = lastRankToCheck;
      }
    }


    if(rankQueueToShow != null) {

      FullTier lastRankFullTier = new FullTier(rankQueueToShow.leagueEntry);

      stringMessage.append(getDetailledRank(rankQueueToShow.leagueEntry, rankQueueToShow.leagueEntrySecond, lastRankFullTier, accountString, baseText, rankQueueToShow.queue));

    } else {
      if(mutlipleAccount) {
        notInGameUnranked(stringMessage, leagueAccount);
      }else {
        notInGameWithoutRankInfo(stringMessage, player);
      }
    }
  }

  private String getDetailledRank(LeagueEntry leagueEntryFirst, LeagueEntry leagueEntrySecond, FullTier tier, String accountString, String baseText, GameQueueConfigId rankedQueue) {
    return String.format(LanguageManager.getText(server.serv_language, baseText), accountString, 
        Ressources.getTierEmote().get(tier.getTier()).getUsableEmote() + " " + tier.toString(server.serv_language),
        FullTierUtil.getTierRankTextDifference(leagueEntrySecond, leagueEntryFirst, server.serv_language, rankedQueue)
        + " / " + LanguageManager.getText(server.serv_language, rankedQueue.getNameId())) + "\n";
  }

  private void notInGameWithoutRankInfo(final StringBuilder stringMessage, DTO.Player player) {
    stringMessage.append(player.getUser().getAsMention() + " : " 
        + LanguageManager.getText(server.serv_language, "informationPanelNotInGame") + " \n");
  }

  private void notInGameUnranked(final StringBuilder stringMessage, DTO.LeagueAccount leagueAccount) {
    stringMessage.append("- " + leagueAccount.leagueAccount_name + " : " 
        + LanguageManager.getText(server.serv_language, "unranked") + " \n");
  }

  public String getInfochannelMessage() {
    return infochannelMessage.toString();
  }

  public static void awaitAll(List<TreatPlayerWorker> playersToWait) {

    boolean needToWait;

    do {
      needToWait = false;
      for(TreatPlayerWorker playerToWait : playersToWait) {
        if(playersInWork.contains(playerToWait)) {
          needToWait = true;
          break;
        }
      }

      if(needToWait) {
        try {
          TimeUnit.MILLISECONDS.sleep(100);
        } catch (InterruptedException e) {
          logger.error("Thread as been interupt when waiting TreatPlayer Worker !", e);
          Thread.currentThread().interrupt();
        }
      }
    }while(needToWait);
  }


  public TreatedPlayer getTreatedPlayer() {
    return treatedPlayer;
  }

}
