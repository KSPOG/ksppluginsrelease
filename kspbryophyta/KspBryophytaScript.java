package net.runelite.client.plugins.microbot.kspbryophyta;


import net.runelite.client.plugins.microbot.kspbank.KspVerifiedBank;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Skill;
import net.runelite.api.WorldView;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Singleton
public class KspBryophytaScript extends Script {
    static final int BRYOPHYTA_NPC_ID = 8195;
    static final int GROWTHLING_NPC_ID = 8194;
    private static final int BRYOPHYTA_CHEST_OBJECT_ID = 56378;
    private static final int BRYOPHYTA_GATE_OBJECT_ID = 32534;
    private static final int BRYOPHYTA_EXIT_OBJECT_ID = 32535;
    private static final int VARROCK_MANHOLE_CLOSED_OBJECT_ID = 881;
    private static final int VARROCK_MANHOLE_OPEN_OBJECT_ID = 882;
    private static final int VARROCK_ALTAR_OBJECT_ID = 14860;

    private static final int LOOP_DELAY_MS = 100;
    private static final int POST_KILL_EMPTY_SCANS = 30;
    private static final long LAIR_ENTRY_TRANSITION_TIMEOUT_MS = 10_000L;
    private static final long QUICK_EXIT_TRANSITION_TIMEOUT_MS = 8_000L;
    private static final long GROWTHLING_ATTACK_RETRY_MS = 3_000L;
    private static final long SEWER_AGGRO_WALK_RETRY_MS = 1_500L;
    private static final long WEB_SLASH_RETRY_MS = 1_500L;
    private static final long CHEST_OPEN_TIMEOUT_MS = 6_000L;
    private static final long ALTAR_INTERACTION_TIMEOUT_MS = 6_000L;
    private static final long MANHOLE_TRANSITION_TIMEOUT_MS = 8_000L;

    private static final String AIR_RUNE = "Air rune";
    private static final String FIRE_RUNE = "Fire rune";
    private static final String LAW_RUNE = "Law rune";
    private static final String MOSSY_KEY = "Mossy key";
    private static final String DEADLY_RED_SPIDER = "Deadly red spider";
    private static final String STRENGTH_POTION_4 = "Strength potion(4)";
    private static final String[] EQUIP_ACTIONS = {"Wield", "Wear", "Equip"};
    private static final String[] GATE_ACTIONS = {"Open", "Unlock", "Enter"};
    private static final String[] PRIORITY_LOOT = {MOSSY_KEY, "Ensouled giant head", "Clue scroll (beginner)"};

    private static final WorldPoint VARROCK_MANHOLE = new WorldPoint(3237, 3458, 0);
    private static final WorldPoint VARROCK_MANHOLE_APPROACH = new WorldPoint(3237, 3459, 0);
    private static final WorldPoint BRYOPHYTA_SEWER_ENTRANCE = new WorldPoint(3174, 9901, 0);

    private static final WorldPoint VARROCK_ALTAR = new WorldPoint(3253, 3486, 0);
    private static final WorldPoint VARROCK_ALTAR_APPROACH = new WorldPoint(3254, 3486, 0);

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
    private int bossMissingScans;
    private int postKillEmptyScans;
    private int postKillKeysAtStart;

    private volatile boolean growthlingWaveActive;
    private volatile int growthlingEmptyScans;
    private volatile int activeGrowthlingIndex = -1;
    private volatile long growthlingAttackSentAt;
    private volatile boolean bossAttackPending;
    private volatile boolean lairEntryPending;
    private volatile String lastGateDialogueFingerprint = "";
    private volatile boolean gateContinueHandled;
    private long lairEntryStartedAt;
    private boolean quickExitPending;
    private long quickExitStartedAt;
    private boolean chestLootPending;
    private int chestKeysBeforeOpen;
    private long chestOpenRequestedAt;
    private long chestOpenConfirmedAt;
    private String pendingChestLootKey = "";
    private boolean altarInteractionPending;
    private long altarInteractionStartedAt;
    private boolean manholeDescentPending;
    private boolean manholeOpenPending;
    private long manholeDescentStartedAt;
    private boolean webSlashPending;
    private long webSlashSentAt;
    private long sewerAggroWalkSentAt;
    private final Set<String> ignoredChestGroundDrops = new HashSet<>();
    private boolean teleportPending;
    private boolean bankClosePending;
    private int bankEpochBeforeOpen = -1;
    private long teleportStartedAt;

