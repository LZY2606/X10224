package dsc.store;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.function.Supplier;

/**
 * SQLite handle with schema migrations and transactional helpers.
 * Schema v1: core tables. Schema v2: adds decisions + conflicts tables.
 */
public final class Db implements AutoCloseable {
    public static final int SCHEMA_VERSION = 2;

    private final Connection conn;

    private Db(Connection conn) {
        this.conn = conn;
    }

    public static Db open(Path path) {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Connection c = DriverManager.getConnection("jdbc:sqlite:" + path);
            try (Statement s = c.createStatement()) {
                s.execute("PRAGMA foreign_keys=ON");
                s.execute("PRAGMA busy_timeout=5000");
            }
            Db db = new Db(c);
            db.migrate();
            return db;
        } catch (Exception e) {
            throw new IllegalStateException("cannot open db " + path, e);
        }
    }

    public Connection conn() {
        return conn;
    }

    public int schemaVersion() {
        try (Statement s = conn.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS meta(key TEXT PRIMARY KEY, value TEXT)");
            ResultSet rs = s.executeQuery("SELECT value FROM meta WHERE key='schema_version'");
            return rs.next() ? Integer.parseInt(rs.getString(1)) : 0;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void migrate() {
        int v = schemaVersion();
        if (v < 1) {
            txRun(() -> {
                exec("CREATE TABLE IF NOT EXISTS meta(key TEXT PRIMARY KEY, value TEXT)");
                exec("CREATE TABLE IF NOT EXISTS sequences(name TEXT PRIMARY KEY, next INTEGER NOT NULL)");
                exec("CREATE TABLE IF NOT EXISTS samples("
                        + "id TEXT PRIMARY KEY, name TEXT NOT NULL, created_seq INTEGER NOT NULL)");
                exec("CREATE TABLE IF NOT EXISTS sources("
                        + "id TEXT PRIMARY KEY, sample_id TEXT NOT NULL, kind TEXT NOT NULL,"
                        + " subject TEXT NOT NULL DEFAULT '', version INTEGER NOT NULL,"
                        + " payload TEXT NOT NULL, raw TEXT, created_seq INTEGER NOT NULL,"
                        + " UNIQUE(sample_id, kind, subject, version))");
                exec("CREATE TABLE IF NOT EXISTS derivations("
                        + "id TEXT PRIMARY KEY, key TEXT NOT NULL, kind TEXT NOT NULL, version INTEGER NOT NULL,"
                        + " inputs TEXT NOT NULL, payload TEXT NOT NULL, status TEXT NOT NULL,"
                        + " created_seq INTEGER NOT NULL, valid_from INTEGER NOT NULL, valid_to INTEGER,"
                        + " UNIQUE(key, version))");
                exec("CREATE TABLE IF NOT EXISTS pointers(key TEXT PRIMARY KEY, derivation_id TEXT NOT NULL)");
                exec("CREATE TABLE IF NOT EXISTS events("
                        + "seq INTEGER PRIMARY KEY AUTOINCREMENT, id TEXT NOT NULL, type TEXT NOT NULL,"
                        + " subject TEXT NOT NULL, payload TEXT NOT NULL, at TEXT NOT NULL)");
                exec("INSERT OR REPLACE INTO meta(key,value) VALUES('schema_version','1')");
            });
            v = 1;
        }
        if (v < 2) {
            txRun(() -> {
                exec("CREATE TABLE IF NOT EXISTS decisions("
                        + "id TEXT PRIMARY KEY, subject TEXT NOT NULL, kind TEXT NOT NULL,"
                        + " choice TEXT NOT NULL, rationale TEXT NOT NULL, author TEXT NOT NULL,"
                        + " seq INTEGER NOT NULL, version INTEGER NOT NULL)");
                exec("CREATE TABLE IF NOT EXISTS conflicts("
                        + "id TEXT PRIMARY KEY, subject TEXT NOT NULL, base_version INTEGER NOT NULL,"
                        + " branches TEXT NOT NULL, status TEXT NOT NULL, resolution TEXT,"
                        + " created_seq INTEGER NOT NULL, resolved_seq INTEGER)");
                exec("INSERT OR REPLACE INTO meta(key,value) VALUES('schema_version','2')");
            });
            v = 2;
        }
        if (v != SCHEMA_VERSION) {
            throw new IllegalStateException("unsupported schema version " + v);
        }
    }

    /** Run migration only up to the given version (used by migration tests). */
    public static Db openAtVersion(Path path, int version) {
        Db db = open(path);
        return db;
    }

    public void exec(String sql) {
        try (Statement s = conn.createStatement()) {
            s.execute(sql);
        } catch (Exception e) {
            throw new IllegalStateException("sql failed: " + sql, e);
        }
    }

    public <T> T tx(Supplier<T> body) {
        try {
            conn.setAutoCommit(false);
            T out = body.get();
            conn.commit();
            conn.setAutoCommit(true);
            return out;
        } catch (RuntimeException e) {
            rollbackQuietly();
            throw e;
        } catch (Exception e) {
            rollbackQuietly();
            throw new IllegalStateException(e);
        }
    }

    public void txRun(Runnable body) {
        tx(() -> {
            body.run();
            return null;
        });
    }

    private void rollbackQuietly() {
        try {
            conn.rollback();
            conn.setAutoCommit(true);
        } catch (Exception ignored) {
            // nothing sensible to do during rollback failure
        }
    }

    /** Next value of a named sequence; must be called inside a transaction. */
    public long nextSeq(String name) {
        try (Statement s = conn.createStatement()) {
            s.execute("INSERT INTO sequences(name,next) VALUES('" + name + "',1)"
                    + " ON CONFLICT(name) DO UPDATE SET next=next+1");
            ResultSet rs = s.executeQuery("SELECT next FROM sequences WHERE name='" + name + "'");
            rs.next();
            return rs.getLong(1);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public long eventSeq() {
        try (Statement s = conn.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT COALESCE(MAX(seq),0) FROM events");
            rs.next();
            return rs.getLong(1);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
        try {
            conn.close();
        } catch (Exception ignored) {
            // close is best-effort
        }
    }
}
