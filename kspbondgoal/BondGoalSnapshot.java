package net.runelite.client.plugins.microbot.kspbondgoal;

import java.util.Collections;
import java.util.List;

final class BondGoalSnapshot
{
    private final long bondPrice;
    private final long extraCoins;
    private final long targetCoins;
    private final long currentCoins;
    private final long remainingCoins;
    private final boolean bankKnown;
    private final double progressPercent;
    private final List<BondActivityAdvisor.ActivityEstimate> activities;

    BondGoalSnapshot(
        long bondPrice,
        long extraCoins,
        long targetCoins,
        long currentCoins,
        long remainingCoins,
        boolean bankKnown,
        double progressPercent,
        List<BondActivityAdvisor.ActivityEstimate> activities)
    {
        this.bondPrice = bondPrice;
        this.extraCoins = extraCoins;
        this.targetCoins = targetCoins;
        this.currentCoins = currentCoins;
        this.remainingCoins = remainingCoins;
        this.bankKnown = bankKnown;
        this.progressPercent = progressPercent;
        this.activities = activities == null ? Collections.emptyList() : activities;
    }

    static BondGoalSnapshot empty()
    {
        return new BondGoalSnapshot(0, 0, 0, 0, 0, false, 0.0, Collections.emptyList());
    }

    long getBondPrice()
    {
        return bondPrice;
    }

    long getExtraCoins()
    {
        return extraCoins;
    }

    long getTargetCoins()
    {
        return targetCoins;
    }

    long getCurrentCoins()
    {
        return currentCoins;
    }

    long getRemainingCoins()
    {
        return remainingCoins;
    }

    boolean isBankKnown()
    {
        return bankKnown;
    }

    double getProgressPercent()
    {
        return progressPercent;
    }

    List<BondActivityAdvisor.ActivityEstimate> getActivities()
    {
        return activities;
    }
}
