package net.runelite.client.plugins.microbot.kspgeflipper;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeSlots;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.grandexchange.models.GrandExchangeOfferDetails;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.world.Rs2WorldUtil;

import java.time.Instant;
import java.util.*;

final class KspGEFlipperAccountStateCollector {
    private static final int COINS = 995;
    private final KspGEFlipperOfferTracker tracker;

    KspGEFlipperAccountStateCollector(KspGEFlipperOfferTracker tracker) { this.tracker = tracker; }

    KspGEFlipperBackendDtos.AccountState collect(KspGEFlipperConfig config) {
        KspGEFlipperBackendDtos.AccountState state = new KspGEFlipperBackendDtos.AccountState();
        state.accountKey = accountKey(config);
        state.worldMember = Rs2WorldUtil.isMemberAccount();
        state.accountMember = state.worldMember;
        state.f2pOnly = config.f2pOnly();
        int detectedSlots = Rs2GrandExchange.getActiveOfferSlots().size() + Math.max(0, Rs2GrandExchange.getAvailableSlotsCount());
        state.totalGeSlots = detectedSlots > 0 ? detectedSlots : Math.max(1, config.maxSlots());
        state.maxPluginSlots = Math.max(1, Math.min(state.totalGeSlots, config.maxSlots()));
        state.inventory = inventory();
        state.bank = bank();
        state.uncollected = uncollected();
        state.gp = state.inventory.getOrDefault(COINS, 0L) + state.bank.getOrDefault(COINS, 0L) + state.uncollected.getOrDefault(COINS, 0L);
        state.offers = offers();
        state.blockedItemNames = names(config.blockedItems());
        state.allowedItemNames = names(config.customItems());

        KspGEFlipperBackendDtos.Strategy strategy = state.strategy;
        strategy.timeframeMinutes = Math.max(5, config.timeframeMinutes());
        strategy.riskLevel = config.riskLevel().name();
        strategy.sellOnly = config.sellOnly();
        strategy.allowBuyAndHold = config.allowBuyAndHold();
        strategy.dumpEnabled = config.enableDumpOpportunities();
        strategy.reservedSlots = Math.max(0, config.reservedSlots());
        strategy.dumpSlots = Math.max(0, config.dumpSlots());
        strategy.minExpectedProfit = Math.max(0, config.minTradeProfit());
        strategy.minDumpExpectedProfit = Math.max(0, config.dumpMinPredictedProfit());
        strategy.maxItemExposurePct = Math.max(1, Math.min(100, config.maxCapitalPercent()));
        strategy.modifyThresholdPct = Math.max(0, config.modifyImprovementPercent());
        strategy.abortThresholdPct = Math.max(0, config.abortDeteriorationPercent());
        return state;
    }

    private List<KspGEFlipperBackendDtos.Offer> offers() {
        List<KspGEFlipperBackendDtos.Offer> result = new ArrayList<>();
        for (GrandExchangeSlots slot : Rs2GrandExchange.getActiveOfferSlots()) {
            GrandExchangeOfferDetails details = Rs2GrandExchange.getOfferDetails(slot);
            if (details == null || details.getItemId() <= 0) continue;
            result.add(toOffer(slot, details, true));
        }
        return result;
    }

    private KspGEFlipperBackendDtos.Offer toOffer(GrandExchangeSlots slot, GrandExchangeOfferDetails details, boolean active) {
        String side = details.isSelling() ? "SELL" : "BUY";
        KspGEFlipperOfferTracker.Times times = tracker.observe(slot, details);
        KspGEFlipperOfferTracker.Attribution attribution = tracker.attribution(details.getItemId(), side);
        KspGEFlipperBackendDtos.Offer offer = new KspGEFlipperBackendDtos.Offer();
        offer.slot = slot.ordinal();
        offer.itemId = details.getItemId();
        offer.side = side;
        offer.offerPrice = details.getPrice();
        offer.totalQuantity = details.getTotalQuantity();
        offer.filledQuantity = details.getQuantitySold();
        offer.amountSpent = details.getSpent();
        offer.active = active;
        offer.firstSeen = times.firstSeen.toString();
        offer.lastChanged = times.lastChanged.toString();
        offer.recommendedPriceUsed = attribution != null && attribution.price == details.getPrice();
        offer.suggestionId = attribution == null ? null : attribution.suggestionId;
        offer.candidateType = attribution == null ? null : attribution.candidateType;
        return offer;
    }

    private static Map<Integer, Long> inventory() {
        Map<Integer, Long> map = new HashMap<>();
        for (Rs2ItemModel item : Rs2Inventory.all()) map.merge(item.getId(), (long) item.getQuantity(), Long::sum);
        return map;
    }

    private static Map<Integer, Long> bank() {
        Map<Integer, Long> map = new HashMap<>();
        for (Rs2ItemModel item : Rs2Bank.bankItems()) map.merge(item.getId(), (long) item.getQuantity(), Long::sum);
        return map;
    }

    private static Map<Integer, Long> uncollected() {
        Map<Integer, Long> map = new HashMap<>();
        for (Map.Entry<GrandExchangeSlots, GrandExchangeOfferDetails> entry : Rs2GrandExchange.getCompletedOffers().entrySet()) {
            GrandExchangeOfferDetails d = entry.getValue();
            if (d == null || d.getQuantitySold() <= 0) continue;
            if (d.isSelling()) {
                long reported = Math.max(0L, d.getSpent());
                if (reported > 0) map.merge(COINS, reported, Long::sum);
            } else map.merge(d.getItemId(), (long) d.getQuantitySold(), Long::sum);
        }
        return map;
    }

    private static Set<String> names(String raw) {
        Set<String> out = new HashSet<>();
        if (raw == null || raw.isBlank()) return out;
        for (String part : raw.split(",")) if (!part.isBlank()) out.add(part.trim().toLowerCase(Locale.ROOT));
        return out;
    }

    private static String accountKey(KspGEFlipperConfig config) {
        if (config.accountKey() != null && !config.accountKey().isBlank()) return config.accountKey().trim();
        try {
            if (Microbot.getClient() != null && Microbot.getClient().getLocalPlayer() != null && Microbot.getClient().getLocalPlayer().getName() != null)
                return Microbot.getClient().getLocalPlayer().getName();
        } catch (Exception ignored) {}
        return "unknown-account";
    }
}
