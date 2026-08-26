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

abstract class KspGEFlipperEmbeddedPolicy extends KspGEFlipperEmbeddedCore {
    protected KspGEFlipperEmbeddedPolicy(KspGEFlipperConfig config) { super(config); }

    protected List<Candidate> generateCandidates(KspGEFlipperBackendDtos.AccountState a) {
        List<Candidate> out = new ArrayList<>();
        RiskSpec risk = risk(a.strategy.riskLevel);
        for (MarketSnapshot s : market.values()) {
            if (!eligible(a, s)) continue;
            List<MarketPoint> history = store.marketHistory(s.item.id, 512);
            Features f = features(s, history);
            if (f.dataAgeSeconds > config.quoteAge() || f.liquidityScore < risk.minLiquidity || s.matchedHourlyVolume() < Math.max(config.minHourlyVolume(), risk.minHourlyVolume)) continue;
            Forecast fc = forecast(s, f, history, a.strategy.timeframeMinutes);
            if ("INVALID".equals(fc.quality) || "STALE".equals(fc.quality) || fc.confidence < risk.minConfidence) continue;
            if (isDump(f, a.strategy)) {
                if (a.strategy.dumpEnabled) addIf(out, bestDump(a, s, f, fc, risk));
            } else addIf(out, bestNormal(a, s, f, fc, risk));
            if (a.strategy.allowBuyAndHold) addIf(out, bestHold(a, s, f, fc, risk));
        }
        out.sort((x, y) -> Double.compare(y.utility, x.utility));
        return out;
    }

    protected Candidate bestNormal(KspGEFlipperBackendDtos.AccountState a, MarketSnapshot s, Features f, Forecast fc, RiskSpec risk) {
        long spread = s.latestHigh - s.latestLow;
        if (spread <= 1) return null;
        long forecastHigh = Math.round(point(fc.high, Math.min(2, fc.high.size() - 1)).mean);
        long sellCeiling = Math.max(s.latestLow + 1, Math.min(s.latestHigh, forecastHigh));
        Candidate best = null;
        double[] steps = {0.05, 0.15, 0.30, 0.50};
        for (double buyStep : steps) for (double sellStep : steps) {
            long buy = s.latestLow + Math.max(1, Math.round(spread * buyStep));
            long sell = sellCeiling - Math.max(1, Math.round(spread * sellStep));
            if (sell <= buy) continue;
            long unitNet = postTaxUnit(s.item.name, sell) - buy;
            if (unitNet <= 0) continue;
            int qty = quantity(a, s, buy, risk, false);
            if (qty <= 0) continue;
            long rawProfit = safeMultiply(unitNet, qty);
            CalibrationBucket calibration = calibrationFor("NORMAL_FLIP", s.item.id);
            long profit = Math.round(rawProfit * bounded(calibration.profitFactor));
            if (profit < Math.max(a.strategy.minExpectedProfit, config.minTradeProfit())) continue;
            Estimate be = estimate(s, f, "BUY", buy, qty, fc.confidence, calibration);
            Estimate se = estimate(s, f, "SELL", sell, qty, fc.confidence, calibration);
            double duration = be.minutes + se.minutes;
            double completion = clamp(be.fill * se.fill, 0.05, 0.99);
            double gph = profit * 60.0 / Math.max(1, duration) * completion;
            if (config.minExpectedGpPerHour() > 0 && gph < config.minExpectedGpPerHour()) continue;
            double rs = riskScore(f, fc, a.gp, buy, qty);
            double utility = utility(gph, fc.confidence, completion, rs, buy * (double) qty, duration, risk);
            Candidate c = candidate(s, "NORMAL_FLIP", buy, sell, qty, rawProfit, profit, duration, gph, rs, fc.confidence, completion, f, utility, false,
                    explain("normal", f, profit, duration, fc.confidence, calibration));
            if (best == null || c.utility > best.utility) best = c;
        }
        return best;
    }

