"""Compose a building from rooms the player drew (the almighty-style mode of the build tool).

Each room: a rectangle on the ground (x0..x1, z0..z1 at floor y), storeys, roof form, wall style, roof colour, the use
of its ground floor and of its upper floors, and which side is its front. Every room is generated with bgen.house at
exactly that footprint, on its own canvas, then merged:

- joinparts.join: a room owns its column (inside its walls, plinth to roof top), so a lower wing's roof stops at the
  taller hall's wall and the hall's skirt roof and balcony stop over the wing; outside the footprints the roof zone of a
  column belongs to whichever room reaches highest there, so two eaves never sit on top of each other;
- every storey can have its own use ("uses": one per storey; household uses get a bay plan with companion rooms;
  "empty" (空房): nothing inside); "interior": false (内饰：无) builds every room without furniture and lamps;
- rooms that touch get doorways in the shared walls (every storey both rooms have; they share one storey height),
  placed clear of corners, front doors and stairs; furniture and partitions keep them free;
- touching rooms within two blocks of each other in height are put on one floor level;
- a room with an even width loses (or gains) a column on the side away from its neighbours, so walls meet.

Looks are cycled the way The Mighty Architect does it, by counters rather than fresh seeds, so stepping a counter back
returns the exact earlier look: "look" for the whole building, and per room "variant" (the room) and "roof_variant"
(only its roof), on top of the room's own "seed" (fixed when it is drawn, so reordering rooms changes nothing).
A room's "sh" (storey height in blocks) overrides the building's default; rooms of different storey heights only get
doorways on the ground floor.
"""
import json
import math
import random
from pathlib import Path

import bgen
import joinparts
import paths
from arch import ROT, Canvas, Frame
import arch

ROOFS = [("xieshan", "歇山"), ("wudian", "庑殿"), ("xuanshan", "悬山"), ("yingshan", "硬山"), ("juanpeng", "卷棚"), ("pyramid", "攒尖"),
         ("luding", "盝顶"), ("shizi", "十字脊"), ("yuanzan", "圆攒尖")]   # (圆攒尖: a round cone over a square plan)
WALLS = [(k, v["cn"], v.get("swatch", "")) for k, v in bgen.WALLS.items()]
THEMES = [(k, v["cn"], v.get("swatch", "")) for k, v in arch.ROOFS.items()]
USES = [("main_room", "堂屋"), ("bedroom", "卧房"), ("study", "书房"), ("kitchen", "厨房"), ("storeroom", "库房"),
        ("guest_room", "客房"), ("inn_hall", "客栈大堂"), ("teahouse", "茶馆"), ("private_dining", "雅间"), ("pharmacy", "药铺"),
        ("general_store", "杂货铺"), ("smithy", "铁匠铺"), ("ancestral_hall", "祠堂"), ("temple_hall", "神殿"),
        ("meditation_room", "静室"), ("seclusion", "闭关室"), ("alchemy_room", "丹房"), ("forging_room", "炼器室"),
        ("talisman_room", "符室"), ("formation_room", "阵室"), ("scripture_store", "藏经"), ("sword_gallery", "剑阁"),
        ("treasury", "宝库"), ("lecture_hall", "讲堂"), ("council_hall", "议事厅"), ("task_hall", "执事大厅"),
        ("throne_hall", "大殿（宝座）"), ("ancestor_tablets", "祖师牌位"), ("teleport_room", "传送阵室"), ("auction_hall", "拍卖厅"),
        ("refectory", "食堂"), ("outer_dorm", "弟子通铺"), ("inner_disciple_room", "弟子居室"), ("elder_quarters", "长老居所"),
        ("training_room", "演武室"), ("herb_store", "药材库"), ("beast_stable", "灵兽舍"), ("lookout", "观景台"),
        ("stele", "碑（碑亭）"),
        # the rest of the uses the build menu's kinds have (furniture.ROOMS), so each kind can be written as a design
        ("bedroom_north", "炕房"), ("maiden_room", "闺房"), ("master_study", "主人书斋"), ("accounts", "账房"),
        ("granary", "粮仓"), ("mill_room", "磨坊"), ("dyeing", "染坊"), ("brewery", "酿酒坊"), ("weaving", "织坊"),
        ("pottery", "陶坊"), ("tofu", "豆腐坊"), ("cloth_shop", "布庄"), ("pawnshop", "当铺"), ("bookshop", "书肆"),
        ("clinic", "医馆"), ("coffin_shop", "棺材铺"), ("gambling_den", "赌坊"), ("stable", "马厩"), ("backstage", "后台"),
        ("guardroom", "门房（守卫）"), ("barracks", "营房"), ("courtroom", "公堂"), ("jail", "牢房"), ("classroom", "学堂"),
        ("discipline_hall", "戒律堂"), ("array_control", "阵枢"), ("spirit_stone_vault", "灵石库"),
        ("artifact_shop", "法器铺"), ("pill_shop", "丹药铺"), ("talisman_shop", "符箓铺"), ("material_shop", "材料铺"),
        ("cultivator_inn_room", "修士客房"), ("auction_box", "拍卖雅间"), ("ship_cabin", "船舱"),
        ("empty", "空房")]   # (空房: no furniture, no lamps - bgen.EMPTY_ROOM)
SHOP_USES = ("inn_hall", "teahouse", "pharmacy", "general_store", "smithy", "tofu", "cloth_shop", "pawnshop", "bookshop",
             "clinic", "coffin_shop", "gambling_den", "artifact_shop", "pill_shop", "talisman_shop", "material_shop")   # 铺面: a shop front
FRONTS = [("south", "南"), ("east", "东"), ("north", "北"), ("west", "西")]
DOORS = [("front", "前"), ("front_back", "前后"), ("four", "四面")]      # 出入口: the sides with doors (and a flight down)
OPENS = [("closed", "封闭"), ("front", "正面敞开"), ("open", "全敞开"), ("through", "穿堂")]   # 开敞: walls, posts with a
# low rail, or 穿堂: the back doorway a passage up to the plate (no door) and the ground floor not partitioned
# an old save's one `open` -> (doors, open)
OLD_OPEN = {"closed": ("front", "closed"), "open": ("front", "open"), "front": ("front", "front"),
            "through": ("front_back", "through"), "back": ("front_back", "closed"), "four": ("four", "closed")}


def doors_open(r):
    """A room's (doors, open): the two rows, or the one `open` of an old save or preset translated. 穿堂 with the
    front door only is 前后 + 穿堂: a way through needs its way out behind (an old save's 穿堂 has no doors either)."""
    o = r.get("open") or "closed"
    if o in ("back", "four"):                             # 前后门, 四面门: the doors are in the old value
        return OLD_OPEN[o]
    d = r.get("doors") if r.get("doors") in dict(DOORS) else "front"
    o = o if o in dict(OPENS) else "closed"
    return ("front_back" if o == "through" and d == "front" else d), o
MAX_SIDE = 256         # the longest side of one room: only against a frame drawn by mistake (127 x 127 builds in 1.5 s, 199 x 99 in 2)

# A big room given a household use is split like a real house: the chosen use takes the front room, the rest get these.
# (Halls, workshops, stores and dormitories have their own big-room layouts and stay one room.)
COMPANIONS = {
    "main_room": ("bedroom", "study", "bedroom", "storeroom"),
    "bedroom": ("bedroom", "study", "storeroom"),
    "study": ("bedroom", "storeroom", "study"),
    "kitchen": ("storeroom",),
    "guest_room": ("guest_room",),
    "private_dining": ("private_dining",),
    "pharmacy": ("herb_store", "storeroom"),
    "general_store": ("storeroom",),
    "inner_disciple_room": ("inner_disciple_room",),
    "elder_quarters": ("meditation_room", "study", "storeroom"),
}


WALL_SLOTS = ("fill", "post", "beam", "base", "dado", "pane", "door", "win", "trim_stairs", "trim_slab", "trim_trapdoor",
              "base_stairs", "floor", "rail", "steps", "inner_stairs", "ladder")
ROOF_SLOTS = ("full", "stairs", "slab", "stripe_stairs", "stripe_slab", "ridge", "cap")


_LINKY = ("_fence", "_pane", "iron_bars", "_wall")
_NOT_SOLID = ("air", "stairs", "slab", "trapdoor", "door", "fence", "pane", "bars", "_wall", "button", "lantern", "chain",
              "torch", "carpet", "banner", "sign", "flower", "potted", "leaves", "ladder", "rail", "plate", "light[")


def connect_shapes(blocks):
    """Fences, panes, iron bars and walls joined to their neighbours, as the game does when a player places them
    (TMA's parts give plain states and TMA lets the world fix the shapes; our stamp does not). In place."""
    def family(st):
        n = st.split("[")[0]
        if n.endswith("_fence") or n.endswith("_fence_gate"):
            return "fence"
        if n.endswith("_pane") or n.endswith("iron_bars"):
            return "pane"
        if n.endswith("_wall"):
            return "wall"
        return None

    def solid(st):
        return st and not any(t in st for t in _NOT_SOLID)

    for (x, y, z), st in list(blocks.items()):
        fam = family(st)
        if not fam or st.split("[")[0].endswith("_fence_gate"):
            continue
        name = st.split("[")[0]
        props = dict(kv.split("=") for kv in st[st.index("[") + 1:-1].split(",")) if "[" in st else {}
        for d, (dx, dz) in (("north", (0, -1)), ("south", (0, 1)), ("east", (1, 0)), ("west", (-1, 0))):
            n = blocks.get((x + dx, y, z + dz))
            link = bool(n) and (family(n) == fam or (fam == "wall" and family(n) in ("pane", "fence") and False) or solid(n))
            props[d] = ("low" if link else "none") if fam == "wall" else ("true" if link else "false")
        if fam == "wall":
            props["up"] = "true"
        blocks[(x, y, z)] = name + "[" + ",".join(f"{k}={v}" for k, v in sorted(props.items())) + "]"


def _in_wall(blocks, x, y, z):
    """A pane where a wall steps diagonally: something solid (or another pane) on a diagonal next to it with nothing
    in the two cells between - the open corner a pane cannot reach across (not the end of a strip of panes in front of
    a wall, where one of those two cells is the wall's)."""
    def empty(c):
        return c is None or c == "minecraft:air"
    for dx, dz in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
        nb = blocks.get((x + dx, y, z + dz))
        if nb and (nb.split("[")[0].endswith("_pane") or not any(t in nb for t in _NOT_SOLID))                 and empty(blocks.get((x + dx, y, z))) and empty(blocks.get((x, y, z + dz))):
            return True
    return False


