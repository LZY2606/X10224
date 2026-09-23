package dsc.rules;

import dsc.model.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of deciding a segment's thermal direction. When data and metadata
 * disagree the decision is {@link Direction#AMBIGUOUS} and every legal
 * candidate is retained, rather than guessing from the file name.
 */
public record DirectionDecision(Direction direction, List<Direction> candidates,
                                String basis, List<String> diagnostics) {

    public static DirectionDecision decided(Direction d, String basis, List<String> diags) {
        return new DirectionDecision(d, List.of(d), basis, diags);
    }

    public static DirectionDecision ambiguous(List<Direction> candidates, String basis,
                                              List<String> diags) {
        return new DirectionDecision(Direction.AMBIGUOUS, new ArrayList<>(candidates), basis,
                diags);
    }
}
