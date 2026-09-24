package com.dsc.model;

import com.dsc.Json;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/** 领域记录。所有实体带稳定身份与版本；created_seq 为全局事件序号，用于 as-of 回放。 */
public final class Records {

    private Records() {}

    /** 原始来源：仪器输出 / 样品质量 / 扫描速率 / 气氛 / 温度标定 / 用户切线。raw 保留原始表示。 */
    public record Source(String id, String sampleId, String kind, int version, String raw,
                         String unit, String meta, long createdSeq) {
        public JsonNode metaJson() { return meta == null || meta.isEmpty() ? Json.obj() : Json.read(meta); }
    }

    /** 归一化后的曲线段（派生层）。 */
    public record Segment(String id, String sourceId, int sourceVersion, String direction,
                          int cycleIndex, int pointCount, double tStart, double tEnd,
                          double dtMin, double dtMax, double dtMean, double dtCv) {}

    /** 用户区间（窗口），版本化；拖动区间产生新版本。 */
    public record Window(String id, String sampleId, String segmentId, double t0, double t1,
                         String label, int version, long createdSeq, String status) {}

    /** 基线候选；status: candidate / accepted / not_selected / superseded。 */
    public record Baseline(String id, String sampleId, String windowId, String kind,
                           String params, String status, String originEvent, long createdSeq) {
        public JsonNode paramsJson() { return Json.read(params); }
    }

    /** 派生结果；deps 为 [{type,id,version}]，status: current / stale / empty / error。 */
    public record Derived(String id, String sampleId, String kind, int version, String deps,
                          String rule, String status, String payload, String diagnostics,
                          String staleReason, long createdSeq) {
        public List<Dep> depList() {
            List<Dep> out = new ArrayList<>();
            for (JsonNode n : Json.read(deps)) {
                out.add(new Dep(n.get("type").asText(), n.get("id").asText(), n.get("version").asInt()));
            }
            return out;
        }
        public JsonNode payloadJson() { return Json.read(payload); }
        public JsonNode diagnosticsJson() { return Json.read(diagnostics); }
    }

    public record Dep(String type, String id, int version) {}

    /** 机器可读事件；seq 单调递增，是回放与排序的唯一基准。 */
    public record Event(String id, long seq, String sampleId, String type, String payload, String actor) {
        public JsonNode payloadJson() { return Json.read(payload); }
    }

    public record Conflict(String id, String sampleId, long baseVersion, String branchA, String branchB,
                           String status, String resolution, long createdSeq) {}

    public record Branch(String id, String sampleId, long baseVersion, String name,
                         String status, String commitJson, long createdSeq) {}

    public record Head(String sampleId, long version, long seq) {}
}
