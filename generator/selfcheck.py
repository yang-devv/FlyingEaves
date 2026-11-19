"""自检: checks on a generated building that need no reference - the general half of the pipeline's "compare" step.

check(blocks, ground, cols) -> {check name: [positions]} for one canvas in world coordinates (ground: the y of the
ground surface; cells the canvas leaves empty above it are air, under it the world's own ground):
  slit       - a glass pane joined to fewer than two sides: light and mobs through the gap beside it
  floating   - blocks joined to nothing that reaches the ground (by a face or an edge): a piece hanging in the air
  loose      - things the game drops when nothing holds them: lanterns, candles, torches, pots, carpets, buttons, ladders
  door       - a door with something solid right in front of or behind it at head height
  low        - walking out of a door or doorway (straight or a cell aside, up to 4 out, down the steps): something
               hanging as low as the door's head (an eave, a rafter end, a fascia board, a beam) - bgen.walkout
  drop       - a door with nothing to step onto in front of or behind it: two or more down (no steps)
  leak       - water beside open air above the ground: it runs out
  crowded    - (a plan, not a canvas: crowded_plans, python selfcheck.py crowded) a flight of steps or the WALK cells
               at its foot running into another building, a wall, a screen; two buildings' eaves over the same cells
  gap        - a hole in a wall round a yard (院墙, 城墙): a cell a player or the wind gets through from outside into a
               yard open to the sky, other than a door (a doorway with no door in it is a hole too) - gaps()
  hollow     - a step of a flight (a stair or a slab whose run comes down to rest on something) with air right under
               it above the ground - hollow()

python selfcheck.py            all the O-menu kinds (each tier, two seeds), the 单体建造 test rooms and the 随机一院 plans
python selfcheck.py <kind>     one kind, with sample positions
"""
import sys
from collections import defaultdict

from bgen_daemon import _standing
from compose import _in_wall
from bgen import walkout

AIR = "minecraft:air"
_SOFT = ("air", "light", "carpet", "pressure_plate", "_door", "button", "torch", "flower", "sapling", "grass[", "fern", "snow[",
         "rail", "lever", "sign", "banner", "vine", "tripwire")


def _name(s):
    return s.split("[", 1)[0].split(":")[-1]


def _props(s):
    if "[" not in s:
        return {}
    return dict(kv.split("=", 1) for kv in s[s.index("[") + 1:-1].split(","))


DIRS = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
# blocks joined by a face or an edge look joined (a roof's stepped corner); only a corner point does not
NB18 = [(dx, dy, dz) for dx in (-1, 0, 1) for dy in (-1, 0, 1) for dz in (-1, 0, 1) if 0 < abs(dx) + abs(dy) + abs(dz) <= 2]


