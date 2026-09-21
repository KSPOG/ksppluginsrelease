package net.runelite.client.plugins.microbot.kspbonestobananas;

import net.runelite.api.gameval.ItemID;

/**
 * GE-tradeable bone families accepted by Bones to Bananas.
 * Candidates with no live GE quote are ignored automatically.
 */
public enum BananaBoneType
{
    BONES(ItemID.BONES, "Bones", false),
    BURNT_BONES(ItemID.BONES_BURNT, "Burnt bones", false),
    BAT_BONES(ItemID.BAT_BONES, "Bat bones", true),
    WOLF_BONES(ItemID.WOLF_BONES, "Wolf bones", true),
    MONKEY_BONES(ItemID.MM_NORMAL_MONKEY_BONES, "Monkey bones", true),
    JOGRE_BONES(ItemID.TBWT_JOGRE_BONES, "Jogre bones", true),
    BIG_BONES(ItemID.BIG_BONES, "Big bones", false);

    private final int itemId;
    private final String itemName;
    private final boolean membersOnly;

    BananaBoneType(int itemId, String itemName, boolean membersOnly)
    {
        this.itemId = itemId;
        this.itemName = itemName;
        this.membersOnly = membersOnly;
    }

    public int getItemId() { return itemId; }
    public String getItemName() { return itemName; }
    public boolean isMembersOnly() { return membersOnly; }
}
