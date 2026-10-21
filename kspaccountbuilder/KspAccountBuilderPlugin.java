package net.runelite.client.plugins.microbot.kspaccountbuilder;

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
    public static final String VERSION = "1.9.57";

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
    private ClientToolbar clientToolbar;

    // UIManager lives in the host classloader and survives plugin hot reloads.
    private static final String SIDEBAR_REGISTRY_KEY = "ksp.accountbuilder.sidebar.v1";
    // Registry lives in the host Swing classloader, not the plugin classloader.
    // SourceLoader may briefly leave two enabled plugin instances after reload.
    private static final String RUNTIME_OWNER_KEY = "ksp.accountbuilder.runtime.owner.v1";
    private static final String OVERLAY_OWNER_KEY = "ksp.accountbuilder.overlay.owner.v1";
    private final Object runtimeOwnerToken = new Object();
    private KspAccountBuilderPanel sidePanel;
    private NavigationButton navButton;
    private volatile boolean panelEnabled;

    private KspRandomEventSolver randomEventSolver;
    private final KspAccountBuilderSkillingAntiban skillingAntiban = new KspAccountBuilderSkillingAntiban();
    private volatile boolean runtimeActive;
    private volatile boolean acceptingRuntimeChanges;
    private String antibanStateKey;

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
        addSidePanel();
        acceptingRuntimeChanges = true;
        log.info("KSP Account Builder loaded stopped; press Start in sidebar to begin automation");
    }

    @Subscribe
    public synchronized void onConfigChanged(ConfigChanged event)
    {
        if (!acceptingRuntimeChanges
                || !KspAccountBuilderConfig.CONFIG_GROUP.equals(event.getGroup())
                || !"runAccountBuilder".equals(event.getKey()))
        {
            return;
        }
        if (config.runAccountBuilder())
        {
            if (isCurrentSidebarOwner()) startRuntime();
            else log.debug("Ignoring Start event from superseded Account Builder sidebar instance");
        }
        else cleanupRuntime("config-stop");
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
        cleanupRuntime("plugin-stop");
        if (wasSidebarOwner)
            configManager.setConfiguration(KspAccountBuilderConfig.CONFIG_GROUP, "runAccountBuilder", false);
        removeSidePanel();
    }

    public synchronized void prepareHotUnload()
    {
        boolean wasSidebarOwner = isCurrentSidebarOwner();
        acceptingRuntimeChanges = false;
        panelEnabled = false;
        cleanupRuntime("hot-unload");
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
        if (!runtimeActive || !ownsRuntime() || script == null || config == null)
        {
            return;
        }

        String taskName = script.getCurrentTaskName();
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
                    KspWalkerGuard.clearActiveWalker("ksp_account_builder_" + reason);
                    KspWalkerGuard.clear("Woodcutting:target-area");
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
