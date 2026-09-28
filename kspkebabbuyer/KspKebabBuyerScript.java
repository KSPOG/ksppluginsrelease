package net.runelite.client.plugins.microbot.kspkebabbuyer;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import javax.inject.Inject;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Slf4j
public class KspKebabBuyerScript extends Script
{
    private final KspKebabBuyerPlugin plugin;

    @Inject
    public KspKebabBuyerScript(KspKebabBuyerPlugin plugin)
    {
        this.plugin = plugin;
    }

    // OSRS Wiki / cache data:
    // Karim = NPC 2877 @ 3274,3181,0. Kebab = item 1971. Price from Karim = 1 coin.
    private static final int KARIM_ID = 2877;
    private static final int KEBAB_ID = 1971;
    private static final String COINS_NAME = "Coins";
    private static final WorldPoint KARIM_TILE = new WorldPoint(3274, 3181, 0);
    private static final WorldPoint AL_KHARID_BANK_TILE = new WorldPoint(3270, 3166, 0);

    private static final int KEBAB_BUY_PRICE = 1;
    private static final long TALK_RETRY_MS = 1_300L;
    private static final long OPTION_CONFIRM_TIMEOUT_MS = 2_400L;
    private static final long DIALOGUE_ACTION_DELAY_MS = 180L;
    private static final long LOOP_DELAY_MS = 100L;
    private static final long WEBWALK_RETRY_MS = 3_000L;
    private static final long WEBWALK_STALL_MS = 30_000L;
    private static final int WEBWALK_MAX_STALL_RECOVERIES = 3;
    private static final int KARIM_REACHED_DISTANCE = 4;
    private static final int BANK_REACHED_DISTANCE = 2;
    private static final long BANK_INTERACTION_RETRY_MS = 2_200L;
    private static final long BANK_OPEN_TIMEOUT_MS = 45_000L;
    private static final long DIALOGUE_STALL_MS = 5_000L;
    private static final long DEPOSIT_RETRY_MS = 900L;
    private static final long WITHDRAW_RETRY_MS = 1_000L;
    private static final long BANK_CLOSE_RETRY_MS = 600L;
    private static final long PRICE_REFRESH_MS = 30_000L;

    private volatile String status = "Starting";
    private volatile long startedAtMs;
    private volatile long kebabsBought;
    private volatile long bankTrips;
    private volatile int inventoryKebabs;
    private volatile int coinsRemaining;
    private volatile int kebabGePrice;
    private volatile String navigationDetails = "";

    private long nextBankAttemptAt;
    private long nextDepositAttemptAt;
    private long nextWithdrawAttemptAt;
    private long nextBankCloseAttemptAt;
    private boolean bankTripHadKebabs;
    private long lastPriceRefreshAt;
    private long nextTalkAttemptAt;
    private long nextDialogueActionAt;
    private long purchaseOptionSelectedAt;
    private long lastDiagnosticAt;
    private int lastObservedKebabCount = -1;
    // Rs2Walker.walkTo is blocking: run a single webwalk off the 100ms buyer loop.
    private ExecutorService webWalkExecutor;
    private Future<Boolean> webWalkTask;
    private WorldPoint webWalkTarget;
    private boolean webWalkToBank;
    private WorldPoint lastNavigationPosition;
    private long lastNavigationProgressAt;
    private long nextWebWalkAttemptAt;
    private int webWalkStallRecoveries;
    private long bankOpenStartedAt;
    private int bankClickAttempts;
    private long staleDialogueStartedAt;
    private int staleDialogueContinueAttempts;

