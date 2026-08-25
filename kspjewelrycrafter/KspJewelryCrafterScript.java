package net.runelite.client.plugins.microbot.kspjewelrycrafter;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeAction;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeRequest;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class KspJewelryCrafterScript extends Script
{
    private static final int LOOP_MS = 650;
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
    private volatile long craftedCount;
    private volatile long estimatedProfit;
    private volatile long sessionStartedAt;
    private volatile int startingCraftingXp;
    private volatile int currentCraftingXp;
    private volatile int lastBatchMade;
    private volatile int currentBatchTarget;

    private PendingOffer pendingOffer;
    private final List<BuyOrder> buyQueue = new ArrayList<>();
    private int buyIndex;
    private int geRetry;
    private String outputPendingSale;
    private long waitingUntil;

    public JewelryCrafterState getState() { return state; }
    public String getStatus() { return status; }
    public JewelryRecipe getActiveRecipe() { return activeRecipe; }
    public JewelryQuote getActiveQuote() { return activeQuote; }
    public boolean isMemberAccount() { return memberAccount; }
    public int getCraftingLevel() { return craftingLevel; }
    public long getCraftedCount() { return craftedCount; }
    public long getEstimatedProfit() { return estimatedProfit; }
    public long getSessionStartedAt() { return sessionStartedAt; }
    public int getStartingCraftingXp() { return startingCraftingXp; }
    public int getCurrentCraftingXp() { return currentCraftingXp; }
    public int getLastBatchMade() { return lastBatchMade; }
    public int getCurrentBatchTarget() { return currentBatchTarget; }

    public boolean run(KspJewelryCrafterConfig config)
    {
        this.config = config;
        this.state = JewelryCrafterState.STARTING;
        this.status = "Starting";
        this.activeRecipe = null;
        this.activeQuote = null;
        this.pendingOffer = null;
        this.buyQueue.clear();
        this.buyIndex = 0;
        this.geRetry = 0;
        this.outputPendingSale = null;
        this.waitingUntil = 0L;
        this.craftedCount = 0L;
        this.estimatedProfit = 0L;
        this.sessionStartedAt = 0L;
        this.startingCraftingXp = 0;
        this.currentCraftingXp = 0;
        this.lastBatchMade = 0;
        this.currentBatchTarget = 0;

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
        startingCraftingXp = currentCraftingXp;
        state = JewelryCrafterState.EVALUATING;
        status = "Evaluating profitable recipes";
    }

    private void refreshAccountEligibility()
    {
        craftingLevel = Microbot.getClientThread().runOnClientThreadOptional(
            () -> Microbot.getClient().getRealSkillLevel(Skill.CRAFTING)).orElse(1);
        currentCraftingXp = Microbot.getClientThread().runOnClientThreadOptional(
            () -> Microbot.getClient().getSkillExperience(Skill.CRAFTING)).orElse(currentCraftingXp);
        try { memberAccount = Rs2Player.isMember(); }
        catch (Exception ex) { memberAccount = false; }
    }

    private void evaluate()
    {
        refreshAccountEligibility();
        JewelryRecipe selected = chooseRecipe();
        if (selected == null)
        {
            pause("No eligible profitable jewellery recipe", 10_000L);
            return;
        }

        JewelryQuote quote = prices.quote(selected, config);
        if (!quote.meets(config))
        {
            pause("Selected recipe no longer meets profit floor", 10_000L);
            return;
        }

        activeRecipe = selected;
        activeQuote = quote;
        state = JewelryCrafterState.BANKING;
        status = "Preparing " + selected.getOutputName();
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
            JewelryQuote q = prices.quote(fixed, config);
            return q.meets(config) ? fixed : null;
        }

        Comparator<JewelryQuote> comparator;
        switch (config.selectionMode())
        {
            case BEST_ROI:
                comparator = Comparator.comparingDouble(JewelryQuote::getRoi);
                break;
            case HIGHEST_LEVEL_PROFITABLE:
                comparator = Comparator.comparingInt(q -> q.getRecipe().getCraftingLevel());
                break;
            case BEST_PROFIT:
            default:
                comparator = Comparator.comparingInt(JewelryQuote::getProfit);
                break;
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
            return;
        }

        JewelryQuote latest = prices.quote(activeRecipe, config);
        if (!latest.meets(config))
        {
            activeQuote = latest;
            if (Rs2Bank.isOpen()) Rs2Bank.depositAll();
            state = JewelryCrafterState.EVALUATING;
            status = "Margin changed - re-evaluating";
            return;
        }
        activeQuote = latest;

        if (distanceTo(EDGEVILLE_BANK) > 8)
        {
            status = "Walking to Edgeville bank";
            Rs2Walker.walkTo(EDGEVILLE_BANK, 5);
            return;
        }

        if (!Rs2Bank.isOpen() && !Rs2Bank.openBank())
        {
            status = "Opening Edgeville bank";
            return;
        }
        if (!Rs2Bank.setWithdrawAsItem())
        {
            status = "Setting bank withdraw mode";
            return;
        }

        if (!Rs2Inventory.isEmpty())
        {
            Rs2Bank.depositAll();
            sleepUntil(Rs2Inventory::isEmpty, 4_000);
        }

        int bars = Rs2Bank.count(activeRecipe.getBarName(), true);
        int gems = activeRecipe.usesGem() ? Rs2Bank.count(activeRecipe.getGemName(), true) : Integer.MAX_VALUE;
        int available = Math.min(bars, gems);
        boolean hasMould = Rs2Bank.count(activeRecipe.getMouldName(), true) > 0;

        if (!hasMould || available <= 0)
        {
            outputPendingSale = activeRecipe.getOutputName();
            state = JewelryCrafterState.TRAVEL_TO_GE;
            status = !hasMould ? "Need mould - going to GE" : "Out of inputs - going to GE";
            Rs2Bank.closeBank();
            return;
        }

        int perInventory = activeRecipe.usesGem() ? 13 : 27;
        int craftUnits = Math.min(perInventory, available);
        if (!Rs2Bank.withdrawX(activeRecipe.getMouldName(), 1, true))
        {
            status = "Withdrawing " + activeRecipe.getMouldName();
            return;
        }
        if (!Rs2Bank.withdrawX(activeRecipe.getBarName(), craftUnits, true))
        {
            status = "Withdrawing " + activeRecipe.getBarName();
            return;
        }
        if (activeRecipe.usesGem() && !Rs2Bank.withdrawX(activeRecipe.getGemName(), craftUnits, true))
        {
            status = "Withdrawing " + activeRecipe.getGemName();
            return;
        }

        Rs2Bank.closeBank();
        state = JewelryCrafterState.CRAFTING;
        status = "Walking to furnace";
    }

    private void craftInventory()
    {
        if (activeRecipe == null)
        {
            state = JewelryCrafterState.EVALUATING;
            return;
        }

        JewelryQuote latest = prices.quote(activeRecipe, config);
        if (!latest.meets(config))
        {
            activeQuote = latest;
            state = JewelryCrafterState.BANKING;
            status = "No longer profitable - banking inputs";
            return;
        }
        activeQuote = latest;

        int barsBefore = Rs2Inventory.count(activeRecipe.getBarName(), true);
        int gemsBefore = activeRecipe.usesGem() ? Rs2Inventory.count(activeRecipe.getGemName(), true) : barsBefore;
        int expected = Math.min(barsBefore, gemsBefore);
        currentBatchTarget = Math.max(0, expected);
        if (expected <= 0)
        {
            state = JewelryCrafterState.BANKING;
            return;
        }

        if (distanceTo(EDGEVILLE_FURNACE) > 6)
        {
            status = "Walking to Edgeville furnace";
            Rs2Walker.walkTo(EDGEVILLE_FURNACE, 3);
            return;
        }

        int outputId = prices.getItemId(activeRecipe.getOutputName());
        int outputBefore = outputId <= 0 ? 0 : Rs2Inventory.count(outputId);

        status = "Opening jewellery furnace interface";
        if (!Rs2Inventory.use(activeRecipe.getBarName()))
        {
            status = "Unable to select " + activeRecipe.getBarName();
            return;
        }
        sleep(120, 220);
        if (!Rs2GameObject.interact("Furnace"))
        {
            status = "Unable to use furnace";
            return;
        }
        if (!sleepUntil(this::isProductionOpen, 4_000))
        {
            status = "Waiting for jewellery interface";
            return;
        }

        if (!selectProduct(outputId, activeRecipe.getOutputName()))
        {
            status = "Unable to select " + activeRecipe.getOutputName();
            return;
        }
        sleep(150, 260);
        clickAllQuantity();
        sleep(120, 220);

        if (isProductionOpen()) Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);

        status = "Crafting " + activeRecipe.getOutputName();
        sleepUntil(() -> Rs2Inventory.count(activeRecipe.getBarName(), true) == 0
            || (activeRecipe.usesGem() && Rs2Inventory.count(activeRecipe.getGemName(), true) == 0), 50_000);

        int outputAfter = outputId <= 0 ? outputBefore : Rs2Inventory.count(outputId);
        int made = Math.max(0, outputAfter - outputBefore);
        if (made == 0)
        {
            int barsAfter = Rs2Inventory.count(activeRecipe.getBarName(), true);
            made = Math.max(0, barsBefore - barsAfter);
        }
        lastBatchMade = made;
        currentBatchTarget = 0;
        refreshAccountEligibility();
        if (made > 0)
        {
            craftedCount += made;
            estimatedProfit += (long) made * activeQuote.getProfit();
            outputPendingSale = activeRecipe.getOutputName();
        }
        state = JewelryCrafterState.BANKING;
        status = "Banking output";
    }

    private void travelToGe()
    {
        if (Rs2Bank.isOpen()) Rs2Bank.closeBank();
        if (distanceTo(GRAND_EXCHANGE) > 8)
        {
            status = "Walking to Grand Exchange";
            Rs2Walker.walkTo(GRAND_EXCHANGE, 6);
            return;
        }
        state = JewelryCrafterState.GE_SELL;
        status = "Selling crafted output";
    }

    private void sellOutput()
    {
        if (handlePendingOffer()) return;

        String output = outputPendingSale != null ? outputPendingSale
            : (activeRecipe == null ? null : activeRecipe.getOutputName());

        if (!Rs2Bank.isOpen())
        {
            if (Rs2GrandExchange.isOpen()) Rs2GrandExchange.closeExchange();
            if (!Rs2Bank.openBank()) { status = "Opening GE bank"; return; }
        }

        int bankedOutput = output == null ? 0 : Rs2Bank.count(output, true);
        if (bankedOutput <= 0)
        {
            Rs2Bank.closeBank();
            outputPendingSale = null;
            geRetry = 0;
            buyQueue.clear();
            buyIndex = 0;
            state = JewelryCrafterState.GE_BUY;
            status = "Selecting profitable restock";
            return;
        }

        if (!Rs2Bank.setWithdrawAsNote()) { status = "Setting noted withdrawals"; return; }
        if (!Rs2Bank.withdrawAll(output, true)) { status = "Withdrawing output to sell"; return; }
        Rs2Bank.closeBank();

        if (!Rs2GrandExchange.openExchange()) { status = "Opening Grand Exchange"; return; }
        int qty = Rs2Inventory.count(output, true);
        int itemId = prices.getItemId(output);
        int price = prices.sellOfferPrice(output, config.sellDiscountPercent(), geRetry);
        if (qty <= 0 || itemId <= 0 || price <= 0)
        {
            status = "Unable to price/prepare output sale";
            return;
        }

        GrandExchangeRequest request = GrandExchangeRequest.builder()
            .action(GrandExchangeAction.SELL)
            .itemName(output)
            .exact(true)
            .quantity(qty)
            .price(price)
            .closeAfterCompletion(false)
            .build();
        if (!Rs2GrandExchange.processOffer(request))
        {
            status = "GE sell placement failed";
            return;
        }
        pendingOffer = new PendingOffer(output, itemId, GrandExchangeAction.SELL, System.currentTimeMillis());
        status = "Waiting for " + output + " to sell";
    }

    private void buyInputs()
    {
        if (handlePendingOffer()) return;
        if (activeRecipe == null)
        {
            state = JewelryCrafterState.EVALUATING;
            return;
        }

        if (buyQueue.isEmpty())
        {
            if (!buildBuyQueue()) return;
        }
        if (buyIndex >= buyQueue.size())
        {
            buyQueue.clear();
            buyIndex = 0;
            geRetry = 0;
            if (Rs2GrandExchange.isOpen()) Rs2GrandExchange.closeExchange();
            state = JewelryCrafterState.RETURN_TO_FURNACE;
            status = "Restock complete";
            return;
        }

        BuyOrder order = buyQueue.get(buyIndex);
        if (!Rs2GrandExchange.openExchange()) { status = "Opening Grand Exchange"; return; }
        int price = prices.buyOfferPrice(order.itemName, config.buyMarkupPercent(), geRetry);
        int itemId = prices.getItemId(order.itemName);
        if (price <= 0 || itemId <= 0)
        {
            pause("No reliable buy price for " + order.itemName, 10_000L);
            return;
        }

        GrandExchangeRequest request = GrandExchangeRequest.builder()
            .action(GrandExchangeAction.BUY)
            .itemName(order.itemName)
            .exact(true)
            .quantity(order.quantity)
            .price(price)
            .closeAfterCompletion(false)
            .build();
        if (!Rs2GrandExchange.processOffer(request))
        {
            status = "GE buy placement failed: " + order.itemName;
            return;
        }
        pendingOffer = new PendingOffer(order.itemName, itemId, GrandExchangeAction.BUY, System.currentTimeMillis());
        status = "Buying " + order.quantity + " x " + order.itemName;
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

        if (Rs2GrandExchange.isOpen()) Rs2GrandExchange.closeExchange();
        if (!Rs2Bank.isOpen() && !Rs2Bank.openBank())
        {
            status = "Opening bank for capital check";
            return false;
        }
        Rs2Bank.setWithdrawAsItem();

        long coins = Rs2Bank.count("Coins", true) + (long) Rs2Inventory.count("Coins", true);
        long spendable = Math.max(0L, coins - config.reserveCoins());
        spendable = spendable * config.capitalUsagePercent() / 100L;
        int unitCost = Math.max(1, activeQuote.getInputCost());
        int affordable = (int) Math.min(Integer.MAX_VALUE, spendable / unitCost);
        int target = Math.min(config.maxRestockUnits(), affordable);

        int existingBars = Rs2Bank.count(activeRecipe.getBarName(), true);
        int existingGems = activeRecipe.usesGem() ? Rs2Bank.count(activeRecipe.getGemName(), true) : target;
        int existingUnits = Math.min(existingBars, existingGems);
        target = Math.max(target, Math.min(config.maxRestockUnits(), existingUnits));

        if (target <= 0)
        {
            pause("Not enough spendable coins to restock", 15_000L);
            return false;
        }

        buyQueue.clear();
        buyIndex = 0;
        if (Rs2Bank.count(activeRecipe.getMouldName(), true) <= 0)
            buyQueue.add(new BuyOrder(activeRecipe.getMouldName(), 1));

        int needBars = Math.max(0, target - existingBars);
        if (needBars > 0) buyQueue.add(new BuyOrder(activeRecipe.getBarName(), needBars));

        if (activeRecipe.usesGem())
        {
            int needGems = Math.max(0, target - existingGems);
            if (needGems > 0) buyQueue.add(new BuyOrder(activeRecipe.getGemName(), needGems));
        }

        Rs2Bank.closeBank();
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
        if (!Rs2GrandExchange.openExchange())
        {
            status = "Opening GE to monitor offer";
            return true;
        }

        GrandExchangeOfferState live = findOfferState(pendingOffer.itemId, pendingOffer.action);
        boolean complete = pendingOffer.action == GrandExchangeAction.BUY
            ? live == GrandExchangeOfferState.BOUGHT
            : live == GrandExchangeOfferState.SOLD;

        if (complete)
        {
            GrandExchangeRequest collect = GrandExchangeRequest.builder()
                .action(GrandExchangeAction.COLLECT)
                .toBank(true)
                .closeAfterCompletion(false)
                .build();
            Rs2GrandExchange.processOffer(collect);
            GrandExchangeAction action = pendingOffer.action;
            pendingOffer = null;
            geRetry = 0;
            if (action == GrandExchangeAction.SELL)
            {
                outputPendingSale = null;
                buyQueue.clear();
                buyIndex = 0;
                state = JewelryCrafterState.GE_BUY;
                status = "Sale complete - selecting profitable restock";
            }
            else
            {
                buyIndex++;
                status = "Input bought";
            }
            return true;
        }

        long timeout = config.offerTimeoutSeconds() * 1_000L;
        if (System.currentTimeMillis() - pendingOffer.placedAt >= timeout)
        {
            String itemName = pendingOffer.itemName;
            GrandExchangeAction action = pendingOffer.action;
            if (geRetry >= config.maxOfferRetries())
            {
                Rs2GrandExchange.abortOffer(itemName, true);
                pendingOffer = null;
                geRetry = 0;
                buyQueue.clear();
                buyIndex = 0;
                state = JewelryCrafterState.EVALUATING;
                pause("GE retry limit reached for " + itemName, 15_000L);
                return true;
            }

            if (Rs2GrandExchange.abortOffer(itemName, true))
            {
                pendingOffer = null;
                geRetry++;
                if (action == GrandExchangeAction.BUY)
                {
                    buyQueue.clear();
                    buyIndex = 0;
                }
                status = "Repricing " + itemName + " (retry " + geRetry + ")";
            }
            return true;
        }

        status = "Waiting for GE offer: " + pendingOffer.itemName;
        return true;
    }

    private GrandExchangeOfferState findOfferState(int itemId, GrandExchangeAction action)
    {
        try
        {
            return Microbot.getClientThread().runOnClientThreadOptional(() ->
            {
                GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
                if (offers == null) return null;
                for (GrandExchangeOffer offer : offers)
                {
                    if (offer == null || offer.getItemId() != itemId) continue;
                    GrandExchangeOfferState s = offer.getState();
                    if (action == GrandExchangeAction.BUY
                        && (s == GrandExchangeOfferState.BUYING || s == GrandExchangeOfferState.BOUGHT)) return s;
                    if (action == GrandExchangeAction.SELL
                        && (s == GrandExchangeOfferState.SELLING || s == GrandExchangeOfferState.SOLD)) return s;
                }
                return null;
            }).orElse(null);
        }
        catch (Exception ignored)
        {
            return null;
        }
    }

    private void returnToFurnace()
    {
        if (Rs2GrandExchange.isOpen()) Rs2GrandExchange.closeExchange();
        if (distanceTo(EDGEVILLE_BANK) > 8)
        {
            status = "Returning to Edgeville";
            Rs2Walker.walkTo(EDGEVILLE_BANK, 5);
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
        if (Microbot.getClient().getLocalPlayer() == null) return Integer.MAX_VALUE;
        return Microbot.getClient().getLocalPlayer().getWorldLocation().distanceTo2D(point);
    }

    private boolean isProductionOpen()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Widget root = Microbot.getClient().getWidget(InterfaceID.SKILLMULTI, 0);
            return root != null && !root.isHidden();
        }).orElse(false);
    }

    private boolean selectProduct(int itemId, String fallbackText)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Widget root = Microbot.getClient().getWidget(InterfaceID.SKILLMULTI, 0);
            if (root == null || root.isHidden()) return false;
            Widget product = findItemWidget(root, itemId);
            if (product == null) product = Rs2Widget.searchChildren(fallbackText, root, true);
            return product != null && !product.isHidden() && Rs2Widget.clickWidget(product);
        }).orElse(false);
    }

    private Widget findItemWidget(Widget widget, int itemId)
    {
        if (widget == null || widget.isHidden()) return null;
        if (itemId > 0 && widget.getItemId() == itemId) return widget;

        Widget[][] groups = { widget.getChildren(), widget.getDynamicChildren(), widget.getStaticChildren(), widget.getNestedChildren() };
        for (Widget[] group : groups)
        {
            if (group == null) continue;
            for (Widget child : group)
            {
                Widget found = findItemWidget(child, itemId);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void clickAllQuantity()
    {
        Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Widget root = Microbot.getClient().getWidget(InterfaceID.SKILLMULTI, 0);
            if (root == null || root.isHidden()) return false;
            Widget all = Rs2Widget.searchChildren("All", root, true);
            return all != null && !all.isHidden() && Rs2Widget.clickWidget(all);
        });
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

    public int getCraftingXpGained()
    {
        return Math.max(0, currentCraftingXp - startingCraftingXp);
    }

    public long getCraftingXpPerHour()
    {
        long elapsed = getRuntimeMillis();
        return elapsed <= 0L ? 0L : Math.round(getCraftingXpGained() * 3_600_000.0 / elapsed);
    }

    public long getEstimatedProfitPerHour()
    {
        long elapsed = getRuntimeMillis();
        return elapsed <= 0L ? 0L : Math.round(estimatedProfit * 3_600_000.0 / elapsed);
    }

    public String getPendingOfferSummary()
    {
        PendingOffer offer = pendingOffer;
        if (offer == null) return "None";
        String side = offer.action == GrandExchangeAction.BUY ? "BUY" : "SELL";
        return side + " " + offer.itemName;
    }

    public String getRestockProgress()
    {
        if (buyQueue.isEmpty()) return "Idle";
        int total = buyQueue.size();
        int current = Math.min(total, Math.max(1, buyIndex + 1));
        return current + "/" + total;
    }

    public int getGeRetry()
    {
        return geRetry;
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
    }

    private static final class PendingOffer
    {
        final String itemName;
        final int itemId;
        final GrandExchangeAction action;
        final long placedAt;

        PendingOffer(String itemName, int itemId, GrandExchangeAction action, long placedAt)
        {
            this.itemName = itemName;
            this.itemId = itemId;
            this.action = action;
            this.placedAt = placedAt;
        }
    }

    private static final class BuyOrder
    {
        final String itemName;
        final int quantity;
        BuyOrder(String itemName, int quantity) { this.itemName = itemName; this.quantity = quantity; }
    }
}
