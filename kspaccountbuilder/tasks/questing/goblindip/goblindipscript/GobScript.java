package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.goblindip.goblindipscript;

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
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.goblindip.reqs.GobReqs;
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
public class GobScript extends Script {
    private static final Logger log = LoggerFactory.getLogger(GobScript.class);
    private static final int LOOP_DELAY_MS = 600;
    private static final int WALK_REFIRE_COOLDOWN_MS = 3_500;
    private static final int GE_OFFER_INPUT_DELAY_MS = 900;
    private static final int ACTION_COOLDOWN_MS = 1_200;
    private static final int MIN_QUEST_BUY_PRICE = 1_000;
    private static final int MAX_QUEST_BUY_PRICE = 2_000;
    private static final int NPC_REACH_DISTANCE = 4;
    private static final String WALK_KEY_VILLAGE = "Goblin Diplomacy:village";
    private static final String WALK_KEY_GENERAL = "Goblin Diplomacy:general";
    private static final String WALK_KEY_GE = "Goblin Diplomacy:grand-exchange";
    private static final String COINS = "Coins";
    private static final int COINS_ID = 995;
    private static final String GOBLIN_MAIL = GobReqs.GOBLIN_MAIL.getDisplayName();
    private static final String BLUE_DYE = GobReqs.BLUE_DYE.getDisplayName();
    private static final String ORANGE_DYE = GobReqs.ORANGE_DYE.getDisplayName();
    private static final String BLUE_GOBLIN_MAIL = "Blue goblin mail";
    private static final String ORANGE_GOBLIN_MAIL = "Orange goblin mail";
    private static final String[] GENERAL_NAMES = {"General Bentnoze", "General Wartface"};
    private static final WorldPoint GOBLIN_VILLAGE_POINT = new WorldPoint(2956, 3511, 0);
    private static final WorldArea GOBLIN_VILLAGE_AREA = new WorldArea(2950, 3504, 16, 16, 0);
    private static final GEArea GRAND_EXCHANGE = GEArea.GRAND_EXCHANGE;
    private static final String[] QUEST_DIALOGUE_OPTIONS = {
            "Do you want me to pick an armour colour for you?",
            "Have you decided",
            "different colour",
            "I've got some orange armour here",
            "I've got some blue armour here",
            "I've got some brown armour here",
            "Yes",
            "Okay"
    };

    private boolean debugLogging;
    private boolean complete;
    private boolean plainMailAccepted;
    private boolean blueMailAccepted;
    private boolean orangeMailAccepted;
    private boolean mailHandInInProgress;
    private boolean requirementBankAudited;
    private long lastActionAtMs;
    private int lastPlainMailCount;
    private int lastBlueMailCount;
    private int lastOrangeMailCount;
    private final List<QuestBuyRequest> pendingRequirementBuys = new ArrayList<>();
    private final List<QuestBuyRequest> activeRequirementBuys = new ArrayList<>();
    private GobState state = GobState.PREPARING;
    private String status = "Idle";

    public boolean run() {
        shutdown();
        complete = false;
        requirementBankAudited = false;
        resetHandInTracking();
        lastActionAtMs = 0L;
        pendingRequirementBuys.clear();
        activeRequirementBuys.clear();
        state = GobState.PREPARING;
        status = "Starting Goblin Diplomacy";

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
            state = GobState.COMPLETE;
            status = "Goblin Diplomacy complete";
            shutdown();
            return;
        }

        updateAcceptedMailFromInventoryLoss();

        KspTaskDebug.throttled(log, debugLogging, "Goblin Diplomacy", "loop", 5_000L,
                "loop | state={} status={} player={} moving={} dialogue={} bankOpen={} geOpen={} offerScreen={} pendingBuys={} activeBuys={} invMail={} invBlueMail={} invOrangeMail={} invBlueDye={} invOrangeDye={} acceptedPlain={} acceptedBlue={} acceptedOrange={} handInProgress={}",
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
                Rs2Inventory.itemQuantity(GobReqs.GOBLIN_MAIL.getItemId()),
                Rs2Inventory.itemQuantity(BLUE_GOBLIN_MAIL),
                Rs2Inventory.itemQuantity(ORANGE_GOBLIN_MAIL),
                Rs2Inventory.itemQuantity(GobReqs.BLUE_DYE.getItemId()),
                Rs2Inventory.itemQuantity(GobReqs.ORANGE_DYE.getItemId()),
                plainMailAccepted,
                blueMailAccepted,
                orangeMailAccepted,
                mailHandInInProgress);

