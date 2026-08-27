package com.ksp.geflipper.candidates;

import com.ksp.geflipper.config.ServerConfig;
import com.ksp.geflipper.executionmodel.*;
import com.ksp.geflipper.features.FeatureEngine;
import com.ksp.geflipper.forecasting.ForecastService;
import com.ksp.geflipper.marketdata.WikiMarketDataService;
import com.ksp.geflipper.model.Models.*;

import java.util.*;

public final class CandidateEngine {
    private final ServerConfig config; private final WikiMarketDataService market; private final FeatureEngine features; private final ForecastService forecasts; private final ExecutionModel execution; private final GeTaxService tax;
    public CandidateEngine(ServerConfig config,WikiMarketDataService market,FeatureEngine features,ForecastService forecasts,ExecutionModel execution,GeTaxService tax){this.config=config;this.market=market;this.features=features;this.forecasts=forecasts;this.execution=execution;this.tax=tax;}

    public List<FlipCandidate> generate(AccountState account){
        List<FlipCandidate> out=new ArrayList<>(); StrategyPreferences p=account.strategy(); RiskSpec risk=risk(p.riskLevel());
        for(MarketSnapshot s:market.all()){
            if(!eligible(account,s))continue; List<MarketPoint> history=market.history(s.item().itemId(),512); MarketFeatures f=features.extract(s,history);
            if(f.dataAgeSeconds()>config.latestRejectSeconds()||f.liquidityScore()<risk.minLiquidity||s.matchedHourlyVolume()<risk.minHourlyVolume)continue;
            ItemForecast fc=forecasts.forecast(s,f,history,p.timeframeMinutes(),config.latestRejectSeconds()); if(fc.quality()==ForecastQuality.INVALID||fc.quality()==ForecastQuality.STALE||fc.confidence()<risk.minConfidence)continue;
            if(isDump(f,p)) { if(p.dumpEnabled()) bestDump(account,s,f,fc,risk).ifPresent(out::add); }
            else bestNormal(account,s,f,fc,risk).ifPresent(out::add);
            if(p.allowBuyAndHold()) bestHold(account,s,f,fc,risk).ifPresent(out::add);
        }
        out.sort(Comparator.comparingDouble(FlipCandidate::utility).reversed()); return out;
    }

    public Optional<FlipCandidate> candidateFor(AccountState account,int itemId,CandidateType preferred){
        return generate(account).stream().filter(c->c.itemId()==itemId&&(preferred==null||c.type()==preferred)).findFirst();
    }

    private Optional<FlipCandidate> bestNormal(AccountState a,MarketSnapshot s,MarketFeatures f,ItemForecast fc,RiskSpec risk){
        StrategyPreferences p=a.strategy(); long spread=s.latestHigh()-s.latestLow(); if(spread<=1)return Optional.empty();
        long forecastHigh=Math.round(fc.highAtOrLast(Math.min(2,fc.high().size()-1)).mean()); long sellCeiling=Math.max(s.latestLow()+1,Math.min(s.latestHigh(),forecastHigh)); FlipCandidate best=null;
        for(double buyStep:new double[]{0.05,0.15,0.30,0.50}) for(double sellStep:new double[]{0.05,0.15,0.30,0.50}){
            long buy=s.latestLow()+Math.max(1,Math.round(spread*buyStep)); long sell=sellCeiling-Math.max(1,Math.round(spread*sellStep)); if(sell<=buy)continue;
            long unitNet=tax.postTaxUnitPrice(s.item().name(),sell)-buy; if(unitNet<=0)continue; int qty=quantity(a,s,buy,risk,false); if(qty<=0)continue; long profit=unitNet*qty; if(profit<p.minExpectedProfit())continue;
            ExecutionModel.Estimate be=execution.estimate(s,f,Side.BUY,buy,qty,fc.confidence()), se=execution.estimate(s,f,Side.SELL,sell,qty,fc.confidence()); double duration=be.expectedMinutes()+se.expectedMinutes(); double completion=be.fillProbability()*se.fillProbability(); double gph=profit*60.0/Math.max(1,duration)*completion; double riskScore=riskScore(f,fc,a.gp(),buy,qty); double utility=utility(gph,fc.confidence(),completion,riskScore,buy*(double)qty,duration,risk);
            FlipCandidate c=new FlipCandidate(s.item().itemId(),s.item().name(),CandidateType.NORMAL_FLIP,buy,sell,qty,(sell-buy)*qty,profit,duration,gph,riskScore,fc.confidence(),completion,f.liquidityScore(),f.volatility1h(),utility,false,explain("normal",f,profit,duration,fc.confidence())); if(best==null||c.utility()>best.utility())best=c;
        }
        return Optional.ofNullable(best);
    }

