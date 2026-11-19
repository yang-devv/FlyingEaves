"""Parametric Song-style building library for the sect (exterior only), generalised from hall.py.

Every building is drawn in a local frame: u = across the front (right-hand when you face the building from outside is
-u... irrelevant, it is symmetric), v = depth with +v the FRONT, y up. A Frame maps local -> world for one of the four
facings and rotates block states. Output goes into a Canvas (dict of blocks + column runs) that exports DVCR files
for the in-game chunk writer (CanvasStamp), so millions of blocks go in at once.

Rules kept from the handbook (research/xianxia-hall.md): 柱高不越间之广; 殿阁 roof rise 1/3 of purlin depth with 折屋;
corner lift by stacked squares + 冲出; brackets on the 普拍枋, at most two intermediate per bay; 朱白 colouring,
grey tiles, white-jade 须弥座.
"""
from __future__ import annotations

import gzip
import math
import os
import struct
from array import array
from collections import defaultdict
from pathlib import Path

# ============================================================================================ blocks and frames

_INTERN = {}
DIRV = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}
OPP = {"north": "south", "south": "north", "west": "east", "east": "west"}


def B(id_, **props):
    """A block state as a canonical string 'minecraft:x[k=v,...]' (interned)."""
    if ":" not in id_:
        id_ = "minecraft:" + id_
    if props:
        s = id_ + "[" + ",".join(f"{k}={str(v).lower() if isinstance(v, bool) else v}" for k, v in sorted(props.items())) + "]"
    else:
        s = id_
    return _INTERN.setdefault(s, s)


def parse(s):
    if "[" not in s:
        return s, {}
    i = s.index("[")
    props = dict(kv.split("=") for kv in s[i + 1:-1].split(","))
    return s[:i], props


# named material sets whose stairs and slab are not "<name>_stairs" / "<name>_slab" (roof themes from data, blocks
# from other mods): stair("roof:grey", ...) looks the ids up here
SETS = {}


def stair(mat, facing, half="bottom", shape=None):
    p = dict(facing=facing, half=half)
    if shape:
        p["shape"] = shape
    return B(SETS[mat]["stairs"] if mat in SETS else mat + "_stairs", **p)


def slab(mat, typ="bottom"):
    return B(SETS[mat]["slab"] if mat in SETS else mat + "_slab", type=typ)


AIR = B("air")

# world x = a*u + b*v, z = c*u + d*v  (all proper rotations)
ROT = {"south": (1, 0, 0, 1), "north": (-1, 0, 0, -1), "east": (0, 1, -1, 0), "west": (0, -1, 1, 0)}


def _world_dir(front, d):
    a, b, c, dd = ROT[front]
    du, dv = DIRV[d]
    x, z = a * du + b * dv, c * du + dd * dv
    return {(0, -1): "north", (0, 1): "south", (-1, 0): "west", (1, 0): "east"}[(x, z)]


_ROT_CACHE = {}


def rotate_state(s, front):
    if front == "south":
        return s
    key = (s, front)
    r = _ROT_CACHE.get(key)
    if r is not None:
        return r
    name, props = parse(s)
    if not props:
        _ROT_CACHE[key] = s
        return s
    q = {}
    sides = {}
    for k, v in props.items():
        if k == "facing" and v in DIRV:
            q[k] = _world_dir(front, v)
        elif k == "axis" and v in ("x", "z") and front in ("east", "west"):
            q[k] = "z" if v == "x" else "x"
        elif k in DIRV:
            sides[_world_dir(front, k)] = v
        else:
            q[k] = v
    q.update(sides)
    r = name + "[" + ",".join(f"{k}={v}" for k, v in sorted(q.items())) + "]"
    r = _INTERN.setdefault(r, r)
    _ROT_CACHE[key] = r
    return r


_HANG = ("minecraft:chain", "minecraft:lantern[hanging=true", "minecraft:soul_lantern[hanging=true")
_CHAIN = "minecraft:chain[axis=y]"
_FLOOR_PREFIX = ("minecraft:lantern[hanging=false", "minecraft:soul_lantern[hanging=false", "minecraft:candle", "minecraft:flower_pot",
                 "minecraft:potted_", "minecraft:torch", "minecraft:rail", "minecraft:lily_pad", "minecraft:redstone_torch")
_FLOOR_SUFFIX = ("_carpet", "_pressure_plate")


def _needs_floor(b):
    if b.startswith(_FLOOR_PREFIX):
        return True
    name = b.split("[", 1)[0]
    if name.endswith(_FLOOR_SUFFIX):
        return True
    if name.endswith("_banner") and not name.endswith("_wall_banner"):
        return True
    if name.endswith("_button") and "face=floor" in b:
        return True
    return False


class Canvas:
    """World-space block store: single blocks (last write wins) plus column runs (written first, under the blocks)."""

    def __init__(self):
        self.blocks = {}
        self.cols = defaultdict(list)

    def set(self, x, y, z, s):
        self.blocks[(x, y, z)] = s

    def get(self, x, y, z):
        return self.blocks.get((x, y, z))

    def run(self, x, z, y0, y1, s):
        if y1 >= y0:
            self.cols[(x, z)].append((y0, y1, s))

    def bbox(self):
        xs = [k[0] for k in self.blocks] + [k[0] for k in self.cols]
        zs = [k[2] for k in self.blocks] + [k[1] for k in self.cols]
        return min(xs), min(zs), max(xs), max(zs)

    def fix_supports(self):
        """Hanging lamps get a chain up to the first solid block above (or are removed if there is none within 60);
        small things that need a floor (standing lanterns, candles, pots, carpets, banners, plates...) are removed
        when the cell under them is explicitly air. Returns (chained, removed)."""
        air = AIR
        blocks = self.blocks

        def solid_at(x, y, z):
            b = blocks.get((x, y, z))
            if b is not None:
                return b != air and not b.startswith(_HANG) and not _needs_floor(b)
            for y0, y1, st in self.cols.get((x, z), ()):
                if y0 <= y <= y1 and st != air:
                    return True
            return False

        chained = removed = 0
        for (x, y, z), b in list(blocks.items()):
            if b.startswith("minecraft:lantern[hanging=true") or b.startswith("minecraft:soul_lantern[hanging=true") or                     (b.startswith("minecraft:bell") and "attachment=ceiling" in b):
                gap = []
                for yy in range(y + 1, y + 61):
                    c = blocks.get((x, yy, z))
                    if c is not None and c.startswith("minecraft:chain"):
                        continue
                    if solid_at(x, yy, z):
                        for g in gap:
                            blocks[(x, g, z)] = _CHAIN
                        chained += bool(gap)
                        break
                    gap.append(yy)
                else:
                    blocks[(x, y, z)] = air
                    yy = y + 1
                    while blocks.get((x, yy, z), "").startswith("minecraft:chain"):
                        blocks[(x, yy, z)] = air
                        yy += 1
                    removed += 1
            elif _needs_floor(b) and blocks.get((x, y - 1, z)) == air:
                blocks[(x, y, z)] = air
                removed += 1
        return chained, removed

    def export(self, path, name=None):
        """Write a DVCR file (see CanvasStamp.read) covering the bounding box; returns (runs, blocks)."""
        self.fix_supports()
        x0, z0, x1, z1 = self.bbox()
        w, d = x1 - x0 + 1, z1 - z0 + 1
        per_col = defaultdict(list)
        for (x, y, z), s in self.blocks.items():
            per_col[(x, z)].append((y, s))
        palette = {}
        counts = bytearray(w * d)
        rows = defaultdict(list)
        for key in set(per_col) | set(self.cols):
            runs = list(self.cols.get(key, ()))
            ys = sorted(per_col.get(key, ()))
            i = 0
            while i < len(ys):
                y, s = ys[i]
                j = i
                while j + 1 < len(ys) and ys[j + 1][0] == ys[j][0] + 1 and ys[j + 1][1] == s:
                    j += 1
                runs.append((y, ys[j][0], s))
                i = j + 1
            col = (key[1] - z0) * w + (key[0] - x0)
            rows[col] = runs
        n = 0
        y0s, y1s, pals = [], [], []
        for col in sorted(rows):
            runs = rows[col]
            if len(runs) > 255:
                # merge: keep the last 255 (later writes matter most); very rare (tall towers with many materials)
                runs = runs[-255:]
            counts[col] = len(runs)
            for a, b_, s in runs:
                y0s.append(a)
                y1s.append(b_)
                pals.append(palette.setdefault(s, len(palette)))
            n += len(runs)
        pal_list = sorted(palette, key=palette.get)
        path = Path(path)
        tmp = path.with_suffix(".tmp")
        with gzip.open(tmp, "wb", compresslevel=3) as f:
            f.write(b"DVCR")
            f.write(struct.pack(">iiiiii", 1, x0, z0, w, d, len(pal_list)))
            for s in pal_list:
                bs = s.encode()
                f.write(struct.pack(">H", len(bs)) + bs)
            f.write(struct.pack(">i", n))
            f.write(bytes(counts))
            for vals, code in ((y0s, "h"), (y1s, "h"), (pals, "H")):   # big-endian shorts
                a = array(code, vals)
                if a.itemsize != 2:
                    raise ValueError("short arrays are not 2 bytes here")
                if struct.pack("=h", 1) != struct.pack(">h", 1):
                    a.byteswap()
                f.write(a.tobytes())
        os.replace(tmp, path)
        return n, len(self.blocks)


class Frame:
    """Local building frame: origin (world ox, oz), facing = world direction of local +v (the front)."""

    def __init__(self, cv: Canvas, ox, oz, front="south"):
        self.cv, self.ox, self.oz, self.front = cv, ox, oz, front
        self.a, self.b, self.c, self.d = ROT[front]

    def w(self, u, v):
        return self.ox + self.a * u + self.b * v, self.oz + self.c * u + self.d * v

    def put(self, u, y, v, s):
        x, z = self.w(u, v)
        self.cv.set(x, y, z, rotate_state(s, self.front))

    def get(self, u, y, v):
        x, z = self.w(u, v)
        return self.cv.get(x, y, z)

    def fill(self, u0, y0, v0, u1, y1, v1, s):
        for u in range(min(u0, u1), max(u0, u1) + 1):
            for v in range(min(v0, v1), max(v0, v1) + 1):
                for y in range(min(y0, y1), max(y0, y1) + 1):
                    self.put(u, y, v, s)

    def col(self, u, v, y0, y1, s):
        x, z = self.w(u, v)
        self.cv.run(x, z, y0, y1, rotate_state(s, self.front))

    def sub(self, du, dv, front_local="south"):
        """A child frame at local (du, dv), its front turned to local direction front_local."""
        x, z = self.w(du, dv)
        return Frame(self.cv, x, z, _world_dir(self.front, front_local))


# ============================================================================================ palette

class Pal:
    TILE, TILE_FULL, RIDGE = "deepslate_tile", "deepslate_tiles", "polished_deepslate"
    COL = B("stripped_mangrove_log", axis="y")
    RED = B("mangrove_planks")
    WHITE = B("calcite")
    WOOD = "spruce"
    JADE = B("smooth_quartz")
    JADE_BRICK = B("quartz_bricks")
    FLOOR = B("polished_andesite")
    STONE = B("stone_bricks")
    GOLD = B("gold_block")


import materials

# roof themes come from data/materials/roofs.json (and the player's run/bgen/materials/); see materials.py
ROOFS = materials.roofs()
def register_roof_sets(roofs):
    """Stairs/slab ids of each roof theme, and of its tile-row stripe (瓦垄) when it has one."""
    for r in roofs.values():
        SETS[r["tile"]] = dict(stairs=r["stairs"], slab=r["slab"])
        if r.get("stripe_stairs") and r.get("stripe_slab"):
            SETS[r["tile"] + ":stripe"] = dict(stairs=r["stripe_stairs"], slab=r["stripe_slab"])


register_roof_sets(ROOFS)
ROOF = dict(ROOFS["grey"])


def roof_theme(name):
    """Switch the roof tiles for the buildings drawn next (grey 灰瓦, teal 绿琉璃, gold 黄琉璃)."""
    ROOF.clear()
    ROOF.update(ROOFS[name])


MASONRY = [B("stone_bricks")] * 6 + [B("mossy_stone_bricks")] * 2 + [B("cracked_stone_bricks"), B("andesite")]


def masonry(x, y, z):
    h = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791)
    return MASONRY[h % len(MASONRY)]


