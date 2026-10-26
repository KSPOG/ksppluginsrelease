package net.runelite.client.plugins.microbot.kspf2pcluesolver;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.plugins.cluescrolls.clues.emote.Emote;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeAction;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeRequest;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeSlots;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.grandexchange.models.WikiPrice;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

/**
 * Experimental beginner clue solver. All game interactions occur on a single
 * scheduled script thread; event callbacks only cache observations.
 * No automatic breaks or world hopping.
 */
public final class KspF2pClueScript extends Script
{
    private static final Logger log = LoggerFactory.getLogger(KspF2pClueScript.class);
    private static final int CLUE_ID = ItemID.TRAIL_CLUE_BEGINNER;
    private static final int COINS = ItemID.COINS;
    private static final long ACTION_GAP = 1_000L;
    private static final long READ_GAP = 6_000L;
    private static final WorldPoint GE = new WorldPoint(3164, 3487, 0);

    private volatile String status = "Idle";
    private volatile String clueName = "None";
    private volatile String neededItem = "-";
    private volatile BeginnerClueCatalog.Step step;
    private volatile long startedAt, completed, committedGp;
    private volatile boolean running;
    private volatile int pendingMapGroup = -1;
    private volatile boolean deviceFeedback, deviceShaking, progressMessage;

    private final Set<String> bankChecked = new HashSet<>();
    private KspF2pClueConfig config;
    private String lastStepKey;
    private long nextActionAt, nextReadAt, nextBankAt, offerPlacedAt;
    private int activePurchaseId = -1;
    private int hotColdAreaIndex, hotColdTileIndex;
    private long deviceRequestedAt;
    private int deviceAttempts;
    private boolean waitingForDevice;
    private int lastCasketCount;

