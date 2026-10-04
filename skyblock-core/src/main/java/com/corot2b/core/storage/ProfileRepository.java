package com.corot2b.core.storage;

import com.corot2b.api.model.SkyBlockProfile;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads and saves profiles asynchronously.
 *
 * <p>Design rules this class exists to enforce:
 * <ul>
 *   <li><b>No JDBC on the main thread.</b> Loads return a {@link CompletableFuture};
 *       saves are fire-and-forget into a queue.</li>
 *   <li><b>Crash safety.</b> A profile is queued the moment it is marked dirty, the
 *       queue is drained on a fixed interval, on unload, and again in
 *       {@link #shutdown()} with a bounded grace period. Losing a save requires
 *       losing the process between two flushes, and the interval is configurable.</li>
 *   <li><b>No cross-server clobbering.</b> Writes carry the in-memory
 *       {@code version} as a {@code WHERE version = ?} guard and bump it. If another
 *       server already wrote, the update affects zero rows and we log and reload
 *       rather than overwriting someone else's progress.</li>
 * </ul>
 */
public final class ProfileRepository implements AutoCloseable {

    private final Database db;
    private final Logger logger;
    private final Gson gson = new Gson();

    private final ScheduledExecutorService io = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "SkyBlock-IO");
        t.setDaemon(true);
        return t;
    });

    private final ConcurrentHashMap<UUID, SkyBlockProfile> cache = new ConcurrentHashMap<>();
    private final ArrayBlockingQueue<UUID> dirty = new ArrayBlockingQueue<>(8192);
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    private final int batchSize;

    public ProfileRepository(Database db, Logger logger, int batchSize, int intervalSeconds) {
        this.db = db;
        this.logger = logger;
        this.batchSize = Math.max(1, batchSize);
        io.scheduleWithFixedDelay(this::flushQuietly, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
    }

    // ------------------------------------------------------------------- loading
    /**
     * Loads a profile from cache or database. Never blocks the caller's thread;
     * the future completes on the IO pool.
     */
    public CompletableFuture<SkyBlockProfile> load(UUID profileId) {
        SkyBlockProfile cached = cache.get(profileId);
        if (cached != null) return CompletableFuture.completedFuture(cached);

        return CompletableFuture.supplyAsync(() -> {
            try {
                SkyBlockProfile loaded = readFromDb(profileId);
                if (loaded != null) cache.put(profileId, loaded);
                return loaded;
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "failed to load profile " + profileId, e);
                return null;
            }
        }, io);
    }

    /** Creates a brand-new profile row and caches it. */
    public CompletableFuture<SkyBlockProfile> create(UUID ownerUuid, String name) {
        UUID profileId = UUID.randomUUID();
        SkyBlockProfile p = new SkyBlockProfile(profileId, ownerUuid, name);
        p.selected(true);
        p.version(0);
        cache.put(profileId, p);
        return CompletableFuture.supplyAsync(() -> {
            try {
                insert(p);
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "failed to insert profile " + profileId, e);
            }
            return p;
        }, io);
    }

    private SkyBlockProfile readFromDb(UUID profileId) throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT owner_uuid, name, selected, coins_cents, bank_cents, fairy_souls, version, last_seen "
                             + "FROM sb_profiles WHERE profile_id = " + db.placeholder(1))) {
            ps.setObject(1, profileId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                UUID owner = rs.getObject("owner_uuid", UUID.class);
                SkyBlockProfile p = new SkyBlockProfile(profileId, owner, rs.getString("name"));
                p.selected(rs.getBoolean("selected"));
                p.loadCoins(rs.getLong("coins_cents"));
                p.loadBank(rs.getLong("bank_cents"));
                p.fairySouls(rs.getInt("fairy_souls"));
                p.version(rs.getInt("version"));
                Timestamp seen = rs.getTimestamp("last_seen");
                if (seen != null) p.lastSeen(seen.getTime());
                loadSkills(c, p);
                loadCollections(c, p);
                return p;
            }
        }
    }

    private void loadSkills(Connection c, SkyBlockProfile p) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT skill, xp FROM sb_skills WHERE profile_id = " + db.placeholder(1))) {
            ps.setObject(1, p.profileId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) p.skillXp(rs.getString("skill"), rs.getLong("xp"));
            }
        }
    }

    private void loadCollections(Connection c, SkyBlockProfile p) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT item_id, amount FROM sb_collections WHERE profile_id = " + db.placeholder(1))) {
            ps.setObject(1, p.profileId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) p.addCollection(rs.getString("item_id"), rs.getLong("amount"));
            }
        }
    }

    // ------------------------------------------------------------------- saving
    /** Marks a profile dirty; it will be written on the next flush. */
    public void markDirty(SkyBlockProfile profile) {
        if (profile == null) return;
        cache.put(profile.profileId(), profile);
        if (!dirty.offer(profile.profileId())) {
            // Queue full means we are far behind; log loudly rather than drop silently.
            logger.severe("save queue is full — profile " + profile.profileId() + " may save late");
        }
    }

    private void flushQuietly() {
        try {
            flush();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "periodic save flush failed", e);
        }
    }

    /** Drains the dirty queue in batches. Safe to call from any thread. */
    public int flush() {
        List<UUID> batch = new ArrayList<>(batchSize);
        dirty.drainTo(batch, batchSize);
        int written = 0;
        for (UUID id : batch) {
            SkyBlockProfile p = cache.get(id);
            if (p == null) continue;
            try {
                if (update(p)) written++;
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "failed to save profile " + id, e);
                // Requeue so the change is not lost.
                dirty.offer(id);
            }
        }
        return written;
    }

    private void insert(SkyBlockProfile p) throws SQLException {
        db.transactionVoid(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO sb_profiles (profile_id, owner_uuid, name, selected, coins_cents, bank_cents, "
                            + "fairy_souls, version, last_seen) VALUES ("
                            + db.placeholder(1) + ", " + db.placeholder(2) + ", " + db.placeholder(3) + ", "
                            + db.placeholder(4) + ", " + db.placeholder(5) + ", " + db.placeholder(6) + ", "
                            + db.placeholder(7) + ", " + db.placeholder(8) + ", " + db.placeholder(9) + ")")) {
                ps.setObject(1, p.profileId());
                ps.setObject(2, p.ownerUuid());
                ps.setString(3, p.name());
                ps.setBoolean(4, p.selected());
                ps.setLong(5, p.coinsCents());
                ps.setLong(6, p.bankCents());
                ps.setInt(7, p.fairySouls());
                ps.setInt(8, 0);
                ps.setTimestamp(9, new Timestamp(System.currentTimeMillis()));
                ps.executeUpdate();
            }
            writeSkills(c, p);
            writeCollections(c, p);
        });
    }

    /**
     * Guarded update. Returns false when the row's version moved on without us,
     * which means another server owns a newer copy and we must not overwrite it.
     */
    private boolean update(SkyBlockProfile p) throws SQLException {
        int expected = p.version();
        return db.transaction(c -> {
            int rows;
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE sb_profiles SET name = " + db.placeholder(2)
                            + ", coins_cents = " + db.placeholder(3)
                            + ", bank_cents = " + db.placeholder(4)
                            + ", fairy_souls = " + db.placeholder(5)
                            + ", skill_average = " + db.placeholder(6)
                            + ", last_seen = " + db.placeholder(7)
                            + ", version = " + db.placeholder(8)
                            + " WHERE profile_id = " + db.placeholder(1)
                            + " AND version = " + db.placeholder(9))) {
                ps.setObject(1, p.profileId());
                ps.setString(2, p.name());
                ps.setLong(3, p.coinsCents());
                ps.setLong(4, p.bankCents());
                ps.setInt(5, p.fairySouls());
                ps.setDouble(6, com.corot2b.core.skills.SkillService.skillAverage(p));
                ps.setTimestamp(7, new Timestamp(System.currentTimeMillis()));
                ps.setInt(8, expected + 1);
                ps.setInt(9, expected);
                rows = ps.executeUpdate();
            }
            if (rows == 0) {
                logger.warning("optimistic lock miss for profile " + p.profileId()
                        + " (expected version " + expected + "); reloading instead of overwriting");
                cache.remove(p.profileId());
                return false;
            }
            p.version(expected + 1);
            writeSkills(c, p);
            writeCollections(c, p);
            return true;
        });
    }

    private void writeSkills(Connection c, SkyBlockProfile p) throws SQLException {
        String sql = db.dialect() == Database.Dialect.POSTGRES
                ? "INSERT INTO sb_skills (profile_id, skill, xp) VALUES (" + db.placeholder(1) + ", "
                + db.placeholder(2) + ", " + db.placeholder(3) + ") ON CONFLICT (profile_id, skill) "
                + "DO UPDATE SET xp = EXCLUDED.xp"
                : "INSERT INTO sb_skills (profile_id, skill, xp) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE xp = VALUES(xp)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (Map.Entry<String, Long> e : p.skills().entrySet()) {
                ps.setObject(1, p.profileId());
                ps.setString(2, e.getKey());
                ps.setLong(3, e.getValue());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void writeCollections(Connection c, SkyBlockProfile p) throws SQLException {
        String sql = db.dialect() == Database.Dialect.POSTGRES
                ? "INSERT INTO sb_collections (profile_id, item_id, amount) VALUES (" + db.placeholder(1) + ", "
                + db.placeholder(2) + ", " + db.placeholder(3) + ") ON CONFLICT (profile_id, item_id) "
                + "DO UPDATE SET amount = EXCLUDED.amount"
                : "INSERT INTO sb_collections (profile_id, item_id, amount) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE amount = VALUES(amount)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (Map.Entry<String, Long> e : p.collections().entrySet()) {
                ps.setObject(1, p.profileId());
                ps.setString(2, e.getKey());
                ps.setLong(3, e.getValue());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // ------------------------------------------------------------------ lifecycle
    public void evict(UUID profileId) {
        SkyBlockProfile p = cache.remove(profileId);
        if (p != null) {
            try {
                update(p);
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "failed to save profile on evict " + profileId, e);
            }
        }
    }

    public Collection<SkyBlockProfile> cached() {
        return cache.values();
    }

    public int dirtyCount() {
        return dirty.size();
    }

    /**
     * Final drain with a hard deadline. Called from {@code onDisable} so a normal
     * restart or plugin reload cannot drop unsaved progress.
     */
    public void shutdown(long graceSeconds) {
        if (!shuttingDown.compareAndSet(false, true)) return;
        long deadline = System.currentTimeMillis() + graceSeconds * 1000L;
        while (!dirty.isEmpty() && System.currentTimeMillis() < deadline) {
            flush();
        }
        for (SkyBlockProfile p : cache.values()) {
            try {
                update(p);
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "failed to save profile on shutdown " + p.profileId(), e);
            }
        }
        io.shutdown();
        try {
            if (!io.awaitTermination(5, TimeUnit.SECONDS)) io.shutdownNow();
        } catch (InterruptedException e) {
            io.shutdownNow();
            Thread.currentThread().interrupt();
        }
        if (!dirty.isEmpty()) {
            logger.severe(dirty.size() + " profile(s) still dirty at shutdown — data may be behind");
        }
    }

    @Override
    public void close() {
        shutdown(30);
    }

    /** Minimal JSON helper so skyblock-core does not force a Gson version on the host. */
    static final class Gson {
        String toJson(Object o) {
            return String.valueOf(o);
        }
    }
}
