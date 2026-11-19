"""出入口 (doors) x 开敞 (open), any pair: every side with doors has a flight down the platform a player walks (and
no other side has one; 穿堂 with 前 is 前后), and the walls are open where 开敞 says - 全敞开 all four, 正面敞开 the
front only, 封闭 and 穿堂 none.
On a plain platform, a 须弥座 and 三层; an old save's one `open` (穿堂, 前后门, 四面门) reads as its pair.

python test_doors.py
"""
import os
import sys

os.chdir(os.path.dirname(os.path.abspath(__file__)))

import compose
import reach
import siteplan
from arch import AIR, Canvas

G = 64
BASE = dict(y=G, roof="xieshan", walls="palace", theme="gold", front="south", seed=3, uses=["main_room"] * 8,
            x0=0, z0=0, x1=20, z1=14)
PODS = (dict(podium=2, podium_style="plain"), dict(podium=3, podium_style="xumizuo"), dict(podium=6, podium_style="tiers"))
SIDES = {"front": {"S"}, "front_back": {"N", "S"}, "four": {"N", "S", "W", "E"}}
OPEN = {"closed": set(), "front": {"S"}, "open": {"N", "S", "W", "E"}, "through": set()}   # (穿堂: a passage, not a wall)


def check(room):
    cv = Canvas()
    compose.build_compose(dict(tier="mortal", seed=3, rooms=[room]), cv)
    R = reach.reachable(cv.blocks, cv.cols, G)
    top = G + room["podium"] + 1
    m = siteplan._plat(room)
    x0, z0, x1, z1 = room["x0"] - m, room["z0"] - m, room["x1"] + m, room["z1"] + m
    walked = {"N": any(z < z0 and G + 1 < s < top for (x, z, s) in R if x0 <= x <= x1),
              "S": any(z > z1 and G + 1 < s < top for (x, z, s) in R if x0 <= x <= x1),
              "W": any(x < x0 and G + 1 < s < top for (x, z, s) in R if z0 <= z <= z1),
              "E": any(x > x1 and G + 1 < s < top for (x, z, s) in R if z0 <= z <= z1)}
    lines = {"N": [(x, room["z0"]) for x in range(room["x0"] + 1, room["x1"])],
             "S": [(x, room["z1"]) for x in range(room["x0"] + 1, room["x1"])],
             "W": [(room["x0"], z) for z in range(room["z0"] + 1, room["z1"])],
             "E": [(room["x1"], z) for z in range(room["z0"] + 1, room["z1"])]}
    # open: most of the wall line air over the rail (above a door's head, so a doorway alone does not count)
    opened = {k for k, cells in lines.items() if sum(cv.blocks.get((x, top + 2, z)) in (None, AIR) for x, z in cells) * 2 > len(cells)}
    return {k for k, v in walked.items() if v}, opened


def main():
    ok = True
    cases = [(d, o, None) for d in SIDES for o in OPEN] + [(None, None, old) for old in ("through", "back", "four")]
    for d, o, old in cases:
        for p in PODS:
            room = dict(BASE, **p, **(dict(open=old) if old else dict(doors=d, open=o)))
            dd, oo = compose.doors_open(room)
            got, opened = check(room)
            good = got == SIDES[dd] and opened == OPEN[oo]
            ok = ok and good
            print(f"  {old or '':8s} {dd:10s} {oo:6s} {p['podium_style']:8s} flights {''.join(sorted(got)):4s} "
                  f"open {''.join(sorted(opened)) or '-':4s} {'ok' if good else 'NO'}")
    return ok


if __name__ == "__main__":
    import bgen_daemon  # noqa: F401
    res = main()
    print("OK" if res else "FAIL")
    sys.exit(0 if res else 1)