    protected Candidate bestDump(KspGEFlipperBackendDtos.AccountState a, MarketSnapshot s, Features f, Forecast fc, RiskSpec risk) {
        if (!a.strategy.dumpEnabled || fc.low.isEmpty()) return null;
        long spread = Math.max(1, s.latestHigh - s.latestLow);
        long buy = s.latestLow + Math.max(1, spread / 8);
        double recoveryProb = clamp(0.30 + fc.confidence * 0.35 + Math.min(0.2, Math.max(0, f.volumeAcceleration - 1) * 0.08) + Math.min(0.15, f.abnormalityScore / 20), 0.15, 0.92);
        long recovery = Math.max(buy + 1, Math.round(point(fc.low, Math.min(2, fc.low.size() - 1)).mean));
        recovery = Math.min(Math.max(recovery, s.avg5mLow), s.latestHigh);
        long unitNet = postTaxUnit(s.item.name, recovery) - buy;
        if (unitNet <= 0) return null;
        int qty = quantity(a, s, buy, risk, true);
        if (qty <= 0) return null;
        CalibrationBucket calibration = calibrationFor("DUMP", s.item.id);
        long rawProfit = safeMultiply(unitNet, qty);
        long profit = Math.round(rawProfit * bounded(calibration.profitFactor));
        if (profit < Math.max(a.strategy.minDumpExpectedProfit, config.dumpMinPredictedProfit())) return null;
        Estimate e = estimate(s, f, "BUY", buy, qty, fc.confidence, calibration);
        double duration = Math.max(5, a.strategy.timeframeMinutes * 0.65) * (1 + fc.robustSigmaPct * 5) / recoveryProb + e.minutes;
        duration *= bounded(calibration.durationFactor);
        double gph = profit * 60.0 / Math.max(1, duration) * recoveryProb;
        double rs = riskScore(f, fc, a.gp, buy, qty) * 1.25;
        double u = utility(gph, fc.confidence, recoveryProb, rs, buy * (double) qty, duration, risk);
        return candidate(s, "DUMP", buy, recovery, qty, rawProfit, profit, duration, gph, rs, fc.confidence, recoveryProb, f, u, false,
                explain("dump", f, profit, duration, fc.confidence, calibration));
    }

    protected Candidate bestHold(KspGEFlipperBackendDtos.AccountState a, MarketSnapshot s, Features f, Forecast fc, RiskSpec risk) {
        if (fc.high.isEmpty() || fc.low.isEmpty() || a.strategy.timeframeMinutes < 60) return null;
        ForecastPoint endHigh = fc.high.get(fc.high.size() - 1), endLow = fc.low.get(fc.low.size() - 1);
        long buy = s.latestLow + Math.max(1, (s.latestHigh - s.latestLow) / 5);
        long sell = Math.round(endHigh.q25);
        if (sell <= buy) return null;
        double expectedReturn = (postTaxUnit(s.item.name, sell) - buy) / (double) Math.max(1, buy);
        if (expectedReturn < Math.max(0.01, f.volatility1h * 1.2)) return null;
        int qty = quantity(a, s, buy, risk, false);
        if (qty <= 0) return null;
        CalibrationBucket calibration = calibrationFor("BUY_AND_HOLD", s.item.id);
        long rawProfit = safeMultiply(postTaxUnit(s.item.name, sell) - buy, qty);
        long profit = Math.round(rawProfit * bounded(calibration.profitFactor));
        if (profit < a.strategy.minExpectedProfit) return null;
        double mins = Math.max(a.strategy.timeframeMinutes, (endHigh.time - fc.generatedAt) / 60_000.0) * bounded(calibration.durationFactor);
        double downside = Math.max(0, buy - endLow.q25) / Math.max(1.0, buy);
        double probability = clamp(fc.confidence * (1 - downside) * bounded(calibration.fillFactor), 0.1, 0.95);
        double gph = profit * 60.0 / Math.max(1, mins) * probability;
        double rs = riskScore(f, fc, a.gp, buy, qty) * (1 + downside * 3);
        double u = utility(gph, fc.confidence, probability, rs, buy * (double) qty, mins, risk);
        return candidate(s, "BUY_AND_HOLD", buy, sell, qty, rawProfit, profit, mins, gph, rs, fc.confidence, probability, f, u, true,
                explain("hold", f, profit, mins, fc.confidence, calibration));
    }

