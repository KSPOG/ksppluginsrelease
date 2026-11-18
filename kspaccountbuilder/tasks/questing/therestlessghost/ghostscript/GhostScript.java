package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.therestlessghost.ghostscript;

import java.util.List;
import java.util.Locale;
import javax.inject.Singleton;
import net.runelite.api.Quest;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.shared.QuestRequirement;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.shared.SimpleQuestScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.KspLiveScene;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

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
        switch (varp(VarPlayerID.PRIESTSTART))
        {
            case 0:
                return new String[] { "I'm looking for a quest!", "Ok, let me help then.", "Okay, let me help then." };
            case 1:
                return new String[] { "Father Aereck sent me to talk to you.", "He's got a ghost haunting his graveyard." };
            case 2:
                return new String[] { "I've lost the amulet of ghostspeak you gave me.",
                        "Yep, now tell me what the problem is.", "Yep, clever aren't I?",
                        "Yes, ok. Do you know why you're a ghost?" };
            default:
                return new String[0];
        }
    }

    @Override protected void advanceDialogue()
    {
        if (!isActionReady()) return;
        if (Rs2Dialogue.hasSelectAnOption())
        {
            List<Widget> choices = Rs2Dialogue.getDialogueOptions();
            for (String expected : dialogueOptions())
            {
                String normalized = normalizeOption(expected);
                for (int index = 0; index < choices.size(); index++)
                {
                    Widget choice = choices.get(index);
                    if (choice == null || !normalized.equals(normalizeOption(choice.getText()))) continue;
                    setStatus("Selecting: " + expected);
                    if (Rs2Widget.clickWidget(choice) || Rs2Dialogue.keyPressForDialogueOption(index + 1)) actionSent();
                    return;
                }
            }
            setStatus("Waiting for a recognized Restless Ghost dialogue option");
        }
        else if (Rs2Dialogue.hasContinue())
        {
            setStatus("Continuing The Restless Ghost dialogue");
            Rs2Dialogue.clickContinue();
            actionSent();
        }
    }

    private static String normalizeOption(String text)
    {
        return text == null ? "" : text.replaceAll("<[^>]*>", " ").toLowerCase(Locale.ROOT)
                .replaceAll("[’']", "").replaceAll("[^a-z0-9]+", " ").trim();
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
                else if (!retrieveBanked(ItemID.AMULET_OF_GHOSTSPEAK)) talkTo("Father Urhney", new WorldPoint(3147, 3175, 0));
                return;
            }
            if (!walkTo("ghost's coffin", COFFIN, 3)) return;
            var ghost = KspLiveScene.nearestNpcModel(Rs2Player.getWorldLocation(), 30,
                    npc -> "Restless ghost".equalsIgnoreCase(npc.getName()));
            if (ghost != null) talkTo("Restless ghost", new WorldPoint(3250, 3195, 0));
            else if (Rs2GameObject.getGameObject(ObjectID.OPENGHOSTCOFFIN) != null)
                interactObject(ObjectID.OPENGHOSTCOFFIN, "Search", COFFIN);
            else interactObject(ObjectID.SHUTGHOSTCOFFIN, "Open", COFFIN);
            return;
        }
        if (stage == 3 || stage == 4)
        {
            if (Rs2Inventory.hasItem(ItemID.GHOSTSKULL) || retrieveBanked(ItemID.GHOSTSKULL))
            {
                if (!Rs2Inventory.hasItem(ItemID.GHOSTSKULL)) return;
                if (!walkTo("ghost's coffin", COFFIN, 3)) return;
                if (Rs2GameObject.getGameObject(ObjectID.OPENGHOSTCOFFIN) == null)
                    interactObject(ObjectID.SHUTGHOSTCOFFIN, "Open", COFFIN);
                else if (!Rs2Player.isMoving() && !Rs2Player.isAnimating() && !Rs2Player.isInteracting())
                {
                    setStatus("Using the ghost's skull on the open coffin");
                    if (Rs2Inventory.useItemOnObject(ItemID.GHOSTSKULL, ObjectID.OPENGHOSTCOFFIN)) actionSent();
                }
            }
            else interactObject(ObjectID.RESTLESS_GHOST_ALTAR, "Search", ALTAR);
        }
    }

    @Override protected boolean prepareQuestItems()
    {
        int stage = varp(VarPlayerID.PRIESTSTART);
        if (stage == 2 && !Rs2Equipment.isWearing("Ghostspeak amulet")
                && !Rs2Inventory.hasItem(ItemID.AMULET_OF_GHOSTSPEAK) && retrieveBanked(ItemID.AMULET_OF_GHOSTSPEAK)) return false;
        return (stage != 3 && stage != 4) || Rs2Inventory.hasItem(ItemID.GHOSTSKULL) || !retrieveBanked(ItemID.GHOSTSKULL);
    }
}
