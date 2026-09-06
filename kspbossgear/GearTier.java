package net.runelite.client.plugins.microbot.kspbossgear;

import java.awt.Color;

public enum GearTier
{
    BUDGET("Budget", new Color(222, 165, 72)),
    MID("Mid", new Color(80, 220, 120)),
    HIGH("High", new Color(80, 180, 255)),
    MAX("Max", new Color(196, 116, 255));

    private final String displayName;
    private final Color color;

    GearTier(String displayName, Color color)
    {
        this.displayName = displayName;
        this.color = color;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public Color getColor()
    {
        return color;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
