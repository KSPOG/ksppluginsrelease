package net.runelite.client.plugins.microbot.KSPAutoMiner;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
public class KSPAutoMinerScript extends Script {
    private static final WorldPoint START_LOCATION = new WorldPoint(3284, 3365, 0);
    private static final WorldPoint COAL_LOCATION = new WorldPoint(3083, 3422, 0);
    private static final WorldPoint GOLD_LOCATION = new WorldPoint(3294, 3286, 0);
    private static final WorldPoint MITHRIL_LOCATION = new WorldPoint(3304, 3304, 0);
    private static final WorldPoint ADAMANT_LOCATION = new WorldPoint(3299, 3317, 0);

    private static final List<String> ALL_ORES = Arrays.asList(
            "Clay",
            "Tin ore",
            "Copper ore",
            "Iron ore",
            "Silver ore",
            "Coal",
            "Gold ore",
            "Mithril ore",
            "Adamantite ore",
            "Runite ore"
    );

    // Requirements based on OSRS wiki pickaxe requirements.
    private static final List<PickaxeRequirement> PICKAXE_REQUIREMENTS = Arrays.asList(
            new PickaxeRequirement(ItemID.BRONZE_PICKAXE, 1, 1),
            new PickaxeRequirement(ItemID.IRON_PICKAXE, 1, 1),
            new PickaxeRequirement(ItemID.STEEL_PICKAXE, 6, 5),
            new PickaxeRequirement(ItemID.BLACK_PICKAXE, 11, 10),
            new PickaxeRequirement(ItemID.MITHRIL_PICKAXE, 21, 20),
            new PickaxeRequirement(ItemID.ADAMANT_PICKAXE, 31, 30),
            new PickaxeRequirement(ItemID.RUNE_PICKAXE, 41, 40),
            new PickaxeRequirement(ItemID.DRAGON_PICKAXE, 61, 60),
            new PickaxeRequirement(ItemID.INFERNAL_PICKAXE, 61, 60),
            new PickaxeRequirement(ItemID.CRYSTAL_PICKAXE, 71, 70)
    );

    public static String status = "Idle";
    public static String modeLabel = "";
    public static int oresMined = 0;

    private static long startTimeMs;
    private int lastInventoryCount;
    private int tinMined;
    private int copperMined;
    private int lastTinCount;
    private int lastCopperCount;
    private MiningStage stage = MiningStage.TIN_COPPER;

    private Rock targetRock;
    private KSPAutoMinerRock selectedRock;
    private WorldPoint targetLocation;

    public boolean run(KSPAutoMinerConfig config) {
        startTimeMs = System.currentTimeMillis();
        oresMined = 0;
        tinMined = 0;
        copperMined = 0;
        lastTinCount = 0;
        lastCopperCount = 0;
        lastInventoryCount = 0;
        status = "Starting";
        modeLabel = config.mode().toString();
        Rs2Antiban.resetAntibanSettings();
        if (config.enableAntiban()) {
            Rs2Antiban.antibanSetupTemplates.applyMiningSetup();
            Rs2AntibanSettings.actionCooldownChance = 0.2;
        }

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) {
                    return;
                }
                if (!Microbot.isLoggedIn()) {
                    return;
                }
                if (config.enableAntiban() && Rs2AntibanSettings.actionCooldownActive) {
                    return;
                }

                KSPAutoMinerMode mode = config.mode();
                modeLabel = mode.toString();
                updateTarget(mode, config.rock());
                updateOreCount();

                if (targetRock == null || targetLocation == null) {
                    status = "No target location";
                    return;
                }

                if (!targetRock.hasRequiredLevel()) {
                    status = "Mining level too low";
                    return;
                }

                if (Rs2Inventory.isFull()) {
                    if (mode.isBankingMode()) {
                        status = "Banking";
                        if (!bankOresAndUpgradePickaxe(targetLocation)) {
                            return;
                        }
                    } else {
                        status = "Dropping ores";
                        dropOres();
                    }
                    return;
                }

                if (Rs2Player.isAnimating() && !Rs2Player.isMoving()) {
                    return;
                }

                if (Rs2Player.getWorldLocation().distanceTo(targetLocation) > 6) {
                    status = "Walking to rocks";
                    CustomWebWalker.walkTo(targetLocation);
                    return;
                }

