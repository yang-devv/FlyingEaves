"""Furniture vocabulary (small block-built pieces) and room types (what goes into a room of each use).

Every piece is drawn in its own frame: origin at the wall-side cell, +v pointing into the room, u along the wall;
footprint u in [0, w), v in [0, d). Pieces only use blocks; block entities (chests, barrels, banners, beds) are fine
because the chunk writer now creates them.
"""
import random

from arch import AIR, B, Pal, put_lamp, slab, stair

# ------------------------------------------------------------------------------------------------ pieces


def _wood(rng):
    return rng.choice(["spruce", "dark_oak", "oak", "mangrove", "cherry", "jungle"])


def p_bed(f, rng, w=3, d=4):
    """架子床: platform, quilt, pillow, four posts and a canopy."""
    wd = _wood(rng)
    quilt = rng.choice(["red", "white", "light_blue", "yellow", "lime", "pink"])
    for u in range(w):
        for v in range(d):
            f.put(u, 0, v, slab(wd, "top"))
            f.put(u, 1, v, B(quilt + "_carpet") if v > 0 else B("white_carpet"))
    for u, v in ((0, 0), (w - 1, 0), (0, d - 1), (w - 1, d - 1)):
        for k in (1, 2):
            f.put(u, k, v, B(wd + "_fence"))
        f.put(u, 3, v, B(wd + "_planks"))
    for u in range(w):
        for v in range(d):
            if f.get(u, 3, v) is None:
                f.put(u, 3, v, slab(wd, "bottom") if rng.random() < 0.5 else B(quilt + "_wool"))


def p_simple_bed(f, rng, w=1, d=2):
    color = rng.choice(["red", "brown", "white", "gray", "light_gray"])
    f.put(0, 0, 0, B(color + "_bed", part="head", facing="north"))
    f.put(0, 0, 1, B(color + "_bed", part="foot", facing="north"))


def p_kang(f, rng, w=4, d=3):
    """炕: brick platform with a stove mouth, mats on top."""
    for u in range(w):
        for v in range(d):
            f.put(u, 0, v, B("bricks"))
            f.put(u, 1, v, B(rng.choice(["yellow_carpet", "red_carpet", "brown_carpet"])))
    f.put(w - 1, 0, d - 1, B("furnace", facing="south", lit=False))


def p_bunk(f, rng, w=8, d=3):
    """通铺: one long sleeping platform for many disciples, quilts in a row."""
    for u in range(w):
        for v in range(d):
            f.put(u, 0, v, slab("spruce", "top"))
            f.put(u, 1, v, B("gray_carpet" if u % 2 else "light_gray_carpet") if v > 0 else B("white_carpet"))


def p_table(f, rng, w=2, d=1):
    wd = _wood(rng)
    for u in range(w):
        for v in range(d):
            f.put(u, 0, v, slab(wd, "top"))


def p_chair(f, rng, w=1, d=1):
    f.put(0, 0, 0, stair(_wood(rng), "north"))


def p_dining(f, rng, w=4, d=4):
    """Table with chairs on both long sides."""
    wd = _wood(rng)
    for u in range(1, w - 1):
        for v in range(1, d - 1):
            f.put(u, 0, v, slab(wd, "top"))
        f.put(u, 0, 0, stair(wd, "north"))
        f.put(u, 0, d - 1, stair(wd, "south"))
    f.put(1, 1, 1, B("flower_pot"))


def p_desk(f, rng, w=3, d=2):
    """书案: desk with a book stand and a candle, chair behind."""
    wd = _wood(rng)
    for u in range(w):
        f.put(u, 0, 0, slab(wd, "top"))
    f.put(1, 0, 0, B("lectern", facing="south"))
    put_lamp(f, "table", 0, 1, 0, n=2)
    f.put(1, 0, 1, stair(wd, "south"))


def p_bookshelf(f, rng, w=3, d=1):
    for u in range(w):
        for k in range(rng.choice((2, 3))):
            f.put(u, k, 0, B("bookshelf") if (u + k) % 3 else B("chiseled_bookshelf", facing="south"))


def p_scroll_rack(f, rng, w=3, d=1):
    """简架 / 玉简架: chiseled bookshelves with amethyst between."""
    for u in range(w):
        f.put(u, 0, 0, B("chiseled_bookshelf", facing="south"))
        f.put(u, 1, 0, B("chiseled_bookshelf", facing="south"))
        f.put(u, 2, 0, B("amethyst_block") if u % 2 else B("chiseled_bookshelf", facing="south"))


def p_cabinet(f, rng, w=2, d=1):
    for u in range(w):
        f.put(u, 0, 0, B("barrel", facing="south"))
        f.put(u, 1, 0, B(_wood(rng) + "_trapdoor", facing="south", half="bottom", open=True))


def p_medicine_wall(f, rng, w=4, d=1):
    """药柜墙: rows of little drawers (barrels and trapdoors) up the wall."""
    for u in range(w):
        for k in range(3):
            f.put(u, k, 0, B("barrel", facing="south") if (u + k) % 2 else B("spruce_planks"))
        f.put(u, 3, 0, B("flower_pot"))


def p_chest(f, rng, w=1, d=1):
    f.put(0, 0, 0, B("chest", facing="south"))


def p_crates(f, rng, w=2, d=2):
    for u in range(w):
        for v in range(d):
            f.put(u, 0, v, B(rng.choice(["barrel", "chest", "spruce_planks", "barrel"]), **({"facing": "south"} if rng.random() < 2 else {})) if False else B("barrel", facing="up"))
            if rng.random() < 0.5:
                f.put(u, 1, v, B("barrel", facing="up"))


def p_sacks(f, rng, w=2, d=1):
    for u in range(w):
        f.put(u, 0, 0, B(rng.choice(["brown_wool", "white_wool", "hay_block", "composter"])))


def p_vat(f, rng, w=1, d=1):
    f.put(0, 0, 0, B("water_cauldron", level=3))


def p_stove(f, rng, w=3, d=1):
    """灶台: bricks with a smoker, a pot and a cutting board."""
    for u in range(w):
        f.put(u, 0, 0, B("bricks"))
    f.put(0, 0, 0, B("smoker", facing="south", lit=True))
    f.put(1, 1, 0, B("cauldron"))
    f.put(2, 1, 0, B("spruce_pressure_plate"))
    f.put(1, 2, 0, B("bricks"))
    f.put(1, 3, 0, B("bricks"))


def p_small_furnace(f, rng, w=1, d=1):
    """小丹炉: cauldron over a fire."""
    f.put(0, 0, 0, B("campfire", lit=True))
    f.put(0, 1, 0, B("cauldron"))


