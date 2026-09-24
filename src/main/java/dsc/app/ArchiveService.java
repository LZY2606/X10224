package dsc.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dsc.domain.Derivation;
import dsc.domain.Event;
import dsc.engine.State;
import dsc.store.Store;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Self-contained counter-example archive. It serializes the entire event log
 * (facts, decisions, generated DERIVED/JUDGMENT events) and every derivation
 * version, including rejected and unadopted candidates. Re-import into a fresh
 * local database preserves stable ids, event order and source relationships.
 */
public final class ArchiveService {
    public static final String FORMAT = "dsc-archive";
    public static final int FORMAT_VERSION = 1;

    private ArchiveService() {}

    public record Archive(String json, long crc32) {}

    public static Archive exportTo(Store store, Path file) {
        ObjectNode root = State.M.createObjectNode();
        root.put("format", FORMAT);
        root.put("formatVersion", FORMAT_VERSION);
        root.put("ruleVersion", dsc.engine.Engine.RULE_VERSION);
        root.put("exportedAt", java.time.Instant.now().toString());

        ArrayNode branches = root.putArray("branches");
        for (Store.BranchInfo b : store.branches()) {
            ObjectNode bn = branches.addObject();
            bn.put("name", b.name());
            bn.put("headSeq", b.headSeq());
            bn.put("headEventId", b.headEventId());
            bn.put("baseSeq", b.baseSeq());
            bn.put("parentBranch", b.parent());
        }
        ArrayNode events = root.putArray("events");
        for (Event e : store.allEvents()) {
            ObjectNode en = events.addObject();
            en.put("seq", e.seq());
            en.put("branch", e.branch());
            en.put("id", e.id());
            en.put("type", e.type());
            en.put("parentId", e.parentId());
            en.put("baseSeq", e.baseSeq());
            en.put("actor", e.actor());
            en.put("createdAt", e.createdAt());
            en.put("targetId", e.targetId());
            en.set("payload", e.payload());
        }
        ArrayNode ders = root.putArray("derivations");
        // pull unique derivation ids from the events table; fetch rows individually
        java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
        for (Event e : store.allEvents()) {
            if (Event.DERIVED.equals(e.type())) ids.add(e.payload().path("derivationId").asText());
        }
        for (String id : ids) {
            Derivation d = store.derivation(id);
            if (d == null) continue;
            ObjectNode dn = ders.addObject();
            dn.put("id", d.id());
            dn.put("key", d.key());
            dn.put("kind", d.kind());
            dn.put("branch", d.branch());
            dn.put("inputEventSeq", d.inputEventSeq());
            dn.put("ruleVersion", d.ruleVersion());
            dn.put("deps", d.deps());
            dn.set("output", d.output());
            dn.set("diagnostics", d.diagnostics());
            dn.put("status", d.status());
        }
        String json = root.toPrettyString();
        CRC32 crc = new CRC32();
        crc.update(json.getBytes(StandardCharsets.UTF_8));
        try {
            Files.writeString(file, json, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return new Archive(json, crc.getValue());
    }

    /**
     * Import into the given (typically fresh) store using direct inserts,
     * preserving ids, order and provenance. Returns the number of events.
     */
    public static int importFrom(Store target, Path file) {
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            return importJson(target, json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static int importJson(Store target, String json) {
        try {
            JsonNode root = State.M.readTree(json);
            if (!FORMAT.equals(root.path("format").asText())) {
                throw new IllegalArgumentException("not a " + FORMAT + " file");
            }
            // branches
            for (JsonNode b : root.path("branches")) {
                target.ensureBranch(b.path("name").asText());
            }
            // bulk-insert preserves ids and (branch, seq) order verbatim
            List<Store.RawEvent> evs = new ArrayList<>();
            for (JsonNode e : root.path("events")) {
                ObjectNode p = (ObjectNode) e.path("payload");
                Store.PendingEvent pe = new Store.PendingEvent(e.path("type").asText(),
                        e.path("id").asText(),
                        e.path("parentId").isNull() ? null : e.path("parentId").asText(),
                        e.path("baseSeq").asLong(-1), e.path("actor").asText(),
                        e.path("targetId").isNull() ? null : e.path("targetId").asText(), p);
                evs.add(new Store.RawEvent(e.path("seq").asLong(), e.path("branch").asText(), pe));
            }
            List<Derivation> ders = new ArrayList<>();
            for (JsonNode d : root.path("derivations")) {
                ders.add(new Derivation(d.path("id").asText(), d.path("key").asText(),
                        d.path("kind").asText(), d.path("inputEventSeq").asLong(),
                        d.path("branch").asText(), d.path("ruleVersion").asText(),
                        d.path("deps").asText("[]"), d.path("output"),
                        d.path("diagnostics"), d.path("status").asText()));
            }
            target.bulkLoadOrdered(root.path("branches"), evs, ders);
            return evs.size();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
