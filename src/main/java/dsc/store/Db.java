package dsc.store;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** SQLite handle with schema versioning and migrations. */
public final class Db implements AutoCloseable {
    public static final int SCHEMA_VERSION = 2;

    /** Historical v1 schema, kept so old databases can be migrated and tested. */
    public static final String V1_SCHEMA = """
        CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT NOT NULL);
        INSERT INTO meta(key, value) VALUES('schema_version', '1');
        CREATE TABLE sample(id TEXT PRIMARY KEY, name TEXT NOT NULL, created_at TEXT NOT NULL);
        CREATE TABLE source_record(id TEXT PRIMARY KEY, sample_id TEXT NOT NULL, kind TEXT NOT NULL,
            version INTEGER NOT NULL, payload TEXT NOT NULL, created_at TEXT NOT NULL, supersedes TEXT);
        CREATE TABLE derivation(id TEXT PRIMARY KEY, sample_id TEXT NOT NULL, kind TEXT NOT NULL,
            version INTEGER NOT NULL, status TEXT NOT NULL, inputs TEXT NOT NULL,
            payload TEXT NOT NULL, created_at TEXT NOT NULL);
        CREATE TABLE current_pointer(sample_id TEXT NOT NULL, kind TEXT NOT NULL,
            derivation_id TEXT NOT NULL, PRIMARY KEY(sample_id, kind));
        CREATE TABLE conflict(id TEXT PRIMARY KEY, sample_id TEXT NOT NULL, kind TEXT NOT NULL,
            base_version INTEGER NOT NULL, left_derivation TEXT NOT NULL, right_derivation TEXT NOT NULL,
            status TEXT NOT NULL, resolution TEXT, created_at TEXT NOT NULL);
        """;

    private static final String V2_UPGRADE = """
        ALTER TABLE derivation ADD COLUMN diagnostics TEXT NOT NULL DEFAULT '[]';
        CREATE TABLE event(id TEXT PRIMARY KEY, seq INTEGER NOT NULL, sample_id TEXT NOT NULL,
            type TEXT NOT NULL, basis TEXT NOT NULL, created_at TEXT NOT NULL);
        """;

    private final Connection conn;

    private Db(Connection conn) {
        this.conn = conn;
    }

    public static Db open(String path) {
        try {
            Connection c = DriverManager.getConnection("jdbc:sqlite:" + path);
            try (Statement s = c.createStatement()) {
                s.execute("PRAGMA foreign_keys = ON");
                s.execute("PRAGMA synchronous = FULL");
            }
            Db db = new Db(c);
            db.migrate();
            return db;
        } catch (SQLException e) {
            throw new IllegalStateException("cannot open database " + path, e);
        }
    }

    public Connection conn() {
        return conn;
    }

    private void migrate() {
        try (Statement s = conn.createStatement()) {
            Integer version = schemaVersion(s);
            if (version == null) {
                // Fresh database: create current schema directly.
                s.executeUpdate(V1_SCHEMA);
                for (String stmt : V2_UPGRADE.split(";")) {
                    if (!stmt.isBlank()) s.executeUpdate(stmt);
                }
                setSchemaVersion(SCHEMA_VERSION);
            } else if (version == 1) {
                for (String stmt : V2_UPGRADE.split(";")) {
                    if (!stmt.isBlank()) s.executeUpdate(stmt);
                }
                setSchemaVersion(SCHEMA_VERSION);
            } else if (version != SCHEMA_VERSION) {
                throw new IllegalStateException("unsupported schema version " + version);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("migration failed", e);
        }
    }

    private Integer schemaVersion(Statement s) throws SQLException {
        try (var rs = s.executeQuery(
                "SELECT value FROM meta WHERE key='schema_version'")) {
            return rs.next() ? Integer.parseInt(rs.getString(1)) : null;
        } catch (SQLException e) {
            return null; // meta table does not exist yet
        }
    }

    private void setSchemaVersion(int v) throws SQLException {
        try (var ps = conn.prepareStatement(
                "INSERT INTO meta(key, value) VALUES('schema_version', ?) " +
                "ON CONFLICT(key) DO UPDATE SET value=excluded.value")) {
            ps.setString(1, String.valueOf(v));
            ps.executeUpdate();
        }
    }

    @Override
    public void close() {
        try {
            conn.close();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
