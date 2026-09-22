package dsc.store;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dsc.domain.Event;
import dsc.domain.Json;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Append-only event log with optimistic concurrency on the branch head. */
public final class EventStore {
    private final Database db;

    public EventStore(Database db) { this.db = db; }

    public long headSeq() {
        try (var rs = db.cx.createStatement().executeQuery("select coalesce(max(seq),0) from events")) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }

    public long branchHeadSeq(String branch) {
        try (var ps = db.cx.prepareStatement("select coalesce(max(seq),0) from events where branch=?")) {
            ps.setString(1, branch == null ? "" : branch);
            try (var rs = ps.executeQuery()) { return rs.next() ? rs.getLong(1) : 0L; }
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }

    /**
     * Append with optimistic concurrency. Two editors basing on the same branch head:
     * the first commits, the second gets a ConcurrentEdit exception carrying the new head.
     */
    public Event append(String branch, String type, String actor, ObjectNode payload,
                        Long expectedHead, long... depEventSeqs) {
        try {
            db.cx.setAutoCommit(false);
            String b = branch == null ? "" : branch;
            long head = branchHeadSeqTx(b);
            if (expectedHead != null && expectedHead != head) {
                db.cx.rollback();
                db.cx.setAutoCommit(true);
                throw new ConcurrentEdit(expectedHead, head, b);
            }
            long seq = headSeqTx() + 1;
            String parentId = head == 0 ? null : eventIdAtTx(head, b);
            Event ev = new Event(seq, b, type, actor, Instant.now(), payload,
                    expectedHead == null ? null : String.valueOf(expectedHead), parentId);
            try (PreparedStatement ps = db.cx.prepareStatement(
                    "insert into events(seq,id,branch,type,actor,at,payload_json,basis_seq,parent_event_id) " +
                    "values(?,?,?,?,?,?,?,?,?)")) {
                ps.setLong(1, ev.seq);
                ps.setString(2, ev.id);
                ps.setString(3, ev.branch);
                ps.setString(4, ev.type);
                ps.setString(5, ev.actor);
                ps.setString(6, ev.at.toString());
                ps.setString(7, Json.string(ev.payload));
                ps.setString(8, ev.basisSeq);
                ps.setString(9, ev.parentEventId);
                ps.executeUpdate();
            }
            db.cx.commit();
            db.cx.setAutoCommit(true);
            return ev;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        } finally {
            try { db.cx.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    private long headSeqTx() throws SQLException {
        try (var rs = db.cx.createStatement().executeQuery("select coalesce(max(seq),0) from events")) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    private long branchHeadSeqTx(String b) throws SQLException {
        try (var ps = db.cx.prepareStatement("select coalesce(max(seq),0) from events where branch=?")) {
            ps.setString(1, b);
            try (var rs = ps.executeQuery()) { return rs.next() ? rs.getLong(1) : 0L; }
        }
    }

    private String eventIdAtTx(long seq, String branch) throws SQLException {
        try (var ps = db.cx.prepareStatement("select id from events where seq=(select max(seq) from events where branch=?)")) {
            ps.setString(1, branch);
            try (var rs = ps.executeQuery()) { return rs.next() ? rs.getString(1) : null; }
        }
    }

    public List<Event> all(String branch) {
        List<Event> out = new ArrayList<>();
        String sql = "select * from events" + (branch == null ? "" : " where branch=?") + " order by seq";
        try (PreparedStatement ps = db.cx.prepareStatement(sql)) {
            if (branch != null) ps.setString(1, branch);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.add(read(rs)); }
        } catch (SQLException e) { throw new IllegalStateException(e); }
        return out;
    }

    public Event at(long seq) {
        try (var ps = db.cx.prepareStatement("select * from events where seq=?")) {
            ps.setLong(1, seq);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? read(rs) : null; }
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }

    private Event read(ResultSet rs) throws SQLException {
        return new Event(
            rs.getLong("seq"), rs.getString("branch"), rs.getString("type"), rs.getString("actor"),
            Instant.parse(rs.getString("at")), Json.parseObj(rs.getString("payload_json")),
            rs.getString("basis_seq"), rs.getString("parent_event_id"));
    }

    /** Raised when two users edit from the same version. Carries enough info to resolve. */
    public static final class ConcurrentEdit extends RuntimeException {
        public final long expectedHead, actualHead;
        public final String branch;
        public ConcurrentEdit(long expectedHead, long actualHead, String branch) {
            super("concurrent edit on branch " + branch + ": based on " + expectedHead + " now " + actualHead);
            this.expectedHead = expectedHead; this.actualHead = actualHead; this.branch = branch;
        }
    }
}