def p_alchemy_furnace(f, rng, w=3, d=3):
    """丹炉: three-legged bronze furnace on a stone dais with a fire under it."""
    for u in range(w):
        for v in range(d):
            f.put(u, 0, v, B("polished_blackstone"))
    f.put(1, 1, 1, B("magma_block"))
    for u, v in ((0, 0), (2, 0), (1, 2)):
        f.put(u, 1, v, B("exposed_cut_copper"))
    for u in range(w):
        for v in range(d):
            f.put(u, 2, v, B("cut_copper") if (u, v) != (1, 1) else B("blast_furnace", facing="south", lit=True))
            f.put(u, 3, v, B("cut_copper_slab", type="bottom"))
    f.put(1, 4, 1, B("lightning_rod", facing="up"))


def p_anvil(f, rng, w=1, d=1):
    f.put(0, 0, 0, B("anvil", facing="east"))


def p_forge(f, rng, w=3, d=2):
    """锻炉: blast furnace, hearth of magma, quench tub."""
    f.put(0, 0, 0, B("blast_furnace", facing="south", lit=True))
    f.put(1, 0, 0, B("magma_block"))
    f.put(1, 1, 0, B("iron_bars"))
    f.put(2, 0, 0, B("water_cauldron", level=3))
    f.put(1, 0, 1, B("anvil", facing="east"))
    f.put(0, 1, 0, B("bricks"))
    f.put(0, 2, 0, B("bricks"))


def p_workbench(f, rng, w=3, d=1):
    for u, b in enumerate(rng.sample(["crafting_table", "smithing_table", "fletching_table", "stonecutter", "grindstone",
                                      "cartography_table", "loom"], w)):
        props = {"facing": "south"} if b in ("stonecutter", "loom") else ({"face": "floor", "facing": "south"} if b == "grindstone" else {})
        f.put(u, 0, 0, B(b, **props))


def p_loom(f, rng, w=2, d=1):
    f.put(0, 0, 0, B("loom", facing="south"))
    f.put(1, 0, 0, B("white_wool"))


def p_cushion(f, rng, w=1, d=1):
    """蒲团."""
    f.put(0, 0, 0, B(rng.choice(["yellow_carpet", "brown_carpet", "orange_carpet"])))


def p_meditation(f, rng, w=3, d=3):
    """打坐处: round mat, incense burner in front, lamps at the side."""
    f.put(1, 0, 1, B("yellow_carpet"))
    for u, v in ((0, 1), (2, 1), (1, 0), (1, 2)):
        f.put(u, 0, v, B("brown_carpet"))
    put_lamp(f, "table", 1, 0, 2, n=3)
    put_lamp(f, "stand", 0, 0, 0)


def p_incense_table(f, rng, w=3, d=1):
    """香案: long altar table, incense, candles, flowers."""
    wd = _wood(rng)
    for u in range(w):
        f.put(u, 0, 0, slab(wd, "top"))
    put_lamp(f, "table", 0, 1, 0, n=2)
    f.put(1, 1, 0, B("flower_pot"))
    put_lamp(f, "table", w - 1, 1, 0, n=2)


def p_shrine(f, rng, w=3, d=2):
    """神龛 / 牌位: tablets on a stepped altar."""
    for u in range(w):
        f.put(u, 0, 0, B("polished_blackstone"))
        f.put(u, 1, 0, B("polished_blackstone_slab", type="bottom"))
        f.put(u, 0, 1, slab("dark_oak", "top"))
    for u in range(w):
        f.put(u, 2, 0, B("dark_oak_sign", rotation=0) if False else B("dark_oak_fence"))
    put_lamp(f, "table", 1, 1, 1, n=4)


def p_statue(f, rng, w=3, d=3):
    """Seated deity / founder statue on a lotus pedestal (blocks only)."""
    mat = rng.choice(["gold_block", "smooth_quartz", "polished_andesite", "cut_copper"])
    for u in range(w):
        for v in range(d):
            f.put(u, 0, v, B("quartz_bricks"))
    f.put(0, 1, 1, stair("quartz", "east"))
    f.put(2, 1, 1, stair("quartz", "west"))
    f.put(1, 1, 1, B(mat))
    f.put(1, 2, 1, B(mat))
    f.put(1, 3, 1, B(mat))
    f.put(1, 4, 1, B("player_head") if False else B(mat))
    f.put(0, 2, 1, B(mat))
    f.put(2, 2, 1, B(mat))


def p_stele(f, rng, w=3, d=3):
    """碑: a stone tablet on a tortoise (赑屃), its head toward the door, a carved face, a rounded top."""
    for u in range(w):
        for v in range(2):
            f.put(u, 0, v, B("polished_andesite"))
    f.put(1, 0, 2, stair("andesite", "north"))             # the tortoise's head
    for u in range(w):
        for y in (1, 2, 3):
            f.put(u, y, 1, B("chiseled_stone_bricks") if u == 1 and y in (2, 3) else B("smooth_stone"))
    f.put(0, 4, 1, stair("stone_brick", "east"))
    f.put(1, 4, 1, B("chiseled_stone_bricks"))
    f.put(2, 4, 1, stair("stone_brick", "west"))
    f.put(1, 5, 1, slab("stone_brick"))


def p_screen(f, rng, w=3, d=1):
    """屏风."""
    wd = _wood(rng)
    for u in range(w):
        f.put(u, 0, 0, B(wd + "_trapdoor", facing="south", half="bottom", open=True))
        f.put(u, 1, 0, B("white_stained_glass_pane", east=True, west=True) if u % 2 else B(wd + "_trapdoor", facing="south", half="bottom", open=True))


def p_rug(f, rng, w=3, d=3):
    c = rng.choice(["red", "brown", "cyan", "blue", "green", "orange"])
    for u in range(w):
        for v in range(d):
            f.put(u, 0, v, B((c if (u in (0, w - 1) or v in (0, d - 1)) else "yellow") + "_carpet"))


def p_floor_lamp(f, rng, w=1, d=1):
    f.put(0, 0, 0, B("dark_oak_fence"))
    put_lamp(f, "stand", 0, 1, 0)


def p_plant(f, rng, w=1, d=1):
    f.put(0, 0, 0, B(rng.choice(["potted_azalea_bush", "potted_flowering_azalea_bush", "potted_fern", "potted_bamboo",
                                 "potted_red_tulip", "potted_lily_of_the_valley", "potted_spruce_sapling", "potted_cherry_sapling"])))


