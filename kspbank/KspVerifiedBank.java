package net.runelite.client.plugins.microbot.kspbank;

import net.runelite.api.GameObject;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;

/**
 * Central bank-target validation for KSP plugins.
 *
 * Generic KSP banking intentionally interacts only with an exact Banker NPC or
 * an exact Bank booth object. It never performs fuzzy name matching on "Bank".
 * Location-specific plugins may still use an explicitly verified object ID.
 */
public final class KspVerifiedBank
{
    private KspVerifiedBank() {}

    public static boolean openBank()
    {
        if (Rs2Bank.isOpen()) return true;

        Rs2NpcModel banker = Rs2Npc.getBankerNPC();
        if (banker != null
                && banker.getName() != null
                && "Banker".equalsIgnoreCase(banker.getName())
                && Rs2Npc.interact(banker, "Bank"))
        {
            return true;
        }

        GameObject booth = Rs2GameObject.get("Bank booth", true);
        return booth != null && Rs2GameObject.interact(booth, "Bank");
    }

    /**
     * Travel may use Microbot's bank location routing, but the final interaction
     * is always re-acquired through the strict target allow-list above.
     */
    public static boolean walkToBankAndOpenBank()
    {
        if (Rs2Bank.isOpen()) return true;
        if (openBank()) return true;
        if (!Rs2Bank.walkToBank()) return false;
        if (Rs2Bank.isOpen()) return true;
        return openBank();
    }
}
