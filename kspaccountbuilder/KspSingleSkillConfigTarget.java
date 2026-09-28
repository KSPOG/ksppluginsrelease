package net.runelite.client.plugins.microbot.kspaccountbuilder;

public final class KspSingleSkillConfigTarget
{
    private KspSingleSkillConfigTarget()
    {
    }

    public interface TargetOption
    {
        KspSingleSkillTarget getTarget();
    }

    public enum Mining implements TargetOption
    {
        NONE(KspSingleSkillTarget.NONE),
        PROGRESSIVE(KspSingleSkillTarget.AUTOMATIC),
        VARROCK_EAST_TIN_COPPER(KspSingleSkillTarget.MINING_TIN_COPPER),
        VARROCK_EAST_IRON(KspSingleSkillTarget.MINING_IRON),
        VARROCK_WEST_CLAY(KspSingleSkillTarget.MINING_CLAY),
        VARROCK_WEST_SILVER(KspSingleSkillTarget.MINING_SILVER),
        BARBARIAN_VILLAGE_COAL(KspSingleSkillTarget.MINING_COAL);

        private final KspSingleSkillTarget target;

        Mining(KspSingleSkillTarget target)
        {
            this.target = target;
        }

        public KspSingleSkillTarget getTarget()
        {
            return target;
        }

        public String toString()
        {
            return this == PROGRESSIVE ? "Progressive" : target.toString();
        }
    }

    public enum Woodcutting implements TargetOption
    {
        NONE(KspSingleSkillTarget.NONE),
        PROGRESSIVE(KspSingleSkillTarget.AUTOMATIC),
        REGULAR_TREES_VARROCK_WEST(KspSingleSkillTarget.WOODCUTTING_REGULAR),
        OAK_TREES_DRAYNOR(KspSingleSkillTarget.WOODCUTTING_OAK_DRAYNOR),
        OAK_TREES_VARROCK_CASTLE(KspSingleSkillTarget.WOODCUTTING_OAK_CASTLE),
        OAK_TREES_VARROCK_WEST(KspSingleSkillTarget.WOODCUTTING_OAK_WEST),
        OAK_TREES_VARROCK_EAST(KspSingleSkillTarget.WOODCUTTING_OAK_EAST),
        WILLOWS_DRAYNOR(KspSingleSkillTarget.WOODCUTTING_WILLOW),
        YEWS_VARROCK_PALACE(KspSingleSkillTarget.WOODCUTTING_YEW);

        private final KspSingleSkillTarget target;

        Woodcutting(KspSingleSkillTarget target)
        {
            this.target = target;
        }

        public KspSingleSkillTarget getTarget()
        {
            return target;
        }

        public String toString()
        {
            return this == PROGRESSIVE ? "Progressive" : target.toString();
        }
    }

    public enum Fishing implements TargetOption
    {
        NONE(KspSingleSkillTarget.NONE),
        PROGRESSIVE(KspSingleSkillTarget.AUTOMATIC),
        SHRIMP_AND_ANCHOVIES(KspSingleSkillTarget.FISHING_SHRIMP),
        SARDINE_AND_HERRING(KspSingleSkillTarget.FISHING_SARDINE),
        TROUT_AND_SALMON(KspSingleSkillTarget.FISHING_TROUT),
        KARAMJA(KspSingleSkillTarget.FISHING_KARAMJA);

        private final KspSingleSkillTarget target;

        Fishing(KspSingleSkillTarget target)
        {
            this.target = target;
        }

        public KspSingleSkillTarget getTarget()
        {
            return target;
        }

        public String toString()
        {
            return this == PROGRESSIVE ? "Progressive" : target.toString();
        }
    }

    public enum Cooking implements TargetOption
    {
        NONE(KspSingleSkillTarget.NONE),
        AUTOMATIC(KspSingleSkillTarget.AUTOMATIC),
        EDGEVILLE_RANGE(KspSingleSkillTarget.COOKING_EDGEVILLE),
        LUMBRIDGE_KITCHEN(KspSingleSkillTarget.COOKING_LUMBRIDGE);

        private final KspSingleSkillTarget target;

        Cooking(KspSingleSkillTarget target)
        {
            this.target = target;
        }

        public KspSingleSkillTarget getTarget()
        {
            return target;
        }

        public String toString()
        {
            return this == AUTOMATIC ? "Automatic" : target.toString();
        }
    }

    public enum Melee implements TargetOption
    {
        NONE(KspSingleSkillTarget.NONE),
        AUTOMATIC(KspSingleSkillTarget.AUTOMATIC),
        COWS(KspSingleSkillTarget.MELEE_COWS),
        CHICKENS(KspSingleSkillTarget.MELEE_CHICKENS),
        AL_KHARID_WARRIORS(KspSingleSkillTarget.MELEE_WARRIORS),
        HILL_GIANTS(KspSingleSkillTarget.MELEE_HILL_GIANTS),
        MOSS_GIANTS(KspSingleSkillTarget.MELEE_MOSS_GIANTS);

        private final KspSingleSkillTarget target;

        Melee(KspSingleSkillTarget target)
        {
            this.target = target;
        }

        public KspSingleSkillTarget getTarget()
        {
            return target;
        }

