package com.corot2b.api.model;

/**
 * Item rarities in ascending order. Ordinal is used for upgrade maths
 * (recombobulators, dungeon stars, pet cores) so order matters.
 */
public enum Rarity {
    COMMON("Common", 0xFFFFFF),
    UNCOMMON("Uncommon", 0x55FF55),
    RARE("Rare", 0x5555FF),
    EPIC("Epic", 0xAA00AA),
    LEGENDARY("Legendary", 0xFFAA00),
    MYTHIC("Mythic", 0xFF55FF),
    DIVINE("Divine", 0x55FFFF),
    SPECIAL("Special", 0xFF5555),
    VERY_SPECIAL("Very Special", 0xFF5555),
    ULTIMATE("Ultimate", 0xFF5555);

    private final String label;
    private final int rgb;

    Rarity(String label, int rgb) {
        this.label = label;
        this.rgb = rgb;
    }

    public String label() {
        return label;
    }

    public int rgb() {
        return rgb;
    }

    /** Minecraft legacy colour code used for lore/display names. */
    public char legacyColor() {
        return switch (this) {
            case COMMON -> 'f';
            case UNCOMMON -> 'a';
            case RARE -> '9';
            case EPIC -> '5';
            case LEGENDARY -> '6';
            case MYTHIC -> 'd';
            case DIVINE -> 'b';
            case SPECIAL, VERY_SPECIAL, ULTIMATE -> 'c';
        };
    }

    public Rarity up(int steps) {
        int i = Math.min(ordinal() + steps, values().length - 1);
        return values()[i];
    }

    public Rarity down(int steps) {
        int i = Math.max(ordinal() - steps, 0);
        return values()[i];
    }

    public boolean atLeast(Rarity other) {
        return ordinal() >= other.ordinal();
    }

    public static Rarity fromString(String s) {
        if (s == null) return COMMON;
        try {
            return valueOf(s.toUpperCase().replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            return COMMON;
        }
    }
}
