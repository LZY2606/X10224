package dsc.domain;

/**
 * 从实测时间-温度轨迹回归得到的扫描段，不依据文件名猜测方向。
 *
 * @param index        段在该 run 内的序号
 * @param startIndex   起点在曲线数组中的下标
 * @param endIndex     终点下标（含）
 * @param startT       段起点温度（规范化 K）
 * @param endT         段终点温度（规范化 K）
 * @param heating      true=加热；false=冷却
 * @param rateKPerSec  回归斜率绝对值
 * @param nonuniform   采样间隔（温度轴）是否非等间隔
 */
public record ScanSegment(int index, int startIndex, int endIndex,
                          double startT, double endT, boolean heating,
                          double rateKPerSec, boolean nonuniform) {

    /** 只有同加热状态的段才允许混用。 */
    public boolean mixesWith(ScanSegment other) {
        return this.heating == other.heating;
    }
}
