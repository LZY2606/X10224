#!/usr/bin/env python3
"""Deterministic generator for the offline DSC case pack.

Writes src/main/resources/casepack/*. The curves are synthetic, built from
explicit physical shapes: a glass-transition heat-capacity step, a cold
crystallization exotherm, two deliberately overlapping melting endotherms and
a cooling crystallization. Time sampling is intentionally non-uniform.
"""
import json
import math
import os
import random

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "casepack")
ROOT = os.path.abspath(ROOT)


def sigmoid(t, t0, width, height):
    return height / (1.0 + math.exp(-(t - t0) / width))


def gauss(t, tc, width, amp):
    return amp * math.exp(-((t - tc) ** 2) / (2 * width * width))


def base_hf(t):
    # gentle instrument drift shared by all segments (mW)
    return 0.20 + 0.004 * t - 0.00003 * (t - 100.0) ** 2


def noise(t, phase):
    return 0.025 * math.sin(t * 7.31 + phase) + 0.018 * math.sin(t * 2.13 + phase * 2.0)


def heating1(t):
    hf = base_hf(t)
    hf += sigmoid(t, 78.0, 1.8, 1.10)                 # glass transition (Cp step, endo up)
    hf += gauss(t, 125.0, 4.5, -5.5)                  # cold crystallization (exo)
    hf += gauss(t, 158.0, 3.2, 8.0)                   # melting endotherm a
    hf += gauss(t, 167.0, 3.5, 5.2)                   # melting endotherm b, overlapping a
    return hf


def cooling(t):
    hf = base_hf(t)
    hf += gauss(t, 140.0, 5.0, -7.0)                  # crystallization on cooling (exo)
    return hf


def heating3(t):
    hf = base_hf(t)
    hf += sigmoid(t, 78.0, 1.8, 1.05)
    hf += gauss(t, 162.0, 4.0, 9.0)
    return hf


def sample_segment(func, t_start, t_end, rate_k_per_min, phase, sparse=()):
    """Return list of (time_s, temp_C, hf_mW); non-uniform spacing in `sparse` ranges."""
    rate = rate_k_per_min / 60.0
    pts = []
    t = t_start
    time_s = 0.0
    step = 0
    while (t < t_end - 1e-9) if t_end > t_start else (t > t_end + 1e-9):
        hf = func(t) + noise(t, phase)
        pts.append((round(time_s, 3), round(t, 4), round(hf, 4)))
        dt = 4.0 if any(a <= t <= b for a, b in sparse) else 1.0
        # vary regular spacing slightly so it is not a rigid grid
        if dt == 1.0 and step % 37 == 11:
            dt = 2.0
        t += rate * dt * (1 if t_end > t_start else -1)
        time_s += dt
        step += 1
    # force endpoint
    pts.append((round(time_s + abs((t_end - t) / rate), 3), t_end, round(func(t_end) + noise(t_end, phase), 4)))
    return pts


