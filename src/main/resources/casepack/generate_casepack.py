#!/usr/bin/env python3
"""生成"热转折案"离线案例包的仪器输出 CSV（仓库内 fixture，可重复生成）。

seg1 升温：玻璃化阶梯 + 熔融峰（EXO_POSITIVE，即熔融在文件中为负——反向热流）
seg2 降温：结晶峰（ENDO_POSITIVE，冷却段）
seg3 升温：两个重叠峰 + 非等间隔温度网格 + 一个无法换算的标记点
"""
import math
import os

HERE = os.path.dirname(os.path.abspath(__file__))
SEG = os.path.join(HERE, "segments")
os.makedirs(SEG, exist_ok=True)

HEADER = "# instrument=TA-DSC-Q2500; sign={sign}; t_unit=C; hf_unit=mW; generated=deterministic\n"


def gauss(x, mu, s, a):
    return a * math.exp(-0.5 * ((x - mu) / s) ** 2)


def step(x, x0, w, a):
    return a / (1 + math.exp(-(x - x0) / w))


def seg1():
    # 文件内热流单位 mW，EXO_POSITIVE：吸热(玻璃阶跃基线变化、熔融)在文件中为负方向
    rows = []
    for i in range(211):  # 20..125C, 0.5C
        t = 20.0 + 0.5 * i
        y_mW = 0.01 * (t - 20.0) - step(t, 55.0, 1.5, 4.0) - gauss(t, 95.0, 3.0, 30.0)
        rows.append((t, y_mW))
    return rows, "EXO_POSITIVE"


def seg2():
    # 冷却 130..20C，ENDO_POSITIVE：结晶放热为负峰
    rows = []
    for i in range(221):  # 130..20, 0.5
        t = 130.0 - 0.5 * i
        y_mW = -0.008 * (t - 20.0) - gauss(t, 78.0, 2.4, 26.0)
        rows.append((t, y_mW))
    return rows, "ENDO_POSITIVE"


def seg3():
    # 升温 20..130C，非等间隔：ΔT 在 0.4/0.5/0.7 间循环
    rows = []
    t = 20.0
    i = 0
    while t <= 130.001:
        y_mW = 0.012 * (t - 20.0) + gauss(t, 88.0, 2.2, 20.0) + gauss(t, 96.0, 2.6, 16.0)
        rows.append((t, y_mW))
        dt = (0.4, 0.5, 0.7)[i % 3]
        t += dt
        i += 1
    # 在重叠峰区域植入一个仪器无法换算的标记点（饱和）
    for k, (tt, _) in enumerate(rows):
        if abs(tt - 92.0) < 0.25:
            rows[k] = (tt, float("nan"))
    return rows, "ENDO_POSITIVE"


def write(name, rows, sign, raw_token):
    with open(os.path.join(SEG, name), "w", encoding="utf-8") as f:
        f.write(HEADER.format(sign=sign))
        f.write("index,temperature_C,heat_flow_mW\n")
        for idx, (t, y) in enumerate(rows, start=1):
            ts = f"{t:.4f}"
            if isinstance(y, float) and math.isnan(y):
                ys = raw_token
            else:
                ys = f"{y:.5f}"
            f.write(f"{idx},{ts},{ys}\n")


write("seg1_heating.csv", *seg1(), "SATURATED")
write("seg2_cooling.csv", *seg2(), "SATURATED")
write("seg3_heating_overlap.csv", *seg3(), "OVERLOAD")
print("generated", sorted(os.listdir(SEG)))
