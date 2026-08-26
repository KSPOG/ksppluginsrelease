package com.ksp.geflipper.analytics;

import com.ksp.geflipper.executionmodel.ExecutionModel;
import com.ksp.geflipper.model.Models.*;
import com.ksp.geflipper.persistence.Store;

import java.util.*;

public final class CalibrationService {
    private final Store store;
    public CalibrationService(Store store){this.store=store;}

    public void recordOutcome(RecommendationOutcome outcome){store.saveOutcome(outcome);recalculate();}

    public void recordDurationSample(Side side,long matchedHourlyVolume,long predictedSeconds,long actualSeconds){
        if(predictedSeconds<=0||actualSeconds<=0)return;String key="duration.factor."+side.name().toLowerCase()+"."+ExecutionModel.tier(matchedHourlyVolume);Map<String,Double> metrics=store.metrics();double old=metrics.getOrDefault(key,1.0);double ratio=clamp(actualSeconds/(double)predictedSeconds,0.25,4.0);double updated=old*0.90+ratio*0.10;store.saveMetric(key,updated,1);
    }

    public CalibrationMetrics metrics(){
        List<RecommendationOutcome> outcomes=store.outcomes(10000);if(outcomes.isEmpty())return new CalibrationMetrics(0,0,0,0,0,0,0,0,0,java.time.Instant.now());double durAbs=0,durPct=0,profitAbs=0;long durN=0;for(var o:outcomes){profitAbs+=Math.abs(o.predictedProfit()-o.actualProfit());if(o.predictedDurationSeconds()>0&&o.actualDurationSeconds()>0){durAbs+=Math.abs(o.predictedDurationSeconds()-o.actualDurationSeconds());durPct+=Math.abs(o.actualDurationSeconds()-o.predictedDurationSeconds())/(double)o.actualDurationSeconds();durN++;}}Map<String,Long> actions=store.recommendationActionCounts();long actionable=actions.entrySet().stream().filter(e->!"WAIT".equals(e.getKey())).mapToLong(Map.Entry::getValue).sum();long modifyN=actions.getOrDefault("MODIFY_BUY",0L)+actions.getOrDefault("MODIFY_SELL",0L);long abortN=actions.getOrDefault("ABORT",0L);double modify=actionable==0?0:modifyN/(double)actionable;double abort=actionable==0?0:abortN/(double)actionable;return new CalibrationMetrics(outcomes.size(),durN==0?0:durAbs/durN,durN==0?0:durPct/durN,profitAbs/outcomes.size(),store.metrics().getOrDefault("forecast.mae",0.0),store.metrics().getOrDefault("forecast.iqrCoverage",0.0),store.metrics().getOrDefault("recommendation.acceptance",0.0),modify,abort,java.time.Instant.now());
    }
    private void recalculate(){CalibrationMetrics m=metrics();store.saveMetric("duration.maeSeconds",m.durationMaeSeconds(),m.samples());store.saveMetric("duration.mape",m.durationMape(),m.samples());store.saveMetric("profit.mae",m.profitMae(),m.samples());store.saveMetric("modify.rate",m.modifyRate(),m.samples());store.saveMetric("abort.rate",m.abortRate(),m.samples());}
    private static double clamp(double v,double lo,double hi){return Math.max(lo,Math.min(hi,v));}
}
