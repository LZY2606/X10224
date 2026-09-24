package dsc.store;

import dsc.Json;
import dsc.domain.Records.*;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persistence facade. Sources are archived separately and never mutated; each
 * derivation is a new immutable version and only becomes current when the
 * pointer switches inside the same transaction.
 */
public final class Store implements AutoCloseable {
    private final Connection conn;

    public Store(Path path) {
        this.conn = dsc.db.Database.open(path);
    }

    public Store(Connection conn) {
        this.conn = conn;
    }

    public Connection connection() {
        return conn;
    }

    @Override
    public void close() {
        try {
            conn.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // ------------------------------------------------------------------ events

    /** Append an event outside of a business transaction (event gets committed immediately). */
    public int appendEvent(String type, Map<String, Object> payload) {
        return appendEventInternal(type, payload);
    }

    int appendEventInternal(String type, Map<String, Object> payload) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO event_log(type,payload,created_at) VALUES(?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, type);
            ps.setString(2, Json.write(payload));
            ps.setString(3, Ids.now());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new IllegalStateException("no event seq");
                }
                return keys.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Map<String, Object>> events(int limit) {
        List<Map<String, Object>> out = new ArrayList<>();
        String sql = "SELECT seq,type,payload,created_at FROM event_log ORDER BY seq DESC LIMIT ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("seq", rs.getInt("seq"));
                    row.put("type", rs.getString("type"));
                    row.put("payload", Json.read(rs.getString("payload")));
                    row.put("createdAt", rs.getString("created_at"));
                    out.add(row);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    public Map<String, Object> event(int seq) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT seq,type,payload,created_at FROM event_log WHERE seq=?")) {
            ps.setInt(1, seq);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("seq", rs.getInt("seq"));
                row.put("type", rs.getString("type"));
                row.put("payload", Json.read(rs.getString("payload")));
                row.put("createdAt", rs.getString("created_at"));
                return row;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public int eventCount() {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM event_log")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
    // ----------------------------------------------------------------- sources

    /** Archive one direct source as a new immutable version; emits IMPORT event. */
    public Source archiveSource(String sampleId, String kind, String raw,
                                String mediaType, Map<String, Object> extra) {
        try {
            conn.setAutoCommit(false);
            int version = nextSourceVersion(sampleId, kind);
            String id = "src-" + kind.toLowerCase().replace('_', '-') + "-v" + version + "-"
                    + Ids.sha256(raw).substring(0, 8);
            int eventSeq = 0;
            {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("action", "ARCHIVE_SOURCE");
                payload.put("sourceId", id);
                payload.put("sampleId", sampleId);
                payload.put("kind", kind);
                payload.put("version", version);
                payload.put("hash", Ids.sha256(raw));
                payload.put("inputFragment", raw.length() > 600 ? raw.substring(0, 600) : raw);
                payload.putAll(extra == null ? Map.of() : extra);
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO event_log(type,payload,created_at) VALUES(?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, "IMPORT");
                    ps.setString(2, Json.write(payload));
                    ps.setString(3, Ids.now());
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        keys.next();
                        eventSeq = keys.getInt(1);
                    }
                }
            }
            String createdAt = Ids.now();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO source_document(id,sample_id,kind,version,raw,media_type,"
                            + "hash,created_event,created_at) VALUES(?,?,?,?,?,?,?,?,?)")) {
                ps.setString(1, id);
                ps.setString(2, sampleId);
                ps.setString(3, kind);
                ps.setInt(4, version);
                ps.setString(5, raw);
                ps.setString(6, mediaType);
                ps.setString(7, Ids.sha256(raw));
                ps.setInt(8, eventSeq);
                ps.setString(9, createdAt);
                ps.executeUpdate();
            }
            conn.commit();
            return new Source(id, sampleId, kind, version, raw, mediaType,
                    Ids.sha256(raw), eventSeq, createdAt);
        } catch (SQLException e) {
            rollbackQuietly(conn);
            throw new RuntimeException(e);
        } finally {
            restoreAutoCommit(conn);
        }
    }

