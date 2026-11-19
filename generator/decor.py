"""Exterior decoration (外饰) for generated buildings. Every function: (f, rng, hx, hz, yb, n, sh, W) with the
building's local frame (front = +v, footprint |u| <= hx, |v| <= hz, ground floor at yb, n storeys of height sh).
Things stand in front of the facade, beside the door, along the side walls or on the roof."""
import math

from arch import AIR, B, ROOF, put_lamp, slab, stair


def _front(hz, k=2):
    return hz + k


def zhaobi(f, rng, hx, hz, yb, n, sh, W):
    """照壁: a screen wall facing the gate, tiled coping, a carved panel in the middle."""
    v = _front(hz, 7)
    w = min(hx + 1, 7)
    for u in range(-w, w + 1):
        for k in range(1, 6):
            f.put(u, yb + k, v, B("stone_bricks") if k == 1 else (B("chiseled_stone_bricks") if abs(u) <= 1 and k == 3 else B("white_terracotta")))
        f.put(u, yb + 6, v, B(ROOF["full"]))
        f.put(u, yb + 6, v - 1, stair(ROOF["tile"], "south"))
        f.put(u, yb + 6, v + 1, stair(ROOF["tile"], "north"))


def drum_stones(f, rng, hx, hz, yb, n, sh, W):
    """抱鼓石 either side of the door."""
    for s in (-2, 2):
        f.put(s, yb + 1, hz + 1, B("chiseled_stone_bricks"))
        f.put(s, yb + 2, hz + 1, stair("stone_brick", "north"))


def mounting_block(f, rng, hx, hz, yb, n, sh, W):
    """上马石 and 拴马桩."""
    f.put(hx - 1, yb + 1, _front(hz, 2), stair("stone_brick", "west"))
    f.put(hx, yb + 1, _front(hz, 2), B("stone_bricks"))
    for k in (1, 2, 3):
        f.put(-hx + 1, yb + k, _front(hz, 2), B("stone_brick_wall") if k < 3 else B("chiseled_stone_bricks"))


def plaque(f, rng, hx, hz, yb, n, sh, W):
    """匾额 over the door: dark board with a gold edge."""
    y = yb + min(sh - 1, 5)
    for u in range(-2, 3):
        f.put(u, y, hz + 1, B("gold_block") if abs(u) == 2 else B("polished_blackstone"))
    f.put(0, y + 1, hz + 1, B("gold_block"))


def couplets(f, rng, hx, hz, yb, n, sh, W):
    """楹联: red boards down both sides of the door, on the wall beside it - two out, or the nearest bit of solid wall
    (two out was a window or an open bay beside a narrow door: the boards hung in front of it, in the way out)."""
    def wall(u):
        return all((b := f.get(u, yb + k, hz)) not in (None, AIR) and not any(t in b for t in ("_door", "pane", "bars", "fence"))
                   for k in range(1, 5))
    for side in (-1, 1):
        s = next((side * a for a in (2, 1, 3) if wall(side * a)), None)
        if s is None:
            continue
        for k in range(2, 5):
            f.put(s, yb + k, hz + 1, B("red_wall_banner", facing="south") if k == 4 else B("red_terracotta"))


def shop_sign(f, rng, hx, hz, yb, n, sh, W):
    """店招 / 幌子: a cloth banner hung from a pole sticking out of the facade."""
    side = rng.choice((-1, 1))
    u = side * (hx - 1)
    y = yb + sh - 1
    for t in range(1, 4):
        f.put(u, y, hz + t, B("spruce_fence", north=True, south=True))
    col = rng.choice(["red", "blue", "white", "yellow", "black", "green"])
    for k in range(1, 4):
        f.put(u, y - k, hz + 3, B(col + "_wool") if k < 3 else B(col + "_carpet"))


def prayer_flags(f, rng, hx, hz, yb, n, sh, W):
    """经幡 / 旗幡: tall poles with long banners at the front corners."""
    col = rng.choice(["red", "yellow", "white", "blue"])
    for s in (-1, 1):
        u = s * (hx + 2)
        for k in range(1, 12):
            f.put(u, yb + k, _front(hz, 2), B("spruce_fence"))
        f.put(u, yb + 12, _front(hz, 2), B("gold_block"))
        for k in range(6, 11):
            f.put(u, yb + k, _front(hz, 3), B(col + "_wool"))


