package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.combat.melee.food;

public enum Food {

    SHRIMP("Shrimp", 315, 3),
    SARDINE("Sardine", 325, 4),
    HERRING("Herring", 347, 5),
    TROUT("Trout", 333, 7),
    SALMON("Salmon", 329, 9);

    private final String displayName;
    private final int itemId;
    private final int healAmount;

    Food(String displayName, int itemId, int healAmount) {
        this.displayName = displayName;
        this.itemId = itemId;
        this.healAmount = healAmount;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public int getItemId() {
        return this.itemId;
    }

    public int getHealAmount() {
        return this.healAmount;
    }
}
