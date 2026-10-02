package net.runelite.client.plugins.microbot.ksputil;

import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.VarClientStr;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeAction;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeRequest;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeSlots;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Grand Exchange compatibility layer for the September 2026 Beyond Max Cash
 * update.
 *
 * Microbot 2.6.26/development still reads legacy varbit 4398 as the offer
 * price and models GE prices as int. Current RuneLite models offer price and
 * spent as long. This helper deliberately avoids the legacy price varbit and
 * writes custom GE values through the chatbox input instead.
 */
public final class KspGrandExchangeSafe
{
    private static final int GE_GROUP = InterfaceID.GE_OFFERS;
    private static final int GE_SLOT_FIRST_CHILD = 7;
    private static final int GE_SETUP_CHILD = 26;
    private static final int GE_QUANTITY_X_CHILD = 7;
    private static final int GE_PRICE_X_CHILD = 12;
    private static final int GE_SELECTED_PRICE_CHILD = 41;
    private static final int GE_SEARCH_GROUP = 162;
    private static final int GE_SEARCH_PROMPT_CHILD = 52;

    private KspGrandExchangeSafe() {}

    /**
     * Compatibility adapter for existing Microbot GrandExchangeRequest callers.
     * The request itself is still int-priced in Microbot 2.6.26, but all KSP
     * internals below use long.
     */
    public static boolean processOffer(GrandExchangeRequest request)
    {
        if (request == null || request.getAction() == null) return false;

        if (request.getAction() == GrandExchangeAction.COLLECT)
        {
            return request.getSlot() == null
                    ? Rs2GrandExchange.collectAll(request.isToBank())
                    : Rs2GrandExchange.collectOffer(request.getSlot(), request.isToBank());
        }

        return processOffer(
                request.getSlot(),
                request.getAction(),
                request.getItemName(),
                request.isExact(),
                request.getQuantity(),
                (long) request.getPrice(),
                request.isCloseAfterCompletion());
    }

    public static boolean buy(
            String itemName,
            int quantity,
            long price,
            boolean exact,
            boolean closeAfterCompletion)
    {
        return processOffer(
                null,
                GrandExchangeAction.BUY,
                itemName,
                exact,
                quantity,
                price,
                closeAfterCompletion);
    }

    public static boolean sell(
            String itemName,
            int quantity,
            long price,
            boolean exact,
            boolean closeAfterCompletion)
    {
        return processOffer(
                null,
                GrandExchangeAction.SELL,
                itemName,
                exact,
                quantity,
                price,
                closeAfterCompletion);
    }

    public static boolean processOffer(
            GrandExchangeSlots requestedSlot,
            GrandExchangeAction action,
            String itemName,
            boolean exact,
            int quantity,
            long price,
            boolean closeAfterCompletion)
    {
        if (action == null || itemName == null || itemName.isBlank()
                || quantity <= 0 || price <= 0L || !Rs2GrandExchange.isOpen())
        {
            return false;
        }

        GrandExchangeSlots slot = requestedSlot != null
                ? requestedSlot
                : Rs2GrandExchange.getAvailableSlot();
        if (slot == null) return false;

        GrandExchangeOffer before = offerAt(slot);
        int slotsBefore = Rs2GrandExchange.getAvailableSlotsCount();

        boolean opened = action == GrandExchangeAction.BUY
                ? openBuyEditor(slot, itemName, exact)
                : openSellEditor(itemName, exact);
        if (!opened) return false;

        if (!setOfferValue(GE_PRICE_X_CHILD, price)) return false;
        if (!setOfferValue(GE_QUANTITY_X_CHILD, quantity)) return false;
        if (!confirmOffer()) return false;

        if (!acceptWarningIfPresent()) return false;
        if (!Rs2GrandExchange.isOpen()) return false;

        boolean committed = sleepUntil(() ->
        {
            GrandExchangeOffer current = offerAt(slot);
            return offerChanged(before, current)
                    || Rs2GrandExchange.getAvailableSlotsCount() < slotsBefore;
        }, 2_500);

        if (!committed) return false;

        // On the current 2.6.26 API getPrice() is int; upstream RuneLite now
        // returns long. Widening the result here compiles against both.
        GrandExchangeOffer placed = offerAt(slot);
        if (placed != null
                && placed.getState() != GrandExchangeOfferState.EMPTY
                && price <= Integer.MAX_VALUE)
        {
            long observedPrice = placed.getPrice();
            if (observedPrice > 0L && observedPrice != price)
            {
                Microbot.log("KSP GE: offer committed with observed price "
                        + observedPrice + " instead of requested " + price);
            }
        }

        if (closeAfterCompletion)
        {
            Rs2GrandExchange.closeExchange();
        }

        return true;
    }

