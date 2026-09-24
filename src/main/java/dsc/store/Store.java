package dsc.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dsc.domain.Derivation;
import dsc.domain.Event;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite-backed append-only archive.
 *
 * Atomicity: appending events + moving derivation current-pointers happens in
 * one transaction. A crash (or {@link #crashBeforeCommit()}) leaves either the
 * old complete version or the new complete version, never a half-written one.
 *
 * Concurrency: commits must name the expected head event seq; a stale client
 * loses with a recoverable {@link ConflictException}, and both branches are
 * kept - a confirmation can never irrevocably overwrite another editor.
 */
public class Store implements AutoCloseable {
    public static final ObjectMapper M = new ObjectMapper();
    public static final int SCHEMA_VERSION = 2;

    private final Connection conn;

    public Store(Path dbFile) {
        try {
            String url = "jdbc:sqlite:" + dbFile.toAbsolutePath();
            this.conn = DriverManager.getConnection(url);
            init();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** In-memory database (tests). */
    public static Store memory() {
        try {
            Connection c = DriverManager.getConnection("jdbc:sqlite::memory:");
            Store s = new Store(c);
            return s;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Store(Connection c) {
        this.conn = c;
        init();
    }

    private void init() {
        try {
            int v = userVersion();
            if (v == 0) {
                ddl();
                conn.createStatement().execute("PRAGMA user_version = " + SCHEMA_VERSION);
            } else if (v < SCHEMA_VERSION) {
                migrate(v);
            }
            conn.createStatement().execute("PRAGMA foreign_keys = ON");
            conn.createStatement().execute("PRAGMA journal_mode=WAL");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private int userVersion() throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA user_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private void ddl() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE schema_meta(
                    k TEXT PRIMARY KEY,
                    v TEXT NOT NULL
                )""");
            st.executeUpdate("""
                CREATE TABLE branches(
                    name TEXT PRIMARY KEY,
                    head_seq INTEGER NOT NULL,
                    head_event_id TEXT,
                    base_seq INTEGER NOT NULL DEFAULT -1,
                    parent_branch TEXT,
                    created_at INTEGER NOT NULL
                )""");
            st.executeUpdate("""
                CREATE TABLE events(
                    seq INTEGER NOT NULL,
                    branch TEXT NOT NULL,
                    id TEXT NOT NULL,
                    type TEXT NOT NULL,
                    parent_id TEXT,
                    base_seq INTEGER NOT NULL DEFAULT -1,
                    actor TEXT,
                    created_at INTEGER NOT NULL,
                    target_id TEXT,
                    payload TEXT NOT NULL,
                    PRIMARY KEY(branch, seq),
                    UNIQUE(branch, id)
                )""");
            st.executeUpdate("""
                CREATE TABLE derivations(
                    id TEXT PRIMARY KEY,
                    dkey TEXT NOT NULL,
                    kind TEXT NOT NULL,
                    branch TEXT NOT NULL,
                    input_event_seq INTEGER NOT NULL,
                    rule_version TEXT NOT NULL,
                    deps TEXT NOT NULL,
                    output TEXT NOT NULL,
                    diagnostics TEXT NOT NULL,
                    status TEXT NOT NULL
                )""");
            st.executeUpdate("""
                CREATE TABLE current_pointers(
                    branch TEXT NOT NULL,
                    dkey TEXT NOT NULL,
                    derivation_id TEXT NOT NULL,
                    PRIMARY KEY(branch, dkey)
                )""");
            st.executeUpdate("INSERT INTO schema_meta(k,v) VALUES('schema_version','"
                    + SCHEMA_VERSION + "')");
        }
    }

    /** Deterministic old-version migration path, exercised by tests. */
    private void migrate(int from) throws SQLException {
        if (from == 1) {
            // v1 archives lacked current_pointers.status snapshot column intent;
            // real v1->2: ensure pointers table exists, then stamp version.
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS current_pointers(
                        branch TEXT NOT NULL,
                        dkey TEXT NOT NULL,
                        derivation_id TEXT NOT NULL,
                        PRIMARY KEY(branch, dkey))""");
            }
        }
        conn.createStatement().execute("PRAGMA user_version = " + SCHEMA_VERSION);
    }

    // ---- branches ----------------------------------------------------------

    public void ensureBranch(String branch) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR IGNORE INTO branches(name,head_seq,head_event_id,base_seq,parent_branch,created_at)"
                        + " VALUES(?,?,?,?,?,?)")) {
            ps.setString(1, branch);
            ps.setLong(2, -1);
            ps.setString(3, null);
            ps.setLong(4, -1);
            ps.setString(5, null);
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public record BranchInfo(String name, long headSeq, String headEventId, long baseSeq, String parent) {}

    public List<BranchInfo> branches() {
        List<BranchInfo> r = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT name,head_seq,head_event_id,base_seq,parent_branch FROM branches ORDER BY name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                r.add(new BranchInfo(rs.getString(1), rs.getLong(2), rs.getString(3),
                        rs.getLong(4), rs.getString(5)));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return r;
    }

    public long headSeq(String branch) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT head_seq FROM branches WHERE name=?")) {
            ps.setString(1, branch);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static class ConflictException extends RuntimeException {
        public final long expected;
        public final long actual;
        public ConflictException(long expected, long actual) {
            super("Conflict: expected head seq " + expected + " but branch head is " + actual
                    + "; resolve by forking before committing");
            this.expected = expected;
            this.actual = actual;
        }
    }

    /** A prepared batch: raw events whose seq is assigned at commit time. */
    public record PendingEvent(String type, String id, String parentId, long baseSeq,
                               String actor, String targetId, JsonNode payload) {}

    public record CommitResult(List<Event> events, List<Derivation> derivations,
                               java.util.Map<String,String> reasons) {}

    /** True to simulate power loss between writing rows and COMMIT. */
    private volatile boolean crashOnNextCommit = false;
    public void crashBeforeCommit() { crashOnNextCommit = true; }

    /**
     * Atomically append events and install derivation versions. Derivations are
     * upserted (content-addressed), and current pointers move inside the same
     * transaction, so readers always see one consistent generation.
     */
    public synchronized CommitResult commit(String branch, long expectedHeadSeq,
                                            List<PendingEvent> events, List<Derivation> derivations,
                                            java.util.Map<String,String> reasons) {
        ensureBranch(branch);
        try {
            long actual = headSeq(branch);
            if (actual != expectedHeadSeq) throw new ConflictException(expectedHeadSeq, actual);
            conn.setAutoCommit(false);
            long seq = actual;
            String parentId = headEventId(branch);
            List<Event> assigned = new ArrayList<>();
            long now = System.currentTimeMillis();
            try {
                for (PendingEvent pe : events) {
                    if (Event.BRANCH_FORKED.equals(pe.type())) {
                        registerFork(pe);
                    }
                    seq++;
                    Event ev = new Event(pe.id(), seq, pe.type(), branch,
                            pe.parentId() == null ? parentId : pe.parentId(),
                            pe.baseSeq() < 0 ? baseSeqOf(branch) : pe.baseSeq(),
                            pe.actor() == null ? "reviewer" : pe.actor(),
                            now, pe.targetId(), pe.payload());
                    insertEvent(ev);
                    assigned.add(ev);
                    parentId = pe.id();
                }
                for (Derivation d : derivations) {
                    upsertDerivation(d);
                    try (PreparedStatement ps = conn.prepareStatement("""
                        INSERT INTO current_pointers(branch,dkey,derivation_id) VALUES(?,?,?)
                        ON CONFLICT(branch,dkey) DO UPDATE SET derivation_id=excluded.derivation_id""")) {
                        ps.setString(1, branch);
                        ps.setString(2, d.key());
                        ps.setString(3, d.id());
                        ps.executeUpdate();
                    }
                }
                if (crashOnNextCommit) {
                    crashOnNextCommit = false;
                    // simulate abrupt process kill: connection gone before COMMIT
                    try { conn.close(); } catch (SQLException ignored) {}
                    throw new SQLException("SIMULATED_CRASH_BEFORE_COMMIT");
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE branches SET head_seq=?, head_event_id=? WHERE name=?")) {
                    ps.setLong(1, seq);
                    ps.setString(2, parentId);
                    ps.setString(3, branch);
                    ps.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                if ("SIMULATED_CRASH_BEFORE_COMMIT".equals(e.getMessage())) {
                    throw new RuntimeException("SIMULATED_CRASH_BEFORE_COMMIT");
                }
                throw e;
            } finally {
                try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
            }
            return new CommitResult(assigned, derivations, reasons);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private long baseSeqOf(String branch) {
        for (BranchInfo b : branches()) if (b.name().equals(branch)) return b.baseSeq();
        return -1;
    }

    private String headEventId(String branch) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT head_event_id FROM branches WHERE name=?")) {
            ps.setString(1, branch);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void registerFork(PendingEvent pe) throws SQLException {
        JsonNode p = pe.payload();
        String name = p.path("branch").asText();
        long baseSeq = p.path("baseSeq").asLong(-1);
        String parent = p.path("parentBranch").asText("main");
        ensureBranch(name);
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE branches SET base_seq=?, parent_branch=? WHERE name=?")) {
            ps.setLong(1, baseSeq);
            ps.setString(2, parent);
            ps.setString(3, name);
            ps.executeUpdate();
        }
    }

    private void insertEvent(Event e) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
            INSERT INTO events(seq,branch,id,type,parent_id,base_seq,actor,created_at,target_id,payload)
            VALUES(?,?,?,?,?,?,?,?,?,?)""")) {
            ps.setLong(1, e.seq());
            ps.setString(2, e.branch());
            ps.setString(3, e.id());
            ps.setString(4, e.type());
            ps.setString(5, e.parentId());
            ps.setLong(6, e.baseSeq());
            ps.setString(7, e.actor());
            ps.setLong(8, e.createdAt());
            ps.setString(9, e.targetId());
            ps.setString(10, M.writeValueAsString(e.payload()));
            ps.executeUpdate();
        } catch (Exception e2) {
            throw new SQLException(e2);
        }
    }

    private void upsertDerivation(Derivation d) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
            INSERT INTO derivations(id,dkey,kind,branch,input_event_seq,rule_version,deps,output,diagnostics,status)
            VALUES(?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(id) DO NOTHING""")) {
            ps.setString(1, d.id());
            ps.setString(2, d.key());
            ps.setString(3, d.kind());
            ps.setString(4, d.branch());
            ps.setLong(5, d.inputEventSeq());
            ps.setString(6, d.ruleVersion());
            ps.setString(7, d.deps());
            ps.setString(8, M.writeValueAsString(d.output()));
            ps.setString(9, M.writeValueAsString(d.diagnostics()));
            ps.setString(10, d.status());
            ps.executeUpdate();
        } catch (Exception e2) {
            throw new SQLException(e2);
        }
    }

    // ---- reads -------------------------------------------------------------

    /**
     * Events visible from a branch in replay order. A fork's ancestry is linear
     * (one parent branch): the parent's own prefix up to baseSeq, then this
     * branch's events with seq > baseSeq.
     */
    public List<Event> eventsOnBranch(String branch) {
        BranchInfo info = branchInfo(branch);
        List<Event> r = new ArrayList<>();
        if (info != null && info.parent() != null && !info.parent().equals(branch)) {
            r.addAll(prefixOf(info.parent(), info.baseSeq()));
        }
        long from = (info != null && info.parent() != null) ? info.baseSeq() + 1 : 0;
        r.addAll(rawEvents(branch, from, Long.MAX_VALUE));
        return r;
    }

    private List<Event> prefixOf(String branch, long upToSeq) {
        BranchInfo info = branchInfo(branch);
        List<Event> r = new ArrayList<>();
        if (info != null && info.parent() != null && !info.parent().equals(branch)) {
            r.addAll(prefixOf(info.parent(), Math.min(info.baseSeq(), upToSeq)));
        }
        long from = (info != null && info.parent() != null) ? info.baseSeq() + 1 : 0;
        for (Event e : rawEvents(branch, from, upToSeq)) r.add(e);
        return r;
    }

    private BranchInfo branchInfo(String branch) {
        for (BranchInfo b : branches()) if (b.name().equals(branch)) return b;
        return null;
    }

    private String parentBranch(String branch) {
        for (BranchInfo b : branches()) if (b.name().equals(branch)) return b.parent();
        return null;
    }

    private List<Event> rawEvents(String branch, long fromSeq, long toSeq) {
        List<Event> r = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT seq,id,type,parent_id,base_seq,actor,created_at,target_id,payload FROM events "
                        + "WHERE branch=? AND seq>=? AND seq<=? ORDER BY seq")) {
            ps.setString(1, branch);
            ps.setLong(2, fromSeq);
            ps.setLong(3, toSeq);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JsonNode payload = M.readTree(rs.getString(9));
                    r.add(new Event(rs.getString(2), rs.getLong(1), rs.getString(3), branch,
                            rs.getString(4), rs.getLong(5), rs.getString(6), rs.getLong(7),
                            rs.getString(8), payload));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return r;
    }

    public List<Event> allEvents() {
        List<Event> r = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT seq,id,type,branch,parent_id,base_seq,actor,created_at,target_id,payload "
                        + "FROM events ORDER BY branch,seq");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                JsonNode payload = M.readTree(rs.getString(10));
                r.add(new Event(rs.getString(2), rs.getLong(1), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getLong(6), rs.getString(7), rs.getLong(8),
                        rs.getString(9), payload));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return r;
    }

    public Derivation derivation(String id) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM derivations WHERE id=?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapDerivation(rs) : null;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public List<Derivation> currentDerivations(String branch) {
        List<Derivation> r = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("""
            SELECT d.* FROM derivations d JOIN current_pointers p ON p.derivation_id=d.id
            WHERE p.branch=? ORDER BY d.dkey""")) {
            ps.setString(1, branch);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) r.add(mapDerivation(rs));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return r;
    }

    private Derivation mapDerivation(ResultSet rs) throws Exception {
        return new Derivation(rs.getString("id"), rs.getString("dkey"), rs.getString("kind"),
                rs.getLong("input_event_seq"), rs.getString("branch"), rs.getString("rule_version"),
                rs.getString("deps"), M.readTree(rs.getString("output")),
                M.readTree(rs.getString("diagnostics")), rs.getString("status"));
    }

    public record RawEvent(long seq, String branch, PendingEvent pe) {}

    public synchronized void bulkLoadOrdered(JsonNode branchesNode, List<RawEvent> events,
                                             List<Derivation> derivations) {
        try {
            conn.setAutoCommit(false);
            try {
                if (branchesNode != null) {
                    for (JsonNode b : branchesNode) {
                        String name = b.path("name").asText();
                        ensureBranch(name);
                        try (PreparedStatement ps = conn.prepareStatement(
                                "UPDATE branches SET head_seq=?,head_event_id=?,base_seq=?,parent_branch=? WHERE name=?")) {
                            ps.setLong(1, b.path("headSeq").asLong());
                            JsonNode heid = b.path("headEventId");
                            ps.setString(2, heid == null || heid.isNull() ? null : heid.asText());
                            ps.setLong(3, b.path("baseSeq").asLong(-1));
                            JsonNode pb = b.path("parentBranch");
                            ps.setString(4, pb == null || pb.isNull() ? null : pb.asText());
                            ps.setString(5, name);
                            ps.executeUpdate();
                        }
                    }
                }
                for (RawEvent re : events) {
                    PendingEvent pe = re.pe();
                    Event ev = new Event(pe.id(), re.seq(), pe.type(), re.branch(), pe.parentId(),
                            pe.baseSeq(), pe.actor(), 0, pe.targetId(), pe.payload());
                    insertEvent(ev);
                }
                for (Derivation d : derivations) {
                    upsertDerivation(d);
                    try (PreparedStatement ps = conn.prepareStatement("""
                        INSERT INTO current_pointers(branch,dkey,derivation_id) VALUES(?,?,?)
                        ON CONFLICT(branch,dkey) DO UPDATE SET derivation_id=excluded.derivation_id""")) {
                        ps.setString(1, d.branch());
                        ps.setString(2, d.key());
                        ps.setString(3, d.id());
                        ps.executeUpdate();
                    }
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public int schemaVersion() {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA user_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /** Delete the local database file (the UI's "delete local database" action). */
    public static void deleteDbFile(Path dbFile) {
        try {
            Files.deleteIfExists(dbFile);
            Files.deleteIfExists(Path.of(dbFile + "-wal"));
            Files.deleteIfExists(Path.of(dbFile + "-shm"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        try { conn.close(); } catch (SQLException ignored) {}
    }
}
