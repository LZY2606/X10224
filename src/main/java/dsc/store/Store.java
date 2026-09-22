package dsc.store;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dsc.model.Doc;
import dsc.model.Event;
import dsc.model.J;

import java.sql.*;
import java.util.*;

/**
 * SQLite-backed append-only journal + versioned document store.
 *
 * <p>Commit semantics: a command's event, new doc revisions, dependency edges and pointer
 * moves are written in ONE transaction. A crash leaves either the old committed state or
 * the complete new state — never a half-written derivation. Pointer moves are explicit
 * rows keyed by (lineage, seq) so rewinding the journal recovers historical conclusions.
 */
public final class Store implements AutoCloseable {

    private final Connection cx;

    public Store(String path) {
        try {
            Class.forName("org.sqlite.JDBC");
            cx = DriverManager.getConnection("jdbc:sqlite:" + path);
            cx.setAutoCommit(false);
            try (Statement st = cx.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA foreign_keys=ON");
                st.execute("PRAGMA busy_timeout=5000");
            }
            cx.commit();
            Migrator.migrate(cx);
        } catch (Exception e) {
            throw new StoreException("cannot open store: " + path, e);
        }
    }

    /** Test-only: expose connection for crash/interrupt simulation. */
    public Connection connection() { return cx; }

