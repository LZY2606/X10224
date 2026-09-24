#!/usr/bin/env python3
"""Generate the domain-specific offline case pack for
热转折案：DSC 基线、相变积分与扫描历史审阅器.

Outputs deterministic instrument-output CSVs (synthetic, physically shaped:
glass step + Gaussian crystallisation/melt peaks), plus separately archived
mass, scan-rate, atmosphere and temperature calibration sources.
"""
import math
import os
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[1] / "app/src/main/resources/cases/thermal-turning-case"
FIX = ROOT / "fixtures"

RATE = 10.0  # K/min declared per segment

def gauss(t, tc, a, w):
    # a = amplitude mW (exotherm-positive will be interpreted per convention)
    return a * math.exp(-0.5 * ((t - tc) / w) ** 2)

def logistic(t, tc, dcp_mw, w):
    return dcp_mw / (1.0 + math.exp(-(t - tc) / w))

def heatflow_cool(t):
    # cooling leg 140 -> 20 C; crystallisation exotherm near 92 C.
    base = 0.0008 * (t - 80.0)
    cryst = gauss(t, 92.0, 1.7, 4.2)
    return base + cryst

def heatflow_heat(t, amended=False, overlap=False):
    # heating leg 20 -> 140 C; glass step near 55 C; melt endotherm near 118 C.
    base = -0.0006 * (t - 80.0)
    glass = logistic(t, 55.0, -0.55, 1.4)
    melt = gauss(t, 118.0, -2.4, 3.0)
    extra = gauss(t, 111.0, -0.8, 3.4) if overlap else 0.0
    shift = 0.10 if amended else 0.0
    return base + glass + melt + extra + shift

def emit(path, seg_id, cycle, direction, sign, t0, t1, fn, nonuniform=False,
         bad_row=None, rate=RATE, file_name=None):
    rows = []
    n = int(round(abs(t1 - t0) / (rate / 60.0))) + 1
    for i in range(n):
        frac = i / (n - 1)
        t = t0 + frac * (t1 - t0)
        if nonuniform:
            # irregular temperature sampling: stretch gaps on upper half
            if t > (t0 + t1) / 2:
                t = t + 0.9 * math.sin(i * 0.7)
                t = min(t, t1)
        time_s = i * 6.0
        hf = fn(t)
        rows.append((time_s, t, hf, i))
    lines = [
        f"# segment-id: {seg_id}",
        f"# cycle: {cycle}",
        f"# direction: {direction}",
        f"# sign-convention: {sign}",
        f"# instrument: PairWise DSC-9 (synthetic offline fixture)",
        f"# file-name-hint: {file_name or path.name}",
        "time_s [s],temperature [\u00b0C],heatflow [mW]",
    ]
    for (time_s, t, hf, i) in rows:
        if bad_row is not None and i == bad_row:
            lines.append(f"{time_s:.1f},{t:.3f},sensor-dropped")
        else:
            lines.append(f"{time_s:.1f},{t:.3f},{hf:.5f}")
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")

def main():
    FIX.mkdir(parents=True, exist_ok=True)

    # cooling leg; file name deliberately says the wrong thing in one fixture
    # (heatflow-cool.csv whose header rules), plus a "reversed" heatflow file.
    emit(FIX / "seg-cool-1.csv", "seg-cool-1", 1, "cooling", "exo-up",
         140.0, 20.0, heatflow_cool, file_name="instrument_run01_heat.csv")

    # heating leg with EXO-DOWN instrument convention (reverse heat flow axis)
    # and a deliberately non-uniform temperature grid + one bad sensor row.
    emit(FIX / "seg-heat-1.csv", "seg-heat-1", 1, "heating", "exo-down",
         20.0, 140.0, heatflow_heat, nonuniform=True, bad_row=340,
         file_name="run02_heating_leg.csv")

    # amended heating leg (localised source change: small offset on signal)
    emit(FIX / "seg-heat-1-v2.csv", "seg-heat-1", 1, "heating", "exo-down",
         20.0, 140.0, lambda t: heatflow_heat(t, amended=True),
         nonuniform=True, bad_row=340, file_name="run02_heating_leg_v2.csv")

    # second cycle: another heating leg carrying an overlapping shoulder
    emit(FIX / "seg-heat-2.csv", "seg-heat-2", 2, "heating", "exo-down",
         20.0, 140.0, lambda t: heatflow_heat(t, overlap=True),
         file_name="run03_cycle2_heating.csv")

    (FIX / "mass-v1.txt").write_text(
        "# source: sample-mass\n# case: thermal-turning-case\n"
        "12.45 mg\n# balance: AB-204 # weighed-at: 2026-09-10T09:12:00+09:00\n",
        encoding="utf-8")
    (FIX / "mass-v2.txt").write_text(
        "# source: sample-mass (revision)\n"
        "12.10 mg\n# balance: AB-204 re-calibrated # weighed-at: 2026-09-12T14:05:00+09:00\n",
        encoding="utf-8")
    (FIX / "rate-cool.txt").write_text(
        "# source: scan-rate segment=seg-cool-1\n10.0 K/min (programmed; actual mean logged 9.98)\n",
        encoding="utf-8")
    (FIX / "rate-heat.txt").write_text(
        "# source: scan-rate segment=seg-heat-1\n10.0 K/min (programmed; actual mean logged 10.04)\n",
        encoding="utf-8")
    (FIX / "atmosphere.txt").write_text(
        "# source: atmosphere\nN2, 50 mL/min, purge started 10 min before run\n", encoding="utf-8")
    (FIX / "calibration-v1.txt").write_text(
        "# source: temperature-calibration v1\n# standard: indium 156.60 C\noffset_K: +0.35\nslope: 1.000\n",
        encoding="utf-8")
    (FIX / "calibration-v2.txt").write_text(
        "# source: temperature-calibration v2 (parameter revision)\n"
        "# standards: indium 156.60 C; tin 231.93 C\noffset_K: +0.12\nslope: 1.000\n",
        encoding="utf-8")
    (FIX / "tangent-slot-melt.txt").write_text(
        "# source: user-selected-tangent slot=slot:heat-melt\n"
        "# picked by reviewer l.chen on the curve page\nleft-anchor-K: 383.15  # 110 C\nright-anchor-K: 398.15 # 125 C\nmethod: chord\n", encoding="utf-8")
    (FIX / "tangent-slot-glass.txt").write_text(
        "# source: user-selected-tangent slot=slot:heat-glass\n"
        "# glass step tangents dragged by reviewer m.ito\nleft-anchor-K: 320.15   # 47 C\nright-anchor-K: 336.15  # 63 C\nmethod: glass-tangent\n", encoding="utf-8")

if __name__ == "__main__":
    main()
    print("fixtures written to", FIX)
