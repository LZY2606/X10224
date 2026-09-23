package dsc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dsc.store.Database;
import dsc.store.VersionStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StoreTest {

    @TempDir Path tmp;

    private static ObjectNode payload(String k, String v) {
        ObjectNode n = VersionStore.M.createObjectNode();
        n.put(k, v);
        return n;
    }

    private VersionStore newStore(String name) throws Exception {
        return new VersionStore(new Database(tmp.resolve(name).toString()));
    }

    private int seed(VersionStore s) throws Exception {
        String tx = s.begin();
        int v = s.stageSource(tx, "tangents", "user-1", payload("chosen", "LINEAR"));
        s.commit(tx, new String[]{"tangents", "user-1", String.valueOf(v)});
        return s.currentVersion("tangents", "user-1");
    }

    @Test
    void writeInterruption_oldVersionStaysVisible() throws Exception {
        VersionStore s = new VersionStore(new Database(tmp.resolve("crash.db").toString()));
        String tx1 = s.begin();
        int v1 = s.stageSource(tx1, "mass", "sample-1", payload("valueMg", "10.24"));
        s.commit(tx1, new String[]{"mass", "sample-1", String.valueOf(v1)});
        assertEquals(1, s.currentVersion("mass", "sample-1"));

        // simulate crash: stage v2 but never commit, journal left in BEGIN
        String tx2 = s.begin();
        s.stageSource(tx2, "mass", "sample-1", payload("valueMg", "99.99"));

        VersionStore s2 = new VersionStore(new Database(tmp.resolve("crash.db").toString()));
        assertEquals(1, s2.currentVersion("mass", "sample-1"),
                "interrupted write must leave the old version visible");
        assertEquals("10.24", s2.currentSource("mass", "sample-1").orElseThrow().get("valueMg").asText());
    }

    @Test
    void oldVersionMigration_addsBranchTable() throws Exception {
        String db = tmp.resolve("migrate.db").toString();
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE schema_meta(version INTEGER NOT NULL)");
            st.execute("INSERT INTO schema_meta VALUES (1)");
            st.execute("CREATE TABLE sources(kind TEXT NOT NULL, id TEXT NOT NULL, version INTEGER NOT NULL," +
                    "payload TEXT NOT NULL, created_event TEXT, PRIMARY KEY(kind,id,version))");
            st.execute("CREATE TABLE current_source(kind TEXT NOT NULL, id TEXT NOT NULL, version INTEGER NOT NULL, PRIMARY KEY(kind,id))");
            st.execute("CREATE TABLE derived(kind TEXT NOT NULL, id TEXT NOT NULL, version INTEGER NOT NULL," +
                    "deps TEXT NOT NULL, status TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(kind,id,version))");
            st.execute("CREATE TABLE events(seq INTEGER PRIMARY KEY AUTOINCREMENT, id TEXT NOT NULL, ts TEXT NOT NULL," +
                    "type TEXT NOT NULL, payload TEXT NOT NULL, base_version INTEGER NOT NULL DEFAULT 0)");
            st.execute("CREATE TABLE write_journal(txid TEXT PRIMARY KEY, state TEXT NOT NULL, payload TEXT NOT NULL)");
        }
        Database migrated = new Database(db); // migrates 1 -> 2
        VersionStore s = new VersionStore(migrated);
        assertDoesNotThrow(s::branches);
        try (var st = migrated.conn().createStatement();
             var rs = st.executeQuery("SELECT MAX(version) FROM schema_meta")) {
            assertTrue(rs.next());
            assertEquals(Database.SCHEMA_VERSION, rs.getInt(1));
        }
    }

    @Test
    void concurrentSave_producesResolvableConflict_bothKept() throws Exception {
        VersionStore s = newStore("conflict.db");
        int base = seed(s);
        assertEquals(1, base);

        // user A saves from base -> current moves to v2
        s.saveSource("tangents", "user-1", payload("chosen", "TANGENT"), base, "A");
        assertEquals(2, s.currentVersion("tangents", "user-1"));
        assertEquals("TANGENT", s.currentSource("tangents", "user-1").orElseThrow().get("chosen").asText());

        // user B saves from the same base -> conflict
        VersionStore.Conflict c = assertThrows(VersionStore.Conflict.class,
                () -> s.saveSource("tangents", "user-1", payload("chosen", "SIGMOIDAL"), base, "B"));
        assertNotNull(c.branchId);
        assertEquals(1, s.branches().size());
        assertEquals("OPEN", s.branches().get(0).get("status").asText());
        // B's incoming decision is staged as its own version (v3), pointer still on A's v2: nothing lost
        assertEquals(2, s.currentVersion("tangents", "user-1"));

        s.resolveBranch(c.branchId, "accept-incoming");
        assertEquals("RESOLVED", s.branches().get(0).get("status").asText());
        assertEquals("accept-incoming", s.branches().get(0).get("chosen").asText());
    }

    @Test
    void deleteAndReimport_keepsStableIdentityOrderAndProvenance() throws Exception {
        String db1 = tmp.resolve("a.db").toString();
        VersionStore s1 = new VersionStore(new Database(db1));
        s1.event("IMPORT", payload("step", "s1"), 0);
        s1.event("PARAM_REVISION", payload("step", "s2"), 0);
        s1.event("MASS_REVISION", payload("step", "s5"), 0);
        List<JsonNode> events = s1.events();
        Files.writeString(tmp.resolve("export.json"), VersionStore.M.writeValueAsString(events));

        // delete local database then import the export into a fresh one
        new java.io.File(db1).delete();
        VersionStore s2 = new VersionStore(new Database(db1));
        JsonNode read = VersionStore.M.readTree(Files.readString(tmp.resolve("export.json")));
        for (JsonNode e : read) s2.importEvent(e);
        List<JsonNode> re = s2.events();
        assertEquals(events.size(), re.size());
        for (int i = 0; i < events.size(); i++) {
            assertEquals(events.get(i).get("id").asText(), re.get(i).get("id").asText(),
                    "stable identity survives export/delete/import");
            assertEquals(events.get(i).get("type").asText(), re.get(i).get("type").asText());
        }
    }
}
