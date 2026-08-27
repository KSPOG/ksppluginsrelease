package net.runelite.client.plugins.microbot.kspsmartsuperheat;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeSlots;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
final class SmartGeTrader
{
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

        if (Rs2GrandExchange.getAvailableSlotsCount() <= 0)
        {
            return TradeResult.failed("No free Grand Exchange slot");
        }

        Set<GrandExchangeSlots> freeBefore = captureAvailableSlots();
        if (!Rs2GrandExchange.buyItem(itemName, price, quantity))
        {
            return TradeResult.failed("GE buy placement failed");
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

    static boolean ensureExchangeOpen()
    {
        if (Rs2Bank.isOpen())
        {
            Rs2Bank.closeBank();
            sleep(150, 300);
        }

        if (Rs2GrandExchange.isOpen())
        {
            return true;
        }

        if (Rs2GrandExchange.openExchange())
        {
            return true;
        }

        Rs2GrandExchange.walkToGrandExchange();
        sleepUntil(() -> !Rs2Player.isMoving(), 20_000);
        return Rs2GrandExchange.openExchange();
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

        // Fallback only if the UI changed too quickly for the free-slot delta to be
        // observed. This keeps compatibility with older clients while the normal
        // path remains exact-slot bound.
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
