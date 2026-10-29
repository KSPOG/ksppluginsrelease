package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.tutorialisland;

import net.runelite.client.plugins.microbot.kspaccountbuilder.KspAccountBuilderPauseGate;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspManagedScript;

import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.ItemID;
import net.runelite.api.NpcID;
import net.runelite.api.ObjectID;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;

import net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.KspBankWidgetHelper;
import net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.KspLiveScene;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.tutorialisland.areas.TutAreas;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.security.LoginManager;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import javax.inject.Singleton;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue.clickContinue;
import static net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue.hasContinue;
import static net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue.hasSelectAnOption;
import static net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue.isInDialogue;

@Singleton
public class TutorialIslandScript extends KspManagedScript
{
    private static final int LOOP_DELAY_MS = 100;
    // Game's authoritative tutorial rock IDs. Always resolve in the live
    // underground scene; no guessed tile coordinates or generic rock clicks.
    private static final int TUTORIAL_TIN_ROCK_ID = ObjectID.TIN_ROCKS;
    private static final int TUTORIAL_COPPER_ROCK_ID = ObjectID.COPPER_ROCKS;
    private static final long MINING_DIAGNOSTIC_INTERVAL_MS = 5_000L;
    private static final long INTERACTION_DISPATCH_TIMEOUT_MS = 1_500L;
    private static final int DEFAULT_CAMERA_ZOOM = 377;
    private static final int RAT_PEN_GATE_ID = 9719;
    private static final int RAT_PEN_INNER_BOUNDARY_X = 3110;
    private static final int ACCOUNT_GUIDE_DOOR_ID = 9721;
    private static final int ACCOUNT_GUIDE_ROOM_MIN_X = 3125;
    private static final int CHAPEL_PASSAGE_DOOR_ID = 9722;
    private static final int MAGIC_PASSAGE_DOOR_ID = 9723;
    private static final int CHAPEL_INTERIOR_DOOR_ID = 1524;
    private static final int CHAPEL_INTERIOR_OPEN_DOOR_ID = 1525;
    private static final int COMPLETION_LOGOUT_RETRY_MS = 6000;
    private static final int QUEUE_LOGIN_RETRY_MS = 10000;
    private static final int QUEUE_LOGIN_SKIP_MS = 60000;
    private static final int QUEUED_LOGIN_EMAIL_PASSWORD_DELAY_MIN_MS = 1400;
    private static final int QUEUED_LOGIN_EMAIL_PASSWORD_DELAY_MAX_MS = 2600;

    private static final int NAME_CREATION_GROUP = 558;
    private static final int NAME_CREATION_CONTAINER_CHILD = 2;
    private static final int NAME_INPUT_CHILD = 7;
    private static final int NAME_INPUT_TEXT_CHILD = 12;
    private static final int NAME_RESPONSE_TEXT_CHILD = 13;
    private static final int LOOK_UP_NAME_BUTTON_CHILD = 18;
    private static final int SET_NAME_BUTTON_CHILD = 19;

    private static final int CHARACTER_CREATION_GROUP = 679;
    private static final int CHARACTER_CREATION_CONTAINER_CHILD = 4;
    private static final int BODY_TYPE_A_CHILD = 68;
    private static final int BODY_TYPE_B_CHILD = 69;
    private static final int CHARACTER_CONFIRM_BUTTON_CHILD = 74;
    private static final int FIXED_VIEWPORT_GROUP = 164;
    private static final int SETTINGS_TAB_CHILD = 41;
    private static final int SETTINGS_PANEL_GROUP = 116;

    private static final String DISPLAY_NAME_TITLE = "Set display name";
    private static final String LOOK_UP_NAME_BUTTON = "Look up name";
    private static final String CHARACTER_CREATOR_TITLE = "Character Creator";
    private static final String EXPERIENCE_PROMPT_TITLE = "How familiar are you with Old School RuneScape?";
    private static final String SETTINGS_TUTORIAL_PROMPT = "flashing icon of a spanner";

    private static final String[] EXPERIENCE_OPTION_TEXTS = {
            "I'm brand new! This is my first time here.",
            "I've played in the past, but not recently.",
            "I'm an experienced player."
    };

    private static final String[] NAME_PREFIXES = {
            "Ash", "Bryn", "Cora", "Dane", "Eli", "Faye", "Glen", "Hale", "Iris", "Joss",
            "Kian", "Lena", "Mira", "Nora", "Oren", "Perr", "Quin", "Rhea", "Sora", "Tavi"
    };

    private static final String[] NAME_SUFFIXES = {
            "ford", "vale", "mere", "wyn", "low", "den", "holt", "wick", "row", "lan",
            "well", "mont", "ley", "mar", "rin", "son", "len", "hart", "brook", "field"
    };

    private static final int[] CHARACTER_CREATION_ARROWS = {
            13, 17, 21, 25, 29, 33, 37, 44, 48, 52, 56, 60
    };

    private static final WorldArea START_AREA            = TutAreas.START_AREA;
    private static final WorldArea SURVIVAL_AREA         = TutAreas.SURVIVAL_AREA;
    private static final WorldArea COOKING_AREA          = TutAreas.COOKING_AREA;
    private static final WorldPoint COOKING_AREA_WALK_TILE = new WorldPoint(3074, 3087, 0);
    private static final WorldPoint QUEST_GUIDE_WALK_TILE = new WorldPoint(3085, 3121, 0);
    private static final WorldArea MINING_SMITHING_AREA  = TutAreas.MINING_SMITHING_AREA;
    private static final WorldArea COMBAT_INSTRUCTOR_AREA= TutAreas.COMBAT_INSTRUCTOR_AREA;
    private static final WorldArea RAT_PIT_AREA          = TutAreas.RAT_PIT_AREA;
    private static final WorldArea TUT_ISLAND_BANK_AREA  = TutAreas.TUT_ISLAND_BANK_AREA;
    private static final WorldArea CHURCH_AREA           = TutAreas.CHURCH_AREA;
    private static final WorldArea TUTORIAL_END_AREA     = TutAreas.TUTORIAL_END_AREA;
    private static final WorldPoint CHAPEL_INSTRUCTOR_TILE = new WorldPoint(3128, 3106, 0);
    private static final WorldPoint MAGIC_INSTRUCTOR_TILE = new WorldPoint(3141, 3090, 0);

    private String lastGeneratedName = "None";
    private boolean nameLookupPending;
    private boolean nameSetPending;
    private boolean characterCustomized;
    private boolean characterConfirmDispatched;
    private boolean experienceSelectionDispatched;
    private String lastCharacterAction = "Waiting";
    private String lastExperienceSelection = "None";
    private String completionState = "Active";
    private TutState status = TutState.NAME;
    private boolean toggledSettings;
    private boolean debugEnabled;
    private boolean completionLogoutRequested;
    private long lastCompletionLogoutAttemptAtMs;
    private int accountQueueIndex;
    private int pendingQueuedLoginIndex = -1;
    private long lastQueueLoginAttemptAtMs;
    private long queuedLoginStartedAtMs;
    private String queuedAccountName = "None";
    private WorldPoint ownFireLocation;
    private boolean waitingForSurvivalFire;
    private boolean survivalCookingDispatched;
    private boolean doughMixDispatched;
    private boolean breadCookingDispatched;
    private boolean treeActionDispatched;
    private boolean fishingActionDispatched;
    private boolean homeTeleportDispatched;
    private long pendingInteractionAtMs;
    private String pendingInteractionKey;
    private boolean windStrikeSelected;
    private int lastMiningProgress = -1;
    private long lastMiningStageAtMs;
    private long lastMiningDiagnosticAtMs;
    private long lastTutorialWalkerWarningAtMs;
    private long lastRangedDiagnosticAtMs;
    private long lastTutorialTransitionDiagnosticAtMs;
    private long lastRangedAttackAtMs;
    private int lastRangedTargetIndex = -1;

    // Tutorial progress (varbit 281) is the authoritative state boundary.
    private TutState monitoredState;
    private int monitoredProgress = -1;
    private long stateEnteredAtMs;
    private long lastStateDiagnosticAtMs;
    private long lastStateRetryAtMs;
    private int currentStateRetries;
    private static final long STATE_DIAGNOSTIC_INTERVAL_MS = 5_000L;
    private static final long STATE_RETRY_INTERVAL_MS = 8_000L;

    public boolean run()
    {
        shutdown();
        resetAccountState();
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(
                this::runLoop, 0, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);
        return true;
    }

    private void runLoop()
    {
        try
        {
            if (KspAccountBuilderPauseGate.isPaused() || !super.run()) return;
            if (!Microbot.isLoggedIn())
            {
                handleQueuedLogin();
                return;
            }

            completeQueuedLoginIfNeeded();
            // Mainland bank cache/bootstrap may still have an in-flight route
            // from a previously running task. Reject foreign destinations
            // every tutorial tick without cancelling valid island-local paths.
            rejectMainlandWalkerRoute();
            calculateStatus();
            monitorCurrentState();

            if (closeBlockingTutorialWidget()
                    || handleSetupWidget()
                    || openSettingsTabForTutorialPrompt())
            {
                return;
            }

            if (hasContinue())
            {
                clickContinue();
                return;
            }

            runStage();
        }
        catch (Exception e)
        {
            debug("Error in TutorialIslandScript: %s", e.getMessage());
        }
    }

    private boolean closeBlockingTutorialWidget()
    {
        if (Rs2Widget.isWidgetVisible(929, 5))
        {
            Rs2Widget.clickWidget(929, 5);
            return true;
        }

        if (Rs2Widget.isWidgetVisible(310, 0))
        {
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
            return true;
        }
        return false;
    }

    private boolean handleSetupWidget()
    {
        if (isExperiencePromptOpen())
        {
            selectRandomExperienceOption();
            return true;
        }

        if (isDisplayNameWidgetOpen())
        {
            status = TutState.NAME;
            enterGeneratedName();
            return true;
        }

        if (isCharacterCreationWidgetOpen())
        {
            status = TutState.CHARACTER;
            randomizeCharacter();
            return true;
        }
        return false;
    }

    /**
     * A retry re-dispatches only the CURRENT stage. It never advances the
     * tutorial or chooses an alternative state without varbit/widget evidence.
     * Progress changes reset its counters and release stale action guards.
     */
    private void monitorCurrentState()
    {
        final long now = System.currentTimeMillis();
        final int progress = Microbot.getVarbitPlayerValue(281);
        if (status != monitoredState || progress != monitoredProgress)
        {
            Microbot.log("[TutIsland] STATE_ENTER | from=" + monitoredState
                    + "/" + monitoredProgress + " to=" + status + "/" + progress
                    + " player=" + Rs2Player.getWorldLocation());
            monitoredState = status;
            monitoredProgress = progress;
            stateEnteredAtMs = now;
            lastStateDiagnosticAtMs = 0L;
            lastStateRetryAtMs = now;
            currentStateRetries = 0;
            pendingInteractionAtMs = 0L;
            pendingInteractionKey = null;
        }
        if (now - lastStateDiagnosticAtMs >= STATE_DIAGNOSTIC_INTERVAL_MS)
        {
            lastStateDiagnosticAtMs = now;
            Microbot.log("[TutIsland] STATE_HEARTBEAT | state=" + status
                    + " progress=" + progress + " elapsedMs=" + (now - stateEnteredAtMs)
                    + " retry=" + currentStateRetries
                    + " player=" + Rs2Player.getWorldLocation()
                    + " moving=" + Rs2Player.isMoving()
                    + " animating=" + Rs2Player.isAnimating()
                    + " interacting=" + Rs2Player.isInteracting()
                    + " walkerTarget=" + Rs2Walker.getCurrentTarget()
                    + " pending=" + pendingInteractionKey
                    + " tin=" + Rs2Inventory.count("Tin ore")
                    + " copper=" + Rs2Inventory.count("Copper ore")
                    + " dough=" + Rs2Inventory.count("Bread dough")
                    + " bread=" + Rs2Inventory.count("Bread"));
        }
        if (status == TutState.FINISHED || Rs2Player.isMoving()
                || Rs2Player.isAnimating() || Rs2Player.isInteracting()
                || isInDialogue() || hasContinue())
        {
            lastStateRetryAtMs = now;
            return;
        }
        if (now - lastStateRetryAtMs >= STATE_RETRY_INTERVAL_MS)
        {
            lastStateRetryAtMs = now;
            currentStateRetries++;
            Microbot.log("[TutIsland] STATE_RETRY | state=" + status
                    + " progress=" + progress + " attempt=" + currentStateRetries
                    + " stalledMs=" + (now - stateEnteredAtMs)
                    + " action=" + pendingInteractionKey);
            pendingInteractionAtMs = 0L;
            pendingInteractionKey = null;
            // The next loop iteration re-evaluates and dispatches the same
            // stage's normal action, including live object/NPC discovery.
        }
    }

