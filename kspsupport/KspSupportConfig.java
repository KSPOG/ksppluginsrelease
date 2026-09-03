package net.runelite.client.plugins.microbot.kspsupport;

import net.runelite.client.config.ConfigButton;
import net.runelite.client.config.ConfigItem;

/**
 * Shared configuration mixin that exposes the same support button on every KSP plugin.
 */
public interface KspSupportConfig
{
    String SUPPORT_KEY = "kspSupportDiscord";
    String SUPPORT_URL = "https://discord.gg/mTBVf5FKB2";

    @ConfigItem(
            keyName = SUPPORT_KEY,
            name = "Support",
            description = "Open the KSP Plugins support Discord.",
            position = 10_000
    )
    default ConfigButton kspSupportDiscord()
    {
        return new ConfigButton();
    }
}
