"""流水线 (programmatic, no model): target -> 单体建造 design -> generate -> 自检 -> measure -> compare -> adjust, repeated.

A target says what a building should come out as - its frame (width, depth), roof form, 重檐, platform - and the
numbers to hit: the height of the ridge over the ground, the height of the eaves, the share of the height that is
roof. Any source: a sentence, a photo measured, published dimensions (data/targets/*.json are of the second kind).
The search tries every 层高 with every 坡度 (they trade off), then walks the settings (层高, 坡度, 出檐, 台基高) one at
a time, keeping a change when the generated building comes closer, until no single change helps; the best design must
pass 自检 (if it does not, the best of its one-step neighbours that does is taken).

A reference map is only for scoring afterwards (python pipeline.py score): its numbers never steer the search.

python pipeline.py fit [targets.json]   fit every target, write out/pipeline/<name>.json (a design) and report.txt
python pipeline.py score <measure.json>  score those designs against a reference (refmap/measure.py), score.txt
"""
import json
import os
import sys
from pathlib import Path

HERE = Path(__file__).parent
os.chdir(HERE)
sys.path.insert(0, str(HERE))

import bgen_daemon  # noqa: F401,E402  (the kinds and materials as the daemon has them)
import compose  # noqa: E402
import selfcheck  # noqa: E402
from arch import Canvas  # noqa: E402

GROUND = 64
AXES = (("sh", (5, 6, 7, 8, 9, 10, 12, 14, 16)), ("pitch", (1, 2, 3, 4)), ("overhang", (1, 2, 3, 4)), ("podium", (0, 1, 2, 3, 4, 5, 6, 8, 10, 12)))


def design(t, p):
    """The 单体建造 room for a target with settings p."""
    w, d = t["w"] | 1, t["d"] | 1
    r = dict(x0=0, z0=0, x1=w - 1, z1=d - 1, y=GROUND, storeys=t.get("storeys", 1), roof=t["roof"], walls=t.get("walls", "palace"),
             theme=t.get("theme", "gold"), front="south", seed=t.get("seed", 1), uses=[t.get("use", "council_hall")] * 8,
             double_eave=t.get("double_eave", False), podium_style=t.get("podium_style", "plain"), base=t.get("base", "white"),
             caihua=t.get("caihua", "hexi"), beasts=t.get("beasts", 3), brackets=t.get("brackets", 0))
    r.update(p)
    return r


