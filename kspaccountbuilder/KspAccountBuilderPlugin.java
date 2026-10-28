package net.runelite.client.plugins.microbot.kspaccountbuilder;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.GameTick;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerPlugin;
import net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.randomevents.KspRandomEventSolver;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.JOptionPane;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseWheelEvent;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.MouseWheelListener;
import net.runelite.client.input.MouseManager;
import net.runelite.client.input.MouseAdapter;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

@PluginDescriptor(
        name = PluginConstants.KSP + "Account Builder",
        description = "Builds a F2P main account with automated account progression",
        tags = {"microbot", "ksp", "account", "builder", "hotreload"},
        authors = {"KSP"},
        version = KspAccountBuilderPlugin.VERSION,
        minClientVersion = "2.0.13",
        enabledByDefault = true,
        isExternal = true
)
@Slf4j
@SuppressWarnings("unused")
public class KspAccountBuilderPlugin extends Plugin
{
    public static final String VERSION = "1.9.61";

    @Inject
    private KspAccountBuilderScript script;

    @Inject
    private KspAccountBuilderConfig config;

    @Inject
    private KSPAccountBuilderOverlay overlay;

    @Inject
    private ConfigManager configManager;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private MouseManager mouseManager;
    @Inject private KeyManager keyManager;
    private boolean hudMouseRegistered;

    @Inject
    private ClientToolbar clientToolbar;

