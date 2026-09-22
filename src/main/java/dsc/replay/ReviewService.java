package dsc.replay;

import dsc.domain.Derivation;
import dsc.domain.Event;
import dsc.domain.Feature;
import dsc.domain.SourceDoc;
import dsc.domain.SourceKind;
import dsc.engine.RunAnalyzer;
import dsc.store.Commit;
import dsc.store.ConcurrentEditException;
import dsc.store.Json;
import dsc.store.Store;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 应用服务。所有可见结论来自事件重放；每次提交完整成功后才移动指针。
 * 回到旧事件读取快照，恢复当时的依赖版本与结论。
 */
public final class ReviewService {

    private final Store store;
    private final GraphEngine graphEngine = new GraphEngine();

    public ReviewService(Store store) {
        this.store = store;
    }

    public long headSeq(String branch) {
        return store.headSeq(branch);
    }

    public List<Event> events(String branch) {
        return store.events(branch);
    }

    /**
     * 追加一个来源版本并重算受影响派生。
     *
     * @param judgeCode 机器可读取的判定依据代码
     */
    public synchronized long submitSource(String actor, String branch, long baseSeq,
                                          SourceDoc newVersion,
                                          String eventType, String judgeCode,
                                          String summary, Map<String, String> payload)
            throws ConcurrentEditException {
        long head = store.headSeq(branch);
        if (baseSeq != head) {
            throw new ConcurrentEditException(branch, baseSeq, head);
        }
        int version = store.nextSourceVersion(newVersion.id());
        SourceDoc versioned = new SourceDoc(newVersion.id(), newVersion.sampleId(),
                newVersion.runId(), newVersion.kind(), version, newVersion.rawText(),
                newVersion.fields(), newVersion.unitContext(), newVersion.diagnostics(),
                newVersion.rationale(), 0L);

        Event event = new Event(0L, eventType, actor, branch, head,
                newVersion.id(), newVersion.runId(), null, judgeCode,
                summary, payload, Event.STATUS_APPLIED);

        return commitWithRebuild(event, branch, List.of(versioned),
                Map.of(versioned.id(), version),
                "来源改版:" + newVersion.kind().code());
    }

    /** 初始导入：一次写入六类来源（各自独立存档）+ 首次推导。 */
    public synchronized long seedArchive(String actor, String branch,
                                         List<SourceDoc> initialSources,
                                         String summary) {
        long head = store.headSeq(branch);
        List<SourceDoc> versioned = new ArrayList<>();
        Map<String, Integer> changed = new LinkedHashMap<>();
        for (SourceDoc source : initialSources) {
            int version = store.nextSourceVersion(source.id());
            SourceDoc v = new SourceDoc(source.id(), source.sampleId(), source.runId(),
                    source.kind(), version, source.rawText(), source.fields(),
                    source.unitContext(), source.diagnostics(), source.rationale(), 0L);
            versioned.add(v);
            changed.put(v.id(), version);
        }
        Event event = new Event(0L, "archive.imported", actor, branch, head,
                null, null, null, "RAW_SEGREGATED_ARCHIVE", summary,
                Map.of("source_kinds", "instrument,mass,rate,atmosphere,calibration,tangent"),
                Event.STATUS_APPLIED);
        return commitWithRebuild(event, branch, versioned, changed, "初始归档");
    }

    /** 接受/否定基线候选或参数调整：生成裁定事件并重算（保留未选候选）。 */
    public synchronized long decide(String actor, String branch, long baseSeq,
                                    String eventType, String featureId,
                                    String judgeCode, String summary,
                                    Map<String, String> payload, String status) {
        long head = store.headSeq(branch);
        if (baseSeq != head) {
            throw new ConcurrentEditException(branch, baseSeq, head);
        }
        Event event = new Event(0L, eventType, actor, branch, head,
                featureId, payload.getOrDefault("runId", null), featureId,
                judgeCode, summary, payload, status);
        SourceDoc extraSource = null;
        Map<String, Integer> changed = Map.of();
        if ("baseline.tangent-updated".equals(eventType) && payload.containsKey("leftT")) {
            // 区间拖动产生切线来源新版本，仅影响该 feature
            String runId = payload.get("runId");
            String sid = payload.getOrDefault("sampleId", "S1");
            String id = dsc.domain.Ids.source(sid, runId, SourceKind.TANGENT_SELECTION);
            int version = store.nextSourceVersion(id);
            extraSource = new SourceDoc(id, sid, runId, SourceKind.TANGENT_SELECTION,
                    version, payload.get("rawCsv", ""),
                    Map.of("feature_id", featureId,
                            "left_t", payload.get("leftT"),
                            "right_t", payload.get("rightT"),
                            "baseline_method", payload.getOrDefault("method", "tangent")),
                    "K", List.of(), summary, 0L);
            changed = Map.of(id, version);
        }
        return commitWithRebuild(event, branch,
                extraSource == null ? List.of() : List.of(extraSource),
                changed, summary);
    }

