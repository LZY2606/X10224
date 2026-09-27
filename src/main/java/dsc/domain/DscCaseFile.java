package dsc.domain;

import java.util.ArrayList;
import java.util.List;

/** 离线案例包中的一份仪器档案解析结果（来源层，不做规范化修改）。 */
public record DscCaseFile(InstrumentOutput instrument,
                          List<Qty> massEntriesRaw,
                          List<Atmosphere> atmospheres,
                          List<TempCalibration> calibrations,
                          List<TangentSelection> tangents,
                          List<String> diagnostics) {}
