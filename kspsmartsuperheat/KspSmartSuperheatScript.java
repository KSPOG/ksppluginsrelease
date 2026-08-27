package net.runelite.client.plugins.microbot.kspsmartsuperheat;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class KspSmartSuperheatScript extends Script
{
    private static final int LOOP_DELAY_MS = 500;
    private static final int BANK_TIMEOUT_MS = 6_000;
    private static final int BANK_WIDGET_TIMEOUT_MS = 4_000;
    private static final int INVENTORY_TIMEOUT_MS = 4_000;
    private static final int BATCH_WITHDRAW_TIMEOUT_MS = 3_500;
    private static final int MAX_BATCH_WITHDRAW_ATTEMPTS = 3;
    private static final int COIN_WITHDRAW_TIMEOUT_MS = 8_000;
    private static final int COIN_WITHDRAW_SETTLE_TIMEOUT_MS = 4_000;
    private static final int MAX_COIN_WITHDRAW_ATTEMPTS = 2;
    private static final int CAST_RESULT_TIMEOUT_MS = 4_500;
    private static final int MAX_CAST_FAILURES = 3;

    private static final int BANK_GROUP = 12;
    private static final int BANK_ROOT_CHILD = 1;
    private static final int BANK_AMOUNT_PROMPT_GROUP = 162;
    private static final int BANK_AMOUNT_PROMPT_CHILD = 43;

    private static final String[] FIRE_RUNE_STAVES = {
        "Staff of fire",
        "Fire battlestaff",
        "Mystic fire staff",
        "Lava battlestaff",
        "Mystic lava staff",
        "Steam battlestaff",
        "Mystic steam staff",
        "Smoke battlestaff",
        "Mystic smoke staff"
    };

    private KspSmartSuperheatConfig config;
    private SuperheatPriceService priceService;

    private volatile SmartSuperheatState state = SmartSuperheatState.STOPPED;
    private volatile String status = "Stopped";
    private volatile SuperheatRecipe activeRecipe;
    private volatile SuperheatQuote activeQuote;
    private volatile boolean freeFireRunes;
    private volatile long nextMarketCheckAt;
    private volatile long nextNoProfitScanAt;
    private volatile long startTime;

    private volatile long barsMade;
    private volatile long estimatedProfit;
    private volatile long magicXp;
    private volatile double smithingXp;
    private volatile int currentBatchTarget;
    private volatile int craftableBarsInBank;
    private volatile int spendableCoins;

    private int castFailures;
    private int sellFailures;
    private final Map<SuperheatRecipe, Integer> unsoldProduced = new EnumMap<>(SuperheatRecipe.class);
    private final Map<SuperheatRecipe, Integer> protectedOutputBaseline = new EnumMap<>(SuperheatRecipe.class);

    public boolean run(KspSmartSuperheatConfig config)
    {
        this.config = config;
        this.priceService = new SuperheatPriceService();
        this.state = SmartSuperheatState.STARTING;
        this.status = "Starting";
        this.activeRecipe = null;
        this.activeQuote = null;
        this.freeFireRunes = false;
        this.nextMarketCheckAt = 0L;
        this.nextNoProfitScanAt = 0L;
        this.startTime = System.currentTimeMillis();
        this.barsMade = 0L;
        this.estimatedProfit = 0L;
        this.magicXp = 0L;
        this.smithingXp = 0.0;
        this.currentBatchTarget = 0;
        this.craftableBarsInBank = 0;
        this.spendableCoins = 0;
        this.castFailures = 0;
        this.sellFailures = 0;
        this.unsoldProduced.clear();
        this.protectedOutputBaseline.clear();

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
                state = SmartSuperheatState.ERROR;
                status = "Error - check client log";
                log.error("KSP Smart Superheat loop error", ex);
                sleep(1_000);
            }
        }, 0, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);

        return true;
    }

    public void stopScript()
    {
        state = SmartSuperheatState.STOPPED;
        status = "Stopped";
        shutdown();
    }

    private void tick()
    {
        switch (state)
        {
            case STARTING:
                startSession();
                break;
            case SCANNING_MARKET:
                scanMarket();
                break;
            case PREPARING_BATCH:
                prepareBatch();
                break;
            case CASTING:
                castOne();
                break;
            case SELLING_OUTPUT:
                sellProducedOutput();
                break;
            case RESTOCKING:
                restock();
                break;
            case WAITING_FOR_PROFIT:
                waitForProfit();
                break;
            case ERROR:
            case STOPPED:
            default:
                break;
        }
    }

    private void startSession()
    {
        if (getRealLevel(Skill.MAGIC) < 43)
        {
            state = SmartSuperheatState.ERROR;
            status = "43 Magic required";
            return;
        }

        if (getRealLevel(Skill.SMITHING) < 1)
        {
            state = SmartSuperheatState.ERROR;
            status = "Smithing level unavailable";
            return;
        }

        state = SmartSuperheatState.SCANNING_MARKET;
        status = "Scanning live prices";
    }

    private void scanMarket()
    {
        int smithing = getRealLevel(Skill.SMITHING);
        freeFireRunes = hasInfiniteFireSource();

        SuperheatQuote best = null;
        SuperheatQuote bestRejected = null;

        for (SuperheatRecipe recipe : SuperheatRecipe.values())
        {
            if (recipe.getSmithingLevel() > smithing)
            {
                continue;
            }

            if (recipe.isMembersOnly() && (!Rs2Player.isMember() || !Rs2Player.isInMemberWorld()))
            {
                continue;
            }

            SuperheatQuote quote = priceService.quote(recipe, config, freeFireRunes);
            if (!quote.isValid())
            {
                continue;
            }

            if (bestRejected == null || quote.getProjectedGpHour() > bestRejected.getProjectedGpHour())
            {
                bestRejected = quote;
            }

            if (!quote.meets(config))
            {
                continue;
            }

            if (best == null
                || quote.getProjectedGpHour() > best.getProjectedGpHour()
                || (quote.getProjectedGpHour() == best.getProjectedGpHour()
                    && quote.getProfitPerBar() > best.getProfitPerBar()))
            {
                best = quote;
            }
        }

        if (best == null)
        {
            activeRecipe = bestRejected == null ? null : bestRejected.getRecipe();
            activeQuote = bestRejected;
            state = SmartSuperheatState.WAITING_FOR_PROFIT;
            nextNoProfitScanAt = System.currentTimeMillis() + config.priceRefreshSeconds() * 1000L;
            status = bestRejected == null
                ? "No usable live market quote"
                : "No recipe meets profit gate";
            return;
        }

        activeRecipe = best.getRecipe();
        activeQuote = best;
        nextMarketCheckAt = System.currentTimeMillis() + config.priceRefreshSeconds() * 1000L;
        state = SmartSuperheatState.PREPARING_BATCH;
        status = "Selected " + activeRecipe.getOutputName();
    }

    private void prepareBatch()
    {
        if (activeRecipe == null || activeQuote == null)
        {
            state = SmartSuperheatState.SCANNING_MARKET;
            return;
        }

        if (!refreshActiveQuoteIfDue())
        {
            return;
        }

        if (!ensureBankOpen())
        {
            status = "Opening GE bank";
            return;
        }

        if (!ensureBankItemMode())
        {
            status = "Waiting for bank item mode";
            return;
        }

        normalizeInventoryForBank();
        ensureAllNatureRunesInInventory();
        captureProtectedOutputBaseline(activeRecipe);

        freeFireRunes = hasInfiniteFireSource();
        activeQuote = priceService.quote(activeRecipe, config, freeFireRunes);
        if (!activeQuote.meets(config))
        {
            state = SmartSuperheatState.SCANNING_MARKET;
            status = "Margin changed - rescanning";
            return;
        }

        craftableBarsInBank = calculateCraftableBarsInBank(activeRecipe, freeFireRunes);
        if (craftableBarsInBank <= 0)
        {
            if (config.autoSellOutput() && totalUnsoldProduced() > 0)
            {
                state = SmartSuperheatState.SELLING_OUTPUT;
                status = "Selling produced bars";
            }
            else
            {
                state = SmartSuperheatState.RESTOCKING;
                status = "Restocking ingredients";
            }
            return;
        }

        int maxBatch = Math.min(activeQuote.getBatchSize(), craftableBarsInBank);
        if (!config.bankWholeInventory())
        {
            int stackSlotsNeeded = 1 + (freeFireRunes ? 0 : 1);
            int occupiedWorkStacks = 0;
            if (Rs2Inventory.itemQuantity(ItemID.NATURERUNE) > 0) occupiedWorkStacks++;
            if (!freeFireRunes && Rs2Inventory.itemQuantity(ItemID.FIRERUNE) > 0) occupiedWorkStacks++;
            int availableSlots = Rs2Inventory.emptySlotCount() + occupiedWorkStacks;
            int materialSlots = Math.max(1, activeRecipe.getMaterialSlotsPerBar());
            maxBatch = Math.min(maxBatch, Math.max(0, (availableSlots - stackSlotsNeeded) / materialSlots));
        }

        if (maxBatch <= 0)
        {
            status = "Not enough free inventory space";
            return;
        }

        currentBatchTarget = maxBatch;
        if (!withdrawBatch(activeRecipe, maxBatch, freeFireRunes))
        {
            status = "Waiting for complete batch withdrawal";
            return;
        }

        if (!inventoryHasBatchSupplies(activeRecipe, maxBatch, freeFireRunes))
        {
            status = "Batch ratio incomplete - retrying";
            return;
        }

        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 3_000);
        castFailures = 0;
        state = SmartSuperheatState.CASTING;
        status = "Superheating " + activeRecipe.getOutputName();
    }

    private void castOne()
    {
        if (activeRecipe == null)
        {
            state = SmartSuperheatState.SCANNING_MARKET;
            return;
        }

        if (!refreshActiveQuoteIfDue())
        {
            return;
        }

        freeFireRunes = hasInfiniteFireSource();
        if (!inventoryHasCastSupplies(activeRecipe, freeFireRunes))
        {
            state = SmartSuperheatState.PREPARING_BATCH;
            status = "Batch complete";
            return;
        }

        int outputBefore = inventoryCountByName(activeRecipe.getOutputName());
        int primaryBefore = Rs2Inventory.itemQuantity(activeRecipe.getPrimaryOreId());

        status = "Casting on " + activeRecipe.getPrimaryOreName();
        Rs2Magic.superHeat(activeRecipe.getPrimaryOreId(), config.castDelayMinMs(), config.castDelayMaxMs());

        boolean success = sleepUntil(() ->
            inventoryCountByName(activeRecipe.getOutputName()) > outputBefore
                || Rs2Inventory.itemQuantity(activeRecipe.getPrimaryOreId()) < primaryBefore,
            CAST_RESULT_TIMEOUT_MS
        );

        if (!success)
        {
            castFailures++;
            status = "Cast did not register (" + castFailures + "/" + MAX_CAST_FAILURES + ")";
            if (castFailures >= MAX_CAST_FAILURES)
            {
                state = SmartSuperheatState.PREPARING_BATCH;
                castFailures = 0;
                status = "Recovering through bank";
            }
            return;
        }

        castFailures = 0;
        barsMade++;
        magicXp += 53L;
        smithingXp += activeRecipe.getSmithingXp();
        estimatedProfit += activeQuote == null ? 0 : activeQuote.getProfitPerBar();
        unsoldProduced.merge(activeRecipe, 1, Integer::sum);

        if (!inventoryHasCastSupplies(activeRecipe, freeFireRunes))
        {
            state = SmartSuperheatState.PREPARING_BATCH;
            status = "Banking completed batch";
        }
    }

    private void restock()
    {
        if (activeRecipe == null)
        {
            state = SmartSuperheatState.SCANNING_MARKET;
            return;
        }

        freeFireRunes = hasInfiniteFireSource();
        activeQuote = priceService.quote(activeRecipe, config, freeFireRunes);
        if (!activeQuote.meets(config))
        {
            state = SmartSuperheatState.SCANNING_MARKET;
            status = "Recipe no longer profitable";
            return;
        }

        if (!ensureBankOpen())
        {
            status = "Opening bank for restock";
            return;
        }

        if (!ensureBankItemMode())
        {
            status = "Waiting for bank item mode";
            return;
        }

        normalizeInventoryForRestock();
        ensureAllNatureRunesInInventory();
        captureProtectedOutputBaseline(activeRecipe);

        int alreadyCraftable = calculateCraftableBarsInBank(activeRecipe, freeFireRunes);
        craftableBarsInBank = alreadyCraftable;
        if (alreadyCraftable > 0)
        {
            state = SmartSuperheatState.PREPARING_BATCH;
            status = "Using banked ingredients";
            return;
        }

        if (config.autoSellOutput() && totalUnsoldProduced() > 0)
        {
            state = SmartSuperheatState.SELLING_OUTPUT;
            status = "Selling output to fund restock";
            return;
        }

        long coinTotal = (long) Rs2Bank.count(ItemID.COINS) + Rs2Inventory.itemQuantity(ItemID.COINS);
        long afterReserve = Math.max(0L, coinTotal - config.cashReserve());
        long budget = afterReserve * config.maxSpendPercent() / 100L;
        long executableBudget = Math.min(budget, Integer.MAX_VALUE);
        spendableCoins = clampInt(executableBudget);

        int targetBars = calculateAffordableRestockBars(
            activeRecipe,
            activeQuote,
            freeFireRunes,
            executableBudget
        );

        if (targetBars <= 0)
        {
            state = SmartSuperheatState.WAITING_FOR_PROFIT;
            nextNoProfitScanAt = System.currentTimeMillis() + config.priceRefreshSeconds() * 1000L;
            status = "Not enough spendable cash";
            return;
        }

        long plannedCost = calculateRestockCost(activeRecipe, activeQuote, freeFireRunes, targetBars);
        if (plannedCost <= 0L || plannedCost > executableBudget || plannedCost > Integer.MAX_VALUE)
        {
            status = "Replanning restock budget";
            return;
        }

        int plannedCoins = (int) plannedCost;
        if (!ensureCoinsInInventory(plannedCoins))
        {
            long liquidCoins = (long) Rs2Bank.count(ItemID.COINS) + Rs2Inventory.itemQuantity(ItemID.COINS);
            log.debug(
                "Restock funding retry: targetBars={}, plannedCost={}, inventory={}, bank={}, liquid={}",
                targetBars,
                plannedCost,
                Rs2Inventory.itemQuantity(ItemID.COINS),
                Rs2Bank.count(ItemID.COINS),
                liquidCoins
            );

            if (Math.max(0L, liquidCoins - config.cashReserve()) <= 0L)
            {
                state = SmartSuperheatState.WAITING_FOR_PROFIT;
                nextNoProfitScanAt = System.currentTimeMillis() + config.priceRefreshSeconds() * 1000L;
                status = "Not enough spendable cash";
            }
            else
            {
                status = "Funding restock plan";
            }
            return;
        }

        log.debug(
            "Atomic restock plan: bars={}, plannedCost={}, inventoryCoins={}, bankCoins={}, reserve={}",
            targetBars,
            plannedCost,
            Rs2Inventory.itemQuantity(ItemID.COINS),
            Rs2Bank.count(ItemID.COINS),
            config.cashReserve()
        );

        long[] remainingBudget = {plannedCost};

        if (!buyMissing(
            activeRecipe.getPrimaryOreName(),
            activeRecipe.getPrimaryOreId(),
            targetBars * activeRecipe.getPrimaryOrePerBar(),
            activeQuote.getPrimaryBuyPrice(),
            remainingBudget))
        {
            return;
        }

        if (activeRecipe.hasSecondaryOre()
            && !buyMissing(
                activeRecipe.getSecondaryOreName(),
                activeRecipe.getSecondaryOreId(),
                targetBars * activeRecipe.getSecondaryOrePerBar(),
                activeQuote.getSecondaryBuyPrice(),
                remainingBudget))
        {
            return;
        }

        if (activeRecipe.getCoalPerBar() > 0
            && !buyMissing(
                "Coal",
                ItemID.COAL,
                targetBars * activeRecipe.getCoalPerBar(),
                activeQuote.getCoalBuyPrice(),
                remainingBudget))
        {
            return;
        }

        if (!buyMissing(
            "Nature rune",
            ItemID.NATURERUNE,
            targetBars,
            activeQuote.getNatureBuyPrice(),
            remainingBudget))
        {
            return;
        }

        if (!freeFireRunes
            && !buyMissing(
                "Fire rune",
                ItemID.FIRERUNE,
                targetBars * 4,
                activeQuote.getFireBuyPrice(),
                remainingBudget))
        {
            return;
        }

        if (!ensureBankOpen())
        {
            return;
        }

        craftableBarsInBank = calculateCraftableBarsInBank(activeRecipe, freeFireRunes);
        if (craftableBarsInBank > 0)
        {
            state = SmartSuperheatState.PREPARING_BATCH;
            status = "Restock ready";
        }
        else
        {
            status = "Verifying restock";
        }
    }

    private boolean buyMissing(
        String itemName,
        int itemId,
        int desiredTotal,
        int offerPrice,
        long[] remainingBudget)
    {
        if (!ensureBankOpen())
        {
            return false;
        }

        int available = availableResourceQuantity(itemId);
        int missing = Math.max(0, desiredTotal - available);
        if (missing <= 0)
        {
            return true;
        }

        if (offerPrice <= 0 || remainingBudget == null || remainingBudget.length == 0)
        {
            status = "Invalid purchase cost for " + itemName;
            return false;
        }

        long requiredCoinsLong = (long) missing * offerPrice;
        if (requiredCoinsLong <= 0L || requiredCoinsLong > Integer.MAX_VALUE)
        {
            status = "Invalid purchase cost for " + itemName;
            return false;
        }

        if (requiredCoinsLong > remainingBudget[0])
        {
            log.debug(
                "Restock budget drift for {}: required={}, remaining={}",
                itemName,
                requiredCoinsLong,
                remainingBudget[0]
            );
            status = "Replanning budget for " + itemName;
            return false;
        }

        int requiredCoins = (int) requiredCoinsLong;
        if (Rs2Inventory.itemQuantity(ItemID.COINS) < requiredCoins && !ensureCoinsInInventory(requiredCoins))
        {
            log.debug(
                "Restock cash drift for {}: required={}, inventory={}, bank={}, remainingPlan={}",
                itemName,
                requiredCoins,
                Rs2Inventory.itemQuantity(ItemID.COINS),
                Rs2Bank.count(ItemID.COINS),
                remainingBudget[0]
            );
            status = "Replanning cash for " + itemName;
            return false;
        }

        Rs2Bank.closeBank();
        status = "Buying " + formatNumber(missing) + " " + itemName;

        SmartGeTrader.TradeResult result = SmartGeTrader.buyToBank(
            itemName,
            missing,
            offerPrice,
            config.geOfferTimeoutSeconds()
        );

        if (!result.isPlaced())
        {
            status = result.getMessage();
            return false;
        }

        int filled = Math.min(missing, result.getFilledQuantity());
        if (filled <= 0)
        {
            status = "No fill for " + itemName;
            return false;
        }

        long committed = Math.min(remainingBudget[0], (long) filled * offerPrice);
        remainingBudget[0] = Math.max(0L, remainingBudget[0] - committed);
        spendableCoins = clampInt(remainingBudget[0]);

        if (filled < missing)
        {
            status = "Partial " + itemName + " fill - replanning";
            sleep(250, 500);
            return false;
        }

        status = "Bought " + formatNumber(filled) + " " + itemName;
        sleep(250, 500);
        return true;
    }

    private void sellProducedOutput()
    {
        if (!config.autoSellOutput())
        {
            state = SmartSuperheatState.RESTOCKING;
            return;
        }

        SuperheatRecipe recipeToSell = null;
        int trackedAmount = 0;

        for (Map.Entry<SuperheatRecipe, Integer> entry : unsoldProduced.entrySet())
        {
            if (entry.getValue() != null && entry.getValue() > 0)
            {
                recipeToSell = entry.getKey();
                trackedAmount = entry.getValue();
                break;
            }
        }

        if (recipeToSell == null || trackedAmount <= 0)
        {
            sellFailures = 0;
            state = SmartSuperheatState.RESTOCKING;
            status = "Output sales complete";
            return;
        }

        final SuperheatRecipe saleRecipe = recipeToSell;

        if (!ensureBankOpen())
        {
            status = "Opening bank for sale";
            return;
        }

        if (!ensureBankItemMode())
        {
            status = "Waiting for bank item mode";
            return;
        }

        normalizeInventoryForBank();
        ensureAllNatureRunesInInventory();

        captureProtectedOutputBaseline(saleRecipe);
        int banked = Rs2Bank.count(saleRecipe.getOutputId());
        int protectedAmount = protectedOutputBaseline.getOrDefault(saleRecipe, banked);
        int sessionOwnedInBank = Math.max(0, banked - protectedAmount);
        int toSell = Math.min(trackedAmount, sessionOwnedInBank);
        if (toSell <= 0)
        {
            unsoldProduced.put(saleRecipe, 0);
            return;
        }

        SuperheatQuote saleQuote = priceService.quote(saleRecipe, config, hasInfiniteFireSource());
        if (!saleQuote.isValid() || saleQuote.getOutputSellPrice() <= 0)
        {
            status = "No current sell price for " + saleRecipe.getOutputName();
            return;
        }

        if (!ensureBankNoteMode())
        {
            status = "Waiting for bank note mode";
            return;
        }

        if (!waitForBankAmountPromptClosed())
        {
            status = "Waiting for bank amount prompt";
            return;
        }

        Rs2ItemModel outputRow = Rs2Bank.bankItems().stream()
            .filter(item -> item != null && item.getId() == saleRecipe.getOutputId())
            .findFirst()
            .orElse(null);
        if (outputRow == null)
        {
            status = "Waiting for bar bank widget";
            return;
        }

        sleep(400, 650);
        int inventoryBefore = inventoryCountByName(saleRecipe.getOutputName());
        int epochBefore = Rs2Bank.getBankLiveEpoch();
        if (!Rs2Bank.withdrawX(saleRecipe.getOutputId(), toSell))
        {
            status = "Could not request noted " + saleRecipe.getOutputName();
            return;
        }

        sleepUntil(
            () -> inventoryCountByName(saleRecipe.getOutputName()) > inventoryBefore,
            INVENTORY_TIMEOUT_MS
        );
        Rs2Bank.syncBankInventoryAfterChange(epochBefore);
        waitForBankAmountPromptClosed();

        int inventoryQty = inventoryCountByName(saleRecipe.getOutputName()) - inventoryBefore;
        if (inventoryQty <= 0)
        {
            status = "Could not withdraw noted " + saleRecipe.getOutputName();
            return;
        }

        Rs2Bank.closeBank();
        status = "Selling " + formatNumber(inventoryQty) + " noted " + saleRecipe.getOutputName();

        SmartGeTrader.TradeResult result = SmartGeTrader.sellFromInventory(
            saleRecipe.getOutputName(),
            inventoryQty,
            saleQuote.getOutputSellPrice(),
            config.geOfferTimeoutSeconds()
        );

        int sold = Math.min(trackedAmount, result.getFilledQuantity());
        if (sold > 0)
        {
            unsoldProduced.put(saleRecipe, Math.max(0, trackedAmount - sold));
            sellFailures = 0;
            status = "Sold " + formatNumber(sold) + " " + saleRecipe.getOutputName();
            return;
        }

        sellFailures++;
        status = result.getMessage().isEmpty() ? "No output sale fill" : result.getMessage();
        if (sellFailures >= 3)
        {
            sellFailures = 0;
            state = SmartSuperheatState.WAITING_FOR_PROFIT;
            nextNoProfitScanAt = System.currentTimeMillis() + config.priceRefreshSeconds() * 1000L;
            status = "Output not selling - pausing";
        }
    }

    private void waitForProfit()
    {
        if (System.currentTimeMillis() >= nextNoProfitScanAt)
        {
            state = SmartSuperheatState.SCANNING_MARKET;
            status = "Rechecking market";
        }
    }

    private boolean refreshActiveQuoteIfDue()
    {
        if (System.currentTimeMillis() < nextMarketCheckAt)
        {
            return true;
        }

        freeFireRunes = hasInfiniteFireSource();
        activeQuote = priceService.quote(activeRecipe, config, freeFireRunes);
        nextMarketCheckAt = System.currentTimeMillis() + config.priceRefreshSeconds() * 1000L;

        if (!activeQuote.meets(config))
        {
            state = SmartSuperheatState.SCANNING_MARKET;
            status = "Profit gate failed - rescanning";
            return false;
        }

        return true;
    }

    private boolean ensureBankOpen()
    {
        if (Rs2GrandExchange.isOpen())
        {
            Rs2GrandExchange.closeExchange();
            sleepUntil(() -> !Rs2GrandExchange.isOpen(), BANK_WIDGET_TIMEOUT_MS);
        }

        if (Rs2Bank.isOpen())
        {
            return ensureBankWidgetsReady();
        }

        if (Rs2Bank.openBank())
        {
            if (!sleepUntil(Rs2Bank::isOpen, BANK_TIMEOUT_MS))
            {
                return false;
            }
            return ensureBankWidgetsReady();
        }

        Rs2GrandExchange.walkToGrandExchange();
        sleepUntil(() -> !Rs2Player.isMoving(), 20_000);

        if (!Rs2Bank.openBank() || !sleepUntil(Rs2Bank::isOpen, BANK_TIMEOUT_MS))
        {
            return false;
        }

        return ensureBankWidgetsReady();
    }

    private boolean bankWidgetsReady()
    {
        return Rs2Bank.isOpen()
            && Rs2Widget.isWidgetVisible(BANK_GROUP, BANK_ROOT_CHILD)
            && Rs2Widget.getWidget(InterfaceID.Bankmain.NOTE) != null
            && Rs2Widget.getWidget(InterfaceID.Bankmain.QUANTITY1_TEXT) != null;
    }

    private boolean ensureBankWidgetsReady()
    {
        return bankWidgetsReady() || sleepUntil(this::bankWidgetsReady, BANK_WIDGET_TIMEOUT_MS);
    }

    private boolean ensureBankItemMode()
    {
        if (!ensureBankWidgetsReady())
        {
            return false;
        }

        if (Rs2Bank.hasWithdrawAsItem())
        {
            return true;
        }

        // Do not use Rs2Bank.setWithdrawAsItem(). In the current Microbot helper,
        // the item-mode branch clicks Bankmain.QUANTITY1_TEXT, which changes the
        // default withdrawal quantity instead of toggling note/item mode.
        if (!Rs2Widget.clickWidget(InterfaceID.Bankmain.NOTE))
        {
            return false;
        }

        return sleepUntil(
            () -> bankWidgetsReady() && Rs2Bank.hasWithdrawAsItem(),
            BANK_WIDGET_TIMEOUT_MS
        );
    }

    private boolean ensureBankNoteMode()
    {
        if (!ensureBankWidgetsReady())
        {
            return false;
        }

        if (!Rs2Bank.hasWithdrawAsItem())
        {
            return true;
        }

        if (!Rs2Widget.clickWidget(InterfaceID.Bankmain.NOTE))
        {
            return false;
        }

        return sleepUntil(
            () -> bankWidgetsReady() && !Rs2Bank.hasWithdrawAsItem(),
            BANK_WIDGET_TIMEOUT_MS
        );
    }

    private boolean bankAmountPromptOpen()
    {
        var prompt = Rs2Widget.getWidget(BANK_AMOUNT_PROMPT_GROUP, BANK_AMOUNT_PROMPT_CHILD);
        return prompt != null
            && prompt.getText() != null
            && prompt.getText().equalsIgnoreCase("Enter amount:");
    }

    private boolean waitForBankAmountPromptClosed()
    {
        return !bankAmountPromptOpen()
            || sleepUntil(() -> !bankAmountPromptOpen(), BANK_WIDGET_TIMEOUT_MS);
    }

    private void normalizeInventoryForBank()
    {
        if (!ensureBankWidgetsReady())
        {
            return;
        }

        if (config.bankWholeInventory())
        {
            for (Rs2ItemModel item : Rs2Inventory.all())
            {
                if (item == null || item.getId() <= 0 || item.getId() == ItemID.NATURERUNE)
                {
                    continue;
                }
                depositIfPresent(item.getId());
            }
            return;
        }

        for (SuperheatRecipe recipe : SuperheatRecipe.values())
        {
            depositIfPresent(recipe.getPrimaryOreId());
            if (recipe.hasSecondaryOre()) depositIfPresent(recipe.getSecondaryOreId());
            depositIfPresent(recipe.getOutputId());
        }

        depositIfPresent(ItemID.COAL);
        depositIfPresent(ItemID.FIRERUNE);
        depositIfPresent(ItemID.COINS);
    }

    private void normalizeInventoryForRestock()
    {
        if (!ensureBankWidgetsReady())
        {
            return;
        }

        if (config.bankWholeInventory())
        {
            for (Rs2ItemModel item : Rs2Inventory.all())
            {
                if (item == null
                    || item.getId() <= 0
                    || item.getId() == ItemID.COINS
                    || item.getId() == ItemID.NATURERUNE)
                {
                    continue;
                }
                depositIfPresent(item.getId());
            }
            return;
        }

        for (SuperheatRecipe recipe : SuperheatRecipe.values())
        {
            depositIfPresent(recipe.getPrimaryOreId());
            if (recipe.hasSecondaryOre()) depositIfPresent(recipe.getSecondaryOreId());
            depositIfPresent(recipe.getOutputId());
        }

        depositIfPresent(ItemID.COAL);
        depositIfPresent(ItemID.FIRERUNE);
    }

    private void depositIfPresent(int itemId)
    {
        if (itemId <= 0 || Rs2Inventory.itemQuantity(itemId) <= 0 || !ensureBankWidgetsReady())
        {
            return;
        }

        int epochBefore = Rs2Bank.getBankLiveEpoch();
        if (Rs2Bank.depositAll(itemId))
        {
            Rs2Bank.syncBankInventoryAfterChange(epochBefore);
            sleep(120, 220);
        }
    }

    private void ensureAllNatureRunesInInventory()
    {
        if (!ensureBankWidgetsReady() || !ensureBankItemMode())
        {
            return;
        }

        int totalNatureRunes = Rs2Inventory.itemQuantity(ItemID.NATURERUNE)
            + Math.max(0, Rs2Bank.count(ItemID.NATURERUNE));
        if (totalNatureRunes > 0)
        {
            ensureInventoryAmount(ItemID.NATURERUNE, totalNatureRunes, "Nature rune");
        }
    }

    private boolean withdrawBatch(SuperheatRecipe recipe, int bars, boolean freeFire)
    {
        if (recipe == null || bars <= 0 || !ensureBankWidgetsReady() || !ensureBankItemMode())
        {
            return false;
        }

        if (!ensureInventoryAmount(
            recipe.getPrimaryOreId(),
            bars * recipe.getPrimaryOrePerBar(),
            recipe.getPrimaryOreName()))
        {
            return false;
        }

        if (recipe.hasSecondaryOre()
            && !ensureInventoryAmount(
                recipe.getSecondaryOreId(),
                bars * recipe.getSecondaryOrePerBar(),
                recipe.getSecondaryOreName()))
        {
            return false;
        }

        if (recipe.getCoalPerBar() > 0
            && !ensureInventoryAmount(ItemID.COAL, bars * recipe.getCoalPerBar(), "Coal"))
        {
            return false;
        }

        ensureAllNatureRunesInInventory();
        if (Rs2Inventory.itemQuantity(ItemID.NATURERUNE) < bars)
        {
            return false;
        }

        if (!freeFire && !ensureInventoryAmount(ItemID.FIRERUNE, bars * 4, "Fire rune"))
        {
            return false;
        }

        return inventoryHasBatchSupplies(recipe, bars, freeFire);
    }

    private boolean ensureInventoryAmount(int itemId, int requiredTotal, String itemName)
    {
        if (itemId <= 0 || requiredTotal <= 0)
        {
            return true;
        }

        for (int attempt = 1; attempt <= MAX_BATCH_WITHDRAW_ATTEMPTS; attempt++)
        {
            if (!ensureBankWidgetsReady() || !ensureBankItemMode() || !waitForBankAmountPromptClosed())
            {
                sleep(250, 450);
                continue;
            }

            int current = Rs2Inventory.itemQuantity(itemId);
            if (current >= requiredTotal)
            {
                return true;
            }

            int banked = Math.max(0, Rs2Bank.count(itemId));
            Rs2ItemModel bankRow = Rs2Bank.bankItems().stream()
                .filter(item -> item != null && item.getId() == itemId)
                .findFirst()
                .orElse(null);
            if (banked <= 0 || bankRow == null)
            {
                log.debug(
                    "Batch withdrawal shortfall for {}: required={}, inventory={}, bank={}",
                    itemName,
                    requiredTotal,
                    current,
                    banked
                );
                return false;
            }

            int deficit = requiredTotal - current;
            int request = Math.min(deficit, banked);
            int emptySlots = Rs2Inventory.emptySlotCount();
            if ((!bankRow.isStackable() && emptySlots < request)
                || (bankRow.isStackable() && current <= 0 && emptySlots <= 0))
            {
                status = "Waiting for inventory space for " + itemName;
                return false;
            }

            int before = current;
            int epochBefore = Rs2Bank.getBankLiveEpoch();
            status = "Withdrawing " + formatNumber(request) + " " + itemName;

            log.debug(
                "Batch withdrawal {}/{} for {}: required={}, inventory={}, bank={}, request={}",
                attempt,
                MAX_BATCH_WITHDRAW_ATTEMPTS,
                itemName,
                requiredTotal,
                current,
                banked,
                request
            );

            if (!Rs2Bank.withdrawX(itemId, request))
            {
                waitForBankAmountPromptClosed();
                sleep(300, 500);
                continue;
            }

            boolean changed = sleepUntil(
                () -> Rs2Inventory.itemQuantity(itemId) > before,
                BATCH_WITHDRAW_TIMEOUT_MS
            );

            Rs2Bank.syncBankInventoryAfterChange(epochBefore);
            waitForBankAmountPromptClosed();
            sleep(changed ? 300 : 450, changed ? 500 : 700);
        }

        int actual = Rs2Inventory.itemQuantity(itemId);
        if (actual < requiredTotal)
        {
            log.debug(
                "Batch withdrawal incomplete for {}: required={}, actual={}",
                itemName,
                requiredTotal,
                actual
            );
        }
        return actual >= requiredTotal;
    }

    private boolean inventoryHasBatchSupplies(SuperheatRecipe recipe, int bars, boolean freeFire)
    {
        if (recipe == null || bars <= 0)
        {
            return false;
        }

        if (Rs2Inventory.itemQuantity(recipe.getPrimaryOreId()) < bars * recipe.getPrimaryOrePerBar())
        {
            return false;
        }
        if (recipe.hasSecondaryOre()
            && Rs2Inventory.itemQuantity(recipe.getSecondaryOreId()) < bars * recipe.getSecondaryOrePerBar())
        {
            return false;
        }
        if (recipe.getCoalPerBar() > 0
            && Rs2Inventory.itemQuantity(ItemID.COAL) < bars * recipe.getCoalPerBar())
        {
            return false;
        }
        if (Rs2Inventory.itemQuantity(ItemID.NATURERUNE) < bars)
        {
            return false;
        }
        return freeFire || Rs2Inventory.itemQuantity(ItemID.FIRERUNE) >= bars * 4;
    }

    private boolean ensureCoinsInInventory(int required)
    {
        if (required <= 0)
        {
            return false;
        }

        // Always trust an already-funded inventory stack before requiring bank widgets.
        // This prevents a transient bank/widget close from sending the state machine back
        // to "Funding restock plan" when the required cash is already present.
        int inventoryCoins = Rs2Inventory.itemQuantity(ItemID.COINS);
        if (inventoryCoins >= required)
        {
            return true;
        }

        if (!ensureBankWidgetsReady() || !ensureBankItemMode() || !waitForBankAmountPromptClosed())
        {
            return false;
        }

        for (int attempt = 1; attempt <= MAX_COIN_WITHDRAW_ATTEMPTS; attempt++)
        {
            inventoryCoins = Rs2Inventory.itemQuantity(ItemID.COINS);
            if (inventoryCoins >= required)
            {
                return true;
            }

            int bankCoins = Rs2Bank.count(ItemID.COINS);
            long liquidCoins = (long) inventoryCoins + bankCoins;
            if (liquidCoins < required)
            {
                return false;
            }

            if (bankCoins <= 0)
            {
                return inventoryCoins >= required;
            }

            // Withdraw-All deliberately avoids the bank X-amount selector/prompt for
            // stackable coins. The configured reserve remains protected because the
            // atomic GE plan is still capped by executableBudget/plannedCost.
            if (Rs2Inventory.isFull())
            {
                status = "Waiting for inventory space for coins";
                return false;
            }

            int inventoryBefore = inventoryCoins;
            int bankBefore = bankCoins;
            int epochBefore = Rs2Bank.getBankLiveEpoch();
            status = "Withdrawing available coins";

            log.debug(
                "Coin stack funding {}/{}: required={}, inventory={}, bank={}, liquid={}",
                attempt,
                MAX_COIN_WITHDRAW_ATTEMPTS,
                required,
                inventoryCoins,
                bankCoins,
                liquidCoins
            );

            if (!Rs2Bank.withdrawAll(ItemID.COINS))
            {
                sleep(300, 500);
                continue;
            }

            boolean transferObserved = sleepUntil(() ->
                    Rs2Inventory.itemQuantity(ItemID.COINS) > inventoryBefore
                        || (Rs2Bank.isOpen() && Rs2Bank.count(ItemID.COINS) < bankBefore),
                COIN_WITHDRAW_TIMEOUT_MS);

            Rs2Bank.syncBankInventoryAfterChange(epochBefore);

            int fundedCoins = Rs2Inventory.itemQuantity(ItemID.COINS);
            if (fundedCoins >= required)
            {
                return true;
            }

            if (transferObserved && sleepUntil(
                () -> Rs2Inventory.itemQuantity(ItemID.COINS) >= required,
                COIN_WITHDRAW_SETTLE_TIMEOUT_MS))
            {
                return true;
            }

            log.debug(
                "Coin funding not yet complete: required={}, inventory={}, bank={}",
                required,
                Rs2Inventory.itemQuantity(ItemID.COINS),
                Rs2Bank.isOpen() ? Rs2Bank.count(ItemID.COINS) : -1
            );
            sleep(300, 500);
        }

        return Rs2Inventory.itemQuantity(ItemID.COINS) >= required;
    }

    private int calculateAffordableRestockBars(
        SuperheatRecipe recipe,
        SuperheatQuote quote,
        boolean freeFire,
        long budget)
    {
        if (!Rs2Bank.isOpen() || recipe == null || quote == null || budget <= 0L)
        {
            return 0;
        }

        long upperBound = resourceBarCapacity(
            recipe.getPrimaryOreId(),
            recipe.getPrimaryOrePerBar(),
            quote.getPrimaryBuyPrice(),
            budget);

        if (recipe.hasSecondaryOre())
        {
            upperBound = Math.min(upperBound, resourceBarCapacity(
                recipe.getSecondaryOreId(),
                recipe.getSecondaryOrePerBar(),
                quote.getSecondaryBuyPrice(),
                budget));
        }

        if (recipe.getCoalPerBar() > 0)
        {
            upperBound = Math.min(upperBound, resourceBarCapacity(
                ItemID.COAL,
                recipe.getCoalPerBar(),
                quote.getCoalBuyPrice(),
                budget));
        }

        upperBound = Math.min(upperBound, resourceBarCapacity(
            ItemID.NATURERUNE,
            1,
            quote.getNatureBuyPrice(),
            budget));

        if (!freeFire)
        {
            upperBound = Math.min(upperBound, resourceBarCapacity(
                ItemID.FIRERUNE,
                4,
                quote.getFireBuyPrice(),
                budget));
        }

        int low = 0;
        int high = clampInt(upperBound);
        while (low < high)
        {
            int mid = low + (int) (((long) high - low + 1L) / 2L);
            if (calculateRestockCost(recipe, quote, freeFire, mid) <= budget)
            {
                low = mid;
            }
            else
            {
                high = mid - 1;
            }
        }

        return low;
    }

    private long resourceBarCapacity(int itemId, int amountPerBar, int offerPrice, long budget)
    {
        if (itemId <= 0 || amountPerBar <= 0 || offerPrice <= 0)
        {
            return 0L;
        }

        long available = availableResourceQuantity(itemId);
        long purchasable = Math.max(0L, budget) / offerPrice;
        long capacity = Math.max(0L, (available + purchasable) / amountPerBar);
        long safeCapacity = Integer.MAX_VALUE / (long) amountPerBar;
        return Math.min(capacity, safeCapacity);
    }

    private long calculateRestockCost(
        SuperheatRecipe recipe,
        SuperheatQuote quote,
        boolean freeFire,
        int bars)
    {
        if (bars <= 0)
        {
            return 0L;
        }

        long total = missingPurchaseCost(
            recipe.getPrimaryOreId(),
            (long) bars * recipe.getPrimaryOrePerBar(),
            quote.getPrimaryBuyPrice());

        if (recipe.hasSecondaryOre())
        {
            total += missingPurchaseCost(
                recipe.getSecondaryOreId(),
                (long) bars * recipe.getSecondaryOrePerBar(),
                quote.getSecondaryBuyPrice());
        }

        if (recipe.getCoalPerBar() > 0)
        {
            total += missingPurchaseCost(
                ItemID.COAL,
                (long) bars * recipe.getCoalPerBar(),
                quote.getCoalBuyPrice());
        }

        total += missingPurchaseCost(ItemID.NATURERUNE, bars, quote.getNatureBuyPrice());

        if (!freeFire)
        {
            total += missingPurchaseCost(ItemID.FIRERUNE, (long) bars * 4L, quote.getFireBuyPrice());
        }

        return total;
    }

    private long missingPurchaseCost(int itemId, long desiredTotal, int offerPrice)
    {
        if (itemId <= 0 || desiredTotal <= 0L || offerPrice <= 0)
        {
            return 0L;
        }

        long available = availableResourceQuantity(itemId);
        long missing = Math.max(0L, desiredTotal - available);
        return missing * offerPrice;
    }

    private int availableResourceQuantity(int itemId)
    {
        long banked = Math.max(0, Rs2Bank.count(itemId));
        if (itemId == ItemID.NATURERUNE)
        {
            banked += Math.max(0, Rs2Inventory.itemQuantity(ItemID.NATURERUNE));
        }
        return clampInt(banked);
    }

    private int calculateCraftableBarsInBank(SuperheatRecipe recipe, boolean freeFire)
    {
        if (!Rs2Bank.isOpen())
        {
            return 0;
        }

        int bars = Rs2Bank.count(recipe.getPrimaryOreId()) / recipe.getPrimaryOrePerBar();

        if (recipe.hasSecondaryOre())
        {
            bars = Math.min(bars, Rs2Bank.count(recipe.getSecondaryOreId()) / recipe.getSecondaryOrePerBar());
        }

        if (recipe.getCoalPerBar() > 0)
        {
            bars = Math.min(bars, Rs2Bank.count(ItemID.COAL) / recipe.getCoalPerBar());
        }

        bars = Math.min(bars, availableResourceQuantity(ItemID.NATURERUNE));

        if (!freeFire)
        {
            bars = Math.min(bars, Rs2Bank.count(ItemID.FIRERUNE) / 4);
        }

        return Math.max(0, bars);
    }

    private void captureProtectedOutputBaseline(SuperheatRecipe recipe)
    {
        if (recipe == null || protectedOutputBaseline.containsKey(recipe) || !Rs2Bank.isOpen())
        {
            return;
        }

        protectedOutputBaseline.put(recipe, Math.max(0, Rs2Bank.count(recipe.getOutputId())));
    }

    private boolean inventoryHasCastSupplies(SuperheatRecipe recipe, boolean freeFire)
    {
        if (Rs2Inventory.itemQuantity(recipe.getPrimaryOreId()) < recipe.getPrimaryOrePerBar()) return false;
        if (recipe.hasSecondaryOre()
            && Rs2Inventory.itemQuantity(recipe.getSecondaryOreId()) < recipe.getSecondaryOrePerBar()) return false;
        if (recipe.getCoalPerBar() > 0
            && Rs2Inventory.itemQuantity(ItemID.COAL) < recipe.getCoalPerBar()) return false;
        if (Rs2Inventory.itemQuantity(ItemID.NATURERUNE) < 1) return false;
        return freeFire || Rs2Inventory.itemQuantity(ItemID.FIRERUNE) >= 4;
    }

    private boolean hasInfiniteFireSource()
    {
        try
        {
            return Rs2Equipment.isWearing(FIRE_RUNE_STAVES);
        }
        catch (RuntimeException ex)
        {
            log.debug("Unable to read equipped fire source: {}", ex.getMessage());
            return false;
        }
    }

    private int inventoryCountByName(String name)
    {
        if (name == null) return 0;
        return Rs2Inventory.all().stream()
            .filter(item -> item != null
                && item.getName() != null
                && item.getName().equalsIgnoreCase(name))
            .mapToInt(item -> Math.max(0, item.getQuantity()))
            .sum();
    }

    private int totalUnsoldProduced()
    {
        int total = 0;
        for (Integer value : unsoldProduced.values())
        {
            if (value != null && value > 0)
            {
                total += value;
            }
        }
        return total;
    }

    private int getRealLevel(Skill skill)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(
            () -> Microbot.getClient().getRealSkillLevel(skill)
        ).orElse(0);
    }

    private int clampInt(long value)
    {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0L, value);
    }

    private String formatNumber(long value)
    {
        return String.format(Locale.ROOT, "%,d", value);
    }

    public SmartSuperheatState getState() { return state; }
    public String getStatus() { return status; }
    public SuperheatRecipe getActiveRecipe() { return activeRecipe; }
    public SuperheatQuote getActiveQuote() { return activeQuote; }
    public boolean hasFreeFireRunes() { return freeFireRunes; }
    public long getBarsMade() { return barsMade; }
    public long getEstimatedProfit() { return estimatedProfit; }
    public long getMagicXp() { return magicXp; }
    public double getSmithingXp() { return smithingXp; }
    public int getCurrentBatchTarget() { return currentBatchTarget; }
    public int getCraftableBarsInBank() { return craftableBarsInBank; }
    public int getSpendableCoins() { return spendableCoins; }
    public int getUnsoldProduced() { return totalUnsoldProduced(); }
    public long getStartTime() { return startTime; }

    public long getBarsPerHour()
    {
        long elapsed = Math.max(1L, System.currentTimeMillis() - startTime);
        return Math.round(barsMade * 3_600_000.0 / elapsed);
    }

    public long getEstimatedProfitPerHour()
    {
        long elapsed = Math.max(1L, System.currentTimeMillis() - startTime);
        return Math.round(estimatedProfit * 3_600_000.0 / elapsed);
    }

    public long getRuntimeMillis()
    {
        return startTime <= 0 ? 0L : Math.max(0L, System.currentTimeMillis() - startTime);
    }
}
