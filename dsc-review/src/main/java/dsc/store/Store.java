package dsc.store;

import dsc.model.DecisionState;
import dsc.model.EventKind;
import dsc.model.JournalEvent;
import dsc.model.RevisionRef;
import dsc.service.Json;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * SQLite-backed append-only journal. Writes are one transaction: the new
 * event row and the branch-head pointer switch commit together, so a crash
 * mid-write leaves either the old version or the complete new version.
 *
 * A {@link CrashHook} can force failure between row insert and commit; the
 * store is also reopened against the same file by the recovery tests.
 */
public final class Store implements AutoCloseable {

    private final String url;
    private final Connection conn;
    private CrashHook crashHook = () -> {};

    public interface CrashHook { void beforeCommit() throws SQLException; }

    public Store(String dbPath) {
        this.url = "jdbc:sqlite:" + dbPath;
        try {
            this.conn = DriverManager.getConnection(url);
            configure(conn);
            migrate(conn);
        } catch (SQLException e) {
            throw new IllegalStateException("cannot open store " + dbPath, e);
        }
    }

    public void setCrashHook(CrashHook hook) { this.crashHook = hook; }

    private static void configure(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA foreign_keys=ON");
            st.execute("PRAGMA synchronous=FULL");
        }
        c.setAutoCommit(false);
    }

    static void migrate(Connection c) throws SQLException {
        int version;
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA user_version")) {
            rs.next();
            version = rs.getInt(1);
        }
        if (version < 1) {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("""
                    CREATE TABLE events (
                        seq INTEGER PRIMARY KEY,
                        branch_id TEXT NOT NULL,
                        parent_seq INTEGER NOT NULL,
                        kind TEXT NOT NULL,
                        state TEXT NOT NULL,
                        actor TEXT NOT NULL,
                        ts INTEGER NOT NULL,
                        summary TEXT NOT NULL,
                        payload TEXT NOT NULL,
                        basis TEXT NOT NULL,
                        outputs TEXT NOT NULL,
                        base_revision TEXT NOT NULL
                    )""");
                st.executeUpdate("""
                    CREATE TABLE branches (
                        branch_id TEXT PRIMARY KEY,
                        head_seq INTEGER NOT NULL,
                        created_from_seq INTEGER NOT NULL
                    )""");
                st.executeUpdate("""
                    CREATE TABLE meta (
                        key TEXT PRIMARY KEY,
                        value TEXT NOT NULL
                    )""");
                st.executeUpdate("INSERT INTO branches VALUES ('main', 0, 0)");
                st.executeUpdate("PRAGMA user_version=1");
            }
        }
        if (version >= 1 && version < 2) {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("ALTER TABLE events ADD COLUMN schema_note TEXT");
                st.executeUpdate("PRAGMA user_version=2");
            }
        }
        c.commit();
    }

    public synchronized List<JournalEvent> loadEvents() {
        return loadEvents(Long.MAX_VALUE);
    }

    public synchronized List<JournalEvent> loadEvents(long uptoSeq) {
        List<JournalEvent> out = new ArrayList<>();
        String sql = "SELECT seq,branch_id,parent_seq,kind,state,actor,ts,summary,payload,basis,outputs,base_revision "
                + "FROM events WHERE seq <= ? ORDER BY seq";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, uptoSeq);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(row(rs));
            }
            conn.commit();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    private JournalEvent row(ResultSet rs) throws SQLException {
        return new JournalEvent(
                rs.getLong("seq"), rs.getString("branch_id"), rs.getLong("parent_seq"),
                EventKind.valueOf(rs.getString("kind")), DecisionState.valueOf(rs.getString("state")),
                rs.getString("actor"), rs.getLong("ts"), rs.getString("summary"),
                rs.getString("payload"),
                parseRefs(rs.getString("basis")), parseIds(rs.getString("outputs")),
                rs.getString("base_revision"));
    }
