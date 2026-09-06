package net.runelite.client.plugins.microbot.kspsmartsmelter;

import net.runelite.api.GameObject;
import net.runelite.api.MenuAction;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.kspbank.KspVerifiedBank;
import net.runelite.client.plugins.microbot.kspsmartsmelter.model.FurnaceLocation;
import net.runelite.client.plugins.microbot.kspsmartsmelter.model.RankingMode;
import net.runelite.client.plugins.microbot.kspsmartsmelter.model.RouteQuote;
import net.runelite.client.plugins.microbot.kspsmartsmelter.model.SmartSmelterState;
import net.runelite.client.plugins.microbot.kspsmartsmelter.model.SmeltRoute;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
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
    private static final int EDGEVILLE_FURNACE_ID = 16469;
    private static final long TARGET_INTERACTION_TIMEOUT_MS = 8_000L;
    // Restock sizing is internal; users should not have to tune a cycle count.
    private static final int AUTO_RESTOCK_CYCLES = 500;
    private static final net.runelite.api.coords.WorldPoint GRAND_EXCHANGE =
            new net.runelite.api.coords.WorldPoint(3164, 3487, 0);

    private final KspSmartSmelterPlugin plugin;
    private final KspSmartSmelterConfig config;
    private final SmartSmelterAntibanController antiban;

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
    private volatile int startingSmithingLevel;
    private volatile long bankInteractionSentAt;
    private volatile long furnaceInteractionSentAt;
    private volatile int antibanHandledTrips;

    private final Object productionStatsLock = new Object();
    private SmeltRoute trackedProductionRoute;
    private RouteQuote trackedProductionQuote;
    private int trackedOutputCount;
    private int trackedInputCycles;

    @Inject
    public KspSmartSmelterScript(KspSmartSmelterPlugin plugin, KspSmartSmelterConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.antiban = new SmartSmelterAntibanController(config);
    }

    public boolean run() {
        startedAt = System.currentTimeMillis();
        startingSmithingXp = Microbot.getClientThread()
                .runOnClientThreadOptional(() -> Microbot.getClient().getSkillExperience(Skill.SMITHING))
                .orElse(0);
        startingSmithingLevel = Microbot.getClientThread()
                .runOnClientThreadOptional(() -> Microbot.getClient().getRealSkillLevel(Skill.SMITHING))
                .orElse(1);
        state = SmartSmelterState.STARTING;
        bankInteractionSentAt = 0L;
        furnaceInteractionSentAt = 0L;
        antibanHandledTrips = 0;
        synchronized (productionStatsLock) {
            clearProductionTrackingLocked();
        }
        antiban.reset();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run() || !isRunning()) {
                    return;
                }

                if (antiban.beforeTick(state)) {
                    return;
                }

                if (shouldScanPrices()) {
                    refreshRoute();
                }

                RouteQuote quoteSnapshot = selectedQuote;
                SmeltRoute routeSnapshot = quoteSnapshot == null ? null : quoteSnapshot.getRoute();
                if (quoteSnapshot == null || routeSnapshot == null) {
                    state = SmartSmelterState.NO_PROFITABLE_ROUTE;
                    Microbot.status = "No profitable eligible route";
                    lastPriceScan = 0L;
                    return;
                }

                if (hasOneCycleInInventory(routeSnapshot)) {
                    smeltTrip(routeSnapshot, quoteSnapshot);
                    return;
                }

                prepareNextTrip(routeSnapshot);
            } catch (Exception ex) {
                Microbot.logStackTrace(getClass().getSimpleName(), ex);
                Microbot.status = "Smart Smelter recovered from an error";
            }
        }, 0, 400, TimeUnit.MILLISECONDS);

        return true;
    }

    private boolean shouldScanPrices() {
        RouteQuote current = selectedQuote;
        SmeltRoute currentRoute = current == null ? null : current.getRoute();
        if (currentRoute != null && availableInputCycles(currentRoute) > 0) {
            return false;
        }

        int refreshSeconds = Math.max(15, config.priceRefreshSeconds());
        return selectedQuote == null
                || System.currentTimeMillis() - lastPriceScan >= refreshSeconds * 1000L;
    }

    private void refreshRoute() {
        RouteQuote current = selectedQuote;
        SmeltRoute currentRoute = current == null ? null : current.getRoute();

        // A selected route stays locked until every complete craftable cycle of its
        // inputs has been consumed from inventory + bank. Market rescans must not
        // interrupt an in-progress stockpile.
        if (currentRoute != null && availableInputCycles(currentRoute) > 0) {
            return;
        }

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
        if (current == null || currentRoute == null || currentRoute == best.getRoute()) {
            selectedQuote = best;
            return;
        }

        double currentScore = score(current);
        double newScore = score(best);
        double required = currentScore * (1.0 + Math.max(0, config.switchAdvantagePercent()) / 100.0);

        if (newScore >= required && availableInputCycles(currentRoute) <= 0) {
            selectedQuote = best;
        } else {
            selectedQuote = quotes.stream()
                    .filter(q -> q != null && q.getRoute() == currentRoute)
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

    private void prepareNextTrip(SmeltRoute route) {
        if (route == null) {
            selectedQuote = null;
            lastPriceScan = 0L;
            state = SmartSmelterState.NO_PROFITABLE_ROUTE;
            Microbot.status = "Route changed - rescanning";
            return;
        }

        if (!openWorkBank()) {
            return;
        }

        state = SmartSmelterState.BANKING;
        Microbot.status = "Preparing " + route.getOutputName();

        if (!Rs2Bank.setWithdrawAsItem()) {
            Microbot.status = "Setting bank item withdrawal mode";
            return;
        }

        depositProductionInventory(route);

        int availableCycles = bankCycles(route);
        if (completedTrips > antibanHandledTrips) {
            antibanHandledTrips = completedTrips;
            antiban.onBatchBanked(availableCycles > 0);
            if (antiban.beforeTick(state)) {
                return;
            }
        }
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

    private int availableInputCycles(SmeltRoute route) {
        if (route == null) {
            return 0;
        }

        int cycles = Integer.MAX_VALUE;
        int[] ids = route.getInputIds();
        int[] quantities = route.getInputQuantities();

        for (int i = 0; i < ids.length; i++) {
            int total = Math.max(0, bankQuantity(ids[i])) + Math.max(0, Rs2Inventory.itemQuantity(ids[i]));
            cycles = Math.min(cycles, total / quantities[i]);
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

    private boolean isInEdgevilleWorkArea() {
        net.runelite.api.coords.WorldPoint player = Microbot.getClientThread()
                .runOnClientThreadOptional(() -> Microbot.getClient().getLocalPlayer() == null
                        ? null
                        : Microbot.getClient().getLocalPlayer().getWorldLocation())
                .orElse(null);

        net.runelite.api.coords.WorldPoint bank = FurnaceLocation.EDGEVILLE.getBankPoint();
        net.runelite.api.coords.WorldPoint furnace = FurnaceLocation.EDGEVILLE.getFurnacePoint();
        if (player == null || bank == null || furnace == null || player.getPlane() != bank.getPlane()) {
            return false;
        }

        return player.distanceTo(bank) <= 20 || player.distanceTo(furnace) <= 20;
    }

    private boolean openWorkBank() {
        if (Rs2Bank.isOpen()) {
            bankInteractionSentAt = 0L;
            return true;
        }

        FurnaceLocation location = config.furnaceLocation();

        if (location == FurnaceLocation.EDGEVILLE) {
            if (!isInEdgevilleWorkArea()) {
                bankInteractionSentAt = 0L;
                if (location.getBankPoint() != null) {
                    state = SmartSmelterState.WALKING_TO_BANK;
                    Microbot.status = "Walking to Edgeville bank";
                    if (!Rs2Player.isMoving()) {
                        Rs2Walker.walkTo(location.getBankPoint(), 4);
                    }
                } else {
                    Microbot.status = "Cannot find Edgeville bank location";
                }
                return false;
            }

            long now = System.currentTimeMillis();
            if (bankInteractionSentAt > 0L
                    && (Rs2Player.isMoving() || now - bankInteractionSentAt < TARGET_INTERACTION_TIMEOUT_MS)) {
                Microbot.status = Rs2Player.isMoving()
                        ? "Approaching Edgeville bank"
                        : "Waiting for Edgeville bank";
                return false;
            }
            bankInteractionSentAt = 0L;

            GameObject bank = Rs2GameObject.get("Bank booth", true);
            if (bank == null) {
                Microbot.status = "Finding nearby Edgeville bank booth";
                return false;
            }

            Microbot.status = "Opening Edgeville bank";
            bankInteractionSentAt = now;
            if (!interactGameObjectWithoutCamera(bank, "Bank")) {
                bankInteractionSentAt = 0L;
                Microbot.status = "Edgeville bank target not ready";
                return false;
            }

            if (sleepUntil(Rs2Bank::isOpen, 5000)) {
                bankInteractionSentAt = 0L;
                return true;
            }

            Microbot.status = Rs2Player.isMoving()
                    ? "Approaching Edgeville bank"
                    : "Waiting for Edgeville bank";
            return false;
        }

        state = SmartSmelterState.WALKING_TO_BANK;
        Microbot.status = "Opening " + location.getDisplayName() + " bank";

        if (KspVerifiedBank.openBank() && sleepUntil(Rs2Bank::isOpen, 2500)) {
            return true;
        }

        if (location != FurnaceLocation.CURRENT_AREA && location.getBankPoint() != null) {
            Microbot.status = "Walking to " + location.getDisplayName() + " bank";
            Rs2Walker.walkTo(location.getBankPoint(), 4);
        }
        return false;
    }

    private void smeltTrip(SmeltRoute route, RouteQuote quoteSnapshot) {
        if (route == null || quoteSnapshot == null) {
            selectedQuote = null;
            lastPriceScan = 0L;
            state = SmartSmelterState.NO_PROFITABLE_ROUTE;
            Microbot.status = "Route changed - rescanning";
            return;
        }
        FurnaceLocation location = config.furnaceLocation();

        int beforeOutput = Rs2Inventory.itemQuantity(route.getOutputId());
        int beforeCycles = inventoryCycles(route);

        if (!isSmeltingInterfaceOpen(route)) {
            if (location == FurnaceLocation.EDGEVILLE) {
                if (!isInEdgevilleWorkArea()) {
                    furnaceInteractionSentAt = 0L;
                    if (location.getFurnacePoint() != null) {
                        state = SmartSmelterState.WALKING_TO_FURNACE;
                        Microbot.status = "Walking to Edgeville furnace";
                        if (!Rs2Player.isMoving()) {
                            Rs2Walker.walkTo(location.getFurnacePoint(), 4);
                        }
                    } else {
                        Microbot.status = "Cannot find Edgeville furnace location";
                    }
                    return;
                }

                long now = System.currentTimeMillis();
                if (furnaceInteractionSentAt > 0L
                        && (Rs2Player.isMoving() || now - furnaceInteractionSentAt < TARGET_INTERACTION_TIMEOUT_MS)) {
                    Microbot.status = Rs2Player.isMoving()
                            ? "Approaching Edgeville furnace"
                            : "Waiting for smelting interface";
                    return;
                }
                furnaceInteractionSentAt = 0L;

                TileObject furnace = Rs2GameObject.findObjectById(EDGEVILLE_FURNACE_ID);
                if (furnace == null) {
                    Microbot.status = "Finding nearby Edgeville furnace";
                    return;
                }

                Microbot.status = "Opening Edgeville furnace interface";
                if (!interactGameObjectWithoutCamera(furnace, "Smelt")) {
                    Microbot.status = "Could not interact with Edgeville furnace";
                    return;
                }
                furnaceInteractionSentAt = now;

                if (!sleepUntil(() -> isSmeltingInterfaceOpen(route), 5000)) {
                    Microbot.status = Rs2Player.isMoving()
                            ? "Approaching Edgeville furnace"
                            : "Waiting for smelting interface";
                    return;
                }
                furnaceInteractionSentAt = 0L;
            } else {
                Rs2TileObjectModel furnace = findConfiguredFurnace(location);
                if (furnace == null) {
                    if (location != FurnaceLocation.CURRENT_AREA && location.getFurnacePoint() != null) {
                        state = SmartSmelterState.WALKING_TO_FURNACE;
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
        } else {
            furnaceInteractionSentAt = 0L;
        }

        state = SmartSmelterState.SMELTING;
        Microbot.status = "Smelting " + route.getOutputName();

        beginProductionTracking(route, quoteSnapshot, beforeOutput, beforeCycles);
        boolean started = route.isCannonballs() ? startCannonballs() : startNormalBar(route);
        if (!started) {
            finishProductionTracking();
            Microbot.status = "Could not start " + route.getOutputName();
            return;
        }
        antiban.onProductionStarted();

        long timeout = Math.max(30_000L, route.getMaxCyclesPerTrip() * (route.isCannonballs() ? 7_000L : 4_000L));
        sleepUntil(() -> !hasOneCycleInInventory(route), (int) Math.min(timeout, 180_000L));

        // Inventory-change events update output/profit continuously while the
        // production interface is running. Take one final snapshot to catch the last
        // change before disarming the monitor, then only finalize the trip counter.
        finishProductionTracking();
        completedTrips++;

        Microbot.status = "Trip complete: " + route.getOutputName();
    }

    public void onInventoryChanged() {
        synchronized (productionStatsLock) {
            if (trackedProductionRoute == null
                    || trackedProductionQuote == null
                    || state != SmartSmelterState.SMELTING) {
                return;
            }
            recordProductionProgressLocked();
        }
    }

    private void beginProductionTracking(
            SmeltRoute route,
            RouteQuote quote,
            int outputCount,
            int inputCycles
    ) {
        synchronized (productionStatsLock) {
            trackedProductionRoute = route;
            trackedProductionQuote = quote;
            trackedOutputCount = Math.max(0, outputCount);
            trackedInputCycles = Math.max(0, inputCycles);
        }
    }

    private void finishProductionTracking() {
        synchronized (productionStatsLock) {
            if (trackedProductionRoute != null && trackedProductionQuote != null) {
                recordProductionProgressLocked();
            }
            clearProductionTrackingLocked();
        }
    }

    private void recordProductionProgressLocked() {
        SmeltRoute route = trackedProductionRoute;
        RouteQuote quote = trackedProductionQuote;
        if (route == null || quote == null) {
            return;
        }

        int currentOutput = Math.max(0, Rs2Inventory.itemQuantity(route.getOutputId()));
        int currentCycles = Math.max(0, inventoryCycles(route));
        int outputDelta = Math.max(0, currentOutput - trackedOutputCount);
        int processedCycleDelta = Math.max(0, trackedInputCycles - currentCycles);

        if (outputDelta > 0) {
            outputProduced += outputDelta;
        }
        if (processedCycleDelta > 0) {
            expectedSessionProfit += processedCycleDelta * quote.getProfitPerCycle();
        }

        trackedOutputCount = currentOutput;
        trackedInputCycles = currentCycles;
    }

    private void clearProductionTrackingLocked() {
        trackedProductionRoute = null;
        trackedProductionQuote = null;
        trackedOutputCount = 0;
        trackedInputCycles = 0;
    }

    private boolean interactGameObjectWithoutCamera(TileObject tileObject, String action) {
        if (!(tileObject instanceof GameObject) || action == null || action.isBlank()) {
            return false;
        }

        GameObject object = (GameObject) tileObject;
        ObjectComposition composition = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            ObjectComposition resolved = Microbot.getClient().getObjectDefinition(object.getId());
            if (resolved != null && resolved.getImpostorIds() != null && resolved.getImpostor() != null) {
                resolved = resolved.getImpostor();
            }
            return resolved;
        }).orElse(null);
        if (composition == null) {
            return false;
        }

        String[] actions = composition.getActions();
        if (actions == null) {
            return false;
        }

        int actionIndex = -1;
        for (int i = 0; i < actions.length; i++) {
            if (actions[i] != null && action.equalsIgnoreCase(actions[i])) {
                actionIndex = i;
                break;
            }
        }
        if (actionIndex < 0) {
            return false;
        }

        MenuAction menuAction = gameObjectMenuAction(actionIndex);
        if (menuAction == null) {
            return false;
        }

        int sceneX = object.getLocalLocation().getSceneX();
        int sceneY = object.getLocalLocation().getSceneY();
        if (object.sizeX() > 1) {
            sceneX -= object.sizeX() / 2;
        }
        if (object.sizeY() > 1) {
            sceneY -= object.sizeY() / 2;
        }

        NewMenuEntry entry = new NewMenuEntry()
                .param0(sceneX)
                .param1(sceneY)
                .opcode(menuAction.getId())
                .identifier(object.getId())
                .itemId(-1)
                .option(actions[actionIndex])
                .target(composition.getName())
                .gameObject(object);

        Microbot.doInvoke(entry, Rs2UiHelper.getObjectClickbox(object));
        return true;
    }

    private MenuAction gameObjectMenuAction(int actionIndex) {
        switch (actionIndex) {
            case 0: return MenuAction.GAME_OBJECT_FIRST_OPTION;
            case 1: return MenuAction.GAME_OBJECT_SECOND_OPTION;
            case 2: return MenuAction.GAME_OBJECT_THIRD_OPTION;
            case 3: return MenuAction.GAME_OBJECT_FOURTH_OPTION;
            case 4: return MenuAction.GAME_OBJECT_FIFTH_OPTION;
            default: return null;
        }
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
        if (!SmartSmelterGeTrader.ensureOverview()) {
            Microbot.status = "Could not open Grand Exchange";
            return;
        }

        int targetCycles = AUTO_RESTOCK_CYCLES;
        int[] ids = route.getInputIds();
        int[] quantities = route.getInputQuantities();

        for (int i = 0; i < ids.length; i++) {
            int desired = targetCycles * quantities[i];
            int existing = bankQuantity(ids[i]) + Rs2Inventory.itemQuantity(ids[i]);
            int wanted = Math.max(0, desired - existing);
            if (wanted <= 0) {
                continue;
            }

            String inputName = itemName(ids[i]);
            Microbot.status = "Placing GE buy: " + inputName;
            if (!SmartSmelterGeTrader.placeBuy(ids[i], inputName, wanted)) {
                Microbot.status = "GE buy placement/verification failed: " + inputName;
                lastPriceScan = 0L;
                return;
            }
            sleep(450, 700);
        }

        state = SmartSmelterState.WAITING_FOR_OFFERS;
        Microbot.status = SmartSmelterGeTrader.hasOpenOffers()
                ? "Waiting for GE restock offers"
                : "Restock offers completed";
        antiban.onGeWaitStart();
        long restockWait = antiban.jitterOfferTimeout(Math.max(3, config.offerWaitSeconds()) * 1000L);
        sleep((int) Math.min(Integer.MAX_VALUE, restockWait));

        if (!SmartSmelterGeTrader.collectCompletedToBank()) {
            return;
        }
        if (Rs2GrandExchange.isOpen()) {
            Rs2GrandExchange.closeExchange();
        }

        lastPriceScan = 0;
        state = SmartSmelterState.WALKING_TO_BANK;
    }

    private void sellBankedOutput(SmeltRoute route) {
        if (!Rs2Bank.isOpen()) {
            KspVerifiedBank.openBank();
        }
        if (!sleepUntil(Rs2Bank::isOpen, 5000)) {
            return;
        }

        String outputName = itemName(route.getOutputId());
        int quantity = bankQuantity(route.getOutputId());
        if (quantity <= 0) {
            Rs2Bank.closeBank();
            return;
        }

        if (!Rs2Bank.setWithdrawAsNote()) {
            Microbot.status = "Setting noted output withdrawal";
            return;
        }

        Microbot.status = "Withdrawing noted " + outputName;
        if (!Rs2Bank.withdrawAll(outputName, true)) {
            Microbot.status = "Failed withdrawing noted " + outputName;
            return;
        }
        final int expectedQuantity = quantity;
        if (!sleepUntil(() -> Rs2Inventory.itemQuantity(outputName, true) >= expectedQuantity, 5000)) {
            Microbot.status = "Waiting for noted output stack";
            return;
        }

        quantity = Rs2Inventory.itemQuantity(outputName, true);
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 3000);

        if (quantity <= 0 || !SmartSmelterGeTrader.ensureOverview()) {
            return;
        }

        if (SmartSmelterGeTrader.placeSell(
                route.getOutputId(), outputName, quantity)) {
            state = SmartSmelterState.WAITING_FOR_OFFERS;
            Microbot.status = "Selling " + route.getOutputName();
            antiban.onGeWaitStart();
            long sellWait = antiban.jitterOfferTimeout(Math.max(3, config.offerWaitSeconds()) * 1000L);
            sleep((int) Math.min(Integer.MAX_VALUE, sellWait));
            SmartSmelterGeTrader.collectCompletedToBank();
        } else {
            Microbot.status = "GE sell placement/verification failed: " + outputName;
            SmartSmelterGeTrader.recoverToOverview();
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

    public int getSmithingLevelsGained() {
        return Math.max(0, getSmithingLevel() - startingSmithingLevel);
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

    public String getAntibanStatus() {
        return antiban.getStatus();
    }

    @Override
    public void shutdown() {
        finishProductionTracking();
        state = SmartSmelterState.STOPPED;
        selectedQuote = null;
        lastQuotes = Collections.emptyList();
        bankInteractionSentAt = 0L;
        furnaceInteractionSentAt = 0L;
        super.shutdown();
    }
}
