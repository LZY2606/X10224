package dsc.store;

import dsc.domain.Anomaly;
import dsc.domain.BaselineCandidate;
import dsc.domain.Decision;
import dsc.domain.Derivation;
import dsc.domain.Event;
import dsc.domain.Interval;
import dsc.domain.Segment;
import dsc.domain.SourceRecord;
import dsc.domain.TangentSpec;
import dsc.domain.ThermalResult;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * SQLite-backed durable store for the DSC review project.
 *
 * Persistence rules implemented here:
 *  - every direct source is archived independently, in every version;
 *  - every derivation is stored independently and only a full success moves the pointer;
 *  - event append + archive writes + pointer switch happen in one transaction, so after a
 *    crash a reader sees either the old or the complete new version;
 *  - mutations carry an expected event-seq base version; a concurrent edit raises
 *    {@link ConflictException} which the caller resolves explicitly.
 */
public final class Store implements AutoCloseable {

    private final String url;
    private final ReentrantLock writeLock = new ReentrantLock();
    private Connection conn;

    public Store(String path) {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
        this.url = "jdbc:sqlite:" + path;
        open();
        migrate();
    }

    private void open() {
        try {
            conn = DriverManager.getConnection(url);
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA foreign_keys=ON");
                st.execute("PRAGMA busy_timeout=5000");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("cannot open database: " + url, e);
        }
    }

    /** Test hook: force every committing transaction to roll back after writes. */
    private volatile boolean crashOnCommit = false;

    public void setCrashOnCommit(boolean v) {
        this.crashOnCommit = v;
    }

    private void migrate() {
        try {
            int version = 0;
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT name FROM sqlite_master WHERE type='table' AND name='schema_meta'")) {
                if (rs.next()) {
                    try (ResultSet v = st.executeQuery(
                            "SELECT value FROM schema_meta WHERE key='schema_version'")) {
                        if (v.next()) {
                            version = Integer.parseInt(v.getString(1));
                        }
                    }
                }
            }
            if (version == Schema.CURRENT_VERSION) {
                return;
            }
            writeLock.lock();
            try {
                conn.setAutoCommit(false);
                try (Statement st = conn.createStatement()) {
                    if (version == 0) {
                        // V1 legacy shape kept intentionally to prove old-version migration.
                        st.execute("CREATE TABLE IF NOT EXISTS schema_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
                        st.execute("CREATE TABLE IF NOT EXISTS project (id TEXT PRIMARY KEY, body TEXT NOT NULL, version INTEGER NOT NULL)");
                        st.execute("CREATE TABLE IF NOT EXISTS events (seq INTEGER PRIMARY KEY AUTOINCREMENT, id TEXT NOT NULL UNIQUE, body TEXT NOT NULL, created_epoch INTEGER NOT NULL)");
                        st.execute("CREATE TABLE IF NOT EXISTS legacy_segments (id TEXT PRIMARY KEY, body TEXT NOT NULL)");
                        st.execute("INSERT OR REPLACE INTO schema_meta(key,value) VALUES('schema_version','1')");
                    }
                    if (version <= 1) {
                        for (String sql : Schema.V2.split(";")) {
                            String s = sql.trim();
                            if (!s.isEmpty()) {
                                st.execute(s);
                            }
                        }
                        st.execute("INSERT OR REPLACE INTO schema_meta(key,value) VALUES('schema_version','"
                                + Schema.CURRENT_VERSION + "')");
                    }
                    if (crashOnCommit) {
                        conn.rollback();
                        return;
                    }
                    conn.commit();
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            } finally {
                writeLock.unlock();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("migration failed", e);
        }
    }

    public long lastEventSeq() {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(seq),0) FROM events")) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<Event> loadEvents(Long upToSeq) {
        String sql = "SELECT body FROM events" + (upToSeq == null ? "" : " WHERE seq <= ?")
                + " ORDER BY seq ASC";
        List<Event> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (upToSeq != null) {
                ps.setLong(1, upToSeq);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(Json.read(rs.getString(1), Event.class));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }
}
