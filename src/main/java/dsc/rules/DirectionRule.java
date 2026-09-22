package dsc.rules;

import dsc.model.Events;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Endo/exo direction is decided ONLY by the instrument's declared heat-flow
 * sign convention metadata. Filenames are never consulted; a filename hint is
 * recorded as an ignored hint. When the convention is missing or ambiguous,
 * the result stays UNDETERMINED and all legal candidates are preserved.
 */
public final class DirectionRule {

    public record Decision(
            String status,               // DETERMINED | UNDETERMINED
            String signConvention,       // e.g. "endo_up" | "exo_up" | null
            List<String> candidates,     // legal conventions kept when undetermined
            List<Map<String, Object>> events) {}

    private DirectionRule() {}

    public static Decision decide(Map<String, String> instrumentHeader, String fileName) {
        List<Map<String, Object>> events = new ArrayList<>();
        String convention = instrumentHeader == null ? null : instrumentHeader.get("sign_convention");

        if (fileName != null) {
            String f = fileName.toLowerCase();
            if (f.contains("endo") || f.contains("exo")) {
                events.add(Map.of(
                        "type", Events.FILENAME_HINT_IGNORED,
                        "fileName", fileName,
                        "reason", "endo/exo direction must come from instrument sign_convention metadata, never from file names"));
            }
        }

        if (convention == null || convention.isBlank()) {
            events.add(Map.of(
                    "type", Events.DIRECTION_UNDETERMINED,
                    "reason", "instrument header lacks sign_convention"));
            return new Decision("UNDETERMINED", null,
                    List.of("endo_up", "exo_up"), events);
        }
        String c = convention.trim().toLowerCase();
        if (!c.equals("endo_up") && !c.equals("exo_up")) {
            events.add(Map.of(
                    "type", Events.DIRECTION_UNDETERMINED,
                    "reason", "unknown sign_convention value: " + convention));
            return new Decision("UNDETERMINED", null,
                    List.of("endo_up", "exo_up"), events);
        }
        events.add(Map.of(
                "type", Events.DIRECTION_DETERMINED,
                "signConvention", c));
        return new Decision("DETERMINED", c, List.of(c), events);
    }

    /** Label a signed peak deviation (mW) using a determined convention. */
    public static String label(String signConvention, double signedDeviation) {
        if (signConvention == null) return "undetermined";
        boolean up = signedDeviation > 0;
        if (signConvention.equals("endo_up")) return up ? "endothermic" : "exothermic";
        return up ? "exothermic" : "endothermic";
    }
}
