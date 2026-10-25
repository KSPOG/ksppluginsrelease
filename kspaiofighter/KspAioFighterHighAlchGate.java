package net.runelite.client.plugins.microbot.kspaiofighter;

/**
 * Single-flight, progress-aware High Alch dispatcher.
 *
 * One alch attempt must finish (inventory decreases or Magic XP increases)
 * before another can be started. An animation may be interrupted by combat,
 * so inventory and XP are the primary acknowledgements.
 *
 * No RuneLite dependencies: deterministic regression coverage is possible.
 */
final class KspAioFighterHighAlchGate
{
    enum Decision { READY, WAIT, COOLDOWN, BACK_OFF }

    private static final long CAST_COOLDOWN_MS = 3_000L;
    private static final long ACTION_ACK_TIMEOUT_MS = 5_000L;
    private static final long FAILURE_BACKOFF_MS = 30_000L;
    private static final int MAX_ATTEMPTS = 3;

    private int targetId = -1;
    private int quantityBefore;
    private int magicXpBefore;
    private int attempts;
    private boolean pending;
    private long attemptedAt;
    private long backOffUntil;

    Decision inspect(int currentQuantity, int currentMagicXp, long now)
    {
        return inspect(currentQuantity, currentMagicXp, now, false);
    }

    /**
     * Combat magic can award Magic XP even if the alchemy spell did not
     * succeed. During combat, only disappearance of the selected inventory
     * item confirms that an alch was actually applied.
     */
    Decision inspect(int currentQuantity, int currentMagicXp, long now, boolean inCombat)
    {
        if (pending)
        {
            if (currentQuantity < quantityBefore
                    || (!inCombat && currentMagicXp > magicXpBefore))
            {
                pending = false;
                attempts = 0;
            }
            else if (now - attemptedAt < ACTION_ACK_TIMEOUT_MS)
            {
                return Decision.WAIT;
            }
            else
            {
                pending = false;
                if (attempts >= MAX_ATTEMPTS)
                {
                    backOffUntil = now + FAILURE_BACKOFF_MS;
                }
            }
        }
        if (now < backOffUntil) return Decision.BACK_OFF;
        // A confirmed cast is cooling down, not in-flight. Combat may
        // continue between legal (5-tick) High Alch casts.
        if (attemptedAt > 0L && now - attemptedAt < CAST_COOLDOWN_MS)
            return Decision.COOLDOWN;
        return Decision.READY;
    }

    boolean isBackedOffItem(int itemId, long now)
    {
        return itemId == targetId && now < backOffUntil;
    }

    void dispatched(int itemId, int quantity, int magicXp, long now)
    {
        if (itemId != targetId)
        {
            targetId = itemId;
            attempts = 0;
            backOffUntil = 0L;
        }
        attempts++;
        quantityBefore = quantity;
        magicXpBefore = magicXp;
        attemptedAt = now;
        pending = true;
    }

    int trackedItemId() { return pending ? targetId : -1; }

    void reset()
    {
        targetId = -1;
        quantityBefore = 0;
        magicXpBefore = 0;
        attempts = 0;
        pending = false;
        attemptedAt = 0L;
        backOffUntil = 0L;
    }
}
