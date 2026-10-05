package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.sellscript;

import java.awt.event.KeyEvent;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Account Builder GE seller.
 *
 * The GE flow intentionally mirrors Microbot-Hub BankSeller. It does not use
 * KspGrandExchangeSafe/KspGrandExchangeHelper and does not call Microbot's
 * legacy processOffer price-varbit path. The real price widget is located by
 * its live "Enter price" action, the chatbox input is populated through
 * MESLAYERINPUT, and each offer is verified before continuing.
 */
@Singleton
public class SellScript extends Script
{
    private static final Logger log = LoggerFactory.getLogger(SellScript.class);

    private static final int LOOP_DELAY_MS = 40;
    private static final int WEB_WALK_COOLDOWN_MS = 1_000;
    private static final int BANK_TIMEOUT_MS = 900;
    private static final int GE_SETUP_TIMEOUT_MS = 3_000;
    private static final int GE_CONFIRM_TIMEOUT_MS = 4_000;
    private static final int MAX_WITHDRAW_FAILURES = 3;
    private static final int MAX_OFFER_FAILURES = 3;
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

    @Inject private KspAccountPlayTimeCache accountPlayTimeCache;
    @Inject private BuyScript buyScript;

    private final Set<String> blockedSellItems = new HashSet<>();
    private final Map<String, Integer> withdrawFailures = new HashMap<>();
    private final Map<String, Integer> offerFailures = new HashMap<>();

