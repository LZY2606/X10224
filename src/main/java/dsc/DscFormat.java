package dsc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parser for the domain-specific offline case-package text format (.dsc.txt). */
public final class DscFormat {
    private DscFormat() {}

    public static String readText(String base, String file) {
        try {
            Path p = Path.of(base, file);
            if (Files.exists(p)) return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new CaseException("cannot read " + file + ": " + e.getMessage());
        }
        try (var in = DscFormat.class.getResourceAsStream("/casepkg/" + file)) {
            if (in == null) throw new CaseException("missing case-package file: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new CaseException("cannot read classpath " + file + ": " + e.getMessage());
        }
    }

    static List<String[]> rows(String text) {
        List<String[]> out = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#") || t.startsWith("//")) continue;
            out.add(t.split("[\\s,]+"));
        }
        return out;
    }

    static Domain.RQ rq(String token, String unit, String basis) {
        return new Domain.RQ(token, unit, basis, Units.num(token));
    }

    public static Domain.InstrumentDoc parseInstrument(String file, String text) {
        String convention = "EXO_UP";
        String instrument = "?";
        String segId = null, role = null, start = null, end = null;
        String tUnit = "degC", fUnit = "mW", timeUnit = "s";
        List<Domain.Segment> segs = new ArrayList<>();
        List<Domain.SP> pts = new ArrayList<>();
        int idx = 0;
        String[] lines = text.split("\\R");
        for (int li = 0; li < lines.length; li++) {
            String rawLine = lines[li];
            String t = rawLine.trim();
            if (t.isEmpty() || t.startsWith("//")) continue;
            if (t.startsWith("#")) {
                String[] h = t.substring(1).trim().split("\\s*=\\s*", 2);
                String key = h[0].trim().toUpperCase(java.util.Locale.ROOT);
                String val = h.length > 1 ? h[1].trim() : "";
                switch (key) {
                    case "SEGMENT" -> {
                        if (segId != null) segs.add(new Domain.Segment(segId, role, rq(start, tUnit, "T"), rq(end, tUnit, "T"), List.copyOf(pts)));
                        String[] sp = val.split("\\s+");
                        segId = sp[0];
                        role = sp.length > 1 ? sp[1].toUpperCase(java.util.Locale.ROOT) : "HEAT";
                        pts = new ArrayList<>();
                        idx = 0;
                    }
                    case "EXO-DIRECTION", "CONVENTION" -> convention = val.toUpperCase(java.util.Locale.ROOT);
                    case "INSTRUMENT" -> instrument = val;
                    case "TEMP-UNIT" -> tUnit = val;
                    case "FLOW-UNIT" -> fUnit = val;
                    case "TIME-UNIT" -> timeUnit = val;
                    case "START" -> start = val;
                    case "END" -> end = val;
                    default -> { }
                }
                continue;
            }
            String[] c = t.split("[\\s,]+");
            List<String> problems = new ArrayList<>();
            String tm = c.length > 0 ? c[0] : "";
            String te = c.length > 1 ? c[1] : "";
            String fl = c.length > 2 ? c[2] : "";
            Double tS = Units.timeS(tm, timeUnit);
            if (tS == null) problems.add("TIME_UNCONVERTIBLE:" + timeUnit + ":'" + tm + "'");
            Double tK = Units.tempK(te, tUnit);
            if (tK == null) problems.add("TEMP_UNCONVERTIBLE:" + tUnit + ":'" + te + "'");
            Double fW = Units.flowW(fl, fUnit);
            if (fW == null) problems.add("FLOW_UNCONVERTIBLE:" + fUnit + ":'" + fl + "'");
            pts.add(new Domain.SP(idx++, tm, te, fl, tS, tK, fW, List.copyOf(problems)));
        }
        if (segId != null)
            segs.add(new Domain.Segment(segId, role, rq(start, tUnit, "T"), rq(end, tUnit, "T"), List.copyOf(pts)));
        return new Domain.InstrumentDoc(file, convention, instrument, List.copyOf(segs));
    }

