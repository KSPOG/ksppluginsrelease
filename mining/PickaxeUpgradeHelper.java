package net.runelite.client.plugins.microbot.mining;

import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.Locale;

/** Handles pickaxe preservation and upgrades while the bank is open. */
public final class PickaxeUpgradeHelper
{
    private PickaxeUpgradeHelper() {}

    private static final Pickaxe[] PICKAXES = new Pickaxe[] {
            new Pickaxe("Bronze pickaxe", 1, 1, false),
            new Pickaxe("Iron pickaxe", 1, 1, false),
            new Pickaxe("Steel pickaxe", 6, 5, false),
            new Pickaxe("Black pickaxe", 11, 10, false),
            new Pickaxe("Mithril pickaxe", 21, 20, false),
            new Pickaxe("Adamant pickaxe", 31, 30, false),
            new Pickaxe("Rune pickaxe", 41, 40, false),
            new Pickaxe("Dragon pickaxe", 61, 60, true),
            new Pickaxe("Infernal pickaxe", 61, 60, true),
            new Pickaxe("Crystal pickaxe", 71, 70, true)
    };

    /**
     * Returns true when any recognized pickaxe is equipped or carried.
     * Inventory detection also accepts future or unknown pickaxes by name.
     */
    public static boolean hasPickaxeEquippedOrInInventory()
    {
        for (Pickaxe pickaxe : PICKAXES)
        {
            if (Rs2Equipment.isWearing(pickaxe.name))
            {
                return true;
            }
        }

        return hasInventoryPickaxe();
    }

    /**
     * Withdraws the best banked pickaxe the account can use for mining.
     * Equipment is preferred when the Attack requirement is met; otherwise
     * one pickaxe is withdrawn and retained in inventory.
     */
    public static boolean withdrawBestPickaxeForMining()
    {
        try
        {
            if (hasPickaxeEquippedOrInInventory())
            {
                return true;
            }

            if (!Rs2Bank.isOpen())
            {
                return false;
            }

            final int miningLevel = Rs2Player.getRealSkillLevel(Skill.MINING);
            final int attackLevel = Rs2Player.getRealSkillLevel(Skill.ATTACK);
            final Pickaxe best = bestAvailableMiningPickaxeInBank(miningLevel);

            if (best == null)
            {
                return false;
            }

            Microbot.status = "Withdrawing " + best.name + "...";
            final boolean canEquip = attackLevel >= best.attackLevel;
            final boolean actionStarted = canEquip
                    ? Rs2Bank.withdrawAndEquip(best.name)
                    : Rs2Bank.withdrawOne(best.name, true);

            if (!actionStarted)
            {
                return false;
            }

            return waitUntil(() -> Rs2Equipment.isWearing(best.name)
                            || Rs2Inventory.hasItem(best.name),
                    2_400L);
        }
        catch (Throwable throwable)
        {
            Microbot.log("Startup pickaxe preparation failed: " + throwable.getMessage());
            return false;
        }
    }

    /**
     * Equips the best pickaxe already carried in inventory when possible.
     * This is called before clicking Deposit inventory so the active tool is
     * normally preserved by being in the weapon slot.
     */
    public static void equipBestCarriedPickaxeIfPossible()
    {
        if (!Rs2Bank.isOpen())
        {
            return;
        }

        final int miningLevel = Rs2Player.getRealSkillLevel(Skill.MINING);
        final int attackLevel = Rs2Player.getRealSkillLevel(Skill.ATTACK);
        final Pickaxe carried = bestAvailablePickaxe(miningLevel, attackLevel, false);

        if (carried == null || Rs2Equipment.isWearing(carried.name))
        {
            return;
        }

        if (Rs2Inventory.hasItem(carried.name))
        {
            Microbot.status = "Equipping " + carried.name + "...";
            Rs2Bank.wearItem(carried.name, true);
            waitUntil(() -> Rs2Equipment.isWearing(carried.name), 1_800L);
        }
    }