        if (handleDialogue()) {
            return;
        }

        clearInactiveHandInTracking();

        if (Rs2Player.isMoving() || Rs2Player.isAnimating()) {
            return;
        }

        if (handleRequirementBuying()) {
            return;
        }

        if (!hasQuestMaterialsInInventory()) {
            state = GobState.BANKING;
            prepareQuestInventory();
            return;
        }

        if (prepareDyedGoblinMail()) {
            state = GobState.PREPARING;
            return;
        }

        if (!hasTurnInItemsInInventory()) {
            state = GobState.BANKING;
            prepareQuestInventory();
            return;
        }

        if (!ensureAtGoblinVillage()) {
            state = GobState.WALKING_TO_VILLAGE;
            return;
        }

        state = GobState.TALKING_TO_GENERALS;
        talkToGeneral();
    }

    private boolean handleDialogue() {
        if (!Rs2Dialogue.isInDialogue()) {
            return false;
        }

        updateAcceptedMailFromInventoryLoss();
        state = GobState.TALKING_TO_GENERALS;
        status = "Handling general dialogue";

        if (Rs2Dialogue.hasContinue()) {
            Rs2Dialogue.clickContinue();
            sleep(150, 300);
            updateAcceptedMailFromInventoryLoss();
            return true;
        }

        if (!Rs2Dialogue.hasSelectAnOption()) {
            return false;
        }

        for (String option : QUEST_DIALOGUE_OPTIONS) {
            if (Rs2Dialogue.clickOption(option, false)) {
                sleep(150, 300);
                updateAcceptedMailFromInventoryLoss();
                return true;
            }
        }

        if (Rs2Dialogue.acceptQuestStartDialogue()) {
            sleep(150, 300);
            updateAcceptedMailFromInventoryLoss();
            return true;
        }

        if (Rs2Dialogue.handleQuestOptionDialogueSelection()) {
            sleep(150, 300);
            updateAcceptedMailFromInventoryLoss();
            return true;
        }

        boolean handled = Rs2Dialogue.keyPressForDialogueOption(1);
        if (handled) {
            sleep(150, 300);
            updateAcceptedMailFromInventoryLoss();
        }
        return handled;
    }

    private boolean handleRequirementBuying() {
        if (hasQuestMaterialsInInventory()) {
            return false;
        }

        state = GobState.BUYING_REQUIREMENTS;
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
            status = "Collecting Goblin Diplomacy buys";
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

        status = "Waiting for Goblin Diplomacy buys";
        return true;
    }

    private boolean auditQuestRequirementsAtBank() {
        status = "Auditing Goblin Diplomacy items";

        if (Rs2GrandExchange.isOpen()) {
            Rs2GrandExchange.closeExchange();
            sleepUntil(() -> !Rs2GrandExchange.isOpen(), 2_000);
            return true;
        }

        if (!Rs2Bank.isOpen()) {
            if (!Rs2Bank.openBank() && !Rs2Bank.walkToBankAndUseBank()) {
                status = "Walking to bank for Goblin Diplomacy items";
                return true;
            }
            sleepUntil(Rs2Bank::isOpen, 3_000);
            return true;
        }

        rebuildPendingRequirementBuys();
        requirementBankAudited = true;

        debug("Requirement audit | pending={} active={} bankMail={} bankBlueDye={} bankOrangeDye={} bankBlueMail={} bankOrangeMail={} coinsInv={} coinsBank={}",
                pendingRequirementBuys,
                activeRequirementBuys,
                Rs2Bank.count(GOBLIN_MAIL),
                Rs2Bank.count(BLUE_DYE),
                Rs2Bank.count(ORANGE_DYE),
                Rs2Bank.count(BLUE_GOBLIN_MAIL),
                Rs2Bank.count(ORANGE_GOBLIN_MAIL),
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
            status = "Not enough GP for Goblin Diplomacy buys";
            debug("Not enough GP for Goblin Diplomacy GE buys | bankCoins={} inventoryCoins={} availableCoins={} estimatedCost={} pending={} active={}",
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

        status = "Withdrawing coins for Goblin Diplomacy buys";
        if (KspBankWidgetHelper.closeBankTutorialOverlayIfOpenAndWait()) {
            return false;
        }
        Rs2Bank.withdrawAll(COINS);
        sleepUntil(() -> Rs2Inventory.itemQuantity(COINS_ID) >= Math.min(Integer.MAX_VALUE, budget.estimatedCost), 3_000);

        long coinsAfterWithdraw = Math.max(0L, Rs2Inventory.itemQuantity(COINS_ID));
        if (coinsAfterWithdraw < budget.estimatedCost) {
            debug("Coin withdraw did not cover Goblin Diplomacy budget | coinsInv={} estimatedCost={} bankCoinsAfter={}",
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
            debug("Skipping Goblin Diplomacy GE offer due to insufficient coins | item={} qty={} coinsInv={} reservedForOthers={}",
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
        queueRequirementBuy(GOBLIN_MAIL, getBaseMissingRequirementQuantity(GOBLIN_MAIL) - getQueuedRequirementBuyQuantity(GOBLIN_MAIL));
        queueRequirementBuy(BLUE_DYE, getBaseMissingRequirementQuantity(BLUE_DYE) - getQueuedRequirementBuyQuantity(BLUE_DYE));
        queueRequirementBuy(ORANGE_DYE, getBaseMissingRequirementQuantity(ORANGE_DYE) - getQueuedRequirementBuyQuantity(ORANGE_DYE));
        clearSatisfiedRequirementBuys();
    }

    private void queueRequirementBuy(String itemName, int quantity) {
        if (quantity <= 0 || isRequirementBuyQueued(itemName)) {
            return;
        }

        pendingRequirementBuys.add(new QuestBuyRequest(itemName, quantity));
    }

    private int getBaseMissingRequirementQuantity(String itemName) {
        if (GOBLIN_MAIL.equalsIgnoreCase(itemName)) {
            int requiredPlainMail = getRequiredPlainMailCountAnywhere();
            return Math.max(0, requiredPlainMail - countOwnedRequirement(GOBLIN_MAIL));
        }

        if (BLUE_DYE.equalsIgnoreCase(itemName)) {
            if (blueMailAccepted) {
                return 0;
            }
            return countOwnedRequirement(BLUE_GOBLIN_MAIL) <= 0 && countOwnedRequirement(BLUE_DYE) <= 0 ? 1 : 0;
        }

        if (ORANGE_DYE.equalsIgnoreCase(itemName)) {
            if (orangeMailAccepted) {
                return 0;
            }
            return countOwnedRequirement(ORANGE_GOBLIN_MAIL) <= 0 && countOwnedRequirement(ORANGE_DYE) <= 0 ? 1 : 0;
        }

        return 0;
    }

    private int getRequiredPlainMailCountAnywhere() {
        int required = plainMailAccepted ? 0 : 1;

        if (!blueMailAccepted && countOwnedRequirement(BLUE_GOBLIN_MAIL) <= 0) {
            required++;
        }

        if (!orangeMailAccepted && countOwnedRequirement(ORANGE_GOBLIN_MAIL) <= 0) {
            required++;
        }

        return required;
    }

    private int countOwnedRequirement(String itemName) {
        if (GOBLIN_MAIL.equalsIgnoreCase(itemName)) {
            return Rs2Inventory.itemQuantity(GobReqs.GOBLIN_MAIL.getItemId()) + Math.max(0, Rs2Bank.count(GOBLIN_MAIL));
        }

        if (BLUE_DYE.equalsIgnoreCase(itemName)) {
            return Rs2Inventory.itemQuantity(GobReqs.BLUE_DYE.getItemId()) + Math.max(0, Rs2Bank.count(BLUE_DYE));
        }

        if (ORANGE_DYE.equalsIgnoreCase(itemName)) {
            return Rs2Inventory.itemQuantity(GobReqs.ORANGE_DYE.getItemId()) + Math.max(0, Rs2Bank.count(ORANGE_DYE));
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
        return GOBLIN_MAIL.equalsIgnoreCase(itemName)
                || BLUE_DYE.equalsIgnoreCase(itemName)
                || ORANGE_DYE.equalsIgnoreCase(itemName);
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

    private void prepareQuestInventory() {
        status = "Preparing Goblin Diplomacy items";

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
            debug("Waiting for withdraw-as-item mode before Goblin Diplomacy withdrawals");
            return;
        }

        if (KspBankWidgetHelper.closeBankTutorialOverlayIfOpenAndWait()) {
            return;
        }

        Rs2Bank.depositAllExcept(GOBLIN_MAIL, BLUE_DYE, ORANGE_DYE, BLUE_GOBLIN_MAIL, ORANGE_GOBLIN_MAIL);
        sleep(250, 450);

        withdrawExistingColouredMail();
        withdrawDyesForMissingMail();
        withdrawRequiredPlainMail();

        if (hasQuestMaterialsInInventory()) {
            Rs2Bank.closeBank();
            sleepUntil(() -> !Rs2Bank.isOpen(), 2_000);
            return;
        }

        status = "Missing Goblin Diplomacy requirements";
        debug("Missing requirements | bankGoblinMail={} bankBlueDye={} bankOrangeDye={} bankBlueMail={} bankOrangeMail={}",
                Rs2Bank.count(GOBLIN_MAIL),
                Rs2Bank.count(BLUE_DYE),
                Rs2Bank.count(ORANGE_DYE),
                Rs2Bank.count(BLUE_GOBLIN_MAIL),
                Rs2Bank.count(ORANGE_GOBLIN_MAIL));
    }

    private void withdrawExistingColouredMail() {
        if (!blueMailAccepted && !hasBlueGoblinMail() && Rs2Bank.count(BLUE_GOBLIN_MAIL) > 0) {
            Rs2Bank.withdrawX(BLUE_GOBLIN_MAIL, 1);
            sleepUntil(this::hasBlueGoblinMail, 2_000);
        }

        if (!orangeMailAccepted && !hasOrangeGoblinMail() && Rs2Bank.count(ORANGE_GOBLIN_MAIL) > 0) {
            Rs2Bank.withdrawX(ORANGE_GOBLIN_MAIL, 1);
            sleepUntil(this::hasOrangeGoblinMail, 2_000);
        }
    }

    private void withdrawDyesForMissingMail() {
        if (!blueMailAccepted && !hasBlueGoblinMail() && !hasBlueDye() && Rs2Bank.count(BLUE_DYE) > 0) {
            Rs2Bank.withdrawX(BLUE_DYE, 1);
            sleepUntil(this::hasBlueDye, 2_000);
        }

        if (!orangeMailAccepted && !hasOrangeGoblinMail() && !hasOrangeDye() && Rs2Bank.count(ORANGE_DYE) > 0) {
            Rs2Bank.withdrawX(ORANGE_DYE, 1);
            sleepUntil(this::hasOrangeDye, 2_000);
        }
    }

    private void withdrawRequiredPlainMail() {
        int neededPlainMail = getRequiredPlainMailCount();
        int inventoryPlainMail = Rs2Inventory.itemQuantity(GobReqs.GOBLIN_MAIL.getItemId());
        int missingPlainMail = Math.max(0, neededPlainMail - inventoryPlainMail);

        if (missingPlainMail <= 0) {
            return;
        }

        if (Rs2Bank.count(GOBLIN_MAIL) <= 0) {
            return;
        }

        Rs2Bank.withdrawX(GOBLIN_MAIL, missingPlainMail);
        sleepUntil(() -> Rs2Inventory.itemQuantity(GobReqs.GOBLIN_MAIL.getItemId()) >= neededPlainMail, 2_000);
    }

    private boolean prepareDyedGoblinMail() {
        if (!orangeMailAccepted && !hasOrangeGoblinMail() && hasOrangeDye() && hasPlainGoblinMail()) {
            status = "Creating orange goblin mail";
            return useDyeOnGoblinMail(GobReqs.ORANGE_DYE.getItemId(), ORANGE_GOBLIN_MAIL);
        }

        if (!blueMailAccepted && !hasBlueGoblinMail() && hasBlueDye() && hasPlainGoblinMail()) {
            status = "Creating blue goblin mail";
            return useDyeOnGoblinMail(GobReqs.BLUE_DYE.getItemId(), BLUE_GOBLIN_MAIL);
        }

        return false;
    }

    private boolean useDyeOnGoblinMail(int dyeId, String expectedMailName) {
        if (!Rs2Inventory.use(dyeId)) {
            return false;
        }

        sleep(200, 350);

        if (!Rs2Inventory.interact(GobReqs.GOBLIN_MAIL.getItemId(), "Use")) {
            return false;
        }

        return sleepUntil(() -> Rs2Inventory.hasItem(expectedMailName), 2_500);
    }

    private boolean ensureAtGoblinVillage() {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation != null && GOBLIN_VILLAGE_AREA.contains(playerLocation)) {
            KspWalkerGuard.clear(WALK_KEY_VILLAGE);
            return true;
        }

        status = "Walking to Goblin Village";
        KspWalkerGuard.walkToDestination(
                WALK_KEY_VILLAGE,
                () -> GOBLIN_VILLAGE_POINT,
                GOBLIN_VILLAGE_AREA::contains,
                3,
                WALK_REFIRE_COOLDOWN_MS);
        return false;
    }

    private boolean talkToGeneral() {
        Rs2NpcModel general = findNearestGeneral();
        if (general == null) {
            status = "Searching for goblin generals";
            KspWalkerGuard.walkFastCanvasToPoint(WALK_KEY_GENERAL, GOBLIN_VILLAGE_POINT, NPC_REACH_DISTANCE, WALK_REFIRE_COOLDOWN_MS);
            return false;
        }

        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        WorldPoint npcLocation = general.getWorldLocation();
        if (playerLocation == null || npcLocation == null) {
            return false;
        }

        if (playerLocation.distanceTo(npcLocation) > NPC_REACH_DISTANCE) {
            status = "Walking to " + general.getName();
            KspWalkerGuard.walkFastCanvasToPoint(WALK_KEY_GENERAL, npcLocation, NPC_REACH_DISTANCE, WALK_REFIRE_COOLDOWN_MS);
            return false;
        }

        status = "Talking to " + general.getName();
        snapshotMailCounts();
        boolean clicked = general.click("Talk-to");
        if (clicked) {
            mailHandInInProgress = true;
            KspWalkerGuard.clear(WALK_KEY_GENERAL);
            boolean dialogueOpened = sleepUntil(Rs2Dialogue::isInDialogue, 4_000);
            updateAcceptedMailFromInventoryLoss();
            if (!dialogueOpened) {
                mailHandInInProgress = false;
            }
        }
        return clicked;
    }

    private Rs2NpcModel findNearestGeneral() {
        return Microbot.getRs2NpcCache().query()
                .fromWorldView()
                .withNames(GENERAL_NAMES)
                .nearestOnClientThread();
    }

    private boolean hasQuestMaterialsInInventory() {
        if (allMailAccepted()) {
            return true;
        }

        return hasTurnInItemsInInventory() || hasMaterialsToCreateTurnInItems();
    }

    private boolean hasMaterialsToCreateTurnInItems() {
        return (blueMailAccepted || hasBlueGoblinMail() || hasBlueDye())
                && (orangeMailAccepted || hasOrangeGoblinMail() || hasOrangeDye())
                && Rs2Inventory.itemQuantity(GobReqs.GOBLIN_MAIL.getItemId()) >= getRequiredPlainMailCount();
    }

    private boolean hasTurnInItemsInInventory() {
        return (plainMailAccepted || hasPlainGoblinMail())
                && (blueMailAccepted || hasBlueGoblinMail())
                && (orangeMailAccepted || hasOrangeGoblinMail());
    }

    private int getRequiredPlainMailCount() {
        int required = plainMailAccepted ? 0 : 1;
        if (!blueMailAccepted && !hasBlueGoblinMail()) {
            required++;
        }
        if (!orangeMailAccepted && !hasOrangeGoblinMail()) {
            required++;
        }
        return required;
    }

    private boolean hasPlainGoblinMail() {
        return Rs2Inventory.itemQuantity(GobReqs.GOBLIN_MAIL.getItemId()) > 0;
    }

    private boolean hasBlueGoblinMail() {
        return Rs2Inventory.hasItem(BLUE_GOBLIN_MAIL);
    }

    private boolean hasOrangeGoblinMail() {
        return Rs2Inventory.hasItem(ORANGE_GOBLIN_MAIL);
    }

    private boolean hasBlueDye() {
        return Rs2Inventory.itemQuantity(GobReqs.BLUE_DYE.getItemId()) > 0;
    }

    private boolean hasOrangeDye() {
        return Rs2Inventory.itemQuantity(GobReqs.ORANGE_DYE.getItemId()) > 0;
    }

    private boolean isQuestComplete() {
        return Rs2Player.getQuestState(Quest.GOBLIN_DIPLOMACY) == QuestState.FINISHED;
    }

    private void snapshotMailCounts() {
        lastPlainMailCount = Rs2Inventory.itemQuantity(GobReqs.GOBLIN_MAIL.getItemId());
        lastBlueMailCount = Rs2Inventory.itemQuantity(BLUE_GOBLIN_MAIL);
        lastOrangeMailCount = Rs2Inventory.itemQuantity(ORANGE_GOBLIN_MAIL);
    }

    private void updateAcceptedMailFromInventoryLoss() {
        if (!mailHandInInProgress) {
            return;
        }

        int plainMailCount = Rs2Inventory.itemQuantity(GobReqs.GOBLIN_MAIL.getItemId());
        int blueMailCount = Rs2Inventory.itemQuantity(BLUE_GOBLIN_MAIL);
        int orangeMailCount = Rs2Inventory.itemQuantity(ORANGE_GOBLIN_MAIL);

        if (!plainMailAccepted && lastPlainMailCount > plainMailCount) {
            plainMailAccepted = true;
            requirementBankAudited = false;
            removeRequirementBuy(GOBLIN_MAIL);
            debug("Detected plain goblin mail hand-in | before={} after={}", lastPlainMailCount, plainMailCount);
        }

        if (!blueMailAccepted && lastBlueMailCount > blueMailCount) {
            blueMailAccepted = true;
            requirementBankAudited = false;
            removeRequirementBuy(BLUE_DYE);
            debug("Detected blue goblin mail hand-in | before={} after={}", lastBlueMailCount, blueMailCount);
        }

        if (!orangeMailAccepted && lastOrangeMailCount > orangeMailCount) {
            orangeMailAccepted = true;
            requirementBankAudited = false;
            removeRequirementBuy(ORANGE_DYE);
            debug("Detected orange goblin mail hand-in | before={} after={}", lastOrangeMailCount, orangeMailCount);
        }

        lastPlainMailCount = plainMailCount;
        lastBlueMailCount = blueMailCount;
        lastOrangeMailCount = orangeMailCount;

        if (!Rs2Dialogue.isInDialogue() && allMailAccepted()) {
            mailHandInInProgress = false;
        }
    }

    private void clearInactiveHandInTracking() {
        if (!mailHandInInProgress || Rs2Dialogue.isInDialogue()) {
            return;
        }

        updateAcceptedMailFromInventoryLoss();
        mailHandInInProgress = false;
    }

    private void removeRequirementBuy(String itemName) {
        removePendingRequirementBuy(itemName);
        removeActiveRequirementBuy(itemName);
    }

    private void removeActiveRequirementBuy(String itemName) {
        Iterator<QuestBuyRequest> iterator = activeRequirementBuys.iterator();

        while (iterator.hasNext()) {
            QuestBuyRequest request = iterator.next();

            if (request.matches(itemName)) {
                iterator.remove();
            }
        }
    }

    private boolean allMailAccepted() {
        return plainMailAccepted && blueMailAccepted && orangeMailAccepted;
    }

    private void resetHandInTracking() {
        plainMailAccepted = false;
        blueMailAccepted = false;
        orangeMailAccepted = false;
        mailHandInInProgress = false;
        lastPlainMailCount = 0;
        lastBlueMailCount = 0;
        lastOrangeMailCount = 0;
    }

    public boolean isComplete() {
        return complete || isQuestComplete();
    }

    public void setDebugLogging(boolean debugLogging) {
        this.debugLogging = debugLogging;
    }

    public GobState getState() {
        return state;
    }

    public String getStatus() {
        return status;
    }

    @Override
    public void shutdown() {
        KspWalkerGuard.clear(WALK_KEY_VILLAGE);
        KspWalkerGuard.clear(WALK_KEY_GENERAL);
        KspWalkerGuard.clear(WALK_KEY_GE);
        pendingRequirementBuys.clear();
        activeRequirementBuys.clear();
        requirementBankAudited = false;
        resetHandInTracking();
        lastActionAtMs = 0L;
        state = GobState.PREPARING;
        status = "Idle";
        super.shutdown();
    }

    private void debug(String message, Object... args) {
        if (debugLogging) {
            KspTaskDebug.info(log, true, "Goblin Diplomacy", message, args);
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
