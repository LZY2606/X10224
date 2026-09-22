package dsc.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 基线引擎：为同一事件窗口生成多个合理候选（线性 / 切线 / S 形），
 * 重叠转折时保留并列候选并给出诊断；采纳与否由人工决定事件记录。
 */
public final class BaselineEngine {

    public record TangentLine(double x1, double y1, double x2, double y2) {
        public double slope() { return (x2 == x1) ? 0.0 : (y2 - y1) / (x2 - x1); }
        public double eval(double x) { return y1 + slope() * (x - x1); }
        /** 两条直线交点 x；平行时返回 NaN。 */
        public static double intersect(TangentLine a, TangentLine b) {
            double ma = a.slope(), mb = b.slope();
            if (Math.abs(ma - mb) < 1e-12) return Double.NaN;
            return (b.y1 - a.y1 + ma * a.x1 - mb * b.x1) / (ma - mb);
        }
    }

    public record Candidate(String type, TangentLine pre, TangentLine post, double joinT, String note) {
        public double eval(double T) {
            return switch (type) {
                case "linear" -> pre.eval(T);
                case "tangential" -> (T <= joinT) ? pre.eval(T) : post.eval(T);
                case "sigmoidal" -> {
                    double w = 1.0 / (1.0 + Math.exp(-(T - joinT) / 1.5));
                    yield (1 - w) * pre.eval(T) + w * post.eval(T);
                }
                default -> pre.eval(T);
            };
        }
    }

    public record Result(List<Candidate> candidates, boolean overlap, String diagnostic) {}

    private BaselineEngine() {}

    /**
     * @param pts      段内点（规范化后）
     * @param t0,t1    窗口温度边界
     * @param userPre/userPost 用户选定切线（可为 null，则用窗口边缘拟合）
     */
    public static Result build(List<DscPoint> pts, double t0, double t1,
                               TangentLine userPre, TangentLine userPost) {
        List<DscPoint> in = window(pts, t0, t1);
        List<Candidate> cands = new ArrayList<>();
        if (in.size() < 4) {
            return new Result(cands, false, "EMPTY_INTERVAL: 窗口 [" + t0 + "," + t1 + "] °C 内数据点不足");
        }
        double lo = Math.min(t0, t1), hi = Math.max(t0, t1);
        TangentLine pre = userPre != null ? userPre : fitEdge(in, lo, true);
        TangentLine post = userPost != null ? userPost : fitEdge(in, hi, false);
        double join = TangentLine.intersect(pre, post);
        if (Double.isNaN(join) || join < lo || join > hi) join = (lo + hi) / 2.0;

        // 线性候选：窗口两端信号值连线
        double y0 = interp(in, lo), y1 = interp(in, hi);
        cands.add(new Candidate("linear", new TangentLine(lo, y0, hi, y1), null, join, "窗口端点连线"));

        cands.add(new Candidate("tangential", pre, post, join,
                (userPre != null ? "用户选定切线" : "窗口边缘拟合切线")));
        cands.add(new Candidate("sigmoidal", pre, post, join, "前后切线 S 形过渡"));

        // 重叠转折检测：线性残差出现多个显著极值
        boolean overlap = countExtrema(in, cands.get(0)) >= 2;
        String diag = overlap
                ? "OVERLAPPING_TRANSITIONS: 窗口内检测到多个显著转折，并列保留全部基线候选"
                : null;
        return new Result(cands, overlap, diag);
    }

    static List<DscPoint> window(List<DscPoint> pts, double t0, double t1) {
        double lo = Math.min(t0, t1), hi = Math.max(t0, t1);
        List<DscPoint> in = new ArrayList<>();
        for (DscPoint p : pts) if (p.tempC() >= lo && p.tempC() <= hi) in.add(p);
        in.sort((a, b) -> Double.compare(a.tempC(), b.tempC()));
        return in;
    }

    private static TangentLine fitEdge(List<DscPoint> in, double edge, boolean leading) {
        int n = Math.max(3, in.size() / 7);
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        int cnt = 0;
        for (int i = 0; i < in.size(); i++) {
            boolean take = leading ? i < n : i >= in.size() - n;
            if (!take) continue;
            DscPoint p = in.get(i);
            sx += p.tempC(); sy += p.heatFlowMw(); sxx += p.tempC() * p.tempC(); sxy += p.tempC() * p.heatFlowMw();
            cnt++;
        }
        double m = (cnt * sxy - sx * sy) / Math.max(1e-12, cnt * sxx - sx * sx);
        double b = (sy - m * sx) / cnt;
        return new TangentLine(edge, m * edge + b, edge + 1, m * (edge + 1) + b);
    }

    private static double interp(List<DscPoint> in, double T) {
        if (T <= in.get(0).tempC()) return in.get(0).heatFlowMw();
        if (T >= in.get(in.size() - 1).tempC()) return in.get(in.size() - 1).heatFlowMw();
        for (int i = 1; i < in.size(); i++) {
            if (in.get(i).tempC() >= T) {
                DscPoint a = in.get(i - 1), b = in.get(i);
                double w = (T - a.tempC()) / (b.tempC() - a.tempC());
                return a.heatFlowMw() + w * (b.heatFlowMw() - a.heatFlowMw());
            }
        }
        return in.get(in.size() - 1).heatFlowMw();
    }

    private static int countExtrema(List<DscPoint> in, Candidate linear) {
        double maxAbs = 0;
        double[] r = new double[in.size()];
        for (int i = 0; i < in.size(); i++) {
            r[i] = in.get(i).heatFlowMw() - linear.eval(in.get(i).tempC());
            maxAbs = Math.max(maxAbs, Math.abs(r[i]));
        }
        double thresh = 0.25 * maxAbs;
        int extrema = 0;
        for (int i = 2; i < r.length - 2; i++) {
            boolean locMax = r[i] > r[i - 1] && r[i] > r[i + 1] && r[i] > thresh;
            boolean locMin = r[i] < r[i - 1] && r[i] < r[i + 1] && r[i] < -thresh;
            if (locMax || locMin) { extrema++; i += 4; }
        }
        return extrema;
    }
}
