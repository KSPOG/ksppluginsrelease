package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.therestlessghost.ghostscript;

import javax.inject.Singleton;
import net.runelite.api.Quest;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.shared.QuestRequirement;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.shared.SimpleQuestScript;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

@Singleton
public class GhostScript extends SimpleQuestScript
{
    private static final WorldPoint COFFIN = new WorldPoint(3250, 3193, 0);
    private static final WorldPoint ALTAR = new WorldPoint(3120, 9567, 0);
    public GhostScript() { super(Quest.THE_RESTLESS_GHOST, "The Restless Ghost"); }
    @Override protected String[] preservedItems() { return new String[] { "Ghostspeak amulet", "Ghost's skull" }; }
    @Override protected QuestRequirement[] requirements() { return NO_REQUIREMENTS; }
    @Override protected String[] dialogueOptions()
    {
        return new String[] { "I'm looking for a quest!", "Yes.", "Father Aereck sent me to talk to you.",
                "He's got a ghost haunting his graveyard.", "I've lost the amulet of ghostspeak you gave me.",
                "Yep, now tell me what the problem is.", "Yep, clever aren't I?.",
                "Yes, ok. Do you know WHY you're a ghost?", "Yes, ok. Do you know why you're a ghost?" };
    }
    @Override protected void progressQuest()
    {
        int stage = varp(VarPlayerID.PRIESTSTART);
        if (stage < 0) return;
        if (stage == 0) { talkTo("Father Aereck", new WorldPoint(3243, 3206, 0)); return; }
        if (stage == 1) { talkTo("Father Urhney", new WorldPoint(3147, 3175, 0)); return; }
        if (stage == 2)
        {
            if (!Rs2Equipment.isWearing("Ghostspeak amulet"))
            {
                if (Rs2Inventory.hasItem(ItemID.AMULET_OF_GHOSTSPEAK))
                {
                    if (Rs2Inventory.interact(ItemID.AMULET_OF_GHOSTSPEAK, "Wear")) actionSent();
                }
                else if (!retrieveBanked("Ghostspeak amulet")) talkTo("Father Urhney", new WorldPoint(3147, 3175, 0));
                return;
            }
            if (!walkTo("ghost's coffin", COFFIN, 3)) return;
            var ghost = Microbot.getRs2NpcCache().query().fromWorldView().withName("Restless ghost").nearestOnClientThread();
            if (ghost != null) talkTo("Restless ghost", new WorldPoint(3250, 3195, 0));
            else if (Rs2GameObject.getGameObject(ObjectID.OPENGHOSTCOFFIN) != null)
                interactObject(ObjectID.OPENGHOSTCOFFIN, "Search", COFFIN);
            else interactObject(ObjectID.SHUTGHOSTCOFFIN, "Open", COFFIN);
            return;
        }
        if (stage == 3 || stage == 4)
        {
            if (Rs2Inventory.hasItem(ItemID.GHOSTSKULL) || retrieveBanked("Ghost's skull"))
            {
                if (!Rs2Inventory.hasItem(ItemID.GHOSTSKULL)) return;
                if (!walkTo("ghost's coffin", COFFIN, 3)) return;
                if (Rs2GameObject.getGameObject(ObjectID.OPENGHOSTCOFFIN) != null)
                    interactObject(ObjectID.OPENGHOSTCOFFIN, "Search", COFFIN);
                else interactObject(ObjectID.SHUTGHOSTCOFFIN, "Open", COFFIN);
            }
            else interactObject(ObjectID.RESTLESS_GHOST_ALTAR, "Search", ALTAR);
        }
    }

    @Override protected boolean prepareQuestItems()
    {
        int stage = varp(VarPlayerID.PRIESTSTART);
        if (stage == 2 && !Rs2Equipment.isWearing("Ghostspeak amulet")
                && !Rs2Inventory.hasItem(ItemID.AMULET_OF_GHOSTSPEAK) && retrieveBanked("Ghostspeak amulet")) return false;
        return (stage != 3 && stage != 4) || Rs2Inventory.hasItem(ItemID.GHOSTSKULL) || !retrieveBanked("Ghost's skull");
    }
}
