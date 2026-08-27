package net.runelite.client.plugins.microbot.kspsmartsuperheat;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeSlots;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntSupplier;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
final class SmartGeTrader
{
    private static final int UI_TIMEOUT_MS = 5_000;
    private static final int FIELD_ATTEMPTS = 3;

    private SmartGeTrader()
    {
    }

    static TradeResult buyToBank(String itemName, int quantity, int price, int timeoutSeconds)
    {
        if (quantity <= 0 || price <= 0)
        {
            return TradeResult.failed("Invalid buy request");
        }

        if (!ensureExchangeOpen())
        {
            return TradeResult.failed("Could not open Grand Exchange");
        }

        GrandExchangeSlots targetSlot = Rs2GrandExchange.getAvailableSlot();
        if (targetSlot == null)
        {
            return TradeResult.failed("No free Grand Exchange slot");
        }

        Set<GrandExchangeSlots> freeBefore = captureAvailableSlots();
        if (!placeBuyOfferSlow(itemName, price, quantity, targetSlot))
        {
            recoverToOverview();
            return TradeResult.failed("GE buy entry failed - retrying");
        }

        GrandExchangeSlots slot = waitForNewOccupiedSlot(freeBefore, itemName, false);
        if (slot == null)
        {
            return TradeResult.failed("Could not resolve buy offer slot");
        }

        boolean completed = sleepUntil(() -> Rs2GrandExchange.hasBoughtOffer(slot), timeoutSeconds * 1000);
        int filled = safeBought(slot);

        if (completed)
        {
            if (filled <= 0) filled = quantity;
            Rs2GrandExchange.collectOffer(slot, true);
            return TradeResult.completed(filled);
        }

        Rs2GrandExchange.cancelSpecificOffers(List.of(slot), true);
        return TradeResult.partial(filled, "Buy timed out; partial fill collected");
    }

    static TradeResult sellFromInventory(String itemName, int quantity, int price, int timeoutSeconds)
    {
        if (quantity <= 0 || price <= 0)
        {
            return TradeResult.failed("Invalid sell request");
        }

        if (!ensureExchangeOpen())
        {
            return TradeResult.failed("Could not open Grand Exchange");
        }

        if (Rs2GrandExchange.getAvailableSlotsCount() <= 0)
        {
            return TradeResult.failed("No free Grand Exchange slot");
        }

        // Give the GE inventory/offer widgets time to settle before Microbot starts
        // its sell sequence. This is particularly important after withdrawing notes.
        sleep(700, 1_000);

        Set<GrandExchangeSlots> freeBefore = captureAvailableSlots();
        if (!Rs2GrandExchange.sellItem(itemName, quantity, price))
        {
            return TradeResult.failed("GE sell placement failed");
        }

        GrandExchangeSlots slot = waitForNewOccupiedSlot(freeBefore, itemName, true);
        if (slot == null)
        {
            return TradeResult.failed("Could not resolve sell offer slot");
        }

        boolean completed = sleepUntil(() -> Rs2GrandExchange.hasSoldOffer(slot), timeoutSeconds * 1000);
        int filled = safeSold(slot);

        if (completed)
        {
            if (filled <= 0) filled = quantity;
            Rs2GrandExchange.collectOffer(slot, true);
            return TradeResult.completed(filled);
        }

        Rs2GrandExchange.cancelSpecificOffers(List.of(slot), true);
        return TradeResult.partial(filled, "Sell timed out; partial fill collected");
    }

    private static boolean placeBuyOfferSlow(String itemName, int price, int quantity, GrandExchangeSlots slot)
    {
        Widget slotWidget = getSlotWidget(slot);
        Widget buyButton = slotWidget == null ? null : slotWidget.getChild(0);
        if (buyButton == null || !Rs2Widget.clickWidget(buyButton))
        {
            log.debug("Unable to click GE buy button for slot {}", slot);
            return false;
        }

        if (!sleepUntil(Rs2GrandExchange::isOfferScreenOpen, UI_TIMEOUT_MS))
        {
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
            log.debug("GE item-search prompt did not become ready for {}", itemName);
            return false;
        }

        Rs2Keyboard.typeString(itemName);
        if (!sleepUntil(() -> Rs2GrandExchange.getSearchResultWidget(itemName, true) != null, UI_TIMEOUT_MS))
        {
            log.debug("No exact GE search result for {}", itemName);
            return false;
        }

        var itemResult = Rs2GrandExchange.getSearchResultWidget(itemName, true);
        if (itemResult == null)
        {
            return false;
        }

        // Microbot's search-result widget needs its result index as param0. Keep that
        // exact interaction, then deliberately wait for the actual offer controls.
        Rs2Widget.clickWidgetFast(itemResult.getLeft(), itemResult.getRight(), 1);
        if (!sleepUntil(() -> getOfferChild(12) != null && getOfferChild(7) != null, UI_TIMEOUT_MS))
        {
            return false;
        }
        sleep(750, 1_050);

        if (!setOfferField("price", 12, price, () -> Microbot.getVarbitValue(4398)))
        {
            return false;
        }

        sleep(500, 750);

        if (!setOfferField(
            "quantity",
            7,
            quantity,
            () -> Microbot.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY)))
        {
            return false;
        }

