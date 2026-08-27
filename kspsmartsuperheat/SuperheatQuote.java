package net.runelite.client.plugins.microbot.kspsmartsuperheat;

public final class SuperheatQuote
{
    private final SuperheatRecipe recipe;
    private final boolean valid;
    private final String reason;
    private final boolean freeFireRunes;
    private final int primaryBuyPrice;
    private final int secondaryBuyPrice;
    private final int coalBuyPrice;
    private final int natureBuyPrice;
    private final int fireBuyPrice;
    private final int outputSellPrice;
    private final int inputCostPerBar;
    private final int taxPerBar;
    private final int profitPerBar;
    private final double roiPercent;
    private final int batchSize;
    private final long projectedGpHour;
    private final long quotedAt;

    private SuperheatQuote(
        SuperheatRecipe recipe,
        boolean valid,
        String reason,
        boolean freeFireRunes,
        int primaryBuyPrice,
        int secondaryBuyPrice,
        int coalBuyPrice,
        int natureBuyPrice,
        int fireBuyPrice,
        int outputSellPrice,
        int inputCostPerBar,
        int taxPerBar,
        int profitPerBar,
        double roiPercent,
        int batchSize,
        long projectedGpHour,
        long quotedAt)
    {
        this.recipe = recipe;
        this.valid = valid;
        this.reason = reason;
        this.freeFireRunes = freeFireRunes;
        this.primaryBuyPrice = primaryBuyPrice;
        this.secondaryBuyPrice = secondaryBuyPrice;
        this.coalBuyPrice = coalBuyPrice;
        this.natureBuyPrice = natureBuyPrice;
        this.fireBuyPrice = fireBuyPrice;
        this.outputSellPrice = outputSellPrice;
        this.inputCostPerBar = inputCostPerBar;
        this.taxPerBar = taxPerBar;
        this.profitPerBar = profitPerBar;
        this.roiPercent = roiPercent;
        this.batchSize = batchSize;
        this.projectedGpHour = projectedGpHour;
        this.quotedAt = quotedAt;
    }

    public static SuperheatQuote invalid(SuperheatRecipe recipe, String reason, boolean freeFireRunes)
    {
        return new SuperheatQuote(
            recipe, false, reason, freeFireRunes,
            -1, -1, -1, -1, -1, -1,
            0, 0, 0, 0.0, 0, 0L, System.currentTimeMillis()
        );
    }

    public static SuperheatQuote valid(
        SuperheatRecipe recipe,
        boolean freeFireRunes,
        int primaryBuyPrice,
        int secondaryBuyPrice,
        int coalBuyPrice,
        int natureBuyPrice,
        int fireBuyPrice,
        int outputSellPrice,
        int inputCostPerBar,
        int taxPerBar,
        int profitPerBar,
        double roiPercent,
        int batchSize,
        long projectedGpHour)
    {
        return new SuperheatQuote(
            recipe, true, "", freeFireRunes,
            primaryBuyPrice, secondaryBuyPrice, coalBuyPrice,
            natureBuyPrice, fireBuyPrice, outputSellPrice,
            inputCostPerBar, taxPerBar, profitPerBar, roiPercent,
            batchSize, projectedGpHour, System.currentTimeMillis()
        );
    }

    public boolean meets(KspSmartSuperheatConfig config)
    {
        return valid
            && profitPerBar >= config.minProfitPerBar()
            && roiPercent >= config.minRoiPercent()
            && projectedGpHour >= config.minProjectedGpHour();
    }

    public SuperheatRecipe getRecipe() { return recipe; }
    public boolean isValid() { return valid; }
    public String getReason() { return reason; }
    public boolean hasFreeFireRunes() { return freeFireRunes; }
    public int getPrimaryBuyPrice() { return primaryBuyPrice; }
    public int getSecondaryBuyPrice() { return secondaryBuyPrice; }
    public int getCoalBuyPrice() { return coalBuyPrice; }
    public int getNatureBuyPrice() { return natureBuyPrice; }
    public int getFireBuyPrice() { return fireBuyPrice; }
    public int getOutputSellPrice() { return outputSellPrice; }
    public int getInputCostPerBar() { return inputCostPerBar; }
    public int getTaxPerBar() { return taxPerBar; }
    public int getProfitPerBar() { return profitPerBar; }
    public double getRoiPercent() { return roiPercent; }
    public int getBatchSize() { return batchSize; }
    public long getProjectedGpHour() { return projectedGpHour; }
    public long getQuotedAt() { return quotedAt; }
}
