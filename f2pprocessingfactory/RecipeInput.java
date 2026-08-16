package net.runelite.client.plugins.microbot.f2pprocessingfactory;

import java.util.Objects;

public final class RecipeInput
{
    private final String itemName;
    private final int requiredNumerator;
    private final int requiredDenominator;
    private final boolean consumed;
    private final boolean stackable;
    private final boolean progressTracked;

    private RecipeInput(
        String itemName,
        int requiredNumerator,
        int requiredDenominator,
        boolean consumed,
        boolean stackable,
        boolean progressTracked)
    {
        this.itemName = Objects.requireNonNull(itemName, "itemName");
        this.requiredNumerator = requiredNumerator;
        this.requiredDenominator = requiredDenominator;
        this.consumed = consumed;
        this.stackable = stackable;
        this.progressTracked = progressTracked;
    }

    public static RecipeInput consumed(String itemName, int unitsPerOutput)
    {
        if (unitsPerOutput <= 0)
        {
            throw new IllegalArgumentException("Consumed input quantity must be positive");
        }
        return new RecipeInput(itemName, unitsPerOutput, 1, true, false, true);
    }

    /**
     * Stackable support material consumed at a fractional rate. For example,
     * Thread is consumed once per five leather items, so use (1, 5).
     */
    public static RecipeInput consumedStackablePerOutputs(
        String itemName,
        int inputUnits,
        int outputUnits)
    {
        if (inputUnits <= 0 || outputUnits <= 0)
        {
            throw new IllegalArgumentException("Fractional input quantities must be positive");
        }
        return new RecipeInput(itemName, inputUnits, outputUnits, true, true, false);
    }

    public static RecipeInput tool(String itemName)
    {
        return new RecipeInput(itemName, 0, 1, false, false, false);
    }

    public String getItemName()
    {
        return itemName;
    }

    /**
     * Retained for compatibility with older recipe code. For ordinary consumed
     * inputs this is the exact units-per-output quantity. Fractional support
     * inputs should use requiredForUnits()/getPossibleOutputUnits() instead.
     */
    public int getUnitsPerOutput()
    {
        return requiredNumerator;
    }

    public int getRequiredNumerator()
    {
        return requiredNumerator;
    }

    public int getRequiredDenominator()
    {
        return requiredDenominator;
    }

    public boolean isConsumed()
    {
        return consumed;
    }

    public boolean isStackable()
    {
        return stackable;
    }

    public boolean isProgressTracked()
    {
        return progressTracked;
    }

    public int requiredForUnits(int outputUnits)
    {
        if (!consumed)
        {
            return outputUnits > 0 ? 1 : 0;
        }
        if (outputUnits <= 0)
        {
            return 0;
        }

        long numerator = (long) outputUnits * requiredNumerator;
        long required = (numerator + requiredDenominator - 1L) / requiredDenominator;
        return required > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) required;
    }

    public int getPossibleOutputUnits(int availableInputUnits)
    {
        if (!consumed)
        {
            return availableInputUnits > 0 ? Integer.MAX_VALUE : 0;
        }
        if (availableInputUnits <= 0)
        {
            return 0;
        }

        long possible = ((long) availableInputUnits * requiredDenominator) / requiredNumerator;
        return possible > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) possible;
    }

    public int getInventorySlotsForUnits(int outputUnits)
    {
        if (outputUnits <= 0)
        {
            return 0;
        }
        if (!consumed)
        {
            return 1;
        }

        int required = requiredForUnits(outputUnits);
        return stackable ? (required > 0 ? 1 : 0) : required;
    }

    public int getEstimatedCostPerOutput(int itemPrice)
    {
        if (!consumed || itemPrice <= 0)
        {
            return 0;
        }

        long numerator = (long) itemPrice * requiredNumerator;
        long cost = (numerator + requiredDenominator - 1L) / requiredDenominator;
        return cost > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) cost;
    }
}
