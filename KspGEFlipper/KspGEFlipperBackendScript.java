package net.runelite.client.plugins.microbot.kspgeflipper;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.world.Rs2WorldUtil;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Slf4j
final class KspGEFlipperBackendScript extends Script {
    private static final int COINS = 995;

    private KspGEFlipperConfig config;
    private KspGEFlipperBackendClient client;
    private KspGEFlipperOfferTracker tracker;
    private KspGEFlipperAccountStateCollector collector;
    private KspGEFlipperTransactionObserver observer;
    private KspGEFlipperSuggestionExecutor executor;
    private KspGEFlipperDumpStream dumpStream;
    private long nextRecommendationAt;
    private int consecutiveErrors;
    private volatile String backendStatus = "Starting";
    private volatile String explanation = "-";

    boolean run(KspGEFlipperConfig config) {
        this.config = config;
        this.client = new KspGEFlipperBackendClient(config.backendUrl(), config.backendApiKey());
        this.tracker = new KspGEFlipperOfferTracker();
        this.collector = new KspGEFlipperAccountStateCollector(tracker);
        KspGEFlipperBackendDtos.AccountState initial = collector.collect(config);
        KspGEFlipperRuntime.accountKey = initial.accountKey;
        this.observer = new KspGEFlipperTransactionObserver(client, tracker, initial.accountKey);
        this.executor = config.executionMode() == KspGEFlipperConfig.ExecutionMode.AUTO
                ? new KspGEFlipperMicrobotExecutor(config, tracker)
                : new KspGEFlipperManualExecutor();
        if (config.enableDumpOpportunities()) {
            this.dumpStream = new KspGEFlipperDumpStream(client);
            dumpStream.start();
        }
        resetOverlay();
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run() || !Microbot.isLoggedIn()) return;
                tick();
            } catch (Exception e) {
                consecutiveErrors++;
                backendStatus = "Error: " + e.getClass().getSimpleName();
                publishRuntimeState();
                KspGEFlipperScript.status = backendStatus;
                log.warn("KSP GE backend tick failed: {}", e.getMessage());
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
        if (now < nextRecommendationAt) {
            publishRuntimeState();
            KspGEFlipperScript.status = executor.status();
            return;
        }
        nextRecommendationAt = now + Math.max(1, config.backendPollSeconds()) * 1000L;

        KspGEFlipperBackendDtos.AccountState account = collector.collect(config);
        updateAccountOverlay(account);
        KspGEFlipperBackendDtos.Suggestion suggestion;
        try {
            suggestion = client.recommendation(account);
            backendStatus = "Connected";
            consecutiveErrors = 0;
        } catch (Exception e) {
            consecutiveErrors++;
            backendStatus = "Unavailable (" + consecutiveErrors + ")";
            publishRuntimeState();
            KspGEFlipperScript.status = backendStatus;
            return;
        }
        if (suggestion == null) return;

        explanation = suggestion.explanation == null ? "-" : suggestion.explanation;
        showSuggestion(suggestion);
        if (suggestion.itemId > 0 && suggestion.id != null) {
            if ("SELL".equalsIgnoreCase(suggestion.type) || "MODIFY_SELL".equalsIgnoreCase(suggestion.type)) tracker.mark(suggestion, "SELL");
            else if ("BUY".equalsIgnoreCase(suggestion.type) || "MODIFY_BUY".equalsIgnoreCase(suggestion.type)) tracker.mark(suggestion, "BUY");
        }
        executor.execute(suggestion);
        publishRuntimeState();
        KspGEFlipperScript.status = executor.status();
    }


    private void updateAccountOverlay(KspGEFlipperBackendDtos.AccountState account) {
        int active=0,buying=0,selling=0;long capital=0;
        for(KspGEFlipperBackendDtos.Offer offer:account.offers){
            if(!offer.active||offer.suggestionId==null)continue;
            active++;
            if("SELL".equalsIgnoreCase(offer.side))selling++;else{buying++;capital+=Math.max(0,offer.offerPrice)*Math.max(0,offer.totalQuantity-offer.filledQuantity);}
        }
        KspGEFlipperScript.activeFlips=active;
        KspGEFlipperScript.buyingFlips=buying;
        KspGEFlipperScript.sellingFlips=selling;
        KspGEFlipperScript.capitalUsed=capital;
    }

    private void publishRuntimeState() {
        KspGEFlipperRuntime.backend = backendStatus;
        KspGEFlipperRuntime.explanation = explanation;
        if (dumpStream == null) {
            KspGEFlipperRuntime.dump = "Off";
        } else {
            KspGEFlipperBackendDtos.DumpSignal signal = dumpStream.latest();
            KspGEFlipperRuntime.dump = signal == null ? dumpStream.status()
                    : signal.name + " " + Math.round(signal.recoveryProbability * 100.0) + "%";
        }
    }

    private void showSuggestion(KspGEFlipperBackendDtos.Suggestion suggestion) {
        String type = suggestion.type == null ? "WAIT" : suggestion.type.toUpperCase(Locale.ROOT);
        KspGEFlipperRuntime.itemId = suggestion.itemId;
        KspGEFlipperScript.bestCandidate = suggestion.name == null ? "-" : suggestion.name;
        KspGEFlipperScript.candidateType = suggestion.candidateType == null
                ? (suggestion.hold ? "BUY_AND_HOLD" : type) : suggestion.candidateType;
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
        KspGEFlipperScript.calibrationStatus = "Server calibrated";
    }

    private void resetOverlay() {
        KspGEFlipperScript.status = "Connecting backend";
        KspGEFlipperScript.bestCandidate = "-";
        KspGEFlipperScript.candidateType = "-";
        KspGEFlipperScript.candidateProfit = KspGEFlipperScript.candidateGpPerHour = 0;
        KspGEFlipperScript.candidateBuy = KspGEFlipperScript.candidateSell = KspGEFlipperScript.candidateQty = 0;
        KspGEFlipperScript.candidateRoi = KspGEFlipperScript.candidateConfidence = 0;
        KspGEFlipperScript.calibrationStatus = "Server";
    }

    String backendStatus() { return backendStatus; }
    String explanation() { return explanation; }
    String dumpStatus() { return dumpStream == null ? "Off" : dumpStream.status(); }
    KspGEFlipperBackendDtos.DumpSignal latestDump() { return dumpStream == null ? null : dumpStream.latest(); }

    @Override
    public void shutdown() {
        if (dumpStream != null) dumpStream.close();
        if (executor != null) executor.shutdown();
        super.shutdown();
    }

    private static int safeInt(long value) { return value <= 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, value); }
}
