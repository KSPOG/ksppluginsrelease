package com.ksp.geflipper.dumps;

import com.ksp.geflipper.config.ServerConfig;
import com.ksp.geflipper.features.FeatureEngine;
import com.ksp.geflipper.forecasting.ForecastService;
import com.ksp.geflipper.marketdata.WikiMarketDataService;
import com.ksp.geflipper.model.Models.*;
import com.ksp.geflipper.persistence.Store;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

public final class DumpService implements AutoCloseable {
    private final ServerConfig config;private final Store store;private final WikiMarketDataService market;private final FeatureEngine features;private final ForecastService forecasts;
    private final ScheduledExecutorService scheduler=Executors.newSingleThreadScheduledExecutor(r->new Thread(r,"ksp-dump-detector"));
    private final Map<Integer,DumpSignal> active=new ConcurrentHashMap<>();private final Map<Integer,Instant> lastEmitted=new ConcurrentHashMap<>();private final CopyOnWriteArrayList<BlockingQueue<DumpSignal>> subscribers=new CopyOnWriteArrayList<>();
    public DumpService(ServerConfig config,Store store,WikiMarketDataService market,FeatureEngine features,ForecastService forecasts){this.config=config;this.store=store;this.market=market;this.features=features;this.forecasts=forecasts;}
    public void start(){scheduler.scheduleWithFixedDelay(this::scanSafely,2,config.dumpPollInterval().toSeconds(),TimeUnit.SECONDS);}
    public List<DumpSignal> active(){return active.values().stream().sorted(Comparator.comparingDouble(DumpSignal::severity).reversed()).toList();}
    public BlockingQueue<DumpSignal> subscribe(){BlockingQueue<DumpSignal> q=new LinkedBlockingQueue<>(256);subscribers.add(q);return q;}
    public void unsubscribe(BlockingQueue<DumpSignal> q){subscribers.remove(q);}
    private void scanSafely(){try{scan();}catch(Exception e){System.err.println("[dump] "+e.getMessage());}}
    private void scan(){Instant now=Instant.now();for(MarketSnapshot s:market.all()){
        List<MarketPoint> h=market.history(s.item().itemId(),512);MarketFeatures f=features.extract(s,h);if(f.distanceFromRollingMedian()>-0.02||f.volumeAcceleration()<1.20||f.abnormalityScore()<2.0||f.liquidityScore()<0.08)continue;
        ItemForecast fc=forecasts.forecast(s,f,h,30,config.latestRejectSeconds());if(fc.quality()==ForecastQuality.INVALID||fc.quality()==ForecastQuality.STALE)continue;double severity=Math.min(10,Math.abs(f.distanceFromRollingMedian())/Math.max(0.002,f.volatility1h())+Math.max(0,f.volumeAcceleration()-1));double recoveryProbability=clamp(0.25+fc.confidence()*0.40+Math.min(.2,severity/25)+Math.min(.15,Math.max(0,f.volumeAcceleration()-1)*.05),.10,.93);long recovery=fc.low().isEmpty()?s.avg5mLow():Math.round(fc.lowAtOrLast(Math.min(2,fc.low().size()-1)).mean());recovery=Math.max(s.latestLow()+1,Math.min(s.latestHigh(),Math.max(recovery,s.avg5mLow())));long unit=Math.max(0,recovery-s.latestLow());long expectedProfit=unit*Math.max(1,Math.min(s.item().buyLimit(),Math.max(1,s.matchedHourlyVolume()/20)));long seconds=Math.round(Math.max(300,30*60*(1+fc.robustSigmaPct()*5)/recoveryProbability));DumpSignal signal=new DumpSignal(UUID.randomUUID(),s.item().itemId(),s.item().name(),now,severity,recoveryProbability,s.latestLow(),recovery,seconds,expectedProfit,f.volumeAcceleration());active.put(s.item().itemId(),signal);
        Instant prev=lastEmitted.get(s.item().itemId());if(prev==null||Duration.between(prev,now).toMinutes()>=10){lastEmitted.put(s.item().itemId(),now);store.saveDump(signal);for(BlockingQueue<DumpSignal> q:subscribers)q.offer(signal);}
    }active.entrySet().removeIf(e->Duration.between(e.getValue().detectedAt(),now).toMinutes()>20);}
    private static double clamp(double v,double lo,double hi){return Math.max(lo,Math.min(hi,v));}
    @Override public void close(){scheduler.shutdownNow();}
}
