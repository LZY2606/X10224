package org.dscreview.model;

import java.util.List;
import static org.dscreview.model.DomainEnums.ValidityCode;

/** Why a derived result is still valid or must be recomputed. */
public record Validity(ValidityCode code, boolean recomputeRequired, String explanation, List<String> staleInputs) {
    public static Validity valid(String why) { return new Validity(ValidityCode.VALID, false, why, List.of()); }
    public static Validity of(ValidityCode c, String why, String... stale) {
        return new Validity(c, true, why, List.of(stale));
    }
}
