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

@PluginDescriptor(
        name = PluginConstants.KSP + "Fishing",
        description = "Direct F2P fishing in Draynor or Lumbridge Swamp with drop, cooking and banking modes.",
        tags = {"ksp", "fishing", "cooking", "banking", "drop", "f2p"},
        authors = {"KSP"},
        version = KspDirectFishingPlugin.VERSION,
        minClientVersion = "1.9.8",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class KspDirectFishingPlugin extends Plugin {
    public static final String VERSION = "0.1.6";
    @Inject private KspDirectFishingConfig config;
    @Inject private KspDirectFishingScript script;
    @Inject private KspDirectFishingOverlay overlay;
    @Inject private OverlayManager overlayManager;
    private long startTime; private int startFishingXp,startCookingXp;
    @Provides KspDirectFishingConfig provideConfig(ConfigManager configManager){return configManager.getConfig(KspDirectFishingConfig.class);}
    @Override protected void startUp(){startTime=System.currentTimeMillis();startFishingXp=Microbot.getClient().getSkillExperience(Skill.FISHING);startCookingXp=Microbot.getClient().getSkillExperience(Skill.COOKING);overlayManager.add(overlay);script.run(config);}
    @Override protected void shutDown(){script.shutdown();overlayManager.remove(overlay);}
    public KspDirectFishingScript getScript(){return script;}
    public int getFishingXpGained(){return Math.max(0,Microbot.getClient().getSkillExperience(Skill.FISHING)-startFishingXp);}
    public int getCookingXpGained(){return Math.max(0,Microbot.getClient().getSkillExperience(Skill.COOKING)-startCookingXp);}
    public int getFishingXpPerHour(){return perHour(getFishingXpGained());} public int getCookingXpPerHour(){return perHour(getCookingXpGained());}
    private int perHour(int gained){long elapsed=Math.max(1L,System.currentTimeMillis()-startTime);return(int)Math.round(gained*3_600_000.0/elapsed);}
    public String getFormattedRuntime(){long elapsed=Math.max(0L,System.currentTimeMillis()-startTime),hours=elapsed/3_600_000L,minutes=(elapsed%3_600_000L)/60_000L,seconds=(elapsed%60_000L)/1_000L;return String.format("%02d:%02d:%02d",hours,minutes,seconds);}
}
