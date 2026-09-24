package dsc.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dsc.domain.*;

import java.util.*;

/**
 * Derivation layer. Pure functions of the {@link State} facts:
 *   NORMALIZED per segment  -> BASELINE per interval -> EVENT per interval
 *
 * Every output value carries a stable @id. Content-addressing makes a
 * derivation reproducible from the same dependency versions: identities survive
 * export/delete/import and full replay.
 *
 * Semantic rules enforced here (also emitted as machine-readable JUDGMENTs):
 *  - endo/exo direction is taken only from the instrument sign_convention, never
 *    from a file name; heating and cooling segments are never mixed inside one
 *    baseline/integration;
 *  - when several baselines are both legitimate, both candidates and their
 *    differing enthalpies are retained (nothing is collapsed to one answer);
 *  - a mass revision makes every mass-normalized derivation stale.
 */
public final class Engine {
    public static final String RULE_VERSION = "dsc-rules-1.0.0";

    private Engine() {}

    public record Reaction(List<Derivation> derivations, List<Event> judgments) {}

    // ---- normalization -----------------------------------------------------

    /** Build (or rebuild) the NORMALIZED derivation for one segment. */
    public static Derivation normalize(String branch, State st, ObjectNode curve) {
        String segId = curve.path("id").asText();
        String sampleId = curve.path("sampleId").asText();
        String massId = st.currentMass(sampleId);
        String calId = st.currentCalibration(sampleId);
        ObjectNode massNode = massId == null ? null : st.mass(massId);
        ObjectNode calNode = calId == null ? null : st.calibration(calId);

        List<String> diags = new ArrayList<>();
        String status = Derivation.OK;
        String dirRaw = curve.path("directionRaw").asText("");
        String signRaw = curve.path("signConventionRaw").asText("");
        Direction direction = Direction.parse(dirRaw);
        SignConvention sign = SignConvention.parse(signRaw);
        if (direction == Direction.UNKNOWN) {
            status = Derivation.INVALID;
            diags.add("DIRECTION_UNKNOWN: explicit direction header missing/unreadable '" + dirRaw
                    + "'; file name is not used to infer heating vs cooling");
        }
        if (sign == SignConvention.UNKNOWN) {
            status = Derivation.INVALID;
            diags.add("SIGN_UNKNOWN: sign_convention header missing/unreadable '" + signRaw
                    + "'; endo/exo direction cannot be assigned");
        }
        Double massG = massNode == null ? null : massNode.path("massCanonical").asDouble(Double.NaN);
        if (massG == null || Double.isNaN(massG) || massG <= 0) {
            status = Derivation.INVALID;
            diags.add("MASS_MISSING: no valid sample mass revision; specific heat flow unavailable");
        }
        double offsetC = calNode == null ? 0.0 : calNode.path("offsetCanonical").asDouble(0.0);
        if (calNode == null) diags.add("CALIBRATION_UNSET: treating temperature offset as 0 degC");

        ObjectNode out = st.obj();
        out.put("kind", "NORMALIZED");
        out.put("@id", "norm:" + segId);
        out.put("segmentId", segId);
        out.put("ruleVersion", RULE_VERSION);
        out.put("massId", massId);
        out.put("calibrationId", calId);
        out.put("direction", direction.name());
        out.put("signConvention", sign.name());

        ObjectNode base = st.obj();
        base.put("@id", "norm:" + segId + ":basis");
        base.put("temperature", "degC (T_read + calibration offset)");
        base.put("signedHeatFlow", "mW, endotherm positive");
        base.put("specificHeatFlow", "W/g = signed mW / (1000 * mass g)");
        out.set("coordinateBasis", base);

        ArrayNode pts = st.array();
        JsonNode srcPts = curve.path("points");
        for (int i = 0; i < srcPts.size(); i++) {
            JsonNode sp = srcPts.get(i);
            ObjectNode q = st.obj();
            q.put("@id", "pt:" + segId + ":" + sp.path("index").asInt(i));
            q.put("index", sp.path("index").asInt(i));
            q.put("tempRaw", sp.path("tempRaw").asText());
            q.put("hfRaw", sp.path("hfRaw").asText());
            if (sp.path("tempC").isNumber()) q.put("tempC", sp.path("tempC").asDouble() + offsetC);
            if (sp.path("hfMW").isNumber() && sign != SignConvention.UNKNOWN) {
                double signed = sp.path("hfMW").asDouble() * sign.endoUpSign();
                q.put("signedHfMW", signed);
                if (massG != null && massG > 0) q.put("specificHfWG", signed / (1000.0 * massG));
            }
            pts.add(q);
        }
        out.set("points", pts);
        ArrayNode gapArr = st.array();
        for (JsonNode g : curve.path("gaps")) {
            ObjectNode gn = st.obj();
            gn.put("@id", "gap:" + segId + ":" + g.path("afterIndex").asInt());
            gn.put("afterIndex", g.path("afterIndex").asInt());
            gn.put("tempDeltaC", g.path("tempDelta").asDouble());
            gn.put("nominalDeltaC", g.path("medianDelta").asDouble());
            gn.put("ratio", g.path("ratio").asDouble());
            gapArr.add(gn);
        }
        out.set("nonuniformGaps", gapArr);

        ObjectNode dg = st.obj();
        for (int i = 0; i < diags.size(); i++) dg.put("d" + i, diags.get(i));
        String deps = depList(segId + ":curve", massId, calId);
        String id = Ids.sha("der", RULE_VERSION, "NORMALIZED", segId,
                String.valueOf(curve.path("curveVersion").asInt(1)), String.valueOf(massId),
                String.valueOf(calId), String.valueOf(curve.path("pointsHash").asText("")));
        return new Derivation(id, "NORMALIZED:" + segId, "NORMALIZED", -1, branch,
                RULE_VERSION, deps, out, dg, status);
    }

