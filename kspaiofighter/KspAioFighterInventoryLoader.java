package net.runelite.client.plugins.microbot.kspaiofighter;


import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Singleton
final class KspAioFighterInventoryLoader
{
    private final KspAioFighterConfig config;
    private final KspAioFighterInventorySettings settings;
    private volatile String lastError = "";

    @Inject
    KspAioFighterInventoryLoader(KspAioFighterConfig config, KspAioFighterInventorySettings settings)
    {
        this.config = config;
        this.settings = settings;
    }

    String getLastError()
    {
        return lastError == null ? "" : lastError;
    }

    boolean loadActiveSetup()
    {
        KspAioFighterGearStyle style = activeStyle();
        return style == null || load(style);
    }

    boolean load(KspAioFighterGearStyle style)
    {
        lastError = "";
        if (style == null || !settings.isEnabled(style)) return true;

        List<KspAioFighterInventoryItem> setup = settings.get(style);
        if (setup.isEmpty()) return true;
        if (!Microbot.isLoggedIn())
        {
            lastError = "Log in before loading an inventory setup.";
            return false;
        }
        if (matchesExactly(setup)) return true;

        Microbot.status = "KSP AIO Fighter: loading " + style + " inventory setup";
        if (!Rs2Bank.walkToBankAndUseBank())
        {
            lastError = "Could not open a bank to load the " + style + " inventory setup.";
            return false;
        }

        Map<InventoryKey, Integer> requested = aggregate(setup);

        // Depositing normalizes noted/unnoted copies into bank stock. Validate by
        // display name here, but keep the actual withdrawal plan keyed by item ID.
        Map<String, Integer> stockNeeded = new LinkedHashMap<>();
        for (Map.Entry<InventoryKey, Integer> entry : requested.entrySet())
        {
            stockNeeded.merge(entry.getKey().normalizedName(), entry.getValue(), Integer::sum);
        }

        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : stockNeeded.entrySet())
        {
            String name = entry.getKey();
            int bankQuantity = Rs2Bank.count(name, true);
            int inventoryQuantity = Rs2Inventory.itemQuantity(name, true);
            if ((long) bankQuantity + inventoryQuantity < entry.getValue())
            {
                missing.add(name + " x" + entry.getValue()
                    + " (inventory: " + inventoryQuantity + ", bank: " + bankQuantity + ")");
            }
        }

        if (!missing.isEmpty())
        {
            Rs2Bank.closeBank();
            lastError = "Missing saved inventory item(s): " + String.join(", ", missing);
            return false;
        }

        if (!Rs2Inventory.isEmpty() && (!Rs2Bank.depositAll()
            || !sleepUntil(Rs2Inventory::isEmpty, 2_500)))
        {
            Rs2Bank.closeBank();
            lastError = "Could not clear the current inventory before loading the saved setup.";
            return false;
        }

        for (Map.Entry<InventoryKey, Integer> entry : requested.entrySet())
        {
            InventoryKey key = entry.getKey();
            boolean modeReady = key.noted ? Rs2Bank.setWithdrawAs(true) : Rs2Bank.setWithdrawAsItem();
            if (!modeReady)
            {
                Rs2Bank.closeBank();
                lastError = "Could not set the bank withdraw mode for " + key.name + ".";
                return false;
            }

            int expectedQuantity = entry.getValue();
            Microbot.status = "KSP AIO Fighter: withdrawing " + expectedQuantity + " " + key.name;
            Rs2Bank.withdrawX(true, key.name, expectedQuantity, true);

            // Bank actions are asynchronous. Do not fire the next withdrawal (or
            // close the bank) until this exact saved item ID is actually present.
            if (!sleepUntil(() -> inventoryQuantity(key.id) >= expectedQuantity, 3_000))
            {
                Rs2Bank.setWithdrawAsItem();
                Rs2Bank.closeBank();
                lastError = "Could not withdraw the saved " + style + " item "
                    + key.name + " x" + expectedQuantity + " (item id " + key.id + ").";
                return false;
            }
        }