    public boolean run(KspF2pClueConfig config)
    {
        this.config = config;
        status = "Waiting for beginner clue";
        startedAt = System.currentTimeMillis();
        completed = 0;
        committedGp = 0;
        nextActionAt = nextReadAt = nextBankAt = 0;
        step = null;
        lastStepKey = null;
        bankChecked.clear();
        activePurchaseId = -1;
        hotColdAreaIndex = hotColdTileIndex = 0;
        deviceRequestedAt = 0;
        deviceAttempts = 0;
        deviceFeedback = waitingForDevice = deviceShaking = false;
        progressMessage = false;
        running = true;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            if (!running || !Microbot.isLoggedIn()) return;
            try
            {
                if (super.run()) tick();
            }
            catch (Exception error)
            {
                status = "Error - see client log";
                log.error("KSP F2P clue solver action failed", error);
                delay(2_000);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    /** Called from RuneLite's event thread: do not interact with the game here. */
    public void onWidgetLoaded(int group)
    {
        if (BeginnerClueCatalog.fromMap(group) != null) pendingMapGroup = group;
        if (group == InterfaceID.TRAIL_CLUETEXT) nextReadAt = 0;
    }

    public void onChatMessage(String message)
    {
        if (message == null) return;
        String normalized = strip(message).toLowerCase(Locale.ROOT);
        if ((normalized.contains("the device") || normalized.contains("strange device"))
                && (normalized.contains("cold") || normalized.contains("warm")
                     || normalized.contains("hot") || normalized.contains("shaking")))
        {
            deviceFeedback = true;
            deviceShaking = normalized.contains("visibly shaking") || normalized.contains("shakes violently");
        }
        if (normalized.contains("you have found another clue")
                || normalized.contains("you find another clue")
                || normalized.contains("you have completed a clue"))
            progressMessage = true;
        // Charlie's assignment is often delivered through dialogue; listen
        // for chat mirrors as a backup to the live dialogue reader below.
        BeginnerClueCatalog.Step clue = BeginnerClueCatalog.fromText(normalized);
        if (clue != null && clue.kind == BeginnerClueCatalog.Kind.CHARLIE)
            acceptStep(clue);
    }

    private void tick()
    {
        long now = System.currentTimeMillis();
        if (now < nextActionAt) return;

        int caskets = Rs2Inventory.itemQuantity("Reward casket (beginner)");
        if (caskets > lastCasketCount) completed += caskets - lastCasketCount;
        lastCasketCount = caskets;

        if (progressMessage)
        {
            progressMessage = false;
            step = null;
            lastStepKey = null;
            bankChecked.clear();
            pendingMapGroup = -1;
            nextReadAt = 0;
        }

        if (!Rs2Inventory.hasItem(CLUE_ID))
        {
            if (Rs2Inventory.hasItem("Scroll box (beginner)"))
            {
                status = "Opening beginner scroll box";
                Rs2Inventory.interact("Scroll box (beginner)", "Open");
                delay(ACTION_GAP);
                return;
            }
            if (config.openCaskets() && Rs2Inventory.hasItem("Reward casket (beginner)"))
            {
                status = "Opening beginner reward";
                Rs2Inventory.interact("Reward casket (beginner)", "Open");
                delay(ACTION_GAP);
                return;
            }
            step = null;
            lastStepKey = null;
            activePurchaseId = -1;
            status = "Waiting for beginner clue";
            clueName = "None";
            neededItem = "-";
            return;
        }

        if (activePurchaseId > 0)
        {
            handlePendingPurchase();
            return;
        }

        if (pendingMapGroup != -1)
        {
            int group = pendingMapGroup;
            pendingMapGroup = -1;
            acceptStep(BeginnerClueCatalog.fromMap(group));
        }

        readClueTextIfOpen();
        readCharlieAssignment();

        if (step != null && now >= nextReadAt
                && !Rs2Dialogue.isInDialogue() && !Rs2Bank.isOpen()
                && !Rs2GrandExchange.isOpen())
        {
            status = "Checking clue progress";
            Rs2Inventory.interact(CLUE_ID, "Read");
            nextReadAt = now + READ_GAP;
            delay(ACTION_GAP);
            return;
        }
        if (step == null)
        {
            status = "Reading clue scroll";
            if (now >= nextReadAt)
            {
                if (Rs2Bank.isOpen()) Rs2Bank.closeBank();
                else Rs2Inventory.interact(CLUE_ID, "Read");
                nextReadAt = now + READ_GAP;
                delay(ACTION_GAP);
            }
            return;
        }

        for (String name : step.requiredItems)
        {
            if (!hasNeededItem(name, step.kind == BeginnerClueCatalog.Kind.EMOTE))
            {
                neededItem = name;
                acquireItem(name);
                return;
            }
        }
        neededItem = "Ready";

        if (Rs2Bank.isOpen()) { Rs2Bank.closeBank(); delay(ACTION_GAP); return; }
        if (Rs2GrandExchange.isOpen())
        {
            status = "Closing Grand Exchange";
            Rs2GrandExchange.closeExchange();
            delay(ACTION_GAP);
            return;
        }
        if (Rs2Dialogue.hasContinue())
        {
            status = "Continuing dialogue";
            Rs2Dialogue.clickContinue();
            delay(ACTION_GAP);
            return;
        }
        if (step.kind == BeginnerClueCatalog.Kind.CHARLIE && Rs2Dialogue.isInDialogue())
        {
            status = "Handing item to Charlie";
            Rs2Dialogue.clickOption("Yes", "Here you go", "I have it", "Give");
            delay(ACTION_GAP);
            return;
        }

        switch (step.kind)
        {
            case NPC:
            case CHARLIE: handleNpc(); break;
            case EMOTE: handleEmote(); break;
            case DIG: handleDig(step.destination); break;
            case HOT_COLD: handleHotCold(); break;
            default: status = "Unsupported clue"; break;
        }
    }

    private void readClueTextIfOpen()
    {
        String text = Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Widget w = Microbot.getClient().getWidget(InterfaceID.TrailCluetext.TEXT);
            return w == null || w.isHidden() ? "" : w.getText();
        }).orElse("");
        BeginnerClueCatalog.Step found = BeginnerClueCatalog.fromText(text);
        if (found != null) acceptStep(found);
    }

