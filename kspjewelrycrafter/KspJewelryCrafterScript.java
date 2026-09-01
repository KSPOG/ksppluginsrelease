package net.runelite.client.plugins.microbot.kspjewelrycrafter;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeAction;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeSlots;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
@Singleton
public class KspJewelryCrafterScript extends Script
{
    private static final int LOOP_MS = 650;
    private static final int EDGEVILLE_FURNACE_ID = 16469;
    private static final int EDGEVILLE_DIRECT_BANK_RADIUS = 20;
    private static final int BANK_WIDGET_GROUP = 12;
    private static final int BANK_WIDGET_CHILD = 1;
    private static final int GE_QUANTITY_X_CHILD = 7;
    private static final int GE_PRICE_X_CHILD = 12;
    private static final int GE_SEARCH_GROUP = 162;
    private static final int GE_SEARCH_PROMPT_CHILD = 52;
    private static final int GE_SELECTED_PRICE_CHILD = 41;
    private static final int GE_OFFER_PRICE_VARBIT = 4398;
    private static final int GE_VALUE_ENTRY_ATTEMPTS = 3;
    private static final int GE_PRICE_CLICK_DELAY_MIN_MS = 650;
    private static final int GE_PRICE_CLICK_DELAY_MAX_MS = 950;
    private static final long TARGET_INTERACTION_TIMEOUT_MS = 8_000L;
    private static final WorldPoint EDGEVILLE_BANK = new WorldPoint(3096, 3494, 0);
    private static final WorldPoint EDGEVILLE_FURNACE = new WorldPoint(3109, 3499, 0);
    private static final WorldPoint GRAND_EXCHANGE = new WorldPoint(3164, 3487, 0);

    private KspJewelryCrafterConfig config;
    private final JewelryPriceService prices = new JewelryPriceService();

    private volatile JewelryCrafterState state = JewelryCrafterState.STOPPED;
    private volatile String status = "Stopped";
    private volatile JewelryRecipe activeRecipe;
    private volatile JewelryQuote activeQuote;
    private volatile boolean memberAccount;
    private volatile int craftingLevel;
    private volatile int startingCraftingLevel;
    private volatile long craftingXpGained;
    private volatile long craftedCount;
    private volatile long estimatedProfit;
    private volatile long sessionStartedAt;
    private volatile int lastBatchMade;
    private volatile int currentBatchTarget;

    private PendingOffer pendingOffer;
    private final List<BuyOrder> buyQueue = new ArrayList<>();
    private int geRetry;
    private String outputPendingSale;
    private long waitingUntil;
    private boolean goldMakeAllSelected;
    private boolean silverMakeAllSelected;
    private long bankInteractionSentAt;
    private long furnaceInteractionSentAt;

    private JewelryRecipe monitoredRecipe;
    private int monitoredBars;
    private int monitoredGems;
    private int monitoredOutput;
    private int monitoredBatchMade;
    private long lastCraftingInventoryChangeAt;
    private boolean craftingMonitorPrimed;

    public JewelryCrafterState getState() { return state; }
    public String getStatus() { return status; }
    public JewelryRecipe getActiveRecipe() { return activeRecipe; }
    public JewelryQuote getActiveQuote() { return activeQuote; }
    public boolean isMemberAccount() { return memberAccount; }
    public int getCraftingLevel() { return craftingLevel; }
    public int getCraftingLevelsGained() { return startingCraftingLevel <= 0 ? 0 : Math.max(0, craftingLevel - startingCraftingLevel); }
    public long getCraftingXpGained() { return craftingXpGained; }
    public long getCraftedCount() { return craftedCount; }
    public long getEstimatedProfit() { return estimatedProfit; }
    public long getSessionStartedAt() { return sessionStartedAt; }
    public int getLastBatchMade() { return lastBatchMade; }
    public int getCurrentBatchTarget() { return currentBatchTarget; }

