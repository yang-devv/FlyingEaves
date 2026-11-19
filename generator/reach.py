"""Can a player walk it: where on a generated building a player standing on the ground outside can get to on foot.

The player is 1.8 high and 0.6 wide. A place to stand is a block's top surface (a full block's at +1, a bottom slab's
and a stair's low part at +0.5, a top slab's and an upside-down stair's at +1, a fence's or a wall's at +1.5) with 1.8
free over it, or a climbable cell (a ladder, a vine, scaffolding, water) with 1.8 free from its bottom. From a place to
stand a player goes to one of the four next columns: up at most 0.6 by walking (a stair's half step), up to a whole
block by jumping (the room over the place he jumps from must then reach as high as his head will be at the top), down
any height (falling: the room over the place he falls into must reach as high as his head was). On a climbable cell he
goes up and down the column, and steps off at the top onto anything up to a block higher. Doors, fence gates and
trapdoors open; carpets, plants, torches, signs, buttons and the like are walked through; panes, bars and chains are
not; fences and walls are 1.5 high (not jumped over).

The canvas: its blocks win over its column runs (later runs over earlier), and where it has neither, cells at or under
the ground y are the world's ground, cells over it air (as the daemon stamps it).

reachable(blocks, cols, ground) -> set of (x, z, s) places stood on (s = the height of the feet, a multiple of 0.5).
"""
import math
from collections import deque

AIR = "minecraft:air"
_PASS = {"air", "cave_air", "void_air", "light", "spirit_light", "short_grass", "grass", "tall_grass", "fern", "large_fern", "dead_bush",
         "vine", "ladder", "tripwire", "string", "redstone_wire", "lever", "cobweb", "water", "bubble_column", "seagrass",
         "tall_seagrass", "kelp", "kelp_plant", "sugar_cane", "glow_lichen", "lily_pad", "scaffolding", "twisting_vines",
         "twisting_vines_plant", "weeping_vines", "weeping_vines_plant", "cave_vines", "cave_vines_plant", "hanging_roots",
         "spore_blossom", "pink_petals", "nether_sprouts", "crimson_roots", "warped_roots", "structure_void", "fire",
         "soul_fire", "sweet_berry_bush", "wheat", "carrots", "potatoes", "beetroots", "nether_wart", "dandelion", "poppy",
         "blue_orchid", "allium", "azure_bluet", "oxeye_daisy", "cornflower", "lily_of_the_valley", "wither_rose",
         "sunflower", "lilac", "rose_bush", "peony", "torchflower", "brown_mushroom", "red_mushroom", "bamboo_sapling",
         "small_dripleaf", "moss_carpet", "tripwire_hook", "end_gateway", "nether_portal", "powder_snow"}
_PASS_END = ("_carpet", "_pressure_plate", "_button", "torch", "_sign", "_banner", "_door", "_fence_gate", "_trapdoor",
             "rail", "_sapling", "_tulip", "_coral_fan", "_coral", "_wall_fan")
_CLIMB = {"ladder", "vine", "scaffolding", "twisting_vines", "twisting_vines_plant", "weeping_vines", "weeping_vines_plant",
          "cave_vines", "cave_vines_plant", "water", "bubble_column"}
_LOW = {"flower_pot": 0.375, "candle": 0.375, "lantern": 0.5625, "soul_lantern": 0.5625, "campfire": 0.4375,
        "soul_campfire": 0.4375, "daylight_detector": 0.375, "chest": 0.875, "trapped_chest": 0.875, "ender_chest": 0.875,
        "enchanting_table": 0.75, "stonecutter": 0.5625, "cake": 0.5, "farmland": 0.9375, "dirt_path": 0.9375,
        "soul_sand": 0.875, "mud": 0.875, "sea_pickle": 0.375, "turtle_egg": 0.4375, "end_portal_frame": 0.8125,
        "lectern": 0.875, "repeater": 0.125, "comparator": 0.125, "conduit": 0.6875, "brewing_stand": 0.875}
