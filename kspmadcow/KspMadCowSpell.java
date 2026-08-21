package net.runelite.client.plugins.microbot.kspmadcow;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells;
import net.runelite.client.plugins.microbot.util.magic.Runes;

/**
 * Standard elemental combat spells supported by KSP Mad Cow.
 *
 * The rune maps are kept here instead of relying on the combat-spell utility's
 * supply metadata so banking is deterministic. The selected elemental staff
 * supplies the matching elemental rune. When that staff is already equipped,
 * banking skips its matching elemental rune and withdraws the remaining spell runes.
 */
@Getter
@RequiredArgsConstructor
public enum KspMadCowSpell {
    WIND_STRIKE("Wind Strike", Rs2CombatSpells.WIND_STRIKE, Runes.AIR, ItemID.STAFF_OF_AIR, req(Runes.AIR, 1, Runes.MIND, 1)),
    WATER_STRIKE("Water Strike", Rs2CombatSpells.WATER_STRIKE, Runes.WATER, ItemID.STAFF_OF_WATER, req(Runes.AIR, 1, Runes.WATER, 1, Runes.MIND, 1)),
    EARTH_STRIKE("Earth Strike", Rs2CombatSpells.EARTH_STRIKE, Runes.EARTH, ItemID.STAFF_OF_EARTH, req(Runes.AIR, 1, Runes.EARTH, 2, Runes.MIND, 1)),
    FIRE_STRIKE("Fire Strike", Rs2CombatSpells.FIRE_STRIKE, Runes.FIRE, ItemID.STAFF_OF_FIRE, req(Runes.AIR, 2, Runes.FIRE, 3, Runes.MIND, 1)),

    WIND_BOLT("Wind Bolt", Rs2CombatSpells.WIND_BOLT, Runes.AIR, ItemID.STAFF_OF_AIR, req(Runes.AIR, 2, Runes.CHAOS, 1)),
    WATER_BOLT("Water Bolt", Rs2CombatSpells.WATER_BOLT, Runes.WATER, ItemID.STAFF_OF_WATER, req(Runes.AIR, 2, Runes.WATER, 2, Runes.CHAOS, 1)),
    EARTH_BOLT("Earth Bolt", Rs2CombatSpells.EARTH_BOLT, Runes.EARTH, ItemID.STAFF_OF_EARTH, req(Runes.AIR, 2, Runes.EARTH, 3, Runes.CHAOS, 1)),
    FIRE_BOLT("Fire Bolt", Rs2CombatSpells.FIRE_BOLT, Runes.FIRE, ItemID.STAFF_OF_FIRE, req(Runes.AIR, 3, Runes.FIRE, 4, Runes.CHAOS, 1)),

    WIND_BLAST("Wind Blast", Rs2CombatSpells.WIND_BLAST, Runes.AIR, ItemID.STAFF_OF_AIR, req(Runes.AIR, 3, Runes.DEATH, 1)),
    WATER_BLAST("Water Blast", Rs2CombatSpells.WATER_BLAST, Runes.WATER, ItemID.STAFF_OF_WATER, req(Runes.AIR, 3, Runes.WATER, 3, Runes.DEATH, 1)),
    EARTH_BLAST("Earth Blast", Rs2CombatSpells.EARTH_BLAST, Runes.EARTH, ItemID.STAFF_OF_EARTH, req(Runes.AIR, 3, Runes.EARTH, 4, Runes.DEATH, 1)),
    FIRE_BLAST("Fire Blast", Rs2CombatSpells.FIRE_BLAST, Runes.FIRE, ItemID.STAFF_OF_FIRE, req(Runes.AIR, 4, Runes.FIRE, 5, Runes.DEATH, 1)),

    WIND_WAVE("Wind Wave", Rs2CombatSpells.WIND_WAVE, Runes.AIR, ItemID.STAFF_OF_AIR, req(Runes.AIR, 5, Runes.BLOOD, 1)),
    WATER_WAVE("Water Wave", Rs2CombatSpells.WATER_WAVE, Runes.WATER, ItemID.STAFF_OF_WATER, req(Runes.AIR, 5, Runes.WATER, 7, Runes.BLOOD, 1)),
    EARTH_WAVE("Earth Wave", Rs2CombatSpells.EARTH_WAVE, Runes.EARTH, ItemID.STAFF_OF_EARTH, req(Runes.AIR, 5, Runes.EARTH, 7, Runes.BLOOD, 1)),
    FIRE_WAVE("Fire Wave", Rs2CombatSpells.FIRE_WAVE, Runes.FIRE, ItemID.STAFF_OF_FIRE, req(Runes.AIR, 5, Runes.FIRE, 7, Runes.BLOOD, 1)),

    WIND_SURGE("Wind Surge", Rs2CombatSpells.WIND_SURGE, Runes.AIR, ItemID.STAFF_OF_AIR, req(Runes.AIR, 7, Runes.WRATH, 1)),
    WATER_SURGE("Water Surge", Rs2CombatSpells.WATER_SURGE, Runes.WATER, ItemID.STAFF_OF_WATER, req(Runes.AIR, 7, Runes.WATER, 10, Runes.WRATH, 1)),
    EARTH_SURGE("Earth Surge", Rs2CombatSpells.EARTH_SURGE, Runes.EARTH, ItemID.STAFF_OF_EARTH, req(Runes.AIR, 7, Runes.EARTH, 10, Runes.WRATH, 1)),
    FIRE_SURGE("Fire Surge", Rs2CombatSpells.FIRE_SURGE, Runes.FIRE, ItemID.STAFF_OF_FIRE, req(Runes.AIR, 7, Runes.FIRE, 10, Runes.WRATH, 1));

    private final String displayName;
    private final Rs2CombatSpells combatSpell;
    private final Runes staffRune;
    private final int staffItemId;
    private final Map<Runes, Integer> requiredRunes;

    @Override
    public String toString() {
        return displayName;
    }

    private static Map<Runes, Integer> req(Object... values) {
        EnumMap<Runes, Integer> map = new EnumMap<>(Runes.class);
        for (int i = 0; i + 1 < values.length; i += 2) {
            map.put((Runes) values[i], (Integer) values[i + 1]);
        }
        return Collections.unmodifiableMap(map);
    }
}
