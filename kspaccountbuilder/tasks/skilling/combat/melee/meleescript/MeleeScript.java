package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.combat.melee.meleescript;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import net.runelite.api.Actor;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspBankMode;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspTaskDebug;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspWalkerGuard;
import net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.KspBankWidgetHelper;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.combat.melee.areas.CombatAreas;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.combat.melee.equipment.weapon.Weapons;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.combat.melee.food.Food;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.combat.melee.loot.alkharidwarriotloot.WarriorLoot;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.combat.melee.loot.cowloot.CowLoot;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.combat.melee.loot.hillgiantloot.HillGiantLoot;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.combat.melee.loot.mossgiantloot.MossGiantLoot;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.combat.melee.meleescript.CombatState;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.combat.melee.npc.NPC;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.buyscript.Buy;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.sell.SellList;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class MeleeScript
        extends Script {
    private static final Logger log = LoggerFactory.getLogger(MeleeScript.class);
    private static final int LOOP_DELAY_MS = 400;
    private static final int WEB_WALK_COOLDOWN_MS = 3000;
    private static final int TARGET_FOOD_COUNT = Buy.MELEE_TARGET_FOOD_COUNT;
    private static final int CHICKEN_TARGET_COMBAT_STAT_LEVEL = 15;
    private static final int LOOT_RADIUS = 12;
    private static final long NO_FOOD_CONFIRMATION_MS = 2_500L;
    private static final WorldPoint CHICKEN_WALK_TARGET = new WorldPoint(3177, 3298, 0);
    private static final WorldPoint CHICKEN_GATE_EAST = new WorldPoint(3262, 3321, 0);
    private static final WorldPoint CHICKEN_GATE_WEST = new WorldPoint(3261, 3321, 0);
    private static final int CHICKEN_GATE_EAST_ID = 1560;
    private static final int CHICKEN_GATE_WEST_ID = 1558;
    private static final int CHICKEN_GATE_INTERACTION_DISTANCE = 12;
    private static final int CHICKEN_COMBAT_RADIUS = 6;
    private static final int PASSIVE_ATTACKER_MAX_DISTANCE = 3;
    private static final String[] CHICKEN_LOOT_NAMES = {"Bones", "Feather", "Raw chicken"};
    private static final List<String> IGNORED_COMBAT_NPC_NAMES = Arrays.asList("skeleton", "hobgoblin");
    private static final WorldPoint EDGEVILLE_TRAPDOOR = new WorldPoint(3096, 3468, 0);
    private static final int EDGEVILLE_TRAPDOOR_CLOSED_ID = 1579;
    private static final int EDGEVILLE_TRAPDOOR_OPEN_ID = 1581;
    private static final int EDGEVILLE_TRAPDOOR_INTERACTION_DISTANCE = 4;
    private static final WorldPoint HILL_GIANT_WALK_TARGET = new WorldPoint(3112, 9847, 0);
    private String status = "Idle";
    private CombatState state = CombatState.PREPARING;
    private boolean debugLogging;
    private long lastWebWalkAtMs;
    private WorldPoint lastWalkTarget;
    private CombatAreas forcedCombatArea;
    private long lastConfirmedFoodAtMs;
    private volatile boolean pendingSellHandoff;

    public void setDebugLogging(boolean debugLogging) {
        this.debugLogging = debugLogging;
    }

    public boolean run() {
        return this.run(null);
    }

    public boolean run(CombatAreas forcedCombatArea) {
        this.shutdown();
        this.pendingSellHandoff = false;
        this.forcedCombatArea = forcedCombatArea;
        this.lastConfirmedFoodAtMs = System.currentTimeMillis();
        this.setStatus("Starting melee training");
        this.state = CombatState.PREPARING;
        this.mainScheduledFuture = this.scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run() || !Microbot.isLoggedIn()) {
                    return;
                }
                TrainingStage stage = this.resolveTrainingStage();
                KspTaskDebug.throttled(log, this.debugLogging, "Melee", "loop", 5_000L,
                        "loop | state={} status={} area={} npc={} player={} moving={} animating={} interacting={} inCombat={} hp={}/{} bankOpen={}",
                        this.state,
                        this.status,
                        stage.area.getDisplayName(),
                        stage.primaryNpc.getDisplayName(),
                        Rs2Player.getWorldLocation(),
                        Rs2Player.isMoving(),
                        Rs2Player.isAnimating(),
                        Rs2Player.isInteracting(),
                        this.isActivelyFighting(stage),
                        Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS),
                        Microbot.getClient().getRealSkillLevel(Skill.HITPOINTS),
                        Rs2Bank.isOpen());
                if (this.handleHealing()) {
                    this.state = CombatState.PREPARING;
                    return;
                }
                if (this.buryBonesInInventory(stage)) {
                    this.state = CombatState.PREPARING;
                    return;
                }
                if (this.lootOwnDrops(stage)) {
                    this.state = CombatState.LOOTING;
                    return;
                }
                if (this.shouldBank(stage)) {
                    this.state = CombatState.BANKING;
                    this.handleBanking(stage);
                    return;
                }
                if (this.equipInventoryUpgrades()) {
                    this.state = CombatState.EQUIPPING;
                    return;
                }
                if (!this.hasCurrentTaskWeaponEquippedOrInInventory()) {
                    this.state = CombatState.PREPARING;
                    this.setStatus("Waiting for melee weapon");
                    return;
                }
                if (this.ensureBalancedAttackStyle()) {
                    this.state = CombatState.PREPARING;
                    return;
                }
                if (!this.ensureInTargetArea(stage.area)) {
                    this.state = CombatState.WALKING_TO_AREA;
                    return;
                }
                if (this.hasLootNearby(stage)) {
                    this.state = CombatState.LOOTING;
                    this.lootOwnDrops(stage);
                    return;
                }
                if (this.isActivelyFighting(stage)) {
                    this.state = CombatState.FIGHTING;
                    this.handleHealing();
                    this.ensureBalancedAttackStyle();
                    if (this.isActivelyFighting(stage)) {
                        this.setStatus("Fighting " + stage.primaryNpc.getDisplayName());
                    }
                    return;
                }
                if (this.ensureBalancedAttackStyle()) {
                    this.state = CombatState.PREPARING;
                    return;
                }
                this.state = CombatState.FIGHTING;
                this.attackTarget(stage);
            }
            catch (Exception ex) {
                Microbot.logStackTrace((String)((Object)((Object)this)).getClass().getSimpleName(), (Exception)ex);
            }
        }, 0L, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);
        return true;
    }

    private TrainingStage resolveTrainingStage() {
        if (this.forcedCombatArea != null) {
            if (this.forcedCombatArea == CombatAreas.COWPEN) {
                return new TrainingStage(CombatAreas.COWPEN, NPC.COW, NPC.COW_CALF,
                        Arrays.stream(CowLoot.values()).map(CowLoot::getDisplayName).toArray(String[]::new));
            }
            if (this.forcedCombatArea == CombatAreas.CHICKENS) {
                return new TrainingStage(CombatAreas.CHICKENS, NPC.CHICKEN, null, CHICKEN_LOOT_NAMES);
            }
            if (this.forcedCombatArea == CombatAreas.AL_KHARID_WARRIOR) {
                return new TrainingStage(CombatAreas.AL_KHARID_WARRIOR, NPC.AL_KHARID_WARRIOR, null,
                        Arrays.stream(WarriorLoot.values()).map(WarriorLoot::getDisplayName).toArray(String[]::new));
            }
            if (this.forcedCombatArea == CombatAreas.HILL_GIANTS) {
                return new TrainingStage(CombatAreas.HILL_GIANTS, NPC.HILL_GIANT, null,
                        Arrays.stream(HillGiantLoot.values()).map(HillGiantLoot::getDisplayName).toArray(String[]::new));
            }
            return new TrainingStage(CombatAreas.MOSS_GIANTS, NPC.MOSS_GIANT, null,
                    Arrays.stream(MossGiantLoot.values()).map(MossGiantLoot::getDisplayName).toArray(String[]::new));
        }

        int attackLevel = this.getSkillLevel(Skill.ATTACK);
        int strengthLevel = this.getSkillLevel(Skill.STRENGTH);
        int defenceLevel = this.getSkillLevel(Skill.DEFENCE);
        if (attackLevel < CHICKEN_TARGET_COMBAT_STAT_LEVEL
                || strengthLevel < CHICKEN_TARGET_COMBAT_STAT_LEVEL
                || defenceLevel < CHICKEN_TARGET_COMBAT_STAT_LEVEL) {
            return new TrainingStage(CombatAreas.CHICKENS, NPC.CHICKEN, null, CHICKEN_LOOT_NAMES);
        }
        if (attackLevel < 30 || strengthLevel < 30 || defenceLevel < 30) {
            return new TrainingStage(CombatAreas.AL_KHARID_WARRIOR, NPC.AL_KHARID_WARRIOR, null, (String[])Arrays.stream(WarriorLoot.values()).map(WarriorLoot::getDisplayName).toArray(String[]::new));
        }
        if (attackLevel < 40 || strengthLevel < 40 || defenceLevel < 40) {
            return new TrainingStage(CombatAreas.HILL_GIANTS, NPC.HILL_GIANT, null, (String[])Arrays.stream(HillGiantLoot.values()).map(HillGiantLoot::getDisplayName).toArray(String[]::new));
        }
        return new TrainingStage(CombatAreas.MOSS_GIANTS, NPC.MOSS_GIANT, null, (String[])Arrays.stream(MossGiantLoot.values()).map(MossGiantLoot::getDisplayName).toArray(String[]::new));
    }

    private boolean handleHealing() {
        Food bestInventoryFood = this.getBestFoodInInventory();
        if (bestInventoryFood == null) {
            return false;
        }

        int currentHp = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
        int maxHp = Microbot.getClient().getRealSkillLevel(Skill.HITPOINTS);

        if (!this.shouldHealNow(currentHp, maxHp)) {
            return false;
        }

        this.setStatus("Eating " + bestInventoryFood.getDisplayName() + " at " + currentHp + "/" + maxHp + " hp");

        if (Rs2Inventory.interact(bestInventoryFood.getItemId(), "Eat")) {
            MeleeScript.sleepUntil(() -> Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS) > currentHp, 1800);
            return true;
        }

        return false;
    }

    private boolean buryBonesInInventory(TrainingStage stage) {
        if (Rs2Player.isMoving() || this.isActivelyFighting(stage)) {
            return false;
        }
        List<Rs2ItemModel> bones = Rs2Inventory.getBones();
        if (bones == null || bones.isEmpty()) {
            return false;
        }
        this.setStatus("Burying bones");
        for (Rs2ItemModel bone : bones) {
            if (bone == null || bone.getName() == null) {
                continue;
            }

            int boneId = bone.getId();
            int quantityBefore = Rs2Inventory.itemQuantity(boneId);
            if (!Rs2Inventory.interact(bone, "Bury")) {
                continue;
            }

            boolean buried = MeleeScript.sleepUntil(
                    () -> Rs2Inventory.itemQuantity(boneId) < quantityBefore,
                    800);
            this.debug("Bone bury interaction | item={} id={} quantityBefore={} buried={}",
                    bone.getName(),
                    boneId,
                    quantityBefore,
                    buried);
            return true;
        }
        return false;
    }

    private boolean shouldBank(TrainingStage stage) {
        if (this.hasInventoryEquipmentToEquip()) {
            return false;
        }

        if (!this.hasCurrentTaskWeaponEquippedOrInInventory()) {
            this.debug("Banking because required melee weapon is not equipped or in inventory");
            return true;
        }

        int foodCount = this.getFoodCountInInventory();
        if (foodCount > 0) {
            this.lastConfirmedFoodAtMs = System.currentTimeMillis();
        } else if (requiresCombatFood(stage)) {
            long noFoodObservedForMs = System.currentTimeMillis() - this.lastConfirmedFoodAtMs;
            if (Rs2Player.isAnimating()
                    || this.isActivelyFighting(stage)
                    || noFoodObservedForMs < NO_FOOD_CONFIRMATION_MS) {
                KspTaskDebug.throttled(log, this.debugLogging, "Melee", "no-food-confirmation", 1_000L,
                        "Delaying no-food bank decision | observedForMs={} animating={} activelyFighting={} inventoryEmptySlots={}",
                        noFoodObservedForMs,
                        Rs2Player.isAnimating(),
                        this.isActivelyFighting(stage),
                        Rs2Inventory.emptySlotCount());
                return false;
            }

            this.debug("Banking after confirmed no-food state | observedForMs={} area={}",
                    noFoodObservedForMs,
                    stage.area.getDisplayName());
            return this.shouldBankForNoFood(stage);
        }
        if (Rs2Inventory.isFull() && this.projectedFreeSlotsAfterBury() <= 0) {
            return true;
        }
        return false;
    }

    private void handleBanking(TrainingStage stage) {
        this.setStatus("Banking for " + stage.primaryNpc.getDisplayName());

        if (!Rs2Bank.isOpen()) {
            if (!Rs2Bank.walkToBankAndUseBank() && !Rs2Bank.openBank()) {
                return;
            }
            MeleeScript.sleepUntil(Rs2Bank::isOpen, 3000);
            return;
        }

        if (!KspBankMode.ensureWithdrawAsItem()) {
            this.debug("Waiting for withdraw-as-item mode before melee banking withdrawals");
            return;
        }

        if (KspBankWidgetHelper.closeBankTutorialOverlayIfOpenAndWait()) {
            return;
        }

        GearPlan gearPlan = this.buildGearPlan();

        if (!Rs2Inventory.isEmpty() && !this.hasMeleeSetupItemsInInventory(gearPlan)) {
            boolean hadSellableLoot = this.hasSellListItemInInventory();
            Rs2Bank.depositAll();
            boolean deposited = MeleeScript.sleepUntil(Rs2Inventory::isEmpty, 3000);
            if (deposited && hadSellableLoot) {
                this.pendingSellHandoff = true;
                this.debug("Deposited melee loot; requesting GE sell handoff");
            }
            return;
        }

        for (String desiredItem : gearPlan.desiredItems) {
            if (desiredItem == null
                    || Rs2Equipment.isWearing((String[]) new String[]{desiredItem})
                    || Rs2Inventory.hasItem((String[]) new String[]{desiredItem})) {
                continue;
            }

            if (Rs2Bank.count(desiredItem) > 0) {
                Rs2Bank.withdrawX(desiredItem, 1);
                MeleeScript.sleepUntil(() -> Rs2Inventory.hasItem((String[]) new String[]{desiredItem}), 2000);
                continue;
            }

            this.debug("Missing melee gear outside bank; GE_BUY should handle purchase | item={}", desiredItem);
        }

        Food bankFood = this.getBestFoodAvailableInBank();
        int currentFoodCount = this.getFoodCountInInventory();
        int missingFoodCount = Math.max(0, TARGET_FOOD_COUNT - currentFoodCount);

        if (bankFood != null && missingFoodCount > 0) {
            this.debug("Withdrawing combat food by item id | food={} id={} amount={}",
                    bankFood.getDisplayName(),
                    bankFood.getItemId(),
                    missingFoodCount);

            Rs2Bank.withdrawX(bankFood.getItemId(), missingFoodCount);
            MeleeScript.sleepUntil(() -> Rs2Inventory.itemQuantity(bankFood.getItemId()) >= 1, 2000);
        } else if (bankFood == null && missingFoodCount > 0) {
            this.debug("Not enough Trout/Salmon available in bank; GE_BUY should handle food purchases");
            if (requiresCombatFood(stage)) {
                this.setStatus("Waiting for 5 Trout/Salmon");
                return;
            }
        }

        Rs2Bank.closeBank();
        MeleeScript.sleepUntil(() -> !Rs2Bank.isOpen(), 2000);
    }

    private boolean equipInventoryUpgrades() {
        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
            MeleeScript.sleepUntil(() -> !Rs2Bank.isOpen(), 2000);
            return true;
        }

        GearPlan gearPlan = this.buildGearPlan();
        for (String desiredItem : gearPlan.desiredItems) {
            if (desiredItem == null || Rs2Equipment.isWearing((String[])new String[]{desiredItem}) || !Rs2Inventory.hasItem((String[])new String[]{desiredItem})) continue;
            this.setStatus("Equipping " + desiredItem);
            boolean interactionStarted = Rs2Inventory.wield((String[])new String[]{desiredItem});
            boolean equipped = interactionStarted
                    && MeleeScript.sleepUntil(() -> Rs2Equipment.isWearing((String[])new String[]{desiredItem}), 2000);
            this.debug("Equipment interaction | item={} interactionStarted={} equipped={} inventoryPresent={}",
                    desiredItem,
                    interactionStarted,
                    equipped,
                    Rs2Inventory.hasItem((String[]) new String[]{desiredItem}));
            if (!equipped) {
                this.setStatus("Retrying equipment: " + desiredItem);
            }
            return equipped;
        }
        return false;
    }

    private boolean lootOwnDrops(TrainingStage stage) {
        if (Rs2Player.isMoving() || this.isActivelyFighting(stage)) {
            return false;
        }
        Rs2TileItemModel loot = this.findNearestLoot(stage);
        if (loot == null) {
            return false;
        }

        this.setStatus("Looting " + loot.getName());
        boolean clicked = loot.pickup();
        this.debug("Loot pickup interaction | clicked={} item={} id={} qty={} loc={} player={}",
                clicked,
                loot.getName(),
                loot.getId(),
                loot.getQuantity(),
                loot.getWorldLocation(),
                Rs2Player.getWorldLocation());
        if (clicked) {
            MeleeScript.sleepUntil(() -> Rs2Player.isMoving() || Rs2Player.isInteracting() || this.findNearestLoot(stage) == null, 1_200);
        }
        return clicked;
    }

    private boolean hasLootNearby(TrainingStage stage) {
        return this.findNearestLoot(stage) != null;
    }

    private Rs2TileItemModel findNearestLoot(TrainingStage stage) {
        if (stage == null || stage.lootNames == null) {
            return null;
        }
        HashSet<String> lootNames = Arrays.stream(stage.lootNames)
                .filter(Objects::nonNull)
                .map(name -> name.trim().toLowerCase(Locale.ENGLISH))
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toCollection(HashSet::new));
        if (lootNames.isEmpty()) {
            return null;
        }

        return Microbot.getRs2TileItemCache().query()
                .fromWorldView()
                .within(LOOT_RADIUS)
                .where(item -> item.getName() != null
                        && item.isLootAble()
                        && this.isLocationInTargetArea(item.getWorldLocation(), stage)
                        && this.canStoreLoot(item)
                        && (lootNames.contains(item.getName().trim().toLowerCase(Locale.ENGLISH))
                            || (stage.area != CombatAreas.CHICKENS && item.isOwned())))
                .nearestOnClientThread(LOOT_RADIUS);
    }

    private boolean canStoreLoot(Rs2TileItemModel item) {
        if (Rs2Inventory.emptySlotCount() > 0) {
            return true;
        }
        return item.isStackable() && Rs2Inventory.hasItem(item.getId());
    }

    private boolean ensureInTargetArea(CombatAreas targetArea) {
        if (targetArea.contains(Rs2Player.getWorldLocation())) {
            KspWalkerGuard.clear("Melee:target-area");
            KspWalkerGuard.clear("Melee:hill-giants-entry");
            return true;
        }
        if (this.handleHillGiantDungeonEntry(targetArea)) {
            return false;
        }
        if (targetArea == CombatAreas.CHICKENS && this.openChickenRouteGateIfNeeded()) {
            return false;
        }
        if (Rs2Player.isMoving()) {
            return false;
        }
        this.setStatus("Walking to " + targetArea.getDisplayName());
        if (KspWalkerGuard.walkToDestination(
                "Melee:target-area",
                () -> this.resolveWalkTarget(targetArea),
                targetArea::contains,
                2,
                WEB_WALK_COOLDOWN_MS)) {
            this.lastWebWalkAtMs = System.currentTimeMillis();
            this.lastWalkTarget = null;
            this.debug("Requested melee area walk | player={} area={}",
                    Rs2Player.getWorldLocation(),
                    targetArea.getDisplayName());
        }
        return false;
    }

    private boolean handleHillGiantDungeonEntry(CombatAreas targetArea) {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (targetArea != CombatAreas.HILL_GIANTS || playerLocation == null) {
            return false;
        }

        if (playerLocation.getY() > 5000) {
            KspWalkerGuard.clear("Melee:hill-giants-entry");
            return false;
        }

        KspWalkerGuard.clear("Melee:target-area");

        if (playerLocation.distanceTo(EDGEVILLE_TRAPDOOR) > EDGEVILLE_TRAPDOOR_INTERACTION_DISTANCE) {
            this.setStatus("Walking to Edgeville dungeon");
            KspWalkerGuard.walkToPoint(
                    "Melee:hill-giants-entry",
                    EDGEVILLE_TRAPDOOR,
                    2,
                    WEB_WALK_COOLDOWN_MS);
            return true;
        }

        TileObject trapdoor = Rs2GameObject.getTileObject(
                EDGEVILLE_TRAPDOOR_OPEN_ID,
                EDGEVILLE_TRAPDOOR,
                EDGEVILLE_TRAPDOOR_INTERACTION_DISTANCE);
        if (trapdoor != null && Rs2GameObject.hasAction(trapdoor, "Climb-down")) {
            this.setStatus("Entering Edgeville dungeon");
            boolean entered = Rs2GameObject.interact(trapdoor, "Climb-down");
            if (entered) {
                MeleeScript.sleepUntil(() -> {
                    WorldPoint current = Rs2Player.getWorldLocation();
                    return current != null && current.getY() > 5000;
                }, 5_000);
            }
            return true;
        }

        trapdoor = Rs2GameObject.getTileObject(
                EDGEVILLE_TRAPDOOR_CLOSED_ID,
                EDGEVILLE_TRAPDOOR,
                EDGEVILLE_TRAPDOOR_INTERACTION_DISTANCE);
        if (trapdoor != null && Rs2GameObject.hasAction(trapdoor, "Open")) {
            this.setStatus("Opening Edgeville trapdoor");
            Rs2GameObject.interact(trapdoor, "Open");
            MeleeScript.sleepUntil(() -> Rs2GameObject.getTileObject(
                    EDGEVILLE_TRAPDOOR_OPEN_ID,
                    EDGEVILLE_TRAPDOOR,
                    EDGEVILLE_TRAPDOOR_INTERACTION_DISTANCE) != null, 2_000);
        }
        return true;
    }

    private WorldPoint resolveWalkTarget(CombatAreas targetArea) {
        if (targetArea == CombatAreas.CHICKENS) {
            return CHICKEN_WALK_TARGET;
        }
        if (targetArea == CombatAreas.HILL_GIANTS) {
            return HILL_GIANT_WALK_TARGET;
        }
        return targetArea.getRandomPoint();
    }

    private boolean openChickenRouteGateIfNeeded() {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null
                || playerLocation.getPlane() != CHICKEN_GATE_EAST.getPlane()
                || playerLocation.getY() <= CHICKEN_GATE_EAST.getY()
                || playerLocation.distanceTo(CHICKEN_GATE_EAST) > CHICKEN_GATE_INTERACTION_DISTANCE) {
            return false;
        }

        TileObject gate = Rs2GameObject.getTileObject(
                CHICKEN_GATE_EAST_ID,
                CHICKEN_GATE_EAST,
                CHICKEN_GATE_INTERACTION_DISTANCE);
        if (gate == null) {
            gate = Rs2GameObject.getTileObject(
                    CHICKEN_GATE_WEST_ID,
                    CHICKEN_GATE_WEST,
                    CHICKEN_GATE_INTERACTION_DISTANCE);
        }

        if (gate == null || !Rs2GameObject.hasAction(gate, "Open")) {
            return false;
        }

        TileObject selectedGate = gate;
        KspWalkerGuard.clear("Melee:target-area");
        this.setStatus("Opening gate to chickens");
        boolean opened = Rs2GameObject.interact(selectedGate, "Open");
        this.debug(
                "Chicken route gate interaction | opened={} gateId={} gate={} player={}",
                opened,
                selectedGate.getId(),
                selectedGate.getWorldLocation(),
                playerLocation);
        if (opened) {
            MeleeScript.sleepUntil(
                    () -> {
                        WorldPoint currentLocation = Rs2Player.getWorldLocation();
                        return (currentLocation != null && currentLocation.getY() <= CHICKEN_GATE_EAST.getY())
                                || !Rs2GameObject.hasAction(selectedGate, "Open");
                    },
                    2_000);
        }
        return opened;
    }

    private void attackTarget(TrainingStage stage) {
        List<Rs2NpcModel> candidates;
        Rs2NpcModel target;
        Player localPlayer = Microbot.getClient().getLocalPlayer();
        Actor currentInteracting = Rs2Player.getInteracting();
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (stage == null || localPlayer == null || playerLocation == null) {
            return;
        }
        Rs2NpcModel currentAttacker = findNpcAttackingPlayer(localPlayer, playerLocation, stage);
        if (currentAttacker != null) {
            this.setStatus("Fighting " + currentAttacker.getName());
            KspTaskDebug.throttled(log, this.debugLogging, "Melee", "already-under-attack", 3_000L,
                    "Skipping new target; player is actively fighting attacker | attacker={} id={} loc={} playerInteracting={} animating={}",
                    currentAttacker.getName(),
                    currentAttacker.getId(),
                    currentAttacker.getWorldLocation(),
                    currentInteracting,
                    Rs2Player.isAnimating());
            return;
        }
        ArrayList<String> npcNames = new ArrayList<String>();
        npcNames.add(stage.primaryNpc.getDisplayName().toLowerCase(Locale.ENGLISH));
        if (stage.secondaryNpc != null) {
            npcNames.add(stage.secondaryNpc.getDisplayName().toLowerCase(Locale.ENGLISH));
        }
        candidates = Microbot.getRs2NpcCache().query()
                .fromWorldView()
                .where(npc -> npc.getCombatLevel() > 0 && !npc.isDead())
                .where(npc -> {
                    String name = npc.getName();
                    return name != null && npcNames.contains(name.toLowerCase(Locale.ENGLISH));
                })
                .where(npc -> this.isNpcInTargetArea(npc, stage))
                .toListOnClientThread()
                .stream()
                .filter(npc -> this.canAttackNpc(npc, localPlayer))
                .sorted(Comparator.comparingInt((Rs2NpcModel npc) -> npc.getInteracting() == null ? 0 : 1)
                        .thenComparingInt(npc -> npc.getWorldLocation().distanceTo(playerLocation)))
                .collect(Collectors.toList());
        target = candidates.isEmpty() ? null : candidates.get(0);
        if (target == null) {
            this.setStatus("Waiting for " + stage.primaryNpc.getDisplayName());
            KspTaskDebug.throttled(log, this.debugLogging, "Melee", "no-target", 3_000L,
                    "no attack target found | primary={} secondary={} player={} area={}",
                    stage.primaryNpc.getDisplayName(),
                    stage.secondaryNpc != null ? stage.secondaryNpc.getDisplayName() : "none",
                    playerLocation,
                    stage.area.getDisplayName());
            return;
        }
        if (Objects.equals(currentInteracting, target.getNpc()) && Rs2Player.isAnimating()) {
            this.setStatus("Fighting " + target.getName());
            return;
        }
        if (!this.canAttackNpc(target, localPlayer)) {
            this.setStatus("Waiting for an available " + stage.primaryNpc.getDisplayName());
            this.debug(
                    "Skipped claimed npc before attack | target={} id={} interacting={} healthRatio={}",
                    target.getName(),
                    target.getId(),
                    target.getInteracting(),
                    target.getHealthRatio());
            return;
        }
        currentAttacker = findNpcAttackingPlayer(localPlayer, playerLocation, stage);
        if (isActivelyFighting(stage)) {
            this.setStatus("Fighting " + (currentAttacker != null
                    ? currentAttacker.getName()
                    : stage.primaryNpc.getDisplayName()));
            this.debug(
                    "Cancelled npc attack because combat started before click | selectedTarget={} attacker={} playerInteracting={}",
                    target.getName(),
                    currentAttacker != null ? currentAttacker.getName() : "unknown",
                    Rs2Player.getInteracting());
            return;
        }
        this.setStatus("Attacking " + target.getName());
        this.debug("Attempting npc attack | target={} id={} loc={} combatLevel={} reachable={} player={} distance={} targetInteracting={}",
                target.getName(),
                target.getId(),
                target.getWorldLocation(),
                target.getCombatLevel(),
                target.isReachable(),
                playerLocation,
                playerLocation.distanceTo(target.getWorldLocation()),
                target.getInteracting());
        target.click("Attack");
        boolean activityStarted = MeleeScript.sleepUntil(() -> Rs2Player.isInteracting() || Rs2Player.isAnimating(), (int)2000);
        this.debug("Npc attack post-click wait | activityStarted={} target={} player={} moving={} animating={} interacting={} inCombat={}",
                activityStarted,
                target.getName(),
                Rs2Player.getWorldLocation(),
                Rs2Player.isMoving(),
                Rs2Player.isAnimating(),
                Rs2Player.isInteracting(),
                Rs2Combat.inCombat());
    }

    private boolean isNpcInTargetArea(Rs2NpcModel npc, TrainingStage stage) {
        return npc != null
                && stage != null
                && this.isLocationInTargetArea(npc.getWorldLocation(), stage);
    }

    private boolean isLocationInTargetArea(WorldPoint location, TrainingStage stage) {
        if (location == null || stage == null || !stage.area.contains(location)) {
            return false;
        }

        return stage.area != CombatAreas.CHICKENS
                || location.distanceTo(CHICKEN_WALK_TARGET) <= CHICKEN_COMBAT_RADIUS;
    }

    private boolean canAttackNpc(Rs2NpcModel npc, Player localPlayer) {
        if (npc == null || localPlayer == null) {
            return false;
        }

        Actor interacting = npc.getInteracting();
        if (interacting != null) {
            return Objects.equals(interacting, localPlayer);
        }

        return npc.getHealthRatio() < 0;
    }

    private boolean isActivelyFighting() {
        return isActivelyFighting(resolveTrainingStage());
    }

    private boolean isActivelyFighting(TrainingStage stage) {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        Actor interacting = Rs2Player.getInteracting();
        if (interacting != null
                && playerLocation != null
                && isActiveCombatLocation(interacting.getWorldLocation(), stage)
                && !this.isIgnoredCombatNpc(interacting.getName())
                && interacting.getCombatLevel() > 0
                && interacting.getHealthRatio() != 0) {
            return true;
        }

        Player localPlayer = Microbot.getClient().getLocalPlayer();
        if (localPlayer == null || playerLocation == null) {
            return false;
        }

        return findNpcAttackingPlayer(localPlayer, playerLocation, stage) != null
                || (Rs2Combat.inCombat()
                    && stage.area.contains(playerLocation)
                    && (Rs2Player.isAnimating() || Rs2Player.isInteracting()));
    }

    private boolean isActiveCombatLocation(WorldPoint location, TrainingStage stage) {
        if (location == null || stage == null) {
            return false;
        }

        return this.isLocationInTargetArea(location, stage);
    }

    private Rs2NpcModel findNpcAttackingPlayer(Player localPlayer, WorldPoint playerLocation) {
        return findNpcAttackingPlayer(localPlayer, playerLocation, resolveTrainingStage());
    }

    private Rs2NpcModel findNpcAttackingPlayer(Player localPlayer, WorldPoint playerLocation, TrainingStage stage) {
        if (localPlayer == null || playerLocation == null) {
            return null;
        }

        return Microbot.getRs2NpcCache().query()
                .fromWorldView()
                .where(npc -> npc != null
                        && !npc.isDead()
                        && npc.getCombatLevel() > 0
                        && !this.isIgnoredCombatNpc(npc.getName())
                        && this.isActiveCombatLocation(npc.getWorldLocation(), stage)
                        && (Rs2Player.isInteracting()
                            || npc.getWorldLocation().distanceTo(playerLocation) <= PASSIVE_ATTACKER_MAX_DISTANCE)
                        && Objects.equals(npc.getInteracting(), localPlayer))
                .toListOnClientThread()
                .stream()
                .min(Comparator.comparingInt(npc -> npc.getWorldLocation()
                        .distanceTo(playerLocation)))
                .orElse(null);
    }

    private boolean isIgnoredCombatNpc(String npcName) {
        return npcName != null
                && IGNORED_COMBAT_NPC_NAMES.contains(npcName.trim().toLowerCase(Locale.ENGLISH));
    }

    private boolean ensureBalancedAttackStyle() {
        int attack = this.getSkillLevel(Skill.ATTACK);
        int strength = this.getSkillLevel(Skill.STRENGTH);
        int defence = this.getSkillLevel(Skill.DEFENCE);

        Skill targetSkill;
        WidgetInfo targetWidget;
        int targetStyleIndex;

        if (attack <= strength && attack <= defence) {
            targetSkill = Skill.ATTACK;
            targetWidget = WidgetInfo.COMBAT_STYLE_ONE;
            targetStyleIndex = 0;
        } else if (strength <= attack && strength <= defence) {
            targetSkill = Skill.STRENGTH;
            targetWidget = WidgetInfo.COMBAT_STYLE_TWO;
            targetStyleIndex = 1;
        } else {
            targetSkill = Skill.DEFENCE;
            targetWidget = WidgetInfo.COMBAT_STYLE_FOUR;
            targetStyleIndex = 3;
        }

        int currentStyleIndex = Microbot.getVarbitPlayerValue(43);

        if (currentStyleIndex == targetStyleIndex) {
            this.setStatus("Training " + targetSkill.getName().toLowerCase(Locale.ENGLISH));
            return false;
        }

        if (Rs2Player.isMoving()) {
            this.setStatus("Waiting to switch combat style");
            return false;
        }

        if (Rs2Tab.getCurrentTab() != InterfaceTab.COMBAT) {
            Rs2Tab.switchToCombatOptionsTab();
            MeleeScript.sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.COMBAT, 1500);
        }

        this.setStatus("Switching combat style to " + targetSkill.getName().toLowerCase(Locale.ENGLISH));

        Rs2Combat.setAttackStyle(targetWidget);

        boolean switched = MeleeScript.sleepUntil(
                () -> Microbot.getVarbitPlayerValue(43) == targetStyleIndex,
                2000
        );

        this.debug("Combat style switch | targetSkill={} targetIndex={} oldIndex={} newIndex={} switched={}",
                targetSkill.getName(),
                targetStyleIndex,
                currentStyleIndex,
                Microbot.getVarbitPlayerValue(43),
                switched);

        return switched;
    }

    private GearPlan buildGearPlan() {
        int attackLevel = this.getSkillLevel(Skill.ATTACK);
        int defenceLevel = this.getSkillLevel(Skill.DEFENCE);

        Buy.MeleeGearPlan buyPlan = Buy.buildMeleeGearPlan(
                attackLevel,
                defenceLevel,
                this.isDragonSlayerCompleted(),
                this::hasItemAnywhere
        );

        return new GearPlan(buyPlan.getDesiredItems());
    }

    private String getBestOwnedWeaponUpToCurrentLevel() {
        int attackLevel = this.getSkillLevel(Skill.ATTACK);
        return Arrays.stream(Weapons.values()).filter(weapon -> attackLevel >= weapon.getRequiredAttackLevel()).sorted(Comparator.comparingInt(Weapons::getRequiredAttackLevel).thenComparingInt(Enum::ordinal).reversed()).map(Weapons::getDisplayName).filter(this::hasWeaponEquippedOrInInventory).findFirst().orElse(null);
    }

    private boolean hasCurrentTaskWeaponEquippedOrInInventory() {
        return this.getBestOwnedWeaponUpToCurrentLevel() != null;
    }

    private boolean hasWeaponEquippedOrInInventory(String itemName) {
        return itemName != null && (Rs2Equipment.isWearing((String[])new String[]{itemName}) || Rs2Inventory.hasItem((String[])new String[]{itemName}));
    }

    private boolean isDragonSlayerCompleted() {
        return Rs2Player.getQuestState((Quest)Quest.DRAGON_SLAYER_I) == QuestState.FINISHED;
    }

    private boolean hasInventoryEquipmentToEquip() {
        return this.buildGearPlan().desiredItems.stream().anyMatch(item -> item != null && Rs2Inventory.hasItem((String[])new String[]{item}) && !Rs2Equipment.isWearing((String[])new String[]{item}));
    }

    private boolean hasMeleeSetupItemsInInventory(GearPlan gearPlan) {
        if (gearPlan != null && gearPlan.desiredItems.stream().anyMatch(item -> item != null && Rs2Inventory.hasItem((String[]) new String[]{item}))) {
            return true;
        }

        return this.getFoodCountInInventory() > 0;
    }

    private boolean hasSellListItemInInventory() {
        return Arrays.stream(SellList.values())
                .anyMatch(item -> Rs2Inventory.hasItem(item.getDisplayName())
                        || Rs2Inventory.hasItem(item.getDisplayName(), true));
    }

    public boolean hasPendingSellHandoff() {
        return this.pendingSellHandoff;
    }

    public void clearPendingSellHandoff() {
        this.pendingSellHandoff = false;
    }

    private boolean hasItemAnywhere(String itemName) {
        return itemName != null && (Rs2Equipment.isWearing((String[])new String[]{itemName}) || Rs2Inventory.hasItem((String[])new String[]{itemName}) || Rs2Inventory.hasItem((String)itemName, (boolean)true) || Rs2Bank.count((String)itemName) > 0);
    }

    private Food getBestFoodInInventory() {
        return Buy.getBestMeleeFoodInInventory();
    }

    private Food getBestFoodAvailableInBank() {
        return Buy.getBestMeleeFoodAvailableInBank();
    }

    private int getFoodCountInInventory() {
        return Buy.getMeleeFoodCountInInventory();
    }

    private boolean shouldBankForNoFood(TrainingStage stage) {
        Food bankFood = this.getBestFoodAvailableInBank();
        if (!requiresCombatFood(stage) && bankFood == null) {
            return false;
        }

        if (requiresCombatFood(stage)) {
            return true;
        }

        if (!Rs2Inventory.isEmpty()) {
            return true;
        }
        if (stage != null && !stage.area.contains(Rs2Player.getWorldLocation())) {
            return bankFood != null;
        }
        int currentHp = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
        int maxHp = Microbot.getClient().getRealSkillLevel(Skill.HITPOINTS);
        return bankFood != null && this.shouldHealNow(currentHp, maxHp);
    }

    private boolean requiresCombatFood(TrainingStage stage) {
        if (stage == null) {
            return false;
        }

        return stage.primaryNpc != NPC.CHICKEN;
    }

    private boolean shouldHealNow(int currentHp, int maxHp) {
        int healThreshold = (int)Math.ceil((double)maxHp * 0.28);
        return currentHp <= healThreshold;
    }

    private int projectedFreeSlotsAfterBury() {
        int emptySlots = Rs2Inventory.emptySlotCount();
        List<Rs2ItemModel> bones = Rs2Inventory.getBones();
        return emptySlots + (bones == null ? 0 : bones.size());
    }

    private int getSkillLevel(Skill skill) {
        return Microbot.getClient().getRealSkillLevel(skill);
    }

    public CombatAreas getTargetArea() {
        return this.resolveTrainingStage().area;
    }

    public void shutdown() {
        this.lastWebWalkAtMs = 0L;
        this.lastWalkTarget = null;
        KspWalkerGuard.clear("Melee:target-area");
        KspWalkerGuard.clear("Melee:hill-giants-entry");
        this.state = CombatState.PREPARING;
        this.status = "Idle";
        super.shutdown();
    }

    private void debug(String message, Object ... args) {
        if (this.debugLogging) {
            KspTaskDebug.info(log, true, "Melee", message, args);
        }
    }

    private void setStatus(String status) {
        this.status = status;
        Microbot.status = status;
    }

    public String getStatus() {
        return this.status;
    }

    public CombatState getState() {
        return this.state;
    }

    private static class GearPlan {
        private final List<String> desiredItems;

        public GearPlan(List<String> desiredItems) {
            this.desiredItems = desiredItems;
        }
    }

    private static class TrainingStage {
        private final CombatAreas area;
        private final NPC primaryNpc;
        private final NPC secondaryNpc;
        private final String[] lootNames;

        public TrainingStage(CombatAreas area, NPC primaryNpc, NPC secondaryNpc, String[] lootNames) {
            this.area = area;
            this.primaryNpc = primaryNpc;
            this.secondaryNpc = secondaryNpc;
            this.lootNames = lootNames;
        }
    }
}
