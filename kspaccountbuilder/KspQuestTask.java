package net.runelite.client.plugins.microbot.kspaccountbuilder;

public enum KspQuestTask
{
    COOKS_ASSISTANT("Cook's Assistant"),
    GOBLIN_DIPLOMACY("Goblin Diplomacy"),
    ROMEO_AND_JULIET("Romeo and Juliet"),
    RUNE_MYSTERIES("Rune Mysteries");

    private final String displayName;

    KspQuestTask(String displayName)
    {
        this.displayName = displayName;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
