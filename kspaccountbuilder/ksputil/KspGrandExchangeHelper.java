package net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;

public final class KspGrandExchangeHelper
{
    private static final String GRAND_EXCHANGE_CLERK = "Grand Exchange Clerk";
    private static final int CLERK_REACHABLE_DISTANCE = 15;

    private KspGrandExchangeHelper()
    {
    }

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
        return Rs2GrandExchange.isOpen() || Rs2GrandExchange.openExchange();
    }

    public static boolean interactClerk()
    {
        Rs2NpcModel clerk = findClerk();
        if (clerk == null)
        {
            return false;
        }

        return clerk.click("Exchange") || clerk.click("Trade");
    }

    public static Rs2NpcModel findClerk()
    {
        return Microbot.getClientThread().invoke(() -> Microbot.getRs2NpcCache().query()
                .fromWorldView()
                .withName(GRAND_EXCHANGE_CLERK)
                .nearestReachable(CLERK_REACHABLE_DISTANCE));
    }
}
