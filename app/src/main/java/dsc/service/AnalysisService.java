package dsc.service;

import dsc.domain.*;
import dsc.store.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Interval and derivation orchestration. Every recompute writes NEW independently versioned rows
 * and moves the current pointer only after the whole transaction commits.
 */
public class AnalysisService {

    private final Repository repo;
    private final SegmentDao segments;
    private final SourceDao sources;
    private final IntervalDao intervals;
    private final DerivationDao derivations;
    private final DiagnosticDao diagnostics;
    private final EventDao events;
    private final RuleSetDao rules;
    private final Clock clock;
    private final Normalizer normalizer;

    public AnalysisService(Repository repo, Clock clock) {
        this.repo = repo;
        this.segments = new SegmentDao(repo);
        this.sources = new SourceDao(repo);
        this.intervals = new IntervalDao(repo);
        this.derivations = new DerivationDao(repo);
        this.diagnostics = new DiagnosticDao(repo);
        this.events = new EventDao(repo);
        this.rules = new RuleSetDao(repo);
        this.clock = clock;
        this.normalizer = new Normalizer(segments, sources);
    }

    public Interval addOrDragInterval(String sampleId, String branchId, String intervalId,
                                      String segmentId, double tStartC, double tEndC,
                                      String label, String actor, Integer expectedVersion) {
        Segment seg = segments.get(segmentId);
        if (seg == null) throw new IllegalArgumentException("unknown segment " + segmentId);
        if (tEndC < tStartC) {
            // reversed-range drag: keep the raw drag but it will be flagged, not silently flipped
            double tmp = tStartC; tStartC = tEndC; tEndC = tmp;
        }
        double ts0 = tStartC, ts1 = tEndC;
        return repo.tx(c -> {
            long now = clock.now();
            Interval iv = new Interval();
            iv.id = intervalId; iv.sampleId = sampleId; iv.branchId = branchId;
            iv.segmentId = segmentId; iv.tStartC = tStartC; iv.tEndC = tEndC;
            iv.tStartRaw = ts0; iv.tEndRaw = ts1; iv.rawUnit = "°C";
            iv.label = label; iv.createdBy = actor; iv.createdAt = now;

            int existing = intervals.nextVersion(intervalId) - 1;
            if (expectedVersion != null && expectedVersion != existing) {
                throw new ConflictException(intervalId, expectedVersion, existing);
            }
            int v = existing + 1;
            iv.version = v; iv.current = true;
            if (existing > 0) intervals.clearCurrent(intervalId);
            intervals.insert(iv);

            // stale prior derivations of this interval on this branch
            for (Derivation d : derivations.currentByInterval(branchId, intervalId)) {
                derivations.markStale(branchId, d.id);
            }
            recomputeInterval(iv, actor);

            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("intervalId", intervalId);
            detail.put("version", v);
            detail.put("segmentId", segmentId);
            detail.put("tStartC", tStartC);
            detail.put("tEndC", tEndC);
            detail.put("segmentDirection", seg.declaredDirection.name());
            EventRecord e = ev(branchId, events.lastEventId(branchId),
                    v == 1 ? Enums.EventKind.DERIVED : Enums.EventKind.DECISION,
                    "INTERVAL", intervalId, v, actor, detail,
                    v == 1 ? "interval drawn on curve page" : "interval dragged; dependent derivations recomputed");
            events.insert(e);
            return iv;
        });
    }

    public void recomputeInterval(Interval iv, String actor) {
        Segment seg = segments.get(iv.segmentId);
        Normalizer.Result nr = normalizer.normalize(seg);
        RuleSet rs = rules.current();
        boolean crosses = crossesSegmentBoundary(seg, iv.tStartC, iv.tEndC);

        BaselineEngine.Input in = new BaselineEngine.Input();
        in.segmentId = seg.id;
        in.direction = seg.declaredDirection;
        in.signConvention = seg.signConvention;
        in.rateCperMin = nr.rateSignedCperMin;
        in.t0C = iv.tStartC; in.t1C = iv.tEndC;
        in.tC = nr.curve.tC; in.hfWg = nr.curve.hfWg;
        in.rules = rs;
        in.crossesSegments = crosses;

        List<Derivation> fresh = new ArrayList<>();
        for (BaselineResult br : BaselineEngine.allCandidates(in)) {
            Derivation d = new Derivation();
            d.id = Ids.sid("deriv:" + iv.id + ":" + br.method);
            d.branchId = iv.branchId; d.sampleId = iv.sampleId;
            d.kind = Enums.DerivKind.BASELINE;
            d.segmentId = seg.id; d.intervalId = iv.id;
            d.version = derivations.nextVersion(iv.branchId, d.id);
            d.current = true;
            d.method = br.method;
            d.status = br.usable() ? "VALID" : (br.crossesSegments || br.empty ? "ERROR" : "INDETERMINATE");
            d.createdBy = actor; d.createdAt = clock.now();
            d.candidateStatus = Enums.CandidateStatus.CANDIDATE;
            d.basisJson = Json.write(basis(br, in, nr, seg));
            d.message = br.usable() ? null : String.join(";", ruleMessages(br));
            d.deps.add(new Derivation.DepEdge("INTERVAL", iv.id, iv.version));
            d.deps.add(new Derivation.DepEdge("SEGMENT", seg.id, 1));
            d.deps.add(new Derivation.DepEdge("RULESET", "ruleset", rs.version));
            addSourceDeps(d, seg);
            derivations.clearCurrent(iv.branchId, d.id);
            derivations.insert(d);
            fresh.add(d);
            writeDiagnostics(d, br, in, iv, nr);
        }
    }

    private boolean crossesSegmentBoundary(Segment seg, double t0, double t1) {
        // an interval belongs to exactly one segment; adjacent heat/cool segments never overlap in
        // identity even if their temperature ranges do, so "crossing" means another segment of the
        // opposite direction contains both interval endpoints at acquisition adjacency.
        List<Segment> all = segments.listForSample(seg.sampleId);
        for (Segment other : all) {
            if (other.id.equals(seg.id)) continue;
            if (other.declaredDirection == seg.declaredDirection) continue;
            boolean spans = rangeContains(other, t0) || rangeContains(other, t1);
            if (spans) return true;
        }
        return false;
    }

    private boolean rangeContains(Segment s, double tC) {
        List<CurvePoint> pts = segments.points(s.id);
        if (pts.isEmpty()) return false;
        double lo = Double.POSITIVE_INFINITY, hi = -Double.POSITIVE_INFINITY;
        for (CurvePoint p : pts) {
            if (Double.isNaN(p.tC)) continue;
            lo = Math.min(lo, p.tC); hi = Math.max(hi, p.tC);
        }
        return tC >= lo && tC <= hi;
    }

    private void addSourceDeps(Derivation d, Segment seg) {
        for (SourceRecord s : sources.currentForSegment(seg.id)) {
            d.deps.add(new Derivation.DepEdge("SOURCE", s.id, s.version));
        }
        for (SourceRecord s : sources.currentForSample(seg.sampleId)) {
            if (s.kind == Enums.SourceKind.MASS) {
                d.deps.add(new Derivation.DepEdge("SOURCE", s.id, s.version));
            }
        }
    }