    private Optional<FlipCandidate> bestDump(AccountState a,MarketSnapshot s,MarketFeatures f,ItemForecast fc,RiskSpec risk){
        if(!a.strategy().dumpEnabled())return Optional.empty(); long spread=Math.max(1,s.latestHigh()-s.latestLow()); long buy=s.latestLow()+Math.max(1,spread/8); double recoveryProb=clamp(0.30+fc.confidence()*0.35+Math.min(0.2,Math.max(0,f.volumeAcceleration()-1)*0.08)+Math.min(0.15,f.abnormalityScore()/20),0.15,0.92); long recovery=Math.max(buy+1,Math.round(fc.lowAtOrLast(Math.min(2,fc.low().size()-1)).mean())); recovery=Math.min(Math.max(recovery,Math.round(s.avg5mLow())),s.latestHigh()); long unitNet=tax.postTaxUnitPrice(s.item().name(),recovery)-buy; if(unitNet<=0)return Optional.empty(); int qty=quantity(a,s,buy,risk,true); long profit=unitNet*qty; if(qty<=0||profit<a.strategy().minDumpExpectedProfit())return Optional.empty(); ExecutionModel.Estimate e=execution.estimate(s,f,Side.BUY,buy,qty,fc.confidence()); double duration=Math.max(5,a.strategy().timeframeMinutes()*0.65)*(1+fc.robustSigmaPct()*5)/recoveryProb+e.expectedMinutes(); double gph=profit*60.0/duration*recoveryProb; double rs=riskScore(f,fc,a.gp(),buy,qty)*1.25; double u=utility(gph,fc.confidence(),recoveryProb,rs,buy*(double)qty,duration,risk);
        return Optional.of(new FlipCandidate(s.item().itemId(),s.item().name(),CandidateType.DUMP,buy,recovery,qty,(recovery-buy)*qty,profit,duration,gph,rs,fc.confidence(),recoveryProb,f.liquidityScore(),f.volatility1h(),u,false,explain("dump",f,profit,duration,fc.confidence())));
    }

    private Optional<FlipCandidate> bestHold(AccountState a,MarketSnapshot s,MarketFeatures f,ItemForecast fc,RiskSpec risk){
        if(fc.high().isEmpty()||a.strategy().timeframeMinutes()<60)return Optional.empty(); ForecastPoint end=fc.high().get(fc.high().size()-1); long buy=s.latestLow()+Math.max(1,(s.latestHigh()-s.latestLow())/5); long sell=Math.round(end.q25()); if(sell<=buy)return Optional.empty(); double expectedReturn=(tax.postTaxUnitPrice(s.item().name(),sell)-buy)/(double)Math.max(1,buy); if(expectedReturn<Math.max(0.01,f.volatility1h()*1.2))return Optional.empty(); int qty=quantity(a,s,buy,risk,false); if(qty<=0)return Optional.empty(); long profit=(tax.postTaxUnitPrice(s.item().name(),sell)-buy)*qty;if(profit<a.strategy().minExpectedProfit())return Optional.empty(); double mins=Math.max(a.strategy().timeframeMinutes(),java.time.Duration.between(fc.generatedAt(),end.time()).toMinutes()); double downside=Math.max(0,buy-fc.low().get(fc.low().size()-1).q25())/buy; double prob=clamp(fc.confidence()*(1-downside),0.1,0.95); double gph=profit*60.0/mins*prob; double rs=riskScore(f,fc,a.gp(),buy,qty)*(1+downside*3); double u=utility(gph,fc.confidence(),prob,rs,buy*(double)qty,mins,risk);
        return Optional.of(new FlipCandidate(s.item().itemId(),s.item().name(),CandidateType.BUY_AND_HOLD,buy,sell,qty,(sell-buy)*qty,profit,mins,gph,rs,fc.confidence(),prob,f.liquidityScore(),f.volatility1h(),u,true,explain("hold",f,profit,mins,fc.confidence())));
    }

