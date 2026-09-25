package dsc.domain;

import java.util.List;

/**
 * 一条基线候选。method 区分生成方式；quality 为拟合优度（残差加权），
 * 仅用于排序，不用于自动唯一化——多个合理候选必须并存并保留差异。
 */
public record BaselineCandidate(String candidateId,
                                String method,
                                List<String> evidence,
                                double rmseMilliWatt,
                                double[] tempsK,
                                double[] valuesMilliWatt) {

    public double valueAt(double tempK) {
        int n = tempsK.length;
        if (tempK <= tempsK[0]) {
            double slope = (valuesMilliWatt[1] - valuesMilliWatt[0]) / (tempsK[1] - tempsK[0]);
            return valuesMilliWatt[0] + slope * (tempK - tempsK[0]);
        }
        if (tempK >= tempsK[n - 1]) {
            double slope = (valuesMilliWatt[n - 1] - valuesMilliWatt[n - 2]) / (tempsK[n - 1] - tempsK[n - 2]);
            return valuesMilliWatt[n - 1] + slope * (tempK - tempsK[n - 1]);
        }
        int i = 0;
        while (i < n - 1 && tempsK[i + 1] < tempK) {
            i++;
        }
        double f = (tempK - tempsK[i]) / (tempsK[i + 1] - tempsK[i]);
        return valuesMilliWatt[i] + f * (valuesMilliWatt[i + 1] - valuesMilliWatt[i]);
    }
}
