package net.runelite.client.plugins.microbot.kspaccountbuilder.util.autologin;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.util.security.LoginManager;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import javax.inject.Singleton;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Singleton
@Slf4j
public class AutoLoginScript extends Script
{
    private static final int LOOP_DELAY_MS = 600;
    private static final int WELCOME_PLAY_CLICK_COOLDOWN_MS = 2_500;
    private static final int LOGIN_ATTEMPT_COOLDOWN_MS = 10_000;
    private static final int POST_LOGIN_SETTLE_MS = 4_000;
    private static final BooleanSupplier ALWAYS_ALLOWED = () -> true;

    @Getter
    private LoginState state = LoginState.IDLE;

    private volatile boolean active;
    private boolean debugLogging;
    private long lastWelcomePlayClickAtMillis;
    private long lastLoginAttemptAtMillis;
    private long lastLoginScreenDebugAtMillis;
    private long loggedInAtMillis;

    public void setDebugLogging(boolean debugLogging)
    {
        this.debugLogging = debugLogging;
    }

    public boolean run()
    {
        return run(ALWAYS_ALLOWED);
    }

    public boolean run(BooleanSupplier kspLoginAllowed)
    {
        shutdown();
        active = true;
        state = LoginState.IDLE;
        lastWelcomePlayClickAtMillis = 0L;
        lastLoginAttemptAtMillis = 0L;
        lastLoginScreenDebugAtMillis = 0L;
        loggedInAtMillis = 0L;

        BooleanSupplier loginAllowed = kspLoginAllowed == null ? ALWAYS_ALLOWED : kspLoginAllowed;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            try
            {
                if (!active || Thread.currentThread().isInterrupted())
                {
                    return;
                }

                tick(loginAllowed);
            }
            catch (Exception ex)
            {
                transitionTo(LoginState.ERROR);
                log.error("[KSP AutoLogin] Error while handling login screen", ex);
            }
        }, 0, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);

        log.info("[KSP AutoLogin] helper started");
        return true;
    }

    private void tick(BooleanSupplier kspLoginAllowed)
    {
        if (!active)
        {
            return;
        }

        Client client = Microbot.getClient();
        if (client == null)
        {
            transitionTo(LoginState.NO_CLIENT);
            return;
        }

        if (!kspLoginAllowed.getAsBoolean())
        {
            transitionTo(LoginState.PAUSED_FOR_KSP_BREAK);
            return;
        }

        if (isMicrobotBreakHandlerActive())
        {
            transitionTo(LoginState.PAUSED_FOR_BREAK_HANDLER);
            return;
        }

        if (isLoginScreenReadyForLoginManager(client))
        {
            handleLoginScreenWithLoginManager(client);
            return;
        }

        if (isPlayButtonVisible())
        {
            if (System.currentTimeMillis() - lastWelcomePlayClickAtMillis < WELCOME_PLAY_CLICK_COOLDOWN_MS)
            {
                transitionTo(LoginState.WAITING_FOR_PLAY_BUTTON);
                return;
            }

            transitionTo(LoginState.CLICKING_PLAY);
            boolean clicked = clickPlayButton();
            if (clicked)
            {
                lastWelcomePlayClickAtMillis = System.currentTimeMillis();
                sleepUntil(() -> !active || !isPlayButtonVisible(), WELCOME_PLAY_CLICK_COOLDOWN_MS);
            }
            return;
        }

        if (Microbot.isLoggedIn())
        {
            handleLoggedInState();
            return;
        }

        GameState gameState = client.getGameState();
        if (gameState == GameState.LOGIN_SCREEN || gameState == GameState.LOGIN_SCREEN_AUTHENTICATOR || gameState == GameState.LOGGING_IN)
        {
            debugLoginScreenState(client, "waiting");
            transitionTo(LoginState.WAITING_FOR_LOGIN);
            return;
        }

        transitionTo(LoginState.IDLE);
    }

    private void handleLoggedInState()
    {
        if (loggedInAtMillis == 0L)
        {
            loggedInAtMillis = System.currentTimeMillis();
            transitionTo(LoginState.LOGGED_IN);
            debug("logged in detected; waiting for welcome screen to settle before stopping helper");
            return;
        }

        if (System.currentTimeMillis() - loggedInAtMillis < POST_LOGIN_SETTLE_MS)
        {
            transitionTo(LoginState.LOGGED_IN);
            return;
        }

        if (!isPlayButtonVisible())
        {
            stopAfterLoginComplete();
        }
    }

    private void stopAfterLoginComplete()
    {
        boolean wasActive = active || isRunning();
        active = false;
        state = LoginState.LOGGED_IN;
        lastWelcomePlayClickAtMillis = 0L;
        lastLoginAttemptAtMillis = 0L;
        lastLoginScreenDebugAtMillis = 0L;
        loggedInAtMillis = 0L;

        if (mainScheduledFuture != null && !mainScheduledFuture.isDone())
        {
            mainScheduledFuture.cancel(false);
        }

        if (scheduledFuture != null && !scheduledFuture.isDone())
        {
            scheduledFuture.cancel(false);
        }

        if (wasActive)
        {
            log.info("[KSP AutoLogin] login complete; helper stopped");
        }
    }

    public boolean isActive()
    {
        return active || isRunning();
    }

    private void handleLoginScreenWithLoginManager(Client client)
    {
        if (!active)
        {
            return;
        }

        if (System.currentTimeMillis() - lastLoginAttemptAtMillis < LOGIN_ATTEMPT_COOLDOWN_MS)
        {
            transitionTo(LoginState.WAITING_FOR_LOGIN_PLAY_NOW);
            return;
        }

        transitionTo(LoginState.CLICKING_LOGIN_PLAY_NOW);
        lastLoginAttemptAtMillis = System.currentTimeMillis();
        int targetWorld = resolveF2pLoginWorld();
        debug("triggering LoginManager login | loginIndex={} gameState={} activeProfile={} loginAttemptActive={} targetWorld={}",
                client.getLoginIndex(),
                client.getGameState(),
                LoginManager.getActiveProfile() != null,
                LoginManager.isLoginAttemptActive(),
                targetWorld);

        if (!active || isMicrobotBreakHandlerActive())
        {
            return;
        }

        boolean loginStarted = LoginManager.login(targetWorld);
        debug("LoginManager login result={} | loginIndex={} gameState={} loginAttemptActive={} targetWorld={}",
                loginStarted,
                client.getLoginIndex(),
                client.getGameState(),
                LoginManager.isLoginAttemptActive(),
                targetWorld);

        if (loginStarted)
        {
            sleepUntil(() -> !active || Microbot.isLoggedIn() || !isLoginScreenReadyForLoginManager(Microbot.getClient()), WELCOME_PLAY_CLICK_COOLDOWN_MS);
        }
    }

    private int resolveF2pLoginWorld()
    {
        try
        {
            int world = LoginManager.getRandomWorld(false);
            return world > 0 ? world : 383;
        }
        catch (Exception ex)
        {
            debug("failed to resolve F2P login world: {}", ex.getMessage());
            return 383;
        }
    }

    private boolean isLoginScreenReadyForLoginManager(Client client)
    {
        return client != null
                && client.getGameState() == GameState.LOGIN_SCREEN
                && !isFatalLoginIndex(client.getLoginIndex());
    }

    private boolean isFatalLoginIndex(int loginIndex)
    {
        return loginIndex == 4
                || loginIndex == 14
                || loginIndex == 34;
    }

    private boolean clickPlayButton()
    {
        if (!active || Microbot.getClientThread() == null)
        {
            return false;
        }

        Boolean clicked = Microbot.getClientThread().invoke((Supplier<Boolean>) () ->
        {
            if (!active)
            {
                return false;
            }

            Client client = Microbot.getClient();
            if (client == null)
            {
                return false;
            }

            Widget updateBottomRibbon = client.getWidget(InterfaceID.WelcomeScreen.URL);
            if (updateBottomRibbon != null)
            {
                updateBottomRibbon.setOnClickListener((Object[]) null);
                updateBottomRibbon.setOnOpListener((Object[]) null);
            }

            Widget newsBanner = client.getWidget(InterfaceID.WelcomeScreen.BANNER);
            if (newsBanner != null)
            {
                newsBanner.setHidden(true);
            }

            Widget playWidget = client.getWidget(InterfaceID.WelcomeScreen.PLAY);
            if (playWidget == null || playWidget.isHidden())
            {
                return false;
            }

            Rs2Widget.clickWidget(playWidget);
            return true;
        });

        debug("play button click result={}", clicked);
        return Boolean.TRUE.equals(clicked);
    }

    private boolean isPlayButtonVisible()
    {
        try
        {
            return Rs2Widget.isWidgetVisible(InterfaceID.WelcomeScreen.PLAY);
        }
        catch (Exception ex)
        {
            debug("play button visibility check failed: {}", ex.getMessage());
            return false;
        }
    }

    private void debugLoginScreenState(Client client, String reason)
    {
        if (!debugLogging || client == null)
        {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastLoginScreenDebugAtMillis < 3_000)
        {
            return;
        }

        lastLoginScreenDebugAtMillis = now;
        log.info("[KSP AutoLogin] login screen {} | loginIndex={} gameState={} canvas={}x{} welcomePlayVisible={}",
                reason,
                client.getLoginIndex(),
                client.getGameState(),
                client.getCanvasWidth(),
                client.getCanvasHeight(),
                isPlayButtonVisible());
    }

    private boolean isMicrobotBreakHandlerActive()
    {
        try
        {
            return BreakHandlerScript.isBreakActive() || BreakHandlerScript.isMicroBreakActive();
        }
        catch (Exception ex)
        {
            debug("break handler state check failed: {}", ex.getMessage());
            return false;
        }
    }

    private void transitionTo(LoginState nextState)
    {
        if (state == nextState)
        {
            return;
        }

        debug("state {} -> {}", state, nextState);
        state = nextState;
    }

    private void debug(String message, Object... args)
    {
        if (debugLogging)
        {
            log.info("[KSP AutoLogin] " + message, args);
        }
    }

    @Override
    public void shutdown()
    {
        boolean wasActive = active || isRunning();
        active = false;
        state = LoginState.IDLE;
        lastWelcomePlayClickAtMillis = 0L;
        lastLoginAttemptAtMillis = 0L;
        lastLoginScreenDebugAtMillis = 0L;
        loggedInAtMillis = 0L;
        super.shutdown();
        if (wasActive)
        {
            log.info("[KSP AutoLogin] helper stopped");
        }
    }
}
