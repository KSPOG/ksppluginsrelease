package net.runelite.client.plugins.microbot.kspseasonalevents;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.widgets.Widget;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import java.awt.event.KeyEvent;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Old School RuneScape's 2026 "All Trick, No Treats" event.
 *
 * Walkthrough: https://oldschool.runescape.wiki/w/2026_Halloween_event
 * Progress is driven by visible scene objects, inventory transitions and
 * NPC actions, not by presumed item/object IDs or a fixed stream of clicks.
 *
 * The wiki documents the sequence but not all menu strings, runtime IDs and
 * quest varbits. Unrecognised UI is logged and awaits review instead of
 * guessing an action. This class is independent of KSP Chopper.
 */
@Slf4j
@Singleton
public final class KspHalloween2026Script extends Script {
    private static final String GROUP = "KspSeasonalEvents";
    private static final String STAGE_KEY = "halloweenStage";
    private static final String LANTERNS_KEY = "halloweenSmashTiles";
    private static final String PENDING_LANTERN_KEY = "halloweenPendingSmashTile";
    private static final String LANTERNS_VERIFIED_KEY = "halloweenLanternsVerified";
    private static final String ADULTS_KEY = "halloweenAdultsGiven";
    private static final String SPOOKED_CHILD_IDS_KEY = "halloweenChildNpcIdsAttempted";
    private static final String CHILD_RETRY_COUNT_KEY = "halloweenChildReportRetries";
    private static final String CHILD_CONFIRMED_KEY = "halloweenChildObjectiveConfirmed";
    private static final String DELIVERED_ADULT_IDS_KEY = "halloweenAdultNpcIdsDelivered";
    // Five NPCs per candidate age group were seen in the 2026 log.
    // Five verified child attempts trigger a report checkpoint, not success.
    private static final int CHILD_REPORT_CHECKPOINT = 5;
    private static final int MAX_CHILD_NPC_SCOUT_STOPS = 4;
    private static final long CHILD_SCENE_SETTLE_MS = 2_000L;
    private static final String TREATER_CHILD_GROUP_KEY = "halloweenConfirmedChildNpcGroup";
    // 2026 client logs show two groups of five NPC definition IDs. The
    // groups are NOT assigned to children or adults without live evidence.
    private static final int TREATER_GROUP_A_START = 16778;
    private static final int TREATER_GROUP_A_END = 16782;
    private static final int TREATER_GROUP_B_START = 16794;
    private static final int TREATER_GROUP_B_END = 16798;
    private enum TreaterAge { CHILD, ADULT, UNKNOWN }
    private enum TreaterGroup { A, B, UNKNOWN }
    private static final WorldArea RIMMINGTON = new WorldArea(2925, 3180, 85, 95, 0);
    private static final WorldPoint STARTER = new WorldPoint(2958, 3211, 0);
    private static final WorldPoint DIANGO = new WorldPoint(3081, 3248, 0);
    private static final WorldPoint CHEMIST = new WorldPoint(2930, 3211, 0);

    // Discovery waypoints, NOT claimed spawn coordinates. The real object
    // must be present in the live cache and expose the required menu action.
    private static final WorldPoint[] RIMMINGTON_SWEEP = {
        new WorldPoint(2957, 3212, 0), new WorldPoint(2969, 3218, 0),
        new WorldPoint(2984, 3229, 0), new WorldPoint(2994, 3212, 0),
        new WorldPoint(2970, 3193, 0), new WorldPoint(2948, 3198, 0),
        new WorldPoint(2941, 3230, 0), new WorldPoint(2970, 3250, 0)
    };
    private static final long ACTION_GAP = 1_400L;
    private static final long WALK_RETRY = 10_000L;
    // Prefer a complete outfit using distinct equipment slots.
    // Match each item against live RuneLite item composition names.
    private static final List<String> DIANGO_COSTUME_PRIORITY = Arrays.asList(
        "skeleton boots", "skeleton gloves", "skeleton leggings", "skeleton shirt",
        "skeleton mask", "spooky boots", "spooky gloves", "spooky skirt",
        "spooky robe", "spooky hood", "spookier boots", "spookier gloves",
        "spookier skirt", "spookier robe", "spookier hood",
        "grim reaper gloves", "grim reaper bottoms", "grim reaper top",
        "grim reaper hood", "gravedigger boots", "gravedigger gloves",
        "gravedigger leggings", "gravedigger top", "gravedigger mask",
        "witch boots", "witch robes", "witch top", "witch hat",
        "jack lantern mask", "halloween wig", "ugly halloween jumper (orange)"
    );
    private static final String[] COSTUME_PIECES = {
        "Skeleton mask", "Skeleton shirt", "Skeleton leggings", "Skeleton boots",
        "Skeleton gloves", "Grim reaper hood", "Grim reaper top",
        "Grim reaper bottoms", "Grim reaper gloves",
        "Zombie head", "Zombie shirt", "Zombie trousers",
        "Zombie boots", "Zombie gloves", "Jack lantern mask",
        "Black h'ween mask", "Red halloween mask", "Blue halloween mask",
        "Green halloween mask", "Ghostly hood", "Ghostly robe",
        "Spooky boots", "Spooky gloves", "Spooky skirt", "Spooky robe", "Spooky hood",
        "Spookier boots", "Spookier gloves", "Spookier skirt", "Spookier robe",
        "Spookier hood", "Gravedigger boots", "Gravedigger gloves",
        "Gravedigger leggings", "Gravedigger top", "Gravedigger mask",
        "Witch boots", "Witch robes", "Witch top", "Witch hat",
        "Halloween wig", "Ugly halloween jumper (orange)"
    };

    public enum Stage {
        STOPPED,
        INTRODUCTION,
        SMASH_LANTERNS,
        VERIFY_LANTERNS,
        REPORT_LANTERNS,
        GET_COSTUME,
        SPOOK_CHILDREN,
        REPORT_CHILDREN,
        CHEMIST_RIDDLE,
        COOK_SUGAR,
        COLLECT_ONIONS,
        COAT_ONIONS,
        GIVE_CANDY,
        FINAL_TALK,
        WAIT_FOR_REWARD,
        COMPLETED,
        NEEDS_REVIEW,
        PAUSED
    }

    @Inject private ConfigManager configManager;
    private volatile KspSeasonalEventsConfig config;
    private volatile Stage stage = Stage.STOPPED;
    private volatile Stage beforePause = Stage.STOPPED;
    private volatile String status = "Stopped - press Start";
    private volatile boolean active;
    private volatile boolean paused;
    private volatile long startedAt;

    private final Set<String> smashedLanternTiles = ConcurrentHashMap.newKeySet();
    private volatile boolean lanternsVerified;
    // Scheduler writes, while Swing panel and overlay read the attempt count.
    private final Set<Integer> spookedChildren = ConcurrentHashMap.newKeySet();
    private final Set<Integer> givenAdultIds = new HashSet<>();
    private volatile boolean childrenReportConfirmed;
    private int childrenReportAttempts;
    private int childSpookRetries;
    private final Map<Integer, Integer> observedTreaterHeights = new HashMap<>();
    private TreaterGroup confirmedChildGroup = TreaterGroup.UNKNOWN;
    private long lastTreaterClassificationLogAt;
    // Track only live-event-NPC-derived scout tiles. The generic Rimmington
    // lantern search waypoints are never used to locate child NPCs.
    private final Set<String> childScoutedNpcTiles = new HashSet<>();
    private long childNoTargetSince;
    private int adultsGiven;
    private int sweepIndex;
    private int sweepsWithoutTarget;
    private int actionsAtStage;
    private long lastActionAt;
    private long lastWalkAt;
    private long lastTalkAt;
    private long lastDiagnosticAt;
    private volatile WorldPoint pendingLantern;
    private long pendingLanternAt;
    private long lastVerificationTalkAt;
    private boolean sweepRequiredByStarter;
    private int starterReportedRemainingCount;
    private int pendingCandyCount = -1;
    private int pendingAdultId = -1;
    private String pendingHolidayItem;
    private long pendingHolidayItemAt;
    private int holidayRetrievalTimeouts;
    private int holidayScrollAttempts;
    private int holidayCloseAttempts;
    // A starter conversation is not complete just because the NPC was clicked.
    private Stage pendingStarterNext;
    private long pendingStarterAt;
    private long pendingStarterLastDialogueAt;
    private boolean pendingStarterDialogueSeen;
    private boolean starterConversationActive;
    private String lastStarterDialogue = "";
    // Track the visible Chemist option menu until it actually changes.
    // Alternating widget clicks and option keybindings avoids silent keybind failures.
    private String pendingChemistOptions = "";
    private long pendingChemistOptionAt;
    private int pendingChemistOptionAttempts;

