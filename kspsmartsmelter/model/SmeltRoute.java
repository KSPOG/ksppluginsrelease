package net.runelite.client.plugins.microbot.kspsmartsmelter.model;

import net.runelite.api.gameval.ItemID;

import java.util.Arrays;

public enum SmeltRoute {
    BRONZE_BAR(
            "Bronze bar", ItemID.BRONZE_BAR, 1, 1, false, false, false, 1.0,
            new int[]{ItemID.COPPER_ORE, ItemID.TIN_ORE},
            new int[]{1, 1}
    ),
    IRON_BAR(
            "Iron bar", ItemID.IRON_BAR, 1, 15, false, true, false, 0.50,
            new int[]{ItemID.IRON_ORE},
            new int[]{1}
    ),
    SILVER_BAR(
            "Silver bar", ItemID.SILVER_BAR, 1, 20, false, false, false, 1.0,
            new int[]{ItemID.SILVER_ORE},
            new int[]{1}
    ),
    STEEL_BAR(
            "Steel bar", ItemID.STEEL_BAR, 1, 30, false, false, false, 1.0,
            new int[]{ItemID.IRON_ORE, ItemID.COAL},
            new int[]{1, 2}
    ),
    GOLD_BAR(
            "Gold bar", ItemID.GOLD_BAR, 1, 40, false, false, false, 1.0,
            new int[]{ItemID.GOLD_ORE},
            new int[]{1}
    ),
    MITHRIL_BAR(
            "Mithril bar", ItemID.MITHRIL_BAR, 1, 50, false, false, false, 1.0,
            new int[]{ItemID.MITHRIL_ORE, ItemID.COAL},
            new int[]{1, 4}
    ),
    ADAMANTITE_BAR(
            "Adamantite bar", ItemID.ADAMANTITE_BAR, 1, 70, false, false, false, 1.0,
            new int[]{ItemID.ADAMANTITE_ORE, ItemID.COAL},
            new int[]{1, 6}
    ),
    RUNITE_BAR(
            "Runite bar", ItemID.RUNITE_BAR, 1, 85, false, false, false, 1.0,
            new int[]{ItemID.RUNITE_ORE, ItemID.COAL},
            new int[]{1, 8}
    ),
    CANNONBALLS(
            "Cannonball", ItemID.MCANNONBALL, 4, 35, true, false, true, 1.0,
            new int[]{ItemID.STEEL_BAR},
            new int[]{1}
    );

    private final String outputName;
    private final int outputId;
    private final int outputQuantity;
    private final int smithingLevel;
    private final boolean membersOnly;
    private final boolean riskyIron;
    private final boolean cannonballs;
    private final double expectedYield;
    private final int[] inputIds;
    private final int[] inputQuantities;

    SmeltRoute(
            String outputName,
            int outputId,
            int outputQuantity,
            int smithingLevel,
            boolean membersOnly,
            boolean riskyIron,
            boolean cannonballs,
            double expectedYield,
            int[] inputIds,
            int[] inputQuantities
    ) {
        this.outputName = outputName;
        this.outputId = outputId;
        this.outputQuantity = outputQuantity;
        this.smithingLevel = smithingLevel;
        this.membersOnly = membersOnly;
        this.riskyIron = riskyIron;
        this.cannonballs = cannonballs;
        this.expectedYield = expectedYield;
        this.inputIds = inputIds;
        this.inputQuantities = inputQuantities;
    }

    public String getOutputName() {
        return outputName;
    }

    public int getOutputId() {
        return outputId;
    }

    public int getOutputQuantity() {
        return outputQuantity;
    }

    public int getSmithingLevel() {
        return smithingLevel;
    }

    public boolean isMembersOnly() {
        return membersOnly;
    }

    public boolean isRiskyIron() {
        return riskyIron;
    }

    public boolean isCannonballs() {
        return cannonballs;
    }

    public double getExpectedYield() {
        return expectedYield;
    }

    public int[] getInputIds() {
        return Arrays.copyOf(inputIds, inputIds.length);
    }

    public int[] getInputQuantities() {
        return Arrays.copyOf(inputQuantities, inputQuantities.length);
    }

    public int getSlotsPerCycle() {
        return Arrays.stream(inputQuantities).sum();
    }

    public int getMaxCyclesPerTrip() {
        int usableSlots = cannonballs ? 27 : 28;
        return Math.max(1, usableSlots / getSlotsPerCycle());
    }

    public boolean hasEnoughInputs(java.util.function.IntUnaryOperator quantityProvider) {
        for (int i = 0; i < inputIds.length; i++) {
            if (quantityProvider.applyAsInt(inputIds[i]) < inputQuantities[i]) {
                return false;
            }
        }
        return true;
    }
}
