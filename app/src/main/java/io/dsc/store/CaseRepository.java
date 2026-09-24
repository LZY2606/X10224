package io.dsc.store;

import io.dsc.model.Branch;
import io.dsc.model.CaseMetadata;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Case and branch metadata, stored alongside the append-only log. */
public final class CaseRepository {
    private final String url;

    public CaseRepository(String dbPath) {
        this.url = "jdbc:sqlite:" + dbPath;
    }

    private Connection connect() {
        try {
            Connection c = DriverManager.getConnection(url);
            c.setAutoCommit(false);
            return c;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void createCase(CaseMetadata meta, String mainBranchId) {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT OR REPLACE INTO cases(case_id, title, material, created_at, "
                             + "rule_version, tags, current_branch) VALUES(?,?,?,?,?,?,?)")) {
            ps.setString(1, meta.caseId());
            ps.setString(2, meta.title());
            ps.setString(3, meta.material());
            ps.setString(4, meta.createdAt());
            ps.setString(5, meta.ruleVersion());
            ps.setString(6, String.join(",", meta.tags()));
            ps.setString(7, mainBranchId);
            ps.executeUpdate();
            c.commit();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void updateCurrentBranch(String caseId, String branchId) {
        mutate("UPDATE cases SET current_branch='" + branchId + "' WHERE case_id='" + caseId + "'");
    }

    private void mutate(String sql) {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute(sql);
            c.commit();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public CaseMetadata getCase(String caseId) {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT case_id, title, material, created_at, rule_version, tags FROM cases "
                             + "WHERE case_id=?")) {
            ps.setString(1, caseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) { c.commit(); return null; }
                String tags = rs.getString(6);
                List<String> tagList = tags == null || tags.isBlank()
                        ? List.of() : List.of(tags.split(","));
                CaseMetadata meta = new CaseMetadata(rs.getString(1), rs.getString(2),
                        rs.getString(3), rs.getString(4), rs.getString(5), tagList);
                c.commit();
                return meta;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<CaseMetadata> listCases() {
        try (Connection c = connect();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT case_id, title, material, created_at, rule_version, tags FROM cases")) {
            List<CaseMetadata> out = new ArrayList<>();
            while (rs.next()) {
                String tags = rs.getString(6);
                out.add(new CaseMetadata(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5),
                        tags == null || tags.isBlank() ? List.of() : List.of(tags.split(","))));
            }
            c.commit();
            return out;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteCase(String caseId) {
        try (Connection c = connect()) {
            for (String sql : List.of(
                    "DELETE FROM events WHERE case_id='" + caseId + "'",
                    "DELETE FROM raw_bodies WHERE case_id='" + caseId + "'",
                    "DELETE FROM cases WHERE case_id='" + caseId + "'",
                    "DELETE FROM archive_log WHERE case_id='" + caseId + "'")) {
                try (Statement st = c.createStatement()) { st.execute(sql); }
            }
            c.commit();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void upsertBranch(Branch branch) {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT OR REPLACE INTO branches(branch_id, case_id, name, parent_branch_id, "
                             + "fork_after_seq, resolution, resolution_event_id, tags) "
                             + "VALUES(?,?,?,?,?,?,?,?)")) {
            ps.setString(1, branch.branchId());
            ps.setString(2, branch.caseId());
            ps.setString(3, branch.name());
            ps.setString(4, branch.parentBranchId());
            if (branch.forkAfterSeq() == null) ps.setNull(5, java.sql.Types.INTEGER);
            else ps.setLong(5, branch.forkAfterSeq());
            ps.setString(6, branch.resolution());
            ps.setString(7, branch.resolutionEventId());
            ps.setString(8, String.join(",", branch.tags()));
            ps.executeUpdate();
            c.commit();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Branch> listBranches(String caseId) {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT branch_id, case_id, name, parent_branch_id, fork_after_seq, "
                             + "resolution, resolution_event_id, tags FROM branches WHERE case_id=?")) {
            ps.setString(1, caseId);
            List<Branch> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Long fork = rs.getLong(5);
                    if (rs.wasNull()) fork = null;
                    String tags = rs.getString(8);
                    out.add(new Branch(rs.getString(1), rs.getString(2), rs.getString(3),
                            rs.getString(4), fork, rs.getString(6), rs.getString(7),
                            tags == null || tags.isBlank() ? List.of() : List.of(tags.split(","))));
                }
            }
            c.commit();
            return out;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
