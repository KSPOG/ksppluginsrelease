package com.ksp.geflipper.selftest;

import com.ksp.geflipper.executionmodel.GeTaxService;
import com.ksp.geflipper.features.FeatureEngine;
import com.ksp.geflipper.forecasting.ForecastService;
import com.ksp.geflipper.model.Models.*;
import com.ksp.geflipper.util.Json;

import java.time.Instant;
import java.util.*;

public final class SelfTest {
    private SelfTest() {}
    public static void main(String[] args) {
        Object parsed=Json.parse(Json.stringify(Map.of("a",1,"b",List.of(true,"x"))));require(parsed instanceof Map,"json roundtrip");
        GeTaxService tax=new GeTaxService();require(tax.taxPerItem("Rune platebody",100_000)==2_000,"2% tax");require(tax.taxPerItem("Hammer",100_000)==0,"exempt tax");
        ItemMeta item=new ItemMeta(1,"Test item",false,1000,true);Instant now=Instant.now();MarketSnapshot s=new MarketSnapshot(item,now,110,100,now,now,108,101,500,500,107,99,5000,5000,100000);List<MarketPoint> history=new ArrayList<>();for(int i=30;i>=1;i--)history.add(new MarketPoint(1,now.minusSeconds(i*300L),105+i/10,98+i/10,400,400,"5M"));FeatureEngine features=new FeatureEngine();MarketFeatures f=features.extract(s,history);ItemForecast fc=new ForecastService().forecast(s,f,history,30,300);require(!fc.low().isEmpty()&&!fc.high().isEmpty(),"forecast points");require(fc.low().get(0).q25()<=fc.low().get(0).mean()&&fc.low().get(0).mean()<=fc.low().get(0).q75(),"quantile order");System.out.println("KSP GE Flipper server self-test: PASS");
    }
    private static void require(boolean condition,String name){if(!condition)throw new IllegalStateException("Self-test failed: "+name);}
}
