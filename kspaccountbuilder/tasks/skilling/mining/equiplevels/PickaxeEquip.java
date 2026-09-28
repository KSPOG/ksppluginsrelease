package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.equiplevels;

import lombok.Getter;

@Getter
public enum PickaxeEquip
{
    BRONZE("Bronze pickaxe", 1),
    IRON("Iron pickaxe", 1),
    STEEL("Steel pickaxe", 5),
    BLACK("Black pickaxe", 10),
    MITHRIL("Mithril pickaxe", 20),
    ADAMANT("Adamant pickaxe", 30),
    RUNE("Rune pickaxe", 40);

    private final String displayName;
    private final int requiredAttackLevel;

    PickaxeEquip(String displayName, int requiredAttackLevel)
    {
        this.displayName = displayName;
        this.requiredAttackLevel = requiredAttackLevel;
    }

    public static PickaxeEquip bestForAttackLevel(int attackLevel)
    {
        PickaxeEquip best = BRONZE;
        for (PickaxeEquip pickaxe : values())
        {
            if (attackLevel >= pickaxe.requiredAttackLevel)
            {
                best = pickaxe;
            }
        }
        return best;
    }
}