package dsc.core;

import java.util.List;

/**
 * Integrated transition enthalpy and the error sources that contributed to it.
 */
public record EnthalpyResult(
        String id,
        double enthalpyJg,
        String signBasis,
        List<ErrorTerm> errorTerms,
        double combinedUncertaintyJg
) {

    public record ErrorTerm(String id, String source, double contributionJg, String basis) {
    }
}
