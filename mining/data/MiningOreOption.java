package net.runelite.client.plugins.microbot.mining.data;


public enum MiningOreOption {
    COPPER_AND_TIN("Copper & Tin", Rocks.COPPER),
    CLAY("Clay", Rocks.CLAY),
    IRON("Iron", Rocks.IRON),
    SILVER("Silver", Rocks.SILVER),
    COAL("Coal", Rocks.COAL),
    GOLD("Gold", Rocks.GOLD),
    GEM("Gem", Rocks.GEM),
    MITHRIL("Mithril", Rocks.MITHRIL),
    ADAMANTITE("Adamantite", Rocks.ADAMANTITE),
    BASALT("Basalt", Rocks.BASALT),
    URT_SALT("Urt salt", Rocks.URT_SALT),
    EFH_SALT("Efh salt", Rocks.EFH_SALT),
    TE_SALT("Te salt", Rocks.TE_SALT),
    RUNITE("Runite", Rocks.RUNITE),
    NONE("None", Rocks.NONE);

    private final String displayName;
    private final Rocks rock;

    MiningOreOption(String displayName, Rocks rock) {
        this.displayName = displayName;
        this.rock = rock;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Rocks getRock() {
        return rock;
    }

    public boolean isCopperAndTin() {
        return this == COPPER_AND_TIN;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
