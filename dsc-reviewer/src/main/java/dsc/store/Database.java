package dsc.store;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite connection management plus the schema. The schema itself is
 * versioned; {@link SchemaMigrator} upgrades older files in place so an
 * existing local database keeps working after a rule/schema upgrade.
 */
public final class Database {

    public static final int SCHEMA_VERSION = 2;

    private Database() {
    }

    public static Connection open(String path) {
        try {
            Class.forName("org.sqlite.JDBC");
            Connection c = DriverManager.getConnection("jdbc:sqlite:" + path);
            c.createStatement().execute("PRAGMA journal_mode=WAL");
            c.createStatement().execute("PRAGMA foreign_keys=ON");
            c.setAutoCommit(false);
            SchemaMigrator.migrate(c);
            return c;
        } catch (Exception e) {
            throw new RuntimeException("cannot open database " + path, e);
        }
    }

    static void createFresh(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS meta (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                )""");
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS events (
                    seq INTEGER PRIMARY KEY AUTOINCREMENT,
                    parent_seq INTEGER NOT NULL,
                    author TEXT NOT NULL,
                    type TEXT NOT NULL,
                    subject_id TEXT NOT NULL,
                    payload TEXT NOT NULL,
                    depends_on TEXT NOT NULL DEFAULT '[]',
                    created_at TEXT NOT NULL,
                    UNIQUE(seq)
                )""");
            // Current-pointer table: only switched to after a full successful write batch.
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS current_pointers (
                    subject_id TEXT PRIMARY KEY,
                    event_seq INTEGER NOT NULL REFERENCES events(seq),
                    version_id TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                )""");
            // Acceptance ledger keeps both accepted and rejected candidates.
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS decisions (
                    candidate_id TEXT NOT NULL,
                    event_seq INTEGER NOT NULL REFERENCES events(seq),
                    state TEXT NOT NULL CHECK (state IN ('ACCEPTED','REJECTED','PENDING','CONFLICT')),
                    reviewer TEXT NOT NULL,
                    note TEXT,
                    PRIMARY KEY (candidate_id, event_seq)
                ) WITHOUT ROWID""");
            st.executeUpdate("INSERT OR REPLACE INTO meta(key,value) VALUES('schema_version','"
                    + SCHEMA_VERSION + "')");
        }
    }
}
