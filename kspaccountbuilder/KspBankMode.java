package net.runelite.client.plugins.microbot.kspaccountbuilder;

import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;

public final class KspBankMode
{
    private KspBankMode()
    {
    }

    public static boolean ensureWithdrawAsItem()
    {
        if (!Rs2Bank.isOpen())
        {
            return false;
        }

        if (Rs2Bank.hasWithdrawAsNote())
        {
            return Rs2Bank.setWithdrawAsItem() && Rs2Bank.hasWithdrawAsItem();
        }

        return Rs2Bank.hasWithdrawAsItem();
    }
}
