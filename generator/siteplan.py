"""总平面图: a compound of several buildings laid out by rule, written as one 单体建造 design (its rooms: halls, side
halls, walls, gates, pavements, a river), so it loads into the room table like any save and every piece stays editable.

Layout (north is -z, the front +z): courts from the back to the front, each a main hall at its north end facing
south, side halls along both sides facing in, the court paved between them; a cross wall with a gate between two
courts; in front an outer yard (a palace's with a 金水河 and bridges) and the front wall with the main gate, a 照壁
outside facing it. A house (住宅) has one court (二进: an outer yard and a 垂花门 before it, 三进: a back row too); a
palace (宫院) one to three courts.

Every frame is an odd number of cells wide and deep, so no frame loses a column (单体建造 drops one from an even side);
the wall pieces are cut round the gates so that each stays odd too.

site(kind, jin, seed) -> design dict (name, tier, look, seed, rooms); python siteplan.py [out_dir] writes a set of them."""
import json
import math
import random
import sys
from pathlib import Path

from arch import ROT, STEP_SLOPES, step_run

T = 3                                     # walls are frames three cells deep
WALK = 2                                  # cells left free to walk at the foot of every flight of steps
TW = 9                                    # the 宫城's outer wall: a 城墙 this thick at the foot (收分 1: 7 on top)
WH = 10                                   # and this high, as high as the 城台 of the towers on it (its walk runs onto them)


def odd(n):
    return n | 1


def room(x0, z0, x1, z1, rng, **kw):
    d = dict(x0=x0, z0=z0, x1=x1, z1=z1, y=0, storeys=1, roof="yingshan", walls="greybrick", theme="grey", bay_style="auto",
             base="auto", front="south", sh=0, podium=0, podium_style="plain", open="closed", uses="main_room", special="",
             round=False, seed=rng.randrange(1 << 30), variant=0, roof_variant=0, stack=False)
    d.update(kw)
    if isinstance(d["uses"], str):
        d["uses"] = [d["uses"]] * 8
    if d["special"]:
        d["shape"] = d["special"]                     # (the generator reads "shape"; the room table reads "special")
    return d


def runs(a0, a1, gaps=(), most=125):
    """The pieces of a wall from a0 to a1 (inclusive) round the gaps [(g0, g1)], each odd and at most `most` long."""
    out, a = [], a0
    for g0, g1 in sorted(gaps) + [(a1 + 1, a1 + 1)]:
        if g0 > a:
            out += _split(a, g0 - 1, most)
        a = g1 + 1
    return out


