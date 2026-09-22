package dsc.store;

import com.fasterxml.jackson.databind.JsonNode;
import dsc.Json;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Resolvable conflicts raised when two users edit from the same base version.
 * Both branches are retained; resolving picks one, the loser stays on record.
 */
public final class ConflictStore {
    private final Db db;

    public ConflictStore(Db db) {
        this.db = db;
    }

    public record Conflict(String id, String subject, long baseVersion, JsonNode branches,
            String status, JsonNode resolution, long createdSeq, Long resolvedSeq) {}

    public Conflict raise(String subject, long baseVersion, JsonNode branches, long seq) {
        String id = "cnf-" + db.nextSeq("conflict");
        try (PreparedStatement ps = db.conn().prepareStatement(
                "INSERT INTO conflicts(id,subject,base_version,branches,status,created_seq)"
                        + " VALUES(?,?,?,?,'open',?)")) {
            ps.setString(1, id);
            ps.setString(2, subject);
            ps.setLong(3, baseVersion);
            ps.setString(4, Json.write(branches));
            ps.setLong(5, seq);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return new Conflict(id, subject, baseVersion, branches, "open", null, seq, null);
    }

    public void resolve(String id, JsonNode resolution, long seq) {
        try (PreparedStatement ps = db.conn().prepareStatement(
                "UPDATE conflicts SET status='resolved', resolution=?, resolved_seq=? WHERE id=?")) {
            ps.setString(1, Json.write(resolution));
            ps.setLong(2, seq);
            ps.setString(3, id);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public Optional<Conflict> byId(String id) {
        try (PreparedStatement ps = db.conn().prepareStatement(
                "SELECT id,subject,base_version,branches,status,resolution,created_seq,resolved_seq"
                        + " FROM conflicts WHERE id=?")) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? Optional.of(row(rs)) : Optional.empty();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public List<Conflict> all() {
        List<Conflict> out = new ArrayList<>();
        try (Statement s = db.conn().createStatement();
                ResultSet rs = s.executeQuery(
                        "SELECT id,subject,base_version,branches,status,resolution,created_seq,resolved_seq"
                                + " FROM conflicts ORDER BY created_seq, id")) {
            while (rs.next()) {
                out.add(row(rs));
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    private Conflict row(ResultSet rs) throws Exception {
        String res = rs.getString(6);
        long resolvedSeq = rs.getLong(8);
        return new Conflict(rs.getString(1), rs.getString(2), rs.getLong(3), Json.parse(rs.getString(4)),
                rs.getString(5), res == null ? null : Json.parse(res), rs.getLong(7),
                rs.wasNull() ? null : resolvedSeq);
    }
}