def p_bonsai(f, rng, w=1, d=1):
    f.put(0, 0, 0, B("polished_andesite"))
    f.put(0, 1, 0, B(rng.choice(["potted_azalea_bush", "potted_flowering_azalea_bush", "potted_spruce_sapling"])))


def p_weapon_rack(f, rng, w=3, d=1):
    for u in range(w):
        f.put(u, 0, 0, B("spruce_fence"))
        f.put(u, 1, 0, B(rng.choice(["lightning_rod", "end_rod", "iron_bars", "chain"]), **({"facing": "up"} if rng.random() < 0.5 else {})) if False else B("lightning_rod", facing="up"))
        f.put(u, 2, 0, B("tripwire_hook", facing="south"))


def p_sword_display(f, rng, w=1, d=1):
    """A sword set upright on a stand (end rod blade, gold guard)."""
    f.put(0, 0, 0, B("polished_blackstone"))
    f.put(0, 1, 0, B("end_rod", facing="up"))
    f.put(0, 2, 0, B("lightning_rod", facing="up"))


def p_counter(f, rng, w=4, d=1):
    """柜台."""
    wd = _wood(rng)
    for u in range(w):
        f.put(u, 0, 0, B(wd + "_planks") if u % 3 else B("barrel", facing="south"))
        f.put(u, 1, 0, slab(wd, "bottom"))
    put_lamp(f, "table", 0, 2, 0, n=1)


def p_shop_shelf(f, rng, w=3, d=1):
    """货架: goods in pots and jars on stacked shelves."""
    wd = _wood(rng)
    goods = ["decorated_pot", "flower_pot", "barrel", "candle", "lantern"]
    for u in range(w):
        for k in range(3):
            f.put(u, k, 0, slab(wd, "top") if k % 2 else B(rng.choice(goods)) if k else B(wd + "_planks"))


def p_throne(f, rng, w=3, d=2):
    """宝座 on a dais."""
    for u in range(w):
        for v in range(d):
            f.put(u, 0, v, B("gold_block") if v == 0 else B("red_carpet"))
    f.put(1, 1, 0, stair("dark_oak", "south"))
    f.put(0, 1, 0, B("gold_block"))
    f.put(2, 1, 0, B("gold_block"))
    f.put(1, 2, 0, B("dark_oak_planks"))
    f.put(1, 3, 0, B("gold_block"))


def p_bathtub(f, rng, w=3, d=2):
    for u in range(w):
        for v in range(d):
            f.put(u, 0, v, B("water") if 0 < u < w - 1 or w < 3 else B("spruce_planks"))
    f.put(0, 0, 0, B("spruce_planks"))
    f.put(w - 1, 0, d - 1, B("spruce_planks"))


def p_cell(f, rng, w=3, d=3):
    """牢房: iron bars front, straw on the floor, chain."""
    for u in range(w):
        for k in range(3):
            f.put(u, k, d - 1, B("iron_bars", east=True, west=True))
    for u in range(w):
        for v in range(d - 1):
            f.put(u, 0, v, B("hay_block") if (u + v) % 3 == 0 else AIR)
    f.put(0, 2, 0, B("chain", axis="y"))


def p_tea_table(f, rng, w=3, d=3):
    f.put(1, 0, 1, B("spruce_slab", type="bottom"))
    f.put(1, 1, 1, B("flower_pot"))
    for u, v in ((0, 1), (2, 1), (1, 0), (1, 2)):
        f.put(u, 0, v, B(rng.choice(["yellow_carpet", "brown_carpet"])))


def p_chess(f, rng, w=3, d=3):
    f.put(1, 0, 1, B("chiseled_quartz_block"))
    f.put(1, 1, 1, B("polished_blackstone_pressure_plate"))
    f.put(0, 0, 1, stair("spruce", "east"))
    f.put(2, 0, 1, stair("spruce", "west"))


def p_brazier(f, rng, w=1, d=1):
    f.put(0, 0, 0, B("soul_campfire" if rng.random() < 0.3 else "campfire", lit=True))


def p_kiln(f, rng, w=2, d=2):
    for u in range(w):
        for v in range(d):
            f.put(u, 0, v, B("bricks"))
            f.put(u, 1, v, B("furnace", facing="south", lit=True) if v == d - 1 else B("bricks"))
            f.put(u, 2, v, B("bricks"))


def p_mill(f, rng, w=2, d=2):
    f.put(0, 0, 0, B("stonecutter", facing="south"))
    f.put(1, 0, 0, B("grindstone", face="floor", facing="south"))
    f.put(0, 0, 1, B("hay_block"))


def p_herb_rack(f, rng, w=3, d=1):
    """晾药架: trapdoor shelves with dried herbs and flowering leaves."""
    for u in range(w):
        f.put(u, 0, 0, B("spruce_fence"))
        f.put(u, 1, 0, B("flowering_azalea_leaves", persistent=True) if u % 2 else B("azalea_leaves", persistent=True))
        f.put(u, 2, 0, B("spruce_trapdoor", facing="south", half="bottom", open=False))


def p_spirit_stones(f, rng, w=2, d=2):
    """灵石堆."""
    for u in range(w):
        for v in range(d):
            f.put(u, 0, v, B(rng.choice(["amethyst_block", "emerald_block", "diamond_block", "lapis_block"])))
            if rng.random() < 0.6:
                f.put(u, 1, v, B("amethyst_cluster", facing="up"))


def p_formation_disk(f, rng, w=3, d=3):
    """阵盘: glowing disk with rune blocks round it."""
    for u in range(w):
        for v in range(d):
            f.put(u, 0, v, B("sea_lantern") if (u, v) == (1, 1) else B("chiseled_polished_blackstone"))
    f.put(1, 1, 1, B("light_blue_stained_glass"))
    for u, v in ((0, 0), (2, 0), (0, 2), (2, 2)):
        f.put(u, 1, v, B("end_rod", facing="up"))


def p_jade_slips(f, rng, w=2, d=1):
    for u in range(w):
        f.put(u, 0, 0, B("chiseled_bookshelf", facing="south"))
        f.put(u, 1, 0, B("green_stained_glass"))


def p_scroll(f, rng, w=1, d=1):
    """挂轴 on the wall."""
    f.put(0, 1, 0, B(rng.choice(["white", "light_gray", "yellow"]) + "_wall_banner", facing="south"))


def p_instrument(f, rng, w=2, d=1):
    f.put(0, 0, 0, B("note_block"))
    f.put(1, 0, 0, B("jukebox"))


def p_beast_pen(f, rng, w=4, d=3):
    for u in range(w):
        f.put(u, 0, d - 1, B("spruce_fence", east=True, west=True))
    for u in range(w):
        for v in range(d - 1):
            f.put(u, 0, v, B("hay_block") if (u + v) % 4 == 0 else AIR)
    f.put(w - 1, 0, 0, B("water_cauldron", level=3))