    private void runStage()
    {
        switch (status)
        {
            case GETTING_STARTED: gettingStarted(); break;
            case SURVIVAL_GUIDE: survivalGuide(); break;
            case COOKING_GUIDE: cookingGuide(); break;
            case QUEST_GUIDE: questGuide(); break;
            case MINING_GUIDE: miningGuide(); break;
            case COMBAT_GUIDE: combatGuide(); break;
            case BANKER_GUIDE: bankerGuide(); break;
            case PRAYER_GUIDE: prayerGuide(); break;
            case MAGE_GUIDE: mageGuide(); break;
            case FINISHED: handleTutorialComplete(); break;
            default: break;
        }
    }

    public static boolean isOnTutorialIsland()
    {
        try
        {
            if (!Microbot.isLoggedIn())
            {
                return false;
            }

            if (isDisplayNameWidgetOpenStatic() || isCharacterCreationWidgetOpenStatic() || isExperiencePromptOpenStatic())
            {
                return true;
            }

            WorldPoint location = getLocalPlayerWorldLocationSafe();
            if (isTutorialIslandLocation(location))
            {
                return true;
            }

            // Valid unfinished varbit progress is independent evidence for
            // Tutorial Island, even if a transitional or instanced client
            // WorldPoint is temporarily not within the static island areas.
            // Zero is ambiguous when the varbit cache is uninitialized, so
            // it must be accompanied by an island position or setup widget.
            int progress = Microbot.getVarbitPlayerValue(281);
            return progress > 0 && progress < 1000;
        }
        catch (Exception ignored)
        {
            return false;
        }
    }

    public static boolean isInTutorialIslandArea()
    {
        try
        {
            if (!Microbot.isLoggedIn())
            {
                return false;
            }

            WorldPoint location = getLocalPlayerWorldLocationSafe();
            return location != null
                    && (TutAreas.TUT_OVERWORLD_AREA.contains(location)
                    || TutAreas.TUTORIAL_ISLAND_UNDERGROUND_AREA.contains(location));
        }
        catch (Exception ignored)
        {
            return false;
        }
    }

    public static boolean isPreTutorialBlockingWidgetOpen()
    {
        return isDisplayNameWidgetOpenStatic() || isCharacterCreationWidgetOpenStatic() || isExperiencePromptOpenStatic();
    }

    private static boolean isTutorialIslandLocation(WorldPoint location) { return location != null && TutAreas.contains(location); }

    private static WorldPoint getLocalPlayerWorldLocationSafe()
    {
        // Tutorial detection is called from the Account Builder worker thread.
        // Query RuneLite player coordinates on the client thread; reading the
        // live Client/Actor directly from a scheduler thread can throw or
        // return an incomplete snapshot, making the island look undetected.
        try
        {
            return Microbot.getClientThread().runOnClientThreadOptional(() ->
            {
                Client client = Microbot.getClient();
                return client == null || client.getLocalPlayer() == null
                        ? null : client.getLocalPlayer().getWorldLocation();
            }).orElse(null);
        }
        catch (Exception ignored)
        {
            return null;
        }
    }

    private static boolean isDisplayNameWidgetOpenStatic()
    {
        return Rs2Widget.isWidgetVisible(NAME_CREATION_GROUP, NAME_CREATION_CONTAINER_CHILD)
                || (Rs2Widget.hasWidget(DISPLAY_NAME_TITLE) && Rs2Widget.hasWidget(LOOK_UP_NAME_BUTTON));
    }

    private static boolean isCharacterCreationWidgetOpenStatic()
    {
        return Rs2Widget.isWidgetVisible(CHARACTER_CREATION_GROUP, CHARACTER_CREATION_CONTAINER_CHILD)
                || Rs2Widget.isWidgetVisible(CHARACTER_CREATION_GROUP, CHARACTER_CONFIRM_BUTTON_CHILD)
                || Rs2Widget.hasWidget(CHARACTER_CREATOR_TITLE);
    }

    private static boolean isExperiencePromptOpenStatic()
    {
        return Rs2Dialogue.isInDialogue()
                && (Rs2Widget.hasWidget(EXPERIENCE_PROMPT_TITLE)
                || Rs2Widget.hasWidget(EXPERIENCE_OPTION_TEXTS[0])
                || Rs2Widget.hasWidget(EXPERIENCE_OPTION_TEXTS[1])
                || Rs2Widget.hasWidget(EXPERIENCE_OPTION_TEXTS[2]));
    }

    public boolean isComplete()
    {
        try
        {
            return Microbot.getVarbitPlayerValue(281) >= 1000;
        }
        catch (Exception ignored)
        {
            return false;
        }
    }

    public void setDebugLogging(boolean enabled) { this.debugEnabled = enabled; }

    // -------------------------------------------------------------------------
    // Status calculation
    // -------------------------------------------------------------------------

    private void calculateStatus()
    {
        int progress = Microbot.getVarbitPlayerValue(281);
        if (progress < 1000 && completionLogoutRequested)
        {
            completionLogoutRequested = false;
            completionState = "Active";
        }

        TutState previous = status;
        if (isDisplayNameWidgetOpen()) status = TutState.NAME;
        else if (isCharacterCreationWidgetOpen()) status = TutState.CHARACTER;
        else status = stageFor(progress);

        if (previous != status)
        {
            // A tutorial-stage transition invalidates any route or interaction
            // dispatched for the previous instructor/room.
            Rs2Walker.clearWalkingRoute("ksp_tutorial_stage_changed");
            pendingInteractionAtMs = 0L;
            pendingInteractionKey = null;
        }
    }

    private TutState stageFor(int progress)
    {
        // Tutorial state 10 is still the Gielinor Guide section: open the
        // starting-room door. Survival begins at state 20.
        if (progress <= 10) return TutState.GETTING_STARTED;
        if (progress < 120) return TutState.SURVIVAL_GUIDE;
        if (progress < 200) return TutState.COOKING_GUIDE;
        if (progress <= 250) return TutState.QUEST_GUIDE;
        if (progress <= 360) return TutState.MINING_GUIDE;
        if (progress < 510) return TutState.COMBAT_GUIDE;
        if (progress < 540) return TutState.BANKER_GUIDE;
        if (progress < 610) return TutState.PRAYER_GUIDE;
        return progress < 1000 ? TutState.MAGE_GUIDE : TutState.FINISHED;
    }

    // -------------------------------------------------------------------------
    // Widget detection helpers
    // -------------------------------------------------------------------------

    private boolean isInStartArea()
    {
        WorldPoint location = getLocalPlayerWorldLocationSafe();
        return location != null && START_AREA.contains(location);
    }

    private boolean isDisplayNameWidgetOpen() { return isDisplayNameWidgetOpenStatic(); }

    private boolean isCharacterCreationWidgetOpen() { return isCharacterCreationWidgetOpenStatic(); }

    private boolean isExperiencePromptOpen()
    {
        return Rs2Dialogue.isInDialogue()
                && (Rs2Widget.hasWidget(EXPERIENCE_PROMPT_TITLE)
                || Rs2Widget.hasWidget(EXPERIENCE_OPTION_TEXTS[0])
                || Rs2Widget.hasWidget(EXPERIENCE_OPTION_TEXTS[1])
                || Rs2Widget.hasWidget(EXPERIENCE_OPTION_TEXTS[2]));
    }

    private boolean openSettingsTabForTutorialPrompt()
    {
        String dialogueText = Rs2Dialogue.getDialogueText();
        if (dialogueText == null
                || !dialogueText.toLowerCase().contains(SETTINGS_TUTORIAL_PROMPT))
        {
            return false;
        }

        if (Rs2Widget.isWidgetVisible(SETTINGS_PANEL_GROUP, 0)
                || Rs2Widget.hasWidget("Controls Settings"))
        {
            return false;
        }

        if (!Rs2Widget.clickWidget(FIXED_VIEWPORT_GROUP, SETTINGS_TAB_CHILD))
        {
            debug("Failed to click the Tutorial Island Settings tab widget.");
        }

        return true;
    }

    // -------------------------------------------------------------------------
    // Name creation
    // -------------------------------------------------------------------------

    private void enterGeneratedName()
    {
        if (nameSetPending)
        {
            if (!isDisplayNameWidgetOpen())
            {
                nameSetPending = false;
                nameLookupPending = false;
                lastGeneratedName = "None";
            }
            return;
        }

        if (nameLookupPending)
        {
            if (isGeneratedNameAvailable(lastGeneratedName))
            {
                boolean clicked = Rs2Widget.clickWidget(NAME_CREATION_GROUP, SET_NAME_BUTTON_CHILD);
                if (!clicked)
                {
                    Widget setName = Rs2Widget.findWidget("Set name", null, false);
                    clicked = setName != null && Rs2Widget.clickWidget(setName);
                }
                if (!clicked)
                {
                    clicked = Rs2Widget.clickWidget(NAME_CREATION_GROUP, LOOK_UP_NAME_BUTTON_CHILD);
                }

                nameSetPending = clicked;
                return;
            }

            String response = getDisplayNameResponse();
            String lower = response.toLowerCase();
            if (!response.isEmpty()
                    && (lower.contains("not available")
                    || lower.contains("unavailable")
                    || lower.contains("already taken")
                    || lower.contains("try another")))
            {
                nameLookupPending = false;
                lastGeneratedName = "None";
            }
            return;
        }

        String name = generateDisplayName();
        lastGeneratedName = name;
        clearDisplayNameInput();

        if (!Rs2Widget.clickWidget(NAME_CREATION_GROUP, NAME_INPUT_CHILD))
        {
            return;
        }

        Rs2Keyboard.typeString(name);

        if (Rs2Widget.clickWidget(NAME_CREATION_GROUP, LOOK_UP_NAME_BUTTON_CHILD))
        {
            nameLookupPending = true;
        }
    }

    private void clearDisplayNameInput()
    {
        String currentInput = getCurrentDisplayNameInput();

        if (currentInput.isEmpty())
        {
            return;
        }

        if (!Rs2Widget.clickWidget(NAME_CREATION_GROUP, NAME_INPUT_CHILD))
        {
            return;
        }

        for (int i = 0; i < currentInput.length(); i++)
        {
            Rs2Keyboard.keyPress(KeyEvent.VK_BACK_SPACE);
        }
    }

    private String getCurrentDisplayNameInput()
    {
        Widget nameInput = Rs2Widget.getWidget(NAME_CREATION_GROUP, NAME_INPUT_TEXT_CHILD);

        if (nameInput == null || nameInput.getText() == null)
        {
            return "";
        }

        return nameInput.getText().replace("*", "").trim();
    }

    private String getDisplayNameResponse()
    {
        Widget responseWidget = Rs2Widget.getWidget(NAME_CREATION_GROUP, NAME_RESPONSE_TEXT_CHILD);

        if (responseWidget == null || responseWidget.getText() == null)
        {
            return "";
        }

        return Rs2UiHelper.stripColTags(responseWidget.getText()).trim();
    }

