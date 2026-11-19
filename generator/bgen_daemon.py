"""The generator behind 营造 (Flying Eaves): the game drops requests into <run>/requests/, this daemon generates each
building with bgen and writes a column-run file the game then stamps (with undo).

Request (JSON): {"id": "...", "kind": "inn", "tier": "mortal", "seed": 12, "x": 0, "y": 64, "z": 0, "facing": "south"}
Result: <run>/out/<id>.dvcr and <run>/out/<id>.json ({"ok": true, "blocks": n, "bbox": [...]} or {"ok": false, "error": ...})
On start it writes <run>/kinds.json (what the in-game menu lists).
While a request waits or is built: <run>/out/<id>.progress.json (progress.py), gone once the result is written.

python bgen_daemon.py [--run DIR] [--watch-stdin]
  --run DIR       the working folder (the mod passes <game dir>/flyingeaves; default: generator/run)
  --watch-stdin   exit when standard input closes (the mod keeps it open: when the game goes, so does the daemon)
Only one daemon serves a folder (<run>/daemon.lock); a second one exits at once.
Pure Python 3.10+, standard library only.
"""
import os
import sys


def _args(argv):
    run, watch = None, False
    i = 0
    while i < len(argv):
        if argv[i] == "--run" and i + 1 < len(argv):
            run = argv[i + 1]
            i += 1
        elif argv[i] == "--watch-stdin":
            watch = True
        i += 1
    return run, watch


if __name__ == "__main__":
    _run, _WATCH = _args(sys.argv[1:])
    if _run:
        os.environ["FLYINGEAVES_RUN"] = os.path.abspath(_run)   # (before paths is imported by the modules below)
else:
    _WATCH = False

import json
import threading
import time
import traceback
from pathlib import Path

import paths
import arch
import bgen
import sites  # noqa: F401  (registers the special-site kinds)
import compose
import materials
import parts
from progress import Progress
import furniture as FU
from arch import B, Canvas, Frame

RUN = paths.RUN
REQ, OUT = RUN / "requests", RUN / "out"
REQ.mkdir(parents=True, exist_ok=True)
OUT.mkdir(parents=True, exist_ok=True)
PROG = Progress(OUT, REQ)   # <id>.progress.json while a request waits or is built (the game shows it)

MORTAL_ARCH = {"thatched_hut", "mud_house", "stone_cottage", "log_cabin", "stilt_house", "cave_dwelling", "north_courtyard",
               "jiangnan_house", "huizhou_house", "tulou", "fisher_shack"}


def group(name, spec):
    if spec["tiers"][0] == "mortal":
        return "凡人·住宅" if name in MORTAL_ARCH else "凡人·城镇"
    if spec["arch"] in ("cloud_port", "spirit_mine", "beast_hill", "trial_ground", "forbidden", "tomb"):
        return "宗门·特殊地点"
    return "修士"


def write_presets():
    """The built-in presets (data/presets/*.json, designs like the player's saves) into run/bgen/presets/ for the room
    table's 预设 tab."""
    out = RUN / "presets"
    out.mkdir(parents=True, exist_ok=True)
    src = Path(__file__).parent / "data" / "presets"
    for p in out.glob("*.json"):                        # (ones that are gone from data go here too)
        if not (src / p.name).exists():
            p.unlink()
    for p in sorted(src.glob("*.json")):
        (out / p.name).write_bytes(p.read_bytes())


def write_library():
    """预设库: the built-in designs (data/library/**, a folder tree) into run/bgen/library/ for the room table."""
    import shutil
    src, out = Path(__file__).parent / "data" / "library", RUN / "library"
    try:
        if out.exists():
            shutil.rmtree(out)
        if src.exists():
            shutil.copytree(src, out)
            print("library", sum(1 for _ in out.rglob("*.json")), flush=True)
    except Exception:
        traceback.print_exc()