def p_coffin(f, rng, w=3, d=1):
    for u in range(w):
        f.put(u, 0, 0, B("dark_oak_planks"))
        f.put(u, 1, 0, B("dark_oak_slab", type="bottom"))


def p_seats(f, rng, w=5, d=3):
    """Rows of benches facing the front (auction hall, lecture hall)."""
    for v in range(d):
        for u in range(w):
            f.put(u, 0, v, stair(_wood(rng), "north") if v % 2 == 0 else AIR)


def p_lectern_dais(f, rng, w=3, d=2):
    """讲台."""
    for u in range(w):
        for v in range(d):
            f.put(u, 0, v, B("polished_andesite"))
    f.put(1, 1, 0, B("lectern", facing="south"))


def p_well(f, rng, w=3, d=3):
    for u in range(w):
        for v in range(d):
            f.put(u, 0, v, B("water") if (u, v) == (1, 1) else B("stone_bricks"))
            f.put(u, 1, v, AIR if (u, v) == (1, 1) else B("stone_brick_wall"))
    f.put(1, 2, 1, B("chain", axis="y"))


def p_cradle(f, rng, w=1, d=1):
    f.put(0, 0, 0, B("composter"))
    f.put(0, 1, 0, B("white_carpet"))


def p_washstand(f, rng, w=2, d=1):
    f.put(0, 0, 0, B("water_cauldron", level=3))
    f.put(1, 0, 0, B("spruce_fence"))
    put_lamp(f, "stand", 1, 1, 0)


def p_map_table(f, rng, w=2, d=1):
    f.put(0, 0, 0, B("cartography_table"))
    f.put(1, 0, 0, B("lectern", facing="south"))


def p_gambling(f, rng, w=3, d=3):
    f.put(1, 0, 1, B("green_carpet"))
    f.put(1, 0, 1, slab("spruce", "top"))
    f.put(1, 1, 1, B("green_carpet"))
    for u, v, fc in ((0, 1, "east"), (2, 1, "west"), (1, 0, "south"), (1, 2, "north")):
        f.put(u, 0, v, stair("spruce", fc))


def p_crystal(f, rng, w=1, d=1):
    f.put(0, 0, 0, B("amethyst_block"))
    f.put(0, 1, 0, B("amethyst_cluster", facing="up"))


def p_dummy(f, rng, w=1, d=1):
    """木人桩."""
    f.put(0, 0, 0, B("spruce_log", axis="y"))
    f.put(0, 1, 0, B("spruce_log", axis="y"))
    f.put(0, 2, 0, B("carved_pumpkin", facing="south") if rng.random() < 0.1 else B("spruce_log", axis="y"))


PIECES = {  # name: (fn, w, d)
    "bed": (p_bed, 3, 4), "simple_bed": (p_simple_bed, 1, 2), "kang": (p_kang, 4, 3), "bunk": (p_bunk, 8, 3),
    "table": (p_table, 2, 1), "chair": (p_chair, 1, 1), "dining": (p_dining, 4, 4), "desk": (p_desk, 3, 2),
    "bookshelf": (p_bookshelf, 3, 1), "scroll_rack": (p_scroll_rack, 3, 1), "cabinet": (p_cabinet, 2, 1),
    "medicine_wall": (p_medicine_wall, 4, 1), "chest": (p_chest, 1, 1), "crates": (p_crates, 2, 2), "sacks": (p_sacks, 2, 1),
    "vat": (p_vat, 1, 1), "stove": (p_stove, 3, 1), "small_furnace": (p_small_furnace, 1, 1),
    "alchemy_furnace": (p_alchemy_furnace, 3, 3), "anvil": (p_anvil, 1, 1), "forge": (p_forge, 3, 2),
    "workbench": (p_workbench, 3, 1), "loom": (p_loom, 2, 1), "cushion": (p_cushion, 1, 1), "meditation": (p_meditation, 3, 3),
    "incense_table": (p_incense_table, 3, 1), "shrine": (p_shrine, 3, 2), "statue": (p_statue, 3, 3), "screen": (p_screen, 3, 1),
    "stele": (p_stele, 3, 3),
    "rug": (p_rug, 3, 3), "floor_lamp": (p_floor_lamp, 1, 1), "plant": (p_plant, 1, 1), "bonsai": (p_bonsai, 1, 1),
    "weapon_rack": (p_weapon_rack, 3, 1), "sword_display": (p_sword_display, 1, 1), "counter": (p_counter, 4, 1),
    "shop_shelf": (p_shop_shelf, 3, 1), "throne": (p_throne, 3, 2), "bathtub": (p_bathtub, 3, 2), "cell": (p_cell, 3, 3),
    "tea_table": (p_tea_table, 3, 3), "chess": (p_chess, 3, 3), "brazier": (p_brazier, 1, 1), "kiln": (p_kiln, 2, 2),
    "mill": (p_mill, 2, 2), "herb_rack": (p_herb_rack, 3, 1), "spirit_stones": (p_spirit_stones, 2, 2),
    "formation_disk": (p_formation_disk, 3, 3), "jade_slips": (p_jade_slips, 2, 1), "scroll": (p_scroll, 1, 1),
    "instrument": (p_instrument, 2, 1), "beast_pen": (p_beast_pen, 4, 3), "coffin": (p_coffin, 3, 1), "seats": (p_seats, 5, 3),
    "lectern_dais": (p_lectern_dais, 3, 2), "well": (p_well, 3, 3), "cradle": (p_cradle, 1, 1), "washstand": (p_washstand, 2, 1),
    "map_table": (p_map_table, 2, 1), "gambling": (p_gambling, 3, 3), "crystal": (p_crystal, 1, 1), "dummy": (p_dummy, 1, 1),
}

