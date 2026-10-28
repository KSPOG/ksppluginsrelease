package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.fishing.fishingscript;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;

import net.runelite.client.plugins.microbot.kspaccountbuilder.KspAccountBuilderPauseGate;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspManagedScript;

import java.awt.event.KeyEvent;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.inject.Singleton;
import net.runelite.api.ItemID;
import net.runelite.api.NPCComposition;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspBankMode;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspTaskDebug;

import net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.KspBankWidgetHelper;
import net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.KspLiveScene;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.fishing.areas.Areas;

import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.fishing.levelreqfishing.LevelReqs;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.fishing.needed.Inventory;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.depositbox.Rs2DepositBox;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class FishingScript extends KspManagedScript
{
    private static final Logger log = LoggerFactory.getLogger(FishingScript.class);

    private static final int LOOP_DELAY_MS = 100;
    private static final int WEB_WALK_COOLDOWN_MS = 1_000;
    private static final int NPC_INTERACTION_COOLDOWN_MS = 100;
    private static final int NPC_INTERACTION_START_TIMEOUT_MS = 1_500;
    private static final int FISHING_SPOT_SEARCH_PADDING_TILES = 8;
    private static final int OUT_OF_AREA_SPOT_FALLBACK_RADIUS = 4;
    private static final int FISHING_SPOT_INTERACTION_DISTANCE = 8;
    private static final int MIN_KARAMJA_COINS = 60;
    private static final int KARAMJA_COIN_RESERVE = 2_000;
    private static final int TROUT_SALMON_FIRE_ID = 43475;
    private static final int NO_COOKING_BATCH = -1;
    private static final String WALK_KEY_TO_FISHING_AREA = "Fishing:target-area";
    private static final String WALK_KEY_TO_TROUT_SALMON_FIRE = "Fishing:trout-salmon-fire";
    private static final long COOKING_WIDGET_ACTION_TIMEOUT_MS = 1_500L;
    private static final long ACTION_DISPATCH_TIMEOUT_MS = 1_500L;
    private static final long COOKING_RECOVERY_TIMEOUT_MS = 25_000L;
    private static final WorldPoint TROUT_SALMON_WALK_POSITION = new WorldPoint(3104, 3431, 0);
    private static final WorldPoint TROUT_SALMON_FIRE_POSITION = new WorldPoint(3106, 3432, 0);
    private static final WorldPoint TROUT_SALMON_FIRE_APPROACH = new WorldPoint(3109, 3432, 0);

    private volatile Areas targetArea = Areas.SHRIMP_ANCHOVIES;
    private LevelReqs targetFish = LevelReqs.SHRIMP;
    private LevelReqs randomLevel50Fish;
    private FishingState state = FishingState.WAITING;
    private boolean debugLogging;
    private boolean progressiveFishing = true;
    private volatile boolean walkingToTargetArea;
    private volatile boolean targetAreaArrivalHandled;
    private int cookingBatchItemId = NO_COOKING_BATCH;
    private boolean expectingCookingXpDrop;
    private long lastNpcInteractionAtMs;
    private long pendingNpcInteractionAtMs;
    private long lastWebWalkAtMs;
    private long pendingCookingWidgetActionAtMs;
    private long pendingCookingFireActionAtMs;
    private long cookingStartedAtMs;
    private boolean skipCookingUntilBanked;
    private long pendingBankActionAtMs;
    private int pendingBankEmptySlots = -1;

    public void setDebugLogging(boolean debugLogging) { this.debugLogging = debugLogging; }

    public boolean run(Areas area) { return run(area, true); }

    public boolean run(Areas area, boolean progressiveFishing)
    {
        shutdown();

        targetArea = area;
        this.progressiveFishing = progressiveFishing;
        targetFish = progressiveFishing ? LevelReqs.SHRIMP : resolveFishForArea(area);
        randomLevel50Fish = null;
        state = FishingState.CHECKING_SUPPLIES;
        targetAreaArrivalHandled = false;
        cookingBatchItemId = NO_COOKING_BATCH;
        expectingCookingXpDrop = false;
        pendingCookingWidgetActionAtMs = 0L;
        pendingCookingFireActionAtMs = 0L;
        cookingStartedAtMs = 0L;
        skipCookingUntilBanked = false;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            if (KspAccountBuilderPauseGate.isPaused() || !super.run() || !Microbot.isLoggedIn())
            {
                return;
            }

            int fishingLevel = Microbot.getClient().getRealSkillLevel(Skill.FISHING);
            LevelReqs desiredFish = progressiveFishing ? resolveFishForLevel(fishingLevel) : targetFish;
            Areas desiredArea = progressiveFishing ? resolveTargetArea(desiredFish) : targetArea;

            if (desiredFish != targetFish || desiredArea != targetArea)
            {
                targetFish = desiredFish;
                targetArea = desiredArea;
                targetAreaArrivalHandled = false;
                clearTargetAreaWalkIfNeeded();
                debug("Switching fishing target to {} in {} for fishing level {}",
                        targetFish.getDisplayName(),
                        targetArea.getDisplayName(),
                        fishingLevel);
            }

            KspTaskDebug.throttled(log, debugLogging, "Fishing", "loop", 5_000L,
                    "loop | level={} state={} area={} targetFish={} player={} moving={} animating={} interacting={} invFull={} bankOpen={} walkerTarget={}",
                    fishingLevel,
                    state,
                    targetArea.getDisplayName(),
                    targetFish.getDisplayName(),
                    Rs2Player.getWorldLocation(),
                    Rs2Player.isMoving(),
                    Rs2Player.isAnimating(),
                    Rs2Player.isInteracting(),
                    Rs2Inventory.isFull(),
                    Rs2Bank.isOpen(),
                    Rs2Walker.getCurrentTarget());

            if (Rs2Inventory.isFull())
            {
                // Stop any prior fishing route before attempting cooking/banking.
                Rs2Walker.clearWalkingRoute("fishing_inventory_full");
                if (!skipCookingUntilBanked && cookTroutOrSalmonBeforeBanking())
                {
                    state = FishingState.COOKING;
                    return;
                }

                state = FishingState.BANKING;
                bankFishOnly();
                return;
            }

            // Full-inventory recovery is complete once a free slot is visible.
            skipCookingUntilBanked = false;
            cookingStartedAtMs = 0L;

            if (!hasRequiredSupplies(targetFish))
            {
                state = FishingState.CHECKING_SUPPLIES;
                prepareRequiredSupplies(targetFish);
                return;
            }

            if (Rs2Bank.isOpen())
            {
                Rs2Bank.closeBank();
                return;
            }

            if (!ensureInTargetArea())
            {
                state = FishingState.WALKING_TO_AREA;
                return;
            }

            if (!isIdleInTargetArea())
            {
                state = FishingState.WAITING;
                return;
            }

            state = FishingState.FISHING;
            fishCurrentTarget();
        }, 0L, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);

        return true;
    }

    private LevelReqs resolveFishForArea(Areas area)
    {
        if (area == Areas.SARDINE_HERRING)
        {
            return LevelReqs.HERRING;
        }
        if (area == Areas.TROUT_SALMON)
        {
            return LevelReqs.SALMON;
        }
        if (area == Areas.KARAMJA)
        {
            return LevelReqs.SWORDFISH;
        }
        return LevelReqs.SHRIMP;
    }

    private LevelReqs resolveFishForLevel(int fishingLevel)
    {
        if (fishingLevel < 50)
        {
            randomLevel50Fish = null;
            return LevelReqs.bestForFishingLevel(fishingLevel);
        }

        LevelReqs preferredFish = selectPreferredLevel50Fish();
        if (preferredFish != randomLevel50Fish)
        {
            randomLevel50Fish = preferredFish;
            debug("Selected level 50 fishing mode | mode={} targetFish={}",
                    getFishingModeName(randomLevel50Fish),
                    randomLevel50Fish.getDisplayName());
        }

        return randomLevel50Fish;
    }

    private LevelReqs selectPreferredLevel50Fish()
    {
        if (hasAvailableItem("Harpoon") && hasAvailableKaramjaFare())
        {
            return LevelReqs.SWORDFISH;
        }

        if (hasAvailableItem("Lobster pot") && hasAvailableKaramjaFare())
        {
            return LevelReqs.LOBSTER;
        }

        return LevelReqs.SALMON;
    }

    private boolean hasAvailableItem(String itemName) { return Rs2Inventory.hasItem(itemName) || Rs2Bank.count(itemName) > 0; }

    private boolean hasAvailableKaramjaFare()
    {
        return Rs2Inventory.itemQuantity(ItemID.COINS_995) + Math.max(0, Rs2Bank.count(ItemID.COINS_995))
                >= MIN_KARAMJA_COINS;
    }

    private String getFishingModeName(LevelReqs fish)
    {
        if (fish == LevelReqs.SALMON)
        {
            return "Trout/Salmon";
        }
        if (fish == LevelReqs.SWORDFISH)
        {
            return "Tuna/Swordfish";
        }
        return "Lobster";
    }

    private boolean cookTroutOrSalmonBeforeBanking()
    {
        if (targetArea != Areas.TROUT_SALMON) {
            resetCookingBatch();
            return false;
        }

        if (cookingBatchItemId == NO_COOKING_BATCH) {
            cookingBatchItemId = selectCookingBatchItem();
            if (cookingBatchItemId == NO_COOKING_BATCH) return false;
        }

        if (Rs2Inventory.count(cookingBatchItemId) <= 0) {
            cookingBatchItemId = selectCookingBatchItem();
            expectingCookingXpDrop = false;
            if (cookingBatchItemId == NO_COOKING_BATCH) return false;
        }

        if (cookingStartedAtMs == 0L)
        {
            cookingStartedAtMs = System.currentTimeMillis();
            KspTaskDebug.throttled(log, true, "Fishing", "full-inventory-cook", 5_000L,
                    "Full inventory: starting trout/salmon cooking | player={} raw={} emptySlots={} fire={}",
                    Rs2Player.getWorldLocation(), Rs2Inventory.count(cookingBatchItemId),
                    Rs2Inventory.emptySlotCount(), TROUT_SALMON_FIRE_POSITION);
        }
        // A fire can be visible but its tile is not walkable. Never let this
        // optional cooking step hold the full-inventory bank transition forever.
        if (!Rs2Player.isAnimating()
                && System.currentTimeMillis() - cookingStartedAtMs >= COOKING_RECOVERY_TIMEOUT_MS)
        {
            KspTaskDebug.throttled(log, true, "Fishing", "cooking-timeout-bank", 5_000L,
                    "Cooking not progressing; banking full inventory instead | player={} raw={} emptySlots={} walkerTarget={}",
                    Rs2Player.getWorldLocation(), Rs2Inventory.count(cookingBatchItemId),
                    Rs2Inventory.emptySlotCount(), Rs2Walker.getCurrentTarget());
            skipCookingUntilBanked = true;
            resetCookingBatch();
            return false;
        }

        Microbot.status = "Cooking " + getCookingBatchName() + " before banking";

        if (isCookingWidgetOpen())
        {
            pendingCookingFireActionAtMs = 0L;
            if (Rs2Player.isAnimating())
            {
                pendingCookingWidgetActionAtMs = 0L;
                return true;
            }

            if (!isCookingWidgetActionPending())
            {
                Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                pendingCookingWidgetActionAtMs = System.currentTimeMillis();
                expectingCookingXpDrop = true;
            }
            return true;
        }

        if (isCookingWidgetActionPending()) return true;

        if (Rs2Player.isAnimating())
        {
            pendingCookingWidgetActionAtMs = 0L;
            return true;
        }

        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null) return true;

        Rs2TileObjectModel fire = KspLiveScene.nearestModel(
                player, 30, object -> object.getId() == TROUT_SALMON_FIRE_ID);
        if (fire == null) {
            pendingCookingFireActionAtMs = 0L;
            resetCookingBatch();
            return false;
        }

        // The fire occupies a sealed/non-walkable tile. Walk to a proven
        // accessible adjacent tile, not the object position itself. From the
        // reachable rim, use the raw fish on the fire directly.
        WorldPoint approach = TROUT_SALMON_FIRE_POSITION.equals(fire.getWorldLocation())
                ? TROUT_SALMON_FIRE_APPROACH : fire.getWorldLocation();
        if (player.distanceTo(approach) > 1)
        {
            Rs2Walker.walkTo(approach, 1);
            return true;
        }

        if (pendingCookingFireActionAtMs > 0L
                && System.currentTimeMillis() - pendingCookingFireActionAtMs < ACTION_DISPATCH_TIMEOUT_MS) {
            return true;
        }

        expectingCookingXpDrop = Rs2Inventory.useItemOnObject(cookingBatchItemId, fire.getId());
        if (expectingCookingXpDrop) pendingCookingFireActionAtMs = System.currentTimeMillis();
        return true;
    }

    private boolean isCookingWidgetOpen()
    {
        return Rs2Widget.isProductionWidgetOpen()
                || Rs2Widget.findWidget("How many would you like to cook?", null, false) != null;
    }

    private boolean isCookingWidgetActionPending()
    {
        if (pendingCookingWidgetActionAtMs == 0L) return false;

        if (Rs2Player.isAnimating())
        {
            pendingCookingWidgetActionAtMs = 0L;
            return false;
        }

        if (System.currentTimeMillis() - pendingCookingWidgetActionAtMs >= COOKING_WIDGET_ACTION_TIMEOUT_MS)
        {
            pendingCookingWidgetActionAtMs = 0L;
            return false;
        }

        return true;
    }

    private int selectCookingBatchItem()
    {
        int cookingLevel = Microbot.getClient().getRealSkillLevel(Skill.COOKING);
        if (cookingLevel >= 25 && Rs2Inventory.count(ItemID.RAW_SALMON) > 0)
        {
            return ItemID.RAW_SALMON;
        }
        if (cookingLevel >= 15 && Rs2Inventory.count(ItemID.RAW_TROUT) > 0)
        {
            return ItemID.RAW_TROUT;
        }
        return NO_COOKING_BATCH;
    }

    private String getCookingBatchName() { return cookingBatchItemId == ItemID.RAW_SALMON ? "salmon" : "trout"; }

    private void resetCookingBatch()
    {
        cookingBatchItemId = NO_COOKING_BATCH;
        expectingCookingXpDrop = false;
        pendingCookingFireActionAtMs = 0L;
    }

    private void fishCurrentTarget()
    {
        long now = System.currentTimeMillis();
        if (!canStartFishingInTargetArea(now)) return;
        if (now - lastNpcInteractionAtMs < NPC_INTERACTION_COOLDOWN_MS) return;

        FishingTarget target = FishingTarget.fromLevelReq(targetFish);
        Rs2NpcModel spot = findNearestFishingSpot(target);
        if (spot == null) {
            Microbot.status = "No reachable fishing spot found";
            return;
        }

        String action = getAvailableAction(spot, target.getActions());
        if (action.isEmpty()) return;

        lastNpcInteractionAtMs = now;
        Microbot.status = "Fishing " + targetFish.getDisplayName();
        if (spot.click(action)) {
            pendingNpcInteractionAtMs = now;
            debug("Fishing interaction dispatched | fish={} action={} spot={} loc={}",
                    targetFish.getDisplayName(), action, spot.getId(), spot.getWorldLocation());
        } else {
            pendingNpcInteractionAtMs = 0L;
            debug("Fishing click rejected | fish={} action={} spot={} loc={}",
                    targetFish.getDisplayName(), action, spot.getId(), spot.getWorldLocation());
        }
    }

    private boolean ensureInTargetArea()
    {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();

        if (playerLocation == null)
        {
            debug("Cannot walk to fishing area; player location is null");
            return false;
        }

        WorldArea area = targetArea.toWorldArea();
        if (area.contains(playerLocation))
        {
            clearTargetAreaWalkIfNeeded();
            return true;
        }

        if (requiresKaramjaTravel())
        {
            Microbot.status = "Traveling via Port Sarim to Karamja";
            return travelToKaramjaFishingSpot();
        }

        Microbot.status = "Walking to " + targetArea.getDisplayName();
        if (Rs2Walker.walkUntil(this.getTargetAreaWalkPoint(), 3, () -> area.contains(Rs2Player.getWorldLocation())))
        {
            lastWebWalkAtMs = System.currentTimeMillis();
            walkingToTargetArea = true;
            debug("Requested fishing area walk | player={} walkerTarget={} area={}",
                    playerLocation,
                    Rs2Walker.getCurrentTarget(),
                    targetArea.getDisplayName());
        }

        return false;
    }

    private WorldPoint getTargetAreaWalkPoint()
    {
        if (targetArea == Areas.TROUT_SALMON)
        {
            return TROUT_SALMON_WALK_POSITION;
        }

        return targetArea.getRandomPoint();
    }

    private void clearTargetAreaWalkIfNeeded() { stopWalkerIfInsideTargetArea(); }

    public void stopWalkerIfInsideTargetArea()
    {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null || !targetArea.contains(playerLocation))
        {
            targetAreaArrivalHandled = false;
            return;
        }

        if (targetAreaArrivalHandled)
        {
            return;
        }

        WorldPoint walkerTarget = Rs2Walker.getCurrentTarget();
        if (walkerTarget != null && targetArea.getArea().contains(walkerTarget))
        {
            Rs2Walker.clearWalkingRoute("ksp_account_builder_fishing_reached_area");
        }
        debug("Cleared fishing walker route because player is inside task area | player={} area={} oldWalkerTarget={}",
                playerLocation,
                targetArea.getDisplayName(),
                walkerTarget);

        targetAreaArrivalHandled = true;
        walkingToTargetArea = false;
        lastWebWalkAtMs = 0L;
    }

    private boolean prepareRequiredSupplies(LevelReqs fish)
    {
        if (!ensureInventoryTabOpen()) return false;

        if (!Rs2Bank.isOpen())
        {
            if (Rs2Bank.openBank() || Rs2Bank.walkToBankAndUseBank()) {
                debug("Opening bank for fishing supplies | fish={}", fish.getDisplayName());
            }
            return false;
        }

        if (isBankInventoryActionPending()) return false;

        if (KspBankWidgetHelper.closeBankTutorialOverlayIfOpen()) return false;
        if (!KspBankMode.ensureWithdrawAsItem()) return false;

        List<String> required = getRequiredItems(fish);
        int beforeDeposit = Rs2Inventory.emptySlotCount();
        if (Rs2Bank.depositAllExcept(required.toArray(new String[0]))) {
            markBankInventoryAction(beforeDeposit);
            return false;
        }

        for (String item : required)
        {
            if (isCoins(item))
            {
                ensureKaramjaCoins();
                if (Rs2Inventory.itemQuantity(ItemID.COINS_995) < MIN_KARAMJA_COINS) return false;
                continue;
            }

            if (isConsumable(item))
            {
                if (Rs2Inventory.count(item) <= 0 && Rs2Bank.count(item) > 0) {
                    int before = Rs2Inventory.emptySlotCount();
                    if (Rs2Bank.withdrawAll(item)) markBankInventoryAction(before);
                    return false;
                }
                continue;
            }

            if (!Rs2Inventory.hasItem(item) && Rs2Bank.count(item) > 0) {
                int before = Rs2Inventory.emptySlotCount();
                if (Rs2Bank.withdrawOne(item)) markBankInventoryAction(before);
                return false;
            }
        }

        if (!hasRequiredSupplies(fish)) return false;
        Rs2Bank.closeBank();
        return false;
    }

    private void bankFishOnly()
    {
        resetCookingBatch();
        if (!ensureInventoryTabOpen()) return;

        if (requiresKaramjaTravel()) {
            depositKaramjaFishOnly();
            return;
        }

        if (!Rs2Bank.isOpen()) {
            Rs2Bank.openBank();
            if (!Rs2Bank.isOpen()) Rs2Bank.walkToBankAndUseBank();
            return;
        }

        if (KspBankWidgetHelper.closeBankTutorialOverlayIfOpen()) return;

        if (isBankInventoryActionPending()) return;
        List<String> required = getRequiredItems(targetFish);
        int before = Rs2Inventory.emptySlotCount();
        if (Rs2Bank.depositAllExcept(required.toArray(new String[0]))) {
            markBankInventoryAction(before);
            return;
        }
        if (!Rs2Inventory.isFull()) Rs2Bank.closeBank();
    }

    private void depositKaramjaFishOnly()
    {
        if (!returnToPortSarimDepositPoint()) return;

        if (!Rs2DepositBox.isOpen()) {
            Rs2DepositBox.openDepositBox();
            return;
        }

        if (isBankInventoryActionPending()) return;
        List<String> required = getRequiredItems(targetFish);
        int before = Rs2Inventory.emptySlotCount();
        if (Rs2DepositBox.depositAllExcept(required, false)) {
            markBankInventoryAction(before);
            return;
        }
        if (!Rs2Inventory.isFull()) Rs2DepositBox.closeDepositBox();
    }

    private boolean isBankInventoryActionPending()
    {
        if (pendingBankActionAtMs == 0L) return false;
        if (Rs2Inventory.emptySlotCount() != pendingBankEmptySlots) {
            pendingBankActionAtMs = 0L;
            pendingBankEmptySlots = -1;
            return false;
        }
        if (System.currentTimeMillis() - pendingBankActionAtMs < ACTION_DISPATCH_TIMEOUT_MS) return true;
        pendingBankActionAtMs = 0L;
        pendingBankEmptySlots = -1;
        return false;
    }

    private void markBankInventoryAction(int beforeEmptySlots)
    {
        pendingBankEmptySlots = beforeEmptySlots;
        pendingBankActionAtMs = System.currentTimeMillis();
    }

    private boolean hasRequiredSupplies(LevelReqs fish)
    {
        for (String itemName : getRequiredItems(fish))
        {
            if (isCoins(itemName))
            {
                if (Rs2Inventory.itemQuantity(ItemID.COINS_995) < MIN_KARAMJA_COINS)
                {
                    return false;
                }
                continue;
            }

            if (isConsumable(itemName))
            {
                if (Rs2Inventory.count(itemName) <= 0)
                {
                    return false;
                }
                continue;
            }

            if (!Rs2Inventory.hasItem(itemName))
            {
                return false;
            }
        }

        return true;
    }

    private Rs2NpcModel findNearestFishingSpot(FishingTarget target)
    {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        WorldPoint searchCenter = getAreaCenter();

        if (playerLocation == null || searchCenter == null || target == null)
        {
            return null;
        }

        int searchRadius = getAreaSearchRadius() + FISHING_SPOT_SEARCH_PADDING_TILES;
        WorldArea fishingArea = targetArea.toWorldArea();

        Rs2NpcModel inAreaSpot = findNearestFishingSpot(target, searchCenter, searchRadius, fishingArea, true);
        if (inAreaSpot != null)
        {
            return inAreaSpot;
        }

        return findNearestFishingSpot(target, searchCenter, searchRadius, fishingArea, false);
    }

    private Rs2NpcModel findNearestFishingSpot(
            FishingTarget target,
            WorldPoint searchCenter,
            int searchRadius,
            WorldArea fishingArea,
            boolean requireInsideArea)
    {
        return KspLiveScene.nearestNpcModel(
                searchCenter, searchRadius,
                candidate -> candidate != null
                        && candidate.getWorldLocation() != null
                        && isFishingSpotName(candidate)
                        && (requireInsideArea
                        ? fishingArea.contains(candidate.getWorldLocation())
                        : isNearTargetArea(candidate.getWorldLocation(), OUT_OF_AREA_SPOT_FALLBACK_RADIUS))
                        && isInteractableFishingSpot(candidate)
                        && !getAvailableAction(candidate, target.getActions()).isEmpty());
    }

    private boolean isFishingSpotName(Rs2NpcModel candidate)
    {
        if (candidate == null || candidate.getName() == null)
        {
            return false;
        }

        String name = Rs2UiHelper.stripColTags(candidate.getName()).trim();
        return "Fishing spot".equalsIgnoreCase(name)
                || "Rod Fishing spot".equalsIgnoreCase(name);
    }

    private boolean isInteractableFishingSpot(Rs2NpcModel candidate)
    {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        WorldPoint spotLocation = candidate != null ? candidate.getWorldLocation() : null;

        return playerLocation != null
                && spotLocation != null
                && playerLocation.getPlane() == spotLocation.getPlane()
                && (candidate.isReachable() || playerLocation.distanceTo(spotLocation) <= FISHING_SPOT_INTERACTION_DISTANCE);
    }

    private String getAvailableAction(Rs2NpcModel npc, List<String> preferredActions)
    {
        if (npc == null || preferredActions == null || preferredActions.isEmpty())
        {
            return "";
        }

        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            NPCComposition composition = Microbot.getClient().getNpcDefinition(npc.getId());
            if (composition == null || composition.getActions() == null)
            {
                return "";
            }

            for (String preferredAction : preferredActions)
            {
                for (String rawAction : composition.getActions())
                {
                    if (rawAction == null)
                    {
                        continue;
                    }

                    String action = Rs2UiHelper.stripColTags(rawAction);
                    if (preferredAction.equalsIgnoreCase(action))
                    {
                        return action;
                    }
                }
            }

            return "";
        }).orElse("");
    }

    private boolean isIdleInTargetArea()
    {
        WorldPoint player = Rs2Player.getWorldLocation();
        return player != null
                && targetArea.contains(player)
                && !Rs2Player.isMoving()
                && !Rs2Player.isAnimating()
                && !Rs2Player.isInteracting();
    }

    private boolean canStartFishingInTargetArea(long now)
    {
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null || !targetArea.contains(player)) return false;

        if (Rs2Player.isMoving() || Rs2Player.isAnimating() || Rs2Player.isInteracting())
        {
            pendingNpcInteractionAtMs = 0L;
            return false;
        }

        if (pendingNpcInteractionAtMs > 0L)
        {
            if (now - pendingNpcInteractionAtMs < NPC_INTERACTION_START_TIMEOUT_MS) return false;
            pendingNpcInteractionAtMs = 0L;
        }

        return true;
    }

    private Areas resolveTargetArea(LevelReqs fish)
    {
        if (fish == LevelReqs.TUNA || fish == LevelReqs.LOBSTER || fish == LevelReqs.SWORDFISH)
        {
            return Areas.KARAMJA;
        }

        if (fish == LevelReqs.TROUT || fish == LevelReqs.SALMON)
        {
            return Areas.TROUT_SALMON;
        }

        if (fish == LevelReqs.SARDINE || fish == LevelReqs.HERRING)
        {
            return Areas.SARDINE_HERRING;
        }

        return Areas.SHRIMP_ANCHOVIES;
    }

    private boolean requiresKaramjaTravel()
    {
        return targetFish == LevelReqs.TUNA
                || targetFish == LevelReqs.LOBSTER
                || targetFish == LevelReqs.SWORDFISH;
    }

    private List<String> getRequiredItems(LevelReqs fish)
    {
        Inventory inventory = Inventory.valueOf(fish.name());
        return inventory.getRequiredItems();
    }

    private boolean isConsumable(String itemName)
    {
        return "Fishing bait".equalsIgnoreCase(itemName)
                || "Feather".equalsIgnoreCase(itemName);
    }

    private boolean isCoins(String itemName) { return "Coins".equalsIgnoreCase(itemName); }

    private void ensureKaramjaCoins()
    {
        int current = Rs2Inventory.itemQuantity(ItemID.COINS_995);
        if (current >= MIN_KARAMJA_COINS) return;

        int bank = Math.max(0, Rs2Bank.count(ItemID.COINS_995));
        int amount = Math.min(KARAMJA_COIN_RESERVE - current, bank);
        if (amount > 0 && !isBankInventoryActionPending()) {
            int before = Rs2Inventory.emptySlotCount();
            if (Rs2Bank.withdrawX(true, ItemID.COINS_995, amount)) markBankInventoryAction(before);
        }
    }

    private WorldPoint getAreaCenter()
    {
        int centerX = (targetArea.getSouthWest().getX() + targetArea.getNorthEast().getX()) / 2;
        int centerY = (targetArea.getSouthWest().getY() + targetArea.getNorthEast().getY()) / 2;
        int plane = targetArea.getSouthWest().getPlane();

        return new WorldPoint(centerX, centerY, plane);
    }

    private int getAreaSearchRadius()
    {
        return Math.max(
                Math.abs(targetArea.getNorthEast().getX() - targetArea.getSouthWest().getX()),
                Math.abs(targetArea.getNorthEast().getY() - targetArea.getSouthWest().getY())
        ) + 1;
    }

    private boolean isNearTargetArea(WorldPoint point, int radius)
    {
        if (point == null || point.getPlane() != targetArea.getSouthWest().getPlane())
        {
            return false;
        }

        if (targetArea.contains(point))
        {
            return true;
        }

        int minX = targetArea.getSouthWest().getX() - radius;
        int maxX = targetArea.getNorthEast().getX() + radius;
        int minY = targetArea.getSouthWest().getY() - radius;
        int maxY = targetArea.getNorthEast().getY() + radius;

        return point.getX() >= minX
                && point.getX() <= maxX
                && point.getY() >= minY
                && point.getY() <= maxY;
    }

    private boolean ensureInventoryTabOpen()
    {
        if (Rs2Tab.getCurrentTab() == InterfaceTab.INVENTORY) return true;
        Rs2Tab.switchTo(InterfaceTab.INVENTORY);
        return false;
    }

    private void debug(String message, Object... args)
    {
        if (debugLogging)
        {
            KspTaskDebug.info(log, true, "Fishing", message, args);
        }
    }

    @Override
    public void shutdown()
    {
        resetCookingBatch();
        randomLevel50Fish = null;
        walkingToTargetArea = false;
        targetAreaArrivalHandled = false;
        lastNpcInteractionAtMs = 0L;
        pendingNpcInteractionAtMs = 0L;
        pendingCookingFireActionAtMs = 0L;
        cookingStartedAtMs = 0L;
        skipCookingUntilBanked = false;
        pendingBankActionAtMs = 0L;
        pendingBankEmptySlots = -1;
        lastWebWalkAtMs = 0L;
        state = FishingState.WAITING;
        super.shutdown();
    }

    public Areas getTargetArea() { return targetArea; }

    private enum FishingTarget
    {
        SHRIMP(LevelReqs.SHRIMP, List.of("Net", "Small net")),
        SARDINE(LevelReqs.SARDINE, List.of("Bait")),
        HERRING(LevelReqs.HERRING, List.of("Bait")),
        TROUT(LevelReqs.TROUT, List.of("Lure", "Bait")),
        SALMON(LevelReqs.SALMON, List.of("Lure", "Bait")),
        TUNA(LevelReqs.TUNA, List.of("Harpoon")),
        LOBSTER(LevelReqs.LOBSTER, List.of("Cage")),
        SWORDFISH(LevelReqs.SWORDFISH, List.of("Harpoon"));

        private final LevelReqs levelReq;
        private final List<String> actions;

        FishingTarget(LevelReqs levelReq, List<String> actions)
        {
            this.levelReq = levelReq;
            this.actions = actions;
        }

        private static FishingTarget fromLevelReq(LevelReqs levelReq)
        {
            for (FishingTarget target : values())
            {
                if (target.levelReq == levelReq)
                {
                    return target;
                }
            }

            return SHRIMP;
        }

        private List<String> getActions() { return actions; }
    }
    // Karamja ferry-specific NPC/dialogue actions remain in FishingScript.
    // All land navigation is delegated to native Rs2Walker.

    private static final List<String> PORT_SARIM_TRAVEL_NPCS = List.of(
            "Seaman Thresnor",
            "Seaman Lorris",
            "Captain Tobias"
    );
    private static final List<String> CUSTOMS_OFFICER_NPCS = List.of("Customs Officer");
    private static final List<String> TRAVEL_ACTIONS = List.of(
            "Musa Point",
            "Port Sarim",
            "Pay-fare",
            "Travel",
            "Talk-to"
    );

    // Force the script to walk to the Port Sarim customs ship first.
    // Do not path directly to Musa Point from the mainland, otherwise WebWalker may choose
    // unrelated transports such as the Rimmington ship network.
    private static final WorldPoint PORT_SARIM_TRAVEL_POINT = new WorldPoint(3029, 3217, 0);
    private static final WorldPoint PORT_SARIM_DEPOSIT_POINT = new WorldPoint(3047, 3236, 0);
    private static final WorldPoint KARAMJA_CUSTOMS_POINT = new WorldPoint(2954, 3148, 0);
    private static final WorldPoint KARAMJA_FISHING_POINT = new WorldPoint(2924, 3177, 0);

    private static final int PORT_SARIM_CUSTOMS_MIN_X = 3039;
    private static final int PORT_SARIM_CUSTOMS_MAX_X = 3052;
    private static final int PORT_SARIM_CUSTOMS_MIN_Y = 3234;
    private static final int PORT_SARIM_CUSTOMS_MAX_Y = 3237;

    private static final int KARAMJA_DOCK_MIN_X = 2951;
    private static final int KARAMJA_DOCK_MAX_X = 2957;
    private static final int KARAMJA_DOCK_MIN_Y = 3144;
    private static final int KARAMJA_DOCK_MAX_Y = 3151;

    private static final int NPC_REACH_DISTANCE = 3;
    private static final int FISHING_AREA_DISTANCE = 5;
    private static final int DEPOSIT_AREA_DISTANCE = 6;
    private static final long WALK_REFIRE_COOLDOWN_MS = 1_000L;
    private static final long TRAVEL_TRANSITION_TIMEOUT_MS = 15_000L;
    private static final long INTERACTION_DISPATCH_TIMEOUT_MS = 1_500L;

    private static final String WALK_KEY_TO_PORT_SARIM_TRAVEL = "ksp_fishing_karamja_port_sarim_travel";
    private static final String WALK_KEY_TO_PORT_SARIM_DEPOSIT = "ksp_fishing_karamja_port_sarim_deposit";
    private static final String WALK_KEY_TO_KARAMJA_CUSTOMS = "ksp_fishing_karamja_customs";
    private static final String WALK_KEY_TO_KARAMJA_FISHING = "ksp_fishing_karamja_fishing";
    private static final String WALK_KEY_TO_PORT_SARIM_NPC = "ksp_fishing_karamja_port_sarim_npc";
    private static final String WALK_KEY_TO_CUSTOMS_NPC = "ksp_fishing_karamja_customs_npc";

    private static volatile TravelDirection pendingTravelDirection;
    private static volatile long pendingTravelUntilMs;
    private static volatile long pendingInteractionAtMs;
    private static volatile String pendingInteractionKey;
