package com.corot2b.core.storage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Applies numbered SQL migrations from the jar in version order, exactly once each.
 *
 * <p>Each applied migration is recorded in {@code sb_schema_version} together with a
 * checksum of its text, so editing a migration that already ran is detected at boot
 * instead of silently diverging two servers that share a database.
 */
public final class MigrationRunner {

    /** Migration files on the classpath, per dialect. */
    private static final String[] POSTGRES_MIGRATIONS = {
            "migrations/postgres/V1__init.sql",
    };

    private static final String[] MYSQL_MIGRATIONS = {
            "migrations/mysql/V1__init.sql",
    };

    private final Database db;
    private final Logger logger;

    public MigrationRunner(Database db, Logger logger) {
        this.db = db;
        this.logger = logger;
    }

    public void run() throws SQLException, IOException {
        ensureVersionTable();
        List<Migration> applied = readApplied();
        String[] files = db.dialect() == Database.Dialect.POSTGRES ? POSTGRES_MIGRATIONS : MYSQL_MIGRATIONS;

        for (String path : files) {
            String sql = readResource(path);
            int version = parseVersion(path);
            String checksum = checksum(sql);

            Migration existing = applied.stream().filter(m -> m.version() == version).findFirst().orElse(null);
            if (existing != null) {
                if (!existing.checksum().equals(checksum)) {
                    throw new SQLException("Migration " + path + " changed after it was applied "
                            + "(recorded=" + existing.checksum() + ", current=" + checksum + "). "
                            + "Write a new migration instead of editing an applied one.");
                }
                continue;
            }

            logger.info("Applying migration " + path);
            db.transactionVoid(c -> {
                try (Statement st = c.createStatement()) {
                    for (String chunk : split(sql)) {
                        if (!chunk.isBlank()) st.execute(chunk);
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO sb_schema_version (version, checksum, description) VALUES ("
                                + db.placeholder(1) + ", " + db.placeholder(2) + ", " + db.placeholder(3) + ")")) {
                    ps.setInt(1, version);
                    ps.setString(2, checksum);
                    ps.setString(3, path);
                    ps.executeUpdate();
                }
            });
        }
        logger.info("Schema is up to date (" + files.length + " migration(s) known).");
    }

    private void ensureVersionTable() throws SQLException {
        String ddl = db.dialect() == Database.Dialect.POSTGRES
                ? """
                  CREATE TABLE IF NOT EXISTS sb_schema_version (
                      version INTEGER PRIMARY KEY,
                      applied_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                      checksum TEXT NOT NULL,
                      description TEXT NOT NULL
                  )
                  """
                : """
                  CREATE TABLE IF NOT EXISTS sb_schema_version (
                      version INT PRIMARY KEY,
                      applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      checksum VARCHAR(64) NOT NULL,
                      description VARCHAR(255) NOT NULL
                  )
                  """;
        try (Connection c = db.open(); Statement st = c.createStatement()) {
            st.execute(ddl);
        }
    }

    private List<Migration> readApplied() throws SQLException {
        List<Migration> out = new ArrayList<>();
        try (Connection c = db.open();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT version, checksum FROM sb_schema_version")) {
            while (rs.next()) out.add(new Migration(rs.getInt(1), rs.getString(2)));
        }
        return out;
    }

    /** Splits on semicolons at line ends, ignoring those inside single quotes. */
    static List<String> split(String sql) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inString = false;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (ch == '\'') inString = !inString;
            if (ch == ';' && !inString) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        if (!cur.toString().isBlank()) out.add(cur.toString());
        return out;
    }

    /** Strips {@code --} comments so the checksum is stable against comment edits. */
    static String stripComments(String sql) {
        StringBuilder sb = new StringBuilder();
        for (String line : sql.split("\n")) {
            int idx = line.indexOf("--");
            sb.append(idx >= 0 ? line.substring(0, idx) : line).append('\n');
        }
        return sb.toString();
    }

    static String checksum(String sql) {
        return Integer.toHexString(stripComments(sql).replaceAll("\\s+", " ").trim().hashCode());
    }

    static int parseVersion(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        int us = name.indexOf("__");
        String head = us > 0 ? name.substring(0, us) : name;
        String digits = head.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) throw new IllegalArgumentException("migration filename must contain a version: " + path);
        return Integer.parseInt(digits);
    }

    private static String readResource(String path) throws IOException {
        try (InputStream in = MigrationRunner.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new IOException("missing migration resource: " + path);
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append('\n');
                return sb.toString();
            }
        }
    }

    private record Migration(int version, String checksum) {}

    /** For diagnostics from the admin dashboard. */
    public List<String> describe() throws SQLException {
        List<String> out = new ArrayList<>();
        for (Migration m : readApplied()) out.add("V" + m.version() + " (" + m.checksum() + ")");
        return out;
    }

    public String dialectName() {
        return db.dialect().name().toLowerCase(Locale.ROOT);
    }
}