# ------------------------------------------------------------------------------------------------ room types
# each: list of (piece, where, (min, max)); where = wall | back (the wall opposite the door) | center | corner | rug | any
ROOMS = {
    # ---- mortal homes
    "main_room": [("incense_table", "back", (1, 1)), ("dining", "center", (1, 1)), ("chair", "wall", (2, 4)), ("chest", "wall", (1, 2)),
                  ("plant", "corner", (0, 2)), ("floor_lamp", "corner", (1, 2)), ("scroll", "back", (0, 1))],
    "bedroom": [("bed", "back", (1, 1)), ("cabinet", "wall", (1, 2)), ("washstand", "wall", (0, 1)), ("floor_lamp", "corner", (1, 1)), ("chest", "wall", (0, 1))],
    "bedroom_north": [("kang", "back", (1, 1)), ("cabinet", "wall", (1, 1)), ("chest", "wall", (1, 1))],
    "maiden_room": [("bed", "back", (1, 1)), ("screen", "wall", (1, 1)), ("loom", "wall", (0, 1)), ("plant", "corner", (1, 3)), ("rug", "center", (1, 1))],
    "embroidery": [("loom", "wall", (1, 2)), ("table", "center", (1, 1)), ("chair", "wall", (1, 2)), ("plant", "corner", (0, 2))],
    "study": [("desk", "back", (1, 1)), ("bookshelf", "wall", (1, 3)), ("plant", "corner", (0, 1)), ("scroll", "wall", (0, 2))],
    "bath": [("bathtub", "center", (1, 1)), ("washstand", "wall", (1, 1)), ("screen", "wall", (0, 1))],
    "kitchen": [("stove", "back", (1, 1)), ("vat", "wall", (1, 2)), ("table", "wall", (1, 1)), ("sacks", "wall", (1, 2)), ("cabinet", "wall", (0, 1))],
    "woodshed": [("sacks", "wall", (2, 4)), ("crates", "corner", (1, 2))],
    "granary": [("sacks", "wall", (3, 6)), ("crates", "corner", (1, 3))],
    "stable": [("beast_pen", "wall", (1, 3)), ("vat", "corner", (1, 1))],
    "henhouse": [("beast_pen", "wall", (1, 2)), ("sacks", "corner", (1, 1))],
    "storeroom": [("crates", "wall", (2, 5)), ("chest", "wall", (1, 3)), ("sacks", "wall", (1, 3))],
    "accounts": [("desk", "back", (1, 1)), ("cabinet", "wall", (1, 2)), ("chest", "wall", (1, 2)), ("map_table", "wall", (0, 1))],
    # ---- mortal trades
    "smithy": [("forge", "back", (1, 1)), ("anvil", "center", (1, 1)), ("weapon_rack", "wall", (1, 2)), ("crates", "corner", (0, 1))],
    "carpentry": [("workbench", "wall", (1, 2)), ("crates", "wall", (1, 2)), ("table", "center", (1, 1))],
    "weaving": [("loom", "wall", (2, 3)), ("sacks", "wall", (1, 2))],
    "dyeing": [("vat", "wall", (2, 4)), ("loom", "wall", (1, 1)), ("crates", "corner", (0, 1))],
    "brewery": [("crates", "wall", (2, 4)), ("vat", "wall", (1, 3)), ("stove", "back", (1, 1))],
    "mill_room": [("mill", "center", (1, 1)), ("sacks", "wall", (2, 4))],
    "tofu": [("mill", "wall", (1, 1)), ("vat", "wall", (1, 2)), ("stove", "back", (1, 1))],
    "pottery": [("kiln", "back", (1, 1)), ("workbench", "wall", (1, 1)), ("shop_shelf", "wall", (1, 2))],
    "pharmacy": [("medicine_wall", "back", (1, 2)), ("counter", "center", (1, 1)), ("herb_rack", "wall", (0, 1))],
    "cloth_shop": [("shop_shelf", "back", (1, 2)), ("counter", "center", (1, 1)), ("loom", "wall", (0, 1))],
    "general_store": [("shop_shelf", "wall", (2, 4)), ("counter", "center", (1, 1)), ("crates", "corner", (1, 2))],
    "pawnshop": [("counter", "center", (1, 1)), ("cabinet", "wall", (1, 3)), ("chest", "wall", (1, 3))],
    "bookshop": [("bookshelf", "wall", (2, 5)), ("counter", "center", (1, 1))],
    "clinic": [("medicine_wall", "back", (1, 1)), ("desk", "wall", (1, 1)), ("simple_bed", "wall", (1, 2))],
    "coffin_shop": [("coffin", "wall", (2, 4)), ("counter", "center", (0, 1))],
    "inn_hall": [("dining", "center", (1, 3)), ("counter", "back", (1, 1)), ("crates", "corner", (0, 1)), ("floor_lamp", "corner", (1, 2))],
    "guest_room": [("simple_bed", "back", (1, 2)), ("table", "wall", (1, 1)), ("chair", "wall", (1, 1)), ("washstand", "wall", (0, 1))],
    "private_dining": [("dining", "center", (1, 1)), ("screen", "wall", (1, 1)), ("plant", "corner", (1, 2)), ("instrument", "wall", (0, 1))],
    "teahouse": [("tea_table", "center", (2, 4)), ("counter", "back", (1, 1)), ("plant", "corner", (1, 2))],
    "gambling_den": [("gambling", "center", (1, 3)), ("counter", "back", (1, 1)), ("chest", "wall", (1, 1))],
    "courtroom": [("throne", "back", (1, 1)), ("table", "center", (1, 1)), ("weapon_rack", "wall", (1, 2)), ("scroll", "back", (1, 1))],
    "jail": [("cell", "wall", (1, 3))],
    "classroom": [("lectern_dais", "back", (1, 1)), ("seats", "center", (1, 2)), ("bookshelf", "wall", (0, 1))],
    "ancestral_hall": [("shrine", "back", (1, 1)), ("incense_table", "center", (1, 1)), ("cushion", "any", (2, 4)), ("candle_stand", "corner", (2, 2))],
    "temple_hall": [("statue", "back", (1, 1)), ("incense_table", "center", (1, 1)), ("cushion", "any", (3, 6)), ("brazier", "corner", (1, 2))],
    "backstage": [("cabinet", "wall", (1, 3)), ("screen", "wall", (1, 2)), ("chest", "wall", (1, 2)), ("instrument", "wall", (0, 1))],
    "barracks": [("bunk", "wall", (1, 2)), ("weapon_rack", "wall", (1, 2)), ("chest", "wall", (1, 2))],
    "guardroom": [("table", "center", (1, 1)), ("weapon_rack", "wall", (1, 2)), ("brazier", "corner", (1, 1))],
    # ---- cultivators
    "meditation_room": [("meditation", "center", (1, 1)), ("bonsai", "corner", (1, 2)), ("scroll", "back", (1, 1)), ("crystal", "corner", (0, 2))],
    "seclusion": [("meditation", "center", (1, 1)), ("formation_disk", "back", (1, 1)), ("spirit_stones", "corner", (1, 2))],
    "alchemy_room": [("alchemy_furnace", "center", (1, 1)), ("medicine_wall", "back", (1, 1)), ("herb_rack", "wall", (1, 2)), ("vat", "corner", (0, 1))],
    "herb_store": [("medicine_wall", "wall", (2, 4)), ("herb_rack", "wall", (1, 2)), ("sacks", "corner", (0, 1))],
    "forging_room": [("forge", "back", (1, 2)), ("anvil", "center", (1, 2)), ("sword_display", "wall", (1, 3)), ("spirit_stones", "corner", (0, 1))],
    "talisman_room": [("desk", "back", (1, 2)), ("jade_slips", "wall", (1, 2)), ("scroll", "wall", (1, 2))],
    "formation_room": [("formation_disk", "center", (1, 1)), ("jade_slips", "wall", (1, 2)), ("crystal", "corner", (2, 4))],
    "scripture_store": [("scroll_rack", "wall", (2, 6)), ("desk", "center", (0, 1)), ("floor_lamp", "corner", (1, 2))],
    "sword_gallery": [("sword_display", "wall", (3, 8)), ("weapon_rack", "wall", (1, 2))],
    "treasury": [("chest", "wall", (2, 6)), ("spirit_stones", "corner", (1, 3)), ("cabinet", "wall", (1, 2))],
    "spirit_stone_vault": [("spirit_stones", "any", (2, 5)), ("chest", "wall", (1, 2))],
    "lecture_hall": [("lectern_dais", "back", (1, 1)), ("cushion", "any", (6, 16)), ("scroll", "back", (1, 2))],
    "council_hall": [("throne", "back", (1, 1)), ("chair", "wall", (4, 10)), ("rug", "center", (1, 1)), ("floor_lamp", "corner", (2, 4))],
    "task_hall": [("counter", "back", (1, 2)), ("jade_slips", "wall", (1, 3)), ("scroll", "wall", (2, 4)), ("seats", "center", (0, 1))],
    "discipline_hall": [("throne", "back", (1, 1)), ("weapon_rack", "wall", (1, 2)), ("cell", "wall", (0, 2))],
    "ancestor_tablets": [("shrine", "back", (1, 2)), ("incense_table", "center", (1, 1)), ("cushion", "any", (2, 4)), ("statue", "wall", (0, 1))],
    "altar_room": [("shrine", "back", (1, 1)), ("formation_disk", "center", (1, 1)), ("brazier", "corner", (2, 4))],
    "teleport_room": [("formation_disk", "center", (1, 1)), ("crystal", "corner", (2, 4)), ("guard_post", "wall", (0, 0))],
    "array_control": [("formation_disk", "center", (1, 1)), ("jade_slips", "wall", (1, 2)), ("map_table", "wall", (1, 1))],
    "healing_room": [("simple_bed", "wall", (2, 4)), ("medicine_wall", "back", (1, 1)), ("small_furnace", "corner", (1, 1))],
    "master_study": [("desk", "back", (1, 1)), ("scroll_rack", "wall", (1, 2)), ("tea_table", "center", (1, 1)), ("bonsai", "corner", (1, 2)), ("chess", "any", (0, 1))],
    "elder_quarters": [("bed", "back", (1, 1)), ("meditation", "center", (1, 1)), ("scroll_rack", "wall", (1, 1)), ("plant", "corner", (1, 2))],
    "inner_disciple_room": [("simple_bed", "back", (1, 1)), ("cushion", "center", (1, 1)), ("desk", "wall", (0, 1)), ("chest", "wall", (1, 1))],
    "outer_dorm": [("bunk", "wall", (1, 3)), ("chest", "wall", (2, 6))],
    "refectory": [("dining", "center", (2, 6)), ("stove", "back", (1, 1)), ("vat", "corner", (1, 2))],
    "auction_hall": [("lectern_dais", "back", (1, 1)), ("seats", "center", (1, 4)), ("floor_lamp", "corner", (2, 4))],
    "auction_box": [("tea_table", "center", (1, 1)), ("screen", "wall", (1, 1)), ("chair", "wall", (2, 3))],
    "artifact_shop": [("sword_display", "wall", (2, 4)), ("counter", "center", (1, 1)), ("shop_shelf", "wall", (1, 2))],
    "pill_shop": [("medicine_wall", "back", (1, 1)), ("counter", "center", (1, 1)), ("shop_shelf", "wall", (1, 2))],
    "talisman_shop": [("jade_slips", "wall", (1, 3)), ("counter", "center", (1, 1)), ("scroll", "wall", (1, 3))],
    "material_shop": [("crates", "wall", (2, 4)), ("spirit_stones", "corner", (1, 2)), ("counter", "center", (1, 1))],
    "cultivator_inn_room": [("simple_bed", "back", (1, 1)), ("meditation", "center", (1, 1)), ("formation_disk", "corner", (0, 1))],
    "beast_stable": [("beast_pen", "wall", (2, 4)), ("vat", "corner", (1, 2)), ("sacks", "wall", (1, 2))],
    "greenhouse": [("plant", "any", (6, 14)), ("herb_rack", "wall", (1, 2)), ("vat", "corner", (1, 1))],
    "star_room": [("map_table", "center", (1, 1)), ("crystal", "corner", (2, 4)), ("scroll_rack", "wall", (1, 1))],
    "corpse_room": [("coffin", "wall", (2, 4)), ("cell", "wall", (0, 1)), ("brazier", "corner", (1, 2))],
    "ship_cabin": [("simple_bed", "wall", (1, 2)), ("map_table", "wall", (1, 1)), ("chest", "wall", (1, 2))],
    "lookout": [("tea_table", "center", (1, 1)), ("instrument", "wall", (0, 1)), ("bonsai", "corner", (1, 2))],
    "stele": [("stele", "center", (1, 1))],
    "training_room": [("dummy", "any", (2, 6)), ("weapon_rack", "wall", (1, 2)), ("rug", "center", (0, 1))],
    "throne_hall": [("throne", "back", (1, 1)), ("rug", "center", (1, 1)), ("brazier", "corner", (2, 4)), ("statue", "wall", (0, 2)), ("chair", "wall", (2, 8))],
    "stair_hall": [("plant", "corner", (1, 2)), ("floor_lamp", "corner", (1, 1))],
}
PIECES["guard_post"] = (p_weapon_rack, 3, 1)