def wind_chimes(f, rng, hx, hz, yb, n, sh, W):
    """风铃 under the eave corners."""
    y = yb + n * sh
    for su in (-1, 1):
        for sv in (-1, 1):
            u, v = su * (hx + 2), sv * (hz + 2)
            f.put(u, y, v, B("chain", axis="y"))
            f.put(u, y - 1, v, B("bell", attachment="ceiling", facing="south"))


def ridge_beasts(f, rng, hx, hz, yb, n, sh, W):
    """脊兽 hint: little upright figures at the ends of the eaves (stairs + a head block)."""
    y = yb + n * sh + 1
    for su in (-1, 1):
        for sv in (-1, 1):
            u, v = su * (hx + 2), sv * (hz + 2)
            for yy in range(y + 12, y - 6, -1):           # sit on the eave tile that is there, not at a guessed height
                st = f.get(u, yy, v)
                if st and st != "minecraft:air":
                    f.put(u, yy + 1, v, B("polished_blackstone_wall"))
                    f.put(u, yy + 2, v, B("polished_blackstone_button", face="floor", facing="south"))
                    break


def stone_lanterns(f, rng, hx, hz, yb, n, sh, W):
    """石灯 along the approach."""
    for s in (-1, 1):
        u = s * 3
        v = _front(hz, 4)
        put_lamp(f, "garden", u, yb, v)


def water_vat(f, rng, hx, hz, yb, n, sh, W):
    """太平缸: big bronze or ceramic water vats beside the facade."""
    for s in (-1, 1):
        u = s * (hx - 1)
        f.put(u, yb + 1, _front(hz, 2), B("water_cauldron", level=3) if rng.random() < 0.5 else B("decorated_pot"))


def millstone(f, rng, hx, hz, yb, n, sh, W):
    """石磨: a round stone on a base, a pole to push it."""
    u, v = -hx - 3, hz - 1
    f.put(u, yb + 1, v, B("smooth_stone"))
    f.put(u, yb + 2, v, B("grindstone", face="floor", facing="east"))
    f.put(u + 1, yb + 2, v, B("spruce_fence", west=True))


def bamboo(f, rng, hx, hz, yb, n, sh, W):
    """竹丛 against a side wall."""
    side = rng.choice((-1, 1))
    for v in range(-hz, hz + 1, 2):
        if rng.random() < 0.6:
            hgt = rng.randint(5, 10)
            for k in range(1, hgt):
                f.put(side * (hx + 2), yb + k, v, B("bamboo", age=1, leaves="large" if k > hgt - 3 else "none", stage=0))


def koi_pond(f, rng, hx, hz, yb, n, sh, W):
    """锦鲤池 beside the building: water over dark prismarine, a little arched slab bridge."""
    side = rng.choice((-1, 1))
    cu = side * (hx + 7)
    water = {(u, v) for u in range(cu - 4, cu + 5) for v in range(-3, 4) if (u - cu) ** 2 / 20 + v ** 2 / 9 <= 1}
    for (u, v) in water:
        f.put(u, yb, v, B("water"))
        f.put(u, yb - 1, v, B("dark_prismarine"))
    for (u, v) in water:                         # a rim on every side of the water, corners included
        for du in (-1, 0, 1):
            for dv in (-1, 0, 1):
                c = (u + du, v + dv)
                if c not in water:
                    f.put(c[0], yb, c[1], B("mossy_cobblestone"))
                    f.put(c[0], yb - 1, c[1], B("mossy_cobblestone"))
    for v in range(-3, 4):
        f.put(cu, yb + (1 if abs(v) < 2 else 0), v, B("spruce_slab", type="bottom" if abs(v) >= 2 else "top"))


def laundry(f, rng, hx, hz, yb, n, sh, W):
    """晾衣架: two posts and a line with cloth."""
    u0 = hx + 2
    for v in (-2, 3):
        for k in range(1, 4):
            f.put(u0, yb + k, v, B("spruce_fence"))
    for v in range(-1, 3):                        # (cloth of stained glass panes: carpets hung in the air drop at an update)
        f.put(u0, yb + 3, v, B("tripwire"))
        f.put(u0, yb + 2, v, B(rng.choice(["white", "light_blue", "red", "blue"]) + "_stained_glass_pane", north=True, south=True))


def farm_tools(f, rng, hx, hz, yb, n, sh, W):
    """农具 leaning on the wall: hay, a composter, a wheelbarrow-ish cart."""
    f.put(-hx - 1, yb + 1, -1, B("hay_block"))
    f.put(-hx - 1, yb + 1, 0, B("composter"))
    f.put(-hx - 1, yb + 1, 1, B("spruce_trapdoor", facing="west", half="bottom", open=True))   # leaning on the wall (east of it)