def write_sites():
    """总平面图: the compounds for 随机一院 (siteplan.py: houses and palaces of one to three courts) into run/bgen/sites/."""
    import siteplan
    try:
        print("sites", siteplan.write_all(RUN / "sites"), flush=True)
    except Exception:
        traceback.print_exc()


def write_kinds():
    kinds = [dict(name=n, cn=s["cn"], tiers=list(s["tiers"]), group=group(n, s)) for n, s in bgen.K.items()]
    tmp = RUN / "kinds.json.tmp"
    tmp.write_text(json.dumps(dict(tiers=bgen.TIERS, tier_cn=["凡人", "练气", "筑基", "金丹", "元婴", "化神"], kinds=kinds,
                                   compose=compose.options()), ensure_ascii=False), encoding="utf-8")
    tmp.replace(RUN / "kinds.json")
    write_presets()
    write_sites()
    write_library()
    print("kinds", len(kinds), flush=True)


def _plant(st):
    return any(p in st for p in ("bamboo", "sapling", "flower", "fern", "grass", "sugar_cane", "tulip", "dandelion", "poppy",
                                 "orchid", "allium", "bluet", "daisy", "lily_of", "rose", "peony", "lilac", "_bush"))


def _standing(st):
    """Things that stand on the ground on their own (a fence, a lamp post, a wall post, a sign or banner pole, a plant):
    the only things the footing props up. Eaves, roof edges, brackets and balconies hang from the building."""
    name = st.split("[")[0]
    if any(k in name for k in ("wall_banner", "wall_sign", "wall_torch", "wall_head", "wall_hanging", "trapdoor", "fence_gate")):
        return False
    if "lantern" in name:
        return "hanging=true" not in st
    return _plant(st) or "fence" in name or name.endswith("_wall") or name.endswith("_sign") or name.endswith("_banner")         or "bamboo" in name or name.endswith("_post")


CLEAR_NATURAL = "minecraft:structure_void"     # a run of it: clear the natural terrain in those cells (CanvasStamp)


def ground_grid(x0, z0, x1, z1, ground, bases, y):
    """The ground y of every column of the box x0..x1, z0..z1 (rows by z, lists), as build's base_y has it - the ground
    a town laid there, else the floor of the nearest room (of two as near, the higher). A town lays ground under every
    column it uses (clear_box looks at no other), so then the rooms are not looked at. Else a distance transform (a 1:1
    宫城 is ~800x1000 columns and ~600 rooms): (distance, -floor) is one number, distance * K + OFF - floor; every cell
    of a room starts at its number, then two sweeps along the rows and two down the columns add K a step - the
    city-block distance is the distance across plus the distance along, so that is exact."""
    w, d = x1 - x0 + 1, z1 - z0 + 1
    if ground or not bases:
        g = [[y] * w for _ in range(d)]
    else:
        K, OFF, INF = 1 << 16, 1 << 15, 1 << 62
        ax0 = min(x0, min(b[0][0] for b in bases))       # (the grid covers the rooms too: one may stand past the box)
        az0 = min(z0, min(b[0][1] for b in bases))
        ax1 = max(x1, max(b[0][2] for b in bases))
        az1 = max(z1, max(b[0][3] for b in bases))
        W, D = ax1 - ax0 + 1, az1 - az0 + 1
        rows = [[INF] * W for _ in range(D)]
        for (bx0, bz0, bx1, bz1), by, _ in bases:
            c = OFF - by
            a, b = bx0 - ax0, bx1 - ax0 + 1
            fill = [c] * (b - a)
            for k in range(bz0 - az0, bz1 - az0 + 1):
                r = rows[k]
                r[a:b] = map(min, r[a:b], fill)
        for k in range(D):
            r = rows[k]
            for i in range(1, W):
                if r[i - 1] + K < r[i]:
                    r[i] = r[i - 1] + K
            for i in range(W - 2, -1, -1):
                if r[i + 1] + K < r[i]:
                    r[i] = r[i + 1] + K
        for k in range(1, D):
            rows[k] = list(map(min, rows[k], [v + K for v in rows[k - 1]]))
        for k in range(D - 2, -1, -1):
            rows[k] = list(map(min, rows[k], [v + K for v in rows[k + 1]]))
        g = [[OFF - v % K for v in rows[k][x0 - ax0:x1 - ax0 + 1]] for k in range(z0 - az0, z1 - az0 + 1)]
    for (gx, gz), gy in ground.items():
        if x0 <= gx <= x1 and z0 <= gz <= z1:
            g[gz - z0][gx - x0] = gy
    return g


