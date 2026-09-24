package dsc.store;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Schema versioning. Current version is 3.
 *  v1: archives, derivation, pointer, event
 *  v2: + branch (retained alternatives from conflicts / unchosen baselines)
 *  v3: + derivation.diagnostics
 * Old databases are migrated step by step; data is preserved.
 */
public final class Schema {
    public static final int CURRENT = 3;

    private Schema() {}

    public static int version(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS schema_meta(version INTEGER NOT NULL)");
            var rs = s.executeQuery("SELECT version FROM schema_meta");
            if (rs.next()) return rs.getInt(1);
            s.execute("INSERT INTO schema_meta(version) VALUES (1)");
            return 1;
        }
    }

    public static void migrate(Connection c) throws SQLException {
        int v = version(c);
        try (Statement s = c.createStatement()) {
            if (v < 1) throw new IllegalStateException("unreachable");
            // v1 base tables (idempotent)
            s.execute("""
                CREATE TABLE IF NOT EXISTS archive(
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  kind TEXT NOT NULL,
                  name TEXT NOT NULL,
                  version INTEGER NOT NULL,
                  raw TEXT NOT NULL,
                  created_event INTEGER,
                  UNIQUE(kind,name,version))""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS derivation(
                  id TEXT NOT NULL,
                  version INTEGER NOT NULL,
                  kind TEXT NOT NULL,
                  status TEXT NOT NULL,
                  inputs TEXT NOT NULL,
                  payload TEXT,
                  created_event INTEGER,
                  PRIMARY KEY(id,version))""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS pointer(
                  node_id TEXT PRIMARY KEY,
                  version INTEGER NOT NULL)""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS event(
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  parent INTEGER,
                  actor TEXT,
                  type TEXT,
                  payload TEXT,
                  pointers TEXT,
                  created_at TEXT DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')))""");
            if (v < 2) {
                s.execute("""
                    CREATE TABLE IF NOT EXISTS branch(
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      base_event INTEGER,
                      actor TEXT,
                      payload TEXT,
                      status TEXT NOT NULL)""");
                s.execute("UPDATE schema_meta SET version=2");
                v = 2;
            }
            if (v < 3) {
                s.execute("ALTER TABLE derivation ADD COLUMN diagnostics TEXT NOT NULL DEFAULT '[]'");
                s.execute("UPDATE schema_meta SET version=3");
                v = 3;
            }
        }
    }
}