def measure(blocks, room):
    """Heights over the ground: the ridge (the highest block over the frame), the eaves (the underside of the roof
    just outside the front wall, in the middle), the floor (the platform's top); and the roof's share of the height."""
    x0, x1, z0, z1 = room["x0"], room["x1"], room["z0"], room["z1"]
    over = [y for (x, y, z) in blocks if x0 <= x <= x1 and z0 <= z <= z1]
    ridge = max(over) - GROUND
    cx = (x0 + x1) // 2
    cz = (z0 + z1) // 2
    floor = min((y for (x, y, z), s in blocks.items() if x == cx and z == cz and y >= GROUND and not s.endswith(":air")),
                default=GROUND) - GROUND                       # (the floor block in the middle of the room)
    lows = []                                         # the eave: along the front, two out from the wall, the lowest roof tile
    for x in range(x0, x1 + 1):                       # over each column (a door's hood or a lamp is not a tile), the middle one
        ys = [y for (xx, y, z), s in blocks.items() if xx == x and z == z1 + 2 and y - GROUND > floor + 2
              and s.split("[")[0].endswith(("_stairs", "_slab")) and "trapdoor" not in s]
        if ys:
            lows.append(min(ys) - GROUND)
    eave = sorted(lows)[len(lows) // 2] if lows else ridge
    return dict(ridge=ridge, eave=eave, floor=floor, roof_share=round((ridge - eave) / max(1, ridge), 3))


def error(m, t):
    e = 0.0
    for k, wgt in (("ridge", 1.0), ("eave", 1.0), ("floor", 0.5)):
        if k in t:
            e += wgt * abs(m[k] - t[k])
    if "roof_share" in t:
        e += 20 * abs(m["roof_share"] - t["roof_share"])
    return e


def build(r):
    cv = Canvas()
    compose.build_compose(dict(tier="mortal", seed=3, rooms=[r]), cv)
    return cv


def fit(t, passes=3):
    p = dict(sh=7, pitch=3, overhang=2, podium=t.get("podium", 0))
    fixed = set(t.get("fixed", ()))
    best_m = measure(build(design(t, p)).blocks, design(t, p))
    best = error(best_m, t)
    tried = 1
    for sh in AXES[0][1]:                             # the storey height and the pitch together first (they trade off:
        for pitch in AXES[1][1]:                      # a lower wall under a steeper roof reaches the same ridge)
            q = dict(p, sh=sh, pitch=pitch)
            r = design(t, q)
            m = measure(build(r).blocks, r)
            tried += 1
            e = error(m, t)
            if e < best - 1e-9:
                best, best_m, p = e, m, q
    for _ in range(passes):
        changed = False
        for axis, values in AXES:
            if axis in fixed:
                continue
            for v in values:
                if v == p[axis]:
                    continue
                q = dict(p, **{axis: v})
                r = design(t, q)
                m = measure(build(r).blocks, r)
                tried += 1
                e = error(m, t)
                if e < best - 1e-9:
                    best, best_m, p, changed = e, m, q, True
        if not changed:
            break
    r = design(t, p)
    cv = build(r)
    check = {k: len(v) for k, v in selfcheck.check(cv.blocks, GROUND, cv.cols).items() if v}
    if check:                                         # 自检 failed: the next best settings that pass it instead
        ranked = []
        for axis, values in AXES:
            for v in values:
                q = dict(p, **{axis: v})
                rr = design(t, q)
                cq = build(rr)
                tried += 1
                if not any(selfcheck.check(cq.blocks, GROUND, cq.cols).values()):
                    ranked.append((error(measure(cq.blocks, rr), t), q))
        if ranked:
            best, p = min(ranked, key=lambda e: e[0])
            r = design(t, p)
            best_m = measure(build(r).blocks, r)
            check = {}
    return dict(name=t["name"], settings=p, measured=best_m, target={k: t[k] for k in ("ridge", "eave", "floor", "roof_share") if k in t},
                error=round(best, 2), tried=tried, selfcheck=check, room=r)


def main():
    src = Path(sys.argv[2]) if len(sys.argv) > 2 else HERE / "data" / "targets" / "forbidden_city.json"
    targets = json.loads(src.read_text(encoding="utf-8"))["targets"]
    out = HERE / "out" / "pipeline"
    out.mkdir(parents=True, exist_ok=True)
    lines = []
    for t in targets:
        res = fit(t)
        room = dict(res.pop("room"))
        room.pop("y", None)
        d = dict(name="流水线·" + t["name"], tier="mortal", look=0, seed=1, rooms=[dict(room, y=0)])
        (out / f"{t['name']}.json").write_text(json.dumps(d, ensure_ascii=False, indent=1), encoding="utf-8")
        line = (f"{t['name']}: error {res['error']} after {res['tried']} builds; settings {res['settings']}; "
                f"measured {res['measured']} vs target {res['target']}; selfcheck {res['selfcheck'] or 'clean'}")
        print(line, flush=True)
        lines.append(line)
    (out / "report.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")


def score(ref_path):
    """The fitted designs (out/pipeline/*.json) against a reference's measurements (refmap/measure.py's output): the
    shares that do not depend on scale - the roof's and the platform's share of the height. Only reported."""
    ref = json.loads(Path(ref_path).read_text(encoding="utf-8"))
    out = HERE / "out" / "pipeline"
    lines, diffs = [], []
    for name, m_ref in ref.items():
        p = out / f"{name}.json"
        if not p.exists():
            continue
        r = dict(json.loads(p.read_text(encoding="utf-8"))["rooms"][0], y=GROUND)
        m = measure(build(r).blocks, r)
        ours = dict(roof_share=m["roof_share"], floor_share=round(m["floor"] / max(1, m["ridge"]), 3))
        d = {k: round(ours[k] - m_ref[k], 3) for k in ours}
        diffs += [abs(v) for v in d.values()]
        lines.append(f"{name}: ours {ours} / reference {dict(roof_share=m_ref['roof_share'], floor_share=m_ref['floor_share'])} / off {d}")
    lines.append(f"mean off {round(sum(diffs) / max(1, len(diffs)), 3)} over {len(diffs)} shares")
    text = "\n".join(lines) + "\n"
    print(text, end="")
    (out / "score.txt").write_text(text, encoding="utf-8")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "fit":
        main()
    elif len(sys.argv) > 2 and sys.argv[1] == "score":
        score(sys.argv[2])
    else:
        print(__doc__)
