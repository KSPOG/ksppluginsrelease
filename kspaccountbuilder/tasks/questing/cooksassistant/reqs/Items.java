package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.cooksassistant.reqs;

public enum Items {
    EGG("Egg", 1944),
    BUCKET_OF_MILK("Bucket of milk", 1927),
    POT_OF_FLOUR("Pot of flour", 1933);

    private final String displayName;
    private final int itemId;

    public String getDisplayName() {
        return this.displayName;
    }

    public int getItemId() {
        return this.itemId;
    }

    Items(String displayName, int itemId) {
        this.displayName = displayName;
        this.itemId = itemId;
    }
}
