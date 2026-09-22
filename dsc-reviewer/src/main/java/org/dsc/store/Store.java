package org.dsc.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SQLite 事件溯源存储。
 * 原子性：appendEvent 及其全部实体/指针/依赖写入在单个事务里提交；
 * 写入中断（kill/-109/断电）后重启，只会看到旧版本或完整新版本，不会出现半截版本。
 * 并发：basisVersion 与当前 head 不一致时抛出 ConcurrencyException（可解决冲突）。
 */
public final class Store implements AutoCloseable {
    static final ObjectMapper JSON = new ObjectMapper();

    private final Connection conn;

    public Store(String jdbcUrl) {
        try {
            conn = DriverManager.getConnection(jdbcUrl);
            conn.setAutoCommit(false);
            init();
        } catch (SQLException e) {
            throw new StoreException("cannot open store: " + jdbcUrl, e);
        }
    }

    public static Store file(Path dbFile) {
        try {
            Files.createDirectories(dbFile.toAbsolutePath().getParent());
            return new Store("jdbc:sqlite:" + dbFile.toAbsolutePath());
        } catch (Exception e) {
            throw new StoreException("cannot create db path", e);
        }
    }

    private void init() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate(Schema.V1);
            conn.commit();
            int v = userVersion(st);
            if (v < 2) {
                try {
                    st.executeUpdate(Schema.V2_ALTER);
                } catch (SQLException ignore) {
                    // 列已存在（部分写入后的重启场景），检查后跳过
                }
                st.executeUpdate(Schema.V2_INDEX);
                st.executeUpdate("PRAGMA user_version = 2");
                conn.commit();
            }
            st.executeUpdate("PRAGMA foreign_keys = ON");
            conn.commit();
        }
    }

    private int userVersion(Statement st) throws SQLException {
        try (ResultSet rs = st.executeQuery("PRAGMA user_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public Connection connection() { return conn; }

    // ---------------------------------------------------------------- events

    public static final class ConcurrencyException extends RuntimeException {
        public final String expected;
        public final String actual;
        public ConcurrencyException(String expected, String actual) {
            super("conflict: edit based on " + expected + " but head is " + actual);
            this.expected = expected;
            this.actual = actual;
        }
    }

    public static class StoreException extends RuntimeException {
        public StoreException(String m, Throwable c) { super(m, c); }
    }

    /** 在事务中执行一批写入；整体成功才提交。 */
    public void tx(RunnableSql work) {
        try {
            work.run();
            conn.commit();
        } catch (RuntimeException e) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            throw e;
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            throw new StoreException("tx failed", e);
        }
    }

    public interface RunnableSql { void run() throws SQLException; }

    public String headEventId(String caseId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT event_id FROM events WHERE case_id=? ORDER BY seq DESC LIMIT 1")) {
            ps.setString(1, caseId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    public long nextSeq(String caseId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COALESCE(MAX(seq),0)+1 FROM events WHERE case_id=?")) {
            ps.setString(1, caseId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /**
     * 追加事件并在同一事务里执行实体写入。
     * expectedBasis 非空且不等于当前 head 时 -> ConcurrencyException。
     */
    public EventRecord appendEvent(String caseId, String type, String actor,
                                   String expectedBasis, ObjectNode payload, TxBody body) {
        try {
            String head = headEventId(caseId);
            if (expectedBasis != null && head != null && !expectedBasis.equals(head)) {
                throw new ConcurrencyException(expectedBasis, head);
            }
            long seq = nextSeq(caseId);
            String eventId = "ev-" + caseId + "-" + seq;
            EventRecord ev = new EventRecord(eventId, caseId, head, seq, type, actor,
                    Instant.now(), payload, head);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO events(event_id,case_id,parent_id,seq,type,actor,at,basis_version,payload)" +
                    " VALUES(?,?,?,?,?,?,?,?,?)")) {
                ps.setString(1, eventId);
                ps.setString(2, caseId);
                ps.setString(3, head);
                ps.setLong(4, seq);
                ps.setString(5, type);
                ps.setString(6, actor);
                ps.setString(7, ev.at().toString());
                ps.setString(8, head);
                try {
                    ps.setString(9, JSON.writeValueAsString(payload));
                } catch (com.fasterxml.jackson.core.JsonProcessingException jpe) {
                    throw new SQLException(jpe);
                }
                ps.executeUpdate();
            }
            if (body != null) body.write(eventId);
            conn.commit();
            return ev;
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            throw new StoreException("appendEvent failed", e);
        }
    }

    public interface TxBody { void write(String eventId) throws SQLException; }

    // ---------------------------------------------------------------- sources

    public void insertSource(String eventId, String caseId, String sourceId, int version,
                             String kind, String segmentId, String rawText, String unit)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO sources(source_id,version,case_id,kind,segment_id,raw_text,unit," +
                "active,superseded_by,created_event) VALUES(?,?,?,?,?,?,?,1,NULL,?)")) {
            int i = 0;
            ps.setString(++i, sourceId);
            ps.setInt(++i, version);
            ps.setString(++i, caseId);
            ps.setString(++i, kind);
            ps.setString(++i, segmentId);
            ps.setString(++i, rawText);
            ps.setString(++i, unit);
            ps.setString(++i, eventId);
            ps.executeUpdate();
        }
    }

    public void supersedeSource(String sourceId, int oldVersion, String newSourceId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE sources SET active=0, superseded_by=? WHERE source_id=? AND version=?")) {
            ps.setString(1, newSourceId);
            ps.setString(2, sourceId);
            ps.setInt(3, oldVersion);
            ps.executeUpdate();
        }
    }

    // ---------------------------------------------------------------- entities

    public void insertEntity(String eventId, String caseId, String entityId, int version,
                             String kind, String label, JsonNode json, String status) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO entities(entity_id,version,case_id,kind,label,json,status,created_event)" +
                " VALUES(?,?,?,?,?,?,?,?)")) {
            int i = 0;
            ps.setString(++i, entityId);
            ps.setInt(++i, version);
            ps.setString(++i, caseId);
            ps.setString(++i, kind);
            ps.setString(++i, label);
            try {
                    ps.setString(++i, JSON.writeValueAsString(json));
                } catch (com.fasterxml.jackson.core.JsonProcessingException jpe) {
                    throw new SQLException(jpe);
                }
            ps.setString(++i, status);
            ps.setString(++i, eventId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO entity_pointers(case_id,entity_id,current_version,updated_event)" +
                " VALUES(?,?,?,?) ON CONFLICT(case_id,entity_id)" +
                " DO UPDATE SET current_version=excluded.current_version, updated_event=excluded.updated_event")) {
            ps.setString(1, caseId);
            ps.setString(2, entityId);
            ps.setInt(3, version);
            ps.setString(4, eventId);
            ps.executeUpdate();
        }
    }

    public void setStatus(String entityId, Integer version, String status) throws SQLException {
        String sql = version == null
                ? "UPDATE entities SET status=? WHERE entity_id=?"
                : "UPDATE entities SET status=? WHERE entity_id=? AND version=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, entityId);
            if (version != null) ps.setInt(3, version);
            ps.executeUpdate();
        }
    }

    public void addDep(String eventId, String fromEntity, int fromVersion,
                       String toEntity, int toVersion) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR IGNORE INTO deps(from_entity,from_version,to_entity,to_version,event_id)" +
                " VALUES(?,?,?,?,?)")) {
            ps.setString(1, fromEntity);
            ps.setInt(2, fromVersion);
            ps.setString(3, toEntity);
            ps.setInt(4, toVersion);
            ps.setString(5, eventId);
            ps.executeUpdate();
        }
    }

    // ---------------------------------------------------------------- queries

    public record EntityRow(String entityId, int version, String kind, String label,
                            JsonNode json, String status, String createdEvent) {}

    public Optional<EntityRow> currentEntity(String entityId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT e.entity_id,e.version,e.kind,e.label,e.json,e.status,e.created_event" +
                " FROM entities e JOIN entity_pointers p" +
                " ON p.entity_id=e.entity_id AND p.current_version=e.version" +
                " WHERE e.entity_id=?")) {
            ps.setString(1, entityId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(readEntity(rs));
            }
        }
    }

    public Optional<EntityRow> entityAt(String entityId, int version) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT entity_id,version,kind,label,json,status,created_event" +
                " FROM entities WHERE entity_id=? AND version=?")) {
            ps.setString(1, entityId);
            ps.setInt(2, version);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(readEntity(rs)) : Optional.empty();
            }
        }
    }

    public List<EntityRow> entitiesByKind(String caseId, String kind) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT entity_id,version,kind,label,json,status,created_event FROM entities" +
                " WHERE case_id=? AND kind=? ORDER BY entity_id,version")) {
            ps.setString(1, caseId);
            ps.setString(2, kind);
            try (ResultSet rs = ps.executeQuery()) {
                List<EntityRow> out = new ArrayList<>();
                while (rs.next()) out.add(readEntity(rs));
                return out;
            }
        }
    }

    /** 截止到某事件（含）时该实体可见的版本——时间旅行：恢复当时结论而非覆盖历史。 */
    public Optional<EntityRow> entityVisibleAt(String entityId, String eventId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT e.entity_id,e.version,e.kind,e.label,e.json,e.status,e.created_event" +
                " FROM entities e JOIN events ev ON ev.event_id=e.created_event" +
                " WHERE e.entity_id=? AND ev.seq<=(SELECT seq FROM events WHERE event_id=?)" +
                " ORDER BY e.version DESC LIMIT 1")) {
            ps.setString(1, entityId);
            ps.setString(2, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(readEntity(rs)) : Optional.empty();
            }
        }
    }

    private EntityRow readEntity(ResultSet rs) throws SQLException {
        try {
            return new EntityRow(rs.getString(1), rs.getInt(2), rs.getString(3),
                    rs.getString(4), JSON.readTree(rs.getString(5)), rs.getString(6),
                    rs.getString(7));
        } catch (Exception e) {
            throw new SQLException("bad entity json", e);
        }
    }

    public List<EventRecord> events(String caseId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT event_id,parent_id,seq,type,actor,at,basis_version,payload" +
                " FROM events WHERE case_id=? ORDER BY seq")) {
            ps.setString(1, caseId);
            try (ResultSet rs = ps.executeQuery()) {
                List<EventRecord> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new EventRecord(rs.getString(1), caseId, rs.getString(2),
                            rs.getLong(3), rs.getString(4), rs.getString(5),
                            Instant.parse(rs.getString(6)),
                            (ObjectNode) JSON.readTree(rs.getString(8)), rs.getString(7)));
                }
                return out;
            }
        } catch (Exception e) {
            throw new SQLException("read events failed", e);
        }
    }

    public List<String> caseIds() throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT DISTINCT case_id FROM events ORDER BY case_id");
             ResultSet rs = ps.executeQuery()) {
            List<String> out = new ArrayList<>();
            while (rs.next()) out.add(rs.getString(1));
            return out;
        }
    }

    /** 沿依赖边反向传播：依赖了 changedEntity 的实体（用于“为什么需要重算”）。 */
    public List<String> dependentsOf(String changedEntity, int changedVersion) throws SQLException {
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT DISTINCT from_entity FROM deps WHERE to_entity=? AND to_version=?")) {
            ps.setString(1, changedEntity);
            ps.setInt(2, changedVersion);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString(1));
            }
        }
        return out;
    }

    public int maxEntityVersion(String entityId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COALESCE(MAX(version),0) FROM entities WHERE entity_id=?")) {
            ps.setString(1, entityId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    @Override public void close() {
        try { conn.close(); } catch (SQLException ignored) {}
    }
}
