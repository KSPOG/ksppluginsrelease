package net.runelite.client.plugins.microbot.kspaccountbuilder;

/** Selectable Account Builder HUDs. The original remains the initial default. */
public enum KspAccountBuilderOverlayStyle
{
    DEFAULT("Default (Original)"),
    NEXUS("Nexus"),
    PRESTIGE("Prestige"),
    COMPACT("Compact"),
    HORIZON("Horizon"),
    VERTICAL_PRO("Vertical Pro"),
    ROADMAP("Roadmap"),
    ELEMENTAL("Elemental"),
    CLASSIC_PLUS("Classic Plus");

    private final String label;

    KspAccountBuilderOverlayStyle(String label) { this.label = label; }

    @Override
    public String toString() { return label; }
}
