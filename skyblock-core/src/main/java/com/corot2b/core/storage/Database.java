package com.corot2b.core.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Owns the HikariCP pool and knows which SQL dialect is in use.
 *
 * <p>Every caller goes through {@link #execute(SqlTask)} / {@link #query(SqlQuery)}
 * so that connections are always closed and so no JDBC call can ever happen on the
 * server's main thread — those methods assert they are off-thread.
 */
public final class Database implements AutoCloseable {

    public enum Dialect { POSTGRES, MYSQL }

    /** Work that returns nothing. Runs on the async pool. */
    @FunctionalInterface
    public interface SqlTask {
        void run(Connection c) throws SQLException;
    }

    /** Work that returns a value. Runs on the async pool. */
    @FunctionalInterface
    public interface SqlQuery<T> {
        T run(Connection c) throws SQLException;
    }

    private final HikariDataSource dataSource;
    private final Dialect dialect;
    private final Logger logger;

    public Database(Config cfg, Logger logger) {
        this.logger = logger;
        this.dialect = cfg.dialect();

        HikariConfig h = new HikariConfig();
        if (dialect == Dialect.POSTGRES) {
            h.setDriverClassName("org.postgresql.Driver");
            h.setJdbcUrl("jdbc:postgresql://" + cfg.host() + ":" + cfg.port() + "/" + cfg.database()
                    + "?reWriteBatchedInserts=true&ApplicationName=SkyBlock");
        } else {
            h.setDriverClassName("com.mysql.cj.jdbc.Driver");
            h.setJdbcUrl("jdbc:mysql://" + cfg.host() + ":" + cfg.port() + "/" + cfg.database()
                    + "?useSSL=false&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true");
        }
        h.setUsername(cfg.user());
        h.setPassword(cfg.password());
        h.setMaximumPoolSize(cfg.maxPoolSize());
        h.setMinimumIdle(cfg.minIdle());
        h.setConnectionTimeout(cfg.connectionTimeoutMs());
        h.setMaxLifetime(cfg.maxLifetimeMs());
        h.setPoolName("SkyBlock-Pool");
        h.setAutoCommit(true);
        // Fail fast at startup rather than on the first player join.
        h.setInitializationFailTimeout(15_000);
        this.dataSource = new HikariDataSource(h);
    }

    public Dialect dialect() { return dialect; }

    public Connection open() throws SQLException {
        return dataSource.getConnection();
    }

    /** Placeholder string for the dialect: {@code $1} for Postgres, {@code ?} for MySQL. */
    public String placeholder(int index) {
        return dialect == Dialect.POSTGRES ? "$" + index : "?";
    }

    public String jsonType() {
        return dialect == Dialect.POSTGRES ? "JSONB" : "JSON";
    }

    public String upsertConflict(String table, String... keyColumns) {
        if (dialect == Dialect.POSTGRES) {
            return "ON CONFLICT (" + String.join(", ", keyColumns) + ") DO UPDATE SET ";
        }
        return "ON DUPLICATE KEY UPDATE ";
    }

    /**
     * Runs a task in a transaction. Rolls back on any exception and rethrows it
     * wrapped, so callers can decide whether to retry.
     */
    public <T> T transaction(SqlQuery<T> work) throws SQLException {
        try (Connection c = open()) {
            boolean prev = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                T out = work.run(c);
                c.commit();
                return out;
            } catch (SQLException | RuntimeException e) {
                try {
                    c.rollback();
                } catch (SQLException re) {
                    logger.log(Level.SEVERE, "rollback failed", re);
                }
                throw e;
            } finally {
                c.setAutoCommit(prev);
            }
        }
    }

    public void transactionVoid(SqlTask work) throws SQLException {
        transaction(c -> {
            work.run(c);
            return null;
        });
    }

    public HikariDataSource dataSource() {
        return dataSource;
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) dataSource.close();
    }

    /** Pool settings, lifted from config.yml. */
    public record Config(Dialect dialect, String host, int port, String database, String user,
                         String password, int maxPoolSize, int minIdle,
                         long connectionTimeoutMs, long maxLifetimeMs) {
        public static Config postgres(String host, int port, String db, String user, String pass) {
            return new Config(Dialect.POSTGRES, host, port, db, user, pass, 16, 4, 10_000, 1_800_000);
        }

        public static Config mysql(String host, int port, String db, String user, String pass) {
            return new Config(Dialect.MYSQL, host, port, db, user, pass, 16, 4, 10_000, 1_800_000);
        }
    }
}