BODY = 1.8          # how tall the player is
STEP = 0.6          # how high he walks up without jumping
JUMP = 1.0          # how high he gets onto by jumping (a block; not a fence)
EPS = 1e-6


def _name(s):
    return s.split("[", 1)[0].split(":")[-1]


def _props(s):
    if "[" not in s:
        return {}
    return dict(kv.split("=", 1) for kv in s[s.index("[") + 1:-1].split(","))


_SHAPES = {}


def shape(s):
    """(lo, hi, climbable) of a block state within its cell: None for lo when nothing there stops a player."""
    r = _SHAPES.get(s)
    if r is not None:
        return r
    n = _name(s)
    climb = n in _CLIMB
    if n in _PASS or n.endswith(_PASS_END) or n.endswith("wall_torch") or n.endswith("_wall_sign") or n.endswith("_wall_banner"):
        r = (None, None, climb)
    elif n == "snow":
        lay = int(_props(s).get("layers", 1))
        r = (None, None, False) if lay <= 1 else (0.0, (lay - 1) / 8.0, False)
    elif n.endswith("_slab"):
        t = _props(s).get("type", "bottom")
        r = (0.0, 0.5, False) if t == "bottom" else (0.5, 1.0, False) if t == "top" else (0.0, 1.0, False)
    elif n.endswith("_stairs"):                         # the low half for standing on; the full slab half for heads
        r = (0.0, 0.5, False) if _props(s).get("half", "bottom") == "bottom" else (0.5, 1.0, False)
    elif n.endswith("_fence") or (n.endswith("_wall") and not n.endswith("wall_banner")) or n.endswith("_wall_fence"):
        r = (0.0, 1.5, False)
    elif n.endswith("_bed"):
        r = (0.0, 0.5625, False)
    elif n.endswith("candle"):
        r = (0.0, 0.375, False)
    elif n.startswith("potted_"):
        r = (0.0, 0.375, False)
    elif n.endswith("_head") or n.endswith("_skull"):
        r = (0.0, 0.5, False)
    elif n in _LOW:
        r = (0.0, _LOW[n], False)
    else:
        r = (0.0, 1.0, False)
    _SHAPES[s] = r
    return r


class Grid:
    """The cells of a canvas as the stamp leaves them."""

    def __init__(self, blocks, cols, ground):
        self.blocks, self.ground = blocks, ground
        self.cols = cols or {}
        self.cache = {}

    def at(self, x, y, z):
        s = self.blocks.get((x, y, z))
        if s is not None:
            return s
        for (y0, y1, st) in reversed(self.cols.get((x, z), ())):
            if y0 <= y <= y1:
                return st
        return "minecraft:stone" if y <= self.ground else AIR

    def cell(self, x, y, z):
        k = (x, y, z)
        r = self.cache.get(k)
        if r is None:
            r = self.cache[k] = shape(self.at(x, y, z))
        return r

    def clear(self, x, z, a, b):
        """Nothing in column (x, z) between heights a and b (open interval) that stops a player."""
        if b <= a + EPS:
            return True
        for y in range(math.floor(a + EPS), math.ceil(b - EPS)):
            lo, hi, _ = self.cell(x, y, z)
            if lo is not None and y + hi > a + EPS and y + lo < b - EPS:
                return False
        return True


def stands(g, x, z, y0, y1):
    """The places to stand in column (x, z) with feet between y0 and y1: {s: climbable}."""
    out = {}
    for y in range(y0 - 1, y1 + 1):
        lo, hi, climb = g.cell(x, y, z)
        if lo is not None and hi is not None:
            s = y + hi
            if y0 <= s <= y1 + 1 and g.clear(x, z, s, s + BODY):
                out[s] = out.get(s, False) or g.cell(x, math.floor(s + EPS), z)[2]
        elif climb and g.clear(x, z, y, y + BODY):
            out[float(y)] = True
    return out