    private void readCharlieAssignment()
    {
        if (!Rs2Dialogue.isInDialogue()) return;
        String text = Rs2Dialogue.getDialogueText();
        BeginnerClueCatalog.Step found = BeginnerClueCatalog.fromText(text);
        if (found != null && found.kind == BeginnerClueCatalog.Kind.CHARLIE)
            acceptStep(found);
    }

    private void acceptStep(BeginnerClueCatalog.Step candidate)
    {
        if (candidate == null || candidate.key.equals(lastStepKey)) return;
        step = candidate;
        lastStepKey = candidate.key;
        clueName = candidate.label;
        bankChecked.clear();
        hotColdAreaIndex = hotColdTileIndex = 0;
        deviceRequestedAt = 0;
        deviceAttempts = 0;
        deviceFeedback = deviceShaking = waitingForDevice = false;
        nextReadAt = System.currentTimeMillis() + READ_GAP;
        status = "Identified: " + candidate.label;
    }

    private boolean hasNeededItem(String name, boolean equip)
    {
        if (equip) return Rs2Equipment.isWearing(name);
        return Rs2Inventory.hasItem(name) || Rs2Equipment.isWearing(name);
    }

    private void acquireItem(String name)
    {
        if (Rs2Inventory.hasItem(name) && step.kind == BeginnerClueCatalog.Kind.EMOTE)
        {
            status = "Equipping " + name;
            boolean clicked = Rs2Inventory.interact(name, "Wear");
            if (!clicked) clicked = Rs2Inventory.interact(name, "Wield");
            if (!clicked) status = "Cannot equip: " + name;
            delay(ACTION_GAP);
            return;
        }

        if (!bankChecked.contains(name))
        {
            if (!Rs2Bank.isOpen())
            {
                if (System.currentTimeMillis() < nextBankAt) return;
                status = "Checking bank for " + name;
                Rs2Bank.walkToBankAndUseBank();
                nextBankAt = System.currentTimeMillis() + 2_000;
                return;
            }
            Rs2Bank.setWithdrawAsItem();
            if (Rs2Bank.hasItem(name))
            {
                status = "Withdrawing " + name;
                Rs2Bank.withdrawOne(name);
                delay(ACTION_GAP);
                return;
            }
            bankChecked.add(name);
            // Fetch coins only if there is something missing and we can buy it.
            if (config.autoBuy() && Rs2Bank.hasItem(COINS)
                    && Rs2Inventory.itemQuantity(COINS) < config.maxSessionSpend())
            {
                status = "Withdrawing GE spending coins";
                Rs2Bank.withdrawAll(COINS);
                delay(ACTION_GAP);
                return;
            }
        }

        if (!config.autoBuy())
        {
            status = "Missing " + name + " (auto-buy disabled)";
            return;
        }
        if (Rs2Bank.isOpen())
        {
            Rs2Bank.closeBank();
            delay(ACTION_GAP);
            return;
        }
        placeOrCollectBuy(name);
    }

