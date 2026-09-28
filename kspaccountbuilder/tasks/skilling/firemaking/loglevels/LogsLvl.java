/*
 * Decompiled with CFR 0.152.
 */
package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.firemaking.loglevels;

public enum LogsLvl {
    LOGS("Logs", 1),
    OAK_LOGS("Oak logs", 15),
    WILLOW_LOGS("Willow logs", 30);

    private final String displayName;
    private final int requiredLevel;

    public String getDisplayName() {
        return this.displayName;
    }

    public int getRequiredLevel() {
        return this.requiredLevel;
    }

    private LogsLvl(String displayName, int requiredLevel) {
        this.displayName = displayName;
        this.requiredLevel = requiredLevel;
    }
}

