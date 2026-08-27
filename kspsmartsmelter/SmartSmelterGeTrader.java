package net.runelite.client.plugins.microbot.kspsmartsmelter;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeSlots;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.function.IntSupplier;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
final class SmartSmelterGeTrader
{
    private static final int UI_TIMEOUT_MS = 5_000;
    private static final int FIELD_ATTEMPTS = 3;
    private static final int PRICE_VARBIT = 4398;

    private SmartSmelterGeTrader()
    {
    }

    static boolean placeBuy(String itemName, int quantity, int percent)
    {
        if (itemName == null || itemName.isBlank() || quantity <= 0)
        {
            return false;
        }

        if (!ensureOverview())
        {
            return false;
        }

        GrandExchangeSlots slot = Rs2GrandExchange.getAvailableSlot();
        Widget slotWidget = getSlotWidget(slot);
        Widget buyButton = slotWidget == null ? null : slotWidget.getChild(0);
        if (slot == null || buyButton == null)
        {
            Microbot.status = "No free GE slot for " + itemName;
            return false;
        }

        Microbot.status = "GE buy: opening " + itemName;
        if (!Rs2Widget.clickWidget(buyButton)
                || !sleepUntil(Rs2GrandExchange::isOfferScreenOpen, UI_TIMEOUT_MS))
        {
            recoverToOverview();
            return false;
        }

        sleep(650, 900);
        if (!Rs2Widget.sleepUntilHasWidgetText(
                "Start typing the name of an item to search for it",
                162,
                52,
                false,
                UI_TIMEOUT_MS))
        {
            log.debug("GE item search prompt not ready for {}", itemName);
            recoverToOverview();
            return false;
        }

        Microbot.status = "GE buy: searching " + itemName;
        Rs2Keyboard.typeString(itemName);
        if (!sleepUntil(() -> Rs2GrandExchange.getSearchResultWidget(itemName, true) != null, UI_TIMEOUT_MS))
        {
            log.debug("No exact GE search result for {}", itemName);
            recoverToOverview();
            return false;
        }

        var result = Rs2GrandExchange.getSearchResultWidget(itemName, true);
        if (result == null)
        {
            recoverToOverview();
            return false;
        }

        Rs2Widget.clickWidgetFast(result.getLeft(), result.getRight(), 1);
        if (!waitForOfferControls())
        {
            recoverToOverview();
            return false;
        }

        return completeOffer(itemName, quantity, percent, "buy");
    }

    static boolean placeSell(String itemName, int quantity, int percent)
    {
        if (itemName == null || itemName.isBlank() || quantity <= 0)
        {
            return false;
        }

        if (!ensureOverview())
        {
            return false;
        }

        if (Rs2GrandExchange.getAvailableSlot() == null)
        {
            Microbot.status = "No free GE slot for " + itemName;
            return false;
        }

        if (!Rs2Inventory.hasItem(itemName, true))
        {
            Microbot.status = "Missing sell item: " + itemName;
            return false;
        }

        Microbot.status = "GE sell: selecting " + itemName;
        if (!Rs2Inventory.interact(itemName, "Offer", true)
                || !sleepUntil(Rs2GrandExchange::isOfferScreenOpen, UI_TIMEOUT_MS))
        {
            recoverToOverview();
            return false;
        }

        if (!waitForOfferControls())
        {
            recoverToOverview();
            return false;
        }

        return completeOffer(itemName, quantity, percent, "sell");
    }

    static boolean ensureOverview()
    {
        if (Rs2Bank.isOpen())
        {
            Rs2Bank.closeBank();
            sleepUntil(() -> !Rs2Bank.isOpen(), UI_TIMEOUT_MS);
            sleep(350, 550);
        }

        if (Rs2GrandExchange.isOfferScreenOpen())
        {
            recoverToOverview();
        }

        if (Rs2GrandExchange.isOpen())
        {
            sleep(450, 700);
            return true;
        }

        if (!Rs2GrandExchange.openExchange())
        {
            return false;
        }

        if (!sleepUntil(Rs2GrandExchange::isOpen, UI_TIMEOUT_MS))
        {
            return false;
        }

        sleep(650, 900);
        return true;
    }

    static void recoverToOverview()
    {
        if (Rs2GrandExchange.isOfferScreenOpen())
        {
            Rs2GrandExchange.backToOverview();
            sleepUntil(() -> !Rs2GrandExchange.isOfferScreenOpen(), UI_TIMEOUT_MS);
            sleep(400, 650);
        }
    }

