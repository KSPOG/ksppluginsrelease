package net.runelite.client.plugins.microbot.f2pprocessingfactory;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum FactoryMode
{
    AUTO_BEST_PROFIT("Automatic - best profitable recipe"),
    FIXED_RECIPE("Fixed recipe");

    private final String displayName;

    @Override
    public String toString()
    {
        return displayName;
    }
}
