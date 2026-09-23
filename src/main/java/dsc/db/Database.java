package dsc.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/** SQLite access + schema migrations. Each migration runs in its own transaction. */
public final class Database implements AutoCloseable {
    private final Connection conn;

    /** Schema migrations, in order. Index 0 -> version 1, etc. */
    public static final List<String> MIGRATIONS = List.of(
        // version 1: versioned sources, segments, derived objects, pointers, events
        """
        CREATE TABLE source_archives(
          id TEXT PRIMARY KEY,
          kind TEXT NOT NULL,
          version INTEGER NOT NULL,
          label TEXT NOT NULL,
          unit TEXT,
          raw_repr TEXT NOT NULL,
          payload TEXT NOT NULL,
          supersedes TEXT,
          created_at TEXT NOT NULL,
          UNIQUE(kind, version)
        );
        CREATE TABLE segments(
          id TEXT PRIMARY KEY,
          archive_id TEXT NOT NULL REFERENCES source_archives(id),
          seg_type TEXT NOT NULL,
          cycle_index INTEGER NOT NULL,
          rate_k_per_min REAL NOT NULL,
          meta TEXT NOT NULL,
          point_count INTEGER NOT NULL,
          t_min REAL NOT NULL,
          t_max REAL NOT NULL
        );
        CREATE TABLE derived_objects(
          id TEXT PRIMARY KEY,
          kind TEXT NOT NULL,
          series TEXT NOT NULL,
          segment_id TEXT,
          version INTEGER NOT NULL,
          status TEXT NOT NULL,
          params TEXT NOT NULL,
          result TEXT NOT NULL,
          deps TEXT NOT NULL,
          explanation TEXT,
          created_seq INTEGER NOT NULL
        );
        CREATE INDEX idx_derived_series ON derived_objects(series, version);
        CREATE TABLE current_pointers(
          scope TEXT PRIMARY KEY,
          derived_id TEXT NOT NULL REFERENCES derived_objects(id),
          seq INTEGER NOT NULL
        );
        CREATE TABLE pointer_history(
          scope TEXT NOT NULL,
          derived_id TEXT NOT NULL,
          seq INTEGER NOT NULL,
          PRIMARY KEY(scope, seq)
        );
        CREATE TABLE events(
          seq INTEGER PRIMARY KEY AUTOINCREMENT,
          id TEXT NOT NULL UNIQUE,
          kind TEXT NOT NULL,
          payload TEXT NOT NULL,
          created_at TEXT NOT NULL
        );
        """,
        // version 2: collaborative review docs + conflict records
        """
        CREATE TABLE review_docs(
          id TEXT PRIMARY KEY,
          version INTEGER NOT NULL,
          body TEXT NOT NULL
        );
        CREATE TABLE conflicts(
          id TEXT PRIMARY KEY,
          doc_id TEXT NOT NULL,
          base_version INTEGER NOT NULL,
          loser_body TEXT NOT NULL,
          winner_body TEXT NOT NULL,
          resolved INTEGER NOT NULL DEFAULT 0,
          created_at TEXT NOT NULL
        );
        """
    );

    public Database(String path) {
        try {
            conn = DriverManager.getConnection("jdbc:sqlite:" + path);
            try (Statement s = conn.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL");
                s.execute("PRAGMA foreign_keys=ON");
                s.execute("PRAGMA busy_timeout=5000");
            }
            migrate();
        } catch (SQLException e) {
            throw new IllegalStateException("cannot open database " + path, e);
        }
    }

    public Connection conn() { return conn; }

    private void migrate() throws SQLException {
        try (Statement s = conn.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS schema_migrations(version INTEGER PRIMARY KEY, applied_at TEXT NOT NULL)");
        }
        int current = schemaVersion();
        for (int v = current + 1; v <= MIGRATIONS.size(); v++) {
            conn.setAutoCommit(false);
            try (Statement s = conn.createStatement()) {
                for (String stmt : MIGRATIONS.get(v - 1).split(";")) {
                    if (!stmt.isBlank()) s.execute(stmt);
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO schema_migrations(version, applied_at) VALUES(?, datetime('now'))")) {
                    ps.setInt(1, v);
                    ps.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw new IllegalStateException("migration to v" + v + " failed", e);
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public int schemaVersion() throws SQLException {
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT COALESCE(MAX(version),0) FROM schema_migrations")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    @Override public void close() {
        try { conn.close(); } catch (SQLException ignored) {}
    }
}
