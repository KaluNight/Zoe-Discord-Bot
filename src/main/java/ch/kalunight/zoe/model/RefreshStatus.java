package ch.kalunight.zoe.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.AtomicDouble;

import ch.kalunight.zoe.model.dto.DTO.Server;
import ch.kalunight.zoe.model.dto.ServerPerLastRefreshComparator;

public class RefreshStatus {

  private static final int DURATION_OF_EVALUATION_PHASE = 3;

  private static final int NUMBER_OF_CYCLE_NEEDED_FOR_ACTION = 18; // 1 cycle happens every 10 seconds

  private static final int MINIMAL_REFRESH_RATE_IN_MINUTES = 5;

  private static final int START_DELAY_BETWEEN_EACH_REFRESH_IN_MINUTES = 5;

  private static final int EVALUTATION_INCREASE_DELAY_VALUE_IN_MINUTES = 5;

  private static final int MAX_REFRESH_RATE_IN_MINUTES = 60;

  private static final int SMART_MOD_TIME_IN_MINUTES = 60;

  private static final int FAILED_HEIGHER_RATE_PUNISHEMENT_IN_HOURS = 6;
  
  private static final double NOT_INIT_REFRESH_SERVER_RATE = -1;
  
  private static final int ONE_MINUTE_CYCLE = 60;

  private static final Logger logger = LoggerFactory.getLogger(RefreshStatus.class); 

  private static final ServerPerLastRefreshComparator serverComparator = new ServerPerLastRefreshComparator();
  
  private AtomicInteger refreshRateInMinute;

  private LocalDateTime evaluationEnd;

  private RefreshPhase refreshPhase;

  private LocalDateTime smartModEnd;

  private AtomicInteger numberOfServerManaged;

  private EvalutationOnRoadResultFailed lastEvaluationFailed;
  
  private Queue<Server> serversToEvaluate;
  
  private Stopwatch analysisClock;
  
  private AtomicDouble currentServerRefreshPerMin;
  
  private AtomicInteger serverRefreshedLastCycle;

  private List<RefreshLoadStatus> refreshLoadsHistory;

  public RefreshStatus() {
    this.refreshRateInMinute = new AtomicInteger();
    this.evaluationEnd = null;
    this.refreshPhase = RefreshPhase.NEED_TO_INIT;
    this.smartModEnd = LocalDateTime.now();
    this.refreshLoadsHistory = Collections.synchronizedList(new ArrayList<RefreshLoadStatus>());
    this.serversToEvaluate = new LinkedList<>();
    this.currentServerRefreshPerMin = new AtomicDouble(NOT_INIT_REFRESH_SERVER_RATE);
  }
  
  private void refreshServerRate(int serverRefreshed) {
    if(analysisClock == null) {
      analysisClock = Stopwatch.createStarted();
      serverRefreshedLastCycle = new AtomicInteger();
      return;
    }
    
    final int numberTotalOfServerRefreshed = serverRefreshedLastCycle.addAndGet(serverRefreshed);
    final int currentCycleTime = (int) analysisClock.elapsed(TimeUnit.SECONDS);
    
    if(currentCycleTime >= ONE_MINUTE_CYCLE) {
      analysisClock.stop();
      double adaptedServerRate = (numberTotalOfServerRefreshed / (double) currentCycleTime) * ONE_MINUTE_CYCLE;
      currentServerRefreshPerMin.set(adaptedServerRate);
      serverRefreshedLastCycle.set(0);
      logger.info("Cycle refresh time : {}s | Current Refresh server rate : {}", currentCycleTime, adaptedServerRate);
      analysisClock = Stopwatch.createStarted();
    }
    
  }

  public void init(int numberOfServerCurrentlyManaged, List<Server> serversToEvaluate) {
    synchronized (this) {
      if(refreshPhase == RefreshPhase.NEED_TO_INIT) {
        refreshRateInMinute.set(START_DELAY_BETWEEN_EACH_REFRESH_IN_MINUTES);
        evaluationEnd = LocalDateTime.now().plusMinutes(DURATION_OF_EVALUATION_PHASE);
        numberOfServerManaged = new AtomicInteger(numberOfServerCurrentlyManaged);
        refreshPhase = RefreshPhase.IN_EVALUATION_PHASE;
        Collections.sort(serversToEvaluate, serverComparator);
        this.serversToEvaluate.clear();
        this.serversToEvaluate.addAll(serversToEvaluate);
        logger.info("Refresh status initiated! Evaluation started.");
      }else {
        logger.warn("Refresh status already initiated!");
      }
    }
  }

