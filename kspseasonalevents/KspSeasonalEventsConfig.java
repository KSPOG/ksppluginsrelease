package net.runelite.client.plugins.microbot.kspseasonalevents;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("KspSeasonalEvents")
public interface KspSeasonalEventsConfig extends Config {
    @ConfigItem(keyName="event", name="Seasonal event",
            description="Select the independently implemented seasonal event", position=0)
    default KspSeasonalEvent event() { return KspSeasonalEvent.HALLOWEEN_2026; }

    @ConfigItem(keyName="walkToEvent", name="Travel to event",
            description="Use Microbot WebWalker only to reach Rimmington before starting Halloween 2026", position=1)
    default boolean walkToEvent() { return true; }

    @ConfigItem(keyName="showOverlay", name="Show status overlay",
            description="Only shown after the side panel Start button is pressed", position=2)
    default boolean showOverlay() { return true; }
}
