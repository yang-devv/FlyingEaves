"""凡人城镇's own test: builds towns offline (town.town -> compose.build_compose, rolling ground made from the seed)
and checks what a town promises. Per town:
  buildings   - how many (rooms that are not ground, walls, gates or ornaments)
  overlaps    - pairs of buildings whose blocks (each built on its own) stand in the same column, not counting
                buildings wall to wall (compose joins those into one)
  unreachable - storeys a player cannot walk to from the street (reach.reachable from every street cell), ground
                floors and upper floors
  selfcheck   - selfcheck.check's findings (floating, low, door, drop, loose, slit, leak)
  times       - planning and building, seconds

python towncheck.py [SEED ...] [--size village|town|big] [--layout line|T|cross|Y|bank|grid] [--box WxD] [--hills NAME]
                    [--dvcr DIR]
(default seeds 1-6, the size and the street plan by the seed; --box: the town sized to a box of W x D blocks; --hills:
one of HILLS, the game's heightmap made from a formula instead of the seed's rolling ground; exits 1 if any town fails
a check)
"""
import math
import os
import sys
import time
from collections import defaultdict

import compose
import reach
import selfcheck
import town
from arch import Canvas

AIR = "minecraft:air"


def footprints(d):
    """Each building built on its own: the columns its blocks stand in above its ground."""
    fps = {}
    for i, r in enumerate(d["rooms"]):
        if r.get("special"):
            continue
        cv = Canvas()
        compose.build_compose(dict(tier="mortal", seed=1, rooms=[dict(r)]), cv)
        fps[i] = {(x, z) for (x, y, z), s in cv.blocks.items() if s != AIR and y > r["y"]}
    return fps


def overlaps(d):
    fps = footprints(d)
    idx = sorted(fps)
    par = {i: i for i in idx}

    def f(i):
        while par[i] != i:
            i = par[i]
        return i
    for a in idx:                                   # wall to wall: one building
        for b in idx:
            if a < b and town.wall_to_wall(d["rooms"][a], d["rooms"][b]):
                par[f(a)] = f(b)
    return [(a, b, len(fps[a] & fps[b])) for i, a in enumerate(idx) for b in idx[i + 1:]
            if f(a) != f(b) and fps[a] & fps[b]]


def unreachable(d, cv, res):
    """(ground floors, upper floors) no one gets to on foot from the street."""
    g = reach.Grid(cv.blocks, cv.cols, res["floor"])
    starts = []
    for r in d["rooms"]:
        if r.get("special") != "street":
            continue
        for x in range(r["x0"], r["x1"] + 1):
            for z in range(r["z0"], r["z1"] + 1):
                gy = res["ground"].get((x, z))
                if gy is not None:
                    starts += [(x, z, s) for s in reach.stands(g, x, z, gy, gy + 1)]
    by = defaultdict(set)
    for (x, z, s) in reach.reachable(cv.blocks, cv.cols, res["floor"], starts=starts):
        by[(x, z)].add(s)
    ground, upper = [], []
    for k, st in enumerate(res["storeys"]):
        for si, (x0, z0, x1, z1, fy) in enumerate(st):
            if not any(fy + 0.5 <= s <= fy + 1.5 for x in range(x0, x1 + 1) for z in range(z0, z1 + 1) for s in by.get((x, z), ())):
                (ground if si == 0 else upper).append((k, si, (x0, z0, x1, z1, fy)))
    return ground, upper


HILLS = {   # synthetic ground as the game samples it (R = 160 round the origin)
    "flat": lambda x, z: 64,
    "hills20": lambda x, z: 70 + 10 * math.sin(x / 35.0) * math.cos(z / 50.0) + 5 * math.sin(z / 23.0 + 1),
    "slope": lambda x, z: 64 + 0.18 * x + 0.12 * z,
    "steep": lambda x, z: 64 + 0.35 * z + 4 * math.sin(x / 19.0),
    "valley": lambda x, z: 64 + 0.004 * x * x + 3 * math.sin(z / 30.0),
    "holes": lambda x, z: -9999 if (x * 7 + z * 13) % 11 == 0 else 66 + 3 * math.sin(z / 40.0),
}


