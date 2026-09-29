package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.tutorialisland;

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
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspWalkerGuard;
import net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.KspBankWidgetHelper;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.tutorialisland.areas.TutAreas;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
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
public class TutorialIslandScript extends Script
{
    private static final int LOOP_DELAY_MS = 250;
    private static final int DEFAULT_CAMERA_ZOOM = 377;
    private static final int RAT_PEN_GATE_ID = 9719;
    private static final int RAT_PEN_INNER_BOUNDARY_X = 3110;
    private static final int ACCOUNT_GUIDE_DOOR_ID = 9721;
    private static final int ACCOUNT_GUIDE_ROOM_MIN_X = 3125;
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
    private static final WorldArea QUEST_GUIDE_AREA      = TutAreas.QUEST_TUT_AREA;
    private static final WorldPoint COOKING_AREA_WALK_TILE = new WorldPoint(3074, 3087, 0);
    private static final WorldPoint QUEST_GUIDE_WALK_TILE = new WorldPoint(3085, 3121, 0);
    private static final WorldArea MINING_SMITHING_AREA  = TutAreas.MINING_SMITHING_AREA;
    private static final WorldArea COMBAT_INSTRUCTOR_AREA= TutAreas.COMBAT_INSTRUCTOR_AREA;
    private static final WorldArea RAT_PIT_AREA          = TutAreas.RAT_PIT_AREA;
    private static final WorldArea TUT_ISLAND_BANK_AREA  = TutAreas.TUT_ISLAND_BANK_AREA;
    private static final WorldArea CHURCH_AREA           = TutAreas.CHURCH_AREA;
    private static final WorldArea TUTORIAL_END_AREA     = TutAreas.TUTORIAL_END_AREA;

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
    private boolean windStrikeSelected;

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
            if (!super.run()) return;
            if (!Microbot.isLoggedIn())
            {
                handleQueuedLogin();
                return;
            }

            completeQueuedLoginIfNeeded();
            calculateStatus();

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

            return Microbot.getVarbitPlayerValue(281) < 1000;
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
        try
        {
            Client client = Microbot.getClient();
            if (client == null || client.getLocalPlayer() == null)
            {
                return null;
            }
            return client.getLocalPlayer().getWorldLocation();
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

        if (isDisplayNameWidgetOpen()) status = TutState.NAME;
        else if (isCharacterCreationWidgetOpen()) status = TutState.CHARACTER;
        else status = stageFor(progress);
    }

    private TutState stageFor(int progress)
    {
        if (progress < 10) return TutState.GETTING_STARTED;
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
        Rs2NpcModel npc = Microbot.getRs2NpcCache().query().fromWorldView().withId(NpcID.GIELINOR_GUIDE).nearest();
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
            if (Rs2Player.isAnimating() || Rs2Player.isInteracting()) return;
            survivalCookingDispatched = false;
        }

        if (waitingForSurvivalFire)
        {
            if (fire != null)
            {
                waitingForSurvivalFire = false;
            }
            else if (Rs2Inventory.hasItem("Logs") && readyForAction() && !Rs2Player.isMoving())
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
        {
            clickNearestTutorialObject(ObjectID.GATE_9470, "Open");
        }

        if (!isInArea(COOKING_AREA))
        {
            walkTutorialLocal(COOKING_AREA_WALK_TILE, 3);
        }

        return false;
    }

    private boolean walkToAccountGuideRoomAndTalk(Rs2NpcModel npc)
    {
        if (npc == null || npc.getWorldLocation() == null)
        {
            return false;
        }

        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null)
        {
            return false;
        }

