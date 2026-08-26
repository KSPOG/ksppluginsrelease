package com.ksp.geflipper.repricing;

import com.ksp.geflipper.model.Models.*;
import com.ksp.geflipper.optimizer.PortfolioOptimizer;
import java.time.Instant;
import java.util.*;

/** BUY/SELL/MODIFY/ABORT/WAIT policy with hysteresis and manual-offer protection. */
public final class ActionPolicy {
    private final PortfolioOptimizer optimizer;
    public ActionPolicy(PortfolioOptimizer optimizer) { this.optimizer = optimizer; }

    public TradeSuggestion decide(AccountState account, List<FlipCandidate> entryCandidates, List<FlipCandidate> exits) {
        List<GeOfferState> active = account.offers().stream().filter(GeOfferState::active).toList();
        List<FlipCandidate> all = new ArrayList<>(entryCandidates.size() + exits.size());
        all.addAll(entryCandidates);
        all.addAll(exits);
        Optional<FlipCandidate> bestEntry = optimizer.select(entryCandidates, account);

        if (!active.isEmpty()) {
            TradeSuggestion action = evaluateOffers(account, active, all, bestEntry.orElse(null));
            if (action != null) return action;
        }

        int slot = freeSlot(account);
        if (slot >= 0) {
            Optional<FlipCandidate> exit = exits.stream()
                    .filter(c -> !hasActiveSell(account, c.itemId()))
                    .max(Comparator.comparingDouble(FlipCandidate::utility));
            if (exit.isPresent()) return fromCandidate(exit.get(), SuggestionType.SELL, slot, 0);
        }

        if (account.strategy().sellOnly()) {
            return TradeSuggestion.waitSuggestion("Sell-only mode: no owned position currently needs a new or modified exit.");
        }
        if (slot < 0) {
            return TradeSuggestion.waitSuggestion("No usable GE slot. Existing plugin offers did not cross modify/abort hysteresis thresholds.");
        }
        return bestEntry.map(c -> fromCandidate(c, SuggestionType.BUY, slot, 0))
                .orElseGet(() -> TradeSuggestion.waitSuggestion("No candidate passed membership, freshness, liquidity, profit, risk, capital and slot gates."));
    }

    private TradeSuggestion evaluateOffers(AccountState account, List<GeOfferState> offers,
                                            List<FlipCandidate> candidates, FlipCandidate bestAlternative) {
        double modify = Math.max(0, account.strategy().modifyThresholdPct()) / 100.0;
        double abort = Math.max(0, account.strategy().abortThresholdPct()) / 100.0;
        boolean sawProtectedOrStable = false;

        for (GeOfferState offer : offers) {
            // Manual offers still consume slots and capital, but automatic policy will never cancel them.
            if (offer.suggestionId() == null) { sawProtectedOrStable = true; continue; }
            FlipCandidate same = candidates.stream()
                    .filter(c -> c.itemId() == offer.itemId())
                    .filter(c -> offer.side() == Side.BUY ? c.proposedBuy() > 0 : c.proposedSell() > 0)
                    .max(Comparator.comparingDouble(FlipCandidate::utility)).orElse(null);
            double currentUtility = approxCurrentUtility(offer, same);

            if (offer.side() == Side.BUY && same != null && same.proposedBuy() != offer.offerPrice()) {
                double improvement = relativeImprovement(currentUtility, same.utility());
                if (improvement > modify) {
                    return suggestion(SuggestionType.MODIFY_BUY, offer, same, same.proposedBuy(),
                            "Modify buy: refreshed expected slot utility improved by " + Math.round(improvement * 100) + "%." );
                }
            }

            if (offer.side() == Side.SELL && same != null && same.proposedSell() != offer.offerPrice()) {
                double newUtility = same.utility();
                double improvement = relativeImprovement(currentUtility, newUtility);
                double priceDelta = Math.abs(same.proposedSell() - offer.offerPrice()) / (double) Math.max(1, offer.offerPrice());
                if (improvement > modify || priceDelta > modify) {
                    return suggestion(SuggestionType.MODIFY_SELL, offer, same, same.proposedSell(),
                            "Modify sell: updated tax-safe exit target materially improved expected completion value.");
                }
            }

            if (offer.side() == Side.BUY && bestAlternative != null && bestAlternative.itemId() != offer.itemId()
                    && bestAlternative.utility() > currentUtility * (1.0 + abort)) {
                return new TradeSuggestion(UUID.randomUUID(), SuggestionType.ABORT, offer.candidateType(), offer.slot(), offer.itemId(),
                        same == null ? "active offer" : same.name(), 0, 0,
                        Math.max(0, offer.totalQuantity() - offer.filledQuantity()), 0, 0,
                        bestAlternative.gpPerHour(), bestAlternative.confidence(), false,
                        "Abort: a replacement candidate exceeds current slot utility by the configured switching threshold.",
                        Instant.now(), 0);
            }
            sawProtectedOrStable = true;
        }
        return sawProtectedOrStable
                ? TradeSuggestion.waitSuggestion("Existing offers are manual/protected or remain inside hysteresis thresholds; WAIT avoids repricing churn.")
                : null;
    }

    private static TradeSuggestion suggestion(SuggestionType type, GeOfferState offer, FlipCandidate c, long price, String explanation) {
        return new TradeSuggestion(UUID.randomUUID(), type, c.type(), offer.slot(), offer.itemId(), c.name(), price,
                c.proposedSell(), Math.max(1, offer.totalQuantity() - offer.filledQuantity()), c.expectedNetProfit(),
                Math.round(c.expectedMinutes() * 60), c.gpPerHour(), c.confidence(), c.hold(), explanation, Instant.now(), 0);
    }

    private static double relativeImprovement(double current, double replacement) {
        return (replacement - current) / Math.max(1.0, Math.abs(current));
    }

    private static double approxCurrentUtility(GeOfferState offer, FlipCandidate same) {
        if (same == null) return 1.0;
        long target = offer.side() == Side.BUY ? same.proposedBuy() : same.proposedSell();
        double distance = Math.abs(target - offer.offerPrice()) / (double) Math.max(1, target);
        double fillPenalty = 1.0 - Math.min(0.85, distance * 3.0);
        double partialCredit = 0.85 + 0.15 * Math.min(1.0, offer.filledQuantity() / (double) Math.max(1, offer.totalQuantity()));
        return same.utility() * Math.max(0.1, fillPenalty) * partialCredit;
    }

    private TradeSuggestion fromCandidate(FlipCandidate c, SuggestionType type, int slot, long age) {
        long price = type == SuggestionType.SELL ? c.proposedSell() : c.proposedBuy();
        return new TradeSuggestion(UUID.randomUUID(), type, c.type(), slot, c.itemId(), c.name(), price, c.proposedSell(),
                c.quantity(), c.expectedNetProfit(), Math.round(c.expectedMinutes() * 60), c.gpPerHour(),
                c.confidence(), c.hold(), c.explanation(), Instant.now(), age);
    }

    private int freeSlot(AccountState account) {
        PortfolioOptimizer.SlotBudget budget = optimizer.slotBudget(account);
        if (budget.free() <= 0) return -1;
        Set<Integer> used = new HashSet<>();
        account.offers().stream().filter(GeOfferState::active).forEach(o -> used.add(o.slot()));
        for (int i = 0; i < budget.usable(); i++) if (!used.contains(i)) return i;
        return -1;
    }

    private static boolean hasActiveSell(AccountState account, int itemId) {
        return account.offers().stream().anyMatch(o -> o.active() && o.side() == Side.SELL && o.itemId() == itemId);
    }
}
