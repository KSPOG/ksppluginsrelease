package net.runelite.client.plugins.microbot.kspbankorganizer;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum GearSortMode
{
    COMBAT_STYLE("Combat style"),
    EQUIPMENT_TYPE("Equipment type");

    private final String displayName;

    @Override
    public String toString()
    {
        return displayName;
    }
}
