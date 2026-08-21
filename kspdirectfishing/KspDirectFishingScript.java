package net.runelite.client.plugins.microbot.kspdirectfishing;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.AnimationID;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
public class KspDirectFishingScript extends Script
{
    private static final int DIRECT_BANK_DISTANCE = 8;
    private static final int DIRECT_FIRE_DISTANCE = 4;
    private static final long FISH_INTERACTION_TIMEOUT = 15_000L;

    private KspDirectFishingConfig config;
    private KspDirectFishingMode mode;

    private volatile KspDirectFishingState state = KspDirectFishingState.STARTING;

    private volatile String status = "Starting";

    private volatile WorldPoint fishingAnchor;
    private volatile boolean fireAvailable;
    private volatile WorldPoint lastFirePoint;

    public KspDirectFishingState getState()
    {
        return state;
    }

    public String getStatus()
    {
        return status;
    }

    public WorldPoint getFishingAnchor()
    {
        return fishingAnchor;
    }

    public boolean isFireAvailable()
    {
        return fireAvailable;
    }

    public WorldPoint getLastFirePoint()
    {
        return lastFirePoint;
    }

    public int getRawFishCount()
    {
        int total = 0;
        if (mode == null)
        {
            return total;
        }

        for (String raw : mode.getRawFish())
        {
            total += Rs2Inventory.count(raw);
        }
        return total;
    }

    public int getBaitCount()
    {
        return mode != null && mode.usesBait()
                ? Rs2Inventory.count("Fishing bait")
                : 0;
    }

    private long lastFishingClick;

    public boolean run(KspDirectFishingConfig config)
    {
        this.config = config;
        this.mode = config.fishingMode();
        this.state = KspDirectFishingState.STARTING;
        this.status = "Locating fishing spot";
        this.lastFishingClick = 0L;
        this.fireAvailable = false;
        this.lastFirePoint = null;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(
                this::loop,
                0,
                500,
                TimeUnit.MILLISECONDS
        );

        return true;
    }