    private GEArea targetArea = GEArea.GRAND_EXCHANGE;
    private SellState state = SellState.GOING_TO_GE;
    private boolean debugLogging;
    private boolean complete;
    private boolean bankInventoryPrepared;
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
            catch (Exception ex)
            {
                log.warn("KSP Account Builder SellScript tick failed", ex);
                abortOfferSetup();
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

        if (hasSellableInventoryItems() || shouldWaitAtGrandExchange())
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

    /** Bank -> note mode -> deposit once -> withdraw each stack -> close bank. */
    private void bankSellItems()
    {
        if (Rs2GrandExchange.isOpen())
        {
            Rs2GrandExchange.closeExchange();
            return;
        }

        if (!Rs2Bank.isOpen())
        {
            Microbot.status = "Opening GE Bank";
            if (!Rs2Bank.openBank()) Rs2Bank.walkToBankAndUseBank();
            return;
        }

        if (KspBankWidgetHelper.closeBankTutorialOverlayIfOpen()) return;

        if (!Rs2Bank.hasWithdrawAsNote())
        {
            Microbot.status = "Setting Withdraw-as-Note";
            if (!Rs2Bank.setWithdrawAsNote()) return;
            if (!sleepUntil(Rs2Bank::hasWithdrawAsNote, BANK_TIMEOUT_MS)) return;
        }

        if (!bankInventoryPrepared)
        {
            if (!Rs2Inventory.isEmpty())
            {
                Microbot.status = "Clearing Inventory";
                int before = Rs2Inventory.emptySlotCount();
                if (!Rs2Bank.depositAll()) return;
                if (!sleepUntil(() -> Rs2Inventory.isEmpty()
                        || Rs2Inventory.emptySlotCount() > before, BANK_TIMEOUT_MS)) return;
                if (!Rs2Inventory.isEmpty()) return;
            }
            bankInventoryPrepared = true;
        }

        Microbot.status = "Withdrawing Sell Items";
        withdrawSellBatch();

        if (!hasSellableInventoryItems())
        {
            if (!hasSellableBankItems())
            {
                Rs2Bank.closeBank();
                complete = true;
                Microbot.status = "GE Sell Complete";
            }
            return;
        }

        Microbot.status = "Closing Bank";
        Rs2Bank.closeBank();
        if (!sleepUntil(() -> !Rs2Bank.isOpen(), BANK_TIMEOUT_MS)) return;
        bankInventoryPrepared = false;
        state = SellState.SELLING_ITEMS;
    }

    private void withdrawSellBatch()
    {
        for (SellList entry : SELL_ENTRIES)
        {
            if (Rs2Inventory.emptySlotCount() <= 0) break;
            String name = entry.getDisplayName();
            if (!shouldSellEntry(entry) || isBlocked(name)) continue;
            int qty = getSellableBankQuantity(name);
            if (qty > 0) withdrawStack(name, qty);
        }

        if (Rs2Inventory.emptySlotCount() > 0 && canAffordGeBuyRequirements())
            withdrawOutdatedTools();
    }

    private boolean withdrawStack(String itemName, int quantity)
    {
        if (itemName == null || quantity <= 0 || Rs2Inventory.emptySlotCount() <= 0) return false;

        int bankBefore = Rs2Bank.count(itemName, true);
        int inventoryBefore = Rs2Inventory.itemQuantity(itemName, true);
        int amount = Math.min(quantity, bankBefore);
        if (amount <= 0) return false;

        boolean sent = amount >= bankBefore
                ? Rs2Bank.withdrawAll(itemName, true)
                : Rs2Bank.withdrawX(itemName, amount, true);
        if (!sent)
        {
            recordWithdraw(itemName, false);
            return false;
        }

        boolean changed = sleepUntil(() ->
                Rs2Inventory.itemQuantity(itemName, true) > inventoryBefore
                        || Rs2Bank.count(itemName, true) < bankBefore,
                BANK_TIMEOUT_MS);
        recordWithdraw(itemName, changed);
        return changed;
    }

    private void withdrawOutdatedTools()
    {
        String desiredPickaxe = resolveDesiredPickaxeName();
        String desiredAxe = resolveDesiredAxeName();

        for (String name : PICKAXE_NAMES)
        {
            if (Rs2Inventory.emptySlotCount() <= 0) return;
            if (!name.equalsIgnoreCase(desiredPickaxe))
            {
                int qty = Rs2Bank.count(name, true);
                if (qty > 0) withdrawStack(name, qty);
            }
        }

        for (String name : AXE_NAMES)
        {
            if (Rs2Inventory.emptySlotCount() <= 0) return;
            if (!name.equalsIgnoreCase(desiredAxe))
            {
                int qty = Rs2Bank.count(name, true);
                if (qty > 0) withdrawStack(name, qty);
            }
        }
    }

    /**
     * Hub-style direct GE seller. No KSP GE helper and no legacy price varbit.
     */
    private void sellInventory()
    {
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
            if (isSetupVisible()) abortOfferSetup();
            state = SellState.RESTOCKING_FROM_BANK;
            return;
        }

        if (!ensureExchangeOpen()) return;
        if (Rs2GrandExchange.getAvailableSlotsCount() <= 0)
        {
            Microbot.status = "Waiting for GE Slot";
            return;
        }

        int remainingSlots = Math.min(8, Rs2GrandExchange.getAvailableSlotsCount());
        while (remainingSlots-- > 0 && Rs2GrandExchange.getAvailableSlotsCount() > 0)
        {
            Rs2ItemModel item = getNextSellableInventoryItem();
            if (item == null) break;

            int quantity = getSellableInventoryQuantity(item.getName(), item.getQuantity());
            if (quantity <= 0) break;

            int price = getAdjustedSellPrice(item);
            Microbot.status = "Selling " + item.getName() + " @ " + price;

            int beforeQty = inventoryTradeQuantity(item.getId());
            int slotsBefore = Rs2GrandExchange.getAvailableSlotsCount();
            boolean placed = placeSellOffer(item, quantity, price);
            int afterQty = inventoryTradeQuantity(item.getId());

            if (placed || afterQty < beforeQty || Rs2GrandExchange.getAvailableSlotsCount() < slotsBefore)
            {
                offerFailures.remove(item.getName().toLowerCase(Locale.ROOT));
                debug("Offer placed | item={} qty={} price={} remaining={}",
                        item.getName(), quantity, price, afterQty);
                continue;
            }

            recordOfferFailure(item.getName());
            break;
        }

        if (!hasSellableInventoryItems()) state = SellState.RESTOCKING_FROM_BANK;
    }

