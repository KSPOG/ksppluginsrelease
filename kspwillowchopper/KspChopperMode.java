package net.runelite.client.plugins.microbot.kspwillowchopper;

/** The resource handling strategy selected in the Chopper side panel. */
public enum KspChopperMode {
    CHOP_BANK("Chop & Bank"),
    CHOP_BURN("Chop & Burn"),
    CHOP_DROP("Chop & Drop");

    private final String label;

    KspChopperMode(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
