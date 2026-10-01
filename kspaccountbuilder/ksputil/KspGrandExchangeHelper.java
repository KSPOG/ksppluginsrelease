package net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

public final class KspGrandExchangeHelper
{
    private static final String GRAND_EXCHANGE_CLERK = "Grand Exchange Clerk";
    private static final int CLERK_REACHABLE_DISTANCE = 15;
    private static final long EXCHANGE_DISPATCH_TIMEOUT_MS = 1_500L;
    private static volatile long pendingExchangeActionAtMs;

    private KspGrandExchangeHelper() {}

    public static boolean closeBankBeforeExchange()
    {
        if (!Rs2Bank.isOpen())
        {
            return false;
        }

        Rs2Bank.closeBank();
        return true;
    }

    public static boolean openExchangeDirectly()
    {
        if (Rs2GrandExchange.isOpen())
        {
            pendingExchangeActionAtMs = 0L;
            return true;
        }
        if (exchangeActionPending()) return false;

        boolean dispatched = Rs2GrandExchange.openExchange();
        if (dispatched) pendingExchangeActionAtMs = System.currentTimeMillis();
        return dispatched;
    }

    public static boolean interactClerk()
    {
        if (Rs2GrandExchange.isOpen())
        {
            pendingExchangeActionAtMs = 0L;
            return true;
        }
        if (exchangeActionPending()
                || Rs2Player.isMoving()
                || Rs2Player.isAnimating()
                || Rs2Player.isInteracting())
        {
            return false;
        }

        Rs2NpcModel clerk = findClerk();
        if (clerk == null)
        {
            return false;
        }

        boolean dispatched = clerk.click("Exchange") || clerk.click("Trade");
        if (dispatched) pendingExchangeActionAtMs = System.currentTimeMillis();
        return dispatched;
    }

    private static boolean exchangeActionPending()
    {
        if (pendingExchangeActionAtMs == 0L) return false;
        if (Rs2GrandExchange.isOpen())
        {
            pendingExchangeActionAtMs = 0L;
            return false;
        }
        if (System.currentTimeMillis() - pendingExchangeActionAtMs < EXCHANGE_DISPATCH_TIMEOUT_MS) return true;
        pendingExchangeActionAtMs = 0L;
        return false;
    }

    public static Rs2NpcModel findClerk()
    {
        return Microbot.getClientThread().invoke(() -> Microbot.getRs2NpcCache().query()
                .fromWorldView()
                .withName(GRAND_EXCHANGE_CLERK)
                .nearestReachable(CLERK_REACHABLE_DISTANCE));
    }
}
