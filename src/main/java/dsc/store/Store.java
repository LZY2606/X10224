package dsc.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Versioned store. Every derivation produces an independent version row;
 * the current pointer switches only after a complete, successful commit.
 * A crash mid-write leaves either the old version or a complete new one.
 */
public final class Store {
    private final Database db;

    public Store(Database db) {
        this.db = db;
    }

    public static String newId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    // ---------- events ----------

    public JsonNode event(String type, JsonNode payload) {
        String id = newId("evt");
        String now = Instant.now().toString();
        try (Connection c = db.connect();
             PreparedStatement p = c.prepareStatement(
                     "INSERT INTO events(id,type,payload,created_at) VALUES(?,?,?,?)")) {
            p.setString(1, id);
            p.setString(2, type);
            p.setString(3, Database.write(payload));
            p.setString(4, now);
            p.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        var node = Database.obj();
        node.put("id", id);
        node.put("type", type);
        node.set("payload", payload);
        node.put("created_at", now);
        return node;
    }

    public List<JsonNode> events() {
        List<JsonNode> out = new ArrayList<>();
        try (Connection c = db.connect();
             PreparedStatement p = c.prepareStatement(
                     "SELECT seq,id,type,payload,created_at FROM events ORDER BY seq")) {
            ResultSet rs = p.executeQuery();
            while (rs.next()) {
                var n = Database.obj();
                n.put("seq", rs.getLong(1));
                n.put("id", rs.getString(2));
                n.put("type", rs.getString(3));
                n.set("payload", Database.parse(rs.getString(4)));
                n.put("created_at", rs.getString(5));
                out.add(n);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    // ---------- sources ----------

    public record SourceRow(String id, String kind, int version, JsonNode payload, long seq, String replaces) {}

    public SourceRow putSource(String kind, int version, JsonNode payload, String replaces) {
        String id = newId("src");
        try (Connection c = db.connect();
             PreparedStatement p = c.prepareStatement(
                     "INSERT INTO sources(id,kind,version,payload,seq,replaces) VALUES(?,?,?,?,?,?)")) {
            p.setString(1, id);
            p.setString(2, kind);
            p.setInt(3, version);
            p.setString(4, Database.write(payload));
            p.setLong(5, nextSeq(c));
            p.setString(6, replaces);
            p.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return new SourceRow(id, kind, version, payload, 0, replaces);
    }

    public List<SourceRow> sources(String kind) {
        List<SourceRow> out = new ArrayList<>();
        try (Connection c = db.connect();
             PreparedStatement p = c.prepareStatement(
                     "SELECT id,kind,version,payload,seq,replaces FROM sources WHERE kind=? ORDER BY version")) {
            p.setString(1, kind);
            ResultSet rs = p.executeQuery();
            while (rs.next()) {
                out.add(new SourceRow(rs.getString(1), rs.getString(2), rs.getInt(3),
                        Database.parse(rs.getString(4)), rs.getLong(5), rs.getString(6)));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    public List<SourceRow> allSources() {
        List<SourceRow> out = new ArrayList<>();
        try (Connection c = db.connect();
             PreparedStatement p = c.prepareStatement(
                     "SELECT id,kind,version,payload,seq,replaces FROM sources ORDER BY kind, version")) {
            ResultSet rs = p.executeQuery();
            while (rs.next()) {
                out.add(new SourceRow(rs.getString(1), rs.getString(2), rs.getInt(3),
                        Database.parse(rs.getString(4)), rs.getLong(5), rs.getString(6)));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    public Optional<SourceRow> latestSource(String kind) {
        var list = sources(kind);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(list.size() - 1));
    }

    // ---------- derived ----------

    public record DerivedRow(String id, String kind, int version, JsonNode inputs,
                             String status, JsonNode payload, long seq) {}

    public List<DerivedRow> derived(String kind) {
        List<DerivedRow> out = new ArrayList<>();
        try (Connection c = db.connect();
             PreparedStatement p = c.prepareStatement(
                     "SELECT id,kind,version,inputs,status,payload,seq FROM derived WHERE kind=? ORDER BY version")) {
            p.setString(1, kind);
            ResultSet rs = p.executeQuery();
            while (rs.next()) {
                out.add(new DerivedRow(rs.getString(1), rs.getString(2), rs.getInt(3),
                        Database.parse(rs.getString(4)), rs.getString(5),
                        Database.parse(rs.getString(6)), rs.getLong(7)));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    public List<DerivedRow> allDerived() {
        List<DerivedRow> out = new ArrayList<>();
        try (Connection c = db.connect();
             PreparedStatement p = c.prepareStatement(
                     "SELECT id,kind,version,inputs,status,payload,seq FROM derived ORDER BY kind, version")) {
            ResultSet rs = p.executeQuery();
            while (rs.next()) {
                out.add(new DerivedRow(rs.getString(1), rs.getString(2), rs.getInt(3),
                        Database.parse(rs.getString(4)), rs.getString(5),
                        Database.parse(rs.getString(6)), rs.getLong(7)));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    public Optional<DerivedRow> derivedById(String id) {
        try (Connection c = db.connect();
             PreparedStatement p = c.prepareStatement(
                     "SELECT id,kind,version,inputs,status,payload,seq FROM derived WHERE id=?")) {
            p.setString(1, id);
            ResultSet rs = p.executeQuery();
            if (rs.next()) {
                return Optional.of(new DerivedRow(rs.getString(1), rs.getString(2), rs.getInt(3),
                        Database.parse(rs.getString(4)), rs.getString(5),
                        Database.parse(rs.getString(6)), rs.getLong(7)));
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void setDerivedStatus(String id, String status) {
        try (Connection c = db.connect();
             PreparedStatement p = c.prepareStatement("UPDATE derived SET status=? WHERE id=?")) {
            p.setString(1, status);
            p.setString(2, id);
            p.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // ---------- atomic publish ----------

    public record DerivedInsert(String id, String kind, int version, JsonNode inputs,
                                String status, JsonNode payload) {}

    /** A batch of derived inserts plus pointer updates, committed atomically. */
    public record PublishBatch(List<DerivedInsert> inserts, List<PointerUpdate> pointers,
                               JsonNode eventTypeAndPayload) {}

    public record PointerUpdate(String name, int version) {}

    /** Test hook: when set, the runnable runs mid-transaction (may throw to simulate a crash). */
    public Runnable midWriteHook;

    public void publish(List<DerivedInsert> inserts, List<PointerUpdate> pointers) {
        Connection c = null;
        try {
            c = db.connect();
            c.setAutoCommit(false);
            long seq = nextSeq(c);
            try (PreparedStatement p = c.prepareStatement(
                    "INSERT INTO derived(id,kind,version,inputs,status,payload,seq) VALUES(?,?,?,?,?,?,?)")) {
                for (DerivedInsert ins : inserts) {
                    p.setString(1, ins.id());
                    p.setString(2, ins.kind());
                    p.setInt(3, ins.version());
                    p.setString(4, Database.write(ins.inputs()));
                    p.setString(5, ins.status());
                    p.setString(6, Database.write(ins.payload()));
                    p.setLong(7, seq);
                    p.executeUpdate();
                }
            }
            if (midWriteHook != null) {
                midWriteHook.run(); // simulated crash point
            }
            try (PreparedStatement p = c.prepareStatement(
                    "INSERT INTO pointers(name,version) VALUES(?,?) " +
                            "ON CONFLICT(name) DO UPDATE SET version=excluded.version")) {
                for (PointerUpdate u : pointers) {
                    p.setString(1, u.name());
                    p.setInt(2, u.version());
                    p.executeUpdate();
                }
            }
            c.commit();
        } catch (Exception e) {
            if (c != null) {
                try { c.rollback(); } catch (SQLException ignored) { }
            }
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException(e);
        } finally {
            if (c != null) {
                try { c.close(); } catch (SQLException ignored) { }
            }
        }
    }

    public int pointer(String name) {
        try (Connection c = db.connect();
             PreparedStatement p = c.prepareStatement("SELECT version FROM pointers WHERE name=?")) {
            p.setString(1, name);
            ResultSet rs = p.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public int head() {
        return pointer("head");
    }

    /** Advance the global head; returns the new head. */
    public int bumpHead() {
        int h = head() + 1;
        publish(List.of(), List.of(new PointerUpdate("head", h)));
        return h;
    }

    // ---------- optimistic concurrency ----------

    public record Conflict(String id, int baseVersion, int currentVersion, JsonNode payload, String status) {}

    /** Returns null on success, or the conflict id when the head moved since baseVersion. */
    public String saveOptimistic(int baseVersion, JsonNode editPayload, Runnable apply) {
        int current = head();
        if (current == baseVersion) {
            apply.run();
            return null;
        }
        String id = newId("conflict");
        try (Connection c = db.connect();
             PreparedStatement p = c.prepareStatement(
                     "INSERT INTO conflicts(id,base_version,current_version,payload,status,seq) VALUES(?,?,?,?,?,?)")) {
            p.setString(1, id);
            p.setInt(2, baseVersion);
            p.setInt(3, current);
            p.setString(4, Database.write(editPayload));
            p.setString(5, "open");
            p.setLong(6, nextSeq(c));
            p.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        var pl = Database.obj();
        pl.put("conflict", id);
        pl.put("base_version", baseVersion);
        pl.put("current_version", current);
        pl.set("edit", editPayload);
        event("decision.conflict", pl);
        return id;
    }

    public List<Conflict> conflicts() {
        List<Conflict> out = new ArrayList<>();
        try (Connection c = db.connect();
             PreparedStatement p = c.prepareStatement(
                     "SELECT id,base_version,current_version,payload,status FROM conflicts ORDER BY seq")) {
            ResultSet rs = p.executeQuery();
            while (rs.next()) {
                out.add(new Conflict(rs.getString(1), rs.getInt(2), rs.getInt(3),
                        Database.parse(rs.getString(4)), rs.getString(5)));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    public void resolveConflict(String id, String resolution, JsonNode note) {
        try (Connection c = db.connect();
             PreparedStatement p = c.prepareStatement("UPDATE conflicts SET status=? WHERE id=?")) {
            p.setString(1, "resolved:" + resolution);
            p.setString(2, id);
            p.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        var pl = Database.obj();
        pl.put("conflict", id);
        pl.put("resolution", resolution);
        pl.set("note", note);
        event("decision.resolved", pl);
    }

    // ---------- helpers ----------

    private long nextSeq(Connection c) throws SQLException {
        try (var s = c.createStatement()) {
            ResultSet rs = s.executeQuery(
                    "SELECT COALESCE(MAX(seq),0) FROM (" +
                            "SELECT seq FROM sources UNION ALL SELECT seq FROM derived UNION ALL SELECT seq FROM conflicts)");
            long max = 0;
            while (rs.next()) max = Math.max(max, rs.getLong(1));
            return max + 1;
        }
    }

    public ArrayNode inputsOf(Object... kindVersionPairs) {
        ArrayNode arr = Database.JSON.createArrayNode();
        for (int i = 0; i < kindVersionPairs.length; i += 2) {
            var n = Database.obj();
            n.put("ref", String.valueOf(kindVersionPairs[i]));
            n.put("version", Integer.parseInt(String.valueOf(kindVersionPairs[i + 1])));
            arr.add(n);
        }
        return arr;
    }
}
