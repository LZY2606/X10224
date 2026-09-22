package dsc.ingest;

import dsc.domain.Models.AtmosphereRecord;
import dsc.domain.Models.MassRecord;
import dsc.domain.Models.Run;
import dsc.domain.Models.Tangent;
import dsc.domain.Models.TempCalibration;

import java.util.ArrayList;
import java.util.List;

/**
 * 一份离线案例包：热转折案（DSC 基线、相变积分与扫描历史审阅器）。
 * 仪器输出、样品质量、扫描速率、气氛、温度标定、用户切线分别是独立来源文件。
 */
public final class CasePack {

    public String manifestVersion;
    public String caseId;
    public String title;
    public String sampleId;
    public String description;
    public final List<Run> runs = new ArrayList<>();
    public final List<MassRecord> masses = new ArrayList<>();
    public final List<AtmosphereRecord> atmospheres = new ArrayList<>();
    public final List<TempCalibration> calibrations = new ArrayList<>();
    public final List<Tangent> tangents = new ArrayList<>();
    /** 导入阶段诊断（坏值留在原位，这里收集具体位置）。 */
    public final List<String> diagnostics = new ArrayList<>();

    public CasePack() {
    }
}
