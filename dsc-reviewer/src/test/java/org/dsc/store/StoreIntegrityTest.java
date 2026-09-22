package org.dsc.store;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class StoreIntegrityTest {

    @TempDir Path tmp;

    @Test
    void failedTransactionLeavesOldVersionIntact() {
        Store store = Store.file(tmp.resolve("a.sqlite"));
        store.appendEvent("c1", "CASE_OPENED", "a", null,
                EventRecord.payload("x", "1"), null);
        Store.EntityRow v1;
        try { v1 = store.currentEntity("ent-1").orElse(null); } catch (Exception e) { throw new RuntimeException(e); }
        assertNull(v1);

        // 写实体 v1（成功）
        store.appendEvent("c1", "TRANSITION_DERIVED", "a", null,
                EventRecord.payload("k", "v1"), ev -> {
                    try {
                        store.insertEntity(ev, "c1", "ent-1", 1, "RESULT", "r",
                                EventRecord.MAPPER.createObjectNode().put("v", 1), "CURRENT");
                    } catch (Exception ex) { throw new RuntimeException(ex); }
                });

        // v2 事务中途失败：必须回滚，仍只见 v1
        RuntimeException boom = assertThrows(RuntimeException.class, () ->
            store.appendEvent("c1", "TRANSITION_DERIVED", "a", null,
                EventRecord.payload("k", "v2"), ev -> {
                    try {
                        store.insertEntity(ev, "c1", "ent-1", 2, "RESULT", "r",
                                EventRecord.MAPPER.createObjectNode().put("v", 2), "CURRENT");
                        throw new RuntimeException("simulated crash mid-write");
                    } catch (Exception ex) { throw new RuntimeException(ex); }
                }));
        assertTrue(boom.getMessage().contains("simulated crash"));
        try {
            assertEquals(1, store.currentEntity("ent-1").orElseThrow().version());
            // 失败的事件也不应存在
            assertEquals(2, store.events("c1").size(), "only CASE_OPENED + successful derive");
        } catch (Exception e) { throw new RuntimeException(e); }
        store.close();
    }

    @Test
    void concurrentEditsFromSameBasisAreRejectedAndResolvable() {
        Store store = Store.file(tmp.resolve("b.sqlite"));
        var e0 = store.appendEvent("c1", "CASE_OPENED", "a", null,
                EventRecord.payload(), null);
        String basis = e0.eventId();

        // reviewer-a 基于 basis 成功
        store.appendEvent("c1", "BASELINE_ADOPTED", "a", basis,
                EventRecord.payload("who", "a"), null);
        // reviewer-b 仍基于旧 basis -> 冲突
        Store.ConcurrencyException ex = assertThrows(Store.ConcurrencyException.class,
                () -> store.appendEvent("c1", "BASELINE_REJECTED", "b", basis,
                        EventRecord.payload("who", "b"), null));
        assertEquals(basis, ex.expected);
        assertNotEquals(basis, ex.actual);

        // 冲突可解决：b 基于最新 head 重试成功
        String newHead;
        try { newHead = store.headEventId("c1"); } catch (Exception e) { throw new RuntimeException(e); }
        assertDoesNotThrow(() -> store.appendEvent("c1", "BASELINE_REJECTED", "b", newHead,
                EventRecord.payload("who", "b"), null));
        store.close();
    }

    @Test
    void oldV1DatabaseMigratesToV2AndKeepsData() throws Exception {
        Path db = tmp.resolve("old.sqlite");
        // 手工建一个 v1 老库（无 label 列）
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db);
             Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            st.executeUpdate("CREATE TABLE events(event_id TEXT PRIMARY KEY, case_id TEXT NOT NULL," +
                    " parent_id TEXT, seq INTEGER NOT NULL, type TEXT NOT NULL, actor TEXT NOT NULL," +
                    " at TEXT NOT NULL, basis_version TEXT, payload TEXT NOT NULL, UNIQUE(case_id,seq))");
            st.executeUpdate("CREATE TABLE sources(source_id TEXT NOT NULL, version INTEGER NOT NULL," +
                    " case_id TEXT NOT NULL, kind TEXT NOT NULL, segment_id TEXT, raw_text TEXT NOT NULL," +
                    " unit TEXT, active INTEGER NOT NULL, superseded_by TEXT, created_event TEXT NOT NULL," +
                    " PRIMARY KEY(source_id,version))");
            st.executeUpdate("CREATE TABLE entities(entity_id TEXT NOT NULL, version INTEGER NOT NULL," +
                    " case_id TEXT NOT NULL, kind TEXT NOT NULL, json TEXT NOT NULL, status TEXT NOT NULL," +
                    " created_event TEXT NOT NULL, PRIMARY KEY(entity_id,version))");
            st.executeUpdate("CREATE TABLE entity_pointers(case_id TEXT NOT NULL, entity_id TEXT NOT NULL," +
                    " current_version INTEGER NOT NULL, updated_event TEXT NOT NULL," +
                    " PRIMARY KEY(case_id,entity_id))");
            st.executeUpdate("CREATE TABLE deps(from_entity TEXT, from_version INTEGER," +
                    " to_entity TEXT, to_version INTEGER, event_id TEXT)");
            st.executeUpdate("INSERT INTO events VALUES('ev-old-1','old',NULL,1,'CASE_OPENED','a'," +
                    "'2026-01-01T00:00:00Z',NULL,'{}')");
            st.executeUpdate("INSERT INTO entities VALUES('ent-old',1,'old','RESULT','{}','CURRENT','ev-old-1')");
            st.executeUpdate("PRAGMA user_version=1");
        }
        // 打开即迁移
        Store reopened = Store.file(db);
        var row = reopened.currentEntity("ent-old").orElseThrow();
        assertEquals(1, row.version());
        assertEquals("RESULT", row.kind());
        assertEquals("", row.label());
        assertEquals(1, reopened.events("old").size());
        reopened.close();
    }

    @Test
    void exportDeleteImportPreservesIdentityOrderAndLinks() throws Exception {
        Store s1 = Store.file(tmp.resolve("x.sqlite"));
        var e1 = s1.appendEvent("c1", "CASE_OPENED", "a", null, EventRecord.payload(), null);
        s1.appendEvent("c1", "TRANSITION_DERIVED", "a", null, EventRecord.payload(), ev -> {
            try {
                s1.insertEntity(ev, "c1", "res-stable-1", 1, "RESULT", "r",
                        EventRecord.MAPPER.createObjectNode().put("dh", 12.3), "CURRENT");
                s1.insertSource(ev, "c1", "src-mass", 1, "MASS", null, "12.4 mg", "mg");
                s1.addDep(ev, "res-stable-1", 1, "src-mass", 1);
            } catch (Exception ex) { throw new RuntimeException(ex); }
        });
        String json = Archive.toJson(Archive.exportAll(s1));
        s1.close();

        // 删除本地数据库
        Path dbFile = tmp.resolve("x.sqlite");
        Files.deleteIfExists(dbFile);
        assertFalse(Files.exists(dbFile));

        Store s2 = Store.file(dbFile);
        Archive.importAll(s2, Archive.parseJson(json));
        var row = s2.currentEntity("res-stable-1").orElseThrow();
        assertEquals(12.3, row.json().get("dh").asDouble(), 1e-9);
        var evs = s2.events("c1");
        assertEquals(2, evs.size());
        assertEquals("CASE_OPENED", evs.get(0).type());
        assertEquals("TRANSITION_DERIVED", evs.get(1).type());
        assertEquals("ev-c1-1", evs.get(1).parentId(), "parent link preserved after re-import");
        assertTrue(s2.dependentsOf("src-mass", 1).contains("res-stable-1"));
        s2.close();
    }
}