    private void loop()
    {
        try
        {
            if (!super.run() || !Microbot.isLoggedIn())
            {
                return;
            }

            if (Rs2Bank.isOpen() && state != KspDirectFishingState.BANKING)
            {
                Rs2Bank.closeBank();
                return;
            }

            mode = config.fishingMode();

            if (fishingAnchor == null)
            {
                Rs2NpcModel initialSpot = findNearestFishingSpot();
                if (initialSpot != null)
                {
                    fishingAnchor = initialSpot.getWorldLocation();
                }
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
        boolean hasSupplies = hasFishingSupplies();
        boolean hasRawFish = hasRawFish();
        boolean hasCookableRawFish = hasCookableRawFish();

        /*
         * A completed/partial fishing trip is processed before banking:
         * 1. inventory full OR fishing supplies ran out
         * 2. cook everything we are able to cook
         * 3. bank cooked/burnt fish and any raw fish that cannot be cooked
         * 4. restock fishing supplies
         */
        if ((Rs2Inventory.isFull() || !hasSupplies) && hasCookableRawFish)
        {
            return KspDirectFishingState.FINDING_FIRE;
        }

        if (Rs2Inventory.isFull() || !hasSupplies)
        {
            return Rs2Bank.isOpen()
                    ? KspDirectFishingState.BANKING
                    : KspDirectFishingState.WALKING_TO_BANK;
        }

        if (hasRawFish && tripShouldBeProcessed())
        {
            return KspDirectFishingState.FINDING_FIRE;
        }

        Rs2NpcModel spot = findNearestFishingSpot();
        if (spot != null)
        {
            return KspDirectFishingState.FISHING;
        }

        if (fishingAnchor != null)
        {
            return KspDirectFishingState.WALKING_TO_FISH;
        }

        return KspDirectFishingState.STARTING;
    }

    private boolean tripShouldBeProcessed()
    {
        return Rs2Inventory.isFull() || !hasFishingSupplies();
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
        if (Rs2Player.isMoving())
        {
            return;
        }

        if (Rs2Inventory.isFull() || !hasFishingSupplies())
        {
            return;
        }

        Rs2NpcModel fishingSpot = findNearestFishingSpot();
        if (fishingSpot == null)
        {
            status = "Finding fishing spot";
            return;
        }

        // Rs2NpcModel performs the required client-thread dispatch internally.
        WorldPoint spotPoint = fishingSpot.getWorldLocation();
        if (spotPoint != null)
        {
            fishingAnchor = spotPoint;
        }

        if ((Rs2Player.isAnimating() || Rs2Player.isInteracting())
                && System.currentTimeMillis() - lastFishingClick < FISH_INTERACTION_TIMEOUT)
        {
            status = "Fishing " + mode;
            return;
        }

        String action = mode.getPrimaryAction();
        status = "Direct click: " + action;

        if (fishingSpot.click(action))
        {
            lastFishingClick = System.currentTimeMillis();

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
                return;
            }

            state = KspDirectFishingState.WALKING_TO_BANK;
            status = "No fire found - banking raw fish";
            return;
        }

        WorldPoint firePoint = getTileObjectWorldPoint(fire);
        if (firePoint == null)
        {
            fireAvailable = false;
            lastFirePoint = null;
            status = "Fire location unavailable";
            return;
        }

        fireAvailable = true;
        lastFirePoint = firePoint;

        int distance = Rs2Player.getWorldLocation().distanceTo(firePoint);

        if (distance > DIRECT_FIRE_DISTANCE)
        {
            state = KspDirectFishingState.WALKING_TO_FIRE;
            status = "Walking to nearest fire";
            Rs2Walker.walkTo(firePoint);
            sleepUntil(() ->
                    !Rs2Player.isMoving()
                            || Rs2Player.getWorldLocation().distanceTo(firePoint) <= 3,
                    10_000
            );
            return;
        }

        state = KspDirectFishingState.COOKING;
        cookAllOn(fire);
    }

    private void cookAllOn(Rs2TileObjectModel fire)
    {
        while (!Thread.currentThread().isInterrupted()
                && Microbot.isLoggedIn()
                && hasCookableRawFish())
        {
            if (Rs2Player.isMoving())
            {
                return;
            }

            if (Rs2Player.isAnimating() || Rs2Player.isInteracting())
            {
                sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isInteracting(), 15_000);
            }

            String rawFish = firstCookableRawFish();
            if (rawFish == null)
            {
                return;
            }

            status = "Cooking " + rawFish;

            /*
             * Direct item -> object interaction.
             * Works for both normal Fires and Forester's Campfires because the
             * actual object id discovered from the cache is used here.
             */
            Integer fireId = Microbot.getClientThread().invoke(fire::getId);
            if (fireId == null)
            {
                fireAvailable = false;
                lastFirePoint = null;
                status = "Fire disappeared";
                return;
            }

            Rs2Inventory.useUnNotedItemOnObject(rawFish, fireId);

            boolean cookWidget = sleepUntil(
                    () -> Rs2Widget.findWidget("How many would you like to cook?", null) != null,
                    3_500
            );

            if (!cookWidget)
            {
                /*
                 * Campfire may have despawned between discovery and interaction.
                 * Re-query next loop rather than spam-clicking the stale object.
                 */
                status = "Fire changed - finding another";
                sleep(1_000, 1_600);
                return;
            }

            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);

            Rs2Player.waitForXpDrop(Skill.COOKING, true);
            sleepUntil(() -> Rs2Player.getAnimation() != AnimationID.IDLE, 5_000);

            final String cookingThis = rawFish;
            sleepUntilTrue(
                    () -> !Rs2Inventory.hasItem(cookingThis)
                            && !Rs2Player.isAnimating(3_500),
                    500,
                    150_000
            );
        }

