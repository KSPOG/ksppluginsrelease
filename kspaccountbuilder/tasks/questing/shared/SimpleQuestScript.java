package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.shared;

import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspWalkerGuard;
import net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.KspBankWidgetHelper;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeSlots;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Shared bank-first GE restocking and guarded interactions for small quest tasks. */
public abstract class SimpleQuestScript extends Script
{
    protected static final QuestRequirement[] NO_REQUIREMENTS = new QuestRequirement[0];
    // Same minimum offer budget as Cook's Assistant/Goblin Diplomacy.
    public static final int BUY_PRICE = 1_000;
    private static final WorldPoint GE = new WorldPoint(3164, 3487, 0);
    private final Quest quest;
    private final String questName;
    private final Map<Integer, Integer> buyPrices = new HashMap<>();
    private boolean debugLogging;
    private boolean bankAudited;
    private boolean bankDeposited;
    private boolean buying;
    private long nextActionAt;
    private volatile QuestTaskState state = QuestTaskState.PREPARING;
    private volatile String status = "Idle";

    protected SimpleQuestScript(Quest quest, String questName)
    {
        this.quest = quest;
        this.questName = questName;
    }

    public boolean run()
    {
        shutdown();
        bankAudited = false;
        bankDeposited = false;
        buying = false;
        nextActionAt = 0;
        state = QuestTaskState.PREPARING;
        status = "Starting " + questName;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            try { tick(); }
            catch (Exception ex) { Microbot.logStackTrace(getClass().getSimpleName(), ex); }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    private void tick()
    {
        if (!super.run() || !Microbot.isLoggedIn()) return;
        if (isComplete()) { state = QuestTaskState.COMPLETE; setStatus(questName + " complete"); shutdown(); return; }
        if (handleDialogue()) return;
        if (System.currentTimeMillis() < nextActionAt) return;
        if (Rs2Player.isAnimating()) return;
        if (!prepareRequirements() || !prepareQuestItems() || !ensureInventorySpace()) return;
        if (Rs2GrandExchange.isOpen()) { Rs2GrandExchange.closeExchange(); return; }
        if (Rs2Bank.isOpen()) { Rs2Bank.closeBank(); return; }
        progressQuest();
    }

    protected abstract QuestRequirement[] requirements();
    protected abstract String[] dialogueOptions();
    protected abstract void progressQuest();
    protected boolean prepareQuestItems() { return true; }
    protected String[] preservedItems() { return new String[0]; }

    public boolean isComplete() { return Rs2Player.getQuestState(quest) == QuestState.FINISHED; }
    public QuestTaskState getState() { return state; }
    public String getStatus() { return status; }
    public void setDebugLogging(boolean enabled) { debugLogging = enabled; }

    /** Called after the builder's bank audit; counts quantities, not item types. */
    public long getMissingRequirementCost()
    {
        long cost = 0;
        for (QuestRequirement item : requirements()) cost += (long) Math.max(0, missing(item) - outstanding(item.itemId)) * buyPrice(item.itemId);
        return cost;
    }

    private int buyPrice(int itemId)
    {
        return buyPrices.computeIfAbsent(itemId, id -> Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            long guide = Microbot.getItemManager() == null ? 0 : Microbot.getItemManager().getItemPrice(id);
            return (int) Math.min(Integer.MAX_VALUE, Math.max(BUY_PRICE, (guide * 120L + 99L) / 100L));
        }).orElse(BUY_PRICE));
    }

    private int missing(QuestRequirement item)
    {
        return Math.max(0, item.quantity - Rs2Inventory.itemQuantity(item.itemId) - Math.max(0, Rs2Bank.count(item.name)));
    }

    private int outstanding(int itemId)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            int quantity = 0;
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            if (offers != null) for (GrandExchangeOffer offer : offers)
                if (offer != null && offer.getItemId() == itemId
                        && (offer.getState() == GrandExchangeOfferState.BUYING || offer.getState() == GrandExchangeOfferState.BOUGHT))
                    quantity += offer.getTotalQuantity();
            return quantity;
        }).orElse(0);
    }

    protected boolean retrieveBanked(String name)
    {
        if (Rs2Bank.count(name) <= 0) return false;
        if (!Rs2Bank.isOpen())
        {
            if (!Rs2Bank.openBank()) Rs2Bank.walkToBankAndUseBank();
            return true;
        }
        if (Rs2Bank.setWithdrawAsItem()) { Rs2Bank.withdrawX(name, 1); actionSent(); }
        return true;
    }

    private boolean inventoryReady()
    {
        for (QuestRequirement item : requirements())
            if (Rs2Inventory.itemQuantity(item.itemId) < item.quantity) return false;
        return true;
    }

    private boolean prepareRequirements()
    {
        if (inventoryReady()) { buying = false; return true; }
        if (!bankAudited || !buying) return prepareAtBank();
        return buyMissingRequirements();
    }

    private boolean prepareAtBank()
    {
        state = QuestTaskState.BANKING;
        setStatus("Preparing " + questName + " items");
        if (Rs2GrandExchange.isOpen()) { Rs2GrandExchange.closeExchange(); return false; }
        if (!Rs2Bank.isOpen())
        {
            bankDeposited = false;
            if (!Rs2Bank.openBank()) Rs2Bank.walkToBankAndUseBank();
            return false;
        }
        if (KspBankWidgetHelper.closeBankTutorialOverlayIfOpen()) return false;
        if (!bankDeposited)
        {
            // Deposit tradeable requirements too: noted wool/GE collections must be unnoted.
            Rs2Bank.depositAllExcept(preservedItems());
            bankDeposited = true;
            actionSent();
            return false;
        }
        if (!Rs2Bank.setWithdrawAsItem()) return false;
        for (QuestRequirement item : requirements())
        {
            int needed = item.quantity - Rs2Inventory.itemQuantity(item.itemId);
            int bankCount = Math.max(0, Rs2Bank.count(item.name));
            if (needed > 0 && bankCount > 0)
            {
                Rs2Bank.withdrawX(item.name, Math.min(needed, bankCount));
                actionSent();
                return false;
            }
        }
        bankAudited = true;
        if (inventoryReady()) { Rs2Bank.closeBank(); return false; }
        long cost = getMissingRequirementCost();
        long coins = (long) Rs2Inventory.itemQuantity(995) + Math.max(0, Rs2Bank.count("Coins"));
        if (coins < cost)
        {
            setStatus("Not enough GP for " + questName + " inputs (need " + cost + ")");
            return false;
        }
        if (Rs2Inventory.itemQuantity(995) < cost)
        {
            Rs2Bank.withdrawAll("Coins");
            actionSent();
            return false;
        }
        buying = true;
        Rs2Bank.closeBank();
        return false;
    }

    private boolean buyMissingRequirements()
    {
        state = QuestTaskState.BUYING_REQUIREMENTS;
        if (!walkTo("ge", GE, 5)) return false;
        if (!Rs2GrandExchange.isOpen()) { Rs2GrandExchange.openExchange(); return false; }
        if (Rs2GrandExchange.isOfferScreenOpen()) { Rs2GrandExchange.backToOverview(); return false; }
        GrandExchangeOffer[] offers = Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient().getGrandExchangeOffers()).orElse(null);
        if (offers == null) return false;
        boolean waiting = false;
        for (QuestRequirement item : requirements())
        {
            int outstanding = 0;
            for (int i = 0; i < offers.length; i++)
            {
                GrandExchangeOffer offer = offers[i];
                if (offer == null || offer.getItemId() != item.itemId) continue;
                GrandExchangeOfferState offerState = offer.getState();
                if (offerState == GrandExchangeOfferState.BOUGHT || offerState == GrandExchangeOfferState.CANCELLED_BUY)
                {
                    setStatus("Collecting " + item.name);
                    if (i < GrandExchangeSlots.values().length
                            && Rs2GrandExchange.collectOffer(GrandExchangeSlots.values()[i], true))
                    {
                        bankAudited = false;
                        bankDeposited = false;
                        buying = false;
                    }
                    return false;
                }
                if (offerState == GrandExchangeOfferState.BUYING) outstanding += offer.getTotalQuantity();
            }
            int needed = Math.max(0, missing(item) - outstanding);
            if (outstanding > 0) waiting = true;
            if (needed <= 0) continue;
            if (Rs2GrandExchange.getAvailableSlotsCount() <= 0) { setStatus("Waiting for free GE slots"); return false; }
            int price = buyPrice(item.itemId);
            if ((long) Rs2Inventory.itemQuantity(995) < (long) needed * price)
            {
                bankAudited = false;
                buying = false;
                setStatus("Rechecking coins for " + item.name);
                return false;
            }
            setStatus("Buying " + needed + " x " + item.name);
            // Existing quest scripts use buyItem and monitor server offer state before collecting.
            Rs2GrandExchange.buyItem(item.name, price, needed);
            actionSent();
            return false;
        }
        if (!waiting) { buying = false; bankAudited = false; }
        setStatus("Waiting for " + questName + " GE inputs");
        return false;
    }

    private boolean ensureInventorySpace()
    {
        if (!Rs2Inventory.isFull()) return true;
        setStatus("Making space for " + questName + " items");
        if (Rs2GrandExchange.isOpen()) { Rs2GrandExchange.closeExchange(); return false; }
        if (!Rs2Bank.isOpen())
        {
            if (!Rs2Bank.openBank()) Rs2Bank.walkToBankAndUseBank();
            return false;
        }
        List<String> keep = new ArrayList<>(Arrays.asList(preservedItems()));
        keep.add("Coins");
        for (QuestRequirement item : requirements()) keep.add(item.name);
        Rs2Bank.depositAllExcept(keep);
        actionSent();
        return false;
    }

    private boolean handleDialogue()
    {
        if (!Rs2Dialogue.isInCutScene() && !Rs2Dialogue.isInDialogue()
                && !Rs2Dialogue.hasContinue() && !Rs2Dialogue.hasSelectAnOption()) return false;
        KspWalkerGuard.clearActiveWalker(questName + ":dialogue");
        state = QuestTaskState.DIALOGUE;
        setStatus("Handling " + questName + " dialogue");
        if (Rs2Dialogue.hasContinue()) Rs2Dialogue.clickContinue();
        else if (Rs2Dialogue.hasSelectAnOption())
        {
            for (String option : dialogueOptions()) if (Rs2Dialogue.clickOption(option, false)) { nextActionAt = 0; return true; }
            if (!Rs2Dialogue.acceptQuestStartDialogue()) Rs2Dialogue.handleQuestOptionDialogueSelection();
        }
        nextActionAt = 0;
        return true;
    }

    protected boolean walkTo(String target, WorldPoint destination, int radius)
    {
        WorldPoint player = Rs2Player.getWorldLocation();
        String key = questName + ":" + target;
        if (player != null && player.getPlane() == destination.getPlane() && player.distanceTo(destination) <= radius)
        {
            KspWalkerGuard.clear(key);
            return true;
        }
        state = QuestTaskState.WALKING;
        setStatus("Walking to " + target);
        KspWalkerGuard.walkToPoint(key, destination, radius, 1_000L);
        return false;
    }

    protected void talkTo(String name, WorldPoint destination)
    {
        if (!walkTo(name, destination, 4)) return;
        var npc = Microbot.getRs2NpcCache().query().fromWorldView().withName(name).nearestOnClientThread();
        if (npc == null || npc.getWorldLocation() == null) { setStatus("Searching for " + name); return; }
        if (!walkTo(name, npc.getWorldLocation(), 4) || Rs2Player.isMoving()) return;
        state = QuestTaskState.INTERACTING;
        setStatus("Talking to " + name);
        if (npc.click("Talk-to")) actionSent();
    }

    protected void interactObject(int objectId, String action, WorldPoint destination)
    {
        if (!walkTo(action, destination, 3)) return;
        state = QuestTaskState.INTERACTING;
        setStatus(action + " quest object");
        if (Rs2GameObject.interact(objectId, action)) actionSent();
    }

    protected void actionSent() { nextActionAt = System.currentTimeMillis() + 1_200L; }
    protected int varp(int id)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getVarpValue(id)).orElse(-1);
    }
    protected void setStatus(String value)
    {
        if (debugLogging && !value.equals(status)) Microbot.log(questName + ": " + value);
        status = value;
        Microbot.status = value;
    }
}
