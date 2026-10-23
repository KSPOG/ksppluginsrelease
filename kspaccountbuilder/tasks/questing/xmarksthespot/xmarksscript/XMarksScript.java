package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.xmarksthespot.xmarksscript;

import javax.inject.Singleton;
import net.runelite.api.Quest;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.shared.QuestRequirement;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.shared.SimpleQuestScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.xmarksthespot.reqs.XMarksRequirements;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

@Singleton
public class XMarksScript extends SimpleQuestScript
{
    private static final WorldPoint[] DIG_TILES = {
        new WorldPoint(3230, 3209, 0), new WorldPoint(3203, 3212, 0),
        new WorldPoint(3109, 3264, 0), new WorldPoint(3078, 3259, 0)
    };
    public XMarksScript() { super(Quest.X_MARKS_THE_SPOT, "X Marks the Spot"); }
    private int stage() { return Microbot.getVarbitValue(VarbitID.CLUEQUEST); }
    @Override protected QuestRequirement[] requirements() { return stage() >= 7 ? NO_REQUIREMENTS : XMarksRequirements.spade(); }
    @Override protected String[] preservedItems() { return new String[] { "Treasure scroll", "Mysterious orb", "Ancient casket" }; }
    @Override protected String[] dialogueOptions()
    {
        return new String[] { "I'm looking for a quest.", "Sounds good, what should I do?", "Can I help?", "Yes.", "Okay, thanks Veos.", "Talk about X Marks the Spot." };
    }
    @Override protected boolean prepareQuestItems()
    {
        return stage() != 6 || Rs2Inventory.hasItem(ItemID.CLUEQUEST_CASKET) || !retrieveBanked("Ancient casket");
    }
    @Override protected void progressQuest()
    {
        int stage = stage();
        if (stage <= 1) { talkTo("Veos", new WorldPoint(3228, 3242, 0)); return; }
        if (stage >= 7 || (stage == 6 && Rs2Inventory.hasItem(ItemID.CLUEQUEST_CASKET)))
        {
            talkTo("Veos", new WorldPoint(3054, 3245, 0));
            return;
        }
        if (stage < 2 || stage > 6) { setStatus("Waiting for X Marks the Spot stage"); return; }
        WorldPoint tile = DIG_TILES[Math.min(3, stage - 2)];
        if (!walkTo("dig site " + stage, tile, 0) || !tile.equals(Rs2Player.getWorldLocation()) || Rs2Player.isMoving()) return;
        setStatus("Digging at clue " + Math.min(4, stage - 1));
        if (Rs2Inventory.interact(ItemID.SPADE, "Dig")) actionSent();
    }
}
