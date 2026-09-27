package dsc.core;

import java.util.List;
import java.util.Optional;

/**
 * Resolves the endo/exo direction strictly from instrument metadata or an
 * explicit human decision. File names are never consulted: when metadata is
 * missing or contradictory the result stays UNDETERMINED and every legal
 * candidate is preserved.
 */
public final class DirectionResolver {

    public record Resolution(Direction direction, boolean determined,
                             List<Direction> candidates, String rationale) {
        public static Resolution determined(Direction d, String rationale) {
            return new Resolution(d, true, List.of(d), rationale);
        }

        public static Resolution undetermined(String rationale) {
            return new Resolution(null, false, List.of(Direction.ENDO_UP, Direction.ENDO_DOWN), rationale);
        }
    }

    private DirectionResolver() {}

    public static Resolution fromMetadata(Optional<String> conventionHeader) {
        if (conventionHeader.isEmpty()) {
            return Resolution.undetermined(
                "instrument metadata carries no convention header; both legal candidates retained");
        }
        String v = conventionHeader.get().trim().toLowerCase();
        return switch (v) {
            case "endo_up" -> Resolution.determined(Direction.ENDO_UP, "instrument header convention=endo_up");
            case "endo_down" -> Resolution.determined(Direction.ENDO_DOWN, "instrument header convention=endo_down");
            default -> Resolution.undetermined(
                "instrument header convention=" + v + " is not decisive; both legal candidates retained");
        };
    }
}
