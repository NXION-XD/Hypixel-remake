package com.corot2b.api.model;

import java.util.Objects;
import java.util.UUID;

/**
 * A pet owned by a profile. Kept in the API module because GUI, gameplay and the
 * web dashboard all need to render pet state.
 *
 * <p>Pet XP uses the same 1-100 curve as the live game's pet levelling: a pet gains
 * XP from the skill it belongs to, and its stats interpolate linearly from base at
 * level 1 to full at level 100.
 */
public final class PetInstance {

    /** XP required to go from level n to n+1, for n = 1..99. */
    private static final long[] PET_XP = new long[100];

    static {
        // Levels 1-15 ramp gently, then it becomes a long grind to 100.
        long acc = 100;
        for (int i = 1; i < PET_XP.length; i++) {
            PET_XP[i - 1] = acc;
            if (i < 15) acc += 100 + i * 50L;
            else if (i < 50) acc += 1000 + (i - 15) * 200L;
            else acc += 8000 + (i - 50) * 500L;
        }
    }

    public static final int MAX_LEVEL = 100;

    private final UUID petId;
    private final String type;
    private Rarity rarity;
    private long xp;
    private String heldItem;
    private boolean active;

    /** Resolved lazily so the API module never depends on the content registry. */
    private transient Object definitionRef;

    public PetInstance(UUID petId, String type, Rarity rarity) {
        this.petId = Objects.requireNonNull(petId);
        this.type = Objects.requireNonNull(type);
        this.rarity = rarity == null ? Rarity.COMMON : rarity;
    }

    public UUID petId() { return petId; }
    public String type() { return type; }
    public Rarity rarity() { return rarity; }
    public long xp() { return xp; }
    public String heldItem() { return heldItem; }
    public boolean active() { return active; }

    public void rarity(Rarity r) { this.rarity = r == null ? Rarity.COMMON : r; }
    public void heldItem(String item) { this.heldItem = item; }
    public void active(boolean a) { this.active = a; }

    /** Adds XP and returns every level crossed. */
    public int[] addXp(long amount) {
        if (amount <= 0) return new int[0];
        int before = level();
        xp = Math.max(0, xp + amount);
        int after = level();
        if (after <= before) return new int[0];
        int[] crossed = new int[after - before];
        for (int i = 0; i < crossed.length; i++) crossed[i] = before + 1 + i;
        return crossed;
    }

    public int level() {
        int level = 1;
        long acc = 0;
        for (int i = 1; i < MAX_LEVEL; i++) {
            long need = PET_XP[i - 1];
            if (acc + need > xp) break;
            acc += need;
            level = i + 1;
        }
        return level;
    }

    public boolean maxed() { return level() >= MAX_LEVEL; }

    /** Progress fraction 0..1 from level 1 (0.0) to level 100 (1.0), used to scale stats. */
    public double levelFraction() {
        return (level() - 1) / (double) (MAX_LEVEL - 1);
    }

    public long xpIntoLevel() {
        long acc = 0;
        for (int i = 1; i < level(); i++) acc += PET_XP[i - 1];
        return xp - acc;
    }

    public long xpForNextLevel() {
        int l = level();
        return l >= MAX_LEVEL ? 0 : PET_XP[l - 1];
    }

    // The stat engine needs the registry definition; the API module only carries it
    // opaquely so skyblock-api stays free of a dependency on skyblock-core.
    public void definition(Object def) { this.definitionRef = def; }

    @SuppressWarnings("unchecked")
    public <T> T definition() { return (T) definitionRef; }

    @Override
    public boolean equals(Object o) {
        return o instanceof PetInstance other && petId.equals(other.petId);
    }

    @Override
    public int hashCode() { return petId.hashCode(); }
}
