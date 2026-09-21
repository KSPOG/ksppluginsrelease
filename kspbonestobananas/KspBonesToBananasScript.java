package net.runelite.client.plugins.microbot.kspbonestobananas;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeAction;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeRequest;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeSlots;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.magic.Rs2Staff;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class KspBonesToBananasScript extends Script
{
    private static final int LOOP_MS = 650;
    private static final int MAX_GE_RETRIES = 5;

    private KspBonesToBananasConfig config;
    private final BonesToBananasPriceService prices = new BonesToBananasPriceService();
    private final KspBonesToBananasAntiban antiban = new KspBonesToBananasAntiban();
    private final Deque<GeOrder> buyQueue = new ArrayDeque<>();

    private volatile KspBonesToBananasState state = KspBonesToBananasState.STOPPED;
    private volatile String status = "Stopped";
    private volatile BananaBoneType activeBone;
    private volatile BonesToBananasQuote activeQuote;
    private volatile String staffName = "None";
    private volatile boolean freeWater, freeEarth;
    private volatile long startedAt, nextScanAt, casts, bonesConverted, bananasProduced, estimatedProfit, magicXp;
    private volatile int currentBatch, bankedBananas, spendableCoins;
    private int castFailures, buyRetryLevel, sellRetryLevel;
    private GeOrder geOrder;

    public boolean run(KspBonesToBananasConfig config)
    {
        this.config = config;
        state = KspBonesToBananasState.STARTING;
        status = "Starting";
        activeBone = null;
        activeQuote = null;
        staffName = "None";
        freeWater = freeEarth = false;
        startedAt = System.currentTimeMillis();
        nextScanAt = casts = bonesConverted = bananasProduced = estimatedProfit = magicXp = 0L;
        currentBatch = bankedBananas = spendableCoins = castFailures = buyRetryLevel = sellRetryLevel = 0;
        buyQueue.clear();
        geOrder = null;

        if (config.antiban()) antiban.reset(config.antibanProfile());
        else antiban.disable();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try
            {
                if (super.run() && Microbot.isLoggedIn()) tick();
            }
            catch (Exception ex)
            {
                if (Microbot.pauseAllScripts.get())
                {
                    status = "Paused for priority plugin";
                    return;
                }
                state = KspBonesToBananasState.ERROR;
                status = "Error - check log";
                log.error("KSP Bones to Bananas tick failed", ex);
            }
        }, 0, LOOP_MS, TimeUnit.MILLISECONDS);
        return true;
    }

    public void stopScript()
    {
        state = KspBonesToBananasState.STOPPED;
        status = "Stopped";
        buyQueue.clear();
        geOrder = null;
        shutdown();
    }

    private void tick()
    {
        if (config.antiban() && antiban.isPaused()
                && state != KspBonesToBananasState.RESTOCKING
                && state != KspBonesToBananasState.SELLING_OUTPUT)
        {
            status = "Anti-ban: " + antiban.getActivity();
            return;
        }

        switch (state)
        {
            case STARTING: start(); break;
            case SCANNING_MARKET: scanMarket(); break;
            case PREPARING_BATCH: prepareBatch(); break;
            case CASTING: cast(); break;
            case RESTOCKING: restock(); break;
            case SELLING_OUTPUT: sellOutput(); break;
            case WAITING_FOR_PROFIT:
                if (System.currentTimeMillis() >= nextScanAt) state = KspBonesToBananasState.SCANNING_MARKET;
                break;
            default: break;
        }
    }

    private void start()
    {
        if (level(Skill.MAGIC) < 15)
        {
            state = KspBonesToBananasState.ERROR;
            status = "15 Magic required";
            return;
        }
        state = KspBonesToBananasState.SCANNING_MARKET;
        status = "Checking bank, staff and live prices";
    }

    private void scanMarket()
    {
        updateStaff();
        if (!ensureBank()) return;
        refreshBananaStock();

        BonesToBananasQuote bestMarket = null;
        BonesToBananasQuote bestBanked = null;
        boolean members = members();
        String invalidReason = null;

        for (BananaBoneType bone : BananaBoneType.values())
        {
            if (bone.isMembersOnly() && !members) continue;
            BonesToBananasQuote q = prices.quote(bone, config, freeWater, freeEarth, maxBatchSize());
            if (!q.isValid())
            {
                if (invalidReason == null) invalidReason = q.getReason();
                continue;
            }
            if (!q.meets(config)) continue;

            int owned = Math.max(0, Rs2Bank.count(bone.getItemId())) + Math.max(0, Rs2Inventory.itemQuantity(bone.getItemId()));
            int ownedBatch = Math.min(q.getBatchSize(), owned);
            if (ownedBatch > 0
                    && q.meetsForQuantity(config, ownedBatch)
                    && (bestBanked == null || q.getProjectedGpHour() > bestBanked.getProjectedGpHour()))
            {
                bestBanked = q;
            }
            if (bestMarket == null || q.getProjectedGpHour() > bestMarket.getProjectedGpHour())
            {
                bestMarket = q;
            }
        }

        if (bankedBananas >= config.sellThreshold())
        {
            state = KspBonesToBananasState.SELLING_OUTPUT;
            status = "Selling accumulated Bananas";
            return;
        }

        BonesToBananasQuote selected = bestBanked != null ? bestBanked : bestMarket;
        if (selected == null)
        {
            if (bankedBananas > 0)
            {
                state = KspBonesToBananasState.SELLING_OUTPUT;
                status = "Liquidating Bananas while market is unprofitable";
                return;
            }
            activeBone = null;
            activeQuote = null;
            state = KspBonesToBananasState.WAITING_FOR_PROFIT;
            status = invalidReason == null ? "No profitable bone type" : invalidReason;
            nextScanAt = System.currentTimeMillis() + Math.max(15, config.priceRefreshSeconds()) * 1000L;
            return;
        }

        activeQuote = selected;
        activeBone = selected.getBone();
        state = KspBonesToBananasState.PREPARING_BATCH;
        status = (bestBanked != null ? "Using profitable banked " : "Selected profitable ") + activeBone.getItemName();
    }

    private void prepareBatch()
    {
        if (!activeReady()) return;
        updateStaff();
        activeQuote = prices.quote(activeBone, config, freeWater, freeEarth, maxBatchSize());
        if (!activeQuote.meets(config))
        {
            state = KspBonesToBananasState.SCANNING_MARKET;
            status = "Current bone type is no longer profitable";
            return;
        }

        if (!ensureBank() || !bankMode(true)) return;
        cleanInventoryForBatch();
        refreshBananaStock();

        if (bankedBananas >= config.sellThreshold())
        {
            state = KspBonesToBananasState.SELLING_OUTPUT;
            status = "Banana sell threshold reached";
            return;
        }

        int boneBank = Math.max(0, Rs2Bank.count(activeBone.getItemId()));
        if (boneBank <= 0)
        {
            state = bankedBananas > 0 ? KspBonesToBananasState.SELLING_OUTPUT : KspBonesToBananasState.RESTOCKING;
            status = bankedBananas > 0 ? "Selling finished output" : "Restocking profitable inputs";
            return;
        }

        if (!hasRuneSupply(ItemID.NATURERUNE, 1)
                || (!freeWater && !hasRuneSupply(ItemID.WATERRUNE, 2))
                || (!freeEarth && !hasRuneSupply(ItemID.EARTHRUNE, 2)))
        {
            state = KspBonesToBananasState.RESTOCKING;
            status = "Restocking runes";
            return;
        }

        if (!ensureRune(ItemID.NATURERUNE, "Nature rune", 1)) return;
        if (!freeWater && !ensureRune(ItemID.WATERRUNE, "Water rune", 2)) return;
        if (!freeEarth && !ensureRune(ItemID.EARTHRUNE, "Earth rune", 2)) return;

        int batch = Math.min(activeQuote.getBatchSize(), Math.min(boneBank, Rs2Inventory.emptySlotCount()));
        if (batch <= 0)
        {
            status = "No inventory space for bones";
            return;
        }
        if (!activeQuote.meetsForQuantity(config, batch))
        {
            state = KspBonesToBananasState.RESTOCKING;
            status = "Topping up inputs for a profitable cast";
            return;
        }

        currentBatch = batch;
        status = "Withdrawing " + batch + " x " + activeBone.getItemName();
        if (!Rs2Bank.withdrawX(activeBone.getItemId(), batch)
                || !sleepUntil(() -> Rs2Inventory.itemQuantity(activeBone.getItemId()) >= batch, 4500))
        {
            return;
        }

        Rs2Bank.closeBank();
        if (!sleepUntil(() -> !Rs2Bank.isOpen(), 3000)) return;
        castFailures = 0;
        state = KspBonesToBananasState.CASTING;
        status = "Casting Bones to Bananas";
    }

    private void cast()
    {
        if (!activeReady()) return;
        updateStaff();

        if (freeWater != activeQuote.hasFreeWater() || freeEarth != activeQuote.hasFreeEarth())
        {
            state = KspBonesToBananasState.PREPARING_BATCH;
            status = "Equipped staff changed - recalculating";
            return;
        }

        int beforeBones = Rs2Inventory.itemQuantity(activeBone.getItemId());
        int beforeBananas = Rs2Inventory.itemQuantity(ItemID.BANANA);
        if (beforeBones <= 0)
        {
            state = KspBonesToBananasState.PREPARING_BATCH;
            return;
        }
        if (!activeQuote.meetsForQuantity(config, beforeBones))
        {
            state = KspBonesToBananasState.PREPARING_BATCH;
            status = "Cast blocked: batch is no longer profitable";
            return;
        }

        status = "Casting on " + beforeBones + " " + activeBone.getItemName();
        if (!Rs2Magic.cast(Rs2Spells.BONES_TO_BANANAS)
                || !sleepUntil(() -> Rs2Inventory.itemQuantity(activeBone.getItemId()) < beforeBones
                        || Rs2Inventory.itemQuantity(ItemID.BANANA) > beforeBananas, 4000))
        {
            castFailures++;
            status = "Cast did not register (" + castFailures + "/3)";
            if (castFailures >= 3)
            {
                castFailures = 0;
                state = KspBonesToBananasState.PREPARING_BATCH;
            }
            return;
        }

        castFailures = 0;
        int remaining = Rs2Inventory.itemQuantity(activeBone.getItemId());
        int converted = Math.max(1, beforeBones - remaining);
        casts++;
        bonesConverted += converted;
        bananasProduced += converted;
        magicXp += 25L;
        estimatedProfit += activeQuote.profitForBones(converted);

        if (config.antiban()) antiban.afterSuccessfulCast(config.antibanProfile());
        state = KspBonesToBananasState.PREPARING_BATCH;
        status = "Converted " + converted + " bones";
    }

    private void restock()
    {
        if (!activeReady()) return;
        if (geOrder != null || !buyQueue.isEmpty())
        {
            tickBuyQueue();
            return;
        }

        updateStaff();
        activeQuote = prices.quote(activeBone, config, freeWater, freeEarth, maxBatchSize());
        if (!activeQuote.meets(config))
        {
            state = KspBonesToBananasState.SCANNING_MARKET;
            status = "Profit disappeared before restock";
            return;
        }

        if (!ensureBank() || !bankMode(true)) return;
        cleanInventoryForBatch();
        refreshBananaStock();

        int ownedBones = totalSupply(activeBone.getItemId());
        int ownedBatch = Math.min(activeQuote.getBatchSize(), ownedBones);
        boolean ownedBatchProfitable = ownedBatch > 0 && activeQuote.meetsForQuantity(config, ownedBatch);
        boolean runesReady = hasRuneSupply(ItemID.NATURERUNE, 1)
                && (freeWater || hasRuneSupply(ItemID.WATERRUNE, 2))
                && (freeEarth || hasRuneSupply(ItemID.EARTHRUNE, 2));

        if (ownedBatchProfitable && runesReady)
        {
            state = KspBonesToBananasState.PREPARING_BATCH;
            status = "Using existing profitable input stock";
            return;
        }
        if (bankedBananas >= config.sellThreshold())
        {
            state = KspBonesToBananasState.SELLING_OUTPUT;
            status = "Selling Bananas before restock";
            return;
        }

        long coins = Math.max(0, Rs2Bank.count(ItemID.COINS)) + (long) Math.max(0, Rs2Inventory.itemQuantity(ItemID.COINS));
        long spendable = Math.max(0L, coins - config.cashReserve());
        spendableCoins = (int) Math.min(Integer.MAX_VALUE, spendable);
        long budget = spendable * config.maxSpendPercent() / 100L;

        int minimumProfitableBones = minimumProfitableBones(activeQuote);
        if (minimumProfitableBones <= 0)
        {
            state = KspBonesToBananasState.SCANNING_MARKET;
            status = "No profitable cast size at current prices";
            return;
        }

        int minimumTarget = Math.max(ownedBones, minimumProfitableBones);
        long maximumTargetLong = (long) ownedBones + Math.max(1, config.restockBones());
        int maximumTarget = (int) Math.min(Integer.MAX_VALUE, maximumTargetLong);
        int targetBones = affordableRestockTarget(minimumTarget, maximumTarget, budget);

        if (targetBones < minimumTarget)
        {
            state = KspBonesToBananasState.WAITING_FOR_PROFIT;
            status = "Not enough spendable cash to top up a profitable cast";
            nextScanAt = System.currentTimeMillis() + 10_000L;
            return;
        }

        int castsNeeded = (targetBones + activeQuote.getBatchSize() - 1) / activeQuote.getBatchSize();
        buildBuy(activeBone.getItemId(), activeBone.getItemName(), targetBones);
        buildBuy(ItemID.NATURERUNE, "Nature rune", castsNeeded);
        if (!freeWater) buildBuy(ItemID.WATERRUNE, "Water rune", castsNeeded * 2);
        if (!freeEarth) buildBuy(ItemID.EARTHRUNE, "Earth rune", castsNeeded * 2);

        if (Rs2Bank.count(ItemID.COINS) > 0)
        {
            int before = Rs2Inventory.itemQuantity(ItemID.COINS);
            status = "Withdrawing trading cash";
            if (!Rs2Bank.withdrawAll(ItemID.COINS)
                    || !sleepUntil(() -> Rs2Inventory.itemQuantity(ItemID.COINS) > before, 5000)) return;
        }

        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 3000);

        if (buyQueue.isEmpty())
        {
            state = KspBonesToBananasState.PREPARING_BATCH;
            return;
        }

        status = "Buying " + buyQueue.size() + " profitable input(s)";
        tickBuyQueue();
    }

    private void sellOutput()
    {
        if (geOrder != null)
        {
            tickGeOrder();
            return;
        }

        if (!ensureBank()) return;
        if (Rs2Inventory.itemQuantity(ItemID.BANANA) > 0)
        {
            Rs2Bank.depositAll("Banana", true);
            if (!sleepUntil(() -> Rs2Inventory.itemQuantity(ItemID.BANANA) == 0, 3000)) return;
        }

        bankedBananas = Math.max(0, Rs2Bank.count(ItemID.BANANA));
        if (bankedBananas <= 0)
        {
            sellRetryLevel = 0;
            state = KspBonesToBananasState.SCANNING_MARKET;
            return;
        }

        if (!bankMode(false)) return;
        status = "Withdrawing noted Bananas";
        if (!Rs2Bank.withdrawAll("Banana", true)
                || !sleepUntil(() -> Rs2Inventory.itemQuantity("Banana", true) >= bankedBananas, 5000)) return;

        int quantity = Rs2Inventory.itemQuantity("Banana", true);
        Rs2Bank.closeBank();
        if (!sleepUntil(() -> !Rs2Bank.isOpen(), 3000)) return;

        int price = prices.sellOfferPrice(ItemID.BANANA, config.sellDiscountPercent(), sellRetryLevel);
        if (price <= 0)
        {
            status = "No live Banana sell price";
            return;
        }

        geOrder = new GeOrder(GrandExchangeAction.SELL, ItemID.BANANA, "Banana", quantity, price);
        tickGeOrder();
    }

    private void buildBuy(int itemId, String name, int target)
    {
        int owned = Math.max(0, Rs2Bank.count(itemId)) + Math.max(0, Rs2Inventory.itemQuantity(itemId));
        int need = Math.max(0, target - owned);
        if (need <= 0) return;
        int price = prices.buyOfferPrice(itemId, config.buyMarkupPercent(), buyRetryLevel);
        if (price > 0) buyQueue.addLast(new GeOrder(GrandExchangeAction.BUY, itemId, name, need, price));
    }

    private void tickBuyQueue()
    {
        if (geOrder == null) geOrder = buyQueue.peekFirst();
        if (geOrder == null)
        {
            buyRetryLevel = 0;
            state = KspBonesToBananasState.PREPARING_BATCH;
            status = "Restock complete";
            return;
        }
        tickGeOrder();
    }

    private void tickGeOrder()
    {
        GeOrder order = geOrder;
        if (order == null || !ensureGeOverview()) return;

        if (order.abortRequested)
        {
            if (!orderSlotCleared(order))
            {
                status = "Waiting for aborted offer collection: " + order.itemName;
                return;
            }
            replanAfterAbort(order);
            return;
        }

        if (!order.placed)
        {
            placeOrder(order);
            return;
        }

        if (order.slot == null)
        {
            order.slot = findOfferSlot(order);
            if (order.slot == null)
            {
                status = "Waiting for GE offer confirmation: " + order.itemName;
                return;
            }
        }

        OfferSnapshot offer = offer(order.slot);
        if (!matchesOrder(offer, order))
        {
            GrandExchangeSlots recovered = findOfferSlot(order);
            if (recovered == null)
            {
                status = "Waiting for tracked GE offer: " + order.itemName;
                return;
            }
            order.slot = recovered;
            offer = offer(recovered);
        }

        boolean complete = offer != null
                && offer.filled >= order.quantity
                && offer.total == order.quantity
                && ((order.action == GrandExchangeAction.BUY && offer.state == GrandExchangeOfferState.BOUGHT)
                || (order.action == GrandExchangeAction.SELL && offer.state == GrandExchangeOfferState.SOLD));

        if (complete)
        {
            status = (order.action == GrandExchangeAction.BUY ? "Bought " : "Sold ")
                    + order.quantity + " x " + order.itemName + " - collecting";
            if (!collectCompletedOrder(order)) return;
            finishOrder(order);
            return;
        }

        int filled = offer == null ? 0 : Math.max(0, offer.filled);
        status = (order.action == GrandExchangeAction.BUY ? "Buying " : "Selling ")
                + order.itemName + " (" + filled + "/" + order.quantity + ")";
        if (order.placedAt > 0
                && System.currentTimeMillis() - order.placedAt >= config.geOfferTimeoutSeconds() * 1000L)
        {
            retryStalledOrder(order);
        }
    }

    private void placeOrder(GeOrder order)
    {
        GrandExchangeRequest request;
        if (order.action == GrandExchangeAction.BUY)
        {
            order.slot = firstFreeGeSlot();
            if (order.slot == null)
            {
                status = "Waiting for a free GE slot";
                return;
            }
            request = GrandExchangeRequest.builder()
                    .slot(order.slot)
                    .action(order.action)
                    .itemName(order.itemName)
                    .exact(true)
                    .quantity(order.quantity)
                    .price(order.price)
                    .closeAfterCompletion(false)
                    .build();
        }
        else
        {
            // SELL ignores request.slot in Microbot, but remembering the first free slot
            // gives us a strong expected-slot hint before falling back to exact matching.
            order.slot = firstFreeGeSlot();
            if (order.slot == null)
            {
                status = "Waiting for a free GE slot";
                return;
            }
            request = GrandExchangeRequest.builder()
                    .action(order.action)
                    .itemName(order.itemName)
                    .exact(true)
                    .quantity(order.quantity)
                    .price(order.price)
                    .closeAfterCompletion(false)
                    .build();
        }

        status = (order.action == GrandExchangeAction.BUY ? "Placing buy: " : "Placing sell: ") + order.itemName;
        boolean placed;
        try
        {
            placed = Rs2GrandExchange.processOffer(request);
        }
        catch (RuntimeException ex)
        {
            log.warn("GE offer placement failed for {}: {}", order.itemName, ex.getMessage());
            placed = false;
        }

        if (!placed)
        {
            GrandExchangeSlots recovered = order.slot != null && matchesOrder(offer(order.slot), order)
                    ? order.slot
                    : findOfferSlot(order);
            if (recovered != null)
            {
                order.slot = recovered;
                order.placed = true;
                order.placedAt = System.currentTimeMillis();
                status = "GE offer confirmed after UI recovery: " + order.itemName;
                return;
            }

            order.slot = null;
            if (Rs2GrandExchange.isOfferScreenOpen()) Rs2GrandExchange.backToOverview();
            status = "GE placement failed - retrying " + order.itemName;
            return;
        }

        order.placed = true;
        order.placedAt = System.currentTimeMillis();
        if (order.slot == null)
        {
            sleepUntil(() -> findOfferSlot(order) != null || !Rs2GrandExchange.isOpen(), 3000);
            order.slot = findOfferSlot(order);
        }
        status = order.slot == null
                ? "Waiting for GE offer confirmation: " + order.itemName
                : "GE offer active: " + order.itemName;
    }

    private void retryStalledOrder(GeOrder order)
    {
        if (order == null || order.abortRequested) return;
        status = "Repricing stalled " + order.itemName;
        if (!Rs2GrandExchange.abortOffer(order.itemName, true)) return;

        // Microbot's abortOffer(..., true) only initiates Collect-to-bank. Do not
        // replan against stale bank quantities until the tracked GE slot is cleared.
        order.abortRequested = true;
        status = "Waiting for aborted offer collection: " + order.itemName;
    }

    private void replanAfterAbort(GeOrder order)
    {
        if (order.action == GrandExchangeAction.BUY)
        {
            buyRetryLevel = Math.min(MAX_GE_RETRIES, buyRetryLevel + 1);
            buyQueue.clear();
            geOrder = null;
            state = KspBonesToBananasState.RESTOCKING;
            status = "Partial buy reconciled - rebuilding input orders";
        }
        else
        {
            sellRetryLevel = Math.min(MAX_GE_RETRIES, sellRetryLevel + 1);
            geOrder = null;
            state = KspBonesToBananasState.SELLING_OUTPUT;
            status = "Partial sale reconciled - rebuilding Banana offer";
        }
    }

    private void finishOrder(GeOrder order)
    {
        if (order.action == GrandExchangeAction.BUY)
        {
            if (buyQueue.peekFirst() == order) buyQueue.pollFirst();
            geOrder = null;
            if (buyQueue.isEmpty())
            {
                buyRetryLevel = 0;
                state = KspBonesToBananasState.PREPARING_BATCH;
                status = "Inputs bought";
            }
        }
        else
        {
            geOrder = null;
            sellRetryLevel = 0;
            bankedBananas = 0;
            state = KspBonesToBananasState.SCANNING_MARKET;
            status = "Bananas sold";
        }
    }

    private GrandExchangeSlots findOfferSlot(GeOrder order)
    {
        if (order == null) return null;
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            if (offers == null) return null;
            int max = Math.min(offers.length, GrandExchangeSlots.values().length);
            for (int i = 0; i < max; i++)
            {
                GrandExchangeOffer ge = offers[i];
                if (ge == null || ge.getItemId() != order.itemId) continue;
                GrandExchangeOfferState s = ge.getState();
                boolean correctSide = order.action == GrandExchangeAction.BUY
                        ? s == GrandExchangeOfferState.BUYING || s == GrandExchangeOfferState.BOUGHT
                        : s == GrandExchangeOfferState.SELLING || s == GrandExchangeOfferState.SOLD;
                if (!correctSide) continue;
                if (ge.getTotalQuantity() != order.quantity || ge.getPrice() != order.price) continue;
                return GrandExchangeSlots.values()[i];
            }
            return null;
        }).orElse(null);
    }

    private OfferSnapshot offer(GrandExchangeSlots slot)
    {
        if (slot == null) return null;
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            int index = slot.ordinal();
            if (offers == null || index >= offers.length || offers[index] == null)
                return new OfferSnapshot(0, GrandExchangeOfferState.EMPTY, 0, 0, 0);
            GrandExchangeOffer ge = offers[index];
            return new OfferSnapshot(ge.getItemId(), ge.getState(), ge.getQuantitySold(),
                    ge.getTotalQuantity(), ge.getPrice());
        }).orElse(null);
    }

    private boolean matchesOrder(OfferSnapshot offer, GeOrder order)
    {
        if (offer == null || order == null) return false;
        boolean correctSide = order.action == GrandExchangeAction.BUY
                ? offer.state == GrandExchangeOfferState.BUYING || offer.state == GrandExchangeOfferState.BOUGHT
                : offer.state == GrandExchangeOfferState.SELLING || offer.state == GrandExchangeOfferState.SOLD;
        return correctSide
                && offer.itemId == order.itemId
                && offer.total == order.quantity
                && offer.price == order.price;
    }

    private GrandExchangeSlots firstFreeGeSlot()
    {
        GrandExchangeSlots[] slots = Rs2GrandExchange.getAvailableSlots();
        return slots == null || slots.length == 0 ? null : slots[0];
    }

    private boolean collectCompletedOrder(GeOrder order)
    {
        if (order == null || order.slot == null) return false;
        if (!Rs2GrandExchange.collectAllToBank()) return false;
        return sleepUntil(() -> orderSlotCleared(order), 5000);
    }

    private boolean orderSlotCleared(GeOrder order)
    {
        if (order == null || order.slot == null) return true;
        OfferSnapshot current = offer(order.slot);
        return current == null
                || current.state == GrandExchangeOfferState.EMPTY
                || current.itemId != order.itemId;
    }

    private boolean ensureGeOverview()
    {
        if (!Rs2GrandExchange.isOpen())
        {
            status = "Opening Grand Exchange";
            if (!Rs2GrandExchange.openExchange())
            {
                status = "Walking to Grand Exchange";
                Rs2GrandExchange.walkToGrandExchange();
                return false;
            }
        }
        if (!Rs2GrandExchange.isOfferScreenOpen()) return true;
        Rs2GrandExchange.backToOverview();
        return sleepUntil(() -> Rs2GrandExchange.isOpen() && !Rs2GrandExchange.isOfferScreenOpen(), 3500);
    }

    private boolean ensureBank()
    {
        if (Rs2Bank.isOpen()) return true;
        if (Rs2GrandExchange.isOpen())
        {
            Rs2GrandExchange.closeExchange();
            return false;
        }

        status = "Opening bank";
        if (Rs2Bank.openBank()) return sleepUntil(Rs2Bank::isOpen, 5000);

        status = "Walking to Grand Exchange bank";
        Rs2GrandExchange.walkToGrandExchange();
        return false;
    }

    private boolean bankMode(boolean itemMode)
    {
        if (!Rs2Bank.isOpen()) return false;
        if (Rs2Bank.hasWithdrawAsItem() == itemMode) return true;
        status = itemMode ? "Setting item withdrawal" : "Setting noted withdrawal";
        boolean changed = itemMode ? Rs2Bank.setWithdrawAsItem() : Rs2Bank.setWithdrawAsNote();
        return changed && sleepUntil(() -> Rs2Bank.hasWithdrawAsItem() == itemMode, 2500);
    }

    private void cleanInventoryForBatch()
    {
        String[] keep;
        if (freeWater && freeEarth) keep = new String[]{"Nature rune"};
        else if (freeWater) keep = new String[]{"Nature rune", "Earth rune"};
        else if (freeEarth) keep = new String[]{"Nature rune", "Water rune"};
        else keep = new String[]{"Nature rune", "Water rune", "Earth rune"};

        Rs2Bank.depositAllExcept(true, keep);
        sleep(180, 300);
    }

    private boolean hasRuneSupply(int id, int needed)
    {
        return totalSupply(id) >= needed;
    }

    private int totalSupply(int id)
    {
        long total = Math.max(0, Rs2Inventory.itemQuantity(id)) + (long) Math.max(0, Rs2Bank.count(id));
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    private int minimumProfitableBones(BonesToBananasQuote quote)
    {
        if (quote == null || !quote.isValid()) return -1;
        for (int bones = 1; bones <= quote.getBatchSize(); bones++)
        {
            if (quote.meetsForQuantity(config, bones)) return bones;
        }
        return -1;
    }

    private int affordableRestockTarget(int minimumTarget, int maximumTarget, long budget)
    {
        if (minimumTarget <= 0 || maximumTarget < minimumTarget || budget < 0) return -1;
        if (estimatedRestockCost(minimumTarget) > budget) return -1;

        int low = minimumTarget;
        int high = maximumTarget;
        int best = minimumTarget;
        while (low <= high)
        {
            int mid = low + (high - low) / 2;
            long cost = estimatedRestockCost(mid);
            if (cost <= budget)
            {
                best = mid;
                low = mid + 1;
            }
            else
            {
                high = mid - 1;
            }
        }
        return best;
    }

    private long estimatedRestockCost(int targetBones)
    {
        if (activeQuote == null || activeBone == null || targetBones <= 0) return Long.MAX_VALUE;

        int castsNeeded = (targetBones + activeQuote.getBatchSize() - 1) / activeQuote.getBatchSize();
        long cost = (long) Math.max(0, targetBones - totalSupply(activeBone.getItemId()))
                * Math.max(0, activeQuote.getBoneBuyPrice());

        cost += (long) Math.max(0, castsNeeded - totalSupply(ItemID.NATURERUNE))
                * Math.max(0, activeQuote.getNatureBuyPrice());

        if (!freeWater)
        {
            cost += (long) Math.max(0, castsNeeded * 2 - totalSupply(ItemID.WATERRUNE))
                    * Math.max(0, activeQuote.getWaterBuyPrice());
        }
        if (!freeEarth)
        {
            cost += (long) Math.max(0, castsNeeded * 2 - totalSupply(ItemID.EARTHRUNE))
                    * Math.max(0, activeQuote.getEarthBuyPrice());
        }
        return cost;
    }

    private boolean ensureRune(int id, String name, int needed)
    {
        if (Rs2Inventory.itemQuantity(id) >= needed) return true;
        if (Rs2Bank.count(id) <= 0) return false;
        status = "Withdrawing " + name + " stack";
        return Rs2Bank.withdrawAll(id)
                && sleepUntil(() -> Rs2Inventory.itemQuantity(id) >= needed, 4500);
    }

    private void refreshBananaStock()
    {
        if (!Rs2Bank.isOpen()) return;
        bankedBananas = Math.max(0, Rs2Bank.count(ItemID.BANANA))
                + Math.max(0, Rs2Inventory.itemQuantity(ItemID.BANANA));
    }

    private void updateStaff()
    {
        try
        {
            Rs2ItemModel weapon = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
            Rs2Staff staff = weapon == null ? Rs2Staff.NONE : Rs2Staff.byItemId(weapon.getId());
            freeWater = staff != Rs2Staff.NONE && staff.provides(Runes.WATER);
            freeEarth = staff != Rs2Staff.NONE && staff.provides(Runes.EARTH);
            staffName = staff == Rs2Staff.NONE ? "None / no rune savings" : prettyStaff(staff);
        }
        catch (RuntimeException ex)
        {
            freeWater = freeEarth = false;
            staffName = "Unknown";
        }
    }

    private int maxBatchSize()
    {
        int runeSlots = 1; // Nature rune is always budgeted/required.
        if (!freeWater) runeSlots++;
        if (!freeEarth) runeSlots++;
        return Math.max(1, 28 - runeSlots);
    }

    private static String prettyStaff(Rs2Staff staff)
    {
        String raw = staff.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder out = new StringBuilder();
        boolean upper = true;
        for (int i = 0; i < raw.length(); i++)
        {
            char c = raw.charAt(i);
            out.append(upper && Character.isLetter(c) ? Character.toUpperCase(c) : c);
            upper = c == ' ';
        }
        return out.toString();
    }

    private boolean activeReady()
    {
        if (activeBone != null && activeQuote != null) return true;
        state = KspBonesToBananasState.SCANNING_MARKET;
        return false;
    }

    private int level(Skill skill)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(
                () -> Microbot.getClient().getRealSkillLevel(skill)).orElse(0);
    }

    private boolean members()
    {
        try { return Rs2Player.isMember() && Rs2Player.isInMemberWorld(); }
        catch (RuntimeException ex) { return false; }
    }

    public KspBonesToBananasState getState() { return state; }
    public String getStatus() { return status; }
    public BananaBoneType getActiveBone() { return activeBone; }
    public BonesToBananasQuote getActiveQuote() { return activeQuote; }
    public String getStaffName() { return staffName; }
    public boolean hasFreeWater() { return freeWater; }
    public boolean hasFreeEarth() { return freeEarth; }
    public int getCurrentBatch() { return currentBatch; }
    public int getBankedBananas() { return bankedBananas; }
    public int getSpendableCoins() { return spendableCoins; }
    public long getCasts() { return casts; }
    public long getBonesConverted() { return bonesConverted; }
    public long getBananasProduced() { return bananasProduced; }
    public long getEstimatedProfit() { return estimatedProfit; }
    public long getMagicXp() { return magicXp; }
    public long getRuntimeMillis() { return startedAt <= 0 ? 0 : System.currentTimeMillis() - startedAt; }
    public long getCastsPerHour() { long r = getRuntimeMillis(); return r <= 0 ? 0 : casts * 3_600_000L / r; }
    public long getBonesPerHour() { long r = getRuntimeMillis(); return r <= 0 ? 0 : bonesConverted * 3_600_000L / r; }
    public long getEstimatedProfitPerHour() { long r = getRuntimeMillis(); return r <= 0 ? 0 : estimatedProfit * 3_600_000L / r; }
    public String getAntibanActivity() { return config != null && config.antiban() ? antiban.getActivity() : "Off"; }
    public int getAntibanShortPauses() { return antiban.getShortPauses(); }
    public int getAntibanLongBreaks() { return antiban.getLongBreaks(); }

    private static final class GeOrder
    {
        final GrandExchangeAction action;
        final int itemId, quantity, price;
        final String itemName;
        GrandExchangeSlots slot;
        boolean placed, abortRequested;
        long placedAt;

        GeOrder(GrandExchangeAction action, int itemId, String itemName, int quantity, int price)
        {
            this.action = action;
            this.itemId = itemId;
            this.itemName = itemName;
            this.quantity = quantity;
            this.price = price;
        }
    }

    private static final class OfferSnapshot
    {
        final int itemId, filled, total, price;
        final GrandExchangeOfferState state;

        OfferSnapshot(int itemId, GrandExchangeOfferState state, int filled, int total, int price)
        {
            this.itemId = itemId;
            this.state = state;
            this.filled = filled;
            this.total = total;
            this.price = price;
        }
    }
}
