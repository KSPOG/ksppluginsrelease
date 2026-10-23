/*
 * Decompiled with CFR 0.152.
 */
package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smithing.tool;

public enum SmithTool {
    HAMMER("Hammer", 2347);

    private final String displayName;
    private final int itemId;

    public String getDisplayName() {
        return this.displayName;
    }

    public int getItemId() {
        return this.itemId;
    }

    private SmithTool(String displayName, int itemId) {
        this.displayName = displayName;
        this.itemId = itemId;
    }
}

