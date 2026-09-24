package dsc.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 纯函数派生引擎：在 CaseState 上计算归一化曲线、基线候选与相变结果。
 * 规则版本写死在 RULE_VERSION；规则改动使所有指纹失配。
 * 不做任何持久化；多解与不确定全部保留，不为单一答案吞掉候选。
 */
public final class DerivationEngine {

    public static final String RULE_VERSION = "dsc-rules-1.0.0";

    private DerivationEngine() {}

    /** 一次完整派生：成功后由存储层整体切换当前指针。 */
    public static Derivation deriveAll(CaseState st) {
        Derivation d = new Derivation();
        for (Segment seg : st.segments.values()) {
            NormalizedCurve curve = normalize(st, seg);
            d.curves.put(seg.id(), curve);
            d.fingerprints.put("curve:" + seg.id(),
                    Fingerprint.of(RULE_VERSION, List.of(
                            "seg:" + seg.id() + ":" + Fingerprint.sha256(rawSegmentHash(seg)),
                            "tcal:" + safe(st.currentTempCalId),
                            "hfcal:" + safe(st.currentHfCalId))));
        }
        for (TangentSelection t : st.tangents.values()) {
            d.tangents.put(t.id(), t);
        }
        for (AnalysisWindow w : st.windows.values()) {
            WindowResult wr = analyzeWindow(st, d, w);
            d.windows.put(w.id(), wr);
            List<String> dep = new ArrayList<>();
            dep.add("win:" + w.id() + ":v" + w.version());
            dep.add("curve:" + w.segmentId());
            dep.add("mass:" + safe(st.currentMassId));
            for (String tid : tangentIdsFor(d, w.segmentId())) dep.add("tangent:" + tid);
            d.fingerprints.put("window:" + w.id(), Fingerprint.of(RULE_VERSION, dep));
        }
        return d;
    }

    private static String safe(String s) { return s == null ? "none" : s; }

    private static List<String> tangentIdsFor(Derivation d, String segmentId) {
        List<String> out = new ArrayList<>();
        for (TangentSelection t : d.tangents.values()) {
            if (t.segmentId().equals(segmentId)) out.add(t.id());
        }
        return out;
    }

    private static String rawSegmentHash(Segment seg) {
        StringBuilder sb = new StringBuilder();
        sb.append(seg.cycle()).append('|').append(seg.signConvention()).append('|')
          .append(seg.rateKPerMin()).append('|').append(seg.pointCount());
        for (SamplePoint p : seg.points()) {
            sb.append(p.tSec.raw()).append(',').append(p.tempC().raw()).append(',')
              .append(p.hfMw().raw()).append(';');
        }
        return Fingerprint.sha256(sb.toString());
    }

    // ---------------------------------------------------------------- normalize

    static NormalizedCurve normalize(CaseState st, Segment seg) {
        List<NormalizedCurve.Point> out = new ArrayList<>();
        List<String> diags = new ArrayList<>();
        TempCalibration tcal = st.tempCal();
        HeatFlowCalibration hfcal = st.hfCal();
        boolean reversed = "exo_up".equals(seg.signConvention());
        int badCount = 0;
        for (SamplePoint p : seg.points()) {
            boolean valid = true;
            StringBuilder diag = new StringBuilder();
            double tempC = Double.NaN;
            double hf = Double.NaN;
            if (p.tempBad() || Double.isNaN(p.temp())) {
                valid = false;
                diag.append("温度不可转换: ").append(p.tempC().raw())
                    .append(" ").append(p.tempC().unit()).append("; ");
            } else {
                tempC = tcal == null ? p.temp() : tcal.apply(p.temp());
            }
            if (p.hfBad() || Double.isNaN(p.hf())) {
                valid = false;
                diag.append("热流不可转换: ").append(p.hfMw().raw())
                    .append(" ").append(p.hfMw().unit()).append("; ");
            } else {
                hf = hfcal == null ? p.hf() : hfcal.apply(p.hf());
                // 统一为“吸热为正”：exo_up 仪器取反；绝不看文件名。
                if (reversed) hf = -hf;
            }
            if (!valid) badCount++;
            out.add(new NormalizedCurve.Point(p.t(), tempC, hf, valid, diag.toString()));
        }
        if (reversed) {
            diags.add("反向热流约定(exo_up)已按吸正规则取反；方向来自元数据而非文件名");
        }
        if (badCount > 0) {
            diags.add(badCount + " 个点无法转换，保留原始表示并排除出数值积分");
        }
        if (seg.direction() == Direction.UNKNOWN) {
            diags.add("扫描方向无法由速率决定（UNKNOWN），窗口积分将被阻止");
        }
        return new NormalizedCurve(seg.id(), seg.direction(), out, diags);
    }

