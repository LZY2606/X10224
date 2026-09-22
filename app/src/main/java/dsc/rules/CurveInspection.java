package dsc.rules;

import dsc.domain.Models.Direction;
import dsc.domain.Models.Point;
import dsc.domain.Models.Run;

import java.util.ArrayList;
import java.util.List;

/**
 * 曲线体检。所有判定都基于数据本身与数据自述字段，绝不使用文件名猜测方向。
 * 诊断条目带代码，供界面从异常点回溯来源链。
 */
public final class CurveInspection {

    public static final String SEV_ERROR = "error";
    public static final String SEV_WARNING = "warning";
    public static final String SEV_INFO = "info";

    public record Issue(String code, String severity, int pointIndex, String message) {
    }

    public record NormalizedCurve(double[] tSec, double[] tempK, double[] heatW,
                                  Direction direction, boolean uniformlySpaced,
                                  List<Issue> issues, boolean usable) {
    }

    private CurveInspection() {
    }

    public static NormalizedCurve inspect(Run run) {
        List<Issue> issues = new ArrayList<>();
        List<Point> pts = run.points;
        int n = pts.size();
        double[] t = new double[n];
        double[] temp = new double[n];
        double[] hf = new double[n];
        int badT = 0;
        int badTemp = 0;
        int badHf = 0;
        for (int i = 0; i < n; i++) {
            Point p = pts.get(i);
            if (p.time() != null && p.time().valid()) {
                t[i] = p.time().value();
            } else {
                t[i] = Double.NaN;
                badT++;
            }
            if (p.temperature() != null && p.temperature().valid()) {
                temp[i] = p.temperature().value();
            } else {
                temp[i] = Double.NaN;
                badTemp++;
            }
            if (p.heatFlow() != null && p.heatFlow().valid()) {
                hf[i] = p.heatFlow().value();
            } else {
                hf[i] = Double.NaN;
                badHf++;
            }
        }
        if (badT > 0) {
            issues.add(new Issue("BAD_TIME", SEV_ERROR, -1, badT + " point(s) have unconverted time; kept raw"));
        }
        if (badTemp > 0) {
            issues.add(new Issue("BAD_TEMPERATURE", SEV_ERROR, -1,
                    badTemp + " point(s) have unconverted temperature; kept raw"));
        }
        if (badHf > 0) {
            issues.add(new Issue("BAD_HEATFLOW", SEV_ERROR, -1,
                    badHf + " point(s) have unconverted heat flow; kept raw"));
        }

        boolean usable = badT + badTemp + badHf == 0;
        Direction dataDirection = inferDirection(temp, issues);
        Direction declared = run.declaredDirection == null ? Direction.UNKNOWN : run.declaredDirection;
        if (declared != Direction.UNKNOWN && declared != dataDirection && dataDirection != Direction.UNKNOWN
                && dataDirection != Direction.ISOTHERMAL) {
            // 反向热流场景：数据自述与实际温度方向矛盾
            issues.add(new Issue("REVERSED_DIRECTION", SEV_ERROR, -1,
                    "declared " + declared + " but temperature axis runs " + dataDirection
                            + "; heating/cooling segments must not be mixed; file name is not evidence"));
        }

        boolean uniform = checkSpacing(t, temp, issues);
        if ("specific".equals(run.heatFlowFamily)) {
            issues.add(new Issue("SPECIFIC_HEATFLOW", SEV_INFO, -1,
                    "heat flow is already mass-specific (W/g); total-flow normalization path disabled"));
        }
        return new NormalizedCurve(t, temp, hf, dataDirection, uniform, issues, usable);
    }

    /** 方向只由温度序列推断。 */
    public static Direction inferDirection(double[] temp, List<Issue> issues) {
        int up = 0;
        int down = 0;
        int flat = 0;
        int reversalAt = -1;
        Double first = null;
        Double last = null;
        for (int i = 1; i < temp.length; i++) {
            double a = temp[i - 1];
            double b = temp[i];
            if (Double.isNaN(a) || Double.isNaN(b)) {
                continue;
            }
            if (first == null) {
                first = a;
            }
            last = b;
            int sign = b > a ? 1 : b < a ? -1 : 0;
            if (sign > 0) {
                up++;
            } else if (sign < 0) {
                down++;
            } else {
                flat++;
            }
            if (reversalAt < 0 && ((sign > 0 && down > 0) || (sign < 0 && up > 0))) {
                reversalAt = i;
            }
        }
        if (reversalAt >= 0) {
            if (issues != null) {
                issues.add(new Issue("MIXED_SEGMENT", SEV_ERROR, reversalAt,
                        "temperature reverses direction at point " + reversalAt
                                + "; a single segment must not mix heating and cooling"));
            }
            return Direction.UNKNOWN;
        }
        if (up > down && up > flat) {
            return Direction.HEATING;
        }
        if (down > up && down > flat) {
            return Direction.COOLING;
        }
        if (flat > 0 && up == 0 && down == 0) {
            return Direction.ISOTHERMAL;
        }
        return Direction.UNKNOWN;
    }

    /** 非等间隔温度：相邻 dT 的离散度超过阈值则告警；积分改走时间轴梯形法。 */
    static boolean checkSpacing(double[] t, double[] temp, List<Issue> issues) {
        List<Double> dt = new ArrayList<>();
        for (int i = 1; i < temp.length; i++) {
            if (!Double.isNaN(temp[i]) && !Double.isNaN(temp[i - 1])) {
                dt.add(Math.abs(temp[i] - temp[i - 1]));
            }
        }
        if (dt.size() < 3) {
            return true;
        }
        double mean = dt.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double var = 0;
        for (double v : dt) {
            var += (v - mean) * (v - mean);
        }
        double cv = mean == 0 ? 0 : Math.sqrt(var / dt.size()) / mean;
        boolean uniform = cv < 0.08;
        if (!uniform) {
            issues.add(new Issue("NONUNIFORM_TEMPERATURE", SEV_WARNING, -1,
                    String.format(
                            "non-uniform temperature spacing (CV=%.2f); integration uses time-axis trapezoid rule",
                            cv)));
        }
        // 时间轴也必须单调
        for (int i = 1; i < t.length; i++) {
            if (!Double.isNaN(t[i]) && !Double.isNaN(t[i - 1]) && t[i] < t[i - 1]) {
                issues.add(new Issue("TIME_NOT_MONOTONIC", SEV_ERROR, i,
                        "time decreases at point " + i));
                break;
            }
        }
        return uniform;
    }
}
