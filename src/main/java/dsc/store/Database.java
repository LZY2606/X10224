package dsc.store;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.Consumer;

/**
 * SQLite connection owner. Writes run inside an explicit transaction; the failure hook lets
 * tests kill the process (or abort the connection) mid-commit so that after restart the store
 * shows either the old version or a complete new version — never a torn write.
 */
public final class Database implements AutoCloseable {
    private final Connection conn;
    private Consumer<String> beforeCommitHook;

    public Database(String path) {
        try {
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection("jdbc:sqlite:" + path);
            conn.setAutoCommit(false);
            applySchema();
            conn.commit();
        } catch (Exception e) {
            throw new RuntimeException("cannot open database " + path, e);
        }
    }

    public Connection connection() {
        return conn;
    }

    /** In-memory database used by tests. */
    public static Database memory() {
        return new Database(":memory:");
    }

    public void setBeforeCommitHook(Consumer<String> hook) {
        this.beforeCommitHook = hook;
    }

    private void applySchema() throws SQLException {
        int version = 0;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='meta'")) {
            if (rs.next()) {
                try (ResultSet vr = st.executeQuery("SELECT value FROM meta WHERE key='schema_version'")) {
                    if (vr.next()) version = Integer.parseInt(vr.getString(1));
                }
            }
        }
        if (version == 0) {
            try (Statement st = conn.createStatement()) {
                for (String ddl : Schema.V1.split(";")) {
                    if (!ddl.trim().isEmpty()) st.execute(ddl);
                }
                st.execute("INSERT OR REPLACE INTO meta(key, value) VALUES('schema_version', '1')");
            }
            version = 1;
        }
        if (version < 2) {
            try (Statement st = conn.createStatement()) {
                st.execute(Schema.V2);
                st.execute("UPDATE meta SET value='2' WHERE key='schema_version'");
            }
        }
    }

    /**
     * Run a unit of writes transactionally. {@code label} is passed to the pre-commit hook;
     * if the hook throws, the transaction is rolled back and the exception propagates,
     * modelling a crash before commit.
     */
    public void tx(String label, Runnable body) {
        try {
            body.run();
            if (beforeCommitHook != null) beforeCommitHook.accept(label);
            conn.commit();
        } catch (RuntimeException e) {
            try { conn.rollback(); } catch (SQLException ignored) { }
            throw e;
        }
    }

    /** Simulated hard crash: abandons the connection without commit or rollback. */
    public void crashBeforeCommit(String label, Runnable body) {
        body.run();
        if (beforeCommitHook != null) beforeCommitHook.accept(label);
        // Intentionally no commit and no rollback: close the connection as a killed process would.
        try { conn.close(); } catch (SQLException ignored) { }
    }

    public void commit() {
        try { conn.commit(); } catch (SQLException e) { throw new RuntimeException(e); }
    }

    @Override
    public void close() {
        try { conn.close(); } catch (SQLException e) { throw new RuntimeException(e); }
    }
}
