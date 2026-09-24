package dsc.domain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses an instrument export file. The raw representation (units, header
 * fields, original point order) is preserved verbatim; normalization happens
 * only in the derived layer.
 */
public class RunFile {
    public final Map<String, String> header = new LinkedHashMap<>();
    public final List<double[]> points = new ArrayList<>(); // [time, temp, heatflow]
    public String timeUnit = "s";
    public String tempUnit = "C";
    public String heatflowUnit = "mW";
    public String convention;       // exo_up | endo_up | null (unknown)
    public String conventionSource; // "instrument-header" | null
    public String sourceFile;

    public static RunFile parse(Path path) {
        RunFile f = new RunFile();
        f.sourceFile = path.getFileName().toString();
        List<String> lines;
        try {
            lines = Files.readAllLines(path);
        } catch (IOException e) {
            throw new RuntimeException("cannot read " + path + ": " + e.getMessage(), e);
        }
        boolean columnsSeen = false;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#")) {
                String body = line.substring(1).trim();
                int colon = body.indexOf(':');
                if (colon > 0) {
                    f.header.put(body.substring(0, colon).trim(), body.substring(colon + 1).trim());
                }
                continue;
            }
            if (!columnsSeen && line.toLowerCase().startsWith("time")) {
                columnsSeen = true;
                continue;
            }
            String[] parts = line.split(",");
            if (parts.length < 3) continue;
            try {
                f.points.add(new double[]{
                    Double.parseDouble(parts[0].trim()),
                    Double.parseDouble(parts[1].trim()),
                    Double.parseDouble(parts[2].trim())});
            } catch (NumberFormatException ignored) {
                // Unconvertible values stay in the raw file; the derived layer
                // simply skips them and a diagnostic is emitted by the caller.
            }
        }
        f.timeUnit = f.header.getOrDefault("time_unit", "s");
        f.tempUnit = f.header.getOrDefault("temperature_unit", "C");
        f.heatflowUnit = f.header.getOrDefault("heat_flow_unit", "mW");
        // Rule: exo/endo direction is NEVER guessed from the file name.
        // It only comes from explicit instrument metadata or a user decision.
        f.convention = f.header.get("heat_flow_convention");
        f.conventionSource = f.convention != null ? "instrument-header" : null;
        return f;
    }
}
