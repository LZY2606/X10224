package dsc.domain;

import java.util.List;
import java.util.Map;

/**
 * 依赖图中一个派生节点在某次推导后的版本。
 * key 是稳定身份（如 feature id / metric id），每次推导产生独立版本，
 * 只有完整成功后当前指针才指向新版本。
 */
public record Derivation(
        String key,
        String facet,
        int version,
        String status,
        String kind,
        long eventSeq,
        Map<String, Integer> dependencyVersions,
        List<String> diagnostics,
        String why,
        String json
) {
    public static final String VALID = "valid";
    public static final String STALE = "stale";
    public static final String EMPTY = "empty";
    public static final String AMBIGUOUS = "ambiguous";
    public static final String ERROR = "error";

    public boolean stillValidAgainst(Map<String, Integer> currentDependencyVersions) {
        return dependencyVersions.entrySet().stream()
                .allMatch(e -> currentDependencyVersions.getOrDefault(e.getKey(), 0)
                        .equals(e.getValue()));
    }
}