def reachable(blocks, cols, ground, margin=3, starts=None):
    """Every place a player gets to from the ground outside the canvas (or from `starts`, (x, z, s) places)."""
    g = Grid(blocks, cols, ground)
    keys = list(blocks) + [(x, 0, z) for (x, z) in (cols or {})]
    xs = [k[0] for k in keys]
    zs = [k[2] for k in keys]
    ys = [k[1] for k in blocks] + [y for runs in (cols or {}).values() for r in runs for y in (r[0], r[1])]
    x0, x1, z0, z1 = min(xs) - margin, max(xs) + margin, min(zs) - margin, max(zs) + margin
    y0, y1 = min(min(ys), ground) - 1, max(ys) + 2
    col = {}

    def places(x, z):
        k = (x, z)
        if k not in col:
            if x0 <= x <= x1 and z0 <= z <= z1:
                col[k] = stands(g, x, z, y0, y1)
            else:
                col[k] = {}
        return col[k]

    seen = set()
    todo = deque()
    if starts is None:
        starts = [(x, z, float(ground + 1)) for x in range(x0, x1 + 1) for z in (z0, z1)] + \
                 [(x, z, float(ground + 1)) for z in range(z0, z1 + 1) for x in (x0, x1)]
    for (x, z, s) in starts:
        if s in places(x, z) and (x, z, s) not in seen:
            seen.add((x, z, s))
            todo.append((x, z, s))
    while todo:
        x, z, s = todo.popleft()
        here = places(x, z)
        nxt = []
        if here.get(s):                                    # on a ladder: up and down it, off the top of it
            for s2 in here:
                if s < s2 <= s + 1 + EPS:
                    nxt.append((x, z, s2))
                elif s - 1 - EPS <= s2 < s and here[s2]:
                    nxt.append((x, z, s2))
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            for s2 in places(nx, nz):
                if s2 > s + JUMP + EPS:
                    continue
                if s2 > s:                                     # up: room over the start as high as the head gets
                    if not g.clear(x, z, s + BODY, s2 + BODY):
                        continue
                elif s2 < s and not g.clear(nx, nz, s2 + BODY, s + BODY):   # down: room to fall into
                    continue
                nxt.append((nx, nz, s2))
        for p in nxt:
            if p not in seen:
                seen.add(p)
                todo.append(p)
    return seen


def section(blocks, cols, ground, x0, x1, z0, z1, y0, y1, axis="x", path=()):
    """A text cut through the canvas (for looking at a stairwell): rows top down, one character a cell. Along x (z fixed
    to z0..z1, the first one with blocks) or along z. path: (x, z, s) places, marked '@' where the feet are."""
    g = Grid(blocks, cols, ground)
    feet = {(x, math.floor(s + EPS), z) for (x, z, s) in path}
    rows = []
    for y in range(y1, y0 - 1, -1):
        line = []
        cells = [(a, z0) for a in range(x0, x1 + 1)] if axis == "x" else [(x0, a) for a in range(z0, z1 + 1)]
        for (x, z) in cells:
            if (x, y, z) in feet:
                line.append("@")
                continue
            s = g.at(x, y, z)
            n = _name(s)
            lo, hi, climb = shape(s)
            if lo is None:
                line.append("H" if n == "ladder" else "D" if n.endswith("_door") else "t" if n.endswith("_trapdoor") else
                            "." if n in ("air", "cave_air", "light") else ",")
            elif n.endswith("_stairs"):
                f = _props(s).get("facing", "")
                line.append({"east": ">", "west": "<", "north": "^", "south": "v"}.get(f, "s") if _props(s).get("half") != "top" else "~")
            elif n.endswith("_slab"):
                line.append("_" if _props(s).get("type") == "bottom" else "-" if _props(s).get("type") == "top" else "#")
            elif hi > 1:
                line.append("|")
            else:
                line.append("#")
        rows.append("%4d %s" % (y, "".join(line)))
    return "\n".join(rows)