def check(blocks, ground, cols=None, only=None):
    """only="floating": that check alone (compose clears a town's loose bits of roof with it)."""
    solid = {p: s for p, s in blocks.items() if s and s != AIR}
    for (x, z), runs in (cols or {}).items():          # column runs (a drum, a plinth) count as blocks
        for (y0, y1, st) in runs:
            if st != AIR:
                for y in range(y0, y1 + 1):
                    solid.setdefault((x, y, z), st)
    out = defaultdict(list)

    def at(x, y, z):
        return solid.get((x, y, z))

    for (x, y, z), s in (solid.items() if only is None else ()):
        n = _name(s)
        p = _props(s)
        if n.endswith("_pane"):                         # (iron bars are a cell's bars or a grille: thin on purpose)
            if sum(p.get(d) == "true" for d in DIRS) < 2 and _in_wall(blocks, x, y, z):
                out["slit"].append((x, y, z))           # (in a wall that steps diagonally; a pane hanging free is cloth)
        if n in ("lantern", "soul_lantern"):
            if p.get("hanging") == "true" and not at(x, y + 1, z) or p.get("hanging") != "true" and not at(x, y - 1, z) and y - 1 > ground:
                out["loose"].append((x, y, z))
        elif n.endswith("candle") or n in ("torch", "soul_torch", "flower_pot") or n.startswith("potted_") or n.endswith("_carpet") \
                or (n.endswith("_button") and p.get("face") == "floor"):
            if not at(x, y - 1, z) and y - 1 > ground:
                out["loose"].append((x, y, z))
        elif n.endswith("wall_torch") or n == "ladder":
            dx, dz = DIRS.get(p.get("facing", "north"), (0, 0))
            if not at(x - dx, y, z - dz):
                out["loose"].append((x, y, z))
        if n.endswith("_door") and p.get("half") == "lower":
            ax = (1, 0) if p.get("facing") in ("east", "west") else (0, 1)
            for sgn in (1, -1):                         # blocked at head height (a block at the foot is a step up)
                c = at(x + sgn * ax[0], y + 1, z + sgn * ax[1])
                if c and not any(t in c for t in _SOFT) and "stairs" not in c and "slab" not in c:
                    out["door"].append((x, y, z))
                    break
        if n.endswith("_door") and p.get("half") == "lower":     # a door with nothing to step onto: a drop of two or more
            ax = (1, 0) if p.get("facing") in ("east", "west") else (0, 1)
            for sgn in (1, -1):
                fx, fz = x + sgn * ax[0], z + sgn * ax[1]
                if at(fx, y, fz):
                    continue                            # (something there: a step, a floor, a wall)
                k = 1
                while k <= 6 and not at(fx, y - k, fz) and y - k > ground:
                    k += 1
                if k >= 3:
                    out["drop"].append((x, y, z))
                    break
        if n == "water":
            for dx, dz in DIRS.values():
                nb = blocks.get((x + dx, y, z + dz))
                if (nb == AIR or nb is None and y > ground) and (x + dx, y, z + dz) not in solid:
                    out["leak"].append((x, y, z))
                    break
    # low: walking out of a door or doorway, something hanging at the door's head height (bgen.walkout)
    out["low"] = [way for way, _ in walkout(blocks, cols or {}, ground)] if only is None else []
    # floating: what the ground does not reach through touching blocks
    seen = set()
    todo = [p for p in solid if p[1] <= ground + 1]
    low = {}                                            # what the daemon props up: a thing standing a little above the
    for (x, y, z), s in solid.items():                  # ground (a fence, a stall, a lamp post) gets stone under it
        if y < low.get((x, z), (1 << 30,))[0]:
            low[(x, z)] = (y, s)
    todo += [(x, y, z) for (x, z), (y, s) in low.items() if ground + 1 < y <= ground + 5 and _standing(s)]
    seen.update(todo)
    while todo:
        x, y, z = todo.pop()
        for d in NB18:
            q = (x + d[0], y + d[1], z + d[2])
            if q in solid and q not in seen:
                seen.add(q)
                todo.append(q)
    out["floating"] = [p for p, s in solid.items() if p not in seen and not _name(s).endswith("light")]   # (light blocks hang in air)
    out["gap"] = gaps(solid, ground) if only is None else []
    out["hollow"] = hollow(solid, ground) if only is None else []
    return dict(out)


def _step(s):
    n = _name(s)
    return n.endswith("_slab") and "type=top" not in s or n.endswith("_stairs") and "half=bottom" in s


def hollow(solid, ground):
    """Steps with nothing under them: a stair or a slab of a flight (followed down its run - a stair the way it
    descends, a slab any way - through steps level or a layer down, its foot stands on the ground or on solid blocks
    down to it, and off it a player walks out under the open sky; a roof's eave or a staircase indoors does not) with
    air (or something soft) right under it above the ground: [(x, y, z)]."""
    def soft(q):
        s = solid.get(q)
        return s is None or _open(s)

    def rests(x0, y0, z0, dirs):
        for dx, dz in dirs:
            x, y, z = x0, y0, z0
            for _ in range(64):                         # down the run: the next step level or a layer down
                for dy in (0, -1):
                    s = solid.get((x + dx, y + dy, z + dz))
                    if s and _step(s):
                        x, y, z = x + dx, y + dy, z + dz
                        break
                else:
                    break
            if y == y0 or any(soft((x, yy, z)) for yy in range(y - 1, ground, -1)):
                continue                                # (it comes down: not a row of slabs on a roof; its foot
                                                        # on the ground, or on what stands on it: a 台地)
            for k in range(1, 7):                       # stepped off onto a floor, out under the open sky (not
                c = (x + k * dx, y, z + k * dz)         # a staircase indoors, nor an eave on a wall)
                if not (soft(c) and soft((c[0], y + 1, c[2])) and (y - 1 <= ground or not soft((c[0], y - 1, c[2])))):
                    break
                if all(soft((c[0], yy, c[2])) for yy in range(y + 2, y + 64)):
                    return True
        return False
    out = []
    for (x, y, z), s in solid.items():
        if y - 1 <= ground or not _step(s) or not soft((x, y - 1, z)) or not soft((x, y + 1, z)):
            continue                                    # (something on it: not a step walked on)
        if _name(s).endswith("_stairs"):
            dx, dz = DIRS.get(_props(s).get("facing"), (0, 0))
            dirs = [(-dx, -dz)]
        else:
            dirs = list(DIRS.values())
        if rests(x, y, z, dirs):
            out.append((x, y, z))
    return out


