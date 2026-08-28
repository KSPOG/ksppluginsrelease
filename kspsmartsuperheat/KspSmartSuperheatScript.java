package net.runelite.client.plugins.microbot.kspsmartsuperheat;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.Skill;
import net.runelite.api.widgets.Widget;
import net.runelite.api.gameval.InterfaceID;
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
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class KspSmartSuperheatScript extends Script
{
    private static final int LOOP_MS = 650, BANK_GROUP = 12, BANK_ROOT = 1, GE_PRICE_X_CHILD = 12, MAX_GE_ATTEMPTS = 5;
    private static final String[] FIRE_STAVES = {"Staff of fire", "Fire battlestaff", "Mystic fire staff", "Lava battlestaff", "Mystic lava staff", "Steam battlestaff", "Mystic steam staff", "Smoke battlestaff", "Mystic smoke staff"};

    private KspSmartSuperheatConfig config;
    private final SuperheatPriceService prices = new SuperheatPriceService();
    private volatile SmartSuperheatState state = SmartSuperheatState.STOPPED;
    private volatile String status = "Stopped";
    private volatile SuperheatRecipe activeRecipe;
    private volatile SuperheatQuote activeQuote;
    private volatile boolean freeFireRunes;
    private volatile long startedAt, nextScanAt, barsMade, estimatedProfit, magicXp;
    private volatile double smithingXp;
    private volatile int currentBatchTarget, craftableBarsInBank, spendableCoins;
    private final Map<SuperheatRecipe, Integer> unsold = new EnumMap<>(SuperheatRecipe.class);
    private final Deque<GeOrder> buyQueue = new ArrayDeque<>();
    private GeOrder geOrder;
    private int castFailures;

    public boolean run(KspSmartSuperheatConfig config)
    {
        this.config = config;
        state = SmartSuperheatState.STARTING;
        status = "Starting";
        activeRecipe = null;
        activeQuote = null;
        freeFireRunes = false;
        startedAt = System.currentTimeMillis();
        nextScanAt = barsMade = estimatedProfit = magicXp = 0L;
        smithingXp = 0D;
        currentBatchTarget = craftableBarsInBank = spendableCoins = castFailures = 0;
        unsold.clear();
        buyQueue.clear();
        geOrder = null;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try { if (super.run() && Microbot.isLoggedIn()) tick(); }
            catch (Exception e)
            {
                // Priority plugins pause cooperatively; an already-running bank call may lose its widget mid-action.
                // Treat that as a transient handoff and retry the same state after the pause is released.
                if (Microbot.pauseAllScripts.get()) { status = "Paused for priority plugin"; return; }
                state = SmartSuperheatState.ERROR; status = "Error - check log"; log.error("Smart Superheat tick failed", e);
            }
        }, 0, LOOP_MS, TimeUnit.MILLISECONDS);
        return true;
    }

    public void stopScript() { state = SmartSuperheatState.STOPPED; status = "Stopped"; buyQueue.clear(); geOrder = null; shutdown(); }

    private void tick()
    {
        switch (state)
        {
            case STARTING: start(); break;
            case SCANNING_MARKET: scan(); break;
            case PREPARING_BATCH: prepare(); break;
            case CASTING: cast(); break;
            case SELLING_OUTPUT: sell(); break;
            case RESTOCKING: restock(); break;
            case WAITING_FOR_PROFIT: if (System.currentTimeMillis() >= nextScanAt) state = SmartSuperheatState.SCANNING_MARKET; break;
            default: break;
        }
    }

    private void start()
    {
        if (level(Skill.MAGIC) < 43) { state = SmartSuperheatState.ERROR; status = "43 Magic required"; return; }
        state = SmartSuperheatState.SCANNING_MARKET;
        status = "Scanning profitable recipes";
    }

    private void scan()
    {
        int smithing = level(Skill.SMITHING);
        freeFireRunes = freeFire();
        SuperheatQuote best = null;
        for (SuperheatRecipe r : SuperheatRecipe.values())
        {
            if (r.getSmithingLevel() > smithing || (r.isMembersOnly() && !members())) continue;
            SuperheatQuote q = prices.quote(r, config, freeFireRunes);
            if (q.meets(config) && (best == null || q.getProjectedGpHour() > best.getProjectedGpHour())) best = q;
        }
        if (best == null)
        {
            activeRecipe = null;
            activeQuote = null;
            state = SmartSuperheatState.WAITING_FOR_PROFIT;
            status = "No profitable recipe";
            nextScanAt = System.currentTimeMillis() + Math.max(15, config.priceRefreshSeconds()) * 1000L;
            return;
        }
        activeQuote = best;
        activeRecipe = best.getRecipe();
        nextScanAt = System.currentTimeMillis() + Math.max(15, config.priceRefreshSeconds()) * 1000L;
        state = SmartSuperheatState.PREPARING_BATCH;
        status = "Selected " + activeRecipe.getOutputName();
    }

    private void prepare()
    {
        if (!recipeReady()) return;
        if (System.currentTimeMillis() >= nextScanAt) { state = SmartSuperheatState.SCANNING_MARKET; return; }
        freeFireRunes = freeFire();
        activeQuote = prices.quote(activeRecipe, config, freeFireRunes);
        if (!activeQuote.meets(config)) { state = SmartSuperheatState.SCANNING_MARKET; status = "Margin changed"; return; }
        if (!ensureBank() || !bankMode(true)) return;
        cleanInventory();
        if (!allNatureRunes()) { state = SmartSuperheatState.RESTOCKING; status = "Need Nature runes"; return; }

        craftableBarsInBank = craftable(activeRecipe);
        if (craftableBarsInBank <= 0) { state = SmartSuperheatState.RESTOCKING; status = "Restocking ingredients"; return; }

        int reservedFireSlot = !freeFireRunes && Rs2Inventory.itemQuantity(ItemID.FIRERUNE) == 0 ? 1 : 0;
        int bySlots = Math.max(0, Rs2Inventory.emptySlotCount() - reservedFireSlot) / Math.max(1, activeRecipe.getMaterialSlotsPerBar());
        int batch = Math.min(Math.min(activeQuote.getBatchSize(), craftableBarsInBank), bySlots);
        if (batch <= 0) { status = "Not enough inventory space"; return; }
        currentBatchTarget = batch;

        if (!ensureQty(activeRecipe.getPrimaryOreId(), batch * activeRecipe.getPrimaryOrePerBar(), activeRecipe.getPrimaryOreName())) return;
        if (activeRecipe.hasSecondaryOre() && !ensureQty(activeRecipe.getSecondaryOreId(), batch * activeRecipe.getSecondaryOrePerBar(), activeRecipe.getSecondaryOreName())) return;
        if (activeRecipe.getCoalPerBar() > 0 && !ensureQty(ItemID.COAL, batch * activeRecipe.getCoalPerBar(), "Coal")) return;
        if (!freeFireRunes && !ensureQty(ItemID.FIRERUNE, batch * 4, "Fire rune")) return;
        if (!batchReady(batch)) { status = "Batch verification failed"; return; }

        Rs2Bank.closeBank();
        if (!sleepUntil(() -> !Rs2Bank.isOpen(), 3000)) return;
        castFailures = 0;
        state = SmartSuperheatState.CASTING;
        status = "Superheating " + activeRecipe.getOutputName();
    }

    private void cast()
    {
        if (!recipeReady()) return;
        freeFireRunes = freeFire();
        if (!castReady()) { state = SmartSuperheatState.PREPARING_BATCH; status = "Banking batch"; return; }
        int beforeBar = Rs2Inventory.itemQuantity(activeRecipe.getOutputId());
        int beforeOre = Rs2Inventory.itemQuantity(activeRecipe.getPrimaryOreId());
        status = "Casting on " + activeRecipe.getPrimaryOreName();
        Rs2Magic.superHeat(activeRecipe.getPrimaryOreId(), config.castDelayMinMs(), config.castDelayMaxMs());
        if (!sleepUntil(() -> Rs2Inventory.itemQuantity(activeRecipe.getOutputId()) > beforeBar || Rs2Inventory.itemQuantity(activeRecipe.getPrimaryOreId()) < beforeOre, 4500))
        {
            if (++castFailures >= 3) { castFailures = 0; state = SmartSuperheatState.PREPARING_BATCH; status = "Recovering through bank"; }
            else status = "Cast did not register (" + castFailures + "/3)";
            return;
        }
        castFailures = 0;
        barsMade++;
        magicXp += 53;
        smithingXp += activeRecipe.getSmithingXp();
        estimatedProfit += activeQuote.getProfitPerBar();
        unsold.merge(activeRecipe, 1, Integer::sum);
        if (!castReady()) { state = SmartSuperheatState.PREPARING_BATCH; status = "Batch complete"; }
    }

    private void restock()
    {
        if (!recipeReady()) return;
        if (geOrder != null || !buyQueue.isEmpty()) { tickBuyQueue(); return; }
        freeFireRunes = freeFire();
        activeQuote = prices.quote(activeRecipe, config, freeFireRunes);
        if (!activeQuote.meets(config)) { state = SmartSuperheatState.SCANNING_MARKET; status = "Recipe no longer profitable"; return; }
        if (!ensureBank() || !bankMode(true)) return;
        cleanInventory();
        allNatureRunes();
        craftableBarsInBank = craftable(activeRecipe);
        if (craftableBarsInBank > 0) { state = SmartSuperheatState.PREPARING_BATCH; status = "Using banked ingredients"; return; }

        long coins = total(ItemID.COINS);
        long spendable = Math.max(0L, coins - config.cashReserve());
        spendableCoins = (int) Math.min(Integer.MAX_VALUE, spendable);
        long budget = spendable * config.maxSpendPercent() / 100L;
        if (budget < activeQuote.getInputCostPerBar())
        {
            if (config.autoSellOutput() && getUnsoldProduced() > 0) { state = SmartSuperheatState.SELLING_OUTPUT; status = "Selling bars to fund restock"; }
            else waitForCash();
            return;
        }

        if (Rs2Bank.count(ItemID.COINS) > 0)
        {
            int before = Rs2Inventory.itemQuantity(ItemID.COINS);
            status = "Withdrawing coins";
            if (!Rs2Bank.withdrawAll(ItemID.COINS) || !sleepUntil(() -> Rs2Inventory.itemQuantity(ItemID.COINS) > before, 5000)) return;
        }

        int target = (int) Math.min(1000L, budget / Math.max(1, activeQuote.getInputCostPerBar()));
        buildBuyQueue(Math.max(1, target));
        if (buyQueue.isEmpty()) { state = SmartSuperheatState.PREPARING_BATCH; return; }
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 3000);
        status = "Restocking " + buyQueue.size() + " input(s)";
    }

    private void sell()
    {
        if (!recipeReady()) return;
        if (geOrder != null) { tickGeOrder(); return; }
        int wanted = unsold.getOrDefault(activeRecipe, 0);
        if (wanted <= 0) { state = SmartSuperheatState.RESTOCKING; return; }
        if (!ensureBank()) return;
        if (Rs2Inventory.itemQuantity(activeRecipe.getOutputId()) > 0)
        {
            Rs2Bank.depositAll(activeRecipe.getOutputName(), true);
            if (!sleepUntil(() -> Rs2Inventory.itemQuantity(activeRecipe.getOutputId()) == 0, 3000)) return;
        }
        if (!bankMode(false)) return;
        int bankQty = Rs2Bank.count(activeRecipe.getOutputId());
        if (bankQty <= 0) { unsold.put(activeRecipe, 0); state = SmartSuperheatState.RESTOCKING; return; }
        status = "Withdrawing all noted " + activeRecipe.getOutputName();
        if (!Rs2Bank.withdrawAll(activeRecipe.getOutputName(), true)
            || !sleepUntil(() -> Rs2Inventory.itemQuantity(activeRecipe.getOutputName(), true) >= bankQty, 5000)) return;
        int qty = Rs2Inventory.itemQuantity(activeRecipe.getOutputName(), true);
        if (qty <= 0) { status = "Noted output withdrawal failed"; return; }
        Rs2Bank.closeBank();
        if (!sleepUntil(() -> !Rs2Bank.isOpen(), 3000)) return;
        activeQuote = prices.quote(activeRecipe, config, freeFireRunes);
        int price = activeQuote.isValid() ? activeQuote.getOutputSellPrice() : 0;
        if (price <= 0) { status = "No sell price"; return; }
        geOrder = new GeOrder(GrandExchangeAction.SELL, activeRecipe.getOutputId(), activeRecipe.getOutputName(), qty, price);
        tickGeOrder();
    }

    private void tickBuyQueue()
    {
        if (geOrder == null) geOrder = buyQueue.peekFirst();
        if (geOrder == null) { state = SmartSuperheatState.PREPARING_BATCH; return; }
        tickGeOrder();
    }

    private void tickGeOrder()
    {
        GeOrder o = geOrder;
        if (o == null || !ensureGeOverview()) return;
        GrandExchangeSlots recovered = findOffer(o.itemId, o.action);
        if (recovered != null) { o.slot = recovered; if (o.placedAt == 0) o.placedAt = System.currentTimeMillis(); }
        if (o.slot == null) { placeOrder(o); return; }

        OfferSnapshot s = offer(o.slot);
        if (s == null || s.itemId != o.itemId) { o.slot = null; o.placedAt = 0; return; }
        boolean done = (o.action == GrandExchangeAction.BUY && s.state == GrandExchangeOfferState.BOUGHT)
            || (o.action == GrandExchangeAction.SELL && s.state == GrandExchangeOfferState.SOLD);
        if (done)
        {
            int filled = s.filled > 0 ? s.filled : o.quantity;
            if (!collectCompleted()) return;
            finishOrder(o, filled, false);
            return;
        }
        if (s.state == GrandExchangeOfferState.EMPTY) { o.slot = null; o.placedAt = 0; return; }
        status = (o.action == GrandExchangeAction.BUY ? "Buying " : "Selling ") + o.itemName + " (slot " + (o.slot.ordinal() + 1) + ")";
        if (o.placedAt > 0 && System.currentTimeMillis() - o.placedAt >= config.geOfferTimeoutSeconds() * 1000L) modifyOrder(o);
    }

    private void placeOrder(GeOrder o)
    {
        if (++o.attempts > MAX_GE_ATTEMPTS) { o.attempts = 1; status = "GE retrying cleanly: " + o.itemName; sleep(1500); }
        GrandExchangeRequest request;
        if (o.action == GrandExchangeAction.BUY)
        {
            o.slot = freeSlot();
            if (o.slot == null) { status = "Waiting for free GE slot"; return; }
            request = GrandExchangeRequest.builder().slot(o.slot).action(o.action).itemName(o.itemName).exact(true).quantity(o.quantity).price(o.price).closeAfterCompletion(false).build();
        }
        else request = GrandExchangeRequest.builder().action(o.action).itemName(o.itemName).exact(true).quantity(o.quantity).price(o.price).closeAfterCompletion(false).build();

        status = (o.action == GrandExchangeAction.BUY ? "Placing buy: " : "Placing sell: ") + o.itemName;
        o.placedAt = System.currentTimeMillis();
        if (!Rs2GrandExchange.processOffer(request))
        {
            o.slot = null;
            o.placedAt = 0;
            status = "GE placement failed - recovering " + o.itemName;
            return;
        }
        sleepUntil(() -> findOffer(o.itemId, o.action) != null || !Rs2GrandExchange.isOpen(), 3000);
        o.slot = findOffer(o.itemId, o.action);
        if (o.slot == null) status = "Waiting for GE slot confirmation: " + o.itemName;
    }

    private void modifyOrder(GeOrder o)
    {
        if (o.slot == null) { o.placedAt = 0; return; }
        if (o.retry >= MAX_GE_ATTEMPTS)
        {
            o.placedAt = System.currentTimeMillis();
            status = "Waiting at final " + (o.action == GrandExchangeAction.BUY ? "buy" : "sell") + " price: " + o.itemName;
            return;
        }
        int next = o.retry + 1;
        int price = o.action == GrandExchangeAction.BUY
            ? prices.buyOfferPrice(o.itemId, config.buyMarkupPercent(), next)
            : prices.sellOfferPrice(o.itemId, config.sellDiscountPercent(), next);
        if (price <= 0 || price == o.price)
        {
            o.placedAt = System.currentTimeMillis();
            status = "No updated GE price - keeping offer: " + o.itemName;
            return;
        }
        if (!modifyGeOffer(o.slot, price)) return;
        o.retry = next;
        o.price = price;
        o.placedAt = System.currentTimeMillis();
        status = "Modified " + (o.action == GrandExchangeAction.BUY ? "buy" : "sell") + " price: " + o.itemName + " (retry " + o.retry + ")";
    }

    private boolean modifyGeOffer(GrandExchangeSlots slot, int newPrice)
    {
        if (slot == null || newPrice <= 0) return false;
        OfferSnapshot current = offer(slot);
        if (current != null && current.price == newPrice) return true;
        if (!ensureGeOverview()) return false;

        Widget slotWidget = Rs2Widget.getWidget(InterfaceID.GeOffers.INDEX_0 + slot.ordinal());
        if (slotWidget == null)
        {
            status = "Waiting for GE slot widget";
            return false;
        }

        status = "Opening GE Modify offer";
        Rs2Widget.clickWidgetFast(slotWidget, 2, 3);
        if (!sleepUntil(() -> geSetupOpen() || Rs2GrandExchange.isOfferScreenOpen() || !Rs2GrandExchange.isOpen(), 3500))
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
            Widget modify = Rs2Widget.getWidget(InterfaceID.GeOffers.DETAILS_MODIFY);
            if (modify == null || !Rs2Widget.clickWidget(modify))
            {
                status = "Waiting for GE Modify button";
                return false;
            }
            if (!sleepUntil(() -> geSetupOpen() || !Rs2GrandExchange.isOpen(), 3000))
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

        Widget setup = Rs2Widget.getWidget(InterfaceID.GeOffers.SETUP);
        Widget priceX = setup == null ? null : setup.getChild(GE_PRICE_X_CHILD);
        if (priceX == null || !Rs2Widget.clickWidget(priceX))
        {
            status = "Unable to open GE price input";
            return false;
        }
        if (!sleepUntil(() -> gePriceInputOpen() || !Rs2GrandExchange.isOpen(), 2500))
        {
            status = "Waiting for GE price input";
            return false;
        }
        if (!Rs2GrandExchange.isOpen())
        {
            status = "GE closed during price input - recovering";
            return false;
        }

        // Same chatbox-value flow as Jewellery Crafter, with the settle delays
        // Microbot's own GE setPrice() uses so the new value is not submitted too early.
        sleep(600, 1000);
        Rs2GrandExchange.setChatboxValue(newPrice);
        sleep(500, 750);
        Rs2Keyboard.enter();
        sleep(800, 1100);
        if (!sleepUntil(() -> !gePriceInputOpen() || !Rs2GrandExchange.isOpen(), 2500))
        {
            status = "Waiting for GE price entry";
            return false;
        }
        if (!Rs2GrandExchange.isOpen())
        {
            status = "GE closed after price entry - recovering";
            return false;
        }

        status = "Confirming modified GE price";
        if (!Rs2Widget.clickWidget(InterfaceID.GeOffers.SETUP_CONFIRM))
        {
            status = "Unable to confirm modified GE price";
            return false;
        }
        sleepUntil(() -> !geSetupOpen() || Rs2Widget.hasWidget("Your offer is much") || !Rs2GrandExchange.isOpen(), 3000);
        if (Rs2Widget.hasWidget("Your offer is much"))
        {
            Rs2Widget.clickWidget("Yes");
            sleepUntil(() -> !geSetupOpen() || !Rs2GrandExchange.isOpen(), 3000);
        }

        if (sleepUntil(() ->
        {
            OfferSnapshot snapshot = offer(slot);
            return snapshot != null && snapshot.price == newPrice;
        }, 3000)) return true;

        status = Rs2GrandExchange.isOpen() ? "Waiting for modified GE price" : "GE closed after price modify - recovering";
        return false;
    }

    private boolean geSetupOpen() { return Rs2Widget.isWidgetVisible(InterfaceID.GeOffers.SETUP); }

    private boolean gePriceInputOpen() { return Rs2Widget.getWidget(InterfaceID.Chatbox.MES_TEXT2) != null; }

    private void finishOrder(GeOrder o, int filled, boolean partial)
    {
        geOrder = null;
        if (o.action == GrandExchangeAction.SELL)
        {
            unsold.put(activeRecipe, Math.max(0, unsold.getOrDefault(activeRecipe, 0) - filled));
            state = getUnsoldProduced() > 0 ? SmartSuperheatState.SELLING_OUTPUT : SmartSuperheatState.RESTOCKING;
            status = partial ? "Partial sale collected" : "Bars sold";
            return;
        }
        if (partial) buyQueue.clear();
        else buyQueue.pollFirst();
        state = buyQueue.isEmpty() ? SmartSuperheatState.PREPARING_BATCH : SmartSuperheatState.RESTOCKING;
        status = partial ? "Partial buy collected - replanning" : "Buy collected";
    }

    private void buildBuyQueue(int bars)
    {
        buyQueue.clear();
        addBuy(activeRecipe.getPrimaryOreId(), activeRecipe.getPrimaryOreName(), bars * activeRecipe.getPrimaryOrePerBar(), activeQuote.getPrimaryBuyPrice());
        if (activeRecipe.hasSecondaryOre()) addBuy(activeRecipe.getSecondaryOreId(), activeRecipe.getSecondaryOreName(), bars * activeRecipe.getSecondaryOrePerBar(), activeQuote.getSecondaryBuyPrice());
        if (activeRecipe.getCoalPerBar() > 0) addBuy(ItemID.COAL, "Coal", bars * activeRecipe.getCoalPerBar(), activeQuote.getCoalBuyPrice());
        addBuy(ItemID.NATURERUNE, "Nature rune", bars, activeQuote.getNatureBuyPrice());
        if (!freeFireRunes) addBuy(ItemID.FIRERUNE, "Fire rune", bars * 4, activeQuote.getFireBuyPrice());
    }

    private void addBuy(int id, String name, int target, int price)
    {
        int missing = Math.max(0, target - (int) Math.min(Integer.MAX_VALUE, total(id)));
        if (missing > 0 && price > 0) buyQueue.addLast(new GeOrder(GrandExchangeAction.BUY, id, name, missing, price));
    }

    private boolean ensureBank()
    {
        if (bankOpen()) return true;
        if (Rs2GrandExchange.isOpen()) { Rs2GrandExchange.closeExchange(); return false; }
        status = "Opening bank";
        if (!Rs2Bank.openBank()) return false;
        return sleepUntil(this::bankOpen, 5000);
    }

    private boolean bankOpen()
    {
        return Rs2Bank.isOpen() && Rs2Widget.isWidgetVisible(BANK_GROUP, BANK_ROOT) && Rs2Widget.getWidget(InterfaceID.Bankmain.NOTE) != null;
    }

    private boolean bankMode(boolean itemMode)
    {
        if (!bankOpen()) return false;
        if (Rs2Bank.hasWithdrawAsItem() == itemMode) return true;
        status = itemMode ? "Setting item withdrawal" : "Setting noted withdrawal";
        if (!Rs2Widget.clickWidget(InterfaceID.Bankmain.NOTE)) return false;
        return sleepUntil(() -> Rs2Bank.hasWithdrawAsItem() == itemMode, 3000);
    }

    private void cleanInventory()
    {
        if (config.bankWholeInventory())
        {
            if (freeFireRunes) Rs2Bank.depositAllExcept(true, "Nature rune");
            else Rs2Bank.depositAllExcept(true, "Nature rune", "Fire rune");
            sleep(180, 300);
            return;
        }
        deposit(activeRecipe.getOutputName());
        deposit(activeRecipe.getPrimaryOreName());
        if (activeRecipe.hasSecondaryOre()) deposit(activeRecipe.getSecondaryOreName());
        if (activeRecipe.getCoalPerBar() > 0) deposit("Coal");
        deposit("Coins");
        if (freeFireRunes) deposit("Fire rune");
    }

    private void deposit(String name)
    {
        if (name != null && Rs2Inventory.itemQuantity(name, true) > 0) { Rs2Bank.depositAll(name, true); sleep(80, 140); }
    }

    private boolean allNatureRunes()
    {
        if (!bankActionReady()) return false;
        int bank = Rs2Bank.count(ItemID.NATURERUNE), inventory = Rs2Inventory.itemQuantity(ItemID.NATURERUNE), total = bank + inventory;
        if (total <= 0) return false;
        if (bank <= 0) return true;
        status = "Withdrawing all Nature runes";
        try
        {
            return bankActionReady() && Rs2Bank.withdrawAll(ItemID.NATURERUNE)
                    && sleepUntil(() -> Rs2Inventory.itemQuantity(ItemID.NATURERUNE) >= total, 5000);
        }
        catch (RuntimeException e)
        {
            if (Microbot.pauseAllScripts.get() || !Rs2Bank.isOpen()) { status = "Bank handoff interrupted - recovering"; return false; }
            throw e;
        }
    }

    private boolean ensureQty(int id, int target, String name)
    {
        if (!bankActionReady()) return false;
        int have = Rs2Inventory.itemQuantity(id);
        if (have >= target) return true;
        int need = target - have;
        if (Rs2Bank.count(id) < need) { status = "Not enough " + name; return false; }
        status = "Withdrawing " + need + " x " + name;
        try
        {
            return bankActionReady() && Rs2Bank.withdrawX(id, need)
                    && sleepUntil(() -> Rs2Inventory.itemQuantity(id) >= target, 4500);
        }
        catch (RuntimeException e)
        {
            if (Microbot.pauseAllScripts.get() || !Rs2Bank.isOpen()) { status = "Bank handoff interrupted - recovering"; return false; }
            throw e;
        }
    }

    private boolean bankActionReady()
    {
        if (Microbot.pauseAllScripts.get()) { status = "Paused for priority plugin"; return false; }
        if (!Rs2Bank.isOpen()) { status = "Bank closed - recovering"; return false; }
        return true;
    }

    private boolean batchReady(int bars)
    {
        return Rs2Inventory.itemQuantity(activeRecipe.getPrimaryOreId()) >= bars * activeRecipe.getPrimaryOrePerBar()
            && (!activeRecipe.hasSecondaryOre() || Rs2Inventory.itemQuantity(activeRecipe.getSecondaryOreId()) >= bars * activeRecipe.getSecondaryOrePerBar())
            && (activeRecipe.getCoalPerBar() == 0 || Rs2Inventory.itemQuantity(ItemID.COAL) >= bars * activeRecipe.getCoalPerBar())
            && Rs2Inventory.itemQuantity(ItemID.NATURERUNE) >= bars
            && (freeFireRunes || Rs2Inventory.itemQuantity(ItemID.FIRERUNE) >= bars * 4);
    }

    private boolean castReady()
    {
        return activeRecipe != null
            && Rs2Inventory.itemQuantity(activeRecipe.getPrimaryOreId()) >= activeRecipe.getPrimaryOrePerBar()
            && (!activeRecipe.hasSecondaryOre() || Rs2Inventory.itemQuantity(activeRecipe.getSecondaryOreId()) >= activeRecipe.getSecondaryOrePerBar())
            && (activeRecipe.getCoalPerBar() == 0 || Rs2Inventory.itemQuantity(ItemID.COAL) >= activeRecipe.getCoalPerBar())
            && Rs2Inventory.itemQuantity(ItemID.NATURERUNE) > 0
            && (freeFireRunes || Rs2Inventory.itemQuantity(ItemID.FIRERUNE) >= 4);
    }

    private int craftable(SuperheatRecipe r)
    {
        if (r == null) return 0;
        long n = total(r.getPrimaryOreId()) / r.getPrimaryOrePerBar();
        if (r.hasSecondaryOre()) n = Math.min(n, total(r.getSecondaryOreId()) / r.getSecondaryOrePerBar());
        if (r.getCoalPerBar() > 0) n = Math.min(n, total(ItemID.COAL) / r.getCoalPerBar());
        n = Math.min(n, total(ItemID.NATURERUNE));
        if (!freeFireRunes) n = Math.min(n, total(ItemID.FIRERUNE) / 4);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, n));
    }

    private long total(int id) { return Math.max(0, Rs2Bank.count(id)) + (long) Rs2Inventory.itemQuantity(id); }
    private boolean recipeReady() { if (activeRecipe != null && activeQuote != null) return true; state = SmartSuperheatState.SCANNING_MARKET; return false; }
    private void waitForCash() { state = SmartSuperheatState.WAITING_FOR_PROFIT; status = "Insufficient spendable cash"; nextScanAt = System.currentTimeMillis() + 10_000L; }
    private int level(Skill s) { return Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getRealSkillLevel(s)).orElse(0); }
    private boolean members() { try { return Rs2Player.isMember() && Rs2Player.isInMemberWorld(); } catch (Exception e) { return false; } }
    private boolean freeFire() { try { return Rs2Equipment.isWearing(FIRE_STAVES); } catch (Exception e) { return false; } }

    private boolean ensureGeOverview()
    {
        if (!Rs2GrandExchange.isOpen())
        {
            status = "Opening Grand Exchange";
            if (!Rs2GrandExchange.openExchange() || !sleepUntil(Rs2GrandExchange::isOpen, 5000)) return false;
        }
        if (!geSubScreen()) return true;
        status = "Returning to GE overview";
        Rs2GrandExchange.backToOverview();
        return sleepUntil(() -> Rs2GrandExchange.isOpen() && !geSubScreen(), 4000);
    }

    private boolean geSubScreen() { return Rs2GrandExchange.isOfferScreenOpen() || Rs2Widget.isWidgetVisible(InterfaceID.GeOffers.SETUP); }

    private GrandExchangeSlots freeSlot()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            int max = Math.min(members() ? 8 : 3, GrandExchangeSlots.values().length);
            for (int i = 0; i < max; i++) if (offers == null || i >= offers.length || offers[i] == null || offers[i].getState() == GrandExchangeOfferState.EMPTY) return GrandExchangeSlots.values()[i];
            return null;
        }).orElse(null);
    }

    private GrandExchangeSlots findOffer(int itemId, GrandExchangeAction action)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            if (offers == null) return null;
            int max = Math.min(offers.length, GrandExchangeSlots.values().length);
            for (int i = 0; i < max; i++)
            {
                GrandExchangeOffer o = offers[i];
                if (o == null || o.getItemId() != itemId) continue;
                GrandExchangeOfferState s = o.getState();
                if (action == GrandExchangeAction.BUY && (s == GrandExchangeOfferState.BUYING || s == GrandExchangeOfferState.BOUGHT)) return GrandExchangeSlots.values()[i];
                if (action == GrandExchangeAction.SELL && (s == GrandExchangeOfferState.SELLING || s == GrandExchangeOfferState.SOLD)) return GrandExchangeSlots.values()[i];
            }
            return null;
        }).orElse(null);
    }

    private OfferSnapshot offer(GrandExchangeSlots slot)
    {
        if (slot == null) return null;
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            int i = slot.ordinal();
            if (offers == null || i >= offers.length || offers[i] == null) return new OfferSnapshot(0, GrandExchangeOfferState.EMPTY, 0, 0);
            GrandExchangeOffer o = offers[i];
            return new OfferSnapshot(o.getItemId(), o.getState(), o.getQuantitySold(), o.getPrice());
        }).orElse(null);
    }

    private boolean offerActive(OfferSnapshot s, GrandExchangeAction a)
    {
        return s != null && ((a == GrandExchangeAction.BUY && s.state == GrandExchangeOfferState.BUYING) || (a == GrandExchangeAction.SELL && s.state == GrandExchangeOfferState.SELLING));
    }

    private boolean hasCompleted()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            if (offers != null) for (GrandExchangeOffer o : offers) if (o != null && (o.getState() == GrandExchangeOfferState.BOUGHT || o.getState() == GrandExchangeOfferState.SOLD)) return true;
            return false;
        }).orElse(false);
    }

    private boolean collectCompleted()
    {
        if (!hasCompleted()) return true;
        if (!ensureGeOverview() || !Rs2GrandExchange.collectAllToBank()) return false;
        return sleepUntil(() -> !hasCompleted(), 5000);
    }

    public SmartSuperheatState getState() { return state; }
    public String getStatus() { return status; }
    public SuperheatRecipe getActiveRecipe() { return activeRecipe; }
    public SuperheatQuote getActiveQuote() { return activeQuote; }
    public boolean hasFreeFireRunes() { return freeFireRunes; }
    public long getBarsMade() { return barsMade; }
    public long getBarsPerHour() { long r = getRuntimeMillis(); return r <= 0 ? 0 : barsMade * 3_600_000L / r; }
    public long getEstimatedProfit() { return estimatedProfit; }
    public long getEstimatedProfitPerHour() { long r = getRuntimeMillis(); return r <= 0 ? 0 : estimatedProfit * 3_600_000L / r; }
    public int getUnsoldProduced() { int n = 0; for (int v : unsold.values()) n += v; return n; }
    public int getCraftableBarsInBank() { return craftableBarsInBank; }
    public int getCurrentBatchTarget() { return currentBatchTarget; }
    public int getSpendableCoins() { return spendableCoins; }
    public long getMagicXp() { return magicXp; }
    public double getSmithingXp() { return smithingXp; }
    public long getRuntimeMillis() { return startedAt <= 0 ? 0 : System.currentTimeMillis() - startedAt; }

    private static final class GeOrder
    {
        final GrandExchangeAction action; final int itemId, quantity; final String itemName; int price;
        GrandExchangeSlots slot; long placedAt; int attempts, retry;
        GeOrder(GrandExchangeAction action, int itemId, String itemName, int quantity, int price) { this.action = action; this.itemId = itemId; this.itemName = itemName; this.quantity = quantity; this.price = price; }
    }

    private static final class OfferSnapshot
    {
        final int itemId, filled, price; final GrandExchangeOfferState state;
        OfferSnapshot(int itemId, GrandExchangeOfferState state, int filled, int price) { this.itemId = itemId; this.state = state; this.filled = filled; this.price = price; }
    }
}
