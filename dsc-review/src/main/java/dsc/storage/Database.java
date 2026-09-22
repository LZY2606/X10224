package dsc.storage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * SQLite connection holder. WAL + foreign keys give us crash-safe single-file
 * storage; every state-changing service method runs in one transaction so a
 * crash between writes leaves either the old complete state or the new one.
 */
public final class Database implements AutoCloseable {

    private final String url;
    private final Connection cx;

    public Database(String path) {
        this.url = "jdbc:sqlite:" + path;
        try {
            Class.forName("org.sqlite.JDBC");
            this.cx = DriverManager.getConnection(url);
            try (var st = cx.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA foreign_keys=ON");
                st.execute("PRAGMA busy_timeout=10000");
                st.execute("PRAGMA synchronous=FULL");
            }
            Migrator.migrate(cx);
        } catch (ClassNotFoundException | SQLException e) {
            throw new StorageException("cannot open database " + path, e);
        }
    }

    public Connection connection() { return cx; }

    /** Test-only hook: perform a hard rollback to simulate power loss mid-commit. */
    public void crashSimulateHardRollback() {
        try {
            cx.rollback();
        } catch (SQLException ignored) {
            // connection may already be dead; that is the point of the simulation
        }
    }

    @Override public void close() {
        try { cx.close(); } catch (SQLException ignored) {}
    }
}
