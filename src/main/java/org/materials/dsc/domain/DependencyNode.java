package org.materials.dsc.domain;

import java.util.List;

/**
 * 依赖图节点：直接来源(raw)或派生(derived)。
 * version 随该对象的产生事件递增；status 为 ok/stale/superseded/rejected。
 */
public record DependencyNode(String nodeId, String kind, String label, int version,
                             String producedEventSeq, String ruleVersion,
                             String status, String staleReason,
                             List<String> dependsOn) {
    public static final String KIND_RAW = "raw";
    public static final String KIND_DERIVED = "derived";
    public static final String STATUS_OK = "ok";
    public static final String STATUS_STALE = "stale";
    public static final String STATUS_SUPERSEDED = "superseded";
    public static final String STATUS_REJECTED = "rejected";
}
