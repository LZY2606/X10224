package dsc.store;

import dsc.domain.Diagnostic;
import dsc.domain.Enums;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DiagnosticDao {
    private final Repository repo;
    public DiagnosticDao(Repository repo) { this.repo = repo; }
    private Connection cx() { return repo.cx(); }

    public void insert(Diagnostic d) {
        try (PreparedStatement ps = cx().prepareStatement(
                "INSERT INTO diagnostic(id,derivation_id,derivation_version,branch_id,segment_id,interval_id,"
              + "rule,severity,verdict,message,evidence_json,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, d.id); ps.setString(2, d.derivationId); ps.setInt(3, d.derivationVersion);
            ps.setString(4, d.branchId); ps.setString(5, d.segmentId); ps.setString(6, d.intervalId);
            ps.setString(7, d.rule.name()); ps.setString(8, d.severity.name()); ps.setString(9, d.verdict.name());
            ps.setString(10, d.message); ps.setString(11, d.evidenceJson); ps.setLong(12, d.createdAt);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public List<Diagnostic> forBranch(String branchId) {
        List<Diagnostic> out = new ArrayList<>();
        try (PreparedStatement ps = cx().prepareStatement(
                "SELECT * FROM diagnostic WHERE branch_id=? ORDER BY created_at, rule")) {
            ps.setString(1, branchId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) out.add(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return out;
    }

    public void deleteForDerivation(String branchId, String derivationId, int version) {
        try (PreparedStatement ps = cx().prepareStatement(
                "DELETE FROM diagnostic WHERE branch_id=? AND derivation_id=? AND derivation_version=?")) {
            ps.setString(1, branchId); ps.setString(2, derivationId); ps.setInt(3, version);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    private Diagnostic map(ResultSet rs) throws SQLException {
        Diagnostic d = new Diagnostic();
        d.id = rs.getString("id"); d.derivationId = rs.getString("derivation_id");
        d.derivationVersion = rs.getInt("derivation_version"); d.branchId = rs.getString("branch_id");
        d.segmentId = rs.getString("segment_id"); d.intervalId = rs.getString("interval_id");
        d.rule = Enums.PolicyRule.valueOf(rs.getString("rule"));
        d.severity = Enums.Severity.valueOf(rs.getString("severity"));
        d.verdict = Enums.Verdict.valueOf(rs.getString("verdict"));
        d.message = rs.getString("message"); d.evidenceJson = rs.getString("evidence_json");
        d.createdAt = rs.getLong("created_at");
        return d;
    }
}
