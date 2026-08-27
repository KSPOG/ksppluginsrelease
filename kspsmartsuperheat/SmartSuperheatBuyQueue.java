package net.runelite.client.plugins.microbot.kspsmartsuperheat;

import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeAction;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeRequest;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeSlots;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.world.Rs2WorldUtil;

import java.util.ArrayList;
import java.util.List;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Stateful Smart Superheat GE BUY queue.
 *
 * <p>This intentionally follows KSP Jewellery Crafter's GE model: orders persist across script
 * ticks, are assigned to explicit free slots, are placed through GrandExchangeRequest, and are
 * reconciled against the client's live GrandExchangeOffer array before any retry. A failed item
 * search therefore returns control to the script; the next tick restores the GE overview and
 * retries the same queued order instead of continuing through a half-open setup screen.</p>
 */
final class SmartSuperheatBuyQueue
{
    private static final int UI_TIMEOUT_MS = 5_000;
    private static final int OFFER_CONFIRM_TIMEOUT_MS = 3_000;
    private static final int MAX_PLACEMENT_FAILURES = 5;
    private static final int MAX_TIMEOUT_RETRIES = 2;

    private static final List<BuyOrder> orders = new ArrayList<>();

    private SmartSuperheatBuyQueue()
    {
    }

    static synchronized void reset()
    {
        orders.clear();
    }

    static synchronized boolean hasActive()
    {
        return !orders.isEmpty() && orders.stream().anyMatch(o -> !o.completed && !o.failed);
    }

    static synchronized boolean start(List<OrderSpec> specs)
    {
        if (hasActive())
        {
            return false;
        }

        orders.clear();
        if (specs == null)
        {
            return false;
        }

        for (OrderSpec spec : specs)
        {
            if (spec == null || spec.itemId <= 0 || spec.itemName == null || spec.itemName.isBlank()
                || spec.quantity <= 0 || spec.price <= 0)
            {
                continue;
            }
            orders.add(new BuyOrder(spec));
        }
        return !orders.isEmpty();
    }

    static synchronized QueueResult tick(int timeoutSeconds)
    {
        if (orders.isEmpty())
        {
            return QueueResult.completed("GE restock queue complete");
        }

        reconcileAssignments();

        if (!ensureGeOverview())
        {
            return QueueResult.active("Recovering Grand Exchange overview");
        }

        QueueResult monitored = monitorOrders(Math.max(1, timeoutSeconds) * 1_000L);
        if (monitored != null)
        {
            return monitored;
        }

        if (allDone())
        {
            if (hasFailed())
            {
                return QueueResult.failed("One or more GE restock orders failed");
            }
            return QueueResult.completed("GE restock offers collected");
        }

        QueueResult placement = placeOrders();
        if (placement != null)
        {
            return placement;
        }

        if (allDone())
        {
            if (hasFailed())
            {
                return QueueResult.failed("One or more GE restock orders failed");
            }
            return QueueResult.completed("GE restock offers collected");
        }

        int active = activeOrders();
        int queued = queuedOrders();
        return QueueResult.active(active > 0
            ? "Buying inputs: " + active + " active" + (queued > 0 ? ", " + queued + " queued" : "")
            : "Waiting for free GE slot");
    }

    private static QueueResult monitorOrders(long timeoutMs)
    {
        boolean completedOffer = false;

        for (BuyOrder order : orders)
        {
            if (order.completed || order.failed || order.slot == null)
            {
                continue;
            }

            OfferSnapshot offer = getOfferSnapshot(order.slot);
            if (offer == null)
            {
                continue;
            }

            if (offer.itemId != order.itemId)
            {
                order.slot = null;
                order.placedAt = 0L;
                continue;
            }

            if (offer.state == GrandExchangeOfferState.BOUGHT)
            {
                order.remaining = 0;
                order.completed = true;
                completedOffer = true;
            }
            else if (offer.state == GrandExchangeOfferState.EMPTY)
            {
                order.slot = null;
                order.placedAt = 0L;
            }
        }

        if (completedOffer || hasCompletedGeOffers())
        {
            if (!collectAllCompletedOffers())
            {
                return QueueResult.active("Collecting completed GE offers");
            }

            for (BuyOrder order : orders)
            {
                if (order.completed)
                {
                    order.slot = null;
                    order.placedAt = 0L;
                }
            }
            return QueueResult.active("Collected completed GE offers");
        }

        long now = System.currentTimeMillis();
        for (BuyOrder order : orders)
        {
            if (order.completed || order.failed || order.slot == null || order.placedAt <= 0L
                || now - order.placedAt < timeoutMs)
            {
                continue;
            }
            return handleTimedOutOrder(order);
        }

        return null;
    }