        status = "Cooking complete";
    }

    private void handleBanking()
    {
        if (Rs2Bank.isOpen())
        {
            state = KspDirectFishingState.BANKING;
            bankInventoryAndRestock();
            return;
        }

        if (config.directBankFirst() && Rs2Bank.isNearBank(DIRECT_BANK_DISTANCE))
        {
            state = KspDirectFishingState.BANKING;
            status = "Direct click: bank";
            Rs2Bank.openBank();
            sleepUntil(Rs2Bank::isOpen, 5_000);
            return;
        }

        state = KspDirectFishingState.WALKING_TO_BANK;
        status = "Walking to nearest bank";
        Rs2Bank.walkToBank();
        Rs2Player.waitForWalking();

        if (Rs2Bank.isNearBank(DIRECT_BANK_DISTANCE))
        {
            status = "Direct click: bank";
            Rs2Bank.openBank();
            sleepUntil(Rs2Bank::isOpen, 5_000);
        }
    }

    private void bankInventoryAndRestock()
    {
        status = "Banking fish";

        Set<Integer> keepIds = new HashSet<>();

        for (String item : mode.getRequiredItems())
        {
            Rs2ItemModel invItem = Rs2Inventory.get(item);
            if (invItem != null)
            {
                keepIds.add(invItem.getId());
            }
        }

        if (keepIds.isEmpty())
        {
            Rs2Bank.depositAll();
        }
        else
        {
            Rs2Bank.depositAllExcept(keepIds.toArray(new Integer[0]));
        }

        Rs2Inventory.waitForInventoryChanges(1_500);

        if (mode == KspDirectFishingMode.SHRIMP_ANCHOVIES)
        {
            if (!Rs2Inventory.hasItem("Small fishing net"))
            {
                if (!Rs2Bank.hasItem("Small fishing net"))
                {
                    failAndStop("Small fishing net not found in bank");
                    return;
                }

                status = "Withdrawing small fishing net";
                Rs2Bank.withdrawOne("Small fishing net");
                sleepUntil(() -> Rs2Inventory.hasItem("Small fishing net"), 3_500);
            }
        }
        else
        {
            if (!Rs2Inventory.hasItem("Fishing rod"))
            {
                if (!Rs2Bank.hasItem("Fishing rod"))
                {
                    failAndStop("Fishing rod not found in bank");
                    return;
                }

                status = "Withdrawing fishing rod";
                Rs2Bank.withdrawOne("Fishing rod");
                sleepUntil(() -> Rs2Inventory.hasItem("Fishing rod"), 3_500);
            }

            if (!Rs2Inventory.hasItem("Fishing bait"))
            {
                if (!Rs2Bank.hasItem("Fishing bait"))
                {
                    failAndStop("Fishing bait not found in bank");
                    return;
                }

                status = "Withdrawing fishing bait";
                Rs2Bank.withdrawAll("Fishing bait");
                sleepUntil(() -> Rs2Inventory.hasItem("Fishing bait"), 3_500);
            }
        }

        if (!hasFishingSupplies())
        {
            failAndStop("Could not obtain required fishing supplies");
            return;
        }

        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 3_000);

        state = KspDirectFishingState.RETURNING_TO_FISH;
        status = "Returning to fishing spot";
    }

    private void handleReturnToFishing()
    {
        Rs2NpcModel visibleSpot = findNearestFishingSpot();
        if (visibleSpot != null)
        {
            state = KspDirectFishingState.FISHING;
            status = "Fishing spot in range";
            return;
        }

        if (fishingAnchor == null)
        {
            status = "Start near a compatible fishing spot";
            return;
        }

        if (Rs2Player.getWorldLocation().distanceTo(fishingAnchor) > 5)
        {
            state = KspDirectFishingState.RETURNING_TO_FISH;
            status = "Walking back to fishing";
            Rs2Walker.walkTo(fishingAnchor);
            return;
        }

        status = "Waiting for fishing spot";
    }

    private Rs2NpcModel findNearestFishingSpot()
    {
        int[] ids = mode.getFishingSpotIds();

        try
        {
            return Microbot.getRs2NpcCache()
                    .query()
                    .where(npc -> Arrays.stream(ids).anyMatch(id -> npc.getId() == id))
                    .nearestOnClientThread();
        }
        catch (RuntimeException ex)
        {
            if (Thread.currentThread().isInterrupted())
            {
                return null;
            }

            log.debug("Fishing spot query failed: {}", ex.getMessage());
            return null;
        }
    }

    private Rs2TileObjectModel findNearestFire()
    {
        int[] fireIds = {
                ObjectID.FIRE,
                ObjectID.FORESTRY_FIRE,
                ObjectID.EAGLEPEAK_CAMPFIRE_TIDY,
                ObjectID.FIRE_COOK
        };

        try
        {
            Rs2TileObjectModel fire = Microbot.getRs2TileObjectCache()
                    .query()
                    .withIds(fireIds)
                    .within(config.fireSearchRadius())
                    .nearestOnClientThread();

            fireAvailable = fire != null;
            lastFirePoint = fire == null ? null : getTileObjectWorldPoint(fire);
            return fire;
        }
        catch (RuntimeException ex)
        {
            fireAvailable = false;
            lastFirePoint = null;

            if (Thread.currentThread().isInterrupted())
            {
                return null;
            }

            log.debug("Fire query failed: {}", ex.getMessage());
            return null;
        }
    }

    private WorldPoint getTileObjectWorldPoint(Rs2TileObjectModel object)
    {
        if (object == null)
        {
            return null;
        }

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
        if (mode == KspDirectFishingMode.SHRIMP_ANCHOVIES)
        {
            return Rs2Inventory.hasItem("Small fishing net");
        }

        return Rs2Inventory.hasItem("Fishing rod")
                && Rs2Inventory.hasItem("Fishing bait");
    }

    private boolean hasRawFish()
    {
        for (String raw : mode.getRawFish())
        {
            if (Rs2Inventory.hasItem(raw))
            {
                return true;
            }
        }

        return false;
    }

    private boolean hasCookableRawFish()
    {
        return firstCookableRawFish() != null;
    }

    private String firstCookableRawFish()
    {
        for (String raw : mode.getRawFish())
        {
            if (!Rs2Inventory.hasItem(raw))
            {
                continue;
            }

            if (Rs2Player.getSkillRequirement(Skill.COOKING, requiredCookingLevel(raw)))
            {
                return raw;
            }
        }

        return null;
    }

    private int requiredCookingLevel(String rawFish)
    {
        if ("Raw herring".equalsIgnoreCase(rawFish))
        {
            return 5;
        }

        // Shrimps, anchovies and sardines are cookable from level 1.
        return 1;
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
