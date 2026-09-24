package dsc.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 事件追加与读取。事件不可变、按 seq 全序，是时间旅行与导出的唯一事实来源。 */
public final class EventStore {

    public static final ObjectMapper MAPPER = new ObjectMapper();

    public record Event(long seq, String id, String type, String actor, String at, JsonNode payload) {}

    private final Db db;

    public EventStore(Db db) { this.db = db; }

    /** 在调用方事务内追加事件，返回 seq。 */
    public long append(String type, String actor, JsonNode payload) {
        String id = UUID.randomUUID().toString();
        try (PreparedStatement ps = db.conn().prepareStatement(
                "INSERT INTO events(id,type,actor,at,payload) VALUES(?,?,?,?,?)")) {
            ps.setString(1, id);
            ps.setString(2, type);
            ps.setString(3, actor);
            ps.setString(4, Instant.now().toString());
            ps.setString(5, MAPPER.writeValueAsString(payload));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        } catch (Exception e) {
            throw new RuntimeException("追加事件失败: " + type, e);
        }
    }

    /** 导入时保留原 id 与顺序。 */
    public long appendPreserving(String id, String type, String actor, String at, JsonNode payload) {
        try (PreparedStatement ps = db.conn().prepareStatement(
                "INSERT INTO events(id,type,actor,at,payload) VALUES(?,?,?,?,?)")) {
            ps.setString(1, id);
            ps.setString(2, type);
            ps.setString(3, actor);
            ps.setString(4, at);
            ps.setString(5, MAPPER.writeValueAsString(payload));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        } catch (Exception e) {
            throw new RuntimeException("导入事件失败: " + id, e);
        }
    }

    public List<Event> readAll() { return readRange(0, Long.MAX_VALUE); }

    public List<Event> readRange(long afterSeqExclusive, long upToSeqInclusive) {
        List<Event> out = new ArrayList<>();
        try (PreparedStatement ps = db.conn().prepareStatement(
                "SELECT seq,id,type,actor,at,payload FROM events WHERE seq>? AND seq<=? ORDER BY seq")) {
            ps.setLong(1, afterSeqExclusive);
            ps.setLong(2, upToSeqInclusive);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Event(rs.getLong(1), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getString(5),
                            MAPPER.readTree(rs.getString(6))));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("读取事件失败", e);
        }
        return out;
    }

    public static ObjectNode obj() { return MAPPER.createObjectNode(); }
}