def connect_panes(blocks):
    """Glass panes and iron bars joined to their real neighbours (solid blocks and other panes), as the game would when
    placing them; a pane given only east-west in a wall that turns (a round tower) leaves a slit otherwise. A pane with
    fewer than two sides to join (where a round wall steps diagonally) would still leave slits beside it: it becomes the
    full glass block of its colour. In place."""
    for (x, y, z), st in list(blocks.items()):
        n = st.split("[")[0]
        if not (n.endswith("_pane") or n.endswith("iron_bars")):
            continue
        props = dict(kv.split("=") for kv in st[st.index("[") + 1:-1].split(",")) if "[" in st else {}
        for d, (dx, dz) in (("north", (0, -1)), ("south", (0, 1)), ("east", (1, 0)), ("west", (-1, 0))):
            nb = blocks.get((x + dx, y, z + dz))
            nn = (nb or "").split("[")[0]
            props[d] = "true" if nb and (nn.endswith("_pane") or nn.endswith("iron_bars") or not any(t in nb for t in _NOT_SOLID)) else "false"
        if n.endswith("_pane") and sum(props[d] == "true" for d in ("north", "south", "east", "west")) < 2 and _in_wall(blocks, x, y, z):
            blocks[(x, y, z)] = n[:-len("_pane")]
            continue
        blocks[(x, y, z)] = n + "[" + ",".join(f"{k}={v}" for k, v in sorted(props.items())) + "]"


def load_bases():
    """Base materials {id: dict(cn, blocks, swatch)} from data/materials/bases.json and run/bgen/materials/bases*.json."""
    import materials
    out = {}
    for d in (Path(__file__).parent / "data" / "materials", paths.RUN / "materials"):
        if not d.exists():
            continue
        for p in sorted(d.glob("bases*.json")):
            for k, e in json.loads(p.read_text(encoding="utf-8")).get("bases", {}).items():
                blocks = [b for b in e.get("blocks", []) if materials.present(b)]
                if k in ("auto", "wall") or blocks:
                    out[k] = dict(e, blocks=blocks)
    return out


def base_block(r, walls):
    """The block a room's base is made of, or None for the default stone (arch.masonry's mix)."""
    bid = r.get("base") or "auto"
    if bid == "auto":
        return None
    if bid == "wall":
        return bgen.WALLS.get(walls, {}).get("base")
    if _BASES[0] is None:                       # (read once a build: a town asks hundreds of times)
        _BASES[0] = load_bases()
    e = _BASES[0].get(bid)
    return arch.B(e["blocks"][0]) if e and e["blocks"] else None


_BASES = [None]


def options():
    """What the room table and the material board need: the lists, and the block in every slot of every style."""
    import parts as _parts
    if not _parts.BAYS:
        _parts.reload_bays()
    styles = []
    for e in _parts.BAYS.values():                 # the styles the bay parts name (the player's saved ones too)
        if e.get("style") and e["style"] not in styles:
            styles.append(e["style"])
    return dict(roofs=ROOFS, walls=WALLS, themes=THEMES, uses=USES, fronts=FRONTS,
                bay_styles=[["auto", "随机"]] + [[s, s] for s in styles],
                bases=[[k, e.get("cn", k), e.get("swatch", "")] for k, e in load_bases().items()],
                wall_slots={k: {s: v.get(s) for s in WALL_SLOTS if v.get(s)} for k, v in bgen.WALLS.items() if not k.startswith("_board_")},
                roof_slots={k: {s: v.get(s) for s in ROOF_SLOTS if v.get(s)} for k, v in arch.ROOFS.items() if not k.startswith("_board_")})


def _override(name, over, table, roof=False):
    """A style with some slots changed (the material board while it is being filled in): registered under a name of
    its own in this process only, so the preview shows the new blocks before they are saved."""
    if not over or name not in table:
        return name
    key = "_board_%s_%08x" % (name, hash(tuple(sorted(over.items()))) & 0xffffffff)
    if key not in table:
        e = dict(table[name])
        e.update({k: v for k, v in over.items() if k in (ROOF_SLOTS if roof else WALL_SLOTS)})
        if roof:
            e["tile"] = "roof:" + key
            arch.register_roof_sets({key: e})
        table[key] = e
    return key


def _overlap(a, b, lo, hi):
    return min(a[hi], b[hi]) - max(a[lo], b[lo]) >= 2


def _touch(a, b, lo, hi, olo, ohi):
    """b lies just past a's `hi` side (walls adjacent or on one line) and they share a stretch along the other axis."""
    return 0 <= b[lo] - a[hi] <= 1 and _overlap(a, b, olo, ohi)


def _local(room, x, z):
    a, b, c, d = ROT[room["front"]]
    dx, dz = x - room["cx"], z - room["cz"]
    return a * dx + c * dz, b * dx + d * dz


_DIRS4 = ((1, 0), (-1, 0), (0, 1), (0, -1))


def _podium_margin(r):
    """台基边距: how many cells of walk between the walls and the platform's edge (1-MAX_MARGIN: a 坛 or a 三台 far
    bigger than its hall), or None for 自动 (one, more where sills, hoods and lanterns by the door stand out into it -
    bgen.widen_podium)."""
    m = str(r.get("podium_margin", "")).strip()
    return min(int(m), MAX_MARGIN) if m.isdigit() and int(m) >= 1 else None


MAX_MARGIN = 32


def _pod(r):
    """The platform height in blocks (0-16: 12 at most as set, a few more when lifted to a neighbour's floor)."""
    return max(0, min(16, int(r.get("podium", 0) or 0)))


def _taper(r):
    return max(0, min(3, int(r.get("taper", 0) or 0)))


def _floor(r):
    """The height of a room's ground floor: its ground plus its 台基 (须弥座 and 三层 are taller than the number says)."""
    if r.get("shape") == "round":
        return r["y"] + _pod(r)
    return r["y"] + bgen.podium_height(_pod(r), r.get("podium_style"), 2 if r.get("colonnade") else 0)


def tower_cells(R):
    """A round room's disc and its wall ring, in its own (u, v): the same circle bgen.round_room builds."""
    rr = R + 0.5
    disc = {(u, v) for u in range(-R, R + 1) for v in range(-R, R + 1) if math.hypot(u, v) <= rr}
    wall = {c for c in disc if any((c[0] + du, c[1] + dv) not in disc for du, dv in _DIRS4)}
    return disc, wall


def _tower_doors(rooms):
    """圆塔 and the rooms it stands in or against. A tower standing in a room, or built against its wall, gets a door
    through its round wall into that room on every storey the two share (through the room's wall too when the tower
    stands outside it), on the side towards the middle of that room; no windows in the part of its wall that looks into
    another room; its own front door is dropped when it would open into another room (unless no door could be put
    anywhere else). The cell in front of a door inside the room is kept free of partitions and furniture."""
    def inside_rect(o, x, z):
        return o["x0"] <= x <= o["x1"] and o["z0"] <= z <= o["z1"]

    for t in rooms:
        if t.get("shape") != "round":
            continue
        R = t["hx"]
        disc, wall = tower_cells(R)
        A, Bm, C, Dm = ROT[t["front"]]

        def world(u, v):
            return t["cx"] + A * u + Bm * v, t["cz"] + C * u + Dm * v
        others = [o for o in rooms if o is not t]
        t["no_win"] = {c for c in wall for du, dv in _DIRS4
                       if (c[0] + du, c[1] + dv) not in disc and any(inside_rect(o, *world(c[0] + du, c[1] + dv)) for o in others)}
        found = False
        for h in others:
            if h.get("shape") == "round":
                continue
            if _floor(t) != _floor(h):                    # floors at different heights
                continue
            levels = min(t["n"], h["n"]) if t["sh"] == h["sh"] and not _taper(h) and not _taper(t) else 1   # (a tower set in: its
            # upper walls stand back from the room)
            reserve = bgen.stair_plan(h["hx"], h["hz"], h["sh"])["reserve"] if h["n"] > 1 else set()
            best = None
            for c in wall:
                outs = [d for d in _DIRS4 if (c[0] + d[0], c[1] + d[1]) not in disc]
                if len(outs) != 1:
                    continue
                du, dv = outs[0]
                if (c[0] + dv, c[1] + du) not in wall or (c[0] - dv, c[1] - du) not in wall:   # a straight bit of wall
                    continue
                if (c[0] - du, c[1] - dv) == (0, -R + 1):              # the ladder is just inside
                    continue
                if c[1] == R and abs(c[0]) <= 1 and not t["no_front"]:   # the front door
                    continue
                ex, ez = world(c[0] + du, c[1] + dv)
                if not inside_rect(h, ex, ez):
                    continue
                hu, hv = _local(h, ex, ez)
                if abs(hu) < h["hx"] and abs(hv) < h["hz"]:             # the tower stands in the room
                    if (hu, hv) in reserve:
                        continue
                    kind, cell = "free", (hu, hv)
                else:                                                   # against its wall: through that wall too
                    x2, z2 = world(c[0] + 2 * du, c[1] + 2 * dv)
                    iu, iv = _local(h, x2, z2)
                    if not (abs(iu) < h["hx"] and abs(iv) < h["hz"]) or not _door_ok(h, hu, hv):
                        continue
                    kind, cell = "wall", (hu, hv)
                score = (ex - h["cx"]) ** 2 + (ez - h["cz"]) ** 2
                if best is None or score < best[0]:
                    best = (score, c, kind, cell)
            if best:
                _, c, kind, cell = best
                t["side_doors"][c] = max(levels, t["side_doors"].get(c, 0))
                book = h.setdefault("free", {}) if kind == "free" else h["side_doors"]
                book[cell] = max(levels, book.get(cell, 0))
                found = True
        if t["no_front"] and not found:                  # nowhere else to get in: keep the front door after all
            t["no_front"] = False


