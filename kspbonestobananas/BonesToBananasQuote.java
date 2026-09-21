package net.runelite.client.plugins.microbot.kspbonestobananas;

public final class BonesToBananasQuote
{
    private final BananaBoneType bone;
    private final boolean valid;
    private final String reason;
    private final int batchSize, boneBuyPrice, bananaSellPrice, bananaTax, natureBuyPrice, waterBuyPrice, earthBuyPrice;
    private final int runeCostPerCast, inputCostPerCast, profitPerCast;
    private final boolean freeWater, freeEarth;
    private final double roiPercent;
    private final long projectedGpHour;

    private BonesToBananasQuote(BananaBoneType bone, boolean valid, String reason, int batchSize,
                                int boneBuyPrice, int bananaSellPrice, int bananaTax,
                                int natureBuyPrice, int waterBuyPrice, int earthBuyPrice,
                                int runeCostPerCast, int inputCostPerCast, int profitPerCast,
                                boolean freeWater, boolean freeEarth, double roiPercent, long projectedGpHour)
    {
        this.bone = bone;
        this.valid = valid;
        this.reason = reason;
        this.batchSize = batchSize;
        this.boneBuyPrice = boneBuyPrice;
        this.bananaSellPrice = bananaSellPrice;
        this.bananaTax = bananaTax;
        this.natureBuyPrice = natureBuyPrice;
        this.waterBuyPrice = waterBuyPrice;
        this.earthBuyPrice = earthBuyPrice;
        this.runeCostPerCast = runeCostPerCast;
        this.inputCostPerCast = inputCostPerCast;
        this.profitPerCast = profitPerCast;
        this.freeWater = freeWater;
        this.freeEarth = freeEarth;
        this.roiPercent = roiPercent;
        this.projectedGpHour = projectedGpHour;
    }

    static BonesToBananasQuote invalid(BananaBoneType bone, String reason, int batchSize, boolean freeWater, boolean freeEarth)
    {
        return new BonesToBananasQuote(bone, false, reason, batchSize, -1, -1, 0,
                -1, -1, -1, 0, 0, 0, freeWater, freeEarth, 0D, 0L);
    }

    static BonesToBananasQuote valid(BananaBoneType bone, int batchSize, int boneBuyPrice,
                                     int bananaSellPrice, int bananaTax, int natureBuyPrice,
                                     int waterBuyPrice, int earthBuyPrice, int runeCostPerCast,
                                     int inputCostPerCast, int profitPerCast, boolean freeWater,
                                     boolean freeEarth, double roiPercent, long projectedGpHour)
    {
        return new BonesToBananasQuote(bone, true, "", batchSize, boneBuyPrice, bananaSellPrice,
                bananaTax, natureBuyPrice, waterBuyPrice, earthBuyPrice, runeCostPerCast,
                inputCostPerCast, profitPerCast, freeWater, freeEarth, roiPercent, projectedGpHour);
    }

    public boolean meets(KspBonesToBananasConfig config)
    {
        return valid && profitPerCast > 0 && profitPerCast >= config.minProfitPerCast()
                && roiPercent >= config.minRoiPercent();
    }

    public boolean meetsForQuantity(KspBonesToBananasConfig config, int bones)
    {
        int profit = profitForBones(bones);
        int cost = inputCostForBones(bones);
        double roi = cost <= 0 ? 0D : profit * 100D / cost;
        return valid && bones > 0 && profit > 0 && profit >= config.minProfitPerCast()
                && roi >= config.minRoiPercent();
    }

    public int inputCostForBones(int bones)
    {
        long value = (long) Math.max(0, bones) * boneBuyPrice + runeCostPerCast;
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    public int profitForBones(int bones)
    {
        long outputNet = (long) Math.max(0, bones) * Math.max(0, bananaSellPrice - bananaTax);
        long value = outputNet - (long) Math.max(0, bones) * boneBuyPrice - runeCostPerCast;
        if (value > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        if (value < Integer.MIN_VALUE) return Integer.MIN_VALUE;
        return (int) value;
    }

    public double getAverageInputCostPerBone()
    {
        return batchSize <= 0 ? 0D : inputCostPerCast / (double) batchSize;
    }

    public double getProfitPerBone()
    {
        return batchSize <= 0 ? 0D : profitPerCast / (double) batchSize;
    }

    public BananaBoneType getBone() { return bone; }
    public boolean isValid() { return valid; }
    public String getReason() { return reason; }
    public int getBatchSize() { return batchSize; }
    public int getBoneBuyPrice() { return boneBuyPrice; }
    public int getBananaSellPrice() { return bananaSellPrice; }
    public int getBananaTax() { return bananaTax; }
    public int getNatureBuyPrice() { return natureBuyPrice; }
    public int getWaterBuyPrice() { return waterBuyPrice; }
    public int getEarthBuyPrice() { return earthBuyPrice; }
    public int getRuneCostPerCast() { return runeCostPerCast; }
    public int getInputCostPerCast() { return inputCostPerCast; }
    public int getProfitPerCast() { return profitPerCast; }
    public boolean hasFreeWater() { return freeWater; }
    public boolean hasFreeEarth() { return freeEarth; }
    public double getRoiPercent() { return roiPercent; }
    public long getProjectedGpHour() { return projectedGpHour; }
}
