package net.runelite.client.plugins.microbot.kspaccountbuilder;

public enum KspQuestTask
{
    COOKS_ASSISTANT("Cook's Assistant"),
    GOBLIN_DIPLOMACY("Goblin Diplomacy"),
    ROMEO_AND_JULIET("Romeo and Juliet"),
    RUNE_MYSTERIES("Rune Mysteries"),
    SHEEP_SHEARER("Sheep Shearer"),
    X_MARKS_THE_SPOT("X Marks the Spot"),
    THE_RESTLESS_GHOST("The Restless Ghost"),
    IMP_CATCHER("Imp Catcher");

    private final String displayName;

    KspQuestTask(String displayName)
    {
        this.displayName = displayName;
    }

    @Override
    public String toString() { return displayName; }
}
