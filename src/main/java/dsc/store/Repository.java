package dsc.store;

import dsc.domain.Artifact;
import dsc.domain.Decision;
import dsc.domain.DomainEvent;
import dsc.domain.SourceRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 只读/追加型仓储。所有写操作由 Engine 在事务内编排。
 */
public class Repository {
    private final Connection c;

    public Repository(Connection c) {
        this.c = c;
    }

    public Connection connection() {
        return c;
    }

    // ---------- 序列 ----------

    public long nextSequence(String table) {
        String col = switch (table) {
            case "sources", "artifacts", "events", "decisions" -> "sequence";
            default -> throw new IllegalArgumentException(table);
        };
        try (var st = c.createStatement();
             var rs = st.executeQuery("SELECT COALESCE(MAX(" + col + "),0)+1 FROM " + table)) {
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public long currentRevision() {
        try (var st = c.createStatement();
             var rs = st.executeQuery("SELECT revision FROM doc_revision WHERE id=1")) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------- 来源 ----------

    public void insertSource(SourceRecord s) {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO sources(id, version, kind, ref_id, original_rep, payload, sequence)
                VALUES (?,?,?,?,?,?,?)
                ON CONFLICT(id,version) DO NOTHING""")) {
            ps.setString(1, s.id());
            ps.setInt(2, s.version());
            ps.setString(3, s.kind().name());
            ps.setString(4, s.refId());
            ps.setString(5, s.originalRep());
            ps.setString(6, s.payloadJson());
            ps.setLong(7, s.sequence());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public int latestSourceVersion(String id) {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COALESCE(MAX(version),0) FROM sources WHERE id=?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<SourceRecord> allSources() {
        List<SourceRecord> out = new ArrayList<>();
        try (var st = c.createStatement();
             var rs = st.executeQuery(
                     "SELECT id,version,kind,ref_id,original_rep,payload,sequence "
                             + "FROM sources ORDER BY sequence, version")) {
            while (rs.next()) {
                out.add(new SourceRecord(rs.getString(1),
                        dsc.domain.SourceKind.valueOf(rs.getString(3)), rs.getString(4),
                        rs.getInt(2), rs.getString(5), rs.getString(6), rs.getLong(7)));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    public List<SourceRecord> sourcesForRef(String refId) {
        List<SourceRecord> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id,version,kind,ref_id,original_rep,payload,sequence "
                        + "FROM sources WHERE ref_id=? ORDER BY kind, version")) {
            ps.setString(1, refId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new SourceRecord(rs.getString(1),
                            dsc.domain.SourceKind.valueOf(rs.getString(3)), rs.getString(4),
                            rs.getInt(2), rs.getString(5), rs.getString(6), rs.getLong(7)));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    // ---------- 产物 ----------

    public void insertArtifact(Artifact a) {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO artifacts(id, version, kind, target_id, status, content,
                                      deps_hash, rule_version, sequence)
                VALUES (?,?,?,?,?,?,?,?,?)
                ON CONFLICT(id,version) DO NOTHING""")) {
            ps.setString(1, a.id());
            ps.setInt(2, a.version());
            ps.setString(3, a.kind().name());
            ps.setString(4, a.targetId());
            ps.setString(5, a.status());
            ps.setString(6, a.contentJson());
            ps.setString(7, a.depsHash());
            ps.setString(8, a.ruleVersion());
            ps.setLong(9, a.sequence());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public int latestArtifactVersion(String id) {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COALESCE(MAX(version),0) FROM artifacts WHERE id=?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<Artifact> allArtifacts() {
        List<Artifact> out = new ArrayList<>();
        try (var st = c.createStatement();
             var rs = st.executeQuery(
                     "SELECT id,version,kind,target_id,status,content,deps_hash,rule_version,sequence "
                             + "FROM artifacts ORDER BY sequence, version")) {
            while (rs.next()) {
                out.add(mapArtifact(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    public Artifact artifactVersion(String id, int version) {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id,version,kind,target_id,status,content,deps_hash,rule_version,sequence "
                        + "FROM artifacts WHERE id=? AND version=?")) {
            ps.setString(1, id);
            ps.setInt(2, version);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapArtifact(rs) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public Map<String, Integer> currentPointers() {
        Map<String, Integer> m = new LinkedHashMap<>();
        try (var rs = c.createStatement().executeQuery(
                "SELECT artifact_id, version FROM current_pointers")) {
            while (rs.next()) {
                m.put(rs.getString(1), rs.getInt(2));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return m;
    }

    public void movePointer(String artifactId, int version) {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO current_pointers(artifact_id, version) VALUES(?,?)
                ON CONFLICT(artifact_id) DO UPDATE SET version=excluded.version""")) {
            ps.setString(1, artifactId);
            ps.setInt(2, version);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private Artifact mapArtifact(ResultSet rs) throws SQLException {
        return new Artifact(rs.getString(1), rs.getInt(2),
                dsc.domain.ArtifactKind.valueOf(rs.getString(3)), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getString(7),
                rs.getString(8), rs.getLong(9));
    }

    // ---------- 事件 / 裁定 ----------

    public void insertEvent(DomainEvent e) {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO events(id,sequence,type,summary,payload,dep_snapshot) VALUES(?,?,?,?,?,?)")) {
            ps.setString(1, e.id());
            ps.setLong(2, e.sequence());
            ps.setString(3, e.type());
            ps.setString(4, e.summary());
            ps.setString(5, e.payloadJson());
            ps.setString(6, e.depSnapshotJson());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public List<DomainEvent> allEvents() {
        List<DomainEvent> out = new ArrayList<>();
        try (var rs = c.createStatement().executeQuery(
                "SELECT id,sequence,type,summary,payload,dep_snapshot FROM events ORDER BY sequence")) {
            while (rs.next()) {
                out.add(new DomainEvent(rs.getString(1), rs.getLong(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getString(6)));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    public void insertDecision(Decision d, String conflictStatus) {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO decisions(id,sequence,author,action,target_id,payload,
                                      base_version,superseded_by,conflict_status)
                VALUES (?,?,?,?,?,?,?,?,?)""")) {
            ps.setString(1, d.id());
            ps.setLong(2, d.sequence());
            ps.setString(3, d.author());
            ps.setString(4, d.action());
            ps.setString(5, d.targetId());
            ps.setString(6, d.payloadJson());
            ps.setLong(7, d.baseVersion());
            if (d.supersededBy() == null) {
                ps.setNull(8, java.sql.Types.INTEGER);
            } else {
                ps.setLong(8, d.supersededBy());
            }
            ps.setString(9, conflictStatus);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<Map<String, Object>> allDecisions() {
        List<Map<String, Object>> out = new ArrayList<>();
        try (var rs = c.createStatement().executeQuery(
                "SELECT id,sequence,author,action,target_id,payload,base_version,"
                        + "superseded_by,conflict_status FROM decisions ORDER BY sequence")) {
            while (rs.next()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getString(1));
                m.put("sequence", rs.getLong(2));
                m.put("author", rs.getString(3));
                m.put("action", rs.getString(4));
                m.put("targetId", rs.getString(5));
                m.put("payload", rs.getString(6));
                m.put("baseVersion", rs.getLong(7));
                m.put("supersededBy", rs.getObject(8));
                m.put("conflictStatus", rs.getString(9));
                out.add(m);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    public void supersedeDecision(String id, long bySequence) {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE decisions SET superseded_by=?, conflict_status='SUPERSEDED' WHERE id=?")) {
            ps.setLong(1, bySequence);
            ps.setString(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------- 区间 ----------

    public void upsertInterval(String intervalId, String segmentId, String transitionId,
                               double t0K, double t1K, long sequence) {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO intervals(interval_id,segment_id,transition_id,t0_k,t1_k,sequence)
                VALUES (?,?,?,?,?,?)
                ON CONFLICT(interval_id) DO UPDATE SET
                    segment_id=excluded.segment_id, transition_id=excluded.transition_id,
                    t0_k=excluded.t0_k, t1_k=excluded.t1_k""")) {
            ps.setString(1, intervalId);
            ps.setString(2, segmentId);
            ps.setString(3, transitionId);
            ps.setDouble(4, t0K);
            ps.setDouble(5, t1K);
            ps.setLong(6, sequence);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<Map<String, Object>> allIntervals() {
        List<Map<String, Object>> out = new ArrayList<>();
        try (var rs = c.createStatement().executeQuery(
                "SELECT interval_id,segment_id,transition_id,t0_k,t1_k,sequence FROM intervals ORDER BY sequence")) {
            while (rs.next()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("intervalId", rs.getString(1));
                m.put("segmentId", rs.getString(2));
                m.put("transitionId", rs.getString(3));
                m.put("t0K", rs.getDouble(4));
                m.put("t1K", rs.getDouble(5));
                m.put("sequence", rs.getLong(6));
                out.add(m);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    // ---------- 事务 / 修订 ----------

    public void beginTx() {
        try {
            c.setAutoCommit(false);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public void commitTx() {
        try {
            c.commit();
            c.setAutoCommit(true);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public void rollbackTx() {
        try {
            c.rollback();
            c.setAutoCommit(true);
        } catch (SQLException ignored) {
        }
    }

    public void bumpRevision(long expected) {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE doc_revision SET revision=? WHERE id=1 AND revision=?")) {
            ps.setLong(1, expected + 1);
            ps.setLong(2, expected);
            int n = ps.executeUpdate();
            if (n == 0) {
                throw new ConflictException(expected, currentRevision());
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public void setCaseMeta(String key, String value) {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO meta(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public String getCaseMeta(String key) {
        try (PreparedStatement ps = c.prepareStatement("SELECT value FROM meta WHERE key=?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
