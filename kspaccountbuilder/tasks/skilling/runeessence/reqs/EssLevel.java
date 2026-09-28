package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.runeessence.reqs;

import lombok.Getter;

@Getter
public enum EssLevel
{
    RUNE_ESSENCE("Rune essence", 1);

    private final String displayName;
    private final int requiredMiningLevel;

    EssLevel(String displayName, int requiredMiningLevel)
    {
        this.displayName = displayName;
        this.requiredMiningLevel = requiredMiningLevel;
    }
}
