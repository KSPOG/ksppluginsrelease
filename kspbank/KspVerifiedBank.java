package net.runelite.client.plugins.microbot.kspbank;

import net.runelite.api.GameObject;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Central bank-target validation for KSP plugins.
 *
 * General KSP banking prefers a verified Bank booth before considering a Banker NPC.
 * AutoMining relies on that order so it does not repeatedly target unreachable bankers
 * behind Varrock-style booths.
 *
 * KSP AIO Factory is intentionally different: its bank-standing/GE workflow must use
 * Banker NPCs. Factory calls are detected here so the Factory never falls into the
 * booth/scene-object scan that was causing interrupted client-thread failures.
 */
public final class KspVerifiedBank
{
    private static final int PREFERRED_BANK_BOOTH_ID = 10355;
    private static final int BANK_OPEN_TIMEOUT_MS = 2_500;
    private static final String AIO_FACTORY_SCRIPT_CLASS =
            "net.runelite.client.plugins.microbot.f2pprocessingfactory.F2PProcessingFactoryScript";

    private KspVerifiedBank() {}

    public static boolean openBank()
    {
        if (Rs2Bank.isOpen()) return true;

        // AIO Factory is a GE/bank-standing workflow. It must interact with a Banker
        // directly and must not scan Bank booth / GE booth scene objects.
        if (isAioFactoryCaller())
        {
            return openAioFactoryBankViaBanker();
        }

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

        // Banker is a final fallback for normal KSP callers at banks without booths.
        Rs2NpcModel banker = Rs2Npc.getBankerNPC();
        if (banker != null && Rs2Npc.interact(banker, "Bank"))
        {
            return sleepUntil(Rs2Bank::isOpen, BANK_OPEN_TIMEOUT_MS);
        }

        return false;
    }

    /**
     * AIO Factory banker-only bank opening.
     *
     * The Factory's old fallback was Rs2Bank.walkToBank(), which discovers the nearest
     * bank by scanning Bank/GE scene objects. The live failure log shows that scan being
     * interrupted inside Rs2GameObject.findGrandExchangeBooth/findBank. Instead, locate
     * a Banker NPC directly. If no banker is currently loaded, route toward the Grand
     * Exchange and let the Factory's next tick retry the Banker interaction.
     *
     * Returning true after a GE walk is intentional: F2PProcessingFactoryScript treats
     * true as "bank transition handled" and waits for the bank root before retrying. This
     * prevents it from dropping into its obsolete Rs2Bank.walkToBank() object-scan path.
     */
    private static boolean openAioFactoryBankViaBanker()
    {
        Rs2NpcModel banker = Rs2Npc.getBankerNPC();
        if (banker != null)
        {
            if (!Rs2Npc.interact(banker, "Bank")) return true;
            return sleepUntil(Rs2Bank::isOpen, BANK_OPEN_TIMEOUT_MS);
        }

        // Factory already trades at the GE, so use the GE route rather than generic
        // bank-object discovery. Once the area/NPCs load, retry the Banker immediately.
        Rs2GrandExchange.walkToGrandExchange();
        banker = Rs2Npc.getBankerNPC();
        if (banker != null)
        {
            if (!Rs2Npc.interact(banker, "Bank")) return true;
            return sleepUntil(Rs2Bank::isOpen, BANK_OPEN_TIMEOUT_MS);
        }

        // Suppress the Factory's Rs2Bank.walkToBank() fallback. Its next 600 ms tick
        // will retry this banker-only path while the GE route/scene finishes loading.
        return true;
    }

    private static boolean isAioFactoryCaller()
    {
        for (StackTraceElement frame : Thread.currentThread().getStackTrace())
        {
            if (AIO_FACTORY_SCRIPT_CLASS.equals(frame.getClassName()))
            {
                return true;
            }
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
