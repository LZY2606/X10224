package dsc.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import dsc.model.Branch;
import dsc.model.Derivation;
import dsc.model.Event;
import dsc.model.Interval;
import dsc.model.Source;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Single repository façade over SQLite. Mutations run in one transaction so that a
 * revision becomes visible together with its event, derivations and pointer move.
 */
public final class Repository {

    private final Database db;
    private final ObjectMapperHolder h;

    public Repository(Database db) {
        this.db = db;
        this.h = new ObjectMapperHolder(new ObjectMapper());
    }

    public Database database() {
        return db;
    }

    public ObjectMapper mapper() {
        return h.mapper;
    }

    public void createProject(String projectId, String code, String name, long nowMs) {
        db.transaction(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO projects(project_id,code,name,created_at_ms) VALUES(?,?,?,?)")) {
                ps.setString(1, projectId);
                ps.setString(2, code);
                ps.setString(3, name);
                ps.setLong(4, nowMs);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public Map<String, String> project(String projectId) {
        return db.transactionReturn(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT project_id,code,name,created_at_ms FROM projects WHERE project_id=?")) {
                ps.setString(1, projectId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("projectId", rs.getString(1));
                    m.put("code", rs.getString(2));
                    m.put("name", rs.getString(3));
                    m.put("createdAtMs", Long.toString(rs.getLong(4)));
                    return m;
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public List<Map<String, String>> projects() {
        return db.transactionReturn(c -> {
            List<Map<String, String>> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT project_id,code,name FROM projects ORDER BY code");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("projectId", rs.getString(1));
                    m.put("code", rs.getString(2));
                    m.put("name", rs.getString(3));
                    out.add(m);
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return out;
        });
    }

    public void insertSource(Source source) {
        db.transaction(c -> {
            try {
                Sources.insert(c, source, h);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public List<Source> sources(String projectId) {
        return db.transactionReturn(c -> {
            try {
                return Sources.allForProject(c, projectId, h);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public Source source(String sourceId) {
        return db.transactionReturn(c -> {
            try {
                return Sources.byId(c, sourceId, h);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public Event appendEvent(Event event) {
        return db.transactionReturn(c -> {
            try {
                return Events.append(c, event);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public Event event(String eventId) {
        return db.transactionReturn(c -> {
            try {
                return Events.byId(c, eventId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public Event eventAt(String projectId, long seq) {
        return db.transactionReturn(c -> {
            try {
                return Events.bySeq(c, projectId, seq);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public List<Event> events(String projectId) {
        return db.transactionReturn(c -> {
            try {
                return Events.forProject(c, projectId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public long eventCount(String projectId) {
        return db.transactionReturn(c -> {
            try {
                return Events.count(c, projectId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void upsertBranch(Branch branch) {
        db.transaction(c -> {
            try {
                Events.upsertBranch(c, branch);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public List<Branch> branches(String projectId) {
        return db.transactionReturn(c -> {
            try {
                return Events.branches(c, projectId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public Branch pointer(String projectId) {
        return db.transactionReturn(c -> {
            try {
                return Events.pointer(c, projectId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void setPointer(String projectId, Branch branch) {
        db.transaction(c -> {
            try {
                Events.setPointer(c, projectId, branch);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void insertDerivation(Derivation derivation, String contentHash) {
        db.transaction(c -> {
            try {
                Derivations.insert(c, derivation, contentHash);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public List<Derivation> derivations(String projectId) {
        return db.transactionReturn(c -> {
            try {
                return Derivations.forProject(c, projectId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public Derivation derivation(String derivationId) {
        return db.transactionReturn(c -> {
            try {
                return Derivations.byId(c, derivationId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void upsertInterval(Interval interval) {
        db.transaction(c -> {
            try {
                Derivations.upsertInterval(c, interval);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public List<Interval> intervals(String projectId) {
        return db.transactionReturn(c -> {
            try {
                return Derivations.intervals(c, projectId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Atomic revision unit: append event + derived candidates + pointer move in one
     * transaction. If the work throws (or the crash hook fires), nothing is visible.
     */
    public CommitResult commit(String projectId, CommitUnit unit) {
        final Event[] stored = new Event[1];
        db.transaction(c -> {
            try {
                stored[0] = Events.append(c, unit.event);
                for (Source s : unit.sources) {
                    Sources.insert(c, s, h);
                }
                for (Interval interval : unit.intervals) {
                    Derivations.upsertInterval(c, interval);
                }
                for (Map.Entry<Derivation, String> e : unit.derivations.entrySet()) {
                    Derivations.insert(c, e.getKey(), e.getValue());
                }
                for (Branch branch : unit.branches) {
                    Events.upsertBranch(c, branch);
                }
                if (unit.pointer != null) {
                    Events.setPointer(c, projectId, unit.pointer);
                }
                if (unit.mark != null) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO replay_marks(project_id,case_key,mark,at_seq) VALUES(?,?,?,?) "
                                    + "ON CONFLICT(project_id,case_key,mark) DO UPDATE SET at_seq=excluded.at_seq")) {
                        ps.setString(1, projectId);
                        ps.setString(2, unit.mark.caseKey());
                        ps.setString(3, unit.mark.mark());
                        ps.setLong(4, unit.mark.atSeq());
                        ps.executeUpdate();
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
        return new CommitResult(stored[0]);
    }

    public void mark(String projectId, String caseKey, String mark, long atSeq) {
        db.transaction(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO replay_marks(project_id,case_key,mark,at_seq) VALUES(?,?,?,?) "
                            + "ON CONFLICT(project_id,case_key,mark) DO UPDATE SET at_seq=excluded.at_seq")) {
                ps.setString(1, projectId);
                ps.setString(2, caseKey);
                ps.setString(3, mark);
                ps.setLong(4, atSeq);
                ps.executeUpdate();
            } catch ( SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public Map<String, Long> marks(String projectId, String caseKey) {
        return db.transactionReturn(c -> {
            Map<String, Long> out = new LinkedHashMap<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT mark,at_seq FROM replay_marks WHERE project_id=? AND case_key=? ORDER BY mark")) {
                ps.setString(1, projectId);
                ps.setString(2, caseKey);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.put(rs.getString(1), rs.getLong(2));
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return out;
        });
    }

    public int derivationCount() {
        return db.transactionReturn(c -> {
            try {
                return Derivations.countAll(c);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
