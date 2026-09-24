package dsc.domain;

/**
 * One quantified contributor to the enthalpy uncertainty band. The UI reads
 * these to answer "where does the error bar come from": each item names its
 * source record and unit, so a stale mass revision shows up here explicitly.
 */
public record UncertaintySource(
        String sourceId,
        String sourceKind,
        double contributionJPerG,
        String basis
) {
}
