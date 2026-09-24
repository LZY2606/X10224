package dsc.store;

import dsc.domain.Derivation;
import dsc.domain.Diagnostic;
import dsc.domain.Enums;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DerivationDao {
    private final Repository repo;
    public DerivationDao(Repository repo) { this.repo = repo; }
    private Connection cx() { return repo.cx(); }

    public void insert(Derivation d) {
        try (PreparedStatement ps = cx().prepareStatement(
                "INSERT INTO derivation(id,version,sample_id,branch_id,kind,segment_id,interval_id,"
              + "is_current,status,method,candidate_status,basis_json,message,created_by,created_at)"
              + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, d.id); ps.setInt(2, d.version); ps.setString(3, d.sampleId);
            ps.setString(4, d.branchId); ps.setString(5, d.kind.name());
            ps.setString(6, d.segmentId); ps.setString(7, d.intervalId);
            ps.setInt(8, d.current ? 1 : 0); ps.setString(9, d.status); ps.setString(10, d.method);
            ps.setString(11, d.candidateStatus.name()); ps.setString(12, d.basisJson);
            ps.setString(13, d.message); ps.setString(14, d.createdBy); ps.setLong(15, d.createdAt);
            ps.executeUpdate();
            insertDeps(d);
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public void insertDeps(Derivation d) throws SQLException {
        try (PreparedStatement ps = cx().prepareStatement(
                "INSERT INTO derivation_dep(branch_id,derivation_id,derivation_version,ref_type,ref_id,ref_version)"
              + " VALUES(?,?,?,?,?,?)")) {
            for (Derivation.DepEdge e : d.deps) {
                ps.setString(1, d.branchId); ps.setString(2, d.id); ps.setInt(3, d.version);
                ps.setString(4, e.refType); ps.setString(5, e.refId); ps.setInt(6, e.refVersion);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public void clearCurrent(String branchId, String id) {
        try (PreparedStatement ps = cx().prepareStatement(
                "UPDATE derivation SET is_current=0 WHERE branch_id=? AND id=?")) {
            ps.setString(1, branchId); ps.setString(2, id);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public int nextVersion(String branchId, String id) {
        try (PreparedStatement ps = cx().prepareStatement(
                "SELECT coalesce(max(version),0)+1 FROM derivation WHERE branch_id=? AND id=?")) {
            ps.setString(1, branchId); ps.setString(2, id);
            ResultSet rs = ps.executeQuery(); rs.next();
            return rs.getInt(1);
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public Derivation current(String branchId, String id) {
        try (PreparedStatement ps = cx().prepareStatement(
                "SELECT * FROM derivation WHERE branch_id=? AND id=? ORDER BY version DESC")) {
            ps.setString(1, branchId); ps.setString(2, id);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) if (rs.getInt("is_current") == 1) return map(rs, deps(branchId, id, rs.getInt("version")));
            return null;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public List<Derivation> currentByInterval(String branchId, String intervalId) {
        List<Derivation> out = new ArrayList<>();
        try (PreparedStatement ps = cx().prepareStatement(
                "SELECT * FROM derivation WHERE branch_id=? AND interval_id=? AND is_current=1 ORDER BY method,version")) {
            ps.setString(1, branchId); ps.setString(2, intervalId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) out.add(map(rs, deps(branchId, rs.getString("id"), rs.getInt("version"))));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return out;
    }

    public List<Derivation> currentByKind(String sampleId, String branchId, Enums.DerivKind kind) {
        List<Derivation> out = new ArrayList<>();
        try (PreparedStatement ps = cx().prepareStatement(
                "SELECT * FROM derivation WHERE sample_id=? AND branch_id=? AND kind=? AND is_current=1 ORDER BY created_at")) {
            ps.setString(1, sampleId); ps.setString(2, branchId); ps.setString(3, kind.name());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) out.add(map(rs, deps(branchId, rs.getString("id"), rs.getInt("version"))));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return out;
    }

    public List<Derivation.DepEdge> deps(String branchId, String id, int version) {
        List<Derivation.DepEdge> out = new ArrayList<>();
        try (PreparedStatement ps = cx().prepareStatement(
                "SELECT ref_type,ref_id,ref_version FROM derivation_dep "
              + "WHERE branch_id=? AND derivation_id=? AND derivation_version=?")) {
            ps.setString(1, branchId); ps.setString(2, id); ps.setInt(3, version);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) out.add(new Derivation.DepEdge(
                    rs.getString("ref_type"), rs.getString("ref_id"), rs.getInt("ref_version")));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return out;
    }

    /** Reverse dependency edges for stale propagation. */
    public List<String> dependentsOf(String branchId, String refType, String refId) {
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = cx().prepareStatement(
                "SELECT DISTINCT derivation_id FROM derivation_dep "
              + "WHERE branch_id=? AND ref_type=? AND ref_id=? AND derivation_id IN "
              + "(SELECT id FROM derivation WHERE branch_id=? AND is_current=1)")) {
            ps.setString(1, branchId); ps.setString(2, refType); ps.setString(3, refId); ps.setString(4, branchId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) out.add(rs.getString(1));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return out;
    }

    public void markStale(String branchId, String id) {
        try (PreparedStatement ps = cx().prepareStatement(
                "UPDATE derivation SET status='STALE' WHERE branch_id=? AND id=? AND is_current=1")) {
            ps.setString(1, branchId); ps.setString(2, id);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public void setCandidateStatus(String branchId, String id, Enums.CandidateStatus st) {
        try (PreparedStatement ps = cx().prepareStatement(
                "UPDATE derivation SET candidate_status=? WHERE branch_id=? AND id=? AND is_current=1")) {
            ps.setString(1, st.name()); ps.setString(2, branchId); ps.setString(3, id);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    private Derivation map(ResultSet rs, List<Derivation.DepEdge> deps) throws SQLException {
        Derivation d = new Derivation();
        d.id = rs.getString("id"); d.version = rs.getInt("version");
        d.sampleId = rs.getString("sample_id"); d.branchId = rs.getString("branch_id");
        d.kind = Enums.DerivKind.valueOf(rs.getString("kind"));
        d.segmentId = rs.getString("segment_id"); d.intervalId = rs.getString("interval_id");
        d.current = rs.getInt("is_current") == 1; d.status = rs.getString("status");
        d.method = rs.getString("method");
        d.candidateStatus = Enums.CandidateStatus.valueOf(rs.getString("candidate_status"));
        d.basisJson = rs.getString("basis_json"); d.message = rs.getString("message");
        d.createdBy = rs.getString("created_by"); d.createdAt = rs.getLong("created_at");
        d.deps = deps;
        return d;
    }
}
