package net.runelite.client.plugins.microbot.kspgeflipper;

import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeSlots;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.grandexchange.models.GrandExchangeOfferDetails;

import java.time.Instant;
import java.util.*;

final class KspGEFlipperTransactionObserver {
    private final KspGEFlipperExecutionSink sink;
    private final KspGEFlipperOfferTracker tracker;
    private final String accountKey;
    private final LinkedHashSet<String> sent = new LinkedHashSet<>();

    KspGEFlipperTransactionObserver(KspGEFlipperExecutionSink sink, KspGEFlipperOfferTracker tracker, String accountKey) {
        this.sink = sink; this.tracker = tracker; this.accountKey = accountKey;
    }

    void poll() {
        for (Map.Entry<GrandExchangeSlots, GrandExchangeOfferDetails> entry : Rs2GrandExchange.getCompletedOffers().entrySet()) {
            GrandExchangeOfferDetails d = entry.getValue();
            if (d == null || d.getItemId() <= 0 || d.getQuantitySold() <= 0) continue;
            String side = d.isSelling() ? "SELL" : "BUY";
            String fingerprint = entry.getKey().ordinal() + ":" + d.getItemId() + ":" + side + ":" + d.getQuantitySold() + ":" + d.getSpent() + ":" + d.getState();
            if (sent.contains(fingerprint)) continue;
            try {
                KspGEFlipperOfferTracker.Times times = tracker.observe(entry.getKey(), d);
                KspGEFlipperOfferTracker.Attribution attribution = tracker.attribution(d.getItemId(), side);
                KspGEFlipperBackendDtos.TradeExecution tx = new KspGEFlipperBackendDtos.TradeExecution();
                tx.id = UUID.randomUUID().toString();
                tx.accountKey = accountKey;
                tx.itemId = d.getItemId();
                tx.side = side;
                tx.quantity = d.getQuantitySold();
                tx.price = side.equals("BUY") && d.getSpent() > 0 ? Math.max(1, d.getSpent() / d.getQuantitySold()) : d.getPrice();
                tx.amountSpent = d.getSpent();
                tx.timestamp = Instant.now().toString();
                tx.suggestionId = attribution == null ? null : attribution.suggestionId;
                tx.recommendationPriceUsed = attribution != null && attribution.price == d.getPrice();
                tx.recommendationOriginatedTrade = attribution != null;
                tx.firstFillAt = (times.firstFill == null ? times.firstSeen : times.firstFill).toString();
                tx.fullFillAt = Instant.now().toString();
                sink.transaction(tx);
                remember(fingerprint);
            } catch (Exception ignored) {
                // Retry next poll; telemetry should not stop GE management.
            }
        }
    }

    private void remember(String fingerprint) {
        sent.add(fingerprint);
        while (sent.size() > 512) {
            Iterator<String> it = sent.iterator();
            if (it.hasNext()) { it.next(); it.remove(); }
        }
    }
}
