package net.runelite.client.plugins.microbot.kspdirectfishing;

import com.google.inject.Provides;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.AWTException;

@PluginDescriptor(
        name = PluginConstants.KSP + "Fishing",
        description = "Direct fishing, fire/campfire cooking and banking for F2P fish.",
        tags = {"ksp", "fishing", "cooking", "banking", "f2p"},
        authors = {"KSP"},
        version = KspDirectFishingPlugin.VERSION,
        minClientVersion = "1.9.8",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class KspDirectFishingPlugin extends Plugin
{
    public static final String VERSION = "0.0.1";

    @Inject
    private KspDirectFishingConfig config;

    @Inject
    private KspDirectFishingScript script;

    @Inject
    private KspDirectFishingOverlay overlay;

    @Inject
    private OverlayManager overlayManager;

    private long startTime;
    private int startFishingXp;
    private int startCookingXp;

    @Provides
    KspDirectFishingConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(KspDirectFishingConfig.class);
    }

    @Override
    protected void startUp() throws AWTException
    {
        startTime = System.currentTimeMillis();
        startFishingXp = Microbot.getClient().getSkillExperience(Skill.FISHING);
        startCookingXp = Microbot.getClient().getSkillExperience(Skill.COOKING);

        overlayManager.add(overlay);
        script.run(config);
    }

    @Override
    protected void shutDown()
    {
        script.shutdown();
        overlayManager.remove(overlay);
    }

    public int getFishingXpGained()
    {
        return Math.max(0,
                Microbot.getClient().getSkillExperience(Skill.FISHING) - startFishingXp);
    }

    public int getCookingXpGained()
    {
        return Math.max(0,
                Microbot.getClient().getSkillExperience(Skill.COOKING) - startCookingXp);
    }

    public String getFormattedRuntime()
    {
        long elapsed = Math.max(0L, System.currentTimeMillis() - startTime);
        long hours = elapsed / 3_600_000L;
        long minutes = (elapsed % 3_600_000L) / 60_000L;
        long seconds = (elapsed % 60_000L) / 1_000L;

        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
