package com.ksp.geflipper.transactions;

import com.ksp.geflipper.analytics.CalibrationService;
import com.ksp.geflipper.marketdata.WikiMarketDataService;
import com.ksp.geflipper.model.Models.*;
import com.ksp.geflipper.persistence.Store;
import com.ksp.geflipper.portfolio.PortfolioService;

import java.time.Duration;
import java.time.Instant;

public final class TransactionService {
    private final Store store;
    private final PortfolioService portfolio;
    private final CalibrationService calibration;
    private final WikiMarketDataService market;

    public TransactionService(Store store,PortfolioService portfolio,CalibrationService calibration,WikiMarketDataService market){
        this.store=store;this.portfolio=portfolio;this.calibration=calibration;this.market=market;
    }

    public Position record(TradeExecution execution){
        store.saveExecution(execution);
        if(execution.suggestionId()!=null)store.markRecommendationStatus(execution.suggestionId(),"EXECUTED");
        Position position=portfolio.applyExecution(execution);
        store.linkPositionExecution(position.id(),execution.id());
        if(execution.recommendationOriginatedTrade())updateAcceptance(execution.accountKey());

        store.recommendation(execution.suggestionId()).ifPresent(s->{
            if(execution.firstFillAt()!=null&&execution.fullFillAt()!=null){
                long actual=Math.max(1,Duration.between(execution.firstFillAt(),execution.fullFillAt()).getSeconds());
                long volume=market.snapshot(execution.itemId()).map(MarketSnapshot::matchedHourlyVolume).orElse(0L);
                calibration.recordDurationSample(execution.side(),volume,s.expectedDurationSeconds(),actual);
            }
        });

        if(execution.side()==Side.SELL&&position.status()==PositionStatus.CLOSED&&position.sourceSuggestionId()!=null){
            store.recommendation(position.sourceSuggestionId()).ifPresent(source->{
                long actualDuration=Math.max(1,Duration.between(position.openedAt(),execution.timestamp()).getSeconds());
                double avgSell=position.closedQuantity()<=0?execution.price():position.totalSellRevenue()/(double)position.closedQuantity();
                String status=store.recommendationStatus(position.sourceSuggestionId());
                boolean modified=source.type()==SuggestionType.MODIFY_BUY||status.contains("MODIFIED");
                boolean aborted=status.contains("ABORT");
                calibration.recordOutcome(new RecommendationOutcome(source.id(),execution.accountKey(),execution.itemId(),
                        source.expectedProfit(),position.realizedProfit(),source.expectedDurationSeconds(),actualDuration,
                        source.price(),avgSell,modified,aborted,Instant.now()));
                store.markRecommendationStatus(source.id(),"CLOSED");
            });
        }
        return position;
    }

    private void updateAcceptance(String account){
        var tx=store.executions(account,1000);if(tx.isEmpty())return;
        double accepted=tx.stream().filter(TradeExecution::recommendationOriginatedTrade).count()/(double)tx.size();
        store.saveMetric("recommendation.acceptance",accepted,tx.size());
    }
}
