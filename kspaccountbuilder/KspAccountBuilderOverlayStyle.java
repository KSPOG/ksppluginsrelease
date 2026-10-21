package net.runelite.client.plugins.microbot.kspaccountbuilder;

/** Available in-game overlay layouts. Original remains the saved-setting default. */
public enum KspAccountBuilderOverlayStyle
{
    DEFAULT("Default (Original)"),
    ACCOUNT_DASHBOARD("Modern - Account Dashboard"),
    TASK_FOCUS("Compact - Task Focus"),
    ACCOUNT_ROADMAP("Themed - Account Roadmap"),
    WORKFLOW_HUD("Horizontal - Workflow HUD");

    private final String label;

    KspAccountBuilderOverlayStyle(String label)
    {
        this.label = label;
    }

    @Override
    public String toString()
    {
        return label;
    }
}
