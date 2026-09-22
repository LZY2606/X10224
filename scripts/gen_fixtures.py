#!/usr/bin/env python3
"""Generate DSC case-pack fixtures deterministically (committed under src/main/resources/casepack)."""
import math, os

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "casepack")
os.makedirs(OUT, exist_ok=True)

def smoothstep(x):
    return 1.0 / (1.0 + math.exp(-x))

def gauss(t, t0, w):
    return math.exp(-((t - t0) / w) ** 2)

def noise(t):
    return 0.01 * math.sin(t * 3.7)

def nonuniform_temps(t0, t1):
    steps = [0.7, 1.3, 0.9, 2.1, 0.5, 1.1]
    temps, t, i = [], t0, 0
    while t <= t1:
        temps.append(round(t, 2))
        t += steps[i % len(steps)]
        i += 1
    return temps

HEADER = """# DSC-EXPORT v2
# instrument: {instrument}
# heatFlowConvention: {conv}
# segment: {segment}
# cycle: {cycle}
# scanRate: {rate} K/min
# atmosphere: {atm}
# columns: time_s,temp_C,heatflow_mW
"""

def write_csv(name, header, rows):
    with open(os.path.join(OUT, name), "w") as f:
        f.write(header)
        for r in rows:
            f.write(r + "\n")

# ---- run 1: heating cycle 1, endo-up, NON-UNIFORM spacing, Tg + cold-crystallization + melting (overlapping), data gap 200-210, one OVERLOAD row
rows = []
t_prev = None
for t in nonuniform_temps(30.0, 220.0):
    if 200.0 < t < 210.0:
        continue  # instrument gap -> empty-interval case
    base = 0.50 + 0.002 * (t - 30.0)
    tg = 0.30 * smoothstep((t - 65.0) / 3.0)
    cryst = -1.5 * gauss(t, 132.0, 6.0)      # exotherm: downward in endo-up
    melt = 2.5 * gauss(t, 160.0, 5.0)        # endotherm: upward in endo-up
    hf = base + tg + cryst + melt + noise(t)
    time_s = (t - 30.0) * 60.0 / 10.0
    if abs(t - 100.5) < 0.4:
        rows.append(f"{time_s:.1f},{t:.2f},OVERLOAD")  # unconvertible cell, kept raw
    else:
        rows.append(f"{time_s:.1f},{t:.2f},{hf:.5f}")
write_csv("run1_heating_cycle1.csv",
          HEADER.format(instrument="DSC-2500", conv="endo-up", segment="heating", cycle=1, rate=10.0, atm="N2 50 mL/min"), rows)

# ---- run 2: cooling cycle 1, EXO-UP (reversed heat flow vs run 1). Filename lies ("ENDO") - metadata must win.
rows = []
t = 220.0
while t >= 40.0:
    base = 0.55 + 0.0018 * (t - 40.0)
    cryst = 1.8 * gauss(t, 140.0, 5.0)       # exotherm plotted UP in exo-up convention
    hf = base + cryst + noise(t)
    time_s = (220.0 - t) * 60.0 / 10.0
    rows.append(f"{time_s:.1f},{t:.2f},{hf:.5f}")
    t -= 1.0
write_csv("run2_cooling_ENDO.csv",
          HEADER.format(instrument="DSC-2500", conv="exo-up", segment="cooling", cycle=1, rate=10.0, atm="N2 50 mL/min"), rows)

# ---- run 3: heating cycle 2 (cycle-segment switching), endo-up, uniform spacing, Tg + smaller melt, no crystallization
rows = []
t = 30.0
while t <= 220.0:
    base = 0.50 + 0.002 * (t - 30.0)
    tg = 0.30 * smoothstep((t - 65.0) / 3.0)
    melt = 1.8 * gauss(t, 160.0, 5.0)
    hf = base + tg + melt + noise(t)
    time_s = (t - 30.0) * 60.0 / 10.0
    rows.append(f"{time_s:.1f},{t:.2f},{hf:.5f}")
    t += 1.0
write_csv("run3_heating_cycle2.csv",
          HEADER.format(instrument="DSC-2500", conv="endo-up", segment="heating", cycle=2, rate=10.0, atm="N2 50 mL/min"), rows)

# ---- run 4: heating cycle 3, NO heatFlowConvention header -> direction undecidable, both candidates kept
rows = []
t = 30.0
while t <= 220.0:
    base = 0.50 + 0.002 * (t - 30.0)
    tg = 0.30 * smoothstep((t - 65.0) / 3.0)
    melt = 1.9 * gauss(t, 160.0, 5.0)
    hf = base + tg + melt + noise(t)
    time_s = (t - 30.0) * 60.0 / 10.0
    rows.append(f"{time_s:.1f},{t:.2f},{hf:.5f}")
    t += 1.0
hdr = HEADER.format(instrument="DSC-2500", conv="endo-up", segment="heating", cycle=3, rate=10.0, atm="N2 50 mL/min")
hdr = "\n".join(l for l in hdr.splitlines() if "heatFlowConvention" not in l) + "\n"
write_csv("run4_heating_cycle3_noconv.csv", hdr, rows)
print("fixtures written to", OUT)
