package net.runelite.client.plugins.microbot.kspkebabbuyer;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.kspbank.KspVerifiedBank;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import javax.inject.Inject;
import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

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

    private static final int KEBAB_BUY_PRICE = 1;
    private static final int DIALOGUE_TIMEOUT_MS = 2_500;
    private static final int INVENTORY_TIMEOUT_MS = 2_500;
    private static final long LOOP_DELAY_MS = 120L;
    private static final long BANK_RETRY_MS = 900L;
    private static final long PRICE_REFRESH_MS = 30_000L;

    private volatile String status = "Starting";
    private volatile long startedAtMs;
    private volatile long kebabsBought;
    private volatile long bankTrips;
    private volatile int inventoryKebabs;
    private volatile int coinsRemaining;
    private volatile int kebabGePrice;

    private long nextBankAttemptAt;
    private long lastPriceRefreshAt;

    public boolean run()
    {
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
                refreshPriceIfNeeded();
                process();
            }
            catch (Exception ex)
            {
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
            handleOpenBank();
            return;
        }

        if (coinsRemaining <= 0 || Rs2Inventory.isFull())
        {
            openBank();
            return;
        }

        if (Rs2Dialogue.isInDialogue())
        {
            status = "Clearing stale dialogue";
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
            sleepUntil(() -> !Rs2Dialogue.isInDialogue(), 800);
            return;
        }

        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null || player.getPlane() != KARIM_TILE.getPlane() || player.distanceTo(KARIM_TILE) > 4)
        {
            status = "Walking to Karim";
            if (!Rs2Player.isMoving())
            {
                Rs2Walker.walkTo(KARIM_TILE, 3);
            }
            return;
        }

        buyOneKebab();
    }

    private void buyOneKebab()
    {
        final int beforeKebabs = kebabCount();
        final int beforeCoins = coinCount();

        if (beforeCoins <= 0)
        {
            status = "Out of coins - banking";
            return;
        }

        status = "Talking to Karim";
        if (!Rs2Npc.interact(KARIM_ID, "Talk-to"))
        {
            status = "Waiting for Karim";
            return;
        }

        if (!sleepUntil(Rs2Dialogue::hasContinue, DIALOGUE_TIMEOUT_MS))
        {
            status = "Waiting for Karim dialogue";
            return;
        }

        // Wiki flow: Karim -> Space -> 2 -> Space.
        status = "Karim: continue";
        Rs2Dialogue.clickContinue();

        if (!sleepUntil(Rs2Dialogue::hasSelectAnOption, DIALOGUE_TIMEOUT_MS))
        {
            status = "Waiting for kebab option";
            return;
        }

        status = "Selecting kebab option";
        Rs2Keyboard.keyPress(KeyEvent.VK_2);

        if (!sleepUntil(Rs2Dialogue::hasContinue, DIALOGUE_TIMEOUT_MS))
        {
            status = "Waiting for purchase confirmation";
            return;
        }

        status = "Confirming kebab purchase";
        Rs2Dialogue.clickContinue();

        if (!sleepUntil(() -> kebabCount() > beforeKebabs, INVENTORY_TIMEOUT_MS))
        {
            status = "Purchase not confirmed - retrying";
            return;
        }

        int bought = Math.max(1, kebabCount() - beforeKebabs);
        kebabsBought += bought;
        refreshSnapshot();
        status = Rs2Inventory.isFull() ? "Inventory full - banking" : "Kebab purchased";

        sleepUntil(() -> !Rs2Dialogue.isInDialogue(), 1_000);
    }

    private void openBank()
    {
        long now = System.currentTimeMillis();
        if (now < nextBankAttemptAt)
        {
            return;
        }

        status = Rs2Inventory.isFull() ? "Walking to bank - inventory full" : "Walking to bank - restocking coins";
        KspVerifiedBank.walkToBankAndOpenBank();
        nextBankAttemptAt = now + BANK_RETRY_MS;
    }

    private void handleOpenBank()
    {
        int kebabsBefore = kebabCount();

        if (!Rs2Inventory.isEmpty())
        {
            status = "Depositing inventory";
            if (!Rs2Bank.depositAll())
            {
                return;
            }

            if (!sleepUntil(Rs2Inventory::isEmpty, INVENTORY_TIMEOUT_MS))
            {
                status = "Waiting for bank deposit";
                return;
            }

            if (kebabsBefore > 0)
            {
                bankTrips++;
            }
        }

        if (!Rs2Bank.setWithdrawAsItem())
        {
            status = "Setting unnoted withdraw mode";
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

        status = "Withdrawing all " + String.format("%,d", bankCoins) + " coins";
        if (!Rs2Bank.withdrawAll(COINS_NAME, true)
                || !sleepUntil(() -> coinCount() >= bankCoins, INVENTORY_TIMEOUT_MS))
        {
            status = "Waiting for all coins";
            return;
        }

        refreshSnapshot();

        status = "Closing bank";
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 1_500);
        nextBankAttemptAt = 0L;
        status = "Walking to Karim";
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

        kebabGePrice = Math.max(0, Microbot.getClientThread()
                .runOnClientThreadOptional(() -> Microbot.getItemManager().getItemPrice(KEBAB_ID))
                .orElse(0));
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
        lastPriceRefreshAt = 0L;
        status = "Starting";
    }

    @Override
    public void shutdown()
    {
        super.shutdown();
        status = "Stopped";
    }

    public String getStatus()
    {
        return status;
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
