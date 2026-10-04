package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.sellscript;

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
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspAccountPlayTimeCache;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspTaskDebug;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspWalkerGuard;
import net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.KspBankWidgetHelper;
import net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.KspGrandExchangeHelper;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.cooksassistant.reqs.Items;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.goblindip.reqs.GobReqs;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.buyscript.Buy;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.buyscript.BuyScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.gearea.GEArea;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.sell.SellList;
import net.runelite.client.plugins.microbot.ksputil.KspGrandExchangeSafe;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Singleton
public class SellScript extends Script
{
    private static final Logger log = LoggerFactory.getLogger(SellScript.class);

    private static final int LOOP_DELAY_MS = 40;
    private static final int WEB_WALK_COOLDOWN_MS = 1_000;
    private static final int MAX_WITHDRAW_FAILURES = 3;
    private static final int BANK_STATE_TIMEOUT_MS = 900;
    private static final int GE_STATE_TIMEOUT_MS = 1_200;
    private static final int TRADE_RESTRICTION_CACHE_MS = 10_000;
    private static final int TRADE_RESTRICTION_MIN_TOTAL_LEVEL = 100;
    private static final int TRADE_RESTRICTION_MIN_QUEST_POINTS = 10;
    private static final int TRADE_RESTRICTION_MIN_HOURS_PLAYED = 20;
    private static final long BUY_AFFORDABILITY_CACHE_MS = 3_000L;

    private static final String[] PICKAXE_NAMES = Buy.PICKAXE_NAMES;
    private static final String[] AXE_NAMES = Buy.AXE_NAMES;
    private static final SellList[] SELL_ENTRIES = SellList.values();
    private static final Skill[] SKILLS = Skill.values();

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

    private final Set<String> blockedSellItems = new HashSet<>();
    private final Map<String, Integer> withdrawFailureCounts = new HashMap<>();