    static String depList(String... ids) {
        ArrayNode a = State.M.createArrayNode();
        for (String s : ids) if (s != null) a.add(s);
        return a.toString();
    }

    // ---- interval resolution: baselines + features ------------------------

    /** Recompute every derivation that depends on one normalized segment. */
    public static Reaction resolveSegment(String branch, State st, String segId) {
        List<Derivation> ders = new ArrayList<>();
        List<Event> judges = new ArrayList<>();
        Derivation norm = st.current.get("NORMALIZED:" + segId);
        if (norm != null) {
            ders.add(norm);
            if (!norm.diagnostics().isEmpty()) {
                for (JsonNode d : norm.diagnostics()) judges.add(judgment(branch, d.asText(), segId, norm));
            }
        }
        ObjectNode curve = st.curve(segId);
        boolean userIntervalExists = false;
        for (ObjectNode iv : st.intervals.values()) {
            if (!segId.equals(iv.path("segmentId").asText())) continue;
            userIntervalExists = true;
            Reaction r = resolveInterval(branch, st, curve, norm, iv);
            ders.addAll(r.derivations());
            judges.addAll(r.judgments());
        }
        if (!userIntervalExists && norm != null && Derivation.OK.equals(norm.status())) {
            Reaction r = autoDetect(branch, st, curve, norm);
            ders.addAll(r.derivations());
            judges.addAll(r.judgments());
        }
        // propagate gap warnings (non-uniform temperature, kept, not resampled)
        if (norm != null) {
            for (JsonNode g : norm.output().path("nonuniformGaps")) {
                judges.add(judgment(branch,
                        "NONUNIFORM_SPACING: gap after index " + g.path("afterIndex").asInt()
                                + " is " + String.format(Locale.ROOT, "%.1f", g.path("ratio").asDouble())
                                + "x nominal; points retained, integration uses actual dT widths",
                        segId, norm));
            }
        }
        return new Reaction(ders, judges);
    }

