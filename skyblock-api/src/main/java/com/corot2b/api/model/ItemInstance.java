package com.corot2b.api.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A single stack of a SkyBlock item.
 *
 * <p>Deliberately a plain value object with short JSON keys: it round-trips through
 * JSONB, through the web API, and into a Bukkit {@code ItemStack}'s
 * PersistentDataContainer without losing anything. The {@link #uid()} is what makes
 * duplicate detection possible — two visually identical items still differ by uid.
 *
 * <p>Attribute keys are abbreviated because inventories are stored per-profile and
 * the difference is measurable at scale:
 * <pre>
 *   n = amount          e = enchantments     r = reforge
 *   s = stars           q = dungeon quality  g = gemstones
 *   hpb = hot potato book level             d = arbitrary extra data
 * </pre>
 */
public final class ItemInstance {

    private final String uid;
    private final String id;
    private int amount;
    private Map<String, Integer> enchantments;
    private String reforge;
    private int stars;
    private int quality;
    private Map<String, String> gemstones;
    private int hotPotatoLevel;
    private Map<String, Object> data;
    private final long createdAt;

    private ItemInstance(String uid, String id, int amount, long createdAt) {
        this.uid = Objects.requireNonNull(uid, "uid");
        this.id = Objects.requireNonNull(id, "id");
        this.amount = Math.max(1, amount);
        this.createdAt = createdAt;
    }

    public static ItemInstance of(String id, int amount) {
        return new ItemInstance(newUid(), id, amount, System.currentTimeMillis());
    }

    public static ItemInstance withUid(String uid, String id, int amount, long createdAt) {
        return new ItemInstance(uid, id, amount, createdAt);
    }

    private static long COUNTER = 0;

    public static synchronized String newUid() {
        COUNTER = (COUNTER + 1) % 1_000_000L;
        return Long.toString(System.currentTimeMillis(), 36)
                + Long.toString(COUNTER, 36)
                + UUID.randomUUID().toString().substring(0, 8);
    }

    // ------------------------------------------------------------ fluent setters
    public ItemInstance enchant(String key, int level) {
        if (enchantments == null) enchantments = new LinkedHashMap<>();
        enchantments.put(key, level);
        return this;
    }

    public ItemInstance reforge(String reforge) {
        this.reforge = reforge;
        return this;
    }

    public ItemInstance stars(int stars) {
        this.stars = Math.max(0, Math.min(15, stars));
        return this;
    }

    public ItemInstance quality(int quality) {
        this.quality = Math.max(1, Math.min(10, quality));
        return this;
    }

    public ItemInstance gem(String slot, String gem) {
        if (gemstones == null) gemstones = new LinkedHashMap<>();
        gemstones.put(slot, gem);
        return this;
    }

    public ItemInstance hotPotato(int level) {
        this.hotPotatoLevel = level;
        return this;
    }

    public ItemInstance data(String key, Object value) {
        if (data == null) data = new LinkedHashMap<>();
        data.put(key, value);
        return this;
    }

    // ------------------------------------------------------------------ getters
    public String uid() { return uid; }
    public String id() { return id; }
    public int amount() { return amount; }
    public long createdAt() { return createdAt; }
    public Map<String, Integer> enchantments() {
        return enchantments == null ? Collections.emptyMap() : Collections.unmodifiableMap(enchantments);
    }
    public String reforge() { return reforge; }
    public int stars() { return stars; }
    public int quality() { return quality; }
    public Map<String, String> gemstones() {
        return gemstones == null ? Collections.emptyMap() : Collections.unmodifiableMap(gemstones);
    }
    public int hotPotatoLevel() { return hotPotatoLevel; }
    public Map<String, Object> data() {
        return data == null ? Collections.emptyMap() : Collections.unmodifiableMap(data);
    }

    public void amount(int amount) {
        this.amount = Math.max(1, amount);
    }

    /** True when the stack carries any attribute that forbids merging. */
    public boolean hasAttributes() {
        return (enchantments != null && !enchantments.isEmpty())
                || reforge != null
                || stars > 0
                || quality > 0
                || (gemstones != null && !gemstones.isEmpty())
                || hotPotatoLevel > 0
                || (data != null && !data.isEmpty());
    }

    /** Deep copy — inventories are mutated constantly and must never alias. */
    public ItemInstance copy() {
        ItemInstance c = new ItemInstance(uid, id, amount, createdAt);
        if (enchantments != null) c.enchantments = new LinkedHashMap<>(enchantments);
        c.reforge = reforge;
        c.stars = stars;
        c.quality = quality;
        if (gemstones != null) c.gemstones = new LinkedHashMap<>(gemstones);
        c.hotPotatoLevel = hotPotatoLevel;
        if (data != null) c.data = new LinkedHashMap<>(data);
        return c;
    }

    /**
     * Canonical fingerprint of everything except uid/creation time. Two stacks with
     * the same fingerprint are interchangeable, which is what the auction house uses
     * to stop the same physical item being listed twice.
     */
    public String fingerprint() {
        StringBuilder sb = new StringBuilder(64);
        sb.append(id).append('#').append(amount);
        if (reforge != null) sb.append("|r=").append(reforge);
        sb.append("|s=").append(stars).append("|q=").append(quality).append("|h=").append(hotPotatoLevel);
        if (enchantments != null && !enchantments.isEmpty()) {
            enchantments.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> sb.append('|').append(e.getKey()).append('=').append(e.getValue()));
        }
        if (gemstones != null && !gemstones.isEmpty()) {
            gemstones.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> sb.append('|').append(e.getKey()).append('~').append(e.getValue()));
        }
        if (data != null && !data.isEmpty()) {
            data.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> sb.append('|').append(e.getKey()).append(':').append(e.getValue()));
        }
        return Integer.toHexString(sb.toString().hashCode()) + "-" + sb.length();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ItemInstance other && uid.equals(other.uid);
    }

    @Override
    public int hashCode() {
        return uid.hashCode();
    }

    @Override
    public String toString() {
        return "ItemInstance{" + id + " x" + amount + (reforge != null ? " " + reforge : "") + "}";
    }
}
