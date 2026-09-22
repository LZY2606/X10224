package dsc.domain;

/** Domain enumerations. Nothing here is inferred from filenames. */
public final class Enums {
    private Enums() {}

    /** Heat-flow plotting/sign convention as DECLARED BY the instrument file header. */
    public enum SignConvention {
        ENDO_UP,        // endothermic deflects upward / positive
        ENDO_DOWN,      // exothermic up (the common DSC exporter default)
        UNDECLARED      // the file does not state it; direction must not be guessed
    }

    public enum SegmentDirection {
        HEATING, COOLING, UNKNOWN
    }

    public enum EventKind {
        GLASS_TRANSITION, CRYSTALLIZATION, MELTING, UNCLASSIFIED
    }

    public enum BaselineMethod {
        LINEAR_ENDPOINT, TANGENT, STEP_SIGMOID
    }

    public enum IntervalStatus {
        EMPTY, OVERLAP, MIXED_SEGMENT, VALID
    }

    public enum Decision {
        ACCEPTED, REJECTED, UNDECIDED
    }
}
