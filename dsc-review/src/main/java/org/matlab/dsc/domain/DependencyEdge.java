package org.matlab.dsc.domain;

/**
 * Edge in the provenance/dependency graph. The target node version was
 * produced using the exact source revision or node version named here.
 */
public record DependencyEdge(String fromNodeVersion, String depRef, String depKind) {
    public static final String KIND_SOURCE = "source";
    public static final String KIND_NODE = "node";
    public static final String KIND_RULE = "rule";
}
