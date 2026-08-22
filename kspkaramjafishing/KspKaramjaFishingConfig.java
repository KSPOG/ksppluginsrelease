package net.runelite.client.plugins.microbot.kspkaramjafishing;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("kspKaramjaFishing")
public interface KspKaramjaFishingConfig extends Config
{
    @Getter
    @RequiredArgsConstructor
    enum Mode
    {
        TUNA_SWORDFISH("Tuna & Swordfish", "Harpoon"),
        LOBSTER("Lobster", "Cage");

        private final String name;
        private final String action;

        @Override
        public String toString()
        {
            return name;
        }
    }

    @ConfigItem(
            keyName = "mode",
            name = "Fish",
            description = "Tuna & Swordfish uses a harpoon. Lobster uses a lobster pot.",
            position = 0
    )
    default Mode mode()
    {
        return Mode.TUNA_SWORDFISH;
    }
}
