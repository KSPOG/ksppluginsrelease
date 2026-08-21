package net.runelite.client.plugins.microbot.ksprenderdisable;

public enum RenderMode
{
    FREEZE_OUTPUT("Freeze renderer output"),
    REDUCE_RENDERING("Reduce rendering only");

    private final String displayName;

    RenderMode(String displayName)
    {
        this.displayName = displayName;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
