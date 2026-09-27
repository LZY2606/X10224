package dsc.store;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Owns the SQLite connection and schema lifecycle.
 *
 * Atomicity rule: a derivation writes into a changeset inside a single
 * transaction; the current pointer only moves after commit. On open, any
 * changeset left OPEN by a crashed process is discarded, so readers always
 * see either the old complete version or the new complete version.
 */
public final class Database implements AutoCloseable {

    private final String url;
    private Connection connection;

    public Database(String filePath) {
        this.url = "jdbc:sqlite:" + filePath;
    }

    public Connection conn() {
        try {
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(url);
                tune(connection);
            }
            return connection;
        } catch (SQLException e) {
            throw new StoreException("open failed: " + url, e);
        }
    }

    private static void tune(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA foreign_keys=ON");
            st.execute("PRAGMA busy_timeout=10000");
            st.execute("PRAGMA synchronous=FULL");
        }
    }

    public void init() {
        try (Statement st = conn().createStatement()) {
            for (String ddl : Schema.DDLS.split(";\\s*")) {
                if (!ddl.isBlank()) st.execute(ddl);
            }
        } catch (SQLException e) {
            throw new StoreException("schema init failed", e);
        }
        migrate();
        recoverInterruptedChangesets();
    }

    private void migrate() {
        Integer rev = metaInt("schema_revision");
        if (rev == null) {
            putMeta("schema_revision", Integer.toString(Schema.LATEST_REVISION));
            return;
        }
        if (rev < Schema.LATEST_REVISION) {
            applyMigrations(rev);
            putMeta("schema_revision", Integer.toString(Schema.LATEST_REVISION));
        }
    }

    /**
     * v1 -> v2: older databases lacked the accepted flag history; add it without
     * touching identities or ordering.
     */
    private void applyMigrations(int from) {
        if (from <= 1) {
            execQuiet("ALTER TABLE baseline_candidate ADD COLUMN legacy_origin TEXT");
        }
    }

    private void execQuiet(String sql) {
        try (Statement st = conn().createStatement()) {
            st.execute(sql);
        } catch (SQLException ignored) {
            // column may already exist in partially migrated files
        }
    }

    /**
     * Crash recovery: every open changeset is unfinished work. Its rows either
     * already committed with the pointer (status would be COMMITTED) or must be
     * abandoned. We never replay half-written op logs on top of live data.
     */
    void recoverInterruptedChangesets() {
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT id FROM changeset WHERE status='OPEN'")) {
            while (rs.next()) {
                String id = rs.getString(1);
                try (PreparedStatement delOps = conn().prepareStatement(
                        "DELETE FROM changeset_op WHERE changeset_id=?")) {
                    delOps.setString(1, id);
                    delOps.executeUpdate();
                }
                try (PreparedStatement del = conn().prepareStatement(
                        "DELETE FROM changeset WHERE id=?")) {
                    del.setString(1, id);
                    del.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new StoreException("recovery failed", e);
        }
    }

    public String meta(String key) {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT value FROM meta WHERE key=?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            throw new StoreException(e);
        }
    }

    public Integer metaInt(String key) {
        String v = meta(key);
        return v == null ? null : Integer.parseInt(v);
    }

    public void putMeta(String key, String value) {
        try (PreparedStatement ps = conn().prepareStatement(
                "INSERT INTO meta(key,value) VALUES(?,?) "
                        + "ON CONFLICT(key) DO UPDATE SET value=excluded.value")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException(e);
        }
    }

    public long nextEventSeq() {
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COALESCE(MAX(seq),0)+1 FROM ledger_event")) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new StoreException(e);
        }
    }

    public int nextMassOrd(String sampleId) {
        return nextOrd("SELECT COALESCE(MAX(ord),0)+1 FROM mass_version WHERE sample_id=?", sampleId);
    }

    private int nextOrd(String sql, String arg) {
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, arg);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new StoreException(e);
        }
    }

    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
        }
    }

    public static final class StoreException extends RuntimeException {
        public StoreException(String msg, Throwable cause) { super(msg, cause); }
        public StoreException(Throwable cause) { super(cause); }
        public StoreException(String msg) { super(msg); }
    }
}
