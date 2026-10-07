package net.runelite.client.plugins.microbot.kspjewelrycrafter;

import net.runelite.api.coords.WorldPoint;

public enum JewelryWorkLocation
{
    EDGEVILLE("Edgeville", new WorldPoint(3109, 3499, 0), new WorldPoint(3096, 3494, 0)),
    AL_KHARID("Al Kharid", new WorldPoint(3273, 3185, 0), new WorldPoint(3269, 3167, 0));

    private final String displayName;
    private final WorldPoint furnacePoint;
    private final WorldPoint bankPoint;

    JewelryWorkLocation(String displayName, WorldPoint furnacePoint, WorldPoint bankPoint)
    {
        this.displayName = displayName;
        this.furnacePoint = furnacePoint;
        this.bankPoint = bankPoint;
    }

    public WorldPoint getFurnacePoint() { return furnacePoint; }
    public WorldPoint getBankPoint() { return bankPoint; }

    @Override
    public String toString() { return displayName; }
}
