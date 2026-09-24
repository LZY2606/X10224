package dsc.engine;

import dsc.casepack.CasePackLoader;
import dsc.domain.RawPoint;

import java.util.List;

/** 引擎内存中的段描述（仪器输出原始点 + 程序方向/气氛等元信息）。 */
public record SegmentMeta(String segmentId, int cycle, String programDirection,
                          String declaredSign, String fileName, String header,
                          String tUnit, String hfUnit,
                          String rateValue, String rateUnit,
                          String gas, String flowRaw, String flowUnit,
                          List<RawPoint> points) {

    public static SegmentMeta from(String segmentId, int cycle, String direction, String sign,
                                   String file, CasePackLoader.CsvData csv,
                                   String rateValue, String rateUnit,
                                   String gas, String flowRaw, String flowUnit) {
        return new SegmentMeta(segmentId, cycle, direction, sign, file, csv.header(),
                csv.tUnit(), csv.hfUnit(), rateValue, rateUnit, gas, flowRaw, flowUnit,
                csv.points());
    }
}