    private static Reaction resolveInterval(String branch, State st, ObjectNode curve,
                                            Derivation norm, ObjectNode iv) {
        List<Derivation> ders = new ArrayList<>();
        List<Event> judges = new ArrayList<>();
        String segId = curve.path("id").asText();
        String ivId = iv.path("id").asText();
        List<String> diags = new ArrayList<>();

        if (iv.has("segments") && iv.path("segments").size() > 1) {
            diags.add("SEGMENT_MIX_REJECTED: interval " + ivId + " spans heating and cooling segments; "
                    + "heating and cooling are never mixed in one baseline/integration");
        }
        int lo = iv.path("loIndex").asInt(-1);
        int hi = iv.path("hiIndex").asInt(-1);
        double[] t = temps(norm);
        double[] y = specific(norm);
        if (lo < 0 || hi < 0 || lo >= hi || hi - lo + 1 < 3 || t.length == 0) {
            diags.add("EMPTY_INTERVAL: " + ivId + " contains fewer than 3 samples; no baseline or integral produced");
            Derivation d = plainKey(branch, st, "BASELINE:" + segId + ":" + ivId, "BASELINE",
                    norm, emptyOut(st, segId, iv, "BASELINE"), diags, Derivation.EMPTY,
                    segId, String.valueOf(iv.path("version").asInt(1)));
            ders.add(d);
            for (String s : diags) judges.add(judgment(branch, s, ivId, d));
            return new Reaction(ders, judges);
        }

        int aWin = Math.max(2, iv.path("anchorWindow").asInt(4));
        int leftWin = Math.min(aWin, (hi - lo + 1) / 2);
        int rightWin = leftWin;

        // Candidate 1: linear chord across the whole interval.
        Analysis.Line chord = Analysis.fit(t, y, lo, hi);
        // Candidate 2: two tangent lines over anchor windows, bent (intersection) baseline.
        Analysis.Line lt = Analysis.fit(t, y, lo, lo + leftWin - 1);
        Analysis.Line rt = Analysis.fit(t, y, hi - rightWin + 1, hi);
        double cross = Analysis.intersectX(lt, rt);

        ObjectNode baseOut = st.obj();
        baseOut.put("kind", "BASELINE");
        baseOut.put("@id", "bas:" + segId + ":" + ivId);
        baseOut.put("segmentId", segId);
        baseOut.put("intervalId", ivId);
        baseOut.put("loIndex", lo);
        baseOut.put("hiIndex", hi);
        ObjectNode coord = st.obj();
        coord.put("@id", "bas:" + segId + ":" + ivId + ":basis");
        coord.put("x", "degC calibrated");
        coord.put("y", "W/g specific heat flow (endo +)");
        baseOut.set("coordinateBasis", coord);
        ArrayNode cands = st.array();
        cands.add(baselineCandidate(st, segId, ivId, "CHORD", "linear fit across full interval",
                chord, chord, Double.NaN, lo, hi, t, y));
        cands.add(baselineCandidate(st, segId, ivId, "TANGENT",
                "two tangents over user anchor windows, bent at intersection",
                lt, rt, cross, lo, hi, t, y));
        baseOut.set("candidates", cands);
        ObjectNode adopted = st.obj();
        if (iv.has("adoptedBaseline")) {
            adopted.put("candidateId", iv.path("adoptedBaseline").asText());
            adopted.put("atEventSeq", iv.path("adoptedBaselineAt").asLong());
        } else {
            adopted.putNull("candidateId");
            diags.add("MULTIPLE_BASELINES: both CHORD and TANGENT remain legal; retained side by side until adopted");
        }
        baseOut.set("adopted", adopted);

        String ivStatus = diags.isEmpty() ? Derivation.MIXED : Derivation.INVALID;
        Derivation bd = plainKey(branch, st, "BASELINE:" + segId + ":" + ivId, "BASELINE", norm,
                baseOut, diags, ivStatus, segId, ivId, String.valueOf(iv.path("version").asInt(1)));
        ders.add(bd);
        for (String s : diags) judges.add(judgment(branch, s, ivId, bd));

        // ---- features over the interval ----
        Reaction feat = featureDerivations(branch, st, curve, norm, iv, t, y, lo, hi,
                chord, lt, rt, cross);
        ders.addAll(feat.derivations());
        judges.addAll(feat.judgments());
        return new Reaction(ders, judges);
    }

    private static ObjectNode baselineCandidate(State st, String segId, String ivId, String name,
                                                String rationale, Analysis.Line leftLine,
                                                Analysis.Line rightLine, double cross,
                                                int lo, int hi, double[] t, double[] y) {
        ObjectNode c = st.obj();
        String cid = "bas:" + segId + ":" + ivId + ":" + name;
        c.put("@id", cid);
        c.put("name", name);
        c.put("rationale", rationale);
        ObjectNode l = st.obj();
        l.put("@id", cid + ":left"); l.put("slopeWGpK", leftLine.m()); l.put("interceptWG", leftLine.b());
        ObjectNode r = st.obj();
        r.put("@id", cid + ":right"); r.put("slopeWGpK", rightLine.m()); r.put("interceptWG", rightLine.b());
        c.set("leftLine", l); c.set("rightLine", r);
        if (!Double.isNaN(cross)) c.put("bendAtC", cross);
        double residualSq = 0;
        for (int i = lo; i <= hi; i++) {
            double bl = Double.isNaN(cross) ? leftLine.at(t[i])
                    : (t[i] <= cross ? leftLine.at(t[i]) : rightLine.at(t[i]));
            residualSq += (y[i] - bl) * (y[i] - bl);
        }
        c.put("residualRmsWG", Math.sqrt(residualSq / (hi - lo + 1)));
        return c;
    }

    private static double[] temps(Derivation norm) {
        JsonNode ps = norm.output().path("points");
        double[] t = new double[ps.size()];
        for (int i = 0; i < ps.size(); i++) t[i] = ps.get(i).path("tempC").asDouble(Double.NaN);
        return t;
    }

    private static double[] specific(Derivation norm) {
        JsonNode ps = norm.output().path("points");
        double[] y = new double[ps.size()];
        for (int i = 0; i < ps.size(); i++) y[i] = ps.get(i).path("specificHfWG").asDouble(0.0);
        return y;
    }

