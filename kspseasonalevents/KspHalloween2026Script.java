package net.runelite.client.plugins.microbot.kspseasonalevents;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.widgets.Widget;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import java.awt.event.KeyEvent;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;
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
 * Old School RuneScape's 2026 "All Trick, No Treats" event:
 * https://oldschool.runescape.wiki/w/2026_Halloween_event
 *
 * Lanterns, children and adults come from the event's own varbits ({@link KspHalloweenProgress});
 * report steps follow the Angel/Henja dialogue. Unrecognised UI stops for review rather than guessing.
 */
@Slf4j
@Singleton
public final class KspHalloween2026Script extends Script {
    private static final String GROUP = "KspSeasonalEvents";
    // Eventscroll.CONGRATULATIONS: interface 807 child 5, text "Event complete!".
    private static final int EVENT_COMPLETE_GROUP = 807;
    private static final int EVENT_COMPLETE_CHILD = 5;
    private static final int EVENT_COMPLETE_WIDGET_ID =
            (EVENT_COMPLETE_GROUP << 16) | EVENT_COMPLETE_CHILD;
    private final AtomicBoolean completionSignaled = new AtomicBoolean();
    private volatile Runnable completionCallback;
    // The saved stage is only a hint; the varbits decide what is done.
    private static final String STAGE_KEY = "halloweenStage";
    // Saved progress from 0.2.17 and earlier: it went stale (a scared child or fed adult never comes back), so it is deleted on start.
    private static final String[] OBSOLETE_KEYS = {
        "halloweenSmashTiles", "halloweenPendingSmashTile", "halloweenLanternsVerified",
        "halloweenAdultsGiven", "halloweenChildNpcIdsConfirmed", "halloweenChildNpcIdsAttempted",
        "halloweenChildReportRetries", "halloweenChildObjectiveConfirmed",
        "halloweenAdultNpcIdsDelivered", "halloweenConfirmedChildNpcGroup"
    };
    private static final int CHILD_REPORT_CHECKPOINT = 5;
    private static final int MAX_CHILD_NPC_SCOUT_STOPS = 4;
    private static final long CHILD_SPOOK_CONFIRM_TIMEOUT_MS = 7_000L;
    private static final long CHILD_SPOOK_ABSENCE_STABLE_MS = 450L;
    private static final long CHILD_SPOOK_RETRY_COOLDOWN_MS = 2_500L;
    private static final long REWARD_SCROLL_WAIT_MS = 20_000L;
    private static final int MAX_REWARD_TALKS = 3;
    // Event NPC ids, verified live; both tasks select by NPC id only.
    private static final int CHILD_TREATER_FIRST_ID = 16778;
    private static final int CHILD_TREATER_LAST_ID = 16782;
    private static final int ADULT_TREATER_FIRST_ID = 16794;
    private static final int ADULT_TREATER_LAST_ID = 16798;
    private enum TreaterAge { CHILD, ADULT, UNKNOWN }
    private static final WorldArea RIMMINGTON = new WorldArea(2925, 3180, 85, 95, 0);
    private static final WorldPoint STARTER = new WorldPoint(2958, 3211, 0);
    private static final WorldPoint DIANGO = new WorldPoint(3081, 3248, 0);
    private static final WorldPoint CHEMIST = new WorldPoint(2930, 3211, 0);

    // Search waypoints, not spawn points: a target only counts once it is in the scene with its action.
    private static final WorldPoint[] RIMMINGTON_SWEEP = {
        new WorldPoint(2957, 3212, 0), new WorldPoint(2969, 3218, 0),
        new WorldPoint(2984, 3229, 0), new WorldPoint(2994, 3212, 0),
        new WorldPoint(2970, 3193, 0), new WorldPoint(2948, 3198, 0),
        new WorldPoint(2941, 3230, 0), new WorldPoint(2970, 3250, 0)
    };
    private static final long DIALOGUE_STALL_TIMEOUT_MS = 4_000L;
    private static final long WORLD_ACTION_STALL_TIMEOUT_MS = 6_000L;
    private static final int MAX_UNCONFIRMED_ACTIONS = 3;
    private static final long WALK_RETRY = 10_000L;
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

    private volatile KspHalloweenPace pace = KspHalloweenPace.draw(System.nanoTime());
    private final java.util.Random paceRng = new java.util.Random();
    // The event's varbits, re-read every loop; the overlay and panel show the same snapshot.
    private volatile KspHalloweenProgress progress = KspHalloweenProgress.NONE;
    private final Set<String> smashedLanternTiles = ConcurrentHashMap.newKeySet();
    private volatile boolean lanternsVerified;
    private final Map<Integer, Long> childSpookCooldownUntil = new HashMap<>();
    private int pendingChildId = -1;
    private int pendingChildIndex = -1;
    private int pendingChildrenBefore;
    private WorldPoint pendingChildTile;
    private long pendingChildSpookAt;
    private long pendingChildAbsentSince;
    private volatile boolean childrenReportConfirmed;
    private int childSpookRetries;
    private int pendingAdultsBefore;
    private int candyFailures;
    private final Map<Integer, Long> adultCooldownUntil = new HashMap<>();
    private long rewardWaitSince;
    private int rewardTalks;
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
    private int pendingCandyCount = -1;
    private int pendingAdultId = -1;
    // Costume clicks in flight: the next piece is clicked straight away, the same piece never twice in a row.
    private final Map<Integer, Long> costumeClickAt = new HashMap<>();
    private final Map<Integer, Integer> costumeClickTries = new HashMap<>();
    private static final long COSTUME_CLICK_SETTLE_MS = 1_800L;
    private static final int MAX_COSTUME_CLICK_TRIES = 3;
    private int holidayScrollAttempts;
    private int holidayCloseAttempts;
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
    private String pendingChemistOptions = "";
    private long pendingChemistOptionAt;
    private int pendingChemistOptionAttempts;
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
    private boolean pendingHolidayClose;
    private long pendingHolidayCloseAt;
    private int pendingHolidayScrollY = -1;
    private KspHalloweenCostume.Outfit costume;
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
        costumeClickAt.clear();
        costumeClickTries.clear();
        costume = null;
        holidayScrollAttempts = 0;
        holidayCloseAttempts = 0;
        clearPendingChildSpook();
        childSpookCooldownUntil.clear();
        adultCooldownUntil.clear();
        candyFailures = 0;
        rewardWaitSince = 0L;
        rewardTalks = 0;
        childrenReportConfirmed = false;
        childSpookRetries = 0;
        childScoutedNpcTiles.clear();
        childNoTargetSince = 0L;
        sweepIndex = 0;
        sweepsWithoutTarget = 0;
        actionsAtStage = 0;
        smashedLanternTiles.clear();
        lanternsVerified = false;

