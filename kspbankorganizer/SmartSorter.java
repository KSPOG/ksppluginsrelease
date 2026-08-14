package net.runelite.client.plugins.microbot.kspbankorganizer;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.plugins.microbot.Microbot;

/** Category-specific ordering based on the smart-sort rules from the reference plugin. */
final class SmartSorter
{
    private static final Pattern CHARGE_PATTERN = Pattern.compile("\\((\\d+)\\)");
    private static final String SUB_OVERRIDES_RESOURCE =
        "/net/runelite/client/plugins/microbot/kspbankorganizer/reference_default_sub_overrides.txt";

    private static final Set<Integer> RUNE_IDS = new HashSet<>(Arrays.asList(
        554, 555, 556, 557, 558, 559, 560, 561, 562, 563, 564, 565, 566, 9075, 21880
    ));
    private static final Set<Integer> RUNE_POUCH_IDS = new HashSet<>(Arrays.asList(12791, 27281));

    private final ItemManager itemManager;
    private final Map<Integer, Integer> subOverrides = new HashMap<>();

    @Inject
    SmartSorter(ItemManager itemManager)
    {
        this.itemManager = itemManager;
        loadSubOverrides();
    }

    Comparator<PlannedItem> comparator(KspBankOrganizerConfig config)
    {
        return Comparator
            .comparingInt((PlannedItem item) -> item.category().ordinal())
            .thenComparingLong(item -> sortKey(item, config))
            .thenComparing(PlannedItem::name, String.CASE_INSENSITIVE_ORDER)
            .thenComparingInt(PlannedItem::itemId);
    }

    long sortKey(PlannedItem item, KspBankOrganizerConfig config)
    {
        switch (item.category())
        {
            case TELEPORTS:
                return teleportKey(item.name(), item.itemId(), config.teleportSortMode());
            case GEAR:
                return gearKey(item.name(), item.itemId(), config.gearSortMode());
            case POTIONS:
                return potionKey(item.name(), item.itemId());
            case FOOD:
                return foodKey(item.name(), item.itemId());
            case SKILLING:
                return skillingKey(item.name(), item.itemId());
            case RAW_MATERIALS:
                return materialKey(item.name(), item.itemId());
            case CURRENCY:
                return currencyKey(item.name(), item.itemId());
            case HIGH_ALCH:
            case QUEST_MISC:
            default:
                return alphabeticalKey(item.name(), item.itemId());
        }
    }

    private long teleportKey(String name, int itemId, TeleportSortMode mode)
    {
        String lower = lower(name);
        if (RUNE_POUCH_IDS.contains(itemId) || lower.contains("rune pouch"))
        {
            return 0L;
        }
        if (lower.contains("construct") && (lower.contains("cape") || lower.contains("hood")))
        {
            return ((long) 1 << 44) | itemId;
        }
        if (lower.contains("farming") && (lower.contains("cape") || lower.contains("hood")))
        {
            return ((long) 2 << 44) | itemId;
        }
        if ((lower.contains("cape") || lower.contains("hood")) && (lower.contains("(t)") || lower.contains("skillcape")))
        {
            return ((long) 3 << 44) | itemId;
        }

        TeleportSub sub = teleportSub(name, itemId);
        if (sub == TeleportSub.OTHER)
        {
            return ((long) 4 << 44) | (stableNameHash(name) & 0x0FFFFFFFL);
        }

        int subOrder = teleportSubOrder(sub, mode);
        int typeOrder = 0;
        int chargeOrder = 0;
        if (sub == TeleportSub.RUNES)
        {
            typeOrder = runeOrder(itemId);
        }
        else if (sub == TeleportSub.JEWELRY)
        {
            typeOrder = jewelryOrder(lower);
            chargeOrder = chargeOrder(name);
        }
        else if (sub == TeleportSub.TABLETS)
        {
            typeOrder = tabletOrder(lower);
        }

        return ((long) 5 << 44)
            | ((long) subOrder << 36)
            | ((long) typeOrder << 24)
            | ((long) (chargeOrder & 0xFF) << 16)
            | (itemId & 0xFFFFL);
    }

