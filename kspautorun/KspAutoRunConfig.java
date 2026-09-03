package net.runelite.client.plugins.microbot.kspautorun;



import net.runelite.client.config.ConfigButton;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(KspAutoRunConfig.GROUP)
@ConfigInformation(
        "Automatically enables Run when your run energy reaches the configured threshold. " +
        "The Run orb is activated using a Microbot widget invoke; no natural mouse click is used."
)
public interface KspAutoRunConfig extends Config
{
    String GROUP = "kspAutoRunInvoke";

    @Range(
            min = 1,
            max = 100
    )
    @ConfigItem(
            keyName = "runThreshold",
            name = "Run threshold",
            description = "Enable Run when run energy is at or above this percentage.",
            position = 0
    )
    default int runThreshold()
    {
        return 10;
    }

    @ConfigItem(
            keyName = "kspSupportDiscord",
            name = "Support",
            description = "Open the KSP Plugins support Discord.",
            position = 10_000
    )
    default ConfigButton kspSupportDiscord()
    {
        return new ConfigButton();
    }
}