        sleep(650, 900);

        Widget confirm = Rs2Widget.findWidget("Confirm", true);
        if (confirm == null || !Rs2Widget.clickWidget(confirm))
        {
            log.debug("GE Confirm widget unavailable after setting {} x {} @ {}", quantity, itemName, price);
            return false;
        }

        return sleepUntil(() -> !Rs2GrandExchange.isOfferScreenOpen(), UI_TIMEOUT_MS);
    }

    private static boolean setOfferField(
        String fieldName,
        int offerChild,
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
            Widget xButton = getOfferChild(offerChild);
            if (xButton == null)
            {
                sleep(300, 500);
                continue;
            }

            if (!Rs2Widget.clickWidget(xButton))
            {
                sleep(300, 500);
                continue;
            }

            // Do not type until the GE chatbox input has actually appeared.
            if (!sleepUntil(() -> Rs2Widget.getWidget(InterfaceID.Chatbox.MES_TEXT2) != null, UI_TIMEOUT_MS))
            {
                continue;
            }

            sleep(650, 950);
            Rs2GrandExchange.setChatboxValue(target);
            sleep(550, 800);
            Rs2Keyboard.enter();

            if (sleepUntil(() -> observedValue.getAsInt() == target, 3_000))
            {
                sleep(500, 750);
                return true;
            }

            log.debug(
                "GE {} entry attempt {}/{} did not register: target={}, observed={}",
                fieldName,
                attempt,
                FIELD_ATTEMPTS,
                target,
                observedValue.getAsInt()
            );
            sleep(500, 750);
        }

        return false;
    }

    static boolean ensureExchangeOpen()
    {
        if (Rs2Bank.isOpen())
        {
            Rs2Bank.closeBank();
            sleep(500, 750);
        }

        if (Rs2GrandExchange.isOfferScreenOpen())
        {
            recoverToOverview();
        }

        if (Rs2GrandExchange.isOpen())
        {
            sleep(500, 750);
            return true;
        }

        if (Rs2GrandExchange.openExchange())
        {
            sleepUntil(Rs2GrandExchange::isOpen, UI_TIMEOUT_MS);
            sleep(700, 1_000);
            return Rs2GrandExchange.isOpen();
        }

        Rs2GrandExchange.walkToGrandExchange();
        sleepUntil(() -> !Rs2Player.isMoving(), 20_000);
        if (!Rs2GrandExchange.openExchange())
        {
            return false;
        }

        sleepUntil(Rs2GrandExchange::isOpen, UI_TIMEOUT_MS);
        sleep(700, 1_000);
        return Rs2GrandExchange.isOpen();
    }

    private static void recoverToOverview()
    {
        if (Rs2GrandExchange.isOfferScreenOpen())
        {
            Rs2GrandExchange.backToOverview();
            sleepUntil(() -> !Rs2GrandExchange.isOfferScreenOpen(), UI_TIMEOUT_MS);
            sleep(500, 750);
        }
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

    private static Set<GrandExchangeSlots> captureAvailableSlots()
    {
        GrandExchangeSlots[] slots = Rs2GrandExchange.getAvailableSlots();
        return slots == null
            ? new HashSet<>()
            : new HashSet<>(Arrays.asList(slots));
    }

    private static GrandExchangeSlots waitForNewOccupiedSlot(
        Set<GrandExchangeSlots> freeBefore,
        String itemName,
        boolean selling)
    {
        final GrandExchangeSlots[] slot = new GrandExchangeSlots[1];

        sleepUntil(() ->
        {
            GrandExchangeSlots[] freeNowArray = Rs2GrandExchange.getAvailableSlots();
            Set<GrandExchangeSlots> freeNow = freeNowArray == null
                ? new HashSet<>()
                : new HashSet<>(Arrays.asList(freeNowArray));

            for (GrandExchangeSlots candidate : freeBefore)
            {
                if (!freeNow.contains(candidate))
                {
                    slot[0] = candidate;
                    return true;
                }
            }

            return false;
        }, 3_000);

        if (slot[0] == null)
        {
            slot[0] = Rs2GrandExchange.findSlotForItem(itemName, selling);
        }

        return slot[0];
    }

    private static int safeBought(GrandExchangeSlots slot)
    {
        try
        {
            return Math.max(0, Rs2GrandExchange.getItemsBoughtFromOffer(slot));
        }
        catch (RuntimeException ex)
        {
            log.debug("Could not read bought amount: {}", ex.getMessage());
            return 0;
        }
    }

    private static int safeSold(GrandExchangeSlots slot)
    {
        try
        {
            return Math.max(0, Rs2GrandExchange.getItemsSoldFromOffer(slot));
        }
        catch (RuntimeException ex)
        {
            log.debug("Could not read sold amount: {}", ex.getMessage());
            return 0;
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
