package dsc.store;

import dsc.model.Source;
import dsc.model.SourceKind;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Sources {

    private Sources() {
    }

    static void insert(Connection c, Source s, ObjectMapperHolder h) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO sources(source_id,project_id,kind,filename,raw_payload,"
                        + "raw_fields_json,revises_source_id,producing_event_id,ingest_seq,"
                        + "branch_root,actor,created_at_ms) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, s.sourceId());
            ps.setString(2, s.projectId());
            ps.setString(3, s.kind().name());
            ps.setString(4, s.filename());
            ps.setString(5, s.rawPayload());
            ps.setString(6, h.mapper.writeValueAsString(s.rawFields()));
            ps.setString(7, s.revisesSourceId());
            ps.setString(8, s.producingEventId());
            ps.setLong(9, s.ingestSeq());
            ps.setString(10, s.branchRoot());
            ps.setString(11, s.actor());
            ps.setLong(12, s.createdAtMs());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw e;
        } catch (Exception e) {
            throw new SQLException(e);
        }
    }

    static List<Source> allForProject(Connection c, String projectId, ObjectMapperHolder h)
            throws SQLException {
        List<Source> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT source_id,project_id,kind,filename,raw_payload,raw_fields_json,"
                        + "revises_source_id,producing_event_id,ingest_seq,branch_root,actor,created_at_ms "
                        + "FROM sources WHERE project_id=? ORDER BY ingest_seq, source_id")) {
            ps.setString(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(read(rs, h));
                }
            }
        }
        return out;
    }

    static Source byId(Connection c, String sourceId, ObjectMapperHolder h) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT source_id,project_id,kind,filename,raw_payload,raw_fields_json,"
                        + "revises_source_id,producing_event_id,ingest_seq,branch_root,actor,created_at_ms "
                        + "FROM sources WHERE source_id=?")) {
            ps.setString(1, sourceId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? read(rs, h) : null;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Source read(ResultSet rs, ObjectMapperHolder h) throws SQLException {
        Map<String, String> fields = new LinkedHashMap<>();
        try {
            fields = h.mapper.readValue(rs.getString(6), LinkedHashMap.class);
        } catch (Exception ignored) {
            // legacy rows default to empty
        }
        return new Source(
                rs.getString(1),
                rs.getString(2),
                SourceKind.valueOf(rs.getString(3)),
                rs.getString(4),
                rs.getString(5),
                fields,
                rs.getString(7),
                rs.getString(8),
                rs.getLong(9),
                rs.getString(10),
                rs.getString(11),
                rs.getLong(12));
    }
}
