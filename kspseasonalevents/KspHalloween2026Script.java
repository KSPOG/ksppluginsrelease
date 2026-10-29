package net.runelite.client.plugins.microbot.kspseasonalevents;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.WidgetInfo;
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
import java.util.concurrent.atomic.AtomicBoolean;
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
    // RuneLite Widget Inspector: Eventscroll.CONGRATULATIONS,
    // interface 807 child 5, full widget ID 52887557, text "Event complete!".
    private static final int EVENT_COMPLETE_GROUP = 807;
    private static final int EVENT_COMPLETE_CHILD = 5;
    private static final int EVENT_COMPLETE_WIDGET_ID =
            (EVENT_COMPLETE_GROUP << 16) | EVENT_COMPLETE_CHILD;
    private final AtomicBoolean completionSignaled = new AtomicBoolean();
    private volatile Runnable completionCallback;
    private static final String STAGE_KEY = "halloweenStage";
    private static final String LANTERNS_KEY = "halloweenSmashTiles";
    private static final String PENDING_LANTERN_KEY = "halloweenPendingSmashTile";
    private static final String LANTERNS_VERIFIED_KEY = "halloweenLanternsVerified";
    private static final String ADULTS_KEY = "halloweenAdultsGiven";
    private static final String SPOOKED_CHILD_IDS_KEY = "halloweenChildNpcIdsConfirmed";
    // v0.2.16 saved click attempts as if they were successful scares.
    // They have no scene-disappearance evidence and must not be migrated.
    private static final String LEGACY_SPOOKED_CHILD_ATTEMPTS_KEY = "halloweenChildNpcIdsAttempted";
    private static final String CHILD_RETRY_COUNT_KEY = "halloweenChildReportRetries";
    private static final String CHILD_CONFIRMED_KEY = "halloweenChildObjectiveConfirmed";
    private static final String DELIVERED_ADULT_IDS_KEY = "halloweenAdultNpcIdsDelivered";
    // Five NPCs per candidate age group were seen in the 2026 log.
    // Five live-scene-confirmed child disappearances trigger the report.
    // The NPC must vanish while its last tile remains inside the loaded
    // scene; mere click dispatch, movement or out-of-scene eviction is NOT proof.
    private static final int CHILD_REPORT_CHECKPOINT = 5;
    private static final int MAX_CHILD_NPC_SCOUT_STOPS = 4;
    private static final long CHILD_SPOOK_CONFIRM_TIMEOUT_MS = 7_000L;
    private static final long CHILD_SPOOK_ABSENCE_STABLE_MS = 450L;
    private static final long CHILD_SPOOK_RETRY_COOLDOWN_MS = 2_500L;
    // Retired heuristic classification persisted under this key in older
    // revisions. Unset it on startup; these verified IDs supersede it.
    private static final String LEGACY_TREATER_CHILD_GROUP_KEY = "halloweenConfirmedChildNpcGroup";
    // Verified 2026 Halloween NPC definition IDs from live game inspection.
    // Both tasks select by NPC.getId(), never by model height, name heuristics
    // or assumed context-menu differences between these two NPC ranges.
    private static final int CHILD_TREATER_FIRST_ID = 16778;
    private static final int CHILD_TREATER_LAST_ID = 16782;
    private static final int ADULT_TREATER_FIRST_ID = 16794;
    private static final int ADULT_TREATER_LAST_ID = 16798;
    private enum TreaterAge { CHILD, ADULT, UNKNOWN }
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
    // Timeouts are only failure recovery, never pacing between valid state changes.
    private static final long DIALOGUE_STALL_TIMEOUT_MS = 4_000L;
    private static final long WORLD_ACTION_STALL_TIMEOUT_MS = 6_000L;
    private static final int MAX_UNCONFIRMED_ACTIONS = 3;
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
    // Scheduler writes; overlay and panel read confirmed disappearances only.
    private final Set<Integer> spookedChildren = ConcurrentHashMap.newKeySet();
    private final Map<Integer, Long> childSpookCooldownUntil = new HashMap<>();
    private int pendingChildId = -1;
    private int pendingChildIndex = -1;
    private WorldPoint pendingChildTile;
    private long pendingChildSpookAt;
    private long pendingChildAbsentSince;
    private final Set<Integer> givenAdultIds = new HashSet<>();
    private volatile boolean childrenReportConfirmed;
    private int childrenReportAttempts;
    private int childSpookRetries;
    // Track only live-event-NPC-derived scout tiles. The generic Rimmington
    // lantern search waypoints are never used to locate child NPCs.
    private final Set<String> childScoutedNpcTiles = new HashSet<>();
    private long childNoTargetSince;
    private int adultsGiven;
    private int sweepIndex;
    private int sweepsWithoutTarget;
    private int actionsAtStage;
    private long lastActionAt;
    private String lastGenericDialogueSignature = "";
    private long lastGenericDialogueAt;
    private int lastGenericDialogueAttempts;    private long lastWalkAt;
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
    private int starterTalkAttempts;
    private int starterNoDialogueTicks;
    private boolean starterInterfaceSeen;
    private String lastStarterObservedWidgetText = "";
    private static final int MAX_STARTER_TALK_ATTEMPTS = 3;
    // Track the visible Chemist option menu until it actually changes.
    // Alternating widget clicks and option keybindings avoids silent keybind failures.
    private String pendingChemistOptions = "";
    private long pendingChemistOptionAt;
    private int pendingChemistOptionAttempts;
    // The 2026 COFFIN answer is entered into a chatbox text prompt, NOT
    // necessarily selected from a list of dialogue options.
    private long chemistConversationAt;
    private long chemistAnswerSubmittedAt;
    private boolean pendingCook;
    private long pendingCookAt;
    private int pendingCookFailures;
    private int pendingOldOnionCount = -1;
    private long pendingOldOnionAt;
    private int pendingOldOnionFailures;
    private long oldOnionCrateMissingSince;
    private int pendingCoatCandyCount = -1;
    private long pendingCoatAt;
    private int pendingCoatFailures;
    private String pendingEquipItem;
    private long pendingEquipAt;
    private boolean pendingHolidayClose;
    private long pendingHolidayCloseAt;
    private int pendingHolidayScrollY = -1;
    private long pendingHolidayScrollAt;
    private boolean chemistAnswerPromptObserved;
    private int chemistAnswerSubmissions;

    public void setCompletionCallback(Runnable callback) {
        completionCallback = callback;
    }

    public synchronized boolean start(KspSeasonalEventsConfig configuration) {
        if (active) return true;
        this.config = configuration;
        this.active = true;
        this.paused = false;
        completionSignaled.set(false);
        this.startedAt = System.currentTimeMillis();
        lastActionAt = lastWalkAt = lastTalkAt = lastDiagnosticAt = 0L;
        lastGenericDialogueSignature = "";
        lastGenericDialogueAt = 0L;
        lastGenericDialogueAttempts = 0;
        clearStagePendingActions();
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
        starterTalkAttempts = 0;
        starterNoDialogueTicks = 0;
        starterInterfaceSeen = false;
        lastStarterObservedWidgetText = "";
        clearChemistOptionPending();
        clearChemistTextPrompt();
        chemistConversationAt = 0L;
        chemistAnswerSubmissions = 0;
        pendingHolidayItem = null;
        pendingHolidayItemAt = 0L;
        holidayRetrievalTimeouts = 0;
        holidayScrollAttempts = 0;
        holidayCloseAttempts = 0;
        spookedChildren.clear();
        clearPendingChildSpook();
        childSpookCooldownUntil.clear();
        givenAdultIds.clear();
        childrenReportConfirmed = false;
        childrenReportAttempts = 0;
        childSpookRetries = 0;
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
        // Only restore verified scene disappearances from v0.2.17 onward.
        // Discard old speculative clicks without resetting lanterns/costume.
        String legacyChildAttempts = configManager.getConfiguration(
                GROUP, LEGACY_SPOOKED_CHILD_ATTEMPTS_KEY);
        if (legacyChildAttempts != null && !legacyChildAttempts.isBlank()) {
            log.warn("Halloween discarding unverified legacy child Spook clicks: {}."
                    + " Lantern and event state are preserved", legacyChildAttempts);
        }
        configManager.unsetConfiguration(GROUP, LEGACY_SPOOKED_CHILD_ATTEMPTS_KEY);
        restoreNpcIds(SPOOKED_CHILD_IDS_KEY, spookedChildren);
        restoreNpcIds(DELIVERED_ADULT_IDS_KEY, givenAdultIds);
        // Earlier heuristic releases could persist reversed group assignments.
        // Keep only NPC IDs that match the now-known task they completed.
        if (spookedChildren.removeIf(id -> !isChildTreaterId(id))) {
            persistNpcIds(SPOOKED_CHILD_IDS_KEY, spookedChildren);
            log.warn("Halloween discarded invalid child-spook NPC IDs from saved progress");
        }
        if (givenAdultIds.removeIf(id -> !isAdultTreaterId(id))) {
            persistNpcIds(DELIVERED_ADULT_IDS_KEY, givenAdultIds);
            log.warn("Halloween discarded invalid adult-delivery NPC IDs from saved progress");
            adultsGiven = Math.min(adultsGiven, givenAdultIds.size());
            configManager.setConfiguration(GROUP, ADULTS_KEY, adultsGiven);
        }
        configManager.unsetConfiguration(GROUP, LEGACY_TREATER_CHILD_GROUP_KEY);
        childrenReportConfirmed = Boolean.parseBoolean(
                configManager.getConfiguration(GROUP, CHILD_CONFIRMED_KEY));
        String savedRetries = configManager.getConfiguration(GROUP, CHILD_RETRY_COUNT_KEY);
        try {
            childSpookRetries = savedRetries == null ? 0
                    : Math.max(0, Math.min(2, Integer.parseInt(savedRetries)));
        } catch (NumberFormatException ignored) {
            childSpookRetries = 0;
        }
        log.info("Halloween saved NPC progress | attemptedChildren={} adultIds={} delivered={}",
                spookedChildren.size(), givenAdultIds.size(), adultsGiven);

        Stage saved = savedStage();
        if (saved == Stage.COMPLETED) {
            change(Stage.COMPLETED, "Previously confirmed Halloween completion");
            // A completed event must not restart on a later client launch.
            // Leave the side panel available for an explicit progress reset.
            active = false;
            status = "Event already complete; reset progress to start another run";
            return false;
        } else if (hasUniqueCandyObjectiveInventory()) {
            // Event-exclusive items are direct evidence that this character
            // has reached the adult-candy subtask. Do not force a pointless
            // walk to Angel/Henja when the required items are already held.
            Stage next = stageForAdultCandyObjective();
            change(next, "Recovered Halloween candy objective from live inventory"
                    + " (oldInion=" + oldInionCount()
                    + " candy=" + Rs2Inventory.count("Candy onion", true)
                    + " molten=" + Rs2Inventory.hasItem("Molten sugar") + ")");
        } else {
            // Without event-exclusive item evidence, Angel/Henja remain
            // authoritative for whether the child/lantern tasks are complete.
            change(Stage.INTRODUCTION,
                    "Synchronizing Halloween event state with Angel / Henja");
            log.info("Halloween saved stage {} deferred to starter dialogue", saved);
        }
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(
                this::safeTick, 0L, 150L, TimeUnit.MILLISECONDS);
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
        if (!active) return;
        try {
            // Complete even if the user paused just before the final scroll.
            if (checkForEventComplete()) return;
            if (paused) return;
            tick();
        }
        catch (Exception e) {
            log.warn("Halloween stage={} player={} failed", stage, Rs2Player.getWorldLocation(), e);
            stopForReview("Event API or interface changed; inspect client logs");
        }
    }

    /**
     * The event scroll is the final authority. The reward inventory and a
     * completion chat message are NOT sufficient to stop the script early.
     * Exact widget and text prevent unrelated quest scrolls from triggering.
     * Called both from the normal loop and GameTick (including while paused).
     */
    public boolean checkForEventComplete() {
        if (!active || completionSignaled.get()) return completionSignaled.get();
        boolean complete = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (Microbot.getClient() == null) return false;
            Widget congratulations = Microbot.getClient().getWidget(
                    EVENT_COMPLETE_GROUP, EVENT_COMPLETE_CHILD);
            if (congratulations == null || congratulations.isHidden()) return false;
            String text = congratulations.getText();
            return text != null && text.replaceAll("<[^>]*>", "").trim()
                    .equalsIgnoreCase("Event complete!");
        }).orElse(false);
        if (!complete || !completionSignaled.compareAndSet(false, true)) return false;

        log.info("Halloween completion widget confirmed | widgetId={} text=Event complete!",
                EVENT_COMPLETE_WIDGET_ID);
        change(Stage.COMPLETED, "Event complete! screen confirmed; stopping event");
        // The owning plugin schedules shutdown on Swing's EDT to remove its
        // overlay and cancel the script without stopping on the client thread.
        Runnable callback = completionCallback;
        if (callback != null) callback.run();
        return true;
    }

    private boolean handleCutscene() {
        // RuneLite's CUTSCENE_STATUS varbit 542, exposed by Microbot.
        if (!Rs2Dialogue.isInCutScene()) return false;
        // Cutscenes must not be interrupted by walking, inventory use, NPC
        // interactions, or generic stage reconciliation.
        if (Rs2Dialogue.hasContinue()) {
            String signature = "cutscene|continue|" + Rs2Dialogue.getDialogueText();
            if (canDispatchDialogue(signature)) {
                Rs2Dialogue.clickContinue();
                dialogueActed("Advancing dialogue during Halloween cutscene", signature);
            }
        } else if (Rs2Dialogue.hasSelectAnOption()) {
            String question = Rs2Dialogue.getQuestion();
            String normalized = question == null ? "" : question.toLowerCase(Locale.ROOT);
            // Never choose Yes to a 'skip cutscene' question.
            if (normalized.contains("skip")) {
                String signature = "cutscene|do-not-skip|" + normalized;
                if (canDispatchDialogue(signature)
                        && Rs2Dialogue.clickOption("No", true)) {
                    dialogueActed("Declining to skip Halloween cutscene", signature);
                } else {
                    status = "Cutscene skip prompt: waiting to decline skip safely";
                }
            } else {
                handleDialogueOption();
            }
        } else {
            status = "Watching Halloween cutscene; no gameplay actions";
        }
        return true;
    }

    private void tick() {
        if (checkForEventComplete()) return;
        if (!Microbot.isLoggedIn()) { status = "Waiting for login"; return; }
        // This gate must precede inventory reconciliation, NPC navigation,
        // and player-location reads. Only explicit cutscene dialogue is acted on.
        if (handleCutscene()) return;
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null) { status = "Waiting for player location"; return; }
        if (stage == Stage.COMPLETED || stage == Stage.NEEDS_REVIEW) return;

        // An observed inventory transition is a stronger signal for the
        // current candy subtask than a persisted local stage. Never redirect
        // away from an active dialogue or an unconfirmed item-use action.
        if (isCandyStage(stage) && !starterConversationActive
                && pendingOldOnionCount < 0 && pendingCoatCandyCount < 0
                && pendingCandyCount < 0 && !pendingCook) {
            Stage correct = stageForAdultCandyObjective();
            if (correct != stage) {
                change(correct, "Inventory reconciliation: oldInion=" + oldInionCount()
                        + " candy=" + Rs2Inventory.count("Candy onion", true)
                        + " adultsGiven=" + adultsGiven
                        + " molten=" + Rs2Inventory.hasItem("Molten sugar"));
                return;
            }
        }

        // Check the Chemist's free-text input BEFORE generic Continue/option
        // handling. A riddle answer field is not a selectable "92" option.
        if (stage == Stage.CHEMIST_RIDDLE && handleChemistAnswerPrompt()) return;

        boolean dialogueVisible = Rs2Dialogue.hasContinue() || Rs2Dialogue.hasSelectAnOption();
        String currentStarterText = "";
        if (starterConversationActive) {
            // The RuneLite dialogue helpers sometimes report no Continue,
            // even while NPC text is visibly rendered in the chatbox.
            List<String> samples = starterDialogueSamples();
            currentStarterText = String.join(" | ", samples);
            dialogueVisible = dialogueVisible || !samples.isEmpty();
            if (dialogueVisible) {
                starterInterfaceSeen = true;
                starterNoDialogueTicks = 0;
                inspectStarterDialogue(samples);
                if (stage == Stage.NEEDS_REVIEW || stage == Stage.COMPLETED) return;
            }
        }

        if (Rs2Dialogue.hasContinue()) {
            if (stage == Stage.CHEMIST_RIDDLE) clearChemistOptionPending();
            String signature = stage + "|continue|" + (starterConversationActive
                    ? currentStarterText : Rs2Dialogue.getDialogueText());
            if (canDispatchDialogue(signature)) {
                Rs2Dialogue.clickContinue();
                dialogueActed("Advancing " + stage + " dialogue", signature);
            }
            return;
        }

        if (Rs2Dialogue.hasSelectAnOption()) {
            handleDialogueOption();
            return;
        }

        // If the text widget is visible but Microbot misses its Continue
        // component, advance once per observed text state via canvas keyboard.
        // This avoids freezing on a valid Angel/Henja dialogue variant.
        if (starterConversationActive && dialogueVisible) {
            String signature = stage + "|starter-visible|" + currentStarterText;
            if (canDispatchDialogue(signature)) {
                Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                dialogueActed("Advancing visible Angel / Henja dialogue", signature);
            }
            return;
        }

        // The interface really disappeared: do not carry a stale dialogue
        // signature into the next separate NPC or object interaction.
        lastGenericDialogueSignature = "";
        lastGenericDialogueAttempts = 0;

        // Angel/Henja are authoritative for the event objective. Never
        // resume a preselected stage just because their dialogue closed.
        if (starterConversationActive) {
            // Consecutive absent widget snapshots distinguish a conversation
            // close from the short frame where one dialogue page replaces another.
            if (starterInterfaceSeen && ++starterNoDialogueTicks >= 2) {
                starterConversationActive = false;
                starterInterfaceSeen = false;
                if (pendingStarterNext != null) {
                    Stage next = pendingStarterNext;
                    pendingStarterNext = null;
                    pendingStarterDialogueSeen = false;
                    starterTalkAttempts = 0;
                    change(next, "Angel / Henja dialogue resolved event state");
                } else {
                    log.warn("Halloween Angel/Henja conversation closed without a classified"
                                    + " objective | attempt={}/{} text={} lastWidgetText={}"
                                    + " textCaptured={}",
                            starterTalkAttempts, MAX_STARTER_TALK_ATTEMPTS,
                            lastStarterDialogue, lastStarterObservedWidgetText,
                            pendingStarterDialogueSeen);
                    status = "Rechecking Angel / Henja; dialogue objective not yet confirmed";
                }
            } else if (!starterInterfaceSeen
                    && System.currentTimeMillis() - pendingStarterAt > 8_000L) {
                log.warn("Halloween starter Talk-to had no dialogue widgets | stage={}"
                                + " attempt={}/{}",
                        stage, starterTalkAttempts, MAX_STARTER_TALK_ATTEMPTS);
                starterConversationActive = false;
                status = "Retrying Angel / Henja: no dialogue widgets appeared";
            } else {
                status = "Reading Angel / Henja dialogue widgets";
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
                // No more NPC clicks or walking after the final dialogue.
                // A reward may arrive before the cutscene finishes; only
                // Eventscroll.CONGRATULATIONS ends the plugin.
                status = "Waiting for Event complete! scroll (807:5)";
                break;
            default:
                break;
        }
    }

    private static boolean isCandyStage(Stage value) {
        return value == Stage.CHEMIST_RIDDLE || value == Stage.COOK_SUGAR
                || value == Stage.COLLECT_ONIONS || value == Stage.COAT_ONIONS
                || value == Stage.GIVE_CANDY;
    }

    private void handleDialogueOption() {
        // Enumerate unknown choices for accurate follow-up debugging.
        List<String> options = Rs2Dialogue.getDialogueOptions().stream()
                .map(w -> w == null ? "" : w.getText())
                .collect(Collectors.toList());
        String signature = stage + "|" + Rs2Dialogue.getQuestion() + "|" + options;
        // Never replay an unchanged option menu while the client is
        // processing it. A changed widget/question is immediately actionable.
        if (stage == Stage.CHEMIST_RIDDLE) {
            if (handleChemistDialogueOption(options)) return;
            if (System.currentTimeMillis() - lastDiagnosticAt > 5_000L) {
                lastDiagnosticAt = System.currentTimeMillis();
                log.warn("Halloween Chemist dialogue requires review | question={} options={}",
                        Rs2Dialogue.getQuestion(), options);
            }
            status = "Waiting for Chemist Halloween / 92 option; see dialogue logs";
            return;
        }
        if (!canDispatchDialogue(signature)) return;
        if (stage == Stage.GET_COSTUME
                && Rs2Dialogue.keyPressForDialogueOption(
                        "I'd like to check holiday items please!", true)) {
            dialogueActed("Selected Diango's holiday-item retrieval dialogue", signature);
            return;
        }

        if ((stage == Stage.REPORT_LANTERNS || stage == Stage.GET_COSTUME
                || stage == Stage.VERIFY_LANTERNS)
                && Rs2Dialogue.keyPressForDialogueOption("Sure, I'll take the hat.", true)) {
            dialogueActed("Accepted Halloween hat from Angel / Henja", signature);
            return;
        }

        String[] choices = {
            "Yes", "Sure", "I'd like to help", "Of course", "Let's do it",
            "I'll help", "I can help", "Okay", "Halloween", "Trick or treat"
        };
        for (String choice : choices) {
            if (Rs2Dialogue.clickOption(choice, true)) {
                dialogueActed("Selected confirmed event dialogue: " + choice, signature);
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
        if (pendingChemistOptionAt != 0L && now - pendingChemistOptionAt < DIALOGUE_STALL_TIMEOUT_MS) {
            status = "Waiting for Chemist menu to change after "
                    + options.get(optionIndex) + " (" + pendingChemistOptionAttempts + " attempts)";
            return true;
        }
        if (pendingChemistOptionAttempts >= 6) {
            stopForReview("Chemist option was selected six times without a dialogue change: "
                    + options.get(optionIndex) + "; options=" + options);
            return true;
        }
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
            chemistConversationAt = now;
            status = "Selected Chemist " + options.get(optionIndex)
                    + " via " + (viaWidget ? "option widget" : "option keybinding")
                    + "; awaiting dialogue change";
            log.info("Halloween dialogue action | stage={} | {}", stage, status);
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

    /**
     * RuneLite chatbox input widgets can display a text prompt without
     * {@link Rs2Dialogue#hasSelectAnOption()}. Read one client-thread
     * snapshot and only type during this plugin's recent Chemist conversation.
     */
    private boolean handleChemistAnswerPrompt() {
        long now = System.currentTimeMillis();
        if (chemistConversationAt == 0L || now - chemistConversationAt > 120_000L)
            return false;

        ChemistInputView view = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget title = Microbot.getClient().getWidget(WidgetInfo.CHATBOX_TITLE);
            Widget fullInput = Microbot.getClient().getWidget(WidgetInfo.CHATBOX_FULL_INPUT);
            Widget input = Microbot.getClient().getWidget(WidgetInfo.CHATBOX_INPUT);
            return new ChemistInputView(isVisible(title), isVisible(fullInput),
                    isVisible(input), widgetText(title), widgetText(fullInput));
        }).orElse(null);
        if (view == null) return false;

        // Never mistake an NPC Continue or selectable choice for a
        // free-text answer field, even when a chatbox text widget is visible.
        if (Rs2Dialogue.hasContinue() || Rs2Dialogue.hasSelectAnOption())
            return false;

        boolean prompt = KspHalloweenChemistInput.isAnswerPrompt(
                view.titleVisible, view.fullInputVisible, view.inputVisible,
                view.titleText, view.fullInputText);

        if (!prompt) {
            if (chemistAnswerSubmittedAt != 0L) {
                log.info("Halloween Chemist answer input closed | submittedAgoMs={} sugar={}"
                        + " moltenSugar={}",
                        now - chemistAnswerSubmittedAt,
                        Rs2Inventory.count("Sugar"), Rs2Inventory.count("Molten sugar"));
                clearChemistTextPrompt();
                // Give the Chemist response time to arrive before retrying.
                lastActionAt = now;
                status = "Chemist answer submitted; checking for sugar or further dialogue";
                return true;
            }
            return false;
        }

        if (!chemistAnswerPromptObserved) {
            chemistAnswerPromptObserved = true;
            log.info("Halloween Chemist riddle text input detected | title={} body={}"
                            + " fullInputVisible={} inputVisible={}",
                    view.titleText, view.fullInputText,
                    view.fullInputVisible, view.inputVisible);
        }

        if (chemistAnswerSubmittedAt != 0L) {
            if (now - chemistAnswerSubmittedAt >= 12_000L) {
                stopForReview("Chemist riddle input still visible after submitting 92;"
                        + " inspect chatbox and logs before retrying");
            } else {
                status = "Waiting for Chemist to accept submitted answer 92";
            }
            return true;
        }
        if (chemistAnswerSubmissions >= 1) {
            stopForReview("Chemist text prompt reopened after submitting 92;"
                    + " inspect game dialogue before another submission");
            return true;
        }
        // One submission per observed prompt. Do not type additional digits
        // while the same input is visible; prevent accidental 9292 responses.
        chemistAnswerSubmittedAt = now;
        chemistAnswerSubmissions++;
        Rs2Keyboard.typeString("92");
        Rs2Keyboard.enter();
        status = "Submitted Chemist COFFIN riddle answer 92; awaiting game response";
        log.info("Halloween dialogue action | stage={} | {}", stage, status);
        return true;
    }

    private static boolean isVisible(Widget widget) {
        return widget != null && !widget.isHidden();
    }

    private static String widgetText(Widget widget) {
        return widget == null || widget.getText() == null ? "" : widget.getText();
    }

    private void clearChemistTextPrompt() {
        chemistAnswerSubmittedAt = 0L;
        chemistAnswerPromptObserved = false;
    }

    private static final class ChemistInputView {
        final boolean titleVisible;
        final boolean fullInputVisible;
        final boolean inputVisible;
        final String titleText;
        final String fullInputText;

        ChemistInputView(boolean titleVisible, boolean fullInputVisible,
                         boolean inputVisible, String titleText, String fullInputText) {
            this.titleVisible = titleVisible;
            this.fullInputVisible = fullInputVisible;
            this.inputVisible = inputVisible;
            this.titleText = titleText;
            this.fullInputText = fullInputText;
        }
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
        // Interact with a loaded Angel/Henja NPC immediately; distance to
        // the STARTER waypoint is irrelevant when the target is in scene.
        Rs2NpcModel henja = Rs2Npc.getNpc("Henja");
        Rs2NpcModel angel = Rs2Npc.getNpc("Angel");
        if (henja == null && angel == null
                && !nearOrWalk(player, STARTER, 8, "Angel / Henja's house"))
            return;
        if (starterTalkAttempts >= MAX_STARTER_TALK_ATTEMPTS) {
            stopForReview("Angel/Henja dialogue did not identify a known Halloween objective"
                    + " after " + starterTalkAttempts + " conversations;"
                    + " lastDialogue=" + lastStarterDialogue
                    + " widgetText=" + lastStarterObservedWidgetText);
            return;
        }
        boolean clicked = henja != null && Rs2Npc.interact(henja, "Talk-to");
        if (!clicked) clicked = angel != null && Rs2Npc.interact(angel, "Talk-to");
        if (!clicked) {
            status = "Locating Angel / Henja in their Rimmington home";
            return;
        }
        starterTalkAttempts++;
        acted("Speaking to Angel / Henja to resolve current event state"
                + " (attempt " + starterTalkAttempts + "/" + MAX_STARTER_TALK_ATTEMPTS + ")");
        lastTalkAt = System.currentTimeMillis();
        pendingStarterNext = null;
        pendingStarterAt = lastTalkAt;
        pendingStarterLastDialogueAt = 0L;
        pendingStarterDialogueSeen = false;
        starterConversationActive = true;
        starterInterfaceSeen = false;
        starterNoDialogueTicks = 0;
        // Keep last non-empty text across retries for accurate failure diagnostics.
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
        boolean newlyConfirmed = smashedLanternTiles.add(tileKey(tile));
        if (newlyConfirmed) {
            configManager.setConfiguration(GROUP, LANTERNS_KEY,
                    String.join(",", smashedLanternTiles));
            log.info("Halloween lantern smash confirmed | tile={} evidence={} count={}/8",
                    tile, evidence, getLanternCount());
        }
        clearPendingLantern();

        // A verified disappearing Smash action is sufficient to END THE
        // LOCAL SMASHING TASK, not to mark the server objective complete.
        // The original introductory dialogue sets sweepRequiredByStarter=true;
        // without this transition an actual 8/8 could continue sweeping
        // forever unless a separate chat message happened to arrive.
        if (newlyConfirmed && stage == Stage.SMASH_LANTERNS
                && smashedLanternTiles.size() >= 8) {
            sweepRequiredByStarter = false;
            change(Stage.REPORT_LANTERNS,
                    "Eight lanterns confirmed locally; checking with Angel / Henja");
        }
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
        // The eighth acknowledgement may have changed the stage directly.
        // Do not overwrite REPORT_LANTERNS with a fallback sweep/recheck.
        if (stage != Stage.SMASH_LANTERNS) return;
        if (lanternsVerified) {
            change(Stage.REPORT_LANTERNS,
                    "Lantern objective already confirmed by Angel/Henja");
            return;
        }
        Rs2TileObjectModel target = lantern();
        if (target != null) {
            WorldPoint location = target.getWorldLocation();
            status = "Smashing jack-o'-lantern " + (smashedLanternTiles.size() + 1) + "/8";
            if (target.click(target.findAction("Smash"))) {
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

    private void inspectStarterDialogue(List<String> dialogueTexts) {
        if (dialogueTexts.isEmpty()) {
            if (System.currentTimeMillis() - lastDiagnosticAt > 5_000L) {
                lastDiagnosticAt = System.currentTimeMillis();
                log.warn("Halloween Angel/Henja dialogue widget is visible but no text"
                        + " could be read | stage={} continue={} choices={}",
                        stage, Rs2Dialogue.hasContinue(), Rs2Dialogue.hasSelectAnOption());
            }
            return; // A visible menu is not itself proof of dialogue content.
        }

        pendingStarterDialogueSeen = true;
        pendingStarterLastDialogueAt = System.currentTimeMillis();
        String observed = String.join(" | ", dialogueTexts);
        if (!observed.equals(lastStarterObservedWidgetText)) {
            lastStarterObservedWidgetText = observed;
            log.info("Halloween Angel/Henja dialogue evidence | stage={} text={}",
                    stage, observed);
        }

        String raw = null;
        KspHalloweenStarterState.Objective objective =
                KspHalloweenStarterState.Objective.UNKNOWN;
        for (String text : dialogueTexts) {
            // Prefer explicit statements on individual dialogue widgets.
            KspHalloweenStarterState.Objective classified =
                    KspHalloweenStarterState.classify(text);
            if (classified != KspHalloweenStarterState.Objective.UNKNOWN) {
                raw = text;
                objective = classified;
                break;
            }
        }
        if (objective == KspHalloweenStarterState.Objective.UNKNOWN) {
            // An event sentence can wrap across several chatbox widget fields.
            raw = String.join(" ", dialogueTexts);
            objective = KspHalloweenStarterState.classify(raw);
        }
        if (objective == KspHalloweenStarterState.Objective.UNKNOWN) {
            lastStarterDialogue = observed;
            if (System.currentTimeMillis() - lastDiagnosticAt > 5_000L) {
                lastDiagnosticAt = System.currentTimeMillis();
                log.info("Halloween Angel/Henja state dialogue not yet classified"
                        + " | stage={} text={}", stage, observed);
            }
            return;
        }
        lastStarterDialogue = raw;
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
                                + childSpookRetries + " reports. Verified disappearances="
                                + spookedChildren.size() + "; inspect child availability"
                                + " and dialogue");
                        return;
                    }
                    if (spookedChildren.size() >= CHILD_REPORT_CHECKPOINT) {
                        spookedChildren.clear();
                        clearPendingChildSpook();
                        childSpookCooldownUntil.clear();
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
                // A dialogue statement can appear while the cutscene or
                // reward screen is still pending: keep waiting for widget 807:5.
                resolved = Stage.WAIT_FOR_REWARD;
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

    /**
     * Read the actual RuneLite dialogue widgets, rather than relying on
     * Rs2Dialogue.getDialogueText(): that helper checks only three legacy
     * child IDs and can return empty for a visible NPC dialogue.
     *
     * Only examine currently visible dialogue groups while a starter
     * conversation initiated by this script is active.
     */
    private List<String> starterDialogueSamples() {
        List<String> captured = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            List<String> result = new ArrayList<>();
            int[] groups = {
                InterfaceID.DIALOG_NPC,
                InterfaceID.DIALOG_PLAYER,
                InterfaceID.DIALOG_SPRITE,
                InterfaceID.DIALOG_DOUBLE_SPRITE,
                InterfaceID.DIALOG_OPTION
            };
            for (int group : groups) {
                collectVisibleDialogueText(Microbot.getClient().getWidget(group, 0),
                        result, 0);
            }

            // Some dialogue layouts have no useful group-0 root.
            collectVisibleDialogueText(Microbot.getClient().getWidget(
                    WidgetInfo.DIALOG_NPC_TEXT), result, 0);
            collectVisibleDialogueText(Microbot.getClient().getWidget(
                    WidgetInfo.DIALOG_PLAYER_TEXT), result, 0);
            collectVisibleDialogueText(Microbot.getClient().getWidget(
                    WidgetInfo.DIALOG_SPRITE_TEXT), result, 0);
            collectVisibleDialogueText(Microbot.getClient().getWidget(
                    WidgetInfo.DIALOG_DOUBLE_SPRITE_TEXT), result, 0);
            collectVisibleDialogueText(Microbot.getClient().getWidget(
                    WidgetInfo.DIALOG_OPTION_OPTIONS), result, 0);
            return result;
        }).orElse(Collections.emptyList());

        // Keep Microbot helpers as additional sources, never the only source.
        List<String> all = new ArrayList<>(captured);
        addStarterDialogueText(all, Rs2Dialogue.getDialogueText());
        if (Rs2Dialogue.hasSelectAnOption())
            addStarterDialogueText(all, Rs2Dialogue.getQuestion());
        return all;
    }

    private static void collectVisibleDialogueText(Widget widget,
                                                    List<String> output, int depth) {
        if (widget == null || widget.isHidden() || depth > 3
                || output.size() >= 40) return;
        addStarterDialogueText(output, widget.getText());
        collectDialogueChildren(widget.getStaticChildren(), output, depth + 1);
        collectDialogueChildren(widget.getDynamicChildren(), output, depth + 1);
        collectDialogueChildren(widget.getNestedChildren(), output, depth + 1);
    }

    private static void collectDialogueChildren(Widget[] children,
                                                List<String> output, int depth) {
        if (children == null) return;
        for (Widget child : children) {
            if (output.size() >= 40) break;
            collectVisibleDialogueText(child, output, depth);
        }
    }

    private static void addStarterDialogueText(List<String> output, String raw) {
        if (raw == null) return;
        String text = raw.replaceAll("(?i)<br\\s*/?>", " ")
                .replaceAll("<[^>]*>", " ")
                .replace("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (text.isEmpty() || text.equalsIgnoreCase("Click here to continue")
                || text.equalsIgnoreCase("Please wait...")
                || text.equalsIgnoreCase("Select an Option")
                || output.contains(text)) return;
        output.add(text);
    }

    private boolean hasUniqueCandyObjectiveInventory() {
        return oldInionCount() > 0
                || Rs2Inventory.count("Candy onion", true) > 0
                || Rs2Inventory.hasItem("Molten sugar")
                // Sugar alone is not proof of Halloween progress, but it
                // becomes actionable when Angel/Henja previously confirmed
                // the adult-candy objective.
                || (childrenReportConfirmed && Rs2Inventory.hasItem("Sugar", true));
    }

    private Stage stageForAdultCandyObjective() {
        int candy = Rs2Inventory.count("Candy onion", true);
        int oldOnions = oldInionCount();
        boolean molten = Rs2Inventory.hasItem("Molten sugar");
        boolean sugar = Rs2Inventory.hasItem("Sugar", true);

        if (adultsGiven >= ONIONS_REQUIRED) return Stage.FINAL_TALK;
        if (candy >= ONIONS_REQUIRED - adultsGiven) return Stage.GIVE_CANDY;
        if (molten && totalOnionsAccountedFor() < ONIONS_REQUIRED)
            return Stage.COLLECT_ONIONS;
        if (molten && oldOnions > 0) return Stage.COAT_ONIONS;
        if (candy > 0) return Stage.GIVE_CANDY;
        if (sugar) return Stage.COOK_SUGAR;
        // No more cooked inputs. First get sugar, then use the exact crate.
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
        int equipped = costumeCount();
        KspDiangoHolidayRetrieval.Snapshot window =
                KspDiangoHolidayRetrieval.inspect(missingCostumeRetrievalNames());

        if (pendingEquipItem != null) {
            if (Rs2Equipment.isWearing(pendingEquipItem, true)) {
                log.info("Halloween costume equipped | item={}", pendingEquipItem);
                pendingEquipItem = null;
            } else if (System.currentTimeMillis() - pendingEquipAt
                    < WORLD_ACTION_STALL_TIMEOUT_MS) {
                status = "Waiting for equipped costume to update: " + pendingEquipItem;
                return;
            } else {
                stopForReview("Costume equip action did not update equipment: "
                        + pendingEquipItem);
                return;
            }
        }

        if (!window.open) {
            pendingHolidayClose = false;
            pendingHolidayScrollY = -1;
        } else if (pendingHolidayClose) {
            if (System.currentTimeMillis() - pendingHolidayCloseAt
                    < WORLD_ACTION_STALL_TIMEOUT_MS) {
                status = "Waiting for Diango retrieval modal to close";
                return;
            }
            pendingHolidayClose = false; // Recovery: another close attempt may be needed.
        }

        if (window.open && pendingHolidayScrollY >= 0) {
            if (window.scrollY != pendingHolidayScrollY) {
                log.info("Diango scroll acknowledged | oldY={} newY={}",
                        pendingHolidayScrollY, window.scrollY);
                pendingHolidayScrollY = -1;
            } else if (System.currentTimeMillis() - pendingHolidayScrollAt
                    < WORLD_ACTION_STALL_TIMEOUT_MS) {
                status = "Waiting for Diango item-list scroll update";
                return;
            } else {
                stopForReview("Diango scroll did not move; inspect visible retrieval items");
                return;
            }
        }

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
                    pendingEquipItem = piece;
                    pendingEquipAt = System.currentTimeMillis();
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
                    pendingHolidayScrollY = window.scrollY;
                    pendingHolidayScrollAt = System.currentTimeMillis();
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
        Rs2NpcModel diango = Rs2Npc.getNpc("Diango");
        if (diango == null) {
            nearOrWalk(player, DIANGO, 8, "Diango in Draynor Village");
            return;
        }
        if (lastTalkAt == 0L
                    || System.currentTimeMillis() - lastTalkAt
                        >= WORLD_ACTION_STALL_TIMEOUT_MS) {
            if (Rs2Npc.interact(diango, "Holiday-items")) {
                lastTalkAt = System.currentTimeMillis();
                acted("Opening Diango to collect missing costume pieces");
                return;
            }
            if (Rs2Npc.interact(diango, "Talk-to")) {
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
        pendingHolidayClose = true;
        pendingHolidayCloseAt = System.currentTimeMillis();
        acted("Closing Diango after full four-slot costume batch");
    }

    private boolean npcHasAction(Rs2NpcModel npc, String action) {
        if (npc == null || action == null) return false;
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (Microbot.getClient() == null) return false;
            // Transformed definitions expose the live context menu for an
            // event NPC; the base definition can be a different variant.
            net.runelite.api.NPCComposition def = npc.getTransformedComposition();
            if (def == null) def = Microbot.getClient().getNpcDefinition(npc.getId());
            if (def == null || def.getActions() == null) return false;
            return Arrays.stream(def.getActions()).anyMatch(a ->
                    a != null && a.equalsIgnoreCase(action));
        }).orElse(false);
    }

    private static boolean isChildTreaterId(int npcId) {
        return npcId >= CHILD_TREATER_FIRST_ID && npcId <= CHILD_TREATER_LAST_ID;
    }

    private static boolean isAdultTreaterId(int npcId) {
        return npcId >= ADULT_TREATER_FIRST_ID && npcId <= ADULT_TREATER_LAST_ID;
    }

    private static boolean isKnownTreaterId(int npcId) {
        return isChildTreaterId(npcId) || isAdultTreaterId(npcId);
    }

    private TreaterAge classifyTreater(Rs2NpcModel npc) {
        if (npc == null) return TreaterAge.UNKNOWN;
        if (isChildTreaterId(npc.getId())) return TreaterAge.CHILD;
        if (isAdultTreaterId(npc.getId())) return TreaterAge.ADULT;
        return TreaterAge.UNKNOWN;
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

    private enum ChildSceneStatus { PRESENT, ABSENT, UNKNOWN }

    private void clearPendingChildSpook() {
        pendingChildId = -1;
        pendingChildIndex = -1;
        pendingChildTile = null;
        pendingChildSpookAt = 0L;
        pendingChildAbsentSince = 0L;
    }

    /**
     * Read the actual RuneLite WorldView NPC collection, not a cached list or
     * a menu action. Index identifies the exact actor clicked; ID identifies
     * the specific child definition. An index reused or morphed while the
     * same actor slot remains populated does not count as disappearance.
     */
    private ChildSceneStatus pendingChildLiveSceneStatus(WorldPoint player) {
        if (pendingChildIndex < 0 || pendingChildTile == null || player == null
                || player.getPlane() != pendingChildTile.getPlane()
                || player.distanceTo(pendingChildTile) > 10) {
            return ChildSceneStatus.UNKNOWN;
        }
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (Microbot.getClient() == null
                    || Microbot.getClient().getTopLevelWorldView() == null
                    || Microbot.getClient().getTopLevelWorldView().npcs() == null
                    || Microbot.getClient().getLocalPlayer() == null) {
                return ChildSceneStatus.UNKNOWN;
            }
            for (net.runelite.api.NPC npc : Microbot.getClient()
                    .getTopLevelWorldView().npcs()) {
                if (npc != null && npc.getIndex() == pendingChildIndex) {
                    return ChildSceneStatus.PRESENT;
                }
            }
            return ChildSceneStatus.ABSENT;
        }).orElse(ChildSceneStatus.UNKNOWN);
    }

    /**
     * Called before attempting any new child. A single missing snapshot is
     * not reliable: the same actor must remain absent for several polls,
     * with its last observed tile still near the player.
     */
    private boolean verifyPendingChildSpook(WorldPoint player) {
        if (pendingChildId < 0) return false;
        final long now = System.currentTimeMillis();
        ChildSceneStatus observed = pendingChildLiveSceneStatus(player);
        if (observed == ChildSceneStatus.ABSENT) {
            if (pendingChildAbsentSince == 0L) pendingChildAbsentSince = now;
            if (now - pendingChildAbsentSince >= CHILD_SPOOK_ABSENCE_STABLE_MS
                    && now - pendingChildSpookAt >= CHILD_SPOOK_ABSENCE_STABLE_MS) {
                final int confirmedId = pendingChildId;
                final int confirmedIndex = pendingChildIndex;
                spookedChildren.add(confirmedId);
                persistNpcIds(SPOOKED_CHILD_IDS_KEY, spookedChildren);
                clearPendingChildSpook();
                childSpookCooldownUntil.remove(confirmedId);
                childNoTargetSince = 0L;
                // The confirmation represents an objective-level improvement,
                // not merely a successful click submission.
                childSpookRetries = 0;
                configManager.unsetConfiguration(GROUP, CHILD_RETRY_COUNT_KEY);
                log.info("Halloween child scare LIVE SCENE CONFIRMED | childNpcId={}"
                        + " index={} confirmed={}/{}",
                        confirmedId, confirmedIndex, spookedChildren.size(),
                        CHILD_REPORT_CHECKPOINT);
                status = "Scare confirmed by child disappearing: "
                        + spookedChildren.size() + "/" + CHILD_REPORT_CHECKPOINT;
                return false;
            }
        } else {
            pendingChildAbsentSince = 0L;
        }

        // UNKNOWN can be transient while walking toward an NPC or while
        // RuneLite swaps scene snapshots. Wait for the bounded timeout rather
        // than retrying the same interaction immediately.
        if (now - pendingChildSpookAt >= CHILD_SPOOK_CONFIRM_TIMEOUT_MS) {
            final int failedId = pendingChildId;
            log.warn("Halloween child scare NOT confirmed | childNpcId={}"
                    + " index={} tile={} liveScene={} elapsedMs={}"
                    + " (not counting; retry after cooldown)",
                    pendingChildId, pendingChildIndex, pendingChildTile,
                    observed, now - pendingChildSpookAt);
            clearPendingChildSpook();
            childSpookCooldownUntil.put(failedId,
                    now + CHILD_SPOOK_RETRY_COOLDOWN_MS);
            return false;
        }

        status = "Waiting for child " + pendingChildId
                + " to disappear from live scene (" + observed + ")";
        return true;
    }

    private void spookChildren(WorldPoint player) {
        if (costumeCount() < 4) {
            clearPendingChildSpook();
            change(Stage.GET_COSTUME, "Need four Halloween outfit pieces equipped");
            return;
        }
        if (!RIMMINGTON.contains(player)
                && !nearOrWalk(player, STARTER, 12, "child trick-or-treaters")) return;

        // Never issue a second Spook while the prior actor is awaiting
        // confirmation. This prevents five rapid clicks on unresolved actions.
        if (verifyPendingChildSpook(player)) return;
        if (spookedChildren.size() >= CHILD_REPORT_CHECKPOINT) {
            childrenReportConfirmed = false;
            change(Stage.REPORT_CHILDREN,
                    "Five child NPC disappearances confirmed by RuneLite live scene");
            return;
        }

        List<Rs2NpcModel> visibleTreaters = Rs2Npc.getNpcs()
                .filter(n -> isKnownTreaterId(n.getId()))
                .collect(Collectors.toList());

        long now = System.currentTimeMillis();
        Rs2NpcModel child = visibleTreaters.stream()
                .filter(n -> isChildTreaterId(n.getId())
                        && !spookedChildren.contains(n.getId())
                        && npcHasAction(n, "Spook")
                        && childSpookCooldownUntil.getOrDefault(n.getId(), 0L) <= now
                        && n.getWorldLocation() != null)
                .min(Comparator.comparingInt(n ->
                        player.distanceTo(n.getWorldLocation())))
                .orElse(null);

        if (child != null) {
            childNoTargetSince = 0L;
            // Keep the NPC's last position in the active scene until the
            // disappearance is verified. Do not begin from an unloaded tile.
            if (player.distanceTo(child.getWorldLocation()) > 9) {
                nearOrWalk(player, child.getWorldLocation(), 6,
                        "unspooked child trick-or-treater");
                return;
            }
            // Take a stable identity and position BEFORE invoking the action.
            // Action dispatch alone is never evidence of a successful scare.
            int id = child.getId();
            int index = child.getIndex();
            WorldPoint tile = child.getWorldLocation();
            if (Rs2Npc.interact(child, "Spook")) {
                pendingChildId = id;
                pendingChildIndex = index;
                pendingChildTile = tile;
                pendingChildSpookAt = now;
                pendingChildAbsentSince = 0L;
                acted("Spook dispatched | childNpcId=" + id
                        + " index=" + index + " awaiting live-scene disappearance"
                        + " confirmed=" + spookedChildren.size()
                        + "/" + CHILD_REPORT_CHECKPOINT);
            }
            return;
        }

        // Scene updates may lag behind movement or an NPC's despawn.
        // Do not report partial *attempts* to Angel/Henja: those dialogs
        // can only tell us the same child objective is still active.
        if (Rs2Player.isMoving()) {
            status = "Waiting for movement to finish before checking child NPCs";
            return;
        }
        if (childNoTargetSince == 0L) childNoTargetSince = now;

        if (scoutVisibleTreaters(player, visibleTreaters)) return;

        if (now - lastDiagnosticAt >= 10_000L) {
            lastDiagnosticAt = now;
            String sceneDetails = visibleTreaters.stream()
                    .map(n -> n.getId() + ":" + classifyTreater(n)
                            + ":spook=" + npcHasAction(n, "Spook")
                            + ":tile=" + n.getWorldLocation())
                    .collect(Collectors.joining(", "));
            log.info("Halloween waiting for remaining child NPCs in live scene"
                    + " | confirmedIds={} visibleTreaters=[{}] scoutedNpcTiles={}"
                    + " (no premature Angel/Henja report)",
                    spookedChildren, sceneDetails, childScoutedNpcTiles);
        }
        status = "Waiting for unspooked child NPCs: "
                + spookedChildren.size() + "/" + CHILD_REPORT_CHECKPOINT;
    }


    private void solveChemist(WorldPoint player) {
        if (Rs2Inventory.hasItem("Sugar", true)
                || Rs2Inventory.hasItem("Molten sugar")) {
            change(Stage.COOK_SUGAR, "Chemist sugar acquired");
            return;
        }
        Rs2NpcModel chemist = Rs2Npc.getNpc("Chemist");
        if (chemist == null) {
            nearOrWalk(player, CHEMIST, 8, "the Chemist west of Rimmington");
            return;
        }
        // One Talk-to per observed conversation. The watchdog allows a
        // retry only when no dialogue/input or sugar appeared.
        long now = System.currentTimeMillis();
        if (chemistConversationAt != 0L
                && now - chemistConversationAt < WORLD_ACTION_STALL_TIMEOUT_MS) {
            status = "Waiting for Chemist dialogue or sugar";
            return;
        }
        if (Rs2Npc.interact(chemist, "Talk-to")) {
            lastTalkAt = System.currentTimeMillis();
            chemistConversationAt = lastTalkAt;
            clearChemistTextPrompt();
            chemistAnswerSubmissions = 0;
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
        if (pendingCook && System.currentTimeMillis() - pendingCookAt
                < WORLD_ACTION_STALL_TIMEOUT_MS) {
            status = "Waiting for sugar-to-molten-sugar inventory transition";
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
        if (pendingCook) {
            if (System.currentTimeMillis() - pendingCookAt < WORLD_ACTION_STALL_TIMEOUT_MS) {
                status = "Waiting for sugar to become molten sugar";
                return;
            }
            pendingCook = false;
            if (++pendingCookFailures >= MAX_UNCONFIRMED_ACTIONS) {
                stopForReview("Cooking sugar failed without inventory change after "
                        + pendingCookFailures + " attempts");
                return;
            }
        }
        net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel sugar =
                Rs2Inventory.get("Sugar", true);
        if (sugar != null && Rs2Inventory.useItemOnObject(sugar.getId(), surface.getId())) {
            pendingCook = true;
            pendingCookAt = System.currentTimeMillis();
            acted("Cooking sugar at " + surface.getName() + "; awaiting molten sugar");
        }
    }

    // Exact names observed in the live 2026 event. The alternate item
    // spelling is included for clients whose item definitions use the wiki name.
    private static final String OLD_ONION_CRATE_NAME = "Crate of onions";
    private static final String[] OLD_ONION_ITEM_NAMES = {"Old inion", "Old onion"};
    private static final int ONIONS_REQUIRED = 5;
    private static final int OLD_ONION_CRATE_SEARCH_RADIUS = 20;
    private static final long OLD_ONION_CRATE_MISSING_REVIEW_MS = 12_000L;

    private int oldInionCount() {
        int total = 0;
        for (String itemName : OLD_ONION_ITEM_NAMES)
            total += Rs2Inventory.count(itemName, true);
        return total;
    }

    private String carriedOldInionName() {
        for (String itemName : OLD_ONION_ITEM_NAMES) {
            if (Rs2Inventory.count(itemName, true) > 0) return itemName;
        }
        return null;
    }

    /** Consumed old onions still count: candy in inventory and verified deliveries. */
    private int totalOnionsAccountedFor() {
        return oldInionCount() + Rs2Inventory.count("Candy onion", true) + adultsGiven;
    }

    private Rs2TileObjectModel oldOnionCrate() {
        return Microbot.getRs2TileObjectCache().query().fromWorldView()
                .where(o -> o != null && o.getWorldLocation() != null
                        && o.getName() != null
                        && OLD_ONION_CRATE_NAME.equalsIgnoreCase(o.getName())
                        && STARTER.distanceTo(o.getWorldLocation()) <= OLD_ONION_CRATE_SEARCH_RADIUS)
                .nearestOnClientThread();
    }

    private String oldOnionCrateAction(Rs2TileObjectModel crate) {
        if (crate == null) return null;
        for (String option : new String[] {"Take", "Collect", "Search", "Pick", "Grab", "Gather"}) {
            String action = crate.findAction(option);
            if (action != null) return action;
        }
        return null;
    }

    private void collectOnions(WorldPoint player) {
        int count = oldInionCount();
        int candy = Rs2Inventory.count("Candy onion", true);
        int total = count + candy + adultsGiven;

        // Inventory is authoritative for the local collection subtask; the
        // overall adult-candy objective was established by Angel/Henja.
        if (total >= ONIONS_REQUIRED) {
            change(count > 0 ? Stage.COAT_ONIONS : Stage.GIVE_CANDY,
                    "Five onions accounted for: raw=" + count + " candy=" + candy
                            + " delivered=" + adultsGiven);
            return;
        }

        // An interaction has not succeeded merely because the crate was clicked.
        // Wait for the inventory receipt, and re-evaluate immediately on change.
        if (pendingOldOnionCount >= 0) {
            if (count > pendingOldOnionCount) {
                log.info("Halloween old inion receipt | before={} after={} total={}/{}",
                        pendingOldOnionCount, count, total, ONIONS_REQUIRED);
                pendingOldOnionCount = -1;
                pendingOldOnionFailures = 0;
            } else if (System.currentTimeMillis() - pendingOldOnionAt
                    < WORLD_ACTION_STALL_TIMEOUT_MS) {
                status = "Waiting for Old inion inventory receipt (" + total + "/5)";
                return;
            } else {
                pendingOldOnionCount = -1;
                if (++pendingOldOnionFailures >= MAX_UNCONFIRMED_ACTIONS) {
                    stopForReview("Crate of onions interaction did not produce an Old inion;"
                            + " inventory names or crate action may have changed");
                    return;
                }
            }
        }

        // PRIORITY: query and interact with the actual, loaded scene object.
        // Never walk toward STARTER first when its crate is in the canvas.
        Rs2TileObjectModel crate = oldOnionCrate();
        if (crate != null) {
            oldOnionCrateMissingSince = 0L;
            String action = oldOnionCrateAction(crate);
            if (action == null) {
                stopForReview("Crate of onions visible at " + crate.getWorldLocation()
                        + " but does not expose a known collection action");
                return;
            }
            if (crate.click(action)) {
                pendingOldOnionCount = count;
                pendingOldOnionAt = System.currentTimeMillis();
                acted("Collecting Old inion " + (total + 1) + "/5 from "
                        + OLD_ONION_CRATE_NAME + " at " + crate.getWorldLocation());
            }
            return;
        }

        // Fall back to walking only when the real crate has not loaded and
        // the player is outside the house area. No repeated webwalk to STARTER
        // while standing at/near the event house.
        if (player.distanceTo(STARTER) > OLD_ONION_CRATE_SEARCH_RADIUS) {
            oldOnionCrateMissingSince = 0L;
            nearOrWalk(player, STARTER, OLD_ONION_CRATE_SEARCH_RADIUS,
                    "the Crate of onions behind Angel/Henja's house");
            return;
        }

        if (oldOnionCrateMissingSince == 0L)
            oldOnionCrateMissingSince = System.currentTimeMillis();
        status = "Searching loaded scene for Crate of onions (no walk-to-start)";
        if (System.currentTimeMillis() - oldOnionCrateMissingSince
                >= OLD_ONION_CRATE_MISSING_REVIEW_MS) {
            stopForReview("Crate of onions not found in loaded scene near Angel/Henja;"
                    + " player=" + player + " raw=" + count
                    + " candy=" + candy + " delivered=" + adultsGiven);
        }
    }

    private void coatOnions() {
        int candy = Rs2Inventory.count("Candy onion");
        int old = oldInionCount();
        if (candy >= 5) {
            change(Stage.GIVE_CANDY, "Five candy onions ready for adults");
            return;
        }
        // A combine may consume its inputs before the output inventory cache
        // updates; treat that as pending until the candy-onion receipt appears.
        if (pendingCoatCandyCount >= 0) {
            if (candy > pendingCoatCandyCount) {
                log.info("Halloween candy onion created | before={} after={}",
                        pendingCoatCandyCount, candy);
                pendingCoatCandyCount = -1;
                pendingCoatFailures = 0;
            } else if (System.currentTimeMillis() - pendingCoatAt
                    < WORLD_ACTION_STALL_TIMEOUT_MS) {
                status = "Waiting for candy onion inventory receipt";
                return;
            } else {
                pendingCoatCandyCount = -1;
                if (++pendingCoatFailures >= MAX_UNCONFIRMED_ACTIONS) {
                    stopForReview("Coating onions did not create candy onions");
                    return;
                }
            }
        }
        if (old <= 0) {
            change(Stage.COLLECT_ONIONS, "Need remaining old onions");
            return;
        }
        if (!Rs2Inventory.hasItem("Molten sugar")) {
            change(Stage.COOK_SUGAR, "Need more molten sugar");
            return;
        }
        String oldItemName = carriedOldInionName();
        if (oldItemName != null && Rs2Inventory.combine("Molten sugar", oldItemName)) {
            pendingCoatCandyCount = candy;
            pendingCoatAt = System.currentTimeMillis();
            acted("Coating old onion with molten sugar (" + candy + "/5 prepared)");
        }
    }

    private void giveCandy(WorldPoint player) {
        int candyCount = Rs2Inventory.count("Candy onion");
        if (pendingCandyCount >= 0) {
            if (candyCount < pendingCandyCount) {
                int used = pendingCandyCount - candyCount;
                if (pendingAdultId > 0 && givenAdultIds.add(pendingAdultId)) {
                    adultsGiven = Math.min(5, adultsGiven + 1);
                    configManager.setConfiguration(GROUP, ADULTS_KEY, adultsGiven);
                    persistNpcIds(DELIVERED_ADULT_IDS_KEY, givenAdultIds);
                    log.info("Halloween candy delivery verified | adultId={} used={} total={}/5",
                            pendingAdultId, used, adultsGiven);
                }
                pendingCandyCount = -1;
                pendingAdultId = -1;
                // Inventory receipt is authoritative; proceed immediately.
            } else if (System.currentTimeMillis() - lastActionAt < WORLD_ACTION_STALL_TIMEOUT_MS) {
                status = "Waiting for candy-onion inventory receipt";
                return;
            } else {
                stopForReview("Adult candy-onion use did not consume an item;"
                        + " check NPC/dialogue before retrying");
                return;
            }
        }
        if (adultsGiven >= 5) {
            change(Stage.FINAL_TALK, "Five adult trick-or-treaters served");
            return;
        }
        if (candyCount <= 0) {
            change(Stage.COAT_ONIONS, "Prepare more candy onions for adults");
            return;
        }
        // Select ONLY verified adult IDs (16794–16798). Never depend on
        // child-group inference, model height or an unrelated Talk-to action.
        // Prefer an adult already in the live scene before any webwalking.
        List<Rs2NpcModel> visibleTreaters = Rs2Npc.getNpcs()
                .filter(n -> isKnownTreaterId(n.getId())
                        && n.getWorldLocation() != null
                        && RIMMINGTON.contains(n.getWorldLocation()))
                .collect(Collectors.toList());
        Rs2NpcModel adult = visibleTreaters.stream()
                .filter(n -> isAdultTreaterId(n.getId())
                        && !givenAdultIds.contains(n.getId()))
                .min(Comparator.comparingInt(n -> player.distanceTo(n.getWorldLocation())))
                .orElse(null);
        net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel item =
                Rs2Inventory.get("Candy onion", true);
        if (adult != null && item != null) {
            // Supersede any route to an earlier search waypoint.
            Rs2Walker.clearWalkingRoute("ksp_halloween_adult_visible");
            if (Rs2Inventory.useItemOnNpc(item.getId(), adult)) {
                pendingCandyCount = candyCount;
                pendingAdultId = adult.getId();
                acted("Giving Candy onion to verified adult npcId=" + adult.getId()
                        + " tile=" + adult.getWorldLocation()
                        + " delivered=" + adultsGiven + "/5");
                sweepsWithoutTarget = 0;
            }
            return;
        }

        // Child NPCs in the scene are not candidates for candy. If no adult
        // ID is loaded, navigate to discover another adult; never reclassify
        // the children as adults or treat them as an ambiguous blocker.
        // Only scout when no undelivered adult is in the loaded scene.
        if (Rs2Player.isMoving()) {
            status = "Waiting for movement to finish before searching for adult NPCs";
            return;
        }
        if (sweepsWithoutTarget >= RIMMINGTON_SWEEP.length) {
            stopForReview("Adult search exhausted at " + adultsGiven
                    + "/5 deliveries; visible NPC IDs="
                    + visibleTreaters.stream().map(n -> String.valueOf(n.getId()))
                            .collect(Collectors.joining(",")));
            return;
        }
        if (!RIMMINGTON.contains(player)
                && !nearOrWalk(player, STARTER, 10, "adult trick-or-treaters"))
            return;
        scanRimmington(player, "Looking for adult trick-or-treaters");
    }

    private void acted(String message) {
        lastActionAt = System.currentTimeMillis();
        status = message;
        log.info("Halloween action | stage={} | {}", stage, message);
    }

    private void clearStagePendingActions() {
        pendingCook = false;
        pendingCookAt = 0L;
        pendingCookFailures = 0;
        pendingOldOnionCount = -1;
        pendingOldOnionAt = 0L;
        pendingOldOnionFailures = 0;
        oldOnionCrateMissingSince = 0L;
        pendingCoatCandyCount = -1;
        pendingCoatAt = 0L;
        pendingCoatFailures = 0;
        pendingEquipItem = null;
        pendingEquipAt = 0L;
        pendingHolidayClose = false;
        pendingHolidayCloseAt = 0L;
        pendingHolidayScrollY = -1;
        pendingHolidayScrollAt = 0L;
    }

    private boolean canDispatchDialogue(String signature) {
        if (!signature.equals(lastGenericDialogueSignature)) {
            lastGenericDialogueSignature = signature;
            lastGenericDialogueAt = 0L;
            lastGenericDialogueAttempts = 0;
        }
        if (lastGenericDialogueAt == 0L) return true;
        if (System.currentTimeMillis() - lastGenericDialogueAt < DIALOGUE_STALL_TIMEOUT_MS)
            return false; // recovery watchdog, not a normal action delay
        if (lastGenericDialogueAttempts >= MAX_UNCONFIRMED_ACTIONS) {
            stopForReview("Unchanged dialogue after " + lastGenericDialogueAttempts
                    + " attempts; widget=" + signature);
            return false;
        }
        log.warn("Halloween dialogue did not advance; retrying | stage={} attempt={}"
                        + " signature={}",
                stage, lastGenericDialogueAttempts + 1, signature);
        return true;
    }

    private void dialogueActed(String message, String signature) {
        lastGenericDialogueAt = System.currentTimeMillis();
        lastGenericDialogueSignature = signature;
        lastGenericDialogueAttempts++;
        status = message;
        log.info("Halloween dialogue action | stage={} | {}", stage, message);
    }

    private void change(Stage next, String message) {
        Stage previous = stage;
        stage = next;
        status = message;
        if (previous != next) {
            clearChemistOptionPending();
            clearChemistTextPrompt();
            lastGenericDialogueSignature = "";
            lastGenericDialogueAt = 0L;
            lastGenericDialogueAttempts = 0;
            clearStagePendingActions();
            chemistConversationAt = 0L;
            chemistAnswerSubmissions = 0;
            Rs2Walker.clearWalkingRoute("ksp_seasonal_stage_changed");
            log.info("KSP Seasonal Events Halloween | {} -> {} | {}", previous, next, message);
            if (next != Stage.STOPPED && next != Stage.PAUSED && next != Stage.NEEDS_REVIEW)
                configManager.setConfiguration(GROUP, STAGE_KEY, next.name());
            actionsAtStage = 0;
            sweepsWithoutTarget = 0;
            sweepIndex = 0;
            if (previous == Stage.SPOOK_CHILDREN && next != Stage.SPOOK_CHILDREN)
                clearPendingChildSpook();
            if (next == Stage.SPOOK_CHILDREN) {
                childNoTargetSince = 0L;
                childScoutedNpcTiles.clear();
            }
        }
    }

    private void stopForReview(String message) {
        log.warn("Halloween review required | stage={} status={} player={} inventory(sugar/old/candy)={}/{}/{}",
                stage, message, Rs2Player.getWorldLocation(), Rs2Inventory.count("Sugar"),
                oldInionCount(), Rs2Inventory.count("Candy onion"));
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
            // Both inventory-independent evidence paths (chat and disappearance
            // of Smash at the exact loaded tile) share the same 8/8 handoff.
            // Angel/Henja still decides the actual *next* quest objective.
            confirmSmashedLantern(pendingLantern, "game smash message");
            return;
        }
        if (text.contains("you have completed the halloween event")
                || text.contains("you have completed the 2026 halloween event")) {
            log.info("Halloween completion chat observed; waiting for Event complete! widget");
            status = "Completion dialogue received; waiting for Event complete! scroll";
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
        configManager.unsetConfiguration(GROUP, LEGACY_SPOOKED_CHILD_ATTEMPTS_KEY);
        configManager.unsetConfiguration(GROUP, DELIVERED_ADULT_IDS_KEY);
        configManager.unsetConfiguration(GROUP, CHILD_RETRY_COUNT_KEY);
        configManager.unsetConfiguration(GROUP, CHILD_CONFIRMED_KEY);
        configManager.unsetConfiguration(GROUP, LEGACY_TREATER_CHILD_GROUP_KEY);
        smashedLanternTiles.clear();
        pendingLantern = null;
        lanternsVerified = false;
        spookedChildren.clear();
        clearPendingChildSpook();
        childSpookCooldownUntil.clear();
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
    /** Distinct children whose exact NPC indices disappeared from RuneLite's live scene after Spook. */
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
        clearPendingChildSpook();
        Rs2Walker.clearWalkingRoute("ksp_seasonal_stopped");
        super.shutdown();
        if (completionSignaled.get() || stage == Stage.COMPLETED) {
            stage = Stage.COMPLETED;
            status = "Event complete! Plugin stopped.";
        } else {
            stage = Stage.STOPPED;
            status = "Stopped - press Start";
        }
    }
}
