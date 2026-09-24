package dsc.store;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite storage with explicit schema migrations.
 * Schema v1: core tables. Schema v2: events.actor column (proves old-version migration).
 */
public final class Database {
    public static final int LATEST_SCHEMA = 2;

    private final Connection conn;

    private Database(Connection conn) {
        this.conn = conn;
    }

    public static Database open(String path) {
        try {
            Connection c = DriverManager.getConnection("jdbc:sqlite:" + path);
            try (Statement s = c.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL");
                s.execute("PRAGMA foreign_keys=ON");
                s.execute("PRAGMA busy_timeout=5000");
            }
            Database db = new Database(c);
            db.migrate();
            db.recoverInterruptedWrites();
            return db;
        } catch (SQLException e) {
            throw new RuntimeException("cannot open database " + path + ": " + e.getMessage(), e);
        }
    }

    public Connection conn() {
        return conn;
    }

    public void close() {
        try {
            conn.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /** Migration steps: index = target schema version. */
    static List<String[]> migrations() {
        List<String[]> steps = new ArrayList<>();
        // -> v1: core schema
        steps.add(new String[] {
            "CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT NOT NULL)",
            "CREATE TABLE samples(id TEXT PRIMARY KEY, name TEXT NOT NULL, seq INTEGER NOT NULL)",
            "CREATE TABLE sources("
                + " id TEXT PRIMARY KEY, sample_id TEXT NOT NULL, kind TEXT NOT NULL,"
                + " version INTEGER NOT NULL, supersedes TEXT, status TEXT NOT NULL,"
                + " payload TEXT NOT NULL, seq INTEGER NOT NULL)",
            "CREATE TABLE derivations("
                + " id TEXT PRIMARY KEY, sample_id TEXT NOT NULL, kind TEXT NOT NULL,"
                + " version INTEGER NOT NULL, inputs TEXT NOT NULL, params TEXT NOT NULL,"
                + " result TEXT, state TEXT NOT NULL, seq INTEGER NOT NULL)",
            "CREATE TABLE pointers("
                + " sample_id TEXT NOT NULL, kind TEXT NOT NULL, derivation_id TEXT NOT NULL,"
                + " PRIMARY KEY(sample_id, kind))",
            "CREATE TABLE events("
                + " id TEXT PRIMARY KEY, sample_id TEXT NOT NULL, seq INTEGER NOT NULL,"
                + " kind TEXT NOT NULL, payload TEXT NOT NULL)",
            "CREATE TABLE seq_counter(name TEXT PRIMARY KEY, value INTEGER NOT NULL)"
        });
        // -> v2: actor on events (who made the decision)
        steps.add(new String[] {
            "ALTER TABLE events ADD COLUMN actor TEXT NOT NULL DEFAULT 'system'"
        });
        return steps;
    }

    private int schemaVersion() throws SQLException {
        try (Statement s = conn.createStatement()) {
            try {
                var rs = s.executeQuery("SELECT value FROM meta WHERE key='schema_version'");
                if (rs.next()) {
                    return Integer.parseInt(rs.getString(1));
                }
            } catch (SQLException noMetaTable) {
                // fresh database
            }
        }
        return 0;
    }

    private void migrate() throws SQLException {
        int v = schemaVersion();
        List<String[]> steps = migrations();
        if (v > steps.size()) {
            throw new IllegalStateException("database schema v" + v + " is newer than supported v" + steps.size());
        }
        while (v < steps.size()) {
            String[] ddl = steps.get(v);
            conn.setAutoCommit(false);
            try (Statement s = conn.createStatement()) {
                for (String sql : ddl) {
                    s.execute(sql);
                }
                if (v == 0) {
                    s.execute("INSERT INTO meta(key,value) VALUES('schema_version','1')");
                } else {
                    s.execute("UPDATE meta SET value='" + (v + 1) + "' WHERE key='schema_version'");
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
            v++;
        }
    }

    /**
     * Crash recovery: a derivation left in state 'building' means the process died
     * mid-write before the atomic pointer switch. Mark it failed; the pointer still
     * references the last complete version, so readers see either old or complete-new.
     */
    private void recoverInterruptedWrites() throws SQLException {
        try (Statement s = conn.createStatement()) {
            s.execute("UPDATE derivations SET state='failed', "
                + " result=json_object('error','interrupted-write') WHERE state='building'");
        }
    }
}