def heightmap(name, R=160):
    f = HILLS[name]
    return dict(R=R, h=[int(round(f(x, z))) if f(x, z) > -9000 else -9999 for z in range(-R, R + 1) for x in range(-R, R + 1)])


def check_town(seed, dvcr=None, size=None, layout=None, hills=None, box=None):
    t0 = time.time()
    R = 160 if not box else max(160, max(town.town_box(box)) // 2 + 20)
    d = town.town(seed, heights=heightmap(hills, R) if hills else None, size=size, layout=layout, box=box)
    tp = time.time() - t0
    cv = Canvas()
    t1 = time.time()
    res = compose.build_compose(dict(tier="mortal", seed=seed, rooms=d["rooms"]), cv)   # (地块 and all, as the game sends it)
    tb = time.time() - t1
    d = dict(d, rooms=compose.expand_rooms(d["rooms"]), patched=len(d["rooms"]))   # (the checks look at the pieces)
    found = {k: v for k, v in selfcheck.check(cv.blocks, res["floor"], cv.cols).items() if v}
    ug, uu = unreachable(d, cv, res)
    ov = overlaps(d)
    if dvcr:
        cv.export(os.path.join(dvcr, f"town_{seed}.dvcr"))
    return dict(seed=seed, name=d["name"], buildings=d["buildings"], rooms=d.get("patched", len(d["rooms"])), pieces=len(d["rooms"]), size=d["size"],
                town_size=d.get("town_size"), layout=d.get("layout"), box=d.get("box"),
                overlaps=ov, unreach_ground=ug, unreach_upper=uu, selfcheck=found, plan_s=tp, build_s=tb)


def main(argv):
    def opt(k):
        return argv[argv.index(k) + 1] if k in argv else None
    dvcr, size, layout, hills, box = opt("--dvcr"), opt("--size"), opt("--layout"), opt("--hills"), opt("--box")
    seeds = [int(a) for i, a in enumerate(argv) if a.isdigit() and (i == 0 or argv[i - 1] not in ("--dvcr",))] or list(range(1, 7))
    bad = 0
    for seed in seeds:
        r = check_town(seed, dvcr, size, layout, hills, box)
        fails = len(r["overlaps"]) + len(r["unreach_ground"]) + len(r["unreach_upper"]) + sum(len(v) for v in r["selfcheck"].values())
        bad += bool(fails)
        print(f"seed {seed} {r['town_size']} {r['layout']}" + (f" box {r['box'][0]}x{r['box'][1]}" if r["box"] else "") + ": "
              f"{r['buildings']} buildings, {r['rooms']} rooms ({r['pieces']} pieces), {r['size'][0]}x{r['size'][1]}, "
              f"plan {r['plan_s']:.2f}s build {r['build_s']:.1f}s | overlaps {len(r['overlaps'])} | unreachable "
              f"{len(r['unreach_ground'])} ground, {len(r['unreach_upper'])} upper | selfcheck "
              f"{ {k: len(v) for k, v in r['selfcheck'].items()} }" + ("" if not fails else "  FAIL"), flush=True)
        for a, b, n in r["overlaps"][:3]:
            print("    overlap", n, "cells: rooms", a, b)
        for k, si, rect in (r["unreach_ground"] + r["unreach_upper"])[:3]:
            print("    unreachable storey", si, rect)
        for k, v in r["selfcheck"].items():
            print("    selfcheck", k, sorted(v)[:3])
    print(f"{len(seeds) - bad} of {len(seeds)} towns clean")
    return 1 if bad else 0


if __name__ == "__main__":
    os.chdir(os.path.dirname(os.path.abspath(__file__)))
    sys.exit(main(sys.argv[1:]))
