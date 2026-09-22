package dsc.store;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** SQLite connection holder with schema migrations.
 *  Migration rule: an old database is migrated forward step by step; data is never dropped. */
public class Database implements AutoCloseable {
    public static final int SCHEMA_VERSION = 2;
    private final Connection conn;

    public Database(String path) {
        try {
            this.conn = DriverManager.getConnection("jdbc:sqlite:" + path);
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA foreign_keys=ON");
                st.execute("PRAGMA busy_timeout=5000");
            }
            migrate();
        } catch (SQLException e) {
            throw new RuntimeException("cannot open database " + path, e);
        }
    }

    public Connection conn() { return conn; }

    private int userVersion() throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA user_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private void migrate() throws SQLException {
        int v = userVersion();
        if (v < 1) {
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS records("
                        + "id TEXT NOT NULL, version INTEGER NOT NULL, kind TEXT NOT NULL,"
                        + " status TEXT NOT NULL, payload TEXT NOT NULL, inputs TEXT NOT NULL DEFAULT '[]',"
                        + " event_id TEXT, created_seq INTEGER NOT NULL,"
                        + " PRIMARY KEY(id, version))");
                st.execute("CREATE TABLE IF NOT EXISTS pointers("
                        + "name TEXT PRIMARY KEY, id TEXT NOT NULL, version INTEGER NOT NULL, seq INTEGER NOT NULL)");
                st.execute("CREATE TABLE IF NOT EXISTS events("
                        + "id TEXT PRIMARY KEY, seq INTEGER UNIQUE NOT NULL, ts TEXT NOT NULL,"
                        + " actor TEXT NOT NULL, type TEXT NOT NULL, payload TEXT NOT NULL, pointers TEXT NOT NULL)");
                st.execute("CREATE TABLE IF NOT EXISTS counters(name TEXT PRIMARY KEY, value INTEGER NOT NULL)");
                st.execute("PRAGMA user_version=1");
            }
            v = 1;
        }
        if (v < 2) {
            // v2: rule versions on records + conflict table for concurrent edits.
            try (Statement st = conn.createStatement()) {
                if (!columns("records").contains("rule_version")) {
                    st.execute("ALTER TABLE records ADD COLUMN rule_version TEXT");
                }
                st.execute("CREATE TABLE IF NOT EXISTS conflicts("
                        + "id TEXT PRIMARY KEY, seq INTEGER NOT NULL, base_seq INTEGER NOT NULL,"
                        + " actor TEXT NOT NULL, type TEXT NOT NULL, branch TEXT NOT NULL,"
                        + " status TEXT NOT NULL DEFAULT 'OPEN', resolution TEXT)");
                st.execute("PRAGMA user_version=2");
            }
            v = 2;
        }
        if (v != SCHEMA_VERSION) throw new IllegalStateException("unsupported schema " + v);
    }

    private List<String> columns(String table) throws SQLException {
        List<String> out = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) out.add(rs.getString("name"));
        }
        return out;
    }

    @Override public void close() {
        try { conn.close(); } catch (SQLException ignored) { }
    }
}
