package net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/**
 * Centralized Account Builder bank access.
 *
 * Cooking Guild banking is deliberately excluded. Generic nearest-bank and
 * direct-open calls can otherwise select the bank booth/banker inside the
 * Cooking Guild, so those cases are redirected to Varrock West.
 */
public final class KspBankAccess
{
    private static final long DIRECT_OPEN_COOLDOWN_MS = 750L;
    private static final long WALK_OPEN_COOLDOWN_MS = 2_000L;
    private static final int COOKS_GUILD_EXCLUSION_RADIUS = 24;

    private static long lastDirectOpenAttemptAtMs;
    private static long lastWalkOpenAttemptAtMs;
    private static BankLocation lastWalkLocation;
    private static boolean lastWalkUsedNearestBank;

    private KspBankAccess()
    {
    }

    public static synchronized boolean openBankDirectFirst()
    {
        if (Rs2Bank.isOpen())
        {
            reset();
            return true;
        }

        BankLocation nearest = safeNearestBank();
        if (!isCookingGuildBankRisk(nearest) && tryDirectOpen())
        {
            return true;
        }

        if (Rs2Player.isMoving() || Rs2Player.isInteracting())
        {
            return Rs2Bank.isOpen();
        }

        return tryWalkOpen(nearest, true);
    }

    public static synchronized boolean openBankDirectFirst(BankLocation requestedLocation)
    {
        if (Rs2Bank.isOpen())
        {
            reset();
            return true;
        }

        BankLocation safeLocation = sanitizeBankLocation(requestedLocation);
        boolean cookingGuildRisk = requestedLocation == BankLocation.COOKS_GUILD
                || isInsideCookingGuildExclusionArea();

        if (!cookingGuildRisk && tryDirectOpen())
        {
            return true;
        }

        if (Rs2Player.isMoving() || Rs2Player.isInteracting())
        {
            return Rs2Bank.isOpen();
        }

        return tryWalkOpen(safeLocation, false);
    }

    public static synchronized void reset()
    {
        lastDirectOpenAttemptAtMs = 0L;
        lastWalkOpenAttemptAtMs = 0L;
        lastWalkLocation = null;
        lastWalkUsedNearestBank = false;
    }

    private static boolean tryDirectOpen()
    {
        if (isInsideCookingGuildExclusionArea())
        {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now - lastDirectOpenAttemptAtMs < DIRECT_OPEN_COOLDOWN_MS)
        {
            return Rs2Bank.isOpen();
        }

        lastDirectOpenAttemptAtMs = now;
        return Rs2Bank.openBank() || Rs2Bank.isOpen();
    }

    private static boolean tryWalkOpen(BankLocation location, boolean nearestBankRequested)
    {
        BankLocation safeLocation = sanitizeBankLocation(
                nearestBankRequested || location == null ? safeNearestBank() : location);

        long now = System.currentTimeMillis();
        boolean sameRequest = lastWalkUsedNearestBank == nearestBankRequested
                && safeLocation == lastWalkLocation;

        if (sameRequest && now - lastWalkOpenAttemptAtMs < WALK_OPEN_COOLDOWN_MS)
        {
            return Rs2Bank.isOpen();
        }

        lastWalkOpenAttemptAtMs = now;
        lastWalkLocation = safeLocation;
        lastWalkUsedNearestBank = nearestBankRequested;

        return safeLocation != null
                && (Rs2Bank.walkToBankAndUseBank(safeLocation) || Rs2Bank.isOpen());
    }

    private static BankLocation safeNearestBank()
    {
        return sanitizeBankLocation(Rs2Bank.getNearestBank());
    }

    private static BankLocation sanitizeBankLocation(BankLocation location)
    {
        return location == BankLocation.COOKS_GUILD
                ? BankLocation.VARROCK_WEST
                : location;
    }

    private static boolean isCookingGuildBankRisk(BankLocation location)
    {
        return location == BankLocation.COOKS_GUILD || isInsideCookingGuildExclusionArea();
    }

    private static boolean isInsideCookingGuildExclusionArea()
    {
        WorldPoint player = Rs2Player.getWorldLocation();
        WorldPoint cooksGuildBank = BankLocation.COOKS_GUILD.getWorldPoint();

        return player != null
                && player.getPlane() == cooksGuildBank.getPlane()
                && player.distanceTo2D(cooksGuildBank) <= COOKS_GUILD_EXCLUSION_RADIUS;
    }
}