    private static ObjectNode emptyOut(State st, String segId, ObjectNode iv, String kind) {
        ObjectNode o = st.obj();
        o.put("kind", kind);
        o.put("@id", kind.toLowerCase(Locale.ROOT) + ":" + segId + ":" + iv.path("id").asText());
        o.put("segmentId", segId);
        o.put("intervalId", iv.path("id").asText());
        o.put("empty", true);
        return o;
    }

    private static Derivation plainKey(String branch, State st, String key, String kind,
                                       Derivation norm, ObjectNode out, List<String> diags,
                                       String status, String... content) {
        ObjectNode dg = st.obj();
        for (int i = 0; i < diags.size(); i++) dg.put("d" + i, diags.get(i));
        ArrayNode depArr = State.M.valueToTree(List.of(norm.id()));
        String id = Ids.sha("der", RULE_VERSION, key, norm.id(),
                String.join("|", content), out.toString(), dg.toString());
        return new Derivation(id, key, kind, -1, branch, RULE_VERSION, depArr.toString(),
                out, dg, status);
    }

    private static Reaction featureDerivations(String branch, State st, ObjectNode curve,
                                               Derivation norm, ObjectNode iv, double[] t,
                                               double[] y, int lo, int hi, Analysis.Line chord,
                                               Analysis.Line lt, Analysis.Line rt, double cross) {
        List<Derivation> ders = new ArrayList<>();
        List<Event> judges = new ArrayList<>();
        String segId = curve.path("id").asText();
        String ivId = iv.path("id").asText();
        List<String> diags = new ArrayList<>();

        // residual against chord; detect peak extrema and glass-like steps
        double[] res = new double[t.length];
        for (int i = lo; i <= hi; i++) res[i] = y[i] - chord.at(t[i]);
        int peakIdx = -1;
        double bestAbs = 0;
        for (int i = lo + 1; i < hi; i++) {
            if (Math.abs(res[i]) > bestAbs && (res[i] >= res[i - 1] && res[i] >= res[i + 1]
                    || res[i] <= res[i - 1] && res[i] <= res[i + 1])) {
                bestAbs = Math.abs(res[i]); peakIdx = i;
            }
        }
        double preMean = 0, postMean = 0;
        int w = Math.max(2, iv.path("anchorWindow").asInt(4));
        for (int i = lo; i < lo + w && i <= hi; i++) preMean += res[i];
        for (int i = hi - w + 1; i <= hi; i++) postMean += res[i];
        preMean /= Math.min(w, hi - lo + 1);
        postMean /= Math.min(w, hi - lo + 1);
        double stepSize = postMean - preMean;
        double spanResidRms = rms(res, lo, hi);
        boolean isPeak = peakIdx >= 0 && bestAbs > 0.15 && bestAbs > 2.5 * spanResidRms;
        boolean isStep = Math.abs(stepSize) > 0.05 && bestAbs < 6.0 * Math.abs(stepSize);

        // overlap with another user interval on the same segment
        for (ObjectNode other : st.intervals.values()) {
            if (!segId.equals(other.path("segmentId").asText())) continue;
            if (other.path("id").asText().equals(ivId)) continue;
            int olo = other.path("loIndex").asInt(-1), ohi = other.path("hiIndex").asInt(-1);
            if (olo <= hi && ohi >= lo) {
                diags.add("OVERLAP_TRANSITION: interval " + ivId + " overlaps " + other.path("id").asText()
                        + " on " + segId + "; both integrals kept, shared samples flagged in the error budget");
            }
        }

        String adoptedCandidate = iv.has("adoptedBaseline") ? iv.path("adoptedBaseline").asText() : null;
        ArrayNode features = st.array();

        if (isPeak) {
            // onset/endset from tangent intersections around the peak limb
            int riseStart = peakIdx;
            while (riseStart > lo + 2 && res[riseStart - 1] * res[peakIdx] > 0
                    && Math.abs(res[riseStart - 1]) > 0.15 * bestAbs) riseStart--;
            int fallEnd = peakIdx;
            while (fallEnd < hi - 2 && res[fallEnd + 1] * res[peakIdx] > 0
                    && Math.abs(res[fallEnd + 1]) > 0.15 * bestAbs) fallEnd++;
            Analysis.Line limbL = Analysis.fit(t, res, Math.max(lo, riseStart - 2), Math.min(hi, riseStart + 2));
            Analysis.Line limbR = Analysis.fit(t, res, Math.max(lo, fallEnd - 2), Math.min(hi, fallEnd + 2));
            double onset = Analysis.intersectX(chord, shifted(limbL, chord, t[Math.max(lo, riseStart - 2)]));
            double endset = Analysis.intersectX(chord, shifted(limbR, chord, t[Math.min(hi, fallEnd + 2)]));
            onset = clamp(onset, t[lo], t[hi]);
            endset = clamp(endset, t[lo], t[hi]);

            for (String candName : new String[]{"CHORD", "TANGENT"}) {
                features.add(peakFeature(st, segId, ivId, candName, norm, curve, iv,
                        lo, hi, t, y, peakIdx, onset, endset,
                        candName.equals("CHORD") ? chord : lt, candName.equals("CHORD") ? chord : rt,
                        cross, adoptedCandidate, diags));
            }
            double dhChord = features.get(0).path("enthalpyJG").asDouble();
            double dhTang = features.get(1).path("enthalpyJG").asDouble();
            if (adoptedCandidate == null) {
                judges.add(judgment(branch,
                        "MULTIPLE_BASELINES: peak enthalpy differs across legal baselines CHORD="
                                + fmt3(dhChord) + " J/g vs TANGENT=" + fmt3(dhTang)
                                + " J/g; difference " + fmt3(Math.abs(dhChord - dhTang))
                                + " J/g retained until a reviewer adopts one",
                        ivId, norm));
            }
        }
        if (isStep) {
            features.add(stepFeature(st, segId, ivId, norm, t, y, lo, hi, chord, cross, lt, rt));
        }
        if (!isPeak && !isStep) {
            diags.add("NO_FEATURE: no resolvable peak or step in " + ivId
                    + " (peak amplitude " + fmt3(bestAbs) + " W/g, step " + fmt3(stepSize) + " W/g)");
        }

        ObjectNode out = st.obj();
        out.put("kind", "EVENT");
        out.put("@id", "evt:" + segId + ":" + ivId);
        out.put("segmentId", segId);
        out.put("intervalId", ivId);
        out.put("direction", norm.output().path("direction").asText());
        out.set("features", features);
        ObjectNode decision = st.obj();
        if (iv.has("acceptedFeature")) {
            decision.put("featureId", iv.path("acceptedFeature").asText());
            decision.put("atEventSeq", iv.path("acceptedFeatureAt").asLong());
            decision.put("state", "ACCEPTED");
        } else if (iv.has("rejectedFeature")) {
            decision.put("featureId", iv.path("rejectedFeature").asText());
            decision.put("atEventSeq", iv.path("rejectedFeatureAt").asLong());
            decision.put("state", "REJECTED");
        } else decision.put("state", "UNDECIDED");
        out.set("review", decision);

        String status = Derivation.OK;
        if (!diags.isEmpty()) status = Derivation.MIXED;
        Derivation ed = plainKey(branch, st, "EVENT:" + segId + ":" + ivId, "EVENT", norm,
                out, diags, status, segId, ivId,
                String.valueOf(iv.path("version").asInt(1)),
                iv.path("adoptedBaseline").asText("none"),
                iv.path("acceptedFeature").asText("none"),
                iv.path("rejectedFeature").asText("none"));
        ders.add(ed);
        for (String s : diags) judges.add(judgment(branch, s, ivId, ed));
        return new Reaction(ders, judges);
    }