    private boolean isGeneratedNameAvailable(String name)
    {
        return getDisplayNameResponse().startsWith("Great! The display name " + name + " is available");
    }

    // -------------------------------------------------------------------------
    // Character creation
    // -------------------------------------------------------------------------

    private void randomizeCharacter()
    {
        if (!isCharacterCreationWidgetOpen())
        {
            characterCustomized = false;
            characterConfirmDispatched = false;
            lastCharacterAction = "Confirmed";
            return;
        }

        if (!characterCustomized)
        {
            lastCharacterAction = "Randomizing";
            selectRandomBodyType();

            List<Integer> arrows = new ArrayList<>(CHARACTER_CREATION_ARROWS.length);
            for (int arrowBaseChild : CHARACTER_CREATION_ARROWS)
            {
                arrows.add(arrowBaseChild);
            }
            Collections.shuffle(arrows);

            int arrowsToClick = ThreadLocalRandom.current().nextInt(5, arrows.size() + 1);
            for (int i = 0; i < arrowsToClick; i++)
            {
                clickRandomCharacterArrow(arrows.get(i));
            }

            characterCustomized = true;
            lastCharacterAction = "Customized";
            return;
        }

        if (characterConfirmDispatched)
        {
            if (!isCharacterCreationWidgetOpen())
            {
                characterCustomized = false;
                characterConfirmDispatched = false;
                lastCharacterAction = "Confirmed";
                return;
            }

            characterConfirmDispatched = false;
            return;
        }

        lastCharacterAction = "Confirming";
        characterConfirmDispatched = confirmCharacterSelection();
    }

    private boolean confirmCharacterSelection()
    {
        if (Rs2Widget.clickWidget(
                CHARACTER_CREATION_GROUP,
                CHARACTER_CONFIRM_BUTTON_CHILD))
        {
            return true;
        }

        Widget confirmWidget = Rs2Widget.findWidget("Confirm", null, false);
        return confirmWidget != null && Rs2Widget.clickWidget(confirmWidget);
    }

    private void selectRandomBodyType()
    {
        int bodyTypeChild = ThreadLocalRandom.current().nextBoolean() ? BODY_TYPE_A_CHILD : BODY_TYPE_B_CHILD;
        Rs2Widget.clickWidget(CHARACTER_CREATION_GROUP, bodyTypeChild);
    }

    private void clickRandomCharacterArrow(int arrowBaseChild)
    {
        int arrowChild = arrowBaseChild + (ThreadLocalRandom.current().nextBoolean() ? 2 : 3);
        int clickCount = ThreadLocalRandom.current().nextInt(1, 8);

        if (!Rs2Widget.isWidgetVisible(CHARACTER_CREATION_GROUP, arrowChild))
        {
            return;
        }

        for (int i = 0; i < clickCount; i++)
        {
            if (!Rs2Widget.clickWidget(CHARACTER_CREATION_GROUP, arrowChild))
            {
                return;
            }
        }
    }

    private void selectRandomExperienceOption()
    {
        if (!isExperiencePromptOpen())
        {
            experienceSelectionDispatched = false;
            return;
        }

        if (experienceSelectionDispatched)
        {
            experienceSelectionDispatched = false;
            return;
        }

        int optionIndex = ThreadLocalRandom.current().nextInt(EXPERIENCE_OPTION_TEXTS.length);
        String optionText = EXPERIENCE_OPTION_TEXTS[optionIndex];
        Widget optionWidget = Rs2Widget.findWidget(optionText, null, false);

        if (optionWidget == null)
        {
            return;
        }

        lastExperienceSelection = "Option " + (optionIndex + 1);
        experienceSelectionDispatched = Rs2Widget.clickWidget(optionWidget);
    }

    // -------------------------------------------------------------------------
    // Tutorial steps
    // -------------------------------------------------------------------------

    private void gettingStarted()
    {
        Rs2NpcModel npc = tutorialNpc(NpcID.GIELINOR_GUIDE);
        int progress = Microbot.getVarbitPlayerValue(281);

        if (progress < 3)
        {
            if (isExperiencePromptOpen())
            {
                selectRandomExperienceOption();
                return;
            }

            walkAndTalk(npc);
            return;
        }

        if (!toggledSettings && configureCameraAfterGielinorGuide())
        {
            return;
        }

        if (progress >= 10)
        {
            // State 10 = leave the Gielinor Guide room, not Survival Expert.
            openTutorialPassageAndWalk(
                    9708,
                    new WorldPoint(3098, 3095, 0),
                    4,
                    () -> Microbot.getVarbitPlayerValue(281) >= 20
                            || isInArea(SURVIVAL_AREA));
            return;
        }

        walkAndTalk(npc);
    }

    private void survivalGuide()
    {
        int progress = Microbot.getVarbitPlayerValue(281);

        if (progress == 10 || progress == 20 || (progress >= 60 && progress < 70))
        {
            talkToSurvivalExpert();
            return;
        }
        if (progress < 40) { clickTab("Inventory"); return; }
        if (progress < 50) { fishShrimp(); return; }
        if (progress < 60) { clickTab("Skills"); return; }

        if (Rs2Inventory.hasItem(ItemID.SHRIMPS) || progress >= 120)
        {
            clearSurvivalActions();
            walkTutorialLocal(COOKING_AREA_WALK_TILE, 3);
            return;
        }

        if (!Rs2Inventory.hasItem("Bronze Axe") || !Rs2Inventory.hasItem("Tinderbox"))
        {
            talkToSurvivalExpert();
            return;
        }

        Rs2TileObjectModel fire = findTutorialFire();
        if (survivalCookingDispatched)
        {
            if (fire == null)
            {
                survivalCookingDispatched = false;
            }
            else if (Rs2Player.isAnimating())
            {
                return;
            }
            else
            {
                survivalCookingDispatched = false;
            }
        }

        if (waitingForSurvivalFire)
        {
            if (fire != null)
            {
                waitingForSurvivalFire = false;
            }
            else if (Rs2Inventory.hasItem("Logs") && !Rs2Player.isAnimating() && !Rs2Player.isMoving())
            {
                waitingForSurvivalFire = false;
            }
            else return;
        }

        if (fire != null)
        {
            if (Rs2Inventory.hasItem(ItemID.RAW_SHRIMPS_2514)) cookShrimpOnFire(fire);
            else if (!Rs2Inventory.hasItem(ItemID.SHRIMPS)) fishShrimp();
            return;
        }

        if (Rs2Inventory.hasItem("Logs")) { lightFire(); return; }
        if (progress >= 90 && !Rs2Inventory.hasItem(ItemID.RAW_SHRIMPS_2514)) { fishShrimp(); return; }
        cutTree();
    }

    private void clearSurvivalActions()
    {
        waitingForSurvivalFire = false;
        survivalCookingDispatched = false;
        treeActionDispatched = false;
        fishingActionDispatched = false;
    }

    private boolean configureCameraAfterGielinorGuide()
    {
        if (isInDialogue() || hasContinue())
        {
            if (hasContinue())
            {
                clickContinue();
            }
            return true;
        }

        if (Rs2Tab.getCurrentTab() != InterfaceTab.SETTINGS)
        {
            Rs2Tab.switchTo(InterfaceTab.SETTINGS);
            return true;
        }

        Rs2Camera.setZoom(DEFAULT_CAMERA_ZOOM);

        if (Rs2Camera.getPitch() <= 250)
        {
            Rs2Camera.setPitch(280);
            return true;
        }

        toggledSettings = true;
        return true;
    }

    private boolean openCookingGate()
    {
        if (Microbot.getVarbitPlayerValue(281) != 120 || isInArea(COOKING_AREA))
        {
            return true;
        }

        if (!Rs2Player.isMoving() && !Rs2Player.isInteracting())
            clickNearestTutorialObject(ObjectID.GATE_9470, "Open");
        walkTutorialLocal(COOKING_AREA_WALK_TILE, 3);

        return false;
    }

    private boolean walkToAccountGuideRoomAndTalk(Rs2NpcModel npc)
    {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null)
        {
            return false;
        }

        // The Account Guide may only load after entering the room.
        if (playerLocation.getX() < ACCOUNT_GUIDE_ROOM_MIN_X)
        {
            clickNearestTutorialObject(ACCOUNT_GUIDE_DOOR_ID, "Open");
            if (npc != null && npc.getWorldLocation() != null)
                walkTutorialLocal(npc.getWorldLocation(), 2);
            return false;
        }