    private boolean ensureExchangeOpen()
    {
        if (Rs2GrandExchange.isOpen()) return true;
        Microbot.status = "Opening GE";
        if (!Rs2GrandExchange.openExchange()) return false;
        return sleepUntil(Rs2GrandExchange::isOpen, GE_SETUP_TIMEOUT_MS);
    }

    private boolean placeSellOffer(Rs2ItemModel item, int quantity, int price)
    {
        if (item == null || item.getName() == null || quantity <= 0 || price <= 0) return false;

        if (isSetupVisible()) abortOfferSetup();
        if (!Rs2GrandExchange.isOpen()) return false;

        if (!Rs2Inventory.interact(item.getId(), "Offer")) return false;
        if (!sleepUntil(() -> isSetupVisible() && setupContainsItem(item.getId()), GE_SETUP_TIMEOUT_MS))
            return false;

        String restriction = findTradeRestrictionNotice();
        if (restriction != null)
        {
            blockedSellItems.add(item.getName().toLowerCase(Locale.ROOT));
            debug("GE refused item | item={} notice={}", item.getName(), restriction);
            abortOfferSetup();
            return false;
        }

        if (!enterPrice(price))
        {
            debug("Failed entering price | item={} price={}", item.getName(), price);
            abortOfferSetup();
            return false;
        }

        if (!enterFullQuantity(quantity))
        {
            debug("Failed entering quantity | item={} qty={}", item.getName(), quantity);
            abortOfferSetup();
            return false;
        }

        Widget confirm = findConfirmButton();
        if (!clickWidget(confirm))
        {
            abortOfferSetup();
            return false;
        }

        long deadline = System.currentTimeMillis() + GE_CONFIRM_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline)
        {
            if (Thread.currentThread().isInterrupted()) return false;
            if (isGePriceWarningVisible())
            {
                acceptGePriceWarning();
                continue;
            }
            if (!isSetupVisible()) return true;
            sleep(80, 140);
        }

