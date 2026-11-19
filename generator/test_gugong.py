"""宫城 (city) walk-through and spacing, one check each:
  tower - a 城台 tower with its stair inside: from where the stair comes up a player walks along the top into the tower
          through its own doors; no hole cut in its wall beside a corner post (the wall at its floor has door blocks
          or wall, nothing open)
  doors - a hall on the main axis opens front and back (出入口 前后), a square one there on all four sides (四面), and
          each of those doors has a flight down its platform that a player walks (a 前后 + 穿堂 gate hall's back
          too, an old save's 穿堂 read so: in at the front, no way down behind it past the railing)
  space - the 宫城 plans and the 随机一院 palace plans have no flight of steps (with WALK cells at its foot) running
          into another building, a wall or a screen, and no two buildings' eaves over the same cells
          (siteplan.crowded) - at the default 45° steps and with every flight at 14° (city seeds 1-6)
  wumen - the 午门 group (the 午门, its two 雁翅楼, their 阙亭) is one 城台 walked on top: from inside the 午门, never
          going down off the 城台, a player gets into every wing and pavilion (city seeds 1-6)
  walltop - the 宫城's outer wall is a 城墙 walked on top: from the ground up a 马道 (a 马道 on every side), never
          climbing anything else, a player walks the whole wall top and onto the 城台 of every tower on it, into each
          角楼 and 城楼 (city seed 1)

python test_gugong.py
"""
import os
import sys

os.chdir(os.path.dirname(os.path.abspath(__file__)))

import arch
import compose
import reach
import siteplan
from arch import Canvas

G = 64
BASE = dict(y=G, roof="xieshan", walls="palace", theme="gold", front="south", seed=3, uses=["main_room"] * 8)


def _build(rooms):
    cv = Canvas()
    compose.build_compose(dict(tier="mortal", seed=3, rooms=rooms), cv)
    return cv


def check_tower():
    r = dict(BASE, x0=0, z0=0, x1=30, z1=14, podium=10, podium_style="chengtai", tunnels=4)
    cv = _build([r])
    floor = G + 10
    holes = [(x, z) for x in range(r["x0"], r["x1"] + 1) for z in (r["z0"], r["z1"])
             if cv.blocks.get((x, floor + 1, z)) in (None, arch.AIR)] \
        + [(x, z) for x in (r["x0"], r["x1"]) for z in range(r["z0"], r["z1"] + 1) if cv.blocks.get((x, floor + 1, z)) in (None, arch.AIR)]
    R = reach.reachable(cv.blocks, cv.cols, G)
    inside = any(r["x0"] < x < r["x1"] and r["z0"] < z < r["z1"] and s >= floor + 1 for (x, z, s) in R)
    print(f"  tower  holes in its wall {len(holes)}  floor reached on foot {inside}")
    return not holes and inside