    private static Analysis.Line shifted(Analysis.Line limb, Analysis.Line chord, double xAt) {
        // limb fitted to residual (zero level); for intersection with chord shift it up
        return new Analysis.Line(limb.m(), limb.b() + chord.at(xAt));
    }

    private static double rms(double[] res, int lo, int hi) {
        double s = 0; int n = 0;
        for (int i = lo; i <= hi; i++) { s += res[i] * res[i]; n++; }
        return Math.sqrt(s / n);
    }

    private static double clamp(double v, double lo, double hi) {
        if (Double.isNaN(v)) return v;
        return Math.max(lo, Math.min(hi, v));
    }

    private static double enthalpyForCandidate(String cand, double[] t, double[] y, int lo, int hi,
                                               Analysis.Line chord, Analysis.Line lt,
                                               Analysis.Line rt, double cross, boolean endo) {
        double dh = 0;
        for (int i = lo + 1; i <= hi; i++) {
            double b1 = baselineAt(cand, t[i - 1], chord, lt, rt, cross);
            double b2 = baselineAt(cand, t[i], chord, lt, rt, cross);
            double dT = Math.abs(t[i] - t[i - 1]);
            dh += 0.5 * ((y[i - 1] - b1) + (y[i] - b2)) * dT;
        }
        return endo ? Math.abs(dh) : -Math.abs(dh);
    }