  public void manageEvaluationPhase(int serversRefreshed) {
    synchronized (this) {
      refreshServerRate(serversRefreshed);
      if(refreshPhase == RefreshPhase.IN_EVALUATION_PHASE) {
        final double serversRefreshRate = currentServerRefreshPerMin.get() + 1; //Add 1 to avoid the case where 0
        if(evaluationEnd.isBefore(LocalDateTime.now())) {
          
          double minuteToRefreshAll = numberOfServerManaged.get() / serversRefreshRate + 3; //The 3 minutes added is for security
          int adaptedMinuteToRefreshAll = (int) minuteToRefreshAll;
          
          if(adaptedMinuteToRefreshAll < 60 && adaptedMinuteToRefreshAll > 0) {
            refreshPhase = RefreshPhase.CLASSIC_MOD;
            refreshRateInMinute.set(adaptedMinuteToRefreshAll);
            serversToEvaluate.clear();
            logger.info("Evaluation period ended ! A refresh rate of {} as been defined.", refreshRateInMinute.get());
          }else {
            refreshPhase = RefreshPhase.SMART_MOD;
            serversToEvaluate.clear();
            smartModEnd = LocalDateTime.now().plusMinutes(SMART_MOD_TIME_IN_MINUTES);
            logger.info("Evaluation objective not achieved and max value reached! Smart mod is now enabled.");
          }
        }
      }else {
        logger.warn("Refresh status not in evaluation phase!");
      }
    }
  }
  
  public void manageEvaluationPhaseOnRoad(int numberOfManagerServer, int queueSize, int serversRefreshed) {
    synchronized (this) {
      refreshServerRate(serversRefreshed);
      if(refreshPhase == RefreshPhase.IN_EVALUATION_PHASE_ON_ROAD) {
        numberOfServerManaged.set(numberOfManagerServer);

        if(queueSize < getNumberOfTaskUnderused()) {
          addRefreshLoadStatus(RefreshLoadStatus.UNDER_USED);
        } else if(queueSize > getNumberOfTaskAllowed()) {
          addRefreshLoadStatus(RefreshLoadStatus.OVER_USED);
        }else {
          addRefreshLoadStatus(RefreshLoadStatus.NORMAL_STATE);
        }

        if(evaluationEnd.isBefore(LocalDateTime.now())) {
          if(isStatusRegular(RefreshLoadStatus.OVER_USED)) {
            lastEvaluationFailed = new EvalutationOnRoadResultFailed(refreshRateInMinute.get(), evaluationEnd);
            evaluationEnd = evaluationEnd.plusMinutes(EVALUTATION_INCREASE_DELAY_VALUE_IN_MINUTES);
            refreshRateInMinute.addAndGet(EVALUTATION_INCREASE_DELAY_VALUE_IN_MINUTES);
            logger.info("Impossible to maintain the rate of {}, we go back to the heigher rate ({} minutes). The evaluation period continue to wait the normal state. (Evaluation on road)",
                refreshRateInMinute.get() - EVALUTATION_INCREASE_DELAY_VALUE_IN_MINUTES, refreshRateInMinute.get());
          }else {
            refreshPhase = RefreshPhase.CLASSIC_MOD;
            refreshLoadsHistory.clear();
            logger.info("Evaluation on road ended correctly ! The refresh rate is {} minutes.", refreshRateInMinute.get());
          }
        }
      }else {
        logger.warn("Refresh status not in evaluation phase on road !");
      }
    }
  }

  public void manageClassicMod(int numberServersManaged, long queueSize, int serversRefreshed) {
    synchronized (this) {
      refreshServerRate(serversRefreshed);
      if(refreshPhase == RefreshPhase.CLASSIC_MOD) {
        numberOfServerManaged.set(numberServersManaged);

        if(queueSize < getNumberOfTaskUnderused()) {
          manageClassicModUnderUsed();
        }else if(queueSize > getNumberOfTaskForceSmartMod()) {
          manageClassicModForceSmartMod(queueSize);
        }else if(queueSize > getNumberOfTaskForceMoreDelay()) {
          manageClassicModForceMoreDelay(queueSize);
        }else if(queueSize > getNumberOfTaskAllowed()) {
          manageClassicModOverload(queueSize);
        }else {
          addRefreshLoadStatus(RefreshLoadStatus.NORMAL_STATE);
        }
      }else {
        logger.warn("Refresh status not in classic mod!");
      }
    }
  }