    // ------------------------------------------------------------ window math

    static WindowResult analyzeWindow(CaseState st, Derivation d, AnalysisWindow w) {
        List<BaselineCandidate> cands = new ArrayList<>();
        List<Transition> trans = new ArrayList<>();
        List<String> diags = new ArrayList<>();
        List<Anomaly> anomalies = new ArrayList<>();

        NormalizedCurve curve = d.curves.get(w.segmentId());
        Segment seg = st.segments.get(w.segmentId());
        double lo = Math.min(w.fromC(), w.toC());
        double hi = Math.max(w.fromC(), w.toC());

        List<NormalizedCurve.Point> win = new ArrayList<>();
        for (NormalizedCurve.Point p : curve.points()) {
            if (p.valid() && p.tempC() >= lo - 1e-9 && p.tempC() <= hi + 1e-9) {
                win.add(p);
            }
        }

        // 空区间：没有任何合法点 -> 不产出基线/积分，阻止派生并记录。
        if (win.isEmpty()) {
            anomalies.add(new Anomaly("empty_interval", "error", "blocked",
                    "window", w.id(),
                    "区间 [" + w.fromC() + ", " + w.toC() + "] °C 内无合法采样点，基线与积分被阻止"));
            diags.add("empty interval: 无法唯一决定任何基线");
            return new WindowResult(w.id(), w.segmentId(), w.fromC(), w.toC(),
                    cands, trans, diags, anomalies, null);
        }

        double[] T = new double[win.size()];
        double[] H = new double[win.size()];
        double[] S = new double[win.size()];
        for (int i = 0; i < win.size(); i++) {
            T[i] = win.get(i).tempC();
            H[i] = win.get(i).hfMw();
            S[i] = win.get(i).tSec();
        }

        // 非等间隔温度检测（非单调/步长方差过大）。
        detectNonUniform(T, w, anomalies, diags);

        // 循环段切换：窗口所属段与其它循环不同，只提示隔离范围（积分绝不跨段）。
        for (Segment other : st.segments.values()) {
            if (!other.id().equals(seg.id()) && other.cycle() != seg.cycle()) {
                anomalies.add(new Anomaly("cycle_switch", "info", "detected",
                        "segment", seg.id(),
                        "循环 " + seg.cycle() + " 与循环 " + other.cycle()
                                + " 分别积分；加热/冷却段不混用"));
                break;
            }
        }
        if (seg.direction() == Direction.UNKNOWN) {
            anomalies.add(new Anomaly("direction_unknown", "error", "blocked",
                    "window", w.id(), "方向未定，积分被阻止（禁止跨加热/冷却段）"));
        }

        // --- 并列基线候选：全部合法候选都保留，差异不被吞掉 ---
        cands.add(linearBaseline(w.id(), T, H));
        cands.add(poly2Baseline(w.id(), T, H));
        TangentSelection tangent = latestTangent(st, w.segmentId());
        if (tangent != null) {
            BaselineCandidate tb = tangentBaseline(w.id(), T, H, tangent);
            if (tb != null) cands.add(tb);
        }
        boolean multiAdmissible = cands.stream().filter(BaselineCandidate::admissible).count() > 1;
        if (multiAdmissible) {
            anomalies.add(new Anomaly("parallel_baselines", "info", "retained",
                    "window", w.id(),
                    cands.stream().filter(BaselineCandidate::admissible).count()
                            + " 条合理基线并列保留，采纳其一不覆盖其余"));
        }

        // --- 每个候选各自积分：多解在焓值上可见 ---
        Double massMg = st.currentMass() == null ? null : st.currentMass().mg();
        for (BaselineCandidate c : cands) {
            Transition t = integrate(w, seg, T, H, S, c, massMg, anomalies);
            trans.add(t);
        }

        // 重叠转折检测（残差中出现两个明显峰）。
        BaselineCandidate ref = cands.get(0);
        if (ref.admissible()) detectOverlap(T, H, ref, w, anomalies);

        // 质量版本：缺失/不可换算时归一化派生过期（此处直接不给比焓）。
        if (massMg == null || Double.isNaN(massMg) || massMg <= 0) {
            anomalies.add(new Anomaly("mass_version_changed", "warning", "detected",
                    "window", w.id(),
                    "缺少有效质量版本，比焓(J/g)不可得；质量修订后所有归一化派生须重算"));
        }

        String accepted = st.acceptedBaseline.get(w.id());
        return new WindowResult(w.id(), w.segmentId(), w.fromC(), w.toC(),
                cands, trans, diags, anomalies, accepted);
    }