    public synchronized boolean start(KspSeasonalEventsConfig configuration) {
        if (active) return true;
        this.config = configuration;
        this.active = true;
        this.paused = false;
        this.startedAt = System.currentTimeMillis();
        lastActionAt = lastWalkAt = lastTalkAt = lastDiagnosticAt = 0L;
        pendingLantern = null;
        pendingLanternAt = 0L;
        lastVerificationTalkAt = 0L;
        sweepRequiredByStarter = false;
        starterReportedRemainingCount = 0;
        pendingCandyCount = -1;
        pendingStarterNext = null;
        pendingStarterAt = 0L;
        pendingStarterLastDialogueAt = 0L;
        pendingStarterDialogueSeen = false;
        starterConversationActive = false;
        lastStarterDialogue = "";
        clearChemistOptionPending();
        pendingHolidayItem = null;
        pendingHolidayItemAt = 0L;
        holidayRetrievalTimeouts = 0;
        holidayScrollAttempts = 0;
        holidayCloseAttempts = 0;
        spookedChildren.clear();
        givenAdultIds.clear();
        childrenReportConfirmed = false;
        childrenReportAttempts = 0;
        childSpookRetries = 0;
        observedTreaterHeights.clear();
        confirmedChildGroup = TreaterGroup.UNKNOWN;
        lastTreaterClassificationLogAt = 0L;
        childScoutedNpcTiles.clear();
        childNoTargetSince = 0L;
        sweepIndex = 0;
        sweepsWithoutTarget = 0;
        actionsAtStage = 0;

        smashedLanternTiles.clear();
        String savedTiles = configManager.getConfiguration(GROUP, LANTERNS_KEY);
        if (savedTiles != null && !savedTiles.isBlank())
            smashedLanternTiles.addAll(Arrays.asList(savedTiles.split(",")));
        lanternsVerified = Boolean.parseBoolean(
                configManager.getConfiguration(GROUP, LANTERNS_VERIFIED_KEY));
        pendingLantern = parseLanternTile(configManager.getConfiguration(GROUP, PENDING_LANTERN_KEY));
        // A click recorded before a pause/restart must be checked on location,
        // not forgotten; there is no guaranteed game-level counter in this API.
        pendingLanternAt = 0L;
        String savedAdults = configManager.getConfiguration(GROUP, ADULTS_KEY);
        try { adultsGiven = savedAdults == null ? 0 : Math.max(0, Math.min(5, Integer.parseInt(savedAdults))); }
        catch (NumberFormatException e) { adultsGiven = 0; }
        restoreNpcIds(SPOOKED_CHILD_IDS_KEY, spookedChildren);
        restoreNpcIds(DELIVERED_ADULT_IDS_KEY, givenAdultIds);
        childrenReportConfirmed = Boolean.parseBoolean(
                configManager.getConfiguration(GROUP, CHILD_CONFIRMED_KEY));
        String savedRetries = configManager.getConfiguration(GROUP, CHILD_RETRY_COUNT_KEY);
        try {
            childSpookRetries = savedRetries == null ? 0
                    : Math.max(0, Math.min(2, Integer.parseInt(savedRetries)));
        } catch (NumberFormatException ignored) {
            childSpookRetries = 0;
        }
        String childGroupConfig = configManager.getConfiguration(GROUP, TREATER_CHILD_GROUP_KEY);
        if ("A".equals(childGroupConfig)) confirmedChildGroup = TreaterGroup.A;
        else if ("B".equals(childGroupConfig)) confirmedChildGroup = TreaterGroup.B;
        log.info("Halloween saved NPC progress | attemptedChildren={} adultIds={} delivered={}",
                spookedChildren.size(), givenAdultIds.size(), adultsGiven);

        Stage saved = savedStage();
        if (saved == Stage.COMPLETED) {
            change(Stage.COMPLETED, "Previously confirmed Halloween completion");
        } else {
            // Saved stages and local counters are recovery hints only.
            // Every non-completed start synchronizes with Angel/Henja so the
            // server's dialogue decides the current event objective.
            change(Stage.INTRODUCTION,
                    "Synchronizing Halloween event state with Angel / Henja");
            log.info("Halloween saved stage {} deferred to starter dialogue", saved);
        }
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(
                this::safeTick, 0L, 600L, TimeUnit.MILLISECONDS);
        log.info("Halloween 2026 started | stage={} lanterns={}/8 adultsGiven={}",
                stage, getLanternCount(), adultsGiven);
        return true;
    }

    private void restoreNpcIds(String key, Set<Integer> destination) {
        String raw = configManager.getConfiguration(GROUP, key);
        if (raw == null || raw.isBlank()) return;
        for (String part : raw.split(",")) {
            try {
                int id = Integer.parseInt(part.trim());
                if (id > 0) destination.add(id);
            } catch (NumberFormatException ignored) {
                log.warn("Invalid saved Halloween NPC ID ignored | key={} value={}", key, part);
            }
        }
    }

    private void persistNpcIds(String key, Set<Integer> ids) {
        configManager.setConfiguration(GROUP, key,
                ids.stream().sorted().map(String::valueOf).collect(Collectors.joining(",")));
    }

    private Stage savedStage() {
        String value = configManager.getConfiguration(GROUP, STAGE_KEY);
        if (value != null) {
            try { return Stage.valueOf(value); }
            catch (IllegalArgumentException ignored) { /* old version or corrupt config */ }
        }
        return Stage.INTRODUCTION;
    }

    private void safeTick() {
        if (!active || paused) return;
        try { tick(); }
        catch (Exception e) {
            log.warn("Halloween stage={} player={} failed", stage, Rs2Player.getWorldLocation(), e);
            stopForReview("Event API or interface changed; inspect client logs");
        }
    }

    private void tick() {
        if (!Microbot.isLoggedIn()) { status = "Waiting for login"; return; }
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null) { status = "Waiting for player location"; return; }
        if (stage == Stage.COMPLETED || stage == Stage.NEEDS_REVIEW) return;

        if (starterConversationActive
                && (Rs2Dialogue.hasContinue() || Rs2Dialogue.hasSelectAnOption())) {
            pendingStarterDialogueSeen = true;
            pendingStarterLastDialogueAt = System.currentTimeMillis();
            inspectStarterDialogue();
            if (stage == Stage.NEEDS_REVIEW || stage == Stage.COMPLETED) return;
        }

        if (Rs2Dialogue.hasContinue()) {
            if (stage == Stage.CHEMIST_RIDDLE) clearChemistOptionPending();
            if (ready()) {
                Rs2Dialogue.clickContinue();
                lastActionAt = System.currentTimeMillis();
                status = "Advancing " + stage + " dialogue";
            }
            return;
        }

        if (Rs2Dialogue.hasSelectAnOption()) {
            handleDialogueOption();
            return;
        }

        // Angel/Henja are authoritative for the event objective. Never
        // resume a preselected stage just because their dialogue closed.
        if (starterConversationActive) {
            if (pendingStarterDialogueSeen) {
                if (System.currentTimeMillis() - pendingStarterLastDialogueAt < 1_500L) {
                    status = "Finishing Angel / Henja conversation";
                    return;
                }
                if (pendingStarterNext == null) {
                    starterConversationActive = false;
                    stopForReview("Angel/Henja dialogue did not identify a known Halloween objective;"
                            + " lastDialogue=" + lastStarterDialogue);
                    return;
                }
                Stage next = pendingStarterNext;
                pendingStarterNext = null;
                pendingStarterDialogueSeen = false;
                starterConversationActive = false;
                change(next, "Angel / Henja dialogue resolved event state");
            } else if (System.currentTimeMillis() - pendingStarterAt > 8_000L) {
                log.warn("Halloween starter click produced no visible dialogue | stage={}", stage);
                starterConversationActive = false;
                pendingStarterNext = null;
                pendingStarterDialogueSeen = false;
                lastActionAt = 0L; // permit a clean Talk-to retry
            } else {
                status = "Waiting for Angel / Henja dialogue";
            }
            return;
        }

