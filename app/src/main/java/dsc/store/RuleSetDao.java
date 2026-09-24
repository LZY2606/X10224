package dsc.store;

import dsc.domain.RuleSet;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class RuleSetDao {
    private final Repository repo;
    public RuleSetDao(Repository repo) { this.repo = repo; }
    private Connection cx() { return repo.cx(); }

    public void insert(RuleSet r) {
        try (PreparedStatement ps = cx().prepareStatement(
                "INSERT INTO ruleset(version,is_current,smoothing_window_c,peak_prominence_wg,"
              + "glass_step_min_wg,sign_policy,created_by,created_at,note) VALUES(?,?,?,?,?,?,?,?,?)")) {
            ps.setInt(1, r.version); ps.setInt(2, r.current ? 1 : 0);
            ps.setDouble(3, r.smoothingWindowC); ps.setDouble(4, r.peakProminenceWg);
            ps.setDouble(5, r.glassStepMinWg); ps.setString(6, r.signPolicy);
            ps.setString(7, r.createdBy); ps.setLong(8, r.createdAt); ps.setString(9, r.note);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public void clearCurrent() {
        try (Statement st = cx().createStatement()) {
            st.execute("UPDATE ruleset SET is_current=0");
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public RuleSet current() {
        try (Statement st = cx().createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM ruleset WHERE is_current=1 ORDER BY version DESC LIMIT 1")) {
            return rs.next() ? map(rs) : null;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public List<RuleSet> all() {
        List<RuleSet> out = new ArrayList<>();
        try (Statement st = cx().createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM ruleset ORDER BY version")) {
            while (rs.next()) out.add(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return out;
    }

    public int nextVersion() {
        try (Statement st = cx().createStatement();
             ResultSet rs = st.executeQuery("SELECT coalesce(max(version),0)+1 FROM ruleset")) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    private RuleSet map(ResultSet rs) throws SQLException {
        RuleSet r = new RuleSet();
        r.version = rs.getInt("version"); r.current = rs.getInt("is_current") == 1;
        r.smoothingWindowC = rs.getDouble("smoothing_window_c");
        r.peakProminenceWg = rs.getDouble("peak_prominence_wg");
        r.glassStepMinWg = rs.getDouble("glass_step_min_wg");
        r.signPolicy = rs.getString("sign_policy");
        r.createdBy = rs.getString("created_by"); r.createdAt = rs.getLong("created_at");
        r.note = rs.getString("note");
        return r;
    }
}
