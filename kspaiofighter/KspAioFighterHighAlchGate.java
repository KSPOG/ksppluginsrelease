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
    enum Decision { READY, WAIT, BACK_OFF }

    private static final long CAST_COOLDOWN_MS = 2_400L;
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
        if (pending)
        {
            if (currentQuantity < quantityBefore || currentMagicXp > magicXpBefore)
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
        if (attemptedAt > 0L && now - attemptedAt < CAST_COOLDOWN_MS)
            return Decision.WAIT;
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
