package dsc.casepack;

import com.fasterxml.jackson.databind.JsonNode;
import dsc.Json;
import dsc.domain.Deriver;
import dsc.domain.Integrate;
import dsc.domain.Rules;
import dsc.domain.State;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** 离线案例包「热转折案」：只从仓库内 resources 读取，无任何外部依赖。 */
public final class CasePack {
    private CasePack() {}

    public static String resource(String name) {
        try (InputStream in = CasePack.class.getResourceAsStream("/casepack/" + name)) {
            if (in == null) throw new IllegalStateException("missing casepack resource " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static JsonNode json(String name) {
        return Json.read(resource(name));
    }

    /** 解析仪器 CSV：表头原样保留，方向只取表头 direction 字段。 */
    public static State.Curve parseCurve(String id, String file) {
        State.Curve c = new State.Curve();
        c.id = id;
        c.rev = 1;
        c.sourceFile = file;
        String lower = file.toLowerCase();
        if (lower.contains("endo")) c.filenameHint = "endo";
        else if (lower.contains("exo")) c.filenameHint = "exo";
        for (String line : resource("instrument/" + file).split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#")) {
                c.rawHeader.add(line);
                if (line.startsWith("#direction=")) {
                    String v = line.substring("#direction=".length()).trim();
                    if (v.equals("exo-up") || v.equals("endo-up")) c.directionMeta = v;
                }
                continue;
            }
            if (line.startsWith("temperature")) continue;
            String[] parts = line.split(",");
            c.points.add(new double[]{Double.parseDouble(parts[0]), Double.parseDouble(parts[1])});
        }
        return c;
    }

    /** 构建初始状态：仪器输出/质量/速率/气氛/标定/切线分别存档，随后首轮派生与规则判定。 */
    public static State initialState() {
        State st = new State();
        st.sampleId = json("sample.json").get("sampleId").asText();

        st.curves.put("curve-heat1", parseCurve("curve-heat1", "heat1.csv"));
        st.curves.put("curve-cool1", parseCurve("curve-cool1", "cool1.csv"));
        st.curves.put("curve-heat2", parseCurve("curve-heat2", "heat2_endo_guess.csv"));

        for (JsonNode m : json("sample.json").get("massVersions")) {
            State.MassVersion mv = new State.MassVersion();
            mv.id = m.get("id").asText();
            mv.rev = 1;
            mv.mg = m.get("mg").asDouble();
            mv.note = m.get("note").asText();
            mv.ts = m.get("ts").asLong();
            st.masses.put(mv.id, mv);
        }
        st.currentMassId = "mass-v1";

        for (JsonNode cj : json("calibration.json").get("calibrations")) {
            State.Calibration cal = new State.Calibration();
            cal.id = cj.get("id").asText();
            cal.rev = 1;
            cal.offsetK = cj.get("offsetK").asDouble();
            cal.slope = cj.get("slope").asDouble();
            cal.reference = cj.get("reference").asText();
            st.calibrations.put(cal.id, cal);
        }

        for (JsonNode sj : json("events_def.json").get("segments")) {
            State.Segment s = new State.Segment();
            s.id = sj.get("id").asText();
            s.rev = 1;
            s.curveId = sj.get("curveId").asText();
            s.kind = sj.get("kind").asText();
            s.rateKPerMin = sj.get("rateKPerMin").asDouble();
            s.atmosphere = sj.get("atmosphere").asText();
            s.calibrationId = sj.get("calibrationId").asText();
            s.cycleIndex = sj.get("cycleIndex").asInt();
            st.segments.put(s.id, s);
        }

        for (JsonNode ej : json("events_def.json").get("events")) {
            State.ThermalEvent e = new State.ThermalEvent();
            e.id = ej.get("id").asText();
            e.rev = 1;
            e.segmentId = ej.get("segmentId").asText();
            e.type = ej.get("type").asText();
            e.tStart = ej.get("tStart").asDouble();
            e.tEnd = ej.get("tEnd").asDouble();
            st.events.put(e.id, e);
        }

        for (JsonNode tj : json("tangents.json").get("tangents")) {
            State.Tangent t = new State.Tangent();
            t.id = tj.get("id").asText();
            t.rev = 1;
            t.eventId = tj.get("eventId").asText();
            t.author = tj.get("author").asText();
            t.side = tj.get("side").asText();
            t.t1 = tj.get("t1").asDouble();
            t.t2 = tj.get("t2").asDouble();
            st.tangents.put(t.id, t);
        }

        for (JsonNode bj : json("baselines.json").get("baselines")) {
            State.Baseline b = new State.Baseline();
            b.id = bj.get("id").asText();
            b.rev = 1;
            b.eventId = bj.get("eventId").asText();
            b.method = bj.get("method").asText();
            b.originEventId = "import";
            State.ThermalEvent e = st.events.get(b.eventId);
            State.Curve c = st.curves.get(st.segments.get(e.segmentId).curveId);
            double[] line = b.method.equals("linear-chord")
                    ? Integrate.chordBaseline(c, e.tStart, e.tEnd)
                    : Integrate.tangentBaseline(st, e);
            if (line != null) {
                b.slope = line[0];
                b.intercept = line[1];
            }
            st.baselines.put(b.id, b);
        }

        Deriver.deriveAll(st);

        int seq = 1;
        for (State.Curve c : st.curves.values()) {
            st.judgments.add(Rules.checkDirection(c, seq++));
            st.judgments.add(Rules.checkSpacing(c, seq++));
        }
        List<State.Judgment> overlaps = Rules.checkOverlaps(st, seq);
        seq += overlaps.size();
        st.judgments.addAll(overlaps);
        for (State.ThermalEvent e : st.events.values()) {
            State.Curve c = st.curves.get(st.segments.get(e.segmentId).curveId);
            st.judgments.add(Rules.checkEmptyInterval(c, e.id, e.tStart, e.tEnd, seq++));
            st.judgments.add(Rules.parallelBaselines(st, e.id, seq++));
        }
        return st;
    }

    public static List<JsonNode> replayScript() {
        List<JsonNode> out = new ArrayList<>();
        for (String line : resource("events.jsonl").split("\n")) {
            if (!line.isBlank()) out.add(Json.read(line));
        }
        return out;
    }
}
