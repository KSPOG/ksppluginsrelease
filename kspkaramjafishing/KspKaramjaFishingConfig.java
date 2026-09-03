package net.runelite.client.plugins.microbot.kspkaramjafishing;



import net.runelite.client.config.ConfigButton;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.kspmule.KspMuleConfig;

@ConfigGroup("kspKaramjaFishing")
public interface KspKaramjaFishingConfig extends Config, KspMuleConfig
{
    @ConfigSection(name = "Local Mule", description = "Automatic excess-GP transfer to KSP Trade Receiver", position = 90)
    String muleSection = KspMuleConfig.SECTION;

    enum Mode
    {
        TUNA_SWORDFISH("Tuna & Swordfish", "Harpoon"),
        LOBSTER("Lobster", "Cage");
        private final String name;
        private final String action;
        Mode(String name, String action) { this.name = name; this.action = action; }
        public String getName() { return name; }
        public String getAction() { return action; }
        @Override public String toString() { return name; }
    }

    @ConfigItem(keyName = "mode", name = "Fish", description = "Tuna & Swordfish uses a harpoon. Lobster uses a lobster pot.", position = 0)
    default Mode mode() { return Mode.TUNA_SWORDFISH; }

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