        for (String key : OBSOLETE_KEYS) configManager.unsetConfiguration(GROUP, key);
        pace = KspHalloweenPace.draw(System.nanoTime());
        progress = Microbot.isLoggedIn()
                ? KspHalloweenProgress.read(Microbot::getVarbitValue) : KspHalloweenProgress.NONE;
        adultsGiven = progress.adults;
        log.info("Halloween game progress at start | {}", progress);

        Stage saved = savedStage();
        Stage fromGame = progress.startStage(costumeOn());
        // Logged in, the varbits decide: the saved stage is not per account.
        if (fromGame == Stage.COMPLETED || (saved == Stage.COMPLETED && !Microbot.isLoggedIn())) {
            change(Stage.COMPLETED, "Previously confirmed Halloween completion");
            // A finished event never restarts; Reset progress in the panel starts another run.
            active = false;
            status = "Event already complete; reset progress to start another run";
            return false;
        } else if (hasUniqueCandyObjectiveInventory()) {
            // Event-only items in the inventory: go straight to the candy step.
            Stage next = stageForAdultCandyObjective();
            change(next, "Recovered Halloween candy objective from live inventory"
                    + " (oldInion=" + oldInionCount()
                    + " candy=" + Rs2Inventory.count("Candy onion", true)
                    + " molten=" + Rs2Inventory.hasItem("Molten sugar") + ")");
        } else if (fromGame != null) {
            if (fromGame == Stage.CHEMIST_RIDDLE) fromGame = stageForAdultCandyObjective();
            if (fromGame.compareTo(Stage.REPORT_LANTERNS) > 0) lanternsVerified = true;
            if (fromGame.compareTo(Stage.REPORT_CHILDREN) > 0) childrenReportConfirmed = true;
            change(fromGame, "Resuming from game state: " + progress);
        } else {
            // Otherwise Angel/Henja's dialogue says where we are.
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
            if (checkForEventComplete()) return;
            if (paused) return;
            tick();
        }
        catch (Exception e) {
            log.warn("Halloween stage={} player={} failed", stage, Rs2Player.getWorldLocation(), e);
            stopForReview("Event API or interface changed; inspect client logs");
        }
    }

    /** Only the exact "Event complete!" scroll (807:5) ends the run; checked every loop and every game tick. */
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
        // The plugin stops the script on Swing's EDT, not on the client thread.
        Runnable callback = completionCallback;
        if (callback != null) callback.run();
        return true;
    }

    private boolean handleCutscene() {
        if (!Rs2Dialogue.isInCutScene()) return false;
        rewardWaitSince = 0L;
        if (Rs2Dialogue.hasContinue()) {
            String signature = "cutscene|continue|" + Rs2Dialogue.getDialogueText();
            if (canDispatchDialogue(signature)) {
                if (!readPage(Rs2Dialogue.getDialogueText())) return true;
                Rs2Dialogue.clickContinue();
                dialogueActed("Advancing dialogue during Halloween cutscene", signature);
            }
        } else if (Rs2Dialogue.hasSelectAnOption()) {
            String question = Rs2Dialogue.getQuestion();
            String normalized = question == null ? "" : question.toLowerCase(Locale.ROOT);
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
        // Cutscenes come before any walking, clicking or stage changes.
        if (handleCutscene()) return;
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null) { status = "Waiting for player location"; return; }
        if (stage == Stage.COMPLETED || stage == Stage.NEEDS_REVIEW) return;

        // The game's varbits first: never wait for a lantern, child or adult that is already done.
        progress = KspHalloweenProgress.read(Microbot::getVarbitValue);
        adultsGiven = progress.adults;
        Stage done = progress.advance(stage);
        if (done != stage && !starterConversationActive) {
            change(done, "Game state shows this step done: " + progress);
            return;
        }

        // In the candy steps the inventory says which step is next, unless an action is still in flight.
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

        // The riddle answer is typed, so check its input box before generic dialogue handling.
        if (stage == Stage.CHEMIST_RIDDLE && handleChemistAnswerPrompt()) return;
        if (Rs2Dialogue.hasContinue()) {
            String said = Rs2Dialogue.getDialogueText();
            String lower = said == null ? "" : said.toLowerCase(Locale.ROOT);
            if (stage == Stage.CHEMIST_RIDDLE && (lower.contains("enough space") || lower.contains("make some room"))) {
                stopForReview("Inventory full: free a slot for the Chemist's sugar, then press Start");
                return;
            }
            // The reward refusal is a dialogue box, not a chat message.
            if (lower.contains("more free inventory slot")) {
                stopForReview("Inventory full: " + said.replaceAll("<[^>]*>", " ").trim() + " Then press Start.");
                return;
            }
        }

        boolean dialogueVisible = Rs2Dialogue.hasContinue() || Rs2Dialogue.hasSelectAnOption();
        String currentStarterText = "";
        if (starterConversationActive) {
            // The dialogue helpers can report no Continue while text is visible.
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
            if (stage == Stage.CHEMIST_RIDDLE) {
                clearChemistOptionPending();
                // Restart the re-talk timer on every page: his riddle is long, and the answer box must not be closed by a re-talk.
                if (chemistConversationAt != 0L) chemistConversationAt = System.currentTimeMillis();
            }
            String signature = stage + "|continue|" + (starterConversationActive
                    ? currentStarterText : Rs2Dialogue.getDialogueText());
            if (canDispatchDialogue(signature)) {
                if (!readPage(starterConversationActive ? currentStarterText : Rs2Dialogue.getDialogueText())) return;
                Rs2Dialogue.clickContinue();
                dialogueActed("Advancing " + stage + " dialogue", signature);
            }
            return;
        }

        if (Rs2Dialogue.hasSelectAnOption()) {
            handleDialogueOption();
            return;
        }

        // Microbot can miss the Continue component of visible starter dialogue: press space once per page.
        if (starterConversationActive && dialogueVisible) {
            String signature = stage + "|starter-visible|" + currentStarterText;
            if (canDispatchDialogue(signature)) {
                if (!readPage(currentStarterText)) return;
                Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                dialogueActed("Advancing visible Angel / Henja dialogue", signature);
            }
            return;
        }

        lastGenericDialogueSignature = "";
        lastGenericDialogueAttempts = 0;

        if (starterConversationActive) {
            // Two empty snapshots in a row: the conversation closed, not just a page change.
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
            case WAIT_FOR_REWARD: {
                // Only the completion scroll ends the run; the reward is claimed by talking again after the cutscene.
                long now = System.currentTimeMillis();
                if (rewardWaitSince == 0L) rewardWaitSince = now;
                if (now - rewardWaitSince < REWARD_SCROLL_WAIT_MS) {
                    status = "Waiting for Event complete! scroll (807:5)";
                    break;
                }
                if (++rewardTalks > MAX_REWARD_TALKS) {
                    stopForReview("No Event complete! scroll after " + MAX_REWARD_TALKS
                            + " more talks to Angel / Henja");
                    break;
                }
                change(Stage.FINAL_TALK, "No reward scroll yet; talking to Angel / Henja to claim it ("
                        + rewardTalks + "/" + MAX_REWARD_TALKS + ")");
                break;
            }
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
        List<String> options = Rs2Dialogue.getDialogueOptions().stream()
                .map(w -> w == null ? "" : w.getText())
                .collect(Collectors.toList());
        String signature = stage + "|" + Rs2Dialogue.getQuestion() + "|" + options;
        // Never replay an unchanged option menu while the client is processing it.
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
        if (!react()) return;
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

    /** Picks the Chemist's Halloween option; a click only counts once the menu changes. */
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

        // Alternate the option widget and its keybinding in case one is ignored.
        if (!react()) return true;
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

    /** The Chemist's typed-answer box, read in one client-thread snapshot during his conversation. */
    private boolean handleChemistAnswerPrompt() {
        long now = System.currentTimeMillis();
        if (chemistConversationAt == 0L || now - chemistConversationAt > 120_000L)
            return false;

        ChemistInputView view = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget title = Microbot.getClient().getWidget(InterfaceID.Chatbox.MES_TEXT);
            Widget fullInput = Microbot.getClient().getWidget(InterfaceID.Chatbox.MES_TEXT2);
            Widget input = Microbot.getClient().getWidget(InterfaceID.Chatbox.INPUT);
            return new ChemistInputView(Microbot.getClient().getVarcIntValue(VarClientID.MESLAYERMODE),
                    isVisible(title), isVisible(fullInput),
                    isVisible(input), widgetText(title), widgetText(fullInput));
        }).orElse(null);
        if (view == null) return false;

        // A Continue or option is never the free-text answer field.
        if (Rs2Dialogue.hasContinue() || Rs2Dialogue.hasSelectAnOption())
            return false;

        boolean prompt = KspHalloweenChemistInput.isAnswerPrompt(view.inputMode,
                view.titleVisible, view.fullInputVisible, view.inputVisible,
                view.titleText, view.fullInputText);

        if (!prompt) {
            if (chemistAnswerSubmittedAt != 0L) {
                log.info("Halloween Chemist answer input closed | submittedAgoMs={} sugar={}"
                        + " moltenSugar={}",
                        now - chemistAnswerSubmittedAt,
                        Rs2Inventory.count("Sugar"), Rs2Inventory.count("Molten sugar"));
                clearChemistTextPrompt();
                lastActionAt = now;
                status = "Chemist answer submitted; checking for sugar or further dialogue";
                return true;
            }
            return false;
        }

        if (!chemistAnswerPromptObserved) {
            chemistAnswerPromptObserved = true;
            log.info("Halloween Chemist riddle text input detected | mode={} title={} body={}"
                            + " fullInputVisible={} inputVisible={}",
                    view.inputMode, view.titleText, view.fullInputText,
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
        // One submission per prompt, so the answer can never be typed twice (9292).
        if (!react()) return true;
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
        final int inputMode;
        final boolean titleVisible;
        final boolean fullInputVisible;
        final boolean inputVisible;
        final String titleText;
        final String fullInputText;

        ChemistInputView(int inputMode, boolean titleVisible, boolean fullInputVisible,
                         boolean inputVisible, String titleText, String fullInputText) {
            this.inputMode = inputMode;
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
        // A loaded Angel/Henja is clicked straight away, wherever the player stands.
        Rs2NpcModel henja = npcNamed("Henja");
        Rs2NpcModel angel = npcNamed("Angel");
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
        if (!react()) return;
        boolean clicked = henja != null && henja.click("Talk-to");
        if (!clicked) clicked = angel != null && angel.click("Talk-to");
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

    private static Rs2NpcModel npcNamed(String name) {
        return Microbot.getRs2NpcCache().query().withName(name).nearestOnClientThread();
    }

    /** Child and adult trick-or-treaters in the loaded scene, by their verified ids. */
    private static List<Rs2NpcModel> treaters() {
        return Microbot.getRs2NpcCache().query()
                .where(n -> isKnownTreaterId(n.getId()))
                .toListOnClientThread();
    }

    private void clearPendingLantern() {
        pendingLantern = null;
        pendingLanternAt = 0L;
    }

    private void confirmSmashedLantern(WorldPoint tile, String evidence) {
        if (tile == null) return;
        boolean newlyConfirmed = smashedLanternTiles.add(tileKey(tile));
        if (newlyConfirmed) {
            log.info("Halloween lantern smash confirmed | tile={} evidence={} game={}",
                    tile, evidence, progress);
        }
        clearPendingLantern();

        // Eight confirmed smashes end the local task; Angel/Henja confirm the objective.
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
        log.info("Halloween all eight lanterns verified | evidence={} recordedTiles={}",
                evidence, smashedLanternTiles.size());
    }

    private boolean pendingLanternConfirmed(WorldPoint player) {
        if (pendingLantern == null) return false;
        // Out of scene range a missing object proves nothing: go back to the clicked tile.
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
            clearPendingLantern();
            return false;
        }
        return true;
    }

    private void smashLanterns(WorldPoint player) {
        if (!RIMMINGTON.contains(player)
                && !nearOrWalk(player, STARTER, 12, "Rimmington lanterns")) return;
        if (pendingLanternConfirmed(player)) return;
        // The eighth smash may already have moved the stage on.
        if (stage != Stage.SMASH_LANTERNS) return;
        if (lanternsVerified) {
            change(Stage.REPORT_LANTERNS,
                    "Lantern objective already confirmed by Angel/Henja");
            return;
        }
        Rs2TileObjectModel target = lantern();
        if (target != null) {
            WorldPoint location = target.getWorldLocation();
            status = "Smashing jack-o'-lantern " + (progress.lanterns + 1) + "/8";
            if (!react()) return;
            if (target.click(target.findAction("Smash"))) {
                pendingLantern = location;
                pendingLanternAt = System.currentTimeMillis();
                lastActionAt = pendingLanternAt;
                log.info("Halloween smash click | id={} tile={}", target.getId(), location);
            }
            return;
        }
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
            return;
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
            KspHalloweenStarterState.Objective classified =
                    KspHalloweenStarterState.classify(text);
            if (classified != KspHalloweenStarterState.Objective.UNKNOWN) {
                raw = text;
                objective = classified;
                break;
            }
        }
        if (objective == KspHalloweenStarterState.Objective.UNKNOWN) {
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
                lanternsVerified = false;
                sweepRequiredByStarter = true;
                resolved = Stage.SMASH_LANTERNS;
                break;

            case SCARE_CHILDREN:
                confirmAllLanterns("Angel/Henja dialogue advanced beyond lanterns");
                childrenReportConfirmed = false;
                resolved = costumeOn() ? Stage.SPOOK_CHILDREN : Stage.GET_COSTUME;
                break;

            case ADULT_CANDY:
                confirmAllLanterns("Angel/Henja dialogue confirms post-lantern objective");
                childrenReportConfirmed = true;
                childSpookRetries = 0;
                resolved = stageForAdultCandyObjective();
                break;

            case CLAIM_REWARD:
                confirmAllLanterns("Angel/Henja final dialogue confirms late event state");
                childrenReportConfirmed = true;
                resolved = Stage.WAIT_FOR_REWARD;
                break;

            case COMPLETE:
                // Said before the cutscene or reward: keep waiting for the completion scroll.
                resolved = Stage.WAIT_FOR_REWARD;
                break;

            case ALREADY_COMPLETE:
                pendingStarterNext = null;
                configManager.setConfiguration(GROUP, STAGE_KEY, Stage.COMPLETED.name());
                change(Stage.COMPLETED, "Angel / Henja say the event is already done");
                Runnable stop = completionCallback;
                if (stop != null) stop.run();
                return;

            default:
                return;
        }

        // The dialogue may never send us back into a step the varbits say is done.
        Stage gameStage = progress.advance(resolved);
        if (gameStage != resolved) {
            if (stage == gameStage && ++childSpookRetries > 2) {
                pendingStarterNext = null;
                stopForReview("Angel/Henja keep asking for " + objective + " but the game says it is done ("
                        + progress + "); inspect the dialogue");
                return;
            }
            log.info("Halloween dialogue said {} but the game says done; going to {} | {}",
                    objective, gameStage, progress);
            resolved = gameStage;
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
     * The visible dialogue text, read from the dialogue widgets themselves: Rs2Dialogue.getDialogueText()
     * checks only a few legacy children and can come back empty.
     */
    private List<String> starterDialogueSamples() {
        List<String> captured = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            List<String> result = new ArrayList<>();
            int[] groups = {
                InterfaceID.CHAT_LEFT,
                InterfaceID.CHAT_RIGHT,
                InterfaceID.OBJECTBOX,
                InterfaceID.OBJECTBOX_DOUBLE,
                InterfaceID.CHATMENU
            };
            for (int group : groups) {
                collectVisibleDialogueText(Microbot.getClient().getWidget(group, 0),
                        result, 0);
            }

            collectVisibleDialogueText(Microbot.getClient().getWidget(
                    InterfaceID.ChatLeft.TEXT), result, 0);
            collectVisibleDialogueText(Microbot.getClient().getWidget(
                    InterfaceID.ChatRight.TEXT), result, 0);
            collectVisibleDialogueText(Microbot.getClient().getWidget(
                    InterfaceID.Objectbox.TEXT), result, 0);
            collectVisibleDialogueText(Microbot.getClient().getWidget(
                    InterfaceID.ObjectboxDouble.TEXT), result, 0);
            collectVisibleDialogueText(Microbot.getClient().getWidget(
                    InterfaceID.Chatmenu.OPTIONS), result, 0);
            return result;
        }).orElse(Collections.emptyList());

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
                // Sugar only counts once every child is scared: the Chemist gives it after that.
                || ((childrenReportConfirmed || progress.childrenDone())
                    && Rs2Inventory.hasItem("Sugar", true));
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
        return Stage.CHEMIST_RIDDLE;
    }

    /** Manual recheck: ask Angel/Henja for the current objective. */
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

    /** A whole Halloween outfit is worn: the children only run from a full costume. */
    private boolean costumeOn() {
        return KspHalloweenCostume.anyWorn(Rs2Equipment::isWearing);
    }

    private static boolean held(int itemId) {
        return Rs2Inventory.hasItem(itemId) || Rs2Equipment.isWearing(itemId);
    }

    private void collectCostume(WorldPoint player) {
        long now = System.currentTimeMillis();
        KspDiangoHolidayRetrieval.Snapshot window = KspDiangoHolidayRetrieval.inspect();
        // A piece that arrived (or went on) has its misses forgotten, so a later loss starts fresh.
        costumeClickTries.keySet().removeIf(k -> k > 0 ? held(k) : Rs2Equipment.isWearing(-k));

        if (!window.open) {
            pendingHolidayClose = false;
            pendingHolidayScrollY = -1;
        } else if (pendingHolidayClose) {
            if (now - pendingHolidayCloseAt < WORLD_ACTION_STALL_TIMEOUT_MS) {
                status = "Waiting for Diango's window to close";
                return;
            }
            pendingHolidayClose = false;
        }
        if (window.open && pendingHolidayScrollY >= 0) {
            if (window.scrollY != pendingHolidayScrollY) {
                pendingHolidayScrollY = -1;
            } else if (now - pendingHolidayScrollAt < WORLD_ACTION_STALL_TIMEOUT_MS) {
                status = "Waiting for Diango's list to scroll";
                return;
            } else {
                stopForReview("Diango's item list did not scroll");
                return;
            }
        }
        if (costumeOn()) {
            if (window.open) {
                closeDiango(window);
                return;
            }
            change(Stage.SPOOK_CHILDREN, "Wearing a full Halloween outfit");
            return;
        }
        // One whole outfit: the one already mostly held, else Skeleton (top of Diango's list).
        if (costume == null || (window.open && !Arrays.stream(costume.pieces)
                .allMatch(id -> held(id) || window.lists(id)))) {
            costume = KspHalloweenCostume.choose(KspHalloween2026Script::held,
                    window.open ? window::lists : id -> true);
            if (costume == null) {
                stopForReview("Diango has no full Halloween outfit for this account");
                return;
            }
            log.info("Halloween costume | outfit={} pieces={}", costume.name, Arrays.toString(costume.pieces));
        }
        List<Integer> missing = Arrays.stream(costume.pieces).filter(id -> !held(id))
                .boxed().collect(Collectors.toList());

        if (missing.isEmpty()) {
            if (window.open) {
                closeDiango(window);
                return;
            }
            boolean inFlight = false;
            for (int id : costume.pieces) {
                if (Rs2Equipment.isWearing(id) || !Rs2Inventory.hasItem(id)) continue;
                if (clickInFlight(id, now)) { inFlight = true; continue; }
                if (!costumeClick(-id, "put on")) return; // wear tries are keyed apart from take tries
                if (!react()) return;
                if (Rs2Inventory.wear(id)) {
                    costumeClickAt.put(id, System.currentTimeMillis());
                    acted("Putting on the " + costume.name + " outfit (" + id + ")");
                }
                return;
            }
            if (inFlight) {
                status = "Putting on the " + costume.name + " outfit";
                return;
            }
            stopForReview("The " + costume.name + " outfit is held but cannot be put on");
            return;
        }

        if (window.open) {
            if (Rs2Inventory.emptySlotCount() < missing.size()) {
                stopForReview("Inventory full: free " + missing.size() + " slots for the "
                        + costume.name + " outfit, then press Start");
                return;
            }
            boolean taking = false;
            for (int id : missing) {
                java.awt.Rectangle piece = window.visible.get(id);
                if (piece == null) continue;
                if (clickInFlight(id, now)) { taking = true; continue; }
                if (!costumeClick(id, "take from Diango")) return;
                if (!react()) return;
                // The list can re-lay out while we paused (the last take landing): click where it is now.
                piece = KspDiangoHolidayRetrieval.inspect().visible.get(id);
                if (piece == null) return;
                if (KspDiangoHolidayRetrieval.take(piece)) {
                    costumeClickAt.put(id, System.currentTimeMillis());
                    acted("Taking " + costume.name + " piece " + id + " from Diango ("
                            + (costume.pieces.length - missing.size() + 1) + "/" + costume.pieces.length + ")");
                }
                return;
            }
            if (taking) {
                status = "Taking the " + costume.name + " outfit from Diango";
                return;
            }
            Integer direction = missing.stream().map(window.offscreen::get)
                    .filter(java.util.Objects::nonNull).findFirst().orElse(null);
            if (direction != null && holidayScrollAttempts < 80) {
                if (!react()) return;
                if (KspDiangoHolidayRetrieval.scroll(window, direction < 0)) {
                    holidayScrollAttempts++;
                    pendingHolidayScrollY = window.scrollY;
                    pendingHolidayScrollAt = System.currentTimeMillis();
                    acted("Scrolling Diango's list " + (direction < 0 ? "up" : "down")
                            + " to the " + costume.name + " outfit; scroll " + holidayScrollAttempts);
                }
                return;
            }
            stopForReview("Diango's list does not show the " + costume.name + " pieces " + missing);
            return;
        }

        holidayScrollAttempts = 0;
        holidayCloseAttempts = 0;
        Rs2NpcModel diango = npcNamed("Diango");
        if (diango == null) {
            nearOrWalk(player, DIANGO, 8, "Diango in Draynor Village");
            return;
        }
        if (lastTalkAt == 0L || now - lastTalkAt >= WORLD_ACTION_STALL_TIMEOUT_MS) {
            if (!react()) return;
            if (npcHasAction(diango, "Holiday-items") && diango.click("Holiday-items")) {
                lastTalkAt = System.currentTimeMillis();
                acted("Opening Diango's holiday items for the costume");
                return;
            }
            if (diango.click("Talk-to")) {
                lastTalkAt = System.currentTimeMillis();
                acted("Asking Diango for holiday items");
                return;
            }
        }
        status = "Opening Diango's holiday items";
    }

    /** A click on this piece is still landing: clicking again now would be a double click. */
    private boolean clickInFlight(int itemId, long now) {
        Long at = costumeClickAt.get(itemId);
        return at != null && now - at < COSTUME_CLICK_SETTLE_MS;
    }

    /** Counts a click on a piece; after too many misses, stops for review. */
    private boolean costumeClick(int itemId, String what) {
        int tries = costumeClickTries.merge(itemId, 1, Integer::sum);
        if (tries > MAX_COSTUME_CLICK_TRIES) {
            stopForReview("Could not " + what + " costume piece " + itemId + " after " + MAX_COSTUME_CLICK_TRIES + " clicks");
            return false;
        }
        return true;
    }

    private void closeDiango(KspDiangoHolidayRetrieval.Snapshot window) {
        if (holidayCloseAttempts >= 3) {
            stopForReview("Unable to close Diango's window after taking the costume");
            return;
        }
        if (!react()) return;
        if (!KspDiangoHolidayRetrieval.close(window)) Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
        holidayCloseAttempts++;
        pendingHolidayClose = true;
        pendingHolidayCloseAt = System.currentTimeMillis();
        acted("Closing Diango's window with the full costume in hand");
    }

    private boolean npcHasAction(Rs2NpcModel npc, String action) {
        if (npc == null || action == null) return false;
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (Microbot.getClient() == null) return false;
            // The transformed definition carries an event NPC's live menu.
            net.runelite.api.NPCComposition def = npc.getNpc().getTransformedComposition();
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
        // Only real NPC positions are scouting anchors; adults are never spooked or counted.
        if (childScoutedNpcTiles.size() >= MAX_CHILD_NPC_SCOUT_STOPS) return false;

        List<Rs2NpcModel> anchors = visible.stream()
                .filter(n -> {
                    TreaterAge age = classifyTreater(n);
                    return age == TreaterAge.ADULT || age == TreaterAge.CHILD;
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
                    progress.children, CHILD_REPORT_CHECKPOINT,
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

    /** Whether the clicked child's actor index is still in the live scene. */
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

    /** Paces the next Spook: settled once the game's child count rises or the child stays gone. */
    private boolean verifyPendingChildSpook(WorldPoint player) {
        if (pendingChildId < 0) return false;
        final long now = System.currentTimeMillis();
        ChildSceneStatus observed = pendingChildLiveSceneStatus(player);
        if (observed == ChildSceneStatus.ABSENT) {
            if (pendingChildAbsentSince == 0L) pendingChildAbsentSince = now;
        } else {
            pendingChildAbsentSince = 0L;
        }
        boolean counted = progress.children > pendingChildrenBefore;
        boolean gone = pendingChildAbsentSince != 0L
                && now - pendingChildAbsentSince >= CHILD_SPOOK_ABSENCE_STABLE_MS
                && now - pendingChildSpookAt >= CHILD_SPOOK_ABSENCE_STABLE_MS;
        if (counted || gone) {
            log.info("Halloween child scare settled | childNpcId={} index={} counted={} gone={} | {}",
                    pendingChildId, pendingChildIndex, counted, gone, progress);
            childSpookCooldownUntil.remove(pendingChildId);
            clearPendingChildSpook();
            childNoTargetSince = 0L;
            status = "Children scared: " + progress.children + "/" + CHILD_REPORT_CHECKPOINT;
            return false;
        }

        // UNKNOWN can be transient (walking, scene swaps): wait for the timeout, do not re-click.
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
        if (!costumeOn()) {
            clearPendingChildSpook();
            change(Stage.GET_COSTUME, "Need a full Halloween outfit on");
            return;
        }
        if (!RIMMINGTON.contains(player)
                && !nearOrWalk(player, STARTER, 12, "child trick-or-treaters")) return;

        // One Spook at a time: wait until the last one has settled.
        if (verifyPendingChildSpook(player)) return;
        // A scared child never shows again, so every visible child with Spook still needs scaring.
        List<Rs2NpcModel> visibleTreaters = treaters();

        long now = System.currentTimeMillis();
        Rs2NpcModel child = visibleTreaters.stream()
                .filter(n -> isChildTreaterId(n.getId())
                        && npcHasAction(n, "Spook")
                        && childSpookCooldownUntil.getOrDefault(n.getId(), 0L) <= now
                        && n.getWorldLocation() != null)
                .min(Comparator.comparingInt(n ->
                        player.distanceTo(n.getWorldLocation())))
                .orElse(null);

        if (child != null) {
            childNoTargetSince = 0L;
            sweepsWithoutTarget = 0;
            // Get close first, so its disappearance happens inside the loaded scene.
            if (player.distanceTo(child.getWorldLocation()) > 9) {
                nearOrWalk(player, child.getWorldLocation(), 6,
                        "unspooked child trick-or-treater");
                return;
            }
            int id = child.getId();
            int index = child.getIndex();
            WorldPoint tile = child.getWorldLocation();
            if (!react()) return;
            if (child.click("Spook")) {
                pendingChildId = id;
                pendingChildIndex = index;
                pendingChildTile = tile;
                pendingChildSpookAt = now;
                pendingChildAbsentSince = 0L;
                pendingChildrenBefore = progress.children;
                acted("Spook dispatched | childNpcId=" + id + " index=" + index
                        + " scared=" + progress.children + "/" + CHILD_REPORT_CHECKPOINT);
            }
            return;
        }

        // Scene updates can lag behind movement or a despawn.
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
            log.info("Halloween looking for remaining child NPCs | {} visibleTreaters=[{}]"
                    + " scoutedNpcTiles={}", progress, sceneDetails, childScoutedNpcTiles);
        }
        // Never stand still waiting: walk the village (bounded; ends in review if they never show).
        scanRimmington(player, "Looking for the remaining child trick-or-treaters ("
                + progress.children + "/" + CHILD_REPORT_CHECKPOINT + ")");
    }


    private void solveChemist(WorldPoint player) {
        if (Rs2Inventory.hasItem("Sugar", true)
                || Rs2Inventory.hasItem("Molten sugar")) {
            change(Stage.COOK_SUGAR, "Chemist sugar acquired");
            return;
        }
        Rs2NpcModel chemist = npcNamed("Chemist");
        if (chemist == null) {
            nearOrWalk(player, CHEMIST, 8, "the Chemist west of Rimmington");
            return;
        }
        // One Talk-to per conversation; retry only if no dialogue, answer box or sugar appeared.
        long now = System.currentTimeMillis();
        if (chemistConversationAt != 0L
                && now - chemistConversationAt < WORLD_ACTION_STALL_TIMEOUT_MS) {
            status = "Waiting for Chemist dialogue or sugar";
            return;
        }
        if (!react()) return;
        if (chemist.click("Talk-to")) {
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
        // Sugar is used on the range or fireplace, which need not offer a Cook action.
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
        if (pendingCook && Rs2Player.isMoving()) pendingCookAt = System.currentTimeMillis(); // still walking there
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
            // The cooking surface may not be loaded yet near the Chemist.
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
        if (!react()) return;
        if (sugar != null && Rs2Inventory.useItemOnObject(sugar.getId(), surface.getId())) {
            pendingCook = true;
            pendingCookAt = System.currentTimeMillis();
            acted("Cooking sugar at " + surface.getName() + "; awaiting molten sugar");
        }
    }

    // Item names seen live, plus the wiki spelling.
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

    /** Onions already used still count: candy in the inventory and adults the game counts as fed. */
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

        if (total >= ONIONS_REQUIRED) {
            change(count > 0 ? Stage.COAT_ONIONS : Stage.GIVE_CANDY,
                    "Five onions accounted for: raw=" + count + " candy=" + candy
                            + " delivered=" + adultsGiven);
            return;
        }

        // A click is not a receipt: wait for the onion to arrive.
        if (pendingOldOnionCount >= 0) {
            if (count > pendingOldOnionCount) {
                log.info("Halloween old inion receipt | before={} after={} total={}/{}",
                        pendingOldOnionCount, count, total, ONIONS_REQUIRED);
                pendingOldOnionCount = -1;
                pendingOldOnionFailures = 0;
            } else if (Rs2Player.isMoving() || System.currentTimeMillis() - pendingOldOnionAt
                    < WORLD_ACTION_STALL_TIMEOUT_MS) {
                // Walking to the crate is not a failed click: the wait counts from arrival.
                if (Rs2Player.isMoving()) pendingOldOnionAt = System.currentTimeMillis();
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

        Rs2TileObjectModel crate = oldOnionCrate();
        if (crate != null) {
            oldOnionCrateMissingSince = 0L;
            String action = oldOnionCrateAction(crate);
            if (action == null) {
                stopForReview("Crate of onions visible at " + crate.getWorldLocation()
                        + " but does not expose a known collection action");
                return;
            }
            if (!react()) return;
            if (crate.click(action)) {
                pendingOldOnionCount = count;
                pendingOldOnionAt = System.currentTimeMillis();
                acted("Collecting Old inion " + (total + 1) + "/5 from "
                        + OLD_ONION_CRATE_NAME + " at " + crate.getWorldLocation());
            }
            return;
        }

        // Walk only when the crate is not loaded and we are away from the house.
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
        // Inputs can vanish before the candy onion shows in the inventory: wait for the receipt.
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
        if (!react()) return;
        if (oldItemName != null && Rs2Inventory.combine("Molten sugar", oldItemName)) {
            pendingCoatCandyCount = candy;
            pendingCoatAt = System.currentTimeMillis();
            acted("Coating old onion with molten sugar (" + candy + "/5 prepared)");
        }
    }

    private void giveCandy(WorldPoint player) {
        int candyCount = Rs2Inventory.count("Candy onion");
        long now = System.currentTimeMillis();
        if (pendingCandyCount >= 0) {
            if (progress.adults > pendingAdultsBefore || candyCount < pendingCandyCount) {
                log.info("Halloween candy delivered | adultId={} | {}", pendingAdultId, progress);
                pendingCandyCount = -1;
                pendingAdultId = -1;
                candyFailures = 0;
            } else if (Rs2Player.isMoving() || now - lastActionAt < WORLD_ACTION_STALL_TIMEOUT_MS) {
                if (Rs2Player.isMoving()) lastActionAt = now;
                status = "Waiting for the adult to take the candy onion";
                return;
            } else {
                // Not taken (walk, can't-reach recovery, a dialogue): retry that adult after a pause.
                adultCooldownUntil.put(pendingAdultId, now + CHILD_SPOOK_RETRY_COOLDOWN_MS);
                pendingCandyCount = -1;
                pendingAdultId = -1;
                if (++candyFailures >= MAX_UNCONFIRMED_ACTIONS) {
                    stopForReview("Adults did not take a candy onion " + candyFailures
                            + " times in a row; check NPC/dialogue | " + progress);
                    return;
                }
            }
        }
        if (candyCount <= 0) {
            change(Stage.COAT_ONIONS, "Prepare more candy onions for adults");
            return;
        }
        // Visible adults (16794-16798) are unfed: a fed adult turns into its vomit form and never comes back.
        List<Rs2NpcModel> visibleTreaters = treaters().stream()
                .filter(n -> n.getWorldLocation() != null && RIMMINGTON.contains(n.getWorldLocation()))
                .collect(Collectors.toList());
        Rs2NpcModel adult = visibleTreaters.stream()
                .filter(n -> isAdultTreaterId(n.getId())
                        && adultCooldownUntil.getOrDefault(n.getId(), 0L) <= now)
                .min(Comparator.comparingInt(n -> player.distanceTo(n.getWorldLocation())))
                .orElse(null);
        net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel item =
                Rs2Inventory.get("Candy onion", true);
        if (adult != null && item != null) {
            Rs2Walker.clearWalkingRoute("ksp_halloween_adult_visible");
            if (!react()) return;
        // The non-deprecated use-on-NPC overload takes the util model.
            if (Rs2Inventory.useItemOnNpc(item.getId(),
                    new net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel(adult.getNpc()))) {
                pendingCandyCount = candyCount;
                pendingAdultsBefore = progress.adults;
                pendingAdultId = adult.getId();
                acted("Giving Candy onion to verified adult npcId=" + adult.getId()
                        + " tile=" + adult.getWorldLocation()
                        + " delivered=" + adultsGiven + "/5");
                sweepsWithoutTarget = 0;
            }
            return;
        }

        // No adult in the scene: search the village; children are never candy targets.
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

    /** A player's reaction before clicking something or picking an option. */
    private boolean react() {
        sleep(pace.reactionMs(paceRng));
        return active && !paused; // Pause/Stop pressed during the pause: skip the click
    }

    /** Time to skim a dialogue page before pressing Continue. */
    private boolean readPage(String text) {
        sleep(pace.readMs(text, paceRng));
        return active && !paused;
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
        costumeClickAt.clear();
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
            return false;
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
            if (next == Stage.WAIT_FOR_REWARD) rewardWaitSince = 0L;
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
            confirmSmashedLantern(pendingLantern, "game smash message");
            return;
        }
        // Full inventory: never touch the player's items, say what to free and stop.
        if (text.contains("more free inventory slot")
                || text.contains("too full to hold any more onions")) {
            stopForReview("Inventory full: " + raw.replaceAll("<[^>]*>", "").trim() + " Then press Start.");
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

    /** Explicit reset only; progress is never reset on a client restart. */
    public synchronized void resetProgress() {
        if (active) return;
        configManager.unsetConfiguration(GROUP, STAGE_KEY);
        for (String key : OBSOLETE_KEYS) configManager.unsetConfiguration(GROUP, key);
        smashedLanternTiles.clear();
        pendingLantern = null;
        lanternsVerified = false;
        clearPendingChildSpook();
        childSpookCooldownUntil.clear();
        adultCooldownUntil.clear();
        childScoutedNpcTiles.clear();
        childNoTargetSince = 0L;
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
    // The overlay and panel show the same game snapshot the script acts on.
    public int getLanternCount() { return progress.lanterns; }
    public int getChildSpookAttempts() { return progress.children; }

    public int getChildSpookCheckpoint() { return CHILD_REPORT_CHECKPOINT; }

    public boolean isChildObjectiveConfirmed() { return childrenReportConfirmed; }

    public int getAdultsGiven() { return progress.adults; }
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