def check_doors():
    d = siteplan.city(1)
    c = next((r["x0"] + r["x1"]) // 2 for r in d["rooms"] if "throne_hall" in r.get("uses", ()))   # (太和殿: the axis)
    axis = [r for r in d["rooms"] if not r.get("special") and r.get("podium_style") != "chengtai"
            and (r["x0"] + r["x1"]) // 2 == c and r["x1"] - r["x0"] >= 14 and compose.doors_open(r)[1] != "through"]
    ok = axis and all(compose.doors_open(r) == ("four" if r["roof"] == "pyramid" else "front_back", "closed") for r in axis)
    print(f"  doors  axis halls {len(axis)}: {[compose.doors_open(r)[0] for r in axis]}")
    for name, room in (("前后", dict(BASE, x0=0, z0=0, x1=20, z1=12, podium=3, podium_style="xumizuo", doors="front_back")),
                       ("四面", dict(BASE, x0=0, z0=0, x1=20, z1=20, podium=8, podium_style="tiers", roof="pyramid", doors="four")),
                       ("穿堂 (old)", dict(BASE, x0=0, z0=0, x1=16, z1=10, podium=2, open="through"))):
        cv = _build([room])
        R = reach.reachable(cv.blocks, cv.cols, G)
        top = G + room["podium"] + 1
        m = siteplan._plat(room)                 # (the platform's ring, a sill's cell wider: its rim is no flight)
        x0, z0, x1, z1 = room["x0"] - m, room["z0"] - m, room["x1"] + m, room["z1"] + m
        walked = {"N": any(z < z0 and G + 1 < s < top for (x, z, s) in R if x0 <= x <= x1),
                  "S": any(z > z1 and G + 1 < s < top for (x, z, s) in R if x0 <= x <= x1),
                  "W": any(x < x0 and G + 1 < s < top for (x, z, s) in R if z0 <= z <= z1),
                  "E": any(x > x1 and G + 1 < s < top for (x, z, s) in R if z0 <= z <= z1)}
        want = {"N", "S", "W", "E"} if compose.doors_open(room)[0] == "four" else {"N", "S"}
        got = {k for k, v in walked.items() if v}
        print(f"  doors  {name}: flights walked {sorted(got)} (want {sorted(want)})")
        ok = ok and got == want
    return ok


def check_space():
    ok = True
    for slope in ("45", "14"):
        run = arch.step_run
        siteplan.step_run = lambda s, f=run, slope=slope: f(slope)
        try:
            plans = [("city", n, siteplan.city(n)) for n in range(1, 7)] + [("palace", j, siteplan.site("palace", j, 1)) for j in (1, 2, 3)]
            for kind, n, d in plans:
                c = siteplan.crowded(d["rooms"])
                if c:
                    print(f"  space  {slope}deg {kind} {n}: {len(c)} {c[:2]}")
                ok = ok and not c
        finally:
            siteplan.step_run = run
    print(f"  space  crowded plans at 45deg and 14deg: {'none' if ok else 'some'}")
    return ok


def check_wumen():
    ok = True
    for n in range(1, 7):
        d = siteplan.city(n)
        cts = [dict(r, y=G) for r in d["rooms"] if r.get("podium_style") == "chengtai" and not r.get("special")]
        wu = max(cts, key=lambda r: int(r.get("tunnels", 0) or 0))
        grp = [wu]                                  # (the towers on one 城台 with it: siteplan._joined)
        for r in grp:
            grp += [o for o in cts if o not in grp and siteplan._joined(r, o)]
        rest = [r for r in grp if r is not wu]
        cv = _build([wu] + rest)
        top = compose._floor(wu)
        stands = reach.stands                       # (up on the 城台 only: not down and up another tower's stair)
        reach.stands = lambda g, x, z, a, b: {s: c for s, c in stands(g, x, z, a, b).items() if s >= top - 0.5}
        try:
            cx, cz = (wu["x0"] + wu["x1"]) // 2, (wu["z0"] + wu["z1"]) // 2
            R = reach.reachable(cv.blocks, cv.cols, G, starts=[(x, cz, float(s)) for x in range(cx - 3, cx + 4)
                                                               for s in (top, top + 0.5, top + 1)])
        finally:
            reach.stands = stands
        cut = [(r["x0"], r["z0"], r["x1"], r["z1"]) for r in rest
               if not any(r["x0"] < x < r["x1"] and r["z0"] < z < r["z1"] and s >= top for (x, z, s) in R)]
        if len(rest) != 4 or cut:
            print(f"  wumen  city {n}: {len(rest)} wings/pavilions, cut off on top {cut}")
            ok = False
    print(f"  wumen  午门 - 雁翅楼 - 阙亭 walked on the 城台, city 1-6: {'all joined' if ok else 'cut'}")
    return ok


def check_walltop(n=1):
    d = siteplan.city(n)
    rooms = [dict(r, y=G) for r in d["rooms"]]
    cv = _build(rooms)
    top = G + siteplan.WH
    walls = [r for r in rooms if r.get("special") == "wall" and min(r["x1"] - r["x0"], r["z1"] - r["z0"]) + 1 >= 7]
    mad = [r for r in walls if siteplan._madao(r)]
    towers = [r for r in rooms if r.get("podium_style") == "chengtai" and int(r.get("podium", 0) or 0) == siteplan.WH]
    stands = reach.stands                           # (on the ground, up on the wall, or on a 马道: nothing else climbed)
    reach.stands = lambda g, x, z, a, b: {s: c for s, c in stands(g, x, z, a, b).items() if s <= G + 2 or s >= top + 0.5
                                          or any(r["x0"] <= x <= r["x1"] and r["z0"] <= z <= r["z1"] for r in mad)}
    try:
        R = reach.reachable(cv.blocks, cv.cols, G)
    finally:
        reach.stands = stands
    cut = []
    for r in walls:                                 # down the middle of each piece's walk, end to end
        cx, cz = (r["x0"] + r["x1"]) // 2, (r["z0"] + r["z1"]) // 2
        line = [(x, cz) for x in range(r["x0"], r["x1"] + 1)] if r["front"] in ("north", "south") else \
            [(cx, z) for z in range(r["z0"], r["z1"] + 1)]
        if any((x, z, float(top + 1)) not in R for x, z in line):
            cut.append((r["x0"], r["z0"], r["x1"], r["z1"]))
    out = [(t["x0"], t["z0"], t["x1"], t["z1"]) for t in towers
           if not any(t["x0"] < x < t["x1"] and t["z0"] < z < t["z1"] and s >= top + 1 for (x, z, s) in R)]
    sides = {r["front"] for r in mad}
    print(f"  walltop city {n}: {len(walls)} wall pieces, 马道 {len(mad)} on {sorted(sides)}, walk cut {cut or 'nowhere'},"
          f" towers {len(towers) - len(out)}/{len(towers)} entered from the top {out or ''}")
    return len(sides) == 4 and not cut and not out and len(towers) >= 8


if __name__ == "__main__":
    import bgen_daemon  # noqa: F401
    res = [check_tower(), check_doors(), check_space(), check_wumen(), check_walltop()]
    print("OK" if all(res) else "FAIL")
    sys.exit(0 if all(res) else 1)
