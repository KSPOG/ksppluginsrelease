package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling;

import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeSlots;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.grandexchange.models.GrandExchangeOfferDetails;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Modify the PRICE of an existing GE offer without aborting the offer.
 *
 * Uses the in-game "Modify offer" workflow already employed by KSP Jewelry
 * Crafter. Quantity and partial fill progress remain owned by RuneScape.
 * Never opens a different slot or confirms if the item/offer changed.
 */
public final class GeOfferPriceModifier
{
    private static final int PRICE_INPUT_CHILD = 12;

    private GeOfferPriceModifier() {}

    public static boolean modify(GrandExchangeSlots slot, int itemId, long oldPrice, int newPrice)
    {
        if (slot == null || itemId <= 0 || oldPrice <= 0 || newPrice <= 0
                || !Rs2GrandExchange.isOpen()) return false;
        GrandExchangeOfferDetails before = Rs2GrandExchange.getOfferDetails(slot);
        if (!matching(before, itemId, oldPrice)) return false;

        if (Rs2GrandExchange.isOfferScreenOpen() || setupOpen())
        {
            Rs2GrandExchange.backToOverview();
            if (!sleepUntil(() -> !Rs2GrandExchange.isOfferScreenOpen() && !setupOpen(), 3_000))
                return false;
        }

        if (!clickSlot(slot)) return false;
        if (!sleepUntil(() -> setupOpen() || Rs2GrandExchange.isOfferScreenOpen(), 3_500))
            return false;
        if (!setupOpen())
        {
            if (!clickComponent(InterfaceID.GeOffers.DETAILS_MODIFY)) return false;
            if (!sleepUntil(GeOfferPriceModifier::setupOpen, 3_500)) return false;
        }

        // The original offer may have filled or been manually changed while
        // opening the editor. Do not confirm an unexpected transaction.
        if (!matching(Rs2GrandExchange.getOfferDetails(slot), itemId, oldPrice))
        {
            Rs2GrandExchange.backToOverview();
            return false;
        }

        if (!clickSetupPrice()) return false;
        if (!sleepUntil(() -> Rs2Widget.isWidgetVisible(InterfaceID.Chatbox.MES_TEXT2), 3_000))
            return false;

        Rs2GrandExchange.setChatboxValue(newPrice);
        Rs2Keyboard.enter();
        if (!sleepUntil(() -> !Rs2Widget.isWidgetVisible(InterfaceID.Chatbox.MES_TEXT2), 2_500))
            return false;
        if (!setupOpen()) return false;

        // Revalidate the slot immediately before committing the price edit.
        if (!matching(Rs2GrandExchange.getOfferDetails(slot), itemId, oldPrice))
        {
            Rs2GrandExchange.backToOverview();
            return false;
        }
        if (!clickComponent(InterfaceID.GeOffers.SETUP_CONFIRM)) return false;

        sleepUntil(() -> !setupOpen() || Rs2Widget.hasWidget("Your offer is much"), 3_000);
        if (Rs2Widget.hasWidget("Your offer is much"))
        {
            if (!Rs2Widget.clickWidget("Yes")) return false;
        }

        boolean confirmed = sleepUntil(() ->
        {
            GrandExchangeOfferDetails updated = Rs2GrandExchange.getOfferDetails(slot);
            return updated != null && updated.getItemId() == itemId
                    && updated.getPrice() == newPrice;
        }, 4_000);
        if (!confirmed && Rs2GrandExchange.isOpen()
                && (setupOpen() || Rs2GrandExchange.isOfferScreenOpen()))
            Rs2GrandExchange.backToOverview();
        return confirmed;
    }

    private static boolean matching(GrandExchangeOfferDetails details, int itemId, long price)
    {
        return details != null && details.isInProgress() && details.getItemId() == itemId
                && details.getPrice() == price;
    }

    private static boolean setupOpen()
    {
        return Rs2Widget.isWidgetVisible(InterfaceID.GeOffers.SETUP);
    }

    private static boolean clickSlot(GrandExchangeSlots slot)
    {
        int componentId = InterfaceID.GeOffers.INDEX_0 + slot.ordinal();
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Widget widget = Microbot.getClient().getWidget(componentId);
            if (widget == null || widget.isHidden()) return false;
            Rs2Widget.clickWidgetFast(widget, 2, 3);
            return true;
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

    private static boolean clickSetupPrice()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Widget setup = Microbot.getClient().getWidget(InterfaceID.GeOffers.SETUP);
            if (setup == null || setup.isHidden()) return false;
            Widget price = setup.getChild(PRICE_INPUT_CHILD);
            return price != null && !price.isHidden() && Rs2Widget.clickWidget(price);
        }).orElse(false);
    }
}
