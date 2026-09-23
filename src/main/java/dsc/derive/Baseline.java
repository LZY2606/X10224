package dsc.derive;

import dsc.domain.Entities.ExoSign;
import dsc.domain.Values.Point;

import java.util.List;

/**
 * 基线模型。支持两种合理基线：
 *  - linear：两侧锚点连成直线（熔融/结晶常用）
 *  - step  ：台阶型，用于玻璃化（Cp 台阶），左右两段水平/斜线在中点过渡
 * 多个合理基线的差异由调用方保留，不在这里压成单一答案。
 */
public final class Baseline {

    public enum Kind {LINEAR, STEP}

    public final Kind kind;
    public final double leftAnchorK;
    public final double rightAnchorK;
    public final double leftValueW;
    public final double rightValueW;
    public final double stepMidK;

    private Baseline(Kind kind, double la, double ra, double lv, double rv, double mid) {
        this.kind = kind;
        this.leftAnchorK = la;
        this.rightAnchorK = ra;
        this.leftValueW = lv;
        this.rightValueW = rv;
        this.stepMidK = mid;
    }

    public static Baseline linear(double la, double ra, double lv, double rv) {
        return new Baseline(Kind.LINEAR, la, ra, lv, rv, Double.NaN);
    }

    public static Baseline step(double la, double ra, double lv, double rv, double mid) {
        return new Baseline(Kind.STEP, la, ra, lv, rv, mid);
    }

    /** 在温度 t(K) 处的基线热流(W)。 */
    public double valueAt(double t) {
        double span = rightAnchorK - leftAnchorK;
        if (span <= 0) {
            return leftValueW;
        }
        double frac = (t - leftAnchorK) / span;
        frac = Math.max(0, Math.min(1, frac));
        if (kind == Kind.LINEAR) {
            return leftValueW + frac * (rightValueW - leftValueW);
        }
        // 台阶：以中点为界的 logistic 平滑（陡度固定，保持可重算）
        double gamma = 40.0 / span;
        double w = 1.0 / (1.0 + Math.exp(-gamma * (t - stepMidK)));
        return leftValueW + w * (rightValueW - leftValueW);
    }

    /** 在锚点处取热流（最近点插值）。 */
    public static double sampleAt(List<Point> ordered, double tempK) {
        if (ordered.isEmpty()) {
            return Double.NaN;
        }
        Point prev = ordered.get(0);
        for (Point p : ordered) {
            if (p.tempK >= tempK) {
                double span = p.tempK - prev.tempK;
                if (span <= 0) {
                    return p.heatFlowW;
                }
                double f = (tempK - prev.tempK) / span;
                return prev.heatFlowW + f * (p.heatFlowW - prev.heatFlowW);
            }
            prev = p;
        }
        return prev.heatFlowW;
    }

    /**
     * 把仪器热流读数换算成"向上=吸热"的约定坐标，依据显式 exoSign。
     * 绝不能从文件名/段名推断方向。
     */
    public static double toEndoUp(double readingW, ExoSign sign) {
        return switch (sign) {
            case EXO_UP -> -readingW;   // 仪器向上是放热，翻转使向上=吸热
            case EXO_DOWN, UNKNOWN -> readingW; // 仪器向下是放热 => 向上即吸热
        };
    }
}