        if (playerLocation.getX() < ACCOUNT_GUIDE_ROOM_MIN_X)
        {
            Microbot.getRs2TileObjectCache().query().fromWorldView()
                    .withId(ACCOUNT_GUIDE_DOOR_ID)
                    .interact("Open");
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

        Rs2NpcModel npc = Microbot.getRs2NpcCache().query().fromWorldView().withId(NpcID.SURVIVAL_EXPERT).nearest();

        if (npc == null)
        {
            npc = Microbot.getRs2NpcCache().query().fromWorldView().withName("Survival Expert").nearest();
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

        KspWalkerGuard.clearActiveWalker("ksp_account_builder_survival_expert");
        if (!npc.click("Talk-to"))
        {
            return false;
        }

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
            if (breadCookingDispatched && (Rs2Player.isAnimating() || Rs2Player.isInteracting())) return;
            breadCookingDispatched = false;

            Rs2TileObjectModel range = tutorialObject(9736);
            if (prepareTutorialObjectInteraction(range, 4)
                    && Rs2Inventory.useItemOnObject(ItemID.BREAD_DOUGH, range.getId()))
            {
                breadCookingDispatched = true;
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
            walkAndTalk(Microbot.getRs2NpcCache().query().fromWorldView().withId(NpcID.MASTER_CHEF).nearest());
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
        Rs2NpcModel npc = Microbot.getRs2NpcCache().query().fromWorldView().withId(NpcID.QUEST_GUIDE).nearest();
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

        Rs2TileObjectModel ladder = Microbot.getRs2TileObjectCache()
                .query()
                .fromWorldView()
                .withId(9726)
                .nearestOnClientThread();

        if (prepareTutorialObjectInteraction(ladder, 4))
        {
            ladder.click("Climb-down");
        }
    }

    private void miningGuide()
    {
        int progress = Microbot.getVarbitPlayerValue(281);
        if (progress == 260 || progress == 330)
        {
            walkAndTalk(Microbot.getRs2NpcCache().query().fromWorldView().withId(NpcID.MINING_INSTRUCTOR).nearest());
            return;
        }

        switch (progress)
        {
            case 300:
                mineTutorialRock(ObjectID.TIN_ROCKS, "Tin ore");
                return;
            case 310:
                mineTutorialRock(ObjectID.COPPER_ROCKS, "Copper ore");
                return;
            case 320:
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

    private void mineTutorialRock(int objectId, String ore)
    {
        if (!Rs2Inventory.contains(ore)) clickTutorialObject(objectId, "Mine", 4);
    }

    private void smeltTutorialBronze()
    {
        if (Rs2Inventory.contains("Bronze bar") || !readyForAction()) return;
        Rs2TileObjectModel furnace = tutorialObject(ObjectID.FURNACE_10082);
        if (!prepareTutorialObjectInteraction(furnace, 4)) return;
        int ore = Rs2Inventory.hasItem(ItemID.TIN_ORE) ? ItemID.TIN_ORE : ItemID.COPPER_ORE;
        Rs2Inventory.useItemOnObject(ore, furnace.getId());
    }

    private void openTutorialAnvil()
    {
        if (Rs2Widget.isSmithingWidgetOpen() || !readyForAction()) return;
        Rs2TileObjectModel anvil = tutorialObject("Anvil");
        if (!prepareTutorialObjectInteraction(anvil, 4)) return;
        if (Rs2Inventory.hasItem(ItemID.BRONZE_BAR)) Rs2Inventory.useItemOnObject(ItemID.BRONZE_BAR, anvil.getId());
        else anvil.click("Smith");
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
        Rs2NpcModel instructor = Microbot.getRs2NpcCache().query().fromWorldView().withId(NpcID.COMBAT_INSTRUCTOR).nearest();

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
                equipTutorialRangeGear();
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
            Microbot.getRs2TileObjectCache().query().fromWorldView().withId(RAT_PEN_GATE_ID).interact("Open");
        }
        else walkAndTalk(instructor);
    }

    private void equipTutorialRangeGear()
    {
        equipTutorialItems("Shortbow", "Bronze arrow");
        if (!Rs2Inventory.hasItem("Shortbow") && !Rs2Inventory.hasItem("Bronze arrow"))
        {
            selectLongrangeCombatStyle();
        }
    }

    private void attackRatWithRange()
    {
        Actor target = Rs2Player.getInteracting();
        if (target != null && "giant rat".equalsIgnoreCase(target.getName())) return;

        if (!Rs2Equipment.isWearing("Shortbow") && Rs2Inventory.hasItem("Shortbow"))
        {
            Rs2Inventory.wield("Shortbow");
            return;
        }
        if (Rs2Inventory.hasItem("Bronze arrow"))
        {
            Rs2Inventory.wield("Bronze arrow");
            return;
        }

        selectLongrangeCombatStyle();
        if (readyForAction()) attackNearestRat();
    }

    private void bankerGuide()
    {
        Rs2NpcModel npc = Microbot.getRs2NpcCache().query().fromWorldView().withId(NpcID.ACCOUNT_GUIDE).nearest();
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

            if (!Rs2Player.isMoving() && !Rs2Player.isInteracting())
            {
                Microbot.getRs2TileObjectCache().query().fromWorldView().interact(ObjectID.BANK_BOOTH_10083);
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

    private void prayerGuide()
    {
        Rs2NpcModel npc = Microbot.getRs2NpcCache().query().fromWorldView().withId(NpcID.BROTHER_BRACE).nearest();
        int progress = Microbot.getVarbitPlayerValue(281);

        if (progress == 540 || progress == 550)
        {
            walkAndTalk(npc);
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

        if (progress == 600)
        {
            walkAndTalk(npc);
        }
    }

    private void mageGuide()
    {
        Rs2NpcModel npc = Microbot.getRs2NpcCache().query().fromWorldView().withId(NpcID.MAGIC_INSTRUCTOR).nearest();
        int progress = Microbot.getVarbitPlayerValue(281);

        if (progress == 610 || progress == 620)
        {
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
            if (Rs2Dialogue.keyPressForDialogueOption("Yes, I'd like to go to the mainland")) return;
            if (Rs2Dialogue.keyPressForDialogueOption("Yes, send me to the mainland")) return;
            if (Rs2Dialogue.keyPressForDialogueOption("Yes")) return;
            Rs2Dialogue.keyPressForDialogueOption(1);
            return;
        }

        Rs2Dialogue.clickContinue();
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

        KspWalkerGuard.clearActiveWalker("ksp_account_builder_tutorial_npc_interaction");

        if (!npc.click(action))
        {
            return false;
        }

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

        Microbot.getRs2TileObjectCache().query().fromWorldView().withId(RAT_PEN_GATE_ID).interact("Open");
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
        Rs2NpcModel rat = Microbot.getRs2NpcCache().query().fromWorldView().withName("Giant rat").nearest();

        if (rat == null)
        {
            return false;
        }

        return rat.click("Attack");
    }

    private boolean selectLongrangeCombatStyle()
    {
        Widget longrange = Rs2Widget.findWidget("Longrange", true);

        if (longrange == null)
        {
            longrange = Rs2Widget.findWidget("Long range", true);
        }

        if (longrange == null)
        {
            clickTab("Combat Options");
            return false;
        }

        return Rs2Widget.clickWidget(longrange);
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
            if (Rs2Player.isAnimating() || Rs2Player.isInteracting())
            {
                return;
            }
            treeActionDispatched = false;
        }

        Rs2TileObjectModel tree = Microbot.getRs2TileObjectCache()
                .query()
                .fromWorldView()
                .withName("Tree")
                .nearestOnClientThread();

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
            if (Rs2Player.isAnimating() || Rs2Player.isInteracting())
            {
                return;
            }
            fishingActionDispatched = false;
        }

        Rs2NpcModel fishingSpot = Microbot.getRs2NpcCache()
                .query()
                .fromWorldView()
                .withId(NpcID.FISHING_SPOT_3317)
                .nearest();

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

        KspWalkerGuard.clearActiveWalker("ksp_account_builder_tutorial_fishing");

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

        return Microbot.getRs2TileObjectCache()
                .query()
                .fromWorldView()
                .withId(ObjectID.FIRE_26185)
                .within(origin, 3)
                .nearestOnClientThread();
    }

    private boolean hasNearbyFire() { return Microbot.getRs2TileObjectCache().query().fromWorldView().withId(ObjectID.FIRE_26185).nearest() != null; }

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

        Rs2NpcModel chicken = Microbot.getRs2NpcCache()
                .query()
                .fromWorldView()
                .withName("chicken")
                .nearestOnClientThread();

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
            Microbot.getRs2TileObjectCache().query().fromWorldView().interact(26815);
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
            KspWalkerGuard.clearActiveWalker("ksp_account_builder_tutorial_quest_door");
            return true;
        }

        walkTutorialLocal(QUEST_GUIDE_WALK_TILE, 1);

        location = Rs2Player.getWorldLocation();
        if (location != null && location.distanceTo(QUEST_GUIDE_WALK_TILE) <= 1)
        {
            KspWalkerGuard.clearActiveWalker("ksp_account_builder_tutorial_quest_door");
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
            KspWalkerGuard.clearActiveWalker("ksp_account_builder_tutorial_reached_area");
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
            KspWalkerGuard.clearActiveWalker("ksp_account_builder_tutorial_local_target_reached");
            return;
        }

        if (Rs2Player.isMoving())
        {
            return;
        }

        KspWalkerGuard.clear("Tutorial Island:local-walk");
        Rs2Walker.walkTo(target, reach);
    }

    private boolean openTutorialPassage(int objectId, BooleanSupplier completed) { return openTutorialPassageAndWalk(objectId, null, 0, completed); }

    private boolean readyForAction()
    {
        return !Rs2Player.isAnimating() && !Rs2Player.isInteracting();
    }

    private Rs2TileObjectModel tutorialObject(int id)
    {
        return Microbot.getRs2TileObjectCache().query().fromWorldView().withId(id).nearestOnClientThread();
    }

    private Rs2TileObjectModel tutorialObject(String name)
    {
        return Microbot.getRs2TileObjectCache().query().fromWorldView().withName(name).nearestOnClientThread();
    }

    private boolean clickTutorialObject(int id, String action, int reach)
    {
        Rs2TileObjectModel object = tutorialObject(id);
        return prepareTutorialObjectInteraction(object, reach) && readyForAction() && object.click(action);
    }

    private boolean clickTutorialObject(String name, String action, int reach)
    {
        Rs2TileObjectModel object = tutorialObject(name);
        return prepareTutorialObjectInteraction(object, reach) && readyForAction() && object.click(action);
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

        KspWalkerGuard.clearActiveWalker("ksp_account_builder_tutorial_object_interaction");
        return true;
    }

    private boolean clickNearestTutorialObject(int objectId, String action)
    {
        Rs2TileObjectModel object = Microbot.getRs2TileObjectCache()
                .query()
                .fromWorldView()
                .withId(objectId)
                .nearest();

        if (object != null && object.click(action))
        {
            return true;
        }

        return Microbot.getRs2TileObjectCache()
                .query()
                .fromWorldView()
                .interact(objectId, action);
    }

    private boolean openTutorialPassageAndWalk(int objectId, WorldPoint target, int reach, BooleanSupplier completed)
    {
        if (completed.getAsBoolean())
        {
            return true;
        }

        if (Rs2Player.isMoving() || Rs2Player.isInteracting())
        {
            return false;
        }

        if (Microbot.getRs2TileObjectCache()
                .query()
                .fromWorldView()
                .interact(objectId, "Open"))
        {
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
