package net.runelite.client.plugins.microbot.ksprenderdisable;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigInformation(
    "Reduces RuneLite/Microbot rendering work. "
        + "Freeze renderer output is intended for unattended clients. "
        + "Aggressive scene filtering can remove clickboxes, so it is disabled by default."
)
@ConfigGroup(KspDisableRenderConfig.GROUP)
public interface KspDisableRenderConfig extends Config
{
    String GROUP = "kspDisableRender";

    @ConfigSection(
        name = "Rendering",
        description = "Main rendering controls",
        position = 0
    )
    String renderingSection = "renderingSection";

    @ConfigSection(
        name = "Aggressive filtering",
        description = "Optional filters. These can affect clickboxes and manual interaction.",
        position = 1,
        closedByDefault = true
    )
    String aggressiveSection = "aggressiveSection";

    @ConfigItem(
        keyName = "renderMode",
        name = "Render mode",
        description = "Freeze renderer output gives the largest saving when GPU/117HD has installed DrawCallbacks. "
            + "Reduce rendering keeps frames visible but lowers scene work.",
        section = renderingSection,
        position = 0
    )
    default RenderMode renderMode()
    {
        return RenderMode.FREEZE_OUTPUT;
    }

    @ConfigItem(
        keyName = "disable2DExtras",
        name = "Disable 2D extras",
        description = "Disables non-widget 2D scene extras such as overhead scene drawing.",
        section = renderingSection,
        position = 1
    )
    default boolean disable2DExtras()
    {
        return true;
    }

    @Range(
        min = 0,
        max = 25
    )
    @ConfigItem(
        keyName = "drawDistance",
        name = "Scene draw distance",
        description = "Temporarily lowers the scene draw distance while the plugin is enabled. "
            + "0 gives the lowest rendering workload.",
        section = renderingSection,
        position = 2
    )
    default int drawDistance()
    {
        return 0;
    }

    @ConfigItem(
        keyName = "filterEntities",
        name = "Filter entities",
        description = "Aggressive: prevents players/NPCs/projectiles from being added for rendering. "
            + "This can remove clickboxes and may interfere with scripts that need them.",
        section = aggressiveSection,
        position = 0
    )
    default boolean filterEntities()
    {
        return false;
    }

    @ConfigItem(
        keyName = "filterTileObjects",
        name = "Filter tile objects",
        description = "Aggressive: prevents tile objects from being rendered. "
            + "Leave disabled if scripts rely on rendered object geometry/clickboxes.",
        section = aggressiveSection,
        position = 1
    )
    default boolean filterTileObjects()
    {
        return false;
    }

    @ConfigItem(
        keyName = "filterSceneTiles",
        name = "Filter scene tiles",
        description = "Very aggressive: skips scene tiles during scene upload. "
            + "The scene may need to reload before all graphics return after changing this setting.",
        section = aggressiveSection,
        position = 2
    )
    default boolean filterSceneTiles()
    {
        return false;
    }
}
