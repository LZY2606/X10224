package dsc.domain;

/** Machine-readable event types. Every human/system action appends one event. */
public final class Events {
    private Events() {
    }

    public static final String IMPORT = "IMPORT";                 // initial source archiving
    public static final String DECLARE_DIRECTION = "DECLARE_DIRECTION"; // resolve EXO/ENDO ambiguity
    public static final String RANGE_DRAG = "RANGE_DRAG";         // draggable interval adjusted
    public static final String MASS_REVISION = "MASS_REVISION";   // parameter revision
    public static final String SOURCE_EDIT = "SOURCE_EDIT";       // local-only source change
    public static final String DERIVATION = "DERIVATION";         // a recompute completed/failed
    public static final String ACCEPT = "ACCEPT";                 // candidate adopted
    public static final String REJECT = "REJECT";                 // candidate not adopted (kept)
    public static final String PARAM_ADJUST = "PARAM_ADJUST";     // tangent / parameter tweak
    public static final String CONFLICT = "CONFLICT";             // optimistic-lock conflict
    public static final String RESOLVE = "RESOLVE";               // conflict resolved
    public static final String EXPORT = "EXPORT";
    public static final String IMPORT_DB = "IMPORT_DB";
    public static final String RESET = "RESET";
    public static final String MIGRATE = "MIGRATE";
    public static final String RECOVER = "RECOVER";               // interrupted write cleaned up
}