# ============================================================================================ shared parts

def positions(bays):
    """Column positions (centre-to-centre) for a symmetric list of bay widths (odd count)."""
    total = sum(bays)
    xs = [-total // 2]
    for bw in bays:
        xs.append(xs[-1] + bw)
    off = (xs[0] + xs[-1]) // 2
    return [x - off for x in xs]


def ring_cells(hx, hz):
    cells = []
    for x in range(-hx, hx + 1):
        cells += [(x, -hz, "north"), (x, hz, "south")]
    for z in range(-hz + 1, hz):
        cells += [(-hx, z, "west"), (hx, z, "east")]
    return cells


def xumizuo(f, hx, hz, top, height, ground=None, fill_from=None, mat=None):
    """One tier of the 须弥座: core (stone below a jade band) and the moulded rim (下枭 / 束腰 / 上枭).
    mat: {core, stairs} of another material (the room's base); white jade when None."""
    core, band = (mat["core"], mat["core"]) if mat else (Pal.JADE, Pal.JADE_BRICK)
    st = (lambda face, half: B(mat["stairs"], facing=face, half=half)) if mat else (lambda face, half: stair("quartz", face, half))
    for u in range(-hx, hx + 1):
        for v in range(-hz, hz + 1):
            base = top - height
            if ground is not None:
                g = ground(*f.w(u, v))
                if g < base:
                    f.col(u, v, g + 1, base - 1, Pal.STONE)
            f.col(u, v, base, top, core)
    if height >= 3:
        for u, v, face in ring_cells(hx, hz):
            inward = OPP[face]
            f.put(u, top - 2, v, st(inward, "bottom"))
            f.put(u, top - 1, v, AIR)
            f.put(u, top, v, st(inward, "top"))
        for u, v, face in ring_cells(hx - 1, hz - 1):
            f.put(u, top - 1, v, band)


def balustrade(f, hx, hz, y, gaps=(), back_gaps=(), step=4, sides=True, side_gaps=()):
    """Jade railing round a rectangle: posts every `step`, panels between; gaps = (u0, u1) spans left open in front/back,
    side_gaps = (v0, v1) spans left open on both sides (a flight down each side)."""
    ring = [(u, hz, True) for u in range(-hx, hx + 1)] + [(u, -hz, True) for u in range(-hx, hx + 1)]
    if sides:
        ring += [(-hx, v, False) for v in range(-hz + 1, hz)] + [(hx, v, False) for v in range(-hz + 1, hz)]
    for u, v, along_u in ring:
        g = gaps if v == hz else back_gaps if v == -hz else ()
        if along_u and any(a <= u <= c for a, c in g) or not along_u and any(a <= v <= c for a, c in side_gaps):
            continue
        post = (u % step == 0) if along_u else (v % step == 0)
        if abs(u) == hx and abs(v) == hz:
            post = True
        if along_u and any(u in (a - 1, c + 1) for a, c in g) or not along_u and any(v in (a - 1, c + 1) for a, c in side_gaps):
            post = True
        if post:
            f.put(u, y + 1, v, B("quartz_pillar", axis="y"))
            f.put(u, y + 2, v, slab("quartz"))
        else:
            f.put(u, y + 1, v, B("diorite_wall", east="low", west="low", up=False) if along_u
                  else B("diorite_wall", north="low", south="low", up=False))


def lantern_post(f, u, y, v):
    """A lamp post on the ground at (u, y) - the building's 'post' lamp (data/parts/lights.json)."""
    put_lamp(f, "post", u, y, v)


# 台阶的坡度 (a room's step_slope): how many cells a flight takes for each block it climbs, and what is on those cells,
# lowest first - every cell of one block's climb in the same layer: a stair (45°), a bottom slab (half a block up), a
# double slab (a whole block). The rises come at even spacing: 27° half a block every cell, 18° half every cell and a
# half (the stair's middle), 14° half every two cells. Every rise is half a block or a stair's half step: walked, not
# jumped.
STEP_SLOPES = {"45": 1, "27": 2, "18": 3, "14": 4}
STEP_PATTERN = {1: ("stair",), 2: ("slab", "full"), 3: ("slab", "stair", "full"), 4: ("slab", "slab", "full", "full")}


def step_run(slope):
    """Cells a flight takes per block it climbs for a step_slope ("45" - the default - "27", "18", "14")."""
    return STEP_SLOPES.get(str(slope or "45"), 1)


def flight(rise, run=1):
    """The cells of a flight climbing `rise` blocks, from the top (next to the platform) out: (k cells out, how many
    blocks under the top step's layer, "stair" / "slab" / "full")."""
    pat = STEP_PATTERN.get(run, STEP_PATTERN[1])
    run = len(pat)
    return [(k, k // run, pat[run - 1 - k % run]) for k in range(rise * run)]


def slab_for(stairs, fallback="minecraft:stone_brick_slab"):
    """The slab of a stairs block's material (for a gentle flight's half steps)."""
    s = stairs.split("[")[0].replace("_stairs", "_slab")
    return s if materials.present(s) else fallback


def step_block(kind, stairs, slab_, facing):
    """A flight's cell: a stair facing up the flight, a bottom slab, or a double slab (a whole block of the slab)."""
    if kind == "stair":
        return B(stairs, facing=facing, half="bottom")
    return B(slab_, type="bottom" if kind == "slab" else "double")


def stairs_down(f, u_half, v_start, y_top, y_bottom, yulu=True, lanterns_every=0, mat=None, run=1):
    """A front flight from (v_start, y_top) going out (+v) and down to y_bottom; 御路 in the middle; rails at the sides;
    landings every 12 steps, filled under down to y_bottom. Returns v after the last step. mat: {core, stairs, slab} of another material. run: cells
    per block it comes down (step_run: 1 is 45°, a stair a cell)."""
    edge = mat["core"] if mat else Pal.JADE
    road = mat["core"] if mat else B("chiseled_quartz_block")
    cap = B(mat["slab"], type="bottom") if mat else slab("quartz")

    def step(kind):
        if mat:
            return step_block(kind, mat["stairs"], mat["slab"], "north")
        return stair("quartz", "north") if kind == "stair" else slab("quartz", "bottom" if kind == "slab" else "double")
    pat = STEP_PATTERN.get(run, STEP_PATTERN[1])

    def under(u, y, v):                              # filled down to the foot: no hollow under the upper steps
        for yy in range(y - 1, y_bottom, -1):        # (what is there already - a lower tier - stays)
            if f.get(u, yy, v) in (None, AIR):
                f.put(u, yy, v, edge)
    v, y, steps = v_start, y_top, 0
    while y > y_bottom:
        if steps and steps % 12 == 0:
            for dv in range(4):
                for u in range(-u_half, u_half + 1):
                    f.put(u, y, v + dv, edge if abs(u) == u_half else Pal.FLOOR)
                    under(u, y, v + dv)
                    for yy in range(y + 1, y + 5):
                        f.put(u, yy, v + dv, AIR)
            if lanterns_every:
                lantern_post(f, -u_half - 1, y, v)
                lantern_post(f, u_half + 1, y, v)
            v += 4
        for kind in reversed(pat):                   # (the top of this block's climb first: next to the one above)
            for u in range(-u_half, u_half + 1):
                if yulu and abs(u) <= max(1, u_half // 5):
                    f.put(u, y, v, road if v % 2 == 0 else edge)
                elif abs(u) == u_half:
                    f.put(u, y, v, edge)
                    f.put(u, y + 1, v, cap)
                else:
                    f.put(u, y, v, step(kind))
                under(u, y, v)
                for yy in range(y + (2 if abs(u) == u_half else 1), y + 5):
                    f.put(u, yy, v, AIR)
            v += 1
        y -= 1
        steps += 1
    return v


def grand_stair(f, half, v_top, y_top, v_bottom, y_bottom, ground=None, rails=True):
    """天梯: a flight from (v_top, y_top) down to (v_bottom, y_bottom) along +v, steps in flights of <= 12 with the
    remaining run spread over the landings; masonry under the steps down to the terrain, air above."""
    rise = y_top - y_bottom
    run = v_bottom - v_top
    nflights = max(1, math.ceil(rise / 12))
    landing_total = max(0, run - rise)
    landing = landing_total // max(1, nflights - 1) if nflights > 1 else 0
    v, y = v_top, y_top
    for k in range(nflights):
        steps = min(12, y - y_bottom)
        for _ in range(steps):
            y -= 1
            for u in range(-half, half + 1):
                x, z = f.w(u, v)
                if ground is not None:
                    g = ground(x, z)
                    if g < y:
                        f.col(u, v, g + 1, y - 1, masonry(x, 0, z))
                if abs(u) <= max(1, half // 5):
                    f.put(u, y, v, B("chiseled_quartz_block") if v % 2 == 0 else Pal.JADE)
                elif abs(u) == half and rails:
                    f.put(u, y, v, Pal.JADE)
                    f.put(u, y + 1, v, slab("quartz"))
                else:
                    f.put(u, y, v, stair("quartz", "north"))
                for yy in range(y + (2 if abs(u) == half else 1), y + 6):
                    f.put(u, yy, v, AIR)
            v += 1
        if k < nflights - 1 and landing > 0:
            for dv in range(landing):
                for u in range(-half - 1, half + 2):
                    x, z = f.w(u, v + dv)
                    if ground is not None:
                        g = ground(x, z)
                        if g < y:
                            f.col(u, v + dv, g + 1, y - 1, masonry(x, 0, z))
                    f.put(u, y, v + dv, Pal.JADE if abs(u) >= half else Pal.FLOOR)
                    for yy in range(y + 1, y + 6):
                        f.put(u, yy, v + dv, AIR)
                    if abs(u) == half + 1 and rails and dv % 4 == 0:
                        f.put(u, y + 1, v + dv, B("quartz_pillar", axis="y"))
                        f.put(u, y + 2, v + dv, slab("quartz"))
            lantern_post(f, -half - 2, y, v)
            lantern_post(f, half + 2, y, v)
            v += landing
    return v, y


def zhewu(run, rise, steps=4):
    n = steps
    hs = [0.0] * (n + 1)
    hs[n] = rise
    drop = rise / 10.0
    for k in range(n - 1, 0, -1):
        hs[k] = hs[k + 1] * k / (k + 1) - drop
        drop /= 2
    return [run * i / n for i in range(n + 1)], hs


def profile_fn(ds, hs):
    def fn(d):
        if d <= 0:
            return hs[0] + d * (hs[1] - hs[0]) / (ds[1] - ds[0])
        for i in range(len(ds) - 1):
            if d <= ds[i + 1]:
                t = (d - ds[i]) / (ds[i + 1] - ds[i])
                return hs[i] + t * (hs[i + 1] - hs[i])
        return hs[-1]
    return fn


def roof_surface(ex, ez, purlin_y, overhang, run, rise, gable_x=None, layers=(8, 5, 3, 2, 1), push=(3, 1), inner=None):
    """Height field of a roof with eave rectangle |u| = ex, |v| = ez (see hall.py). gable_x: 歇山 / 悬山 when >= ex."""
    ds, hs = zhewu(run, rise)
    prof = profile_fn(ds, hs)
    h = {}
    for u in range(-ex - 3, ex + 4):
        for v in range(-ez - 3, ez + 4):
            e_z, e_x = ez - abs(v), ex - abs(u)
            if inner is not None and abs(u) <= inner[0] and abs(v) <= inner[1]:
                continue
            kz, kx = max(e_x, 0), max(e_z, 0)
            if e_z < 0 and not ((e_z == -1 and push[0] and kz <= push[0]) or (e_z == -2 and push[1] and kz <= push[1])):
                continue
            if e_x < 0 and not ((e_x == -1 and push[0] and kx <= push[0]) or (e_x == -2 and push[1] and kx <= push[1])):
                continue
            if gable_x is not None and abs(u) <= gable_x:
                if gable_x >= ex and e_x < 0:
                    continue
                e = e_z
            else:
                e = min(e_z, e_x)
            dd = max(e, 0) - overhang
            y = purlin_y + 0.35 * dd if dd < 0 else purlin_y + prof(dd)
            if not (gable_x is not None and gable_x >= ex):
                k = max(e_z, e_x, 0)
                y += 0.5 * sum(1 for L in layers if k <= L)
            h[(u, v)] = y
    return h


def _row_mat(mat, stripe, u, v, up_dir):
    """The tile for a slab cell: the stripe on odd rows running down the slope (rows follow the uphill direction)."""
    if not stripe or up_dir is None:
        return mat
    row = u if up_dir in ("north", "south") else v
    return stripe if row % 2 else mat


def raster_roof(f, h, base_fn, mat=None, full=None, rafters=True, min_y=None):
    """Height field -> stairs/slabs/full blocks (local frame), solid under non-edge cells, rafter ends under the lip.
    min_y: nothing of it lower than this, the rafter ends included (an eave over doors: kept clear above the door
    heads, floor + 4) - where it would droop lower it runs on flat instead."""
    if min_y is not None:
        h = {k: max(v, min_y + (1 if rafters else 0)) for k, v in h.items()}
    mat = mat or ROOF["tile"]
    full_b = B(full or ROOF["full"])
    stripe = mat + ":stripe" if mat + ":stripe" in SETS else None
    for (u, v), val in h.items():
        y = math.floor(val)
        fr = val - y
        nb = {d: h.get((u + du, v + dv)) for d, (du, dv) in DIRV.items()}
        up = max(((nv, d) for d, nv in nb.items() if nv is not None), default=(None, None))
        if fr >= 0.5 and up[0] is not None and up[0] >= val + 0.25:
            # 瓦垄: every other row of tiles running down the slope in a slightly different tile
            row = u if up[1] in ("north", "south") else v
            f.put(u, y, v, stair(stripe if stripe and row % 2 else mat, up[1]))
        elif fr >= 0.5:
            f.put(u, y, v, slab(_row_mat(mat, stripe, u, v, up[1]), "top") if fr < 0.85 else full_b)
        else:
            f.put(u, y, v, slab(_row_mat(mat, stripe, u, v, up[1]), "bottom"))
        low = min((nv for nv in nb.values() if nv is not None), default=val)
        bottom = base_fn(u, v, val) if min_y is None else max(min_y, base_fn(u, v, val))
        edge = any(nv is None for nv in nb.values())
        top_fill = y - 1 if not edge else min(y - 1, math.floor(low))
        for yy in range(min(bottom, math.floor(low) + 1), top_fill + 1):
            if f.get(u, yy, v) is None:
                f.put(u, yy, v, full_b)
        if edge and rafters and f.get(u, y - 1, v) is None:
            f.put(u, y - 1, v, slab(Pal.WOOD, "top"))


def beams_and_brackets(f, hx, hz, ytop, cols_x, cols_z, reach, levels):
    """阑额 (七朱八白) + 普拍枋 + brackets stepping out `reach`; returns the eave purlin y."""
    for u, v, face in ring_cells(hx, hz):
        k = u if face in ("north", "south") else v
        f.put(u, ytop, v, Pal.WHITE if (k % 3 == 1) else Pal.RED)
        f.put(u, ytop + 1, v, B(Pal.WOOD + "_planks"))

    def spots(cs):
        out = set(cs)
        for a, c in zip(cs, cs[1:]):
            span = c - a
            n = 2 if span >= 10 else 1 if span >= 5 else 0
            for i in range(1, n + 1):
                out.add(a + round(span * i / (n + 1)))
        return sorted(out)

    pos = []
    for x in spots([c for c in cols_x if abs(c) <= hx]):
        pos += [(x, -hz, "north"), (x, hz, "south")]
    for z in spots([c for c in cols_z if abs(c) <= hz])[1:-1]:
        pos += [(-hx, z, "west"), (hx, z, "east")]
    y0 = ytop + 2
    for u, v, face in pos:
        du, dv = DIRV[face]
        su, sv = (1, 0) if face in ("north", "south") else (0, 1)
        f.put(u, y0, v, B(Pal.WOOD + "_planks"))
        for lv in range(1, levels + 1):
            ou, ov = u + du * lv, v + dv * lv
            f.put(ou, y0 + lv - 1, ov, stair(Pal.WOOD, OPP[face], "top"))
            if lv in (1, levels):
                f.put(ou + su, y0 + lv - 1, ov + sv, B(Pal.WOOD + "_trapdoor", facing=face, half="top", open=False))
                f.put(ou - su, y0 + lv - 1, ov - sv, B(Pal.WOOD + "_trapdoor", facing=face, half="top", open=False))
    for u, v, face in ring_cells(hx, hz):
        du, dv = DIRV[face]
        f.put(u + du * reach, y0 + levels - 1, v + dv * reach, B("dark_oak_log", axis="x" if face in ("north", "south") else "z"))
    for u, v, face in ring_cells(hx, hz):
        for lv in range(0, levels):
            if f.get(u, y0 + lv, v) is None:
                f.put(u, y0 + lv, v, B(Pal.WOOD + "_planks") if lv == 0 else Pal.RED)
    return y0 + levels - 1


def columns(f, xs, zs, y_floor, top_fn, keep_fn=lambda u, v: True):
    for u in xs:
        for v in zs:
            if not keep_fn(u, v):
                continue
            for du in (-1, 0, 1):
                for dv in (-1, 0, 1):
                    if du or dv:
                        f.put(u + du, y_floor + 1, v + dv, slab("smooth_stone"))
            for y in range(y_floor + 1, top_fn(u, v) + 1):
                f.put(u, y, v, Pal.COL)


def walls(f, hx, hz, y_floor, y_top, xs, zs, door_half=None, window_from=None, back_door=True):
    """Walls between the columns of a ring: front 隔扇 doors on the middle bays and windows on the side bays."""
    door_half = door_half if door_half is not None else hx // 3
    window_from = window_from if window_from is not None else door_half
    for u, v, face in ring_cells(hx, hz):
        if (face in ("north", "south") and u in xs) or (face in ("west", "east") and v in zs):
            continue
        h = y_top - y_floor
        for y in range(y_floor + 1, y_top + 1):
            k = y - y_floor
            if face == "south" and abs(u) <= max(1, door_half // 3) and k <= max(4, int(h * 0.55)):
                f.put(u, y, v, AIR)                          # the middle leaves stand open: a real way in
                f.put(u, y, v - 1, AIR)
                continue
            if face == "south" and abs(u) < hx - 1:
                if abs(u) > door_half and k <= max(3, h // 4):
                    f.put(u, y, v, B("bricks") if k < max(3, h // 4) else Pal.RED)
                    continue
                if k <= h * 0.62:
                    if k in (1, round(h * 0.3), round(h * 0.62)):
                        f.put(u, y, v, Pal.RED)
                    else:
                        f.put(u, y, v, B("spruce_trapdoor", facing="south", half="bottom", open=True))
                        f.put(u, y, v - 1, B("white_stained_glass_pane", east=True, west=True))
                    continue
                f.put(u, y, v, Pal.WHITE if k % 7 else Pal.RED)
            elif face == "north" and back_door and abs(u) <= max(2, door_half // 3) and k <= h * 0.55:
                f.put(u, y, v, Pal.RED if k == 1 or k >= h * 0.55 - 1 else B("spruce_trapdoor", facing="north", half="bottom", open=True))
            else:
                f.put(u, y, v, Pal.WHITE if k not in (1, 2, h - 1) else Pal.RED)


# ------------------------------------------------------------------------------------------------ ornaments
# Places on a building where an ornament goes (data/parts/ornaments.json): the two ends of the main ridge (鸱吻),
# the middle of the main ridge (脊刹), the top of a pyramid roof (宝顶), and under the eaves at every post (斗拱). The
# code keeps its own drawing for each place as the default; a part with that "point" may replace it. One choice per
# place and size in a building (begin_ornaments starts a building), so a building stays of a piece.
ORN = dict(chosen={}, rng=None, tier=None)
DEFAULT_WEIGHT = 3          # how strongly the code's own drawing competes with the parts for a place


def begin_ornaments(key, tier):
    import random as _random
    ORN.update(chosen={}, rng=_random.Random(f"ornaments/{key}"), tier=tier, lamps={}, lamp_rng=_random.Random(f"lamps/{key}"))


INDOOR_MOUNTS = ("ceiling", "floor", "inlay")


def lamp_cells(mount, n=1, slots=None):
    """The lamp this building uses at one kind of place (data/parts/lights.json, by mount: door, corner, post, garden,
    rail, stand, table): [(dy, block)]. One kind per building per place, picked with the building's own lamp rng (not
    the generator's), so every door lamp of a building matches and nothing else of the building changes."""
    import parts as _parts
    if not _parts.LIGHTS:
        _parts.reload_lights()
    chosen = ORN.setdefault("lamps", {})
    if mount not in chosen:
        cands = [e for e in _parts.LIGHTS.values()
                 if e.get("mount") == mount and (not e.get("tiers") or ORN.get("tier") in e["tiers"])]
        import random as _random
        rng = ORN.get("lamp_rng") or _random.Random(f"lamps/{mount}")
        chosen[mount] = rng.choices(cands, weights=[e.get("weight", 1) for e in cands])[0] if cands else None
    e = chosen[mount]
    if e is None:
        return []
    slots = dict(slots or {})
    slots.setdefault("rail", "spruce_fence")
    out = []
    for dy, s in e.get("blocks", []):
        s = s.replace("$n", str(max(1, min(4, n))))
        if s == "$jade":
            s = Pal.JADE
        elif s.startswith("$") and parse(s[1:])[0] in slots:
            name, props = parse(s[1:])
            s = B(slots[name], **props)
        else:
            s = _slot_block(s)
        out.append((dy, s))
    return out


def put_lamp(f, mount, u, y, v, n=1, slots=None):
    """Draw this building's lamp for a kind of place with its mounting point at (u, y, v)."""
    for dy, st in lamp_cells(mount, n, slots):
        f.put(u, y + dy, v, st)


_SWAP_X = {"east": "west", "west": "east"}
_SWAP_Z = {"north": "south", "south": "north"}
_SWAP_SHAPE = {"inner_left": "inner_right", "inner_right": "inner_left", "outer_left": "outer_right", "outer_right": "outer_left"}


def mirror_state(s, mx=False, mz=False):
    """A block state mirrored across the x (east-west) and/or z (north-south) axis."""
    if not (mx or mz) or "[" not in s:
        return s
    name, props = parse(s)
    q = {}
    for k, v in props.items():
        if k == "facing":
            v = _SWAP_X.get(v, v) if mx else v
            v = _SWAP_Z.get(v, v) if mz else v
        elif k == "shape" and (mx != mz):
            v = _SWAP_SHAPE.get(v, v)
        elif k == "hinge" and (mx != mz):
            v = {"left": "right", "right": "left"}.get(v, v)
        if mx and k in _SWAP_X:
            k = _SWAP_X[k]
        elif mz and k in _SWAP_Z:
            k = _SWAP_Z[k]
        q[k] = v
    return name + "[" + ",".join(f"{k}={v}" for k, v in sorted(q.items())) + "]"


def _slot_block(s, slots=None):
    """$ridge, $full, $cap, $gold, $tile_stairs[facing=..,half=..], $tile_slab[type=..] in the building's roof, or a
    slot the caller passes (brackets: $trim_stairs, $trim_trapdoor, $trim_slab of the walls); else s itself."""
    if not s.startswith("$"):
        return s
    name, props = parse(s[1:])
    if slots and slots.get(name):
        return B(slots[name], **props)
    if name == "tile_stairs":
        return stair(ROOF["tile"], props.get("facing", "north"), props.get("half", "bottom"), props.get("shape"))
    if name == "tile_slab":
        return slab(ROOF["tile"], props.get("type", "bottom"))
    if name == "gold":
        return Pal.GOLD
    return B(ROOF.get(name) or ROOF["ridge"], **props)


def place_ornament(f, point, u0, y0, v0, size=0, mx=False, mz=False, face="south", slots=None):
    """Draw an ornament part at a place, or return False when the code should draw its own one there.
    A part's cells are (du, dy, dv, block) in its own frame: for ridge ends +du points out along the ridge (the +u
    end; the other end is mirrored), for brackets +dv points out of the wall (turned to the wall's side by face)."""
    if ORN["rng"] is None:
        return False
    import parts as _parts
    if not _parts.ORNAMENTS:
        _parts.reload_ornaments()
    key = (point, size)
    if key not in ORN["chosen"]:
        cands = [e for e in _parts.ORNAMENTS.values()
                 if e.get("point") == point and (not e.get("tiers") or ORN["tier"] in e["tiers"])
                 and e.get("min_size", 0) <= size <= e.get("max_size", 999)]
        if not cands:
            ORN["chosen"][key] = None
        else:
            pool = cands + [None]
            weights = [e.get("weight", 1) for e in cands] + [DEFAULT_WEIGHT if point != "ridge_mid" else DEFAULT_WEIGHT * 2]
            ORN["chosen"][key] = ORN["rng"].choices(pool, weights=weights)[0]
    part = ORN["chosen"][key]
    if part is None:
        return False
    for du, dy, dv, s in part["cells"]:
        st = mirror_state(_slot_block(s, slots), mx, mz)
        if mx:
            du = -du
        if mz:
            dv = -dv
        if face != "south":                          # turn a bracket from the front wall to its own wall
            du, dv = {"north": (-du, -dv), "east": (dv, -du), "west": (-dv, du)}[face]
            st = rotate_state(st, face)
        f.put(u0 + du, y0 + dy, v0 + dv, st)
    return True


def main_ridge(f, half, ridge_y, chiwei=6):
    extra = max(0, chiwei // 8)                 # giant halls get a taller ridge body
    for u in range(-half, half + 1):
        for v in (-1, 0, 1):
            f.put(u, ridge_y + 1, v, B(ROOF["ridge"]) if v == 0 else stair(ROOF["tile"], "north" if v == 1 else "south"))
            if v:                                           # no gap under the ridge's side tiles
                for yy in range(ridge_y, ridge_y - 5, -1):
                    if f.get(u, yy, v) is not None:
                        break
                    f.put(u, yy, v, B(ROOF["full"]))
        for e in range(extra + 1):
            f.put(u, ridge_y + 2 + e, 0, B(ROOF["ridge"]))
            if e < extra:
                for v in (-1, 1):
                    f.put(u, ridge_y + 2 + e, v, stair(ROOF["tile"], "north" if v == 1 else "south"))
        f.put(u, ridge_y + 3 + extra, 0, slab(ROOF["tile"]))
    place_ornament(f, "ridge_mid", 0, ridge_y + 2 + extra, 0, size=half)       # 脊刹 (none by default)
    for sgn in (-1, 1):
        u0 = sgn * (half + 1)
        if place_ornament(f, "ridge_end", u0, ridge_y, 0, size=chiwei, mx=sgn < 0):
            continue
        for dy in range(1, chiwei + 1):
            f.put(u0, ridge_y + dy, 0, B(ROOF["ridge"]))
        f.put(u0 - sgn, ridge_y + chiwei, 0, stair(ROOF["tile"], "east" if sgn > 0 else "west", "top"))
        if chiwei >= 5:
            f.put(u0 - sgn * 2, ridge_y + chiwei - 1, 0, stair(ROOF["tile"], "east" if sgn > 0 else "west", "top"))
        f.put(u0, ridge_y + chiwei + 1, 0, slab(ROOF["tile"]))
        f.put(u0 + sgn, ridge_y + 2, 0, stair(ROOF["tile"], "west" if sgn > 0 else "east"))


def ridge_cells(f, h, cells):
    for (u, v) in cells:
        val = h.get((u, v))
        if val is not None:
            f.put(u, math.floor(val) + 1, v, B(ROOF["ridge"]))


def finial(f, y, height=6):
    """宝顶 for pyramid roofs."""
    if place_ornament(f, "finial", 0, y, 0, size=height):
        return
    f.put(0, y + 1, 0, B(ROOF["ridge"]))
    for dy in range(2, height):
        f.put(0, y + dy, 0, B("chain", axis="y") if dy < height - 2 else Pal.GOLD)
    f.put(0, y + height, 0, B("lightning_rod", facing="up"))


# ============================================================================================ building types

def hall(f, y_base, bays_u, bays_v, fujie=True, roof="xieshan", col_low=None, col_up=None, tiers=((13, 3), (9, 3), (5, 3)),
         ground=None, stair_half=None, levels_low=3, levels_up=4, back_stairs=False, rafters_low=3, rafters_up=4):
    """A hall on a 须弥座 standing on ground level y_base. fujie: outer bay ring is a corridor under a lower roof (重檐).
    roof: 'xieshan' (歇山), 'wudian' (庑殿), 'xuanshan' (悬山), 'pyramid' (攒尖). Returns dict with floor, ridge, extents."""
    XS, ZS = positions(bays_u), positions(bays_v)
    OUT_X, OUT_Z = XS[-1], ZS[-1]
    IN_X, IN_Z = (XS[-2], ZS[-2]) if fujie else (OUT_X, OUT_Z)
    col_low = col_low or min(bays_u[0] if fujie else max(bays_u), 14)
    col_up = col_up or (col_low + 11 + levels_low)
    total_h = sum(t[1] for t in tiers)
    Y0 = y_base + total_h
    # platform tiers, outermost first
    top = y_base
    for i, (m, hgt) in enumerate(tiers):
        top += hgt
        xumizuo(f, OUT_X + m, OUT_Z + m, top, hgt, ground if i == 0 else None)
    sh = stair_half or max(3, bays_u[len(bays_u) // 2] // 2)
    top = y_base
    for i, (m, hgt) in enumerate(tiers):
        top += hgt
        balustrade(f, OUT_X + m, OUT_Z + m, top, [(-sh, sh)], [(-sh, sh)] if back_stairs else ())
    if tiers:
        stairs_down(f, sh, OUT_Z + tiers[-1][0] + 1, Y0, y_base, lanterns_every=0)
        if back_stairs:
            back = f.sub(0, 0, "north")
            stairs_down(back, sh, OUT_Z + tiers[-1][0] + 1, Y0, y_base)
    # floor
    for u in range(-OUT_X - 2, OUT_X + 3):
        for v in range(-OUT_Z - 2, OUT_Z + 3):
            f.put(u, Y0, v, Pal.FLOOR)
    low_top, up_top = Y0 + col_low, Y0 + col_up

    def keep(u, v):
        outer = abs(u) == OUT_X or abs(v) == OUT_Z
        inner = abs(u) <= IN_X and abs(v) <= IN_Z and (abs(u) == IN_X or abs(v) == IN_Z)
        return outer or inner

    columns(f, XS, ZS, Y0, lambda u, v: low_top if (abs(u) == OUT_X or abs(v) == OUT_Z) else up_top, keep)
    res = dict(floor=Y0, out=(OUT_X, OUT_Z))
    if fujie:
        low_p = beams_and_brackets(f, OUT_X, OUT_Z, low_top + 1, XS, ZS, levels_low, levels_low)
        if col_low >= 14:
            for u, v, face in ring_cells(IN_X, IN_Z):
                along = u if face in ("north", "south") else v
                if along % 9 == 4 and face == "south":
                    put_lamp(f, "stand", u, Y0 + 2, v + 1)
        up_p = beams_and_brackets(f, IN_X, IN_Z, up_top + 1, XS, ZS, levels_up, levels_up)
        walls(f, IN_X, IN_Z, Y0, up_top, XS, ZS)
        lex, lez = OUT_X + levels_low + rafters_low, OUT_Z + levels_low + rafters_low
        lrun = lex - IN_X - 3
        kl = max(1.0, max(lex, lez) / 45.0)
        low_h = roof_surface(lex, lez, low_p + 1, rafters_low, lrun, lrun * 0.5,
                             layers=tuple(max(1, round(L * kl)) for L in (5, 3, 1)), push=(round(2 * kl), round(kl) - 1), inner=(IN_X, IN_Z))
        raster_roof(f, low_h, lambda u, v, val: max(low_p + 1, math.floor(val) - 2))
        for u, v, face in ring_cells(IN_X + 1, IN_Z + 1):
            val = max([low_h.get((u + a, v + c), 0) for a in (-1, 0, 1) for c in (-1, 0, 1)])
            f.put(u, math.floor(val) + 1, v, B(ROOF["ridge"]))
            f.put(u, math.floor(val) + 2, v, slab(ROOF["tile"]))
        purlin, hx, hz, lv, raf = up_p, IN_X, IN_Z, levels_up, rafters_up
    else:
        purlin = beams_and_brackets(f, OUT_X, OUT_Z, low_top + 1, XS, ZS, levels_low, levels_low)
        walls(f, OUT_X, OUT_Z, Y0, low_top, XS, ZS)
        hx, hz, lv, raf = OUT_X, OUT_Z, levels_low, rafters_low
    ex, ez = hx + lv + raf, hz + lv + raf
    run = ez - raf
    rise = round(2 * (hz + lv) / 3.0)
    big = max(ex, ez) >= 30
    k_scale = max(1.0, max(ex, ez) / 45.0)          # giant halls: corner lift and 冲出 grow with the eaves
    layers = tuple(max(1, round(L * k_scale)) for L in ((8, 5, 3, 2, 1) if big else (5, 3, 1) if max(ex, ez) >= 14 else (2, 1)))
    if k_scale > 1.5:
        layers = layers + tuple(max(1, round(L * k_scale * 0.5)) for L in (8, 5, 3))
    push = (round(3 * k_scale), round(1 * k_scale)) if big else (2, 0)
    if roof == "pyramid":
        rise = round((hz + lv) * 0.9)
        h = roof_surface(ex, ez, purlin + 1, raf, run, rise, None, layers, push)
    elif roof == "wudian":
        h = roof_surface(ex, ez, purlin + 1, raf, run, rise, None, layers, push)
    elif roof in ("xuanshan", "juanpeng"):
        h = roof_surface(ex, ez, purlin + 1, raf, run, rise, ex, layers, push)
        if roof == "juanpeng":       # rounded ridge: shave the top row into a soft crest
            top = max(h.values())
            h = {k: (val if val < top - 0.75 else top - 0.5) for k, val in h.items()}
    elif roof == "yingshan":
        h = roof_surface(OUT_X + 1, ez, purlin + 1, raf, run, rise, OUT_X + 1, layers, push)
    else:
        h = roof_surface(ex, ez, purlin + 1, raf, run, rise, hx - 2, layers, push)
    raster_roof(f, h, lambda u, v, val: (purlin if (abs(u) <= hx and abs(v) <= hz) else max(purlin, math.floor(val) - 2)))
    ridge_y = math.floor(max(h.values()))
    if roof == "xieshan":
        gx = hx - 2
        for sgn in (-1, 1):
            u = sgn * gx
            for v in range(-ez, ez + 1):
                t, s = h.get((u, v)), h.get((u + sgn, v))
                if t is None or s is None:
                    continue
                for y in range(math.floor(s) + 1, math.floor(t)):
                    f.put(u, y, v, Pal.RED)
            f.put(u, math.floor(h[(u, 0)]) - 3, 0, Pal.WHITE)
            f.put(u, math.floor(h[(u, 0)]) - 4, 0, Pal.WHITE)
        ridge_y = math.floor(max(h[(u, 0)] for u in range(-gx, gx + 1)))
        main_ridge(f, gx, ridge_y, round(7 * k_scale) if big else 4)
        for sgn in (-1, 1):
            ridge_cells(f, h, [(sgn * gx, v) for v in range(-ez + lv + 4, ez - lv - 3)])
            for sv in (-1, 1):
                u0, v0 = sgn * (gx + 1), sv * (ez - lv - 4)
                ridge_cells(f, h, [(u0 + sgn * i, v0 + sv * i) for i in range(0, lv + raf + 5)])
    elif roof == "wudian":
        half = ex - ez
        ridge_y = math.floor(max(h[(u, 0)] for u in range(-half, half + 1)))
        main_ridge(f, half, ridge_y, round(8 * k_scale) if big else 4)
        for sgn in (-1, 1):
            for sv in (-1, 1):
                ridge_cells(f, h, [(sgn * (half + t), sv * t) for t in range(1, ez + 1)])
    elif roof in ("xuanshan", "juanpeng", "yingshan"):
        if roof != "juanpeng":
            main_ridge(f, ex if roof == "xuanshan" else OUT_X + 1, ridge_y, 3)
        for sgn in (-1, 1):            # 山墙 gable walls under the roof
            u = sgn * OUT_X
            for v in range(-OUT_Z, OUT_Z + 1):
                t = h.get((u, v))
                if t is None:
                    continue
                for y in range(purlin + 1, math.floor(t)):
                    if f.get(u, y, v) is None:
                        f.put(u, y, v, Pal.WHITE)
    elif roof == "pyramid":
        for sgn in (-1, 1):
            for sv in (-1, 1):
                ridge_cells(f, h, [(sgn * t, sv * t) for t in range(1, ex + 1)])
        finial(f, ridge_y, 6 if big else 4)
    res.update(ridge=ridge_y, eave=(ex, ez), base=y_base, inner=(IN_X, IN_Z))
    return res


def corridor_ring(f, y, hx, hz, width=7, col_h=7, gaps=(), side_walls=True):
    """回廊 round a courtyard: centre line on the rectangle |u| = hx, |v| = hz. gaps: list of (side, a0, a1) left open
    (side in 'front', 'back', 'left', 'right'; a = position along that side) for gate halls. Roof is one height field:
    ridge on the centre line, so the corners meet as proper valleys/hips."""
    w2 = width // 2
    rise_per = 0.55
    ridge = y + col_h + 4

    def in_gap(u, v):
        for side, a0, a1 in gaps:
            if side == "front" and v > 0 and abs(v - hz) <= w2 + 3 and a0 <= u <= a1:
                return True
            if side == "back" and v < 0 and abs(v + hz) <= w2 + 3 and a0 <= u <= a1:
                return True
            if side == "left" and u < 0 and abs(u + hx) <= w2 + 3 and a0 <= v <= a1:
                return True
            if side == "right" and u > 0 and abs(u - hx) <= w2 + 3 and a0 <= v <= a1:
                return True
        return False

    h = {}
    for u in range(-hx - w2 - 3, hx + w2 + 4):
        for v in range(-hz - w2 - 3, hz + w2 + 4):
            du = abs(abs(u) - hx) if abs(v) <= hz + w2 + 2 else 99
            dv = abs(abs(v) - hz) if abs(u) <= hx + w2 + 2 else 99
            d = min(du, dv)
            if d > w2 + 2 or in_gap(u, v):
                continue
            h[(u, v)] = ridge - rise_per * d
    # floor, columns, rails/walls
    for (u, v), val in h.items():
        du = abs(abs(u) - hx) if abs(v) <= hz + w2 else 99
        dv = abs(abs(v) - hz) if abs(u) <= hx + w2 else 99
        d = min(du, dv)
        if d <= w2:
            f.put(u, y, v, Pal.FLOOR)
            for yy in range(y + 1, y + col_h + 1):
                if f.get(u, yy, v) is None:
                    f.put(u, yy, v, AIR)
        on_col_line = d == w2
        if on_col_line:
            along = u if dv < du else v
            outer = (abs(u) > hx) if du <= dv else (abs(v) > hz)
            if along % 5 == 0:
                for yy in range(y + 1, y + col_h + 1):
                    f.put(u, yy, v, Pal.COL)
            elif outer and side_walls:
                for yy in range(y + 1, y + col_h + 1):
                    k = yy - y
                    pane = B("white_stained_glass_pane", **({"east": True, "west": True} if dv < du else {"north": True, "south": True}))
                    f.put(u, yy, v, Pal.RED if k in (1, col_h) else (pane if (k in (col_h // 2, col_h // 2 + 1) and along % 5 in (2, 3))
                                                                    else Pal.WHITE))
            elif not outer:
                f.put(u, y + col_h, v, B("spruce_trapdoor", facing="south", half="top", open=False))
                if along % 5 in (1, 4):
                    f.put(u, y + 1, v, B("spruce_fence", east=True, west=True) if dv < du else B("spruce_fence", north=True, south=True))
            f.put(u, y + col_h + 1, v, Pal.RED)
    raster_roof(f, h, lambda u, v, val: y + col_h + 1)
    # ridge capping on the centre line
    for (u, v), val in h.items():
        du = abs(abs(u) - hx) if abs(v) <= hz else 99
        dv = abs(abs(v) - hz) if abs(u) <= hx else 99
        if min(du, dv) == 0:
            f.put(u, math.floor(val) + 1, v, B(ROOF["ridge"]))


def wall_ring(f, y, hx, hz, height=8, gaps=()):
    """院墙: red-plastered wall with a tiled coping; gaps as in corridor_ring (side, a0, a1)."""
    for u, v, face in ring_cells(hx, hz):
        along = u if face in ("north", "south") else v
        side = {"south": "front", "north": "back", "west": "left", "east": "right"}[face]
        if any(s == side and a0 <= along <= a1 for s, a0, a1 in gaps):
            continue
        for yy in range(y + 1, y + height):
            f.put(u, yy, v, B("stone_bricks") if yy - y <= 1 else B("red_terracotta"))
        f.put(u, y + height, v, B(ROOF["full"]))
        out = DIRV[face]
        f.put(u + out[0], y + height, v + out[1], stair(ROOF["tile"], OPP[face]))
        f.put(u - out[0], y + height, v - out[1], stair(ROOF["tile"], face))
        f.put(u, y + height + 1, v, B(ROOF["ridge"]))


def tower(f, y_base, size_u, size_v, floors=3, floor_h=10, shrink=2, top_roof="xieshan", tiers=((6, 3), (3, 3)), ground=None):
    """楼阁: several storeys, each with a balcony (平座) and a skirt roof (腰檐), shrinking upward; top roof xieshan."""
    total = sum(t[1] for t in tiers)
    y = y_base + total
    top = y_base
    hx, hz = size_u // 2, size_v // 2
    for i, (m, hgt) in enumerate(tiers):
        top += hgt
        xumizuo(f, hx + m, hz + m, top, hgt, ground if i == 0 else None)
        balustrade(f, hx + m, hz + m, top, [(-3, 3)])
    stairs_down(f, 3, hz + tiers[-1][0] + 1, y, y_base)
    for k in range(floors):
        last = k == floors - 1
        xs = list(range(-hx, hx + 1, max(4, (2 * hx) // 6)))
        if xs[-1] != hx:
            xs.append(hx)
        zs = list(range(-hz, hz + 1, max(4, (2 * hz) // 4)))
        if zs[-1] != hz:
            zs.append(hz)
        for u in range(-hx, hx + 1):
            for v in range(-hz, hz + 1):
                f.put(u, y, v, Pal.FLOOR if k == 0 else B(Pal.WOOD + "_planks"))
        ctop = y + floor_h
        columns(f, xs, zs, y, lambda u, v: ctop, lambda u, v: abs(u) == hx or abs(v) == hz)
        walls(f, hx, hz, y, ctop, xs, zs, door_half=max(2, hx // 4))
        p = beams_and_brackets(f, hx, hz, ctop + 1, xs, zs, 2, 2)
        if last:
            ex, ez = hx + 5, hz + 5
            rise = round(2 * (hz + 2) / 3.0)
            h = roof_surface(ex, ez, p + 1, 3, ez - 3, rise, (hx - 2) if top_roof == "xieshan" else None,
                             (5, 3, 1) if ex >= 14 else (2, 1), (2, 0))
            raster_roof(f, h, lambda u, v, val: p if (abs(u) <= hx and abs(v) <= hz) else max(p, math.floor(val) - 2))
            ry = math.floor(max(h[(u, 0)] for u in range(-(hx - 2), hx - 1))) if top_roof == "xieshan" else math.floor(max(h.values()))
            if top_roof == "xieshan":
                main_ridge(f, hx - 2, ry, 4)
            else:
                finial(f, ry, 5)
            return dict(top=ry)
        # skirt roof round this storey, then the balcony of the next one standing on it
        ex, ez = hx + 5, hz + 5
        h = roof_surface(ex, ez, p + 1, 3, 4, 2.5, None, (3, 2, 1), (1, 0), inner=(hx, hz))
        raster_roof(f, h, lambda u, v, val: max(p + 1, math.floor(val) - 1))
        y_next = math.floor(max(h.values())) + 1
        nhx, nhz = hx - shrink, hz - shrink
        for u in range(-nhx - 2, nhx + 3):
            for v in range(-nhz - 2, nhz + 3):
                f.put(u, y_next, v, B(Pal.WOOD + "_planks"))
                for yy in range(p + 1, y_next):
                    if abs(u) <= hx and abs(v) <= hz and f.get(u, yy, v) is None:
                        f.put(u, yy, v, Pal.RED)
        for u, v, face in ring_cells(nhx + 2, nhz + 2):
            f.put(u, y_next + 1, v, B("spruce_fence", **({"east": True, "west": True} if face in ("north", "south") else {"north": True, "south": True})))
        y, hx, hz = y_next, nhx, nhz


def pagoda(f, y_base, size, storeys=9, storey_h=8, shrink=1, ground=None):
    """Square pagoda (密檐/楼阁式 mix): masonry body, an eave at every storey, pyramid top with a spire (塔刹)."""
    hx = size // 2
    xumizuo(f, hx + 6, hx + 6, y_base + 3, 3, ground)
    xumizuo(f, hx + 3, hx + 3, y_base + 6, 3)
    balustrade(f, hx + 6, hx + 6, y_base + 3, [(-2, 2)])
    balustrade(f, hx + 3, hx + 3, y_base + 6, [(-2, 2)])
    stairs_down(f, 2, hx + 4, y_base + 6, y_base)
    y = y_base + 6
    for k in range(storeys):
        top = y + storey_h
        for u in range(-hx, hx + 1):
            for v in range(-hx, hx + 1):
                edge = abs(u) == hx or abs(v) == hx
                if not edge:
                    continue
                for yy in range(y + 1, top + 1):
                    corner = abs(u) == hx and abs(v) == hx
                    win = (abs(u) <= 1 or abs(v) <= 1) and y + 2 <= yy <= y + storey_h - 3
                    pane = B("white_stained_glass_pane", **({"east": True, "west": True} if abs(v) == hx else {"north": True, "south": True}))
                    f.put(u, yy, v, Pal.COL if corner else (AIR if win and k > 0 else pane if win else
                                                            Pal.WHITE if (yy - y) % 4 else Pal.RED))
        for u in range(-hx, hx + 1):
            for v in range(-hx, hx + 1):
                f.put(u, top + 1, v, B(Pal.WOOD + "_planks"))
        ex = hx + 3
        if k == storeys - 1:
            h = roof_surface(ex, ex, top + 2, 2, ex - 2, round(ex * 0.9), None, (3, 2, 1), (1, 0))
            raster_roof(f, h, lambda u, v, val: top + 1)
            ry = math.floor(max(h.values()))
            for sgn in (-1, 1):
                for sv in (-1, 1):
                    ridge_cells(f, h, [(sgn * t, sv * t) for t in range(1, ex + 1)])
            f.put(0, ry + 1, 0, B(ROOF["ridge"]))
            for dy in range(2, 16):
                f.put(0, ry + dy, 0, Pal.GOLD if dy in (4, 7, 10) else B("chain", axis="y"))
            f.put(0, ry + 16, 0, Pal.GOLD)
            f.put(0, ry + 17, 0, B("lightning_rod", facing="up"))
            return dict(top=ry + 17)
        h = roof_surface(ex, ex, top + 2, 2, 2, 1.6, None, (2, 1), (1, 0), inner=(hx - 1, hx - 1))
        raster_roof(f, h, lambda u, v, val: top + 1)
        y = math.floor(max(h.values()))
        if (k + 1) % 2 == 0:
            hx -= shrink


def pavilion(f, y_base, size=9, double=False, ground=None):
    """亭: square, columns at the corners (and mid-sides when big), pyramid roof, jade base with railing."""
    hx = size // 2
    xumizuo(f, hx + 2, hx + 2, y_base + 2, 2, ground)
    balustrade(f, hx + 2, hx + 2, y_base + 2, [(-1, 1)], step=3)
    y = y_base + 2
    xs = [-hx, hx] if size < 11 else [-hx, 0, hx]
    columns(f, xs, xs, y, lambda u, v: y + max(5, size // 2 + 2), lambda u, v: abs(u) == hx or abs(v) == hx)
    p = beams_and_brackets(f, hx, hx, y + max(5, size // 2 + 2) + 1, xs, xs, 1, 1)
    ex = hx + 3
    if double:
        h = roof_surface(ex, ex, p + 1, 2, 3, 1.8, None, (2, 1), (1, 0), inner=(hx - 1, hx - 1))
        raster_roof(f, h, lambda u, v, val: p + 1)
        y2 = math.floor(max(h.values()))
        for u in range(-hx + 1, hx):
            for v in range(-hx + 1, hx):
                for yy in range(p + 1, y2 + 4):
                    if abs(u) == hx - 1 or abs(v) == hx - 1:
                        f.put(u, yy, v, Pal.RED)
        p = y2 + 3
        ex = hx + 1
    h = roof_surface(ex, ex, p + 1, 2, ex - 2, round(ex * 0.95), None, (3, 2, 1) if ex >= 6 else (1,), (1, 0))
    raster_roof(f, h, lambda u, v, val: p)
    ry = math.floor(max(h.values()))
    for sgn in (-1, 1):
        for sv in (-1, 1):
            ridge_cells(f, h, [(sgn * t, sv * t) for t in range(1, ex + 1)])
    finial(f, ry, 4)
    return dict(top=ry)


def gate_tower(f, y_base, width, depth, height, n_arch=3, arch_w=7, arch_h=11, hall_bays=(7, 9, 9, 11, 9, 9, 7),
               hall_depth=(7, 9, 9, 7), ground=None):
    """城门楼 / 山门: a masonry podium (城台) with arched passages along v, a crenellated parapet and a double-eave
    hall on top (like 天安门 / 南天门). Returns the top floor y."""
    hx, hz = width // 2, depth // 2
    top = y_base + height
    for u in range(-hx, hx + 1):
        for v in range(-hz, hz + 1):
            g = ground(*f.w(u, v)) if ground else y_base
            batter = (top - y_base) // 10      # walls lean in a little (收分)
            if max(abs(u) - (hx - batter), abs(v) - (hz - batter), 0) > 0:
                # outer skin steps in with height
                inset = max(abs(u) - hx + batter, abs(v) - hz + batter)
                yb = y_base + inset * 10
                f.col(u, v, min(g + 1, y_base), yb, B("stone_bricks"))
                continue
            f.col(u, v, min(g + 1, y_base), top - 1, B("stone_bricks"))
            f.put(u, top, v, Pal.FLOOR)
    # red plaster on the upper podium faces, white jade band - round the top edge, which is set in when the walls lean
    # (on the outer ring, a tall podium's skin stops lower: a band there hung in the air)
    batter = (top - y_base) // 10
    for u, v, face in ring_cells(hx - batter, hz - batter):
        f.put(u, top - 1, v, Pal.JADE)
    # arched passages
    spacing = arch_w + 6
    centres = [(i - (n_arch - 1) / 2) * spacing for i in range(n_arch)]
    for c in centres:
        c = int(round(c))
        aw = arch_w if c == 0 else arch_w - 2
        ah = arch_h if c == 0 else arch_h - 3
        r = aw / 2
        for u in range(c - aw // 2, c + aw // 2 + 1):
            du = abs(u - c)
            hh = ah - r + math.sqrt(max(0.0, r * r - du * du))
            for v in range(-hz - 1, hz + 2):
                for yy in range(y_base + 1, y_base + int(hh) + 1):
                    f.put(u, yy, v, AIR)
                f.put(u, y_base, v, Pal.FLOOR)
        for v in (-hz, hz):           # jade voussoir ring
            for u in range(c - aw // 2 - 1, c + aw // 2 + 2):
                du = abs(u - c)
                hh = ah - r + math.sqrt(max(0.0, (r + 1) ** 2 - du * du))
                f.put(u, y_base + int(hh) + 1, v, Pal.JADE)
    # parapet with crenels and a balustrade on top
    balustrade(f, hx - 1, hz - 1, top, [], step=4)
    res = hall(f, top, list(hall_bays), list(hall_depth), fujie=True, roof="xieshan", tiers=((2, 2),), col_low=8, col_up=19,
               levels_low=2, levels_up=3, rafters_low=3, rafters_up=3)
    # the way up: a stair inside the podium from a doorway in its back face to the hall's floor
    res["well"] = stair_well(f, y_base, res["floor"], (-hx + 1, hx - 1), -hz, "minecraft:stone_brick_stairs", B("stone_bricks"))
    return res


def solid_at(f, u, y, v):
    """Something (not air) in the canvas at local (u, y, v): its blocks over its column runs."""
    s = f.get(u, y, v)
    if s is not None:
        return s != AIR
    x, z = f.w(u, v)
    for (y0, y1, st) in reversed(f.cv.cols.get((x, z), ())):
        if y0 <= y <= y1:
            return st != AIR
    return False


_DIR = {(1, 0): "east", (-1, 0): "west", (0, 1): "south", (0, -1): "north"}


def _well_path(n, rise):
    """A switchback in a well two cells wide (rows 0, 1) and n + 2 long (t = 0 .. n + 1): from the floor (level 0) at
    t = 0 of row 0 up `rise` steps, [(t, row, level, kind, up)] in walking order. kind: 'floor' (walked on at level + 1),
    'stair' (rising along +t in row 0, -t in row 1), 'arrive' (the floor above, stepped onto off the last stair). The
    first flight is the short one, behind a stretch of floor, so the last is whole and lands at the end of its row."""
    F = -(-rise // n)
    r = rise - n * (F - 1)
    path = [(t, 0, 0, "floor", 0) for t in range(0, n - r + 1)]
    lvl = 0
    for i in range(F):
        row = i % 2
        k = r if i == 0 else n
        for t in (range(n - k + 1, n + 1) if row == 0 else range(n, 0, -1)):
            lvl += 1
            path.append((t, row, lvl, "stair", 1 if row == 0 else -1))
        end = n + 1 if row == 0 else 0
        if i < F - 1:
            path += [(end, row, lvl, "floor", 0), (end, 1 - row, lvl, "floor", 0)]
        else:
            path.append((end, row, lvl, "arrive", 0))
    return path


def unseen_light():
    """The light block a player does not see: vanilla's 光源方块 (seen only in creative, holding the light item)."""
    return B("light", level=15, waterlogged=False)


def stair_well(f, y, top, u_range, v_back, stairs, floor, light=None, avoid=None):
    """登城梯: a stair inside a solid platform (a 城台) from a doorway in its back face (-v) at the ground (floor y) up to
    its top floor (the floor block at `top`, walked on at top + 1): a switchback in a well two cells wide, cut into the
    masonry (the steps on solid stone, three free over every step), coming up through a hole in the top floor where
    that floor is free to walk on (nothing on it two high, no door beside it). Placed in the first spot (along the back
    from the west end, then up the sides) where the well, with masonry all round it, misses the tunnels. Returns the
    walking path [(u, y, v)] or None when nowhere fits. avoid(u, v): columns another part will take (a neighbour
    joined later), kept clear of the well, its walls and its doorway."""
    rise = top - y
    if rise < 1:
        return None
    light = light or unseen_light()
    body_c, exit_c = {}, {}

    def body(u, v):                                       # masonry all the way up the column
        if (u, v) not in body_c:
            body_c[(u, v)] = all(solid_at(f, u, yy, v) for yy in range(y + 1, top + 1))
        return body_c[(u, v)]

    def exit_ok(u, v):                                    # the top floor there, free to walk on
        if (u, v) not in exit_c:
            exit_c[(u, v)] = solid_at(f, u, top, v) and not solid_at(f, u, top + 1, v) and not solid_at(f, u, top + 2, v) \
                and not any("_door" in (f.get(u + du, top + 1, v + dv) or "") for du, dv in DIRV.values())
        return exit_c[(u, v)]

    u0s = list(range(u_range[0], u_range[1] + 1))
    orients = [((1, 0), (0, 1)), ((-1, 0), (0, 1)), ((0, 1), (1, 0)), ((0, 1), (-1, 0))]
    for dl, dc in orients:                                # (along the back first: the sides may meet a wall)
        pd = (0, -1) if dl[1] == 0 else (-dc[0], 0)       # the way in: out of the back, or out of the side
        for n in (6, 5, 4, 3):
            path0 = _well_path(n, rise)
            for v0 in (range(v_back + 1, v_back + 4) if dl[1] == 0 else range(v_back + 1, -v_back - n - 1)):
                for u0 in (u0s if dl != (-1, 0) and dc != (-1, 0) else u0s[::-1]):
                    def at(t, row):
                        return u0 + dl[0] * t + dc[0] * row, v0 + dl[1] * t + dc[1] * row
                    well = {at(t, r) for t in range(n + 2) for r in (0, 1)}
                    ring = {at(t, r) for t in range(-1, n + 3) for r in (-1, 0, 1, 2)} - well
                    door = []                             # the way in: from the foot of the well back out of the face
                    pu, pv = at(0, 0)
                    while solid_at(f, pu + pd[0], y + 1, pv + pd[1]) and len(door) < 6:
                        pu, pv = pu + pd[0], pv + pd[1]
                        door.append((pu, pv))
                    if not door or solid_at(f, pu + pd[0], y + 1, pv + pd[1]) or solid_at(f, pu + pd[0], y + 2, pv + pd[1]):
                        continue
                    if not all(body(*c) for c in well) or not all(body(*c) for c in ring if c not in door):
                        continue
                    if avoid and (any(avoid(*c) for c in well | ring | set(door)) or avoid(pu + pd[0], pv + pd[1], True)):
                        continue
                    tops = [at(t, r) for (t, r, lv, kind, up) in path0 if kind == "arrive" or lv + 3 >= rise]
                    if not all(exit_ok(*c) for c in tops):
                        continue
                    keep, out = set(), []
                    for (t, r, lv, kind, up) in path0:
                        u, v = at(t, r)
                        keep.add((u, y + lv, v))
                        if kind == "stair":
                            d = (dl[0] * up, dl[1] * up)
                            f.put(u, y + lv, v, B(stairs, facing=_DIR[d], half="bottom"))
                        elif kind == "floor" or not solid_at(f, u, y + lv, v):
                            f.put(u, y + lv, v, floor)
                        out.append((u, y + lv + 1, v))
                    for i, (t, r, lv, kind, up) in enumerate(path0):
                        if kind == "arrive":
                            continue
                        u, v = at(t, r)
                        for yy in range(y + lv + 1, min(y + lv + 3, top) + 1):   # three free over it (the top floor cut)
                            if (u, yy, v) not in keep:
                                f.put(u, yy, v, light if light and i % 6 == 3 and yy == y + lv + 2 and yy < top else AIR)
                    for (u, v) in door:
                        f.put(u, y + 1, v, AIR)
                        f.put(u, y + 2, v, AIR)
                    return out
    return None


def paifang(f, y, span=14, h_in=16, h_out=12):
    """牌坊 三间四柱 with three little hip roofs; stone posts with 抱鼓石."""
    cin, cout = span // 2 - 2, span
    for c, hh in ((cin, h_in), (cout, h_out)):
        for sgn in (-1, 1):
            u = sgn * c
            for du in (-1, 0, 1):
                for dv in (-1, 0, 1):
                    f.put(u + du, y + 1, dv, Pal.JADE)
            f.put(u, y + 2, -2, stair("quartz", "south"))
            f.put(u, y + 2, 2, stair("quartz", "north"))
            f.put(u, y + 1, -2, Pal.JADE)
            f.put(u, y + 1, 2, Pal.JADE)
            for yy in range(y + 2, y + hh + 1):
                f.put(u, yy, 0, Pal.JADE if yy < y + hh - 2 else Pal.RED)
    for u in range(-cin, cin + 1):
        f.put(u, y + h_in - 4, 0, Pal.RED)
        f.put(u, y + h_in - 1, 0, Pal.RED)
        if abs(u) <= 2:
            f.put(u, y + h_in - 2, 0, Pal.GOLD if abs(u) < 2 else B("polished_blackstone"))
            f.put(u, y + h_in - 3, 0, Pal.GOLD if abs(u) < 2 else B("polished_blackstone"))
    for sgn in (-1, 1):
        for u in range(cin, cout + 1):
            f.put(sgn * u, y + h_out - 2, 0, Pal.RED)
            f.put(sgn * u, y + h_out - 1, 0, Pal.RED)

    def little_roof(u0, u1, yb, depth):
        sub = f.sub((u0 + u1) // 2, 0, "south")
        ex = (u1 - u0) // 2 + 2
        h = roof_surface(ex, depth, yb, 1, depth - 1, depth * 0.55, None, (2, 1), (1, 0))
        raster_roof(sub, h, lambda u, v, val: yb)
        main_ridge(sub, ex - depth, math.floor(max(h.values())), 3)

    little_roof(-cin - 1, cin + 1, y + h_in + 1, 3)
    for sgn in (-1, 1):
        a, c = sorted((sgn * (cin + 2), sgn * (cout + 1)))
        little_roof(a, c, y + h_out + 1, 2)


# ============================================================================================ ground works

def terrace(f, u0, v0, u1, v1, y, ground, edge_rail=True, pave=Pal.FLOOR, clear_to=None, axis_path=0, clear_h=6, grid=9,
            keep_rock=None):
    """A paved terrace at y over local rectangle: masonry down to the terrain, air above (cutting into slopes),
    jade railing on edges that drop more than 2. axis_path: half width of a jade 御道 along u = 0."""
    COURSES = [B("stone_bricks"), B("stone_bricks"), B("andesite"), B("stone_bricks"), B("mossy_stone_bricks"),
               B("stone_bricks"), B("cracked_stone_bricks"), B("polished_andesite")]
    for u in range(u0, u1 + 1):
        for v in range(v0, v1 + 1):
            x, z = f.w(u, v)
            g = ground(x, z)
            if keep_rock is not None and g > y + keep_rock:
                continue                      # leave tall rock standing in the court as an outcrop, no flat cut
            if g < y:
                if u in (u0, u1) or v in (v0, v1):
                    # banded courses (each 5 high, varied), red-plastered parapet band just under the rim
                    yy = g + 1
                    k = (x * 7 + z * 13) % 3
                    while yy <= y - 1:
                        top_ = min(y - 1, (yy // 5) * 5 + 4)
                        band = COURSES[((yy // 5) + k) % len(COURSES)]
                        if top_ >= y - 3:
                            band = B("stone_bricks")
                        f.col(u, v, yy, top_, band)
                        yy = top_ + 1
                    if y - g > 6:
                        f.col(u, v, y - 2, y - 2, B("polished_andesite"))
                else:
                    f.col(u, v, g + 1, y - 1, B("stone"))
            top = max(g, y + clear_h) if clear_to is None else clear_to
            if top > y:
                f.col(u, v, y + 1, top, AIR)
            edge = u in (u0, u1) or v in (v0, v1)
            line = grid and ((u - u0) % grid == 0 or (v - v0) % grid == 0)
            f.col(u, v, y, y, (Pal.JADE if abs(u) <= axis_path else B("stone_bricks") if line else pave) if not edge else B("stone_bricks"))
    # buttresses (扶壁) every 18 blocks where the wall is tall: 3 wide, 2 deep, stepped back near the top
    for side in ("n", "s", "w", "e"):
        rng_ = range(u0 + 9, u1 - 8, 18) if side in ("n", "s") else range(v0 + 9, v1 - 8, 18)
        for t in rng_:
            uu, vv, du, dv = ((t, v0, 0, -1) if side == "n" else (t, v1, 0, 1) if side == "s" else
                              (u0, t, -1, 0) if side == "w" else (u1, t, 1, 0))
            for w_ in (-1, 0, 1):
                for depth in (1, 2):
                    bu = uu + (w_ if side in ("n", "s") else 0) + du * depth
                    bv = vv + (w_ if side in ("w", "e") else 0) + dv * depth
                    x, z = f.w(bu, bv)
                    g = ground(x, z)
                    top_ = y - 3 - 4 * (depth - 1)
                    if top_ - g > 8:
                        f.col(bu, bv, g + 1, top_, B("stone_bricks"))
                        f.put(bu, top_ + 1, bv, slab("stone_brick"))
    if edge_rail:
        for u in range(u0, u1 + 1):
            for v in (v0, v1):
                if ground(*f.w(u, v)) < y - 2 and u % 4:
                    f.put(u, y + 1, v, B("diorite_wall", east="low", west="low", up=False))
                elif ground(*f.w(u, v)) < y - 2:
                    f.put(u, y + 1, v, B("quartz_pillar", axis="y"))
                    f.put(u, y + 2, v, slab("quartz"))
        for v in range(v0 + 1, v1):
            for u in (u0, u1):
                if ground(*f.w(u, v)) < y - 2 and v % 4:
                    f.put(u, y + 1, v, B("diorite_wall", north="low", south="low", up=False))
                elif ground(*f.w(u, v)) < y - 2:
                    f.put(u, y + 1, v, B("quartz_pillar", axis="y"))
                    f.put(u, y + 2, v, slab("quartz"))


def pool(f, u0, v0, u1, v1, y, depth=3):
    """A stone-edged water pool sunk into a terrace (reflects the halls, the trick from 诛仙世界)."""
    for u in range(u0, u1 + 1):
        for v in range(v0, v1 + 1):
            edge = u in (u0, u1) or v in (v0, v1)
            if edge:
                f.put(u, y, v, Pal.JADE)
                f.put(u, y + 1, v, slab("quartz"))
                continue
            f.put(u, y - depth, v, B("dark_prismarine"))
            for yy in range(y - depth + 1, y + 1):
                f.put(u, yy, v, B("water"))


# ============================================================================================ courtyard clusters, trees, ornaments

def _h(*a):
    h = 2166136261
    for v in a:
        h = ((h ^ (v & 0xFFFFFFFF)) * 16777619) & 0xFFFFFFFF
    return h


def pine(f, u, y, v, height=14, seed=0):
    """A flat-topped pine (松): leaning trunk, tiers of dark leaves wider at the top, as in painted pines."""
    r = _h(u, v, seed)
    lean_u, lean_v = (r % 3) - 1, ((r >> 2) % 3) - 1
    tu, tv = u, v
    for k in range(height):
        if k > height * 0.5 and k % 4 == 0:          # the lean steps over face to face, so the trunk stays in one piece
            if lean_u:
                f.put(tu + lean_u, y + k, tv, B("spruce_log", axis="x"))
            tu += lean_u
            if lean_v:
                f.put(tu, y + k, tv + lean_v, B("spruce_log", axis="z"))
            tv += lean_v
        f.put(tu, y + 1 + k, tv, B("spruce_log", axis="y"))
    for tier, (dy, rad) in enumerate(((height - 6, 3), (height - 3, 4), (height, 3))):
        for du in range(-rad - 1, rad + 2):
            for dv in range(-rad - 1, rad + 2):
                d = abs(du) + abs(dv) * 1.3
                if d <= rad + ((_h(du, dv, r, tier) % 3) - 1) * 0.6:
                    for t in (0, 1):
                        if f.get(tu + du, y + dy + t, tv + dv) is None:
                            f.put(tu + du, y + dy + t, tv + dv, B("spruce_leaves", persistent=True))


def round_tree(f, u, y, v, height=9, leaves="dark_oak_leaves", seed=0):
    """A broad courtyard tree (槐 / 银杏 stand-in): straight trunk, round crown."""
    for k in range(height - 3):
        f.put(u, y + 1 + k, v, B("dark_oak_log", axis="y"))
    rad = 4
    cy = y + height - 2
    for du in range(-rad, rad + 1):
        for dv in range(-rad, rad + 1):
            for dy in range(-2, 4):
                d = du * du + dv * dv + (dy * 1.6) ** 2
                if d <= rad * rad + (_h(du, dv, dy, seed) % 5) - 2 and f.get(u + du, cy + dy, v + dv) is None:
                    f.put(u + du, cy + dy, v + dv, B(leaves, persistent=True))


def cauldron(f, u, y, v):
    """A bronze 鼎 on the court: oxidised copper body on short legs, incense smoke source on top."""
    for du in (-1, 0, 1):
        for dv in (-1, 0, 1):
            if du and dv:
                f.put(u + du, y + 1, v + dv, B("oxidized_cut_copper"))
            f.put(u + du, y + 2, v + dv, B("oxidized_cut_copper"))
            f.put(u + du, y + 3, v + dv, B("oxidized_cut_copper_slab", type="bottom") if (du or dv) else B("campfire", lit=True))
    f.put(u - 1, y + 4, v, B("oxidized_cut_copper_stairs", facing="east", half="bottom"))
    f.put(u + 1, y + 4, v, B("oxidized_cut_copper_stairs", facing="west", half="bottom"))


def huabiao(f, u, y, v, height=16):
    """华表: a tall white column on a plinth with a cloud board near the top and a crouching beast."""
    for du in (-1, 0, 1):
        for dv in (-1, 0, 1):
            f.put(u + du, y + 1, v + dv, Pal.JADE)
            f.put(u + du, y + 2, v + dv, slab("quartz") if (du or dv) else Pal.JADE)
    for k in range(3, height):
        f.put(u, y + k, v, B("quartz_pillar", axis="y"))
    for du, dv in ((-1, 0), (1, 0)):
        f.put(u + du, y + height - 4, v + dv, B("quartz_stairs", facing="east" if du < 0 else "west", half="top"))
    f.put(u, y + height, v, B("chiseled_quartz_block"))
    f.put(u, y + height + 1, v, B("quartz_slab", type="bottom"))


def paving(f, u0, v0, u1, v1, y, every=8):
    for u in range(u0, u1 + 1):
        for v in range(v0, v1 + 1):
            if (u - u0) % every == 0 or (v - v0) % every == 0:
                f.put(u, y, v, B("stone_bricks"))


def courtyard_unit(f, y, w, d, seed=0, big=False):
    """One walled courtyard (院): gate house in the front wall, main hall at the back, side halls east and west,
    trees and a cauldron in the yard. Local frame: centre of the yard, front = +v. Returns number of buildings."""
    r = _h(seed, w, d)
    hx, hz = w // 2, d // 2
    wall_ring(f, y, hx, hz, height=7, gaps=[("front", -4, 4)])
    hall(f.sub(0, hz - 3), y, [5, 7, 5], [4, 5], fujie=False, roof="xuanshan", tiers=((1, 1),), col_low=6)
    main_w = max(5, (w - 18) // 5)
    bays = [main_w - 1, main_w, main_w + 2, main_w, main_w - 1] if w >= 44 else [main_w, main_w + 2, main_w]
    roof = ("xieshan", "xuanshan", "wudian")[r % 3] if big else ("xuanshan", "xieshan")[r % 2]
    hall(f.sub(0, -hz + 9 + (2 if big else 0)), y, bays, [5, 7, 5] if big else [6, 6], fujie=False, roof=roof,
         tiers=((3, 2), (1, 2)) if big else ((2, 2),), col_low=8 if big else 7)
    n = 2
    for s in (-1, 1):
        side_len = max(3, (d - 30) // 8)
        hall(f.sub(s * (hx - 7), 2, "east" if s < 0 else "west"), y, [side_len] * 3, [5, 5], fujie=False, roof="xuanshan",
             tiers=((1, 1),), col_low=6)
        n += 1
    paving(f, -hx + 1, -hz + 1, hx - 1, hz - 1, y, 6)
    for s in (-1, 1):
        if (r >> (3 + s)) & 1:
            pine(f, s * (hx // 3), y, hz // 3, 11 + r % 5, seed + s)
        else:
            round_tree(f, s * (hx // 3), y, hz // 3, 9, seed=seed + s)
    if big:
        cauldron(f, 0, y, 2)
    return n + 1


def fill_zone(f, u0, v0, u1, v1, y, seed=0, lane=6, min_w=40, max_w=64, min_d=46, max_d=70, big_every=4, themes=("grey",)):
    """Pack courtyard units into a rectangle (rows front to back, units side by side), lanes between them."""
    n = 0
    v = v1
    row = 0
    while v - min_d >= v0:
        r = _h(seed, row)
        d = min_d + r % (max_d - min_d + 1)
        if v - d < v0:
            d = v - v0
        u = u0
        k = 0
        while u + min_w <= u1:
            r2 = _h(seed, row, k)
            w = min_w + r2 % (max_w - min_w + 1)
            if u + w > u1:
                w = u1 - u
            if w >= min_w:
                cu, cv = u + w // 2, v - d // 2
                roof_theme(themes[(r2 >> 5) % len(themes)])
                n += courtyard_unit(f.sub(cu, cv), y, w - 1, d - 1, seed=r2, big=(k + row) % big_every == 0)
            u += w + lane
            k += 1
        v -= d + lane
        row += 1
    roof_theme("grey")
    return n



# ============================================================================================ variety: styles and archetypes

STYLES = {   # columns, beam / board colour, wall colour
    "red": dict(COL=B("stripped_mangrove_log", axis="y"), RED=B("mangrove_planks"), WHITE=B("calcite")),
    "dark": dict(COL=B("stripped_dark_oak_log", axis="y"), RED=B("dark_oak_planks"), WHITE=B("smooth_sandstone")),
    "plain": dict(COL=B("stripped_spruce_log", axis="y"), RED=B("spruce_planks"), WHITE=B("white_terracotta")),
    "grey": dict(COL=B("stripped_mangrove_log", axis="y"), RED=B("mangrove_planks"), WHITE=B("light_gray_terracotta")),
}


def style(name):
    for k, v in STYLES[name].items():
        setattr(Pal, k, v)


def chimney(f, u, y, v, height=6):
    for k in range(height):
        f.put(u, y + k, v, B("bricks"))
    f.put(u, y + height, v, B("campfire", lit=True))


def rockery(f, u, y, v, r=3, seed=0):
    """假山: a lumpy pile of tuff, andesite and moss."""
    mats = (B("tuff"), B("andesite"), B("mossy_cobblestone"), B("cobblestone"))
    for du in range(-r, r + 1):
        for dv in range(-r, r + 1):
            hgt = int((r + 1 - max(abs(du), abs(dv))) * 1.6) + (_h(du, dv, seed) % 3)
            for k in range(1, hgt):
                f.put(u + du, y + k, v + dv, mats[_h(du, dv, k, seed) % 4])
            if hgt > 1 and _h(du, dv, seed, 9) % 3 == 0:
                f.put(u + du, y + hgt, v + dv, B("moss_carpet"))


def pond(f, u0, v0, u1, v1, y, seed=0):
    """An irregular garden pond with lily pads, edged with stone."""
    cu, cv = (u0 + u1) / 2, (v0 + v1) / 2
    ru, rv = max(1, (u1 - u0) / 2), max(1, (v1 - v0) / 2)
    for u in range(u0, u1 + 1):
        for v in range(v0, v1 + 1):
            d = ((u - cu) / ru) ** 2 + ((v - cv) / rv) ** 2 + 0.25 * math.sin(u * 0.7 + seed) * math.cos(v * 0.5)
            if d <= 1:
                f.put(u, y, v, B("water"))
                f.put(u, y - 1, v, B("water"))
                f.put(u, y - 2, v, B("mud"))
                if _h(u, v, seed) % 23 == 0:
                    f.put(u, y + 1, v, B("lily_pad"))
            elif d <= 1.35:
                f.put(u, y, v, B("mossy_cobblestone") if _h(u, v) % 3 == 0 else B("stone"))


def herb_beds(f, u0, v0, u1, v1, y, seed=0):
    """药圃: rows of farmland with crops / flowers and water channels."""
    crops = [B("wheat", age=7), B("carrots", age=7), B("potatoes", age=7), B("beetroots", age=3)]
    flowers = [B("allium"), B("blue_orchid"), B("azure_bluet"), B("cornflower"), B("lily_of_the_valley")]
    for u in range(u0, u1 + 1):
        for v in range(v0, v1 + 1):
            rowk = (v - v0) % 5
            if rowk == 0:
                f.put(u, y, v, B("water"))
            elif rowk == 4:
                f.put(u, y, v, B("dirt_path"))
            else:
                f.put(u, y, v, B("farmland", moisture=7))
                kind = (u // 6 + v // 5 + seed) % 3
                f.put(u, y + 1, v, crops[(u // 6 + seed) % 4] if kind else flowers[_h(u, v, seed) % 5])


def _yard_trees(f, hx, hz, y, seed, r):
    for s_ in (-1, 1):
        if (r >> (3 + s_)) & 1:
            pine(f, s_ * (hx // 3), y, hz // 3, 11 + r % 4, seed=seed + s_)
        else:
            round_tree(f, s_ * (hx // 3), y, hz // 3, 9, seed=seed + s_)


def courtyard_variant(f, y, w, d, seed=0, kind="siheyuan"):
    """Courtyard archetypes, each walled with a gate in front (front = +v). Returns the number of buildings.
    siheyuan 四合院 / two 二进院 / garden 园林 / tower 楼院 / alchemy 丹院 / forge 器坊 / arena 演武场 / herbs 药圃"""
    r = _h(seed, w, d, len(kind))
    hx, hz = w // 2, d // 2
    gate_kind = r % 3
    wall_ring(f, y, hx, hz, height=6 + r % 3, gaps=[("front", -4, 4)])
    paving(f, -hx + 1, -hz + 1, hx - 1, hz - 1, y, 6)
    if gate_kind == 0:
        hall(f.sub(0, hz - 3), y, [5, 7, 5], [4, 5], fujie=False, roof="xuanshan", tiers=((1, 1),), col_low=6)
    elif gate_kind == 1:
        hall(f.sub(0, hz - 3), y, [7], [5], fujie=False, roof="xieshan", tiers=((1, 1),), col_low=6)
    else:   # moon gate in the wall
        for du in range(-5, 6):
            for k in range(1, 10):
                if du * du + (k - 4) ** 2 > 16:
                    f.put(du, y + k, hz, Pal.WHITE if k < 9 else B(ROOF["full"]))
                else:
                    f.put(du, y + k, hz, AIR)
    n = 1
    roofs = ["xieshan", "xuanshan", "juanpeng", "yingshan", "wudian"]
    mw = max(5, (w - 18) // 5)
    if kind in ("siheyuan", "alchemy", "forge"):
        bays = [mw - 1, mw, mw + 2, mw, mw - 1] if w >= 44 else [mw, mw + 2, mw]
        hall(f.sub(0, -hz + 9), y, bays, [6, 6] if r % 2 else [5, 7, 5], fujie=False, roof=roofs[r % 5],
             tiers=((2, 2),) if r % 3 else ((3, 2), (1, 2)), col_low=7 + r % 3)
        side_roof = ("xuanshan", "juanpeng", "yingshan", "xieshan")[(r >> 3) % 4]
        for s_ in (-1, 1):
            sl = max(3, (d - 30) // 8)
            hall(f.sub(s_ * (hx - 7), 2, "east" if s_ < 0 else "west"), y, [sl] * 3, [5, 5], fujie=False,
                 roof=side_roof, tiers=((1, 1),), col_low=6)
        n += 3
        if kind == "alchemy":
            for s_ in (-1, 1):
                chimney(f, s_ * (mw * 2), y + 13, -hz + 9, 5)
                cauldron(f, s_ * (hx // 3), y, hz // 4)
            herb_beds(f, -hx + 3, hz - 14, -6, hz - 6, y, seed)
        elif kind == "forge":
            for s_ in (-1, 0, 1):
                chimney(f, s_ * (mw * 2) + 1, y + 12, -hz + 7, 8)
            for k in range(3):
                f.put(-hx // 3 + k * 4, y + 1, hz // 4, B("anvil", facing="north"))
                f.put(hx // 3 - k * 4, y + 1, hz // 4, B("blast_furnace", facing="south", lit=True))
        else:
            _yard_trees(f, hx, hz, y, seed, r)
    elif kind == "two":
        for u in range(-hx + 1, hx):      # inner wall across the middle, 垂花门 in it
            for k in range(1, 6):
                if abs(u) > 2:
                    f.put(u, y + k, 0, Pal.WHITE if k < 5 else B(ROOF["full"]))
        hall(f.sub(0, 1), y, [5], [4], fujie=False, roof="xieshan", tiers=(), col_low=5)
        hall(f.sub(0, -hz + 8), y, [mw, mw + 2, mw], [5, 6], fujie=False, roof=roofs[r % 4], tiers=((2, 2),), col_low=8)
        for s_ in (-1, 1):
            hall(f.sub(s_ * (hx - 6), -hz // 2, "east" if s_ < 0 else "west"), y, [4, 5, 4], [5, 5], fujie=False,
                 roof="yingshan", tiers=((1, 1),), col_low=6)
            round_tree(f, s_ * 8, y, hz // 2, 8, seed=seed + s_)
        n += 5
    elif kind == "garden":
        pond(f, -hx + 5, -hz + 5, 2, hz // 3, y, seed)
        pavilion(f.sub(hx // 3, -hz // 3), y, size=9, double=(r % 2 == 0))
        rockery(f, -hx // 3, y, hz // 2, 3, seed)
        rockery(f, hx // 2, y, hz // 3, 2, seed + 1)
        hall(f.sub(hx // 3, hz // 2 - 2), y, [5, 6, 5], [5, 5], fujie=False, roof="juanpeng", tiers=((1, 1),), col_low=6)
        for k in range(4):
            pine(f, -hx + 4 + k * 6, y, hz - 6, 9 + k % 3, seed=seed + k)
        n += 3
    elif kind == "tower":
        tower(f.sub(0, -4), y, min(25, w - 18), min(21, d - 24), floors=2 + r % 2, floor_h=8, shrink=2, tiers=((3, 2),))
        for s_ in (-1, 1):
            hall(f.sub(s_ * (hx - 6), 4, "east" if s_ < 0 else "west"), y, [4, 5, 4], [5, 5], fujie=False, roof="xuanshan",
                 tiers=((1, 1),), col_low=6)
        n += 3
    elif kind == "arena":
        for u in range(-hx + 4, hx - 3):
            for v in range(-hz + 4, hz - 3):
                f.put(u, y, v, B("polished_andesite") if (u + v) % 2 else B("andesite"))
        rw, rd_ = min(14, hx - 8), min(10, hz - 10)
        for u in range(-rw, rw + 1):
            for v in range(-rd_, rd_ + 1):
                for k in (1, 2):
                    f.put(u, y + k, v, Pal.JADE if k == 1 else Pal.FLOOR)
        balustrade(f, rw, rd_, y + 2, [(-2, 2)], [(-2, 2)])
        for s_ in (-1, 1):
            for v in range(-hz + 6, hz - 5, 7):
                for k in range(1, 12):
                    f.put(s_ * (hx - 3), y + k, v, B("spruce_fence"))
                f.put(s_ * (hx - 3), y + 12, v, B("red_banner"))
        hall(f.sub(0, -hz + 6), y, [5, 7, 5], [5, 5], fujie=False, roof="xieshan", tiers=((2, 2),), col_low=7)
        n += 1
    elif kind == "herbs":
        herb_beds(f, -hx + 3, -hz + 3, hx - 3, hz - 10, y, seed)
        hall(f.sub(-hx + 9, hz - 10), y, [4, 5], [4, 4], fujie=False, roof="yingshan", tiers=(), col_low=5)
        n += 1
    return n


ARCHETYPES = {
    "inner_west": [("siheyuan", 3), ("two", 2), ("alchemy", 3), ("garden", 1), ("herbs", 1)],
    "inner_east": [("siheyuan", 3), ("two", 2), ("forge", 3), ("tower", 1), ("garden", 1)],
    "library": [("tower", 3), ("garden", 2), ("two", 2)],
    "outer": [("siheyuan", 4), ("two", 3), ("arena", 1), ("herbs", 2), ("garden", 1)],
    "peak": [("siheyuan", 2), ("garden", 1), ("tower", 1), ("two", 1)],
}
STYLE_SETS = {"inner_west": ("red", "red", "plain"), "inner_east": ("red", "dark", "red"), "library": ("dark", "red"),
              "outer": ("plain", "grey", "red"), "peak": ("red", "plain")}


def fill_zone2(f, u0, v0, u1, v1, y, seed=0, district="outer", themes=("grey",), lane=6, min_w=40, max_w=66, min_d=46, max_d=72):
    """Like fill_zone but picks an archetype, a wall/column style and a roof colour for every courtyard."""
    arch_list = ARCHETYPES[district]
    total_w = sum(wt for _, wt in arch_list)
    n = 0
    v = v1
    row = 0
    while v - min_d >= v0:
        r = _h(seed, row)
        d = min_d + r % (max_d - min_d + 1)
        if v - d < v0:
            d = v - v0
        u = u0
        k = 0
        while u + min_w <= u1:
            r2 = _h(seed, row, k, 77)
            w = min_w + r2 % (max_w - min_w + 1)
            if u + w > u1:
                w = u1 - u
            if w >= min_w:
                pick = r2 % total_w
                kind = arch_list[0][0]
                for kd, wt in arch_list:
                    if pick < wt:
                        kind = kd
                        break
                    pick -= wt
                roof_theme(themes[(r2 >> 7) % len(themes)])
                style(STYLE_SETS[district][(r2 >> 11) % len(STYLE_SETS[district])])
                n += courtyard_variant(f.sub(u + w // 2, v - d // 2), y, w - 1, d - 1, seed=r2, kind=kind)
            u += w + lane
            k += 1
        v -= d + lane
        row += 1
    roof_theme("grey")
    style("red")
    return n
