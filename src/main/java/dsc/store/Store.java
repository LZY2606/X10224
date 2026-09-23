package dsc.store;

import dsc.json.Json;
import dsc.model.BaselineCandidate;
import dsc.model.Direction;
import dsc.model.EventRecord;
import dsc.model.SamplePoint;
import dsc.model.Segment;
import dsc.model.SourceRecord;
import dsc.model.Study;
import dsc.model.Transition;
import dsc.units.Quantity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SQLite-backed event archive. All writes go through
 * {@link #withTransaction(Write)}; a derivation writes its new versions into
 * the transaction and only flips the current pointers at the end, so a
 * crash mid-write leaves the database at the previous complete state
 * (journal_mode=WAL guarantees readers never observe a torn transaction).
 */
public class Store implements AutoCloseable {

    private final Connection conn;
    private boolean crashBeforeCommit = false;

    public Store(Path dbFile) {
        try {
            String url = "jdbc:sqlite:" + dbFile.toAbsolutePath();
            this.conn = DriverManager.getConnection(url);
            configure();
            int version = userVersion();
            if (version == 0) {
                createFresh();
            } else {
                migrate(version);
            }
        } catch (SQLException e) {
            throw new StoreException("cannot open " + dbFile, e);
        }
    }

    /** Test/tooling hook: create against an in-memory database. */
    public static Store memory() {
        try {
            Connection c = DriverManager.getConnection("jdbc:sqlite::memory:");
            Store s = new Store(c);
            return s;
        } catch (SQLException e) {
            throw new StoreException("in-memory db failed", e);
        }
    }

    private Store(Connection c) throws SQLException {
        this.conn = c;
        configure();
        createFresh();
    }

    private void configure() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA foreign_keys=ON");
            st.execute("PRAGMA busy_timeout=5000");
        }
    }

    private int userVersion() throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA user_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private void createFresh() throws SQLException {
        try (Statement st = conn.createStatement()) {
            for (String ddl : Schema.V1.split(";")) {
                if (!ddl.isBlank()) st.execute(ddl);
            }
            st.execute(Schema.V2_ADD);
            st.execute("PRAGMA user_version=" + Schema.CURRENT_VERSION);
        }
    }

    /**
     * Migration from an older on-disk schema. Migration itself is
     * transactional; a crash leaves the old database intact.
     */
    void migrate(int fromVersion) throws SQLException {
        if (fromVersion >= Schema.CURRENT_VERSION) return;
        boolean prevAuto = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try (Statement st = conn.createStatement()) {
            if (fromVersion == 1) {
                st.execute(Schema.V2_ADD);
                st.execute("PRAGMA user_version=" + Schema.CURRENT_VERSION);
            }
            if (crashBeforeCommit) {
                throw new SQLException("simulated crash during migration");
            }
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(prevAuto);
        }
    }

    void setCrashBeforeCommit(boolean v) {
        this.crashBeforeCommit = v;
    }

    // ------------------------------------------------------------- locking

    /**
     * Begin a write transaction, enforcing the optimistic concurrency rule:
     * two editors working from the same baseSeq cannot both commit — the
     * loser receives {@link ConflictException} and must reconcile.
     */
    public synchronized <T> T withTransaction(long expectedBaseSeq, String studyId,
                                               Write<T> body) {
        try {
            conn.setAutoCommit(false);
            Tx tx = new Tx(conn, this);
            T result = body.run(tx);
            long currentHead = tx.headSeq(studyId);
            if (expectedBaseSeq >= 0 && currentHead != expectedBaseSeq) {
                conn.rollback();
                throw new ConflictException(
                        "study " + studyId + " moved from seq " + expectedBaseSeq
                                + " to " + currentHead + " by another user; reconcile required");
            }
            if (crashBeforeCommit) {
                // Simulated power loss: the process dies before COMMIT.
                conn.rollback();
                throw new SQLException("simulated crash before commit");
            }
            conn.commit();
            return result;
        } catch (ConflictException ce) {
            throw ce;
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignored) { }
            throw new StoreException("transaction failed", e);
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) { }
        }
    }

    public <T> T withTransaction(Write<T> body) {
        return withTransaction(-1, null, body);
    }

    public synchronized Connection raw() {
        return conn;
    }

    @Override
    public synchronized void close() {
        try {
            conn.close();
        } catch (SQLException ignored) {
        }
    }

    /** A database snapshot file (for export). */
    public static void backupTo(Connection c, Path target) {
        try {
            Files.createDirectories(target.getParent());
            try (Statement st = c.createStatement()) {
                st.execute("BACKUP TO '" + target.toAbsolutePath().toString().replace("'", "''")
                        + "'");
            }
        } catch (SQLException e) {
            throw new StoreException("backup failed", e);
        }
    }

    // ------------------------------------------------------------- readers

    public List<Study> listStudies() {
        List<Study> out = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT id,material_code,description,created_at,head_seq,base_seq "
                             + "FROM study ORDER BY id")) {
            while (rs.next()) {
                out.add(new Study(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getLong(5), rs.getLong(6)));
            }
        } catch (SQLException e) {
            throw new StoreException("listStudies", e);
        }
        return out;
    }

    public Study study(String id) {
        return withTransaction(tx -> {
            String sql = "SELECT id,material_code,description,created_at,head_seq,base_seq "
                    + "FROM study WHERE id=?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;
                    return new Study(rs.getString(1), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getLong(5), rs.getLong(6));
                }
            }
        });
    }

    /** Append-only event ledger. */
    public List<EventRecord> events(String studyId) {
        List<EventRecord> out = new ArrayList<>();
        String sql = "SELECT seq,study_id,type,actor,subject_id,rule_code,rationale,"
                + "affected_ids,payload,at FROM event_log WHERE study_id=? ORDER BY seq";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, studyId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    List<String> aff = new ArrayList<>();
                    String affText = rs.getString(8);
                    if (affText != null && !affText.isBlank()) {
                        for (Object o : Json.parseArray(affText)) aff.add(o.toString());
                    }
                    Map<String, Object> payload = new LinkedHashMap<>();
                    String p = rs.getString(9);
                    if (p != null && !p.isBlank()) payload = Json.parseObject(p);
                    out.add(new EventRecord(rs.getLong(1), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getString(5), rs.getString(6),
                            rs.getString(7), aff, payload, rs.getString(10)));
                }
            }
        } catch (SQLException e) {
            throw new StoreException("events", e);
        }
        return out;
    }

    // --------------------------------------------------------- row mapping

    static SourceRecord mapSource(ResultSet rs, Connection c) throws SQLException {
        String id = rs.getString("id");
        int version = rs.getInt("version");
        List<SamplePoint> points = new ArrayList<>();
        String pSql = "SELECT idx,temp_value,temp_unit,temp_raw,temp_convertible,"
                + "hf_value,hf_unit,hf_raw,hf_convertible,diagnostic FROM raw_point "
                + "WHERE source_id=? AND version=? ORDER BY idx";
        try (PreparedStatement ps = c.prepareStatement(pSql)) {
            ps.setString(1, id);
            ps.setInt(2, version);
            try (ResultSet prs = ps.executeQuery()) {
                while (prs.next()) {
                    Quantity t = prs.getInt(5) == 1
                            ? Quantity.of(prs.getDouble(2), prs.getString(3), prs.getString(4))
                            : Quantity.unconvertible(prs.getString(4), prs.getString(3));
                    Quantity hf = prs.getInt(9) == 1
                            ? Quantity.of(prs.getDouble(6), prs.getString(7), prs.getString(8))
                            : Quantity.unconvertible(prs.getString(8), prs.getString(7));
                    points.add(new SamplePoint(prs.getInt(1), t, hf, prs.getString(10)));
                }
            }
        }
        return new SourceRecord(id, rs.getString("study_id"),
                SourceRecord.Type.valueOf(rs.getString("type")), version,
                rs.getString("supersedes_id"), rs.getString("segment_id"),
                rs.getString("unit"), rs.getDouble("value"), rs.getString("raw_text"),
                rs.getString("basis"), rs.getString("recorded_at"),
                rs.getString("recorded_by"), points, rs.getInt("retired") == 1);
    }

    static Segment mapSegment(ResultSet rs, Connection c) throws SQLException {
        String id = rs.getString("id");
        List<String> diags = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT text FROM segment_diag WHERE segment_id=? ORDER BY idx")) {
            ps.setString(1, id);
            try (ResultSet drs = ps.executeQuery()) {
                while (drs.next()) diags.add(drs.getString(1));
            }
        }
        return new Segment(id, rs.getString("study_id"), rs.getInt("ord"),
                rs.getString("cycle"), Direction.valueOf(rs.getString("direction")),
                rs.getString("direction_basis"), rs.getString("trace_source_id"),
                rs.getString("rate_source_id"), rs.getString("atmosphere_source_id"),
                rs.getString("calibration_source_id"), rs.getDouble("t_start_k"),
                rs.getDouble("t_end_k"), rs.getInt("point_count"),
                rs.getInt("reversed_signal") == 1, rs.getInt("non_uniform") == 1, diags);
    }

    static BaselineCandidate mapBaseline(ResultSet rs, Connection c) throws SQLException {
        String id = rs.getString("id");
        int version = rs.getInt("version");
        List<String> diags = new ArrayList<>();
        List<String> deps = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT text FROM baseline_diag WHERE baseline_id=? AND version=? ORDER BY idx")) {
            ps.setString(1, id);
            ps.setInt(2, version);
            try (ResultSet drs = ps.executeQuery()) {
                while (drs.next()) diags.add(drs.getString(1));
            }
        }
        String depText = rs.getString("depends_on");
        if (depText != null && !depText.isBlank()) {
            for (Object o : Json.parseArray(depText)) deps.add(o.toString());
        }
        return new BaselineCandidate(id, rs.getString("study_id"), rs.getString("segment_id"),
                version, BaselineCandidate.Method.valueOf(rs.getString("method")),
                rs.getDouble("left_t_k"), rs.getDouble("right_t_k"),
                rs.getDouble("left_hf_w"), rs.getDouble("right_hf_w"),
                rs.getDouble("slope_w_per_k"), rs.getDouble("intercept_w"),
                BaselineCandidate.Status.valueOf(rs.getString("status")),
                rs.getString("decided_by"), diags, deps, rs.getInt("created_seq"),
                rs.getInt("decided_seq"));
    }

    static Transition mapTransition(ResultSet rs, Connection c) throws SQLException {
        String id = rs.getString("id");
        int version = rs.getInt("version");
        List<Transition.ErrorSource> errs = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT code,description,contribution_jg,source_id FROM transition_error "
                        + "WHERE transition_id=? AND version=? ORDER BY idx")) {
            ps.setString(1, id);
            ps.setInt(2, version);
            try (ResultSet ers = ps.executeQuery()) {
                while (ers.next()) {
                    errs.add(new Transition.ErrorSource(ers.getString(1), ers.getString(2),
                            ers.getDouble(3), ers.getString(4)));
                }
            }
        }
        List<String> deps = new ArrayList<>();
        String depText = rs.getString("depends_on");
        if (depText != null && !depText.isBlank()) {
            for (Object o : Json.parseArray(depText)) deps.add(o.toString());
        }
        return new Transition(id, rs.getString("study_id"), rs.getString("segment_id"),
                rs.getString("baseline_id"), version,
                Transition.Kind.valueOf(rs.getString("kind")),
                rs.getDouble("onset_t_k"), rs.getDouble("peak_t_k"),
                rs.getDouble("endset_t_k"), rs.getDouble("enthalpy_jg"),
                rs.getDouble("enthalpy_error_jg"), errs, deps,
                rs.getInt("stale") == 1, rs.getInt("created_seq"), rs.getInt("stale_seq"));
    }

    // ------------------------------------------------------------- helpers

    static Map<String, Object> payloadOf(ResultSet rs) throws SQLException {
        String p = rs.getString("payload");
        return p == null || p.isBlank() ? new LinkedHashMap<>() : Json.parseObject(p);
    }

    /** Functional transaction body. */
    @FunctionalInterface
    public interface Write<T> {
        T run(Tx tx) throws SQLException;
    }

    public static class ConflictException extends RuntimeException {
        public ConflictException(String m) { super(m); }
    }

    public static class StoreException extends RuntimeException {
        public StoreException(String m, Throwable c) { super(m, c); }
    }
}
