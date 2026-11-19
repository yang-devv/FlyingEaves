"""Special sites of a sect (one archetype each, generated from a seed like every bgen kind):
  cloud_port    云舟港: stone quay, several piers out over the edge, docked cloud ships, warehouses, cranes, dock tower
  spirit_mine   灵矿: rock mound with a timbered adit, rails out to a sorting yard, ore heaps, headframe, slag heap,
                smelter hut, watchtower
  beast_hill    灵兽山: fenced paddocks, perches on tall posts, beast caves, troughs, a stable
  trial_ground  试炼场: round arena with tiered stands, a raised central platform, banners, a judges' pavilion
  forbidden     禁地: a sealed cave mouth chained shut, black pillars with runes, rune circle, warning steles
  tomb          祖师陵·碑林: spirit way with paired statues, a stele forest, a burial mound with a hall in front
Importing this module registers the archetypes and kinds in bgen.
"""
import math
import random

import arch
import bgen
import xianxia as X
from arch import AIR, B, Frame, Pal, balustrade, hall, lantern_post, masonry, pavilion, pine, round_tree, slab, stair


def _noise(u, v, seed, cell=7.0):
    """Cheap smooth value noise in [-1, 1]."""
    def r(i, k):
        h = (i * 374761393 + k * 668265263 + seed * 2654435761) & 0xFFFFFFFF
        h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
        return (h & 0xFFFF) / 32767.5 - 1.0
    fu, fv = u / cell, v / cell
    i, k = math.floor(fu), math.floor(fv)
    tu, tv = fu - i, fv - k
    tu, tv = tu * tu * (3 - 2 * tu), tv * tv * (3 - 2 * tv)
    a = r(i, k) * (1 - tu) + r(i + 1, k) * tu
    c = r(i, k + 1) * (1 - tu) + r(i + 1, k + 1) * tu
    return a * (1 - tv) + c * tv


