package dsc.core;

/**
 * 极简线性最小二乘（正规方程，带主元保护），只用于基线拟合，避免引入外部数学库。
 */
public final class LeastSquares {
    private LeastSquares() {}

    /** 解 A x = b，A 为 n×m，高斯消元（n 与 m 均很小）。 */
    public static double[] solve(double[][] a, double[] b) {
        int n = a.length, m = a[0].length;
        double[][] mtx = new double[n][m + 1];
        for (int i = 0; i < n; i++) {
            System.arraycopy(a[i], 0, mtx[i], 0, m);
            mtx[i][m] = b[i];
        }
        int rows = Math.min(n, m);
        for (int col = 0; col < rows; col++) {
            int piv = col;
            for (int r = col + 1; r < n; r++)
                if (Math.abs(mtx[r][col]) > Math.abs(mtx[piv][col])) piv = r;
            double[] tmp = mtx[col]; mtx[col] = mtx[piv]; mtx[piv] = tmp;
            double pv = mtx[col][col];
            if (Math.abs(pv) < 1e-15) continue;
            for (int r = 0; r < n; r++) {
                if (r == col) continue;
                double f = mtx[r][col] / pv;
                for (int c = col; c <= m; c++) mtx[r][c] -= f * mtx[col][c];
            }
        }
        double[] x = new double[m];
        for (int i = 0; i < m && i < n; i++) {
            if (Math.abs(mtx[i][i]) > 1e-15) x[i] = mtx[i][m] / mtx[i][i];
        }
        return x;
    }

    /** y = a + b*x 拟合。 */
    public static double[] linear(double[] xs, double[] ys) {
        int n = xs.length;
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (int i = 0; i < n; i++) {
            sx += xs[i]; sy += ys[i]; sxx += xs[i] * xs[i]; sxy += xs[i] * ys[i];
        }
        double[][] a = {{n, sx}, {sx, sxx}};
        double[] b = {sy, sxy};
        return solve(a, b);
    }

    public static double rms(double[] xs, double[] ys, double[] ab) {
        double s = 0;
        for (int i = 0; i < xs.length; i++) {
            double r = ys[i] - (ab[0] + ab[1] * xs[i]);
            s += r * r;
        }
        return Math.sqrt(s / Math.max(1, xs.length));
    }
}
