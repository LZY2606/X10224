package dsc.model;

/** Domain enums shared by import, derivation and the review UI. */
public final class Types {
    private Types() {}

    /** Typed archive slots: every direct input is filed separately and never inferred from a file name. */
    public enum SourceKind {
        INSTRUMENT_OUTPUT,
        SAMPLE_MASS,
        SCAN_RATE,
        ATMOSPHERE,
        TEMPERATURE_CALIBRATION,
        TANGENT_SELECTION
    }

    /** Explicit heat-flow sign convention recorded by the instrument metadata. */
    public enum HeatFlowConvention {
        /** Positive heat flow = heat into sample (melting positive). */
        ENDO_UP,
        /** Positive heat flow = heat out of sample (crystallisation positive). */
        EXO_UP
    }

    public enum ScanDirection {
        HEATING,
        COOLING,
        /** Isothermal hold; never used together with ramped segments for baselines. */
        ISOTHERMAL
    }

    public enum TransitionKind {
        GLASS,
        CRYSTALLISATION,
        MELTING,
        UNRESOLVED
    }

    public enum Decision {
        ACCEPTED,
        REJECTED,
        /** Left as a legitimate parallel candidate. */
        UNDECIDED
    }
}
