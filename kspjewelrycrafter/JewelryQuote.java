package net.runelite.client.plugins.microbot.kspjewelrycrafter;

public final class JewelryQuote
{
    private final JewelryRecipe recipe;
    private final boolean valid;
    private final String reason;
    private final int barBuyPrice;
    private final int gemBuyPrice;
    private final int outputSellPrice;
    private final int inputCost;
    private final int tax;
    private final int profit;
    private final double roi;

    private JewelryQuote(JewelryRecipe recipe, boolean valid, String reason,
                         int barBuyPrice, int gemBuyPrice, int outputSellPrice,
                         int inputCost, int tax, int profit, double roi)
    {
        this.recipe = recipe;
        this.valid = valid;
        this.reason = reason;
        this.barBuyPrice = barBuyPrice;
        this.gemBuyPrice = gemBuyPrice;
        this.outputSellPrice = outputSellPrice;
        this.inputCost = inputCost;
        this.tax = tax;
        this.profit = profit;
        this.roi = roi;
    }

    public static JewelryQuote invalid(JewelryRecipe recipe, String reason)
    {
        return new JewelryQuote(recipe, false, reason, 0, 0, 0, 0, 0, 0, 0.0);
    }

    public static JewelryQuote valid(JewelryRecipe recipe, int barBuyPrice, int gemBuyPrice,
                                     int outputSellPrice, int inputCost, int tax, int profit, double roi)
    {
        return new JewelryQuote(recipe, true, "", barBuyPrice, gemBuyPrice,
            outputSellPrice, inputCost, tax, profit, roi);
    }

    public boolean meets(KspJewelryCrafterConfig config)
    {
        return valid && profit >= Math.max(1, config.minimumProfitPerItem())
            && roi >= Math.max(0.0, config.minimumRoiPercent());
    }

    public JewelryRecipe getRecipe() { return recipe; }
    public boolean isValid() { return valid; }
    public String getReason() { return reason; }
    public int getBarBuyPrice() { return barBuyPrice; }
    public int getGemBuyPrice() { return gemBuyPrice; }
    public int getOutputSellPrice() { return outputSellPrice; }
    public int getInputCost() { return inputCost; }
    public int getTax() { return tax; }
    public int getProfit() { return profit; }
    public double getRoi() { return roi; }
}
