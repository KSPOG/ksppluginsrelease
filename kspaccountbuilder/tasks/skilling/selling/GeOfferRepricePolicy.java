package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling;

import java.util.HashMap;
import java.util.Map;

/**
 * Session-owned GE offer monitoring. Only explicitly registered offers may be
 * repriced; pre-existing player offers are never touched.
 *
 * The in-game Modify Offer interface updates the price without changing
 * the original order or its partially filled quantity.
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
        Offer offer = offers.get(itemId);
        if (offer == null)
        {
            offer = new Offer(price);
            offers.put(itemId, offer);
        }
        else if (offer.queuedPrice > 0 && offer.queuedPrice != price)
        {
            // Something else placed a different offer at this slot or price.
            // Do not keep adjusting a transaction we no longer recognize.
            offers.remove(itemId);
            return;
        }
        offer.currentPrice = price;
        offer.lastFilled = 0;
        offer.lastProgressAt = now;
        offer.cancelPending = false;
        offer.queuedPrice = 0;
    }

    public int candidate(int itemId, int observedPrice, int filled, long now, boolean buying)
    {
        Offer offer = offers.get(itemId);
        if (offer == null || offer.cancelPending || offer.queuedPrice > 0
                || offer.reprices >= MAX_REPRICES || observedPrice <= 0)
            return 0;

        if (observedPrice != offer.currentPrice)
        {
            // Respect manual pricing adjustments.
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

    /**
     * Called only once the live GE slot confirms the modified unit price.
     * Existing filled quantity is left intact; only the quote/timer changes.
     */
    public void modified(int itemId, int newPrice, long now)
    {
        Offer offer = offers.get(itemId);
        if (offer == null || newPrice <= 0) return;
        offer.currentPrice = newPrice;
        offer.lastProgressAt = now;
        offer.reprices++;
    }

    /** An unsuccessful UI update must not retry every 50-100ms tick. */
    public void defer(int itemId, long now)
    {
        Offer offer = offers.get(itemId);
        if (offer != null) offer.lastProgressAt = now;
    }

    public void requestCancellation(int itemId, int intendedPrice)
    {
        Offer offer = offers.get(itemId);
        if (offer != null && intendedPrice > 0)
        {
            offer.cancelPending = true;
            offer.queuedPrice = intendedPrice;
        }
    }

    public void cancellationConfirmed(int itemId, long now)
    {
        Offer offer = offers.get(itemId);
        if (offer == null || !offer.cancelPending) return;
        offer.cancelPending = false;
        offer.reprices++;
        offer.lastProgressAt = now;
    }

    public int priceForNewOffer(int itemId, int fallback)
    {
        Offer offer = offers.get(itemId);
        return offer == null || offer.queuedPrice <= 0 ? fallback : offer.queuedPrice;
    }

    public boolean awaitingCancellation(int itemId)
    {
        Offer offer = offers.get(itemId);
        return offer != null && offer.cancelPending;
    }

    public void forget(int itemId) { offers.remove(itemId); }

    public int retries(int itemId)
    {
        Offer offer = offers.get(itemId);
        return offer == null ? 0 : offer.reprices;
    }

    public static int nextPrice(int initial, int current, int repriceCount, boolean buying)
    {
        if (initial <= 0 || current <= 0 || repriceCount >= MAX_REPRICES) return 0;
        // Round upward for buying, downward for selling. Always move at least
        // one GP while respecting the signed 32-bit GE per-unit price limit.
        long delta = Math.max(1L, ((long) current * PRICE_STEP_PERCENT + 99L) / 100L);
        if (buying)
        {
            long ceiling = Math.min(Integer.MAX_VALUE, ((long) initial * 150L + 99L) / 100L);
            long newPrice = Math.min(ceiling, (long) current + delta);
            return newPrice > current ? (int) newPrice : 0;
        }
        long floor = Math.max(1L, ((long) initial * 65L) / 100L);
        long newPrice = Math.max(floor, (long) current - delta);
        return newPrice < current ? (int) newPrice : 0;
    }

    private static final class Offer
    {
        final int initialPrice;
        int currentPrice;
        int queuedPrice;
        int lastFilled;
        int reprices;
        long lastProgressAt;
        boolean cancelPending;

        Offer(int initialPrice)
        {
            this.initialPrice = initialPrice;
        }
    }
}
