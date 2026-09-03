package net.runelite.client.plugins.microbot.kspbankorganizer;


import net.runelite.client.plugins.microbot.kspsupport.KspSupportConfig;
import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(KspBankOrganizerConfig.GROUP)
public interface KspBankOrganizerConfig extends Config, KspSupportConfig
{
    String GROUP = "kspbankorganizer";

    @ConfigSection(
        name = "Execution",
        description = "How the organizer runs",
        position = -3
    )
    String executionSection = "execution";

    @ConfigSection(
        name = "Tab mappings",
        description = "Choose the destination for each category",
        position = -2
    )
    String tabMappingSection = "tabMappings";

    @ConfigSection(
        name = "Sorting",
        description = "Smart sorting inside category tabs",
        position = -1
    )
    String sortingSection = "sorting";

    @ConfigSection(
        name = "Classification",
        description = "Custom categorization rules",
        position = 0,
        closedByDefault = true
    )
    String classificationSection = "classification";

    @ConfigSection(
        name = "Overlay",
        description = "Bank item/category overlay",
        position = 1,
        closedByDefault = true
    )
    String overlaySection = "overlay";

    @ConfigItem(
        keyName = "operationMode",
        name = "Mode",
        description = "Preview the plan without moving items, or organize the bank immediately.",
        position = 0,
        section = executionSection
    )
    default OperationMode operationMode()
    {
        return OperationMode.PREVIEW;
    }

    @ConfigItem(
        keyName = "createMissingTabs",
        name = "Create missing tabs",
        description = "Create a destination tab when it is the next appendable real bank tab.",
        position = 1,
        section = executionSection
    )
    default boolean createMissingTabs()
    {
        return true;
    }

    @ConfigItem(
        keyName = "sortWithinTabs",
        name = "Smart sort tabs",
        description = "After moving items, reorder each configured tab using category-specific smart sorting.",
        position = 2,
        section = executionSection
    )
    default boolean sortWithinTabs()
    {
        return true;
    }

    @ConfigItem(
        keyName = "strictVerification",
        name = "Verify every move",
        description = "Verify stack count and quantities after every bank movement. Recommended.",
        position = 3,
        section = executionSection
    )
    default boolean strictVerification()
    {
        return true;
    }

    @ConfigItem(
        keyName = "closeBankWhenFinished",
        name = "Close bank when done",
        description = "Close the bank after a successful organize run.",
        position = 4,
        section = executionSection
    )
    default boolean closeBankWhenFinished()
    {
        return false;
    }

    @ConfigItem(keyName = "teleportsTarget", name = "Teleports", description = "Destination for teleport items.", position = 0, section = tabMappingSection)
    default BankTarget teleportsTarget() { return BankTarget.TAB_1; }

    @ConfigItem(keyName = "gearTarget", name = "Combat", description = "Destination for combat gear.", position = 1, section = tabMappingSection)
    default BankTarget gearTarget() { return BankTarget.TAB_2; }

    @ConfigItem(keyName = "potionsTarget", name = "Potions", description = "Destination for potions.", position = 2, section = tabMappingSection)
    default BankTarget potionsTarget() { return BankTarget.TAB_3; }

    @ConfigItem(keyName = "foodTarget", name = "Food", description = "Destination for food.", position = 3, section = tabMappingSection)
    default BankTarget foodTarget() { return BankTarget.TAB_4; }

    @ConfigItem(keyName = "skillingTarget", name = "Skilling", description = "Destination for skilling tools/outfits.", position = 4, section = tabMappingSection)
    default BankTarget skillingTarget() { return BankTarget.TAB_5; }

    @ConfigItem(keyName = "materialsTarget", name = "Materials", description = "Destination for raw materials.", position = 5, section = tabMappingSection)
    default BankTarget materialsTarget() { return BankTarget.TAB_6; }

    @ConfigItem(keyName = "highAlchTarget", name = "High Alch", description = "Destination for items explicitly classified as High Alch.", position = 6, section = tabMappingSection)
    default BankTarget highAlchTarget() { return BankTarget.TAB_7; }

    @ConfigItem(keyName = "currencyTarget", name = "Currency", description = "Destination for coins/tokens/currencies.", position = 7, section = tabMappingSection)
    default BankTarget currencyTarget() { return BankTarget.TAB_8; }

