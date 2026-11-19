"""圆塔 收分 and 平座: a round tower honours its taper (every storey up a smaller circle - its wall there and not out
at the ground storey's radius), every storey's inside is walked to from the ground, and with 平座 every upper storey's
railed walk round it is too (through its door at the front).

python test_round_taper.py
"""
import math
import os
import sys
from collections import defaultdict

os.chdir(os.path.dirname(os.path.abspath(__file__)))

import bgen
import compose
import reach
from arch import Canvas

G = 64
BASE = dict(x0=0, z0=0, y=G, shape="round", roof="xieshan", walls="darkwood", theme="grey", front="south", seed=5,
            uses=["temple_hall", "scripture_store"] * 4, podium=2)
CASES = {"qiniandian-like": dict(BASE, x1=24, z1=24, storeys=3, sh=7, taper=2),
         "yingxian-like": dict(BASE, x1=20, z1=20, storeys=5, sh=7, taper=1, pingzuo=True),
         "pingzuo-no-taper": dict(BASE, x1=14, z1=14, storeys=3, sh=5, pingzuo=True, front="east")}


def main():
    bad = 0
    for name, room in CASES.items():
        cv = Canvas()
        out = compose.build_compose(dict(tier="mortal", seed=3, rooms=[room]), cv)
        R, n, t = room["x1"] // 2, room["storeys"], room.get("taper", 0)
        radii = bgen.round_radii(R, n, t)
        by = defaultdict(set)
        for (x, z, s) in reach.reachable(cv.blocks, cv.cols, G):
            by[(x, z)].add(s)
        msg = []
        for s, (_, _, _, _, fy) in enumerate(out["storeys"][0]):
            Rs = radii[s]
            if cv.blocks.get((R + Rs, fy + 1, R)) in (None, bgen.AIR):          # its wall at its own radius
                msg.append(f"storey {s}: no wall at r={Rs}")
            if Rs < R and cv.blocks.get((R + R, fy + 2, R)) not in (None, bgen.AIR):   # (not out at the ground storey's)
                msg.append(f"storey {s}: wall still at r={R}")
            ins = [(u, v) for u in range(-Rs, Rs + 1) for v in range(-Rs, Rs + 1) if math.hypot(u, v) <= Rs - 0.5]
            if not any(fy + 0.5 <= q <= fy + 1.5 for (u, v) in ins for q in by.get((R + u, R + v), ())):
                msg.append(f"storey {s}: not reached")
            if room.get("pingzuo") and s > 0:
                pe = max(radii[s - 1] + 0.5, Rs + 2.5) + 1
                walk = [(u, v) for u in range(-int(pe), int(pe) + 1) for v in range(-int(pe), int(pe) + 1)
                        if Rs + 0.5 < math.hypot(u, v) <= pe - 1.5]
                got = sum(1 for (u, v) in walk if any(abs(q - fy - 1) < 0.01 for q in by.get((R + u, R + v), ())))
                if got < len(walk):
                    msg.append(f"平座 {s}: {got}/{len(walk)} reached")
        print(f"  {name:16s} radii {radii}  {'ok' if not msg else msg}")
        bad += bool(msg)
    print("FAIL" if bad else "ok", bad)
    return bad


if __name__ == "__main__":
    sys.exit(1 if main() else 0)
