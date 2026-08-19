package net.runelite.client.plugins.microbot.kspbryophyta;

import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class KspBryophytaScript extends Script
{
    private static final int BRYOPHYTA_NPC_ID = 8195;
    private static final int GROWTHLING_NPC_ID = 8194;
    private static final int BRYOPHYTA_CHEST_OBJECT_ID = 56378;

    private static final int LOOP_DELAY_MS = 400;
    private static final long BOSS_MISSING_CONFIRM_MS = 1200L;
    private static final long POST_KILL_LOOT_WINDOW_MS = 2400L;
    private static final long GROWTHLING_CLICK_COOLDOWN_MS = 350L;
    private static final long ENTRY_RETRY_MS = 1800L;
    private static final long CHEST_RETRY_MS = 1800L;
    private static final long ALTAR_RETRY_MS = 1400L;
    private static final long MANHOLE_RETRY_MS = 1400L;

    private static final String AIR_RUNE = "Air rune";
    private static final String FIRE_RUNE = "Fire rune";
    private static final String LAW_RUNE = "Law rune";
    private static final String MOSSY_KEY = "Mossy key";

    private static final WorldPoint VARROCK_MANHOLE = new WorldPoint(3237, 3458, 0);
    private static final WorldPoint BRYOPHYTA_SEWER_ENTRANCE = new WorldPoint(3174, 9901, 0);

    // User supplied: new Area(3252, 3488, 3259, 3471)
    // WorldArea takes south-west + width/height, so the inclusive bounds are x=3252..3259, y=3471..3488.
    private static final WorldArea VARROCK_ALTAR_AREA =
            new WorldArea(new WorldPoint(3252, 3471, 0), 8, 18);
    private static final WorldPoint VARROCK_ALTAR_TARGET = new WorldPoint(3255, 3480, 0);

    private KspBryophytaConfig config;
    private final BryophytaEquipmentSettings equipmentSettings;

    private volatile BryophytaState state = BryophytaState.STARTING;
    private volatile String status = "Starting...";
    private volatile int kills;
    private volatile int mossyKeys;
    private volatile int chestAttempts;
    private volatile int foodRemaining;
    private volatile int prayerPoints;

    private String mainWeapon = "";
    private boolean bossWasPresent;
    private boolean killRegisteredForCycle;
    private boolean restockRequired;
    private boolean prayerRestoredAfterBank;
    private boolean loadoutVerified;

    private int entryFailures;
    private int altarFailures;

    private long lastBossSeenAt;
    private long killRegisteredAt;
    private long lastGrowthlingClickAt;
    private long lastMagicCastAt;
    private long lastEntryAttemptAt;
    private long lastChestAttemptAt;
    private long lastAltarAttemptAt;
    private long lastManholeAttemptAt;

    @Inject
    public KspBryophytaScript(BryophytaEquipmentSettings equipmentSettings)
    {
        this.equipmentSettings = equipmentSettings;
    }

    public boolean run(KspBryophytaConfig config)
    {
        this.config = config;
        resetRuntimeState();
        Microbot.enableAutoRunOn = true;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            try
            {
                if (!Microbot.isLoggedIn() || state == BryophytaState.STOPPED)
                {
                    return;
                }

                if (!super.run())
                {
                    return;
                }

                updateCounters();

                if (!validateStaticRequirements())
                {
                    return;
                }

                boolean insideLair = isInsideLair();

                if (insideLair)
                {
                    handleInsideLair();
                }
                else
                {
                    handleOutsideLair();
                }
            }
            catch (Exception ex)
            {
                Microbot.logStackTrace(getClass().getSimpleName(), ex);
                failAndStop("Unexpected error: " + ex.getClass().getSimpleName());
            }
        }, 0, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);

        return true;
    }

    private void resetRuntimeState()
    {
        state = BryophytaState.STARTING;
        status = "Starting - first action is a full Varrock restock.";
        kills = 0;
        chestAttempts = 0;
        mossyKeys = 0;
        foodRemaining = 0;
        prayerPoints = 0;

        bossWasPresent = false;
        killRegisteredForCycle = false;
        restockRequired = true;
        prayerRestoredAfterBank = false;
        loadoutVerified = false;
        mainWeapon = equipmentSettings.mainWeaponFor(config.strategy());

        entryFailures = 0;
        altarFailures = 0;

        lastBossSeenAt = 0L;
        killRegisteredAt = 0L;
        lastGrowthlingClickAt = 0L;
        lastMagicCastAt = 0L;
        lastEntryAttemptAt = 0L;
        lastChestAttemptAt = 0L;
        lastAltarAttemptAt = 0L;
        lastManholeAttemptAt = 0L;
    }

    private void updateCounters()
    {
        mossyKeys = Rs2Inventory.itemQuantity(MOSSY_KEY);
        foodRemaining = Rs2Inventory.itemQuantity(normalize(config.foodName()));
        prayerPoints = Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER);
    }

    private boolean validateStaticRequirements()
    {
        if (Microbot.getClient().getRealSkillLevel(Skill.MAGIC) < 25)
        {
            failAndStop("Varrock Teleport requires level 25 Magic.");
            return false;
        }

        if (config.protectFromMagic()
                && Microbot.getClient().getRealSkillLevel(Skill.PRAYER) < 37)
        {
            failAndStop("Protect from Magic is enabled but Prayer is below level 37.");
            return false;
        }

        if (normalize(config.foodName()).isEmpty())
        {
            failAndStop("Food name cannot be empty.");
            return false;
        }

        if (normalize(config.growthlingToolName()).isEmpty())
        {
            failAndStop("Growthling axe name cannot be empty.");
            return false;
        }

        return true;
    }

    private void handleInsideLair()
    {
        if (restockRequired || shouldEmergencyRestock())
        {
            restockRequired = true;
            handleVarrockTeleport("Restocking from Bryophyta...");
            return;
        }

        if (!loadoutVerified && !verifyEquipmentLoadout())
        {
            // We cannot repair a missing loadout from inside the instance; return to Varrock first.
            restockRequired = true;
            handleVarrockTeleport("Loadout changed - returning to Varrock...");
            return;
        }

        handleSurvival();

        Rs2NpcModel growthling = getNearestGrowthling();
        if (growthling != null)
        {
            handleGrowthlings(growthling);
            return;
        }

        restoreMainWeaponIfNeeded();

        Rs2NpcModel bryophyta = getBryophyta();
        long now = System.currentTimeMillis();

        if (bryophyta != null && !bryophyta.isDead())
        {
            bossWasPresent = true;
            lastBossSeenAt = now;
            killRegisteredForCycle = false;
            handleBryophyta(bryophyta);
            return;
        }

        if (bossWasPresent
                && !killRegisteredForCycle
                && now - lastBossSeenAt >= BOSS_MISSING_CONFIRM_MS)
        {
            registerKill();
        }

        if (killRegisteredForCycle)
        {
            handlePostKill(now);
            return;
        }

        setState(BryophytaState.WAITING_FOR_RESPAWN, "Waiting for Bryophyta...");
    }

    private void handleOutsideLair()
    {
        Rs2Prayer.disableAllPrayers();

        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null)
        {
            return;
        }

        if (restockRequired)
        {
            if (isUnderground(player))
            {
                handleVarrockTeleport("Returning to Varrock for restock...");
                return;
            }

            performBankRestock();
            return;
        }

        if (!prayerRestoredAfterBank)
        {
            if (isUnderground(player))
            {
                restockRequired = true;
                handleVarrockTeleport("Prayer was not restored - returning to Varrock...");
                return;
            }

            restorePrayerAtVarrockAltar();
            return;
        }

        if (!loadoutVerified || !verifyEquipmentLoadout())
        {
            restockRequired = true;
            loadoutVerified = false;
            return;
        }

        if (!hasMinimumTripSupplies())
        {
            restockRequired = true;
            return;
        }

        if (!config.autoEnterLair())
        {
            setState(BryophytaState.WAITING_AT_ENTRANCE, "Restocked and prayer restored; auto navigation disabled.");
            return;
        }

        navigateToBryophyta();
    }

    private boolean shouldEmergencyRestock()
    {
        if (prayerPoints <= config.teleportAtPrayerPoints())
        {
            status = "Prayer threshold reached.";
            return true;
        }

        if (foodRemaining <= config.teleportAtFoodCount())
        {
            status = "Food threshold reached.";
            return true;
        }

        switch (config.strategy())
        {
            case RANGED:
                String ammo = selectedAmmoName();
                return ammo != null && getEquippedQuantity(EquipmentInventorySlot.AMMO, ammo) <= 0;

            case MAGIC_FIRE:
                BryophytaFireSpell spell = config.fireSpell();
                return Rs2Inventory.itemQuantity(AIR_RUNE) < spell.getAirRunesPerCast()
                        || Rs2Inventory.itemQuantity(FIRE_RUNE) < spell.getFireRunesPerCast()
                        || Rs2Inventory.itemQuantity(spell.getCatalystRuneName()) < 1;

            case MELEE:
            default:
                return false;
        }
    }

    private void handleSurvival()
    {
        if (config.protectFromMagic() && !Rs2Prayer.isOutOfPrayer())
        {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, true);
        }

        Rs2Player.eatAt(config.eatAtPercent());

        if (config.maintainPoisonProtection())
        {
            Rs2Player.drinkAntiPoisonPotion();
        }
    }

    private void handleVarrockTeleport(String reason)
    {
        if (!hasVarrockTeleportRunesForOne())
        {
            failAndStop(
                    "Missing Varrock teleport runes. Required for emergency return: 3 Air rune, 1 Fire rune, 1 Law rune."
            );
            return;
        }

        setState(BryophytaState.TELEPORTING_VARROCK, reason);
        Rs2Prayer.disableAllPrayers();

        boolean cast = Rs2Magic.cast(MagicAction.VARROCK_TELEPORT);
        if (!cast)
        {
            failAndStop("Could not cast Varrock Teleport. Check the standard spellbook and rune supply.");
            return;
        }

        boolean landed = sleepUntil(this::isOnSurface, 10_000);
        if (!landed)
        {
            failAndStop("Varrock Teleport did not complete within 10 seconds.");
            return;
        }

        bossWasPresent = false;
        killRegisteredForCycle = false;
        restockRequired = true;
        prayerRestoredAfterBank = false;
        loadoutVerified = false;
        setState(BryophytaState.BANKING, "Varrock reached - heading to East bank.");
    }

    private void performBankRestock()
    {
        setState(BryophytaState.BANKING, "Banking at Varrock East...");

        if (!Rs2Bank.walkToBankAndUseBank(BankLocation.VARROCK_EAST))
        {
            return;
        }

        // Phase 1: clean the inventory, withdraw every missing equipment item, then equip it.
        if (!Rs2Bank.depositAll())
        {
            status = "Waiting for inventory deposit...";
            return;
        }

        Map<EquipmentInventorySlot, String> desired = equipmentSettings.equipmentFor(config.strategy());
        if (!withdrawMissingEquipment(desired))
        {
            return;
        }

        if (!Rs2Bank.closeBank())
        {
            return;
        }

        setState(BryophytaState.EQUIPPING, "Equipping " + config.strategy() + " loadout...");
        if (!unequipExplicitlyEmptySlots())
        {
            return;
        }
        if (!equipDesiredLoadout(desired))
        {
            return;
        }

        mainWeapon = equipmentSettings.mainWeaponFor(config.strategy());

        // Phase 2: displaced equipment is now in the inventory. Bank it and withdraw only trip supplies.
        if (!Rs2Bank.walkToBankAndUseBank(BankLocation.VARROCK_EAST))
        {
            return;
        }

        if (!Rs2Bank.depositAll())
        {
            status = "Waiting for displaced equipment deposit...";
            return;
        }

        if (!withdrawTripSupplies())
        {
            return;
        }

        Rs2Bank.closeBank();

        if (!verifyEquipmentLoadout())
        {
            failAndStop("The required " + config.strategy() + " equipment did not equip correctly.");
            return;
        }

        if (!verifyFullTripSupplies())
        {
            failAndStop("The bank cycle completed but required food/runes/supplies are still missing.");
            return;
        }

        restockRequired = false;
        prayerRestoredAfterBank = false;
        loadoutVerified = true;
        altarFailures = 0;
        setState(BryophytaState.RESTORING_PRAYER, "Restocked - restoring Prayer at Varrock altar.");
    }

    private boolean withdrawMissingEquipment(Map<EquipmentInventorySlot, String> desired)
    {
        for (Map.Entry<EquipmentInventorySlot, String> entry : desired.entrySet())
        {
            EquipmentInventorySlot slot = entry.getKey();
            String itemName = entry.getValue();

            if (slot == EquipmentInventorySlot.AMMO)
            {
                int equipped = getEquippedQuantity(slot, itemName);
                int needed = Math.max(0, config.rangedArrowAmount() - equipped);
                if (needed > 0 && !withdrawExact(itemName, needed, "Ranged ammunition"))
                {
                    return false;
                }
                continue;
            }

            Rs2ItemModel equipped = Rs2Equipment.get(slot);
            if (equipped != null
                    && equipped.getName() != null
                    && equipped.getName().equalsIgnoreCase(itemName))
            {
                continue;
            }

            if (!withdrawExact(itemName, 1, slot.name() + " equipment"))
            {
                return false;
            }
        }

        return true;
    }

    private boolean unequipExplicitlyEmptySlots()
    {
        for (EquipmentInventorySlot slot : BryophytaLoadout.CONFIGURABLE_SLOTS)
        {
            if (!equipmentSettings.isExplicitEmpty(config.strategy(), slot))
            {
                continue;
            }

            if (Rs2Equipment.get(slot) == null)
            {
                continue;
            }

            if (!Rs2Equipment.unEquip(slot))
            {
                failAndStop("Could not remove equipment from slot configured as empty: " + slot.name());
                return false;
            }

            if (!sleepUntil(() -> Rs2Equipment.get(slot) == null, 2500))
            {
                failAndStop("Equipment slot did not become empty: " + slot.name());
                return false;
            }
        }

        return true;
    }

    private boolean equipDesiredLoadout(Map<EquipmentInventorySlot, String> desired)
    {
        for (Map.Entry<EquipmentInventorySlot, String> entry : desired.entrySet())
        {
            EquipmentInventorySlot slot = entry.getKey();
            String itemName = entry.getValue();

            if (slot == EquipmentInventorySlot.AMMO)
            {
                if (Rs2Inventory.itemQuantity(itemName) > 0 && !equipAmmoStack(itemName))
                {
                    failAndStop("Could not equip required ammunition: " + itemName);
                    return false;
                }
                continue;
            }

            if (Rs2Equipment.isWearing(itemName, true))
            {
                continue;
            }

            if (!Rs2Inventory.contains(itemName, true))
            {
                failAndStop("Missing required equipment after bank withdrawal: " + itemName);
                return false;
            }

            if (!interactEquip(itemName))
            {
                failAndStop("Could not equip required item: " + itemName + ". Check skill/quest requirements.");
                return false;
            }
        }

        return true;
    }

    private boolean interactEquip(String itemName)
    {
        boolean interacted = Rs2Inventory.interact(itemName, "Wield", true);
        if (!interacted)
        {
            interacted = Rs2Inventory.interact(itemName, "Wear", true);
        }
        if (!interacted)
        {
            interacted = Rs2Inventory.interact(itemName, "Equip", true);
        }
        if (!interacted)
        {
            return false;
        }

        return sleepUntil(
                () -> Rs2Equipment.isWearing(itemName, true)
                        || !Rs2Inventory.contains(itemName, true),
                2500
        );
    }


    private boolean equipAmmoStack(String itemName)
    {
        int inventoryAmount = Rs2Inventory.itemQuantity(itemName);
        if (inventoryAmount <= 0)
        {
            return getEquippedQuantity(EquipmentInventorySlot.AMMO, itemName) > 0;
        }

        int equippedBefore = getEquippedQuantity(EquipmentInventorySlot.AMMO, itemName);
        boolean interacted = Rs2Inventory.interact(itemName, "Wield", true);
        if (!interacted)
        {
            interacted = Rs2Inventory.interact(itemName, "Wear", true);
        }
        if (!interacted)
        {
            return false;
        }

        int expected = equippedBefore + inventoryAmount;
        return sleepUntil(
                () -> getEquippedQuantity(EquipmentInventorySlot.AMMO, itemName) >= expected
                        || Rs2Inventory.itemQuantity(itemName) == 0,
                3000
        );
    }

    private boolean withdrawTripSupplies()
    {
        String growthlingTool = normalize(config.growthlingToolName());
        if (!withdrawExact(growthlingTool, 1, "Growthling axe"))
        {
            return false;
        }

        int teleports = config.varrockTeleportCount();
        int requiredAir = teleports * 3;
        int requiredFire = teleports;
        int requiredLaw = teleports;

        if (config.strategy() == BryophytaStrategy.MAGIC_FIRE)
        {
            BryophytaFireSpell spell = config.fireSpell();
            requiredAir += spell.getAirRunesPerCast() * config.magicCastSupply();
            requiredFire += spell.getFireRunesPerCast() * config.magicCastSupply();

            if (!withdrawExact(
                    spell.getCatalystRuneName(),
                    config.magicCastSupply(),
                    spell + " catalyst runes"))
            {
                return false;
            }
        }

        if (!withdrawExact(AIR_RUNE, requiredAir, "Varrock/combat Air runes")
                || !withdrawExact(FIRE_RUNE, requiredFire, "Varrock/combat Fire runes")
                || !withdrawExact(LAW_RUNE, requiredLaw, "Varrock Law runes"))
        {
            return false;
        }

        if (!withdrawExact(normalize(config.foodName()), config.foodAmount(), "Food"))
        {
            return false;
        }

        if (config.withdrawMossyKey() && Rs2Bank.hasBankItem(MOSSY_KEY, 1, true))
        {
            // Optional: do not stop if the bank cache races or a key disappears between the check and withdrawal.
            Rs2Bank.withdrawDeficit(MOSSY_KEY, 1, true);
        }

        return true;
    }

    private boolean withdrawExact(String itemName, int amount, String purpose)
    {
        if (amount <= 0)
        {
            return true;
        }

        if (Rs2Bank.withdrawDeficit(itemName, amount, true))
        {
            return true;
        }

        failAndStop("Missing required " + purpose + " in bank: " + itemName + " x" + amount);
        return false;
    }

    private void restorePrayerAtVarrockAltar()
    {
        int realPrayer = Microbot.getClient().getRealSkillLevel(Skill.PRAYER);
        int currentPrayer = Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER);

        if (currentPrayer >= realPrayer)
        {
            prayerRestoredAfterBank = true;
            altarFailures = 0;
            setState(BryophytaState.WALKING_TO_SEWERS, "Prayer full - heading to Varrock Sewers.");
            return;
        }

        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null)
        {
            return;
        }

        setState(BryophytaState.RESTORING_PRAYER, "Restoring Prayer at Varrock altar...");

        if (!VARROCK_ALTAR_AREA.contains(player))
        {
            Rs2Walker.walkTo(VARROCK_ALTAR_TARGET, 3);
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastAltarAttemptAt < ALTAR_RETRY_MS)
        {
            return;
        }
        lastAltarAttemptAt = now;

        boolean prayed = Microbot.getRs2TileObjectCache()
                .query()
                .withName("Altar")
                .where(object -> object.getWorldLocation() != null
                        && VARROCK_ALTAR_AREA.contains(object.getWorldLocation()))
                .interact("Pray-at");

        if (!prayed)
        {
            prayed = Microbot.getRs2TileObjectCache()
                    .query()
                    .withName("Altar")
                    .where(object -> object.getWorldLocation() != null
                            && VARROCK_ALTAR_AREA.contains(object.getWorldLocation()))
                    .interact("Pray");
        }

        if (!prayed)
        {
            altarFailures++;
            if (altarFailures >= 5)
            {
                failAndStop("Could not use an altar inside the configured Varrock altar area.");
            }
            return;
        }

        boolean restored = sleepUntil(
                () -> Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER)
                        >= Microbot.getClient().getRealSkillLevel(Skill.PRAYER),
                4000
        );

        if (restored)
        {
            prayerRestoredAfterBank = true;
            altarFailures = 0;
            setState(BryophytaState.WALKING_TO_SEWERS, "Prayer restored - heading to Varrock Sewers.");
        }
    }

    private void navigateToBryophyta()
    {
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null)
        {
            return;
        }

        if (!isUnderground(player))
        {
            navigateToSewerManhole(player);
            return;
        }

        setState(BryophytaState.WALKING_TO_LAIR, "Walking through Varrock Sewers to Bryophyta...");

        if (handleNearbyWeb())
        {
            return;
        }

        if (tryEnterNearbyLairGate())
        {
            return;
        }

        Rs2Walker.walkTo(BRYOPHYTA_SEWER_ENTRANCE, 5);
    }

    private void navigateToSewerManhole(WorldPoint player)
    {
        setState(BryophytaState.WALKING_TO_SEWERS, "Walking to Varrock sewer manhole...");

        if (player.distanceTo(VARROCK_MANHOLE) > 6)
        {
            Rs2Walker.walkTo(VARROCK_MANHOLE, 4);
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastManholeAttemptAt < MANHOLE_RETRY_MS)
        {
            return;
        }
        lastManholeAttemptAt = now;

        boolean climbed = Microbot.getRs2TileObjectCache()
                .query()
                .withName("Manhole")
                .within(8)
                .interact("Climb-down");

        if (!climbed)
        {
            boolean opened = Microbot.getRs2TileObjectCache()
                    .query()
                    .withName("Manhole")
                    .within(8)
                    .interact("Open");

            if (opened)
            {
                sleep(600, 900);
                climbed = Microbot.getRs2TileObjectCache()
                        .query()
                        .withName("Manhole")
                        .within(8)
                        .interact("Climb-down");
            }
        }

        if (climbed)
        {
            sleepUntil(() -> {
                WorldPoint nowPoint = Rs2Player.getWorldLocation();
                return nowPoint != null && isUnderground(nowPoint);
            }, 5000);
        }
    }

    private boolean handleNearbyWeb()
    {
        boolean webPresent = Microbot.getRs2TileObjectCache()
                .query()
                .withName("Web")
                .within(8)
                .nearestOnClientThread() != null;

        if (!webPresent)
        {
            return false;
        }

        boolean switchedToAxe = false;
        if (config.strategy() != BryophytaStrategy.MELEE
                && !Rs2Equipment.isWearing(normalize(config.growthlingToolName()), true))
        {
            if (!equipGrowthlingTool())
            {
                failAndStop("Could not equip the Growthling axe to slash the Varrock Sewers web.");
                return true;
            }
            switchedToAxe = true;
        }

        status = "Slashing sewer web...";
        Microbot.getRs2TileObjectCache()
                .query()
                .withName("Web")
                .within(8)
                .interact("Slash");

        sleep(650, 950);

        if (switchedToAxe)
        {
            restoreMainWeaponIfNeeded();
        }

        return true;
    }

    private boolean tryEnterNearbyLairGate()
    {
        boolean gateNearby = Microbot.getRs2TileObjectCache()
                .query()
                .withName("Gate")
                .within(BRYOPHYTA_SEWER_ENTRANCE, 20)
                .nearestOnClientThread() != null;

        if (!gateNearby)
        {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now - lastEntryAttemptAt < ENTRY_RETRY_MS)
        {
            return true;
        }
        lastEntryAttemptAt = now;

        if (config.protectFromMagic() && !Rs2Prayer.isOutOfPrayer())
        {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, true);
        }

        setState(BryophytaState.ENTERING_LAIR, "Entering Bryophyta's lair...");

        boolean entered = Microbot.getRs2TileObjectCache()
                .query()
                .withName("Gate")
                .within(BRYOPHYTA_SEWER_ENTRANCE, 20)
                .interact("Open");

        if (!entered)
        {
            entered = Microbot.getRs2TileObjectCache()
                    .query()
                    .withName("Gate")
                    .within(BRYOPHYTA_SEWER_ENTRANCE, 20)
                    .interact("Enter");
        }

        if (!entered)
        {
            entryFailures++;
            if (entryFailures >= 4)
            {
                String suffix = Rs2Inventory.contains(MOSSY_KEY, true)
                        ? " A Mossy key is present, so check the gate interaction/path."
                        : " If this account has never permanently unlocked Bryophyta, bring a Mossy key once.";
                failAndStop("Could not enter Bryophyta after four attempts." + suffix);
            }
            return true;
        }

        entryFailures = 0;
        sleepUntil(this::isInsideLair, 6000);
        return true;
    }

    private void handleGrowthlings(Rs2NpcModel growthling)
    {
        setState(BryophytaState.KILLING_GROWTHLINGS, "Growthling - switching to " + config.growthlingToolName() + ".");

        if (!equipGrowthlingTool())
        {
            failAndStop("Required Growthling axe is missing/unusable: " + config.growthlingToolName());
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastGrowthlingClickAt < GROWTHLING_CLICK_COOLDOWN_MS)
        {
            return;
        }

        lastGrowthlingClickAt = now;
        status = "Attacking Growthling...";
        growthling.click("Attack");
    }

    private boolean equipGrowthlingTool()
    {
        String tool = normalize(config.growthlingToolName());
        if (Rs2Equipment.isWearing(tool, true))
        {
            return true;
        }

        if (!Rs2Inventory.contains(tool, true))
        {
            return false;
        }

        return interactEquip(tool);
    }

    private void restoreMainWeaponIfNeeded()
    {
        if (mainWeapon == null || mainWeapon.isBlank() || Rs2Equipment.isWearing(mainWeapon, true))
        {
            return;
        }

        if (!Rs2Inventory.contains(mainWeapon, true))
        {
            // A two-handed switch can put the normal weapon in inventory; if it is genuinely gone,
            // force a bank cycle instead of continuing with the wrong weapon.
            restockRequired = true;
            loadoutVerified = false;
            return;
        }

        status = "Restoring " + mainWeapon + "...";
        interactEquip(mainWeapon);
    }

    private void handleBryophyta(Rs2NpcModel bryophyta)
    {
        setState(BryophytaState.FIGHTING_BRYOPHYTA, "Fighting Bryophyta - " + config.strategy());

        switch (config.strategy())
        {
            case MELEE:
                attackWithCurrentWeapon(bryophyta);
                break;

            case RANGED:
                if (!Rs2Equipment.isWearing("Maple shortbow", true))
                {
                    restockRequired = true;
                    return;
                }
                if (maintainRange(bryophyta))
                {
                    return;
                }
                attackWithCurrentWeapon(bryophyta);
                break;

            case MAGIC_FIRE:
                if (!Rs2Equipment.isWearing("Staff of fire", true))
                {
                    restockRequired = true;
                    return;
                }
                if (maintainRange(bryophyta))
                {
                    return;
                }
                castFireSpell(bryophyta);
                break;

            default:
                throw new IllegalStateException("Unsupported Bryophyta strategy: " + config.strategy());
        }
    }

    private void attackWithCurrentWeapon(Rs2NpcModel bryophyta)
    {
        if (Rs2Player.isIdle())
        {
            bryophyta.click("Attack");
        }
    }

    private void castFireSpell(Rs2NpcModel bryophyta)
    {
        long now = System.currentTimeMillis();
        if (now - lastMagicCastAt < config.magicCastIntervalMs() || Rs2Player.isMoving())
        {
            return;
        }

        lastMagicCastAt = now;
        status = "Casting " + config.fireSpell() + "...";

        boolean cast = Rs2Magic.castOn(config.fireSpell().getMagicAction(), bryophyta);
        if (!cast)
        {
            if (shouldEmergencyRestock())
            {
                restockRequired = true;
            }
            else
            {
                status = "Could not cast " + config.fireSpell() + ".";
            }
        }
    }

    /**
     * @return true when movement was issued this loop.
     */
    private boolean maintainRange(Rs2NpcModel bryophyta)
    {
        if (bryophyta.getDistanceFromPlayer() >= config.minimumRangeDistance())
        {
            return false;
        }

        WorldPoint player = Rs2Player.getWorldLocation();
        WorldPoint boss = bryophyta.getWorldLocation();
        if (player == null || boss == null)
        {
            return false;
        }

        WorldPoint safeTile = findFartherWalkableTile(player, boss);
        if (safeTile == null)
        {
            status = "Too close - no farther tile found.";
            return false;
        }

        status = "Creating distance from Bryophyta...";
        return Rs2Walker.walkFastCanvas(safeTile);
    }

    private WorldPoint findFartherWalkableTile(WorldPoint player, WorldPoint boss)
    {
        int plane = player.getPlane();
        int currentDistance = player.distanceTo(boss);
        ArrayList<WorldPoint> candidates = new ArrayList<>();

        for (int radius = 2; radius <= 4; radius++)
        {
            candidates.add(new WorldPoint(player.getX() + radius, player.getY(), plane));
            candidates.add(new WorldPoint(player.getX() - radius, player.getY(), plane));
            candidates.add(new WorldPoint(player.getX(), player.getY() + radius, plane));
            candidates.add(new WorldPoint(player.getX(), player.getY() - radius, plane));
            candidates.add(new WorldPoint(player.getX() + radius, player.getY() + radius, plane));
            candidates.add(new WorldPoint(player.getX() + radius, player.getY() - radius, plane));
            candidates.add(new WorldPoint(player.getX() - radius, player.getY() + radius, plane));
            candidates.add(new WorldPoint(player.getX() - radius, player.getY() - radius, plane));
        }

        return candidates.stream()
                .map(Rs2Tile::getNearestWalkableTileWithLineOfSight)
                .filter(tile -> tile != null && tile.getPlane() == plane)
                .filter(tile -> tile.distanceTo(boss) > currentDistance)
                .max(Comparator.comparingInt(tile -> tile.distanceTo(boss)))
                .orElse(null);
    }

    private void registerKill()
    {
        kills++;
        killRegisteredForCycle = true;
        killRegisteredAt = System.currentTimeMillis();
        setState(BryophytaState.LOOTING, "Bryophyta defeated - looting.");
        Rs2Prayer.disableAllPrayers();
    }

    private void handlePostKill(long now)
    {
        if (config.lootBossDrops() && now - killRegisteredAt <= POST_KILL_LOOT_WINDOW_MS)
        {
            setState(BryophytaState.LOOTING, "Looting Bryophyta drops...");
            attemptBossLoot();
            return;
        }

        if (config.openRewardChest())
        {
            handleChest(now);
            return;
        }

        // No chest attempt configured: leave/re-enter to make the next spawn cycle deterministic.
        setState(BryophytaState.WAITING_FOR_RESPAWN, "Waiting for next Bryophyta cycle...");
        bossWasPresent = false;
        killRegisteredForCycle = false;
    }

    private void attemptBossLoot()
    {
        if (pickupNamedGroundItem(MOSSY_KEY))
        {
            return;
        }
        if (pickupNamedGroundItem("Ensouled giant head"))
        {
            return;
        }
        if (pickupNamedGroundItem("Clue scroll (beginner)"))
        {
            return;
        }

        if (config.lootValueThreshold() <= 0)
        {
            return;
        }

        Rs2TileItemModel valuable = Microbot.getRs2TileItemCache()
                .query()
                .fromWorldView()
                .within(8)
                .where(item -> item.isLootAble()
                        && item.getTotalValue() >= config.lootValueThreshold())
                .nearestOnClientThread();

        if (valuable != null)
        {
            status = "Looting " + valuable.getName() + "...";
            valuable.pickup();
        }
    }

    private boolean pickupNamedGroundItem(String itemName)
    {
        Rs2TileItemModel item = Microbot.getRs2TileItemCache()
                .query()
                .fromWorldView()
                .withName(itemName)
                .within(8)
                .nearestOnClientThread();

        if (item == null || !item.isLootAble())
        {
            return false;
        }

        status = "Looting " + itemName + "...";
        return item.pickup();
    }

    private void handleChest(long now)
    {
        if (now - lastChestAttemptAt < CHEST_RETRY_MS)
        {
            setState(BryophytaState.WAITING_FOR_RESPAWN, "Waiting for Bryophyta respawn...");
            return;
        }

        lastChestAttemptAt = now;
        setState(BryophytaState.OPENING_CHEST, "Attempting Bryophyta chest...");

        boolean interacted = Microbot.getRs2TileObjectCache()
                .query()
                .withId(BRYOPHYTA_CHEST_OBJECT_ID)
                .within(20)
                .interact("Open");

        if (interacted)
        {
            chestAttempts++;
            bossWasPresent = false;
            killRegisteredForCycle = false;
            setState(BryophytaState.WAITING_FOR_RESPAWN, "Chest attempted - waiting for respawn.");
        }
        else
        {
            status = "Could not interact with Bryophyta chest.";
        }
    }

    private boolean verifyEquipmentLoadout()
    {
        for (EquipmentInventorySlot slot : BryophytaLoadout.CONFIGURABLE_SLOTS)
        {
            if (equipmentSettings.isExplicitEmpty(config.strategy(), slot) && Rs2Equipment.get(slot) != null)
            {
                return false;
            }
        }

        Map<EquipmentInventorySlot, String> desired = equipmentSettings.equipmentFor(config.strategy());
        for (Map.Entry<EquipmentInventorySlot, String> entry : desired.entrySet())
        {
            EquipmentInventorySlot slot = entry.getKey();
            String name = entry.getValue();

            if (slot == EquipmentInventorySlot.AMMO)
            {
                if (getEquippedQuantity(slot, name) <= 0)
                {
                    return false;
                }
                continue;
            }

            Rs2ItemModel item = Rs2Equipment.get(slot);
            if (item == null || item.getName() == null || !item.getName().equalsIgnoreCase(name))
            {
                return false;
            }
        }

        loadoutVerified = true;
        return true;
    }

    private String selectedAmmoName()
    {
        return equipmentSettings.equipmentFor(config.strategy()).get(EquipmentInventorySlot.AMMO);
    }

    private boolean verifyFullTripSupplies()
    {
        if (!hasVarrockRuneTargets())
        {
            return false;
        }

        String tool = normalize(config.growthlingToolName());
        if (!Rs2Inventory.contains(tool, true) && !Rs2Equipment.isWearing(tool, true))
        {
            return false;
        }

        if (Rs2Inventory.itemQuantity(normalize(config.foodName())) < config.foodAmount())
        {
            return false;
        }

        if (config.strategy() == BryophytaStrategy.RANGED
                && selectedAmmoName() != null
                && getEquippedQuantity(EquipmentInventorySlot.AMMO, selectedAmmoName()) < config.rangedArrowAmount())
        {
            return false;
        }

        if (config.strategy() == BryophytaStrategy.MAGIC_FIRE)
        {
            BryophytaFireSpell spell = config.fireSpell();
            int casts = config.magicCastSupply();
            if (Rs2Inventory.itemQuantity(AIR_RUNE)
                    < config.varrockTeleportCount() * 3 + spell.getAirRunesPerCast() * casts)
            {
                return false;
            }
            if (Rs2Inventory.itemQuantity(FIRE_RUNE)
                    < config.varrockTeleportCount() + spell.getFireRunesPerCast() * casts)
            {
                return false;
            }
            if (Rs2Inventory.itemQuantity(spell.getCatalystRuneName()) < casts)
            {
                return false;
            }
        }

        return true;
    }

    private boolean hasMinimumTripSupplies()
    {
        if (!hasVarrockTeleportRunesForOne())
        {
            failAndStop("Emergency Varrock teleport runes are missing before entering the sewers.");
            return false;
        }

        String tool = normalize(config.growthlingToolName());
        if (!Rs2Inventory.contains(tool, true) && !Rs2Equipment.isWearing(tool, true))
        {
            failAndStop("Growthling axe is missing before entering the sewers: " + tool);
            return false;
        }

        if (Rs2Inventory.itemQuantity(normalize(config.foodName())) <= config.teleportAtFoodCount())
        {
            return false;
        }

        return true;
    }

    private boolean hasVarrockRuneTargets()
    {
        int teleports = config.varrockTeleportCount();
        return Rs2Inventory.itemQuantity(AIR_RUNE) >= teleports * 3
                && Rs2Inventory.itemQuantity(FIRE_RUNE) >= teleports
                && Rs2Inventory.itemQuantity(LAW_RUNE) >= teleports;
    }

    private boolean hasVarrockTeleportRunesForOne()
    {
        return Rs2Inventory.itemQuantity(AIR_RUNE) >= 3
                && Rs2Inventory.itemQuantity(FIRE_RUNE) >= 1
                && Rs2Inventory.itemQuantity(LAW_RUNE) >= 1;
    }

    private int getEquippedQuantity(EquipmentInventorySlot slot, String exactName)
    {
        Rs2ItemModel item = Rs2Equipment.get(slot);
        if (item == null || item.getName() == null || !item.getName().equalsIgnoreCase(exactName))
        {
            return 0;
        }
        return item.getQuantity();
    }

    private Rs2NpcModel getBryophyta()
    {
        return Microbot.getRs2NpcCache()
                .query()
                .withId(BRYOPHYTA_NPC_ID)
                .fromWorldView()
                .nearestOnClientThread();
    }

    private Rs2NpcModel getNearestGrowthling()
    {
        return Microbot.getRs2NpcCache()
                .query()
                .withId(GROWTHLING_NPC_ID)
                .fromWorldView()
                .where(npc -> !npc.isDead())
                .nearestOnClientThread();
    }

    private boolean isInsideLair()
    {
        if (getBryophyta() != null || getNearestGrowthling() != null)
        {
            return true;
        }

        return Microbot.getRs2TileObjectCache()
                .query()
                .withId(BRYOPHYTA_CHEST_OBJECT_ID)
                .fromWorldView()
                .within(30)
                .nearestOnClientThread() != null;
    }

    private boolean isOnSurface()
    {
        WorldPoint point = Rs2Player.getWorldLocation();
        return point != null && !isUnderground(point) && !isInsideLair();
    }

    private static boolean isUnderground(WorldPoint point)
    {
        return point != null && point.getY() > 9000;
    }

    private static String normalize(String value)
    {
        return value == null ? "" : value.trim();
    }

    private void setState(BryophytaState newState, String newStatus)
    {
        state = newState;
        status = newStatus;
        Microbot.status = newStatus;
    }

    private void failAndStop(String reason)
    {
        if (state == BryophytaState.STOPPED)
        {
            return;
        }

        state = BryophytaState.MISSING_REQUIRED_ITEM;
        status = reason;
        Microbot.status = reason;
        Microbot.showMessage("KSP Bryophyta: " + reason);
        super.shutdown();
        Rs2Prayer.disableAllPrayers();
        state = BryophytaState.STOPPED;
    }

    public BryophytaState getState()
    {
        return state;
    }

    public String getStatus()
    {
        return status;
    }

    public int getKills()
    {
        return kills;
    }

    public int getMossyKeys()
    {
        return mossyKeys;
    }

    public int getChestAttempts()
    {
        return chestAttempts;
    }

    public int getFoodRemaining()
    {
        return foodRemaining;
    }

    public int getPrayerPoints()
    {
        return prayerPoints;
    }

    public void setStopped(String reason)
    {
        state = BryophytaState.STOPPED;
        status = reason;
        Microbot.status = reason;
    }

    @Override
    public void shutdown()
    {
        super.shutdown();
        Rs2Prayer.disableAllPrayers();
        if (state != BryophytaState.STOPPED)
        {
            setStopped("Stopped.");
        }
    }
}
