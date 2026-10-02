package net.runelite.client.plugins.microbot.ksputil;

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
 * GE offer entry that deliberately avoids Rs2GrandExchange.processOffer().
 *
 * The September 2026 Beyond Max Cash update invalidated Microbot's legacy
 * hard-coded offer-price varbit (4398). Rs2GrandExchange.processOffer() still
 * reads that varbit from setPrice(), so KSP plugins use widget/chatbox state
 * instead until Microbot exposes the new offer-value API.
 */
public final class KspGrandExchangeSafe
{
    private static final int GE_QUANTITY_X_CHILD = 7;
    private static final int GE_PRICE_X_CHILD = 12;
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

        if (!Rs2GrandExchange.isOpen() || request.getItemName() == null
                || request.getItemName().isBlank() || request.getQuantity() <= 0)
        {
            return false;
        }

        int slotsBefore = Rs2GrandExchange.getAvailableSlotsCount();
        if (slotsBefore <= 0) return false;

        boolean opened = request.getAction() == GrandExchangeAction.BUY
                ? openBuyEditor(request)
                : openSellEditor(request);
        if (!opened) return false;

        if (request.getPrice() <= 0) return false;
        if (!setOfferValue(GE_PRICE_X_CHILD, request.getPrice())) return false;
        if (!setOfferValue(GE_QUANTITY_X_CHILD, request.getQuantity())) return false;

        if (!clickComponent(InterfaceID.GeOffers.SETUP_CONFIRM)) return false;

        sleepUntil(() -> !Rs2GrandExchange.isOfferScreenOpen()
                || Rs2Widget.hasWidget("Your offer is much")
                || !Rs2GrandExchange.isOpen(), 3_000);

        if (Rs2Widget.hasWidget("Your offer is much"))
        {
            Rs2Widget.clickWidget("Yes");
            sleepUntil(() -> !Rs2GrandExchange.isOfferScreenOpen()
                    || !Rs2GrandExchange.isOpen(), 3_000);
        }

        if (!Rs2GrandExchange.isOpen()) return false;

        boolean committed = sleepUntil(() ->
                Rs2GrandExchange.getAvailableSlotsCount() < slotsBefore, 2_000);

        if (committed && request.isCloseAfterCompletion())
        {
            Rs2GrandExchange.closeExchange();
        }

        return committed;
    }

    private static boolean openBuyEditor(GrandExchangeRequest request)
    {
        GrandExchangeSlots slot = request.getSlot() != null
                ? request.getSlot()
                : Rs2GrandExchange.getAvailableSlot();
        if (slot == null) return false;

        int componentId = InterfaceID.GeOffers.INDEX_0 + slot.ordinal();
        boolean clicked = Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Widget slotWidget = Microbot.getClient().getWidget(componentId);
            if (slotWidget == null || slotWidget.isHidden()) return false;
            Widget buy = slotWidget.getChild(0);
            return buy != null && !buy.isHidden() && Rs2Widget.clickWidget(buy);
        }).orElse(false);
        if (!clicked) return false;

        if (!sleepUntil(Rs2GrandExchange::isOfferScreenOpen, 3_000)) return false;
        if (!Rs2Widget.sleepUntilHasWidgetText(
                "Start typing the name of an item to search for it",
                GE_SEARCH_GROUP, GE_SEARCH_PROMPT_CHILD, false, 3_000))
        {
            return false;
        }

        Rs2Keyboard.typeString(request.getItemName());
        if (!sleepUntil(() -> Rs2GrandExchange.getSearchResultWidget(
                request.getItemName(), request.isExact()) != null, 2_500))
        {
            return false;
        }

        var result = Rs2GrandExchange.getSearchResultWidget(
                request.getItemName(), request.isExact());
        if (result == null || result.getLeft() == null) return false;

        Rs2Widget.clickWidgetFast(result.getLeft(), result.getRight(), 1);
        return sleepUntil(KspGrandExchangeSafe::setupOpen, 2_500);
    }

    private static boolean openSellEditor(GrandExchangeRequest request)
    {
        if (!Rs2Inventory.hasItem(request.getItemName(), request.isExact())) return false;
        if (!Rs2Inventory.interact(request.getItemName(), "Offer", request.isExact())) return false;
        return sleepUntil(KspGrandExchangeSafe::setupOpen, 2_500);
    }

    private static boolean setOfferValue(int child, int value)
    {
        if (value <= 0 || !setupOpen()) return false;

        Widget control = Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Widget setup = Microbot.getClient().getWidget(InterfaceID.GeOffers.SETUP);
            if (setup == null || setup.isHidden()) return null;
            Widget w = setup.getChild(child);
            return w != null && !w.isHidden() ? w : null;
        }).orElse(null);
        if (control == null || !Rs2Widget.clickWidget(control)) return false;

        if (!sleepUntil(() -> Rs2Widget.isWidgetVisible(InterfaceID.Chatbox.MES_TEXT2)
                || !Rs2GrandExchange.isOpen(), 2_000))
        {
            return false;
        }
        if (!Rs2GrandExchange.isOpen()) return false;

        Rs2GrandExchange.setChatboxValue(value);
        Rs2Keyboard.enter();

        return sleepUntil(() -> !Rs2Widget.isWidgetVisible(InterfaceID.Chatbox.MES_TEXT2)
                || !Rs2GrandExchange.isOpen(), 2_000)
                && Rs2GrandExchange.isOpen();
    }

    private static boolean setupOpen()
    {
        return Rs2GrandExchange.isOpen()
                && Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Widget setup = Microbot.getClient().getWidget(InterfaceID.GeOffers.SETUP);
            return setup != null && !setup.isHidden();
        }).orElse(false);
    }

    private static boolean clickComponent(int componentId)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Widget widget = Microbot.getClient().getWidget(componentId);
            return widget != null && !widget.isHidden() && Rs2Widget.clickWidget(widget);
        }).orElse(false);
    }
}