    public boolean run(KspJewelryCrafterConfig config)
    {
        this.config = config;
        state = JewelryCrafterState.STARTING;
        status = "Starting";
        activeRecipe = null;
        activeQuote = null;
        pendingOffer = null;
        buyQueue.clear();
        geRetry = 0;
        outputPendingSale = null;
        waitingUntil = craftingXpGained = craftedCount = estimatedProfit = sessionStartedAt = 0L;
        startingCraftingLevel = lastBatchMade = currentBatchTarget = 0;
        goldMakeAllSelected = silverMakeAllSelected = false;
        bankInteractionSentAt = furnaceInteractionSentAt = 0L;
        resetCraftingMonitor();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            try
            {
                if (!super.run() || !Microbot.isLoggedIn()) return;
                tick();
            }
            catch (Exception ex)
            {
                status = "Error: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
                log.error("KSP Jewelry Crafter tick failed", ex);
            }
        }, 0, LOOP_MS, TimeUnit.MILLISECONDS);
        return true;
    }

    private void tick()
    {
        switch (state)
        {
            case STARTING: start(); break;
            case EVALUATING: evaluate(); break;
            case BANKING: bankForCrafting(); break;
            case CRAFTING: craftInventory(); break;
            case TRAVEL_TO_GE: travelToGe(); break;
            case GE_SELL: sellOutput(); break;
            case GE_BUY: buyInputs(); break;
            case RETURN_TO_FURNACE: returnToFurnace(); break;
            case WAITING: waitState(); break;
            case STOPPED: default: break;
        }
    }

    private void start()
    {
        sessionStartedAt = System.currentTimeMillis();
        refreshAccountEligibility();
        state = JewelryCrafterState.EVALUATING;
        status = "Evaluating profitable recipes";
    }

    private void refreshAccountEligibility()
    {
        int level = Microbot.getClientThread().runOnClientThreadOptional(
            () -> Microbot.getClient().getRealSkillLevel(Skill.CRAFTING)).orElse(-1);
        if (level > 0)
        {
            craftingLevel = level;
            if (startingCraftingLevel <= 0) startingCraftingLevel = level;
        }
        try { memberAccount = Rs2Player.isMember(); }
        catch (Exception ex) { memberAccount = false; }
    }

    private void evaluate()
    {
        refreshAccountEligibility();
        if (activeRecipe != null && activeRecipe.isEligible(craftingLevel, memberAccount))
        {
            activeQuote = prices.quote(activeRecipe, config);
            if (hasCraftingInputsInInventory())
            {
                primeCraftingMonitor();
                state = JewelryCrafterState.CRAFTING;
                status = Rs2Player.isAnimating() ? "Detected active crafting" : "Using existing inventory inputs";
            }
            else
            {
                state = JewelryCrafterState.BANKING;
                status = "Checking existing inputs";
            }
            return;
        }

        JewelryRecipe selected = chooseRecipe();
        if (selected == null && config.selectionMode() == JewelrySelectionMode.FIXED_RECIPE)
        {
            JewelryRecipe fixed = config.fixedRecipe();
            if (fixed != null && fixed.isEligible(craftingLevel, memberAccount)) selected = fixed;
        }
        if (selected == null)
        {
            if (!closeBankIfDone("Closing bank - no eligible recipe")) return;
            pause("No eligible profitable jewellery recipe", 10_000L);
            return;
        }

        activeRecipe = selected;
        activeQuote = prices.quote(selected, config);
        if (hasCraftingInputsInInventory())
        {
            primeCraftingMonitor();
            state = JewelryCrafterState.CRAFTING;
            status = Rs2Player.isAnimating() ? "Detected active crafting" : "Using existing inventory inputs";
        }
        else
        {
            state = JewelryCrafterState.BANKING;
            status = "Preparing " + selected.getOutputName();
        }
    }

    private JewelryRecipe chooseRecipe()
    {
        List<JewelryQuote> viable = new ArrayList<>();
        for (JewelryRecipe recipe : JewelryRecipe.eligible(craftingLevel, memberAccount))
        {
            JewelryQuote quote = prices.quote(recipe, config);
            if (quote.meets(config)) viable.add(quote);
        }
        if (viable.isEmpty()) return null;

        if (config.selectionMode() == JewelrySelectionMode.FIXED_RECIPE)
        {
            JewelryRecipe fixed = config.fixedRecipe();
            if (!fixed.isEligible(craftingLevel, memberAccount)) return null;
            JewelryQuote quote = prices.quote(fixed, config);
            return quote.meets(config) ? fixed : null;
        }

        Comparator<JewelryQuote> comparator;
        switch (config.selectionMode())
        {
            case BEST_ROI: comparator = Comparator.comparingDouble(JewelryQuote::getRoi); break;
            case HIGHEST_LEVEL_PROFITABLE: comparator = Comparator.comparingInt(q -> q.getRecipe().getCraftingLevel()); break;
            case BEST_PROFIT:
            default: comparator = Comparator.comparingInt(JewelryQuote::getProfit); break;
        }
        JewelryQuote best = viable.stream().max(comparator).orElse(null);
        if (best != null) activeQuote = best;
        return best == null ? null : best.getRecipe();
    }

    private void bankForCrafting()
    {
        if (activeRecipe == null)
        {
            state = JewelryCrafterState.EVALUATING;
            status = "Re-evaluating";
            return;
        }

        activeQuote = prices.quote(activeRecipe, config);
        if (!bankWidgetOpen() && hasCraftingInputsInInventory())
        {
            bankInteractionSentAt = 0L;
            primeCraftingMonitor();
            state = JewelryCrafterState.CRAFTING;
            status = Rs2Player.isAnimating() ? "Crafting already in progress" : "Using carried inputs first";
            return;
        }
        resetCraftingMonitor();

        if (!openVerifiedBank(true, "Interacting with Edgeville bank")) return;
        if (!Rs2Bank.setWithdrawAsItem())
        {
            status = "Setting bank withdraw mode";
            return;
        }
        if (!depositCraftedOutput()) return;

        int available = availableInputUnits();
        boolean hasMould = Rs2Inventory.hasItem(activeRecipe.getMouldName())
            || Rs2Bank.count(activeRecipe.getMouldName(), true) > 0;
        if (!hasMould || available <= 0)
        {
            if (!prepareOutputForSale()) return;
            leaveBank(JewelryCrafterState.TRAVEL_TO_GE,
                !hasMould ? "Need mould - going to GE" : "Existing inputs exhausted - going to GE");
            return;
        }

        int craftUnits = Math.min(activeRecipe.usesGem() ? 13 : 27, available);
        if (!normalizeCraftingInventory(craftUnits)) return;
        int bars = Rs2Inventory.count(activeRecipe.getBarName(), true);
        int gems = activeRecipe.usesGem() ? Rs2Inventory.count(activeRecipe.getGemName(), true) : craftUnits;
        if (bars != craftUnits || gems != craftUnits || !Rs2Inventory.hasItem(activeRecipe.getMouldName()))
        {
            status = "Inventory setup verification failed";
            return;
        }

        currentBatchTarget = craftUnits;
        primeCraftingMonitor();
        leaveBank(JewelryCrafterState.CRAFTING, "Ready: " + craftUnits + " x " + activeRecipe.getOutputName());
    }

    private int availableInputUnits()
    {
        int bars = Rs2Inventory.count(activeRecipe.getBarName(), true) + Rs2Bank.count(activeRecipe.getBarName(), true);
        if (!activeRecipe.usesGem()) return bars;
        int gems = Rs2Inventory.count(activeRecipe.getGemName(), true) + Rs2Bank.count(activeRecipe.getGemName(), true);
        return Math.min(bars, gems);
    }

    private boolean normalizeCraftingInventory(int target)
    {
        if (!depositUnneededInventory()) return false;
        if (!trimInventory(activeRecipe.getMouldName(), 1)) return false;
        if (!trimInventory(activeRecipe.getBarName(), target)) return false;
        if (activeRecipe.usesGem() && !trimInventory(activeRecipe.getGemName(), target)) return false;
        if (!ensureMould()) return false;
        if (!ensureInventoryAmount(activeRecipe.getBarName(), target)) return false;
        return !activeRecipe.usesGem() || ensureInventoryAmount(activeRecipe.getGemName(), target);
    }

    private boolean depositUnneededInventory()
    {
        if (!bankWidgetOpen())
        {
            status = "Waiting for bank widget before inventory cleanup";
            return false;
        }
        if (!hasUnneededInventory()) return true;
        status = "Depositing unneeded inventory";
        boolean started = activeRecipe.usesGem()
            ? Rs2Bank.depositAllExcept(true, activeRecipe.getMouldName(), activeRecipe.getBarName(), activeRecipe.getGemName())
            : Rs2Bank.depositAllExcept(true, activeRecipe.getMouldName(), activeRecipe.getBarName());
        if (!started) return false;
        if (sleepUntil(() -> !hasUnneededInventory(), 5_000)) return true;
        status = "Waiting for inventory cleanup";
        return false;
    }

    private boolean hasUnneededInventory()
    {
        return Rs2Inventory.all().stream().anyMatch(item -> item != null && !isNeededInventoryItem(item.getName()));
    }

    private boolean isNeededInventoryItem(String name)
    {
        if (name == null) return false;
        if (name.equalsIgnoreCase(activeRecipe.getMouldName())) return true;
        if (name.equalsIgnoreCase(activeRecipe.getBarName())) return true;
        return activeRecipe.usesGem() && name.equalsIgnoreCase(activeRecipe.getGemName());
    }

    private boolean trimInventory(String item, int target)
    {
        int current = Rs2Inventory.count(item, true);
        if (current <= target) return true;
        status = "Depositing excess " + item.toLowerCase();
        if (!Rs2Bank.depositX(item, current - target)) return false;
        if (sleepUntil(() -> Rs2Inventory.count(item, true) <= target, 4_000)) return true;
        status = "Waiting to trim " + item.toLowerCase();
        return false;
    }

    private boolean bankWidgetOpen()
    {
        return Rs2Widget.isWidgetVisible(BANK_WIDGET_GROUP, BANK_WIDGET_CHILD);
    }

    private boolean openVerifiedBank(boolean edgeville, String openingStatus)
    {
        boolean wasOpen = bankWidgetOpen();
        int epoch = Rs2Bank.getBankLiveEpoch();
        if (wasOpen)
        {
            bankInteractionSentAt = 0L;
            if (Rs2Bank.verifyBankMirrorAfterOpen(true, epoch)) return true;
            status = "Waiting for bank contents";
            return sleepUntil(() -> Rs2Bank.verifyBankMirrorAfterOpen(true, epoch), 4_000);
        }

        long now = System.currentTimeMillis();
        if (bankInteractionSentAt > 0L
            && (Rs2Player.isMoving() || now - bankInteractionSentAt < TARGET_INTERACTION_TIMEOUT_MS))
        {
            status = Rs2Player.isMoving() ? "Approaching bank" : "Waiting for bank widget";
            return false;
        }
        bankInteractionSentAt = 0L;

        if (edgeville && distanceTo(EDGEVILLE_BANK) > EDGEVILLE_DIRECT_BANK_RADIUS)
        {
            status = "Walking to Edgeville";
            if (!Rs2Player.isMoving()) Rs2Walker.walkTo(EDGEVILLE_BANK, 10);
            return false;
        }

        status = openingStatus;
        long attemptStartedAt = System.currentTimeMillis();
        bankInteractionSentAt = attemptStartedAt;
        if (!Rs2Bank.openBank())
        {
            if (System.currentTimeMillis() - attemptStartedAt < 750L) bankInteractionSentAt = 0L;
            else bankInteractionSentAt = System.currentTimeMillis();
            status = bankInteractionSentAt > 0L
                ? (Rs2Player.isMoving() ? "Approaching bank" : "Waiting for bank widget")
                : "Bank target not ready";
            return false;
        }
        bankInteractionSentAt = 0L;
        if (!sleepUntil(this::bankWidgetOpen, 5_000))
        {
            status = "Waiting for bank widget";
            return false;
        }
        if (!sleepUntil(() -> Rs2Bank.verifyBankMirrorAfterOpen(false, epoch), 4_000))
        {
            status = "Waiting for bank contents";
            return false;
        }
        return true;
    }

    private boolean closeBankIfDone(String closingStatus)
    {
        if (!bankWidgetOpen())
        {
            bankInteractionSentAt = 0L;
            return true;
        }
        status = closingStatus;
        if (!Rs2Bank.closeBank()) return false;
        if (sleepUntil(() -> !bankWidgetOpen(), 5_000))
        {
            bankInteractionSentAt = 0L;
            return true;
        }
        status = "Waiting for bank widget to close";
        return false;
    }

    private boolean leaveBank(JewelryCrafterState nextState, String nextStatus)
    {
        if (!closeBankIfDone("Closing bank - banking complete")) return false;
        bankInteractionSentAt = 0L;
        state = nextState;
        status = nextStatus;
        return true;
    }

    private boolean depositCraftedOutput()
    {
        if (!bankWidgetOpen())
        {
            status = "Waiting for bank widget before deposit";
            return false;
        }
        String output = activeRecipe.getOutputName();
        if (Rs2Inventory.count(output, true) <= 0) return true;
        status = "Depositing crafted output";
        if (!Rs2Bank.depositAll(output, true)) return false;
        if (sleepUntil(() -> Rs2Inventory.count(output, true) == 0, 4_000)) return true;
        status = "Waiting for output deposit";
        return false;
    }

    private boolean prepareOutputForSale()
    {
        if (!bankWidgetOpen())
        {
            status = "Waiting for bank widget before sale prep";
            return false;
        }
        String output = activeRecipe.getOutputName();
        int carried = Rs2Inventory.itemQuantity(output, true);
        int banked = Rs2Bank.count(output, true);
        if (banked <= 0)
        {
            outputPendingSale = carried > 0 ? output : null;
            return true;
        }
        if (!Rs2Bank.setWithdrawAsNote())
        {
            status = "Setting noted output withdrawal";
            return false;
        }
        int target = carried + banked;
        status = "Withdrawing all output as notes";
        if (!Rs2Bank.withdrawAll(output, true)) return false;
        if (!sleepUntil(() -> Rs2Inventory.itemQuantity(output, true) >= target, 5_000))
        {
            status = "Waiting for full noted output stack";
            return false;
        }
        outputPendingSale = output;
        return true;
    }

    private boolean ensureMould()
    {
        if (!bankWidgetOpen())
        {
            status = "Waiting for bank widget before mould withdrawal";
            return false;
        }
        String mould = activeRecipe.getMouldName();
        if (Rs2Inventory.hasItem(mould)) return true;
        if (Rs2Bank.count(mould, true) <= 0)
        {
            status = mould + " not available";
            return false;
        }
        status = "Withdrawing " + mould;
        if (!Rs2Bank.withdrawOne(mould, true)) return false;
        if (sleepUntil(() -> Rs2Inventory.hasItem(mould), 4_000)) return true;
        status = "Waiting for " + mould;
        return false;
    }

    private boolean ensureInventoryAmount(String item, int target)
    {
        if (!bankWidgetOpen())
        {
            status = "Waiting for bank widget before withdrawal";
            return false;
        }
        int current = Rs2Inventory.count(item, true);
        if (current >= target) return true;
        int need = target - current;
        if (Rs2Bank.count(item, true) < need)
        {
            status = "Not enough " + item.toLowerCase();
            return false;
        }
        status = "Withdrawing " + need + " x " + item;
        if (!Rs2Bank.withdrawX(item, need, true)) return false;
        if (sleepUntil(() -> Rs2Inventory.count(item, true) >= target, 4_000)) return true;
        status = "Waiting for " + item + " count";
        return false;
    }

    private boolean hasCraftingInputsInInventory()
    {
        if (activeRecipe == null || !Rs2Inventory.hasItem(activeRecipe.getMouldName())) return false;
        int bars = Rs2Inventory.count(activeRecipe.getBarName(), true);
        return bars > 0 && (!activeRecipe.usesGem() || Rs2Inventory.count(activeRecipe.getGemName(), true) > 0);
    }

    private void primeCraftingMonitor()
    {
        if (activeRecipe == null) return;
        monitoredRecipe = activeRecipe;
        monitoredBars = Rs2Inventory.count(activeRecipe.getBarName(), true);
        monitoredGems = activeRecipe.usesGem() ? Rs2Inventory.count(activeRecipe.getGemName(), true) : monitoredBars;
        monitoredOutput = Rs2Inventory.count(activeRecipe.getOutputName(), true);
        monitoredBatchMade = 0;
        lastCraftingInventoryChangeAt = System.currentTimeMillis();
        craftingMonitorPrimed = true;
        currentBatchTarget = Math.min(monitoredBars, monitoredGems);
    }

    private boolean craftingInventoryChanged()
    {
        if (!craftingMonitorPrimed || monitoredRecipe != activeRecipe) return false;
        int bars = Rs2Inventory.count(activeRecipe.getBarName(), true);
        int gems = activeRecipe.usesGem() ? Rs2Inventory.count(activeRecipe.getGemName(), true) : bars;
        return monitoredBars != bars || monitoredGems != gems
            || monitoredOutput != Rs2Inventory.count(activeRecipe.getOutputName(), true);
    }

    private int observeCraftingProgress()
    {
        if (!craftingMonitorPrimed || monitoredRecipe != activeRecipe)
        {
            primeCraftingMonitor();
            return 0;
        }
        int bars = Rs2Inventory.count(activeRecipe.getBarName(), true);
        int gems = activeRecipe.usesGem() ? Rs2Inventory.count(activeRecipe.getGemName(), true) : bars;
        int output = Rs2Inventory.count(activeRecipe.getOutputName(), true);
        int made = Math.max(0, monitoredBars - bars);
        boolean changed = bars != monitoredBars || gems != monitoredGems || output != monitoredOutput;
        monitoredBars = bars;
        monitoredGems = gems;
        monitoredOutput = output;
        if (changed) lastCraftingInventoryChangeAt = System.currentTimeMillis();
        if (made <= 0) return 0;

        monitoredBatchMade += made;
        lastBatchMade = monitoredBatchMade;
        craftedCount += made;
        craftingXpGained += Math.round(made * activeRecipe.getXp());
        if (activeQuote != null && activeQuote.isValid()) estimatedProfit += (long) made * activeQuote.getProfit();
        outputPendingSale = activeRecipe.getOutputName();
        return made;
    }

    private void finishCraftingBatch()
    {
        observeCraftingProgress();
        lastBatchMade = monitoredBatchMade;
        currentBatchTarget = 0;
        furnaceInteractionSentAt = 0L;
        refreshAccountEligibility();
        resetCraftingMonitor();
        state = JewelryCrafterState.BANKING;
        status = "Banking output";
    }

    private void resetCraftingMonitor()
    {
        monitoredRecipe = null;
        monitoredBars = monitoredGems = monitoredOutput = monitoredBatchMade = 0;
        lastCraftingInventoryChangeAt = 0L;
        craftingMonitorPrimed = false;
    }

    private void craftInventory()
    {
        if (activeRecipe == null)
        {
            furnaceInteractionSentAt = 0L;
            resetCraftingMonitor();
            state = JewelryCrafterState.EVALUATING;
            return;
        }
        activeQuote = prices.quote(activeRecipe, config);
        if (!craftingMonitorPrimed || monitoredRecipe != activeRecipe) primeCraftingMonitor();
        if (craftingInventoryChanged()) observeCraftingProgress();
        if (!hasCraftingInputsInInventory())
        {
            finishCraftingBatch();
            return;
        }
        if (isJewelryProductionOpen()) furnaceInteractionSentAt = 0L;

        if (Rs2Player.isAnimating())
        {
            status = "Crafting " + activeRecipe.getOutputName();
            sleepUntil(() -> craftingInventoryChanged() || !Rs2Player.isAnimating(), 3_000);
            if (craftingInventoryChanged()) observeCraftingProgress();
            if (!hasCraftingInputsInInventory()) finishCraftingBatch();
            return;
        }
        if (System.currentTimeMillis() - lastCraftingInventoryChangeAt < 1_500L)
        {
            status = "Waiting for next craft";
            return;
        }
        if (sleepUntil(() -> Rs2Player.isAnimating() || craftingInventoryChanged(), 500))
        {
            if (craftingInventoryChanged()) observeCraftingProgress();
            status = "Crafting " + activeRecipe.getOutputName();
            return;
        }

        if (!isJewelryProductionOpen() && furnaceInteractionSentAt > 0L)
        {
            long elapsed = System.currentTimeMillis() - furnaceInteractionSentAt;
            if (Rs2Player.isMoving() || elapsed < TARGET_INTERACTION_TIMEOUT_MS)
            {
                status = Rs2Player.isMoving() ? "Approaching Edgeville furnace" : "Waiting for jewellery interface";
                return;
            }
            furnaceInteractionSentAt = 0L;
        }

        TileObject furnace = Rs2GameObject.findObjectById(EDGEVILLE_FURNACE_ID);
        if (furnace == null)
        {
            status = distanceTo(EDGEVILLE_FURNACE) <= 6 ? "Finding nearby Edgeville furnace" : "Walking to Edgeville furnace";
            if (!Rs2Player.isMoving()) Rs2Walker.walkTo(EDGEVILLE_FURNACE, 3);
            return;
        }
        if (!isJewelryProductionOpen())
        {
            status = "Opening jewellery furnace interface";
            if (!Rs2GameObject.interact(furnace, "Smelt"))
            {
                status = "Unable to use Edgeville furnace";
                return;
            }
            furnaceInteractionSentAt = System.currentTimeMillis();
            if (!sleepUntil(this::isJewelryProductionOpen, 5_000))
            {
                status = "Waiting for jewellery interface";
                return;
            }
            furnaceInteractionSentAt = 0L;
        }
        if (!makeAllSelected()) return;

        String widgetName = craftingWidgetName(activeRecipe);
        status = "Selecting " + widgetName;
        if (!Rs2Widget.clickWidget(widgetName))
        {
            status = "Unable to select " + widgetName;
            return;
        }
        status = "Starting " + activeRecipe.getOutputName();
        sleepUntil(() -> Rs2Player.isAnimating() || craftingInventoryChanged(), 5_000);
        if (craftingInventoryChanged()) observeCraftingProgress();
        if (!hasCraftingInputsInInventory()) finishCraftingBatch();
    }

    private boolean makeAllSelected()
    {
        boolean silver = activeRecipe.getBarName().equalsIgnoreCase("Silver bar");
        if (silver ? silverMakeAllSelected : goldMakeAllSelected) return true;
        status = "Selecting Make All";
        if (!Rs2Widget.clickWidget("All", true))
        {
            status = "Unable to select Make All";
            return false;
        }
        if (silver) silverMakeAllSelected = true;
        else goldMakeAllSelected = true;
        sleep(120, 220);
        return true;
    }

    private void travelToGe()
    {
        furnaceInteractionSentAt = 0L;
        resetCraftingMonitor();
        if (bankWidgetOpen() && !closeBankIfDone("Closing bank for GE trip")) return;
        if (distanceTo(GRAND_EXCHANGE) > 8)
        {
            status = "Walking to Grand Exchange";
            if (!Rs2Player.isMoving()) Rs2Walker.walkTo(GRAND_EXCHANGE, 6);
            return;
        }
        state = JewelryCrafterState.GE_SELL;
        status = outputPendingSale == null ? "No output pending - restocking" : "Selling crafted output";
    }

    private boolean openVerifiedGe(String openingStatus)
    {
        if (Rs2GrandExchange.isOpen()) return true;
        status = openingStatus;
        if (!Rs2GrandExchange.openExchange())
        {
            status = "GE widget closed - reopening";
            return false;
        }
        if (sleepUntil(Rs2GrandExchange::isOpen, 5_000)) return true;
        status = "Waiting for Grand Exchange widget";
        return false;
    }

    private boolean geSetupOpen()
    {
        return Rs2Widget.isWidgetVisible(InterfaceID.GeOffers.SETUP);
    }

    private boolean geSubScreenOpen()
    {
        return Rs2GrandExchange.isOfferScreenOpen() || geSetupOpen();
    }

    private boolean ensureGeOverview(String openingStatus)
    {
        if (!openVerifiedGe(openingStatus)) return false;
        if (!geSubScreenOpen()) return true;
        status = "Returning to GE overview";
        Rs2GrandExchange.backToOverview();
        if (sleepUntil(() -> Rs2GrandExchange.isOpen() && !geSubScreenOpen(), 4_000)) return true;
        status = Rs2GrandExchange.isOpen() ? "Waiting for GE overview" : "GE closed - recovering";
        return false;
    }

    private boolean placeBuyOfferSafely(GrandExchangeSlots slot, String itemName, int quantity, int price)
    {
        if (slot == null || itemName == null || itemName.isBlank() || quantity <= 0 || price <= 0)
        {
            status = "Invalid GE buy request";
            return false;
        }
        if (!ensureGeOverview("Opening Grand Exchange")) return false;

        status = "Opening GE buy offer: " + itemName;
        if (!clickGeSlotChildSafely(slot, 0))
            return recoverGrandExchange("GE buy slot is not ready");
        if (!sleepUntil(() -> Rs2GrandExchange.isOfferScreenOpen() || geSetupOpen(), 3_500))
            return recoverGrandExchange("GE buy editor did not open");

        if (!selectGeBuyItem(itemName))
            return recoverGrandExchange("GE item search did not become ready");
        if (!sleepUntil(this::geSetupControlsReady, 3_500))
            return recoverGrandExchange("GE offer controls did not load");
        if (!setGeOfferValue(GE_PRICE_X_CHILD, price, "price"))
            return recoverGrandExchange("GE price entry failed");
        if (!setGeOfferValue(GE_QUANTITY_X_CHILD, quantity, "quantity"))
            return recoverGrandExchange("GE quantity entry failed");
        return submitGeOfferSafely("buy");
    }

    private boolean placeSellOfferSafely(String itemName, int quantity, int price)
    {
        if (itemName == null || itemName.isBlank() || quantity <= 0 || price <= 0)
        {
            status = "Invalid GE sell request";
            return false;
        }
        if (!ensureGeOverview("Opening Grand Exchange")) return false;

        status = "Opening GE sell offer: " + itemName;
        if (!Rs2Inventory.interact(itemName, "Offer", true))
            return recoverGrandExchange("Unable to open GE sell offer");
        if (!sleepUntil(this::geSetupControlsReady, 3_500))
            return recoverGrandExchange("GE sell controls did not load");
        if (!setGeOfferValue(GE_PRICE_X_CHILD, price, "price"))
            return recoverGrandExchange("GE sell price entry failed");
        if (!setGeOfferValue(GE_QUANTITY_X_CHILD, quantity, "quantity"))
            return recoverGrandExchange("GE sell quantity entry failed");
        return submitGeOfferSafely("sell");
    }

    private boolean selectGeBuyItem(String itemName)
    {
        if (geSelectedOfferReady()) return true;

        if (!geSearchResultReady(itemName))
        {
            status = "Waiting for GE item search";
            if (!Rs2Widget.sleepUntilHasWidgetText(
                "Start typing the name of an item to search for it",
                GE_SEARCH_GROUP, GE_SEARCH_PROMPT_CHILD, false, 3_500))
                return false;

            Rs2Keyboard.typeString(itemName);
            if (!sleepUntil(() -> geSearchResultReady(itemName), 3_500))
                return false;
        }

        for (int attempt = 1; attempt <= 3; attempt++)
        {
            if (geSelectedOfferReady()) return true;
            if (!geSearchResultReady(itemName))
            {
                if (sleepUntil(this::geSelectedOfferReady, 900)) return true;
                status = "Waiting for GE search result: " + itemName;
                continue;
            }

            status = attempt == 1
                ? "Selecting GE item: " + itemName
                : "Retrying GE item selection (" + attempt + "/3): " + itemName;
            sleep(250, 450);
            if (!clickGeSearchResultSafely(itemName))
            {
                sleep(250, 400);
                continue;
            }
            if (sleepUntil(this::geSelectedOfferReady, 2_000))
            {
                sleep(250, 450);
                return true;
            }
            sleep(300, 500);
        }

        status = "GE item did not select: " + itemName;
        return false;
    }

    private boolean geSearchResultReady(String itemName)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(
            () -> Rs2GrandExchange.getSearchResultWidget(itemName, true) != null).orElse(false);
    }

    private boolean geSelectedOfferReady()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Widget setup = Microbot.getClient().getWidget(InterfaceID.GeOffers.SETUP);
            if (setup == null || setup.isHidden()) return false;
            Widget itemPrice = setup.getChild(GE_SELECTED_PRICE_CHILD);
            if (itemPrice == null || itemPrice.isHidden()) return false;
            String text = itemPrice.getText();
            if (text == null || text.isBlank()) return false;
            for (int i = 0; i < text.length(); i++)
                if (Character.isDigit(text.charAt(i))) return true;
            return false;
        }).orElse(false);
    }

    private boolean clickGeSearchResultSafely(String itemName)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            var result = Rs2GrandExchange.getSearchResultWidget(itemName, true);
            if (result == null || result.getLeft() == null) return false;
            Rs2Widget.clickWidgetFast(result.getLeft(), result.getRight(), 1);
            return true;
        }).orElse(false);
    }

    private boolean geSetupChildVisible(int child)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Widget setup = Microbot.getClient().getWidget(InterfaceID.GeOffers.SETUP);
            if (setup == null || setup.isHidden()) return false;
            Widget control = setup.getChild(child);
            return control != null && !control.isHidden();
        }).orElse(false);
    }

    private boolean clickGeSetupChildSafely(int child)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Widget setup = Microbot.getClient().getWidget(InterfaceID.GeOffers.SETUP);
            if (setup == null || setup.isHidden()) return false;
            Widget control = setup.getChild(child);
            if (control == null || control.isHidden()) return false;
            return Rs2Widget.clickWidget(control);
        }).orElse(false);
    }

    private boolean clickGeSlotChildSafely(GrandExchangeSlots slot, int child)
    {
        if (slot == null) return false;
        int componentId = InterfaceID.GeOffers.INDEX_0 + slot.ordinal();
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Widget slotWidget = Microbot.getClient().getWidget(componentId);
            if (slotWidget == null || slotWidget.isHidden()) return false;
            Widget control = slotWidget.getChild(child);
            if (control == null || control.isHidden()) return false;
            return Rs2Widget.clickWidget(control);
        }).orElse(false);
    }

    private boolean clickGeComponentSafely(int componentId)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Widget widget = Microbot.getClient().getWidget(componentId);
            if (widget == null || widget.isHidden()) return false;
            return Rs2Widget.clickWidget(widget);
        }).orElse(false);
    }

    private boolean clickGeSlotFastSafely(GrandExchangeSlots slot, int param0, int identifier)
    {
        if (slot == null) return false;
        int componentId = InterfaceID.GeOffers.INDEX_0 + slot.ordinal();
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Widget widget = Microbot.getClient().getWidget(componentId);
            if (widget == null || widget.isHidden()) return false;
            Rs2Widget.clickWidgetFast(widget, param0, identifier);
            return true;
        }).orElse(false);
    }

    private boolean geSetupControlsReady()
    {
        return Rs2GrandExchange.isOpen() && geSetupOpen()
            && geSelectedOfferReady()
            && geSetupChildVisible(GE_PRICE_X_CHILD)
            && geSetupChildVisible(GE_QUANTITY_X_CHILD);
    }

    private boolean setGeOfferValue(int child, int value, String label)
    {
        if (value <= 0) return false;
        if (geOfferValueMatches(child, value)) return true;

        for (int attempt = 1; attempt <= GE_VALUE_ENTRY_ATTEMPTS; attempt++)
        {
            if (!Rs2GrandExchange.isOpen() || !geSetupOpen())
            {
                status = "GE closed before setting " + label;
                return false;
            }
            if (!geSetupChildVisible(child))
            {
                status = "Waiting for GE " + label + " control";
                sleep(250, 450);
                continue;
            }

            status = attempt == 1
                ? "Setting GE " + label + ": " + value
                : "Retrying GE " + label + " (" + attempt + "/" + GE_VALUE_ENTRY_ATTEMPTS + "): " + value;

            if (child == GE_PRICE_X_CHILD)
                sleep(GE_PRICE_CLICK_DELAY_MIN_MS, GE_PRICE_CLICK_DELAY_MAX_MS);
            else
                sleep(300, 500);

            if (!clickGeSetupChildSafely(child))
            {
                sleep(250, 450);
                continue;
            }
            if (!sleepUntil(() -> gePriceInputOpen() || !Rs2GrandExchange.isOpen(), 3_000))
            {
                status = "Waiting for GE " + label + " input";
                sleep(250, 450);
                continue;
            }
            if (!Rs2GrandExchange.isOpen()) return false;

            sleep(600, 1_000);
            Rs2GrandExchange.setChatboxValue(value);
            sleep(500, 750);
            Rs2Keyboard.enter();
            if (!Rs2GrandExchange.isOpen()) return false;

            if (sleepUntil(() -> geOfferValueMatches(child, value), 1_500))
            {
                sleepUntil(() -> !gePriceInputOpen(), 1_000);
                return true;
            }

            if (gePriceInputOpen())
            {
                status = "Waiting for GE " + label + " entry";
                sleepUntil(() -> !gePriceInputOpen() || geOfferValueMatches(child, value)
                    || !Rs2GrandExchange.isOpen(), 1_500);
            }
            if (!Rs2GrandExchange.isOpen()) return false;
            if (geOfferValueMatches(child, value)) return true;
            sleep(250, 450);
        }

        status = "GE " + label + " did not update to " + value;
        return false;
    }

    private boolean geOfferValueMatches(int child, int value)
    {
        if (child == GE_PRICE_X_CHILD)
            return Microbot.getVarbitValue(GE_OFFER_PRICE_VARBIT) == value;
        if (child == GE_QUANTITY_X_CHILD)
            return Microbot.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY) == value;
        return false;
    }

    private boolean submitGeOfferSafely(String kind)
    {
        if (!Rs2GrandExchange.isOpen() || !geSetupOpen())
            return recoverGrandExchange("GE " + kind + " editor closed before confirm");

        status = "Confirming GE " + kind + " offer";
        if (!clickGeComponentSafely(InterfaceID.GeOffers.SETUP_CONFIRM))
            return recoverGrandExchange("Unable to confirm GE " + kind + " offer");

        sleepUntil(() -> !geSetupOpen() || Rs2Widget.hasWidget("Your offer is much")
            || !Rs2GrandExchange.isOpen(), 3_000);
        if (Rs2Widget.hasWidget("Your offer is much"))
        {
            Rs2Widget.clickWidget("Yes");
            sleepUntil(() -> !geSetupOpen() || !Rs2GrandExchange.isOpen(), 3_000);
        }

        if (!Rs2GrandExchange.isOpen())
            return recoverGrandExchange("GE closed while confirming " + kind + " offer");
        if (geSetupOpen())
            return recoverGrandExchange("GE " + kind + " offer did not submit");
        sleep(250, 450);
        return true;
    }

    private boolean recoverGrandExchange(String reason)
    {
        status = reason + " - resetting Grand Exchange";

        if (geSubScreenOpen())
        {
            Rs2GrandExchange.backToOverview();
            sleepUntil(() -> !geSubScreenOpen() || !Rs2GrandExchange.isOpen(), 2_000);
        }
        if (Rs2GrandExchange.isOpen())
        {
            Rs2GrandExchange.closeExchange();
            sleepUntil(() -> !Rs2GrandExchange.isOpen(), 2_500);
        }

        sleep(600, 900);
        status = "Reopening Grand Exchange";
        if (!Rs2GrandExchange.openExchange())
        {
            status = "Grand Exchange reopen failed - retrying";
            return false;
        }

        if (geSubScreenOpen())
        {
            Rs2GrandExchange.backToOverview();
            sleepUntil(() -> Rs2GrandExchange.isOpen() && !geSubScreenOpen(), 2_500);
        }
        status = Rs2GrandExchange.isOpen() && !geSubScreenOpen()
            ? "Grand Exchange recovered - retrying offer"
            : "Grand Exchange recovery incomplete - retrying";
        return false;
    }

    private void sellOutput()
    {
        if (handlePendingOffer()) return;
        String output = outputPendingSale;
        if (output == null)
        {
            state = JewelryCrafterState.GE_BUY;
            status = "Selecting profitable restock";
            return;
        }

        int qty = Rs2Inventory.itemQuantity(output, true);
        if (qty <= 0)
        {
            if (!recoverSaleStack(output)) return;
            qty = Rs2Inventory.itemQuantity(output, true);
            if (qty <= 0)
            {
                outputPendingSale = null;
                state = JewelryCrafterState.GE_BUY;
                status = "No output left - restocking";
                return;
            }
        }
        if (bankWidgetOpen() && !closeBankIfDone("Closing bank before selling")) return;
        if (!ensureGeOverview("Opening Grand Exchange")) return;
        if (hasCompletedGeOffers() && !collectAllCompletedOffers(false)) return;

        qty = Rs2Inventory.itemQuantity(output, true);
        int itemId = prices.getItemId(output);
        int price = prices.sellOfferPrice(output, config.sellDiscountPercent(), geRetry);
        if (qty <= 0 || itemId <= 0 || price <= 0)
        {
            status = "Unable to price/prepare output sale";
            return;
        }

        if (!placeSellOfferSafely(output, qty, price)) return;
        GrandExchangeSlots slot = findOfferSlot(itemId, GrandExchangeAction.SELL);
        pendingOffer = new PendingOffer(output, itemId, GrandExchangeAction.SELL, slot, System.currentTimeMillis());
        status = "Selling all " + qty + " x " + output;
    }

    private boolean recoverSaleStack(String output)
    {
        if (Rs2GrandExchange.isOpen())
        {
            Rs2GrandExchange.closeExchange();
            if (Rs2GrandExchange.isOpen())
            {
                status = "Closing GE for output recovery";
                return false;
            }
        }
        if (!openVerifiedBank(false, "Opening bank for output recovery")) return false;
        int banked = Rs2Bank.count(output, true);
        if (banked <= 0) return closeBankIfDone("Closing empty output bank");
        if (!Rs2Bank.setWithdrawAsNote())
        {
            status = "Setting noted output withdrawal";
            return false;
        }
        status = "Recovering full output stack";
        if (!Rs2Bank.withdrawAll(output, true)) return false;
        if (!sleepUntil(() -> Rs2Inventory.itemQuantity(output, true) >= banked, 5_000))
        {
            status = "Waiting for recovered output stack";
            return false;
        }
        return closeBankIfDone("Closing bank after output recovery");
    }

    private void buyInputs()
    {
        if (activeRecipe == null)
        {
            buyQueue.clear();
            state = JewelryCrafterState.EVALUATING;
            return;
        }
        if (buyQueue.isEmpty() && !buildBuyQueue()) return;
        if (buyQueue.isEmpty()) return;

        reconcileBuyAssignments();
        if (!ensureGeOverview("Opening Grand Exchange")) return;
        if (!monitorBuyOffers()) return;
        if (allBuyOrdersDone())
        {
            finishParallelRestock();
            return;
        }

        placeBuyOffers();
        if (allBuyOrdersDone())
        {
            finishParallelRestock();
            return;
        }
        int active = activeBuyOrders();
        int queued = queuedBuyOrders();
        status = active > 0
            ? "Buying inputs: " + active + " active" + (queued > 0 ? ", " + queued + " queued" : "")
            : "Waiting for free GE slot";
    }

    private boolean monitorBuyOffers()
    {
        reconcileBuyAssignments();
        boolean bought = false;
        for (BuyOrder order : buyQueue)
        {
            if (order.completed || order.failed || order.slot == null) continue;
            OfferSnapshot offer = getOfferSnapshot(order.slot);
            if (offer == null) continue;
            if (offer.itemId != order.itemId)
            {
                order.slot = null;
                order.placedAt = 0L;
                continue;
            }
            if (offer.state == GrandExchangeOfferState.BOUGHT)
            {
                order.completed = true;
                order.remaining = 0;
                bought = true;
            }
            else if (offer.state == GrandExchangeOfferState.EMPTY)
            {
                order.slot = null;
                order.placedAt = 0L;
            }
        }

        if (bought || hasCompletedGeOffers())
        {
            if (!collectAllCompletedOffers(true)) return false;
            for (BuyOrder order : buyQueue)
                if (order.completed) { order.slot = null; order.placedAt = 0L; }
            status = "Collected completed purchases to bank";
            return false;
        }

        long timeout = config.offerTimeoutSeconds() * 1_000L;
        long now = System.currentTimeMillis();
        for (BuyOrder order : buyQueue)
        {
            if (order.completed || order.failed || order.slot == null || now - order.placedAt < timeout) continue;
            return handleTimedOutBuyOrder(order);
        }
        return true;
    }

    private boolean handleTimedOutBuyOrder(BuyOrder order)
    {
        if (order.retry >= config.maxOfferRetries())
        {
            order.placedAt = System.currentTimeMillis();
            status = "Waiting at final buy price: " + order.itemName;
            return true;
        }
        int nextRetry = order.retry + 1;
        int price = prices.buyOfferPrice(order.itemName, config.buyMarkupPercent(), nextRetry);
        if (price <= 0)
        {
            order.placedAt = System.currentTimeMillis();
            status = "No updated buy price - keeping offer: " + order.itemName;
            return true;
        }
        if (!modifyGeOffer(order.slot, price)) return false;
        order.retry = nextRetry;
        order.placedAt = System.currentTimeMillis();
        status = "Modified buy price: " + order.itemName + " (retry " + order.retry + ")";
        return true;
    }

    private void placeBuyOffers()
    {
        reconcileBuyAssignments();
        List<GrandExchangeSlots> freeSlots = getAvailableGeSlots();
        if (freeSlots.isEmpty()) return;

        int slotIndex = 0;
        for (BuyOrder order : buyQueue)
        {
            if (slotIndex >= freeSlots.size()) break;
            if (order.completed || order.failed || order.slot != null || order.remaining <= 0) continue;
            GrandExchangeSlots slot = freeSlots.get(slotIndex++);
            int itemId = prices.getItemId(order.itemName);
            int price = prices.buyOfferPrice(order.itemName, config.buyMarkupPercent(), order.retry);
            if (itemId <= 0 || price <= 0)
            {
                order.failed = true;
                status = "No reliable buy price for " + order.itemName;
                continue;
            }

            order.itemId = itemId;
            order.slot = slot;
            order.placedAt = System.currentTimeMillis();
            status = "Buying " + order.remaining + " x " + order.itemName + " in GE slot " + (slot.ordinal() + 1);
            if (!placeBuyOfferSafely(slot, order.itemName, order.remaining, price))
            {
                order.slot = null;
                order.placedAt = 0L;
                return;
            }
            if (!sleepUntil(() -> offerMatches(order) || !Rs2GrandExchange.isOpen(), 3_000))
            {
                status = "Waiting for GE slot confirmation: " + order.itemName;
                return;
            }
            if (!Rs2GrandExchange.isOpen())
            {
                status = "GE closed after placing " + order.itemName + " - recovering";
                return;
            }
        }
    }

    private boolean offerMatches(BuyOrder order)
    {
        OfferSnapshot offer = getOfferSnapshot(order.slot);
        return offer != null && offer.itemId == order.itemId
            && (offer.state == GrandExchangeOfferState.BUYING || offer.state == GrandExchangeOfferState.BOUGHT);
    }

    private void reconcileBuyAssignments()
    {
        for (BuyOrder order : buyQueue)
        {
            if (order.completed || order.failed || order.itemId <= 0) continue;
            OfferSnapshot current = getOfferSnapshot(order.slot);
            if (current != null && current.itemId == order.itemId
                && (current.state == GrandExchangeOfferState.BUYING || current.state == GrandExchangeOfferState.BOUGHT)) continue;

            GrandExchangeSlots recovered = findOfferSlot(order.itemId, GrandExchangeAction.BUY);
            if (recovered != null)
            {
                order.slot = recovered;
                if (order.placedAt <= 0L) order.placedAt = System.currentTimeMillis();
            }
            else if (order.slot != null)
            {
                order.slot = null;
                order.placedAt = 0L;
            }
        }
    }

    private List<GrandExchangeSlots> getAvailableGeSlots()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            List<GrandExchangeSlots> free = new ArrayList<>();
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            int max = Math.min(memberAccount ? 8 : 3, GrandExchangeSlots.values().length);
            for (int i = 0; i < max; i++)
            {
                GrandExchangeOffer offer = offers != null && i < offers.length ? offers[i] : null;
                if (offer == null || offer.getState() == GrandExchangeOfferState.EMPTY) free.add(GrandExchangeSlots.values()[i]);
            }
            return free;
        }).orElse(new ArrayList<>());
    }

    private GrandExchangeSlots findOfferSlot(int itemId, GrandExchangeAction action)
    {
        if (itemId <= 0) return null;
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            if (offers == null) return null;
            int max = Math.min(offers.length, GrandExchangeSlots.values().length);
            for (int i = 0; i < max; i++)
            {
                GrandExchangeOffer offer = offers[i];
                if (offer == null || offer.getItemId() != itemId) continue;
                GrandExchangeOfferState s = offer.getState();
                if (action == GrandExchangeAction.BUY
                    && (s == GrandExchangeOfferState.BUYING || s == GrandExchangeOfferState.BOUGHT)) return GrandExchangeSlots.values()[i];
                if (action == GrandExchangeAction.SELL
                    && (s == GrandExchangeOfferState.SELLING || s == GrandExchangeOfferState.SOLD)) return GrandExchangeSlots.values()[i];
            }
            return null;
        }).orElse(null);
    }

    private OfferSnapshot getOfferSnapshot(GrandExchangeSlots slot)
    {
        if (slot == null) return null;
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            int index = slot.ordinal();
            if (offers == null || index >= offers.length || offers[index] == null)
                return new OfferSnapshot(0, GrandExchangeOfferState.EMPTY, 0, 0);
            GrandExchangeOffer offer = offers[index];
            return new OfferSnapshot(offer.getItemId(), offer.getState(), offer.getQuantitySold(), offer.getPrice());
        }).orElse(null);
    }

    private boolean hasCompletedGeOffers()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            if (offers == null) return false;
            for (GrandExchangeOffer offer : offers)
                if (offer != null && (offer.getState() == GrandExchangeOfferState.BOUGHT || offer.getState() == GrandExchangeOfferState.SOLD)) return true;
            return false;
        }).orElse(false);
    }

    private boolean collectAllCompletedOffers(boolean toBank)
    {
        if (!hasCompletedGeOffers()) return true;
        if (!ensureGeOverview("Reopening Grand Exchange to collect")) return false;
        status = toBank ? "Collecting completed purchases to bank" : "Collecting sale proceeds to inventory";
        boolean collected = toBank
            ? Rs2GrandExchange.collectAllToBank()
            : Rs2GrandExchange.collectAllToInventory();
        if (!collected)
        {
            status = Rs2GrandExchange.isOpen() ? "Collect all failed - retrying" : "GE closed during Collect all - recovering";
            return false;
        }
        if (sleepUntil(() -> !hasCompletedGeOffers(), 5_000)) return true;
        status = Rs2GrandExchange.isOpen() ? "Waiting for Collect all" : "GE closed after Collect all - recovering";
        return false;
    }

    private boolean modifyGeOffer(GrandExchangeSlots slot, int newPrice)
    {
        if (slot == null || newPrice <= 0) return false;
        OfferSnapshot current = getOfferSnapshot(slot);
        if (current != null && current.price == newPrice) return true;
        if (!ensureGeOverview("Reopening Grand Exchange to modify offer")) return false;

        status = "Opening GE Modify offer";
        if (!clickGeSlotFastSafely(slot, 2, 3))
        {
            status = "Waiting for GE slot widget";
            return false;
        }
        if (!sleepUntil(() -> geSetupOpen() || Rs2GrandExchange.isOfferScreenOpen() || !Rs2GrandExchange.isOpen(), 3_500))
        {
            status = "Waiting for GE Modify offer";
            return false;
        }
        if (!Rs2GrandExchange.isOpen())
        {
            status = "GE closed while opening Modify offer - recovering";
            return false;
        }

        if (!geSetupOpen())
        {
            if (!clickGeComponentSafely(InterfaceID.GeOffers.DETAILS_MODIFY))
            {
                status = "Waiting for GE Modify button";
                return false;
            }
            if (!sleepUntil(() -> geSetupOpen() || !Rs2GrandExchange.isOpen(), 3_000))
            {
                status = "Waiting for GE offer setup";
                return false;
            }
        }
        if (!Rs2GrandExchange.isOpen())
        {
            status = "GE closed before price edit - recovering";
            return false;
        }

        if (!setGeOfferValue(GE_PRICE_X_CHILD, newPrice, "modified price"))
        {
            status = Rs2GrandExchange.isOpen()
                ? "Unable to set modified GE price"
                : "GE closed during modified price entry - recovering";
            return false;
        }
        status = "Confirming modified GE price";
        if (!clickGeComponentSafely(InterfaceID.GeOffers.SETUP_CONFIRM))
        {
            status = "Unable to confirm modified GE price";
            return false;
        }
        sleepUntil(() -> !geSetupOpen() || Rs2Widget.hasWidget("Your offer is much") || !Rs2GrandExchange.isOpen(), 3_000);
        if (Rs2Widget.hasWidget("Your offer is much"))
        {
            Rs2Widget.clickWidget("Yes");
            sleepUntil(() -> !geSetupOpen() || !Rs2GrandExchange.isOpen(), 3_000);
        }

        if (sleepUntil(() ->
        {
            OfferSnapshot snapshot = getOfferSnapshot(slot);
            return snapshot != null && snapshot.price == newPrice;
        }, 3_000)) return true;

        status = Rs2GrandExchange.isOpen() ? "Waiting for modified GE price" : "GE closed after price modify - recovering";
        return false;
    }

    private boolean gePriceInputOpen()
    {
        return Rs2Widget.isWidgetVisible(InterfaceID.Chatbox.MES_TEXT2);
    }

    private boolean allBuyOrdersDone()
    {
        return !buyQueue.isEmpty() && buyQueue.stream().allMatch(o -> o.completed || o.failed);
    }

    private boolean hasFailedBuyOrders()
    {
        return buyQueue.stream().anyMatch(o -> o.failed);
    }

    private int activeBuyOrders()
    {
        return (int) buyQueue.stream().filter(o -> !o.completed && !o.failed && o.slot != null).count();
    }

    private int queuedBuyOrders()
    {
        return (int) buyQueue.stream().filter(o -> !o.completed && !o.failed && o.slot == null).count();
    }

    private void finishParallelRestock()
    {
        boolean failed = hasFailedBuyOrders();
        buyQueue.clear();
        geRetry = 0;
        if (Rs2GrandExchange.isOpen()) Rs2GrandExchange.closeExchange();
        if (failed)
        {
            pause("One or more input offers could not be placed", 15_000L);
            return;
        }
        state = JewelryCrafterState.RETURN_TO_FURNACE;
        status = "Restock complete";
    }

    private boolean buildBuyQueue()
    {
        refreshAccountEligibility();
        JewelryRecipe best = chooseRecipe();
        if (best == null)
        {
            pause("No profitable recipe to restock", 10_000L);
            return false;
        }
        activeRecipe = best;
        activeQuote = prices.quote(best, config);

        // Rs2Bank.count() reads Microbot's mirrored bank cache and does not require the
        // bank interface to be open. Only initialise that cache once if this session has
        // never received a live bank snapshot; normal crafting bank trips keep it fresh.
        if (Rs2Bank.getBankLiveEpoch() <= 0)
        {
            if (Rs2GrandExchange.isOpen())
            {
                Rs2GrandExchange.closeExchange();
                if (Rs2GrandExchange.isOpen())
                {
                    status = "Closing GE to initialise bank cache";
                    return false;
                }
            }
            if (!openVerifiedBank(false, "Opening bank once to initialise bank cache")) return false;
            if (!closeBankIfDone("Closing bank after bank cache initialisation")) return false;
            status = "Bank cache initialised - returning to GE";
            return false;
        }

        status = "Planning restock from cached bank and carried coins";
        long coins = Rs2Bank.count("Coins", true) + (long) Rs2Inventory.itemQuantity("Coins", true);
        long spendable = Math.max(0L, coins - config.reserveCoins());
        spendable = spendable * config.capitalUsagePercent() / 100L;
        int unitCost = Math.max(1, activeQuote.getInputCost());
        int affordable = (int) Math.min(Integer.MAX_VALUE, spendable / unitCost);
        int target = Math.min(config.maxRestockUnits(), affordable);

        int existingBars = Rs2Bank.count(activeRecipe.getBarName(), true) + Rs2Inventory.count(activeRecipe.getBarName(), true);
        int existingGems = activeRecipe.usesGem()
            ? Rs2Bank.count(activeRecipe.getGemName(), true) + Rs2Inventory.count(activeRecipe.getGemName(), true)
            : target;
        int existingUnits = Math.min(existingBars, existingGems);
        target = Math.max(target, Math.min(config.maxRestockUnits(), existingUnits));
        if (target <= 0)
        {
            pause("Not enough spendable coins to restock", 15_000L);
            return false;
        }

        buyQueue.clear();
        if (!Rs2Inventory.hasItem(activeRecipe.getMouldName()) && Rs2Bank.count(activeRecipe.getMouldName(), true) <= 0)
            buyQueue.add(new BuyOrder(activeRecipe.getMouldName(), 1));
        int needBars = Math.max(0, target - existingBars);
        if (needBars > 0) buyQueue.add(new BuyOrder(activeRecipe.getBarName(), needBars));
        if (activeRecipe.usesGem())
        {
            int needGems = Math.max(0, target - existingGems);
            if (needGems > 0) buyQueue.add(new BuyOrder(activeRecipe.getGemName(), needGems));
        }

        if (buyQueue.isEmpty())
        {
            state = JewelryCrafterState.RETURN_TO_FURNACE;
            status = "Inputs already stocked";
            return false;
        }
        return true;
    }

    private boolean handlePendingOffer()
    {
        if (pendingOffer == null) return false;
        if (!ensureGeOverview("Opening GE to monitor offer")) return true;

        GrandExchangeSlots slot = pendingOffer.slot;
        OfferSnapshot offer = getOfferSnapshot(slot);
        if (offer == null || offer.itemId != pendingOffer.itemId
            || (offer.state != GrandExchangeOfferState.SELLING && offer.state != GrandExchangeOfferState.SOLD))
        {
            slot = findOfferSlot(pendingOffer.itemId, pendingOffer.action);
            pendingOffer.slot = slot;
            offer = getOfferSnapshot(slot);
        }
        if (offer == null || slot == null)
        {
            status = "Recovering live GE sale offer";
            return true;
        }

        if (offer.state == GrandExchangeOfferState.SOLD)
        {
            if (!collectAllCompletedOffers(false)) return true;
            String itemName = pendingOffer.itemName;
            pendingOffer = null;
            geRetry = 0;
            if (Rs2Inventory.itemQuantity(itemName, true) > 0)
            {
                outputPendingSale = itemName;
                state = JewelryCrafterState.GE_SELL;
                status = "Selling remaining output";
            }
            else
            {
                outputPendingSale = null;
                buyQueue.clear();
                state = JewelryCrafterState.GE_BUY;
                status = "All output sold - selecting restock";
            }
            return true;
        }

        long timeout = config.offerTimeoutSeconds() * 1_000L;
        if (System.currentTimeMillis() - pendingOffer.placedAt >= timeout)
        {
            if (geRetry >= config.maxOfferRetries())
            {
                pendingOffer.placedAt = System.currentTimeMillis();
                status = "Waiting at final sell price: " + pendingOffer.itemName;
                return true;
            }
            int nextRetry = geRetry + 1;
            int price = prices.sellOfferPrice(pendingOffer.itemName, config.sellDiscountPercent(), nextRetry);
            if (price <= 0)
            {
                pendingOffer.placedAt = System.currentTimeMillis();
                status = "No updated sell price - keeping offer";
                return true;
            }
            if (!modifyGeOffer(slot, price)) return true;
            geRetry = nextRetry;
            pendingOffer.placedAt = System.currentTimeMillis();
            status = "Modified sell price: " + pendingOffer.itemName + " (retry " + geRetry + ")";
            return true;
        }

        status = "Waiting for GE offer: " + pendingOffer.itemName;
        return true;
    }

    private void returnToFurnace()
    {
        if (Rs2GrandExchange.isOpen())
        {
            Rs2GrandExchange.closeExchange();
            if (Rs2GrandExchange.isOpen())
            {
                status = "Closing Grand Exchange";
                return;
            }
        }
        if (distanceTo(EDGEVILLE_BANK) > EDGEVILLE_DIRECT_BANK_RADIUS)
        {
            status = "Returning to Edgeville";
            if (!Rs2Player.isMoving()) Rs2Walker.walkTo(EDGEVILLE_BANK, 10);
            return;
        }
        state = JewelryCrafterState.BANKING;
        status = "Preparing next crafting inventory";
    }

    private void pause(String reason, long millis)
    {
        status = reason;
        waitingUntil = System.currentTimeMillis() + millis;
        state = JewelryCrafterState.WAITING;
    }

    private void waitState()
    {
        if (System.currentTimeMillis() >= waitingUntil)
        {
            state = JewelryCrafterState.EVALUATING;
            status = "Re-evaluating";
        }
    }

    private int distanceTo(WorldPoint point)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            if (Microbot.getClient().getLocalPlayer() == null) return Integer.MAX_VALUE;
            return Microbot.getClient().getLocalPlayer().getWorldLocation().distanceTo2D(point);
        }).orElse(Integer.MAX_VALUE);
    }

    private boolean isJewelryProductionOpen()
    {
        return Rs2Widget.isGoldCraftingWidgetOpen() || Rs2Widget.isSilverCraftingWidgetOpen();
    }

    private String craftingWidgetName(JewelryRecipe recipe)
    {
        String name = recipe.getOutputName();
        if (name.endsWith(" (u)")) name = name.substring(0, name.length() - 4);
        if (name.equalsIgnoreCase("Dragonstone bracelet")) return "Dragon bracelet";
        return name;
    }

    public long getRuntimeMillis()
    {
        long start = sessionStartedAt;
        return start <= 0L ? 0L : Math.max(0L, System.currentTimeMillis() - start);
    }

    public String getFormattedRuntime()
    {
        long elapsed = getRuntimeMillis();
        long hours = elapsed / 3_600_000L;
        long minutes = (elapsed % 3_600_000L) / 60_000L;
        long seconds = (elapsed % 60_000L) / 1_000L;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    public long getCraftingXpPerHour()
    {
        long elapsed = getRuntimeMillis();
        return elapsed <= 0L ? 0L : Math.round(craftingXpGained * 3_600_000.0 / elapsed);
    }

    public long getEstimatedProfitPerHour()
    {
        long elapsed = getRuntimeMillis();
        return elapsed <= 0L ? 0L : Math.round(estimatedProfit * 3_600_000.0 / elapsed);
    }

    public String getPendingOfferSummary()
    {
        if (pendingOffer != null) return "SELL " + pendingOffer.itemName;
        int active = activeBuyOrders();
        if (active > 0) return "BUY " + active + " slot" + (active == 1 ? "" : "s");
        int queued = queuedBuyOrders();
        return queued > 0 ? "BUY " + queued + " queued" : "None";
    }

    public String getRestockProgress()
    {
        if (buyQueue.isEmpty()) return "Idle";
        int completed = (int) buyQueue.stream().filter(o -> o.completed).count();
        int failed = (int) buyQueue.stream().filter(o -> o.failed).count();
        int active = activeBuyOrders();
        String progress = completed + "/" + buyQueue.size() + " done";
        if (active > 0) progress += ", " + active + " active";
        if (failed > 0) progress += ", " + failed + " failed";
        return progress;
    }

    public int getGeRetry()
    {
        int retry = geRetry;
        for (BuyOrder order : buyQueue) retry = Math.max(retry, order.retry);
        return retry;
    }

    @Override
    public void shutdown()
    {
        super.shutdown();
        state = JewelryCrafterState.STOPPED;
        status = "Stopped";
        pendingOffer = null;
        buyQueue.clear();
        currentBatchTarget = 0;
        goldMakeAllSelected = silverMakeAllSelected = false;
        bankInteractionSentAt = furnaceInteractionSentAt = 0L;
        resetCraftingMonitor();
    }

    private static final class PendingOffer
    {
        final String itemName;
        final int itemId;
        final GrandExchangeAction action;
        GrandExchangeSlots slot;
        long placedAt;

        PendingOffer(String itemName, int itemId, GrandExchangeAction action, GrandExchangeSlots slot, long placedAt)
        {
            this.itemName = itemName;
            this.itemId = itemId;
            this.action = action;
            this.slot = slot;
            this.placedAt = placedAt;
        }
    }

    private static final class BuyOrder
    {
        final String itemName;
        final int quantity;
        int remaining;
        int itemId;
        int retry;
        GrandExchangeSlots slot;
        long placedAt;
        boolean completed;
        boolean failed;

        BuyOrder(String itemName, int quantity)
        {
            this.itemName = itemName;
            this.quantity = quantity;
            this.remaining = quantity;
        }
    }

    private static final class OfferSnapshot
    {
        final int itemId;
        final GrandExchangeOfferState state;
        final int quantitySold;
        final int price;

        OfferSnapshot(int itemId, GrandExchangeOfferState state, int quantitySold, int price)
        {
            this.itemId = itemId;
            this.state = state;
            this.quantitySold = quantitySold;
            this.price = price;
        }
    }
}
