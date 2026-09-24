package dsc.store;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.Function;

/**
 * SQLite connection holder. All multi-row writes go through {@link #tx} so a crash leaves either
 * the old pointer or the whole new version. PRAGMA settings favor durability (FULL synchronous
 * WAL) which is what the write-interruption tests exercise.
 */
public class Database implements AutoCloseable {

    private final Connection cx;
    private int schemaVersion;

    public Database(String path) {
        try {
            Class.forName("org.sqlite.JDBC");
            String url = "jdbc:sqlite:" + path;
            cx = DriverManager.getConnection(url);
            cx.setAutoCommit(true);
            try (Statement st = cx.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA synchronous=FULL");
                st.execute("PRAGMA foreign_keys=ON");
                st.execute("PRAGMA busy_timeout=10000");
            }
            migrate();
        } catch (Exception e) {
            throw new RuntimeException("open db " + path, e);
        }
    }

    public Connection raw() { return cx; }

    public int schemaVersion() { return schemaVersion; }

    private void migrate() throws SQLException {
        boolean hasTables;
        try (Statement st = cx.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT count(*) FROM sqlite_master WHERE type='table' AND name='meta'")) {
            rs.next();
            hasTables = rs.getInt(1) > 0;
        }
        if (!hasTables) {
            createV2();
            return;
        }
        try (PreparedStatement ps = cx.prepareStatement("SELECT value FROM meta WHERE key='schema_version'")) {
            ResultSet rs = ps.executeQuery();
            schemaVersion = rs.next() ? Integer.parseInt(rs.getString(1)) : 1;
        } catch (SQLException e) {
            schemaVersion = 1;
        }
        if (schemaVersion < 2) migrateV1ToV2();
    }

    private void createV2() throws SQLException {
        String sql = readResource("/db/schema-v2.sql");
        cx.setAutoCommit(false);
        try (Statement st = cx.createStatement()) {
            for (String s : sql.split(";")) {
                if (!s.isBlank()) st.execute(s);
            }
            st.execute("INSERT OR REPLACE INTO meta(key,value) VALUES('schema_version','2')");
        }
        cx.commit();
        cx.setAutoCommit(true);
        schemaVersion = 2;
    }

    /**
     * v1 -> v2: derivations/dependencies/diagnostics become branch scoped on 'main'.
     * Uses table rebuilds because SQLite cannot add NOT NULL/primary-key columns in place.
     */
    private void migrateV1ToV2() throws SQLException {
        cx.setAutoCommit(false);
        try (Statement st = cx.createStatement()) {
            st.execute("ALTER TABLE derivation RENAME TO derivation_v1");
            st.execute("""
                CREATE TABLE derivation (
                  id TEXT NOT NULL, version INTEGER NOT NULL, sample_id TEXT NOT NULL,
                  branch_id TEXT NOT NULL,
                  kind TEXT NOT NULL, segment_id TEXT, interval_id TEXT, is_current INTEGER NOT NULL,
                  status TEXT NOT NULL, method TEXT, candidate_status TEXT NOT NULL, basis_json TEXT,
                  message TEXT, created_by TEXT, created_at INTEGER NOT NULL,
                  PRIMARY KEY (id, version, branch_id))""");
            st.execute("""
                INSERT INTO derivation
                  (id,version,sample_id,branch_id,kind,segment_id,interval_id,is_current,status,
                   method,candidate_status,basis_json,message,created_by,created_at)
                SELECT id,version,sample_id,'main',kind,segment_id,interval_id,is_current,status,
                   method,candidate_status,basis_json,message,created_by,created_at
                FROM derivation_v1""");
            st.execute("DROP TABLE derivation_v1");

            st.execute("ALTER TABLE derivation_dep RENAME TO derivation_dep_v1");
            st.execute("""
                CREATE TABLE derivation_dep (
                  branch_id TEXT NOT NULL, derivation_id TEXT NOT NULL, derivation_version INTEGER NOT NULL,
                  ref_type TEXT NOT NULL, ref_id TEXT NOT NULL, ref_version INTEGER NOT NULL,
                  PRIMARY KEY (branch_id, derivation_id, derivation_version, ref_type, ref_id))""");
            st.execute("""
                INSERT INTO derivation_dep
                  (branch_id,derivation_id,derivation_version,ref_type,ref_id,ref_version)
                SELECT 'main',derivation_id,derivation_version,ref_type,ref_id,ref_version
                FROM derivation_dep_v1""");
            st.execute("DROP TABLE derivation_dep_v1");

            try {
                st.execute("ALTER TABLE diagnostic ADD COLUMN branch_id TEXT NOT NULL DEFAULT 'main'");
            } catch (SQLException ignore) { }

            st.execute("INSERT OR REPLACE INTO meta(key,value) VALUES('schema_version','2')");
            cx.commit();
        } catch (SQLException e) {
            cx.rollback();
            throw e;
        } finally {
            cx.setAutoCommit(true);
        }
        schemaVersion = 2;
    }

    public <T> T tx(Function<Connection, T> body) {
        try {
            cx.setAutoCommit(false);
            T result = body.apply(cx);
            cx.commit();
            return result;
        } catch (RuntimeException e) {
            try { cx.rollback(); } catch (SQLException ignored) { }
            throw e;
        } catch (SQLException e) {
            try { cx.rollback(); } catch (SQLException ignored) { }
            throw new RuntimeException(e);
        } finally {
            try { cx.setAutoCommit(true); } catch (SQLException ignored) { }
        }
    }

    @Override public void close() {
        try { cx.close(); } catch (SQLException ignored) { }
    }

    static String readResource(String name) {
        try {
            return new String(Database.class.getResourceAsStream(name).readAllBytes());
        } catch (Exception e) {
            throw new RuntimeException("missing resource " + name, e);
        }
    }
}
