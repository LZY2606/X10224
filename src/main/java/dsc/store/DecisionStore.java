package dsc.store;

import com.fasterxml.jackson.databind.JsonNode;
import dsc.Json;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Human/machine decisions (baseline adoption, direction ruling). Versioned per (subject,kind). */
public final class DecisionStore {
    private final Db db;

    public DecisionStore(Db db) {
        this.db = db;
    }

    public record Decision(String id, String subject, String kind, String choice, String rationale,
            String author, long seq, int version) {}

    public Decision decide(String subject, String kind, String choice, String rationale,
            String author, long seq) {
        int version = version(subject, kind) + 1;
        String id = "dec-" + db.nextSeq("decision");
        try (PreparedStatement ps = db.conn().prepareStatement(
                "INSERT INTO decisions(id,subject,kind,choice,rationale,author,seq,version)"
                        + " VALUES(?,?,?,?,?,?,?,?)")) {
            ps.setString(1, id);
            ps.setString(2, subject);
            ps.setString(3, kind);
            ps.setString(4, choice);
            ps.setString(5, rationale);
            ps.setString(6, author);
            ps.setLong(7, seq);
            ps.setInt(8, version);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return new Decision(id, subject, kind, choice, rationale, author, seq, version);
    }

    public int version(String subject, String kind) {
        try (Statement s = db.conn().createStatement();
                ResultSet rs = s.executeQuery("SELECT COALESCE(MAX(version),0) FROM decisions WHERE subject='"
                        + subject + "' AND kind='" + kind + "'")) {
            rs.next();
            return rs.getInt(1);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public Optional<Decision> latest(String subject, String kind) {
        try (PreparedStatement ps = db.conn().prepareStatement(
                "SELECT id,subject,kind,choice,rationale,author,seq,version FROM decisions"
                        + " WHERE subject=? AND kind=? ORDER BY version DESC LIMIT 1")) {
            ps.setString(1, subject);
            ps.setString(2, kind);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? Optional.of(row(rs)) : Optional.empty();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public List<Decision> all() {
        return query("SELECT id,subject,kind,choice,rationale,author,seq,version FROM decisions ORDER BY seq, id");
    }

    public List<Decision> asOf(long seq) {
        return query("SELECT id,subject,kind,choice,rationale,author,seq,version FROM decisions"
                + " WHERE seq<=" + seq + " ORDER BY seq, id");
    }

    private List<Decision> query(String sql) {
        List<Decision> out = new ArrayList<>();
        try (Statement s = db.conn().createStatement(); ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                out.add(row(rs));
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    private Decision row(ResultSet rs) throws Exception {
        return new Decision(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getLong(7), rs.getInt(8));
    }
}