    private static QueueResult handleTimedOutOrder(BuyOrder order)
    {
        OfferSnapshot snapshot = getOfferSnapshot(order.slot);
        int filled = snapshot != null && snapshot.itemId == order.itemId
            ? Math.max(0, snapshot.quantitySold)
            : 0;

        if (filled > 0)
        {
            order.remaining = Math.max(0, order.remaining - filled);
        }

        if (order.slot != null)
        {
            Rs2GrandExchange.cancelSpecificOffers(List.of(order.slot), true);
            sleepUntil(() -> isSlotEmpty(order.slot), UI_TIMEOUT_MS);
        }

        order.slot = null;
        order.placedAt = 0L;
        order.timeoutRetries++;

        if (order.remaining <= 0)
        {
            order.completed = true;
            return QueueResult.active("Collected partial fill for " + order.itemName);
        }

        if (order.timeoutRetries > MAX_TIMEOUT_RETRIES)
        {
            order.failed = true;
            return QueueResult.failed("GE buy timed out repeatedly: " + order.itemName);
        }

        return QueueResult.active("Re-queueing " + order.remaining + " x " + order.itemName);
    }

    private static QueueResult placeOrders()
    {
        reconcileAssignments();
        List<GrandExchangeSlots> freeSlots = getAvailableGeSlots();
        if (freeSlots.isEmpty())
        {
            return null;
        }

        int slotIndex = 0;
        for (BuyOrder order : orders)
        {
            if (slotIndex >= freeSlots.size())
            {
                break;
            }
            if (order.completed || order.failed || order.slot != null || order.remaining <= 0)
            {
                continue;
            }

            GrandExchangeSlots slot = freeSlots.get(slotIndex++);
            order.slot = slot;
            order.placedAt = System.currentTimeMillis();

            GrandExchangeRequest request = GrandExchangeRequest.builder()
                .slot(slot)
                .action(GrandExchangeAction.BUY)
                .itemName(order.itemName)
                .exact(true)
                .quantity(order.remaining)
                .price(order.price)
                .closeAfterCompletion(false)
                .build();

            Microbot.status = "Buying " + order.remaining + " x " + order.itemName
                + " in GE slot " + (slot.ordinal() + 1);

            if (!Rs2GrandExchange.processOffer(request))
            {
                GrandExchangeSlots recovered = findOfferSlot(order.itemId);
                if (recovered != null)
                {
                    order.slot = recovered;
                    order.placedAt = System.currentTimeMillis();
                    return QueueResult.active("Recovered live GE buy: " + order.itemName);
                }

                order.placementFailures++;
                order.slot = null;
                order.placedAt = 0L;

                if (order.placementFailures >= MAX_PLACEMENT_FAILURES)
                {
                    order.failed = true;
                    return QueueResult.failed("GE item selection repeatedly failed: " + order.itemName);
                }

                // Exactly like Jewellery Crafter: stop this tick. The next tick runs
                // ensureGeOverview() first, backing out of a half-open item-search/setup screen
                // before retrying the same queued order.
                return QueueResult.active("GE buy placement failed - retrying " + order.itemName);
            }

            if (!sleepUntil(() -> offerMatches(order) || !Rs2GrandExchange.isOpen(), OFFER_CONFIRM_TIMEOUT_MS))
            {
                return QueueResult.active("Waiting for GE slot confirmation: " + order.itemName);
            }

            if (!Rs2GrandExchange.isOpen())
            {
                return QueueResult.active("GE closed after placing " + order.itemName + " - recovering");
            }
        }

        return null;
    }