# what does not close a hole: you walk through it, or the wind does (a lantern hanging in a gap leaves the gap)
_OPEN = ("carpet", "pressure_plate", "button", "torch", "flower", "sapling", "fern", "rail", "lever", "sign", "banner", "vine",
         "tripwire", "lantern", "candle", "chain", "potted_", "lily_pad", "cobweb")


def _open(s):
    n = _name(s)
    return n in ("air", "cave_air", "light", "grass", "tall_grass", "snow") or any(t in n for t in _OPEN)


def _label(cells, W, D):
    """Connected regions (4-neighbours) of the cells set True in a W x D grid (a flat list, index i * D + j): a list
    of the same size with 0 for cells not set and 1, 2, ... per region (in scan order), and the region count."""
    lab = [0] * (W * D)
    n = 0
    for k in range(W * D):
        if not cells[k] or lab[k]:
            continue
        n += 1
        lab[k] = n
        todo = [k]
        while todo:
            c = todo.pop()
            i, j = divmod(c, D)
            for q, ok in ((c - D, i > 0), (c + D, i < W - 1), (c - 1, j > 0), (c + 1, j < D - 1)):
                if ok and cells[q] and not lab[q]:
                    lab[q] = n
                    todo.append(q)
    return lab, n


def _close(grid, W, D):
    """Binary closing with the 3 x 3 square, once, the outside of the grid counting as empty (scipy.ndimage's
    binary_closing(grid, ones((3, 3)), iterations=1, border_value=0)): grow by one cell every way, then shrink back."""
    dil = [False] * (W * D)
    for k in range(W * D):
        if grid[k]:
            i, j = divmod(k, D)
            for a in range(max(0, i - 1), min(W, i + 2)):
                for b in range(max(0, j - 1), min(D, j + 2)):
                    dil[a * D + b] = True
    out = [False] * (W * D)
    for i in range(1, W - 1):
        for j in range(1, D - 1):
            k = i * D + j
            out[k] = all(dil[a * D + b] for a in (i - 1, i, i + 1) for b in (j - 1, j, j + 1))
    return out


