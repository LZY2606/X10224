package dsc.rule;

import java.util.List;

/**
 * 基线模型。同名字段 startK/endK 表示其生效范围；evaluate 在区间内给出基线值。
 * 不同方法对同一事件可能产生多个合理候选，必须保留差异。
 */
public interface BaselineModel {

    String method();

    double startK();

    double endK();

    double evaluate(double tK);

    /** 供 SVG 并排绘制的采样折线（按温度升序）。 */
    default List<XY> samples(List<XY> curve) {
        List<XY> out = new java.util.ArrayList<>();
        for (XY p : CurveMath.slice(curve, startK(), endK())) {
            out.add(new XY(p.x(), evaluate(p.x())));
        }
        if (out.isEmpty()) {
            out.add(new XY(startK(), evaluate(startK())));
            out.add(new XY(endK(), evaluate(endK())));
        }
        return out;
    }

    record LineBaseline(String methodName, double startK, double endK,
                        CurveMath.Line line) implements BaselineModel {
        @Override
        public String method() {
            return methodName;
        }

        @Override
        public double startK() {
            return startK;
        }

        @Override
        public double endK() {
            return endK;
        }

        @Override
        public double evaluate(double tK) {
            return line.y(tK);
        }
    }

    record SigmoidStepBaseline(double startK, double endK, double yLow, double yHigh,
                               double tMidK, double widthK) implements BaselineModel {
        @Override
        public String method() {
            return "sigmoid-step";
        }

        @Override
        public double evaluate(double tK) {
            double z = (tMidK - tK) / (widthK / 2.0);
            double s = 1.0 / (1.0 + Math.exp(Math.max(-60, Math.min(60, z))));
            return yLow + (yHigh - yLow) * s;
        }
    }
}
