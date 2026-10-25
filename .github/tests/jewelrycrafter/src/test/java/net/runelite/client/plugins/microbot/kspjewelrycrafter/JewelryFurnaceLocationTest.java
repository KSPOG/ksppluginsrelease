package net.runelite.client.plugins.microbot.kspjewelrycrafter;

import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JewelryFurnaceLocationTest
{
    @Test
    void allFourFurnacesAreSelectable()
    {
        assertEquals(4, JewelryFurnaceLocation.values().length);
        assertEquals("Edgeville", JewelryFurnaceLocation.EDGEVILLE.toString());
        assertEquals("Falador", JewelryFurnaceLocation.FALADOR.toString());
        assertEquals("Lumbridge", JewelryFurnaceLocation.LUMBRIDGE.toString());
        assertEquals("Al Kharid", JewelryFurnaceLocation.AL_KHARID.toString());
    }

    @Test
    void configuredWorldAreasPreserveLocationCoordinates()
    {
        verify(JewelryFurnaceLocation.FALADOR, 2970, 3368, 7, 9, 0);
        verify(JewelryFurnaceLocation.LUMBRIDGE, 3221, 3252, 9, 6, 0);
        verify(JewelryFurnaceLocation.AL_KHARID, 3271, 3184, 9, 5, 0);
    }

    @Test
    void eachFurnaceAnchorLiesInItsSelectedWorldArea()
    {
        for (JewelryFurnaceLocation location : JewelryFurnaceLocation.values())
        {
            assertTrue(location.getArea().contains(location.getFurnacePoint()),
                    "Out-of-bounds furnace anchor: " + location);
            assertEquals(0, location.getFurnacePoint().getPlane());
        }
        assertFalse(JewelryFurnaceLocation.FALADOR.getArea().contains(
                JewelryFurnaceLocation.AL_KHARID.getFurnacePoint()));
    }

    @Test
    void eachFurnaceHasCorrectLocalBankAndLumbridgeUsesUpperFloor()
    {
        assertEquals(BankLocation.EDGEVILLE, JewelryFurnaceLocation.EDGEVILLE.getBank());
        assertEquals(BankLocation.FALADOR_WEST, JewelryFurnaceLocation.FALADOR.getBank());
        assertEquals(BankLocation.LUMBRIDGE_TOP, JewelryFurnaceLocation.LUMBRIDGE.getBank());
        assertEquals(BankLocation.AL_KHARID, JewelryFurnaceLocation.AL_KHARID.getBank());
        assertEquals(2, JewelryFurnaceLocation.LUMBRIDGE.getBank().getWorldPoint().getPlane());
    }

    @Test
    void onlyEdgevilleNeedsLegacyFurnaceId()
    {
        assertEquals(16469, JewelryFurnaceLocation.EDGEVILLE.getKnownFurnaceId());
        assertEquals(-1, JewelryFurnaceLocation.FALADOR.getKnownFurnaceId());
        assertEquals(-1, JewelryFurnaceLocation.LUMBRIDGE.getKnownFurnaceId());
        assertEquals(-1, JewelryFurnaceLocation.AL_KHARID.getKnownFurnaceId());
    }

    private static void verify(JewelryFurnaceLocation location, int x, int y,
                               int width, int height, int plane)
    {
        WorldArea area = location.getArea();
        assertEquals(x, area.getX(), location.toString());
        assertEquals(y, area.getY(), location.toString());
        assertEquals(width, area.getWidth(), location.toString());
        assertEquals(height, area.getHeight(), location.toString());
        assertEquals(plane, area.getPlane(), location.toString());
    }
}
