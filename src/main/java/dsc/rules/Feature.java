package dsc.rules;

/**
 * onset / peak / endset。
 * @param tK      特征温度（K，规范坐标）
 * @param y       该温度的比热流（W/g）
 * @param kind    ONSET / PEAK / ENDSET / STEP_MID
 */
public record Feature(String kind, double tK, double y) {
}
