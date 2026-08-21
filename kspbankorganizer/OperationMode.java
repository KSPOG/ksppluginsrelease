package net.runelite.client.plugins.microbot.kspbankorganizer;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum OperationMode
{
    PREVIEW("Preview only"),
    ORGANIZE("Organize bank");

    private final String displayName;

    public String displayName()
    {
        return displayName;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
