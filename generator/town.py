"""凡人城镇 v2: a mortal Chinese town laid out by rule on the ground it stands on, written as one 单体建造 design (its rooms:
shops, houses, a temple, a yamen, walls, gates, a river with bridges, ornaments, and the ground itself as 台地 terraces
and 街道 street pieces, each at its own y), so it loads into the room table like 随机一院 and every piece stays editable.

Three sizes (`size`): a village (8-15 buildings, one lane, no wall), a town (25-40, a main street, side lanes and back
lanes, often a wall with gate houses), a big town (60-100, mostly a street grid round a 十字街 - `grid` - with a wall
and 城台 gate towers, a 衙门 and a temple in double blocks).
The street plan (`layout`) varies:
- line: one main street, gently curving, side lanes off it, back lanes along the plot backs;
- T: the main street runs up to a cross street, the temple across the head of the T;
- cross: a 十字街, the main street crossed near the middle;
- Y: the main street forks at a little square, a tree in the fork;
- bank: a river through the town, a street along each bank, bridges across;
- grid (a big town): the 十字街 and two streets parallel to each side of the main street, streets across between them,
  blocks (坊) between - the 衙门 north of the cross street, the temple south of it, each in a block two deep;
and some towns have a river across the main street (a bridge where it crosses, houses backing onto the water).

Every street is cut into short straight pieces at their own level (one block up or down from the next; a lane between
two streets at different levels climbs in steps), each a 街道 room; a curve is a piece shifted a cell across. Along
each street the parcels: a parcel stands on a 台地 at the street's level in front of it, its building(s) facing the
street - rows of shops wall to wall (SHOP_USES on the ground floor) with a yard and a house behind, houses, huts, an
inn, a courtyard house or a temple (siteplan compounds), a market square with a well and stalls. What goes where
depends on how far from the middle of the town it is (rich near the temple, the yamen and the square: tile roofs, two
storeys, courtyards; poor at the edges: thatch, mud and wattle, smaller), and the back yards get vegetable plots,
haystacks, wells, woodpiles and trees. A parcel never takes ground another has claimed (streets, the river, the wall,
other parcels), and every building keeps what it reaches out to (eaves, steps, galleries) inside its parcel (the front
eaves over at most half the street), so no two buildings overlap; the alleys between parcels lead to the yards.

One material family per town (walls, tiles, base, street and yard surfaces, roof forms), shifted by the district.

The ground between the buildings is laid by compose.terrain_ground (surfaces, retaining walls, steps where levels
meet); the daemon clears every column above that ground and fills under it.

A box (`box`: W x D in blocks, x by z) sizes the town to it instead: the main street about as long as the box is deep,
its side lanes by its length, its parcels as deep as the box is wide; a box wider (or deeper) than the size's own plan
(BASE) is laid out in blocks (坊) - streets parallel to the main one PITCH apart and streets across between them PITCH
apart, as many as the box holds, whatever the street plan (a line's main street curving through the blocks, a 十字街
the grid's, a T's cross street across the head of them, a Y's blocks along its stem and out from its branches, a river
town's east of the river) - the wall round it all. No box: the size's own plan, as before.

town(seed, heights=None, size=None, layout=None, box=None) -> design dict. heights: None (rolling ground made from the seed), a
function (x, z) -> y in the design's coordinates, or {"R": R, "h": [...]} - the ground of the (2R+1) x (2R+1) columns
round the design's origin, row by row (z from -R), as the game samples it; the design then carries world heights
("anchored"). The town is laid out round the origin, its entrance at +z (towards the player).
python town.py SEED [out.json|-] [village|town|big|-] [line|T|cross|Y|bank|grid|-] [WxD]
"""
import json
import math
import random
import sys

import siteplan
from compose import SHOP_USES
from siteplan import odd, room

FAMILIES = [   # walls (main, others), tiles, roof forms (weights), base, street, yards, 马头墙 share, cn; poor / rich districts
    dict(cn="江南", walls=("whitewall", "darkwood"), themes=("black", "grey"), roofs={"yingshan": 3, "xuanshan": 2, "xieshan": 1},
         base="greybrick", street=(0, 0, 1), yard=(0, 3, 1), horse=0.35,
         poor=dict(walls=("wattle", "darkwood", "mud"), themes=("thatch", "thatch", "wood")), rich=dict(walls=("whitewall",), themes=("black",))),
    dict(cn="徽州", walls=("whitewall", "whitewall", "greybrick"), themes=("black",), roofs={"yingshan": 5, "xieshan": 1},
         base="white", street=(0, 1), yard=(3, 0), horse=0.8,
         poor=dict(walls=("wattle", "mud", "whitewall"), themes=("thatch", "grey")), rich=dict(walls=("whitewall",), themes=("black",))),
    dict(cn="北方", walls=("greybrick", "redbrick"), themes=("grey", "grey", "black"), roofs={"yingshan": 3, "juanpeng": 2, "xuanshan": 1},
         base="greybrick", street=(2, 0, 3), yard=(0, 3), horse=0.0,
         poor=dict(walls=("mud", "mud", "redbrick"), themes=("thatch", "mud")), rich=dict(walls=("greybrick",), themes=("grey", "black"))),
    dict(cn="山村", walls=("stone", "log", "wattle"), themes=("slate", "thatch", "wood"), roofs={"xuanshan": 3, "yingshan": 2},
         base="rubble", street=(1, 3), yard=(1, 2, 0), horse=0.0,
         poor=dict(walls=("log", "wattle", "mud"), themes=("thatch",)), rich=dict(walls=("stone", "darkwood"), themes=("slate", "grey"))),
    dict(cn="黄土", walls=("mud", "redbrick", "mud"), themes=("mud", "grey"), roofs={"yingshan": 3, "juanpeng": 2},
         base="rammed", street=(3, 2), yard=(0,), horse=0.0,
         poor=dict(walls=("mud",), themes=("mud", "thatch")), rich=dict(walls=("redbrick", "greybrick"), themes=("grey",))),
    dict(cn="川西", walls=("darkwood", "wattle", "whitewall"), themes=("grey", "black"), roofs={"xuanshan": 3, "xieshan": 2, "yingshan": 1},
         base="rubble", street=(0, 1), yard=(1, 0, 2), horse=0.1,
         poor=dict(walls=("wattle", "darkwood", "log"), themes=("thatch", "wood")), rich=dict(walls=("whitewall", "darkwood"), themes=("black", "grey"))),
    dict(cn="富庶", walls=("redbrick", "darkwood", "greybrick"), themes=("grey", "black"), roofs={"xieshan": 3, "yingshan": 2, "xuanshan": 1},
         base="greybrick", street=(0, 2), yard=(3, 2), horse=0.2, landmark=("teal", "grey"),   # (glazed tiles on the temple, the inn)
         poor=dict(walls=("wattle", "mud", "darkwood"), themes=("thatch", "grey")), rich=dict(walls=("redbrick", "greybrick"), themes=("black", "grey"))),
]
SHOPS = ("teahouse", "pharmacy", "general_store", "general_store", "smithy", "tofu", "cloth_shop", "pawnshop", "bookshop",
         "clinic", "coffin_shop", "gambling_den", "inn_hall")
RARE_SHOPS = ("pill_shop", "talisman_shop", "material_shop")       # (a mortal town on a cultivator's road)
HOMES = ("main_room", "main_room", "bedroom", "kitchen", "study")
POOR_HOMES = ("main_room", "main_room", "bedroom", "kitchen", "storeroom")
WORKS = ("mill_room", "dyeing", "brewery", "weaving", "pottery", "granary", "storeroom")
UPPER = {"teahouse": "private_dining", "inn_hall": "guest_room", "gambling_den": "private_dining"}
DIRS = ("north", "east", "south", "west")
GROUP = True       # 地块: the design's ground pieces gathered into a few rooms (compose.group_ground)
ORN = dict(ding=0, sundial=1, measure=2, vat=3, well=4, stall=5, bamboo=6, hay=7, tree=8, veg=9, woodpile=10, lion=11,
           flowers=12, pond=13, rack=14, lamp=15)   # 陈设 by variant (bgen.special_shape)

# size classes: main street length and width, side lanes, back lane share, wall share, river share
CLASSES = {
    "village": dict(L=(72, 100), W=(3, 5, 5), lanes=(0, 2), back=0.0, wall=0.0, river=0.35, most=15),
    "town": dict(L=(138, 170), W=(5, 7, 7), lanes=(2, 4), back=0.6, wall=0.65, river=0.4, most=40),
    "big": dict(L=(176, 200), W=(7,), lanes=(4, 7), back=1.0, wall=1.0, river=0.4, most=100),
}
LAYOUTS = {
    "village": {"line": 4, "T": 2, "Y": 2, "cross": 1, "bank": 2},
    "town": {"line": 2, "T": 3, "cross": 2, "Y": 2, "bank": 2},
    "big": {"grid": 7, "T": 1, "bank": 1},
}
BASE = {"village": (95, 95), "town": (165, 200), "big": (270, 245)}   # (about what a town of the size covers, W x D)
PITCH = {"village": (48, 56), "town": (42, 48), "big": (38, 42)}     # (a town in blocks: its streets this far apart)
KIND_ORDER = {"main": 0, "cross": 1, "branch": 1, "bank": 1, "lane": 2, "back": 3}


# ------------------------------------------------------------------------------------------------ ground

def _synth(rng):
    """Rolling ground: a few long waves, a tilt, a few blocks of relief over the town."""
    relief = rng.uniform(3.0, 9.0)
    waves = []
    for _ in range(3):
        a = rng.uniform(0, math.pi)
        waves.append((math.cos(a), math.sin(a), rng.uniform(70, 220), rng.uniform(0, 2 * math.pi), rng.uniform(0.5, 1.0)))
    tot = sum(w[4] for w in waves)
    tilt = rng.uniform(-0.025, 0.025)
    lean = rng.uniform(-0.06, 0.06)

    def h(x, z):
        v = sum(w * math.sin(2 * math.pi * (x * cx + z * cz) / lam + ph) for cx, cz, lam, ph, w in waves) / tot
        return relief / 2 * v + tilt * z + lean * x
    return h


