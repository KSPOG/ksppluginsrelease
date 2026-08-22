package net.runelite.client.plugins.microbot.kspkaramjafishing;

import com.google.inject.Provides;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
        name = PluginConstants.KSP + "Karamja Fishing",
        description = "Tuna/Swordfish or Lobster fishing at Musa Point with ship travel and Port Sarim deposit-box banking.",
        tags = {"ksp", "fishing", "tuna", "swordfish", "lobster", "karamja", "f2p"},
        authors = {"KSP"},
        version = KspKaramjaFishingPlugin.VERSION,
        minClientVersion = "1.9.8",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class KspKaramjaFishingPlugin extends Plugin
{
    public static final String VERSION = "0.1.1";

    @Inject private KspKaramjaFishingConfig config;
    @Inject private KspKaramjaFishingScript script;
    @Inject private KspKaramjaFishingOverlay overlay;
    @Inject private OverlayManager overlayManager;

    private long started;
    private int startXp;

    @Provides
    KspKaramjaFishingConfig provideConfig(ConfigManager manager)
    {
        return manager.getConfig(KspKaramjaFishingConfig.class);
    }

    @Override
    protected void startUp()
    {
        started = System.currentTimeMillis();
        startXp = Microbot.getClient().getSkillExperience(Skill.FISHING);
        overlayManager.add(overlay);
        script.run(config);
    }

    @Override
    protected void shutDown()
    {
        script.shutdown();
        overlayManager.remove(overlay);
    }

    public KspKaramjaFishingScript getScript() { return script; }

    public int xp()
    {
        return Math.max(0, Microbot.getClient().getSkillExperience(Skill.FISHING) - startXp);
    }

    public int xpHour()
    {
        return (int) Math.round(xp() * 3_600_000.0 / Math.max(1, System.currentTimeMillis() - started));
    }

    public String runtime()
    {
        long s = Math.max(0, System.currentTimeMillis() - started) / 1000;
        return String.format("%02d:%02d:%02d", s / 3600, s / 60 % 60, s % 60);
    }
}
