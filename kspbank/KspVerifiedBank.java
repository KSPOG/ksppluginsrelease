package net.runelite.client.plugins.microbot.kspbank;

import net.runelite.api.GameObject;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Central bank-target validation for KSP plugins.
 *
 * Banking prefers a verified Bank booth object before considering a Banker NPC.
 * Object ID 10355 is checked first so Varrock-style banking does not repeatedly
 * target an unreachable banker behind the booth.
 */
public final class KspVerifiedBank
{
    private static final int PREFERRED_BANK_BOOTH_ID = 10355;
    private static final int BANK_OPEN_TIMEOUT_MS = 2_500;

    private KspVerifiedBank() {}

    public static boolean openBank()
    {
        if (Rs2Bank.isOpen()) return true;

        // Prefer the exact booth requested by AutoMining. If it exists in the
        // scene, never fall through to a Banker NPC during this interaction.
        GameObject preferredBooth = Rs2GameObject.getGameObject(PREFERRED_BANK_BOOTH_ID);
        if (preferredBooth != null)
        {
            if (!Rs2GameObject.interact(preferredBooth, "Bank")) return false;
            return sleepUntil(Rs2Bank::isOpen, BANK_OPEN_TIMEOUT_MS);
        }

        // Other KSP plugins can still use a verified Bank booth at banks whose
        // booth object has a different revision/location-specific ID.
        GameObject booth = Rs2GameObject.get("Bank booth", true);
        if (booth != null)
        {
            if (!Rs2GameObject.interact(booth, "Bank")) return false;
            return sleepUntil(Rs2Bank::isOpen, BANK_OPEN_TIMEOUT_MS);
        }

        // Banker is now a final fallback only for banks without a booth object.
        Rs2NpcModel banker = Rs2Npc.getBankerNPC();
        if (banker != null
                && banker.getName() != null
                && "Banker".equalsIgnoreCase(banker.getName())
                && Rs2Npc.interact(banker, "Bank"))
        {
            return sleepUntil(Rs2Bank::isOpen, BANK_OPEN_TIMEOUT_MS);
        }

        return false;
    }

    /**
     * Walk fully into bank interaction range before attempting the final target.
     * This avoids repeatedly invoking Bank against a target that is not reachable
     * yet while fast plugin loops are still approaching the bank.
     */
    public static boolean walkToBankAndOpenBank()
    {
        if (Rs2Bank.isOpen()) return true;
        if (!Rs2Bank.walkToBank()) return false;
        if (Rs2Bank.isOpen()) return true;
        return openBank();
    }
}
