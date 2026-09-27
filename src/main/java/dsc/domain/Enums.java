package dsc.domain;

/** Heating/cooling classification. Decided from data + metadata, never the file name. */
public enum ScanDirection { HEATING, COOLING, ISOTHERMAL, AMBIGUOUS }

/** Event classification with the endothermic/exothermic character expressed separately. */
public enum TransitionKind {
    GLASS_STEP("glass transition", true),
    MELT("melting", false),
    CRYSTALLIZATION("crystallization", true),
    UNKNOWN("unclassified", false);

    private final String label;
    private final boolean exothermicCanonical;

    TransitionKind(String label, boolean exothermicCanonical) {
        this.label = label;
        this.exothermicCanonical = exothermicCanonical;
    }

    public String label() { return label; }
    /** Conventional exothermic-up character of this kind, used only as a cross-check hint. */
    public boolean exothermicCanonical() { return exothermicCanonical; }
}