    private static double baselineAt(String cand, double temp, Analysis.Line chord,
                                     Analysis.Line lt, Analysis.Line rt, double cross) {
        if ("TANGENT".equals(cand) && !Double.isNaN(cross)) {
            return temp <= cross ? lt.at(temp) : rt.at(temp);
        }
        return chord.at(temp);
    }

    private static ObjectNode peakFeature(State st, String segId, String ivId, String candName,
                                          Derivation norm, ObjectNode curve, ObjectNode iv,
                                          int lo, int hi, double[] t, double[] y, int peakIdx,
                                          double onset, double endset, Analysis.Line chord,
                                          Analysis.Line lt, Analysis.Line rt, double cross,
                                          String adoptedCandidate, List<String> diags) {
        String fid = "feat:" + segId + ":" + ivId + ":" + candName;
        ObjectNode f = st.obj();
        f.put("@id", fid);
        f.put("kind", "PEAK");
        f.put("baselineCandidate", candName);
        boolean endo = true;
        double signedPeakHf = norm.output().path("points").get(peakIdx).path("signedHfMW").asDouble();
        endo = signedPeakHf >= 0;
        f.put("thermalSense", endo ? "ENDOTHERMIC" : "EXOTHERMIC");

        putValue(st, f, "onset", "temp:" + segId + ":" + ivId + ":" + candName + ":onset",
                onset, "degC", "intersection of leading tangent with baseline; calibrated");
        putValue(st, f, "peak", "temp:" + segId + ":" + ivId + ":" + candName + ":peak",
                t[peakIdx], "degC", "temperature at extremum; calibrated");
        putValue(st, f, "endset", "temp:" + segId + ":" + ivId + ":" + candName + ":endset",
                endset, "degC", "intersection of trailing tangent with baseline; calibrated");

        // signed trapezoidal integral over actual (possibly non-uniform) dT widths.
        double dh = enthalpyForCandidate(candName, t, y, lo, hi, chord, lt, rt, cross, endo);
        putValue(st, f, "enthalpy", "dh:" + segId + ":" + ivId + ":" + candName,
                dh, "J/g", "signed integral (endo positive), actual dT widths, mass-normalized");
        double ownDh = f.path("enthalpy").path("value").asDouble();
        double otherDh = enthalpyForCandidate("CHORD".equals(candName) ? "TANGENT" : "CHORD",
                t, y, lo, hi, chord, lt, rt, cross, endo);
        errorBudget(st, f, fid, norm, curve, iv, lo, hi, y, ownDh, otherDh);
        f.put("adopted", candName.equals(adoptedCandidate) ||
                (adoptedCandidate != null && adoptedCandidate.endsWith(":" + candName)));
        return f;
    }

    private static ObjectNode stepFeature(State st, String segId, String ivId, Derivation norm,
                                          double[] t, double[] y, int lo, int hi,
                                          Analysis.Line chord, double cross,
                                          Analysis.Line lt, Analysis.Line rt) {
        String fid = "feat:" + segId + ":" + ivId + ":STEP";
        ObjectNode f = st.obj();
        f.put("@id", fid);
        f.put("kind", "STEP");
        f.put("baselineCandidate", Double.isNaN(cross) ? "CHORD" : "TANGENT");
        int mid = (lo + hi) / 2;
        double tgMid = Analysis.intersectX(lt, rt);
        double tg = Double.isNaN(tgMid) ? t[mid] : tgMid;
        putValue(st, f, "onset", "temp:" + segId + ":" + ivId + ":STEP:onset", t[lo], "degC",
                "left edge of step window; calibrated");
        putValue(st, f, "peak", "temp:" + segId + ":" + ivId + ":STEP:mid", tg, "degC",
                "midpoint / tangent intersection Tg; calibrated");
        putValue(st, f, "endset", "temp:" + segId + ":" + ivId + ":STEP:endset", t[hi], "degC",
                "right edge of step window; calibrated");
        int w = Math.max(2, (hi - lo + 1) / 4);
        double pre = avg(y, lo, lo + w - 1), post = avg(y, hi - w + 1, hi);
        double deltaCp = post - pre;
        putValue(st, f, "deltaCp", "dcp:" + segId + ":" + ivId + ":STEP", deltaCp, "W/g",
                "difference of mean specific heat flow across the glass step");
        ObjectNode note = st.obj();
        note.put("@id", "dh:" + segId + ":" + ivId + ":STEP:none");
        note.put("display", "—");
        note.put("unit", "J/g");
        note.put("source", "glass transition reports deltaCp, not an enthalpy");
        f.set("enthalpy", note);
        ObjectNode eb = st.obj();
        eb.put("@id", fid + ":errors");
        ObjectNode massE = st.obj();
        massE.put("@id", fid + ":errors:cpMass");
        massE.put("unit", "W/g");
        massE.put("sourceId", norm.output().path("massId").isNull() ? "" : norm.output().path("massId").asText());
        massE.put("value", Math.abs(deltaCp) * 0.001);
        eb.set("massScaling", massE);
        f.set("errorBudget", eb);
        f.put("thermalSense", "GLASS_TRANSITION");
        f.put("adopted", false);
        return f;
    }