    private GEArea targetArea = GEArea.GRAND_EXCHANGE;
    private SellState state = SellState.GOING_TO_GE;
    private boolean debugLogging;
    private boolean complete;
    private boolean sellInventoryReset;
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
        targetArea = area;
        complete = false;
        sellInventoryReset = false;
        state = SellState.GOING_TO_GE;
        Microbot.status = "Walking to GE";

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            try
            {
                if (!super.run() || !Microbot.isLoggedIn()) return;
                if (complete)
                {
                    Microbot.status = "GE Sell Complete";
                    return;
                }

                updateState();
                switch (state)
                {
                    case GOING_TO_GE:
                        if (ensureInTargetArea()) state = SellState.RESTOCKING_FROM_BANK;
                        break;
                    case RESTOCKING_FROM_BANK:
                        prepareSellInventoryFromBank();
                        break;
                    case SELLING_ITEMS:
                        handleSellingItems();
                        break;
                }
            }
            catch (Exception ex)
            {
                log.warn("KSP Account Builder SellScript tick failed", ex);
            }
        }, 0L, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);
        return true;
    }

    private void updateState()
    {
        if (!targetArea.toWorldArea().contains(Rs2Player.getWorldLocation()))
        {
            state = SellState.GOING_TO_GE;
            return;
        }

        if (hasSellableInventoryItems() || shouldWaitAtGrandExchange())
        {
            state = SellState.SELLING_ITEMS;
            return;
        }

        state = SellState.RESTOCKING_FROM_BANK;
    }

    private boolean ensureInTargetArea()
    {
        if (targetArea.toWorldArea().contains(Rs2Player.getWorldLocation()))
        {
            KspWalkerGuard.clear("GE Sell:target-area");
            return true;
        }
        if (Rs2Player.isMoving()) return false;

        Microbot.status = "Walking to GE";
        KspWalkerGuard.walkToDestination(
                "GE Sell:target-area",
                targetArea::getRandomPoint,
                targetArea.toWorldArea()::contains,
                2,
                WEB_WALK_COOLDOWN_MS);
        return false;
    }

    /**
     * Fast state-driven banking:
     * bank widget -> note mode -> clean inventory once -> withdraw every sellable
     * stack, confirming each inventory/bank change -> close bank -> GE.
     */
    private void prepareSellInventoryFromBank()
    {
        if (Rs2GrandExchange.isOpen())
        {
            if (shouldWaitAtGrandExchange())
            {
                state = SellState.SELLING_ITEMS;
                Microbot.status = "Waiting for GE Slot";
                return;
            }
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

        // As soon as the bank widget exists, select notes. Do not wait for a later tick.
        if (!Rs2Bank.hasWithdrawAsNote())
        {
            Microbot.status = "Setting Withdraw-as-Note";
            if (!Rs2Bank.setWithdrawAsNote()) return;
            if (!sleepUntil(Rs2Bank::hasWithdrawAsNote, BANK_STATE_TIMEOUT_MS)) return;
        }

        // This state is entered with no sellable inventory. Clear protected/junk
        // inventory once so the sell batch can use every available slot.
        if (!sellInventoryReset)
        {
            if (!Rs2Inventory.isEmpty())
            {
                Microbot.status = "Clearing Inventory";
                int before = Rs2Inventory.emptySlotCount();
                if (!Rs2Bank.depositAll()) return;
                if (!sleepUntil(() -> Rs2Inventory.isEmpty()
                        || Rs2Inventory.emptySlotCount() > before, BANK_STATE_TIMEOUT_MS)) return;
                if (!Rs2Inventory.isEmpty()) return;
            }
            sellInventoryReset = true;
        }

        Microbot.status = "Withdrawing Sell Items";
        boolean withdrewAny = withdrawAllSellableStacks();

        if (!withdrewAny && !hasSellableBankItems())
        {
            complete = true;
            Microbot.status = "GE Sell Complete";
            Rs2Bank.closeBank();
            return;
        }

        if (!hasSellableInventoryItems()) return;

        Microbot.status = "Closing Bank";
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), BANK_STATE_TIMEOUT_MS);
        if (Rs2Bank.isOpen()) return;

        sellInventoryReset = false;
        state = SellState.SELLING_ITEMS;
        Microbot.status = "Opening GE";
    }

    private boolean withdrawAllSellableStacks()
    {
        boolean withdrewAny = false;

        for (SellList entry : SELL_ENTRIES)
        {
            if (Rs2Inventory.emptySlotCount() <= 0) break;
            if (!shouldSellEntry(entry) || isBlockedSellItem(entry.getDisplayName())) continue;

            int qty = getSellableBankQuantity(entry.getDisplayName());
            if (qty <= 0) continue;

            if (withdrawSellStack(entry.getDisplayName(), qty)) withdrewAny = true;
        }

        if (Rs2Inventory.emptySlotCount() > 0 && canAffordGeBuyRequirements())
        {
            withdrewAny |= withdrawOutdatedToolsAsNotes();
        }

        return withdrewAny;
    }

    private boolean withdrawSellStack(String itemName, int quantity)
    {
        if (itemName == null || quantity <= 0 || Rs2Inventory.emptySlotCount() <= 0) return false;

        int beforeInventory = Rs2Inventory.itemQuantity(itemName, true);
        int beforeBank = Rs2Bank.count(itemName, true);
        int withdraw = Math.min(quantity, beforeBank);
        if (withdraw <= 0) return false;

        boolean dispatched = withdraw >= beforeBank
                ? Rs2Bank.withdrawAll(itemName, true)
                : Rs2Bank.withdrawX(itemName, withdraw, true);
        if (!dispatched)
        {
            recordWithdrawResult(itemName, false);
            return false;
        }

        // Noted-aware confirmation. The old Rs2Inventory.count(name) check could
        // miss the noted variant and burn the full timeout for every item.
        boolean changed = sleepUntil(() ->
                Rs2Inventory.itemQuantity(itemName, true) > beforeInventory
                        || Rs2Bank.count(itemName, true) < beforeBank,
                BANK_STATE_TIMEOUT_MS);

        recordWithdrawResult(itemName, changed);
        return changed;
    }

    private boolean withdrawOutdatedToolsAsNotes()
    {
        boolean withdrew = false;
        String desiredPickaxe = resolveDesiredPickaxeName();
        String desiredAxe = resolveDesiredAxeName();

        for (String name : PICKAXE_NAMES)
        {
            if (Rs2Inventory.emptySlotCount() <= 0) return withdrew;
            if (name.equalsIgnoreCase(desiredPickaxe)) continue;
            int qty = Rs2Bank.count(name, true);
            if (qty > 0 && withdrawSellStack(name, qty)) withdrew = true;
        }

        for (String name : AXE_NAMES)
        {
            if (Rs2Inventory.emptySlotCount() <= 0) return withdrew;
            if (name.equalsIgnoreCase(desiredAxe)) continue;
            int qty = Rs2Bank.count(name, true);
            if (qty > 0 && withdrawSellStack(name, qty)) withdrew = true;
        }
        return withdrew;
    }

    private void handleSellingItems()
    {
        if (Rs2Player.isMoving()) return;

        if (Rs2GrandExchange.hasSoldOffer())
        {
            if (!ensureGrandExchangeOpen()) return;
            Microbot.status = "Collecting Sold Items";
            Rs2GrandExchange.collectAllToBank();
            return;
        }

        if (Rs2GrandExchange.isOfferScreenOpen())
        {
            Rs2GrandExchange.backToOverview();
            sleepUntil(() -> Rs2GrandExchange.isOpen()
                    && !Rs2GrandExchange.isOfferScreenOpen(), GE_STATE_TIMEOUT_MS);
            return;
        }

        if (!ensureGrandExchangeOpen()) return;
        processAvailableSellSlots();
    }

    private boolean ensureGrandExchangeOpen()
    {
        if (Rs2GrandExchange.isOpen()) return true;
        if (Rs2Bank.isOpen())
        {
            KspGrandExchangeHelper.closeBankBeforeExchange();
            return false;
        }

        Microbot.status = "Opening GE";
        KspGrandExchangeHelper.openExchangeDirectly();
        if (!Rs2GrandExchange.isOpen()) KspGrandExchangeHelper.interactClerk();
        return false;
    }

    private void processAvailableSellSlots()
    {
        int availableSlots = Rs2GrandExchange.getAvailableSlotsCount();
        if (availableSlots <= 0)
        {
            Microbot.status = "Waiting for GE Slot";
            return;
        }

        while (availableSlots > 0)
        {
            Rs2ItemModel item = getNextSellableInventoryItem();
            if (item == null)
            {
                state = SellState.RESTOCKING_FROM_BANK;
                return;
            }

            // KspGrandExchangeSafe already waits until the slot changes. Do not add
            // another two-second commit wait here.
            if (!placeFallbackSellOffer(item)) return;

            if (Rs2GrandExchange.isOfferScreenOpen())
            {
                Rs2GrandExchange.backToOverview();
                if (!sleepUntil(() -> Rs2GrandExchange.isOpen()
                        && !Rs2GrandExchange.isOfferScreenOpen(), GE_STATE_TIMEOUT_MS)) return;
            }

            availableSlots = Rs2GrandExchange.getAvailableSlotsCount();
        }

        Microbot.status = "Waiting for GE Slot";
    }

    private boolean placeFallbackSellOffer(Rs2ItemModel item)
    {
        if (item == null || item.getName() == null) return false;
        int qty = getSellableInventoryQuantity(item.getName(), item.getQuantity());
        if (qty <= 0) return false;

        Microbot.status = "Selling " + item.getName();
        boolean offered = KspGrandExchangeSafe.sell(
                item.getName(), qty, (long) getAdjustedSellPrice(item), true, false);

        debug("GE sell offer | item={} qty={} offered={} slots={}",
                item.getName(), qty, offered,
                Rs2GrandExchange.isOpen() ? Rs2GrandExchange.getAvailableSlotsCount() : -1);
        return offered;
    }

    private int getAdjustedSellPrice(Rs2ItemModel item)
    {
        if (item == null) return 1;
        int itemId = item.getUnNotedId() > 0 ? item.getUnNotedId() : item.getId();
        int guide = Rs2GrandExchange.getPrice(itemId);
        if (guide <= 0 && item.getPrice() > 0L)
        {
            guide = (int) Math.min(Integer.MAX_VALUE, item.getPrice());
        }
        return Math.max(1, (int) ((long) Math.max(1, guide) * 90L / 100L));
    }

    private boolean shouldWaitAtGrandExchange()
    {
        return Rs2GrandExchange.isOpen()
                && (Rs2GrandExchange.isOfferScreenOpen()
                || Rs2GrandExchange.hasSoldOffer()
                || Rs2GrandExchange.getAvailableSlotsCount() <= 0);
    }

    private boolean shouldSellEntry(SellList sellList)
    {
        if (sellList.isTradeRestricted() && !hasUnlockedTradeRestrictedItems()) return false;
        if (PROTECTED_SKILL_RESOURCES.contains(sellList) && !canAffordGeBuyRequirements()) return false;

        int firemakingLevel = Microbot.getClient().getRealSkillLevel(Skill.FIREMAKING);
        if (sellList == SellList.LOGS) return firemakingLevel >= 15;
        if (sellList == SellList.OAK_LOGS) return firemakingLevel >= 30;
        if (sellList == SellList.SILVER_ORE)
            return Microbot.getClient().getRealSkillLevel(Skill.SMITHING) < 20;
        if (sellList == SellList.SMALL_FISHING_NET
                || sellList == SellList.FISHING_ROD
                || sellList == SellList.FISHING_BAIT)
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
        {
            if (shouldSellEntry(entry)
                    && !isBlockedSellItem(entry.getDisplayName())
                    && getSellableBankQuantity(entry.getDisplayName()) > 0) return true;
        }
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
            if (item == null || item.getName() == null || isBlockedSellItem(item.getName())) continue;
            if (getSellableInventoryQuantity(item.getName(), item.getQuantity()) <= 0) continue;
            if (isAllowedSellItemName(item.getName())) return item;
        }

        if (!canAffordGeBuyRequirements()) return null;
        String desiredPickaxe = resolveDesiredPickaxeName();
        String desiredAxe = resolveDesiredAxeName();
        for (Rs2ItemModel item : items)
        {
            if (item == null || item.getName() == null || isBlockedSellItem(item.getName())) continue;
            if (Buy.isOutdatedToolName(item.getName(), desiredPickaxe, desiredAxe)) return item;
        }
        return null;
    }

    private boolean isAllowedSellItemName(String itemName)
    {
        for (SellList entry : SELL_ENTRIES)
        {
            if (entry.getDisplayName().equalsIgnoreCase(itemName)
                    && shouldSellEntry(entry)
                    && !isBlockedSellItem(entry.getDisplayName())) return true;
        }
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
        {
            for (Items item : Items.values())
                if (item.getDisplayName().equalsIgnoreCase(itemName)) reserved++;
        }

        if (isQuestIncomplete(Quest.GOBLIN_DIPLOMACY))
        {
            for (GobReqs item : GobReqs.values())
                if (item.getDisplayName().equalsIgnoreCase(itemName)) reserved += item.getQuantity();
        }
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

    private String resolveDesiredPickaxeName()
    {
        return Buy.resolveDesiredPickaxeNameForBuy();
    }

    private String resolveDesiredAxeName()
    {
        return Buy.resolveDesiredAxeNameForBuy();
    }

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
        long playTimeMillis = accountHash != 0L
                && accountPlayTimeCache != null
                && accountPlayTimeCache.hasCachedPlayTime(accountHash)
                ? accountPlayTimeCache.getPlayTimeMillis(accountHash)
                : -1L;

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

    private int getQuestPoints()
    {
        return Microbot.getVarbitPlayerValue(101);
    }

    private boolean isBlockedSellItem(String itemName)
    {
        return itemName != null && blockedSellItems.contains(itemName.toLowerCase(Locale.ENGLISH));
    }

    private void recordWithdrawResult(String itemName, boolean success)
    {
        if (itemName == null) return;
        String key = itemName.toLowerCase(Locale.ENGLISH);
        if (success)
        {
            withdrawFailureCounts.remove(key);
            return;
        }

        int failures = withdrawFailureCounts.getOrDefault(key, 0) + 1;
        withdrawFailureCounts.put(key, failures);
        if (failures >= MAX_WITHDRAW_FAILURES)
        {
            blockedSellItems.add(key);
            debug("Blocking sell item after repeated withdrawal failures | item={} failures={}",
                    itemName, failures);
        }
    }

    private void debug(String message, Object... args)
    {
        if (debugLogging) KspTaskDebug.info(log, true, "GE Sell", message, args);
    }

    @Override
    public void shutdown()
    {
        state = SellState.GOING_TO_GE;
        complete = false;
        sellInventoryReset = false;
        blockedSellItems.clear();
        withdrawFailureCounts.clear();
        tradeRestrictionUnlockedCache = null;
        lastTradeRestrictionCheckAtMs = 0L;
        lastBuyAffordabilityCheckAtMs = 0L;
        cachedBuyAffordability = false;
        KspWalkerGuard.clear("GE Sell:target-area");
        super.shutdown();
    }

    public boolean isComplete()
    {
        return complete;
    }

    public GEArea getTargetArea()
    {
        return targetArea;
    }
}
