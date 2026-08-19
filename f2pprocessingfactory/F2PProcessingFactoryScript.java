package net.runelite.client.plugins.microbot.f2pprocessingfactory;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeSlots;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
public class F2PProcessingFactoryScript extends Script
{
    private static final int LOOP_DELAY_MILLIS = 600;
    private static final int BANK_OPEN_TIMEOUT_MILLIS = 6_000;
    private static final int INVENTORY_CHANGE_TIMEOUT_MILLIS = 5_000;
    private static final int PROCESS_START_TIMEOUT_MILLIS = 3_000;
    private static final int PROCESS_FINISH_TIMEOUT_MILLIS = 50_000;
    private static final int IMMEDIATE_COMBINE_STEP_TIMEOUT_MILLIS = 2_500;
    private static final int MAX_PROCESS_FAILURES = 3;
    private static final double PURCHASE_SAFETY_MULTIPLIER = 1.10;
    private static final long GE_PLACEMENT_FAILURE_BACKOFF_MILLIS = 10_000L;
    private static final long GE_PLACEMENT_WARNING_INTERVAL_MILLIS = 60_000L;
    private static final int MAX_PROGRESS_WATCHDOG_RETRIES = 3;
    private static final int MAX_PROCESS_WITHDRAW_X_ATTEMPTS = 3;
    private static final long WATCHDOG_MIN_RETRY_GAP_MILLIS = 1_500L;
    private static final long WATCHDOG_STUCK_EDITOR_TIMEOUT_MILLIS = 8_000L;
    // Fixed mode must never fall into the generic multi-minute "no recipe" wait
    // because of a single transient market quote. Keep the selected recipe pinned
    // and re-evaluate its margin on a short cadence instead.
    private static final long FIXED_RECIPE_MARKET_RECHECK_MILLIS = 10_000L;
    // Keyboard actions are permitted only while the exact target widget is still
    // visible. This prevents a stale state-machine tick from sending Space into
    // chat/dialogue/GE after the production interface has already closed.
    private static final long FACTORY_SPACE_MIN_GAP_MILLIS = 2_000L;
    // Bank Withdraw-X has its own chatbox amount prompt. Never rely on the
    // generic Rs2Bank.handleAmount() delayed Enter because the prompt can disappear
    // during an interface transition before that Enter is sent.
    private static final String BANK_AMOUNT_PROMPT = "Enter amount:";
    private static final int BANK_AMOUNT_PROMPT_GROUP = 162;
    private static final int BANK_AMOUNT_PROMPT_CHILD = 43;
    private static final int BANK_CHATBOX_INPUT_MODE = 7;
    private static final int BANK_WITHDRAW_X_PROMPT_IDENTIFIER = 6;
    private static final int BANK_AMOUNT_PROMPT_TIMEOUT_MILLIS = 2_000;
    private static final long BANK_ENTER_MIN_GAP_MILLIS = 600L;

    private F2PProcessingFactoryConfig config;
    private FactoryPriceService priceService;
    private GeBuyLimitTracker buyLimitTracker;
    private FactoryAntibanController antiban;

    private volatile FactoryState state = FactoryState.STOPPED;
    private volatile FactoryRecipe activeRecipe;
    private volatile ProfitQuote activeQuote;
    private volatile String status = "Stopped";
    private volatile FactoryStats stats = new FactoryStats();
    private volatile int cycleTargetUnits;
    private volatile int cycleProcessedUnits;
    private volatile long waitingUntil;
    private volatile int observedCoinTotal;
    private volatile int observedSpendableCoins;
    private volatile boolean membersAccount;
    private volatile boolean memberWorld;

    private CyclePlan activePlan;
    private int processFailures;
    private int outputRemainingToSell;
    private String sellingItemName;
    private final List<String> saleOutputQueue = new ArrayList<>();
    private int saleOutputIndex;
    private boolean initialized;
    private volatile boolean waitingForGeSlot;
    private volatile boolean waitingForExistingGeOffer;
    private volatile long nextGePlacementAttemptAt;
    private volatile long lastGePlacementWarningAt;
    private final Set<GrandExchangeSlots> factoryBuyOfferSlots = new HashSet<>();
    private final Map<GrandExchangeSlots, Integer> factoryBuyOfferPrices = new HashMap<>();
    private final Map<GrandExchangeSlots, Integer> factoryBuyOfferRetries = new HashMap<>();
    private final Map<Integer, Integer> buyPriceRetryByItem = new HashMap<>();
    private final Map<Integer, Integer> pendingCollectedBuyCredits = new HashMap<>();
    private final Set<GrandExchangeSlots> factorySellOfferSlots = new HashSet<>();
    private final Map<GrandExchangeSlots, Integer> factorySellOfferPrices = new HashMap<>();
    /**
     * Binds every factory-owned SELL slot to the item it was created for. The live
     * GrandExchangeOffer can briefly stop reporting a normal SELLING/SOLD state while
     * Jagex transitions a Modify offer. Never use that transient gap as permission to
     * create a second SELL from the inventory.
     */
    private final Map<GrandExchangeSlots, Integer> factorySellOfferItemIds = new HashMap<>();
    private final Map<Integer, Integer> sellPriceRetryByItem = new HashMap<>();
    /**
     * Outputs whose sale retry policy was exhausted are temporarily market-blocked.
     * Their unsold remainder is collected back to the bank, then the factory moves
     * on instead of sitting in WAITING_FOR_MARKET with an empty Grand Exchange.
     */
    private final Map<Integer, Long> sellMarketBlockedUntil = new HashMap<>();
    private boolean sellRetryPolicyExhaustedThisCall;

    // Generic progress watchdog. Normal state handlers are already retried by the
    // 600 ms main loop; this layer detects when repeated attempts produce no
    // meaningful client change and escalates to a safe state-specific recovery.
    private volatile FactoryState watchdogState = FactoryState.STOPPED;
    private volatile String watchdogFingerprint = "";
    private volatile long watchdogLastProgressAt;
    private volatile long watchdogLastRetryAt;
    private volatile int watchdogRetryCount;
    private volatile String watchdogLastRecovery = "Idle";
    private volatile long lastFactorySpaceAt;
    private volatile long lastBankEnterAt;

