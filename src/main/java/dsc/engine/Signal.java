package dsc.engine;

/** 简单移动平滑与一阶差分，供转折检测使用；原始曲线不被修改。 */
public final class Signal {

    private Signal() {
    }

    public static double[] smooth(double[] y, int half) {
        int n = y.length;
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            int lo = Math.max(0, i - half);
            int hi = Math.min(n - 1, i + half);
            double sum = 0;
            int count = 0;
            for (int j = lo; j <= hi; j++) {
                sum += y[j];
                count++;
            }
            out[i] = sum / count;
        }
        return out;
    }

    /** d y / d T。 */
    public static double[] derivative(double[][] points, int col) {
        int n = points.length;
        double[] out = new double[n];
        out[0] = n > 1 ? (points[1][col] - points[0][col])
                / Math.max(1e-12, points[1][1] - points[0][1]) : 0;
        for (int i = 1; i < n - 1; i++) {
            double dT = points[i + 1][1] - points[i - 1][1];
            out[i] = dT == 0 ? 0 : (points[i + 1][col] - points[i - 1][col]) / dT;
        }
        if (n > 1) {
            out[n - 1] = (points[n - 1][col] - points[n - 2][col])
                    / Math.max(1e-12, points[n - 1][1] - points[n - 2][1]);
        }
        return out;
    }

    public static double[] column(double[][] points, int col) {
        double[] out = new double[points.length];
        for (int i = 0; i < points.length; i++) {
            out[i] = points[i][col];
        }
        return out;
    }

    public static double noiseLevel(double[] y, int from, int to) {
        double sum = 0;
        int count = 0;
        for (int i = Math.max(1, from); i < Math.min(y.length - 1, to); i++) {
            double second = y[i - 1] - 2 * y[i] + y[i + 1];
            sum += second * second;
            count++;
        }
        return count == 0 ? 0 : Math.sqrt(sum / count) / Math.sqrt(6);
    }
}
