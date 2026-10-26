package net.runelite.client.plugins.microbot.kspwillowchopper;

/** The resource handling strategy selected in the Chopper side panel. */
public enum KspChopperMode {
    CHOP_BANK("Chop & Bank"),
    CHOP_BURN("Chop & Burn"),
    CHOP_DROP("Chop & Drop"),
    PROGRESSIVE_CHOP_BANK("Progressive Chop & Bank"),
    PROGRESSIVE_CHOP_BURN("Progressive Chop & Burn"),
    PROGRESSIVE_CHOP_DROP("Progressive Chop & Drop");

    private final String label;

    KspChopperMode(String label) {
        this.label = label;
    }

    public boolean isProgressive() {
        return this == PROGRESSIVE_CHOP_BANK || this == PROGRESSIVE_CHOP_BURN
                || this == PROGRESSIVE_CHOP_DROP;
    }

    public boolean isBank() {
        return this == CHOP_BANK || this == PROGRESSIVE_CHOP_BANK;
    }

    public boolean isBurn() {
        return this == CHOP_BURN || this == PROGRESSIVE_CHOP_BURN;
    }

    public boolean isDrop() {
        return this == CHOP_DROP || this == PROGRESSIVE_CHOP_DROP;
    }

    @Override
    public String toString() {
        return label;
    }
}
