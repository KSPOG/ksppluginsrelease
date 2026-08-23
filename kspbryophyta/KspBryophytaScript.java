package net.runelite.client.plugins.microbot.kspbryophyta;

import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Skill;
import net.runelite.api.WorldView;
import net.runelite.api.gameval.VarPlayerID;
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
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
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
    private static final int BRYOPHYTA_NPC_ID = 8195;
    private static final int GROWTHLING_NPC_ID = 8194;
    private static final int BRYOPHYTA_CHEST_OBJECT_ID = 56378;
    private static final int BRYOPHYTA_GATE_OBJECT_ID = 32534;
    private static final int VARROCK_MANHOLE_CLOSED_OBJECT_ID = 881;
    private static final int VARROCK_MANHOLE_OPEN_OBJECT_ID = 882;
    private static final int VARROCK_ALTAR_OBJECT_ID = 14860;

    private static final int LOOP_DELAY_MS = 400;
    private static final long BOSS_MISSING_CONFIRM_MS = 1200L;
    private static final long POST_KILL_LOOT_WINDOW_MS = 2400L;
    private static final long GROWTHLING_ATTACK_RETRY_MS = 2200L;
    private static final long BOSS_ATTACK_RETRY_MS = 2200L;
    private static final long AUTOCAST_RETRY_MS = 1500L;
    private static final long ENTRY_RETRY_MS = 1800L;
    private static final long LAIR_ENTRY_TRANSITION_TIMEOUT_MS = 10_000L;
    private static final long LAIR_DIALOGUE_RETRY_MS = 350L;
    private static final long CHEST_RETRY_MS = 1800L;
    private static final long ALTAR_RETRY_MS = 850L;
    private static final long ALTAR_INTERACTION_TIMEOUT_MS = 6_000L;
    private static final long MANHOLE_RETRY_MS = 900L;
    private static final long MANHOLE_TRANSITION_TIMEOUT_MS = 8_000L;
    private static final long WALK_REISSUE_MS = 2800L;
    private static final long WEB_RETRY_MS = 1800L;
    private static final long RANGE_MOVE_RETRY_MS = 650L;
    private static final long AUTO_RETALIATE_RETRY_MS = 1500L;

    private static final String AIR_RUNE = "Air rune";
    private static final String FIRE_RUNE = "Fire rune";
    private static final String LAW_RUNE = "Law rune";
    private static final String MOSSY_KEY = "Mossy key";
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
    private int autocastFailures;

    private long lastBossSeenAt;
    private long killRegisteredAt;
    private long lastGrowthlingClickAt;
    private long lastBossAttackClickAt;
    private long lastAutocastAttemptAt;
    private long lastEntryAttemptAt;
    private boolean lairEntryPending;
    private long lairEntryStartedAt;
    private long lastLairDialogueActionAt;
    private long lastChestAttemptAt;
    private long lastAltarAttemptAt;
    private boolean altarInteractionPending;
    private long altarInteractionStartedAt;
    private long lastManholeAttemptAt;
    private boolean manholeDescentPending;
    private long manholeDescentStartedAt;
    private long lastWalkIssuedAt;
    private long lastWebAttemptAt;
    private long lastRangeMoveAt;
    private long lastAutoRetaliateAttemptAt;
    private final Set<Integer> handledGrowthlings = new HashSet<>();

    @Inject
    public KspBryophytaScript(BryophytaEquipmentSettings equipmentSettings) {
        this.equipmentSettings = equipmentSettings;
    }
    public boolean run(KspBryophytaConfig config) {
        this.config = config;
        resetRuntimeState();
        Microbot.enableAutoRunOn = true;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || state == BryophytaState.STOPPED || !super.run()) {
                    return;
                }

                updateCounters();
                if (!validateStaticRequirements()) {
                    return;
                }

                if (!Rs2Bank.isOpen() && !ensureAutoRetaliateDisabled()) {
                    return;
                }

                if (isInsideLair()) {
                    handleInsideLair();
                } else {
                    handleOutsideLair();
                }
            } catch (Exception ex) {
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
        entryFailures = altarFailures = autocastFailures = 0;
        lastBossSeenAt = killRegisteredAt = lastGrowthlingClickAt = lastBossAttackClickAt = 0L;
        lastAutocastAttemptAt = lastEntryAttemptAt = lastLairDialogueActionAt = lastChestAttemptAt = 0L;
        lastAltarAttemptAt = lastManholeAttemptAt = lastWalkIssuedAt = lastWebAttemptAt = 0L;
        lastRangeMoveAt = lastAutoRetaliateAttemptAt = 0L;

        bossWasPresent = killRegisteredForCycle = prayerRestoredAfterBank = loadoutVerified = false;
        restockRequired = true;
        mainWeapon = equipmentSettings.mainWeaponFor(config.strategy());
        resetTransitions();
        handledGrowthlings.clear();
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

        handleSurvival();

        Rs2NpcModel growthling = getNearestGrowthling();
        if (growthling != null) {
            handleGrowthlings(growthling);
            return;
        }

        if (!restoreMainWeaponIfNeeded()) {
            return;
        }

        Rs2NpcModel bryophyta = getBryophyta();
        long now = System.currentTimeMillis();

        if (bryophyta != null && !bryophyta.isDead()) {
            bossWasPresent = true;
            lastBossSeenAt = now;
            killRegisteredForCycle = false;
            handleBryophyta(bryophyta);
            return;
        }

        if (bossWasPresent
                && !killRegisteredForCycle
                && now - lastBossSeenAt >= BOSS_MISSING_CONFIRM_MS) {
            registerKill();
        }

        if (killRegisteredForCycle) {
            handlePostKill(now);
            return;
        }

        setState(BryophytaState.WAITING_FOR_RESPAWN, "Waiting for Bryophyta...");
    }

    private void handleOutsideLair() {
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null) {
            return;
        }

        maintainTravelPrayer();

        if (restockRequired) {
            if (isUnderground(player)) {
                handleVarrockTeleport("Returning to Varrock for restock...");
                return;
            }

            performBankRestock();
            return;
        }

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

    /**
     * Keeps Protect from Magic active once the Bryophyta gate is reached and throughout
     * its dialogue/instance transition. During ordinary surface/sewer travel prayers stay off.
     */
    private void maintainTravelPrayer() {
        boolean atGate = objectVisible(BRYOPHYTA_GATE_OBJECT_ID, BRYOPHYTA_SEWER_ENTRANCE, 8);
        boolean entering = lairEntryPending || state == BryophytaState.ENTERING_LAIR;

        if (config.protectFromMagic() && !Rs2Prayer.isOutOfPrayer() && (atGate || entering)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, true);
        } else if (!entering && !atGate) {
            Rs2Prayer.disableAllPrayers();
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
                        && Microbot.getClient().getBoostedSkillLevel(Skill.STRENGTH)
                        <= Microbot.getClient().getRealSkillLevel(Skill.STRENGTH)
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
        if (Microbot.getVarbitPlayerValue(VarPlayerID.OPTION_NODEF) == 1) {
            return true;
        }

        long now = System.currentTimeMillis();
        if (now - lastAutoRetaliateAttemptAt < AUTO_RETALIATE_RETRY_MS) {
            return false;
        }
        lastAutoRetaliateAttemptAt = now;

        status = "Disabling Auto Retaliate...";
        boolean disabled = Rs2Combat.setAutoRetaliate(false);
        if (disabled) {
            status = "Auto Retaliate disabled.";
        } else {
            status = "Could not disable Auto Retaliate yet - retrying...";
        }
        return disabled;
    }

    private void handleSurvival() {
        if (config.protectFromMagic() && !Rs2Prayer.isOutOfPrayer()) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, true);
        }

        maintainStrengthBoost();
        Rs2Player.eatAt(config.eatAtPercent());

        if (config.maintainPoisonProtection()) {
            Rs2Player.drinkAntiPoisonPotion();
        }
    }
    private void maintainStrengthBoost() {
        if (config.strategy() != BryophytaStrategy.MELEE || !config.useStrengthPotion()) {
            return;
        }
        if (Microbot.getClient().getBoostedSkillLevel(Skill.STRENGTH)
                > Microbot.getClient().getRealSkillLevel(Skill.STRENGTH)) {
            return;
        }

        String potion = availableStrengthPotion();
        if (potion != null) {
            setStatus("Drinking Strength potion...");
            Rs2Inventory.interact(potion, "Drink", true);
        }
    }
    private boolean hasAnyStrengthPotionDose() {
        return availableStrengthPotion() != null;
    }
    private String availableStrengthPotion() {
        for (int dose = 4; dose >= 1; dose--) {
            String potion = "Strength potion(" + dose + ")";
            if (Rs2Inventory.contains(potion, true)) {
                return potion;
            }
        }
        return null;
    }

    private void handleVarrockTeleport(String reason) {
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
        if (!sleepUntil(this::isOnSurface, 10_000)) {
            failAndStop("Varrock Teleport did not complete within 10 seconds.");
            return;
        }

        bossWasPresent = killRegisteredForCycle = prayerRestoredAfterBank = loadoutVerified = false;
        restockRequired = true;
        resetTransitions();
        setState(BryophytaState.BANKING, "Varrock reached - heading to East bank.");
    }
    private void performBankRestock() {
        setState(BryophytaState.BANKING, "Banking at Varrock East...");
        if (!Rs2Bank.walkToBankAndUseBank(BankLocation.VARROCK_EAST)) {
            return;
        }
        if (!Rs2Inventory.isEmpty() && !Rs2Bank.depositAll()) {
            setStatus("Waiting for inventory deposit...");
            return;
        }
        if (!depositWornEquipmentAtBank()) {
            return;
        }

        Map<EquipmentInventorySlot, String> desired = equipmentSettings.equipmentFor(config.strategy());
        setState(BryophytaState.EQUIPPING, "Equipping " + config.strategy() + " loadout while bank stays open...");
        if (!prepareEquipmentLoadout(desired) || !withdrawTripSupplies()) {
            return;
        }
        mainWeapon = equipmentSettings.mainWeaponFor(config.strategy());
        if (mainWeapon == null || mainWeapon.isBlank()) {
            failAndStop("The selected " + config.strategy() + " setup requires a weapon.");
            return;
        }

        if (!verifyEquipmentLoadout() || !verifyFullTripSupplies()) {
            failAndStop("Bank restock verification failed for equipment or trip supplies.");
            return;
        }

        if (!Rs2Bank.closeBank() || !sleepUntil(() -> !Rs2Bank.isOpen(), 2500)) {
            setStatus("Restock complete - waiting for bank to close...");
            return;
        }

        markRestockComplete();
        if (!ensureAutoRetaliateDisabled()) {
            setStatus("Restock complete - waiting to disable Auto Retaliate...");
            return;
        }

        if (config.strategy() == BryophytaStrategy.MAGIC_FIRE && !setSelectedAutocastWithoutMagicTab(config.fireSpell())) {
            failAndStop("Could not set " + config.fireSpell()
                    + " to autocast after banking. Check the selected Magic weapon and Combat spell selector.");
            return;
        }
        if (config.strategy() == BryophytaStrategy.MAGIC_FIRE) {
            autocastFailures = 0;
            lastAutocastAttemptAt = System.currentTimeMillis();
        }

        setState(BryophytaState.RESTORING_PRAYER, "Restocked - restoring Prayer at Varrock altar.");
    }

    private boolean depositWornEquipmentAtBank() {
        if (Rs2Equipment.items().isEmpty()) {
            return true;
        }

        if (!Rs2Bank.isOpen()) {
            status = "Bank closed before worn equipment could be deposited.";
            return false;
        }

        Widget depositEquipment = Rs2Widget.getWidget(WidgetInfo.BANK_DEPOSIT_EQUIPMENT.getId());
        if (depositEquipment == null || depositEquipment.isHidden()) {
            status = "Waiting for bank Deposit worn items button...";
            return false;
        }

        status = "Depositing worn equipment without closing bank...";
        Rs2Widget.clickWidget(depositEquipment);
        if (!sleepUntil(() -> Rs2Equipment.items().isEmpty(), 2500)) {
            status = "Waiting for worn equipment to deposit...";
            return false;
        }
        return true;
    }

    private boolean prepareEquipmentLoadout(Map<EquipmentInventorySlot, String> desired) {
        for (Map.Entry<EquipmentInventorySlot, String> entry : desired.entrySet()) {
            EquipmentInventorySlot slot = entry.getKey();
            String itemName = entry.getValue();

            if (slot == EquipmentInventorySlot.AMMO) {
                if (Rs2Bank.hasBankItem(itemName, 1, true)
                        && !withdrawAllAvailableStack(itemName, 1, "Ranged ammunition")) {
                    return false;
                }
                if (getEquippedQuantity(slot, itemName) <= 0 && Rs2Inventory.itemQuantity(itemName) <= 0) {
                    failAndStop("Missing required Ranged ammunition in bank/equipment: " + itemName);
                    return false;
                }
                if (Rs2Inventory.itemQuantity(itemName) > 0 && !equipAmmoStack(itemName)) {
                    failAndStop("Could not equip required ammunition: " + itemName);
                    return false;
                }
                continue;
            }

            if (Rs2Equipment.isWearing(itemName, true)) {
                continue;
            }
            if (!Rs2Inventory.contains(itemName, true)
                    && !withdrawExact(itemName, 1, slot.name() + " equipment")) {
                return false;
            }
            if (!interactEquip(itemName)) {
                failAndStop("Could not equip required item: " + itemName + ". Check skill/quest requirements.");
                return false;
            }
        }
        return true;
    }
    private boolean interactEquip(String itemName) {
        if (!interactInventory(itemName, EQUIP_ACTIONS)) {
            return false;
        }
        return sleepUntil(() -> Rs2Equipment.isWearing(itemName, true)
                || !Rs2Inventory.contains(itemName, true), 2500);
    }
    private boolean equipAmmoStack(String itemName) {
        int inventoryAmount = Rs2Inventory.itemQuantity(itemName);
        if (inventoryAmount <= 0) {
            return getEquippedQuantity(EquipmentInventorySlot.AMMO, itemName) > 0;
        }

        int expected = getEquippedQuantity(EquipmentInventorySlot.AMMO, itemName) + inventoryAmount;
        if (!interactInventory(itemName, "Wield", "Wear")) {
            return false;
        }
        return sleepUntil(() -> getEquippedQuantity(EquipmentInventorySlot.AMMO, itemName) >= expected
                || Rs2Inventory.itemQuantity(itemName) == 0, 3000);
    }

    private boolean withdrawTripSupplies() {
        String growthlingTool = normalize(config.growthlingToolName());
        if (!withdrawExact(growthlingTool, 1, "Growthling axe")) {
            return false;
        }

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
                    || !withdrawAllAvailableStack(spell.getCatalystRuneName(), 1, spell + " catalyst runes")) {
                return false;
            }
        } else {
            if (!withdrawExact(AIR_RUNE, requiredAir, "Varrock Air runes")
                    || !withdrawExact(FIRE_RUNE, requiredFire, "Varrock Fire runes")) {
                return false;
            }
        }

        if (!withdrawExact(LAW_RUNE, requiredLaw, "Varrock Law runes")) {
            return false;
        }

        if (config.strategy() == BryophytaStrategy.MELEE && config.useStrengthPotion()) {
            if (!withdrawExact(STRENGTH_POTION_4, config.strengthPotionAmount(), "Strength potion")) {
                return false;
            }
        }

        if (!withdrawExact(normalize(config.foodName()), config.foodAmount(), "Food")) {
            return false;
        }

        if (config.withdrawMossyKey() && Rs2Bank.hasBankItem(MOSSY_KEY, 1, true)) {
            Rs2Bank.withdrawDeficit(MOSSY_KEY, 1, true);
        }

        return true;
    }

    private boolean withdrawExact(String itemName, int amount, String purpose) {
        if (amount <= 0 || Rs2Inventory.itemQuantity(itemName) >= amount) {
            return true;
        }
        if (!Rs2Bank.withdrawDeficit(itemName, amount, true)) {
            failAndStop("Missing required " + purpose + " in bank: " + itemName + " x" + amount);
            return false;
        }
        if (sleepUntil(() -> Rs2Inventory.itemQuantity(itemName) >= amount, 2500)) {
            return true;
        }
        failAndStop("Could not withdraw required " + purpose + ": " + itemName + " x" + amount);
        return false;
    }

    private boolean withdrawAllAvailableStack(String itemName, int minimumRequired, String purpose) {
        int inventoryBefore = Rs2Inventory.itemQuantity(itemName);
        int bankAvailable = Rs2Bank.bankItems().stream()
                .filter(item -> item != null
                        && item.getName() != null
                        && item.getName().equalsIgnoreCase(itemName))
                .mapToInt(Rs2ItemModel::getQuantity)
                .sum();

        long totalAvailable = (long) inventoryBefore + bankAvailable;
        if (totalAvailable < minimumRequired) {
            failAndStop("Missing required " + purpose + ": " + itemName
                    + " (need at least " + minimumRequired + ", available " + totalAvailable + ")");
            return false;
        }

        if (bankAvailable <= 0) {
            return inventoryBefore >= minimumRequired;
        }

        if (!Rs2Bank.withdrawAll(itemName, true)) {
            failAndStop("Could not withdraw all available " + purpose + ": " + itemName);
            return false;
        }

        int expected = (int) Math.min(Integer.MAX_VALUE, totalAvailable);
        if (!sleepUntil(() -> Rs2Inventory.itemQuantity(itemName) >= expected, 3000)) {
            if (Rs2Inventory.itemQuantity(itemName) < minimumRequired) {
                failAndStop("Withdrawal did not supply enough " + purpose + ": " + itemName);
                return false;
            }
        }

        return true;
    }
    private void restorePrayerAtVarrockAltar() {
        int realPrayer = Microbot.getClient().getRealSkillLevel(Skill.PRAYER);
        if (Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER) >= realPrayer) {
            prayerRestoredAfterBank = true;
            altarFailures = 0;
            altarInteractionPending = false;
            altarInteractionStartedAt = lastWalkIssuedAt = 0L;
            setState(BryophytaState.WALKING_TO_SEWERS, "Prayer full - heading to Varrock Sewers.");
            return;
        }

        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null) {
            return;
        }
        setState(BryophytaState.RESTORING_PRAYER, "Restoring Prayer at Varrock altar...");

        if (!objectVisible(VARROCK_ALTAR_OBJECT_ID, VARROCK_ALTAR, 1)) {
            altarInteractionPending = false;
            altarInteractionStartedAt = 0L;
            walkToControlled(VARROCK_ALTAR_APPROACH, 2, "Walking to Varrock altar...");
            return;
        }

        long now = System.currentTimeMillis();
        if (altarInteractionPending) {
            if (now - altarInteractionStartedAt < ALTAR_INTERACTION_TIMEOUT_MS) {
                setStatus(player.distanceTo(VARROCK_ALTAR) > 2
                        ? "Altar clicked - walking to Pray-at..."
                        : "Altar clicked - waiting for Prayer restore...");
                return;
            }
            altarInteractionPending = false;
            altarInteractionStartedAt = 0L;
        }
        if (now - lastAltarAttemptAt < ALTAR_RETRY_MS || Rs2Player.isAnimating()) {
            return;
        }
        lastAltarAttemptAt = now;

        boolean prayed = interactObject(VARROCK_ALTAR_OBJECT_ID, VARROCK_ALTAR, 1, "Pray-at")
                || interactObject(VARROCK_ALTAR_OBJECT_ID, VARROCK_ALTAR, 1, "Pray")
                || Microbot.getRs2TileObjectCache().query().withName("Altar")
                    .where(object -> VARROCK_ALTAR.equals(object.getWorldLocation())).interact("Pray-at");
        if (!prayed) {
            setStatus("Altar 14860 found but Pray-at was not accepted; retrying...");
            if (++altarFailures >= 5) {
                failAndStop("Could not Pray-at altar 14860 at 3253,3486,0 after five attempts.");
            }
            return;
        }

        altarInteractionPending = true;
        altarInteractionStartedAt = now;
        altarFailures = 0;
        setStatus("Clicked altar 14860 - waiting for Prayer restore...");
    }

    private void navigateToBryophyta() {
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null) {
            return;
        }

        if (!isUnderground(player)) {
            navigateToSewerManhole(player);
            return;
        }

        manholeDescentPending = false;
        manholeDescentStartedAt = 0L;
        setState(BryophytaState.WALKING_TO_LAIR, "Walking through Varrock Sewers to Bryophyta...");

        if (handleLairEntryDialogue()) {
            return;
        }

        if (tryEnterNearbyLairGate()) {
            return;
        }

        if (handleNearbyWeb()) {
            return;
        }

        walkToControlled(BRYOPHYTA_SEWER_ENTRANCE, 4,
                "Walking through Varrock Sewers to Bryophyta...");
    }
    private void navigateToSewerManhole(WorldPoint player) {
        setState(BryophytaState.WALKING_TO_SEWERS, "Heading to Varrock sewer manhole...");
        long now = System.currentTimeMillis();

        if (manholeDescentPending) {
            if (isUnderground(player)) {
                manholeDescentPending = false;
                manholeDescentStartedAt = 0L;
                setState(BryophytaState.WALKING_TO_LAIR, "Varrock Sewers loaded - heading to Bryophyta.");
                return;
            }
            if (now - manholeDescentStartedAt < MANHOLE_TRANSITION_TIMEOUT_MS) {
                setStatus("Descending through manhole - waiting for sewer load...");
                return;
            }
            manholeDescentPending = false;
            manholeDescentStartedAt = lastManholeAttemptAt = 0L;
            setStatus("Sewer transition timed out - retrying once...");
            return;
        }

        if (Rs2Player.isMoving() || Rs2Player.isAnimating()) {
            setStatus("Approaching Varrock sewer manhole...");
            return;
        }

        boolean open = objectVisible(VARROCK_MANHOLE_OPEN_OBJECT_ID, VARROCK_MANHOLE, 2);
        boolean closed = !open && objectVisible(VARROCK_MANHOLE_CLOSED_OBJECT_ID, VARROCK_MANHOLE, 2);
        if (!open && !closed) {
            if (player.distanceTo(VARROCK_MANHOLE_APPROACH) > 3) {
                walkToControlled(VARROCK_MANHOLE_APPROACH, 2, "Walking to Varrock sewer entrance...");
            } else {
                setStatus("At sewer entrance - waiting for manhole object...");
            }
            return;
        }
        if (now - lastManholeAttemptAt < MANHOLE_RETRY_MS) {
            return;
        }
        lastManholeAttemptAt = now;

        if (open) {
            setState(BryophytaState.WALKING_TO_SEWERS, "Climbing down into Varrock Sewers...");
            boolean climbed = interactObject(VARROCK_MANHOLE_OPEN_OBJECT_ID, VARROCK_MANHOLE, 2, "Climb-down")
                    || interactNamedObject("Manhole", VARROCK_MANHOLE, 2, "Climb-down");
            if (climbed) {
                manholeDescentPending = true;
                manholeDescentStartedAt = now;
                setStatus("Climb-down accepted - waiting for Varrock Sewers to load...");
            } else {
                setStatus("Open manhole found - retrying Climb-down...");
            }
            return;
        }

        setState(BryophytaState.WALKING_TO_SEWERS, "Opening Varrock sewer manhole...");
        boolean opened = interactObject(VARROCK_MANHOLE_CLOSED_OBJECT_ID, VARROCK_MANHOLE, 2, "Open")
                || interactNamedObject("Manhole", VARROCK_MANHOLE, 2, "Open");
        lastManholeAttemptAt = opened ? 0L : lastManholeAttemptAt;
        setStatus(opened ? "Manhole opened - climbing down..." : "Closed manhole found - retrying Open...");
    }

    private boolean handleNearbyWeb() {
        boolean webPresent = Microbot.getRs2TileObjectCache()
                .query()
                .withName("Web")
                .within(8)
                .nearestOnClientThread() != null;

        if (!webPresent) {
            return false;
        }

        boolean switchedToAxe = false;
        if (config.strategy() != BryophytaStrategy.MELEE
                && !Rs2Equipment.isWearing(normalize(config.growthlingToolName()), true)) {
            if (!equipGrowthlingTool()) {
                failAndStop("Could not equip the Growthling axe to slash the Varrock Sewers web.");
                return true;
            }
            switchedToAxe = true;
        }

        long now = System.currentTimeMillis();
        if (now - lastWebAttemptAt < WEB_RETRY_MS) {
            status = "Waiting for sewer web to clear...";
            return true;
        }
        lastWebAttemptAt = now;

        status = "Slashing sewer web...";
        Microbot.getRs2TileObjectCache()
                .query()
                .withName("Web")
                .within(8)
                .interact("Slash");

        sleep(650, 950);

        if (switchedToAxe) {
            restoreMainWeaponIfNeeded();
        }

        return true;
    }
    private boolean tryEnterNearbyLairGate() {
        long now = System.currentTimeMillis();
        if (lairEntryPending) {
            if (isInsideLair()) {
                completeLairEntry();
                return true;
            }
            if (handleLairEntryDialogue()) {
                return true;
            }
            if (now - lairEntryStartedAt < LAIR_ENTRY_TRANSITION_TIMEOUT_MS) {
                setState(BryophytaState.ENTERING_LAIR, "Gate accepted - waiting for Bryophyta lair/dialogue...");
                return true;
            }

            clearLairEntryPending();
            setStatus("Bryophyta entry timed out - retrying gate...");
            if (++entryFailures >= 4) {
                failBryophytaEntry("Bryophyta entry dialogue/instance transition timed out after four attempts.");
            }
            return true;
        }

        if (!objectVisible(BRYOPHYTA_GATE_OBJECT_ID, BRYOPHYTA_SEWER_ENTRANCE, 8)) {
            return false;
        }

        WorldPoint player = Rs2Player.getWorldLocation();
        if (player != null && player.distanceTo(BRYOPHYTA_SEWER_ENTRANCE) > 10) {
            walkToControlled(BRYOPHYTA_SEWER_ENTRANCE, 8, "Approaching Bryophyta gate...");
            return true;
        }

        cancelWalkerForBryophytaGate();
        if (handleLairEntryDialogue()) {
            startLairEntry(now);
            return true;
        }
        if (now - lastEntryAttemptAt < ENTRY_RETRY_MS) {
            return true;
        }
        lastEntryAttemptAt = now;

        setState(BryophytaState.ENTERING_LAIR, "Opening Bryophyta gate...");

        boolean entered = false;
        for (String action : GATE_ACTIONS) {
            if (interactBryophytaGate(action)) {
                entered = true;
                break;
            }
        }
        entered = entered || Microbot.getRs2TileObjectCache().query().withId(BRYOPHYTA_GATE_OBJECT_ID)
                .within(BRYOPHYTA_SEWER_ENTRANCE, 8).interact();

        if (entered || Rs2Dialogue.isInDialogue()) {
            startLairEntry(now);
            entryFailures = 0;
            setState(BryophytaState.ENTERING_LAIR, "Bryophyta gate accepted - completing entry dialogue...");
            return true;
        }

        setStatus(Rs2Inventory.contains(MOSSY_KEY, true)
                ? "Gate interaction not accepted yet - Mossy key available; retrying..."
                : "Gate interaction not accepted yet; retrying...");
        if (++entryFailures >= 4) {
            failBryophytaEntry("Could not enter Bryophyta after four gate attempts.");
        }
        return true;
    }

    private void cancelWalkerForBryophytaGate() {
        if (Rs2Walker.getCurrentTarget() != null) {
            Rs2Walker.clearWalkingRoute("KSP Bryophyta: gate 32534 reached");
        }
        lastWalkIssuedAt = 0L;
    }

    private boolean interactBryophytaGate(String action) {
        return Microbot.getRs2TileObjectCache()
                .query()
                .withId(BRYOPHYTA_GATE_OBJECT_ID)
                .within(BRYOPHYTA_SEWER_ENTRANCE, 8)
                .interact(action);
    }
    private boolean handleLairEntryDialogue() {
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null || player.distanceTo(BRYOPHYTA_SEWER_ENTRANCE) > 12) {
            return false;
        }

        boolean options = Rs2Dialogue.hasSelectAnOption();
        boolean canContinue = Rs2Dialogue.hasContinue();
        if (!options && !canContinue) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now - lastLairDialogueActionAt < LAIR_DIALOGUE_RETRY_MS) {
            return true;
        }

        if (options) {
            boolean selected = Rs2Dialogue.clickOption(true, "Yes, let's go!")
                    || Rs2Dialogue.clickOption(false, "Yes, let's go", "Yes", "Enter", "Go in", "Continue");
            if (!selected) {
                setStatus("Bryophyta entry options visible - waiting for affirmative option...");
                return true;
            }
            setState(BryophytaState.ENTERING_LAIR, "Selected 'Yes, let's go!' - completing Bryophyta entry...");
        } else {
            Rs2Dialogue.clickContinue();
            setState(BryophytaState.ENTERING_LAIR, "Continuing Bryophyta gate dialogue...");
        }

        lastLairDialogueActionAt = now;
        startLairEntry(now);
        return true;
    }
    private void completeLairEntry() {
        clearLairEntryPending();
        entryFailures = 0;
        lastWalkIssuedAt = 0L;
        // Sewer navigation must never survive the instance transition. Range movement inside
        // the lair uses direct canvas walking and should not compete with the old webwalker.
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
        if (player == null || player.distanceTo(target) <= distance) {
            return;
        }

        status = walkingStatus;
        long now = System.currentTimeMillis();

        if (Rs2Player.isMoving() || now - lastWalkIssuedAt < WALK_REISSUE_MS) {
            return;
        }

        lastWalkIssuedAt = now;
        Rs2Walker.walkTo(target, distance);
    }
    private void handleGrowthlings(Rs2NpcModel growthling) {
        setState(BryophytaState.KILLING_GROWTHLINGS,
                "Growthling - switching to " + config.growthlingToolName() + ".");
        if (!equipGrowthlingTool()) {
            failAndStop("Required Growthling axe is missing/unusable: " + config.growthlingToolName());
            return;
        }
        if (isPlayerInteractingWithGrowthling()) {
            setStatus("Engaged with Growthling - waiting for hit...");
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastGrowthlingClickAt < GROWTHLING_ATTACK_RETRY_MS
                || Rs2Player.isMoving() || Rs2Player.isAnimating()) {
            return;
        }

        setStatus("Attacking Growthling...");
        if (growthling.click("Attack")) {
            handledGrowthlings.add(growthling.getIndex());
            lastGrowthlingClickAt = now;
            setStatus("Growthling attack sent - waiting for despawn...");
        } else {
            lastGrowthlingClickAt = now - (GROWTHLING_ATTACK_RETRY_MS - 700L);
            setStatus("Growthling attack not accepted - waiting to retry...");
        }
    }

    private boolean equipGrowthlingTool() {
        String tool = normalize(config.growthlingToolName());
        if (Rs2Equipment.isWearing(tool, true)) {
            return true;
        }

        if (!Rs2Inventory.contains(tool, true)) {
            return false;
        }

        return interactEquip(tool);
    }
    private boolean restoreMainWeaponIfNeeded() {
        if (mainWeapon == null || mainWeapon.isBlank()) {
            restockRequired = true;
            loadoutVerified = false;
            return false;
        }
        if (Rs2Equipment.isWearing(mainWeapon, true)) {
            return true;
        }
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
        if (!restoreMainWeaponIfNeeded()) {
            return;
        }

        if (config.strategy() == BryophytaStrategy.MAGIC_FIRE && !ensureSelectedSpellAutocast()) {
            return;
        }
        if (config.strategy() != BryophytaStrategy.MELEE && maintainRange(bryophyta)) {
            return;
        }
        attackWithCurrentWeapon(bryophyta);
    }

    private void attackWithCurrentWeapon(Rs2NpcModel bryophyta) {
        if (bryophyta == null || bryophyta.isDead()) {
            return;
        }

        if (isPlayerInteractingWith(bryophyta)) {
            status = "Engaged with Bryophyta...";
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastBossAttackClickAt < BOSS_ATTACK_RETRY_MS
                || Rs2Player.isMoving()
                || Rs2Player.isAnimating()) {
            return;
        }

        lastBossAttackClickAt = now;
        status = "Attacking Bryophyta...";
        if (!bryophyta.click("Attack")) {
            status = "Bryophyta attack click not accepted - waiting to retry...";
        }
    }
    private boolean isPlayerInteractingWithGrowthling() {
        net.runelite.api.Actor target = playerInteractionTarget();
        return target instanceof net.runelite.api.NPC
                && ((net.runelite.api.NPC) target).getId() == GROWTHLING_NPC_ID;
    }
    private boolean isPlayerInteractingWith(Rs2NpcModel npc) {
        return npc != null && npc.getNpc() != null && playerInteractionTarget() == npc.getNpc();
    }

    private boolean setSelectedAutocastWithoutMagicTab(BryophytaFireSpell selectedSpell) {
        if (selectedSpell == null) {
            return false;
        }

        Rs2CombatSpells combatSpell = selectedSpell.getCombatSpell();
        if (combatSpell == null) {
            return false;
        }

        if (Rs2Magic.getCurrentAutoCastSpell() == combatSpell) {
            return true;
        }

        if (Microbot.getClient().getRealSkillLevel(Skill.MAGIC) < combatSpell.getRequiredLevel()) {
            status = selectedSpell + " requires Magic level " + combatSpell.getRequiredLevel() + ".";
            return false;
        }

        Rs2Tab.switchTo(InterfaceTab.COMBAT);
        if (!sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.COMBAT, 2000)) {
            status = "Could not open Combat tab to configure autocast.";
            return false;
        }

        Widget spellButton = Rs2Widget.getWidget(WidgetInfo.COMBAT_SPELL_BOX.getId());
        if (spellButton == null) {
            status = "Selected Magic weapon does not expose an autocast Spell button.";
            return false;
        }

        Rs2Widget.clickWidget(spellButton);
        if (!sleepUntil(() -> Rs2Widget.isWidgetVisible(201, 1), 2000)) {
            status = "Autocast spell selector did not open.";
            return false;
        }

        Widget autoCastOptions = Rs2Widget.getWidget(201, 1);
        if (autoCastOptions == null) {
            return false;
        }

        Widget spellSprite = Rs2Widget.findWidget(
                combatSpell.getMagicAction().getSprite(),
                List.of(autoCastOptions));
        if (spellSprite == null) {
            status = selectedSpell + " is not available in this weapon's autocast selector.";
            return false;
        }

        Rs2Widget.clickWidget(spellSprite);
        boolean selected = sleepUntil(() -> Rs2Magic.getCurrentAutoCastSpell() == combatSpell, 2500);
        if (!selected) {
            status = selectedSpell + " autocast selection was not confirmed.";
        }
        return selected;
    }

    private boolean ensureSelectedSpellAutocast() {
        if (config.strategy() != BryophytaStrategy.MAGIC_FIRE) {
            return true;
        }

        long now = System.currentTimeMillis();
        if (autocastFailures > 0 && now - lastAutocastAttemptAt < AUTOCAST_RETRY_MS) {
            return false;
        }

        lastAutocastAttemptAt = now;
        BryophytaFireSpell spell = config.fireSpell();
        status = "Setting " + spell + " to autocast...";

        boolean configured = setSelectedAutocastWithoutMagicTab(spell);
        if (configured) {
            autocastFailures = 0;
            status = spell + " autocast ready.";
            return true;
        }

        if (shouldEmergencyRestock()) {
            restockRequired = true;
            return false;
        }

        autocastFailures++;
        status = "Could not set " + spell + " to autocast (" + autocastFailures + "/4).";
        if (autocastFailures >= 4) {
            failAndStop("Could not set " + spell
                    + " to autocast. Check that the selected Magic weapon supports standard spell autocasting, "
                    + "the account has the required Magic level, and the standard spellbook is active.");
        }
        return false;
    }

    private boolean maintainRange(Rs2NpcModel bryophyta) {
        if (bryophyta == null || bryophyta.getNpc() == null) {
            return false;
        }

        // Both areas must come from the same active WorldView. Rs2Player#getWorldLocation()
        // translates instance coordinates, while Actor#getWorldArea() remains in scene space;
        // mixing them produced bogus ~998-tile distances inside Bryophyta's lair.
        WorldArea[] areas = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (Microbot.getClient() == null || Microbot.getClient().getLocalPlayer() == null) {
                return null;
            }
            net.runelite.api.Player player = Microbot.getClient().getLocalPlayer();
            WorldView playerView = player.getWorldView();
            WorldView bossView = bryophyta.getNpc().getWorldView();
            if (playerView == null || bossView == null || playerView.getId() != bossView.getId()) {
                return null;
            }
            return new WorldArea[] { player.getWorldArea(), bryophyta.getNpc().getWorldArea() };
        }).orElse(null);
        if (areas == null || areas[0] == null || areas[1] == null) {
            return false;
        }

        WorldArea playerArea = areas[0];
        WorldArea bossArea = areas[1];
        WorldPoint player = new WorldPoint(playerArea.getX(), playerArea.getY(), playerArea.getPlane());
        WorldView worldView = bryophyta.getWorldView();
        int targetDistance = config.minimumRangeDistance();
        int currentDistance = bossArea.distanceTo(playerArea);

        if (currentDistance == targetDistance) {
            return false;
        }
        if (currentDistance == Integer.MAX_VALUE || currentDistance > 30) {
            setStatus("Waiting for Bryophyta combat position...");
            return true;
        }
        if (Rs2Player.isMoving()) {
            setStatus("Adjusting distance to Bryophyta: " + currentDistance + " -> " + targetDistance + " tiles...");
            return true;
        }

        long now = System.currentTimeMillis();
        if (now - lastRangeMoveAt < RANGE_MOVE_RETRY_MS) {
            return true;
        }

        WorldPoint target = findBestRangeTile(player, bossArea, worldView, targetDistance);
        if (target == null) {
            setStatus("Could not find a walkable " + targetDistance + "-tile position around Bryophyta.");
            return true;
        }

        lastRangeMoveAt = now;
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
                .min(Comparator.comparingInt(player::distanceTo))
                .orElse(null);
    }

    private void registerKill() {
        kills++;
        killRegisteredForCycle = true;
        killRegisteredAt = System.currentTimeMillis();
        setState(BryophytaState.LOOTING, "Bryophyta defeated - looting.");
        Rs2Prayer.disableAllPrayers();
    }

    private void handlePostKill(long now) {
        if (config.lootBossDrops() && now - killRegisteredAt <= POST_KILL_LOOT_WINDOW_MS) {
            setState(BryophytaState.LOOTING, "Looting Bryophyta drops...");
            attemptBossLoot();
            return;
        }

        if (config.openRewardChest()) {
            handleChest(now);
            return;
        }

        setState(BryophytaState.WAITING_FOR_RESPAWN, "Waiting for next Bryophyta cycle...");
        bossWasPresent = false;
        killRegisteredForCycle = false;
    }
    private void attemptBossLoot() {
        for (String itemName : PRIORITY_LOOT) {
            if (pickupNamedGroundItem(itemName)) {
                return;
            }
        }
        if (config.lootValueThreshold() <= 0) {
            return;
        }

        Rs2TileItemModel valuable = Microbot.getRs2TileItemCache().query().fromWorldView().within(8)
                .where(item -> item.isLootAble() && item.getTotalValue() >= config.lootValueThreshold())
                .nearestOnClientThread();
        if (valuable != null) {
            setStatus("Looting " + valuable.getName() + "...");
            valuable.pickup();
        }
    }

    private boolean pickupNamedGroundItem(String itemName) {
        Rs2TileItemModel item = Microbot.getRs2TileItemCache()
                .query()
                .fromWorldView()
                .withName(itemName)
                .within(8)
                .nearestOnClientThread();

        if (item == null || !item.isLootAble()) {
            return false;
        }

        status = "Looting " + itemName + "...";
        return item.pickup();
    }

    private void handleChest(long now) {
        if (now - lastChestAttemptAt < CHEST_RETRY_MS) {
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

        if (interacted) {
            chestAttempts++;
            bossWasPresent = false;
            killRegisteredForCycle = false;
            setState(BryophytaState.WAITING_FOR_RESPAWN, "Chest attempted - waiting for respawn.");
        } else {
            status = "Could not interact with Bryophyta chest.";
        }
    }

    private boolean verifyEquipmentLoadout() {
        for (EquipmentInventorySlot slot : BryophytaLoadout.CONFIGURABLE_SLOTS) {
            if (equipmentSettings.isExplicitEmpty(config.strategy(), slot) && Rs2Equipment.get(slot) != null) {
                return false;
            }
        }

        Map<EquipmentInventorySlot, String> desired = equipmentSettings.equipmentFor(config.strategy());
        for (Map.Entry<EquipmentInventorySlot, String> entry : desired.entrySet()) {
            EquipmentInventorySlot slot = entry.getKey();
            String name = entry.getValue();

            if (slot == EquipmentInventorySlot.AMMO) {
                if (getEquippedQuantity(slot, name) <= 0) {
                    return false;
                }
                continue;
            }

            Rs2ItemModel item = Rs2Equipment.get(slot);
            if (item == null || item.getName() == null || !item.getName().equalsIgnoreCase(name)) {
                return false;
            }
        }

        loadoutVerified = true;
        return true;
    }

    private String selectedAmmoName() {
        return equipmentSettings.equipmentFor(config.strategy()).get(EquipmentInventorySlot.AMMO);
    }

    private boolean verifyFullTripSupplies() {
        if (!hasVarrockRuneTargets()) {
            return false;
        }

        if (!hasGrowthlingTool()) {
            return false;
        }

        if (Rs2Inventory.itemQuantity(normalize(config.foodName())) < config.foodAmount()) {
            return false;
        }

        if (requiresStrengthPotion()
                && Rs2Inventory.itemQuantity(STRENGTH_POTION_4) < config.strengthPotionAmount()) {
            return false;
        }

        if (config.strategy() == BryophytaStrategy.RANGED
                && selectedAmmoName() != null
                && getEquippedQuantity(EquipmentInventorySlot.AMMO, selectedAmmoName()) <= 0) {
            return false;
        }

        if (config.strategy() == BryophytaStrategy.MAGIC_FIRE) {
            BryophytaFireSpell spell = config.fireSpell();
            if (Rs2Inventory.itemQuantity(AIR_RUNE)
                    < config.varrockTeleportCount() * 3 + spell.getAirRunesPerCast()) {
                return false;
            }
            if (Rs2Inventory.itemQuantity(FIRE_RUNE)
                    < config.varrockTeleportCount() + spell.getFireRunesPerCast()) {
                return false;
            }
            if (Rs2Inventory.itemQuantity(spell.getCatalystRuneName()) < 1) {
                return false;
            }
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

        if (Rs2Inventory.itemQuantity(normalize(config.foodName())) <= config.teleportAtFoodCount()) {
            return false;
        }

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
        return Rs2Inventory.itemQuantity(AIR_RUNE) >= 3
                && Rs2Inventory.itemQuantity(FIRE_RUNE) >= 1
                && Rs2Inventory.itemQuantity(LAW_RUNE) >= 1;
    }

    private int getEquippedQuantity(EquipmentInventorySlot slot, String exactName) {
        Rs2ItemModel item = Rs2Equipment.get(slot);
        if (item == null || item.getName() == null || !item.getName().equalsIgnoreCase(exactName)) {
            return 0;
        }
        return item.getQuantity();
    }

    private Rs2NpcModel getBryophyta() {
        return Microbot.getRs2NpcCache()
                .query()
                .withId(BRYOPHYTA_NPC_ID)
                .fromWorldView()
                .nearestOnClientThread();
    }
    private Rs2NpcModel getNearestGrowthling() {
        Rs2NpcModel any = Microbot.getRs2NpcCache().query().withId(GROWTHLING_NPC_ID)
                .fromWorldView().nearestOnClientThread();
        if (any == null) {
            handledGrowthlings.clear();
            return null;
        }
        return Microbot.getRs2NpcCache().query().withId(GROWTHLING_NPC_ID).fromWorldView()
                .where(this::isGrowthlingActionable).nearestOnClientThread();
    }
    private boolean isGrowthlingActionable(Rs2NpcModel npc) {
        if (npc == null || handledGrowthlings.contains(npc.getIndex()) || npc.isDead()) {
            return false;
        }
        int scale = npc.getHealthScale();
        return scale <= 0 || npc.getHealthRatio() > 0;
    }

    private boolean isInsideLair() {
        boolean instanced = Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient() != null && Microbot.getClient().isInInstancedRegion()
        ).orElse(false);

        if (instanced) {
            return true;
        }

        if (getBryophyta() != null || getNearestGrowthling() != null) {
            return true;
        }

        return Microbot.getRs2TileObjectCache()
                .query()
                .withId(BRYOPHYTA_CHEST_OBJECT_ID)
                .fromWorldView()
                .within(30)
                .nearestOnClientThread() != null;
    }

    private boolean isOnSurface() {
        WorldPoint point = Rs2Player.getWorldLocation();
        return point != null && !isUnderground(point) && !isInsideLair();
    }

    private static boolean isUnderground(WorldPoint point) {
        return point != null && point.getY() > 9000;
    }

    public void confirmManholeDescent() {
        manholeDescentPending = true;
        manholeDescentStartedAt = System.currentTimeMillis();
        lastManholeAttemptAt = manholeDescentStartedAt;
        setState(BryophytaState.WALKING_TO_SEWERS,
                "Manhole descent confirmed - loading Varrock Sewers...");
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
        altarInteractionPending = manholeDescentPending = lairEntryPending = false;
        altarInteractionStartedAt = manholeDescentStartedAt = lairEntryStartedAt = 0L;
        lastLairDialogueActionAt = 0L;
    }

    private void startLairEntry(long now) {
        lairEntryPending = true;
        if (lairEntryStartedAt == 0L) {
            lairEntryStartedAt = now;
        }
    }

    private void clearLairEntryPending() {
        lairEntryPending = false;
        lairEntryStartedAt = lastLairDialogueActionAt = 0L;
    }

    private void setStatus(String newStatus) {
        status = newStatus;
        Microbot.status = newStatus;
    }

    private boolean interactInventory(String itemName, String... actions) {
        Rs2ItemModel item = Rs2Inventory.get(itemName, true);
        if (item == null || item.getInventoryActions() == null) {
            return false;
        }
        for (String wanted : actions) {
            for (String available : item.getInventoryActions()) {
                if (available != null && available.equalsIgnoreCase(wanted)) {
                    return Rs2Inventory.interact(item, available);
                }
            }
        }
        return false;
    }

    private boolean objectVisible(int id, WorldPoint center, int radius) {
        return Microbot.getRs2TileObjectCache().query().withId(id).within(center, radius)
                .nearestOnClientThread() != null;
    }

    private boolean interactObject(int id, WorldPoint center, int radius, String action) {
        return Microbot.getRs2TileObjectCache().query().withId(id).within(center, radius).interact(action);
    }

    private boolean interactNamedObject(String name, WorldPoint center, int radius, String action) {
        return Microbot.getRs2TileObjectCache().query().withName(name).within(center, radius).interact(action);
    }

    private net.runelite.api.Actor playerInteractionTarget() {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient() == null || Microbot.getClient().getLocalPlayer() == null
                        ? null : Microbot.getClient().getLocalPlayer().getInteracting()).orElse(null);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
    private void setState(BryophytaState newState, String newStatus) {
        state = newState;
        setStatus(newStatus);
    }

    private void failAndStop(String reason) {
        if (state == BryophytaState.STOPPED) {
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
    public BryophytaState getState() {
        return state;
    }
    public String getStatus() {
        return status;
    }
    public int getKills() {
        return kills;
    }
    public int getMossyKeys() {
        return mossyKeys;
    }
    public int getChestAttempts() {
        return chestAttempts;
    }
    public int getFoodRemaining() {
        return foodRemaining;
    }
    public int getPrayerPoints() {
        return prayerPoints;
    }

    public void setStopped(String reason) {
        state = BryophytaState.STOPPED;
        status = reason;
        Microbot.status = reason;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        Rs2Prayer.disableAllPrayers();
        if (state != BryophytaState.STOPPED) {
            setStopped("Stopped.");
        }
    }
}
