/*
 * Decompiled with CFR 0.152.
 */
package net.runelite.client.plugins.microbot.kspaccountbuilder;

import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smelting.barlevel.BarLevels;

public enum KspDeveloperSmeltingTarget {
    DEFAULT_PROGRESS(null),
    BRONZE(BarLevels.BRONZE),
    IRON(BarLevels.IRON),
    SILVER(BarLevels.SILVER),
    STEEL(BarLevels.STEEL),
    GOLD(BarLevels.GOLD),
    MITHRIL(BarLevels.MITHRIL),
    ADAMANT(BarLevels.ADAMANT),
    RUNE(BarLevels.RUNE);

    private final BarLevels barLevel;

    private KspDeveloperSmeltingTarget(BarLevels barLevel) {
        this.barLevel = barLevel;
    }

    public BarLevels getBarLevel() {
        return this.barLevel;
    }
}

