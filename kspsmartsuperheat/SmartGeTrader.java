package net.runelite.client.plugins.microbot.kspsmartsuperheat;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeAction;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeRequest;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeSlots;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.world.Rs2WorldUtil;

import java.util.ArrayList;
import java.util.List;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Grand Exchange execution for Smart Superheat.
 *
 * <p>The initial offer path mirrors KSP Jewellery Crafter: exact
 * {@link GrandExchangeRequest} placement followed by verification against the client's live
 * {@link GrandExchangeOffer} array. No GE setup-child arithmetic or inferred "free before / free
 * after" slot detection is used.</p>
 */
@Slf4j
final class SmartGeTrader
{
    private static final int UI_TIMEOUT_MS = 5_000;
    private static final int OFFER_CONFIRM_TIMEOUT_MS = 3_500;

    private SmartGeTrader()
    {
    }

    static TradeResult buyToBank(
        int itemId,
        String itemName,
        int quantity,
        int price,
        int timeoutSeconds)
    {
        return execute(itemId, itemName, quantity, price, timeoutSeconds, GrandExchangeAction.BUY);
    }

    static TradeResult sellFromInventory(
        int itemId,
        String itemName,
        int quantity,
        int price,
        int timeoutSeconds)
    {
        return execute(itemId, itemName, quantity, price, timeoutSeconds, GrandExchangeAction.SELL);
    }

    private static TradeResult execute(
        int itemId,
        String itemName,
        int quantity,
        int price,
        int timeoutSeconds,
        GrandExchangeAction action)
    {
        if (itemId <= 0 || itemName == null || itemName.isBlank() || quantity <= 0 || price <= 0)
        {
            return TradeResult.failed("Invalid " + actionName(action) + " request");
        }

        if (!ensureExchangeOpen())
        {
            return TradeResult.failed("Could not open Grand Exchange");
        }

        // Recovery / duplicate guard. If a previous pass already created this offer, monitor it
        // instead of placing another full restock order.
        GrandExchangeSlots existing = findOfferSlot(itemId, action);
        if (existing != null)
        {
            Microbot.status = "GE " + actionName(action) + " already active: " + itemName;
            return waitForOffer(existing, itemId, itemName, quantity, timeoutSeconds, action);
        }

        List<GrandExchangeSlots> freeSlots = getAvailableGeSlots();
        if (freeSlots.isEmpty())
        {
            return TradeResult.failed("No free Grand Exchange slot");
        }

        GrandExchangeSlots requestedSlot = freeSlots.get(0);
        GrandExchangeRequest request;
        if (action == GrandExchangeAction.BUY)
        {
            // Jewellery Crafter explicitly owns BUY slots.
            request = GrandExchangeRequest.builder()
                .slot(requestedSlot)
                .action(GrandExchangeAction.BUY)
                .itemName(itemName)
                .exact(true)
                .quantity(quantity)
                .price(price)
                .closeAfterCompletion(false)
                .build();
        }
        else
        {
            // SELL placement is left to Microbot's inventory-offer path, then resolved from the
            // live client offer array, matching Jewellery Crafter.
            request = GrandExchangeRequest.builder()
                .action(GrandExchangeAction.SELL)
                .itemName(itemName)
                .exact(true)
                .quantity(quantity)
                .price(price)
                .closeAfterCompletion(false)
                .build();
        }

        Microbot.status = action == GrandExchangeAction.BUY
            ? "Buying " + quantity + " x " + itemName + " in GE slot " + (requestedSlot.ordinal() + 1)
            : "Selling " + quantity + " x " + itemName;

        if (!Rs2GrandExchange.processOffer(request))
        {
            // processOffer may have progressed far enough for the offer to exist. Do not blindly
            // retry and create a duplicate; a subsequent pass can recover it by item/state.
            GrandExchangeSlots recovered = findOfferSlot(itemId, action);
            if (recovered != null)
            {
                return waitForOffer(recovered, itemId, itemName, quantity, timeoutSeconds, action);
            }

            recoverToOverview();
            return TradeResult.failed(
                Rs2GrandExchange.isOpen()
                    ? "GE " + actionName(action) + " placement failed"
                    : "GE closed during " + actionName(action) + " placement"
            );
        }

        final GrandExchangeSlots expectedBuySlot = action == GrandExchangeAction.BUY
            ? requestedSlot
            : null;

        boolean registered = sleepUntil(() ->
            (expectedBuySlot != null && offerMatches(expectedBuySlot, itemId, action))
                || findOfferSlot(itemId, action) != null
                || !Rs2GrandExchange.isOpen(),
            OFFER_CONFIRM_TIMEOUT_MS
        );

        if (!registered)
        {
            return TradeResult.failed("Waiting for GE " + actionName(action) + " slot confirmation");
        }

        GrandExchangeSlots resolved = findOfferSlot(itemId, action);
        if (resolved == null && expectedBuySlot != null && offerMatches(expectedBuySlot, itemId, action))
        {
            resolved = expectedBuySlot;
        }

        if (resolved == null)
        {
            return TradeResult.failed("Could not resolve " + actionName(action) + " offer slot");
        }

        return waitForOffer(resolved, itemId, itemName, quantity, timeoutSeconds, action);
    }

