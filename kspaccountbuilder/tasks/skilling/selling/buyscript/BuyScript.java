package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.buyscript;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import javax.inject.Singleton;
import net.runelite.http.api.item.ItemPrice;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.KspBankWidgetHelper;
import net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.KspGrandExchangeHelper;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspTaskDebug;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspWalkerGuard;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.combat.melee.food.Food;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.gearea.GEArea;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeAction;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeRequest;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeSlots;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.grandexchange.models.GrandExchangeOfferDetails;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class BuyScript extends Script {
    private static final Logger log = LoggerFactory.getLogger(BuyScript.class);

    private static final int LOOP_DELAY_MS = 600;
    private static final int WEB_WALK_COOLDOWN_MS = 3000;
    private static final int ACTION_COOLDOWN_MS = 1200;
    private static final int BANK_WAIT_TIMEOUT_MS = 3000;
    private static final int GE_OFFER_INPUT_DELAY_MS = 900;

    private static final int TARGET_SMITHING_LEVEL = Buy.TARGET_SMITHING_LEVEL;
    private static final int TARGET_SMITHING_XP = Buy.TARGET_SMITHING_XP;
    private static final double BRONZE_BAR_SMITHING_XP = Buy.BRONZE_BAR_SMITHING_XP;

    private static final String COPPER_ORE_NAME = Buy.COPPER_ORE_NAME;
    private static final String TIN_ORE_NAME = Buy.TIN_ORE_NAME;
    private static final String COINS_NAME = Buy.COINS_NAME;

    private static final String[] PICKAXE_NAMES = Buy.PICKAXE_NAMES;

    private static final String[] AXE_NAMES = Buy.AXE_NAMES;

    private static final String HAMMER_NAME = Buy.HAMMER_NAME;
    private static final int HAMMER_ITEM_ID = Buy.HAMMER_ITEM_ID;
    private static final int HAMMER_BUY_PRICE = 300;
    private static final String TINDERBOX_NAME = Buy.TINDERBOX_NAME;
    private static final String FISHING_BAIT_NAME = Buy.FISHING_BAIT_NAME;
    private static final int FISHING_BAIT_BUY_QUANTITY = Buy.FISHING_BAIT_BUY_QUANTITY;
    private static final String FEATHER_NAME = Buy.FEATHER_NAME;
    private static final int FEATHER_BUY_QUANTITY = Buy.FEATHER_BUY_QUANTITY;
    private static final String SMALL_FISHING_NET_NAME = Buy.SMALL_FISHING_NET_NAME;
    private static final String FISHING_ROD_NAME = Buy.FISHING_ROD_NAME;
    private static final String FLY_FISHING_ROD_NAME = Buy.FLY_FISHING_ROD_NAME;
    private static final String HARPOON_NAME = Buy.HARPOON_NAME;
    private static final int HARPOON_BUY_PRICE = 600;
    private static final String LOBSTER_POT_NAME = Buy.LOBSTER_POT_NAME;
    private static final String LEATHER_NAME = Buy.LEATHER_NAME;
    private static final String THREAD_NAME = Buy.THREAD_NAME;
    private static final String NEEDLE_NAME = Buy.NEEDLE_NAME;
    private static final String GOLD_BAR_NAME = Buy.GOLD_BAR_NAME;
    private static final String RING_MOULD_NAME = Buy.RING_MOULD_NAME;
    private static final String NECKLACE_MOULD_NAME = Buy.NECKLACE_MOULD_NAME;
    private static final String CHISEL_NAME = Buy.CHISEL_NAME;
    private static final String TIARA_MOULD_NAME = Buy.TIARA_MOULD_NAME;
    private static final String BRONZE_SWORD_NAME = "Bronze sword";
    private static final String WOODEN_SHIELD_NAME = "Wooden shield";
    private static final String TROUT_NAME = Food.TROUT.getDisplayName();
    private static final String SALMON_NAME = Food.SALMON.getDisplayName();
    private static final int MIN_GOLD_BAR_BUY_QUANTITY = 50;
    private static final int MAX_GOLD_BAR_BUY_QUANTITY = 100;

    private GEArea targetArea = GEArea.GRAND_EXCHANGE;
    private boolean debugLogging;

    private long lastWebWalkAtMs;
    private long lastActionAtMs;

    private boolean bankToolsAudited;
    private boolean bankHasDesiredPickaxe;
    private boolean bankHasDesiredAxe;
    private boolean bankHasHammer;
    private boolean bankHasTinderbox;
    private boolean bankHasSmallFishingNet;
    private boolean bankHasFishingRod;
    private boolean bankHasFlyFishingRod;
    private boolean bankHasHarpoon;
    private boolean bankHasLobsterPot;
    private int bankFishingBaitCount;
    private int bankFeatherCount;
    private int bankLeatherCount;
    private int bankThreadCount;
    private boolean bankHasNeedle;
    private int leatherNeeded;
    private int bankGoldBarCount;
    private boolean bankHasRingMould;
    private boolean bankHasNecklaceMould;
    private boolean bankHasChisel;
    private boolean bankHasTiaraMould;
    private int goldBarsToBuy;
    private boolean bankHasBronzeSword;
    private boolean bankHasWoodenShield;
    private int bankTroutCount;
    private int bankSalmonCount;

    private String lastDesiredPickaxe;
    private String lastDesiredAxe;

    private final List<String> pendingMissingToolBuys = new ArrayList<>();
    private final List<String> pendingOreBuys = new ArrayList<>();
    private final List<String> pendingFishingSupplyBuys = new ArrayList<>();
    private final Map<String, Integer> pendingOreBuyQuantities = new HashMap<>();
    private final Map<String, Integer> pendingFishingSupplyBuyQuantities = new HashMap<>();

    private int requiredBronzeBars;
    private int copperOreNeeded;
    private int tinOreNeeded;
    private int bankCopperOreCount;
    private int bankTinOreCount;

    private boolean complete;
    private boolean insufficientCoinsForMissingBuys;

    public void setDebugLogging(boolean debugLogging) {
        this.debugLogging = debugLogging;
    }

    public boolean run(GEArea area) {
        this.shutdown();
        this.targetArea = area;
        this.complete = false;
        this.insufficientCoinsForMissingBuys = false;

        Microbot.status = "Walking to GE";

        this.mainScheduledFuture = this.scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!super.run() || !Microbot.isLoggedIn()) {
                return;
            }

            if (this.complete) {
                Microbot.status = this.insufficientCoinsForMissingBuys
                        ? "Not enough GP for GE Buy"
                        : "GE Buy Complete";
                return;
            }

            String desiredPickaxe = this.resolveDesiredPickaxeName();
            String desiredAxe = this.resolveDesiredAxeName();

            KspTaskDebug.throttled(
                    log,
                    this.debugLogging,
                    "GE Buy",
                    "loop",
                    5_000L,
                    "loop | desiredPickaxe={} desiredAxe={} audited={} bankHasPickaxe={} bankHasAxe={} bankHasHammer={} bankHasTinderbox={} baitBank={} feathersBank={} bankHasNet={} bankHasRod={} bankHasFlyRod={} pendingToolBuys={} pendingSupplyBuys={} pendingOreBuys={} barsNeeded={} copperNeeded={} tinNeeded={} complete={} player={} bankOpen={} geOpen={} offerScreen={} slots={} coins={}",
                    desiredPickaxe,
                    desiredAxe,
                    this.bankToolsAudited,
                    this.bankHasDesiredPickaxe,
                    this.bankHasDesiredAxe,
                    this.bankHasHammer,
                    this.bankHasTinderbox,
                    this.bankFishingBaitCount,
                    this.bankFeatherCount,
                    this.bankHasSmallFishingNet,
                    this.bankHasFishingRod,
                    this.bankHasFlyFishingRod,
                    this.pendingMissingToolBuys,
                    this.pendingFishingSupplyBuys,
                    this.pendingOreBuys,
                    this.requiredBronzeBars,
                    this.copperOreNeeded,
                    this.tinOreNeeded,
                    this.complete,
                    Rs2Player.getWorldLocation(),
                    Rs2Bank.isOpen(),
                    Rs2GrandExchange.isOpen(),
                    Rs2GrandExchange.isOfferScreenOpen(),
                    Rs2GrandExchange.isOpen() ? Rs2GrandExchange.getAvailableSlotsCount() : -1,
                    Rs2Inventory.itemQuantity(995)
            );

            if (desiredPickaxe == null || desiredAxe == null) {
                return;
            }

            this.updateDesiredToolTracking(desiredPickaxe, desiredAxe);

            if (this.completeIfNothingMissingForBuy(desiredPickaxe, desiredAxe)) {
                return;
            }

            if (Rs2Bank.isOpen()) {
                this.prepareExchangeInventory(desiredPickaxe, desiredAxe, true);
                return;
            }

            if (!this.ensureInTargetArea()) {
                return;
            }

            if (Rs2Player.isMoving()) {
                Microbot.status = "Waiting at GE";
                return;
            }

            if (Rs2Player.isInteracting()
                    && !Rs2GrandExchange.isOpen()
                    && !Rs2Bank.isOpen()
                    && !this.targetArea.toWorldArea().contains(Rs2Player.getWorldLocation())) {
                Microbot.status = "Waiting for GE interaction";
                return;
            }

            if (this.hasOutdatedToolEquipped(desiredPickaxe, desiredAxe) && !this.unequipOutdatedTools()) {
                return;
            }

            if (!this.bankToolsAudited || Rs2Bank.isOpen()) {
                this.prepareExchangeInventory(desiredPickaxe, desiredAxe, true);
                return;
            }

            if (this.handlePendingMissingToolBuys(desiredPickaxe, desiredAxe)) {
                return;
            }

            if (this.handlePendingFishingSupplyBuys()) {
                return;
            }

            if (this.handlePendingOreBuys()) {
                return;
            }

            Rs2ItemModel outdatedInventoryTool = this.getNextOutdatedInventoryTool(desiredPickaxe, desiredAxe);
            String missingTool = this.getNextMissingToolToBuy(desiredPickaxe, desiredAxe);

            if (missingTool == null
                    && this.pendingMissingToolBuys.isEmpty()
                    && this.pendingFishingSupplyBuys.isEmpty()
                    && this.pendingOreBuys.isEmpty()
                    && !this.needsFishingSupplies()
                    && !this.needsSmithingOres()) {
                Microbot.status = "GE Buy Complete";
                this.complete = true;
                return;
            }

            if (Rs2GrandExchange.isOfferScreenOpen()) {
                Microbot.status = "Opening GE Overview";
                this.returnToGrandExchangeOverview();
                return;
            }

            if (this.shouldPrepareExchangeInventory(outdatedInventoryTool, missingTool, desiredPickaxe, desiredAxe)) {
                this.prepareExchangeInventory(
                        desiredPickaxe,
                        desiredAxe,
                        missingTool != null || this.needsFishingSupplies() || this.needsSmithingOres()
                );
                return;
            }

            if (!this.ensureGrandExchangeOpen()) {
                return;
            }

            this.syncPendingMissingToolBuysFromActiveOffers(desiredPickaxe, desiredAxe);
            this.syncPendingFishingSupplyBuysFromActiveOffers();
            this.syncPendingOreBuysFromActiveOffers();

            this.processAvailableExchangeSlots(desiredPickaxe, desiredAxe);

        }, 0L, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);

        return true;
    }

    private boolean ensureInTargetArea() {
        if (this.targetArea.toWorldArea().contains(Rs2Player.getWorldLocation())) {
            KspWalkerGuard.clear("GE Buy:target-area");
            return true;
        }

        if (Rs2Player.isMoving()) {
            return false;
        }

        Microbot.status = "Walking to GE";

        if (KspWalkerGuard.walkToDestination(
                "GE Buy:target-area",
                this.targetArea::getRandomPoint,
                this.targetArea.toWorldArea()::contains,
                2,
                WEB_WALK_COOLDOWN_MS)) {
            this.lastWebWalkAtMs = System.currentTimeMillis();
            this.debug(
                    "Requested GE buy area walk | player={} area={}",
                    Rs2Player.getWorldLocation(),
                    this.targetArea.getDisplayName()
            );
        }
        return false;
    }

    private boolean completeIfNothingMissingForBuy(String desiredPickaxe, String desiredAxe) {
        if (!Rs2Bank.isOpen() && !this.bankToolsAudited) {
            return false;
        }

        this.refreshBankAuditSnapshot(desiredPickaxe, desiredAxe);
        this.calculateSmithingOreNeeds();

        boolean hasPendingBuys = !this.pendingMissingToolBuys.isEmpty()
                || !this.pendingFishingSupplyBuys.isEmpty()
                || !this.pendingOreBuys.isEmpty()
                || Rs2GrandExchange.hasBoughtOffer();

        if (hasPendingBuys || this.hasMissingBuyRequirement(desiredPickaxe, desiredAxe)) {
            return false;
        }

        Microbot.status = "GE Buy Skipped";
        this.complete = true;
        this.insufficientCoinsForMissingBuys = false;
        this.debug(
                "Skipping GE buy; nothing missing | desiredPickaxe={} desiredAxe={} hammer={} tinderbox={} baitBank={} feathersBank={} net={} rod={} flyRod={} copperNeeded={} tinNeeded={} bankOpen={}",
                desiredPickaxe,
                desiredAxe,
                this.bankHasHammer,
                this.bankHasTinderbox,
                this.bankFishingBaitCount,
                this.bankFeatherCount,
                this.bankHasSmallFishingNet,
                this.bankHasFishingRod,
                this.bankHasFlyFishingRod,
                this.copperOreNeeded,
                this.tinOreNeeded,
                Rs2Bank.isOpen()
        );
        return true;
    }

    private boolean hasMissingBuyRequirement(String desiredPickaxe, String desiredAxe) {
        return !this.hasToolAnywhere(desiredPickaxe)
                || !this.hasToolAnywhere(desiredAxe)
                || !this.hasHammerAnywhere()
                || !this.hasTinderboxAnywhere()
                || this.hasFishingBuyRequirementMissing()
                || this.hasSmithingOreNeed();
    }

    private boolean ensureEnoughCoinsForMissingBuys(String desiredPickaxe, String desiredAxe) {
        if (!this.pendingMissingToolBuys.isEmpty()
                || !this.pendingFishingSupplyBuys.isEmpty()
                || !this.pendingOreBuys.isEmpty()
                || Rs2GrandExchange.hasBoughtOffer()) {
            return true;
        }

        BuyBudget budget = this.calculateMissingBuyBudget(desiredPickaxe, desiredAxe);

        this.debug(
                "GE buy budget | bankCoins={} inventoryCoins={} availableCoins={} estimatedCost={} enough={} missing={}",
                budget.bankCoins,
                budget.inventoryCoins,
                budget.getAvailableCoins(),
                budget.estimatedCost,
                budget.hasEnoughCoins(),
                budget.getDetails()
        );

        if (budget.estimatedCost <= 0L || budget.getAvailableCoins() > 0L) {
            return true;
        }

        Microbot.status = "Not enough GP for GE Buy";
        this.complete = true;
        this.insufficientCoinsForMissingBuys = true;
        this.bankToolsAudited = true;
        return false;
    }

    private BuyBudget calculateMissingBuyBudget(String desiredPickaxe, String desiredAxe) {
        BuyBudget budget = new BuyBudget(
                Math.max(0, Rs2Bank.count(COINS_NAME)),
                Math.max(0, Rs2Inventory.itemQuantity(995))
        );

        this.addToolToBudgetIfMissing(budget, desiredPickaxe);
        this.addToolToBudgetIfMissing(budget, desiredAxe);
        this.addToolToBudgetIfMissing(budget, HAMMER_NAME);
        this.addToolToBudgetIfMissing(budget, TINDERBOX_NAME);
        this.addToolToBudgetIfMissing(budget, SMALL_FISHING_NET_NAME);
        this.addToolToBudgetIfMissing(budget, FISHING_ROD_NAME);
        this.addToolToBudgetIfMissing(budget, FLY_FISHING_ROD_NAME);
        this.addToolToBudgetIfMissing(budget, HARPOON_NAME);
        this.addToolToBudgetIfMissing(budget, LOBSTER_POT_NAME);

        this.addFishingSupplyToBudgetIfMissing(budget, FISHING_BAIT_NAME);
        this.addFishingSupplyToBudgetIfMissing(budget, FEATHER_NAME);
        this.addFishingSupplyToBudgetIfMissing(budget, LEATHER_NAME);
        this.addFishingSupplyToBudgetIfMissing(budget, THREAD_NAME);
        this.addFishingSupplyToBudgetIfMissing(budget, NEEDLE_NAME);
        this.addFishingSupplyToBudgetIfMissing(budget, GOLD_BAR_NAME);
        this.addFishingSupplyToBudgetIfMissing(budget, RING_MOULD_NAME);
        this.addFishingSupplyToBudgetIfMissing(budget, NECKLACE_MOULD_NAME);
        this.addFishingSupplyToBudgetIfMissing(budget, CHISEL_NAME);
        this.addFishingSupplyToBudgetIfMissing(budget, TIARA_MOULD_NAME);
        this.addFishingSupplyToBudgetIfMissing(budget, BRONZE_SWORD_NAME);
        this.addFishingSupplyToBudgetIfMissing(budget, WOODEN_SHIELD_NAME);
        this.addFishingSupplyToBudgetIfMissing(budget, SALMON_NAME);
        this.addOreToBudgetIfMissing(budget, COPPER_ORE_NAME);
        this.addOreToBudgetIfMissing(budget, TIN_ORE_NAME);

        return budget;
    }

    public boolean canAffordMissingBuys() {
        String desiredPickaxe = this.resolveDesiredPickaxeName();
        String desiredAxe = this.resolveDesiredAxeName();
        if (Rs2Bank.isOpen()) {
            this.refreshBankAuditSnapshot(desiredPickaxe, desiredAxe);
        }
        this.calculateSmithingOreNeeds();
        this.calculateCraftingNeeds();
        BuyBudget budget = this.calculateMissingBuyBudget(desiredPickaxe, desiredAxe);
        return budget.estimatedCost <= 0L || budget.getAvailableCoins() > 0L;
    }

    private void addToolToBudgetIfMissing(BuyBudget budget, String itemName) {
        if (!this.shouldBuyFishingTool(itemName)) {
            return;
        }

        if (itemName == null || this.hasToolAnywhere(itemName) || this.isMissingToolBuyPending(itemName)) {
            return;
        }

        this.addMissingBuyToBudget(budget, itemName, 1);
    }

    private void addFishingSupplyToBudgetIfMissing(BuyBudget budget, String itemName) {
        int quantity = this.getFishingSupplyQuantityToBuy(itemName);

        if (quantity <= 0 || this.isFishingSupplyBuyPending(itemName)) {
            return;
        }

        this.addMissingBuyToBudget(budget, itemName, quantity);
    }

    private void addOreToBudgetIfMissing(BuyBudget budget, String itemName) {
        int quantity = this.getOreQuantityToBuy(itemName);

        if (quantity <= 0 || this.isOreBuyPending(itemName)) {
            return;
        }

        this.addMissingBuyToBudget(budget, itemName, quantity);
    }

    private void addMissingBuyToBudget(BuyBudget budget, String itemName, int quantity) {
        int unitPrice = this.getAdjustedBuyPrice(itemName);
        long totalPrice = (long) Math.max(1, unitPrice) * (long) quantity;

        budget.estimatedCost += totalPrice;
        budget.addDetail(quantity, itemName, Math.max(1, unitPrice), totalPrice);
    }

    private void refreshBankAuditSnapshot(String desiredPickaxe, String desiredAxe) {
        if (!Rs2Bank.isOpen()) {
            return;
        }

        this.bankHasDesiredPickaxe = Rs2Bank.count(desiredPickaxe) > 0;
        this.bankHasDesiredAxe = Rs2Bank.count(desiredAxe) > 0;
        this.bankHasHammer = this.hasItemIdInBank(HAMMER_ITEM_ID);
        this.bankHasTinderbox = Rs2Bank.count(TINDERBOX_NAME) > 0;
        this.bankFishingBaitCount = Math.max(0, Rs2Bank.count(FISHING_BAIT_NAME));
        this.bankFeatherCount = Math.max(0, Rs2Bank.count(FEATHER_NAME));
        this.bankHasSmallFishingNet = Rs2Bank.count(SMALL_FISHING_NET_NAME) > 0;
        this.bankHasFishingRod = Rs2Bank.count(FISHING_ROD_NAME) > 0;
        this.bankHasFlyFishingRod = Rs2Bank.count(FLY_FISHING_ROD_NAME) > 0;
        this.bankHasHarpoon = Rs2Bank.count(HARPOON_NAME) > 0;
        this.bankHasLobsterPot = Rs2Bank.count(LOBSTER_POT_NAME) > 0;
        this.bankLeatherCount = Math.max(0, Rs2Bank.count(LEATHER_NAME));
        this.bankThreadCount = Math.max(0, Rs2Bank.count(THREAD_NAME));
        this.bankHasNeedle = Rs2Bank.count(NEEDLE_NAME) > 0;
        this.bankGoldBarCount = Math.max(0, Rs2Bank.count(GOLD_BAR_NAME));
        this.bankHasRingMould = Rs2Bank.count(RING_MOULD_NAME) > 0;
        this.bankHasNecklaceMould = Rs2Bank.count(NECKLACE_MOULD_NAME) > 0;
        this.bankHasChisel = Rs2Bank.count(CHISEL_NAME) > 0;
        this.bankHasTiaraMould = Rs2Bank.count(TIARA_MOULD_NAME) > 0;
        this.bankHasBronzeSword = Rs2Bank.count(BRONZE_SWORD_NAME) > 0;
        this.bankHasWoodenShield = Rs2Bank.count(WOODEN_SHIELD_NAME) > 0;
        this.bankTroutCount = Math.max(0, Rs2Bank.count(Food.TROUT.getItemId()));
        this.bankSalmonCount = Math.max(0, Rs2Bank.count(Food.SALMON.getItemId()));
        this.refreshCachedBankOreCounts();
        this.bankToolsAudited = true;
    }

    private boolean shouldPrepareExchangeInventory(
            Rs2ItemModel outdatedInventoryTool,
            String missingTool,
            String desiredPickaxe,
            String desiredAxe
    ) {
        if (Rs2Bank.isOpen()) {
            return true;
        }

        if (outdatedInventoryTool != null) {
            return false;
        }

        boolean missingBuyItem = missingTool != null || this.needsFishingSupplies() || this.needsSmithingOres();

        if (missingBuyItem && Rs2Inventory.itemQuantity(995) > 0) {
            return false;
        }

        if (this.hasOutdatedToolInBank(desiredPickaxe, desiredAxe)) {
            return true;
        }

        return missingBuyItem;
    }

    private void prepareExchangeInventory(String desiredPickaxe, String desiredAxe, boolean needCoinsForBuying) {
        if (!Rs2Bank.isOpen()) {
            if (Rs2GrandExchange.isOpen()) {
                Microbot.status = "Closing GE";
                Rs2GrandExchange.closeExchange();
                BuyScript.sleepUntil(() -> !Rs2GrandExchange.isOpen(), 2000);
                return;
            }

            if (this.targetArea.toWorldArea().contains(Rs2Player.getWorldLocation())) {
                Microbot.status = "Using GE Bank";

                if (!Rs2Bank.openBank()) {
                    Rs2Bank.walkToBankAndUseBank();
                }

                BuyScript.sleepUntil(Rs2Bank::isOpen, BANK_WAIT_TIMEOUT_MS);
                return;
            }

            Microbot.status = "Opening Bank";

            if (Rs2Bank.openBank()) {
                BuyScript.sleepUntil(Rs2Bank::isOpen, BANK_WAIT_TIMEOUT_MS);
            } else {
                Microbot.status = "Walking to Bank";
                Rs2Bank.walkToBankAndUseBank();
            }

            return;
        }

        Microbot.status = "Preparing GE Items";

        this.refreshBankAuditSnapshot(desiredPickaxe, desiredAxe);

        this.debug(
                "Preparing GE inventory | desiredPickaxe={} bankHasPickaxe={} desiredAxe={} bankHasAxe={} needCoins={} coinsInv={} coinsBank={}",
                desiredPickaxe,
                this.bankHasDesiredPickaxe,
                desiredAxe,
                this.bankHasDesiredAxe,
                needCoinsForBuying,
                Rs2Inventory.itemQuantity(995),
                Rs2Bank.count(COINS_NAME)
        );

        if (KspBankWidgetHelper.closeBankTutorialOverlayIfOpenAndWait()) {
            return;
        }

        if (!Rs2Inventory.isEmpty()) {
            Rs2Bank.depositAll();
            BuyScript.sleepUntil(Rs2Inventory::isEmpty, BANK_WAIT_TIMEOUT_MS);
            this.refreshBankAuditSnapshot(desiredPickaxe, desiredAxe);
        }

        this.calculateSmithingOreNeeds();
        this.calculateCraftingNeeds();

        this.debug(
                "Tool/Ore/Fishing audit | hammerInBank={} tinderboxInBank={} baitBank={} feathersBank={} netInBank={} rodInBank={} flyRodInBank={} barsNeeded={} copperNeeded={} tinNeeded={} pendingTools={} pendingSupply={} pendingOres={}",
                this.bankHasHammer,
                this.bankHasTinderbox,
                this.bankFishingBaitCount,
                this.bankFeatherCount,
                this.bankHasSmallFishingNet,
                this.bankHasFishingRod,
                this.bankHasFlyFishingRod,
                this.requiredBronzeBars,
                this.copperOreNeeded,
                this.tinOreNeeded,
                this.pendingMissingToolBuys,
                this.pendingFishingSupplyBuys,
                this.pendingOreBuys
        );

        if (needCoinsForBuying && !this.ensureEnoughCoinsForMissingBuys(desiredPickaxe, desiredAxe)) {
            return;
        }

        if (!Rs2Bank.hasWithdrawAsNote()) {
            Rs2Bank.setWithdrawAsNote();
            BuyScript.sleepUntil(Rs2Bank::hasWithdrawAsNote, 2000);
        }

        this.withdrawOutdatedToolsAsNotes(desiredPickaxe, desiredAxe);

        if (needCoinsForBuying && Rs2Inventory.itemQuantity(995) <= 0 && Rs2Bank.count(COINS_NAME) > 0) {
            Rs2Bank.withdrawAll(COINS_NAME);
            BuyScript.sleepUntil(() -> Rs2Inventory.itemQuantity(995) > 0, BANK_WAIT_TIMEOUT_MS);
        }

        Rs2Bank.closeBank();
        BuyScript.sleepUntil(() -> !Rs2Bank.isOpen(), 2000);

        this.bankToolsAudited = true;
        Microbot.status = "Opening GE";
    }

    private void updateDesiredToolTracking(String desiredPickaxe, String desiredAxe) {
        if (Objects.equals(this.lastDesiredPickaxe, desiredPickaxe)
                && Objects.equals(this.lastDesiredAxe, desiredAxe)) {
            return;
        }

        this.lastDesiredPickaxe = desiredPickaxe;
        this.lastDesiredAxe = desiredAxe;

        this.bankToolsAudited = false;
        this.bankHasDesiredPickaxe = false;
        this.bankHasDesiredAxe = false;
        this.bankHasHammer = false;
        this.bankHasTinderbox = false;
        this.bankHasSmallFishingNet = false;
        this.bankHasFishingRod = false;
        this.bankHasFlyFishingRod = false;
        this.bankHasHarpoon = false;
        this.bankHasLobsterPot = false;
        this.bankFishingBaitCount = 0;
        this.bankFeatherCount = 0;
        this.bankLeatherCount = 0;
        this.bankThreadCount = 0;
        this.bankHasNeedle = false;
        this.bankGoldBarCount = 0;
        this.bankHasRingMould = false;
        this.bankHasNecklaceMould = false;
        this.bankHasChisel = false;
        this.bankHasTiaraMould = false;
        this.goldBarsToBuy = 0;
        this.bankHasBronzeSword = false;
        this.bankHasWoodenShield = false;
        this.bankTroutCount = 0;
        this.bankSalmonCount = 0;
        this.bankHasSmallFishingNet = false;
        this.bankHasFishingRod = false;
        this.bankHasFlyFishingRod = false;
        this.bankHasHarpoon = false;
        this.bankHasLobsterPot = false;
        this.bankFishingBaitCount = 0;
        this.bankFeatherCount = 0;

        this.requiredBronzeBars = 0;
        this.leatherNeeded = 0;
        this.copperOreNeeded = 0;
        this.tinOreNeeded = 0;
        this.bankCopperOreCount = 0;
        this.bankTinOreCount = 0;

        this.complete = false;
        this.pendingMissingToolBuys.clear();
        this.pendingFishingSupplyBuys.clear();
        this.pendingOreBuys.clear();
        this.pendingFishingSupplyBuyQuantities.clear();
        this.pendingOreBuyQuantities.clear();
    }

    private void calculateSmithingOreNeeds() {
        int currentSmithingLevel = Microbot.getClient().getRealSkillLevel(Skill.SMITHING);
        int currentSmithingXp = Microbot.getClient().getSkillExperience(Skill.SMITHING);

        if (currentSmithingLevel >= TARGET_SMITHING_LEVEL || currentSmithingXp >= TARGET_SMITHING_XP) {
            this.requiredBronzeBars = 0;
            this.copperOreNeeded = 0;
            this.tinOreNeeded = 0;
            return;
        }

        int xpRemaining = TARGET_SMITHING_XP - currentSmithingXp;
        this.requiredBronzeBars = (int) Math.ceil(xpRemaining / BRONZE_BAR_SMITHING_XP);

        int ownedCopper = this.getOwnedCount(COPPER_ORE_NAME);
        int ownedTin = this.getOwnedCount(TIN_ORE_NAME);

        this.copperOreNeeded = Math.max(0, this.requiredBronzeBars - ownedCopper);
        this.tinOreNeeded = Math.max(0, this.requiredBronzeBars - ownedTin);

        KspTaskDebug.throttled(
                log,
                this.debugLogging,
                "GE Buy",
                "smithing-ore-calc",
                5_000L,
                "Smithing ore calc | level={} xp={} xpRemaining={} barsNeeded={} ownedCopper={} ownedTin={} copperNeeded={} tinNeeded={}",
                currentSmithingLevel,
                currentSmithingXp,
                xpRemaining,
                this.requiredBronzeBars,
                ownedCopper,
                ownedTin,
                this.copperOreNeeded,
                this.tinOreNeeded
        );
    }

    private boolean needsSmithingOres() {
        this.calculateSmithingOreNeeds();
        return this.copperOreNeeded > 0 || this.tinOreNeeded > 0;
    }

    private int getOwnedCount(String itemName) {
        int count = 0;

        for (Rs2ItemModel item : Rs2Inventory.all()) {
            if (item != null && item.getName() != null && item.getName().equalsIgnoreCase(itemName)) {
                count += item.getQuantity();
            }
        }

        if (Rs2Bank.isOpen()) {
            int bankCount = Rs2Bank.count(itemName);
            this.cacheBankOreCount(itemName, bankCount);
            count += bankCount;
        } else if (this.bankToolsAudited) {
            count += this.getCachedBankOreCount(itemName);
        }

        return count;
    }

    private void refreshCachedBankOreCounts() {
        if (!Rs2Bank.isOpen()) {
            return;
        }

        this.bankCopperOreCount = Math.max(0, Rs2Bank.count(COPPER_ORE_NAME));
        this.bankTinOreCount = Math.max(0, Rs2Bank.count(TIN_ORE_NAME));
    }

    private void cacheBankOreCount(String itemName, int bankCount) {
        if (COPPER_ORE_NAME.equalsIgnoreCase(itemName)) {
            this.bankCopperOreCount = Math.max(0, bankCount);
        } else if (TIN_ORE_NAME.equalsIgnoreCase(itemName)) {
            this.bankTinOreCount = Math.max(0, bankCount);
        }
    }

    private int getCachedBankOreCount(String itemName) {
        if (COPPER_ORE_NAME.equalsIgnoreCase(itemName)) {
            return this.bankCopperOreCount;
        }

        if (TIN_ORE_NAME.equalsIgnoreCase(itemName)) {
            return this.bankTinOreCount;
        }

        return 0;
    }

    private boolean hasFishingBuyRequirementMissing() {
        return this.getFishingSupplyQuantityToBuy(FISHING_BAIT_NAME) > 0
                || this.getFishingSupplyQuantityToBuy(FEATHER_NAME) > 0
                || (this.shouldKeepBasicFishingKit() && !this.hasFishingToolInBank(SMALL_FISHING_NET_NAME))
                || (this.shouldKeepBasicFishingKit() && !this.hasFishingToolInBank(FISHING_ROD_NAME))
                || !this.hasFishingToolInBank(FLY_FISHING_ROD_NAME)
                || !this.hasFishingToolInBank(HARPOON_NAME)
                || !this.hasFishingToolInBank(LOBSTER_POT_NAME)
                || this.hasCraftingBuyRequirementMissing()
                || this.hasJewelleryBuyRequirementMissing()
                || this.hasChickenMeleeBuyRequirementMissing()
                || this.getFishingSupplyQuantityToBuy(SALMON_NAME) > 0;
    }

    private boolean needsFishingSupplies() {
        return this.getNextFishingSupplyToBuy() != null;
    }

    private boolean handlePendingFishingSupplyBuys() {
        this.clearSatisfiedPendingFishingSupplyBuys();

        if (!this.hasFishingSupplyNeed() && this.pendingFishingSupplyBuys.isEmpty()) {
            return false;
        }

        if (!this.ensureGrandExchangeOpen()) {
            return true;
        }

        if (Rs2GrandExchange.isOfferScreenOpen()) {
            Microbot.status = "Opening GE Overview";
            this.returnToGrandExchangeOverview();
            return true;
        }

        this.syncPendingFishingSupplyBuysFromActiveOffers();

        if (this.hasCollectableFishingSupplyBuy()) {
            Microbot.status = "Collecting fishing supplies";
            this.markCompletedPendingFishingSupplyBuys();
            Rs2GrandExchange.collectAllToBank();
            BuyScript.sleepUntil(() -> !Rs2GrandExchange.hasBoughtOffer(), 5000);
            this.clearSatisfiedPendingFishingSupplyBuys();
            return true;
        }

        if (!this.pendingFishingSupplyBuys.isEmpty() && this.getNextFishingSupplyToBuy() == null) {
            Microbot.status = "Waiting for fishing supplies";
            return true;
        }

        return false;
    }

    private boolean hasFishingSupplyNeed() {
        return this.getFishingSupplyQuantityToBuy(FISHING_BAIT_NAME) > 0
                || this.getFishingSupplyQuantityToBuy(FEATHER_NAME) > 0
                || this.getFishingSupplyQuantityToBuy(LEATHER_NAME) > 0
                || this.getFishingSupplyQuantityToBuy(THREAD_NAME) > 0
                || this.getFishingSupplyQuantityToBuy(NEEDLE_NAME) > 0
                || this.getFishingSupplyQuantityToBuy(GOLD_BAR_NAME) > 0
                || this.getFishingSupplyQuantityToBuy(RING_MOULD_NAME) > 0
                || this.getFishingSupplyQuantityToBuy(NECKLACE_MOULD_NAME) > 0
                || this.getFishingSupplyQuantityToBuy(CHISEL_NAME) > 0
                || this.getFishingSupplyQuantityToBuy(TIARA_MOULD_NAME) > 0
                || this.getFishingSupplyQuantityToBuy(BRONZE_SWORD_NAME) > 0
                || this.getFishingSupplyQuantityToBuy(WOODEN_SHIELD_NAME) > 0
                || this.getFishingSupplyQuantityToBuy(SALMON_NAME) > 0;
    }

    private String getNextFishingSupplyToBuy() {
        if (this.getFishingSupplyQuantityToBuy(FISHING_BAIT_NAME) > 0
                && !this.isFishingSupplyBuyPending(FISHING_BAIT_NAME)) {
            return FISHING_BAIT_NAME;
        }

        if (this.getFishingSupplyQuantityToBuy(FEATHER_NAME) > 0
                && !this.isFishingSupplyBuyPending(FEATHER_NAME)) {
            return FEATHER_NAME;
        }

        String[] supplies = {
                LEATHER_NAME, THREAD_NAME, NEEDLE_NAME, GOLD_BAR_NAME, RING_MOULD_NAME, NECKLACE_MOULD_NAME,
                CHISEL_NAME, TIARA_MOULD_NAME,
                BRONZE_SWORD_NAME, WOODEN_SHIELD_NAME, SALMON_NAME
        };

        for (String supply : supplies) {
            if (this.getFishingSupplyQuantityToBuy(supply) > 0 && !this.isFishingSupplyBuyPending(supply)) {
                return supply;
            }
        }

        return null;
    }

    private int getFishingSupplyQuantityToBuy(String itemName) {
        if (itemName == null) {
            return 0;
        }

        if (FISHING_BAIT_NAME.equalsIgnoreCase(itemName)) {
            return this.shouldKeepBasicFishingKit() && this.bankFishingBaitCount <= 0 ? FISHING_BAIT_BUY_QUANTITY : 0;
        }

        if (FEATHER_NAME.equalsIgnoreCase(itemName)) {
            return this.bankFeatherCount <= 0 ? FEATHER_BUY_QUANTITY : 0;
        }

        if (LEATHER_NAME.equalsIgnoreCase(itemName)) {
            this.calculateCraftingNeeds();
            return Math.max(0, this.leatherNeeded - this.bankLeatherCount);
        }

        if (THREAD_NAME.equalsIgnoreCase(itemName)) {
            this.calculateCraftingNeeds();
            return this.leatherNeeded > 0 && this.bankThreadCount <= 0 ? 1 : 0;
        }

        if (NEEDLE_NAME.equalsIgnoreCase(itemName)) {
            this.calculateCraftingNeeds();
            return this.leatherNeeded > 0 && !this.bankHasNeedle ? 1 : 0;
        }

        if (GOLD_BAR_NAME.equalsIgnoreCase(itemName)) {
            return this.bankGoldBarCount <= 0 ? this.getGoldBarsToBuy() : 0;
        }

        if (RING_MOULD_NAME.equalsIgnoreCase(itemName)) {
            return !this.bankHasRingMould ? 1 : 0;
        }

        if (NECKLACE_MOULD_NAME.equalsIgnoreCase(itemName)) {
            return !this.bankHasNecklaceMould ? 1 : 0;
        }

        if (CHISEL_NAME.equalsIgnoreCase(itemName)) {
            return !this.bankHasChisel ? 1 : 0;
        }

        if (TIARA_MOULD_NAME.equalsIgnoreCase(itemName)) {
            return !this.bankHasTiaraMould ? 1 : 0;
        }

        if (BRONZE_SWORD_NAME.equalsIgnoreCase(itemName)) {
            return this.shouldUseChickenMeleeKit() && !this.bankHasBronzeSword ? 1 : 0;
        }

        if (WOODEN_SHIELD_NAME.equalsIgnoreCase(itemName)) {
            return this.shouldUseChickenMeleeKit() && !this.bankHasWoodenShield ? 1 : 0;
        }

        if (SALMON_NAME.equalsIgnoreCase(itemName)) {
            if (this.shouldUseChickenMeleeKit()) {
                return 0;
            }
            int validFoodCount = this.bankTroutCount + this.bankSalmonCount;
            return validFoodCount < Buy.MELEE_TARGET_FOOD_COUNT
                    ? Math.max(Buy.MELEE_FOOD_PURCHASE_QUANTITY,
                            Buy.MELEE_TARGET_FOOD_COUNT - validFoodCount)
                    : 0;
        }

        return 0;
    }

    private void calculateCraftingNeeds() {
        int currentCraftingLevel = Microbot.getClient().getRealSkillLevel(Skill.CRAFTING);
        int currentCraftingXp = Microbot.getClient().getSkillExperience(Skill.CRAFTING);
        this.leatherNeeded = Buy.getRequiredLeatherGlovesForCraftingTarget(currentCraftingLevel, currentCraftingXp);
    }

    private boolean hasCraftingBuyRequirementMissing() {
        this.calculateCraftingNeeds();
        return this.leatherNeeded > 0
                && (this.bankLeatherCount < this.leatherNeeded || this.bankThreadCount <= 0 || !this.bankHasNeedle);
    }

    private boolean hasJewelleryBuyRequirementMissing() {
        return this.bankGoldBarCount <= 0
                || !this.bankHasRingMould
                || !this.bankHasNecklaceMould
                || !this.bankHasChisel
                || !this.bankHasTiaraMould;
    }

    private boolean hasChickenMeleeBuyRequirementMissing() {
        return this.shouldUseChickenMeleeKit()
                && (!this.bankHasBronzeSword
                || !this.bankHasWoodenShield);
    }

    private boolean shouldKeepBasicFishingKit() {
        return Microbot.getClient().getRealSkillLevel(Skill.FISHING) < 20;
    }

    private boolean shouldUseChickenMeleeKit() {
        return Microbot.getClient().getRealSkillLevel(Skill.ATTACK) < 15
                || Microbot.getClient().getRealSkillLevel(Skill.STRENGTH) < 15
                || Microbot.getClient().getRealSkillLevel(Skill.DEFENCE) < 15;
    }

    private int getGoldBarsToBuy() {
        if (this.goldBarsToBuy <= 0) {
            this.goldBarsToBuy = ThreadLocalRandom.current().nextInt(MIN_GOLD_BAR_BUY_QUANTITY, MAX_GOLD_BAR_BUY_QUANTITY + 1);
        }

        return this.goldBarsToBuy;
    }

    private boolean handlePendingOreBuys() {
        this.calculateSmithingOreNeeds();
        this.clearSatisfiedPendingOreBuys();

        if (!this.hasSmithingOreNeed() && this.pendingOreBuys.isEmpty()) {
            return false;
        }

        if (!this.ensureGrandExchangeOpen()) {
            return true;
        }

        if (Rs2GrandExchange.isOfferScreenOpen()) {
            Microbot.status = "Opening GE Overview";
            this.returnToGrandExchangeOverview();
            return true;
        }

        this.syncPendingOreBuysFromActiveOffers();

        if (this.hasCollectableOreBuy()) {
            Microbot.status = "Collecting ore buys";
            this.markCompletedPendingOreBuys();
            Rs2GrandExchange.collectAllToBank();
            BuyScript.sleepUntil(() -> !Rs2GrandExchange.hasBoughtOffer(), 5000);
            this.calculateSmithingOreNeeds();
            this.clearSatisfiedPendingOreBuys();
            return true;
        }

        if (!this.pendingOreBuys.isEmpty() && this.getNextOreToBuy() == null) {
            Microbot.status = "Waiting for ore buys";
            return true;
        }

        return false;
    }

    private boolean hasCollectableOreBuy() {
        for (GrandExchangeOfferDetails details : Rs2GrandExchange.getCompletedOffers().values()) {
            if (this.isExpectedOreBuy(details)) {
                return true;
            }
        }

        for (GrandExchangeSlots slot : Rs2GrandExchange.getActiveOfferSlots()) {
            if (this.isCompletedExpectedOreBuy(Rs2GrandExchange.getOfferDetails(slot))) {
                return true;
            }
        }

        return false;
    }

    private boolean isExpectedOreBuy(GrandExchangeOfferDetails details) {
        return details != null
                && !details.isSelling()
                && this.isExpectedOre(details.getItemName());
    }

    private boolean isCompletedExpectedOreBuy(GrandExchangeOfferDetails details) {
        return this.isExpectedOreBuy(details) && details.isCompleted();
    }

    private void processAvailableExchangeSlots(String desiredPickaxe, String desiredAxe) {
        int availableSlots = Rs2GrandExchange.getAvailableSlotsCount();

        KspTaskDebug.throttled(
                log,
                this.debugLogging,
                "GE Buy",
                "processing-slots",
                5_000L,
                "Processing GE slots | availableSlots={} pendingToolBuys={} pendingSupplyBuys={} pendingOreBuys={} desiredPickaxe={} desiredAxe={} baitBank={} feathersBank={} copperNeeded={} tinNeeded={}",
                availableSlots,
                this.pendingMissingToolBuys,
                this.pendingFishingSupplyBuys,
                this.pendingOreBuys,
                desiredPickaxe,
                desiredAxe,
                this.bankFishingBaitCount,
                this.bankFeatherCount,
                this.copperOreNeeded,
                this.tinOreNeeded
        );

        if (availableSlots <= 0) {
            if (!this.pendingMissingToolBuys.isEmpty()
                    || !this.pendingFishingSupplyBuys.isEmpty()
                    || !this.pendingOreBuys.isEmpty()) {
                Microbot.status = "Waiting for GE buys";
            }
            return;
        }

        boolean placedOffer = false;

        while (availableSlots > 0) {
            Rs2ItemModel outdatedInventoryTool = this.getNextOutdatedInventoryTool(desiredPickaxe, desiredAxe);

            if (outdatedInventoryTool != null) {
                if (!this.placeFallbackSellOffer(outdatedInventoryTool)) {
                    break;
                }

                placedOffer = true;
                availableSlots = Rs2GrandExchange.getAvailableSlotsCount();
                continue;
            }

            String missingTool = this.getNextMissingToolToBuy(desiredPickaxe, desiredAxe);

            if (missingTool != null) {
                int toolPrice = this.getAdjustedBuyPrice(missingTool);
                if (this.canAffordQuantity(1, toolPrice)) {
                    if (!this.placeFallbackBuyOffer(missingTool)) {
                        break;
                    }

                    placedOffer = true;
                    availableSlots = Rs2GrandExchange.getAvailableSlotsCount();
                    continue;
                }

                this.debug("Skipping unaffordable GE tool for current balance | item={} price={} coins={}",
                        missingTool,
                        toolPrice,
                        this.getCurrentSpendableCoins());
            }

            String fishingSupply = this.getNextFishingSupplyToBuy();
            int fishingSupplyQuantity = this.getFishingSupplyQuantityToBuy(fishingSupply);

            if (fishingSupply != null && fishingSupplyQuantity > 0) {
                int unitPrice = this.getAdjustedBuyPrice(fishingSupply);
                int affordableQuantity = this.getAffordableQuantity(fishingSupplyQuantity, unitPrice);
                if (affordableQuantity > 0) {
                    if (!this.placeFishingSupplyBuyOffer(fishingSupply, affordableQuantity)) {
                        break;
                    }

                    placedOffer = true;
                    availableSlots = Rs2GrandExchange.getAvailableSlotsCount();
                    continue;
                }

                this.debug("Skipping unaffordable GE supply for current balance | item={} requested={} unitPrice={} coins={}",
                        fishingSupply,
                        fishingSupplyQuantity,
                        unitPrice,
                        this.getCurrentSpendableCoins());
            }

            this.calculateSmithingOreNeeds();

            String oreToBuy = this.getNextOreToBuy();
            int oreQuantity = this.getOreQuantityToBuy(oreToBuy);

            if (oreToBuy == null || oreQuantity <= 0) {
                break;
            }

            int oreUnitPrice = this.getAdjustedBuyPrice(oreToBuy);
            int affordableOreQuantity = this.getAffordableQuantity(oreQuantity, oreUnitPrice);
            if (affordableOreQuantity <= 0) {
                this.debug("Skipping unaffordable GE ore for current balance | item={} requested={} unitPrice={} coins={}",
                        oreToBuy,
                        oreQuantity,
                        oreUnitPrice,
                        this.getCurrentSpendableCoins());
                break;
            }

            if (!this.placeOreBuyOffer(oreToBuy, affordableOreQuantity)) {
                break;
            }

            placedOffer = true;
            availableSlots = Rs2GrandExchange.getAvailableSlotsCount();
        }

        if (!placedOffer
                && this.pendingMissingToolBuys.isEmpty()
                && this.pendingFishingSupplyBuys.isEmpty()
                && this.pendingOreBuys.isEmpty()
                && this.hasMissingBuyRequirement(desiredPickaxe, desiredAxe)) {
            Microbot.status = "No affordable GE buys remain";
            this.insufficientCoinsForMissingBuys = true;
            this.complete = true;
            return;
        }

        if (!placedOffer
                && (!this.pendingMissingToolBuys.isEmpty()
                || !this.pendingFishingSupplyBuys.isEmpty()
                || !this.pendingOreBuys.isEmpty())) {
            Microbot.status = "Waiting for GE buys";
        }
    }

    private String getNextOreToBuy() {
        if (this.copperOreNeeded > 0 && !this.isOreBuyPending(COPPER_ORE_NAME)) {
            return COPPER_ORE_NAME;
        }

        if (this.tinOreNeeded > 0 && !this.isOreBuyPending(TIN_ORE_NAME)) {
            return TIN_ORE_NAME;
        }

        return null;
    }

    private int getOreQuantityToBuy(String oreName) {
        if (oreName == null) {
            return 0;
        }

        if (oreName.equalsIgnoreCase(COPPER_ORE_NAME)) {
            return this.copperOreNeeded;
        }

        if (oreName.equalsIgnoreCase(TIN_ORE_NAME)) {
            return this.tinOreNeeded;
        }

        return 0;
    }

    private boolean placeOreBuyOffer(String itemName, int quantity) {
        if (itemName == null || quantity <= 0 || this.isOreBuyPending(itemName)) {
            return false;
        }

        this.waitForActionCooldown();

        Microbot.status = "Buying " + quantity + "x " + itemName;

        int buyPrice = this.getAdjustedBuyPrice(itemName);
        quantity = this.getAffordableQuantity(quantity, buyPrice);
        if (quantity <= 0) {
            return false;
        }

        GrandExchangeRequest request = GrandExchangeRequest.builder()
                .action(GrandExchangeAction.BUY)
                .itemName(itemName)
                .exact(true)
                .quantity(quantity)
                .price(buyPrice)
                .closeAfterCompletion(false)
                .build();

        this.waitForGrandExchangeOfferInput();

        boolean offered = Rs2GrandExchange.processOffer(request);

        this.debug(
                "GE ore buy offer | item={} qty={} price={} offered={} slots={}",
                itemName,
                quantity,
                buyPrice,
                offered,
                Rs2GrandExchange.isOpen() ? Rs2GrandExchange.getAvailableSlotsCount() : -1
        );

        if (offered) {
            this.lastActionAtMs = System.currentTimeMillis();
            this.pendingOreBuys.add(itemName);
            this.pendingOreBuyQuantities.merge(this.normalizeItemName(itemName), quantity, Integer::sum);
            BuyScript.sleepUntil(() -> !Rs2GrandExchange.isOfferScreenOpen(), 2000);
        }

        return offered;
    }

    private boolean placeFishingSupplyBuyOffer(String itemName, int quantity) {
        if (itemName == null || quantity <= 0 || this.isFishingSupplyBuyPending(itemName)) {
            return false;
        }

        this.waitForActionCooldown();

        Microbot.status = "Buying " + quantity + "x " + itemName;

        int buyPrice = this.getAdjustedBuyPrice(itemName);
        quantity = this.getAffordableQuantity(quantity, buyPrice);
        if (quantity <= 0) {
            return false;
        }

        GrandExchangeRequest request = GrandExchangeRequest.builder()
                .action(GrandExchangeAction.BUY)
                .itemName(itemName)
                .exact(true)
                .quantity(quantity)
                .price(buyPrice)
                .closeAfterCompletion(false)
                .build();

        this.waitForGrandExchangeOfferInput();

        boolean offered = Rs2GrandExchange.processOffer(request);

        this.debug(
                "GE fishing-supply buy offer | item={} qty={} price={} offered={} slots={}",
                itemName,
                quantity,
                buyPrice,
                offered,
                Rs2GrandExchange.isOpen() ? Rs2GrandExchange.getAvailableSlotsCount() : -1
        );

        if (offered) {
            this.lastActionAtMs = System.currentTimeMillis();
            this.pendingFishingSupplyBuys.add(itemName);
            this.pendingFishingSupplyBuyQuantities.merge(this.normalizeItemName(itemName), quantity, Integer::sum);
            BuyScript.sleepUntil(() -> !Rs2GrandExchange.isOfferScreenOpen(), 2000);
        }

        return offered;
    }

    private void syncPendingOreBuysFromActiveOffers() {
        for (GrandExchangeSlots slot : Rs2GrandExchange.getActiveOfferSlots()) {
            GrandExchangeOfferDetails details = Rs2GrandExchange.getOfferDetails(slot);

            if (details == null || details.isSelling() || details.getItemName() == null) {
                continue;
            }

            String itemName = details.getItemName();

            if (this.isExpectedOre(itemName) && !this.isOreBuyPending(itemName)) {
                this.pendingOreBuys.add(itemName);
                this.pendingOreBuyQuantities.putIfAbsent(
                        this.normalizeItemName(itemName),
                        Math.max(0, details.getTotalQuantity())
                );
            }
        }
    }

    private void syncPendingFishingSupplyBuysFromActiveOffers() {
        for (GrandExchangeSlots slot : Rs2GrandExchange.getActiveOfferSlots()) {
            GrandExchangeOfferDetails details = Rs2GrandExchange.getOfferDetails(slot);

            if (details == null || details.isSelling() || details.getItemName() == null) {
                continue;
            }

            String itemName = details.getItemName();

            if (this.isExpectedFishingSupply(itemName) && !this.isFishingSupplyBuyPending(itemName)) {
                this.pendingFishingSupplyBuys.add(itemName);
                this.pendingFishingSupplyBuyQuantities.putIfAbsent(
                        this.normalizeItemName(itemName),
                        Math.max(0, details.getTotalQuantity())
                );
            }
        }
    }

    private boolean hasCollectableFishingSupplyBuy() {
        for (GrandExchangeOfferDetails details : Rs2GrandExchange.getCompletedOffers().values()) {
            if (this.isExpectedFishingSupplyBuy(details)) {
                return true;
            }
        }

        for (GrandExchangeSlots slot : Rs2GrandExchange.getActiveOfferSlots()) {
            if (this.isCompletedExpectedFishingSupplyBuy(Rs2GrandExchange.getOfferDetails(slot))) {
                return true;
            }
        }

        return false;
    }

    private boolean isExpectedFishingSupplyBuy(GrandExchangeOfferDetails details) {
        return details != null
                && !details.isSelling()
                && this.isExpectedFishingSupply(details.getItemName());
    }

    private boolean isCompletedExpectedFishingSupplyBuy(GrandExchangeOfferDetails details) {
        return this.isExpectedFishingSupplyBuy(details) && details.isCompleted();
    }

    private void markCompletedPendingFishingSupplyBuys() {
        for (GrandExchangeOfferDetails details : Rs2GrandExchange.getCompletedOffers().values()) {
            if (details == null || details.isSelling() || details.getItemName() == null) {
                continue;
            }

            this.markPendingFishingSupplyBought(details.getItemName(), details.getQuantitySold());
        }

        for (GrandExchangeSlots slot : Rs2GrandExchange.getActiveOfferSlots()) {
            GrandExchangeOfferDetails details = Rs2GrandExchange.getOfferDetails(slot);

            if (details == null
                    || details.isSelling()
                    || !details.isCompleted()
                    || details.getItemName() == null) {
                continue;
            }

            this.markPendingFishingSupplyBought(details.getItemName(), details.getQuantitySold());
        }
    }

    private boolean markPendingFishingSupplyBought(String itemName, int collectedQuantity) {
        Iterator<String> iterator = this.pendingFishingSupplyBuys.iterator();

        while (iterator.hasNext()) {
            String pendingSupply = iterator.next();

            if (!pendingSupply.equalsIgnoreCase(itemName)) {
                continue;
            }

            this.cachePendingFishingSupplyBuy(pendingSupply, collectedQuantity);
            iterator.remove();
            return true;
        }

        return false;
    }

    private void cachePendingFishingSupplyBuy(String itemName, int collectedQuantity) {
        int quantity = collectedQuantity > 0
                ? collectedQuantity
                : this.pendingFishingSupplyBuyQuantities.getOrDefault(this.normalizeItemName(itemName), 0);

        if (quantity <= 0) {
            return;
        }

        if (FISHING_BAIT_NAME.equalsIgnoreCase(itemName)) {
            this.bankFishingBaitCount += quantity;
        } else if (FEATHER_NAME.equalsIgnoreCase(itemName)) {
            this.bankFeatherCount += quantity;
        } else if (LEATHER_NAME.equalsIgnoreCase(itemName)) {
            this.bankLeatherCount += quantity;
        } else if (THREAD_NAME.equalsIgnoreCase(itemName)) {
            this.bankThreadCount += quantity;
        } else if (NEEDLE_NAME.equalsIgnoreCase(itemName)) {
            this.bankHasNeedle = true;
        } else if (GOLD_BAR_NAME.equalsIgnoreCase(itemName)) {
            this.bankGoldBarCount += quantity;
        } else if (RING_MOULD_NAME.equalsIgnoreCase(itemName)) {
            this.bankHasRingMould = true;
        } else if (NECKLACE_MOULD_NAME.equalsIgnoreCase(itemName)) {
            this.bankHasNecklaceMould = true;
        } else if (CHISEL_NAME.equalsIgnoreCase(itemName)) {
            this.bankHasChisel = true;
        } else if (TIARA_MOULD_NAME.equalsIgnoreCase(itemName)) {
            this.bankHasTiaraMould = true;
        } else if (BRONZE_SWORD_NAME.equalsIgnoreCase(itemName)) {
            this.bankHasBronzeSword = true;
        } else if (WOODEN_SHIELD_NAME.equalsIgnoreCase(itemName)) {
            this.bankHasWoodenShield = true;
        } else if (TROUT_NAME.equalsIgnoreCase(itemName)) {
            this.bankTroutCount += quantity;
        } else if (SALMON_NAME.equalsIgnoreCase(itemName)) {
            this.bankSalmonCount += quantity;
        }

        this.pendingFishingSupplyBuyQuantities.remove(this.normalizeItemName(itemName));
        this.bankToolsAudited = true;
    }

    private void clearSatisfiedPendingFishingSupplyBuys() {
        Iterator<String> iterator = this.pendingFishingSupplyBuys.iterator();

        while (iterator.hasNext()) {
            String pendingSupply = iterator.next();

            if (this.isFishingSupplyStillNeeded(pendingSupply)) {
                continue;
            }

            this.pendingFishingSupplyBuyQuantities.remove(this.normalizeItemName(pendingSupply));
            iterator.remove();
        }
    }

    private boolean isFishingSupplyStillNeeded(String itemName) {
        return this.getFishingSupplyQuantityToBuy(itemName) > 0;
    }

    private void markCompletedPendingOreBuys() {
        for (GrandExchangeOfferDetails details : Rs2GrandExchange.getCompletedOffers().values()) {
            if (details == null || details.isSelling() || details.getItemName() == null) {
                continue;
            }

            this.markPendingOreBought(details.getItemName(), details.getQuantitySold());
        }

        for (GrandExchangeSlots slot : Rs2GrandExchange.getActiveOfferSlots()) {
            GrandExchangeOfferDetails details = Rs2GrandExchange.getOfferDetails(slot);

            if (details == null
                    || details.isSelling()
                    || !details.isCompleted()
                    || details.getItemName() == null) {
                continue;
            }

            this.markPendingOreBought(details.getItemName(), details.getQuantitySold());
        }
    }

    private boolean markPendingOreBought(String itemName, int collectedQuantity) {
        Iterator<String> iterator = this.pendingOreBuys.iterator();

        while (iterator.hasNext()) {
            String pendingOre = iterator.next();

            if (!pendingOre.equalsIgnoreCase(itemName)) {
                continue;
            }

            this.cachePendingOreBuy(pendingOre, collectedQuantity);
            iterator.remove();
            return true;
        }

        return false;
    }

    private void cachePendingOreBuy(String itemName, int collectedQuantity) {
        int quantity = collectedQuantity > 0
                ? collectedQuantity
                : this.pendingOreBuyQuantities.getOrDefault(this.normalizeItemName(itemName), 0);

        if (quantity <= 0) {
            return;
        }

        if (COPPER_ORE_NAME.equalsIgnoreCase(itemName)) {
            this.bankCopperOreCount += quantity;
        } else if (TIN_ORE_NAME.equalsIgnoreCase(itemName)) {
            this.bankTinOreCount += quantity;
        }

        this.pendingOreBuyQuantities.remove(this.normalizeItemName(itemName));
        this.bankToolsAudited = true;
    }

    private void clearSatisfiedPendingOreBuys() {
        Iterator<String> iterator = this.pendingOreBuys.iterator();

        while (iterator.hasNext()) {
            String pendingOre = iterator.next();

            if (this.isOreStillNeeded(pendingOre)) {
                continue;
            }

            this.pendingOreBuyQuantities.remove(this.normalizeItemName(pendingOre));
            iterator.remove();
        }
    }

    private boolean isOreStillNeeded(String oreName) {
        if (oreName == null) {
            return false;
        }

        if (COPPER_ORE_NAME.equalsIgnoreCase(oreName)) {
            return this.copperOreNeeded > 0;
        }

        if (TIN_ORE_NAME.equalsIgnoreCase(oreName)) {
            return this.tinOreNeeded > 0;
        }

        return false;
    }

    private boolean hasSmithingOreNeed() {
        return this.copperOreNeeded > 0 || this.tinOreNeeded > 0;
    }

    private int getInventoryCountByName(String itemName) {
        int count = 0;

        for (Rs2ItemModel item : Rs2Inventory.all()) {
            if (item != null && item.getName() != null && item.getName().equalsIgnoreCase(itemName)) {
                count += item.getQuantity();
            }
        }

        return count;
    }

    private boolean isOreBuyPending(String itemName) {
        for (String pendingOre : this.pendingOreBuys) {
            if (pendingOre.equalsIgnoreCase(itemName)) {
                return true;
            }
        }

        return false;
    }

    private boolean isFishingSupplyBuyPending(String itemName) {
        if (itemName == null) {
            return false;
        }

        for (String pendingSupply : this.pendingFishingSupplyBuys) {
            if (pendingSupply.equalsIgnoreCase(itemName)) {
                return true;
            }
        }

        return false;
    }

    private boolean isExpectedOre(String itemName) {
        return itemName != null
                && (itemName.equalsIgnoreCase(COPPER_ORE_NAME)
                || itemName.equalsIgnoreCase(TIN_ORE_NAME));
    }

    private boolean isExpectedFishingSupply(String itemName) {
        return itemName != null
                && (itemName.equalsIgnoreCase(FISHING_BAIT_NAME)
                || itemName.equalsIgnoreCase(FEATHER_NAME)
                || itemName.equalsIgnoreCase(LEATHER_NAME)
                || itemName.equalsIgnoreCase(THREAD_NAME)
                || itemName.equalsIgnoreCase(NEEDLE_NAME)
                || itemName.equalsIgnoreCase(GOLD_BAR_NAME)
                || itemName.equalsIgnoreCase(RING_MOULD_NAME)
                || itemName.equalsIgnoreCase(NECKLACE_MOULD_NAME)
                || itemName.equalsIgnoreCase(CHISEL_NAME)
                || itemName.equalsIgnoreCase(TIARA_MOULD_NAME)
                || itemName.equalsIgnoreCase(BRONZE_SWORD_NAME)
                || itemName.equalsIgnoreCase(WOODEN_SHIELD_NAME)
                || itemName.equalsIgnoreCase(TROUT_NAME)
                || itemName.equalsIgnoreCase(SALMON_NAME));
    }

    private String normalizeItemName(String itemName) {
        return itemName == null ? "" : itemName.trim().toLowerCase(Locale.ENGLISH);
    }

    private void withdrawOutdatedToolsAsNotes(String desiredPickaxe, String desiredAxe) {
        for (String pickaxeName : PICKAXE_NAMES) {
            if (pickaxeName.equalsIgnoreCase(desiredPickaxe)
                    || Rs2Bank.count(pickaxeName) <= 0
                    || Rs2Inventory.isFull()) {
                continue;
            }

            Rs2Bank.withdrawAll(pickaxeName, true);
            BuyScript.sleepUntil(() -> Rs2Inventory.hasItem(pickaxeName, true), BANK_WAIT_TIMEOUT_MS);
        }

        for (String axeName : AXE_NAMES) {
            if (axeName.equalsIgnoreCase(desiredAxe)
                    || Rs2Bank.count(axeName) <= 0
                    || Rs2Inventory.isFull()) {
                continue;
            }

            Rs2Bank.withdrawAll(axeName, true);
            BuyScript.sleepUntil(() -> Rs2Inventory.hasItem(axeName, true), BANK_WAIT_TIMEOUT_MS);
        }
    }

    private boolean ensureGrandExchangeOpen() {
        if (Rs2GrandExchange.isOpen()) {
            return true;
        }

        if (Rs2Bank.isOpen()) {
            Microbot.status = "Closing Bank";
            KspGrandExchangeHelper.closeBankBeforeExchange();
            BuyScript.sleepUntil(() -> !Rs2Bank.isOpen(), 2000);
            return false;
        }

        Microbot.status = "Opening GE";

        this.debug(
                "Opening GE | player={} bankOpen={} geOpen={}",
                Rs2Player.getWorldLocation(),
                Rs2Bank.isOpen(),
                Rs2GrandExchange.isOpen()
        );

        if (KspGrandExchangeHelper.openExchangeDirectly()) {
            BuyScript.sleepUntil(Rs2GrandExchange::isOpen, BANK_WAIT_TIMEOUT_MS);
            return Rs2GrandExchange.isOpen();
        }

        if (this.targetArea.toWorldArea().contains(Rs2Player.getWorldLocation())
                && KspGrandExchangeHelper.interactClerk()) {
            BuyScript.sleepUntil(Rs2GrandExchange::isOpen, BANK_WAIT_TIMEOUT_MS);

            if (Rs2GrandExchange.isOpen()) {
                return true;
            }
        }

        if (KspGrandExchangeHelper.interactClerk()) {
            BuyScript.sleepUntil(Rs2GrandExchange::isOpen, BANK_WAIT_TIMEOUT_MS);
            return Rs2GrandExchange.isOpen();
        }

        return false;
    }

    private boolean placeFallbackSellOffer(Rs2ItemModel item) {
        if (item == null) {
            return false;
        }

        this.waitForActionCooldown();

        Microbot.status = "Selling " + item.getName();

        GrandExchangeRequest request = GrandExchangeRequest.builder()
                .action(GrandExchangeAction.SELL)
                .itemName(item.getName())
                .quantity(item.getQuantity())
                .percent(-10)
                .closeAfterCompletion(false)
                .build();

        this.waitForGrandExchangeOfferInput();

        boolean offered = Rs2GrandExchange.processOffer(request);

        if (offered) {
            this.lastActionAtMs = System.currentTimeMillis();
            BuyScript.sleepUntil(() -> !Rs2Inventory.hasItem(item.getName(), true), 5000);
        }

        this.debug(
                "GE fallback sell offer | item={} qty={} percent=-10 offered={} slots={}",
                item.getName(),
                item.getQuantity(),
                offered,
                Rs2GrandExchange.isOpen() ? Rs2GrandExchange.getAvailableSlotsCount() : -1
        );

        return offered;
    }

    private boolean placeFallbackBuyOffer(String itemName) {
        if (itemName == null || this.isMissingToolBuyPending(itemName)) {
            return false;
        }

        this.waitForActionCooldown();

        Microbot.status = "Buying " + itemName;
        int buyPrice = this.getAdjustedBuyPrice(itemName);
        if (!this.canAffordQuantity(1, buyPrice)) {
            this.debug("Cannot afford GE tool offer | item={} price={} coins={}",
                    itemName,
                    buyPrice,
                    this.getCurrentSpendableCoins());
            return false;
        }

        GrandExchangeRequest request = GrandExchangeRequest.builder()
                .action(GrandExchangeAction.BUY)
                .itemName(itemName)
                .exact(true)
                .quantity(1)
                .price(buyPrice)
                .closeAfterCompletion(false)
                .build();

        this.waitForGrandExchangeOfferInput();

        boolean offered = Rs2GrandExchange.processOffer(request);

        this.debug(
                "GE missing-tool buy offer | item={} qty=1 price={} offered={} slots={}",
                itemName,
                buyPrice,
                offered,
                Rs2GrandExchange.isOpen() ? Rs2GrandExchange.getAvailableSlotsCount() : -1
        );

        if (offered) {
            this.lastActionAtMs = System.currentTimeMillis();
            this.pendingMissingToolBuys.add(itemName);
            BuyScript.sleepUntil(() -> !Rs2GrandExchange.isOfferScreenOpen(), 2000);
        }

        return offered;
    }

    private long getCurrentSpendableCoins() {
        return Math.max(0L, Rs2Inventory.itemQuantity(995));
    }

    private boolean canAffordQuantity(int quantity, int unitPrice) {
        return quantity > 0
                && unitPrice > 0
                && getCurrentSpendableCoins() >= (long) quantity * unitPrice;
    }

    private int getAffordableQuantity(int requestedQuantity, int unitPrice) {
        if (requestedQuantity <= 0 || unitPrice <= 0) {
            return 0;
        }

        long affordableQuantity = getCurrentSpendableCoins() / unitPrice;
        return (int) Math.min(requestedQuantity, Math.min(Integer.MAX_VALUE, affordableQuantity));
    }

    private boolean handlePendingMissingToolBuys(String desiredPickaxe, String desiredAxe) {
        this.clearOwnedPendingMissingToolBuys();

        if (!this.ensureGrandExchangeOpen()) {
            return false;
        }

        if (Rs2GrandExchange.isOfferScreenOpen()) {
            Microbot.status = "Opening GE Overview";
            this.returnToGrandExchangeOverview();
            return true;
        }

        this.syncPendingMissingToolBuysFromActiveOffers(desiredPickaxe, desiredAxe);

        boolean collectableToolBuy = this.hasCollectableMissingToolBuy(desiredPickaxe, desiredAxe);
        boolean collectableFishingSupplyBuy = this.hasCollectableFishingSupplyBuy();
        boolean collectableOreBuy = this.hasCollectableOreBuy();
        boolean collectableSell = Rs2GrandExchange.hasSoldOffer();

        if (collectableToolBuy || collectableFishingSupplyBuy || collectableOreBuy || collectableSell) {
            Microbot.status = "Collecting GE Offers";

            if (collectableToolBuy) {
                this.markCompletedPendingToolBuys();
            }

            if (collectableFishingSupplyBuy) {
                this.markCompletedPendingFishingSupplyBuys();
            }

            if (collectableOreBuy) {
                this.markCompletedPendingOreBuys();
            }

            Rs2GrandExchange.collectAllToBank();

            BuyScript.sleepUntil(
                    () -> !Rs2GrandExchange.hasBoughtOffer() && !Rs2GrandExchange.hasSoldOffer(),
                    5000
            );

            this.clearOwnedPendingMissingToolBuys();
            if (collectableFishingSupplyBuy) {
                this.clearSatisfiedPendingFishingSupplyBuys();
            }
            if (collectableOreBuy) {
                this.calculateSmithingOreNeeds();
                this.clearSatisfiedPendingOreBuys();
            }
            return true;
        }

        return false;
    }

    private void waitForActionCooldown() {
        long remaining = ACTION_COOLDOWN_MS - (System.currentTimeMillis() - this.lastActionAtMs);

        if (remaining > 0L) {
            BuyScript.sleep((int) Math.min(remaining, ACTION_COOLDOWN_MS));
        }
    }

    private void waitForGrandExchangeOfferInput() {
        BuyScript.sleep(GE_OFFER_INPUT_DELAY_MS);
    }

    private void syncPendingMissingToolBuysFromActiveOffers(String desiredPickaxe, String desiredAxe) {
        for (GrandExchangeSlots slot : Rs2GrandExchange.getActiveOfferSlots()) {
            GrandExchangeOfferDetails details = Rs2GrandExchange.getOfferDetails(slot);

            if (details == null || details.isSelling() || details.getItemName() == null) {
                continue;
            }

            String itemName = details.getItemName();

            if (this.isExpectedMissingTool(itemName, desiredPickaxe, desiredAxe)
                    && !this.isMissingToolBuyPending(itemName)) {
                this.pendingMissingToolBuys.add(itemName);
            }
        }
    }

    private boolean hasCollectableMissingToolBuy(String desiredPickaxe, String desiredAxe) {
        for (GrandExchangeOfferDetails details : Rs2GrandExchange.getCompletedOffers().values()) {
            if (this.isExpectedMissingToolBuy(details, desiredPickaxe, desiredAxe)) {
                return true;
            }
        }

        for (GrandExchangeSlots slot : Rs2GrandExchange.getActiveOfferSlots()) {
            if (this.isCompletedExpectedMissingToolBuy(
                    Rs2GrandExchange.getOfferDetails(slot),
                    desiredPickaxe,
                    desiredAxe
            )) {
                return true;
            }
        }

        return false;
    }

    private boolean isExpectedMissingToolBuy(GrandExchangeOfferDetails details, String desiredPickaxe, String desiredAxe) {
        return details != null
                && !details.isSelling()
                && this.isExpectedMissingTool(details.getItemName(), desiredPickaxe, desiredAxe);
    }

    private boolean isCompletedExpectedMissingToolBuy(
            GrandExchangeOfferDetails details,
            String desiredPickaxe,
            String desiredAxe
    ) {
        return this.isExpectedMissingToolBuy(details, desiredPickaxe, desiredAxe) && details.isCompleted();
    }

    private void markCompletedPendingToolBuys() {
        boolean markedAny = false;

        for (GrandExchangeOfferDetails details : Rs2GrandExchange.getCompletedOffers().values()) {
            if (details == null || details.isSelling() || details.getItemName() == null) {
                continue;
            }

            if (this.markPendingToolBought(details.getItemName())) {
                markedAny = true;
            }
        }

        for (GrandExchangeSlots slot : Rs2GrandExchange.getActiveOfferSlots()) {
            GrandExchangeOfferDetails details = Rs2GrandExchange.getOfferDetails(slot);

            if (details == null
                    || details.isSelling()
                    || !details.isCompleted()
                    || details.getItemName() == null) {
                continue;
            }

            if (this.markPendingToolBought(details.getItemName())) {
                markedAny = true;
            }
        }

        if (!markedAny && Rs2GrandExchange.hasBoughtOffer() && this.pendingMissingToolBuys.size() == 1) {
            String pendingTool = this.pendingMissingToolBuys.remove(0);
            this.markDesiredToolBought(pendingTool);
        }
    }

    private boolean markPendingToolBought(String itemName) {
        Iterator<String> iterator = this.pendingMissingToolBuys.iterator();

        while (iterator.hasNext()) {
            String pendingTool = iterator.next();

            if (!pendingTool.equalsIgnoreCase(itemName)) {
                continue;
            }

            this.markDesiredToolBought(pendingTool);
            iterator.remove();
            return true;
        }

        return false;
    }

    private void clearOwnedPendingMissingToolBuys() {
        Iterator<String> iterator = this.pendingMissingToolBuys.iterator();

        while (iterator.hasNext()) {
            String pendingTool = iterator.next();

            if (!this.hasToolAnywhere(pendingTool)) {
                continue;
            }

            this.markDesiredToolBought(pendingTool);
            iterator.remove();
        }
    }

    private void markDesiredToolBought(String toolName) {
        if (Objects.equals(toolName, this.lastDesiredPickaxe)) {
            this.bankHasDesiredPickaxe = true;
        }

        if (Objects.equals(toolName, this.lastDesiredAxe)) {
            this.bankHasDesiredAxe = true;
        }

        if (HAMMER_NAME.equalsIgnoreCase(toolName)) {
            this.bankHasHammer = true;
        }

        if (TINDERBOX_NAME.equalsIgnoreCase(toolName)) {
            this.bankHasTinderbox = true;
        }

        if (SMALL_FISHING_NET_NAME.equalsIgnoreCase(toolName)) {
            this.bankHasSmallFishingNet = true;
        }

        if (FISHING_ROD_NAME.equalsIgnoreCase(toolName)) {
            this.bankHasFishingRod = true;
        }

        if (FLY_FISHING_ROD_NAME.equalsIgnoreCase(toolName)) {
            this.bankHasFlyFishingRod = true;
        }

        if (HARPOON_NAME.equalsIgnoreCase(toolName)) {
            this.bankHasHarpoon = true;
        }

        if (LOBSTER_POT_NAME.equalsIgnoreCase(toolName)) {
            this.bankHasLobsterPot = true;
        }

        this.bankToolsAudited = true;
    }

    private void returnToGrandExchangeOverview() {
        if (System.currentTimeMillis() - this.lastActionAtMs < ACTION_COOLDOWN_MS) {
            return;
        }

        Rs2GrandExchange.backToOverview();
        this.lastActionAtMs = System.currentTimeMillis();

        BuyScript.sleepUntil(() -> !Rs2GrandExchange.isOfferScreenOpen(), 2000);
    }

    private Rs2ItemModel getNextOutdatedInventoryTool(String desiredPickaxe, String desiredAxe) {
        for (Rs2ItemModel item : Rs2Inventory.all()) {
            if (item == null || item.getName() == null) {
                continue;
            }

            if (this.isOutdatedToolName(item.getName(), desiredPickaxe, desiredAxe)) {
                return item;
            }
        }

        return null;
    }

    private boolean hasOutdatedToolInBank(String desiredPickaxe, String desiredAxe) {
        for (String pickaxeName : PICKAXE_NAMES) {
            if (this.isOutdatedToolName(pickaxeName, desiredPickaxe, desiredAxe)
                    && Rs2Bank.count(pickaxeName) > 0) {
                return true;
            }
        }

        for (String axeName : AXE_NAMES) {
            if (this.isOutdatedToolName(axeName, desiredPickaxe, desiredAxe)
                    && Rs2Bank.count(axeName) > 0) {
                return true;
            }
        }

        return false;
    }

    private boolean hasOutdatedToolEquipped(String desiredPickaxe, String desiredAxe) {
        for (String pickaxeName : PICKAXE_NAMES) {
            if (this.isOutdatedToolName(pickaxeName, desiredPickaxe, desiredAxe)
                    && Rs2Equipment.isWearing(new String[]{pickaxeName})) {
                return true;
            }
        }

        for (String axeName : AXE_NAMES) {
            if (this.isOutdatedToolName(axeName, desiredPickaxe, desiredAxe)
                    && Rs2Equipment.isWearing(new String[]{axeName})) {
                return true;
            }
        }

        return false;
    }

    private boolean unequipOutdatedTools() {
        if (Rs2Tab.getCurrentTab() != InterfaceTab.EQUIPMENT) {
            Rs2Tab.switchTo(InterfaceTab.EQUIPMENT);
            BuyScript.sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.EQUIPMENT, 1500);
        }

        Rs2Equipment.unEquip(new EquipmentInventorySlot[]{EquipmentInventorySlot.WEAPON});
        BuyScript.sleep(250);

        if (Rs2Tab.getCurrentTab() != InterfaceTab.INVENTORY) {
            Rs2Tab.switchTo(InterfaceTab.INVENTORY);
            BuyScript.sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.INVENTORY, 1500);
        }

        return !Rs2Equipment.isWearing("pickaxe", false)
                && !Rs2Equipment.isWearing(new String[]{"pickaxe"})
                && !Rs2Equipment.isWearing("axe", false)
                && !Rs2Equipment.isWearing(new String[]{"axe"});
    }

    private String getNextMissingToolToBuy(String desiredPickaxe, String desiredAxe) {
        if (!this.hasToolAnywhere(desiredPickaxe) && !this.isMissingToolBuyPending(desiredPickaxe)) {
            return desiredPickaxe;
        }

        if (!this.hasToolAnywhere(desiredAxe) && !this.isMissingToolBuyPending(desiredAxe)) {
            return desiredAxe;
        }

        if (!this.hasHammerAnywhere() && !this.isMissingToolBuyPending(HAMMER_NAME)) {
            return HAMMER_NAME;
        }

        if (!this.hasTinderboxAnywhere() && !this.isMissingToolBuyPending(TINDERBOX_NAME)) {
            return TINDERBOX_NAME;
        }

        if (this.shouldBuyFishingTool(SMALL_FISHING_NET_NAME)
                && !this.hasFishingToolInBank(SMALL_FISHING_NET_NAME)
                && !this.isMissingToolBuyPending(SMALL_FISHING_NET_NAME)) {
            return SMALL_FISHING_NET_NAME;
        }

        if (this.shouldBuyFishingTool(FISHING_ROD_NAME)
                && !this.hasFishingToolInBank(FISHING_ROD_NAME)
                && !this.isMissingToolBuyPending(FISHING_ROD_NAME)) {
            return FISHING_ROD_NAME;
        }

        if (!this.hasFishingToolInBank(FLY_FISHING_ROD_NAME) && !this.isMissingToolBuyPending(FLY_FISHING_ROD_NAME)) {
            return FLY_FISHING_ROD_NAME;
        }

        if (!this.hasFishingToolInBank(HARPOON_NAME) && !this.isMissingToolBuyPending(HARPOON_NAME)) {
            return HARPOON_NAME;
        }

        if (!this.hasFishingToolInBank(LOBSTER_POT_NAME) && !this.isMissingToolBuyPending(LOBSTER_POT_NAME)) {
            return LOBSTER_POT_NAME;
        }

        return null;
    }

    private boolean isMissingToolBuyPending(String toolName) {
        for (String pendingTool : this.pendingMissingToolBuys) {
            if (pendingTool.equalsIgnoreCase(toolName)) {
                return true;
            }
        }

        return false;
    }

    private boolean isExpectedMissingTool(String itemName, String desiredPickaxe, String desiredAxe) {
        return itemName != null
                && (itemName.equalsIgnoreCase(desiredPickaxe)
                || itemName.equalsIgnoreCase(desiredAxe)
                || itemName.equalsIgnoreCase(HAMMER_NAME)
                || itemName.equalsIgnoreCase(TINDERBOX_NAME)
                || itemName.equalsIgnoreCase(SMALL_FISHING_NET_NAME)
                || itemName.equalsIgnoreCase(FISHING_ROD_NAME)
                || itemName.equalsIgnoreCase(FLY_FISHING_ROD_NAME)
                || itemName.equalsIgnoreCase(HARPOON_NAME)
                || itemName.equalsIgnoreCase(LOBSTER_POT_NAME));
    }

    private boolean shouldBuyFishingTool(String itemName) {
        if (itemName == null) {
            return false;
        }

        if (SMALL_FISHING_NET_NAME.equalsIgnoreCase(itemName) || FISHING_ROD_NAME.equalsIgnoreCase(itemName)) {
            return this.shouldKeepBasicFishingKit();
        }

        return true;
    }

    private boolean hasToolAnywhere(String toolName) {
        if (HAMMER_NAME.equalsIgnoreCase(toolName)) {
            return this.hasHammerAnywhere();
        }

        if (TINDERBOX_NAME.equalsIgnoreCase(toolName)) {
            return this.hasTinderboxAnywhere();
        }

        if (this.isFishingToolName(toolName)) {
            return this.hasFishingToolInBank(toolName);
        }

        return Rs2Equipment.isWearing(new String[]{toolName})
                || Rs2Inventory.hasItem(new String[]{toolName})
                || Rs2Inventory.hasItem(toolName, true)
                || Rs2Bank.isOpen() && Rs2Bank.count(toolName) > 0
                || this.isToolCachedInBank(toolName);
    }

    private boolean hasHammerAnywhere() {
        return this.hasItemIdInInventory(HAMMER_ITEM_ID)
                || Rs2Bank.isOpen() && this.hasItemIdInBank(HAMMER_ITEM_ID)
                || this.isToolCachedInBank(HAMMER_NAME);
    }

    private boolean hasTinderboxAnywhere() {
        return Rs2Inventory.hasItem(TINDERBOX_NAME)
                || Rs2Inventory.hasItem(TINDERBOX_NAME, true)
                || Rs2Bank.isOpen() && Rs2Bank.count(TINDERBOX_NAME) > 0
                || this.isToolCachedInBank(TINDERBOX_NAME);
    }

    private boolean hasFishingToolInBank(String toolName) {
        if (toolName == null) {
            return false;
        }

        return Rs2Bank.isOpen() && Rs2Bank.count(toolName) > 0
                || this.isToolCachedInBank(toolName);
    }

    private boolean isFishingToolName(String toolName) {
        return toolName != null
                && (SMALL_FISHING_NET_NAME.equalsIgnoreCase(toolName)
                || FISHING_ROD_NAME.equalsIgnoreCase(toolName)
                || FLY_FISHING_ROD_NAME.equalsIgnoreCase(toolName)
                || HARPOON_NAME.equalsIgnoreCase(toolName)
                || LOBSTER_POT_NAME.equalsIgnoreCase(toolName));
    }

    private boolean hasItemIdInInventory(int itemId) {
        return Buy.hasItemIdInInventory(itemId);
    }

    private boolean hasItemIdInBank(int itemId) {
        return Buy.hasItemIdInBank(itemId);
    }

    private boolean isToolCachedInBank(String toolName) {
        if (!this.bankToolsAudited) {
            return false;
        }

        if (Objects.equals(toolName, this.lastDesiredPickaxe)) {
            return this.bankHasDesiredPickaxe;
        }

        if (Objects.equals(toolName, this.lastDesiredAxe)) {
            return this.bankHasDesiredAxe;
        }

        if (HAMMER_NAME.equalsIgnoreCase(toolName)) {
            return this.bankHasHammer;
        }

        if (TINDERBOX_NAME.equalsIgnoreCase(toolName)) {
            return this.bankHasTinderbox;
        }

        if (SMALL_FISHING_NET_NAME.equalsIgnoreCase(toolName)) {
            return this.bankHasSmallFishingNet;
        }

        if (FISHING_ROD_NAME.equalsIgnoreCase(toolName)) {
            return this.bankHasFishingRod;
        }

        if (FLY_FISHING_ROD_NAME.equalsIgnoreCase(toolName)) {
            return this.bankHasFlyFishingRod;
        }

        if (HARPOON_NAME.equalsIgnoreCase(toolName)) {
            return this.bankHasHarpoon;
        }

        if (LOBSTER_POT_NAME.equalsIgnoreCase(toolName)) {
            return this.bankHasLobsterPot;
        }

        return false;
    }

    private boolean isOutdatedToolName(String itemName, String desiredPickaxe, String desiredAxe) {
        return Buy.isOutdatedToolName(itemName, desiredPickaxe, desiredAxe);
    }

    private String resolveDesiredPickaxeName() {
        return Buy.resolveDesiredPickaxeNameForBuy();
    }

    private String resolveDesiredAxeName() {
        return Buy.resolveDesiredAxeNameForBuy();
    }

    private int getAdjustedSellPrice(Rs2ItemModel item) {
        int guidePrice = this.getGuidePrice(item);

        if (guidePrice <= 0) {
            return 1;
        }

        return Math.max(1, (int) ((long) guidePrice * 90L / 100L));
    }

    private int getAdjustedBuyPrice(String itemName) {
        if (HAMMER_NAME.equalsIgnoreCase(itemName)) {
            return HAMMER_BUY_PRICE;
        }

        if (HARPOON_NAME.equalsIgnoreCase(itemName)) {
            return HARPOON_BUY_PRICE;
        }

        int guidePrice = this.getGuidePrice(itemName);

        if (guidePrice <= 0) {
            return 1;
        }

        return Math.max(1, (int) (((long) guidePrice * 110L + 99L) / 100L));
    }

    private int getGuidePrice(String itemName) {
        if (itemName == null || itemName.isEmpty()) {
            return 1;
        }

        try {
            List<ItemPrice> matches = Microbot.getItemManager().search(itemName);

            if (matches == null || matches.isEmpty()) {
                return 1;
            }

            for (ItemPrice match : matches) {
                if (match != null && itemName.equalsIgnoreCase(match.getName())) {
                    return this.getGuidePrice(match);
                }
            }

            return this.getGuidePrice(matches.get(0));
        } catch (Exception ex) {
            this.debug("Unable to resolve guide price for {}: {}", itemName, ex.getMessage());
            return 1;
        }
    }

    private int getGuidePrice(ItemPrice itemPrice) {
        if (itemPrice == null) {
            return 1;
        }

        int gePrice = Rs2GrandExchange.getPrice(itemPrice.getId());

        if (gePrice > 0) {
            return gePrice;
        }

        return itemPrice.getPrice();
    }

    private int getGuidePrice(Rs2ItemModel item) {
        if (item == null) {
            return 1;
        }

        int unnotedId = item.getUnNotedId();
        int gePrice = unnotedId > 0 ? Rs2GrandExchange.getPrice(unnotedId) : 0;

        if (gePrice > 0) {
            return gePrice;
        }

        int linkedId = item.getLinkedId();
        gePrice = linkedId > 0 ? Rs2GrandExchange.getPrice(linkedId) : 0;

        if (gePrice > 0) {
            return gePrice;
        }

        gePrice = Rs2GrandExchange.getPrice(item.getId());

        if (gePrice > 0) {
            return gePrice;
        }

        int itemModelPrice = item.getPrice();

        if (itemModelPrice > 0) {
            return itemModelPrice;
        }

        return this.getGuidePrice(item.getName());
    }

    private WorldPoint getAreaCenter() {
        int centerX = (this.targetArea.getSouthWest().getX() + this.targetArea.getNorthEast().getX()) / 2;
        int centerY = (this.targetArea.getSouthWest().getY() + this.targetArea.getNorthEast().getY()) / 2;
        int plane = this.targetArea.getSouthWest().getPlane();

        return new WorldPoint(centerX, centerY, plane);
    }

    private void debug(String message, Object... args) {
        if (this.debugLogging) {
            KspTaskDebug.info(log, true, "GE Buy", message, args);
        }
    }

    private static final class BuyBudget {
        private final long bankCoins;
        private final long inventoryCoins;
        private final StringBuilder details = new StringBuilder();

        private long estimatedCost;

        private BuyBudget(long bankCoins, long inventoryCoins) {
            this.bankCoins = bankCoins;
            this.inventoryCoins = inventoryCoins;
        }

        private long getAvailableCoins() {
            return this.bankCoins + this.inventoryCoins;
        }

        private boolean hasEnoughCoins() {
            return this.getAvailableCoins() >= this.estimatedCost;
        }

        private void addDetail(int quantity, String itemName, int unitPrice, long totalPrice) {
            if (this.details.length() > 0) {
                this.details.append(", ");
            }

            this.details
                    .append(quantity)
                    .append("x ")
                    .append(itemName)
                    .append(" @ ")
                    .append(unitPrice)
                    .append("gp = ")
                    .append(totalPrice)
                    .append("gp");
        }

        private String getDetails() {
            return this.details.length() == 0 ? "none" : this.details.toString();
        }
    }

    public void shutdown() {
        this.lastWebWalkAtMs = 0L;
        this.lastActionAtMs = 0L;

        this.bankToolsAudited = false;
        this.bankHasDesiredPickaxe = false;
        this.bankHasDesiredAxe = false;
        this.bankHasHammer = false;
        this.bankHasTinderbox = false;
        this.bankHasSmallFishingNet = false;
        this.bankHasFishingRod = false;
        this.bankHasFlyFishingRod = false;
        this.bankHasHarpoon = false;
        this.bankHasLobsterPot = false;
        this.bankFishingBaitCount = 0;
        this.bankFeatherCount = 0;
        this.bankLeatherCount = 0;
        this.bankThreadCount = 0;
        this.bankHasNeedle = false;
        this.leatherNeeded = 0;
        this.bankGoldBarCount = 0;
        this.bankHasRingMould = false;
        this.bankHasNecklaceMould = false;
        this.bankHasChisel = false;
        this.bankHasTiaraMould = false;
        this.goldBarsToBuy = 0;
        this.bankHasBronzeSword = false;
        this.bankHasWoodenShield = false;
        this.bankTroutCount = 0;
        this.bankSalmonCount = 0;

        this.lastDesiredPickaxe = null;
        this.lastDesiredAxe = null;

        this.pendingMissingToolBuys.clear();
        this.pendingFishingSupplyBuys.clear();
        this.pendingOreBuys.clear();
        this.pendingFishingSupplyBuyQuantities.clear();
        this.pendingOreBuyQuantities.clear();

        this.requiredBronzeBars = 0;
        this.leatherNeeded = 0;
        this.copperOreNeeded = 0;
        this.tinOreNeeded = 0;
        this.bankCopperOreCount = 0;
        this.bankTinOreCount = 0;

        this.complete = false;
        this.insufficientCoinsForMissingBuys = false;
        KspWalkerGuard.clear("GE Buy:target-area");

        super.shutdown();
    }

    public boolean isComplete() {
        return this.complete;
    }

    public GEArea getTargetArea() {
        return this.targetArea;
    }
}
