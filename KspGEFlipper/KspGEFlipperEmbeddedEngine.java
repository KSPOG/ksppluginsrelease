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

/** In-process orchestration facade for the embedded GE intelligence stack. */
final class KspGEFlipperEmbeddedEngine extends KspGEFlipperEmbeddedPortfolio implements KspGEFlipperExecutionSink, AutoCloseable {
    KspGEFlipperEmbeddedEngine(KspGEFlipperConfig config) { super(config); }

    synchronized void start() {
        refreshMarket(true);
        validatePersistentState();
    }

    synchronized KspGEFlipperBackendDtos.Suggestion recommendation(KspGEFlipperBackendDtos.AccountState account) {
        refreshMarket(false);
        if (account == null) return waitSuggestion("Account state unavailable.");
        if (market.isEmpty()) return waitSuggestion("Embedded market data unavailable: " + lastMarketError);

        PortfolioView portfolio = portfolio(account.accountKey);
        List<Candidate> entries = generateCandidates(account);
        List<Candidate> exits = exitCandidates(account, portfolio);
        KspGEFlipperBackendDtos.Suggestion suggestion = decide(account, entries, exits);
        if (suggestion == null) suggestion = waitSuggestion("No action selected.");
        persistRecommendation(account, suggestion);
        lastSuggestion = suggestion;
        updateRecommendationMetrics();
        periodicSave();
        return suggestion;
    }

    @Override
    public synchronized void transaction(KspGEFlipperBackendDtos.TradeExecution execution) {
        if (execution == null || execution.itemId <= 0 || execution.quantity <= 0) return;
        store.addExecution(execution);
        if (execution.suggestionId != null) store.markRecommendation(execution.suggestionId, "EXECUTED");
        Position position = applyExecution(execution);
        calibrateExecution(execution, position);
        updateAcceptance(execution.accountKey);
        store.saveIfDirty();
    }

    synchronized String status() {
        if (market.isEmpty()) return "Embedded market unavailable";
        long age = Math.max(0, (System.currentTimeMillis() - lastMarketSuccess) / 1000L);
        return "Embedded ready (market " + age + "s)";
    }

    synchronized String explanation() {
        return lastSuggestion == null || lastSuggestion.explanation == null ? "-" : lastSuggestion.explanation;
    }

    synchronized KspGEFlipperBackendDtos.DumpSignal latestDump() {
        KspGEFlipperBackendDtos.DumpSignal best = null;
        for (KspGEFlipperBackendDtos.DumpSignal signal : activeDumps.values()) {
            if (best == null || signal.severity > best.severity) best = signal;
        }
        return best;
    }

    synchronized JsonObject portfolioJson(String accountKey) {
        PortfolioView view = portfolio(accountKey);
        JsonObject root = new JsonObject();
        root.addProperty("realizedProfit", view.realized);
        root.addProperty("unrealizedProfit", view.unrealized);
        JsonArray array = new JsonArray();
        for (Position p : view.positions) {
            JsonObject o = new JsonObject();
            o.addProperty("id", p.id);
            o.addProperty("itemId", p.itemId);
            o.addProperty("type", p.type);
            o.addProperty("status", p.status);
            o.addProperty("openQuantity", p.boughtQuantity);
            o.addProperty("closedQuantity", p.soldQuantity);
            o.addProperty("totalBuyCost", p.totalBuyCost);
            o.addProperty("remainingCostBasis", p.remainingCostBasis);
            o.addProperty("realizedCostBasis", p.realizedCostBasis);
            o.addProperty("totalSellRevenue", p.totalSellRevenue);
            o.addProperty("taxPaid", p.taxPaid);
            o.addProperty("realizedProfit", p.realizedProfit());
            array.add(o);
        }
        root.add("positions", array);
        return root;
    }

    synchronized JsonObject metricsJson() {
        JsonObject root = new JsonObject();
        JsonObject calibration = new JsonObject();
        List<Outcome> outcomes = store.outcomes(5000);
        double durationAbs = 0, durationPct = 0, profitAbs = 0;
        long durationN = 0;
        for (Outcome o : outcomes) {
            profitAbs += Math.abs(o.predictedProfit - o.actualProfit);
            if (o.predictedDurationSeconds > 0 && o.actualDurationSeconds > 0) {
                durationAbs += Math.abs(o.predictedDurationSeconds - o.actualDurationSeconds);
                durationPct += Math.abs(o.actualDurationSeconds - o.predictedDurationSeconds) / (double) o.actualDurationSeconds;
                durationN++;
            }
        }
        calibration.addProperty("samples", outcomes.size());
        calibration.addProperty("durationMaeSeconds", durationN == 0 ? 0 : durationAbs / durationN);
        calibration.addProperty("durationMape", durationN == 0 ? 0 : durationPct / durationN);
        calibration.addProperty("profitMae", outcomes.isEmpty() ? 0 : profitAbs / outcomes.size());
        calibration.addProperty("forecastMae", metricValue("forecast.mae"));
        calibration.addProperty("iqrCoverage", metricValue("forecast.iqrCoverage"));
        calibration.addProperty("recommendationAcceptanceRate", metricValue("recommendation.acceptance"));
        calibration.addProperty("modifyRate", actionRate("MODIFY"));
        calibration.addProperty("abortRate", actionRate("ABORT"));
        root.add("calibration", calibration);

        JsonObject buckets = new JsonObject();
        for (Map.Entry<String, CalibrationBucket> e : store.calibration().entrySet()) {
            JsonObject b = new JsonObject();
            CalibrationBucket c = e.getValue();
            b.addProperty("samples", c.samples);
            b.addProperty("durationFactor", c.durationFactor);
            b.addProperty("fillFactor", c.fillFactor);
            b.addProperty("profitFactor", c.profitFactor);
            b.addProperty("priceErrorPct", c.priceErrorPct);
            b.addProperty("modificationRate", c.modificationRate);
            b.addProperty("abortRate", c.abortRate);
            buckets.add(e.getKey(), b);
        }
        root.add("buckets", buckets);
        return root;
    }

    synchronized JsonObject priceJson(int itemId, int timeframeMinutes) {
        MarketSnapshot snapshot = market.get(itemId);
        JsonObject root = new JsonObject();
        if (snapshot == null) return root;
        List<MarketPoint> history = store.marketHistory(itemId, 512);
        Features features = features(snapshot, history);
        Forecast forecast = forecast(snapshot, features, history, timeframeMinutes);
        root.add("current", marketJson(snapshot));
        root.add("features", featuresJson(features));
        root.add("forecast", forecastJson(forecast));
        JsonArray hist = new JsonArray();
        for (MarketPoint p : history) {
            JsonObject o = new JsonObject();
            o.addProperty("timestamp", Instant.ofEpochMilli(p.timestamp).toString());
            o.addProperty("highPrice", p.high);
            o.addProperty("lowPrice", p.low);
            o.addProperty("resolution", p.resolution);
            hist.add(o);
        }
        root.add("history", hist);
        return root;
    }

    @Override
    public synchronized void close() {
        store.saveNow();
    }

}
