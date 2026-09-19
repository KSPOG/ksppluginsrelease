package net.runelite.client.plugins.microbot.ksprobesofruin;

import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.cluescrolls.clues.emote.Emote;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
        name = PluginConstants.KSP + "Robes of Ruin Helper",
        description = "Quest-Helper-style guide for obtaining the full Robes of Ruin set.",
        tags = {"microbot", "ksp", "robes of ruin", "crack the clue", "guide", "shortest path", "emotes"},
        authors = {"KSP"},
        version = KspRobesOfRuinPlugin.VERSION,
        minClientVersion = "2.6.19",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class KspRobesOfRuinPlugin extends Plugin
{
    public static final String VERSION = "0.0.1";

    // RuneLite's Water Altar world-map/object location is 3185,3165. The solved
    // Robes of Ruin instruction is the tile immediately east of that entrance.
    static final WorldPoint LUMBRIDGE_DIG_TILE = new WorldPoint(3186, 3165, 0);

    // RuneLite's hard cryptic clue uses this exact gate-adjacent basement tile:
    // "Dig by the gate in the basement of the West Varrock bank."
    static final WorldPoint VARROCK_VAULT_GATE_TILE = new WorldPoint(3191, 9825, 0);

    static final List<String> REQUIRED_DIG_ITEMS = List.of(
            "Spade",
            "Amulet of defence",
            "Blue dye",
            "Bowl",
            "Chaos rune",
            "Emerald amulet",
            "Feather",
            "Fire tiara",
            "Fish food",
            "Hammer",
            "Iron chainbody",
            "Leather cowl",
            "Mind tiara",
            "Pie shell",
            "Poisoned fish food",
            "Potato",
            "Purple dye",
            "Raw beef",
            "Raw rat meat",
            "Raw sardine",
            "Red bead",
            "Redberries",
            "Redberry pie",
            "Shrimps",
            "Steel arrow",
            "Steel scimitar",
            "Tin ore",
            "Water rune"
    );

    static final List<Emote> EMOTE_SEQUENCE = List.of(
            Emote.PANIC,
            Emote.NO,
            Emote.BECKON,
            Emote.LAUGH,
            Emote.SHRUG,
            Emote.CRY,
            Emote.SPIN,
            Emote.YES,
            Emote.THINK,
            Emote.DANCE,
            Emote.BLOW_KISS,
            Emote.WAVE,
            Emote.BOW,
            Emote.PANIC,
            Emote.HEADBANG,
            Emote.JUMP_FOR_JOY,
            Emote.ANGRY
    );

    static final List<String> REWARD_ITEMS = List.of(
            "Hood of ruin",
            "Robe top of ruin",
            "Robe bottom of ruin",
            "Gloves of ruin",
            "Socks of ruin",
            "Cloak of ruin",
            "Infinite money bag"
    );

    private static final String PROGRESS_DIG = "_progressDig";
    private static final String PROGRESS_EMOTE = "_progressEmote";
    private static final String PROGRESS_VAULT = "_progressVault";
    private static final String PROGRESS_REWARDS = "_progressRewards";
    private static final long PATH_REFRESH_MS = 4_000L;

    @Inject private Client client;
    @Inject private EventBus eventBus;
    @Inject private ConfigManager configManager;
    @Inject private KspRobesOfRuinConfig config;
    @Inject private OverlayManager overlayManager;
    @Inject private KspRobesOfRuinOverlay overlay;
    @Inject private KspRobesOfRuinSceneOverlay sceneOverlay;
    @Inject private KspRobesOfRuinEmoteOverlay emoteOverlay;

    private boolean digComplete;
    private boolean awaitingDigContinue;
    private boolean vaultUnlocked;
    private boolean rewardsComplete;
    private int emoteIndex;
    private String feedback = "Ready";
    private WorldPoint lastPathTarget;
    private WorldPoint lastPathStart;
    private long lastPathUpdateMs;

    enum GuideStage
    {
        PREPARE_ITEMS,
        DIG_LUMBRIDGE,
        CONFIRM_DIG,
        TRAVEL_VARROCK,
        EMOTE_SEQUENCE,
        SEARCH_REWARDS,
        COMPLETE
    }

    @Provides
    KspRobesOfRuinConfig provideConfig(ConfigManager manager)
    {
        return manager.getConfig(KspRobesOfRuinConfig.class);
    }

    @Override
    protected void startUp()
    {
        loadProgress();
        overlayManager.add(overlay);
        overlayManager.add(sceneOverlay);
        overlayManager.add(emoteOverlay);
        feedback = "Guide started";
        updateShortestPath(true);
    }

    @Override
    protected void shutDown()
    {
        clearShortestPath();
        overlayManager.remove(emoteOverlay);
        overlayManager.remove(sceneOverlay);
        overlayManager.remove(overlay);
        awaitingDigContinue = false;
        feedback = "Stopped";
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (client.getLocalPlayer() == null)
        {
            return;
        }

        if (awaitingDigContinue && !Rs2Dialogue.isInDialogue())
        {
            awaitingDigContinue = false;
            setDigComplete(true);
            feedback = "Lumbridge clue confirmed - go to Varrock west bank basement";
        }

        String dialogue = clean(Rs2Dialogue.getDialogueText());
        if (!dialogue.isEmpty()
                && dialogue.contains("well done")
                && dialogue.contains("time to take your reward"))
        {
            unlockVault();
        }

        if (getRewardCount() >= REWARD_ITEMS.size() && !rewardsComplete)
        {
            rewardsComplete = true;
            configManager.setConfiguration(KspRobesOfRuinConfig.GROUP, PROGRESS_REWARDS, true);
            feedback = "All Robes of Ruin rewards detected";
        }

        if (emoteIndex > 0 && !vaultUnlocked
                && digComplete && !isInVarrockWestBankBasement())
        {
            setEmoteIndex(0);
            feedback = "Emote sequence reset after leaving the Varrock basement";
        }

        updateShortestPath(false);
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        String message = clean(event.getMessage());
        if (message.isEmpty())
        {
            return;
        }

        if (message.contains("you've found a new special clue")
                && message.contains("magical force prevents you"))
        {
            awaitingDigContinue = true;
            feedback = "Clue found - click Continue so the step counts";
            return;
        }

        if (message.contains("well done") && message.contains("time to take your reward"))
        {
            unlockVault();
        }
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        if (event.getParam1() != ComponentID.EMOTES_EMOTE_CONTAINER
                || resolveStage() != GuideStage.EMOTE_SEQUENCE
                || !isAtVaultGate())
        {
            return;
        }

        int spriteId = spriteForEmoteGridIndex(event.getParam0());
        if (spriteId < 0 || emoteIndex >= EMOTE_SEQUENCE.size())
        {
            return;
        }

        Emote expected = EMOTE_SEQUENCE.get(emoteIndex);
        if (spriteId == expected.getSpriteId())
        {
            int completed = emoteIndex + 1;
            setEmoteIndex(completed);
            if (completed >= EMOTE_SEQUENCE.size())
            {
                feedback = "17/17 complete - waiting for the vault teleport";
            }
            else
            {
                feedback = "Correct: " + expected.getName()
                        + " - next " + EMOTE_SEQUENCE.get(completed).getName();
            }
            return;
        }

        // The in-game puzzle restarts on a wrong emote. If the wrong click itself
        // is Panic, it also serves as the first input of a fresh sequence.
        int restartedAt = spriteId == EMOTE_SEQUENCE.get(0).getSpriteId() ? 1 : 0;
        setEmoteIndex(restartedAt);
        feedback = restartedAt == 1
                ? "Wrong emote - sequence restarted at 1/17 Panic"
                : "Wrong emote - sequence reset to Panic";
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!KspRobesOfRuinConfig.GROUP.equals(event.getGroup()))
        {
            return;
        }

        if ("resetProgress".equals(event.getKey())
                && Boolean.TRUE.equals(configManager.getConfiguration(
                        KspRobesOfRuinConfig.GROUP, "resetProgress", Boolean.class)))
        {
            resetProgress();
            configManager.setConfiguration(KspRobesOfRuinConfig.GROUP, "resetProgress", false);
        }

        updateShortestPath(true);
    }

    GuideStage resolveStage()
    {
        KspRobesOfRuinPhase phase = config.phase();
        if (phase == KspRobesOfRuinPhase.REWARD_CHESTS)
        {
            return rewardsComplete ? GuideStage.COMPLETE : GuideStage.SEARCH_REWARDS;
        }
        if (phase == KspRobesOfRuinPhase.VARROCK_EMOTES)
        {
            if (vaultUnlocked)
            {
                return GuideStage.SEARCH_REWARDS;
            }
            return isAtVaultGate() ? GuideStage.EMOTE_SEQUENCE : GuideStage.TRAVEL_VARROCK;
        }
        if (phase == KspRobesOfRuinPhase.LUMBRIDGE_DIG)
        {
            if (awaitingDigContinue)
            {
                return GuideStage.CONFIRM_DIG;
            }
            return hasExactDigInventory() ? GuideStage.DIG_LUMBRIDGE : GuideStage.PREPARE_ITEMS;
        }

        if (rewardsComplete)
        {
            return GuideStage.COMPLETE;
        }
        if (vaultUnlocked)
        {
            return GuideStage.SEARCH_REWARDS;
        }
        if (!digComplete)
        {
            if (awaitingDigContinue)
            {
                return GuideStage.CONFIRM_DIG;
            }
            return hasExactDigInventory() ? GuideStage.DIG_LUMBRIDGE : GuideStage.PREPARE_ITEMS;
        }
        return isAtVaultGate() ? GuideStage.EMOTE_SEQUENCE : GuideStage.TRAVEL_VARROCK;
    }

    WorldPoint getSceneTarget()
    {
        GuideStage stage = resolveStage();
        if (stage == GuideStage.DIG_LUMBRIDGE || stage == GuideStage.CONFIRM_DIG)
        {
            return LUMBRIDGE_DIG_TILE;
        }
        if (stage == GuideStage.TRAVEL_VARROCK || stage == GuideStage.EMOTE_SEQUENCE)
        {
            return VARROCK_VAULT_GATE_TILE;
        }
        return null;
    }

    String getSceneLabel()
    {
        GuideStage stage = resolveStage();
        if (stage == GuideStage.DIG_LUMBRIDGE || stage == GuideStage.CONFIRM_DIG)
        {
            return "Dig here";
        }
        if (stage == GuideStage.TRAVEL_VARROCK || stage == GuideStage.EMOTE_SEQUENCE)
        {
            return "Stand here for emotes";
        }
        return "";
    }

    String getInstruction()
    {
        switch (resolveStage())
        {
            case PREPARE_ITEMS:
                return "Put the exact 28 required items in your inventory.";
            case DIG_LUMBRIDGE:
                return "Follow the route, stand on the highlighted tile east of the Water Altar, then dig.";
            case CONFIRM_DIG:
                return "Click Continue on the clue message. The step does not count until it is dismissed.";
            case TRAVEL_VARROCK:
                return "Go to Varrock west bank basement and stand by the vault gates.";
            case EMOTE_SEQUENCE:
                Emote emote = getExpectedEmote();
                return emote == null
                        ? "17/17 entered. Wait for the Mysterious Old Man to confirm and teleport you."
                        : "Perform " + emote.getName() + " (" + (emoteIndex + 1) + "/17).";
            case SEARCH_REWARDS:
                return "Search the highlighted chests inside the vault until all rewards are collected.";
            case COMPLETE:
            default:
                return "All seven Robes of Ruin rewards detected.";
        }
    }

    int getStepNumber()
    {
        switch (resolveStage())
        {
            case PREPARE_ITEMS: return 1;
            case DIG_LUMBRIDGE:
            case CONFIRM_DIG: return 2;
            case TRAVEL_VARROCK: return 3;
            case EMOTE_SEQUENCE: return 4;
            case SEARCH_REWARDS:
            case COMPLETE:
            default: return 5;
        }
    }

    int getEmoteIndex()
    {
        return emoteIndex;
    }

    Emote getExpectedEmote()
    {
        if (resolveStage() != GuideStage.EMOTE_SEQUENCE || !isAtVaultGate()
                || emoteIndex < 0 || emoteIndex >= EMOTE_SEQUENCE.size())
        {
            return null;
        }
        return EMOTE_SEQUENCE.get(emoteIndex);
    }

    String getFeedback()
    {
        return feedback;
    }

    int getRewardCount()
    {
        int count = 0;
        for (String reward : REWARD_ITEMS)
        {
            if (Rs2Inventory.hasItem(reward, true))
            {
                count++;
            }
        }
        return count;
    }

    int getPresentRequiredCount()
    {
        return REQUIRED_DIG_ITEMS.size() - getMissingItems().size();
    }

    List<String> getMissingItems()
    {
        List<String> missing = new ArrayList<>();
        if (!Microbot.isLoggedIn())
        {
            missing.addAll(REQUIRED_DIG_ITEMS);
            return missing;
        }

        for (String item : REQUIRED_DIG_ITEMS)
        {
            if (!Rs2Inventory.hasItem(item, true))
            {
                missing.add(item);
            }
        }
        return missing;
    }

    List<String> getExtraInventoryItems()
    {
        if (!Microbot.isLoggedIn())
        {
            return Collections.emptyList();
        }

        Set<String> required = new LinkedHashSet<>();
        for (String item : REQUIRED_DIG_ITEMS)
        {
            required.add(item.toLowerCase(Locale.ROOT));
        }

        Set<String> extras = new LinkedHashSet<>();
        Rs2Inventory.all().forEach(item ->
        {
            String name = item == null ? null : item.getName();
            if (name != null && !required.contains(name.toLowerCase(Locale.ROOT)))
            {
                extras.add(name);
            }
        });
        return new ArrayList<>(extras);
    }

    boolean isAtVaultGate()
    {
        WorldPoint player = playerLocation();
        return player != null
                && player.getPlane() == VARROCK_VAULT_GATE_TILE.getPlane()
                && player.distanceTo(VARROCK_VAULT_GATE_TILE) <= 5;
    }

    List<Rs2TileObjectModel> getVisibleRewardChests()
    {
        if (resolveStage() != GuideStage.SEARCH_REWARDS || !isInVarrockWestBankBasement())
        {
            return Collections.emptyList();
        }

        try
        {
            return Microbot.getRs2TileObjectCache().query()
                    .where(object -> object != null
                            && object.getName() != null
                            && "Chest".equalsIgnoreCase(object.getName())
                            && object.getWorldLocation() != null
                            && isInVarrockWestBankBasement(object.getWorldLocation()))
                    .within(30)
                    .toList();
        }
        catch (RuntimeException ignored)
        {
            return Collections.emptyList();
        }
    }

    private boolean hasExactDigInventory()
    {
        return getMissingItems().isEmpty() && getExtraInventoryItems().isEmpty();
    }

    private boolean isInVarrockWestBankBasement()
    {
        return isInVarrockWestBankBasement(playerLocation());
    }

    private boolean isInVarrockWestBankBasement(WorldPoint point)
    {
        return point != null
                && point.getPlane() == 0
                && point.getX() >= 3168 && point.getX() <= 3210
                && point.getY() >= 9808 && point.getY() <= 9850;
    }

    private WorldPoint playerLocation()
    {
        return client.getLocalPlayer() == null ? null : client.getLocalPlayer().getWorldLocation();
    }

    private int spriteForEmoteGridIndex(int gridIndex)
    {
        Widget container = client.getWidget(ComponentID.EMOTES_EMOTE_CONTAINER);
        if (container == null)
        {
            return -1;
        }

        Widget[] children = container.getDynamicChildren();
        if (children == null)
        {
            return -1;
        }

        for (Widget widget : children)
        {
            if (widget == null)
            {
                continue;
            }
            int index = widget.getOriginalX() / 42 + ((widget.getOriginalY() - 6) / 49) * 4;
            if (index == gridIndex)
            {
                return widget.getSpriteId();
            }
        }
        return -1;
    }

    private void loadProgress()
    {
        digComplete = Boolean.TRUE.equals(configManager.getConfiguration(
                KspRobesOfRuinConfig.GROUP, PROGRESS_DIG, Boolean.class));
        Integer savedEmote = configManager.getConfiguration(
                KspRobesOfRuinConfig.GROUP, PROGRESS_EMOTE, Integer.class);
        emoteIndex = savedEmote == null ? 0 : Math.max(0, Math.min(EMOTE_SEQUENCE.size(), savedEmote));
        vaultUnlocked = Boolean.TRUE.equals(configManager.getConfiguration(
                KspRobesOfRuinConfig.GROUP, PROGRESS_VAULT, Boolean.class));
        rewardsComplete = Boolean.TRUE.equals(configManager.getConfiguration(
                KspRobesOfRuinConfig.GROUP, PROGRESS_REWARDS, Boolean.class));
    }

    private void setDigComplete(boolean value)
    {
        digComplete = value;
        configManager.setConfiguration(KspRobesOfRuinConfig.GROUP, PROGRESS_DIG, value);
    }

    private void setEmoteIndex(int value)
    {
        emoteIndex = Math.max(0, Math.min(EMOTE_SEQUENCE.size(), value));
        configManager.setConfiguration(KspRobesOfRuinConfig.GROUP, PROGRESS_EMOTE, emoteIndex);
    }

    private void resetProgress()
    {
        digComplete = false;
        awaitingDigContinue = false;
        vaultUnlocked = false;
        rewardsComplete = false;
        setEmoteIndex(0);
        configManager.setConfiguration(KspRobesOfRuinConfig.GROUP, PROGRESS_DIG, false);
        configManager.setConfiguration(KspRobesOfRuinConfig.GROUP, PROGRESS_VAULT, false);
        configManager.setConfiguration(KspRobesOfRuinConfig.GROUP, PROGRESS_REWARDS, false);
        feedback = "Saved guide progress reset";
        clearShortestPath();
    }

    private void unlockVault()
    {
        setDigComplete(true);
        setEmoteIndex(EMOTE_SEQUENCE.size());
        vaultUnlocked = true;
        configManager.setConfiguration(KspRobesOfRuinConfig.GROUP, PROGRESS_VAULT, true);
        feedback = "Vault unlocked - search the highlighted chests";
        clearShortestPath();
    }

    private void updateShortestPath(boolean force)
    {
        if (client.getLocalPlayer() == null)
        {
            return;
        }

        if (!config.useShortestPath())
        {
            clearShortestPath();
            return;
        }

        WorldPoint target = getRouteTarget();
        if (target == null)
        {
            clearShortestPath();
            return;
        }

        WorldPoint start = playerLocation();
        if (start == null)
        {
            return;
        }

        long now = System.currentTimeMillis();
        boolean targetChanged = !target.equals(lastPathTarget);
        boolean playerMoved = lastPathStart == null
                || start.getPlane() != lastPathStart.getPlane()
                || start.distanceTo(lastPathStart) >= 4;

        if (!force && !targetChanged && !playerMoved && now - lastPathUpdateMs < PATH_REFRESH_MS)
        {
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("start", start);
        data.put("target", target);

        Map<String, Object> pathConfig = new HashMap<>();
        pathConfig.put("drawTiles", true);
        pathConfig.put("drawMinimap", true);
        pathConfig.put("drawMap", true);
        data.put("config", pathConfig);

        eventBus.post(new PluginMessage("shortestpath", "path", data));
        lastPathTarget = target;
        lastPathStart = start;
        lastPathUpdateMs = now;
    }

    private WorldPoint getRouteTarget()
    {
        GuideStage stage = resolveStage();
        WorldPoint player = playerLocation();

        if (stage == GuideStage.DIG_LUMBRIDGE)
        {
            return player != null && player.getPlane() == 0 && player.distanceTo(LUMBRIDGE_DIG_TILE) <= 2
                    ? null : LUMBRIDGE_DIG_TILE;
        }

        if (stage == GuideStage.TRAVEL_VARROCK)
        {
            return VARROCK_VAULT_GATE_TILE;
        }

        if (stage == GuideStage.EMOTE_SEQUENCE && !isAtVaultGate())
        {
            return VARROCK_VAULT_GATE_TILE;
        }

        return null;
    }

    private void clearShortestPath()
    {
        if (lastPathTarget == null)
        {
            return;
        }

        eventBus.post(new PluginMessage("shortestpath", "clear", Collections.emptyMap()));
        lastPathTarget = null;
        lastPathStart = null;
        lastPathUpdateMs = 0L;
    }

    private static String clean(String message)
    {
        return message == null
                ? ""
                : message.replaceAll("<[^>]+>", "").trim().toLowerCase(Locale.ROOT);
    }
}
