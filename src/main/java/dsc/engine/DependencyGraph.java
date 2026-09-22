package dsc.engine;

import dsc.model.BaselineCandidate;
import dsc.model.Interval;
import dsc.model.ProjectState;
import dsc.model.Segment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DependencyGraph {

    public static final String NODE_RAW = "raw";
    public static final String NODE_MODE = "mode";
    public static final String NODE_CALIBRATION = "calibration";
    public static final String NODE_MASS = "mass";
    public static final String NODE_RATE = "rate";
    public static final String NODE_TANGENT = "tangent";
    public static final String NODE_INTERVAL = "interval";
    public static final String NODE_BASELINE = "baseline";
    public static final String NODE_EVENT = "event";
    public static final String NODE_INTEGRATION = "integration";

    public record Node(String id, String kind, String name) {
    }

    public record Edge(String from, String to, String reason) {
    }

    public record View(List<Node> nodes, List<Edge> edges, Map<String, String> nodeStatus,
                       Map<String, String> nodeVersion, List<String> staleReasons) {
    }

    public View build(ProjectState state) {
        List<Node> nodes = new ArrayList<>();
        List<Edge> edges = new ArrayList<>();
        Map<String, String> status = new LinkedHashMap<>();
        Map<String, String> versions = new LinkedHashMap<>();
        List<String> reasons = new ArrayList<>();

        String calId = state.currentCalibration() == null ? "cal:v0" : state.currentCalibration().id();
        String massId = state.currentMass() == null ? "mass:v0" : state.currentMass().id();
        nodes.add(new Node(calId, NODE_CALIBRATION, "温度标定 " + (state.currentCalibration() == null ? "缺失" : state.currentCalibration().version())));
        nodes.add(new Node(massId, NODE_MASS, "样品质量 " + (state.currentMass() == null ? "缺失" : state.currentMass().version())));
        versions.put(calId, calId);
        versions.put(massId, massId);
        status.put(calId, "valid");
        status.put(massId, "valid");

        for (Segment seg : state.segments.values()) {
            String rawId = "raw:" + seg.segmentId();
            String modeId = "mode:" + seg.segmentId();
            String rateId = "rate:" + seg.segmentId();
            nodes.add(new Node(rawId, NODE_RAW, "仪器输出 " + seg.label()));
            nodes.add(new Node(modeId, NODE_MODE, "扫描方向 " + seg.mode()));
            nodes.add(new Node(rateId, NODE_RATE, "扫描速率"));
            edges.add(new Edge(rawId, modeId, "温度序列斜率判定方向"));
            edges.add(new Edge(rawId, rateId, "由时间-温度序列测得"));
            status.put(rawId, "valid");
            status.put(modeId, "valid");
            status.put(rateId, "valid");
            versions.put(rawId, seg.blobId());
            versions.put(modeId, seg.segmentId() + "@" + seg.introducedAtEvent());
        }

        for (Interval interval : state.intervals.values()) {
            if (interval.supersededByEvent() != null) {
                continue;
            }
            String iid = "interval:" + interval.intervalId();
            String rawId = "raw:" + interval.segmentId();
            nodes.add(new Node(iid, NODE_INTERVAL, "积分区间 " + interval.intervalId()));
            edges.add(new Edge(rawId, iid, "区间读取该段原始曲线"));
            edges.add(new Edge("mode:" + interval.segmentId(), iid, "方向隔离"));
            status.put(iid, "valid");

            String tLeft = "tangent:" + interval.intervalId() + ":left";
            String tRight = "tangent:" + interval.intervalId() + ":right";
            for (String tid : List.of(tLeft, tRight)) {
                nodes.add(new Node(tid, NODE_TANGENT, "用户切线 " + tid.substring(tid.lastIndexOf(':') + 1)));
                edges.add(new Edge(rawId, tid, "切线在原始温度窗口上拟合"));
                edges.add(new Edge(tid, iid, "基线依赖切线窗口"));
                status.put(tid, state.tangents.containsKey(tid) ? "valid" : "missing");
            }

            for (BaselineCandidate bc : state.baselines.values()) {
                if (!bc.intervalId().equals(interval.intervalId())) {
                    continue;
                }
                String bid = "baseline:" + bc.candidateId();
                nodes.add(new Node(bid, NODE_BASELINE, "基线 " + bc.method()));
                edges.add(new Edge(iid, bid, "由区间生成"));
                edges.add(new Edge(tLeft, bid, "左切线"));
                edges.add(new Edge(tRight, bid, "右切线"));
                edges.add(new Edge(calId, bid, "温度标定应用"));
                boolean stale = BaselineCandidate.SUPERSEDED.equals(bc.status());
                status.put(bid, stale ? "stale" : "valid");
                versions.put(bid, bc.candidateId());
                if (stale) {
                    reasons.add(bc.candidateId() + " 已被新切线/区间参数取代");
                }
            }

            for (var te : state.thermalEvents.values()) {
                if (!te.intervalId().equals(interval.intervalId())) {
                    continue;
                }
                String eid = "event:" + te.thermalId();
                nodes.add(new Node(eid, NODE_EVENT, "热事件 " + te.type()));
                edges.add(new Edge("baseline:" + te.baselineCandidateId(), eid, "onset/peak/endset 依赖基线"));
                edges.add(new Edge(calId, eid, "温度标定应用"));
                status.put(eid, isEventStale(state, te) ? "stale" : "valid");
            }

            for (var integ : state.integrations.values()) {
                if (!integ.intervalId().equals(interval.intervalId())) {
                    continue;
                }
                String gid = "integration:" + integ.integrationId();
                nodes.add(new Node(gid, NODE_INTEGRATION, "焓变 " + integ.integrationId()));
                edges.add(new Edge("baseline:" + integ.candidateId(), gid, "基线面积"));
                edges.add(new Edge(massId, gid, "质量归一化"));
                edges.add(new Edge(calId, gid, "温度标定"));
                edges.add(new Edge("rate:" + interval.segmentId(), gid, "速率/积分坐标"));
                String st = IntegrationStatus.of(state, integ);
                status.put(gid, st);
                versions.put(gid, integ.integrationId());
                if (IntegrationStatus.STALE.equals(st)) {
                    reasons.add(integ.integrationId() + " 归一化依据已过期（质量 v" + integ.massVersion()
                            + " ≠ 当前版本，或基线已被取代）");
                }
            }
        }
        return new View(nodes, edges, status, versions, reasons);
    }

    private boolean isEventStale(ProjectState state, dsc.model.ThermalEvent te) {
        var bc = state.baselines.get(te.baselineCandidateId());
        return bc != null && BaselineCandidate.SUPERSEDED.equals(bc.status());
    }
}
