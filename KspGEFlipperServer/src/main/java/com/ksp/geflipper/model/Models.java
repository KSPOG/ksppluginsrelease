package com.ksp.geflipper.model;

import java.time.Instant;
import java.util.*;

public final class Models {
    private Models() {}

    public enum Side { BUY, SELL }
    public enum RiskLevel { LOW, MEDIUM, HIGH }
    public enum SuggestionType { BUY, SELL, MODIFY_BUY, MODIFY_SELL, ABORT, WAIT }
    public enum CandidateType { NORMAL_FLIP, DUMP, BUY_AND_HOLD, POSITION_EXIT }
    public enum ForecastQuality { GOOD, LIMITED, STALE, INVALID }
    public enum PositionType { COPILOT, PERSONAL, UNMATCHED }
    public enum PositionStatus { OPEN, CLOSED }

    public record StrategyPreferences(
            int timeframeMinutes,
            RiskLevel riskLevel,
            boolean sellOnly,
            boolean allowBuyAndHold,
            boolean dumpEnabled,
            int reservedSlots,
            int dumpSlots,
            long minExpectedProfit,
            long minDumpExpectedProfit,
            double maxItemExposurePct,
            double modifyThresholdPct,
            double abortThresholdPct
    ) {
        public static StrategyPreferences defaults() {
            return new StrategyPreferences(30, RiskLevel.MEDIUM, false, false, false, 0, 1,
                    1_000, 5_000, 30.0, 10.0, 20.0);
        }
    }

    public record GeOfferState(
            int slot,
            int itemId,
            Side side,
            long offerPrice,
            int totalQuantity,
            int filledQuantity,
            long amountSpent,
            boolean active,
            Instant firstSeen,
            Instant lastChanged,
            boolean recommendedPriceUsed,
            UUID suggestionId,
            CandidateType candidateType
    ) {}

    public record PortfolioSnapshot(long realizedProfit, long unrealizedProfit, List<Position> positions) {
        public static PortfolioSnapshot empty() { return new PortfolioSnapshot(0, 0, List.of()); }
    }

    public record AccountState(
            String accountKey,
            boolean worldMember,
            boolean accountMember,
            boolean f2pOnly,
            int totalGeSlots,
            int maxPluginSlots,
            long gp,
            Map<Integer, Long> inventory,
            Map<Integer, Long> bank,
            Map<Integer, Long> uncollected,
            Map<Integer, Long> otherStorage,
            List<GeOfferState> offers,
            Set<Integer> blockedItems,
            Set<String> blockedItemNames,
            Set<String> allowedItemNames,
            StrategyPreferences strategy,
            PortfolioSnapshot portfolio
    ) {
        public long ownedQuantity(int itemId) {
            return inventory.getOrDefault(itemId, 0L) + bank.getOrDefault(itemId, 0L)
                    + uncollected.getOrDefault(itemId, 0L) + otherStorage.getOrDefault(itemId, 0L);
        }
    }

    public record ItemMeta(int itemId, String name, boolean members, int buyLimit, boolean tradeable) {}

    public record MarketPoint(
            int itemId,
            Instant timestamp,
            long highPrice,
            long lowPrice,
            long highVolume,
            long lowVolume,
            String resolution
    ) {}

    public record MarketSnapshot(
            ItemMeta item,
            Instant observedAt,
            long latestHigh,
            long latestLow,
            Instant latestHighTime,
            Instant latestLowTime,
            long avg5mHigh,
            long avg5mLow,
            long volume5mHigh,
            long volume5mLow,
            long avg1hHigh,
            long avg1hLow,
            long volume1hHigh,
            long volume1hLow,
            long dailyVolume
    ) {
        public long matchedHourlyVolume() { return Math.max(0, Math.min(volume1hHigh, volume1hLow)); }
        public long mid() { return latestHigh > 0 && latestLow > 0 ? (latestHigh + latestLow) / 2 : 0; }
    }