    /** 从某事件分叉出新分支（比较两个分支后选择继续哪一版；未选候选保留）。 */
    public synchronized String branchFrom(String name, long fromSeq, String fromBranch) {
        String json = store.snapshotAt(fromSeq, fromBranch);
        if (json == null) {
            throw new IllegalArgumentException("事件 #" + fromSeq + " 没有快照，无法分叉");
        }
        long newSeq = store.headSeq("main");
        Event marker = new Event(0L, "branch.created", "reviewer", name, fromSeq,
                null, null, null, "BRANCH_POINT",
                "从 " + fromBranch + "#" + fromSeq + " 分叉，保留比较基线",
                Map.of("from_branch", fromBranch, "from_seq", String.valueOf(fromSeq)),
                Event.STATUS_APPLIED);
        // 分支事件直接落到目标分支：借用 seedArchive 的原子通道但不带新来源
        return commitRaw(marker, json, "分支点") == 0 ? name : name;
    }

    private long commitRaw(Event event, String snapshotJson, String label) {
        long head = store.headSeq(event.branch());
        Event assigned = new Event(0L, event.type(), event.actor(), event.branch(),
                event.baseSeq(), event.targetId(), event.runId(), event.featureId(),
                event.judgeCode(), event.summary(), event.payload(), event.status());
        Commit commit = new Commit(assigned, List.of(), List.of(), List.of(),
                snapshotJson, label);
        return store.appendCommit(commit, head, true);
    }

    private long commitWithRebuild(Event event, String branch,
                                   List<SourceDoc> newSources,
                                   Map<String, Integer> changed,
                                   String label) {
        long provisionalSeq = store.headSeq(branch) + 1;
        Map<String, SourceDoc> sourcesAt = store.sourcesAt(provisionalSeq - 1, branch);
        for (SourceDoc source : newSources) {
            sourcesAt.put(source.id(), withEventSeq(source, provisionalSeq));
        }
        SourceBundle bundle = SourceBundles.assemble(sourcesAt);
        List<Derivation> previous = store.derivationsAt(provisionalSeq - 1);
        GraphEngine.RebuildResult rebuilt = graphEngine.rebuild(
                sourcesAt, bundle, previous, changed, provisionalSeq, true);

        ViewAssembler.Snapshot snapshot = ViewAssembler.assemble(
                provisionalSeq, branch,
                sourcesAt.values().stream().sorted(
                                java.util.Comparator.comparing(SourceDoc::id)
                                        .thenComparingInt(SourceDoc::version))
                        .toList(),
                bundle, rebuilt.analyzed(),
                withEvent(store.events(branch), event),
                rebuilt.derivations(), rebuilt.notes());
        List<Commit.PointerMove> moves = new ArrayList<>();
        for (Derivation derivation : rebuilt.derivations()) {
            String ref = derivation.key().startsWith("run:")
                    ? derivation.key().substring(4) : "global";
            moves.add(new Commit.PointerMove("current", ref, derivation.key(),
                    derivation.version()));
        }
        Commit commit = new Commit(event,
                newSources.stream().map(s -> withEventSeq(s, provisionalSeq)).toList(),
                rebuilt.derivations(), moves,
                Json.write(snapshot.state()), label);
        return store.appendCommit(commit, provisionalSeq - 1, false);
    }

    public ViewState viewAt(long seq, String branch) {
        String json = store.snapshotAt(seq, branch);
        if (json == null) {
            throw new IllegalArgumentException("事件 #" + seq + " 无快照");
        }
        return Json.read(json, ViewState.class);
    }

    public ViewState viewHead(String branch) {
        return viewAt(store.headSeq(branch), branch);
    }

    public SourceDoc sourceVersion(String id, int version) {
        return store.allSourceVersions(id).stream()
                .filter(s -> s.version() == version).findFirst().orElse(null);
    }

    public List<SourceDoc> sourceHistory(String id) {
        return store.allSourceVersions(id);
    }

