package dsc.engine;

import java.util.ArrayList;
import java.util.List;

public final class Geometry {
    private Geometry() {
    }

    public record Window(double[] temp, double[] hf) {
    }

    public static Window slice(double[] t, double[] hf, double start, double end) {
        List<Double> tt = new ArrayList<>();
        List<Double> hh = new ArrayList<>();
        for (int i = 0; i < t.length; i++) {
            if (!Double.isNaN(hf[i]) && t[i] >= start - 1e-9 && t[i] <= end + 1e-9) {
                tt.add(t[i]);
                hh.add(hf[i]);
            }
        }
        return new Window(toVec(tt), toVec(hh));
    }

    private static double[] toVec(List<Double> xs) {
        double[] out = new double[xs.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = xs.get(i);
        }
        return out;
    }

    public static double[] tangentLine(double[] t, double[] hf, double tStart, double tEnd) {
        List<Double> tt = new ArrayList<>();
        List<Double> hh = new ArrayList<>();
        for (int i = 0; i < t.length; i++) {
            if (!Double.isNaN(hf[i]) && t[i] >= tStart - 1e-9 && t[i] <= tEnd + 1e-9) {
                tt.add(t[i]);
                hh.add(hf[i]);
            }
        }
        if (tt.size() < 2) {
            return new double[]{0, 0};
        }
        return Stats.linfit(toVec(tt), toVec(hh));
    }

    public static double evalLine(double[] mb, double t) {
        return mb[0] * t + mb[1];
    }

    public static record PeakFeatures(double onsetK, double peakK, double endsetK, double peakValue,
                                      double signedAreaVsLinear, double[] deviation) {
    }

    public static PeakFeatures peakFeatures(double[] t, double[] hf, double[] baselineLine) {
        int n = t.length;
        double[] dev = new double[n];
        for (int i = 0; i < n; i++) {
            dev[i] = hf[i] - (baselineLine[0] * t[i] + baselineLine[1]);
        }
        int peakIdx = 0;
        double maxAbs = -1;
        for (int i = 0; i < n; i++) {
            double a = Math.abs(dev[i]);
            if (a > maxAbs) {
                maxAbs = a;
                peakIdx = i;
            }
        }
        double peakSign = Math.signum(dev[peakIdx]);
        double threshold = 0.05 * maxAbs;
        int onsetIdx = peakIdx;
        while (onsetIdx > 0 && Math.abs(dev[onsetIdx - 1]) > threshold) {
            onsetIdx--;
        }
        int endsetIdx = peakIdx;
        while (endsetIdx < n - 1 && Math.abs(dev[endsetIdx + 1]) > threshold) {
            endsetIdx++;
        }
        int lo = Math.max(0, onsetIdx - 4);
        int hi = Math.min(n - 1, onsetIdx + 4);
        double[] onFit = Stats.linfit(sliceArr(t, lo, hi), sliceArr(dev, lo, hi));
        double onsetT = -onFit[1] / (onFit[0] == 0 ? 1e-30 : onFit[0]);
        if (!Double.isFinite(onsetT) || onsetT < t[0] || onsetT > t[peakIdx]) {
            onsetT = t[onsetIdx];
        }
        lo = Math.max(peakIdx, endsetIdx - 4);
        hi = Math.min(n - 1, endsetIdx + 4);
        double[] endFit = Stats.linfit(sliceArr(t, lo, hi), sliceArr(dev, lo, hi));
        double endT = -endFit[1] / (endFit[0] == 0 ? 1e-30 : endFit[0]);
        if (!Double.isFinite(endT) || endT < t[peakIdx] || endT > t[n - 1]) {
            endT = t[endsetIdx];
        }
        double area = Stats.trapz(t, dev);
        return new PeakFeatures(onsetT, t[peakIdx], endT, dev[peakIdx], peakSign * area, dev);
    }

    private static double[] sliceArr(double[] xs, int lo, int hi) {
        double[] out = new double[hi - lo + 1];
        System.arraycopy(xs, lo, out, 0, out.length);
        return out;
    }

    public record StepFeatures(double onsetK, double midK, double endsetK, double step, double[] stepLine) {
    }

    public static StepFeatures stepFeatures(double[] t, double[] hf, double[] linearBaseline) {
        int n = t.length;
        double[] dev = new double[n];
        for (int i = 0; i < n; i++) {
            dev[i] = hf[i] - (linearBaseline[0] * t[i] + linearBaseline[1]);
        }
        double pre = mean(dev, 0, Math.max(1, n / 5));
        double post = mean(dev, n - Math.max(1, n / 5), n);
        double step = post - pre;
        double loLevel = Math.min(pre, post);
        double hiLevel = Math.max(pre, post);
        double mid = loLevel + 0.5 * step;
        int midIdx = 0;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            if (Math.abs(dev[i] - mid) < best) {
                best = Math.abs(dev[i] - mid);
                midIdx = i;
            }
        }
        double lowBand = loLevel + 0.1 * step;
        double highBand = loLevel + 0.9 * step;
        int onsetIdx = midIdx;
        while (onsetIdx > 0 && dev[onsetIdx - 1] < lowBand) {
            onsetIdx--;
        }
        int endIdx = midIdx;
        while (endIdx < n - 1 && dev[endIdx + 1] < highBand) {
            endIdx++;
        }
        int a = Math.max(0, onsetIdx - 4);
        int b = Math.min(n - 1, onsetIdx + 4);
        double[] onFit = Stats.linfit(sliceArr(t, a, b), sliceArr(dev, a, b));
        double onsetT = intersectLevel(t, onFit, lowBand, dev, onsetIdx, true);
        int c = Math.max(midIdx, endIdx - 4);
        int d = Math.min(n - 1, endIdx + 4);
        double[] endFit = Stats.linfit(sliceArr(t, c, d), sliceArr(dev, c, d));
        double endT = intersectLevel(t, endFit, highBand, dev, endIdx, false);
        double[] line = linearBaseline.clone();
        return new StepFeatures(onsetT, t[midIdx], endT, step, line);
    }

    private static double intersectLevel(double[] t, double[] fit, double level, double[] dev, int fallback,
                                         boolean onsetSide) {
        double x = (level - fit[1]) / (fit[0] == 0 ? 1e-30 : fit[0]);
        if (!Double.isFinite(x) || x < t[0] || x > t[t.length - 1]) {
            return t[fallback];
        }
        return x;
    }

    private static double mean(double[] xs, int lo, int hi) {
        double s = 0;
        int k = 0;
        for (int i = lo; i < hi; i++) {
            s += xs[i];
            k++;
        }
        return k == 0 ? 0 : s / k;
    }

    public static double rms(double[] t, double[] hf, double[] baselineLine) {
        double s = 0;
        int n = 0;
        for (int i = 0; i < t.length; i++) {
            double d = hf[i] - (baselineLine[0] * t[i] + baselineLine[1]);
            s += d * d;
            n++;
        }
        return Math.sqrt(s / Math.max(1, n));
    }
}
