package com.ksp.geflipper.portfolio;

import com.ksp.geflipper.executionmodel.*;
import com.ksp.geflipper.features.FeatureEngine;
import com.ksp.geflipper.forecasting.ForecastService;
import com.ksp.geflipper.marketdata.WikiMarketDataService;
import com.ksp.geflipper.model.Models.*;
import com.ksp.geflipper.persistence.Store;

import java.time.*;
import java.util.*;

public final class PortfolioService {
    private final Store store; private final WikiMarketDataService market; private final GeTaxService tax; private final FeatureEngine features; private final ForecastService forecasts; private final ExecutionModel execution;
    public PortfolioService(Store store,WikiMarketDataService market,GeTaxService tax,FeatureEngine features,ForecastService forecasts,ExecutionModel execution){this.store=store;this.market=market;this.tax=tax;this.features=features;this.forecasts=forecasts;this.execution=execution;}

    public PortfolioSnapshot snapshot(String accountKey){
        List<Position> positions=store.positions(accountKey); long realized=0,unrealized=0;
        for(Position p:positions){realized+=p.realizedProfit();int remaining=Math.max(0,p.openQuantity()-p.closedQuantity());if(remaining>0){MarketSnapshot s=market.snapshot(p.itemId()).orElse(null);if(s!=null){long proceeds=tax.postTaxUnitPrice(s.item().name(),s.latestHigh())*remaining;long unitCost=p.openQuantity()<=0?0:p.totalBuyCost()/p.openQuantity();unrealized+=proceeds-unitCost*remaining;}}}
        return new PortfolioSnapshot(realized,unrealized,positions);
    }

    public Position applyExecution(TradeExecution e){
        List<Position> all=store.positions(e.accountKey());
        if(e.side()==Side.BUY){
            Position existing=all.stream().filter(p->p.itemId()==e.itemId()&&p.status()==PositionStatus.OPEN).findFirst().orElse(null);
            PositionType type=store.recommendation(e.suggestionId()).map(s->PositionType.COPILOT).orElse(e.recommendationOriginatedTrade()?PositionType.COPILOT:PositionType.PERSONAL);
            Position next=existing==null?new Position(UUID.randomUUID(),e.accountKey(),e.itemId(),type,e.suggestionId(),e.quantity(),0,e.price()*e.quantity(),0,0,e.timestamp(),null,PositionStatus.OPEN)
                    :new Position(existing.id(),existing.accountKey(),existing.itemId(),existing.type(),existing.sourceSuggestionId()!=null?existing.sourceSuggestionId():e.suggestionId(),existing.openQuantity()+e.quantity(),existing.closedQuantity(),existing.totalBuyCost()+e.price()*e.quantity(),existing.totalSellRevenue(),existing.taxPaid(),existing.openedAt(),null,PositionStatus.OPEN);
            store.savePosition(next);return next;
        }
        Position existing=all.stream().filter(p->p.itemId()==e.itemId()&&p.status()==PositionStatus.OPEN&&p.openQuantity()>p.closedQuantity()).findFirst().orElse(null);
        if(existing==null){ Position p=new Position(UUID.randomUUID(),e.accountKey(),e.itemId(),PositionType.UNMATCHED,null,0,e.quantity(),0,e.price()*e.quantity(),0,e.timestamp(),e.timestamp(),PositionStatus.CLOSED);store.savePosition(p);return p; }
        int sellQty=Math.min(e.quantity(),Math.max(0,existing.openQuantity()-existing.closedQuantity())); MarketSnapshot s=market.snapshot(e.itemId()).orElse(null);String name=s==null?"":s.item().name();long taxPaid=tax.taxPerItem(name,e.price())*sellQty;int closed=existing.closedQuantity()+sellQty;PositionStatus status=closed>=existing.openQuantity()?PositionStatus.CLOSED:PositionStatus.OPEN;Position next=new Position(existing.id(),existing.accountKey(),existing.itemId(),existing.type(),existing.sourceSuggestionId(),existing.openQuantity(),closed,existing.totalBuyCost(),existing.totalSellRevenue()+e.price()*sellQty,existing.taxPaid()+taxPaid,existing.openedAt(),status==PositionStatus.CLOSED?e.timestamp():null,status);store.savePosition(next);return next;
    }

    public List<FlipCandidate> exitCandidates(AccountState account){
        List<FlipCandidate> out=new ArrayList<>();
        for(Position p:store.positions(account.accountKey())){
            int remaining=Math.max(0,p.openQuantity()-p.closedQuantity());if(remaining<=0)continue;long available=account.ownedQuantity(p.itemId());int qty=(int)Math.min(remaining,available);if(qty<=0)continue;
            MarketSnapshot s=market.snapshot(p.itemId()).orElse(null);if(s==null||s.latestHigh()<=0)continue;List<MarketPoint> h=market.history(p.itemId(),256);MarketFeatures f=features.extract(s,h);ItemForecast fc=forecasts.forecast(s,f,h,account.strategy().timeframeMinutes(),300);
            long unitCost=p.openQuantity()<=0?0:p.totalBuyCost()/p.openQuantity();long breakEven=tax.minimumBreakEvenSell(s.item().name(),unitCost);long forecastTarget=fc.high().isEmpty()?s.latestHigh():Math.round(fc.highAtOrLast(Math.min(1,fc.high().size()-1)).mean());long sell=Math.max(breakEven,Math.min(s.latestHigh(),forecastTarget));long unitNet=tax.postTaxUnitPrice(s.item().name(),sell)-unitCost;long profit=unitNet*qty;ExecutionModel.Estimate estimate=execution.estimate(s,f,Side.SELL,sell,qty,fc.confidence());double gph=profit*60.0/Math.max(1,estimate.expectedMinutes())*estimate.fillProbability();double utility=gph*fc.confidence()*estimate.fillProbability();boolean shouldExit=account.strategy().sellOnly()||profit>0||p.type()==PositionType.UNMATCHED;
            if(shouldExit)out.add(new FlipCandidate(p.itemId(),s.item().name(),CandidateType.POSITION_EXIT,0,sell,qty,profit,profit,estimate.expectedMinutes(),gph,fc.robustSigmaPct(),fc.confidence(),estimate.fillProbability(),f.liquidityScore(),f.volatility1h(),utility,false,"position exit: remaining="+remaining+", unitCost="+unitCost+", target="+sell));
        }
        out.sort(Comparator.comparingDouble(FlipCandidate::utility).reversed());return out;
    }
}