    private static TangentSelection latestTangent(CaseState st, String segmentId) {
        TangentSelection best = null;
        for (TangentSelection t : st.tangents.values()) {
            if (t.segmentId().equals(segmentId)) best = t;
        }
        return best;
    }

    // -------------------------------------------------------------- detectors

    private static void detectNonUniform(double[] T, AnalysisWindow w,
                                         List<Anomaly> anomalies, List<String> diags) {
        if (T.length < 3) return;
        double mean = 0;
        int n = T.length - 1;
        boolean nonMono = false;
        double[] d = new double[n];
        for (int i = 0; i < n; i++) {
            d[i] = T[i + 1] - T[i];
            if (d[i] <= 0) nonMono = true;
            mean += d[i];
        }
        mean /= n;
        double var = 0;
        for (double x : d) var += (x - mean) * (x - mean);
        var /= n;
        double cv = mean != 0 ? Math.sqrt(var) / Math.abs(mean) : 1e9;
        if (nonMono || cv > 0.35) {
            anomalies.add(new Anomaly("nonuniform_temp", "warning", "detected",
                    "window", w.id(),
                    "温度间隔非等间隔/非单调（CV=" + String.format("%.2f", cv)
                            + "）；积分按实际采样点梯形累加，不假设等间隔"));
            diags.add("non-uniform temperature grid handled with trapezoid integration");
        }
    }

    private static void detectOverlap(double[] T, double[] H, BaselineCandidate ref,
                                      AnalysisWindow w, List<Anomaly> anomalies) {
        double[] resid = new double[T.length];
        for (int i = 0; i < T.length; i++) resid[i] = H[i] - ref.hfMw()[i];
        int peaks = 0;
        boolean above = false;
        double sd = sd(resid);
        for (int i = 1; i < resid.length - 1; i++) {
            boolean local = resid[i] > resid[i - 1] && resid[i] >= resid[i + 1]
                    && resid[i] > 2.0 * sd;
            if (local && !above) { peaks++; above = true; }
            if (resid[i] < 0.5 * sd) above = false;
        }
        int peaksNeg = 0;
        above = false;
        for (int i = 1; i < resid.length - 1; i++) {
            boolean local = resid[i] < resid[i - 1] && resid[i] <= resid[i + 1]
                    && resid[i] < -2.0 * sd;
            if (local && !above) { peaksNeg++; above = true; }
            if (resid[i] > -0.5 * sd) above = false;
        }
        if (peaks >= 2 || peaksNeg >= 2 || (peaks >= 1 && peaksNeg >= 1)) {
            anomalies.add(new Anomaly("overlapping_transitions", "warning", "retained",
                    "window", w.id(),
                    "窗口内存在重叠转折（残差峰数 " + (peaks + peaksNeg)
                            + "）；保留未分离的整体积分并标注，不强行拆成单解"));
        }
    }

    private static double sd(double[] x) {
        if (x.length == 0) return 0;
        double m = 0;
        for (double v : x) m += v;
        m /= x.length;
        double s = 0;
        for (double v : x) s += (v - m) * (v - m);
        return Math.sqrt(s / x.length);
    }

    // --------------------------------------------------------------- baselines

    private static BaselineCandidate linearBaseline(String windowId, double[] T, double[] H) {
        int n = T.length;
        double h0 = n == 1 ? H[0] : H[0];
        double h1 = n == 1 ? H[0] : H[n - 1];
        double t0 = T[0], t1 = T[n - 1];
        double slope = n == 1 || t1 == t0 ? 0 : (h1 - h0) / (t1 - t0);
        double[] B = new double[n];
        for (int i = 0; i < n; i++) B[i] = h0 + slope * (T[i] - t0);
        return new BaselineCandidate(windowId + "-bl-linear", windowId, "linear_endpoints",
                T, B, new ArrayList<>(), rms(H, B), true, null);
    }

