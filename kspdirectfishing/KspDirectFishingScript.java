package net.runelite.client.plugins.microbot.kspdirectfishing;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.AnimationID;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
public class KspDirectFishingScript extends Script
{
    private static final int DIRECT_BANK_OBJECT_SEARCH_RADIUS = 20;
    private static final long BANK_OPEN_RETRY_DELAY = 8_000L;
    private static final long FIRE_STATUS_REFRESH_DELAY = 1_500L;
    private static final int DIRECT_FIRE_DISTANCE = 4;
    private static final long FISH_INTERACTION_TIMEOUT = 15_000L;
    private static final long FAILED_CLICK_RETRY_DELAY = 2_000L;
    private static final int[] SUPPORTED_FIRE_IDS = {
            ObjectID.FIRE, ObjectID.FORESTRY_FIRE, ObjectID.EAGLEPEAK_CAMPFIRE_TIDY,
            ObjectID.FIRE_COOK, 43475
    };

    private KspDirectFishingConfig config;
    private KspDirectFishingMode mode;

    @Getter private volatile KspDirectFishingState state = KspDirectFishingState.STARTING;
    @Getter private volatile String status = "Starting";
    @Getter private volatile WorldPoint fishingAnchor;
    @Getter private volatile boolean fireAvailable;
    @Getter private volatile WorldPoint lastFirePoint;

    private long lastFishingClick;
    private long lastFailedFishingClick;
    private long lastBankOpenAttempt;
    private boolean bankOpenRequested;
    private long lastFireStatusCheck;

    public int getRawFishCount()
    {
        if (mode == null) return 0;
        switch (mode)
        {
            case SHRIMP_ANCHOVIES:
                return Rs2Inventory.count(ItemID.RAW_SHRIMP) + Rs2Inventory.count(ItemID.RAW_ANCHOVIES);
            case SARDINE_HERRING:
                return Rs2Inventory.count(ItemID.RAW_SARDINE) + Rs2Inventory.count(ItemID.RAW_HERRING);
            default:
                return 0;
        }
    }

    public int getBaitCount()
    {
        if (mode == null || !mode.usesBait()) return 0;
        return Rs2Inventory.all().stream()
                .filter(item -> item.getId() == ItemID.FISHING_BAIT)
                .mapToInt(item -> Math.max(0, item.getQuantity()))
                .sum();
    }

    public boolean run(KspDirectFishingConfig config)
    {
        this.config = config;
        mode = config.fishingMode();
        state = KspDirectFishingState.STARTING;
        status = "Locating fishing spot";
        lastFishingClick = lastFailedFishingClick = lastBankOpenAttempt = lastFireStatusCheck = 0L;
        bankOpenRequested = fireAvailable = false;
        lastFirePoint = null;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(this::loop, 0, 500, TimeUnit.MILLISECONDS);
        return true;
    }

    private void loop()
    {
        try
        {
            if (!super.run() || !Microbot.isLoggedIn()) return;

            if (Rs2Bank.isOpen() && state != KspDirectFishingState.BANKING)
            {
                Rs2Bank.closeBank();
                return;
            }

            mode = config.fishingMode();
            refreshFireStatusIfDue();

            if (fishingAnchor == null)
            {
                Rs2NpcModel initialSpot = findNearestFishingSpot();
                if (initialSpot != null) fishingAnchor = initialSpot.getWorldLocation();
            }

            KspDirectFishingState next = determineState();
            state = next;
            switch (next)
            {
                case FISHING:
                    handleFishing();
                    break;
                case WALKING_TO_FISH:
                case RETURNING_TO_FISH:
                    handleReturnToFishing();
                    break;
                case FINDING_FIRE:
                case WALKING_TO_FIRE:
                case USING_FIRE:
                case WAITING_FOR_COOK_INTERFACE:
                case COOKING:
                case WAITING_FOR_FIRE:
                    handleCookingFlow();
                    break;
                case WALKING_TO_BANK:
                case BANKING:
                    handleBanking();
                    break;
                case STARTING:
                    handleStart();
                    break;
                case ERROR:
                default:
                    break;
            }
        }
        catch (Exception ex)
        {
            state = KspDirectFishingState.ERROR;
            status = "Error - check client log";
            log.error("KSP Direct Fishing loop error", ex);
            sleep(1_500);
        }
    }