def cart(f, rng, hx, hz, yb, n, sh, W):
    """车: a two-wheeled cart parked at the side."""
    u, v = hx + 3, -1
    for t in range(3):
        f.put(u, yb + 1, v + t, B("spruce_slab", type="top"))
    f.put(u - 1, yb + 1, v + 1, B("spruce_trapdoor", facing="west", half="bottom", open=True))
    f.put(u + 1, yb + 1, v + 1, B("spruce_trapdoor", facing="east", half="bottom", open=True))
    f.put(u, yb + 1, v + 3, B("spruce_fence"))
    f.put(u, yb + 2, v, B("barrel", facing="up"))


def stall(f, rng, hx, hz, yb, n, sh, W):
    """货摊 in front: a counter under a cloth awning."""
    v = _front(hz, 3)
    for u in range(-2, 3):
        f.put(u, yb + 1, v, B("barrel", facing="up") if u % 2 else B("spruce_planks"))
        f.put(u, yb + 2, v, B(rng.choice(["flower_pot", "decorated_pot", "candle"])) if u % 2 == 0 else AIR)
    col = rng.choice(["red", "white", "yellow", "blue"])
    for u in range(-3, 4):
        for t in (0, 1):
            f.put(u, yb + 4, v - t, B(col + "_carpet") if (u + t) % 2 else B("white_carpet"))
        f.put(u, yb + 4, v + 1, AIR)
    for u in (-3, 3):
        for k in range(1, 4):
            f.put(u, yb + k, v, B("spruce_fence"))


def stele_small(f, rng, hx, hz, yb, n, sh, W):
    """碑: a small stone tablet on a base at the front corner."""
    u, v = hx + 2, _front(hz, 3)
    f.put(u, yb + 1, v, B("polished_andesite"))
    for k in (2, 3, 4):
        f.put(u, yb + k, v, B("polished_blackstone"))
    f.put(u, yb + 5, v, B("polished_blackstone_slab", type="bottom"))


def rune_floor(f, rng, hx, hz, yb, n, sh, W):
    """阵纹地砖: a glowing rune circle set into the ground floor (only where it fits inside the walls)."""
    rad = min(hx, hz) - 2.6
    if rad < 2.4:
        return
    for u in range(-hx + 1, hx):
        for v in range(-hz + 1, hz):
            r = math.hypot(u, v)
            if rad - 1 <= r <= rad:
                f.put(u, yb, v, B("sea_lantern") if (u + v) % 3 == 0 else B("chiseled_polished_blackstone"))
            elif r < 1:
                f.put(u, yb, v, B("glowstone"))


def flying_sword(f, rng, hx, hz, yb, n, sh, W):
    """悬空飞剑: a sword hanging in the air above the forecourt (end rod blade, gold guard)."""
    v = _front(hz, 5)
    y = yb + 6 + rng.randint(0, 4)
    f.put(0, y, v, B("end_rod", facing="up"))
    f.put(0, y + 1, v, B("end_rod", facing="up"))
    f.put(0, y + 2, v, B("gold_block"))
    f.put(0, y + 3, v, B("lightning_rod", facing="up"))


def spirit_lamps(f, rng, hx, hz, yb, n, sh, W):
    """灵石灯: posts topped with glowing crystals."""
    for s in (-1, 1):
        for v in (hz - 2, -hz + 2):
            u = s * (hx + 2)
            f.put(u, yb + 1, v, B("polished_blackstone"))
            f.put(u, yb + 2, v, B("polished_blackstone_wall"))
            f.put(u, yb + 3, v, B("amethyst_block"))
            f.put(u, yb + 4, v, B("amethyst_cluster", facing="up"))