    protected List<Candidate> exitCandidates(KspGEFlipperBackendDtos.AccountState account, PortfolioView portfolio) {
        List<Candidate> out = new ArrayList<>();
        for (Position p : portfolio.positions) {
            int remaining = p.remaining();
            if (remaining <= 0) continue;
            long available = owned(account, p.itemId);
            int qty = (int) Math.min(remaining, available);
            if (qty <= 0) continue;
            MarketSnapshot s = market.get(p.itemId);
            if (s == null || s.latestHigh <= 0) continue;
            List<MarketPoint> history = store.marketHistory(p.itemId, 256);
            Features f = features(s, history);
            Forecast fc = forecast(s, f, history, account.strategy.timeframeMinutes);
            long unitCost = p.averageBuyPrice();
            long breakEven = minimumBreakEvenSell(s.item.name, unitCost);
            long forecastTarget = fc.high.isEmpty() ? s.latestHigh : Math.round(point(fc.high, Math.min(1, fc.high.size() - 1)).mean);
            long sell = Math.max(breakEven, Math.min(s.latestHigh, forecastTarget));
            long unitNet = postTaxUnit(s.item.name, sell) - unitCost;
            long profit = safeMultiply(unitNet, qty);
            Estimate estimate = estimate(s, f, "SELL", sell, qty, fc.confidence, calibrationFor("POSITION_EXIT", p.itemId));
            double gph = profit * 60.0 / Math.max(1, estimate.minutes) * estimate.fill;
            double utility = gph * fc.confidence * estimate.fill;
            boolean shouldExit = account.strategy.sellOnly || profit > 0 || "UNMATCHED".equals(p.type);
            if (shouldExit) out.add(candidate(s, "POSITION_EXIT", 0, sell, qty, profit, profit, estimate.minutes, gph, fc.robustSigmaPct,
                    fc.confidence, estimate.fill, f, utility, false, "position exit: remaining=" + remaining + ", unitCost=" + unitCost + ", target=" + sell));
        }
        out.sort((x, y) -> Double.compare(y.utility, x.utility));
        return out;
    }

    protected KspGEFlipperBackendDtos.Suggestion decide(KspGEFlipperBackendDtos.AccountState a, List<Candidate> entries, List<Candidate> exits) {
        List<KspGEFlipperBackendDtos.Offer> active = new ArrayList<>();
        for (KspGEFlipperBackendDtos.Offer o : a.offers) if (o.active) active.add(o);
        Candidate bestEntry = select(entries, a);
        if (!active.isEmpty()) {
            KspGEFlipperBackendDtos.Suggestion action = evaluateOffers(a, active, entries, exits, bestEntry);
            if (action != null) return action;
        }
        int freeSlot = freeSlot(a);
        if (freeSlot >= 0) {
            Candidate bestExit = null;
            for (Candidate c : exits) if (!hasActiveSell(a, c.itemId) && (bestExit == null || c.utility > bestExit.utility)) bestExit = c;
            if (bestExit != null) return suggestion(bestExit, "SELL", freeSlot, bestExit.sell, "Exit owned position using tax-safe forecast target.");
        }
        if (a.strategy.sellOnly) return waitSuggestion("Sell-only mode: no owned position currently needs a new or modified exit.");
        if (freeSlot < 0) return waitSuggestion("No usable GE slot. Existing offers remain inside modify/abort hysteresis thresholds.");
        return bestEntry == null ? waitSuggestion("No candidate passed membership, freshness, liquidity, profit, risk, capital and slot gates.")
                : suggestion(bestEntry, "BUY", freeSlot, bestEntry.buy, bestEntry.explanation);
    }

