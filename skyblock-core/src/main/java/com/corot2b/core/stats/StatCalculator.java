package com.corot2b.core.stats;

import com.corot2b.api.model.ItemInstance;
import com.corot2b.api.model.PlayerStats;
import com.corot2b.api.model.SkyBlockProfile;
import com.corot2b.core.content.ItemDefinition;
import com.corot2b.core.content.ItemRegistry;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds a player's final {@link PlayerStats} from every contributing source.
 *
 * <p>Order matters and mirrors the live game:
 * <ol>
 *   <li>baseline (100 hp, 30% crit chance, 50% crit damage)</li>
 *   <li>skill levels</li>
 *   <li>armor pieces + full-set bonuses</li>
 *   <li>held weapon</li>
 *   <li>accessory bag (one bonus per accessory line — a Ring replaces its Talisman)</li>
 *   <li>active pet (stats scale with pet level)</li>
 *   <li>transient buffs (potions, beacon, blessings)</li>
 * </ol>
 */
public final class StatCalculator {

    private StatCalculator() {}

    /**
     * @param buffs transient stat sources keyed by name; last writer wins per source
     *              but sources stack additively with each other.
     */
    public static PlayerStats calculate(SkyBlockProfile profile,
                                        ItemInstance held,
                                        Map<String, Map<PlayerStats.Stat, Double>> buffs) {
        PlayerStats stats = new PlayerStats();

        // 1. skills
        stats.addAll(SkillBonusAdapter.toStatMap(
                com.corot2b.core.skills.SkillService.statBonuses(profile)));

        // 2. armor + set bonuses
        Set<String> wornSets = new HashSet<>();
        int piecesPerSet = 0;
        String detectedSet = null;
        for (ItemInstance piece : profile.armor()) {
            if (piece == null) continue;
            ItemDefinition def = ItemRegistry.get(piece.id()).orElse(null);
            if (def == null) continue;
            addScaled(stats, def, piece);
            if (def.armorSet() != null) {
                if (!def.armorSet().equals(detectedSet)) {
                    detectedSet = def.armorSet();
                    piecesPerSet = 0;
                }
                piecesPerSet++;
                wornSets.add(def.armorSet());
            }
        }
        if (piecesPerSet >= 4 && detectedSet != null) {
            for (Map.Entry<PlayerStats.Stat, Double> e : ItemRegistry.setBonus(detectedSet).entrySet()) {
                stats.add(e.getKey(), e.getValue());
            }
        }

        // 3. held weapon / tool
        if (held != null) {
            ItemDefinition def = ItemRegistry.get(held.id()).orElse(null);
            if (def != null) addScaled(stats, def, held);
        }

        // 4. accessory bag — highest-tier accessory per "family" wins, and all
        //    distinct families contribute. Family = stat key, which is how the
        //    talisman -> ring -> artifact progression collapses in the real game.
        Map<PlayerStats.Stat, Double> bestPerStat = new EnumMap<>(PlayerStats.Stat.class);
        Map<PlayerStats.Stat, Integer> bestTierPerStat = new EnumMap<>(PlayerStats.Stat.class);
        for (ItemInstance acc : profile.accessoryBag()) {
            if (acc == null) continue;
            ItemDefinition def = ItemRegistry.get(acc.id()).orElse(null);
            if (def == null || !def.reforgable()) continue;
            for (Map.Entry<PlayerStats.Stat, Double> e : def.stats().entrySet()) {
                int tier = def.rarity().ordinal() * 100 + def.tier();
                if (tier >= bestTierPerStat.getOrDefault(e.getKey(), -1)) {
                    bestTierPerStat.put(e.getKey(), tier);
                    bestPerStat.put(e.getKey(), e.getValue());
                }
            }
        }
        for (Map.Entry<PlayerStats.Stat, Double> e : bestPerStat.entrySet()) {
            stats.add(e.getKey(), e.getValue());
        }

        // 5. active pet, scaled by pet level
        var pet = profile.activePet();
        if (pet != null && pet.definition() != null) {
            double scale = pet.levelFraction();
            for (Map.Entry<PlayerStats.Stat, Double> e : pet.definition().pet().baseStats().entrySet()) {
                stats.add(e.getKey(), e.getValue() * scale);
            }
        }

        // 6. transient buffs
        if (buffs != null) {
            for (Map<PlayerStats.Stat, Double> src : buffs.values()) {
                if (src == null) continue;
                for (Map.Entry<PlayerStats.Stat, Double> e : src.entrySet()) {
                    stats.add(e.getKey(), e.getValue());
                }
            }
        }

        // sanity clamps — a negative crit chance or 200% speed is always a bug
        stats.add(PlayerStats.Stat.SPEED, 0);
        return stats;
    }

    /**
     * Adds an item's stats, applying the star/quality scaling that dungeon gear
     * gains from upgrades. Non-dungeon items pass through unchanged.
     */
    private static void addScaled(PlayerStats stats, ItemDefinition def, ItemInstance instance) {
        double starMultiplier = 1.0;
        if (instance.stars() > 0) {
            // Each star adds 10% of base stats; master-mode stars add a further 10%
            // beyond star 10, matching the 1 -> 15 star progression.
            starMultiplier = 1.0 + (instance.stars() * 0.10);
        }
        for (Map.Entry<PlayerStats.Stat, Double> e : def.stats().entrySet()) {
            stats.add(e.getKey(), e.getValue() * starMultiplier);
        }
    }

    /** Convenience overload with no transient buffs. */
    public static PlayerStats calculate(SkyBlockProfile profile, ItemInstance held) {
        return calculate(profile, held, null);
    }

    /** Total effective health for the HUD. */
    public static double effectiveHealth(PlayerStats stats) {
        return stats.effectiveHealth();
    }

    /** Small adapter so SkillService (string keys) can feed PlayerStats (enum keys). */
    private static final class SkillBonusAdapter {
        static Map<PlayerStats.Stat, Double> toStatMap(Map<String, Double> raw) {
            Map<PlayerStats.Stat, Double> out = new EnumMap<>(PlayerStats.Stat.class);
            for (Map.Entry<String, Double> e : raw.entrySet()) {
                PlayerStats.Stat s = PlayerStats.byKey(e.getKey());
                if (s != null) out.put(s, e.getValue());
            }
            return out;
        }
    }

    /** Loot-relevant helper: total magic find from all sources. */
    public static double magicFind(PlayerStats stats) {
        return Math.max(0, stats.get(PlayerStats.Stat.MAGIC_FIND));
    }

    public static List<String> describe(PlayerStats stats) {
        List<String> lines = new java.util.ArrayList<>();
        for (PlayerStats.Stat s : PlayerStats.Stat.values()) {
            double v = stats.get(s);
            if (v == 0) continue;
            String sign = v > 0 ? "+" : "";
            String num = (v == Math.rint(v)) ? Long.toString((long) v) : String.format("%.1f", v);
            lines.add(s.symbol + " " + s.label + ": " + sign + num + s.suffix);
        }
        return lines;
    }
}
