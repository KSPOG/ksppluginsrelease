package net.runelite.client.plugins.microbot.kspkebabbuyer;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.kspbank.KspVerifiedBank;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
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
    private static final int INVENTORY_TIMEOUT_MS = 2_500;
    private static final long TALK_RETRY_MS = 4_000L;
    private static final long OPTION_CONFIRM_TIMEOUT_MS = 4_500L;
    private static final long DIALOGUE_ACTION_DELAY_MS = 650L;
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
    private long nextTalkAttemptAt;
    private long nextDialogueActionAt;
    private long purchaseOptionSelectedAt;
    private long lastDiagnosticAt;
    private int lastObservedKebabCount = -1;

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
                recordPurchasedKebabs();
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
            if (player != null && !Rs2Player.isMoving())
            {
                Rs2Walker.walkTo(destination == null ? KARIM_TILE : destination, 2);
            }
            return;
        }

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

        status = "Karim option not recognized";
        if (now - lastDiagnosticAt >= 4_000L)
        {
            StringBuilder labels = new StringBuilder();
            for (Widget option : options)
            {
                if (option != null)
                {
                    labels.append('[').append(option.getText()).append("] ");
                }
            }
            diagnostic("Unexpected Karim dialogue options: " + labels);
        }
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

        if (hasNonCoinInventory())
        {
            status = "Depositing non-coin items";
            if (!Rs2Bank.depositAllExcept(true, COINS_NAME))
            {
                return;
            }

            if (!sleepUntil(() -> !hasNonCoinInventory(), INVENTORY_TIMEOUT_MS))
            {
                status = "Waiting for bank deposit";
                return;
            }

            if (kebabsBefore > 0)
            {
                bankTrips++;
            }
        }

        // Keep coins in the inventory between bank trips. Only withdraw from
        // the bank when the inventory actually has no coins left.
        if (coinCount() <= 0)
        {
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
        }

        refreshSnapshot();

        status = "Closing bank";
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 1_500);
        nextBankAttemptAt = 0L;
        nextTalkAttemptAt = 0L;
        nextDialogueActionAt = 0L;
        purchaseOptionSelectedAt = 0L;
        lastObservedKebabCount = kebabCount();
        status = "Walking to Karim";
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
        nextTalkAttemptAt = 0L;
        nextDialogueActionAt = 0L;
        purchaseOptionSelectedAt = 0L;
        lastDiagnosticAt = 0L;
        lastObservedKebabCount = -1;
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