def crag(f, y, cu, cv, R, H, seed, mats, top=None, squash=1.0):
    """A craggy rock mass: lumpy outline, uneven shoulders, terraces, darker stone low down; not a cone."""
    for u in range(-int(R * 1.3), int(R * 1.3) + 1):
        for v in range(-int(R * 1.3), int(R * 1.3) + 1):
            n1 = _noise(u, v, seed, 9.0)
            n2 = _noise(u, v, seed + 7, 3.5)
            d = math.hypot(u, v * squash) / (R * (1 + 0.25 * n1))
            if d > 1:
                continue
            h = H * (1 - d) ** 0.55 * (0.8 + 0.25 * n2)
            h = int(h // 3 * 3 + (2 if n2 > 0.3 else 0))        # rough ledges
            for t in range(1, h + 1):
                m = mats[0] if t < h * 0.4 else mats[(int(t / 4) + (u + v) // 5) % len(mats)]
                f.put(cu + u, y + t, cv + v, m)
            if top is not None and h > 2 and _noise(u, v, seed + 3, 5.0) > -0.2:
                f.put(cu + u, y + h, cv + v, top)


def _floor(f, hx, hz, y, mat=B("stone_bricks"), grid=8):
    for u in range(-hx, hx + 1):
        for v in range(-hz, hz + 1):
            f.put(u, y, v, mat if (u % grid and v % grid) else B("polished_andesite"))


def cloud_port(f, y, rng, spec, tier):
    k = bgen.TIER_K[tier]
    hx, hz = int(45 * (1 + (k - 1) * 0.3)), int(30 * (1 + (k - 1) * 0.3))
    _floor(f, hx, hz, y)
    for u in range(-hx, hx + 1):                 # quay edge: masonry wall face and a balustrade on the open side
        for t in range(1, 7):
            f.put(u, y - t, hz, masonry(u, y - t, hz))
    n = rng.randint(2, 4)
    for i in range(n):
        u = int(-hx + (i + 0.5) * (2 * hx) / n)
        L = rng.randint(45, 80)
        X.pier(f.sub(u, hz, "south"), y, length=L, width=9)
        side = rng.choice((-1, 1))
        X.cloud_ship(f.sub(u + side * 12, hz + L // 2, "south"), y + 3, length=rng.randint(36, 56), beam=rng.randint(9, 13), seed=rng.randint(0, 999))
    bgen.build(f.sub(-hx + 12, -hz + 12), y, "dock_tower", tier if tier in bgen.K["dock_tower"]["tiers"] else "jindan", rng.randint(0, 999))
    for j in range(rng.randint(2, 4)):          # warehouses along the back
        wspec = dict(bgen.K["granary"], decor=())
        bgen.house(f.sub(-hx + 32 + j * 22, -hz + 8), y, random.Random(rng.random()), wspec, "mortal", hx=8, hz=6)
    for i in range(n):                          # cranes: a mast and a boom with a hanging hook
        u = int(-hx + (i + 0.5) * (2 * hx) / n) + 6
        for t in range(1, 14):
            f.put(u, y + t, hz - 2, B("stripped_spruce_log", axis="y"))
        for t in range(0, 9):
            f.put(u, y + 13, hz - 2 + t, B("spruce_planks") if t < 8 else B("spruce_fence"))
        for t in range(1, 6):
            f.put(u, y + 13 - t, hz + 6, B("chain", axis="y"))
        f.put(u, y + 7, hz + 6, B("barrel", facing="up"))
    for u in range(-hx + 6, hx - 5, 10):
        X.formation_pillar(f.sub(u, -hz - 3), y - 1, y, height=10, color="cyan") if u % 30 == 0 else lantern_post(f, u, y, -hz + 2)
    return dict(hx=hx, hz=hz + 80, top=y + 30)


def spirit_mine(f, y, rng, spec, tier):
    k = bgen.TIER_K[tier]
    R = int(rng.randint(22, 30) * (1 + (k - 1) * 0.3))
    H = int(R * 1.2)
    rock = [B("deepslate"), B("stone"), B("andesite"), B("tuff"), B("stone")]
    crag(f, y, 0, -int(R * 0.35), R, H, rng.randint(0, 999), rock, top=B("moss_block"), squash=1.25)
    aw, ah = 3, 6                               # adit: timbered tunnel into the spur
    for v in range(-R, 6):
        for u in range(-aw, aw + 1):
            for t in range(1, ah + 1):
                f.put(u, y + t, v, AIR)
            f.put(u, y, v, B("gravel"))
        if v % 4 == 0:
            for t in range(1, ah + 1):
                f.put(-aw - 1, y + t, v, B("stripped_spruce_log", axis="y"))
                f.put(aw + 1, y + t, v, B("stripped_spruce_log", axis="y"))
            for u in range(-aw - 1, aw + 2):
                f.put(u, y + ah + 1, v, B("stripped_spruce_log", axis="x"))
            f.put(0, y + ah, v, B("light", level=15, waterlogged=False))
        for u in (-1, 1):
            f.put(u, y + 1, v, B("rail", shape="north_south"))
    for u in range(-aw - 2, aw + 3):            # portal frame with a tiled hood and a plaque
        f.put(u, y + ah + 2, 6, B("dark_oak_planks"))
        f.put(u, y + ah + 3, 7, stair(arch.ROOF["tile"], "north"))
    for t in range(1, ah + 2):
        f.put(-aw - 2, y + t, 6, B("stripped_dark_oak_log", axis="y"))
        f.put(aw + 2, y + t, 6, B("stripped_dark_oak_log", axis="y"))
    for v in range(6, 30):                      # rails out to the sorting yard
        for u in (-1, 1):
            f.put(u, y + 1, v, B("rail", shape="north_south"))
    yard = [B("raw_iron_block"), B("raw_copper_block"), B("amethyst_block"), B("raw_gold_block"), B("coal_block")]
    for i in range(6):                          # ore heaps
        cu, cv = rng.randint(-R, R), rng.randint(14, 34)
        mat = yard[rng.randrange(len(yard))]
        for u in range(-3, 4):
            for v in range(-3, 4):
                hgt = 3 - max(abs(u), abs(v))
                for t in range(1, hgt + 1):
                    f.put(cu + u, y + t, cv + v, mat if (u + v + t) % 3 else B("gravel"))
    hu = R + 8                                  # headframe: a lattice tower with a wheel
    for t in range(1, 22):
        for (du, dv) in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
            f.put(hu + du, y + t, 10 + dv, B("stripped_spruce_log", axis="y"))
        if t % 5 == 0:
            for d in range(-2, 3):
                for (a, b) in ((d, -2), (d, 2), (-2, d), (2, d)):
                    f.put(hu + a, y + t, 10 + b, B("spruce_planks"))
    for a in range(0, 360, 20):
        r = math.radians(a)
        f.put(hu, y + 22 + round(3 * math.sin(r)), 10 + round(3 * math.cos(r)), B("dark_oak_planks"))
    for u in range(-R - 14, -R - 2):            # slag heap
        for v in range(8, 22):
            d = math.hypot(u + R + 8, v - 15) / 6
            for t in range(1, int(5 * max(0, 1 - d)) + 1):
                f.put(u, y + t, v, B("tuff") if (u + v) % 2 else B("gravel"))
    bgen.build(f.sub(-R + 2, 36), y, "forge_house", "zhuji", rng.randint(0, 999))
    bgen.build(f.sub(R + 4, 36), y, "outer_dorm", "lianqi", rng.randint(0, 999))
    bgen.build(f.sub(-R - 16, -6), y, "watchtower", "zhuji", rng.randint(0, 999))
    return dict(hx=R + 20, hz=R + 40, top=y + H)


def beast_hill(f, y, rng, spec, tier):
    k = bgen.TIER_K[tier]
    hx, hz = int(40 * (1 + (k - 1) * 0.3)), int(32 * (1 + (k - 1) * 0.3))
    for u in range(-hx, hx + 1):
        for v in range(-hz, hz + 1):
            f.put(u, y, v, B("grass_block") if (u * 3 + v) % 11 else B("coarse_dirt"))
    for i in range(4):                          # paddocks
        cu, cv = (-hx // 2 if i % 2 else hx // 2), (-hz // 2 if i < 2 else hz // 2)
        w, d = rng.randint(10, 16), rng.randint(8, 12)
        for u in range(-w, w + 1):
            for v in (-d, d):
                f.put(cu + u, y + 1, cv + v, B("spruce_fence", east=True, west=True) if abs(u) > 1 or v < 0 else AIR)
        for v in range(-d + 1, d):
            for u in (-w, w):
                f.put(cu + u, y + 1, cv + v, B("spruce_fence", north=True, south=True))
        for u in range(-2, 3):
            f.put(cu + u, y + 1, cv - d + 2, B("composter") if u % 2 else B("water_cauldron", level=3))
        f.put(cu + w - 2, y + 1, cv, B("hay_block"))
        f.put(cu + w - 2, y + 2, cv, B("hay_block"))
    for i in range(5):                          # perches: tall posts with a nest platform
        cu, cv = rng.randint(-hx + 4, hx - 4), rng.randint(-hz + 4, hz - 4)
        hgt = rng.randint(10, 18)
        for t in range(1, hgt):
            f.put(cu, y + t, cv, B("stripped_spruce_log", axis="y"))
        for u in range(-2, 3):
            for v in range(-2, 3):
                if abs(u) + abs(v) <= 3:
                    f.put(cu + u, y + hgt, cv + v, B("hay_block") if abs(u) + abs(v) < 2 else B("spruce_slab", type="bottom"))
    for i in range(3):                          # beast caves in little rock mounds at the back
        cu = -hx + 12 + i * (hx - 6)
        crag(f, y, cu, -hz - 5, 8, 10, rng.randint(0, 999), [B("stone"), B("mossy_cobblestone"), B("andesite")], top=B("grass_block"))
        for u in range(-2, 3):
            for v in range(-hz - 8, -hz + 3):
                for t in range(1, 5):
                    f.put(cu + u, y + t, v, AIR)
        for v in range(-hz - 8, -hz + 3):        # the roof over the cave only where the rock beside holds it up
            if not any(f.get(cu + side, y + 5, v) not in (None, AIR) for side in (-3, 3)):
                for u in range(-2, 3):
                    for t in range(5, 25):
                        f.put(cu + u, y + t, v, AIR)
    bgen.build(f.sub(0, hz + 12), y, "beast_stable", tier if tier in bgen.K["beast_stable"]["tiers"] else "zhuji", rng.randint(0, 999))
    return dict(hx=hx, hz=hz + 25, top=y + 20)


def trial_ground(f, y, rng, spec, tier):
    k = bgen.TIER_K[tier]
    R = int(rng.randint(30, 40) * (1 + (k - 1) * 0.3))
    for u in range(-R - 10, R + 11):
        for v in range(-R - 10, R + 11):
            d = math.hypot(u, v)
            if d <= R:
                f.put(u, y, v, B("polished_andesite") if int(d) % 6 else B("chiseled_stone_bricks"))
            elif d <= R + 10:                    # tiered stands: one step up every 2 blocks out
                t = int((d - R) / 2) + 1
                for s in range(1, t + 1):
                    f.put(u, y + s, v, B("stone_bricks"))
                f.put(u, y + t + 1, v, stair("stone_brick", "north" if abs(v) >= abs(u) and v > 0 else "south" if abs(v) >= abs(u) else "west" if u > 0 else "east") if int(d) % 2 else AIR)
    for a in range(0, 360, 90):                  # four gates through the stands
        r = math.radians(a)
        for t in range(R - 1, R + 12):
            for w in range(-3, 4):
                u, v = round(t * math.cos(r) - w * math.sin(r)), round(t * math.sin(r) + w * math.cos(r))
                for s in range(1, 8):
                    f.put(u, y + s, v, AIR)
                f.put(u, y, v, B("polished_andesite"))
    r0 = R // 3                                  # raised central platform
    for u in range(-r0, r0 + 1):
        for v in range(-r0, r0 + 1):
            if math.hypot(u, v) <= r0:
                for s in (1, 2, 3):
                    f.put(u, y + s, v, Pal.JADE if s == 3 else B("quartz_bricks"))
    for a in range(0, 360, 30):
        r = math.radians(a)
        u, v = round((R + 11) * math.cos(r)), round((R + 11) * math.sin(r))
        for s in range(1, 21):                   # a flag pole from the ground (it stood on air six up, outside the stands)
            f.put(u, y + s, v, B("spruce_fence"))
        f.put(u, y + 21, v, B(rng.choice(["red", "yellow", "blue", "white"]) + "_banner", rotation=0))
    pavilion(f.sub(0, -R - 16), y + 6, size=11, double=True)
    return dict(hx=R + 12, hz=R + 22, top=y + 25)


def forbidden(f, y, rng, spec, tier):
    R = rng.randint(24, 30)
    crag(f, y, 0, -int(R * 0.45), R, int(R * 1.5), rng.randint(0, 999),
         [B("blackstone"), B("basalt", axis="y"), B("blackstone"), B("polished_basalt", axis="y")], squash=1.2)
    for u in range(-4, 5):                       # cave mouth, sealed by chains across it
        for v in range(-R, 4):
            for t in range(1, 9):
                if abs(u) < 4 or t < 7:
                    f.put(u, y + t, v, AIR)
        for t in range(1, 9):
            f.put(u, y + t, 3, B("chain", axis="x") if t % 2 else AIR)
    cr = 14                                      # rune circle in front
    for u in range(-cr, cr + 1):
        for v in range(4, 4 + 2 * cr + 1):
            d = math.hypot(u, v - 4 - cr)
            if d <= cr:
                f.put(u, y, v, B("crying_obsidian") if abs(d - cr + 1) < 0.8 or abs(d - cr / 2) < 0.6 else B("polished_blackstone"))
    for a in range(0, 360, 45):                  # black pillars with red runes round the circle
        r = math.radians(a)
        u, v = round((cr + 3) * math.cos(r)), 4 + cr + round((cr + 3) * math.sin(r))
        for t in range(1, 13):
            f.put(u, y + t, v, B("polished_blackstone_bricks") if t % 4 else B("redstone_lamp", lit=True))
        f.put(u, y + 13, v, B("soul_campfire", lit=True))
    for s in (-1, 1):
        X.stele(f.sub(s * (cr + 9), 4 + 2 * cr + 4), y, height=12, width=5)
    return dict(hx=R, hz=R + 2 * cr, top=y + R)


def tomb(f, y, rng, spec, tier):
    k = bgen.TIER_K[tier]
    L = int(rng.randint(50, 70) * (1 + (k - 1) * 0.2))
    for v in range(0, L):                        # spirit way with paired statues
        for u in range(-3, 4):
            f.put(u, y, v, B("polished_andesite") if abs(u) < 3 else B("stone_bricks"))
        if v % 10 == 5:
            for s in (-1, 1):
                u = s * 7
                f.put(u, y + 1, v, B("stone_bricks"))
                f.put(u, y + 2, v, B("chiseled_stone_bricks"))
                f.put(u, y + 3, v, stair("stone_brick", "west" if s > 0 else "east"))
                f.put(u, y + 4, v, B("stone_brick_wall"))
        if v % 10 == 0 and v:
            for s in (-1, 1):
                pine(f, s * 12, y, v, 11, seed=v * s)
    arch.paifang(f.sub(0, L + 4), y, span=16, h_in=16, h_out=12)
    rows = rng.randint(4, 6)                     # stele forest on both sides of the hall
    for s in (-1, 1):
        for r in range(rows):
            for c in range(4):
                X.stele(f.sub(s * (22 + c * 9), -8 - r * 10), y, height=rng.randint(7, 11), width=3)
    hall(f.sub(0, -12), y, [7, 9, 7], [7, 9, 7], fujie=False, roof="xieshan", tiers=((3, 2), (1, 2)))
    R = 20                                       # burial mound behind
    for u in range(-R, R + 1):
        for v in range(-R, R + 1):
            d = math.hypot(u, v) / R
            if d > 1:
                continue
            h = int(14 * (1 - d * d))
            cv = v - 50
            for t in range(1, h + 1):
                f.put(u, y + t, cv, B("grass_block") if t == h else B("dirt"))
            if abs(d - 1) < 0.06:
                for t in (1, 2):
                    f.put(u, y + t, cv, B("stone_bricks"))
    return dict(hx=60, hz=L + 10, top=y + 20)


for name, fn in (("cloud_port", cloud_port), ("spirit_mine", spirit_mine), ("beast_hill", beast_hill),
                 ("trial_ground", trial_ground), ("forbidden", forbidden), ("tomb", tomb)):
    bgen.ARCHETYPES[name] = fn
bgen.kind("cloud_port", "cloud_port", size=((90, 110), (60, 80)), tiers=("jindan", "yuanying", "huashen"), cn="云舟港")
bgen.kind("spirit_mine", "spirit_mine", size=((70, 90), (90, 110)), tiers=("lianqi", "zhuji", "jindan"), cn="灵矿")
bgen.kind("beast_hill", "beast_hill", size=((80, 100), (80, 100)), tiers=("zhuji", "jindan", "yuanying"), cn="灵兽山")
bgen.kind("trial_ground", "trial_ground", size=((80, 110), (90, 120)), tiers=("zhuji", "jindan", "yuanying"), cn="试炼场")
bgen.kind("forbidden", "forbidden", size=((60, 70), (80, 100)), tiers=("jindan", "yuanying", "huashen"), cn="禁地")
bgen.kind("tomb", "tomb", size=((120, 130), (110, 150)), tiers=("jindan", "yuanying", "huashen"), cn="祖师陵·碑林")
