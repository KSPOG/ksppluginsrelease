package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.goblindip.reqs;

import net.runelite.api.ItemID;

public enum GobReqs {
    GOBLIN_MAIL("Goblin mail", ItemID.GOBLIN_MAIL, 3),
    BLUE_DYE("Blue dye", ItemID.BLUE_DYE, 1),
    ORANGE_DYE("Orange dye", ItemID.ORANGE_DYE, 1);

    private final String displayName;
    private final int itemId;
    private final int quantity;

    GobReqs(String displayName, int itemId, int quantity) {
        this.displayName = displayName;
        this.itemId = itemId;
        this.quantity = quantity;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getItemId() {
        return itemId;
    }

    public int getQuantity() {
        return quantity;
    }
}