    private static TradeResult waitForOffer(
        GrandExchangeSlots slot,
        int itemId,
        String itemName,
        int requestedQuantity,
        int timeoutSeconds,
        GrandExchangeAction action)
    {
        if (slot == null)
        {
            return TradeResult.failed("Missing GE offer slot");
        }

        GrandExchangeOfferState completedState = action == GrandExchangeAction.BUY
            ? GrandExchangeOfferState.BOUGHT
            : GrandExchangeOfferState.SOLD;

        sleepUntil(() ->
        {
            OfferSnapshot snapshot = getOfferSnapshot(slot);
            return snapshot != null
                && snapshot.itemId == itemId
                && snapshot.state == completedState;
        }, Math.max(1, timeoutSeconds) * 1_000);

        OfferSnapshot snapshot = getOfferSnapshot(slot);
        if (snapshot != null && snapshot.itemId == itemId && snapshot.state == completedState)
        {
            int filled = snapshot.quantitySold > 0 ? snapshot.quantitySold : requestedQuantity;
            if (!collectSlotToBank(slot))
            {
                // Do not let the caller debit its remaining restock budget until collection is
                // confirmed. The next pass will recover this same completed offer.
                return TradeResult.failed("Offer completed; waiting for collection");
            }
            return TradeResult.completed(Math.min(requestedQuantity, filled));
        }

        int filled = snapshot != null && snapshot.itemId == itemId
            ? Math.max(0, snapshot.quantitySold)
            : 0;

        if (snapshot != null && snapshot.itemId == itemId && isActiveState(snapshot.state, action))
        {
            Microbot.status = "GE " + actionName(action) + " timed out: " + itemName;
            Rs2GrandExchange.cancelSpecificOffers(List.of(slot), true);
            sleepUntil(() -> isSlotEmpty(slot), UI_TIMEOUT_MS);
        }

        return TradeResult.partial(
            Math.min(requestedQuantity, filled),
            capitalize(actionName(action)) + " timed out; partial fill collected"
        );
    }

    static boolean ensureExchangeOpen()
    {
        if (Rs2Bank.isOpen())
        {
            Rs2Bank.closeBank();
            if (!sleepUntil(() -> !Rs2Bank.isOpen(), UI_TIMEOUT_MS))
            {
                return false;
            }
            sleep(250, 450);
        }

        if (!openVerifiedGe())
        {
            Rs2GrandExchange.walkToGrandExchange();
            sleepUntil(() -> !Rs2Player.isMoving(), 20_000);
            if (!openVerifiedGe())
            {
                return false;
            }
        }

        if (!geSubScreenOpen())
        {
            return true;
        }

        return recoverToOverview();
    }

    private static boolean openVerifiedGe()
    {
        if (Rs2GrandExchange.isOpen())
        {
            return true;
        }

        if (!Rs2GrandExchange.openExchange())
        {
            return false;
        }

        return sleepUntil(Rs2GrandExchange::isOpen, UI_TIMEOUT_MS);
    }

    private static boolean recoverToOverview()
    {
        if (!Rs2GrandExchange.isOpen())
        {
            return false;
        }
        if (!geSubScreenOpen())
        {
            return true;
        }

        Microbot.status = "Returning to GE overview";
        Rs2GrandExchange.backToOverview();
        boolean ready = sleepUntil(
            () -> Rs2GrandExchange.isOpen() && !geSubScreenOpen(),
            4_000
        );
        if (ready)
        {
            sleep(250, 450);
        }
        return ready;
    }