    private long potionKey(String name, int itemId)
    {
        String lower = lower(name);
        int divine = lower.contains("divine") ? 0 : 1;
        return ((long) divine << 36)
            | ((long) potionPriority(lower) << 24)
            | ((long) (chargeOrder(name) & 0xFF) << 16)
            | (itemId & 0xFFFFL);
    }

    private long foodKey(String name, int itemId)
    {
        int heal = foodHeal(lower(name));
        return ((long) (999 - heal) << 16) | (itemId & 0xFFFFL);
    }

    private long materialKey(String name, int itemId)
    {
        Integer override = subOverrides.get(itemId);
        if (override != null)
        {
            return ((long) override << 28) | (itemId & 0xFFFFL);
        }

        String lower = lower(name);
        int skill = 99;
        int type = 0;
        int tier = 50;

        if (lower.contains(" ore") || lower.equals("clay") || lower.equals("coal"))
        {
            skill = 4; type = 0; tier = oreTier(lower);
        }
        else if (lower.contains(" bar"))
        {
            skill = 4; type = 1; tier = barTier(lower);
        }
        else if (lower.contains("uncut") || isGem(lower))
        {
            skill = 4; type = 2; tier = gemTier(lower);
        }
        else if (lower.contains("logs") || lower.equals("logs"))
        {
            skill = 2; type = 0; tier = logTier(lower);
        }
        else if (lower.contains("plank"))
        {
            skill = 2; type = 1; tier = materialPlankTier(lower);
        }
        else if (lower.contains("arrow shaft") || lower.contains("bow string") || lower.contains("feather") || lower.contains("headless"))
        {
            skill = 2; type = 2;
        }
        else if (lower.contains("hide") || lower.contains("leather"))
        {
            skill = 9; type = 0; tier = hideTier(lower);
        }
        else if (lower.contains("wool") || lower.contains("flax") || lower.contains("sinew")
            || lower.contains("sand") || lower.contains("molten glass") || lower.contains("thread"))
        {
            skill = 9; type = 1;
        }
        else if (lower.contains("seed"))
        {
            skill = 0; type = 0; tier = seedTier(lower);
        }
        else if (lower.contains("grimy") || lower.contains("herb"))
        {
            skill = 0; type = 1; tier = herbTier(lower);
        }
        else if (lower.contains("compost") || lower.contains("plant cure"))
        {
            skill = 0; type = 2;
        }
        else if (lower.contains("essence"))
        {
            skill = 1; type = 0; tier = essenceTier(lower);
        }
        else if (lower.startsWith("raw "))
        {
            skill = 3; type = 0;
        }
        else if (lower.contains("fur") || lower.contains("kebbit"))
        {
            skill = 15; type = 0;
        }
        else if (lower.contains("bone") || lower.contains("ashes"))
        {
            skill = 5; type = 0;
        }

        return ((long) skill << 28)
            | ((long) (type & 0xF) << 24)
            | ((long) (tier & 0xFF) << 16)
            | (itemId & 0xFFFFL);
    }

