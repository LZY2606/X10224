package dsc;

import java.sql.*;

/** SQLite handle with schema migrations. Current schema version: 2. */
public final class Db implements AutoCloseable {
    public static final int SCHEMA_VERSION = 2;
    public final Connection conn;

    private Db(Connection c) { this.conn = c; }

    public static Db open(String path) {
        try {
            Connection c = DriverManager.getConnection("jdbc:sqlite:" + path);
            try (Statement st = c.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA foreign_keys=ON");
                st.execute("PRAGMA busy_timeout=5000");
            }
            Db db = new Db(c);
            db.migrate();
            return db;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public static Db memory() {
        try {
            Connection c = DriverManager.getConnection("jdbc:sqlite::memory:");
            Db db = new Db(c);
            db.migrate();
            return db;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    int schemaVersion() {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT value FROM meta WHERE key='schema_version'")) {
            if (rs.next()) return Integer.parseInt(rs.getString(1));
        } catch (SQLException ignored) {}
        return 1;
    }

    private void migrate() {
        try {
            conn.setAutoCommit(false);
            int v = schemaVersion();
            if (v < 1) throw new IllegalStateException("unreachable");
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS meta(key TEXT PRIMARY KEY, value TEXT NOT NULL)");
                if (v == 1 && !tableExists("records")) {
                    // v1 base schema
                    st.execute("CREATE TABLE records(id TEXT NOT NULL, version INTEGER NOT NULL, kind TEXT NOT NULL," +
                               " payload TEXT NOT NULL, hash TEXT NOT NULL, seq INTEGER NOT NULL," +
                               " PRIMARY KEY(id, version))");
                    st.execute("CREATE TABLE heads(id TEXT PRIMARY KEY, version INTEGER NOT NULL)");
                    st.execute("CREATE TABLE events(seq INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL," +
                               " actor TEXT, payload TEXT NOT NULL, at TEXT NOT NULL)");
                    st.execute("CREATE TABLE conflicts(id TEXT PRIMARY KEY, record_id TEXT NOT NULL, base_version INTEGER NOT NULL," +
                               " ours TEXT NOT NULL, theirs TEXT NOT NULL, status TEXT NOT NULL, resolution TEXT, seq INTEGER NOT NULL)");
                }
                if (v < 2) {
                    // v1 -> v2: introduce branches so parallel baselines / competing decisions coexist
                    if (!columnExists("records", "branch"))
                        st.execute("ALTER TABLE records ADD COLUMN branch TEXT NOT NULL DEFAULT 'main'");
                    if (!columnExists("heads", "branch")) {
                        st.execute("ALTER TABLE heads RENAME TO heads_v1");
                        st.execute("CREATE TABLE heads(id TEXT NOT NULL, branch TEXT NOT NULL, version INTEGER NOT NULL," +
                                   " PRIMARY KEY(id, branch))");
                        st.execute("INSERT INTO heads(id, branch, version) SELECT id, 'main', version FROM heads_v1");
                        st.execute("DROP TABLE heads_v1");
                    }
                    if (!columnExists("conflicts", "branch"))
                        st.execute("ALTER TABLE conflicts ADD COLUMN branch TEXT NOT NULL DEFAULT 'main'");
                    st.execute("INSERT OR REPLACE INTO meta(key, value) VALUES('schema_version','2')");
                }
            }
            conn.commit();
            conn.setAutoCommit(true);
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            throw new RuntimeException(e);
        }
    }

    private boolean tableExists(String name) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='" + name + "'")) {
            return rs.next();
        }
    }

    private boolean columnExists(String table, String col) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) if (rs.getString("name").equals(col)) return true;
        }
        return false;
    }

    @Override public void close() {
        try { conn.close(); } catch (SQLException ignored) {}
    }
}
