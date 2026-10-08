package net.runelite.client.plugins.microbot.kspsupport;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.GameState;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.LoginManager;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A plugin-owned timer that continues running while action scripts are paused/offline. */
public final class KspBreakService
{
    private static final AtomicInteger ACTIVE_BREAKS = new AtomicInteger();
    private static final Logger log = LoggerFactory.getLogger(KspBreakService.class);
    private static final String AUTO_LOGIN = "net.runelite.client.plugins.microbot.accountselector.AutoLoginPlugin";
    private static final String BREAK_HANDLER = "net.runelite.client.plugins.microbot.breakhandler.BreakHandlerPlugin";
    private static final long COMBAT_GRACE_MS = 11_000;
    private volatile ScheduledExecutorService timer;
    private volatile KspBreakConfig config;
    private volatile long generation;
    private long nextBreakAt, lastTick, lastCombatAt, breakEndsAt, duration, lastActionAt;
    private int world;
    private volatile boolean pauseOwned;
    private boolean loggedOut, enabled;

    public synchronized void start(KspBreakConfig settings)
    {
        shutdown();
        config = settings;
        enabled = settings.doBreaks();
        lastTick = System.currentTimeMillis();
        lastCombatAt = 0;
        if (enabled)
        {
            stopPlugin(BREAK_HANDLER);
            scheduleNext(lastTick);
        }
        timer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "KSP break timer");
            thread.setDaemon(true);
            return thread;
        });
        timer.scheduleWithFixedDelay(this::tickSafely, 500, 500, TimeUnit.MILLISECONDS);
    }

    private void tickSafely()
    {
        if (timer == null) return;
        try { tick(System.currentTimeMillis()); }
        catch (Exception ex) { log.warn("KSP break timer failed; will retry", ex); }
    }

    private void tick(long now)
    {
        KspBreakConfig current = config;
        long run = generation;
        if (current == null) return;
        long elapsed = Math.max(0, now - lastTick);
        lastTick = now;
        if (current.doBreaks() != enabled)
        {
            enabled = current.doBreaks();
            if (enabled) { stopPlugin(BREAK_HANDLER); scheduleNext(now); }
            else if (pauseOwned) breakEndsAt = now; // Finish login before releasing our pause.
        }
        if (Microbot.isLoggedIn() && Rs2Combat.inCombat()) lastCombatAt = now;
        if (!pauseOwned)
        {
            if (!enabled) return;
            if (!Microbot.isLoggedIn() || shouldPause())
            {
                nextBreakAt += elapsed;
                return;
            }
            if (now < nextBreakAt
                    || Rs2Widget.isWidgetVisible(InterfaceID.Trademain.ACCEPT)
                    || Rs2Widget.isWidgetVisible(InterfaceID.Tradeconfirm.TRADE2ACCEPT)) return;
            synchronized (this)
            {
                if (generation != run || config != current || !Microbot.pauseAllScripts.compareAndSet(false, true)) return;
                pauseOwned = true;
                ACTIVE_BREAKS.incrementAndGet();
            }
            world = Microbot.getClient().getWorld();
            duration = randomMinutes(current.breakDurationMinMinutes(), current.breakDurationMaxMinutes(), 1, 180);
            breakEndsAt = 0;
            loggedOut = false;
            lastActionAt = 0;
            if (generation != run) return;
            Rs2Walker.setTarget(null);
        }
        if (generation != run || !pauseOwned) return;
        // Account Builder likewise disables standalone auto-login to keep a break offline.
        stopPlugin(AUTO_LOGIN);
        if (!loggedOut)
        {
            if (!enabled && Microbot.isLoggedIn()) { finish(now, run); return; }
            if (Microbot.isLoggedIn())
            {
                Microbot.status = "Break: waiting to log out safely";
                if (Rs2Combat.inCombat() || now - lastCombatAt < COMBAT_GRACE_MS) return;
                if (now - lastActionAt >= 3_000) { lastActionAt = now; if (generation == run) Rs2Player.logout(); }
                return;
            }
            // Count the full break only after logout was observed.
            loggedOut = true;
            breakEndsAt = enabled ? now + duration : now;
            lastActionAt = 0;
        }
        if (enabled && now < breakEndsAt)
        {
            Microbot.status = "Break: " + ((breakEndsAt - now + 999) / 1000) + " seconds remaining";
            // A manually enabled login helper must not shorten the scheduled break.
            if (Microbot.isLoggedIn() && now - lastActionAt >= 3_000
                    && !Rs2Combat.inCombat() && now - lastCombatAt >= COMBAT_GRACE_MS)
            { lastActionAt = now; if (generation == run) Rs2Player.logout(); }
            return;
        }
        Microbot.status = "Break finished: waiting to log in";
        if (Rs2Widget.isWidgetVisible(InterfaceID.WelcomeScreen.PLAY))
        {
            if (now - lastActionAt >= 3_000)
            { lastActionAt = now; if (generation == run) Rs2Widget.clickWidget(InterfaceID.WelcomeScreen.PLAY); }
            return;
        }
        if (Microbot.isLoggedIn() && Microbot.getClient().getGameState() == GameState.LOGGED_IN
                && Microbot.getClient().getLocalPlayer() != null)
        { finish(now, run); return; }
        if (Microbot.getClient().getGameState() != GameState.LOGIN_SCREEN || now - lastActionAt < 10_000) return;
        int loginIndex = Microbot.getClient().getLoginIndex();
        if (loginIndex == 4 || loginIndex == 14 || loginIndex == 34) return;
        if (LoginManager.getActiveProfile() == null || LoginManager.isLoginAttemptActive()) return;
        lastActionAt = now;
        if (generation == run) LoginManager.login(world); // Keep the current F2P/members world rather than selecting F2P universally.
    }

    private void scheduleNext(long now)
    {
        KspBreakConfig current = config;
        if (current != null) nextBreakAt = now + randomMinutes(current.breakAfterMinMinutes(), current.breakAfterMaxMinutes(), 5, 300);
    }

    static long randomMinutes(int first, int second, int minimum, int maximum)
    {
        int low = Math.max(minimum, Math.min(maximum, Math.min(first, second)));
        int high = Math.max(low, Math.max(minimum, Math.min(maximum, Math.max(first, second))));
        return TimeUnit.MINUTES.toMillis(ThreadLocalRandom.current().nextInt(low, high + 1));
    }

    private synchronized void finish(long now, long run)
    {
        if (generation != run) return;
        releasePause();
        loggedOut = false;
        scheduleNext(now);
        Microbot.status = "Break finished";
    }

    private synchronized void releasePause()
    {
        if (pauseOwned)
        {
            Microbot.pauseAllScripts.compareAndSet(true, false);
            pauseOwned = false;
            ACTIVE_BREAKS.decrementAndGet();
        }
    }

    public synchronized void shutdown()
    {
        generation++;
        config = null;
        if (timer != null) { timer.shutdownNow(); timer = null; }
        releasePause();
    }

    public static boolean awaitResume()
    {
        while (shouldPause())
        {
            if (Thread.currentThread().isInterrupted()) return false;
            try { Thread.sleep(250); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); return false; }
        }
        return !Thread.currentThread().isInterrupted();
    }

    public static boolean isBreakActive() { return ACTIVE_BREAKS.get() > 0; }

    public static boolean shouldPause()
    {
        return isBreakActive() || Microbot.pauseAllScripts.get()
                || BreakHandlerScript.isBreakActive() || BreakHandlerScript.isMicroBreakActive();
    }

    private static void stopPlugin(String className)
    {
        Plugin plugin = Microbot.getPlugin(className);
        if (plugin != null && Microbot.isPluginEnabled(plugin)) Microbot.stopPlugin(plugin);
    }
}