def _wall_walks(rooms):
    """城墙 onto 城台: a wall piece (or a gate in one) 7 or more deep whose end meets the 城台 of a tower as high as its
    walk (the 宫城's outer wall and its 角楼, 神武门, 午门): the walk runs on onto the 城台's top, round the tower. Each
    such tower gets a doorway in the wall that faces the walk (side_doors; none where its front door is). Returns
    [(cells, y, door)]: the 城台's rim across the walk's lanes, its parapet there opened once the parts are joined, y the
    walk's floor (the paving's layer), door (x, z, dx, dz: the doorway, out of it; the tower's frame) or None - a door
    is hung in it then."""
    towers = [t for t in rooms if t.get("podium_style") == "chengtai" and _pod(t) > 0 and t["_on"] is None
              and t.get("shape") not in ("round",) + bgen.SPECIAL_SHAPES]
    out = []
    for r in rooms:
        if r.get("shape") not in ("wall", "gate", "glazedgate") or r["hz"] < bgen.THICK or r["_on"] is not None:
            continue
        ht = r["hz"] - max(0, min(_taper(r), r["hz"] - 3))   # (bgen.thick_wall: the parapets at +-ht, the walk between)
        y = r["y"] + r["sh"]
        a, b, c, d = ROT[r["front"]]
        for s in (-1, 1):
            for t in towers:
                m = bgen.chengtai_size(_pod(t), 0, _podium_margin(t))[0]
                rim = (t["x0"] - m, t["z0"] - m, t["x1"] + m, t["z1"] + m)
                for k in (0, 1):                          # the column past the end - or the piece's own last one, where
                    u = s * (r["hx"] + k)                 # it ran a cell on onto the 城台's rim (siteplan.wall_pieces)
                    cells = [(r["cx"] + a * u + b * v, r["cz"] + c * u + d * v) for v in range(-ht + 1, ht)]
                    if k == 1 or all(x in (rim[0], rim[2]) or z in (rim[1], rim[3]) for x, z in cells):
                        break
                if _floor(t) != y or not all(rim[0] <= x <= rim[2] and rim[1] <= z <= rim[3]
                                             and not (t["x0"] <= x <= t["x1"] and t["z0"] <= z <= t["z1"]) for x, z in cells):
                    continue
                lu, lv = _local(t, *cells[len(cells) // 2])   # the door: on the face the walk comes to, in line with it
                if abs(lu) > t["hx"]:                         # (never beside a corner post)
                    face = [((1 if lu > 0 else -1) * t["hx"], max(-t["hz"] + 2, min(t["hz"] - 2, lv + k))) for k in (0, 1, -1, 2, -2)]
                else:
                    face = [(max(-t["hx"] + 2, min(t["hx"] - 2, lu + k)), (1 if lv > 0 else -1) * t["hz"]) for k in (0, 1, -1, 2, -2)]
                door = None
                if not (face[0][1] == t["hz"] and abs(face[0][0]) <= 2 and not t.get("no_front")):   # (not its front door)
                    A, Bm, C, Dm = ROT[t["front"]]
                    for fu, fv in face:
                        if _door_ok(t, fu, fv):
                            t["side_doors"][(fu, fv)] = max(1, t["side_doors"].get((fu, fv), 0))
                            du, dv = ((1 if fu > 0 else -1), 0) if abs(fu) == t["hx"] else (0, (1 if fv > 0 else -1))
                            door = (t["cx"] + A * fu + Bm * fv, t["cz"] + C * fu + Dm * fv, A * du + Bm * dv, C * du + Dm * dv,
                                    (t["x0"], t["z0"], t["x1"], t["z1"]))
                            break
                out.append((cells, y, door))
                break
    return out


def _door_ok(room, u, v):
    hx, hz, n, sh = room["hx"], room["hz"], room["n"], room["sh"]
    if abs(u) == hx and abs(v) == hz:
        return False
    if v == hz and abs(u) <= 2 and not room.get("no_front"):   # the front door
        return False
    iu, iv = (u - (1 if u > 0 else -1), v) if abs(u) == hx else (u, v - (1 if v > 0 else -1))
    if n > 1 and (iu, iv) in bgen.stair_plan(hx, hz, sh)["reserve"]:   # stairs on this storey
        return False
    return True


class Superseded(Exception):
    """A preview being built was replaced by a newer one."""


CANCEL = lambda: None                    # the daemon sets this while it builds a preview: raises Superseded when it is stale
PROGRESS = lambda done, total: None      # the daemon sets this: rooms built so far, for the game's progress line
FLAT_SHAPES = ("paving", "river", "terrace", "street", "stairs", "bridge")
GROUND_SHAPES = ("terrace", "street", "stairs")    # 台地, 街道, 石阶: ground only (a level and a surface), laid by terrain_ground
COMPOUND_SHAPES = ("wall", "gate", "glazedgate", "chuihua", "paving", "river") + GROUND_SHAPES   # a design with one of these is a compound
PALACE_WALLS = ("palace", "yellow", "jade")


def compound_ground(cv, rooms):
    """院子的地: a compound (a design with walls, gates, pavements or a river) gets one ground over its whole rectangle -
    every column nothing has built in at the ground y gets a court surface there (grey bricks inside palace walls,
    packed earth otherwise) with masonry under it, which the game carries on down to solid ground, so dips are filled;
    the daemon clears every column above the ground (the compound rectangle is given to it as a base at the ground y),
    so a hill inside the walls is cut away. Without it the courts between the buildings were whatever the terrain was,
    and every building stood on its own stone block, at its own height above them. Returns the base entry
    ((x0, z0, x1, z1), ground y, None), or None for a design that is no compound."""
    ground = [r for r in rooms if r["_on"] is None]
    if not any(r.get("shape") in COMPOUND_SHAPES for r in ground):
        return None
    flat = [r["y"] for r in ground if r.get("shape") in bgen.SPECIAL_SHAPES]   # (specials are never levelled)
    g = max(set(flat), key=flat.count) if flat else min(r["y"] for r in ground)
    x0, x1 = min(r["x0"] for r in ground), max(r["x1"] for r in ground)
    z0, z1 = min(r["z0"] for r in ground), max(r["z1"] for r in ground)
    walls = [r.get("walls") for r in ground if r.get("shape") == "wall"] or [r.get("walls") for r in ground]
    paved = max(walls, key=walls.count) in PALACE_WALLS
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if (x, g, z) in cv.blocks or any(a <= g <= b and s != arch.AIR for a, b, s in cv.cols.get((x, z), ())):
                continue                                   # (something stands there or lies there: a river's air too)
            h = ((x * 73856093) ^ (z * 83492791)) % 100
            if paved:
                s = "minecraft:cracked_stone_bricks" if h < 8 else "minecraft:polished_andesite" if h < 14 else "minecraft:stone_bricks"
            else:
                s = "minecraft:coarse_dirt" if h < 12 else "minecraft:dirt_path"
            cv.set(x, g, z, s)
            cv.run(x, z, g - 3, g - 1, arch.masonry(x, 0, z))
    return ((x0, z0, x1, z1), g, None)
# 街道 by its variant: the field (block, weight) and the stone of the strip down its middle (None: none)
STREET_STYLES = [
    ([("stone_bricks", 50), ("polished_andesite", 20), ("cracked_stone_bricks", 12), ("andesite", 10), ("mossy_stone_bricks", 8)],
     "smooth_stone"),                                                   # 青石板
    ([("cobblestone", 45), ("mossy_cobblestone", 15), ("gravel", 12), ("andesite", 18), ("stone", 10)], "stone_bricks"),   # 毛石
    ([("bricks", 55), ("mud_bricks", 25), ("packed_mud", 10), ("terracotta", 10)], "stone_bricks"),          # 砖街
    ([("dirt_path", 55), ("coarse_dirt", 25), ("gravel", 12), ("rooted_dirt", 8)], "cobblestone"),           # 土路
]
# 台地 by its variant: what its ground is
TERRACE_STYLES = [
    [("dirt_path", 45), ("coarse_dirt", 30), ("packed_mud", 15), ("gravel", 10)],                   # 土院
    [("grass_block", 78), ("coarse_dirt", 10), ("dirt_path", 8), ("moss_block", 4)],                 # 草地
    [("cobblestone", 40), ("andesite", 25), ("gravel", 20), ("mossy_cobblestone", 15)],              # 石院
    [("bricks", 70), ("mud_bricks", 15), ("packed_mud", 15)],                                        # 砖院
    [("grass_block", 85), ("coarse_dirt", 8), ("moss_block", 7)],                                    # 坡岸: a bank (no steps up to it)
    [("grass_block", 80), ("podzol", 8), ("coarse_dirt", 6), ("moss_block", 6)],                    # 林: a grove (trees on it)
]
BANK = 4                                  # 台地 of this variant: the top of a retaining wall up a cut (宗门), never stepped onto
GROVE = 5                                 # 台地 of this variant: a grove - grass and trees, never stepped onto either


def _bank(r):
    return r.get("shape") == "terrace" and int(r.get("variant", 0) or 0) % len(TERRACE_STYLES) in (BANK, GROVE)


def plant_groves(cv, rooms, level, owner):
    """林: trees on every grove (a 台地 of variant GROVE) - five apart, a little off the grid, now and then one left
    out - each where its 5 x 5 is all that grove's ground at its level and nothing stands over its middle."""
    import random as _r
    for r in rooms:
        if r.get("shape") != "terrace" or int(r.get("variant", 0) or 0) % len(TERRACE_STYLES) != GROVE or "fp" not in r:
            continue
        x0, z0, x1, z1 = r["fp"]
        g = r["y"]
        for x in range(x0 + 2, x1 - 1, 5):
            for z in range(z0 + 2, z1 - 1, 5):
                h = _hash(x, z, 9)
                if h % 100 < 22:
                    continue
                cx = max(x0 + 2, min(x1 - 2, x + h % 3 - 1))
                cz = max(z0 + 2, min(z1 - 2, z + (h // 3) % 3 - 1))
                if any(level.get((cx + du, cz + dv)) != g or owner.get((cx + du, cz + dv)) is not r
                       for du in range(-2, 3) for dv in range(-2, 3)):
                    continue
                if any(cv.blocks.get((cx, yy, cz)) not in (None, arch.AIR) for yy in range(g + 1, g + 4)):
                    continue
                bgen.town_ornament(Frame(cv, cx, cz, "south"), g, _r.Random(h), "tree", 2, 2, None)
_GROUND_PRIO = {"terrace": 1, "paving": 2, "river": 2, "street": 3, "stairs": 4}
# 石阶: how far a cell is up the flight (rows from its front edge, the low end) and which way its steps face (up)
_RISE = {"south": (lambda x, z, f: f[3] - z, "north"), "north": (lambda x, z, f: z - f[1], "south"),
         "east": (lambda x, z, f: f[2] - x, "west"), "west": (lambda x, z, f: x - f[0], "east")}
_NAME4 = {(1, 0): "east", (-1, 0): "west", (0, 1): "south", (0, -1): "north"}


def _hash(x, z, salt=0):
    return ((x * 73856093) ^ (z * 83492791) ^ (salt * 19349663)) & 0x7fffffff


def _pick(table, x, z, salt=0):
    tot = sum(w for _, w in table)
    h = _hash(x, z, salt) % tot
    for b, w in table:
        if h < w:
            return "minecraft:" + b
        h -= w
    return "minecraft:" + table[0][0]


BRIDGE_PIER = 9                          # 石桥: a pier every this many cells along it


def build_bridges(cv, rooms, floor, ground):
    """石桥 (bridge): a stone deck at the room's y over its frame, nothing under it but piers - across the frame every
    BRIDGE_PIER cells along it and at both ends, down to the canvas's floor - with a corbel (an upside-down step) either
    side of each pier, a top slab under the rest of the deck (so the game does not carry a footing down from it), a
    balustrade along both long sides with a lantern on each pier. Its "front" is the way across it (east/west: along
    x). Its deck goes into `ground`; returns the columns that get no footing (all but the piers)."""
    nofoot = set()
    halls = [o for o in rooms if not o.get("shape") or o.get("shape") == "round"]

    def covered(x, z):                            # (a room built on the deck: a 廊桥)
        return any(o["x0"] <= x <= o["x1"] and o["z0"] <= z <= o["z1"] for o in halls)
    for r in rooms:
        if r.get("shape") != "bridge":
            continue
        x0, z0, x1, z1 = r["x0"], r["z0"], r["x1"], r["z1"]
        y = r["y"]
        along_x = r.get("front", "south") in ("east", "west")
        L = (x1 - x0) if along_x else (z1 - z0)
        Wd = (z1 - z0) if along_x else (x1 - x0)
        base = r.get("base_blk") or "minecraft:stone_bricks"
        field = STREET_STYLES[int(r.get("variant", 0) or 0) % len(STREET_STYLES)][0]
        rail = "minecraft:stone_brick_wall[up=true,%s]" % ("east=low,west=low,north=none,south=none" if along_x else
                                                          "north=low,south=low,east=none,west=none")
        n_p = max(1, round(L / BRIDGE_PIER))
        piers = {0, L} | {round(L * k / n_p) for k in range(1, n_p)}
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                t = (x - x0) if along_x else (z - z0)
                s = (z - z0) if along_x else (x - x0)
                cv.set(x, y, z, _pick(field, x, z, 5))
                ground[(x, z)] = y
                if t in piers:
                    cv.run(x, z, floor, y - 1, base)
                else:
                    nofoot.add((x, z))
                    cv.set(x, y - 1, z, "minecraft:stone_brick_slab[type=top,waterlogged=false]")
                    for d in (-1, 1):                     # a corbel out from the pier next to it
                        if t + d in piers and 0 < t + d < L:
                            face = ("west" if d > 0 else "east") if along_x else ("north" if d > 0 else "south")
                            face = {"west": "east", "east": "west", "north": "south", "south": "north"}[face]
                            cv.set(x, y - 2, z, f"minecraft:stone_brick_stairs[facing={face},half=top,shape=straight,waterlogged=false]")
                if s in (0, Wd) and Wd >= 4 and not covered(x, z):   # the balustrade (the ends stay open; none under a 廊桥)
                    if t in piers and 0 < t < L:
                        cv.set(x, y + 1, z, base)
                        cv.set(x, y + 2, z, "minecraft:lantern[hanging=false,waterlogged=false]")
                    else:
                        cv.set(x, y + 1, z, rail)
    return nofoot


def terrain_ground(cv, rooms):
    """城镇的地: ground at many levels. Every 台地 (terrace) and 街道 (street) frame is ground at its own y (a 街道 over a
    台地 where they overlap, a 铺地 or 河桥 over both, smaller pieces over bigger ones; under a building, a gate or a wall
    the ground is at its own y): its surface (a street's stones with a strip down the middle, a terrace's earth, grass,
    cobbles or bricks by its variant) on masonry (the room's base material) that goes down to under the lowest level,
    so where a higher terrace meets a lower one its ground is a retaining wall. Where two levels meet: a step (a stair
    on the low side) all along an edge one block high; on a higher edge a flight of steps three wide - built out on the
    low side, else cut into the high side - on every stretch of edge between two pieces of ground where one fits, so
    each piece can be walked to from the next. Columns something is built in at their ground y keep it (masonry under).
    Returns dict(ground={(x, z): y}, floor=the lowest y of the masonry)."""
    AIRB = arch.AIR
    level, owner = {}, {}
    grounds = [r for r in rooms if r["_on"] is None and r.get("shape") in _GROUND_PRIO and "fp" in r]

    def area(r):
        x0, z0, x1, z1 = r["fp"]
        return (x1 - x0 + 1) * (z1 - z0 + 1)
    for r in sorted(grounds, key=lambda r: (_GROUND_PRIO[r["shape"]], -area(r))):
        x0, z0, x1, z1 = r["fp"]
        rise = _RISE[r.get("front", "south")][0] if r["shape"] == "stairs" else None
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                level[(x, z)], owner[(x, z)] = r["y"] + (rise(x, z, r["fp"]) if rise else 0), r
    for r in rooms:                              # under a building (a gate, a wall): its own ground
        if r["_on"] is not None or r.get("shape") in FLAT_SHAPES or "fp" not in r:
            continue
        x0, z0, x1, z1 = r["fp"]
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if (x, z) in level:
                    level[(x, z)] = r["y"]
    if not level:
        return dict(ground={}, floor=min(r["y"] for r in rooms) - 3)
    floor = min(level.values()) - 3

    def solid_at(x, y, z):
        b = cv.blocks.get((x, y, z))
        if b is not None:
            return b != AIRB
        return any(a <= y <= c and st != AIRB for a, c, st in cv.cols.get((x, z), ()))

    def free(x, z, y0, y1):
        return not any(solid_at(x, y, z) for y in range(y0, y1 + 1))

    def base_of(r, x, y, z):
        return r.get("base_blk") or arch.masonry(x, y, z)

    def stair_block(r, facing, kind="stair"):
        if r.get("shape") in ("street", "stairs"):
            field = STREET_STYLES[int(r.get("variant", 0) or 0) % len(STREET_STYLES)][0]
            st = bgen.stairs_of("minecraft:" + field[0][0], "minecraft:stone_brick_stairs")
        else:
            st = bgen.stairs_of(r.get("base_blk"), "minecraft:stone_brick_stairs")
        return arch.step_block(kind, st, arch.slab_for(st), facing)

    stair_at = {}                                 # (x, z) -> (y, stair block, what fills under it)
    cut = {}                                      # (x, z) -> y of a step cut into a higher terrace (air over it)
    used = set()
    # 0. 石阶: a step on every row of a flight but its top one, facing up the flight
    for (x, z), r in owner.items():
        if r.get("shape") != "stairs":
            continue
        rise, face = _RISE[r.get("front", "south")]
        x0, z0, x1, z1 = r["fp"]
        top = (z1 - z0) if r.get("front", "south") in ("south", "north") else (x1 - x0)
        if rise(x, z, r["fp"]) < top and level[(x, z)] == r["y"] + rise(x, z, r["fp"]) and free(x, z, level[(x, z)] + 1, level[(x, z)] + 2):
            stair_at[(x, z)] = (level[(x, z)] + 1, stair_block(r, face), None)
            used.add((x, z))
    # 1. edges two or more high: a flight on every stretch of edge between two pieces of ground
    edges = {}
    for (x, z), ga in level.items():
        for (dx, dz) in _NAME4:
            b = (x + dx, z + dz)
            gb = level.get(b)
            if gb is None or ga - gb < 2 or owner[b].get("shape") == "river" or owner[(x, z)].get("shape") == "river"                     or _bank(owner[b]) or _bank(owner[(x, z)]):
                continue                          # (no flight down onto a river: its banks, its water)
            edges.setdefault((id(owner[(x, z)]), id(owner[b]), dx, dz), []).append((x, z))
    for (ia, ib, dx, dz), cells in edges.items():
        lat = (1, 0) if dx == 0 else (0, 1)       # along the edge
        cells = sorted(cells, key=lambda c: (c[0] * lat[1] + c[1] * lat[0], c[0] * lat[0] + c[1] * lat[1]))
        stretches, cur = [], [cells[0]]
        for c in cells[1:]:
            if (c[0] - cur[-1][0], c[1] - cur[-1][1]) == lat:
                cur.append(c)
            else:
                stretches.append(cur)
                cur = [c]
        stretches.append(cur)
        for st in stretches:
            if len(st) < 3:
                continue
            mid = (len(st) - 1) / 2
            for i in sorted(range(1, len(st) - 1), key=lambda i: (abs(i - mid), i)):
                a0 = st[i]
                ga, gb = level[a0], level[(a0[0] + dx, a0[1] + dz)]
                d = ga - gb
                ra, rb = owner[a0], owner[(a0[0] + dx, a0[1] + dz)]
                trio = [(a0[0] + t * lat[0], a0[1] + t * lat[1]) for t in (-1, 0, 1)]
                flt = arch.flight(d, arch.step_run(ra.get("step_slope")))   # 坡度: the higher ground's (its steps down)
                L = len(flt)

                def ok_low():                     # built out on the low side (and a cell to step off at its foot)
                    for (ax, az) in trio:
                        if level.get((ax, az)) != ga or not free(ax, az, ga + 1, ga + 2):
                            return False
                        for k in range(L + 1):
                            c = (ax + (k + 1) * dx, az + (k + 1) * dz)
                            if c in used or level.get(c) != gb or owner.get(c) is not rb:
                                return False
                            if not free(c[0], c[1], gb + 1, (ga - flt[k][1] if k < L else gb) + 2):
                                return False
                    return True

                def ok_cut():                     # cut into the high side
                    for (ax, az) in trio:
                        c = (ax + dx, az + dz)
                        if c in used or level.get(c) != gb or not free(c[0], c[1], gb + 1, gb + 2):
                            return False
                        for k in range(L + 1):
                            c = (ax - k * dx, az - k * dz)
                            if c in used or level.get(c) != ga or owner.get(c) is not ra or not free(c[0], c[1], ga + 1, ga + 2):
                                return False
                    return True
                face = _NAME4[(-dx, -dz)]
                if ok_low():
                    for (ax, az) in trio:
                        for k, dt, kind in flt:     # (from the top: next to the higher ground, out)
                            c = (ax + (k + 1) * dx, az + (k + 1) * dz)
                            stair_at[c] = (ga - dt, stair_block(rb, face, kind), base_of(rb, c[0], 0, c[1]))
                            used.add(c)
                        used.add((ax + (L + 1) * dx, az + (L + 1) * dz))
                        used.add((ax, az))
                    break
                if ok_cut():
                    for (ax, az) in trio:
                        for k, dt, kind in flt:     # (k out from the top of the flight: L - 1 - k in from the edge)
                            c = (ax - (L - 1 - k) * dx, az - (L - 1 - k) * dz)
                            cut[c] = ga - dt
                            stair_at[c] = (ga - dt, stair_block(ra, face, kind), None)
                            used.add(c)
                        used.add((ax + dx, az + dz))
                        used.add((ax - L * dx, az - L * dz))
                    break
    # 2. edges one high: a step all along them, on the low side
    for (x, z), ga in level.items():
        if (x, z) in cut:
            continue
        for (dx, dz) in _NAME4:
            b = (x + dx, z + dz)
            if level.get(b) != ga - 1 or b in used or b in stair_at or owner[b].get("shape") == "river" or _bank(owner[b])                     or _bank(owner[(x, z)]):
                continue
            gb = ga - 1
            if not free(b[0], b[1], gb + 1, gb + 2) or not free(x, z, ga + 1, ga + 1):
                continue
            stair_at[b] = (gb + 1, stair_block(owner[b], _NAME4[(-dx, -dz)]), None)
    # 3. the ground itself: masonry from three under the lowest ground next to it (the game carries it on down to solid
    # ground) - not from under the lowest level of all, which on a mountain would be a hundred blocks of it a column
    # (only where the ground's levels are far apart - a mountain; a town keeps one floor under all of it, so every bit
    # of its ground stands on something that reaches the bottom, even a strip on its own)
    deep = max(level.values()) - min(level.values()) > 24

    down = set()                                  # (deep: a piece of ground cut off from the rest - a strip between
    if deep:                                      # two walls, a patch by the town wall - has its lowest column on down
        seen = set()                              # to the floor, so it stands on something too)
        for c0 in level:
            if c0 in seen:
                continue
            comp, todo = [c0], [c0]
            seen.add(c0)
            while todo:
                x, z = todo.pop()
                for c in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)):
                    if c in level and c not in seen:
                        seen.add(c)
                        comp.append(c)
                        todo.append(c)
            down.add(min(comp, key=lambda c: (level[c], c)))

    def floor_at(x, z, g):
        if not deep or (x, z) in down:
            return floor
        return min([g] + [level[c] for c in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)) if c in level]) - 3

    for (x, z), g in level.items():
        r = owner[(x, z)]
        fl = floor_at(x, z, g)
        if r.get("shape") == "river":             # 河: the channel built itself (banks, water, bed three down)
            cv.run(x, z, min(fl, g - 7), g - 4, base_of(r, x, 0, z))
            continue
        if (x, z) in cut:
            for y in range(cut[(x, z)] + 1, g + 1):
                cv.set(x, y, z, AIRB)
            cv.run(x, z, min(fl, cut[(x, z)] - 4), cut[(x, z)] - 1, base_of(r, x, 0, z))
            continue
        if solid_at(x, g, z):                     # something built there at its ground y: masonry under it only
            cv.run(x, z, fl, g - 1, base_of(r, x, 0, z))
            continue
        shape = r.get("shape")
        var = int(r.get("variant", 0) or 0)
        if shape == "stairs":
            sb = _pick(STREET_STYLES[var % len(STREET_STYLES)][0], x, z, 1)
        elif shape == "street":
            field, strip = STREET_STYLES[var % len(STREET_STYLES)]
            x0, z0, x1, z1 = r["fp"]
            narrow = min(x1 - x0, z1 - z0) + 1
            off = abs(z - (z0 + z1) // 2) if (x1 - x0) >= (z1 - z0) else abs(x - (x0 + x1) // 2)
            sb = "minecraft:" + strip if strip and narrow >= 5 and off <= (1 if narrow >= 7 else 0) else _pick(field, x, z, 1)
        else:
            sb = _pick(TERRACE_STYLES[var % len(TERRACE_STYLES)], x, z, 2)
        cv.set(x, g, z, sb)
        top = g - 1
        if sb.endswith("grass_block") or sb.endswith("moss_block"):
            cv.set(x, g - 1, z, "minecraft:dirt")
            top = g - 2
        lo = min(level.get((x + dx, z + dz), g) for dx, dz in _NAME4)
        if lo < top:                              # a retaining wall's face: course by course
            cv.run(x, z, fl, lo, base_of(r, x, 0, z))
            for y in range(lo + 1, top + 1):
                cv.set(x, y, z, base_of(r, x, y, z))
        else:
            cv.run(x, z, fl, top, base_of(r, x, 0, z))
    # 4. the steps (on the ground)
    for (x, z), (y, blk, fill) in stair_at.items():
        if (x, z) not in cut:
            for yy in range(level[(x, z)] + 1, y):
                cv.set(x, yy, z, fill or base_of(owner[(x, z)], x, yy, z))
        cv.set(x, y, z, blk)
    plant_groves(cv, rooms, level, owner)  # 林
    return dict(ground=level, floor=floor)


LEVEL_UP = 4                              # touching rooms with floors this close share the highest one (and a doorway)          # shapes that lie on the ground: no wall in front of a door, no column of their own


def _ladder_corner(r):
    """Where the ladder up into a stacked room comes up (its own coords): a corner its own stairs up (when it has
    storeys) leave free."""
    hx, hz = r["hx"], r["hz"]
    busy = set()
    if r["n"] > 1:
        sp = bgen.stair_plan(hx, hz, r["sh"])
        busy = sp["reserve"] | sp["well"] | sp["above"]
    return next((c for c in ((-hx + 1, -hz + 1), (hx - 1, -hz + 1), (hx - 1, hz - 1), (-hx + 1, hz - 1)) if c not in busy),
                (-hx + 1, -hz + 1))


def _ladder_free(rooms, by_i):
    """The ladder into a stacked room and the cell you step off it onto kept free of furniture and partitions, in the
    room (its first storey) and in the room under it (its top storey, where you get on)."""
    for r in rooms:
        if r["_on"] is None or r.get("shape") in bgen.SPECIAL_SHAPES or r.get("shape") == "round":
            continue
        cu, cvv = _ladder_corner(r)
        A, Bm, C, Dm = ROT[r["front"]]
        for u, v in ((cu, cvv), (cu, cvv + (1 if cvv < 0 else -1)), (cu + (1 if cu < 0 else -1), cvv)):
            free = r.setdefault("free", {})
            free[(u, v)] = max(1, free.get((u, v), 0))
            low = by_i[r["_on"]]
            lu, lv = _local(low, r["cx"] + A * u + Bm * v, r["cz"] + C * u + Dm * v)
            if abs(lu) < low["hx"] and abs(lv) < low["hz"] and low["n"] == 1:   # (kept free on every storey up to then:
                # only where that is the top one, else it would wipe the stairs below)
                lf = low.setdefault("free", {})
                lf[(lu, lv)] = max(low["n"], lf.get((lu, lv), 0))


def _stack_ladder(cv, r, low, up):
    """A ladder up into a stacked room: in its back-left corner (inside its walls), from the floor of the top storey of
    the room under it up through the ceiling and the upper floor, on a post under the upper room's wall line."""
    lo = low["res"]["floor"] + (low["n"] - 1) * low["res"]["sh"] + 1
    hi = up["res"]["floor"]
    if hi < lo:
        return
    if r.get("shape") == "round":
        x, z, px, pz, facing = r["cx"], r["cz"] - r["hx"] + 1, r["cx"], r["cz"] - r["hx"], "south"
    else:
        cu, cvv = _ladder_corner(r)
        A, Bm, C, Dm = ROT[r["front"]]
        x, z = r["cx"] + A * cu + Bm * cvv, r["cz"] + C * cu + Dm * cvv
        pu = cu + (1 if cu > 0 else -1)
        px, pz = r["cx"] + A * pu + Bm * cvv, r["cz"] + C * pu + Dm * cvv
        facing = arch._world_dir(r["front"], "west" if cu > 0 else "east")
    post = (bgen.WALLS.get(low.get("walls")) or {}).get("post") or "minecraft:stripped_spruce_log[axis=y]"
    for y in range(lo, hi + 1):
        if cv.blocks.get((px, y, pz)) in (None, "minecraft:air"):
            cv.set(px, y, pz, post)                  # (where the lower room has nothing there: its wall or floor stays)
        cv.set(x, y, z, f"minecraft:ladder[facing={facing},waterlogged=false]")
    for y in (hi + 1, hi + 2):                       # nothing standing on the hatch
        if cv.blocks.get((x, y, z)) not in (None, "minecraft:air") and "wall" not in str(cv.blocks.get((x, y, z))):
            cv.set(x, y, z, "minecraft:air")


# ------------------------------------------------------------------------------------------------ 地块 (patches)

PATCH_SHAPES = ("terrace", "street", "stairs", "paving", "river", "bridge")   # what a 地块 may hold
PATCH_KEYS = ("variant", "walls", "theme", "base", "seed", "sh", "roof_variant")
_FRONT_I = {"south": 0, "east": 1, "north": 2, "west": 3}
_I_FRONT = ("south", "east", "north", "west")


def group_ground(rooms, most=250):
    """地块: the ground pieces of a design (台地, 街道, 石阶, 铺地, 河, 石桥) gathered into a few rooms of their own - each
    one room of the room table, its frame round its pieces (at most `most` a side), the pieces inside it as "parts"
    (each in the frame's own coordinates: across from its west edge, down from its north edge, as drawn facing south;
    the levels from the frame's y). compose takes them apart again before anything else (expand_rooms), so what gets
    built is the same. A big town or a 化神 sect is a few hundred ground pieces: this way they are a dozen or two
    rooms. Returns the rooms, the others as they were, each 地块 where its first piece was."""
    out, groups = [], []
    for r in rooms:
        if (r.get("special") or r.get("shape")) not in PATCH_SHAPES or r.get("parts"):
            out.append(r)
            continue
        x0, z0, x1, z1 = min(r["x0"], r["x1"]), min(r["z0"], r["z1"]), max(r["x0"], r["x1"]), max(r["z0"], r["z1"])
        for g in groups:
            a0, b0, a1, b1 = min(g["bb"][0], x0), min(g["bb"][1], z0), max(g["bb"][2], x1), max(g["bb"][3], z1)
            if a1 - a0 < most and b1 - b0 < most:
                g["bb"] = (a0, b0, a1, b1)
                g["rooms"].append(r)
                break
        else:
            g = dict(bb=(x0, z0, x1, z1), rooms=[r], at=len(out))
            groups.append(g)
            out.append(g)
    res = []
    for item in out:
        if "bb" not in item:
            res.append(item)
            continue
        if len(item["rooms"]) == 1:
            res.append(item["rooms"][0])
            continue
        X0, Z0, X1, Z1 = item["bb"]
        y = min(p["y"] for p in item["rooms"])
        parts = []
        for p in item["rooms"]:
            q = dict(s=p.get("special") or p.get("shape"), a0=min(p["x0"], p["x1"]) - X0, b0=min(p["z0"], p["z1"]) - Z0,
                     a1=max(p["x0"], p["x1"]) - X0, b1=max(p["z0"], p["z1"]) - Z0, dy=p["y"] - y, f=_FRONT_I.get(p.get("front", "south"), 0))
            for k in PATCH_KEYS:
                if p.get(k) not in (None, "", 0):
                    q[k] = p[k]
            parts.append(q)
        first = item["rooms"][0]
        res.append(dict(x0=X0, z0=Z0, x1=X1, z1=Z1, y=y, special="patch", shape="patch", front="south", parts=parts,
                        storeys=1, roof="xieshan", walls=first.get("walls", "greybrick"), theme=first.get("theme", "grey"),
                        base=first.get("base", "auto"), seed=first.get("seed", 0), variant=0, roof_variant=0, uses=["main_room"] * 8,
                        sh=0, podium=0, podium_style="plain", open="closed", round=False, stack=False))
    return res


def expand_rooms(rooms):
    """The 地块 among the rooms taken apart into their pieces again (where the 地块 is now: its frame, its y, which way the
    game turned it - its front), in its place in the list; every other room as it is."""
    out = []
    for r in rooms:
        parts = r.get("parts")
        if (r.get("shape") or r.get("special")) != "patch" or not parts:
            out.append(r)
            continue
        if isinstance(parts, str):
            parts = json.loads(parts)
        x0, x1 = sorted((int(r["x0"]), int(r["x1"])))
        z0, z1 = sorted((int(r["z0"]), int(r["z1"])))
        k = _FRONT_I.get(r.get("front", "south"), 0)

        def at(a, b):                                  # (the frame's own (a, b) where the game has turned it to)
            return ((x0 + a, z0 + b), (x0 + b, z1 - a), (x1 - a, z1 - b), (x1 - b, z0 + a))[k]
        for q in parts:
            p0, p1 = at(q["a0"], q["b0"]), at(q["a1"], q["b1"])
            d = dict(x0=min(p0[0], p1[0]), z0=min(p0[1], p1[1]), x1=max(p0[0], p1[0]), z1=max(p0[1], p1[1]),
                     y=int(r["y"]) + int(q.get("dy", 0)), special=q["s"], shape=q["s"], front=_I_FRONT[(int(q.get("f", 0)) + k) % 4],
                     storeys=1, roof="xieshan", uses=["main_room"] * 8, podium=0, podium_style="plain", open="closed",
                     round=False, stack=False, variant=0, roof_variant=0, sh=0, walls="greybrick", theme="grey", base="auto")
            for kk in PATCH_KEYS:
                if kk in q:
                    d[kk] = q[kk]
            out.append(d)
    return out


def build_compose(req, cv):
    tier = req.get("tier", "mortal")
    seed = int(req.get("seed", 0))
    look = int(req.get("look", 0))
    _BASES[0] = None                                    # (the base materials as they are now)
    k = bgen.TIER_K[tier]
    rng0 = random.Random(f"compose/{seed}")
    base = bgen.K["inner_residence"]
    sh0 = rng0.randint(*base["sh"])
    sh = int(round(sh0 * (1 + (k - 1) * 0.35)))         # what bgen.house makes of it
    req["rooms"] = expand_rooms(req["rooms"])           # 地块: their pieces (the daemon clears over the pieces' frames, not theirs)
    rooms = []
    for i, r in enumerate(req["rooms"]):
        x0, x1 = sorted((int(r["x0"]), int(r["x1"])))
        z0, z1 = sorted((int(r["z0"]), int(r["z1"])))
        if x1 - x0 + 1 > MAX_SIDE or z1 - z0 + 1 > MAX_SIDE:
            raise ValueError(f"房间 {i + 1} 太大（{x1 - x0 + 1}×{z1 - z0 + 1}）：每边最多 {MAX_SIDE} 格")
        rooms.append(dict(r, x0=x0, x1=x1, z0=z0, z1=z1, y=int(r["y"]), n=max(1, min(8, int(r.get("storeys", 1)))),
                          front=r.get("front", "south"), _i=i))
    # 叠放: a stacked room stands on the last room before it whose walls hold its middle (rooms on rooms on rooms too)
    for r in rooms:
        r["_on"] = None
        if r.get("stack") and r.get("shape") not in bgen.SPECIAL_SHAPES:
            mx, mz = (r["x0"] + r["x1"]) / 2, (r["z0"] + r["z1"]) / 2
            under = [o for o in rooms[:r["_i"]] if o["x0"] <= mx <= o["x1"] and o["z0"] <= mz <= o["z1"]
                     and o.get("shape") not in bgen.SPECIAL_SHAPES]
            if under:
                r["_on"] = under[-1]["_i"]
    by_i = {r["_i"]: r for r in rooms}

    def depth(r):
        return 0 if r["_on"] is None else 1 + depth(by_i[r["_on"]])
    rooms.sort(key=lambda r: (depth(r), r.get("shape") not in bgen.SPECIAL_SHAPES, r["n"]))
    # (what a room stands on first; walls, pavements and gates before the houses, so a house wins where they meet;
    # lower first, taller rooms win the eaves between)
    # odd widths: an even one drops the column away from its neighbours (or grows into a neighbour's wall line)
    for r in rooms:
        if r.get("shape") in GROUND_SHAPES:            # (ground has no walls to line up: it keeps its frame)
            continue
        for lo, hi, olo, ohi in (("x0", "x1", "z0", "z1"), ("z0", "z1", "x0", "x1")):
            if (r[hi] - r[lo]) % 2 == 0:
                continue
            if r.get("shape") == "wall":               # 院墙: an end never pulls back from what it runs up to (the column
                # it gave up was a slot to walk through beside the gate house); near(c): what stands on column c beside it
                def near(c):                           # (or at its corner)
                    return [o for o in rooms if o is not r and o.get("shape") not in FLAT_SHAPES and o[lo] <= c <= o[hi]
                            and min(o[ohi], r[ohi]) - max(o[olo], r[olo]) >= -1]
                if any(o[olo] <= r[olo] and r[ohi] <= o[ohi] for o in near(r[hi])):
                    r[hi] -= 1                         # (that end is inside something that covers it: drop it there)
                elif any(o[olo] <= r[olo] and r[ohi] <= o[ohi] for o in near(r[lo])):
                    r[lo] += 1
                else:                                  # grown into what it meets, a house first (it wins where they overlap)
                    grow = [(all(o.get("shape") in bgen.SPECIAL_SHAPES for o in near(c)), i, k, step)
                            for i, (k, c, step) in enumerate(((hi, r[hi] + 1, 1), (lo, r[lo] - 1, -1))) if near(c)]
                    if grow:
                        _, _, k, step = min(grow)
                        r[k] += step
                    else:
                        r[hi] -= 1                     # (a free end)
                continue
            g_hi = [o[lo] - r[hi] for o in rooms if o is not r and _touch(r, o, lo, hi, olo, ohi)]
            g_lo = [r[lo] - o[hi] for o in rooms if o is not r and _touch(o, r, lo, hi, olo, ohi)]
            if g_hi and g_lo:                  # neighbours on both sides: grow into a wall line we can share
                if min(g_hi) == 1:
                    r[hi] += 1
                elif min(g_lo) == 1:
                    r[lo] -= 1
                else:
                    r[hi] -= 1
            elif g_hi:
                r[lo] += 1
            else:
                r[hi] -= 1
    # at least 5 across (a frame is never narrower: a 3-deep room was drawn 5 deep and its wall landed on the
    # neighbour's, over the doorway between them - 千秋亭's wings walled off the middle): grown away from the neighbours
    for r in rooms:
        if r.get("shape") in bgen.SPECIAL_SHAPES or r["_on"] is not None:
            continue
        for lo, hi, olo, ohi in (("x0", "x1", "z0", "z1"), ("z0", "z1", "x0", "x1")):
            while r[hi] - r[lo] < 4:
                if any(_touch(r, o, lo, hi, olo, ohi) for o in rooms if o is not r):
                    r[lo] -= 1
                else:
                    r[hi] += 1
    # touching rooms close in height share one floor level (the higher one; the plinth fills under the other)
    parent = list(range(len(rooms)))

    def find(i):
        while parent[i] != i:
            parent[i] = parent[parent[i]]
            i = parent[i]
        return i

    pairs = []
    for i, a in enumerate(rooms):
        for j in range(i + 1, len(rooms)):
            b = rooms[j]
            if a["_on"] is not None or b["_on"] is not None:
                continue                                     # (a room up on another is on neither's floor)
            if a.get("shape") in bgen.SPECIAL_SHAPES or b.get("shape") in bgen.SPECIAL_SHAPES:
                continue                                     # (a gate, a wall, a pavement, a river: on the ground, as drawn)
            if a["x0"] <= b["x1"] and b["x0"] <= a["x1"] and a["z0"] <= b["z1"] and b["z0"] <= a["z1"] \
                    and abs(_floor(a) - _floor(b)) <= LEVEL_UP:   # overlapping (a tower standing in a hall): one floor too
                parent[find(i)] = find(j)
            for lo, hi, olo, ohi in (("x0", "x1", "z0", "z1"), ("z0", "z1", "x0", "x1")):
                for p, q in ((a, b), (b, a)):
                    if _touch(p, q, lo, hi, olo, ohi):
                        pairs.append((p, q, lo, hi, olo, ohi))
                        if abs(_floor(a) - _floor(b)) <= LEVEL_UP:
                            parent[find(i)] = find(j)
    # the floors (ground + 台基) line up: each room's ground moves so its floor is the highest floor of its group
    fls = [max(_floor(o) for j, o in enumerate(rooms) if find(j) == find(i)) for i in range(len(rooms))]
    for r, fl in zip(rooms, fls):
        up = fl - _floor(r)
        if up > 0:
            r["podium"] = _pod(r) + up                 # lifted onto the platform of its own, its steps down to the real ground
            # (a round tower too: its round platform; lifting its ground instead left it over a gap, steps in the air)
            if not r.get("podium_style"):
                r["podium_style"] = "plain"
        else:
            r["y"] -= _floor(r) - fl
    for r in rooms:
        r["cx"], r["cz"] = (r["x0"] + r["x1"]) // 2, (r["z0"] + r["z1"]) // 2
        half_x, half_z = max(2, (r["x1"] - r["x0"]) // 2), max(2, (r["z1"] - r["z0"]) // 2)
        r["hx"], r["hz"] = (half_x, half_z) if r["front"] in ("south", "north") else (half_z, half_x)
        r["sh"] = max(4, min(16, int(r["sh"]))) if r.get("sh") else sh   # (a great hall's storey is 12-16 high)
        r["side_doors"] = {}                            # (the doorways to its neighbours; "doors" is 出入口)
        r["tall_beside"] = []                           # (its gables a taller room is built against: no 马头墙 there)
        if r.get("shape") == "round":                    # 圆塔: the round room fits inside the drawn rectangle
            r["hx"] = r["hz"] = max(2, min(r["hx"], r["hz"]))
    # a front door that would open straight into another room is dropped (no door, no steps there); that room is
    # reached through the doorways to its neighbours instead
    for r in rooms:
        a, b, c, d = ROT[r["front"]]
        fx, fz = r["cx"] + b * (r["hz"] + 1), r["cz"] + d * (r["hz"] + 1)
        r["no_front"] = any(o is not r and o["x0"] <= fx <= o["x1"] and o["z0"] <= fz <= o["z1"] and o["_on"] == r["_on"]
                            and o.get("shape") not in FLAT_SHAPES for o in rooms)   # (a pavement or a river in front is no wall)
    # doorways where rooms touch
    for p, q, lo, hi, olo, ohi in pairs:
        if _floor(p) != _floor(q) or p.get("shape") in ("round",) + bgen.SPECIAL_SHAPES or q.get("shape") in ("round",) + bgen.SPECIAL_SHAPES:
            continue
        levels = min(p["n"], q["n"]) if p["sh"] == q["sh"] and not _taper(p) and not _taper(q) else 1   # upper floors line up only then
        e1, e2 = p[hi], q[lo]
        a0, a1 = max(p[olo], q[olo]) + 1, min(p[ohi], q[ohi]) - 1
        mid = (a0 + a1) / 2
        for t in sorted(range(a0, a1 + 1), key=lambda t: (abs(t - mid), t)):
            cells = [(p, e1, t), (q, e2, t)] if lo == "x0" else [(p, t, e1), (q, t, e2)]
            loc = [(rm, _local(rm, x, z)) for rm, x, z in cells]
            if all(_door_ok(rm, u, v) for rm, (u, v) in loc):
                for rm, (u, v) in loc:
                    rm["side_doors"][(u, v)] = max(levels, rm["side_doors"].get((u, v), 0))
                break
    # 马头墙 (bgen.horsehead_walls): none on a gable a taller room is built against - it stood a cell into that room
    for p, q, lo, hi, olo, ohi in pairs:
        for r, o, e in ((p, q, p[hi]), (q, p, q[lo])):
            if r.get("horsehead") and _floor(o) + o["n"] * o["sh"] > _floor(r) + r["n"] * r["sh"]:
                u, _ = _local(r, e, r["cz"]) if lo == "x0" else _local(r, r["cx"], e)
                if abs(u) >= r["hx"]:
                    r["tall_beside"] = sorted(set(r["tall_beside"]) | {1 if u > 0 else -1})
    # no windows in walls that a neighbouring room is built against
    solid_cols = {}                                     # (x, z) -> the rooms (not flat) whose frame holds it
    for o in rooms:
        if o.get("shape") not in FLAT_SHAPES:
            for x in range(o["x0"], o["x1"] + 1):
                for z in range(o["z0"], o["z1"] + 1):
                    solid_cols.setdefault((x, z), []).append(o)
    for r in rooms:
        A, Bm, C, Dm = ROT[r["front"]]
        cells = set()
        for u in range(-r["hx"], r["hx"] + 1):
            for v in range(-r["hz"], r["hz"] + 1):
                if abs(u) != r["hx"] and abs(v) != r["hz"]:
                    continue
                for du, dv in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    ou, ov = u + du, v + dv
                    if abs(ou) <= r["hx"] and abs(ov) <= r["hz"]:
                        continue
                    x, z = r["cx"] + A * ou + Bm * ov, r["cz"] + C * ou + Dm * ov
                    if any(o is not r for o in solid_cols.get((x, z), ())):
                        cells.add((u, v))
        r["no_win"] = cells
    _tower_doors(rooms)
    walks = _wall_walks(rooms)
    _ladder_free(rooms, by_i)
    # build every room on its own canvas
    parts = []
    built = {}                          # request index -> its wall top, part, result (for the rooms stacked on it)
    for i, r in enumerate(rooms):
        CANCEL()                                       # (a preview a newer one has replaced stops here)
        PROGRESS(i, len(rooms))
        n = r["n"]
        uses = list(r.get("uses") or [])
        if not uses:
            uses = [r.get("use", "main_room")] + [r.get("use_upper", r.get("use", "bedroom"))] * (n - 1)
        uses = (uses + [uses[-1]] * n)[:n]
        fopen = list(r.get("floor_open") or [])            # 开敞 floor by floor ("" = as the whole room)
        floors = [dict(program=(u,) + COMPANIONS.get(u, ()) * 6, split=u in COMPANIONS, plan="bays" if u in COMPANIONS else None,
                       open=fopen[i] if i < len(fopen) and fopen[i] in dict(OPENS) else None)
                  for i, u in enumerate(uses)]
        doors, opens = doors_open(r)
        spec = dict(base)
        walls = _override(r.get("walls", "palace"), r.get("walls_override"), bgen.WALLS)
        theme = _override(r.get("theme", "grey"), r.get("roof_override"), arch.ROOFS, roof=True)
        spec.update(walls=(walls,), themes=(theme,), roofs=(r.get("roof", "xieshan"),),
                    storeys=(n, n), sh=(sh0, sh0), ground=(uses[0],), upper=(uses[-1],), _floors=floors,
                    decor=(), shop=uses[0] in SHOP_USES,
                    balcony=n > 1 and not r["no_front"] and _taper(r) != 1,   # (set in by one: too narrow a ledge to step out on)
                    podium=(1, 1), _wing=True, steep=0.5, _side_doors=r["side_doors"], _no_podium_ring=not int(r.get("podium", 0)),   # a platform you ask for shows: a stone ledge round the walls
                   
                    _no_front_door=r["no_front"], _no_window_cells=r["no_win"], _sh=r["sh"], _free_cells=r.get("free"),
                    _pod=_pod(r),                                         # no 台基 unless the room asks for one
                    _base=base_block(r, walls),                           # what the 台基 and the stone under it are made of
                    _taper=_taper(r),                                     # 收分: every storey set in this much a side
                    _pingzuo=bool(r.get("pingzuo")),                       # 平座: a railed walk round every upper storey (圆塔)
                    _veranda=bool(r.get("veranda")),                    # 前廊
                    horsehead=bool(r.get("horsehead")) and r.get("roof") in ("yingshan", "xuanshan"),   # 马头墙: gable roofs only
                    _tall_beside=r["tall_beside"],                       # (but not where a taller room stands against it)
                    _pitch=int(r.get("pitch", 0) or 0), _eave_out=int(r.get("overhang", 0) or 0), _lift=int(r.get("lift", 0) or 0),
                    _double_eave=bool(r.get("double_eave") or r.get("triple_eave")),   # 重檐
                    _extra_eaves=2 if r.get("triple_eave") else 1 if r.get("double_eave") else 0,   # 三重檐: one more
                    _podium_style=r.get("podium_style") if r.get("podium_style") in ("xumizuo", "tiers", "chengtai") else None,
                    _step_slope=r.get("step_slope"),                     # 坡度: the front steps 45° (a block a cell) or gentler
                    _podium_margin=_podium_margin(r),                    # 台基边距: cells of walk round the walls (None: 自动)
                    _podium_shape=r.get("podium_shape") if r.get("podium_shape") in ("square", "round") else None,   # 台基形状 (跟随: None)
                    _colonnade=bool(r.get("colonnade")),                 # 周围廊
                    _open=opens == "open", _through=opens == "through",  # 开敞: 全敞开 / 穿堂
                    _open_front=opens == "front",                        # 正面敞开 (a stage)
                    _back_door=doors in ("front_back", "four") and opens != "through",   # 出入口 前后: doors front and back, a
                                                                         # flight down behind (穿堂 makes its own way out there)
                    _four=doors == "four",                               # 四面: and on both sides (a square hall)
                    _back_veranda=bool(r.get("back_veranda")),           # 后廊
                    _beasts=int(r.get("beasts", 0) or 0),                 # 走兽
                    _storey_eaves=True, _variant=int(r.get("variant", 0) or 0),                                   # 每层的腰檐跟着屋顶的出檐、翘角、斗拱、走兽
                    _tunnels=int(r.get("tunnels", 0) or 0),               # 城台的门洞
                    _neighbours=[((o["x0"], o["z0"], o["x1"], o["z1"]), o.get("shape") not in bgen.SPECIAL_SHAPES)   # (the 城台's stair
                                 for o in rooms if o is not r and o.get("shape") not in FLAT_SHAPES and o["_on"] is None]   # keeps off them)
                    if r.get("podium_style") == "chengtai" else None,
                    _brackets=int(r.get("brackets", 0) or 0),             # 斗拱
                    _caihua=r.get("caihua") if r.get("caihua") in bgen.CAIHUA else None,   # 彩画
                    _round_roof=r.get("round_roof") if r.get("round_roof") in bgen.ROUND_ROOFS else None,   # 圆塔的屋顶
                    _bay_style=r.get("bay_style") if r.get("bay_style") not in (None, "auto") else None)
        if req.get("interior") is False:                 # 内饰：无 - no furniture, no lamps inside (stairs, ladders kept)
            spec["_bare"] = True
        if r["_on"] is not None and r["_on"] in built:      # 叠放: on the room under it (its roof's terrace, or its wall top)
            r["y"] = built[r["_on"]]["wall_top"]
        ups = [o for o in rooms if o["_on"] == r["_i"]]
        if ups and r.get("shape") not in bgen.SPECIAL_SHAPES:   # something stands on this room: its roof is cut flat round it
            o = ups[0]
            oex, oez = (o["hx"], o["hz"]) if o["front"] in ("south", "north") else (o["hz"], o["hx"])
            corners = [_local(r, o["cx"] + sx * oex, o["cz"] + sz * oez) for sx in (-1, 1) for sz in (-1, 1)]
            spec["_cap"] = (min(c[0] for c in corners), min(c[1] for c in corners), max(c[0] for c in corners), max(c[1] for c in corners))
        rs = r.get("seed", i)
        arch.begin_ornaments(f"compose/{seed}/{look}/{rs}/{int(r.get('roof_variant', 0))}", tier)   # a roof reroll changes them
        spec["_roof_rng"] = random.Random(f"compose/{seed}/{look}/{rs}/roof/{int(r.get('roof_variant', 0))}")
        rcv = Canvas()
        rrng = random.Random(f"compose/{seed}/{look}/{rs}/{int(r.get('variant', 0))}")
        if r.get("shape") in GROUND_SHAPES or r.get("shape") == "bridge":   # 台地, 街道, 石阶, 石桥: no part of their own
            r["fp"], r["top"] = (r["x0"], r["z0"], r["x1"], r["z1"]), r["y"]
            r["base_blk"] = base_block(r, r.get("walls"))
            continue
        if r.get("shape") in bgen.SPECIAL_SHAPES:        # 牌楼, 影壁, 随墙门, 琉璃门, 垂花门
            res = bgen.special_shape(Frame(rcv, r["cx"], r["cz"], r["front"]), r["y"], rrng, spec, tier, r["shape"], r["hx"], r["hz"])
            flat = r["shape"] in FLAT_SHAPES               # (a pavement or a channel: no column of its own above the ground)
            dz = 1 if r["shape"] == "wall" and r["hz"] < bgen.THICK else r["hz"]   # (a 宫墙 is 3 deep whatever its frame
            # up to 5 deep - a 城墙 is as thick as its frame: a frame round it owned the
            ex, ez = (r["hx"], dz) if r["front"] in ("south", "north") else (dz, r["hx"])   # empty cell beside it, and the
            # end of a wall or a house running up to it was cut off there - a slot through into the yard)
            fp = (r["cx"] - ex, r["cz"] - ez, r["cx"] + ex, r["cz"] + ez)
            r["fp"], r["top"] = fp, res["top"]
            r["base_blk"] = spec["_base"]
            parts.append(dict(blocks=rcv.blocks, cols=rcv.cols, fp=fp, y=r["y"], wall_top=res["floor"] + res["sh"],
                              top=r["y"] - 1 if flat else (res["floor"] + res["sh"] if r["shape"] == "wall" else res["top"])))
            # (no platform of its own to share out: no floor; a wall owns its column only to its top course, so a hall's
            # eave reaching over it is not cut off out on the far side)
            built[r["_i"]] = dict(wall_top=res["floor"] + res["sh"], part=len(parts) - 1, res=res, n=n, walls=r.get("walls"))
            continue
        if r.get("shape") == "round":                    # 圆塔: the round room fits inside the drawn rectangle
            R = r["hx"]
            res = bgen.round_room(Frame(rcv, r["cx"], r["cz"], r["front"]), r["y"], rrng, spec, tier, R, n, sh=r["sh"])
            if spec["_podium_shape"] == "square" and r.get("podium_style") == "chengtai" and _pod(r):   # (its stair: cut in now)
                bgen.chengtai_well(Frame(rcv, r["cx"], r["cz"], r["front"]), R, R, r["y"], _pod(r), 0, spec["_base"],
                                   spec.get("_neighbours"), spec["_podium_margin"])
            r["mask"] = {(r["cx"] + u, r["cz"] + v) for u in range(-R - 1, R + 2) for v in range(-R - 1, R + 2)
                         if u * u + v * v <= (R + 0.5) ** 2}
        else:
            res = bgen.house(Frame(rcv, r["cx"], r["cz"], r["front"]), r["y"], rrng, spec, tier, hx=r["hx"], hz=r["hz"], n=n)
        bgen.roof_theme("grey")
        arch.style("red")
        ex, ez = (r["hx"], r["hz"]) if r["front"] in ("south", "north") else (r["hz"], r["hx"])
        fp = (r["cx"] - ex, r["cz"] - ez, r["cx"] + ex, r["cz"] + ez)
        top = max([y for (x, y, z) in rcv.blocks if fp[0] <= x <= fp[2] and fp[1] <= z <= fp[3]] or [res["top"]])
        r["fp"], r["top"] = fp, top
        r["base_blk"] = spec["_base"]
        parts.append(dict(blocks=rcv.blocks, cols=rcv.cols, fp=fp, y=r["y"], wall_top=res["floor"] + (n - 1) * res["sh"] + res.get("top_sh", res["sh"]), top=top,
                          mask=r.get("mask"), floor=res["floor"]))
        built[r["_i"]] = dict(wall_top=res.get("cap_y") or res.get("wall_top", res["floor"] + n * res["sh"]), part=len(parts) - 1, res=res, n=n,
                              walls=r.get("walls"))
    for r in rooms:                     # 叠放: the room under another owns its column only up to its wall top - above that
        if r["_on"] is not None and r["_on"] in built and r["_i"] in built:    # its roof and the upper room's eaves meet
            low = parts[built[r["_on"]]["part"]]
            low["top"] = min(low["top"], built[r["_on"]]["wall_top"])
    who = {}
    joinparts.join(cv, parts, who)
    for cells, y, door in walks:        # 城墙 onto 城台: the 城台's parapet across the walk goes (one walk, wall to tower)
        for x, z in cells:
            for yy in range(y + 1, y + 4):
                if (x, yy, z) in cv.blocks:
                    cv.blocks[(x, yy, z)] = bgen.AIR
        if door:                        # and a door hung in the doorway the tower got from the walk (its front door's kind),
            x, z, dx, dz, (x0, z0, x1, z1) = door   # its wall closed over it
            ring = [(u, z0) for u in range(x0, x1 + 1)] + [(u, z1) for u in range(x0, x1 + 1)] \
                + [(x0, w) for w in range(z0, z1 + 1)] + [(x1, w) for w in range(z0, z1 + 1)]
            kind = next((n for n in ((cv.blocks.get((u, y + 1, w)) or "").split("[")[0] for u, w in ring) if n.endswith("_door")),
                        "minecraft:spruce_door")
            facing = {(0, -1): "north", (0, 1): "south", (1, 0): "east", (-1, 0): "west"}[(-dx, -dz)]
            for kk, half in ((1, "lower"), (2, "upper")):
                cv.blocks[(x, y + kk, z)] = f"{kind}[facing={facing},half={half},hinge=left,open=false,powered=false]"
            side = cv.blocks.get((x + dz, y + 3, z + dx))
            if side and not side.endswith(":air") and "_door" not in side:
                cv.blocks[(x, y + 3, z)] = side
    for r in rooms:                     # 叠放: a ladder from the lower room's top storey up through its ceiling
        if r["_on"] is not None and r["_on"] in built and r["_i"] in built:
            _stack_ladder(cv, r, built[r["_on"]], built[r["_i"]])
    terrain = None
    if any(r.get("shape") in GROUND_SHAPES for r in rooms):
        terrain = terrain_ground(cv, rooms)   # 城镇: ground at many levels, retaining walls and steps between them
        terrain["nofoot"] = build_bridges(cv, rooms, terrain["floor"], terrain["ground"])
        court = None
    else:
        court = compound_ground(cv, rooms)  # 院子: one ground over the whole compound
    # a neighbour's eave, fascia board or roof edge hanging at the head of a door (rooms on different floors, or not
    # touching: each room keeps its own eaves over its own doors, not over the next one's) - that piece goes; and so
    # does a trapdoor there of the room's own (an awning over a shop counter, a hood over an opening: only trim)
    def trim_low():
        for door, low in bgen.walkout(cv.blocks, cv.cols, terrain["floor"] if terrain else min(r["y"] for r in rooms)):
            mine = who.get(door)
            for c in low:
                if who.get(c) is not None and (who.get(c) != mine or "_trapdoor" in (cv.blocks.get(c) or "")):
                    cv.blocks[c] = bgen.AIR
    trim_low()
    connect_panes(cv.blocks)          # glass panes joined to what is really round them (a round wall steps at the diagonals)
    if bgen.clear_doorways(cv):
        trim_low()                    # (a way it opened up: what hangs at its head)
    bgen.settle_loose(cv)
    if terrain:                       # 城镇: bits of roof left hanging where two rooms' gables meet on a shared wall go
        import selfcheck              # (lazy: selfcheck imports the daemon, which imports this module)
        loose = selfcheck.check(cv.blocks, terrain["floor"], cv.cols, only="floating").get("floating", [])
        if len(loose) <= 200:         # (a few small pieces; more would be something else, left for the self-check)
            for c in loose:
                cv.blocks[c] = bgen.AIR
    connect_panes(cv.blocks)          # again: a doorway cleared, an eave trimmed, a loose bit gone beside a pane since -
                                      # a pane left joined on one side (a slit; a 茶楼's next to the doorway into the room
                                      # wall to wall with it) joins what is there now, or turns full glass
    storeys = []                        # where each room's floors are (for checks: can a player walk up to them)
    for r in rooms:
        b = built.get(r["_i"])
        if not b or r.get("shape") in bgen.SPECIAL_SHAPES:
            continue
        res = b["res"]
        x0, z0, x1, z1 = r["fp"]
        tp = _taper(r)
        cx, cz, ex, ez = (x0 + x1) // 2, (z0 + z1) // 2, (x1 - x0) // 2, (z1 - z0) // 2
        storeys.append([(cx - max(2, ex - s * tp) + 1, cz - max(2, ez - s * tp) + 1, cx + max(2, ex - s * tp) - 1,
                         cz + max(2, ez - s * tp) - 1, res["floor"] + s * res["sh"]) for s in range(b["n"])])
    bases = [(r["fp"], r["y"], r.get("base_blk")) for r in rooms if r["_on"] is None] or \
        [(r["fp"], r["y"], r.get("base_blk")) for r in rooms]
    if court:
        bases.append(court)             # (last: inside a room's frame the room's own base wins the tie)
    out = dict(rooms=len(rooms), bases=bases, top=max(r["top"] for r in rooms),
               doors=sum(len(r["side_doors"]) for r in rooms), sh=sh, storeys=storeys)
    if terrain:
        out["ground"] = terrain["ground"]   # {(x, z): ground y} for every column of the town (the daemon clears above it)
        out["floor"] = terrain["floor"]     # the lowest y the canvas's ground runs reach down to
        out["nofoot"] = terrain["nofoot"]   # 石桥 decks: no footing under them (the daemon, the game's carry-down)
    return out
