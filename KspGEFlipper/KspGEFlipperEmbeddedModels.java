package net.runelite.client.plugins.microbot.kspgeflipper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Internal Java-11 compatible models for the embedded recommendation core. */
final class KspGEFlipperEmbeddedModels {
    private KspGEFlipperEmbeddedModels() {}

    static final class ItemMeta {
        int id;
        String name;
        boolean members;
        int buyLimit;
        boolean tradeable;
    }

    static final class MarketPoint {
        int itemId;
        long timestamp;
        long high;
        long low;
        long highVolume;
        long lowVolume;
        String resolution;
    }

    static final class MarketSnapshot {
        ItemMeta item;
        long observedAt;
        long latestHigh;
        long latestLow;
        long latestHighTime;
        long latestLowTime;
        long avg5mHigh;
        long avg5mLow;
        long volume5mHigh;
        long volume5mLow;
        long avg1hHigh;
        long avg1hLow;
        long volume1hHigh;
        long volume1hLow;
        long dailyVolume;

        long matchedHourlyVolume() { return Math.max(0L, Math.min(volume1hHigh, volume1hLow)); }
        double mid() { return latestHigh > 0 && latestLow > 0 ? (latestHigh + latestLow) / 2.0 : 0.0; }
    }

    static final class Features {
        double spread;
        double spreadPct;
        double return5m;
        double return1h;
        double return24h;
        double volatility1h;
        double volumeAcceleration;
        double priceVelocity;
        double distanceFromRollingMedian;
        double highLowImbalance;
        double liquidityScore;
        double abnormalityScore;
        double dataAgeSeconds;
    }

    static final class ForecastPoint {
        long time;
        double mean;
        double q25;
        double q75;
    }

    static final class Forecast {
        int itemId;
        List<ForecastPoint> low = new ArrayList<>();
        List<ForecastPoint> high = new ArrayList<>();
        double confidence;
        String quality = "INVALID";
        double robustSigmaPct;
        long generatedAt;
    }

    static final class Candidate {
        int itemId;
        String name;
        String type;
        long buy;
        long sell;
        int quantity;
        long expectedGrossProfit;
        long expectedNetProfit;
        double expectedMinutes;
        double gpPerHour;
        double riskScore;
        double confidence;
        double fillProbability;
        double liquidityScore;
        double volatilityScore;
        double utility;
        boolean hold;
        String explanation;
    }

    static final class Position {
        String id;
        String accountKey;
        int itemId;
        String type;
        String sourceSuggestionId;
        int boughtQuantity;
        int soldQuantity;
        long totalBuyCost;
        long remainingCostBasis;
        long realizedCostBasis;
        long totalSellRevenue;
        long taxPaid;
        long openedAt;
        long closedAt;
        String status = "OPEN";

        int remaining() { return Math.max(0, boughtQuantity - soldQuantity); }
        long averageBuyPrice() {
            int remaining = remaining();
            return remaining <= 0 ? 0L : remainingCostBasis / remaining;
        }
        long realizedProfit() {
            if (boughtQuantity <= 0) return 0L;
            return totalSellRevenue - taxPaid - realizedCostBasis;
        }
    }

    static final class RecommendationRecord {
        KspGEFlipperBackendDtos.Suggestion suggestion;
        String accountKey;
        String status = "ISSUED";
        Map<String, Double> features = new HashMap<>();
        long createdAt;
    }

    static final class ExecutionRecord {
        KspGEFlipperBackendDtos.TradeExecution execution;
        long recordedAt;
    }

    static final class Outcome {
        String recommendationId;
        String accountKey;
        int itemId;
        String candidateType;
        long predictedProfit;
        long actualProfit;
        long predictedDurationSeconds;
        long actualDurationSeconds;
        long recommendedPrice;
        double actualAveragePrice;
        boolean modified;
        boolean aborted;
        long recordedAt;
    }

    static final class CalibrationBucket {
        long samples;
        double durationFactor = 1.0;
        double fillFactor = 1.0;
        double profitFactor = 1.0;
        double priceErrorPct;
        double modificationRate;
        double abortRate;
    }

    static final class Metric {
        double value;
        long samples;
    }

    static final class PersistedState {
        Map<String, Position> positions = new HashMap<>();
        Map<String, RecommendationRecord> recommendations = new HashMap<>();
        List<ExecutionRecord> executions = new ArrayList<>();
        List<Outcome> outcomes = new ArrayList<>();
        Map<String, CalibrationBucket> calibration = new HashMap<>();
        Map<String, Metric> metrics = new HashMap<>();
        Map<Integer, List<MarketPoint>> history = new HashMap<>();
        Map<Integer, Forecast> forecasts = new HashMap<>();
    }
}
