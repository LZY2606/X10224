package dsc.store;

import com.fasterxml.jackson.core.type.TypeReference;
import dsc.core.ProjectService;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SQLite 持久化。
 *
 * 原子性：事件流 + 来源文档在同一个事务里提交；未提交（进程在写入中断后重启）时
 * SQLite 回滚，所以重启后要么看到旧版本，要么看到完整新版本，绝不出现半成品。
 *
 * 并发：appendEvents 接收客户端基准序号 expectedBaseSeq，冲突抛
 * ConcurrentEditException（可解决冲突：调用方重放/合并后重试）。
 */
public final class SqliteStore implements AutoCloseable {

    private final Connection conn;

    public SqliteStore(Path dbPath) {
        try {
            Files.createDirectories(dbPath.toAbsolutePath().getParent());
            this.conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                st.executeUpdate(Schema.V1);
            }
            conn.commit();
            migrate();
        } catch (Exception e) {
            throw new IllegalStateException("cannot open store: " + dbPath, e);
        }
    }

    private void migrate() throws SQLException {
        int v = intMeta("schema_version").orElse(1);
        if (v < 2) {
            try (Statement st = conn.createStatement()) {
                for (String sql : Schema.V2.split(";")) {
                    if (!sql.isBlank()) {
                        st.execute(sql);
                    }
                }
            }
            setMeta("schema_version", "2");
            conn.commit();
        }
    }

    public Optional<Integer> intMeta(String key) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT value FROM meta WHERE key=?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(Integer.parseInt(rs.getString(1))) : Optional.empty();
            }
        }
    }

    private void setMeta(String key, String value) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO meta(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }

    public long currentSeq() {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(seq),0) FROM events")) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public String revision() {
        return Long.toString(currentSeq());
    }

    /** 原子追加事件（与来源文档同事务）。expectedBaseSeq<0 表示不检查。 */
    public synchronized void appendBatch(List<Map<String, Object>> newEvents,
                                         List<SourceDoc> sourceDocs, long expectedBaseSeq) {
        try {
            long base = currentSeq();
            if (expectedBaseSeq >= 0 && expectedBaseSeq != base) {
                throw new ConcurrentEditException(expectedBaseSeq, base);
            }
            long seq = base;
            for (Map<String, Object> ev : newEvents) {
                seq++;
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO events(seq,type,actor,basis,payload,timestamp) VALUES(?,?,?,?,?,?)")) {
                    ps.setLong(1, seq);
                    ps.setString(2, (String) ev.get("type"));
                    ps.setString(3, str(ev.get("actor")));
                    ps.setString(4, str(ev.get("basis")));
                    ps.setString(5, ProjectService.JSON.writeValueAsString(ev.get("payload")));
                    ps.setString(6, str(ev.get("timestamp")));
                    ps.executeUpdate();
                }
            }
            for (SourceDoc doc : sourceDocs) {
                putSourceDoc(doc);
            }
            setMeta("revision", Long.toString(seq));
            conn.commit();
        } catch (ConcurrentEditException e) {
            rollback();
            throw e;
        } catch (Exception e) {
            rollback();
            throw new IllegalStateException("append failed; transaction rolled back (old version intact)", e);
        }
    }

    private void rollback() {
        try {
            conn.rollback();
        } catch (SQLException ignored) {
            // best effort
        }
    }

    public void putSourceDoc(SourceDoc doc) throws Exception {
        String hash = sha256(doc.doc);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO sources(kind,source_id,version,doc,content_hash) VALUES(?,?,?,?,?) "
                        + "ON CONFLICT(kind,source_id,version) DO UPDATE SET doc=excluded.doc, "
                        + "content_hash=excluded.content_hash")) {
            ps.setString(1, doc.kind);
            ps.setString(2, doc.sourceId);
            ps.setInt(3, doc.version);
            ps.setString(4, doc.doc);
            ps.setString(5, hash);
            ps.executeUpdate();
        }
    }

    public List<SourceDoc> allSourceDocs() {
        List<SourceDoc> out = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT kind,source_id,version,doc,content_hash FROM sources ORDER BY kind,source_id,version")) {
            while (rs.next()) {
                out.add(new SourceDoc(rs.getString(1), rs.getString(2), rs.getInt(3),
                        rs.getString(4), rs.getString(5)));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    public List<Map<String, Object>> loadEvents() {
        List<Map<String, Object>> out = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT seq,type,actor,basis,payload,timestamp FROM events ORDER BY seq")) {
            while (rs.next()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("seq", rs.getLong(1));
                m.put("type", rs.getString(2));
                m.put("actor", rs.getString(3));
                m.put("basis", rs.getString(4));
                m.put("payload", ProjectService.JSON.readValue(rs.getString(5),
                        new TypeReference<Map<String, Object>>() {
                        }));
                m.put("timestamp", rs.getString(6));
                out.add(m);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    /** 整库导出为单个 JSON（身份、顺序、来源关系全部保留）。 */
    public Map<String, Object> exportPackage() {
        try {
            Map<String, Object> pkg = new LinkedHashMap<>();
            pkg.put("format", "dsc-review-export");
            pkg.put("schemaVersion", Schema.CURRENT_VERSION);
            pkg.put("ruleVersion", "dsc-rules-1.0.0");
            List<Map<String, Object>> docs = new ArrayList<>();
            for (SourceDoc d : allSourceDocs()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("kind", d.kind);
                m.put("sourceId", d.sourceId);
                m.put("version", d.version);
                m.put("doc", ProjectService.JSON.readValue(d.doc,
                        new TypeReference<Map<String, Object>>() {
                        }));
                m.put("contentHash", d.contentHash);
                docs.add(m);
            }
            pkg.put("sources", docs);
            pkg.put("events", loadEvents());
            return pkg;
        } catch (Exception e) {
            throw new IllegalStateException("export failed", e);
        }
    }

    /** 删除全部数据后导入（验证身份/顺序/来源关系在导出-删除-导入后保持）。 */
    public void importPackageAfterWipe(Map<String, Object> pkg) {
        try {
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("DELETE FROM events");
                st.executeUpdate("DELETE FROM sources");
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> docs = (List<Map<String, Object>>) pkg.getOrDefault("sources", List.of());
            for (Map<String, Object> d : docs) {
                SourceDoc doc = new SourceDoc((String) d.get("kind"), (String) d.get("sourceId"),
                        ((Number) d.get("version")).intValue(),
                        ProjectService.JSON.writeValueAsString(d.get("doc")),
                        (String) d.getOrDefault("contentHash", ""));
                if (!doc.contentHash.isEmpty() && !doc.contentHash.equals(sha256(doc.doc))) {
                    throw new IllegalStateException("content hash mismatch for " + doc.kind + ":"
                            + doc.sourceId);
                }
                putSourceDoc(doc);
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> events = (List<Map<String, Object>>) pkg.getOrDefault("events", List.of());
            long seq = 0;
            for (Map<String, Object> ev : events) {
                seq++;
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO events(seq,type,actor,basis,payload,timestamp) VALUES(?,?,?,?,?,?)")) {
                    ps.setLong(1, seq);
                    ps.setString(2, (String) ev.get("type"));
                    ps.setString(3, str(ev.get("actor")));
                    ps.setString(4, str(ev.get("basis")));
                    Object payload = ev.get("payload");
                    ps.setString(5, payload instanceof String ? (String) payload
                            : ProjectService.JSON.writeValueAsString(payload));
                    ps.setString(6, str(ev.get("timestamp")));
                    ps.executeUpdate();
                }
            }
            setMeta("revision", Long.toString(seq));
            setMeta("schema_version", Integer.toString(Schema.CURRENT_VERSION));
            conn.commit();
        } catch (Exception e) {
            rollback();
            throw new IllegalStateException("import failed; transaction rolled back", e);
        }
    }

    static String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    @Override
    public void close() {
        try {
            conn.close();
        } catch (SQLException ignored) {
            // close only
        }
    }

    public static final class SourceDoc {
        public final String kind;
        public final String sourceId;
        public final int version;
        public final String doc;
        public final String contentHash;

        public SourceDoc(String kind, String sourceId, int version, String doc, String contentHash) {
            this.kind = kind;
            this.sourceId = sourceId;
            this.version = version;
            this.doc = doc;
            this.contentHash = contentHash;
        }
    }
}
