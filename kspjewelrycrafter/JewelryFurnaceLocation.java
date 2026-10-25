package net.runelite.client.plugins.microbot.kspjewelrycrafter;

import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;

/**
 * Furnace, approach area, and dedicated bank for a jewellery crafting loop.
 * RuneLite WorldArea constructor order: x, y, width, height, plane.
 *
 * The additional furnace bounds below are the user-provided locations,
 * expressed in the API's required argument order.
 */
public enum JewelryFurnaceLocation
{
    EDGEVILLE("Edgeville", new WorldArea(3106, 3496, 8, 8, 0),
            new WorldPoint(3109, 3499, 0), BankLocation.EDGEVILLE, 16469),
    FALADOR("Falador", new WorldArea(2970, 3368, 7, 9, 0),
            new WorldPoint(2973, 3371, 0), BankLocation.FALADOR_WEST, -1),
    LUMBRIDGE("Lumbridge", new WorldArea(3221, 3252, 9, 6, 0),
            new WorldPoint(3225, 3254, 0), BankLocation.LUMBRIDGE_TOP, -1),
    AL_KHARID("Al Kharid", new WorldArea(3271, 3184, 9, 5, 0),
            new WorldPoint(3275, 3186, 0), BankLocation.AL_KHARID, -1);

    private final String displayName;
    private final WorldArea area;
    private final WorldPoint furnacePoint;
    private final BankLocation bank;
    private final int knownFurnaceId;

    JewelryFurnaceLocation(String displayName, WorldArea area, WorldPoint furnacePoint,
                           BankLocation bank, int knownFurnaceId)
    {
        this.displayName = displayName;
        this.area = area;
        this.furnacePoint = furnacePoint;
        this.bank = bank;
        this.knownFurnaceId = knownFurnaceId;
    }

    public WorldArea getArea() { return area; }
    public WorldPoint getFurnacePoint() { return furnacePoint; }
    public BankLocation getBank() { return bank; }
    public int getKnownFurnaceId() { return knownFurnaceId; }

    @Override
    public String toString() { return displayName; }
}
