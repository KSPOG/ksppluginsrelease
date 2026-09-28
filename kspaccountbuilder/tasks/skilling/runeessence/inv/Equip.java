package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.runeessence.inv;

import lombok.Getter;

@Getter
public enum Equip
{
    BRONZE("Bronze pickaxe", 1, 1),
    IRON("Iron pickaxe", 1, 1),
    STEEL("Steel pickaxe", 6, 5),
    BLACK("Black pickaxe", 11, 10),
    MITHRIL("Mithril pickaxe", 21, 20),
    ADAMANT("Adamant pickaxe", 31, 30),
    RUNE("Rune pickaxe", 41, 40);

    private final String displayName;
    private final int requiredMiningLevel;
    private final int requiredAttackLevel;

    Equip(String displayName, int requiredMiningLevel, int requiredAttackLevel)
    {
        this.displayName = displayName;
        this.requiredMiningLevel = requiredMiningLevel;
        this.requiredAttackLevel = requiredAttackLevel;
    }

    public static Equip bestForLevels(int miningLevel, int attackLevel)
    {
        Equip best = BRONZE;
        for (Equip pickaxe : values())
        {
            if (miningLevel >= pickaxe.requiredMiningLevel
                    && attackLevel >= pickaxe.requiredAttackLevel)
            {
                best = pickaxe;
            }
        }
        return best;
    }
}
