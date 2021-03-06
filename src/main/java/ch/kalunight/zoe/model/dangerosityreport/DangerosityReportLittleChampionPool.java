package ch.kalunight.zoe.model.dangerosityreport;

import ch.kalunight.zoe.translation.LanguageManager;
import net.dv8tion.jda.api.JDA;

public class DangerosityReportLittleChampionPool extends DangerosityReport {

  private static final int LITTLE_CHAMPION_POOL_LOW_VALUE = 5;

  private static final int LITTLE_CHAMPION_POOL_LOW_CHAMPIONS_POOL_MINIMUM = 4;
  
  private static final int LITTLE_CHAMPION_POOL_MEDIUM_VALUE = 10;

  private static final int LITTLE_CHAMPION_POOL_MEDIUM_CHAMPIONS_POOL_MINIMUM = 3;
  
  private static final int LITTLE_CHAMPION_POOL_HIGH_VALUE = 15;

  private static final int LITTLE_CHAMPION_POOL_HIGH_CHAMPIONS_POOL_MINIMUM = 2;
  
  private int championPoolSize;
  
  public DangerosityReportLittleChampionPool(int championPoolSize) {
    super(DangerosityReportType.LITTLE_CHAMPION_POOL, DangerosityReportSource.PLAYER);
    this.championPoolSize = championPoolSize;
  }

  @Override
  protected String getInfoToShow(String lang, JDA jda) {
    return String.format(LanguageManager.getText(lang, "dangerosityReportLittleChampionPoolInfo"), championPoolSize);
  }

  @Override
  public int getReportValue() {
    
    if(LITTLE_CHAMPION_POOL_HIGH_CHAMPIONS_POOL_MINIMUM >= championPoolSize) {
      return LITTLE_CHAMPION_POOL_HIGH_VALUE;
    }
    
    if(LITTLE_CHAMPION_POOL_MEDIUM_CHAMPIONS_POOL_MINIMUM >= championPoolSize) {
      return LITTLE_CHAMPION_POOL_MEDIUM_VALUE;
    }
    
    if(LITTLE_CHAMPION_POOL_LOW_CHAMPIONS_POOL_MINIMUM >= championPoolSize) {
      return LITTLE_CHAMPION_POOL_LOW_VALUE;
    }
    
    return BASE_SCORE;
  }
  
  public int getChampionPoolSize() {
    return championPoolSize;
  }
  
}
