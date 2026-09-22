package dsc.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dsc.Json;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Append-only machine-readable event log. Every mutation appends events inside the same transaction. */
public final class EventStore {
    private final Db db;

    public EventStore(Db db) {
        this.db = db;
    }

    public record Event(long seq, String id, String type, String subject, JsonNode payload, String at) {}

    /** Append an event; caller must hold a transaction. Returns the event seq. */
    public long append(String type, String subject, ObjectNode payload) {
        try (PreparedStatement ps = db.conn().prepareStatement(
                "INSERT INTO events(id,type,subject,payload,at) VALUES(?,?,?,?,?)")) {
            payload.put("ruleNote", payload.path("ruleNote").asText(""));
            long guess = db.eventSeq() + 1;
            ps.setString(1, "evt-" + guess);
            ps.setString(2, type);
            ps.setString(3, subject);
            ps.setString(4, Json.write(payload));
            ps.setString(5, Instant.now().toString());
            ps.executeUpdate();
            try (Statement s = db.conn().createStatement();
                    ResultSet rs = s.executeQuery("SELECT last_insert_rowid()")) {
                rs.next();
                long seq = rs.getLong(1);
                try (Statement fix = db.conn().createStatement()) {
                    fix.executeUpdate("UPDATE events SET id='evt-" + seq + "' WHERE seq=" + seq);
                }
                return seq;
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public List<Event> list() {
        return query("SELECT seq,id,type,subject,payload,at FROM events ORDER BY seq");
    }

    public List<Event> upTo(long seq) {
        return query("SELECT seq,id,type,subject,payload,at FROM events WHERE seq<=" + seq + " ORDER BY seq");
    }

    private List<Event> query(String sql) {
        List<Event> out = new ArrayList<>();
        try (Statement s = db.conn().createStatement(); ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                out.add(new Event(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        Json.parse(rs.getString(5)), rs.getString(6)));
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return out;
    }
}