  private void manageClassicModOverload(long queueSize) {
    addRefreshLoadStatus(RefreshLoadStatus.OVER_USED);
    if(isStatusRegular(RefreshLoadStatus.OVER_USED)) {
      int newRefreshRate = refreshRateInMinute.get() + EVALUTATION_INCREASE_DELAY_VALUE_IN_MINUTES;
      if(newRefreshRate >= MAX_REFRESH_RATE_IN_MINUTES) {
        logger.info("Zoe is a bit overloaded for 1 minutes straight and the refresh rate is to high ! {} are currently in queue. The smart mod has been enabled.", queueSize);
        refreshPhase = RefreshPhase.SMART_MOD;
        smartModEnd = LocalDateTime.now().plusMinutes(SMART_MOD_TIME_IN_MINUTES);

      }else {
        refreshRateInMinute.set(newRefreshRate);
        logger.info("Zoe is a bit overloaded ! {} are currently in queue. {} minutes added to the refresh cycle. Refresh rate is currently of {}",
            queueSize, EVALUTATION_INCREASE_DELAY_VALUE_IN_MINUTES, refreshRateInMinute.get());
      }
      refreshLoadsHistory.clear();
    }
  }

  private void manageClassicModForceMoreDelay(long queueSize) {
    int newRefreshRate = refreshRateInMinute.get() + EVALUTATION_INCREASE_DELAY_VALUE_IN_MINUTES;

    addRefreshLoadStatus(RefreshLoadStatus.WAY_TO_MUCH_OVER_USED);
    if(isLast3CycleStatusSame(RefreshLoadStatus.WAY_TO_MUCH_OVER_USED)) {
      if(newRefreshRate >= MAX_REFRESH_RATE_IN_MINUTES) {
        logger.info("Zoe is a bit overloaded and the refresh rate is to high ! {} are currently in queue. The smart mod has been enabled. (Force More Delay)", queueSize);
        refreshPhase = RefreshPhase.SMART_MOD;
        smartModEnd = LocalDateTime.now().plusMinutes(SMART_MOD_TIME_IN_MINUTES);
      }else {
        refreshRateInMinute.set(newRefreshRate);
        logger.info("Zoe is a bit overloaded ! {} are currently in queue. {} minutes added to the refresh cycle. Refresh rate is currently of {} (Force More Delay)",
            queueSize, EVALUTATION_INCREASE_DELAY_VALUE_IN_MINUTES, refreshRateInMinute.get());
      }
      refreshLoadsHistory.clear();
    }
  }

  private boolean isLast3CycleStatusSame(RefreshLoadStatus loadStatus) {

    List<RefreshLoadStatus> last3 = refreshLoadsHistory.subList(Math.max(refreshLoadsHistory.size() - 3, 0), refreshLoadsHistory.size());

    if(last3.size() == 3) {
      boolean sameStatus = true;

      for(RefreshLoadStatus last3Status : last3) {
        if(!last3Status.equals(loadStatus)){
          sameStatus = false;
          break;
        }
      }

      return sameStatus;

    }else {
      return false;
    }
  }

  private void manageClassicModForceSmartMod(long queueSize) {
    logger.warn("Zoe is overloaded ! {} are currently in queue. The smart mod is now enabled.", queueSize);
    refreshPhase = RefreshPhase.SMART_MOD;
    smartModEnd = LocalDateTime.now().plusMinutes(SMART_MOD_TIME_IN_MINUTES);
    refreshLoadsHistory.clear();
  }

  private void manageClassicModUnderUsed() {
    addRefreshLoadStatus(RefreshLoadStatus.UNDER_USED);
    if(isStatusRegular(RefreshLoadStatus.UNDER_USED)) {
      int newRefreshRate = refreshRateInMinute.get() - EVALUTATION_INCREASE_DELAY_VALUE_IN_MINUTES;
      if(newRefreshRate >= MINIMAL_REFRESH_RATE_IN_MINUTES &&
          (lastEvaluationFailed == null || (lastEvaluationFailed.getTimeOfTheFail().isBefore(LocalDateTime.now().minusHours(FAILED_HEIGHER_RATE_PUNISHEMENT_IN_HOURS)) 
              || newRefreshRate > lastEvaluationFailed.getRateFailed()))) {
        refreshRateInMinute.set(newRefreshRate);
        refreshLoadsHistory.clear();
        refreshPhase = RefreshPhase.IN_EVALUATION_PHASE_ON_ROAD;
        evaluationEnd = LocalDateTime.now().plusMinutes(refreshRateInMinute.get());
        logger.info("Zoe has been underused for 3 minutes straight, we lower the refresh rate by {}. The refresh rate is now of {} minutes.",
            EVALUTATION_INCREASE_DELAY_VALUE_IN_MINUTES, refreshRateInMinute.get());
      }
    }
  }
  
