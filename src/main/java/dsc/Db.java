package dsc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** SQLite storage. Schema is versioned; old databases are migrated in place. */
public class Db implements AutoCloseable {
    public static final int SCHEMA_VERSION = 2;
    public final Connection c;

    public Db(String path) {
        try {
            this.c = DriverManager.getConnection("jdbc:sqlite:" + path);
            try (Statement s = c.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL");
                s.execute("PRAGMA foreign_keys=ON");
                s.execute("PRAGMA busy_timeout=5000");
            }
            migrate();
        } catch (SQLException e) {
            throw new RuntimeException("open db " + path, e);
        }
    }

    private int userVersion() throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("PRAGMA user_version")) {
            return r.getInt(1);
        }
    }

    private void migrate() throws SQLException {
        int v = userVersion();
        if (v == 0) {
            try (Statement s = c.createStatement()) {
                s.execute("PRAGMA user_version=1");
            }
            v = 1;
        }
        if (v == 1) {
            // v1 -> v2: full current schema. v1 only had instrument_output/mass_version/meta.
            try (Statement s = c.createStatement()) {
                s.executeUpdate("CREATE TABLE IF NOT EXISTS meta(key TEXT PRIMARY KEY, value TEXT)");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS instrument_output("
                        + "id TEXT PRIMARY KEY, seq INTEGER, sample TEXT, filename TEXT,"
                        + "header_json TEXT, raw TEXT, imported_at TEXT)");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS mass_version("
                        + "id TEXT PRIMARY KEY, seq INTEGER, sample TEXT, value REAL, unit TEXT,"
                        + "raw TEXT, note TEXT, supersedes TEXT)");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS calibration("
                        + "id TEXT PRIMARY KEY, seq INTEGER, sample TEXT, raw TEXT, coeff REAL, uncertainty REAL)");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS tangent("
                        + "id TEXT PRIMARY KEY, seq INTEGER, segment_id TEXT, pair_id TEXT, side TEXT,"
                        + "t_lo REAL, t_hi REAL, author TEXT, version INTEGER, status TEXT)");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS derivation("
                        + "id TEXT PRIMARY KEY, seq INTEGER, status TEXT, trigger TEXT, created_at TEXT)");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS derived("
                        + "derivation_id TEXT, id TEXT, kind TEXT, reused INTEGER, json TEXT,"
                        + "PRIMARY KEY(derivation_id, id))");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS dep("
                        + "derivation_id TEXT, result_id TEXT, depends_on TEXT,"
                        + "PRIMARY KEY(derivation_id, result_id, depends_on))");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS event("
                        + "seq INTEGER PRIMARY KEY AUTOINCREMENT, id TEXT, derivation_id TEXT,"
                        + "type TEXT, subject TEXT, payload_json TEXT, created_at TEXT)");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS conflict("
                        + "id TEXT PRIMARY KEY, seq INTEGER, entity TEXT, entity_id TEXT,"
                        + "base_version INTEGER, mine_json TEXT, theirs_json TEXT,"
                        + "status TEXT, resolution TEXT)");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS adoption("
                        + "seq INTEGER PRIMARY KEY AUTOINCREMENT, transition_id TEXT, candidate TEXT,"
                        + "author TEXT, base_version INTEGER, status TEXT)");
                s.executeUpdate("PRAGMA user_version=" + SCHEMA_VERSION);
            }
        }
    }

    /** Remove derivations left in 'building' state by an interrupted write. */
    public int cleanInterrupted() {
        try (Statement s = c.createStatement()) {
            s.executeUpdate("DELETE FROM derived WHERE derivation_id IN (SELECT id FROM derivation WHERE status='building')");
            s.executeUpdate("DELETE FROM dep WHERE derivation_id IN (SELECT id FROM derivation WHERE status='building')");
            return s.executeUpdate("DELETE FROM derivation WHERE status='building'");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public String meta(String key) {
        try (var ps = c.prepareStatement("SELECT value FROM meta WHERE key=?")) {
            ps.setString(1, key);
            try (ResultSet r = ps.executeQuery()) {
                return r.next() ? r.getString(1) : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void setMeta(String key, String value) {
        try (var ps = c.prepareStatement("INSERT INTO meta(key,value) VALUES(?,?) "
                + "ON CONFLICT(key) DO UPDATE SET value=excluded.value")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        try {
            c.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
