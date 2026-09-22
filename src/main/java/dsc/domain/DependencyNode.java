package dsc.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * A node in the derivation dependency graph. Recomputation propagates only to
 * dependents of a changed source; each node records why it is still valid or why
 * it must be recomputed.
 */
public final class DependencyNode {
    public String derivedId;
    public String kind;                 // BASELINE | TRANSITION | INTEGRAL | NORMALIZED_TRACE
    public List<String> dependsOn = new ArrayList<>();
    public List<String> inputHashes = new ArrayList<>();
    public String currentHash;
    public boolean dirty;
    public String reason;

    public DependencyNode() {}
    public DependencyNode(String derivedId, String kind) { this.derivedId = derivedId; this.kind = kind; }
}
