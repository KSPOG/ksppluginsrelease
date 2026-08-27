package com.ksp.geflipper.features;

import com.ksp.geflipper.model.Models.*;
import java.time.*;
import java.util.*;

public final class FeatureEngine {
    public MarketFeatures extract(MarketSnapshot s,List<MarketPoint> history) {
        double mid=Math.max(1,s.mid()), spread=s.latestHigh()-s.latestLow(), spreadPct=spread/mid;
        double mid5=mid(s.avg5mHigh(),s.avg5mLow(),mid), mid1=mid(s.avg1hHigh(),s.avg1hLow(),mid);
        double r5=(mid-mid5)/Math.max(1,mid5), r1=(mid-mid1)/Math.max(1,mid1);
        List<Double> mids=new ArrayList<>(); for(MarketPoint p:history) if(p.highPrice()>0&&p.lowPrice()>0)mids.add((p.highPrice()+p.lowPrice())/2.0);
        double median=median(mids,mid), sigma=robustSigma(mids,median), distance=(mid-median)/Math.max(1,median);
        double volatility=sigma/Math.max(1,median);
        double recentVol=Math.max(1,s.volume5mHigh()+s.volume5mLow()), hourly=Math.max(1,s.volume1hHigh()+s.volume1hLow());
        double acceleration=(recentVol*12.0)/hourly;
        double imbalance=(s.volume1hHigh()-s.volume1hLow())/(double)Math.max(1,s.volume1hHigh()+s.volume1hLow());
        double liquidity=clamp(Math.log10(s.matchedHourlyVolume()+1)/5.0,0,1);
        double age=Math.max(Duration.between(s.latestHighTime(),Instant.now()).toSeconds(),Duration.between(s.latestLowTime(),Instant.now()).toSeconds());
        double velocity=history.size()<2?r5:velocity(history);
        double abnormality=Math.abs(distance)/Math.max(0.002,volatility)+Math.max(0,acceleration-1)*0.5;
        double r24=historyReturn(history,Duration.ofHours(24));
        return new MarketFeatures(s.item().itemId(),spread,spreadPct,r5,r1,r24,volatility,acceleration,velocity,distance,imbalance,liquidity,abnormality,age);
    }

    public Map<String,Double> asMap(MarketFeatures f){ Map<String,Double> m=new LinkedHashMap<>(); m.put("spread",f.spread());m.put("spreadPct",f.spreadPct());m.put("return5m",f.return5m());m.put("return1h",f.return1h());m.put("return24h",f.return24h());m.put("volatility1h",f.volatility1h());m.put("volumeAcceleration",f.volumeAcceleration());m.put("priceVelocity",f.priceVelocity());m.put("distanceFromRollingMedian",f.distanceFromRollingMedian());m.put("highLowImbalance",f.highLowImbalance());m.put("liquidityScore",f.liquidityScore());m.put("abnormalityScore",f.abnormalityScore());m.put("dataAgeSeconds",f.dataAgeSeconds()); return m; }
    private static double velocity(List<MarketPoint> h){MarketPoint a=h.get(Math.max(0,h.size()-4)),b=h.get(h.size()-1);double am=(a.highPrice()+a.lowPrice())/2.0,bm=(b.highPrice()+b.lowPrice())/2.0;double mins=Math.max(1,Duration.between(a.timestamp(),b.timestamp()).toSeconds()/60.0);return (bm-am)/Math.max(1,am)/mins;}
    private static double historyReturn(List<MarketPoint> h,Duration horizon){if(h.size()<2)return 0;MarketPoint last=h.get(h.size()-1),first=h.get(0);for(int i=h.size()-2;i>=0;i--)if(Duration.between(h.get(i).timestamp(),last.timestamp()).compareTo(horizon)>=0){first=h.get(i);break;}double a=(first.highPrice()+first.lowPrice())/2.0,b=(last.highPrice()+last.lowPrice())/2.0;return (b-a)/Math.max(1,a);}
    private static double mid(long h,long l,double fallback){return h>0&&l>0?(h+l)/2.0:fallback;}
    private static double median(List<Double> v,double fallback){if(v.isEmpty())return fallback;List<Double> s=new ArrayList<>(v);s.sort(Double::compare);int n=s.size();return n%2==1?s.get(n/2):(s.get(n/2-1)+s.get(n/2))/2;}
    private static double robustSigma(List<Double> values,double med){if(values.size()<3)return Math.max(1,med*0.01);List<Double>d=new ArrayList<>();for(double v:values)d.add(Math.abs(v-med));return Math.max(1,median(d,1)*1.4826);}
    private static double clamp(double v,double lo,double hi){return Math.max(lo,Math.min(hi,v));}
}
