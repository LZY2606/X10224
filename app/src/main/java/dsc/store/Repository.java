package dsc.store;

import dsc.domain.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Hand-written JDBC repository. Writes are issued by services inside a Database.tx block. */
public class Repository {

    private final Database db;
    public Repository(Database db) { this.db = db; }
    public Connection cx() { return db.raw(); }

    // ---- samples ----
    public void insertSample(Sample s) {
        try (PreparedStatement ps = cx().prepareStatement(
                "INSERT INTO sample(id,code,material,note,created_at,revision) VALUES(?,?,?,?,?,?)")) {
            ps.setString(1, s.id); ps.setString(2, s.code); ps.setString(3, s.material);
            ps.setString(4, s.note); ps.setLong(5, s.createdAt); ps.setLong(6, s.revision);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public Sample getSample(String id) {
        try (PreparedStatement ps = cx().prepareStatement("SELECT * FROM sample WHERE id=?")) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? mapSample(rs) : null;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public List<Sample> listSamples() {
        List<Sample> out = new ArrayList<>();
        try (Statement st = cx().createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM sample ORDER BY created_at")) {
            while (rs.next()) out.add(mapSample(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return out;
    }

    public void bumpSample(String id) {
        try (PreparedStatement ps = cx().prepareStatement(
                "UPDATE sample SET revision=revision+1 WHERE id=?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    private Sample mapSample(ResultSet rs) throws SQLException {
        Sample s = new Sample();
        s.id = rs.getString("id"); s.code = rs.getString("code");
        s.material = rs.getString("material"); s.note = rs.getString("note");
        s.createdAt = rs.getLong("created_at"); s.revision = rs.getLong("revision");
        return s;
    }

    // ---- branches ----
    public void insertBranch(Branch b) {
        try (PreparedStatement ps = cx().prepareStatement(
                "INSERT INTO branch(id,sample_id,name,parent_branch_id,fork_from_event_id,reason,selected,created_at)"
              + " VALUES(?,?,?,?,?,?,?,?)")) {
            ps.setString(1, b.id); ps.setString(2, b.sampleId); ps.setString(3, b.name);
            ps.setString(4, b.parentBranchId); ps.setString(5, b.forkFromEventId);
            ps.setString(6, b.reason); ps.setInt(7, b.selected ? 1 : 0); ps.setLong(8, b.createdAt);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public List<Branch> listBranches(String sampleId) {
        List<Branch> out = new ArrayList<>();
        try (PreparedStatement ps = cx().prepareStatement(
                "SELECT * FROM branch WHERE sample_id=? ORDER BY created_at")) {
            ps.setString(1, sampleId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) out.add(mapBranch(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return out;
    }

    public Branch getBranch(String id) {
        try (PreparedStatement ps = cx().prepareStatement("SELECT * FROM branch WHERE id=?")) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? mapBranch(rs) : null;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public void selectBranch(String sampleId, String branchId) {
        try (PreparedStatement ps = cx().prepareStatement(
                "UPDATE branch SET selected=(id=?) WHERE sample_id=?")) {
            ps.setString(1, branchId); ps.setString(2, sampleId);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    private Branch mapBranch(ResultSet rs) throws SQLException {
        Branch b = new Branch();
        b.id = rs.getString("id"); b.sampleId = rs.getString("sample_id");
        b.name = rs.getString("name"); b.parentBranchId = rs.getString("parent_branch_id");
        b.forkFromEventId = rs.getString("fork_from_event_id"); b.reason = rs.getString("reason");
        b.selected = rs.getInt("selected") == 1; b.createdAt = rs.getLong("created_at");
        return b;
    }
}