    private KspDirectFishingState determineState()
    {
        boolean needsProcessing = Rs2Inventory.isFull() || !hasFishingSupplies();
        if (needsProcessing && hasCookableRawFish()) return KspDirectFishingState.FINDING_FIRE;
        if (needsProcessing)
        {
            return Rs2Bank.isOpen() ? KspDirectFishingState.BANKING : KspDirectFishingState.WALKING_TO_BANK;
        }
        if (findNearestFishingSpot() != null) return KspDirectFishingState.FISHING;
        if (fishingAnchor != null) return KspDirectFishingState.WALKING_TO_FISH;
        return KspDirectFishingState.STARTING;
    }

    private void handleStart()
    {
        Rs2NpcModel spot = findNearestFishingSpot();
        if (spot != null)
        {
            fishingAnchor = spot.getWorldLocation();
            status = "Fishing spot locked";
            state = KspDirectFishingState.FISHING;
            return;
        }
        status = "Start near a compatible fishing spot";
    }

    private void handleFishing()
    {
        if (Rs2Player.isMoving() || Rs2Inventory.isFull() || !hasFishingSupplies()) return;

        Rs2NpcModel fishingSpot = findNearestFishingSpot();
        if (fishingSpot == null)
        {
            status = "Finding fishing spot";
            return;
        }

        WorldPoint spotPoint = fishingSpot.getWorldLocation();
        if (spotPoint != null) fishingAnchor = spotPoint;

        if ((Rs2Player.isAnimating() || Rs2Player.isInteracting())
                && System.currentTimeMillis() - lastFishingClick < FISH_INTERACTION_TIMEOUT)
        {
            status = "Fishing " + mode;
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastFailedFishingClick < FAILED_CLICK_RETRY_DELAY)
        {
            status = "Waiting to retry fishing";
            return;
        }

        String action = mode.getPrimaryAction();
        status = "Direct click: " + action;
        if (fishingSpot.click(action))
        {
            lastFishingClick = now;
            lastFailedFishingClick = 0L;
            Rs2Player.waitForXpDrop(Skill.FISHING, true);

            while (!Thread.currentThread().isInterrupted()
                    && Microbot.isLoggedIn()
                    && !Rs2Inventory.isFull()
                    && hasFishingSupplies()
                    && (Rs2Player.isAnimating() || Rs2Player.isInteracting()))
            {
                Rs2Player.waitForXpDrop(Skill.FISHING, 10_000, true);
            }
        }
        else
        {
            lastFailedFishingClick = now;
            status = "Fishing click failed: " + action;
        }
    }

    private void handleCookingFlow()
    {
        if (!hasCookableRawFish())
        {
            state = KspDirectFishingState.WALKING_TO_BANK;
            status = "Cooking complete";
            return;
        }

        Rs2TileObjectModel fire = findNearestFire();
        if (fire == null)
        {
            if (config.waitForFire())
            {
                state = KspDirectFishingState.WAITING_FOR_FIRE;
                status = "Waiting for Fire / Forester's Campfire";
            }
            else
            {
                state = KspDirectFishingState.WALKING_TO_BANK;
                status = "No fire found - banking raw fish";
            }
            return;
        }

        WorldPoint firePoint = getTileObjectWorldPoint(fire);
        if (firePoint == null)
        {
            updateFireStatus(null);
            status = "Fire location unavailable";
            return;
        }

        fireAvailable = true;
        lastFirePoint = firePoint;
        turnCameraTowardCampfire(firePoint);

        if (Rs2Player.getWorldLocation().distanceTo(firePoint) > DIRECT_FIRE_DISTANCE)
        {
            state = KspDirectFishingState.WALKING_TO_FIRE;
            status = "Walking to nearest fire";
            Rs2Walker.walkTo(firePoint, DIRECT_FIRE_DISTANCE);
            sleepUntil(() -> !Rs2Player.isMoving()
                    || Rs2Player.getWorldLocation().distanceTo(firePoint) <= 3, 10_000);
            return;
        }

        state = KspDirectFishingState.USING_FIRE;
        status = "Preparing fish for fire";
        cookAllOn(fire);
    }