    private static boolean ensureGeOverview()
    {
        if (Rs2Bank.isOpen())
        {
            Rs2Bank.closeBank();
            if (!sleepUntil(() -> !Rs2Bank.isOpen(), UI_TIMEOUT_MS))
            {
                return false;
            }
        }

        if (!Rs2GrandExchange.isOpen())
        {
            if (!Rs2GrandExchange.openExchange())
            {
                return false;
            }
            if (!sleepUntil(Rs2GrandExchange::isOpen, UI_TIMEOUT_MS))
            {
                return false;
            }
        }

        if (!geSubScreenOpen())
        {
            return true;
        }

        Microbot.status = "Returning to GE overview";
        Rs2GrandExchange.backToOverview();
        if (sleepUntil(() -> Rs2GrandExchange.isOpen() && !geSubScreenOpen(), 4_000))
        {
            return true;
        }

        return false;
    }

    private static boolean geSetupOpen()
    {
        return Rs2Widget.isWidgetVisible(InterfaceID.GeOffers.SETUP);
    }

    private static boolean geSubScreenOpen()
    {
        return Rs2GrandExchange.isOfferScreenOpen() || geSetupOpen();
    }

    private static void reconcileAssignments()
    {
        for (BuyOrder order : orders)
        {
            if (order.completed || order.failed)
            {
                continue;
            }

            OfferSnapshot current = getOfferSnapshot(order.slot);
            if (current != null && current.itemId == order.itemId
                && (current.state == GrandExchangeOfferState.BUYING
                    || current.state == GrandExchangeOfferState.BOUGHT))
            {
                continue;
            }

            GrandExchangeSlots recovered = findOfferSlot(order.itemId);
            if (recovered != null)
            {
                order.slot = recovered;
                if (order.placedAt <= 0L)
                {
                    order.placedAt = System.currentTimeMillis();
                }
            }
            else if (order.slot != null)
            {
                order.slot = null;
                order.placedAt = 0L;
            }
        }
    }