    protected KspGEFlipperBackendDtos.Suggestion evaluateOffers(KspGEFlipperBackendDtos.AccountState a,
                                                                List<KspGEFlipperBackendDtos.Offer> offers,
                                                                List<Candidate> entries, List<Candidate> exits, Candidate bestAlternative) {
        double modify = Math.max(0, a.strategy.modifyThresholdPct) / 100.0;
        double abort = Math.max(0, a.strategy.abortThresholdPct) / 100.0;
        for (KspGEFlipperBackendDtos.Offer offer : offers) {
            if (offer.suggestionId == null) continue; // manual offer protection
            Candidate same = bestSame(offer, entries, exits);
            double currentUtility = approxCurrentUtility(offer, same);
            if ("BUY".equalsIgnoreCase(offer.side) && same != null && same.buy > 0 && same.buy != offer.offerPrice) {
                double improvement = relativeImprovement(currentUtility, same.utility);
                if (improvement > modify) {
                    store.markRecommendation(offer.suggestionId, "MODIFIED");
                    return suggestion(same, "MODIFY_BUY", offer.slot, same.buy,
                            "Modify buy: refreshed slot utility improved by " + Math.round(improvement * 100) + "%.");
                }
            }
            if ("SELL".equalsIgnoreCase(offer.side) && same != null && same.sell > 0 && same.sell != offer.offerPrice) {
                double improvement = relativeImprovement(currentUtility, same.utility);
                double priceDelta = Math.abs(same.sell - offer.offerPrice) / (double) Math.max(1, offer.offerPrice);
                if (improvement > modify || priceDelta > Math.max(modify, config.sellRepricePercent() / 100.0)) {
                    store.markRecommendation(offer.suggestionId, "MODIFIED");
                    return suggestion(same, "MODIFY_SELL", offer.slot, same.sell, "Modify sell: updated tax-safe exit target materially improves completion value.");
                }
            }
            if ("BUY".equalsIgnoreCase(offer.side) && bestAlternative != null && bestAlternative.itemId != offer.itemId
                    && bestAlternative.utility > currentUtility * (1.0 + abort)) {
                store.markRecommendation(offer.suggestionId, "ABORTED");
                KspGEFlipperBackendDtos.Suggestion s = suggestion(bestAlternative, "ABORT", offer.slot, 0,
                        "Abort: replacement candidate exceeds current slot utility by configured switching threshold.");
                s.itemId = offer.itemId;
                s.name = same == null ? "active offer" : same.name;
                s.quantity = Math.max(0, offer.totalQuantity - offer.filledQuantity);
                s.exitPrice = 0;
                return s;
            }
        }
        return waitSuggestion("Existing offers are manual/protected or remain inside hysteresis thresholds; WAIT avoids repricing churn.");
    }

    protected Candidate select(List<Candidate> entries, KspGEFlipperBackendDtos.AccountState a) {
        SlotBudget slots = slotBudget(a);
        Candidate best = null;
        double bestUtility = -Double.MAX_VALUE;
        for (Candidate c : entries) {
            if (c.buy <= 0 || c.quantity <= 0) continue;
            if (c.buy * (double) c.quantity > Math.max(0, a.gp - config.reserveCoins())) continue;
            if (!slotAllowed(c, slots)) continue;
            double max = Math.max(0.01, Math.min(1, a.strategy.maxItemExposurePct / 100.0));
            if (c.buy * (double) c.quantity > Math.max(1, a.gp) * max) continue;
            boolean sameActive = false;
            for (KspGEFlipperBackendDtos.Offer o : a.offers) if (o.active && o.itemId == c.itemId) sameActive = true;
            double diversification = sameActive ? 0.82 : 1.0;
            double scarcity = slots.usable <= 0 ? 1.0 : slots.active / (double) slots.usable;
            double adjusted = c.utility * diversification * (1 - 0.12 * Math.min(1, scarcity));
            if (adjusted > bestUtility) { bestUtility = adjusted; best = c; }
        }
        return best;
    }

    protected boolean slotAllowed(Candidate c, SlotBudget b) {
        if (b.free <= 0) return false;
        if ("DUMP".equals(c.type)) return b.dumpReserved > 0 && b.dumpActive < b.dumpReserved;
        int normalCapacity = Math.max(0, b.usable - b.dumpReserved);
        return b.normalActive < normalCapacity;
    }

    protected SlotBudget slotBudget(KspGEFlipperBackendDtos.AccountState a) {
        SlotBudget b = new SlotBudget();
        int detected = Math.max(1, a.totalGeSlots), configured = Math.max(1, a.maxPluginSlots);
        b.total = Math.min(detected, configured);
        int reserved = Math.max(0, Math.min(b.total, a.strategy.reservedSlots));
        b.usable = Math.max(0, b.total - reserved);
        for (KspGEFlipperBackendDtos.Offer o : a.offers) if (o.active) {
            b.active++;
            if ("DUMP".equalsIgnoreCase(o.candidateType)) b.dumpActive++; else b.normalActive++;
        }
        b.free = Math.max(0, b.usable - b.active);
        b.dumpReserved = a.strategy.dumpEnabled && a.strategy.timeframeMinutes <= 30 ? Math.max(0, Math.min(b.usable, a.strategy.dumpSlots)) : 0;
        return b;
    }

    // ---------------- portfolio / outcomes / calibration ----------------

    protected Candidate bestSame(KspGEFlipperBackendDtos.Offer offer, List<Candidate> entries, List<Candidate> exits) {
        Candidate best = null;
        for (Candidate c : entries) if (c.itemId == offer.itemId && "BUY".equalsIgnoreCase(offer.side) && c.buy > 0 && (best == null || c.utility > best.utility)) best = c;
        for (Candidate c : exits) if (c.itemId == offer.itemId && "SELL".equalsIgnoreCase(offer.side) && c.sell > 0 && (best == null || c.utility > best.utility)) best = c;
        return best;
    }

