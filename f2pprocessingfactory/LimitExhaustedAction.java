package net.runelite.client.plugins.microbot.f2pprocessingfactory;

public enum LimitExhaustedAction
{
    SWITCH_RECIPE("Switch to another profitable recipe"),
    WAIT_FOR_RESET("Wait for the tracked limit to reset"),
    STOP_PLUGIN("Stop the plugin");

    private final String displayName;

    LimitExhaustedAction(String displayName)
    {
        this.displayName = displayName;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
