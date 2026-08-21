package net.runelite.client.plugins.microbot.kspbryophyta;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum BryophytaStrategy
{
    MELEE("Melee"),
    RANGED("Ranged"),
    MAGIC_FIRE("Magic - Fire");

    private final String displayName;

    @Override
    public String toString()
    {
        return displayName;
    }
}
