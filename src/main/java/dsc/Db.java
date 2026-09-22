package dsc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.nio.file.Path;

/**
 * SQLite handle with schema migrations. Current schema version is 3.
 * v1: meta, objects, versions, version_objects, events
 * v2: + deps
 * v3: + conflicts
 */
public final class Db implements AutoCloseable {
    public static final int SCHEMA_VERSION = 3;

    private final Connection conn;

    public Db(Path path) {
        try {
            conn = DriverManager.getConnection("jdbc:sqlite:" + path);
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA foreign_keys=ON");
                st.execute("PRAGMA busy_timeout=5000");
            }
            conn.setAutoCommit(false);
            migrate();
        } catch (Exception e) {
            throw new RuntimeException("cannot open db " + path, e);
        }
    }

    public Connection conn() {
        return conn;
    }

    private int schemaVersion() throws Exception {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            conn.commit();
            ResultSet rs = st.executeQuery("SELECT value FROM meta WHERE key='schema_version'");
            return rs.next() ? Integer.parseInt(rs.getString(1)) : 0;
        }
    }

    private void migrate() throws Exception {
        int v = schemaVersion();
        try (Statement st = conn.createStatement()) {
            if (v < 1) {
                st.execute("CREATE TABLE IF NOT EXISTS objects ("
                        + "id TEXT PRIMARY KEY, kind TEXT NOT NULL, slot TEXT, "
                        + "payload TEXT NOT NULL, created_seq INTEGER NOT NULL)");
                st.execute("CREATE TABLE IF NOT EXISTS versions ("
                        + "id TEXT PRIMARY KEY, parent_id TEXT, seq INTEGER NOT NULL, reason TEXT)");
                st.execute("CREATE TABLE IF NOT EXISTS version_objects ("
                        + "version_id TEXT NOT NULL, object_id TEXT NOT NULL, ord INTEGER NOT NULL, "
                        + "PRIMARY KEY(version_id, object_id))");
                st.execute("CREATE TABLE IF NOT EXISTS events ("
                        + "seq INTEGER PRIMARY KEY AUTOINCREMENT, id TEXT NOT NULL, "
                        + "type TEXT NOT NULL, payload TEXT NOT NULL, version_id TEXT)");
            }
            if (v < 2) {
                st.execute("CREATE TABLE IF NOT EXISTS deps ("
                        + "derived_id TEXT NOT NULL, source_id TEXT NOT NULL, "
                        + "PRIMARY KEY(derived_id, source_id))");
            }
            if (v < 3) {
                st.execute("CREATE TABLE IF NOT EXISTS conflicts ("
                        + "id TEXT PRIMARY KEY, base_version TEXT, branch_version TEXT, "
                        + "payload TEXT NOT NULL, status TEXT NOT NULL, resolved_by_version TEXT)");
            }
            st.executeUpdate("INSERT OR REPLACE INTO meta(key,value) VALUES('schema_version','"
                    + SCHEMA_VERSION + "')");
            conn.commit();
        }
    }

    @Override
    public void close() {
        try {
            conn.close();
        } catch (Exception ignored) {
        }
    }
}