# walkway kept clear around wall pieces: (rows in front, columns at each side)
CLEARANCE = {"bed": (1, 1), "simple_bed": (1, 1), "kang": (1, 0), "bunk": (1, 0), "cradle": (1, 0), "desk": (1, 0),
             "stove": (1, 0), "bookshelf": (1, 0), "scroll_rack": (1, 0), "cabinet": (1, 0), "washstand": (1, 0),
             "medicine_wall": (1, 0), "shop_shelf": (1, 0), "counter": (1, 0), "workbench": (1, 0)}


class Keep:
    """A frame that writes nothing into some columns (the stairs and the cells in front of doorways): a piece that
    reaches over them is drawn without those cells rather than standing on the stairs (a big room's layout leaves such
    a piece out whole: clear_of)."""

    def __init__(self, f, cols):
        self.f, self.cols = f, cols

    def put(self, u, y, v, s):
        if self.f.w(u, v) not in self.cols:
            self.f.put(u, y, v, s)

    def get(self, u, y, v):
        return self.f.get(u, y, v)

    def w(self, u, v):
        return self.f.w(u, v)

    def sub(self, du, dv, front="south"):
        return Keep(self.f.sub(du, dv, front), self.cols)

    def clear_of(self, w, d):
        """True when a piece w x d drawn in this frame touches none of the kept columns (a big room's layout places
        pieces without the small room's walkway checks: one that would lose cells to the stairs is left out whole)."""
        return not any(self.f.w(u, v) in self.cols for u in range(w) for v in range(d))


