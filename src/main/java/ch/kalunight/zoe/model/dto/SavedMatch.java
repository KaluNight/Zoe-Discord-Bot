package ch.kalunight.zoe.model.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import ch.kalunight.zoe.exception.PlayerNotFoundException;
import net.rithms.riot.api.endpoints.match.dto.Match;
import net.rithms.riot.api.endpoints.match.dto.Participant;
import net.rithms.riot.api.endpoints.match.dto.ParticipantIdentity;
import net.rithms.riot.api.endpoints.match.dto.ParticipantTimeline;
import net.rithms.riot.api.endpoints.match.dto.Player;

public class SavedMatch implements Serializable {

  private static final int BLUE_TEAM_ID = 100;

  private static final long serialVersionUID = -3423117740284389063L;

  private List<SavedMatchPlayer> players;

  private int queueId;

  private String gameVersion;

  private long gameCreation;
  
  /**
   * Match duration in seconds.
   */
  private long gameDurations;

  private boolean blueSideHasWin;

  public SavedMatch(Match match) {
    queueId = match.getQueueId();
    gameVersion = match.getGameVersion();
    gameDurations = match.getGameDuration();
    gameCreation = match.getGameCreation();
    
    players = new ArrayList<>();

    for(Participant participant : match.getParticipants()) {
      buildPlayer(match, participant);
    }

    if(match.getTeamByTeamId(100).getWin().equals("Win")) {
      blueSideHasWin = true;
    }else {
      blueSideHasWin = false;
    }
  }

  private void buildPlayer(Match match, Participant participant) {
    Player player = null;

    for(ParticipantIdentity participantIdentity : match.getParticipantIdentities()) {
      if(participant.getParticipantId() == participantIdentity.getParticipantId()) {
        player = participantIdentity.getPlayer();
      }
    }

    if(player != null) {
      ParticipantTimeline timeline = participant.getTimeline();
      String role = null;
      String lane = null;
      if(timeline != null) {
        role = timeline.getRole();
        lane = timeline.getLane();
      }

      boolean blueSide;
      if(participant.getTeamId() == BLUE_TEAM_ID) {
        blueSide = true;
      }else {
        blueSide = false;
      }

      SavedMatchPlayer savedPlayer = new SavedMatchPlayer(blueSide, player.getSummonerId(), participant.getChampionId(),
          participant.getStats(), role, lane);

      players.add(savedPlayer);
    }
  }

  public SavedMatchPlayer getSavedMatchPlayerBySummonerId(String summonerId) {

    for(SavedMatchPlayer savedMatchPlayer : players) {
      if(savedMatchPlayer.getSummonerId().equals(summonerId)) {
        return savedMatchPlayer;
      }
    }
    return null;
  }

  public SavedMatchPlayer getSavedMatchPlayerByChampionId(int championId) {

    for(SavedMatchPlayer savedMatchPlayer : players) {
      if(savedMatchPlayer.getChampionId() == championId) {
        return savedMatchPlayer;
      }
    }
    return null;
  }

  public boolean isGivenAccountWinner(String summonerId) {

    for(SavedMatchPlayer player : players) {
      if(player.getSummonerId().equals(summonerId)) {
        return (player.isBlueSide() && blueSideHasWin) || (!player.isBlueSide() && !blueSideHasWin);
      }
    }

    throw new PlayerNotFoundException("Impossible to give a winner in the game since the player is not in the game");
  }

  public long getGameCreation() {
    return gameCreation;
  }

  public long getGameDurations() {
    return gameDurations;
  }
  
  public List<SavedMatchPlayer> getPlayers() {
    return players;
  }

  public boolean isBlueSideHasWin() {
    return blueSideHasWin;
  }

  public int getQueueId() {
    return queueId;
  }

  public String getGameVersion() {
    return gameVersion;
  }

}