    private static void errorBudget(State st, ObjectNode f, String fid, Derivation norm,
                                    ObjectNode curve, ObjectNode iv, int lo, int hi,
                                    double[] y, double ownEnthalpy, double otherEnthalpy) {
        ObjectNode eb = st.obj();
        eb.put("@id", fid + ":errors");

        double spread = Math.abs(otherEnthalpy - ownEnthalpy);
        ObjectNode baseE = st.obj();
        baseE.put("@id", fid + ":errors:baselineModel");
        baseE.put("unit", "J/g");
        baseE.put("value", spread);
        baseE.put("source", "difference between the retained CHORD and TANGENT candidates");
        eb.set("baselineModel", baseE);

        double gapUnc = 0;
        for (JsonNode g : norm.output().path("nonuniformGaps")) {
            int after = g.path("afterIndex").asInt();
            if (after >= lo - 1 && after < hi) {
                double dExtra = g.path("tempDeltaC").asDouble() - g.path("nominalDeltaC").asDouble();
                gapUnc += Math.abs(y[after + 1] - y[after]) * dExtra * 0.5;
            }
        }
        ObjectNode gapE = st.obj();
        gapE.put("@id", fid + ":errors:nonuniform");
        gapE.put("unit", "J/g");
        gapE.put("value", gapUnc);
        gapE.put("source", "actual dT widths used; trapezoid error across large non-uniform gaps");
        eb.set("nonuniformSpacing", gapE);

        String massId = norm.output().path("massId").asText("");
        double relMass = 0.001;
        ObjectNode massE = st.obj();
        massE.put("@id", fid + ":errors:mass");
        massE.put("unit", "J/g");
        massE.put("sourceId", massId);
        massE.put("value", Math.abs(ownEnthalpy) * relMass);
        massE.put("source", "relative uncertainty of adopted mass revision " + massId + " (default 0.1%)");
        eb.set("massScaling", massE);

        String segId = curve.path("id").asText();
        String ivId = iv.path("id").asText();
        int overlapSamples = 0;
        for (ObjectNode other : st.intervals.values()) {
            if (!segId.equals(other.path("segmentId").asText())) continue;
            if (other.path("id").asText().equals(ivId)) continue;
            int olo = other.path("loIndex").asInt(-1), ohi = other.path("hiIndex").asInt(-1);
            overlapSamples += Math.max(0, Math.min(hi, ohi) - Math.max(lo, olo) + 1);
        }
        ObjectNode ov = st.obj();
        ov.put("@id", fid + ":errors:overlap");
        ov.put("unit", "samples");
        ov.put("value", overlapSamples);
        ov.put("source", "samples shared with another overlapping interval on the same segment");
        eb.set("overlap", ov);

        double combined = Math.sqrt(spread * spread + gapUnc * gapUnc
                + massE.path("value").asDouble() * massE.path("value").asDouble());
        ObjectNode comb = st.obj();
        comb.put("@id", fid + ":errors:combined");
        comb.put("unit", "J/g");
        comb.put("value", combined);
        comb.put("source", "root-sum-square of baselineModel, nonuniformSpacing and massScaling");
        eb.set("combined", comb);
        f.set("errorBudget", eb);
    }

    private static void putValue(State st, ObjectNode parent, String field, String id,
                                 double value, String unit, String source) {
        ObjectNode v = st.obj();
        v.put("@id", id);
        v.put("value", value);
        v.put("unit", unit);
        v.put("source", source);
        parent.set(field, v);
    }

    private static double avg(double[] y, int lo, int hi) {
        double s = 0;
        for (int i = lo; i <= hi; i++) s += y[i];
        return s / (hi - lo + 1);
    }

    private static String fmt3(double v) { return String.format(Locale.ROOT, "%.3f", v); }

    // ---- automatic candidate detection before any user interval -----------

