package dsc.store;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Schema migrations. v1 -> v2 adds the conflicts table; existing rows are preserved. */
public final class Migrator {
    private Migrator() {}

    /** The historical v1 schema, kept so old databases can be reproduced and migrated. */
    public static void createV1(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS schema_version(version INTEGER NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS sources(" +
                    "id TEXT PRIMARY KEY, kind TEXT NOT NULL, version INTEGER NOT NULL," +
                    "payload TEXT NOT NULL, seq INTEGER NOT NULL, replaces TEXT)");
            s.execute("CREATE TABLE IF NOT EXISTS derived(" +
                    "id TEXT PRIMARY KEY, kind TEXT NOT NULL, version INTEGER NOT NULL," +
                    "inputs TEXT NOT NULL, status TEXT NOT NULL, payload TEXT NOT NULL, seq INTEGER NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS events(" +
                    "seq INTEGER PRIMARY KEY AUTOINCREMENT, id TEXT NOT NULL," +
                    "type TEXT NOT NULL, payload TEXT NOT NULL, created_at TEXT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS pointers(name TEXT PRIMARY KEY, version INTEGER NOT NULL)");
            s.execute("DELETE FROM schema_version");
            s.execute("INSERT INTO schema_version(version) VALUES (1)");
        }
    }

    public static void migrate(Connection c, int from, int to) throws SQLException {
        if (from == 1 && to >= 2) {
            try (Statement s = c.createStatement()) {
                s.execute("CREATE TABLE IF NOT EXISTS conflicts(" +
                        "id TEXT PRIMARY KEY, base_version INTEGER NOT NULL, current_version INTEGER NOT NULL," +
                        "payload TEXT NOT NULL, status TEXT NOT NULL, seq INTEGER NOT NULL)");
            }
        }
    }
}
