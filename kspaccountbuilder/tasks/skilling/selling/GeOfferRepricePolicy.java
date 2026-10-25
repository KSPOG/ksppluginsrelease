package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling;

import java.util.HashMap;
import java.util.Map;

/**
 * Monitors only GE offers placed during this Account Builder task session.
 * Supports in-place price modifications without resetting quantity or partial
 * fills. A manually repriced offer is no longer managed by this policy.
 */
public final class GeOfferRepricePolicy
{
    public static final long STALL_MS = 90_000L;
    public static final int MAX_REPRICES = 5;
    private static final int PRICE_STEP_PERCENT = 5;
    private final Map<Integer, Offer> offers = new HashMap<>();

    public void reset() { offers.clear(); }

    public void placed(int itemId, int price, long now)
    {
        if (itemId <= 0 || price <= 0) return;
        // Placement is a new order. Its expiry and caps must not inherit from
        // the previous offer for the same item.
        offers.put(itemId, new Offer(price, now));
    }

    /**
     * @return 0 when no change should occur; else the requested new GE price.
     */
    public int candidate(int itemId, int observedPrice, int filled, long now, boolean buying)
    {
        Offer offer = offers.get(itemId);
        if (offer == null || offer.reprices >= MAX_REPRICES || observedPrice <= 0) return 0;

        if (observedPrice != offer.currentPrice)
        {
            // User or another script changed the order. Do not override them.
            offers.remove(itemId);
            return 0;
        }
        if (filled > offer.lastFilled)
        {
            offer.lastFilled = filled;
            offer.lastProgressAt = now;
            return 0;
        }
        if (now - offer.lastProgressAt < STALL_MS) return 0;
        return nextPrice(offer.initialPrice, observedPrice, offer.reprices, buying);
    }

    /** Only call when the live GE slot confirms the new price. */
    public void modified(int itemId, int newPrice, long now)
    {
        Offer offer = offers.get(itemId);
        if (offer == null || newPrice <= 0) return;
        offer.currentPrice = newPrice;
        offer.lastProgressAt = now;
        offer.reprices++;
    }

    /** Failed GUI interactions must not repeat every script tick. */
    public void defer(int itemId, long now)
    {
        Offer offer = offers.get(itemId);
        if (offer != null) offer.lastProgressAt = now;
    }

    public void forget(int itemId) { offers.remove(itemId); }

    public int retries(int itemId)
    {
        Offer offer = offers.get(itemId);
        return offer == null ? 0 : offer.reprices;
    }

    /**
     * Five 5% steps maximum, clamped to 65% of the original sell price
     * or 150% of the initial buy price. Price is always at least 1 GP.
     */
    public static int nextPrice(int initial, int current, int reprices, boolean buying)
    {
        if (initial <= 0 || current <= 0 || reprices >= MAX_REPRICES) return 0;
        long delta = Math.max(1L, ((long) current * PRICE_STEP_PERCENT + 99L) / 100L);
        if (buying)
        {
            long ceiling = Math.min(Integer.MAX_VALUE, ((long) initial * 150L + 99L) / 100L);
            long updated = Math.min(ceiling, (long) current + delta);
            return updated > current ? (int) updated : 0;
        }
        long floor = Math.max(1L, ((long) initial * 65L) / 100L);
        long updated = Math.max(floor, (long) current - delta);
        return updated < current ? (int) updated : 0;
    }

    private static final class Offer
    {
        final int initialPrice;
        int currentPrice;
        int lastFilled;
        int reprices;
        long lastProgressAt;

        Offer(int initialPrice, long now)
        {
            this.initialPrice = initialPrice;
            this.currentPrice = initialPrice;
            this.lastProgressAt = now;
        }
    }
}
