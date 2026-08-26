package com.ksp.geflipper.optimizer;

import com.ksp.geflipper.model.Models.*;
import java.util.*;

/** Capital + GE-slot allocator. A slot is treated as scarce capital, not as a free side effect. */
public final class PortfolioOptimizer {
    public Optional<FlipCandidate> select(List<FlipCandidate> candidates, AccountState account) {
        SlotBudget slots = slotBudget(account);
        if (slots.free() <= 0) return Optional.empty();

        long committed = account.offers().stream()
                .filter(GeOfferState::active)
                .filter(o -> o.side() == Side.BUY)
                .mapToLong(o -> multiply(o.offerPrice(), Math.max(0, o.totalQuantity() - o.filledQuantity())))
                .sum();
        long freeCapital = Math.max(0, account.gp() - committed);

        return candidates.stream()
                .filter(c -> slotAllowed(c, account, slots))
                .filter(c -> c.proposedBuy() <= 0 || multiply(c.proposedBuy(), c.quantity()) <= freeCapital)
                .filter(c -> withinExposure(c, account))
                .max(Comparator.comparingDouble(c -> adjustedUtility(c, account, slots)));
    }

    public double slotValue(FlipCandidate candidate) { return candidate.utility(); }

    private boolean slotAllowed(FlipCandidate c, AccountState a, SlotBudget slots) {
        if (c.type() == CandidateType.DUMP) {
            return a.strategy().dumpEnabled() && slots.dumpActive() < slots.dumpReserved() && slots.free() > 0;
        }
        if (slots.dumpReserved() <= 0) return slots.free() > 0;
        int normalCapacity = Math.max(0, slots.usable() - slots.dumpReserved());
        return slots.normalActive() < normalCapacity;
    }

    private boolean withinExposure(FlipCandidate c, AccountState a) {
        if (c.proposedBuy() <= 0) return true;
        double max = Math.max(0.01, Math.min(1.0, a.strategy().maxItemExposurePct() / 100.0));
        return multiply(c.proposedBuy(), c.quantity()) <= Math.max(1, a.gp()) * max;
    }

    private double adjustedUtility(FlipCandidate c, AccountState a, SlotBudget slots) {
        double scarcity = slots.usable() <= 0 ? 1.0 : slots.active() / (double) slots.usable();
        boolean sameItemActive = a.offers().stream().anyMatch(o -> o.active() && o.itemId() == c.itemId());
        double diversification = sameItemActive ? 0.82 : 1.0;
        double capital = c.proposedBuy() <= 0 ? 0 : multiply(c.proposedBuy(), c.quantity());
        double capitalOpportunity = capital / Math.max(1.0, a.gp()) * Math.max(0.05, c.expectedMinutes() / 60.0) * 0.08;
        return c.utility() * diversification * (1.0 - 0.12 * Math.min(1.0, scarcity)) - Math.abs(c.utility()) * capitalOpportunity;
    }

    public SlotBudget slotBudget(AccountState a) {
        int detected = Math.max(1, a.totalGeSlots());
        int configured = Math.max(1, a.maxPluginSlots());
        int total = Math.min(detected, configured);
        int reserved = Math.max(0, Math.min(total, a.strategy().reservedSlots()));
        int usable = Math.max(0, total - reserved);
        int active = (int) a.offers().stream().filter(GeOfferState::active).count();
        int dumpActive = (int) a.offers().stream().filter(GeOfferState::active)
                .filter(o -> o.candidateType() == CandidateType.DUMP)
                .count();
        int dumpReserved = a.strategy().dumpEnabled() && a.strategy().timeframeMinutes() <= 30
                ? Math.max(0, Math.min(usable, a.strategy().dumpSlots())) : 0;
        int normalActive = active - dumpActive;
        return new SlotBudget(total, usable, active, Math.max(0, usable - active), dumpReserved, dumpActive, normalActive);
    }

    private static long multiply(long price, int quantity) {
        if (price <= 0 || quantity <= 0) return 0;
        return price > Long.MAX_VALUE / quantity ? Long.MAX_VALUE : price * quantity;
    }

    public record SlotBudget(int total, int usable, int active, int free, int dumpReserved, int dumpActive, int normalActive) {}
}
