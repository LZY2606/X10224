package dsc.model;

import java.time.Instant;
import java.util.List;

/**
 * 仪器输出档案：原始文件的关键元数据与扫描段。
 * 仪器型号、符号约定、温度标定引用在此处分别存档。
 */
public record InstrumentOutput(String sourceId,
                               String fileName,
                               String instrumentModel,
                               String signConventionRaw,
                               String calibrationRef,
                               Instant acquiredAt,
                               List<Segment> segments) {
    public InstrumentOutput {
        segments = List.copyOf(segments);
    }
}