def clear_box(cv, cols, frames, top, ground, bases, y):
    """Every column of the bounding box (and of the frames) the building does not stand in gets a run over its ground
    up to the top: air in the frames, CLEAR_NATURAL everywhere else. Columns it stands in were done by build."""
    x0, z0, x1, z1 = cv.bbox()
    if frames:
        x0, z0 = min(x0, min(f[0] for f in frames)), min(z0, min(f[1] for f in frames))
        x1, z1 = max(x1, max(f[0] for f in frames)), max(z1, max(f[1] for f in frames))
    g = ground_grid(x0, z0, x1, z1, ground, bases, y)
    air = B("air")
    for bz in range(z0, z1 + 1):
        for i, yb in enumerate(g[bz - z0]):
            key = (x0 + i, bz)
            if ground and key not in ground:
                continue        # 城镇: no ground laid here - the fallback y is a 地块's own y (-65: bedrock up), leave it
            if key not in cols:
                cv.cols[key].insert(0, (yb + 1, max(yb + 1, top), air if key in frames else CLEAR_NATURAL))


def build(req):
    cv = Canvas()
    x, y, z = int(req.get("x", 0)), int(req.get("y", 0)), int(req.get("z", 0))
    bases = None
    ground = {}
    nofoot = set()
    if req.get("type") == "compose":
        res = compose.build_compose(req, cv)
        bases = res["bases"]                       # ((x0, z0, x1, z1), ground y, base block or None) per room, after levelling
        ground = res.get("ground") or {}           # 城镇: the ground y of every column (terraces at many levels)
        nofoot = res.get("nofoot") or set()        # 石桥: decks over the air, no footing under them
        y = min(b[1] for b in bases)
    else:
        f = Frame(cv, x, z, req.get("facing", "south"))
        PROG.set("建筑")
        res = bgen.build(f, y, req["kind"], req.get("tier"), int(req.get("seed", 0))) or {}
    PROG.set("地基")
    if not cv.blocks:
        raise ValueError("nothing generated")

    cell = {}                                      # (the rooms over each 16x16 cell: a column inside a room finds it
    for i, ((x0, z0, x1, z1), _, _) in enumerate(bases or ()):   # there, not by going through them all - a big
        for gx in range(x0 >> 4, (x1 >> 4) + 1):   # town is thousands of rooms over a million columns)
            for gz in range(z0 >> 4, (z1 >> 4) + 1):
                cell.setdefault((gx, gz), []).append(i)

    def nearest(bx, bz):
        best = None
        for i in cell.get((bx >> 4, bz >> 4), ()):
            (x0, z0, x1, z1), by, blk = bases[i]
            if x0 <= bx <= x1 and z0 <= bz <= z1 and (best is None or -by < best[0][1]):
                best = ((0, -by), by, blk)
        if best is not None:
            return best
        for (x0, z0, x1, z1), by, blk in bases:
            d = max(x0 - bx, bx - x1, 0) + max(z0 - bz, bz - z1, 0)
            if best is None or (d, -by) < best[0]:
                best = ((d, -by), by, blk)
        return best

    def base_y(bx, bz):
        """Ground level under a column: the floor of the nearest room (several rooms may stand at different heights);
        in a town the ground the design laid there."""
        g = ground.get((bx, bz))
        if g is not None:
            return g
        return nearest(bx, bz)[1] if bases else y

    def stone(bx, yy, bz):
        """The base material under a column: the nearest room's choice, else the default stone."""
        blk = nearest(bx, bz)[2] if bases else None
        return blk or arch.masonry(bx, yy, bz)

    # ground work: clear the footprint above the floor (a hillside must not poke into the rooms) and put a masonry
    # plinth under the parts that stand on the ground - from the ground level itself (the clicked block's layer, so no
    # gap is left between plinth and building on uneven ground) down 3; the game carries it on down to solid ground
    # (CanvasStamp.stampForTool). Columns that only carry eaves get no plinth, so the stone base is not wider than the
    # building. Columns are written before blocks.
    cols = {(bx, bz) for (bx, by, bz) in cv.blocks}
    top = max(by for (_, by, _) in cv.blocks)
    # things standing a little above the ground (bamboo, a fence, a sign pole, a lamp post beside a raised platform)
    # get something under them down to the ground: soil under plants, masonry under the rest
    low = {}
    for (bx, by, bz), st in cv.blocks.items():
        if st != arch.AIR and by < low.get((bx, bz), (1 << 30, None))[0]:
            low[(bx, bz)] = (by, st)
    for (bx, bz), (by, st) in low.items():
        if (bx, bz) in nofoot:
            continue
        yb = base_y(bx, bz)
        if yb + 1 < by <= yb + 5 and _standing(st):     # (not the eaves: with no platform they hang only 3-5 up)
            plant = _plant(st)
            for yy in range(yb + 1, by):
                if (bx, yy, bz) not in cv.blocks:
                    cv.blocks[(bx, yy, bz)] = (B("grass_block") if yy == by - 1 else B("dirt")) if plant else stone(bx, yy, bz)
    grounded = set()
    for (bx, by, bz) in cv.blocks:
        if (bx, bz) in nofoot:
            continue
        yb = base_y(bx, bz)
        if yb <= by <= yb + 2:
            grounded.add((bx, bz))
    for (bx, bz) in cols:
        yb = base_y(bx, bz)
        cv.cols[(bx, bz)].insert(0, (yb + 1, max(yb + 1, top), B("air")))
        if (bx, bz) in grounded:
            if _plant(low.get((bx, bz), (0, ""))[1]):     # plants keep soil under them (bamboo does not grow on stone)
                cv.cols[(bx, bz)].insert(0, (yb, yb, B("grass_block")))
                cv.cols[(bx, bz)].insert(0, (yb - 3, yb - 1, B("dirt")))
            else:
                covered = any(cv.blocks.get((bx, yy, bz), arch.AIR) != arch.AIR for yy in (yb, yb + 1))
                cv.cols[(bx, bz)].insert(0, (yb - 3, yb if covered else yb - 1, stone(bx, 0, bz)))
    # the rest of the bounding box, in the same pass. 单体建造: everything above the ground inside the frames the player
    # drew is cleared too (air) - the strip a house leaves when it drops a column to an odd width, the corners round a
    # round tower. Every other column of the box (a yard, the way between two courts, the ground round a gate) gets a
    # run of CLEAR_NATURAL over its ground: the game (CanvasStamp.writeColumns) takes away only the natural terrain
    # there - dirt, grass, stone, sand, plants, trees - and leaves anything else (someone's house, water), so no bank,
    # bush or tuft of grass is left standing in a yard, on a path or in a doorway.
    frames = set()
    if req.get("type") == "compose":
        for rm in req.get("rooms", []):
            ax0, ax1 = sorted((int(rm["x0"]), int(rm["x1"])))
            az0, az1 = sorted((int(rm["z0"]), int(rm["z1"])))
            frames.update((bx, bz) for bx in range(ax0, ax1 + 1) for bz in range(az0, az1 + 1))
    clear_box(cv, cols, frames, top, ground, bases, y)
    PROG.set("写出")
    n, b = cv.export(OUT / f"{req['id']}.dvcr")
    x0, z0, x1, z1 = cv.bbox()
    out = dict(ok=True, blocks=b, runs=n, bbox=[x0, y - 3, z0, x1, top, z1], top=int(res.get("top", top)) if isinstance(res, dict) else top)
    if isinstance(res, dict) and res.get("sh"):
        out["sh"] = res["sh"]                      # 单体建造: the default storey height (rooms the player did not set)
    return out


