package net.runelite.client.plugins.microbot.kspsmartsmelter.model;

import net.runelite.api.coords.WorldPoint;

public enum FurnaceLocation {
    EDGEVILLE(
            "Edgeville",
            new WorldPoint(3108, 3499, 0),
            new WorldPoint(3096, 3491, 0)
    ),
    AL_KHARID(
            "Al Kharid",
            new WorldPoint(3273, 3185, 0),
            new WorldPoint(3269, 3167, 0)
    ),
    CURRENT_AREA(
            "Current area",
            null,
            null
    );

    private final String displayName;
    private final WorldPoint furnacePoint;
    private final WorldPoint bankPoint;

    FurnaceLocation(String displayName, WorldPoint furnacePoint, WorldPoint bankPoint) {
        this.displayName = displayName;
        this.furnacePoint = furnacePoint;
        this.bankPoint = bankPoint;
    }

    public String getDisplayName() {
        return displayName;
    }

    public WorldPoint getFurnacePoint() {
        return furnacePoint;
    }

    public WorldPoint getBankPoint() {
        return bankPoint;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