    // UIManager lives in the host classloader and survives plugin hot reloads.
    private static final String SIDEBAR_REGISTRY_KEY = "ksp.accountbuilder.sidebar.v1";
    // Registry lives in the host Swing classloader, not the plugin classloader.
    // SourceLoader may briefly leave two enabled plugin instances after reload.
    private static final String RUNTIME_OWNER_KEY = "ksp.accountbuilder.runtime.owner.v1";
    private static final String OVERLAY_OWNER_KEY = "ksp.accountbuilder.overlay.owner.v1";
    private final Object runtimeOwnerToken = new Object();
    private final MouseAdapter hudMouseListener = new MouseAdapter()
    {
        @Override
        public MouseEvent mousePressed(MouseEvent e)
        {
            if (javax.swing.SwingUtilities.isEventDispatchThread()
                    && !net.runelite.client.plugins.microbot.util.mouse.BotEventGuard.isSynthetic()
                    && overlay != null
                    && e.getButton() == MouseEvent.BUTTON1 && overlay.clickAt(e.getPoint()))
                e.consume();
            else if (shouldBlockCanvasInput(e)) e.consume();
            return e;
        }
        @Override
        public MouseEvent mouseReleased(MouseEvent e)
        {
            if (shouldBlockCanvasInput(e) || javax.swing.SwingUtilities.isEventDispatchThread()
                    && !net.runelite.client.plugins.microbot.util.mouse.BotEventGuard.isSynthetic()
                     && overlay != null && overlay.acceptsMouse(e.getPoint())) e.consume();
            return e;
        }
        @Override
        public MouseEvent mouseClicked(MouseEvent e)
        {
            if (shouldBlockCanvasInput(e) || javax.swing.SwingUtilities.isEventDispatchThread()
                    && !net.runelite.client.plugins.microbot.util.mouse.BotEventGuard.isSynthetic()
                     && overlay != null && overlay.acceptsMouse(e.getPoint())) e.consume();
            return e;
        }
        @Override
        public MouseEvent mouseDragged(MouseEvent e)
        {
            if (shouldBlockCanvasInput(e) || javax.swing.SwingUtilities.isEventDispatchThread()
                    && !net.runelite.client.plugins.microbot.util.mouse.BotEventGuard.isSynthetic()
                     && overlay != null && overlay.acceptsMouse(e.getPoint())) e.consume();
            return e;
        }
        @Override
        public MouseEvent mouseMoved(MouseEvent e)
        {
            // Update the HUD's own hover state before consuming physical
            // movement. Otherwise tile/object tooltips and menu targets
            // continue responding while Block Input is enabled.
            if (javax.swing.SwingUtilities.isEventDispatchThread()
                    && !net.runelite.client.plugins.microbot.util.mouse.BotEventGuard.isSynthetic()
                    && overlay != null)
                overlay.hoverAt(e.getPoint());
            if (shouldBlockCanvasInput(e)) e.consume();
            return e;
        }
    };
    private final MouseWheelListener hudWheelListener = new MouseWheelListener()
    {
        @Override public MouseWheelEvent mouseWheelMoved(MouseWheelEvent e)
        {
            if (shouldBlockCanvasInput(e)) e.consume();
            return e;
        }
    };
    private final KeyListener hudKeyListener = new KeyListener()
    {
        @Override public void keyPressed(KeyEvent e) { interceptKey(e); }
        @Override public void keyReleased(KeyEvent e) { interceptKey(e); }
        @Override public void keyTyped(KeyEvent e) { interceptKey(e); }
    };
    private void interceptKey(KeyEvent e)
    {
        if (!mayBlockGameInput() || !javax.swing.SwingUtilities.isEventDispatchThread()) return;
        // Emergency unlock, including when companion UI is unreachable.
        if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_ESCAPE)
            configManager.setConfiguration(KspAccountBuilderConfig.CONFIG_GROUP, "blockGameInput", false);
        e.consume();
    }
    private boolean mayBlockGameInput()
    {
        // This setting protects automation from accidental physical input
        // even while the Builder is running or the HUD is hidden.
        return config != null && config.blockGameInput()
                && mayRenderOverlay() && Microbot.isLoggedIn();
    }

    private boolean shouldBlockCanvasInput(MouseEvent event)
    {
        if (!mayBlockGameInput()) return false;

        // Microbot's AwtEmitter wraps bot-owned input in BotEventGuard.
        // Unlike screen-coordinate or EDT checks, this identifies the input
        // producer, so bot clicks/movement cannot be mistaken for human input.
        if (net.runelite.client.plugins.microbot.util.mouse.BotEventGuard.isSynthetic())
            return false;

        // The bot may also dispatch directly on its worker thread.
        // Consume only ordinary physical canvas input arriving via AWT.
        return javax.swing.SwingUtilities.isEventDispatchThread();
    }

    /**
     * The host CanvasInputListener records physical input independently of
     * MouseManager event consumption. Without this guard its InputArbiter
     * marks HUMAN and aborts BotMouse gestures even when our events are blocked.
     * Restore the host's previous setting on disable/unload.
     */
    private synchronized void syncInputArbiterGuard()
    {
        boolean shouldOwn = runtimeActive && ownsRuntime() && isCurrentSidebarOwner()
                && config != null && config.blockGameInput();
        if (shouldOwn && !inputArbiterOverrideOwned)
        {
            inputArbiterPreviouslyDisabled =
                    net.runelite.client.plugins.microbot.util.input.InputArbiter.isDisabled();
            net.runelite.client.plugins.microbot.util.input.InputArbiter.setDisabled(true);
            inputArbiterOverrideOwned = true;
        }
        else if (!shouldOwn)
        {
            restoreInputArbiterGuard();
        }
    }

    private synchronized void restoreInputArbiterGuard()
    {
        if (!inputArbiterOverrideOwned) return;
        net.runelite.client.plugins.microbot.util.input.InputArbiter
                .setDisabled(inputArbiterPreviouslyDisabled);
        inputArbiterOverrideOwned = false;
    }

    private void registerHudMouse()
    {
        if (!hudMouseRegistered && mouseManager != null)
        {
            mouseManager.registerMouseListener(hudMouseListener);
            mouseManager.registerMouseWheelListener(hudWheelListener);
            if (keyManager != null) keyManager.registerKeyListener(hudKeyListener);
            hudMouseRegistered = true;
        }
    }
    private void unregisterHudMouse()
    {
        if (hudMouseRegistered && mouseManager != null)
        {
            mouseManager.unregisterMouseListener(hudMouseListener);
            mouseManager.unregisterMouseWheelListener(hudWheelListener);
            if (keyManager != null) keyManager.unregisterKeyListener(hudKeyListener);
            hudMouseRegistered = false;
        }
    }

    void dispatchHudAction(String action, KspCinematicHudRenderer renderer)
    {
        if (!mayRenderOverlay()) return;
        if (action.startsWith("queue:row:"))
        {
            try { renderer.selectQueueRow(Integer.parseInt(action.substring("queue:row:".length()))); }
            catch (NumberFormatException ignored) { }
            return;
        }
        if (action.startsWith("queue:") && !action.equals("queue-close"))
        {
            if (isRuntimeActive()) return; // Prevent editing the running queue snapshot.
            String queue = config.orderedTaskQueue();
            int selected = renderer.selectedQueueRow();
            if ("queue:add".equals(action))
            {
                String task = (String) JOptionPane.showInputDialog(null, "Choose a task",
                        "Add Account Builder Task", JOptionPane.PLAIN_MESSAGE, null,
                        KspAccountBuilderTaskQueue.SUPPORTED_TASKS,
                        KspAccountBuilderTaskQueue.SUPPORTED_TASKS[0]);
                if (task == null) return;
                String value = JOptionPane.showInputDialog(null,
                        "Active work duration in seconds (30–86400):", "300");
                if (value == null) return;
                try
                {
                    int seconds = Integer.parseInt(value.trim());
                    String updated = KspAccountBuilderTaskQueue.append(queue, task, seconds);
                    configManager.setConfiguration(KspAccountBuilderConfig.CONFIG_GROUP,
                            "orderedTaskQueue", updated);
                    if (!updated.isEmpty())
                        configManager.setConfiguration(KspAccountBuilderConfig.CONFIG_GROUP,
                                "useOrderedTaskQueue", true);
                    renderer.selectQueueRow(KspAccountBuilderTaskQueue.parse(updated).size() - 1);
                }
                catch (NumberFormatException ignored)
                {
                    JOptionPane.showMessageDialog(null, "Enter a whole number from 30 to 86400 seconds.");
                }
                return;
            }
            if ("queue:up".equals(action) || "queue:down".equals(action))
            {
                int delta = action.equals("queue:up") ? -1 : 1;
                String updated = KspAccountBuilderTaskQueue.move(queue, selected, delta);
                configManager.setConfiguration(KspAccountBuilderConfig.CONFIG_GROUP,
                        "orderedTaskQueue", updated);
                if (!updated.equals(queue)) renderer.selectQueueRow(selected + delta);
                else renderer.scrollQueue(delta);
                return;
            }
            if ("queue:remove".equals(action))
            {
                String updated = KspAccountBuilderTaskQueue.remove(queue, selected);
                configManager.setConfiguration(KspAccountBuilderConfig.CONFIG_GROUP,
                        "orderedTaskQueue", updated);
                if (updated.isEmpty())
                    configManager.setConfiguration(KspAccountBuilderConfig.CONFIG_GROUP,
                            "useOrderedTaskQueue", false);
                renderer.selectQueueRow(Math.min(selected, KspAccountBuilderTaskQueue.parse(updated).size() - 1));
                return;
            }
        }
        if (action.startsWith("nav:"))
        {
            renderer.navigate(action.substring(4));
            return;
        }
        switch (action)
        {
            case "minimize":
                // Minimize the large viewport HUD into the real chatbox.
                configManager.setConfiguration(KspAccountBuilderConfig.CONFIG_GROUP, "hudFitChatbox", true);
                setCompactOverlay(false);
                return;
            case "restore":
                // The chatbox "Full view" button must actually restore the
                // full cinematic layout rather than changing an unused flag.
                configManager.setConfiguration(KspAccountBuilderConfig.CONFIG_GROUP, "hudFitChatbox", false);
                setCompactOverlay(false);
                return;
            case "collapse": renderer.toggleCollapsed(); return;
            case "close":
                setHudVisible(false);
                configManager.setConfiguration(KspAccountBuilderConfig.CONFIG_GROUP, "showHud", false);
                return;
            case "pause": script.requestManualPause(); return;
            case "resume": script.requestManualResume(); return;
            case "stop":
                configManager.setConfiguration(KspAccountBuilderConfig.CONFIG_GROUP, "runAccountBuilder", false);
                return;
            case "skip": script.requestManualSkip(); return;
            case "settings": case "settings-header": case "settings-close": renderer.openSettings(); return;
            case "queuetip": renderer.openQueue(); return;
            case "queue-close": renderer.openQueue(); return;
            case "setting:block":
                configManager.setConfiguration(KspAccountBuilderConfig.CONFIG_GROUP, "blockGameInput",
                        !config.blockGameInput());
                return;
            case "setting:tooltips":
                configManager.setConfiguration(KspAccountBuilderConfig.CONFIG_GROUP, "hudTooltips",
                        !config.hudTooltips());
                return;
            case "setting:lock":
                boolean lock = !config.lockHudPosition();
                if (overlay != null) overlay.applyPositionLock(lock);
                configManager.setConfiguration(KspAccountBuilderConfig.CONFIG_GROUP, "lockHudPosition", lock);
                return;
            case "setting:animation":
                configManager.setConfiguration(KspAccountBuilderConfig.CONFIG_GROUP, "showSkillAnimations",
                        !config.showSkillAnimations());
                return;
            case "animation-mode":
                configManager.setConfiguration(KspAccountBuilderConfig.CONFIG_GROUP, "hudAnimationIntensity",
                        (config.hudAnimationIntensity() + 1) % 3);
                return;
            case "setting:compact":
                setCompactOverlay(!isCompactOverlay());
                configManager.setConfiguration(KspAccountBuilderConfig.CONFIG_GROUP, "compactHud", isCompactOverlay());
                return;
            case "setting:details":
                boolean newDetails = !config.hudDetailsVisible();
                setOverlayDetailsVisible(newDetails);
                configManager.setConfiguration(KspAccountBuilderConfig.CONFIG_GROUP, "hudDetailsVisible", newDetails);
                return;
            case "setting:visible":
                setHudVisible(false);
                configManager.setConfiguration(KspAccountBuilderConfig.CONFIG_GROUP, "showHud", false);
                return;
            case "scale-up": case "scale-down":
                int[] scales = {75, 100, 125, 150};
                int index = 1;
                for (int i = 0; i < scales.length; i++)
                    if (config.hudScalePercent() == scales[i]) index = i;
                index = Math.max(0, Math.min(scales.length - 1, index + (action.equals("scale-up") ? 1 : -1)));
                configManager.setConfiguration(KspAccountBuilderConfig.CONFIG_GROUP, "hudScalePercent", scales[index]);
                return;
            default: return;
        }
    }

    private KspAccountBuilderPanel sidePanel;
    private NavigationButton navButton;
    private volatile boolean panelEnabled;

    private KspRandomEventSolver randomEventSolver;
    private final KspAccountBuilderSkillingAntiban skillingAntiban = new KspAccountBuilderSkillingAntiban();
    private volatile boolean runtimeActive;
    private volatile boolean acceptingRuntimeChanges;
    private String antibanStateKey;
    private boolean inputArbiterOverrideOwned;
    private boolean inputArbiterPreviouslyDisabled;

    @Provides
    KspAccountBuilderConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(KspAccountBuilderConfig.class);
    }

    @Override
    protected synchronized void startUp()
    {
        acceptingRuntimeChanges = false;
        cleanupRuntime("restart-before-start");
        migrateSingleSkillTargetDefaults();
        // Start/Stop is an explicit action, not a persisted auto-run option.
        // Always require a fresh Start click after plugin enable or hot reload.
        configManager.setConfiguration(KspAccountBuilderConfig.CONFIG_GROUP, "runAccountBuilder", false);
        panelEnabled = true;
        registerHudMouse();
        if (overlay != null) overlay.applyPositionLock(config.lockHudPosition());
        setCompactOverlay(config.compactHud());
        setOverlayDetailsVisible(config.hudDetailsVisible());
        setHudVisible(config.showHud());
        addSidePanel();
        acceptingRuntimeChanges = true;
        syncInputArbiterGuard();
        log.info("KSP Account Builder loaded stopped; press Start in sidebar to begin automation");
    }

    @Subscribe
    public synchronized void onConfigChanged(ConfigChanged event)
    {
        if (!acceptingRuntimeChanges
                || !KspAccountBuilderConfig.CONFIG_GROUP.equals(event.getGroup()))
        {
            return;
        }
        if ("blockGameInput".equals(event.getKey()))
        {
            syncInputArbiterGuard();
            return;
        }
        if (!"runAccountBuilder".equals(event.getKey())) return;
        if (config.runAccountBuilder())
        {
            if (isCurrentSidebarOwner()) startRuntime();
            else log.debug("Ignoring Start event from superseded Account Builder sidebar instance");
        }
        else cleanupRuntime("config-stop");
        syncInputArbiterGuard();
    }

    private void startRuntime()
    {
        // One authoritative runtime per game client, including across plugin
        // classloaders. A per-instance runtimeActive flag is not sufficient.
        synchronized (UIManager.getDefaults())
        {
            if (!acceptingRuntimeChanges || runtimeActive || !isCurrentSidebarOwner()) return;
            Object registry = UIManager.get(RUNTIME_OWNER_KEY);
            if (registry instanceof Object[])
            {
                Object[] previous = (Object[]) registry;
                if (previous.length >= 2 && previous[0] != runtimeOwnerToken
                        && previous[1] instanceof Runnable)
                {
                    log.warn("Account Builder runtime takeover: stopping previous source-loader instance");
                    try { ((Runnable) previous[1]).run(); }
                    catch (Exception ex)
                    {
                        log.error("Cannot safely stop old Account Builder runtime; refusing second start", ex);
                        return;
                    }
                }
            }
            // Register BEFORE the script starts so concurrent configuration
            // callbacks cannot each launch independent child task loops.
            UIManager.put(RUNTIME_OWNER_KEY,
                    new Object[]{runtimeOwnerToken, (Runnable) this::stopSupersededRuntime});
            try
            {
                log.info("Starting KSP Account Builder runtime from configuration | owner={}",
                        System.identityHashCode(runtimeOwnerToken));
                stopStandaloneBreakHandler();
                // Clear any leftover overlay registered by a superseded instance.
                removePreviousOverlay();
                overlayManager.add(overlay);
                UIManager.put(OVERLAY_OWNER_KEY, new Object[]{runtimeOwnerToken, overlay});
                randomEventSolver = new KspRandomEventSolver();
                Microbot.getBlockingEventManager().add(randomEventSolver);
                resetAntibanState();
                runtimeActive = true;
                if (!script.run(config))
                {
                    log.warn("KSP Account Builder script refused to start");
                    cleanupRuntime("start-failed");
                }
            }
            catch (Exception ex)
            {
                log.error("KSP Account Builder start failed", ex);
                cleanupRuntime("start-exception");
            }
        }
    }

    private void stopSupersededRuntime()
    {
        // A superseded instance must ignore future config events, which are
        // broadcast to all enabled plugin instances by RuneLite.
        acceptingRuntimeChanges = false;
        unregisterHudMouse();
        cleanupRuntime("superseded-by-source-loader");
    }

    /** Only the plugin instance whose sidebar is actually visible may start. */
    private boolean isCurrentSidebarOwner()
    {
        Object registration = UIManager.get(SIDEBAR_REGISTRY_KEY);
        return panelEnabled && navButton != null
                && registration instanceof Object[]
                && ((Object[]) registration).length > 0
                && ((Object[]) registration)[0] == navButton;
    }

    private void removePreviousOverlay()
    {
        Object registration = UIManager.get(OVERLAY_OWNER_KEY);
        if (!(registration instanceof Object[]) || ((Object[]) registration).length < 2) return;
        Object old = ((Object[]) registration)[1];
        if (old instanceof KSPAccountBuilderOverlay)
        {
            // Same-classloader registrations are normally removed here.
            overlayManager.remove((KSPAccountBuilderOverlay) old);
        }
        else if (old instanceof net.runelite.client.ui.overlay.Overlay)
        {
            // Overlay base type survives separate SourceLoader classloaders.
            overlayManager.remove((net.runelite.client.ui.overlay.Overlay) old);
        }
        UIManager.getDefaults().remove(OVERLAY_OWNER_KEY);
    }

    boolean mayRenderOverlay()
    {
        return runtimeActive && ownsRuntime() && isCurrentSidebarOwner();
    }

    private boolean ownsRuntime()
    {
        Object registry = UIManager.get(RUNTIME_OWNER_KEY);
        return registry instanceof Object[]
                && ((Object[]) registry).length > 0
                && ((Object[]) registry)[0] == runtimeOwnerToken;
    }

    @Override
    protected synchronized void shutDown()
    {
        boolean wasSidebarOwner = isCurrentSidebarOwner();
        acceptingRuntimeChanges = false;
        panelEnabled = false;
        unregisterHudMouse();
        cleanupRuntime("plugin-stop");
        restoreInputArbiterGuard();
        if (wasSidebarOwner)
            configManager.setConfiguration(KspAccountBuilderConfig.CONFIG_GROUP, "runAccountBuilder", false);
        removeSidePanel();
    }

    public synchronized void prepareHotUnload()
    {
        boolean wasSidebarOwner = isCurrentSidebarOwner();
        acceptingRuntimeChanges = false;
        panelEnabled = false;
        unregisterHudMouse();
        cleanupRuntime("hot-unload");
        restoreInputArbiterGuard();
        if (wasSidebarOwner)
            configManager.setConfiguration(KspAccountBuilderConfig.CONFIG_GROUP, "runAccountBuilder", false);
        removeSidePanel();
    }

    public void afterHotReload()
    {
        log.info("KSP Account Builder hot reload completed | version={}", VERSION);
    }

    public boolean isAntibanEnabledForCurrentTask()
    {
        return config != null && config.useAntiban();
    }

    public int getAntibanChancePercent()
    {
        return config == null ? 0 : Math.max(0, Math.min(100, config.antibanChancePercent()));
    }

    public long getAntibanTimeUntilNextActionSeconds()
    {
        return -1L;
    }

    public String getAntibanActivity()
    {
        if (config == null || !config.useAntiban())
        {
            return "Disabled";
        }
        return "Task profile active; custom mouse OFF";
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        syncInputArbiterGuard();
        if (!runtimeActive || !ownsRuntime() || script == null || config == null)
        {
            return;
        }

        String taskName = script.getCurrentTaskName();

        // GameTick enforcement runs independently of the tutorial worker.
        // A stale bank-cache or old task worker must not retain a mainland
        // WebWalker destination while this client is still on Tutorial Island.
        if ("TUTORIAL_ISLAND".equals(taskName)
                && Microbot.getClient() != null
                && Microbot.getClient().getLocalPlayer() != null)
        {
            net.runelite.api.coords.WorldPoint player =
                    Microbot.getClient().getLocalPlayer().getWorldLocation();
            net.runelite.api.coords.WorldPoint destination = Rs2Walker.getCurrentTarget();
            if (net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.tutorialisland.areas.TutAreas.contains(player)
                    && destination != null
                    && !net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.tutorialisland.areas.TutAreas.contains(destination))
            {
                Rs2Walker.clearWalkingRoute("ksp_tutorial_game_tick_mainland_route_rejected");
            }
        }

        // The EssenceMining worker may be inside a synchronous Rs2Walker call
        // when Aubury teleports the player into the essence mine. Cancel its
        // obsolete surface routes from the independent game-tick callback,
        // rather than waiting for the worker to return and notice the region.
        // Never cancel the portal route used to leave the mine.
        if ("RUNE_ESSENCE".equals(taskName)
                && Microbot.getClient() != null
                && Microbot.getClient().getLocalPlayer() != null
                && Microbot.getClient().getLocalPlayer().getWorldLocation() != null
                && Microbot.getClient().getLocalPlayer().getWorldLocation().getRegionID() == 11595)
        {
            Rs2Walker.clearWalkingRoute("Rune Essence:aubury");
            Rs2Walker.clearWalkingRoute("Rune Essence:bank");
        }
        boolean useAntiban = config.useAntiban();
        int chance = Math.max(0, Math.min(100, config.antibanChancePercent()));
        String stateKey = taskName + '|' + useAntiban + '|' + chance;

        if (!stateKey.equals(antibanStateKey))
        {
            antibanStateKey = stateKey;
            skillingAntiban.reset();
            log.info("Account Builder antiban state | task={} enabled={} actionChance={}%%",
                    taskName, useAntiban, chance);
        }

        // The extra GameTick mouse controller has been suspended because it
        // races banking/GE/skilling input. The builder's native antiban profiles
        // and break handling remain configured by KspAccountBuilderScript.
        // Never override global Rs2AntibanSettings on every GameTick:
        // that fights script-specific state changes during task handoffs.
        skillingAntiban.reset();
    }

    private void resetAntibanState()
    {
        antibanStateKey = null;
        skillingAntiban.reset();
        Rs2AntibanSettings.antibanEnabled = false;
    }

    private void stopStandaloneBreakHandler()
    {
        try
        {
            Plugin breakHandler = Microbot.getPlugin(BreakHandlerPlugin.class.getName());
            if (breakHandler == null || !Microbot.isPluginEnabled(breakHandler))
            {
                return;
            }

            boolean stopped = Microbot.stopPlugin(breakHandler);
            log.info("Stopped standalone Break Handler before Account Builder start | stopped={}", stopped);
        }
        catch (Exception ex)
        {
            log.warn("Unable to stop standalone Break Handler before Account Builder start", ex);
        }
    }

    private void cleanupRuntime(String reason)
    {
        // Disable callbacks before child cleanup. Only the owning instance may
        // clear shared walker / antiban resources during a plugin unload.
        boolean wasActive = runtimeActive;
        boolean wasOwner = ownsRuntime();
        runtimeActive = false;
        restoreInputArbiterGuard();
        if (wasActive || randomEventSolver != null)
        {
            log.info("Stopping KSP Account Builder runtime | reason={}", reason);
        }

        try
        {
            // A dormant old plugin instance must not shut down a freshly
            // started owner's walker or task scripts during hot reload.
            if (script != null && (wasActive || wasOwner))
            {
                script.shutdown();
            }
        }
        catch (Exception ex)
        {
            log.error("Account Builder script shutdown failed | reason={}", reason, ex);
        }
        finally
        {
            if (wasOwner)
            {
                try { resetAntibanState(); }
                catch (Exception ex) { log.warn("Unable to reset antiban during shutdown", ex); }
            }

            if (wasOwner)
            {
                try
                {
                    Rs2Walker.clearWalkingRoute("ksp_account_builder_" + reason);
                    Rs2Walker.clearWalkingRoute("Woodcutting:target-area");
                }
                catch (Exception ex) { log.warn("Unable to clear walker during shutdown", ex); }
            }

            KspRandomEventSolver solver = randomEventSolver;
            randomEventSolver = null;
            if (solver != null)
            {
                try { Microbot.getBlockingEventManager().remove(solver); }
                catch (Exception ex) { log.warn("Unable to remove random event solver", ex); }
            }
            if (overlayManager != null && overlay != null)
            {
                try { overlayManager.remove(overlay); }
                catch (Exception ex) { log.warn("Unable to remove Account Builder overlay", ex); }
                Object registration = UIManager.get(OVERLAY_OWNER_KEY);
                if (registration instanceof Object[] && ((Object[]) registration).length > 0
                        && ((Object[]) registration)[0] == runtimeOwnerToken)
                    UIManager.getDefaults().remove(OVERLAY_OWNER_KEY);
            }
            synchronized (UIManager.getDefaults())
            {
                if (ownsRuntime()) UIManager.getDefaults().remove(RUNTIME_OWNER_KEY);
            }
        }
    }

    void setHudVisible(boolean visible)
    {
        if (overlay != null) overlay.setHudVisible(visible);
    }

    boolean isHudVisible()
    {
        return overlay != null && overlay.isHudVisible();
    }

    void setOverlayDetailsVisible(boolean visible)
    {
        if (overlay != null) overlay.setDetailsVisible(visible);
    }

    void setCompactOverlay(boolean compact)
    {
        if (overlay != null) overlay.setCompactMode(compact);
    }

    boolean isCompactOverlay()
    {
        return overlay != null && overlay.isCompactMode();
    }

    boolean isRuntimeActive()
    {
        return runtimeActive;
    }

    private void addSidePanel()
    {
        SwingUtilities.invokeLater(() ->
        {
            if (!panelEnabled || navButton != null) return;

            // A reloaded plugin has a different classloader/instance, so its
            // own navButton field cannot identify the previous toolbar entry.
            Object registered = UIManager.get(SIDEBAR_REGISTRY_KEY);
            if (registered instanceof Object[])
            {
                Object[] previous = (Object[]) registered;
                if (previous.length >= 2 && previous[0] instanceof NavigationButton)
                {
                    clientToolbar.removeNavigation((NavigationButton) previous[0]);
                }
                if (previous.length >= 2 && previous[1] instanceof Runnable)
                {
                    ((Runnable) previous[1]).run();
                }
                UIManager.getDefaults().remove(SIDEBAR_REGISTRY_KEY);
            }

            sidePanel = new KspAccountBuilderPanel(this, config, script, configManager);
            navButton = NavigationButton.builder()
                    .tooltip("KSP Account Builder")
                    .icon(createPanelIcon())
                    .priority(6)
                    .panel(sidePanel)
                    .build();

            final NavigationButton newButton = navButton;
            final KspAccountBuilderPanel newPanel = sidePanel;
            UIManager.put(SIDEBAR_REGISTRY_KEY, new Object[]{newButton, (Runnable) newPanel::dispose});
            clientToolbar.addNavigation(newButton);
            // A replacement sidebar supersedes its predecessor, including
            // any running script from the previous classloader. Do not wait
            // until the user presses Start to stop an orphaned task scheduler.
            synchronized (UIManager.getDefaults())
            {
                Object existing = UIManager.get(RUNTIME_OWNER_KEY);
                if (existing instanceof Object[] && ((Object[]) existing).length >= 2
                        && ((Object[]) existing)[0] != runtimeOwnerToken
                        && ((Object[]) existing)[1] instanceof Runnable)
                {
                    log.warn("Account Builder sidebar takeover: stopping previous active runtime");
                    try { ((Runnable) ((Object[]) existing)[1]).run(); }
                    catch (Exception ex) { log.error("Unable to stop superseded Account Builder runtime", ex); }
                }
            }
        });
    }

    private void removeSidePanel()
    {
        SwingUtilities.invokeLater(() ->
        {
            if (navButton != null)
            {
                clientToolbar.removeNavigation(navButton);
                Object registered = UIManager.get(SIDEBAR_REGISTRY_KEY);
                if (registered instanceof Object[] && ((Object[]) registered).length > 0
                        && ((Object[]) registered)[0] == navButton)
                {
                    UIManager.getDefaults().remove(SIDEBAR_REGISTRY_KEY);
                }
                navButton = null;
            }
            if (sidePanel != null)
            {
                sidePanel.dispose();
                sidePanel = null;
            }
        });
    }

    private static BufferedImage createPanelIcon()
    {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try
        {
            g.setColor(new Color(116, 197, 255));
            g.fillRoundRect(2, 1, 12, 14, 3, 3);
            g.setColor(new Color(35, 42, 52));
            g.fillRect(5, 4, 6, 2);
            g.fillRect(5, 8, 6, 2);
            g.fillRect(5, 12, 6, 1);
        }
        finally
        {
            g.dispose();
        }
        return image;
    }

    private void migrateSingleSkillTargetDefaults()
    {
        String[] progressiveKeys = {
                "singleSkillMiningTarget",
                "singleSkillWoodcuttingTarget",
                "singleSkillFishingTarget",
                "singleSkillSmithingTarget",
                "singleSkillSmeltingTarget"
        };

        for (String key : progressiveKeys)
        {
            if ("AUTOMATIC".equals(configManager.getConfiguration(KspAccountBuilderConfig.CONFIG_GROUP, key)))
            {
                configManager.setConfiguration(KspAccountBuilderConfig.CONFIG_GROUP, key, "PROGRESSIVE");
            }
        }
    }
}