                GameObject rock = findNearestRock(targetLocation, 12);
                if (rock == null) {
                    status = "No rocks found";
                    return;
                }

                status = "Mining " + targetRock.getName();
                if (Rs2GameObject.interact(rock)) {
                    Rs2Player.waitForXpDrop(Skill.MINING, true);
                    if (config.enableAntiban()) {
                        Rs2Antiban.actionCooldown();
                        Rs2Antiban.takeMicroBreakByChance();
                    }
                }
            } catch (Exception ex) {
                Microbot.log("KSPAutoMiner error: " + ex.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);

        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        status = "Stopped";
        Rs2Antiban.resetAntibanSettings();
    }

    public static Duration getRuntime() {
        if (startTimeMs == 0) {
            return Duration.ZERO;
        }
        long elapsed = System.currentTimeMillis() - startTimeMs;
        return Duration.ofMillis(elapsed);
    }

    private void updateTarget(KSPAutoMinerMode mode, KSPAutoMinerRock rockSelection) {
        int miningLevel = Microbot.getClientThread().invoke(() -> Microbot.getClient().getRealSkillLevel(Skill.MINING));
        int combatLevel = Microbot.getClientThread().invoke(() -> {
            if (Microbot.getClient() == null || Microbot.getClient().getLocalPlayer() == null) {
                return 3;
            }
            return Microbot.getClient().getLocalPlayer().getCombatLevel();
        });

        if (rockSelection != selectedRock) {
            selectedRock = rockSelection;
            resetOreCounters();
        }

        MiningStage newStage = determineStage(mode, miningLevel, combatLevel);
        if (newStage != stage) {
            stage = newStage;
            resetOreCounters();
        }

        if (!mode.isProgressiveMode()) {
            targetRock = mapRockSelection(rockSelection);
            targetLocation = mapLocationForRockSelection(rockSelection);
            return;
        }

        switch (stage) {
            case TIN_COPPER:
                targetLocation = START_LOCATION;
                targetRock = selectBalancedTinCopper();
                break;
            case IRON:
                targetLocation = START_LOCATION;
                targetRock = Rock.IRON;
                break;
            case COAL:
                targetLocation = COAL_LOCATION;
                targetRock = Rock.COAL;
                break;
            case GOLD:
                targetLocation = GOLD_LOCATION;
                targetRock = Rock.GOLD;
                break;
            case MITHRIL:
                targetLocation = MITHRIL_LOCATION;
                targetRock = Rock.MITHRIL;
                break;
            case ADAMANT:
                targetLocation = ADAMANT_LOCATION;
                targetRock = Rock.ADAMANTITE;
                break;
        }
    }

    private MiningStage determineStage(KSPAutoMinerMode mode, int miningLevel, int combatLevel) {
        if (!mode.isProgressiveMode()) {
            return miningLevel < 15 ? MiningStage.TIN_COPPER : MiningStage.IRON;
        }

        if (miningLevel < 15) {
            return MiningStage.TIN_COPPER;
        }
        if (miningLevel < 30) {
            return MiningStage.IRON;
        }
        if (miningLevel < 40) {
            return MiningStage.COAL;
        }
        if (miningLevel < 55) {
            if (combatLevel < 64) {
                return MiningStage.COAL;
            }
            return MiningStage.GOLD;
        }
        if (miningLevel < 70) {
            return MiningStage.MITHRIL;
        }
        return MiningStage.ADAMANT;
    }

    private Rock selectBalancedTinCopper() {
        if (tinMined <= copperMined) {
            return Rock.TIN;
        }
        return Rock.COPPER;
    }

    private WorldPoint mapLocationForRockSelection(KSPAutoMinerRock rockSelection) {
        if (rockSelection == null) {
            return START_LOCATION;
        }

        switch (rockSelection) {
            case COAL:
                return COAL_LOCATION;
            case GOLD:
                return GOLD_LOCATION;
            case MITHRIL:
                return MITHRIL_LOCATION;
            case ADAMANT:
                return ADAMANT_LOCATION;
            case RUNE:
                return ADAMANT_LOCATION;
            default:
                return START_LOCATION;
        }
    }

    private Rock mapRockSelection(KSPAutoMinerRock rockSelection) {
        if (rockSelection == null) {
            return Rock.TIN;
        }

        switch (rockSelection) {
            case CLAY:
                return Rock.CLAY;
            case COPPER_TIN:
                return selectBalancedTinCopper();
            case IRON:
                return Rock.IRON;
            case SILVER:
                return Rock.SILVER;
            case COAL:
                return Rock.COAL;
            case GOLD:
                return Rock.GOLD;
            case MITHRIL:
                return Rock.MITHRIL;
            case ADAMANT:
                return Rock.ADAMANTITE;
            case RUNE:
                return Rock.RUNITE;
            default:
                return Rock.TIN;
        }
    }

    private void updateOreCount() {
        if (stage == MiningStage.TIN_COPPER || targetRock == Rock.TIN || targetRock == Rock.COPPER) {
            int tinCount = Rs2Inventory.count("Tin ore");
            int copperCount = Rs2Inventory.count("Copper ore");
            int tinDelta = tinCount - lastTinCount;
            int copperDelta = copperCount - lastCopperCount;

            if (tinDelta > 0) {
                tinMined += tinDelta;
                oresMined += tinDelta;
            }
            if (copperDelta > 0) {
                copperMined += copperDelta;
                oresMined += copperDelta;
            }

            lastTinCount = tinCount;
            lastCopperCount = copperCount;
            lastInventoryCount = tinCount + copperCount;
            return;
        }

        int currentCount = countRelevantOres();
        int delta = currentCount - lastInventoryCount;
        if (delta > 0) {
            oresMined += delta;
        }
        lastInventoryCount = currentCount;
    }

    private int countRelevantOres() {
        if (targetRock == null) {
            return 0;
        }

        if (targetRock == Rock.TIN || targetRock == Rock.COPPER) {
            return Rs2Inventory.count("Tin ore") + Rs2Inventory.count("Copper ore");
        }

        return Rs2Inventory.count(targetRock.getOreName());
    }

    private void resetOreCounters() {
        lastInventoryCount = countRelevantOres();
        lastTinCount = Rs2Inventory.count("Tin ore");
        lastCopperCount = Rs2Inventory.count("Copper ore");
    }


    private boolean bankOresAndUpgradePickaxe(WorldPoint returnLocation) {
        if (!Rs2Bank.walkToBankAndUseBank()) {
            return false;
        }
        if (!Rs2Bank.isOpen()) {
            return false;
        }

        Rs2Bank.depositAllExcept("pickaxe");
        upgradePickaxeIfAvailable();
        Rs2Bank.closeBank();

        if (returnLocation != null) {
            CustomWebWalker.walkTo(returnLocation);
        }
        return true;
    }

    private void upgradePickaxeIfAvailable() {
        Rs2ItemModel bestFromBank = getBestPickaxeFromBank();
        if (bestFromBank == null) {
            return;
        }

        Rs2ItemModel currentPickaxe = getBestPickaxeOnPlayer();
        int currentLevel = currentPickaxe != null ? getPickaxeMiningLevel(currentPickaxe.getId()) : 0;
        int bankLevel = getPickaxeMiningLevel(bestFromBank.getId());

        if (bankLevel <= currentLevel) {
            return;
        }

        if (getPickaxeAttackLevel(bestFromBank.getId()) > 1) {
            Rs2ItemModel currentWeapon = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
            Rs2Bank.withdrawAndEquip(bestFromBank.getId());
            if (currentWeapon != null && currentWeapon.getId() != bestFromBank.getId()) {
                Rs2Bank.depositOne(currentWeapon.getId());
            }
        } else {
            if (!Rs2Inventory.hasItem(bestFromBank.getId())) {
                Rs2Bank.withdrawOne(bestFromBank.getId());
            }
        }

        if (currentPickaxe != null && currentPickaxe.getId() != bestFromBank.getId()
                && Rs2Inventory.hasItem(currentPickaxe.getId())) {
            Rs2Bank.depositOne(currentPickaxe.getId());
        }
    }

    private Rs2ItemModel getBestPickaxeOnPlayer() {
        Rs2ItemModel equipped = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
        if (equipped != null && isPickaxe(equipped.getId()) && canUsePickaxe(equipped.getId())) {
            return equipped;
        }

        return Rs2Inventory.items()
                .filter(item -> isPickaxe(item.getId()) && canUsePickaxe(item.getId()))
                .max((first, second) -> Integer.compare(
                        getPickaxeMiningLevel(first.getId()),
                        getPickaxeMiningLevel(second.getId())))
                .orElse(null);
    }

    private Rs2ItemModel getBestPickaxeFromBank() {
        return Rs2Bank.getAll(item -> isPickaxe(item.getId()) && canUsePickaxe(item.getId()))
                .max((first, second) -> Integer.compare(
                        getPickaxeMiningLevel(first.getId()),
                        getPickaxeMiningLevel(second.getId())))
                .orElse(null);
    }

    private boolean canUsePickaxe(int itemId) {
        PickaxeRequirement requirement = getPickaxeRequirement(itemId);
        return requirement != null
                && Rs2Player.getSkillRequirement(Skill.MINING, requirement.miningLevel)
                && Rs2Player.getSkillRequirement(Skill.ATTACK, requirement.attackLevel);
    }

    private boolean isPickaxe(int itemId) {
        return getPickaxeRequirement(itemId) != null;
    }

    private int getPickaxeMiningLevel(int itemId) {
        PickaxeRequirement requirement = getPickaxeRequirement(itemId);
        return requirement != null ? requirement.miningLevel : 0;
    }

    private int getPickaxeAttackLevel(int itemId) {
        PickaxeRequirement requirement = getPickaxeRequirement(itemId);
        return requirement != null ? requirement.attackLevel : 0;
    }

    private PickaxeRequirement getPickaxeRequirement(int itemId) {
        return PICKAXE_REQUIREMENTS.stream()
                .filter(requirement -> requirement.itemId == itemId)
                .findFirst()
                .orElse(null);
    }


    private GameObject findNearestRock(WorldPoint searchCenter, int radius) {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (searchCenter == null || targetRock == null || playerLocation == null) {
            return null;
        }

        return Rs2GameObject.getGameObjects(Rs2GameObject.nameMatches(targetRock.getName(), false), searchCenter, radius)
                .stream()
                .filter(Rs2GameObject::isReachable)
                .min((first, second) -> Integer.compare(
                        first.getWorldLocation().distanceTo(playerLocation),
                        second.getWorldLocation().distanceTo(playerLocation)))
                .orElse(null);
    }

    private void dropOres() {
        for (String ore : ALL_ORES) {
            if (Rs2Inventory.hasItem(ore, false)) {
                Rs2Inventory.dropAll(ore);
            }
        }
    }

    private enum MiningStage {
        TIN_COPPER,
        IRON,
        COAL,
        GOLD,
        MITHRIL,
        ADAMANT
    }

    private enum Rock {
        CLAY("Clay rocks", "Clay", 1),
        TIN("Tin rocks", "Tin ore", 1),
        COPPER("Copper rocks", "Copper ore", 1),
        IRON("Iron rocks", "Iron ore", 15),
        SILVER("Silver rocks", "Silver ore", 20),
        COAL("Coal rocks", "Coal", 30),
        GOLD("Gold rocks", "Gold ore", 40),
        MITHRIL("Mithril rocks", "Mithril ore", 55),
        ADAMANTITE("Adamantite rocks", "Adamantite ore", 70),
        RUNITE("Runite rocks", "Runite ore", 85);

        private final String name;
        private final String oreName;
        private final int miningLevel;

        Rock(String name, String oreName, int miningLevel) {
            this.name = name;
            this.oreName = oreName;
            this.miningLevel = miningLevel;
        }

        public String getName() {
            return name;
        }

        public String getOreName() {
            return oreName;
        }

        public boolean hasRequiredLevel() {
            return Microbot.getClient().getRealSkillLevel(Skill.MINING) >= miningLevel;
        }
    }


    private static final class PickaxeRequirement {
        private final int itemId;
        private final int miningLevel;
        private final int attackLevel;

        private PickaxeRequirement(int itemId, int miningLevel, int attackLevel) {
            this.itemId = itemId;
            this.miningLevel = miningLevel;
            this.attackLevel = attackLevel;
        }
    }


}
