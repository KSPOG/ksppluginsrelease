package net.runelite.client.plugins.microbot.kspwillowchopper;

public enum KspForestryEvent {
    NONE("None"),RISING_ROOTS("Rising Roots"),STRUGGLING_SAPLING("Struggling Sapling"),FRIENDLY_ENTLINGS("Friendly Entlings"),BEEHIVE("Beehive"),PHEASANT("Pheasant Control"),POACHERS("Poachers / Fox"),ENCHANTMENT_RITUAL("Enchantment Ritual"),LEPRECHAUN("Woodcutting Leprechaun"),FLOWERING_TREE("Flowering Tree");
    private final String label;
    KspForestryEvent(String label){this.label=label;}
    @Override public String toString(){return label;}
}