        // Give the client one final inventory update while the bank is still open.
        boolean complete = matchesExactly(setup) || sleepUntil(() -> matchesExactly(setup), 2_500);
        Rs2Bank.setWithdrawAsItem();
        Rs2Bank.closeBank();

        if (!complete)
        {
            lastError = "The bank load finished, but the inventory does not match the saved " + style + " setup.";
            return false;
        }

        Microbot.status = "KSP AIO Fighter: " + style + " inventory setup loaded";
        return true;
    }

    private boolean matchesExactly(List<KspAioFighterInventoryItem> setup)
    {
        Map<Integer, Integer> wanted = new LinkedHashMap<>();
        for (KspAioFighterInventoryItem item : setup)
        {
            wanted.merge(item.getId(), item.getQuantity(), Integer::sum);
        }

        Map<Integer, Integer> actual = new LinkedHashMap<>();
        for (Rs2ItemModel item : Rs2Inventory.getList(value -> value != null && value.getId() > 0))
        {
            actual.merge(item.getId(), Math.max(1, item.getQuantity()), Integer::sum);
        }
        return wanted.equals(actual);
    }

    private int inventoryQuantity(int itemId)
    {
        int quantity = 0;
        for (Rs2ItemModel item : Rs2Inventory.getList(value -> value != null && value.getId() == itemId))
        {
            quantity += Math.max(1, item.getQuantity());
        }
        return quantity;
    }

    private Map<InventoryKey, Integer> aggregate(List<KspAioFighterInventoryItem> setup)
    {
        Map<InventoryKey, Integer> result = new LinkedHashMap<>();
        for (KspAioFighterInventoryItem item : setup)
        {
            InventoryKey key = new InventoryKey(item.getId(), item.getName(), item.isNoted());
            result.merge(key, item.getQuantity(), Integer::sum);
        }
        return result;
    }

    KspAioFighterGearStyle activeStyle()
    {
        if (config.trainAttack() && level(Skill.ATTACK) < config.attackTarget()) return KspAioFighterGearStyle.ATTACK;
        if (config.trainStrength() && level(Skill.STRENGTH) < config.strengthTarget()) return KspAioFighterGearStyle.STRENGTH;
        if (config.trainDefence() && level(Skill.DEFENCE) < config.defenceTarget()) return KspAioFighterGearStyle.DEFENCE;
        if (config.trainRanged() && level(Skill.RANGED) < config.rangedTarget()) return KspAioFighterGearStyle.RANGED;
        if (config.trainMagic() && level(Skill.MAGIC) < config.magicTarget()) return KspAioFighterGearStyle.MAGIC;

        // Useful for manually disabled targets at the level cap and for loading a setup
        // before the fighter itself decides there is no remaining level-target work.
        if (config.trainAttack()) return KspAioFighterGearStyle.ATTACK;
        if (config.trainStrength()) return KspAioFighterGearStyle.STRENGTH;
        if (config.trainDefence()) return KspAioFighterGearStyle.DEFENCE;
        if (config.trainRanged()) return KspAioFighterGearStyle.RANGED;
        if (config.trainMagic()) return KspAioFighterGearStyle.MAGIC;
        return null;
    }

    private int level(Skill skill)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
            Microbot.getClient() == null ? 1 : Microbot.getClient().getRealSkillLevel(skill)).orElse(1);
    }

    private static final class InventoryKey
    {
        private final int id;
        private final String name;
        private final boolean noted;

        private InventoryKey(int id, String name, boolean noted)
        {
            this.id = id;
            this.name = name == null ? "" : name.trim();
            this.noted = noted;
        }

        private String normalizedName()
        {
            return name.toLowerCase(java.util.Locale.ROOT);
        }

        @Override
        public boolean equals(Object other)
        {
            if (this == other) return true;
            if (!(other instanceof InventoryKey)) return false;
            InventoryKey key = (InventoryKey) other;
            return id == key.id && noted == key.noted && name.equalsIgnoreCase(key.name);
        }

        @Override
        public int hashCode()
        {
            int result = Integer.hashCode(id);
            result = 31 * result + normalizedName().hashCode();
            result = 31 * result + Boolean.hashCode(noted);
            return result;
        }
    }
}