    @Inject
    public KspBryophytaScript(BryophytaEquipmentSettings equipmentSettings) {
        this.equipmentSettings = equipmentSettings;
    }
    public boolean run(KspBryophytaConfig config) {
        this.config = config;
        resetRuntimeState();
        Microbot.enableAutoRunOn = true;
        if (!validateStaticRequirements()) return false;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || state == BryophytaState.STOPPED || !super.run()) return;
                updateCounters();
                if (!Rs2Bank.isOpen() && !ensureAutoRetaliateDisabled()) return;
                if (isInsideLair()) handleInsideLair(); else handleOutsideLair();
            } catch (Exception ex) {
                if (isInterruption(ex)) return;
                Microbot.logStackTrace(getClass().getSimpleName(), ex);
                failAndStop("Unexpected error: " + ex.getClass().getSimpleName());
            }
        }, 0, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);
        return true;
    }
    private void resetRuntimeState() {
        state = BryophytaState.STARTING;
        status = "Starting - first action is a full Varrock restock.";
        kills = chestAttempts = mossyKeys = foodRemaining = prayerPoints = 0;
        entryFailures = altarFailures = bossMissingScans = postKillEmptyScans = postKillKeysAtStart = 0;
        growthlingWaveActive = false;
        growthlingEmptyScans = 0;
        activeGrowthlingIndex = -1;
        growthlingAttackSentAt = 0L;
        bossAttackPending = false;
        quickExitStartedAt = chestOpenRequestedAt = chestOpenConfirmedAt = 0L;
        chestKeysBeforeOpen = 0;
        bossWasPresent = killRegisteredForCycle = prayerRestoredAfterBank = loadoutVerified = false;
        quickExitPending = chestLootPending = altarInteractionPending = manholeDescentPending = manholeOpenPending = false;
        webSlashPending = teleportPending = bankClosePending = false;
        webSlashSentAt = sewerAggroWalkSentAt = 0L;
        bankEpochBeforeOpen = -1;
        teleportStartedAt = 0L;
        pendingChestLootKey = lastGateDialogueFingerprint = "";
        gateContinueHandled = false;
        restockRequired = true;
        mainWeapon = equipmentSettings.mainWeaponFor(config.strategy());
        resetTransitions();
        ignoredChestGroundDrops.clear();
    }

    private void updateCounters() {
        mossyKeys = Rs2Inventory.itemQuantity(MOSSY_KEY);
        foodRemaining = Rs2Inventory.itemQuantity(normalize(config.foodName()));
        prayerPoints = Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER);
    }

    private boolean validateStaticRequirements() {
        if (Microbot.getClient().getRealSkillLevel(Skill.MAGIC) < 25) {
            failAndStop("Varrock Teleport requires level 25 Magic.");
            return false;
        }

        if (config.protectFromMagic()
                && Microbot.getClient().getRealSkillLevel(Skill.PRAYER) < 37) {
            failAndStop("Protect from Magic is enabled but Prayer is below level 37.");
            return false;
        }

        if (normalize(config.foodName()).isEmpty()) {
            failAndStop("Food name cannot be empty.");
            return false;
        }

        if (normalize(config.growthlingToolName()).isEmpty()) {
            failAndStop("Growthling axe name cannot be empty.");
            return false;
        }

        return true;
    }

    private void handleInsideLair() {
        long now = System.currentTimeMillis();
        if (chestLootPending) { handleChestLoot(now); return; }
        if (quickExitPending) { handleQuickExitReset(now); return; }
        if (killRegisteredForCycle) { handlePostKill(now); return; }
        if (restockRequired || shouldEmergencyRestock()) {
            restockRequired = true;
            handleVarrockTeleport("Restocking from Bryophyta...");
            return;
        }
        if (!loadoutVerified && !verifyEquipmentLoadout()) {
            restockRequired = true;
            handleVarrockTeleport("Loadout changed - returning to Varrock...");
            return;
        }

        Rs2NpcModel bryophyta = getBryophyta();
        boolean liveBoss = bryophyta != null && !bryophyta.isDead();
        maintainCombatPrayer(liveBoss);
        handleSurvival();
        Rs2NpcModel growthling = getNearestGrowthling();
        if (growthling != null) { handleGrowthlings(growthling); return; }
        if (awaitGrowthlingWaveClear()) return;
        if (!restoreMainWeaponIfNeeded()) return;

        if (liveBoss) {
            bossWasPresent = true;
            bossMissingScans = 0;
            killRegisteredForCycle = false;
            handleBryophyta(bryophyta);
            return;
        }
        if (bossWasPresent && !killRegisteredForCycle) {
            if (++bossMissingScans >= 2) registerKill();
            else setState(BryophytaState.WAITING_FOR_RESPAWN, "Bryophyta missing - confirming NPC cache...");
        } else setState(BryophytaState.WAITING_FOR_RESPAWN, "Waiting for Bryophyta...");
    }

    private void handleOutsideLair() {
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null) return;
        if (quickExitPending) completeQuickExitReset();

        if (restockRequired) {
            if (isUnderground(player)) {
                handleVarrockTeleport("Returning to Varrock for restock...");
                return;
            }

            performBankRestock();
            return;
        }

        if (!Rs2Bank.isOpen()) maintainTravelPrayer();

        if (!prayerRestoredAfterBank) {
            if (isUnderground(player)) {
                restockRequired = true;
                handleVarrockTeleport("Prayer was not restored - returning to Varrock...");
                return;
            }

            restorePrayerAtVarrockAltar();
            return;
        }

        if (!loadoutVerified || !verifyEquipmentLoadout()) {
            restockRequired = true;
            loadoutVerified = false;
            return;
        }

        if (!hasMinimumTripSupplies()) {
            restockRequired = true;
            return;
        }

        if (!config.autoEnterLair()) {
            setState(BryophytaState.WAITING_AT_ENTRANCE, "Restocked and prayer restored; auto navigation disabled.");
            return;
        }

        navigateToBryophyta();
    }

    private void maintainTravelPrayer() {
        WorldPoint player = Rs2Player.getWorldLocation();
        boolean atGate = player != null && isUnderground(player)
                && player.distanceTo(BRYOPHYTA_SEWER_ENTRANCE) <= 8
                && objectVisible(BRYOPHYTA_GATE_OBJECT_ID, BRYOPHYTA_SEWER_ENTRANCE, 8);
        if (config.protectFromMagic() && !Rs2Prayer.isOutOfPrayer() && atGate) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, true);
        } else if (!atGate && Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MAGIC)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, false);
        }
    }

    private boolean shouldEmergencyRestock() {
        if (prayerPoints <= config.teleportAtPrayerPoints()) {
            status = "Prayer threshold reached.";
            return true;
        }
        if (foodRemaining <= config.teleportAtFoodCount()) {
            status = "Food threshold reached.";
            return true;
        }
        switch (config.strategy()) {
            case RANGED:
                String ammo = selectedAmmoName();
                return ammo != null && getEquippedQuantity(EquipmentInventorySlot.AMMO, ammo) <= 0;
            case MAGIC_FIRE:
                BryophytaFireSpell spell = config.fireSpell();
                return Rs2Inventory.itemQuantity(AIR_RUNE) < spell.getAirRunesPerCast()
                        || Rs2Inventory.itemQuantity(FIRE_RUNE) < spell.getFireRunesPerCast()
                        || Rs2Inventory.itemQuantity(spell.getCatalystRuneName()) < 1;
            case MELEE:
                if (config.useStrengthPotion()
                        && Microbot.getClient().getBoostedSkillLevel(Skill.STRENGTH) <= Microbot.getClient().getRealSkillLevel(Skill.STRENGTH)
                        && !hasAnyStrengthPotionDose()) {
                    status = "Strength potion depleted.";
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    private boolean ensureAutoRetaliateDisabled() {
        if (Microbot.getVarbitPlayerValue(VarPlayerID.OPTION_NODEF) == 1) return true;
        if (Rs2Tab.getCurrentTab() != InterfaceTab.COMBAT) {
            setStatus("Opening Combat tab to disable Auto Retaliate...");
            Rs2Tab.switchTo(InterfaceTab.COMBAT);
            return false;
        }
        Widget retaliate = Rs2Widget.getWidget(WidgetInfo.COMBAT_AUTO_RETALIATE.getId());
        if (retaliate == null) {
            setStatus("Waiting for Auto Retaliate widget...");
            return false;
        }
        setStatus("Disabling visible Auto Retaliate widget...");
        Rs2Widget.clickWidget(retaliate);
        return false;
    }

    private void handleSurvival() {
        maintainStrengthBoost();
        Rs2Player.eatAt(config.eatAtPercent());
        if (config.maintainPoisonProtection()) Rs2Player.drinkAntiPoisonPotion();
    }

    private void maintainCombatPrayer(boolean liveBoss) {
        if (config.protectFromMagic() && liveBoss && !Rs2Prayer.isOutOfPrayer()) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, true);
        } else if (!liveBoss && Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MAGIC)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, false);
        }
    }

    private void maintainStrengthBoost() {
        if (config.strategy() != BryophytaStrategy.MELEE || !config.useStrengthPotion()) return;
        if (Microbot.getClient().getBoostedSkillLevel(Skill.STRENGTH) > Microbot.getClient().getRealSkillLevel(Skill.STRENGTH)) return;
        String potion = availableStrengthPotion();
        if (potion != null) {
            setStatus("Drinking Strength potion...");
            Rs2Inventory.interact(potion, "Drink", true);
        }
    }
    private boolean hasAnyStrengthPotionDose() { return availableStrengthPotion() != null; }
    private String availableStrengthPotion() {
        for (int dose = 4; dose >= 1; dose--) {
            String potion = "Strength potion(" + dose + ")";
            if (Rs2Inventory.contains(potion, true)) return potion;
        }
        return null;
    }

    private void handleVarrockTeleport(String reason) {
        if (isOnSurface()) {
            teleportPending = false;
            teleportStartedAt = 0L;
            bossWasPresent = killRegisteredForCycle = prayerRestoredAfterBank = loadoutVerified = false;
            restockRequired = true;
            resetTransitions();
            setState(BryophytaState.BANKING, "Varrock reached - heading to East bank.");
            return;
        }
        if (teleportPending) {
            if (System.currentTimeMillis() - teleportStartedAt > 10_000L) {
                teleportPending = false;
                failAndStop("Varrock Teleport did not complete within 10 seconds.");
            } else setState(BryophytaState.TELEPORTING_VARROCK, "Teleport sent - waiting for surface region...");
            return;
        }
        if (!hasVarrockTeleportRunesForOne()) {
            failAndStop("Missing Varrock teleport runes. Required: 3 Air rune, 1 Fire rune, 1 Law rune.");
            return;
        }
        setState(BryophytaState.TELEPORTING_VARROCK, reason);
        Rs2Prayer.disableAllPrayers();
        if (!Rs2Magic.cast(MagicAction.VARROCK_TELEPORT)) {
            failAndStop("Could not cast Varrock Teleport. Check the standard spellbook and rune supply.");
            return;
        }
        teleportPending = true;
        teleportStartedAt = System.currentTimeMillis();
    }

    private void performBankRestock() {
        setState(BryophytaState.BANKING, "Banking at Varrock East...");
        if (!Rs2Bank.isOpen()) {
            if (bankClosePending) {
                if (!ensureAutoRetaliateDisabled()) return;
                if (config.strategy() == BryophytaStrategy.MAGIC_FIRE && !ensureSelectedSpellAutocast()) return;
                bankClosePending = false;
                markRestockComplete();
                setState(BryophytaState.RESTORING_PRAYER, "Restocked - restoring Prayer at Varrock altar.");
                return;
            }
            WorldPoint player = Rs2Player.getWorldLocation();
            WorldPoint bank = BankLocation.VARROCK_EAST.getWorldPoint();
            if (player == null) return;
            if (player.distanceTo(bank) > 8) {
                walkToControlled(bank, 4, "Walking to Varrock East bank...");
                return;
            }
            int epoch = Rs2Bank.getBankLiveEpoch();
            boolean opened = KspVerifiedBank.openBank();
            if (opened) bankEpochBeforeOpen = epoch;
            setStatus(opened ? "Bank interaction sent - checking bank widget/container..." : "Waiting for visible Varrock East bank target...");
            return;
        }
        if (bankEpochBeforeOpen >= 0 && Rs2Bank.getBankLiveEpoch() <= bankEpochBeforeOpen) {
            setStatus("Bank widget open - checking live bank container snapshot...");
            return;
        }
        bankEpochBeforeOpen = -1;
        Map<EquipmentInventorySlot, String> desired = equipmentSettings.equipmentFor(config.strategy());
        if (!depositUnneededInventory(desired) || !depositIncorrectWornEquipmentAtBank(desired)) return;
        setState(BryophytaState.EQUIPPING, "Completing " + config.strategy() + " loadout...");
        if (!prepareEquipmentLoadout(desired) || !withdrawTripSupplies()) return;
        mainWeapon = equipmentSettings.mainWeaponFor(config.strategy());
        if (mainWeapon == null || mainWeapon.isBlank()) {
            failAndStop("The selected " + config.strategy() + " setup requires a weapon.");
            return;
        }
        if (!verifyEquipmentLoadout() || !verifyFullTripSupplies()) {
            setStatus("Checking bank/inventory/equipment container updates...");
            return;
        }
        bankClosePending = true;
        setStatus("Restock verified - closing bank widget...");
        Rs2Widget.clickChildWidget(786434, 11);
    }

    private boolean depositUnneededInventory(Map<EquipmentInventorySlot, String> desired) {
        Set<String> keep = desiredInventoryNames(desired);
        Rs2ItemModel unwanted = Rs2Inventory.all().stream()
                .filter(item -> item != null && item.getName() != null)
                .filter(item -> !keep.contains(item.getName().toLowerCase()))
                .findFirst().orElse(null);
        if (unwanted == null) return true;
        setStatus("Banking " + unwanted.getName() + "...");
        Rs2Bank.depositAll(unwanted.getId());
        return false;
    }

    private Set<String> desiredInventoryNames(Map<EquipmentInventorySlot, String> desired) {
        Set<String> keep = new HashSet<>();
        for (String name : desired.values()) addKeepName(keep, name);
        addKeepName(keep, config.growthlingToolName());
        addKeepName(keep, AIR_RUNE);
        addKeepName(keep, FIRE_RUNE);
        addKeepName(keep, LAW_RUNE);
        addKeepName(keep, config.foodName());
        if (config.strategy() == BryophytaStrategy.MAGIC_FIRE) addKeepName(keep, config.fireSpell().getCatalystRuneName());
        if (config.strategy() == BryophytaStrategy.MELEE && config.useStrengthPotion()) addKeepName(keep, STRENGTH_POTION_4);
        if (config.withdrawMossyKey()) addKeepName(keep, MOSSY_KEY);
        return keep;
    }

    private void addKeepName(Set<String> keep, String name) {
        String normalized = normalize(name);
        if (!normalized.isEmpty()) keep.add(normalized.toLowerCase());
    }

    private boolean depositIncorrectWornEquipmentAtBank(Map<EquipmentInventorySlot, String> desired) {
        if (!Rs2Bank.isOpen()) return false;
        for (EquipmentInventorySlot slot : BryophytaLoadout.CONFIGURABLE_SLOTS) {
            Rs2ItemModel worn = Rs2Equipment.get(slot);
            if (worn == null) continue;
            String wanted = desired.get(slot);
            boolean explicitEmpty = equipmentSettings.isExplicitEmpty(config.strategy(), slot);
            if ((wanted != null && worn.getName() != null && worn.getName().equalsIgnoreCase(wanted)) || (wanted == null && !explicitEmpty)) continue;
            if (Rs2Inventory.isFull()) {
                Rs2ItemModel food = Rs2Inventory.get(normalize(config.foodName()), true);
                if (food == null || !Rs2Bank.depositOne(food.getId())) {
                    setStatus("Need one inventory slot to swap incorrect worn equipment...");
                    return false;
                }
                return false;
            }
            setStatus("Removing incorrect " + slot.name().toLowerCase() + ": " + worn.getName());
            Rs2Equipment.unEquip(slot);
            return false;
        }
        return true;
    }

    private boolean prepareEquipmentLoadout(Map<EquipmentInventorySlot, String> desired) {
        for (Map.Entry<EquipmentInventorySlot, String> entry : desired.entrySet()) {
            EquipmentInventorySlot slot = entry.getKey();
            String itemName = entry.getValue();
            if (slot == EquipmentInventorySlot.AMMO) {
                if (getEquippedQuantity(slot, itemName) > 0) continue;
                if (Rs2Inventory.itemQuantity(itemName) > 0) {
                    if (!equipAmmoStack(itemName)) failAndStop("Could not equip required ammunition: " + itemName);
                    return false;
                }
                if (bankQuantityExact(itemName) < 1) {
                    failAndStop("Missing required Ranged ammunition in bank/equipment: " + itemName);
                    return false;
                }
                withdrawAllAvailableStack(itemName, 1, "Ranged ammunition");
                return false;
            }
            if (Rs2Equipment.isWearing(itemName, true)) continue;
            if (!Rs2Inventory.contains(itemName, true)) {
                if (bankQuantityExact(itemName) < 1) {
                    failAndStop("Missing required " + slot.name().toLowerCase() + " equipment in bank: " + itemName);
                    return false;
                }
                withdrawExact(itemName, 1, slot.name() + " equipment");
                return false;
            }
            if (!interactEquip(itemName)) failAndStop("Could not equip required item: " + itemName + ". Check skill/quest requirements.");
            return false;
        }
        return true;
    }

    private boolean interactEquip(String itemName) {
        if (Rs2Equipment.isWearing(itemName, true)) return true;
        Rs2ItemModel item = Rs2Inventory.get(itemName, true);
        if (item == null) return false;
        return Rs2Bank.isOpen() ? Rs2Bank.wearItem(item.getId()) : interactInventory(itemName, EQUIP_ACTIONS);
    }

    private boolean equipAmmoStack(String itemName) {
        if (getEquippedQuantity(EquipmentInventorySlot.AMMO, itemName) > 0 && Rs2Inventory.itemQuantity(itemName) <= 0) return true;
        Rs2ItemModel ammo = Rs2Inventory.get(itemName, true);
        return ammo != null && (Rs2Bank.isOpen() ? Rs2Bank.wearItem(ammo.getId()) : interactInventory(itemName, "Wield", "Wear"));
    }

    private boolean withdrawTripSupplies() {
        String growthlingTool = normalize(config.growthlingToolName());
        if (!withdrawExact(growthlingTool, 1, "Growthling axe")) return false;
        int teleports = config.varrockTeleportCount();
        int requiredAir = teleports * 3;
        int requiredFire = teleports;
        int requiredLaw = teleports;
        if (config.strategy() == BryophytaStrategy.MAGIC_FIRE) {
            BryophytaFireSpell spell = config.fireSpell();
            int minimumAir = requiredAir + spell.getAirRunesPerCast();
            int minimumFire = requiredFire + spell.getFireRunesPerCast();
            if (!withdrawAllAvailableStack(AIR_RUNE, minimumAir, spell + " / Varrock Air runes")
                    || !withdrawAllAvailableStack(FIRE_RUNE, minimumFire, spell + " / Varrock Fire runes")
                    || !withdrawAllAvailableStack(spell.getCatalystRuneName(), 1, spell + " catalyst runes")) return false;
        } else if (!withdrawExact(AIR_RUNE, requiredAir, "Varrock Air runes") || !withdrawExact(FIRE_RUNE, requiredFire, "Varrock Fire runes")) return false;
        if (!withdrawExact(LAW_RUNE, requiredLaw, "Varrock Law runes")) return false;
        if (config.strategy() == BryophytaStrategy.MELEE && config.useStrengthPotion()
                && !withdrawExact(STRENGTH_POTION_4, config.strengthPotionAmount(), "Strength potion")) return false;
        if (!withdrawExact(normalize(config.foodName()), config.foodAmount(), "Food")) return false;
        if (config.withdrawMossyKey() && Rs2Inventory.itemQuantity(MOSSY_KEY) < 1 && bankQuantityExact(MOSSY_KEY) >= 1) {
            setStatus("Withdrawing Mossy key...");
            Rs2Bank.withdrawDeficit(MOSSY_KEY, 1, true);
            return false;
        }
        return true;
    }

    private boolean withdrawExact(String itemName, int amount, String purpose) {
        if (amount <= 0 || Rs2Inventory.itemQuantity(itemName) >= amount) return true;
        int need = amount - Rs2Inventory.itemQuantity(itemName);
        if (bankQuantityExact(itemName) < need) {
            failAndStop("Missing required " + purpose + " in bank: " + itemName + " x" + amount);
            return false;
        }
        setStatus("Withdrawing " + purpose + ": " + itemName + "...");
        Rs2Bank.withdrawDeficit(itemName, amount, true);
        return false;
    }

    private boolean withdrawAllAvailableStack(String itemName, int minimumRequired, String purpose) {
        int inventory = Rs2Inventory.itemQuantity(itemName);
        int bank = Rs2Bank.bankItems().stream().filter(item -> item != null && item.getName() != null && item.getName().equalsIgnoreCase(itemName)).mapToInt(Rs2ItemModel::getQuantity).sum();
        if ((long) inventory + bank < minimumRequired) {
            failAndStop("Missing required " + purpose + ": " + itemName + " (need at least " + minimumRequired + ")");
            return false;
        }
        if (bank <= 0) return inventory >= minimumRequired;
        setStatus("Withdrawing all " + itemName + "...");
        Rs2Bank.withdrawAll(itemName, true);
        return false;
    }

    private void restorePrayerAtVarrockAltar() {
        int realPrayer = Microbot.getClient().getRealSkillLevel(Skill.PRAYER);
        if (Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER) >= realPrayer) {
            prayerRestoredAfterBank = true;
            altarFailures = 0;
            altarInteractionPending = false;
            altarInteractionStartedAt = 0L;
            setState(BryophytaState.WALKING_TO_SEWERS, "Prayer full - heading to Varrock Sewers.");
            return;
        }
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null) return;
        setState(BryophytaState.RESTORING_PRAYER, "Restoring Prayer at Varrock altar...");
        if (!objectVisible(VARROCK_ALTAR_OBJECT_ID, VARROCK_ALTAR, 1)) {
            altarInteractionPending = false;
            walkToControlled(VARROCK_ALTAR_APPROACH, 2, "Walking to Varrock altar...");
            return;
        }
        if (altarInteractionPending) {
            if (System.currentTimeMillis() - altarInteractionStartedAt > ALTAR_INTERACTION_TIMEOUT_MS) {
                altarInteractionPending = false;
                if (++altarFailures >= 5) failAndStop("Could not Pray-at altar 14860 at 3253,3486,0 after five attempts.");
            } else setStatus("Altar interaction sent - checking Prayer level...");
            return;
        }
        if (Rs2Player.isAnimating()) return;
        boolean prayed = interactObject(VARROCK_ALTAR_OBJECT_ID, VARROCK_ALTAR, 1, "Pray-at")
                || interactObject(VARROCK_ALTAR_OBJECT_ID, VARROCK_ALTAR, 1, "Pray")
                || Microbot.getRs2TileObjectCache().query().withName("Altar").where(object -> VARROCK_ALTAR.equals(object.getWorldLocation())).interact("Pray-at");
        if (!prayed) {
            setStatus("Altar 14860 visible - waiting for an actionable Pray-at entry...");
            return;
        }
        altarInteractionPending = true;
        altarInteractionStartedAt = System.currentTimeMillis();
        setStatus("Altar interaction sent - checking Prayer level...");
    }

    private void navigateToBryophyta() {
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null) return;
        if (!isUnderground(player)) {
            navigateToSewerManhole(player);
            return;
        }
        manholeDescentPending = false;
        manholeDescentStartedAt = 0L;
        setState(BryophytaState.WALKING_TO_LAIR, "Walking through Varrock Sewers to Bryophyta...");
        if (handleLairEntryDialogue()) return;
        if (tryEnterNearbyLairGate()) return;
        if (handleNearbyWeb()) return;
        if (isDeadlyRedSpiderAttackingPlayer()) {
            handleSewerAggroWalk();
            return;
        }
        sewerAggroWalkSentAt = 0L;
        walkToControlled(BRYOPHYTA_SEWER_ENTRANCE, 4, "Walking through Varrock Sewers to Bryophyta...");
    }

    private void navigateToSewerManhole(WorldPoint player) {
        setState(BryophytaState.WALKING_TO_SEWERS, "Heading to Varrock sewer manhole...");
        if (manholeDescentPending) {
            if (isUnderground(player)) {
                manholeDescentPending = false;
                manholeDescentStartedAt = 0L;
                setState(BryophytaState.WALKING_TO_LAIR, "Varrock Sewers loaded - heading to Bryophyta.");
            } else if (System.currentTimeMillis() - manholeDescentStartedAt > MANHOLE_TRANSITION_TIMEOUT_MS) {
                manholeDescentPending = false;
                setStatus("Sewer transition did not occur - rechecking manhole state...");
            } else setStatus("Climb-down sent - checking for underground region...");
            return;
        }
        if (player.distanceTo(VARROCK_MANHOLE_APPROACH) > 1) {
            walkToControlled(VARROCK_MANHOLE_APPROACH, 1, "Walking to Varrock sewer entrance...");
            return;
        }
        boolean open = objectVisible(VARROCK_MANHOLE_OPEN_OBJECT_ID, VARROCK_MANHOLE, 2);
        boolean closed = !open && objectVisible(VARROCK_MANHOLE_CLOSED_OBJECT_ID, VARROCK_MANHOLE, 2);
        if (!open && !closed) {
            setStatus("At sewer entrance - waiting for manhole 881/882...");
            return;
        }
        if (open) {
            manholeOpenPending = false;
            if (Rs2Player.isMoving() || Rs2Player.isAnimating()) return;
            boolean climbed = interactObject(VARROCK_MANHOLE_OPEN_OBJECT_ID, VARROCK_MANHOLE, 2, "Climb-down")
                    || interactNamedObject("Manhole", VARROCK_MANHOLE, 2, "Climb-down");
            if (climbed) {
                manholeDescentPending = true;
                manholeDescentStartedAt = System.currentTimeMillis();
                setStatus("Climb-down sent - checking for sewer region...");
            }
            return;
        }
        if (manholeOpenPending) {
            setStatus("Open sent - checking for manhole 882...");
            return;
        }
        boolean opened = interactObject(VARROCK_MANHOLE_CLOSED_OBJECT_ID, VARROCK_MANHOLE, 2, "Open")
                || interactNamedObject("Manhole", VARROCK_MANHOLE, 2, "Open");
        manholeOpenPending = opened;
        setStatus(opened ? "Open sent - checking for manhole 882..." : "Manhole 881 visible but Open is not actionable yet...");
    }

    private boolean handleNearbyWeb() {
        boolean webPresent = Microbot.getRs2TileObjectCache().query().withName("Web").within(8).nearestOnClientThread() != null;
        if (!webPresent) {
            webSlashPending = false;
            webSlashSentAt = 0L;
            if (config.strategy() != BryophytaStrategy.MELEE && Rs2Equipment.isWearing(normalize(config.growthlingToolName()), true)) restoreMainWeaponIfNeeded();
            return false;
        }
        if (config.strategy() != BryophytaStrategy.MELEE && !Rs2Equipment.isWearing(normalize(config.growthlingToolName()), true)) {
            if (!equipGrowthlingTool()) failAndStop("Could not equip the Growthling axe to slash the Varrock Sewers web.");
            return true;
        }
        long now = System.currentTimeMillis();
        if (webSlashPending && now - webSlashSentAt < WEB_SLASH_RETRY_MS) {
            setStatus("Slash sent - checking whether web still exists...");
            return true;
        }
        webSlashPending = false;
        status = "Slashing visible sewer web...";
        webSlashPending = Microbot.getRs2TileObjectCache().query().withName("Web").within(8).interact("Slash");
        if (webSlashPending) webSlashSentAt = now;
        return true;
    }

    private boolean isDeadlyRedSpiderAttackingPlayer() {
        return Microbot.getRs2NpcCache().query().withName(DEADLY_RED_SPIDER).fromWorldView()
                .where(Rs2NpcModel::isInteractingWithPlayer).nearestOnClientThread() != null;
    }

    private void handleSewerAggroWalk() {
        setState(BryophytaState.WALKING_TO_LAIR, "Ignoring Deadly red spider - continuing to Bryophyta...");
        Rs2Player.eatAt(config.eatAtPercent());
        if (Rs2Player.isMoving()) return;
        long now = System.currentTimeMillis();
        if (now - sewerAggroWalkSentAt < SEWER_AGGRO_WALK_RETRY_MS) return;
        WorldPoint target = Rs2Walker.getCurrentTarget();
        if (target != null && target.distanceTo(BRYOPHYTA_SEWER_ENTRANCE) <= 4)
            Rs2Walker.clearWalkingRoute("KSP Bryophyta: spider interrupted sewer travel");
        Rs2Walker.walkTo(BRYOPHYTA_SEWER_ENTRANCE, 4);
        sewerAggroWalkSentAt = now;
    }

    private boolean tryEnterNearbyLairGate() {
        long now = System.currentTimeMillis();
        if (lairEntryPending) {
            if (isInsideLair()) { completeLairEntry(); return true; }
            if (serviceLairGateWidgetNow() || handleLairEntryDialogue()) return true;
            if (now - lairEntryStartedAt > LAIR_ENTRY_TRANSITION_TIMEOUT_MS) {
                clearLairEntryPending();
                setStatus("Bryophyta entry did not transition - rechecking gate 32534...");
                if (++entryFailures >= 4) failBryophytaEntry("Bryophyta entry dialogue/instance transition timed out after four attempts.");
            } else setState(BryophytaState.ENTERING_LAIR, "Checking for Bryophyta gate dialogue/instance...");
            return true;
        }
        if (!objectVisible(BRYOPHYTA_GATE_OBJECT_ID, BRYOPHYTA_SEWER_ENTRANCE, 8)) return false;
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player != null && player.distanceTo(BRYOPHYTA_SEWER_ENTRANCE) > 10) {
            walkToControlled(BRYOPHYTA_SEWER_ENTRANCE, 8, "Approaching Bryophyta gate...");
            return true;
        }
        cancelWalkerForBryophytaGate();
        if (handleLairEntryDialogue()) { startLairEntry(now); return true; }
        setState(BryophytaState.ENTERING_LAIR, "Interacting with visible Bryophyta gate 32534...");
        for (String action : GATE_ACTIONS) {
            if (interactBryophytaGate(action)) {
                startLairEntry(now);
                entryFailures = 0;
                serviceLairGateWidgetNow();
                return true;
            }
        }
        if (Microbot.getRs2TileObjectCache().query().withId(BRYOPHYTA_GATE_OBJECT_ID).within(BRYOPHYTA_SEWER_ENTRANCE, 8).interact()) {
            startLairEntry(now);
            entryFailures = 0;
            return true;
        }
        setStatus("Gate 32534 visible but no gate action is currently actionable...");
        return true;
    }

    private void cancelWalkerForBryophytaGate() {
        if (Rs2Walker.getCurrentTarget() != null) Rs2Walker.clearWalkingRoute("KSP Bryophyta: gate 32534 reached");
    }

    private boolean interactBryophytaGate(String action) {
        return Microbot.getRs2TileObjectCache().query().withId(BRYOPHYTA_GATE_OBJECT_ID).within(BRYOPHYTA_SEWER_ENTRANCE, 8).interact(action);
    }

    public void onWidgetLoaded(int groupId) {
        if (!lairEntryPending || state == BryophytaState.STOPPED) return;
        if (groupId == InterfaceID.DIALOG_OPTION || groupId == InterfaceID.DIALOG_NPC
                || groupId == InterfaceID.DIALOG_PLAYER || groupId == InterfaceID.DIALOG_SPRITE
                || groupId == InterfaceID.DIALOG_DOUBLE_SPRITE || groupId == 229) {
            lastGateDialogueFingerprint = "";
            gateContinueHandled = false;
            serviceLairGateWidgetNow();
        }
    }

    public void onClientTick() {
        if (isRunning() && lairEntryPending) serviceLairGateWidgetNow();
    }

    private boolean serviceLairGateWidgetNow() {
        if (!lairEntryPending) return false;
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget optionRoot = Microbot.getClient().getWidget(InterfaceID.DIALOG_OPTION, 1);
            if (optionRoot != null && !optionRoot.isHidden()) {
                Widget[] children = optionRoot.getDynamicChildren();
                StringBuilder fingerprint = new StringBuilder("option:");
                if (children != null) for (Widget child : children) if (child != null && !child.isHidden()) fingerprint.append(child.getText()).append('|');
                String fp = fingerprint.toString();
                gateContinueHandled = false;
                if (fp.equals(lastGateDialogueFingerprint)) return true;
                if (Rs2Dialogue.keyPressForDialogueOption(1)) {
                    lastGateDialogueFingerprint = fp;
                    setState(BryophytaState.ENTERING_LAIR, "Gate option widget visible - selected option 1.");
                    return true;
                }
            } else lastGateDialogueFingerprint = "";
            if (Rs2Dialogue.hasContinue()) {
                if (gateContinueHandled) return true;
                Rs2Dialogue.clickContinue();
                gateContinueHandled = true;
                setState(BryophytaState.ENTERING_LAIR, "Gate Continue widget visible - continued.");
                return true;
            }
            gateContinueHandled = false;
            return false;
        }).orElse(false);
    }

    private boolean handleLairEntryDialogue() {
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null || player.distanceTo(BRYOPHYTA_SEWER_ENTRANCE) > 12) return false;
        if (serviceLairGateWidgetNow()) return true;
        if (!Rs2Dialogue.hasSelectAnOption() && !Rs2Dialogue.hasContinue()) return false;
        startLairEntry(System.currentTimeMillis());
        return serviceLairGateWidgetNow();
    }

    private void completeLairEntry() {
        clearLairEntryPending();
        entryFailures = 0;
        Rs2Walker.clearWalkingRoute("KSP Bryophyta: entered lair");
        setState(BryophytaState.WAITING_FOR_RESPAWN, "Bryophyta lair entered - locating boss...");
    }

    private void failBryophytaEntry(String prefix) {
        String suffix = Rs2Inventory.contains(MOSSY_KEY, true)
                ? " A Mossy key is present; gate actions and the full confirmation dialogue were attempted."
                : " If this account has never permanently unlocked Bryophyta, bring a Mossy key once.";
        failAndStop(prefix + suffix);
    }

    private void walkToControlled(WorldPoint target, int distance, String walkingStatus) {
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null || player.distanceTo(target) <= distance) return;
        status = walkingStatus;
        if (Rs2Player.isMoving()) return;
        WorldPoint activeTarget = Rs2Walker.getCurrentTarget();
        if (activeTarget != null && activeTarget.distanceTo(target) <= distance) return;
        Rs2Walker.walkTo(target, distance);
    }

    private synchronized void handleGrowthlings(Rs2NpcModel growthling) {
        if (growthling == null || isGrowthlingDeadOrDying(growthling)) return;
        setState(BryophytaState.KILLING_GROWTHLINGS, "Growthling - using " + config.growthlingToolName() + ".");
        if (!equipGrowthlingTool()) {
            failAndStop("Required Growthling axe is missing/unusable: " + config.growthlingToolName());
            return;
        }
        int index = growthling.getIndex();
        long now = System.currentTimeMillis();
        markGrowthlingSeen();
        if (activeGrowthlingIndex == index && growthlingAttackSentAt > 0L) {
            if (isPlayerInteractingWith(growthling) || Rs2Player.isMoving() || Rs2Player.isAnimating()
                    || now - growthlingAttackSentAt < GROWTHLING_ATTACK_RETRY_MS) {
                setStatus("Growthling " + index + " engaged - waiting for kill...");
                return;
            }
            setStatus("Growthling " + index + " attack stalled - retrying...");
        } else {
            activeGrowthlingIndex = index;
            growthlingAttackSentAt = 0L;
            setStatus("Attacking Growthling " + index + "...");
        }
        growthling.click("Attack");
        growthlingAttackSentAt = now;
    }

    private boolean equipGrowthlingTool() {
        String tool = normalize(config.growthlingToolName());
        if (Rs2Equipment.isWearing(tool, true)) return true;
        if (!Rs2Inventory.contains(tool, true)) return false;
        return interactEquip(tool);
    }

    private boolean restoreMainWeaponIfNeeded() {
        if (mainWeapon == null || mainWeapon.isBlank()) {
            restockRequired = true;
            loadoutVerified = false;
            return false;
        }
        if (Rs2Equipment.isWearing(mainWeapon, true)) return true;
        if (!Rs2Inventory.contains(mainWeapon, true)) {
            restockRequired = true;
            loadoutVerified = false;
            return false;
        }
        setStatus("Restoring " + mainWeapon + "...");
        return interactEquip(mainWeapon);
    }

    private void handleBryophyta(Rs2NpcModel bryophyta) {
        setState(BryophytaState.FIGHTING_BRYOPHYTA, "Fighting Bryophyta - " + config.strategy());
        if (!restoreMainWeaponIfNeeded()) return;
        if (config.strategy() == BryophytaStrategy.MAGIC_FIRE && !ensureSelectedSpellAutocast()) return;
        if (config.strategy() != BryophytaStrategy.MELEE && maintainRange(bryophyta)) return;
        attackWithCurrentWeapon(bryophyta);
    }

    private void attackWithCurrentWeapon(Rs2NpcModel bryophyta) {
        if (bryophyta == null || bryophyta.isDead()) { bossAttackPending = false; return; }
        if (isPlayerInteractingWith(bryophyta)) {
            bossAttackPending = false;
            status = "Engaged with Bryophyta...";
            return;
        }
        if (bossAttackPending) {
            if (Rs2Player.isMoving() || Rs2Player.isAnimating()) return;
            bossAttackPending = false;
        }
        if (Rs2Player.isMoving() || Rs2Player.isAnimating()) return;
        status = "Attacking visible Bryophyta...";
        bossAttackPending = bryophyta.click("Attack");
        if (!bossAttackPending) status = "Bryophyta visible - Attack not actionable yet...";
    }

    private boolean isPlayerInteractingWith(Rs2NpcModel npc) {
        return npc != null && npc.getNpc() != null && playerInteractionTarget() == npc.getNpc();
    }

    private boolean setSelectedAutocastWithoutMagicTab(BryophytaFireSpell selectedSpell) {
        if (selectedSpell == null || selectedSpell.getCombatSpell() == null) return false;
        Rs2CombatSpells combatSpell = selectedSpell.getCombatSpell();
        if (Rs2Magic.getCurrentAutoCastSpell() == combatSpell) return true;
        if (Microbot.getClient().getRealSkillLevel(Skill.MAGIC) < combatSpell.getRequiredLevel()) {
            failAndStop(selectedSpell + " requires Magic level " + combatSpell.getRequiredLevel() + ".");
            return false;
        }
        if (Rs2Tab.getCurrentTab() != InterfaceTab.COMBAT) {
            setStatus("Opening Combat tab for autocast...");
            Rs2Tab.switchTo(InterfaceTab.COMBAT);
            return false;
        }
        if (Rs2Widget.isWidgetVisible(201, 1)) {
            Widget options = Rs2Widget.getWidget(201, 1);
            Widget spell = options == null ? null : Rs2Widget.findWidget(combatSpell.getMagicAction().getSprite(), List.of(options));
            if (spell == null) {
                failAndStop(selectedSpell + " is not available in this weapon's autocast selector.");
                return false;
            }
            setStatus("Selecting visible " + selectedSpell + " autocast widget...");
            Rs2Widget.clickWidget(spell);
            return false;
        }
        Widget spellButton = Rs2Widget.getWidget(WidgetInfo.COMBAT_SPELL_BOX.getId());
        if (spellButton == null) {
            failAndStop("Selected Magic weapon does not expose an autocast Spell button.");
            return false;
        }
        setStatus("Opening visible autocast selector...");
        Rs2Widget.clickWidget(spellButton);
        return false;
    }

    private boolean ensureSelectedSpellAutocast() {
        if (config.strategy() != BryophytaStrategy.MAGIC_FIRE) return true;
        BryophytaFireSpell spell = config.fireSpell();
        if (Rs2Magic.getCurrentAutoCastSpell() == spell.getCombatSpell()) return true;
        return setSelectedAutocastWithoutMagicTab(spell);
    }

    private boolean maintainRange(Rs2NpcModel bryophyta) {
        if (bryophyta == null || bryophyta.getNpc() == null) return false;
        WorldArea[] areas = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (Microbot.getClient() == null || Microbot.getClient().getLocalPlayer() == null) return null;
            net.runelite.api.Player player = Microbot.getClient().getLocalPlayer();
            WorldView playerView = player.getWorldView(), bossView = bryophyta.getNpc().getWorldView();
            if (playerView == null || bossView == null || playerView.getId() != bossView.getId()) return null;
            return new WorldArea[]{player.getWorldArea(), bryophyta.getNpc().getWorldArea()};
        }).orElse(null);
        if (areas == null || areas[0] == null || areas[1] == null) return false;
        WorldArea playerArea = areas[0], bossArea = areas[1];
        int targetDistance = config.minimumRangeDistance(), currentDistance = bossArea.distanceTo(playerArea);
        if (currentDistance == targetDistance) return false;
        if (currentDistance == Integer.MAX_VALUE || currentDistance > 30) {
            setStatus("Waiting for Bryophyta combat position...");
            return true;
        }
        if (Rs2Player.isMoving()) return true;
        WorldPoint player = new WorldPoint(playerArea.getX(), playerArea.getY(), playerArea.getPlane());
        WorldPoint target = findBestRangeTile(player, bossArea, bryophyta.getWorldView(), targetDistance);
        if (target == null) {
            setStatus("Could not find a walkable " + targetDistance + "-tile position around Bryophyta.");
            return true;
        }
        WorldPoint walkerTarget = Rs2Walker.getCurrentTarget();
        if (target.equals(walkerTarget)) return true;
        setStatus("Moving from " + currentDistance + " to " + targetDistance + " tiles from Bryophyta...");
        Rs2Walker.walkFastCanvas(target);
        return true;
    }

    private WorldPoint findBestRangeTile(WorldPoint player, WorldArea bossArea, WorldView worldView, int distance) {
        int minX = bossArea.getX() - distance;
        int maxX = bossArea.getX() + bossArea.getWidth() - 1 + distance;
        int minY = bossArea.getY() - distance;
        int maxY = bossArea.getY() + bossArea.getHeight() - 1 + distance;
        List<WorldPoint> ring = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            ring.add(new WorldPoint(x, minY, player.getPlane()));
            ring.add(new WorldPoint(x, maxY, player.getPlane()));
        }
        for (int y = minY + 1; y < maxY; y++) {
            ring.add(new WorldPoint(minX, y, player.getPlane()));
            ring.add(new WorldPoint(maxX, y, player.getPlane()));
        }
        return ring.stream()
                .filter(Rs2Tile::isWalkable)
                .filter(tile -> bossArea.distanceTo(tile) == distance)
                .filter(tile -> worldView == null || tile.toWorldArea().hasLineOfSightTo(worldView, bossArea))
                .min(Comparator.comparingInt(player::distanceTo)).orElse(null);
    }

    private synchronized void registerKill() {
        if (killRegisteredForCycle) return;
        kills++;
        killRegisteredForCycle = true;
        bossMissingScans = postKillEmptyScans = 0;
        postKillKeysAtStart = Rs2Inventory.itemQuantity(MOSSY_KEY);
        setState(BryophytaState.LOOTING, "Bryophyta defeated - waiting for ground drops.");
        Rs2Prayer.disableAllPrayers();
    }

    public void onBryophytaDespawned() {
        if (!isRunning() || !bossWasPresent || killRegisteredForCycle) return;
        if (state == BryophytaState.FIGHTING_BRYOPHYTA || state == BryophytaState.KILLING_GROWTHLINGS) registerKill();
    }

    private void handlePostKill(long now) {
        int currentKeys = Rs2Inventory.itemQuantity(MOSSY_KEY);
        if (config.openRewardChest() && currentKeys > postKillKeysAtStart) {
            setState(BryophytaState.OPENING_CHEST, "New Mossy key looted - opening reward chest...");
            handleChest(now);
            return;
        }
        if (config.lootBossDrops()) {
            if (attemptBossLoot()) { postKillEmptyScans = 0; return; }
            if (++postKillEmptyScans < POST_KILL_EMPTY_SCANS) {
                setState(BryophytaState.LOOTING, "Waiting for Bryophyta drops (" + postKillEmptyScans + "/" + POST_KILL_EMPTY_SCANS + ")...");
                return;
            }
        }
        if (currentKeys <= 0) { handleQuickExitReset(now); return; }
        if (config.openRewardChest()) { handleChest(now); return; }
        setState(BryophytaState.WAITING_FOR_RESPAWN, "Waiting for next Bryophyta cycle...");
        bossWasPresent = killRegisteredForCycle = false;
    }

    private boolean attemptBossLoot() {
        for (String itemName : PRIORITY_LOOT) if (pickupNamedGroundItem(itemName)) return true;
        if (config.lootValueThreshold() <= 0) return false;
        Rs2TileItemModel valuable = Microbot.getRs2TileItemCache().query().fromWorldView().within(8)
                .where(item -> item.isLootAble() && item.getTotalValue() >= config.lootValueThreshold()).nearestOnClientThread();
        if (valuable == null) return false;
        setStatus("Looting " + valuable.getName() + "...");
        valuable.pickup();
        return true;
    }

    private boolean pickupNamedGroundItem(String itemName) {
        Rs2TileItemModel item = Microbot.getRs2TileItemCache().query().fromWorldView().withName(itemName).within(8).nearestOnClientThread();
        if (item == null || !item.isLootAble()) return false;
        status = "Looting " + itemName + "...";
        item.pickup();
        return true;
    }

    private void handleQuickExitReset(long now) {
        if (quickExitPending) {
            if (!isInstancedRegion()) {
                completeQuickExitReset();
                return;
            }
            if (now - quickExitStartedAt > QUICK_EXIT_TRANSITION_TIMEOUT_MS) {
                quickExitPending = false;
                quickExitStartedAt = 0L;
                setStatus("Quick-exit transition timed out - retrying Rock Pile...");
            } else setState(BryophytaState.WAITING_FOR_RESPAWN, "Quick-exit sent - waiting for Varrock Sewers...");
            return;
        }
        setState(BryophytaState.WAITING_FOR_RESPAWN, "No Mossy key - using Quick-exit Rock Pile...");
        if (!Microbot.getRs2TileObjectCache().query().withId(BRYOPHYTA_EXIT_OBJECT_ID).interact("Quick-exit")) {
            setStatus("Waiting for Quick-exit Rock Pile 32535...");
            return;
        }
        quickExitPending = true;
        quickExitStartedAt = now;
        setStatus("Quick-exit sent - waiting for Varrock Sewers...");
    }

    private void completeQuickExitReset() {
        quickExitPending = false;
        quickExitStartedAt = 0L;
        bossWasPresent = killRegisteredForCycle = false;
        postKillEmptyScans = postKillKeysAtStart = 0;
        growthlingWaveActive = false;
        growthlingEmptyScans = 0;
        activeGrowthlingIndex = -1;
        growthlingAttackSentAt = 0L;
        clearLairEntryPending();
        setState(BryophytaState.WALKING_TO_LAIR, "Quick-exit complete - re-entering Bryophyta.");
    }

    private void handleChest(long now) {
        chestKeysBeforeOpen = Rs2Inventory.itemQuantity(MOSSY_KEY);
        if (chestKeysBeforeOpen <= 0) { handleQuickExitReset(now); return; }
        if (!objectVisible(BRYOPHYTA_CHEST_OBJECT_ID, Rs2Player.getWorldLocation(), 20)) {
            setState(BryophytaState.OPENING_CHEST, "Mossy key ready - waiting for reward chest 56378...");
            return;
        }
        setState(BryophytaState.OPENING_CHEST, "Opening Bryophyta reward chest with Mossy key...");
        if (!Microbot.getRs2TileObjectCache().query().withId(BRYOPHYTA_CHEST_OBJECT_ID).within(20).interact("Open")) {
            setStatus("Chest 56378 visible - waiting for Open interaction...");
            return;
        }
        chestAttempts++;
        ignoredChestGroundDrops.clear();
        pendingChestLootKey = "";
        chestLootPending = true;
        chestOpenRequestedAt = now;
        chestOpenConfirmedAt = 0L;
        setState(BryophytaState.LOOTING, "Chest Open sent - checking key/reward state...");
    }

    private void handleChestLoot(long now) {
        int currentKeys = Rs2Inventory.itemQuantity(MOSSY_KEY);
        if (chestOpenConfirmedAt == 0L && currentKeys < chestKeysBeforeOpen) chestOpenConfirmedAt = now;
        if (chestOpenConfirmedAt == 0L) {
            if (now - chestOpenRequestedAt > CHEST_OPEN_TIMEOUT_MS) {
                chestLootPending = false;
                setStatus("Chest key was not consumed - rechecking chest...");
            } else setState(BryophytaState.LOOTING, "Checking for Mossy key consumption...");
            return;
        }
        Rs2TileItemModel loot = getNearestChestGroundLoot();
        if (!pendingChestLootKey.isEmpty()) {
            boolean stillPresent = loot != null && pendingChestLootKey.equals(groundLootKey(loot.getId(), loot.getWorldLocation()));
            if (stillPresent) {
                setStatus("Take sent - checking ground item removal...");
                return;
            }
            pendingChestLootKey = "";
            loot = getNearestChestGroundLoot();
        }
        if (loot == null) {
            finishChestCycle("Chest has no remaining ground rewards - waiting for Bryophyta respawn.");
            return;
        }
        int needed = inventorySlotsNeededFor(loot);
        if (!ensureLootSpace(needed)) {
            setStatus("Chest reward visible - making " + needed + " inventory slot(s)...");
            return;
        }
        String key = groundLootKey(loot.getId(), loot.getWorldLocation());
        setState(BryophytaState.LOOTING, "Taking visible chest reward: " + loot.getName() + "...");
        if (loot.pickup()) pendingChestLootKey = key;
    }

    private Rs2TileItemModel getNearestChestGroundLoot() {
        return Microbot.getRs2TileItemCache().query().fromWorldView().within(10)
                .where(item -> item != null && item.isLootAble() && isChestLootCandidate(item)).nearestOnClientThread();
    }

    private boolean isChestLootCandidate(Rs2TileItemModel item) {
        String name = item.getName();
        return name != null && !name.equalsIgnoreCase("Giant bones") && !name.equalsIgnoreCase("Big bones")
                && !ignoredChestGroundDrops.contains(groundLootKey(item.getId(), item.getWorldLocation()));
    }

    private static String groundLootKey(int itemId, WorldPoint point) {
        return point == null ? String.valueOf(itemId) : itemId + "@" + point.getX() + "," + point.getY() + "," + point.getPlane();
    }

    private int inventorySlotsNeededFor(Rs2TileItemModel item) {
        if (item == null) return 0;
        if (item.isStackable()) return Rs2Inventory.hasItem(item.getId()) ? 0 : 1;
        return Math.max(1, Math.min(item.getQuantity(), Rs2Inventory.capacity()));
    }

    private boolean ensureLootSpace(int requiredSlots) {
        if (requiredSlots <= 0 || freeInventorySlots() >= requiredSlots) return true;
        if (freeOneLootSlot()) setStatus("Inventory-space action sent - checking inventory container...");
        return false;
    }

    private int freeInventorySlots() { return Math.max(0, Rs2Inventory.capacity() - Rs2Inventory.all().size()); }

    private boolean freeOneLootSlot() {
        String foodName = normalize(config.foodName());
        if (foodName.isEmpty() || Rs2Inventory.get(foodName, true) == null) return false;
        int currentHp = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
        int realHp = Microbot.getClient().getRealSkillLevel(Skill.HITPOINTS);
        if (currentHp < realHp && interactInventory(foodName, "Eat")) {
            setStatus("Eating " + foodName + " to make room for chest loot...");
            return true;
        }
        Rs2ItemModel food = Rs2Inventory.get(foodName, true);
        WorldPoint dropTile = Rs2Player.getWorldLocation();
        if (food != null && interactInventory(foodName, "Drop")) {
            ignoredChestGroundDrops.add(groundLootKey(food.getId(), dropTile));
            setStatus("Dropping " + foodName + " to make room for chest loot...");
            return true;
        }
        return false;
    }

    private void finishChestCycle(String message) {
        chestLootPending = false;
        ignoredChestGroundDrops.clear();
        chestKeysBeforeOpen = 0;
        chestOpenRequestedAt = chestOpenConfirmedAt = 0L;
        pendingChestLootKey = "";
        bossWasPresent = false;
        killRegisteredForCycle = false;
        postKillKeysAtStart = 0;
        setState(BryophytaState.WAITING_FOR_RESPAWN, message);
    }

    private boolean verifyEquipmentLoadout() {
        for (EquipmentInventorySlot slot : BryophytaLoadout.CONFIGURABLE_SLOTS) {
            if (equipmentSettings.isExplicitEmpty(config.strategy(), slot) && Rs2Equipment.get(slot) != null) return false;
        }
        Map<EquipmentInventorySlot, String> desired = equipmentSettings.equipmentFor(config.strategy());
        for (Map.Entry<EquipmentInventorySlot, String> entry : desired.entrySet()) {
            EquipmentInventorySlot slot = entry.getKey();
            String name = entry.getValue();
            if (slot == EquipmentInventorySlot.AMMO) {
                if (getEquippedQuantity(slot, name) <= 0) return false;
                continue;
            }
            Rs2ItemModel item = Rs2Equipment.get(slot);
            if (item == null || item.getName() == null || !item.getName().equalsIgnoreCase(name)) return false;
        }
        loadoutVerified = true;
        return true;
    }

    private int bankQuantityExact(String name) {
        if (name == null || name.isBlank()) return 0;
        return Rs2Bank.bankItems().stream().filter(item -> item != null && item.getName() != null && item.getName().equalsIgnoreCase(name)).mapToInt(Rs2ItemModel::getQuantity).sum();
    }

    private String selectedAmmoName() { return equipmentSettings.equipmentFor(config.strategy()).get(EquipmentInventorySlot.AMMO); }

    private boolean verifyFullTripSupplies() {
        if (!hasVarrockRuneTargets() || !hasGrowthlingTool()) return false;
        if (Rs2Inventory.itemQuantity(normalize(config.foodName())) < config.foodAmount()) return false;
        if (requiresStrengthPotion() && Rs2Inventory.itemQuantity(STRENGTH_POTION_4) < config.strengthPotionAmount()) return false;
        if (config.strategy() == BryophytaStrategy.RANGED && selectedAmmoName() != null
                && getEquippedQuantity(EquipmentInventorySlot.AMMO, selectedAmmoName()) <= 0) return false;
        if (config.strategy() == BryophytaStrategy.MAGIC_FIRE) {
            BryophytaFireSpell spell = config.fireSpell();
            if (Rs2Inventory.itemQuantity(AIR_RUNE) < config.varrockTeleportCount() * 3 + spell.getAirRunesPerCast()) return false;
            if (Rs2Inventory.itemQuantity(FIRE_RUNE) < config.varrockTeleportCount() + spell.getFireRunesPerCast()) return false;
            if (Rs2Inventory.itemQuantity(spell.getCatalystRuneName()) < 1) return false;
        }
        return true;
    }

    private boolean hasMinimumTripSupplies() {
        if (!hasVarrockTeleportRunesForOne()) {
            failAndStop("Emergency Varrock teleport runes are missing before entering the sewers.");
            return false;
        }
        if (!hasGrowthlingTool()) {
            failAndStop("Growthling axe is missing before entering the sewers: " + normalize(config.growthlingToolName()));
            return false;
        }
        if (Rs2Inventory.itemQuantity(normalize(config.foodName())) <= config.teleportAtFoodCount()) return false;
        if (requiresStrengthPotion() && !hasAnyStrengthPotionDose()) {
            failAndStop("Strength potion is missing before entering the sewers.");
            return false;
        }
        return true;
    }

    private boolean hasGrowthlingTool() {
        String tool = normalize(config.growthlingToolName());
        return Rs2Inventory.contains(tool, true) || Rs2Equipment.isWearing(tool, true);
    }
    private boolean requiresStrengthPotion() { return config.strategy() == BryophytaStrategy.MELEE && config.useStrengthPotion(); }
    private boolean hasVarrockRuneTargets() {
        int teleports = config.varrockTeleportCount();
        return Rs2Inventory.itemQuantity(AIR_RUNE) >= teleports * 3
                && Rs2Inventory.itemQuantity(FIRE_RUNE) >= teleports
                && Rs2Inventory.itemQuantity(LAW_RUNE) >= teleports;
    }
    private boolean hasVarrockTeleportRunesForOne() {
        return Rs2Inventory.itemQuantity(AIR_RUNE) >= 3 && Rs2Inventory.itemQuantity(FIRE_RUNE) >= 1 && Rs2Inventory.itemQuantity(LAW_RUNE) >= 1;
    }

    private int getEquippedQuantity(EquipmentInventorySlot slot, String exactName) {
        Rs2ItemModel item = Rs2Equipment.get(slot);
        if (item == null || item.getName() == null || !item.getName().equalsIgnoreCase(exactName)) return 0;
        return item.getQuantity();
    }

    private Rs2NpcModel getBryophyta() {
        return Microbot.getRs2NpcCache().query().withId(BRYOPHYTA_NPC_ID).fromWorldView().nearestOnClientThread();
    }

    private Rs2NpcModel getNearestGrowthling() {
        if (activeGrowthlingIndex >= 0) {
            Rs2NpcModel active = findGrowthlingByIndex(activeGrowthlingIndex);
            if (isGrowthlingActionable(active)) { markGrowthlingSeen(); return active; }
            activeGrowthlingIndex = -1;
            growthlingAttackSentAt = 0L;
        }
        Rs2NpcModel next = Microbot.getRs2NpcCache().query().withId(GROWTHLING_NPC_ID).fromWorldView()
                .where(this::isGrowthlingActionable).nearestOnClientThread();
        if (next != null) markGrowthlingSeen();
        else if (Microbot.getRs2NpcCache().query().withId(GROWTHLING_NPC_ID).fromWorldView().nearestOnClientThread() != null) growthlingWaveActive = true;
        return next;
    }

    private Rs2NpcModel findGrowthlingByIndex(int index) {
        return Microbot.getRs2NpcCache().query().withId(GROWTHLING_NPC_ID).fromWorldView()
                .where(npc -> npc != null && npc.getIndex() == index).nearestOnClientThread();
    }

    private void markGrowthlingSeen() {
        growthlingWaveActive = true;
        growthlingEmptyScans = 0;
    }

    private boolean awaitGrowthlingWaveClear() {
        if (!growthlingWaveActive) return false;
        if (++growthlingEmptyScans < 3) {
            setState(BryophytaState.KILLING_GROWTHLINGS, "Rechecking Growthling NPC 8194 cache (" + growthlingEmptyScans + "/3)...");
            return true;
        }
        growthlingWaveActive = false;
        growthlingEmptyScans = 0;
        activeGrowthlingIndex = -1;
        growthlingAttackSentAt = 0L;
        return false;
    }

    public void onGrowthlingSpawned(int index) {
        if (!isRunning()) return;
        growthlingEmptyScans = 0;
        markGrowthlingSeen();
    }

    public void onGrowthlingDespawned(int index) {
        if (!isRunning()) return;
        if (activeGrowthlingIndex == index) {
            activeGrowthlingIndex = -1;
            growthlingAttackSentAt = 0L;
        }
        growthlingWaveActive = true;
    }

    private boolean isGrowthlingDeadOrDying(Rs2NpcModel npc) {
        if (npc == null || npc.isDead()) return true;
        int scale = npc.getHealthScale();
        return scale > 0 && npc.getHealthRatio() <= 0;
    }

    private boolean isGrowthlingActionable(Rs2NpcModel npc) { return npc != null && !isGrowthlingDeadOrDying(npc); }

    private boolean isInstancedRegion() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient() != null && Microbot.getClient().isInInstancedRegion()).orElse(false);
    }

    private boolean isInsideLair() {
        if (isInstancedRegion()) return true;
        if (getBryophyta() != null || getNearestGrowthling() != null) return true;
        return Microbot.getRs2TileObjectCache().query().withId(BRYOPHYTA_CHEST_OBJECT_ID).fromWorldView().within(30).nearestOnClientThread() != null;
    }

    private boolean isOnSurface() {
        WorldPoint point = Rs2Player.getWorldLocation();
        return point != null && !isUnderground(point) && !isInsideLair();
    }
    private static boolean isUnderground(WorldPoint point) { return point != null && point.getY() > 9000; }

    public void confirmManholeDescent() {
        manholeDescentPending = true;
        manholeDescentStartedAt = System.currentTimeMillis();
        setState(BryophytaState.WALKING_TO_SEWERS, "Manhole descent confirmed - loading Varrock Sewers...");
    }

    private void markRestockComplete() {
        restockRequired = false;
        prayerRestoredAfterBank = false;
        loadoutVerified = true;
        altarFailures = 0;
        altarInteractionPending = false;
        altarInteractionStartedAt = 0L;
    }

    private void resetTransitions() {
        quickExitPending = altarInteractionPending = manholeDescentPending = manholeOpenPending = lairEntryPending = false;
        webSlashPending = bankClosePending = false;
        quickExitStartedAt = altarInteractionStartedAt = manholeDescentStartedAt = lairEntryStartedAt = 0L;
        webSlashSentAt = sewerAggroWalkSentAt = 0L;
        lastGateDialogueFingerprint = "";
        gateContinueHandled = false;
    }
    private void startLairEntry(long now) { lairEntryPending = true; if (lairEntryStartedAt == 0L) lairEntryStartedAt = now; }
    private void clearLairEntryPending() {
        lairEntryPending = false;
        lairEntryStartedAt = 0L;
        lastGateDialogueFingerprint = "";
        gateContinueHandled = false;
    }
    private void setStatus(String newStatus) { status = newStatus; Microbot.status = newStatus; }

    private boolean interactInventory(String itemName, String... actions) {
        Rs2ItemModel item = Rs2Inventory.get(itemName, true);
        if (item == null || item.getInventoryActions() == null) return false;
        for (String wanted : actions) for (String available : item.getInventoryActions())
            if (available != null && available.equalsIgnoreCase(wanted)) return Rs2Inventory.interact(item, available);
        return false;
    }

    private boolean objectVisible(int id, WorldPoint center, int radius) {
        return Microbot.getRs2TileObjectCache().query().withId(id).within(center, radius).nearestOnClientThread() != null;
    }
    private boolean interactObject(int id, WorldPoint center, int radius, String action) {
        return Microbot.getRs2TileObjectCache().query().withId(id).within(center, radius).interact(action);
    }
    private boolean interactNamedObject(String name, WorldPoint center, int radius, String action) {
        return Microbot.getRs2TileObjectCache().query().withName(name).within(center, radius).interact(action);
    }
    private net.runelite.api.Actor playerInteractionTarget() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient() == null || Microbot.getClient().getLocalPlayer() == null
                ? null : Microbot.getClient().getLocalPlayer().getInteracting()).orElse(null);
    }
    private static String normalize(String value) { return value == null ? "" : value.trim(); }
    private static boolean isInterruption(Throwable error) {
        if (Thread.currentThread().isInterrupted()) return true;
        for (Throwable cause = error; cause != null; cause = cause.getCause())
            if (cause instanceof InterruptedException) return true;
        return false;
    }
    private void setState(BryophytaState newState, String newStatus) { state = newState; setStatus(newStatus); }

    private void failAndStop(String reason) {
        if (state == BryophytaState.STOPPED) return;
        state = BryophytaState.MISSING_REQUIRED_ITEM;
        status = reason;
        Microbot.status = reason;
        Microbot.showMessage("KSP Bryophyta: " + reason);
        Rs2Prayer.disableAllPrayers();
        state = BryophytaState.STOPPED;
        super.shutdown();
    }

    public BryophytaState getState() { return state; }
    public String getStatus() { return status; }
    public int getKills() { return kills; }
    public int getMossyKeys() { return mossyKeys; }
    public int getChestAttempts() { return chestAttempts; }
    public int getFoodRemaining() { return foodRemaining; }
    public int getPrayerPoints() { return prayerPoints; }
    public void setStopped(String reason) { state = BryophytaState.STOPPED; status = reason; Microbot.status = reason; }

    @Override
    public void shutdown() {
        Rs2Prayer.disableAllPrayers();
        if (state != BryophytaState.STOPPED) setStopped("Stopped.");
        super.shutdown();
    }
}
