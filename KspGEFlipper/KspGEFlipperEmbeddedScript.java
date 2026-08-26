package net.runelite.client.plugins.microbot.kspgeflipper;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.world.Rs2WorldUtil;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Runs the complete recommendation backend in-process with the Microbot plugin. */
@Slf4j
final class KspGEFlipperEmbeddedScript extends Script {
    private static final int COINS = 995;

    private KspGEFlipperConfig config;
    private KspGEFlipperEmbeddedEngine engine;
    private KspGEFlipperOfferTracker tracker;
    private KspGEFlipperAccountStateCollector collector;
    private KspGEFlipperTransactionObserver observer;
    private KspGEFlipperSuggestionExecutor executor;
    private long nextRecommendationAt;
    private volatile String status = "Starting embedded engine";
    private volatile String explanation = "-";

    boolean run(KspGEFlipperConfig config) {
        this.config = config;
        this.tracker = new KspGEFlipperOfferTracker();
        this.collector = new KspGEFlipperAccountStateCollector(tracker);
        this.engine = new KspGEFlipperEmbeddedEngine(config);
        this.engine.start();
        KspGEFlipperBackendDtos.AccountState initial = collector.collect(config);
        KspGEFlipperRuntime.accountKey = initial.accountKey;
        this.observer = new KspGEFlipperTransactionObserver(engine, tracker, initial.accountKey);
        this.executor = config.executionMode() == KspGEFlipperConfig.ExecutionMode.AUTO
                ? new KspGEFlipperMicrobotExecutor(config, tracker)
                : new KspGEFlipperManualExecutor();
        resetOverlay();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run() || !Microbot.isLoggedIn()) return;
                tick();
            } catch (Exception e) {
                status = "Embedded error: " + e.getClass().getSimpleName();
                KspGEFlipperRuntime.backend = status;
                KspGEFlipperScript.status = status;
                log.warn("KSP GE embedded tick failed: {}", e.getMessage());
            }
        }, 0, 700, TimeUnit.MILLISECONDS);
        return true;
    }

    private void tick() {
        KspGEFlipperScript.members = Rs2WorldUtil.isMemberAccount();
        KspGEFlipperScript.cash = Rs2Inventory.itemQuantity(COINS);
        observer.poll();
        executor.tick();

        long now = System.currentTimeMillis();
        if (now >= nextRecommendationAt) {
            nextRecommendationAt = now + Math.max(1, config.backendPollSeconds()) * 1000L;
            KspGEFlipperBackendDtos.AccountState account = collector.collect(config);
            KspGEFlipperRuntime.accountKey = account.accountKey;
            updateAccountOverlay(account);
            KspGEFlipperBackendDtos.Suggestion suggestion = engine.recommendation(account);
            status = engine.status();
            if (suggestion != null) {
                explanation = suggestion.explanation == null ? "-" : suggestion.explanation;
                showSuggestion(suggestion);
                if (suggestion.itemId > 0 && suggestion.id != null) {
                    if ("SELL".equalsIgnoreCase(suggestion.type) || "MODIFY_SELL".equalsIgnoreCase(suggestion.type)) tracker.mark(suggestion, "SELL");
                    else if ("BUY".equalsIgnoreCase(suggestion.type) || "MODIFY_BUY".equalsIgnoreCase(suggestion.type)) tracker.mark(suggestion, "BUY");
                }
                executor.execute(suggestion);
            }
        }

        publishRuntimeState();
        KspGEFlipperScript.status = executor.status();
    }

    private void publishRuntimeState() {
        KspGEFlipperRuntime.backend = status;
        KspGEFlipperRuntime.explanation = explanation;
        KspGEFlipperBackendDtos.DumpSignal dump = engine.latestDump();
        KspGEFlipperRuntime.dump = dump == null ? (config.enableDumpOpportunities() ? "Scanning" : "Off")
                : dump.name + " " + Math.round(dump.recoveryProbability * 100.0) + "%";
    }

    private void updateAccountOverlay(KspGEFlipperBackendDtos.AccountState account) {
        int active = 0, buying = 0, selling = 0;
        long capital = 0;
        for (KspGEFlipperBackendDtos.Offer offer : account.offers) {
            if (!offer.active || offer.suggestionId == null) continue;
            active++;
            if ("SELL".equalsIgnoreCase(offer.side)) selling++;
            else {
                buying++;
                capital += Math.max(0, offer.offerPrice) * Math.max(0, offer.totalQuantity - offer.filledQuantity);
            }
        }
        KspGEFlipperScript.activeFlips = active;
        KspGEFlipperScript.buyingFlips = buying;
        KspGEFlipperScript.sellingFlips = selling;
        KspGEFlipperScript.capitalUsed = capital;
    }

    private void showSuggestion(KspGEFlipperBackendDtos.Suggestion suggestion) {
        String type = suggestion.type == null ? "WAIT" : suggestion.type.toUpperCase(Locale.ROOT);
        KspGEFlipperRuntime.itemId = suggestion.itemId;
        KspGEFlipperScript.bestCandidate = suggestion.name == null ? "-" : suggestion.name;
        KspGEFlipperScript.candidateType = suggestion.candidateType == null ? (suggestion.hold ? "BUY_AND_HOLD" : type) : suggestion.candidateType;
        KspGEFlipperScript.candidateQty = Math.max(0, suggestion.quantity);
        if ("SELL".equals(type) || "MODIFY_SELL".equals(type)) {
            KspGEFlipperScript.candidateBuy = 0;
            KspGEFlipperScript.candidateSell = safeInt(suggestion.price);
        } else {
            KspGEFlipperScript.candidateBuy = safeInt(suggestion.price);
            KspGEFlipperScript.candidateSell = safeInt(suggestion.exitPrice);
        }
        KspGEFlipperScript.candidateProfit = suggestion.expectedProfit;
        KspGEFlipperScript.candidateExpectedMinutes = (int) Math.max(0, Math.round(suggestion.expectedDurationSeconds / 60.0));
        KspGEFlipperScript.candidateGpPerHour = Math.round(suggestion.expectedGpPerHour);
        KspGEFlipperScript.candidateConfidence = suggestion.confidence;
        KspGEFlipperScript.candidateRoi = suggestion.price <= 0 || suggestion.quantity <= 0 ? 0
                : suggestion.expectedProfit * 100.0 / Math.max(1.0, suggestion.price * (double) suggestion.quantity);
        KspGEFlipperScript.candidateVolume = 0;
        KspGEFlipperScript.calibrationStatus = "Embedded calibrated";
    }

    private void resetOverlay() {
        KspGEFlipperScript.status = "Starting embedded engine";
        KspGEFlipperScript.bestCandidate = "-";
        KspGEFlipperScript.candidateType = "-";
        KspGEFlipperScript.candidateProfit = KspGEFlipperScript.candidateGpPerHour = 0;
        KspGEFlipperScript.candidateBuy = KspGEFlipperScript.candidateSell = KspGEFlipperScript.candidateQty = 0;
        KspGEFlipperScript.candidateRoi = KspGEFlipperScript.candidateConfidence = 0;
        KspGEFlipperScript.calibrationStatus = "Embedded";
    }

    String engineStatus() { return status; }
    String explanation() { return explanation; }
    KspGEFlipperBackendDtos.DumpSignal latestDump() { return engine == null ? null : engine.latestDump(); }
    JsonAccess data() { return engine == null ? null : new JsonAccess(engine); }

    @Override
    public void shutdown() {
        if (executor != null) executor.shutdown();
        if (engine != null) engine.close();
        super.shutdown();
    }

    static final class JsonAccess {
        private final KspGEFlipperEmbeddedEngine engine;
        JsonAccess(KspGEFlipperEmbeddedEngine engine) { this.engine = engine; }
        com.google.gson.JsonObject portfolio(String accountKey) { return engine.portfolioJson(accountKey); }
        com.google.gson.JsonObject metrics() { return engine.metricsJson(); }
        com.google.gson.JsonObject price(int itemId, int timeframe) { return engine.priceJson(itemId, timeframe); }
    }

    private static int safeInt(long value) { return value <= 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, value); }
}