    private void cookAllOn(Rs2TileObjectModel fire)
    {
        while (!Thread.currentThread().isInterrupted() && Microbot.isLoggedIn() && hasCookableRawFish())
        {
            if (Rs2Player.isMoving())
            {
                state = KspDirectFishingState.WALKING_TO_FIRE;
                status = "Finishing movement to fire";
                return;
            }

            if (Rs2Player.isAnimating() || Rs2Player.isInteracting())
            {
                state = KspDirectFishingState.COOKING;
                status = "Cooking in progress";
                sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isInteracting(), 15_000);
            }

            String rawFish = firstCookableRawFish();
            if (rawFish == null)
            {
                status = "No cookable raw fish";
                return;
            }

            Integer fireId = Microbot.getClientThread().invoke(fire::getId);
            if (fireId == null)
            {
                updateFireStatus(null);
                state = KspDirectFishingState.FINDING_FIRE;
                status = "Fire disappeared";
                return;
            }

            state = KspDirectFishingState.USING_FIRE;
            status = "Turning camera to campfire";
            WorldPoint activeFirePoint = getTileObjectWorldPoint(fire);
            if (activeFirePoint != null) turnCameraTowardCampfire(activeFirePoint);

            status = "Using " + rawFish + " on fire";
            Rs2Inventory.useUnNotedItemOnObject(rawFish, fireId);
            state = KspDirectFishingState.WAITING_FOR_COOK_INTERFACE;
            status = "Waiting for cook interface";

            if (!sleepUntil(() -> Rs2Widget.findWidget("How many would you like to cook?", null) != null, 3_500))
            {
                state = KspDirectFishingState.FINDING_FIRE;
                status = "Cook interface did not open";
                sleep(1_000, 1_600);
                return;
            }

            status = "Starting " + rawFish;
            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
            if (!sleepUntil(() -> Rs2Player.isAnimating() || Rs2Player.getAnimation() != AnimationID.IDLE, 5_000))
            {
                state = KspDirectFishingState.WAITING_FOR_COOK_INTERFACE;
                status = "Cooking did not start - retrying";
                sleep(800, 1_300);
                return;
            }

            state = KspDirectFishingState.COOKING;
            status = "Cooking " + rawFish;
            Rs2Player.waitForXpDrop(Skill.COOKING, true);

            String cookingThis = rawFish;
            sleepUntilTrue(() -> !Rs2Inventory.hasItem(cookingThis)
                    && !Rs2Player.isAnimating(3_500), 500, 150_000);
        }