    public boolean run()
    {
        if (webWalkExecutor == null || webWalkExecutor.isShutdown())
        {
            webWalkExecutor = Executors.newSingleThreadExecutor(task ->
            {
                Thread worker = new Thread(task, "KspKebabBuyer-Webwalker");
                worker.setDaemon(true);
                return worker;
            });
        }
        resetSession();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            try
            {
                if (!Microbot.isLoggedIn() || !super.run())
                {
                    return;
                }

                refreshSnapshot();
                recordPurchasedKebabs();
                refreshPriceIfNeeded();
                process();
            }
            catch (Exception ex)
            {
                // Source Loader can interrupt a script while unloading it.
                // ActorModel.getWorldLocation() then wraps InterruptedException.
                for (Throwable cause = ex; cause != null; cause = cause.getCause())
                {
                    if (cause instanceof InterruptedException)
                    {
                        Thread.currentThread().interrupt();
                        log.debug("KSP Kebab Buyer interrupted during plugin reload/shutdown");
                        return;
                    }
                }
                status = "Error - check client log";
                log.error("KSP Kebab Buyer loop error", ex);
            }
        }, 0L, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);

        return true;
    }

    private void process()
    {
        if (Rs2Bank.isOpen())
        {
            if (bankOpenStartedAt > 0L)
            {
                log.info("KSP Kebab Buyer: bank widget verified open after {}ms and {} attempted clicks",
                        System.currentTimeMillis() - bankOpenStartedAt, bankClickAttempts);
            }
            resetNavigation();
            handleOpenBank();
            return;
        }

        if (nextBankCloseAttemptAt > 0L)
        {
            resetBankInteractionState();
        }

        if (coinsRemaining <= 0 || Rs2Inventory.isFull())
        {
            // A bank trip is the only time a partially completed conversation
            // should be dismissed. Never cancel Karim's dialogue while buying.
            if (Rs2Dialogue.isInDialogue())
            {
                status = "Closing dialogue for bank trip";
                Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
                return;
            }
            openBank();
            return;
        }

        WorldPoint player = Rs2Player.getWorldLocation();
        Rs2NpcModel karim = findKarim();
        WorldPoint destination = karim == null ? KARIM_TILE : karim.getWorldLocation();
        if (player == null || destination == null
                || player.getPlane() != destination.getPlane()
                || player.distanceTo(destination) > 4)
        {
            status = "Walking to Karim";
            if (Rs2Dialogue.isInDialogue())
            {
                Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
                return;
            }
            if (player != null)
            {
                webWalk(player, destination == null ? KARIM_TILE : destination,
                        KARIM_REACHED_DISTANCE, false);
            }
            return;
        }

        cancelWebWalk("arrived at Karim");

        // Karim's dialogue can start with either a continue widget or the
        // purchase options. Resume whichever stage the game is actually showing.
        if (Rs2Dialogue.hasSelectAnOption())
        {
            selectKebabOption();
            return;
        }

        if (Rs2Dialogue.hasContinue())
        {
            long now = System.currentTimeMillis();
            if (now >= nextDialogueActionAt)
            {
                status = "Continuing Karim dialogue";
                Rs2Dialogue.clickContinue();
                nextDialogueActionAt = now + DIALOGUE_ACTION_DELAY_MS;
            }
            return;
        }

        long now = System.currentTimeMillis();
        if (purchaseOptionSelectedAt > 0L)
        {
            if (now - purchaseOptionSelectedAt < OPTION_CONFIRM_TIMEOUT_MS)
            {
                status = "Waiting for kebab purchase";
                return;
            }
            purchaseOptionSelectedAt = 0L;
            diagnostic("Purchase option selected, but no kebab received; retrying Karim");
        }

        if (now < nextTalkAttemptAt)
        {
            status = "Waiting for Karim dialogue";
            return;
        }

        if (karim == null)
        {
            status = "Karim not loaded - searching for NPC";
            diagnostic("Karim NPC missing near " + player + " (expected ID " + KARIM_ID + ")");
            nextTalkAttemptAt = now + 1_500L;
            return;
        }

        status = "Talking to Karim";
        boolean clicked = Rs2Npc.interact(karim, "Talk-to");
        nextTalkAttemptAt = now + (clicked ? TALK_RETRY_MS : 1_500L);
        if (!clicked)
        {
            status = "Karim interaction failed - retrying";
            diagnostic("Talk-to failed for Karim id=" + karim.getId()
                    + " at " + karim.getWorldLocation());
            return;
        }
        status = "Waiting for Karim dialogue";
    }

    private Rs2NpcModel findKarim()
    {
        Rs2NpcModel npc = Rs2Npc.getNpc(KARIM_ID);
        if (npc != null && "Karim".equalsIgnoreCase(npc.getName()))
        {
            return npc;
        }
        // Name fallback handles NPC cache/variant changes without ever clicking
        // a different character just because they occupy the expected tile.
        return Rs2Npc.getNpc("Karim", true);
    }

    private void selectKebabOption()
    {
        long now = System.currentTimeMillis();
        if (now < nextDialogueActionAt)
        {
            return;
        }

        List<Widget> options = Rs2Dialogue.getDialogueOptions();
        for (int i = 0; i < options.size(); i++)
        {
            Widget option = options.get(i);
            String label = option == null || option.getText() == null ? ""
                    : option.getText().replaceAll("<[^>]+>", "")
                            .trim().toLowerCase(Locale.ROOT);

            // Karim's standard offer is "Yes please". Check the live option
            // text instead of blindly pressing 2 (quest dialogue can vary).
            boolean affirmative = label.contains("yes")
                    || label.contains("sure")
                    || label.contains("buy a kebab")
                    || label.contains("have a kebab")
                    || label.contains("one kebab");
            boolean decline = label.contains("miss")
                    || label.contains("not ")
                    || label.contains("no ")
                    || label.contains("don't");

            if (!affirmative || decline)
            {
                continue;
            }

            staleDialogueStartedAt = 0L;
            staleDialogueContinueAttempts = 0;
            status = "Buying kebab: " + label;
            if (Rs2Dialogue.keyPressForDialogueOption(i + 1))
            {
                purchaseOptionSelectedAt = now;
                nextDialogueActionAt = now + DIALOGUE_ACTION_DELAY_MS;
                nextTalkAttemptAt = now + TALK_RETRY_MS;
            }
            else
            {
                status = "Kebab dialogue option failed - retrying";
                diagnostic("Unable to select Karim dialogue option: " + label);
            }
            return;
        }

        // A stale option widget can survive after selecting "Yes" while the
        // actual choice is replaced by "Please wait...". Previously this
        // branch returned forever because hasSelectAnOption() stayed true.
        if (staleDialogueStartedAt == 0L)
        {
            staleDialogueStartedAt = now;
        }
        status = "Waiting for Karim dialogue";
        if (now - staleDialogueStartedAt < DIALOGUE_STALL_MS
                || (purchaseOptionSelectedAt > 0L
                    && now - purchaseOptionSelectedAt < OPTION_CONFIRM_TIMEOUT_MS))
        {
            return;
        }

        StringBuilder labels = new StringBuilder();
        for (Widget option : options)
        {
            if (option != null)
            {
                labels.append('[').append(option.getText()).append("] ");
            }
        }
        diagnostic("Resetting unrecognized Karim dialogue after "
                + (now - staleDialogueStartedAt) + "ms: " + labels);
        if (Rs2Dialogue.hasContinue() && staleDialogueContinueAttempts < 2)
        {
            status = "Continuing stalled dialogue";
            staleDialogueContinueAttempts++;
            Rs2Dialogue.clickContinue();
        }
        else
        {
            status = "Restarting stalled Karim dialogue";
            staleDialogueContinueAttempts = 0;
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
            nextTalkAttemptAt = now + TALK_RETRY_MS;
            purchaseOptionSelectedAt = 0L;
        }
        nextDialogueActionAt = now + 750L;
        staleDialogueStartedAt = 0L;
    }

    private void recordPurchasedKebabs()
    {
        int current = kebabCount();
        if (lastObservedKebabCount >= 0 && current > lastObservedKebabCount)
        {
            int bought = current - lastObservedKebabCount;
            kebabsBought += bought;
            purchaseOptionSelectedAt = 0L;
            nextTalkAttemptAt = 0L;
            nextDialogueActionAt = 0L;
            staleDialogueStartedAt = 0L;
            staleDialogueContinueAttempts = 0;
            status = Rs2Inventory.isFull() ? "Inventory full - banking"
                    : "Purchased " + bought + " kebab" + (bought == 1 ? "" : "s");
        }
        lastObservedKebabCount = current;
    }

    private void diagnostic(String message)
    {
        long now = System.currentTimeMillis();
        if (now - lastDiagnosticAt >= 4_000L)
        {
            log.warn("KSP Kebab Buyer: {}", message);
            lastDiagnosticAt = now;
        }
    }

    private void openBank()
    {
        long now = System.currentTimeMillis();
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null)
        {
            status = "Waiting for player location";
            return;
        }

        if (player.getPlane() != AL_KHARID_BANK_TILE.getPlane()
                || player.distanceTo(AL_KHARID_BANK_TILE) > BANK_REACHED_DISTANCE)
        {
            bankOpenStartedAt = 0L;
            bankClickAttempts = 0;
            webWalk(player, AL_KHARID_BANK_TILE, BANK_REACHED_DISTANCE, true);
            return;
        }
        cancelWebWalk("arrived at Al Kharid bank");

        if (bankOpenStartedAt == 0L)
        {
            bankOpenStartedAt = now;
        }
        if (now - bankOpenStartedAt >= BANK_OPEN_TIMEOUT_MS)
        {
            status = "Bank interface unavailable - stopped";
            log.error("KSP Kebab Buyer: bank widget never opened after {} click attempts at {}",
                    bankClickAttempts, player);
            Microbot.showMessage("KSP Kebab Buyer: bank did not open. Check client log.");
            Microbot.stopPlugin(plugin);
            return;
        }

        navigationDetails = "Bank@" + player.getX() + "," + player.getY()
                + " attempts=" + bankClickAttempts;

        if (Rs2Player.isMoving())
        {
            status = "Approaching Al Kharid bank";
            return;
        }
        if (now < nextBankAttemptAt)
        {
            // A click is not proof that the bank opened. Check the widget in
            // process() on every iteration and wait for it before trying again.
            return;
        }

        if (Rs2Inventory.isItemSelected())
        {
            status = "Clearing selected item";
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
            nextBankAttemptAt = now + 500L;
            return;
        }

        // The previous code always clicked the first Bank booth. A successful
        // click result did not mean the interface opened, so it retried that
        // same (possibly unreachable) booth indefinitely. Prefer a Banker,
        // verify the widget after the click, and alternate with a reachable
        // booth when an interaction does not open it.
        boolean preferBanker = (bankClickAttempts / 2) % 2 == 0;
        boolean clicked = false;
        String target = "none";
        if (preferBanker)
        {
            Rs2NpcModel banker = Rs2Npc.getBankerNPC();
            if (banker != null && banker.getWorldLocation() != null
                    && player.distanceTo(banker.getWorldLocation()) <= 10)
            {
                clicked = Rs2Npc.interact(banker, "Bank");
                if (clicked)
                {
                    target = "Banker";
                }
            }
        }

        if (!clicked)
        {
            GameObject booth = Rs2GameObject.findReachableObject(
                    "Bank booth", true, 10, player, true, "Bank");
            if (booth != null && booth.getWorldLocation() != null
                    && player.distanceTo(booth.getWorldLocation()) <= 10)
            {
                clicked = Rs2GameObject.interact(booth, "Bank");
                if (clicked)
                {
                    target = "reachable booth";
                }
            }
        }

        if (!clicked && !preferBanker)
        {
            Rs2NpcModel banker = Rs2Npc.getBankerNPC();
            if (banker != null && banker.getWorldLocation() != null
                    && player.distanceTo(banker.getWorldLocation()) <= 10)
            {
                clicked = Rs2Npc.interact(banker, "Bank");
                if (clicked)
                {
                    target = "Banker fallback";
                }
            }
        }

        bankClickAttempts++;
        status = clicked ? "Waiting for bank widget" : "No reachable bank target";
        navigationDetails = target + "@" + player.getX() + "," + player.getY()
                + " try " + bankClickAttempts;
        nextBankAttemptAt = now + (clicked ? BANK_INTERACTION_RETRY_MS : 900L);

        if (!clicked || bankClickAttempts % 2 == 0)
        {
            log.warn("KSP Kebab Buyer: bank still closed after attempt {} at {}, target={}, clickIssued={}",
                    bankClickAttempts, player, target, clicked);
        }
    }

    /**
     * Both directions use Microbot's actual webwalker (Rs2Walker.walkTo).
     * One dedicated worker owns a route until arrival, failure, or stall; the
     * buying/banking scheduled loop remains responsive while it runs.
     */
    private void webWalk(WorldPoint player, WorldPoint target, int reachedDistance, boolean toBank)
    {
        long now = System.currentTimeMillis();
        if (webWalkExecutor == null || webWalkExecutor.isShutdown())
        {
            status = "Webwalker unavailable";
            log.error("KSP Kebab Buyer: webwalker executor is not running");
            Microbot.stopPlugin(plugin);
            return;
        }

        if (webWalkTarget == null || !webWalkTarget.equals(target)
                || webWalkToBank != toBank)
        {
            cancelWebWalk("new destination");
            webWalkTarget = target;
            webWalkToBank = toBank;
            lastNavigationPosition = player;
            lastNavigationProgressAt = now;
            nextWebWalkAttemptAt = 0L;
            webWalkStallRecoveries = 0;
            log.info("KSP Kebab Buyer: webwalking to {} ({})",
                    toBank ? "Al Kharid bank" : "Karim", target);
        }

        if (!player.equals(lastNavigationPosition))
        {
            lastNavigationPosition = player;
            lastNavigationProgressAt = now;
            webWalkStallRecoveries = 0;
        }

        int remaining = player.getPlane() == target.getPlane()
                ? player.distanceTo(target) : Integer.MAX_VALUE;
        if (remaining <= reachedDistance)
        {
            cancelWebWalk("destination in range");
            return;
        }

        navigationDetails = (toBank ? "Web: bank " : "Web: Karim ")
                + (remaining == Integer.MAX_VALUE ? "other plane" : remaining + " tiles")
                + (webWalkStallRecoveries > 0 ? " retry " + webWalkStallRecoveries : "");
        status = toBank ? "Webwalking to Al Kharid bank" : "Webwalking to Karim";

        if (now - lastNavigationProgressAt >= WEBWALK_STALL_MS
                && now >= nextWebWalkAttemptAt)
        {
            webWalkStallRecoveries++;
            log.warn("KSP Kebab Buyer: webwalker stalled at {} toward {}, attempt {}/{}",
                    player, target, webWalkStallRecoveries, WEBWALK_MAX_STALL_RECOVERIES);
            if (webWalkStallRecoveries >= WEBWALK_MAX_STALL_RECOVERIES)
            {
                cancelWebWalk("stalled too many times");
                status = "Webwalker failed - stopped";
                Microbot.showMessage("KSP Kebab Buyer: webwalker stalled repeatedly. Check client log.");
                Microbot.stopPlugin(plugin);
                return;
            }
            // Cancel this walk, including its pathfinder, before retrying.
            // Do not inject manual minimap/canvas clicks or route waypoints.
            cancelActiveWebWalk("stall recovery");
            lastNavigationProgressAt = now;
            nextWebWalkAttemptAt = now + WEBWALK_RETRY_MS;
            status = "Recalculating webwalker route";
            return;
        }

        if (webWalkTask != null)
        {
            if (!webWalkTask.isDone())
            {
                return; // Never submit another route while the current one runs.
            }

            try
            {
                boolean arrived = webWalkTask.get();
                log.info("KSP Kebab Buyer: webwalker completed target={}, arrived={}",
                        target, arrived);
            }
            catch (CancellationException ignored)
            {
                log.debug("KSP Kebab Buyer: previous webwalk cancelled");
            }
            catch (InterruptedException ex)
            {
                Thread.currentThread().interrupt();
                return;
            }
            catch (ExecutionException ex)
            {
                log.warn("KSP Kebab Buyer: webwalker failed target={}", target, ex.getCause());
            }
            webWalkTask = null;
            nextWebWalkAttemptAt = now + WEBWALK_RETRY_MS;
        }

        if (now < nextWebWalkAttemptAt)
        {
            return;
        }

        final WorldPoint destination = target;
        final int distance = reachedDistance;
        nextWebWalkAttemptAt = now + WEBWALK_RETRY_MS;
        webWalkTask = webWalkExecutor.submit(() -> Rs2Walker.walkTo(destination, distance));
    }

    private void cancelActiveWebWalk(String reason)
    {
        if (webWalkTask != null && !webWalkTask.isDone())
        {
            webWalkTask.cancel(true);
        }
        webWalkTask = null;
        // The global walker also owns the pathfinder. Clear only the route
        // matching the destination this plugin submitted.
        if (webWalkTarget != null && webWalkTarget.equals(Rs2Walker.getCurrentTarget()))
        {
            Rs2Walker.clearWalkingRoute("kebab-buyer:" + reason);
        }
    }

    private void cancelWebWalk(String reason)
    {
        if (webWalkTarget != null)
        {
            cancelActiveWebWalk(reason);
        }
        webWalkTarget = null;
        lastNavigationPosition = null;
        lastNavigationProgressAt = 0L;
        nextWebWalkAttemptAt = 0L;
        webWalkStallRecoveries = 0;
    }

    private void resetNavigation()
    {
        cancelWebWalk("reset");
        navigationDetails = "";
        bankOpenStartedAt = 0L;
        bankClickAttempts = 0;
    }

    private void handleOpenBank()
    {
        long now = System.currentTimeMillis();
        if (kebabCount() > 0)
        {
            bankTripHadKebabs = true;
        }

        if (hasNonCoinInventory())
        {
            status = "Depositing everything except coins";
            if (now >= nextDepositAttemptAt)
            {
                boolean clicked = Rs2Bank.depositAllExcept(true, COINS_NAME);
                nextDepositAttemptAt = now + (clicked ? DEPOSIT_RETRY_MS : 400L);
                if (!clicked)
                {
                    diagnostic("Bank deposit failed; retrying");
                }
            }
            return;
        }

        if (bankTripHadKebabs)
        {
            bankTrips++;
            bankTripHadKebabs = false;
        }

        // The coin stack stays in inventory across ordinary bank trips.
        // Only withdraw when it actually ran out; verify on later loops rather
        // than blocking the script for a 2.5-second inventory wait.
        if (coinCount() <= 0)
        {
            status = "Restocking coins";
            if (now < nextWithdrawAttemptAt)
            {
                return;
            }

            if (!Rs2Bank.setWithdrawAsItem())
            {
                nextWithdrawAttemptAt = now + 500L;
                return;
            }

            int bankCoins = Rs2Bank.count(COINS_NAME, true);
            if (bankCoins <= 0)
            {
                status = "Out of coins - stopping";
                log.info("KSP Kebab Buyer stopped: no coins remain in inventory or bank");
                Microbot.showMessage("KSP Kebab Buyer: out of coins - stopping.");
                Microbot.stopPlugin(plugin);
                return;
            }

            if (Rs2Bank.withdrawAll(COINS_NAME, true))
            {
                status = "Withdrawing all coins";
                nextWithdrawAttemptAt = now + WITHDRAW_RETRY_MS;
            }
            else
            {
                status = "Coin withdrawal failed - retrying";
                nextWithdrawAttemptAt = now + 500L;
            }
            return;
        }

        status = "Closing bank";
        if (now >= nextBankCloseAttemptAt)
        {
            Rs2Bank.closeBank();
            nextBankCloseAttemptAt = now + BANK_CLOSE_RETRY_MS;
        }
        if (!Rs2Bank.isOpen())
        {
            resetBankInteractionState();
            status = "Returning to Karim";
        }
    }

    private void resetBankInteractionState()
    {
        nextBankAttemptAt = 0L;
        nextDepositAttemptAt = 0L;
        nextWithdrawAttemptAt = 0L;
        nextBankCloseAttemptAt = 0L;
        nextTalkAttemptAt = 0L;
        nextDialogueActionAt = 0L;
        purchaseOptionSelectedAt = 0L;
        staleDialogueStartedAt = 0L;
        staleDialogueContinueAttempts = 0;
        lastObservedKebabCount = kebabCount();
    }

    private boolean hasNonCoinInventory()
    {
        return Rs2Inventory.all().stream()
                .anyMatch(item -> item != null && !COINS_NAME.equalsIgnoreCase(item.getName()));
    }

    private void refreshSnapshot()
    {
        inventoryKebabs = Math.max(0, kebabCount());
        coinsRemaining = Math.max(0, coinCount());
    }

    private void refreshPriceIfNeeded()
    {
        long now = System.currentTimeMillis();
        if (now - lastPriceRefreshAt < PRICE_REFRESH_MS)
        {
            return;
        }

        long livePrice = Microbot.getClientThread()
                .runOnClientThreadOptional(() -> (long) Microbot.getItemManager().getItemPrice(KEBAB_ID))
                .orElse(0L);
        kebabGePrice = livePrice <= 0L ? 0 : (int) Math.min(Integer.MAX_VALUE, livePrice);
        lastPriceRefreshAt = now;
    }

    private int kebabCount()
    {
        return Rs2Inventory.itemQuantity(KEBAB_ID);
    }

    private int coinCount()
    {
        return Rs2Inventory.itemQuantity(COINS_NAME, true);
    }

    private void resetSession()
    {
        startedAtMs = System.currentTimeMillis();
        kebabsBought = 0L;
        bankTrips = 0L;
        inventoryKebabs = 0;
        coinsRemaining = 0;
        kebabGePrice = 0;
        nextBankAttemptAt = 0L;
        nextDepositAttemptAt = 0L;
        nextWithdrawAttemptAt = 0L;
        nextBankCloseAttemptAt = 0L;
        bankTripHadKebabs = false;
        lastPriceRefreshAt = 0L;
        nextTalkAttemptAt = 0L;
        nextDialogueActionAt = 0L;
        purchaseOptionSelectedAt = 0L;
        lastDiagnosticAt = 0L;
        lastObservedKebabCount = -1;
        staleDialogueStartedAt = 0L;
        staleDialogueContinueAttempts = 0;
        resetNavigation();
        status = "Starting";
    }

    @Override
    public void shutdown()
    {
        cancelWebWalk("plugin shutdown");
        if (webWalkExecutor != null)
        {
            webWalkExecutor.shutdownNow();
            webWalkExecutor = null;
        }
        super.shutdown();
        status = "Stopped";
    }

    public String getStatus()
    {
        return status;
    }

    public String getNavigationDetails()
    {
        return navigationDetails;
    }

    public long getRuntimeMs()
    {
        return startedAtMs <= 0L ? 0L : Math.max(0L, System.currentTimeMillis() - startedAtMs);
    }

    public long getKebabsBought()
    {
        return kebabsBought;
    }

    public long getKebabsPerHour()
    {
        long runtime = getRuntimeMs();
        return runtime <= 0L ? 0L : Math.round(kebabsBought * 3_600_000.0D / runtime);
    }

    public long getBankTrips()
    {
        return bankTrips;
    }

    public int getInventoryKebabs()
    {
        return inventoryKebabs;
    }

    public int getCoinsRemaining()
    {
        return coinsRemaining;
    }

    public int getKebabGePrice()
    {
        return kebabGePrice;
    }

    public long getGpSpent()
    {
        return kebabsBought * KEBAB_BUY_PRICE;
    }

    public long getEstimatedProfit()
    {
        if (kebabGePrice <= 0)
        {
            return 0L;
        }
        return kebabsBought * (long) Math.max(0, kebabGePrice - KEBAB_BUY_PRICE);
    }

    public long getEstimatedProfitPerHour()
    {
        long runtime = getRuntimeMs();
        return runtime <= 0L ? 0L : Math.round(getEstimatedProfit() * 3_600_000.0D / runtime);
    }
}
