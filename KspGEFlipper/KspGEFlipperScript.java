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

    public static volatile String status = "Idle", bestCandidate = "-";
    public static volatile long cash, profit, capitalUsed, candidateProfit;
    public static volatile int activeFlips, buyingFlips, sellingFlips, completedFlips, candidateBuy, candidateSell, candidateQty, candidateVolume, marketItems;
    public static volatile double candidateRoi;
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
        profit = capitalUsed = candidateProfit = 0;
        activeFlips = buyingFlips = sellingFlips = completedFlips = candidateBuy = candidateSell = candidateQty = candidateVolume = marketItems = 0;
        candidateRoi = 0; bestCandidate = "-";
        flips.clear(); limits.clear(); cooldowns.clear();
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
        repriceStale();
        listPendingSells();
        fillFreeSlots();

        cash = Rs2Inventory.itemQuantity(COINS);
        updateStats();
        if (flips.isEmpty() && "-".equals(bestCandidate)) status = "Scanning market";
    }

    private boolean ensureCash() {
        if (cash > Math.max(0, config.reserveCoins()) || !flips.isEmpty()) return true;
        long now = System.currentTimeMillis();
        if (now < nextBankTry) { status = "Need more coins"; return false; }
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
        for (Map.Entry<GrandExchangeSlots, GrandExchangeOfferDetails> e : Rs2GrandExchange.getCompletedOffers().entrySet()) {
            GrandExchangeOfferDetails d = e.getValue();
            Flip f = flips.get(d.getItemId());
            if (f == null) continue;

            GrandExchangeOfferState s = d.getState();
            if (!f.selling && (s == GrandExchangeOfferState.BOUGHT || s == GrandExchangeOfferState.CANCELLED_BUY)) {
                finishBuy(e.getKey(), d, f);
            } else if (f.selling && (s == GrandExchangeOfferState.SOLD || s == GrandExchangeOfferState.CANCELLED_SELL)) {
                finishSell(e.getKey(), d, f, s == GrandExchangeOfferState.SOLD);
            }
        }
    }

    private void finishBuy(GrandExchangeSlots slot, GrandExchangeOfferDetails d, Flip f) {
        int qty = d.getQuantitySold();
        long spent = d.getSpent() > 0 ? d.getSpent() : (long) f.buyPrice * qty;
        Rs2GrandExchange.collectOffer(slot, false);
        if (qty <= 0) { flips.remove(f.item.id); return; }

        useLimit(f.item.id, qty);
        f.boughtQty = qty;
        f.buySpent = spent;
        f.selling = true;
        f.changed = System.currentTimeMillis();
        status = "Bought " + qty + " x " + f.item.name;
    }

    private void finishSell(GrandExchangeSlots slot, GrandExchangeOfferDetails d, Flip f, boolean complete) {
        accountSold(f, d.getQuantitySold());
        Rs2GrandExchange.collectOffer(slot, false);
        if (complete || f.sold >= f.boughtQty) {
            flips.remove(f.item.id);
            completedFlips++;
            status = "Completed " + f.item.name;
        } else {
            f.changed = System.currentTimeMillis();
            f.reprices++;
        }
    }

    private void repriceStale() {
        long timeout = clamp(config.offerTimeout(), 30, 3600) * 1000L;
        long now = System.currentTimeMillis();
        for (Flip f : new ArrayList<>(flips.values())) {
            if (now - f.changed < timeout) continue;
            GrandExchangeSlots slot = Rs2GrandExchange.findSlotForItem(f.item.id, f.selling);
            if (slot == null) continue;
            GrandExchangeOfferDetails d = Rs2GrandExchange.getOfferDetails(slot);
            if (d == null || !d.isInProgress()) continue;

            status = "Repricing " + f.item.name;
            if (f.selling) accountSold(f, d.getQuantitySold());
            Rs2GrandExchange.cancelSpecificOffers(Collections.singletonList(slot), false);

            if (!f.selling) {
                int qty = d.getQuantitySold();
                if (qty <= 0) { flips.remove(f.item.id); cooldowns.put(f.item.id, now + 30_000); continue; }
                useLimit(f.item.id, qty);
                f.boughtQty = qty;
                f.buySpent = d.getSpent() > 0 ? d.getSpent() : (long) f.buyPrice * qty;
                f.selling = true;
            } else if (f.sold >= f.boughtQty) {
                flips.remove(f.item.id);
                completedFlips++;
                continue;
            }
            f.reprices++;
            f.changed = now;
        }
    }

    private void listPendingSells() {
        for (Flip f : new ArrayList<>(flips.values())) {
            if (!f.selling || Rs2GrandExchange.findSlotForItem(f.item.id, true) != null) continue;
            int remaining = f.boughtQty - f.sold;
            if (remaining <= 0) { flips.remove(f.item.id); continue; }
            int qty = Math.min(remaining, Rs2Inventory.itemQuantity(f.item.id));
            if (qty <= 0) continue;

            int price = sellPrice(f);
            status = "Selling " + f.item.name + " @ " + price;
            if (Rs2GrandExchange.sellItem(f.item.name, qty, price)) {
                f.sellPrice = price;
                f.changed = System.currentTimeMillis();
            }
            return;
        }
    }

    private void fillFreeSlots() {
        int buying = (int) flips.values().stream().filter(f -> !f.selling).count();
        int inventoryHeadroom = Math.max(0, 28 - Rs2Inventory.count() - buying);
        int free = Math.min(inventoryHeadroom, Math.min(clamp(config.maxSlots(), 1, 8) - flips.size(), Rs2GrandExchange.getAvailableSlotsCount()));
        Set<Integer> occupied = occupiedItems();
        while (free-- > 0) {
            long available = Rs2Inventory.itemQuantity(COINS) - Math.max(0, config.reserveCoins());
            if (available <= 0) return;
            long cap = Math.round(available * clamp(config.maxCapitalPercent(), 1, 100) / 100.0);
            long budget = Math.max(1, Math.min(cap, available / (free + 1)));
            Candidate c = best(budget, occupied);
            if (c == null) {
                bestCandidate = "-"; candidateBuy = candidateSell = candidateQty = candidateVolume = 0;
                candidateProfit = 0; candidateRoi = 0;
                return;
            }

            bestCandidate = c.item.name;
            candidateBuy = c.buy; candidateSell = c.sell; candidateQty = c.qty;
            candidateProfit = c.profit; candidateRoi = c.roi;
            Hour ch = market.hours.get(c.item.id);
            candidateVolume = ch == null ? 0 : Math.min(ch.highVolume, ch.lowVolume);
            status = "Buying " + c.item.name + " @ " + c.buy;
            if (!Rs2GrandExchange.buyItem(c.item.name, c.buy, c.qty)) {
                cooldowns.put(c.item.id, System.currentTimeMillis() + 60_000);
                return;
            }
            flips.put(c.item.id, new Flip(c));
            occupied.add(c.item.id);
        }
    }

    private Set<Integer> occupiedItems() {
        Set<Integer> ids = new HashSet<>();
        for (GrandExchangeSlots slot : Rs2GrandExchange.getActiveOfferSlots()) {
            GrandExchangeOfferDetails d = Rs2GrandExchange.getOfferDetails(slot);
            if (d != null && d.getItemId() > 0) ids.add(d.getItemId());
        }
        return ids;
    }

    private Candidate best(long budget, Set<Integer> occupied) {
        long nowSec = System.currentTimeMillis() / 1000L;
        long maxAge = clamp(config.quoteAge(), 30, 1800);
        int minVol = Math.max(1, config.minHourlyVolume());
        long minProfit = Math.max(0, config.minTradeProfit());
        double minRoi = Math.max(0, config.minNetRoi());
        Set<String> whitelist = whitelist();
        Candidate best = null;

        for (Item item : market.items.values()) {
            if (item.limit <= 0 || item.members && !members || flips.containsKey(item.id) || occupied.contains(item.id)) continue;
            if (!whitelist.isEmpty() && !whitelist.contains(item.name.toLowerCase(Locale.ROOT))) continue;
            if (cooldowns.getOrDefault(item.id, 0L) > System.currentTimeMillis()) continue;

            Quote q = market.quotes.get(item.id);
            Hour h = market.hours.get(item.id);
            if (q == null || h == null || q.high <= 0 || q.low <= 0) continue;
            if (nowSec - q.highTime > maxAge || nowSec - q.lowTime > maxAge) continue;
            int volume = Math.min(h.highVolume, h.lowVolume);
            if (volume < minVol) continue;
            if (!sane(q, h)) continue;

            int edgeLow = edge(q.low), edgeHigh = edge(q.high);
            int buy = q.low + edgeLow, sell = q.high - edgeHigh;
            if (sell <= buy || buy <= 0) continue;
            long net = sell - buy - tax(item.name, sell);
            double roi = net * 100.0 / buy;
            if (net <= 0 || roi < minRoi) continue;

            int limit = remainingLimit(item.id, item.limit);
            int liquidQty = Math.max(1, volume / 12); // ~5 minutes of conservative two-sided 1h flow
            int qty = (int) Math.min(Math.min(limit, liquidQty), budget / buy);
            long tradeProfit = net * qty;
            if (qty <= 0 || tradeProfit < minProfit) continue;

            Candidate c = new Candidate(item, buy, sell, qty, net, roi, tradeProfit);
            if (best == null || c.profit > best.profit) best = c;
        }
        return best;
    }

    private int sellPrice(Flip f) {
        Quote q = market.quotes.get(f.item.id);
        int target = q == null || q.high <= 0 ? f.sellPrice : q.high - edge(q.high) * Math.max(1, f.reprices + 1);
        long unitCost = Math.max(1, (f.buySpent + f.boughtQty - 1) / f.boughtQty);
        return Math.max(1, Math.max(target, minSellFor(unitCost, f.item.name)));
    }

    private void accountSold(Flip f, int totalSold) {
        int sold = Math.min(totalSold, f.boughtQty);
        if (sold <= f.sold) return;
        int delta = sold - f.sold;
        long oldCost = f.buySpent * f.sold / f.boughtQty;
        long newCost = f.buySpent * sold / f.boughtQty;
        profit += (long) f.sellPrice * delta - tax(f.item.name, f.sellPrice) * delta - (newCost - oldCost);
        f.sold = sold;
    }


    private void updateStats() {
        activeFlips = flips.size();
        buyingFlips = sellingFlips = 0;
        long used = 0;
        for (Flip f : flips.values()) {
            if (f.selling) sellingFlips++; else buyingFlips++;
            used += f.selling ? Math.max(0, f.buySpent) : (long) f.buyPrice * Math.max(0, f.requestedQty);
        }
        capitalUsed = used;
    }

    private int remainingLimit(int id, int limit) {
        LimitWindow w = limits.get(id);
        if (w == null || System.currentTimeMillis() - w.started >= FOUR_HOURS) return limit;
        return Math.max(0, limit - w.used);
    }

    private void useLimit(int id, int qty) {
        long now = System.currentTimeMillis();
        LimitWindow w = limits.get(id);
        if (w == null || now - w.started >= FOUR_HOURS) limits.put(id, new LimitWindow(now, qty));
        else w.used += qty;
    }

    private Set<String> whitelist() {
        String s = config.customItems();
        if (s == null || s.isBlank()) return Collections.emptySet();
        Set<String> out = new HashSet<>();
        for (String x : s.split(",")) if (!x.isBlank()) out.add(x.trim().toLowerCase(Locale.ROOT));
        return out;
    }

    private int edge(int price) {
        return Math.max(1, (int) Math.round(price * clamp(config.edgePercent(), 0, 5) / 100.0));
    }

    private static boolean sane(Quote q, Hour h) {
        if (h.avgHigh <= 0 || h.avgLow <= 0) return true;
        double current = (q.high + q.low) / 2.0, average = (h.avgHigh + h.avgLow) / 2.0;
        return current > average * 0.75 && current < average * 1.25;
    }

    private static long tax(String name, int sell) {
        if (TAX_FREE.contains(name.toLowerCase(Locale.ROOT))) return 0;
        return Math.min(5_000_000L, (long) Math.floor(sell * 0.02));
    }

    private static int minSellFor(long cost, String name) {
        long target = cost + 1, p;
        if (TAX_FREE.contains(name.toLowerCase(Locale.ROOT))) p = target;
        else if (target <= 245_000_000L) p = (long) Math.ceil(target / 0.98);
        else p = target + 5_000_000L;
        return (int) Math.min(Integer.MAX_VALUE, p);
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
    private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
    public static Duration runtime() { return Duration.ofMillis(Math.max(0, System.currentTimeMillis() - started)); }

    private static final class Flip {
        final Item item;
        final int buyPrice, requestedQty;
        int sellPrice, boughtQty, sold, reprices;
        long buySpent, changed = System.currentTimeMillis();
        boolean selling;
        Flip(Candidate c) { item = c.item; buyPrice = c.buy; requestedQty = c.qty; sellPrice = c.sell; }
    }

    private static final class LimitWindow {
        final long started;
        int used;
        LimitWindow(long started, int used) { this.started = started; this.used = used; }
    }

    private static final class Candidate {
        final Item item;
        final int buy, sell, qty;
        final long net, profit;
        final double roi;
        Candidate(Item item, int buy, int sell, int qty, long net, double roi, long profit) {
            this.item = item; this.buy = buy; this.sell = sell; this.qty = qty; this.net = net; this.roi = roi; this.profit = profit;
        }
    }

    private static final class Item {
        final int id, limit;
        final String name;
        final boolean members;
        Item(int id, String name, boolean members, int limit) { this.id = id; this.name = name; this.members = members; this.limit = limit; }
    }

    private static final class Quote {
        final int high, low;
        final long highTime, lowTime;
        Quote(int high, int low, long highTime, long lowTime) { this.high = high; this.low = low; this.highTime = highTime; this.lowTime = lowTime; }
    }

    private static final class Hour {
        final int avgHigh, avgLow, highVolume, lowVolume;
        Hour(int avgHigh, int avgLow, int highVolume, int lowVolume) {
            this.avgHigh = avgHigh; this.avgLow = avgLow; this.highVolume = highVolume; this.lowVolume = lowVolume;
        }
    }

    private static final class Market {
        private static final String BASE = "https://prices.runescape.wiki/api/v1/osrs/";
        private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        final Map<Integer, Item> items = new HashMap<>();
        final Map<Integer, Quote> quotes = new HashMap<>();
        final Map<Integer, Hour> hours = new HashMap<>();
        long mappedAt, pricesAt;

        void refreshIfNeeded() {
            long now = System.currentTimeMillis();
            try {
                if (items.isEmpty() || now - mappedAt > 6 * 60 * 60 * 1000L) loadMapping();
                if (quotes.isEmpty() || now - pricesAt > 60_000L) {
                    loadLatest(); loadHour(); pricesAt = now;
                }
            } catch (Exception e) {
                log.warn("Market refresh failed: {}", e.getMessage());
            }
        }

        private void loadMapping() throws Exception {
            JsonArray a = new JsonParser().parse(get("mapping")).getAsJsonArray();
            Map<Integer, Item> next = new HashMap<>();
            for (JsonElement e : a) {
                JsonObject o = e.getAsJsonObject();
                int id = n(o, "id"), limit = n(o, "limit");
                String name = text(o, "name");
                if (id > 0 && limit > 0 && !name.isBlank()) next.put(id, new Item(id, name, bool(o, "members"), limit));
            }
            items.clear(); items.putAll(next); mappedAt = System.currentTimeMillis();
        }

        private void loadLatest() throws Exception {
            JsonObject data = new JsonParser().parse(get("latest")).getAsJsonObject().getAsJsonObject("data");
            Map<Integer, Quote> next = new HashMap<>();
            for (Map.Entry<String, JsonElement> e : data.entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                next.put(Integer.parseInt(e.getKey()), new Quote(n(o, "high"), n(o, "low"), l(o, "highTime"), l(o, "lowTime")));
            }
            quotes.clear(); quotes.putAll(next);
        }

        private void loadHour() throws Exception {
            JsonObject data = new JsonParser().parse(get("1h")).getAsJsonObject().getAsJsonObject("data");
            Map<Integer, Hour> next = new HashMap<>();
            for (Map.Entry<String, JsonElement> e : data.entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                next.put(Integer.parseInt(e.getKey()), new Hour(n(o, "avgHighPrice"), n(o, "avgLowPrice"), n(o, "highPriceVolume"), n(o, "lowPriceVolume")));
            }
            hours.clear(); hours.putAll(next);
        }

        private String get(String path) throws Exception {
            HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + path)).timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "KSP-GE-Flipper/" + KspGEFlipperPlugin.VERSION + " (https://github.com/KSPOG/ksppluginsrelease)")
                    .GET().build();
            HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) throw new IllegalStateException(path + " HTTP " + r.statusCode());
            return r.body();
        }

        private static int n(JsonObject o, String k) { return missing(o, k) ? 0 : o.get(k).getAsInt(); }
        private static long l(JsonObject o, String k) { return missing(o, k) ? 0 : o.get(k).getAsLong(); }
        private static boolean bool(JsonObject o, String k) { return !missing(o, k) && o.get(k).getAsBoolean(); }
        private static String text(JsonObject o, String k) { return missing(o, k) ? "" : o.get(k).getAsString(); }
        private static boolean missing(JsonObject o, String k) { return o == null || !o.has(k) || o.get(k).isJsonNull(); }
    }
}
