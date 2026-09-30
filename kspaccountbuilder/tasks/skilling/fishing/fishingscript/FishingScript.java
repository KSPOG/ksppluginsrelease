package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.fishing.fishingscript;

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
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspWalkerGuard;
import net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.KspBankWidgetHelper;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.fishing.areas.Areas;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.fishing.helper.KaramjaTravelHelper;
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
public class FishingScript extends Script
{
    private static final Logger log = LoggerFactory.getLogger(FishingScript.class);

    private static final int LOOP_DELAY_MS = 100;
    private static final int WEB_WALK_COOLDOWN_MS = 1_000;
    private static final int NPC_INTERACTION_COOLDOWN_MS = 100;
    private static final int FISHING_SPOT_SEARCH_PADDING_TILES = 8;
    private static final int OUT_OF_AREA_SPOT_FALLBACK_RADIUS = 4;
    private static final int FISHING_SPOT_INTERACTION_DISTANCE = 8;
    private static final int MIN_KARAMJA_COINS = 60;
    private static final int KARAMJA_COIN_RESERVE = 2_000;
    private static final int TROUT_SALMON_FIRE_ID = 43475;
    private static final int NO_COOKING_BATCH = -1;
    private static final String WALK_KEY_TO_FISHING_AREA = "Fishing:target-area";
    private static final String WALK_KEY_TO_TROUT_SALMON_FIRE = "Fishing:trout-salmon-fire";
    private static final WorldPoint TROUT_SALMON_WALK_POSITION = new WorldPoint(3104, 3431, 0);
    private static final WorldPoint TROUT_SALMON_FIRE_POSITION = new WorldPoint(3106, 3432, 0);

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
    private long lastWebWalkAtMs;

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

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            if (!super.run() || !Microbot.isLoggedIn())
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
                if (cookTroutOrSalmonBeforeBanking())
                {
                    state = FishingState.COOKING;
                    return;
                }

                state = FishingState.BANKING;
                bankFishOnly();
                return;
            }

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

        Microbot.status = "Cooking " + getCookingBatchName() + " before banking";

        if (Rs2Widget.isProductionWidgetOpen()
                || Rs2Widget.findWidget("How many would you like to cook?", null, false) != null)
        {
            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
            expectingCookingXpDrop = true;
            return true;
        }

        if (Rs2Player.isAnimating()) return true;

        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null) return true;

        if (player.distanceTo(TROUT_SALMON_FIRE_POSITION) > 2) {
            KspWalkerGuard.walkToPoint(WALK_KEY_TO_TROUT_SALMON_FIRE, TROUT_SALMON_FIRE_POSITION, 2, WEB_WALK_COOLDOWN_MS);
            return true;
        }

        Rs2TileObjectModel fire = Microbot.getRs2TileObjectCache().query()
                .fromWorldView().withId(TROUT_SALMON_FIRE_ID).nearest();
        if (fire == null) {
            resetCookingBatch();
            return false;
        }

        expectingCookingXpDrop = Rs2Inventory.useItemOnObject(cookingBatchItemId, fire.getId());
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
    }

    private void fishCurrentTarget()
    {
        if (!canStartFishingInTargetArea()) return;

        long now = System.currentTimeMillis();
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
        if (!spot.click(action)) {
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
            return KaramjaTravelHelper.travelToKaramjaFishingSpot();
        }

        Microbot.status = "Walking to " + targetArea.getDisplayName();
        if (KspWalkerGuard.walkToDestination(
                WALK_KEY_TO_FISHING_AREA,
                this::getTargetAreaWalkPoint,
                area::contains,
                3,
                WEB_WALK_COOLDOWN_MS))
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
        KspWalkerGuard.clearReachedDestination(
                WALK_KEY_TO_FISHING_AREA,
                "ksp_account_builder_fishing_reached_area");
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

        if (KspBankWidgetHelper.closeBankTutorialOverlayIfOpen()) return false;
        if (!KspBankMode.ensureWithdrawAsItem()) return false;

        List<String> required = getRequiredItems(fish);
        Rs2Bank.depositAllExcept(required.toArray(new String[0]));

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
                    Rs2Bank.withdrawAll(item);
                    return false;
                }
                continue;
            }

            if (!Rs2Inventory.hasItem(item) && Rs2Bank.count(item) > 0) {
                Rs2Bank.withdrawOne(item);
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

        List<String> required = getRequiredItems(targetFish);
        Rs2Bank.depositAllExcept(required.toArray(new String[0]));
        if (!Rs2Inventory.isFull()) Rs2Bank.closeBank();
    }

    private void depositKaramjaFishOnly()
    {
        if (!KaramjaTravelHelper.returnToPortSarimDepositPoint()) return;

        if (!Rs2DepositBox.isOpen()) {
            Rs2DepositBox.openDepositBox();
            return;
        }

        List<String> required = getRequiredItems(targetFish);
        Rs2DepositBox.depositAllExcept(required, false);
        if (!Rs2Inventory.isFull()) Rs2DepositBox.closeDepositBox();
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
        List<Rs2NpcModel> candidates = Microbot.getRs2NpcCache().query()
                .fromWorldView()
                .within(searchCenter, searchRadius)
                .where(candidate -> candidate != null
                        && candidate.getWorldLocation() != null
                        && isFishingSpotName(candidate)
                        && (requireInsideArea
                        ? fishingArea.contains(candidate.getWorldLocation())
                        : isNearTargetArea(candidate.getWorldLocation(), OUT_OF_AREA_SPOT_FALLBACK_RADIUS))
                        && isInteractableFishingSpot(candidate))
                .toListOnClientThread();

        return candidates.stream()
                .filter(candidate -> !getAvailableAction(candidate, target.getActions()).isEmpty())
                .min(Comparator.comparingInt(candidate -> {
                    WorldPoint playerLocation = Rs2Player.getWorldLocation();
                    WorldPoint candidateLocation = candidate.getWorldLocation();
                    if (playerLocation == null || candidateLocation == null)
                    {
                        return Integer.MAX_VALUE;
                    }
                    return playerLocation.distanceTo(candidateLocation);
                }))
                .orElse(null);
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
                && !Rs2Player.isAnimating();
    }

    private boolean canStartFishingInTargetArea()
    {
        WorldPoint player = Rs2Player.getWorldLocation();
        return player != null
                && targetArea.contains(player)
                && !Rs2Player.isAnimating();
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
        if (amount > 0) Rs2Bank.withdrawX(true, ItemID.COINS_995, amount);
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
        lastWebWalkAtMs = 0L;
        state = FishingState.WAITING;
        KspWalkerGuard.clear(WALK_KEY_TO_FISHING_AREA);
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
}
