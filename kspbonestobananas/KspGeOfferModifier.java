package net.runelite.client.plugins.microbot.kspbonestobananas;

import java.awt.Rectangle;
import net.runelite.api.MenuAction;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeSlots;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/** Reprices an existing GE slot; never aborts or submits a new offer. */
final class KspGeOfferModifier
{
    private KspGeOfferModifier() { }

    static boolean modifyPrice(GrandExchangeSlots slot, int price)
    {
        if (slot == null || price <= 0 || !Rs2GrandExchange.isOpen()
                || Rs2GrandExchange.isOfferScreenOpen()) return false;
        Widget slotWidget = Rs2Widget.getWidget(InterfaceID.GE_OFFERS, 7 + slot.ordinal());
        if (slotWidget == null || slotWidget.getActions() == null) return false;

        int optionIndex = -1;
        String[] actions = slotWidget.getActions();
        for (int i = 0; i < actions.length; i++)
        {
            if (actions[i] != null && "Modify offer".equalsIgnoreCase(actions[i].trim()))
            {
                optionIndex = i + 1;
                break;
            }
        }
        if (optionIndex < 1) return false; // Do not guess an action index.

        Rectangle bounds = slotWidget.getBounds();
        if (bounds == null) return false;
        Microbot.doInvoke(new NewMenuEntry().option("Modify offer").target("")
                .identifier(optionIndex).type(MenuAction.CC_OP).param0(2)
                .param1(slotWidget.getId()).itemId(-1).forceLeftClick(false), bounds);

        if (!sleepUntil(Rs2GrandExchange::isOfferScreenOpen, 3000)) return false;
        Widget container = Rs2Widget.getWidget(InterfaceID.GE_OFFERS, 26);
        if (container == null) return false;
        Widget priceButton = container.getChild(12);
        if (priceButton == null || priceButton.getBounds() == null) return false;
        if (!Rs2Widget.clickWidget(priceButton)) return false;
        if (!sleepUntil(() -> Rs2Widget.getWidget(InterfaceID.Chatbox.MES_TEXT2) != null, 2500)) return false;
        Rs2Keyboard.typeString(Integer.toString(price));
        Rs2Keyboard.enter();

        Widget confirm = Rs2Widget.findWidget("Confirm",
                java.util.Arrays.asList(container.getDynamicChildren()), true);
        if (confirm == null || !Rs2Widget.clickWidget(confirm)) return false;
        sleepUntil(() -> !Rs2GrandExchange.isOfferScreenOpen(), 4000);
        return true;
    }
}