  public List<Server> getServersToLoadInEvaluation(int serversLoadable) {
    List<Server> serversToRefresh = new ArrayList<>();
    
    while(serversToRefresh.size() <= serversLoadable && !serversToEvaluate.isEmpty()) {
      serversToRefresh.add(serversToEvaluate.poll());
    }
    
    return serversToRefresh;
  }

  /**
   * Manage Smart Mod. If smart mod end, return true.
   * @param numberOfServerCurrentlyManaged
   * @return True if smart mod has ended.
   */
  public boolean manageSmartMod(int numberOfServerCurrentlyManaged, int serversRefreshed) {
    synchronized (this) {
      refreshServerRate(serversRefreshed);
      if(refreshPhase == RefreshPhase.SMART_MOD) {
        if(smartModEnd.isBefore(LocalDateTime.now())) {
          refreshRateInMinute.set(START_DELAY_BETWEEN_EACH_REFRESH_IN_MINUTES);
          evaluationEnd = LocalDateTime.now().plusMinutes(DURATION_OF_EVALUATION_PHASE);
          numberOfServerManaged.set(numberOfServerCurrentlyManaged);
          refreshPhase = RefreshPhase.NEED_TO_INIT;
          logger.info("Smart mod ended! Evaluation of performance started. An init will first happens.");
          return true;
        }
      } else {
        logger.warn("Refresh status not in smart mod!");
      }
    }
    return false;
  }

  private void addRefreshLoadStatus(RefreshLoadStatus refreshLoad) {
    refreshLoadsHistory.add(refreshLoad);

    if(refreshLoadsHistory.size() == NUMBER_OF_CYCLE_NEEDED_FOR_ACTION + 1) {
      refreshLoadsHistory.remove(0);
    }
  }

  private boolean isStatusRegular(RefreshLoadStatus refreshLoad) {
    boolean statusRegular = false;
    if(refreshLoadsHistory.size() == NUMBER_OF_CYCLE_NEEDED_FOR_ACTION) {
      statusRegular = true;

      if(refreshLoad.equals(RefreshLoadStatus.OVER_USED)) {
        for(RefreshLoadStatus refreshLoadStatus : refreshLoadsHistory) {
          if(!refreshLoadStatus.equals(RefreshLoadStatus.OVER_USED) && !refreshLoadStatus.equals(RefreshLoadStatus.WAY_TO_MUCH_OVER_USED)) {
            statusRegular = false;
            break;
          }
        }
      }else {
        for(RefreshLoadStatus refreshLoadStatus : refreshLoadsHistory) {
          if(!refreshLoadStatus.equals(refreshLoad)) {
            statusRegular = false;
            break;
          }
        }
      }
    }
    return statusRegular;
  }

  private double getNumberOfTaskHandledEvery10Seconds() {
    return ((double) numberOfServerManaged.get() * 10) / (refreshRateInMinute.get() * 60);
  }

  private double getNumberOfTaskUnderused() {
    return getNumberOfTaskHandledEvery10Seconds() * 50 / 100; //50% of handled usage
  }

  private double getNumberOfTaskAllowed() {
    return getNumberOfTaskHandledEvery10Seconds() * 150 / 100; //150% of handled usage
  }

  private double getNumberOfTaskForceMoreDelay() {
    return getNumberOfTaskHandledEvery10Seconds() * 300 / 100; //300% of handled usage
  }

  private double getNumberOfTaskForceSmartMod() {
    return getNumberOfTaskHandledEvery10Seconds() * 500 / 100; //500% of handled usage
  }

  public AtomicDouble getCurrentServerRefreshPerMin() {
    return currentServerRefreshPerMin;
  }

  public RefreshPhase getRefreshPhase() {
    return refreshPhase;
  }

  public void setRefreshPhase(RefreshPhase refreshPhase) {
    this.refreshPhase = refreshPhase;
  }

  public LocalDateTime getEvaluationEnd() {
    return evaluationEnd;
  }

  public void setEvaluationEnd(LocalDateTime evaluationEnd) {
    this.evaluationEnd = evaluationEnd;
  }

  public LocalDateTime getSmartModEnd() {
    return smartModEnd;
  }

  public void setSmartModEnd(LocalDateTime smartModEnd) {
    this.smartModEnd = smartModEnd;
  }

  public List<RefreshLoadStatus> getRefreshLoadsHistory() {
    return refreshLoadsHistory;
  }
  
  public Queue<Server> getServersToEvaluate() {
    return serversToEvaluate;
  }

  public AtomicInteger getNumberOfServerManaged() {
    return numberOfServerManaged;
  }

  public AtomicInteger getRefresRatehInMinute() {
    return refreshRateInMinute;
  }

}
