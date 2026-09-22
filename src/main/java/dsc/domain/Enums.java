package dsc.domain;

public final class Enums {
    private Enums() {}

    /** Whether temperature rises or falls along the program. Never inferred from file names. */
    public enum ScanDirection { HEATING, COOLING, UNKNOWN }

    /**
     * Sign convention reported by the instrument for the heat-flow channel.
     * ENDO_UP  : endothermic deflection is positive (common DSC).
     * ENDO_DOWN: endothermic deflection is negative (some instruments/exports).
     * Every derivation records the convention it canonicalized from.
     */
    public enum HeatFlowConvention { ENDO_UP, ENDO_DOWN, UNKNOWN }

    public enum SegmentKind { FIRST_HEAT, COOLING, REHEAT, OTHER }

    public enum TemperatureGrid { EVEN, UNEVEN }

    public enum TransitionType {
        GLASS("step", "Tg"),
        CRYSTALLIZATION("peak", "Tc"),
        MELTING("peak", "Tm");

        public final String shape;
        public final String shorthand;
        TransitionType(String shape, String shorthand) { this.shape = shape; this.short = shorthand; }
    }

    public enum BaselineMethod { LINEAR_CHORD, TANGENT_PAIR, STEP_PAIR }

    public enum CandidateStatus { ACCEPTED, ALTERNATE, REJECTED }

    public enum Severity { INFO, WARN, REJECT }

    public enum EventStatus { APPLIED, REJECTED, CONFLICT }
}
