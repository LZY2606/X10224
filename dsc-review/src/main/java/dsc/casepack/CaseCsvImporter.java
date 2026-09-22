package dsc.casepack;

import dsc.model.SignalConvention;
import dsc.model.TraceImport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Imports one of the committed instrument-output fixtures, preserving the
 * original text of every cell. Headers carry the convention explicitly so the
 * file name never decides exo/endo direction.
 */
public final class CaseCsvImporter {

    private CaseCsvImporter() {}

    public static TraceImport load(Path csv, String segmentId, int cycleFallback, double rateFallback)
            throws IOException {
        List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
        TraceImport ti = new TraceImport();
        ti.segmentId = segmentId;
        ti.sourceFile = csv.getFileName().toString();
        ti.cycle = cycleFallback;
        ti.conventionRaw = "unknown";
        ti.declaredRate = rateFallback;
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            if (t.startsWith("#")) {
                readHeader(ti, t);
                continue;
            }
            String[] parts = t.split(",", -1);
            if (parts.length < 2) continue;
            if (parts[0].toLowerCase().contains("temperature")) continue;
            ti.rows.add(new TraceImport.Row(parts[0].trim(), parts[1].trim(),
                    parts.length > 2 ? parts[2].trim() : null));
        }
        return ti;
    }

    private static void readHeader(TraceImport ti, String h) {
        String low = h.toLowerCase();
        if (low.contains("convention=exo-up")) ti.conventionRaw = "exo-up";
        else if (low.contains("convention=endo-up")) ti.conventionRaw = "endo-up";
        else ti.conventionRaw = "unknown";
        java.util.regex.Matcher mRate = java.util.regex.Pattern
                .compile("rate=([-0-9.]+)").matcher(h);
        if (mRate.find()) ti.declaredRate = Double.parseDouble(mRate.group(1));
        java.util.regex.Matcher mCycle = java.util.regex.Pattern
                .compile("cycle=([0-9]+)").matcher(h);
        if (mCycle.find()) ti.cycle = Integer.parseInt(mCycle.group(1));
    }
}
