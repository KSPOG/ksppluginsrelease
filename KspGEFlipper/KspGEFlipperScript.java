package net.runelite.client.plugins.microbot.kspgeflipper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeSlots;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.grandexchange.models.GrandExchangeOfferDetails;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.world.Rs2WorldUtil;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.Global.sleep;

@Slf4j
public class KspGEFlipperScript extends Script {
    private static final int COINS = 995;
    private static final long FOUR_HOURS = 4 * 60 * 60 * 1000L;
    private static final Set<String> TAX_FREE = Set.of(
            "old school bond", "chisel", "gardening trowel", "glassblowing pipe", "hammer", "needle",
            "pestle and mortar", "rake", "saw", "secateurs", "seed dibber", "shears", "spade", "watering can");

    public static volatile String status = "Idle", bestCandidate = "-", candidateType = "-";
    public static volatile long cash, profit, capitalUsed, candidateProfit, candidateGpPerHour;
    public static volatile int activeFlips, buyingFlips, sellingFlips, completedFlips, candidateBuy, candidateSell,
            candidateQty, candidateVolume, candidateExpectedMinutes, marketItems;
    public static volatile double candidateRoi, candidateConfidence;
    public static volatile boolean members;
    private static volatile long started;

    private final Market market = new Market();
    private final Map<Integer, Flip> flips = new HashMap<>();
    private final Map<Integer, LimitWindow> limits = new HashMap<>();
    private final Map<Integer, Long> cooldowns = new HashMap<>();
    private KspGEFlipperConfig config;
    private long nextBankTry;