    private static List<GrandExchangeSlots> getAvailableGeSlots()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            List<GrandExchangeSlots> free = new ArrayList<>();
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            int max = Math.min(Rs2WorldUtil.isMemberAccount() ? 8 : 3, GrandExchangeSlots.values().length);
            for (int i = 0; i < max; i++)
            {
                GrandExchangeOffer offer = offers != null && i < offers.length ? offers[i] : null;
                if (offer == null || offer.getState() == GrandExchangeOfferState.EMPTY)
                {
                    free.add(GrandExchangeSlots.values()[i]);
                }
            }
            return free;
        }).orElse(new ArrayList<>());
    }

    private static GrandExchangeSlots findOfferSlot(int itemId)
    {
        if (itemId <= 0)
        {
            return null;
        }
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            if (offers == null)
            {
                return null;
            }
            int max = Math.min(offers.length, GrandExchangeSlots.values().length);
            for (int i = 0; i < max; i++)
            {
                GrandExchangeOffer offer = offers[i];
                if (offer == null || offer.getItemId() != itemId)
                {
                    continue;
                }
                GrandExchangeOfferState state = offer.getState();
                if (state == GrandExchangeOfferState.BUYING || state == GrandExchangeOfferState.BOUGHT)
                {
                    return GrandExchangeSlots.values()[i];
                }
            }
            return null;
        }).orElse(null);
    }

    private static OfferSnapshot getOfferSnapshot(GrandExchangeSlots slot)
    {
        if (slot == null)
        {
            return null;
        }
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            int index = slot.ordinal();
            if (offers == null || index >= offers.length || offers[index] == null)
            {
                return new OfferSnapshot(0, GrandExchangeOfferState.EMPTY, 0);
            }
            GrandExchangeOffer offer = offers[index];
            return new OfferSnapshot(offer.getItemId(), offer.getState(), offer.getQuantitySold());
        }).orElse(null);
    }

    private static boolean offerMatches(BuyOrder order)
    {
        OfferSnapshot offer = getOfferSnapshot(order.slot);
        return offer != null && offer.itemId == order.itemId
            && (offer.state == GrandExchangeOfferState.BUYING || offer.state == GrandExchangeOfferState.BOUGHT);
    }

    private static boolean hasCompletedGeOffers()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            if (offers == null)
            {
                return false;
            }
            for (GrandExchangeOffer offer : offers)
            {
                if (offer != null && (offer.getState() == GrandExchangeOfferState.BOUGHT
                    || offer.getState() == GrandExchangeOfferState.SOLD))
                {
                    return true;
                }
            }
            return false;
        }).orElse(false);
    }

    private static boolean collectAllCompletedOffers()
    {
        if (!hasCompletedGeOffers())
        {
            return true;
        }
        if (!ensureGeOverview())
        {
            return false;
        }
        Microbot.status = "Collecting completed GE offers";
        if (!Rs2GrandExchange.collectAllToBank())
        {
            return false;
        }
        return sleepUntil(() -> !hasCompletedGeOffers(), UI_TIMEOUT_MS);
    }

    private static boolean isSlotEmpty(GrandExchangeSlots slot)
    {
        OfferSnapshot snapshot = getOfferSnapshot(slot);
        return snapshot == null || snapshot.state == GrandExchangeOfferState.EMPTY;
    }

    private static boolean allDone()
    {
        return !orders.isEmpty() && orders.stream().allMatch(o -> o.completed || o.failed);
    }

    private static boolean hasFailed()
    {
        return orders.stream().anyMatch(o -> o.failed);
    }

    private static int activeOrders()
    {
        return (int) orders.stream().filter(o -> !o.completed && !o.failed && o.slot != null).count();
    }

    private static int queuedOrders()
    {
        return (int) orders.stream().filter(o -> !o.completed && !o.failed && o.slot == null).count();
    }

    static final class OrderSpec
    {
        private final int itemId;
        private final String itemName;
        private final int quantity;
        private final int price;

        OrderSpec(int itemId, String itemName, int quantity, int price)
        {
            this.itemId = itemId;
            this.itemName = itemName;
            this.quantity = quantity;
            this.price = price;
        }
    }

    private static final class BuyOrder
    {
        private final int itemId;
        private final String itemName;
        private final int price;
        private int remaining;
        private GrandExchangeSlots slot;
        private long placedAt;
        private int placementFailures;
        private int timeoutRetries;
        private boolean completed;
        private boolean failed;

        private BuyOrder(OrderSpec spec)
        {
            this.itemId = spec.itemId;
            this.itemName = spec.itemName;
            this.remaining = spec.quantity;
            this.price = spec.price;
        }
    }

    private static final class OfferSnapshot
    {
        private final int itemId;
        private final GrandExchangeOfferState state;
        private final int quantitySold;

        private OfferSnapshot(int itemId, GrandExchangeOfferState state, int quantitySold)
        {
            this.itemId = itemId;
            this.state = state;
            this.quantitySold = Math.max(0, quantitySold);
        }
    }

    static final class QueueResult
    {
        private final boolean completed;
        private final boolean failed;
        private final String message;

        private QueueResult(boolean completed, boolean failed, String message)
        {
            this.completed = completed;
            this.failed = failed;
            this.message = message == null ? "" : message;
        }

        static QueueResult active(String message)
        {
            return new QueueResult(false, false, message);
        }

        static QueueResult completed(String message)
        {
            return new QueueResult(true, false, message);
        }

        static QueueResult failed(String message)
        {
            return new QueueResult(false, true, message);
        }

        boolean isCompleted()
        {
            return completed;
        }

        boolean isFailed()
        {
            return failed;
        }

        String getMessage()
        {
            return message;
        }
    }
}
