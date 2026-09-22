package dsc.derive;

import dsc.domain.BaselineCandidate;
import dsc.domain.DerivationRecord;
import dsc.domain.TransitionResult;

import java.util.List;

/** All legal candidates for one interval analysis plus interval-level diagnostics. */
public record DerivationOutput(List<DerivationRecord> records, List<String> diagnostics) {
}
