package com.corot2b.api.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One SkyBlock profile. A player may own several; exactly one is {@code selected}
 * per account (enforced by a partial unique index in the schema).
 *
 * <p>Mutable in memory, flushed by the async writer in skyblock-core. The
 * {@link #version} field is an optimistic lock: a save that finds a newer row in the
 * database loses, which is what keeps two servers sharing one database from
 * silently clobbering each other.
 */
public final class SkyBlockProfile {

    private final UUID profileId;
    private final UUID ownerUuid;
    private String name;
    private boolean selected;
    private long coinsCents;
    private long bankCents;
    private int fairySouls;
    private int version;

    private final Map<String, Long> skillXp = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Long> collections = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, QuestProgress> quests = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Integer> achievements = new java.util.concurrent.ConcurrentHashMap<>();

    private UUID islandId;
    private final List<ItemInstance> inventory = new ArrayList<>(36);
    private final ItemInstance[] armor = new ItemInstance[4];
    private final List<ItemInstance> accessoryBag = new ArrayList<>();
    private final List<PetInstance> pets = new ArrayList<>();
    private UUID activePetId;

    /** Free-form bag for module-owned state (minions, forge, commissions, wardrobe...). */
    private final Map<String, Object> data = new java.util.concurrent.ConcurrentHashMap<>();

    private long lastSeen = System.currentTimeMillis();

    public SkyBlockProfile(UUID profileId, UUID ownerUuid, String name) {
        this.profileId = profileId;
        this.ownerUuid = ownerUuid;
        this.name = name;
        for (int i = 0; i < 36; i++) inventory.add(null);
    }

    // -------------------------------------------------------------- identity
    public UUID profileId() { return profileId; }
    public UUID ownerUuid() { return ownerUuid; }
    public String name() { return name; }
    public void name(String name) { this.name = name; }
    public boolean selected() { return selected; }
    public void selected(boolean selected) { this.selected = selected; }
    public long lastSeen() { return lastSeen; }
    public void lastSeen(long t) { this.lastSeen = t; }

    // ----------------------------------------------------------------- money
    public long coinsCents() { return coinsCents; }
    public long bankCents() { return bankCents; }

    /**
     * Applies a signed coin delta. Returns false and changes nothing if it would
     * drive the balance negative — callers must branch on the result rather than
     * trusting the mutation.
     */
    public boolean addCoins(long deltaCents) {
        if (deltaCents == 0) return true;
        long next = coinsCents + deltaCents;
        if (next < 0) return false;
        coinsCents = next;
        return true;
    }

    public boolean addBank(long deltaCents) {
        if (deltaCents == 0) return true;
        long next = bankCents + deltaCents;
        if (next < 0) return false;
        bankCents = next;
        return true;
    }

    /**
     * Direct setters used only by the persistence layer when hydrating a row.
     * Named distinctly from the delta methods so gameplay code cannot reach for
     * them by accident and bypass the negative-balance guard.
     */
    public void loadCoins(long cents) { this.coinsCents = Math.max(0, cents); }
    public void loadBank(long cents) { this.bankCents = Math.max(0, cents); }

    public int fairySouls() { return fairySouls; }
    public void fairySouls(int v) { this.fairySouls = Math.max(0, v); }

    // ------------------------------------------------------------ optimistic lock
    public int version() { return version; }
    public void version(int v) { this.version = v; }

    // ---------------------------------------------------------------- skills
    public long skillXp(String skill) { return skillXp.getOrDefault(skill.toLowerCase(), 0L); }
    public void skillXp(String skill, long xp) { skillXp.put(skill.toLowerCase(), Math.max(0, xp)); }
    public Map<String, Long> skills() { return skillXp; }

    // ----------------------------------------------------------- collections
    public long collection(String itemId) { return collections.getOrDefault(itemId, 0L); }

    public long addCollection(String itemId, long amount) {
        if (amount <= 0) return collection(itemId);
        return collections.merge(itemId, amount, Long::sum);
    }

    public Map<String, Long> collections() { return collections; }

    // ---------------------------------------------------------------- quests
    public QuestProgress quest(String questId) {
        return quests.computeIfAbsent(questId, k -> new QuestProgress(k));
    }

    public Map<String, QuestProgress> quests() { return quests; }

    // ---------------------------------------------------------- achievements
    public int achievementTier(String id) { return achievements.getOrDefault(id, 0); }
    public void achievementTier(String id, int tier) { achievements.put(id, tier); }
    public Map<String, Integer> achievements() { return achievements; }

    // -------------------------------------------------------------- inventory
    public List<ItemInstance> inventory() { return inventory; }
    public ItemInstance[] armor() { return armor; }
    public List<ItemInstance> accessoryBag() { return accessoryBag; }
    public List<PetInstance> pets() { return pets; }
    public UUID activePetId() { return activePetId; }
    public void activePetId(UUID id) { this.activePetId = id; }
    public UUID islandId() { return islandId; }
    public void islandId(UUID id) { this.islandId = id; }
    public Map<String, Object> data() { return data; }

    @SuppressWarnings("unchecked")
    public <T> T data(String key, T fallback) {
        Object v = data.get(key);
        return v == null ? fallback : (T) v;
    }

    public PetInstance activePet() {
        if (activePetId == null) return null;
        for (PetInstance p : pets) if (p.petId().equals(activePetId)) return p;
        return null;
    }

    /**
     * Mutable quest state. Objectives are keyed by objective id; a quest is complete
     * when every objective's progress meets its target.
     */
    public static final class QuestProgress {
        private final String questId;
        private final Map<String, Long> objectives = new java.util.LinkedHashMap<>();
        private String state = "active";
        private long startedAt = System.currentTimeMillis();
        private Long finishedAt;

        public QuestProgress(String questId) { this.questId = questId; }

        public String questId() { return questId; }
        public String state() { return state; }
        public void state(String state) { this.state = state; }
        public long startedAt() { return startedAt; }
        public void startedAt(long t) { this.startedAt = t; }
        public Long finishedAt() { return finishedAt; }
        public void finishedAt(Long t) { this.finishedAt = t; }
        public Map<String, Long> objectives() { return objectives; }

        public long progress(String objectiveId) { return objectives.getOrDefault(objectiveId, 0L); }

        public void setProgress(String objectiveId, long value) { objectives.put(objectiveId, value); }

        public long increment(String objectiveId, long by) {
            return objectives.merge(objectiveId, by, Long::sum);
        }
    }
}