def _given(heights):
    """The game's samples (world y round the origin) as a function of the design's (x, z), clamped at the edge."""
    if callable(heights):
        return heights
    step = int(heights.get("step", 1))                  # (a huge town: every step-th column sampled)
    R = int(heights["R"]) // step
    n = 2 * R + 1
    raw = list(heights["h"])
    good = sorted(v for v in raw if v is not None and v > -9000)
    fill = good[len(good) // 2] if good else 64
    vals = [v if v is not None and v > -9000 else fill for v in raw]

    def h(x, z):
        i = max(-R, min(R, int(round(x / step)))) + R
        k = max(-R, min(R, int(round(z / step)))) + R
        return vals[k * n + i]
    return h


# ------------------------------------------------------------------------------------------------ buildings

def margins(r, spare=1):
    """How far a building reaches past its frame (side, front, back): its eaves (measured with 出檐 1: a gable roof's
    end three out, the rest two, a cell more from two storeys up), its plinth ring, its front steps (a step a block of
    台基, and one), a 前廊 or 后廊 four more, a 城台 three all round. `spare` more round each (planning: the eaves of two
    buildings never meet)."""
    st = int(r.get("storeys", 1))
    eave = 2 + (1 if st >= 2 else 0) + (1 if r.get("double_eave") else 0)
    side = max(eave, 3 if r.get("roof") in ("yingshan", "xuanshan") else 2) + (1 if r.get("horsehead") else 0)
    pod = int(r.get("podium", 0) or 0)
    steps = pod + 1 + (2 if r.get("podium_style") == "xumizuo" and pod else 0)
    front = max(eave, steps) + (4 if r.get("veranda") else 0)
    back = eave + (4 if r.get("back_veranda") else 0)
    if r.get("podium_style") == "chengtai" and pod:
        side, front, back = max(side, 4), max(eave, 4), max(back, 4)
    return side + spare, front + spare, back + spare


def reach_rect(r, spare=0):
    """The world rectangle a building reaches over: its frame and its margins on the sides they are on."""
    sm, fm, bm = margins(r, spare)
    fr = r.get("front", "south")
    x0, z0, x1, z1 = r["x0"], r["z0"], r["x1"], r["z1"]
    if fr == "south":
        return (x0 - sm, z0 - bm, x1 + sm, z1 + fm)
    if fr == "north":
        return (x0 - sm, z0 - fm, x1 + sm, z1 + bm)
    if fr == "east":
        return (x0 - bm, z0 - sm, x1 + fm, z1 + sm)
    return (x0 - fm, z0 - sm, x1 + bm, z1 + sm)


def front_gap(r, hw):
    """How far back from the street a building's front wall stands: its eaves may reach over half the street (the
    other side's eaves over the other half), its steps (in front of a 前廊) and a cell of ground stay off it."""
    _, f, _ = margins(r, 0)
    pod = int(r.get("podium", 0) or 0)
    steps = pod + 1 + (2 if r.get("podium_style") == "xumizuo" and pod else 0) + (4 if r.get("veranda") else 0)
    return max(f - hw, steps + 1)


def _hits(a, b):
    return a[0] <= b[2] and b[0] <= a[2] and a[1] <= b[3] and b[1] <= a[3]


def wall_to_wall(a, b):
    """Two frames side by side (touching along a side, or one row apart): one building to compose."""
    gx = max(a["x0"], b["x0"]) - min(a["x1"], b["x1"])
    gz = max(a["z0"], b["z0"]) - min(a["z1"], b["z1"])
    return gx <= 1 and gz <= 1


def resolve(rooms):
    """No two buildings reach into each other (eaves, steps, galleries - not counting buildings wall to wall, which
    compose joins): the lesser one gives way (lower "_rank": a side wing before a back house before a shop before a
    main hall; else the smaller) - its frame shrinks away from the other (staying odd and at least 5 across), or it
    goes. Returns how many were shrunk and dropped."""
    shrunk = dropped = 0
    for _ in range(4):
        bl = [r for r in rooms if not r.get("special")]
        rr = [reach_rect(r) for r in bl]
        changed = False
        for i, a in enumerate(bl):
            for j in range(i + 1, len(bl)):
                b = bl[j]
                if not _hits(rr[i], rr[j]) or a not in rooms or b not in rooms or wall_to_wall(a, b):
                    continue
                if not _hits(reach_rect(a), reach_rect(b)):
                    continue

                def keep(r):                                  # (a main hall stays, a side wing gives way first)
                    return (r.get("_rank", 1), (r["x1"] - r["x0"]) * (r["z1"] - r["z0"]))
                small, big = (a, b) if keep(a) <= keep(b) else (b, a)
                rs, rbg = reach_rect(small), reach_rect(big)
                need = {"x0": rbg[2] - rs[0] + 1, "x1": rs[2] - rbg[0] + 1, "z0": rbg[3] - rs[1] + 1, "z1": rs[3] - rbg[1] + 1}
                for side in sorted(need, key=lambda k: need[k]):   # the least cut that leaves it 5 across
                    n = need[side] + need[side] % 2              # (stays odd)
                    lo, hi = ("x0", "x1") if side[0] == "x" else ("z0", "z1")
                    if n > 0 and small[hi] - small[lo] + 1 - n >= 5 and not small.get("_gate"):
                        small[side] += n if side.endswith("0") else -n
                        shrunk += 1
                        break
                else:
                    rooms.remove(small)
                    dropped += 1
                changed = True
        if not changed:
            break
    return shrunk, dropped


def turn(d, k):
    return DIRS[(DIRS.index(d) + k) % 4]


def rot_rect(r, k):
    """A rectangle (x0, z0, x1, z1) turned k quarter turns clockwise (north to east) about the origin."""
    pts = [(r[0], r[1]), (r[2], r[3])]
    for _ in range(k % 4):
        pts = [(-z, x) for x, z in pts]
    xs, zs = [p[0] for p in pts], [p[1] for p in pts]
    return (min(xs), min(zs), max(xs), max(zs))


# ------------------------------------------------------------------------------------------------ streets

class Street:
    """A street, a lane or a river along one axis ("x" or "z"): short straight pieces, each from a0 to a1 along the
    axis, centred across at c, w wide, at its own level y. cfun(a): the centre line it follows (a piece is shifted at
    most `shift` across from the one before). `fixed`: spans (a0, a1) that stay one piece (where another street joins:
    its whole width faces one level); `holes`: spans left out (where a river crosses)."""

    def __init__(self, axis, lo, hi, cfun, w, kind, fixed=(), holes=(), minlen=3, maxlen=9, shift=1):
        self.axis, self.lo, self.hi, self.cfun = axis, lo, hi, cfun
        self.w, self.hw, self.kind = w, w // 2, kind
        self.step = 1
        self.pieces = self._cut(fixed, holes, minlen, maxlen, shift)

    def _cut(self, fixed, holes, minlen, maxlen, shift):
        marks = sorted([(max(self.lo, a), min(self.hi, b), False) for a, b in fixed if b >= self.lo and a <= self.hi] +
                       [(max(self.lo, a), min(self.hi, b), True) for a, b in holes if b >= self.lo and a <= self.hi])
        clean = []
        for m in marks:                                    # (overlapping marks: the later one starts after)
            if clean and m[0] <= clean[-1][1]:
                if m[1] <= clean[-1][1]:
                    continue
                m = (clean[-1][1] + 1, m[1], m[2])
            clean.append(m)
        out, a, prev = [], self.lo, None

        def target(t):
            return int(round(self.cfun(t)))
        for s0, s1, hole in clean + [(self.hi + 1, self.hi + 1, False)]:
            while a <= s0 - 1:
                c = target(a + 1)
                if prev is not None:
                    c = max(prev - shift, min(prev + shift, c))
                b = min(s0 - 1, a + minlen - 1)
                while b + 1 <= s0 - 1 and b - a + 1 < maxlen and target(b + 1) == c:
                    b += 1
                if 0 < s0 - 1 - b < minlen:
                    b = s0 - 1
                out.append(dict(a0=a, a1=b, c=c, y=0))
                prev, a = c, b + 1
            if s0 > self.hi:
                break
            c = target((s0 + s1) / 2)
            if prev is not None:
                c = max(prev - shift, min(prev + shift, c))
            if not hole:
                out.append(dict(a0=s0, a1=s1, c=c, y=0, fixed=True))
            prev, a = c, s1 + 1
        return out

    def rect(self, p, extra=0):
        h = self.hw + extra
        if self.axis == "z":
            return (p["c"] - h, p["a0"], p["c"] + h, p["a1"])
        return (p["a0"], p["c"] - h, p["a1"], p["c"] + h)

    def span(self, a0, a1):
        return [p for p in self.pieces if p["a1"] >= a0 and p["a0"] <= a1]

    def piece_at(self, a):
        for p in self.pieces:
            if p["a0"] <= a <= p["a1"]:
                return p
        return min(self.pieces, key=lambda p: min(abs(p["a0"] - a), abs(p["a1"] - a)))

    def xz(self, a, c):
        return (c, a) if self.axis == "z" else (a, c)


_FIELD = type("Field", (), {"axis": "x", "hw": 0, "kind": "field"})()   # (a field inside the wall: a yard with no street)


class Side:
    """One side of a street (-1: the low side across, north of a street along x, west of one along z; +1: the other)
    as parcels see it: u along the street, d the depth from the first row clear of the street."""

    def __init__(self, st, side):
        self.st, self.side = st, side
        self.facing = {("x", -1): "south", ("x", 1): "north", ("z", -1): "east", ("z", 1): "west"}[(st.axis, side)]

    def lines(self, u0, u1):
        """The front line (the first row clear of the street all along u0..u1) and how near the ground reaches to the
        street (to its edge where it bends away)."""
        ss = self.st.span(u0, u1) or [self.st.piece_at(u0)]
        hw = self.st.hw
        if self.side < 0:
            return min(p["c"] for p in ss) - hw - 1, max(p["c"] for p in ss) - hw - 1
        return max(p["c"] for p in ss) + hw + 1, min(p["c"] for p in ss) + hw + 1

    def rect(self, u0, u1, F, d0, d1):
        a, b = F + self.side * d0, F + self.side * d1
        lo, hi = min(a, b), max(a, b)
        if self.st.axis == "x":
            return (min(u0, u1), lo, max(u0, u1), hi)
        return (lo, min(u0, u1), hi, max(u0, u1))

    def near_rect(self, u0, u1, near, F, d1):
        """From the street's edge (near) to depth d1."""
        a, b = near, F + self.side * d1
        lo, hi = min(a, b), max(a, b)
        if self.st.axis == "x":
            return (u0, lo, u1, hi)
        return (lo, u0, hi, u1)

    def y(self, u0, u1):
        return self.st.piece_at((u0 + u1) // 2)["y"]

    def level_ok(self, u0, u1, y):
        return all(abs(p["y"] - y) <= 1 for p in self.st.span(u0, u1))

    def xz(self, u, F, d):
        a = F + self.side * d
        return (u, a) if self.st.axis == "x" else (a, u)

    def ends(self):
        """Along u: the low end's and the high end's facing (into the parcel from either end)."""
        return ("east", "west") if self.st.axis == "x" else ("south", "north")


# ------------------------------------------------------------------------------------------------ the town

class Town:
    def __init__(self, seed, heights=None, size=None, layout=None, attempt=0, box=None):
        self.seed = seed
        rng = self.rng = random.Random(f"town/{seed}" if not attempt else f"town/{seed}/{attempt}")
        self.box = box
        if box and not size:                                # (a box and no size: the size the box is)
            side = math.sqrt(box[0] * box[1])
            size = "village" if side < 120 else "town" if side < 220 else "big"
        self.size = size or rng.choices(("village", "town", "big"), weights=(30, 45, 25))[0]
        self.C = CLASSES[self.size]
        lay = LAYOUTS[self.size]
        self.layout = layout or rng.choices(list(lay), weights=list(lay.values()))[0]
        self.walled = rng.random() < self.C["wall"]
        fam = self.fam = dict(rng.choice(FAMILIES))
        import compose
        bays = [b[0] for b in compose.options()["bay_styles"]]
        self.mat = dict(wall=fam["walls"][0], walls=fam["walls"], theme=rng.choice(fam["themes"]),
                        base=fam["base"] if rng.random() < 0.8 else rng.choice(("greybrick", "rubble", "auto")),
                        street=rng.choice(fam["street"]), yard=None,
                        bay=rng.choice(bays), tall=rng.uniform(0.25, 0.7), veranda=rng.uniform(0.0, 0.4),
                        podium=rng.choice((0, 0, 1)), sh=rng.choice((0, 0, 5, 6)), rare=rng.random() < 0.3)
        clash = {(0, 2), (1, 2), (2, 3), (3, 0)}             # (yards that would look like the street: not those)
        yards = [v for v in fam["yard"] if (self.mat["street"], v) not in clash] or [1]
        self.mat["yard"] = rng.choice(yards)
        if rng.random() < 0.2:                              # (now and then a town of another tile)
            self.mat["theme"] = rng.choice(("grey", "black", "slate", "wood", "thatch", "mud"))
        self.mat["lane"] = 3 if self.mat["street"] in (0, 2) and rng.random() < 0.4 else self.mat["street"]
        if heights is None:
            self.anchored = False
            self.H = _synth(rng)
        else:
            self.anchored = True
            self.H = _given(heights)
        self.rooms = []
        self.buildings = []
        self.claim = {}                                     # (x, z) -> what took it: "street", "river", "wall", "plot"
        self.streets = []
        self.rivers = []
        self.river_fix = {}
        self.later = []                                     # lanes and back lanes: laid once the landmarks have their ground
        self.centre = (0, 0)
        self.radius = 80
        self.yards = []                                     # back yards and gardens to furnish at the end
        self.W = 5
        self.gridded = self.size == "big"                   # (in blocks: streets parallel to the main one)
        self.dmax = {"village": 64, "town": 44, "big": 28}[self.size]   # (how deep a parcel goes from its street)
        self.k, self.G, self.outer, self.xr, self.pad = 2, None, None, 0, 0
        self.least = LEAST[self.size]
        if box:
            self.fit_box(*box)

    def fit_box(self, bw, bd):
        """The town sized to a box bw x bd (x by z): the main street as long as the box is deep less the wall and the
        ends; in blocks (gridded) where the box is wider or deeper than the size's own plan - k streets a side PITCH
        apart, lanes out past the outer ones as far as the box goes; else the parcels as deep as the box is wide, side
        lanes by the street's length, no back lanes in a narrow box. How many buildings it may (and must) have goes by
        its area."""
        rng, C = self.rng, dict(self.C)
        pad = self.pad = 17 if self.walled else 4          # (a wall: 12 out from the town, its own 5)
        BW, BD = BASE[self.size]
        self.gridded = self.size == "big" or bw > BW * 1.2 or bd > BD * 1.4
        L = odd(max(41, bd - 2 * pad - 12 - {"T": 50, "bank": 24}.get(self.layout, 0)))   # (a T: the temple across its
        # head; a river town: the river on past the ends)
        Lb = sum(C["L"]) / 2
        C["L"] = (L, L)
        area = bw * bd / float(BW * BD)
        C["most"] = max(C["most"], int(C["most"] * area * 3))
        self.least = int(LEAST[self.size] * min(1.0, area * 0.7))
        self.xr = int(bw // 2 - pad)                       # (a river across the town: the whole width)
        if self.gridded:
            G = self.G = rng.randint(*PITCH[self.size])
            self.k = max(1, int((bw / 2 - pad - 22) // G))
            self.outer = int(bw / 2 - pad - self.k * G - 6)
            self.dmax = 28
        else:
            C["lanes"] = tuple(max(0, round(n * L / Lb)) for n in C["lanes"])
            self.dmax = max(14, min(self.dmax, int(bw / 2 - pad - 6)))
            if bw < BW * 0.8:
                C["back"] = 0.0
        self.C = C

    def offs(self, G):
        """The streets along the main one, G apart either side: two a side, or as many as the box holds."""
        return [-G * j for j in range(self.k, 0, -1)] + [G * j for j in range(1, self.k + 1)]

    def cross_zs(self, lo, hi, G, zc=None, edge=14):
        """Where streets cross between the streets along z: G either side of zc (two each way, or as many as the main
        street holds), clear of its ends."""
        zc = (lo + hi) // 2 if zc is None else zc
        K = 2 if not self.box else (hi - lo) // G + 1
        return [zc + j * G for j in range(-K, K + 1) if j and lo + edge <= zc + j * G <= hi - edge]

    @staticmethod
    def jointed(st, a):
        """The street was cut with a joint at a (one piece over it at one level): a street across may leave it there."""
        return any(p.get("fixed") and p["a0"] <= a <= p["a1"] for p in st.pieces)

    # -------------------------------------------------------------------------------------------- claims

    def free(self, rect):
        x0, z0, x1, z1 = rect
        cl = self.claim
        return not any((x, z) in cl for x in range(x0, x1 + 1) for z in range(z0, z1 + 1))

    @staticmethod
    def cells(rect):
        return [(x, z) for x in range(rect[0], rect[2] + 1) for z in range(rect[1], rect[3] + 1)]

    def take(self, rect, tag="plot"):
        x0, z0, x1, z1 = rect
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                self.claim.setdefault((x, z), tag)

    def depth_free(self, s, u0, u1, F, dmax):
        """How deep a parcel over u0..u1 can go from its front line before it meets claimed ground (-1: not at all)."""
        cl = self.claim
        for d in range(dmax + 1):
            for u in range(u0, u1 + 1):
                if s.xz(u, F, d) in cl:
                    return d - 1
        return dmax

    def run_free(self, s, u0, umax, rows=3):
        """How far along from u0 the frontage is free (rows deep), up to umax."""
        cl = self.claim
        u = u0
        while u <= umax:
            F, _ = s.lines(u, u)
            if any(s.xz(u, F, d) in cl for d in range(rows)):
                break
            u += 1
        return u - 1

    # -------------------------------------------------------------------------------------------- districts

    def wealth(self, x, z):
        """1 in the middle of the town (the temple, the yamen, the square), 0 at the edges; a little noise."""
        cx, cz = self.centre
        d = math.hypot(x - cx, z - cz) / max(30.0, self.radius)
        n = ((int(x) * 7349 + int(z) * 9157 + self.seed * 31) % 100) / 100.0 - 0.5
        return max(0.0, min(1.0, 1.15 - 1.1 * d + 0.25 * n))

    def ward(self, w):
        return "rich" if w > 0.66 else "poor" if w < 0.3 else "mid"

    # -------------------------------------------------------------------------------------------- pieces

    def pick_roof(self, big=False):
        roofs = self.fam["roofs"]
        if big and self.rng.random() < 0.6:
            return self.rng.choice(("xieshan", "xieshan", "wudian")) if "xieshan" in roofs or self.rng.random() < 0.5 else "xieshan"
        keys = list(roofs)
        return self.rng.choices(keys, weights=[roofs[k] for k in keys])[0]

    def building(self, use, storeys=1, roof=None, upper=None, wealth=0.5, **kw):
        """A building's settings (no frame yet): the town's materials, shifted by the district, a little variation."""
        rng, mat = self.rng, self.mat
        ward = self.ward(wealth)
        roof = roof or self.pick_roof()
        walls = mat["wall"] if rng.random() < 0.7 else rng.choice(mat["walls"])
        theme = mat["theme"]
        podium = mat["podium"]
        if ward == "poor":
            walls = rng.choice(self.fam["poor"]["walls"])
            theme = rng.choice(self.fam["poor"]["themes"])
            podium = 0
            if roof in ("xieshan", "wudian"):
                roof = "xuanshan"
        elif ward == "rich" and rng.random() < 0.7:
            walls = rng.choice(self.fam["rich"]["walls"])
            theme = rng.choice(self.fam["rich"]["themes"])
        horse = roof == "yingshan" and walls in ("whitewall", "greybrick") and rng.random() < self.fam["horse"] and ward != "poor"             and use != "kitchen"                           # (a kitchen's door in its gable end: the 马头墙 hung over it)
        upper = upper or UPPER.get(use) or rng.choice(("bedroom", "bedroom", "storeroom", "study"))
        if walls == "stone" and use in SHOP_USES:           # (石墙's shop counter: its awning hangs at the opening's head)
            walls = next((w for w in mat["walls"] if w != "stone"), "log")
        d = dict(storeys=storeys, roof=roof, walls=walls,
                 theme=theme, bay_style=mat["bay"], base=mat["base"], sh=mat["sh"], podium=podium,
                 podium_style="plain", open="closed", uses=[use] + [upper] * 7, horsehead=horse, overhang=1,
                 pitch=rng.choice((0, 0, 2, 3)), lift=rng.choice((0, 0, 1, 2)) if roof in ("xieshan", "wudian") else 0,
                 veranda=False, back_veranda=False)
        d.update(kw)
        if ward == "poor":
            d.update(storeys=1, veranda=False, horsehead=False)
        return d

    def place(self, d, rect, y, front, rank=2):
        x0, z0, x1, z1 = rect
        r = room(x0, z0, x1, z1, self.rng, front=front, y=y, _rank=rank, **d)
        self.rooms.append(r)
        self.buildings.append(r)
        return r

    def ground(self, kind, rect, y, variant=0, **kw):
        x0, z0, x1, z1 = rect
        r = room(min(x0, x1), min(z0, z1), max(x0, x1), max(z0, z1), self.rng, special=kind, y=y, variant=variant,
                 walls=kw.pop("walls", self.mat["wall"]), theme=kw.pop("theme", self.mat["theme"]), base=kw.pop("base", self.mat["base"]),
                 front=kw.pop("front", "south"), **kw)
        self.rooms.append(r)
        return r

    def ornament(self, kind, cx, cz, y, half=1, front="south", **kw):
        return self.ground("ornament", (cx - half, cz - half, cx + half, cz + half), y, variant=ORN[kind], front=front, **kw)

    def terrace(self, s, u0, u1, F, near, d0, d1, y, variant=None):
        """A parcel's ground from depth d0 to d1 (d0 = None: from the street's edge)."""
        rect = s.near_rect(u0, u1, near, F, d1) if d0 is None else s.rect(u0, u1, F, d0, d1)
        return self.ground("terrace", rect, y, variant=self.mat["yard"] if variant is None else variant)

    # -------------------------------------------------------------------------------------------- streets

    def targets(self, st):
        """The ground under each piece (the mean over it and a little round it), smoothed along the street."""
        raw = []
        for p in st.pieces:
            vals = []
            for a in range(p["a0"] - 2, p["a1"] + 3, 2):
                for c in range(p["c"] - st.hw - 3, p["c"] + st.hw + 4, 3):
                    vals.append(self.H(*st.xz(a, c)))
            raw.append(sum(vals) / len(vals))
        return [sum(raw[max(0, i - 1):i + 2]) / len(raw[max(0, i - 1):i + 2]) for i in range(len(raw))]

    def grade(self, st, fixed=None, floor=None, bounds=None):
        """Each piece's level: the ground under it, a block up or down from the one before (more where two fixed levels
        are too far apart for that: a lane climbing between two streets), and only where the ground has clearly gone
        up or down. fixed: {piece index: y}; floor: no piece lower; bounds: [(lo, hi)] a piece's level (a street on a
        river bank: from the water's bank up two)."""
        fixed = fixed or {}
        n = len(st.pieces)
        t = self.targets(st)
        steep = len(fixed) <= 1 and st.kind != "river" and all(p["a1"] - p["a0"] >= 2 for p in st.pieces)
        s = 1
        ks = sorted(fixed)
        for i, j in zip(ks, ks[1:]):
            s = max(s, math.ceil(abs(fixed[j] - fixed[i]) / max(1, j - i)))
        st.step = s
        lo_b, hi_b = [-1 << 20] * n, [1 << 20] * n
        sb = 2 if steep else s
        for i, y in fixed.items():
            for j in range(n):
                lo_b[j] = max(lo_b[j], y - abs(j - i) * sb)
                hi_b[j] = min(hi_b[j], y + abs(j - i) * sb)
        ys = []
        for i in range(n):
            if i in fixed:
                y = fixed[i]
            elif not ys:
                y = round(t[i])
            else:
                prev = ys[-1]
                if s == 1 and abs(t[i] - prev) > 2.5 and st.w >= 3 and steep:
                    y = prev + (2 if t[i] > prev else -2)     # (the ground climbs faster than a step a piece: a
                elif s == 1:                                  # flight of steps up the street - a 梯街)
                    y = prev + (1 if t[i] > prev + 0.75 else -1 if t[i] < prev - 0.75 else 0)
                else:
                    y = prev + max(-s, min(s, round(t[i] - prev)))
            if floor is not None:
                y = max(floor, y)
            y = max(lo_b[i], min(hi_b[i], y))
            if ys:
                y = max(ys[-1] - max(s, 2 if steep else 1), min(ys[-1] + max(s, 2 if steep else 1), y))
            if bounds is not None and i not in fixed:
                y = max(bounds[i][0], min(bounds[i][1], y))
            ys.append(int(y))
        for p, y in zip(st.pieces, ys):
            p["y"] = y
        return st

    def climb(self, st):
        t = self.targets(st)
        return sum(abs(t[i] - t[i - 1]) for i in range(1, len(t)))

    def curvy(self, axis, lo, hi, w, kind, joints=(), holes=(), pin=None, amp=(1.5, 8.0), base=0, tries=10):
        """A street from lo to hi along the axis, gently curving round `base` across (a sine): of a few, the one that
        climbs least over the ground. joints: [(a, width)] where other streets join (kept one piece); pin: (a, c) the
        centre there."""
        rng = self.rng
        L = hi - lo + 1
        fixed = [(a - wd // 2 - 1, a + wd // 2 + 1) for a, wd in joints]
        best = None
        for _ in range(tries):
            A, P, ph = rng.uniform(*amp), rng.uniform(0.7, 1.7) * L, rng.uniform(0, 2 * math.pi)

            def f(a, A=A, P=P, ph=ph):
                return base + A * math.sin(2 * math.pi * (a - lo) / P + ph)
            g = f
            if pin is not None:
                off = pin[1] - f(pin[0])
                g = (lambda a, f=f, off=off: f(a) + off)
            st = Street(axis, lo, hi, g, w, kind, fixed=fixed, holes=holes)
            c = self.climb(st)
            if best is None or c < best[0]:
                best = (c, st)
        return best[1]

    def add_street(self, st):
        self.streets.append(st)
        for p in st.pieces:
            self.take(st.rect(p), "street")
        return st

    def free_street(self, st, extra=0):
        return all(self.free(st.rect(p, extra)) for p in st.pieces)

    def child(self, parent, aj, side, kind="lane", w=3, length=24, to=None, wobble=0.0):
        """A street off another one, across it, from its edge at aj (its joint) out to `side`, `length` long or on to
        the street `to` (its far end joins that one); its first piece at the level of the joint."""
        p = parent.piece_at(aj)
        axis = "x" if parent.axis == "z" else "z"
        start = p["c"] + side * (parent.hw + 1)
        q = None
        if to is not None:
            q = to.piece_at(aj)
            end = q["c"] - side * (to.hw + 1)
        else:
            end = start + side * (length - 1)
        lo, hi = sorted((start, end))
        if hi - lo < 4:
            return None
        rng = self.rng
        if wobble:
            A, P, ph = rng.uniform(0, wobble), rng.uniform(30, 70), rng.uniform(0, 2 * math.pi)
            f0 = A * math.sin(2 * math.pi * (start - lo) / P + ph)
            cf = (lambda a, A=A, P=P, ph=ph, f0=f0: aj + A * math.sin(2 * math.pi * (a - lo) / P + ph) - f0)
        else:
            cf = (lambda a, c=aj: c)
        st = Street(axis, lo, hi, cf, w, kind, maxlen=5 if kind == "lane" else 9)
        if not st.pieces:
            return None
        if not all(self.free(st.rect(pp)) for pp in st.pieces):
            # (something is in the way: as far as it is free from the joint)
            keep = []
            for pp in (st.pieces if side > 0 else st.pieces[::-1]):
                if not self.free(st.rect(pp)):
                    break
                keep.append(pp)
            if len(keep) < 2 or q is not None:
                return None
            st.pieces = keep if side > 0 else keep[::-1]
            st.lo, st.hi = st.pieces[0]["a0"], st.pieces[-1]["a1"]
        ni = 0 if side > 0 else len(st.pieces) - 1
        fixed = {ni: p["y"]}
        if q is not None:
            fixed[len(st.pieces) - 1 - ni] = q["y"]
        self.grade(st, fixed)
        st.parent, st.side, st.aj = parent, side, aj
        return self.add_street(st)

    def child_mid(self, parent, a, side, length):
        """A lane off a street that was cut without a joint there: only where the piece there is long enough that
        the lane's mouth faces one level."""
        p = parent.piece_at(a)
        if p["a1"] - p["a0"] < 4:
            return None
        a = max(p["a0"] + 2, min(p["a1"] - 2, a))
        return self.child(parent, a, side, "lane", 3, length=length)

    def lane_spots(self, a0, a1, n, gap=26):
        """n places along a street for side lanes, at least `gap` apart, each on a side (0: both, a cross lane)."""
        rng = self.rng
        out = []
        for _ in range(n * 12):
            if len(out) >= n or a1 <= a0:
                break
            a = rng.randint(a0, a1)
            if all(abs(a - b) >= gap for b, _ in out):
                out.append((a, rng.choice((-1, 1, 1, -1, 0))))
        return sorted(out)

    # -------------------------------------------------------------------------------------------- rivers

    def river_w(self):
        return {"village": 7, "town": self.rng.choice((9, 11)), "big": 11}[self.size]

    def grade_river(self, rv):
        """The water's level piece by piece: a little under the ground along it, never rising downstream (the higher
        end is upstream), dropping a block or two where the ground has clearly fallen (a weir: the reach above is
        closed off at its end); a bridge's neighbours at the bridge's level."""
        t = [v - 1.5 for v in self.targets(rv)]
        n = len(t)
        order = list(range(n)) if t[0] >= t[-1] else list(range(n - 1, -1, -1))
        y = None
        ys = [0] * n
        for i in order:
            if y is None:
                y = round(t[i])
            elif t[i] < y - 1.5:
                y -= min(2, int(y - t[i] - 0.5))
            ys[i] = y
        for i, p in enumerate(rv.pieces):
            if p.get("bridge"):
                for j in (i - 1, i + 1):
                    if 0 <= j < n:
                        ys[j] = ys[i]
        for p, v in zip(rv.pieces, ys):
            p["y"] = v
        return rv

    def river(self, axis, lo, hi, cf, w, bridges=(), joints=()):
        """A river along the axis from lo to hi following cf, a bridge piece over each span in `bridges` [(a0, a1)]
        (and one piece over each of `joints`: where a lane leaves the street on its bank)."""
        rv = Street(axis, lo, hi, cf, w, "river", fixed=list(bridges) + list(joints), minlen=5, maxlen=11)
        for p in rv.pieces:
            p["bridge"] = any(p["a0"] == a and p["a1"] == b for a, b in bridges)
        ps = rv.pieces                                     # (a 河桥 frame is at least 5 long: a shorter piece joins
        i = 0                                              # the one next to it that is no bridge)
        while i < len(ps):
            p = ps[i]
            if p["a1"] - p["a0"] < 4 and not p["bridge"]:
                nb = [j for j in (i - 1, i + 1) if 0 <= j < len(ps) and not ps[j]["bridge"]]
                if nb:
                    j = nb[0]
                    ps[j]["a0"], ps[j]["a1"] = min(ps[j]["a0"], p["a0"]), max(ps[j]["a1"], p["a1"])
                    del ps[i]
                    continue
            i += 1
        self.odd_pieces(ps)
        rv.lo, rv.hi = ps[0]["a0"], ps[-1]["a1"]
        self.grade_river(rv)
        self.rivers.append(rv)
        for p in rv.pieces:
            self.take(rv.rect(p), "river")
        return rv

    @staticmethod
    def odd_pieces(ps):
        """Every piece but a bridge an odd number of cells long (单体建造 drops a column off an even frame, and the water
        ran out of the gap): a cell more off the next piece (or the one before, when a bridge is next; the river's end
        at either end)."""
        def even(p):
            return (p["a1"] - p["a0"]) % 2 == 1
        runs, cur = [], []                                 # (the stretches between bridges)
        for p in ps + [None]:
            if p is None or p["bridge"]:
                if cur:
                    runs.append(cur)
                cur = []
            else:
                cur.append(p)
        for run in runs:
            open_lo, open_hi = run[0] is ps[0], run[-1] is ps[-1]
            T = run[-1]["a1"] - run[0]["a0"] + 1
            if not (open_lo or open_hi) and len(run) % 2 != T % 2 and len(run) > 1:
                j = min(range(len(run) - 1), key=lambda j: run[j + 1]["a1"] - run[j]["a0"])   # (one piece fewer: the
                run[j]["a1"] = run[j + 1]["a1"]                                             # two shortest together)
                ps.remove(run[j + 1])
                del run[j + 1]
            if open_lo and not open_hi:                    # (from the far end back: the river's start takes the odd cell)
                for j in range(len(run) - 1, 0, -1):
                    if even(run[j]):
                        run[j]["a0"] -= 1
                        run[j - 1]["a1"] -= 1
                if even(run[0]):
                    run[0]["a0"] -= 1
            else:
                for j in range(len(run) - 1):
                    if even(run[j]):
                        run[j]["a1"] += 1
                        run[j + 1]["a0"] += 1
                if even(run[-1]) and open_hi:
                    run[-1]["a1"] += 1

    def bank(self, rv, side, w, kind, a0=None, a1=None, fix=None):
        """A street along a river's bank (side -1: the low side across, +1 the other), its pieces cut as the river's,
        right beside it: its level from the water's bank up two, at a bridge the bridge's level."""
        a0 = rv.lo if a0 is None else a0
        a1 = rv.hi if a1 is None else a1
        st = Street.__new__(Street)
        st.axis, st.lo, st.hi, st.w, st.hw, st.kind, st.step = rv.axis, a0, a1, w, w // 2, kind, 1
        off = side * (rv.hw + 1 + w // 2)
        st.cfun = (lambda a, f=rv.cfun, off=off: f(a) + off)
        st.pieces = [dict(a0=max(a0, p["a0"]), a1=min(a1, p["a1"]), c=p["c"] + off, y=0, ry=p["y"], bridge=p.get("bridge"))
                     for p in rv.pieces if p["a1"] >= a0 and p["a0"] <= a1]
        st.pieces = [p for p in st.pieces if p["a1"] >= p["a0"]]
        if not st.pieces or not all(self.free(st.rect(p)) for p in st.pieces):
            return None
        fixed = {i: p["ry"] for i, p in enumerate(st.pieces) if p["bridge"]}
        fixed.update(fix or {})
        self.grade(st, fixed, bounds=[(p["ry"], p["ry"] + 2) for p in st.pieces])
        return self.add_street(st)

    def river_spot(self, a0, a1, avoid, xr):
        """Where a river crosses the main street (along z): of a few places from a0 to a1 clear of `avoid` (the
        lanes, the cross street), the lowest ground across the town."""
        rng = self.rng
        best = None
        for _ in range(8):
            z = rng.randint(a0, a1) if a1 > a0 else a0
            if any(abs(z - b) < 16 for b in avoid):
                continue
            m = sum(self.H(x, z) for x in range(-xr, xr + 1, 6))
            if best is None or m < best[0]:
                best = (m, z)
        return None if best is None else best[1]

    def river_across(self, main, holes, xr):
        """A river along x across the main street (cut with a hole for it): a bridge where the main street was, the
        pieces either side of the hole at the bridge's level (river_fix, for grading the main street)."""
        rng = self.rng
        h0, h1 = holes[0]
        zr = (h0 + h1) // 2
        before = [p for p in main.pieces if p["a1"] < h0]
        after = [p for p in main.pieces if p["a0"] > h1]
        pc = (before[-1] if before else after[0])["c"]
        w = h1 - h0 + 1
        A, P, ph = rng.uniform(1.5, 6.0), rng.uniform(80, 160), rng.uniform(0, 2 * math.pi)
        f0 = A * math.sin(2 * math.pi * pc / P + ph)
        cf = (lambda x, A=A, P=P, ph=ph, f0=f0: zr + A * math.sin(2 * math.pi * x / P + ph) - f0)
        bh = max(2, main.hw)
        rv = self.river("x", pc - xr, pc + xr, cf, w, [(pc - bh, pc + bh)],
                        joints=[(pc - bh - 5, pc - bh - 1), (pc + bh + 1, pc + bh + 5)])
        bp = next(p for p in rv.pieces if p["bridge"])
        self.river_fix = {}
        if before:
            self.river_fix[main.pieces.index(before[-1])] = bp["y"]
        if after:
            self.river_fix[main.pieces.index(after[0])] = bp["y"]
        self.river_pc = pc
        return rv

    def bank_lanes(self, rv, main, pc):
        """Lanes along the river's banks from the main street out (some of the four), houses facing the water."""
        rng = self.rng
        for side in (-1, 1):
            for way in (-1, 1):
                if rng.random() > (0.55 if self.size != "village" else 0.35):
                    continue
                near = main.piece_at(rv.piece_at(pc)["c"] + side * (rv.hw + 2))
                if way > 0:
                    a0 = near["c"] + main.hw + 1
                    a1 = min(rv.hi - 4, a0 + rng.randint(24, 60))
                else:
                    a1 = near["c"] - main.hw - 1
                    a0 = max(rv.lo + 4, a1 - rng.randint(24, 60))
                if a1 - a0 < 12:
                    continue
                self.bank(rv, side, 3, "bank", a0, a1, fix=None)

    def main_with_river(self, lo, hi, W, joints, pin=None, fixed=None, a0=None, a1=None, avoid=(), amp=(1.5, 8.0)):
        """The main street along z, now and then (by the size) with a river across it between a0 and a1: the river,
        its bridge, bank lanes. Returns the main street (graded, added)."""
        rng, C = self.rng, self.C
        holes = []
        zr = None
        xr = max({"village": 50, "town": 80, "big": 110}[self.size], self.xr)
        if rng.random() < C["river"]:
            rw = self.river_w()
            zr = self.river_spot(a0 if a0 is not None else lo + 20, a1 if a1 is not None else hi - 20,
                                 [a for a, _ in joints] + list(avoid), xr)
            if zr is not None:
                holes = [(zr - rw // 2, zr + rw // 2)]
                joints = [(a, w) for a, w in joints if abs(a - zr) > rw // 2 + w // 2 + 4]
        main = self.curvy("z", lo, hi, W, "main", joints=joints, holes=holes, pin=pin, amp=amp)
        rv = self.river_across(main, holes, xr) if holes else None
        fx = dict(fixed or {})
        fx.update(self.river_fix)
        self.grade(main, fx)
        self.add_street(main)
        self.main = main
        if rv is not None:
            self.bank_lanes(rv, main, self.river_pc)
        self.zr = zr
        return main

    def off_river(self, spots):
        zr = getattr(self, "zr", None)
        return [s for s in spots if zr is None or abs(s[0] - zr) > 12]

    def clip_rivers(self):
        """A river ends where the town does (two past its last parcel or street), or a little way outside the wall it
        leaves through (its water gate): the pieces past that go, the last one cut short (odd, at least 5 long)."""
        for rv in self.rivers:
            if getattr(self, "wall_rect", None):
                X0, Z0, X1, Z1 = self.wall_rect
                lo, hi = ((X0, X1) if rv.axis == "x" else (Z0, Z1))
                lo, hi = lo - 3, hi + 3
            else:
                cl = [c for c, t in self.claim.items() if t not in ("river",)]
                k = 0 if rv.axis == "x" else 1
                lo, hi = min(c[k] for c in cl) - 2, max(c[k] for c in cl) + 2
            keep = []
            for p in rv.pieces:
                if p["a1"] < lo or p["a0"] > hi:
                    continue
                q = dict(p)
                if q["a0"] < lo:
                    q["a0"] = lo + ((q["a1"] - lo) % 2)        # (odd: a cell more off)
                if q["a1"] > hi:
                    q["a1"] = hi - ((hi - q["a0"]) % 2)
                if q["a1"] - q["a0"] >= 4 or q.get("bridge"):
                    keep.append(q)
            if keep:
                rv.pieces = keep
                rv.lo, rv.hi = keep[0]["a0"], keep[-1]["a1"]

    def merge_ground(self):
        """Fewer rooms: 台地 of one level and one surface that meet along a whole side run together into one."""
        ts = [r for r in self.rooms if r.get("special") == "terrace"]
        rest = [r for r in self.rooms if r.get("special") != "terrace"]
        changed = True
        while changed:
            changed = False
            by_x = {}
            by_z = {}
            for r in ts:
                by_x.setdefault((r["y"], r["variant"], r["z0"], r["z1"], r["x1"] + 1), []).append(r)
                by_z.setdefault((r["y"], r["variant"], r["x0"], r["x1"], r["z1"] + 1), []).append(r)
            gone = set()
            for r in ts:
                if id(r) in gone:
                    continue
                for nb in by_x.get((r["y"], r["variant"], r["z0"], r["z1"], r["x0"]), []) + \
                        by_z.get((r["y"], r["variant"], r["x0"], r["x1"], r["z0"]), []):
                    if nb is r or id(nb) in gone or max(r["x1"], nb["x1"]) - min(r["x0"], nb["x0"]) >= 120                             or max(r["z1"], nb["z1"]) - min(r["z0"], nb["z0"]) >= 120:   # (a room's side: at most 256)
                        continue
                    r["x0"], r["z0"] = min(r["x0"], nb["x0"]), min(r["z0"], nb["z0"])
                    gone.add(id(nb))
                    changed = True
                    break
            ts = [r for r in ts if id(r) not in gone]
        self.rooms = rest + ts

    def lay_rivers(self):
        """Every river as 河桥 pieces: a plain channel, a bridge where a street crosses, each reach closed at its ends
        (the river's two ends, a weir down to the next reach)."""
        for rv in self.rivers:
            ps = rv.pieces
            n = len(ps)
            run = None
            for i, p in enumerate(ps + [None]):
                if p is not None:
                    lo_c = i == 0 or ps[i - 1]["y"] < p["y"]
                    hi_c = i == n - 1 or ps[i + 1]["y"] < p["y"]
                    if p.get("bridge"):
                        var = 2
                    elif lo_c and hi_c:
                        var = 5
                    elif lo_c:
                        var = 3 if rv.axis == "x" else 4
                    elif hi_c:
                        var = 4 if rv.axis == "x" else 3
                    else:
                        var = 1
                    if run and var == 1 and run["var"] == 1 and p["y"] == run["y"] and p["c"] == run["c"] and p["a1"] - run["a0"] < 60:
                        run["a1"] = p["a1"]
                        continue
                if run:
                    self.ground("river", rv.rect(run), run["y"], variant=run["var"], front="east" if rv.axis == "z" else "south",
                                walls=self.mat["wall"])
                run = dict(p, var=var) if p is not None else None

    # -------------------------------------------------------------------------------------------- street plans

    def net_bank(self):
        """A river along z through the town, the main street along its east bank, a lane along the west bank (or a
        street further west, the houses between backing onto the water), bridges across."""
        rng, C = self.rng, self.C
        L = odd(rng.randint(*C["L"]))
        W = self.W = rng.choice(C["W"])
        lo, hi = -(L // 2), L // 2
        rw = self.river_w()
        rhw = rw // 2
        wb = 5                                                 # (a bridge 5 wide, the lane over it 3)
        nb = 1 if self.size == "village" else rng.randint(2, 3)
        if self.box:
            nb = max(nb, L // 100)                             # (a long river town: more bridges)
        bz = [a for a, _ in self.lane_spots(lo + 18, hi - 18, nb, gap=30)]
        spots = [(a, 1) for a, _ in self.lane_spots(lo + 16, hi - 16, rng.randint(*C["lanes"]))
                 if all(abs(a - b) > 12 for b in bz)]
        if self.gridded:                                       # (a big river town: streets across every 40 or so)
            step = rng.randint(38, 44)
            spots = [(a, 1) for a in range(lo + 24, hi - 20, self.G or step) if all(abs(a - b) > 8 for b in bz)]
        xr = -(rhw + 1 + W // 2)
        east = None
        if self.box and self.gridded:                          # (as many blocks east of the main street as the box
            kb = max(1, int((self.box[0] - 2 * self.pad - rw - 62) // self.G))   # holds, the town centred in it)
            east = [self.G * j for j in range(1, kb + 1)]
            xr -= (kb * self.G - rw - 18) // 2
        A, P, ph = rng.uniform(1.5, 5.0), rng.uniform(1.0, 2.0) * L, rng.uniform(0, 2 * math.pi)
        cf = (lambda z, A=A, P=P, ph=ph: xr + A * math.sin(2 * math.pi * z / P + ph))
        rv = self.river("z", lo - 12, hi + 12, cf, rw, [(z - wb // 2, z + wb // 2) for z in bz],
                        joints=[(a - 2, a + 2) for a, _ in spots])
        main = self.bank(rv, 1, W, "main", lo, hi)
        self.main = main
        west = None
        if rng.random() < 0.6 or self.size == "village":
            Ww = 3 if self.size == "village" else rng.choice((3, 5))
            west = self.bank(rv, -1, Ww, "bank", lo + rng.randint(0, 24), hi - rng.randint(0, 24))
            if west is not None:
                for z in bz:
                    if west.lo + 3 <= z <= west.hi - 3:
                        self.child_mid(west, z, -1, rng.randint(16, 30)) if west.piece_at(z)["a1"] - west.piece_at(z)["a0"] >= 4 \
                            else self.child(west, z, -1, "lane", 3, length=rng.randint(16, 30))
        else:
            Ww = 5
            D = rng.randint(24, 32)
            off = rhw + 1 + D + Ww // 2 + 1
            ws = Street("z", lo + rng.randint(4, 20), hi - rng.randint(4, 20), lambda z, cf=cf, off=off: cf(z) - off, Ww, "cross",
                        fixed=[(z - wb // 2 - 1, z + wb // 2 + 1) for z in bz])
            if self.free_street(ws):
                self.grade(ws)
                self.add_street(ws)
                for z in bz:                                   # 桥 on to the west street
                    if not (ws.lo + 3 <= z <= ws.hi - 3):
                        continue
                    q = ws.piece_at(z)
                    rp = rv.piece_at(z)
                    a0, a1 = q["c"] + ws.hw + 1, rp["c"] - rv.hw - 1
                    if a1 - a0 < 3:
                        continue
                    cn = Street("x", a0, a1, lambda a, z=z: z, 3, "lane", maxlen=5)
                    if all(self.free(cn.rect(p)) for p in cn.pieces):
                        self.grade(cn, {0: q["y"], len(cn.pieces) - 1: rp["y"]})
                        self.add_street(cn)
        if self.gridded:
            G = rng.randint(38, 42)
            self.blocks(main, east or [G, 2 * G], [a for a, _ in spots], (), (), lo + 6, hi - 6)
        else:
            self.later.append(lambda: self.lanes_and_backs(main, spots, sides=(1,)))
        self.ends = [("entrance", main, len(main.pieces) - 1), ("far", main, 0)]
        self.centre = (main.piece_at(0)["c"] + 10, 0)
        self.radius = L / 2

    def net_line(self):
        """One main street along z, side lanes off it, back lanes along the plot backs."""
        rng, C = self.rng, self.C
        L = odd(rng.randint(*C["L"]) + (12 if self.size == "town" else 0))
        W = self.W = rng.choice(C["W"])
        lo, hi = -(L // 2), L // 2
        if self.G:                                             # (a box bigger than a town of its size: in blocks, the
            zs = self.cross_zs(lo, hi, self.G)                 # main street curving through them)
            main = self.main_with_river(lo, hi, W, [(z, 5) for z in zs], avoid=zs)
            self.blocks(main, self.offs(self.G), [z for z in zs if self.jointed(main, z)], (), (), lo + 8, hi - 8)
        else:
            spots = self.lane_spots(lo + 18, hi - 18, rng.randint(*C["lanes"]))
            main = self.main_with_river(lo, hi, W, [(a, 3) for a, _ in spots])
            spots = self.off_river(spots)
            self.later.append(lambda: self.lanes_and_backs(main, spots))
        self.ends = [("entrance", main, len(main.pieces) - 1), ("far", main, 0)]
        self.centre = (main.piece_at(0)["c"], 0)
        self.radius = L / 2

    def lanes_and_backs(self, main, spots, sides=(-1, 1), crossers=()):
        """Side lanes off a street at the spots, and on a side with lanes (often) a back lane along the plot backs,
        the lanes running on to it (and across the streets in `crossers` - a cross street's arm: a gap in the back
        lane where it crosses, its pieces either side at the arm's level)."""
        rng, C = self.rng, self.C
        backs = {}
        for side in sides:
            zs = [a for a, sd in spots if sd in (side, 0)]
            if zs and rng.random() < C["back"]:
                O = rng.randint(34, 44) + main.hw
                a0 = max(main.lo + 6, min(zs) - rng.randint(10, 24))
                a1 = min(main.hi - 6, max(zs) + rng.randint(10, 24))
                if self.size == "big":                       # (a big town's back streets run the whole way)
                    a0, a1 = main.lo + 10, main.hi - 10
                if a1 - a0 < 24:
                    continue
                cf = (lambda a, s=side, O=O: main.cfun(a) + s * O)
                holes, joins = [], []
                for cr in crossers:
                    x = round(cf(cr.aj))
                    if cr.lo <= x <= cr.hi:
                        q = cr.piece_at(x)
                        holes.append((q["c"] - cr.hw, q["c"] + cr.hw))
                        joins.append((q["c"] - cr.hw, q["c"] + cr.hw, q["y"]))
                b = Street(main.axis, a0, a1, cf, 3, "back", fixed=[(a - 2, a + 2) for a in zs], holes=holes, maxlen=7)
                segs, cur = [], []                         # (the stretches of it that are free: a temple in the way
                for p in b.pieces + [None]:                 # cuts it in two)
                    if p is not None and self.free(b.rect(p, 2)) and (not cur or p["a0"] == cur[-1]["a1"] + 1):
                        cur.append(p)
                        continue
                    if len(cur) >= 4:
                        segs.append(cur)
                    cur = [p] if p is not None and self.free(b.rect(p, 2)) else []
                backs[side] = []
                for ps in segs:
                    sb = Street.__new__(Street)
                    sb.__dict__.update(b.__dict__)
                    sb.pieces, sb.lo, sb.hi = ps, ps[0]["a0"], ps[-1]["a1"]
                    fx = {}
                    for h0, h1, y in joins:
                        for i, p in enumerate(ps):
                            if p["a1"] == h0 - 1 or p["a0"] == h1 + 1:
                                fx[i] = y
                    self.grade(sb, fx)
                    self.add_street(sb)
                    backs[side].append(sb)
        for a, sd in spots:
            for side in ((-1, 1) if sd == 0 else (sd,)):
                if side not in sides:
                    continue
                b = next((sb for sb in backs.get(side, ()) if sb.lo + 2 <= a <= sb.hi - 2), None)
                self.child(main, a, side, "lane", 3, length=rng.randint(18, 34), to=b)
        return backs

    def net_T(self):
        """The main street along z up to a cross street at the far end; the temple across the head of the T."""
        rng, C = self.rng, self.C
        L = odd(rng.randint(*C["L"]))
        W = self.W = rng.choice(C["W"])
        lo, hi = -(L // 2), L // 2
        Lc = odd(rng.randint(int(L * 0.45), int(L * 0.7)) if self.size != "village" else rng.randint(int(L * 0.4), int(L * 0.55)))
        if self.size == "big" and not self.box:                # (the temple across the head: a big town's T shorter)
            L = odd(int(L * 0.82))
            lo, hi = -(L // 2) + 20, L // 2 + 20
        if self.box:                                           # (a box: the head and its temple inside it too, the
            lo, hi = -(L // 2) + 25, L // 2 + 25               # cross street across the blocks)
            Lc = odd(min(self.box[0] - 2 * self.pad - 20, 2 * self.k * self.G + 50 if self.G else Lc))
        Wc = max(3, W - rng.choice((0, 2)))
        cspots = [(a, 1) for a, _ in self.lane_spots(-(Lc // 2) + 14, Lc // 2 - 14, rng.randint(0, 2), gap=22) if abs(a) > W + 10]
        cross = self.curvy("x", -(Lc // 2), Lc // 2, Wc, "cross", joints=[(0, W)] + [(a, 3) for a, _ in cspots],
                           amp=(0.0, 3.0), base=lo)
        self.grade(cross)
        self.add_street(cross)
        jp = cross.piece_at(0)
        start = jp["c"] + cross.hw + 1
        spots = self.lane_spots(start + 16, hi - 16, rng.randint(*C["lanes"]))
        if self.gridded:                                       # (a big town's T: streets across every 40 or so, blocks)
            step = rng.randint(38, 44)
            spots = [(a, 0) for a in range(start + 30, hi - 20, self.G or step)]
        main = self.main_with_river(start, hi, W, [(a, 3) for a, _ in spots], pin=(start, 0), fixed={0: jp["y"]},
                                    a0=start + 22, a1=hi - 22)
        spots = self.off_river(spots)
        for a, _ in cspots:                                    # (lanes off the cross street towards the entrance)
            self.later.append(lambda a=a: self.child(cross, a, 1, "lane", 3, length=rng.randint(16, 28)))
        if self.gridded:
            G = rng.randint(38, 42)
            self.blocks(main, self.offs(self.G or G), [a for a, _ in spots], (), (), start + 6, hi - 8)
        else:
            self.later.append(lambda: self.lanes_and_backs(main, spots))
        self.ends = [("entrance", main, len(main.pieces) - 1), ("cross_w", cross, 0), ("cross_e", cross, len(cross.pieces) - 1)]
        self.head = (cross, 0)
        self.centre = (0, start + (hi - start) // 3)
        self.radius = L / 2

    def parallel(self, main, off, w, kind, crossers=(), joints=(), a0=None, a1=None):
        """A street along `main` `off` across from it (its centre line following main's), cut round the streets in
        `crossers` (a gap where it crosses one, its pieces either side at that one's level) and into the stretches of
        it that are free. joints: [a] where a lane (3 wide) will join it. Returns the stretches (graded, added)."""
        a0 = main.lo + 4 if a0 is None else a0
        a1 = main.hi - 4 if a1 is None else a1
        cf = (lambda a, O=off: main.cfun(a) + O)
        if self.layout == "grid":                              # (a planned town's streets run straight)
            cm = round(main.piece_at((a0 + a1) // 2)["c"])
            cf = (lambda a, O=off, cm=cm: cm + O)
        holes, joins = [], []
        for cr in crossers:
            x = round(cf(cr.aj))
            if cr.lo <= x <= cr.hi:
                q = cr.piece_at(x)
                holes.append((q["c"] - cr.hw, q["c"] + cr.hw))
                joins.append((q["c"] - cr.hw, q["c"] + cr.hw, q["y"]))
        b = Street(main.axis, a0, a1, cf, w, kind, fixed=[(a - 2, a + 2) for a in joints], holes=holes, maxlen=7)
        segs, cur = [], []
        for p in b.pieces + [None]:
            if p is not None and self.free(b.rect(p, 2)) and (not cur or p["a0"] == cur[-1]["a1"] + 1):
                cur.append(p)
                continue
            if len(cur) >= 4:
                segs.append(cur)
            cur = [p] if p is not None and self.free(b.rect(p, 2)) else []
        out = []
        for ps in segs:
            sb = Street.__new__(Street)
            sb.__dict__.update(b.__dict__)
            sb.pieces, sb.lo, sb.hi = ps, ps[0]["a0"], ps[-1]["a1"]
            fx = {}
            for h0, h1, y in joins:
                for i, p in enumerate(ps):
                    if p["a1"] == h0 - 1 or p["a0"] == h1 + 1:
                        fx[i] = y
            self.grade(sb, fx)
            self.add_street(sb)
            out.append(sb)
        return out

    def net_grid(self):
        """A big town's plan: a 十字街 (the main street along z, the cross street along x through the middle), and
        streets parallel to both about every 44 cells - blocks (坊) between them, lanes into some."""
        rng, C = self.rng, self.C
        L = odd(rng.randint(*C["L"]))
        W = self.W = 7 if self.size == "big" else rng.choice(C["W"])   # (a 十字街 in a box: a town's, a village's too)
        lo, hi = -(L // 2), L // 2
        G = rng.randint(38, 42)
        if self.G:
            G = self.G
        zc = rng.randint(-8, 8)
        xs_off = self.offs(G)                                  # (the streets along z, off the main street)
        zs = self.cross_zs(lo, hi, G, zc)                      # (the streets along x, off the cross street)
        main = self.main_with_river(lo, hi, W, [(zc, W)] + [(z, 5) for z in zs], avoid=[zc - 16, zc + 16] + zs, amp=(0.0, 0.6))
        if self.box:
            zs = [z for z in zs if self.jointed(main, z)]
        self.grid_z = zs
        jp = main.piece_at(zc)
        arms = []
        half = (L // 2) - rng.randint(0, 10)
        if self.box:                                           # (out past the outer streets along z)
            half = self.k * G + max(10, min(self.outer, 40))
        for side in (-1, 1):
            st = self.child(main, zc, side, "cross", W, length=half, wobble=0.0)
            if st is not None:
                arms.append(st)
        self.arms = arms
        by_side = {st.side: st for st in arms}
        yq = rng.choice((-1, 1))                               # the yamen's block: north of the cross street, on this
        skip = {(zc - G, yq), (zc + G, -yq)}                   # side; the temple's: south of it on the other (two blocks
        self.grid_sites = []                                   # deep: no street along x through them)
        if yq in by_side:
            self.grid_sites.append(("yamen", Side(by_side[yq], -1)))
        if -yq in by_side:
            self.grid_sites.append(("temple", Side(by_side[-yq], 1)))
        self.blocks(main, xs_off, zs, arms, skip, lo + 8, hi - 8)

        def later():
            for st in arms:                                    # lanes into the blocks off the cross street
                for a, sd in self.lane_spots(st.lo + 10, st.hi - 10, rng.randint(1, 3), gap=20):
                    if all(abs(a - (jp["c"] + o)) > 10 for o in [0] + xs_off):
                        for side in ((-1, 1) if sd == 0 else (sd,)):
                            self.child_mid(st, a, side, rng.randint(14, G // 2 + 4))
        self.later.append(later)
        self.ends = [("entrance", main, len(main.pieces) - 1), ("far", main, 0)] + \
                    [("arm", st, len(st.pieces) - 1 if st.side > 0 else 0) for st in arms]
        self.centre = (jp["c"], zc)
        self.radius = L * 0.75
        self.crossing = (jp["c"], zc, jp["y"])

    def blocks(self, main, xs_off, zs, crossers=(), skip=(), a0=None, a1=None, outer=None):
        """Streets parallel to the main one `xs_off` across from it, and streets across between neighbouring ones at
        each of `zs` (the main street cut with a joint there), lanes out past the outer ones (`outer` long: in a box,
        as far as it goes): blocks (坊)."""
        rng = self.rng
        outer = self.outer if outer is None else outer
        cols = {0: main}
        for off in xs_off:                                     # the streets along z, either side
            cols[off] = self.parallel(main, off, 5, "cross", crossers=crossers, joints=zs, a0=a0, a1=a1)
        order = sorted(cols)
        for z in zs:                                           # the streets along x: between neighbouring streets along
            for k in range(len(order)):                        # z (and lanes out past the outer ones)
                o = order[k]
                src = cols[o] if o == 0 else next((sb for sb in cols[o] if sb.lo + 3 <= z <= sb.hi - 3), None)
                if src is None:
                    continue
                for side in (1, -1):
                    k2 = k + side
                    if 0 <= k2 < len(order):
                        o2 = order[k2]
                        if o2 == 0 or (z, 1 if o2 > 0 else -1) in skip:
                            continue
                        dst = next((sb for sb in cols[o2] if sb.lo + 3 <= z <= sb.hi - 3), None)
                        if dst is not None:
                            self.child(src, z, side, "cross", 5, to=dst)
                    elif o != 0 and (outer is None or outer >= 14):
                        self.later.append(lambda src=src, z=z, side=side: self.child(
                            src, z, side, "lane", 3, length=rng.randint(24, 40) if outer is None else outer))
        return cols

    def net_cross(self):
        """A 十字街: the main street along z crossed by a street along x near the middle (in a big box: the grid)."""
        if self.G:
            return self.net_grid()
        rng, C = self.rng, self.C
        L = odd(rng.randint(*C["L"]))
        W = self.W = rng.choice(C["W"])
        lo, hi = -(L // 2), L // 2
        zc = rng.randint(-L // 8, L // 10)
        Wc = max(3, W - rng.choice((0, 0, 2)))
        spots = [s for s in self.lane_spots(lo + 16, hi - 16, rng.randint(*C["lanes"])) if abs(s[0] - zc) > Wc + 14]
        main = self.main_with_river(lo, hi, W, [(zc, Wc)] + [(a, 3) for a, _ in spots], avoid=(zc - 16, zc + 16))
        spots = self.off_river(spots)
        jp = main.piece_at(zc)
        arms = []
        big = self.size == "big"
        for side in (-1, 1):
            Lc = rng.randint(int(L * 0.3), int(L * 0.45)) if not big else rng.randint(int(L * 0.42), int(L * 0.5))
            st = self.child(main, zc, side, "cross", Wc, length=Lc, wobble=3.0 if not big else 1.5)
            if st is not None:
                arms.append(st)
        self.arms = arms
        self.later.append(lambda: self.lanes_and_backs(main, spots, crossers=arms if big else ()))
        for st in arms:                                       # lanes off the arms
            n = rng.randint(0, 2 if self.size != "village" else 1) if not big else rng.randint(2, 4)
            for a, sd in self.lane_spots(st.lo + 12, st.hi - 12, n, gap=22):
                if abs(a - jp["c"]) < main.hw + 12:
                    continue
                for side in ((-1, 1) if sd == 0 else (sd,)):
                    self.later.append(lambda st=st, a=a, side=side: self.child_mid(st, a, side, rng.randint(14, 26) if not big else rng.randint(20, 36)))
        self.ends = [("entrance", main, len(main.pieces) - 1), ("far", main, 0)] + \
                    [("arm", st, len(st.pieces) - 1 if st.side > 0 else 0) for st in arms]
        self.centre = (jp["c"], zc)
        self.radius = L / 2
        self.crossing = (jp["c"], zc, jp["y"])

    def net_Y(self):
        """The main street from the entrance up to a fork at a little square, two streets on from there, a tree in
        the fork."""
        rng, C = self.rng, self.C
        L = odd(rng.randint(*C["L"]))
        W = self.W = rng.choice(C["W"])
        lo, hi = -(L // 2), L // 2
        zf = rng.randint(-L // 10, L // 8)
        Wb = max(3, W - rng.choice((0, 2)))
        P = W + 3                                              # the fork square's half width
        Dp = W + 2                                             # and its depth
        spots = self.lane_spots(zf + 16, hi - 16, max(0, rng.randint(*C["lanes"]) - 1))
        if self.G:                                             # (in a box: blocks along the stem, no lanes)
            zs = self.cross_zs(zf + 2, hi, self.G)
            main = self.main_with_river(zf, hi, W, [(z, 5) for z in zs], pin=(zf, 0), a0=zf + 20, a1=hi - 20, avoid=zs)
            self.blocks(main, self.offs(self.G), [z for z in zs if self.jointed(main, z)], (), (), zf + 6, hi - 8)
            spots = []
        else:
            main = self.main_with_river(zf, hi, W, [(a, 3) for a, _ in spots], pin=(zf, 0), a0=zf + 20, a1=hi - 20)
            spots = self.off_river(spots)
        y0 = main.pieces[0]["y"]
        sq = (-P, zf - Dp, P, zf - 1)
        self.fork = dict(rect=sq, y=y0)
        self.take(sq, "street")
        k = rng.uniform(0.22, 0.34)
        spread = rng.randint(14, 24) if self.size != "village" else rng.randint(11, 16)
        bhi = zf - Dp - 1
        self.branches = []
        for sgn in (-1, 1):
            o0 = P - Wb // 2

            def cf(z, sgn=sgn, o0=o0):
                return sgn * (o0 + min(spread, k * (bhi - z)))
            blo = lo + rng.randint(0, 8)
            zb = self.cross_zs(blo, bhi, self.G) if self.G else []
            st = Street("z", blo, bhi, cf, Wb, "branch", fixed=[(z - 3, z + 3) for z in zb], minlen=2, maxlen=7)
            self.grade(st, {len(st.pieces) - 1: y0})
            self.add_street(st)
            self.branches.append(st)
            kb = int((self.box[0] / 2 - self.pad - 22 - P - spread) // self.G) if self.G else 0
            if kb > 0:                                         # (in a box: blocks out from each branch)
                self.blocks(st, [sgn * self.G * j for j in range(1, kb + 1)], [z for z in zb if self.jointed(st, z)],
                            (), (), blo + 4, bhi - 6, outer=int(self.box[0] / 2 - self.pad - P - spread - kb * self.G - 6))
        for st, sgn in zip(self.branches, (-1, 1)):
            if self.G:
                continue
            for a, sd in self.lane_spots(st.lo + 12, st.hi - 18, rng.randint(0, 2 if self.size != "village" else 1), gap=22):
                self.later.append(lambda st=st, a=a, sgn=sgn: self.child_mid(st, a, sgn, rng.randint(14, 26)))   # (outer side)
        self.later.append(lambda: self.lanes_and_backs(main, spots))
        self.ends = [("entrance", main, len(main.pieces) - 1)] + [("branch", st, 0) for st in self.branches]
        self.centre = (0, zf)
        self.radius = L / 2

    # -------------------------------------------------------------------------------------------- parcels

    def row(self, s, u0, umax, homes=False):
        """A row of one to five shops (or houses) wall to wall, a yard behind, often a house at the back."""
        rng = self.rng
        F0, _ = s.lines(u0, u0)
        w = self.wealth(*s.xz(u0, F0, 8)) + (0.15 if s.st.kind in ("main", "cross") else 0)
        ward = self.ward(w)
        lane = s.st.kind in ("lane", "back")
        if homes or lane:
            n = rng.choices((1, 2, 3), weights=(5, 3, 1) if ward != "rich" and self.size != "big" else (2, 3, 3))[0]
        else:
            n = rng.choices((1, 2, 3, 4, 5), weights=(1, 3, 4, 3, 1) if ward != "rich" else (1, 2, 4, 4, 3))[0]
        roof = self.pick_roof()
        pod = self.mat["podium"]
        gallery = not homes and not lane and ward != "poor" and rng.random() < self.mat["veranda"] + (0.2 if ward == "rich" else 0)
        if ward == "poor":
            depth = rng.choice((7, 7, 9))
        else:
            depth = rng.choice((9, 11, 11, 13)) if not (homes or lane) else rng.choice((7, 9, 9, 11))
        tall = self.mat["tall"] + (0.25 if ward == "rich" else -0.2 if ward == "poor" else 0)
        units = []
        for i in range(n):
            st = 1 if rng.random() > tall else 2
            if rng.random() < 0.05 and ward == "rich":
                st = 3
            if homes or lane:
                use = rng.choice((HOMES if ward != "poor" else POOR_HOMES) if rng.random() < 0.78 else WORKS)
            else:
                use = rng.choice(RARE_SHOPS) if self.mat["rare"] and rng.random() < 0.12 else rng.choice(SHOPS)
            d = self.building(use, storeys=st, roof=roof, podium=pod, veranda=gallery, wealth=w)   # (one roof form: they join)
            wd = rng.choice((5, 7, 7, 9) if ward == "poor" else (9, 11, 11, 13) if ward == "rich" else (7, 9, 9, 11))
            units.append((d, wd, depth))
        while units:
            sm = [margins(dict(d), 0) for d, _, _ in units]   # (eaves of two rows meet over the alley, not more)
            left, right = sm[0][0], sm[-1][0]
            width = left + sum(wd for _, wd, _ in units) - (len(units) - 1) + right   # (next door shares the wall)
            u1 = u0 + width - 1
            y = s.y(u0, u1)
            if u1 <= umax and (s.level_ok(u0, u1, y) or len(units) == 1):
                break
            units.pop()
        if not units:
            return None
        F, near = s.lines(u0, u1)
        fm = max(front_gap(d, s.st.hw) for d, _, _ in units)
        bm = max(m[2] for m in sm) + 1
        D = self.depth_free(s, u0, u1, F, self.dmax)        # (the far half of a block: the next street's)
        if D < fm + depth + bm:
            if D >= fm + 7 + bm:
                depth = 7 if (D - fm - bm) < 9 else 9
                units = [(d, wd, depth) for d, wd, _ in units]
            else:
                return None
        u = u0 + left
        deepest = 0
        for (d, wd, dp) in units:
            self.place(d, s.rect(u, u + wd - 1, F, fm, fm + dp - 1), y, s.facing)
            u += wd - 1
            deepest = max(deepest, dp)
        back0 = fm + deepest + bm                           # the first depth behind the row's eaves
        D = min(D, back0 + 40)
        if rng.random() < (0.82 if ward != "poor" else 0.5) + (0.15 if self.size == "big" else 0):   # a house at the back
            hd = self.building(rng.choice((HOMES if ward != "poor" else POOR_HOMES) if rng.random() < 0.8 else WORKS),
                               storeys=1 if rng.random() < (0.75 if ward != "rich" else 0.45) else 2,
                               roof=roof if rng.random() < 0.6 else None, wealth=w - 0.1)
            hs, hf, hb = margins(dict(hd))
            hw_ = odd(min(width - 2 * hs, rng.choice((9, 11, 13, 15, 17) if ward != "poor" else (5, 7, 9))))
            hdp = rng.choice((7, 9, 9))
            yard = rng.randint(2, 5) if ward != "rich" else rng.randint(4, 8)
            if hw_ >= 5:
                yb = round(self.H(*s.xz((u0 + u1) // 2, F, back0 + yard + hf + hdp // 2)))
                yb = max(y - 3, min(y + 3, yb))
                if abs(yb - y) <= 1:
                    yb = y
                if yb != y:
                    yard = max(yard, 2 * abs(yb - y) + 4)   # (room for a flight down from one to the other)
                hb0 = back0 + yard + hf
                back = hb0 + hdp - 1 + hb
                if back <= D:
                    hx0 = u0 + (width - hw_) // 2 if rng.random() < 0.6 else (u0 + hs if rng.random() < 0.5 else u1 - hs - hw_ + 1)
                    self.place(hd, s.rect(hx0, hx0 + hw_ - 1, F, hb0, hb0 + hdp - 1), yb, s.facing, rank=1)
                    cutb = back0 + yard // 2
                    self.terrace(s, u0, u1, F, near, None, cutb, y)
                    self.terrace(s, u0, u1, F, near, cutb + 1, back, yb)
                    self.take(s.rect(u0, u1, F, 0, back))
                    if yb == y:
                        self.yard_spot(s, u0, u1, F, back0, hb0 - hf, y, w)
                    return u1
        if self.size == "big":                                 # (a big town: a narrow yard, the rest of the block to the
            back = min(D, back0 + rng.randint(1, 3))           # houses on the next street)
        else:
            back = min(D, back0 + rng.randint(2, 6) + (rng.randint(3, 8) if ward == "poor" or homes else 0))
        back = max(back, back0)
        if self.size == "village":
            self.terrace(s, u0, u1, F, near, None, back0 - 1, y)
            self.terrace(s, u0, u1, F, near, back0, back, y, variant=1 if self.mat["yard"] != 1 else 0)
        else:                                                  # (one 台地: the yard as the rest of the parcel)
            self.terrace(s, u0, u1, F, near, None, back, y)
        self.take(s.rect(u0, u1, F, 0, back))
        self.yard_spot(s, u0, u1, F, back0 - 1, back, y, w, garden=True)
        return u1

    def yard_spot(self, s, u0, u1, F, d0, d1, y, w, garden=False):
        """A back yard (depths d0..d1 behind a row, one level): kept for furnishing at the end."""
        if d1 - d0 >= 2:
            self.yards.append(dict(s=s, F=F, u=(u0, u1), d=(d0, d1), y=y, w=w, garden=garden))

    def hut(self, s, u0, umax):
        """A poor house on its own: one room, thatch, a patch of garden beside it."""
        rng = self.rng
        F0, _ = s.lines(u0, u0)
        w = min(0.25, self.wealth(*s.xz(u0, F0, 6)))
        d = self.building(rng.choice(POOR_HOMES), roof=rng.choice(("xuanshan", "yingshan")), wealth=w)
        sm, _, bm = margins(dict(d), 0)
        wd, dp = rng.choice((5, 7, 7)), rng.choice((5, 7))
        garden = rng.choice((0, 5, 6, 7)) if self.size != "big" else 0
        width = 2 * sm + wd + garden
        u1 = u0 + width - 1
        if u1 > umax:
            width -= garden
            garden = 0
            u1 = u0 + width - 1
            if u1 > umax:
                return None
        F, near = s.lines(u0, u1)
        y = s.y(u0, u1)
        fm = front_gap(d, s.st.hw)
        back = fm + dp + bm + rng.randint(1, 5)
        D = self.depth_free(s, u0, u1, F, back)
        if D < fm + dp + bm:
            return None
        back = min(back, D)
        left = rng.random() < 0.5 or not garden
        hx = u0 + sm if left else u0 + sm + garden
        self.place(d, s.rect(hx, hx + wd - 1, F, fm, fm + dp - 1), y, s.facing)
        self.terrace(s, u0, u1, F, near, None, back, y, variant=rng.choice((self.mat["yard"], 1)))
        self.take(s.rect(u0, u1, F, 0, back))
        if garden:
            gu0 = hx + wd + sm - 1 if left else u0
            self.yard_spot(s, gu0, gu0 + garden, F, 1, back - 1, y, w, garden=True)
        return u1

    def inn(self, s, u0, umax):
        """客栈 / 茶楼 / 酒楼: one big building, two or three storeys, a stable or a kitchen behind."""
        rng = self.rng
        use = rng.choice(("inn_hall", "inn_hall", "teahouse"))
        d = self.building(use, storeys=rng.choice((2, 2, 3)), roof=self.pick_roof(big=True), podium=1,
                          veranda=rng.random() < 0.5, wealth=0.8)
        if self.fam.get("landmark"):
            d["theme"] = rng.choice(self.fam["landmark"])
        w, dp = rng.choice((13, 15, 17)), rng.choice((11, 13))
        sm, fm, bm = margins(d, 0)
        fm = front_gap(d, s.st.hw)
        bm += 1
        width = w + 2 * sm
        u1 = u0 + width - 1
        if u1 > umax:
            return None
        y = s.y(u0, u1)
        F, near = s.lines(u0, u1)
        D = self.depth_free(s, u0, u1, F, 60)
        if D < fm + dp + bm:
            return None
        self.place(d, s.rect(u0 + sm, u0 + sm + w - 1, F, fm, fm + dp - 1), y, s.facing, rank=3)
        back = fm + dp + bm
        if rng.random() < 0.6:
            sd = self.building(rng.choice(("stable", "kitchen", "storeroom")), roof="xuanshan" if "xuanshan" in self.fam["roofs"] else None)
            ss, sf, sb = margins(sd)
            sw = odd(min(width - 2 * ss, rng.choice((9, 11, 13))))
            b0 = back + rng.randint(2, 4) + sf
            if b0 + 7 + sb <= D:
                self.place(sd, s.rect(u0 + (width - sw) // 2, u0 + (width - sw) // 2 + sw - 1, F, b0, b0 + 6), y, s.facing, rank=1)
                back = b0 + 7 + sb
        self.terrace(s, u0, u1, F, near, None, back, y)
        self.take(s.rect(u0, u1, F, 0, back))
        return u1

    def shrine(self, s, u0, umax):
        """土地庙: a village's little temple, a tree and a vessel before it."""
        rng = self.rng
        d = self.building("temple_hall", roof=rng.choice(("xieshan", "xuanshan")), podium=1, wealth=0.7,
                          theme=rng.choice(self.fam["rich"]["themes"]))
        sm, fm, bm = margins(d, 0)
        wd, dp = rng.choice((7, 9)), rng.choice((7, 9))
        width = 2 * sm + wd + 7
        u1 = u0 + width - 1
        if u1 > umax:
            return None
        F, near = s.lines(u0, u1)
        y = s.y(u0, u1)
        fm = front_gap(d, s.st.hw) + 3
        back = fm + dp + bm
        if self.depth_free(s, u0, u1, F, back) < back:
            return None
        self.place(d, s.rect(u0 + sm, u0 + sm + wd - 1, F, fm, fm + dp - 1), y, s.facing, rank=3)
        tx, tz = s.xz(u0 + sm + wd + 3, F, fm + dp // 2)
        self.ornament("tree", tx, tz, y, half=2)
        self.terrace(s, u0, u1, F, near, None, back, y, variant=2)
        self.take(s.rect(u0, u1, F, 0, back))
        return u1

    def compound_rooms(self, temple=False, yamen=False):
        """A walled compound from siteplan.site in its own coordinates, its gate at the front (+z): a 住宅 of one or two
        courts, a 祠堂 / 庙 (the main hall a 神殿 or 祠堂 on a 台基, a paved forecourt with a 鼎 either side of the way
        in), or a 衙门 (three courts: the 大堂, the 二堂, the 三堂; a forecourt with two stone lions). Returns (rooms,
        width, depth)."""
        rng = self.rng
        jin = rng.choice((2, 3)) if yamen else 2 if temple else rng.choice((1, 2))
        d = siteplan.site("house", jin, rng.randrange(1 << 30))
        rs = [dict(r) for r in d["rooms"] if r.get("special") != "screen"]
        CW = d["size"][0]
        D = max(r["z1"] for r in rs) + 1
        mat = self.mat
        rich = self.fam["rich"]
        wall = rng.choice(rich["walls"]) if (yamen or temple or rng.random() < 0.6) else mat["wall"]
        theme = rng.choice(rich["themes"])
        for r in rs:
            r["walls"] = wall if not r.get("special") or r["special"] in ("wall", "gate", "chuihua") else r["walls"]
            r["theme"] = theme
            r["base"] = mat["base"]
            r["bay_style"] = mat["bay"]
            r.pop("_role", None)
            if not r.get("special"):                       # (the courts are tight: no galleries, low wings)
                r.update(overhang=1, veranda=False, back_veranda=False, colonnade=False,
                         podium=min(1, int(r.get("podium", 0) or 0)))
                if r["front"] in ("east", "west"):
                    r["storeys"] = 1
        halls = [r for r in rs if not r.get("special") and r["front"] == "south"]
        hall = max(halls, key=lambda r: (r["x1"] - r["x0"]) * (r["z1"] - r["z0"]))
        for r in rs:
            if not r.get("special"):                       # (who gives way: a side wing first, the main hall last)
                r["_rank"] = 3 if r is hall else 0 if r["front"] in ("east", "west") else 1
        c = CW // 2
        if temple:
            if self.fam.get("landmark"):
                hall["theme"] = rng.choice(self.fam["landmark"])
            hall.update(uses=[rng.choice(("temple_hall", "ancestral_hall"))] * 8, roof=rng.choice(("xieshan", "xieshan", "wudian")),
                        podium=rng.choice((1, 1, 2)), podium_style="plain", storeys=1, double_eave=rng.random() < 0.3, horsehead=False,
                        veranda=False, back_veranda=False, colonnade=False,
                        caihua=rng.choice(("", "xuanzi")), brackets=0)
            for r in rs:
                if not r.get("special") and r is not hall:
                    r["uses"] = [rng.choice(("guest_room", "storeroom", "meditation_room", "study"))] * 8
            fore = rng.choice((7, 9, 11))
            rs.append(room(c - 6, D, c + 6, D + fore - 1 - (fore % 2 == 0), rng, special="paving", walls=wall,
                           theme=theme, base=mat["base"]))
            oz = D + fore // 2
            for sx in (-1, 1):
                rs.append(room(c + sx * 5 - 1, oz - 1, c + sx * 5 + 1, oz + 1, rng, special="ornament", variant=ORN["ding"],
                               walls=wall, theme=theme, base=mat["base"]))
                if CW >= 33 and fore >= 7:                     # 古树 either side of the forecourt
                    rs.append(room(c + sx * 11 - 2, oz - 2, c + sx * 11 + 2, oz + 2, rng, special="ornament", variant=ORN["tree"],
                                   walls=wall, theme=theme, base=mat["base"]))
            D += fore
        elif yamen:
            for r in rs:
                if r.get("special"):
                    continue
                if r is hall:                              # 大堂: the court room, on a 台基, a hip-and-gable roof
                    r.update(uses=["courtroom"] * 8, roof="xieshan", podium=1, storeys=1, horsehead=False, caihua="xuanzi")
                elif r["front"] == "south":
                    r.update(uses=[rng.choice(("accounts", "main_room", "study"))] * 8, horsehead=False)
                else:
                    r.update(uses=[rng.choice(("guardroom", "jail", "accounts", "barracks", "storeroom"))] * 8)
            fore = 9
            rs.append(room(c - 7, D, c + 7, D + fore - 1, rng, special="paving", walls=wall, theme=theme, base=mat["base"]))
            for sx in (-1, 1):                             # 石狮 either side of the way in
                rs.append(room(c + sx * 5 - 1, D + 2, c + sx * 5 + 1, D + 4, rng, special="ornament", variant=ORN["lion"],
                               walls=wall, theme=theme, base=mat["base"], front="south"))
            D += fore
        return rs, CW, D

    def put_group(self, rs, facing, target, y):
        """Rooms laid out in their own coordinates (their front +z, "south") into the town: turned to face `facing`,
        the rectangle from their (0, 0) corner put with its low corner on `target` (x0, z0)."""
        k = (DIRS.index(facing) - DIRS.index("south")) % 4
        CW = max(r["x1"] for r in rs) + 1
        D = max(r["z1"] for r in rs) + 1
        bx0, bz0, _, _ = rot_rect((0, 0, CW - 1, D - 1), k)
        dx, dz = target[0] - bx0, target[1] - bz0
        out = []
        for r in rs:
            r = dict(r)
            x0, z0, x1, z1 = rot_rect((r["x0"], r["z0"], r["x1"], r["z1"]), k)
            r.update(x0=x0 + dx, x1=x1 + dx, z0=z0 + dz, z1=z1 + dz, y=y, front=turn(r.get("front", "south"), k))
            self.rooms.append(r)
            if not r.get("special"):
                self.buildings.append(r)
            out.append(r)
        return out

    def compound(self, s, u0, umax, temple=False, yamen=False):
        """A walled compound on a side of the street, its gate on the street (a little forecourt before it)."""
        rs, CW, D = self.compound_rooms(temple, yamen)
        fore = 2
        garden = self.rng.choice((0, 0, 9, 11, 13)) if not (temple or yamen) else 0   # (a rich house: a garden beside it)
        width = CW + 4 + garden
        u1 = u0 + width - 1
        if u1 > umax and garden:
            width -= garden
            garden = 0
            u1 = u0 + width - 1
        if u1 > umax:
            return None
        y = s.y(u0, u1)
        if max(p["y"] for p in s.st.span(u0, u1)) - min(p["y"] for p in s.st.span(u0, u1)) > 2:
            return None                                        # (a steep street: no room for a compound's one level)
        F, near = s.lines(u0, u1)
        need = fore + D + 1
        if self.depth_free(s, u0, u1, F, need) < need:
            return None
        self.put_group(rs, s.facing, s.rect(u0 + 2, u0 + 2 + CW - 1, F, fore, fore + D - 1), y)
        self.terrace(s, u0, u0 + CW + 3, F, near, None, need, y, variant=3 if (temple or yamen) else 2)
        # (paved: a 随墙门's sill is a block up - from a dirt path, 15/16 high, a jump too high)
        if garden:                                             # 花园: grass, a tree or two, flowers, a pond, bamboo
            g0 = u0 + CW + 4
            self.terrace(s, g0, u1, F, near, None, need, y, variant=1)
            self.yard_spot(s, g0 - 1, u1 + 1, F, 3, need - 2, y, 1.0, garden=True)
            self.yards[-1]["rich"] = True
        self.take(s.rect(u0, u1, F, 0, need))
        return u1

    def head_compound(self, st, a, side, temple=True, yamen=False):
        """A compound across the head of a T (one side of the cross street), its gate on the main street's axis."""
        rs, CW, D = self.compound_rooms(temple=temple, yamen=yamen)
        s = Side(st, side)
        u0 = a - CW // 2 - 2
        u1 = u0 + CW + 3
        F, near = s.lines(u0, u1)
        need = 2 + D + 1
        if self.depth_free(s, u0, u1, F, need) < need:
            return False
        y = s.y(u0, u1)
        self.put_group(rs, s.facing, s.rect(u0 + 2, u0 + 2 + CW - 1, F, 2, 2 + D - 1), y)
        self.terrace(s, u0, u1, F, near, None, need, y, variant=3)
        self.take(s.rect(u0, u1, F, 0, need))
        return True

    def end_compound(self, st, i, temple=True):
        """A temple across the end of a street, its gate on the street's axis: the street leads to it."""
        rs, CW, D = self.compound_rooms(temple=temple)
        p = st.pieces[i]
        last = i == len(st.pieces) - 1
        facing = {("z", False): "south", ("z", True): "north", ("x", False): "east", ("x", True): "west"}[(st.axis, last)]
        c0 = p["c"] - CW // 2
        if last:
            a0, a1 = p["a1"] + 3, p["a1"] + 3 + D - 1
            ap = (p["a1"] + 1, a1)
        else:
            a0, a1 = p["a0"] - 3 - D + 1, p["a0"] - 3
            ap = (a0, p["a0"] - 1)
        if st.axis == "z":
            rect, apron = (c0, a0, c0 + CW - 1, a1), (c0 - 3, ap[0], c0 + CW + 2, ap[1])
        else:
            rect, apron = (a0, c0, a1, c0 + CW - 1), (ap[0], c0 - 3, ap[1], c0 + CW + 2)
        if not self.free(apron):
            return False
        self.put_group(rs, facing, rect[:2], p["y"])
        self.ground("terrace", apron, p["y"], variant=3)
        self.take(apron)
        return True

    def square(self, s, u0, umax, small=False):
        """A little square on the street: paved, a well, stalls down both ends, a stage or a pavilion at the back (a
        village's: a well and a big tree)."""
        rng = self.rng
        if small:
            w, dp = odd(rng.randint(11, 15)), odd(rng.randint(9, 11))
        else:
            w, dp = odd(rng.randint(19, 27)), odd(rng.randint(13, 19))
        u1 = u0 + w + 3
        if u1 > umax:
            return None
        y = s.y(u0, u1)
        F, near = s.lines(u0, u1)
        if max(p["y"] for p in s.st.span(u0, u1)) - min(p["y"] for p in s.st.span(u0, u1)) > 2:
            return None
        D = self.depth_free(s, u0, u1, F, dp + 20)
        if D < dp + 2:
            return None
        self.ground("paving", s.rect(u0 + 2, u0 + 2 + w - 1, F, 0, dp - 1), y, front=s.facing)
        wu = u0 + 2 + w // 2 + rng.choice((-4, 4) if not small else (-3, 3))
        self.ornament("well", *s.xz(wu, F, dp // 2), y)
        back = dp
        if small:
            tu = u0 + 2 + w // 2 - (wu - (u0 + 2 + w // 2))
            self.ornament("tree", *s.xz(tu, F, dp // 2), y, half=2)
            self.terrace(s, u0, u1, F, near, None, back, y, variant=1)
            self.take(s.rect(u0, u1, F, 0, back))
            return u1
        lo_f, hi_f = s.ends()
        for ua, fr in ((u0 + 2, lo_f), (u0 + w - 1, hi_f)):   # 货摊 down both ends of the square, facing in
            for b in range(2, dp - 4, 4):
                if rng.random() < 0.75:
                    self.ground("ornament", s.rect(ua, ua + 2, F, b, b + 2), y, variant=ORN["stall"], front=fr)
        what = rng.choice(("stage", "pavilion", "pavilion", "none"))
        if what == "stage" and D >= dp + 1 + 9 + 5:            # 戏台: open to the square, up on a 台基
            sd = self.building("backstage", roof=rng.choice(("xieshan", "juanpeng")), podium=2, open="front", wealth=0.8)
            ss, sf, sb = margins(sd)
            sw = odd(min(w - 2 * ss + 4, rng.choice((11, 13))))
            b0 = dp + 1
            sx0 = u0 + 2 + (w - sw) // 2
            self.place(sd, s.rect(sx0, sx0 + sw - 1, F, b0, b0 + 8), y, s.facing)
            back = b0 + 9 + sb
        elif what == "pavilion" and D >= dp + 1 + 9 + 4:     # 亭: open all round, a pyramid roof
            pd = self.building("lookout", roof="pyramid", open="open", podium=1, wealth=0.8)
            ps, pf, pb = margins(pd)
            pc = rng.choice((7, 9))
            pu = u0 + 2 + (w // 2 - pc // 2) + rng.choice((-1, 1)) * (w // 4)
            pu = max(u0 + ps, min(u1 - ps - pc + 1, pu))
            b0 = dp + 1
            self.place(pd, s.rect(pu, pu + pc - 1, F, b0, b0 + pc - 1), y, s.facing, rank=1)
            back = b0 + pc + pb
        self.terrace(s, u0, u1, F, near, None, back, y, variant=1 if rng.random() < 0.5 else self.mat["yard"])
        self.take(s.rect(u0, u1, F, 0, back))
        return u1

    def gap(self, s, u0, umax):
        """An empty plot (a vegetable patch, a threshing floor) between two parcels."""
        rng = self.rng
        w = rng.randint(5, 9)
        u1 = min(umax, u0 + w - 1)
        if u1 - u0 < 2:
            return None
        y = s.y(u0, u1)
        F, near = s.lines(u0, u1)
        D = self.depth_free(s, u0, u1, F, rng.randint(8, 16))
        if D < 3:
            return None
        self.terrace(s, u0, u1, F, near, None, D, y, variant=1)
        self.take(s.rect(u0, u1, F, 0, D))
        if u1 - u0 >= 4 and D >= 6:
            self.yard_spot(s, u0, u1, F, 1, D, y, self.wealth(*s.xz(u0, F, 3)), garden=True)
        return u1

    # -------------------------------------------------------------------------------------------- filling

    def fill(self, s, a0=None, a1=None, homes_from=None):
        """Parcels all along one side of a street: shop rows (or houses towards the ends and on lanes, huts at the
        poor edges, courtyard houses in the rich middle), now and then a gap."""
        rng = self.rng
        st = s.st
        a0 = st.lo if a0 is None else a0
        a1 = st.hi if a1 is None else a1
        u = a0
        lane = st.kind in ("lane", "back")
        while u <= a1 - 4:
            umax = self.run_free(s, u, a1)
            if umax - u < 4:
                u = umax + 2
                continue
            kind = "row"
            dense = self.size == "big"
            if rng.random() < (0.07 if not lane else 0.1) * (0.3 if dense else 1) or len(self.buildings) >= self.C["most"]:
                kind = "gap"                                  # (a town of its size has all its houses: gardens on)
            F0, _ = s.lines(u, u)
            w = self.wealth(*s.xz(u, F0, 8)) + (0.15 if st.kind in ("main", "cross") else 0)
            homes = lane or (homes_from is not None and homes_from(u)) or w < 0.35
            if kind == "row" and self.ward(w) == "poor" and rng.random() < 0.45:
                kind = "hut"
            if kind == "row" and self.ward(w) == "rich" and (lane or homes) and rng.random() < 0.3 and self.size != "village":
                kind = "house"
            x1 = None
            if kind == "row":
                x1 = self.row(s, u, umax, homes=homes)
            elif kind == "hut":
                x1 = self.hut(s, u, umax)
            elif kind == "house":
                x1 = self.compound(s, u, umax)
            elif kind == "gap":
                x1 = self.gap(s, u, umax)
            if kind == "gap" and len(self.buildings) >= self.C["most"]:
                u = (x1 + 1) if x1 is not None else u + 2
                continue
            if x1 is None and kind not in ("row", "hut"):
                x1 = self.row(s, u, umax, homes=homes)
            if x1 is None:
                x1 = self.hut(s, u, umax)
            if x1 is None:
                x1 = self.gap(s, u, umax)
            if x1 is None:
                u += 2
                continue
            u = x1 + 1

    # -------------------------------------------------------------------------------------------- yards

    def furnish(self):
        """Back yards and gardens: a vegetable plot, a haystack, a woodpile, a well, a tree or bamboo - by the
        district (a rich yard a tree or flowers, a poor one vegetables and hay), each where nothing a building
        reaches over (its eaves, its steps) is, two clear of the streets and of the yard's ends along the street
        (the ways in), and a way round it."""
        rng = self.rng
        busy = set()
        for r in self.buildings:
            x0, z0, x1, z1 = reach_rect(r, 1)
            busy.update((x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1))
        for r in self.rooms:
            if r.get("special") in ("ornament", "paving", "gate", "wall", "paifang", "river"):
                busy.update((x, z) for x in range(r["x0"] - 1, r["x1"] + 2) for z in range(r["z0"] - 1, r["z1"] + 2))
        for k, v in self.claim.items():
            if v in ("street", "river", "wall"):
                busy.update((k[0] + dx, k[1] + dz) for dx in (-2, -1, 0, 1, 2) for dz in (-2, -1, 0, 1, 2))
        level = {}                                         # (the ground's levels as terrain_ground will lay them)
        ts = [r for r in self.rooms if r.get("special") == "terrace"]
        for r in sorted(ts, key=lambda r: -(r["x1"] - r["x0"] + 1) * (r["z1"] - r["z0"] + 1)):
            for c in self.cells((r["x0"], r["z0"], r["x1"], r["z1"])):
                level[c] = r["y"]
        for st in self.streets + self.rivers:
            for p in st.pieces:
                for c in self.cells(st.rect(p)):
                    level[c] = p["y"] - (100 if st.kind == "river" else 0)
        edge1, edge3 = set(), set()                        # (a cell next to a change of level, three from one: a
        for (x, z), y in level.items():                    # flight may be cut into the ground there)
            if any(level.get((x + dx, z + dz)) != y for dx in (-1, 0, 1) for dz in (-1, 0, 1)):
                edge1.update((x + dx, z + dz) for dx in (-1, 0, 1) for dz in (-1, 0, 1))
                edge3.update((x + dx, z + dz) for dx in range(-4, 5) for dz in range(-4, 5))
        placed = 0
        for yd in self.yards:
            s, F, (u0, u1), (d0, d1) = yd["s"], yd["F"], yd["u"], yd["d"]
            ward = self.ward(yd["w"])
            ok = {s.xz(u, F, d) for u in range(u0 + 2, u1 - 1) for d in range(d0, d1 + 1)} - busy - edge1
            kinds = {"poor": (("veg", 4), ("hay", 3), ("woodpile", 2), ("tree", 1), ("rack", 1)),
                     "mid": (("veg", 3), ("tree", 2), ("woodpile", 2), ("well", 1), ("hay", 1), ("bamboo", 1), ("rack", 1)),
                     "rich": (("tree", 4), ("flowers", 3), ("bamboo", 2), ("pond", 1), ("well", 1))}[ward]
            if yd.get("rich"):
                kinds = (("tree", 4), ("flowers", 3), ("pond", 2), ("bamboo", 2), ("lamp", 1))
            for _ in range(1 if yd.get("few") or self.size == "big" else 3 if yd["garden"] else rng.choice((1, 2))):
                kind = rng.choices([k for k, _ in kinds], weights=[w for _, w in kinds])[0]
                half = 2 if kind in ("tree", "pond", "veg", "flowers") else 1

                def fits(h, dig=False):                    # (a pond, a vegetable plot: dug into the ground, off the edges)
                    return sorted(c for c in ok if all((c[0] + dx, c[1] + dz) in ok for dx in range(-h, h + 1) for dz in range(-h, h + 1))
                                  and not (dig and c in edge3))
                cells = fits(half, kind in ("pond", "veg"))
                if not cells and half == 2:
                    kind = {"tree": "bamboo", "pond": "well", "veg": "hay", "flowers": "bamboo"}[kind]
                    half = 1
                    cells = fits(1)
                if not cells:
                    break
                cx, cz = rng.choice(cells)
                self.ornament(kind, cx, cz, yd["y"], half=half, front=s.facing)
                placed += 1
                gone = {(cx + dx, cz + dz) for dx in range(-half - 2, half + 3) for dz in range(-half - 2, half + 3)}
                ok -= gone                                   # (a way round each)
        return placed

    # -------------------------------------------------------------------------------------------- the whole town

    def plan(self):
        getattr(self, "net_" + self.layout)()
        if self.box:                                       # (rich in the middle, poor out at the edges of the box)
            self.radius = max(self.radius, 0.4 * max(self.box))
        self.landmarks()                                  # (before the lanes: a temple, a yamen want deep ground)
        for f in self.later:
            f()
        for st in sorted(self.streets, key=lambda st: KIND_ORDER.get(st.kind, 4)):
            for side in (-1, 1):
                self.fill(Side(st, side), homes_from=self.homes_rule(st))
        if self.walled:
            self.town_wall()
        else:                                                  # (the slots between parcels: ground too)
            cl = [c for c, t in self.claim.items() if t != "river"]
            self.plug((min(c[0] for c in cl), min(c[1] for c in cl), max(c[0] for c in cl), max(c[1] for c in cl)))
        self.furnish()
        self.lay_streets()
        self.clip_rivers()
        self.lay_rivers()
        self.merge_ground()
        self.fixed = resolve(self.rooms)
        return self.design()

    # -------------------------------------------------------------------------------------------- the wall

    def town_wall(self, margin=12):
        """城墙: a wall round the town (its outer face `margin` out from all it has claimed, a river aside), three thick
        on frames five deep, in pieces at the ground's level along it (over a river at the water's: a water gate);
        a gate where a street's end comes out towards it (the street run on through it and a little way out): a
        gate house the street runs through (a town), a tower on a 城台 the street runs under (a big town); the ground
        inside the wall that no parcel took laid out as gardens and fields."""
        rng = self.rng
        cl = [c for c, t in self.claim.items() if t != "river"]
        X0 = min(c[0] for c in cl) - margin
        X1 = max(c[0] for c in cl) + margin
        Z0 = min(c[1] for c in cl) - margin
        Z1 = max(c[1] for c in cl) + margin
        if (X1 - X0) % 2:
            X1 += 1
        if (Z1 - Z0) % 2:
            Z1 += 1
        # the wall follows the town's outline: in bands along z, each band's sides `margin` out from what it holds
        # (a band of the town that is narrower, a narrower wall; a step in the wall between two bands)
        nb = max(1, round((Z1 - Z0) / 36))
        cuts = [Z0 + round(k * (Z1 - Z0) / nb) for k in range(nb + 1)]
        bands = []
        for k in range(nb):
            zb0, zb1 = cuts[k] + (1 if k else 0), cuts[k + 1]
            xs = [c[0] for c in cl if zb0 - 10 <= c[1] <= zb1 + 10]
            bands.append([zb0, zb1, (min(xs) - margin) if xs else X0, (max(xs) + margin) if xs else X1])
        for _ in range(2):                                     # (steps of a few cells: none, the wider line)
            for a, b in zip(bands, bands[1:]):
                if abs(a[2] - b[2]) < 8:
                    a[2] = b[2] = min(a[2], b[2])
                if abs(a[3] - b[3]) < 8:
                    a[3] = b[3] = max(a[3], b[3])
        for name, st, i in self.ends:                          # (a street out east or west: the bands its gate stands
            if st.axis == "x":                                 # across, one line)
                zc = st.pieces[i]["c"]
                over = [b for b in bands if b[0] - 14 <= zc <= b[1] + 14]
                lo_w, hi_e = min(b[2] for b in over), max(b[3] for b in over)
                for b in over:
                    b[2], b[3] = lo_w, hi_e
        self.bands = bands
        X0, X1 = min(b[2] for b in bands), max(b[3] for b in bands)
        self.wall_rect = (X0, Z0, X1, Z1)

        def band_of(z):
            return next((k for k, b in enumerate(bands) if b[0] <= z <= b[1]), 0 if z < Z0 + 10 else len(bands) - 1)
        big = self.size == "big"
        wall_mat = dict(walls=rng.choice(("greybrick", "greybrick", "stone")) if self.fam["cn"] != "黄土" else "mud",
                        theme=rng.choice(("grey", "black")), base=rng.choice(("greybrick", "rubble")))
        if self.fam["cn"] == "山村":
            wall_mat["walls"] = "stone"
        sh = 9 if big else 7
        gaps = {"n": [], "s": []}
        for k in range(len(bands)):
            gaps[("w", k)], gaps[("e", k)] = [], []
        self.gates = []
        for name, st, i in self.ends:                          # the gates
            p = st.pieces[i]
            last = i == len(st.pieces) - 1
            out = 1 if last else -1
            end = p["a1"] if last else p["a0"]
            if st.axis == "z":
                wall_c = Z1 - 2 if out > 0 else Z0 + 2
                side = "s" if out > 0 else "n"
            else:
                bk = band_of(p["c"])
                wall_c = bands[bk][3] - 2 if out > 0 else bands[bk][2] + 2
                side = ("e", bk) if out > 0 else ("w", bk)
            if abs(wall_c - end) > 48:
                continue                                      # (the street ends well inside: no gate for it)
            g0, g1 = wall_c - 6, wall_c + 6                    # (the gate's ground: its 城台, or its steps)
            a_lo, a_hi = (end + 1, wall_c + 2 + 6) if out > 0 else (wall_c - 2 - 6, end - 1)
            ext = Street(st.axis, a_lo, a_hi, lambda a, c=p["c"]: c, st.w, st.kind, fixed=[(g0, g1)], maxlen=7)
            if not ext.pieces or not all(self.free(ext.rect(q)) for q in ext.pieces):
                continue
            near = 0 if out > 0 else len(ext.pieces) - 1
            gi = next(i for i, q in enumerate(ext.pieces) if q.get("fixed"))
            self.grade(ext, {near: p["y"], gi: p["y"]})        # (the gate at the street's level: its aprons too)
            gp = next(q for q in ext.pieces if q.get("fixed"))
            gy = gp["y"]
            tower = big and not any(self.claim.get(c) == "river" for c in self.cells(ext.rect(gp, 10 - st.hw)))
            half = 10 if tower else 5                          # (half the gate's width along the wall; by a river a
            if any(self.claim.get(c) not in (None, "river") for c in self.cells(ext.rect(gp, half - st.hw))):
                continue                                      # gate house, standing over the river's bank)
            self.add_street(ext)
            c = p["c"]
            front = {"s": "south", "n": "north", "e": "east", "w": "west"}[side if isinstance(side, str) else side[0]]
            if tower:                                          # 城门楼 on a 城台, the street through its 门洞
                d = self.building("lookout", storeys=rng.choice((1, 2)), roof=rng.choice(("xieshan", "xieshan", "wudian")),
                                  wealth=0.9, walls=wall_mat["walls"] if wall_mat["walls"] != "stone" else "greybrick",
                                  theme=wall_mat["theme"], podium=8, podium_style="chengtai", tunnels=2,
                                  double_eave=rng.random() < 0.5, base=wall_mat["base"], horsehead=False, veranda=False)
                d["uses"] = ["guardroom"] + ["lookout"] * 7
                fr = (c - 7, wall_c - 3, c + 7, wall_c + 3) if st.axis == "z" else (wall_c - 3, c - 7, wall_c + 3, c + 7)
            else:                                              # 门楼: a gate house the street runs through
                d = self.building("guardroom", storeys=rng.choice((1, 1, 2)), roof=rng.choice(("xieshan", "xieshan", "yingshan")),
                                  wealth=0.9, walls=wall_mat["walls"], theme=wall_mat["theme"], podium=1, open="through",
                                  base=wall_mat["base"], horsehead=False, veranda=False)
                fr = (c - 5, wall_c - 4, c + 5, wall_c + 4) if st.axis == "z" else (wall_c - 4, c - 5, wall_c + 4, c + 5)
            r = self.place(d, fr, gy, front, rank=5)
            r["_gate"] = True
            self.gates.append(r)
            gaps[side].append((c - half, c + half))
            for k in (-1, 1):                                  # aprons either side of the gate, at the gate's level
                a0, a1 = (g1 + 1, g1 + 2) if k > 0 else (g0 - 2, g0 - 1)
                rect = (c - half, a0, c + half, a1) if st.axis == "z" else (a0, c - half, a1, c + half)
                self.ground("terrace", rect, gy, variant=self.mat["street"] == 3 and 0 or 2)
                self.take(rect, "street")
            gr = (c - half, g0, c + half, g1) if st.axis == "z" else (g0, c - half, g1, c + half)
            self.ground("terrace", gr, gy, variant=2)
            self.take(gr, "wall")
        # the wall pieces, round the gates (and at a river's level where one crosses)
        runs = [("n", "x", Z0 + 2, bands[0][2], bands[0][3]), ("s", "x", Z1 - 2, bands[-1][2], bands[-1][3])]
        for k, (zb0, zb1, w, e) in enumerate(bands):           # (the side walls run on into the end walls' frames and the
            a0 = Z0 + 3 if k == 0 else zb0 - 3                 # steps', onto their bodies: no hole left at a corner)
            a1 = Z1 - 3 if k == len(bands) - 1 else zb1 + 3
            runs += [(("w", k), "z", w + 2, a0, a1), (("e", k), "z", e - 2, a0, a1)]
            if k + 1 < len(bands):                             # a step: a wall across from one band's line to the next's
                nb_ = bands[k + 1]
                if nb_[2] != w:
                    gaps[("sw", k)] = []
                    runs.append((("sw", k), "x", zb1 + 1, min(w, nb_[2]), max(w, nb_[2]) + 4))
                if nb_[3] != e:
                    gaps[("se", k)] = []
                    runs.append((("se", k), "x", zb1 + 1, min(e, nb_[3]) - 4, max(e, nb_[3])))
        for side, axis, cc, a0, a1 in runs:
            def at(a, k=0, axis=axis, cc=cc):
                return (a, cc + k) if axis == "x" else (cc + k, a)
            wet = []                                           # river crossings: (a0, a1, river y)
            a = a0
            while a <= a1:
                if any(self.claim.get(at(a, k)) == "river" for k in (-2, 0, 2)):
                    b = a
                    while b + 1 <= a1 and any(self.claim.get(at(b + 1, k)) == "river" for k in (-2, 0, 2)):
                        b += 1
                    ys = [self.river_y(at(t, k)) for t in range(a, b + 1) for k in (-2, 0, 2)]
                    wet.append((a, b, min(v for v in ys if v is not None)))
                    a = b + 1
                else:
                    a += 1
            pieces = []
            for p0, p1 in siteplan._gap_pieces(a0, a1, sorted(gaps[side] + [(w0, w1) for w0, w1, _ in wet])):
                if p1 - p0 < 2:
                    continue
                pieces += siteplan._split(p0, p1 + (0 if (p1 - p0) % 2 == 0 else 1), 31)   # (odd: a cell on into the gap,
                # or past the run's end into the corner, where the wall across runs: no hole left at the corner)
            for w0, w1, ry in wet:
                pieces.append((w0, w1 + (w1 - w0) % 2, ry))
            for pc in pieces:
                q0, q1 = pc[0], pc[1]
                if q1 < q0:
                    continue
                rect = (q0, cc - 2, q1, cc + 2) if axis == "x" else (cc - 2, q0, cc + 2, q1)
                if len(pc) == 3:
                    y = pc[2]
                else:
                    hs = [self.H(*at(t, k)) for t in range(q0, q1 + 1) for k in (-2, 0, 2)]
                    y = round(sum(hs) / len(hs) - 0.5)
                    self.ground("terrace", rect, y, variant=0)  # (its footing: masonry down to under the town)
                self.ground("wall", rect, y, front="south" if axis == "x" else "east", sh=sh, **wall_mat)
                self.take(rect, "wall")
        def inside(x, z):
            b = bands[band_of(z)]
            return Z0 + 5 <= z <= Z1 - 5 and b[2] + 5 <= x <= b[3] - 5 and \
                not any(abs(z - (bb[1] + 1)) <= 2 for bb in bands[:-1])
        self.fill_free((X0 + 5, Z0 + 5, X1 - 5, Z1 - 5), inside=inside)   # (fields: no wild ground left inside)
        self.plug((X0 + 5, Z0 + 5, X1 - 5, Z1 - 5))

    def plug(self, rect, reach=3):
        """Inside the wall: a strip of ground no one took between two that were (a cell or three wide - a slot down to
        the town's footings between two terraces, a hole in what walls a yard in) gets ground at the lower of the two
        levels either side, in runs."""
        X0, Z0, X1, Z1 = rect
        level = {}
        for r in self.rooms:
            if r.get("special") in ("terrace", "wall", "paving"):
                for c in self.cells((r["x0"], r["z0"], r["x1"], r["z1"])):
                    level.setdefault(c, r["y"])
        for st in self.streets + self.rivers:
            for p in st.pieces:
                for c in self.cells(st.rect(p)):
                    level[c] = p["y"]
        fill = {}
        for x in range(X0, X1 + 1):
            for z in range(Z0, Z1 + 1):
                if (x, z) in level or (x, z) in self.claim:
                    continue
                best = None
                for dx, dz in ((1, 0), (0, 1)):
                    a = next((level[(x + k * dx, z + k * dz)] for k in range(1, reach + 1) if (x + k * dx, z + k * dz) in level), None)
                    b = next((level[(x - k * dx, z - k * dz)] for k in range(1, reach + 1) if (x - k * dx, z - k * dz) in level), None)
                    if a is not None and b is not None:
                        y = min(a, b)
                        best = y if best is None else min(best, y)
                if best is not None:
                    fill[(x, z)] = best
        done = set()
        for (x, z), y in sorted(fill.items()):
            if (x, z) in done:
                continue
            z1 = z
            while fill.get((x, z1 + 1)) == y and (x, z1 + 1) not in done:
                z1 += 1
            x1 = x
            while all(fill.get((x1 + 1, zz)) == y and (x1 + 1, zz) not in done for zz in range(z, z1 + 1)):
                x1 += 1
            for c in self.cells((x, z, x1, z1)):
                done.add(c)
            self.ground("terrace", (x, z, x1, z1), y, variant=0)
            self.take((x, z, x1, z1))

    def river_y(self, c):
        for rv in self.rivers:
            for p in rv.pieces:
                x0, z0, x1, z1 = rv.rect(p)
                if x0 <= c[0] <= x1 and z0 <= c[1] <= z1:
                    return p["y"]
        return None

    def fill_free(self, rect, most=48, tol=1.5, inside=None):
        """The ground inside the wall no parcel took, as few 台地 as will do: from each free cell the biggest
        rectangle of free cells (at most `most` a side) over which the ground stays within `tol` of where it started,
        at its mean level - grass, now and then a vegetable plot, a haystack, a tree (furnish)."""
        rng = self.rng
        X0, Z0, X1, Z1 = rect
        cl = self.claim
        hgt = {}

        def h(x, z):
            v = hgt.get((x, z))
            if v is None:
                v = hgt[(x, z)] = self.H(x, z)
            return v
        fake = Side(_FIELD, -1)
        done = set()
        placed = []
        for z in range(Z0, Z1 + 1):
            for x in range(X0, X1 + 1):
                if (x, z) in cl or (x, z) in done or (inside and not inside(x, z)):
                    continue
                h0 = h(x, z)

                def ok(xx, zz):
                    return X0 <= xx <= X1 and zz <= Z1 and (xx, zz) not in cl and (xx, zz) not in done and \
                        abs(h(xx, zz) - h0) <= tol and (not inside or inside(xx, zz))
                x1 = x
                while x1 + 1 - x < most and ok(x1 + 1, z):
                    x1 += 1
                z1 = z
                while z1 + 1 - z < most and all(ok(xx, z1 + 1) for xx in range(x, x1 + 1)):
                    z1 += 1
                r = (x, z, x1, z1)
                cs = self.cells(r)
                done.update(cs)
                if len(cs) < 40:                               # (a pocket between parcels: left as it is - a slot
                    continue                                   # into a yard gets ground from plug)
                y = round(sum(h(*c) for c in cs) / len(cs))
                placed.extend(cs)
                self.ground("terrace", r, y, variant=1)
                if (x1 - x) >= 6 and (z1 - z) >= 6 and rng.random() < 0.5:
                    self.yards.append(dict(s=fake, F=z1, u=(x - 1, x1 + 1), d=(1, z1 - z - 1), y=y,
                                           w=0.2 if rng.random() < 0.7 else 0.5, garden=False, few=True))
        for c in placed:
            cl.setdefault(c, "plot")

    def fill_inside(self, rect, tile=9, near=True):
        """The ground inside the wall no parcel took: in tiles at the ground's level, run together along x where
        they are at one level - grass, a vegetable field, a tree now and then (furnish)."""
        rng = self.rng
        X0, Z0, X1, Z1 = rect
        fake = Side(_FIELD, -1)
        for z in range(Z0, Z1 - tile + 2, tile):
            row = []
            for x in range(X0, X1 - tile + 2, tile):
                r = (x, z, x + tile - 1, z + tile - 1)
                if not self.free(r) or near and not any(self.claim.get(c) in ("plot", "street") for c in
                                                        self.cells((x - 5, z - 5, x + tile + 4, z + tile + 4))):
                    row.append(None)                          # (taken; or far from the houses: left as it is)
                    continue
                hs = [self.H(xx, zz) for xx in range(x, x + tile, 3) for zz in range(z, z + tile, 3)]
                row.append(round(sum(hs) / len(hs)))
            i = 0
            while i < len(row):
                if row[i] is None:
                    i += 1
                    continue
                j = i
                while j + 1 < len(row) and row[j + 1] == row[i] and j - i < 14:
                    j += 1
                x0 = X0 + i * tile
                r = (x0, z, X0 + (j + 1) * tile - 1, z + tile - 1)
                y = row[i]
                self.ground("terrace", r, y, variant=1)
                self.take(r, "plot")
                if rng.random() < 0.4:                          # (now and then a vegetable plot, a haystack, a tree)
                    self.yards.append(dict(s=fake, F=z + tile - 1, u=(x0 - 1, r[2] + 1), d=(1, tile - 2), y=y,
                                           w=0.2 if rng.random() < 0.7 else 0.5, garden=False, few=True))
                i = j + 1

    def homes_rule(self, st):
        if st.kind != "main":
            return None
        span = st.hi - st.lo
        return lambda u: u < st.lo + span * 0.2 or u > st.hi - span * 0.12

    def landmarks(self):
        """The temple, the square, the inns where the street plan wants them; the entrance (and some far ends)
        under a 牌坊."""
        rng = self.rng
        main = self.main
        far = rng.choices(("paifang", "temple", "open"), weights=(35, 40, 25))[0]
        temple_done = self.size == "village"
        if getattr(self, "head", None) and self.size != "village":
            st, _ = self.head
            temple_done = self.head_compound(st, 0, -1, temple=True)
        elif self.layout == "Y":
            self.fork_tree()
        if not temple_done and far == "temple" and self.layout in ("line", "cross") and self.size != "big" and not self.box:
            temple_done = self.end_compound(main, 0, temple=True)
        span = 12 if self.W <= 5 else 14                     # the entrance: a 牌坊 over the street, ground round it
        for name, st, i in self.ends:
            if self.walled:                                   # (a walled town: gates instead)
                break
            if name == "entrance" or (name == "far" and far == "paifang"):
                p = st.pieces[i]
                a = p["a1"] - 3 if i == len(st.pieces) - 1 else p["a0"] + 3
                pc = st.piece_at(a)
                half = min(span, st.hw + 9)
                while half > st.hw + 4 and any(self.claim.get(c) == "river" for c in
                                               self.cells(st.rect(dict(a0=a - 3, a1=a + 3, c=pc["c"]), half + 3 - st.hw))):
                    half -= 1
                apron = st.rect(dict(a0=a - 3, a1=a + 3, c=pc["c"]), half + 3 - st.hw)
                if any(self.claim.get(c) == "river" for c in self.cells(apron)):
                    continue
                self.ground("terrace", apron, pc["y"], variant=self.mat["street"] == 3 and 0 or 2)
                self.take(apron, "street")
                self.ground("paifang", st.rect(dict(a0=a - 2, a1=a + 2, c=pc["c"]), half - st.hw), pc["y"],
                            front="south" if st.axis == "z" else "east", sh=rng.choice((6, 7, 8)))
        mid = (main.lo + main.hi) // 2
        if self.layout == "cross":
            mid = self.crossing[1]
        sides = [-1, 1]
        rng.shuffle(sides)
        if self.layout == "bank":
            sides = [1, 1]
        plans = {-1: [], 1: []}
        if not temple_done:
            plans[sides[0]].append((mid + rng.randint(-25, 10), "temple"))
        elif self.size == "village":
            plans[sides[0]].append((mid + rng.randint(-20, 20), "shrine"))
        plans[sides[1]].append((mid + rng.randint(-15, 15), "square"))
        if self.size == "big" or (self.size == "town" and rng.random() < 0.3):
            self.yamen()
        if not temple_done:
            for k, s in getattr(self, "grid_sites", ()):
                if k == "temple" and self.site_compound(s, temple=True):
                    temple_done = True
        for _ in range(rng.choice((1, 1, 2)) if self.size != "village" else rng.choice((0, 0, 1))):
            plans[rng.choice((-1, 1))].append((rng.randint(main.lo + 20, max(main.lo + 21, main.hi - 20)), "inn"))
        if self.size != "village":
            for side in (-1, 1):
                if rng.random() < 0.5:
                    plans[side].append((main.lo + 4, "house"))
        for side in (-1, 1):
            s = Side(main, side)
            for u, kind in sorted(plans[side]):
                for du in (0, -8, 8, -16, 16, -24, 24, -32, 32):
                    uu = max(main.lo + 2, min(main.hi - 10, u + du))
                    umax = self.run_free(s, uu, main.hi)
                    if umax - uu < 12:
                        continue
                    fn = {"temple": lambda: self.compound(s, uu, umax, temple=True),
                          "square": lambda: self.square(s, uu, umax, small=self.size == "village"),
                          "inn": lambda: self.inn(s, uu, umax), "shrine": lambda: self.shrine(s, uu, umax),
                          "house": lambda: self.compound(s, uu, umax)}[kind]
                    if fn() is not None:
                        break

    def yamen(self):
        """衙门: on a street along x, its north side (facing south) as near the middle as it fits; else on the main
        street."""
        cands = [s for k, s in getattr(self, "grid_sites", ()) if k == "yamen"] + \
            [Side(st, -1) for st in self.streets if st.axis == "x" and st.kind in ("cross", "main")] + \
            [Side(self.main, 1), Side(self.main, -1)]
        return any(self.site_compound(s, yamen=True) for s in cands)

    def site_compound(self, s, temple=False, yamen=False):
        """A compound on this side of the street, as near the middle of the town as it fits."""
        cx, cz = self.centre
        st = s.st
        mid = cx if st.axis == "x" else cz
        us = sorted(range(st.lo + 2, st.hi - 20, 2), key=lambda u: abs(u + 20 - mid))
        for u in us[:40]:
            umax = self.run_free(s, u, st.hi)
            if umax - u < 28:
                continue
            for _ in range(3):                                 # (a narrower one, another time)
                if self.compound(s, u, umax, temple=temple, yamen=yamen) is not None:
                    return True
        return False

    def fork_tree(self):
        """In the fork of a Y: a big tree at the point."""
        x0, z0, x1, z1 = self.fork["rect"]
        y = self.fork["y"]
        cx = (x0 + x1) // 2
        rect = (cx - 3, z0 - 6, cx + 3, z0 - 1)
        if self.free(rect):
            self.ornament("tree", cx, z0 - 3, y, half=2)
            self.ground("terrace", rect, y, variant=1)
            self.take(rect)

    def lay_streets(self):
        """Every street as 街道 pieces (pieces of one level and one line run together into one room)."""
        for st in self.streets:
            var = self.mat["street"] if st.kind in ("main", "cross", "branch", "bank") else self.mat["lane"]
            run = None
            for p in st.pieces + [None]:
                if run and p and p["y"] == run["y"] and p["c"] == run["c"] and p["a1"] - run["a0"] < 80:
                    run["a1"] = p["a1"]
                    continue
                if run:
                    self.ground("street", st.rect(run), run["y"], variant=var, front="south" if st.axis == "z" else "east")
                run = dict(p) if p else None
        if getattr(self, "fork", None):
            self.ground("street", self.fork["rect"], self.fork["y"], variant=self.mat["street"])

    def design(self):
        """The rooms as a design for the room table."""
        out = []
        for r in self.rooms:
            r = dict(r)
            for k in [k for k in r if k.startswith("_")]:
                r.pop(k)
            out.append(r)
        x0 = min(r["x0"] for r in out)
        x1 = max(r["x1"] for r in out)
        z0 = min(r["z0"] for r in out)
        z1 = max(r["z1"] for r in out)
        nb = sum(1 for r in self.rooms if not r.get("special") and not r.get("_gate"))
        cn = {"village": "村", "town": "镇", "big": "城"}[self.size]
        if GROUP:                                       # 地块: the ground pieces a few rooms (compose takes them apart)
            import compose
            out = compose.group_ground(out)
        return dict(name=f"凡人城镇·{self.fam['cn']}{cn}", kind="town", jin=0, seed=self.seed, tier="mortal", look=0,
                    size=[x1 - x0 + 1, z1 - z0 + 1], rooms=out, terrain=True, anchored=self.anchored, origin=[0, 0],
                    y_ref=self.main.piece_at(0)["y"], buildings=nb, town_size=self.size, layout=self.layout,
                    box=list(self.box) if self.box else None)


LEAST = {"village": 8, "town": 25, "big": 60}               # (fewer buildings than this: planned again, another way)


def town_box(box):
    """A box as asked for ("WxD", [W, D], (W, D); empty: none) as (W, D), both whole numbers above 0."""
    if not box:
        return None
    if isinstance(box, str):
        box = box.lower().replace("×", "x").replace("*", "x").split("x")
    w, d = (int(v) for v in box)
    if w <= 0 or d <= 0:
        raise ValueError(f"town box {w}x{d}: both sides above 0")
    return w, d


def town(seed, heights=None, size=None, layout=None, box=None):
    """The town for this seed; one with fewer buildings than its size wants (LEAST; in a box, by its area) is planned
    again from the seed's next draw (same size and street plan), up to five more times, and the one with most
    buildings kept. size, layout: None (by the seed) or one of CLASSES, LAYOUTS; box: None or W x D (town_box)."""
    box = town_box(box)
    size = size or None
    if size is not None and size not in CLASSES:
        raise ValueError(f"town size {size!r}: one of {', '.join(CLASSES)}")
    t = Town(seed, heights, size, layout, box=box)
    best = t.plan()
    for k in range(1, 6):
        if best["buildings"] >= t.least:
            break
        t2 = Town(seed, heights, t.size, t.layout, attempt=k, box=box)
        d = t2.plan()
        if d["buildings"] > best["buildings"]:
            best = d
    return best


if __name__ == "__main__":
    def arg(i):
        return sys.argv[i] if len(sys.argv) > i and sys.argv[i] != "-" else None
    d = town(int(sys.argv[1]) if len(sys.argv) > 1 else 1, size=arg(3), layout=arg(4), box=arg(5))
    out = sys.argv[2] if len(sys.argv) > 2 and sys.argv[2] != "-" else None
    print(d["name"], d["town_size"], d["layout"], len(d["rooms"]), "rooms,", d["buildings"], "buildings, size", d["size"])
    if out:
        with open(out, "w", encoding="utf-8") as f:
            json.dump(d, f, ensure_ascii=False, indent=1)