def furnish(f, rng, room_type, u0, v0, u1, v1, y, doors=(), back_side="north", sh=6):
    """Place the pieces of a room type inside the interior rectangle (u0..u1, v0..v1) at floor level y (pieces stand
    on y+1). doors: interior cells to keep free (in front of doorways). back_side: wall opposite the main door."""
    f = Keep(f, {f.w(du, dv) for du, dv in doors})
    spec = ROOMS.get(room_type, ROOMS["storeroom"])
    if (u1 - u0 + 1) * (v1 - v0 + 1) >= 150:
        from furniture_big import furnish_big
        furnish_big(f, rng, room_type, u0, v0, u1, v1, y, doors=doors, sh=sh)
        return 1
    occ = set()
    for (du, dv) in doors:
        for a in (-1, 0, 1):
            for b in (-1, 0, 1):
                occ.add((du + a, dv + b))
    W, D = u1 - u0 + 1, v1 - v0 + 1
    if W < 2 or D < 2:
        return 0
    placed = 0

    def fits(cells):
        return all(u0 <= cu <= u1 and v0 <= cv <= v1 and (cu, cv) not in occ for cu, cv in cells)

    # wall frames: (origin u, origin v, child-front local direction, along-vector)
    walls = {
        "north": lambda t: ((u0 + t, v0), "south", (1, 0), (0, 1)),
        "south": lambda t: ((u1 - t, v1), "north", (-1, 0), (0, -1)),
        "west": lambda t: ((u0, v1 - t), "east", (0, -1), (1, 0)),
        "east": lambda t: ((u1, v0 + t), "west", (0, 1), (-1, 0)),
    }
    lengths = {"north": W, "south": W, "west": D, "east": D}

    def try_wall(side, name, w, d):
        L = lengths[side]
        starts = list(range(0, max(1, L - w + 1)))
        rng.shuffle(starts)
        for t in starts:
            (ou, ov), front, a, b = walls[side](t)
            cells = [(ou + a[0] * i + b[0] * j, ov + a[1] * i + b[1] * j) for i in range(w) for j in range(d)]
            if fits(cells):
                sub = f.sub(ou, ov, front)
                PIECES[name][0](_Lift(sub, y + 1), rng, w, d) if False else PIECES[name][0](_Lift(sub, y + 1), rng)
                occ.update(cells)
                # keep a walkway: nothing else goes right in front of beds, desks, stoves...; beds also keep their sides free
                fr, sd = CLEARANCE.get(name, (0, 0))
                for j in range(d, d + fr):
                    for i in range(-sd, w + sd):
                        occ.add((ou + a[0] * i + b[0] * j, ov + a[1] * i + b[1] * j))
                for j in range(d):
                    for i in [*range(-sd, 0), *range(w, w + sd)]:
                        occ.add((ou + a[0] * i + b[0] * j, ov + a[1] * i + b[1] * j))
                return True
        return False

    area = W * D
    for name, where, (lo, hi) in spec:
        if name not in PIECES:
            continue
        fn, w, d = PIECES[name]
        n = rng.randint(lo, hi)
        # a room of 40-150 cells furnished like a 4x5 one looks empty: more of its wall and corner pieces
        if area >= 40 and where in ("wall", "corner") and hi > 0:
            n += min(2 * hi, (area - 20) // 40)
        for _ in range(n):
            ok = False
            if where in ("back",):
                ok = try_wall(back_side, name, w, d)
            if not ok and where in ("wall", "back"):
                sides = list(walls)
                rng.shuffle(sides)
                for s in sides:
                    if try_wall(s, name, w, d):
                        ok = True
                        break
            if not ok and where in ("center", "any", "corner", "rug"):
                cands = []
                if where == "center":
                    cands = [((u0 + u1 - w + 1) // 2, (v0 + v1 - d + 1) // 2)]
                elif where == "corner":
                    cands = [(u0, v0), (u1 - w + 1, v0), (u0, v1 - d + 1), (u1 - w + 1, v1 - d + 1)]
                    rng.shuffle(cands)
                else:
                    cands = [(rng.randint(u0, max(u0, u1 - w + 1)), rng.randint(v0, max(v0, v1 - d + 1))) for _ in range(12)]
                for cu, cv in cands:
                    cells = [(cu + i, cv + j) for i in range(w) for j in range(d)]
                    if fits(cells):
                        PIECES[name][0](_Lift(f.sub(cu, cv, "south"), y + 1), rng)
                        occ.update(cells)
                        ok = True
                        break
            placed += ok
    # a ceiling lamp in larger rooms
    if W * D >= 30:
        return placed
    return placed


class _Lift:
    """Frame wrapper that adds a fixed y offset (pieces are written with y = 0 at the floor they stand on)."""

    def __init__(self, f, y):
        self.f, self.y = f, y

    def put(self, u, k, v, s):
        if s is None:
            return
        self.f.put(u, self.y + k, v, s)

    def get(self, u, k, v):
        return self.f.get(u, self.y + k, v)

    def sub(self, du, dv, front="south"):
        return _Lift(self.f.sub(du, dv, front), self.y)



# ---- more pieces (2026-11-15): curio shelf, dresser, wardrobe, daybed, lamp table, wine jars, herb basket, censer

def p_curio_shelf(f, rng, w=3, d=1):
    """博古架: a wooden frame of open cells, each holding a pot, a candle, a crystal or a little vase."""
    wd = _wood(rng)
    items = [B("decorated_pot", facing="south"), B("candle", candles=1, lit=True), B("amethyst_cluster", facing="up"),
             B("potted_azalea_bush"), B("potted_fern"), B("flower_pot"), B("potted_bamboo")]
    for u in range(w):
        for k in (0, 1, 2):
            if k in (0, 2):
                f.put(u, k, 0, slab(wd, "top"))
            else:
                f.put(u, k, 0, rng.choice(items))
        f.put(u, 3, 0, rng.choice(items) if rng.random() < 0.5 else None)


def p_dresser(f, rng, w=2, d=1):
    """梳妆台: a low table with a lamp and a little pot, a stool in front."""
    wd = _wood(rng)
    for u in range(w):
        f.put(u, 0, 0, slab(wd, "top"))
    put_lamp(f, "table", 0, 1, 0, n=2)
    f.put(1, 1, 0, rng.choice([B("potted_pink_tulip"), B("potted_white_tulip"), B("flower_pot")]))


def p_wardrobe(f, rng, w=2, d=1):
    """衣柜: two cabinets high with closed doors."""
    wd = _wood(rng)
    for u in range(w):
        for k in (0, 1):
            f.put(u, k, 0, B(wd + "_planks"))
        f.put(u, 2, 0, slab(wd))


def p_daybed(f, rng, w=3, d=2):
    """罗汉床: a wide low couch with a back and a little table on it."""
    wd = _wood(rng)
    cloth = rng.choice(["red", "yellow", "brown", "cyan"])
    for u in range(w):
        f.put(u, 0, 0, stair(wd, "south"))
        f.put(u, 0, 1, slab(wd, "top"))
        f.put(u, 1, 1, B(cloth + "_carpet"))
    f.put(w // 2, 1, 1, B("brown_carpet"))


def p_lamp_table(f, rng, w=1, d=1):
    """灯几: a small stand with a lantern on it."""
    wd = _wood(rng)
    f.put(0, 0, 0, B(wd + "_fence"))
    if rng.random() < 0.6:
        put_lamp(f, "stand", 0, 1, 0)
    else:
        put_lamp(f, "table", 0, 1, 0, n=3)


def p_wine_jars(f, rng, w=2, d=1):
    """酒坛、米缸: a pair of big jars."""
    for u in range(w):
        f.put(u, 0, 0, rng.choice([B("decorated_pot", facing="south"), B("barrel", facing="up"), B("cauldron")]))


def p_herb_basket(f, rng, w=1, d=1):
    """药篓: a basket with herbs drying on top."""
    f.put(0, 0, 0, B("composter", level=rng.randint(3, 7)))
    f.put(0, 1, 0, rng.choice([B("potted_fern"), B("potted_red_mushroom"), B("potted_dandelion")]))


def p_censer(f, rng, w=1, d=1):
    """香炉: a stand with smoke over it."""
    f.put(0, 0, 0, B("polished_blackstone_bricks"))
    if rng.random() < 0.5:
        f.put(0, 1, 0, B("brewing_stand", has_bottle_0=False, has_bottle_1=False, has_bottle_2=False))
    else:
        put_lamp(f, "table", 0, 1, 0, n=4)


PIECES.update({"curio_shelf": (p_curio_shelf, 3, 1), "dresser": (p_dresser, 2, 1), "wardrobe": (p_wardrobe, 2, 1),
               "daybed": (p_daybed, 3, 2), "lamp_table": (p_lamp_table, 1, 1), "wine_jars": (p_wine_jars, 2, 1),
               "herb_basket": (p_herb_basket, 1, 1), "censer": (p_censer, 1, 1)})

# optional extras per room type (0..1 or 0..2 each): rooms of the same type stop looking alike
EXTRAS = {
    "main_room": [("curio_shelf", "wall", (0, 1)), ("censer", "back", (0, 1)), ("daybed", "wall", (0, 1)), ("lamp_table", "corner", (0, 2))],
    "bedroom": [("wardrobe", "wall", (0, 1)), ("dresser", "wall", (0, 1)), ("lamp_table", "corner", (0, 1)), ("screen", "wall", (0, 1))],
    "maiden_room": [("dresser", "wall", (1, 1)), ("wardrobe", "wall", (0, 1)), ("curio_shelf", "wall", (0, 1))],
    "study": [("curio_shelf", "wall", (0, 1)), ("lamp_table", "corner", (0, 1)), ("daybed", "wall", (0, 1)), ("censer", "corner", (0, 1))],
    "kitchen": [("wine_jars", "wall", (0, 2)), ("herb_basket", "corner", (0, 1))],
    "storeroom": [("wine_jars", "wall", (0, 2)), ("wardrobe", "wall", (0, 1))],
    "guest_room": [("wardrobe", "wall", (0, 1)), ("lamp_table", "corner", (0, 1)), ("dresser", "wall", (0, 1))],
    "private_dining": [("curio_shelf", "wall", (0, 1)), ("censer", "corner", (0, 1)), ("lamp_table", "corner", (0, 2))],
    "teahouse": [("curio_shelf", "wall", (0, 1)), ("wine_jars", "wall", (0, 1)), ("lamp_table", "corner", (0, 2))],
    "pharmacy": [("herb_basket", "corner", (1, 2)), ("wine_jars", "wall", (0, 1))],
    "meditation_room": [("censer", "back", (0, 1)), ("lamp_table", "corner", (0, 2))],
    "elder_quarters": [("curio_shelf", "wall", (0, 1)), ("censer", "corner", (0, 1)), ("lamp_table", "corner", (0, 1)), ("daybed", "wall", (0, 1))],
    "inner_disciple_room": [("lamp_table", "corner", (0, 1)), ("wardrobe", "wall", (0, 1)), ("curio_shelf", "wall", (0, 1))],
    "cultivator_inn_room": [("lamp_table", "corner", (0, 1)), ("wardrobe", "wall", (0, 1)), ("censer", "corner", (0, 1))],
    "alchemy_room": [("herb_basket", "corner", (0, 2)), ("wine_jars", "wall", (0, 1))],
    "scripture_store": [("lamp_table", "corner", (0, 2)), ("curio_shelf", "wall", (0, 1))],
    "ancestral_hall": [("censer", "center", (0, 1))],
    "temple_hall": [("censer", "center", (1, 1)), ("lamp_table", "corner", (0, 2))],
}
for _rt, _extra in EXTRAS.items():
    if _rt in ROOMS:
        ROOMS[_rt] = list(ROOMS[_rt]) + _extra

# pieces come from part templates when there are any (data/parts/, run/bgen/parts/; see parts.py); the functions
# above are what the default templates were exported from and stay as the fallback for a piece with no template
import parts as _parts  # noqa: E402

_parts.install(PIECES)
