package net.runelite.client.plugins.microbot.kspgeflipper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static net.runelite.client.plugins.microbot.kspgeflipper.KspGEFlipperEmbeddedModels.*;

abstract class KspGEFlipperEmbeddedPortfolio extends KspGEFlipperEmbeddedPolicy {
    protected KspGEFlipperEmbeddedPortfolio(KspGEFlipperConfig config) { super(config); }

    protected Position applyExecution(KspGEFlipperBackendDtos.TradeExecution e) {
        long now = parseTime(e.timestamp, System.currentTimeMillis());
        List<Position> positions = store.positions(e.accountKey);
        if ("BUY".equalsIgnoreCase(e.side)) {
            Position p = null;
            for (Position candidate : positions) if (candidate.itemId == e.itemId && "OPEN".equals(candidate.status)) { p = candidate; break; }
            if (p == null) {
                p = new Position();
                p.id = UUID.randomUUID().toString();
                p.accountKey = e.accountKey;
                p.itemId = e.itemId;
                p.type = e.recommendationOriginatedTrade ? "COPILOT" : "PERSONAL";
                p.sourceSuggestionId = e.suggestionId;
                p.openedAt = now;
            }
            long buyCost = safeMultiply(e.price, e.quantity);
            p.boughtQuantity += e.quantity;
            p.totalBuyCost += buyCost;
            p.remainingCostBasis += buyCost;
            p.status = "OPEN";
            store.putPosition(p);
            return p;
        }

        Position p = null;
        for (Position candidate : positions) if (candidate.itemId == e.itemId && "OPEN".equals(candidate.status) && candidate.remaining() > 0) { p = candidate; break; }
        if (p == null) {
            p = new Position();
            p.id = UUID.randomUUID().toString();
            p.accountKey = e.accountKey;
            p.itemId = e.itemId;
            p.type = "UNMATCHED";
            p.soldQuantity = e.quantity;
            p.totalSellRevenue = safeMultiply(e.price, e.quantity);
            p.openedAt = now;
            p.closedAt = now;
            p.status = "CLOSED";
            store.putPosition(p);
            return p;
        }
        int remainingBefore = p.remaining();
        int sellQty = Math.min(e.quantity, remainingBefore);
        long allocatedCost = remainingBefore <= 0 ? 0 : p.remainingCostBasis * sellQty / remainingBefore;
        p.realizedCostBasis += allocatedCost;
        p.remainingCostBasis = Math.max(0, p.remainingCostBasis - allocatedCost);
        p.soldQuantity += sellQty;
        p.totalSellRevenue += safeMultiply(e.price, sellQty);
        MarketSnapshot snapshot = market.get(e.itemId);
        p.taxPaid += taxPerItem(snapshot == null ? "" : snapshot.item.name, e.price) * (long) sellQty;
        if (p.remaining() <= 0) {
            p.status = "CLOSED";
            p.closedAt = now;
        }
        store.putPosition(p);
        return p;
    }

    protected PortfolioView portfolio(String accountKey) {
        PortfolioView view = new PortfolioView();
        view.positions = store.positions(accountKey);
        for (Position p : view.positions) {
            view.realized += p.realizedProfit();
            int remaining = p.remaining();
            if (remaining <= 0) continue;
            MarketSnapshot s = market.get(p.itemId);
            if (s == null) continue;
            long proceeds = safeMultiply(postTaxUnit(s.item.name, s.latestHigh), remaining);
            view.unrealized += proceeds - p.remainingCostBasis;
        }
        return view;
    }

    protected void calibrateExecution(KspGEFlipperBackendDtos.TradeExecution e, Position position) {
        RecommendationRecord record = store.recommendation(e.suggestionId);
        if (record == null || record.suggestion == null) return;
        KspGEFlipperBackendDtos.Suggestion s = record.suggestion;
        String type = s.candidateType == null ? "NORMAL_FLIP" : s.candidateType;
        long firstFill = parseTime(e.firstFillAt, parseTime(e.timestamp, System.currentTimeMillis()));
        long fullFill = parseTime(e.fullFillAt, parseTime(e.timestamp, System.currentTimeMillis()));
        long actualFillSeconds = Math.max(1, (fullFill - firstFill) / 1000L);
        double priceError = s.price <= 0 ? 0 : Math.abs(e.price - s.price) / (double) s.price;
        CalibrationBucket global = store.calibration("type:" + type);
        CalibrationBucket item = store.calibration("item:" + e.itemId);
        updateBucket(global, s, e, actualFillSeconds, priceError, false, false);
        updateBucket(item, s, e, actualFillSeconds, priceError, false, false);

        if ("SELL".equalsIgnoreCase(e.side) && position != null && "CLOSED".equals(position.status) && position.sourceSuggestionId != null) {
            RecommendationRecord source = store.recommendation(position.sourceSuggestionId);
            if (source != null && source.suggestion != null) {
                KspGEFlipperBackendDtos.Suggestion original = source.suggestion;
                Outcome o = new Outcome();
                o.recommendationId = original.id;
                o.accountKey = e.accountKey;
                o.itemId = e.itemId;
                o.candidateType = original.candidateType;
                o.predictedProfit = original.expectedProfit;
                o.actualProfit = position.realizedProfit();
                o.predictedDurationSeconds = original.expectedDurationSeconds;
                o.actualDurationSeconds = Math.max(1, (parseTime(e.timestamp, System.currentTimeMillis()) - position.openedAt) / 1000L);
                o.recommendedPrice = original.price;
                o.actualAveragePrice = position.soldQuantity <= 0 ? e.price : position.totalSellRevenue / (double) position.soldQuantity;
                o.modified = "MODIFY_BUY".equals(original.type) || "MODIFY_SELL".equals(original.type) || source.status.contains("MODIFIED");
                o.aborted = source.status.contains("ABORT");
                o.recordedAt = System.currentTimeMillis();
                store.addOutcome(o);
                store.markRecommendation(original.id, "CLOSED");
                updateOutcomeBucket(store.calibration("type:" + safeType(original.candidateType)), o);
                updateOutcomeBucket(store.calibration("item:" + e.itemId), o);
            }
        }
    }