def lions(f, rng, hx, hz, yb, n, sh, W):
    """石狮 / 貔貅: a pair on plinths flanking the approach."""
    mat = rng.choice(["stone_brick", "quartz", "polished_blackstone", "cut_copper"])
    for s in (-1, 1):
        u = s * max(3, hx // 2 + 1)
        v = _front(hz, 3)
        f.put(u, yb + 1, v, B("polished_andesite"))
        f.put(u, yb + 2, v, B(mat + ("_bricks" if mat == "quartz" else "s" if mat in ("stone_brick",) else "")) if mat != "cut_copper" else B("cut_copper"))
        f.put(u, yb + 3, v, stair(mat if mat != "polished_blackstone" else "polished_blackstone", "north" if True else "south"))


def bonsai_row(f, rng, hx, hz, yb, n, sh, W):
    """盆景 on stands along the front."""
    for u in range(-hx, hx + 1, 3):
        if abs(u) < 2:
            continue
        f.put(u, yb + 1, _front(hz, 1), B("polished_andesite"))
        f.put(u, yb + 2, _front(hz, 1), B(rng.choice(["potted_azalea_bush", "potted_flowering_azalea_bush", "potted_spruce_sapling", "potted_bamboo"])))


def nets(f, rng, hx, hz, yb, n, sh, W):
    """渔网 drying on poles."""
    for v in range(-hz, hz + 1, 3):
        for k in (1, 2, 3):
            f.put(hx + 2, yb + k, v, B("spruce_fence") if k < 3 else B("cobweb"))


def drying_racks(f, rng, hx, hz, yb, n, sh, W):
    """晒架: racks with hay / dyed cloth / herbs in front."""
    v = _front(hz, 4)
    mat = rng.choice(["hay_block", "yellow_wool", "blue_wool", "red_wool", "flowering_azalea_leaves"])
    for u in range(-4, 5):
        f.put(u, yb + 1, v, B("spruce_fence") if u % 2 else B(mat, **({"persistent": True} if "leaves" in mat else {})))
        f.put(u, yb + 2, v, B("spruce_slab", type="bottom"))


def waterwheel(f, rng, hx, hz, yb, n, sh, W):
    """水车 against the side wall: a wheel of planks and logs round an axle."""
    u0 = hx + 1
    R = 4
    for a in range(0, 360, 10):
        r = math.radians(a)
        v, k = round(R * math.cos(r)), round(R * math.sin(r))
        f.put(u0, yb + R + k, v, B("spruce_planks"))
    for t in range(-R, R + 1):
        f.put(u0, yb + R, t, B("stripped_spruce_log", axis="z"))
        f.put(u0, yb + R + t, 0, B("stripped_spruce_log", axis="y"))
    f.put(u0 - 1, yb + R, 0, B("stripped_spruce_log", axis="x"))


def watch_corners(f, rng, hx, hz, yb, n, sh, W):
    """Small corner watch platforms with braziers (坞堡角楼 hint)."""
    for su in (-1, 1):
        for sv in (-1, 1):
            u, v = su * hx, sv * hz
            for k in range(1, sh + 4):
                f.put(u, yb + k, v, B("stone_bricks"))
            f.put(u, yb + sh + 4, v, B("campfire", lit=True))


ALL = {
    "zhaobi": zhaobi, "drum_stones": drum_stones, "mounting_block": mounting_block, "plaque": plaque, "couplets": couplets,
    "shop_sign": shop_sign, "prayer_flags": prayer_flags, "wind_chimes": wind_chimes, "ridge_beasts": ridge_beasts,
    "stone_lanterns": stone_lanterns, "water_vat": water_vat, "millstone": millstone, "bamboo": bamboo, "koi_pond": koi_pond,
    "laundry": laundry, "farm_tools": farm_tools, "cart": cart, "stall": stall, "stele_small": stele_small,
    "rune_floor": rune_floor, "flying_sword": flying_sword, "spirit_lamps": spirit_lamps, "lions": lions,
    "bonsai_row": bonsai_row, "nets": nets, "drying_racks": drying_racks, "waterwheel": waterwheel, "watch_corners": watch_corners,
}
# what a building of each tier may get at random on top of its kind's own decor
BY_TIER = {
    "mortal": ["laundry", "farm_tools", "cart", "millstone", "bamboo", "water_vat", "couplets", "drum_stones"],
    "lianqi": ["water_vat", "stone_lanterns", "bonsai_row", "bamboo", "couplets", "plaque", "stele_small"],
    "zhuji": ["stone_lanterns", "bonsai_row", "plaque", "wind_chimes", "koi_pond", "spirit_lamps", "stele_small"],
    "jindan": ["plaque", "wind_chimes", "ridge_beasts", "koi_pond", "spirit_lamps", "rune_floor", "lions", "prayer_flags"],
    "yuanying": ["plaque", "wind_chimes", "ridge_beasts", "spirit_lamps", "rune_floor", "flying_sword", "lions", "zhaobi"],
    "huashen": ["wind_chimes", "ridge_beasts", "spirit_lamps", "rune_floor", "flying_sword", "lions", "zhaobi", "prayer_flags"],
}