        public String toString()
        {
            return this == AUTOMATIC ? "Automatic" : target.toString();
        }
    }

    public enum Crafting implements TargetOption
    {
        NONE(KspSingleSkillTarget.NONE),
        PROGRESSIVE(KspSingleSkillTarget.AUTOMATIC),
        LEATHER_GLOVES(KspSingleSkillTarget.CRAFTING_LEATHER_GLOVES),
        GOLD_RING(KspSingleSkillTarget.CRAFTING_GOLD_RING),
        GOLD_NECKLACE(KspSingleSkillTarget.CRAFTING_GOLD_NECKLACE),
        CUT_SAPPHIRE(KspSingleSkillTarget.CRAFTING_CUT_SAPPHIRE),
        SAPPHIRE_RING(KspSingleSkillTarget.CRAFTING_SAPPHIRE_RING),
        SAPPHIRE_NECKLACE(KspSingleSkillTarget.CRAFTING_SAPPHIRE_NECKLACE),
        TIARA(KspSingleSkillTarget.CRAFTING_TIARA),
        CUT_EMERALD(KspSingleSkillTarget.CRAFTING_CUT_EMERALD),
        EMERALD_RING(KspSingleSkillTarget.CRAFTING_EMERALD_RING),
        EMERALD_NECKLACE(KspSingleSkillTarget.CRAFTING_EMERALD_NECKLACE),
        CUT_RUBY(KspSingleSkillTarget.CRAFTING_CUT_RUBY),
        RUBY_RING(KspSingleSkillTarget.CRAFTING_RUBY_RING),
        RUBY_NECKLACE(KspSingleSkillTarget.CRAFTING_RUBY_NECKLACE),
        CUT_DIAMOND(KspSingleSkillTarget.CRAFTING_CUT_DIAMOND),
        DIAMOND_RING(KspSingleSkillTarget.CRAFTING_DIAMOND_RING),
        DIAMOND_NECKLACE(KspSingleSkillTarget.CRAFTING_DIAMOND_NECKLACE);

        private final KspSingleSkillTarget target;

        Crafting(KspSingleSkillTarget target)
        {
            this.target = target;
        }

        public KspSingleSkillTarget getTarget()
        {
            return target;
        }

        public String toString()
        {
            return this == PROGRESSIVE ? "Progressive" : target.toString();
        }
    }

    public enum Smithing implements TargetOption
    {
        NONE(KspSingleSkillTarget.NONE),
        PROGRESSIVE(KspSingleSkillTarget.AUTOMATIC),
        BRONZE_DAGGER(KspSingleSkillTarget.SMITHING_BRONZE_DAGGER),
        BRONZE_SCIMITAR(KspSingleSkillTarget.SMITHING_BRONZE_SCIMITAR),
        BRONZE_WARHAMMER(KspSingleSkillTarget.SMITHING_BRONZE_WARHAMMER),
        BRONZE_PLATEBODY(KspSingleSkillTarget.SMITHING_BRONZE_PLATEBODY),
        IRON_SCIMITAR(KspSingleSkillTarget.SMITHING_IRON_SCIMITAR),
        IRON_WARHAMMER(KspSingleSkillTarget.SMITHING_IRON_WARHAMMER),
        IRON_PLATEBODY(KspSingleSkillTarget.SMITHING_IRON_PLATEBODY),
        STEEL_SCIMITAR(KspSingleSkillTarget.SMITHING_STEEL_SCIMITAR),
        STEEL_WARHAMMER(KspSingleSkillTarget.SMITHING_STEEL_WARHAMMER),
        STEEL_PLATEBODY(KspSingleSkillTarget.SMITHING_STEEL_PLATEBODY);

        private final KspSingleSkillTarget target;

        Smithing(KspSingleSkillTarget target)
        {
            this.target = target;
        }

        public KspSingleSkillTarget getTarget()
        {
            return target;
        }

        public String toString()
        {
            return this == PROGRESSIVE ? "Progressive" : target.toString();
        }
    }

    public enum Smelting implements TargetOption
    {
        NONE(KspSingleSkillTarget.NONE),
        PROGRESSIVE(KspSingleSkillTarget.AUTOMATIC),
        BRONZE_BAR(KspSingleSkillTarget.SMELTING_BRONZE),
        IRON_BAR(KspSingleSkillTarget.SMELTING_IRON),
        SILVER_BAR(KspSingleSkillTarget.SMELTING_SILVER),
        STEEL_BAR(KspSingleSkillTarget.SMELTING_STEEL),
        GOLD_BAR(KspSingleSkillTarget.SMELTING_GOLD),
        MITHRIL_BAR(KspSingleSkillTarget.SMELTING_MITHRIL),
        ADAMANT_BAR(KspSingleSkillTarget.SMELTING_ADAMANT),
        RUNE_BAR(KspSingleSkillTarget.SMELTING_RUNE);

        private final KspSingleSkillTarget target;

        Smelting(KspSingleSkillTarget target)
        {
            this.target = target;
        }

        public KspSingleSkillTarget getTarget()
        {
            return target;
        }

        public String toString()
        {
            return this == PROGRESSIVE ? "Progressive" : target.toString();
        }
    }
}
