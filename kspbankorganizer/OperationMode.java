package net.runelite.client.plugins.microbot.kspbankorganizer;

public enum OperationMode
{
    PREVIEW("Preview only"),
    ORGANIZE("Organize bank");

    private final String displayName;

    OperationMode(String displayName)
    {
        this.displayName = displayName;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
