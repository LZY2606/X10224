package dsc.derive;

import dsc.core.Quantity;
import dsc.core.Units;
import dsc.events.Judgment;
import dsc.model.NodeStatus;

import java.util.List;

/**
 * One baseline candidate: a piecewise-linear baseline over a region.
 * A region can legally have several candidates at once; they are retained
 * side by side until the reviewer accepts or rejects each one.
 */
public record BaselineResult(
        String id,
        String regionId,
        String mode,
        double slopeMilliwattPerCelsius,
        double interceptMilliwatt,
        double leftAnchorCelsius,
        double rightAnchorCelsius,
        double rmseMilliwatt,
        NodeStatus status,
        String ruleVersion,
        List<String> dependencyIds,
        List<Judgment> judgments,
        long derivedAtSeq) {

    public Quantity baselineAt(double tCelsius) {
        return new Quantity(slopeMilliwattPerCelsius * tCelsius + interceptMilliwatt, Units.MW);
    }

    public boolean acceptedEligible() {
        return status == NodeStatus.VALID || status == NodeStatus.MULTIPLE;
    }
}
