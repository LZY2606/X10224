package dsc.store;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite schema with explicit, ordered migrations. Old databases are migrated
 * forward step by step; tests cover migrating a v1 database to the latest schema.
 */
public final class Database {
    public static final int LATEST = 2;

    private final Connection conn;

    private Database(Connection conn) { this.conn = conn; }

    public static Database open(String path) {
        try {
            Connection c = DriverManager.getConnection("jdbc:sqlite:" + path);
            try (Statement s = c.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL");
                s.execute("PRAGMA foreign_keys=ON");
            }
            Database db = new Database(c);
            db.migrateTo(LATEST);
            return db;
        } catch (SQLException e) {
            throw new RuntimeException("cannot open database " + path, e);
        }
    }

    public Connection conn() { return conn; }

    public int schemaVersion() {
        try (Statement s = conn.createStatement();
             var rs = s.executeQuery("SELECT COALESCE(MAX(version),0) FROM schema_migrations")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            return 0; // no migration table yet
        }
    }

    /** Apply migrations up to `target`. Exposed for migration tests. */
    public void migrateTo(int target) {
        int current = schemaVersion();
        List<String[]> steps = migrations();
        for (int v = current + 1; v <= target && v <= steps.size(); v++) {
            try {
                conn.setAutoCommit(false);
                try (Statement s = conn.createStatement()) {
                    for (String sql : steps.get(v - 1)) s.execute(sql);
                    s.execute("INSERT INTO schema_migrations(version) VALUES (" + v + ")");
                }
                conn.commit();
                conn.setAutoCommit(true);
            } catch (SQLException e) {
                try { conn.rollback(); conn.setAutoCommit(true); } catch (SQLException ignored) {}
                throw new RuntimeException("migration to v" + v + " failed", e);
            }
        }
    }

    private static List<String[]> migrations() {
        List<String[]> m = new ArrayList<>();
        // v1: base tables
        m.add(new String[] {
            "CREATE TABLE IF NOT EXISTS schema_migrations(version INTEGER PRIMARY KEY)",
            "CREATE TABLE IF NOT EXISTS objects(" +
                "id TEXT PRIMARY KEY, kind TEXT NOT NULL, name TEXT NOT NULL," +
                "content TEXT NOT NULL, created_seq INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS versions(" +
                "id TEXT PRIMARY KEY, seq INTEGER NOT NULL, parent TEXT," +
                "status TEXT NOT NULL, state_json TEXT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS pointers(name TEXT PRIMARY KEY, version_id TEXT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS events(" +
                "id TEXT PRIMARY KEY, seq INTEGER NOT NULL, kind TEXT NOT NULL," +
                "base_version TEXT, payload TEXT NOT NULL)"
        });
        // v2: events learn the resulting version; conflicts get their own table
        m.add(new String[] {
            "ALTER TABLE events ADD COLUMN resulting_version TEXT",
            "CREATE TABLE IF NOT EXISTS conflicts(" +
                "id TEXT PRIMARY KEY, seq INTEGER NOT NULL, base_version TEXT NOT NULL," +
                "branch_a TEXT NOT NULL, branch_b TEXT NOT NULL," +
                "status TEXT NOT NULL, resolution TEXT)"
        });
        return m;
    }

    public void close() {
        try { conn.close(); } catch (SQLException ignored) {}
    }
}