    public int nextSourceVersion(String sampleId, String kind) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COALESCE(MAX(version),0) FROM source_document "
                        + "WHERE sample_id=? AND kind=?")) {
            ps.setString(1, sampleId);
            ps.setString(2, kind);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) + 1 : 1;
            }
        }
    }

    public Source latestSource(String sampleId, String kind) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM source_document WHERE sample_id=? AND kind=? "
                        + "ORDER BY version DESC LIMIT 1")) {
            ps.setString(1, sampleId);
            ps.setString(2, kind);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapSource(rs) : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Source> sources(String sampleId) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM source_document WHERE sample_id=? ORDER BY kind,version")) {
            ps.setString(1, sampleId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Source> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(mapSource(rs));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private Source mapSource(ResultSet rs) throws SQLException {
        return new Source(rs.getString("id"), rs.getString("sample_id"),
                rs.getString("kind"), rs.getInt("version"), rs.getString("raw"),
                rs.getString("media_type"), rs.getString("hash"),
                rs.getInt("created_event"), rs.getString("created_at"));
    }

    public void storeRawPoints(String sourceId, List<RawPoint> points) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO raw_point(source_id,seg_index,seq,time_raw,time_unit,"
                        + "temp_raw,temp_unit,hf_raw,hf_unit) VALUES(?,?,?,?,?,?,?,?,?)")) {
            for (RawPoint p : points) {
                ps.setString(1, sourceId);
                ps.setInt(2, p.segIndex());
                ps.setInt(3, p.seq());
                setDouble(ps, 4, p.timeRaw());
                ps.setString(5, p.timeUnit());
                setDouble(ps, 6, p.tempRaw());
                ps.setString(7, p.tempUnit());
                setDouble(ps, 8, p.hfRaw());
                ps.setString(9, p.hfUnit());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<RawPoint> rawPoints(String sourceId) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM raw_point WHERE source_id=? ORDER BY seg_index,seq")) {
            ps.setString(1, sourceId);
            try (ResultSet rs = ps.executeQuery()) {
                List<RawPoint> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new RawPoint(rs.getInt("seg_index"), rs.getInt("seq"),
                            getNullable(rs, "time_raw"), rs.getString("time_unit"),
                            getNullable(rs, "temp_raw"), rs.getString("temp_unit"),
                            getNullable(rs, "hf_raw"), rs.getString("hf_unit")));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static void setDouble(PreparedStatement ps, int idx, Double value) throws SQLException {
        if (value == null) {
            ps.setNull(idx, java.sql.Types.REAL);
        } else {
            ps.setDouble(idx, value);
        }
    }

    private static Double getNullable(ResultSet rs, String column) throws SQLException {
        double v = rs.getDouble(column);
        return rs.wasNull() ? null : v;
    }

    static void rollbackQuietly(Connection c) {
        try {
            c.rollback();
        } catch (SQLException ignored) {
        }
    }

    static void restoreAutoCommit(Connection c) {
        try {
            c.setAutoCommit(true);
        } catch (SQLException ignored) {
        }
    }


    // ------------------------------------------------------------- derivations

    public int nextVersion(String slot) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COALESCE(MAX(version),0) FROM derivation WHERE slot=?")) {
            ps.setString(1, slot);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) + 1 : 1;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Atomically persist a complete derivation and, when setCurrent is true,
     * switch the slot pointer. Event + rows + pointer commit together, so an
     * interrupted writer leaves the old pointer intact.
     */
    public Derivation commitDerivation(DerivationSpec spec) {
        boolean tx = false;
        try {
            tx = conn.getAutoCommit();
            if (tx) {
                conn.setAutoCommit(false);
            }
            int version = nextVersion(spec.slot);
            String id = "der-" + slotShort(spec.slot) + "-v" + version + "-"
                    + Ids.sha256(Json.write(spec.params) + Json.write(spec.result))
                        .substring(0, 10);
            int eventSeq = appendEventInternal(spec.eventType, spec.eventPayload);
            String createdAt = Ids.now();
            String diagJson = Json.write(spec.diagnostics.stream()
                    .map(d -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("code", d.code());
                        m.put("message", d.message());
                        m.put("tempC", d.tempC());
                        m.put("basis", d.basis());
                        m.put("detail", d.detail());
                        return m;
                    }).toList());
            String depsJson = Json.write(spec.deps);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO derivation(id,sample_id,kind,version,slot,status,accepted,"
                            + "params,result,diagnostics,deps_json,created_event,created_at,complete) "
                            + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,1)")) {
                ps.setString(1, id);
                ps.setString(2, spec.sampleId);
                ps.setString(3, spec.kind);
                ps.setInt(4, version);
                ps.setString(5, spec.slot);
                ps.setString(6, spec.status);
                ps.setInt(7, spec.accepted ? 1 : 0);
                ps.setString(8, Json.write(spec.params));
                ps.setString(9, spec.result == null ? null : Json.write(spec.result));
                ps.setString(10, diagJson);
                ps.setString(11, depsJson);
                ps.setInt(12, eventSeq);
                ps.setString(13, createdAt);
                ps.executeUpdate();
            }
            if (spec.setCurrent) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO pointer(sample_id,slot,derivation_id) VALUES(?,?,?) "
                                + "ON CONFLICT(sample_id,slot) DO UPDATE SET derivation_id=excluded.derivation_id")) {
                    ps.setString(1, spec.sampleId);
                    ps.setString(2, spec.slot);
                    ps.setString(3, id);
                    ps.executeUpdate();
                }
            }
            if (tx) {
                conn.commit();
            }
            return new Derivation(id, spec.sampleId, spec.kind, version, spec.slot,
                    spec.status, spec.accepted, Json.write(spec.params),
                    spec.result == null ? null : Json.write(spec.result),
                    spec.diagnostics, spec.deps, eventSeq, createdAt, true);
        } catch (SQLException e) {
            if (tx) {
                rollbackQuietly(conn);
            }
            throw new RuntimeException(e);
        } finally {
            if (tx) {
                restoreAutoCommit(conn);
            }
        }
    }

    private static String slotShort(String slot) {
        StringBuilder sb = new StringBuilder();
        for (String part : slot.split("[:/_-]")) {
            if (part.isEmpty()) {
                continue;
            }
            sb.append(part.charAt(0));
            if (part.length() > 1) {
                sb.append(part.substring(Math.min(3, part.length() - 1)));
            }
        }
        return sb.length() > 24 ? sb.substring(0, 24) : sb.toString();
    }

    public Derivation current(String sampleId, String slot) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT d.* FROM derivation d JOIN pointer p ON p.derivation_id=d.id "
                        + "WHERE p.sample_id=? AND p.slot=?")) {
            ps.setString(1, sampleId);
            ps.setString(2, slot);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapDerivation(rs) : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Derivation byId(String id) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM derivation WHERE id=?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapDerivation(rs) : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Derivation> versionsOfSlot(String slot) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM derivation WHERE slot=? ORDER BY version")) {
            ps.setString(1, slot);
            try (ResultSet rs = ps.executeQuery()) {
                List<Derivation> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(mapDerivation(rs));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Derivation> allDerivations(String sampleId) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM derivation WHERE sample_id=? ORDER BY created_event")) {
            ps.setString(1, sampleId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Derivation> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(mapDerivation(rs));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<String> allSlots(String sampleId) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT DISTINCT slot FROM derivation WHERE sample_id=? ORDER BY slot")) {
            ps.setString(1, sampleId);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Derivation mapDerivation(ResultSet rs) throws SQLException {
        List<Map<String, Object>> rawDiags = (List<Map<String, Object>>)
                Json.M.getTypeFactory().constructCollectionType(List.class, Map.class);
        List<Diagnostic> diags = Json.M.readValue(rs.getString("diagnostics"),
                Json.M.getTypeFactory().constructCollectionType(List.class, Map.class))
                .stream().map(m -> new Diagnostic(
                        String.valueOf(m.get("code")),
                        String.valueOf(m.get("message")),
                        m.get("tempC") == null ? null : ((Number) m.get("tempC")).doubleValue(),
                        m.get("basis") == null ? null : String.valueOf(m.get("basis")),
                        (Map<String, Object>) m.getOrDefault("detail", Map.of())))
                .toList();
        List<Dependency> deps = Json.M.readValue(rs.getString("deps_json"),
                Json.M.getTypeFactory().constructCollectionType(List.class, Map.class))
                .stream().map(m -> new Dependency(String.valueOf(m.get("kind")),
                        String.valueOf(m.get("id")),
                        ((Number) m.get("version")).intValue())).toList();
        return new Derivation(rs.getString("id"), rs.getString("sample_id"),
                rs.getString("kind"), rs.getInt("version"), rs.getString("slot"),
                rs.getString("status"), rs.getInt("accepted") == 1,
                rs.getString("params"), rs.getString("result"), diags, deps,
                rs.getInt("created_event"), rs.getString("created_at"),
                rs.getInt("complete") == 1);
    }

    /** Inputs for an atomic derivation commit. */
    public static final class DerivationSpec {
        public String sampleId;
        public String kind;
        public String slot;
        public String status = "CURRENT";
        public boolean accepted = false;
        public Object params = Map.of();
        public Object result;
        public List<Diagnostic> diagnostics = List.of();
        public List<Dependency> deps = List.of();
        public boolean setCurrent = true;
        public String eventType = "DERIVE";
        public Map<String, Object> eventPayload = Map.of();
    }

    // --------------------------------------------------------------- conflicts

    /** Record an optimistic-lock conflict between two branches from one base. */
    public Conflict openConflict(String sampleId, String slot, String baseDerivationId,
                                 String branchAId, String branchAReviewer,
                                 String branchBId, String branchBReviewer,
                                 Map<String, Object> extra) {
        try {
            conn.setAutoCommit(false);
            String id = "conf-" + Ids.uuid().substring(0, 8);
            int eventSeq;
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("action", "CONFLICT_OPENED");
            payload.put("conflictId", id);
            payload.put("slot", slot);
            payload.put("baseDerivationId", baseDerivationId);
            payload.put("branchA", Map.of("derivationId", branchAId, "reviewer", branchAReviewer));
            payload.put("branchB", Map.of("derivationId", branchBId, "reviewer", branchBReviewer));
            payload.putAll(extra == null ? Map.of() : extra);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO event_log(type,payload,created_at) VALUES(?,?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, "CONFLICT");
                ps.setString(2, Json.write(payload));
                ps.setString(3, Ids.now());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    eventSeq = keys.getInt(1);
                }
            }
            String createdAt = Ids.now();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO conflict(id,sample_id,slot,base_derivation_id,"
                            + "branch_a_id,branch_a_reviewer,branch_b_id,branch_b_reviewer,"
                            + "status,created_event,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)")) {
                ps.setString(1, id);
                ps.setString(2, sampleId);
                ps.setString(3, slot);
                ps.setString(4, baseDerivationId);
                ps.setString(5, branchAId);
                ps.setString(6, branchAReviewer);
                ps.setString(7, branchBId);
                ps.setString(8, branchBReviewer);
                ps.setString(9, "OPEN");
                ps.setInt(10, eventSeq);
                ps.setString(11, createdAt);
                ps.executeUpdate();
            }
            conn.commit();
            return new Conflict(id, sampleId, slot, baseDerivationId,
                    branchAId, branchAReviewer, branchBId, branchBReviewer,
                    "OPEN", null, eventSeq, 0, createdAt);
        } catch (SQLException e) {
            rollbackQuietly(conn);
            throw new RuntimeException(e);
        } finally {
            restoreAutoCommit(conn);
        }
    }

    public List<Conflict> conflicts(String sampleId) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM conflict WHERE sample_id=? ORDER BY created_event")) {
            ps.setString(1, sampleId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Conflict> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(mapConflict(rs));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Conflict conflict(String id) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM conflict WHERE id=?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapConflict(rs) : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /** Resolve by keeping one branch; the other stays on file as a retained alternative. */
    public Conflict resolveConflict(String conflictId, String keep) {
        try {
            conn.setAutoCommit(false);
            Conflict c = conflict(conflictId);
            if (c == null || !"OPEN".equals(c.status())) {
                throw new IllegalStateException("conflict not open: " + conflictId);
            }
            String keptId = "A".equals(keep) ? c.branchAId() : c.branchBId();
            String droppedId = "A".equals(keep) ? c.branchBId() : c.branchAId();
            int eventSeq;
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("action", "CONFLICT_RESOLVED");
            payload.put("conflictId", conflictId);
            payload.put("kept", keep);
            payload.put("keptDerivationId", keptId);
            payload.put("retainedAlternativeId", droppedId);
            payload.put("note", "未采纳分支保留在档，不删除、不覆盖");
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO event_log(type,payload,created_at) VALUES(?,?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, "RESOLVE");
                ps.setString(2, Json.write(payload));
                ps.setString(3, Ids.now());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    eventSeq = keys.getInt(1);
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE conflict SET status='RESOLVED', kept_branch=?, resolved_event=? WHERE id=?")) {
                ps.setString(1, keep);
                ps.setInt(2, eventSeq);
                ps.setString(3, conflictId);
                ps.executeUpdate();
            }
            // retained alternative is explicitly marked RETAINED but never deleted
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE derivation SET status='RETAINED_ALTERNATIVE' WHERE id=?")) {
                ps.setString(1, droppedId);
                ps.executeUpdate();
            }
            conn.commit();
            return conflict(conflictId);
        } catch (SQLException e) {
            rollbackQuietly(conn);
            throw new RuntimeException(e);
        } finally {
            restoreAutoCommit(conn);
        }
    }

    private Conflict mapConflict(ResultSet rs) throws SQLException {
        return new Conflict(rs.getString("id"), rs.getString("sample_id"),
                rs.getString("slot"), rs.getString("base_derivation_id"),
                rs.getString("branch_a_id"), rs.getString("branch_a_reviewer"),
                rs.getString("branch_b_id"), rs.getString("branch_b_reviewer"),
                rs.getString("status"), rs.getString("kept_branch"),
                rs.getInt("created_event"), rs.getInt("resolved_event"),
                rs.getString("created_at"));
    }

    // ----------------------------------------------------- event-based replay

    /**
     * Fold the event journal up to {@code seq}: every DERIVE/DECIDE event that
     * carried setCurrent moved a slot pointer. Returning to an old event
     * therefore restores the exact derivations visible at that time.
     */
    public Map<String, String> pointersAsOf(int seq) {
        Map<String, String> pointers = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT seq,payload FROM event_log WHERE seq<=? ORDER BY seq")) {
            ps.setInt(1, seq);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> payload = Json.read(rs.getString("payload"));
                    Object slot = payload.get("slot");
                    Object derivId = payload.get("derivationId");
                    Boolean setCurrent = (Boolean) payload.get("setCurrent");
                    if (slot != null && derivId != null && Boolean.TRUE.equals(setCurrent)) {
                        pointers.put(String.valueOf(slot), String.valueOf(derivId));
                    }
                    if ("CONFLICT_RESOLVED".equals(payload.get("action"))) {
                        // pointer itself did not move on resolve; the kept derivation was already current
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return pointers;
    }

    public void markStale(String derivationId, String reason) {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE derivation SET status='STALE' WHERE id=?")) {
            ps.setString(1, derivationId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", "MARKED_STALE");
        payload.put("derivationId", derivationId);
        payload.put("reason", reason);
        appendEventInternal("STALE", payload);
    }

    public void markStatus(String derivationId, String status) {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE derivation SET status=? WHERE id=?")) {
            ps.setString(1, status);
            ps.setString(2, derivationId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // -------------------------------------------------------- export / import

    /** Whole-archive export: identities, ordering and provenance are all explicit. */
    public Map<String, Object> exportArchive() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("format", "dsc-archive-1");
        out.put("exportedAt", Ids.now());
        out.put("sources", queryMaps(
                "SELECT id,sample_id,kind,version,raw,media_type,hash,created_event,created_at "
                        + "FROM source_document ORDER BY kind,version"));
        out.put("points", queryMaps(
                "SELECT source_id,seg_index,seq,time_raw,time_unit,temp_raw,temp_unit,"
                        + "hf_raw,hf_unit FROM raw_point ORDER BY source_id,seg_index,seq"));
        out.put("derivations", queryMaps(
                "SELECT id,sample_id,kind,version,slot,status,accepted,params,result,"
                        + "diagnostics,deps_json,created_event,created_at,complete "
                        + "FROM derivation ORDER BY created_event,version"));
        out.put("pointers", queryMaps(
                "SELECT sample_id,slot,derivation_id FROM pointer ORDER BY sample_id,slot"));
        out.put("events", queryMaps(
                "SELECT seq,type,payload,created_at FROM event_log ORDER BY seq"));
        out.put("conflicts", queryMaps(
                "SELECT id,sample_id,slot,base_derivation_id,branch_a_id,branch_a_reviewer,"
                        + "branch_b_id,branch_b_reviewer,status,kept_branch,created_event,"
                        + "resolved_event,created_at FROM conflict ORDER BY created_event"));
        return out;
    }

    private List<Map<String, Object>> queryMaps(String sql) {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            var meta = rs.getMetaData();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    Object v = rs.getObject(i);
                    row.put(meta.getColumnName(i), v);
                }
                rows.add(row);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return rows;
    }

    /** Replace the whole archive; used after the local database was deleted. */
    public void importArchive(Map<String, Object> archive) {
        try {
            conn.setAutoCommit(false);
            for (String table : List.of("conflict", "pointer", "derivation",
                    "raw_point", "source_document", "event_log")) {
                try (Statement st = conn.createStatement()) {
                    st.execute("DELETE FROM " + table);
                    if ("event_log".equals(table)) {
                        st.execute("DELETE FROM sqlite_sequence WHERE name='event_log'");
                    }
                }
            }
            insertRows("INSERT INTO source_document(id,sample_id,kind,version,raw,media_type,"
                            + "hash,created_event,created_at) VALUES(?,?,?,?,?,?,?,?,?)",
                    (List<Map<String, Object>>) archive.get("sources"),
                    List.of("id", "sample_id", "kind", "version", "raw", "media_type",
                            "hash", "created_event", "created_at"));
            insertRows("INSERT INTO raw_point(source_id,seg_index,seq,time_raw,time_unit,"
                            + "temp_raw,temp_unit,hf_raw,hf_unit) VALUES(?,?,?,?,?,?,?,?,?)",
                    (List<Map<String, Object>>) archive.get("points"),
                    List.of("source_id", "seg_index", "seq", "time_raw", "time_unit",
                            "temp_raw", "temp_unit", "hf_raw", "hf_unit"));
            insertRows("INSERT INTO event_log(seq,type,payload,created_at) VALUES(?,?,?,?)",
                    (List<Map<String, Object>>) archive.get("events"),
                    List.of("seq", "type", "payload", "created_at"));
            insertRows("INSERT INTO derivation(id,sample_id,kind,version,slot,status,accepted,"
                            + "params,result,diagnostics,deps_json,created_event,created_at,complete) "
                            + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    (List<Map<String, Object>>) archive.get("derivations"),
                    List.of("id", "sample_id", "kind", "version", "slot", "status", "accepted",
                            "params", "result", "diagnostics", "deps_json", "created_event",
                            "created_at", "complete"));
            insertRows("INSERT INTO pointer(sample_id,slot,derivation_id) VALUES(?,?,?)",
                    (List<Map<String, Object>>) archive.get("pointers"),
                    List.of("sample_id", "slot", "derivation_id"));
            insertRows("INSERT INTO conflict(id,sample_id,slot,base_derivation_id,branch_a_id,"
                            + "branch_a_reviewer,branch_b_id,branch_b_reviewer,status,kept_branch,"
                            + "created_event,resolved_event,created_at) "
                            + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    (List<Map<String, Object>>) archive.get("conflicts"),
                    List.of("id", "sample_id", "slot", "base_derivation_id", "branch_a_id",
                            "branch_a_reviewer", "branch_b_id", "branch_b_reviewer", "status",
                            "kept_branch", "created_event", "resolved_event", "created_at"));
            conn.commit();
        } catch (SQLException e) {
            rollbackQuietly(conn);
            throw new RuntimeException(e);
        } finally {
            restoreAutoCommit(conn);
        }
    }

    private void insertRows(String sql, List<Map<String, Object>> rows, List<String> cols)
            throws SQLException {
        if (rows == null) {
            return;
        }
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Map<String, Object> row : rows) {
                for (int i = 0; i < cols.size(); i++) {
                    Object value = row.get(cols.get(i));
                    if (value == null) {
                        ps.setNull(i + 1, java.sql.Types.OTHER);
                    } else if (value instanceof Number n) {
                        ps.setObject(i + 1, n);
                    } else if (value instanceof Boolean b) {
                        ps.setInt(i + 1, b ? 1 : 0);
                    } else {
                        ps.setString(i + 1, String.valueOf(value));
                    }
                }
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}