        if (npc == null || npc.getWorldLocation() == null)
        {
            logTutorialTransition("Account Guide not visible", Microbot.getVarbitPlayerValue(281));
            return false;
        }
        return walkAndTalk(npc, 1);
    }

    private boolean talkToSurvivalExpert()
    {
        if (isInDialogue())
        {
            return true;
        }

        Rs2NpcModel npc = tutorialNpc(NpcID.SURVIVAL_EXPERT);

        if (npc == null)
        {
            npc = tutorialNpc("Survival Expert");
        }

        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        WorldPoint npcLocation = npc != null ? npc.getWorldLocation() : null;

        if (playerLocation == null)
        {
            return false;
        }

        if (npcLocation == null)
        {
            walkToArea(SURVIVAL_AREA);
            return false;
        }

        if (playerLocation.distanceTo(npcLocation) > 4)
        {
            walkTutorialLocal(npcLocation, 4);
            return false;
        }

        Rs2Walker.clearWalkingRoute("ksp_account_builder_survival_expert");

        // This direct NPC interaction previously bypassed the tutorial's
        // existing acknowledgement guard and could issue Talk-to repeatedly
        // while the first click was still being processed.
        String interactionKey = "npc:survival-expert";
        if (isTutorialInteractionPending(interactionKey)
                || Rs2Player.isMoving()
                || Rs2Player.isAnimating()
                || Rs2Player.isInteracting())
        {
            return false;
        }
        if (!npc.click("Talk-to"))
        {
            return false;
        }
        markTutorialInteraction(interactionKey);
        return true;
    }

    private void cookingGuide()
    {
        int progress = Microbot.getVarbitPlayerValue(281);

        if (Rs2Inventory.contains("Bread"))
        {
            doughMixDispatched = breadCookingDispatched = false;
            openTutorialPassageAndWalk(9710, QUEST_GUIDE_WALK_TILE, 3,
                    () -> Microbot.getVarbitPlayerValue(281) >= 200);
            return;
        }

        if (Rs2Inventory.contains("Bread dough"))
        {
            doughMixDispatched = false;
            if (breadCookingDispatched && (Rs2Player.isAnimating()
                    || isTutorialInteractionPending("cooking:range-use"))) return;
            breadCookingDispatched = false;

            Rs2TileObjectModel range = tutorialObject(9736);
            if (range == null) range = tutorialObject("Range");
            if (range == null)
            {
                logMissingTutorialObject("Range", 9736);
                return;
            }
            if (!prepareTutorialObjectInteraction(range, 4) || !readyForAction()) return;

            // Bread dough is already produced. Interact with the stove/range
            // directly rather than selecting dough from the inventory.
            if (range.click("Cook") || range.click("Use"))
            {
                markTutorialInteraction("cooking:range-use");
                breadCookingDispatched = true;
                Microbot.log("[TutIsland] Cooking range action dispatched | objectId="
                        + range.getId() + " progress=" + progress);
            }
            return;
        }

        if (progress == 120) { openCookingGate(); return; }
        if (progress == 130)
        {
            if (walkToArea(COOKING_AREA, COOKING_AREA_WALK_TILE))
            {
                openTutorialPassage(ObjectID.DOOR_9709, () -> Microbot.getVarbitPlayerValue(281) != 130);
            }
            return;
        }
        if (progress == 140)
        {
            walkAndTalk(tutorialNpc(NpcID.MASTER_CHEF));
            return;
        }

        if (progress >= 150 && progress < 200) mixBreadDough();
    }

    private void mixBreadDough()
    {
        if (doughMixDispatched)
        {
            if (!Rs2Inventory.contains("Bucket of water") || !Rs2Inventory.contains("Pot of flour"))
            {
                doughMixDispatched = false;
            }
            else if (!Rs2Inventory.isItemSelected())
            {
                doughMixDispatched = false;
            }
            else return;
        }

        if (Rs2Inventory.contains("Bucket of water") && Rs2Inventory.contains("Pot of flour"))
        {
            doughMixDispatched = Rs2Inventory.combine("Bucket of water", "Pot of flour");
        }
    }

    private void questGuide()
    {
        Rs2NpcModel npc = tutorialNpc(NpcID.QUEST_GUIDE);
        int progress = Microbot.getVarbitPlayerValue(281);

        if (progress == 200 || progress == 210)
        {
            if (!walkToQuestGuideDoorTile())
            {
                return;
            }

            openTutorialPassage(
                    9716,
                    () -> Microbot.getVarbitPlayerValue(281) >= 220 || isNpcReachable(npc, 4));
            return;
        }

        if (progress == 220 || progress == 240)
        {
            walkAndTalk(npc);
            return;
        }

        if (progress == 230)
        {
            clickTab("Quest List");
            return;
        }

        if (Rs2Tab.getCurrentTab() != InterfaceTab.INVENTORY)
        {
            Rs2Tab.switchTo(InterfaceTab.INVENTORY);
            return;
        }

        Rs2TileObjectModel ladder = tutorialObject(9726);

        if (prepareTutorialObjectInteraction(ladder, 4))
        {
            // Do not dispatch another ladder click while the previous action
            // is awaiting movement or the tutorial's state transition.
            if (readyForAction() && ladder.click("Climb-down"))
            {
                markTutorialInteraction("object:9726:Climb-down");
            }
        }
    }

    private void miningGuide()
    {
        int progress = Microbot.getVarbitPlayerValue(281);
        long now = System.currentTimeMillis();
        if (progress != lastMiningProgress)
        {
            Microbot.log("[TutIsland] Mining stage " + lastMiningProgress + " -> " + progress
                    + " | player=" + Rs2Player.getWorldLocation()
                    + " tin=" + Rs2Inventory.count("Tin ore")
                    + " copper=" + Rs2Inventory.count("Copper ore")
                    + " pickaxe=" + Rs2Inventory.hasItem("Bronze pickaxe"));
            lastMiningProgress = progress;
            lastMiningStageAtMs = now;
            pendingInteractionAtMs = 0L;
            pendingInteractionKey = null;
        }

        if (now - lastMiningDiagnosticAtMs >= MINING_DIAGNOSTIC_INTERVAL_MS)
        {
            lastMiningDiagnosticAtMs = now;
            Microbot.log("[TutIsland] Mining heartbeat | progress=" + progress
                    + " player=" + Rs2Player.getWorldLocation()
                    + " tin=" + Rs2Inventory.count("Tin ore")
                    + " copper=" + Rs2Inventory.count("Copper ore")
                    + " moving=" + Rs2Player.isMoving()
                    + " animating=" + Rs2Player.isAnimating()
                    + " walkerTarget=" + Rs2Walker.getCurrentTarget());
        }

        switch (progress)
        {
            case 260:
            case 290:
            case 330:
                walkAndTalk(tutorialNpc(NpcID.MINING_INSTRUCTOR));
                return;
            case 270:
                // Legacy tutorial stages still occur on some clients, but
                // Prospect was removed from most rocks in March 2023.
                prospectOrMineTutorialRock(TUTORIAL_TIN_ROCK_ID, "Tin ore");
                return;
            case 280:
                prospectOrMineTutorialRock(TUTORIAL_COPPER_ROCK_ID, "Copper ore");
                return;
            case 300:
                mineTutorialRock(TUTORIAL_TIN_ROCK_ID, "Tin ore");
                return;
            case 310:
                mineTutorialRock(TUTORIAL_COPPER_ROCK_ID, "Copper ore");
                return;
            case 320:
                if (!Rs2Inventory.hasItem("Tin ore")) {
                    mineTutorialRock(TUTORIAL_TIN_ROCK_ID, "Tin ore");
                    return;
                }
                if (!Rs2Inventory.hasItem("Copper ore")) {
                    mineTutorialRock(TUTORIAL_COPPER_ROCK_ID, "Copper ore");
                    return;
                }
                smeltTutorialBronze();
                return;
            case 340:
                openTutorialAnvil();
                return;
            case 350:
                smithTutorialDagger();
                return;
            default:
                if (progress >= 360 || Rs2Inventory.contains("Bronze dagger"))
                {
                    openTutorialPassage(ObjectID.GATE_9718,
                            () -> Microbot.getVarbitPlayerValue(281) > 360 || isInArea(COMBAT_INSTRUCTOR_AREA));
                }
        }
    }

    private void prospectOrMineTutorialRock(int objectId, String ore)
    {
        Rs2TileObjectModel rock = findTutorialMiningRock(objectId, ore);
        if (rock == null)
        {
            debugMiningRockMissing(objectId, ore, "Prospect or Mine");
            return;
        }
        if (rock.hasAction("Prospect"))
        {
            interactWithTutorialMiningRock(rock, "Prospect");
            return;
        }
        // Some versions lack the historical Prospect action. Mine only the
        // correct ore; never click an unidentified rock or unrelated object.
        if (!Rs2Inventory.hasItem(ore))
            interactWithTutorialMiningRock(rock, "Mine");
        else if (System.currentTimeMillis() - lastMiningStageAtMs >= 5_000L)
            walkAndTalk(tutorialNpc(NpcID.MINING_INSTRUCTOR));
    }

    private void mineTutorialRock(int objectId, String ore)
    {
        if (Rs2Inventory.hasItem(ore))
        {
            if (System.currentTimeMillis() - lastMiningStageAtMs >= 6_000L)
                walkAndTalk(tutorialNpc(NpcID.MINING_INSTRUCTOR));
            return;
        }
        Rs2TileObjectModel rock = findTutorialMiningRock(objectId, ore);
        if (rock == null)
        {
            debugMiningRockMissing(objectId, ore, "Mine");
            return;
        }
        interactWithTutorialMiningRock(rock, "Mine");
    }

    /**
     * Select a live object by its specific ore ID, or a clearly labelled
     * variant of that ore. Bare "Rocks" objects of an unknown ID are not
     * eligible because copper and tin are different objectives.
     */
    private Rs2TileObjectModel findTutorialMiningRock(int objectId, String ore)
    {
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null || !MINING_SMITHING_AREA.contains(player)) return null;
        String expectedName = ore.startsWith("Tin") ? "tin" : "copper";
        return KspLiveScene.nearestModel(player, 18,
                object -> object != null
                        && (object.getId() == objectId
                            || (object.getName() != null
                                && object.getName().toLowerCase(java.util.Locale.ROOT).contains(expectedName)
                                && object.getName().toLowerCase(java.util.Locale.ROOT).contains("rock")))
                        && (object.hasAction("Mine") || object.hasAction("Prospect")));
    }

    private void interactWithTutorialMiningRock(Rs2TileObjectModel rock, String action)
    {
        if (rock == null || !rock.hasAction(action)) return;
        // Direct scene-object interaction lets the client approach a nearby
        // rock itself; do not invoke WebWalker, which may bootstrap a
        // mainland bank route inside the tutorial mining instance.
        if (!readyForAction()) return;
        if (rock.click(action))
        {
            markTutorialInteraction("mining:" + rock.getId() + ":" + action);
            Microbot.log("[TutIsland] Mining " + action + " | objectId=" + rock.getId()
                    + " tile=" + rock.getWorldLocation()
                    + " progress=" + lastMiningProgress);
        }
    }

    private void debugMiningRockMissing(int objectId, String ore, String action)
    {
        long now = System.currentTimeMillis();
        if (now - lastMiningDiagnosticAtMs < MINING_DIAGNOSTIC_INTERVAL_MS) return;
        lastMiningDiagnosticAtMs = now;
        Microbot.log("[TutIsland] Mining rock unavailable | action=" + action
                + " ore=" + ore + " expectedObjectId=" + objectId
                + " player=" + Rs2Player.getWorldLocation()
                + " varbit281=" + lastMiningProgress + " - checking live scene");
    }

    private void rejectMainlandWalkerRoute()
    {
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null || !TutAreas.contains(player)) return;
        WorldPoint target = Rs2Walker.getCurrentTarget();
        if (target == null || TutAreas.contains(target)) return;
        Rs2Walker.clearWalkingRoute("ksp_tutorial_reject_mainland_bank_route");
        long now = System.currentTimeMillis();
        if (now - lastTutorialWalkerWarningAtMs >= 5_000L)
        {
            lastTutorialWalkerWarningAtMs = now;
            debug("Tutorial Island cancelled mainland walker destination | player={} target={} progress={}",
                    player, target, Microbot.getVarbitPlayerValue(281));
        }
    }

    private void smeltTutorialBronze()
    {
        if (Rs2Inventory.contains("Bronze bar") || Rs2Player.isAnimating()) return;
        Rs2TileObjectModel furnace = tutorialObject(ObjectID.FURNACE_10082);
        if (furnace == null) furnace = tutorialObject("Furnace");
        if (furnace == null)
        {
            logMissingTutorialObject("Furnace", ObjectID.FURNACE_10082);
            return;
        }
        // Use the live object from the underground scene directly. Calling
        // prepareTutorialObjectInteraction() here invokes walkTo(), whose
        // short-distance bank comparison attempts to route to Lumbridge.
        if (!readyForAction()) return;

        // The tutorial furnace exposes a direct live "Use" interaction.
        // Prefer it over selecting ore: selecting inventory items can race
        // with the asynchronous game client and leave this step stalled.
        if (furnace.hasAction("Use"))
        {
            if (isTutorialInteractionPending("object:furnace:Use")) return;
            if (furnace.click("Use"))
            {
                markTutorialInteraction("object:furnace:Use");
                Microbot.log("[TutIsland] Furnace Use dispatched | tile="
                        + furnace.getWorldLocation() + " progress="
                        + Microbot.getVarbitPlayerValue(281));
                return;
            }
        }

        // Fallback for furnace variants without a usable "Use" action.
        if (!Rs2Inventory.hasItem(ItemID.TIN_ORE)
                || !Rs2Inventory.hasItem(ItemID.COPPER_ORE)) return;

        int ore = ItemID.TIN_ORE;
        if (!useTutorialItemOnLiveObject(ore, furnace, "object:furnace:smelt-bronze"))
            logTutorialInteractionPending("furnace", furnace, ore);
    }

    private void openTutorialAnvil()
    {
        if (Rs2Widget.isSmithingWidgetOpen() || Rs2Player.isAnimating()) return;
        Rs2TileObjectModel anvil = tutorialObject("Anvil");
        if (anvil == null) logMissingTutorialObject("Anvil", -1);
        if (!prepareTutorialObjectInteraction(anvil, 4) || !readyForAction()) return;

        // Wait for the previous interaction to be acknowledged before
        // attempting another item-on-object or Smith interaction.
        if (Rs2Inventory.hasItem(ItemID.BRONZE_BAR))
        {
            if (!useTutorialItemOnLiveObject(ItemID.BRONZE_BAR, anvil,
                    "object:anvil:bronze-bar"))
                logTutorialInteractionPending("anvil", anvil, ItemID.BRONZE_BAR);
        }
        else if (anvil.click("Smith"))
        {
            markTutorialInteraction("object:anvil:Smith");
        }
    }

    private void smithTutorialDagger()
    {
        if (Rs2Inventory.contains("Bronze dagger")) return;
        if (Rs2Widget.isSmithingWidgetOpen()) { Rs2Widget.clickWidget(312, 9); return; }
        clickTutorialObject("Anvil", "Smith", 4);
    }

    private void combatGuide()
    {
        int progress = Microbot.getVarbitPlayerValue(281);
        Rs2NpcModel instructor = tutorialNpc(NpcID.COMBAT_INSTRUCTOR);

        if (progress <= 370) { walkAndTalk(instructor); return; }

        switch (progress)
        {
            case 390:
                clickTab("Worn Equipment");
                return;
            case 400:
                if (Rs2Widget.getWidget(84, 1) == null) Rs2Widget.clickWidget(387, 1);
                return;
            case 405:
                equipTutorialDagger();
                return;
            case 410:
                if (Rs2Widget.isWidgetVisible(84, 3)) closeEquipmentStats();
                else walkAndTalk(instructor);
                return;
            case 420:
                equipTutorialItems("Bronze sword", "Wooden shield");
                return;
            case 430:
                clickTab("Combat Options");
                return;
            case 440:
                ensureInsideRatPen();
                return;
            case 450:
                if (readyForAction()) attackNearestRat();
                return;
            case 470:
                leaveRatPenOrTalk(instructor);
                return;
            case 480:
                // Some client builds remain on 480 after equipping both
                // ranged items. Do not idle waiting for a 490 varbit edge:
                // attempt the tutorial rat once the required gear is worn.
                attackRatWithRange();
                return;
            case 490:
                attackRatWithRange();
                return;
            case 500:
                clickTutorialObject("Ladder", "Climb-up", 4);
                return;
            default:
        }
    }

    private void equipTutorialDagger()
    {
        if (Rs2Equipment.isWearing("Bronze dagger")) { closeEquipmentStats(); return; }
        if (Rs2Widget.getWidget(84, 1) != null) Rs2Widget.clickWidget("Bronze dagger");
        else clickTab("Worn Equipment");
    }

    private void equipTutorialItems(String... items)
    {
        if (Rs2Tab.getCurrentTab() != InterfaceTab.INVENTORY)
        {
            Rs2Tab.switchTo(InterfaceTab.INVENTORY);
            return;
        }
        for (String item : items)
        {
            if (Rs2Inventory.hasItem(item) && !Rs2Equipment.isWearing(item))
            {
                Rs2Inventory.wield(item);
                return;
            }
        }
    }

    private void leaveRatPenOrTalk(Rs2NpcModel instructor)
    {
        if (isInsideRatPen(Rs2Player.getWorldLocation()))
        {
            clickNearestTutorialObject(RAT_PEN_GATE_ID, "Open");
        }
        else walkAndTalk(instructor);
    }

    private void equipTutorialRangeGear()
    {
        // During the tutorial 480 stage Vannaka gives a shortbow and 50
        // bronze arrows. Equip each item once. Repeated clicks on a combat
        // style are not required to unlock the ranged rat attack.
        if (equipRequiredTutorialRangedGear()) return;
        if ((!Rs2Equipment.isWearing("Shortbow")
                    || !Rs2Equipment.isWearing("Bronze arrow"))
                && System.currentTimeMillis() - lastRangedDiagnosticAtMs >= 6_000L)
        {
            lastRangedDiagnosticAtMs = System.currentTimeMillis();
            walkAndTalk(tutorialNpc(NpcID.COMBAT_INSTRUCTOR));
            Microbot.log("[TutIsland] Missing ranged gear at Vannaka"
                    + " | bowEquipped=" + Rs2Equipment.isWearing("Shortbow")
                    + " arrowsEquipped=" + Rs2Equipment.isWearing("Bronze arrow"));
            return;
        }
        if (System.currentTimeMillis() - lastRangedDiagnosticAtMs >= 5_000L)
        {
            lastRangedDiagnosticAtMs = System.currentTimeMillis();
            Microbot.log("[TutIsland] Ranged equipment ready; awaiting next tutorial stage"
                    + " | progress=" + Microbot.getVarbitPlayerValue(281)
                    + " shortbow=" + Rs2Equipment.isWearing("Shortbow")
                    + " bronzeArrows=" + Rs2Equipment.isWearing("Bronze arrow"));
        }
    }

    /** @return true if equipping gear or waiting for it from Vannaka. */
    private boolean equipRequiredTutorialRangedGear()
    {
        if (!Rs2Equipment.isWearing("Shortbow"))
        {
            if (Rs2Inventory.hasItem("Shortbow") && readyForAction())
            {
                if (Rs2Inventory.wield("Shortbow"))
                    markTutorialInteraction("ranged:shortbow");
                return true;
            }
            return true;
        }
        if (!Rs2Equipment.isWearing("Bronze arrow"))
        {
            if (Rs2Inventory.hasItem("Bronze arrow") && readyForAction())
            {
                if (Rs2Inventory.wield("Bronze arrow"))
                    markTutorialInteraction("ranged:arrows");
                return true;
            }
            return true;
        }
        return false;
    }

    private void attackRatWithRange()
    {
        if (equipRequiredTutorialRangedGear())
        {
            if (System.currentTimeMillis() - lastRangedDiagnosticAtMs >= 5_000L)
            {
                lastRangedDiagnosticAtMs = System.currentTimeMillis();
                Microbot.log("[TutIsland] Waiting for tutorial ranged gear"
                        + " | shortbowEquipped=" + Rs2Equipment.isWearing("Shortbow")
                        + " arrowsEquipped=" + Rs2Equipment.isWearing("Bronze arrow")
                        + " bowInventory=" + Rs2Inventory.hasItem("Shortbow")
                        + " arrowsInventory=" + Rs2Inventory.hasItem("Bronze arrow"));
            }
            return;
        }

        // All bow styles are permitted by the tutorial. The old code clicked
        // Longrange on EVERY 100ms tick and starved Attack interactions.
        // Never change combat style during this rat-killing stage.
        Actor target = Rs2Player.getInteracting();
        if (target != null && "giant rat".equalsIgnoreCase(target.getName())
                && (Rs2Player.isAnimating()
                    || System.currentTimeMillis() - lastRangedAttackAtMs < 4_000L))
        {
            return;
        }
        if (!readyForAction()) return;
        if (attackNearestRat())
        {
            lastRangedAttackAtMs = System.currentTimeMillis();
            return;
        }
        if (System.currentTimeMillis() - lastRangedDiagnosticAtMs >= 5_000L)
        {
            lastRangedDiagnosticAtMs = System.currentTimeMillis();
            Microbot.log("[TutIsland] Ranged rat attack retry | progress="
                    + Microbot.getVarbitPlayerValue(281)
                    + " player=" + Rs2Player.getWorldLocation()
                    + " target=" + (target == null ? "none" : target.getName())
                    + " lastRatIndex=" + lastRangedTargetIndex
                    + " moving=" + Rs2Player.isMoving()
                    + " animating=" + Rs2Player.isAnimating());
        }
    }

    private void bankerGuide()
    {
        Rs2NpcModel npc = tutorialNpc(NpcID.ACCOUNT_GUIDE);
        int progress = Microbot.getVarbitPlayerValue(281);

        if (progress == 510)
        {
            if (Rs2Bank.isOpen())
            {
                return;
            }

            WorldPoint bankEntrance = new WorldPoint(3120, 3124, 0);
            WorldPoint playerLocation = Rs2Player.getWorldLocation();

            if (playerLocation == null)
            {
                return;
            }

            if (playerLocation.distanceTo(bankEntrance) > 5)
            {
                walkTutorialLocal(bankEntrance, 5);
                return;
            }

            // Only the Tutorial Island booth at this bank room is valid.
            // Never use Rs2Bank.openBank/walkToBank, which can choose
            // Lumbridge Castle's upstairs bank while still on the island.
            if (!readyForAction()) return;
            Rs2TileObjectModel tutorialBooth = KspLiveScene.nearestModel(
                    playerLocation, 18, object ->
                    object != null && object.getWorldLocation() != null
                    && TUT_ISLAND_BANK_AREA.contains(object.getWorldLocation())
                    && (object.getId() == ObjectID.BANK_BOOTH_10083
                        || "Bank booth".equalsIgnoreCase(object.getName()))
                    // Tutorial Island is special: these booths advertise
                    // "Use", not the mainland "Bank" action.
                    && (object.hasAction("Use") || object.hasAction("Bank")));
            if (tutorialBooth == null)
            {
                if (System.currentTimeMillis() - lastTutorialWalkerWarningAtMs >= 5_000L)
                {
                    lastTutorialWalkerWarningAtMs = System.currentTimeMillis();
                    Microbot.log("[TutIsland] Waiting for LOCAL bank booth"
                            + " | progress=" + progress + " player=" + playerLocation
                            + " expectedActions=[Use,Bank] expectedId="
                            + ObjectID.BANK_BOOTH_10083
                            + " allowedRoom=" + TUT_ISLAND_BANK_AREA);
                }
                return;
            }
            if (prepareTutorialObjectInteraction(tutorialBooth, 4)
                    && readyForAction())
            {
                String boothAction = tutorialBooth.hasAction("Use") ? "Use" : "Bank";
                if (tutorialBooth.click(boothAction))
                {
                    markTutorialInteraction("tutorial:bank-booth");
                    Microbot.log("[TutIsland] Dispatched Tutorial Island bank booth"
                            + " | action=" + boothAction + " id=" + tutorialBooth.getId()
                            + " tile=" + tutorialBooth.getWorldLocation()
                            + " progress=" + progress);
                }
            }
            return;
        }

        if (progress == 520)
        {
            handleBankSpaceAndPollBooth();
            return;
        }

        if (progress == 525 || progress == 530)
        {
            if (closePollOrOptionsWidget())
            {
                return;
            }

            walkToAccountGuideRoomAndTalk(npc);
            return;
        }

        if (progress == 531)
        {
            clickTab("Account Management");
            return;
        }

        if (progress == 532)
        {
            walkToAccountGuideRoomAndTalk(npc);
        }
    }

    private void advanceThroughTutorialDoor(int doorId, WorldPoint destination, int progress)
    {
        Rs2TileObjectModel door = tutorialObject(doorId);
        if (door != null && door.hasAction("Open"))
        {
            if (clickTutorialObject(doorId, "Open", 4))
            {
                logTutorialTransition("Opened progression door " + doorId, progress);
            }
            return;
        }

        // Opening can morph/remove the closed door. Only then walk toward
        // the next section; do not blindly click a different object.
        walkTutorialLocal(destination, 3);
        logTutorialTransition("Waiting for door " + doorId + " or next room", progress);
    }

    private boolean ensurePrayerChapelEntrance(Rs2NpcModel npc)
    {
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null)
        {
            return false;
        }
        if (player.distanceTo(CHAPEL_INSTRUCTOR_TILE) > 6)
        {
            walkTutorialLocal(CHAPEL_INSTRUCTOR_TILE, 5);
            return false;
        }

        // The inner chapel entrance is distinct from door 9722 and can
        // remain closed after the tutorial advances from 540 to 550.
        Rs2TileObjectModel innerDoor = KspLiveScene.nearestModel(player, 9,
                object -> object != null && object.getWorldLocation() != null
                        && (object.getId() == CHAPEL_INTERIOR_DOOR_ID
                            || object.getId() == CHAPEL_INTERIOR_OPEN_DOOR_ID)
                        && object.getWorldLocation().distanceTo(CHAPEL_INSTRUCTOR_TILE) <= 5
                        && object.hasAction("Open"));
        if (innerDoor != null)
        {
            if (prepareTutorialObjectInteraction(innerDoor, 3)
                    && readyForAction() && innerDoor.click("Open"))
            {
                markTutorialInteraction("chapel:inner-door");
                logTutorialTransition("Opened inner chapel door " + innerDoor.getId(), 550);
            }
            return false;
        }

        if (npc == null || npc.getWorldLocation() == null)
        {
            walkTutorialLocal(CHAPEL_INSTRUCTOR_TILE, 2);
            logTutorialTransition("Brother Brace not visible", 550);
            return false;
        }
        return true;
    }

    private void logTutorialTransition(String message, int progress)
    {
        long now = System.currentTimeMillis();
        if (now - lastTutorialTransitionDiagnosticAtMs < 5_000L)
        {
            return;
        }
        lastTutorialTransitionDiagnosticAtMs = now;
        Microbot.log("[TutIsland] " + message + " | progress=" + progress
                + " player=" + Rs2Player.getWorldLocation());
    }

    private void prayerGuide()
    {
        Rs2NpcModel npc = tutorialNpc(NpcID.BROTHER_BRACE);
        int progress = Microbot.getVarbitPlayerValue(281);

        if (progress == 540)
        {
            advanceThroughTutorialDoor(CHAPEL_PASSAGE_DOOR_ID, CHAPEL_INSTRUCTOR_TILE, progress);
            return;
        }

        if (progress == 550)
        {
            if (ensurePrayerChapelEntrance(npc))
            {
                walkAndTalk(npc);
            }
            return;
        }

        if (progress == 560)
        {
            clickTab("Prayer");
            return;
        }

        if (progress == 570)
        {
            walkAndTalk(npc);
            return;
        }

        if (progress == 580)
        {
            clickTab("Friends list");
            return;
        }

        if (progress == 590)
        {
            clickTab("Ignore list");
            return;
        }

        if (progress == 600)
        {
            walkAndTalk(npc);
        }
    }

    private void mageGuide()
    {
        Rs2NpcModel npc = tutorialNpc(NpcID.MAGIC_INSTRUCTOR);
        int progress = Microbot.getVarbitPlayerValue(281);

        if (progress == 610)
        {
            advanceThroughTutorialDoor(MAGIC_PASSAGE_DOOR_ID, MAGIC_INSTRUCTOR_TILE, progress);
            return;
        }

        if (progress == 620)
        {
            if (npc == null)
            {
                walkTutorialLocal(MAGIC_INSTRUCTOR_TILE, 3);
                logTutorialTransition("Magic Instructor not visible", progress);
                return;
            }
            walkAndTalk(npc);
            return;
        }

        if (progress == 630)
        {
            clickTab("Magic");
            return;
        }

        if (progress == 640)
        {
            walkAndTalk(npc);
            return;
        }

        if (progress == 650)
        {
            widgetCast();
            return;
        }

        if (progress >= 660 && progress < 1000)
        {
            if (isInDialogue())
            {
                handleFinalMageDialogue();
                return;
            }

            if (progress >= 680)
            {
                castLumbridgeHomeTeleport();
                return;
            }

            walkAndTalk(npc);
        }
    }

    private void handleFinalMageDialogue()
    {
        if (hasSelectAnOption())
        {
            // The subsequent Ironman question can have the same varbit
            // value as the mainland question. Only select known safe text.
            if (Rs2Dialogue.keyPressForDialogueOption("No, I'm not planning to do that.")) return;
            if (Rs2Dialogue.keyPressForDialogueOption("No, I'm not planning to do that")) return;
            if (Rs2Dialogue.keyPressForDialogueOption("No, I don't want to be an Ironman")) return;
            if (Rs2Dialogue.keyPressForDialogueOption("No, I don't want to become an Ironman")) return;

            if (Rs2Dialogue.keyPressForDialogueOption("Yes, I'd like to go to the mainland")) return;
            if (Rs2Dialogue.keyPressForDialogueOption("Yes, send me to the mainland")) return;

            // Accept the short 'Yes.' only when the visible prompt is
            // explicitly about travelling to the mainland, not account type.
            String prompt = Rs2Dialogue.getQuestion();
            if (prompt == null || prompt.isEmpty())
            {
                prompt = Rs2Dialogue.getDialogueText();
            }
            String text = prompt == null ? "" : prompt.toLowerCase(java.util.Locale.ROOT);
            if (text.contains("mainland") && !text.contains("ironman"))
            {
                if (Rs2Dialogue.keyPressForDialogueOption("Yes.")) return;
            }

            logTutorialTransition("Unrecognized final dialogue; no option selected"
                    + " question=" + prompt
                    + " options=" + Rs2Dialogue.getDialogueOptions().stream()
                            .map(Widget::getText).collect(java.util.stream.Collectors.toList()),
                    Microbot.getVarbitPlayerValue(281));
            return;
        }

        if (hasContinue())
        {
            Rs2Dialogue.clickContinue();
        }
    }

    private boolean castLumbridgeHomeTeleport()
    {
        WorldPoint location = Rs2Player.getWorldLocation();
        if (Microbot.getVarbitPlayerValue(281) >= 1000
                || (location != null && !TutAreas.contains(location)))
        {
            homeTeleportDispatched = false;
            return true;
        }

        if (homeTeleportDispatched)
        {
            if (Rs2Player.isAnimating() || Rs2Player.isInteracting())
            {
                return true;
            }

            homeTeleportDispatched = false;
        }

        if (Rs2Tab.getCurrentTab() != InterfaceTab.MAGIC)
        {
            Rs2Tab.switchTo(InterfaceTab.MAGIC);
            return false;
        }

        Widget homeTeleport = Rs2Widget.findWidget("Lumbridge Home Teleport", true);

        if (homeTeleport == null)
        {
            homeTeleport = Rs2Widget.findWidget("Home Teleport", true);
        }

        if (homeTeleport == null)
        {
            return false;
        }

        if (Rs2Widget.clickWidget(homeTeleport))
        {
            homeTeleportDispatched = true;
            return true;
        }

        return false;
    }

    // -------------------------------------------------------------------------
    // Completion and login queue
    // -------------------------------------------------------------------------

    private void handleTutorialComplete()
    {
        if (!shouldRunMultipleAccounts())
        {
            completionState = "Finished";
            shutdown();
            return;
        }

        completionState = "Logging out";

        if (!completionLogoutRequested)
        {
            completionLogoutRequested = true;
            resetAccountProgressState();
        }

        long now = System.currentTimeMillis();
        if (now - lastCompletionLogoutAttemptAtMs < COMPLETION_LOGOUT_RETRY_MS)
        {
            return;
        }

        lastCompletionLogoutAttemptAtMs = now;
        Rs2Player.logout();
    }

    private void handleQueuedLogin()
    {
        if (!shouldRunMultipleAccounts())
        {
            return;
        }

        if (isRuleBreakingLoginBlockVisible())
        {
            skipFailedQueuedLogin();
            clickLoginBackButton();
            return;
        }

        AccountQueueEntry account = getNextQueuedAccount();

        if (account == null)
        {
            completionState = "Queue empty";
            return;
        }

        long now = System.currentTimeMillis();

        if (isQueuedLoginTimedOut(now))
        {
            skipFailedQueuedLogin();
            return;
        }

        if (now - lastQueueLoginAttemptAtMs < QUEUE_LOGIN_RETRY_MS)
        {
            return;
        }

        lastQueueLoginAttemptAtMs = now;
        queuedAccountName = account.username;
        completionState = "Logging in";

        if (pendingQueuedLoginIndex < 0 || pendingQueuedLoginIndex != accountQueueIndex)
        {
            pendingQueuedLoginIndex = accountQueueIndex;
            queuedLoginStartedAtMs = now;
        }

        int world = account.world > 0 ? account.world : Login.getRandomWorld(false);
        performQueuedLogin(account, world);
    }

    private boolean performQueuedLogin(AccountQueueEntry account, int world)
    {
        Client client = Microbot.getClient();

        if (client == null)
        {
            return false;
        }

        try
        {
            LoginManager.setWorld(world);
        }
        catch (Exception ignored)
        {
        }

        if (client.getLoginIndex() == 3 || client.getLoginIndex() == 24)
        {
            Rs2Keyboard.keyPress(KeyEvent.VK_ENTER);
            sleep(randomDelay(700, 1200));
        }

        client.setUsername(account.username);
        sleep(randomDelay(QUEUED_LOGIN_EMAIL_PASSWORD_DELAY_MIN_MS, QUEUED_LOGIN_EMAIL_PASSWORD_DELAY_MAX_MS));
        client.setPassword(account.password);
        sleep(randomDelay(400, 900));
        Rs2Keyboard.keyPress(KeyEvent.VK_ENTER);
        sleep(randomDelay(350, 700));
        Rs2Keyboard.keyPress(KeyEvent.VK_ENTER);

        return true;
    }

    private void completeQueuedLoginIfNeeded()
    {
        if (pendingQueuedLoginIndex < 0)
        {
            return;
        }

        accountQueueIndex = Math.max(accountQueueIndex, pendingQueuedLoginIndex + 1);
        pendingQueuedLoginIndex = -1;
        queuedLoginStartedAtMs = 0L;
        completionState = "Active";
    }

    private boolean isQueuedLoginTimedOut(long now)
    {
        return pendingQueuedLoginIndex >= 0
                && queuedLoginStartedAtMs > 0
                && now - queuedLoginStartedAtMs >= QUEUE_LOGIN_SKIP_MS;
    }

    private void skipFailedQueuedLogin()
    {
        accountQueueIndex = Math.max(accountQueueIndex, pendingQueuedLoginIndex + 1);
        pendingQueuedLoginIndex = -1;
        queuedLoginStartedAtMs = 0L;
        lastQueueLoginAttemptAtMs = 0L;
        completionState = "Skipped login";
    }

    private boolean isRuleBreakingLoginBlockVisible()
    {
        return Rs2Widget.hasWidget("Your account has been involved")
                || Rs2Widget.hasWidget("serious rule breaking")
                || Rs2Widget.hasWidget("View Appeal Options");
    }

    private void clickLoginBackButton()
    {
        Widget backButton = Rs2Widget.findWidget("Back", true);

        if (backButton != null)
        {
            Rs2Widget.clickWidget(backButton);
            sleep(randomDelay(600, 1200));
            return;
        }

        Client client = Microbot.getClient();

        if (client == null)
        {
            return;
        }

        int loginBoxWidth = 804;
        int loginBoxX = (client.getCanvasWidth() / 2) - (loginBoxWidth / 2);
        int buttonX = loginBoxX + 365;
        int buttonY = 222;

        Microbot.getMouse().click(buttonX, buttonY);
        sleep(randomDelay(600, 1200));
    }

    private AccountQueueEntry getNextQueuedAccount()
    {
        List<AccountQueueEntry> accounts = parseAccountQueue();

        if (accountQueueIndex >= accounts.size())
        {
            return null;
        }

        return accounts.get(accountQueueIndex);
    }

    private List<AccountQueueEntry> parseAccountQueue() { return new ArrayList<>(); }

    private boolean shouldRunMultipleAccounts() { return false; }

    // -------------------------------------------------------------------------
    // State reset
    // -------------------------------------------------------------------------

    private void resetAccountState()
    {
        completionLogoutRequested = false;
        lastCompletionLogoutAttemptAtMs = 0L;
        accountQueueIndex = 0;
        pendingQueuedLoginIndex = -1;
        lastQueueLoginAttemptAtMs = 0L;
        queuedLoginStartedAtMs = 0L;
        queuedAccountName = "None";
        completionState = "Active";
        resetAccountProgressState();
    }

    private void resetAccountProgressState()
    {
        toggledSettings = false;
        ownFireLocation = null;
        lastQueueLoginAttemptAtMs = 0L;
        lastGeneratedName = "None";
        nameLookupPending = false;
        nameSetPending = false;
        characterCustomized = false;
        characterConfirmDispatched = false;
        experienceSelectionDispatched = false;
        lastCharacterAction = "Waiting";
        lastExperienceSelection = "None";
        waitingForSurvivalFire = false;
        survivalCookingDispatched = false;
        doughMixDispatched = false;
        breadCookingDispatched = false;
        treeActionDispatched = false;
        fishingActionDispatched = false;
        homeTeleportDispatched = false;
        windStrikeSelected = false;
        pendingInteractionAtMs = 0L;
        pendingInteractionKey = null;
        lastMiningProgress = -1;
        lastMiningStageAtMs = 0L;
        lastMiningDiagnosticAtMs = 0L;
        lastTutorialWalkerWarningAtMs = 0L;
        lastRangedDiagnosticAtMs = 0L;
        lastRangedAttackAtMs = 0L;
        lastRangedTargetIndex = -1;
        monitoredState = null;
        monitoredProgress = -1;
        stateEnteredAtMs = 0L;
        lastStateDiagnosticAtMs = 0L;
        lastStateRetryAtMs = 0L;
        currentStateRetries = 0;
    }

    // -------------------------------------------------------------------------
    // NPC walk/talk helpers
    // -------------------------------------------------------------------------

    private boolean walkAndTalk(Rs2NpcModel npc) { return walkAndTalk(npc, 2); }

    private boolean walkAndTalk(Rs2NpcModel npc, int reach)
    {
        return walkAndAct(npc, reach, "Talk-to", null);
    }

    private boolean walkAndAct(Rs2NpcModel npc, int reach, String action, Runnable afterClick)
    {
        if (npc == null)
        {
            return false;
        }

        WorldPoint npcLocation = npc.getWorldLocation();
        WorldPoint playerLocation = Rs2Player.getWorldLocation();

        if (npcLocation == null || playerLocation == null)
        {
            return false;
        }

        if (playerLocation.distanceTo(npcLocation) > reach)
        {
            walkTutorialLocal(npcLocation, reach);
            return false;
        }

        Rs2Walker.clearWalkingRoute("ksp_account_builder_tutorial_npc_interaction");

        String interactionKey = "npc:" + npc.getIndex() + ":" + action;
        if (isTutorialInteractionPending(interactionKey)
                || Rs2Player.isMoving()
                || Rs2Player.isAnimating()
                || Rs2Player.isInteracting())
        {
            return false;
        }

        if (!npc.click(action))
        {
            return false;
        }
        markTutorialInteraction(interactionKey);

        if (afterClick != null)
        {
            afterClick.run();
        }

        return true;
    }

    // -------------------------------------------------------------------------
    // Combat helpers
    // -------------------------------------------------------------------------

    private boolean walkAndAttackRat()
    {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();

        if (!isInsideRatPen(playerLocation))
        {
            if (!ensureInsideRatPen())
            {
                return false;
            }
        }

        return attackNearestRat();
    }

    private boolean ensureInsideRatPen()
    {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();

        if (isInsideRatPen(playerLocation))
        {
            return true;
        }

        if (!isTutorialInteractionPending("rat-gate")
                && clickNearestTutorialObject(RAT_PEN_GATE_ID, "Open"))
        {
            markTutorialInteraction("rat-gate");
        }
        return false;
    }

    private boolean isInsideRatPen(WorldPoint location)
    {
        return location != null
                && location.getPlane() == RAT_PIT_AREA.getPlane()
                && location.getX() >= RAT_PIT_AREA.getX()
                && location.getX() <= RAT_PEN_INNER_BOUNDARY_X
                && location.getY() >= RAT_PIT_AREA.getY()
                && location.getY() < RAT_PIT_AREA.getY() + RAT_PIT_AREA.getHeight();
    }

    private boolean attackNearestRat()
    {
        Rs2NpcModel rat = tutorialNpc("Giant rat");

        if (rat == null)
        {
            return false;
        }

        String key = "rat:" + rat.getIndex();
        if (isTutorialInteractionPending(key)
                || Rs2Player.isMoving()
                || Rs2Player.isAnimating()
                || Rs2Player.isInteracting()) return false;
        if (!rat.click("Attack")) return false;
        markTutorialInteraction(key);
        if (Microbot.getVarbitPlayerValue(281) == 490)
        {
            lastRangedTargetIndex = rat.getIndex();
            Microbot.log("[TutIsland] Ranged Attack dispatched | ratId="
                    + rat.getId() + " npcIndex=" + rat.getIndex()
                    + " player=" + Rs2Player.getWorldLocation());
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Skilling helpers
    // -------------------------------------------------------------------------

    private void lightFire()
    {
        if (waitingForSurvivalFire || !Rs2Inventory.hasItem("Logs"))
        {
            return;
        }

        WorldPoint fireLocation = Rs2Player.getWorldLocation();
        if (fireLocation == null)
        {
            return;
        }

        if (Rs2Player.isStandingOnGameObject())
        {
            WorldPoint nearestWalkable = Rs2Tile.getNearestWalkableTileWithLineOfSight(fireLocation);
            if (nearestWalkable != null && !Rs2Player.isMoving())
            {
                Rs2Walker.walkFastCanvas(nearestWalkable);
            }
            return;
        }

        if (!Rs2Inventory.combine("Tinderbox", "Logs"))
        {
            return;
        }

        ownFireLocation = fireLocation;
        waitingForSurvivalFire = true;
        treeActionDispatched = false;
    }

    private void cutTree()
    {
        if (Rs2Inventory.hasItem("Logs"))
        {
            treeActionDispatched = false;
            return;
        }

        if (treeActionDispatched)
        {
            if (Rs2Player.isAnimating())
            {
                return;
            }
            treeActionDispatched = false;
        }

        Rs2TileObjectModel tree = tutorialObject("Tree");

        if (!prepareTutorialObjectInteraction(tree, 4))
        {
            return;
        }

        if (tree.click("Chop down"))
        {
            treeActionDispatched = true;
        }
    }

    private void fishShrimp()
    {
        if (Rs2Inventory.hasItem(ItemID.RAW_SHRIMPS_2514)
                || Rs2Inventory.hasItem(ItemID.SHRIMPS))
        {
            fishingActionDispatched = false;
            return;
        }

        if (fishingActionDispatched)
        {
            if (Rs2Player.isAnimating())
            {
                return;
            }
            fishingActionDispatched = false;
        }

        Rs2NpcModel fishingSpot = tutorialNpc(NpcID.FISHING_SPOT_3317);

        if (fishingSpot == null || fishingSpot.getWorldLocation() == null)
        {
            return;
        }

        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        WorldPoint spotLocation = fishingSpot.getWorldLocation();

        if (playerLocation == null)
        {
            return;
        }

        if (playerLocation.distanceTo(spotLocation) > 4)
        {
            walkTutorialLocal(spotLocation, 4);
            return;
        }

        Rs2Walker.clearWalkingRoute("ksp_account_builder_tutorial_fishing");

        if (fishingSpot.click("Net"))
        {
            fishingActionDispatched = true;
        }
    }

    private void cookShrimpOnOwnFire()
    {
        Rs2TileObjectModel fire = findTutorialFire();
        if (fire != null)
        {
            cookShrimpOnFire(fire);
        }
    }

    private void cookShrimpOnFire(Rs2TileObjectModel fire)
    {
        if (fire == null || !Rs2Inventory.hasItem(ItemID.RAW_SHRIMPS_2514))
        {
            survivalCookingDispatched = false;
            return;
        }

        if (survivalCookingDispatched)
        {
            return;
        }

        if (!prepareTutorialObjectInteraction(fire, 4))
        {
            return;
        }

        if (Rs2Inventory.useItemOnObject(ItemID.RAW_SHRIMPS_2514, fire.getId()))
        {
            survivalCookingDispatched = true;
        }
    }

    private Rs2TileObjectModel findTutorialFire()
    {
        WorldPoint origin = ownFireLocation != null ? ownFireLocation : Rs2Player.getWorldLocation();
        if (origin == null)
        {
            return null;
        }

        return KspLiveScene.nearestModel(origin, 3,
                object -> object.getId() == ObjectID.FIRE_26185);
    }

    private boolean hasNearbyFire() { return tutorialObject(ObjectID.FIRE_26185) != null; }

    // -------------------------------------------------------------------------
    // Magic helper
    // -------------------------------------------------------------------------

    private boolean widgetCast()
    {
        if (Microbot.getVarbitPlayerValue(281) != 650)
        {
            windStrikeSelected = false;
            return true;
        }

        if (Rs2Player.isAnimating() || Rs2Player.isInteracting())
        {
            return true;
        }

        if (!windStrikeSelected)
        {
            Widget windStrike = Rs2Widget.findWidget("Wind Strike", null, true);

            if (windStrike == null)
            {
                windStrike = Rs2Widget.getWidget(218, 11);
            }

            if (windStrike == null)
            {
                clickTab("Magic");
                return false;
            }

            try
            {
                if (Rs2Widget.isHidden(windStrike.getId()))
                {
                    clickTab("Magic");
                    return false;
                }
            }
            catch (Exception ignored)
            {
                return false;
            }

            if (!Rs2Widget.clickWidget(windStrike))
            {
                return false;
            }

            windStrikeSelected = true;
            return true;
        }

        Rs2NpcModel chicken = tutorialNpc("chicken");

        if (chicken == null)
        {
            return false;
        }

        if (chicken.click("Cast"))
        {
            windStrikeSelected = false;
            return true;
        }

        return false;
    }

    private void clickTab(String tabName)
    {
        Widget widget = Rs2Widget.findWidget(tabName, true);

        if (widget != null)
        {
            Rs2Widget.clickWidget(widget);
        }
    }

    private void closeEquipmentStats()
    {
        if (!Rs2Widget.isWidgetVisible(84, 3))
        {
            return;
        }

        Widget widgetOptions = Rs2Widget.getWidget(84, 3);

        if (widgetOptions == null || widgetOptions.getDynamicChildren() == null)
        {
            return;
        }

        for (Widget dynamicWidgetOption : widgetOptions.getDynamicChildren())
        {
            String[] actionsText = dynamicWidgetOption.getActions();

            if (actionsText != null
                    && Arrays.stream(actionsText).anyMatch(a -> a.equalsIgnoreCase("close")))
            {
                Rs2Widget.clickWidget(dynamicWidgetOption);
                return;
            }
        }
    }

    private void handleBankSpaceAndPollBooth()
    {
        if (KspBankWidgetHelper.closeBankTutorialOverlayIfOpen())
        {
            return;
        }

        if (Rs2Widget.isWidgetVisible(928, 4))
        {
            Rs2Widget.clickWidget(928, 4);
            return;
        }

        if (Rs2Widget.isWidgetVisible(289, 5))
        {
            Widget widgetOptions = Rs2Widget.getWidget(289, 4);

            if (widgetOptions != null && widgetOptions.getDynamicChildren() != null)
            {
                for (Widget dynamicWidgetOption : widgetOptions.getDynamicChildren())
                {
                    String widgetText = dynamicWidgetOption.getText();

                    if (widgetText != null && widgetText.equalsIgnoreCase("Want more bank space?"))
                    {
                        Rs2Widget.clickWidget(289, 7);
                        return;
                    }
                }
            }
        }

        if (Rs2Bank.isOpen())
        {
            Rs2Bank.closeBank();
            return;
        }

        if (!Rs2Player.isMoving() && !Rs2Player.isInteracting())
        {
            Rs2TileObjectModel object = tutorialObject(26815);
            if (object != null) object.click();
        }
    }

    private boolean closePollOrOptionsWidget()
    {
        if (Rs2Widget.isWidgetVisible(928, 4))
        {
            Rs2Widget.clickWidget(928, 4);
            return true;
        }

        if (!Rs2Widget.isWidgetVisible(310, 2))
        {
            return false;
        }

        Widget widgetOptions = Rs2Widget.getWidget(310, 2);

        if (widgetOptions == null || widgetOptions.getDynamicChildren() == null)
        {
            return false;
        }

        for (Widget dynamicWidgetOption : widgetOptions.getDynamicChildren())
        {
            String[] actionsText = dynamicWidgetOption.getActions();

            if (actionsText != null
                    && Arrays.stream(actionsText).anyMatch(a -> a.equalsIgnoreCase("close")))
            {
                Rs2Widget.clickWidget(dynamicWidgetOption);
                return true;
            }
        }

        return false;
    }

    private boolean isInArea(WorldArea area)
    {
        WorldPoint location = Rs2Player.getWorldLocation();
        return location != null && area.contains(location);
    }

    private boolean walkToQuestGuideDoorTile()
    {
        WorldPoint location = Rs2Player.getWorldLocation();

        if (location != null && location.distanceTo(QUEST_GUIDE_WALK_TILE) <= 1)
        {
            Rs2Walker.clearWalkingRoute("ksp_account_builder_tutorial_quest_door");
            return true;
        }

        walkTutorialLocal(QUEST_GUIDE_WALK_TILE, 1);

        location = Rs2Player.getWorldLocation();
        if (location != null && location.distanceTo(QUEST_GUIDE_WALK_TILE) <= 1)
        {
            Rs2Walker.clearWalkingRoute("ksp_account_builder_tutorial_quest_door");
            return true;
        }

        return false;
    }

    private boolean walkToArea(WorldArea area) { return walkToArea(area, randomPoint(area)); }

    private boolean walkToArea(WorldArea area, WorldPoint target)
    {
        WorldPoint location = Rs2Player.getWorldLocation();

        if (location != null && area.contains(location))
        {
            Rs2Walker.clearWalkingRoute("ksp_account_builder_tutorial_reached_area");
            return true;
        }

        walkTutorialLocal(target, 3);
        return false;
    }

    private void walkTutorialLocal(WorldPoint target, int reach)
    {
        if (target == null)
        {
            return;
        }

        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null)
        {
            return;
        }

        if (playerLocation.distanceTo(target) <= reach)
        {
            Rs2Walker.clearWalkingRoute("ksp_account_builder_tutorial_local_target_reached");
            return;
        }

        if (Rs2Player.isMoving())
        {
            return;
        }

        // Delegate navigation to Microbot WebWalker. Stage-specific
        // interactions remain responsible for instructional doors and gates;
        // the walker owns routing and obstacle traversal.
        if (TutAreas.contains(playerLocation) && !TutAreas.contains(target))
        {
            debug("Tutorial Island refused mainland destination | player={} target={}",
                    playerLocation, target);
            return;
        }
        Rs2Walker.walkTo(target, reach);
    }

    private boolean openTutorialPassage(int objectId, BooleanSupplier completed) { return openTutorialPassageAndWalk(objectId, null, 0, completed); }

    private boolean readyForAction()
    {
        if (Rs2Player.isMoving() || Rs2Player.isAnimating() || Rs2Player.isInteracting())
        {
            return false;
        }
        return !isTutorialInteractionPending();
    }

    private boolean isTutorialInteractionPending()
    {
        if (pendingInteractionAtMs == 0L) return false;
        if (System.currentTimeMillis() - pendingInteractionAtMs < INTERACTION_DISPATCH_TIMEOUT_MS) return true;
        pendingInteractionAtMs = 0L;
        pendingInteractionKey = null;
        return false;
    }

    private boolean isTutorialInteractionPending(String key)
    {
        return pendingInteractionKey != null
                && pendingInteractionKey.equals(key)
                && isTutorialInteractionPending();
    }

    private void markTutorialInteraction(String key)
    {
        pendingInteractionKey = key;
        pendingInteractionAtMs = System.currentTimeMillis();
        Microbot.log("[TutIsland] ACTION_DISPATCH | state=" + status
                + " progress=" + Microbot.getVarbitPlayerValue(281)
                + " action=" + key + " attempt=" + currentStateRetries);
    }

    /**
     * Use an inventory item on the exact object obtained from the live
     * Tutorial Island scene. Microbot's Rs2Inventory.useItemOnObject(...)
     * goes through the tile-object CACHE after selecting the item: that
     * cache can miss an otherwise visible tutorial furnace/range/anvil.
     * The model click instead binds to the actual TileObject/WorldView.
     * The selection is async: if it has not appeared yet, retry next tick.
     */
    private boolean useTutorialItemOnLiveObject(int itemId,
            Rs2TileObjectModel object, String interactionKey)
    {
        if (object == null || !Rs2Inventory.hasItem(itemId)
                || isTutorialInteractionPending(interactionKey))
            return false;

        if (!Rs2Inventory.isItemSelected())
        {
            if (!Rs2Inventory.use(itemId)) return false;
            if (!Rs2Inventory.isItemSelected()) return false;
        }

        if (!object.click("")) return false;
        markTutorialInteraction(interactionKey);
        Microbot.log("[TutIsland] Live item-on-object dispatched | itemId="
                + itemId + " objectId=" + object.getId()
                + " tile=" + object.getWorldLocation()
                + " progress=" + Microbot.getVarbitPlayerValue(281));
        return true;
    }

    private void logTutorialInteractionPending(String purpose,
            Rs2TileObjectModel object, int itemId)
    {
        long now = System.currentTimeMillis();
        if (now - lastTutorialTransitionDiagnosticAtMs < 5_000L) return;
        lastTutorialTransitionDiagnosticAtMs = now;
        Microbot.log("[TutIsland] Waiting for live item-on-object action"
                + " | purpose=" + purpose + " itemId=" + itemId
                + " objectId=" + (object == null ? -1 : object.getId())
                + " player=" + Rs2Player.getWorldLocation()
                + " itemSelected=" + Rs2Inventory.isItemSelected()
                + " progress=" + Microbot.getVarbitPlayerValue(281));
    }

    private void logMissingTutorialObject(String name, int expectedId)
    {
        long now = System.currentTimeMillis();
        if (now - lastTutorialTransitionDiagnosticAtMs < 5_000L) return;
        lastTutorialTransitionDiagnosticAtMs = now;
        Microbot.log("[TutIsland] Live object missing | name=" + name
                + " id=" + expectedId + " player=" + Rs2Player.getWorldLocation()
                + " progress=" + Microbot.getVarbitPlayerValue(281));
    }

    private Rs2TileObjectModel tutorialObject(int id)
    {
        return KspLiveScene.nearestModel(Rs2Player.getWorldLocation(), 45,
                object -> object.getId() == id);
    }

    private Rs2TileObjectModel tutorialObject(String name)
    {
        return KspLiveScene.nearestModel(Rs2Player.getWorldLocation(), 45,
                object -> name.equalsIgnoreCase(object.getName()));
    }

    private Rs2NpcModel tutorialNpc(int id)
    {
        return KspLiveScene.nearestNpcModel(Rs2Player.getWorldLocation(), 45,
                npc -> npc.getId() == id);
    }

    private Rs2NpcModel tutorialNpc(String name)
    {
        return KspLiveScene.nearestNpcModel(Rs2Player.getWorldLocation(), 45,
                npc -> name.equalsIgnoreCase(npc.getName()));
    }

    private boolean clickTutorialObject(int id, String action, int reach)
    {
        Rs2TileObjectModel object = tutorialObject(id);
        if (!prepareTutorialObjectInteraction(object, reach) || !readyForAction()) return false;
        if (!object.click(action)) return false;
        markTutorialInteraction("object:" + id + ":" + action);
        return true;
    }

    private boolean clickTutorialObject(String name, String action, int reach)
    {
        Rs2TileObjectModel object = tutorialObject(name);
        if (!prepareTutorialObjectInteraction(object, reach) || !readyForAction()) return false;
        if (!object.click(action)) return false;
        markTutorialInteraction("object:" + name + ":" + action);
        return true;
    }

    private boolean prepareTutorialObjectInteraction(Rs2TileObjectModel object, int reach)
    {
        if (object == null || object.getWorldLocation() == null)
        {
            return false;
        }

        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null)
        {
            return false;
        }

        if (playerLocation.distanceTo(object.getWorldLocation()) > reach)
        {
            walkTutorialLocal(object.getWorldLocation(), reach);
            return false;
        }

        Rs2Walker.clearWalkingRoute("ksp_account_builder_tutorial_object_interaction");
        return true;
    }

    private boolean clickNearestTutorialObject(int objectId, String action)
    {
        Rs2TileObjectModel object = tutorialObject(objectId);

        if (isTutorialInteractionPending("object:" + objectId + ":" + action)) return false;

        if (object != null && object.click(action))
        {
            markTutorialInteraction("object:" + objectId + ":" + action);
            return true;
        }

        boolean interacted = false;
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player != null) interacted = KspLiveScene.interact(objectId, player, 45, action);
        if (interacted) markTutorialInteraction("object:" + objectId + ":" + action);
        return interacted;
    }

    private boolean openTutorialPassageAndWalk(int objectId, WorldPoint target, int reach, BooleanSupplier completed)
    {
        if (completed.getAsBoolean())
        {
            return true;
        }

        if (Rs2Player.isMoving() || Rs2Player.isInteracting()
                || isTutorialInteractionPending("open:" + objectId))
        {
            return false;
        }

        WorldPoint player = Rs2Player.getWorldLocation();
        if (player != null && KspLiveScene.interact(objectId, player, 45, "Open"))
        {
            markTutorialInteraction("open:" + objectId);
            return false;
        }

        if (target != null)
        {
            walkTutorialLocal(target, reach);
        }

        return completed.getAsBoolean();
    }

    private boolean isNpcReachable(Rs2NpcModel npc, int reach)
    {
        WorldPoint location = Rs2Player.getWorldLocation();
        return npc != null
                && npc.getWorldLocation() != null
                && location != null
                && npc.getWorldLocation().distanceTo(location) <= reach;
    }

    private WorldPoint randomPoint(WorldArea area)
    {
        int x = ThreadLocalRandom.current().nextInt(area.getX(), area.getX() + area.getWidth());
        int y = ThreadLocalRandom.current().nextInt(area.getY(), area.getY() + area.getHeight());
        return new WorldPoint(x, y, area.getPlane());
    }

    // -------------------------------------------------------------------------
    // Misc helpers
    // -------------------------------------------------------------------------

    private String generateDisplayName()
    {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String name = NAME_PREFIXES[random.nextInt(NAME_PREFIXES.length)]
                + NAME_SUFFIXES[random.nextInt(NAME_SUFFIXES.length)];

        if (name.length() <= 12)
        {
            return name;
        }

        return name.substring(0, 12);
    }

    private int randomDelay(int min, int max) { return ThreadLocalRandom.current().nextInt(min, max + 1); }

    private void debug(String message, Object... args)
    {
        if (!debugEnabled)
        {
            return;
        }

        try
        {
            Microbot.log("[TutIsland] " + String.format(message, args));
        }
        catch (Exception ignored)
        {
        }
    }

    @Override
    public void shutdown()
    {
        debugEnabled = false;
        pendingInteractionAtMs = 0L;
        pendingInteractionKey = null;
        Rs2Walker.clearWalkingRoute("Tutorial Island:local-walk");
        Rs2Walker.clearWalkingRoute("ksp_tutorial_shutdown");
        super.shutdown();
    }

    // -------------------------------------------------------------------------
    // Public accessors (used by overlay)
    // -------------------------------------------------------------------------

    public String getLastGeneratedName()     { return lastGeneratedName; }
    public boolean isPlayerInStartArea()     { return isInStartArea(); }
    public boolean isNameCreationOpen()      { return isDisplayNameWidgetOpen(); }
    public boolean isCharacterCreationOpen() { return isCharacterCreationWidgetOpen(); }
    public String getLastCharacterAction()   { return lastCharacterAction; }
    public boolean isExperiencePromptVisible() { return isExperiencePromptOpen(); }
    public String getLastExperienceSelection() { return lastExperienceSelection; }
    public String getQueuedAccountName()     { return queuedAccountName; }

    public String getStatus()
    {
        if (completionLogoutRequested)
        {
            return completionState;
        }

        return status == null ? "Unknown" : status.name();
    }

    public int getRemainingQueuedAccounts() { return Math.max(0, parseAccountQueue().size() - accountQueueIndex); }

    // -------------------------------------------------------------------------
    // Inner types
    // -------------------------------------------------------------------------

    private static final class AccountQueueEntry
    {
        private final String username;
        private final String password;
        private final int world;

        private AccountQueueEntry(String username, String password, int world)
        {
            this.username = username;
            this.password = password;
            this.world = world;
        }
    }

}
