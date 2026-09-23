package io.dscreview.domain;

import java.util.List;

/**
 * The six kinds of direct sources, archived separately and versioned
 * independently: instrument output, sample mass, scan rate, atmosphere,
 * temperature calibration, and user-selected tangents. Every source keeps its
 * original representation ({@link #raw}) alongside any structured fields.
 */
public sealed interface Source permits Source.Instrument, Source.Mass, Source.Rate,
        Source.Atmosphere, Source.TempCalibration, Source.Tangents {

    String id();
    int version();
    String createdAt();
    String raw();
    String origin();

    /** Instrument output: one or more raw columns in the exact imported form. */
    record Instrument(
            String id, int version, String createdAt, String raw, String origin,
            String fileLabel,
            String endoConventionDeclared,
            List<RawColumn> columns) implements Source {
        public RawColumn column(String name) {
            return columns.stream().filter(c -> c.name().equalsIgnoreCase(name))
                    .findFirst().orElse(null);
        }
    }

    /** Sample mass; unit is carried explicitly. */
    record Mass(
            String id, int version, String createdAt, String raw, String origin,
            Qty mass, String reason) implements Source {}

    /** Nominal scan rate per cycle/segment where declared. */
    record Rate(
            String id, int version, String createdAt, String raw, String origin,
            Qty rate, String segmentRef) implements Source {}

    /** Purge atmosphere and declared flow. */
    record Atmosphere(
            String id, int version, String createdAt, String raw, String origin,
            String gas, Qty flow, String note) implements Source {}

    /**
     * Temperature calibration: additive offset (and optional scale) applied
     * during normalisation, with the standard / calibration reference stated.
     */
    record TempCalibration(
            String id, int version, String createdAt, String raw, String origin,
            double offsetC, double scale, String reference, String appliedTo) implements Source {}

    /** User-selected tangent anchor points, stored as raw temperature text. */
    record Tangents(
            String id, int version, String createdAt, String raw, String origin,
            String windowId, List<String> anchorTemps, String method) implements Source {}
}
