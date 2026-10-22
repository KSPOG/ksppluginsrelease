package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.stronghold.inventory;

import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;

public final class InvFood
{
    public static final int REQUIRED_FOOD = 20;

    public boolean hasRequiredFood()
    {
        for (Rs2Food food : Rs2Food.values())
        {
            if (Rs2Inventory.itemQuantity(food.getId()) >= REQUIRED_FOOD) return true;
        }
        return false;
    }

    public boolean prepare()
    {
        if (hasRequiredFood())
        {
            if (Rs2Bank.isOpen())
            {
                Rs2Bank.closeBank();
                return false;
            }
            return true;
        }

        if (!Rs2Bank.isOpen())
        {
            Rs2Bank.openBank();
            if (!Rs2Bank.isOpen()) Rs2Bank.walkToBankAndUseBank();
            return false;
        }

        if (!Rs2Inventory.isEmpty())
        {
            Rs2Bank.depositAll();
            return false;
        }

        Rs2Food food = findBestAvailableFood();
        if (food == null) return false;

        Rs2Bank.withdrawX(food.getId(), REQUIRED_FOOD);
        return false;
    }

    public Rs2Food findBestAvailableFood()
    {
        if (!Rs2Bank.isOpen()) return null;

        Rs2Food best = null;
        for (Rs2Food food : Rs2Food.values())
        {
            if (Rs2Bank.count(food.getId()) < REQUIRED_FOOD) continue;
            if (best == null || food.getHeal() > best.getHeal()) best = food;
        }
        return best;
    }
}