    public record MarketFeatures(
            int itemId,
            double spread,
            double spreadPct,
            double return5m,
            double return1h,
            double return24h,
            double volatility1h,
            double volumeAcceleration,
            double priceVelocity,
            double distanceFromRollingMedian,
            double highLowImbalance,
            double liquidityScore,
            double abnormalityScore,
            double dataAgeSeconds
    ) {}

    public record ForecastPoint(Instant time, double mean, double q25, double q75) {}

    public record ItemForecast(
            int itemId,
            List<ForecastPoint> low,
            List<ForecastPoint> high,
            double confidence,
            ForecastQuality quality,
            double robustSigmaPct,
            Instant generatedAt
    ) {
        public ForecastPoint lowAtOrLast(int index) { return low.get(Math.min(Math.max(index, 0), low.size() - 1)); }
        public ForecastPoint highAtOrLast(int index) { return high.get(Math.min(Math.max(index, 0), high.size() - 1)); }
    }

    public record FlipCandidate(
            int itemId,
            String name,
            CandidateType type,
            long proposedBuy,
            long proposedSell,
            int quantity,
            long expectedGrossProfit,
            long expectedNetProfit,
            double expectedMinutes,
            double gpPerHour,
            double riskScore,
            double confidence,
            double fillProbability,
            double liquidityScore,
            double volatilityScore,
            double utility,
            boolean hold,
            String explanation
    ) {}

    public record TradeSuggestion(
            UUID id,
            SuggestionType type,
            CandidateType candidateType,
            int slot,
            int itemId,
            String name,
            long price,
            long exitPrice,
            int quantity,
            long expectedProfit,
            long expectedDurationSeconds,
            double expectedGpPerHour,
            double confidence,
            boolean hold,
            String explanation,
            Instant generatedAt,
            long marketAgeSeconds
    ) {
        public static TradeSuggestion waitSuggestion(String explanation) {
            return new TradeSuggestion(UUID.randomUUID(), SuggestionType.WAIT, null, -1, -1, "-", 0, 0, 0,
                    0, 0, 0, 0, false, explanation, Instant.now(), 0);
        }
    }

    public record TradeExecution(
            UUID id,
            String accountKey,
            int itemId,
            Side side,
            long price,
            int quantity,
            long amountSpent,
            Instant timestamp,
            UUID suggestionId,
            boolean recommendationPriceUsed,
            boolean recommendationOriginatedTrade,
            Instant firstFillAt,
            Instant fullFillAt
    ) {}

    public record Position(
            UUID id,
            String accountKey,
            int itemId,
            PositionType type,
            UUID sourceSuggestionId,
            int openQuantity,
            int closedQuantity,
            long totalBuyCost,
            long totalSellRevenue,
            long taxPaid,
            Instant openedAt,
            Instant closedAt,
            PositionStatus status
    ) {
        public long averageBuyPrice() { return openQuantity <= 0 ? 0 : totalBuyCost / openQuantity; }
        public long realizedProfit() { return totalSellRevenue - taxPaid - proportionalClosedCost(); }
        private long proportionalClosedCost() {
            int bought = Math.max(1, openQuantity);
            return totalBuyCost * Math.min(closedQuantity, openQuantity) / bought;
        }
    }

    public record DumpSignal(
            UUID id,
            int itemId,
            String name,
            Instant detectedAt,
            double severity,
            double recoveryProbability,
            long currentLow,
            long predictedRecoveryPrice,
            long estimatedRecoverySeconds,
            long expectedProfit,
            double volumeAcceleration
    ) {}

    public record RecommendationOutcome(
            UUID recommendationId,
            String accountKey,
            int itemId,
            long predictedProfit,
            long actualProfit,
            long predictedDurationSeconds,
            long actualDurationSeconds,
            long recommendedPrice,
            double actualAveragePrice,
            boolean modified,
            boolean aborted,
            Instant recordedAt
    ) {}

    public record CalibrationMetrics(
            long samples,
            double durationMaeSeconds,
            double durationMape,
            double profitMae,
            double forecastMae,
            double iqrCoverage,
            double recommendationAcceptanceRate,
            double modifyRate,
            double abortRate,
            Instant calculatedAt
    ) {}
}
