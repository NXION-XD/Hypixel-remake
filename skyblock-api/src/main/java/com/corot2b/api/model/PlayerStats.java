package com.corot2b.api.model;

import java.util.EnumMap;
import java.util.Map;

/**
 * Every numeric stat a player can have. Enum-keyed so aggregation is a tight loop
 * with no string hashing, which matters because stats are recomputed on every
 * inventory change, level up and potion tick.
 */
public final class PlayerStats {

    public enum Stat {
        HEALTH("Health", "❤"),
        DEFENSE("Defense", "❈"),
        STRENGTH("Strength", "❁"),
        DAMAGE("Damage", "⚔"),
        CRIT_CHANCE("Crit Chance", "☣", "%"),
        CRIT_DAMAGE("Crit Damage", "☠", "%"),
        INTELLIGENCE("Intelligence", "✎"),
        SPEED("Speed", "✦"),
        ATTACK_SPEED("Attack Speed", "⚔", "%"),
        MAGIC_FIND("Magic Find", "✯", "%"),
        PET_LUCK("Pet Luck", "♣", "%"),
        MINING_SPEED("Mining Speed", "⸕"),
        MINING_FORTUNE("Mining Fortune", "☘", "%"),
        FARMING_FORTUNE("Farming Fortune", "☘", "%"),
        FORAGING_FORTUNE("Foraging Fortune", "☘", "%"),
        FISHING_SPEED("Fishing Speed", "⚓"),
        SEA_CREATURE_CHANCE("Sea Creature Chance", "α", "%"),
        HEALTH_REGEN("Health Regen", "❣", "%"),
        MANA_REGEN("Mana Regen", "✎", "%"),
        DAMAGE_PERCENT("Damage", "⚔", "%"),
        TRUE_DEFENSE("True Defense", "❂"),
        FEROCITY("Ferocity", "⫽"),
        SWAP_WAIT("Swap Wait", "⇄"),
        WISDOM("Wisdom", "☯");

        public final String label;
        public final String symbol;
        public final String suffix;

        Stat(String label, String symbol) { this(label, symbol, ""); }
        Stat(String label, String symbol, String suffix) {
            this.label = label; this.symbol = symbol; this.suffix = suffix;
        }
    }

    private final EnumMap<Stat, Double> values = new EnumMap<>(Stat.class);

    public PlayerStats() {
        // SkyBlock's baseline: 100 health, 30% crit chance, 50% crit damage.
        values.put(Stat.HEALTH, 100.0);
        values.put(Stat.CRIT_CHANCE, 30.0);
        values.put(Stat.CRIT_DAMAGE, 50.0);
        values.put(Stat.SPEED, 100.0);
        values.put(Stat.HEALTH_REGEN, 100.0);
        values.put(Stat.MANA_REGEN, 100.0);
    }

    public double get(Stat s) { return values.getOrDefault(s, 0.0); }

    public PlayerStats add(Stat s, double v) {
        values.merge(s, v, Double::sum);
        return this;
    }

    public PlayerStats addAll(Map<String, Double> byName) {
        if (byName == null) return this;
        for (Map.Entry<String, Double> e : byName.entrySet()) {
            Stat s = byKey(e.getKey());
            if (s != null && e.getValue() != null) add(s, e.getValue());
        }
        return this;
    }

    public PlayerStats merge(PlayerStats other) {
        if (other == null) return this;
        for (Map.Entry<Stat, Double> e : other.values.entrySet()) add(e.getKey(), e.getValue());
        return this;
    }

    public PlayerStats copy() {
        PlayerStats c = new PlayerStats();
        c.values.clear();
        c.values.putAll(this.values);
        return c;
    }

    /** Effective health = health * (1 + defense/100). */
    public double effectiveHealth() {
        return get(Stat.HEALTH) * (1 + Math.max(0, get(Stat.DEFENSE)) / 100.0);
    }

    /** Fraction of incoming damage absorbed by defense: defense / (defense + 100). */
    public double damageReduction() {
        double d = Math.max(0, get(Stat.DEFENSE));
        return d / (d + 100.0);
    }

    public double critChanceClamped() {
        return Math.max(0, Math.min(100, get(Stat.CRIT_CHANCE)));
    }

    public Map<Stat, Double> asMap() { return values; }

    /** Maps loose config/JSON stat keys onto the enum, tolerating both naming styles. */
    public static Stat byKey(String key) {
        if (key == null) return null;
        String norm = key.trim().toLowerCase().replace(' ', '_').replace('-', '_');
        for (Stat s : Stat.values()) {
            if (s.name().toLowerCase().equals(norm)) return s;
        }
        return switch (norm) {
            case "hp", "max_health" -> Stat.HEALTH;
            case "def" -> Stat.DEFENSE;
            case "str" -> Stat.STRENGTH;
            case "crit_chance" -> Stat.CRIT_CHANCE;
            case "crit_damage" -> Stat.CRIT_DAMAGE;
            case "int", "intelligence_bonus" -> Stat.INTELLIGENCE;
            case "sea_creature" -> Stat.SEA_CREATURE_CHANCE;
            default -> null;
        };
    }
}
