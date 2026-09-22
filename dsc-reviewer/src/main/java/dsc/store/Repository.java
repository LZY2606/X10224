package dsc.store;

import dsc.domain.DerivedVersion;
import dsc.domain.Diagnostic;
import dsc.domain.EventRecord;
import dsc.domain.SourceRecord;
import dsc.domain.Types;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SQLite-backed version store.
 *
 * <p>All mutations run in {@link #writeTx}: either the previous fully committed
 * state or the entire new state is visible after restart. Optimistic
 * concurrency uses the head revision (hash of the last event) as the
 * expected-version token; two editors starting from the same revision cause a
 * {@link ConflictException} that callers resolve explicitly.
 */
public final class Repository implements AutoCloseable {

    public static final class ConflictException extends RuntimeException {
        public ConflictException(String message) {
            super(message);
        }
    }

    public static final class Commit {
        public final long seq;
        public final String eventId;
        public final String revision;

        Commit(long seq, String eventId, String revision) {
            this.seq = seq;
            this.eventId = eventId;
            this.revision = revision;
        }
    }

    /** Incoming mutation prepared by the engine. */
    public static final class ChangeSet {
        public Types.EventKind kind;
        public String scopeId = "";
        public String actor = "reviewer";
        public String branch = "main";
        public String parentEventId;
        public String baseRevision;
        public Map<String, Object> payload = new LinkedHashMap<>();
        public final List<SourceRecord> insertSources = new ArrayList<>();
        public final List<DerivedVersion> insertDerived = new ArrayList<>();
        /** key: source id + "#" + oldVersion */
        public final List<String[]> supersedeSources = new ArrayList<>();
        /** key: derived id (latest version is superseded) */
        public final List<String> supersedeDerived = new ArrayList<>();
        /** source id -> current version row to point at */
        public final List<SourcePointer> pointSources = new ArrayList<>();
        public final List<String> pointDerived = new ArrayList<>();
        public final List<DecisionPointer> decisions = new ArrayList<>();
    }

    public record SourcePointer(String id, String scopeId, Types.SourceKind kind, int version) {
    }

    public record DecisionPointer(String windowId, String adoptedBaselineId, String eventId, long seq) {
    }

    private final Connection conn;
    private final Clock clock;
    private boolean failNextCommit;

    public interface Clock {
        long now();
    }

    public Repository(String jdbcUrl) {
        this(jdbcUrl, System::currentTimeMillis);
    }

    public Repository(String jdbcUrl, Clock clock) {
        this.clock = clock;
        try {
            this.conn = DriverManager.getConnection(jdbcUrl);
            this.conn.setAutoCommit(false);
            Schema.migrate(this.conn);
            this.conn.commit();
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot open database: " + jdbcUrl, e);
        }
    }

    public String headRevision() {
        try (var ps = conn.prepareStatement(
                "SELECT content_hash FROM event_stream ORDER BY seq DESC LIMIT 1");
                var rs = ps.executeQuery()) {
            return rs.next() ? rs.getString(1) : "ROOT";
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public long nextSeq() {
        try (var ps = conn.prepareStatement("SELECT COALESCE(MAX(seq), 0) + 1 FROM event_stream");
                var rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Test hook: the next commit throws after writing half of the rows. */
    public void armWriteInterrupt() {
        this.failNextCommit = true;
    }

    @Override
    public void close() {
        try {
            conn.close();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------
    // Commit
    // ------------------------------------------------------------------

    public Commit writeTx(ChangeSet cs) {
        try {
            long seq = nextSeq();
            String prevHash = headRevision();
            if (cs.baseRevision != null && !cs.baseRevision.equals(prevHash)) {
                throw new ConflictException(
                        "Revision conflict: expected " + cs.baseRevision + " but head is " + prevHash);
            }
            String parentEvent = cs.parentEventId != null ? cs.parentEventId : lastEventId();
            List<String> produced = new ArrayList<>();

            for (SourcePointer sp : cs.pointSources) {
                upsertSourceCurrent(sp);
            }
            int inserted = 0;
            int total = cs.insertSources.size() + cs.supersedeSources.size()
                    + cs.insertDerived.size() + cs.supersedeDerived.size();
            for (var key : cs.supersedeSources) {
                supersedeSource(key[0], Integer.parseInt(key[1]), key[2]);
                produced.add(key[0]);
                maybeInterrupt(++inserted, total);
            }
            for (SourceRecord s : cs.insertSources) {
                insertSource(s, seq);
                produced.add(s.id());
                maybeInterrupt(++inserted, total);
            }
            for (String id : cs.supersedeDerived) {
                supersedeDerivedLatest(id);
                maybeInterrupt(++inserted, total);
            }
            for (DerivedVersion d : cs.insertDerived) {
                insertDerived(d, seq);
                produced.add(d.id());
                maybeInterrupt(++inserted, total);
            }
            for (String id : cs.pointDerived) {
                upsertDerivedCurrent(id);
            }
            for (DecisionPointer dp : cs.decisions) {
                upsertDecision(dp);
            }

            String content = seq + "|" + cs.kind + "|" + cs.scopeId + "|"
                    + Json.write(cs.payload) + "|" + String.join(",", produced) + "|" + prevHash;
            String hash = dsc.domain.Ids.sha256(content);
            String eventId = dsc.domain.Ids.event(seq, hash);

            insertEvent(seq, eventId, cs, parentEvent, produced, hash, prevHash);
            if (failNextCommit) {
                failNextCommit = false;
                throw new SQLException("Simulated write interruption before commit");
            }
            conn.commit();
            return new Commit(seq, eventId, hash);
        } catch (SQLException e) {
            rollback();
            throw new IllegalStateException("Commit failed and was rolled back: " + e.getMessage(), e);
        } catch (ConflictException e) {
            rollback();
            throw e;
        }
    }

    private void maybeInterrupt(int done, int total) throws SQLException {
        if (failNextCommit && done >= Math.max(1, total / 2)) {
            throw new SQLException("Simulated write interruption mid-batch");
        }
    }

    private void rollback() {
        try {
            conn.rollback();
        } catch (SQLException ignored) {
            // best effort
        }
    }

    private String lastEventId() {
        try (var ps = conn.prepareStatement("SELECT id FROM event_stream ORDER BY seq DESC LIMIT 1");
                var rs = ps.executeQuery()) {
            return rs.next() ? rs.getString(1) : null;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------
    // Row mappers / statements
    // ------------------------------------------------------------------

    private void insertSource(SourceRecord s, long seq) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO source_version(id, version, scope_id, kind, replaced_by,
                    effective_event_id, superseded_event_id, raw_text, unit,
                    parsed_json, diagnostics_json, content_hash, parent_hash, seq)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            int i = 0;
            ps.setString(++i, s.id());
            ps.setInt(++i, s.version());
            ps.setString(++i, s.scopeId());
            ps.setString(++i, s.kind().name());
            ps.setString(++i, s.replacedBy());
            ps.setString(++i, s.effectiveEventId());
            ps.setString(++i, s.supersededEventId());
            ps.setString(++i, s.rawText());
            ps.setString(++i, s.unit());
            ps.setString(++i, Json.write(s.parsed()));
            ps.setString(++i, Json.write(s.diagnostics().stream().map(Diagnostic::toMap).toList()));
            ps.setString(++i, s.contentHash());
            ps.setString(++i, s.parentHash());
            ps.setLong(++i, seq);
            ps.executeUpdate();
        }
    }

    private void supersedeSource(String id, int oldVersion, String byEventId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE source_version SET replaced_by = ?, superseded_event_id = ? WHERE id = ? AND version = ?")) {
            ps.setString(1, byEventId);
            ps.setString(2, byEventId);
            ps.setString(3, id);
            ps.setInt(4, oldVersion);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Cannot supersede missing source " + id + "#v" + oldVersion);
            }
        }
    }

    private void upsertSourceCurrent(SourcePointer sp) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO source_current(id, scope_id, kind, version) VALUES (?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET version = excluded.version
                """)) {
            ps.setString(1, sp.id());
            ps.setString(2, sp.scopeId());
            ps.setString(3, sp.kind().name());
            ps.setInt(4, sp.version());
            ps.executeUpdate();
        }
    }

    private void insertDerived(DerivedVersion d, long seq) throws SQLException {
        List<Map<String, Object>> deps = d.deps().stream()
                .map(x -> Map.of("source", x.source(), "kind", x.sourceKind().name(),
                        "version", x.sourceVersion()))
                .map(m -> (Map<String, Object>) new LinkedHashMap<>(m))
                .toList();
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO derived_version(id, version, kind, scope_id, replaced_by,
                    deps_json, payload_json, diagnostics_json, produced_by_event,
                    content_hash, seq)
                VALUES (?,?,?,?,0,?,?,?,?,?,?)
                """)) {
            int i = 0;
            ps.setString(++i, d.id());
            ps.setInt(++i, d.version());
            ps.setString(++i, d.kind().name());
            ps.setString(++i, d.scopeId());
            ps.setString(++i, Json.write(deps));
            ps.setString(++i, Json.write(d.payload()));
            ps.setString(++i, Json.write(d.diagnostics().stream().map(Diagnostic::toMap).toList()));
            ps.setString(++i, d.producedByEvent());
            ps.setString(++i, d.contentHash());
            ps.setLong(++i, seq);
            ps.executeUpdate();
        }
    }

    private void supersedeDerivedLatest(String id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE derived_version SET replaced_by = 1 WHERE id = ? AND version = "
                        + "(SELECT MAX(version) FROM derived_version WHERE id = ?)")) {
            ps.setString(1, id);
            ps.setString(2, id);
            int n = ps.executeUpdate();
            if (n != 1) {
                throw new SQLException("Cannot supersede missing derived artifact " + id);
            }
        }
    }

    private void upsertDerivedCurrent(String id) throws SQLException {
        try (PreparedStatement sel = conn.prepareStatement(
                "SELECT kind, scope_id, MAX(version) FROM derived_version WHERE id = ? GROUP BY id");
                PreparedStatement up = conn.prepareStatement("""
                        INSERT INTO derived_current(id, kind, scope_id, version) VALUES (?,?,?,?)
                        ON CONFLICT(id) DO UPDATE SET version = excluded.version
                        """)) {
            sel.setString(1, id);
            try (ResultSet rs = sel.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Cannot point at missing derived artifact " + id);
                }
                up.setString(1, id);
                up.setString(2, rs.getString(1));
                up.setString(3, rs.getString(2));
                up.setInt(4, rs.getInt(3));
                up.executeUpdate();
            }
        }
    }

    private void upsertDecision(DecisionPointer dp) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO decision_current(window_id, adopted_baseline_id, decided_event_id, decided_seq)
                VALUES (?,?,?,?)
                ON CONFLICT(window_id) DO UPDATE SET
                    adopted_baseline_id = excluded.adopted_baseline_id,
                    decided_event_id = excluded.decided_event_id,
                    decided_seq = excluded.decided_seq
                """)) {
            ps.setString(1, dp.windowId());
            ps.setString(2, dp.adoptedBaselineId());
            ps.setString(3, dp.eventId());
            ps.setLong(4, dp.seq());
            ps.executeUpdate();
        }
    }

    private void insertEvent(long seq, String eventId, ChangeSet cs, String parentEvent,
            List<String> produced, String hash, String prevHash) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO event_stream(seq, id, kind, scope_id, actor, branch, parent_event_id,
                    wall_time, payload_json, produced_ids_json, base_revision, content_hash, prev_hash)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            int i = 0;
            ps.setLong(++i, seq);
            ps.setString(++i, eventId);
            ps.setString(++i, cs.kind.name());
            ps.setString(++i, cs.scopeId);
            ps.setString(++i, cs.actor);
            ps.setString(++i, cs.branch);
            ps.setString(++i, parentEvent);
            ps.setLong(++i, clock.now());
            ps.setString(++i, Json.write(cs.payload));
            ps.setString(++i, Json.write(produced));
            ps.setString(++i, cs.baseRevision);
            ps.setString(++i, hash);
            ps.setString(++i, prevHash);
            ps.executeUpdate();
        }
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    public List<Map<String, Object>> listSamples() {
        return query("SELECT id, slug, name, created_event_id, created_seq, ordinal FROM sample ORDER BY ordinal");
    }

    public List<Map<String, Object>> listSegments() {
        return query("SELECT * FROM segment ORDER BY sample_id, ordinal");
    }

    public Map<String, Object> segment(String id) {
        return queryOne("SELECT * FROM segment WHERE id = ?", id);
    }

    private List<Map<String, Object>> query(String sql, Object... args) {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                var meta = rs.getMetaData();
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int c = 1; c <= meta.getColumnCount(); c++) {
                        row.put(meta.getColumnName(c), rs.getObject(c));
                    }
                    rows.add(row);
                }
                return rows;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Object> queryOne(String sql, Object... args) {
        List<Map<String, Object>> rows = query(sql, args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void insertSample(String id, String slug, String name, String eventId, long seq, int ordinal) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO sample(id, slug, name, created_event_id, created_seq, ordinal) VALUES (?,?,?,?,?,?)")) {
            ps.setString(1, id);
            ps.setString(2, slug);
            ps.setString(3, name);
            ps.setString(4, eventId);
            ps.setLong(5, seq);
            ps.setInt(6, ordinal);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public void insertSegment(String id, String sampleId, int ordinal, String label,
            Types.Direction direction, Types.SignConvention sign, String eventId, long seq) {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO segment(id, sample_id, ordinal, label, declared_direction,
                    sign_convention, created_event_id, created_seq)
                VALUES (?,?,?,?,?,?,?,?)
                """)) {
            ps.setString(1, id);
            ps.setString(2, sampleId);
            ps.setInt(3, ordinal);
            ps.setString(4, label);
            ps.setString(5, direction.name());
            ps.setString(6, sign.name());
            ps.setString(7, eventId);
            ps.setLong(8, seq);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public SourceRecord currentSource(String id) {
        Map<String, Object> row = queryOne("""
                SELECT v.* FROM source_version v
                JOIN source_current c ON c.id = v.id AND c.version = v.version
                WHERE v.id = ?
                """, id);
        return row == null ? null : mapSource(row);
    }

    public List<SourceRecord> currentSourcesForScope(String scopeId) {
        List<SourceRecord> out = new ArrayList<>();
        for (Map<String, Object> row : query("""
                SELECT v.* FROM source_version v
                JOIN source_current c ON c.id = v.id AND c.version = v.version
                WHERE v.scope_id = ? ORDER BY v.kind, v.version
                """, scopeId)) {
            out.add(mapSource(row));
        }
        return out;
    }

    public SourceRecord sourceAt(String id, int version) {
        Map<String, Object> row = queryOne("SELECT * FROM source_version WHERE id = ? AND version = ?", id, version);
        return row == null ? null : mapSource(row);
    }

    public List<SourceRecord> sourceHistory(String id) {
        List<SourceRecord> out = new ArrayList<>();
        for (Map<String, Object> row : query("SELECT * FROM source_version WHERE id = ? ORDER BY version", id)) {
            out.add(mapSource(row));
        }
        return out;
    }

    public int nextSourceVersion(String id) {
        Map<String, Object> row = queryOne("SELECT COALESCE(MAX(version),0) AS m FROM source_version WHERE id = ?", id);
        return ((Number) row.get("m")).intValue() + 1;
    }

    private SourceRecord mapSource(Map<String, Object> row) {
        return new SourceRecord(
                (String) row.get("id"),
                (String) row.get("scope_id"),
                Types.SourceKind.valueOf((String) row.get("kind")),
                ((Number) row.get("version")).intValue(),
                (String) row.get("replaced_by"),
                (String) row.get("effective_event_id"),
                (String) row.get("superseded_event_id"),
                (String) row.get("raw_text"),
                (String) row.get("unit"),
                Json.readMap((String) row.get("parsed_json")),
                mapDiagnostics((String) row.get("diagnostics_json")),
                (String) row.get("content_hash"),
                (String) row.get("parent_hash"));
    }

    private static List<Diagnostic> mapDiagnostics(String json) {
        List<Diagnostic> out = new ArrayList<>();
        for (Map<String, Object> m : Json.readList(json)) {
            out.add(Diagnostic.of((String) m.get("code"),
                    Types.Severity.valueOf((String) m.get("severity")),
                    (String) m.get("message"),
                    (String) m.get("rawValue"),
                    (String) m.get("ref")));
        }
        return out;
    }

    public DerivedVersion currentDerived(String id) {
        Map<String, Object> row = queryOne("""
                SELECT v.* FROM derived_version v
                JOIN derived_current c ON c.id = v.id AND c.version = v.version
                WHERE v.id = ?
                """, id);
        return row == null ? null : mapDerived(row);
    }

    public DerivedVersion derivedAt(String id, int version) {
        Map<String, Object> row = queryOne("SELECT * FROM derived_version WHERE id = ? AND version = ?", id, version);
        return row == null ? null : mapDerived(row);
    }

    public List<DerivedVersion> derivedHistory(String id) {
        List<DerivedVersion> out = new ArrayList<>();
        for (Map<String, Object> row : query("SELECT * FROM derived_version WHERE id = ? ORDER BY version", id)) {
            out.add(mapDerived(row));
        }
        return out;
    }

    public int nextDerivedVersion(String id) {
        Map<String, Object> row = queryOne("SELECT COALESCE(MAX(version),0) AS m FROM derived_version WHERE id = ?", id);
        return ((Number) row.get("m")).intValue() + 1;
    }

    public List<DerivedVersion> currentDerivedForScope(String scopeId) {
        List<DerivedVersion> out = new ArrayList<>();
        for (Map<String, Object> row : query("""
                SELECT v.* FROM derived_version v
                JOIN derived_current c ON c.id = v.id AND c.version = v.version
                WHERE v.scope_id = ? ORDER BY v.kind, v.id
                """, scopeId)) {
            out.add(mapDerived(row));
        }
        return out;
    }

    private DerivedVersion mapDerived(Map<String, Object> row) {
        List<DerivedVersion.Dep> deps = new ArrayList<>();
        for (Map<String, Object> m : Json.readList((String) row.get("deps_json"))) {
            deps.add(new DerivedVersion.Dep(
                    (String) m.get("source"),
                    Types.SourceKind.valueOf((String) m.get("kind")),
                    ((Number) m.get("version")).intValue()));
        }
        return new DerivedVersion(
                (String) row.get("id"),
                Types.DerivedKind.valueOf((String) row.get("kind")),
                (String) row.get("scope_id"),
                ((Number) row.get("version")).intValue(),
                ((Number) row.get("replaced_by")).intValue() != 0,
                deps,
                Json.readMap((String) row.get("payload_json")),
                mapDiagnostics((String) row.get("diagnostics_json")),
                (String) row.get("produced_by_event"),
                (String) row.get("content_hash"));
    }

    public List<EventRecord> events(String scopeId) {
        List<Map<String, Object>> rows = scopeId == null
                ? query("SELECT * FROM event_stream ORDER BY seq")
                : query("SELECT * FROM event_stream WHERE scope_id = ? OR scope_id = '' ORDER BY seq", scopeId);
        List<EventRecord> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            out.add(mapEvent(row));
        }
        return out;
    }

    public EventRecord eventById(String id) {
        Map<String, Object> row = queryOne("SELECT * FROM event_stream WHERE id = ?", id);
        return row == null ? null : mapEvent(row);
    }

    public long eventSeq(String eventId) {
        if (eventId == null) {
            return 0;
        }
        Map<String, Object> row = queryOne("SELECT seq FROM event_stream WHERE id = ?", eventId);
        if (row == null) {
            return 0;
        }
        return ((Number) row.get("seq")).longValue();
    }

    private EventRecord mapEvent(Map<String, Object> row) {
        return new EventRecord(
                ((Number) row.get("seq")).longValue(),
                (String) row.get("id"),
                Types.EventKind.valueOf((String) row.get("kind")),
                (String) row.get("scope_id"),
                (String) row.get("actor"),
                (String) row.get("branch"),
                (String) row.get("parent_event_id"),
                ((Number) row.get("wall_time")).longValue(),
                Json.readMap((String) row.get("payload_json")),
                Json.readList((String) row.get("produced_ids_json")).stream()
                        .map(x -> (String) x).toList(),
                (String) row.get("base_revision"),
                (String) row.get("content_hash"),
                (String) row.get("prev_hash"));
    }

    public List<Map<String, Object>> decisions() {
        return query("SELECT * FROM decision_current ORDER BY decided_seq");
    }
