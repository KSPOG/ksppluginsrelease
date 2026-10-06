package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.sellscript;

import java.awt.event.KeyEvent;
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

import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.VarClientStr;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspAccountPlayTimeCache;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspTaskDebug;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspWalkerGuard;
import net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.KspBankWidgetHelper;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.cooksassistant.reqs.Items;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.goblindip.reqs.GobReqs;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.buyscript.Buy;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.buyscript.BuyScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.gearea.GEArea;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.sell.SellList;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Account Builder GE seller.
 *
 * Normal banking and GE progression is state-driven. Actions are dispatched
 * once and subsequent scheduler ticks advance only after the expected widget,
 * bank, inventory, or offer state is observed. Time values below are recovery
 * ceilings/caches only; they are never used as normal action pacing sleeps.
 */
@Singleton
public class SellScript extends Script
{
    private static final Logger log = LoggerFactory.getLogger(SellScript.class);

    private static final int LOOP_DELAY_MS = 40;
    private static final int WEB_WALK_COOLDOWN_MS = 1_000;
    private static final long BANK_ACTION_TIMEOUT_NS = TimeUnit.SECONDS.toNanos(2);
    private static final long GE_ACTION_TIMEOUT_NS = TimeUnit.SECONDS.toNanos(4);
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
        NONE,
        OPEN,
        SET_NOTE,
        DEPOSIT,
        WITHDRAW,
        CLOSE
    }

    private enum OfferPhase
    {
        IDLE,
        WAIT_SETUP,
        CLICK_PRICE,
        WAIT_PRICE_INPUT,
        WAIT_PRICE_ACCEPTED,
        CLICK_QUANTITY,
        WAIT_QUANTITY_INPUT,
        WAIT_QUANTITY_ACCEPTED,
        CONFIRM,
        WAIT_CONFIRM,
        RECOVERING
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
    private Boolean tradeRestrictionUnlockedCache;
    private long lastTradeRestrictionCheckAtMs;
    private long lastBuyAffordabilityCheckAtMs;
    private boolean cachedBuyAffordability;

    private BankAction bankAction = BankAction.NONE;
    private long bankActionStartedNs;
    private int bankActionEmptySlots;
    private String pendingWithdrawName;
    private int pendingWithdrawBankBefore;
    private int pendingWithdrawInventoryBefore;

    private OfferPhase offerPhase = OfferPhase.IDLE;
    private long offerPhaseStartedNs;
    private int offerItemId;
    private String offerItemName;
    private int offerQuantity;
    private int offerPrice;
    private int offerInventoryBefore;
    private int offerSlotsBefore;
    private boolean geOpenRequested;
    private long geOpenRequestedNs;

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
            catch (Exception ex)
            {
                log.warn("KSP Account Builder SellScript tick failed", ex);
                beginOfferRecovery("tick exception");
            }
        }, 0L, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);
        return true;
    }

    private void tick()
    {
        if (!targetArea.toWorldArea().contains(Rs2Player.getWorldLocation()))
        {
            state = SellState.GOING_TO_GE;
            walkToGe();
            return;
        }

        KspWalkerGuard.clear(WALK_KEY);

        if (offerPhase != OfferPhase.IDLE
                || geOpenRequested
                || hasSellableInventoryItems()
                || shouldWaitAtGrandExchange())
            state = SellState.SELLING_ITEMS;
        else
            state = SellState.RESTOCKING_FROM_BANK;

        if (state == SellState.RESTOCKING_FROM_BANK) bankSellItems();
        else sellInventory();
    }

    private void walkToGe()
    {
        if (Rs2Player.isMoving()) return;
        Microbot.status = "Walking to GE";
        KspWalkerGuard.walkToDestination(
                WALK_KEY,
                targetArea::getRandomPoint,
                targetArea.toWorldArea()::contains,
                2,
                WEB_WALK_COOLDOWN_MS);
    }

    private void bankSellItems()
    {
        if (Rs2GrandExchange.isOpen())
        {
            Rs2GrandExchange.closeExchange();
            return;
        }

        if (processPendingBankAction()) return;

        if (!Rs2Bank.isOpen())
        {
            Microbot.status = "Opening GE Bank";
            boolean sent = Rs2Bank.openBank();
            if (!sent) sent = Rs2Bank.walkToBankAndUseBank();
            if (sent) startBankAction(BankAction.OPEN);
            return;
        }

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
            Microbot.status = "Closing Bank";
            completeAfterBankClose = false;
            if (Rs2Bank.closeBank()) startBankAction(BankAction.CLOSE);
            return;
        }

        if (!hasSellableBankItems())
        {
            Microbot.status = "Closing Bank";
            completeAfterBankClose = true;
            if (Rs2Bank.closeBank()) startBankAction(BankAction.CLOSE);
        }
    }

    private boolean processPendingBankAction()
    {
        if (bankAction == BankAction.NONE) return false;

        boolean completeNow = false;
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
            if (!name.equalsIgnoreCase(desiredPickaxe) && Rs2Bank.count(name, true) > 0)
                return dispatchWithdrawal(name, Rs2Bank.count(name, true));

        String desiredAxe = resolveDesiredAxeName();
        for (String name : AXE_NAMES)
            if (!name.equalsIgnoreCase(desiredAxe) && Rs2Bank.count(name, true) > 0)
                return dispatchWithdrawal(name, Rs2Bank.count(name, true));

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
        if (offerPhase != OfferPhase.IDLE)
        {
            advanceOffer();
            return;
        }

        if (Rs2Bank.isOpen())
        {
            Rs2Bank.closeBank();
            return;
        }

        if (Rs2GrandExchange.hasSoldOffer())
        {
            if (!ensureExchangeOpen()) return;
            Microbot.status = "Collecting Sold Items";
            Rs2GrandExchange.collectAllToBank();
            return;
        }

        if (!hasSellableInventoryItems())
        {
            state = SellState.RESTOCKING_FROM_BANK;
            return;
        }

        if (!ensureExchangeOpen()) return;
        if (Rs2GrandExchange.getAvailableSlotsCount() <= 0)
        {
            Microbot.status = "Waiting for GE Slot";
            return;
        }

        Rs2ItemModel item = getNextSellableInventoryItem();
        if (item == null) return;

        int quantity = getSellableInventoryQuantity(item.getName(), item.getQuantity());
        if (quantity <= 0) return;

        startSellOffer(item, quantity, getAdjustedSellPrice(item));
    }

    private boolean ensureExchangeOpen()
    {
        if (Rs2GrandExchange.isOpen())
        {
            geOpenRequested = false;
            return true;
        }

        if (geOpenRequested && System.nanoTime() - geOpenRequestedNs < GE_ACTION_TIMEOUT_NS)
            return false;

        geOpenRequested = false;
        Microbot.status = "Opening GE";
        if (Rs2GrandExchange.openExchange())
        {
            geOpenRequested = true;
            geOpenRequestedNs = System.nanoTime();
        }
        return false;
    }

    private void startSellOffer(Rs2ItemModel item, int quantity, int price)
    {
        if (item == null || item.getName() == null || quantity <= 0 || price <= 0) return;

        offerItemId = item.getId();
        offerItemName = item.getName();
        offerQuantity = quantity;
        offerPrice = price;
        offerInventoryBefore = inventoryTradeQuantity(offerItemId);
        offerSlotsBefore = Rs2GrandExchange.getAvailableSlotsCount();

        Microbot.status = "Selling " + offerItemName + " @ " + offerPrice;
        if (Rs2Inventory.interact(offerItemId, "Offer"))
            setOfferPhase(OfferPhase.WAIT_SETUP);
        else
            clearOffer();
    }

    private void advanceOffer()
    {
        if (offerPhase == OfferPhase.RECOVERING)
        {
            advanceOfferRecovery();
            return;
        }

        if (offerPhaseExpired())
        {
            beginOfferRecovery("phase timeout: " + offerPhase);
            return;
        }

        switch (offerPhase)
        {
            case WAIT_SETUP:
            {
                String restriction = findTradeRestrictionNotice();
                if (restriction != null)
                {
                    blockedSellItems.add(offerItemName.toLowerCase(Locale.ROOT));
                    debug("GE refused item | item={} notice={}", offerItemName, restriction);
                    beginOfferRecovery("trade restriction");
                    return;
                }
                if (isSetupVisible() && setupContainsItem(offerItemId))
                    setOfferPhase(OfferPhase.CLICK_PRICE);
                return;
            }
            case CLICK_PRICE:
            {
                Widget price = findCustomPriceButton();
                if (clickWidget(price)) setOfferPhase(OfferPhase.WAIT_PRICE_INPUT);
                return;
            }
            case WAIT_PRICE_INPUT:
                if (isChatboxInputVisible())
                {
                    if (!setChatboxInputValue(offerPrice))
                    {
                        beginOfferRecovery("price input unavailable");
                        return;
                    }
                    Rs2Keyboard.keyPress(KeyEvent.VK_ENTER);
                    setOfferPhase(OfferPhase.WAIT_PRICE_ACCEPTED);
                }
                return;
            case WAIT_PRICE_ACCEPTED:
                if (!isChatboxInputVisible() && isSetupVisible())
                    setOfferPhase(OfferPhase.CLICK_QUANTITY);
                return;
            case CLICK_QUANTITY:
            {
                Widget quantity = findQuantityButton();
                if (clickWidget(quantity)) setOfferPhase(OfferPhase.WAIT_QUANTITY_INPUT);
                return;
            }
            case WAIT_QUANTITY_INPUT:
                if (isChatboxInputVisible())
                {
                    if (!setChatboxInputValue(offerQuantity))
                    {
                        beginOfferRecovery("quantity input unavailable");
                        return;
                    }
                    Rs2Keyboard.keyPress(KeyEvent.VK_ENTER);
                    setOfferPhase(OfferPhase.WAIT_QUANTITY_ACCEPTED);
                }
                return;
            case WAIT_QUANTITY_ACCEPTED:
                if (!isChatboxInputVisible() && isSetupVisible())
                    setOfferPhase(OfferPhase.CONFIRM);
                return;
            case CONFIRM:
            {
                Widget confirm = findConfirmButton();
                if (clickWidget(confirm)) setOfferPhase(OfferPhase.WAIT_CONFIRM);
                return;
            }
            case WAIT_CONFIRM:
                if (isGePriceWarningVisible())
                {
                    acceptGePriceWarning();
                    offerPhaseStartedNs = System.nanoTime();
                    return;
                }
                if (!isSetupVisible()
                        || inventoryTradeQuantity(offerItemId) < offerInventoryBefore
                        || Rs2GrandExchange.getAvailableSlotsCount() < offerSlotsBefore)
                {
                    debug("Offer placed | item={} qty={} price={}", offerItemName, offerQuantity, offerPrice);
                    clearOffer();
                }
                return;
            default:
                return;
        }
    }

    private void setOfferPhase(OfferPhase phase)
    {
        offerPhase = phase;
        offerPhaseStartedNs = System.nanoTime();
    }

    private boolean offerPhaseExpired()
    {
        return offerPhaseStartedNs > 0L
                && System.nanoTime() - offerPhaseStartedNs >= GE_ACTION_TIMEOUT_NS;
    }

    private void beginOfferRecovery(String reason)
    {
        if (offerPhase == OfferPhase.IDLE) return;
        debug("Recovering GE offer | phase={} item={} reason={}", offerPhase, offerItemName, reason);
        setOfferPhase(OfferPhase.RECOVERING);
        dispatchOfferExit();
    }

    private void advanceOfferRecovery()
    {
        if (!isChatboxInputVisible() && !isSetupVisible())
        {
            clearOffer();
            return;
        }

        if (offerPhaseExpired())
        {
            dispatchOfferExit();
            offerPhaseStartedNs = System.nanoTime();
        }
    }

    private void dispatchOfferExit()
    {
        if (isChatboxInputVisible())
        {
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
            return;
        }

        if (!isSetupVisible()) return;

        Widget back = findBackButton();
        if (back != null && clickWidget(back)) return;
        if (Rs2GrandExchange.isOfferScreenOpen()) Rs2GrandExchange.backToOverview();
        else Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
    }

    private void abortOfferSetup()
    {
        dispatchOfferExit();
        clearOffer();
    }

    private void clearOffer()
    {
        offerPhase = OfferPhase.IDLE;
        offerPhaseStartedNs = 0L;
        offerItemId = 0;
        offerItemName = null;
        offerQuantity = 0;
        offerPrice = 0;
        offerInventoryBefore = 0;
        offerSlotsBefore = 0;
    }

    private boolean isGePriceWarningVisible()
    {
        return Rs2Widget.hasWidget("Your offer is much")
                || Rs2Widget.hasWidget("much lower than")
                || Rs2Widget.hasWidget("much higher than")
                || Rs2Widget.hasWidget("Select an Option");
    }

    private void acceptGePriceWarning()
    {
        if (!Rs2Widget.clickWidget("Yes")) Rs2Keyboard.keyPress(KeyEvent.VK_1);
    }

    private boolean isSetupVisible()
    {
        return clientValue(() -> isVisible(Microbot.getClient().getWidget(InterfaceID.GeOffers.SETUP)), false);
    }

    private boolean isChatboxInputVisible()
    {
        return clientValue(() -> isVisible(Microbot.getClient().getWidget(InterfaceID.Chatbox.MES_TEXT2)), false);
    }

    private boolean setChatboxInputValue(long value)
    {
        if (value <= 0L) return false;
        return clientValue(() ->
        {
            Widget input = Microbot.getClient().getWidget(InterfaceID.Chatbox.MES_TEXT2);
            if (!isVisible(input)) return false;
            String text = Long.toString(value);
            input.setText(text + "*");
            Microbot.getClient().setVarcStrValue(VarClientStr.INPUT_TEXT, text);
            return true;
        }, false);
    }

    private boolean setupContainsItem(int expectedItemId)
    {
        return clientValue(() -> containsItemId(
                Microbot.getClient().getWidget(InterfaceID.GeOffers.SETUP), expectedItemId, 0), false);
    }

    private boolean containsItemId(Widget root, int expectedItemId, int depth)
    {
        if (root == null || expectedItemId <= 0 || depth > 14) return false;
        if (root.getItemId() > 0 && sameTradeItem(root.getItemId(), expectedItemId)) return true;

        Widget[] dynamic = root.getDynamicChildren();
        if (dynamic != null)
            for (Widget child : dynamic)
                if (containsItemId(child, expectedItemId, depth + 1)) return true;

        Widget[] statics = root.getStaticChildren();
        if (statics != null)
            for (Widget child : statics)
                if (containsItemId(child, expectedItemId, depth + 1)) return true;

        return false;
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

    private Widget findCustomPriceButton()
    {
        return clientValue(() ->
        {
            Widget container = Microbot.getClient().getWidget(ComponentID.GRAND_EXCHANGE_OFFER_CONTAINER);
            if (isVisible(container))
            {
                Widget exact = container.getChild(12);
                if (isClickable(exact)) return exact;
            }

            Widget setup = Microbot.getClient().getWidget(InterfaceID.GeOffers.SETUP);
            Widget match = findWidgetRecursive(setup, this::isCustomPriceWidget, 0);
            return match != null ? match : findWidgetRecursive(container, this::isCustomPriceWidget, 0);
        }, null);
    }

    private Widget findQuantityButton()
    {
        return clientValue(() ->
        {
            Widget container = Microbot.getClient().getWidget(ComponentID.GRAND_EXCHANGE_OFFER_CONTAINER);
            if (isVisible(container))
            {
                Widget exact = container.getChild(7);
                if (isClickable(exact)) return exact;
            }
            return findWidgetRecursive(
                    Microbot.getClient().getWidget(InterfaceID.GeOffers.SETUP), this::isQuantityWidget, 0);
        }, null);
    }

    private Widget findConfirmButton()
    {
        return clientValue(() -> findWidgetRecursive(
                Microbot.getClient().getWidget(InterfaceID.GeOffers.SETUP), this::isConfirmWidget, 0), null);
    }

    private Widget findBackButton()
    {
        return clientValue(() ->
        {
            Widget setup = Microbot.getClient().getWidget(InterfaceID.GeOffers.SETUP);
            if (!isVisible(setup)) return null;
            Widget parent = setup.getParent();
            Widget match = findWidgetRecursive(parent, this::isBackWidget, 0);
            return match != null ? match : findWidgetRecursive(setup, this::isBackWidget, 0);
        }, null);
    }

    private Widget findWidgetRecursive(Widget root, Predicate<Widget> predicate, int depth)
    {
        if (!isVisible(root) || predicate == null || depth > 14) return null;
        if (predicate.test(root) && root.getBounds() != null) return root;

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

    private boolean isCustomPriceWidget(Widget widget)
    {
        if (widget == null) return false;
        String[] actions = widget.getActions();
        if (actions != null)
            for (String action : actions)
            {
                String value = normalize(action);
                if (value.equals("enter price") || value.equals("set price")
                        || value.contains("custom price")) return true;
            }
        return normalize(widget.getText()).contains("price per item") && hasAnyAction(widget);
    }

    private boolean isQuantityWidget(Widget widget)
    {
        if (widget == null) return false;
        String[] actions = widget.getActions();
        if (actions != null)
            for (String action : actions)
            {
                String value = normalize(action);
                if (value.equals("enter quantity") || value.equals("set quantity")
                        || value.contains("custom quantity")) return true;
            }
        return normalize(widget.getText()).contains("quantity") && hasAnyAction(widget);
    }

    private boolean isConfirmWidget(Widget widget)
    {
        if (widget == null) return false;
        String[] actions = widget.getActions();
        if (actions != null)
            for (String action : actions)
                if (normalize(action).contains("confirm")) return true;
        return normalize(widget.getText()).contains("confirm") && hasAnyAction(widget);
    }

    private boolean isBackWidget(Widget widget)
    {
        if (widget == null || widget.getActions() == null) return false;
        for (String action : widget.getActions())
            if ("back".equals(normalize(action))) return true;
        return false;
    }

    private boolean clickWidget(Widget widget)
    {
        if (widget == null) return false;
        return clientValue(() -> isClickable(widget) && Rs2Widget.clickWidget(widget), false);
    }

    private boolean isClickable(Widget widget)
    {
        return isVisible(widget) && widget.getBounds() != null;
    }

    private boolean isVisible(Widget widget)
    {
        return widget != null && !widget.isHidden();
    }

    private boolean hasAnyAction(Widget widget)
    {
        if (widget == null || widget.getActions() == null) return false;
        for (String action : widget.getActions())
            if (action != null && !action.trim().isEmpty()) return true;
        return false;
    }

    private String normalize(String value)
    {
        return value == null ? "" : value.replaceAll("<[^>]*>", "").trim().toLowerCase(Locale.ROOT);
    }

    private int inventoryTradeQuantity(int itemId)
    {
        int quantity = 0;
        for (Rs2ItemModel item : Rs2Inventory.all())
            if (item != null && sameTradeItem(item.getId(), itemId))
                quantity += Math.max(0, item.getQuantity());
        return quantity;
    }

    private boolean sameTradeItem(int left, int right)
    {
        if (left <= 0 || right <= 0) return false;
        int leftUnnoted = Rs2ItemModel.getUnNotedId(left);
        int rightUnnoted = Rs2ItemModel.getUnNotedId(right);
        if (leftUnnoted <= 0) leftUnnoted = left;
        if (rightUnnoted <= 0) rightUnnoted = right;
        return leftUnnoted == rightUnnoted;
    }

    private int getAdjustedSellPrice(Rs2ItemModel item)
    {
        if (item == null) return 1;
        int id = item.getUnNotedId() > 0 ? item.getUnNotedId() : item.getId();
        int guide = Rs2GrandExchange.getPrice(id);
        if (guide <= 0 && item.getPrice() > 0L)
            guide = (int) Math.min(Integer.MAX_VALUE, item.getPrice());
        return Math.max(1, (int) ((long) Math.max(1, guide) * 90L / 100L));
    }

    private boolean shouldWaitAtGrandExchange()
    {
        return Rs2GrandExchange.isOpen()
                && (Rs2GrandExchange.isOfferScreenOpen()
                || Rs2GrandExchange.hasSoldOffer()
                || Rs2GrandExchange.getAvailableSlotsCount() <= 0);
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
        return hasSellableInventoryItems() || hasSellableBankItems();
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
        abortOfferSetup();
        KspWalkerGuard.clear(WALK_KEY);
        state = SellState.GOING_TO_GE;
        complete = false;
        bankInventoryPrepared = false;
        completeAfterBankClose = false;
        clearBankAction();
        blockedSellItems.clear();
        tradeRestrictionUnlockedCache = null;
        lastTradeRestrictionCheckAtMs = 0L;
        lastBuyAffordabilityCheckAtMs = 0L;
        cachedBuyAffordability = false;
        geOpenRequested = false;
        geOpenRequestedNs = 0L;
        super.shutdown();
    }

    public boolean isComplete() { return complete; }
    public GEArea getTargetArea() { return targetArea; }
}
