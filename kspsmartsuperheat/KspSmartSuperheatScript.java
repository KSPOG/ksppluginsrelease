package net.runelite.client.plugins.microbot.kspsmartsuperheat;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
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
    private static final int INVENTORY_TIMEOUT_MS = 4_000;
    private static final int CAST_RESULT_TIMEOUT_MS = 4_500;
    private static final int MAX_CAST_FAILURES = 3;

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
        int magic = getRealLevel(Skill.MAGIC);
        if (magic < 43)
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

        if (!Rs2Bank.setWithdrawAsItem())
        {
            status = "Restoring bank item mode";
            return;
        }

        normalizeInventoryForBank();
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
            if (Rs2Inventory.count(ItemID.NATURE_RUNE) > 0) occupiedWorkStacks++;
            if (!freeFireRunes && Rs2Inventory.count(ItemID.FIRE_RUNE) > 0) occupiedWorkStacks++;
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
        withdrawBatch(activeRecipe, maxBatch, freeFireRunes);

        if (!inventoryHasCastSupplies(activeRecipe, freeFireRunes))
        {
            status = "Waiting for batch withdrawal";
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
        int primaryBefore = Rs2Inventory.count(activeRecipe.getPrimaryOreId());

        status = "Casting on " + activeRecipe.getPrimaryOreName();
        Rs2Magic.superHeat(activeRecipe.getPrimaryOreId(), config.castDelayMinMs(), config.castDelayMaxMs());

        boolean success = sleepUntil(() ->
            inventoryCountByName(activeRecipe.getOutputName()) > outputBefore
                || Rs2Inventory.count(activeRecipe.getPrimaryOreId()) < primaryBefore,
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

        if (!Rs2Bank.setWithdrawAsItem())
        {
            status = "Restoring bank item mode";
            return;
        }

        normalizeInventoryForBank();
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

        long coinTotal = (long) Rs2Bank.count(ItemID.COINS) + Rs2Inventory.count(ItemID.COINS);
        long afterReserve = Math.max(0L, coinTotal - config.cashReserve());
        long budget = afterReserve * config.maxSpendPercent() / 100L;
        spendableCoins = clampInt(budget);

        int perBar = Math.max(1, activeQuote.getInputCostPerBar());
        int affordableBars = (int) Math.min(Integer.MAX_VALUE, budget / perBar);
        int targetBars = Math.min(config.restockTargetBars(), affordableBars);

        if (targetBars <= 0)
        {
            state = SmartSuperheatState.WAITING_FOR_PROFIT;
            nextNoProfitScanAt = System.currentTimeMillis() + config.priceRefreshSeconds() * 1000L;
            status = "Not enough spendable cash";
            return;
        }

        if (!buyMissing(
            activeRecipe.getPrimaryOreName(),
            activeRecipe.getPrimaryOreId(),
            targetBars * activeRecipe.getPrimaryOrePerBar(),
            activeQuote.getPrimaryBuyPrice()))
        {
            return;
        }

        if (activeRecipe.hasSecondaryOre()
            && !buyMissing(
                activeRecipe.getSecondaryOreName(),
                activeRecipe.getSecondaryOreId(),
                targetBars * activeRecipe.getSecondaryOrePerBar(),
                activeQuote.getSecondaryBuyPrice()))
        {
            return;
        }

        if (activeRecipe.getCoalPerBar() > 0
            && !buyMissing(
                "Coal",
                ItemID.COAL,
                targetBars * activeRecipe.getCoalPerBar(),
                activeQuote.getCoalBuyPrice()))
        {
            return;
        }

        if (!buyMissing("Nature rune", ItemID.NATURE_RUNE, targetBars, activeQuote.getNatureBuyPrice()))
        {
            return;
        }

        if (!freeFireRunes
            && !buyMissing("Fire rune", ItemID.FIRE_RUNE, targetBars * 4, activeQuote.getFireBuyPrice()))
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
            state = SmartSuperheatState.WAITING_FOR_PROFIT;
            nextNoProfitScanAt = System.currentTimeMillis() + config.priceRefreshSeconds() * 1000L;
            status = "Restock did not yield a complete recipe";
        }
    }

    private boolean buyMissing(String itemName, int itemId, int desiredTotal, int offerPrice)
    {
        if (!ensureBankOpen())
        {
            return false;
        }

        int banked = Rs2Bank.count(itemId);
        int missing = Math.max(0, desiredTotal - banked);
        if (missing <= 0)
        {
            return true;
        }

        long requiredCoinsLong = (long) missing * offerPrice;
        int requiredCoins = clampInt(requiredCoinsLong);
        if (requiredCoins <= 0)
        {
            status = "Invalid purchase cost for " + itemName;
            return false;
        }

        if (!ensureCoinsInInventory(requiredCoins))
        {
            status = "Insufficient coins for " + itemName;
            state = SmartSuperheatState.WAITING_FOR_PROFIT;
            nextNoProfitScanAt = System.currentTimeMillis() + config.priceRefreshSeconds() * 1000L;
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

        if (result.getFilledQuantity() <= 0)
        {
            status = "No fill for " + itemName;
            return false;
        }

        status = "Bought " + formatNumber(result.getFilledQuantity()) + " " + itemName;
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

        // Keep the selected sale recipe effectively final for the inventory-change
        // lambda below and make the whole sale attempt operate on one stable recipe.
        final SuperheatRecipe saleRecipe = recipeToSell;

        if (!ensureBankOpen())
        {
            status = "Opening bank for sale";
            return;
        }

        normalizeInventoryForBank();

        captureProtectedOutputBaseline(saleRecipe);
        int banked = Rs2Bank.count(saleRecipe.getOutputId());
        int protectedAmount = protectedOutputBaseline.getOrDefault(saleRecipe, banked);
        int sessionOwnedInBank = Math.max(0, banked - protectedAmount);
        int toSell = Math.min(trackedAmount, sessionOwnedInBank);
        if (toSell <= 0)
        {
            // If tracking says bars remain but the bank is back at/below the
            // protected pre-session baseline, prefer leaving bars untouched.
            unsoldProduced.put(saleRecipe, 0);
            return;
        }

        SuperheatQuote saleQuote = priceService.quote(saleRecipe, config, hasInfiniteFireSource());
        if (!saleQuote.isValid() || saleQuote.getOutputSellPrice() <= 0)
        {
            status = "No current sell price for " + saleRecipe.getOutputName();
            return;
        }

        if (!Rs2Bank.setWithdrawAsNote())
        {
            status = "Switching bank to note mode";
            return;
        }

        Rs2Bank.withdrawX(saleRecipe.getOutputId(), toSell);
        sleepUntil(() -> inventoryCountByName(saleRecipe.getOutputName()) > 0, INVENTORY_TIMEOUT_MS);

        int inventoryQty = inventoryCountByName(saleRecipe.getOutputName());
        if (inventoryQty <= 0)
        {
            status = "Could not withdraw " + saleRecipe.getOutputName();
            return;
        }

        Rs2Bank.closeBank();
        status = "Selling " + formatNumber(inventoryQty) + " " + saleRecipe.getOutputName();

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
            sleep(150, 300);
        }

        if (Rs2Bank.isOpen())
        {
            return true;
        }

        if (Rs2Bank.openBank())
        {
            return sleepUntil(Rs2Bank::isOpen, BANK_TIMEOUT_MS);
        }

        Rs2GrandExchange.walkToGrandExchange();
        sleepUntil(() -> !Rs2Player.isMoving(), 20_000);

        if (!Rs2Bank.openBank())
        {
            return false;
        }

        return sleepUntil(Rs2Bank::isOpen, BANK_TIMEOUT_MS);
    }

    private void normalizeInventoryForBank()
    {
        if (!Rs2Bank.isOpen())
        {
            return;
        }

        if (config.bankWholeInventory())
        {
            if (!Rs2Inventory.isEmpty())
            {
                Rs2Bank.depositAll();
                sleepUntil(Rs2Inventory::isEmpty, INVENTORY_TIMEOUT_MS);
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
        depositIfPresent(ItemID.NATURE_RUNE);
        depositIfPresent(ItemID.FIRE_RUNE);
        depositIfPresent(ItemID.COINS);
    }

    private void depositIfPresent(int itemId)
    {
        if (itemId > 0 && Rs2Inventory.count(itemId) > 0)
        {
            Rs2Bank.depositAll(itemId);
            sleep(100, 180);
        }
    }

    private void withdrawBatch(SuperheatRecipe recipe, int bars, boolean freeFire)
    {
        if (bars <= 0) return;

        Rs2Bank.withdrawX(recipe.getPrimaryOreId(), bars * recipe.getPrimaryOrePerBar());
        sleep(120, 220);

        if (recipe.hasSecondaryOre())
        {
            Rs2Bank.withdrawX(recipe.getSecondaryOreId(), bars * recipe.getSecondaryOrePerBar());
            sleep(120, 220);
        }

        if (recipe.getCoalPerBar() > 0)
        {
            Rs2Bank.withdrawX(ItemID.COAL, bars * recipe.getCoalPerBar());
            sleep(120, 220);
        }

        Rs2Bank.withdrawX(ItemID.NATURE_RUNE, bars);
        sleep(120, 220);

        if (!freeFire)
        {
            Rs2Bank.withdrawX(ItemID.FIRE_RUNE, bars * 4);
            sleep(120, 220);
        }

        sleepUntil(() -> inventoryHasCastSupplies(recipe, freeFire), INVENTORY_TIMEOUT_MS);
    }

    private boolean ensureCoinsInInventory(int required)
    {
        if (!Rs2Bank.isOpen())
        {
            return false;
        }

        int inventoryCoins = Rs2Inventory.count(ItemID.COINS);
        if (inventoryCoins >= required)
        {
            return true;
        }

        int missing = required - inventoryCoins;
        int bankCoins = Rs2Bank.count(ItemID.COINS);
        if (bankCoins < missing)
        {
            return false;
        }

        Rs2Bank.setWithdrawAsItem();
        Rs2Bank.withdrawX(ItemID.COINS, missing);
        return sleepUntil(() -> Rs2Inventory.count(ItemID.COINS) >= required, INVENTORY_TIMEOUT_MS);
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

        bars = Math.min(bars, Rs2Bank.count(ItemID.NATURE_RUNE));

        if (!freeFire)
        {
            bars = Math.min(bars, Rs2Bank.count(ItemID.FIRE_RUNE) / 4);
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
        if (Rs2Inventory.count(recipe.getPrimaryOreId()) < recipe.getPrimaryOrePerBar()) return false;
        if (recipe.hasSecondaryOre()
            && Rs2Inventory.count(recipe.getSecondaryOreId()) < recipe.getSecondaryOrePerBar()) return false;
        if (recipe.getCoalPerBar() > 0
            && Rs2Inventory.count(ItemID.COAL) < recipe.getCoalPerBar()) return false;
        if (Rs2Inventory.count(ItemID.NATURE_RUNE) < 1) return false;
        return freeFire || Rs2Inventory.count(ItemID.FIRE_RUNE) >= 4;
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
