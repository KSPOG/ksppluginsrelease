package com.ksp.geflipper.executionmodel;

import com.ksp.geflipper.model.Models.*;
import com.ksp.geflipper.persistence.Store;
import java.time.*;
import java.util.Map;

public final class ExecutionModel {
    public record Estimate(double fillProbability,double expectedMinutes,double aggressiveness,double calibratedFactor) {}
    private final Store store;
    public ExecutionModel(Store store){this.store=store;}

    public Estimate estimate(MarketSnapshot s,MarketFeatures f,Side side,long offerPrice,int quantity,double forecastConfidence){
        double relevant=side==Side.BUY?s.latestLow():s.latestHigh(); double spread=Math.max(1,s.latestHigh()-s.latestLow());
        double distance=Math.abs(offerPrice-relevant)/spread; double aggressiveness=clamp(1-distance,0,1);
        double flow=Math.max(1,s.matchedHourlyVolume()); double pressure=quantity/flow;
        double tod=hourFactor(Instant.now()); double volumeScore=clamp(Math.log10(flow+1)/5.0,0,1);
        double probability=clamp(0.12+0.42*aggressiveness+0.23*forecastConfidence+0.18*volumeScore-0.12*Math.min(1,pressure),0.05,0.985);
        double base=2.5+60.0*pressure*2.2; double priceFactor=1.45-0.75*aggressiveness; double volFactor=1+f.volatility1h()*7.0;
        double calibration=calibration(side,flow); double minutes=clamp(base*priceFactor*volFactor*tod*calibration/Math.max(0.10,probability),1.0,720.0);
        return new Estimate(probability,minutes,aggressiveness,calibration);
    }

    private double calibration(Side side,double volume){Map<String,Double> m=store.metrics();String key="duration.factor."+side.name().toLowerCase()+"."+tier(volume);return clamp(m.getOrDefault(key,1.0),0.35,3.0);}
    public static String tier(double volume){if(volume<100)return"vlow";if(volume<1_000)return"low";if(volume<10_000)return"medium";if(volume<100_000)return"high";return"vhigh";}
    private static double hourFactor(Instant now){int h=now.atZone(ZoneOffset.UTC).getHour();return h>=12&&h<=22?0.90:h>=6?1.0:1.12;}
    private static double clamp(double v,double lo,double hi){return Math.max(lo,Math.min(hi,v));}
}
