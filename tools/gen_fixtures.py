#!/usr/bin/env python3
"""Generate DSC curve fixtures for the thermal-turning case pack.
Curves are synthetic but physically shaped: glass step, exo/endo Gaussians.
"""
import math, os

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "casepack")

def gauss(t, center, amp, sigma):
    return amp * math.exp(-0.5 * ((t - center) / sigma) ** 2)

def sigmoid_step(t, center, height, width):
    return height / (1.0 + math.exp(-(t - center) / width))

def base_hf(t):
    return 0.5 + 0.004 * (t - 25.0)

def emit_segment(lines, seg_idx, direction, rate, gas, flow, points):
    lines.append(f"# segment: {seg_idx}")
    lines.append(f"# direction: {direction}")
    lines.append(f"# scan_rate: {rate} K/min")
    lines.append(f"# atmosphere: {gas} {flow} mL/min")
    lines.append("time_s,temp_C,heatflow_mW")
    for (ts, tc, hf) in points:
        lines.append(f"{ts:.3f},{tc:.3f},{hf:.6f}")

def heat_points(t_lo, t_hi, step_c, rate, features, nonuniform=False):
    pts = []
    ts = 0.0
    tc = t_lo
    i = 0
    direction = 1 if t_hi > t_lo else -1
    while (tc - t_hi) * direction < -1e-9:
        hf = base_hf(tc)
        for f in features:
            hf += f(tc)
        pts.append((ts, tc, hf))
        if nonuniform:
            dc = step_c * (0.4 if i % 2 == 0 else 1.6)
        else:
            dc = step_c
        dt = dc / rate * 60.0
        ts += dt
        tc += direction * dc
        i += 1
    return pts

# ---------- run1.csv : 3-segment cycle, non-uniform cooling, overlapping pair in seg3
lines = [
    "# instrument: TA-DSC-250",
    "# sign_convention: ENDO_UP",
    "# sample_id: PET-2026-0417",
    "# calibration_id: cal-in-sn-v1",
]
seg1_feat = [
    lambda t: sigmoid_step(t, 75.0, 0.30, 1.2),
    lambda t: gauss(t, 130.0, -1.10, 5.0),
    lambda t: gauss(t, 250.0, 1.80, 4.0),
]
emit_segment(lines, 1, "HEATING", 10.0, "N2", 50.0, heat_points(25, 260, 0.5, 10.0, seg1_feat))
seg2_feat = [lambda t: gauss(t, 190.0, -0.90, 6.0)]
emit_segment(lines, 2, "COOLING", 10.0, "N2", 50.0, heat_points(260, 25, 0.5, 10.0, seg2_feat, nonuniform=True))
seg3_feat = [
    lambda t: sigmoid_step(t, 75.0, 0.28, 1.2),
    lambda t: gauss(t, 130.0, -0.90, 4.0),
    lambda t: gauss(t, 141.0, -0.55, 5.0),
    lambda t: gauss(t, 250.0, 1.60, 4.0),
]
emit_segment(lines, 3, "HEATING", 20.0, "N2", 50.0, heat_points(25, 260, 0.5, 20.0, seg3_feat))
open(os.path.join(OUT, "run1.csv"), "w").write("\n".join(lines) + "\n")

# ---------- run2_reversed.csv : declares ENDO_UP but heat flow channel inverted
lines = [
    "# instrument: TA-DSC-250",
    "# sign_convention: ENDO_UP",
    "# sample_id: PET-2026-0417",
    "# calibration_id: cal-in-sn-v1",
]
feat = [
    lambda t: sigmoid_step(t, 75.0, -0.30, 1.2),
    lambda t: gauss(t, 250.0, -1.70, 4.0),
]
emit_segment(lines, 1, "HEATING", 10.0, "N2", 50.0, heat_points(25, 260, 0.5, 10.0, feat))
open(os.path.join(OUT, "run2_reversed.csv"), "w").write("\n".join(lines) + "\n")

# ---------- run3_filename_trap.csv : filename says cooling, header says HEATING, ENDO_DOWN
lines = [
    "# instrument: TA-DSC-250",
    "# sign_convention: ENDO_DOWN",
    "# sample_id: PET-2026-0417",
    "# calibration_id: cal-in-sn-v1",
]
feat = [lambda t: gauss(t, 248.0, -1.50, 4.0)]
emit_segment(lines, 1, "HEATING", 10.0, "N2", 50.0, heat_points(25, 260, 0.5, 10.0, feat))
open(os.path.join(OUT, "run3_cooling_trap.csv"), "w").write("\n".join(lines) + "\n")
print("fixtures written")