def _split(a0, a1, most):
    n = a1 - a0 + 1
    if n <= most:
        return [(a0, a1)]
    k = -(-n // most)
    if n % 2 and k % 2 == 0:
        k += 1                                    # an odd length cuts into an odd number of odd pieces
    p = odd(n // k)
    if p > most:
        p -= 2
    cuts = [p] * (k - 1)
    cuts.append(n - p * (k - 1))
    out, a = [], a0
    for c in cuts:
        out.append((a, a + c - 1))
        a += c
    return out


def site(kind="house", jin=2, seed=1, river=True):
    rng = random.Random(f"site/{kind}/{jin}/{seed}")
    palace = kind == "palace"
    CW = rng.choice((37, 41, 45, 49, 53)) if palace else rng.choice((25, 29, 33, 37, 41))   # (each 1 more than a multiple of 4)
    c = CW // 2
    if palace:
        mat = dict(walls=rng.choice(("palace", "palace", "jade", "yellow")), theme=rng.choice(("gold", "gold", "jade", "teal")))
    else:
        mat = dict(walls=rng.choice(("greybrick", "greybrick", "whitewall", "darkwood", "redbrick", "mud")),
                   theme=rng.choice(("grey", "grey", "black", "slate", "wood", "thatch")))
    rooms = []
    z = T + 2                                     # (a gap behind the back row: its eaves stay clear of the wall)
    courts = jin if palace else 1
    if not palace and jin >= 3:                   # 后罩房: a long back row, a narrow yard in front of it
        rooms.append(room(T, z, CW - T - 1, z + 6, rng, front="south", storeys=rng.choice((1, 1, 2)), uses="storeroom", **mat))
        z += 7
        rooms.append(room(T, z, CW - T - 1, z + 2, rng, special="paving", **mat))
        z += 3
    for k in range(courts):
        front_court = k == courts - 1
        MD = rng.choice((9, 11, 13) if palace else (7, 9, 11))
        MW = odd(round(CW * rng.uniform(0.38, 0.56)))
        x0, x1 = c - MW // 2, c + MW // 2
        if palace:
            hall = dict(roof=rng.choice(("xieshan", "xieshan", "wudian", "luding", "pyramid" if MW < 20 else "xieshan")),
                        double_eave=rng.random() < 0.45, podium=rng.choice((2, 3, 4, 5)), podium_style=rng.choice(("xumizuo", "xumizuo", "tiers")),
                        base="white", caihua=rng.choice(("hexi", "xuanzi", "sushi")), beasts=rng.choice((2, 3, 4)), brackets=rng.choice((0, 2, 3)),
                        pitch=rng.choice((2, 3, 3, 4)), colonnade=rng.random() < 0.25, veranda=rng.random() < 0.3,
                        uses="council_hall" if front_court else "main_room")
        else:
            hall = dict(roof=rng.choice(("yingshan", "yingshan", "xuanshan", "juanpeng", "xieshan")), podium=rng.choice((0, 1, 1, 2)),
                        podium_style="plain", storeys=rng.choice((1, 1, 1, 2)), veranda=rng.random() < 0.4,
                        horsehead=mat["walls"] == "whitewall" and rng.random() < 0.6, uses="main_room")
            if hall["horsehead"]:
                hall["roof"] = "yingshan"
        k_hall = len(rooms)
        for p in rooms:                            # clear of the buildings before it (the last court's side halls, a back
            if p.get("special"):                   # row): their eaves and its, their steps and WALK cells at their foot
                continue
            if _hit((x0 - _reach(hall), 0, x1 + _reach(hall), 0), (p["x0"] - _reach(p), 0, p["x1"] + _reach(p), 0)):
                z = max(z, p["z1"] + 1 + max(_reach(p) + _reach(hall) + 1,
                                             _steps(p) + WALK + _plat(hall) + 1 if p["front"] == "south" else 0))
        rooms.append(room(x0, z, x1, z + MD - 1, rng, front="south", _role="main", **dict(mat, **hall)))
        room_w = x0 - T                            # 耳房 either side of the main hall, set back a cell (not always)
        if room_w >= 6 and rng.random() < 0.75:
            ew = min(7, room_w - 1)
            ew -= 0 if ew % 2 else 1
            side = dict(roof="yingshan" if not palace else "xuanshan", uses="storeroom" if not palace else "study",
                        caihua="xuanzi" if palace else "")
            rooms.append(room(x0 - ew, z + 1, x0 - 1, z + MD - 2, rng, front="south", _ear=(-1, T), **dict(mat, **side)))
            rooms.append(room(x1 + 1, z + 1, x1 + ew, z + MD - 2, rng, front="south", _ear=(1, CW - T - 1), **dict(mat, **side)))
            _part_ears(rooms)                      # (clear of the hall's eaves: built against it, the hall's roof took theirs)
        z0 = z + MD
        CD = odd(rng.randint(11, 23))
        SW = rng.choice((7, 9, 11)) if palace else rng.choice((5, 7, 9))
        wing = dict(roof=rng.choice(("xuanshan", "yingshan", "xieshan")) if palace else rng.choice(("yingshan", "yingshan", "xuanshan", "juanpeng")),
                    uses="study" if palace else "bedroom", caihua=rng.choice(("xuanzi", "sushi", "")) if palace else "",
                    podium=rng.choice((0, 1, 2)) if palace else rng.choice((0, 0, 1)), storeys=rng.choice((1, 1, 1, 2)),
                    veranda=rng.random() < 0.35)
        def need():                                          # (the wings' steps and eaves meeting across the court)
            return max(2 * _steps(wing) + WALK, 2 * _reach(wing) + 1)
        while SW > 5 and CW - 2 * (T + SW) < need():
            SW -= 2
        if CW - 2 * (T + SW) < need():
            wing["veranda"] = False
        while CW - 2 * (T + SW) < need() and wing["podium"]:   # (shorter flights: a lower platform)
            wing["podium"] -= 1
        wings = [room(T, z0 + 2, T + SW - 1, z0 + CD - 3, rng, front="east", **dict(mat, **wing)),
                 room(CW - T - SW, z0 + 2, CW - T - 1, z0 + CD - 3, rng, front="west", **dict(mat, **wing))]
        # 配殿 start clear of the main hall and its 耳房 - their eaves, platforms and front steps - with their own eaves
        # and a cell to stand on between: two rows past the hall's front they stood under its eaves, and the 耳房's
        # doors opened onto their gable ends
        ahead = [b for r in rooms[k_hall:] if not r.get("special") for b in [_zone(r)] + _walk_rects(r)]
        m = max(_reach(wings[0]), _steps(wings[0]) + WALK)        # (their own flights come out sideways)
        start = max([z0 + 2] + [b[3] + m + 2 for w in wings for b in ahead if b[0] <= w["x1"] + m and w["x0"] - m <= b[2]])
        start += (start - z0) % 2                  # (an even shift: the court stays odd deep)
        CD += start - (z0 + 2)                     # (the court longer by as much: the wings keep their length)
        hr = rooms[k_hall]                         # (and deep enough for the hall's steps and WALK cells before the
        CD = max(CD, (hr["z1"] + _steps(hr) + WALK + 2 - z0) | 1)   # wall in front: a gentle flight ran into it)
        for w in wings:
            w["z0"], w["z1"] = start, z0 + CD - 3
        rooms += wings
        rooms.append(room(T + SW, z0, CW - T - SW - 1, z0 + CD - 1, rng, special="paving", **mat))
        z = z0 + CD
        if not front_court or (not palace and jin >= 2):   # a cross wall to the next court (or the outer yard), a gate in it
            gw = 9
            gate = "glazedgate" if palace else "chuihua"
            rooms.append(room(c - gw // 2, z - (0 if palace else 1), c + gw // 2, z + (2 if palace else 3), rng, special=gate,
                              front="south", sh=6, **mat))
            for a0, a1 in runs(T, CW - T - 1, [(c - gw // 2, c + gw // 2)]):
                rooms.append(room(a0, z, a1, z + 2, rng, special="wall", front="south", sh=5 if not palace else 7, **mat))
            z += T
    # the outer yard (a palace's with the 金水河 across it) and the front wall
    OD = 0
    if palace and river:
        rooms.append(room(T, z, CW - T - 1, z + 6, rng, special="river", **mat))
        z += 7
        OD = 5
    elif palace:
        OD = 7
    elif jin >= 2:
        OD = rng.choice((7, 9))
    gw = 11 if palace else 7
    gate = dict(front="south", roof="xieshan", open="through", podium=2, podium_style="xumizuo", base="white", caihua="xuanzi",
                uses="lookout", **mat)
    if palace:                                    # the 门殿 (its frame from z + OD - 2): its back steps and WALK cells
        ends = [z - 1] if river else []           # clear of the river and every flight before it (with their strips),
        ends += [b[3] for r in rooms if not r.get("special") for b in _walk_rects(r)]   # and of every platform across
        fl = _walk_rects(dict(gate, x0=c - gw // 2, z0=0, x1=c + gw // 2, z1=6))[0]     # its flight (the side halls'
        ends += [f[3] for r in rooms if not r.get("special") for f in [_foot(r)] if f[0] <= fl[2] and fl[0] <= f[2]]   # corners)
        OD = max([OD] + [e + _steps(gate) + 3 - z + WALK for e in ends]    # its eaves of the last court's side halls'
                 + [w["z1"] + _reach(w) + _reach(gate) + 3 - z for w in wings])   # (they touched its corners)
    total = z + OD + T
    if total % 2 == 0:                            # (the side walls between the end walls must be odd long)
        OD += 1
    if OD:
        rooms.append(room(T, z, CW - T - 1, z + OD - 1, rng, special="paving", **mat))
        z += OD
    sz = z + T + 4                                # (the 照壁's row)
    if palace:                                    # 门殿 through the front wall, open front to back
        rooms.append(room(c - gw // 2, z - 2, c + gw // 2, z + 4, rng, **gate))
        sz = max(sz, rooms[-1]["z1"] + _steps(rooms[-1]) + WALK + 2)   # (its steps came down against the 照壁's base)
    else:
        rooms.append(room(c - gw // 2, z, c + gw // 2, z + 2, rng, special="gate", front="south", sh=6, **mat))
    for a0, a1 in runs(0, CW - 1, [(c - gw // 2, c + gw // 2)]):
        rooms.append(room(a0, z, a1, z + 2, rng, special="wall", front="south", sh=7 if palace else 5, **mat))
    D = z + T
    for a0, a1 in runs(0, CW - 1):               # the back wall, and the side walls between the two
        rooms.append(room(a0, 0, a1, 2, rng, special="wall", front="south", sh=7 if palace else 5, **mat))
    for xa in (0, CW - T):
        for a0, a1 in runs(T, D - T - 1):
            rooms.append(room(xa, a0, xa + 2, a1, rng, special="wall", front="east", sh=7 if palace else 5, **mat))
    sw = 11 if palace else 9                      # 照壁 outside, facing the gate
    rooms.append(room(c - sw // 2, sz, c + sw // 2, sz + 2, rng, special="screen", front="north", **mat))
    for r in rooms:
        r.pop("_role", None)
    name = ("宫院" if palace else "住宅") + "·" + "一二三"[jin - 1] + "进"
    return dict(name=name, kind=kind, jin=jin, seed=seed, tier="mortal", look=0, size=[CW, sz + 3], rooms=rooms)


def wall_pieces(a0, a1, gaps=()):
    """Wall pieces from a0 to a1 round the gaps, each made odd by running one cell on into the gap (or past the end):
    the gate or the crossing wall there is joined after the walls and wins the cell they share."""
    out = []
    for p0, p1 in _gap_pieces(a0, a1, gaps):
        out += _split(p0, p1 + (0 if (p1 - p0) % 2 == 0 else 1), 125)
    return out


def _gap_pieces(a0, a1, gaps):
    out, a = [], a0
    for g0, g1 in sorted(gaps):
        if g0 > a:
            out.append((a, g0 - 1))
        a = g1 + 1
    if a <= a1:
        out.append((a, a1))
    return out


def city(seed=1):
    """宫城: a palace city on one axis, after the Forbidden City's plan (not its measurements): from the north 神武门,
    the inner court (two halls and a square one between, galleries down both sides), 乾清门, the outer court (three
    halls on 三台, two pavilions either side of the great court, galleries), 太和门, the 金水河 with five bridges,
    the 午门 on a U of 城台 out in front; 东华门 and 西华门 in the side walls, a 角楼 on each corner. The outer wall a
    城墙 walked on top from tower to tower, up a 马道 a side (_city_wall). Down the axis each building stands clear of
    the one before it (its steps, eaves and platform, and a cell to stand on between)."""
    rng = random.Random(f"city/{seed}")
    gal = dict(podium=1, podium_style="plain")       # (galleries() rooms: x 14..22, steps facing in)
    W = max([151] + [2 * (23 + _steps(gal) + WALK + _steps(dict(podium=p, podium_style=st)) + xh) + 1   # (四面门: the
                     for xh, p, st in ((7, 3, "xumizuo"), (10, 8, "tiers"))]) | 1                    # square halls)
    c = W // 2
    gold = dict(walls="palace", theme="gold")
    rooms, walls = [], []

    def hall(x_half, z0, depth, **kw):
        d = dict(gold, front="south", podium=3, podium_style="xumizuo", base="white", caihua="hexi", beasts=3, brackets=3, pitch=3,
                 uses="council_hall")
        d.update(kw)
        rooms.append(room(c - x_half, z0, c + x_half, z0 + depth - 1, rng, **d))
        rooms[-1]["doors"], rooms[-1]["open"] = axis_open(rooms[-1], c)     # (on the axis: a way through it)
        return rooms[-1]

    def gate_house(x_half, z_wall, depth, **kw):   # a gate hall across a wall line (the wall at z_wall .. z_wall + 2)
        d = dict(gold, front="south", open="through", roof="xieshan", podium=3, podium_style="xumizuo", base="white",
                 caihua="xuanzi", beasts=2, pitch=3, uses="lookout")
        d.update(kw)
        z0 = z_wall + 1 - depth // 2
        rooms.append(room(c - x_half, z0, c + x_half, z0 + depth - 1, rng, **d))
        return (c - x_half, c + x_half)

    def galleries(z0, z1, inner):                 # 庑: long rooms down both sides of a court, facing in
        for x0, front in ((inner, "east"), (W - inner - 9, "west")):
            for a0, a1 in _split(z0, z1 if (z1 - z0) % 2 == 0 else z1 - 1, 125):
                rooms.append(room(x0, a0, x0 + 8, a1, rng, front=front, roof="xuanshan", podium=1, caihua="xuanzi",
                                  uses="storeroom", **gold))

    def paving(x0, z0, x1, z1):
        if (x1 - x0) % 2:
            x1 -= 1
        if (z1 - z0) % 2:
            z1 -= 1
        rooms.append(room(x0, z0, x1, z1, rng, special="paving", **gold))

    def cross_wall(z, gap):
        for a0, a1 in wall_pieces(3, W - 4, [gap]):
            walls.append(room(a0, z, a1, z + 2, rng, special="wall", front="south", sh=9, **gold))

    def after(a, r):
        """Move r (south of a on the axis) to the first row where a's front steps and r's back steps (or platforms) leave
        WALK cells free between them, and their eaves stay clear of each other: drawn 3-5 rows apart, each hall's doors
        opened onto the next one's back wall and its steps ran into the next one's platform."""
        fa = _steps(a) if a["front"] == "south" and a.get("podium_style") != "chengtai" else _plat(a)
        fb = _steps(r) if _opens_back(r) else _plat(r)
        gap = max(fa + fb + WALK, _reach(a) + _reach(r) + 1)
        r["z0"], r["z1"] = a["z1"] + gap + 1, a["z1"] + gap + 1 + r["z1"] - r["z0"]
        return r

    # 神武门 (the outer wall round the whole palace comes last: _city_wall)
    rooms.append(room(c - 13, -5, c + 13, 7, rng, front="north", roof="wudian", double_eave=True, podium=WH, podium_style="chengtai",
                      tunnels=3, caihua="hexi", beasts=3, pitch=3, uses="lookout", **gold))
    gate_n = rooms[-1]
    # 后三宫, each clear of the one before
    kn = after(gate_n, hall(22, 0, 15, roof="wudian", double_eave=True))          # 坤宁宫
    jt = after(kn, hall(7, 0, 13, roof="pyramid", beasts=2))                       # 交泰殿
    qq = after(jt, hall(22, 0, 17, roof="wudian", double_eave=True, podium=5))     # 乾清宫
    probe = dict(gold, front="south", podium=3, podium_style="xumizuo", open="through", x0=0, x1=0, z0=0, z1=10)   # (乾清门 as gate_house makes it)
    wq = after(qq, probe)["z0"] + 4                                                # its wall line
    galleries(6, wq - 4, 14)
    paving(23, wq - 10, W - 24, wq - 4)
    cross_wall(wq, gate_house(15, wq, 11, double_eave=False))                      # 乾清门
    gate_q = rooms[-1]
    # 前朝: 保和殿, 中和殿, 太和殿, each on its 三台
    tiers = dict(podium=8, podium_style="tiers", base="white")
    bh = after(gate_q, hall(25, 0, 21, roof="xieshan", double_eave=True, **tiers))   # 保和殿
    zh = after(bh, hall(10, 0, 21, roof="pyramid", **tiers))                         # 中和殿
    th = after(zh, hall(31, 0, 31, roof="wudian", double_eave=True, beasts=5, brackets=4, uses="throne_hall", **tiers))   # 太和殿
    wt = th["z1"] + max(56, _steps(th) + WALK + _steps(dict(podium=3, podium_style="xumizuo")) + 9)   # 太和门's wall line
    galleries(wq + 6, wt - 4, 14)
    # 体仁阁, 弘义阁, clear of the galleries' eaves and steps (2 rows off them, the galleries' doors opened onto their backs)
    tx = max(30, 23 + _steps(gal) + WALK + _plat(dict(podium=2, podium_style="xumizuo")))   # (past the galleries' steps)
    for x0, front in ((tx, "east"), (W - tx - 15, "west")):
        rooms.append(room(x0, wt - 42, x0 + 14, wt - 22, rng, front=front, storeys=2, roof="wudian", podium=2, podium_style="xumizuo",
                          base="white", caihua="xuanzi", beasts=2, uses="scripture_store", **gold))
    paving(47, wt - 38, W - 48, wt - 8)
    cross_wall(wt, gate_house(22, wt, 17, double_eave=True))           # 太和门
    gate_t = rooms[-1]
    # the 金水河 court: the river WALK cells past the foot of 太和门's steps (they came down onto it)
    zr = gate_t["z1"] + _steps(gate_t) + WALK + 1
    zr += (zr - wt) % 2                                                # (the pavings stay odd deep)
    rooms.append(room(3, zr, W - 4, zr + 8, rng, special="river", **gold))
    paving(3, wt + 4, W - 4, zr - 2)
    paving(3, zr + 10, W - 4, zr + 18)
    # the front wall and the 午门 on its U of 城台
    wf = zr + 20                                                       # the front wall's line
    ct = dict(podium=WH, podium_style="chengtai", caihua="hexi", beasts=3, pitch=3, uses="lookout", **gold)
    rooms.append(room(c - 31, wf - 6, c + 31, wf + 10, rng, front="south", roof="wudian", double_eave=True, tunnels=4, **ct))
    # the wings (雁翅楼) joined to the 午门 and to their end pavilions (阙亭) as at the 紫禁城: one 城台 top, walked from
    # one to the next (their eaves meet over it - siteplan._joined; pushed clear of each other's eaves, the 城台 met
    # rim to rim and each one's parapet cut the walk between them)
    for x0, front in ((c - 43, "east"), (c + 33, "west")):            # the wings (雁翅楼), no ways through
        rooms.append(room(x0, wf + 14, x0 + 10, wf + 66, rng, front=front, roof="xieshan", tunnels=1, **ct))
    for x0 in (c - 45, c + 31):                                         # their end pavilions
        rooms.append(room(x0, wf + 68, x0 + 14, wf + 82, rng, front="south", roof="pyramid", double_eave=True, tunnels=1, **ct))
    # the side walls, 东华门 / 西华门
    side_gap = (wt - 38, wt - 16)
    for xa, front in ((0, "west"), (W - 3, "east")):
        x0 = xa - 5 if front == "west" else xa - 4
        rooms.append(room(x0, side_gap[0], x0 + 12, side_gap[1], rng, front=front, roof="xieshan", double_eave=True, podium=WH,
                          podium_style="chengtai", tunnels=2, caihua="xuanzi", beasts=2, pitch=3, uses="lookout", _tag="sidegate", **gold))
        for a0, a1 in wall_pieces(3, wf - 1, [side_gap]):
            walls.append(room(xa, a0, xa + 2, a1, rng, special="wall", front="east", sh=10, **gold))
    # 角楼 on the four corners, up on the wall
    for cx, cz in ((1, 1), (W - 2, 1), (1, wf + 1), (W - 2, wf + 1)):
        rooms.append(room(cx - 7, cz - 7, cx + 7, cz + 7, rng, front="south", roof="shizi", double_eave=True, podium=WH,
                          podium_style="chengtai", tunnels=1, beasts=3, caihua="xuanzi", uses="lookout", _tag="corner", **gold))
    # 陈设 in the great court, in pairs either side of the way to 太和殿: vessels, a sundial, a measure
    for i, zz in enumerate((wt - 36, wt - 28, wt - 20)):
        for side in (-1, 1):
            rooms.append(room(c + side * 9 - 1, zz, c + side * 9 + 1, zz + 2, rng, special="ornament", variant=(i * 2 + (side > 0)) % 4,
                              **gold))
    # 东西宫: palace courts down both sides of the inner court, inside an outer wall; the corner towers and the side
    # gates go out to the outer wall, a plain gate where the old side walls are crossed
    sides = [site("palace", 1, seed * 10 + k, river=False) for k in range(8)]   # (no 金水河 of their own)
    cols = []                                          # as many down each side as fit before the side gate
    corner = dict(podium=WH, podium_style="chengtai")
    for k in range(2):                                 # (the first clear of the corner tower: its eaves and platform)
        first = [r for r in sides[k * 4]["rooms"] if not r.get("special")]
        z, col = max(T + 1, 8 + _reach(corner) + 1 + max(_reach(r) - r["z0"] for r in first)), []
        for d in sides[k * 4:(k + 1) * 4]:
            if z + d["size"][1] > side_gap[0] - 4:
                break
            col.append((z, d))
            z += d["size"][1] + 2
        cols.append(col)
    SW = max(d["size"][0] for col in cols for z, d in col)
    off = T + SW + 2
    for r in walls + rooms:
        r["x0"] += off
        r["x1"] += off
    W2 = W + 2 * off
    moved = [r for r in rooms if r.get("_tag") in ("corner", "sidegate")]
    rooms = [r for r in rooms if r.get("_tag") not in ("corner", "sidegate")]
    for xa in (off, off + W - 3):                      # where the side gates were: a gate in the inner wall
        rooms.append(room(xa, side_gap[0] + 6, xa + 2, side_gap[1] - 6, rng, special="gate", front="east", sh=7, **gold))
    for xa, col in zip((T + 1, W2 - T - 1 - SW), cols):
        for z, d in col:
            cw = d["size"][0]
            for r in d["rooms"]:
                r = dict(r)
                r["x0"], r["x1"] = r["x0"] + xa + (SW - cw) // 2, r["x1"] + xa + (SW - cw) // 2
                r["z0"], r["z1"] = r["z0"] + z, r["z1"] + z
                rooms.append(r)
    for r in moved:
        r = dict(r)
        if r["_tag"] == "corner":                       # (on the middle of the 城墙's corner: _city_wall)
            cx = 2 - TW // 2 if r["x0"] < W2 // 2 else W2 - 3 + TW // 2
            cz = 2 - TW // 2 if r["z0"] < wf // 2 else wf + TW // 2
            r["x0"], r["x1"], r["z0"], r["z1"] = cx - 7, cx + 7, cz - 7, cz + 7
        else:
            west = r["x0"] < W2 // 2
            r["x0"], r["x1"] = (-5, 7) if west else (W2 - 8, W2 + 4)
        rooms.append(r)
    for r in walls + rooms:
        r.pop("_tag", None)
    walls += _city_wall(walls + rooms, W2, wf, rng, gold)
    return dict(name="宫城", kind="city", jin=0, seed=seed, tier="mortal", look=0, size=[W2 + 2 * TW + 4, wf + 92], rooms=walls + rooms)


def _city_wall(rooms, W2, wf, rng, gold):
    """The 宫城's outer wall, a 城墙 (bgen.thick_wall): TW thick, 收分 1, WH high, 垛口 outside, 女墙 inside, grown out
    from the old 宫墙 line (its inner face where that one's stood: nothing inside moves). Its pieces stop at the 城台 of
    the towers on it (角楼, 神武门, 东华门 / 西华门, 午门), as high as it: the walk runs onto their tops (compose opens
    their parapet there and a door from the walk into each tower), round the whole palace. 马道: one a side, cut in a
    piece of its own as near the side's gate as its foot (and the WALK cells there) stays clear at every 坡度; the other
    pieces none (门洞 无)."""
    o = TW - 3
    towers = [r for r in rooms if r.get("podium_style") == "chengtai" and int(r.get("podium", 0) or 0) == WH]
    lines = (("north", True, -o, 2), ("south", True, wf, wf + o + 2), ("west", False, -o, 2), ("east", False, W2 - 3, W2 - 1 + o))
    L = WH * max(STEP_SLOPES.values())                    # (the longest 马道: at the gentlest 坡度)
    need = 2 * (L // 2 + 1) + 1                           # (a piece long enough for it: bgen.thick_wall)
    out = []
    for front, along_x, d0, d1 in lines:
        def piece(a0, a1, madao=False):
            x0, z0, x1, z1 = (a0, d0, a1, d1) if along_x else (d0, a0, d1, a1)
            return room(x0, z0, x1, z1, rng, special="wall", front=front, sh=WH, taper=1, step_slope="27",
                        tunnels=0 if madao else 1, **gold)
        on = []                                           # the 城台 on the line, across its whole depth
        for t in towers:
            f = _foot(t)
            (a0, a1), (b0, b1) = ((f[0], f[2]), (f[1], f[3])) if along_x else ((f[1], f[3]), (f[0], f[2]))
            if b0 <= d0 and d1 <= b1:
                on.append((a0, a1))
        spans = _gap_pieces(min(a for a, b in on), max(b for a, b in on), on)
        gate = sorted(on)[len(on) // 2]                   # (the middle tower on the line: its gate)
        best = None
        for k in range(0, max(b - a for a, b in spans) + 1, 2):   # out from the gate, either way in turn
            for a0, a1 in spans:
                for p0 in ((gate[1] + 1 + k,) if a0 == gate[1] + 1 else (gate[0] - need - k,) if a1 == gate[0] - 1 else ()):
                    if a0 <= p0 and p0 + need - 1 <= a1 and _madao_clear(piece(p0, p0 + need - 1, True), rooms):
                        best = (a0, a1, p0)
                        break
                if best:
                    break
            if best:
                break
        for a0, a1 in spans:
            cuts = [(a0, a1)] if not best or best[0] != a0 else [(a0, best[2] - 1), (best[2], best[2] + need - 1), (best[2] + need, a1)]
            for c0, c1 in cuts:
                if c0 > c1:
                    continue
                if best and (c0, c1) == (best[2], best[2] + need - 1):
                    out.append(piece(c0, c1, True))
                else:
                    out += [piece(p0, p1) for p0, p1 in _odd_pieces(c0, c1, 125)]
    return out


CITY_REAL_SOURCES = """紫禁城 1:1 (city_real): where the numbers come from (metres; E-W x W-to-E, N-S z N-to-S; x 0..752, z 0..960 the
outer faces of the wall). (S) = a published figure, (E) = an estimate where nothing was found.
 wall 961 x 753, 10 high (S: dpm.org.cn/explore/buildings.html; measured 963 x 753, 9.3 high, 8.6/6.6 thick -
     dpm 紫禁城城墙现状调查与保护初探). Built 9 thick at the foot, 7 at the top (收分 1 a side), 10 high, 垛口 out,
     女墙 in, 马道 up the inner face (E: a few, by the gates - one either side of each, 8 in all, 27°), on the outer
     face line.
 moat 筒子河 52 wide (S: en/zh.wikipedia, dpm 236508); 20 m off the wall (E). Off by default: every river piece draws
     its own bridges (5 a piece), the real moat has three crossings.
 角楼 tower ~14 across, 27.5 high, 城台 28.5 square (S: dpm 236522 / wall paper). Built 15 x 15 十字脊 on a 城台 21 square.
 午门 城台 127.3 E-W x 36.8, 12 high, 正楼 60.05 x 25.00, 9 x 5 bays (S: dpm 236454, wall paper); wings 13 bays (S); built 123 E-W;
     wings reaching ~97 m south of the wall, ~25 m wide, 4 pavilions (E).
 神武门 / 东华门 / 西华门 城台 50.18 x 26.46 (S: wall paper); 神武门 31 high (S); 东华门/西华门 ~170 m north of the
     south wall (E: "off-centre toward the 午门 end").
 后三宫 court 218 N-S (乾清门 front columns to 坤宁门 back) x 118 (S: Fu Xinian via chinaculture.org, 故宫传);
     前三殿 court 437 (太和门 front columns to 乾清门 front columns) x 234 (S, same).
 三台 130 x ~228, 8.13 high (S: dpm 236465, sohu 643059665); halls ~30 apart (S: thepaper 16234598). Built as each hall's
     own 三层 platform (8 high): one terrace under all three is no room setting.
 太和殿 63.96 x 37 (S: zh.wikipedia); 太和门 41 x 21 (preset; S: 9 x 4 bays, 1300 m2); 中和殿 25 x 25, 保和殿 49 x 25,
     乾清宫 45 x 25, 坤宁宫 45 x 21, 交泰殿 15 x 15 (presets, bay counts S); 乾清门 5 x 3 bays (S) 29 x 13 (E).
 太和门广场 ~26000 m2 (S: dpm 236439) -> 163 x 157 between its galleries (E); 太和门 front 176 m north of the wall (E).
 内金水河 <= 11.8 wide (S: visitbeijing); 5 bridges in front of 太和门 (S), 11 apart (E); the river's run east and west is
     left out (every river piece draws bridges); a piece with one bridge in front of 武英门 and 文渊阁 (E).
 体仁阁/弘义阁 2 storeys, 25 high, 9 x 3 bays (S: dpm 236453) -> 43 x 17 (E).
 御花园 140 x 80 (S: dpm 236519; 135 x 89 also given) -> 135 x 80; 延晖阁 3 bays, 2 storeys (S); 浮碧亭 ~8 wide (S).
 东西六宫 each 50 x 50, a 3 x 3 grid of 50 m cells each side, 一长街 9 wide, 二长街 7, lanes 4 (S: 故宫传 / 王璞子,
     baike en); 六宫 + 五所 = 后三宫 in area (S: Fu); compounds built 49 x 49, rows 5 apart (E).
 养心殿 81.3 x 62.6, front hall 36 x 12 (S: bjd.com.cn, dpm 236442).
 慈宁宫 75 x 98 (S: sohu 169253891); 慈宁宫花园 50 x 130 (S: zh.wikipedia); 寿安宫 78 x 107 (S: dpm 236505); 寿康宫 (E).
 雨花阁 24.8 x 16.2, 22.8 high, 3 storeys outside / 4 inside (S: dili360) -> 25 x 17, 3 storeys.
 宁寿宫 area ~115-120 x 395-406 (S: dpm snippet / obj.cc) -> 119 x 398; 九龙壁 29.4 long (S: dpm 236477);
     乐寿堂 36.15 x 23.20 (S: dpm 236470); 宁寿宫花园 160 x 37-40 (S: dpm 236440); 畅音阁 3 eaves, 20.7 high, 3 x 3 bays
     (S: dpm 236437); 阅是楼 5 x 3, 2 storeys (S); 符望阁 2 storeys outside (S); 梵华楼 7 bays / 佛日楼 3 bays, 2 storeys,
     against the north wall (S: dpm 236572/236573); the rest of the 宁寿宫 halls sized from bay counts (E).
 文渊阁 34.7 x 14.7, 2 storeys (S: gmw.cn); 文华殿 / 武英殿 5 x 3 bays 工字 (S), compounds 91 x 131 / 71 x 101 (E);
     宝蕴楼 three 2-storey buildings, 376.8 + 2 x 118.2 m2 a floor (S: dpm 180962); 奉先殿 front hall 45 x 23 (S); 箭亭
     430 m2 (S); 南三所 three compounds (S), sizes (E); 斋宫 / 毓庆宫 / 英华殿 / 建福宫花园 / 延春阁 / 南薰殿 (E).
Positions across the site are estimates fitted to those sizes and to what stands next to what (dpm pages)."""


def city_real(seed=1, moat=False):
    """紫禁城 at 1:1 (1 block = 1 m): the real layout from public measurements (CITY_REAL_SOURCES says which are
    measured and which estimated), built only of 单体建造 rooms (halls, galleries, towers on 城台, and the specials:
    walls, gates, glazed gates, screens, river, paving, ornaments). x runs east, z south; the outer face of the wall is
    x 0..752, z 0..960; the 午门's wings reach on to the south, the moat (moat=True) 72 out all round.
    Main buildings carry "label" (their name; the editor ignores it)."""
    rng = random.Random(f"city_real/{seed}")
    gold = dict(walls="palace", theme="gold")
    rooms, walls = [], []
    CX = 376                                         # the axis

    def add(x0, z0, w, d, label="", **kw):
        assert w % 2 and d % 2, (label, w, d)
        r = room(x0, z0, x0 + w - 1, z0 + d - 1, rng, **dict(gold, **kw))
        if label:
            r["label"] = label
        rooms.append(r)
        return r

    def at(cx, z0, w, d, label="", **kw):           # centred on cx across
        return add(cx - w // 2, z0, w, d, label, **kw)

    def wall_x(z, a0, a1, gaps=(), sh=7, t=3, front="south", **kw):   # an E-W wall z..z+t-1 from x a0 to a1
        for p0, p1 in wall_pieces(a0, a1, gaps):
            walls.append(room(p0, z, p1, z + t - 1, rng, special="wall", front=front, sh=sh, **dict(gold, **kw)))

    def wall_z(x, a0, a1, gaps=(), sh=7, t=3, front="east", **kw):    # a N-S wall x..x+t-1 from z a0 to a1
        for p0, p1 in wall_pieces(a0, a1, gaps):
            walls.append(room(x, p0, x + t - 1, p1, rng, special="wall", front=front, sh=sh, **dict(gold, **kw)))

    def gate_x(cx, z, w=9, kind="glazedgate", label=""):     # a gate in an E-W wall (front south)
        return at(cx, z, w, 3, label, special=kind, front="south", sh=6)

    def gate_z(x, cz, w=9, kind="gate", front="east", label=""):   # a gate in a N-S wall
        return add(x, cz - w // 2, 3, w, label, special=kind, front=front, sh=6)

    def pave(cx, z0, z1, w=21):                      # a 甬路 strip down the axis (z0..z1, made odd)
        if (z1 - z0) % 2:
            z1 -= 1
        if z1 - z0 >= 4:
            at(cx, z0, w, z1 - z0 + 1, special="paving")

    def gallery(x0, z0, z1, front, gaps=(), depth=9):   # 庑: a N-S row of long rooms facing in, round the gaps
        for p0, p1 in _gap_pieces(z0, z1, gaps):
            if p1 - p0 < 4:
                continue
            for a0, a1 in _odd_pieces(p0, p1, 125):           # (right up to what stands in the gap: joined to it)
                add(x0, a0, depth, a1 - a0 + 1, front=front, roof="xuanshan", podium=1, caihua="xuanzi", uses="storeroom")

    HALL = dict(podium=2, podium_style="xumizuo", base="white", caihua="xuanzi", beasts=2, pitch=3, uses="council_hall")
    GREAT = dict(podium=8, podium_style="tiers", base="white", caihua="hexi", beasts=3, pitch=1, overhang=2, uses="council_hall")
    CT = dict(podium_style="chengtai", caihua="hexi", beasts=3, pitch=3, uses="lookout")

    # ---- the outer wall, 角楼, 神武门 / 东华门 / 西华门, the 午门 on its U of 城台 ----
    E, S = 752, 960
    sw_gap = (315, 437)                              # the 午门's 城台 across the south wall
    OUT = dict(sh=10, t=9, taper=1, step_slope="27", tunnels=1)   # 城墙: 9 thick at the foot, 7 at the top, 10 high;
    # 马道 at 27° only on the pieces _real_madao picks (门洞 无: none on the rest)
    # (front: the outer face, the 垛口 on it; the pieces stop at the 城台 of the towers: a wall piece owns its column up
    # to its walk, and where it ran under a 城台 its 收分 left holes in the 城台's top in front of the tower's door)
    n_out = len(walls)
    wall_x(0, 12, E - 12, [(352, 400)], front="north", **OUT)
    wall_x(S - 8, 12, E - 12, [sw_gap], front="south", **OUT)
    side_gate = (769, 811)                           # 东华门 / 西华门 (centre z 790)
    wall_z(0, 12, S - 12, [(side_gate[0] - 3, side_gate[1] + 3)], front="west", **OUT)
    wall_z(E - 8, 12, S - 12, [(side_gate[0] - 3, side_gate[1] + 3)], front="east", **OUT)
    outer = walls[n_out:]
    for cx, cz in ((1, 1), (E - 1, 1), (1, S - 1), (E - 1, S - 1)):
        at(cx, cz - 7, 15, 15, "角楼", roof="shizi", double_eave=True, podium=10,
           tunnels=1, **dict(CT, caihua="xuanzi"))
    at(CX, -6, 43, 21, "神武门", front="north", roof="wudian", double_eave=True, podium=10, tunnels=3, **CT)
    add(-9, side_gate[0], 21, 43, "西华门", front="west", roof="wudian", double_eave=True, podium=10, tunnels=3, **CT)
    add(E - 10, side_gate[0], 21, 43, "东华门", front="east", roof="wudian", double_eave=True, podium=10, tunnels=3, **CT)
    at(CX, 937, 61, 25, "午门", roof="wudian", double_eave=True, podium=12, tunnels=3, **CT)
    for wx in (329, 2 * CX - 329):                   # the U: a pavilion at each corner, the 雁翅楼 down each wing
        at(wx, 937, 23, 21, roof="pyramid", double_eave=True, podium=12, tunnels=1, **CT)
        at(wx, 964, 15, 63, front="east" if wx < CX else "west", roof="xieshan", podium=12, tunnels=1, **CT)
        at(wx, 1032, 23, 23, roof="pyramid", double_eave=True, podium=12, tunnels=1, **CT)

    # ---- 太和门广场: galleries with 熙和门 / 协和门, the 内金水河 and its five bridges, 太和门 ----
    for x0, front, name in ((285, "east", "熙和门"), (458, "west", "协和门")):
        gallery(x0, 786, 931, front, [(845, 871)])
        add(x0 - 2, 845, 13, 27, name, front=front, roof="xieshan", open="through", **HALL)
    at(CX, 812, 67, 11, "内金水桥", special="river")
    pave(CX, 791, 810)
    pave(CX, 824, 931)
    at(CX, 764, 41, 21, "太和门", roof="xieshan", double_eave=True, sh=9, open="through", **dict(HALL, podium=3, caihua="hexi", beasts=3))
    for cx, name in ((CX - 70, "贞度门"), (CX + 70, "昭德门")):
        at(cx, 769, 21, 11, name, roof="xieshan", open="through", **HALL)
    wall_x(773, 269, 483, [(356, 396), (CX - 80, CX - 60), (CX + 60, CX + 80)], sh=9)

    # ---- 前三殿: the galleries round the court (体仁阁 / 弘义阁, 崇楼 on the corners), the three halls ----
    for x0, front in ((259, "east"), (484, "west")):
        gallery(x0, 405, 769, front, [(525, 539), (679, 721), (753, 769)])
    for x0, front in ((259, "east"), (479, "west")):  # 崇楼 on the court's corners
        for z0 in (525, 753):
            add(x0, z0, 15, 15, front=front, roof="xieshan", double_eave=True, **HALL)
    for x0, front, name in ((259, "east", "弘义阁"), (477, "west", "体仁阁")):
        add(x0, 679, 17, 43, name, front=front, storeys=2, roof="wudian", **dict(HALL, uses="scripture_store"))
    th = at(CX, 547, 63, 37, "太和殿", roof="wudian", double_eave=True, sh=12, brackets=4, **dict(GREAT, beasts=5, uses="throne_hall"))
    at(CX, 493, 25, 25, "中和殿", roof="pyramid", sh=9, **GREAT)
    at(CX, 439, 49, 25, "保和殿", roof="xieshan", double_eave=True, sh=10, **GREAT)
    pave(CX, 598, 762)
    for i, (dx, var) in enumerate(((-26, 2), (26, 1))):                     # 嘉量 west, 日晷 east, in front of 太和殿
        at(CX + dx, 600, 3, 3, special="ornament", variant=var)
    for dx in (-40, 40):
        for zz in (612, 640, 668):
            at(CX + dx, zz, 3, 3, special="ornament", variant=3)              # 铜缸
    for z, a in ((538, 274), (403, 269)):            # 中右门 / 中左门 and 后右门 / 后左门: walls from the galleries to the 三台
        wall_x(z, a, 311, [(286, 294)], sh=7)
        wall_x(z, 441, 2 * CX - a, [(2 * CX - 294, 2 * CX - 286)], sh=7)
        gate_x(290, z, 9, "gate")
        gate_x(2 * CX - 290, z, 9, "gate")

    # ---- 乾清门广场: 隆宗门 / 景运门 at its ends, 内右门 / 内左门 into the long streets ----
    wall_z(259, 348, 402, [(369, 381)], sh=8)
    gate_z(259, 375, 13, "gate", "west", "隆宗门")
    wall_z(491, 348, 402, [(369, 381)], sh=8)
    gate_z(491, 375, 13, "gate", "east", "景运门")
    wall_x(345, 262, 316, [(308, 316)])
    wall_x(345, 436, 490, [(436, 444)])
    gate_x(312, 345, 9, "gate")
    gate_x(440, 345, 9, "gate")
    for dx in (-30, 30):
        at(CX + dx, 352, 3, 3, special="ornament", variant=3)

    # ---- 后三宫: 乾清门, 乾清宫, 交泰殿, 坤宁宫, 坤宁门, the galleries down both sides ----
    at(CX, 335, 29, 13, "乾清门", roof="xieshan", open="through", **HALL)
    wall_x(339, 317, 435, [(362, 390)], sh=8)
    for x0, front in ((317, "east"), (427, "west")):
        gallery(x0, 135, 337, front, [(262, 274)])
        gate_z(x0 + 3, 268, 9, "glazedgate", front)                          # 月华门 / 日精门
    at(CX, 256, 45, 25, "乾清宫", roof="wudian", double_eave=True, sh=8, **dict(HALL, podium=5, caihua="hexi", beasts=3, pitch=1, overhang=2))
    at(CX, 220, 15, 15, "交泰殿", roof="pyramid", **dict(HALL, podium=3, caihua="hexi"))
    at(CX, 178, 45, 21, "坤宁宫", roof="wudian", double_eave=True, **dict(HALL, podium=3, caihua="hexi", beasts=3))
    at(CX, 129, 17, 11, "坤宁门", roof="xieshan", open="through", **dict(HALL, podium=1, podium_style="plain"))
    wall_x(132, 309, 443, [(368, 384)], sh=8)
    pave(CX, 289, 333)
    pave(CX, 205, 219, 9)
    pave(CX, 141, 175, 9)

    # ---- 御花园 (135 x 80): 钦安殿, 万春亭 / 千秋亭, 浮碧亭 / 澄瑞亭, 延晖阁, 堆秀山 with 御景亭, 顺贞门 ----
    wall_x(49, 309, 443, [(368, 384)], sh=7)
    gate_x(CX, 49, 17, "glazedgate", "顺贞门")
    wall_z(309, 52, 131)
    wall_z(441, 52, 131)
    at(CX, 60, 25, 17, "钦安殿", roof="luding", double_eave=True, **HALL)
    for cx, name in ((CX - 40, "千秋亭"), (CX + 40, "万春亭")):
        for r in _preset_rooms("故宫/亭/千秋亭.json", cx - 9, 97, rng):
            rooms.append(r)
        rooms[-5]["label"] = name
    for cx, name in ((CX - 48, "澄瑞亭"), (CX + 48, "浮碧亭")):
        at(cx, 74, 9, 9, name, roof="pyramid", open="open", podium=1, base="white")
    at(323, 54, 15, 11, "延晖阁", storeys=2, roof="xieshan", **dict(HALL, podium=1, podium_style="plain"))
    at(428, 56, 9, 9, "堆秀山", roof="pyramid", open="open", podium=10, podium_style="chengtai", base="rubble", tunnels=1, uses="lookout")
    pave(CX, 81, 127)
    pave(CX, 19, 47)

    # ---- 东六宫 / 西六宫 (49 x 49 each, a 3 x 3 grid with the 五所 to the north), 斋宫, 毓庆宫, 养心殿 ----
    rows = (221, 167, 113)                           # south to north
    six = (((445, ("景仁宫", "承乾宫", "钟粹宫")), (501, ("延禧宫", "永和宫", "景阳宫"))),
           ((259, ("永寿宫", "翊坤宫", "储秀宫")), (203, ("太极殿", "长春宫", "咸福宫"))))
    for side in six:
        for x0, names in side:
            for z0, name in zip(rows, names):
                _six_palace(x0, z0, name, rng, rooms, walls, small=name in ("景阳宫", "咸福宫"))
    _five_courts(445, 57, "乾东五所", rng, rooms, walls)
    _five_courts(203, 57, "乾西五所", rng, rooms, walls)
    _court(445, 277, 49, 63, "斋宫", rng, rooms, walls, hall=dict(w=25, d=13, roof="xieshan"), back=None)
    _court(501, 277, 49, 69, "毓庆宫", rng, rooms, walls, hall=dict(w=21, d=11, roof="xieshan"), back=dict(w=21, d=11, roof="yingshan"),
           link=True)
    _court(226, 277, 81, 63, "养心殿", rng, rooms, walls, hall=dict(w=37, d=13, roof="xieshan"), back=dict(w=33, d=11, roof="yingshan"),
           link=True, wings=True)

    # ---- east of the 前朝: 奉先殿, 箭亭, 南三所, 文华殿 and 文渊阁 ----
    _court(557, 281, 65, 119, "奉先殿", rng, rooms, walls, hall=dict(w=45, d=23, roof="wudian", double_eave=True, podium=3),
           back=dict(w=45, d=11, roof="xieshan"), link=True)
    at(555, 440, 29, 15, "箭亭", roof="xieshan", **HALL)
    for k, x0 in enumerate((600, 631, 662)):
        _court(x0, 520, 29, 101, "南三所" if k == 1 else "", rng, rooms, walls, hall=dict(w=13, d=9, roof="xieshan"),
               back=dict(w=19, d=9, roof="yingshan"), middle=dict(w=19, d=9, roof="yingshan"), mat=dict(walls="palace", theme="jade"))
    _court(520, 650, 91, 131, "文华殿", rng, rooms, walls, hall=dict(w=29, d=15, roof="xieshan"), back=dict(w=29, d=15, roof="xieshan"),
           link=True, extra=[dict(z=652, w=35, d=15, label="文渊阁", storeys=2, roof="xieshan", walls="darkwood", theme="black",
                                  pond=True)])

    # ---- west: 武英殿, 宝蕴楼, 南薰殿, 慈宁宫 and its garden, 寿康宫, 寿安宫, 英华殿, 雨花阁, 建福宫花园 ----
    _court(142, 680, 71, 101, "武英殿", rng, rooms, walls, hall=dict(w=29, d=15, roof="xieshan"), back=dict(w=29, d=15, roof="xieshan"),
           link=True)
    at(177, 784, 19, 7, special="river")             # the 金水河 and its bridge in front of 武英门
    at(95, 695, 41, 9, "宝蕴楼", storeys=2, roof="wudian", walls="greybrick", theme="grey", podium=1, uses="treasury")
    for x0 in (75, 107):
        add(x0, 704, 9, 13, storeys=2, roof="wudian", walls="greybrick", theme="grey", podium=1, front="east" if x0 < 95 else "west",
            uses="treasury")
    _court(100, 820, 41, 51, "南薰殿", rng, rooms, walls, hall=dict(w=21, d=11, roof="xieshan"), back=None)
    _court(151, 349, 75, 99, "慈宁宫", rng, rooms, walls, hall=dict(w=35, d=17, roof="xieshan", double_eave=True),
           back=dict(w=31, d=13, roof="xieshan"), gatehall=True)
    _court(163, 460, 51, 131, "慈宁宫花园", rng, rooms, walls, hall=dict(w=21, d=13, roof="xieshan", z=480), back=None,
           pavilions=[(188, 540, 11, "临溪亭"), (175, 570, 9, ""), (201, 570, 9, "")], gate="gate")
    _court(81, 341, 61, 111, "寿康宫", rng, rooms, walls, hall=dict(w=25, d=13, roof="xieshan"), back=dict(w=25, d=11, roof="yingshan"))
    _court(63, 225, 79, 107, "寿安宫", rng, rooms, walls, hall=dict(w=33, d=13, roof="xieshan"), back=dict(w=33, d=11, roof="yingshan"))
    _court(70, 140, 65, 77, "英华殿", rng, rooms, walls, hall=dict(w=25, d=13, roof="xieshan"), back=None)
    _court(151, 180, 49, 91, "", rng, rooms, walls, hall=dict(w=25, d=17, roof="pyramid", storeys=3, z=225, label="雨花阁",
                                                                       caihua="hexi", beasts=3), back=dict(w=25, d=11, roof="xieshan"))
    _court(110, 45, 61, 67, "建福宫花园", rng, rooms, walls, hall=dict(w=17, d=17, roof="pyramid", double_eave=True, storeys=2,
                                                                  label="延春阁", z=62), back=None,
           pavilions=[(125, 92, 9, ""), (155, 92, 9, "")])

    # ---- 宁寿宫 (119 x 401): 九龙壁, 皇极门, 宁寿门, 皇极殿, 宁寿宫; behind 养性门 three ways: the garden (west),
    # 养性殿 / 乐寿堂 / 颐和轩 / 景祺阁 (middle), 畅音阁 / 阅是楼 / 景福宫 / 梵华楼 / 佛日楼 (east) ----
    NX = 686
    wall_z(627, 9, 406, [], sh=8)
    wall_z(743, 9, 406, [], sh=8)
    wall_x(404, 630, 742, [(676, 696)], sh=8)
    gate_x(NX, 404, 21, "glazedgate", "皇极门")
    at(NX, 412, 29, 3, "九龙壁", special="screen", front="north")
    at(NX, 360, 27, 13, "宁寿门", roof="xieshan", open="through", **HALL)
    wall_x(365, 630, 742, [(673, 699)], sh=8)
    at(NX, 300, 45, 25, "皇极殿", roof="wudian", double_eave=True, sh=8, **dict(HALL, podium=4, caihua="hexi", beasts=3))
    at(NX, 250, 35, 17, "宁寿宫", roof="xieshan", **HALL)
    pave(NX, 331, 357)
    wall_x(238, 630, 742, [(642, 650), (680, 692), (722, 730)], sh=8)
    gate_x(NX, 238, 13, "glazedgate", "养性门")
    gate_x(646, 238, 9, "gate")
    gate_x(726, 238, 9, "gate")
    wall_z(662, 30, 237, [], sh=7)
    wall_z(708, 30, 237, [], sh=7)
    at(NX, 205, 31, 17, "养性殿", roof="xieshan", **HALL)
    at(NX, 160, 37, 23, "乐寿堂", roof="xieshan", **dict(HALL, uses="study"))
    at(NX, 128, 31, 9, "颐和轩", roof="xieshan", **dict(HALL, podium=1, podium_style="plain"))
    at(NX, 80, 31, 15, "景祺阁", storeys=2, roof="xieshan", **dict(HALL, podium=1, podium_style="plain"))
    at(645, 205, 13, 11, "古华轩", roof="xieshan", open="open", podium=1)
    at(645, 172, 19, 11, "遂初堂", roof="xieshan", **dict(HALL, podium=1, podium_style="plain"))
    at(645, 140, 19, 11, "萃赏楼", storeys=2, roof="xieshan", **dict(HALL, podium=1, podium_style="plain"))
    at(645, 100, 21, 21, "符望阁", storeys=2, roof="pyramid", **dict(HALL, caihua="hexi"))
    at(645, 80, 27, 9, "倦勤斋", roof="juanpeng", podium=1)
    at(726, 198, 17, 17, "畅音阁", front="north", storeys=3, roof="xieshan", open="front", podium=1, caihua="hexi", uses="lookout")
    at(726, 215, 21, 9, storeys=2, front="north", roof="juanpeng", podium=1, uses="backstage")   # 扮戏楼
    at(726, 160, 25, 13, "阅是楼", storeys=2, roof="juanpeng", **dict(HALL, podium=1, podium_style="plain", uses="lookout"))
    at(726, 55, 23, 23, "景福宫", roof="xieshan", **HALL)
    at(725, 15, 31, 11, "梵华楼", storeys=2, roof="xieshan", podium=1, caihua="xuanzi", uses="temple_hall")
    at(703, 15, 13, 11, "佛日楼", storeys=2, roof="xieshan", podium=1, caihua="xuanzi", uses="temple_hall")

    # ---- the moat: 52 wide, 20 off the wall (off by default - see CITY_REAL_SOURCES) ----
    if moat:                                         # (the south run stops either side of the 午门's forecourt)
        for z0, spans in ((-72, [(-72, E + 72)]), (S + 21, [(-72, sw_gap[0] - 13), (sw_gap[1] + 13, E + 72)])):
            for a0, a1 in spans:
                for p0, p1 in _split(a0, a1 - (a1 - a0) % 2, 251):
                    add(p0, z0, p1 - p0 + 1, 51, "筒子河" if p0 == -72 and z0 < 0 else "", special="river")
        for x0 in (-72, E + 21):
            for p0, p1 in _split(-21, S + 20 - (S + 41) % 2, 251):
                add(x0, p0, 51, p1 - p0 + 1, special="river", front="east")
    allr = walls + rooms
    _real_madao(outer, allr, {"north": (352, 400), "south": sw_gap, "west": side_gate, "east": side_gate})
    for r in allr:                                   # (odd frames, but for a preset's own pieces: 千秋亭's porches)
        assert (r["x1"] - r["x0"]) % 2 == 0 and (r["z1"] - r["z0"]) % 2 == 0 or r.get("_preset"), r.get("label")
        r.pop("_preset", None)
    xs = [r["x0"] for r in allr] + [r["x1"] for r in allr]
    zs = [r["z0"] for r in allr] + [r["z1"] for r in allr]
    for r in allr:                                   # doors by where each hall stands (axis_open)
        r["doors"], r["open"] = axis_open(r, CX)
    return dict(name="紫禁城·1:1", kind="city_real", jin=0, seed=seed, tier="mortal", look=0,
                size=[max(xs) - min(xs) + 1, max(zs) - min(zs) + 1], origin=[min(xs), min(zs)], rooms=allr)


def _real_madao(outer, rooms, gates):
    """The 紫禁城's 马道: a few, by the gates (not one a wall piece) - on each side of each side's gate the wall piece
    nearest it whose 马道 foot stays clear (_madao_clear), so two a side, eight in all; the rest none (门洞 无). The
    walk runs on round the whole wall over the 城台 (compose._wall_walks), so one up is enough to walk all of it."""
    for front, (g0, g1) in gates.items():
        ns = front in ("north", "south")
        side = sorted((r for r in outer if r["front"] == front), key=lambda r: r["x0"] if ns else r["z0"])
        for before in (True, False):
            near = [r for r in side if ((r["x1"] if ns else r["z1"]) < g0 if before else (r["x0"] if ns else r["z0"]) > g1)]
            for r in (reversed(near) if before else near):
                r["tunnels"] = 0
                if _madao_clear(r, rooms):
                    break
                r["tunnels"] = 1


def _odd_pieces(a0, a1, most):
    """a0..a1 (inclusive) cut into pieces each odd long and at most `most`: an even length into an even number of them."""
    n = a1 - a0 + 1
    k = -(-n // most)
    if k % 2 != n % 2:
        k += 1
    s = (n // k) - (1 - (n // k) % 2)               # each odd, the spare (even) shared out two at a time
    sizes = [s] * k
    i = 0
    while sum(sizes) < n:
        sizes[i % k] += 2
        i += 1
    out, a = [], a0
    for c in sizes:
        out.append((a, a + c - 1))
        a += c
    return out


def _preset_rooms(rel, x0, z0, rng):
    """The rooms of a library preset (data/library/<rel>), moved so its frame's corner is at (x0, z0)."""
    d = json.loads((Path(__file__).resolve().parent / "data" / "library" / rel).read_text(encoding="utf-8"))
    out = []
    for r in d["rooms"]:
        r = dict(r, x0=r["x0"] + x0, x1=r["x1"] + x0, z0=r["z0"] + z0, z1=r["z1"] + z0, seed=rng.randrange(1 << 30), _preset=True)
        out.append(r)
    return out


def _six_palace(x0, z0, name, rng, rooms, walls, small=False):
    """One of the 东西六宫: a 49 x 49 二进院 - its walls, a 琉璃门 in the south one, the 前殿 (5 bays 歇山; 3 bays 庑殿 for
    景阳宫 / 咸福宫) with a wall either side of it, 东西配殿 in the front court, the 后殿 (5 bays 硬山) at the back."""
    gold = dict(walls="palace", theme="gold")
    c = x0 + 24
    for z, gaps in ((z0, ()), (z0 + 46, [(c - 4, c + 4)])):
        for p0, p1 in wall_pieces(x0, x0 + 48, gaps):
            walls.append(room(p0, z, p1, z + 2, rng, special="wall", front="south", sh=7, **gold))
    for x in (x0, x0 + 46):
        for p0, p1 in wall_pieces(z0 + 3, z0 + 45):
            walls.append(room(x, p0, x + 2, p1, rng, special="wall", front="east", sh=7, **gold))
    rooms.append(room(c - 4, z0 + 46, c + 4, z0 + 48, rng, special="glazedgate", front="south", sh=6, **gold))
    rooms.append(room(c - 10, z0 + 4, c + 10, z0 + 10, rng, roof="yingshan", podium=1, uses="bedroom", caihua="xuanzi", **gold))
    fw = 7 if small else 10
    rooms.append(room(c - fw, z0 + 19, c + fw, z0 + 29, rng, roof="wudian" if small else "xieshan", podium=2, podium_style="xumizuo",
                      base="white", caihua="xuanzi", beasts=2, uses="main_room", label=name, **gold))
    e = 2 if small else 0                             # (up to the 前殿's platform: a narrower one leaves no hole beside it)
    for a0, a1 in ((x0 + 3, x0 + 11 + e), (x0 + 37 - e, x0 + 45)):
        walls.append(room(a0, z0 + 23, a1, z0 + 25, rng, special="wall", front="south", sh=6, **gold))
    rooms.append(room(x0 + 3, z0 + 36, x0 + 9, z0 + 44, rng, front="east", roof="yingshan", podium=1, caihua="xuanzi", uses="study", **gold))
    rooms.append(room(x0 + 39, z0 + 36, x0 + 45, z0 + 44, rng, front="west", roof="yingshan", podium=1, caihua="xuanzi", uses="study", **gold))


def _five_courts(x0, z0, name, rng, rooms, walls):
    """乾东五所 / 乾西五所: five narrow compounds side by side (21 wide, 51 deep), three rows of rooms in each, a gate in
    each one's south wall."""
    gold = dict(walls="palace", theme="gold")
    W, D = 105, 51
    gates = [(x0 + 21 * k + 7, x0 + 21 * k + 13) for k in range(5)]
    for z, gaps in ((z0, ()), (z0 + D - 3, gates)):
        for p0, p1 in wall_pieces(x0, x0 + W - 1, gaps):
            walls.append(room(p0, z, p1, z + 2, rng, special="wall", front="south", sh=6, **gold))
    for k in range(6):
        xw = min(x0 + 21 * k, x0 + W - 3)
        for p0, p1 in wall_pieces(z0 + 3, z0 + D - 4):
            walls.append(room(xw, p0, xw + 2, p1, rng, special="wall", front="east", sh=6, **gold))
    for k, (g0, g1) in enumerate(gates):
        rooms.append(room(g0, z0 + D - 3, g1, z0 + D - 1, rng, special="gate", front="south", sh=6, **gold))
        c = (g0 + g1) // 2
        for j, zz in enumerate((z0 + 5, z0 + 19, z0 + 33)):
            r = room(c - 6, zz, c + 6, zz + 6, rng, roof="yingshan" if j < 2 else "xieshan", podium=1, caihua="xuanzi",
                     uses="bedroom" if j < 2 else "main_room", **gold)
            if k == 2 and j == 2:
                r["label"] = name
            rooms.append(r)


def _court(x0, z0, W, D, name, rng, rooms, walls, hall, back=None, middle=None, link=False, wings=False, gatehall=False,
           extra=(), pavilions=(), gate="glazedgate", mat=None):
    """A walled compound W x D (both odd) facing south: a gate in the middle of the south wall (a gate hall with
    gatehall), the main hall (hall: w, d, roof, ... and its z if given, else 2/5 of the way down), a back hall behind it
    (back), one between (middle), the two joined by a 穿廊 (link: a 工字殿), 配殿 either side of the front court (wings),
    further buildings (extra: z, w, d, label, ... - pond: a pool with a bridge in front of it) and pavilions
    [(cx, z0, size, label)]. name labels the main hall unless the hall gives its own label."""
    mat = mat or dict(walls="palace", theme="gold")
    c = x0 + W // 2
    gw = 21 if gatehall else 9
    for z, gaps in ((z0, ()), (z0 + D - 3, [(c - gw // 2, c + gw // 2)])):
        for p0, p1 in wall_pieces(x0, x0 + W - 1, gaps):
            walls.append(room(p0, z, p1, z + 2, rng, special="wall", front="south", sh=7, **mat))
    for x in (x0, x0 + W - 3):
        for p0, p1 in wall_pieces(z0 + 3, z0 + D - 4):
            walls.append(room(x, p0, x + 2, p1, rng, special="wall", front="east", sh=7, **mat))
    if gatehall:
        rooms.append(room(c - 10, z0 + D - 8, c + 10, z0 + D + 2, rng, roof="xieshan", open="through", podium=2, podium_style="xumizuo",
                          base="white", caihua="xuanzi", beasts=2, uses="lookout", **mat))
    else:
        rooms.append(room(c - gw // 2, z0 + D - 3, c + gw // 2, z0 + D - 1, rng, special=gate, front="south", sh=6, **mat))

    def bld(spec, z, **dflt):
        s = dict(dflt)
        s.update(spec)
        w, d = s.pop("w"), s.pop("d")
        s.pop("z", None)
        label = s.pop("label", "")
        pond = s.pop("pond", False)
        kw = dict(mat)
        kw.update(podium=2, podium_style="xumizuo", base="white", caihua="xuanzi", beasts=2, uses="main_room")
        kw.update(s)
        if not int(kw.get("podium", 0) or 0):
            kw["podium_style"] = "plain"
        r = room(c - w // 2, z, c + w // 2, z + d - 1, rng, **kw)
        if label:
            r["label"] = label
        rooms.append(r)
        if pond:
            rooms.append(room(c - 9, z + d + 3, c + 9, z + d + 9, rng, special="river", **mat))
        return r

    zb = z0 + 5
    for e in extra:
        bld(e, e["z"])
        zb = max(zb, e["z"] + e["d"] + (14 if e.get("pond") else 6))
    b = None
    if back:
        b = bld(back, zb, podium=1, podium_style="plain")
    if middle:
        m = bld(middle, zb + back["d"] + 8 if back else zb, podium=1, podium_style="plain")
        b = m
    hz = hall.get("z")
    if hz is None:
        hz = (b["z1"] + (16 if link else 9)) if b else z0 + (2 * D) // 5 - hall["d"] // 2
    h = bld(hall, hz)
    if not hall.get("label") and name:
        h["label"] = name
    if link and b is not None:                     # 穿廊 between the back hall and the main hall
        rooms.append(room(c - 4, b["z1"] + 1, c + 4, h["z0"] - 1, rng, roof="juanpeng", podium=1, uses="main_room", **mat))
    if wings:                                      # 配殿 in the front court
        fz0 = h["z1"] + 8
        fz1 = z0 + D - 6
        if (fz1 - fz0) % 2:
            fz1 -= 1
        if fz1 - fz0 >= 6:
            for xa, xb, front in ((x0 + 5, x0 + 15, "east"), (x0 + W - 16, x0 + W - 6, "west")):
                rooms.append(room(xa, fz0, xb, fz1, rng, front=front, roof="yingshan", podium=1, caihua="xuanzi", uses="study", **mat))
    for cx, pz, s, label in pavilions:
        r = room(cx - s // 2, pz, cx + s // 2, pz + s - 1, rng, roof="pyramid", open="open", podium=1, **mat)
        if label:
            r["label"] = label
        rooms.append(r)


def site_free(seed):
    """A compound whose layout is drawn at random (see the module text); every frame odd, walls cut round the gates."""
    rng = random.Random(f"free/{seed}")
    CW = odd(rng.randint(25, 61))
    c = CW // 2
    mat = dict(walls="palace", theme="gold")
    rooms, walls = [], []
    walled = rng.random() < 0.9
    inner0, inner1 = (T, CW - T - 1) if walled else (0, CW - 1)

    def court(z):
        """One court from z: maybe a main hall at the back, wings, a pavilion; returns where it ends."""
        depth = odd(rng.randint(15, 31))
        hall_d = odd(rng.randint(7, 13))
        if rng.random() < 0.9:                    # a main hall across the back, somewhere along it
            hw = odd(rng.randint(9, max(9, inner1 - inner0 - 6)))
            hx0 = rng.randint(inner0, inner1 - hw + 1) if rng.random() < 0.3 else c - hw // 2
            rooms.append(room(hx0, z, hx0 + hw - 1, z + hall_d - 1, rng, front="south", _role="main", **mat))
            room_w = min(hx0 - inner0, inner1 - (hx0 + hw - 1))   # (both sides: a hall set off the middle had its east
            if rng.random() < 0.5 and room_w >= 6:            # 耳房 run through the side wall and out of the compound)
                ew = min(7, room_w - 1)
                ew -= 0 if ew % 2 else 1
                if ew >= 5:
                    rooms.append(room(hx0 - ew, z + 1, hx0 - 1, z + hall_d - 2, rng, front="south", _ear=(-1, inner0), **mat))
                    rooms.append(room(hx0 + hw, z + 1, hx0 + hw + ew - 1, z + hall_d - 2, rng, front="south", _ear=(1, inner1), **mat))
        else:
            hall_d = 0
        z0 = z + hall_d + 1
        z1 = z + depth - 1
        sides = rng.choice(("none", "west", "east", "both", "both"))
        ww = odd(rng.randint(5, 11))
        if z1 - z0 >= 8:
            wl0, wl1 = z0 + 1, z1 - 1
            wl1 -= 0 if (wl1 - wl0) % 2 == 0 else 1
            if sides in ("west", "both"):
                rooms.append(room(inner0, wl0, inner0 + ww - 1, wl1, rng, front="east", **mat))
            if sides in ("east", "both"):
                rooms.append(room(inner1 - ww + 1, wl0, inner1, wl1, rng, front="west", **mat))
        px0 = inner0 + (ww if sides in ("west", "both") else 0)
        px1 = inner1 - (ww if sides in ("east", "both") else 0)
        if rng.random() < 0.7 and px1 - px0 >= 4 and z1 - z0 >= 4:
            rooms.append(room(px0, z0, px0 + ((px1 - px0) // 2) * 2, z0 + ((z1 - z0) // 2) * 2, rng, special="paving", **mat))
        if rng.random() < 0.35 and px1 - px0 >= 15 and z1 - z0 >= 13:      # a pavilion out in the court
            pc = odd(rng.randint(7, 9))
            px = rng.randint(px0 + 2, px1 - pc - 1)
            pz = rng.randint(z0 + 2, z1 - pc - 1)
            rooms.append(room(px, pz, px + pc - 1, pz + pc - 1, rng, front="south", **mat))
        elif rng.random() < 0.3 and px1 - px0 >= 9:                         # or 陈设 down the middle
            for k in range(rng.randint(1, 3)):
                oz = z0 + 2 + k * 5
                if oz + 2 < z1:
                    rooms.append(room(c - 5, oz, c - 3, oz + 2, rng, special="ornament", variant=rng.randint(0, 3), **mat))
                    rooms.append(room(c + 3, oz, c + 5, oz + 2, rng, special="ornament", variant=rng.randint(0, 3), **mat))
        return z + depth

    def divider(z):
        """What stands between two courts: a wall with a gate (of some kind, somewhere), a gate hall, or nothing."""
        kind = rng.choice(("chuihua", "glazedgate", "gate", "hall", "none"))
        if kind == "none":
            return z
        gw = 9 if kind != "hall" else odd(rng.randint(11, 17))
        gx = c - gw // 2 if rng.random() < 0.7 else rng.randint(inner0 + 2, inner1 - gw - 1)
        wz = z
        if kind == "hall":                        # (rows of its own: across the wall it ran a row into each court, and
            rooms.append(room(gx, z, gx + gw - 1, z + 8, rng, front="south", open="through", **mat))   # its steps
            wz = z + 3                            # and the next hall's met there)
        else:
            rooms.append(room(gx, z - (1 if kind == "chuihua" else 0), gx + gw - 1, z + (3 if kind == "chuihua" else 2), rng,
                              special=kind, front="south", sh=6, **mat))
        for a0, a1 in wall_pieces(inner0, inner1, [(gx, gx + gw - 1)]):
            walls.append(room(a0, wz, a1, wz + 2, rng, special="wall", front="south", sh=rng.choice((5, 6, 7)), **mat))
        return wz + T + (3 if kind == "hall" else 0)

    z = (T if walled else 0) + 2
    if rng.random() < 0.35:                        # a back row along the back wall
        rooms.append(room(inner0, z, inner1 - (0 if (inner1 - inner0) % 2 == 0 else 1), z + 6, rng, front="south", **mat))
        z += 9
    for k in range(rng.randint(1, 3)):
        if k:
            z = divider(z) + 1
        z = court(z)
    # the outer yard: a 倒座 row facing in, a river, or just space
    yard = rng.choice(("plain", "daozuo", "river", "plain"))
    gate_kind = rng.choice(("gate", "glazedgate", "hall", "paifang_out"))
    gw = 7 if gate_kind == "gate" else 13 if gate_kind == "glazedgate" else odd(rng.randint(11, 15))
    gx = c - gw // 2 if rng.random() < 0.6 else (inner1 - gw - 1 if rng.random() < 0.5 else inner0 + 1)
    if yard == "river" and inner1 - inner0 >= 12:
        rooms.append(room(inner0, z + 1, inner1 - (0 if (inner1 - inner0) % 2 == 0 else 1), z + 7, rng, special="river", **mat))
        z += 9
    elif yard == "daozuo":
        z += 3
        for x0, x1 in _gap_pieces(inner0, inner1, [(gx - 1, gx + gw)]):
            if x1 - x0 >= 4:
                rooms.append(room(x0, z, x0 + ((x1 - x0) // 2) * 2, z + 6, rng, front="north", **mat))
        z += 7
    else:
        z += rng.choice((3, 5, 7))
    if walled:
        if gate_kind == "hall":
            rooms.append(room(gx, z - 2, gx + gw - 1, z + 4, rng, front="south", open="through", **mat))
        elif gate_kind in ("gate", "glazedgate"):
            rooms.append(room(gx, z, gx + gw - 1, z + 2, rng, special=gate_kind, front="south", sh=6, **mat))
        gap = [(gx, gx + gw - 1)] if gate_kind != "paifang_out" else [(c - 3, c + 3)]
        for a0, a1 in wall_pieces(0, CW - 1, gap):
            walls.append(room(a0, z, a1, z + 2, rng, special="wall", front="south", sh=rng.choice((5, 7, 9)), **mat))
        D = z + T
        for a0, a1 in wall_pieces(0, CW - 1):
            walls.append(room(a0, 0, a1, 2, rng, special="wall", front="south", sh=rng.choice((5, 7, 9)), **mat))
        for xa in (0, CW - T):
            for a0, a1 in wall_pieces(T, D - T - 1):
                walls.append(room(xa, a0, xa + 2, a1, rng, special="wall", front="east", sh=7, **mat))
        if gate_kind == "paifang_out":
            rooms.append(room(c - 8, D + 3, c + 8, D + 7, rng, special="paifang", sh=7, **mat))
        if rng.random() < 0.5:                     # a 照壁 facing the gate, outside
            sx = gx + gw // 2 if gate_kind != "paifang_out" else c
            rooms.append(room(sx - 5, D + (10 if gate_kind == "paifang_out" else 4), sx + 5, D + (12 if gate_kind == "paifang_out" else 6), rng,
                              special="screen", front="north", **mat))
    else:
        D = z
    return dict(name="随机院落", kind="free", jin=0, seed=seed, tier="mortal", look=0, size=[CW, D + 12], rooms=walls + rooms)


def randomize(d, rng):
    """随机一院 全参数随机: every building's every setting drawn at random (as 随机一栋); walls and tiles one random pick
    for the whole compound; the walls, gates, pavements and rivers keep their shape, in those materials."""
    import compose
    o = compose.options()
    walls = [w[0] for w in o["walls"]]
    wall, theme = rng.choice(walls), rng.choice([t[0] for t in o["themes"]])
    bay = rng.choice([b[0] for b in o["bay_styles"]])
    # one ground for the whole compound: every building on the same platform height, the main halls a step or more
    # up on a 须弥座 or 三层 (random heights per building made the floors jump about from one to the next)
    # (the compound's court is one flat ground at the foot of them all: the ordinary buildings 0-2 up, the main halls a
    # modest 1-3 more - 7 to 12 high plinths in a court stood the halls up like towers)
    p0 = rng.choice([0, 0, 1, 1, 2])
    lift = rng.choice([1, 2, 2, 3])
    main_style = rng.choice(["xumizuo", "tiers", "plain"])
    given = [(r, (r["x0"], r["z0"], r["x1"], r["z1"])) for r in d["rooms"] if not r.get("special")]
    for r in d["rooms"]:
        r["walls"], r["theme"] = wall, theme
        if r.get("special"):
            continue
        st = rng.randint(1, 5)
        ev = rng.randint(0, 2)
        r.update(storeys=st, sh=rng.choice([0, 4, 5, 6, 7, 8, 9, 10, 12, 14, 16]), taper=rng.randint(0, 3) if st > 1 else 0,
                 podium=rng.choice([0, 1, 2, 3, 4, 5, 6, 8, 10, 12]), podium_style=rng.choice(["plain", "xumizuo", "tiers", "chengtai"]),
                 tunnels=rng.randint(0, 4), base=rng.choice([b[0] for b in o["bases"]]), roof=rng.choice([x[0] for x in o["roofs"]]),
                 bay_style=bay, double_eave=ev >= 1, triple_eave=ev == 2, pitch=rng.randint(0, 4), overhang=rng.randint(0, 4),
                 lift=rng.randint(0, 4), beasts=rng.randint(0, 5), brackets=rng.randint(0, 4),
                 caihua=rng.choice(["", "xuanzi", "hexi", "sushi"]), horsehead=rng.random() < 0.5,
                 open=rng.choices(["closed", "open", "front", "through"], weights=(3, 3, 3, 1))[0],
                 doors=rng.choices(["front", "front_back", "four"], weights=(7, 2, 1))[0],   # (mostly the front only)
                 uses=[rng.choice([u[0] for u in o["uses"]]) for _ in range(8)],
                 floor_open=[rng.choice(["", "closed", "through", "open", "front"]) if rng.random() < 0.25 else "" for _ in range(8)],
                 variant=rng.randint(0, 3), roof_variant=rng.randint(0, 3))
        if r.get("_role") == "main":
            r.update(podium=p0 + lift, podium_style=main_style)
        else:
            r.update(podium=p0, podium_style="plain")
        g = rng.randint(0, 4)
        r.update(veranda=g in (1, 3), back_veranda=g in (2, 3), colonnade=g == 4)
        if rng.random() < 0.15:
            r.update(round=True, shape="round", round_roof=rng.choice(["cone", "helmet", "dome", "luding", "flat"]))
        if r.get("roof") == "yuanzan" and abs((r["x1"] - r["x0"]) - (r["z1"] - r["z0"])) > 2:
            r["roof"] = "pyramid"                      # (圆攒尖 is sized by the long side: on a long frame it reached 15 out)
        _fit_frame(r)
    _part_ears(d["rooms"])
    _close_walls(d["rooms"], given)
    _clear_steps(d["rooms"])
    d["name"] += "·全参数随机"
    return d


def _close_walls(rooms, given):
    """A wall that ran up to a building (a gate hall in it, a hall at its end) whose frame then shrank (_fit_frame) runs
    on into the cells the building gave up: they were a slot through the wall into the yard."""
    def inside(f, x, z):
        return f[0] <= x <= f[2] and f[1] <= z <= f[3]
    for w in rooms:
        if w.get("special") != "wall":
            continue
        along_x = w["x1"] - w["x0"] >= w["z1"] - w["z0"]
        lo, hi = ("x0", "x1") if along_x else ("z0", "z1")
        mid = (w["z0"] + w["z1"]) // 2 if along_x else (w["x0"] + w["x1"]) // 2
        for end, step in ((hi, 1), (lo, -1)):
            while True:
                a = w[end] + step
                x, z = (a, mid) if along_x else (mid, a)
                if not any(inside(f, x, z) and not inside((r["x0"], r["z0"], r["x1"], r["z1"]), x, z) for r, f in given)                         or any(inside((o["x0"], o["z0"], o["x1"], o["z1"]), x, z) for o in rooms if o is not w):
                    break                              # (only cells a building gave up, and nothing else stands on)
                w[end] = a


def _side_reach(r):
    """How far a room reaches past a side of its frame (left or right as seen from its front): its eaves (one more
    with 重檐, and one to spare - a gable reaches a cell past its 出檐), its platform's ring, a 周围廊."""
    eave = {0: 3, 1: 2, 2: 3, 3: 4, 4: 5}[int(r.get("overhang", 0) or 0)] + (2 if int(r.get("brackets", 0) or 0) >= 3 else 0)         + (1 if r.get("double_eave") or r.get("triple_eave") else 0) + 1
    return max(eave, _podium_out(r)) + (4 if r.get("colonnade") else 0)


def _step_rect(r):
    """The cells a room's front steps take (x0, z0, x1, z1): out from the middle of its front, as wide as the flight."""
    n = _steps(r)
    hw = 1 if r.get("podium_style") not in ("xumizuo", "tiers") else 4
    cx, cz = (r["x0"] + r["x1"]) // 2, (r["z0"] + r["z1"]) // 2
    fr = r.get("front", "south")
    if fr == "south":
        return (cx - hw, r["z1"] + 1, cx + hw, r["z1"] + n)
    if fr == "north":
        return (cx - hw, r["z0"] - n, cx + hw, r["z0"] - 1)
    if fr == "east":
        return (r["x1"] + 1, cz - hw, r["x1"] + n, cz + hw)
    return (r["x0"] - n, cz - hw, r["x0"] - 1, cz + hw)


def _clear_steps(rooms):
    """Front steps that would come down onto another building (inside what it reaches out to: its eaves, its platform,
    its own steps), a wall or a gate: the platform comes down a step at a time until they are clear (or it is gone).
    Between buildings facing each other across a narrow yard the steps met, or ran under the other's eaves."""
    def hit(a, b):
        return a[0] <= b[2] and b[0] <= a[2] and a[1] <= b[3] and b[1] <= a[3]

    blds = [r for r in rooms if not r.get("special")]
    solid = [(r["x0"], r["z0"], r["x1"], r["z1"]) for r in rooms if r.get("special") and r.get("special") not in ("paving", "river")]
    for r in blds:
        while int(r.get("podium", 0) or 0) > 0:
            st = _step_rect(r)
            others = [(o["x0"] - _reach(o), o["z0"] - _reach(o), o["x1"] + _reach(o), o["z1"] + _reach(o)) for o in blds if o is not r]                 + [_step_rect(o) for o in blds if o is not r] + solid
            if not any(hit(st, b) for b in others):
                break
            r["podium"] = int(r["podium"]) - 1
            if not r["podium"]:
                r["podium_style"] = "plain"


def _part_ears(rooms):
    """耳房 stand clear of the main hall: shrunk into their frames, a 5-7 wide 耳房 and the hall still had their eaves
    over the same cells, and the taller one's roof took them - half the 耳房's roof (a 方亭 as often as not) cut away.
    Each moves out (as far as the compound's side), then narrows from the hall's side, else it goes."""
    hall, drop = None, []
    for r in rooms:
        if r.get("_role") == "main":
            hall = r
        ear = r.pop("_ear", None)
        if not ear or hall is None:
            continue
        side, lim = ear
        short = _side_reach(r) + _side_reach(hall) - ((hall["x0"] - r["x1"] - 1) if side < 0 else (r["x0"] - hall["x1"] - 1))
        if short > 0:
            mv = max(0, min(short, (r["x0"] - lim) if side < 0 else (lim - r["x1"])))
            r["x0"] += side * mv
            r["x1"] += side * mv
            short -= mv
        if short > 0:
            cut = short + short % 2                       # (an even cut keeps the width odd)
            if r["x1"] - r["x0"] + 1 - cut < 5:
                drop.append(r)
            elif side < 0:
                r["x1"] -= cut
            else:
                r["x0"] += cut
    for r in drop:
        rooms.remove(r)


def _eave_zone(r):
    """The cells a room's eaves and platform reach over (x0, z0, x1, z1), a front or back gallery only on its side."""
    m = _reach(r) - (4 if (r.get("veranda") or r.get("back_veranda")) and not r.get("colonnade") else 0)
    ext = {d: m for d in ("south", "north", "east", "west")}
    back = {"south": "north", "north": "south", "east": "west", "west": "east"}
    fr = r.get("front", "south")
    ext[fr] += 4 if r.get("veranda") and not r.get("colonnade") else 0
    ext[back[fr]] += 4 if r.get("back_veranda") and not r.get("colonnade") else 0
    return (r["x0"] - ext["west"], r["z0"] - ext["north"], r["x1"] + ext["east"], r["z1"] + ext["south"])


def _zone(r):
    """The cells a room reaches out over (x0, z0, x1, z1): its frame grown by _reach."""
    m = _reach(r)
    return (r["x0"] - m, r["z0"] - m, r["x1"] + m, r["z1"] + m)


def _reach(r):
    """How far past its frame a room reaches: its eaves (出檐, 斗拱), its platform's ring (样式, height), its galleries."""
    eave = {0: 3, 1: 2, 2: 3, 3: 4, 4: 5}[int(r.get("overhang", 0) or 0)] + (2 if int(r.get("brackets", 0) or 0) >= 3 else 0)
    plat = _podium_out(r)
    gal = 4 if (r.get("colonnade") or r.get("veranda") or r.get("back_veranda")) else 0
    return max(eave, plat) + gal


def _podium_out(r):
    """How far a room's platform reaches past its frame (the one place it is reckoned): its 台基边距 (podium_margin,
    the cells of walk round the walls: 自动 one) and the style's own rim - a 须弥座's balustrade a cell further, 三层
    its lower tiers two cells each further out (bgen.podium_tiers), a 城台 three. A walk of one is reckoned two: bgen.widen_podium grows it a cell where a
    window sill or a lantern by the door stands in it, and its steps come out that cell further."""
    pod = int(r.get("podium", 0) or 0)
    if not pod:
        return 0
    import bgen
    import compose
    given = compose._podium_margin(r)
    m = max(given or 1, 2)                                # (what sticks out of the walls: one cell, and one to walk)
    step = bgen.tier_step(pod, given, step_run(r.get("step_slope")))   # (三层: 2, wider under a wide margin)
    style = r.get("podium_style")
    out = {"xumizuo": m + 1, "tiers": m + 1 + 2 * step, "chengtai": max(3, given or 0)}.get(style, m)
    if r.get("podium_shape") == "round" and r.get("shape") != "round":   # 台基形状 圆 under a square hall: the circle
        hx, hz = (r["x1"] - r["x0"]) // 2, (r["z1"] - r["z0"]) // 2         # round its corners (bgen.house)
        gal = 4 if (r.get("colonnade") or r.get("veranda") or r.get("back_veranda")) else 0
        out = (m + 1 + 2 * step if style == "tiers" else m + 1 if style == "xumizuo" else m)             + math.ceil(math.hypot(hx + gal, hz + gal)) - min(hx, hz)
    return out


def _steps(r):
    """How far a room's front steps reach past its frame: the platform's height times the cells its 坡度 (step_slope)
    takes a block (45°: a step a block; gentler: 2-4 cells a block; the one place step length is reckoned), its ring, a 须弥座's moulding (三层: its tiers
    stepping out), a front gallery."""
    pod = int(r.get("podium", 0) or 0)
    extra = _podium_out(r)
    run = step_run(r.get("step_slope")) if r.get("podium_style") != "chengtai" else 1     # (a 城台's steps: behind it)
    return (pod * run + extra if pod else 1) + (4 if r.get("veranda") or r.get("colonnade") else 0)


SOLID_SPECIALS = ("wall", "gate", "glazedgate", "chuihua", "screen", "river", "paifang", "ornament")


def _plat(r):
    """How far a room's platform (台基) reaches past its frame on the ground (_podium_out)."""
    return _podium_out(r)


def _foot(r):
    """What a room stands on, on the ground (x0, z0, x1, z1): its frame and its platform's ring."""
    p = _plat(r)
    return (r["x0"] - p, r["z0"] - p, r["x1"] + p, r["z1"] + p)


def _walk_rects(r, strip=WALK):
    """Each flight of a room's steps with the strip in front of its foot that stays free to walk (strip cells out):
    the front one, and behind and on the sides those its doors there come down (_opens_back, 四面门). None on a 城台
    (its stair is inside it)."""
    if r.get("podium_style") == "chengtai":
        return []
    n = _steps(r) + strip
    hw = 1 if r.get("podium_style") not in ("xumizuo", "tiers") else 4
    cx, cz = (r["x0"] + r["x1"]) // 2, (r["z0"] + r["z1"]) // 2
    rect = {"south": (cx - hw, r["z1"] + 1, cx + hw, r["z1"] + n), "north": (cx - hw, r["z0"] - n, cx + hw, r["z0"] - 1),
            "east": (r["x1"] + 1, cz - hw, r["x1"] + n, cz + hw), "west": (r["x0"] - n, cz - hw, r["x0"] - 1, cz + hw)}
    back = {"south": "north", "north": "south", "east": "west", "west": "east"}
    fr = r.get("front", "south")
    sides = [d for d in rect if d not in (fr, back[fr])]
    faces = [fr] + ([back[fr]] if _opens_back(r) else []) + (sides if _doors(r) == "four" else [])
    return [rect[d] for d in faces]


def _madao(r, run=None):
    """Where a 城墙 piece's 马道 (bgen.thick_wall: a flight up along its inner face, cut into it, the low end at its
    -u end) is got onto from the ground, (x0, z0, x1, z1): beside its lowest two blocks of steps and a cell past them,
    WALK cells out from the inner face - kept free to walk like the foot of any flight. None: no 马道 (a 宫墙 3 deep,
    门洞 无, a piece too short for one). run: cells a block (None: the piece's 坡度)."""
    if (r.get("special") or r.get("shape")) != "wall" or int(r.get("tunnels", 0) or 0) == 1:
        return None
    fr = r.get("front", "south")
    ns = fr in ("south", "north")
    hx = ((r["x1"] - r["x0"]) if ns else (r["z1"] - r["z0"])) // 2
    hz = ((r["z1"] - r["z0"]) if ns else (r["x1"] - r["x0"])) // 2
    run = run or step_run(r.get("step_slope"))
    L = max(4, min(16, int(r.get("sh", 0) or 0) or 6)) * run
    if hz < 3 or L // 2 + 1 > hx or L - L // 2 > hx:
        return None
    lo = -(L // 2)                                        # (the lowest step's u)
    a, b, c, d = ROT[fr]
    cx, cz = (r["x0"] + r["x1"]) // 2, (r["z0"] + r["z1"]) // 2
    pts = [(cx + a * u + b * v, cz + c * u + d * v) for u in (lo - 1, lo + 2 * run - 1) for v in (-hz - 1, -hz - WALK)]
    return (min(p[0] for p in pts), min(p[1] for p in pts), max(p[0] for p in pts), max(p[1] for p in pts))


def _madao_hits(w, m, rooms):
    """What stands in the cells m at the foot of wall piece w's 马道: a building's platform or a flight of its steps,
    a wall (other than the pieces of w's own line), a gate, a screen, the river."""
    ns = w.get("front", "south") in ("south", "north")
    out = []
    for b in rooms:
        k = b.get("special") or b.get("shape") or ""
        if b is w or (k == "wall" and b.get("front") == w.get("front")
                      and ((b["z0"], b["z1"]) == (w["z0"], w["z1"]) if ns else (b["x0"], b["x1"]) == (w["x0"], w["x1"]))):
            continue
        if k in ("", "round") and not b.get("stack") and b.get("_on") is None:
            if _hit(m, _foot(b)) or any(_hit(m, f) for f in _walk_rects(b, 0)):
                out.append(b)
        elif k in SOLID_SPECIALS:
            g = 0 if k == "river" else 1
            if _hit(m, (b["x0"] - g, b["z0"] - g, b["x1"] + g, b["z1"] + g)):
                out.append(b)
    return out


def _madao_clear(w, rooms):
    """Whether wall piece w has a 马道 and its foot stays clear at every 坡度."""
    ms = [_madao(w, run) for run in sorted(set(STEP_SLOPES.values()))]
    return all(ms) and not any(_madao_hits(w, m, rooms) for m in ms)


def _doors(r):
    """A room's 出入口: front, front_back or four (compose.doors_open: an old `open` read too)."""
    import compose
    return compose.doors_open(r)[0]


def _opens_back(r):
    """Whether a room has a way out at the back with a flight down behind it (出入口 前后, 四面; not on a 城台)."""
    return _doors(r) in ("front_back", "four") and r.get("podium_style") != "chengtai"


def axis_open(r, cx):
    """The doors a hall gets from where it stands, after the 紫禁城: a hall on the main axis (x middle cx, facing along
    it) opens front and back (前后门: the axis walks through it, as 太和殿 and 乾清宫 do), a square one there on all four
    sides (四面门: 中和殿, 交泰殿); a gate hall keeps its 前后 + 穿堂, a 城台 tower its own way, an open one
    (a pavilion, a stage) its own; off the axis (配殿, the 东西六宫, 廊房) the front only, facing its yard. Returns
    (doors, open), the two rows (compose.DOORS, compose.OPENS)."""
    import compose
    pair = compose.doors_open(r)
    if (r.get("special") or pair != ("front", "closed") or r.get("podium_style") == "chengtai"
            or (r["x0"] + r["x1"]) // 2 != cx or r.get("front") not in ("south", "north")):
        return pair
    return ("four" if r["x1"] - r["x0"] == r["z1"] - r["z0"] or r.get("roof") == "pyramid" else "front_back"), "closed"


def _hit(a, b):
    return a[0] <= b[2] and b[0] <= a[2] and a[1] <= b[3] and b[1] <= a[3]


def _joined(a, b):
    """Rooms built together on purpose: one row cut in pieces (a gallery), or towers on one 城台 - two 城台 of one
    height that meet or overlap are one top (the 午门, its 雁翅楼 and their 阙亭), walked from tower to tower, their
    eaves meeting over it as at the 紫禁城."""
    if (a.get("podium_style") == b.get("podium_style") == "chengtai" and int(a.get("podium", 0) or 0)
            and int(a.get("podium", 0) or 0) == int(b.get("podium", 0) or 0)):
        fa, fb = _foot(a), _foot(b)
        return fa[0] <= fb[2] + 1 and fb[0] <= fa[2] + 1 and fa[1] <= fb[3] + 1 and fb[1] <= fa[3] + 1
    return a.get("front") == b.get("front") and (
        (a["x0"], a["x1"]) == (b["x0"], b["x1"]) and (a["z1"] + 1 == b["z0"] or b["z1"] + 1 == a["z0"])
        or (a["z0"], a["z1"]) == (b["z0"], b["z1"]) and (a["x1"] + 1 == b["x0"] or b["x1"] + 1 == a["x0"]))


def _abut(a, b):
    """Two rooms built against each other: their frames meet wall to wall and the one's side lies within the other's
    (a 穿廊 between two halls - a 工字殿, a porch on a pavilion, a gallery run up to the hall it was cut round, a wing
    on a 楼). compose joins them into one (joinparts: each owns its column), so their eaves and the steps between them
    meet on purpose."""
    def inside(p0, p1, q0, q1):
        return q0 <= p0 and p1 <= q1 or p0 <= q0 and q1 <= p1
    return (inside(a["x0"], a["x1"], b["x0"], b["x1"]) and (a["z1"] + 1 == b["z0"] or b["z1"] + 1 == a["z0"])
            or inside(a["z0"], a["z1"], b["z0"], b["z1"]) and (a["x1"] + 1 == b["x0"] or b["x1"] + 1 == a["x0"]))


def crowded(rooms, strip=WALK):
    """Where a plan is too tight to walk (rooms as a design has them: special or shape for the flat things):
      ("steps", a, b) - a flight of a's steps, or the strip of `strip` cells at its foot, runs into b: its platform or
                        walls, its own steps, a wall, a gate, a screen, the river (a 城墙's 马道 too: _madao);
      ("touch", a, b) - a's and b's eaves or platforms reach over the same cells (a roof on a roof, a wing on a gate).
    a and b are (x0, z0, x1, z1) frames. Rooms built together (a gallery cut in pieces, towers on one 城台: _joined)
    do not touch each other; rooms built against each other (_abut), and so on round (a pavilion's porches), neither
    touch nor step into each other."""
    def kind(r):
        return r.get("special") or r.get("shape") or ""
    blds = [r for r in rooms if kind(r) in ("", "round") and not r.get("stack") and r.get("_on") is None]
    solid = [r for r in rooms if kind(r) in SOLID_SPECIALS]
    grp = list(range(len(blds)))                           # (the rooms built against each other, as groups)

    def top(i):
        while grp[i] != i:
            grp[i] = grp[grp[i]]
            i = grp[i]
        return i
    for i, a in enumerate(blds):
        for j in range(i + 1, len(blds)):
            if _abut(a, blds[j]):
                grp[top(i)] = top(j)
    gid = {id(r): top(i) for i, r in enumerate(blds)}

    def fr(r):
        return (r["x0"], r["z0"], r["x1"], r["z1"])
    flights = {id(r): _walk_rects(r, 0) for r in blds}
    out = []
    for i, a in enumerate(blds):
        for w in _walk_rects(a, strip):
            for b in blds:
                if b is not a and gid[id(a)] != gid[id(b)] and (_hit(w, _foot(b)) or any(_hit(w, f) for f in flights[id(b)])):
                    out.append(("steps", fr(a), fr(b)))
            for b in solid:                                # (a wall's, a screen's base course a cell out)
                g = 0 if kind(b) == "river" else 1
                if _hit(w, (b["x0"] - g, b["z0"] - g, b["x1"] + g, b["z1"] + g)) and not _hit(fr(a), fr(b)):   # (a gate
                    out.append(("steps", fr(a), fr(b)))                                                        # hall's own wall)
        for b in blds[i + 1:]:
            if not _joined(a, b) and gid[id(a)] != gid[id(b)] and _hit(_eave_zone(a), _eave_zone(b)):
                out.append(("touch", fr(a), fr(b)))
    for w in rooms:                                        # a 城墙's 马道: its foot
        m = _madao(w)
        if m:
            out += [("steps", fr(w), fr(b)) for b in _madao_hits(w, m, rooms)]
    return out


def _fit_frame(r):
    """Shrink the room's frame so that with all it reaches out it stays inside the frame it was given (random settings
    pushed platforms and eaves into the next building); where the frame is too small for that, the outward things go.
    The front gives way for the steps first (as many steps as the platform is high: they ran into the next building's
    steps, its eaves, the wall)."""
    for _ in range(3):
        m = _reach(r)
        w, d = r["x1"] - r["x0"] + 1, r["z1"] - r["z0"] + 1
        if w - 2 * m >= 5 and d - 2 * m >= 5:
            break
        if r.get("colonnade") or r.get("veranda") or r.get("back_veranda"):
            r.update(colonnade=False, veranda=False, back_veranda=False)
        elif r.get("podium_style") in ("tiers", "chengtai"):
            r["podium_style"] = "plain"
        elif int(r.get("overhang", 0) or 0) != 1:
            r["overhang"], r["brackets"] = 1, 0
    fr = r.get("front", "south")
    a, b = ("z0", "z1") if fr in ("south", "north") else ("x0", "x1")      # the depth axis; the front is b for south/east
    o0, o1 = ("x0", "x1") if a == "z0" else ("z0", "z1")
    across, deep = r[o1] - r[o0] + 1, r[b] - r[a] + 1
    side = min(_reach(r), (across - 5) // 2)
    back = max(0, min(_reach(r), (deep - 5) // 2))
    front = max(0, min(max(_reach(r), _steps(r)), deep - 5 - back))
    if (deep - back - front) % 2 == 0:                    # (odd: 单体建造 drops a column from an even side)
        if deep - back - front - 1 >= 5:
            front += 1
        else:
            back -= 1
    if side > 0:
        r[o0] += side
        r[o1] -= side
    nb, nf = (back, front) if fr in ("south", "east") else (front, back)
    r[a] += nb
    r[b] -= nf


def _unused():
    d = {"name": ""}
    d["name"] += "·全参数随机"
    return d


def write_all(out_dir, seeds=4):
    """A set of plans for 随机一院: house and palace, one to three courts, a few seeds each."""
    out = Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    for old in out.glob("*.json"):
        old.unlink()
    n = 0
    for kind in ("house", "palace"):
        for jin in (1, 2, 3):
            for s in range(1, seeds + 1):
                d = site(kind, jin, s)
                (out / f"{kind}_{jin}_{s}.json").write_text(json.dumps(d, ensure_ascii=False, indent=1), encoding="utf-8")
                n += 1
    (out / "city_1.json").write_text(json.dumps(city(1), ensure_ascii=False, indent=1), encoding="utf-8")
    return n + 1


if __name__ == "__main__":
    print(write_all(sys.argv[1] if len(sys.argv) > 1 else "out/sites"), "plans")
