package org.materials.dsc.domain;

import java.util.List;

/**
 * 依赖图中的一个派生节点。每次推导都是独立版本（fingerprint 含规则版本+依赖指纹），
 * 只有完整成功后调用方才切换当前指针。
 */
public record DerivedNode(
        String id,
        String kind,
        List<String> dependencyIds,
        String ruleVersion,
        String fingerprint,
        Object payload,
        String status,
        List<String> diagnostics,
        long eventSeq) {

    public boolean isStale() {
        return "stale".equals(status);
    }
}
