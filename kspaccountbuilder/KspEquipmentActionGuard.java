package net.runelite.client.plugins.microbot.kspaccountbuilder;

/**
 * Bounded equipment dispatch/acknowledgement guard shared by skilling tasks.
 *
 * Inventory possession is not proof that the tool was equipped. A client
 * should retry only after its previously issued equipment action has had
 * time to update the worn slot, and should cool down on repeated failures.
 */
public final class KspEquipmentActionGuard
{
    public enum Decision { ALREADY_EQUIPPED, WAIT, TRY, COOLDOWN }

    private static final long ACK_TIMEOUT_MS = 1_500L;
    private static final long FAILURE_COOLDOWN_MS = 30_000L;
    private static final int MAX_ATTEMPTS = 3;

    private String itemName;
    private long attemptedAtMs;
    private int attempts;

    public Decision inspect(String name, boolean worn, long now)
    {
        if (name == null || worn)
        {
            reset();
            return Decision.ALREADY_EQUIPPED;
        }
        if (!name.equalsIgnoreCase(itemName))
        {
            reset();
            itemName = name;
        }
        if (attempts >= MAX_ATTEMPTS)
        {
            if (now - attemptedAtMs < FAILURE_COOLDOWN_MS) return Decision.COOLDOWN;
            attempts = 0;
        }
        if (attemptedAtMs > 0L && now - attemptedAtMs < ACK_TIMEOUT_MS)
            return Decision.WAIT;
        return Decision.TRY;
    }

    public void dispatched(String name, long now)
    {
        if (name == null) return;
        if (!name.equalsIgnoreCase(itemName))
        {
            reset();
            itemName = name;
        }
        attempts++;
        attemptedAtMs = now;
    }

    public void reset()
    {
        itemName = null;
        attemptedAtMs = 0L;
        attempts = 0;
    }

    public int getAttempts() { return attempts; }
}