    private long skillingKey(String name, int itemId)
    {
        String lower = lower(name);
        Integer override = subOverrides.get(itemId);
        int skill = override == null ? 99 : override;
        int tier = 50;

        if (lower.contains("bottomless") && lower.contains("compost")) { skill = 0; tier = 0; }
        else if (lower.contains("compost")) { skill = 0; tier = lower.contains("ultra") ? 1 : lower.contains("super") ? 2 : 3; }
        else if (lower.contains("herb sack")) { skill = 0; tier = 4; }
        else if (lower.contains("magic secateurs")) { skill = 0; tier = 6; }
        else if (lower.contains("spade")) { skill = 0; tier = 7; }
        else if (lower.contains("rake")) { skill = 0; tier = 8; }
        else if (lower.contains("seed dibber")) { skill = 0; tier = 9; }
        else if (lower.contains("watering can")) { skill = 0; tier = 12; }
        else if (lower.contains("seed box")) { skill = 0; tier = 13; }
        else if (lower.contains("farmer")) { skill = 0; tier = 20 + outfitSlot(lower); }
        else if (lower.contains("colossal pouch")) { skill = 1; tier = 0; }
        else if (lower.contains("giant pouch")) { skill = 1; tier = 4; }
        else if (lower.contains("large pouch")) { skill = 1; tier = 3; }
        else if (lower.contains("medium pouch")) { skill = 1; tier = 2; }
        else if (lower.contains("small pouch")) { skill = 1; tier = 1; }
        else if (lower.contains("talisman")) { skill = 1; tier = 10; }
        else if (lower.contains("lumberjack")) { skill = 2; tier = outfitSlot(lower); }
        else if (lower.contains("log basket") || lower.contains("forestry kit")) { skill = 2; tier = 10; }
        else if (lower.contains("axe") && !lower.contains("pickaxe") && !lower.contains("battleaxe")) { skill = 2; tier = 20 + toolTier(lower); }
        else if (lower.contains("angler")) { skill = 3; tier = outfitSlot(lower); }
        else if (lower.contains("fish barrel") || lower.contains("tackle box")) { skill = 3; tier = 10; }
        else if (lower.contains("harpoon")) { skill = 3; tier = 20 + toolTier(lower); }
        else if (lower.contains("rod") || lower.contains("net")) { skill = 3; tier = 30; }
        else if (lower.contains("prospector")) { skill = 4; tier = outfitSlot(lower); }
        else if (lower.contains("gem bag") || lower.contains("coal bag")) { skill = 4; tier = 10; }
        else if (lower.contains("pickaxe")) { skill = 4; tier = 20 + toolTier(lower); }
        else if (lower.contains("bone") || lower.contains("skull")) { skill = 5; tier = 20; }
        else if (lower.contains("graceful")) { skill = 6; tier = outfitSlot(lower); }
        else if (lower.contains("pyromancer")) { skill = 7; tier = outfitSlot(lower); }
        else if (lower.contains("tinderbox")) { skill = 7; tier = 10; }
        else if (lower.contains("rogue")) { skill = 8; tier = outfitSlot(lower); }
        else if (lower.contains("hammer") || lower.contains("chisel") || lower.contains("needle")
            || lower.contains("glassblowing") || lower.contains("pestle") || lower.contains("shears")) { skill = 9; tier = 0; }
        else if (lower.contains("plank sack")) { skill = 10; tier = 0; }
        else if (lower.contains("saw")) { skill = 10; tier = 1; }
        else if (lower.contains("sailing") || lower.contains("ship") || lower.contains("hull") || lower.contains("rigging")) { skill = 17; tier = 0; }

        if (override != null)
        {
            skill = override;
        }
        return ((long) skill << 28) | ((long) (tier & 0xFFF) << 16) | (itemId & 0xFFFFL);
    }

    private long gearKey(String name, int itemId, GearSortMode mode)
    {
        String lower = lower(name);
        ItemEquipmentStats stats = equipmentStats(itemId);
        GearSub sub = gearSub(lower, stats);
        int subOrder = gearSubOrder(sub, mode);
        int slotOrder = 99;
        long stat = 0;

        if (stats != null)
        {
            slotOrder = slotOrder(stats.getSlot());
            switch (sub)
            {
                case MELEE_WEAPON:
                case MELEE_ARMOR:
                    stat = (long) stats.getStr() * 1000L
                        + Math.max(stats.getAstab(), Math.max(stats.getAslash(), stats.getAcrush()));
                    break;
                case RANGED_WEAPON:
                case RANGED_ARMOR:
                    stat = (long) stats.getRstr() * 1000L + stats.getArange();
                    break;
                case MAGE_WEAPON:
                case MAGE_ARMOR:
                    stat = (long) stats.getMdmg() * 1000L + stats.getAmagic();
                    break;
                default:
                    break;
            }
        }

        long inverted = Math.max(0, 999999L - stat);
        return ((long) subOrder << 44)
            | ((long) slotOrder << 36)
            | ((inverted & 0xFFFFFL) << 16)
            | (itemId & 0xFFFFL);
    }

