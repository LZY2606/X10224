package dsc.analysis;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dsc.util.Json;

/** 基线模型：linear / tangent（分段切线）/ sigmoidal（S 形混合）。 */
public final class Baselines {

    public record Model(String method, ObjectNode params) {
        public double at(double x) {
            return switch (method) {
                case "linear" -> params.get("a").asDouble() * x + params.get("b").asDouble();
                case "tangent" -> {
                    double xStar = params.get("xStar").asDouble();
                    if (x <= xStar) yield params.get("aL").asDouble() * x + params.get("bL").asDouble();
                    yield params.get("aR").asDouble() * x + params.get("bR").asDouble();
                }
                case "sigmoidal" -> {
                    double xc = params.get("xc").asDouble();
                    double s = params.get("s").asDouble();
                    double w = 1.0 / (1.0 + Math.exp(-(x - xc) / s));
                    double l = params.get("aL").asDouble() * x + params.get("bL").asDouble();
                    double r = params.get("aR").asDouble() * x + params.get("bR").asDouble();
                    yield (1 - w) * l + w * r;
                }
                default -> throw new IllegalStateException("unknown baseline method " + method);
            };
        }
    }

    public static Model linear(double x0, double y0, double x1, double y1) {
        double a = (y1 - y0) / (x1 - x0);
        double b = y0 - a * x0;
        ObjectNode p = Json.obj();
        p.put("a", a); p.put("b", b);
        p.put("x0", x0); p.put("y0", y0); p.put("x1", x1); p.put("y1", y1);
        return new Model("linear", p);
    }

    /** 分段切线基线：xStar 左侧用左切线，右侧用右切线。 */
    public static Model tangent(double aL, double bL, double aR, double bR, double xStar) {
        ObjectNode p = Json.obj();
        p.put("aL", aL); p.put("bL", bL); p.put("aR", aR); p.put("bR", bR); p.put("xStar", xStar);
        return new Model("tangent", p);
    }

    /** S 形基线：左右切线按 logistic 权重混合，xc 取区间中点，s 取区间宽度的 1/10。 */
    public static Model sigmoidal(double aL, double bL, double aR, double bR, double x0, double x1) {
        ObjectNode p = Json.obj();
        p.put("aL", aL); p.put("bL", bL); p.put("aR", aR); p.put("bR", bR);
        p.put("xc", (x0 + x1) / 2.0); p.put("s", Math.max(1e-6, (x1 - x0) / 10.0));
        return new Model("sigmoidal", p);
    }

    private Baselines() {}
}
