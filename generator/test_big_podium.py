"""大台基: a platform far bigger than the room on it - 台基边距 past 4, 台基形状 (方 / 圆) apart from the room's own, 三层
stepping out wide with a flight down each tier, a 城台's tunnels crossing under a 四面门 tower. Each case: the player
walks from the ground onto every tier and into the room's floor; the platform (its flights aside) stays inside what
siteplan reserves for it (siteplan._podium_out); a 城台 with 四面门 is open through on both axes.

python test_big_podium.py
"""
import os
import sys

os.chdir(os.path.dirname(os.path.abspath(__file__)))

import compose
import reach
import siteplan
from arch import Canvas

G = 64
BASE = dict(y=G, x0=0, z0=0, roof="xieshan", walls="palace", theme="gold", front="south", seed=3, uses=["main_room"] * 8,
            storeys=1, sh=6, base="white")
CASES = (
    ("三层 边距12", dict(BASE, x1=20, z1=12, podium=6, podium_style="tiers", podium_margin="12"), (2, 4, 6)),
    ("圆塔 三层 边距16", dict(BASE, x1=16, z1=16, podium=6, podium_style="tiers", podium_margin="16", shape="round", round=True,
                          round_roof="cone"), (2, 4, 6)),
    ("方殿 圆台 三层", dict(BASE, x1=14, z1=10, podium=6, podium_style="tiers", podium_shape="round", podium_margin="6",
                       roof="pyramid", doors="four"), (2, 4, 6)),
    ("圆塔 方城台 四面", dict(BASE, x1=14, z1=14, podium=9, podium_style="chengtai", podium_shape="square", podium_margin="6",
                          shape="round", round=True, round_roof="cone", doors="four", tunnels=2), (9,)),
    ("方楼 城台 四面", dict(BASE, x1=20, z1=20, podium=9, podium_style="chengtai", podium_margin="5", roof="pyramid",
                       doors="four", tunnels=2), (9,)),
)


def solid(cv, x, y, z):
    st = cv.blocks.get((x, y, z))
    if st is None:
        st = next((s for y0, y1, s in cv.cols.get((x, z), ()) if y0 <= y <= y1), None)
    return st not in (None, "minecraft:air")


def main():
    bad = 0
    for name, room, tops in CASES:
        cv = Canvas()
        compose.build_compose(dict(tier="mortal", seed=3, rooms=[dict(room)]), cv)
        R = reach.reachable(cv.blocks, cv.cols, G)
        feet = {s for (x, z, s) in R}
        cx, cz = (room["x0"] + room["x1"]) // 2, (room["z0"] + room["z1"]) // 2
        floor = G + room["podium"] + 1
        inside = any(room["x0"] < x < room["x1"] and room["z0"] < z < room["z1"] and s == floor for (x, z, s) in R)
        tiers = all(G + t + 1 in feet for t in tops)
        out = siteplan._podium_out(room)
        over = [(x, z) for (x, y, z) in cv.blocks if G < y <= G + room["podium"] and abs(x - cx) > 5 and abs(z - cz) > 5
                and not (room["x0"] - out <= x <= room["x1"] + out and room["z0"] - out <= z <= room["z1"] + out)]
        ok = inside and tiers and not over
        msg = ""
        if room.get("podium_style") == "chengtai":       # 四面: a way through front to back and side to side
            ns = all(not solid(cv, cx, G + 1, z) and not solid(cv, cx, G + 2, z) for z in range(room["z0"] - 6, room["z1"] + 7))
            ew = all(not solid(cv, x, G + 1, cz) and not solid(cv, x, G + 2, cz) for x in range(room["x0"] - 6, room["x1"] + 7))
            ok = ok and ns and ew
            msg = f"  through N-S {ns} E-W {ew}"
        print(f"  {name:12s} floor reached {inside}  tiers {sorted(int(f - G - 1) for f in feet if f - G - 1 in tops)}"
              f"  outside reserve {len(over)}{msg}  {'ok' if ok else 'NO'}")
        bad += not ok
    print("FAIL" if bad else "ok", bad)
    return bad


if __name__ == "__main__":
    sys.exit(1 if main() else 0)
