package dsc.store;

import com.fasterxml.jackson.databind.JsonNode;
import dsc.event.DscEvent;
import dsc.util.Json;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Append-only event log over SQLite with:
 * <ul>
 *   <li>content-stable event ids and a verified hash chain;</li>
 *   <li>optimistic concurrency via the command's observed basis version;</li>
 *   <li>atomic head pointer movement (derivation rows + pointer in one transaction);</li>
 *   <li>an injectable pre-commit hook to simulate a crash mid-write;</li>
 *   <li>schema migration from v1 databases.</li>
 * </ul>
 */
public final class EventStore implements AutoCloseable {

    /** Invoked inside the append transaction immediately before commit. */
    public interface PreCommitHook {
        void beforeCommit(long nextSeq, Connection connection) throws Exception;
    }

    private final String dbUrl;
    private final Connection conn;
    private final Object writeLock = new Object();
    private volatile PreCommitHook hook;
    private final AtomicLong conflictSalt = new AtomicLong();

    public EventStore(String path) {
        this.dbUrl = "jdbc:sqlite:" + path;
        try {
            this.conn = DriverManager.getConnection(dbUrl);
            this.conn.setAutoCommit(true);
        } catch (SQLException e) {
            throw new IllegalStateException("cannot open " + path, e);
        }
        configure();
        migrate();
    }

    /** Test/backup constructor around an existing connection (used by crash simulations). */
    private EventStore(Connection existing) {
        this.dbUrl = null;
        this.conn = existing;
        configure();
        migrate();
    }

    public static EventStore onConnection(Connection connection) {
        return new EventStore(connection);
    }