    protected void updateBucket(CalibrationBucket b, KspGEFlipperBackendDtos.Suggestion s, KspGEFlipperBackendDtos.TradeExecution e,
                              long actualFillSeconds, double priceError, boolean modified, boolean aborted) {
        double alpha = clamp(config.calibrationLearningRate(), 0.01, 0.50);
        double predicted = Math.max(1, s.expectedDurationSeconds);
        double durationRatio = actualFillSeconds / predicted;
        b.durationFactor = ewmaValue(b.samples == 0 ? 1 : b.durationFactor, durationRatio, alpha);
        double success = e.quantity > 0 ? 1.0 : 0.0;
        b.fillFactor = ewmaValue(b.samples == 0 ? 1 : b.fillFactor, success, alpha);
        b.priceErrorPct = ewmaValue(b.priceErrorPct, priceError, alpha);
        b.modificationRate = ewmaValue(b.modificationRate, modified ? 1 : 0, alpha);
        b.abortRate = ewmaValue(b.abortRate, aborted ? 1 : 0, alpha);
        b.samples++;
    }

    protected void updateOutcomeBucket(CalibrationBucket b, Outcome o) {
        double alpha = clamp(config.calibrationLearningRate(), 0.01, 0.50);
        double profitRatio = o.predictedProfit == 0 ? 1.0 : o.actualProfit / (double) Math.max(1, Math.abs(o.predictedProfit));
        if (o.predictedProfit < 0) profitRatio = -profitRatio;
        b.profitFactor = ewmaValue(b.samples == 0 ? 1 : b.profitFactor, clamp(profitRatio, 0.2, 1.8), alpha);
        if (o.predictedDurationSeconds > 0 && o.actualDurationSeconds > 0)
            b.durationFactor = ewmaValue(b.durationFactor, o.actualDurationSeconds / (double) o.predictedDurationSeconds, alpha);
        b.modificationRate = ewmaValue(b.modificationRate, o.modified ? 1 : 0, alpha);
        b.abortRate = ewmaValue(b.abortRate, o.aborted ? 1 : 0, alpha);
        b.samples++;
    }


    // ---------------- dump detector ----------------

    protected void detectDumps() {
        if (!config.enableDumpOpportunities()) { activeDumps.clear(); return; }
        long now = System.currentTimeMillis();
        Map<Integer, KspGEFlipperBackendDtos.DumpSignal> fresh = new HashMap<>();
        for (MarketSnapshot s : market.values()) {
            List<MarketPoint> history = store.marketHistory(s.item.id, 256);
            Features f = features(s, history);
            if (f.distanceFromRollingMedian > -Math.max(0.005, config.dumpDropPercent() / 100.0) || f.volumeAcceleration < 1.20 || f.abnormalityScore < 2.0) continue;
            Forecast fc = forecast(s, f, history, Math.min(30, Math.max(5, config.timeframeMinutes())));
            if (fc.low.isEmpty()) continue;
            KspGEFlipperBackendDtos.DumpSignal signal = new KspGEFlipperBackendDtos.DumpSignal();
            signal.id = UUID.randomUUID().toString();
            signal.itemId = s.item.id;
            signal.name = s.item.name;
            signal.detectedAt = Instant.ofEpochMilli(now).toString();
            signal.severity = Math.abs(f.distanceFromRollingMedian) * 100 + Math.max(0, f.volumeAcceleration - 1);
            signal.recoveryProbability = clamp(0.30 + fc.confidence * 0.35 + Math.min(0.20, (f.volumeAcceleration - 1) * 0.08), 0.15, 0.92);
            signal.currentLow = s.latestLow;
            signal.predictedRecoveryPrice = Math.max(s.latestLow + 1, Math.min(s.latestHigh, Math.round(point(fc.low, Math.min(2, fc.low.size() - 1)).mean)));
            signal.estimatedRecoverySeconds = Math.max(60, Math.round(Math.max(5, config.timeframeMinutes() * 0.65) * 60 / signal.recoveryProbability));
            signal.expectedProfit = Math.max(0, postTaxUnit(s.item.name, signal.predictedRecoveryPrice) - s.latestLow);
            signal.volumeAcceleration = f.volumeAcceleration;
            fresh.put(s.item.id, signal);
        }
        activeDumps.clear();
        activeDumps.putAll(fresh);
    }

    // ---------------- helpers ----------------

}