        switch (stage) {
            case INTRODUCTION:
                talkToStarter(player);
                break;
            case SMASH_LANTERNS:
                smashLanterns(player);
                break;
            case VERIFY_LANTERNS:
                talkToStarter(player);
                break;
            case REPORT_LANTERNS:
                talkToStarter(player);
                break;
            case GET_COSTUME:
                collectCostume(player);
                break;
            case SPOOK_CHILDREN:
                spookChildren(player);
                break;
            case REPORT_CHILDREN:
                talkToStarter(player);
                break;
            case CHEMIST_RIDDLE:
                solveChemist(player);
                break;
            case COOK_SUGAR:
                cookSugar(player);
                break;
            case COLLECT_ONIONS:
                collectOnions(player);
                break;
            case COAT_ONIONS:
                coatOnions();
                break;
            case GIVE_CANDY:
                giveCandy(player);
                break;
            case FINAL_TALK:
                talkToStarter(player);
                break;
            case WAIT_FOR_REWARD:
                // A reward item can prove something was granted, but it must
                // not become the event-state oracle. If completion chat was
                // missed, ask Angel/Henja again and resolve their dialogue.
                if (Rs2Inventory.hasItem("Vampyre costume top")
                        || Rs2Equipment.isWearing("Vampyre costume top", true)) {
                    change(Stage.FINAL_TALK,
                            "Vampyre reward observed; rechecking final event state with Angel/Henja");
                } else {
                    status = "Waiting for final reward/completion message from Angel / Henja";
                }
                break;
            default:
                break;
        }
    }

    private void handleDialogueOption() {
        if (!ready()) return;
        // Enumerate unknown choices for accurate follow-up debugging.
        List<String> options = Rs2Dialogue.getDialogueOptions().stream()
                .map(w -> w == null ? "" : w.getText())
                .collect(Collectors.toList());
        if (stage == Stage.GET_COSTUME
                && Rs2Dialogue.keyPressForDialogueOption(
                        "I'd like to check holiday items please!", true)) {
            acted("Selected Diango's holiday-item retrieval dialogue");
            return;
        }

        if ((stage == Stage.REPORT_LANTERNS || stage == Stage.GET_COSTUME
                || stage == Stage.VERIFY_LANTERNS)
                && Rs2Dialogue.keyPressForDialogueOption("Sure, I'll take the hat.", true)) {
            acted("Accepted Halloween hat from Angel / Henja");
            return;
        }

        if (stage == Stage.CHEMIST_RIDDLE) {
            // Wiki-confirmed conversation: select Halloween, then solve COFFIN = 92.
            // Never choose unrelated Chemist topics or a random numeric option.
            if (handleChemistDialogueOption(options)) return;
            if (System.currentTimeMillis() - lastDiagnosticAt > 5_000L) {
                lastDiagnosticAt = System.currentTimeMillis();
                log.warn("Halloween Chemist dialogue requires review | question={} options={}",
                        Rs2Dialogue.getQuestion(), options);
            }
            status = "Waiting for Chemist Halloween / 92 option; see dialogue logs";
            return;
        }
        String[] choices = {
            "Yes", "Sure", "I'd like to help", "Of course", "Let's do it",
            "I'll help", "I can help", "Okay", "Halloween", "Trick or treat"
        };
        for (String choice : choices) {
            if (Rs2Dialogue.clickOption(choice, true)) {
                acted("Selected confirmed event dialogue: " + choice);
                return;
            }
        }
        if (System.currentTimeMillis() - lastDiagnosticAt > 5_000L) {
            lastDiagnosticAt = System.currentTimeMillis();
            log.warn("Halloween dialogue needs review | stage={} options={}", stage, options);
        }
        status = "Unknown " + stage + " dialogue options; see logs (no random selection)";
    }

    /**
     * The Chemist's visible dialogue must provide the exact wiki event option.
     * An attempted click is NOT treated as success until the menu changes or closes.
     */
    private boolean handleChemistDialogueOption(List<String> options) {
        int optionIndex = KspHalloweenChemistDialogue.findOptionIndex(options);
        if (optionIndex < 0) {
            clearChemistOptionPending();
            return false;
        }

        String question = Rs2Dialogue.getQuestion();
        String signature = (question == null ? "" : question) + "|" + options;
        if (!signature.equals(pendingChemistOptions)) {
            pendingChemistOptions = signature;
            pendingChemistOptionAt = 0L;
            pendingChemistOptionAttempts = 0;
            log.info("Halloween Chemist options observed | question={} options={} chosen={}",
                    question, options, options.get(optionIndex));
        }

        long now = System.currentTimeMillis();
        if (pendingChemistOptionAt != 0L && now - pendingChemistOptionAt < 2_000L) {
            status = "Waiting for Chemist menu to change after "
                    + options.get(optionIndex) + " (" + pendingChemistOptionAttempts + " attempts)";
            return true;
        }
        if (pendingChemistOptionAttempts >= 6) {
            stopForReview("Chemist option was selected six times without a dialogue change: "
                    + options.get(optionIndex) + "; options=" + options);
            return true;
        }
        if (!ready()) return true;

        List<Widget> liveOptions = Rs2Dialogue.getDialogueOptions();
        if (optionIndex >= liveOptions.size()) {
            status = "Chemist options changed while reading widgets; retrying";
            clearChemistOptionPending();
            return true;
        }

        // First try client-local widget bounds, then RuneLite/Microbot's option
        // keybinding on the unchanged menu. No fixed canvas coordinates.
        boolean viaWidget = pendingChemistOptionAttempts % 2 == 0;
        boolean selected = viaWidget
                ? Rs2Widget.clickWidget(liveOptions.get(optionIndex))
                : Rs2Dialogue.keyPressForDialogueOption(optionIndex + 1);
        pendingChemistOptionAttempts++;
        pendingChemistOptionAt = now;
        if (selected) {
            acted("Selecting Chemist " + options.get(optionIndex)
                    + " via " + (viaWidget ? "option widget" : "option keybinding")
                    + "; awaiting dialogue change");
        } else {
            log.warn("Halloween Chemist option input failed | option={} method={} attempt={}",
                    options.get(optionIndex), viaWidget ? "widget" : "key", pendingChemistOptionAttempts);
            status = "Retrying Chemist " + options.get(optionIndex) + " option";
        }
        return true;
    }

    private void clearChemistOptionPending() {
        pendingChemistOptions = "";
        pendingChemistOptionAt = 0L;
        pendingChemistOptionAttempts = 0;
    }

    private boolean nearOrWalk(WorldPoint player, WorldPoint destination,
                               int distance, String purpose) {
        if (player.distanceTo(destination) <= distance) return true;
        status = "Walking to " + purpose;
        if (config == null || !config.walkToEvent()) {
            status = "Travel disabled: move toward " + purpose;
            return false;
        }
        if (!Rs2Player.isMoving() && System.currentTimeMillis() - lastWalkAt >= WALK_RETRY) {
            lastWalkAt = System.currentTimeMillis();
            log.info("Halloween travel | stage={} player={} target={}", stage, player, destination);
            Rs2Walker.walkTo(destination, distance);
        }
        return false;
    }

    private void talkToStarter(WorldPoint player) {
        if (!nearOrWalk(player, STARTER, 8, "Angel / Henja's house"))
            return;
        if (!ready()) return;
        boolean clicked = Rs2Npc.getNpc("Henja") != null
                && Rs2Npc.interact("Henja", "Talk-to");
        if (!clicked) clicked = Rs2Npc.getNpc("Angel") != null
                && Rs2Npc.interact("Angel", "Talk-to");
        if (!clicked) {
            status = "Locating Angel / Henja in their Rimmington home";
            return;
        }
        acted("Speaking to Angel / Henja to resolve current event state");
        lastTalkAt = System.currentTimeMillis();
        pendingStarterNext = null;
        pendingStarterAt = lastTalkAt;
        pendingStarterLastDialogueAt = 0L;
        pendingStarterDialogueSeen = false;
        starterConversationActive = true;
        lastStarterDialogue = "";
    }

    private Rs2TileObjectModel lantern() {
        return Microbot.getRs2TileObjectCache().query().fromWorldView()
                .where(o -> o != null && o.getName() != null
                        && o.getWorldLocation() != null
                        && o.getName().toLowerCase(Locale.ROOT).contains("jack")
                        && o.getName().toLowerCase(Locale.ROOT).contains("lantern")
                        && o.hasActionContaining("Smash")
                        && !smashedLanternTiles.contains(tileKey(o.getWorldLocation())))
                .nearestOnClientThread();
    }

    private static String tileKey(WorldPoint point) {
        return point.getX() + ":" + point.getY() + ":" + point.getPlane();
    }

    private static WorldPoint parseLanternTile(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            String[] parts = value.split(":");
            if (parts.length != 3) return null;
            WorldPoint tile = new WorldPoint(Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            return RIMMINGTON.contains(tile) ? tile : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void clearPendingLantern() {
        pendingLantern = null;
        pendingLanternAt = 0L;
        configManager.unsetConfiguration(GROUP, PENDING_LANTERN_KEY);
    }

    private void confirmSmashedLantern(WorldPoint tile, String evidence) {
        if (tile == null) return;
        if (smashedLanternTiles.add(tileKey(tile))) {
            configManager.setConfiguration(GROUP, LANTERNS_KEY,
                    String.join(",", smashedLanternTiles));
            log.info("Halloween lantern smash confirmed | tile={} evidence={} count={}/8",
                    tile, evidence, getLanternCount());
        }
        clearPendingLantern();
    }

    private void confirmAllLanterns(String evidence) {
        lanternsVerified = true;
        clearPendingLantern();
        configManager.setConfiguration(GROUP, LANTERNS_VERIFIED_KEY, true);
        log.info("Halloween all eight lanterns verified | evidence={} recordedTiles={}",
                evidence, smashedLanternTiles.size());
    }

    private boolean pendingLanternConfirmed(WorldPoint player) {
        if (pendingLantern == null) return false;
        // Never treat a missing object as smashed when its tile is out of
        // scene range. Revisit the exact clicked tile after any interruption.
        if (player.distanceTo(pendingLantern) > 9) {
            nearOrWalk(player, pendingLantern, 6, "confirm clicked jack-o'-lantern");
            return true;
        }
        if (pendingLanternAt != 0L
                && System.currentTimeMillis() - pendingLanternAt < 1_100L) return true;
        final WorldPoint clickedTile = pendingLantern;
        boolean stillPresent = Microbot.getRs2TileObjectCache().query().fromWorldView()
                .where(o -> o != null && clickedTile.equals(o.getWorldLocation())
                        && o.getName() != null
                        && o.getName().toLowerCase(Locale.ROOT).contains("lantern")
                        && o.hasActionContaining("Smash"))
                .nearestOnClientThread() != null;
        if (!stillPresent) {
            confirmSmashedLantern(clickedTile, "smash action disappeared at loaded tile");
            return false;
        }
        if (pendingLanternAt == 0L
                || System.currentTimeMillis() - pendingLanternAt > 7_500L) {
            log.info("Halloween smash still available | tile={} retry allowed", clickedTile);
            clearPendingLantern(); // Only retry a verified still-smashable object.
            return false;
        }
        return true;
    }

    private void smashLanterns(WorldPoint player) {
        if (!RIMMINGTON.contains(player)
                && !nearOrWalk(player, STARTER, 12, "Rimmington lanterns")) return;
        if (pendingLanternConfirmed(player)) return;
        if (lanternsVerified) {
            change(Stage.REPORT_LANTERNS,
                    "Lantern objective already confirmed by Angel/Henja");
            return;
        }
        Rs2TileObjectModel target = lantern();
        if (target != null) {
            WorldPoint location = target.getWorldLocation();
            status = "Smashing jack-o'-lantern " + (smashedLanternTiles.size() + 1) + "/8";
            if (ready() && target.click(target.findAction("Smash"))) {
                pendingLantern = location;
                pendingLanternAt = System.currentTimeMillis();
                configManager.setConfiguration(GROUP, PENDING_LANTERN_KEY, tileKey(location));
                lastActionAt = pendingLanternAt;
                log.info("Halloween smash click | id={} tile={}", target.getId(), location);
            }
            return;
        }
        // Consult the starter immediately at 7/8 if no target is visible;
        // the eighth smash may have succeeded in a previous session.
        if (smashedLanternTiles.size() >= 7 && !sweepRequiredByStarter) {
            lastVerificationTalkAt = 0L;
            change(Stage.VERIFY_LANTERNS,
                    "Checking missed eighth lantern with Angel/Henja");
            return;
        }
        // Search all areas only if the starter specifically says one remains.
        if (sweepsWithoutTarget >= RIMMINGTON_SWEEP.length) {
            sweepRequiredByStarter = false;
            lastVerificationTalkAt = 0L;
            change(Stage.VERIFY_LANTERNS,
                    "No smashable lantern in full Rimmington sweep; checking with Angel/Henja");
            return;
        }
        scanRimmington(player, "Searching for remaining jack-o'-lanterns");
    }

    private void scanRimmington(WorldPoint player, String purpose) {
        if (sweepsWithoutTarget >= 24) {
            stopForReview("Cannot locate remaining " + stage
                    + " targets in Rimmington; check logs/NPC state");
            return;
        }
        WorldPoint next = RIMMINGTON_SWEEP[sweepIndex % RIMMINGTON_SWEEP.length];
        if (player.distanceTo(next) <= 7) {
            sweepIndex++;
            sweepsWithoutTarget++;
            if (stage == Stage.SMASH_LANTERNS
                    && sweepsWithoutTarget >= RIMMINGTON_SWEEP.length) {
                sweepRequiredByStarter = false;
                change(Stage.VERIFY_LANTERNS,
                        "Sweep exhausted; checking Halloween lantern progress with children");
                return;
            }
            next = RIMMINGTON_SWEEP[sweepIndex % RIMMINGTON_SWEEP.length];
        }
        status = purpose + " (" + (sweepIndex % RIMMINGTON_SWEEP.length + 1) + "/8 areas)";
        nearOrWalk(player, next, 6, purpose);
    }

    private void inspectStarterDialogue() {
        String raw = Rs2Dialogue.hasSelectAnOption()
                ? Rs2Dialogue.getQuestion() : Rs2Dialogue.getDialogueText();
        if (raw == null || raw.isBlank()) return;

        lastStarterDialogue = raw.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
        KspHalloweenStarterState.Objective objective =
                KspHalloweenStarterState.classify(raw);
        if (objective == KspHalloweenStarterState.Objective.UNKNOWN) {
            if (System.currentTimeMillis() - lastDiagnosticAt > 5_000L) {
                lastDiagnosticAt = System.currentTimeMillis();
                log.info("Halloween Angel/Henja state dialogue not yet classified"
                        + " | stage={} text={}", stage, raw);
            }
            return;
        }

        Stage resolved;
        switch (objective) {
            case SMASH_LANTERNS:
                // Dialogue outranks any stale local 8/8 count. Keep observed
                // tiles as discovery history, but never auto-advance from it.
                lanternsVerified = false;
                configManager.unsetConfiguration(GROUP, LANTERNS_VERIFIED_KEY);
                sweepRequiredByStarter = true;
                resolved = Stage.SMASH_LANTERNS;
                break;

            case SCARE_CHILDREN:
                confirmAllLanterns("Angel/Henja dialogue advanced beyond lanterns");
                childrenReportConfirmed = false;
                configManager.unsetConfiguration(GROUP, CHILD_CONFIRMED_KEY);
                resolved = costumeCount() >= 4 ? Stage.SPOOK_CHILDREN : Stage.GET_COSTUME;

                if (stage == Stage.REPORT_CHILDREN && resolved == Stage.SPOOK_CHILDREN
                        && pendingStarterNext != resolved) {
                    childSpookRetries++;
                    configManager.setConfiguration(
                            GROUP, CHILD_RETRY_COUNT_KEY, childSpookRetries);
                    int allowedRetries =
                            spookedChildren.size() >= CHILD_REPORT_CHECKPOINT ? 1 : 2;
                    if (childSpookRetries > allowedRetries) {
                        pendingStarterNext = null;
                        stopForReview("Angel/Henja still requires child scares after "
                                + childSpookRetries + " verified reports. Inspect the"
                                + " Spook responses and child NPC availability");
                        return;
                    }
                    if (spookedChildren.size() >= CHILD_REPORT_CHECKPOINT) {
                        spookedChildren.clear();
                        persistNpcIds(SPOOKED_CHILD_IDS_KEY, spookedChildren);
                    }
                }
                break;

            case ADULT_CANDY:
                confirmAllLanterns("Angel/Henja dialogue confirms post-lantern objective");
                childrenReportConfirmed = true;
                configManager.setConfiguration(GROUP, CHILD_CONFIRMED_KEY, true);
                configManager.unsetConfiguration(GROUP, CHILD_RETRY_COUNT_KEY);
                childSpookRetries = 0;

                // If local delivery bookkeeping says 5/5 but the starter still
                // describes the adult-candy objective, the local counter lost
                // the authority contest. Re-open all adult identities safely.
                if (adultsGiven >= 5) {
                    log.warn("Angel/Henja contradict local adults 5/5; resetting local"
                            + " delivery acknowledgements to follow server dialogue");
                    adultsGiven = 0;
                    givenAdultIds.clear();
                    configManager.setConfiguration(GROUP, ADULTS_KEY, 0);
                    persistNpcIds(DELIVERED_ADULT_IDS_KEY, givenAdultIds);
                }
                resolved = stageForAdultCandyObjective();
                break;

            case CLAIM_REWARD:
                confirmAllLanterns("Angel/Henja final dialogue confirms late event state");
                childrenReportConfirmed = true;
                configManager.setConfiguration(GROUP, CHILD_CONFIRMED_KEY, true);
                resolved = Stage.WAIT_FOR_REWARD;
                break;

            case COMPLETE:
                resolved = Stage.COMPLETED;
                break;

            default:
                return;
        }

        if (pendingStarterNext != resolved) {
            log.info("Halloween Angel/Henja resolved event objective"
                            + " | dialogueObjective={} previousStage={} resolvedStage={}"
                            + " local(lanterns={}/8 childAttempts={}/{} adults={}/5)"
                            + " text={}",
                    objective, stage, resolved, getLanternCount(),
                    getChildSpookAttempts(), CHILD_REPORT_CHECKPOINT,
                    adultsGiven, raw);
        }
        pendingStarterNext = resolved;
        pendingStarterDialogueSeen = true;
        pendingStarterLastDialogueAt = System.currentTimeMillis();
    }

    private Stage stageForAdultCandyObjective() {
        int candy = Rs2Inventory.count("Candy onion");
        int oldOnions = Rs2Inventory.count("Old onion");
        boolean molten = Rs2Inventory.hasItem("Molten sugar");
        boolean sugar = Rs2Inventory.hasItem("Sugar", true);

        if (candy > 0) return Stage.GIVE_CANDY;
        if (molten && oldOnions > 0) return Stage.COAT_ONIONS;
        if (molten) return Stage.COLLECT_ONIONS;
        if (sugar) return Stage.COOK_SUGAR;
        return Stage.CHEMIST_RIDDLE;
    }

    /**
     * Recovery control: request a fresh server-side event-state read from
     * Angel/Henja. It never fabricates lantern completion or changes counters.
     */
    public synchronized boolean requestStarterStateCheck() {
        if (!active || stage == Stage.COMPLETED) return false;
        if (stage == Stage.PAUSED) {
            beforePause = Stage.INTRODUCTION;
            configManager.setConfiguration(GROUP, STAGE_KEY, Stage.INTRODUCTION.name());
            status = "Event state will be rechecked with Angel / Henja on resume";
            return true;
        }
        starterConversationActive = false;
        pendingStarterNext = null;
        pendingStarterDialogueSeen = false;
        change(Stage.INTRODUCTION,
                "Manual event-state recheck requested; consult Angel / Henja");
        return true;
    }

    public boolean canRequestStarterStateCheck() {
        return active && stage != Stage.COMPLETED;
    }

    // An outfit requires four *different equipment slots*. Retrieving four
    // masks or multiple robes does not satisfy the Halloween costume task.
    private enum CostumeSlot { HEAD, BODY, LEGS, HANDS, FEET }

    private CostumeSlot costumeSlot(String item) {
        if (item == null) return null;
        String name = item.toLowerCase(Locale.ROOT);
        if (name.contains("boots")) return CostumeSlot.FEET;
        if (name.contains("gloves")) return CostumeSlot.HANDS;
        if (name.contains("leggings") || name.contains("trousers")
                || name.contains("bottoms") || name.contains("skirt")) return CostumeSlot.LEGS;
        if (name.contains("shirt") || name.contains("top") || name.contains("robe")
                || name.contains("jumper")) return CostumeSlot.BODY;
        if (name.contains("mask") || name.contains("hood") || name.contains("wig")
                || name.contains("hat") || name.contains("head")) return CostumeSlot.HEAD;
        return null;
    }

    private Set<CostumeSlot> heldCostumeSlots(boolean includeInventory) {
        Set<CostumeSlot> slots = EnumSet.noneOf(CostumeSlot.class);
        for (String piece : COSTUME_PIECES) {
            CostumeSlot slot = costumeSlot(piece);
            if (slot != null && (Rs2Equipment.isWearing(piece, true)
                    || (includeInventory && Rs2Inventory.hasItem(piece, true)))) {
                slots.add(slot);
            }
        }
        return slots;
    }

    private int costumeCount() {
        return heldCostumeSlots(false).size();
    }

    private void collectCostume(WorldPoint player) {
        if (!ready()) return;
        int equipped = costumeCount();
        KspDiangoHolidayRetrieval.Snapshot window =
                KspDiangoHolidayRetrieval.inspect(missingCostumeRetrievalNames());

        if (equipped >= 4) {
            if (window.open) {
                closeHolidayRetrieval(window);
                return;
            }
            change(Stage.SPOOK_CHILDREN, "Four distinct Halloween costume slots equipped");
            return;
        }

        // One pending withdrawal at a time. Keep the modal open until the
        // item is actually in inventory; a click alone does not count.
        if (pendingHolidayItem != null) {
            if (Rs2Inventory.hasItem(pendingHolidayItem, true)
                    || Rs2Equipment.isWearing(pendingHolidayItem, true)) {
                log.info("Diango withdrawal confirmed | item={}", pendingHolidayItem);
                pendingHolidayItem = null;
                pendingHolidayItemAt = 0L;
                holidayRetrievalTimeouts = 0;
                holidayScrollAttempts = 0;
            } else if (System.currentTimeMillis() - pendingHolidayItemAt < 5_000L) {
                status = "Waiting for holiday item: " + pendingHolidayItem
                        + " (retrieval remains open)";
                return;
            } else {
                holidayRetrievalTimeouts++;
                log.warn("Diango withdrawal unconfirmed | item={} attempts={}",
                        pendingHolidayItem, holidayRetrievalTimeouts);
                if (holidayRetrievalTimeouts >= 3) {
                    stopForReview("Diango item not received: " + pendingHolidayItem);
                    return;
                }
                pendingHolidayItem = null;
                pendingHolidayItemAt = 0L;
            }
        }

        Set<CostumeSlot> ownedSlots = heldCostumeSlots(true);

        // Do NOT close Diango's window after retrieving just one item.
        // Collect all four different wearable slots first, in one session.
        if (ownedSlots.size() >= 4) {
            if (window.open) {
                log.info("Diango costume batch complete | ownedSlots={} equipped={}/4; closing once",
                        ownedSlots, equipped);
                closeHolidayRetrieval(window);
                return;
            }

            // Once the entire batch is held, equip without opening Diango
            // again. Never replace an already-equipped Halloween slot.
            Set<CostumeSlot> wornSlots = heldCostumeSlots(false);
            for (String piece : COSTUME_PIECES) {
                CostumeSlot slot = costumeSlot(piece);
                if (slot == null || wornSlots.contains(slot)
                        || !Rs2Inventory.hasItem(piece, true)) continue;
                if (Rs2Inventory.wear(piece)) {
                    acted("Equipping collected costume: " + piece
                            + " (" + wornSlots.size() + "/4 slots worn)");
                } else {
                    status = "Failed to equip collected costume: " + piece;
                }
                return;
            }

            // Do not reopen Diango when all planned items are already owned.
            stopForReview("Four costume slots owned but fewer than four equipped;"
                    + " check inventory/equipment actions");
            return;
        }

        if (window.open) {
            // Stay here until the complete missing batch is in inventory.
            // Never interact with Diango while his retrieval modal is up.
            if (Rs2Inventory.getEmptySlots() <= 0) {
                stopForReview("Inventory full before collecting four distinct Halloween pieces");
                return;
            }
            if (window.itemName != null && window.itemBounds != null) {
                if (KspDiangoHolidayRetrieval.select(window)) {
                    pendingHolidayItem = window.itemName;
                    pendingHolidayItemAt = System.currentTimeMillis();
                    holidayScrollAttempts = 0;
                    acted("Collecting Halloween piece " + (ownedSlots.size() + 1)
                            + "/4: " + pendingHolidayItem + " (keeping Diango open)");
                }
                return;
            }
            if (holidayScrollAttempts < 24 && window.canScrollDown()) {
                if (KspDiangoHolidayRetrieval.scrollDown(window)) {
                    holidayScrollAttempts++;
                    acted("Searching additional missing costume slots; scroll "
                            + holidayScrollAttempts);
                }
                return;
            }
            log.warn("Diango batch cannot be completed | ownedSlots={} itemWidgets={}"
                            + " scroll={}/{}", ownedSlots, window.itemWidgets,
                    window.scrollY, window.scrollMax);
            stopForReview("Diango has no additional suitable wearable costume pieces");
            return;
        }

        // Only reopen the modal if it closed before the complete batch was
        // acquired (e.g. human intervention or a client UI interruption).
        holidayScrollAttempts = 0;
        holidayCloseAttempts = 0;
        if (!nearOrWalk(player, DIANGO, 8, "Diango in Draynor Village")) return;
        if (Rs2Npc.getNpc("Diango") != null
                && System.currentTimeMillis() - lastTalkAt > 6_000L) {
            if (Rs2Npc.interact("Diango", "Holiday-items")) {
                lastTalkAt = System.currentTimeMillis();
                acted("Opening Diango to collect missing costume pieces");
                return;
            }
            if (Rs2Npc.interact("Diango", "Talk-to")) {
                lastTalkAt = System.currentTimeMillis();
                acted("Asking Diango for holiday items");
                return;
            }
        }
        status = "Collecting complete Halloween outfit (" + ownedSlots.size()
                + "/4 slots already owned)";
    }

    private List<String> missingCostumeRetrievalNames() {
        Set<CostumeSlot> ownedSlots = heldCostumeSlots(true);
        return DIANGO_COSTUME_PRIORITY.stream()
                .filter(item -> {
                    CostumeSlot slot = costumeSlot(item);
                    return slot != null && !ownedSlots.contains(slot)
                            && !Rs2Inventory.hasItem(item, true)
                            && !Rs2Equipment.isWearing(item, true);
                })
                .collect(Collectors.toList());
    }

    private void closeHolidayRetrieval(KspDiangoHolidayRetrieval.Snapshot window) {
        if (holidayCloseAttempts >= 3) {
            stopForReview("Unable to close Diango retrieval after collecting full costume");
            return;
        }
        if (!KspDiangoHolidayRetrieval.close(window)) {
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
        }
        holidayCloseAttempts++;
        acted("Closing Diango after full four-slot costume batch");
    }

    private boolean npcHasAction(Rs2NpcModel npc, String action) {
        if (npc == null) return false;
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (Microbot.getClient() == null) return false;
            net.runelite.api.NPCComposition def =
                    Microbot.getClient().getNpcDefinition(npc.getId());
            if (def == null || def.getActions() == null) return false;
            return Arrays.stream(def.getActions()).anyMatch(a ->
                    a != null && a.equalsIgnoreCase(action));
        }).orElse(false);
    }

    private TreaterGroup treaterGroup(int npcId) {
        if (npcId >= TREATER_GROUP_A_START && npcId <= TREATER_GROUP_A_END)
            return TreaterGroup.A;
        if (npcId >= TREATER_GROUP_B_START && npcId <= TREATER_GROUP_B_END)
            return TreaterGroup.B;
        return TreaterGroup.UNKNOWN;
    }

    private void observeTreaterAges() {
        // Once confirmed (including after a saved resume), do not make any
        // synchronous client-thread requests just to remeasure NPC heights.
        if (confirmedChildGroup != TreaterGroup.UNKNOWN) return;

        List<Rs2NpcModel> candidates = Rs2Npc.getNpcs()
                .filter(n -> n.getName() != null
                        && n.getName().toLowerCase(Locale.ROOT).contains("trick")
                        && treaterGroup(n.getId()) != TreaterGroup.UNKNOWN)
                .collect(Collectors.toList());

        // RuneLite actor heights belong to the client thread. Read the
        // complete observed set in ONE batch instead of blocking separately
        // for every NPC on every scheduler tick.
        Map<Integer, Integer> heights = Microbot.getClientThread()
                .runOnClientThreadOptional(() -> {
                    Map<Integer, Integer> snapshot = new HashMap<>();
                    for (Rs2NpcModel candidate : candidates) {
                        int height = candidate.getLogicalHeight();
                        if (height > 0) snapshot.put(candidate.getId(), height);
                    }
                    return snapshot;
                }).orElse(Collections.emptyMap());
        observedTreaterHeights.putAll(heights);

        List<Integer> a = new ArrayList<>();
        List<Integer> b = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : observedTreaterHeights.entrySet()) {
            TreaterGroup group = treaterGroup(entry.getKey());
            if (group == TreaterGroup.A) a.add(entry.getValue());
            if (group == TreaterGroup.B) b.add(entry.getValue());
        }

        // The two NPC ID ranges are not assumed to be children/adults.
        // Verify actual relative model size using multiple live samples.
        if (a.size() >= 2 && b.size() >= 2) {
            int aMin = a.stream().mapToInt(Integer::intValue).min().orElse(0);
            int aMax = a.stream().mapToInt(Integer::intValue).max().orElse(0);
            int bMin = b.stream().mapToInt(Integer::intValue).min().orElse(0);
            int bMax = b.stream().mapToInt(Integer::intValue).max().orElse(0);
            if (aMax > 0 && aMax * 100L <= bMin * 80L) {
                confirmedChildGroup = TreaterGroup.A;
            } else if (bMax > 0 && bMax * 100L <= aMin * 80L) {
                confirmedChildGroup = TreaterGroup.B;
            }
            if (confirmedChildGroup != TreaterGroup.UNKNOWN) {
                configManager.setConfiguration(GROUP, TREATER_CHILD_GROUP_KEY,
                        confirmedChildGroup.name());
                log.info("Halloween child/adult NPC groups confirmed by live model heights"
                        + " | childGroup={} groupAHeights={} groupBHeights={}",
                        confirmedChildGroup, a, b);
                return;
            }
            stopForReview("Child/adult NPC models cannot be differentiated safely"
                    + "; groupAHeights=" + a + " groupBHeights=" + b);
            return;
        }

        if (System.currentTimeMillis() - lastTreaterClassificationLogAt > 10_000L) {
            lastTreaterClassificationLogAt = System.currentTimeMillis();
            log.info("Halloween NPC age classification not yet verified"
                    + " | observedHeights={} (unknown NPCs excluded)",
                    observedTreaterHeights);
        }
    }

    private TreaterAge classifyTreater(Rs2NpcModel npc) {
        if (npc == null || npc.getName() == null) return TreaterAge.UNKNOWN;
        String name = npc.getName().toLowerCase(Locale.ROOT);
        if (!name.contains("trick")) return TreaterAge.UNKNOWN;
        if (name.contains("adult") || name.contains("grown-up"))
            return TreaterAge.ADULT;
        if (name.contains("child") || name.contains("kid"))
            return TreaterAge.CHILD;
        TreaterGroup group = treaterGroup(npc.getId());
        if (group == TreaterGroup.UNKNOWN
                || confirmedChildGroup == TreaterGroup.UNKNOWN)
            return TreaterAge.UNKNOWN;
        return group == confirmedChildGroup ? TreaterAge.CHILD : TreaterAge.ADULT;
    }

    private boolean scoutVisibleTreaters(WorldPoint player, List<Rs2NpcModel> visible) {
        // Only actual NPC positions observed through RuneLite are navigation
        // anchors. We may scout *near* adults to load nearby children, but
        // never click Spook on an adult or count it towards child progress.
        if (childScoutedNpcTiles.size() >= MAX_CHILD_NPC_SCOUT_STOPS) return false;

        List<Rs2NpcModel> anchors = visible.stream()
                .filter(n -> {
                    TreaterAge age = classifyTreater(n);
                    return age == TreaterAge.ADULT
                            || (age == TreaterAge.CHILD
                                && !spookedChildren.contains(n.getId()));
                })
                .filter(n -> n.getWorldLocation() != null
                        && RIMMINGTON.contains(n.getWorldLocation()))
                .sorted(Comparator.comparingInt(n ->
                        player.distanceTo(n.getWorldLocation())))
                .collect(Collectors.toList());

        for (Rs2NpcModel anchor : anchors) {
            WorldPoint target = anchor.getWorldLocation();
            String key = tileKey(target);
            if (childScoutedNpcTiles.contains(key)) continue;

            if (player.distanceTo(target) <= 7) {
                childScoutedNpcTiles.add(key);
                continue;
            }
            if (Rs2Player.isMoving()
                    || System.currentTimeMillis() - lastWalkAt < WALK_RETRY) {
                status = "Waiting to scout near visible trick-or-treater NPCs";
                return true;
            }
            if (config == null || !config.walkToEvent()) {
                status = "Walking disabled; unable to scout observed NPC at " + target;
                return true;
            }

            childScoutedNpcTiles.add(key);
            log.info("Halloween NPC-guided child search | anchorNpcId={} age={} tile={}"
                            + " attempts={}/{} scoutStops={}/{}",
                    anchor.getId(), classifyTreater(anchor), target,
                    spookedChildren.size(), CHILD_REPORT_CHECKPOINT,
                    childScoutedNpcTiles.size(), MAX_CHILD_NPC_SCOUT_STOPS);
            nearOrWalk(player, target, 6, "visible trick-or-treater NPC location");
            return true;
        }
        return false;
    }

    private void spookChildren(WorldPoint player) {
        if (costumeCount() < 4) {
            change(Stage.GET_COSTUME, "Need four Halloween outfit pieces equipped");
            return;
        }
        if (!RIMMINGTON.contains(player)
                && !nearOrWalk(player, STARTER, 12, "child trick-or-treaters")) return;

        observeTreaterAges();
        if (stage == Stage.NEEDS_REVIEW) return;

        if (spookedChildren.size() >= CHILD_REPORT_CHECKPOINT) {
            childrenReportConfirmed = false;
            change(Stage.REPORT_CHILDREN,
                    "Five distinct child Spook attempts; verifying with Angel/Henja");
            return;
        }

        // One live scene snapshot per tick. Child selection is based on the
        // verified age group AND the actual Spook action. Adult NPCs are never
        // eligible targets, even when travelling past them for discovery.
        List<Rs2NpcModel> visibleTreaters = Rs2Npc.getNpcs()
                .filter(n -> n.getName() != null
                        && n.getName().toLowerCase(Locale.ROOT).contains("trick"))
                .collect(Collectors.toList());

        Rs2NpcModel child = visibleTreaters.stream()
                .filter(n -> classifyTreater(n) == TreaterAge.CHILD
                        && !spookedChildren.contains(n.getId())
                        && npcHasAction(n, "Spook"))
                .min(Comparator.comparingInt(n -> n.getWorldLocation() == null
                        ? Integer.MAX_VALUE : player.distanceTo(n.getWorldLocation())))
                .orElse(null);

        if (child != null) {
            childNoTargetSince = 0L;
            if (ready() && Rs2Npc.interact(child, "Spook")) {
                spookedChildren.add(child.getId());
                persistNpcIds(SPOOKED_CHILD_IDS_KEY, spookedChildren);
                // If new children become available after moving, previous
                // partial-search retries should not count as stalled progress.
                childSpookRetries = 0;
                configManager.unsetConfiguration(GROUP, CHILD_RETRY_COUNT_KEY);
                acted("Spook attempt | childNpcId=" + child.getId()
                        + " index=" + child.getIndex()
                        + " distinctAttempts=" + spookedChildren.size()
                        + "/" + CHILD_REPORT_CHECKPOINT);
            }
            return;
        }

        // NPC scene updates may lag behind a movement or Spook action.
        // Give the scene time to settle before initiating any new route.
        long now = System.currentTimeMillis();
        if (childNoTargetSince == 0L) {
            childNoTargetSince = now;
            status = "Checking nearby child trick-or-treaters ("
                    + spookedChildren.size() + "/" + CHILD_REPORT_CHECKPOINT
                    + " Spook attempts)";
            return;
        }
        if (Rs2Player.isMoving() || now - childNoTargetSince < CHILD_SCENE_SETTLE_MS) {
            status = "Waiting for visible child NPCs ("
                    + spookedChildren.size() + "/" + CHILD_REPORT_CHECKPOINT + ")";
            return;
        }

        if (scoutVisibleTreaters(player, visibleTreaters)) return;

        String sceneDetails = visibleTreaters.stream()
                .map(n -> n.getId() + ":" + classifyTreater(n)
                        + ":spook=" + npcHasAction(n, "Spook")
                        + ":tile=" + n.getWorldLocation())
                .collect(Collectors.joining(", "));
        log.info("Halloween no further unspooked child in loaded scene"
                        + " | attemptedIds={} visibleTreaters=[{}] scoutedNpcTiles={}"
                        + " => return to Angel/Henja for authoritative quest check",
                spookedChildren, sceneDetails, childScoutedNpcTiles);
        childrenReportConfirmed = false;
        change(Stage.REPORT_CHILDREN,
                "No additional child Spook targets in live scene; verifying quest dialogue");
    }


    private void solveChemist(WorldPoint player) {
        if (Rs2Inventory.hasItem("Sugar", true)
                || Rs2Inventory.hasItem("Molten sugar")) {
            change(Stage.COOK_SUGAR, "Chemist sugar acquired");
            return;
        }
        if (!nearOrWalk(player, CHEMIST, 8, "the Chemist west of Rimmington")) return;
        if (ready() && Rs2Npc.getNpc("Chemist") != null
                && System.currentTimeMillis() - lastTalkAt > 6_000L
                && Rs2Npc.interact("Chemist", "Talk-to")) {
            lastTalkAt = System.currentTimeMillis();
            acted("Talking to Chemist; COFFIN riddle answer is 92");
        } else {
            status = "Chemist puzzle: select answer 92";
        }
    }

    private Rs2TileObjectModel cookingSurface() {
        // Sugar is USED ON the range/fireplace; the object itself need not
        // advertise a Cook or Use context-menu action.
        return Microbot.getRs2TileObjectCache().query().fromWorldView()
                .where(o -> o != null && o.getName() != null
                        && (o.getName().equalsIgnoreCase("Range")
                            || o.getName().equalsIgnoreCase("Fireplace")))
                .nearestOnClientThread();
    }

    private void cookSugar(WorldPoint player) {
        if (Rs2Inventory.hasItem("Molten sugar")) {
            change(Stage.COLLECT_ONIONS, "Molten sugar ready");
            return;
        }
        if (!Rs2Inventory.hasItem("Sugar", true)) {
            change(Stage.CHEMIST_RIDDLE, "Need sugar from Chemist");
            return;
        }
        if (!RIMMINGTON.contains(player)
                && !nearOrWalk(player, CHEMIST, 8, "Rimmington range / fireplace")) return;
        Rs2TileObjectModel surface = cookingSurface();
        if (surface == null) {
            // The Chemist and Angel/Henja are far enough apart that the
            // cooking surface may not be loaded into the scene yet.
            if (!nearOrWalk(player, STARTER, 8, "Angel / Henja range to cook sugar"))
                return;
            status = "Locating range in Angel/Henja's house or Chemist fireplace";
            return;
        }
        net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel sugar =
                Rs2Inventory.get("Sugar", true);
        if (sugar != null && ready()
                && Rs2Inventory.useItemOnObject(sugar.getId(), surface.getId())) {
            acted("Cooking sugar at " + surface.getName());
        }
    }

    private void collectOnions(WorldPoint player) {
        if (Rs2Inventory.count("Old onion") >= 5) {
            change(Stage.COAT_ONIONS, "Five event-only old onions collected");
            return;
        }
        // The Chemist is already inside Rimmington, but the crate behind
        // Angel/Henja's home may still be outside the loaded scene.
        if (!nearOrWalk(player, STARTER, 8, "old onion crate behind Angel / Henja's house"))
            return;
        Rs2TileObjectModel crate = Microbot.getRs2TileObjectCache().query().fromWorldView()
                .where(o -> o != null && o.getName() != null
                        && o.getName().toLowerCase(Locale.ROOT).contains("onion")
                        && (o.hasActionContaining("Take") || o.hasActionContaining("Search")
                            || o.hasActionContaining("Collect")))
                .nearestOnClientThread();
        if (crate != null && ready()) {
            String action = crate.findAction("Take");
            if (action == null) action = crate.findAction("Search");
            if (action == null) action = crate.findAction("Collect");
            if (action != null && crate.click(action)) acted("Collecting OLD onion (not regular onion)");
        } else {
            status = "Locating the OLD onion crate behind Angel/Henja's house";
        }
    }

    private void coatOnions() {
        int candy = Rs2Inventory.count("Candy onion");
        int old = Rs2Inventory.count("Old onion");
        if (candy >= 5) {
            change(Stage.GIVE_CANDY, "Five candy onions ready for adults");
            return;
        }
        if (old <= 0) {
            change(Stage.COLLECT_ONIONS, "Need remaining old onions");
            return;
        }
        if (!Rs2Inventory.hasItem("Molten sugar")) {
            change(Stage.COOK_SUGAR, "Need more molten sugar");
            return;
        }
        if (ready() && Rs2Inventory.combine("Molten sugar", "Old onion"))
            acted("Coating old onion with molten sugar (" + candy + "/5 prepared)");
    }

    private void giveCandy(WorldPoint player) {
        int candyCount = Rs2Inventory.count("Candy onion");
        if (pendingCandyCount >= 0) {
            if (System.currentTimeMillis() - lastActionAt >= 1_200L) {
                if (candyCount < pendingCandyCount) {
                    int used = pendingCandyCount - candyCount;
                    if (pendingAdultId > 0 && givenAdultIds.add(pendingAdultId)) {
                        adultsGiven = Math.min(5, adultsGiven + 1);
                        configManager.setConfiguration(GROUP, ADULTS_KEY, adultsGiven);
                        persistNpcIds(DELIVERED_ADULT_IDS_KEY, givenAdultIds);
                        log.info("Halloween candy delivery verified | adultId={} used={} total={}/5",
                                pendingAdultId, used, adultsGiven);
                    }
                }
                pendingCandyCount = -1;
                pendingAdultId = -1;
            } else return;
        }
        if (adultsGiven >= 5) {
            change(Stage.FINAL_TALK, "Five adult trick-or-treaters served");
            return;
        }
        if (candyCount <= 0) {
            change(Stage.COAT_ONIONS, "Prepare more candy onions for adults");
            return;
        }
        if (!RIMMINGTON.contains(player)
                && !nearOrWalk(player, STARTER, 10, "adult trick-or-treaters")) return;
        observeTreaterAges();
        if (stage == Stage.NEEDS_REVIEW) return;
        Rs2NpcModel adult = Rs2Npc.getNpcs().filter(n -> n.getName() != null
                && n.getName().toLowerCase(Locale.ROOT).contains("trick")
                && classifyTreater(n) == TreaterAge.ADULT
                && !spookedChildren.contains(n.getId())
                && !givenAdultIds.contains(n.getId())
                && (npcHasAction(n, "Talk-to") || npcHasAction(n, "Trick-or-treat")))
                .findFirst().orElse(null);
        net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel item =
                Rs2Inventory.get("Candy onion");
        if (adult != null && item != null) {
            if (ready() && Rs2Inventory.useItemOnNpc(item.getId(), adult)) {
                pendingCandyCount = candyCount;
                pendingAdultId = adult.getId();
                acted("Offering candy onion to adult npcId=" + adult.getId());
                sweepsWithoutTarget = 0;
            }
            return;
        }
        if (sweepsWithoutTarget >= RIMMINGTON_SWEEP.length) {
            stopForReview("Adult search exhausted at " + adultsGiven
                    + "/5 deliveries; inspect available NPC actions");
            return;
        }
        scanRimmington(player, "Looking for adult trick-or-treaters");
    }

    private void acted(String message) {
        lastActionAt = System.currentTimeMillis();
        status = message;
        log.info("Halloween action | stage={} | {}", stage, message);
    }

    private boolean ready() {
        return System.currentTimeMillis() - lastActionAt >= ACTION_GAP;
    }

    private void change(Stage next, String message) {
        Stage previous = stage;
        stage = next;
        status = message;
        if (previous != next) {
            clearChemistOptionPending();
            Rs2Walker.clearWalkingRoute("ksp_seasonal_stage_changed");
            log.info("KSP Seasonal Events Halloween | {} -> {} | {}", previous, next, message);
            if (next != Stage.STOPPED && next != Stage.PAUSED && next != Stage.NEEDS_REVIEW)
                configManager.setConfiguration(GROUP, STAGE_KEY, next.name());
            actionsAtStage = 0;
            sweepsWithoutTarget = 0;
            sweepIndex = 0;
            if (next == Stage.SPOOK_CHILDREN) {
                childNoTargetSince = 0L;
                childScoutedNpcTiles.clear();
            }
        }
    }

    private void stopForReview(String message) {
        log.warn("Halloween review required | stage={} status={} player={} inventory(sugar/old/candy)={}/{}/{}",
                stage, message, Rs2Player.getWorldLocation(), Rs2Inventory.count("Sugar"),
                Rs2Inventory.count("Old onion"), Rs2Inventory.count("Candy onion"));
        beforePause = stage;
        stage = Stage.NEEDS_REVIEW;
        status = message;
    }

    public void onChatMessage(String raw) {
        if (!active || raw == null) return;
        String text = raw.replaceAll("<[^>]*>", "").toLowerCase(Locale.ROOT);
        if (stage == Stage.SMASH_LANTERNS && pendingLantern != null
                && text.startsWith("you smash")
                && (text.contains("lantern") || text.contains("pumpkin"))) {
            confirmSmashedLantern(pendingLantern, "game smash message");
            // Local smash acknowledgements update the overlay only. Angel/Henja
            // dialogue remains authoritative for leaving the lantern objective.
            if (smashedLanternTiles.size() >= 8) {
                change(Stage.VERIFY_LANTERNS,
                        "Eight local smash acknowledgements; verifying with Angel/Henja");
            }
            return;
        }
        if (text.contains("you have completed the halloween event")
                || text.contains("you have completed the 2026 halloween event")) {
            change(Stage.COMPLETED, "Halloween completion confirmed by game");
            return;
        }
        if (text.contains("jack") || text.contains("onion")
                || text.contains("trick-or-treat") || text.contains("sugar")
                || text.contains("halloween")) {
            log.info("Halloween game message | stage={} | {}", stage, text);
        }
    }

    public void setPaused(boolean value) {
        if (!active) return;
        if (value) {
            beforePause = stage;
            Rs2Walker.clearWalkingRoute("ksp_seasonal_paused");
            paused = true;
            stage = Stage.PAUSED;
            status = "Paused (progress preserved)";
        } else {
            paused = false;
            stage = beforePause == Stage.PAUSED ? savedStage() : beforePause;
            status = "Resuming " + stage;
        }
    }

    /** Intentional explicit reset; never silently reset progress on client restart. */
    public synchronized void resetProgress() {
        if (active) return;
        configManager.unsetConfiguration(GROUP, STAGE_KEY);
        configManager.unsetConfiguration(GROUP, LANTERNS_KEY);
        configManager.unsetConfiguration(GROUP, PENDING_LANTERN_KEY);
        configManager.unsetConfiguration(GROUP, LANTERNS_VERIFIED_KEY);
        configManager.unsetConfiguration(GROUP, ADULTS_KEY);
        configManager.unsetConfiguration(GROUP, SPOOKED_CHILD_IDS_KEY);
        configManager.unsetConfiguration(GROUP, DELIVERED_ADULT_IDS_KEY);
        configManager.unsetConfiguration(GROUP, CHILD_RETRY_COUNT_KEY);
        configManager.unsetConfiguration(GROUP, CHILD_CONFIRMED_KEY);
        configManager.unsetConfiguration(GROUP, TREATER_CHILD_GROUP_KEY);
        smashedLanternTiles.clear();
        pendingLantern = null;
        lanternsVerified = false;
        spookedChildren.clear();
        childScoutedNpcTiles.clear();
        childNoTargetSince = 0L;
        givenAdultIds.clear();
        pendingAdultId = -1;
        childrenReportConfirmed = false;
        childSpookRetries = 0;
        adultsGiven = 0;
        stage = Stage.STOPPED;
        status = "Progress reset; press Start";
    }

    public boolean isActive() { return active; }
    public boolean isPaused() { return paused; }
    public String getStatus() { return status; }
    public Stage getStage() { return stage; }
    public int getLanternCount() { return lanternsVerified ? 8
            : Math.min(8, smashedLanternTiles.size()); }
    /** A unique NPC Spook interaction was issued; not proof of a successful scare. */
    public int getChildSpookAttempts() {
        return Math.min(CHILD_REPORT_CHECKPOINT, spookedChildren.size());
    }

    public int getChildSpookCheckpoint() { return CHILD_REPORT_CHECKPOINT; }

    /** True only after Angel/Henja confirms the next objective. */
    public boolean isChildObjectiveConfirmed() { return childrenReportConfirmed; }

    public int getAdultsGiven() { return adultsGiven; }
    public long getRuntimeMs() { return startedAt == 0L ? 0L :
            System.currentTimeMillis() - startedAt; }

    @Override
    public synchronized void shutdown() {
        active = false;
        paused = false;
        Rs2Walker.clearWalkingRoute("ksp_seasonal_stopped");
        super.shutdown();
        stage = Stage.STOPPED;
        status = "Stopped - press Start";
    }
}
