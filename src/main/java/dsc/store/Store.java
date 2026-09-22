package dsc.store;

import dsc.domain.Derivation;
import dsc.domain.Event;
import dsc.domain.SourceDoc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SQLite 存储层。appendCommit 是唯一写入口，单事务原子提交。
 * expectedBaseSeq 实现两位使用者同版本编辑的乐观并发冲突检测。
 */
public final class Store implements AutoCloseable {

    private final String url;
    private final Connection connection;

    public Store(String path) {
        try {
            this.url = "jdbc:sqlite:" + path;
            this.connection = DriverManager.getConnection(url);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
                statement.execute("PRAGMA busy_timeout = 10000");
            }
            Schema.init(connection);
        } catch (SQLException e) {
            throw new IllegalStateException("打开数据库失败: " + path, e);
        }
    }

    public Connection connection() {
        return connection;
    }

    public long headSeq(String branch) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COALESCE(MAX(seq),0) FROM events WHERE branch = ?")) {
            ps.setString(1, branch);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 原子追加一次提交。
     *
     * @param expectedBaseSeq 调用方编辑时基于的事件序号；与分支当前头不一致则抛冲突
     */
    public synchronized long appendCommit(Commit commit, long expectedBaseSeq,
                                          boolean allowRejected) {
        long currentHead = headSeq(commit.event().branch());
        if (expectedBaseSeq != currentHead) {
            throw new ConcurrentEditException(commit.event().branch(),
                    expectedBaseSeq, currentHead);
        }
        try {
            connection.setAutoCommit(false);
            try {
                long seq = insertEvent(commit.event(), currentHead + 1);
                for (SourceDoc source : commit.newSourceVersions()) {
                    insertSource(source, seq);
                }
                for (Derivation derivation : commit.newDerivations()) {
                    insertDerivation(derivation);
                }
                for (Commit.PointerMove move : commit.pointerMoves()) {
                    upsertPointer(move);
                }
                if (commit.snapshotJson() != null) {
                    insertSnapshot(seq, commit.event().branch(),
                            commit.snapshotLabel(), commit.snapshotJson());
                }
                connection.commit();
                return seq;
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("提交失败", e);
        }
    }

    /** 写入中断模拟：在事件插入后、提交前抛出，必须整体回滚。 */
    public synchronized long appendCommitCrashAfterEvent(Commit commit, long expectedBaseSeq) {
        long currentHead = headSeq(commit.event().branch());
        if (expectedBaseSeq != currentHead) {
            throw new ConcurrentEditException(commit.event().branch(),
                    expectedBaseSeq, currentHead);
        }
        try {
            connection.setAutoCommit(false);
            insertEvent(commit.event(), currentHead + 1);
            connection.rollback();
            connection.setAutoCommit(true);
            throw new SimulatedCrashException("模拟写入中断：事件已写入但未提交，回滚");
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private long insertEvent(Event event, long assignedSeq) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO events(seq,type,actor,branch,base_seq,target_id,run_id,"
                        + "feature_id,judge_code,summary,payload,status) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setLong(1, assignedSeq);
            ps.setString(2, event.type());
            ps.setString(3, event.actor());
            ps.setString(4, event.branch());
            ps.setLong(5, event.baseSeq());
            ps.setString(6, event.targetId());
            ps.setString(7, event.runId());
            ps.setString(8, event.featureId());
            ps.setString(9, event.judgeCode());
            ps.setString(10, event.summary());
            ps.setString(11, Json.write(event.payload()));
            ps.setString(12, event.status());
            ps.executeUpdate();
        }
        return assignedSeq;
    }

    private void insertSource(SourceDoc source, long seq) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO sources(id,sample_id,run_id,kind,version,raw_text,fields,"
                        + "unit_context,diagnostics,rationale,event_seq) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, source.id());
            ps.setString(2, source.sampleId());
            ps.setString(3, source.runId());
            ps.setString(4, source.kind().code());
            ps.setInt(5, source.version());
            ps.setString(6, source.rawText());
            ps.setString(7, Json.write(source.fields()));
            ps.setString(8, source.unitContext());
            ps.setString(9, Json.write(source.diagnostics()));
            ps.setString(10, source.rationale());
            ps.setLong(11, seq);
            ps.executeUpdate();
        }
    }

    private void insertDerivation(Derivation derivation) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO derivations(key,facet,version,status,kind,event_seq,"
                        + "dependency_versions,diagnostics,why,json) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, derivation.key());
            ps.setString(2, derivation.facet());
            ps.setInt(3, derivation.version());
            ps.setString(4, derivation.status());
            ps.setString(5, derivation.kind());
            ps.setLong(6, derivation.eventSeq());
            ps.setString(7, Json.write(derivation.dependencyVersions()));
            ps.setString(8, Json.write(derivation.diagnostics()));
            ps.setString(9, derivation.why());
            ps.setString(10, derivation.json());
            ps.executeUpdate();
        }
    }

    private void upsertPointer(Commit.PointerMove move) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO pointers(scope,ref,key,current_version) VALUES(?,?,?,?) "
                        + "ON CONFLICT(scope,ref,key) DO UPDATE SET current_version=excluded.current_version")) {
            ps.setString(1, move.scope());
            ps.setString(2, move.ref());
            ps.setString(3, move.key());
            ps.setInt(4, move.currentVersion());
            ps.executeUpdate();
        }
    }

    private void insertSnapshot(long seq, String branch, String label, String json)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO snapshots(event_seq,branch,label,json) VALUES(?,?,?,?)")) {
            ps.setLong(1, seq);
            ps.setString(2, branch);
            ps.setString(3, label);
            ps.setString(4, json);
            ps.executeUpdate();
        }
    }

    public List<Event> events(String branch) {
        List<Event> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM events WHERE branch = ? ORDER BY seq")) {
            ps.setString(1, branch);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(readEvent(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    public List<Event> allEvents() {
        List<Event> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM events ORDER BY seq");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(readEvent(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    public Map<String, List<String>> branches() {
        Map<String, List<String>> out = new HashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT branch, label FROM snapshots ORDER BY event_seq");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.computeIfAbsent(rs.getString(1), k -> new ArrayList<>())
                        .add(rs.getString(2));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Event readEvent(ResultSet rs) throws SQLException {
        Map<String, String> payload = Json.read(rs.getString("payload"), Map.class);
        return new Event(rs.getLong("seq"), rs.getString("type"), rs.getString("actor"),
                rs.getString("branch"), rs.getLong("base_seq"), rs.getString("target_id"),
                rs.getString("run_id"), rs.getString("feature_id"), rs.getString("judge_code"),
                rs.getString("summary"), payload, rs.getString("status"));
    }

    /** 截至某事件序号，每个来源 id 的最新版本。 */
    public Map<String, SourceDoc> sourcesAt(long seq, String branch) {
        Map<String, SourceDoc> out = new HashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT s.* FROM sources s JOIN events e ON e.seq = s.event_seq "
                        + "WHERE e.branch = ? AND s.event_seq <= ? "
                        + "ORDER BY s.version")) {
            ps.setString(1, branch);
            ps.setLong(2, seq);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getString("id"), readSource(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private SourceDoc readSource(ResultSet rs) throws SQLException {
        return new SourceDoc(
                rs.getString("id"), rs.getString("sample_id"), rs.getString("run_id"),
                dsc.domain.SourceKind.fromCode(rs.getString("kind")),
                rs.getInt("version"), rs.getString("raw_text"),
                Json.read(rs.getString("fields"), Map.class),
                rs.getString("unit_context"),
                Json.read(rs.getString("diagnostics"), List.class),
                rs.getString("rationale"), rs.getLong("event_seq"));
    }

    public List<SourceDoc> allSourceVersions(String id) {
        List<SourceDoc> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM sources WHERE id = ? ORDER BY version")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(readSource(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    public int nextSourceVersion(String id) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COALESCE(MAX(version),0)+1 FROM sources WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 1;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<Derivation> derivationsAt(long seq) {
        List<Derivation> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM derivations WHERE event_seq <= ? ORDER BY key, version")) {
            ps.setLong(1, seq);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(readDerivation(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Derivation readDerivation(ResultSet rs) throws SQLException {
        return new Derivation(rs.getString("key"), rs.getString("facet"),
                rs.getInt("version"), rs.getString("status"), rs.getString("kind"),
                rs.getLong("event_seq"),
                Json.read(rs.getString("dependency_versions"), Map.class),
                Json.read(rs.getString("diagnostics"), List.class),
                rs.getString("why"), rs.getString("json"));
    }

    public String snapshotAt(long seq, String branch) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT json FROM snapshots WHERE event_seq = ? AND branch = ?")) {
            ps.setLong(1, seq);
            ps.setString(2, branch);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<long[]> snapshotSeqs(String branch) {
        List<long[]> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT event_seq FROM snapshots WHERE branch = ? ORDER BY event_seq")) {
            ps.setString(1, branch);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new long[]{rs.getLong(1)});
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    public void putMeta(String key, String value) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO meta(key,value) VALUES(?,?) "
                        + "ON CONFLICT(key) DO UPDATE SET value=excluded.value")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public String getMeta(String key) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT value FROM meta WHERE key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