    /**
     * After Deposit inventory, selects the highest-level pickaxe that exists in
     * the bank/equipment and can actually be equipped by this account.
     *
     * When an upgrade is equipped, the previous weapon is moved to inventory;
     * every inventory pickaxe is then deposited, leaving only the upgraded tool
     * equipped.
     */
    public static boolean ensureBestPickaxeAfterDeposit()
    {
        try
        {
            if (!Rs2Bank.isOpen())
            {
                return false;
            }

            final int miningLevel = Rs2Player.getRealSkillLevel(Skill.MINING);
            final int attackLevel = Rs2Player.getRealSkillLevel(Skill.ATTACK);
            final Pickaxe best = bestAvailablePickaxe(miningLevel, attackLevel, true);

            if (best == null)
            {
                return false;
            }

            if (!Rs2Equipment.isWearing(best.name))
            {
                Microbot.status = "Upgrading to " + best.name + "...";

                boolean equipped = false;
                if (Rs2Inventory.hasItem(best.name))
                {
                    equipped = Rs2Bank.wearItem(best.name, true);
                }
                else if (Rs2Bank.hasBankItem(best.name, true))
                {
                    equipped = Rs2Bank.withdrawAndEquip(best.name);
                }

                if (!equipped || !waitUntil(() -> Rs2Equipment.isWearing(best.name), 2_400L))
                {
                    return false;
                }
            }

            // Equipping the upgrade displaces the obsolete pickaxe into the
            // inventory. Deposit every inventory pickaxe now that the retained
            // best pickaxe is safely equipped.
            depositAllInventoryPickaxes();

            return waitUntil(() -> Rs2Equipment.isWearing(best.name)
                            && !hasInventoryPickaxe(),
                    2_400L);
        }
        catch (Throwable throwable)
        {
            Microbot.log("Pickaxe upgrade failed: " + throwable.getMessage());
            return false;
        }
    }

    /** Compatibility entry point retained for any external callers. */
    public static void tryUpgradePickaxe()
    {
        ensureBestPickaxeAfterDeposit();
    }

    public static boolean isPickaxeName(String name)
    {
        if (name == null)
        {
            return false;
        }

        final String normalized = name.toLowerCase(Locale.ROOT).trim();
        for (Pickaxe pickaxe : PICKAXES)
        {
            if (normalized.equals(pickaxe.name.toLowerCase(Locale.ROOT)))
            {
                return true;
            }
        }
        return normalized.contains("pickaxe");
    }

    private static Pickaxe bestAvailableMiningPickaxeInBank(int miningLevel)
    {
        Pickaxe best = null;

        for (Pickaxe pickaxe : PICKAXES)
        {
            if (pickaxe.miningLevel > miningLevel
                    || (pickaxe.membersOnly && !isMembersWorld()))
            {
                continue;
            }

            if (Rs2Bank.hasBankItem(pickaxe.name, true))
            {
                best = pickaxe;
            }
        }

        return best;
    }

    private static Pickaxe bestAvailablePickaxe(int miningLevel, int attackLevel, boolean includeBank)
    {
        Pickaxe best = null;

        for (Pickaxe pickaxe : PICKAXES)
        {
            if (pickaxe.miningLevel > miningLevel
                    || pickaxe.attackLevel > attackLevel
                    || (pickaxe.membersOnly && !isMembersWorld()))
            {
                continue;
            }

            final boolean available = Rs2Equipment.isWearing(pickaxe.name)
                    || Rs2Inventory.hasItem(pickaxe.name)
                    || (includeBank && Rs2Bank.hasBankItem(pickaxe.name, true));

            if (available)
            {
                best = pickaxe;
            }
        }

        return best;
    }

    private static void depositAllInventoryPickaxes()
    {
        if (!hasInventoryPickaxe())
        {
            return;
        }

        Microbot.status = "Depositing outdated pickaxe...";
        Rs2Bank.depositAll(item -> item != null
                && item.getName() != null
                && isPickaxeName(item.getName()));
        Rs2Inventory.waitForInventoryChanges(1_800);
    }

    private static boolean hasInventoryPickaxe()
    {
        return Rs2Inventory.items().anyMatch(item -> item != null
                && item.getName() != null
                && isPickaxeName(item.getName()));
    }

    private static boolean isMembersWorld()
    {
        try
        {
            return Microbot.getClient() != null
                    && Microbot.getClient().getWorldType().contains(WorldType.MEMBERS);
        }
        catch (Throwable ignored)
        {
            return false;
        }
    }

    private static boolean waitUntil(Check check, long timeoutMs)
    {
        final long end = System.currentTimeMillis() + Math.max(0L, timeoutMs);
        while (System.currentTimeMillis() < end)
        {
            if (check.get())
            {
                return true;
            }
            sleep(50L);
        }
        return check.get();
    }

    private static void sleep(long ms)
    {
        try
        {
            Thread.sleep(ms);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    private interface Check
    {
        boolean get();
    }

    private static final class Pickaxe
    {
        private final String name;
        private final int miningLevel;
        private final int attackLevel;
        private final boolean membersOnly;

        private Pickaxe(String name, int miningLevel, int attackLevel, boolean membersOnly)
        {
            this.name = name;
            this.miningLevel = miningLevel;
            this.attackLevel = attackLevel;
            this.membersOnly = membersOnly;
        }
    }
}
