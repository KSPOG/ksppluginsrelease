package net.runelite.client.plugins.microbot.kspsmartsmelter.model;

public final class RouteQuote {
    private final SmeltRoute route;
    private final double inputCostPerCycle;
    private final double netRevenuePerCycle;
    private final double profitPerCycle;
    private final double roiPercent;
    private final double tripProfit;
    private final int tripCycles;
    private final int liquidity;

    public RouteQuote(
            SmeltRoute route,
            double inputCostPerCycle,
            double netRevenuePerCycle,
            double profitPerCycle,
            double roiPercent,
            double tripProfit,
            int tripCycles,
            int liquidity
    ) {
        this.route = route;
        this.inputCostPerCycle = inputCostPerCycle;
        this.netRevenuePerCycle = netRevenuePerCycle;
        this.profitPerCycle = profitPerCycle;
        this.roiPercent = roiPercent;
        this.tripProfit = tripProfit;
        this.tripCycles = tripCycles;
        this.liquidity = liquidity;
    }

    public SmeltRoute getRoute() {
        return route;
    }

    public double getInputCostPerCycle() {
        return inputCostPerCycle;
    }

    public double getNetRevenuePerCycle() {
        return netRevenuePerCycle;
    }

    public double getProfitPerCycle() {
        return profitPerCycle;
    }

    public double getRoiPercent() {
        return roiPercent;
    }

    public double getTripProfit() {
        return tripProfit;
    }

    public int getTripCycles() {
        return tripCycles;
    }

    public int getLiquidity() {
        return liquidity;
    }
}
