package net.runelite.client.plugins.microbot.geflipper;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(FlipperConfig.GROUP)
public interface FlipperConfig extends Config
{
    String GROUP = "geflipper";

    @ConfigItem(
        keyName = "guide",
        name = "How to use",
        description = "Requirements and operating instructions",
        position = 0
    )
    default String guide()
    {
        return "Install and enable Flipping Copilot, sign in, and resume suggestions. "
            + "Start this plugin with access to the Grand Exchange. The follower reads Copilot's "
            + "active RuneLite overlays and performs the highlighted action through current "
            + "RuneLite and Microbot APIs.";
    }

    @ConfigItem(
        keyName = "prepareInventory",
        name = "Prepare inventory",
        description = "Bank inventory items and withdraw coins before following suggestions",
        position = 1
    )
    default boolean prepareInventory()
    {
        return true;
    }

    @ConfigItem(
        keyName = "autoWalkToGe",
        name = "Walk to Grand Exchange",
        description = "Walk to the Grand Exchange when no clerk or Copilot highlight is available",
        position = 2
    )
    default boolean autoWalkToGe()
    {
        return true;
    }

    @ConfigItem(
        keyName = "minimumActionDelay",
        name = "Minimum action delay",
        description = "Minimum delay between actions in milliseconds",
        position = 3
    )
    default int minimumActionDelay()
    {
        return 350;
    }

    @ConfigItem(
        keyName = "maximumActionDelay",
        name = "Maximum action delay",
        description = "Maximum delay between actions in milliseconds",
        position = 4
    )
    default int maximumActionDelay()
    {
        return 700;
    }

    @ConfigItem(
        keyName = "highlightRetryDelay",
        name = "Highlight retry delay",
        description = "How long an unchanged highlight must remain before it is retried",
        position = 5
    )
    default int highlightRetryDelay()
    {
        return 2500;
    }
}