    public synchronized long headSeq() {
        try (Statement st = cx.createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(seq), 0) FROM events")) {
            rs.next();
            long v = rs.getLong(1);
            cx.commit();
            return v;
        } catch (SQLException e) {
            throw new StoreException("headSeq failed", e);
        }
    }

    public synchronized String headVersion() {
        return Long.toString(headSeq());
    }

    /** A complete, all-or-nothing write produced by one command. */
    public record Commit(Event event,
                         List<Doc> docs,
                         Map<String, Pointer> pointerMoves,
                         List<DepEdge> deps) {
        public Commit {
            docs = docs == null ? List.of() : List.copyOf(docs);
            pointerMoves = pointerMoves == null ? Map.of() : Map.copyOf(pointerMoves);
            deps = deps == null ? List.of() : List.copyOf(deps);
        }
    }

    public record Pointer(String lineage, String docId, String version) {}

    public record DepEdge(String derivedId, String derivedVersion,
                          String sourceId, String sourceVersion, String role) {}

    /**
     * Append one commit atomically.
     *
     * @param expectedHead caller's last seen head seq as string ("" for creation). Two
     *                     users editing the same version: the second writer fails with
     *                     {@link ConflictException} and must resolve explicitly.
     */
    public synchronized Event commit(Commit c, String expectedHead, boolean allowExpectedMismatch) {
        long head = headSeqInternal();
        if (!allowExpectedMismatch && expectedHead != null && !expectedHead.isEmpty()
                && Long.parseLong(expectedHead) != head) {
            throw new ConflictException(
                    "document changed since you last read it (head=" + head + ", expected=" + expectedHead + ")");
        }
        try {
            long seq = head + 1;
            Event ev = new Event(seq, c.event().id, c.event().caseId, c.event().type,
                    c.event().actor, expectedHead == null ? "" : expectedHead,
                    c.event().payload, c.event().ts);
            insertEvent(ev);
            for (Doc d : c.docs()) insertDoc(d);
            for (DepEdge e : c.deps()) insertDep(e);
            for (Pointer p : c.pointerMoves().values()) {
                insertPointer(p.lineage(), seq, p.docId(), p.version());
            }
            cx.commit();
            return ev;
        } catch (SQLException e) {
            rollback();
            throw new StoreException("commit failed", e);
        }
    }

    /**
     * Write the event rows but roll the whole transaction back — used by the
     * write-interruption test to prove a restart never observes partial output.
     */
    public synchronized void simulateCrashDuringCommit(Commit c) {
        long seq = headSeqInternal() + 1;
        Event ev = new Event(seq, c.event().id, c.event().caseId, c.event().type,
                c.event().actor, "", c.event().payload, c.event().ts);
        try {
            insertEvent(ev);
            for (Doc d : c.docs()) insertDoc(d);
            // Crash here: no pointer rows, no commit.
            cx.rollback();
        } catch (SQLException e) {
            rollback();
            throw new StoreException("crash simulation failed", e);
        }
    }

    private long headSeqInternal() {
        try (Statement st = cx.createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(seq), 0) FROM events")) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new StoreException("head failed", e);
        }
    }

    private void insertEvent(Event ev) throws SQLException {
        try (PreparedStatement ps = cx.prepareStatement(
                "INSERT INTO events(seq,id,case_id,type,actor,expected_version,ts,payload) VALUES(?,?,?,?,?,?,?,?)")) {
            ps.setLong(1, ev.seq);
            ps.setString(2, ev.id);
            ps.setString(3, ev.caseId);
            ps.setString(4, ev.type);
            ps.setString(5, ev.actor);
            ps.setString(6, ev.expectedVersion);
            ps.setString(7, ev.ts);
            ps.setString(8, J.string(ev.payload));
            ps.executeUpdate();
        }
    }

    private void insertDoc(Doc d) throws SQLException {
        try (PreparedStatement ps = cx.prepareStatement(
                "INSERT INTO docs(id,version,lineage,kind,seq,base_version,data) VALUES(?,?,?,?,?,?,?)")) {
            ps.setString(1, d.id);
            ps.setString(2, d.version);
            ps.setString(3, d.lineage);
            ps.setString(4, d.kind);
            ps.setLong(5, d.seq);
            ps.setString(6, d.baseVersion == null ? "" : d.baseVersion);
            ps.setString(7, J.string(d.data));
            ps.executeUpdate();
        }
    }

    private void insertDep(DepEdge e) throws SQLException {
        try (PreparedStatement ps = cx.prepareStatement(
                "INSERT OR IGNORE INTO dep(derived_id,derived_version,source_id,source_version,role) VALUES(?,?,?,?,?)")) {
            ps.setString(1, e.derivedId());
            ps.setString(2, e.derivedVersion());
            ps.setString(3, e.sourceId());
            ps.setString(4, e.sourceVersion());
            ps.setString(5, e.role());
            ps.executeUpdate();
        }
    }

    private void insertPointer(String lineage, long seq, String docId, String version) throws SQLException {
        try (PreparedStatement ps = cx.prepareStatement(
                "INSERT INTO pointers(lineage,seq,doc_id,version) VALUES(?,?,?,?)")) {
            ps.setString(1, lineage);
            ps.setLong(2, seq);
            ps.setString(3, docId);
            ps.setString(4, version);
            ps.executeUpdate();
        }
    }

    private void rollback() {
        try { cx.rollback(); } catch (SQLException ignore) { }
    }

    public synchronized List<Event> events(String caseId) {
        String sql = "SELECT * FROM events" + (caseId == null ? "" : " WHERE case_id=?") + " ORDER BY seq";
        try (PreparedStatement ps = cx.prepareStatement(sql)) {
            if (caseId != null) ps.setString(1, caseId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Event> out = new ArrayList<>();
                while (rs.next()) out.add(readEvent(rs));
                cx.commit();
                return out;
            }
        } catch (SQLException e) {
            throw new StoreException("events failed", e);
        }
    }

    public synchronized Event eventById(String eventId) {
        try (PreparedStatement ps = cx.prepareStatement("SELECT * FROM events WHERE id=?")) {
            ps.setString(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                Event ev = rs.next() ? readEvent(rs) : null;
                cx.commit();
                return ev;
            }
        } catch (SQLException e) {
            throw new StoreException("eventById failed", e);
        }
    }

    private Event readEvent(ResultSet rs) throws SQLException {
        return new Event(
                rs.getLong("seq"),
                rs.getString("id"),
                rs.getString("case_id"),
                rs.getString("type"),
                rs.getString("actor"),
                rs.getString("expected_version"),
                J.parse(rs.getString("payload")),
                rs.getString("ts"));
    }

    /**
     * Materialized view of the case as of {@code seq}: the doc revision each lineage
     * pointer referenced at that point in the journal. Rewinding to an old event restores
     * exactly the visible conclusions of that moment, never latest recomputations.
     */
    public synchronized Snapshot snapshot(String caseId, long seq) {
        Map<String, Doc> current = new LinkedHashMap<>();
        try (PreparedStatement ps = cx.prepareStatement(
                "SELECT p.lineage, p.seq AS pseq, d.id, d.version, d.kind, d.seq AS dseq, d.base_version, d.data "
                        + "FROM pointers p JOIN docs d ON d.id=p.doc_id AND d.version=p.version "
                        + "JOIN events e ON e.seq=p.seq "
                        + "WHERE e.case_id=? AND p.seq<=? "
                        + "ORDER BY p.seq")) {
            ps.setString(1, caseId);
            ps.setLong(2, seq);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Doc d = new Doc(rs.getString("id"), rs.getString("lineage"), rs.getString("kind"),
                            rs.getLong("dseq"), rs.getString("base_version"),
                            rs.getString("version"), J.parse(rs.getString("data")));
                    current.put(d.lineage, d);
                }
            }
            cx.commit();
        } catch (SQLException e) {
            throw new StoreException("snapshot failed", e);
        }
        return new Snapshot(seq, current);
    }

    public synchronized Doc docVersion(String id, String version) {
        try (PreparedStatement ps = cx.prepareStatement("SELECT * FROM docs WHERE id=? AND version=?")) {
            ps.setString(1, id);
            ps.setString(2, version);
            try (ResultSet rs = ps.executeQuery()) {
                Doc d = rs.next() ? new Doc(rs.getString("id"), rs.getString("lineage"), rs.getString("kind"),
                        rs.getLong("seq"), rs.getString("base_version"),
                        rs.getString("version"), J.parse(rs.getString("data"))) : null;
                cx.commit();
                return d;
            }
        } catch (SQLException e) {
            throw new StoreException("docVersion failed", e);
        }
    }

    public synchronized List<Doc> lineageRevisions(String lineage) {
        try (PreparedStatement ps = cx.prepareStatement(
                "SELECT * FROM docs WHERE lineage=? ORDER BY seq")) {
            ps.setString(1, lineage);
            try (ResultSet rs = ps.executeQuery()) {
                List<Doc> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new Doc(rs.getString("id"), rs.getString("lineage"), rs.getString("kind"),
                            rs.getLong("seq"), rs.getString("base_version"),
                            rs.getString("version"), J.parse(rs.getString("data"))));
                }
                cx.commit();
                return out;
            }
        } catch (SQLException e) {
            throw new StoreException("lineage failed", e);
        }
    }

    public synchronized List<DepEdge> depsOf(String derivedId, String derivedVersion) {
        try (PreparedStatement ps = cx.prepareStatement(
                "SELECT * FROM dep WHERE derived_id=? AND derived_version=? ORDER BY role")) {
            ps.setString(1, derivedId);
            ps.setString(2, derivedVersion);
            try (ResultSet rs = ps.executeQuery()) {
                List<DepEdge> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new DepEdge(rs.getString("derived_id"), rs.getString("derived_version"),
                            rs.getString("source_id"), rs.getString("source_version"),
                            rs.getString("role")));
                }
                cx.commit();
                return out;
            }
        } catch (SQLException e) {
            throw new StoreException("deps failed", e);
        }
    }

    public synchronized List<String> dependentsOf(String sourceId, String sourceVersion) {
        try (PreparedStatement ps = cx.prepareStatement(
                "SELECT DISTINCT derived_id FROM dep WHERE source_id=? AND source_version=?")) {
            ps.setString(1, sourceId);
            ps.setString(2, sourceVersion);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> out = new ArrayList<>();
                while (rs.next()) out.add(rs.getString(1));
                cx.commit();
                return out;
            }
        } catch (SQLException e) {
            throw new StoreException("dependents failed", e);
        }
    }

    /**
     * Export a case as a self-contained JSON archive: every event, every doc revision
     * (accepted AND unaccepted), pointers and dependency edges. Stable ids and versions
     * are embedded, so re-import preserves identity, order and provenance.
     */
    public synchronized JsonObject exportCase(String caseId) {
        JsonObject out = J.obj();
        J.put(out, "format", "thermal-turning-point-case");
        J.put(out, "formatVersion", 1);
        J.put(out, "caseId", caseId);
        JsonArray evs = J.arr();
        for (Event e : events(caseId)) evs.add(e.toJson());
        J.put(out, "events", evs);

        JsonArray docArr = J.arr();
        try (PreparedStatement ps = cx.prepareStatement(
                "SELECT d.* FROM docs d JOIN events e ON e.seq=d.seq WHERE e.case_id=? "
                        + "ORDER BY d.seq, d.id")) {
            ps.setString(1, caseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    docArr.add(new Doc(rs.getString("id"), rs.getString("lineage"), rs.getString("kind"),
                            rs.getLong("seq"), rs.getString("base_version"),
                            rs.getString("version"), J.parse(rs.getString("data"))).toJson());
                }
            }
        } catch (SQLException e) {
            throw new StoreException("export docs failed", e);
        }
        J.put(out, "docs", docArr);

        JsonArray ptrArr = J.arr();
        try (PreparedStatement ps = cx.prepareStatement(
                "SELECT p.* FROM pointers p JOIN events e ON e.seq=p.seq WHERE e.case_id=? ORDER BY p.seq, p.lineage")) {
            ps.setString(1, caseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JsonObject p = J.obj();
                    J.put(p, "lineage", rs.getString("lineage"));
                    J.put(p, "seq", rs.getLong("seq"));
                    J.put(p, "docId", rs.getString("doc_id"));
                    J.put(p, "version", rs.getString("version"));
                    ptrArr.add(p);
                }
            }
        } catch (SQLException e) {
            throw new StoreException("export pointers failed", e);
        }
        J.put(out, "pointers", ptrArr);

        JsonArray depArr = J.arr();
        try (PreparedStatement ps = cx.prepareStatement(
                "SELECT DISTINCT dep.* FROM dep JOIN events e ON e.seq IN "
                        + "(SELECT seq FROM docs WHERE id=dep.derived_id AND version=dep.derived_version) "
                        + "WHERE e.case_id=?")) {
            ps.setString(1, caseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JsonObject p = J.obj();
                    J.put(p, "derivedId", rs.getString("derived_id"));
                    J.put(p, "derivedVersion", rs.getString("derived_version"));
                    J.put(p, "sourceId", rs.getString("source_id"));
                    J.put(p, "sourceVersion", rs.getString("source_version"));
                    J.put(p, "role", rs.getString("role"));
                    depArr.add(p);
                }
            }
        } catch (SQLException e) {
            throw new StoreException("export deps failed", e);
        }
        J.put(out, "deps", depArr);
        cx.commit();
        return out;
    }

    /**
     * Import an exported archive. Existing seq space must be empty for this case
     * (caller deletes the DB first in the round-trip scenario). Identity, seq order,
     * versions and edges are loaded verbatim in one transaction.
     */
    public synchronized void importCase(JsonObject archive) {
        String caseId = archive.get("caseId").getAsString();
        try {
            for (var el : archive.getAsJsonArray("events")) {
                Event e = Event.fromJson(el.getAsJsonObject());
                insertEvent(e);
            }
            for (var el : archive.getAsJsonArray("docs")) {
                insertDoc(Doc.fromJson(el.getAsJsonObject()));
            }
            for (var el : J.arrOrEmpty(archive, "pointers")) {
                JsonObject p = el.getAsJsonObject();
                insertPointer(p.get("lineage").getAsString(), p.get("seq").getAsLong(),
                        p.get("docId").getAsString(), p.get("version").getAsString());
            }
            for (var el : J.arrOrEmpty(archive, "deps")) {
                JsonObject p = el.getAsJsonObject();
                insertDep(new DepEdge(p.get("derivedId").getAsString(), p.get("derivedVersion").getAsString(),
                        p.get("sourceId").getAsString(), p.get("sourceVersion").getAsString(),
                        p.get("role").getAsString()));
            }
            cx.commit();
        } catch (SQLException e) {
            rollback();
            throw new StoreException("import failed", e);
        }
    }

    public synchronized void saveConflictResolution(String conflictEventId, String caseId, String resolution) {
        long seq = headSeqInternal();
        try (PreparedStatement ps = cx.prepareStatement(
                "INSERT OR REPLACE INTO conflict_resolutions(conflict_event_id,case_id,resolution,seq) VALUES(?,?,?,?)")) {
            ps.setString(1, conflictEventId);
            ps.setString(2, caseId);
            ps.setString(3, resolution);
            ps.setLong(4, seq);
            ps.executeUpdate();
            cx.commit();
        } catch (SQLException e) {
            rollback();
            throw new StoreException("conflict resolution failed", e);
        }
    }

    @Override
    public synchronized void close() {
        try {
            cx.close();
        } catch (SQLException e) {
            throw new StoreException("close failed", e);
        }
    }
}
