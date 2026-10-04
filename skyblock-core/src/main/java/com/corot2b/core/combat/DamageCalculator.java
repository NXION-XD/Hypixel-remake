package com.corot2b.core.combat;

import com.corot2b.api.model.PlayerStats;

import java.util.List;
import java.util.Random;

/**
 * The damage model. Written as pure static functions with an injectable RNG so it
 * can be unit-tested deterministically without a running server.
 *
 * <pre>
 *   melee  = (5 + weaponDamage)
 *          * (1 + strength / 5)
 *          * (crit ? 1 + critDamage/100 : 1)
 *          * (1 + damagePercent / 100)
 *          * product(additive multipliers)
 *          * product(multiplicative multipliers)
 *          * (1 - defense / (defense + 100))
 *
 *   ehp    = health * (1 + defense / 100)
 *   ability= base * (1 + intelligence / 100)
 * </pre>
 */
public final class DamageCalculator {

    public static final double BASE_FLAT_DAMAGE = 5.0;
    public static final double STRENGTH_DIVISOR = 5.0;
    public static final double DEFENSE_DIVISOR = 100.0;
    public static final double INTELLIGENCE_DIVISOR = 100.0;

    private DamageCalculator() {}

    public record HitResult(double damage, boolean crit, double critChance,
                            double beforeReduction, double reductionFraction) {}

    /** Resolves one melee hit. {@code forceCrit} may be null for a natural roll. */
    public static HitResult melee(PlayerStats attacker,
                                  PlayerStats defender,
                                  double weaponDamage,
                                  Boolean forceCrit,
                                  List<Double> multipliers,
                                  Random rng) {
        double critChance = clamp(attacker.get(PlayerStats.Stat.CRIT_CHANCE), 0, 100);
        boolean crit = forceCrit != null ? forceCrit : (rng.nextDouble() * 100) < critChance;

        double dmg = (BASE_FLAT_DAMAGE + Math.max(0, weaponDamage))
                * (1 + attacker.get(PlayerStats.Stat.STRENGTH) / STRENGTH_DIVISOR);
        if (crit) {
            dmg *= 1 + attacker.get(PlayerStats.Stat.CRIT_DAMAGE) / 100.0;
        }
        dmg *= 1 + attacker.get(PlayerStats.Stat.DAMAGE_PERCENT) / 100.0;
        if (multipliers != null) {
            for (double m : multipliers) dmg *= m;
        }

        double defense = defender == null ? 0 : Math.max(0, defender.get(PlayerStats.Stat.DEFENSE));
        double reduction = defense / (defense + DEFENSE_DIVISOR);
        dmg *= 1 - reduction;

        return new HitResult(Math.max(1, Math.round(dmg)), crit, critChance, dmg / (1 - reduction), reduction);
    }

    public static HitResult melee(PlayerStats attacker, PlayerStats defender, double weaponDamage, Random rng) {
        return melee(attacker, defender, weaponDamage, null, null, rng);
    }

    /** Deterministic variant used by tests: crit decided by the supplied roll. */
    public static HitResult meleeDeterministic(PlayerStats attacker,
                                               PlayerStats defender,
                                               double weaponDamage,
                                               double critRollPercent,
                                               List<Double> multipliers) {
        double critChance = clamp(attacker.get(PlayerStats.Stat.CRIT_CHANCE), 0, 100);
        return melee(attacker, defender, weaponDamage, critRollPercent < critChance, multipliers, new Random(0));
    }

    /** Ability damage scales off intelligence. */
    public static double ability(double baseDamage, PlayerStats caster, double multiplier) {
        double intelligence = Math.max(0, caster.get(PlayerStats.Stat.INTELLIGENCE));
        return Math.max(1, Math.round(baseDamage * (1 + intelligence / INTELLIGENCE_DIVISOR) * multiplier));
    }

    /** Damage a defender actually takes after defense reduction. */
    public static double reduce(double incoming, PlayerStats defender) {
        double defense = defender == null ? 0 : Math.max(0, defender.get(PlayerStats.Stat.DEFENSE));
        return incoming * (1 - defense / (defense + DEFENSE_DIVISOR));
    }

    public static double effectiveHealth(PlayerStats stats) {
        return stats.effectiveHealth();
    }

    /**
     * Ferocity grants extra attacks: each 100% is one guaranteed extra hit, with the
     * remainder rolled. Returns the number of additional hits.
     */
    public static int ferocityStrikes(double ferocity, Random rng) {
        if (ferocity <= 0) return 0;
        double f = ferocity / 100.0;
        int guaranteed = (int) Math.floor(f);
        return guaranteed + (rng.nextDouble() < (f - guaranteed) ? 1 : 0);
    }

    /**
     * Fortune rolls for gathering skills: each 100 fortune is one guaranteed extra
     * drop, the fractional part is rolled.
     */
    public static int fortuneRolls(double fortune, Random rng) {
        if (fortune <= 0) return 0;
        double f = fortune / 100.0;
        int guaranteed = (int) Math.floor(f);
        return guaranteed + (rng.nextDouble() < (f - guaranteed) ? 1 : 0);
    }

    /** Mana cost after intelligence-based reductions. */
    public static double manaCost(double baseCost, PlayerStats caster) {
        return Math.max(0, baseCost);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