        return inventoryTradeQuantity(item.getId()) == 0;
    }

    /**
     * Clicks the exact price row shown in the user's screenshot. It is located
     * by its live Enter price/Set price action rather than a child index.
     */
    private boolean enterPrice(int price)
    {
        Widget priceWidget = findCustomPriceButton();
        if (!clickWidget(priceWidget)) return false;
        if (!sleepUntil(this::isChatboxInputVisible, GE_SETUP_TIMEOUT_MS)) return false;
        if (!setChatboxInputValue(price)) return false;
        Rs2Keyboard.keyPress(KeyEvent.VK_ENTER);
        return sleepUntil(() -> !isChatboxInputVisible(), GE_SETUP_TIMEOUT_MS)
                && isSetupVisible();
    }

    private boolean enterFullQuantity(int quantity)
    {
        Widget all = findAllQuantityButton();
        if (all != null && clickWidget(all))
        {
            sleepUntil(this::isSetupVisible, 500);
            return true;
        }

        Widget quantityWidget = findQuantityButton();
        if (!clickWidget(quantityWidget)) return false;
        if (!sleepUntil(this::isChatboxInputVisible, GE_SETUP_TIMEOUT_MS)) return false;
        if (!setChatboxInputValue(quantity)) return false;
        Rs2Keyboard.keyPress(KeyEvent.VK_ENTER);
        return sleepUntil(() -> !isChatboxInputVisible(), GE_SETUP_TIMEOUT_MS)
                && isSetupVisible();
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

    private void abortOfferSetup()
    {
        if (isChatboxInputVisible())
        {
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
            sleepUntil(() -> !isChatboxInputVisible(), 600);
        }
        if (!isSetupVisible()) return;

        Widget back = findBackButton();
        if (back != null && clickWidget(back)
                && sleepUntil(() -> !isSetupVisible(), GE_SETUP_TIMEOUT_MS)) return;

        if (Rs2GrandExchange.isOfferScreenOpen()) Rs2GrandExchange.backToOverview();
        else Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
        sleepUntil(() -> !isSetupVisible(), GE_SETUP_TIMEOUT_MS);
    }

    private boolean isSetupVisible()
    {
        return clientValue(() -> isVisible(Microbot.getClient().getWidget(InterfaceID.GeOffers.SETUP)), false);
    }

    private boolean isChatboxInputVisible()
    {
        return clientValue(() -> isVisible(Microbot.getClient().getWidget(ComponentID.CHATBOX_FULL_INPUT)), false);
    }

    private boolean setChatboxInputValue(long value)
    {
        if (value <= 0L) return false;
        return clientValue(() ->
        {
            Widget input = Microbot.getClient().getWidget(ComponentID.CHATBOX_FULL_INPUT);
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
            Widget setup = Microbot.getClient().getWidget(InterfaceID.GeOffers.SETUP);
            Widget match = findWidgetRecursive(setup, this::isCustomPriceWidget, 0);
            if (match != null) return match;

            Widget container = Microbot.getClient().getWidget(ComponentID.GRAND_EXCHANGE_OFFER_CONTAINER);
            return findWidgetRecursive(container, this::isCustomPriceWidget, 0);
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

    private Widget findAllQuantityButton()
    {
        return clientValue(() ->
        {
            Widget container = Microbot.getClient().getWidget(ComponentID.GRAND_EXCHANGE_OFFER_CONTAINER);
            if (!isVisible(container)) return null;

            Widget current = container.getChild(50);
            if (isClickable(current) && isAllQuantityWidget(current)) return current;

            Widget legacy = container.getChild(6);
            if (isClickable(legacy) && isAllQuantityWidget(legacy)) return legacy;

            return findWidgetRecursive(container, this::isAllQuantityWidget, 0);
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
        {
            for (String action : actions)
            {
                String value = normalize(action);
                if (value.equals("enter price") || value.equals("set price")
                        || value.contains("custom price")) return true;
            }
        }

        // Current GE price row fallback: only accept it when it is clickable.
        String text = normalize(widget.getText());
        return text.contains("price per item") && hasAnyAction(widget);
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

    private boolean isAllQuantityWidget(Widget widget)
    {
        if (widget == null) return false;
        if ("all".equals(normalize(widget.getText()))) return true;
        String[] actions = widget.getActions();
        if (actions != null)
            for (String action : actions)
            {
                String value = normalize(action);
                if (value.equals("all") || value.contains("set all")
                        || (value.contains("all") && value.contains("quantity"))) return true;
            }
        return false;
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

    private void recordWithdraw(String itemName, boolean success)
    {
        if (itemName == null) return;
        String key = itemName.toLowerCase(Locale.ROOT);
        if (success)
        {
            withdrawFailures.remove(key);
            return;
        }
        int failures = withdrawFailures.merge(key, 1, Integer::sum);
        if (failures >= MAX_WITHDRAW_FAILURES) blockedSellItems.add(key);
    }

    private void recordOfferFailure(String itemName)
    {
        if (itemName == null) return;
        String key = itemName.toLowerCase(Locale.ROOT);
        int failures = offerFailures.merge(key, 1, Integer::sum);
        debug("Offer placement failed | item={} failures={}", itemName, failures);
        if (failures >= MAX_OFFER_FAILURES)
        {
            blockedSellItems.add(key);
            abortOfferSetup();
        }
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
        blockedSellItems.clear();
        withdrawFailures.clear();
        offerFailures.clear();
        tradeRestrictionUnlockedCache = null;
        lastTradeRestrictionCheckAtMs = 0L;
        lastBuyAffordabilityCheckAtMs = 0L;
        cachedBuyAffordability = false;
        super.shutdown();
    }

    public boolean isComplete() { return complete; }
    public GEArea getTargetArea() { return targetArea; }
}