    public String exportJson() {
        return Json.write(Map.of(
                "format", "dsc-review-export/1",
                "exported_at", java.time.Instant.now().toString(),
                "events", store.allEvents(),
                "sources", allSources(),
                "derivations", allDerivations()));
    }

    /** 导入到空库：身份、顺序、来源关系原样恢复。 */
    public synchronized long importJson(String json, String branch) {
        ExportBundle bundle = Json.read(json, ExportBundle.class);
        if (store.headSeq(branch) != 0) {
            throw new IllegalStateException("目标库非空；导入仅接受空库以保证身份与顺序不被重排");
        }
        long lastSeq = 0;
        for (Event event : bundle.events()) {
            List<SourceDoc> sources = bundle.sources().stream()
                    .filter(s -> s.eventSeq() == event.seq())
                    .map(s -> s)
                    .toList();
            List<Derivation> derivations = bundle.derivations().stream()
                    .filter(d -> d.eventSeq() == event.seq())
                    .toList();
            String snapshot = store.snapshotAt(event.seq(), event.branch());
            Commit commit = new Commit(
                    new Event(event.seq(), event.type(), event.actor(), event.branch(),
                            event.baseSeq(), event.targetId(), event.runId(),
                            event.featureId(), event.judgeCode(), event.summary(),
                            event.payload(), event.status()),
                    sources, derivations, List.of(),
                    snapshot, "imported#" + event.seq());
            store.appendCommit(commit, lastSeq, true);
            lastSeq = event.seq();
        }
        for (Derivation derivation : bundle.derivations()) {
            // 指针在导入完成后统一按最终版本重建
        }
        rebuildPointersFromDerivations(branch);
        return lastSeq;
    }

    private void rebuildPointersFromDerivations(String branch) {
        Map<String, Integer> latest = new LinkedHashMap<>();
        for (Derivation derivation : allDerivations()) {
            latest.put(derivation.key(),
                    Math.max(latest.getOrDefault(derivation.key(), 0), derivation.version()));
        }
        List<Commit.PointerMove> moves = latest.entrySet().stream()
                .map(e -> new Commit.PointerMove("current",
                        e.getKey().startsWith("run:") ? e.getKey().substring(4) : "global",
                        e.getKey(), e.getValue()))
                .toList();
        Commit pointerCommit = new Commit(
                new Event(0L, "import.pointers", "importer", branch, store.headSeq(branch),
                        null, null, null, "IDENTITY_PRESERVED",
                        "导入后按原始派生版本恢复当前指针", Map.of(), Event.STATUS_APPLIED),
                List.of(), List.of(), moves, null, "导入指针");
        store.appendCommit(pointerCommit, store.headSeq(branch), true);
    }

    private List<SourceDoc> allSources() {
        List<SourceDoc> out = new ArrayList<>();
        try (var ps = store.connection().prepareStatement("SELECT * FROM sources ORDER BY id,version");
             var rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new SourceDoc(rs.getString("id"), rs.getString("sample_id"),
                        rs.getString("run_id"),
                        SourceKind.fromCode(rs.getString("kind")),
                        rs.getInt("version"), rs.getString("raw_text"),
                        Json.read(rs.getString("fields"), Map.class),
                        rs.getString("unit_context"),
                        Json.read(rs.getString("diagnostics"), List.class),
                        rs.getString("rationale"), rs.getLong("event_seq")));
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    private List<Derivation> allDerivations() {
        List<Derivation> out = new ArrayList<>();
        try (var ps = store.connection().prepareStatement(
                "SELECT * FROM derivations ORDER BY key,version");
             var rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new Derivation(rs.getString("key"), rs.getString("facet"),
                        rs.getInt("version"), rs.getString("status"), rs.getString("kind"),
                        rs.getLong("event_seq"),
                        Json.read(rs.getString("dependency_versions"), Map.class),
                        Json.read(rs.getString("diagnostics"), List.class),
                        rs.getString("why"), rs.getString("json")));
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    private static SourceDoc withEventSeq(SourceDoc source, long seq) {
        return new SourceDoc(source.id(), source.sampleId(), source.runId(),
                source.kind(), source.version(), source.rawText(), source.fields(),
                source.unitContext(), source.diagnostics(), source.rationale(), seq);
    }

    private static List<Event> withEvent(List<Event> events, Event event) {
        List<Event> all = new ArrayList<>(events);
        all.add(event);
        return all;
    }

    public record ExportBundle(
            String format,
            String exported_at,
            List<Event> events,
            List<SourceDoc> sources,
            List<Derivation> derivations) {
    }
}
