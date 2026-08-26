package net.runelite.client.plugins.microbot.kspsmartsmelter;

import net.runelite.api.Skill;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.kspsmartsmelter.model.FurnaceLocation;
import net.runelite.client.plugins.microbot.kspsmartsmelter.model.RankingMode;
import net.runelite.client.plugins.microbot.kspsmartsmelter.model.RouteQuote;
import net.runelite.client.plugins.microbot.kspsmartsmelter.model.SmartSmelterState;
import net.runelite.client.plugins.microbot.kspsmartsmelter.model.SmeltRoute;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeAction;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeRequest;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.world.Rs2WorldUtil;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class KspSmartSmelterScript extends Script {
    private static final int CANNONBALL_INTERFACE = 17694733;
    private static final int CANNONBALL_BUTTON = 17694734;
    private static final net.runelite.api.coords.WorldPoint GRAND_EXCHANGE =
            new net.runelite.api.coords.WorldPoint(3164, 3487, 0);

    private final KspSmartSmelterPlugin plugin;
    private final KspSmartSmelterConfig config;

    private volatile SmartSmelterState state = SmartSmelterState.STARTING;
    private volatile RouteQuote selectedQuote;
    private volatile List<RouteQuote> lastQuotes = Collections.emptyList();
    private volatile long lastPriceScan;
    private volatile int completedTrips;
    private volatile int restockCount;
    private volatile long outputProduced;
    private volatile double expectedSessionProfit;
    private volatile long startedAt;
    private volatile int startingSmithingXp;

    @Inject
    public KspSmartSmelterScript(KspSmartSmelterPlugin plugin, KspSmartSmelterConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public boolean run() {
        startedAt = System.currentTimeMillis();
        startingSmithingXp = Microbot.getClientThread()
                .runOnClientThreadOptional(() -> Microbot.getClient().getSkillExperience(Skill.SMITHING))
                .orElse(0);
        state = SmartSmelterState.STARTING;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run() || !isRunning()) {
                    return;
                }

                if (shouldScanPrices()) {
                    refreshRoute();
                }

                if (selectedQuote == null) {
                    state = SmartSmelterState.NO_PROFITABLE_ROUTE;
                    Microbot.status = "No profitable eligible route";
                    return;
                }

                if (hasOneCycleInInventory(selectedQuote.getRoute())) {
                    smeltTrip();
                    return;
                }

                prepareNextTrip();
            } catch (Exception ex) {
                Microbot.logStackTrace(getClass().getSimpleName(), ex);
                Microbot.status = "Smart Smelter recovered from an error";
            }
        }, 0, 400, TimeUnit.MILLISECONDS);

        return true;
    }

    private boolean shouldScanPrices() {
        int refreshSeconds = Math.max(15, config.priceRefreshSeconds());
        return selectedQuote == null
                || System.currentTimeMillis() - lastPriceScan >= refreshSeconds * 1000L;
    }

    private void refreshRoute() {
        state = SmartSmelterState.SCANNING;
        Microbot.status = "Scanning profitable smelting routes...";

        List<RouteQuote> quotes = SmartRouteSelector.scan(config);
        lastQuotes = quotes;
        lastPriceScan = System.currentTimeMillis();

        if (quotes.isEmpty()) {
            selectedQuote = null;
            return;
        }

        RouteQuote best = quotes.get(0);
        if (selectedQuote == null || selectedQuote.getRoute() == best.getRoute()) {
            selectedQuote = best;
            return;
        }

        double currentScore = score(selectedQuote);
        double newScore = score(best);
        double required = currentScore * (1.0 + Math.max(0, config.switchAdvantagePercent()) / 100.0);

        if (newScore >= required && !hasOneCycleInInventory(selectedQuote.getRoute())) {
            selectedQuote = best;
        } else {
            selectedQuote = quotes.stream()
                    .filter(q -> q.getRoute() == selectedQuote.getRoute())
                    .findFirst()
                    .orElse(best);
        }
    }

    private double score(RouteQuote quote) {
        if (config.rankingMode() == RankingMode.ROI) {
            return quote.getRoiPercent();
        }
        if (config.rankingMode() == RankingMode.PROFIT_PER_CYCLE) {
            return quote.getProfitPerCycle();
        }
        return quote.getTripProfit();
    }

    private void prepareNextTrip() {
        SmeltRoute route = selectedQuote.getRoute();

        if (!openWorkBank()) {
            return;
        }

        state = SmartSmelterState.BANKING;
        Microbot.status = "Preparing " + route.getOutputName();

        depositProductionInventory(route);

        int availableCycles = bankCycles(route);
        if (availableCycles <= 0) {
            Rs2Bank.closeBank();

            if (!config.autoRestock()) {
                Microbot.showMessage("KSP Smart Smelter: out of inputs for " + route.getOutputName());
                Microbot.stopPlugin(plugin);
                return;
            }

            restock(route);
            return;
        }

        int cycles = Math.min(route.getMaxCyclesPerTrip(), availableCycles);
        if (cycles <= 0) {
            return;
        }

        if (route.isCannonballs() && !ensureMouldInInventory()) {
            Microbot.showMessage("KSP Smart Smelter: cannonball route selected but no mould was found.");
            lastPriceScan = 0;
            selectedQuote = null;
            return;
        }

        int[] ids = route.getInputIds();
        int[] quantities = route.getInputQuantities();

        for (int i = 0; i < ids.length; i++) {
            int amount = quantities[i] * cycles;
            if (!Rs2Bank.withdrawX(ids[i], amount)) {
                Microbot.status = "Failed withdrawing " + itemName(ids[i]);
                return;
            }
            final int itemId = ids[i];
            final int wanted = amount;
            sleepUntil(() -> Rs2Inventory.itemQuantity(itemId) >= wanted, 3000);
        }

        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 3000);

        if (!hasOneCycleInInventory(route)) {
            Microbot.status = "Inventory setup failed";
        }
    }

    private void depositProductionInventory(SmeltRoute route) {
        if (route.isCannonballs()) {
            int keepId = Rs2Inventory.hasItem(ItemID.DOUBLE_AMMO_MOULD)
                    ? ItemID.DOUBLE_AMMO_MOULD
                    : (Rs2Inventory.hasItem(ItemID.AMMO_MOULD) ? ItemID.AMMO_MOULD : -1);

            if (keepId > 0) {
                Rs2Bank.depositAllExcept(keepId);
            } else {
                Rs2Bank.depositAll();
            }
        } else {
            Rs2Bank.depositAll();
        }
        sleep(150, 300);
    }

    private boolean ensureMouldInInventory() {
        if (Rs2Inventory.hasItem(ItemID.DOUBLE_AMMO_MOULD) || Rs2Inventory.hasItem(ItemID.AMMO_MOULD)) {
            return true;
        }

        if (bankQuantity(ItemID.DOUBLE_AMMO_MOULD) > 0) {
            Rs2Bank.withdrawOne(ItemID.DOUBLE_AMMO_MOULD);
        } else if (bankQuantity(ItemID.AMMO_MOULD) > 0) {
            Rs2Bank.withdrawOne(ItemID.AMMO_MOULD);
        } else {
            return false;
        }

        return sleepUntil(() ->
                Rs2Inventory.hasItem(ItemID.DOUBLE_AMMO_MOULD)
                        || Rs2Inventory.hasItem(ItemID.AMMO_MOULD), 3000);
    }

    private int bankCycles(SmeltRoute route) {
        int cycles = Integer.MAX_VALUE;
        int[] ids = route.getInputIds();
        int[] quantities = route.getInputQuantities();

        for (int i = 0; i < ids.length; i++) {
            cycles = Math.min(cycles, bankQuantity(ids[i]) / quantities[i]);
        }
        return cycles == Integer.MAX_VALUE ? 0 : cycles;
    }

    private int bankQuantity(int id) {
        try {
            return Rs2Bank.bankItems().stream()
                    .filter(item -> item.getId() == id)
                    .mapToInt(item -> item.getQuantity())
                    .sum();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private boolean openWorkBank() {
        if (Rs2Bank.isOpen()) {
            return true;
        }

        FurnaceLocation location = config.furnaceLocation();
        state = SmartSmelterState.WALKING_TO_BANK;
        Microbot.status = "Opening " + location.getDisplayName() + " bank";

        // Interact first when a bank is already loaded/reachable in the selected area.
        if (Rs2Bank.openBank() && sleepUntil(Rs2Bank::isOpen, 2500)) {
            return true;
        }

        if (location != FurnaceLocation.CURRENT_AREA && location.getBankPoint() != null) {
            Microbot.status = "Walking to " + location.getDisplayName() + " bank";
            Rs2Walker.walkTo(location.getBankPoint(), 4);
        }
        return false;
    }

    private void smeltTrip() {
        SmeltRoute route = selectedQuote.getRoute();
        state = SmartSmelterState.WALKING_TO_FURNACE;
        FurnaceLocation location = config.furnaceLocation();

        int beforeOutput = Rs2Inventory.itemQuantity(route.getOutputId());
        int beforeCycles = inventoryCycles(route);

        // Reuse an already-open product/smithing widget instead of clicking the furnace again.
        if (!isSmeltingInterfaceOpen(route)) {
            Rs2TileObjectModel furnace = findConfiguredFurnace(location);
            if (furnace == null) {
                if (location != FurnaceLocation.CURRENT_AREA && location.getFurnacePoint() != null) {
                    Microbot.status = "Walking to " + location.getDisplayName() + " furnace";
                    Rs2Walker.walkTo(location.getFurnacePoint(), 4);
                } else {
                    Microbot.status = "Cannot find Furnace";
                }
                return;
            }

            Microbot.status = "Opening furnace interface";
            if (!furnace.click("Smelt")) {
                Microbot.status = "Could not interact with Furnace";
                return;
            }
        }

        state = SmartSmelterState.SMELTING;
        Microbot.status = "Smelting " + route.getOutputName();

        boolean started = route.isCannonballs() ? startCannonballs() : startNormalBar(route);
        if (!started) {
            Microbot.status = "Could not start " + route.getOutputName();
            return;
        }

        long timeout = Math.max(30_000L, route.getMaxCyclesPerTrip() * (route.isCannonballs() ? 7_000L : 4_000L));
        sleepUntil(() -> !hasOneCycleInInventory(route), (int) Math.min(timeout, 180_000L));

        int afterOutput = Rs2Inventory.itemQuantity(route.getOutputId());
        int produced = Math.max(0, afterOutput - beforeOutput);
        int processedCycles = Math.max(0, beforeCycles - inventoryCycles(route));

        outputProduced += produced;
        if (selectedQuote != null && selectedQuote.getRoute() == route) {
            expectedSessionProfit += processedCycles * selectedQuote.getProfitPerCycle();
        }
        completedTrips++;

        Microbot.status = "Trip complete: " + route.getOutputName();
    }

    private Rs2TileObjectModel findConfiguredFurnace(FurnaceLocation location) {
        if (location != FurnaceLocation.CURRENT_AREA && location.getFurnacePoint() != null) {
            return Microbot.getRs2TileObjectCache().query()
                    .withName("Furnace")
                    .within(location.getFurnacePoint(), 8)
                    .nearestOnClientThread();
        }
        return Microbot.getRs2TileObjectCache().query()
                .withName("Furnace")
                .nearestOnClientThread();
    }

    private boolean isSmeltingInterfaceOpen(SmeltRoute route) {
        if (route.isCannonballs()) {
            return Rs2Widget.getWidget(CANNONBALL_INTERFACE) != null || Rs2Widget.hasWidget("Cannonball");
        }
        return Rs2Widget.isSmithingWidgetOpen() || Rs2Widget.isProductionWidgetOpen();
    }

    private boolean startCannonballs() {
        if (!sleepUntil(() ->
                Rs2Widget.getWidget(CANNONBALL_INTERFACE) != null
                        || Rs2Widget.hasWidget("Cannonball"), 5000)) {
            return false;
        }

        if (Rs2Widget.getWidget(CANNONBALL_BUTTON) != null) {
            return Rs2Widget.clickWidget(CANNONBALL_BUTTON);
        }

        Widget widget = Rs2Widget.findWidget("Cannonball", false);
        return invokeAction(widget, "Smelt All") || invokeAction(widget, "Make All") || Rs2Widget.clickWidget(widget);
    }

    private boolean startNormalBar(SmeltRoute route) {
        if (!sleepUntil(() -> Rs2Widget.isSmithingWidgetOpen() || Rs2Widget.isProductionWidgetOpen(), 5000)) {
            return false;
        }

        // The generic product dialogue (SKILLMULTI) is separate from the Smithing widget.
        if (Rs2Widget.isProductionWidgetOpen()) {
            Microbot.status = "Selecting " + route.getOutputName() + " / All";
            Rs2Widget.enableQuantityOption("All");
            sleep(100, 180);
            if (Rs2Widget.handleProcessingInterface(route.getOutputName())) {
                return true;
            }
        }

        Widget widget = findSmeltActionWidget(route);
        if (widget == null) {
            return false;
        }

        return invokeAction(widget, "Smelt All")
                || invokeAction(widget, "Smelt-All")
                || invokeAction(widget, "Make All")
                || invokeAction(widget, "All")
                || Rs2Widget.clickWidget(widget);
    }

    private Widget findSmeltActionWidget(SmeltRoute route) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget production = Microbot.getClient().getWidget(InterfaceID.SKILLMULTI, 0);
            Widget found = findSmeltActionWidgetRecursive(production, route);
            if (found != null) {
                return found;
            }
            return findSmeltActionWidgetRecursive(Microbot.getClient().getWidget(InterfaceID.SMITHING, 0), route);
        }).orElse(null);
    }

    private Widget findSmeltActionWidgetRecursive(Widget widget, SmeltRoute route) {
        if (widget == null || widget.isHidden()) {
            return null;
        }

        boolean itemMatches = widget.getItemId() == route.getOutputId();
        boolean nameMatches =
                safe(widget.getText()).toLowerCase().contains(route.getOutputName().toLowerCase())
                        || safe(widget.getName()).toLowerCase().contains(route.getOutputName().toLowerCase());

        if ((itemMatches || nameMatches) && hasProductionAction(widget)) {
            return widget;
        }

        Widget[][] groups = {
                widget.getChildren(),
                widget.getDynamicChildren(),
                widget.getStaticChildren(),
                widget.getNestedChildren()
        };

        for (Widget[] group : groups) {
            if (group == null) {
                continue;
            }
            for (Widget child : group) {
                Widget found = findSmeltActionWidgetRecursive(child, route);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    private boolean hasProductionAction(Widget widget) {
        String[] actions = widget.getActions();
        if (actions == null) {
            return false;
        }
        for (String action : actions) {
            String value = safe(action).toLowerCase();
            if (value.contains("smelt") || value.contains("make")) {
                return true;
            }
        }
        return false;
    }

    private boolean invokeAction(Widget widget, String actionName) {
        if (widget == null || widget.getActions() == null) {
            return false;
        }

        String[] actions = widget.getActions();
        for (int i = 0; i < actions.length; i++) {
            String action = safe(actions[i]);
            if (action.equalsIgnoreCase(actionName)) {
                Rs2Widget.clickWidgetFast(widget, -1, i + 1);
                return true;
            }
        }
        return false;
    }

    private int inventoryCycles(SmeltRoute route) {
        int cycles = Integer.MAX_VALUE;
        int[] ids = route.getInputIds();
        int[] quantities = route.getInputQuantities();

        for (int i = 0; i < ids.length; i++) {
            cycles = Math.min(cycles, Rs2Inventory.itemQuantity(ids[i]) / quantities[i]);
        }

        return cycles == Integer.MAX_VALUE ? 0 : cycles;
    }

    private boolean hasOneCycleInInventory(SmeltRoute route) {
        int[] ids = route.getInputIds();
        int[] quantities = route.getInputQuantities();
        for (int i = 0; i < ids.length; i++) {
            if (Rs2Inventory.itemQuantity(ids[i]) < quantities[i]) {
                return false;
            }
        }
        return !route.isCannonballs()
                || Rs2Inventory.hasItem(ItemID.AMMO_MOULD)
                || Rs2Inventory.hasItem(ItemID.DOUBLE_AMMO_MOULD);
    }

    private void restock(SmeltRoute route) {
        restockCount++;
        state = SmartSmelterState.WALKING_TO_GE;
        Microbot.status = "Walking to Grand Exchange";
        Rs2Walker.walkTo(GRAND_EXCHANGE);

        if (config.autoSellOutput()) {
            sellBankedOutput(route);
        }

        state = SmartSmelterState.RESTOCKING;
        if (!Rs2GrandExchange.isOpen() && !Rs2GrandExchange.openExchange()) {
            Microbot.status = "Could not open Grand Exchange";
            return;
        }

        int targetCycles = Math.max(1, config.restockCycles());
        int[] ids = route.getInputIds();
        int[] quantities = route.getInputQuantities();

        for (int i = 0; i < ids.length; i++) {
            int wanted = targetCycles * quantities[i];
            GrandExchangeRequest request = GrandExchangeRequest.builder()
                    .action(GrandExchangeAction.BUY)
                    .itemName(itemName(ids[i]))
                    .quantity(wanted)
                    .percent(config.buyPercent())
                    .build();

            if (!Rs2GrandExchange.processOffer(request)) {
                Microbot.status = "Failed placing buy: " + itemName(ids[i]);
                Rs2GrandExchange.backToOverview();
            }
        }

        state = SmartSmelterState.WAITING_FOR_OFFERS;
        Microbot.status = "Waiting for GE restock offers";
        sleep(Math.max(3, config.offerWaitSeconds()) * 1000);

        Rs2GrandExchange.collectAllToBank();
        sleep(500, 900);
        Rs2GrandExchange.closeExchange();

        lastPriceScan = 0;
        state = SmartSmelterState.WALKING_TO_BANK;
    }

    private void sellBankedOutput(SmeltRoute route) {
        if (!Rs2Bank.isOpen()) {
            Rs2Bank.openBank();
        }
        if (!sleepUntil(Rs2Bank::isOpen, 5000)) {
            return;
        }

        int quantity = bankQuantity(route.getOutputId());
        if (quantity <= 0) {
            Rs2Bank.closeBank();
            return;
        }

        Rs2Bank.withdrawAll(route.getOutputId());
        sleepUntil(() -> Rs2Inventory.itemQuantity(route.getOutputId()) > 0, 3000);
        quantity = Rs2Inventory.itemQuantity(route.getOutputId());
        Rs2Bank.closeBank();

        if (quantity <= 0 || !Rs2GrandExchange.openExchange()) {
            return;
        }

        GrandExchangeRequest sell = GrandExchangeRequest.builder()
                .action(GrandExchangeAction.SELL)
                .itemName(itemName(route.getOutputId()))
                .quantity(quantity)
                .percent(config.sellPercent())
                .build();

        if (Rs2GrandExchange.processOffer(sell)) {
            state = SmartSmelterState.WAITING_FOR_OFFERS;
            Microbot.status = "Selling " + route.getOutputName();
            sleep(Math.max(3, config.offerWaitSeconds()) * 1000);
            Rs2GrandExchange.collectAllToBank();
        } else {
            Rs2GrandExchange.backToOverview();
        }
    }

    private String itemName(int itemId) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (Microbot.getItemManager() == null) {
                return String.valueOf(itemId);
            }
            return Microbot.getItemManager().getItemComposition(itemId).getName();
        }).orElse(String.valueOf(itemId));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public SmartSmelterState getState() {
        return state;
    }

    public RouteQuote getSelectedQuote() {
        return selectedQuote;
    }

    public List<RouteQuote> getLastQuotes() {
        return lastQuotes;
    }

    public long getLastPriceScan() {
        return lastPriceScan;
    }

    public int getCompletedTrips() {
        return completedTrips;
    }

    public long getOutputProduced() {
        return outputProduced;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public int getSmithingLevel() {
        return Microbot.getClientThread()
                .runOnClientThreadOptional(() -> Microbot.getClient().getRealSkillLevel(Skill.SMITHING))
                .orElse(1);
    }

    public boolean isMemberAccount() {
        return Rs2WorldUtil.isMemberAccount();
    }

    public int getRestockCount() {
        return restockCount;
    }

    public double getExpectedSessionProfit() {
        return expectedSessionProfit;
    }

    public double getExpectedProfitPerHour() {
        long elapsed = System.currentTimeMillis() - startedAt;
        if (elapsed <= 0) {
            return 0;
        }
        return expectedSessionProfit * 3_600_000.0 / elapsed;
    }

    public double getOutputPerHour() {
        long elapsed = System.currentTimeMillis() - startedAt;
        if (elapsed <= 0) {
            return 0;
        }
        return outputProduced * 3_600_000.0 / elapsed;
    }

    public int getSmithingXpGained() {
        int current = Microbot.getClientThread()
                .runOnClientThreadOptional(() -> Microbot.getClient().getSkillExperience(Skill.SMITHING))
                .orElse(startingSmithingXp);
        return Math.max(0, current - startingSmithingXp);
    }

    public double getSmithingXpPerHour() {
        long elapsed = System.currentTimeMillis() - startedAt;
        if (elapsed <= 0) {
            return 0;
        }
        return getSmithingXpGained() * 3_600_000.0 / elapsed;
    }

    public int getSelectedBankCycles() {
        RouteQuote quote = selectedQuote;
        return quote == null ? 0 : bankCycles(quote.getRoute());
    }

    public int getSelectedInventoryCycles() {
        RouteQuote quote = selectedQuote;
        return quote == null ? 0 : inventoryCycles(quote.getRoute());
    }

    @Override
    public void shutdown() {
        state = SmartSmelterState.STOPPED;
        selectedQuote = null;
        lastQuotes = Collections.emptyList();
        super.shutdown();
    }
}
