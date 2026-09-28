/*
 * Decompiled with CFR 0.152.
 */
package net.runelite.client.plugins.microbot.kspaccountbuilder;

import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.areas.Areas;

public enum KspDeveloperMiningTarget {
    DEFAULT_PROGRESS(null),
    TIN_COPPER_VARROCK_EAST(Areas.TIN_COPPER_VARROCK_EAST),
    IRON_VARROCK_EAST(Areas.IRON_VARROCK_EAST),
    SILVER_VARROCK_WEST(Areas.SILVER_VARROCK_WEST),
    COAL_BARBARIAN_VILLAGE(Areas.COAL_BARBARIAN_VILLAGE),
    CLAY_VARROCK_WEST(Areas.CLAY_VARROCK_WEST);

    private final Areas area;

    private KspDeveloperMiningTarget(Areas area) {
        this.area = area;
    }

    public Areas getArea() {
        return this.area;
    }
}

