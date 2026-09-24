package dsc.domain;

/** 派生节点种类；稳定身份由 (kind, 业务键) 决定。 */
public enum NodeKind {
    NORMALIZED_CURVE("归一化曲线"),
    BASELINE("基线"),
    TRANSITION("相变结果");

    private final String label;
    NodeKind(String label) { this.label = label; }
    public String label() { return label; }
}
