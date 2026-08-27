package com.ksp.geflipper.forecasting;

import com.ksp.geflipper.model.Models.*;
import com.ksp.geflipper.persistence.Store;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Deterministic probabilistic baseline: EWMA + robust trend + quantile residual bands. */
public final class ForecastService {
    private final Store store;
    private final Map<Integer,Instant> lastPersisted = new ConcurrentHashMap<>();
    private final Map<Integer,Deque<PendingForecast>> pending = new ConcurrentHashMap<>();
    private long validationSamples;
    public ForecastService() { this(null); }
    public ForecastService(Store store) { this.store = store; }

    public ItemForecast forecast(MarketSnapshot s,MarketFeatures f,List<MarketPoint> history,int timeframeMinutes,int staleRejectSeconds) {
        Instant now=Instant.now();
        validateDue(s, now);
        if(s.latestHigh()<=0||s.latestLow()<=0)return new ItemForecast(s.item().itemId(),List.of(),List.of(),0,ForecastQuality.INVALID,1,now);
        List<Double> lows=new ArrayList<>(), highs=new ArrayList<>(); for(MarketPoint p:history)if(p.lowPrice()>0&&p.highPrice()>0){lows.add((double)p.lowPrice());highs.add((double)p.highPrice());}
        lows.add((double)s.latestLow()); highs.add((double)s.latestHigh());
        double lowEwma=ewma(lows,0.30), highEwma=ewma(highs,0.30), lowSlope=slope(lows), highSlope=slope(highs);
        double lowSigma=robustSigma(lows), highSigma=robustSigma(highs), sigmaPct=Math.max(lowSigma/Math.max(1,lowEwma),highSigma/Math.max(1,highEwma));
        int[] horizons=horizons(timeframeMinutes); List<ForecastPoint> lowOut=new ArrayList<>(),highOut=new ArrayList<>();
        for(int h:horizons){ double scale=Math.sqrt(Math.max(1,h)/5.0), qLow=0.674*lowSigma*scale,qHigh=0.674*highSigma*scale; double trendScale=Math.min(h/5.0,24.0); double lm=Math.max(1,lowEwma+lowSlope*trendScale),hm=Math.max(lm+1,highEwma+highSlope*trendScale); lowOut.add(new ForecastPoint(now.plusSeconds(h*60L),lm,Math.max(1,lm-qLow),lm+qLow)); highOut.add(new ForecastPoint(now.plusSeconds(h*60L),hm,Math.max(1,hm-qHigh),hm+qHigh)); }
        double freshness=1-Math.min(1,f.dataAgeSeconds()/Math.max(1,staleRejectSeconds)); double historyScore=Math.min(1,history.size()/96.0); double confidence=clamp(0.40+freshness*0.22+f.liquidityScore()*0.20+historyScore*0.15-sigmaPct*2.0,0.05,0.97);
        ForecastQuality quality=f.dataAgeSeconds()>staleRejectSeconds?ForecastQuality.STALE:history.size()<12?ForecastQuality.LIMITED:confidence>=0.55?ForecastQuality.GOOD:ForecastQuality.LIMITED;
        ItemForecast result = new ItemForecast(s.item().itemId(),lowOut,highOut,confidence,quality,sigmaPct,now);
        persist(result);
        enqueue(result);
        return result;
    }

    private void validateDue(MarketSnapshot snapshot, Instant now) {
        if (store == null) return;
        Deque<PendingForecast> queue = pending.get(snapshot.item().itemId());
        if (queue == null) return;
        synchronized (queue) {
            while (!queue.isEmpty() && !queue.peekFirst().time.isAfter(now)) {
                PendingForecast p = queue.removeFirst();
                double mae = (Math.abs(snapshot.latestLow() - p.lowMean) + Math.abs(snapshot.latestHigh() - p.highMean)) / 2.0;
                double coverage = (inside(snapshot.latestLow(), p.lowQ25, p.lowQ75) ? 0.5 : 0.0)
                        + (inside(snapshot.latestHigh(), p.highQ25, p.highQ75) ? 0.5 : 0.0);
                Map<String,Double> metrics = store.metrics();
                double oldMae = metrics.getOrDefault("forecast.mae", mae);
                double oldCoverage = metrics.getOrDefault("forecast.iqrCoverage", coverage);
                validationSamples++;
                double alpha = Math.max(0.02, Math.min(0.20, 2.0 / (Math.min(validationSamples, 99) + 1.0)));
                store.saveMetric("forecast.mae", oldMae * (1-alpha) + mae * alpha, validationSamples);
                store.saveMetric("forecast.iqrCoverage", oldCoverage * (1-alpha) + coverage * alpha, validationSamples);
                store.saveMetric("forecast.validationSamples", validationSamples, validationSamples);
            }
        }
    }

    private void enqueue(ItemForecast forecast) {
        if (store == null || forecast.low().isEmpty() || forecast.high().isEmpty()) return;
        Deque<PendingForecast> queue = pending.computeIfAbsent(forecast.itemId(), ignored -> new ArrayDeque<>());
        synchronized (queue) {
            int n = Math.min(forecast.low().size(), forecast.high().size());
            for (int i=0;i<n;i++) {
                ForecastPoint low=forecast.low().get(i), high=forecast.high().get(i);
                queue.addLast(new PendingForecast(low.time(),low.mean(),low.q25(),low.q75(),high.mean(),high.q25(),high.q75()));
            }
            while (queue.size() > 64) queue.removeFirst();
        }
    }

    private static boolean inside(double value,double q25,double q75){return value>=q25&&value<=q75;}

    private void persist(ItemForecast forecast) {
        if (store == null || forecast.quality() == ForecastQuality.INVALID) return;
        Instant previous = lastPersisted.get(forecast.itemId());
        if (previous != null && Duration.between(previous, forecast.generatedAt()).toSeconds() < 60) return;
        lastPersisted.put(forecast.itemId(), forecast.generatedAt());
        store.saveForecast(forecast);
    }

    private static int[] horizons(int t){if(t<=15)return new int[]{5,10,15,30};if(t<=60)return new int[]{5,15,30,60,120};if(t<=240)return new int[]{15,30,60,120,240,480};return new int[]{30,60,120,240,480,1440};}
    private static double ewma(List<Double> v,double a){double x=v.get(0);for(int i=1;i<v.size();i++)x=a*v.get(i)+(1-a)*x;return x;}
    private static double slope(List<Double> v){int n=Math.min(24,v.size());if(n<3)return 0;double sx=0,sy=0,sxx=0,sxy=0;for(int j=0;j<n;j++){double x=j,y=v.get(v.size()-n+j);sx+=x;sy+=y;sxx+=x*x;sxy+=x*y;}double den=n*sxx-sx*sx;return Math.abs(den)<1e-9?0:(n*sxy-sx*sy)/den;}
    private static double robustSigma(List<Double> v){if(v.size()<3)return Math.max(1,v.get(v.size()-1)*0.01);List<Double>s=new ArrayList<>(v);s.sort(Double::compare);double med=s.get(s.size()/2);List<Double>d=new ArrayList<>();for(double x:v)d.add(Math.abs(x-med));d.sort(Double::compare);return Math.max(1,d.get(d.size()/2)*1.4826);}
    private static double clamp(double v,double lo,double hi){return Math.max(lo,Math.min(hi,v));}
    private record PendingForecast(Instant time,double lowMean,double lowQ25,double lowQ75,double highMean,double highQ25,double highQ75){}
}