    public boolean run(
        F2PProcessingFactoryConfig config,
        ConfigManager configManager,
        Gson gson)
    {
        this.config = config;
        this.priceService = new FactoryPriceService();
        this.buyLimitTracker = new GeBuyLimitTracker(configManager, gson);
        this.antiban = new FactoryAntibanController(config);
        this.stats = new FactoryStats();
        this.state = FactoryState.STARTING;
        this.status = "Starting";
        this.activeRecipe = null;
        this.activeQuote = null;
        this.activePlan = null;
        this.cycleTargetUnits = 0;
        this.cycleProcessedUnits = 0;
        this.outputRemainingToSell = 0;
        this.sellingItemName = null;
        this.saleOutputQueue.clear();
        this.saleOutputIndex = 0;
        this.waitingUntil = 0L;
        this.processFailures = 0;
        this.observedCoinTotal = 0;
        this.observedSpendableCoins = 0;
        this.membersAccount = false;
        this.memberWorld = false;
        this.initialized = false;
        this.waitingForGeSlot = false;
        this.waitingForExistingGeOffer = false;
        this.nextGePlacementAttemptAt = 0L;
        this.lastGePlacementWarningAt = 0L;
        this.factoryBuyOfferSlots.clear();
        this.factoryBuyOfferPrices.clear();
        this.factoryBuyOfferRetries.clear();
        this.buyPriceRetryByItem.clear();
        this.pendingCollectedBuyCredits.clear();
        this.factorySellOfferSlots.clear();
        this.factorySellOfferPrices.clear();
        this.factorySellOfferItemIds.clear();
        this.sellPriceRetryByItem.clear();
        this.sellMarketBlockedUntil.clear();
        this.sellRetryPolicyExhaustedThisCall = false;
        this.lastFactorySpaceAt = 0L;
        resetProgressWatchdog();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            try
            {
                if (!super.run() || !Microbot.isLoggedIn())
                {
                    return;
                }
                tick();
            }
            catch (Exception ex)
            {
                status = "Error: " + safeMessage(ex);
                log.error("KSP AIO Factory failure", ex);
            }
        }, 0, LOOP_DELAY_MILLIS, TimeUnit.MILLISECONDS);

        return true;
    }

    private void tick()
    {
        if (config != null
            && config.mode() == FactoryMode.FIXED_RECIPE
            && activeRecipe != null
            && activeRecipe != config.fixedRecipe())
        {
            log.info(
                "Fixed recipe changed/enforced: abandoning active {} plan and switching to {}",
                activeRecipe,
                config.fixedRecipe()
            );
            resetCycle();
        }

        if (antiban != null && antiban.beforeTick(state))
        {
            // Anti-ban pauses are intentional and must never be interpreted as a
            // stalled factory action.
            syncProgressWatchdog(false);
            return;
        }

        syncProgressWatchdog(false);

        switch (state)
        {
            case STARTING:
                startSession();
                break;
            case OPENING_BANK:
                openAndNormalizeBank();
                break;
            case EVALUATING_RECIPES:
                evaluateRecipes();
                break;
            case PREPARING_CYCLE:
                prepareCycle();
                break;
            case BUYING_INPUTS:
                buyInputs();
                break;
            case PREPARING_INVENTORY:
                prepareProcessingInventory();
                break;
            case PROCESSING:
                processInventory();
                break;
            case BANKING_OUTPUT:
                bankProcessedOutput();
                break;
            case SELLING_OUTPUT:
                sellOutput();
                break;
            case WAITING_FOR_LIMIT:
            case WAITING_FOR_MARKET:
                waitForReevaluation();
                break;
            case STOPPED:
            default:
                break;
        }

        syncProgressWatchdog(true);
    }

    private void startSession()
    {
        if (!initialized)
        {
            String accountName = getAccountName();
            buyLimitTracker.loadForAccount(accountName);
            membersAccount = detectMembersAccount();
            memberWorld = detectMemberWorld();
            initialized = true;
            log.info(
                "Loaded KSP AIO Factory buy-limit ledger for {} (account type: {})",
                accountName,
                membersAccount ? (memberWorld ? "Members / member world" : "Members / F2P world") : "Free-to-play"
            );
        }
        state = FactoryState.OPENING_BANK;
        status = "Opening bank";
    }

    private void openAndNormalizeBank()
    {
        if (!ensureBankOpen())
        {
            return;
        }

        // Sale preparation temporarily switches the bank to noted withdrawals.
        // Normal factory banking/processing must always resume in item mode.
        if (!Rs2Bank.setWithdrawAsItem())
        {
            status = "Unable to restore bank withdraw-as-item mode";
            return;
        }

        int coinsId = ItemID.COINS;
        int inventoryCoinsBefore = inventoryCount(coinsId);
        int bankCoinsBefore = bankCount(coinsId);

        if (!Rs2Inventory.isEmpty())
        {
            status = "Depositing inventory";
            Rs2Bank.depositAll();
            if (!sleepUntil(Rs2Inventory::isEmpty, INVENTORY_CHANGE_TIMEOUT_MILLIS))
            {
                status = "Waiting for inventory deposit";
                return;
            }
        }

        // Rs2Bank.count() reads Microbot's mirrored bank container. The inventory can
        // become empty one tick before that mirror receives ItemContainerChanged.
        // Wait for deposited coins to become visible so low-capital accounts are not
        // incorrectly evaluated as having zero spendable coins.
        long expectedCoins = (long) inventoryCoinsBefore + bankCoinsBefore;
        int expectedMinimumCoins = expectedCoins > Integer.MAX_VALUE
            ? Integer.MAX_VALUE
            : (int) expectedCoins;
        if (expectedMinimumCoins > 0 && bankCount(coinsId) < expectedMinimumCoins)
        {
            status = "Syncing bank balance";
            sleepUntil(
                () -> bankCount(coinsId) >= expectedMinimumCoins,
                INVENTORY_CHANGE_TIMEOUT_MILLIS
            );
        }
        else if (bankCount(coinsId) <= 0)
        {
            // Also cover a bank that was already open before the plugin started and
            // whose mirrored container has not produced its first live snapshot yet.
            status = "Syncing bank balance";
            sleepUntil(() -> bankCount(coinsId) > 0, 2_500);
        }

        refreshCoinSnapshot();
        state = activePlan == null ? FactoryState.EVALUATING_RECIPES : FactoryState.PREPARING_CYCLE;
        status = activePlan == null ? "Evaluating recipes" : "Preparing cycle";
    }

    private void evaluateRecipes()
    {
        if (!ensureBankOpen())
        {
            return;
        }

        membersAccount = detectMembersAccount();
        memberWorld = detectMemberWorld();
        List<FactoryRecipe> candidates = buildCandidateList();
        if (candidates.isEmpty())
        {
            waitForMarket("No supported recipes meet the account's membership/skill requirements");
            return;
        }

        // Always drain work that is already funded and sitting in the bank before
        // committing more coins to the GE. This is deliberately evaluated before
        // profitability/new-purchase planning so a large affordable procurement
        // cannot strand processable inputs or finished outputs from an earlier run.
        if (startExistingBankBacklog())
        {
            return;
        }

        // Fixed mode is not a recipe search. The configured recipe remains pinned
        // even when a rapidly-moving market quote temporarily crosses the configured
        // margin. A bad/transient quote pauses only NEW capital for this one recipe
        // and is rechecked quickly; it must never collapse into the generic
        // "No recipe currently meets the configured margin" state.
        if (config.mode() == FactoryMode.FIXED_RECIPE)
        {
            evaluateFixedRecipe(config.fixedRecipe());
            return;
        }

        List<CyclePlan> viablePlans = new ArrayList<>();
        long shortestLimitReset = Long.MAX_VALUE;
        boolean anyProfitableQuote = false;

        for (FactoryRecipe recipe : candidates)
        {
            if (isRecipeSellMarketBlocked(recipe))
            {
                log.info("Skipping {}: primary output {} is temporarily market-blocked after exhausting sell retries",
                    recipe, recipe.getOutputItemName());
                continue;
            }

            ProfitQuote quote = priceService.quote(recipe, config);
            if (!quote.isValid() || quote.getProfitPerUnit() <= 0 || !quote.meets(config))
            {
                log.debug("Skipping {}: {}", recipe, quote.isValid()
                    ? (quote.getProfitPerUnit() <= 0
                        ? "not profitable at current prices"
                        : "margin below configured minimum")
                    : quote.getError());
                continue;
            }

            anyProfitableQuote = true;

            String limitBlockedInput = findLimitBlockedMissingInput(recipe);
            if (limitBlockedInput != null)
            {
                int blockedItemId = priceService.getItemId(limitBlockedInput);
                long reset = blockedItemId <= 0
                    ? 0L
                    : buyLimitTracker.getMillisUntilNextReset(blockedItemId);
                if (reset > 0)
                {
                    shortestLimitReset = Math.min(shortestLimitReset, reset);
                }
                log.info(
                    "Skipping {}: GE buy-limit capacity exhausted for missing input {}",
                    recipe,
                    limitBlockedInput
                );
                continue;
            }

            CyclePlan plan = createCyclePlan(recipe, quote);
            if (plan.targetUnits > 0)
            {
                viablePlans.add(plan);
            }
            else if (plan.nextLimitResetMillis > 0)
            {
                shortestLimitReset = Math.min(shortestLimitReset, plan.nextLimitResetMillis);
            }
        }

        if (viablePlans.isEmpty())
        {
            if (anyProfitableQuote && shortestLimitReset != Long.MAX_VALUE)
            {
                handleLimitExhaustion(shortestLimitReset);
            }
            else if (anyProfitableQuote)
            {
                refreshCoinSnapshot();
                waitForMarket(String.format(
                    "No affordable batch: %,d coins spendable (%,d total, %,d reserve)",
                    observedSpendableCoins,
                    observedCoinTotal,
                    config.cashReserve()
                ));
            }
            else
            {
                waitForMarket("No recipe currently meets the configured margin");
            }
            return;
        }

        CyclePlan selected;
        if (config.mode() == FactoryMode.FIXED_RECIPE)
        {
            // Fixed mode is strict: never substitute another recipe. If the fixed
            // recipe cannot currently run, the normal wait/re-evaluation path below
            // handles it instead of silently changing what the user selected.
            selected = viablePlans.stream()
                .filter(plan -> plan.recipe == config.fixedRecipe())
                .findFirst()
                .orElse(null);
        }
        else
        {
            selected = selectBestPlan(viablePlans);
        }

        if (selected == null)
        {
            long reset = shortestLimitReset == Long.MAX_VALUE
                ? TimeUnit.MINUTES.toMillis(config.reevaluateMinutes())
                : shortestLimitReset;
            handleLimitExhaustion(reset);
            return;
        }

        activatePlan(selected);
        status = "Selected " + activeRecipe.getDisplayName();

        log.info(
            "Selected {}: target={}, stock={}, purchases={}, profit/unit={}, ROI={}%, estimated hourly={}",
            selected.recipe,
            selected.targetUnits,
            selected.stockUnits,
            selected.purchaseQuantities,
            selected.quote.getProfitPerUnit(),
            String.format("%.2f", selected.quote.getRoiPercent()),
            selected.quote.getEstimatedProfitPerHour()
        );
    }

    private void evaluateFixedRecipe(FactoryRecipe recipe)
    {
        if (recipe == null || !isRecipeEligibleForAccount(recipe))
        {
            activePlan = null;
            activeRecipe = recipe;
            activeQuote = null;
            waitForFixedRecipeMarket("Fixed recipe is not currently eligible for this account/world");
            return;
        }

        // Keep the selected recipe visible/pinned while the market is being checked.
        // activePlan stays null until a new purchase is actually safe to start.
        activePlan = null;
        activeRecipe = recipe;

        if (isRecipeSellMarketBlocked(recipe))
        {
            activeQuote = priceService.quote(recipe, config);
            waitForFixedRecipeMarket(
                "Fixed recipe output is temporarily market-blocked: "
                    + recipe.getOutputItemName());
            return;
        }

        ProfitQuote quote = priceService.quote(recipe, config);
        activeQuote = quote;

        if (!quote.isValid())
        {
            String error = quote.getError() == null || quote.getError().isBlank()
                ? "price lookup unavailable"
                : quote.getError();
            log.info("Fixed recipe {} quote temporarily unavailable: {}", recipe, error);
            waitForFixedRecipeMarket(
                "Fixed recipe price temporarily unavailable: " + error);
            return;
        }

        // Profitability settings still protect new purchases. The important fixed-mode
        // behavior is that crossing the threshold does NOT discard the recipe or park
        // it in the generic multi-minute no-recipe state.
        if (quote.getProfitPerUnit() <= 0 || !quote.meets(config))
        {
            log.info(
                "Fixed recipe {} temporarily below configured margin: profit/unit={}, ROI={}%; required profit/unit>={}, ROI>={}%",
                recipe,
                quote.getProfitPerUnit(),
                String.format("%.2f", quote.getRoiPercent()),
                config.minimumProfitPerUnit(),
                config.minimumRoiPercent()
            );
            waitForFixedRecipeMarket(String.format(
                "Fixed recipe below margin: %,d gp/unit, %.2f%% ROI; rechecking",
                quote.getProfitPerUnit(),
                quote.getRoiPercent()
            ));
            return;
        }

        String limitBlockedInput = findLimitBlockedMissingInput(recipe);
        if (limitBlockedInput != null)
        {
            int blockedItemId = priceService.getItemId(limitBlockedInput);
            long reset = blockedItemId <= 0
                ? 0L
                : buyLimitTracker.getMillisUntilNextReset(blockedItemId);
            log.info(
                "Fixed recipe {} waiting for GE buy-limit capacity on {}",
                recipe,
                limitBlockedInput
            );
            handleLimitExhaustion(reset);
            return;
        }

        CyclePlan plan = createCyclePlan(recipe, quote);
        if (plan.targetUnits <= 0)
        {
            if (plan.nextLimitResetMillis > 0)
            {
                handleLimitExhaustion(plan.nextLimitResetMillis);
                return;
            }

            refreshCoinSnapshot();
            waitForFixedRecipeMarket(String.format(
                "Fixed recipe has no affordable batch: %,d coins spendable (%,d total, %,d reserve)",
                observedSpendableCoins,
                observedCoinTotal,
                config.cashReserve()
            ));
            return;
        }

        activatePlan(plan);
        status = "Selected fixed recipe: " + activeRecipe.getDisplayName();
        log.info(
            "Selected fixed {}: target={}, stock={}, purchases={}, profit/unit={}, ROI={}%, estimated hourly={}",
            plan.recipe,
            plan.targetUnits,
            plan.stockUnits,
            plan.purchaseQuantities,
            plan.quote.getProfitPerUnit(),
            String.format("%.2f", plan.quote.getRoiPercent()),
            plan.quote.getEstimatedProfitPerHour()
        );
    }

    private boolean startExistingBankBacklog()
    {
        List<CyclePlan> stockPlans = new ArrayList<>();
        // Fixed mode must remain fixed even when unrelated processable materials
        // are already present in the bank. Automatic mode may drain any backlog.
        List<FactoryRecipe> backlogRecipes = config.mode() == FactoryMode.FIXED_RECIPE
            ? Collections.singletonList(config.fixedRecipe())
            : Arrays.asList(FactoryRecipe.values());
        for (FactoryRecipe recipe : backlogRecipes)
        {
            if (!isRecipeEligibleForAccount(recipe))
            {
                continue;
            }
            // Preserve already-banked inputs when their primary output just failed
            // the sale retry policy; do not manufacture more of a temporarily
            // unsellable output while alternatives are available.
            if (isRecipeSellMarketBlocked(recipe))
            {
                continue;
            }

            int stockUnits = calculateBankStockUnits(recipe);
            if (stockUnits <= 0)
            {
                continue;
            }

            // A backlog cycle intentionally has no purchase quantities. The quote is
            // retained for overlay/ranking information, but existing bank stock is
            // finished even when the current market margin is below the normal entry
            // threshold because no new capital is being committed to these inputs.
            ProfitQuote quote = priceService.quote(recipe, config);
            stockPlans.add(new CyclePlan(
                recipe,
                quote,
                stockUnits,
                stockUnits,
                Collections.emptyMap(),
                Collections.emptySet(),
                0L,
                stockUnits
            ));
        }

        if (!stockPlans.isEmpty())
        {
            CyclePlan selected;
            if (config.mode() == FactoryMode.FIXED_RECIPE)
            {
                selected = stockPlans.stream()
                    .filter(plan -> plan.recipe == config.fixedRecipe())
                    .findFirst()
                    .orElse(null);
            }
            else
            {
                selected = selectBestBacklogPlan(stockPlans);
            }

            if (selected != null)
            {
                activatePlan(selected);
                status = "Finishing banked inputs: " + selected.recipe.getDisplayName()
                    + " x" + selected.targetUnits;
                log.info(
                    "Bank backlog first: processing {} existing units of {} before any new GE purchase",
                    selected.targetUnits,
                    selected.recipe
                );
                return true;
            }
        }

        if (!config.sellOutputs())
        {
            return false;
        }

        ExistingOutputBacklog outputBacklog = findExistingOutputBacklog();
        if (outputBacklog == null)
        {
            return false;
        }

        ProfitQuote quote = priceService.quote(outputBacklog.recipe, config);
        activeRecipe = outputBacklog.recipe;
        activeQuote = quote;
        activePlan = new CyclePlan(
            outputBacklog.recipe,
            quote,
            0,
            0,
            Collections.emptyMap(),
            Collections.emptySet(),
            0L,
            1
        );
        cycleTargetUnits = 0;
        cycleProcessedUnits = 0;
        processFailures = 0;
        prepareSaleOutputQueue(outputBacklog.recipe, outputBacklog.outputName);
        if (!selectNextSaleOutputFromBank())
        {
            resetCycle();
            return false;
        }
        state = FactoryState.SELLING_OUTPUT;
        status = "Selling banked output first: " + sellingItemName
            + " x" + outputRemainingToSell;
        log.info(
            "Bank backlog first: selling {} existing {} before any new GE purchase",
            outputRemainingToSell,
            sellingItemName
        );
        return true;
    }

    private CyclePlan selectBestBacklogPlan(List<CyclePlan> plans)
    {
        return plans.stream()
            .max(Comparator
                .comparingInt((CyclePlan plan) -> plan.quote != null && plan.quote.isValid() ? 1 : 0)
                .thenComparingInt(plan -> plan.quote == null ? 0 : plan.quote.getEstimatedProfitPerHour())
                .thenComparingInt(plan -> plan.stockUnits))
            .orElse(null);
    }

    private ExistingOutputBacklog findExistingOutputBacklog()
    {
        ExistingOutputBacklog best = null;
        Set<String> seenOutputs = new HashSet<>();

        List<FactoryRecipe> outputRecipes = config.mode() == FactoryMode.FIXED_RECIPE
            ? Collections.singletonList(config.fixedRecipe())
            : Arrays.asList(FactoryRecipe.values());

        for (FactoryRecipe recipe : outputRecipes)
        {
            if (!isRecipeEligibleForAccount(recipe))
            {
                continue;
            }

            for (String outputName : recipe.getSaleOutputItemNames())
            {
                if (outputName == null || !seenOutputs.add(outputName.toLowerCase(Locale.ROOT)))
                {
                    continue;
                }

                int outputId = priceService.getItemId(outputName);
                if (outputId <= 0)
                {
                    continue;
                }
                if (isSellMarketBlocked(outputId))
                {
                    continue;
                }

                int quantity = bankCount(outputId) + inventoryCountByName(outputName);
                if (quantity <= 0)
                {
                    continue;
                }

                // In fixed mode, the fixed recipe's own output/byproducts win
                // immediately. Automatic mode drains the largest finished stack.
                ExistingOutputBacklog candidate = new ExistingOutputBacklog(recipe, outputName, quantity);
                if (config.mode() == FactoryMode.FIXED_RECIPE && recipe == config.fixedRecipe())
                {
                    return candidate;
                }
                if (best == null || candidate.quantity > best.quantity)
                {
                    best = candidate;
                }
            }
        }
        return best;
    }

    private void activatePlan(CyclePlan selected)
    {
        activePlan = selected;
        activeRecipe = selected.recipe;
        activeQuote = selected.quote;
        cycleTargetUnits = selected.targetUnits;
        cycleProcessedUnits = 0;
        outputRemainingToSell = 0;
        sellingItemName = null;
        saleOutputQueue.clear();
        saleOutputIndex = 0;
        processFailures = 0;
        state = FactoryState.PREPARING_CYCLE;
    }

    private CyclePlan selectBestPlan(List<CyclePlan> plans)
    {
        return plans.stream()
            .max(Comparator
                .comparingDouble(CyclePlan::selectionScore)
                .thenComparingInt(plan -> plan.quote.getProfitPerUnit()))
            .orElse(null);
    }

    private void prepareCycle()
    {
        if (activePlan == null || activeRecipe == null)
        {
            state = FactoryState.EVALUATING_RECIPES;
            return;
        }

        membersAccount = detectMembersAccount();
        memberWorld = detectMemberWorld();
        if (!isRecipeEligibleForAccount(activeRecipe))
        {
            if (activeRecipe.isMembersOnly() && !membersAccount)
            {
                finishOrReevaluate(activeRecipe.getDisplayName() + " requires a members account");
            }
            else if (activeRecipe.isMembersOnly() && !memberWorld)
            {
                finishOrReevaluate(activeRecipe.getDisplayName() + " requires a members world");
            }
            else
            {
                finishOrReevaluate(String.format(
                    "%s requires %d %s",
                    activeRecipe.getDisplayName(),
                    activeRecipe.getRequiredLevel(),
                    activeRecipe.getRequiredSkill().name()
                ));
            }
            return;
        }

        if (!ensureBankOpen())
        {
            return;
        }

        if (!activePlan.purchaseQuantities.isEmpty() && config.buyInputs())
        {
            state = FactoryState.BUYING_INPUTS;
            status = "Buying inputs";
            return;
        }

        int available = calculateBankStockUnits(activeRecipe);
        cycleTargetUnits = Math.min(cycleTargetUnits, available);
        if (cycleTargetUnits <= 0)
        {
            finishOrReevaluate("No processable materials available");
            return;
        }

        state = FactoryState.PREPARING_INVENTORY;
        status = "Preparing inventory";
    }

    private void buyInputs()
    {
        if (activePlan == null || activePlan.purchaseQuantities.isEmpty())
        {
            state = FactoryState.OPENING_BANK;
            return;
        }

        // Fund the whole outstanding purchase plan once, then open the GE and place
        // every distinct missing input into its own free GE slot before waiting.
        // This keeps multiple input offers live concurrently instead of serializing them.
        if (!ensureCoinsForPurchases(activePlan))
        {
            finishOrReevaluate("Not enough spendable coins");
            return;
        }

        if (Rs2Bank.isOpen())
        {
            Rs2Bank.closeBank();
            sleepUntil(() -> !Rs2Bank.isOpen(), BANK_OPEN_TIMEOUT_MILLIS);
        }
        if (!ensureGrandExchangeOpen())
        {
            return;
        }

        placeOutstandingInputOffers();

        Map<String, Integer> remainingPurchases = new LinkedHashMap<>(activePlan.purchaseQuantities);
        for (Map.Entry<String, Integer> entry : remainingPurchases.entrySet())
        {
            int requested = Math.max(0, entry.getValue());
            if (requested <= 0)
            {
                activePlan.purchaseQuantities.remove(entry.getKey());
                continue;
            }

            status = "Buying " + entry.getKey();
            int bought = executeBuyOffer(entry.getKey(), requested);
            int remaining = Math.max(0, requested - bought);
            if (remaining <= 0)
            {
                activePlan.purchaseQuantities.remove(entry.getKey());
                continue;
            }

            activePlan.purchaseQuantities.put(entry.getKey(), remaining);
            if (!waitingForGeSlot && !waitingForExistingGeOffer)
            {
                status = "Waiting for remaining " + entry.getKey() + " (" + remaining + ")";
            }

            // Other missing inputs may already have live offers in parallel. Continue
            // through the purchase map so each active GE slot can be monitored/repriced
            // during this pass instead of letting the first unfinished item block the rest.
            state = FactoryState.BUYING_INPUTS;
        }

        if (!activePlan.purchaseQuantities.isEmpty())
        {
            state = FactoryState.BUYING_INPUTS;
            return;
        }

        Rs2GrandExchange.collectAllToBank();
        sleep(400, 800);
        FactoryGrandExchangeInvoker.closeExchangeWithoutMouse();

        state = FactoryState.OPENING_BANK;
        status = "Checking purchased materials";
    }

    private void placeOutstandingInputOffers()
    {
        if (activePlan == null || activePlan.purchaseQuantities.isEmpty())
        {
            return;
        }

        GrandExchangeSlots[] freeSlots = Rs2GrandExchange.getAvailableSlots();
        if (freeSlots == null || freeSlots.length == 0)
        {
            waitingForGeSlot = true;
            status = "All available GE slots occupied; waiting";
            return;
        }

        List<BuyPlacementCandidate> candidates = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : new LinkedHashMap<>(activePlan.purchaseQuantities).entrySet())
        {
            String itemName = entry.getKey();
            int requested = Math.max(0, entry.getValue());
            int itemId = priceService.getItemId(itemName);
            if (requested <= 0 || itemId <= 0)
            {
                continue;
            }

            // Never place the same input item in more than one GE slot. If an offer
            // for this item is already live, keep monitoring/repricing that exact slot
            // instead of creating a duplicate offer in another free slot.
            if (findMatchingOfferSlot(itemId, false) != null)
            {
                continue;
            }

            int pendingCredit = Math.max(0, pendingCollectedBuyCredits.getOrDefault(itemId, 0));
            int stillNeeded = Math.max(0, requested - pendingCredit);
            if (stillNeeded <= 0)
            {
                continue;
            }

            int officialLimit = priceService.getTradeLimit(itemId, config.unknownItemLimit());
            int remainingCapacity = buyLimitTracker.getRemainingCapacity(
                itemId,
                officialLimit,
                config.buyLimitUsagePercent(),
                config.coldStartReservePercent()
            );
            int quantity = Math.min(stillNeeded, remainingCapacity);
            if (quantity <= 0)
            {
                continue;
            }

            int retry = Math.max(0, Math.min(
                config.maxPriceRetries(),
                buyPriceRetryByItem.getOrDefault(itemId, 0)));
            int price = priceService.getBuyOfferPrice(itemId, config.buyMarkupPercent(), retry);
            if (price <= 0)
            {
                continue;
            }

            candidates.add(new BuyPlacementCandidate(itemName, itemId, quantity, price, retry));
        }

        if (candidates.isEmpty())
        {
            return;
        }

        // Use one GE slot per distinct missing input. A single item's complete
        // requested quantity is placed in one offer; free slots are only used for
        // other distinct inputs. This avoids three competing offers for the same item.
        int offersToPlace = Math.min(freeSlots.length, candidates.size());
        int placed = 0;
        for (int slotIndex = 0; slotIndex < offersToPlace; slotIndex++)
        {
            BuyPlacementCandidate candidate = candidates.get(slotIndex);
            int quantity = candidate.quantity;
            GrandExchangeSlots slot = freeSlots[slotIndex];

            status = "Placing " + candidate.itemName + " x" + quantity
                + " in GE slot " + (slot.ordinal() + 1);
            if (!FactoryGrandExchangeInvoker.placeBuyOffer(
                slot,
                candidate.itemName,
                candidate.itemId,
                quantity,
                candidate.price))
            {
                beginGePlacementBackoff();
                waitingForExistingGeOffer = true;
                String reason = FactoryGrandExchangeInvoker.getLastFailureReason();
                status = "GE buy failed: " + reason;
                logGePlacementFailure("buy", candidate.itemName, quantity, candidate.price, reason);
                return;
            }

            clearGePlacementBackoff();
            GrandExchangeSlots actualSlot = waitForOfferSlot(candidate.itemId, false, slot);
            if (actualSlot == null)
            {
                waitingForExistingGeOffer = true;
                status = "Offer placed; waiting for GE slot sync: " + candidate.itemName;
                return;
            }

            factoryBuyOfferSlots.add(actualSlot);
            factoryBuyOfferPrices.put(actualSlot, candidate.price);
            factoryBuyOfferRetries.put(actualSlot, candidate.retry);
            buyPriceRetryByItem.putIfAbsent(candidate.itemId, candidate.retry);
            placed++;
        }

        if (placed > 0)
        {
            status = placed == 1
                ? "Placed 1 distinct input buy offer"
                : "Placed " + placed + " distinct input buy offers in parallel";
        }
    }

    private int executeBuyOffer(String itemName, int requestedQuantity)
    {
        clearGeWaitFlags();

        if (isGePlacementBackoffActive())
        {
            waitingForExistingGeOffer = true;
            status = "Recovering GE interface; retrying in " + gePlacementBackoffSeconds() + "s";
            return 0;
        }

        int itemId = priceService.getItemId(itemName);
        if (itemId <= 0 || requestedQuantity <= 0)
        {
            return 0;
        }

        // A single Collect-all click can collect several completed parallel input
        // offers. Consume those already-accounted credits before placing anything new.
        int collectedCredit = consumePendingBuyCredit(itemId, requestedQuantity);
        if (collectedCredit > 0)
        {
            status = "Credited collected " + itemName + " x" + collectedCredit;
            return collectedCredit;
        }

        // If every factory-owned parallel BUY is already terminal, press the GE
        // Collect button before inspecting individual slots. This snapshots and
        // credits every completed slot atomically so a Collect-all cannot cause a
        // second purchase of an item that was collected in the same click.
        if (collectTrackedFactoryBuyOffersIfReady())
        {
            collectedCredit = consumePendingBuyCredit(itemId, requestedQuantity);
            if (collectedCredit > 0)
            {
                status = "Collected " + itemName + " x" + collectedCredit;
                return collectedCredit;
            }
        }

        int officialLimit = priceService.getTradeLimit(itemId, config.unknownItemLimit());
        int remainingCapacity = buyLimitTracker.getRemainingCapacity(
            itemId,
            officialLimit,
            config.buyLimitUsagePercent(),
            config.coldStartReservePercent()
        );
        int quantityRemaining = Math.min(requestedQuantity, remainingCapacity);
        if (quantityRemaining <= 0)
        {
            log.info("Buy limit exhausted for {}", itemName);
            return 0;
        }

        GrandExchangeSlots trackedFactoryOffer = findTrackedFactoryBuyOfferSlot(itemId);
        GrandExchangeSlots existingOffer = trackedFactoryOffer != null
            ? trackedFactoryOffer
            : findMatchingOfferSlot(itemId, false);
        if (existingOffer != null)
        {
            GrandExchangeOffer offer = getOffer(existingOffer);
            int filled = offer == null ? 0 : Math.max(0, offer.getQuantitySold());
            int total = offer == null ? requestedQuantity : Math.max(1, offer.getTotalQuantity());
            boolean factoryOwned = factoryBuyOfferSlots.contains(existingOffer);

            if (!factoryOwned)
            {
                // Observe pre-existing player offers without modifying them. Completed
                // offers are always collected with the Grand Exchange's top Collect
                // button. The helper snapshots/reconciles any factory-owned completed
                // BUY offers that the same Collect-all click may clear.
                if (offer != null && isFinishedBuyState(offer.getState()))
                {
                    if (!collectCompletedOfferWithButton(existingOffer, "existing " + itemName + " buy"))
                    {
                        waitingForExistingGeOffer = true;
                        return 0;
                    }
                    int credited = Math.min(requestedQuantity, filled);
                    status = "Collected existing " + itemName + " offer (" + filled + "/" + total + ")";
                    return credited;
                }

                waitingForExistingGeOffer = true;
                status = "Waiting for existing " + itemName + " offer (" + filled + "/" + total + ")";
                return 0;
            }

            OfferWaitResult result = waitForOfferResult(
                existingOffer,
                itemId,
                false,
                Math.min(requestedQuantity, total),
                TimeUnit.SECONDS.toMillis(config.offerTimeoutSeconds()),
                itemName
            );

            if (result.completed || result.cancelled)
            {
                if (!collectTrackedFactoryBuyOffersIfReady())
                {
                    waitingForExistingGeOffer = true;
                    status = "Input offer finished; waiting for other factory buys before Collect";
                    return 0;
                }
                int credit = consumePendingBuyCredit(itemId, requestedQuantity);
                status = "Collected " + itemName + " x" + credit;
                return credit;
            }

            if (result.missing)
            {
                waitingForExistingGeOffer = true;
                status = "Waiting to rediscover buy offer: " + itemName;
                return 0;
            }

            if (!config.abortStalledOffers())
            {
                waitingForExistingGeOffer = true;
                status = "Buy offer active: " + itemName + " (" + result.filledQuantity + "/" + total + ")";
                return 0;
            }

            int currentRetry = Math.max(0, factoryBuyOfferRetries.getOrDefault(
                existingOffer,
                buyPriceRetryByItem.getOrDefault(itemId, 0)));
            if (currentRetry < config.maxPriceRetries())
            {
                int nextRetry = currentRetry + 1;
                int newPrice = calculateBuyReprice(itemId, existingOffer, nextRetry);
                status = "Modifying buy offer: " + itemName + " @ " + newPrice;
                if (!FactoryGrandExchangeInvoker.modifyOfferPrice(existingOffer, itemId, newPrice))
                {
                    waitingForExistingGeOffer = true;
                    status = "Modify offer failed for " + itemName + ": "
                        + FactoryGrandExchangeInvoker.getLastFailureReason();
                    return 0;
                }
                factoryBuyOfferRetries.put(existingOffer, nextRetry);
                buyPriceRetryByItem.merge(itemId, nextRetry, Math::max);
                factoryBuyOfferPrices.put(existingOffer, newPrice);
                waitingForExistingGeOffer = true;
                status = "Modified buy offer: " + itemName + " @ " + newPrice;
                return 0;
            }

            // Maximum reprices reached. Cancel this exact slot without collecting;
            // Collect-all is intentionally deferred until every tracked input offer
            // is terminal so one click can be accounted for atomically.
            if (!FactoryGrandExchangeInvoker.cancelOfferWithoutCollect(existingOffer, itemId))
            {
                waitingForExistingGeOffer = true;
                status = "Unable to cancel stalled buy offer: "
                    + FactoryGrandExchangeInvoker.getLastFailureReason();
                return 0;
            }
            boolean cancelled = sleepUntil(() ->
            {
                GrandExchangeOffer current = getOffer(existingOffer);
                return current != null && isFinishedBuyState(current.getState());
            }, 4_000);
            if (!cancelled)
            {
                waitingForExistingGeOffer = true;
                status = "Waiting for cancelled buy offer state: " + itemName;
                return 0;
            }
            if (collectTrackedFactoryBuyOffersIfReady())
            {
                return consumePendingBuyCredit(itemId, requestedQuantity);
            }
            waitingForExistingGeOffer = true;
            status = "Cancelled " + itemName + "; waiting for other input offers before Collect";
            return 0;
        }

        if (!ensureFreeGeSlot())
        {
            return 0;
        }

        GrandExchangeSlots reservedSlot = getFirstAvailableGeSlot();
        if (reservedSlot == null)
        {
            waitingForGeSlot = true;
            status = "Waiting for a free GE slot";
            return 0;
        }

        int retry = Math.max(0, Math.min(
            config.maxPriceRetries(),
            buyPriceRetryByItem.getOrDefault(itemId, 0)));
        int price = priceService.getBuyOfferPrice(itemId, config.buyMarkupPercent(), retry);
        if (price <= 0)
        {
            log.warn("Unable to determine a buy price for {}", itemName);
            return 0;
        }

        if (!FactoryGrandExchangeInvoker.placeBuyOffer(
            reservedSlot, itemName, itemId, quantityRemaining, price
        ))
        {
            beginGePlacementBackoff();
            waitingForExistingGeOffer = true;
            String reason = FactoryGrandExchangeInvoker.getLastFailureReason();
            status = "GE buy failed: " + reason;
            logGePlacementFailure("buy", itemName, quantityRemaining, price, reason);
            return 0;
        }

        clearGePlacementBackoff();

        GrandExchangeSlots slot = waitForOfferSlot(itemId, false, reservedSlot);
        if (slot == null)
        {
            waitingForExistingGeOffer = true;
            status = "Offer placed; waiting for GE slot sync: " + itemName;
            log.warn("Could not identify newly placed buy offer for {} by item ID {}", itemName, itemId);
            return 0;
        }

        factoryBuyOfferSlots.add(slot);
        factoryBuyOfferPrices.put(slot, price);
        factoryBuyOfferRetries.put(slot, retry);
        buyPriceRetryByItem.putIfAbsent(itemId, retry);

        OfferWaitResult result = waitForOfferResult(
            slot,
            itemId,
            false,
            quantityRemaining,
            TimeUnit.SECONDS.toMillis(config.offerTimeoutSeconds()),
            itemName
        );

        if (result.completed || result.cancelled)
        {
            if (!collectTrackedFactoryBuyOffersIfReady())
            {
                waitingForExistingGeOffer = true;
                status = "Input offer finished; waiting for other factory buys before Collect";
                return 0;
            }
            return consumePendingBuyCredit(itemId, requestedQuantity);
        }

        if (result.missing)
        {
            waitingForExistingGeOffer = true;
            status = "Waiting to rediscover buy offer: " + itemName;
            return 0;
        }

        if (!config.abortStalledOffers())
        {
            waitingForExistingGeOffer = true;
            status = "Buy offer active: " + itemName + " (" + result.filledQuantity + "/" + quantityRemaining + ")";
            return 0;
        }

        int currentRetry = Math.max(retry, factoryBuyOfferRetries.getOrDefault(
            slot,
            buyPriceRetryByItem.getOrDefault(itemId, retry)));
        if (currentRetry < config.maxPriceRetries())
        {
            int nextRetry = currentRetry + 1;
            int newPrice = calculateBuyReprice(itemId, slot, nextRetry);
            status = "Modifying buy offer: " + itemName + " @ " + newPrice;
            if (!FactoryGrandExchangeInvoker.modifyOfferPrice(slot, itemId, newPrice))
            {
                waitingForExistingGeOffer = true;
                status = "Modify offer failed for " + itemName + ": "
                    + FactoryGrandExchangeInvoker.getLastFailureReason();
                return 0;
            }
            factoryBuyOfferRetries.put(slot, nextRetry);
            buyPriceRetryByItem.merge(itemId, nextRetry, Math::max);
            factoryBuyOfferPrices.put(slot, newPrice);
            waitingForExistingGeOffer = true;
            status = "Modified buy offer: " + itemName + " @ " + newPrice;
            return 0;
        }

        if (!FactoryGrandExchangeInvoker.cancelOfferWithoutCollect(slot, itemId))
        {
            waitingForExistingGeOffer = true;
            status = "Unable to cancel stalled buy offer: "
                + FactoryGrandExchangeInvoker.getLastFailureReason();
            return 0;
        }
        boolean cancelled = sleepUntil(() ->
        {
            GrandExchangeOffer current = getOffer(slot);
            return current != null && isFinishedBuyState(current.getState());
        }, 4_000);
        if (!cancelled)
        {
            waitingForExistingGeOffer = true;
            status = "Waiting for cancelled buy offer state: " + itemName;
            return 0;
        }
        if (collectTrackedFactoryBuyOffersIfReady())
        {
            return consumePendingBuyCredit(itemId, requestedQuantity);
        }
        waitingForExistingGeOffer = true;
        status = "Cancelled " + itemName + "; waiting for other input offers before Collect";
        return 0;
    }

    private void prepareProcessingInventory()
    {
        if (activeRecipe == null)
        {
            resetCycle();
            return;
        }

        if (!ensureBankOpen())
        {
            return;
        }

        if (!Rs2Bank.setWithdrawAsItem())
        {
            status = "Unable to set bank withdraw-as-item mode";
            return;
        }

        Rs2Bank.depositAll();
        sleepUntil(Rs2Inventory::isEmpty, INVENTORY_CHANGE_TIMEOUT_MILLIS);

        int unitsRemaining = Math.max(0, cycleTargetUnits - cycleProcessedUnits);
        int bankUnits = calculateBankStockUnits(activeRecipe);
        int batchUnits = Math.min(
            Math.min(activeRecipe.getMaximumInventoryBatch(), unitsRemaining),
            bankUnits
        );

        if (batchUnits <= 0)
        {
            beginSellingOrFinish();
            return;
        }

        for (RecipeInput input : activeRecipe.getInputs())
        {
            int itemId = priceService.getItemId(input.getItemName());
            if (itemId <= 0)
            {
                finishOrReevaluate("Unable to resolve " + input.getItemName());
                return;
            }

            int quantity = input.isConsumed()
                ? input.requiredForUnits(batchUnits)
                : 1;

            if (bankCount(itemId) < quantity)
            {
                beginSellingOrFinish();
                return;
            }

            if (!withdrawProcessingItemWithX(itemId, input.getItemName(), quantity))
            {
                status = "Withdraw-X failed for " + input.getItemName() + " x" + quantity;
                return;
            }
        }

        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), BANK_OPEN_TIMEOUT_MILLIS);
        state = FactoryState.PROCESSING;
        status = "Processing " + activeRecipe.getDisplayName();
    }


    /**
     * Processing inventory preparation must always use one verified bank Withdraw-X
     * action for the complete required quantity. The factory owns the amount prompt
     * and Enter checks so a stale bank/chatbox transition cannot receive the key.
     * Treat a partial withdrawal as a failed atomic withdrawal: bank the partial amount
     * and retry the full X quantity instead of topping up one item at a time.
     */
    private boolean withdrawProcessingItemWithX(int itemId, String itemName, int quantity)
    {
        if (quantity <= 0)
        {
            return true;
        }
        if (!Rs2Bank.isOpen())
        {
            return false;
        }

        for (int attempt = 1; attempt <= MAX_PROCESS_WITHDRAW_X_ATTEMPTS; attempt++)
        {
            int current = inventoryCount(itemId);
            if (current == quantity)
            {
                return true;
            }

            // Never turn a partial/incorrect withdrawal into a sequence of small top-ups.
            // Reset this item to zero and retry one complete Withdraw-X operation.
            if (current != 0)
            {
                log.warn(
                    "Processing Withdraw-X reset: {} expected={} inventory={} attempt={}/{}",
                    itemName,
                    quantity,
                    current,
                    attempt,
                    MAX_PROCESS_WITHDRAW_X_ATTEMPTS
                );
                if (!Rs2Bank.depositAll(itemId))
                {
                    return false;
                }
                if (!sleepUntil(
                    () -> inventoryCount(itemId) == 0,
                    INVENTORY_CHANGE_TIMEOUT_MILLIS))
                {
                    return false;
                }
            }

            status = "Withdraw-X " + itemName + " x" + quantity
                + " (" + attempt + "/" + MAX_PROCESS_WITHDRAW_X_ATTEMPTS + ")";

            if (!withdrawXWithVerifiedBankPrompt(itemId, itemName, quantity))
            {
                log.warn(
                    "Processing Withdraw-X invoke failed: {} x{} attempt={}/{}",
                    itemName,
                    quantity,
                    attempt,
                    MAX_PROCESS_WITHDRAW_X_ATTEMPTS
                );
                continue;
            }

            boolean exactQuantityReached = sleepUntil(
                () -> inventoryCount(itemId) == quantity,
                INVENTORY_CHANGE_TIMEOUT_MILLIS
            );
            if (exactQuantityReached)
            {
                log.debug(
                    "Processing Withdraw-X complete: {} x{} attempt={}",
                    itemName,
                    quantity,
                    attempt
                );
                return true;
            }

            log.warn(
                "Processing Withdraw-X made no exact progress: {} expected={} inventory={} attempt={}/{}",
                itemName,
                quantity,
                inventoryCount(itemId),
                attempt,
                MAX_PROCESS_WITHDRAW_X_ATTEMPTS
            );
        }

        // Leave the bank open. The normal state watchdog can safely re-enter bank
        // preparation, but processing must never begin with a partial ingredient stack.
        int leftover = inventoryCount(itemId);
        if (leftover != 0)
        {
            Rs2Bank.depositAll(itemId);
            sleepUntil(() -> inventoryCount(itemId) == 0, INVENTORY_CHANGE_TIMEOUT_MILLIS);
        }
        return false;
    }

    /**
     * Executes a bank Withdraw-X without using Rs2Bank.handleAmount()'s delayed,
     * unchecked Enter. Every keyboard step is bound to the exact live bank amount
     * prompt and the bank root widget.
     */
    private boolean withdrawXWithVerifiedBankPrompt(int itemId, String itemName, int quantity)
    {
        if (quantity <= 0)
        {
            return true;
        }

        if (!isBankRootVisible() || Rs2GrandExchange.isOpen())
        {
            log.warn("Factory suppressed bank Withdraw-X for {} x{}: bank root is not exclusively active",
                itemName, quantity);
            return false;
        }

        // Never stack a new Withdraw-X on top of an old numeric input layer.
        // If the *exact* bank amount prompt is left over from our previous attempt,
        // Escape is safe and intentional; any other input is left untouched.
        if (isExactBankAmountPromptOpen())
        {
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
            if (!sleepUntil(() -> !isExactBankAmountPromptOpen(), 800))
            {
                log.warn("Factory could not close stale verified bank amount prompt before {} x{}",
                    itemName, quantity);
                return false;
            }
        }
        else if (isAnyChatboxNumericInputVisible())
        {
            log.warn("Factory suppressed bank Withdraw-X for {} x{}: unrelated numeric input is active",
                itemName, quantity);
            return false;
        }

        // Quantity one never needs a keyboard prompt.
        if (quantity == 1)
        {
            return Rs2Bank.withdrawOne(itemId);
        }

        Rs2ItemModel bankItem = Rs2Bank.bankItems().stream()
            .filter(item -> item != null
                && (item.getId() == itemId
                    || (item.getName() != null && item.getName().equalsIgnoreCase(itemName))))
            .findFirst()
            .orElse(null);
        if (bankItem == null)
        {
            log.warn("Factory verified Withdraw-X could not resolve bank row for {} ({})",
                itemName, itemId);
            return false;
        }

        // Open the bank's literal Withdraw-X prompt directly. Identifier 6 is the
        // bank-item X-prompt action used by Microbot's own handleAmount() path.
        NewMenuEntry entry = new NewMenuEntry()
            .param0(bankItem.getSlot())
            .param1(Rs2Bank.BANK_ITEM_CONTAINER)
            .opcode(MenuAction.CC_OP.getId())
            .identifier(BANK_WITHDRAW_X_PROMPT_IDENTIFIER)
            .itemId(bankItem.getId())
            .target(bankItem.getName());

        Rectangle bounds = Rs2Bank.itemBounds(bankItem);
        if (bounds == null)
        {
            bounds = new Rectangle(1, 1);
        }

        Microbot.doInvoke(entry, bounds);

        if (!sleepUntil(
            () -> isExactBankAmountPromptOpen(),
            BANK_AMOUNT_PROMPT_TIMEOUT_MILLIS))
        {
            log.warn("Factory suppressed bank Enter for {} x{}: exact 'Enter amount:' widget never opened",
                itemName, quantity);
            return false;
        }

        // Re-resolve immediately before typing. Do not type into a prompt that was
        // valid only on the previous state-machine observation.
        if (!isExactBankAmountPromptOpen())
        {
            log.warn("Factory suppressed bank amount typing for {} x{}: prompt became stale",
                itemName, quantity);
            return false;
        }

        Rs2Keyboard.typeString(String.valueOf(quantity));
        sleep(80, 140);

        // Critical second widget check: this is the race missing from the generic
        // Rs2Bank helper. If the bank/prompt changed while typing, Enter is forbidden.
        if (!isExactBankAmountPromptOpen() || !bankAmountInputMatches(quantity))
        {
            log.warn("Factory suppressed bank Enter for {} x{}: prompt/input changed before confirmation",
                itemName, quantity);
            return false;
        }

        long remainingGap = BANK_ENTER_MIN_GAP_MILLIS
            - (System.currentTimeMillis() - lastBankEnterAt);
        if (remainingGap > 0L)
        {
            sleep((int) remainingGap);
        }

        // Third and final live check immediately at the keyboard event.
        if (!isExactBankAmountPromptOpen() || !bankAmountInputMatches(quantity))
        {
            log.warn("Factory suppressed bank Enter for {} x{} after cooldown: prompt/input is no longer exact",
                itemName, quantity);
            return false;
        }

        Microbot.log("KSP AIO Factory bank pressing Enter for verified 'Enter amount:' prompt: "
            + itemName + " x" + quantity);
        Rs2Keyboard.enter();
        lastBankEnterAt = System.currentTimeMillis();

        return sleepUntil(
            () -> !isExactBankAmountPromptOpen(),
            BANK_AMOUNT_PROMPT_TIMEOUT_MILLIS);
    }

    private boolean isBankRootVisible()
    {
        // Direct widget predicate: no bank-pin side effects.
        return Rs2Widget.isWidgetVisible(12, 1);
    }

    private boolean isExactBankAmountPromptOpen()
    {
        if (!isBankRootVisible() || Rs2GrandExchange.isOpen())
        {
            return false;
        }

        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            if (Microbot.getClient().getVarcIntValue(VarClientID.MESLAYERMODE)
                != BANK_CHATBOX_INPUT_MODE)
            {
                return false;
            }

            Widget prompt = Microbot.getClient().getWidget(
                BANK_AMOUNT_PROMPT_GROUP, BANK_AMOUNT_PROMPT_CHILD);
            Widget input = Microbot.getClient().getWidget(InterfaceID.Chatbox.MES_TEXT2);

            if (prompt == null || prompt.isHidden() || input == null || input.isHidden())
            {
                return false;
            }

            String promptText = prompt.getText();
            return promptText != null
                && BANK_AMOUNT_PROMPT.equalsIgnoreCase(
                    promptText.replaceAll("<[^>]*>", "").trim());
        }).orElse(false);
    }

    private boolean bankAmountInputMatches(int quantity)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Widget input = Microbot.getClient().getWidget(InterfaceID.Chatbox.MES_TEXT2);
            if (input == null || input.isHidden())
            {
                return false;
            }

            String text = input.getText();
            if (text == null)
            {
                return false;
            }

            String digits = text.replaceAll("[^0-9]", "");
            return digits.equals(String.valueOf(quantity));
        }).orElse(false);
    }

    private void processInventory()
    {
        if (activeRecipe == null)
        {
            resetCycle();
            return;
        }

        RecipeInput first = activeRecipe.getInteractionItemA();
        RecipeInput second = activeRecipe.getInteractionItemB();
        if (!Rs2Inventory.hasItem(first.getItemName())
            || (second != null && !Rs2Inventory.hasItem(second.getItemName())))
        {
            state = FactoryState.BANKING_OUTPUT;
            return;
        }

        int outputId = priceService.getItemId(activeRecipe.getOutputItemName());
        int outputBefore = inventoryCount(outputId);
        Map<String, Integer> inputBefore = new HashMap<>();
        for (RecipeInput input : activeRecipe.getInputs())
        {
            inputBefore.put(input.getItemName(), inventoryCount(priceService.getItemId(input.getItemName())));
        }
        int expectedUnits = deriveAvailableUnitsFromInputs(inputBefore);

        if (activeRecipe.isSingleItemInteraction())
        {
            // Direct inventory actions such as cleaning grimy herbs do not use an
            // item-on-item combination or production dialogue. Process every
            // prepared item through its configured inventory action.
            status = activeRecipe.getProcessingAction() + "ing " + activeRecipe.getOutputItemName();
            repeatSingleItemActions(first, outputId, outputBefore, expectedUnits);
        }
        else
        {
            if (!Rs2Inventory.combine(first.getItemName(), second.getItemName()))
            {
                registerProcessFailure("Unable to combine inputs");
                return;
            }

            boolean interfaceAppeared = sleepUntil(
                () -> isProductionDialogueOpen()
                    || inventoryCount(outputId) > outputBefore
                    || inputsChanged(inputBefore),
                PROCESS_START_TIMEOUT_MILLIS
            );

            if (interfaceAppeared
                && isProductionDialogueOpen()
                && inventoryCount(outputId) <= outputBefore)
            {
                if (activeRecipe.requiresProductionOptionSelection())
                {
                    status = "Selecting " + activeRecipe.getProductionOptionText();
                    if (!selectProductionProduct(
                        activeRecipe.getProductionOptionText(),
                        outputId))
                    {
                        registerProcessFailure(
                            "Unable to select " + activeRecipe.getProductionOptionText());
                        return;
                    }
                    sleep(250, 450);
                }

                if (!isAllProductionQuantityEnabled())
                {
                    status = "Enabling production quantity: All";
                    if (!clickAllProductionQuantity())
                    {
                        registerProcessFailure("Unable to enable All production quantity");
                        return;
                    }

                    sleepUntil(
                        () -> isAllProductionQuantityEnabled()
                            || !isProductionDialogueOpen()
                            || inventoryCount(outputId) > outputBefore
                            || inputsChanged(inputBefore),
                        PROCESS_START_TIMEOUT_MILLIS
                    );
                }

                if (isProductionDialogueOpen()
                    && inventoryCount(outputId) <= outputBefore
                    && !inputsChanged(inputBefore))
                {
                    status = "Starting production";
                    if (!pressSpaceForProduction(outputId))
                    {
                        // Do not blindly retry a keyboard action. The script will
                        // either observe normal production progress below or fail
                        // this processing attempt and rebuild state safely.
                        log.warn("Factory suppressed Space because the exact production widget target was not valid");
                    }
                }

                boolean productionStarted = sleepUntil(
                    () -> !isProductionDialogueOpen()
                        || inventoryCount(outputId) > outputBefore
                        || inputsChanged(inputBefore),
                    PROCESS_START_TIMEOUT_MILLIS
                );

                if (!productionStarted)
                {
                    registerProcessFailure("Production dialogue did not start");
                    return;
                }

                if (antiban != null)
                {
                    antiban.onProductionStarted();
                }
            }
            else if (interfaceAppeared && inventoryCount(outputId) > outputBefore)
            {
                // Item-on-item recipes such as amulet/symbol stringing, toppings,
                // dyes, capes, cakes and soft clay complete one item per combine.
                // Keep combining the remaining prepared pairs instead of waiting for
                // a production interface that these recipes never open.
                status = "Processing immediate combines";
                repeatImmediateCombinations(first, second, outputId, outputBefore, expectedUnits);
            }
        }

        boolean completedBatch = isBatchComplete(inputBefore, outputBefore, outputId, expectedUnits)
            || sleepUntil(
                () -> isBatchComplete(inputBefore, outputBefore, outputId, expectedUnits),
                PROCESS_FINISH_TIMEOUT_MILLIS
            );

        int made = Math.max(0, inventoryCount(outputId) - outputBefore);
        if (made <= 0)
        {
            made = deriveUnitsMadeFromInputs(inputBefore);
        }

        if (made <= 0)
        {
            registerProcessFailure("No processing progress detected");
            return;
        }

        processFailures = 0;
        cycleProcessedUnits += made;
        stats.recordProcessed(made);
        state = FactoryState.BANKING_OUTPUT;
        status = completedBatch
            ? "Banking " + activeRecipe.getOutputItemName()
            : "Banking partial batch (" + made + ")";
    }

    private void repeatSingleItemActions(
        RecipeInput input,
        int outputId,
        int outputBefore,
        int expectedUnits)
    {
        int inputId = priceService.getItemId(input.getItemName());
        int attempts = 0;
        int maximumAttempts = Math.max(1, expectedUnits + 2);

        while (inventoryCount(outputId) - outputBefore < expectedUnits
            && attempts < maximumAttempts
            && Rs2Inventory.hasItem(input.getItemName()))
        {
            int outputAtStepStart = inventoryCount(outputId);
            int inputAtStepStart = inventoryCount(inputId);

            if (!Rs2Inventory.interact(inputId, activeRecipe.getProcessingAction()))
            {
                break;
            }

            boolean progressed = sleepUntil(
                () -> inventoryCount(outputId) > outputAtStepStart
                    || inventoryCount(inputId) < inputAtStepStart,
                IMMEDIATE_COMBINE_STEP_TIMEOUT_MILLIS
            );
            if (!progressed)
            {
                break;
            }

            attempts++;
            if (antiban != null)
            {
                int rhythmPause = antiban.immediateCombineRhythmPauseMillis();
                if (rhythmPause > 0)
                {
                    sleep(rhythmPause);
                }
            }
        }
    }

    private void repeatImmediateCombinations(
        RecipeInput first,
        RecipeInput second,
        int outputId,
        int outputBefore,
        int expectedUnits)
    {
        int firstId = priceService.getItemId(first.getItemName());
        int secondId = priceService.getItemId(second.getItemName());
        int attempts = 0;
        int maximumAttempts = Math.max(1, expectedUnits + 2);

        while (inventoryCount(outputId) - outputBefore < expectedUnits
            && attempts < maximumAttempts
            && Rs2Inventory.hasItem(first.getItemName())
            && Rs2Inventory.hasItem(second.getItemName()))
        {
            int outputAtStepStart = inventoryCount(outputId);
            int firstAtStepStart = inventoryCount(firstId);
            int secondAtStepStart = inventoryCount(secondId);

            if (!Rs2Inventory.combine(first.getItemName(), second.getItemName()))
            {
                break;
            }

            boolean progressed = sleepUntil(
                () -> inventoryCount(outputId) > outputAtStepStart
                    || inventoryCount(firstId) < firstAtStepStart
                    || inventoryCount(secondId) < secondAtStepStart
                    || isProductionDialogueOpen(),
                IMMEDIATE_COMBINE_STEP_TIMEOUT_MILLIS
            );
            if (!progressed || isProductionDialogueOpen())
            {
                break;
            }
            attempts++;
            if (antiban != null)
            {
                int rhythmPause = antiban.immediateCombineRhythmPauseMillis();
                if (rhythmPause > 0)
                {
                    sleep(rhythmPause);
                }
            }
        }
    }

    private boolean selectProductionProduct(String optionText, int outputId)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Widget productionRoot = Microbot.getClient().getWidget(InterfaceID.SKILLMULTI, 0);
            if (productionRoot == null || productionRoot.isHidden())
            {
                return false;
            }

            Widget product = findProductionItemWidget(productionRoot, outputId);
            if (product == null && optionText != null && !optionText.isBlank())
            {
                product = Rs2Widget.searchChildren(optionText, productionRoot, true);
            }
            return product != null
                && !product.isHidden()
                && Rs2Widget.clickWidget(product);
        }).orElse(false);
    }

    private Widget findProductionItemWidget(Widget widget, int itemId)
    {
        if (widget == null || widget.isHidden())
        {
            return null;
        }
        if (itemId > 0 && widget.getItemId() == itemId)
        {
            return widget;
        }

        Widget[][] groups = {
            widget.getChildren(),
            widget.getNestedChildren(),
            widget.getDynamicChildren(),
            widget.getStaticChildren()
        };
        for (Widget[] group : groups)
        {
            if (group == null)
            {
                continue;
            }
            for (Widget child : group)
            {
                Widget found = findProductionItemWidget(child, itemId);
                if (found != null)
                {
                    return found;
                }
            }
        }
        return null;
    }

    private boolean isProductionDialogueOpen()
    {
        return getVisibleProductionRoot() != null
            && (Rs2Widget.isProductionWidgetOpen()
                || Rs2Widget.hasWidget("How many do you wish to make")
                || Rs2Widget.hasWidget("How many would you like to make")
                || Rs2Widget.hasWidget("Choose a quantity"));
    }

    /**
     * Strict keyboard gate for production. Space is a global key event, so merely
     * remembering that a production dialogue was open on the previous tick is not
     * enough. Re-resolve the live SKILLMULTI root immediately before the keypress,
     * prove that the recipe's output/product still belongs to that root, prove All
     * is selected, and reject the key if a GE/chatbox numeric input is visible.
     */
    private boolean pressSpaceForProduction(int outputId)
    {
        if (!isProductionKeyboardTargetValid(outputId))
        {
            return false;
        }

        long now = System.currentTimeMillis();
        long remainingGap = FACTORY_SPACE_MIN_GAP_MILLIS - (now - lastFactorySpaceAt);
        if (remainingGap > 0L)
        {
            sleep((int) Math.min(Integer.MAX_VALUE, remainingGap));
        }

        // Re-resolve after a short settle because the production widget can close
        // between selection/quantity clicks and this global keyboard event.
        sleep(80, 140);
        if (!isProductionKeyboardTargetValid(outputId))
        {
            return false;
        }

        Microbot.log("KSP AIO Factory pressing Space for verified production widget: "
            + activeRecipe.getDisplayName());
        Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
        lastFactorySpaceAt = System.currentTimeMillis();
        return true;
    }

    private boolean isProductionKeyboardTargetValid(int outputId)
    {
        if (activeRecipe == null || outputId <= 0 || !isAllProductionQuantityEnabled())
        {
            return false;
        }

        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Widget productionRoot = Microbot.getClient().getWidget(InterfaceID.SKILLMULTI, 0);
            if (productionRoot == null || productionRoot.isHidden())
            {
                return false;
            }

            // Never send Space while the GE is open or a chatbox numeric entry
            // widget is active. Those interfaces also consume keyboard input.
            Widget numericInput = Microbot.getClient().getWidget(InterfaceID.Chatbox.MES_TEXT2);
            if ((numericInput != null && !numericInput.isHidden())
                || Rs2GrandExchange.isOpen()
                || Rs2GrandExchange.isOfferScreenOpen())
            {
                return false;
            }

            Widget product = findProductionItemWidget(productionRoot, outputId);
            if (product != null && !product.isHidden())
            {
                return true;
            }

            if (activeRecipe.requiresProductionOptionSelection())
            {
                String optionText = activeRecipe.getProductionOptionText();
                Widget option = optionText == null || optionText.isBlank()
                    ? null
                    : Rs2Widget.searchChildren(optionText, productionRoot, true);
                return option != null && !option.isHidden();
            }

            return false;
        }).orElse(false);
    }

    private Widget getVisibleProductionRoot()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Widget root = Microbot.getClient().getWidget(InterfaceID.SKILLMULTI, 0);
            return root != null && !root.isHidden() ? root : null;
        }).orElse(null);
    }

    private boolean isAllProductionQuantityEnabled()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            int selectedQuantity = Microbot.getClient().getVarcIntValue(
                VarClientID.SKILLMULTI_QUANTITY
            );
            int suggestedQuantity = Microbot.getClient().getVarcIntValue(
                VarClientID.SKILLMULTI_SUGGESTEDQUANTITY
            );

            // Some interfaces use a negative sentinel for All. Most expose All
            // as the complete suggested quantity.
            return selectedQuantity < 0
                || (suggestedQuantity > 0 && selectedQuantity >= suggestedQuantity);
        }).orElse(false);
    }

    private boolean clickAllProductionQuantity()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Widget productionRoot = Microbot.getClient().getWidget(InterfaceID.SKILLMULTI, 0);
            if (productionRoot == null || productionRoot.isHidden())
            {
                return false;
            }

            Widget allButton = Rs2Widget.searchChildren(
                "All",
                productionRoot,
                true
            );
            return allButton != null
                && !allButton.isHidden()
                && Rs2Widget.clickWidget(allButton);
        }).orElse(false);
    }

    private boolean inputsChanged(Map<String, Integer> inputBefore)
    {
        for (RecipeInput input : activeRecipe.getInputs())
        {
            int itemId = priceService.getItemId(input.getItemName());
            int before = inputBefore.getOrDefault(input.getItemName(), 0);
            if (inventoryCount(itemId) < before)
            {
                return true;
            }
        }
        return false;
    }

    private void bankProcessedOutput()
    {
        if (!ensureBankOpen())
        {
            return;
        }

        Rs2Bank.depositAll();
        sleepUntil(Rs2Inventory::isEmpty, INVENTORY_CHANGE_TIMEOUT_MILLIS);

        boolean anotherBatchAvailable = cycleProcessedUnits < cycleTargetUnits
            && calculateBankStockUnits(activeRecipe) > 0;
        if (!anotherBatchAvailable)
        {
            beginSellingOrFinish();
        }
        else
        {
            if (antiban != null)
            {
                antiban.onBatchBanked(true);
            }
            state = FactoryState.PREPARING_INVENTORY;
            status = "Preparing next batch";
        }
    }

    private void beginSellingOrFinish()
    {
        if (activeRecipe == null)
        {
            resetCycle();
            return;
        }

        if (!config.sellOutputs())
        {
            resetCycle();
            return;
        }

        if (!ensureBankOpen())
        {
            return;
        }

        // A processing recipe can have more than one tradeable output. Doughs
        // produce the requested dough + an empty Pot + an empty Jug, while
        // soft-clay recipes return the empty Jug/Bucket. Drain all of them.
        prepareSaleOutputQueue(activeRecipe, activeRecipe.getOutputItemName());
        if (!selectNextSaleOutputFromBank())
        {
            resetCycle();
            return;
        }

        state = FactoryState.SELLING_OUTPUT;
        status = "Selling " + sellingItemName;
    }

    private void prepareSaleOutputQueue(FactoryRecipe recipe, String preferredFirst)
    {
        saleOutputQueue.clear();
        saleOutputIndex = 0;
        sellingItemName = null;
        outputRemainingToSell = 0;

        if (recipe == null)
        {
            return;
        }

        if (preferredFirst != null && !preferredFirst.isBlank())
        {
            saleOutputQueue.add(preferredFirst);
        }
        for (String outputName : recipe.getSaleOutputItemNames())
        {
            if (outputName == null || outputName.isBlank())
            {
                continue;
            }
            boolean alreadyQueued = saleOutputQueue.stream()
                .anyMatch(existing -> existing.equalsIgnoreCase(outputName));
            if (!alreadyQueued)
            {
                saleOutputQueue.add(outputName);
            }
        }
    }

    private boolean selectNextSaleOutputFromBank()
    {
        while (saleOutputIndex < saleOutputQueue.size())
        {
            String outputName = saleOutputQueue.get(saleOutputIndex++);
            int outputId = priceService.getItemId(outputName);
            if (outputId <= 0)
            {
                continue;
            }
            if (isSellMarketBlocked(outputId))
            {
                log.info("Skipping temporarily market-blocked sale output {} while draining the output queue", outputName);
                continue;
            }

            int quantity = bankCount(outputId) + inventoryCountByName(outputName);
            if (quantity <= 0)
            {
                continue;
            }

            sellingItemName = outputName;
            outputRemainingToSell = quantity;
            return true;
        }

        sellingItemName = null;
        outputRemainingToSell = 0;
        return false;
    }

    private void finishCurrentSaleTarget()
    {
        String completedOutput = sellingItemName;
        FactoryGrandExchangeInvoker.closeExchangeWithoutMouse();
        sellingItemName = null;
        outputRemainingToSell = 0;
        state = FactoryState.SELLING_OUTPUT;
        status = "Finished selling " + (completedOutput == null ? "output" : completedOutput)
            + "; preparing next recipe output";
    }

    private void sellOutput()
    {
        if (activeRecipe == null)
        {
            resetCycle();
            return;
        }

        // The previous output in the recipe's sale queue has completed. Open the
        // bank and advance to the next primary/secondary output before ending the
        // cycle. This is what ensures Pot/Jug/Bucket stacks are actually sold.
        if (sellingItemName == null || outputRemainingToSell <= 0)
        {
            if (Rs2GrandExchange.isOpen() || Rs2GrandExchange.isOfferScreenOpen())
            {
                FactoryGrandExchangeInvoker.closeExchangeWithoutMouse();
            }
            if (!ensureBankOpen())
            {
                return;
            }
            if (!selectNextSaleOutputFromBank())
            {
                resetCycle();
                return;
            }
            status = "Selling " + sellingItemName;
        }

        int outputId = priceService.getItemId(sellingItemName);
        String outputName = sellingItemName;

        // If this factory already has a live sale for the current output, monitor
        // and reprice that exact slot before doing a new bank withdrawal.
        GrandExchangeSlots liveSale = findMatchingOfferSlot(outputId, true);
        if (liveSale != null)
        {
            if (Rs2Bank.isOpen())
            {
                Rs2Bank.closeBank();
                sleepUntil(() -> !Rs2Bank.isOpen(), BANK_OPEN_TIMEOUT_MILLIS);
            }
            if (!ensureGrandExchangeOpen())
            {
                return;
            }

            int sold = executeSellOffer(outputName, outputRemainingToSell);
            if (waitingForGeSlot || waitingForExistingGeOffer)
            {
                state = FactoryState.SELLING_OUTPUT;
                return;
            }

            outputRemainingToSell = Math.max(0, outputRemainingToSell - sold);
            if (sellRetryPolicyExhaustedThisCall)
            {
                handleExhaustedSellRetryPolicy(outputId, outputName, sold);
                return;
            }
            if (sold <= 0)
            {
                waitForMarket("Output did not sell within the retry policy");
                return;
            }
            if (outputRemainingToSell <= 0)
            {
                finishCurrentSaleTarget();
            }
            else
            {
                state = FactoryState.SELLING_OUTPUT;
                status = "Selling remaining " + outputName + ": " + outputRemainingToSell;
            }
            return;
        }

        int inventoryQuantity = inventoryCountByName(outputName);

        // Sale prep only: bank the inventory, switch to noted mode, then Withdraw-all
        // of the selected primary/byproduct output stack.
        if (inventoryQuantity <= 0)
        {
            if (!ensureBankOpen())
            {
                return;
            }

            int inBank = bankCount(outputId);
            if (inBank <= 0)
            {
                // The target disappeared between queue selection and sale prep;
                // skip it and continue with the next output instead of ending cycle.
                finishCurrentSaleTarget();
                return;
            }

            Rs2Bank.depositAll();
            sleepUntil(Rs2Inventory::isEmpty, INVENTORY_CHANGE_TIMEOUT_MILLIS);

            if (!Rs2Bank.setWithdrawAsNote())
            {
                finishOrReevaluate("Unable to enable noted withdrawal for sale prep");
                return;
            }

            if (!Rs2Bank.withdrawAll(outputId))
            {
                finishOrReevaluate("Unable to Withdraw-all output for sale");
                return;
            }

            sleepUntil(() -> inventoryCountByName(outputName) > 0, INVENTORY_CHANGE_TIMEOUT_MILLIS);
            inventoryQuantity = inventoryCountByName(outputName);
            if (inventoryQuantity <= 0)
            {
                finishOrReevaluate("Unable to withdraw noted output for sale");
                return;
            }
        }

        if (Rs2Bank.isOpen())
        {
            Rs2Bank.closeBank();
            sleepUntil(() -> !Rs2Bank.isOpen(), BANK_OPEN_TIMEOUT_MILLIS);
        }
        if (!ensureGrandExchangeOpen())
        {
            return;
        }

        int sellQuantity = Math.min(inventoryQuantity, outputRemainingToSell);
        int sold = executeSellOffer(outputName, sellQuantity);
        if (waitingForGeSlot || waitingForExistingGeOffer)
        {
            state = FactoryState.SELLING_OUTPUT;
            return;
        }

        outputRemainingToSell = Math.max(0, outputRemainingToSell - sold);
        if (sellRetryPolicyExhaustedThisCall)
        {
            handleExhaustedSellRetryPolicy(outputId, outputName, sold);
            return;
        }
        if (sold <= 0)
        {
            waitForMarket("Output did not sell within the retry policy");
            return;
        }

        if (outputRemainingToSell <= 0)
        {
            finishCurrentSaleTarget();
        }
        else
        {
            state = FactoryState.SELLING_OUTPUT;
            status = "Selling remaining " + outputName + ": " + outputRemainingToSell;
        }
    }

    private int executeSellOffer(String itemName, int requestedQuantity)
    {
        clearGeWaitFlags();
        sellRetryPolicyExhaustedThisCall = false;

        if (isGePlacementBackoffActive())
        {
            waitingForExistingGeOffer = true;
            status = "Recovering GE interface; retrying sale in " + gePlacementBackoffSeconds() + "s";
            return 0;
        }

        int itemId = priceService.getItemId(itemName);
        if (itemId <= 0 || requestedQuantity <= 0)
        {
            return 0;
        }

        GrandExchangeSlots existingOffer = findMatchingOfferSlot(itemId, true);
        GrandExchangeSlots trackedFactoryOffer = findTrackedFactorySellOfferSlot(itemId);

        // A Modify offer can briefly transition through a live state that does not
        // satisfy offerMatches(..., selling=true). Previously that made this method
        // fall through to placeSellOffer(), which invokes the inventory item's
        // "Offer" action. Once the factory owns a SELL slot for this item, a missing
        // live match means WAIT/REDISCOVER -- never create another inventory SELL.
        if (existingOffer == null && trackedFactoryOffer != null)
        {
            // If a failed Modify left the setup editor open, waiting on the tracked
            // slot can never make progress because the overview/offer array is not
            // being allowed to settle. Recover the editor first; only then wait for
            // the tracked slot to reappear. This recovery never invokes an inventory
            // Offer action.
            if (FactoryGrandExchangeInvoker.hasOpenOfferEditor())
            {
                status = "Recovering stale GE Modify editor: " + itemName;
                if (FactoryGrandExchangeInvoker.recoverStaleOfferEditor())
                {
                    log.info("Recovered stale GE Modify editor for {} in factory slot {}",
                        itemName, trackedFactoryOffer);
                    sleep(250, 450);
                }
                else
                {
                    waitingForExistingGeOffer = true;
                    status = "Waiting for GE Modify recovery: " + itemName;
                    return 0;
                }
            }

            waitingForExistingGeOffer = true;
            status = "Tracked sell offer transitioning: " + itemName + " in " + trackedFactoryOffer;
            log.info("Suppressing initial SELL for {} because factory slot {} is still bound to itemId {}",
                itemName, trackedFactoryOffer, itemId);
            return 0;
        }

        if (existingOffer != null)
        {
            GrandExchangeOffer offer = getOffer(existingOffer);
            int filled = offer == null ? 0 : Math.max(0, offer.getQuantitySold());
            int total = offer == null ? requestedQuantity : Math.max(1, offer.getTotalQuantity());
            boolean factoryOwned = factorySellOfferSlots.contains(existingOffer);
            if (factoryOwned)
            {
                factorySellOfferItemIds.put(existingOffer, itemId);
            }

            if (!factoryOwned)
            {
                // Never alter a pre-existing player sale. If it has finished, use the
                // GE Collect button so its slot is clean before the factory proceeds.
                if (offer != null && isFinishedSellState(offer.getState()))
                {
                    collectCompletedOfferWithButton(existingOffer, "existing " + itemName + " sale");
                    status = "Collected existing " + itemName + " sale; preparing current batch";
                }
                else
                {
                    status = "Waiting for existing " + itemName + " sale (" + filled + "/" + total + ")";
                }
                waitingForExistingGeOffer = true;
                return 0;
            }

            OfferWaitResult result = waitForOfferResult(
                existingOffer,
                itemId,
                true,
                Math.min(requestedQuantity, total),
                TimeUnit.SECONDS.toMillis(config.offerTimeoutSeconds()),
                itemName
            );

            int soldThisOffer = Math.max(0, result.filledQuantity);
            int offerPrice = factorySellOfferPrices.getOrDefault(existingOffer,
                offer == null ? priceService.getSellOfferPrice(itemId, config.sellDiscountPercent(), 0) : offer.getPrice());

            if (result.completed || result.cancelled)
            {
                if (!collectCompletedOfferWithButton(existingOffer, itemName + " sale"))
                {
                    waitingForExistingGeOffer = true;
                    return 0;
                }
            }
            else if (result.missing)
            {
                waitingForExistingGeOffer = true;
                status = "Waiting to rediscover sell offer: " + itemName;
                return 0;
            }
            else if (config.abortStalledOffers())
            {
                int currentRetry = Math.max(0, sellPriceRetryByItem.getOrDefault(itemId, 0));
                if (currentRetry < config.maxPriceRetries())
                {
                    int nextRetry = currentRetry + 1;
                    int newPrice = calculateSellReprice(itemId, existingOffer, nextRetry);
                    status = "Modifying sell offer: " + itemName + " @ " + newPrice;
                    if (!FactoryGrandExchangeInvoker.modifyOfferPrice(existingOffer, itemId, newPrice))
                    {
                        waitingForExistingGeOffer = true;
                        status = "Modify offer failed for " + itemName + ": "
                            + FactoryGrandExchangeInvoker.getLastFailureReason();
                        return 0;
                    }
                    sellPriceRetryByItem.put(itemId, nextRetry);
                    factorySellOfferPrices.put(existingOffer, newPrice);
                    waitingForExistingGeOffer = true;
                    status = "Modified sell offer: " + itemName + " @ " + newPrice;
                    return 0;
                }

                // Reprice policy exhausted. Cancel the exact slot without collecting,
                // then explicitly press the GE Collect button once the CANCELLED_SELL
                // state is visible. Price changes themselves always use Modify offer.
                if (!FactoryGrandExchangeInvoker.cancelOfferWithoutCollect(existingOffer, itemId))
                {
                    waitingForExistingGeOffer = true;
                    status = "Unable to cancel stalled sell offer: "
                        + FactoryGrandExchangeInvoker.getLastFailureReason();
                    return 0;
                }
                boolean cancelled = sleepUntil(() ->
                {
                    GrandExchangeOffer current = getOffer(existingOffer);
                    return current != null && isFinishedSellState(current.getState());
                }, 4_000);
                if (!cancelled)
                {
                    waitingForExistingGeOffer = true;
                    status = "Waiting for cancelled sell offer state: " + itemName;
                    return 0;
                }
                GrandExchangeOffer cancelledOffer = getOffer(existingOffer);
                if (cancelledOffer != null)
                {
                    soldThisOffer = Math.max(soldThisOffer, cancelledOffer.getQuantitySold());
                }
                if (!collectCompletedOfferWithButton(existingOffer, itemName + " cancelled sale"))
                {
                    waitingForExistingGeOffer = true;
                    return 0;
                }
                sellRetryPolicyExhaustedThisCall = true;
                markSellMarketBlocked(itemId);
            }
            else
            {
                waitingForExistingGeOffer = true;
                status = "Sell offer active: " + itemName + " (" + soldThisOffer + "/" + total + ")";
                return 0;
            }

            factorySellOfferSlots.remove(existingOffer);
            factorySellOfferPrices.remove(existingOffer);
            factorySellOfferItemIds.remove(existingOffer);
            if (result.completed)
            {
                sellPriceRetryByItem.remove(itemId);
            }

            if (soldThisOffer > 0)
            {
                stats.recordSold(soldThisOffer, Math.max(1, offerPrice), config.geTaxPercent());
            }
            return Math.min(requestedQuantity, soldThisOffer);
        }

        // Belt-and-suspenders guard: if a factory slot is still bound to this
        // output, the inventory-based initial SELL path is forbidden even if the
        // client's live GE array is in a transient state.
        trackedFactoryOffer = findTrackedFactorySellOfferSlot(itemId);
        if (trackedFactoryOffer != null)
        {
            waitingForExistingGeOffer = true;
            status = "Waiting for tracked sell offer: " + itemName + " in " + trackedFactoryOffer;
            return 0;
        }

        if (!ensureFreeGeSlot())
        {
            return 0;
        }

        GrandExchangeSlots reservedSlot = getFirstAvailableGeSlot();
        if (reservedSlot == null)
        {
            waitingForGeSlot = true;
            status = "Waiting for a free GE slot";
            return 0;
        }

        int retry = Math.max(0, sellPriceRetryByItem.getOrDefault(itemId, 0));
        int price = priceService.getSellOfferPrice(itemId, config.sellDiscountPercent(), retry);
        if (price <= 0)
        {
            return 0;
        }

        if (!FactoryGrandExchangeInvoker.placeSellOffer(
            reservedSlot, itemName, itemId, requestedQuantity, price
        ))
        {
            beginGePlacementBackoff();
            waitingForExistingGeOffer = true;
            String reason = FactoryGrandExchangeInvoker.getLastFailureReason();
            status = "GE sell failed: " + reason;
            logGePlacementFailure("sell", itemName, requestedQuantity, price, reason);
            return 0;
        }

        clearGePlacementBackoff();

        GrandExchangeSlots slot = waitForOfferSlot(itemId, true, reservedSlot);
        if (slot == null)
        {
            waitingForExistingGeOffer = true;
            status = "Sale placed; waiting for GE slot sync: " + itemName;
            log.warn("Could not identify newly placed sell offer for {} by item ID {}", itemName, itemId);
            return 0;
        }

        factorySellOfferSlots.add(slot);
        factorySellOfferPrices.put(slot, price);
        factorySellOfferItemIds.put(slot, itemId);
        sellPriceRetryByItem.putIfAbsent(itemId, retry);

        OfferWaitResult result = waitForOfferResult(
            slot,
            itemId,
            true,
            requestedQuantity,
            TimeUnit.SECONDS.toMillis(config.offerTimeoutSeconds()),
            itemName
        );

        int soldThisOffer = Math.max(0, result.filledQuantity);
        if (result.completed || result.cancelled)
        {
            if (!collectCompletedOfferWithButton(slot, itemName + " sale"))
            {
                waitingForExistingGeOffer = true;
                return 0;
            }
        }
        else if (result.missing)
        {
            waitingForExistingGeOffer = true;
            status = "Waiting to rediscover sell offer: " + itemName;
            return 0;
        }
        else if (config.abortStalledOffers())
        {
            int currentRetry = Math.max(retry, sellPriceRetryByItem.getOrDefault(itemId, retry));
            if (currentRetry < config.maxPriceRetries())
            {
                int nextRetry = currentRetry + 1;
                int newPrice = calculateSellReprice(itemId, slot, nextRetry);
                status = "Modifying sell offer: " + itemName + " @ " + newPrice;
                if (!FactoryGrandExchangeInvoker.modifyOfferPrice(slot, itemId, newPrice))
                {
                    waitingForExistingGeOffer = true;
                    status = "Modify offer failed for " + itemName + ": "
                        + FactoryGrandExchangeInvoker.getLastFailureReason();
                    return 0;
                }
                sellPriceRetryByItem.put(itemId, nextRetry);
                factorySellOfferPrices.put(slot, newPrice);
                waitingForExistingGeOffer = true;
                status = "Modified sell offer: " + itemName + " @ " + newPrice;
                return 0;
            }

            if (!FactoryGrandExchangeInvoker.cancelOfferWithoutCollect(slot, itemId))
            {
                waitingForExistingGeOffer = true;
                status = "Unable to cancel stalled sell offer: "
                    + FactoryGrandExchangeInvoker.getLastFailureReason();
                return 0;
            }
            boolean cancelled = sleepUntil(() ->
            {
                GrandExchangeOffer current = getOffer(slot);
                return current != null && isFinishedSellState(current.getState());
            }, 4_000);
            if (!cancelled)
            {
                waitingForExistingGeOffer = true;
                status = "Waiting for cancelled sell offer state: " + itemName;
                return 0;
            }
            GrandExchangeOffer cancelledOffer = getOffer(slot);
            if (cancelledOffer != null)
            {
                soldThisOffer = Math.max(soldThisOffer, cancelledOffer.getQuantitySold());
            }
            if (!collectCompletedOfferWithButton(slot, itemName + " cancelled sale"))
            {
                waitingForExistingGeOffer = true;
                return 0;
            }
            sellRetryPolicyExhaustedThisCall = true;
            markSellMarketBlocked(itemId);
        }
        else
        {
            waitingForExistingGeOffer = true;
            status = "Sell offer active: " + itemName + " (" + soldThisOffer + "/" + requestedQuantity + ")";
            return 0;
        }

        factorySellOfferSlots.remove(slot);
        factorySellOfferPrices.remove(slot);
        factorySellOfferItemIds.remove(slot);
        if (result.completed)
        {
            sellPriceRetryByItem.remove(itemId);
        }

        if (soldThisOffer > 0)
        {
            stats.recordSold(soldThisOffer, price, config.geTaxPercent());
        }
        return Math.min(requestedQuantity, soldThisOffer);
    }

    private CyclePlan createCyclePlan(FactoryRecipe recipe, ProfitQuote quote)
    {
        int configuredTarget = Math.max(1, config.targetUnitsPerCycle());
        // Units per cycle is a minimum planning target, not a GE purchase cap.
        // When automatic buying is enabled, bulk procurement scales above that
        // minimum to the largest amount permitted by current bank stock, GE buy
        // limits and spendable coins. Processing still happens in recipe-sized
        // inventory batches until cycleTargetUnits is complete.
        int requestedTarget = configuredTarget;
        Map<String, Integer> planningCounts = new HashMap<>();

        for (RecipeInput input : recipe.getInputs())
        {
            int itemId = priceService.getItemId(input.getItemName());
            if (itemId <= 0)
            {
                return CyclePlan.unavailable(recipe, quote);
            }

            int actualCount = bankCount(itemId) + inventoryCount(itemId);
            // Existing bank/inventory stock is always credited before new purchases.
            // This prevents the bulk procurement planner from buying a fresh full
            // amount while usable partial stock is already present.
            planningCounts.put(input.getItemName(), actualCount);
        }

        int stockUnits = calculateConsumableUnits(recipe, planningCounts);
        int maximumUnitsByStockAndLimits = Integer.MAX_VALUE;
        long shortestReset = Long.MAX_VALUE;
        Set<Integer> limitBlockedItems = new HashSet<>();

        for (RecipeInput input : recipe.getInputs())
        {
            int itemId = priceService.getItemId(input.getItemName());
            int existing = planningCounts.getOrDefault(input.getItemName(), 0);
            int buyCapacity = 0;

            if (config.buyInputs())
            {
                int officialLimit = priceService.getTradeLimit(itemId, config.unknownItemLimit());
                buyCapacity = buyLimitTracker.getRemainingCapacity(
                    itemId,
                    officialLimit,
                    config.buyLimitUsagePercent(),
                    config.coldStartReservePercent()
                );
            }

            if (input.isConsumed())
            {
                int totalAvailable = (int) Math.min(
                    Integer.MAX_VALUE,
                    (long) existing + Math.max(0, buyCapacity)
                );
                int possibleUnits = input.getPossibleOutputUnits(totalAvailable);
                maximumUnitsByStockAndLimits = Math.min(maximumUnitsByStockAndLimits, possibleUnits);
                if (possibleUnits <= 0 && existing <= 0)
                {
                    limitBlockedItems.add(itemId);
                }
            }
            else if (existing <= 0 && buyCapacity <= 0)
            {
                maximumUnitsByStockAndLimits = 0;
                limitBlockedItems.add(itemId);
            }

            if (limitBlockedItems.contains(itemId))
            {
                long reset = buyLimitTracker.getMillisUntilNextReset(itemId);
                if (reset > 0)
                {
                    shortestReset = Math.min(shortestReset, reset);
                }
            }
        }

        if (maximumUnitsByStockAndLimits == Integer.MAX_VALUE)
        {
            maximumUnitsByStockAndLimits = 0;
        }

        // Without GE buying, preserve the configured cycle size. With buying
        // enabled, use the full stock + buy-limit ceiling and let affordability
        // determine how large the one-shot procurement should be.
        int upperTarget = config.buyInputs()
            ? Math.max(0, maximumUnitsByStockAndLimits)
            : Math.max(0, Math.min(requestedTarget, maximumUnitsByStockAndLimits));
        int spendableCoins = getSpendableCoins();
        int affordableTarget = findMaximumAffordableTarget(
            recipe,
            planningCounts,
            upperTarget,
            spendableCoins
        );
        int targetUnits = Math.max(0, affordableTarget);

        Map<String, Integer> purchases = buildPurchaseQuantities(recipe, planningCounts, targetUnits);
        return new CyclePlan(
            recipe,
            quote,
            targetUnits,
            Math.min(stockUnits, targetUnits),
            purchases,
            limitBlockedItems,
            shortestReset == Long.MAX_VALUE ? 0L : shortestReset,
            requestedTarget
        );
    }

    private int calculateConsumableUnits(FactoryRecipe recipe, Map<String, Integer> counts)
    {
        int units = Integer.MAX_VALUE;
        for (RecipeInput input : recipe.getInputs())
        {
            if (!input.isConsumed())
            {
                continue;
            }
            units = Math.min(
                units,
                input.getPossibleOutputUnits(counts.getOrDefault(input.getItemName(), 0))
            );
        }
        return units == Integer.MAX_VALUE ? 0 : Math.max(0, units);
    }

    private int findMaximumAffordableTarget(
        FactoryRecipe recipe,
        Map<String, Integer> existingCounts,
        int upperTarget,
        int spendableCoins)
    {
        int low = 0;
        int high = Math.max(0, upperTarget);
        while (low < high)
        {
            int middle = low + ((high - low + 1) / 2);
            long cost = applyPurchaseSafetyBuffer(
                calculatePurchaseCost(recipe, existingCounts, middle)
            );
            if (cost <= spendableCoins)
            {
                low = middle;
            }
            else
            {
                high = middle - 1;
            }
        }
        return low;
    }

    private long calculatePurchaseCost(
        FactoryRecipe recipe,
        Map<String, Integer> existingCounts,
        int targetUnits)
    {
        long cost = 0L;
        for (RecipeInput input : recipe.getInputs())
        {
            int required = input.isConsumed() ? input.requiredForUnits(targetUnits) : (targetUnits > 0 ? 1 : 0);
            int missing = Math.max(0, required - existingCounts.getOrDefault(input.getItemName(), 0));
            if (missing <= 0)
            {
                continue;
            }

            int itemId = priceService.getItemId(input.getItemName());
            int price = priceService.getBuyOfferPrice(itemId, config.buyMarkupPercent(), 0);
            if (price <= 0)
            {
                return Long.MAX_VALUE;
            }
            cost += (long) missing * price;
            if (cost < 0 || cost > Integer.MAX_VALUE)
            {
                return Math.min(Long.MAX_VALUE, cost);
            }
        }
        return cost;
    }

    private Map<String, Integer> buildPurchaseQuantities(
        FactoryRecipe recipe,
        Map<String, Integer> existingCounts,
        int targetUnits)
    {
        if (!config.buyInputs() || targetUnits <= 0)
        {
            return Collections.emptyMap();
        }

        Map<String, Integer> purchases = new LinkedHashMap<>();
        for (RecipeInput input : recipe.getInputs())
        {
            int required = input.isConsumed() ? input.requiredForUnits(targetUnits) : 1;
            int missing = Math.max(0, required - existingCounts.getOrDefault(input.getItemName(), 0));
            if (missing > 0)
            {
                purchases.put(input.getItemName(), missing);
            }
        }
        return purchases;
    }

    private List<FactoryRecipe> buildCandidateList()
    {
        // Every supported output is part of the automatic pool by default.
        // Skill requirements remain the hard eligibility gate; profitability,
        // affordability, liquidity/buy limits, and configured margin thresholds
        // decide whether the recipe is actually selected.
        if (config.mode() == FactoryMode.FIXED_RECIPE)
        {
            FactoryRecipe fixed = config.fixedRecipe();
            if (isRecipeEligibleForAccount(fixed))
            {
                return Collections.singletonList(fixed);
            }
            return Collections.emptyList();
        }

        List<FactoryRecipe> recipes = new ArrayList<>();
        for (FactoryRecipe recipe : FactoryRecipe.values())
        {
            if (isRecipeEligibleForAccount(recipe))
            {
                recipes.add(recipe);
            }
        }
        return recipes;
    }

    private boolean isRecipeEligibleForAccount(FactoryRecipe recipe)
    {
        if (recipe == null)
        {
            return false;
        }
        if (recipe.isMembersOnly() && (!membersAccount || !memberWorld))
        {
            return false;
        }
        return hasRequiredSkill(recipe);
    }

    /**
     * Returns the first missing input whose usable GE buy-limit capacity is zero.
     * Existing bank/inventory stock is credited first, so hitting a limit never
     * discards materials the account already owns; it only prevents a new buy.
     */
    private String findLimitBlockedMissingInput(FactoryRecipe recipe)
    {
        if (recipe == null || !config.buyInputs())
        {
            return null;
        }

        for (RecipeInput input : recipe.getInputs())
        {
            int itemId = priceService.getItemId(input.getItemName());
            if (itemId <= 0)
            {
                continue;
            }

            int minimumRequired = input.isConsumed() ? input.requiredForUnits(1) : 1;
            int existing = bankCount(itemId) + inventoryCount(itemId);
            if (existing >= minimumRequired)
            {
                continue;
            }

            int officialLimit = priceService.getTradeLimit(itemId, config.unknownItemLimit());
            int remainingCapacity = buyLimitTracker.getRemainingCapacity(
                itemId,
                officialLimit,
                config.buyLimitUsagePercent(),
                config.coldStartReservePercent()
            );
            if (remainingCapacity <= 0)
            {
                return input.getItemName();
            }
        }
        return null;
    }

    private boolean detectMembersAccount()
    {
        try
        {
            return Rs2Player.isMember();
        }
        catch (Exception ex)
        {
            log.debug("Unable to determine account membership: {}", ex.getMessage());
            return false;
        }
    }

    private boolean detectMemberWorld()
    {
        try
        {
            return Rs2Player.isInMemberWorld();
        }
        catch (Exception ex)
        {
            log.debug("Unable to determine current world membership type: {}", ex.getMessage());
            return false;
        }
    }

    private boolean hasRequiredSkill(FactoryRecipe recipe)
    {
        try
        {
            Integer level = Microbot.getClientThread().runOnClientThreadOptional(
                () -> Microbot.getClient().getRealSkillLevel(recipe.getRequiredSkill())
            ).orElse(null);
            return level != null && level >= recipe.getRequiredLevel();
        }
        catch (Exception ex)
        {
            return false;
        }
    }

    private int calculateBankStockUnits(FactoryRecipe recipe)
    {
        if (recipe == null)
        {
            return 0;
        }

        int units = Integer.MAX_VALUE;
        for (RecipeInput input : recipe.getInputs())
        {
            int itemId = priceService.getItemId(input.getItemName());
            if (itemId <= 0)
            {
                return 0;
            }

            int count = bankCount(itemId) + inventoryCount(itemId);
            if (input.isConsumed())
            {
                units = Math.min(units, input.getPossibleOutputUnits(count));
            }
            else if (count <= 0)
            {
                return 0;
            }
        }
        return units == Integer.MAX_VALUE ? 0 : Math.max(0, units);
    }

    private int getSpendableCoins()
    {
        refreshCoinSnapshot();
        return observedSpendableCoins;
    }

    private void refreshCoinSnapshot()
    {
        int coinsId = ItemID.COINS;
        long total = (long) bankCount(coinsId) + inventoryCount(coinsId);
        long spendable = Math.max(0L, total - config.cashReserve());
        observedCoinTotal = total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
        observedSpendableCoins = spendable > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) spendable;
    }

    private long applyPurchaseSafetyBuffer(long rawCost)
    {
        if (rawCost <= 0 || rawCost == Long.MAX_VALUE)
        {
            return rawCost;
        }
        double buffered = Math.ceil(rawCost * PURCHASE_SAFETY_MULTIPLIER);
        return buffered >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) buffered;
    }

    private boolean ensureCoinsForPurchases(CyclePlan plan)
    {
        long required = 0;
        for (Map.Entry<String, Integer> entry : plan.purchaseQuantities.entrySet())
        {
            int itemId = priceService.getItemId(entry.getKey());
            if (itemId <= 0)
            {
                continue;
            }

            // Coins committed to a live matching offer are already funded. Waiting on
            // that offer must not trigger an "insufficient coins" cycle reset.
            if (findMatchingOfferSlot(itemId, false) != null)
            {
                continue;
            }

            int pendingCredit = Math.max(0, pendingCollectedBuyCredits.getOrDefault(itemId, 0));
            int stillNeeded = Math.max(0, entry.getValue() - pendingCredit);
            int price = priceService.getBuyOfferPrice(itemId, config.buyMarkupPercent(), 0);
            required += (long) stillNeeded * Math.max(0, price);
        }
        required = applyPurchaseSafetyBuffer(required);

        if (required <= 0)
        {
            return true;
        }

        int coinsId = ItemID.COINS;
        int inventoryCoins = inventoryCount(coinsId);
        if (inventoryCoins >= required)
        {
            refreshCoinSnapshot();
            return true;
        }

        if (!ensureBankOpen())
        {
            return false;
        }

        if (bankCount(coinsId) <= 0)
        {
            status = "Syncing bank balance";
            sleepUntil(() -> bankCount(coinsId) > 0, 2_500);
        }

        int bankCoins = bankCount(coinsId);
        long totalCoins = (long) bankCoins + inventoryCoins;
        long spendable = Math.max(0L, totalCoins - config.cashReserve());
        if (spendable < required)
        {
            refreshCoinSnapshot();
            status = String.format(
                "Need %,d coins; %,d spendable (%,d total, %,d reserve)",
                required,
                observedSpendableCoins,
                observedCoinTotal,
                config.cashReserve()
            );
            return false;
        }

        int withdraw = (int) Math.min(Integer.MAX_VALUE, required);
        Rs2Bank.depositAll();
        if (!withdrawXWithVerifiedBankPrompt(coinsId, "Coins", withdraw))
        {
            status = "Verified bank withdrawal failed for coins x" + withdraw;
            return false;
        }
        return sleepUntil(
            () -> inventoryCount(coinsId) >= withdraw,
            INVENTORY_CHANGE_TIMEOUT_MILLIS
        );
    }

    private boolean ensureBankOpen()
    {
        if (isBankRootVisible())
        {
            return true;
        }

        FactoryGrandExchangeInvoker.beginBankTransitionGuard();
        try
        {
            if (Rs2GrandExchange.isOpen())
            {
                FactoryGrandExchangeInvoker.closeExchangeWithoutMouse();
                sleepUntil(() -> !Rs2GrandExchange.isOpen(), 3_000);
            }

            // Never begin a bank interaction while a stale numeric/chatbox layer is
            // still active. A bank click does not grant permission to press Enter.
            if (isAnyChatboxNumericInputVisible())
            {
                status = "Waiting for stale input to close before bank";
                log.warn("Factory deferred bank open because MES_TEXT2/input mode is still active");
                return false;
            }

            if (Rs2Bank.openBank())
            {
                boolean opened = sleepUntil(this::isBankRootVisible, BANK_OPEN_TIMEOUT_MILLIS);
                if (opened)
                {
                    // Give the interface swap one client tick to settle. GE Enter is
                    // still locked during this interval.
                    sleep(250, 400);
                }
                return opened;
            }

            status = "Walking to bank";
            Rs2Bank.walkToBank();
            return false;
        }
        finally
        {
            FactoryGrandExchangeInvoker.endBankTransitionGuard();
        }
    }

    private boolean isAnyChatboxNumericInputVisible()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Widget input = Microbot.getClient().getWidget(InterfaceID.Chatbox.MES_TEXT2);
            return input != null
                && !input.isHidden()
                && Microbot.getClient().getVarcIntValue(VarClientID.MESLAYERMODE)
                    == BANK_CHATBOX_INPUT_MODE;
        }).orElse(false);
    }

    private boolean ensureGrandExchangeOpen()
    {
        if (Rs2GrandExchange.isOpen())
        {
            return true;
        }

        if (Rs2Bank.isOpen())
        {
            Rs2Bank.closeBank();
            sleepUntil(() -> !Rs2Bank.isOpen(), 3_000);
        }

        status = "Walking to Grand Exchange";
        if (!Rs2GrandExchange.walkToGrandExchange())
        {
            return false;
        }

        if (!Rs2GrandExchange.openExchange())
        {
            return false;
        }
        return sleepUntil(Rs2GrandExchange::isOpen, 8_000);
    }

    private boolean ensureFreeGeSlot()
    {
        if (Rs2GrandExchange.getAvailableSlotsCount() > 0)
        {
            return true;
        }

        // Always use the top GE Collect button. Completed factory BUY slots are
        // snapshotted before every Collect-all so even a partially-completed parallel
        // BUY set can be collected safely without losing per-item accounting.
        if (!factoryBuyOfferSlots.isEmpty())
        {
            collectTrackedFactoryBuyOffersIfReady();
            if (Rs2GrandExchange.getAvailableSlotsCount() > 0)
            {
                return true;
            }
        }

        // A completed unrelated/player offer is also collected through the top GE
        // Collect button. Active offers remain untouched by Collect-all.
        for (GrandExchangeSlots slot : GrandExchangeSlots.values())
        {
            if (slot.ordinal() >= 3
                || factoryBuyOfferSlots.contains(slot)
                || factorySellOfferSlots.contains(slot))
            {
                continue;
            }

            GrandExchangeOffer offer = getOffer(slot);
            if (offer == null)
            {
                continue;
            }
            GrandExchangeOfferState offerState = offer.getState();
            if (!isFinishedBuyState(offerState) && !isFinishedSellState(offerState))
            {
                continue;
            }

            if (collectCompletedOfferWithButton(slot, "completed existing GE offer"))
            {
                if (Rs2GrandExchange.getAvailableSlotsCount() > 0)
                {
                    return true;
                }
            }
        }

        waitingForGeSlot = true;
        status = !factoryBuyOfferSlots.isEmpty()
            ? "All available GE slots occupied; waiting for parallel input offers"
            : "All available GE slots occupied; waiting";
        return false;
    }

    private GrandExchangeSlots getFirstAvailableGeSlot()
    {
        GrandExchangeSlots[] slots = Rs2GrandExchange.getAvailableSlots();
        return slots == null || slots.length == 0 ? null : slots[0];
    }

    private GrandExchangeSlots waitForOfferSlot(
        int itemId,
        boolean selling,
        GrandExchangeSlots expectedSlot)
    {
        final GrandExchangeSlots[] result = new GrandExchangeSlots[1];
        sleepUntil(() ->
        {
            if (expectedSlot != null && offerMatches(expectedSlot, itemId, selling))
            {
                result[0] = expectedSlot;
                return true;
            }

            result[0] = findMatchingOfferSlot(itemId, selling);
            return result[0] != null;
        }, 12_000);
        return result[0];
    }

    private GrandExchangeSlots findMatchingOfferSlot(int itemId, boolean selling)
    {
        if (itemId <= 0)
        {
            return null;
        }

        for (GrandExchangeSlots slot : GrandExchangeSlots.values())
        {
            if (offerMatches(slot, itemId, selling))
            {
                return slot;
            }
        }
        return null;
    }

    private GrandExchangeSlots findTrackedFactorySellOfferSlot(int itemId)
    {
        if (itemId <= 0)
        {
            return null;
        }

        for (GrandExchangeSlots slot : new HashSet<>(factorySellOfferSlots))
        {
            Integer boundItemId = factorySellOfferItemIds.get(slot);
            if (boundItemId != null && boundItemId == itemId)
            {
                return slot;
            }

            // Backfill the binding for slots created before this map was populated
            // or recovered after a short client-sync delay.
            GrandExchangeOffer offer = getOffer(slot);
            if (offer != null && offer.getItemId() == itemId)
            {
                factorySellOfferItemIds.put(slot, itemId);
                return slot;
            }
        }
        return null;
    }

    private GrandExchangeSlots findTrackedFactoryBuyOfferSlot(int itemId)
    {
        GrandExchangeSlots terminalMatch = null;
        GrandExchangeSlots activeMatch = null;
        int lowestRetry = Integer.MAX_VALUE;

        for (GrandExchangeSlots slot : new HashSet<>(factoryBuyOfferSlots))
        {
            GrandExchangeOffer offer = getOffer(slot);
            if (offer == null || offer.getItemId() != itemId || !isBuyState(offer.getState()))
            {
                continue;
            }
            if (isFinishedBuyState(offer.getState()))
            {
                if (terminalMatch == null)
                {
                    terminalMatch = slot;
                }
                continue;
            }

            // When the same input was split over several free GE slots, rotate
            // timeout/reprice attention toward the least-retried active slot.
            int retry = Math.max(0, factoryBuyOfferRetries.getOrDefault(slot, 0));
            if (activeMatch == null || retry < lowestRetry)
            {
                activeMatch = slot;
                lowestRetry = retry;
            }
        }
        return activeMatch != null ? activeMatch : terminalMatch;
    }

    private boolean offerMatches(GrandExchangeSlots slot, int itemId, boolean selling)
    {
        GrandExchangeOffer offer = getOffer(slot);
        if (offer == null || offer.getItemId() != itemId)
        {
            return false;
        }

        GrandExchangeOfferState offerState = offer.getState();
        return selling ? isSellState(offerState) : isBuyState(offerState);
    }

    /**
     * A retry must actually move the offered price. Market snapshots can round two
     * consecutive adaptive prices to the same integer, especially on cheap F2P
     * items; never report that as a successful Modify offer.
     */
    private int calculateBuyReprice(int itemId, GrandExchangeSlots slot, int retryAttempt)
    {
        int calculated = priceService.getBuyOfferPrice(itemId, config.buyMarkupPercent(), retryAttempt);
        GrandExchangeOffer live = getOffer(slot);
        int current = live == null
            ? factoryBuyOfferPrices.getOrDefault(slot, -1)
            : live.getPrice();
        if (calculated <= 0)
        {
            return calculated;
        }
        if (current > 0 && calculated <= current)
        {
            return current == Integer.MAX_VALUE ? current : current + 1;
        }
        return calculated;
    }

    private int calculateSellReprice(int itemId, GrandExchangeSlots slot, int retryAttempt)
    {
        int calculated = priceService.getSellOfferPrice(itemId, config.sellDiscountPercent(), retryAttempt);
        GrandExchangeOffer live = getOffer(slot);
        int current = live == null
            ? factorySellOfferPrices.getOrDefault(slot, -1)
            : live.getPrice();
        if (calculated <= 0)
        {
            return calculated;
        }
        if (current > 1 && calculated >= current)
        {
            return current - 1;
        }
        return calculated;
    }

    private GrandExchangeOffer getOffer(GrandExchangeSlots slot)
    {
        if (slot == null || Microbot.getClient() == null)
        {
            return null;
        }

        try
        {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            int index = slot.ordinal();
            if (offers == null || index < 0 || index >= offers.length)
            {
                return null;
            }
            GrandExchangeOffer offer = offers[index];
            if (offer == null || offer.getState() == GrandExchangeOfferState.EMPTY || offer.getItemId() <= 0)
            {
                return null;
            }
            return offer;
        }
        catch (Exception ex)
        {
            return null;
        }
    }

    private int consumePendingBuyCredit(int itemId, int requestedQuantity)
    {
        if (itemId <= 0 || requestedQuantity <= 0)
        {
            return 0;
        }
        int available = Math.max(0, pendingCollectedBuyCredits.getOrDefault(itemId, 0));
        if (available <= 0)
        {
            return 0;
        }

        int consumed = Math.min(requestedQuantity, available);
        int remaining = available - consumed;
        if (remaining <= 0)
        {
            pendingCollectedBuyCredits.remove(itemId);
        }
        else
        {
            pendingCollectedBuyCredits.put(itemId, remaining);
        }
        return consumed;
    }

    /**
     * Snapshot every completed factory-owned BUY offer. The top GE Collect button may
     * clear several completed offers at once, including only a subset of parallel BUYs.
     * Capturing the fills before clicking Collect preserves exact per-item accounting.
     */
    private List<CompletedBuySnapshot> snapshotCompletedFactoryBuyOffers()
    {
        List<CompletedBuySnapshot> completed = new ArrayList<>();
        for (GrandExchangeSlots slot : new HashSet<>(factoryBuyOfferSlots))
        {
            GrandExchangeOffer offer = getOffer(slot);
            if (offer == null || !isFinishedBuyState(offer.getState()))
            {
                continue;
            }

            int itemId = offer.getItemId();
            int quantity = Math.max(0, offer.getQuantitySold());
            int price = Math.max(1, factoryBuyOfferPrices.getOrDefault(slot, offer.getPrice()));
            boolean fullyBought = offer.getState() == GrandExchangeOfferState.BOUGHT;
            completed.add(new CompletedBuySnapshot(slot, itemId, quantity, price, fullyBought));
        }
        return completed;
    }

    private void reconcileCollectedFactoryBuys(List<CompletedBuySnapshot> completed)
    {
        for (CompletedBuySnapshot snapshot : completed)
        {
            if (snapshot.quantity > 0)
            {
                pendingCollectedBuyCredits.merge(snapshot.itemId, snapshot.quantity, Integer::sum);
                buyLimitTracker.recordPurchase(snapshot.itemId, snapshot.quantity);
                stats.recordBought(snapshot.quantity, snapshot.price);
            }
            if (snapshot.fullyBought)
            {
                buyPriceRetryByItem.remove(snapshot.itemId);
            }
            factoryBuyOfferSlots.remove(snapshot.slot);
            factoryBuyOfferPrices.remove(snapshot.slot);
            factoryBuyOfferRetries.remove(snapshot.slot);
        }
    }

    /**
     * Collect completed factory BUYs through the single Collect control at the top of
     * the Grand Exchange. Unlike older builds, this does not require every parallel
     * BUY to be terminal: completed slots are snapshotted and credited while active
     * offers remain on the GE untouched.
     */
    private boolean collectTrackedFactoryBuyOffersIfReady()
    {
        if (factoryBuyOfferSlots.isEmpty())
        {
            return false;
        }

        List<CompletedBuySnapshot> completed = snapshotCompletedFactoryBuyOffers();
        if (completed.isEmpty())
        {
            return false;
        }

        return collectWithTopGeButton(null, "completed input offers", completed);
    }

    /**
     * All factory BUY/SELL collection funnels through the top GE Collect button.
     * Any completed factory BUYs that may be swept up by the same click are reconciled
     * atomically so Collect-all can never cause a duplicate purchase.
     */
    private boolean collectCompletedOfferWithButton(GrandExchangeSlots slot, String description)
    {
        return collectWithTopGeButton(slot, description, snapshotCompletedFactoryBuyOffers());
    }

    private boolean collectWithTopGeButton(
        GrandExchangeSlots expectedSlot,
        String description,
        List<CompletedBuySnapshot> completedFactoryBuys)
    {
        status = "Using top GE Collect button: " + description;
        if (!Rs2GrandExchange.collectAllToBank())
        {
            status = "Top GE Collect button failed for " + description;
            return false;
        }

        List<GrandExchangeSlots> slotsToClear = new ArrayList<>();
        if (expectedSlot != null)
        {
            slotsToClear.add(expectedSlot);
        }
        for (CompletedBuySnapshot snapshot : completedFactoryBuys)
        {
            if (!slotsToClear.contains(snapshot.slot))
            {
                slotsToClear.add(snapshot.slot);
            }
        }

        if (!slotsToClear.isEmpty())
        {
            boolean cleared = sleepUntil(() -> slotsToClear.stream()
                .allMatch(Rs2GrandExchange::isSlotAvailable), 5_000);
            if (!cleared)
            {
                status = "Waiting for top GE Collect to clear: " + description;
                return false;
            }
        }
        else
        {
            sleep(300, 600);
        }

        reconcileCollectedFactoryBuys(completedFactoryBuys);
        return true;
    }

    private OfferWaitResult waitForOfferResult(
        GrandExchangeSlots slot,
        int itemId,
        boolean selling,
        int requestedQuantity,
        long timeoutMillis,
        String itemName)
    {
        long effectiveTimeout = antiban == null
            ? Math.max(1_000L, timeoutMillis)
            : antiban.jitterOfferTimeout(timeoutMillis);
        long deadline = System.currentTimeMillis() + effectiveTimeout;
        if (antiban != null)
        {
            antiban.onGeWaitStart();
        }
        int latestFilled = 0;
        int latestTotal = Math.max(1, requestedQuantity);

        while (System.currentTimeMillis() < deadline
            && state != FactoryState.STOPPED
            && Microbot.isLoggedIn())
        {
            GrandExchangeOffer offer = getOffer(slot);
            if (offer == null || offer.getItemId() != itemId)
            {
                return OfferWaitResult.missing(latestFilled, latestTotal);
            }

            GrandExchangeOfferState offerState = offer.getState();
            if (selling ? !isSellState(offerState) : !isBuyState(offerState))
            {
                return OfferWaitResult.missing(latestFilled, latestTotal);
            }

            latestFilled = Math.max(0, offer.getQuantitySold());
            latestTotal = Math.max(1, offer.getTotalQuantity());
            status = (selling ? "Waiting for sale: " : "Waiting for purchase: ")
                + itemName + " (" + latestFilled + "/" + latestTotal + ")";

            if (selling ? isFinishedSellState(offerState) : isFinishedBuyState(offerState))
            {
                boolean cancelled = offerState == GrandExchangeOfferState.CANCELLED_BUY
                    || offerState == GrandExchangeOfferState.CANCELLED_SELL;
                return new OfferWaitResult(true, cancelled, false, latestFilled, latestTotal);
            }

            sleep(450, 750);
        }

        return new OfferWaitResult(false, false, false, latestFilled, latestTotal);
    }

    private static boolean isBuyState(GrandExchangeOfferState state)
    {
        return state == GrandExchangeOfferState.BUYING
            || state == GrandExchangeOfferState.BOUGHT
            || state == GrandExchangeOfferState.CANCELLED_BUY;
    }

    private static boolean isSellState(GrandExchangeOfferState state)
    {
        return state == GrandExchangeOfferState.SELLING
            || state == GrandExchangeOfferState.SOLD
            || state == GrandExchangeOfferState.CANCELLED_SELL;
    }

    private static boolean isFinishedBuyState(GrandExchangeOfferState state)
    {
        return state == GrandExchangeOfferState.BOUGHT
            || state == GrandExchangeOfferState.CANCELLED_BUY;
    }

    private static boolean isFinishedSellState(GrandExchangeOfferState state)
    {
        return state == GrandExchangeOfferState.SOLD
            || state == GrandExchangeOfferState.CANCELLED_SELL;
    }

    private void clearGeWaitFlags()
    {
        waitingForGeSlot = false;
        waitingForExistingGeOffer = false;
    }

    private void beginGePlacementBackoff()
    {
        nextGePlacementAttemptAt = System.currentTimeMillis()
            + GE_PLACEMENT_FAILURE_BACKOFF_MILLIS;
    }

    private void clearGePlacementBackoff()
    {
        nextGePlacementAttemptAt = 0L;
        lastGePlacementWarningAt = 0L;
    }

    private void logGePlacementFailure(
        String side,
        String itemName,
        int quantity,
        int price,
        String reason)
    {
        long now = System.currentTimeMillis();
        if (lastGePlacementWarningAt == 0L
            || now - lastGePlacementWarningAt >= GE_PLACEMENT_WARNING_INTERVAL_MILLIS)
        {
            log.warn(
                "Failed to place {} offer for {} x{} at {}: {}; pausing before retry",
                side, itemName, quantity, price, reason
            );
            lastGePlacementWarningAt = now;
        }
        else
        {
            log.debug(
                "GE {} placement still recovering for {} x{} at {}: {}",
                side, itemName, quantity, price, reason
            );
        }
    }

    private boolean isGePlacementBackoffActive()
    {
        return System.currentTimeMillis() < nextGePlacementAttemptAt;
    }

    private long gePlacementBackoffSeconds()
    {
        long remaining = Math.max(0L, nextGePlacementAttemptAt - System.currentTimeMillis());
        return Math.max(1L, (remaining + 999L) / 1_000L);
    }

    private static final class OfferWaitResult
    {
        private final boolean completed;
        private final boolean cancelled;
        private final boolean missing;
        private final int filledQuantity;
        private final int totalQuantity;

        private OfferWaitResult(
            boolean completed,
            boolean cancelled,
            boolean missing,
            int filledQuantity,
            int totalQuantity)
        {
            this.completed = completed;
            this.cancelled = cancelled;
            this.missing = missing;
            this.filledQuantity = Math.max(0, filledQuantity);
            this.totalQuantity = Math.max(0, totalQuantity);
        }

        private static OfferWaitResult missing(int filledQuantity, int totalQuantity)
        {
            return new OfferWaitResult(false, false, true, filledQuantity, totalQuantity);
        }
    }

    private void resetProgressWatchdog()
    {
        watchdogState = state;
        watchdogFingerprint = buildProgressFingerprint();
        watchdogLastProgressAt = System.currentTimeMillis();
        watchdogLastRetryAt = 0L;
        watchdogRetryCount = 0;
        watchdogLastRecovery = "Idle";
    }

    /**
     * Observes meaningful client-side progress for every active factory state. The
     * normal state machine already retries actions on every scheduled tick. This
     * watchdog only intervenes when those retries keep producing the exact same
     * observable state for too long.
     */
    private void syncProgressWatchdog(boolean allowRecovery)
    {
        FactoryState current = state;
        long now = System.currentTimeMillis();

        if (current == FactoryState.STOPPED
            || current == FactoryState.WAITING_FOR_LIMIT
            || current == FactoryState.WAITING_FOR_MARKET)
        {
            watchdogState = current;
            watchdogFingerprint = buildProgressFingerprint();
            watchdogLastProgressAt = now;
            watchdogLastRetryAt = 0L;
            watchdogRetryCount = 0;
            return;
        }

        String fingerprint = buildProgressFingerprint();
        if (current != watchdogState || !fingerprint.equals(watchdogFingerprint))
        {
            watchdogState = current;
            watchdogFingerprint = fingerprint;
            watchdogLastProgressAt = now;
            watchdogLastRetryAt = 0L;
            watchdogRetryCount = 0;
            watchdogLastRecovery = "Idle";
            return;
        }

        if (!allowRecovery)
        {
            return;
        }

        // Waiting for a real active GE offer/slot is not a stalled click. The one
        // exception is a setup/Modify editor that was left open: that screen must
        // either change or be recovered.
        boolean offerEditorOpen = FactoryGrandExchangeInvoker.hasOpenOfferEditor();
        boolean trackedOfferMissing = hasTrackedFactoryOfferMissingLive();
        if ((waitingForGeSlot || waitingForExistingGeOffer)
            && !offerEditorOpen
            && !trackedOfferMissing)
        {
            watchdogLastProgressAt = now;
            return;
        }

        long timeout = getProgressWatchdogTimeoutMillis(
            current,
            offerEditorOpen || trackedOfferMissing
        );
        if (now - watchdogLastProgressAt < timeout
            || now - watchdogLastRetryAt < WATCHDOG_MIN_RETRY_GAP_MILLIS)
        {
            return;
        }

        watchdogRetryCount++;
        watchdogLastRetryAt = now;
        watchdogLastProgressAt = now;
        watchdogLastRecovery = "Retry " + watchdogRetryCount + "/"
            + MAX_PROGRESS_WATCHDOG_RETRIES + " in " + prettifyState(current);

        log.warn(
            "KSP AIO Factory progress watchdog: no meaningful change in {} for {} ms; retry {}/{}; status={}",
            current, timeout, watchdogRetryCount, MAX_PROGRESS_WATCHDOG_RETRIES, status
        );
        status = "Retrying stalled " + prettifyState(current) + " ("
            + watchdogRetryCount + "/" + MAX_PROGRESS_WATCHDOG_RETRIES + ")";

        recoverStalledState(current, watchdogRetryCount);
        watchdogFingerprint = buildProgressFingerprint();

        if (watchdogRetryCount >= MAX_PROGRESS_WATCHDOG_RETRIES)
        {
            watchdogLastRecovery = "Escalated " + prettifyState(current);
            watchdogRetryCount = 0;
            watchdogLastProgressAt = System.currentTimeMillis();
        }
    }

    private boolean hasTrackedFactoryOfferMissingLive()
    {
        for (GrandExchangeSlots slot : factorySellOfferSlots)
        {
            Integer expectedItemId = factorySellOfferItemIds.get(slot);
            GrandExchangeOffer live = getOffer(slot);
            if (expectedItemId != null
                && (live == null || live.getItemId() != expectedItemId))
            {
                return true;
            }
        }

        for (GrandExchangeSlots slot : factoryBuyOfferSlots)
        {
            GrandExchangeOffer live = getOffer(slot);
            if (live == null)
            {
                return true;
            }
        }
        return false;
    }

    private long getProgressWatchdogTimeoutMillis(FactoryState current, boolean offerEditorOpen)
    {
        if (offerEditorOpen
            && (current == FactoryState.BUYING_INPUTS || current == FactoryState.SELLING_OUTPUT))
        {
            return WATCHDOG_STUCK_EDITOR_TIMEOUT_MILLIS;
        }

        switch (current)
        {
            case STARTING:
                return 8_000L;
            case OPENING_BANK:
                return Math.max(10_000L, BANK_OPEN_TIMEOUT_MILLIS + 4_000L);
            case EVALUATING_RECIPES:
            case PREPARING_CYCLE:
                return 12_000L;
            case PREPARING_INVENTORY:
                return Math.max(12_000L, INVENTORY_CHANGE_TIMEOUT_MILLIS + 7_000L);
            case PROCESSING:
                return Math.max(60_000L, PROCESS_FINISH_TIMEOUT_MILLIS + 10_000L);
            case BANKING_OUTPUT:
                return Math.max(12_000L, INVENTORY_CHANGE_TIMEOUT_MILLIS + 7_000L);
            case BUYING_INPUTS:
            case SELLING_OUTPUT:
                long offerTimeout = config == null
                    ? 90_000L
                    : TimeUnit.SECONDS.toMillis(Math.max(1, config.offerTimeoutSeconds()));
                return Math.max(20_000L, offerTimeout + 15_000L);
            default:
                return 15_000L;
        }
    }

    private void recoverStalledState(FactoryState stalledState, int retry)
    {
        // The GE setup editor is the highest-priority recovery because leaving it
        // open prevents the live offer overview/slot array from stabilising.
        if (FactoryGrandExchangeInvoker.hasOpenOfferEditor())
        {
            if (FactoryGrandExchangeInvoker.recoverStaleOfferEditor())
            {
                clearGeWaitFlags();
                watchdogLastRecovery = "Recovered stale GE editor";
                status = "Recovered stale GE editor; retrying " + prettifyState(stalledState);
                return;
            }
        }

        switch (stalledState)
        {
            case STARTING:
                initialized = false;
                state = FactoryState.STARTING;
                break;

            case OPENING_BANK:
                if (Rs2Bank.isOpen())
                {
                    Rs2Bank.closeBank();
                }
                if (Rs2GrandExchange.isOpen())
                {
                    Rs2GrandExchange.closeExchange();
                }
                state = FactoryState.OPENING_BANK;
                break;

            case EVALUATING_RECIPES:
            case PREPARING_CYCLE:
            case PREPARING_INVENTORY:
                // Re-enter through a fresh bank snapshot. activePlan is preserved,
                // so already-funded/bought work is not discarded or duplicated.
                if (Rs2Bank.isOpen())
                {
                    Rs2Bank.closeBank();
                }
                state = FactoryState.OPENING_BANK;
                break;

            case BUYING_INPUTS:
                clearGeWaitFlags();
                clearGePlacementBackoff();
                if (retry >= MAX_PROGRESS_WATCHDOG_RETRIES && Rs2GrandExchange.isOpen())
                {
                    Rs2GrandExchange.closeExchange();
                }
                state = FactoryState.BUYING_INPUTS;
                break;

            case PROCESSING:
                if (retry >= MAX_PROGRESS_WATCHDOG_RETRIES)
                {
                    // Preserve any partial work and re-bank it rather than clicking
                    // the same unresponsive processing action forever.
                    processFailures = 0;
                    state = FactoryState.BANKING_OUTPUT;
                    status = "Processing stalled repeatedly; banking partial progress";
                }
                else
                {
                    // Keeping PROCESSING causes the normal handler to retry the
                    // recipe interaction on the next loop.
                    state = FactoryState.PROCESSING;
                }
                break;

            case BANKING_OUTPUT:
                if (retry >= MAX_PROGRESS_WATCHDOG_RETRIES && Rs2Bank.isOpen())
                {
                    Rs2Bank.closeBank();
                }
                state = retry >= MAX_PROGRESS_WATCHDOG_RETRIES
                    ? FactoryState.OPENING_BANK
                    : FactoryState.BANKING_OUTPUT;
                break;

            case SELLING_OUTPUT:
                clearGeWaitFlags();
                clearGePlacementBackoff();
                if (retry >= MAX_PROGRESS_WATCHDOG_RETRIES && Rs2GrandExchange.isOpen())
                {
                    Rs2GrandExchange.closeExchange();
                }
                state = FactoryState.SELLING_OUTPUT;
                break;

            default:
                break;
        }
    }

    private String buildProgressFingerprint()
    {
        StringBuilder fingerprint = new StringBuilder(256);
        fingerprint.append(state).append('|')
            .append(cycleTargetUnits).append('|')
            .append(cycleProcessedUnits).append('|')
            .append(outputRemainingToSell).append('|')
            .append(saleOutputIndex).append('|')
            .append(Rs2Bank.isOpen()).append('|')
            .append(Rs2GrandExchange.isOpen()).append('|')
            .append(FactoryGrandExchangeInvoker.hasOpenOfferEditor()).append('|')
            .append(Rs2Inventory.fullSlotCount()).append('|');

        if (activeRecipe != null && priceService != null)
        {
            fingerprint.append(activeRecipe.name()).append('|');
            for (RecipeInput input : activeRecipe.getInputs())
            {
                int id = priceService.getItemId(input.getItemName());
                fingerprint.append('I').append(id).append('=')
                    .append(inventoryCount(id)).append(';');
                if (Rs2Bank.isOpen())
                {
                    fingerprint.append('B').append(id).append('=')
                        .append(bankCount(id)).append(';');
                }
            }
            for (String output : activeRecipe.getSaleOutputItemNames())
            {
                int id = priceService.getItemId(output);
                fingerprint.append('O').append(id).append('=')
                    .append(inventoryCount(id)).append(';');
                if (Rs2Bank.isOpen())
                {
                    fingerprint.append('S').append(id).append('=')
                        .append(bankCount(id)).append(';');
                }
            }
        }

        for (GrandExchangeSlots slot : GrandExchangeSlots.values())
        {
            GrandExchangeOffer offer = getOffer(slot);
            if (offer == null)
            {
                fingerprint.append('G').append(slot.ordinal()).append("=E;");
                continue;
            }
            fingerprint.append('G').append(slot.ordinal()).append('=')
                .append(offer.getItemId()).append(',')
                .append(offer.getState()).append(',')
                .append(offer.getPrice()).append(',')
                .append(offer.getQuantitySold()).append(',')
                .append(offer.getTotalQuantity()).append(';');
        }

        return fingerprint.toString();
    }

    private static String prettifyState(FactoryState value)
    {
        if (value == null)
        {
            return "state";
        }
        return value.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private int deriveAvailableUnitsFromInputs(Map<String, Integer> before)
    {
        int units = Integer.MAX_VALUE;
        for (RecipeInput input : activeRecipe.getInputs())
        {
            if (!input.isConsumed())
            {
                continue;
            }
            units = Math.min(
                units,
                input.getPossibleOutputUnits(before.getOrDefault(input.getItemName(), 0))
            );
        }
        return units == Integer.MAX_VALUE ? 0 : Math.max(0, units);
    }

    private boolean isBatchComplete(
        Map<String, Integer> before,
        int outputBefore,
        int outputId,
        int expectedUnits)
    {
        if (expectedUnits > 0 && inventoryCount(outputId) - outputBefore >= expectedUnits)
        {
            return true;
        }

        boolean hasProgressInput = false;
        for (RecipeInput input : activeRecipe.getInputs())
        {
            if (!input.isConsumed() || !input.isProgressTracked())
            {
                continue;
            }
            hasProgressInput = true;
            int itemId = priceService.getItemId(input.getItemName());
            int initial = before.getOrDefault(input.getItemName(), 0);
            int requiredConsumed = input.requiredForUnits(expectedUnits);
            if (initial - inventoryCount(itemId) < requiredConsumed)
            {
                return false;
            }
        }
        return hasProgressInput && expectedUnits > 0;
    }

    private int deriveUnitsMadeFromInputs(Map<String, Integer> before)
    {
        int derived = Integer.MAX_VALUE;
        for (RecipeInput input : activeRecipe.getInputs())
        {
            if (!input.isConsumed() || !input.isProgressTracked())
            {
                continue;
            }
            int current = inventoryCount(priceService.getItemId(input.getItemName()));
            int consumed = Math.max(0, before.getOrDefault(input.getItemName(), current) - current);
            derived = Math.min(derived, input.getPossibleOutputUnits(consumed));
        }
        return derived == Integer.MAX_VALUE ? 0 : derived;
    }

    private void registerProcessFailure(String message)
    {
        processFailures++;
        status = message + " (" + processFailures + "/" + MAX_PROCESS_FAILURES + ")";
        if (processFailures >= MAX_PROCESS_FAILURES)
        {
            processFailures = 0;
            state = FactoryState.BANKING_OUTPUT;
        }
    }

    private int inventoryCountByName(String itemName)
    {
        if (itemName == null || itemName.isBlank())
        {
            return 0;
        }
        return Math.max(0, Rs2Inventory.itemQuantity(itemName));
    }

    private int bankCount(int itemId)
    {
        if (itemId <= 0)
        {
            return 0;
        }

        // While the bank is open, the live client ItemContainer is authoritative.
        // Rs2Bank.count() uses a mirrored cache which can be empty or stale if this
        // external plugin did not receive the bank ItemContainerChanged event.
        if (Rs2Bank.isOpen())
        {
            int liveCount = liveContainerCount(InventoryID.BANK, itemId);
            if (liveCount >= 0)
            {
                return liveCount;
            }
        }

        return Math.max(0, Rs2Bank.count(itemId));
    }

    private int liveContainerCount(int containerId, int itemId)
    {
        try
        {
            Integer count = Microbot.getClientThread().runOnClientThreadOptional(() ->
            {
                ItemContainer container = Microbot.getClient().getItemContainer(containerId);
                if (container == null)
                {
                    return -1;
                }

                long total = 0L;
                Item[] items = container.getItems();
                if (items != null)
                {
                    for (Item item : items)
                    {
                        if (item != null && item.getId() == itemId)
                        {
                            total += Math.max(0, item.getQuantity());
                        }
                    }
                }
                return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
            }).orElse(-1);
            return count;
        }
        catch (Exception ex)
        {
            log.debug("Unable to read live item container {}: {}", containerId, ex.getMessage());
            return -1;
        }
    }

    private int inventoryCount(int itemId)
    {
        return itemId <= 0 ? 0 : Math.max(0, Rs2Inventory.itemQuantity(itemId));
    }

    private String getAccountName()
    {
        try
        {
            String name = Microbot.getClientThread().runOnClientThreadOptional(() ->
            {
                Player player = Microbot.getClient().getLocalPlayer();
                return player == null ? null : player.getName();
            }).orElse(null);
            return name == null || name.trim().isEmpty() ? "unknown" : name;
        }
        catch (Exception ex)
        {
            return "unknown";
        }
    }

    private void handleLimitExhaustion(long resetDelayMillis)
    {
        switch (config.limitExhaustedAction())
        {
            case STOP_PLUGIN:
                status = "Buy limit exhausted; stopping";
                shutdown();
                break;
            case WAIT_FOR_RESET:
            case SWITCH_RECIPE:
            default:
                long fallback = TimeUnit.MINUTES.toMillis(config.reevaluateMinutes());
                waitingUntil = System.currentTimeMillis() + Math.max(1_000L,
                    resetDelayMillis > 0 ? Math.min(resetDelayMillis, fallback) : fallback);
                state = FactoryState.WAITING_FOR_LIMIT;
                status = "Waiting for buy-limit capacity";
                break;
        }
    }

    private void markSellMarketBlocked(int itemId)
    {
        if (itemId <= 0)
        {
            return;
        }
        long cooldown = TimeUnit.MINUTES.toMillis(Math.max(1, config.reevaluateMinutes()));
        sellMarketBlockedUntil.put(itemId, System.currentTimeMillis() + cooldown);
    }

    private boolean isSellMarketBlocked(int itemId)
    {
        if (itemId <= 0)
        {
            return false;
        }
        Long until = sellMarketBlockedUntil.get(itemId);
        if (until == null)
        {
            return false;
        }
        if (System.currentTimeMillis() >= until)
        {
            sellMarketBlockedUntil.remove(itemId);
            return false;
        }
        return true;
    }

    private boolean isRecipeSellMarketBlocked(FactoryRecipe recipe)
    {
        if (recipe == null || priceService == null)
        {
            return false;
        }
        return isSellMarketBlocked(priceService.getItemId(recipe.getOutputItemName()));
    }

    /**
     * The maximum SELL reprice path has already cancelled the exact offer and
     * collected the unsold remainder to the bank. That is progress, not a reason
     * to park the entire factory in WAITING_FOR_MARKET. Temporarily block only the
     * failed output and continue through the output queue / recipe selection.
     */
    private void handleExhaustedSellRetryPolicy(int itemId, String itemName, int soldThisPass)
    {
        markSellMarketBlocked(itemId);
        long remainingMs = Math.max(0L, sellMarketBlockedUntil.getOrDefault(itemId, 0L)
            - System.currentTimeMillis());
        long remainingSeconds = Math.max(1L, (remainingMs + 999L) / 1_000L);

        log.info(
            "Sell retry policy exhausted for {}; soldThisPass={}, banked unsold remainder; "
                + "temporarily skipping this output for {}s and continuing",
            itemName, soldThisPass, remainingSeconds
        );

        sellRetryPolicyExhaustedThisCall = false;
        clearGeWaitFlags();
        clearGePlacementBackoff();
        finishCurrentSaleTarget();
        status = "Banked unsold " + itemName + "; skipping it temporarily and continuing";
        resetProgressWatchdog();
    }

    private void waitForFixedRecipeMarket(String reason)
    {
        // Do not use the configured multi-minute automatic-market wait here. Fixed
        // mode is already pinned to one recipe, so a transient quote should be
        // sampled again quickly instead of looking like a permanent "no recipe"
        // failure. FactoryPriceService/Microbot price caching prevents this cadence
        // from turning into an API request loop.
        waitingUntil = System.currentTimeMillis() + FIXED_RECIPE_MARKET_RECHECK_MILLIS;
        state = FactoryState.WAITING_FOR_MARKET;
        status = reason;
    }

    private void waitForMarket(String reason)
    {
        waitingUntil = System.currentTimeMillis()
            + TimeUnit.MINUTES.toMillis(config.reevaluateMinutes());
        state = FactoryState.WAITING_FOR_MARKET;
        status = reason;
    }

    private void waitForReevaluation()
    {
        if (System.currentTimeMillis() >= waitingUntil)
        {
            activePlan = null;
            state = FactoryState.OPENING_BANK;
            status = "Reevaluating";
        }
    }

    private void finishOrReevaluate(String reason)
    {
        log.info("Cycle ended: {}", reason);
        if (cycleProcessedUnits > 0)
        {
            beginSellingOrFinish();
        }
        else
        {
            resetCycle();
        }
    }

    private void resetCycle()
    {
        activePlan = null;
        activeRecipe = null;
        activeQuote = null;
        cycleTargetUnits = 0;
        cycleProcessedUnits = 0;
        outputRemainingToSell = 0;
        sellingItemName = null;
        saleOutputQueue.clear();
        saleOutputIndex = 0;
        processFailures = 0;
        clearGeWaitFlags();
        clearGePlacementBackoff();
        factoryBuyOfferSlots.clear();
        factoryBuyOfferPrices.clear();
        factoryBuyOfferRetries.clear();
        buyPriceRetryByItem.clear();
        pendingCollectedBuyCredits.clear();
        factorySellOfferSlots.clear();
        factorySellOfferPrices.clear();
        factorySellOfferItemIds.clear();
        sellPriceRetryByItem.clear();
        sellRetryPolicyExhaustedThisCall = false;
        state = FactoryState.OPENING_BANK;
        status = "Starting next cycle";
        resetProgressWatchdog();
    }

    private static String safeMessage(Exception ex)
    {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    public FactoryState getState()
    {
        return state;
    }

    public FactoryRecipe getActiveRecipe()
    {
        return activeRecipe;
    }

    public ProfitQuote getActiveQuote()
    {
        return activeQuote;
    }

    public String getStatus()
    {
        return status;
    }

    public FactoryStats getStats()
    {
        return stats;
    }

    public int getCycleTargetUnits()
    {
        return cycleTargetUnits;
    }

    public int getCycleProcessedUnits()
    {
        return cycleProcessedUnits;
    }

    public int getObservedCoinTotal()
    {
        return observedCoinTotal;
    }

    public int getObservedSpendableCoins()
    {
        return observedSpendableCoins;
    }

    public boolean isMembersAccount()
    {
        return membersAccount;
    }

    public boolean isMemberWorld()
    {
        return memberWorld;
    }

    public long getWaitingUntil()
    {
        return waitingUntil;
    }

    public String getRuntimeText()
    {
        Duration duration = stats.getRuntime();
        long hours = duration.toHours();
        long minutes = duration.minusHours(hours).toMinutes();
        long seconds = duration.minusHours(hours).minusMinutes(minutes).getSeconds();
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    public long getWaitSeconds()
    {
        return Math.max(0L, (waitingUntil - System.currentTimeMillis()) / 1_000L);
    }

    public String getAntibanStatus()
    {
        return antiban == null ? "Disabled" : antiban.getStatus();
    }

    public long getAntibanPauseSeconds()
    {
        return antiban == null ? 0L : antiban.getPauseSeconds();
    }

    public int getProgressWatchdogRetryCount()
    {
        return watchdogRetryCount;
    }

    public String getProgressWatchdogStatus()
    {
        return watchdogLastRecovery;
    }

    @Override
    public void shutdown()
    {
        state = FactoryState.STOPPED;
        status = "Stopped";
        activePlan = null;
        activeRecipe = null;
        activeQuote = null;
        outputRemainingToSell = 0;
        sellingItemName = null;
        saleOutputQueue.clear();
        saleOutputIndex = 0;
        factoryBuyOfferSlots.clear();
        factoryBuyOfferPrices.clear();
        factoryBuyOfferRetries.clear();
        buyPriceRetryByItem.clear();
        pendingCollectedBuyCredits.clear();
        factorySellOfferSlots.clear();
        factorySellOfferPrices.clear();
        factorySellOfferItemIds.clear();
        sellPriceRetryByItem.clear();
        watchdogState = FactoryState.STOPPED;
        watchdogFingerprint = "";
        watchdogLastProgressAt = 0L;
        watchdogLastRetryAt = 0L;
        watchdogRetryCount = 0;
        watchdogLastRecovery = "Stopped";
        if (antiban != null)
        {
            antiban.reset();
        }
        super.shutdown();
    }

    private static final class BuyPlacementCandidate
    {
        private final String itemName;
        private final int itemId;
        private final int quantity;
        private final int price;
        private final int retry;

        private BuyPlacementCandidate(String itemName, int itemId, int quantity, int price, int retry)
        {
            this.itemName = itemName;
            this.itemId = itemId;
            this.quantity = quantity;
            this.price = price;
            this.retry = retry;
        }
    }

    private static final class CompletedBuySnapshot
    {
        private final GrandExchangeSlots slot;
        private final int itemId;
        private final int quantity;
        private final int price;
        private final boolean fullyBought;

        private CompletedBuySnapshot(
            GrandExchangeSlots slot,
            int itemId,
            int quantity,
            int price,
            boolean fullyBought)
        {
            this.slot = slot;
            this.itemId = itemId;
            this.quantity = quantity;
            this.price = price;
            this.fullyBought = fullyBought;
        }
    }

    private static final class ExistingOutputBacklog
    {
        private final FactoryRecipe recipe;
        private final String outputName;
        private final int quantity;

        private ExistingOutputBacklog(FactoryRecipe recipe, String outputName, int quantity)
        {
            this.recipe = recipe;
            this.outputName = outputName;
            this.quantity = quantity;
        }
    }

    private static final class CyclePlan
    {
        private final FactoryRecipe recipe;
        private final ProfitQuote quote;
        private final int targetUnits;
        private final int stockUnits;
        private final Map<String, Integer> purchaseQuantities;
        private final Set<Integer> limitBlockedItems;
        private final long nextLimitResetMillis;
        private final int requestedTarget;

        private CyclePlan(
            FactoryRecipe recipe,
            ProfitQuote quote,
            int targetUnits,
            int stockUnits,
            Map<String, Integer> purchaseQuantities,
            Set<Integer> limitBlockedItems,
            long nextLimitResetMillis,
            int requestedTarget)
        {
            this.recipe = recipe;
            this.quote = quote;
            this.targetUnits = targetUnits;
            this.stockUnits = stockUnits;
            this.purchaseQuantities = new LinkedHashMap<>(purchaseQuantities);
            this.limitBlockedItems = Collections.unmodifiableSet(new HashSet<>(limitBlockedItems));
            this.nextLimitResetMillis = nextLimitResetMillis;
            this.requestedTarget = requestedTarget;
        }

        static CyclePlan unavailable(FactoryRecipe recipe, ProfitQuote quote)
        {
            return new CyclePlan(
                recipe,
                quote,
                0,
                0,
                Collections.emptyMap(),
                Collections.emptySet(),
                0L,
                1
            );
        }

        double selectionScore()
        {
            double availability = requestedTarget <= 0
                ? 0.0
                : Math.min(1.0, targetUnits / (double) requestedTarget);
            return quote.getEstimatedProfitPerHour() * Math.sqrt(Math.max(0.0, availability));
        }
    }
}