    private static Reaction autoDetect(String branch, State st, ObjectNode curve, Derivation norm) {
        List<Derivation> ders = new ArrayList<>();
        List<Event> judges = new ArrayList<>();
        String segId = curve.path("id").asText();
        double[] t = temps(norm);
        double[] y = specific(norm);
        if (t.length < 12) return new Reaction(ders, judges);
        Analysis.Line global = Analysis.fit(t, y, 0, t.length - 1);
        double[] res = new double[t.length];
        for (int i = 0; i < t.length; i++) res[i] = y[i] - global.at(t[i]);
        List<Integer> extrema = Analysis.localExtrema(res, 8);
        double rmsAll = rms(res, 0, t.length - 1);
        int found = 0;
        for (int px : extrema) {
            if (Math.abs(res[px]) < 3.0 * rmsAll) continue;
            int lo = Math.max(0, px - 8), hi = Math.min(t.length - 1, px + 8);
            ObjectNode autoIv = st.obj();
            String ivId = "auto:" + segId + ":" + found;
            autoIv.put("id", ivId);
            autoIv.put("segmentId", segId);
            autoIv.put("loIndex", lo);
            autoIv.put("hiIndex", hi);
            autoIv.put("anchorWindow", 4);
            autoIv.put("version", 1);
            autoIv.put("automatic", true);
            // provisional interval: it is a *candidate*, never silently adopted
            ObjectNode note = st.obj();
            note.put("@id", "autodetect:" + segId + ":" + found);
            note.put("message", "auto-detected candidate interval; reviewer must draw/adopt on the curve page");
            note.put("peakIndex", px);
            note.put("unit", "degC");
            note.put("peakTempC", t[px]);
            ders.add(new Derivation(
                    Ids.sha("der", RULE_VERSION, "AUTODETECT", segId, ivId, String.valueOf(px)),
                    "AUTODETECT:" + segId + ":" + found, "AUTODETECT", -1, branch, RULE_VERSION,
                    depList(norm.id()), note, st.obj(), Derivation.OK));
            judges.add(judgment(branch,
                    "CANDIDATE_DETECTED: provisional feature near " + fmt3(t[px])
                            + " degC on " + segId + "; kept as an unadopted candidate",
                    ivId, norm));
            found++;
        }
        return new Reaction(ders, judges);
    }

    // ---- judgments shared across the engine --------------------------------

    private static Event judgment(String branch, String code, String targetId, Derivation d) {
        ObjectNode p = State.M.createObjectNode();
        p.put("code", code);
        p.put("targetId", targetId);
        p.put("ruleVersion", RULE_VERSION);
        p.put("derivationId", d == null ? null : d.id());
        p.put("derivationStatus", d == null ? null : d.status());
        String id = Ids.sha("evt", "J", branch, code, targetId, d == null ? "" : d.id());
        return new Event(id, -1, Event.JUDGMENT, branch, null, -1, "engine",
                0, targetId, p);
    }

    /**
     * Whole-sample checks that need more than one segment:
     * direction/file-name and heating-vs-cooling mixing rules.
     */
    public static List<Event> sampleJudgments(String branch, State st, String sampleId) {
        List<Event> judges = new ArrayList<>();
        List<ObjectNode> curves = st.curvesOf(sampleId);
        for (ObjectNode c : curves) {
            String segId = c.path("id").asText();
            String fileName = c.path("fileName").asText("");
            Direction parsed = Direction.parse(c.path("directionRaw").asText(""));
            String lower = fileName.toLowerCase(Locale.ROOT);
            Direction nameGuess = (lower.contains("heat") && !lower.contains("cool")) ? Direction.HEATING
                    : (lower.contains("cool") ? Direction.COOLING : Direction.UNKNOWN);
            if (nameGuess != Direction.UNKNOWN && parsed != Direction.UNKNOWN && nameGuess != parsed) {
                ObjectNode p = State.M.createObjectNode();
                p.put("code", "FILENAME_DIRECTION_MISMATCH: file '" + fileName
                        + "' suggests " + nameGuess + " but header declares " + parsed
                        + "; header wins, file name is never used for endo/exo or direction");
                p.put("targetId", segId);
                p.put("ruleVersion", RULE_VERSION);
                judges.add(new Event(
                        Ids.sha("evt", "J", branch, "NAMEMISMATCH", segId, fileName),
                        -1, Event.JUDGMENT, branch, null, -1, "engine", 0, segId, p));
            }
        }
        for (ObjectNode iv : st.intervals.values()) {
            JsonNode segs = iv.path("segments");
            if (segs.isArray() && segs.size() > 1) {
                ObjectNode p = State.M.createObjectNode();
                String code = "SEGMENT_MIX_REJECTED: interval " + iv.path("id").asText()
                        + " joins heating and cooling segments; baselines/integrals are never mixed";
                p.put("code", code);
                p.put("targetId", iv.path("id").asText());
                p.put("ruleVersion", RULE_VERSION);
                judges.add(new Event(
                        Ids.sha("evt", "J", branch, "MIX", iv.path("id").asText()),
                        -1, Event.JUDGMENT, branch, null, -1, "engine", 0,
                        iv.path("id").asText(), p));
            }
        }
        return judges;
    }
}
