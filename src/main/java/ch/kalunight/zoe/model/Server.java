package ch.kalunight.zoe.model;

import java.util.ArrayList;
import java.util.List;
import org.joda.time.DateTime;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.TextChannel;

public class Server {

  private Guild guild;
  private List<Player> players;
  private List<Team> teams;
  private TextChannel infoChannel;
  private ControlPannel controlePannel;
  private SpellingLangage langage;
  private DateTime lastRefresh;
  
  public Server(Guild guild, SpellingLangage langage) {
    this.guild = guild;
    this.langage = langage;
    players = new ArrayList<>();
    teams = new ArrayList<>();
    controlePannel = new ControlPannel();
    lastRefresh = DateTime.now();
  }
  
  public synchronized boolean isNeedToBeRefreshed() {
    boolean needToBeRefreshed = false;
    if(lastRefresh == null || lastRefresh.isBefore(DateTime.now().minusMinutes(3))) {
      needToBeRefreshed = true;
      lastRefresh = DateTime.now();
    }
    return needToBeRefreshed;
  }

  public List<Player> getPlayers() {
    return players;
  }

  public void setPlayers(List<Player> players) {
    this.players = players;
  }

  public List<Team> getTeams() {
    return teams;
  }

  public void setTeams(List<Team> teams) {
    this.teams = teams;
  }

  public Guild getGuild() {
    return guild;
  }

  public TextChannel getInfoChannel() {
    return infoChannel;
  }

  public void setInfoChannel(TextChannel infoChannel) {
    this.infoChannel = infoChannel;
  }

  public SpellingLangage getLangage() {
    return langage;
  }

  public void setLangage(SpellingLangage langage) {
    this.langage = langage;
  }

  public DateTime getLastRefresh() {
    return lastRefresh;
  }

  public void setLastRefresh(DateTime lastRefresh) {
    this.lastRefresh = lastRefresh;
  }

  public ControlPannel getControlePannel() {
    return controlePannel;
  }

  public void setControlePannel(ControlPannel controlePannel) {
    this.controlePannel = controlePannel;
  }
}
