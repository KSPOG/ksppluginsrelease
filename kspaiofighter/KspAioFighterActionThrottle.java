package net.runelite.client.plugins.microbot.kspaiofighter;

import java.util.Arrays;

/**
 * Allocation-free throttling for noncritical checks on the 100ms fighter loop.
 * Each action has its own clock: slowing gear/style checks must never defer
 * healing, High Alch, loot confirmation, or a pending dialogue.
 */
final class KspAioFighterActionThrottle
{
    enum Action
    {
        GEAR_CHECK,
        COMBAT_STYLE,
        POTION_CHECK,
        NPC_SEARCH,
        EMPTY_LOOT_SCAN
    }

    private final long[] checkedAt = new long[Action.values().length];
    private final boolean[] checked = new boolean[Action.values().length];

    boolean ready(Action action, long now, long intervalMillis)
    {
        int index = action.ordinal();
        if (checked[index] && now - checkedAt[index] < Math.max(0L, intervalMillis))
            return false;
        checkedAt[index] = now;
        checked[index] = true;
        return true;
    }

    void invalidate(Action action)
    {
        checked[action.ordinal()] = false;
    }

    void reset()
    {
        Arrays.fill(checkedAt, 0L);
        Arrays.fill(checked, false);
    }
}
