package ch.kalunight.zoe.riotapi;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.rithms.riot.api.RiotApi;
import net.rithms.riot.api.RiotApiException;
import net.rithms.riot.api.endpoints.champion_mastery.dto.ChampionMastery;
import net.rithms.riot.api.endpoints.league.dto.LeagueEntry;
import net.rithms.riot.api.endpoints.match.dto.Match;
import net.rithms.riot.api.endpoints.match.dto.MatchList;
import net.rithms.riot.api.endpoints.spectator.dto.CurrentGameInfo;
import net.rithms.riot.api.endpoints.summoner.dto.Summoner;
import net.rithms.riot.constant.CallPriority;
import net.rithms.riot.constant.Platform;

/**
 * This class is a wrapper for riotApi which will cache some data to reduces requests to riot servers
 */
public class CachedRiotApi {

  private final RiotApi riotApi;

  private final ConcurrentHashMap<MatchKey, Match> matchCache = new ConcurrentHashMap<>();

  private final AtomicInteger apiMatchRequestCount = new AtomicInteger(0);
  private final AtomicInteger allMatchRequestCount = new AtomicInteger(0);
  private final AtomicInteger matchListRequestCount = new AtomicInteger(0);
  private final AtomicInteger summonerRequestCount = new AtomicInteger(0);
  private final AtomicInteger leagueEntryRequestCount = new AtomicInteger(0);
  private final AtomicInteger championMasteryRequestCount = new AtomicInteger(0);
  private final AtomicInteger currentGameInfoRequestCount = new AtomicInteger(0);

  public CachedRiotApi(RiotApi riotApi) {
    this.riotApi = riotApi;
  }

  public synchronized void cleanCache() {
    CacheManager.cleanMatchCache();
  }
  
  public Match getMatch(Platform platform, long matchId, CallPriority priority) throws RiotApiException {
    Match match = CacheManager.getMatch(platform, Long.toString(matchId));

    if(match == null) {
      match = riotApi.getMatch(platform, matchId, priority);

      CacheManager.putMatch(platform, match);

      apiMatchRequestCount.incrementAndGet();
    }
    
    allMatchRequestCount.incrementAndGet();
    
    return match;
  }
  
  public MatchList getMatchListByAccountId(Platform platform, String accountId, Set<Integer> champion, Set<Integer> queue,
      Set<Integer> season, long beginTime, long endTime, int beginIndex, int endIndex, CallPriority priority) throws RiotApiException {
    MatchList matchList = riotApi.getMatchListByAccountId(platform, accountId, champion, queue, season, beginTime, endTime, beginIndex, endIndex, priority);

    matchListRequestCount.incrementAndGet();

    return matchList;
  }

  public Summoner getSummoner(Platform platform, String summonerId, CallPriority priority) throws RiotApiException {
    Summoner summoner = riotApi.getSummoner(platform, summonerId, priority);

    summonerRequestCount.incrementAndGet();

    return summoner;
  }

  public Summoner getSummonerByName(Platform platform, String summonerName, CallPriority priority) throws RiotApiException {
    Summoner summoner = riotApi.getSummonerByName(platform, summonerName, priority);

    summonerRequestCount.incrementAndGet();

    return summoner;
  }

  public Summoner getSummonerByPuuid(Platform platform, String puuid, CallPriority priority) throws RiotApiException {
    Summoner summoner = riotApi.getSummonerByPuuid(platform, puuid, priority);

    summonerRequestCount.incrementAndGet();

    return summoner;
  }

  public Set<LeagueEntry> getLeagueEntriesBySummonerId(Platform platform, String summonerId, CallPriority callPriority) throws RiotApiException {
    Set<LeagueEntry> leagueEntries = riotApi.getLeagueEntriesBySummonerId(platform, summonerId, callPriority);

    leagueEntryRequestCount.incrementAndGet();

    return leagueEntries;
  }

  public CurrentGameInfo getActiveGameBySummoner(Platform platform, String summonerId, CallPriority priority) throws RiotApiException {
    CurrentGameInfo gameInfo = riotApi.getActiveGameBySummoner(platform, summonerId, priority);

    currentGameInfoRequestCount.incrementAndGet();

    return gameInfo;
  }

  public ChampionMastery getChampionMasteriesBySummonerByChampion(Platform platform, String summonerId, int championId, CallPriority priority) throws RiotApiException {
    ChampionMastery mastery = riotApi.getChampionMasteriesBySummonerByChampion(platform, summonerId, championId, priority);

    championMasteryRequestCount.incrementAndGet();

    return mastery;
  }

  public List<ChampionMastery> getChampionMasteriesBySummoner(Platform platform, String summonerId, CallPriority priority) throws RiotApiException {
    List<ChampionMastery> masteries = riotApi.getChampionMasteriesBySummoner(platform, summonerId, priority);

    championMasteryRequestCount.incrementAndGet();

    return masteries;
  }

  public synchronized void clearCounts() {
    apiMatchRequestCount.set(0);
    allMatchRequestCount.set(0);
    matchListRequestCount.set(0);
    summonerRequestCount.set(0);
    leagueEntryRequestCount.set(0);
    championMasteryRequestCount.set(0);
    currentGameInfoRequestCount.set(0);
  }

  public int getTotalRequestCount() {
    return apiMatchRequestCount.intValue() + allMatchRequestCount.intValue() + matchListRequestCount.intValue()
        + summonerRequestCount.intValue() + leagueEntryRequestCount.intValue() + championMasteryRequestCount.intValue()
        + currentGameInfoRequestCount.intValue();
  }
  
  public int getNumberOfCachedMatch() {
    return matchCache.size();
  }

  public int getApiMatchRequestCount() {
    return apiMatchRequestCount.intValue();
  }

  public int getAllMatchRequestCount() {
    return allMatchRequestCount.intValue();
  }

  public int getMatchListRequestCount() {
    return matchListRequestCount.intValue();
  }

  public int getSummonerRequestCount() {
    return summonerRequestCount.intValue();
  }

  public int getLeagueEntryRequestCount() {
    return leagueEntryRequestCount.intValue();
  }

  public int getChampionMasteryRequestCount() {
    return championMasteryRequestCount.intValue();
  }

  public int getCurrentGameInfoRequestCount() {
    return currentGameInfoRequestCount.intValue();
  }
}
