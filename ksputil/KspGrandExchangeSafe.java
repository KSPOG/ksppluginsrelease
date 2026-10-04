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

import java.util.Arrays;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Grand Exchange compatibility layer for the September 2026 Beyond Max Cash update.
 * Avoids Microbot's obsolete GE price varbit and keeps all RuneLite Widget access
 * on the client thread.
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

    public static boolean buy(String itemName, int quantity, long price,
                              boolean exact, boolean closeAfterCompletion)
    {
        return processOffer(null, GrandExchangeAction.BUY, itemName, exact,
                quantity, price, closeAfterCompletion);
    }

    public static boolean sell(String itemName, int quantity, long price,
                               boolean exact, boolean closeAfterCompletion)
    {
        return processOffer(null, GrandExchangeAction.SELL, itemName, exact,
                quantity, price, closeAfterCompletion);
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
        if (!acceptWarningIfPresent() || !Rs2GrandExchange.isOpen()) return false;

        boolean committed = sleepUntil(() ->
        {
            GrandExchangeOffer current = offerAt(slot);
            return offerChanged(before, current)
                    || Rs2GrandExchange.getAvailableSlotsCount() < slotsBefore;
        }, 2_500);

        if (!committed) return false;

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

        if (closeAfterCompletion) Rs2GrandExchange.closeExchange();
        return true;
    }

    private static boolean openBuyEditor(GrandExchangeSlots slot, String itemName, boolean exact)
    {
        if (!clickSlotChild(slot, 0)) return false;
        if (!sleepUntil(Rs2GrandExchange::isOfferScreenOpen, 2_500)) return false;

        if (!Rs2Widget.sleepUntilHasWidgetText(
                "Start typing the name of an item to search for it",
                GE_SEARCH_GROUP, GE_SEARCH_PROMPT_CHILD, false, 2_500))
        {
            return false;
        }

        Rs2Keyboard.typeString(itemName);
        if (!sleepUntil(() -> searchResultReady(itemName, exact), 2_500)) return false;
        if (!clickSearchResult(itemName, exact)) return false;
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
        if (!clickSetupChild(child)) return false;
        if (!sleepUntil(KspGrandExchangeSafe::chatboxValueInputOpen, 2_000)) return false;
        if (!setChatboxValue(value)) return false;

        Rs2Keyboard.enter();
        return sleepUntil(() -> !chatboxValueInputOpen() || !Rs2GrandExchange.isOpen(), 2_000)
                && Rs2GrandExchange.isOpen();
    }

    public static boolean setChatboxValue(long value)
    {
        if (value <= 0L) return false;
        String text = Long.toString(value);

        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Widget input = Microbot.getClient().getWidget(InterfaceID.Chatbox.MES_TEXT2);
            if (input == null || input.isHidden()) return false;
            input.setText(text + "*");
            Microbot.getClient().setVarcStrValue(VarClientStr.INPUT_TEXT, text);
            return true;
        }).orElse(false);
    }

    private static boolean confirmOffer()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Widget setup = Microbot.getClient().getWidget(GE_GROUP, GE_SETUP_CHILD);
            if (setup == null || setup.isHidden() || setup.getDynamicChildren() == null) return false;
            Widget confirm = Rs2Widget.findWidget(
                    "Confirm", Arrays.asList(setup.getDynamicChildren()), true);
            return confirm != null && Rs2Widget.clickWidget(confirm);
        }).orElse(false);
    }

    private static boolean acceptWarningIfPresent()
    {
        sleepUntil(() -> !Rs2GrandExchange.isOfferScreenOpen()
                || Rs2Widget.hasWidget("Your offer is much")
                || !Rs2GrandExchange.isOpen(), 2_500);

        if (!Rs2GrandExchange.isOpen()) return false;
        if (!Rs2Widget.hasWidget("Your offer is much")) return true;
        if (!Rs2Widget.clickWidget("Yes")) return false;

        return sleepUntil(() -> !Rs2GrandExchange.isOfferScreenOpen()
                || !Rs2GrandExchange.isOpen(), 2_500)
                && Rs2GrandExchange.isOpen();
    }

    private static boolean clickSlotChild(GrandExchangeSlots slot, int child)
    {
        if (slot == null) return false;
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Widget root = Microbot.getClient().getWidget(GE_GROUP,
                    GE_SLOT_FIRST_CHILD + slot.ordinal());
            if (root == null || root.isHidden()) return false;
            Widget control = root.getChild(child);
            return control != null && !control.isHidden() && Rs2Widget.clickWidget(control);
        }).orElse(false);
    }

    private static boolean clickSetupChild(int child)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Widget setup = Microbot.getClient().getWidget(GE_GROUP, GE_SETUP_CHILD);
            if (setup == null || setup.isHidden()) return false;
            Widget control = setup.getChild(child);
            return control != null && !control.isHidden() && Rs2Widget.clickWidget(control);
        }).orElse(false);
    }

    private static boolean setupOpen()
    {
        if (!Rs2GrandExchange.isOpen()) return false;
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Widget setup = Microbot.getClient().getWidget(GE_GROUP, GE_SETUP_CHILD);
            return setup != null && !setup.isHidden();
        }).orElse(false);
    }

    private static boolean chatboxValueInputOpen()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Widget input = Microbot.getClient().getWidget(InterfaceID.Chatbox.MES_TEXT2);
            return input != null && !input.isHidden();
        }).orElse(false);
    }

    private static boolean searchResultReady(String itemName, boolean exact)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                Rs2GrandExchange.getSearchResultWidget(itemName, exact) != null).orElse(false);
    }

    private static boolean clickSearchResult(String itemName, boolean exact)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            var result = Rs2GrandExchange.getSearchResultWidget(itemName, exact);
            if (result == null || result.getLeft() == null) return false;
            Rs2Widget.clickWidgetFast(result.getLeft(), result.getRight(), 1);
            return true;
        }).orElse(false);
    }

    public static long readDisplayedOfferPrice()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Widget setup = Microbot.getClient().getWidget(GE_GROUP, GE_SETUP_CHILD);
            if (setup == null || setup.isHidden()) return -1L;
            Widget price = setup.getChild(GE_SELECTED_PRICE_CHILD);
            if (price == null || price.isHidden() || price.getText() == null) return -1L;
            String digits = price.getText().replaceAll("[^0-9]", "");
            if (digits.isEmpty()) return -1L;
            try { return Long.parseLong(digits); }
            catch (NumberFormatException ignored) { return -1L; }
        }).orElse(-1L);
    }

    private static GrandExchangeOffer offerAt(GrandExchangeSlots slot)
    {
        if (slot == null || Microbot.getClient() == null) return null;
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            int index = slot.ordinal();
            return offers != null && index >= 0 && index < offers.length ? offers[index] : null;
        }).orElse(null);
    }

    private static boolean offerChanged(GrandExchangeOffer before, GrandExchangeOffer after)
    {
        if (after == null || after.getState() == GrandExchangeOfferState.EMPTY) return false;
        if (before == null || before.getState() == GrandExchangeOfferState.EMPTY) return true;
        return before.getItemId() != after.getItemId()
                || before.getState() != after.getState()
                || before.getTotalQuantity() != after.getTotalQuantity()
                || before.getQuantitySold() != after.getQuantitySold();
    }
}
