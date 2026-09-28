/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  javax.inject.Singleton
 *  net.runelite.api.MenuAction
 *  net.runelite.api.Skill
 *  net.runelite.api.coords.WorldPoint
 *  net.runelite.api.widgets.Widget
 *  net.runelite.client.plugins.Plugin
 *  net.runelite.client.plugins.microbot.Microbot
 *  net.runelite.client.plugins.microbot.Script
 *  net.runelite.client.plugins.microbot.util.bank.Rs2Bank
 *  net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeAction
 *  net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeRequest
 *  net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange
 *  net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory
 *  net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel
 *  net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard
 *  net.runelite.client.plugins.microbot.util.math.Rs2Random
 *  net.runelite.client.plugins.microbot.util.menu.NewMenuEntry
 *  net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper
 *  net.runelite.client.plugins.microbot.util.npc.Rs2Npc
 *  net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel
 *  net.runelite.client.plugins.microbot.util.player.Rs2Player
 *  net.runelite.client.plugins.microbot.util.walker.Rs2Walker
 *  net.runelite.client.plugins.microbot.util.widget.Rs2Widget
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.sellscript;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspAccountPlayTimeCache;
import net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.KspGrandExchangeHelper;
import net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.KspBankWidgetHelper;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspTaskDebug;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspWalkerGuard;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.cooksassistant.reqs.Items;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.goblindip.reqs.GobReqs;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.buyscript.Buy;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.buyscript.BuyScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.gearea.GEArea;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.sell.SellList;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.sellscript.SellState;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeAction;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeRequest;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class SellScript
extends Script {
    private static final Logger log = LoggerFactory.getLogger(SellScript.class);
    private static final int LOOP_DELAY_MS = 600;
    private static final int WEB_WALK_COOLDOWN_MS = 3000;
    private static final int ACTION_COOLDOWN_MS = 1200;
    private static final int GE_OFFER_INPUT_DELAY_MS = 900;
    private static final int INVENTORY_WAIT_TIMEOUT_MS = 3000;
    private static final int OFFER_SCREEN_WAIT_TIMEOUT_MS = 3000;
    private static final int TRADE_RESTRICTION_CACHE_MS = 300000;
    private static final int TRADE_RESTRICTION_MIN_TOTAL_LEVEL = 100;
    private static final int TRADE_RESTRICTION_MIN_QUEST_POINTS = 10;
    private static final int TRADE_RESTRICTION_MIN_HOURS_PLAYED = 20;
    private static final int MAX_WITHDRAW_FAILURES = 3;
    private static final int SIDE_JOURNAL_TAB_CONTAINER_WIDGET_ID = 41222157;
    private static final int ACCOUNT_SUMMARY_WIDGET_ID = 46661633;
    private static final int TIME_PLAYED_WIDGET_ID = 46661634;
    private static final String[] PICKAXE_NAMES = Buy.PICKAXE_NAMES;
    private static final String[] AXE_NAMES = Buy.AXE_NAMES;
    private static final long BUY_AFFORDABILITY_CACHE_MS = 3_000L;
    private static final Set<SellList> PROTECTED_SKILL_RESOURCES = EnumSet.of(
            SellList.LOGS,
            SellList.OAK_LOGS,
            SellList.YEW_LOGS,
            SellList.COWHIDE,
            SellList.RAW_CHICKEN,
            SellList.SMALL_FISHING_NET,
            SellList.FISHING_ROD,
            SellList.FISHING_BAIT,
            SellList.SILVER_ORE,
            SellList.SILVER_BAR,
            SellList.LIMPWURT_ROOT,
            SellList.EARTH_TALISMAN,
            SellList.BODY_TALISMAN,
            SellList.TUNA,
            SellList.LOBSTER,
            SellList.SWORDFISH,
            SellList.SPINACH_ROLL);
    @Inject
    private KspAccountPlayTimeCache accountPlayTimeCache;
    @Inject
    private BuyScript buyScript;
    private GEArea targetArea = GEArea.GRAND_EXCHANGE;
    private boolean debugLogging;
    private long lastWebWalkAtMs;
    private long lastActionAtMs;
    private final Set<String> blockedSellItems = new HashSet<String>();
    private final Map<String, Integer> withdrawFailureCounts = new HashMap<String, Integer>();
    private Boolean tradeRestrictionUnlockedCache;
    private Integer cachedHoursPlayed;
    private long lastTradeRestrictionCheckAtMs;
    private long lastBuyAffordabilityCheckAtMs;
    private boolean cachedBuyAffordability;
    private boolean attemptedHoursPlayedLookup;
    private SellState state = SellState.GOING_TO_GE;
    private boolean complete;

    public void setDebugLogging(boolean debugLogging) {
        this.debugLogging = debugLogging;
    }

    public boolean run(GEArea area) {
        this.shutdown();
        this.targetArea = area;
        this.complete = false;
        Microbot.status = "Walking to GE";
        this.mainScheduledFuture = this.scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!super.run() || !Microbot.isLoggedIn()) {
                return;
            }
            if (this.complete) {
                Microbot.status = "GE Sell Complete";
                return;
            }
            if (this.completeIfBankHasNothingToSell()) {
                return;
            }
            this.updateState();
            KspTaskDebug.throttled(log, this.debugLogging, "GE Sell", "loop", 5_000L,
                    "loop | state={} complete={} player={} moving={} interacting={} bankOpen={} geOpen={} offerScreen={} slots={} hasInvSellable={} hasBankSellable={} blockedItems={}",
                    this.state,
                    this.complete,
                    Rs2Player.getWorldLocation(),
                    Rs2Player.isMoving(),
                    Rs2Player.isInteracting(),
                    Rs2Bank.isOpen(),
                    Rs2GrandExchange.isOpen(),
                    Rs2GrandExchange.isOfferScreenOpen(),
                    Rs2GrandExchange.isOpen() ? Rs2GrandExchange.getAvailableSlotsCount() : -1,
                    this.hasSellableInventoryItems(),
                    this.hasSellableBankItems(),
                    this.blockedSellItems);
            switch (this.state) {
                case GOING_TO_GE: {
                    if (!this.ensureInTargetArea()) {
                        return;
                    }
                    this.state = SellState.RESTOCKING_FROM_BANK;
                    return;
                }
                case RESTOCKING_FROM_BANK: {
                    if (!this.hasSellableInventoryItems()) {
                        this.prepareSellInventoryFromBank();
                        return;
                    }
                    this.state = SellState.SELLING_ITEMS;
                    return;
                }
                case SELLING_ITEMS: {
                    this.handleSellingItems();
                    return;
                }
            }
        }, 0L, 600L, TimeUnit.MILLISECONDS);
        return true;
    }

    private boolean completeIfBankHasNothingToSell() {
        if (!Rs2Bank.isOpen()) {
            return false;
        }

        if (this.hasSellableInventoryItems() || this.hasSellableBankItems()) {
            return false;
        }

        this.complete = true;
        this.state = SellState.GOING_TO_GE;
        Microbot.status = "GE Sell Skipped";
        this.debug("Skipping GE sell; bank contains no sellable items | blockedItems={}", this.blockedSellItems);
        return true;
    }

    private boolean ensureInTargetArea() {
        if (this.targetArea.toWorldArea().contains(Rs2Player.getWorldLocation())) {
            KspWalkerGuard.clear("GE Sell:target-area");
            return true;
        }
        if (Rs2Player.isMoving()) {
            return false;
        }
        Microbot.status = "Walking to GE";
        if (KspWalkerGuard.walkToDestination(
                "GE Sell:target-area",
                this.targetArea::getRandomPoint,
                this.targetArea.toWorldArea()::contains,
                2,
                WEB_WALK_COOLDOWN_MS)) {
            this.lastWebWalkAtMs = System.currentTimeMillis();
            this.debug("Requested GE sell area walk | player={} area={}",
                    Rs2Player.getWorldLocation(),
                    this.targetArea.getDisplayName());
        }
        return false;
    }

    private void prepareSellInventoryFromBank() {
        if (!Rs2Bank.isOpen()) {
            if (Rs2GrandExchange.isOpen()) {
                if (this.shouldWaitAtGrandExchange()) {
                    this.state = SellState.SELLING_ITEMS;
                    Microbot.status = "Waiting for GE Slot";
                    return;
                }
                Microbot.status = "Closing GE";
                Rs2GrandExchange.closeExchange();
                SellScript.sleepUntil(() -> !Rs2GrandExchange.isOpen(), (int)2000);
                return;
            }
            if (this.targetArea.toWorldArea().contains(Rs2Player.getWorldLocation())) {
                Microbot.status = "Using GE Bank";
                Rs2Bank.walkToBankAndUseBank();
                SellScript.sleepUntil(Rs2Bank::isOpen, (int)3000);
                return;
            }
            Microbot.status = "Opening Bank";
            if (Rs2Bank.openBank()) {
                SellScript.sleepUntil(Rs2Bank::isOpen, (int)3000);
            } else {
                Microbot.status = "Walking to Bank";
                Rs2Bank.walkToBankAndUseBank();
            }
            return;
        }
        Microbot.status = "Withdrawing Sell Items";
        this.debug("Preparing sell inventory | bankOpen={} withdrawAsNote={} sellableBankItems={} inventoryEmpty={}",
                Rs2Bank.isOpen(),
                Rs2Bank.hasWithdrawAsNote(),
                this.hasSellableBankItems(),
                Rs2Inventory.isEmpty());
        if (KspBankWidgetHelper.closeBankTutorialOverlayIfOpenAndWait()) {
            return;
        }
        if (!Rs2Inventory.isEmpty()) {
            Rs2Bank.depositAll();
            SellScript.sleepUntil(Rs2Inventory::isEmpty, (int)3000);
        }
        if (!Rs2Bank.hasWithdrawAsNote()) {
            Rs2Bank.setWithdrawAsNote();
            SellScript.sleepUntil(Rs2Bank::hasWithdrawAsNote, (int)2000);
        }
        boolean withdrewAny = false;
        for (SellList sellList : SellList.values()) {
            if (!this.shouldSellEntry(sellList)) continue;
            if (Rs2Inventory.isFull()) break;
            if (this.isBlockedSellItem(sellList.getDisplayName())) continue;
            int quantityToSell = this.getSellableBankQuantity(sellList.getDisplayName());
            if (quantityToSell <= 0) continue;
            if (quantityToSell >= Rs2Bank.count(sellList.getDisplayName(), true)) {
                Rs2Bank.withdrawAll((String)sellList.getDisplayName(), (boolean)true);
            } else {
                Rs2Bank.withdrawX(sellList.getDisplayName(), quantityToSell, true);
            }
            boolean withdrew = SellScript.sleepUntil(() -> Rs2Inventory.hasItem((String)sellList.getDisplayName(), (boolean)true), (int)3000);
            this.recordWithdrawResult(sellList.getDisplayName(), withdrew);
            this.debug("Withdraw sell item | item={} qty={} reservedForQuests={} withdrew={} invFull={}",
                    sellList.getDisplayName(),
                    quantityToSell,
                    this.getReservedQuestRequirementQuantity(sellList.getDisplayName()),
                    withdrew,
                    Rs2Inventory.isFull());
            withdrewAny = withdrewAny || withdrew;
        }
        withdrewAny = this.withdrawOutdatedToolsAsNotes() || withdrewAny;
        if (!withdrewAny) {
            if (!this.hasSellableBankItems()) {
                this.complete = true;
                Microbot.status = "GE Sell Complete";
            } else {
                Microbot.status = "Waiting for sell item withdraw";
            }
            return;
        }
        Rs2Bank.closeBank();
        SellScript.sleepUntil(() -> !Rs2Bank.isOpen(), (int)2000);
        Microbot.status = "Opening GE";
    }

    private void recordWithdrawResult(String itemName, boolean withdrew) {
        if (itemName == null) {
            return;
        }
        String normalizedName = itemName.toLowerCase(Locale.ENGLISH);
        if (withdrew) {
            this.withdrawFailureCounts.remove(normalizedName);
            return;
        }
        int failureCount = this.withdrawFailureCounts.getOrDefault(normalizedName, 0) + 1;
        this.withdrawFailureCounts.put(normalizedName, failureCount);
        if (failureCount < MAX_WITHDRAW_FAILURES) {
            return;
        }
        this.blockedSellItems.add(normalizedName);
        this.debug("Blocking sell item after repeated withdrawal failures | item={} failures={}",
                itemName,
                failureCount);
    }

    private void updateState() {
        if (!this.targetArea.toWorldArea().contains(Rs2Player.getWorldLocation())) {
            this.state = SellState.GOING_TO_GE;
            return;
        }
        if (!this.hasSellableInventoryItems()) {
            this.state = this.shouldWaitAtGrandExchange()
                    ? SellState.SELLING_ITEMS
                    : SellState.RESTOCKING_FROM_BANK;
            return;
        }
        this.state = SellState.SELLING_ITEMS;
    }

    private boolean shouldWaitAtGrandExchange() {
        return Rs2GrandExchange.isOpen()
                && (Rs2GrandExchange.isOfferScreenOpen()
                || Rs2GrandExchange.hasSoldOffer()
                || Rs2GrandExchange.getAvailableSlotsCount() <= 0);
    }

    private void handleSellingItems() {
        if (Rs2Player.isMoving() || Rs2Player.isInteracting()) {
            Microbot.status = "Waiting at GE";
            return;
        }
        if (Rs2GrandExchange.hasSoldOffer() && this.ensureGrandExchangeOpen()) {
            Microbot.status = "Collecting Sold Items";
            Rs2GrandExchange.collectAllToBank();
            SellScript.sleepUntil(() -> !Rs2GrandExchange.hasSoldOffer(), (int)5000);
            return;
        }
        if (Rs2GrandExchange.isOfferScreenOpen()) {
            Microbot.status = "Opening GE Overview";
            this.returnToGrandExchangeOverview();
            return;
        }
        if (!this.ensureGrandExchangeOpen()) {
            return;
        }
        if (Rs2GrandExchange.getAvailableSlotsCount() <= 0) {
            if (Rs2GrandExchange.hasSoldOffer()) {
                Rs2GrandExchange.collectAllToBank();
                SellScript.sleepUntil(() -> !Rs2GrandExchange.hasSoldOffer(), (int)5000);
            }
            return;
        }
        Rs2ItemModel nextItem = this.getNextSellableInventoryItem();
        if (nextItem == null) {
            Microbot.status = "Waiting at GE";
            this.state = SellState.RESTOCKING_FROM_BANK;
            return;
        }
        this.placeFallbackSellOffer(nextItem);
    }

    private boolean shouldSellEntry(SellList sellList) {
        if (sellList.isTradeRestricted() && !this.hasUnlockedTradeRestrictedItems()) {
            return false;
        }
        if (PROTECTED_SKILL_RESOURCES.contains(sellList) && !this.canAffordGeBuyRequirements()) {
            return false;
        }
        int firemakingLevel = Microbot.getClient().getRealSkillLevel(Skill.FIREMAKING);
        if (sellList == SellList.LOGS) {
            return firemakingLevel >= 15;
        }
        if (sellList == SellList.OAK_LOGS) {
            return firemakingLevel >= 30;
        }
        if (sellList == SellList.SILVER_ORE) {
            return Microbot.getClient().getRealSkillLevel(Skill.SMITHING) < 20;
        }
        if (sellList == SellList.SMALL_FISHING_NET
                || sellList == SellList.FISHING_ROD
                || sellList == SellList.FISHING_BAIT) {
            return Microbot.getClient().getRealSkillLevel(Skill.FISHING) >= 20;
        }
        return true;
    }

    private int getSellableBankQuantity(String itemName) {
        int bankQuantity = Math.max(0, Rs2Bank.count(itemName, true));
        return Math.max(0, bankQuantity - this.getReservedQuestRequirementQuantity(itemName));
    }

    private int getSellableInventoryQuantity(String itemName, int inventoryQuantity) {
        return Math.max(0, inventoryQuantity - this.getReservedQuestRequirementQuantity(itemName));
    }

    private int getReservedQuestRequirementQuantity(String itemName) {
        if (itemName == null) {
            return 0;
        }

        int reservedQuantity = 0;
        if (this.isQuestIncomplete(Quest.COOKS_ASSISTANT)) {
            for (Items item : Items.values()) {
                if (item.getDisplayName().equalsIgnoreCase(itemName)) {
                    reservedQuantity += 1;
                }
            }
        }
        if (this.isQuestIncomplete(Quest.GOBLIN_DIPLOMACY)) {
            for (GobReqs item : GobReqs.values()) {
                if (item.getDisplayName().equalsIgnoreCase(itemName)) {
                    reservedQuantity += item.getQuantity();
                }
            }
        }
        return reservedQuantity;
    }

    private boolean isQuestIncomplete(Quest quest) {
        return Rs2Player.getQuestState(quest) != QuestState.FINISHED;
    }

    private boolean hasSellableInventoryItems() {
        return this.getNextSellableInventoryItem() != null;
    }

    private boolean hasSellableBankItems() {
        for (SellList sellList : SellList.values()) {
            if (!this.shouldSellEntry(sellList)
                    || this.isBlockedSellItem(sellList.getDisplayName())
                    || this.getSellableBankQuantity(sellList.getDisplayName()) <= 0) continue;
            return true;
        }
        return this.hasOutdatedToolInBank();
    }

    public boolean hasSellListItemsAvailable() {
        return this.hasSellableInventoryItems() || this.hasSellableBankItems();
    }

    private Rs2ItemModel getNextSellableInventoryItem() {
        List<String> sellNames = this.getAllowedSellNames();
        for (Rs2ItemModel item : Rs2Inventory.all()) {
            if (item == null
                    || item.getName() == null
                    || this.isBlockedSellItem(item.getName())
                    || this.getSellableInventoryQuantity(item.getName(), item.getQuantity()) <= 0) {
                continue;
            }

            if (sellNames.stream().anyMatch(name -> name.equalsIgnoreCase(item.getName()))) {
                return item;
            }
        }

        if (!this.canAffordGeBuyRequirements()) {
            return null;
        }

        String desiredPickaxe = this.resolveDesiredPickaxeName();
        String desiredAxe = this.resolveDesiredAxeName();
        for (Rs2ItemModel item : Rs2Inventory.all()) {
            if (item == null
                    || item.getName() == null
                    || this.isBlockedSellItem(item.getName())
                    || !this.isOutdatedToolName(item.getName(), desiredPickaxe, desiredAxe)) {
                continue;
            }

            return item;
        }
        return null;
    }

    private List<String> getAllowedSellNames() {
        ArrayList<String> names = new ArrayList<String>();
        for (SellList sellList : SellList.values()) {
            if (!this.shouldSellEntry(sellList) || this.isBlockedSellItem(sellList.getDisplayName())) continue;
            names.add(sellList.getDisplayName());
        }
        return names;
    }

    private boolean withdrawOutdatedToolsAsNotes() {
        if (!this.canAffordGeBuyRequirements()) {
            return false;
        }

        boolean withdrew;
        boolean withdrewAny = false;
        String desiredPickaxe = this.resolveDesiredPickaxeName();
        String desiredAxe = this.resolveDesiredAxeName();
        for (String pickaxeName : PICKAXE_NAMES) {
            if (pickaxeName.equalsIgnoreCase(desiredPickaxe) || Rs2Inventory.isFull() || Rs2Bank.count((String)pickaxeName) <= 0) continue;
            Rs2Bank.withdrawAll((String)pickaxeName, (boolean)true);
            withdrew = SellScript.sleepUntil(() -> Rs2Inventory.hasItem((String)pickaxeName, (boolean)true), (int)3000);
            withdrewAny = withdrewAny || withdrew;
        }
        for (String axeName : AXE_NAMES) {
            if (axeName.equalsIgnoreCase(desiredAxe) || Rs2Inventory.isFull() || Rs2Bank.count((String)axeName) <= 0) continue;
            Rs2Bank.withdrawAll((String)axeName, (boolean)true);
            withdrew = SellScript.sleepUntil(() -> Rs2Inventory.hasItem((String)axeName, (boolean)true), (int)3000);
            withdrewAny = withdrewAny || withdrew;
        }
        return withdrewAny;
    }

    private boolean hasOutdatedToolInBank() {
        if (!this.canAffordGeBuyRequirements()) {
            return false;
        }

        String desiredPickaxe = this.resolveDesiredPickaxeName();
        String desiredAxe = this.resolveDesiredAxeName();
        for (String pickaxeName : PICKAXE_NAMES) {
            if (pickaxeName.equalsIgnoreCase(desiredPickaxe) || Rs2Bank.count((String)pickaxeName) <= 0) continue;
            return true;
        }
        for (String axeName : AXE_NAMES) {
            if (axeName.equalsIgnoreCase(desiredAxe) || Rs2Bank.count((String)axeName) <= 0) continue;
            return true;
        }
        return false;
    }

    private boolean canAffordGeBuyRequirements() {
        long now = System.currentTimeMillis();
        if (now - this.lastBuyAffordabilityCheckAtMs < BUY_AFFORDABILITY_CACHE_MS) {
            return this.cachedBuyAffordability;
        }

        this.cachedBuyAffordability = this.buyScript.canAffordMissingBuys();
        this.lastBuyAffordabilityCheckAtMs = now;
        if (!this.cachedBuyAffordability) {
            this.debug("Protecting required skill resources and pickaxes/axes; ordinary SellList products remain eligible");
        }
        return this.cachedBuyAffordability;
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

    private boolean isBlockedSellItem(String itemName) {
        return itemName != null && this.blockedSellItems.contains(itemName.toLowerCase(Locale.ENGLISH));
    }

    private boolean hasUnlockedTradeRestrictedItems() {
        long now = System.currentTimeMillis();
        if (this.tradeRestrictionUnlockedCache != null
                && now - this.lastTradeRestrictionCheckAtMs < TRADE_RESTRICTION_CACHE_MS) {
            return this.tradeRestrictionUnlockedCache;
        }
        long accountHash = this.getCurrentAccountHash();
        long playTimeMillis = accountHash != 0L
                && this.accountPlayTimeCache != null
                && this.accountPlayTimeCache.hasCachedPlayTime(accountHash)
                ? this.accountPlayTimeCache.getPlayTimeMillis(accountHash)
                : -1L;
        boolean unlocked = false;
        if (this.getTotalLevel() >= TRADE_RESTRICTION_MIN_TOTAL_LEVEL
                && this.getQuestPoints() >= TRADE_RESTRICTION_MIN_QUEST_POINTS) {
            unlocked = playTimeMillis >= TimeUnit.HOURS.toMillis(TRADE_RESTRICTION_MIN_HOURS_PLAYED);
        }
        this.tradeRestrictionUnlockedCache = unlocked;
        this.lastTradeRestrictionCheckAtMs = now;
        this.debug("Trade restriction check | accountHash={} playTimeMinutes={} totalLevel={} questPoints={} unlocked={}",
                accountHash == 0L ? "unavailable" : Long.toUnsignedString(accountHash),
                playTimeMillis < 0L ? -1L : TimeUnit.MILLISECONDS.toMinutes(playTimeMillis),
                this.getTotalLevel(),
                this.getQuestPoints(),
                unlocked);
        return unlocked;
    }

    private long getCurrentAccountHash() {
        if (!Microbot.isLoggedIn() || Microbot.getClient() == null) {
            return 0L;
        }
        return Microbot.getClientThread()
                .runOnClientThreadOptional(() -> Microbot.getClient().getAccountHash())
                .orElse(0L);
    }

    private int getTotalLevel() {
        int total = 0;
        for (Skill skill : Skill.values()) {
            if (skill == Skill.OVERALL) continue;
            total += Microbot.getClient().getRealSkillLevel(skill);
        }
        return total;
    }

    private int getQuestPoints() {
        return Microbot.getVarbitPlayerValue((int)101);
    }

    private int getHoursPlayed() {
        String[] parts;
        if (this.cachedHoursPlayed != null) {
            return this.cachedHoursPlayed;
        }
        String timePlayedText = this.getTimePlayedText();
        if (timePlayedText == null || timePlayedText.isEmpty()) {
            return 0;
        }
        int totalHours = 0;
        for (String rawPart : parts = timePlayedText.toLowerCase(Locale.ENGLISH).split(",")) {
            String part = rawPart.trim();
            if (part.endsWith("days") || part.endsWith("day")) {
                totalHours += this.extractLeadingNumber(part) * 24;
                continue;
            }
            if (part.endsWith("hours") || part.endsWith("hour")) {
                totalHours += this.extractLeadingNumber(part);
                continue;
            }
            if (!part.endsWith("minutes") && !part.endsWith("minute")) continue;
            totalHours += this.extractLeadingNumber(part) / 60;
        }
        this.cachedHoursPlayed = totalHours;
        return totalHours;
    }

    private int extractLeadingNumber(String text) {
        String digits = text.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(digits);
        }
        catch (NumberFormatException ex) {
            return 0;
        }
    }

    private String getTimePlayedText() {
        Widget timePlayedWidget = Rs2Widget.getWidget((int)46661634);
        if (timePlayedWidget == null || !Rs2Widget.isWidgetVisible((int)timePlayedWidget.getId()) || timePlayedWidget.getText() == null || timePlayedWidget.getText().isEmpty()) {
            if (this.attemptedHoursPlayedLookup) {
                return null;
            }
            if (!this.ensureTimePlayedPanelVisible()) {
                return null;
            }
            timePlayedWidget = Rs2Widget.getWidget((int)46661634);
            if (timePlayedWidget == null || timePlayedWidget.getText() == null || timePlayedWidget.getText().isEmpty()) {
                return null;
            }
        }
        return this.stripWidgetTags(timePlayedWidget.getText());
    }

    private boolean ensureTimePlayedPanelVisible() {
        Widget accountSummaryWidget;
        this.attemptedHoursPlayedLookup = true;
        Widget timePlayedWidget = Rs2Widget.getWidget((int)46661634);
        if (timePlayedWidget != null && Rs2Widget.isWidgetVisible((int)timePlayedWidget.getId())) {
            return true;
        }
        if (Rs2GrandExchange.isOpen() || Rs2Bank.isOpen() || Rs2Player.isMoving() || Rs2Player.isInteracting()) {
            return false;
        }
        Widget sideJournalTabContainer = Rs2Widget.getWidget((int)41222157);
        if (sideJournalTabContainer != null) {
            Rs2Widget.clickWidget((Widget)sideJournalTabContainer);
            SellScript.sleep((int)200, (int)350);
        }
        if ((accountSummaryWidget = Rs2Widget.getWidget((int)46661633)) != null) {
            Rs2Widget.clickWidget((Widget)accountSummaryWidget);
            SellScript.sleepUntil(() -> {
                Widget widget = Rs2Widget.getWidget((int)46661634);
                return widget != null && widget.getText() != null && !widget.getText().isEmpty();
            }, (int)2000);
        }
        return (timePlayedWidget = Rs2Widget.getWidget((int)46661634)) != null && timePlayedWidget.getText() != null && !timePlayedWidget.getText().isEmpty();
    }

    private String stripWidgetTags(String text) {
        return text == null ? null : text.replaceAll("<[^>]+>", "").trim();
    }

    private boolean ensureGrandExchangeOpen() {
        if (Rs2GrandExchange.isOpen()) {
            return true;
        }
        if (Rs2Bank.isOpen()) {
            Microbot.status = "Closing Bank";
            KspGrandExchangeHelper.closeBankBeforeExchange();
            SellScript.sleepUntil(() -> !Rs2Bank.isOpen(), (int)2000);
            return false;
        }
        Microbot.status = "Opening GE";
        this.debug("Opening GE for sell task | player={} bankOpen={} geOpen={}", Rs2Player.getWorldLocation(), Rs2Bank.isOpen(), Rs2GrandExchange.isOpen());
        if (KspGrandExchangeHelper.openExchangeDirectly()) {
            SellScript.sleepUntil(Rs2GrandExchange::isOpen, (int)3000);
            return Rs2GrandExchange.isOpen();
        }
        if (KspGrandExchangeHelper.interactClerk()) {
            SellScript.sleepUntil(Rs2GrandExchange::isOpen, (int)3000);
            return Rs2GrandExchange.isOpen();
        }
        return false;
    }

    private boolean placeFallbackSellOffer(Rs2ItemModel item) {
        GrandExchangeRequest request;
        boolean offered;
        if (item == null || System.currentTimeMillis() - this.lastActionAtMs < 1200L) {
            return false;
        }
        int quantityToSell = this.getSellableInventoryQuantity(item.getName(), item.getQuantity());
        if (quantityToSell <= 0) {
            return false;
        }
        Microbot.status = "Selling " + item.getName();
        this.waitForGrandExchangeOfferInput();
        if (offered = Rs2GrandExchange.processOffer((GrandExchangeRequest)(request = GrandExchangeRequest.builder().action(GrandExchangeAction.SELL).itemName(item.getName()).quantity(quantityToSell).percent(-10).closeAfterCompletion(false).build()))) {
            this.lastActionAtMs = System.currentTimeMillis();
            SellScript.sleepUntil(() -> this.getSellableInventoryQuantity(item.getName(), Rs2Inventory.itemQuantity(item.getName())) <= 0, (int)5000);
        }
        this.debug("GE sell offer | item={} qty={} reservedForQuests={} percent=-10 offered={} slots={} offerScreen={}",
                item.getName(),
                quantityToSell,
                this.getReservedQuestRequirementQuantity(item.getName()),
                offered,
                Rs2GrandExchange.isOpen() ? Rs2GrandExchange.getAvailableSlotsCount() : -1,
                Rs2GrandExchange.isOfferScreenOpen());
        return offered;
    }

    private void waitForGrandExchangeOfferInput() {
        SellScript.sleep(GE_OFFER_INPUT_DELAY_MS);
    }

    private void returnToGrandExchangeOverview() {
        if (System.currentTimeMillis() - this.lastActionAtMs < 1200L) {
            return;
        }
        Rs2GrandExchange.backToOverview();
        this.lastActionAtMs = System.currentTimeMillis();
        SellScript.sleepUntil(() -> !Rs2GrandExchange.isOfferScreenOpen(), (int)2000);
    }

    private WorldPoint getAreaCenter() {
        int centerX = (this.targetArea.getSouthWest().getX() + this.targetArea.getNorthEast().getX()) / 2;
        int centerY = (this.targetArea.getSouthWest().getY() + this.targetArea.getNorthEast().getY()) / 2;
        int plane = this.targetArea.getSouthWest().getPlane();
        return new WorldPoint(centerX, centerY, plane);
    }

    private void debug(String message, Object ... args) {
        if (this.debugLogging) {
            KspTaskDebug.info(log, true, "GE Sell", message, args);
        }
    }

    public void shutdown() {
        this.state = SellState.GOING_TO_GE;
        this.lastWebWalkAtMs = 0L;
        this.lastActionAtMs = 0L;
        this.blockedSellItems.clear();
        this.withdrawFailureCounts.clear();
        this.tradeRestrictionUnlockedCache = null;
        this.cachedHoursPlayed = null;
        this.lastTradeRestrictionCheckAtMs = 0L;
        this.lastBuyAffordabilityCheckAtMs = 0L;
        this.cachedBuyAffordability = false;
        this.attemptedHoursPlayedLookup = false;
        this.complete = false;
        KspWalkerGuard.clear("GE Sell:target-area");
        super.shutdown();
    }

    public boolean isComplete() {
        return this.complete;
    }

    public GEArea getTargetArea() {
        return this.targetArea;
    }
}
