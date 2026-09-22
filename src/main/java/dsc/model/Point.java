package dsc.model;

/**
 * 仪器输出中的一个采样点。rawTemperature/rawHeatFlow 保留原始表示；
 * temperatureK/heatFlowW 仅做单位规范化，吸放热方向的翻转属于派生层。
 */
public record Point(
        int index,
        String rawTemperature,
        String rawHeatFlow,
        Double temperatureK,
        Double heatFlowW,
        boolean valid,
        String parseError) {

    public static Point invalid(int index, String rawT, String rawHf, String error) {
        return new Point(index, rawT, rawHf, null, null, false, error);
    }

    public static Point valid(int index, String rawT, String rawHf, double temperatureK, double heatFlowW) {
        return new Point(index, rawT, rawHf, temperatureK, heatFlowW, true, null);
    }
}
