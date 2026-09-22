package dsc.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dsc.domain.Model.*;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Storage layer. Sources of each kind are archived separately and versioned.
 *  Every derivation is an independent version; the current pointer switches
 *  atomically inside the same transaction, so after an interrupted write a
 *  restart sees either the old version or a complete new one. */
public final class Store {
    private final Db db;
    private final ObjectMapper om = new ObjectMapper();

    public Store(Db db) {
        this.db = db;
    }

    public ObjectMapper om() {
        return om;
    }

    public static String newId(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private static String now() {
        return Instant.now().toString();
    }

    // ---------------------------------------------------------------- samples

    public synchronized String createSample(String name) {
        String id = newId("smp");
        exec("INSERT INTO sample(id, name, created_at) VALUES(?,?,?)", id, name, now());
        return id;
    }

    public synchronized List<Map<String, Object>> samples() {
        return queryMaps("SELECT * FROM sample ORDER BY created_at, id");
    }

    // ---------------------------------------------------------------- sources

    /** Archive a new version of a source record. Kinds are stored separately. */
    public synchronized String addSource(String sampleId, SourceKind kind, JsonNode payload) {
        int version = latestSourceVersion(sampleId, kind) + 1;
        String supersedes = latestSourceId(sampleId, kind);
        String id = newId("src");
        exec("INSERT INTO source_record(id, sample_id, kind, version, payload, created_at, supersedes)" +
             " VALUES(?,?,?,?,?,?,?)",
             id, sampleId, kind.name(), version, payload.toString(), now(), supersedes);
        return id;
    }

    public int latestSourceVersion(String sampleId, SourceKind kind) {
        return queryInt("SELECT COALESCE(MAX(version),0) FROM source_record WHERE sample_id=? AND kind=?",
                sampleId, kind.name());
    }

    public String latestSourceId(String sampleId, SourceKind kind) {
        List<Map<String, Object>> rows = queryMaps(
                "SELECT id FROM source_record WHERE sample_id=? AND kind=? ORDER BY version DESC LIMIT 1",
                sampleId, kind.name());
        return rows.isEmpty() ? null : (String) rows.get(0).get("id");
    }

    public SourceRecord source(String id) {
        List<Map<String, Object>> rows = queryMaps("SELECT * FROM source_record WHERE id=?", id);
        if (rows.isEmpty()) return null;
        Map<String, Object> r = rows.get(0);
        return new SourceRecord((String) r.get("id"), (String) r.get("sample_id"),
                SourceKind.valueOf((String) r.get("kind")), ((Number) r.get("version")).intValue(),
                (String) r.get("payload"), (String) r.get("created_at"), (String) r.get("supersedes"));
    }

    public List<SourceRecord> sourcesOf(String sampleId) {
        List<SourceRecord> out = new ArrayList<>();
        for (Map<String, Object> r : queryMaps(
                "SELECT * FROM source_record WHERE sample_id=? ORDER BY kind, version", sampleId)) {
            out.add(new SourceRecord((String) r.get("id"), (String) r.get("sample_id"),
                    SourceKind.valueOf((String) r.get("kind")), ((Number) r.get("version")).intValue(),
                    (String) r.get("payload"), (String) r.get("created_at"), (String) r.get("supersedes")));
        }
        return out;
    }

    // ------------------------------------------------------------ derivations

    /** Commit a derivation version and switch the current pointer atomically.
     *  The preCommitHook runs inside the transaction; if it throws (simulated
     *  write interruption) everything rolls back and the old pointer remains. */
    public synchronized String commitDerivation(String sampleId, DerivationKind kind,
            Map<String, String> inputs, JsonNode payload, List<Diagnostic> diagnostics,
            Runnable preCommitHook) {
        int version = currentVersion(sampleId, kind) + 1;
        String id = newId("der");
        try {
            db.conn().setAutoCommit(false);
            try (PreparedStatement ps = db.conn().prepareStatement(
                    "INSERT INTO derivation(id, sample_id, kind, version, status, inputs, payload, diagnostics, created_at)" +
                    " VALUES(?,?,?,?,?,?,?,?,?)")) {
                ps.setString(1, id);
                ps.setString(2, sampleId);
                ps.setString(3, kind.name());
                ps.setInt(4, version);
                ps.setString(5, DerivationStatus.COMPLETE.name());
                ps.setString(6, om.writeValueAsString(inputs));
                ps.setString(7, payload.toString());
                ps.setString(8, om.writeValueAsString(diagnostics));
                ps.setString(9, now());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = db.conn().prepareStatement(
                    "INSERT INTO current_pointer(sample_id, kind, derivation_id) VALUES(?,?,?) " +
                    "ON CONFLICT(sample_id, kind) DO UPDATE SET derivation_id=excluded.derivation_id")) {
                ps.setString(1, sampleId);
                ps.setString(2, kind.name());
                ps.setString(3, id);
                ps.executeUpdate();
            }
            if (preCommitHook != null) preCommitHook.run();
            db.conn().commit();
        } catch (Exception e) {
            try {
                db.conn().rollback();
            } catch (SQLException ignored) { }
            throw new IllegalStateException("derivation commit rolled back", e);
        } finally {
            try {
                db.conn().setAutoCommit(true);
            } catch (SQLException ignored) { }
        }
        return id;
    }

    public String commitDerivation(String sampleId, DerivationKind kind,
            Map<String, String> inputs, JsonNode payload, List<Diagnostic> diagnostics) {
        return commitDerivation(sampleId, kind, inputs, payload, diagnostics, null);
    }

    /** Optimistic commit: when the current version no longer matches the base the
     *  caller started from, the new derivation is stored but NOT made current and
     *  a resolvable conflict is created instead. Returns the conflict id or null. */
    public synchronized String commitDecision(String sampleId, DerivationKind kind, int expectedBaseVersion,
            Map<String, String> inputs, JsonNode payload, List<Diagnostic> diagnostics, String user) {
        int current = currentVersion(sampleId, kind);
        if (current == expectedBaseVersion) {
            commitDerivation(sampleId, kind, inputs, payload, diagnostics);
            return null;
        }
        // Conflicting branch: keep both sides, do not overwrite.
        int version = current + 1;
        String id = newId("der");
        exec("INSERT INTO derivation(id, sample_id, kind, version, status, inputs, payload, diagnostics, created_at)" +
             " VALUES(?,?,?,?,?,?,?,?,?)",
             id, sampleId, kind.name(), version, DerivationStatus.COMPLETE.name(),
             json(inputs), payload.toString(), json(diagnostics), now());
        String left = currentDerivationId(sampleId, kind);
        String conflictId = newId("cfl");
        exec("INSERT INTO conflict(id, sample_id, kind, base_version, left_derivation, right_derivation, status, resolution, created_at)" +
             " VALUES(?,?,?,?,?,?,?,?,?)",
             conflictId, sampleId, kind.name(), expectedBaseVersion, left, id, "OPEN", null, now());
        addEvent(sampleId, "CONFLICT_DETECTED", om.valueToTree(Map.of(
                "conflictId", conflictId,
                "kind", kind.name(),
                "baseVersion", expectedBaseVersion,
                "currentVersion", current,
                "left", left,
                "right", id,
                "secondUser", user,
                "rule", "two editors from the same base version produce a resolvable conflict; neither branch is overwritten")));
        return conflictId;
    }

    public synchronized void resolveConflict(String conflictId, String winnerDerivationId, String user) {
        Conflict c = conflict(conflictId);
        if (c == null || !"OPEN".equals(c.status())) {
            throw new IllegalStateException("conflict not open: " + conflictId);
        }
        String loser = winnerDerivationId.equals(c.leftDerivation()) ? c.rightDerivation() : c.leftDerivation();
        exec("UPDATE current_pointer SET derivation_id=? WHERE sample_id=? AND kind=?",
                winnerDerivationId, c.sampleId(), c.kind());
        exec("UPDATE conflict SET status='RESOLVED', resolution=? WHERE id=?",
                winnerDerivationId, conflictId);
        addEvent(c.sampleId(), "CONFLICT_RESOLVED", om.valueToTree(Map.of(
                "conflictId", conflictId,
                "winner", winnerDerivationId,
                "loserKept", loser,
                "resolvedBy", user,
                "rule", "unselected candidate is preserved; confirmation is not an irreversible overwrite")));
    }

    public Conflict conflict(String id) {
        List<Map<String, Object>> rows = queryMaps("SELECT * FROM conflict WHERE id=?", id);
        if (rows.isEmpty()) return null;
        return toConflict(rows.get(0));
    }

    public List<Conflict> conflictsOf(String sampleId) {
        List<Conflict> out = new ArrayList<>();
        for (Map<String, Object> r : queryMaps(
                "SELECT * FROM conflict WHERE sample_id=? ORDER BY created_at, id", sampleId)) {
            out.add(toConflict(r));
        }
        return out;
    }

    private Conflict toConflict(Map<String, Object> r) {
        return new Conflict((String) r.get("id"), (String) r.get("sample_id"), (String) r.get("kind"),
                ((Number) r.get("base_version")).intValue(), (String) r.get("left_derivation"),
                (String) r.get("right_derivation"), (String) r.get("status"),
                (String) r.get("resolution"), (String) r.get("created_at"));
    }

    public int currentVersion(String sampleId, DerivationKind kind) {
        List<Map<String, Object>> rows = queryMaps(
                "SELECT d.version FROM current_pointer p JOIN derivation d ON d.id = p.derivation_id" +
                " WHERE p.sample_id=? AND p.kind=?", sampleId, kind.name());
        return rows.isEmpty() ? 0 : ((Number) rows.get(0).get("version")).intValue();
    }

    public String currentDerivationId(String sampleId, DerivationKind kind) {
        List<Map<String, Object>> rows = queryMaps(
                "SELECT derivation_id FROM current_pointer WHERE sample_id=? AND kind=?",
                sampleId, kind.name());
        return rows.isEmpty() ? null : (String) rows.get(0).get("derivation_id");
    }

    public Derivation derivation(String id) {
        List<Map<String, Object>> rows = queryMaps("SELECT * FROM derivation WHERE id=?", id);
        if (rows.isEmpty()) return null;
        return toDerivation(rows.get(0));
    }

    public List<Derivation> derivationsOf(String sampleId) {
        List<Derivation> out = new ArrayList<>();
        for (Map<String, Object> r : queryMaps(
                "SELECT * FROM derivation WHERE sample_id=? ORDER BY kind, version", sampleId)) {
            out.add(toDerivation(r));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Derivation toDerivation(Map<String, Object> r) {
        try {
            return new Derivation((String) r.get("id"), (String) r.get("sample_id"),
                    DerivationKind.valueOf((String) r.get("kind")), ((Number) r.get("version")).intValue(),
                    DerivationStatus.valueOf((String) r.get("status")),
                    om.readValue((String) r.get("inputs"), Map.class),
                    (String) r.get("payload"), (String) r.get("diagnostics"), (String) r.get("created_at"));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public synchronized void markStale(String derivationId) {
        exec("UPDATE derivation SET status='STALE' WHERE id=?", derivationId);
    }

    // ---------------------------------------------------------------- events

    /** Append a machine-readable judgment event with its basis. */
    public synchronized String addEvent(String sampleId, String type, JsonNode basis) {
        long seq = queryInt("SELECT COALESCE(MAX(seq),0) FROM event") + 1L;
        String id = newId("evt");
        exec("INSERT INTO event(id, seq, sample_id, type, basis, created_at) VALUES(?,?,?,?,?,?)",
                id, seq, sampleId, type, basis.toString(), now());
        return id;
    }

    public List<Event> eventsOf(String sampleId) {
        List<Event> out = new ArrayList<>();
        for (Map<String, Object> r : queryMaps(
                "SELECT * FROM event WHERE sample_id=? ORDER BY seq", sampleId)) {
            out.add(new Event((String) r.get("id"), ((Number) r.get("seq")).longValue(),
                    (String) r.get("sample_id"), (String) r.get("type"),
                    (String) r.get("basis"), (String) r.get("created_at")));
        }
        return out;
    }

    public List<Event> allEvents() {
        List<Event> out = new ArrayList<>();
        for (Map<String, Object> r : queryMaps("SELECT * FROM event ORDER BY seq")) {
            out.add(new Event((String) r.get("id"), ((Number) r.get("seq")).longValue(),
                    (String) r.get("sample_id"), (String) r.get("type"),
                    (String) r.get("basis"), (String) r.get("created_at")));
        }
        return out;
    }

    // ---------------------------------------------------------------- reset

    public synchronized void reset() {
        for (String table : List.of("event", "conflict", "current_pointer", "derivation",
                "source_record", "sample")) {
            exec("DELETE FROM " + table);
        }
    }

    // ---------------------------------------------------------------- helpers

    private String json(Object o) {
        try {
            return om.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void exec(String sql, Object... args) {
        try (PreparedStatement ps = db.conn().prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                if (args[i] == null) ps.setNull(i + 1, java.sql.Types.VARCHAR);
                else ps.setObject(i + 1, args[i]);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(sql, e);
        }
    }

    private int queryInt(String sql, Object... args) {
        List<Map<String, Object>> rows = queryMaps(sql, args);
        return rows.isEmpty() ? 0 : ((Number) rows.get(0).values().iterator().next()).intValue();
    }

    public List<Map<String, Object>> queryMaps(String sql, Object... args) {
        try (PreparedStatement ps = db.conn().prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> out = new ArrayList<>();
                int n = rs.getMetaData().getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= n; i++) {
                        row.put(rs.getMetaData().getColumnName(i), rs.getObject(i));
                    }
                    out.add(row);
                }
                return out;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(sql, e);
        }
    }
}
