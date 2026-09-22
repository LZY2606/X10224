package dsc.store;

import com.fasterxml.jackson.databind.JsonNode;
import dsc.Json;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Versioned source rows. Instrument output, mass, scan rate, atmosphere, temperature
 * calibration and user tangents are stored separately (kind + subject), each revision
 * producing a new version while old versions are retained. Raw representation is kept.
 */
public final class SourceStore {
    private final Db db;

    public SourceStore(Db db) {
        this.db = db;
    }

    public record SourceRow(String id, String sampleId, String kind, String subject, int version,
            JsonNode payload, String raw, long createdSeq) {}

    /** Add a new version of a source; caller must hold a transaction. */
    public SourceRow add(String sampleId, String kind, String subject, JsonNode payload, String raw, long seq) {
        int version = currentVersion(sampleId, kind, subject) + 1;
        String id = "src-" + db.nextSeq("source");
        try (PreparedStatement ps = db.conn().prepareStatement(
                "INSERT INTO sources(id,sample_id,kind,subject,version,payload,raw,created_seq)"
                        + " VALUES(?,?,?,?,?,?,?,?)")) {
            ps.setString(1, id);
            ps.setString(2, sampleId);
            ps.setString(3, kind);
            ps.setString(4, subject);
            ps.setInt(5, version);
            ps.setString(6, Json.write(payload));
            ps.setString(7, raw);
            ps.setLong(8, seq);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return new SourceRow(id, sampleId, kind, subject, version, payload, raw, seq);
    }

    public int currentVersion(String sampleId, String kind, String subject) {
        try (Statement s = db.conn().createStatement();
                ResultSet rs = s.executeQuery("SELECT COALESCE(MAX(version),0) FROM sources WHERE sample_id='"
                        + sampleId + "' AND kind='" + kind + "' AND subject='" + subject + "'")) {
            rs.next();
            return rs.getInt(1);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public Optional<SourceRow> get(String sampleId, String kind, String subject) {
        int v = currentVersion(sampleId, kind, subject);
        if (v == 0) {
            return Optional.empty();
        }
        return get(sampleId, kind, subject, v);
    }

    public Optional<SourceRow> get(String sampleId, String kind, String subject, int version) {
        String sql = "SELECT id,sample_id,kind,subject,version,payload,raw,created_seq FROM sources"
                + " WHERE sample_id=? AND kind=? AND subject=? AND version=?";
        try (PreparedStatement ps = db.conn().prepareStatement(sql)) {
            ps.setString(1, sampleId);
            ps.setString(2, kind);
            ps.setString(3, subject);
            ps.setInt(4, version);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? Optional.of(row(rs)) : Optional.empty();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public Optional<SourceRow> byId(String id) {
        try (PreparedStatement ps = db.conn().prepareStatement(
                "SELECT id,sample_id,kind,subject,version,payload,raw,created_seq FROM sources WHERE id=?")) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? Optional.of(row(rs)) : Optional.empty();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public List<SourceRow> all() {
        return query("SELECT id,sample_id,kind,subject,version,payload,raw,created_seq FROM sources"
                + " ORDER BY created_seq, id");
    }

    /** Latest version per (kind,subject) visible at the given event seq. */
    public List<SourceRow> asOf(String sampleId, long seq) {
        List<SourceRow> all = query(
                "SELECT id,sample_id,kind,subject,version,payload,raw,created_seq FROM sources"
                        + " WHERE sample_id='" + sampleId + "' AND created_seq<=" + seq
                        + " ORDER BY kind, subject, version");
        Map<String, SourceRow> latest = new LinkedHashMap<>();
        for (SourceRow r : all) {
            latest.put(r.kind() + "|" + r.subject(), r);
        }
        return new ArrayList<>(latest.values());
    }

    public List<SourceRow> query(String sql) {
        List<SourceRow> out = new ArrayList<>();
        try (Statement s = db.conn().createStatement(); ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                out.add(row(rs));
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    private SourceRow row(ResultSet rs) throws Exception {
        String raw = rs.getString(7);
        return new SourceRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getInt(5), Json.parse(rs.getString(6)), raw == null ? "" : raw, rs.getLong(8));
    }
}
