package dsc.replay;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 依赖图：节点是稳定 key（run、feature、metric），边是所依赖的来源版本。
 * 重算时只重放受影响节点，并为每个节点说明为何仍有效或为何需要重算。
 */
public final class DependencyGraph {

    private final Map<String, Node> nodes = new LinkedHashMap<>();

    public record Node(
            String key,
            String facet,
            String kind,
            Map<String, Integer> dependsOn,
            Map<String, Integer> computedFrom
    ) {
    }

    public record Invalidation(String key, String reason, List<String> changedDeps) {
    }

    public void upsert(String key, String facet, String kind,
                       Map<String, Integer> dependsOn,
                       Map<String, Integer> computedFrom) {
        nodes.put(key, new Node(key, facet, kind, dependsOn, computedFrom));
    }

    /**
     * @param changedSourceVersions 来源 id -> 最新版本
     * @return 受影响、必须重算的节点，附原因
     */
    public Map<String, Invalidation> affectedBy(Map<String, Integer> changedSourceVersions) {
        Map<String, Invalidation> out = new LinkedHashMap<>();
        for (Node node : nodes.values()) {
            List<String> changed = node.computedFrom.entrySet().stream()
                    .filter(e -> {
                        Integer now = changedSourceVersions.get(e.getKey());
                        return now != null && !now.equals(e.getValue());
                    })
                    .map(Map.Entry::getKey)
                    .toList();
            if (!changed.isEmpty()) {
                out.put(node.key(), new Invalidation(node.key(),
                        explain(node, changed), changed));
            }
        }
        return out;
    }

    public boolean unchanged(String key, Map<String, Integer> currentSourceVersions) {
        Node node = nodes.get(key);
        if (node == null) {
            return false;
        }
        return node.computedFrom.entrySet().stream()
                .allMatch(e -> currentSourceVersions.getOrDefault(e.getKey(), 0)
                        .equals(e.getValue()));
    }

    public String explainValid(String key) {
        Node node = nodes.get(key);
        if (node == null) {
            return "尚未计算";
        }
        return "依赖版本未变：" + node.computedFrom();
    }

    private String explain(Node node, List<String> changed) {
        return switch (node.facet) {
            case "mass" -> "样品质量换版，所有归一化（焓变 J/g）派生过期：" + changed;
            case "calibration" -> "温度标定换版，温度坐标与 onset/peak/endset 需要重算：" + changed;
            case "instrument" -> "仪器输出改版，扫描段、检测与积分局部重算：" + changed;
            case "tangent" -> "切线/区间调整，仅该转折的基线与焓变重算：" + changed;
            case "rate" -> "扫描速率换版，速率相关误差项重算：" + changed;
            default -> "依赖来源改版：" + changed;
        };
    }

    public Map<String, Node> nodes() {
        return nodes;
    }
}
