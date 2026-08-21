package net.runelite.client.plugins.microbot.ksprenderdisable;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum RenderMode
{
    FREEZE_OUTPUT("Freeze renderer output"),
    REDUCE_RENDERING("Reduce rendering only");

    private final String displayName;

    @Override
    public String toString()
    {
        return displayName;
    }
}