    private boolean eligible(AccountState a,MarketSnapshot s){ItemMeta i=s.item();if(!i.tradeable()||i.buyLimit()<=0||s.latestHigh()<=s.latestLow()||s.latestLow()<=0)return false;if((a.f2pOnly()||!a.worldMember()||!a.accountMember())&&i.members())return false;String normalized=i.name().toLowerCase(Locale.ROOT);if(a.blockedItems().contains(i.itemId())||a.blockedItemNames().contains(normalized))return false;return a.allowedItemNames().isEmpty()||a.allowedItemNames().contains(normalized);}
    private boolean isDump(MarketFeatures f,StrategyPreferences p){return p.dumpEnabled()&&p.timeframeMinutes()<=30&&f.distanceFromRollingMedian()<-0.02&&f.volumeAcceleration()>1.20&&f.abnormalityScore()>2.0;}
    private int quantity(AccountState a,MarketSnapshot s,long buy,RiskSpec r,boolean dump){double configPct=Math.max(0.01,Math.min(1,a.strategy().maxItemExposurePct()/100.0));double pct=Math.min(configPct,r.exposure)*(dump?0.65:1);long byCapital=(long)Math.floor(a.gp()*pct/Math.max(1,buy));long byLiquidity=(long)Math.floor(s.matchedHourlyVolume()*(a.strategy().timeframeMinutes()/60.0)*r.participation*(dump?0.6:1));long used=a.offers().stream().filter(o->o.itemId()==s.item().itemId()&&o.side()==Side.BUY).mapToLong(o->o.filledQuantity()).sum();long remaining=Math.max(0,s.item().buyLimit()-used);return (int)Math.max(0,Math.min(Integer.MAX_VALUE,Math.min(remaining,Math.min(byCapital,Math.max(1,byLiquidity)))));}
    private static double riskScore(MarketFeatures f,ItemForecast fc,long gp,long buy,int qty){double exposure=buy*(double)qty/Math.max(1,gp);return clamp(fc.robustSigmaPct()*3+f.volatility1h()*2+(1-f.liquidityScore())*0.5+exposure,0,3);}
    private static double utility(double gph,double confidence,double fill,double riskScore,double capital,double minutes,RiskSpec r){double riskPenalty=r.riskWeight*riskScore*0.18;double capitalCost=capital*(minutes/60.0)*0.00002;double slotCost=gph*0.03;return gph*confidence*fill*(1-riskPenalty)-capitalCost-slotCost;}
    private static String explain(String type,MarketFeatures f,long profit,double mins,double c){return type+": liquidity="+pct(f.liquidityScore())+", spread="+pct(f.spreadPct())+", confidence="+pct(c)+", expectedProfit="+profit+", expectedMinutes="+Math.round(mins);}
    private static String pct(double x){return String.format(Locale.ROOT,"%.1f%%",x*100);}
    private static double clamp(double v,double lo,double hi){return Math.max(lo,Math.min(hi,v));}
    private static RiskSpec risk(RiskLevel r){return switch(r){case LOW->new RiskSpec(.15,.72,.035,.30,500,1.35);case HIGH->new RiskSpec(.50,.45,.09,.05,25,.65);default->new RiskSpec(.30,.58,.055,.15,100,1.0);};}
    private record RiskSpec(double exposure,double minConfidence,double participation,double minLiquidity,long minHourlyVolume,double riskWeight){}
}
