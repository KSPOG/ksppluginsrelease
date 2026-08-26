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

abstract class KspGEFlipperEmbeddedCore {
    protected static final String WIKI = "https://prices.runescape.wiki/api/v1/osrs/";
    protected static final Set<String> TAX_EXEMPT = new HashSet<>(Arrays.asList(
            "old school bond", "chisel", "gardening trowel", "glassblowing pipe", "hammer", "needle",
            "pestle and mortar", "rake", "saw", "secateurs", "seed dibber", "shears", "spade", "watering can"
    ));

    protected final KspGEFlipperConfig config;
    protected final KspGEFlipperEmbeddedStore store;
    protected final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    protected final Map<Integer, ItemMeta> items = new HashMap<>();
    protected final Map<Integer, MarketSnapshot> market = new HashMap<>();
    protected final Map<Integer, Deque<PendingForecast>> pendingForecasts = new HashMap<>();
    protected final Map<Integer, Long> lastForecastPersist = new HashMap<>();
    protected final Map<Integer, KspGEFlipperBackendDtos.DumpSignal> activeDumps = new HashMap<>();
    protected long nextMappingRefresh;
    protected long nextMarketRefresh;
    protected long nextSave;
    protected long lastMarketSuccess;
    protected String lastMarketError = "not-started";
    protected KspGEFlipperBackendDtos.Suggestion lastSuggestion;

    protected KspGEFlipperEmbeddedCore(KspGEFlipperConfig config) {
        this.config = config;
        this.store = new KspGEFlipperEmbeddedStore();
    }

    // ---------------- market data ----------------

