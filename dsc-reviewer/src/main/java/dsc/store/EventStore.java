package dsc.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Append-only event log with optimistic concurrency and atomic version
 * switching.
 *
 * Atomicity contract: {@link #appendBatch} writes every event of one command
 * inside a single transaction and only moves current pointers at the end.
 * A crash mid-write leaves either the previous committed version or the full
 * new one (tested in {@code StoreAtomicityTest} with a forced rollback).
 *
 * Concurrency contract: a command carries the parent seq the editor saw.
 * If the log has advanced on the same subject, a {@link ConflictException}
 * is raised before anything is written — the second reviewer must rebase or
 * explicitly resolve.
 */
public class EventStore {

    private final Connection c;
    private final Object writeLock = new Object();
    private final ObjectMapper json = new ObjectMapper();

    public EventStore(Connection c) {
        this.c = c;
    }

    public long head() {
        try (PreparedStatement ps = c.prepareStatement("SELECT MAX(seq) FROM events");
             ResultSet rs = ps.executeQuery()) {
            c.commit();
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Event> readAll() {
        return readUpTo(Long.MAX_VALUE);
    }

    public List<Event> readUpTo(long maxSeq) {
        List<Event> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT seq,parent_seq,author,type,subject_id,payload,depends_on,created_at "
                        + "FROM events WHERE seq <= ? ORDER BY seq")) {
            ps.setLong(1, maxSeq);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
            c.commit();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    /**
     * Append a batch atomically. Pointer moves only after all rows are in.
     *
     * @param expectedParent the head seq the client branched from
     * @param newEvents      events to append; each is (type, subject, payloadJson, dependsOn)
     * @param pointerSubject subject whose current pointer should land on the last event; nullable
     */
    public List<Event> appendBatch(long expectedParent, String author,
                                   List<NewEvent> newEvents, String pointerSubject) {
        synchronized (writeLock) {
            long currentHead = headInternal();
            if (expectedParent != currentHead) {
                String conflicted = newEvents.isEmpty() ? pointerSubject : newEvents.get(0).subjectId();
                List<Event> conflicting = eventsAfter(expectedParent);
                boolean sameSubject = conflicting.stream()
                        .anyMatch(e -> e.subjectId().equals(conflicted));
                if (sameSubject) {
                    throw new ConflictException(expectedParent, currentHead, conflicted);
                }
            }
            try {
                List<Event> appended = new ArrayList<>();
                long parent = expectedParent;
                long firstSeq = currentHead + 1;
                for (NewEvent ne : newEvents) {
                    long seq = insert(parent, author, ne);
                    Event e = new Event(seq, parent, author, ne.type(), ne.subjectId(),
                            ne.payload(), ne.dependsOn(), ne.createdAt() != null ? ne.createdAt()
                                    : Instant.now().toString());
                    appended.add(e);
                    parent = seq;
                }
                if (pointerSubject != null && !appended.isEmpty()) {
                    Event last = appended.get(appended.size() - 1);
                    upsertPointer(pointerSubject, last.seq(),
                            json.writeValueAsString(appended.stream().map(Event::seq).toList()),
                            last.createdAt());
                }
                c.commit();
                return appended;
            } catch (Exception e) {
                rollback();
                if (e instanceof ConflictException ce) throw ce;
                throw new RuntimeException("append failed; transaction rolled back", e);
            }
        }
    }

    /** Test hook: force an interruption (rollback) after rows were written but before commit. */
    public void simulateCrashAfterInserts(long expectedParent, String author, List<NewEvent> events) {
        synchronized (writeLock) {
            long currentHead = headInternal();
            try {
                for (NewEvent ne : events) {
                    insert(expectedParent, author, ne);
                }
                // Simulate process kill: no commit, roll the connection back.
                c.rollback();
            } catch (SQLException e) {
                rollback();
                throw new RuntimeException(e);
            }
        }
    }

    private long insert(long parent, String author, NewEvent ne) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO events(parent_seq,author,type,subject_id,payload,depends_on,created_at)"
                        + " VALUES(?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, parent);
            ps.setString(2, author);
            ps.setString(3, ne.type().name());
            ps.setString(4, ne.subjectId());
            ps.setString(5, ne.payload());
            try {
                ps.setString(6, json.writeValueAsString(ne.dependsOn()));
            } catch (Exception e) {
                ps.setString(6, "[]");
            }
            ps.setString(7, ne.createdAt() != null ? ne.createdAt() : Instant.now().toString());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private void upsertPointer(String subject, long seq, String versionId, String at) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO current_pointers(subject_id,event_seq,version_id,updated_at) "
                        + "VALUES(?,?,?,?) ON CONFLICT(subject_id) DO UPDATE SET "
                        + "event_seq=excluded.event_seq,version_id=excluded.version_id,"
                        + "updated_at=excluded.updated_at")) {
            ps.setString(1, subject);
            ps.setLong(2, seq);
            ps.setString(3, versionId);
            ps.setString(4, at);
            ps.executeUpdate();
        }
    }

    public Long pointerFor(String subject) {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT event_seq FROM current_pointers WHERE subject_id=?")) {
            ps.setString(1, subject);
            try (ResultSet rs = ps.executeQuery()) {
                Long v = rs.next() ? rs.getLong(1) : null;
                c.commit();
                return v;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /** Explicitly switch a pointer to an older event (time-travel review / branch continuation). */
    public void switchPointer(String subject, long eventSeq) {
        synchronized (writeLock) {
            try {
                upsertPointer(subject, eventSeq, "switch@" + eventSeq, Instant.now().toString());
                c.commit();
            } catch (SQLException e) {
                rollback();
                throw new RuntimeException(e);
            }
        }
    }

    public void recordDecision(String candidateId, long eventSeq, String state,
                               String reviewer, String note) {
        synchronized (writeLock) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO decisions(candidate_id,event_seq,state,reviewer,note) "
                            + "VALUES(?,?,?,?,?) ON CONFLICT(candidate_id,event_seq) DO UPDATE SET "
                            + "state=excluded.state,note=excluded.note")) {
                ps.setString(1, candidateId);
                ps.setLong(2, eventSeq);
                ps.setString(3, state);
                ps.setString(4, reviewer);
                ps.setString(5, note);
                ps.executeUpdate();
                c.commit();
            } catch (SQLException e) {
                rollback();
                throw new RuntimeException(e);
            }
        }
    }

    public List<DecisionRow> decisions() {
        List<DecisionRow> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT candidate_id,event_seq,state,reviewer,note FROM decisions ORDER BY event_seq");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new DecisionRow(rs.getString(1), rs.getLong(2),
                        rs.getString(3), rs.getString(4), rs.getString(5)));
            }
            c.commit();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    private long headInternal() {
        try (PreparedStatement ps = c.prepareStatement("SELECT MAX(seq) FROM events");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() && rs.getObject(1) != null ? rs.getLong(1) : 0L;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private List<Event> eventsAfter(long seq) {
        List<Event> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT seq,parent_seq,author,type,subject_id,payload,depends_on,created_at "
                        + "FROM events WHERE seq > ? ORDER BY seq")) {
            ps.setLong(1, seq);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    private Event map(ResultSet rs) throws SQLException {
        List<String> deps;
        try {
            deps = json.readValue(rs.getString(7), new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            deps = List.of();
        }
        return new Event(rs.getLong(1), rs.getLong(2), rs.getString(3),
                EventType.valueOf(rs.getString(4)), rs.getString(5), rs.getString(6),
                deps, rs.getString(8));
    }

    private void rollback() {
        try {
            c.rollback();
        } catch (SQLException ignored) {
        }
    }

    /** A fact waiting to be appended. */
    public record NewEvent(EventType type, String subjectId, String payload,
                           List<String> dependsOn, String createdAt) {
        public NewEvent(EventType type, String subjectId, String payload, List<String> dependsOn) {
            this(type, subjectId, payload, dependsOn, null);
        }
    }

    public record DecisionRow(String candidateId, long eventSeq, String state,
                              String reviewer, String note) {
    }
}