    private static boolean openBuyEditor(
            GrandExchangeSlots slot,
            String itemName,
            boolean exact)
    {
        Widget slotWidget = Rs2Widget.getWidget(GE_GROUP, GE_SLOT_FIRST_CHILD + slot.ordinal());
        if (slotWidget == null || slotWidget.isHidden()) return false;

        Widget buy = slotWidget.getChild(0);
        if (buy == null || buy.isHidden() || !Rs2Widget.clickWidget(buy)) return false;

        if (!sleepUntil(Rs2GrandExchange::isOfferScreenOpen, 2_500)) return false;

        if (!Rs2Widget.sleepUntilHasWidgetText(
                "Start typing the name of an item to search for it",
                GE_SEARCH_GROUP,
                GE_SEARCH_PROMPT_CHILD,
                false,
                2_500))
        {
            return false;
        }

        Rs2Keyboard.typeString(itemName);

        if (!sleepUntil(() ->
                Rs2GrandExchange.getSearchResultWidget(itemName, exact) != null, 2_500))
        {
            return false;
        }

        var result = Rs2GrandExchange.getSearchResultWidget(itemName, exact);
        if (result == null || result.getLeft() == null) return false;

        Rs2Widget.clickWidgetFast(result.getLeft(), result.getRight(), 1);
        return sleepUntil(KspGrandExchangeSafe::setupOpen, 2_500);
    }

    private static boolean openSellEditor(String itemName, boolean exact)
    {
        if (!Rs2Inventory.hasItem(itemName, exact)) return false;
        if (!Rs2Inventory.interact(itemName, "Offer", exact)) return false;
        return sleepUntil(KspGrandExchangeSafe::setupOpen, 2_500);
    }

    private static boolean setOfferValue(int child, long value)
    {
        if (value <= 0L || !setupOpen()) return false;

        Widget control = setupChild(child);
        if (control == null || !Rs2Widget.clickWidget(control)) return false;

        if (!sleepUntil(KspGrandExchangeSafe::chatboxValueInputOpen, 2_000))
        {
            return false;
        }

        if (!setChatboxValue(value)) return false;
        Rs2Keyboard.enter();

        return sleepUntil(() ->
                !chatboxValueInputOpen() || !Rs2GrandExchange.isOpen(), 2_000)
                && Rs2GrandExchange.isOpen();
    }

    /**
     * Equivalent to Microbot's setChatboxValue(int), but accepts the 64-bit
     * values used by current RuneLite's GrandExchangeOffer API.
     */
    public static boolean setChatboxValue(long value)
    {
        Widget input = Rs2Widget.getWidget(InterfaceID.Chatbox.MES_TEXT2);
        if (input == null) return false;

        String text = Long.toString(value);
        input.setText(text + "*");

        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Microbot.getClient().setVarcStrValue(VarClientStr.INPUT_TEXT, text);
            return true;
        }).orElse(false);
    }

    private static boolean confirmOffer()
    {
        Widget setup = Rs2Widget.getWidget(GE_GROUP, GE_SETUP_CHILD);
        if (setup == null || setup.isHidden() || setup.getDynamicChildren() == null) return false;

        Widget confirm = Rs2Widget.findWidget(
                "Confirm",
                java.util.Arrays.asList(setup.getDynamicChildren()),
                true);

        return confirm != null && Rs2Widget.clickWidget(confirm);
    }

    private static boolean acceptWarningIfPresent()
    {
        sleepUntil(() ->
                !Rs2GrandExchange.isOfferScreenOpen()
                        || Rs2Widget.hasWidget("Your offer is much")
                        || !Rs2GrandExchange.isOpen(), 2_500);

        if (!Rs2GrandExchange.isOpen()) return false;

        if (Rs2Widget.hasWidget("Your offer is much"))
        {
            if (!Rs2Widget.clickWidget("Yes")) return false;
            return sleepUntil(() ->
                    !Rs2GrandExchange.isOfferScreenOpen()
                            || !Rs2GrandExchange.isOpen(), 2_500)
                    && Rs2GrandExchange.isOpen();
        }

        return true;
    }

    private static boolean setupOpen()
    {
        Widget setup = Rs2Widget.getWidget(GE_GROUP, GE_SETUP_CHILD);
        return Rs2GrandExchange.isOpen() && setup != null && !setup.isHidden();
    }

    private static boolean chatboxValueInputOpen()
    {
        Widget input = Rs2Widget.getWidget(InterfaceID.Chatbox.MES_TEXT2);
        return input != null && !input.isHidden();
    }

    private static Widget setupChild(int child)
    {
        Widget setup = Rs2Widget.getWidget(GE_GROUP, GE_SETUP_CHILD);
        return setup == null ? null : setup.getChild(child);
    }

    public static long readDisplayedOfferPrice()
    {
        Widget price = setupChild(GE_SELECTED_PRICE_CHILD);
        if (price == null || price.getText() == null) return -1L;

        String digits = price.getText().replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return -1L;

        try
        {
            return Long.parseLong(digits);
        }
        catch (NumberFormatException ignored)
        {
            return -1L;
        }
    }

    private static GrandExchangeOffer offerAt(GrandExchangeSlots slot)
    {
        if (slot == null || Microbot.getClient() == null) return null;

        GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
        int index = slot.ordinal();
        return offers != null && index >= 0 && index < offers.length
                ? offers[index]
                : null;
    }

    private static boolean offerChanged(
            GrandExchangeOffer before,
            GrandExchangeOffer after)
    {
        if (after == null || after.getState() == GrandExchangeOfferState.EMPTY)
        {
            return false;
        }

        if (before == null || before.getState() == GrandExchangeOfferState.EMPTY)
        {
            return true;
        }

        return before.getItemId() != after.getItemId()
                || before.getState() != after.getState()
                || before.getTotalQuantity() != after.getTotalQuantity()
                || before.getQuantitySold() != after.getQuantitySold();
    }
}