    @ConfigItem(keyName = "questMiscTarget", name = "Quest/Misc", description = "Destination for unmatched and quest/misc items.", position = 8, section = tabMappingSection)
    default BankTarget questMiscTarget() { return BankTarget.TAB_9; }

    @ConfigItem(
        keyName = "gearSortMode",
        name = "Gear sort",
        description = "Sort gear primarily by combat style or by equipment type.",
        position = 0,
        section = sortingSection
    )
    default GearSortMode gearSortMode()
    {
        return GearSortMode.COMBAT_STYLE;
    }

    @ConfigItem(
        keyName = "teleportSortMode",
        name = "Teleport sort",
        description = "Preferred order for runes, jewelry and tablets.",
        position = 1,
        section = sortingSection
    )
    default TeleportSortMode teleportSortMode()
    {
        return TeleportSortMode.RUNES_FIRST;
    }

    @ConfigItem(
        keyName = "manualOverrides",
        name = "Item ID overrides",
        description = "Comma-separated itemId:CATEGORY entries, e.g. 995:CURRENCY,4151:GEAR",
        position = 0,
        section = classificationSection
    )
    default String manualOverrides()
    {
        return "";
    }

    @ConfigItem(keyName = "regexTeleports", name = "Teleports regex", description = "Extra Java regex for Teleports.", position = 1, section = classificationSection)
    default String regexTeleports() { return ""; }

    @ConfigItem(keyName = "regexGear", name = "Combat regex", description = "Extra Java regex for Combat.", position = 2, section = classificationSection)
    default String regexGear() { return ""; }

    @ConfigItem(keyName = "regexPotions", name = "Potions regex", description = "Extra Java regex for Potions.", position = 3, section = classificationSection)
    default String regexPotions() { return ""; }

    @ConfigItem(keyName = "regexFood", name = "Food regex", description = "Extra Java regex for Food.", position = 4, section = classificationSection)
    default String regexFood() { return ""; }

    @ConfigItem(keyName = "regexSkilling", name = "Skilling regex", description = "Extra Java regex for Skilling.", position = 5, section = classificationSection)
    default String regexSkilling() { return ""; }

    @ConfigItem(keyName = "regexMaterials", name = "Materials regex", description = "Extra Java regex for Materials.", position = 6, section = classificationSection)
    default String regexMaterials() { return ""; }

    @ConfigItem(keyName = "regexHighAlch", name = "High Alch regex", description = "Extra Java regex for High Alch.", position = 7, section = classificationSection)
    default String regexHighAlch() { return ""; }

    @ConfigItem(keyName = "regexCurrency", name = "Currency regex", description = "Extra Java regex for Currency.", position = 8, section = classificationSection)
    default String regexCurrency() { return ""; }

    @ConfigItem(keyName = "regexQuestMisc", name = "Quest/Misc regex", description = "Extra Java regex for Quest/Misc.", position = 9, section = classificationSection)
    default String regexQuestMisc() { return ""; }

    @ConfigItem(
        keyName = "showOverlay",
        name = "Show status overlay",
        description = "Show organizer status, move counts and current phase.",
        position = 0,
        section = overlaySection
    )
    default boolean showOverlay()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showCategoryBoxes",
        name = "Color bank items",
        description = "Draw translucent category boxes over visible bank items.",
        position = 1,
        section = overlaySection
    )
    default boolean showCategoryBoxes()
    {
        return true;
    }

    @ConfigItem(
        keyName = "highlightMisplaced",
        name = "Highlight misplaced",
        description = "Outline visible items in red when they are not in their configured destination tab.",
        position = 2,
        section = overlaySection
    )
    default boolean highlightMisplaced()
    {
        return true;
    }

    @ConfigItem(
        keyName = "overlayOpacity",
        name = "Category opacity",
        description = "Category box opacity from 0 to 100.",
        position = 3,
        section = overlaySection
    )
    default int overlayOpacity()
    {
        return 28;
    }

    @Alpha
    @ConfigItem(keyName = "misplacedColor", name = "Misplaced outline", description = "Outline color for misplaced items.", position = 4, section = overlaySection)
    default Color misplacedColor()
    {
        return new Color(255, 70, 70, 220);
    }
}