    private void placeOrCollectBuy(String name)
    {
        int id = itemId(name);
        if (id <= 0) { status = "No approved item ID for " + name; return; }

        if (!Rs2GrandExchange.isOpen())
        {
            status = "Walking to Grand Exchange";
            WorldPoint here = Rs2Player.getWorldLocation();
            if (here == null || here.distanceTo2D(GE) > 12)
            {
                Rs2GrandExchange.walkToGrandExchange();
                delay(1_500);
                return;
            }
            Rs2GrandExchange.openExchange();
            delay(ACTION_GAP);
            return;
        }
        if (Rs2GrandExchange.isOfferScreenOpen())
        {
            Rs2GrandExchange.backToOverview();
            delay(ACTION_GAP);
            return;
        }
        GrandExchangeSlots existing = Rs2GrandExchange.findSlotForItem(id, false);
        if (existing != null)
        {
            activePurchaseId = id;
            offerPlacedAt = System.currentTimeMillis();
            handlePendingPurchase();
            return;
        }

        int remaining = config.maxSessionSpend() - (int) Math.min(Integer.MAX_VALUE, committedGp);
        WikiPrice wiki = Rs2GrandExchange.getRealTimePrices(id);
        if (wiki == null || wiki.buyPrice <= 0)
        {
            status = "No verified market quote: " + name;
            return;
        }
        long estimate = (long) Math.ceil(wiki.buyPrice * (1.0 + config.pricePremium() / 100.0));
        if (estimate <= 0 || estimate > config.maxUnitPrice() || estimate > remaining)
        {
            status = "GE purchase exceeds spending cap: " + name;
            return;
        }
        int offerPrice = (int) estimate;
        if (Rs2Inventory.itemQuantity(COINS) < offerPrice)
        {
            status = "Insufficient coins for " + name;
            return;
        }
        GrandExchangeSlots slot = findFreeF2pSlot();
        if (slot == null) { status = "All 3 F2P GE slots occupied"; return; }

        status = "Buying " + name + " for " + offerPrice + " GP";
        GrandExchangeRequest request = GrandExchangeRequest.builder()
            .slot(slot).action(GrandExchangeAction.BUY).itemName(name).exact(true)
            .quantity(1).price(offerPrice).closeAfterCompletion(false).build();
        if (Rs2GrandExchange.processOffer(request))
        {
            // Conservative cap: committed offers are treated as spent even
            // if a partial fill later returns coins.
            committedGp += offerPrice;
            activePurchaseId = id;
            offerPlacedAt = System.currentTimeMillis();
            delay(2_000);
        }
        else
        {
            status = "GE offer placement failed: " + name;
            delay(2_000);
        }
    }

