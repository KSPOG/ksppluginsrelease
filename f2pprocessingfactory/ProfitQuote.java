package net.runelite.client.plugins.microbot.f2pprocessingfactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ProfitQuote
{
    private final FactoryRecipe recipe;
    private final boolean valid;
    private final String error;
    private final Map<String, Integer> inputPrices;
    private final int outputPrice;
    private final int inputCostPerUnit;
    private final int taxPerUnit;
    private final int profitPerUnit;
    private final double roiPercent;
    private final int estimatedProfitPerHour;

    private ProfitQuote(
        FactoryRecipe recipe,
        boolean valid,
        String error,
        Map<String, Integer> inputPrices,
        int outputPrice,
        int inputCostPerUnit,
        int taxPerUnit,
        int profitPerUnit,
        double roiPercent,
        int estimatedProfitPerHour)
    {
        this.recipe = recipe;
        this.valid = valid;
        this.error = error;
        this.inputPrices = Collections.unmodifiableMap(new LinkedHashMap<>(inputPrices));
        this.outputPrice = outputPrice;
        this.inputCostPerUnit = inputCostPerUnit;
        this.taxPerUnit = taxPerUnit;
        this.profitPerUnit = profitPerUnit;
        this.roiPercent = roiPercent;
        this.estimatedProfitPerHour = estimatedProfitPerHour;
    }

    public static ProfitQuote valid(
        FactoryRecipe recipe,
        Map<String, Integer> inputPrices,
        int outputPrice,
        int inputCostPerUnit,
        int taxPerUnit,
        int profitPerUnit,
        double roiPercent)
    {
        long hourly = (long) profitPerUnit * recipe.getEstimatedUnitsPerHour();
        int estimatedProfitPerHour = hourly > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) hourly;
        return new ProfitQuote(
            recipe,
            true,
            "",
            inputPrices,
            outputPrice,
            inputCostPerUnit,
            taxPerUnit,
            profitPerUnit,
            roiPercent,
            estimatedProfitPerHour
        );
    }

    public static ProfitQuote invalid(FactoryRecipe recipe, String error)
    {
        return new ProfitQuote(recipe, false, error, Collections.emptyMap(), 0, 0, 0, 0, 0.0, 0);
    }

    public FactoryRecipe getRecipe()
    {
        return recipe;
    }

    public boolean isValid()
    {
        return valid;
    }

    public String getError()
    {
        return error;
    }

    public Map<String, Integer> getInputPrices()
    {
        return inputPrices;
    }

    public int getOutputPrice()
    {
        return outputPrice;
    }

    public int getInputCostPerUnit()
    {
        return inputCostPerUnit;
    }

    public int getTaxPerUnit()
    {
        return taxPerUnit;
    }

    public int getProfitPerUnit()
    {
        return profitPerUnit;
    }

    public double getRoiPercent()
    {
        return roiPercent;
    }

    public int getEstimatedProfitPerHour()
    {
        return estimatedProfitPerHour;
    }

    public boolean meets(F2PProcessingFactoryConfig config)
    {
        return valid
            && profitPerUnit > 0
            && profitPerUnit >= config.minimumProfitPerUnit()
            && roiPercent >= config.minimumRoiPercent();
    }
}