    private static boolean waitForOfferControls()
    {
        boolean ready = sleepUntil(() ->
                Rs2GrandExchange.isOfferScreenOpen()
                        && getOfferChild(12) != null
                        && getOfferChild(7) != null,
                UI_TIMEOUT_MS);
        if (ready)
        {
            sleep(650, 900);
        }
        return ready;
    }

    private static boolean completeOffer(String itemName, int quantity, int percent, String action)
    {
        if (!sleepUntil(() -> Microbot.getVarbitValue(PRICE_VARBIT) > 0, UI_TIMEOUT_MS))
        {
            log.debug("GE {} price baseline did not become available for {}", action, itemName);
            recoverToOverview();
            return false;
        }

        int baseline = Math.max(1, Microbot.getVarbitValue(PRICE_VARBIT));
        int targetPrice = adjustedPrice(baseline, percent);

        Microbot.status = "GE " + action + ": setting price";
        if (!setOfferField("price", 12, targetPrice, () -> Microbot.getVarbitValue(PRICE_VARBIT)))
        {
            recoverToOverview();
            return false;
        }

        sleep(450, 700);
        Microbot.status = "GE " + action + ": setting quantity";
        if (!setOfferField(
                "quantity",
                7,
                quantity,
                () -> Microbot.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY)))
        {
            recoverToOverview();
            return false;
        }

        sleep(550, 800);
        Widget confirm = Rs2Widget.findWidget("Confirm", true);
        if (confirm == null)
        {
            log.debug("GE Confirm widget missing for {} {} x {} @ {}", action, itemName, quantity, targetPrice);
            recoverToOverview();
            return false;
        }

        // Final guard: never confirm unless both GE values still match exactly.
        if (Microbot.getVarbitValue(PRICE_VARBIT) != targetPrice
                || Microbot.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY) != quantity)
        {
            log.debug(
                    "GE {} values changed before confirm for {}: price {}/{}, quantity {}/{}",
                    action,
                    itemName,
                    Microbot.getVarbitValue(PRICE_VARBIT),
                    targetPrice,
                    Microbot.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY),
                    quantity);
            recoverToOverview();
            return false;
        }

        Microbot.status = "GE " + action + ": confirming " + itemName;
        if (!Rs2Widget.clickWidget(confirm))
        {
            recoverToOverview();
            return false;
        }

        return sleepUntil(() -> !Rs2GrandExchange.isOfferScreenOpen(), UI_TIMEOUT_MS);
    }

    private static boolean setOfferField(
            String fieldName,
            int childIndex,
            int target,
            IntSupplier observedValue)
    {
        if (target <= 0)
        {
            return false;
        }

        if (observedValue.getAsInt() == target)
        {
            return true;
        }

        for (int attempt = 1; attempt <= FIELD_ATTEMPTS; attempt++)
        {
            Widget xButton = getOfferChild(childIndex);
            if (xButton == null || !Rs2Widget.clickWidget(xButton))
            {
                sleep(300, 500);
                continue;
            }

            if (!sleepUntil(() -> Rs2Widget.getWidget(InterfaceID.Chatbox.MES_TEXT2) != null, UI_TIMEOUT_MS))
            {
                log.debug("GE {} amount prompt did not open on attempt {}", fieldName, attempt);
                continue;
            }

            sleep(600, 900);
            Rs2GrandExchange.setChatboxValue(target);
            sleep(500, 750);
            Rs2Keyboard.enter();

            if (sleepUntil(() -> observedValue.getAsInt() == target, 3_000))
            {
                sleep(450, 700);
                return true;
            }

            log.debug(
                    "GE {} entry attempt {}/{} failed: target={}, observed={}",
                    fieldName,
                    attempt,
                    FIELD_ATTEMPTS,
                    target,
                    observedValue.getAsInt());
            sleep(450, 700);
        }

        return false;
    }

    private static int adjustedPrice(int baseline, int percent)
    {
        double multiplier = (100.0 + percent) / 100.0;
        long adjusted = Math.round(baseline * multiplier);
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, adjusted));
    }

    private static Widget getSlotWidget(GrandExchangeSlots slot)
    {
        if (slot == null)
        {
            return null;
        }
        return Rs2Widget.getWidget(InterfaceID.GE_OFFERS, 7 + slot.ordinal());
    }

    private static Widget getOfferChild(int childIndex)
    {
        Widget offerContainer = Rs2Widget.getWidget(InterfaceID.GE_OFFERS, 26);
        return offerContainer == null ? null : offerContainer.getChild(childIndex);
    }
}
