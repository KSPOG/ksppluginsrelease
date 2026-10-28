package net.runelite.client.plugins.microbot.kspseasonalevents;

/** Independent event modules can be added without editing another KSP plugin. */
public enum KspSeasonalEvent {
    HALLOWEEN_2026("Halloween 2026 (entry beta)");

    private final String label;
    KspSeasonalEvent(String label) { this.label = label; }
    @Override public String toString() { return label; }
}
