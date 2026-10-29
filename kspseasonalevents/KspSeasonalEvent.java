package net.runelite.client.plugins.microbot.kspseasonalevents;

/** Independent event modules can be added without editing another KSP plugin. */
public enum KspSeasonalEvent {
    HALLOWEEN("Halloween");

    private final String label;
    KspSeasonalEvent(String label) { this.label = label; }
    @Override public String toString() { return label; }
}
