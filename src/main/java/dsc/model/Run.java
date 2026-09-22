package dsc.model;

import java.util.List;
import java.util.Map;

/**
 * 一次仪器运行输出，包含一个或多个程序段（如 H1-C1-H2 循环履历）。
 */
public record Run(
        String id,
        String instrumentFile,
        Map<String, String> metadata,
        HeatFlowSign declaredSign,
        List<Segment> segments,
        List<Diagnostic> diagnostics) {
}