    public boolean run(KspGEFlipperConfig config) {
        this.config = config;
        started = System.currentTimeMillis();
        profit = capitalUsed = candidateProfit = candidateGpPerHour = 0;
        activeFlips = buyingFlips = sellingFlips = completedFlips = candidateBuy = candidateSell = candidateQty =
                candidateVolume = candidateExpectedMinutes = marketItems = 0;
        candidateRoi = candidateConfidence = 0;
        candidateType = "-";
        bestCandidate = "-";
        flips.clear();
        limits.clear();
        cooldowns.clear();
        nextBankTry = 0;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run() || !Microbot.isLoggedIn()) return;
                tick();
            } catch (Exception e) {
                status = "Error: " + e.getClass().getSimpleName();
                log.error("GE flipper tick failed", e);
            }
        }, 0, 700, TimeUnit.MILLISECONDS);
        return true;
    }

    private void tick() {
        members = Rs2WorldUtil.isMemberAccount();
        cash = Rs2Inventory.itemQuantity(COINS);
        updateStats();

        market.refreshIfNeeded();
        marketItems = market.items.size();
        if (!ensureCash() || !ensureGe()) return;

        processCompleted();
        reevaluateStaleOffers();
        listPendingBuys();
        listPendingSells();
        fillFreeSlots();

        cash = Rs2Inventory.itemQuantity(COINS);
        updateStats();
        if (flips.isEmpty() && "-".equals(bestCandidate)) status = "Scanning market";
    }

    private boolean ensureCash() {
        if (cash > Math.max(0, config.reserveCoins()) || !flips.isEmpty()) return true;
        long now = System.currentTimeMillis();
        if (now < nextBankTry) {
            status = "Need more coins";
            return false;
        }
        nextBankTry = now + 30_000;
        status = "Loading coins";
        if (Rs2GrandExchange.isOpen()) Rs2GrandExchange.closeExchange();
        if (!Rs2Bank.openBank()) return false;
        Rs2Bank.withdrawAll(COINS);
        sleep(250);
        Rs2Bank.closeBank();
        cash = Rs2Inventory.itemQuantity(COINS);
        if (cash > Math.max(0, config.reserveCoins())) nextBankTry = 0;
        return cash > Math.max(0, config.reserveCoins());
    }

    private boolean ensureGe() {
        if (Rs2GrandExchange.isOpen()) return true;
        status = "Opening GE";
        if (Rs2GrandExchange.openExchange()) return true;
        if (!config.walkToGe() || !Rs2GrandExchange.walkToGrandExchange()) return false;
        return Rs2GrandExchange.openExchange();
    }

    private void processCompleted() {
        for (Map.Entry<GrandExchangeSlots, GrandExchangeOfferDetails> entry : Rs2GrandExchange.getCompletedOffers().entrySet()) {
            GrandExchangeOfferDetails details = entry.getValue();
            Flip flip = flips.get(details.getItemId());
            if (flip == null) continue;

            GrandExchangeOfferState state = details.getState();
            if (!flip.selling && (state == GrandExchangeOfferState.BOUGHT || state == GrandExchangeOfferState.CANCELLED_BUY)) {
                finishBuy(entry.getKey(), details, flip, state == GrandExchangeOfferState.CANCELLED_BUY);
            } else if (flip.selling && (state == GrandExchangeOfferState.SOLD || state == GrandExchangeOfferState.CANCELLED_SELL)) {
                finishSell(entry.getKey(), details, flip, state == GrandExchangeOfferState.SOLD);
            }
        }
    }

    private void finishBuy(GrandExchangeSlots slot, GrandExchangeOfferDetails details, Flip flip, boolean cancelled) {
        int qty = details.getQuantitySold();
        long spent = details.getSpent() > 0 ? details.getSpent() : (long) flip.buyPrice * qty;
        Rs2GrandExchange.collectOffer(slot, false);

        if (qty <= 0) {
            if (cancelled && flip.pendingBuyReprice && !flip.abortRequested) {
                flip.applyPendingBuyPlan();
                flip.changed = System.currentTimeMillis();
                status = "Relisting " + flip.item.name;
                return;
            }
            telemetry("BUY_ABORTED", flip, 0, flip.buyPrice, 0);
            flips.remove(flip.item.id);
            cooldowns.put(flip.item.id, System.currentTimeMillis() + 30_000);
            return;
        }

        useLimit(flip.item.id, qty);
        flip.requestedQty = qty;
        flip.boughtQty = qty;
        flip.buySpent = spent;
        flip.selling = true;
        flip.clearPendingBuyPlan();
        flip.abortRequested = false;
        flip.changed = System.currentTimeMillis();
        telemetry("BUY_FILLED", flip, qty, Math.max(1, (int) (spent / Math.max(1, qty))), spent);
        status = "Bought " + qty + " x " + flip.item.name;
    }

    private void finishSell(GrandExchangeSlots slot, GrandExchangeOfferDetails details, Flip flip, boolean complete) {
        accountSold(flip, details.getQuantitySold());
        Rs2GrandExchange.collectOffer(slot, false);
        if (complete || flip.sold >= flip.boughtQty) {
            telemetry("FLIP_COMPLETED", flip, flip.sold, flip.sellPrice, 0);
            flips.remove(flip.item.id);
            completedFlips++;
            status = "Completed " + flip.item.name;
        } else {
            flip.changed = System.currentTimeMillis();
            flip.reprices++;
        }
    }

    private void reevaluateStaleOffers() {
        long timeout = clamp(config.offerTimeout(), 30, 3600) * 1000L;
        long now = System.currentTimeMillis();

        for (Flip flip : new ArrayList<>(flips.values())) {
            if (now - flip.changed < timeout) continue;
            GrandExchangeSlots slot = Rs2GrandExchange.findSlotForItem(flip.item.id, flip.selling);
            if (slot == null) continue;
            GrandExchangeOfferDetails details = Rs2GrandExchange.getOfferDetails(slot);
            if (details == null || !details.isInProgress()) continue;

            if (flip.selling) {
                reevaluateSell(slot, details, flip, now);
            } else {
                reevaluateBuy(slot, flip, now);
            }
        }
    }

    private void reevaluateBuy(GrandExchangeSlots slot, Flip flip, long now) {
        long budget = Math.max(1L, (long) flip.buyPrice * Math.max(1, flip.requestedQty));
        Candidate fresh = candidateForItem(flip.item, budget, flip.type);
        double abortPct = Math.max(0, config.abortDeteriorationPercent());
        double modifyPct = Math.max(0, config.modifyImprovementPercent());

        if (fresh == null) {
            status = "Aborting stale buy: " + flip.item.name;
            flip.abortRequested = true;
            flip.clearPendingBuyPlan();
            Rs2GrandExchange.cancelSpecificOffers(Collections.singletonList(slot), false);
            flip.changed = now;
            return;
        }

        double deterioration = percentChange(flip.entryUtility, fresh.utility) * -1.0;
        double improvement = percentChange(flip.entryUtility, fresh.utility);
        if (deterioration >= abortPct) {
            status = "Aborting weak buy: " + flip.item.name;
            flip.abortRequested = true;
            flip.clearPendingBuyPlan();
            Rs2GrandExchange.cancelSpecificOffers(Collections.singletonList(slot), false);
            flip.changed = now;
            return;
        }

        if (fresh.buy != flip.buyPrice && improvement >= modifyPct) {
            status = "Improving buy: " + flip.item.name;
            flip.pendingBuyPrice = fresh.buy;
            flip.pendingSellPrice = fresh.sell;
            flip.pendingUtility = fresh.utility;
            flip.pendingExpectedMinutes = fresh.expectedMinutes;
            flip.pendingExpectedGpPerHour = fresh.gpPerHour;
            flip.pendingConfidence = fresh.confidence;
            flip.pendingBuyReprice = true;
            flip.abortRequested = false;
            telemetry("BUY_MODIFY", flip, flip.requestedQty, fresh.buy, 0);
            Rs2GrandExchange.cancelSpecificOffers(Collections.singletonList(slot), false);
            flip.changed = now;
            return;
        }

        status = "Waiting on buy: " + flip.item.name;
        flip.changed = now;
    }

    private void reevaluateSell(GrandExchangeSlots slot, GrandExchangeOfferDetails details, Flip flip, long now) {
        accountSold(flip, details.getQuantitySold());
        if (flip.sold >= flip.boughtQty) return;

        int target = sellPrice(flip);
        double deltaPct = Math.abs(percentChange(flip.sellPrice, target));
        if (target != flip.sellPrice && deltaPct >= Math.max(0, config.sellRepricePercent())) {
            status = "Repricing sell: " + flip.item.name;
            telemetry("SELL_MODIFY", flip, flip.boughtQty - flip.sold, target, 0);
            Rs2GrandExchange.cancelSpecificOffers(Collections.singletonList(slot), false);
            flip.changed = now;
            return;
        }

        status = "Waiting on sell: " + flip.item.name;
        flip.changed = now;
    }

    private void listPendingBuys() {
        for (Flip flip : new ArrayList<>(flips.values())) {
            if (flip.selling || flip.abortRequested || Rs2GrandExchange.findSlotForItem(flip.item.id, false) != null) continue;

            long available = Rs2Inventory.itemQuantity(COINS) - Math.max(0, config.reserveCoins());
            int limit = remainingLimit(flip.item.id, flip.item.limit);
            int qty = (int) Math.min(Math.min(flip.requestedQty, limit), available / Math.max(1, flip.buyPrice));
            if (qty <= 0) {
                flips.remove(flip.item.id);
                cooldowns.put(flip.item.id, System.currentTimeMillis() + 30_000);
                continue;
            }

            status = "Buying " + flip.item.name + " @ " + flip.buyPrice;
            if (Rs2GrandExchange.buyItem(flip.item.name, flip.buyPrice, qty)) {
                flip.requestedQty = qty;
                flip.changed = System.currentTimeMillis();
                telemetry("BUY_RELISTED", flip, qty, flip.buyPrice, 0);
            }
            return;
        }
    }

    private void listPendingSells() {
        for (Flip flip : new ArrayList<>(flips.values())) {
            if (!flip.selling || Rs2GrandExchange.findSlotForItem(flip.item.id, true) != null) continue;
            int remaining = flip.boughtQty - flip.sold;
            if (remaining <= 0) {
                flips.remove(flip.item.id);
                continue;
            }
            int qty = Math.min(remaining, Rs2Inventory.itemQuantity(flip.item.id));
            if (qty <= 0) continue;

            int price = sellPrice(flip);
            status = "Selling " + flip.item.name + " @ " + price;
            if (Rs2GrandExchange.sellItem(flip.item.name, qty, price)) {
                flip.sellPrice = price;
                flip.changed = System.currentTimeMillis();
                telemetry("SELL_LISTED", flip, qty, price, 0);
            }
            return;
        }
    }

    private void fillFreeSlots() {
        Set<Integer> occupied = occupiedItems();
        int reserved = clamp(config.reservedSlots(), 0, 7);
        int configuredMax = clamp(config.maxSlots(), 1, 8);
        int totalPluginLimit = Math.max(0, configuredMax - reserved);
        if (totalPluginLimit <= 0) return;

        if (config.enableDumpOpportunities() && clamp(config.timeframeMinutes(), 5, 240) <= 30) {
            fillDumpSlot(totalPluginLimit, reserved, occupied);
        }

        int dumpReserve = config.enableDumpOpportunities() && clamp(config.timeframeMinutes(), 5, 240) <= 30 ? 1 : 0;
        int normalLimit = Math.max(0, totalPluginLimit - dumpReserve);
        int normalActive = (int) flips.values().stream().filter(f -> f.type == CandidateType.NORMAL).count();
        int buying = (int) flips.values().stream().filter(f -> !f.selling).count();
        int inventoryHeadroom = Math.max(0, 28 - Rs2Inventory.count() - buying);
        int actualAvailable = Math.max(0, Rs2GrandExchange.getAvailableSlotsCount() - reserved);
        int free = Math.min(inventoryHeadroom, Math.min(normalLimit - normalActive, actualAvailable));
        if (free <= 0) return;

        while (free-- > 0) {
            long available = availableCash();
            if (available <= 0) return;
            long budget = budgetForRisk(available, free + 1);
            Candidate candidate = best(budget, occupied, CandidateType.NORMAL);
            if (candidate == null) {
                clearCandidateOverlay();
                return;
            }

            showCandidate(candidate);
            status = "Buying " + candidate.item.name + " @ " + candidate.buy;
            if (!Rs2GrandExchange.buyItem(candidate.item.name, candidate.buy, candidate.qty)) {
                cooldowns.put(candidate.item.id, System.currentTimeMillis() + 60_000);
                return;
            }

            Flip flip = new Flip(candidate);
            flips.put(candidate.item.id, flip);
            telemetry("RECOMMENDATION_ACCEPTED", flip, candidate.qty, candidate.buy, candidate.profit);
            occupied.add(candidate.item.id);
        }
    }

    private void fillDumpSlot(int totalPluginLimit, int reserved, Set<Integer> occupied) {
        if (flips.values().stream().anyMatch(f -> f.type == CandidateType.DUMP)) return;
        if (flips.size() >= totalPluginLimit) return;
        if (Math.max(0, Rs2GrandExchange.getAvailableSlotsCount() - reserved) <= 0) return;

        long available = availableCash();
        if (available <= 0) return;
        Candidate candidate = best(budgetForRisk(available, 1), occupied, CandidateType.DUMP);
        if (candidate == null) return;

        showCandidate(candidate);
        status = "Dump buy " + candidate.item.name + " @ " + candidate.buy;
        if (!Rs2GrandExchange.buyItem(candidate.item.name, candidate.buy, candidate.qty)) {
            cooldowns.put(candidate.item.id, System.currentTimeMillis() + 60_000);
            return;
        }

        Flip flip = new Flip(candidate);
        flips.put(candidate.item.id, flip);
        telemetry("DUMP_RECOMMENDATION_ACCEPTED", flip, candidate.qty, candidate.buy, candidate.profit);
        occupied.add(candidate.item.id);
    }

    private long availableCash() {
        return Math.max(0, Rs2Inventory.itemQuantity(COINS) - Math.max(0, config.reserveCoins()));
    }

    private long budgetForRisk(long available, int remainingSlots) {
        RiskProfile risk = riskProfile();
        double hardCap = clamp(config.maxCapitalPercent(), 1, 100) / 100.0;
        double capFraction = Math.min(hardCap, risk.maxItemExposure);
        long perItemCap = Math.max(1, Math.round(available * capFraction));
        return Math.max(1, Math.min(perItemCap, available / Math.max(1, remainingSlots)));
    }

    private Set<Integer> occupiedItems() {
        Set<Integer> ids = new HashSet<>();
        for (GrandExchangeSlots slot : Rs2GrandExchange.getActiveOfferSlots()) {
            GrandExchangeOfferDetails details = Rs2GrandExchange.getOfferDetails(slot);
            if (details != null && details.getItemId() > 0) ids.add(details.getItemId());
        }
        return ids;
    }

    private Candidate best(long budget, Set<Integer> occupied, CandidateType type) {
        Set<String> whitelist = itemSet(config.customItems());
        Set<String> blocked = itemSet(config.blockedItems());
        Candidate best = null;

        for (Item item : market.items.values()) {
            String normalizedName = item.name.toLowerCase(Locale.ROOT);
            if (item.limit <= 0 || item.members && !members || flips.containsKey(item.id) || occupied.contains(item.id)) continue;
            if (!whitelist.isEmpty() && !whitelist.contains(normalizedName)) continue;
            if (blocked.contains(normalizedName)) continue;
            if (cooldowns.getOrDefault(item.id, 0L) > System.currentTimeMillis()) continue;

            Candidate candidate = candidateForItem(item, budget, type);
            if (candidate != null && (best == null || candidate.utility > best.utility)) best = candidate;
        }
        return best;
    }

    private Candidate candidateForItem(Item item, long budget, CandidateType type) {
        String normalizedName = item.name.toLowerCase(Locale.ROOT);
        Set<String> whitelist = itemSet(config.customItems());
        if (item.limit <= 0 || item.members && !members || itemSet(config.blockedItems()).contains(normalizedName)
                || !whitelist.isEmpty() && !whitelist.contains(normalizedName)) return null;

        Quote quote = market.quotes.get(item.id);
        MarketWindow hour = market.hours.get(item.id);
        MarketWindow five = market.fives.get(item.id);
        if (quote == null || hour == null || quote.high <= 0 || quote.low <= 0 || quote.high <= quote.low) return null;

        long nowSec = System.currentTimeMillis() / 1000L;
        long maxAge = clamp(config.quoteAge(), 30, 1800);
        if (nowSec - quote.highTime > maxAge || nowSec - quote.lowTime > maxAge) return null;
        int volume = Math.min(hour.highVolume, hour.lowVolume);
        if (volume < Math.max(1, config.minHourlyVolume())) return null;
        if (!sane(quote, hour)) return null;

        MarketWindow effectiveFive = five == null ? hour : five;
        Forecast forecast = forecast(quote, effectiveFive, hour, maxAge, nowSec);
        RiskProfile risk = riskProfile();
        if (forecast.confidence < risk.minConfidence) return null;

        if (type == CandidateType.DUMP) {
            if (!config.enableDumpOpportunities() || !isDump(quote, effectiveFive, hour)) return null;
            return dumpCandidate(item, quote, effectiveFive, hour, forecast, risk, budget, volume);
        }
        if (config.enableDumpOpportunities() && isDump(quote, effectiveFive, hour)) return null;
        return normalCandidate(item, quote, effectiveFive, hour, forecast, risk, budget, volume);
    }

    private Candidate normalCandidate(Item item, Quote quote, MarketWindow five, MarketWindow hour, Forecast forecast,
                                      RiskProfile risk, long budget, int volume) {
        int spread = quote.high - quote.low;
        int baseBuyEdge = Math.max(1, Math.min(spread / 3, edge(quote.low)));
        int baseSellEdge = Math.max(1, Math.min(spread / 3, edge(quote.high)));
        int sellCeiling = Math.min(quote.high, Math.max(quote.low + 1, (int) Math.round(forecast.highMean)));
        Candidate best = null;

        for (int step = 1; step <= 4; step++) {
            double stepFactor = step / 4.0;
            int buy = quote.low + Math.max(1, (int) Math.round(baseBuyEdge * stepFactor));
            int sell = sellCeiling - Math.max(1, (int) Math.round(baseSellEdge * stepFactor));
            if (buy <= 0 || sell <= buy) continue;

            long net = sell - buy - tax(item.name, sell);
            double roi = net * 100.0 / buy;
            if (net <= 0 || roi < Math.max(0, config.minNetRoi())) continue;

            int qty = sizedQuantity(item, buy, budget, volume, risk, false);
            if (qty <= 0) continue;
            long tradeProfit = net * qty;
            if (tradeProfit < Math.max(0, config.minTradeProfit())) continue;

            double aggression = clamp(((buy - quote.low) + (quote.high - sell)) / (double) spread, 0, 1);
            double fillProbability = fillProbability(forecast, aggression, volume);
            double expectedMinutes = expectedDurationMinutes(qty, volume, aggression, forecast, fillProbability);
            double gpPerHour = tradeProfit * 60.0 / expectedMinutes * fillProbability;
            if (gpPerHour < Math.max(0, config.minExpectedGpPerHour())) continue;
            double utility = utility(gpPerHour, forecast, risk, fillProbability);

            Candidate candidate = new Candidate(item, CandidateType.NORMAL, buy, sell, qty, net, roi, tradeProfit,
                    volume, expectedMinutes, gpPerHour, forecast.confidence, fillProbability, utility);
            if (best == null || candidate.utility > best.utility) best = candidate;
        }
        return best;
    }

    private Candidate dumpCandidate(Item item, Quote quote, MarketWindow five, MarketWindow hour, Forecast forecast,
                                    RiskProfile risk, long budget, int volume) {
        int spread = quote.high - quote.low;
        int buy = quote.low + Math.max(1, Math.min(spread / 4, edge(quote.low)));
        int recovery = (int) Math.round(five.avgLow * 0.65 + hour.avgLow * 0.35);
        int sell = Math.min(quote.high - 1, Math.max(buy + 1, recovery));
        if (sell <= buy) return null;

        long net = sell - buy - tax(item.name, sell);
        double roi = net * 100.0 / buy;
        if (net <= 0 || roi < Math.max(0, config.minNetRoi())) return null;

        int qty = sizedQuantity(item, buy, budget, volume, risk, true);
        if (qty <= 0) return null;
        long tradeProfit = net * qty;
        if (tradeProfit < Math.max(0, config.dumpMinPredictedProfit())) return null;

        double dropPct = (hour.avgLow - quote.low) * 100.0 / Math.max(1, hour.avgLow);
        double volumeAcceleration = five.lowVolume <= 0 || hour.lowVolume <= 0
                ? 1.0 : five.lowVolume * 12.0 / hour.lowVolume;
        double recoveryProbability = clamp(0.25 + forecast.confidence * 0.35
                + Math.min(0.20, dropPct / 50.0) + Math.min(0.20, Math.max(0, volumeAcceleration - 1.0) * 0.10), 0.20, 0.90);
        double expectedMinutes = Math.max(5.0, clamp(config.timeframeMinutes(), 5, 240) * 0.75)
                * (1.0 + forecast.uncertaintyPct * 5.0) / recoveryProbability;
        double gpPerHour = tradeProfit * 60.0 / expectedMinutes * recoveryProbability;
        if (gpPerHour < Math.max(0, config.minExpectedGpPerHour())) return null;
        double utility = utility(gpPerHour, forecast, risk, recoveryProbability);

        return new Candidate(item, CandidateType.DUMP, buy, sell, qty, net, roi, tradeProfit, volume,
                expectedMinutes, gpPerHour, forecast.confidence, recoveryProbability, utility);
    }

    private int sizedQuantity(Item item, int buy, long budget, int hourlyVolume, RiskProfile risk, boolean dump) {
        int limit = remainingLimit(item.id, item.limit);
        int timeframe = clamp(config.timeframeMinutes(), 5, 240);
        double participation = risk.liquidityParticipation * (dump ? 0.60 : 1.0);
        int liquidQty = Math.max(1, (int) Math.floor(hourlyVolume * (timeframe / 60.0) * participation));
        long affordable = budget / Math.max(1, buy);
        return (int) Math.min(Math.min(limit, liquidQty), Math.min(Integer.MAX_VALUE, affordable));
    }

    private Forecast forecast(Quote quote, MarketWindow five, MarketWindow hour, long maxAge, long nowSec) {
        double low5 = five.avgLow > 0 ? five.avgLow : quote.low;
        double high5 = five.avgHigh > 0 ? five.avgHigh : quote.high;
        double low1 = hour.avgLow > 0 ? hour.avgLow : low5;
        double high1 = hour.avgHigh > 0 ? hour.avgHigh : high5;

        double midLatest = (quote.high + quote.low) / 2.0;
        double mid5 = (high5 + low5) / 2.0;
        double mid1 = (high1 + low1) / 2.0;
        double trend5v1 = safeRatio(mid5 - mid1, mid1);
        double trendLatest = safeRatio(midLatest - mid5, mid5);
        double horizonWeight = Math.min(1.5, clamp(config.timeframeMinutes(), 5, 240) / 60.0);
        double directional = (trendLatest * 0.65 + trend5v1 * 0.35) * Math.min(0.75, horizonWeight * 0.35);

        double lowMean = quote.low * 0.55 + low5 * 0.30 + low1 * 0.15;
        double highMean = quote.high * 0.55 + high5 * 0.30 + high1 * 0.15;
        lowMean *= 1.0 + directional;
        highMean *= 1.0 + directional;

        double spreadLatest = safeRatio(quote.high - quote.low, midLatest);
        double spread5 = safeRatio(high5 - low5, mid5);
        double spread1 = safeRatio(high1 - low1, mid1);
        double uncertainty = clamp(Math.abs(trendLatest) + Math.abs(trend5v1) * 0.75
                + Math.abs(spreadLatest - spread5) * 0.50 + Math.abs(spread5 - spread1) * 0.35, 0, 0.30);

        long age = Math.max(nowSec - quote.highTime, nowSec - quote.lowTime);
        double freshness = 1.0 - clamp(age / (double) Math.max(1, maxAge), 0, 1);
        int volume = Math.min(hour.highVolume, hour.lowVolume);
        double volumeScore = clamp(Math.log10(volume + 1.0) / 4.0, 0, 1);
        double confidence = clamp(0.45 + freshness * 0.20 + volumeScore * 0.20 - uncertainty * 1.75, 0.10, 0.97);

        return new Forecast(lowMean, highMean, uncertainty, confidence);
    }

    private boolean isDump(Quote quote, MarketWindow five, MarketWindow hour) {
        if (hour.avgLow <= 0 || five.avgLow <= 0 || quote.low <= 0) return false;
        double dropPct = (hour.avgLow - quote.low) * 100.0 / hour.avgLow;
        double volumeAcceleration = five.lowVolume <= 0 || hour.lowVolume <= 0
                ? 1.0 : five.lowVolume * 12.0 / hour.lowVolume;
        return dropPct >= Math.max(0.5, config.dumpDropPercent())
                && quote.low < five.avgLow
                && volumeAcceleration >= 1.20;
    }

    private double fillProbability(Forecast forecast, double aggression, int volume) {
        double volumeScore = clamp(Math.log10(volume + 1.0) / 4.0, 0, 1);
        return clamp(0.20 + aggression * 0.40 + forecast.confidence * 0.25 + volumeScore * 0.15, 0.15, 0.98);
    }

    private double expectedDurationMinutes(int qty, int hourlyVolume, double aggression, Forecast forecast, double fillProbability) {
        double flowMinutes = 60.0 * qty / Math.max(1, hourlyVolume);
        double priceFactor = 1.30 - aggression * 0.65;
        double uncertaintyFactor = 1.0 + forecast.uncertaintyPct * 6.0;
        double minutes = (3.0 + flowMinutes * 2.5) * priceFactor * uncertaintyFactor / Math.max(0.15, fillProbability);
        return clamp(minutes, 2.0, 360.0);
    }

    private double utility(double gpPerHour, Forecast forecast, RiskProfile risk, double executionProbability) {
        double riskFactor = 1.0 / (1.0 + forecast.uncertaintyPct * risk.riskWeight * 8.0);
        return gpPerHour * forecast.confidence * executionProbability * riskFactor;
    }

    private int sellPrice(Flip flip) {
        Quote quote = market.quotes.get(flip.item.id);
        int target = quote == null || quote.high <= 0
                ? flip.sellPrice
                : quote.high - edge(quote.high) * Math.max(1, flip.reprices + 1);
        long unitCost = Math.max(1, (flip.buySpent + flip.boughtQty - 1) / Math.max(1, flip.boughtQty));
        return Math.max(1, Math.max(target, minSellFor(unitCost, flip.item.name)));
    }

    private void accountSold(Flip flip, int totalSold) {
        int sold = Math.min(totalSold, flip.boughtQty);
        if (sold <= flip.sold) return;
        int delta = sold - flip.sold;
        long oldCost = flip.buySpent * flip.sold / Math.max(1, flip.boughtQty);
        long newCost = flip.buySpent * sold / Math.max(1, flip.boughtQty);
        long realized = (long) flip.sellPrice * delta - tax(flip.item.name, flip.sellPrice) * delta - (newCost - oldCost);
        profit += realized;
        flip.sold = sold;
        telemetry("SELL_FILLED", flip, delta, flip.sellPrice, realized);
    }

    private void showCandidate(Candidate candidate) {
        bestCandidate = candidate.item.name;
        candidateType = candidate.type.name();
        candidateBuy = candidate.buy;
        candidateSell = candidate.sell;
        candidateQty = candidate.qty;
        candidateProfit = candidate.profit;
        candidateRoi = candidate.roi;
        candidateVolume = candidate.volume;
        candidateExpectedMinutes = (int) Math.round(candidate.expectedMinutes);
        candidateGpPerHour = Math.round(candidate.gpPerHour);
        candidateConfidence = candidate.confidence;
    }

    private void clearCandidateOverlay() {
        bestCandidate = "-";
        candidateType = "-";
        candidateBuy = candidateSell = candidateQty = candidateVolume = candidateExpectedMinutes = 0;
        candidateProfit = candidateGpPerHour = 0;
        candidateRoi = candidateConfidence = 0;
    }

    private void updateStats() {
        activeFlips = flips.size();
        buyingFlips = sellingFlips = 0;
        long used = 0;
        for (Flip flip : flips.values()) {
            if (flip.selling) sellingFlips++; else buyingFlips++;
            used += flip.selling ? Math.max(0, flip.buySpent) : (long) flip.buyPrice * Math.max(0, flip.requestedQty);
        }
        capitalUsed = used;
    }

    private int remainingLimit(int id, int limit) {
        LimitWindow window = limits.get(id);
        if (window == null || System.currentTimeMillis() - window.started >= FOUR_HOURS) return limit;
        return Math.max(0, limit - window.used);
    }

    private void useLimit(int id, int qty) {
        long now = System.currentTimeMillis();
        LimitWindow window = limits.get(id);
        if (window == null || now - window.started >= FOUR_HOURS) limits.put(id, new LimitWindow(now, qty));
        else window.used += qty;
    }

    private Set<String> itemSet(String raw) {
        if (raw == null || raw.isBlank()) return Collections.emptySet();
        Set<String> out = new HashSet<>();
        for (String item : raw.split(",")) {
            if (!item.isBlank()) out.add(item.trim().toLowerCase(Locale.ROOT));
        }
        return out;
    }

    private RiskProfile riskProfile() {
        KspGEFlipperConfig.RiskLevel risk = config.riskLevel();
        if (risk == null) risk = KspGEFlipperConfig.RiskLevel.MEDIUM;
        switch (risk) {
            case LOW:
                return new RiskProfile(0.15, 0.72, 0.03, 1.40);
            case HIGH:
                return new RiskProfile(0.50, 0.45, 0.08, 0.65);
            case MEDIUM:
            default:
                return new RiskProfile(0.30, 0.58, 0.05, 1.00);
        }
    }

    private int edge(int price) {
        return Math.max(1, (int) Math.round(price * clamp(config.edgePercent(), 0, 5) / 100.0));
    }

    private static boolean sane(Quote quote, MarketWindow hour) {
        if (hour.avgHigh <= 0 || hour.avgLow <= 0) return true;
        double current = (quote.high + quote.low) / 2.0;
        double average = (hour.avgHigh + hour.avgLow) / 2.0;
        return current > average * 0.75 && current < average * 1.25;
    }

    private static long tax(String name, int sell) {
        if (sell < 50 || TAX_FREE.contains(name.toLowerCase(Locale.ROOT))) return 0;
        return Math.min(5_000_000L, (long) Math.floor(sell * 0.02));
    }

    private static int minSellFor(long cost, String name) {
        long target = cost + 1;
        if (target < 50 || TAX_FREE.contains(name.toLowerCase(Locale.ROOT))) {
            return (int) Math.min(Integer.MAX_VALUE, target);
        }
        long price = target <= 245_000_000L ? (long) Math.ceil(target / 0.98) : target + 5_000_000L;
        return (int) Math.min(Integer.MAX_VALUE, price);
    }

    private static double percentChange(double oldValue, double newValue) {
        if (Math.abs(oldValue) < 1e-9) return newValue > 0 ? 100.0 : 0.0;
        return (newValue - oldValue) * 100.0 / Math.abs(oldValue);
    }

    private static double safeRatio(double numerator, double denominator) {
        return Math.abs(denominator) < 1e-9 ? 0.0 : numerator / denominator;
    }

    private void telemetry(String event, Flip flip, int qty, int price, long value) {
        log.info("KSP_GE event={} suggestion={} itemId={} item=\"{}\" type={} qty={} price={} value={} confidence={} expectedMinutes={} expectedGpPerHour={}",
                event, flip.suggestionId, flip.item.id, flip.item.name, flip.type, qty, price, value,
                String.format(Locale.ROOT, "%.3f", flip.confidence),
                String.format(Locale.ROOT, "%.1f", flip.expectedMinutes), Math.round(flip.expectedGpPerHour));
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    public static Duration runtime() { return Duration.ofMillis(Math.max(0, System.currentTimeMillis() - started)); }

    private enum CandidateType {
        NORMAL,
        DUMP
    }

    private static final class RiskProfile {
        final double maxItemExposure;
        final double minConfidence;
        final double liquidityParticipation;
        final double riskWeight;

        RiskProfile(double maxItemExposure, double minConfidence, double liquidityParticipation, double riskWeight) {
            this.maxItemExposure = maxItemExposure;
            this.minConfidence = minConfidence;
            this.liquidityParticipation = liquidityParticipation;
            this.riskWeight = riskWeight;
        }
    }

    private static final class Forecast {
        final double lowMean;
        final double highMean;
        final double uncertaintyPct;
        final double confidence;

        Forecast(double lowMean, double highMean, double uncertaintyPct, double confidence) {
            this.lowMean = lowMean;
            this.highMean = highMean;
            this.uncertaintyPct = uncertaintyPct;
            this.confidence = confidence;
        }
    }

    private static final class Flip {
        final Item item;
        final String suggestionId = UUID.randomUUID().toString();
        final CandidateType type;
        int buyPrice;
        int sellPrice;
        int requestedQty;
        int boughtQty;
        int sold;
        int reprices;
        long buySpent;
        long changed = System.currentTimeMillis();
        double entryUtility;
        double expectedMinutes;
        double expectedGpPerHour;
        double confidence;
        boolean selling;
        boolean pendingBuyReprice;
        boolean abortRequested;
        int pendingBuyPrice;
        int pendingSellPrice;
        double pendingUtility;
        double pendingExpectedMinutes;
        double pendingExpectedGpPerHour;
        double pendingConfidence;

        Flip(Candidate candidate) {
            item = candidate.item;
            type = candidate.type;
            buyPrice = candidate.buy;
            sellPrice = candidate.sell;
            requestedQty = candidate.qty;
            entryUtility = candidate.utility;
            expectedMinutes = candidate.expectedMinutes;
            expectedGpPerHour = candidate.gpPerHour;
            confidence = candidate.confidence;
        }

        void applyPendingBuyPlan() {
            if (!pendingBuyReprice) return;
            buyPrice = pendingBuyPrice;
            sellPrice = pendingSellPrice;
            entryUtility = pendingUtility;
            expectedMinutes = pendingExpectedMinutes;
            expectedGpPerHour = pendingExpectedGpPerHour;
            confidence = pendingConfidence;
            clearPendingBuyPlan();
        }

        void clearPendingBuyPlan() {
            pendingBuyReprice = false;
            pendingBuyPrice = 0;
            pendingSellPrice = 0;
            pendingUtility = 0;
            pendingExpectedMinutes = 0;
            pendingExpectedGpPerHour = 0;
            pendingConfidence = 0;
        }
    }

    private static final class LimitWindow {
        final long started;
        int used;

        LimitWindow(long started, int used) {
            this.started = started;
            this.used = used;
        }
    }

    private static final class Candidate {
        final Item item;
        final CandidateType type;
        final int buy;
        final int sell;
        final int qty;
        final int volume;
        final long net;
        final long profit;
        final double roi;
        final double expectedMinutes;
        final double gpPerHour;
        final double confidence;
        final double executionProbability;
        final double utility;

        Candidate(Item item, CandidateType type, int buy, int sell, int qty, long net, double roi, long profit,
                  int volume, double expectedMinutes, double gpPerHour, double confidence,
                  double executionProbability, double utility) {
            this.item = item;
            this.type = type;
            this.buy = buy;
            this.sell = sell;
            this.qty = qty;
            this.net = net;
            this.roi = roi;
            this.profit = profit;
            this.volume = volume;
            this.expectedMinutes = expectedMinutes;
            this.gpPerHour = gpPerHour;
            this.confidence = confidence;
            this.executionProbability = executionProbability;
            this.utility = utility;
        }
    }

    private static final class Item {
        final int id;
        final int limit;
        final String name;
        final boolean members;

        Item(int id, String name, boolean members, int limit) {
            this.id = id;
            this.name = name;
            this.members = members;
            this.limit = limit;
        }
    }

    private static final class Quote {
        final int high;
        final int low;
        final long highTime;
        final long lowTime;

        Quote(int high, int low, long highTime, long lowTime) {
            this.high = high;
            this.low = low;
            this.highTime = highTime;
            this.lowTime = lowTime;
        }
    }

    private static final class MarketWindow {
        final int avgHigh;
        final int avgLow;
        final int highVolume;
        final int lowVolume;

        MarketWindow(int avgHigh, int avgLow, int highVolume, int lowVolume) {
            this.avgHigh = avgHigh;
            this.avgLow = avgLow;
            this.highVolume = highVolume;
            this.lowVolume = lowVolume;
        }
    }

    private static final class Market {
        private static final String BASE = "https://prices.runescape.wiki/api/v1/osrs/";
        private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        final Map<Integer, Item> items = new HashMap<>();
        final Map<Integer, Quote> quotes = new HashMap<>();
        final Map<Integer, MarketWindow> fives = new HashMap<>();
        final Map<Integer, MarketWindow> hours = new HashMap<>();
        long mappedAt;
        long latestAt;
        long windowsAt;

        void refreshIfNeeded() {
            long now = System.currentTimeMillis();
            try {
                if (items.isEmpty() || now - mappedAt > 6 * 60 * 60 * 1000L) loadMapping();
                if (quotes.isEmpty() || now - latestAt > 30_000L) {
                    loadLatest();
                    latestAt = now;
                }
                if (fives.isEmpty() || hours.isEmpty() || now - windowsAt > 60_000L) {
                    loadWindow("5m", fives);
                    loadWindow("1h", hours);
                    windowsAt = now;
                }
            } catch (Exception e) {
                log.warn("Market refresh failed: {}", e.getMessage());
            }
        }

        private void loadMapping() throws Exception {
            JsonArray array = new JsonParser().parse(get("mapping")).getAsJsonArray();
            Map<Integer, Item> next = new HashMap<>();
            for (JsonElement element : array) {
                JsonObject object = element.getAsJsonObject();
                int id = n(object, "id");
                int limit = n(object, "limit");
                String name = text(object, "name");
                if (id > 0 && limit > 0 && !name.isBlank()) {
                    next.put(id, new Item(id, name, bool(object, "members"), limit));
                }
            }
            items.clear();
            items.putAll(next);
            mappedAt = System.currentTimeMillis();
        }

        private void loadLatest() throws Exception {
            JsonObject data = new JsonParser().parse(get("latest")).getAsJsonObject().getAsJsonObject("data");
            Map<Integer, Quote> next = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : data.entrySet()) {
                JsonObject object = entry.getValue().getAsJsonObject();
                next.put(Integer.parseInt(entry.getKey()), new Quote(
                        n(object, "high"), n(object, "low"), l(object, "highTime"), l(object, "lowTime")));
            }
            quotes.clear();
            quotes.putAll(next);
        }

        private void loadWindow(String path, Map<Integer, MarketWindow> target) throws Exception {
            JsonObject data = new JsonParser().parse(get(path)).getAsJsonObject().getAsJsonObject("data");
            Map<Integer, MarketWindow> next = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : data.entrySet()) {
                JsonObject object = entry.getValue().getAsJsonObject();
                next.put(Integer.parseInt(entry.getKey()), new MarketWindow(
                        n(object, "avgHighPrice"), n(object, "avgLowPrice"),
                        n(object, "highPriceVolume"), n(object, "lowPriceVolume")));
            }
            target.clear();
            target.putAll(next);
        }

        private String get(String path) throws Exception {
            HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + path)).timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "KSP-GE-Flipper/" + KspGEFlipperPlugin.VERSION + " (https://github.com/KSPOG/ksppluginsrelease)")
                    .GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IllegalStateException(path + " HTTP " + response.statusCode());
            return response.body();
        }

        private static int n(JsonObject object, String key) { return missing(object, key) ? 0 : object.get(key).getAsInt(); }
        private static long l(JsonObject object, String key) { return missing(object, key) ? 0 : object.get(key).getAsLong(); }
        private static boolean bool(JsonObject object, String key) { return !missing(object, key) && object.get(key).getAsBoolean(); }
        private static String text(JsonObject object, String key) { return missing(object, key) ? "" : object.get(key).getAsString(); }
        private static boolean missing(JsonObject object, String key) {
            return object == null || !object.has(key) || object.get(key).isJsonNull();
        }
    }
}