    protected double approxCurrentUtility(KspGEFlipperBackendDtos.Offer offer, Candidate same) {
        if (same == null) return 1.0;
        long target = "BUY".equalsIgnoreCase(offer.side) ? same.buy : same.sell;
        double distance = Math.abs(target - offer.offerPrice) / (double) Math.max(1, target);
        double fillPenalty = 1 - Math.min(0.85, distance * 3);
        double partialCredit = 0.85 + 0.15 * Math.min(1, offer.filledQuantity / (double) Math.max(1, offer.totalQuantity));
        return same.utility * Math.max(0.1, fillPenalty) * partialCredit;
    }

    protected int freeSlot(KspGEFlipperBackendDtos.AccountState a) {
        SlotBudget b = slotBudget(a);
        if (b.free <= 0) return -1;
        Set<Integer> used = new HashSet<>();
        for (KspGEFlipperBackendDtos.Offer o : a.offers) if (o.active) used.add(o.slot);
        for (int i = 0; i < b.usable; i++) if (!used.contains(i)) return i;
        return -1;
    }

    protected boolean hasActiveSell(KspGEFlipperBackendDtos.AccountState a, int itemId) {
        for (KspGEFlipperBackendDtos.Offer o : a.offers) if (o.active && o.itemId == itemId && "SELL".equalsIgnoreCase(o.side)) return true;
        return false;
    }

    protected long owned(KspGEFlipperBackendDtos.AccountState a, int itemId) {
        return a.inventory.getOrDefault(itemId, 0L) + a.bank.getOrDefault(itemId, 0L) + a.uncollected.getOrDefault(itemId, 0L) + a.otherStorage.getOrDefault(itemId, 0L);
    }

    protected KspGEFlipperBackendDtos.Suggestion suggestion(Candidate c, String type, int slot, long price, String explanation) {
        KspGEFlipperBackendDtos.Suggestion s = new KspGEFlipperBackendDtos.Suggestion();
        s.id = UUID.randomUUID().toString();
        s.type = type;
        s.candidateType = c == null ? null : c.type;
        s.slot = slot;
        s.itemId = c == null ? -1 : c.itemId;
        s.name = c == null ? "-" : c.name;
        s.price = price;
        s.exitPrice = c == null ? 0 : c.sell;
        s.quantity = c == null ? 0 : c.quantity;
        s.expectedProfit = c == null ? 0 : c.expectedNetProfit;
        s.expectedDurationSeconds = c == null ? 0 : Math.max(0, Math.round(c.expectedMinutes * 60));
        s.expectedGpPerHour = c == null ? 0 : c.gpPerHour;
        s.confidence = c == null ? 0 : c.confidence;
        s.hold = c != null && c.hold;
        s.explanation = explanation;
        s.generatedAt = Instant.now().toString();
        MarketSnapshot snapshot = c == null ? null : market.get(c.itemId);
        s.marketAgeSeconds = snapshot == null ? 0 : Math.max(0, (System.currentTimeMillis() - Math.min(snapshot.latestHighTime, snapshot.latestLowTime)) / 1000L);
        return s;
    }

    protected KspGEFlipperBackendDtos.Suggestion waitSuggestion(String explanation) {
        Candidate empty = new Candidate();
        empty.type = "WAIT";
        empty.itemId = -1;
        empty.name = "-";
        return suggestion(empty, "WAIT", -1, 0, explanation);
    }

    protected Candidate candidate(MarketSnapshot s, String type, long buy, long sell, int qty, long gross, long net,
                                double mins, double gph, double risk, double confidence, double fill, Features f,
                                double utility, boolean hold, String explanation) {
        Candidate c = new Candidate();
        c.itemId = s.item.id; c.name = s.item.name; c.type = type; c.buy = buy; c.sell = sell; c.quantity = qty;
        c.expectedGrossProfit = gross; c.expectedNetProfit = net; c.expectedMinutes = mins; c.gpPerHour = gph;
        c.riskScore = risk; c.confidence = confidence; c.fillProbability = fill; c.liquidityScore = f.liquidityScore;
        c.volatilityScore = f.volatility1h; c.utility = utility; c.hold = hold; c.explanation = explanation;
        return c;
    }


}
