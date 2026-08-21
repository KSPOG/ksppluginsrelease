package net.runelite.client.plugins.microbot.kspbryophyta;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Lightweight item record used by the equipment picker UI. */
@Getter
@RequiredArgsConstructor
final class BryophytaEquipmentItem
{
    private final int id;
    private final String name;
    private final boolean members;
    private final boolean twoHanded;

    @Override
    public String toString()
    {
        return name;
    }
}