def write_instrument(path, convention, primary, segments, tag):
    lines = []
    lines.append("# DSC-9000 instrument export")
    lines.append("# Instrument: DSC-9000 SN:0842")
    lines.append("# Sample: PET-1042")
    lines.append(f"# ExportTag: {tag}")
    lines.append(f"# HeatFlowConvention: {convention}")
    lines.append(f"# Primary: {'yes' if primary else 'no'}")
    lines.append("# Heat flow sign convention is part of the instrument output;")
    lines.append("# it must never be inferred from the file name.")
    sign = 1.0 if convention == "EndoUp" else -1.0
    for idx, (name, pts) in enumerate(segments, start=1):
        lines.append(f"# Segment: {idx} ({name})")
        lines.append("Time(s),Temperature(C),HeatFlow(mW)")
        for tm, tc, hf in pts:
            lines.append(f"{tm},{tc},{round(hf * sign, 4)}")
    with open(path, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")


def main():
    os.makedirs(ROOT, exist_ok=True)
    seg1 = sample_segment(heating1, 30.0, 180.0, 10.0, 0.3,
                          sparse=((50.0, 60.0), (100.0, 106.0)))
    seg2 = sample_segment(cooling, 180.0, 30.0, 10.0, 1.7)
    seg3 = sample_segment(heating3, 30.0, 180.0, 10.0, 2.9,
                          sparse=((60.0, 64.0),))
    write_instrument(os.path.join(ROOT, "instrument_output_v1.txt"),
                     "EndoUp", True,
                     [("heating 30->180C", seg1),
                      ("cooling 180->30C", seg2),
                      ("heating 30->180C second cycle", seg3)],
                     "v1-primary")
    # reversed heat flow: same three segments, ExoUp export convention (sign flipped)
    write_instrument(os.path.join(ROOT, "instrument_output_reversed.txt"),
                     "ExoUp", False,
                     [("heating 30->180C", seg1),
                      ("cooling 180->30C", seg2),
                      ("heating 30->180C second cycle", seg3)],
                     "re-export-opposite-convention")

    with open(os.path.join(ROOT, "mass_v1.txt"), "w", encoding="utf-8") as fh:
        fh.write(
            "# Sample mass record\nSample: PET-1042\nMass(mg): 5.10\n"
            "Uncertainty(mg): 0.02\nBalance: XP205 SN:331\n"
            "WeighedAt: 2026-09-10T09:12+09:00\nNote: pan re-sealed before run\n")

    with open(os.path.join(ROOT, "mass_v2.txt"), "w", encoding="utf-8") as fh:
        fh.write(
            "# Sample mass record (revision)\nSample: PET-1042\nMass(mg): 5.42\n"
            "Uncertainty(mg): 0.02\nBalance: XP205 SN:331\n"
            "WeighedAt: 2026-09-12T10:05+09:00\n"
            "Note: re-weighed after pan re-seal; supersedes 5.10 mg\n")

    with open(os.path.join(ROOT, "scan_rate.txt"), "w", encoding="utf-8") as fh:
        fh.write("# Scan rate program\nRate(K/min): 10.0\nAppliesTo: all segments\n")

    with open(os.path.join(ROOT, "atmosphere.txt"), "w", encoding="utf-8") as fh:
        fh.write("# Atmosphere record\nGas: Nitrogen\nFlow(mL/min): 50.0\n")

    with open(os.path.join(ROOT, "temp_calibration_v1.txt"), "w", encoding="utf-8") as fh:
        fh.write(
            "# Temperature calibration record\nStandard: Indium\n"
            "CertifiedOnset(C): 156.6\nMeasuredOnset(C): 155.9\n"
            "Offset(C): +0.7\nRule: calibrated_C = measured_C + offset\n")

    tangents_v1 = {
        "seg1": {
            "glass1": {
                "left": {"t": 68.0, "slope_mW_per_C": 0.002},
                "right": {"t": 90.0, "slope_mW_per_C": 0.002},
            },
            "peak2": {"left": {"t": 150.0, "slope_mW_per_C": -0.04}},
            "peak3": {"left": {"t": 163.0, "slope_mW_per_C": -0.02}},
        },
        "seg3": {
            "glass1": {
                "left": {"t": 68.0, "slope_mW_per_C": 0.002},
                "right": {"t": 90.0, "slope_mW_per_C": 0.002},
            },
            "peak1": {"left": {"t": 154.0, "slope_mW_per_C": -0.03}},
        },
    }
    tangents_v2 = json.loads(json.dumps(tangents_v1))
    # parameter revision: only the seg1 peak2 tangent anchor moves
    tangents_v2["seg1"]["peak2"]["left"]["t"] = 152.0
    tangents_v2["seg1"]["peak2"]["left"]["slope_mW_per_C"] = -0.02

    with open(os.path.join(ROOT, "tangents_v1.json"), "w", encoding="utf-8") as fh:
        json.dump(tangents_v1, fh, indent=2, ensure_ascii=False)
        fh.write("\n")
    with open(os.path.join(ROOT, "tangents_v2.json"), "w", encoding="utf-8") as fh:
        json.dump(tangents_v2, fh, indent=2, ensure_ascii=False)
        fh.write("\n")

    # short probe with NO convention header, file name deliberately says "exo";
    # used by the direction-rule tests (not imported by the main replay)
    pts = sample_segment(lambda t: 0.1 + 0.003 * t + gauss(t, 120.0, 4.0, 4.0),
                         40.0, 160.0, 10.0, 4.4)
    with open(os.path.join(ROOT, "probe_exo_dump.txt"), "w", encoding="utf-8") as fh:
        fh.write("# raw probe export\n# Instrument: unknown handheld unit\n")
        fh.write("# Segment: 1\nTime(s),Temperature(C),HeatFlow(mW)\n")
        for row in pts:
            fh.write(",".join(str(v) for v in row) + "\n")

    manifest = {
        "sample": {"id": "smp-pet1042", "name": "PET-1042 thermal history sample"},
        "description": "Heat/cool/heat DSC history: glass transition, crystallization and melting with recomputable baselines and integrations.",
        "steps": [
            {"n": 1, "type": "importPrimary", "file": "instrument_output_v1.txt",
             "kind": "INSTRUMENT_OUTPUT", "note": "initial import: three cycle segments, non-uniform temperature grid"},
            {"n": 2, "type": "import", "file": "mass_v1.txt", "kind": "SAMPLE_MASS",
             "note": "sample mass archived separately"},
            {"n": 3, "type": "import", "file": "scan_rate.txt", "kind": "SCAN_RATE"},
            {"n": 4, "type": "import", "file": "atmosphere.txt", "kind": "ATMOSPHERE"},
            {"n": 5, "type": "import", "file": "temp_calibration_v1.txt",
             "kind": "TEMP_CALIBRATION"},
            {"n": 6, "type": "import", "file": "tangents_v1.json",
             "kind": "TANGENT_SELECTION",
             "note": "user-selected tangents archived separately from instrument output"},
            {"n": 7, "type": "derive",
             "note": "full pipeline: segmentation, normalization, detection, parallel baselines, integrations"},
            {"n": 8, "type": "attempt", "action": "integrateEmpty",
             "segmentKey": "s1", "from": 200.0, "to": 210.0,
             "note": "empty interval beyond scan range must be rejected with a diagnostic"},
            {"n": 9, "type": "importSecondary", "file": "instrument_output_reversed.txt",
             "kind": "INSTRUMENT_OUTPUT",
             "note": "reversed heat flow export (ExoUp); endo/exo classification must agree with v1"},
            {"n": 10, "type": "import", "file": "tangents_v2.json",
             "kind": "TANGENT_SELECTION",
             "note": "parameter revision: only seg1 peak2 tangent changes"},
            {"n": 11, "type": "import", "file": "mass_v2.txt", "kind": "SAMPLE_MASS",
             "note": "mass revision: every normalized derivation becomes stale and recomputes"},
            {"n": 12, "type": "decide", "reviewer": "reviewer-A",
             "segmentKey": "s1", "transition": "peak2", "accept": "TANGENT",
             "note": "reviewer A accepts the tangent baseline"},
            {"n": 13, "type": "decide", "reviewer": "reviewer-B",
             "segmentKey": "s1", "transition": "peak2", "accept": "LINEAR",
             "fromSameBase": True,
             "note": "reviewer B edits from the same pre-A version: conflict must be recorded"},
            {"n": 14, "type": "resolveConflict", "keep": "reviewer-A",
             "note": "resolve: continue A; the B candidate is retained, not overwritten"},
        ],
    }
    with open(os.path.join(ROOT, "manifest.json"), "w", encoding="utf-8") as fh:
        json.dump(manifest, fh, indent=2, ensure_ascii=False)
        fh.write("\n")

    print("case pack written to", ROOT)


if __name__ == "__main__":
    main()