    private ItemEquipmentStats equipmentStats(int itemId)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            ItemStats stats = itemManager.getItemStats(itemId);
            if (stats != null && stats.isEquipable())
            {
                return stats.getEquipment();
            }
            return null;
        }).orElse(null);
    }

    private static GearSub gearSub(String lower, ItemEquipmentStats stats)
    {
        boolean weaponByName = containsAny(lower, "scimitar", "longsword", "sword", "dagger", "mace", "warhammer",
            "battleaxe", "halberd", "spear", "hasta", "whip", "godsword", "rapier", "bludgeon",
            "shortbow", "longbow", "crossbow", "blowpipe", "ballista", "staff", "wand", "trident");

        if (stats != null)
        {
            int meleeAttack = Math.max(stats.getAstab(), Math.max(stats.getAslash(), stats.getAcrush()));
            boolean weapon = stats.getSlot() == 3 || stats.isTwoHanded() || weaponByName;
            if (stats.getMdmg() > 0 || stats.getAmagic() > Math.max(meleeAttack, stats.getArange()))
            {
                return weapon ? GearSub.MAGE_WEAPON : GearSub.MAGE_ARMOR;
            }
            if (stats.getArange() > meleeAttack || stats.getRstr() > stats.getStr())
            {
                return weapon ? GearSub.RANGED_WEAPON : GearSub.RANGED_ARMOR;
            }
            if (weapon)
            {
                return GearSub.MELEE_WEAPON;
            }
            return GearSub.MELEE_ARMOR;
        }

        if (containsAny(lower, "crossbow", "shortbow", "longbow", "blowpipe", "ballista", "dart", "javelin", "thrownaxe", "knife")) return GearSub.RANGED_WEAPON;
        if (containsAny(lower, "staff", "wand", "trident", "sanguinesti", "kodai")) return GearSub.MAGE_WEAPON;
        if (containsAny(lower, "d'hide", "dragonhide", "coif", "vambraces", "chaps", "armadyl", "karil", "masori")) return GearSub.RANGED_ARMOR;
        if (containsAny(lower, "mystic", "infinity", "ahrim", "ancestral", "virtus", "mage's book")) return GearSub.MAGE_ARMOR;
        if (weaponByName) return GearSub.MELEE_WEAPON;
        return GearSub.MELEE_ARMOR;
    }

    private static int gearSubOrder(GearSub sub, GearSortMode mode)
    {
        if (mode == GearSortMode.EQUIPMENT_TYPE)
        {
            switch (sub)
            {
                case MELEE_WEAPON: return 0;
                case RANGED_WEAPON: return 1;
                case MAGE_WEAPON: return 2;
                case MELEE_ARMOR: return 3;
                case RANGED_ARMOR: return 4;
                case MAGE_ARMOR: return 5;
                default: return 6;
            }
        }

        switch (sub)
        {
            case MELEE_WEAPON: return 0;
            case MELEE_ARMOR: return 1;
            case RANGED_WEAPON: return 2;
            case RANGED_ARMOR: return 3;
            case MAGE_WEAPON: return 4;
            case MAGE_ARMOR: return 5;
            default: return 6;
        }
    }

    private static int slotOrder(int slot)
    {
        switch (slot)
        {
            case 3: return 0;  // weapon
            case 0: return 1;  // head
            case 2: return 2;  // neck
            case 1: return 3;  // cape
            case 4: return 4;  // body
            case 7: return 5;  // legs
            case 5: return 6;  // shield
            case 9: return 7;  // gloves
            case 10: return 8; // boots
            case 12: return 9; // ring
            case 13: return 10;// ammo
            default: return 99;
        }
    }

    private static long currencyKey(String name, int itemId)
    {
        switch (itemId)
        {
            case 995: return 0;
            case 13204: return 1;
            case 6529: return 2;
            case 6306: return 3;
            case 26792: return 4;
            default: return 1000L;
        }
    }

    private static long alphabeticalKey(String name, int itemId)
    {
        // Name is the next comparator component; equal keys produce true alphabetical ordering.
        return 0L;
    }

    private static TeleportSub teleportSub(String name, int itemId)
    {
        String lower = lower(name);
        if (RUNE_IDS.contains(itemId) || lower.endsWith(" rune")) return TeleportSub.RUNES;
        if (lower.contains("glory(") || lower.contains("dueling(") || lower.contains("games necklace(")
            || lower.contains("wealth(") || lower.contains("skills necklace(") || lower.contains("combat bracelet(")
            || lower.contains("passage(") || lower.contains("burning amulet(") || lower.contains("digsite pendant(")
            || lower.contains("slayer ring(")) return TeleportSub.JEWELRY;
        if (lower.contains("teleport") || lower.contains("scroll")) return TeleportSub.TABLETS;
        return TeleportSub.OTHER;
    }

    private static int teleportSubOrder(TeleportSub sub, TeleportSortMode mode)
    {
        switch (mode)
        {
            case JEWELRY_FIRST:
                return sub == TeleportSub.JEWELRY ? 0 : sub == TeleportSub.RUNES ? 1 : sub == TeleportSub.TABLETS ? 2 : 3;
            case TABLETS_FIRST:
                return sub == TeleportSub.TABLETS ? 0 : sub == TeleportSub.RUNES ? 1 : sub == TeleportSub.JEWELRY ? 2 : 3;
            case RUNES_FIRST:
            default:
                return sub == TeleportSub.RUNES ? 0 : sub == TeleportSub.JEWELRY ? 1 : sub == TeleportSub.TABLETS ? 2 : 3;
        }
    }

    private static int runeOrder(int itemId)
    {
        switch (itemId)
        {
            case 556: return 0;
            case 555: return 1;
            case 557: return 2;
            case 554: return 3;
            case 558: return 20;
            case 559: return 21;
            case 564: return 22;
            case 562: return 23;
            case 9075: return 24;
            case 561: return 25;
            case 563: return 26;
            case 560: return 27;
            case 565: return 28;
            case 566: return 29;
            case 21880: return 30;
            default: return 50;
        }
    }

    private static int jewelryOrder(String lower)
    {
        if (lower.contains("ring of dueling")) return 0;
        if (lower.contains("games necklace")) return 1;
        if (lower.contains("ring of wealth")) return 2;
        if (lower.contains("glory")) return 3;
        if (lower.contains("skills necklace")) return 4;
        if (lower.contains("combat bracelet")) return 5;
        if (lower.contains("necklace of passage")) return 6;
        if (lower.contains("burning amulet")) return 7;
        if (lower.contains("digsite pendant")) return 8;
        if (lower.contains("slayer ring")) return 9;
        return 50;
    }

    private static int tabletOrder(String lower)
    {
        if (lower.contains("varrock teleport")) return 0;
        if (lower.contains("lumbridge teleport")) return 1;
        if (lower.contains("falador teleport")) return 2;
        if (lower.contains("camelot teleport")) return 3;
        if (lower.contains("ardougne teleport")) return 4;
        if (lower.contains("watchtower teleport")) return 5;
        if (lower.contains("teleport to house")) return 6;
        if (lower.contains("barrows")) return 61;
        if (lower.contains("zul-andra") || lower.contains("zulandra")) return 107;
        return 120;
    }

    private static int chargeOrder(String name)
    {
        Matcher matcher = CHARGE_PATTERN.matcher(name == null ? "" : name);
        if (matcher.find())
        {
            try
            {
                return 99 - Integer.parseInt(matcher.group(1));
            }
            catch (NumberFormatException ignored)
            {
                // fall through
            }
        }
        return 100;
    }

    private static int potionPriority(String lower)
    {
        if (lower.contains("super combat")) return 0;
        if (lower.contains("ranging potion")) return 1;
        if (lower.contains("bastion potion")) return 2;
        if (lower.contains("saradomin brew")) return 3;
        if (lower.contains("blighted super restore")) return 4;
        if (lower.contains("super restore")) return 4;
        if (lower.contains("prayer potion")) return 5;
        if (lower.contains("surge")) return 6;
        if (lower.contains("anti-venom+") || lower.contains("antivenom+")) return 7;
        if (lower.contains("anti-venom") || lower.contains("antivenom")) return 9;
        if (lower.contains("antipoison")) return 10;
        if (lower.contains("stamina")) return 11;
        if (lower.contains("extended antifire")) return 12;
        if (lower.contains("antifire")) return 13;
        if (lower.contains("menaphite remedy")) return 14;
        if (lower.contains("super attack")) return 20;
        if (lower.contains("super strength")) return 21;
        if (lower.contains("super defence")) return 22;
        if (lower.contains("super energy")) return 23;
        if (lower.contains("attack potion")) return 30;
        if (lower.contains("strength potion")) return 31;
        if (lower.contains("defence potion")) return 32;
        if (lower.contains("magic potion")) return 33;
        if (lower.contains("agility")) return 40;
        if (lower.contains("fishing")) return 41;
        if (lower.contains("hunter")) return 42;
        if (lower.contains("mining")) return 43;
        if (lower.contains("woodcutting")) return 44;
        if (lower.contains("overload")) return 50;
        if (lower.contains("battlemage")) return 51;
        if (lower.contains("energy")) return 52;
        if (lower.contains("antidote")) return 53;
        return 99;
    }

    private static int foodHeal(String lower)
    {
        if (lower.contains("anglerfish")) return 22;
        if (lower.contains("dark crab")) return 22;
        if (lower.contains("manta ray")) return 22;
        if (lower.contains("tuna potato")) return 22;
        if (lower.contains("sea turtle")) return 21;
        if (lower.contains("shark")) return 20;
        if (lower.contains("mushroom potato")) return 20;
        if (lower.contains("karambwan")) return 18;
        if (lower.contains("monkfish")) return 16;
        if (lower.contains("potato with cheese")) return 16;
        if (lower.contains("swordfish")) return 14;
        if (lower.contains("chilli potato")) return 14;
        if (lower.contains("bass")) return 13;
        if (lower.contains("lobster")) return 12;
        if (lower.contains("cake")) return 12;
        if (lower.contains("stew")) return 11;
        if (lower.contains("pizza")) return 11;
        if (lower.contains("wine")) return 11;
        if (lower.contains("tuna")) return 10;
        if (lower.contains("salmon")) return 9;
        if (lower.contains("pike")) return 8;
        if (lower.contains("pie")) return 8;
        if (lower.contains("trout")) return 7;
        if (lower.contains("cod")) return 7;
        if (lower.contains("mackerel")) return 6;
        if (lower.contains("herring")) return 5;
        if (lower.contains("bread")) return 5;
        if (lower.contains("sardine")) return 4;
        if (lower.contains("shrimps")) return 3;
        if (lower.contains("meat") || lower.contains("chicken")) return 3;
        if (lower.contains("anchovies")) return 1;
        if (lower.contains("cooked")) return 5;
        return 0;
    }

    private static int oreTier(String lower)
    {
        if (lower.contains("copper")) return 0;
        if (lower.contains("tin")) return 1;
        if (lower.contains("clay")) return 2;
        if (lower.contains("iron")) return 3;
        if (lower.contains("silver")) return 4;
        if (lower.contains("coal")) return 5;
        if (lower.contains("gold")) return 6;
        if (lower.contains("mithril")) return 7;
        if (lower.contains("adamant")) return 8;
        if (lower.contains("runite")) return 9;
        if (lower.contains("amethyst")) return 10;
        return 50;
    }

    private static int barTier(String lower)
    {
        if (lower.contains("bronze")) return 0;
        if (lower.contains("iron")) return 1;
        if (lower.contains("steel")) return 2;
        if (lower.contains("silver")) return 3;
        if (lower.contains("gold")) return 4;
        if (lower.contains("mithril")) return 5;
        if (lower.contains("adamant")) return 6;
        if (lower.contains("runite")) return 7;
        return 50;
    }

    private static int logTier(String lower)
    {
        if (lower.equals("logs")) return 0;
        if (lower.contains("oak")) return 1;
        if (lower.contains("willow")) return 2;
        if (lower.contains("teak")) return 3;
        if (lower.contains("maple")) return 4;
        if (lower.contains("mahogany")) return 5;
        if (lower.contains("yew")) return 6;
        if (lower.contains("magic")) return 7;
        if (lower.contains("redwood")) return 8;
        return 50;
    }

    private static int materialPlankTier(String lower)
    {
        if (lower.contains("mahogany")) return 3;
        if (lower.contains("teak")) return 2;
        if (lower.contains("oak")) return 1;
        return 0;
    }

    private static int hideTier(String lower)
    {
        if (lower.contains("cowhide") || (lower.contains("leather") && !lower.contains("dragon"))) return 0;
        if (lower.contains("green")) return 1;
        if (lower.contains("blue")) return 2;
        if (lower.contains("red")) return 3;
        if (lower.contains("black")) return 4;
        return 50;
    }

    private static boolean isGem(String lower)
    {
        return containsAny(lower, "sapphire", "emerald", "ruby", "diamond", "dragonstone", "onyx", "zenyte");
    }

    private static int gemTier(String lower)
    {
        if (lower.contains("sapphire")) return 0;
        if (lower.contains("emerald")) return 1;
        if (lower.contains("ruby")) return 2;
        if (lower.contains("diamond")) return 3;
        if (lower.contains("dragonstone")) return 4;
        if (lower.contains("onyx")) return 5;
        if (lower.contains("zenyte")) return 6;
        return 50;
    }

    private static int seedTier(String lower)
    {
        String[] order = {"potato", "onion", "cabbage", "tomato", "sweetcorn", "strawberry", "watermelon", "snape",
            "acorn", "willow seed", "maple seed", "yew seed", "magic seed", "guam", "marrentill", "tarromin",
            "harralander", "ranarr", "toadflax", "irit", "avantoe", "kwuarm", "snapdragon", "cadantine",
            "lantadyme", "dwarf weed", "torstol"};
        for (int i = 0; i < order.length; i++) if (lower.contains(order[i])) return i;
        return 50;
    }

    private static int herbTier(String lower)
    {
        int base = lower.contains("grimy") ? 0 : 100;
        String[] order = {"guam", "marrentill", "tarromin", "harralander", "ranarr", "toadflax", "irit", "avantoe",
            "kwuarm", "snapdragon", "cadantine", "lantadyme", "dwarf weed", "torstol"};
        for (int i = 0; i < order.length; i++) if (lower.contains(order[i])) return base + i;
        return base + 50;
    }

    private static int essenceTier(String lower)
    {
        if (lower.contains("rune essence")) return 0;
        if (lower.contains("pure essence")) return 1;
        if (lower.contains("daeyalt")) return 2;
        if (lower.contains("dark")) return 3;
        return 50;
    }

    private static int outfitSlot(String lower)
    {
        if (containsAny(lower, "hood", "hat", "helmet", "head")) return 0;
        if (containsAny(lower, "top", "body", "jacket")) return 1;
        if (containsAny(lower, "legs", "trousers", "robe bottom")) return 2;
        if (lower.contains("glove")) return 3;
        if (lower.contains("boot")) return 4;
        if (lower.contains("cape")) return 5;
        return 9;
    }

    private static int toolTier(String lower)
    {
        String[] order = {"bronze", "iron", "steel", "black", "mithril", "adamant", "rune", "dragon", "crystal", "infernal", "3rd age"};
        for (int i = 0; i < order.length; i++) if (lower.contains(order[i])) return i;
        return 50;
    }

    private void loadSubOverrides()
    {
        try (InputStream stream = SmartSorter.class.getResourceAsStream(SUB_OVERRIDES_RESOURCE))
        {
            if (stream == null) return;
            StringBuilder text = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)))
            {
                String line;
                while ((line = reader.readLine()) != null) text.append(line);
            }
            String normalized = text.toString().replace("\\:", ":");
            for (String token : normalized.split(","))
            {
                int colon = token.indexOf(':');
                if (colon <= 0) continue;
                try
                {
                    subOverrides.put(Integer.parseInt(token.substring(0, colon).trim()),
                        Integer.parseInt(token.substring(colon + 1).trim()));
                }
                catch (NumberFormatException ignored)
                {
                }
            }
        }
        catch (Exception ignored)
        {
        }
    }

    private static boolean containsAny(String lower, String... needles)
    {
        for (String needle : needles) if (lower.contains(needle)) return true;
        return false;
    }

    private static String lower(String value)
    {
        return value == null ? "" : value.toLowerCase();
    }

    private static long stableNameHash(String value)
    {
        return value == null ? 0 : Integer.toUnsignedLong(value.toLowerCase().hashCode());
    }

    private enum TeleportSub { RUNES, JEWELRY, TABLETS, OTHER }
    private enum GearSub { MELEE_WEAPON, MELEE_ARMOR, RANGED_WEAPON, RANGED_ARMOR, MAGE_WEAPON, MAGE_ARMOR, GENERAL }
}