        state = KspDirectFishingState.WALKING_TO_BANK;
        status = "Cooking complete";
    }

    private void handleBanking()
    {
        if (Rs2Bank.isOpen())
        {
            bankOpenRequested = false;
            lastBankOpenAttempt = 0L;
            state = KspDirectFishingState.BANKING;
            bankInventoryAndRestock();
            return;
        }

        if (config.directBankFirst())
        {
            Rs2TileObjectModel bankBooth = null;
            try
            {
                bankBooth = Microbot.getRs2TileObjectCache().query()
                        .withNames("Bank booth", "Bank chest", "Bank")
                        .within(DIRECT_BANK_OBJECT_SEARCH_RADIUS)
                        .nearestOnClientThread();
            }
            catch (RuntimeException ex)
            {
                log.debug("Bank booth query failed: {}", ex.getMessage());
            }

            if (bankBooth != null)
            {
                state = KspDirectFishingState.BANKING;
                long now = System.currentTimeMillis();
                if (!bankOpenRequested)
                {
                    bankOpenRequested = true;
                    lastBankOpenAttempt = now;
                    status = "Clicking bank booth once";
                    status = bankBooth.click("Bank") ? "Waiting for bank to open" : "Bank booth click failed";
                    sleepUntil(Rs2Bank::isOpen, 5_000);
                    if (Rs2Bank.isOpen())
                    {
                        bankOpenRequested = false;
                        lastBankOpenAttempt = 0L;
                    }
                    return;
                }

                if (now - lastBankOpenAttempt < BANK_OPEN_RETRY_DELAY)
                {
                    status = "Waiting for bank to open";
                    return;
                }

                bankOpenRequested = false;
                status = "Bank open timed out - retrying";
                return;
            }
        }

        bankOpenRequested = false;
        state = KspDirectFishingState.WALKING_TO_BANK;
        status = "Bank booth not visible - walking closer";
        Rs2Bank.walkToBank();
    }

    private void bankInventoryAndRestock()
    {
        status = "Banking fish";
        Set<Integer> keepIds = new HashSet<>();
        for (String item : mode.getRequiredItems())
        {
            Rs2ItemModel invItem = Rs2Inventory.get(item);
            if (invItem != null) keepIds.add(invItem.getId());
        }

        if (keepIds.isEmpty()) Rs2Bank.depositAll();
        else Rs2Bank.depositAllExcept(keepIds.toArray(new Integer[0]));
        Rs2Inventory.waitForInventoryChanges(1_500);

        if (mode == KspDirectFishingMode.SHRIMP_ANCHOVIES)
        {
            if (!withdrawSupply("Small fishing net", false)) return;
        }
        else if (!withdrawSupply("Fishing rod", false) || !withdrawSupply("Fishing bait", true))
        {
            return;
        }

        if (!hasFishingSupplies())
        {
            failAndStop("Could not obtain required fishing supplies");
            return;
        }

        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 3_000);
        bankOpenRequested = false;
        lastBankOpenAttempt = 0L;
        state = KspDirectFishingState.RETURNING_TO_FISH;
        status = "Returning to fishing spot";
    }

    private boolean withdrawSupply(String item, boolean all)
    {
        if (Rs2Inventory.hasItem(item)) return true;
        if (!Rs2Bank.hasItem(item))
        {
            failAndStop(item + " not found in bank");
            return false;
        }

        status = "Withdrawing " + item.toLowerCase(Locale.ROOT);
        if (all) Rs2Bank.withdrawAll(item);
        else Rs2Bank.withdrawOne(item);
        sleepUntil(() -> Rs2Inventory.hasItem(item), 3_500);
        return true;
    }

    private void handleReturnToFishing()
    {
        Rs2NpcModel visibleSpot = findNearestFishingSpot();
        if (visibleSpot == null)
        {
            state = KspDirectFishingState.RETURNING_TO_FISH;
            status = "Waiting for fishing spot to load";
            return;
        }

        if (Rs2Inventory.isFull() || !hasFishingSupplies())
        {
            status = "Cannot return to fish yet";
            return;
        }

        WorldPoint spotPoint = visibleSpot.getWorldLocation();
        if (spotPoint != null) fishingAnchor = spotPoint;

        long now = System.currentTimeMillis();
        if (now - lastFailedFishingClick < FAILED_CLICK_RETRY_DELAY)
        {
            status = "Waiting to retry fishing";
            return;
        }

        String action = mode.getPrimaryAction();
        state = KspDirectFishingState.FISHING;
        status = "Direct return: " + action;
        if (visibleSpot.click(action))
        {
            lastFishingClick = now;
            lastFailedFishingClick = 0L;
            status = "Fishing " + mode;
        }
        else
        {
            lastFailedFishingClick = now;
            state = KspDirectFishingState.RETURNING_TO_FISH;
            status = "Return click failed: " + action;
        }
    }

    private Rs2NpcModel findNearestFishingSpot()
    {
        int[] ids = mode.getFishingSpotIds();
        try
        {
            return Microbot.getRs2NpcCache().query()
                    .where(npc -> Arrays.stream(ids).anyMatch(id -> npc.getId() == id))
                    .nearestOnClientThread();
        }
        catch (RuntimeException ex)
        {
            if (Thread.currentThread().isInterrupted()) return null;
            log.debug("Fishing spot query failed: {}", ex.getMessage());
            return null;
        }
    }

    private void turnCameraTowardCampfire(WorldPoint firePoint)
    {
        if (firePoint == null) return;
        try
        {
            int angle = (Rs2Camera.angleToTile(firePoint) - 90) % 360;
            if (angle < 0) angle += 360;
            Rs2Camera.setAngle(angle, 25);
        }
        catch (RuntimeException ex)
        {
            log.debug("Camera turn toward campfire failed: {}", ex.getMessage());
        }
    }

    private void refreshFireStatusIfDue()
    {
        long now = System.currentTimeMillis();
        if (now - lastFireStatusCheck < FIRE_STATUS_REFRESH_DELAY) return;
        lastFireStatusCheck = now;

        try
        {
            updateFireStatus(queryNearestFire());
        }
        catch (RuntimeException ex)
        {
            updateFireStatus(null);
            log.debug("Fire status refresh failed: {}", ex.getMessage());
        }
    }

    private Rs2TileObjectModel findNearestFire()
    {
        try
        {
            Rs2TileObjectModel fire = queryNearestFire();
            updateFireStatus(fire);
            return fire;
        }
        catch (RuntimeException ex)
        {
            updateFireStatus(null);
            if (!Thread.currentThread().isInterrupted()) log.debug("Fire query failed: {}", ex.getMessage());
            return null;
        }
    }

    private Rs2TileObjectModel queryNearestFire()
    {
        return Microbot.getRs2TileObjectCache().query()
                .withIds(SUPPORTED_FIRE_IDS)
                .within(config.fireSearchRadius())
                .nearestOnClientThread();
    }

    private void updateFireStatus(Rs2TileObjectModel fire)
    {
        fireAvailable = fire != null;
        lastFirePoint = fire == null ? null : getTileObjectWorldPoint(fire);
    }

    private WorldPoint getTileObjectWorldPoint(Rs2TileObjectModel object)
    {
        if (object == null) return null;
        try
        {
            return Microbot.getClientThread().invoke(object::getWorldLocation);
        }
        catch (RuntimeException ex)
        {
            log.debug("Tile object location lookup failed: {}", ex.getMessage());
            return null;
        }
    }

    private boolean hasFishingSupplies()
    {
        return mode == KspDirectFishingMode.SHRIMP_ANCHOVIES
                ? Rs2Inventory.hasItem("Small fishing net")
                : Rs2Inventory.hasItem("Fishing rod") && Rs2Inventory.hasItem("Fishing bait");
    }

    private boolean hasCookableRawFish() { return firstCookableRawFish() != null; }

    private String firstCookableRawFish()
    {
        for (String raw : mode.getRawFish())
        {
            if (Rs2Inventory.hasItem(raw)
                    && Rs2Player.getSkillRequirement(Skill.COOKING, requiredCookingLevel(raw)))
            {
                return raw;
            }
        }
        return null;
    }

    private int requiredCookingLevel(String rawFish)
    {
        return "Raw herring".equalsIgnoreCase(rawFish) ? 5 : 1;
    }

    private void failAndStop(String message)
    {
        state = KspDirectFishingState.ERROR;
        status = message;
        Microbot.showMessage(message);
        log.error(message);
        shutdown();
    }

    @Override
    public void shutdown()
    {
        state = KspDirectFishingState.STARTING;
        status = "Stopped";
        super.shutdown();
    }
}
