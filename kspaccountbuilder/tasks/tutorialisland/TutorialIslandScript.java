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
    private static final int LOOP_DELAY_MS = 600;
    private static final int DEFAULT_CAMERA_ZOOM = 377;
    private static final int NAME_ATTEMPT_COOLDOWN_MS = 3500;
    private static final int CHARACTER_ATTEMPT_COOLDOWN_MS = 3500;
    private static final int CHARACTER_CONFIRM_RETRIES = 3;
    private static final int CHARACTER_CONFIRM_TIMEOUT_MS = 2500;
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

    private long lastNameAttemptAtMs;
    private long lastCharacterAttemptAtMs;
    private String lastGeneratedName = "None";
    private String lastCharacterAction = "Waiting";
    private String lastExperienceSelection = "None";
    private String completionState = "Active";
    private Status status = Status.NAME;
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

    public boolean run()
    {
        shutdown();
        resetAccountState();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try
            {
                if (!super.run())
                {
                    return;
                }

                if (!Microbot.isLoggedIn())
                {
                    handleQueuedLogin();
                    return;
                }

                completeQueuedLoginIfNeeded();
                calculateStatus();

                if (Rs2Widget.isWidgetVisible(929, 5))
                {
                    Rs2Widget.clickWidget(929, 5);
                    Rs2Random.waitEx(1200, 300);
                    return;
                }

                if (Rs2Widget.isWidgetVisible(310, 0))
                {
                    Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
                    Rs2Random.waitEx(1200, 300);
                    return;
                }

                if (isExperiencePromptOpen())
                {
                    selectRandomExperienceOption();
                    return;
                }

                if (isDisplayNameWidgetOpen())
                {
                    status = Status.NAME;
                    if (!isNameAttemptCoolingDown())
                    {
                        enterGeneratedName();
                        lastNameAttemptAtMs = System.currentTimeMillis();
                    }
                    return;
                }

                if (isCharacterCreationWidgetOpen())
                {
                    status = Status.CHARACTER;
                    if (!isCharacterAttemptCoolingDown())
                    {
                        lastCharacterAttemptAtMs = System.currentTimeMillis();
                        randomizeCharacter();
                    }
                    return;
                }

                if (openSettingsTabForTutorialPrompt())
                {
                    return;
                }

                if (hasContinue())
                {
                    clickContinue();
                    return;
                }

                if (Rs2Player.isMoving() || Rs2Player.isAnimating())
                {
                    return;
                }

                switch (status)
                {
                    case NAME:
                        break;
                    case CHARACTER:
                        break;
                    case GETTING_STARTED:
                        gettingStarted();
                        break;
                    case SURVIVAL_GUIDE:
                        survivalGuide();
                        break;
                    case COOKING_GUIDE:
                        cookingGuide();
                        break;
                    case QUEST_GUIDE:
                        questGuide();
                        break;
                    case MINING_GUIDE:
                        miningGuide();
                        break;
                    case COMBAT_GUIDE:
                        combatGuide();
                        break;
                    case BANKER_GUIDE:
                        bankerGuide();
                        break;
                    case PRAYER_GUIDE:
                        prayerGuide();
                        break;
                    case MAGE_GUIDE:
                        mageGuide();
                        break;
                    case FINISHED:
                        handleTutorialComplete();
                        break;
                }
            }
            catch (Exception e)
            {
                debug("Error in TutorialIslandScript: %s", e.getMessage());
            }
        }, 0, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);

        return true;
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

    private static boolean isTutorialIslandLocation(WorldPoint location)
    {
        return location != null && TutAreas.contains(location);
    }

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

    public void setDebugLogging(boolean enabled)
    {
        this.debugEnabled = enabled;
    }

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

        if (isDisplayNameWidgetOpen())
        {
            status = Status.NAME;
        }
        else if (isCharacterCreationWidgetOpen())
        {
            status = Status.CHARACTER;
        }
        else if (progress < 10)
        {
            status = Status.GETTING_STARTED;
        }
        else if (progress < 120)
        {
            status = Status.SURVIVAL_GUIDE;
        }
        else if (progress < 200)
        {
            status = Status.COOKING_GUIDE;
        }
        else if (progress <= 250)
        {
            status = Status.QUEST_GUIDE;
        }
        else if (progress <= 360)
        {
            status = Status.MINING_GUIDE;
        }
        else if (progress < 510)
        {
            status = Status.COMBAT_GUIDE;
        }
        else if (progress < 540)
        {
            status = Status.BANKER_GUIDE;
        }
        else if (progress < 610)
        {
            status = Status.PRAYER_GUIDE;
        }
        else if (progress < 1000)
        {
            status = Status.MAGE_GUIDE;
        }
        else
        {
            status = Status.FINISHED;
        }
    }

    // -------------------------------------------------------------------------
    // Widget detection helpers
    // -------------------------------------------------------------------------

    private boolean isInStartArea()
    {
        WorldPoint location = getLocalPlayerWorldLocationSafe();
        return location != null && START_AREA.contains(location);
    }

    private boolean isDisplayNameWidgetOpen()
    {
        return isDisplayNameWidgetOpenStatic();
    }

    private boolean isCharacterCreationWidgetOpen()
    {
        return isCharacterCreationWidgetOpenStatic();
    }

    private boolean isExperiencePromptOpen()
    {
        return Rs2Dialogue.isInDialogue()
                && (Rs2Widget.hasWidget(EXPERIENCE_PROMPT_TITLE)
                || Rs2Widget.hasWidget(EXPERIENCE_OPTION_TEXTS[0])
                || Rs2Widget.hasWidget(EXPERIENCE_OPTION_TEXTS[1])
                || Rs2Widget.hasWidget(EXPERIENCE_OPTION_TEXTS[2]));
    }

    private boolean isNameAttemptCoolingDown()
    {
        return System.currentTimeMillis() - lastNameAttemptAtMs < NAME_ATTEMPT_COOLDOWN_MS;
    }

    private boolean isCharacterAttemptCoolingDown()
    {
        return System.currentTimeMillis() - lastCharacterAttemptAtMs < CHARACTER_ATTEMPT_COOLDOWN_MS;
    }

    private boolean openSettingsTabForTutorialPrompt()
    {
        String dialogueText = Rs2Dialogue.getDialogueText();
        if (dialogueText == null
                || !dialogueText.toLowerCase().contains(SETTINGS_TUTORIAL_PROMPT))
        {
            return false;
        }

        if (!Rs2Widget.clickWidget(FIXED_VIEWPORT_GROUP, SETTINGS_TAB_CHILD))
        {
            debug("Failed to click the Tutorial Island Settings tab widget.");
            return true;
        }

        sleepUntil(() -> Rs2Widget.isWidgetVisible(SETTINGS_PANEL_GROUP, 0)
                || Rs2Widget.hasWidget("Controls Settings"), 3000);
        return true;
    }

    // -------------------------------------------------------------------------
    // Name creation
    // -------------------------------------------------------------------------

    private void enterGeneratedName()
    {
        String name = generateDisplayName();
        lastGeneratedName = name;

        clearDisplayNameInput();
        sleep(randomDelay(180, 420));

        Rs2Widget.clickWidget(NAME_CREATION_GROUP, NAME_INPUT_CHILD);
        sleep(randomDelay(240, 520));

        Rs2Keyboard.typeString(name);
        sleep(randomDelay(220, 520));

        Rs2Widget.clickWidget(NAME_CREATION_GROUP, LOOK_UP_NAME_BUTTON_CHILD);
        sleep(randomDelay(4200, 5600));

        if (isGeneratedNameAvailable(name))
        {
            Rs2Widget.clickWidget(NAME_CREATION_GROUP, LOOK_UP_NAME_BUTTON_CHILD);
            sleepUntil(() -> !isDisplayNameWidgetOpen(), 6000);
        }
    }

    private void clearDisplayNameInput()
    {
        String currentInput = getCurrentDisplayNameInput();

        if (currentInput.isEmpty())
        {
            return;
        }

        Rs2Widget.clickWidget(NAME_CREATION_GROUP, NAME_INPUT_CHILD);
        sleep(randomDelay(240, 520));

        for (int i = 0; i < currentInput.length(); i++)
        {
            Rs2Keyboard.keyPress(KeyEvent.VK_BACK_SPACE);
            sleep(randomDelay(18, 55));
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

    private boolean isGeneratedNameAvailable(String name)
    {
        Widget responseWidget = Rs2Widget.getWidget(NAME_CREATION_GROUP, NAME_RESPONSE_TEXT_CHILD);

        if (responseWidget == null || responseWidget.getText() == null)
        {
            return false;
        }

        String responseText = Rs2UiHelper.stripColTags(responseWidget.getText());
        return responseText.startsWith("Great! The display name " + name + " is available");
    }

    // -------------------------------------------------------------------------
    // Character creation
    // -------------------------------------------------------------------------

    private void randomizeCharacter()
    {
        lastCharacterAction = "Randomizing";

        selectRandomBodyType();
        sleep(randomDelay(450, 900));

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
            sleep(randomDelay(90, 240));
        }

        sleep(randomDelay(700, 1400));
        lastCharacterAction = "Confirming";
        if (confirmCharacterSelection())
        {
            lastCharacterAction = "Confirmed";
        }
        else
        {
            lastCharacterAction = "Confirm failed";
            debug("Character customization failed: the Confirm button did not close the creator.");
        }
    }

    private boolean confirmCharacterSelection()
    {
        for (int attempt = 0; attempt < CHARACTER_CONFIRM_RETRIES; attempt++)
        {
            boolean clicked = Rs2Widget.clickWidget(
                    CHARACTER_CREATION_GROUP,
                    CHARACTER_CONFIRM_BUTTON_CHILD);

            if (!clicked)
            {
                Widget confirmWidget = Rs2Widget.findWidget("Confirm", null, false);
                clicked = confirmWidget != null && Rs2Widget.clickWidget(confirmWidget);
            }

            if (clicked && sleepUntil(
                    () -> !isCharacterCreationWidgetOpen(),
                    CHARACTER_CONFIRM_TIMEOUT_MS))
            {
                return true;
            }

            sleep(randomDelay(300, 650));
        }

        return false;
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
            sleep(randomDelay(120, 360));
        }
    }

    private void selectRandomExperienceOption()
    {
        int optionIndex = ThreadLocalRandom.current().nextInt(EXPERIENCE_OPTION_TEXTS.length);
        String optionText = EXPERIENCE_OPTION_TEXTS[optionIndex];
        Widget optionWidget = Rs2Widget.findWidget(optionText, null, false);

        if (optionWidget == null)
        {
            return;
        }

        lastExperienceSelection = "Option " + (optionIndex + 1);
        sleep(randomDelay(450, 1100));

        Rs2Widget.clickWidget(optionWidget);
        sleep(randomDelay(600, 1400));
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

        walkAndTalk(npc);
    }

    private void survivalGuide()
    {
        int progress = Microbot.getVarbitPlayerValue(281);

        if (!toggledSettings && configureCameraAfterGielinorGuide())
        {
            return;
        }

        if (progress == 10 || progress == 20 || progress == 60)
        {
            talkToSurvivalExpert();
        }
        else if (progress < 40)
        {
            clickTab("Inventory");
        }
        else if (progress < 50)
        {
            fishShrimp();
        }
        else if (progress < 70)
        {
            clickTab("Skills");
            talkToSurvivalExpert();
        }
        else if (progress <= 90)
        {
            if (!Rs2Inventory.hasItem("Bronze Axe") || !Rs2Inventory.hasItem("Tinderbox"))
            {
                talkToSurvivalExpert();
                return;
            }
            if (!Rs2Inventory.hasItem(ItemID.RAW_SHRIMPS_2514))
            {
                ownFireLocation = null;
                fishShrimp();
                return;
            }
            boolean firePresent = ownFireLocation != null && hasNearbyFire();
            if (!firePresent && !Rs2Inventory.hasItem("Logs"))
            {
                ownFireLocation = null;
                cutTree();
                return;
            }
            if (!firePresent)
            {
                lightFire();
                return;
            }
            cookShrimpOnOwnFire();
        }
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
            Rs2Random.waitEx(1200, 300);
            return true;
        }

        Rs2Camera.setZoom(DEFAULT_CAMERA_ZOOM);
        Rs2Random.waitEx(300, 100);
        Rs2Camera.setPitch(280);
        sleepUntil(() -> Rs2Camera.getPitch() > 250);
        toggledSettings = true;
        return true;
    }

    private boolean openCookingGate()
    {
        BooleanSupplier gatePassed = () -> Microbot.getVarbitPlayerValue(281) != 120 || isInArea(COOKING_AREA);

        if (gatePassed.getAsBoolean())
        {
            return true;
        }

        if (clickNearestTutorialObject(ObjectID.GATE_9470, "Open"))
        {
            sleepUntil(gatePassed::getAsBoolean, 1_500);
        }

        if (gatePassed.getAsBoolean())
        {
            return true;
        }

        walkTutorialLocal(COOKING_AREA_WALK_TILE, 3);
        sleepUntil(gatePassed::getAsBoolean, 1_500);
        return gatePassed.getAsBoolean();
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
        if (!walkToArea(SURVIVAL_AREA))
        {
            return false;
        }

        Rs2NpcModel npc = Microbot.getRs2NpcCache().query().fromWorldView().withId(NpcID.SURVIVAL_EXPERT).nearest();

        if (npc == null)
        {
            npc = Microbot.getRs2NpcCache().query().fromWorldView().withName("Survival Expert").nearest();
        }

        return walkAndTalk(npc, 4);
    }

    private void cookingGuide()
    {
        Rs2NpcModel npc = Microbot.getRs2NpcCache().query().fromWorldView().withId(NpcID.MASTER_CHEF).nearest();
        int progress = Microbot.getVarbitPlayerValue(281);

        if (progress == 120)
        {
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
            openCookingGate();
        }
        else if (progress == 130)
        {
            if (!walkToArea(COOKING_AREA, COOKING_AREA_WALK_TILE)) return;
            openTutorialPassage(
                    ObjectID.DOOR_9709,
                    () -> Microbot.getVarbitPlayerValue(281) != 130);
        }
        else if (progress == 140)
        {
            walkAndTalk(npc);
        }
        else if (progress < 200)
        {
            if (!Rs2Inventory.contains("Bread dough") && !Rs2Inventory.contains("Bread"))
            {
                Rs2Inventory.combine("Bucket of water", "Pot of flour");
                sleepUntil(() -> Rs2Inventory.contains("Dough"), 2000);
            }
            else if (Rs2Inventory.contains("Bread dough"))
            {
                Rs2Inventory.interact("Bread dough");
                Microbot.getRs2TileObjectCache().query().fromWorldView().interact(9736, "Use");
                sleepUntil(() -> Rs2Inventory.contains("Bread"));
            }
            else if (Rs2Inventory.contains("Bread")
                    && openTutorialPassageAndWalk(
                            9710,
                            QUEST_GUIDE_WALK_TILE,
                            3,
                            () -> Microbot.getVarbitPlayerValue(281) >= 200))
            {
                Rs2Random.waitEx(2400, 100);
            }
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
            Rs2Random.waitEx(1200, 300);
        }
        else if (progress == 220 || progress == 240)
        {
            walkAndTalk(npc);
        }
        else if (progress == 230)
        {
            clickTab("Quest List");
        }
        else
        {
            Rs2Tab.switchTo(InterfaceTab.INVENTORY);
            Rs2Random.waitEx(600, 100);
            Microbot.getRs2TileObjectCache().query().fromWorldView().interact(9726, "Climb-down");
            Rs2Random.waitEx(2400, 100);
        }
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

    private void miningGuide()
    {
        Rs2NpcModel npc = Microbot.getRs2NpcCache().query().fromWorldView().withId(NpcID.MINING_INSTRUCTOR).nearest();

        if (Microbot.getVarbitPlayerValue(281) == 260)
        {
            walkTutorialLocal(new WorldPoint(3082, 9505, 0), 2);
            Rs2Player.waitForWalking();
            walkAndTalk(npc);
            return;
        }

        if (Rs2Inventory.contains("Bronze dagger"))
        {
            openTutorialPassage(
                    ObjectID.GATE_9718,
                    () -> Microbot.getVarbitPlayerValue(281) > 360 || isInArea(COMBAT_INSTRUCTOR_AREA));
            return;
        }

        if (Rs2Inventory.contains("Bronze bar") && Rs2Inventory.contains("Hammer"))
        {
            Microbot.getClientThread().invoke(() ->
                    Microbot.getRs2TileObjectCache().query().fromWorldView().withName("Anvil").interact("Smith"));
            sleepUntil(Rs2Widget::isSmithingWidgetOpen);
            Rs2Widget.clickWidget(312, 9);
            Rs2Random.waitEx(1200, 300);
            sleepUntil(() -> Rs2Inventory.contains("Bronze dagger") && !Rs2Player.isAnimating(1800));
            return;
        }

        if (Rs2Inventory.contains("Bronze bar") && !Rs2Inventory.contains("Hammer"))
        {
            walkAndTalk(npc);
            return;
        }

        if (Rs2Inventory.contains("Bronze pickaxe")
                && (!Rs2Inventory.contains("Copper ore") || !Rs2Inventory.contains("Tin ore")))
        {
            List<Integer> rockIds = new ArrayList<>();
            if (!Rs2Inventory.contains("Copper ore"))
            {
                rockIds.add(ObjectID.COPPER_ROCKS);
            }
            if (!Rs2Inventory.contains("Tin ore"))
            {
                rockIds.add(ObjectID.TIN_ROCKS);
            }

            Collections.shuffle(rockIds);
            int rockId = rockIds.get(0);

            Microbot.getRs2TileObjectCache().query().fromWorldView().interact(rockId, "Mine");
            sleepUntil(() -> rockId == ObjectID.COPPER_ROCKS
                    ? Rs2Inventory.contains("Copper ore") && !Rs2Player.isAnimating(1800)
                    : Rs2Inventory.contains("Tin ore") && !Rs2Player.isAnimating(1800));
        }
        else if (Rs2Inventory.contains("Copper ore") && Rs2Inventory.contains("Tin ore"))
        {
            List<Integer> ores = Arrays.asList(ItemID.TIN_ORE, ItemID.COPPER_ORE);
            Collections.shuffle(ores);
            Rs2Inventory.useItemOnObject(ores.get(0), ObjectID.FURNACE_10082);
            sleepUntil(() -> Rs2Inventory.contains("Bronze bar") && !Rs2Player.isAnimating(1800));
        }
    }

    private void combatGuide()
    {
        Rs2NpcModel npc = Microbot.getRs2NpcCache().query().fromWorldView().withId(NpcID.COMBAT_INSTRUCTOR).nearest();
        int progress = Microbot.getVarbitPlayerValue(281);

        if (progress <= 370)
        {
            if (!walkToArea(COMBAT_INSTRUCTOR_AREA)) return;
            walkAndTalk(npc);
        }
        else if (progress <= 410)
        {
            if (isInDialogue())
            {
                clickContinue();
                return;
            }
            clickTab("Worn Equipment");
            Rs2Widget.clickWidget(387, 1);
            sleepUntil(() -> Rs2Widget.getWidget(84, 1) != null);
            Rs2Random.waitEx(1200, 300);
            Rs2Widget.clickWidget("Bronze dagger");
            Rs2Random.waitEx(2400, 300);
            closeEquipmentStats();
            walkAndTalk(npc);
        }
        else if (progress == 500)
        {
            walkTutorialLocal(new WorldPoint(3111, 9526, Rs2Player.getWorldLocation().getPlane()), 3);
            Rs2Player.waitForWalking();
            Microbot.getClientThread().invoke(() ->
                    Microbot.getRs2TileObjectCache().query().fromWorldView().withName("Ladder").interact("Climb-up"));
            sleepUntil(() -> Microbot.getVarbitPlayerValue(281) != 500);
        }
        else if (progress == 480 || progress == 490)
        {
            Actor rat = Rs2Player.getInteracting();
            if (rat != null && rat.getName().equalsIgnoreCase("giant rat"))
            {
                return;
            }
            if (Rs2Inventory.hasItem("Shortbow"))
            {
                Rs2Inventory.wield("Shortbow");
                Rs2Random.waitEx(600, 100);
            }
            if (Rs2Inventory.hasItem("Bronze arrow"))
            {
                Rs2Inventory.wield("Bronze arrow");
                Rs2Random.waitEx(600, 100);
            }
            selectLongrangeCombatStyle();
            attackNearestRat();
        }
        else if (progress == 470)
        {
            walkTutorialLocal(new WorldPoint(3108, 9508, 0), 2);
            Rs2Player.waitForWalking();
            walkAndTalk(npc);
        }
        else if (progress == 430)
        {
            clickTab("Combat Options");
        }
        else if (progress >= 420)
        {
            if (isInDialogue())
            {
                clickContinue();
                return;
            }
            if (Microbot.getClient().getLocalPlayer().isInteracting() || Rs2Player.isAnimating())
            {
                return;
            }
            if (Rs2Equipment.isWearing("Bronze sword"))
            {
                if (!ensureInsideRatPen())
                {
                    return;
                }

                walkAndAttackRat();
            }
            else if (Rs2Inventory.hasItem("Bronze sword"))
            {
                Rs2Tab.switchTo(InterfaceTab.INVENTORY);
                Rs2Random.waitEx(600, 100);
                Rs2Inventory.wield("Bronze sword");
                Rs2Random.waitEx(600, 100);
                Rs2Inventory.wield("Wooden shield");
            }
            else
            {
                walkAndTalk(npc);
            }
        }
    }

    private void bankerGuide()
    {
        Rs2NpcModel npc = Microbot.getRs2NpcCache().query().fromWorldView().withId(NpcID.ACCOUNT_GUIDE).nearest();
        int progress = Microbot.getVarbitPlayerValue(281);

        if (progress == 510)
        {
            // Use a specific walkable tile near the bank entrance — randomPoint() can land on
            // unreachable counter/wall tiles inside the bank building (seen at 3128,3118).
            WorldPoint bankEntrance = new WorldPoint(3120, 3124, 0);
            walkTutorialLocal(bankEntrance, 5);
            Rs2Player.waitForWalking();
            Microbot.getRs2TileObjectCache().query().fromWorldView().interact(ObjectID.BANK_BOOTH_10083);
            sleepUntil(() -> Microbot.getVarbitPlayerValue(281) != 510);
        }
        else if (progress == 520)
        {
            handleBankSpaceAndPollBooth();
        }
        else if (progress == 525 || progress == 530)
        {
            closePollOrOptionsWidget();
            if (npc != null)
            {
                walkToAccountGuideRoomAndTalk(npc);
            }
        }
        else if (progress == 531)
        {
            clickTab("Account Management");
        }
        else if (progress == 532)
        {
            if (Rs2Dialogue.isInDialogue())
            {
                clickContinue();
                return;
            }
            walkToAccountGuideRoomAndTalk(npc);
        }
    }

    private void prayerGuide()
    {
        Rs2NpcModel npc = Microbot.getRs2NpcCache().query().fromWorldView().withId(NpcID.BROTHER_BRACE).nearest();
        int progress = Microbot.getVarbitPlayerValue(281);

        if (progress == 640 || progress == 550 || progress == 540)
        {
            walkTutorialLocal(new WorldPoint(3125, 3106, 0), 3);
            Rs2Player.waitForWalking();
            walkAndTalk(npc);
        }
        else if (progress == 560)
        {
            clickTab("Prayer");
        }
        else if (progress == 570)
        {
            walkAndTalk(npc);
        }
        else if (progress == 580)
        {
            clickTab("Friends list");
        }
        else if (progress == 600)
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
            walkTutorialLocal(new WorldPoint(3142, 3089, 0), 3);
            Rs2Player.waitForWalking();
            walkAndTalk(npc);
        }
        else if (progress == 630)
        {
            clickTab("Magic");
        }
        else if (progress == 640)
        {
            walkAndTalk(npc);
        }
        else if (progress == 650)
        {
            widgetCast();
        }
        else if (progress == 680)
        {
            if (isInDialogue())
            {
                handleFinalMageDialogue();
                return;
            }

            castLumbridgeHomeTeleport();
        }
        else if (progress > 680)
        {
            if (isInDialogue())
            {
                handleFinalMageDialogue();
                return;
            }

            walkAndTalk(npc);
        }
        else if (progress >= 660)
        {
            if (isInDialogue())
            {
                handleFinalMageDialogue();
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
        if (Rs2Tab.getCurrentTab() != InterfaceTab.MAGIC)
        {
            Rs2Tab.switchTo(InterfaceTab.MAGIC);
            Rs2Random.waitEx(600, 100);
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

        Rs2Widget.clickWidget(homeTeleport);
        return sleepUntil(() -> {
            WorldPoint location = Rs2Player.getWorldLocation();
            return Microbot.getVarbitPlayerValue(281) >= 1000
                    || (location != null && !TutAreas.contains(location));
        }, 15_000);
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

    private List<AccountQueueEntry> parseAccountQueue()
    {
        return new ArrayList<>();
    }

    private boolean shouldRunMultipleAccounts()
    {
        return false;
    }

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
        lastNameAttemptAtMs = 0L;
        lastGeneratedName = "None";
        lastCharacterAction = "Waiting";
        lastExperienceSelection = "None";
    }

    // -------------------------------------------------------------------------
    // NPC walk/talk helpers
    // -------------------------------------------------------------------------

    private boolean walkAndTalk(Rs2NpcModel npc)
    {
        return walkAndTalk(npc, 2);
    }

    private boolean walkAndTalk(Rs2NpcModel npc, int reach)
    {
        return walkAndAct(npc, reach, "Talk-to", () -> sleepUntil(Rs2Dialogue::isInDialogue, 5000));
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

        if (npc.click(action))
        {
            if (afterClick != null)
            {
                afterClick.run();
            }
            return true;
        }

        if (playerLocation.distanceTo(npcLocation) <= reach)
        {
            return false;
        }

        walkTutorialLocal(npcLocation, reach);
        Rs2Player.waitForWalking();
        playerLocation = Rs2Player.getWorldLocation();

        if (playerLocation == null || playerLocation.distanceTo(npcLocation) > reach)
        {
            return false;
        }

        if (npc.click(action))
        {
            if (afterClick != null)
            {
                afterClick.run();
            }
            return true;
        }

        return false;
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
        clickTab("Combat Options");

        Widget longrange = Rs2Widget.findWidget("Longrange", true);

        if (longrange == null)
        {
            longrange = Rs2Widget.findWidget("Long range", true);
        }

        if (longrange == null)
        {
            return false;
        }

        Rs2Widget.clickWidget(longrange);
        Rs2Random.waitEx(600, 100);
        return true;
    }

    // -------------------------------------------------------------------------
    // Skilling helpers
    // -------------------------------------------------------------------------

    private void lightFire()
    {
        WorldPoint fireLocation = Rs2Player.getWorldLocation();

        if (Rs2Player.isStandingOnGameObject())
        {
            WorldPoint nearestWalkable = Rs2Tile.getNearestWalkableTileWithLineOfSight(Rs2Player.getWorldLocation());
            Rs2Walker.walkFastCanvas(nearestWalkable);
            Rs2Player.waitForWalking();
            fireLocation = Rs2Player.getWorldLocation();
        }

        Rs2Inventory.combine("Logs", "Tinderbox");

        WorldPoint confirmedLocation = fireLocation;
        boolean fireConfirmed = sleepUntil(() -> !Rs2Inventory.hasItem("Logs"), 5000);

        if (fireConfirmed)
        {
            ownFireLocation = confirmedLocation;
        }
    }

    private void cutTree()
    {
        Microbot.getClientThread().invoke(() ->
                Microbot.getRs2TileObjectCache().query().fromWorldView().withName("Tree").interact("Chop down"));
        sleepUntil(() -> Rs2Inventory.hasItem("Logs") && !Rs2Player.isAnimating(2400));
    }

    private void fishShrimp()
    {
        Microbot.getRs2NpcCache().query().fromWorldView().withId(NpcID.FISHING_SPOT_3317).interact("Net");
        sleepUntil(() -> Rs2Inventory.contains(false, "shrimps"));
    }

    private void cookShrimpOnOwnFire()
    {
        if (!Rs2Inventory.interact(ItemID.RAW_SHRIMPS_2514, "Use"))
        {
            return;
        }

        Rs2Random.waitEx(240, 80);
        Microbot.getRs2TileObjectCache().query().fromWorldView().withId(ObjectID.FIRE_26185).interact("Use");
        sleepUntil(() -> !Rs2Inventory.hasItem(ItemID.RAW_SHRIMPS_2514)
                || Microbot.getVarbitPlayerValue(281) > 90, 5000);
    }

    private boolean hasNearbyFire()
    {
        return Microbot.getRs2TileObjectCache().query().fromWorldView().withId(ObjectID.FIRE_26185).nearest() != null;
    }

    // -------------------------------------------------------------------------
    // Magic helper
    // -------------------------------------------------------------------------

    private boolean widgetCast()
    {
        if (Rs2Player.isAnimating() || Rs2Player.getInteracting() != null)
        {
            return true;
        }

        Widget windStrike = Rs2Widget.findWidget("Wind Strike", null, true);

        if (windStrike == null)
        {
            windStrike = Rs2Widget.getWidget(218, 11);
        }
        if (windStrike == null)
        {
            return false;
        }

        boolean hidden;
        try
        {
            hidden = Rs2Widget.isHidden(windStrike.getId());
        }
        catch (Exception ignored)
        {
            hidden = true;
        }

        if (hidden)
        {
            clickTab("Magic");
            windStrike = Rs2Widget.getWidget(218, 8);

            if (windStrike == null)
            {
                windStrike = Rs2Widget.findWidget("Wind Strike", null, true);
            }
            if (windStrike == null)
            {
                return false;
            }

            try
            {
                if (Rs2Widget.isHidden(windStrike.getId()))
                {
                    return false;
                }
            }
            catch (Exception ignored)
            {
                return false;
            }
        }

        Rs2Widget.clickWidget(windStrike);
        Rs2Random.waitEx(150, 50);

        Rs2NpcModel chicken = Microbot.getRs2NpcCache().query().fromWorldView().withName("chicken").nearestOnClientThread();

        if (chicken == null)
        {
            return false;
        }

        if (!chicken.click("Cast"))
        {
            chicken.click("Cast");
        }

        sleepUntil(() -> Rs2Player.isAnimating() || Microbot.getVarbitPlayerValue(281) != 650, 2000);
        return true;
    }

    // -------------------------------------------------------------------------
    // UI helpers
    // -------------------------------------------------------------------------

    private void clickTab(String tabName)
    {
        Widget widget = Rs2Widget.findWidget(tabName, true);

        if (widget != null)
        {
            Rs2Widget.clickWidget(widget);
            Rs2Random.waitEx(1200, 300);
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
                Rs2Random.waitEx(1200, 300);
                return;
            }
        }
    }

    private void handleBankSpaceAndPollBooth()
    {
        if (KspBankWidgetHelper.closeBankTutorialOverlayIfOpenAndWait())
        {
            return;
        }

        if (Rs2Widget.isWidgetVisible(928, 4))
        {
            Rs2Widget.clickWidget(928, 4);
            Rs2Random.waitEx(1200, 300);
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
                        Rs2Random.waitEx(1200, 300);
                        break;
                    }
                }
            }
        }

        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen());
        Microbot.getRs2TileObjectCache().query().fromWorldView().interact(26815);
        sleepUntil(() -> Microbot.getVarbitPlayerValue(281) != 520 || Rs2Widget.isWidgetVisible(928, 4));
    }

    private void closePollOrOptionsWidget()
    {
        if (Rs2Widget.isWidgetVisible(928, 4))
        {
            Rs2Widget.clickWidget(928, 4);
            Rs2Random.waitEx(1200, 300);
            return;
        }

        if (!Rs2Widget.isWidgetVisible(310, 2))
        {
            return;
        }

        Widget widgetOptions = Rs2Widget.getWidget(310, 2);

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
                Rs2Random.waitEx(1200, 300);
                return;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Area / world helpers
    // -------------------------------------------------------------------------

    private boolean walkToArea(WorldArea area)
    {
        return walkToArea(area, randomPoint(area));
    }

    private boolean walkToArea(WorldArea area, WorldPoint target)
    {
        WorldPoint location = Rs2Player.getWorldLocation();

        if (location != null && area.contains(location))
        {
            KspWalkerGuard.clearActiveWalker("ksp_account_builder_tutorial_reached_area");
            return true;
        }

        walkTutorialLocal(target, 3);
        Rs2Player.waitForWalking();

        location = Rs2Player.getWorldLocation();
        if (location != null && area.contains(location))
        {
            KspWalkerGuard.clearActiveWalker("ksp_account_builder_tutorial_reached_area");
            return true;
        }

        return false;
    }

    private void walkTutorialLocal(WorldPoint target, int reach)
    {
        if (target == null)
        {
            return;
        }

        KspWalkerGuard.clear("Tutorial Island:local-walk");
        WorldPoint playerLocation = Rs2Player.getWorldLocation();

        if (playerLocation != null && playerLocation.distanceTo(target) <= reach)
        {
            return;
        }

        Rs2Walker.walkTo(target, reach);
        Rs2Player.waitForWalking();
    }

    private boolean openTutorialPassage(int objectId, BooleanSupplier completed)
    {
        return openTutorialPassageAndWalk(objectId, null, 0, completed);
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
        for (int attempt = 0; attempt < 3; attempt++)
        {
            if (completed.getAsBoolean())
            {
                return true;
            }

            boolean interacted = Microbot.getRs2TileObjectCache()
                    .query()
                    .fromWorldView()
                    .interact(objectId, "Open");

            if (interacted)
            {
                sleepUntil(() -> completed.getAsBoolean(), 1200);
            }
            else
            {
                Rs2Random.waitEx(300, 100);
            }

            if (completed.getAsBoolean())
            {
                return true;
            }

            if (target != null)
            {
                walkTutorialLocal(target, reach);
                sleepUntil(() -> completed.getAsBoolean(), 1200);
            }
        }

        return completed.getAsBoolean();
    }

    private boolean isInArea(WorldArea area)
    {
        WorldPoint location = Rs2Player.getWorldLocation();
        return location != null && area.contains(location);
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

    private int randomDelay(int min, int max)
    {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

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

    public int getRemainingQueuedAccounts()
    {
        return Math.max(0, parseAccountQueue().size() - accountQueueIndex);
    }

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

    private enum Status
    {
        NAME,
        CHARACTER,
        GETTING_STARTED,
        SURVIVAL_GUIDE,
        COOKING_GUIDE,
        QUEST_GUIDE,
        MINING_GUIDE,
        COMBAT_GUIDE,
        BANKER_GUIDE,
        PRAYER_GUIDE,
        MAGE_GUIDE,
        FINISHED
    }
}