    public static Domain.MassDoc parseMass(String file, String text) {
        String mass = "", unit = "mg", revision = "v1", note = "";
        for (String[] r : rows(text)) {
            if (r.length < 2) continue;
            switch (r[0].toUpperCase(java.util.Locale.ROOT)) {
                case "MASS" -> { mass = r[1]; unit = r.length > 2 ? r[2] : unit; }
                case "REVISION" -> revision = r[1];
                case "NOTE" -> note = String.join(" ", java.util.Arrays.copyOfRange(r, 1, r.length));
                default -> { }
            }
        }
        Double g = Units.massG(mass, unit);
        return new Domain.MassDoc(file, new Domain.RQ(mass, unit, "m", g), revision, note);
    }

    public static Domain.RateDoc parseRate(String file, String text) {
        Map<String, Domain.RQ> rates = new LinkedHashMap<>();
        for (String[] r : rows(text)) {
            if (r.length < 3) continue;
            String seg = r[0];
            Domain.RQ rq = new Domain.RQ(r[1], r[2], "dT/dt", Units.rateKPerMin(r[1], r[2]));
            rates.put(seg, rq);
        }
        return new Domain.RateDoc(file, Map.copyOf(rates));
    }

    public static Domain.AtmosphereDoc parseAtmos(String file, String text) {
        String gas = "N2"; String flow = "", unit = "mL/min";
        for (String[] r : rows(text)) {
            if (r.length < 2) continue;
            switch (r[0].toUpperCase(java.util.Locale.ROOT)) {
                case "GAS" -> gas = r[1];
                case "FLOW" -> { flow = r[1]; unit = r.length > 2 ? r[2] : unit; }
                default -> { }
            }
        }
        return new Domain.AtmosphereDoc(file, gas, rq(flow, unit, "gas flow"));
    }

    public static Domain.CalibrationDoc parseCalib(String file, String text) {
        String ref = "In"; String off = "0.0"; String unit = "K";
        for (String[] r : rows(text)) {
            if (r.length < 2) continue;
            switch (r[0].toUpperCase(java.util.Locale.ROOT)) {
                case "REFERENCE" -> ref = r[1];
                case "OFFSET" -> { off = r[1]; unit = r.length > 2 ? r[2] : unit; }
                default -> { }
            }
        }
        Double k = Units.tempK(off, unit) == null ? null : Units.tempK(off, unit) - 273.15;
        return new Domain.CalibrationDoc(file, ref, new Domain.RQ(off, unit, "delta T", k));
    }

    public static Domain.TangentDoc parseTangent(String file, String text) {
        List<Domain.TangentAnchor> anchors = new ArrayList<>();
        String tUnit = "degC", fUnit = "mW";
        String[] lines = text.split("\\R");
        for (String rawLine : lines) {
            String t = rawLine.trim();
            if (t.isEmpty() || t.startsWith("//")) continue;
            if (t.startsWith("#")) {
                String[] h = t.substring(1).trim().split("\\s*=\\s*", 2);
                if (h.length == 2) {
                    if (h[0].trim().equalsIgnoreCase("temp-unit")) tUnit = h[1].trim();
                    if (h[0].trim().equalsIgnoreCase("flow-unit")) fUnit = h[1].trim();
                }
                continue;
            }
            String[] c = t.split("[\\s,]+");
            if (c.length < 5 || c[0].equalsIgnoreCase("RANGE")) continue;
            String ft = c[4], fu = c.length > 5 ? c[5] : fUnit;
            anchors.add(new Domain.TangentAnchor(c[0],
                    rq(c[1], tUnit, "T"), rq(c[2], c[3], "heat flow"),
                    rq(ft, tUnit, "T"), rq("", fu, "heat flow")));
        }
        return new Domain.TangentDoc(file, List.copyOf(anchors));
    }
}
