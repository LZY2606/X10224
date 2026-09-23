package dsc.rules;

import dsc.model.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Rule DIR-DATA-1: heating/cooling is decided from the measured temperature
 * trend and the instrument's programmed scan-rate sign. A file name (or its
 * words "heat"/"cool") is never accepted as evidence. When the two
 * evidence channels disagree the result is AMBIGUOUS with both candidates
 * retained.
 */
public final class DirectionResolver {

    private DirectionResolver() {}

    /**
     * @param temperatureAscending measured T(end) &gt; T(start)
     * @param programmedRateKmin   programmed rate, signed by the program
     *                             (positive = heat, negative = cool)
     * @param fileNameGuess        what the file name would imply, or null;
     *                             accepted for nothing except diagnostics
     */
    public static DirectionDecision decide(boolean temperatureAscending,
                                           double programmedRateKmin,
                                           Direction fileNameGuess) {
        List<String> diags = new ArrayList<>();
        Direction fromData = temperatureAscending ? Direction.HEATING : Direction.COOLING;
        Direction fromRate = programmedRateKmin > 0 ? Direction.HEATING
                : programmedRateKmin < 0 ? Direction.COOLING : null;

        if (fileNameGuess != null && fileNameGuess != fromData) {
            diags.add("file-name-implies-" + fileNameGuess + " but measured trend implies "
                    + fromData + "; file name ignored per " + dsc.model.RuleVersions.R_DIRECTION);
        }

        if (fromRate != null && fromRate != fromData) {
            List<Direction> both = List.of(Direction.HEATING, Direction.COOLING);
            diags.add("programmed-rate implies " + fromRate + " but measured trend implies "
                    + fromData + "; segment quarantined as AMBIGUOUS");
            return DirectionDecision.ambiguous(both,
                    "measured-trend vs programmed-rate conflict", diags);
        }
        return DirectionDecision.decided(fromData,
                "measured temperature trend" + (fromRate != null ? ", confirmed by scan rate"
                        : ""), diags);
    }
}
