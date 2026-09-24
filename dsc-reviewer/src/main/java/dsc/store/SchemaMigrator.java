package dsc.store;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Old-version migration, exercised by the automated tests against a
 * hand-built v1 database file. v1 lacked the decisions ledger; v2 adds it
 * without touching existing rows.
 */
final class SchemaMigrator {

    private SchemaMigrator() {
    }

    static void migrate(Connection c) throws SQLException {
        boolean committedHere = c.getAutoCommit();
        int from = readVersion(c);
        if (from == 0) {
            Database.createFresh(c);
            if (committedHere) c.commit();
            return;
        }
        try (Statement st = c.createStatement()) {
            if (from < 2) {
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS decisions (
                        candidate_id TEXT NOT NULL,
                        event_seq INTEGER NOT NULL REFERENCES events(seq),
                        state TEXT NOT NULL CHECK (state IN ('ACCEPTED','REJECTED','PENDING','CONFLICT')),
                        reviewer TEXT NOT NULL,
                        note TEXT,
                        PRIMARY KEY (candidate_id, event_seq)
                    ) WITHOUT ROWID""");
            }
            st.executeUpdate("INSERT OR REPLACE INTO meta(key,value) VALUES('schema_version','"
                    + Database.SCHEMA_VERSION + "')");
        }
        if (committedHere) c.commit();
    }

    private static int readVersion(Connection c) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='table' AND name='meta'")) {
            if (!rs.next()) return 0;
        }
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT value FROM meta WHERE key='schema_version'")) {
            return rs.next() ? Integer.parseInt(rs.getString(1)) : 1;
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