def city_real_site(seed):
    """紫禁城 1:1 for the room table: the one real layout (siteplan.city_real, the rooms' looks by the seed). The game puts
    the anchor - on the axis, just south of the 午门's wings - on the crosshair, the 午门 facing the player and the rest
    away from him; with "level" every room stands on the crosshair's ground, one level as the real site is flat
    (compose.compound_ground paves the whole rectangle there, the daemon clears what is above it, the game fills dips)."""
    import siteplan
    d = siteplan.city_real(seed)
    d["anchor"] = [376, max(r["z1"] for r in d["rooms"]) + 1]
    d["level"] = True
    return d


def reload_materials():
    """Material files or the game's block list changed: rebuild the wall styles and roof themes in place, so new or
    edited styles are used (and listed in the room table) without restarting the daemon."""
    materials.forget_registry()
    bgen.WALLS.clear()
    bgen.WALLS.update(materials.walls())
    roofs = materials.roofs()
    arch.ROOFS.clear()
    arch.ROOFS.update(roofs)
    arch.register_roof_sets(roofs)
    compose.WALLS[:] = [(k, v["cn"], v.get("swatch", "")) for k, v in bgen.WALLS.items()]
    compose.THEMES[:] = [(k, v["cn"], v.get("swatch", "")) for k, v in arch.ROOFS.items()]
    write_kinds()
    print("materials reloaded:", len(bgen.WALLS), "walls,", len(arch.ROOFS), "roofs", flush=True)