    private void configure() {
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA foreign_keys=ON");
            st.execute("PRAGMA busy_timeout=10000");
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public void setPreCommitHook(PreCommitHook hook) {
        this.hook = hook;
    }

    // ---------------------------------------------------------------- migration

    private void migrate() {
        try {
            int version = currentSchemaVersion();
            if (version == 0) {
                try (Statement st = conn.createStatement()) {
                    for (String ddl : Schema.V1_DDL.split(";")) {
                        if (!ddl.isBlank()) st.execute(ddl);
                    }
                    st.execute("INSERT OR REPLACE INTO meta(key, value) VALUES('schema_version','1')");
                }
                version = 1;
            }
            if (version == 1) {
                try (Statement st = conn.createStatement()) {
                    st.execute(Schema.V2_DDL);
                    for (String ddl : Schema.V2_EXTRA.split(";")) {
                        if (!ddl.isBlank()) st.execute(ddl);
                    }
                    st.execute("UPDATE meta SET value='2' WHERE key='schema_version'");
                }
                version = 2;
            }
            if (version != Schema.CURRENT_VERSION) {
                throw new IllegalStateException("unsupported schema version " + version);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("migration failed", e);
        }
    }

    private int currentSchemaVersion() throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='table' AND name='meta'")) {
            if (!rs.next()) return 0;
        }
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT value FROM meta WHERE key='schema_version'")) {
            return rs.next() ? Integer.parseInt(rs.getString(1)) : 1;
        }
    }

    public int schemaVersion() {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT value FROM meta WHERE key='schema_version'")) {
            return rs.next() ? Integer.parseInt(rs.getString(1)) : 0;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------------------------------------------------------------- append

    /**
     * Append one event. {@code expectedBasis} is the state version the author observed; when the
     * log has already advanced past it, a {@link OptimisticConflictException} is raised instead of
     * a silent last-write-wins overwrite.
     */
    public DscEvent append(String type, String actor, String ruleVersion,
                           long expectedBasis, JsonNode payload) {
        return append(type, actor, ruleVersion, expectedBasis, payload, true);
    }

    public DscEvent append(String type, String actor, String ruleVersion,
                           long expectedBasis, JsonNode payload, boolean enforceBasis) {
        synchronized (writeLock) {
            try {
                long head = headSeq();
                if (enforceBasis && expectedBasis >= 0 && expectedBasis < head) {
                    throw new OptimisticConflictException("STALE_BASIS",
                            "basis v" + expectedBasis + " is older than current v" + head);
                }
                conn.setAutoCommit(false);
                long nextSeq = head + 1;
                String prevHash = hashAt(head);
                DscEvent event = new DscEvent(nextSeq, type, actor, ruleVersion,
                        expectedBasis, payload, prevHash);
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO events(id, seq, type, actor, created_at, rule_version, "
                                + "basis_version, payload, prev_hash, hash) VALUES(?,?,?,?,?,?,?,?,?,?)")) {
                    ps.setString(1, event.id());
                    ps.setLong(2, event.seq());
                    ps.setString(3, event.type());
                    ps.setString(4, event.actor());
                    ps.setString(5, event.createdAt());
                    ps.setString(6, event.ruleVersion());
                    ps.setLong(7, event.basisVersion());
                    ps.setString(8, event.payload() == null ? "{}" : event.payload().toString());
                    ps.setString(9, event.prevHash());
                    ps.setString(10, event.hash());
                    ps.executeUpdate();
                }
                PreCommitHook current = hook;
                if (current != null) {
                    current.beforeCommit(nextSeq, conn);
                }
                conn.commit();
                conn.setAutoCommit(true);
                return event;
            } catch (OptimisticConflictException e) {
                rollbackQuietly();
                throw e;
            } catch (Exception e) {
                rollbackQuietly();
                if (e.getMessage() != null && e.getMessage().contains("UNIQUE")) {
                    throw new OptimisticConflictException("DUP_EVENT", e.getMessage());
                }
                throw new IllegalStateException("append failed", e);
            }
        }
    }

    private void rollbackQuietly() {
        try {
            conn.rollback();
        } catch (SQLException ignored) {
        }
        try {
            conn.setAutoCommit(true);
        } catch (SQLException ignored) {
        }
    }

    public long headSeq() {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(seq),0) FROM events")) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private String hashAt(long seq) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT hash FROM events WHERE seq = ?")) {
            ps.setLong(1, seq);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : "";
            }
        }
    }

    public List<DscEvent> loadAll() {
        List<DscEvent> out = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT id, seq, type, actor, created_at, rule_version, basis_version, "
                             + "payload, prev_hash, hash FROM events ORDER BY seq")) {
            while (rs.next()) {
                out.add(map(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        verifyChain(out);
        return out;
    }

    public List<DscEvent> loadUpTo(long seq) {
        List<DscEvent> all = loadAll();
        List<DscEvent> out = new ArrayList<>();
        for (DscEvent e : all) {
            if (e.seq() <= seq) out.add(e);
        }
        return out;
    }

    private DscEvent map(ResultSet rs) throws SQLException {
        DscEvent e = new DscEvent();
        e.setId(rs.getString("id"));
        e.setSeq(rs.getLong("seq"));
        e.setType(rs.getString("type"));
        e.setActor(rs.getString("actor"));
        e.setCreatedAt(rs.getString("created_at"));
        e.setRuleVersion(rs.getString("rule_version"));
        e.setBasisVersion(rs.getLong("basis_version"));
        e.setPayload(Json.MAPPER.readTree(rs.getString("payload")));
        e.setPrevHash(rs.getString("prev_hash"));
        e.setHash(rs.getString("hash"));
        return e;
    }

    public void verifyChain(List<DscEvent> events) {
        String prev = "";
        for (DscEvent e : events) {
            if (!e.prevHash().equals(prev)) {
                throw new IllegalStateException("hash chain broken at seq " + e.seq());
            }
            if (!e.hashValid()) {
                throw new IllegalStateException("event content tampered at seq " + e.seq());
            }
            prev = e.hash();
        }
    }

    // ---------------------------------------------------------------- derivations + head

    /**
     * Atomically persist derived projections and advance the current pointer. Either the whole
     * new state is visible after commit or the old pointer remains.
     */
    public void commitState(long stateVersion, long eventSeq, String ruleVersion,
                            String rulesDigest, List<NodeSnapshot> nodes) {
        synchronized (writeLock) {
            try {
                conn.setAutoCommit(false);
                try (PreparedStatement del = conn.prepareStatement(
                        "DELETE FROM derivations WHERE state_version = ?")) {
                    del.setLong(1, stateVersion);
                    del.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO derivations(state_version, node_id, payload, status) "
                                + "VALUES(?,?,?,?)")) {
                    for (NodeSnapshot node : nodes) {
                        ps.setLong(1, stateVersion);
                        ps.setString(2, node.nodeId());
                        ps.setString(3, node.payload());
                        ps.setString(4, node.status());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT OR REPLACE INTO state_heads(state_version, event_seq, "
                                + "rule_version, rules_digest, created_at) "
                                + "VALUES(?,?,?,?,?)")) {
                    ps.setLong(1, stateVersion);
                    ps.setLong(2, eventSeq);
                    ps.setString(3, ruleVersion);
                    ps.setString(4, rulesDigest == null ? "" : rulesDigest);
                    ps.setString(5, java.time.Instant.now().toString());
                    ps.executeUpdate();
                }
                PreCommitHook current = hook;
                if (current != null) {
                    current.beforeCommit(-stateVersion, conn);
                }
                conn.commit();
                conn.setAutoCommit(true);
            } catch (Exception e) {
                rollbackQuietly();
                throw new IllegalStateException("commitState failed", e);
            }
        }
    }

    public long currentHeadVersion() {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COALESCE(MAX(state_version),0) FROM state_heads")) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<NodeSnapshot> loadState(long stateVersion) {
        List<NodeSnapshot> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT node_id, payload, status FROM derivations WHERE state_version = ?")) {
            ps.setLong(1, stateVersion);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new NodeSnapshot(rs.getString(1), rs.getString(2), rs.getString(3)));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    public long nextConflictSalt() {
        return conflictSalt.incrementAndGet();
    }

    // ---------------------------------------------------------------- import

    /**
     * Import a foreign log. Stable ids, order and source relations are preserved: the chain is
     * rebuilt with the original seq numbers and content. The database must be empty; the caller
     * models "delete local database then import" by pointing at a fresh file.
     */
    public int importLog(List<DscEvent> events) {
        synchronized (writeLock) {
            if (headSeq() != 0) {
                throw new IllegalStateException("import target database must be empty");
            }
            verifyChain(events);
            try {
                conn.setAutoCommit(false);
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO events(id, seq, type, actor, created_at, rule_version, "
                                + "basis_version, payload, prev_hash, hash) VALUES(?,?,?,?,?,?,?,?,?,?)")) {
                    for (DscEvent e : events) {
                        ps.setString(1, e.id());
                        ps.setLong(2, e.seq());
                        ps.setString(3, e.type());
                        ps.setString(4, e.actor());
                        ps.setString(5, e.createdAt());
                        ps.setString(6, e.ruleVersion());
                        ps.setLong(7, e.basisVersion());
                        ps.setString(8, e.payload().toString());
                        ps.setString(9, e.prevHash());
                        ps.setString(10, e.hash());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                conn.commit();
                conn.setAutoCommit(true);
                return events.size();
            } catch (Exception e) {
                rollbackQuietly();
                throw new IllegalStateException("import failed", e);
            }
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
