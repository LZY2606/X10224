package dsc.store;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Schema creation and migration. Fixtures ship a v1 database; open migrates it to current. */
final class Schema {
    static final int CURRENT_VERSION = 1;

    private Schema() {
    }

    static void initialize(Connection c) throws SQLException {
        int existing = currentUserVersion(c);
        if (existing == 0) {
            createV1(c);
            setUserVersion(c, 1);
        } else {
            migrate(c, existing);
        }
    }

    static int currentUserVersion(Connection c) throws SQLException {
        try (var rs = c.createStatement().executeQuery("PRAGMA user_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    static void setUserVersion(Connection c, int v) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA user_version = " + v);
        }
    }

    static void createV1(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS projects (
                    project_id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    current_branch TEXT NOT NULL,
                    head_event_id TEXT
                )""");
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS events (
                    project_id TEXT NOT NULL,
                    seq INTEGER NOT NULL,
                    event_id TEXT PRIMARY KEY,
                    type TEXT NOT NULL,
                    at TEXT NOT NULL,
                    author TEXT NOT NULL,
                    parent_event_id TEXT,
                    branch_id TEXT NOT NULL,
                    basis_refs TEXT NOT NULL,
                    payload TEXT NOT NULL
                )""");
            s.executeUpdate("""
                CREATE UNIQUE INDEX IF NOT EXISTS idx_events_seq
                    ON events(project_id, seq)""");
            s.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_events_branch
                    ON events(project_id, branch_id)""");
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS branches (
                    project_id TEXT NOT NULL,
                    branch_id TEXT NOT NULL,
                    label TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    forked_from_event_id TEXT,
                    tip_event_id TEXT NOT NULL,
                    adopted INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (project_id, branch_id)
                )""");
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS locks (
                    project_id TEXT PRIMARY KEY,
                    holder TEXT NOT NULL,
                    acquired_at TEXT NOT NULL
                )""");
        }
    }

    /** Migration path from older fixture databases. Future steps append here. */
    static void migrate(Connection c, int from) throws SQLException {
        int v = from;
        if (v < 1) {
            createV1(c);
            v = 1;
        }
        // Example forward migrations (kept ready for schema evolution):
        // if (v < 2) { ...additive ALTER...; v = 2; }
        setUserVersion(c, v);
    }
}
