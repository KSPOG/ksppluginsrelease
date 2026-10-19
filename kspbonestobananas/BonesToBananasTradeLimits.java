package net.runelite.client.plugins.microbot.kspbonestobananas;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.gameval.ItemID;

/**
 * Conservative, account-scoped GE four-hour BUY-limit ledger.
 * Counts actual fill deltas, never order placement or sales.
 * In-client memory survives script restarts, but cannot know external purchases
 * or purchases made before the client launched.
 */
final class BonesToBananasTradeLimits
{
    private static final long FOUR_HOURS_MS = 4L * 60 * 60 * 1000;
    private static final Map<String, Window> WINDOWS = new HashMap<>();

    private BonesToBananasTradeLimits() { }

    static int limit(int itemId)
    {
        if (itemId == ItemID.NATURERUNE) return 18_000;
        if (itemId == ItemID.WATERRUNE || itemId == ItemID.EARTHRUNE) return 50_000;
        // The supported regular/burnt/bat/wolf/monkey/jogre/big bones
        // use a conservative 3,000-per-4h cap. Some may have higher
        // published limits; conservatism avoids oversizing offers.
        for (BananaBoneType bone : BananaBoneType.values())
            if (bone.getItemId() == itemId) return 3_000;
        return 0;
    }

    static synchronized int remaining(String account, int itemId)
    {
        int limit = limit(itemId);
        if (limit <= 0 || account == null || account.isEmpty()) return 0;
        String key = account + ":" + itemId;
        Window w = WINDOWS.get(key);
        if (w == null) return limit;
        if (System.currentTimeMillis() - w.firstFillAt >= FOUR_HOURS_MS)
        {
            WINDOWS.remove(key);
            return limit;
        }
        return Math.max(0, limit - w.filled);
    }

    static synchronized void recordFill(String account, int itemId, int delta)
    {
        if (delta <= 0 || account == null || account.isEmpty()) return;
        String key = account + ":" + itemId;
        long now = System.currentTimeMillis();
        Window w = WINDOWS.get(key);
        if (w == null || now - w.firstFillAt >= FOUR_HOURS_MS)
        {
            w = new Window(now);
            WINDOWS.put(key, w);
        }
        w.filled = (int) Math.min(Integer.MAX_VALUE, (long) w.filled + delta);
    }

    private static final class Window
    {
        final long firstFillAt;
        int filled;
        Window(long firstFillAt) { this.firstFillAt = firstFillAt; }
    }
}
