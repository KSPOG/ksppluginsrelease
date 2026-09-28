package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.cooksassistant.cookscript;

import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspBankMode;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspTaskDebug;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspWalkerGuard;
import net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.KspBankWidgetHelper;
import net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.KspGrandExchangeHelper;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.cooksassistant.reqs.Items;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.gearea.GEArea;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeAction;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeRequest;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeSlots;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.grandexchange.models.GrandExchangeOfferDetails;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Singleton
public class CooksScript extends Script {
    private static final Logger log = LoggerFactory.getLogger(CooksScript.class);
    private static final int LOOP_DELAY_MS = 600;
    private static final int WALK_REFIRE_COOLDOWN_MS = 3_500;
    private static final int GE_OFFER_INPUT_DELAY_MS = 900;
    private static final int ACTION_COOLDOWN_MS = 1_200;
    private static final int MIN_QUEST_BUY_PRICE = 1_000;
    private static final int MAX_QUEST_BUY_PRICE = 2_000;
    private static final int NPC_REACH_DISTANCE = 5;
    private static final String WALK_KEY_COOK = "Cook's Assistant:cook";
    private static final String WALK_KEY_GE = "Cook's Assistant:grand-exchange";
    private static final String COINS = "Coins";
    private static final int COINS_ID = 995;
    private static final String EGG = Items.EGG.getDisplayName();
    private static final String BUCKET_OF_MILK = Items.BUCKET_OF_MILK.getDisplayName();
    private static final String POT_OF_FLOUR = Items.POT_OF_FLOUR.getDisplayName();
    private static final WorldPoint LUMBRIDGE_COOK_POINT = new WorldPoint(3208, 3214, 0);
    private static final WorldArea LUMBRIDGE_KITCHEN_AREA = new WorldArea(3205, 3208, 10, 10, 0);
    private static final GEArea GRAND_EXCHANGE = GEArea.GRAND_EXCHANGE;
    private static final String[] COOK_DIALOGUE_OPTIONS = {
            "What's wrong?",
            "I'm always happy to help a cook in distress.",
            "Actually, I know where to find this stuff.",
            "I can do that.",
            "Yes",
            "Okay"
    };

    private boolean debugLogging;
    private boolean complete;
    private boolean requirementBankAudited;
    private long lastActionAtMs;
    private final List<QuestBuyRequest> pendingRequirementBuys = new ArrayList<>();
    private final List<QuestBuyRequest> activeRequirementBuys = new ArrayList<>();
    private CooksState state = CooksState.PREPARING;
    private String status = "Idle";