def _watch_stdin():
    """The mod keeps our standard input open; when it closes (the game quit or crashed), stop."""
    def run():
        try:
            while sys.stdin.read(4096):
                pass
        except Exception:
            pass
        print("standard input closed: the game is gone, stopping", flush=True)
        os._exit(0)
    threading.Thread(target=run, name="stdin-watch", daemon=True).start()


_LOCK = []


def _lock():
    """One daemon per folder: hold <run>/daemon.lock open and locked while we run. False if another one holds it."""
    f = open(RUN / "daemon.lock", "a+")
    try:
        if os.name == "nt":
            import msvcrt
            f.seek(0)
            msvcrt.locking(f.fileno(), msvcrt.LK_NBLCK, 1)
        else:
            import fcntl
            fcntl.flock(f.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
    except OSError:
        f.close()
        return False
    _LOCK.append(f)
    return True


def main():
    if _WATCH:
        _watch_stdin()
    if not _lock():
        print("another generator already serves", RUN, "- exiting", flush=True)
        return
    write_kinds()
    PROG.start_thread()
    compose.PROGRESS = lambda done, total: PROG.set("建筑", done, total)
    print("waiting for requests in", REQ, flush=True)
    seen = materials.stamp()
    seen_parts = parts.stamp()
    while True:
        now = materials.stamp()
        if now != seen:
            seen = now
            try:
                reload_materials()
                parts.install(FU.PIECES)        # a part may have been skipped for a block the game did not list yet
            except Exception:
                traceback.print_exc()
        now = parts.stamp()
        if now != seen_parts:
            seen_parts = now
            try:
                parts.install(FU.PIECES)
                parts.reload_lights()
                parts.reload_bays()
                parts.reload_ornaments()
                write_kinds()                       # the style list of the bays may have changed
                print("parts reloaded", flush=True)
            except Exception:
                traceback.print_exc()
        queue = sorted(REQ.glob("*.json"), key=lambda q: (q.name.startswith("pv_"), q.stat().st_mtime))
        pvs = [q for q in queue if q.name.startswith("pv_")]
        for old in pvs[:-1]:                     # only the newest preview counts: the game shows that one
            old.unlink(missing_ok=True)
            PROG.finish(old.stem)
        queue = [q for q in queue if not q.name.startswith("pv_")] + pvs[-1:]
        for p in queue:
            try:
                req = json.loads(p.read_text(encoding="utf-8"))
            except Exception:
                time.sleep(0.2)          # still being written
                continue
            p.unlink(missing_ok=True)
            PROG.start(req["id"])
            if not str(req.get("id", "")).startswith("pv_"):   # keep what was asked for, to rebuild it when something looks wrong
                (OUT / f"{req['id']}.req.json").write_text(json.dumps(req, ensure_ascii=False), encoding="utf-8")
            t0 = time.time()
            if req.get("type") in ("sentence", "site"):   # 一句话出建筑 / 随机一院: a design back, the game lays it out
                try:
                    if req["type"] == "site":           # a new compound every time (a random seed), not one of a few files
                        import random as _r
                        import secrets
                        import siteplan
                        rs = _r.Random(secrets.randbits(62))   # (the OS's randomness: never a clock, never repeats)
                        if req.get("which") == "city":
                            d = siteplan.city(rs.randrange(1, 1 << 30))
                        elif req.get("which") == "city_real":   # 紫禁城 1:1
                            d = city_real_site(rs.randrange(1, 1 << 30))
                        elif req.get("which") == "town":   # 随机城镇: on the ground the game sampled round the crosshair
                            import town
                            d = town.town(rs.randrange(1, 1 << 62), heights=req.get("heights"),   # (the size and the box
                                          size=req.get("size") or None, box=req.get("box"))        # as chosen; none: by the seed)
                        elif req.get("which") == "sect":   # 随机宗门: courts up the mountain the game sampled round the crosshair
                            import sect_plan
                            d = sect_plan.sect(rs.randrange(1, 1 << 62), tier=str(req.get("tier") or "jindan"), heights=req.get("heights"))
                        else:
                            d = siteplan.randomize(siteplan.site_free(rs.randrange(1, 1 << 62)), rs)   # (layout and settings both random)
                        said = [d["name"]]
                    else:
                        import sentence
                        d, said = sentence.parse(str(req.get("text", "")))
                    out = dict(ok=True, design=d, said=said, site=req["type"] == "site")
                except Exception as e:
                    out = dict(ok=False, error=f"{type(e).__name__}: {e}")
                    traceback.print_exc()
                tmp = OUT / f"{req['id']}.json.tmp"
                tmp.write_text(json.dumps(out, ensure_ascii=False), encoding="utf-8")
                tmp.replace(OUT / f"{req['id']}.json")
                PROG.finish(req["id"])
                print(req["id"], req["type"], req.get("text") or req.get("seed"), out.get("ok"), flush=True)
                continue
            if str(req.get("id", "")).startswith("pv_"):   # a preview: stop building it the moment a newer one waits
                def stale(mine=req["id"]):
                    if any(q.name.startswith("pv_") and q.stem != mine for q in REQ.glob("pv_*.json")):
                        raise compose.Superseded()
                compose.CANCEL = stale
            try:
                out = build(req)
            except compose.Superseded:
                compose.CANCEL = lambda: None
                PROG.finish(req["id"])
                print(req["id"], "superseded", flush=True)
                continue
            except Exception as e:
                out = dict(ok=False, error=f"{type(e).__name__}: {e}")
                traceback.print_exc()
            compose.CANCEL = lambda: None
            out["seconds"] = round(time.time() - t0, 2)
            tmp = OUT / f"{req['id']}.json.tmp"
            tmp.write_text(json.dumps(out, ensure_ascii=False), encoding="utf-8")
            tmp.replace(OUT / f"{req['id']}.json")
            PROG.finish(req["id"])
            print(req["id"], req.get("kind", "compose"), req.get("tier"), req.get("seed"), out.get("ok"), out.get("blocks"), out["seconds"], "s", flush=True)
        time.sleep(0.1)


if __name__ == "__main__":
    main()