    protected void refreshMarket(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now < nextMarketRefresh) return;
        nextMarketRefresh = now + Math.max(10, config.embeddedMarketPollSeconds()) * 1000L;
        try {
            if (items.isEmpty() || now >= nextMappingRefresh) loadMapping();
            JsonObject latest = data(get("latest"));
            JsonObject five = data(get("5m"));
            JsonObject hour = data(get("1h"));
            JsonObject day = null;
            try { day = data(get("24h")); } catch (Exception ignored) { }
            long observed = System.currentTimeMillis();
            for (ItemMeta item : items.values()) {
                JsonObject l = object(latest, Integer.toString(item.id));
                if (l == null) continue;
                long high = longValue(l, "high", 0), low = longValue(l, "low", 0);
                if (high <= 0 || low <= 0) continue;
                JsonObject f = object(five, Integer.toString(item.id));
                JsonObject h = object(hour, Integer.toString(item.id));
                JsonObject d = day == null ? null : object(day, Integer.toString(item.id));
                MarketSnapshot s = new MarketSnapshot();
                s.item = item;
                s.observedAt = observed;
                s.latestHigh = high;
                s.latestLow = low;
                s.latestHighTime = longValue(l, "highTime", observed / 1000L) * 1000L;
                s.latestLowTime = longValue(l, "lowTime", observed / 1000L) * 1000L;
                s.avg5mHigh = longValue(f, "avgHighPrice", high);
                s.avg5mLow = longValue(f, "avgLowPrice", low);
                s.volume5mHigh = longValue(f, "highPriceVolume", 0);
                s.volume5mLow = longValue(f, "lowPriceVolume", 0);
                s.avg1hHigh = longValue(h, "avgHighPrice", high);
                s.avg1hLow = longValue(h, "avgLowPrice", low);
                s.volume1hHigh = longValue(h, "highPriceVolume", 0);
                s.volume1hLow = longValue(h, "lowPriceVolume", 0);
                s.dailyVolume = longValue(d, "highPriceVolume", 0) + longValue(d, "lowPriceVolume", 0);
                market.put(item.id, s);
                appendHistoricalPoint(s);
                validateForecasts(s);
            }
            detectDumps();
            lastMarketSuccess = observed;
            lastMarketError = "";
        } catch (Exception e) {
            lastMarketError = e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage());
        }
        periodicSave();
    }

    protected void loadMapping() throws Exception {
        JsonElement parsed = new JsonParser().parse(get("mapping"));
        if (!parsed.isJsonArray()) throw new IllegalStateException("Wiki mapping is not an array");
        Map<Integer, ItemMeta> fresh = new HashMap<>();
        for (JsonElement element : parsed.getAsJsonArray()) {
            if (!element.isJsonObject()) continue;
            JsonObject o = element.getAsJsonObject();
            ItemMeta item = new ItemMeta();
            item.id = intValue(o, "id", 0);
            item.name = text(o, "name", "");
            item.members = boolValue(o, "members", false);
            item.buyLimit = intValue(o, "limit", 0);
            item.tradeable = item.buyLimit > 0;
            if (item.id > 0 && !item.name.isBlank()) fresh.put(item.id, item);
        }
        if (!fresh.isEmpty()) {
            items.clear();
            items.putAll(fresh);
            nextMappingRefresh = System.currentTimeMillis() + Duration.ofHours(6).toMillis();
        }
    }

    protected void appendHistoricalPoint(MarketSnapshot s) {
        // Persist one point per five minutes and only for items liquid enough to ever pass a risk profile.
        if (s.matchedHourlyVolume() < 25) return;
        List<MarketPoint> existing = store.marketHistory(s.item.id, 1);
        if (!existing.isEmpty() && s.observedAt - existing.get(existing.size() - 1).timestamp < 300_000L) return;
        MarketPoint p = new MarketPoint();
        p.itemId = s.item.id;
        p.timestamp = s.observedAt;
        p.high = s.latestHigh;
        p.low = s.latestLow;
        p.highVolume = Math.max(0, s.volume5mHigh);
        p.lowVolume = Math.max(0, s.volume5mLow);
        p.resolution = "LATEST";
        store.appendMarketPoint(p, Math.max(96, config.embeddedHistoryPoints()));
    }

    protected String get(String endpoint) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(WIKI + endpoint))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "KSP-GE-Flipper-Embedded/" + KspGEFlipperPlugin.VERSION + " (https://github.com/KSPOG/ksppluginsrelease)")
                .GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IllegalStateException(endpoint + " HTTP " + response.statusCode());
        return response.body();
    }

    protected static JsonObject data(String json) {
        JsonObject root = new JsonParser().parse(json).getAsJsonObject();
        return root.has("data") && root.get("data").isJsonObject() ? root.getAsJsonObject("data") : new JsonObject();
    }

    // ---------------- features / forecast ----------------

    protected Features features(MarketSnapshot s, List<MarketPoint> history) {
        Features f = new Features();
        double mid = Math.max(1.0, s.mid());
        f.spread = s.latestHigh - s.latestLow;
        f.spreadPct = f.spread / mid;
        double mid5 = midpoint(s.avg5mHigh, s.avg5mLow, mid);
        double mid1 = midpoint(s.avg1hHigh, s.avg1hLow, mid);
        f.return5m = (mid - mid5) / Math.max(1.0, mid5);
        f.return1h = (mid - mid1) / Math.max(1.0, mid1);

        List<Double> mids = new ArrayList<>();
        for (MarketPoint p : history) if (p.high > 0 && p.low > 0 && "LATEST".equals(p.resolution)) mids.add((p.high + p.low) / 2.0);
        double median = median(mids, mid);
        double sigma = robustSigma(mids, median);
        f.distanceFromRollingMedian = (mid - median) / Math.max(1.0, median);
        f.volatility1h = sigma / Math.max(1.0, median);
        double recentVol = Math.max(1.0, s.volume5mHigh + s.volume5mLow);
        double hourly = Math.max(1.0, s.volume1hHigh + s.volume1hLow);
        f.volumeAcceleration = recentVol * 12.0 / hourly;
        f.highLowImbalance = (s.volume1hHigh - s.volume1hLow) / (double) Math.max(1L, s.volume1hHigh + s.volume1hLow);
        f.liquidityScore = clamp(Math.log10(s.matchedHourlyVolume() + 1.0) / 5.0, 0, 1);
        f.dataAgeSeconds = Math.max((System.currentTimeMillis() - s.latestHighTime) / 1000.0, (System.currentTimeMillis() - s.latestLowTime) / 1000.0);
        f.priceVelocity = velocity(history);
        f.abnormalityScore = Math.abs(f.distanceFromRollingMedian) / Math.max(0.002, f.volatility1h) + Math.max(0, f.volumeAcceleration - 1) * 0.5;
        f.return24h = historyReturn(history, Duration.ofHours(24).toMillis());
        return f;
    }

    protected Forecast forecast(MarketSnapshot s, Features f, List<MarketPoint> history, int timeframeMinutes) {
        List<Double> lows = new ArrayList<>(), highs = new ArrayList<>();
        for (MarketPoint p : history) {
            if (!"LATEST".equals(p.resolution) || p.low <= 0 || p.high <= 0) continue;
            lows.add((double) p.low);
            highs.add((double) p.high);
        }
        lows.add((double) s.latestLow);
        highs.add((double) s.latestHigh);
        Forecast fc = new Forecast();
        fc.itemId = s.item.id;
        fc.generatedAt = System.currentTimeMillis();
        if (s.latestHigh <= 0 || s.latestLow <= 0) return fc;
        double lowEwma = ewma(lows, 0.30), highEwma = ewma(highs, 0.30);
        double lowSlope = slope(lows), highSlope = slope(highs);
        double lowSigma = robustSigma(lows, lowEwma), highSigma = robustSigma(highs, highEwma);
        fc.robustSigmaPct = Math.max(lowSigma / Math.max(1, lowEwma), highSigma / Math.max(1, highEwma));
        int[] horizons = horizons(timeframeMinutes);
        for (int horizon : horizons) {
            double scale = Math.sqrt(Math.max(1, horizon) / 5.0);
            double lowBand = 0.674 * lowSigma * scale, highBand = 0.674 * highSigma * scale;
            double trendScale = Math.min(horizon / 5.0, 24.0);
            double lowMean = Math.max(1, lowEwma + lowSlope * trendScale);
            double highMean = Math.max(lowMean + 1, highEwma + highSlope * trendScale);
            fc.low.add(forecastPoint(fc.generatedAt + horizon * 60_000L, lowMean, Math.max(1, lowMean - lowBand), lowMean + lowBand));
            fc.high.add(forecastPoint(fc.generatedAt + horizon * 60_000L, highMean, Math.max(1, highMean - highBand), highMean + highBand));
        }
        double freshness = 1 - Math.min(1, f.dataAgeSeconds / Math.max(1, config.quoteAge()));
        double historyScore = Math.min(1, lows.size() / 96.0);
        fc.confidence = clamp(0.40 + freshness * 0.22 + f.liquidityScore * 0.20 + historyScore * 0.15 - fc.robustSigmaPct * 2.0, 0.05, 0.97);
        fc.quality = f.dataAgeSeconds > config.quoteAge() ? "STALE" : lows.size() < 12 ? "LIMITED" : fc.confidence >= 0.55 ? "GOOD" : "LIMITED";
        long last = lastForecastPersist.getOrDefault(s.item.id, 0L);
        if (fc.generatedAt - last >= 60_000L) {
            store.putForecast(fc);
            lastForecastPersist.put(s.item.id, fc.generatedAt);
            enqueueForecast(fc);
        }
        return fc;
    }

    protected void enqueueForecast(Forecast fc) {
        Deque<PendingForecast> q = pendingForecasts.computeIfAbsent(fc.itemId, k -> new ArrayDeque<>());
        int n = Math.min(fc.low.size(), fc.high.size());
        for (int i = 0; i < n; i++) {
            PendingForecast p = new PendingForecast();
            p.time = fc.low.get(i).time;
            p.lowMean = fc.low.get(i).mean;
            p.lowQ25 = fc.low.get(i).q25;
            p.lowQ75 = fc.low.get(i).q75;
            p.highMean = fc.high.get(i).mean;
            p.highQ25 = fc.high.get(i).q25;
            p.highQ75 = fc.high.get(i).q75;
            q.addLast(p);
        }
        while (q.size() > 64) q.removeFirst();
    }

    protected void validateForecasts(MarketSnapshot s) {
        Deque<PendingForecast> q = pendingForecasts.get(s.item.id);
        if (q == null) return;
        long now = System.currentTimeMillis();
        while (!q.isEmpty() && q.peekFirst().time <= now) {
            PendingForecast p = q.removeFirst();
            double mae = (Math.abs(s.latestLow - p.lowMean) + Math.abs(s.latestHigh - p.highMean)) / 2.0;
            double coverage = (inside(s.latestLow, p.lowQ25, p.lowQ75) ? 0.5 : 0) + (inside(s.latestHigh, p.highQ25, p.highQ75) ? 0.5 : 0);
            updateMetricEwma("forecast.mae", mae, 0.08);
            updateMetricEwma("forecast.iqrCoverage", coverage, 0.08);
        }
    }

    // ---------------- candidates / optimizer ----------------

    protected CalibrationBucket calibrationFor(String type, int itemId) {
        CalibrationBucket typeBucket = store.calibration("type:" + safeType(type));
        CalibrationBucket itemBucket = store.calibration("item:" + itemId);
        if (!config.enableSelfCalibration()) return neutralBucket();
        long warmup = Math.max(1, config.calibrationWarmupSamples());
        if (typeBucket.samples < warmup && itemBucket.samples < warmup) return neutralBucket();
        double itemWeight = itemBucket.samples <= 0 ? 0 : Math.min(0.70, itemBucket.samples / 20.0);
        CalibrationBucket blended = new CalibrationBucket();
        blended.samples = typeBucket.samples + itemBucket.samples;
        blended.durationFactor = blend(typeBucket.durationFactor, itemBucket.durationFactor, itemWeight);
        blended.fillFactor = blend(typeBucket.fillFactor, itemBucket.fillFactor, itemWeight);
        blended.profitFactor = blend(typeBucket.profitFactor, itemBucket.profitFactor, itemWeight);
        blended.priceErrorPct = blend(typeBucket.priceErrorPct, itemBucket.priceErrorPct, itemWeight);
        blended.modificationRate = blend(typeBucket.modificationRate, itemBucket.modificationRate, itemWeight);
        blended.abortRate = blend(typeBucket.abortRate, itemBucket.abortRate, itemWeight);
        return blended;
    }

    protected CalibrationBucket neutralBucket() { return new CalibrationBucket(); }

    protected boolean eligible(KspGEFlipperBackendDtos.AccountState a, MarketSnapshot s) {
        ItemMeta i = s.item;
        if (!i.tradeable || i.buyLimit <= 0 || s.latestHigh <= s.latestLow || s.latestLow <= 0) return false;
        if ((a.f2pOnly || !a.worldMember || !a.accountMember) && i.members) return false;
        String normalized = i.name.toLowerCase(Locale.ROOT);
        if (a.blockedItems.contains(i.id) || a.blockedItemNames.contains(normalized)) return false;
        return a.allowedItemNames.isEmpty() || a.allowedItemNames.contains(normalized);
    }

    protected boolean isDump(Features f, KspGEFlipperBackendDtos.Strategy p) {
        return p.dumpEnabled && p.timeframeMinutes <= 30
                && f.distanceFromRollingMedian < -Math.max(0.005, config.dumpDropPercent() / 100.0)
                && f.volumeAcceleration > 1.20 && f.abnormalityScore > 2.0;
    }

    protected int quantity(KspGEFlipperBackendDtos.AccountState a, MarketSnapshot s, long buy, RiskSpec risk, boolean dump) {
        double configPct = Math.max(0.01, Math.min(1, a.strategy.maxItemExposurePct / 100.0));
        double pct = Math.min(configPct, risk.exposure) * (dump ? 0.65 : 1.0);
        long usableGp = Math.max(0, a.gp - config.reserveCoins());
        long byCapital = (long) Math.floor(usableGp * pct / Math.max(1, buy));
        long byLiquidity = (long) Math.floor(s.matchedHourlyVolume() * (a.strategy.timeframeMinutes / 60.0) * risk.participation * (dump ? 0.6 : 1));
        long used = 0;
        for (KspGEFlipperBackendDtos.Offer o : a.offers) if (o.itemId == s.item.id && "BUY".equalsIgnoreCase(o.side)) used += o.filledQuantity;
        long remaining = Math.max(0, s.item.buyLimit - used);
        long qty = Math.min(remaining, Math.min(byCapital, Math.max(1, byLiquidity)));
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, qty));
    }

    protected Estimate estimate(MarketSnapshot s, Features f, String side, long price, int qty, double confidence, CalibrationBucket calibration) {
        double spread = Math.max(1, s.latestHigh - s.latestLow);
        double aggression = "BUY".equals(side) ? (price - s.latestLow) / spread : (s.latestHigh - price) / spread;
        aggression = clamp(aggression, 0, 1);
        double volumeScore = clamp(Math.log10(s.matchedHourlyVolume() + 1) / 5.0, 0, 1);
        double baseFill = clamp(0.20 + aggression * 0.40 + confidence * 0.25 + volumeScore * 0.15, 0.15, 0.98);
        double flowMinutes = 60.0 * qty / Math.max(1, s.matchedHourlyVolume());
        double priceFactor = 1.30 - aggression * 0.65;
        double uncertainty = f.volatility1h + Math.abs(f.return5m - f.return1h) * 0.5;
        double minutes = clamp((3 + flowMinutes * 2.5) * priceFactor * (1 + uncertainty * 6) / baseFill, 2, 360);
        Estimate e = new Estimate();
        e.fill = clamp(baseFill * bounded(calibration.fillFactor), 0.10, 0.99);
        e.minutes = clamp(minutes * bounded(calibration.durationFactor), 2, 720);
        e.aggression = aggression;
        return e;
    }

    protected double bounded(double value) {
        double max = Math.max(0, config.calibrationMaxAdjustmentPercent()) / 100.0;
        return clamp(value <= 0 ? 1.0 : value, 1.0 - max, 1.0 + max);
    }

    protected long taxPerItem(String name, long sell) {
        if (sell < 50 || TAX_EXEMPT.contains(name == null ? "" : name.toLowerCase(Locale.ROOT))) return 0;
        return Math.min(5_000_000L, (long) Math.floor(sell * 0.02));
    }

    protected long postTaxUnit(String name, long sell) { return Math.max(0, sell - taxPerItem(name, sell)); }

    protected long minimumBreakEvenSell(String name, long cost) {
        if (cost <= 0) return 1;
        if (TAX_EXEMPT.contains(name == null ? "" : name.toLowerCase(Locale.ROOT)) || cost < 49) return cost + 1;
        long sell = (long) Math.ceil((cost + 1) / 0.98);
        for (int i = 0; i < 8 && postTaxUnit(name, sell) <= cost; i++) sell++;
        return Math.max(1, sell);
    }

    protected void persistRecommendation(KspGEFlipperBackendDtos.AccountState account, KspGEFlipperBackendDtos.Suggestion s) {
        if (s.id == null) return;
        RecommendationRecord r = new RecommendationRecord();
        r.suggestion = s;
        r.accountKey = account.accountKey;
        r.createdAt = System.currentTimeMillis();
        MarketSnapshot snapshot = market.get(s.itemId);
        if (snapshot != null) {
            Features f = features(snapshot, store.marketHistory(s.itemId, 512));
            r.features.put("spreadPct", f.spreadPct);
            r.features.put("return5m", f.return5m);
            r.features.put("return1h", f.return1h);
            r.features.put("volatility1h", f.volatility1h);
            r.features.put("volumeAcceleration", f.volumeAcceleration);
            r.features.put("liquidityScore", f.liquidityScore);
            r.features.put("abnormalityScore", f.abnormalityScore);
        }
        store.putRecommendation(account.accountKey, r);
    }

    protected void updateRecommendationMetrics() {
        long total = 0, modify = 0, abort = 0;
        for (RecommendationRecord r : store.state().recommendations.values()) {
            if (r == null || r.suggestion == null) continue;
            String type = r.suggestion.type == null ? "WAIT" : r.suggestion.type;
            if (!"WAIT".equals(type)) total++;
            if (type.startsWith("MODIFY")) modify++;
            if ("ABORT".equals(type)) abort++;
        }
        store.putMetric("recommendation.modifyRate", total == 0 ? 0 : modify / (double) total, total);
        store.putMetric("recommendation.abortRate", total == 0 ? 0 : abort / (double) total, total);
    }

    protected void updateAcceptance(String accountKey) {
        List<KspGEFlipperBackendDtos.TradeExecution> tx = store.executions(accountKey, 1000);
        if (tx.isEmpty()) return;
        long accepted = 0;
        for (KspGEFlipperBackendDtos.TradeExecution e : tx) if (e.recommendationOriginatedTrade) accepted++;
        store.putMetric("recommendation.acceptance", accepted / (double) tx.size(), tx.size());
    }

    protected void updateMetricEwma(String key, double sample, double alpha) {
        Metric old = store.metric(key);
        long count = old == null ? 1 : old.samples + 1;
        double value = old == null ? sample : old.value * (1 - alpha) + sample * alpha;
        store.putMetric(key, value, count);
    }

    protected double metricValue(String key) { Metric m = store.metric(key); return m == null ? 0 : m.value; }

    protected double actionRate(String type) {
        return "ABORT".equals(type) ? metricValue("recommendation.abortRate") : metricValue("recommendation.modifyRate");
    }

    protected void validatePersistentState() {
        // Ensure no impossible position survives a corrupt/older snapshot.
        for (Position p : store.state().positions.values()) {
            if (p.boughtQuantity < 0) p.boughtQuantity = 0;
            if (p.soldQuantity < 0) p.soldQuantity = 0;
            if (p.boughtQuantity > 0 && p.realizedCostBasis == 0 && p.remainingCostBasis == 0) {
                int closed = Math.min(p.soldQuantity, p.boughtQuantity);
                p.realizedCostBasis = p.totalBuyCost * closed / Math.max(1, p.boughtQuantity);
                p.remainingCostBasis = Math.max(0, p.totalBuyCost - p.realizedCostBasis);
            }
            if (p.boughtQuantity > 0 && p.soldQuantity >= p.boughtQuantity) {
                p.status = "CLOSED";
                p.remainingCostBasis = 0;
            }
        }
    }

    protected void periodicSave() {
        long now = System.currentTimeMillis();
        if (now >= nextSave) {
            nextSave = now + 300_000L;
            store.saveIfDirty();
        }
    }

    protected static void addIf(List<Candidate> out, Candidate c) { if (c != null) out.add(c); }
    protected static double relativeImprovement(double current, double replacement) { return (replacement - current) / Math.max(1, Math.abs(current)); }
    protected static double riskScore(Features f, Forecast fc, long gp, long buy, int qty) {
        double exposure = buy * (double) qty / Math.max(1, gp);
        return clamp(fc.robustSigmaPct * 3 + f.volatility1h * 2 + (1 - f.liquidityScore) * 0.5 + exposure, 0, 3);
    }
    protected static double utility(double gph, double confidence, double fill, double riskScore, double capital, double minutes, RiskSpec r) {
        double riskPenalty = r.riskWeight * riskScore * 0.18;
        double capitalCost = capital * (minutes / 60.0) * 0.00002;
        double slotCost = gph * 0.03;
        return gph * confidence * fill * (1 - riskPenalty) - capitalCost - slotCost;
    }
    protected static String explain(String type, Features f, long profit, double mins, double confidence, CalibrationBucket c) {
        return type + ": liquidity=" + pct(f.liquidityScore) + ", spread=" + pct(f.spreadPct) + ", confidence=" + pct(confidence)
                + ", expectedProfit=" + profit + ", expectedMinutes=" + Math.round(mins) + ", calibrationSamples=" + c.samples;
    }
    protected static RiskSpec risk(String value) {
        String r = value == null ? "MEDIUM" : value.toUpperCase(Locale.ROOT);
        if ("LOW".equals(r)) return new RiskSpec(.15, .72, .035, .30, 500, 1.35);
        if ("HIGH".equals(r)) return new RiskSpec(.50, .45, .09, .05, 25, .65);
        return new RiskSpec(.30, .58, .055, .15, 100, 1.0);
    }
    protected static ForecastPoint forecastPoint(long time, double mean, double q25, double q75) { ForecastPoint p = new ForecastPoint(); p.time=time;p.mean=mean;p.q25=q25;p.q75=q75;return p; }
    protected static ForecastPoint point(List<ForecastPoint> points, int index) { return points.get(Math.max(0, Math.min(points.size() - 1, index))); }
    protected static int[] horizons(int t) { if (t <= 15) return new int[]{5,10,15,30}; if (t <= 60) return new int[]{5,15,30,60,120}; if (t <= 240) return new int[]{15,30,60,120,240,480}; return new int[]{30,60,120,240,480,1440}; }
    protected static double ewma(List<Double> v, double a) { double x=v.get(0); for(int i=1;i<v.size();i++) x=a*v.get(i)+(1-a)*x; return x; }
    protected static double slope(List<Double> v) { int n=Math.min(24,v.size()); if(n<3)return 0; double sx=0,sy=0,sxx=0,sxy=0; for(int j=0;j<n;j++){double x=j,y=v.get(v.size()-n+j);sx+=x;sy+=y;sxx+=x*x;sxy+=x*y;} double den=n*sxx-sx*sx; return Math.abs(den)<1e-9?0:(n*sxy-sx*sy)/den; }
    protected static double robustSigma(List<Double> v, double fallback) { if(v.size()<3)return Math.max(1,fallback*0.01); double med=median(v,fallback); List<Double>d=new ArrayList<>();for(double x:v)d.add(Math.abs(x-med));return Math.max(1,median(d,1)*1.4826); }
    protected static double median(List<Double> v, double fallback) { if(v.isEmpty())return fallback; List<Double>s=new ArrayList<>(v);Collections.sort(s);int n=s.size();return n%2==1?s.get(n/2):(s.get(n/2-1)+s.get(n/2))/2; }
    protected static double midpoint(long h,long l,double fallback){return h>0&&l>0?(h+l)/2.0:fallback;}
    protected static double velocity(List<MarketPoint> h){List<MarketPoint> l=new ArrayList<>();for(MarketPoint p:h)if("LATEST".equals(p.resolution))l.add(p);if(l.size()<2)return 0;MarketPoint a=l.get(Math.max(0,l.size()-4)),b=l.get(l.size()-1);double am=(a.high+a.low)/2.0,bm=(b.high+b.low)/2.0;double mins=Math.max(1,(b.timestamp-a.timestamp)/60000.0);return (bm-am)/Math.max(1,am)/mins;}
    protected static double historyReturn(List<MarketPoint> h,long horizon){List<MarketPoint> l=new ArrayList<>();for(MarketPoint p:h)if("LATEST".equals(p.resolution))l.add(p);if(l.size()<2)return 0;MarketPoint last=l.get(l.size()-1),first=l.get(0);for(int i=l.size()-2;i>=0;i--)if(last.timestamp-l.get(i).timestamp>=horizon){first=l.get(i);break;}double a=(first.high+first.low)/2.0,b=(last.high+last.low)/2.0;return (b-a)/Math.max(1,a);}
    protected static boolean inside(double v,double lo,double hi){return v>=lo&&v<=hi;}
    protected static double clamp(double v,double lo,double hi){return Math.max(lo,Math.min(hi,v));}
    protected static String pct(double x){return String.format(Locale.ROOT,"%.1f%%",x*100);}
    protected static long safeMultiply(long a,int b){if(a==0||b==0)return 0;if(a>0&&a>Long.MAX_VALUE/b)return Long.MAX_VALUE;if(a<0&&a<Long.MIN_VALUE/b)return Long.MIN_VALUE;return a*b;}
    protected static double blend(double a,double b,double w){return a*(1-w)+b*w;}
    protected static double ewmaValue(double old,double sample,double alpha){return old*(1-alpha)+sample*alpha;}
    protected static String safeType(String type){return type==null||type.isBlank()?"NORMAL_FLIP":type;}
    protected static long parseTime(String value,long fallback){try{return value==null?fallback:Instant.parse(value).toEpochMilli();}catch(Exception ignored){return fallback;}}

    protected static JsonObject object(JsonObject parent,String key){if(parent==null||!parent.has(key)||!parent.get(key).isJsonObject())return null;return parent.getAsJsonObject(key);}
    protected static long longValue(JsonObject o,String key,long fallback){try{return o!=null&&o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsLong():fallback;}catch(Exception e){return fallback;}}
    protected static int intValue(JsonObject o,String key,int fallback){return (int)Math.max(Integer.MIN_VALUE,Math.min(Integer.MAX_VALUE,longValue(o,key,fallback)));}
    protected static boolean boolValue(JsonObject o,String key,boolean fallback){try{return o!=null&&o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsBoolean():fallback;}catch(Exception e){return fallback;}}
    protected static String text(JsonObject o,String key,String fallback){try{return o!=null&&o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsString():fallback;}catch(Exception e){return fallback;}}

    protected static JsonObject marketJson(MarketSnapshot s){JsonObject o=new JsonObject();o.addProperty("itemId",s.item.id);o.addProperty("name",s.item.name);o.addProperty("latestHigh",s.latestHigh);o.addProperty("latestLow",s.latestLow);o.addProperty("avg5mHigh",s.avg5mHigh);o.addProperty("avg5mLow",s.avg5mLow);o.addProperty("avg1hHigh",s.avg1hHigh);o.addProperty("avg1hLow",s.avg1hLow);o.addProperty("matchedHourlyVolume",s.matchedHourlyVolume());return o;}
    protected static JsonObject featuresJson(Features f){JsonObject o=new JsonObject();o.addProperty("spread",f.spread);o.addProperty("spreadPct",f.spreadPct);o.addProperty("return5m",f.return5m);o.addProperty("return1h",f.return1h);o.addProperty("return24h",f.return24h);o.addProperty("volatility1h",f.volatility1h);o.addProperty("volumeAcceleration",f.volumeAcceleration);o.addProperty("priceVelocity",f.priceVelocity);o.addProperty("distanceFromRollingMedian",f.distanceFromRollingMedian);o.addProperty("highLowImbalance",f.highLowImbalance);o.addProperty("liquidityScore",f.liquidityScore);o.addProperty("abnormalityScore",f.abnormalityScore);o.addProperty("dataAgeSeconds",f.dataAgeSeconds);return o;}
    protected static JsonObject forecastJson(Forecast f){JsonObject o=new JsonObject();o.addProperty("itemId",f.itemId);o.addProperty("confidence",f.confidence);o.addProperty("quality",f.quality);o.addProperty("robustSigmaPct",f.robustSigmaPct);o.add("low",forecastArray(f.low));o.add("high",forecastArray(f.high));return o;}
    protected static JsonArray forecastArray(List<ForecastPoint> points){JsonArray a=new JsonArray();for(ForecastPoint p:points){JsonObject o=new JsonObject();o.addProperty("time",Instant.ofEpochMilli(p.time).toString());o.addProperty("mean",p.mean);o.addProperty("q25",p.q25);o.addProperty("q75",p.q75);a.add(o);}return a;}

    protected static final class PendingForecast { long time; double lowMean,lowQ25,lowQ75,highMean,highQ25,highQ75; }
    protected static final class Estimate { double fill,minutes,aggression; }
    protected static final class RiskSpec { final double exposure,minConfidence,participation,minLiquidity; final long minHourlyVolume; final double riskWeight; RiskSpec(double e,double c,double p,double l,long v,double r){exposure=e;minConfidence=c;participation=p;minLiquidity=l;minHourlyVolume=v;riskWeight=r;} }
    protected static final class SlotBudget { int total,usable,active,free,dumpReserved,dumpActive,normalActive; }
    protected static final class PortfolioView { long realized,unrealized; List<Position> positions = new ArrayList<>(); }

    protected abstract void detectDumps();
}
