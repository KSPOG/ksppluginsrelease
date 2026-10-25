package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.sellscript;

import net.runelite.client.plugins.microbot.kspaccountbuilder.KspAccountBuilderPauseGate;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspManagedScript;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;

import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspAccountPlayTimeCache;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspAccountBuilderConfig;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspTrainSingleSkillTask;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspTaskDebug;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspWalkerGuard;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspWorldMapGuard;
import net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.KspBankWidgetHelper;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.cooksassistant.reqs.Items;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.goblindip.reqs.GobReqs;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.buyscript.Buy;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.combat.melee.equipment.armour.Armour;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.combat.melee.equipment.weapon.Weapons;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.buyscript.BuyScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.gearea.GEArea;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.GeOfferRepricePolicy;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.sell.SellList;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeAction;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeRequest;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeSlots;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class SellScript extends KspManagedScript
{
    private static final Logger log = LoggerFactory.getLogger(SellScript.class);
    private static final int LOOP_DELAY_MS = 100;
    private static final long BANK_ACTION_TIMEOUT_NS = TimeUnit.SECONDS.toNanos(3);
    private static final long GE_ACTION_TIMEOUT_NS = TimeUnit.SECONDS.toNanos(5);
    private static final long BANK_EXCEPTION_BACKOFF_NS = TimeUnit.MILLISECONDS.toNanos(500);
    private static final int TRADE_RESTRICTION_CACHE_MS = 10_000;
    private static final int TRADE_RESTRICTION_MIN_TOTAL_LEVEL = 100;
    private static final int TRADE_RESTRICTION_MIN_QUEST_POINTS = 10;
    private static final int TRADE_RESTRICTION_MIN_HOURS_PLAYED = 20;
    private static final long BUY_AFFORDABILITY_CACHE_MS = 3_000L;

    private static final String WALK_KEY = "GE Sell:target-area";
    private static final String[] PICKAXE_NAMES = Buy.PICKAXE_NAMES;
    private static final String[] AXE_NAMES = Buy.AXE_NAMES;
    private static final SellList[] SELL_ENTRIES = SellList.values();
    private static final Skill[] SKILLS = Skill.values();

    private static final Set<SellList> PROTECTED_SKILL_RESOURCES = EnumSet.of(
            SellList.LOGS, SellList.OAK_LOGS, SellList.YEW_LOGS,
            SellList.COWHIDE, SellList.RAW_CHICKEN,
            SellList.SMALL_FISHING_NET, SellList.FISHING_ROD, SellList.FISHING_BAIT,
            SellList.SILVER_ORE, SellList.SILVER_BAR,
            SellList.LIMPWURT_ROOT, SellList.EARTH_TALISMAN, SellList.BODY_TALISMAN,
            SellList.TUNA, SellList.LOBSTER, SellList.SWORDFISH, SellList.SPINACH_ROLL);

    private enum BankAction { NONE, OPEN, SET_NOTE, DEPOSIT, WITHDRAW, CLOSE }

    @Inject private KspAccountPlayTimeCache accountPlayTimeCache;
    @Inject private BuyScript buyScript;
    @Inject private KspAccountBuilderConfig config;

    private final Set<String> blockedSellItems = new HashSet<>();
    // Avoid infinite bank loops if a particular item's withdrawal cannot be
    // confirmed. Other eligible items should still be processed.
    private final Map<String, Integer> failedBankWithdrawals = new HashMap<>();
    private final Set<String> skippedBankWithdrawals = new HashSet<>();
    private static final int MAX_WITHDRAW_FAILURES = 3;
    private Set<String> outdatedMeleeGearCache;

    private GEArea targetArea = GEArea.GRAND_EXCHANGE;
    private SellState state = SellState.GOING_TO_GE;
    private boolean debugLogging;
    private boolean complete;
    private boolean bankInventoryPrepared;
    private boolean completeAfterBankClose;

    private BankAction bankAction = BankAction.NONE;
    private long bankActionStartedNs;
    private int bankActionEmptySlots;
    private String pendingWithdrawName;
    private int pendingWithdrawBankBefore;
    private int pendingWithdrawInventoryBefore;
    private long nextBankOpenAttemptNs;

    private boolean geOpenRequested;
    private long geOpenRequestedNs;
    private boolean geOverviewRequested;
    private long geOverviewRequestedNs;

    private int pendingOfferItemId;
    private String pendingOfferItemName;
    private long pendingOfferStartedNs;
    private final GeOfferRepricePolicy sellRepricing = new GeOfferRepricePolicy();
    private GrandExchangeSlots repricingSlot;
    private int repricingItemId;
    private long repricingCancelAtMs;
    private int repricingCancelAttempts;

    private Boolean tradeRestrictionUnlockedCache;
    private long lastTradeRestrictionCheckAtMs;
    private long lastBuyAffordabilityCheckAtMs;
    private boolean cachedBuyAffordability;

    public void setDebugLogging(boolean debugLogging) { this.debugLogging = debugLogging; }

    public boolean run(GEArea area)
    {
        shutdown();
        targetArea = area == null ? GEArea.GRAND_EXCHANGE : area;
        state = SellState.GOING_TO_GE;
        complete = false;
        Microbot.status = "Walking to GE";

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            try
            {
                if (KspAccountBuilderPauseGate.isPaused() || !super.run() || !Microbot.isLoggedIn() || complete) return;
                tick();
            }
            catch (RuntimeException ex)
            {
                log.warn("KSP Account Builder SellScript tick failed", ex);
                recoverGeState("tick exception: " + ex.getMessage());
            }
        }, 0L, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);
        return true;
    }

    private void tick()
    {
        if (repricingSlot != null)
        {
            if (!ensureGeOverview()) return;
            handleCancelledSellOffer();
            return;
        }
        // Gear ranking is shared by bank checks, inventory checks and the
        // SellList filter; only compute it once per scheduler tick.
        outdatedMeleeGearCache = null;
        if (!KspWorldMapGuard.isSceneReady())
        {
            Microbot.status = "Waiting for game scene";
            return;
        }

        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null)
        {
            Microbot.status = "Waiting for game scene";
            return;
        }

        if (!targetArea.toWorldArea().contains(player))
        {
            state = SellState.GOING_TO_GE;
            walkToGe();
            return;
        }

        KspWalkerGuard.clear(WALK_KEY);

        // Once a bank batch starts, keep ownership of the state until every
        // eligible item that fits has been withdrawn and the bank is closed.
        if (Rs2Bank.isOpen() || bankAction != BankAction.NONE || bankInventoryPrepared)
        {
            state = SellState.RESTOCKING_FROM_BANK;
        }
        else if (pendingOfferItemId > 0 || geOpenRequested || geOverviewRequested
                || hasSellableInventoryItems() || hasCompletedSellOffers() || hasOpenSellOffers())
        {
            state = SellState.SELLING_ITEMS;
        }
        else
        {
            state = SellState.RESTOCKING_FROM_BANK;
        }

        if (state == SellState.RESTOCKING_FROM_BANK) bankSellItems();
        else sellInventory();
    }

    private void walkToGe()
    {
        Microbot.status = "Walking to GE";
        KspWalkerGuard.walkToDestination(WALK_KEY, targetArea::getRandomPoint,
                targetArea.toWorldArea()::contains, 2, 0L);
    }

    private void bankSellItems()
    {
        if (!KspWorldMapGuard.isSceneReady()) return;

        if (Rs2GrandExchange.isOpen())
        {
            Rs2GrandExchange.closeExchange();
            return;
        }

        if (processPendingBankAction()) return;

        if (!Rs2Bank.isOpen())
        {
            Microbot.status = "Opening GE Bank";
            if (System.nanoTime() < nextBankOpenAttemptNs) return;
            try
            {
                boolean sent = Rs2Bank.openBank();
                if (!sent && !Rs2Player.isMoving()) sent = Rs2Bank.walkToBankAndUseBank();
                if (sent) startBankAction(BankAction.OPEN);
            }
            catch (RuntimeException ex)
            {
                nextBankOpenAttemptNs = System.nanoTime() + BANK_EXCEPTION_BACKOFF_NS;
                debug("GE bank open deferred | message={}", ex.getMessage());
            }
            return;
        }

        nextBankOpenAttemptNs = 0L;
        if (KspBankWidgetHelper.closeBankTutorialOverlayIfOpen()) return;

        if (!Rs2Bank.hasWithdrawAsNote())
        {
            Microbot.status = "Setting Withdraw-as-Note";
            if (Rs2Bank.setWithdrawAsNote()) startBankAction(BankAction.SET_NOTE);
            return;
        }

        if (!bankInventoryPrepared)
        {
            if (!Rs2Inventory.isEmpty())
            {
                Microbot.status = "Clearing Inventory";
                bankActionEmptySlots = Rs2Inventory.emptySlotCount();
                if (Rs2Bank.depositAll()) startBankAction(BankAction.DEPOSIT);
                return;
            }
            bankInventoryPrepared = true;
        }

        // Drain every eligible item into this inventory before leaving the bank.
        if (dispatchNextWithdrawal()) return;

        if (hasSellableInventoryItems())
        {
            closeBank(false);
            return;
        }

        if (hasOpenSellOffers() || hasCompletedSellOffers())
        {
            closeBank(false);
            return;
        }

        if (!hasSellableBankItems()) closeBank(true);
    }

    private void closeBank(boolean finishWhenClosed)
    {
        Microbot.status = "Closing Bank";
        completeAfterBankClose = finishWhenClosed;
        if (Rs2Bank.closeBank()) startBankAction(BankAction.CLOSE);
    }

    private boolean processPendingBankAction()
    {
        if (bankAction == BankAction.NONE) return false;

        boolean completeNow;
        switch (bankAction)
        {
            case OPEN:
                completeNow = Rs2Bank.isOpen();
                break;
            case SET_NOTE:
                completeNow = Rs2Bank.hasWithdrawAsNote();
                break;
            case DEPOSIT:
                completeNow = Rs2Inventory.isEmpty()
                        || Rs2Inventory.emptySlotCount() > bankActionEmptySlots;
                if (completeNow && Rs2Inventory.isEmpty()) bankInventoryPrepared = true;
                break;
            case WITHDRAW:
                completeNow = pendingWithdrawName == null
                        || Rs2Inventory.itemQuantity(pendingWithdrawName, true) > pendingWithdrawInventoryBefore
                        || Rs2Bank.count(pendingWithdrawName, true) < pendingWithdrawBankBefore;
                break;
            case CLOSE:
                completeNow = !Rs2Bank.isOpen();
                break;
            default:
                completeNow = true;
                break;
        }

        if (completeNow)
        {
            BankAction finished = bankAction;
            String finishedItem = pendingWithdrawName;
            clearBankAction();
            if (finished == BankAction.WITHDRAW && finishedItem != null)
                failedBankWithdrawals.remove(finishedItem.toLowerCase(Locale.ROOT));
            if (finished == BankAction.CLOSE)
            {
                bankInventoryPrepared = false;
                if (completeAfterBankClose)
                {
                    completeAfterBankClose = false;
                    complete = true;
                    Microbot.status = "GE Sell Complete";
                }
                else state = SellState.SELLING_ITEMS;
                return true;
            }

            // OPEN/SET_NOTE/DEPOSIT/WITHDRAW completed from actual widget,
            // inventory or bank changes. Continue this same tick instead of
            // burning an extra scheduler cycle per item.
            return false;
        }

        if (bankActionExpired())
        {
            debug("Bank action recovery | action={} item={}", bankAction, pendingWithdrawName);
            if (bankAction == BankAction.WITHDRAW && pendingWithdrawName != null)
                recordFailedWithdrawal(pendingWithdrawName);
            clearBankAction();
            return false;
        }
        return true;
    }

    private void startBankAction(BankAction action)
    {
        bankAction = action;
        bankActionStartedNs = System.nanoTime();
    }

    private boolean bankActionExpired()
    {
        return bankActionStartedNs > 0L
                && System.nanoTime() - bankActionStartedNs >= BANK_ACTION_TIMEOUT_NS;
    }

    private void recordFailedWithdrawal(String name)
    {
        String key = name.toLowerCase(Locale.ROOT);
        int failures = failedBankWithdrawals.merge(key, 1, Integer::sum);
        if (failures >= MAX_WITHDRAW_FAILURES && skippedBankWithdrawals.add(key))
            log.warn("[KSP GE Sell] Skipping bank withdrawal for {} after {} failed attempts this run", name, failures);
    }

    private boolean isWithdrawalSkipped(String name)
    {
        return name != null && skippedBankWithdrawals.contains(name.toLowerCase(Locale.ROOT));
    }

    private void clearBankAction()
    {
        bankAction = BankAction.NONE;
        bankActionStartedNs = 0L;
        pendingWithdrawName = null;
        pendingWithdrawBankBefore = 0;
        pendingWithdrawInventoryBefore = 0;
    }

    private boolean dispatchNextWithdrawal()
    {
        if (Rs2Inventory.emptySlotCount() <= 0) return false;

        for (SellList entry : SELL_ENTRIES)
        {
            String name = entry.getDisplayName();
            if (!shouldSellEntry(entry) || isBlocked(name) || isWithdrawalSkipped(name)) continue;
            int quantity = getSellableBankQuantity(name);
            if (quantity > 0) return dispatchWithdrawal(name, quantity);
        }

        // Outdated combat equipment may be sold even when buy requirements
        // are not affordable; this sale can be what funds the next purchase.
        for (String name : getOutdatedMeleeGearNames())
        {
            int count = Rs2Bank.count(name, true);
            if (count > 0 && !isWithdrawalSkipped(name))
                return dispatchWithdrawal(name, count);
        }
        if (!canAffordGeBuyRequirements()) return false;

        String desiredPickaxe = resolveDesiredPickaxeName();
        for (String name : PICKAXE_NAMES)
        {
            int count = Rs2Bank.count(name, true);
            if (!name.equalsIgnoreCase(desiredPickaxe) && count > 0 && !isWithdrawalSkipped(name))
                return dispatchWithdrawal(name, count);
        }

        String desiredAxe = resolveDesiredAxeName();
        for (String name : AXE_NAMES)
        {
            int count = Rs2Bank.count(name, true);
            if (!name.equalsIgnoreCase(desiredAxe) && count > 0 && !isWithdrawalSkipped(name))
                return dispatchWithdrawal(name, count);
        }
        return false;
    }

    private boolean dispatchWithdrawal(String itemName, int quantity)
    {
        if (itemName == null || quantity <= 0 || Rs2Inventory.emptySlotCount() <= 0 || isWithdrawalSkipped(itemName)) return false;

        int bankBefore = Rs2Bank.count(itemName, true);
        int amount = Math.min(quantity, bankBefore);
        if (amount <= 0) return false;

        int inventoryBefore = Rs2Inventory.itemQuantity(itemName, true);
        boolean sent = amount >= bankBefore
                ? Rs2Bank.withdrawAll(itemName, true)
                : Rs2Bank.withdrawX(itemName, amount, true);
        if (!sent)
        {
            recordFailedWithdrawal(itemName);
            return true; // Yield until the next tick; do not close the bank mid-batch.
        }

        Microbot.status = "Batch withdrawing " + itemName;
        pendingWithdrawName = itemName;
        pendingWithdrawBankBefore = bankBefore;
        pendingWithdrawInventoryBefore = inventoryBefore;
        startBankAction(BankAction.WITHDRAW);
        return true;
    }

    private void sellInventory()
    {
        if (!KspWorldMapGuard.isSceneReady()) return;
        if (Rs2Bank.isOpen())
        {
            state = SellState.RESTOCKING_FROM_BANK;
            return;
        }
        if (pendingOfferItemId > 0)
        {
            advancePendingOffer();
            return;
        }
        if (!ensureGeOverview()) return;
        if (checkStalledSellOffers()) return;

        if (hasCompletedSellOffers())
        {
            Microbot.status = "Collecting Sold Items";
            Rs2GrandExchange.collectAllToBank();
            return;
        }

        Rs2ItemModel item = getNextSellableInventoryItem();
        if (item != null)
        {
            int quantity = getSellableInventoryQuantity(item.getName(), item.getQuantity());
            if (quantity > 0) placeSellOffer(item, quantity);
            return;
        }

        if (hasOpenSellOffers())
        {
            Microbot.status = "Waiting for GE sales";
            return;
        }

        if (hasSellableBankItems())
        {
            Rs2GrandExchange.closeExchange();
            state = SellState.RESTOCKING_FROM_BANK;
            return;
        }

        Rs2GrandExchange.closeExchange();
        complete = true;
        Microbot.status = "GE Sell Complete";
    }

    private void placeSellOffer(Rs2ItemModel item, int quantity)
    {
        if (item == null || item.getName() == null || quantity <= 0) return;
        int itemId = canonicalItemId(item);
        if (itemId <= 0) return;

        GrandExchangeSlots existing = findSellOfferSlot(itemId);
        if (existing != null)
        {
            Microbot.status = "GE sell already active: " + item.getName();
            return;
        }
        if (Rs2GrandExchange.getAvailableSlotsCount() <= 0)
        {
            Microbot.status = "Waiting for GE Slot";
            return;
        }

        int price = sellRepricing.priceForNewOffer(itemId, getAdjustedSellPrice(item));
        Microbot.status = "Selling " + quantity + " x " + item.getName() + " @ " + price;
        GrandExchangeRequest request = GrandExchangeRequest.builder()
                .action(GrandExchangeAction.SELL)
                .itemName(item.getName())
                .exact(true)
                .quantity(quantity)
                .price(price)
                .closeAfterCompletion(false)
                .build();

        boolean accepted;
        try { accepted = Rs2GrandExchange.processOffer(request); }
        catch (RuntimeException ex)
        {
            debug("GE sell request failed | item={} message={}", item.getName(), ex.getMessage());
            recoverGeState("processOffer exception");
            return;
        }

        if (!accepted)
        {
            String restriction = findTradeRestrictionNotice();
            if (restriction != null)
            {
                blockedSellItems.add(item.getName().toLowerCase(Locale.ROOT));
                debug("GE refused item | item={} notice={}", item.getName(), restriction);
            }
            recoverGeState("processOffer returned false");
            return;
        }
        pendingOfferItemId = itemId;
        pendingOfferItemName = item.getName();
        pendingOfferStartedNs = System.nanoTime();
        sellRepricing.placed(itemId, price, System.currentTimeMillis());
    }

    private void advancePendingOffer()
    {
        if (findSellOfferSlot(pendingOfferItemId) != null)
        {
            debug("GE sell registered | item={} id={}", pendingOfferItemName, pendingOfferItemId);
            clearPendingOffer();
            return;
        }

        String restriction = findTradeRestrictionNotice();
        if (restriction != null)
        {
            if (pendingOfferItemName != null)
                blockedSellItems.add(pendingOfferItemName.toLowerCase(Locale.ROOT));
            debug("GE refused pending item | item={} notice={}", pendingOfferItemName, restriction);
            recoverGeState("trade restriction");
            clearPendingOffer();
            return;
        }

        if (!Rs2GrandExchange.isOpen())
        {
            recoverGeState("GE closed while registering offer");
            clearPendingOffer();
            return;
        }

        if (pendingOfferStartedNs > 0L
                && System.nanoTime() - pendingOfferStartedNs >= GE_ACTION_TIMEOUT_NS)
        {
            debug("GE registration recovery | item={}", pendingOfferItemName);
            recoverGeState("offer registration timeout");
            clearPendingOffer();
        }
    }

    /**
     * Check only offers this script placed; never touch an unrelated user
     * offer or one with a manually changed price. Completed offers and
     * partial progress reset their individual repricing timers.
     */
    private boolean checkStalledSellOffers()
    {
        for (GrandExchangeSlots slot : GrandExchangeSlots.values())
        {
            GrandExchangeOffer offer = clientValue(() ->
            {
                GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
                return offers != null && slot.ordinal() < offers.length
                        ? offers[slot.ordinal()] : null;
            }, null);
            if (offer == null) continue;
            int id = offer.getItemId();
            if (offer.getState() == GrandExchangeOfferState.SOLD)
            {
                sellRepricing.forget(id);
                continue;
            }
            if (offer.getState() != GrandExchangeOfferState.SELLING) continue;

            int nextPrice = sellRepricing.candidate(id, offer.getPrice(),
                    offer.getQuantitySold(), System.currentTimeMillis(), false);
            if (nextPrice <= 0) continue;
            // Cancel exactly this slot, not all GE offers. The core utility
            // also requests collection of both unsold items and sale proceeds.
            Microbot.status = "Repricing GE sale " + id + " to " + nextPrice + " gp";
            if (Rs2GrandExchange.cancelSpecificOffers(java.util.Collections.singletonList(slot), true).isEmpty())
                return true;
            sellRepricing.requestCancellation(id, nextPrice);
            repricingSlot = slot;
            repricingItemId = id;
            repricingCancelAtMs = System.currentTimeMillis();
            repricingCancelAttempts = 1;
            debug("GE sell repricing dispatched | item={} slot={} oldPrice={} nextPrice={} sold={}",
                    id, slot, offer.getPrice(), nextPrice, offer.getQuantitySold());
            return true;
        }
        return false;
    }

    private void handleCancelledSellOffer()
    {
        if (repricingSlot == null) return;
        GrandExchangeSlots slot = repricingSlot;
        GrandExchangeOffer offer = clientValue(() ->
        {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            return offers != null && slot.ordinal() < offers.length
                    ? offers[slot.ordinal()] : null;
        }, null);

        if (offer != null && offer.getItemId() == repricingItemId
                && offer.getState() == GrandExchangeOfferState.SELLING)
        {
            if (System.currentTimeMillis() - repricingCancelAtMs >= 8_000L)
            {
                if (repricingCancelAttempts >= 3)
                {
                    // Safe failure: keep existing sale alive. No repeated
                    // cancellation clicks or duplicate sell placements.
                    debug("GE sell cancellation not confirmed | item={} slot={}", repricingItemId, slot);
                    sellRepricing.forget(repricingItemId);
                    repricingSlot = null;
                    repricingItemId = 0;
                    return;
                }
                repricingCancelAttempts++;
                repricingCancelAtMs = System.currentTimeMillis();
                Rs2GrandExchange.cancelSpecificOffers(java.util.Collections.singletonList(slot), true);
            }
            Microbot.status = "Waiting for GE sell cancellation";
            return;
        }

        // An offer might have sold entirely while cancellation was in
        // flight. Never re-post a fully completed transaction.
        boolean completed = offer != null && offer.getItemId() == repricingItemId
                && offer.getState() == GrandExchangeOfferState.SOLD;
        if (!Rs2GrandExchange.collectAllToBank())
        {
            Microbot.status = "Collecting cancelled GE sell stock";
            return;
        }
        if (completed) sellRepricing.forget(repricingItemId);
        else sellRepricing.cancellationConfirmed(repricingItemId, System.currentTimeMillis());

        debug("GE sell repricing collected | item={} completed={} attempts={}",
                repricingItemId, completed, sellRepricing.retries(repricingItemId));
        repricingSlot = null;
        repricingItemId = 0;
        repricingCancelAtMs = 0L;
        repricingCancelAttempts = 0;
        bankInventoryPrepared = false;
        state = SellState.RESTOCKING_FROM_BANK;
        // Bank audit and withdrawal will determine the actual unsold amount;
        // a partial fill must not recreate the original full order.
    }

    private boolean ensureGeOverview()
    {
        if (Rs2Bank.isOpen()) return false;

        if (!Rs2GrandExchange.isOpen())
        {
            geOverviewRequested = false;
            if (geOpenRequested)
            {
                if (System.nanoTime() - geOpenRequestedNs < GE_ACTION_TIMEOUT_NS) return false;
                geOpenRequested = false;
            }
            Microbot.status = "Opening Grand Exchange";
            try
            {
                if (Rs2GrandExchange.openExchange())
                {
                    geOpenRequested = true;
                    geOpenRequestedNs = System.nanoTime();
                }
            }
            catch (RuntimeException ex) { debug("GE open deferred | message={}", ex.getMessage()); }
            return false;
        }

        geOpenRequested = false;
        if (geSubScreenOpen())
        {
            if (geOverviewRequested && System.nanoTime() - geOverviewRequestedNs < GE_ACTION_TIMEOUT_NS)
                return false;
            Microbot.status = "Returning to GE overview";
            Rs2GrandExchange.backToOverview();
            geOverviewRequested = true;
            geOverviewRequestedNs = System.nanoTime();
            return false;
        }
        geOverviewRequested = false;
        return true;
    }

    private boolean geSubScreenOpen()
    {
        return Rs2GrandExchange.isOfferScreenOpen() || Rs2Widget.isWidgetVisible(InterfaceID.GeOffers.SETUP);
    }

    private void recoverGeState(String reason)
    {
        debug("GE recovery | reason={} open={} subScreen={}", reason, Rs2GrandExchange.isOpen(), geSubScreenOpen());
        if (Rs2GrandExchange.isOpen() && geSubScreenOpen())
        {
            Rs2GrandExchange.backToOverview();
            geOverviewRequested = true;
            geOverviewRequestedNs = System.nanoTime();
        }
    }

    private GrandExchangeSlots findSellOfferSlot(int itemId)
    {
        if (itemId <= 0) return null;
        return clientValue(() ->
        {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            if (offers == null) return null;
            int max = Math.min(offers.length, GrandExchangeSlots.values().length);
            for (int i = 0; i < max; i++)
            {
                GrandExchangeOffer offer = offers[i];
                if (offer == null || offer.getItemId() != itemId) continue;
                GrandExchangeOfferState state = offer.getState();
                if (state == GrandExchangeOfferState.SELLING || state == GrandExchangeOfferState.SOLD)
                    return GrandExchangeSlots.values()[i];
            }
            return null;
        }, null);
    }

    private boolean hasOpenSellOffers()
    {
        return clientValue(() ->
        {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            if (offers == null) return false;
            for (GrandExchangeOffer offer : offers)
                if (offer != null && offer.getState() == GrandExchangeOfferState.SELLING) return true;
            return false;
        }, false);
    }

    private boolean hasCompletedSellOffers()
    {
        return clientValue(() ->
        {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            if (offers == null) return false;
            for (GrandExchangeOffer offer : offers)
                if (offer != null && offer.getState() == GrandExchangeOfferState.SOLD) return true;
            return false;
        }, false);
    }

    private void clearPendingOffer()
    {
        pendingOfferItemId = 0;
        pendingOfferItemName = null;
        pendingOfferStartedNs = 0L;
    }

    private String findTradeRestrictionNotice()
    {
        return clientValue(() ->
        {
            Widget setup = Microbot.getClient().getWidget(InterfaceID.GeOffers.SETUP);
            Widget match = findWidgetRecursive(setup, widget ->
            {
                String text = normalize(widget == null ? null : widget.getText());
                return text.contains("restricted for trading") || text.contains("restrictions will lift");
            }, 0);
            return match == null ? null : match.getText().replaceAll("<[^>]*>", "").trim();
        }, null);
    }

    private Widget findWidgetRecursive(Widget root, Predicate<Widget> predicate, int depth)
    {
        if (root == null || root.isHidden() || predicate == null || depth > 14) return null;
        if (predicate.test(root)) return root;
        Widget[] dynamic = root.getDynamicChildren();
        if (dynamic != null)
            for (Widget child : dynamic)
            {
                Widget match = findWidgetRecursive(child, predicate, depth + 1);
                if (match != null) return match;
            }
        Widget[] statics = root.getStaticChildren();
        if (statics != null)
            for (Widget child : statics)
            {
                Widget match = findWidgetRecursive(child, predicate, depth + 1);
                if (match != null) return match;
            }
        return null;
    }

    private int canonicalItemId(Rs2ItemModel item)
    {
        if (item == null) return 0;
        int unnoted = item.getUnNotedId();
        return unnoted > 0 ? unnoted : item.getId();
    }

    private int getAdjustedSellPrice(Rs2ItemModel item)
    {
        if (item == null) return 1;
        int id = canonicalItemId(item);
        int guide = Rs2GrandExchange.getPrice(id);
        if (guide <= 0 && item.getPrice() > 0L)
            guide = (int) Math.min(Integer.MAX_VALUE, item.getPrice());
        return Math.max(1, (int) ((long) Math.max(1, guide) * 90L / 100L));
    }

    /**
     * Do not sell the only reusable tool needed by an enabled activity.
     * In normal automatic mode all skills remain eligible; forced single-skill
     * mode narrows the resource reservation to that activity.
     */
    private boolean taskAvailable(KspTrainSingleSkillTask task)
    {
        if (config == null) return true;
        if (config.runSingleQuest()) return false;
        if (config.trainSingleSkill()) return config.singleSkillTask() == task;
        return true;
    }

    private boolean taskNeedsItem(SellList entry)
    {
        switch (entry)
        {
            case SMALL_FISHING_NET:
            case FISHING_ROD:
            case FISHING_BAIT:
                return taskAvailable(KspTrainSingleSkillTask.FISHING);
            case LOGS:
            case OAK_LOGS:
            case YEW_LOGS:
                return taskAvailable(KspTrainSingleSkillTask.FIREMAKING);
            case RAW_CHICKEN:
                return taskAvailable(KspTrainSingleSkillTask.COOKING);
            case COWHIDE:
                return taskAvailable(KspTrainSingleSkillTask.CRAFTING);
            case SILVER_ORE:
                return taskAvailable(KspTrainSingleSkillTask.SMELTING);
            case SILVER_BAR:
                return taskAvailable(KspTrainSingleSkillTask.SMITHING)
                        || taskAvailable(KspTrainSingleSkillTask.CRAFTING);
            case BRONZE_BAR:
            case IRON_BAR:
            case STEEL_BAR:
            case GOLD_BAR:
            case MITHRIL_BAR:
            case ADAMANT_BAR:
            case RUNE_BAR:
                return taskAvailable(KspTrainSingleSkillTask.SMITHING)
                        || taskAvailable(KspTrainSingleSkillTask.CRAFTING);
            case TUNA:
            case LOBSTER:
            case SWORDFISH:
            case SPINACH_ROLL:
                return taskAvailable(KspTrainSingleSkillTask.MELEE);
            default:
                return false;
        }
    }

    private boolean shouldSellEntry(SellList entry)
    {
        if (entry.isTradeRestricted() && !hasUnlockedTradeRestrictedItems()) return false;
        if (isSupportedMeleeEquipment(entry.getDisplayName())
                && !getOutdatedMeleeGearNames().contains(entry.getDisplayName())) return false;
        // A resource that no enabled task uses can be sold without blocking
        // the sale on the affordability of unrelated future GE purchases.
        if (!taskNeedsItem(entry)) return true;
        if (PROTECTED_SKILL_RESOURCES.contains(entry) && !canAffordGeBuyRequirements()) return false;

        int firemakingLevel = Microbot.getClient().getRealSkillLevel(Skill.FIREMAKING);
        if (entry == SellList.LOGS) return firemakingLevel >= 15;
        if (entry == SellList.OAK_LOGS) return firemakingLevel >= 30;
        if (entry == SellList.SILVER_ORE)
            return Microbot.getClient().getRealSkillLevel(Skill.SMITHING) < 20;
        if (entry == SellList.SMALL_FISHING_NET || entry == SellList.FISHING_ROD || entry == SellList.FISHING_BAIT)
            return Microbot.getClient().getRealSkillLevel(Skill.FISHING) >= 20;
        return true;
    }

    private boolean hasSellableInventoryItems() { return getNextSellableInventoryItem() != null; }

    private boolean hasSellableBankItems()
    {
        for (SellList entry : SELL_ENTRIES)
            if (shouldSellEntry(entry) && !isBlocked(entry.getDisplayName())
                    && !isWithdrawalSkipped(entry.getDisplayName()) && getSellableBankQuantity(entry.getDisplayName()) > 0) return true;
        return hasOutdatedToolInBank() || hasOutdatedMeleeGearInBank();
    }

    public boolean hasSellListItemsAvailable()
    {
        return hasSellableInventoryItems() || hasSellableBankItems()
                || hasOpenSellOffers() || hasCompletedSellOffers();
    }

    private Rs2ItemModel getNextSellableInventoryItem()
    {
        List<Rs2ItemModel> items = Rs2Inventory.all();
        for (Rs2ItemModel item : items)
        {
            if (item == null || item.getName() == null || isBlocked(item.getName())) continue;
            if (getSellableInventoryQuantity(item.getName(), item.getQuantity()) <= 0) continue;
            if (isAllowedSellItemName(item.getName())) return item;
        }
        Set<String> outdatedMeleeGear = getOutdatedMeleeGearNames();
        for (Rs2ItemModel item : items)
            if (item != null && item.getName() != null && !isBlocked(item.getName())
                    && outdatedMeleeGear.contains(item.getName())
                    && getSellableInventoryQuantity(item.getName(), item.getQuantity()) > 0)
                return item;
        if (!canAffordGeBuyRequirements()) return null;
        String desiredPickaxe = resolveDesiredPickaxeName();
        String desiredAxe = resolveDesiredAxeName();
        for (Rs2ItemModel item : items)
            if (item != null && item.getName() != null && !isBlocked(item.getName())
                    && Buy.isOutdatedToolName(item.getName(), desiredPickaxe, desiredAxe)) return item;
        return null;
    }

    private boolean isAllowedSellItemName(String name)
    {
        for (SellList entry : SELL_ENTRIES)
            if (entry.getDisplayName().equalsIgnoreCase(name)
                    && shouldSellEntry(entry) && !isBlocked(entry.getDisplayName())) return true;
        return false;
    }

    private int getSellableBankQuantity(String itemName)
    {
        return Math.max(0, Rs2Bank.count(itemName, true) - getReservedRequirementQuantity(itemName));
    }

    private int getSellableInventoryQuantity(String itemName, int inventoryQuantity)
    {
        return Math.max(0, inventoryQuantity - getReservedRequirementQuantity(itemName));
    }

    private int getReservedRequirementQuantity(String itemName)
    {
        int questReserved = getReservedQuestRequirementQuantity(itemName);
        if (itemName == null) return questReserved;
        // Explicit reservations for future clue-solving and reusable supplies.
        // Clues and scroll boxes must never become generic GE liquidation items.
        if ("Clue scroll (beginner)".equalsIgnoreCase(itemName)
                || "Scroll box (beginner)".equalsIgnoreCase(itemName)
                || "Reward casket (beginner)".equalsIgnoreCase(itemName))
            return Integer.MAX_VALUE;
        if (taskAvailable(KspTrainSingleSkillTask.FISHING)
                && ("Small fishing net".equalsIgnoreCase(itemName)
                || "Fishing rod".equalsIgnoreCase(itemName)
                || "Fly fishing rod".equalsIgnoreCase(itemName)
                || "Harpoon".equalsIgnoreCase(itemName)
                || "Lobster pot".equalsIgnoreCase(itemName)))
            return Math.max(questReserved, 1);
        // Protect a dig tool for clue/quest use instead of selling every spade.
        if ("Spade".equalsIgnoreCase(itemName)) return Math.max(questReserved, 1);
        return questReserved;
    }

    private int getReservedQuestRequirementQuantity(String itemName)
    {
        if (itemName == null) return 0;
        int reserved = 0;
        if (isQuestIncomplete(Quest.COOKS_ASSISTANT))
            for (Items item : Items.values())
                if (item.getDisplayName().equalsIgnoreCase(itemName)) reserved++;
        if (isQuestIncomplete(Quest.GOBLIN_DIPLOMACY))
            for (GobReqs item : GobReqs.values())
                if (item.getDisplayName().equalsIgnoreCase(itemName)) reserved += item.getQuantity();
        if (isQuestIncomplete(Quest.SHEEP_SHEARER) && "Ball of wool".equalsIgnoreCase(itemName))
        {
            int stage = Microbot.getClientThread().runOnClientThreadOptional(
                    () -> Microbot.getClient().getVarpValue(net.runelite.api.gameval.VarPlayerID.SHEEP)).orElse(0);
            reserved += stage > 1 ? Math.max(0, 21 - stage) : 20;
        }
        if (isQuestIncomplete(Quest.IMP_CATCHER)
                && ("Black bead".equalsIgnoreCase(itemName) || "White bead".equalsIgnoreCase(itemName)
                || "Red bead".equalsIgnoreCase(itemName) || "Yellow bead".equalsIgnoreCase(itemName))) reserved++;
        if (isQuestIncomplete(Quest.X_MARKS_THE_SPOT) && "Spade".equalsIgnoreCase(itemName)) reserved++;
        return reserved;
    }

    private boolean isQuestIncomplete(Quest quest) { return Rs2Player.getQuestState(quest) != QuestState.FINISHED; }

    private boolean hasOutdatedMeleeGearInBank()
    {
        for (String name : getOutdatedMeleeGearNames())
            if (!isWithdrawalSkipped(name) && Rs2Bank.count(name, true) > 0) return true;
        return false;
    }

    private boolean isSupportedMeleeEquipment(String name)
    {
        for (Weapons weapon : Weapons.values())
            if (weapon.getDisplayName().equalsIgnoreCase(name)) return true;
        for (Armour armour : Armour.values())
            if (armour.getDisplayName().equalsIgnoreCase(name)) return true;
        return false;
    }

    /** Only sell an equipped-tier weapon or armour piece if a better piece
     * for the same equipment slot is already owned. Future-level gear is kept.
     */
    private Set<String> getOutdatedMeleeGearNames()
    {
        if (outdatedMeleeGearCache != null) return outdatedMeleeGearCache;
        Set<String> outdated = new HashSet<>();
        int attack = Microbot.getClient().getRealSkillLevel(Skill.ATTACK);
        int defence = Microbot.getClient().getRealSkillLevel(Skill.DEFENCE);
        Set<String> protectedGear = new HashSet<>();
        Buy.MeleeGearPlan plan = Buy.buildMeleeGearPlan(attack, defence,
                Rs2Player.getQuestState(Quest.DRAGON_SLAYER_I) == QuestState.FINISHED,
                this::hasGearAnywhere);
        for (String name : plan.getDesiredItems())
            if (name != null) protectedGear.add(name.toLowerCase(Locale.ROOT));

        for (Weapons weapon : Weapons.values())
        {
            String name = weapon.getDisplayName();
            if (weapon.getRequiredAttackLevel() <= attack && hasGearAnywhere(name)
                    && !protectedGear.contains(name.toLowerCase(Locale.ROOT))
                    && hasOwnedWeaponUpgrade(name, attack))
                outdated.add(name);
        }
        for (Armour armour : Armour.values())
        {
            String name = armour.getDisplayName();
            if (armour.getRequiredDefenceLevel() <= defence && hasGearAnywhere(name)
                    && !protectedGear.contains(name.toLowerCase(Locale.ROOT))
                    && hasOwnedArmourUpgrade(name, armour.getRequiredDefenceLevel(), defence))
                outdated.add(name);
        }
        outdatedMeleeGearCache = outdated;
        return outdated;
    }

    private boolean hasOwnedWeaponUpgrade(String current, int attack)
    {
        for (Weapons weapon : Weapons.values())
            if (weapon.getRequiredAttackLevel() <= attack
                    && !weapon.getDisplayName().equalsIgnoreCase(current)
                    && hasGearAnywhere(weapon.getDisplayName()))
                return true;
        return false;
    }

    private boolean hasOwnedArmourUpgrade(String current, int currentLevel, int defence)
    {
        String slot = meleeArmourSlot(current);
        for (Armour armour : Armour.values())
            if (armour.getRequiredDefenceLevel() <= defence
                    && armour.getRequiredDefenceLevel() >= currentLevel
                    && !armour.getDisplayName().equalsIgnoreCase(current)
                    && meleeArmourSlot(armour.getDisplayName()).equals(slot)
                    && hasGearAnywhere(armour.getDisplayName()))
                return true;
        return false;
    }

    private String meleeArmourSlot(String name)
    {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("helm")) return "head";
        if (lower.contains("shield")) return "shield";
        if (lower.contains("legs")) return "legs";
        return "body";
    }

    private boolean hasGearAnywhere(String name)
    {
        return Rs2Equipment.isWearing(name) || Rs2Inventory.hasItem(name)
                || Rs2Inventory.hasItem(name, true) || Rs2Bank.count(name, true) > 0;
    }

    private boolean hasOutdatedToolInBank()
    {
        if (!canAffordGeBuyRequirements()) return false;
        String desiredPickaxe = resolveDesiredPickaxeName();
        String desiredAxe = resolveDesiredAxeName();
        for (String name : PICKAXE_NAMES)
            if (!name.equalsIgnoreCase(desiredPickaxe) && !isWithdrawalSkipped(name) && Rs2Bank.count(name, true) > 0) return true;
        for (String name : AXE_NAMES)
            if (!name.equalsIgnoreCase(desiredAxe) && !isWithdrawalSkipped(name) && Rs2Bank.count(name, true) > 0) return true;
        return false;
    }

    private String resolveDesiredPickaxeName() { return Buy.resolveDesiredPickaxeNameForBuy(); }
    private String resolveDesiredAxeName() { return Buy.resolveDesiredAxeNameForBuy(); }

    private boolean canAffordGeBuyRequirements()
    {
        long now = System.currentTimeMillis();
        if (now - lastBuyAffordabilityCheckAtMs < BUY_AFFORDABILITY_CACHE_MS) return cachedBuyAffordability;
        cachedBuyAffordability = buyScript.canAffordMissingBuys();
        lastBuyAffordabilityCheckAtMs = now;
        return cachedBuyAffordability;
    }

    private boolean hasUnlockedTradeRestrictedItems()
    {
        long now = System.currentTimeMillis();
        if (tradeRestrictionUnlockedCache != null
                && now - lastTradeRestrictionCheckAtMs < TRADE_RESTRICTION_CACHE_MS)
            return tradeRestrictionUnlockedCache;

        long accountHash = getCurrentAccountHash();
        long playTimeMillis = accountHash != 0L && accountPlayTimeCache != null
                && accountPlayTimeCache.hasCachedPlayTime(accountHash)
                ? accountPlayTimeCache.getPlayTimeMillis(accountHash) : -1L;

        boolean unlocked = getTotalLevel() >= TRADE_RESTRICTION_MIN_TOTAL_LEVEL
                && getQuestPoints() >= TRADE_RESTRICTION_MIN_QUEST_POINTS
                && playTimeMillis >= TimeUnit.HOURS.toMillis(TRADE_RESTRICTION_MIN_HOURS_PLAYED);
        tradeRestrictionUnlockedCache = unlocked;
        lastTradeRestrictionCheckAtMs = now;
        return unlocked;
    }

    private long getCurrentAccountHash()
    {
        if (!Microbot.isLoggedIn() || Microbot.getClient() == null) return 0L;
        return Microbot.getClientThread().runOnClientThreadOptional(
                () -> Microbot.getClient().getAccountHash()).orElse(0L);
    }

    private int getTotalLevel()
    {
        int total = 0;
        for (Skill skill : SKILLS)
            if (skill != Skill.OVERALL) total += Microbot.getClient().getRealSkillLevel(skill);
        return total;
    }

    private int getQuestPoints() { return Microbot.getVarbitPlayerValue(101); }

    private boolean isBlocked(String itemName)
    {
        if (itemName == null) return false;
        if (getReservedRequirementQuantity(itemName) == Integer.MAX_VALUE) return true;
        return blockedSellItems.contains(itemName.toLowerCase(Locale.ROOT));
    }

    private String normalize(String value)
    {
        return value == null ? "" : value.replaceAll("<[^>]*>", "").trim().toLowerCase(Locale.ROOT);
    }

    private <T> T clientValue(Supplier<T> supplier, T fallback)
    {
        if (supplier == null) return fallback;
        try
        {
            T value = Microbot.getClientThread().invoke(supplier);
            return value == null ? fallback : value;
        }
        catch (RuntimeException ex) { return fallback; }
    }

    private void debug(String message, Object... args)
    {
        if (debugLogging) KspTaskDebug.info(log, true, "GE Sell", message, args);
    }

    @Override
    public void shutdown()
    {
        KspWalkerGuard.clear(WALK_KEY);
        state = SellState.GOING_TO_GE;
        complete = false;
        bankInventoryPrepared = false;
        completeAfterBankClose = false;
        clearBankAction();
        clearPendingOffer();
        sellRepricing.reset();
        repricingSlot = null;
        repricingItemId = 0;
        repricingCancelAtMs = 0L;
        repricingCancelAttempts = 0;
        blockedSellItems.clear();
        failedBankWithdrawals.clear();
        skippedBankWithdrawals.clear();
        outdatedMeleeGearCache = null;
        tradeRestrictionUnlockedCache = null;
        lastTradeRestrictionCheckAtMs = 0L;
        lastBuyAffordabilityCheckAtMs = 0L;
        cachedBuyAffordability = false;
        geOpenRequested = false;
        geOpenRequestedNs = 0L;
        geOverviewRequested = false;
        geOverviewRequestedNs = 0L;
        nextBankOpenAttemptNs = 0L;
        super.shutdown();
    }

    public boolean isComplete() { return complete; }
    public GEArea getTargetArea() { return targetArea; }
}