def gaps(solid, ground, layers=3, reach=1, yard=50, hole=6):
    """Holes in the walls round a yard, layer by layer from the ground up to a door's height: in each layer, what
    blocks the way (a door too: shut) is closed by `reach` (gaps up to 2 wide shut); a region that closing walls in,
    at least `yard` cells and mostly under the open sky, is a yard. Any of it reached from outside through the real
    blocks is a leak; the gap is each stretch the closing filled that is open both to the outside and through to the
    yard, at most `hole` long: [(x, y, z)]. (Pure Python: what scipy.ndimage's closing / label / dilation /
    find_objects did, cell for cell; only reach=1 is used.)"""
    if reach != 1:
        raise ValueError("gaps: only reach=1")
    lay = [(p, s) for p, s in solid.items() if ground < p[1] <= ground + layers]
    if not lay:
        return []
    pad = reach + 2
    x0 = min(p[0] for p, _ in lay) - pad
    z0 = min(p[2] for p, _ in lay) - pad
    W = max(p[0] for p, _ in lay) - x0 + pad + 1
    D = max(p[2] for p, _ in lay) - z0 + pad + 1
    N = W * D
    roof = [False] * N                                  # something overhead (a roof, an eave, a gate's tower)
    for (x, y, z), s in solid.items():
        if y > ground + layers and 0 <= x - x0 < W and 0 <= z - z0 < D and _name(s) != "light":
            roof[(x - x0) * D + (z - z0)] = True
    border = [k for k in range(N) if k < D or k >= N - D or k % D in (0, D - 1)]
    out = []
    for y in range(ground + 1, ground + layers + 1):
        grid = [False] * N
        for (x, yy, z), s in lay:
            if yy == y and not _open(s):
                grid[(x - x0) * D + (z - z0)] = True
        closed = _close(grid, W, D)
        shut = [a or b for a, b in zip(closed, grid)]
        lab, _ = _label([not v for v in shut], W, D)
        edge = {lab[k] for k in border} - {0}
        outside = [v in edge for v in lab]
        lab, n = _label([not a and not b for a, b in zip(shut, outside)], W, D)
        size = [0] * (n + 1)
        covered = [0] * (n + 1)
        for k in range(N):
            if lab[k]:
                size[lab[k]] += 1
                covered[lab[k]] += roof[k]
        yardlab = {i for i in range(1, n + 1) if size[i] >= yard and covered[i] * 2 <= size[i]}
        yards = [v in yardlab for v in lab]
        if not any(yards):
            continue
        lab, _ = _label([not a and not b for a, b in zip(grid, outside)], W, D)   # from the yard through what is open
        yl = {lab[k] for k in range(N) if yards[k]}                               # (rooms, doorways, holes)
        inyard = [v in yl for v in lab]
        filled = [a and not b for a, b in zip(shut, grid)]   # what the closing shut: a hole, a doorway, a window bay
        lab, n = _label(filled, W, D)
        near = list(outside)                                 # the outside grown by one cell (4-neighbours)
        for k in range(N):
            if outside[k]:
                i, j = divmod(k, D)
                if i > 0: near[k - D] = True
                if i < W - 1: near[k + D] = True
                if j > 0: near[k - 1] = True
                if j < D - 1: near[k + 1] = True
        hit = {lab[k] for k in range(N) if filled[k] and inyard[k] and near[k]}   # open both to the outside and to the yard
        box = {}
        for k in range(N):
            if lab[k] in hit:
                i, j = divmod(k, D)
                b = box.get(lab[k])
                box[lab[k]] = (i, i, j, j) if b is None else (min(b[0], i), max(b[1], i), min(b[2], j), max(b[3], j))
        keep = {v for v, (a, b, c, d) in box.items() if max(b - a + 1, d - c + 1) <= hole}   # (and short: a hole through
        out += [(k // D + x0, y, k % D + z0) for k in range(N) if lab[k] in keep]           # a wall, not an alley behind a hall)
    tall = defaultdict(int)                             # a hole at least two high (a slot a player or the wind goes
    for x, y, z in out:                                 # through; one layer alone was the closing joining a step or
        tall[(x, z)] += 1                               # a slab to a wall)
    return [p for p in out if tall[(p[0], p[2])] >= 2]


def _cases():
    import bgen_daemon  # noqa: F401  (registers the special-site kinds before the loop over them)
    import bgen
    import compose
    from arch import Canvas, Frame
    for name, spec in bgen.K.items():
        if name in ("floating_pavilion", "cliff_hall"):  # (floats on purpose; hangs on a cliff, its posts down to a ledge there)
            continue
        for tier in sorted(set((spec["tiers"][0], spec["tiers"][-1]))):
            for seed in (1, 2):
                cv = Canvas()
                try:
                    bgen.build(Frame(cv, 0, 0, "south"), 64, name, tier, seed)
                except Exception as e:                  # (a kind that fails to build is a finding too)
                    yield (name, tier, seed), None, repr(e)
                    continue
                yield (name, tier, seed), cv, None
    base = dict(x0=0, z0=0, x1=20, z1=14, y=64, storeys=1, roof="xieshan", walls="palace", theme="gold", uses=["main_room"] * 8,
                front="south", seed=5, sh=7, podium=0)
    variants = {                                         # 单体建造: one design per feature (and a few together)
        "taper3": [dict(base, x1=26, z1=26, storeys=4, taper=3, roof="pyramid")],
        "taper_eaves": [dict(base, x1=26, z1=26, storeys=5, taper=2, lift=4, overhang=3, beasts=3, brackets=3)],
        "double_xumi": [dict(base, double_eave=True, podium=4, podium_style="xumizuo", base="white", beasts=5, caihua="hexi", brackets=4)],
        "colonnade": [dict(base, colonnade=True, podium=2, podium_style="tiers")],
        "open_stage": [dict(base, open="front", veranda=True, back_veranda=True)],
        "chengtai": [dict(base, x1=30, podium=10, podium_style="chengtai", tunnels=4)],
        "stack": [dict(base, x1=24, z1=16, podium=3, podium_style="xumizuo"), dict(base, x0=6, z0=4, x1=18, z1=12, stack=True, seed=6)],
        "round_roofs": [dict(base, x1=14, z1=14, shape="round", round_roof=f, storeys=2, seed=i) for i, f in enumerate(("cone",))],
        "round_flat": [dict(base, x1=14, z1=14, shape="round", round_roof="flat", storeys=2)],
        "round_taper": [dict(base, x1=24, z1=24, shape="round", storeys=3, taper=2, double_eave=True, podium=3)],   # 收分 圆塔
        "round_pingzuo": [dict(base, x1=20, z1=20, shape="round", storeys=4, taper=1, pingzuo=True, podium=2),   # 平座
                          dict(base, x0=24, x1=36, z1=12, shape="round", storeys=3, sh=5, pingzuo=True, seed=6)],
        "round_helmet_double": [dict(base, x1=14, z1=14, shape="round", round_roof="helmet", double_eave=True, podium=3, podium_style="tiers")],
        "specials": [dict(base, x0=i * 16, x1=i * 16 + 12, z1=4, shape=s, seed=i) for i, s in
                     enumerate(("paifang", "screen", "gate", "glazedgate", "chuihua", "wall", "paving", "river"))],
        "bridge": [dict(base, x1=8, z0=-2, z1=2, shape="street", front="east", special="street"),   # 石桥 between two paths
                   dict(base, x0=9, x1=40, z0=-2, z1=2, shape="bridge", front="east", special="bridge", seed=6),
                   dict(base, x0=41, x1=49, z0=-2, z1=2, shape="street", front="east", special="street", seed=7)],
        "bridge_covered": [dict(base, x1=8, z0=-1, z1=1, shape="street", front="east", special="street"),   # 廊桥
                           dict(base, x0=9, x1=49, z0=-2, z1=2, shape="bridge", front="east", special="bridge", seed=6),
                           dict(base, x0=11, x1=47, z0=-2, z1=2, roof="xuanshan", walls="darkwood", theme="grey", open="through",
                                front="east", sh=5, uses=["lookout"] * 8, seed=8),
                           dict(base, x0=50, x1=58, z0=-1, z1=1, shape="street", front="east", special="street", seed=7)],
        "seam": [dict(base, x1=14, z1=10, podium=8, podium_style="xumizuo"), dict(base, x0=17, z0=2, x1=25, z1=8, seed=6)],
    }
    for name, rooms in variants.items():
        cv = Canvas()
        try:
            compose.build_compose(dict(tier="mortal", seed=3, rooms=rooms), cv)
        except Exception as e:
            yield ("compose", name), None, repr(e)
            continue
        yield ("compose", name), cv, None
    import siteplan
    for kind in ("house", "palace"):
        for jin in (1, 2, 3):
            d = siteplan.site(kind, jin, 1)
            for r in d["rooms"]:
                r["y"] = 64
            cv = Canvas()
            compose.build_compose(dict(tier="mortal", seed=3, rooms=d["rooms"]), cv)
            yield ("site", kind, jin), cv, None


def crowded_plans():
    """crowded - plans (no building): a flight of steps, or the WALK cells at its foot, running into another building,
    a wall or a screen, or two buildings' eaves over the same cells (siteplan.crowded) - the 随机一院 plans, the 宫城 (seeds 1-6)."""
    import siteplan
    out = [(("site", kind, jin, seed), siteplan.crowded(siteplan.site(kind, jin, seed)["rooms"]))
           for kind in ("house", "palace") for jin in (1, 2, 3) for seed in (1, 2)]
    return out + [(("city", seed), siteplan.crowded(siteplan.city(seed)["rooms"])) for seed in range(1, 7)]


def main(only=None):
    import os
    os.chdir(os.path.dirname(os.path.abspath(__file__)))
    if only == "crowded":                              # (plans only: nothing built)
        found = [(key, c) for key, c in crowded_plans() if c]
        print(len(found), "crowded plans")
        for key, c in found:
            print(f"      {len(c):5d}  {key}  {c[:2]}")
        return
    total = defaultdict(int)
    worst = defaultdict(list)
    n = 0
    for key, cv, err in _cases():
        if only and key[0] != only:
            continue
        n += 1
        if err:
            total["error"] += 1
            worst["error"].append((1, key, err[:80]))
            continue
        res = check(cv.blocks, 64, cv.cols)
        for k, v in res.items():
            if v:
                total[k] += 1
                worst[k].append((len(v), key, sorted(v)[:3]))
    print(n, "buildings")
    for k in ("error", "slit", "floating", "loose", "door", "drop", "low", "leak", "gap", "hollow"):
        print(f"  {k:9s} in {total.get(k, 0):3d}")
        for c, key, sample in sorted(worst.get(k, []), key=lambda t: -t[0])[:6 if not only else 50]:
            print(f"      {c:5d}  {key}  {sample}")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else None)