    private static BaselineCandidate poly2Baseline(String windowId, double[] T, double[] H) {
        int n = T.length;
        double[] B = new double[n];
        boolean admissible = n >= 3;
        List<String> diags = new ArrayList<>();
        if (admissible) {
            double[] c = fitPoly2(T, H);
            for (int i = 0; i < n; i++) B[i] = c[0] + c[1] * T[i] + c[2] * T[i] * T[i];
        } else {
            for (int i = 0; i < n; i++) B[i] = H[i];
            diags.add("点数不足 3，二次基线不可用");
        }
        return new BaselineCandidate(windowId + "-bl-poly2", windowId, "polynomial2",
                T, B, diags, admissible ? rms(H, B) : Double.POSITIVE_INFINITY,
                admissible, null);
    }

    private static BaselineCandidate tangentBaseline(String windowId, double[] T, double[] H,
                                                     TangentSelection tg) {
        Double pre = meanLine(T, H, tg.preLeftC(), tg.preRightC());
        Double post = meanLine(T, H, tg.postLeftC(), tg.postRightC());
        List<String> diags = new ArrayList<>();
        double[] B = new double[T.length];
        if (pre == null || post == null) {
            diags.add("切线锚点区间缺少采样点，切线台阶基线不可用（候选保留但不可采纳）");
            for (int i = 0; i < T.length; i++) B[i] = H[i];
            return new BaselineCandidate(windowId + "-bl-tangent", windowId, "tangent_step",
                    T, B, diags, Double.POSITIVE_INFINITY, false, tg.id());
        }
        double mid = (midpoint(tg.preLeftC(), tg.preRightC())
                + midpoint(tg.postLeftC(), tg.postRightC())) / 2.0;
        for (int i = 0; i < T.length; i++) {
            B[i] = T[i] < mid ? pre : post;
        }
        return new BaselineCandidate(windowId + "-bl-tangent", windowId, "tangent_step",
                T, B, diags, rms(H, B), true, tg.id());
    }

    private static double midpoint(double a, double b) { return (a + b) / 2.0; }

    private static Double meanLine(double[] T, double[] H, double lo, double hi) {
        double l = Math.min(lo, hi), h = Math.max(lo, hi);
        double sum = 0;
        int k = 0;
        for (int i = 0; i < T.length; i++) {
            if (T[i] >= l && T[i] <= h) { sum += H[i]; k++; }
        }
        return k == 0 ? null : sum / k;
    }

    private static double[] fitPoly2(double[] x, double[] y) {
        int n = x.length;
        double sx = 0, sx2 = 0, sx3 = 0, sx4 = 0, sy = 0, sxy = 0, sx2y = 0;
        for (int i = 0; i < n; i++) {
            double xi = x[i], yi = y[i];
            sx += xi; sx2 += xi * xi; sx3 += xi * xi * xi; sx4 += xi * xi * xi * xi;
            sy += yi; sxy += xi * yi; sx2y += xi * xi * yi;
        }
        double[][] A = {{n, sx, sx2}, {sx, sx2, sx3}, {sx2, sx3, sx4}};
        double[] b = {sy, sxy, sx2y};
        return solve3(A, b);
    }

    private static double[] solve3(double[][] A, double[] b) {
        for (int p = 0; p < 3; p++) {
            int max = p;
            for (int i = p + 1; i < 3; i++) if (Math.abs(A[i][p]) > Math.abs(A[max][p])) max = i;
            double[] tmp = A[p]; A[p] = A[max]; A[max] = tmp;
            double tv = b[p]; b[p] = b[max]; b[max] = tv;
            if (Math.abs(A[p][p]) < 1e-12) return new double[]{0, 0, 0};
            for (int i = p + 1; i < 3; i++) {
                double f = A[i][p] / A[p][p];
                b[i] -= f * b[p];
                for (int j = p; j < 3; j++) A[i][j] -= f * A[p][j];
            }
        }
        double[] x = new double[3];
        for (int i = 2; i >= 0; i--) {
            double sum = b[i];
            for (int j = i + 1; j < 3; j++) sum -= A[i][j] * x[j];
            x[i] = sum / A[i][i];
        }
        return x;
    }

    private static double rms(double[] a, double[] b) {
        double s = 0;
        for (int i = 0; i < a.length; i++) {
            double d = a[i] - b[i];
            s += d * d;
        }
        return Math.sqrt(s / a.length);
    }

    // -------------------------------------------------------------- integrate