public static boolean travelToKaramjaFishingSpot()
    {
        if (handleTravelDialogue(TravelDirection.TO_KARAMJA))
        {
            return false;
        }

        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null)
        {
            return false;
        }

        if (isTravelTransitionPending(TravelDirection.TO_KARAMJA, playerLocation))
        {
            return false;
        }

        if (isNearKaramjaFishingSpot(playerLocation))
        {
            clearKaramjaTravelState();
            return true;
        }

        if (isInKaramjaArea(playerLocation))
        {
            Rs2Walker.walkTo(KARAMJA_FISHING_POINT, FISHING_AREA_DISTANCE);
            return false;
        }

        if (!isNearPortSarimTravelPoint(playerLocation))
        {
            // Important: the only mainland target before Musa Point must be the Port Sarim boat.
            // This prevents the global pathfinder from selecting Rimmington/charter-style routes.
            Rs2Walker.walkTo(PORT_SARIM_TRAVEL_POINT, NPC_REACH_DISTANCE);
            return false;
        }

        interactWithNearestNpc(
                PORT_SARIM_TRAVEL_NPCS,
                WALK_KEY_TO_PORT_SARIM_NPC,
                TravelDirection.TO_KARAMJA);
        return false;
    }

    public static boolean returnToPortSarimDepositPoint()
    {
        if (handleTravelDialogue(TravelDirection.TO_PORT_SARIM))
        {
            return false;
        }

        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null)
        {
            return false;
        }

        if (isTravelTransitionPending(TravelDirection.TO_PORT_SARIM, playerLocation))
        {
            return false;
        }

        if (!isInKaramjaArea(playerLocation)
                && playerLocation.distanceTo(PORT_SARIM_DEPOSIT_POINT) <= DEPOSIT_AREA_DISTANCE)
        {
            clearKaramjaTravelState();
            return true;
        }

        if (isInKaramjaDockArea(playerLocation))
        {
            interactWithNearestNpc(
                    CUSTOMS_OFFICER_NPCS,
                    WALK_KEY_TO_CUSTOMS_NPC,
                    TravelDirection.TO_PORT_SARIM);
            return false;
        }

        if (isInKaramjaArea(playerLocation))
        {
            Rs2Walker.walkTo(KARAMJA_CUSTOMS_POINT, NPC_REACH_DISTANCE);
            return false;
        }

        Rs2Walker.walkTo(PORT_SARIM_DEPOSIT_POINT, DEPOSIT_AREA_DISTANCE);
        return false;
    }

    public static boolean isNearKaramjaFishingSpot() { return isNearKaramjaFishingSpot(Rs2Player.getWorldLocation()); }

    public static boolean isNearKaramjaFishingSpot(WorldPoint location)
    {
        return location != null
                && location.getPlane() == KARAMJA_FISHING_POINT.getPlane()
                && location.distanceTo(KARAMJA_FISHING_POINT) <= FISHING_AREA_DISTANCE;
    }

    public static boolean isInKaramjaDockArea() { return isInKaramjaDockArea(Rs2Player.getWorldLocation()); }

    public static boolean isInKaramjaDockArea(WorldPoint location)
    {
        return isWithinBounds(
                location,
                KARAMJA_DOCK_MIN_X,
                KARAMJA_DOCK_MAX_X,
                KARAMJA_DOCK_MIN_Y,
                KARAMJA_DOCK_MAX_Y
        );
    }

    public static boolean isNearPortSarimTravelPoint() { return isNearPortSarimTravelPoint(Rs2Player.getWorldLocation()); }

    public static boolean isNearPortSarimTravelPoint(WorldPoint location)
    {
        return location != null
                && location.getPlane() == PORT_SARIM_TRAVEL_POINT.getPlane()
                && location.distanceTo2D(PORT_SARIM_TRAVEL_POINT) <= NPC_REACH_DISTANCE;
    }

    public static boolean isInPortSarimCustomsArea() { return isInPortSarimCustomsArea(Rs2Player.getWorldLocation()); }

    public static boolean isInPortSarimCustomsArea(WorldPoint location)
    {
        return isWithinBounds(
                location,
                PORT_SARIM_CUSTOMS_MIN_X,
                PORT_SARIM_CUSTOMS_MAX_X,
                PORT_SARIM_CUSTOMS_MIN_Y,
                PORT_SARIM_CUSTOMS_MAX_Y
        );
    }

    public static boolean isInKaramjaArea() { return isInKaramjaArea(Rs2Player.getWorldLocation()); }

    public static boolean isInKaramjaArea(WorldPoint location)
    {
        return location != null
                && location.getPlane() == 0
                && location.getX() < 3000
                && location.getY() < 3200;
    }

    public static WorldPoint getKaramjaFishingPoint() { return KARAMJA_FISHING_POINT; }

    public static WorldPoint getKaramjaCustomsPoint() { return KARAMJA_CUSTOMS_POINT; }

    public static WorldPoint getPortSarimTravelPoint() { return PORT_SARIM_TRAVEL_POINT; }

    public static WorldPoint getPortSarimDepositPoint() { return PORT_SARIM_DEPOSIT_POINT; }

    public static void clearKaramjaTravelState()
    {
        clearPendingTravel();
        clearPendingInteraction();
        // Core WebWalker owns route state; the ferry step has no keyed walks.
        if (Rs2Walker.getCurrentTarget() != null)
        {
            Rs2Walker.clearWalkingRoute("ksp_fishing_karamja_arrived");
        }
    }

    private static boolean handleTravelDialogue(TravelDirection direction)
    {
        if (Rs2Dialogue.hasContinue())
        {
            if (isInteractionPending("dialogue-continue")) return true;
            Rs2Dialogue.clickContinue();
            markInteraction("dialogue-continue");
            return true;
        }

        if (!Rs2Dialogue.hasSelectAnOption())
        {
            return false;
        }

        boolean clicked = Rs2Dialogue.clickOption(
                "Yes please",
                "Yes",
                "Musa Point",
                "Karamja",
                "Port Sarim",
                "pay",
                "travel",
                "sail"
        );
        if (clicked)
        {
            markTravelPending(direction);
        }
        return clicked;
    }

    

    

    private static boolean interactWithNearestNpc(
            List<String> npcNames,
            String walkKey,
            TravelDirection direction)
    {
        Rs2NpcModel npc = findNearestNpc(npcNames);
        if (npc == null)
        {
            return false;
        }

        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        WorldPoint npcLocation = npc.getWorldLocation();
        if (playerLocation == null || npcLocation == null)
        {
            return false;
        }

        if (playerLocation.distanceTo(npcLocation) > NPC_REACH_DISTANCE)
        {
            Rs2Walker.walkTo(npcLocation, NPC_REACH_DISTANCE);
            return false;
        }

        String action = getKaramjaNpcAvailableAction(npc, TRAVEL_ACTIONS);
        if (action.isEmpty())
        {
            return false;
        }

        String interactionKey = "npc:" + npc.getIndex() + ":" + action;
        if (isInteractionPending(interactionKey)
                || Rs2Player.isMoving()
                || Rs2Player.isAnimating()
                || Rs2Player.isInteracting())
        {
            return false;
        }

        boolean clicked = npc.click(action);
        if (clicked)
        {
            markInteraction(interactionKey);
            if (!"Talk-to".equalsIgnoreCase(action))
            {
                markTravelPending(direction);
            }
        }
        return clicked;
    }

    private static boolean isInteractionPending(String key)
    {
        if (pendingInteractionAtMs == 0L || pendingInteractionKey == null) return false;
        if (!pendingInteractionKey.equals(key)) return false;
        if (System.currentTimeMillis() - pendingInteractionAtMs < INTERACTION_DISPATCH_TIMEOUT_MS) return true;
        clearPendingInteraction();
        return false;
    }

    private static void markInteraction(String key)
    {
        pendingInteractionKey = key;
        pendingInteractionAtMs = System.currentTimeMillis();
    }

    private static void clearPendingInteraction()
    {
        pendingInteractionAtMs = 0L;
        pendingInteractionKey = null;
    }

    private static boolean isTravelTransitionPending(TravelDirection direction, WorldPoint playerLocation)
    {
        TravelDirection pendingDirection = pendingTravelDirection;
        if (pendingDirection == null)
        {
            return false;
        }

        boolean arrived = pendingDirection == TravelDirection.TO_KARAMJA
                ? isInKaramjaArea(playerLocation)
                : !isInKaramjaArea(playerLocation);
        if (arrived || System.currentTimeMillis() >= pendingTravelUntilMs)
        {
            clearPendingTravel();
            return false;
        }

        return pendingDirection == direction;
    }

    private static void markTravelPending(TravelDirection direction)
    {
        pendingTravelDirection = direction;
        pendingTravelUntilMs = System.currentTimeMillis() + TRAVEL_TRANSITION_TIMEOUT_MS;
    }

    private static void clearPendingTravel()
    {
        pendingTravelDirection = null;
        pendingTravelUntilMs = 0L;
    }

    private static Rs2NpcModel findNearestNpc(List<String> npcNames)
    {
        if (npcNames == null || npcNames.isEmpty())
        {
            return null;
        }

        return KspLiveScene.nearestNpcModel(Rs2Player.getWorldLocation(), 30,
                npc -> npc.getName() != null
                        && npcNames.stream().anyMatch(name -> name.equalsIgnoreCase(npc.getName())));
    }

    private static String getKaramjaNpcAvailableAction(Rs2NpcModel npc, List<String> preferredActions)
    {
        if (npc == null || preferredActions == null || preferredActions.isEmpty())
        {
            return "";
        }

        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (Microbot.getClient() == null)
            {
                return "";
            }

            NPCComposition composition = Microbot.getClient().getNpcDefinition(npc.getId());
            if (composition == null || composition.getActions() == null)
            {
                return "";
            }

            for (String preferredAction : preferredActions)
            {
                for (String action : composition.getActions())
                {
                    if (action != null && action.equalsIgnoreCase(preferredAction))
                    {
                        return action;
                    }
                }
            }

            return "";
        }).orElse("");
    }

    private static boolean isWithinBounds(
            WorldPoint location,
            int minX,
            int maxX,
            int minY,
            int maxY)
    {
        return location != null
                && location.getPlane() == 0
                && location.getX() >= minX
                && location.getX() <= maxX
                && location.getY() >= minY
                && location.getY() <= maxY;
    }

    private enum TravelDirection
    {
        TO_KARAMJA,
        TO_PORT_SARIM
    }

}
