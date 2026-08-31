package net.runelite.client.plugins.microbot.kspdirectfishing;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.plugins.microbot.kspmule.KspMuleConfig;

@ConfigGroup("kspDirectFishing")
public interface KspDirectFishingConfig extends Config, KspMuleConfig
{
    @ConfigSection(name="Local Mule",description="Automatic excess-GP transfer to KSP Trade Receiver",position=90)
    String muleSection=KspMuleConfig.SECTION;

    @ConfigItem(keyName="fishingMode",name="Fish",description="Choose Shrimp/Anchovies (small net) or Sardine/Herring (fishing rod + bait).",position=0)
    default KspDirectFishingMode fishingMode(){return KspDirectFishingMode.SHRIMP_ANCHOVIES;}
    @ConfigItem(keyName="fishingLocation",name="Location",description="Choose where the plugin should fish.",position=1)
    default KspDirectFishingLocation fishingLocation(){return KspDirectFishingLocation.DRAYNOR_VILLAGE;}
    @ConfigItem(keyName="dropFish",name="Drop fish",description="Drop caught fish instead of cooking/banking. Lumbridge Swamp always drops fish automatically.",position=2)
    default boolean dropFish(){return false;}
    @Range(min=8,max=50)
    @ConfigItem(keyName="fireSearchRadius",name="Fire search radius",description="Maximum local search radius for a normal Fire or Forester's Campfire.",position=3)
    default int fireSearchRadius(){return 30;}
    @ConfigItem(keyName="waitForFire",name="Wait for fire",description="If no Fire/Forester's Campfire is available, wait instead of banking raw fish.",position=4)
    default boolean waitForFire(){return true;}
    @ConfigItem(keyName="directBankFirst",name="Direct bank interaction",description="If a bank booth is visible in the local scene, click that booth directly. Walking is recovery only.",position=5)
    default boolean directBankFirst(){return true;}
}
