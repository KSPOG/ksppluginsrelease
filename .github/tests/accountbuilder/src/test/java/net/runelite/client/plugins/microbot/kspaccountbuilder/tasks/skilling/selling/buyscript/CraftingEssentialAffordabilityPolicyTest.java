package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.buyscript;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CraftingEssentialAffordabilityPolicyTest {
    @Test void bothAffordablePrioritizesChisel() { assertEquals("Chisel",CraftingSupplyPriorityPolicy.chooseAffordable(true,false,100,"Uncut emerald",true,false,200,500)); }
    @Test void chiselOnlyBudget() { assertEquals("Chisel",CraftingSupplyPriorityPolicy.chooseAffordable(true,false,100,"Uncut emerald",true,false,200,100)); }
    @Test void gemAffordableChiselUnaffordable() { assertEquals("Uncut emerald",CraftingSupplyPriorityPolicy.chooseAffordable(true,false,500,"Uncut emerald",true,false,100,150)); }
    @Test void neitherAffordable() { assertNull(CraftingSupplyPriorityPolicy.chooseAffordable(true,false,500,"Uncut emerald",true,false,200,100)); }
    @Test void zeroCoins() { assertNull(CraftingSupplyPriorityPolicy.chooseAffordable(true,false,100,"Uncut emerald",true,false,100,0)); }
    @Test void pendingChiselAllowsGem() { assertEquals("Uncut sapphire",CraftingSupplyPriorityPolicy.chooseAffordable(true,true,100,"Uncut sapphire",true,false,60,100)); }
    @Test void pendingGemAllowsChisel() { assertEquals("Chisel",CraftingSupplyPriorityPolicy.chooseAffordable(true,false,70,"Uncut ruby",true,true,40,70)); }
    @Test void bothPendingDoNotDuplicate() { assertNull(CraftingSupplyPriorityPolicy.chooseAffordable(true,true,70,"Uncut ruby",true,true,40,500)); }
    @Test void invalidChiselPriceAllowsGem() { assertEquals("Uncut sapphire",CraftingSupplyPriorityPolicy.chooseAffordable(true,false,1,"Uncut sapphire",true,false,45,45)); }
    @Test void invalidGemPriceRejected() { assertNull(CraftingSupplyPriorityPolicy.chooseAffordable(false,false,0,"Uncut sapphire",true,false,1,1000)); }
    @Test void missingGemSelectionRejected() { assertNull(CraftingSupplyPriorityPolicy.chooseAffordable(false,false,0,null,true,false,45,1000)); }
    @Test void alreadyOwnedEssentialsRejected() { assertNull(CraftingSupplyPriorityPolicy.chooseAffordable(false,false,70,"Uncut emerald",false,false,45,1000)); }
}
