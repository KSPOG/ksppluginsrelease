package com.ksp.geflipper.persistence;

import com.ksp.geflipper.model.Models.*;

import java.util.*;

public interface Store extends AutoCloseable {
    void saveAccount(AccountState account);
    void saveItem(ItemMeta item);
    void saveRawMarket(String endpoint, String payload, java.time.Instant observedAt);
    void saveOffers(String accountKey, List<GeOfferState> offers);
    void saveMarketPoint(MarketPoint point);
    List<MarketPoint> recentMarketPoints(int itemId, int limit);
    void saveForecast(ItemForecast forecast);
    void saveRecommendation(String accountKey, TradeSuggestion suggestion, Map<String,Double> features, StrategyPreferences preferences);
    Optional<TradeSuggestion> recommendation(UUID id);
    void markRecommendationStatus(UUID id, String status);
    String recommendationStatus(UUID id);
    Map<String,Long> recommendationActionCounts();
    void saveExecution(TradeExecution execution);
    List<TradeExecution> executions(String accountKey, int limit);
    void savePosition(Position position);
    void linkPositionExecution(UUID positionId, UUID executionId);
    List<Position> positions(String accountKey);
    void saveDump(DumpSignal signal);
    List<DumpSignal> dumps(int limit);
    void saveOutcome(RecommendationOutcome outcome);
    List<RecommendationOutcome> outcomes(int limit);
    void saveMetric(String key, double value, long sampleCount);
    Map<String,Double> metrics();
    @Override default void close() {}
}
