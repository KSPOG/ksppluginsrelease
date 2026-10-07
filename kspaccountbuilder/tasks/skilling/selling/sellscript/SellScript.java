package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.sellscript;

import java.util.EnumSet;
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
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspTaskDebug;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspWalkerGuard;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspWorldMapGuard;
import net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.KspBankWidgetHelper;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.cooksassistant.reqs.Items;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.goblindip.reqs.GobReqs;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.buyscript.Buy;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.buyscript.BuyScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.gearea.GEArea;
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

/**
 * Account Builder GE seller.
 *
 * <p>The GE path deliberately follows the two proven KSP implementations:
 * Jewelry Crafter's rule that the selected GE setup must be genuinely ready,
 * and Smart Smelter's use of {@link GrandExchangeRequest}/{@link Rs2GrandExchange#processOffer}
 * plus verification against RuneLite's live {@link GrandExchangeOffer} array.
 * This avoids maintaining a second fragile implementation of the price and
 * quantity chatbox widgets inside Account Builder.</p>
 *
 * <p>Normal progression is scheduler/state driven. Time values are recovery
 * ceilings only and are never used as post-click sleeps.</p>
 */
@Singleton
public class SellScript extends Script
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

    private enum BankAction
    {
        NONE, OPEN, SET_NOTE, DEPOSIT, WITHDRAW, CLOSE
    }

    @Inject private KspAccountPlayTimeCache accountPlayTimeCache;
    @Inject private BuyScript buyScript;

    private final Set<String> blockedSellItems = new HashSet<>();

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

    private Boolean tradeRestrictionUnlockedCache;
    private long lastTradeRestrictionCheckAtMs;
    private long lastBuyAffordabilityCheckAtMs;
    private boolean cachedBuyAffordability;

    public void setDebugLogging(boolean debugLogging)
    {
        this.debugLogging = debugLogging;
    }

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
                if (!super.run() || !Microbot.isLoggedIn() || complete) return;
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

        if (pendingOfferItemId > 0 || geOpenRequested || geOverviewRequested
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
        KspWalkerGuard.walkToDestination(
                WALK_KEY,
                targetArea::getRandomPoint,
                targetArea.toWorldArea()::contains,
                2,
                0L);
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
                // The client can briefly invalidate its local WorldPoint while a
                // scene is changing. Do not hammer another synchronous scene scan.
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

        if (dispatchNextWithdrawal()) return;

        if (hasSellableInventoryItems())
        {
            closeBank(false);
            return;
        }

        // If there are already live GE offers, leave the bank and service them.
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
            clearBankAction();
            if (finished == BankAction.CLOSE)
            {
                bankInventoryPrepared = false;
                if (completeAfterBankClose)
                {
                    completeAfterBankClose = false;
                    complete = true;
                    Microbot.status = "GE Sell Complete";
                }
                else
                {
                    state = SellState.SELLING_ITEMS;
                }
            }
            return true;
        }

        if (bankActionExpired())
        {
            debug("Bank action recovery | action={} item={}", bankAction, pendingWithdrawName);
            clearBankAction();
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
            if (!shouldSellEntry(entry) || isBlocked(name)) continue;
            int quantity = getSellableBankQuantity(name);
            if (quantity > 0) return dispatchWithdrawal(name, quantity);
        }

        if (!canAffordGeBuyRequirements()) return false;

        String desiredPickaxe = resolveDesiredPickaxeName();
        for (String name : PICKAXE_NAMES)
        {
            int count = Rs2Bank.count(name, true);
            if (!name.equalsIgnoreCase(desiredPickaxe) && count > 0)
                return dispatchWithdrawal(name, count);
        }

        String desiredAxe = resolveDesiredAxeName();
        for (String name : AXE_NAMES)
        {
            int count = Rs2Bank.count(name, true);
            if (!name.equalsIgnoreCase(desiredAxe) && count > 0)
                return dispatchWithdrawal(name, count);
        }

        return false;
    }

    private boolean dispatchWithdrawal(String itemName, int quantity)
    {
        if (itemName == null || quantity <= 0 || Rs2Inventory.emptySlotCount() <= 0) return false;

        int bankBefore = Rs2Bank.count(itemName, true);
        int amount = Math.min(quantity, bankBefore);
        if (amount <= 0) return false;

        int inventoryBefore = Rs2Inventory.itemQuantity(itemName, true);
        boolean sent = amount >= bankBefore
                ? Rs2Bank.withdrawAll(itemName, true)
                : Rs2Bank.withdrawX(itemName, amount, true);
        if (!sent) return false;

        Microbot.status = "Withdrawing " + itemName;
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
            Rs2Bank.closeBank();
            return;
        }

        if (pendingOfferItemId > 0)
        {
            advancePendingOffer();
            return;
        }

        if (!ensureGeOverview()) return;

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

    /**
     * Mirrors Smart Smelter: let Microbot's current GE request flow own the
     * widgets and verify placement from the live offer array afterwards.
     */
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

        int price = getAdjustedSellPrice(item);
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
        try
        {
            accepted = Rs2GrandExchange.processOffer(request);
        }
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
     * Jewelry Crafter only proceeds once the GE is on a stable overview/setup.
     * We keep the same readiness rule but observe it over scheduler ticks rather
     * than sleeping for UI transitions.
     */
    private boolean ensureGeOverview()
    {
        if (Rs2Bank.isOpen())
        {
            Rs2Bank.closeBank();
            return false;
        }

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
            catch (RuntimeException ex)
            {
                debug("GE open deferred | message={}", ex.getMessage());
            }
            return false;
        }

        geOpenRequested = false;

        if (geSubScreenOpen())
        {
            if (geOverviewRequested
                    && System.nanoTime() - geOverviewRequestedNs < GE_ACTION_TIMEOUT_NS)
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
        return Rs2GrandExchange.isOfferScreenOpen()
                || Rs2Widget.isWidgetVisible(InterfaceID.GeOffers.SETUP);
    }

    private void recoverGeState(String reason)
    {
        debug("GE recovery | reason={} open={} subScreen={}",
                reason, Rs2GrandExchange.isOpen(), geSubScreenOpen());

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

    private boolean shouldSellEntry(SellList entry)
    {
        if (entry.isTradeRestricted() && !hasUnlockedTradeRestrictedItems()) return false;
        if (PROTECTED_SKILL_RESOURCES.contains(entry) && !canAffordGeBuyRequirements()) return false;

        int firemakingLevel = Microbot.getClient().getRealSkillLevel(Skill.FIREMAKING);
        if (entry == SellList.LOGS) return firemakingLevel >= 15;
        if (entry == SellList.OAK_LOGS) return firemakingLevel >= 30;
        if (entry == SellList.SILVER_ORE)
            return Microbot.getClient().getRealSkillLevel(Skill.SMITHING) < 20;
        if (entry == SellList.SMALL_FISHING_NET || entry == SellList.FISHING_ROD
                || entry == SellList.FISHING_BAIT)
            return Microbot.getClient().getRealSkillLevel(Skill.FISHING) >= 20;
        return true;
    }

    private boolean hasSellableInventoryItems()
    {
        return getNextSellableInventoryItem() != null;
    }

    private boolean hasSellableBankItems()
    {
        for (SellList entry : SELL_ENTRIES)
            if (shouldSellEntry(entry) && !isBlocked(entry.getDisplayName())
                    && getSellableBankQuantity(entry.getDisplayName()) > 0) return true;
        return hasOutdatedToolInBank();
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
        return Math.max(0, Rs2Bank.count(itemName, true) - getReservedQuestRequirementQuantity(itemName));
    }

    private int getSellableInventoryQuantity(String itemName, int inventoryQuantity)
    {
        return Math.max(0, inventoryQuantity - getReservedQuestRequirementQuantity(itemName));
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
            int stage = Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getVarpValue(net.runelite.api.gameval.VarPlayerID.SHEEP)).orElse(0);
            reserved += stage > 1 ? Math.max(0, 21 - stage) : 20;
        }
        if (isQuestIncomplete(Quest.IMP_CATCHER)
                && ("Black bead".equalsIgnoreCase(itemName) || "White bead".equalsIgnoreCase(itemName)
                || "Red bead".equalsIgnoreCase(itemName) || "Yellow bead".equalsIgnoreCase(itemName))) reserved++;
        if (isQuestIncomplete(Quest.X_MARKS_THE_SPOT) && "Spade".equalsIgnoreCase(itemName)) reserved++;

        return reserved;
    }

    private boolean isQuestIncomplete(Quest quest)
    {
        return Rs2Player.getQuestState(quest) != QuestState.FINISHED;
    }

    private boolean hasOutdatedToolInBank()
    {
        if (!canAffordGeBuyRequirements()) return false;
        String desiredPickaxe = resolveDesiredPickaxeName();
        String desiredAxe = resolveDesiredAxeName();

        for (String name : PICKAXE_NAMES)
            if (!name.equalsIgnoreCase(desiredPickaxe) && Rs2Bank.count(name) > 0) return true;
        for (String name : AXE_NAMES)
            if (!name.equalsIgnoreCase(desiredAxe) && Rs2Bank.count(name) > 0) return true;
        return false;
    }

    private String resolveDesiredPickaxeName() { return Buy.resolveDesiredPickaxeNameForBuy(); }
    private String resolveDesiredAxeName() { return Buy.resolveDesiredAxeNameForBuy(); }

    private boolean canAffordGeBuyRequirements()
    {
        long now = System.currentTimeMillis();
        if (now - lastBuyAffordabilityCheckAtMs < BUY_AFFORDABILITY_CACHE_MS)
            return cachedBuyAffordability;

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
        return itemName != null && blockedSellItems.contains(itemName.toLowerCase(Locale.ROOT));
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
        catch (RuntimeException ex)
        {
            return fallback;
        }
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
        blockedSellItems.clear();
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