    private GrandExchangeSlots findFreeF2pSlot()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            for (int i = 0; i < 3 && i < GrandExchangeSlots.values().length; i++)
            {
                GrandExchangeOffer offer = offers != null && i < offers.length ? offers[i] : null;
                if (offer == null || offer.getState() == GrandExchangeOfferState.EMPTY)
                    return GrandExchangeSlots.values()[i];
            }
            return (GrandExchangeSlots) null;
        }).orElse(null);
    }

    private void handlePendingPurchase()
    {
        if (activePurchaseId <= 0) return;
        if (Rs2Inventory.hasItem(activePurchaseId))
        {
            status = "GE item received";
            activePurchaseId = -1;
            delay(ACTION_GAP);
            return;
        }
        if (!Rs2GrandExchange.isOpen())
        {
            status = "Reopening GE for collection";
            Rs2GrandExchange.openExchange();
            delay(1_500);
            return;
        }
        if (Rs2GrandExchange.isOfferScreenOpen())
        {
            Rs2GrandExchange.backToOverview();
            delay(ACTION_GAP);
            return;
        }
        GrandExchangeSlots slot = Rs2GrandExchange.findSlotForItem(activePurchaseId, false);
        if (slot == null)
        {
            status = "GE slot missing: inspect offers manually";
            return; // Never place a duplicate automatically.
        }
        Boolean bought = Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            GrandExchangeOffer[] all = Microbot.getClient().getGrandExchangeOffers();
            int index = slot.ordinal();
            return all != null && index < all.length && all[index] != null
                && all[index].getState() == GrandExchangeOfferState.BOUGHT;
        }).orElse(false);
        if (Boolean.TRUE.equals(bought))
        {
            status = "Collecting GE purchase";
            Rs2GrandExchange.collectAllToInventory();
            delay(1_500);
        }
        else if (System.currentTimeMillis() - offerPlacedAt > TimeUnit.MINUTES.toMillis(3))
            status = "GE offer pending: not cancelling automatically";
        else
            status = "Waiting for GE buy";
    }

    private void handleNpc()
    {
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null) return;
        if (player.getPlane() != step.destination.getPlane()
                || player.distanceTo2D(step.destination) > step.radius)
        {
            status = "Walking: " + step.label;
            if (!Rs2Player.isMoving()) Rs2Walker.walkTo(step.destination, step.radius);
            delay(1_500);
            return;
        }
        if (Rs2Dialogue.isInDialogue())
        {
            status = "Waiting for dialogue to advance";
            Rs2Dialogue.clickOption("Yes", "Here you go", "I have it", "Give");
            delay(ACTION_GAP);
            return;
        }
        status = step.label;
        if (Rs2Npc.getNpc(step.npc, true) != null)
            Rs2Npc.interact(Rs2Npc.getNpc(step.npc, true), "Talk-to");
        delay(2_000);
        nextReadAt = 0;
    }

    private void handleDig(WorldPoint spot)
    {
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null) return;
        if (!spot.equals(player))
        {
            status = "Walking to dig tile";
            if (!Rs2Player.isMoving()) Rs2Walker.walkTo(spot, 0);
            delay(1_500);
            return;
        }
        status = "Digging: " + clueName;
        Rs2Inventory.interact("Spade", "Dig");
        delay(2_000);
        nextReadAt = 0;
    }

    private void handleEmote()
    {
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null) return;
        if (player.getPlane() != step.destination.getPlane()
                || player.distanceTo2D(step.destination) > step.radius)
        {
            status = "Walking to emote location";
            if (!Rs2Player.isMoving()) Rs2Walker.walkTo(step.destination, step.radius);
            delay(1_500);
            return;
        }
        if (Rs2Npc.getNpc("Uri", true) != null)
        {
            status = "Talking to Uri";
            Rs2Npc.interact(Rs2Npc.getNpc("Uri", true), "Talk-to");
            nextReadAt = 0;
            delay(2_000);
            return;
        }
        status = "Performing " + step.emote;
        if (Rs2Tab.getCurrentTab() != InterfaceTab.EMOTES)
        {
            Rs2Tab.switchToEmotesTab();
            delay(ACTION_GAP);
            return;
        }
        Emote emote = Emote.valueOf(step.emote);
        Widget target = Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Widget container = Microbot.getClient().getWidget(WidgetInfo.EMOTE_CONTAINER);
            return findEmoteWidget(container, emote);
        }).orElse(null);
        if (target == null)
        {
            status = "Emote widget not visible: " + step.emote;
            return;
        }
        if (Rs2Widget.clickWidget(target)) delay(2_000);
        else
        {
            status = "Unable to click emote";
            delay(ACTION_GAP);
        }
    }

    /** Find emotes through widget label or sprite; never guess widget indices. */
    private Widget findEmoteWidget(Widget w, Emote emote)
    {
        if (w == null || w.isHidden()) return null;
        String expected = emote.getName();
        boolean labelled = expected.equalsIgnoreCase(strip(w.getName()))
                        || expected.equalsIgnoreCase(strip(w.getText()));
        Widget[] children = w.getChildren();
        if (children != null)
            for (Widget c : children)
            {
                Widget result = findEmoteWidget(c, emote);
                if (result != null) return result;
            }
        return (labelled || (emote.hasSprite() && w.getSpriteId() >= 0
                && w.getSpriteId() == emote.getSpriteId())) ? w : null;
    }

    private static String strip(String s)
    { return s == null ? "" : s.replaceAll("<[^>]*>", "").trim(); }

    /**
     * Experimental 5-location device search. Only dig after explicit device
     * shaking. Stop with a visible status on inconclusive responses.
     */
    private void handleHotCold()
    {
        if (!Rs2Inventory.hasItem("Strange device"))
        {
            WorldPoint reldo = step.destination;
            WorldPoint player = Rs2Player.getWorldLocation();
            if (player == null) return;
            if (player.getPlane() != reldo.getPlane() || player.distanceTo2D(reldo) > 4)
            {
                status = "Walking to Reldo for device";
                if (!Rs2Player.isMoving()) Rs2Walker.walkTo(reldo, 4);
            }
            else
            {
                status = "Requesting strange device from Reldo";
                if (Rs2Dialogue.isInDialogue())
                    Rs2Dialogue.clickOption("Strange device", "Hot and cold", "Yes");
                else if (Rs2Npc.getNpc("Reldo", true) != null)
                    Rs2Npc.interact(Rs2Npc.getNpc("Reldo", true), "Talk-to");
            }
            delay(2_000);
            return;
        }
        if (hotColdAreaIndex >= BeginnerClueCatalog.HOT_COLD_CENTERS.length)
        {
            status = "Device clues inconclusive: manual review needed";
            return;
        }
        WorldPoint center = BeginnerClueCatalog.HOT_COLD_CENTERS[hotColdAreaIndex];
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null) return;
        if (!deviceShaking)
        {
            if (player.distanceTo2D(center) > 0)
            {
                status = "Probing hot/cold location " + (hotColdAreaIndex+1) + "/5";
                if (!Rs2Player.isMoving()) Rs2Walker.walkTo(center, 0);
                delay(1_500);
                return;
            }
            if (deviceFeedback)
            {
                deviceFeedback = false;
                waitingForDevice = false;
                deviceAttempts = 0;
                hotColdAreaIndex++;
                return;
            }
            if (waitingForDevice && System.currentTimeMillis() - deviceRequestedAt > 7_000L)
                waitingForDevice = false;
            if (!waitingForDevice)
            {
                if (++deviceAttempts > 3)
                {
                    status = "No device feedback; manual review needed";
                    hotColdAreaIndex = BeginnerClueCatalog.HOT_COLD_CENTERS.length;
                    return;
                }
                waitingForDevice = true;
                deviceRequestedAt = System.currentTimeMillis();
                status = "Feeling strange device";
                if (!Rs2Inventory.interact("Strange device", "Feel"))
                {
                    status = "Device interaction unsupported; manual review";
                    hotColdAreaIndex = BeginnerClueCatalog.HOT_COLD_CENTERS.length;
                }
                delay(2_000);
                return;
            }
            status = "Awaiting hot/cold device reading";
            return;
        }
        if (hotColdTileIndex >= 81)
        {
            status = "9x9 hot/cold area searched; manual review";
            return;
        }
        int dx = (hotColdTileIndex % 9) - 4;
        int dy = (hotColdTileIndex / 9) - 4;
        WorldPoint guess = new WorldPoint(center.getX()+dx, center.getY()+dy, 0);
        if (!guess.equals(player))
        {
            status = "Checking hot/cold dig tile " + (hotColdTileIndex+1) + "/81";
            if (!Rs2Player.isMoving()) Rs2Walker.walkTo(guess, 0);
            delay(1_500);
            return;
        }
        if (Rs2Inventory.interact("Spade", "Dig"))
        {
            hotColdTileIndex++;
            delay(2_000);
        }
        else status = "Cannot dig at device location";
    }

    private static int itemId(String name)
    {
        switch (name.toLowerCase(Locale.ROOT))
        {
            case "spade": return ItemID.SPADE;
            case "bronze axe": return ItemID.BRONZE_AXE;
            case "chef's hat": return ItemID.CHEFS_HAT;
            case "gold necklace": return ItemID.GOLD_NECKLACE;
            case "gold ring": return ItemID.GOLD_RING;
            case "leather boots": return ItemID.LEATHER_BOOTS;
            case "red cape": return ItemID.RED_CAPE;
            case "pike": return ItemID.PIKE;
            case "trout": return ItemID.TROUT;
            case "leather body": return ItemID.LEATHER_ARMOUR;
            case "leather chaps": return ItemID.LEATHER_CHAPS;
            case "raw herring": return ItemID.RAW_HERRING;
            case "raw trout": return ItemID.RAW_TROUT;
            case "iron ore": return ItemID.IRON_ORE;
            case "iron dagger": return ItemID.IRON_DAGGER;
            default: return -1;
        }
    }

    private void delay(long ms) { nextActionAt = System.currentTimeMillis() + ms; }

    @Override public void shutdown()
    {
        running = false;
        super.shutdown();
        status = "Stopped";
        step = null;
        activePurchaseId = -1;
        bankChecked.clear();
    }

    public String getStatus() { return status; }
    public String getClueName() { return clueName; }
    public String getNeededItem() { return neededItem; }
    public long getCommittedGp() { return committedGp; }
    public long getCompleted() { return completed; }
    public long getRuntimeMs() { return startedAt <= 0 ? 0 : Math.max(0, System.currentTimeMillis() - startedAt); }
}