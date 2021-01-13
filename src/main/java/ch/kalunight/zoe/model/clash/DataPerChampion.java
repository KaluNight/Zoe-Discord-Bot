package ch.kalunight.zoe.model.clash;

import java.util.ArrayList;
import java.util.List;

import ch.kalunight.zoe.model.dangerosityreport.DangerosityReport;
import ch.kalunight.zoe.model.dangerosityreport.DangerosityReportType;
import ch.kalunight.zoe.model.dto.SavedMatch;
import ch.kalunight.zoe.model.dto.SavedMatchPlayer;
import ch.kalunight.zoe.model.dto.SavedSimpleMastery;

public class DataPerChampion implements Comparable<DataPerChampion> {

  private int championId;

  private List<SavedMatch> matchs;
  
  private Integer nbrWin;

  private Integer nbrLose;

  private Double winrate;

  private SavedSimpleMastery mastery;
  
  private List<DangerosityReport> dangerosityReports;
  
  public DataPerChampion(int championId, List<SavedMatch> matchs) {
    this.championId = championId;
    this.matchs = matchs;
    this.dangerosityReports = new ArrayList<>();
  }

  public double getWinrate() {
    if(winrate == null) {
      nbrWin = 0;
      nbrLose = 0;

      for(SavedMatch match : matchs) {
        for(SavedMatchPlayer player : match.getPlayers()) {
          if(player.getChampionId() == championId) {
            if(match.isGivenAccountWinner(player.getAccountId())){
              nbrWin++;
            }else {
              nbrLose++;
            }
          }
        }
      }

      if(getNumberOfGame() != 0) {
        winrate = (nbrWin.doubleValue() / (nbrWin + nbrLose)) * 100;
      }else {
        winrate = 0d;
      }
    }
    
    return winrate;
  }
  
  @Override
  public int compareTo(DataPerChampion objectToTest) {
    if(objectToTest.getNumberOfGame() > getNumberOfGame()) {
      return 1;
    }else if(objectToTest.getNumberOfGame() < getNumberOfGame()) {
      return -1;
    }
    
    return 0;
  }
  
  public int getNumberOfWin() {
    if(nbrWin == null) {
      getWinrate();
    }
    
    return nbrWin;
  }
  
  public int getNumberOfLose() {
    if(nbrLose == null) {
      getWinrate();
    }
    
    return nbrLose;
  }
  
  public List<DangerosityReport> getDangerosityReports() {
    return dangerosityReports;
  }
  
  public DangerosityReport getDangerosityReport(DangerosityReportType type) {
    for(DangerosityReport report : dangerosityReports) {
      if(report.getReportType() == type) {
        return report;
      }
    }
    return null;
  }
  
  public int getNumberOfGame() {
    if(nbrWin == null || nbrLose == null) {
      return matchs.size();
    }else {
      return nbrLose + nbrWin;
    }
  }

  public int getChampionId() {
    return championId;
  }

  public List<SavedMatch> getMatchs() {
    return matchs;
  }

  public SavedSimpleMastery getMastery() {
    return mastery;
  }

  public void setMastery(SavedSimpleMastery mastery) {
    this.mastery = mastery;
  }
  
}