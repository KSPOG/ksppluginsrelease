package net.runelite.client.plugins.microbot.kspfleshcrawlers;

import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.time.Duration;
import java.time.Instant;

@PluginDescriptor(
        name = PluginConstants.KSP + "Flesh Crawlers",
        description = "Fights Flesh Crawlers in the Stronghold of Security with melee goal balancing, looting, bone burying and supply handling.",
        tags = {"ksp", "microbot", "combat", "flesh crawler", "stronghold of security", "training"},
        authors = {"KSP"},
        version = KspFleshCrawlerPlugin.VERSION,
        minClientVersion = "1.9.8",
        isExternal = PluginConstants.IS_EXTERNAL,
        enabledByDefault = false
)
public class KspFleshCrawlerPlugin extends Plugin {
    public static final String VERSION = "1.0.1";

    @Inject
    private KspFleshCrawlerConfig config;

    @Inject
    private KspFleshCrawlerScript script;

    @Inject
    private KspFleshCrawlerOverlay overlay;

    @Inject
    private OverlayManager overlayManager;

    private Instant startedAt;
    private long startXp;

    @Provides
    KspFleshCrawlerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(KspFleshCrawlerConfig.class);
    }

    @Override
    protected void startUp() {
        startedAt = Instant.now();
        startXp = Microbot.isLoggedIn() ? Microbot.getClient().getOverallExperience() : 0L;
        overlayManager.add(overlay);
        script.run(config);
    }

    @Override
    protected void shutDown() {
        script.shutdown();
        overlayManager.remove(overlay);
        startedAt = null;
        startXp = 0L;
    }

    String getRuntimeText() {
        if (startedAt == null) {
            return "00:00:00";
        }
        long seconds = Duration.between(startedAt, Instant.now()).getSeconds();
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }

    long getXpGained() {
        if (!Microbot.isLoggedIn() || startXp <= 0L) {
            return 0L;
        }
        return Math.max(0L, Microbot.getClient().getOverallExperience() - startXp);
    }

    long getXpPerHour() {
        long seconds = getRuntimeSeconds();
        return seconds <= 0 ? 0L : (getXpGained() * 3600L) / seconds;
    }

    long getKillsPerHour() {
        long seconds = getRuntimeSeconds();
        return seconds <= 0 ? 0L : (script.getKills() * 3600L) / seconds;
    }

    private long getRuntimeSeconds() {
        if (startedAt == null) {
            return 0L;
        }
        return Math.max(0L, Duration.between(startedAt, Instant.now()).getSeconds());
    }
}
