package com.corot2b.core.content;

import com.corot2b.api.model.PlayerStats;
import com.corot2b.api.model.Rarity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable definition of an item type: its base stats, category, price and lore.
 *
 * <p>Definitions live in code ({@link ItemRegistry}) rather than in data files so
 * they are type-checked, refactorable and unit-testable. Anything an operator needs
 * to tune at runtime (drop rates, taxes, XP) belongs in config.yml instead.
 */
public final class ItemDefinition {

    public enum Category {
        WEAPON, TOOL, ARMOR, ACCESSORY, MATERIAL, FOOD, CONSUMABLE, MINION, PET, PET_ITEM,
        BLOCK, QUEST, SPECIAL
    }

    private final String id;
    private final String name;
    private final Category category;
    private final Rarity rarity;
    private final int maxStack;
    private final long npcValue;
    private final Map<PlayerStats.Stat, Double> stats;
    private final List<String> lore;
    private final List<String> tags;
    private final Ability ability;
    private final boolean reforgable;
    private final boolean enchantable;
    private final String collectionId;
    private final String armorSlot;
    private final String armorSet;
    private final String toolKind;
    private final String skill;
    private final int tier;
    private final MinionInfo minion;
    private final PetInfo pet;
    private final int dungeonFloor;
    private final boolean consumable;

    private ItemDefinition(Builder b) {
        this.id = b.id;
        this.name = b.name;
        this.category = b.category;
        this.rarity = b.rarity;
        this.maxStack = b.maxStack;
        this.npcValue = b.npcValue;
        this.stats = Collections.unmodifiableMap(new EnumMap<>(b.stats));
        this.lore = List.copyOf(b.lore);
        this.tags = List.copyOf(b.tags);
        this.ability = b.ability;
        this.reforgable = b.reforgable;
        this.enchantable = b.enchantable;
        this.collectionId = b.collectionId;
        this.armorSlot = b.armorSlot;
        this.armorSet = b.armorSet;
        this.toolKind = b.toolKind;
        this.skill = b.skill;
        this.tier = b.tier;
        this.minion = b.minion;
        this.pet = b.pet;
        this.dungeonFloor = b.dungeonFloor;
        this.consumable = b.consumable;
    }

    public String id() { return id; }
    public String name() { return name; }
    public Category category() { return category; }
    public Rarity rarity() { return rarity; }
    public int maxStack() { return maxStack; }
    public long npcValue() { return npcValue; }
    public Map<PlayerStats.Stat, Double> stats() { return stats; }
    public List<String> lore() { return lore; }
    public List<String> tags() { return tags; }
    public Ability ability() { return ability; }
    public boolean hasAbility() { return ability != null; }
    public boolean reforgable() { return reforgable; }
    public boolean enchantable() { return enchantable; }
    public String collectionId() { return collectionId; }
    public String armorSlot() { return armorSlot; }
    public String armorSet() { return armorSet; }
    public String toolKind() { return toolKind; }
    public String skill() { return skill; }
    public int tier() { return tier; }
    public MinionInfo minion() { return minion; }
    public PetInfo pet() { return pet; }
    public int dungeonFloor() { return dungeonFloor; }
    public boolean consumable() { return consumable; }

    public boolean isArmor() { return category == Category.ARMOR; }
    public boolean isWeapon() { return category == Category.WEAPON; }
    public boolean isTool() { return category == Category.TOOL; }
    public boolean hasTag(String tag) { return tags.contains(tag); }

    public static Builder builder(String id, String name) {
        return new Builder(id, name);
    }

    /** A right-click ability. {@code multiplier} scales off intelligence when > 0. */
    public record Ability(String name, int manaCost, double cooldownSeconds, String description,
                          double baseDamage, double multiplier) {
        public Ability(String name, int manaCost, double cooldownSeconds, String description) {
            this(name, manaCost, cooldownSeconds, description, 0, 0);
        }
    }

    public record MinionInfo(String type, int tier, String skill) {}

    public record PetInfo(String type, String skill, Map<PlayerStats.Stat, Double> baseStats) {}

    public static final class Builder {
        private final String id;
        private final String name;
        private Category category = Category.MATERIAL;
        private Rarity rarity = Rarity.COMMON;
        private int maxStack = 64;
        private long npcValue = 1;
        private final Map<PlayerStats.Stat, Double> stats = new EnumMap<>(PlayerStats.Stat.class);
        private final List<String> lore = new ArrayList<>();
        private final List<String> tags = new ArrayList<>();
        private Ability ability;
        private boolean reforgable;
        private boolean enchantable;
        private String collectionId;
        private String armorSlot;
        private String armorSet;
        private String toolKind;
        private String skill;
        private int tier;
        private MinionInfo minion;
        private PetInfo pet;
        private int dungeonFloor;
        private boolean consumable;

        private Builder(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public Builder category(Category c) { this.category = c; return this; }
        public Builder rarity(Rarity r) { this.rarity = r; return this; }
        public Builder maxStack(int n) { this.maxStack = Math.max(1, n); return this; }
        public Builder value(long cents) { this.npcValue = Math.max(0, cents); return this; }

        public Builder stat(PlayerStats.Stat s, double v) { this.stats.put(s, v); return this; }

        /** Accepts loose string keys so tier tables can be written compactly. */
        public Builder stat(String key, double v) {
            PlayerStats.Stat s = PlayerStats.byKey(key);
            if (s != null) this.stats.put(s, v);
            return this;
        }

        public Builder lore(String... lines) { Collections.addAll(this.lore, lines); return this; }
        public Builder tags(String... t) { Collections.addAll(this.tags, t); return this; }
        public Builder ability(Ability a) { this.ability = a; return this; }
        public Builder reforgable(boolean v) { this.reforgable = v; return this; }
        public Builder enchantable(boolean v) { this.enchantable = v; return this; }
        public Builder collection(String id) { this.collectionId = id; return this; }
        public Builder armorSlot(String slot) { this.armorSlot = slot; return this; }
        public Builder armorSet(String set) { this.armorSet = set; return this; }
        public Builder toolKind(String kind) { this.toolKind = kind; return this; }
        public Builder skill(String skill) { this.skill = skill; return this; }
        public Builder tier(int tier) { this.tier = tier; return this; }
        public Builder minion(MinionInfo m) { this.minion = m; return this; }
        public Builder pet(PetInfo p) { this.pet = p; return this; }
        public Builder dungeonFloor(int floor) { this.dungeonFloor = floor; return this; }
        public Builder consumable(boolean v) { this.consumable = v; return this; }

        /** Multiplies every already-set stat by a factor — used for armor piece weights. */
        public Builder scaleStats(double factor) {
            for (Map.Entry<PlayerStats.Stat, Double> e : stats.entrySet()) {
                stats.put(e.getKey(), e.getValue() * factor);
            }
            return this;
        }

        public ItemDefinition build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("item id required");
            if (maxStack == 1 && category != Category.MATERIAL) {
                // single-stack items are the norm for gear; nothing to do, kept explicit
            }
            return new ItemDefinition(this);
        }
    }

    /** Convenience for tier tables: copies stats from a map of loose keys. */
    public static Map<PlayerStats.Stat, Double> statMap(Map<String, Double> raw) {
        Map<PlayerStats.Stat, Double> out = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : raw.entrySet()) {
            PlayerStats.Stat s = PlayerStats.byKey(e.getKey());
            if (s != null) out.put(s, e.getValue());
        }
        return out;
    }
}