    private static boolean geSetupOpen()
    {
        return Rs2Widget.isWidgetVisible(InterfaceID.GeOffers.SETUP);
    }

    private static boolean geSubScreenOpen()
    {
        return Rs2GrandExchange.isOfferScreenOpen() || geSetupOpen();
    }

    private static List<GrandExchangeSlots> getAvailableGeSlots()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            List<GrandExchangeSlots> free = new ArrayList<>();
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            int max = Math.min(
                Rs2WorldUtil.isMemberAccount() ? 8 : 3,
                GrandExchangeSlots.values().length
            );

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

    private static GrandExchangeSlots findOfferSlot(int itemId, GrandExchangeAction action)
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
                if (action == GrandExchangeAction.BUY
                    && (state == GrandExchangeOfferState.BUYING || state == GrandExchangeOfferState.BOUGHT))
                {
                    return GrandExchangeSlots.values()[i];
                }
                if (action == GrandExchangeAction.SELL
                    && (state == GrandExchangeOfferState.SELLING || state == GrandExchangeOfferState.SOLD))
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
                return new OfferSnapshot(0, GrandExchangeOfferState.EMPTY, 0, 0);
            }

            GrandExchangeOffer offer = offers[index];
            return new OfferSnapshot(
                offer.getItemId(),
                offer.getState(),
                offer.getQuantitySold(),
                offer.getPrice()
            );
        }).orElse(null);
    }

    private static boolean offerMatches(
        GrandExchangeSlots slot,
        int itemId,
        GrandExchangeAction action)
    {
        OfferSnapshot snapshot = getOfferSnapshot(slot);
        return snapshot != null
            && snapshot.itemId == itemId
            && (action == GrandExchangeAction.BUY
                ? snapshot.state == GrandExchangeOfferState.BUYING
                    || snapshot.state == GrandExchangeOfferState.BOUGHT
                : snapshot.state == GrandExchangeOfferState.SELLING
                    || snapshot.state == GrandExchangeOfferState.SOLD);
    }

    private static boolean isActiveState(GrandExchangeOfferState state, GrandExchangeAction action)
    {
        return action == GrandExchangeAction.BUY
            ? state == GrandExchangeOfferState.BUYING
            : state == GrandExchangeOfferState.SELLING;
    }

    private static boolean isSlotEmpty(GrandExchangeSlots slot)
    {
        OfferSnapshot snapshot = getOfferSnapshot(slot);
        return snapshot == null || snapshot.state == GrandExchangeOfferState.EMPTY;
    }

    private static boolean collectSlotToBank(GrandExchangeSlots slot)
    {
        if (slot == null)
        {
            return false;
        }

        if (!ensureExchangeOpen())
        {
            return false;
        }

        Rs2GrandExchange.collectOffer(slot, true);
        return sleepUntil(() -> isSlotEmpty(slot), UI_TIMEOUT_MS);
    }

    private static String actionName(GrandExchangeAction action)
    {
        return action == GrandExchangeAction.BUY ? "buy" : "sell";
    }

    private static String capitalize(String value)
    {
        if (value == null || value.isEmpty())
        {
            return "Trade";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static final class OfferSnapshot
    {
        private final int itemId;
        private final GrandExchangeOfferState state;
        private final int quantitySold;
        @SuppressWarnings("unused")
        private final int price;

        private OfferSnapshot(
            int itemId,
            GrandExchangeOfferState state,
            int quantitySold,
            int price)
        {
            this.itemId = itemId;
            this.state = state;
            this.quantitySold = Math.max(0, quantitySold);
            this.price = price;
        }
    }

    static final class TradeResult
    {
        private final boolean placed;
        private final boolean completed;
        private final int filledQuantity;
        private final String message;

        private TradeResult(boolean placed, boolean completed, int filledQuantity, String message)
        {
            this.placed = placed;
            this.completed = completed;
            this.filledQuantity = Math.max(0, filledQuantity);
            this.message = message == null ? "" : message;
        }

        static TradeResult completed(int filled)
        {
            return new TradeResult(true, true, filled, "Completed");
        }

        static TradeResult partial(int filled, String message)
        {
            return new TradeResult(true, false, filled, message);
        }

        static TradeResult failed(String message)
        {
            return new TradeResult(false, false, 0, message);
        }

        boolean isPlaced() { return placed; }
        boolean isCompleted() { return completed; }
        int getFilledQuantity() { return filledQuantity; }
        String getMessage() { return message; }
    }
}