    private static Transition integrate(AnalysisWindow w, Segment seg,
                                        double[] T, double[] H, double[] S,
                                        BaselineCandidate base, Double massMg,
                                        List<Anomaly> windowAnoms) {
        List<ErrorSource> errors = new ArrayList<>();
        List<String> myAnoms = new ArrayList<>();

        // 对时间梯形积分（非等间隔温度也正确）；吸热为正。
        double areaMj = 0;
        for (int i = 1; i < T.length; i++) {
            double dt = S[i] - S[i - 1];
            if (dt < 0) dt = -dt;
            double r0 = H[i - 1] - base.hfMw()[i - 1];
            double r1 = H[i] - base.hfMw()[i];
            areaMj += 0.5 * (r0 + r1) * dt;
        }

        double jPerG = Double.NaN;
        if (massMg != null && !Double.isNaN(massMg) && massMg > 0) {
            jPerG = areaMj / massMg; // mJ / mg == J/g
        }

        // onset/peak/endset 由残差曲线决定。
        int pk = 0;
        double bestAbs = -1;
        for (int i = 0; i < T.length; i++) {
            double r = H[i] - base.hfMw()[i];
            if (Math.abs(r) > bestAbs) { bestAbs = Math.abs(r); pk = i; }
        }
        double sd = sdResidual(H, base);
        double level = Math.max(0.35 * bestAbs, 2.0 * sd);
        int on = pk;
        while (on > 0 && Math.abs(H[on - 1] - base.hfMw()[on - 1]) > level) on--;
        int en = pk;
        while (en < T.length - 1 && Math.abs(H[en + 1] - base.hfMw()[en + 1]) > level) en++;

        String kind = classify(seg.direction(), areaMj, base.method());
        String id = w.id() + "-tr-" + base.method();

        // 误差来源（焓变 J/g），全部带来源身份。
        errors.add(new ErrorSource(base.id(), "baseline",
                "基线方法 " + base.method() + "（候选间差异另见并列候选）", 0));
        if (massMg == null || Double.isNaN(massMg) || massMg <= 0) {
            myAnoms.add("质量缺失");
            errors.add(new ErrorSource("mass", "mass", "无有效质量版本，比焓不可得", Double.NaN));
        } else {
            double dm = 0.05; // 5e-3 kg 天平量级以下的保守估计：0.05 mg
            double mag = Math.abs(areaMj / massMg) * (dm / massMg);
            errors.add(new ErrorSource("mass", "mass",
                    "称量不确定度 ±0.05 mg 传播到比焓", mag));
        }
        if (seg.rateKPerMin() == 0 || Double.isNaN(seg.rateKPerMin())) {
            errors.add(new ErrorSource(seg.id(), "rate", "扫描速率无效，时间积分可靠性下降", Double.NaN));
        }
        // 采样误差：相邻点梯形离散化，按最大步长估算。
        double maxStep = 0;
        for (int i = 1; i < S.length; i++) maxStep = Math.max(maxStep, Math.abs(S[i] - S[i - 1]));
        double sampleMag = (massMg != null && massMg > 0)
                ? bestAbs * maxStep / massMg : Double.NaN;
        errors.add(new ErrorSource(seg.id(), "sampling",
                "梯形积分离散化（最大采样间隔 " + String.format("%.2f", maxStep) + " s）", sampleMag));

        if (!base.admissible()) {
            myAnoms.add("基线不可采纳");
            errors.add(new ErrorSource(base.id(), "baseline",
                    "候选不可采纳（" + String.join(";", base.diagnostics()) + "）", Double.NaN));
        }
        if (seg.direction() == Direction.UNKNOWN) {
            jPerG = Double.NaN;
            myAnoms.add("方向未定");
            windowAnoms.add(new Anomaly("reverse_heatflow", "error", "blocked",
                    "transition", id, "扫描方向未知，吸放热方向不能判定，比焓符号被阻止"));
        }

        return new Transition(id, w.id(), base.id(), kind,
                T[on], T[pk], T[en], jPerG, errors);
    }

    private static double sdResidual(double[] H, BaselineCandidate base) {
        double[] r = new double[H.length];
        for (int i = 0; i < H.length; i++) r[i] = H[i] - base.hfMw()[i];
        return sd(r);
    }

    /**
     * 事件分类：方向来自段元数据，符号来自已统一的“吸热为正”，
     * 绝不从文件名/declaredDirection 猜测。冷却段结晶为负。
     */
    static String classify(Direction dir, double areaMj, String baselineMethod) {
        if (baselineMethod.equals("tangent_step")) {
            // 台阶基线用于玻璃化：阶跃 ΔCp，不按焓峰分类。
            return "glass";
        }
        if (dir == Direction.HEATING) return areaMj >= 0 ? "melting" : "crystallization";
        if (dir == Direction.COOLING) return areaMj <= 0 ? "crystallization" : "melting";
        return "unknown";
    }
}
