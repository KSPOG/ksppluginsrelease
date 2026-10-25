package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.buyscript;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CraftingSupplyPriorityPolicyTest {
    @Test void prioritizesChiselBeforeGem() {
        assertEquals("Chisel", CraftingSupplyPriorityPolicy.choose(true, false, "Uncut emerald", true, false));
    }
    @Test void pendingChiselAllowsMissingGemPurchase() {
        assertEquals("Uncut emerald", CraftingSupplyPriorityPolicy.choose(true, true, "Uncut emerald", true, false));
    }
    @Test void pendingGemAllowsMissingChiselPurchase() {
        assertEquals("Chisel", CraftingSupplyPriorityPolicy.choose(true, false, "Uncut sapphire", true, true));
    }
    @Test void pendingChiselWithoutGemDemandDoesNotBuyAgain() {
        assertNull(CraftingSupplyPriorityPolicy.choose(true, true, "Uncut sapphire", false, false));
    }
    @Test void missingGemOnlySelectsGem() {
        assertEquals("Uncut ruby", CraftingSupplyPriorityPolicy.choose(false, false, "Uncut ruby", true, false));
    }
    @Test void pendingGemCannotBeOrderedTwice() {
        assertNull(CraftingSupplyPriorityPolicy.choose(false, false, "Uncut diamond", true, true));
    }
    @Test void noEssentialShortageFallsBackToExistingSupplies() {
        assertNull(CraftingSupplyPriorityPolicy.choose(false, false, "Uncut emerald", false, false));
    }
    @Test void absentGemSelectionFallsBackWithoutFailure() {
        assertNull(CraftingSupplyPriorityPolicy.choose(false, false, null, false, false));
    }
}
