package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.miningscript;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import javax.inject.Singleton;

import net.runelite.api.ObjectComposition;
import net.runelite.api.ObjectID;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspBankMode;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspTaskDebug;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspWalkerGuard;
import net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.KspBankWidgetHelper;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.areas.Areas;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.equiplevels.PickaxeEquip;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.levelreqmining.MiningReq;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.rocklevel.RockLevel;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.buyscript.Buy;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.depositbox.Rs2DepositBox;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class MiningScript extends Script
{
    private static final Logger log = LoggerFactory.getLogger(MiningScript.class);

    private static final int LOOP_DELAY_MS = 600;
    private static final int WEB_WALK_COOLDOWN_MS = 3_000;
    private static final int OBJECT_INTERACTION_COOLDOWN_MS = 2_500;
    private static final int ROCK_SEARCH_PADDING_TILES = 8;
    private static final int OUT_OF_AREA_ROCK_FALLBACK_RADIUS = 4;
    private static final int MID_TIER_RANDOM_MAX_LEVEL = 60;
    private static final String BANK_WALK_KEY = "Mining:bank";
    private static final String DEPOSIT_BOX_WALK_KEY = "Mining:port-sarim-deposit-box";
    private static final int MUGGER_COMBAT_LEVEL = 6;
    private static final int RAT_COMBAT_LEVEL = 6;
    private static final int SILVER_SAFE_COMBAT_LEVEL = (MUGGER_COMBAT_LEVEL * 2) + 1;
    private static final int IRON_SAFE_COMBAT_LEVEL = (RAT_COMBAT_LEVEL * 2) + 1;
    private static final String COPPER_ORE_NAME = Buy.COPPER_ORE_NAME;
    private static final String TIN_ORE_NAME = Buy.TIN_ORE_NAME;
    private static final int[] COPPER_ROCK_IDS = {
            ObjectID.COPPER_ROCKS,
            ObjectID.COPPER_ROCKS_10943,
            ObjectID.COPPER_ROCKS_11161,
            ObjectID.COPPER_ROCKS_37944
    };
    private static final int[] TIN_ROCK_IDS = {
            ObjectID.TIN_ROCKS,
            ObjectID.TIN_ROCKS_11360,
            ObjectID.TIN_ROCKS_11361,
            ObjectID.TIN_ROCKS_37945
    };
    private static final int[] IRON_ROCK_IDS = {
            ObjectID.IRON_ROCKS,
            ObjectID.IRON_ROCKS_11365,
            ObjectID.IRON_ROCKS_36203,
            ObjectID.IRON_ROCKS_42833
    };
    private static final int[] SILVER_ROCK_IDS = {
            ObjectID.SILVER_ROCKS,
            ObjectID.SILVER_ROCKS_11369,
            ObjectID.SILVER_ROCKS_36205
    };
    private static final int[] COAL_ROCK_IDS = {
            ObjectID.COAL_ROCKS,
            ObjectID.COAL_ROCKS_11366,
            ObjectID.COAL_ROCKS_11367,
            ObjectID.COAL_ROCKS_36204
    };
    private static final int[] GOLD_ROCK_IDS = {
            ObjectID.GOLD_ROCKS,
            ObjectID.GOLD_ROCKS_11371,
            ObjectID.GOLD_ROCKS_36206
    };

    private static final List<String> PICKAXE_NAMES = Buy.PICKAXE_NAME_LIST;

    private Areas targetArea = Areas.TIN_COPPER_VARROCK_EAST;

    private boolean startingTargetRockInitialized;
    private int initializedSelectionBand = -1;
    private RockLevel randomMidTierRock;
    private boolean debugLogging;
    private boolean progressiveMining = true;
    private boolean walkingToTargetArea;

    private long lastWebWalkAtMs;
    private long lastObjectInteractionAtMs;
    private long lastUnderAttackAtMs;

    public void setDebugLogging(boolean debugLogging)
    {
        this.debugLogging = debugLogging;
    }

    public void setProgressiveMining(boolean progressiveMining)
    {
        this.progressiveMining = progressiveMining;
    }

    public boolean run(Areas area)
    {
        shutdown();

        this.targetArea = area;
        this.startingTargetRockInitialized = false;
        this.randomMidTierRock = null;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            if (!super.run() || !Microbot.isLoggedIn())
            {
                return;
            }

            int miningLevel = Microbot.getClient().getRealSkillLevel(Skill.MINING);
            int attackLevel = Microbot.getClient().getRealSkillLevel(Skill.ATTACK);

            initializeStartingTargetRock(miningLevel);

            Areas desiredArea = progressiveMining ? resolveTargetArea(miningLevel) : targetArea;

            if (desiredArea != targetArea)
            {
                targetArea = desiredArea;
                debug("Switching mining area to {} for mining level {}", targetArea.getDisplayName(), miningLevel);
            }

            RockLevel targetRock = targetArea == Areas.TIN_COPPER_VARROCK_EAST
                    ? getStarterRockToMine()
                    : resolveTargetRock(miningLevel);
            KspTaskDebug.throttled(log, debugLogging, "Mining", "loop", 5_000L,
                    "loop | level={} attack={} area={} targetRock={} player={} moving={} animating={} interacting={} invFull={} bankOpen={}",
                    miningLevel,
                    attackLevel,
                    targetArea.getDisplayName(),
                    targetRock != null ? targetRock.getDisplayName() : "none",
                    Rs2Player.getWorldLocation(),
                    Rs2Player.isMoving(),
                    Rs2Player.isAnimating(),
                    Rs2Player.isInteracting(),
                    Rs2Inventory.isFull(),
                    Rs2Bank.isOpen());

            if (Rs2Inventory.isFull())
            {
                debug("Inventory full; banking ores | player={} area={} pickaxeToKeep={}",
                        Rs2Player.getWorldLocation(),
                        targetArea.getDisplayName(),
                        resolveInventoryPickaxeToKeep(miningLevel));
                if (targetArea == Areas.GOLD_RIMMINGTON)
                {
                    depositGoldOresAtPortSarim(miningLevel);
                }
                else
                {
                    bankOresOnly(miningLevel);
                }
                return;
            }

            if (Rs2Bank.isOpen() && hasAnyPickaxeEquippedOrInInventory())
            {
                Microbot.status = "Closing bank";
                closeBankIfOpen();
                return;
            }

            if (!upgradePickaxe(miningLevel, attackLevel))
            {
                return;
            }

            if (!hasAnyPickaxeEquippedOrInInventory())
            {
                closeBankIfOpen();
                Microbot.status = "No pickaxe available";
                debug("No pickaxe available in inventory or equipment, waiting before proceeding");
                return;
            }

            if (targetArea == Areas.SILVER_VARROCK_WEST && !canMineSilverSafely())
            {
                KspWalkerGuard.clearActiveWalker("ksp_account_builder_silver_mugger_guard");
                KspWalkerGuard.clear("Mining:target-area");
                walkingToTargetArea = false;
                Microbot.status = "Waiting for Mugger to become passive";
                return;
            }

            if (targetArea == Areas.IRON_VARROCK_EAST && !canMineIronSafely())
            {
                KspWalkerGuard.clearActiveWalker("ksp_account_builder_iron_rat_guard");
                KspWalkerGuard.clear("Mining:target-area");
                walkingToTargetArea = false;
                Microbot.status = "Waiting for Rat to become passive";
                return;
            }

            if (!ensureInTargetArea())
            {
                return;
            }

            if (!isIdleInTargetArea())
            {
                return;
            }

            mineForCurrentLevel(miningLevel);

        }, 0L, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);

        return true;
    }

    private boolean upgradePickaxe(int miningLevel, int attackLevel)
    {
        MiningReq bestMiningReq = MiningReq.bestForMiningLevel(miningLevel);
        String targetPickaxeName = resolveDesiredPickaxe(bestMiningReq);

        if (targetPickaxeName == null)
        {
            return true;
        }

        String activePickaxeName = resolveBestOwnedPickaxeName(targetPickaxeName);

        if (activePickaxeName == null)
        {
            ensureInventoryTabOpen();

            if (!Rs2Bank.walkToBankAndUseBank() && !Rs2Bank.openBank())
            {
                return false;
            }

            activePickaxeName = resolveBestOwnedPickaxeName(targetPickaxeName);

            if (activePickaxeName == null)
            {
                debug("No eligible pickaxe available up to target {}", targetPickaxeName);
                closeBankIfOpen();
                return true;
            }
        }

        MiningReq activePickaxeReq = resolveMiningReq(activePickaxeName);
        boolean canEquipActivePickaxe = activePickaxeReq != null && canEquipDesiredPickaxe(activePickaxeReq, attackLevel);

        if (!Rs2Equipment.isWearing(activePickaxeName) && !Rs2Inventory.hasItem(activePickaxeName))
        {
            if (!Rs2Bank.isOpen())
            {
                ensureInventoryTabOpen();

                if (!Rs2Bank.walkToBankAndUseBank() && !Rs2Bank.openBank())
                {
                    return false;
                }
            }

            if (Rs2Bank.isOpen() && Rs2Bank.count(activePickaxeName) > 0)
            {
                if (KspBankWidgetHelper.closeBankTutorialOverlayIfOpen())
                {
                    sleep(300);
                    return false;
                }

                if (!KspBankMode.ensureWithdrawAsItem())
                {
                    debug("Waiting for withdraw-as-item mode before withdrawing {}", activePickaxeName);
                    return false;
                }

                String pickaxeToWithdraw = activePickaxeName;

                Rs2Bank.withdrawOne(activePickaxeName);
                sleepUntil(() -> Rs2Inventory.hasItem(pickaxeToWithdraw), 3_000);
            }
        }

        if (canEquipActivePickaxe
                && Rs2Inventory.hasItem(activePickaxeName)
                && !Rs2Equipment.isWearing(activePickaxeName))
        {
            if (Rs2Bank.isOpen())
            {
                closeBankIfOpen();
                return false;
            }

            String pickaxeToWield = activePickaxeName;

            Rs2Inventory.wield(activePickaxeName);
            sleepUntil(() -> Rs2Equipment.isWearing(pickaxeToWield), 2_000);
        }

        if (Rs2Bank.isOpen())
        {
            if (KspBankWidgetHelper.closeBankTutorialOverlayIfOpen())
            {
                sleep(300);
                return false;
            }

            depositOutdatedPickaxes(activePickaxeName);

            if (!hasOutdatedPickaxeInInventory(activePickaxeName))
            {
                closeBankIfOpen();
            }

            return false;
        }

        return Rs2Equipment.isWearing(activePickaxeName) || Rs2Inventory.hasItem(activePickaxeName);
    }

    private void depositOutdatedPickaxes(String desiredPickaxeName)
    {
        for (String pickaxeName : PICKAXE_NAMES)
        {
            if (pickaxeName.equalsIgnoreCase(desiredPickaxeName))
            {
                continue;
            }

            if (Rs2Inventory.hasItem(pickaxeName))
            {
                Rs2Bank.depositAll(pickaxeName);
            }
        }
    }

    private boolean hasOutdatedPickaxeInInventory(String desiredPickaxeName)
    {
        for (String pickaxeName : PICKAXE_NAMES)
        {
            if (!pickaxeName.equalsIgnoreCase(desiredPickaxeName) && Rs2Inventory.hasItem(pickaxeName))
            {
                return true;
            }
        }

        return false;
    }

    private String resolveDesiredPickaxe(MiningReq miningReq)
    {
        return miningReq != null ? miningReq.getDisplayName() : null;
    }

    private String resolveBestOwnedPickaxeName(String targetPickaxeName)
    {
        int targetIndex = PICKAXE_NAMES.indexOf(targetPickaxeName);

        if (targetIndex < 0)
        {
            return null;
        }

        for (int index = targetIndex; index >= 0; index--)
        {
            String pickaxeName = PICKAXE_NAMES.get(index);

            if (Rs2Equipment.isWearing(pickaxeName)
                    || Rs2Inventory.hasItem(pickaxeName)
                    || (Rs2Bank.isOpen() && Rs2Bank.count(pickaxeName) > 0))
            {
                return pickaxeName;
            }
        }

        return null;
    }

    private MiningReq resolveMiningReq(String pickaxeName)
    {
        if (pickaxeName == null)
        {
            return null;
        }

        return Arrays.stream(MiningReq.values())
                .filter(req -> pickaxeName.equalsIgnoreCase(req.getDisplayName()))
                .findFirst()
                .orElse(null);
    }

    private boolean canEquipDesiredPickaxe(MiningReq miningReq, int attackLevel)
    {
        PickaxeEquip equipRequirement = PickaxeEquip.valueOf(miningReq.name());
        return attackLevel >= equipRequirement.getRequiredAttackLevel();
    }

    private boolean ensureInTargetArea()
    {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();

        if (playerLocation == null)
        {
            debug("Cannot walk to mining area; player location is null");
            return false;
        }

        WorldArea area = targetArea.toWorldArea();

        if (area.contains(playerLocation))
        {
            clearTargetAreaWalkIfNeeded(area);
            return true;
        }

        if (Rs2Player.isMoving())
        {
            KspTaskDebug.throttled(log, debugLogging, "Mining", "walk-moving", 3_000L,
                    "waiting for walker | player={} targetArea={} walkerTarget={}",
                    playerLocation,
                    targetArea.getDisplayName(),
                    Rs2Walker.getCurrentTarget());
            return false;
        }

        Microbot.status = "Walking to " + targetArea.getDisplayName();

        if (KspWalkerGuard.walkToDestination(
                "Mining:target-area",
                targetArea::getRandomPoint,
                area::contains,
                3,
                WEB_WALK_COOLDOWN_MS))
        {
            lastWebWalkAtMs = System.currentTimeMillis();
            walkingToTargetArea = true;
            debug("Requested mining area walk | player={} walkerTarget={} area={}",
                    playerLocation,
                    Rs2Walker.getCurrentTarget(),
                    targetArea.getDisplayName());
        }

        return false;
    }

    private void clearTargetAreaWalkIfNeeded(WorldArea area)
    {
        WorldPoint walkerTarget = Rs2Walker.getCurrentTarget();

        if (!walkingToTargetArea && (walkerTarget == null || !area.contains(walkerTarget)))
        {
            return;
        }

        KspWalkerGuard.clearActiveWalker("ksp_account_builder_mining_reached_area");
        KspWalkerGuard.clear("Mining:target-area");
        walkingToTargetArea = false;
    }

    private WorldPoint getAreaCenter()
    {
        int centerX = (targetArea.getSouthWest().getX() + targetArea.getNorthEast().getX()) / 2;
        int centerY = (targetArea.getSouthWest().getY() + targetArea.getNorthEast().getY()) / 2;
        int plane = targetArea.getSouthWest().getPlane();

        return new WorldPoint(centerX, centerY, plane);
    }

    private void ensureInventoryTabOpen()
    {
        if (Rs2Tab.getCurrentTab() != InterfaceTab.INVENTORY)
        {
            Rs2Tab.switchTo(InterfaceTab.INVENTORY);
            sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.INVENTORY, 1_200);
        }
    }

    private void bankOresOnly(int miningLevel)
    {
        ensureInventoryTabOpen();

        BankLocation bankLocation = resolveBankLocation();
        WorldPoint bankPoint = bankLocation.getWorldPoint();
        WorldPoint playerLocation = Rs2Player.getWorldLocation();

        if (playerLocation == null)
        {
            return;
        }

        if (!Rs2Bank.isNearBank(bankLocation, 8))
        {
            Microbot.status = "Walking to " + bankLocation;
            KspWalkerGuard.walkToDestination(
                    BANK_WALK_KEY,
                    bankLocation::getWorldPoint,
                    point -> point != null && point.distanceTo(bankPoint) <= 8,
                    6,
                    WEB_WALK_COOLDOWN_MS);
            return;
        }

        KspWalkerGuard.clearReachedDestination(
                BANK_WALK_KEY,
                "ksp_account_builder_mining_reached_bank");

        if (!Rs2Bank.isOpen() && !Rs2Bank.openBank())
        {
            return;
        }

        if (Rs2Bank.isOpen())
        {
            if (KspBankWidgetHelper.closeBankTutorialOverlayIfOpen())
            {
                sleep(300);
                return;
            }

            String pickaxeToKeep = resolveInventoryPickaxeToKeep(miningLevel);

            if (pickaxeToKeep != null)
            {
                Rs2Bank.depositAllExcept(pickaxeToKeep);
                sleepUntil(() -> !Rs2Inventory.isFull(), 2_000);
            }
            else
            {
                Rs2Bank.depositAll();
                sleepUntil(() -> Rs2Inventory.isEmpty(), 2_000);
            }

            sleep(300);
            closeBankIfOpen();
        }
    }

    private BankLocation resolveBankLocation()
    {
        if (targetArea == Areas.TIN_COPPER_VARROCK_EAST || targetArea == Areas.IRON_VARROCK_EAST)
        {
            return BankLocation.VARROCK_EAST;
        }

        if (targetArea == Areas.CLAY_VARROCK_WEST || targetArea == Areas.SILVER_VARROCK_WEST)
        {
            return BankLocation.VARROCK_WEST;
        }

        return BankLocation.EDGEVILLE;
    }

    private void depositGoldOresAtPortSarim(int miningLevel)
    {
        ensureInventoryTabOpen();

        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        WorldArea depositArea = Areas.PORT_SARIM_DEPOSIT_BOX.toWorldArea();

        if (playerLocation == null)
        {
            return;
        }

        if (!depositArea.contains(playerLocation))
        {
            Microbot.status = "Walking to " + Areas.PORT_SARIM_DEPOSIT_BOX.getDisplayName();
            KspWalkerGuard.walkToDestination(
                    DEPOSIT_BOX_WALK_KEY,
                    Areas.PORT_SARIM_DEPOSIT_BOX::getRandomPoint,
                    depositArea::contains,
                    3,
                    WEB_WALK_COOLDOWN_MS);
            return;
        }

        KspWalkerGuard.clear(DEPOSIT_BOX_WALK_KEY);

        if (!Rs2DepositBox.openDepositBox())
        {
            debug("Could not open Port Sarim deposit box | player={}", playerLocation);
            return;
        }

        sleepUntil(Rs2DepositBox::isOpen, 2_000);
        if (!Rs2DepositBox.isOpen())
        {
            return;
        }

        String pickaxeToKeep = resolveInventoryPickaxeToKeep(miningLevel);
        List<String> itemsToKeep = pickaxeToKeep == null
                ? Collections.emptyList()
                : Collections.singletonList(pickaxeToKeep);

        Rs2DepositBox.depositAllExcept(itemsToKeep, false);
        sleepUntil(() -> !Rs2Inventory.isFull(), 2_000);
        debug("Deposited Rimmington gold ore | kept={} invFull={} depositBoxOpen={}",
                itemsToKeep,
                Rs2Inventory.isFull(),
                Rs2DepositBox.isOpen());

        if (Rs2DepositBox.isOpen())
        {
            Rs2DepositBox.closeDepositBox();
        }
    }

    private boolean closeBankIfOpen()
    {
        if (!Rs2Bank.isOpen())
        {
            return true;
        }

        Rs2Bank.closeBank();
        return sleepUntil(() -> !Rs2Bank.isOpen(), 2_000);
    }

    private String resolveInventoryPickaxeToKeep(int miningLevel)
    {
        String targetPickaxeName = resolveDesiredPickaxe(MiningReq.bestForMiningLevel(miningLevel));
        int targetIndex = PICKAXE_NAMES.indexOf(targetPickaxeName);

        if (targetIndex < 0)
        {
            return null;
        }

        for (int index = targetIndex; index >= 0; index--)
        {
            String pickaxeName = PICKAXE_NAMES.get(index);

            if (Rs2Equipment.isWearing(pickaxeName))
            {
                return null;
            }
        }

        for (int index = targetIndex; index >= 0; index--)
        {
            String pickaxeName = PICKAXE_NAMES.get(index);

            if (Rs2Inventory.hasItem(pickaxeName))
            {
                return pickaxeName;
            }
        }

        return null;
    }

    private boolean hasAnyPickaxeEquippedOrInInventory()
    {
        for (String pickaxeName : PICKAXE_NAMES)
        {
            if (Rs2Equipment.isWearing(pickaxeName) || Rs2Inventory.hasItem(pickaxeName))
            {
                return true;
            }
        }

        return false;
    }

    private void mineForCurrentLevel(int miningLevel)
    {
        // Check if player is under attack and handle combat state
        if (isPlayerUnderAttack())
        {
            lastUnderAttackAtMs = System.currentTimeMillis();
            KspTaskDebug.throttled(log, debugLogging, "Mining", "under-attack", 2_000L,
                    "player is under attack, waiting for combat to end | player={} interacting={}",
                    Rs2Player.getWorldLocation(),
                    Rs2Player.isInteracting());
            return;
        }

        // Reset combat timer if player is no longer under attack
        if (System.currentTimeMillis() - lastUnderAttackAtMs < 5_000)
        {
            debug("Waiting for player to recover from combat");
            return;
        }

        if (!isIdleInTargetArea())
        {
            KspTaskDebug.throttled(log, debugLogging, "Mining", "not-idle", 2_000L,
                    "waiting for idle before mining | player={} moving={} animating={} interacting={} area={}",
                    Rs2Player.getWorldLocation(),
                    Rs2Player.isMoving(),
                    Rs2Player.isAnimating(),
                    Rs2Player.isInteracting(),
                    targetArea.getDisplayName());
            return;
        }

        if (System.currentTimeMillis() - lastObjectInteractionAtMs < OBJECT_INTERACTION_COOLDOWN_MS)
        {
            KspTaskDebug.throttled(log, debugLogging, "Mining", "interaction-cooldown", 2_000L,
                    "interaction cooldown active | elapsed={}ms cooldown={}ms",
                    System.currentTimeMillis() - lastObjectInteractionAtMs,
                    OBJECT_INTERACTION_COOLDOWN_MS);
            return;
        }

        Rs2TileObjectModel targetRock = findNearestRockInTargetArea(miningLevel);

        if (targetRock == null)
        {
            return;
        }

        if (!isIdleInTargetArea())
        {
            debug("Rock candidate found but player stopped being idle | rock={} id={} loc={} moving={} animating={} interacting={}",
                    targetRock.getName(),
                    targetRock.getId(),
                    targetRock.getWorldLocation(),
                    Rs2Player.isMoving(),
                    Rs2Player.isAnimating(),
                    Rs2Player.isInteracting());
            return;
        }

        lastObjectInteractionAtMs = System.currentTimeMillis();

        Microbot.status = "Mining " + targetRock.getName();
        debug("Attempting rock interaction | objectName={} id={} loc={} reachable={} player={} distance={} hasMineAction={} insideArea={}",
                targetRock.getName(),
                targetRock.getId(),
                targetRock.getWorldLocation(),
                targetRock.isReachable(),
                Rs2Player.getWorldLocation(),
                Rs2Player.getWorldLocation() != null ? Rs2Player.getWorldLocation().distanceTo(targetRock.getWorldLocation()) : -1,
                hasObjectAction(targetRock, "Mine"),
                targetArea.toWorldArea().contains(targetRock.getWorldLocation()));
        boolean interactionStarted = targetRock.click("Mine");
        debug("Rock interaction result | clicked={} objectName={} id={} loc={} player={} moving={} animating={} interacting={}",
                interactionStarted,
                targetRock.getName(),
                targetRock.getId(),
                targetRock.getWorldLocation(),
                Rs2Player.getWorldLocation(),
                Rs2Player.isMoving(),
                Rs2Player.isAnimating(),
                Rs2Player.isInteracting());

        if (interactionStarted)
        {
            boolean activityStarted = sleepUntil(() -> Rs2Player.isAnimating() || Rs2Player.isInteracting(), 1_200);
            debug("Rock post-click wait | activityStarted={} moving={} animating={} interacting={} player={}",
                    activityStarted,
                    Rs2Player.isMoving(),
                    Rs2Player.isAnimating(),
                    Rs2Player.isInteracting(),
                    Rs2Player.getWorldLocation());
        }
        else
        {
            debug("Rock interaction was not accepted by tile object API | objectName={} id={} loc={}",
                    targetRock.getName(),
                    targetRock.getId(),
                    targetRock.getWorldLocation());
        }
    }

    private boolean isIdleInTargetArea()
    {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        return playerLocation != null
                && targetArea.toWorldArea().contains(playerLocation)
                && !Rs2Player.isMoving()
                && !Rs2Player.isAnimating()
                && !Rs2Player.isInteracting();
    }

    private boolean isPlayerUnderAttack()
    {
        // Check if the player is in combat with an NPC
        // The player is considered under attack if they are interacting but not animating a skill
        return Rs2Player.isInteracting() && !Rs2Player.isAnimating();
    }

    private Rs2TileObjectModel findNearestRockInTargetArea(int miningLevel)
    {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        WorldPoint searchCenter = getAreaCenter();

        if (playerLocation == null || searchCenter == null)
        {
            return null;
        }

        RockLevel targetRock = targetArea == Areas.TIN_COPPER_VARROCK_EAST
                ? getStarterRockToMine()
                : resolveTargetRock(miningLevel);

        Rs2TileObjectModel rock = findNearestMatchingRock(searchCenter, targetRock);
        if (rock == null)
        {
            Microbot.status = "No reachable " + (targetRock != null ? targetRock.getDisplayName() : "target") + " rock found";
            debug("No reachable {} rock found inside {}", targetRock != null ? targetRock.getDisplayName() : "target", targetArea.getDisplayName());
        }

        return rock;
    }

    private Rs2TileObjectModel findNearestMatchingRock(WorldPoint searchCenter, RockLevel targetRock)
    {
        int[] allowedObjectIds = getTargetRockIds(targetRock);
        if (targetRock == null)
        {
            return null;
        }

        int searchRadius = getAreaSearchRadius() + ROCK_SEARCH_PADDING_TILES;
        WorldArea miningArea = targetArea.toWorldArea();

        Rs2TileObjectModel inAreaRock = findNearestMatchingRock(searchCenter, searchRadius, targetRock, allowedObjectIds, miningArea, true);
        if (inAreaRock != null)
        {
            debug("Rock candidate selected | expected={} objectName={} id={} loc={} reachable={} insideArea={} searchCenter={} radius={}",
                    targetRock.getDisplayName(),
                    inAreaRock.getName(),
                    inAreaRock.getId(),
                    inAreaRock.getWorldLocation(),
                    inAreaRock.isReachable(),
                    miningArea.contains(inAreaRock.getWorldLocation()),
                    searchCenter,
                    searchRadius);
            return inAreaRock;
        }

        Rs2TileObjectModel nearbyRock = findNearestMatchingRock(searchCenter, searchRadius, targetRock, allowedObjectIds, miningArea, false);
        if (nearbyRock != null && isNearTargetArea(nearbyRock.getWorldLocation(), miningArea, OUT_OF_AREA_ROCK_FALLBACK_RADIUS))
        {
            debug("Using nearby {} rock at {} just outside {}", targetRock.getDisplayName(), nearbyRock.getWorldLocation(), targetArea.getDisplayName());
            return nearbyRock;
        }

        return null;
    }

    private Rs2TileObjectModel findNearestMatchingRock(
            WorldPoint searchCenter,
            int searchRadius,
            RockLevel targetRock,
            int[] allowedObjectIds,
            WorldArea miningArea,
            boolean requireInsideArea)
    {
        return Microbot.getRs2TileObjectCache().query()
                .fromWorldView()
                .within(searchCenter, searchRadius)
                .where(rock -> rock != null
                        && rock.getWorldLocation() != null
                        && (!requireInsideArea || miningArea.contains(rock.getWorldLocation()))
                        && isTargetRock(rock, targetRock, allowedObjectIds)
                        && rock.isReachable()
                        && hasObjectAction(rock, "Mine"))
                .nearestOnClientThread();
    }

    private static boolean hasObjectAction(Rs2TileObjectModel object, String expectedAction)
    {
        if (object == null || expectedAction == null)
        {
            return false;
        }

        ObjectComposition composition = object.getObjectComposition();
        if (composition == null || composition.getActions() == null)
        {
            return false;
        }

        for (String rawAction : composition.getActions())
        {
            if (rawAction == null)
            {
                continue;
            }

            String action = Rs2UiHelper.stripColTags(rawAction);
            if (expectedAction.equalsIgnoreCase(action))
            {
                return true;
            }
        }

        return false;
    }

    private boolean isTargetRock(Rs2TileObjectModel rock, RockLevel targetRock, int[] allowedObjectIds)
    {
        if (isAllowedObjectId(rock, allowedObjectIds))
        {
            return true;
        }

        String rockName = rock.getName();
        if (rockName == null || targetRock == null)
        {
            return false;
        }

        String normalizedName = rockName.toLowerCase(Locale.ENGLISH);
        String normalizedOre = targetRock.getDisplayName().toLowerCase(Locale.ENGLISH);
        return normalizedName.contains(normalizedOre) && normalizedName.contains("rock");
    }

    private boolean isAllowedObjectId(Rs2TileObjectModel rock, int[] allowedObjectIds)
    {
        if (allowedObjectIds == null || allowedObjectIds.length == 0)
        {
            return false;
        }

        for (int allowedObjectId : allowedObjectIds)
        {
            if (rock.getId() == allowedObjectId)
            {
                return true;
            }
        }

        return false;
    }

    private int[] getTargetRockIds(RockLevel targetRock)
    {
        if (targetRock == RockLevel.COPPER)
        {
            return COPPER_ROCK_IDS;
        }

        if (targetRock == RockLevel.TIN)
        {
            return TIN_ROCK_IDS;
        }

        if (targetRock == RockLevel.IRON)
        {
            return IRON_ROCK_IDS;
        }

        if (targetRock == RockLevel.SILVER)
        {
            return SILVER_ROCK_IDS;
        }

        if (targetRock == RockLevel.COAL)
        {
            return COAL_ROCK_IDS;
        }

        if (targetRock == RockLevel.GOLD)
        {
            return GOLD_ROCK_IDS;
        }

        return new int[0];
    }

    private boolean isNearTargetArea(WorldPoint point, WorldArea area, int radius)
    {
        if (point == null || area == null || point.getPlane() != area.getPlane())
        {
            return false;
        }

        int minX = area.getX() - radius;
        int maxX = area.getX() + area.getWidth() - 1 + radius;
        int minY = area.getY() - radius;
        int maxY = area.getY() + area.getHeight() - 1 + radius;

        return point.getX() >= minX
                && point.getX() <= maxX
                && point.getY() >= minY
                && point.getY() <= maxY;
    }

    private int getAreaSearchRadius()
    {
        int width = Math.abs(targetArea.getNorthEast().getX() - targetArea.getSouthWest().getX());
        int height = Math.abs(targetArea.getNorthEast().getY() - targetArea.getSouthWest().getY());

        return Math.max(width, height) + 2;
    }

    private Areas resolveTargetArea(int miningLevel)
    {
        RockLevel targetRock = resolveTargetRock(miningLevel);

        if (targetRock == RockLevel.COAL)
        {
            return Areas.COAL_BARBARIAN_VILLAGE;
        }

        if (targetRock == RockLevel.GOLD)
        {
            return Areas.GOLD_RIMMINGTON;
        }

        if (targetRock == RockLevel.SILVER)
        {
            return Areas.SILVER_VARROCK_WEST;
        }

        if (targetRock == RockLevel.IRON)
        {
            return Areas.IRON_VARROCK_EAST;
        }

        return Areas.TIN_COPPER_VARROCK_EAST;
    }

    private RockLevel resolveTargetRock(int miningLevel)
    {
        if (startingTargetRockInitialized && randomMidTierRock != null)
        {
            return randomMidTierRock;
        }

        if (miningLevel >= RockLevel.COAL.getRequiredMiningLevel())
        {
            return RockLevel.COAL;
        }

        if (miningLevel >= RockLevel.SILVER.getRequiredMiningLevel())
        {
            if (canMineSilverSafely())
            {
                return RockLevel.SILVER;
            }

            return canMineIronSafely() ? RockLevel.IRON : null;
        }

        if (miningLevel >= RockLevel.IRON.getRequiredMiningLevel())
        {
            return canMineIronSafely() ? RockLevel.IRON : null;
        }

        return null;
    }

    private boolean shouldRandomizeMidTierRock(int miningLevel)
    {
        return miningLevel >= RockLevel.COAL.getRequiredMiningLevel()
                && miningLevel < MID_TIER_RANDOM_MAX_LEVEL;
    }

    private void initializeStartingTargetRock(int miningLevel)
    {
        int selectionBand = getSelectionBand(miningLevel);
        if (startingTargetRockInitialized && initializedSelectionBand == selectionBand)
        {
            return;
        }

        if (shouldRandomizeMidTierRock(miningLevel))
        {
            List<RockLevel> randomOptions;
            if (canMineIronSafely() && canMineSilverSafely())
            {
                randomOptions = miningLevel >= RockLevel.GOLD.getRequiredMiningLevel()
                        ? Arrays.asList(RockLevel.IRON, RockLevel.SILVER, RockLevel.COAL, RockLevel.GOLD)
                        : Arrays.asList(RockLevel.IRON, RockLevel.SILVER, RockLevel.COAL);
            }
            else if (canMineIronSafely())
            {
                randomOptions = miningLevel >= RockLevel.GOLD.getRequiredMiningLevel()
                        ? Arrays.asList(RockLevel.IRON, RockLevel.COAL, RockLevel.GOLD)
                        : Arrays.asList(RockLevel.IRON, RockLevel.COAL);
            }
            else if (canMineSilverSafely())
            {
                randomOptions = miningLevel >= RockLevel.GOLD.getRequiredMiningLevel()
                        ? Arrays.asList(RockLevel.SILVER, RockLevel.COAL, RockLevel.GOLD)
                        : Arrays.asList(RockLevel.SILVER, RockLevel.COAL);
            }
            else
            {
                randomOptions = miningLevel >= RockLevel.GOLD.getRequiredMiningLevel()
                        ? Arrays.asList(RockLevel.COAL, RockLevel.GOLD)
                        : Arrays.asList(RockLevel.COAL);
            }

            randomMidTierRock = randomOptions.get(ThreadLocalRandom.current().nextInt(randomOptions.size()));

            debug("Selected starting mining ore {} for mining level {}", randomMidTierRock.getDisplayName(), miningLevel);
        }
        else
        {
            randomMidTierRock = null;
        }

        startingTargetRockInitialized = true;
        initializedSelectionBand = selectionBand;
    }

    private int getSelectionBand(int miningLevel)
    {
        if (!shouldRandomizeMidTierRock(miningLevel))
        {
            return 0;
        }

        return miningLevel >= RockLevel.GOLD.getRequiredMiningLevel() ? 2 : 1;
    }

    private boolean canMineSilverSafely()
    {
        int combatLevel = getCombatLevel();
        boolean safe = combatLevel >= SILVER_SAFE_COMBAT_LEVEL;

        if (!safe)
        {
            KspTaskDebug.throttled(log, debugLogging, "Mining", "silver-mugger-guard", 10_000L,
                    "skipping silver until Mugger becomes passive | requiredCombat={} currentCombat={} muggerCombat={}",
                    SILVER_SAFE_COMBAT_LEVEL,
                    combatLevel,
                    MUGGER_COMBAT_LEVEL);
        }

        return safe;
    }

    private boolean canMineIronSafely()
    {
        int combatLevel = getCombatLevel();
        boolean safe = combatLevel >= IRON_SAFE_COMBAT_LEVEL;

        if (!safe)
        {
            KspTaskDebug.throttled(log, debugLogging, "Mining", "iron-rat-guard", 10_000L,
                    "skipping iron until Rat becomes passive | requiredCombat={} currentCombat={} ratCombat={}",
                    IRON_SAFE_COMBAT_LEVEL,
                    combatLevel,
                    RAT_COMBAT_LEVEL);
        }

        return safe;
    }

    private int getCombatLevel()
    {
        if (Microbot.getClient() == null || Microbot.getClient().getLocalPlayer() == null)
        {
            return 0;
        }

        return Microbot.getClient().getLocalPlayer().getCombatLevel();
    }

    private RockLevel getStarterRockToMine()
    {
        int tinCount = getOwnedOreCount(TIN_ORE_NAME);
        int copperCount = getOwnedOreCount(COPPER_ORE_NAME);

        if (copperCount < tinCount)
        {
            return RockLevel.COPPER;
        }

        return RockLevel.TIN;
    }

    private int getOwnedOreCount(String oreName)
    {
        return Rs2Inventory.count(oreName) + Math.max(0, Rs2Bank.count(oreName));
    }

    private void debug(String message, Object... args)
    {
        if (debugLogging)
        {
            KspTaskDebug.info(log, true, "Mining", message, args);
        }
    }

    @Override
    public void shutdown()
    {
        startingTargetRockInitialized = false;
        initializedSelectionBand = -1;
        randomMidTierRock = null;
        walkingToTargetArea = false;
        KspWalkerGuard.clear("Mining:target-area");
        KspWalkerGuard.clear(BANK_WALK_KEY);
        KspWalkerGuard.clear(DEPOSIT_BOX_WALK_KEY);
        lastWebWalkAtMs = 0L;
        lastObjectInteractionAtMs = 0L;
        lastUnderAttackAtMs = 0L;

        super.shutdown();
    }

    public Areas getTargetArea()
    {
        return targetArea;
    }
}
