package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.stronghold.inventory;

import java.util.Arrays;
import java.util.Comparator;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;

public final class InvFood
{
    public static final int REQUIRED_FOOD = 20;

    public boolean hasRequiredFood()
    {
        return Arrays.stream(Rs2Food.values())
                .anyMatch(food -> Rs2Inventory.itemQuantity(food.getId()) >= REQUIRED_FOOD);
    }

    public boolean prepare()
    {
        if (hasRequiredFood())
        {
            return true;
        }

        if (!Rs2Bank.isOpen())
        {
            return Rs2Bank.openBank() || Rs2Bank.walkToBankAndUseBank();
        }

        if (!Rs2Inventory.isEmpty())
        {
            Rs2Bank.depositAll();
            Script.sleepUntil(Rs2Inventory::isEmpty, 3_000);
            return false;
        }

        Rs2Food selectedFood = findBestAvailableFood();
        if (selectedFood == null)
        {
            return false;
        }

        if (!Rs2Bank.withdrawX(selectedFood.getId(), REQUIRED_FOOD))
        {
            return false;
        }

        return Script.sleepUntil(
                () -> Rs2Inventory.itemQuantity(selectedFood.getId()) >= REQUIRED_FOOD,
                3_000);
    }

    public Rs2Food findBestAvailableFood()
    {
        if (!Rs2Bank.isOpen())
        {
            return null;
        }

        return Arrays.stream(Rs2Food.values())
                .filter(food -> Rs2Bank.count(food.getId()) >= REQUIRED_FOOD)
                .max(Comparator.comparingInt(Rs2Food::getHeal))
                .orElse(null);
    }
}