    public boolean run() {
        shutdown();
        complete = false;
        requirementBankAudited = false;
        lastActionAtMs = 0L;
        pendingRequirementBuys.clear();
        activeRequirementBuys.clear();
        state = CooksState.PREPARING;
        status = "Starting Cook's Assistant";

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                runLoop();
            } catch (Exception ex) {
                Microbot.logStackTrace(getClass().getSimpleName(), ex);
            }
        }, 0, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);

        return true;
    }

    private void runLoop() {
        if (!super.run() || !Microbot.isLoggedIn()) {
            return;
        }

        if (isQuestComplete()) {
            complete = true;
            state = CooksState.COMPLETE;
            status = "Cook's Assistant complete";
            shutdown();
            return;
        }

        KspTaskDebug.throttled(log, debugLogging, "Cook's Assistant", "loop", 5_000L,
                "loop | state={} status={} player={} moving={} dialogue={} bankOpen={} geOpen={} offerScreen={} pendingBuys={} activeBuys={} egg={} milk={} flour={}",
                state,
                status,
                Rs2Player.getWorldLocation(),
                Rs2Player.isMoving(),
                Rs2Dialogue.isInDialogue(),
                Rs2Bank.isOpen(),
                Rs2GrandExchange.isOpen(),
                Rs2GrandExchange.isOfferScreenOpen(),
                pendingRequirementBuys,
                activeRequirementBuys,
                Rs2Inventory.itemQuantity(Items.EGG.getItemId()),
                Rs2Inventory.itemQuantity(Items.BUCKET_OF_MILK.getItemId()),
                Rs2Inventory.itemQuantity(Items.POT_OF_FLOUR.getItemId()));

        if (handleDialogue()) {
            return;
        }

        if (Rs2Player.isMoving() || Rs2Player.isAnimating()) {
            return;
        }

        if (!hasRequirementsInInventory() && handleRequirementBuying()) {
            return;
        }

        if (!hasRequirementsInInventory()) {
            state = CooksState.BANKING;
            prepareQuestInventory();
            return;
        }

        if (!ensureAtCook()) {
            state = CooksState.WALKING_TO_COOK;
            return;
        }

        state = CooksState.TALKING_TO_COOK;
        talkToCook();
    }

    private boolean handleDialogue() {
        if (!Rs2Dialogue.isInDialogue()) {
            return false;
        }

        state = CooksState.TALKING_TO_COOK;
        status = "Handling cook dialogue";

        if (Rs2Dialogue.hasContinue()) {
            Rs2Dialogue.clickContinue();
            return true;
        }

        if (!Rs2Dialogue.hasSelectAnOption()) {
            return false;
        }

        for (String option : COOK_DIALOGUE_OPTIONS) {
            if (Rs2Dialogue.clickOption(option, false)) {
                return true;
            }
        }

        if (Rs2Dialogue.acceptQuestStartDialogue()) {
            return true;
        }

        if (Rs2Dialogue.handleQuestOptionDialogueSelection()) {
            return true;
        }

        return Rs2Dialogue.keyPressForDialogueOption(1);
    }

    private boolean handleRequirementBuying() {
        state = CooksState.BUYING_REQUIREMENTS;
        clearSatisfiedRequirementBuys();

        if (!requirementBankAudited) {
            return auditQuestRequirementsAtBank();
        }

        rebuildPendingRequirementBuys();

        if (pendingRequirementBuys.isEmpty() && activeRequirementBuys.isEmpty()) {
            return false;
        }

        if (!ensureAtGrandExchange()) {
            status = "Walking to Grand Exchange";
            return true;
        }

        if (!ensureGrandExchangeOpen()) {
            return true;
        }

        if (Rs2GrandExchange.isOfferScreenOpen()) {
            status = "Returning to GE overview";
            returnToGrandExchangeOverview();
            return true;
        }

        syncActiveRequirementBuysFromOpenOffers();

        if (hasCollectableRequirementBuy()) {
            status = "Collecting Cook's Assistant buys";
            Rs2GrandExchange.collectAllToBank();
            sleepUntil(() -> !Rs2GrandExchange.hasBoughtOffer(), 5_000);
            clearSatisfiedRequirementBuys();
            requirementBankAudited = false;
            return true;
        }

        if (!pendingRequirementBuys.isEmpty()) {
            processAvailableRequirementBuySlots();
            return true;
        }

        status = "Waiting for Cook's Assistant buys";
        return true;
    }

    private boolean auditQuestRequirementsAtBank() {
        status = "Auditing Cook's Assistant items";

        if (Rs2GrandExchange.isOpen()) {
            Rs2GrandExchange.closeExchange();
            sleepUntil(() -> !Rs2GrandExchange.isOpen(), 2_000);
            return true;
        }

        if (!Rs2Bank.isOpen()) {
            if (!Rs2Bank.openBank() && !Rs2Bank.walkToBankAndUseBank()) {
                status = "Walking to bank for Cook's Assistant items";
                return true;
            }
            sleepUntil(Rs2Bank::isOpen, 3_000);
            return true;
        }

        rebuildPendingRequirementBuys();
        requirementBankAudited = true;

        debug("Requirement audit | pending={} active={} bankEgg={} bankMilk={} bankFlour={} coinsInv={} coinsBank={}",
                pendingRequirementBuys,
                activeRequirementBuys,
                Rs2Bank.count(EGG),
                Rs2Bank.count(BUCKET_OF_MILK),
                Rs2Bank.count(POT_OF_FLOUR),
                Rs2Inventory.itemQuantity(COINS_ID),
                Rs2Bank.count(COINS));

        if (pendingRequirementBuys.isEmpty() && activeRequirementBuys.isEmpty()) {
            return false;
        }

        if (!ensureCoinsForRequirementBuying()) {
            return true;
        }

        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 2_000);
        return true;
    }

    private boolean ensureCoinsForRequirementBuying() {
        QuestBuyBudget budget = calculateQuestBuyBudget();

        debug("Quest buy budget | bankCoins={} inventoryCoins={} availableCoins={} estimatedCost={} enough={} pending={} active={}",
                budget.bankCoins,
                budget.inventoryCoins,
                budget.getAvailableCoins(),
                budget.estimatedCost,
                budget.hasEnoughCoins(),
                pendingRequirementBuys,
                activeRequirementBuys);

        if (budget.estimatedCost <= 0L) {
            return true;
        }

        if (!budget.hasEnoughCoins()) {
            status = "Not enough GP for Cook's Assistant buys";
            debug("Not enough GP for Cook's Assistant GE buys | bankCoins={} inventoryCoins={} availableCoins={} estimatedCost={} pending={} active={}",
                    budget.bankCoins,
                    budget.inventoryCoins,
                    budget.getAvailableCoins(),
                    budget.estimatedCost,
                    pendingRequirementBuys,
                    activeRequirementBuys);
            return false;
        }

        if (budget.inventoryCoins >= budget.estimatedCost) {
            return true;
        }

        status = "Withdrawing coins for Cook's Assistant buys";
        if (KspBankWidgetHelper.closeBankTutorialOverlayIfOpenAndWait()) {
            return false;
        }
        Rs2Bank.withdrawAll(COINS);
        sleepUntil(() -> Rs2Inventory.itemQuantity(COINS_ID) >= Math.min(Integer.MAX_VALUE, budget.estimatedCost), 3_000);

        long coinsAfterWithdraw = Math.max(0L, Rs2Inventory.itemQuantity(COINS_ID));
        if (coinsAfterWithdraw < budget.estimatedCost) {
            debug("Coin withdraw did not cover Cook's Assistant budget | coinsInv={} estimatedCost={} bankCoinsAfter={}",
                    coinsAfterWithdraw,
                    budget.estimatedCost,
                    Math.max(0, Rs2Bank.count(COINS)));
        }

        return coinsAfterWithdraw >= budget.estimatedCost;
    }

    private boolean ensureAtGrandExchange() {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        WorldArea exchangeArea = GRAND_EXCHANGE.toWorldArea();
        if (playerLocation != null && exchangeArea.contains(playerLocation)) {
            KspWalkerGuard.clear(WALK_KEY_GE);
            return true;
        }

        KspWalkerGuard.walkToDestination(
                WALK_KEY_GE,
                GRAND_EXCHANGE::getRandomPoint,
                exchangeArea::contains,
                3,
                WALK_REFIRE_COOLDOWN_MS);
        return false;
    }

    private boolean ensureGrandExchangeOpen() {
        if (Rs2GrandExchange.isOpen()) {
            return true;
        }

        if (Rs2Bank.isOpen()) {
            KspGrandExchangeHelper.closeBankBeforeExchange();
            sleepUntil(() -> !Rs2Bank.isOpen(), 2_000);
            return false;
        }

        status = "Opening Grand Exchange";

        if (KspGrandExchangeHelper.openExchangeDirectly()) {
            sleepUntil(Rs2GrandExchange::isOpen, 3_000);
            return Rs2GrandExchange.isOpen();
        }

        boolean clicked = KspGrandExchangeHelper.interactClerk();

        if (clicked) {
            sleepUntil(Rs2GrandExchange::isOpen, 3_000);
        }

        debug("Grand Exchange Clerk interaction | clicked={} player={} geOpen={}", clicked, Rs2Player.getWorldLocation(), Rs2GrandExchange.isOpen());
        return Rs2GrandExchange.isOpen();
    }

    private void processAvailableRequirementBuySlots() {
        int availableSlots = Rs2GrandExchange.getAvailableSlotsCount();

        debug("Processing quest GE slots | availableSlots={} pending={} active={}",
                availableSlots,
                pendingRequirementBuys,
                activeRequirementBuys);

        while (availableSlots > 0 && !pendingRequirementBuys.isEmpty()) {
            QuestBuyRequest request = pendingRequirementBuys.get(0);

            if (!placeRequirementBuyOffer(request)) {
                return;
            }

            availableSlots = Rs2GrandExchange.getAvailableSlotsCount();
        }
    }

    private boolean placeRequirementBuyOffer(QuestBuyRequest buyRequest) {
        if (buyRequest == null || buyRequest.quantity <= 0) {
            return false;
        }

        waitForActionCooldown();
        status = "Buying " + buyRequest.quantity + "x " + buyRequest.itemName;

        int offerPrice = getQuestBuyOfferPrice(buyRequest);
        if (offerPrice <= 0) {
            status = "Not enough GP for " + buyRequest.itemName;
            debug("Skipping Cook's Assistant GE offer due to insufficient coins | item={} qty={} coinsInv={} reservedForOthers={}",
                    buyRequest.itemName,
                    buyRequest.quantity,
                    Rs2Inventory.itemQuantity(COINS_ID),
                    getMinimumPendingQuestBuyCostExcluding(buyRequest));
            return false;
        }

        var requestBuilder = GrandExchangeRequest.builder()
                .action(GrandExchangeAction.BUY)
                .itemName(buyRequest.itemName)
                .exact(true)
                .quantity(buyRequest.quantity)
                .closeAfterCompletion(false);

        GrandExchangeRequest request = requestBuilder.price(offerPrice).build();

        waitForGrandExchangeOfferInput();

        boolean offered = Rs2GrandExchange.processOffer(request);

        debug("GE requirement buy offer | item={} qty={} price={} offered={} slots={} coinsInv={}",
                buyRequest.itemName,
                buyRequest.quantity,
                offerPrice,
                offered,
                Rs2GrandExchange.isOpen() ? Rs2GrandExchange.getAvailableSlotsCount() : -1,
                Rs2Inventory.itemQuantity(COINS_ID));

        if (!offered) {
            return false;
        }

        lastActionAtMs = System.currentTimeMillis();
        pendingRequirementBuys.remove(0);
        activeRequirementBuys.add(buyRequest);
        sleepUntil(() -> !Rs2GrandExchange.isOfferScreenOpen(), 2_000);
        return true;
    }

    private int getQuestBuyOfferPrice(QuestBuyRequest buyRequest) {
        long inventoryCoins = Math.max(0L, Rs2Inventory.itemQuantity(COINS_ID));
        long reservedForOtherPending = getMinimumPendingQuestBuyCostExcluding(buyRequest);
        long spendableCoins = inventoryCoins - reservedForOtherPending;
        long minimumCost = (long) buyRequest.quantity * MIN_QUEST_BUY_PRICE;

        if (spendableCoins < minimumCost) {
            return 0;
        }

        int affordablePerItem = (int) Math.min(Integer.MAX_VALUE, spendableCoins / Math.max(1, buyRequest.quantity));
        int upperBound = Math.min(MAX_QUEST_BUY_PRICE, affordablePerItem);
        return ThreadLocalRandom.current().nextInt(MIN_QUEST_BUY_PRICE, upperBound + 1);
    }

    private QuestBuyBudget calculateQuestBuyBudget() {
        QuestBuyBudget budget = new QuestBuyBudget(
                Math.max(0L, Rs2Bank.count(COINS)),
                Math.max(0L, Rs2Inventory.itemQuantity(COINS_ID))
        );

        budget.estimatedCost = getMinimumPendingQuestBuyCost();
        return budget;
    }

    private long getMinimumPendingQuestBuyCost() {
        long cost = 0L;

        for (QuestBuyRequest request : pendingRequirementBuys) {
            cost += (long) request.quantity * MIN_QUEST_BUY_PRICE;
        }

        return cost;
    }

    private long getMinimumPendingQuestBuyCostExcluding(QuestBuyRequest excludedRequest) {
        long cost = 0L;

        for (QuestBuyRequest request : pendingRequirementBuys) {
            if (request == excludedRequest) {
                continue;
            }

            cost += (long) request.quantity * MIN_QUEST_BUY_PRICE;
        }

        return cost;
    }

    private void rebuildPendingRequirementBuys() {
        queueRequirementBuy(EGG, getBaseMissingRequirementQuantity(EGG) - getQueuedRequirementBuyQuantity(EGG));
        queueRequirementBuy(BUCKET_OF_MILK, getBaseMissingRequirementQuantity(BUCKET_OF_MILK) - getQueuedRequirementBuyQuantity(BUCKET_OF_MILK));
        queueRequirementBuy(POT_OF_FLOUR, getBaseMissingRequirementQuantity(POT_OF_FLOUR) - getQueuedRequirementBuyQuantity(POT_OF_FLOUR));
        clearSatisfiedRequirementBuys();
    }

    private void queueRequirementBuy(String itemName, int quantity) {
        if (quantity <= 0 || isRequirementBuyQueued(itemName)) {
            return;
        }

        pendingRequirementBuys.add(new QuestBuyRequest(itemName, quantity));
    }

    private int getBaseMissingRequirementQuantity(String itemName) {
        return countOwnedRequirement(itemName) <= 0 ? 1 : 0;
    }

    private int countOwnedRequirement(String itemName) {
        if (EGG.equalsIgnoreCase(itemName)) {
            return Rs2Inventory.itemQuantity(Items.EGG.getItemId()) + Math.max(0, Rs2Bank.count(EGG));
        }

        if (BUCKET_OF_MILK.equalsIgnoreCase(itemName)) {
            return Rs2Inventory.itemQuantity(Items.BUCKET_OF_MILK.getItemId()) + Math.max(0, Rs2Bank.count(BUCKET_OF_MILK));
        }

        if (POT_OF_FLOUR.equalsIgnoreCase(itemName)) {
            return Rs2Inventory.itemQuantity(Items.POT_OF_FLOUR.getItemId()) + Math.max(0, Rs2Bank.count(POT_OF_FLOUR));
        }

        return Rs2Inventory.itemQuantity(itemName) + Math.max(0, Rs2Bank.count(itemName));
    }

    private int getQueuedRequirementBuyQuantity(String itemName) {
        int quantity = 0;

        for (QuestBuyRequest request : pendingRequirementBuys) {
            if (request.matches(itemName)) {
                quantity += request.quantity;
            }
        }

        for (QuestBuyRequest request : activeRequirementBuys) {
            if (request.matches(itemName)) {
                quantity += request.quantity;
            }
        }

        return quantity;
    }

    private boolean isRequirementBuyQueued(String itemName) {
        return getQueuedRequirementBuyQuantity(itemName) > 0;
    }

    private void syncActiveRequirementBuysFromOpenOffers() {
        for (GrandExchangeSlots slot : Rs2GrandExchange.getActiveOfferSlots()) {
            GrandExchangeOfferDetails details = Rs2GrandExchange.getOfferDetails(slot);

            if (!isExpectedRequirementBuy(details)) {
                continue;
            }

            removePendingRequirementBuy(details.getItemName());

            if (!isRequirementBuyQueuedInActive(details.getItemName())) {
                activeRequirementBuys.add(new QuestBuyRequest(details.getItemName(), Math.max(1, details.getTotalQuantity())));
            }
        }
    }

    private void removePendingRequirementBuy(String itemName) {
        Iterator<QuestBuyRequest> iterator = pendingRequirementBuys.iterator();

        while (iterator.hasNext()) {
            QuestBuyRequest request = iterator.next();

            if (request.matches(itemName)) {
                iterator.remove();
            }
        }
    }

    private boolean isRequirementBuyQueuedInActive(String itemName) {
        for (QuestBuyRequest request : activeRequirementBuys) {
            if (request.matches(itemName)) {
                return true;
            }
        }

        return false;
    }

    private boolean hasCollectableRequirementBuy() {
        if (Rs2GrandExchange.hasBoughtOffer()) {
            return true;
        }

        for (GrandExchangeOfferDetails details : Rs2GrandExchange.getCompletedOffers().values()) {
            if (isExpectedRequirementBuy(details)) {
                return true;
            }
        }

        for (GrandExchangeSlots slot : Rs2GrandExchange.getActiveOfferSlots()) {
            GrandExchangeOfferDetails details = Rs2GrandExchange.getOfferDetails(slot);
            if (isExpectedRequirementBuy(details) && details.isCompleted()) {
                return true;
            }
        }

        return false;
    }

    private boolean isExpectedRequirementBuy(GrandExchangeOfferDetails details) {
        return details != null
                && !details.isSelling()
                && details.getItemName() != null
                && isQuestRequirement(details.getItemName());
    }

    private boolean isQuestRequirement(String itemName) {
        return EGG.equalsIgnoreCase(itemName)
                || BUCKET_OF_MILK.equalsIgnoreCase(itemName)
                || POT_OF_FLOUR.equalsIgnoreCase(itemName);
    }

    private void clearSatisfiedRequirementBuys() {
        clearSatisfiedRequirementBuys(pendingRequirementBuys);
        clearSatisfiedRequirementBuys(activeRequirementBuys);
    }

    private void clearSatisfiedRequirementBuys(List<QuestBuyRequest> requests) {
        Iterator<QuestBuyRequest> iterator = requests.iterator();

        while (iterator.hasNext()) {
            QuestBuyRequest request = iterator.next();

            if (getBaseMissingRequirementQuantity(request.itemName) > 0) {
                continue;
            }

            iterator.remove();
        }
    }

    private void prepareQuestInventory() {
        status = "Preparing Cook's Assistant items";

        if (Rs2GrandExchange.isOpen()) {
            Rs2GrandExchange.closeExchange();
            sleepUntil(() -> !Rs2GrandExchange.isOpen(), 2_000);
            return;
        }

        if (!Rs2Bank.isOpen()) {
            if (!Rs2Bank.openBank() && !Rs2Bank.walkToBankAndUseBank()) {
                status = "Walking to bank";
                return;
            }
            sleepUntil(Rs2Bank::isOpen, 3_000);
            return;
        }

        if (!KspBankMode.ensureWithdrawAsItem()) {
            debug("Waiting for withdraw-as-item mode before Cook's Assistant withdrawals");
            return;
        }

        if (KspBankWidgetHelper.closeBankTutorialOverlayIfOpenAndWait()) {
            return;
        }

        Rs2Bank.depositAllExcept(EGG, BUCKET_OF_MILK, POT_OF_FLOUR);
        sleep(250, 450);

        withdrawRequiredItem(Items.EGG);
        withdrawRequiredItem(Items.BUCKET_OF_MILK);
        withdrawRequiredItem(Items.POT_OF_FLOUR);

        if (hasRequirementsInInventory()) {
            Rs2Bank.closeBank();
            sleepUntil(() -> !Rs2Bank.isOpen(), 2_000);
            return;
        }

        status = "Missing Cook's Assistant requirements";
        debug("Missing requirements | bankEgg={} bankMilk={} bankFlour={}",
                Rs2Bank.count(EGG),
                Rs2Bank.count(BUCKET_OF_MILK),
                Rs2Bank.count(POT_OF_FLOUR));
    }

    private void withdrawRequiredItem(Items item) {
        if (Rs2Inventory.itemQuantity(item.getItemId()) > 0 || Rs2Bank.count(item.getDisplayName()) <= 0) {
            return;
        }

        Rs2Bank.withdrawX(item.getDisplayName(), 1);
        sleepUntil(() -> Rs2Inventory.itemQuantity(item.getItemId()) > 0, 2_000);
    }

    private boolean ensureAtCook() {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation != null && LUMBRIDGE_KITCHEN_AREA.contains(playerLocation)) {
            KspWalkerGuard.clear(WALK_KEY_COOK);
            return true;
        }

        status = "Walking to Lumbridge cook";
        KspWalkerGuard.walkToDestination(
                WALK_KEY_COOK,
                () -> LUMBRIDGE_COOK_POINT,
                LUMBRIDGE_KITCHEN_AREA::contains,
                3,
                WALK_REFIRE_COOLDOWN_MS);
        return false;
    }

    private boolean talkToCook() {
        Rs2NpcModel cook = findNearestCook();
        if (cook == null) {
            status = "Searching for Cook";
            KspWalkerGuard.walkFastCanvasToPoint(WALK_KEY_COOK, LUMBRIDGE_COOK_POINT, NPC_REACH_DISTANCE, WALK_REFIRE_COOLDOWN_MS);
            return false;
        }

        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        WorldPoint npcLocation = cook.getWorldLocation();
        if (playerLocation == null || npcLocation == null) {
            return false;
        }

        if (playerLocation.distanceTo(npcLocation) > NPC_REACH_DISTANCE) {
            status = "Walking to Cook";
            KspWalkerGuard.walkFastCanvasToPoint(WALK_KEY_COOK, npcLocation, NPC_REACH_DISTANCE, WALK_REFIRE_COOLDOWN_MS);
            return false;
        }

        status = "Talking to Cook";
        boolean clicked = cook.click("Talk-to");
        if (clicked) {
            KspWalkerGuard.clear(WALK_KEY_COOK);
            sleepUntil(Rs2Dialogue::isInDialogue, 4_000);
        }
        return clicked;
    }

    private Rs2NpcModel findNearestCook() {
        return Microbot.getRs2NpcCache().query()
                .fromWorldView()
                .withName("Cook")
                .nearestOnClientThread();
    }

    private boolean hasRequirementsInInventory() {
        return Rs2Inventory.itemQuantity(Items.EGG.getItemId()) > 0
                && Rs2Inventory.itemQuantity(Items.BUCKET_OF_MILK.getItemId()) > 0
                && Rs2Inventory.itemQuantity(Items.POT_OF_FLOUR.getItemId()) > 0;
    }

    private boolean isQuestComplete() {
        return Rs2Player.getQuestState(Quest.COOKS_ASSISTANT) == QuestState.FINISHED;
    }

    public boolean isComplete() {
        return complete || isQuestComplete();
    }

    public void setDebugLogging(boolean debugLogging) {
        this.debugLogging = debugLogging;
    }

    public CooksState getState() {
        return state;
    }

    public String getStatus() {
        return status;
    }

    @Override
    public void shutdown() {
        KspWalkerGuard.clear(WALK_KEY_COOK);
        KspWalkerGuard.clear(WALK_KEY_GE);
        pendingRequirementBuys.clear();
        activeRequirementBuys.clear();
        requirementBankAudited = false;
        lastActionAtMs = 0L;
        state = CooksState.PREPARING;
        status = "Idle";
        super.shutdown();
    }

    private void returnToGrandExchangeOverview() {
        if (System.currentTimeMillis() - lastActionAtMs < ACTION_COOLDOWN_MS) {
            return;
        }

        Rs2GrandExchange.backToOverview();
        lastActionAtMs = System.currentTimeMillis();
        sleepUntil(() -> !Rs2GrandExchange.isOfferScreenOpen(), 2_000);
    }

    private void waitForActionCooldown() {
        long elapsed = System.currentTimeMillis() - lastActionAtMs;
        long remaining = ACTION_COOLDOWN_MS - elapsed;

        if (remaining > 0) {
            sleep((int) remaining, (int) remaining + 150);
        }
    }

    private void waitForGrandExchangeOfferInput() {
        sleep(GE_OFFER_INPUT_DELAY_MS, GE_OFFER_INPUT_DELAY_MS + 250);
    }

    private void debug(String message, Object... args) {
        if (debugLogging) {
            KspTaskDebug.info(log, true, "Cook's Assistant", message, args);
        }
    }

    private static final class QuestBuyRequest {
        private final String itemName;
        private final int quantity;

        private QuestBuyRequest(String itemName, int quantity) {
            this.itemName = itemName;
            this.quantity = quantity;
        }

        private boolean matches(String otherItemName) {
            return itemName != null
                    && otherItemName != null
                    && itemName.equalsIgnoreCase(otherItemName);
        }

        @Override
        public String toString() {
            return quantity + "x " + itemName.toLowerCase(Locale.ROOT);
        }
    }

    private static final class QuestBuyBudget {
        private final long bankCoins;
        private final long inventoryCoins;
        private long estimatedCost;

        private QuestBuyBudget(long bankCoins, long inventoryCoins) {
            this.bankCoins = bankCoins;
            this.inventoryCoins = inventoryCoins;
        }

        private long getAvailableCoins() {
            return bankCoins + inventoryCoins;
        }

        private boolean hasEnoughCoins() {
            return getAvailableCoins() >= estimatedCost;
        }
    }
}
