package net.runelite.client.plugins.microbot.kspbankorganizer;

public enum TeleportSortMode
{
    RUNES_FIRST("Runes first"),
    JEWELRY_FIRST("Jewelry first"),
    TABLETS_FIRST("Tablets first");

    private final String displayName;

    TeleportSortMode(String displayName)
    {
        this.displayName = displayName;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
