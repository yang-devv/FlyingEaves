"""Building generator: kind x tier x seed -> a different building every time (exterior + interior + decor).

Like a xianxia take on The Mighty Architect, one level up: instead of the player drawing rooms, a kind (what the
building is for) chooses an archetype (house, tower, stilt house, cave, round house, pavilion, compound, ceremonial
hall, floating pavilion) and ranges for size, storeys, styles, roof forms, room programme and decor; the tier
(mortal ... huashen) scales sizes and richness; the seed picks everything else.

Pipeline for the common 'house' archetype:
  massing (footprint, storeys, podium) -> walls with posts, doors and windows per storey -> floors and stairs ->
  rooms by BSP, each furnished by use (furniture.py) -> skirt roofs and balconies on upper storeys -> roof
  (arch.roof_surface: hip, hip-gable, overhanging gable, flush gable, rolled, pyramid, thatch) -> exterior decor.
Everything is drawn through arch.Frame into a Canvas, so it exports to the chunk writer like the sect.
"""
from __future__ import annotations

import math
import random
import re

import joinparts
import parts as _parts_mod

import arch
from arch import (AIR, B, Canvas, Frame, Pal, ROOF, ROOFS, finial, main_ridge, masonry, raster_roof, ridge_cells, roof_surface,
                  roof_theme, slab, stair, xumizuo, balustrade, lantern_post, pine, round_tree, rockery, put_lamp)
import furniture as FU

TIERS = ["mortal", "lianqi", "zhuji", "jindan", "yuanying", "huashen"]
TIER_K = {"mortal": 1.0, "lianqi": 1.1, "zhuji": 1.3, "jindan": 1.7, "yuanying": 2.5, "huashen": 3.6}

# extra roof materials for humble buildings
ROOFS.setdefault("jade", dict(tile="prismarine_brick", full="prismarine_bricks", ridge="dark_prismarine", cap="dark_prismarine"))

# wall styles: posts, infill, base course, beam, floor, window kind
import materials

# wall styles come from data/materials/walls.json (and the player's run/bgen/materials/); see materials.py
WALLS = materials.walls()

# ------------------------------------------------------------------------------------------------ kinds
# size: (w, d) ranges at mortal scale; storeys; walls; roofs; roof colour; rooms: ground and upper floors; decor; archetype
K = {}


def kind(name, arch_="house", size=((7, 11), (7, 9)), storeys=(1, 1), walls=("wattle",), roofs=("xuanshan",), themes=("grey",),
         ground=("main_room",), upper=("bedroom",), decor=(), shop=False, balcony=False, podium=(0, 1), sh=(5, 6), steep=0.5,
         tiers=("mortal",), horsehead=False, cn=""):
    K[name] = dict(arch=arch_, size=size, storeys=storeys, walls=walls, roofs=roofs, themes=themes, ground=ground, upper=upper,
                   decor=decor, shop=shop, balcony=balcony, podium=podium, sh=sh, steep=steep, tiers=tiers, horsehead=horsehead, cn=cn)


# ---- mortal homes
kind("thatched_hut", size=((5, 8), (5, 7)), walls=("mud", "yellow"), roofs=("xuanshan", "yingshan"), themes=("thatch",), ground=("main_room", "kitchen"), steep=0.75, decor=("woodpile", "vat", "fence_yard", "drying_rack"), cn="茅屋")
kind("mud_house", size=((7, 10), (6, 8)), walls=("mud", "yellow"), roofs=("yingshan", "xuanshan"), themes=("mud", "grey"), ground=("main_room", "bedroom", "kitchen"), decor=("woodpile", "vat", "mill_stone"), cn="土坯房")
kind("stone_cottage", size=((7, 10), (6, 8)), walls=("stone",), roofs=("yingshan",), themes=("slate",), ground=("main_room", "bedroom_north", "kitchen"), decor=("woodpile", "well"), cn="石屋")
kind("log_cabin", size=((6, 9), (6, 8)), walls=("log",), roofs=("xuanshan",), themes=("wood",), ground=("main_room", "bedroom"), steep=0.6, decor=("woodpile", "hunter_rack"), cn="猎户木屋")
kind("stilt_house", "stilt", size=((8, 12), (7, 9)), storeys=(1, 2), walls=("wattle", "log"), roofs=("xuanshan", "xieshan"), themes=("grey", "wood"), ground=("main_room", "bedroom", "kitchen"), upper=("bedroom", "storeroom"), balcony=True, cn="吊脚楼")
kind("cave_dwelling", "cave", size=((7, 9), (9, 13)), walls=("yellow",), ground=("main_room", "bedroom_north"), decor=("mill_stone", "drying_rack"), cn="窑洞")
kind("north_courtyard", "compound", size=((25, 35), (27, 37)), walls=("greybrick",), roofs=("yingshan",), themes=("grey",), ground=("main_room", "bedroom_north"), cn="四合院")
kind("jiangnan_house", size=((9, 13), (8, 11)), storeys=(1, 2), walls=("whitewall",), roofs=("xuanshan", "yingshan"), themes=("grey",), ground=("main_room", "kitchen", "study"), upper=("bedroom", "maiden_room"), horsehead=False, decor=("plants", "vat"), cn="江南宅")
kind("huizhou_house", size=((11, 15), (9, 13)), storeys=(2, 2), walls=("whitewall",), roofs=("yingshan",), themes=("grey",), ground=("main_room", "study", "kitchen"), upper=("bedroom", "maiden_room", "storeroom"), horsehead=True, decor=("plants",), cn="徽派宅")
kind("tulou", "round", size=((31, 45), (31, 45)), storeys=(3, 4), walls=("yellow", "mud"), themes=("grey",), ground=("kitchen", "granary", "storeroom"), upper=("bedroom", "bedroom", "storeroom"), cn="土楼")
kind("fisher_shack", "stilt", size=((5, 8), (5, 7)), walls=("log",), roofs=("xuanshan",), themes=("thatch", "wood"), ground=("main_room",), decor=("nets",), cn="渔家棚屋")
# ---- mortal production
kind("watermill", size=((8, 11), (7, 9)), walls=("log", "stone"), roofs=("xuanshan",), themes=("wood", "grey"), ground=("mill_room",), decor=("waterwheel", "sacks_out"), cn="水车磨坊")
kind("smithy", size=((8, 11), (7, 9)), walls=("stone", "redbrick"), roofs=("yingshan",), themes=("slate", "grey"), ground=("smithy",), shop=True, decor=("chimney", "anvil_out", "signpole"), cn="铁匠铺")
kind("dye_house", size=((9, 13), (8, 10)), walls=("wattle",), roofs=("xuanshan",), themes=("grey",), ground=("dyeing", "storeroom"), decor=("dye_racks", "vat"), cn="染坊")
kind("brewery", size=((9, 13), (8, 11)), walls=("redbrick", "wattle"), roofs=("yingshan",), themes=("grey",), ground=("brewery", "storeroom"), decor=("jars", "signpole"), cn="酿酒坊")
kind("weaving_house", size=((9, 12), (7, 9)), storeys=(1, 2), walls=("wattle",), roofs=("xuanshan",), themes=("grey",), ground=("weaving",), upper=("storeroom",), cn="织坊")
kind("kiln_house", size=((8, 11), (7, 9)), walls=("redbrick",), roofs=("yingshan",), themes=("slate",), ground=("pottery",), decor=("chimney", "jars"), cn="陶窑")
kind("granary", size=((9, 13), (8, 10)), storeys=(1, 2), walls=("greybrick", "stone"), roofs=("xuanshan",), themes=("grey",), ground=("granary",), upper=("granary",), podium=(1, 2), cn="粮仓")
kind("tofu_shop", size=((7, 9), (6, 8)), walls=("wattle",), roofs=("yingshan",), themes=("grey",), ground=("tofu",), shop=True, decor=("signpole",), cn="豆腐坊")
# ---- mortal commerce
kind("pharmacy", size=((8, 11), (8, 10)), storeys=(1, 2), walls=("greybrick", "wattle"), roofs=("yingshan", "xuanshan"), themes=("grey",), ground=("pharmacy", "clinic"), upper=("herb_store",), shop=True, decor=("signpole", "lanterns"), cn="药铺")
kind("inn", size=((11, 15), (10, 13)), storeys=(2, 3), walls=("wattle", "greybrick"), roofs=("xieshan", "xuanshan"), themes=("grey",), ground=("inn_hall", "kitchen"), upper=("guest_room",), shop=True, balcony=True, decor=("signpole", "lanterns", "stable_out"), cn="客栈")
kind("restaurant", size=((11, 15), (10, 13)), storeys=(2, 3), walls=("palace", "wattle"), roofs=("xieshan",), themes=("grey", "teal"), ground=("inn_hall", "kitchen"), upper=("private_dining",), shop=True, balcony=True, decor=("lanterns", "signpole"), cn="酒楼")
kind("teahouse", size=((9, 13), (9, 11)), storeys=(1, 2), walls=("wattle", "palace"), roofs=("xieshan", "juanpeng"), themes=("grey",), ground=("teahouse",), upper=("teahouse",), shop=True, balcony=True, decor=("lanterns", "plants"), cn="茶馆")
kind("pawnshop", size=((8, 10), (8, 10)), walls=("greybrick",), roofs=("yingshan",), themes=("grey",), ground=("pawnshop", "accounts"), shop=True, decor=("signpole",), cn="当铺")
kind("escort_agency", "compound", size=((23, 31), (25, 33)), walls=("greybrick",), roofs=("yingshan",), themes=("grey",), ground=("guardroom", "barracks"), decor=("flags",), cn="镖局")
kind("cloth_shop", size=((8, 11), (8, 10)), storeys=(1, 2), walls=("wattle",), roofs=("xuanshan",), themes=("grey",), ground=("cloth_shop",), upper=("storeroom",), shop=True, decor=("signpole",), cn="布庄")
kind("general_store", size=((8, 11), (7, 9)), walls=("wattle", "mud"), roofs=("xuanshan",), themes=("grey", "thatch"), ground=("general_store",), shop=True, decor=("crates_out", "signpole"), cn="杂货铺")
kind("bookshop", size=((8, 10), (8, 10)), storeys=(1, 2), walls=("whitewall",), roofs=("yingshan",), themes=("grey",), ground=("bookshop",), upper=("study",), shop=True, cn="书肆")
kind("clinic", size=((8, 11), (8, 10)), walls=("wattle",), roofs=("yingshan",), themes=("grey",), ground=("clinic", "pharmacy"), shop=True, decor=("herb_racks",), cn="医馆")
kind("coffin_shop", size=((7, 9), (7, 9)), walls=("darkwood",), roofs=("yingshan",), themes=("black",), ground=("coffin_shop",), shop=True, cn="棺材铺")
kind("gambling_house", size=((10, 13), (9, 11)), storeys=(1, 2), walls=("redbrick",), roofs=("yingshan",), themes=("grey",), ground=("gambling_den",), upper=("private_dining",), shop=True, decor=("lanterns",), cn="赌坊")
# ---- mortal civic and faith
kind("county_office", "compound", size=((31, 41), (35, 45)), walls=("greybrick",), roofs=("xieshan", "yingshan"), themes=("grey",), ground=("courtroom", "accounts", "jail"), decor=("lions", "drum"), cn="衙门")
kind("academy", "compound", size=((29, 39), (33, 43)), walls=("whitewall",), roofs=("xieshan",), themes=("grey",), ground=("classroom", "study", "bookshop"), cn="书院")
kind("ancestral_hall", size=((11, 15), (11, 15)), walls=("greybrick", "palace"), roofs=("xieshan", "yingshan"), themes=("grey",), ground=("ancestral_hall",), podium=(1, 3), decor=("lions", "lanterns", "plaque"), cn="祠堂")
kind("earth_shrine", "pavilion", size=((5, 7), (5, 7)), walls=("greybrick",), roofs=("xieshan", "pyramid"), themes=("grey",), ground=("temple_hall",), cn="土地庙")
kind("city_god_temple", size=((13, 19), (13, 17)), walls=("palace",), roofs=("xieshan",), themes=("grey", "teal"), ground=("temple_hall",), podium=(2, 3), decor=("lions", "incense_burner", "plaque"), cn="城隍庙")
kind("buddhist_temple", "compound", size=((35, 47), (41, 55)), walls=("palace",), roofs=("xieshan", "wudian"), themes=("grey", "gold"), ground=("temple_hall",), decor=("incense_burner", "pagoda_small"), cn="寺院")
kind("taoist_temple", "compound", size=((31, 43), (37, 49)), walls=("darkwood", "palace"), roofs=("xieshan",), themes=("grey", "teal"), ground=("temple_hall", "meditation_room"), decor=("incense_burner",), cn="道观")
kind("opera_stage", "pavilion", size=((11, 15), (9, 11)), walls=("palace",), roofs=("xieshan", "juanpeng"), themes=("grey",), ground=("backstage",), podium=(3, 4), cn="戏台")
kind("post_station", "compound", size=((23, 29), (23, 31)), walls=("mud", "greybrick"), roofs=("yingshan",), themes=("grey",), ground=("inn_hall", "stable", "guest_room"), cn="驿站")
kind("city_gate", "gate", size=((25, 35), (13, 17)), walls=("greybrick",), roofs=("xieshan",), themes=("grey",), ground=("guardroom",), cn="城门楼")
kind("watchtower", "tower", size=((7, 9), (7, 9)), storeys=(3, 4), walls=("stone", "greybrick"), roofs=("pyramid", "xieshan"), themes=("grey", "slate"), ground=("guardroom",), upper=("lookout",), cn="角楼/烽火台")
kind("palace_hall", "hall", size=((33, 45), (19, 25)), walls=("palace",), roofs=("wudian", "xieshan"), themes=("gold",), ground=("throne_hall",), cn="王宫大殿")
kind("prince_mansion", "compound", size=((41, 55), (45, 61)), walls=("palace",), roofs=("xieshan", "yingshan"), themes=("teal", "grey"), ground=("council_hall", "master_study", "bedroom"), decor=("lions",), cn="王府")
kind("paifang", "paifang", size=((13, 19), (3, 3)), cn="牌坊")
kind("pagoda", "tower", size=((9, 13), (9, 13)), storeys=(5, 9), walls=("greybrick", "palace"), roofs=("pyramid",), themes=("grey",), ground=("temple_hall",), upper=("scripture_store",), sh=(5, 6), cn="塔")
kind("bridge_house", "pavilion", size=((5, 7), (13, 19)), walls=("wattle",), roofs=("xuanshan",), themes=("grey",), ground=("lookout",), cn="廊桥")
# ---- cultivators
kind("clan_fort", "compound", size=((41, 55), (41, 55)), walls=("stone", "greybrick"), roofs=("xieshan", "yingshan"), themes=("grey", "teal"), ground=("council_hall", "ancestral_hall", "treasury"), tiers=("lianqi", "zhuji"), decor=("watch_corners",), cn="家族坞堡")
kind("elder_court", "compound", size=((25, 33), (27, 35)), walls=("darkwood", "palace"), roofs=("xieshan",), themes=("teal", "grey"), ground=("elder_quarters", "master_study", "meditation_room"), tiers=("zhuji", "jindan"), cn="族老院/长老院")
kind("spirit_field_hut", size=((6, 9), (6, 8)), walls=("wattle",), roofs=("xuanshan",), themes=("thatch", "grey"), ground=("herb_store", "bedroom"), tiers=("lianqi",), decor=("spirit_field",), cn="灵田小屋")
kind("alchemy_house", size=((9, 13), (9, 12)), storeys=(1, 2), walls=("darkwood", "palace"), roofs=("xieshan",), themes=("teal", "grey"), ground=("alchemy_room", "herb_store"), upper=("meditation_room",), tiers=("lianqi", "zhuji", "jindan"), decor=("chimney", "herb_racks"), cn="丹房")
kind("alchemy_tower", "tower", size=((11, 15), (11, 15)), storeys=(4, 7), walls=("palace", "darkwood"), roofs=("pyramid", "xieshan"), themes=("teal", "red"), ground=("alchemy_room",), upper=("herb_store", "alchemy_room", "meditation_room"), tiers=("zhuji", "jindan", "yuanying"), cn="炼丹塔")
kind("forge_house", size=((10, 14), (9, 12)), walls=("stone", "redbrick"), roofs=("yingshan",), themes=("slate", "black"), ground=("forging_room",), tiers=("lianqi", "zhuji", "jindan"), decor=("chimney", "anvil_out"), cn="器坊")
kind("refiner_tower", "tower", size=((11, 15), (11, 15)), storeys=(3, 6), walls=("stone", "darkwood"), roofs=("pyramid",), themes=("black", "grey"), ground=("forging_room",), upper=("sword_gallery", "spirit_stone_vault", "forging_room"), tiers=("jindan", "yuanying"), decor=("chimney",), cn="炼器楼")
kind("talisman_hall", size=((9, 13), (9, 11)), storeys=(1, 2), walls=("palace", "whitewall"), roofs=("xieshan",), themes=("teal", "grey"), ground=("talisman_room", "talisman_shop"), upper=("scripture_store",), tiers=("lianqi", "zhuji", "jindan"), cn="符堂")
kind("formation_hall", size=((11, 15), (11, 15)), walls=("darkwood",), roofs=("pyramid", "xieshan"), themes=("black", "teal"), ground=("formation_room", "array_control"), tiers=("zhuji", "jindan", "yuanying"), cn="阵堂")
kind("library_pavilion", "tower", size=((15, 21), (11, 15)), storeys=(3, 5), walls=("palace", "darkwood"), roofs=("xieshan",), themes=("teal", "grey", "gold"), ground=("scripture_store",), upper=("scripture_store", "master_study"), tiers=("zhuji", "jindan", "yuanying", "huashen"), cn="藏经阁")
kind("teaching_hall", "hall", size=((21, 29), (15, 19)), walls=("palace",), roofs=("xieshan",), themes=("teal", "grey"), ground=("lecture_hall",), tiers=("zhuji", "jindan", "yuanying"), cn="传功殿")
kind("task_hall", "hall", size=((21, 29), (15, 19)), walls=("darkwood", "palace"), roofs=("xieshan",), themes=("grey", "teal"), ground=("task_hall",), tiers=("lianqi", "zhuji", "jindan", "yuanying"), cn="执事堂")
kind("discipline_hall", size=((13, 17), (11, 15)), walls=("darkwood",), roofs=("yingshan", "xieshan"), themes=("black",), ground=("discipline_hall", "jail"), tiers=("zhuji", "jindan"), decor=("lions",), cn="戒律堂")
kind("seclusion_cave", "cave", size=((9, 13), (11, 17)), walls=("cave",), ground=("seclusion", "meditation_room", "spirit_stone_vault"), tiers=("zhuji", "jindan", "yuanying", "huashen"), cn="闭关洞府")
kind("meditation_court", "compound", size=((19, 25), (21, 27)), walls=("whitewall", "darkwood"), roofs=("juanpeng", "xieshan"), themes=("grey", "teal"), ground=("meditation_room", "inner_disciple_room"), tiers=("lianqi", "zhuji", "jindan"), cn="静室小院")
kind("outer_dorm", size=((13, 25), (9, 13)), storeys=(1, 3), walls=("wattle", "greybrick", "log", "redbrick", "whitewall"), roofs=("yingshan", "xuanshan", "xieshan", "juanpeng"), themes=("grey", "teal", "wood"), ground=("outer_dorm", "outer_dorm", "refectory", "storeroom"), upper=("outer_dorm", "meditation_room"), balcony=True, tiers=("lianqi",), decor=("laundry", "water_vat"), cn="外门弟子舍")
kind("inner_residence", size=((9, 13), (9, 11)), storeys=(1, 2), walls=("palace", "darkwood"), roofs=("xieshan", "juanpeng"), themes=("teal", "grey"), ground=("inner_disciple_room", "meditation_room"), upper=("bedroom", "study"), tiers=("zhuji", "jindan"), decor=("plants", "bonsai_out"), cn="内门弟子居")
kind("beast_stable", size=((13, 19), (9, 13)), walls=("log", "stone"), roofs=("xuanshan",), themes=("wood", "grey"), ground=("beast_stable",), tiers=("lianqi", "zhuji", "jindan"), decor=("fence_yard",), cn="灵兽厩")
kind("market_shop", size=((8, 11), (8, 11)), storeys=(1, 3), walls=("palace", "darkwood", "wattle"), roofs=("xieshan", "yingshan", "juanpeng"), themes=("grey", "teal"), ground=("artifact_shop", "pill_shop", "talisman_shop", "material_shop"), upper=("storeroom", "cultivator_inn_room"), shop=True, balcony=True, tiers=("lianqi", "zhuji", "jindan"), decor=("signpole", "lanterns"), cn="坊市铺面")
kind("auction_house", "tower", size=((19, 27), (19, 27)), storeys=(3, 5), walls=("palace",), roofs=("xieshan",), themes=("gold", "teal"), ground=("auction_hall",), upper=("auction_box",), tiers=("zhuji", "jindan", "yuanying"), cn="拍卖楼")
kind("cultivator_inn", size=((13, 17), (11, 15)), storeys=(3, 4), walls=("palace", "darkwood"), roofs=("xieshan",), themes=("grey", "teal"), ground=("inn_hall", "refectory"), upper=("cultivator_inn_room",), shop=True, balcony=True, tiers=("lianqi", "zhuji", "jindan"), decor=("lanterns", "signpole"), cn="修士客栈")
kind("teleport_hall", "hall", size=((17, 23), (17, 23)), walls=("jade", "palace"), roofs=("pyramid", "xieshan"), themes=("teal", "gold"), ground=("teleport_room",), tiers=("jindan", "yuanying", "huashen"), cn="传送殿")
kind("dock_tower", "tower", size=((11, 15), (11, 15)), storeys=(3, 5), walls=("darkwood", "stone"), roofs=("pyramid",), themes=("grey",), ground=("ship_cabin", "storeroom"), upper=("lookout",), tiers=("zhuji", "jindan", "yuanying"), cn="云舟码头楼")
kind("array_tower", "tower", size=((9, 11), (9, 11)), storeys=(4, 8), walls=("darkwood", "jade"), roofs=("pyramid",), themes=("black", "jade"), ground=("array_control",), upper=("formation_room",), tiers=("jindan", "yuanying", "huashen"), decor=("beacon_top",), cn="阵眼塔")
kind("star_terrace", "star", size=((25, 41), (25, 41)), tiers=("jindan", "yuanying", "huashen"), cn="观星台")
kind("trial_tower", "tower", size=((13, 17), (13, 17)), storeys=(7, 13), walls=("stone", "darkwood"), roofs=("pyramid",), themes=("black", "grey"), ground=("training_room",), upper=("training_room",), tiers=("zhuji", "jindan", "yuanying"), cn="试炼塔")
kind("ancestor_hall", "hall", size=((21, 29), (15, 21)), walls=("palace", "darkwood"), roofs=("xieshan", "wudian"), themes=("teal", "gold"), ground=("ancestor_tablets",), tiers=("jindan", "yuanying", "huashen"), cn="祖师堂")
kind("treasury", size=((11, 15), (11, 15)), walls=("stone", "jade"), roofs=("yingshan", "wudian"), themes=("black", "grey"), ground=("treasury", "spirit_stone_vault"), podium=(2, 3), tiers=("zhuji", "jindan", "yuanying"), decor=("lions",), cn="宝库")
kind("sect_gate", "gate", size=((29, 41), (13, 17)), walls=("palace", "jade"), roofs=("xieshan", "wudian"), themes=("teal", "gold"), ground=("guardroom",), tiers=("zhuji", "jindan", "yuanying", "huashen"), cn="山门")
kind("main_hall", "hall", size=((41, 61), (23, 33)), walls=("palace", "jade"), roofs=("wudian", "xieshan"), themes=("gold", "teal", "jade"), ground=("throne_hall", "council_hall"), tiers=("jindan", "yuanying", "huashen"), cn="宗门主殿")
kind("cliff_hall", "stilt", size=((13, 19), (7, 9)), storeys=(2, 3), walls=("palace", "darkwood"), roofs=("xieshan",), themes=("grey", "teal"), ground=("temple_hall", "meditation_room"), upper=("lookout", "meditation_room"), balcony=True, tiers=("zhuji", "jindan", "yuanying"), cn="悬空崖殿")
kind("floating_pavilion", "floating", size=((11, 17), (11, 17)), storeys=(2, 3), walls=("jade", "palace"), roofs=("xieshan", "pyramid"), themes=("jade", "gold", "teal"), ground=("lookout", "meditation_room"), upper=("lookout",), balcony=True, tiers=("yuanying", "huashen"), cn="浮空阁")
kind("sword_pavilion", "tower", size=((11, 15), (11, 15)), storeys=(3, 6), walls=("darkwood", "palace"), roofs=("xieshan", "pyramid"), themes=("black", "grey", "teal"), ground=("sword_gallery",), upper=("sword_gallery", "training_room", "meditation_room"), tiers=("zhuji", "jindan", "yuanying"), decor=("lanterns",), cn="剑阁")
kind("sect_market_street", "street", size=((61, 101), (25, 31)), tiers=("zhuji", "jindan", "yuanying"), cn="坊市街")


# ------------------------------------------------------------------------------------------------ helpers

def odd(n):
    n = int(round(n))
    return n if n % 2 else n + 1


def pick(rng, seq):
    return seq[rng.randrange(len(seq))]


def set_walls(name):
    w = WALLS[name]
    Pal.COL = w["post"]
    return w


PITCH = {1: 0.35, 2: 0.55, 3: 0.8, 4: 1.0}
BAND = lambda sh: 2              # 重檐 (单体建造): the wall on between the lower and the upper eave - a wall row and a beam
# (流水线: with 3 to 5 the roofs of 太和殿, 保和殿, 乾清宫 came out a tenth of their height too tall for any 层高 and 坡度)          # 单体建造 坡度: 缓, 中, 陡, 很陡 (rise over run)
EAVE_OUT = {1: 1, 2: 2, 3: 3, 4: 4}                  # 出檐: how far the eaves reach past the walls
LIFT = {1: 0, 2: 1, 3: 2, 4: 3}                      # 翘角: rows of corner lift (none, small, middle, big)
BEAST_N = {1: 0, 2: 3, 3: 5, 4: 7, 5: 9}             # 走兽: beasts on each hip (无, 3, 5, 7, 9), a 仙人 in front of them
BRACKET_L = {1: 0, 2: 1, 3: 2, 4: 3}                 # 斗拱: rows of brackets under the eaves (无, 一层, 两层, 三层)
# 彩画 on the wall plates between two posts: (青, 绿) alternate bay by bay from the middle; the ends and the middle of a
# bay by the style - 旋子: 箍头 of the other colour, a 旋花 (glazed) in from each end; 和玺: gold ends and a gold middle;
# 苏式: a white 包袱 over the middle third
CAIHUA = {"xuanzi": dict(body=("blue_concrete", "cyan_concrete"), flower="light_blue_glazed_terracotta"),
          "hexi": dict(body=("blue_concrete", "cyan_concrete"), gold="gold_block"),
          "sushi": dict(body=("blue_concrete", "cyan_concrete"), panel="smooth_quartz")}


def caihua_cell(style, j, m, par):
    """The block of cell j (0 .. m-1) of a wall plate m cells long between two posts, in bay parity par."""
    st = CAIHUA[style]
    body, other = st["body"][par], st["body"][1 - par]
    if style == "xuanzi":
        if j in (0, m - 1):
            return other
        if m >= 5 and j in (1, m - 2):
            return st["flower"]
        return body
    if style == "hexi":
        if j in (0, m - 1) or (m >= 5 and j == m // 2):
            return st["gold"]
        return body
    third = max(1, m // 3)                                # sushi
    lo = (m - third) // 2
    if m >= 3 and lo <= j < lo + third:
        return st["panel"]
    return other if j in (0, m - 1) and m >= 4 else body


def paint_ring(f, style, hx, hz, bay, y):
    """彩画 on one row of a wall ring (|u| = hx or |v| = hz), between posts at the corners and every bay cells."""
    for limit, at in ((hx, lambda a: (a, hz)), (hx, lambda a: (a, -hz)), (hz, lambda a: (-hx, a)), (hz, lambda a: (hx, a))):
        segs, cur = [], []
        for a in range(-limit, limit + 1):
            if abs(a) == limit or a % bay == 0:
                if cur:
                    segs.append(cur)
                cur = []
            else:
                cur.append(a)
        for i, seg in enumerate(segs):
            par = int(abs(i - (len(segs) - 1) / 2)) % 2
            for j, a in enumerate(seg):
                blk = caihua_cell(style, j, len(seg), par)
                if materials.present("minecraft:" + blk):
                    u, v = at(a)
                    f.put(u, y, v, B(blk))


def cap_roof(f, h, rect, rail):
    """叠放: a roof (heights h) cut flat where it meets the walls of the room stacked on it (rect = u0, v0, u1, v1 in the
    roof's frame): the flat tiled over, a rail round its edge (the 平座 round the upper room). Call after rastering
    the capped h; returns the flat's y (the upper room's ground)."""
    u0, v0, u1, v1 = rect
    ring = [(u, v) for u in range(u0 - 1, u1 + 2) for v in range(v0 - 1, v1 + 2)
            if (u in (u0 - 1, u1 + 1) or v in (v0 - 1, v1 + 1)) and (u, v) in h]
    flat = math.floor(min((h[c] for c in ring), default=max(h.values())))
    high = {c for c, val in h.items() if math.floor(val) >= flat}
    todo = [c for c in high if u0 <= c[0] <= u1 and v0 <= c[1] <= v1]
    cells = set(todo)                    # the flat round the upper room only (not a lifted eave corner reaching as high)
    while todo:
        u, v = todo.pop()
        for du, dv in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            c = (u + du, v + dv)
            if c in high and c not in cells:
                cells.add(c)
                todo.append(c)
    for (u, v) in cells:
        f.put(u, flat, v, B(ROOF["full"]))
        edge = any((u + du, v + dv) not in cells for du, dv in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if edge and not (u0 <= u <= u1 and v0 <= v <= v1):
            f.put(u, flat + 1, v, B(rail))
    return flat


def capped(h, rect):
    """The heights h cut at the level where they meet the ring round rect (see cap_roof)."""
    u0, v0, u1, v1 = rect
    ring = [h[(u, v)] for u in range(u0 - 1, u1 + 2) for v in range(v0 - 1, v1 + 2)
            if (u in (u0 - 1, u1 + 1) or v in (v0 - 1, v1 + 1)) and (u, v) in h]
    if not ring:
        return h
    flat = math.floor(min(ring))
    return {c: min(val, flat + 0.99) for c, val in h.items()}


def ride_beasts(f, h, hips, n):
    """走兽: on each hip (cells from the eave corner inwards, on the roof surface h, the ridge block one above it),
    a 仙人 at the corner and n beasts behind him, as many as the hip has room for (its top cell left bare)."""
    beast = ROOF.get("beast") or "polished_blackstone_button"
    for cells in hips:
        cells = [c for c in cells if c in h]
        for i in range(min(n + 1, len(cells) - 1)):
            u, v = cells[i]
            f.put(u, math.floor(h[(u, v)]) + 2, v, B(beast, face="floor", facing="south"))


def roof_for(f, hx, hz, top_y, form, rng, overhang=2, steep=0.5, corner=True, tier_k=1.0, vary=None, pitch=0, eave_out=0,
             lift=0, extra_out=0, beasts=0, cap=None, min_y=None):
    """Main roof over a rectangle |u|<=hx, |v|<=hz whose wall plates are at top_y. Returns the ridge y.
    vary: a random source of its own (单体建造 rerolls a roof by itself): pitch, eave reach, ridge height and how far
    the corners lift change with it. pitch / eave_out / lift (单体建造, 0 = 自动): set those three outright (the reroll
    still changes the rest). extra_out: added to the eave reach whatever it is (a colonnade under it).
    beasts (单体建造 走兽, 0 = 自动): BEAST_N's count on every hip of a hipped roof.
    cap (单体建造 叠放): (rect, rail) - cut flat where it meets the room stacked on it (cap_roof); returns the flat's y.
    min_y: the eaves (rafter ends too) no lower than this - over a low storey they run flat rather than droop down to
    the door heads (floor + 4)."""
    hips = []                                       # (the hips, from the eave corner in, for the beasts)
    ridge_h = 3 if tier_k > 1 else 2
    lift_less = False
    if vary is not None:
        dsteep, dout = vary.choice((-0.1, 0.0, 0.1, 0.2)), vary.choice((-1, 0, 0, 1))   # (the same draws as ever)
        if not pitch:
            steep = max(0.3, steep + dsteep)
        if not eave_out:
            overhang = max(1, overhang + dout)
        ridge_h = vary.choice((2, 3))
        lift_less = vary.random() < 0.3
    if pitch:
        steep = PITCH.get(pitch, steep)
    if eave_out:
        overhang = EAVE_OUT.get(eave_out, overhang)
    overhang += extra_out
    ex, ez = hx + overhang, hz + overhang
    big = max(ex, ez) >= 14
    # corner lift (翼角) even on small hipped roofs: two half-block steps toward the corner, one extra row of push
    layers = (3, 2, 1) if big else (2, 1) if max(ex, ez) >= 6 else (1,)
    if lift_less and not lift:
        layers = layers[1:] or layers
    if lift:
        layers = (3, 2, 1)[3 - LIFT[lift]:] if LIFT.get(lift) else ()
    if not corner or not ROOF.get("corner_lift", True):
        layers = ()
    run = max(2, ez - overhang)
    # small buildings need a steeper pitch than the Song ratio to read as a roof in blocks (and look taller) - unless the
    # 坡度 was set outright (单体建造), which then means what it says
    rise = max(4 if run >= 4 else 3, run * (steep + 0.25 if run < 12 and not pitch else steep) * (1.0 if form != "pyramid" else 1.2))
    if form == "pyramid":
        h = roof_surface(ex, ex if hx == hz else ez, top_y + 1, overhang, run, rise, None, layers, (1, 0))
    elif form == "wudian":
        h = roof_surface(ex, ez, top_y + 1, overhang, run, rise, None, layers, (2, 0) if big else (1, 0))
    elif form == "xieshan":
        h = roof_surface(ex, ez, top_y + 1, overhang, run, rise, max(1, hx - 2), layers, (2, 1) if big else (1, 0))
    elif form == "luding":                    # 盝顶: a 庑殿 cut off flat a little way up, a ridge round the flat
        full = roof_surface(ex, ez, top_y + 1, overhang, run, rise, None, layers, (2, 0) if big else (1, 0))
        flat = top_y + 1 + max(2, round(rise * 0.45))
        h = {k: min(v, flat) for k, v in full.items()}
    elif form == "shizi":                     # 十字脊: two 歇山 crossing at right angles
        gx, gz = max(1, hx - 2), max(1, hz - 2)
        run2 = max(2, ex - overhang)
        rise2 = max(4 if run2 >= 4 else 3, run2 * (steep + 0.25 if run2 < 12 else steep))
        h1 = roof_surface(ex, ez, top_y + 1, overhang, run, rise, gx, layers, (1, 0))
        h2 = {(v, u): val for (u, v), val in roof_surface(ez, ex, top_y + 1, overhang, run2, rise2, gz, layers, (1, 0)).items()}
        h = {k: max(h1.get(k, -1e9), h2.get(k, -1e9)) for k in set(h1) | set(h2)}
    elif form == "yuanzan":                   # 圆攒尖 over a square (or any) plan: a round cone reaching past the corners
        ext = math.hypot(hx, hz) + 0.7 * overhang
        rr = max(4, ext * ((PITCH.get(pitch) or steep) + 0.2))
        h = {}
        for u in range(-int(ext) - 1, int(ext) + 2):
            for v in range(-int(ext) - 1, int(ext) + 2):
                r = math.hypot(u, v)
                if r <= ext:
                    h[(u, v)] = top_y + 1 + rr * (1 - r / ext) ** 1.3
    elif form == "yingshan":
        h = roof_surface(hx + 1, ez, top_y + 1, overhang, run, rise, hx + 1, (), (0, 0))
    else:  # xuanshan / juanpeng
        h = roof_surface(ex, ez, top_y + 1, overhang, run, rise, ex, (), (0, 0))
        if form == "juanpeng":
            top = max(h.values())
            h = {k: (v if v < top - 0.75 else top - 0.5) for k, v in h.items()}
    if cap:
        h = capped(h, cap[0])
    if min_y is not None:
        h = {kk: max(vv, min_y + 1) for kk, vv in h.items()}   # (the hips, ridges and beasts follow the eaves raised)
    raster_roof(f, h, lambda u, v, val: top_y if (abs(u) <= hx and abs(v) <= hz) else max(top_y, math.floor(val) - 2), min_y=min_y)
    if cap:
        return cap_roof(f, h, cap[0], cap[1])
    ry = math.floor(max(h.values()))
    if form in ("xuanshan", "yingshan"):
        main_ridge(f, ex if form == "xuanshan" else hx + 1, ry, ridge_h)
        # gable walls under the roof
        for sgn in (-1, 1):
            u = sgn * hx
            for v in range(-hz, hz + 1):
                t = h.get((u, v))
                if t is None:
                    continue
                for y in range(top_y + 1, math.floor(t)):
                    if f.get(u, y, v) is None:
                        f.put(u, y, v, Pal.WHITE if form == "xuanshan" else WALL_FILL[0])
    elif form == "xieshan":
        gx = max(1, hx - 2)
        ry = math.floor(max(h.get((u, 0), 0) for u in range(-gx, gx + 1)))
        main_ridge(f, gx, ry, ridge_h)
        for sgn in (-1, 1):
            u = sgn * gx
            for v in range(-ez, ez + 1):
                t, s = h.get((u, v)), h.get((u + sgn, v))
                if t is None or s is None:
                    continue
                for y in range(math.floor(s) + 1, math.floor(t)):
                    f.put(u, y, v, Pal.RED)
        # 垂脊 down the gable edges and 戗脊 out to the eave corners, so even a small hip-gable roof reads clearly
        for sgn in (-1, 1):
            ridge_cells(f, h, [(sgn * gx, v) for v in range(-ez + (ex - gx) + 1, ez - (ex - gx))])
            for sv in (-1, 1):
                v0 = ez - (ex - gx)
                ridge_cells(f, h, [(sgn * (gx + i), sv * (v0 + i)) for i in range(0, ex - gx + 1)])
                hips.append([(sgn * (gx + i), sv * (v0 + i)) for i in range(ex - gx, -1, -1)])
        if not beasts and tier_k > 1.05 and max(ex, ez) >= 7:          # little beasts riding the corner ridges
            for sgn in (-1, 1):
                for sv in (-1, 1):
                    cu, cv = sgn * (ex - 1), sv * (ez - 1)
                    val = h.get((cu, cv))
                    if val is not None:
                        f.put(cu, math.floor(val) + 2, cv, B("polished_blackstone_button", face="floor", facing="south"))
    elif form == "wudian":
        half = max(0, ex - ez)
        main_ridge(f, half, ry, ridge_h)
        for sgn in (-1, 1):
            for sv in (-1, 1):
                ridge_cells(f, h, [(sgn * (half + t), sv * t) for t in range(1, ez + 1)])
                hips.append([(sgn * (half + t), sv * t) for t in range(ez, 0, -1)])
    elif form == "yuanzan":
        finial(f, ry, 4 if tier_k < 1.5 else 7)
    elif form == "pyramid":
        for sgn in (-1, 1):
            for sv in (-1, 1):
                ridge_cells(f, h, [(sgn * t, sv * t) for t in range(1, ex + 1)])
                hips.append([(sgn * t, sv * t) for t in range(ex, 0, -1)])
        finial(f, ry, 4 if tier_k < 1.5 else 7)
    elif form == "luding":
        ry = math.floor(flat)
        cells = [k for k, v in full.items() if v >= flat]
        cellset = set(cells)
        for (u, v) in cells:                  # 围脊: a ridge round the edge of the flat
            if any((u + du, v + dv) not in cellset for du, dv in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                f.put(u, ry + 1, v, B(ROOF["ridge"]))
        half = max(0, ex - ez)
        for sgn in (-1, 1):                    # the hips from the flat's corners down to the eave corners
            for sv in (-1, 1):
                ridge_cells(f, h, [(sgn * (half + t), sv * t) for t in range(1, ez + 1)
                                   if full.get((sgn * (half + t), sv * t), 0) < flat])
                hips.append([(sgn * (half + t), sv * t) for t in range(ez, 0, -1) if full.get((sgn * (half + t), sv * t), 0) < flat])
    elif form == "shizi":
        ry = math.floor(max(h.values()))
        ry1 = math.floor(max(h.get((u, 0), 0) for u in range(-gx, gx + 1)))
        ry2 = math.floor(max(h.get((0, v), 0) for v in range(-gz, gz + 1)))
        main_ridge(f, gx, ry1, ridge_h)                       # along u
        main_ridge(f.sub(0, 0, "east"), gz, ry2, ridge_h)     # and across it
        for sgn in (-1, 1):                                    # the four 山花, red, under each gable
            u = sgn * gx
            for v in range(-ez, ez + 1):
                t, s = h.get((u, v)), h.get((u + sgn, v))
                if t is not None and s is not None:
                    for y in range(math.floor(s) + 1, math.floor(t)):
                        f.put(u, y, v, Pal.RED)
            v = sgn * gz
            for u in range(-ex, ex + 1):
                t, s = h.get((u, v)), h.get((u, v + sgn))
                if t is not None and s is not None:
                    for y in range(math.floor(s) + 1, math.floor(t)):
                        f.put(u, y, v, Pal.RED)
        for sgn in (-1, 1):                                    # 戗脊 out to the eave corners
            for sv in (-1, 1):
                ridge_cells(f, h, [(sgn * (ex - i), sv * (ez - i)) for i in range(0, min(ex, ez) - max(gx, gz) + 1)])
                hips.append([(sgn * (ex - i), sv * (ez - i)) for i in range(0, min(ex, ez) - max(gx, gz) + 1)])
        finial(f, ry + 1, 4 if tier_k < 1.5 else 7)           # a gilt knob where the ridges cross
    if BEAST_N.get(beasts):                       # 走兽 the player asked for, on every hip
        ride_beasts(f, h, hips, BEAST_N[beasts])
    return ry


WALL_FILL = [B("white_terracotta")]
DOOR_OF = {"oak_trapdoor": "oak_door", "spruce_trapdoor": "spruce_door", "dark_oak_trapdoor": "dark_oak_door",
           "birch_trapdoor": "birch_door", "iron_bars": "spruce_door"}


# ------------------------------------------------------------------------------------------------ rooms

def stair_plan(hx, hz, sh):
    """The way up from one storey to the next, along the west wall from the back (local coords).

    Returns dict(steps=[(u, v, rise, facing)], landing=[(u, v, rise)], ladder=bool, reserve=set of cells to keep
    free on this storey, well=set of cells left open in the floor above, above=set to keep free on the storey above).
    rise is the block's height above this storey's floor (1..sh). Straight flight with a free cell to step on when the
    room is deep enough; otherwise straight from the back wall (step on from the side); otherwise a U-turn with a
    landing; a ladder in rooms too small for any of these."""
    u0 = -hx + 1
    steps, landing, ladder = [], [], False
    if 2 * hz - 3 >= sh:                                   # straight, a free cell behind the first step
        steps = [(u0, -hz + 2 + i, i + 1, "south") for i in range(sh)]
        approach = [(u0, -hz + 1)]
    elif 2 * hz - 2 >= sh:                                 # straight from the back wall
        steps = [(u0, -hz + 1 + i, i + 1, "south") for i in range(sh)]
        approach = [(u0 + 1, -hz + 1)]
    else:
        l1 = 2 * hz - 2
        rest = sh - l1
        if hx >= 3 and l1 >= 2 and rest <= l1 - 1:       # U-turn: toward the front, a landing, back toward the back wall
            steps = [(u0, -hz + 1 + i, i + 1, "south") for i in range(l1)]
            landing = [(u0 + 1, -hz + l1, l1)]
            steps += [(u0 + 1, -hz + l1 - 1 - j, l1 + 1 + j, "north") for j in range(rest)]
            approach = [(u0 + 1, -hz + 1)] if rest < l1 - 1 else [(u0 + 2, -hz + 1)]
        else:                                              # too small: a ladder on the west wall
            ladder = True
            steps = [(u0, -hz + 1, r, "east") for r in range(1, sh + 1)]
            approach = [(u0 + 1, -hz + 1)]
    cells = {(su, sv) for su, sv, _, _ in steps} | {(lu, lv) for lu, lv, _ in landing}
    well = {(su, sv) for su, sv, rise, _ in steps if rise + 3 >= sh}
    well |= {(lu, lv) for lu, lv, rise in landing if rise + 3 >= sh}
    top = max(steps, key=lambda t: t[2])
    above = set(well) | {(top[0] + du, top[1] + dv) for du, dv in ((1, 0), (-1, 0), (0, 1), (0, -1))}
    reserve = cells | set(approach) | {(su + 1, sv) for su, sv in cells}
    # kept free of furniture and lamps only (not of partitions or windows): room to walk in at the foot of the stairs
    # (the cells round the one you step on from) and two cells to walk off at the head, on the storey above (a big
    # room's 通铺 two cells past the top step was a bed the stairs ran into)
    n4 = ((1, 0), (-1, 0), (0, 1), (0, -1))
    clear = {(au + du, av + dv) for au, av in approach for du, dv in n4} - cells
    clear_above = set()
    if not ladder:                         # (and straight on to the first step: a storey set in over this one (收分)
        ahead = {"south": (0, 1), "north": (0, -1), "east": (1, 0), "west": (-1, 0)}   # leaves floor behind it)
        du, dv = ahead[steps[0][3]]
        clear |= {(steps[0][0] - du * k, steps[0][1] - dv * k) for k in (1, 2)}
        du, dv = ahead[top[3]]
        clear_above = {(top[0] + du * k, top[1] + dv * k) for k in (1, 2)}
    return dict(steps=steps, landing=landing, ladder=ladder, reserve=reserve, well=well, above=above, approach=approach,
                clear=clear, clear_above=clear_above)


def bays(rect):
    """A house plan (开间): the middle bay, straight in from the front door, is one room the full depth; the side bays
    are split front and back when the house is deep. Same return shape as bsp. Narrow houses get front and back rooms."""
    u0, v0, u1, v1 = rect
    W, D = u1 - u0 + 1, v1 - v0 + 1
    mid = (v0 + v1) // 2
    if W >= 11:
        cw = max(5, (W // 3) | 1)
        c0, c1 = -(cw // 2), cw // 2
        if c0 - 1 - u0 < 3 or u1 - (c1 + 1) < 3:
            return [rect], []
        rooms, walls = [(c0, v0, c1, v1)], []
        for side, (a, b) in ((-1, (u0, c0 - 2)), (1, (c1 + 2, u1))):
            wu = c0 - 1 if side < 0 else c1 + 1
            if D >= 9:
                rooms += [(a, mid + 1, b, v1), (a, v0, b, mid - 1)]
                walls += [("v", mid, a, b, u0 - 99),
                          ("u", wu, mid + 1, v1, (mid + 1 + v1) // 2), ("u", wu, v0, mid - 1, (v0 + mid - 1) // 2),
                          ("u", wu, mid, mid, u0 - 99)]
            else:
                rooms.append((a, v0, b, v1))
                walls.append(("u", wu, v0, v1, (v0 + v1) // 2))
        return rooms, walls
    if D >= 9:
        return [(u0, mid + 1, u1, v1), (u0, v0, u1, mid - 1)], [("v", mid, u0, u1, 0)]
    return [rect], []


def bsp(rect, max_side, rng, depth=0):
    """Split an interior rectangle (u0, v0, u1, v1) into rooms; returns (rooms, walls) where walls are
    (('u'|'v'), coord, a0, a1, door) partition lines with one doorway each."""
    u0, v0, u1, v1 = rect
    W, D = u1 - u0 + 1, v1 - v0 + 1
    if (W <= max_side and D <= max_side) or depth > 5 or (W < 7 and D < 7) or (W >= D and W < 7) or (D > W and D < 7):
        return [rect], []
    if W >= D:
        cut = rng.randint(u0 + 3, u1 - 3)
        if abs(cut) <= 2:                                  # never wall off the front door (it is at u = 0)
            for alt in (3, -3, 4, -4):
                if u0 + 3 <= alt <= u1 - 3:
                    cut = alt
                    break
        door = rng.randint(v0 + 1, v1 - 1)
        a, wa = bsp((u0, v0, cut - 1, v1), max_side, rng, depth + 1)
        b, wb = bsp((cut + 1, v0, u1, v1), max_side, rng, depth + 1)
        return a + b, wa + wb + [("u", cut, v0, v1, door)]
    cut = rng.randint(v0 + 3, v1 - 3)
    door = rng.randint(u0 + 1, u1 - 1)
    a, wa = bsp((u0, v0, u1, cut - 1), max_side, rng, depth + 1)
    b, wb = bsp((u0, cut + 1, u1, v1), max_side, rng, depth + 1)
    return a + b, wa + wb + [("v", cut, u0, u1, door)]


EMPTY_ROOM = "empty"        # 空房: a use with nothing in it (no furniture, no lamps; walls, partitions, stairs as ever)


class _Scratch(Canvas):
    """内饰：无 - the furniture and lamps drawn on a canvas of their own that is thrown away: what they read sees their
    own writes over the building's, so they draw the same random numbers as when they are kept and the rest of the
    building comes out the same with or without them."""

    def __init__(self, under):
        super().__init__()
        self.under = under

    def get(self, x, y, z):
        key = (x, y, z)
        return self.blocks[key] if key in self.blocks else self.under.get(x, y, z)


def scratch(f):
    """A frame like f (a Frame) whose writes go nowhere (see _Scratch)."""
    return Frame(_Scratch(f.cv), f.ox, f.oz, f.front)


def storey_interior(f, rng, hx, hz, y, sh, program, wall_blk, stairs_cells, front_door_u, max_side=9, partitions=True, side_doors=(),
                    tier="mortal", furn_free=(), bare=False):
    """Partition one storey and furnish its rooms. program: list of room types to hand out. side_doors: interior cells
    just inside doorways in the outer walls (to rooms built next to this one) - kept free of partitions and furniture.
    furn_free: cells kept free of furniture and lamps only (the way on at the foot and off at the head of stairs).
    bare (内饰：无): the partitions and doorways as ever, the furniture and lamps not kept. A room of EMPTY_ROOM gets
    neither."""
    rect = (-hx + 1, -hz + 1, hx - 1, hz - 1)
    rooms, walls_ = bays(rect) if partitions == "bays" else bsp(rect, max_side, rng) if partitions else ([rect], [])
    doors = []
    for axis, c, a0, a1, door in walls_:
        for t in range(a0, a1 + 1):
            for k in range(1, sh):
                cell = (c, t) if axis == "u" else (t, c)
                if cell in stairs_cells:                 # leave the stairs (and the space over them) as they are
                    continue
                if abs(t - door) <= 0 and k <= 3:
                    f.put(cell[0], y + k, cell[1], AIR)
                    continue
                f.put(cell[0], y + k, cell[1], wall_blk)
        if not a0 <= door <= a1:          # a wall piece without a doorway
            continue
        doors.append((c, door) if axis == "u" else (door, c))
        # doorway frame lintel
        cell = (c, door) if axis == "u" else (door, c)
        f.put(cell[0], y + 3, cell[1], AIR)
        f.put(cell[0], y + 4, cell[1], wall_blk)
    for (cu, cv) in side_doors:          # a partition must not close a doorway to the next room
        for k in range(1, 4):
            f.put(cu, y + k, cv, AIR)
    rooms.sort(key=lambda r: (-(r[3]), abs(r[0] + r[2])))   # rooms nearest the front first, the middle one first
    used = []
    ff = scratch(f) if bare else f
    for i, (a, b, c, d) in enumerate(rooms):
        rt = program[i % len(program)]
        used.append((a, b, c, d, rt))
        if rt == EMPTY_ROOM:
            continue
        dlist = [dd for dd in doors if a - 1 <= dd[0] <= c + 1 and b - 1 <= dd[1] <= d + 1]
        if b <= hz - 1 <= d + 1 and a <= front_door_u <= c:
            dlist.append((front_door_u, hz - 1))
        dlist += [s for s in stairs_cells] + [s for s in furn_free if s not in stairs_cells]
        dlist += [s for s in side_doors if a - 1 <= s[0] <= c + 1 and b - 1 <= s[1] <= d + 1]
        FU.furnish(ff, rng, rt, a, b, c, d, y, doors=dlist, back_side="north", sh=sh)
    near_doors = {(du + i, dv + j) for (du, dv) in list(doors) + list(side_doors) for i in (-1, 0, 1) for j in (-1, 0, 1)}
    for (a, b, c, d, rt) in used:
        if rt == EMPTY_ROOM:
            continue
        big = (c - a + 1) * (d - b + 1) >= 120        # standing lamps only light the edges of a big room
        fixture = pick_light(rng, rt, tier, mounts=("ceiling", "inlay", "floor") if big else None)
        if not bare:                                  # (the lamp is still picked: the same draws either way)
            light_room(f, a, b, c, d, y, sh, set(stairs_cells) | set(furn_free) | set(side_doors) | near_doors,   # a room on the house's axis gets its lamps in pairs
                       fixture=fixture, mirror=a + c == 0)
    return used


def _passable(st):
    """Air, or something you walk over (carpet, a pressure plate): an aisle laid with carpet is still a place to light."""
    return not st or st == AIR or "carpet" in st or "pressure_plate" in st


def _is_lamp(st):
    return bool(st) and any(key in st for key, _ in _EMIT)


def pick_light(rng, room_type, tier, mounts=None):
    """One kind of lamp for a room, from the light fixtures in data/parts/lights.json (by room type and tier)."""
    if not _parts_mod.LIGHTS:
        _parts_mod.reload_lights()
    cands = [e for e in _parts_mod.LIGHTS.values()
             if (not e.get("tiers") or tier in e["tiers"]) and (not e.get("rooms") or room_type in e["rooms"])
             and e.get("mount", "hang") in (mounts or arch.INDOOR_MOUNTS)]
    if not cands:
        return None

    def weight(e):                          # a lamp made for this kind of room (it names the room) is preferred
        return e.get("weight", 1) * (3 if room_type in (e.get("rooms") or ()) else 1)
    total = sum(weight(e) for e in cands)
    r = rng.random() * total
    for e in cands:
        r -= weight(e)
        if r <= 0:
            return e
    return cands[-1]


# light the blocks give (what the planner counts as already there)
_EMIT = (("minecraft:light[", 15), ("spirit_light", 15), ("soul_lantern", 10), ("sea_lantern", 15), ("lantern", 15), ("campfire", 15),
         ("glowstone", 15), ("froglight", 15), ("shroomlight", 15), ("torch", 14), ("end_rod", 14), ("candle", 9))
TARGET_LIGHT = 12      # every cell you walk through gets at least this much block light (the unseen light blocks see to it)
def light_block():
    """The unseen light (arch.unseen_light: vanilla's 光源方块)."""
    return arch.unseen_light()


def light_room(f, a, b, c, d, y, sh, keep_free, fixture=None, mirror=False, mask=None):
    """Light a room in two steps. The lamps you see are there for looks: a few of one kind (fixture: hung just above
    head height, set into the ceiling or the floor, or standing against a wall) where a lamp belongs - the middle of
    the room, a pair across a room built on an axis (mirror), an even grid in a hall. Then the light you do not see:
    vanilla light blocks at head height over the darkest cells until every cell you walk through gets TARGET_LIGHT,
    counting the lamps and candles the furniture brought. Light falls off by one per block (horizontal plus vertical),
    as in the game; the room is a plain rectangle (mask: only these cells, e.g. inside a round wall).
    No fixture: a hanging lantern."""
    fixture = fixture or dict(mount="none", light=0, blocks=[[0, "minecraft:air"]])   # no lamp of its own: only the unseen light
    sources = []
    for u in range(a, c + 1):
        for v in range(b, d + 1):
            for k in range(1, sh):
                st = f.get(u, y + k, v)
                if not st:
                    continue
                for key, lv in _EMIT:
                    if key in st and "lit=false" not in st:
                        if key == "candle":               # 3 light per candle in the block (1-4)
                            m = re.search(r"candles=(\d)", st)
                            lv = 3 * int(m.group(1)) if m else 3
                        elif key in ("minecraft:light[", "spirit_light"):
                            m = re.search(r"level=(\d+)", st)
                            lv = int(m.group(1)) if m else 15
                        sources.append((u, v, k, lv))
                        break
    hang_k = min(3, sh - 1)                 # a lantern at y+3 hangs just above a standing player
    mount = fixture.get("mount", "hang")
    lamp_k = {"hang": hang_k, "ceiling": sh, "floor": 1, "inlay": 0}.get(mount, hang_k)
    top_block = max(fixture["blocks"], key=lambda t: t[0])[0]

    def level(u, v):
        best = 0
        for su, sv, sk, lv in sources:
            best = max(best, lv - abs(u - su) - abs(v - sv) - abs(1 - sk))
        return best

    def free(u, v):
        if (u, v) in keep_free:
            return False
        if mount == "floor" and not (u in (a, c) or v in (b, d)):     # standing lamps only along the walls
            return False
        if mount == "floor":                 # ...and not in front of a gap in that wall (a doorway)
            for ou, ov in ((u - 1, v), (u + 1, v), (u, v - 1), (u, v + 1)):
                if not (a <= ou <= c and b <= ov <= d) and all(_passable(f.get(ou, y + k, ov)) for k in (1, 2)):
                    return False
        if mount == "inlay":                 # set into the floor where people walk (not under furniture)
            return all(_passable(f.get(u, y + k, v)) for k in (1, 2)) and not f.get(u, y + 1, v)
        if mount == "ceiling":
            st = f.get(u, y + sh - 1, v)
            return not (st and st != AIR and "lantern" not in st and "chain" not in st)
        if mount == "hang":                  # a hanging lamp only needs its own cell (a table or a seat may stand under it)
            return all(_passable(f.get(u, y + k, v)) for k in range(hang_k, min(sh, hang_k + 2)))
        for k in range(1, 1 + top_block + 1):                 # a standing lamp needs its whole column
            if not _passable(f.get(u, y + k, v)) or (k == 1 and f.get(u, y + k, v)):
                return False
        return True

    def walkable(u, v):                     # only cells a person can stand in need light (not under a bed or shelf)
        return all(_passable(f.get(u, y + k, v)) for k in (1, 2))

    cells = [(u, v) for u in range(a, c + 1) for v in range(b, d + 1) if mask is None or (u, v) in mask]
    if not cells:
        return
    targets = [t for t in cells if walkable(*t)] or cells
    cu, cv = (a + c) / 2, (b + d) / 2
    placed = []
    width = c - a + 1

    def twin(u):
        return a + c - u

    def put_lamp(lu, lv):
        base_k = {"hang": hang_k, "ceiling": sh, "floor": 1, "inlay": 0}.get(mount, hang_k)
        for pu in ({lu, twin(lu)} if mirror else {lu}):
            for dk, st in fixture["blocks"]:
                f.put(pu, y + base_k + dk, lv, st)
            sources.append((pu, lv, lamp_k + (top_block if mount == "floor" else 0), fixture.get("light", 15)))
            placed.append((pu, lv))

    def ok(u, v):                           # free (and so is its mirror twin)
        return free(u, v) and (not mirror or free(twin(u), v))

    def spaced(lo, hi, step):               # positions about `step` apart, centred between lo and hi
        n = max(1, round((hi - lo) / step))
        return [round(lo + (hi - lo) * (i + 0.5) / n) for i in range(n)]

    # 1. the lamps you see, where a lamp belongs
    if mount == "floor":                    # standing lamps: one against each side wall, half way along
        spots = [(a, round(cv)), (c, round(cv))]
    elif len(cells) >= 120:                 # a hall: an even grid, 7 apart
        spots = [(gu, gv) for gu in spaced(a, c, 7) for gv in spaced(b, d, 7)]
    elif mirror and width >= 7:             # a room on the axis: a pair, a quarter of the way in from each side
        spots = [(round(cu - width / 4), round(cv))]
    else:
        spots = [(round(cu), round(cv))]
    for su, sv in spots if mount != "none" else ():
        if mirror and su > cu:
            continue
        cands = sorted((t for t in cells if ok(*t) and abs(t[0] - su) + abs(t[1] - sv) <= 2
                        and all(abs(t[0] - pu) + abs(t[1] - pv) >= 3 for pu, pv in placed)),
                       key=lambda t: abs(t[0] - su) + abs(t[1] - sv))
        if cands:
            put_lamp(*cands[0])
    # 2. the light you do not see: a light block at head height over the darkest cell left, until every cell you walk
    #    through is lit (no collision, so it stands in no one's way; a player holding the light item sees and breaks it)
    #    A map of the light at walking height is kept and updated around each new light (it used to be worked out
    #    again from every source for every cell, which a big room made take minutes); the cells are gone through in
    #    order and each one still too dark gets a light over it or as near as there is room.
    lk = 2 if sh >= 4 else 1
    lvl = {t: 0 for t in cells}

    def shine(su, sv, sk, lv):
        top = lv - abs(1 - sk)
        rad = top - TARGET_LIGHT              # farther than this it cannot make a cell bright enough
        for du in range(-rad, rad + 1):
            rem = rad - abs(du)
            for dv in range(-rem, rem + 1):
                cc = (su + du, sv + dv)
                if cc in lvl:
                    val = top - abs(du) - abs(dv)
                    if val > lvl[cc]:
                        lvl[cc] = val

    for src in sources:
        shine(*src)
    near = [(0, 0)] + sorted(((du, dv) for du in range(-2, 3) for dv in range(-2, 3) if (du, dv) != (0, 0)),
                             key=lambda t: abs(t[0]) + abs(t[1]))
    tset = set(targets)
    for t in sorted(targets, key=lambda t: (t[1], t[0])):
        if lvl[t] >= TARGET_LIGHT:
            continue
        for du, dv in near:
            cc = (t[0] + du, t[1] + dv)
            if cc in tset and cc not in keep_free and not f.get(cc[0], y + lk, cc[1]):
                f.put(cc[0], y + lk, cc[1], light_block())
                sources.append((cc[0], cc[1], lk, 15))
                shine(cc[0], cc[1], lk, 15)
                break


# ------------------------------------------------------------------------------------------------ archetype: house

def house(f, y, rng, spec, tier, hx=None, hz=None, n=None, ground=None):
    k = TIER_K[tier]
    outer = f
    may_wing = not spec.get("_wing") and not spec.get("_no_wing") and spec["arch"] == "house"
    if may_wing:                       # built on a scratch canvas; a side wing is joined to it at the end (joinparts)
        f = Frame(Canvas(), outer.ox, outer.oz, outer.front)
    walls_name = pick(rng, spec["walls"])
    W = set_walls(walls_name)
    bay_ctx = dict(chosen={}, tier=tier, walls=walls_name, style=spec.get("_bay_style"))
    WALL_FILL[0] = W["fill"]
    roof_theme(pick(rng, spec["themes"]))
    if hx is None:
        w = odd(rng.randint(*spec["size"][0]) * (1 + (k - 1) * 0.6))
        d = odd(rng.randint(*spec["size"][1]) * (1 + (k - 1) * 0.6))
        hx, hz = w // 2, d // 2
    n = n or rng.randint(*spec["storeys"])
    sh = int(round(rng.randint(*spec["sh"]) * (1 + (k - 1) * 0.35)))
    sh = spec.get("_sh") or sh                 # 单体建造: the storey height the player set
    pod = rng.randint(*spec["podium"]) + (1 if k > 1.2 else 0)
    if spec.get("_pod") is not None:            # 单体建造: the platform the player asked for (0 = the floor on the ground)
        pod = spec["_pod"]
    form = pick(rng, spec["roofs"])
    # podium / plinth
    yb = y
    pstyle = spec.get("_podium_style") or "plain"
    colon = 2 if spec.get("_colonnade") else 0          # 周围廊: a ring of posts this far out from the walls
    margin = spec.get("_podium_margin")                  # 台基边距: cells of walk round the walls (None: 自动, one)
    plat = None                                          # the 台基 as drawn (podium), widened by widen_podium
    vdepth = (3 if hx < 7 else 4) if spec.get("_veranda") else 0   # 前廊 the player asked for: how far out it reaches
    bdepth = (3 if hx < 7 else 4) if spec.get("_back_veranda") else 0   # 后廊
    # the faces with a way out at the ground storey (local fronts: south the front), each gets its own flight down the
    # platform: the back too with 前后门 or 穿堂 (a gate hall open behind had a railing there and no way down: in at the
    # front, no way out the other side), the sides with 四面门
    backs = (["north"] if spec.get("_back_door") or spec.get("_through") else []) + (["east", "west"] if spec.get("_four") else [])
    if spec.get("_podium_shape") == "round" and pod > 0:
        # 台基形状 圆: a round 坛 under the hall - a disc (or drums) round its corners, the 台基边距 out past them
        pstyle = "plain" if pstyle == "chengtai" else pstyle         # (a 城台 is square: round, a plain one)
        out = max(colon, vdepth, bdepth)
        rc = math.ceil(math.hypot(hx + colon, hz + out) - 0.5)
        round_platform(f, rc, y, pod, pstyle, spec.get("_base"), spec.get("_no_front_door"), spec.get("_step_slope"), margin,
                       backs, half=2 if hx <= 4 else 3 if hx <= 8 else 4)
        yb = y + pod
    elif pstyle == "chengtai" and pod > 0:
        yb = chengtai(f, hx, hz, y, pod, max(colon, vdepth, bdepth), spec.get("_base"), spec.get("_tunnels", 0), margin,
                      cross=bool(spec.get("_four")))
        spec = dict(spec, _back_door=True)                # 城楼: doors front and back (the stair comes up behind it)
    elif pstyle in ("xumizuo", "tiers") and pod > 0:
        # 须弥座 (one tier) or 三层 (three, stepping in): white jade with a balustrade round every top, a 御路 flight in front
        # (wide enough to carry the gallery; 台基边距: that many cells of walk inside the balustrade)
        plat = [pstyle, max(colon, vdepth, bdepth, margin or 1), 0, 0]
        yb, plat_cv = podium(f, hx, hz, y, pod, plat, spec, W, backs)
    elif pod:
        ring = 0 if spec.get("_no_podium_ring") else (margin or 1)   # 单体建造: the platform stays inside the drawn rectangle
        ring = max(ring, colon)                              # (under the colonnade too)
        # (and out under a front gallery, the steps in front of it; and under a back one)
        plat = ["plain", ring, max(ring, vdepth), max(ring, bdepth)]
        yb, plat_cv = podium(f, hx, hz, y, pod, plat, spec, W, backs)
    bay = 3 if hx <= 5 else 4 if hx <= 9 else 5
    door_w = 1 if hx <= 4 else 2 if hx <= 8 else 3
    taper = max(0, int(spec.get("_taper") or 0))     # 单体建造 收分: every storey set in this much on each side
    side_doors = dict(spec.get("_side_doors") or {})
    wing_plan = None
    if may_wing and hx >= 4 and rng.random() < 0.4:   # a side wing, planned now so the doorway is kept free inside
        side = rng.choice((-1, 1)) if n == 1 else 1      # the stairs run along the west wall: wings of taller houses go east
        whz = max(3, hz - 1 - rng.randint(0, 2))
        whx = max(3, min(hx, rng.randint(3, 6)))
        wing_plan = (side, whx, whz)
        side_doors[(side * hx, -(hz - whz))] = 1
    rows = BRACKET_L.get(spec.get("_brackets", 0), 1)   # 斗拱 the player set (自动: one row, as ever)
    extra = spec.get("_extra_eaves", 1 if spec.get("_double_eave") else 0)
    cut = 0
    if n > 1 and not extra and spec.get("_storey_eaves"):
        # 单体建造 最上一层: every skirt meets the wall's top row (its storey's top - 1), but the roof sat on the top
        # storey's ceiling a row above its walls - from outside the top storey showed two more rows of wall than every
        # other one (more under a taller bracket set lifting its eaves). Its ceiling comes down that much, so the
        # roof's eaves meet its walls where the skirts meet theirs: the walls drawn the same as below, their top rows
        # under the eaves like theirs, the room inside that much lower. (重檐: its lower eave is at the storey line
        # already. The sect's own kinds stay as they were: their streets put a lower roof at a neighbour's door head.)
        cut = max(0, min(2 - max(0, 6 - sh) + max(0, rows - 1), sh - 5))   # (never under 5: a room four high)
    stairs_cells = set()
    well = set()                       # the hole in this floor where the stairs from below come up
    above_free = set()                 # cells of this storey kept free around the top of the stairs from below
    below_furn_free = set()            # ... and kept free of furniture: the way off the top of them
    for s in range(n):
        ys = yb + s * sh
        shs = sh - cut if s == n - 1 else sh   # (the room inside the top storey: lower by the cut)
        hxs, hzs = max(2, hx - s * taper), max(2, hz - s * taper)       # 收分: this storey set in by taper a side
        hxn, hzn = (max(2, hx - (s + 1) * taper), max(2, hz - (s + 1) * taper)) if s < n - 1 else (hxs, hzs)
        bay = 3 if hxs <= 5 else 4 if hxs <= 9 else 5
        door_w = 1 if hxs <= 4 else 2 if hxs <= 8 else 3
        # floor
        for u in range(-hxs, hxs + 1):
            for v in range(-hzs, hzs + 1):
                if (u, v) not in well and not _is_lamp(f.get(u, ys, v)):   # keep lamps set into the ceiling below
                    f.put(u, ys, v, W["floor"])
        # walls; no windows next to this storey's stairs or onto a wing / neighbouring room
        no_win = set(spec.get("_no_window_cells") or ())
        if wing_plan:
            wside, _, wwhz = wing_plan
            for vv in range(-hzs, -hzs + 2 * wwhz + 1):
                no_win.add((wside * hxs, vv))
        if s < n - 1:                                       # (mirrored, so the two gables still match)
            for (su, sv) in stair_plan(hxn, hzn, sh)["reserve"]:
                for du, dv in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    no_win.add((su + du, sv + dv))
                    no_win.add((-(su + du), sv + dv))
        wspec = dict(spec, _no_window_cells=no_win)
        fo = ((spec.get("_floors") or [{}] * n)[min(s, len(spec.get("_floors") or [{}]) - 1)] or {}).get("open")
        if fo:                                       # 开敞 set for this floor alone (a stage on the ground floor, a closed room over it)
            wspec.update(_open=fo == "open", _open_front=fo == "front", _through=fo == "through")
        WINDOWS.clear()
        draw_walls(f, rng, W, hxs, hzs, ys, sh, bay, s, n, wspec, door_w, bay_ctx)
        if shs < sh:                       # (the top storey's walls over its lower ceiling go: the roof comes down there)
            for u in range(-hxs - 1, hxs + 2):
                for v in range(-hzs - 1, hzs + 2):
                    if abs(u) >= hxs or abs(v) >= hzs:
                        x, z = f.w(u, v)
                        for yy in range(ys + shs + 1, ys + sh + 1):
                            f.cv.blocks.pop((x, yy, z), None)
        # doorways to rooms built against this one (单体建造): three high with a beam over them (two when the storey is
        # low) - two was a crouch through
        inner = []
        for (du, dv), levels in side_doors.items():
            if s < levels:
                tall = min(3, shs - 1)                # (a storey of 4: up to its wall plate - the neighbour's is 3 high)
                for kk in range(1, tall + 1):
                    f.put(du, ys + kk, dv, AIR)
                if shs > tall:
                    f.put(du, ys + tall + 1, dv, W["beam"])
                inner.append((du - (1 if du > 0 else -1), dv) if abs(du) == hxs else (du, dv - (1 if dv > 0 else -1)))
        for cell, levels in (spec.get("_free_cells") or {}).items():   # in front of the door of a tower standing in here
            if s < levels and tuple(cell) not in inner:
                inner.append(tuple(cell))
        # every door the wall bays put in (the front door, doors out to a balcony, 隔扇 all along the front): the cell
        # just inside it is kept free of partitions, furniture and lamps like a doorway to the next room
        for u in range(-hxs + 1, hxs):
            if "_door" in (f.get(u, ys + 1, hzs) or "") and (u, hzs - 1) not in inner:
                inner.append((u, hzs - 1))
            if "_door" in (f.get(u, ys + 1, -hzs) or "") and (u, -hzs + 1) not in inner:
                inner.append((u, -hzs + 1))
        # stairs to the next storey: along the west wall from the back
        below_free = above_free
        next_well, next_free = set(), set()
        if s < n - 1:
            sp = stair_plan(hxn, hzn, sh)
            lad_face, lad_post = "east", (-1, 0)
            if sp["ladder"]:                       # (set in: its wall is further out - a post holds it, but not one standing
                su0, sv0 = sp["steps"][0][:2]      # on the ladder up from below: then it hangs on the back wall)
                if f.get(su0 - 1, ys + 1, sv0) in (None, AIR) and ((su0 - 1, sv0) in well or f.get(su0, ys + 1, sv0 - 1) not in (None, AIR)):
                    lad_face, lad_post = "south", (0, -1)
            for su, sv, rise, facing in sp["steps"]:
                f.put(su, ys + rise, sv, B(W["ladder"], facing=lad_face) if sp["ladder"] else B(W["inner_stairs"], facing=facing, half="bottom"))
                if sp["ladder"] and f.get(su + lad_post[0], ys + rise, sv + lad_post[1]) in (None, AIR):
                    f.put(su + lad_post[0], ys + rise, sv + lad_post[1], W["post"])
                for kk in range(ys + rise + 1, ys + rise + 4):
                    if kk >= ys + sh:              # headroom through the floor above
                        f.put(su, kk, sv, AIR)
            for lu, lv, rise in sp["landing"]:
                f.put(lu, ys + rise, lv, B("spruce_planks"))
                for kk in range(ys + rise + 1, ys + rise + 4):
                    if kk >= ys + sh:
                        f.put(lu, kk, lv, AIR)
            stairs_cells = set(sp["reserve"])
            next_well, next_free = sp["well"], sp["above"]
            furn_free, next_furn_free = set(sp["clear"]), set(sp["clear_above"])
        else:
            furn_free, next_furn_free = set(), set()
        furn_free |= below_furn_free
        below_furn_free = next_furn_free
        well, above_free = next_well, next_free
        # rooms: one program per storey when given (单体建造), else ground / upper
        floors = spec.get("_floors")
        if floors:
            fl = floors[min(s, len(floors) - 1)]
            program, split, plan = list(fl["program"]), fl.get("split"), fl.get("plan")
        else:
            program, split, plan = list(spec["ground"] if s == 0 else spec["upper"]), spec.get("_split"), spec.get("_plan")
        keep_free = (stairs_cells if s < n - 1 else set()) | below_free
        if s == 0 and (spec.get("_through") or spec.get("_back_door") or spec.get("_four")):   # 穿堂, 前后门: the way
            dwp = 0 if door_w <= 1 else 1                                                      # through stays clear
            keep_free |= {(u, v) for u in range(-dwp, dwp + 1) for v in range(-hzs + 1, hzs)}
            if spec.get("_four"):
                keep_free |= {(u, v) for u in range(-hxs + 1, hxs) for v in range(-dwp, dwp + 1)}
        windows = [w for w in WINDOWS if w[3] < shs]
        assigned = storey_interior(f, rng, hxs, hzs, ys, shs, program, W["fill"], keep_free, 0,
                                   max_side=spec.get("_max_side") or int(8 * min(k, 2)),
                                   partitions=False if wspec.get("_open") or (s == 0 and wspec.get("_through")) else   # (the plan's own value)
                                   (hxs >= 5 or hzs >= 5) and (len(set(program)) > 1 or split) and (plan or True),
                                   side_doors=inner, tier=tier, furn_free=furn_free, bare=bool(spec.get("_bare")))
        # windows by the room behind them: bedrooms keep only a high strip (no one looks in), stores none. Decided for
        # each mirrored pair (u and -u) together, and the front stays full windows, so the outside stays symmetric
        def behind(wu, wv):
            iu = wu - (1 if wu > 0 else -1) if abs(wu) == hxs else wu
            iv = wv - (1 if wv > 0 else -1) if abs(wu) != hxs else wv
            return next((t for (a, b, c, d, t) in assigned if a <= iu <= c and b <= iv <= d), None)
        level = {}
        for (wu, wv, wys, wk, wtop) in windows:
            if wv == hzs and abs(wu) != hxs:
                continue
            rt = behind(wu, wv)
            lv = 2 if rt in CLOSED_ROOMS else 1 if rt in PRIVATE_ROOMS else 0
            key = (abs(wu), wv)
            level[key] = max(level.get(key, 0), lv)
        for (wu, wv, wys, wk, wtop) in windows:
            lv = level.get((abs(wu), wv), 0)
            if lv == 2 or (lv == 1 and wk < wtop):
                f.put(wu, wys + wk, wv, W["fill"])
        stairs_cells = set()
        # skirt roof and balcony above every storey but the last (楼阁)
        if s < n - 1:
            top = ys + sh
            want = spec["balcony"] or rng.random() < 0.4          # (one draw, as before)
            if hxn < hxs:                                           # set in: the terrace always gets a rail at its edge
                for u in range(-hxs, hxs + 1):                      # and a floor: the ring between the walls below and the
                    for v in range(-hzs, hzs + 1):                  # walls above is this storey's ceiling, open to the sky
                        if (abs(u) > hxn or abs(v) > hzn) and f.get(u, top, v) in (None, AIR):
                            f.put(u, top, v, W["floor"])
                ring = lambda uu, vv: (abs(uu) == hxs and abs(vv) <= hzs) or (abs(vv) == hzs and abs(uu) <= hxs)
                for u in range(-hxs, hxs + 1):
                    for v in range(-hzs, hzs + 1):
                        if ring(u, v):
                            con = {d: True for d, (du, dv) in (("east", (1, 0)), ("west", (-1, 0)), ("south", (0, 1)), ("north", (0, -1)))
                                   if ring(u + du, v + dv)}
                            f.put(u, top + 1, v, B(W["rail"], **con))
            elif want:
                bw = 2 if spec["balcony"] else 1           # a real balcony is two wide, so the doors open onto a walk
                for u in range(-hxs - bw, hxs + bw + 1):
                    for v in range(-hzs - bw, hzs + bw + 1):
                        if abs(u) > hxs or abs(v) > hzs:
                            f.put(u, top, v, W["beam"])
                            if abs(u) == hxs + bw or abs(v) == hzs + bw:
                                f.put(u, top + 1, v, B(W["rail"], **({"east": True, "west": True} if abs(v) == hzs + bw else {"north": True, "south": True})))
            if spec.get("_storey_eaves"):          # 单体建造: each storey's 腰檐 like the roof - its reach, its lifted corners,
                out = EAVE_OUT.get(spec.get("_eave_out", 0)) or 2     # brackets under it, beasts on its corners
                lyr = (3, 2, 1)[3 - LIFT[spec["_lift"]]:] if LIFT.get(spec.get("_lift", 0)) else ((2, 1) if hxs >= 6 else (1,))
                up = max(0, 6 - sh)                   # (a low storey: the eave starts higher, clear over the doors under it)
                h = roof_surface(hxs + out + 1, hzs + out + 1, top - 1 + up, out, out, 0.7 * out, None, lyr if LIFT.get(spec.get("_lift", 0), 1) else (),
                                 (0, 0), inner=(hxs, hzs))
                h = {kk: max(vv, ys + 5) for kk, vv in h.items()}   # (its lip and rafter ends clear over the door heads)
                raster_roof(f, h, lambda u, v, val: max(top - 1 + up, math.floor(val) - 1), min_y=ys + 4)   # (a thin eave)
                if BRACKET_L.get(spec.get("_brackets", 0), 1):
                    eave_brackets(f, W, hxs, hzs, top, 3 if hxs <= 5 else 4 if hxs <= 9 else 5)
                if BEAST_N.get(spec.get("_beasts", 0)):
                    hips = [[(sgn * (hxs + i), sv * (hzs + i)) for i in range(out + 1, 0, -1)] for sgn in (-1, 1) for sv in (-1, 1)]
                    for cells in hips:
                        ridge_cells(f, h, cells)
                    ride_beasts(f, h, hips, BEAST_N[spec["_beasts"]])
            else:
                up = max(0, 5 - sh)                   # (its lowest tiles clear over the door heads)
                h = roof_surface(hxs + 3, hzs + 3, top - 1 + up, 2, 2, 1.4, None, (), (0, 0), inner=(hxs, hzs))
                raster_roof(f, {kk: vv for kk, vv in h.items()}, lambda u, v, val: top - 1 + up, min_y=ys + 4)
    top_y = yb + n * sh - cut
    head = yb + (n - 1) * sh + 4               # nothing outside lower than this over the top storey's doors (floor + 4)
    hxl, hzl = max(2, hx - (n - 1) * taper), max(2, hz - (n - 1) * taper)      # the last storey (smaller when set in)
    bay_l = 3 if hxl <= 5 else 4 if hxl <= 9 else 5
    for _eave in range(extra):
        # 重檐 (三重檐: twice): a lower eave round the top of the walls (brackets under it), the walls carried on up a
        # band above it (posts at the bay lines, a beam on top), and the main roof over that - the inside that much taller
        eave_brackets(f, W, hxl, hzl, top_y, bay_l)
        up = max(0, 6 - sh) if spec.get("_storey_eaves") else max(0, 5 - sh)   # (a low storey: the lower eave clear over its doors)
        h = roof_surface(hxl + 3, hzl + 3, top_y - 1 + up, 2, 2, 1.4, None, (), (0, 0), inner=(hxl, hzl))
        h = {kk: max(vv, head + 1) for kk, vv in h.items()}   # (its lip clear over the top storey's doors)
        raster_roof(f, h, lambda u, v, val: top_y - 1 + up, min_y=head)
        if BEAST_N.get(spec.get("_beasts", 0)):    # 走兽 on the lower eave too: its corner ridges and a few beasts
            low_hips = [[(sgn * (hxl + i), sv * (hzl + i)) for i in (3, 2, 1)] for sgn in (-1, 1) for sv in (-1, 1)]
            for cells in low_hips:
                ridge_cells(f, h, cells)
            ride_beasts(f, h, low_hips, BEAST_N[spec["_beasts"]])
        band = BAND(sh)
        for u in range(-hxl, hxl + 1):
            for v in range(-hzl, hzl + 1):
                if abs(u) != hxl and abs(v) != hzl:
                    continue
                post = (abs(u) == hxl and abs(v) == hzl) or (abs(v) == hzl and u % bay_l == 0) or (abs(u) == hxl and v % bay_l == 0)
                for kk in range(1, band + 1):
                    f.put(u, top_y + kk, v, W["beam"] if kk == band else W["post"] if post else W["fill"])
        if spec.get("_caihua") in CAIHUA:          # 彩画 on the band's 额枋 too (the row under its beam)
            paint_ring(f, spec["_caihua"], hxl, hzl, bay_l, top_y + band - 1)
        top_y += band
    for u in range(-hxl, hxl + 1):         # ceiling of the last storey
        for v in range(-hzl, hzl + 1):
            if spec.get("_caihua") and not cut and (abs(u) == hxl or abs(v) == hzl) and f.get(u, top_y, v) not in (None, AIR):
                continue                           # (the painted wall plate and the post heads stay)
            if not _is_lamp(f.get(u, top_y, v)):
                f.put(u, top_y, v, W["beam"] if (abs(u) == hxl or abs(v) == hzl) else B("spruce_planks"))
    if cut and spec.get("_caihua") in CAIHUA:      # (its painted plate came down with the ceiling)
        paint_ring(f, spec["_caihua"], hxl, hzl, bay_l, top_y)
    if rows > 1:                                   # taller bracket sets lift the eaves: the walls carried up a band
        for u in range(-hxl, hxl + 1):
            for v in range(-hzl, hzl + 1):
                if abs(u) == hxl or abs(v) == hzl:
                    for kk in range(1, rows):
                        f.put(u, top_y + kk, v, W["beam"])
        top_y += rows - 1
    ry = roof_for(f, hxl, hzl, top_y, form, rng, overhang=2 + (1 if k > 1.2 else 0),
                  steep=spec["steep"], tier_k=k,
                  vary=spec.get("_roof_rng"),      # 单体建造 rerolls a roof on its own
                  pitch=spec.get("_pitch", 0), eave_out=spec.get("_eave_out", 0), lift=spec.get("_lift", 0),
                  extra_out=(1 if colon and n == 1 and not spec.get("_double_eave") else 0) + max(0, rows - 1),   # (brackets carry the eaves out)
                  beasts=spec.get("_beasts", 0), cap=(spec["_cap"], W["rail"]) if spec.get("_cap") else None, min_y=head)
    if rows:
        eave_brackets(f, W, hxl, hzl, top_y, bay_l, levels=rows)
    eave_fascia(f, W, hxl, hzl, top_y, min_y=head)       # (clear over the door heads: a door is floor+1..+2, the board not at +3)
    if colon:                                      # 周围廊: a walk round the house, posts under the eaves
        bay0 = 3 if hx <= 5 else 4 if hx <= 9 else 5
        dw = 1 if hx <= 4 else 2 if hx <= 8 else 3
        for u in range(-hx - colon, hx + colon + 1):
            for v in range(-hz - colon, hz + colon + 1):
                if (abs(u) > hx or abs(v) > hz) and f.get(u, yb, v) is None:
                    f.put(u, yb, v, W["floor"])
        ex, ez = hx + colon, hz + colon
        for u in range(-ex, ex + 1):
            for v in range(-ez, ez + 1):
                if abs(u) != ex and abs(v) != ez:
                    continue
                corner = abs(u) == ex and abs(v) == ez
                along = u if abs(v) == ez else v
                if not corner and along % bay0:
                    continue
                if v == ez and abs(u) <= dw and not corner:      # not in front of the door
                    continue
                if not corner:                                   # nor in front of any other way through the wall
                    wu, wv = (u, hz if v > 0 else -hz) if abs(v) == ez else (hx if u > 0 else -hx, v)
                    w1 = f.get(wu, yb + 1, wv) or ""
                    if w1 == "" or w1 == AIR or "_door" in w1:
                        continue
                hit = next((yy for yy in range(yb + 1, yb + sh + 8) if f.get(u, yy, v) is not None), None)
                if hit is None:
                    continue
                for yy in range(yb + 1, hit):
                    f.put(u, yy, v, W["post"])
    # variety in plan and elevation: a front veranda (前廊) and/or a side wing (厢), by chance
    wing_ok = not spec.get("_wing") and spec["arch"] == "house" and hx >= 4
    if wing_ok and rng.random() < 0.35:
        veranda(f, rng, hx, hz, yb, sh, W)
    elif spec.get("_veranda"):                     # 单体建造: a front veranda the player asked for (on the platform's top)
        veranda(f, rng, hx, hz, yb, sh, W, keep_floor=True)
    if spec.get("_back_veranda"):                  # 单体建造: and / or one along the back (the same, turned round)
        veranda(f.sub(0, 0, "north"), rng, hx, hz, yb, sh, W, keep_floor=True)
    wing_part = None
    if wing_plan:
        side, whx, whz = wing_plan
        base_pod = pod - (1 if k > 1.2 else 0)          # the wing stands on the same plinth height as the main block
        wspec = dict(spec, storeys=(1, 1), _wing=True, decor=(), shop=False, balcony=False, podium=(base_pod, base_pod),
                     roofs=("yingshan", "xuanshan") if form not in ("pyramid",) else ("pyramid",),
                     _side_doors={(-side * whx, 0): 1}, _floors=None, _tall_beside=(-side,))   # (the main block: as tall)
        wf = Frame(Canvas(), f.ox, f.oz, f.front).sub(side * (hx + whx), -(hz - whz))
        wres = house(wf, y, rng, wspec, tier, hx=whx, hz=whz, n=1)
        wing_part = (wf, whx, whz, wres)
    if spec.get("horsehead"):
        horsehead_walls(f, hxl, hzl, top_y, ry, W, [s for s in (-1, 1) if s not in (spec.get("_tall_beside") or ())])
    decorate(f, rng, spec, hx, hz, yb, n, sh, W, tier)
    if plat and plat[1]:                           # sills, hoods, lanterns by the door: never left shutting the walk round it
        widen_podium(f, hx, hz, y, pod, yb, plat, plat_cv, spec, W, backs)
    if pstyle == "chengtai" and pod > 0:
        way = chengtai_well(f, hx, hz, y, pod, max(colon, vdepth, bdepth), spec.get("_base"), spec.get("_neighbours"), margin)
        if way and not _walks_in(f, way[-1], hx, hz, yb, chengtai_size(pod, max(colon, vdepth, bdepth), margin)[0], spec.get("_neighbours")):
            # the top's walk from the stair to the tower's doors is cut off (a neighbour on the 城台): a door (a real one,
            # not a hole beside a corner post) in the tower's wall nearest where the stair comes up
            au, _, av = way[-1]
            wall = [(u, v) for u in range(-hx + 2, hx - 1) for v in (-hz, hz)] + [(u, v) for u in (-hx, hx) for v in range(-hz + 2, hz - 1)]
            wall.sort(key=lambda c: abs(c[0] - au) + abs(c[1] - av))
            for cu, cv in wall[:12]:
                du, dv = ((1 if cu > 0 else -1), 0) if abs(cu) == hx else (0, (1 if cv > 0 else -1))
                ou, ov, iu, iv = cu + du, cv + dv, cu - du, cv - dv
                if f.get(cu, yb + 1, cv) == W["post"] or not arch.solid_at(f, ou, yb, ov) or arch.solid_at(f, ou, yb + 1, ov)                         or arch.solid_at(f, ou, yb + 2, ov):
                    continue                              # (a post, or the stair's hole or something in front of it)
                facing = {(0, 1): "north", (0, -1): "south", (1, 0): "west", (-1, 0): "east"}[(du, dv)]
                for kk in (1, 2):
                    f.put(cu, yb + kk, cv, B(W.get("door") or "spruce_door", facing=facing, half="lower" if kk == 1 else "upper",
                                             hinge="left", open=False, powered=False))
                    if "stairs" not in (f.get(iu, yb + kk, iv) or ""):
                        f.put(iu, yb + kk, iv, AIR)
                break
    if may_wing:
        parts = []
        if wing_part:
            wf, whx, whz, wres = wing_part
            parts.append(_part(wf, whx, whz, y, wres["floor"] + wres["sh"]))
        parts.append(_part(f, hx, hz, y, top_y))
        joinparts.join(outer.cv, parts)
    return dict(hx=hx, hz=hz, top=ry, floor=yb, storeys=n, sh=sh, wall_top=top_y, cap_y=ry if spec.get("_cap") else None,
                top_sh=sh - cut)


def stairs_of(block, fallback):
    """The stairs block that goes with a block (smooth_quartz → smooth_quartz_stairs, deepslate_bricks →
    deepslate_brick_stairs, packed_mud → mud_brick_stairs ...), or fallback when the game has none."""
    if not block:
        return fallback
    name = block.split("[")[0].split(":")[-1]
    special = {"packed_mud": "mud_brick_stairs", "red_terracotta": "brick_stairs", "calcite": "polished_diorite_stairs",
               "andesite": "andesite_stairs", "stone": "stone_stairs"}
    for c in (special.get(name), name + "_stairs", name.replace("_bricks", "_brick") + "_stairs", name.replace("_tiles", "_tile") + "_stairs",
              name.replace("smooth_", "") + "_stairs", name.replace("polished_", "") + "_stairs", name[:-1] + "_stairs" if name.endswith("s") else None):
        if c and materials.present("minecraft:" + c):
            return "minecraft:" + c
    return fallback


def slab_of(block, fallback):
    """The slab that goes with a block, or fallback."""
    st = stairs_of(block, None)
    if st and materials.present(st.replace("_stairs", "_slab")):
        return st.replace("_stairs", "_slab")
    return fallback


def platform_mat(base):
    """A platform of the room's base material ({core, stairs, slab}), or None for the default white jade."""
    if not base:
        return None
    return dict(core=base, stairs=stairs_of(base, "minecraft:stone_brick_stairs"), slab=slab_of(base, "minecraft:stone_brick_slab"))


def podium(f, hx, hz, y, pod, plat, spec, W, backs=(), keep=False):
    """The 台基 of a house - 平台, or 须弥座 / 三层 - drawn on a canvas of its own and laid onto f's (keep: not over
    what is there now): (its top, that canvas). plat: [style, out, front, back], how far out from the walls it reaches
    a side (a 须弥座: its top tier's balustrade), the front and back of a 平台 (a gallery there). backs: the other
    faces with a flight down (local "north", "east", "west")."""
    pc = Canvas()
    g = Frame(pc, f.ox, f.oz, f.front)
    style, ring, front, back = plat
    if style in ("xumizuo", "tiers"):
        run = arch.step_run(spec.get("_step_slope"))                        # 坡度: cells per block
        step = tier_step(pod, spec.get("_podium_margin"), run)
        tiers = podium_tiers(pod, style, ring, step)
        sh_half = 2 if hx <= 4 else 3 if hx <= 8 else 4
        top = y
        for m, t in tiers:
            top += t
            arch.xumizuo(g, hx + m, hz + m, top, t, mat=platform_mat(spec.get("_base")))
        sv_half = 2 if hz <= 4 else 3 if hz <= 8 else 4                   # (a side flight: by the side's length)
        top = y
        for m, t in tiers:
            top += t
            arch.balustrade(g, hx + m, hz + m, top, [(-sh_half, sh_half)], back_gaps=[(-sh_half, sh_half)] if "north" in backs else (),
                            side_gaps=[(-sv_half, sv_half)] if "east" in backs else ())
        # one flight from the top down over all the tiers; tiers stepping out wide: a flight down each (tier_step)
        for m, ft, fb in [(tiers[-1][0], top, y)] if step <= 2 else tier_flights(tiers, y):
            if not spec.get("_no_front_door"):
                arch.stairs_down(g, sh_half, hz + m + 1, ft, fb, mat=platform_mat(spec.get("_base")), run=run)
            for d in backs:
                side = d != "north"
                arch.stairs_down(g.sub(0, 0, d), sv_half if side else sh_half, (hx if side else hz) + m + 1, ft, fb,
                                 mat=platform_mat(spec.get("_base")), run=run)
    else:
        for u in range(-hx - ring, hx + 1 + ring):
            for v in range(-hz - back, hz + 1 + front):
                for yy in range(y + 1, y + pod + 1):
                    g.put(u, yy, v, spec.get("_base") or W["base"])
        if ring and W.get("base_stairs"):           # 台明: the platform's top edge moulded with upside-down stairs
            for u in range(-hx - ring, hx + 1 + ring):
                for v in range(-hz - back, hz + 1 + front):
                    side = abs(u) == hx + ring
                    end = v == hz + front or v == -hz - back
                    if not (side or end) or (side and end):
                        continue
                    facing = "north" if v == hz + front else "south" if v == -hz - back else "west" if u == hx + ring else "east"
                    g.put(u, y + pod, v, B(W["base_stairs"], facing=facing, half="top"))
        pblock = spec.get("_base") or W["base"]
        psteps = stairs_of(pblock, W["steps"])             # steps of the platform's own material
        pslab = arch.slab_for(psteps)
        # 坡度 (step_slope): a block up a cell (45°, stairs) or gentler, over 2-4 cells a block with slabs
        flights = ([] if spec.get("_no_front_door") else [(g, hz + front)]) \
            + [(g.sub(0, 0, d), hz + back if d == "north" else hx + ring) for d in backs]
        for ff, edge in flights:
            for s_out, s_dn, s_cell in arch.flight(pod, arch.step_run(spec.get("_step_slope"))):
                for u in (-1, 0, 1):
                    ff.put(u, y + pod - s_dn, edge + 1 + s_out, arch.step_block(s_cell, psteps, pslab, "north"))
                    for yy in range(y + 1, y + pod - s_dn):   # filled under the step (no hollow under the upper steps)
                        ff.put(u, yy, edge + 1 + s_out, pblock)
        top = y + pod
    for key, st in pc.blocks.items():
        if not (keep and key in f.cv.blocks):
            f.cv.blocks[key] = st
    for key, runs in pc.cols.items():
        f.cv.cols[key].extend(runs)
    return top, pc


def walk_cut(f, hx, hz, yb):
    """The sides ("front", "back", "west", "east") where the walk round the walls on a platform (its top at yb) is
    cut: a place along the wall where something stands in it (a sill, a hood, a lantern by the door) and no cell out
    to the platform's edge has two blocks of air over something to stand on."""
    def at(u, yy, v):
        x, z = f.w(u, v)
        st = f.cv.blocks.get((x, yy, z))
        if st is None:
            st = next((s for y0, y1, s in f.cv.cols.get((x, z), ()) if y0 <= yy <= y1), None)
        return st

    cut = set()
    for side, cells, (du, dv) in (("front", [(a, hz) for a in range(-hx, hx + 1)], (0, 1)),
                                  ("back", [(a, -hz) for a in range(-hx, hx + 1)], (0, -1)),
                                  ("east", [(hx, a) for a in range(-hz, hz + 1)], (1, 0)),
                                  ("west", [(-hx, a) for a in range(-hz, hz + 1)], (-1, 0))):
        for u, v in cells:
            hit = False
            for d in range(1, 9):
                cu, cv = u + du * d, v + dv * d
                if _passable(at(cu, yb, cv)):
                    break                                   # (the platform's edge)
                if _passable(at(cu, yb + 1, cv)) and _passable(at(cu, yb + 2, cv)):
                    hit = False
                    break
                hit = True
            if hit:
                cut.add(side)
                break
    return cut


def widen_podium(f, hx, hz, y, pod, yb, plat, pc, spec, W, backs=()):
    """台基边距 at least what sticks out of the walls and one more: where a sill, a hood or a lantern by the door
    leaves no way past it, the platform grows a cell on that side (a 须弥座 all round; a few times at most) - lifted
    off and drawn again, its steps out in front of the new edge."""
    for _ in range(3):
        cut = walk_cut(f, hx, hz, yb)
        if not cut:
            return
        style, ring, front, back = plat
        if style == "plain":
            ring += 1 if cut & {"west", "east"} else 0
            plat = [style, ring, max(ring, front + ("front" in cut)), max(ring, back + ("back" in cut))]
        else:
            plat = [style, ring + 1, 0, 0]
        for key, st in pc.blocks.items():          # (what is still as the platform left it)
            if f.cv.blocks.get(key) == st:
                del f.cv.blocks[key]
        for key, runs in pc.cols.items():
            for r in runs:
                if r in f.cv.cols.get(key, ()):
                    f.cv.cols[key].remove(r)
        _, pc = podium(f, hx, hz, y, pod, plat, spec, W, backs, keep=True)


def square_platform(f, R, y, pod, spec, W):
    """台基形状 方 under a round tower (radius R): the square 台 a house that wide stands on - 平台, 须弥座, 三层 or a 城台
    (its tunnels; a tall one's stair is cut in once the tower is built: compose, chengtai_well). Returns its top."""
    style = spec.get("_podium_style") or "plain"
    margin = spec.get("_podium_margin")
    if style == "chengtai":
        return chengtai(f, R, R, y, pod, 0, spec.get("_base"), spec.get("_tunnels", 0), margin, cross=bool(spec.get("_four")))
    ring = margin or 1
    plat = [style, ring, 0, 0] if style in ("xumizuo", "tiers") else ["plain", ring, ring, ring]
    return podium(f, R, R, y, pod, plat, spec, W)[0]


def round_platform(f, R, y, pod, style, base, no_front, slope=None, margin=None, backs=(), half=1):
    """圆塔的台基 (pod blocks high): a disc one block wider than the tower (平台), or a 须弥座 drum, or three drums
    stepping in (三层) - those two white jade (or the base material) with a rail round each top and a flight up to the door
    in front (+v). The plain disc gets steps of its own material, filled underneath. slope: the room's step_slope.
    margin: the 台基边距 (cells out past the walls; None: one), the drums stepping out wide when it is (tier_step) with a
    flight down each. backs: the other faces with a flight (local "north", "east", "west"); half: a flight's half width
    (a square hall on a round 坛: R the circle round its corners)."""
    if pod <= 0:
        return
    rr = R + 0.5
    run = arch.step_run(slope)
    disc = lambda r: {(u, v) for u in range(-int(r) - 1, int(r) + 2) for v in range(-int(r) - 1, int(r) + 2) if math.hypot(u, v) <= r}
    faces = ([] if no_front else [f]) + [f.sub(0, 0, d) for d in backs]
    dirs = []                                                   # each flight's way out, in f's (u, v) (the rail open in
    for g in [f] + [f.sub(0, 0, d) for d in backs]:             # front even with no flight there, as it always was)
        (x0, z0), (x1, z1) = g.w(0, 0), g.w(0, 1)
        dirs.append(next((du, dv) for du, dv in ((0, 1), (0, -1), (1, 0), (-1, 0))
                         if f.w(du, dv) == (f.ox + x1 - x0, f.oz + z1 - z0)))
    if style not in ("xumizuo", "tiers"):
        m = margin or 1
        for (u, v) in disc(rr + m):
            for yy in range(y + 1, y + pod + 1):
                f.put(u, yy, v, base or masonry(u, yy, v))
        st = stairs_of(base, "minecraft:stone_brick_stairs")
        sl = arch.slab_for(st)
        for g in faces:
            for s_out, s_dn, s_cell in arch.flight(pod, run):
                for u in range(-half, half + 1):
                    v = R + m + 1 + s_out
                    g.put(u, y + pod - s_dn, v, arch.step_block(s_cell, st, sl, "north"))
                    for yy in range(y + 1, y + pod - s_dn):        # filled under the upper steps
                        g.put(u, yy, v, base or masonry(u, yy, v))
        return
    mat = platform_mat(base)
    core = base or Pal.JADE
    step = tier_step(pod, margin, run)
    tiers = podium_tiers(pod, style, margin or 0, step)
    top = y
    for m, h in tiers:
        top += h
        cells = disc(rr + m)
        for (u, v) in cells:
            for yy in range(top - h + 1, top + 1):
                f.put(u, yy, v, core)
        ring = {c for c in cells if any((c[0] + du, c[1] + dv) not in cells for du, dv in ((1, 0), (-1, 0), (0, 1), (0, -1)))}
        for (u, v) in ring:                                     # a rail round the top, open where a flight comes
            if any(u * du + v * dv > 0 and abs(u * dv - v * du) <= half for du, dv in dirs):
                continue
            con = {d: ("low" if (u + du, v + dv) in ring else "none") for d, (du, dv) in
                   (("east", (1, 0)), ("west", (-1, 0)), ("south", (0, 1)), ("north", (0, -1)))}
            straight = (con["east"] == con["west"] == "low" and con["north"] == con["south"] == "none") or \
                       (con["north"] == con["south"] == "low" and con["east"] == con["west"] == "none")
            if (u * 7 + v * 13) % 5 == 0:
                f.put(u, top + 1, v, B("quartz_pillar", axis="y"))
                f.put(u, top + 2, v, slab("quartz"))
            else:
                f.put(u, top + 1, v, B("diorite_wall", up=not straight, **con))
    for m, ft, fb in [(tiers[-1][0], top, y)] if step <= 2 else tier_flights(tiers, y):
        for g in faces:
            arch.stairs_down(g, half, int(rr) + m + 1, ft, fb, mat=mat, run=run)


# ------------------------------------------------------------------------------------------------ 单体建造: special shapes

SPECIAL_SHAPES = ("paifang", "screen", "gate", "glazedgate", "chuihua", "wall", "paving", "river", "ornament", "terrace", "street",
                  "stairs", "bridge")
# (台地 terrace, 街道 street: ground only - a level and a surface over the frame; compose.terrain_ground lays them, with
# retaining walls and steps where two levels meet; 石阶 stairs: ground that climbs a block a row from its front edge back)


ORNAMENTS = ("ding", "sundial", "measure", "vat", "well", "stall", "bamboo", "hay", "tree", "veg", "woodpile", "lion",
             "flowers", "pond", "rack", "lamp")   # 陈设 by variant: 鼎 日晷 嘉量 铜缸 井 货摊 竹丛 草垛 树 菜畦 柴垛 石狮 花坛 池 晒架 石灯
TREES = {   # 树 by kind: trunk, leaves, trunk height, crown (radius by layer, from the bottom)
    "huai": ("oak_log", "oak_leaves", 4, (2, 2, 2, 1)),            # 槐 / 榆: a round crown
    "liu": ("oak_log", "jungle_leaves", 4, (2, 2, 1)),             # 柳: a low crown, vines hanging from its edge
    "tao": ("cherry_log", "cherry_leaves", 3, (2, 2, 1)),          # 桃 / 梅: pink, low
    "song": ("spruce_log", "spruce_leaves", 3, (2, 1, 2, 1, 1, 0)),  # 松: tiers
    "zao": ("dark_oak_log", "dark_oak_leaves", 4, (2, 2, 1)),      # 枣 / 皂角: dark
    "yin": ("birch_log", "birch_leaves", 5, (1, 2, 2, 1)),         # 银杏 / 白杨: tall and pale
}


def town_ornament(f, y, rng, kind, hx, hz, base):
    """凡人城镇's 陈设 (the frame |u| <= hx, |v| <= hz; most are drawn 3 x 3 or 5 x 5 round the middle):
    tree     - a trunk on a patch of grass and a crown of leaves (persistent), its kind by chance (TREES)
    veg      - 菜畦: the frame in rows of farmland with crops (wheat, carrots, potatoes, beetroot), a ditch of water
               down the middle
    woodpile - 柴垛: logs stacked two high under a board, a chopping block
    lion     - 石狮: a stone lion on a plinth, facing out of the frame's front
    flowers  - 花坛: a raised bed edged in stone, grass and flowers, a shrub in the middle
    pond     - 池: water edged in stone, lily pads, a bed of clay
    rack     - 晒架: two posts, a line, cloth hung on it
    lamp     - 石灯: a stone lantern on a post
    Returns dict(floor, sh, top)."""
    R = max(1, min(hx, hz))
    if kind == "tree":
        sp = rng.choice(("huai", "huai", "liu", "tao", "song", "zao", "yin")) if R >= 2 else "tao"
        log, leaf, trunk, crown = TREES[sp]
        trunk += rng.choice((0, 0, 1))
        lv = B(leaf, persistent=True)
        f.put(0, y, 0, B("grass_block"))
        for u, v in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            f.put(u, y, v, B("rooted_dirt") if rng.random() < 0.5 else B("coarse_dirt"))
        top = y + trunk
        for k in range(1, trunk + 1):
            f.put(0, y + k, 0, B(log, axis="y"))
        yy = top
        for i, rad in enumerate(crown):
            rad = min(rad, R)
            yy = top + i
            for u in range(-rad, rad + 1):
                for v in range(-rad, rad + 1):
                    if rad >= 2 and abs(u) == rad and abs(v) == rad:
                        continue                          # (round off the corners)
                    if (u, v) == (0, 0) and i < len(crown) - 1 and sp != "song":
                        f.put(0, yy, 0, B(log, axis="y"))
                        continue
                    f.put(u, yy, v, lv)
        if sp == "song":
            for k in range(trunk + 1, trunk + len(crown) - 1):
                f.put(0, y + k, 0, B(log, axis="y"))
        if sp == "liu":                                   # 柳: vines down from the crown's edge
            for u, v in ((2, 0), (-2, 0), (0, 2), (0, -2), (2, 1), (-1, 2), (1, -2), (-2, -1)):
                if rng.random() < 0.7 and abs(u) <= R and abs(v) <= R:
                    for k in range(1, rng.randint(2, 3) + 1):
                        f.put(u, top - k, v, B("vine", up=True))
        return dict(floor=y, sh=yy - y, top=yy + 1)
    if kind == "veg":
        crops = [("wheat", 7), ("carrots", 7), ("potatoes", 7), ("beetroots", 3)]
        mid = 0
        for v in range(-hz, hz + 1):
            crop, most = rng.choice(crops)
            for u in range(-hx, hx + 1):
                if v == mid and hz >= 2:
                    f.put(u, y, v, B("water", level=0))
                    f.put(u, y - 1, v, B("dirt"))
                    continue
                f.put(u, y, v, B("farmland", moisture=7))
                f.put(u, y - 1, v, B("dirt"))
                f.put(u, y + 1, v, B(crop, age=rng.randint(max(1, most - 4), most)))
        return dict(floor=y, sh=1, top=y + 2)
    if kind == "woodpile":
        log = rng.choice(("oak_log", "spruce_log", "birch_log"))
        for u in (-1, 0, 1):
            for v in (-1, 0):
                for k in (1, 2):
                    f.put(u, y + k, v, B(log, axis="x"))
            f.put(u, y + 3, -1, B("spruce_slab", type="bottom"))
            f.put(u, y + 3, 0, B("spruce_slab", type="bottom"))
        f.put(1, y + 1, 1, B("stripped_oak_log", axis="y"))   # (the chopping block)
        return dict(floor=y, sh=3, top=y + 4)
    if kind == "lion":
        f.put(0, y + 1, 0, B("polished_andesite"))
        f.put(0, y + 2, 0, B("chiseled_stone_bricks"))
        f.put(0, y + 3, 0, B("stone_brick_stairs", facing="north", half="bottom"))
        f.put(0, y + 2, 1, B("stone_brick_stairs", facing="north", half="bottom"))
        return dict(floor=y, sh=3, top=y + 4)
    if kind == "flowers":
        edge = base if base else B("stone_bricks")
        flowers = ("poppy", "allium", "azure_bluet", "oxeye_daisy", "cornflower", "pink_tulip", "red_tulip", "lily_of_the_valley")
        for u in range(-R, R + 1):
            for v in range(-R, R + 1):
                if abs(u) == R or abs(v) == R:
                    f.put(u, y + 1, v, edge)
                else:
                    f.put(u, y, v, B("dirt"))
                    f.put(u, y + 1, v, B("grass_block"))
                    if (u, v) == (0, 0) and R >= 2:
                        f.put(u, y + 2, v, B(rng.choice(("azalea", "flowering_azalea"))))
                    elif rng.random() < 0.8:
                        f.put(u, y + 2, v, B(rng.choice(flowers)))
        return dict(floor=y, sh=2, top=y + 3)
    if kind == "pond":
        edge = base if base else B("stone_bricks")
        for u in range(-R, R + 1):
            for v in range(-R, R + 1):
                if abs(u) == R or abs(v) == R:
                    f.put(u, y, v, edge)
                    f.put(u, y - 1, v, edge)
                    f.put(u, y - 2, v, edge)
                else:
                    f.put(u, y - 2, v, B("clay"))
                    f.put(u, y - 1, v, B("water", level=0))
                    f.put(u, y, v, B("water", level=0))
                    if rng.random() < 0.35:
                        f.put(u, y + 1, v, B("lily_pad"))
        return dict(floor=y, sh=1, top=y + 2)
    if kind == "rack":
        for u in (-1, 1):
            for k in (1, 2, 3):
                f.put(u, y + k, 0, B("spruce_fence"))
        f.put(0, y + 3, 0, B("spruce_fence", east=True, west=True))
        f.put(-1, y + 3, 0, B("spruce_fence", east=True))
        f.put(1, y + 3, 0, B("spruce_fence", west=True))
        f.put(0, y + 2, 0, B(rng.choice(("white", "light_blue", "blue", "red", "yellow")) + "_stained_glass_pane", east=True, west=True))
        return dict(floor=y, sh=3, top=y + 4)
    # lamp
    f.put(0, y + 1, 0, B("polished_andesite"))
    f.put(0, y + 2, 0, B("stone_brick_wall", up=True))
    f.put(0, y + 3, 0, B("lantern", hanging=False))
    f.put(0, y + 4, 0, B("stone_brick_slab", type="bottom"))
    return dict(floor=y, sh=4, top=y + 5)


def special_shape(f, y, rng, spec, tier, shape, hx, hz):
    """A shape the room settings cannot make, in the frame |u| <= hx along the front, |v| <= hz deep, front +v:
    牌楼 (paifang), 影壁 (screen), 随墙门 (gate), 琉璃门 (glazedgate), 垂花门 (chuihua). Of the room's walls (W),
    tiles (the roof theme) and base. Returns dict(floor, sh, top)."""
    k = TIER_K[tier]
    wname = pick(rng, spec["walls"])
    W = set_walls(wname)
    roof_theme(pick(rng, spec["themes"]))
    sh = spec.get("_sh") or 6
    base = spec.get("_base") or W["base"]
    palace = wname in ("palace", "yellow", "jade")
    if shape in ("wall", "gate", "glazedgate") and hz >= THICK:   # 城墙: a frame 7 or more deep, a wall that thick
        return thick_wall(f, y, spec, shape, hx, hz, base, B("red_terracotta") if palace else W["fill"], max(4, min(16, sh)))
    if shape == "wall":                                   # 宫墙: a wall the frame long, its footing, a tiled coping
        d = 0 if hz < 2 else 1
        wall_h = max(4, min(10, sh))
        body = B("red_terracotta") if palace else W["fill"]
        for u in range(-hx, hx + 1):
            for v in range(-d, d + 1):
                f.put(u, y + 1, v, base)
                for kk in range(2, wall_h + 1):
                    f.put(u, y + kk, v, body)
        ry = roof_for(f, hx, d, y + wall_h, "xuanshan", rng, overhang=1, steep=0.45, tier_k=1.0)
        return dict(floor=y, sh=wall_h, top=ry + 3)
    if shape in ("terrace", "street", "stairs", "bridge"):   # ground only: compose.terrain_ground lays it with the others
        return dict(floor=y, sh=1, top=y)
    if shape == "ornament":                               # 陈设: a bronze piece on a stone plinth in the middle of the frame
        kind = ORNAMENTS[int(spec.get("_variant", 0) or 0) % len(ORNAMENTS)]
        if kind in ("tree", "veg", "woodpile", "lion", "flowers", "pond", "rack", "lamp"):
            return town_ornament(f, y, rng, kind, hx, hz, base)
        if kind == "stall":                               # 货摊 (a town's): a counter under a striped awning, open at the front
            cloth = rng.choice((("red_wool", "white_wool"), ("blue_wool", "white_wool"), ("yellow_wool", "brown_wool")))
            for u in (-1, 1):
                for yy in (y + 1, y + 2, y + 3):
                    f.put(u, yy, -1, B("spruce_fence"))
            f.put(0, y + 1, -1, B("barrel", facing="up"))
            for u in (-1, 0, 1):
                f.put(u, y + 1, 0, B("spruce_slab", type="top"))
                for v in (-1, 0):
                    f.put(u, y + 4, v, B(cloth[(u + 1) % 2]))
            f.put(rng.choice((-1, 1)), y + 2, 0, B(rng.choice(("flower_pot", "potted_red_tulip", "decorated_pot", "potted_fern"))))
            return dict(floor=y, sh=4, top=y + 5)
        if kind == "bamboo":                              # 竹丛: a clump on its own patch of grass
            for u, v, h in ((0, 0, 6), (1, -1, 5), (-1, 1, 4), (-1, -1, 3)):
                f.put(u, y, v, B("grass_block"))
                for k in range(1, h + 1):
                    f.put(u, y + k, v, B("bamboo", age=1, stage=0, leaves="large" if k >= h - 1 else "small" if k == h - 2 else "none"))
            return dict(floor=y, sh=6, top=y + 7)
        if kind == "hay":                                 # 草垛: bales stacked by a threshing floor
            for u, v, k in ((0, 0, 1), (1, 0, 1), (0, 1, 1), (0, 0, 2), (-1, -1, 1)):
                f.put(u, y + k, v, B("hay_block", axis="y"))
            f.put(1, y + 1, -1, B("composter"))
            return dict(floor=y, sh=3, top=y + 3)
        if kind == "well":                                # 井 (a town's): a stone curb round the shaft, a windlass frame
            curb = base if not palace else B("stone_bricks")
            for u in (-1, 0, 1):
                for v in (-1, 0, 1):
                    if u or v:
                        for yy in range(y - 3, y + 2):
                            f.put(u, yy, v, curb)
            f.put(0, y - 4, 0, curb)
            for yy in range(y - 3, y + 1):
                f.put(0, yy, 0, B("water", level=0))
            for u in (-1, 1):
                f.put(u, y + 2, 0, B("spruce_fence"))
                f.put(u, y + 3, 0, B("spruce_fence"))
            for u in (-1, 0, 1):
                f.put(u, y + 4, 0, B("spruce_slab", type="bottom"))
            f.put(0, y + 3, 0, B("chain", axis="y"))
            return dict(floor=y, sh=4, top=y + 5)
        stone = B("smooth_quartz") if palace else B("polished_andesite")
        for u in (-1, 0, 1):
            for v in (-1, 0, 1):
                f.put(u, y + 1, v, B("smooth_stone_slab", type="top") if (u or v) else stone)
        if kind == "ding":                                # 鼎: the vessel, two ears
            f.put(0, y + 2, 0, B("cauldron"))
            for u in (-1, 1):
                f.put(u, y + 2, 0, B("lightning_rod", facing="up"))
        elif kind == "sundial":                           # 日晷: a dial tilted on a pillar, the gnomon through it
            f.put(0, y + 2, 0, B("quartz_pillar", axis="y"))
            f.put(0, y + 3, 0, B("smooth_quartz_stairs", facing="north", half="bottom"))
            f.put(0, y + 4, 0, B("lightning_rod", facing="north"))
        elif kind == "measure":                           # 嘉量: a square measure on a pillar, under a little roof
            f.put(0, y + 2, 0, B("quartz_pillar", axis="y"))
            f.put(0, y + 3, 0, B("cut_copper"))
            f.put(0, y + 4, 0, B("cut_copper_slab", type="bottom"))
        else:                                             # 铜缸: a big water vat
            f.put(0, y + 2, 0, B("water_cauldron", level=3))
            for u, v, face in ((-1, 0, "east"), (1, 0, "west"), (0, -1, "south"), (0, 1, "north")):
                f.put(u, y + 2, v, B("cut_copper_stairs", facing=face, half="bottom"))
        return dict(floor=y, sh=4, top=y + 5)
    if shape == "paving":                                 # 铺地: bricks laid over the frame, a stone 甬路 to the front
        road = max(1, min(hx, hz) // 4)
        field = (B("stone_bricks"), B("cracked_stone_bricks"), B("stone_bricks"), B("polished_andesite"))
        for u in range(-hx, hx + 1):
            for v in range(-hz, hz + 1):
                if abs(u) < road:
                    blk = B("smooth_quartz") if palace else B("smooth_stone")
                elif abs(u) == road:
                    blk = B("chiseled_stone_bricks") if v % 4 == 0 else B("polished_andesite")
                else:
                    blk = field[rng.randrange(len(field))] if rng.random() < 0.15 else field[0]
                f.put(u, y, v, blk)
        return dict(floor=y, sh=1, top=y + 1)
    if shape == "river":                                  # 河桥: a channel along the frame, railed banks, bridges
        bank = 1 if hz >= 4 else 0
        wv = hz - bank                                    # |v| < wv: water
        deck = B("smooth_quartz") if palace else B("stone_bricks")
        rail_u = lambda: B("diorite_wall", east="low", west="low", north="none", south="none", up=False)
        rail_v = lambda: B("diorite_wall", north="low", south="low", east="none", west="none", up=False)
        width = 2 * hx + 1
        count = 1 if width < 21 else 3 if width < 41 else 5
        gap = max(7, width // (count + 1))
        centres = [0] + [s * i * gap for i in range(1, count // 2 + 1) for s in (1, -1)]
        bridge = {c + du for c in centres for du in (-1, 0, 1) if abs(c + du) <= hx}
        rvar = int(spec.get("_variant", 0) or 0)          # 0: bridges by the width; 1: none (a plain channel); 2: one
        if rvar in (1, 3, 4, 5):                          # bridge the frame wide (a street across); 3, 4, 5: none, the
            bridge, centres = set(), []                   # channel closed at the -u end, the +u end, both (a river's ends)
        elif rvar == 2:
            bridge, centres = set(range(-hx, hx + 1)), [0]
        ends = {3: (-hx,), 4: (hx,), 5: (-hx, hx)}.get(rvar, ())
        for u in range(-hx, hx + 1):
            for v in range(-hz, hz + 1):
                f.put(u, y - 3, v, base)                  # the bed
                if abs(v) < wv and u not in ends:
                    f.put(u, y - 2, v, B("water", level=0))
                    f.put(u, y - 1, v, B("water", level=0))
                    f.put(u, y, v, AIR)
                else:                                     # the banks, stone to the top
                    for yy in (y - 2, y - 1, y):
                        f.put(u, yy, v, base)
                    if abs(v) == wv and u not in bridge and u not in ends:
                        f.put(u, y + 1, v, rail_u())
        for u in bridge:                                  # each bridge: a deck one up, a ramp at each end, rails
            c = min(centres, key=lambda c: abs(u - c))
            side = abs(u - c) == 1 if rvar != 2 else abs(u) == hx
            for v in range(-hz, hz + 1):
                if abs(v) >= wv:                          # on the bank: a step up
                    f.put(u, y + 1, v, B(stairs_of(deck, "minecraft:stone_brick_stairs"), facing="north" if v > 0 else "south", half="bottom")
                          if abs(v) == hz else deck)
                else:
                    f.put(u, y + 1, v, deck)
                    f.put(u, y, v, deck if abs(v) >= wv - 1 else AIR)   # an arch under it over the water
                if side and abs(v) < hz:
                    f.put(u, y + 2, v, rail_v())
        return dict(floor=y, sh=2, top=y + 3)
    if shape == "paifang":                                # 牌楼: three bays, four posts, as wide as the frame
        span = max(5, hx)
        h_out = max(6, sh + 3)
        arch.paifang(f, y, span=span, h_in=h_out + 4, h_out=h_out)
        return dict(floor=y, sh=sh, top=y + h_out + 12)
    d = 0 if hz < 2 else 1                                # walls and gates: one or three thick
    if shape == "screen":                                 # 影壁: base, a framed body with a glazed panel, a tiled cap
        for u in range(-hx, hx + 1):
            for v in range(-d - 1, d + 2):
                for yy in (y + 1, y + 2):
                    f.put(u, yy, v, base)
        body = max(4, min(8, 2 * hx // 3))
        y0 = y + 2
        for u in range(-hx + 1, hx):
            for v in range(-d, d + 1):
                for kk in range(1, body + 1):
                    f.put(u, y0 + kk, v, W["post"] if abs(u) == hx - 1 else W["beam"] if kk in (1, body) else W["fill"])
        pw, ph = max(1, hx // 3), max(1, body // 3)
        glaze = B("cyan_glazed_terracotta") if materials.present("minecraft:cyan_glazed_terracotta") else W["fill"]
        for u in range(-pw, pw + 1):
            for kk in range(body // 2 - ph // 2 + 1, body // 2 + ph // 2 + 2):
                for v in (-d, d):
                    f.put(u, y0 + kk, v, glaze)
        ry = roof_for(f, hx - 1, d, y0 + body, "wudian", rng, overhang=1, steep=0.5, tier_k=1.0)
        return dict(floor=y, sh=body, top=ry + 3)
    if shape in ("gate", "glazedgate"):                   # 随墙门 / 琉璃门: a wall the frame's width, a gateway in the middle
        wall_h = max(5, min(8, 4 + hx // 4))
        for u in range(-hx, hx + 1):
            for v in range(-d, d + 1):
                f.put(u, y + 1, v, base)                  # a footing course
                for kk in range(2, wall_h + 1):
                    opening = abs(u) <= 1 and kk <= 5
                    f.put(u, y + kk, v, AIR if opening else W["fill"])
        for u in (-2, 2):                                 # posts either side of the way through, a lintel over it
            for v in range(-d, d + 1):
                for kk in range(2, 7):
                    f.put(u, y + kk, v, W["post"])
        for u in range(-2, 3):
            for v in range(-d, d + 1):
                f.put(u, y + 6, v, W["beam"])
        ry = roof_for(f, hx, d, y + wall_h, "xuanshan", rng, overhang=1, steep=0.45, tier_k=1.0)   # the coping
        top = ry
        if shape == "glazedgate":                         # 琉璃门: the gateway framed in glaze, a little roof of its own over it
            glaze = B("yellow_glazed_terracotta") if materials.present("minecraft:yellow_glazed_terracotta") else W["beam"]
            for u in range(-3, 4):
                for v in (-d - 1, d + 1):
                    for kk in range(1, wall_h + 2):
                        if abs(u) == 3 or kk in (wall_h, wall_h + 1):
                            f.put(u, y + kk, v, glaze if kk < wall_h else W["beam"])
            ry2 = roof_for(f.sub(0, 0, "south"), 3, d + 1, y + wall_h + 2, "xieshan", rng, overhang=1, steep=0.55, tier_k=1.0)
            top = max(top, ry2)
        return dict(floor=y, sh=wall_h, top=top + 3)
    # 垂花门: a little gate house under a 卷棚 roof: doors in the back row of posts, the front posts hanging short
    hz2 = max(2, hz)
    top_y = y + 1 + max(5, sh)
    for u in range(-hx, hx + 1):
        for v in range(-hz2, hz2 + 1):
            f.put(u, y + 1, v, base)                      # its floor, one up
    for v in range(-hz2, hz2 + 1):                        # side walls
        for sgn in (-1, 1):
            for yy in range(y + 2, top_y):
                f.put(sgn * hx, yy, v, W["post"] if abs(v) == hz2 or v == -1 else W["fill"])
    for u in range(-hx, hx + 1):                          # the back row: wall, the way through in the middle
        for yy in range(y + 2, top_y):
            f.put(u, yy, -1, AIR if abs(u) <= 1 and yy <= y + 5 else W["fill"] if abs(u) != hx else W["post"])
    for u in range(-hx, hx + 1):                          # beams over both rows
        f.put(u, top_y, -1, W["beam"])
        f.put(u, top_y, hz2, W["beam"])
    for sgn in (-1, 1):                                   # 垂莲柱: the front posts stop short, a carved knob under them
        u = sgn * max(2, hx - 1)
        for yy in range(top_y - 2, top_y):
            f.put(u, yy, hz2, W["post"])
        f.put(u, top_y - 3, hz2, B("dark_oak_fence"))
    for u in range(-hx + 1, hx):                          # 挂落: a fascia between them
        if f.get(u, top_y - 1, hz2) is None:
            f.put(u, top_y - 1, hz2, B("spruce_trapdoor", facing="north", half="top", open=False))
    for u in (-1, 0, 1):                                  # a step up at the front
        f.put(u, y + 1, hz2 + 1, B(stairs_of(base, "minecraft:stone_brick_stairs"), facing="north", half="bottom"))
    ry = roof_for(f, hx, hz2, top_y, "juanpeng", rng, overhang=1, steep=0.55, tier_k=k)
    return dict(floor=y + 1, sh=top_y - y - 1, top=ry + 3)


THICK = 3                                            # a wall or gate frame this half deep (7 deep) or more: 城墙, as thick
#                                                      as the frame (a shallower one: the 宫墙 3 thick under a tiled coping)


def thick_wall(f, y, spec, shape, hx, hz, base, body, wall_h):
    """城墙: a wall (or a gate in one) as thick as its frame (2 hz + 1), the frame's length. 收分 (the room's taper): each
    face leans in that many cells by the top (at most to a top 7 wide), the last course of each step a stair. On top a
    paved walk between 垛口 along the outer face (the front, +v) and a 女墙 along the inner one. A wall: 马道, a flight up
    along the inner face, cut into it, from the ground at one end to the walk at the other, its steps as the room's 坡度
    makes them (arch.flight: 45° a block a cell, gentler 2-4 cells a block), a 女墙 between it and the walk; none when the
    piece is too short for it, or with 门洞 无 (tunnels 1: a long wall in pieces, the 马道 in some of them only). A gate
    (随墙门 / 琉璃门): an arched 门洞 through the whole depth, 3 wide (5 on a frame 15 or more long), a glazed frame round
    both mouths on a 琉璃门. Returns dict(floor, sh, top) like special_shape."""
    H = wall_h
    t = max(0, min(int(spec.get("_taper") or 0), hz - 3))   # (the top at least 7 wide: a walk 5 wide between the parapets)

    def inset(kk):                                        # how far in each face is at course kk (1: the footing, H: the top)
        return round(t * (kk - 1) / (H - 1)) if H > 1 else 0
    ht = hz - t                                           # the top's half width: the parapets at v = +ht and -ht
    pave, cap = B("polished_andesite"), slab("stone_brick")
    lean = stairs_of(body, None) if "terracotta" not in body else None   # (a red wall: no stair of its colour)
    for u in range(-hx, hx + 1):
        x, z = f.w(u, 0)
        merlon = (x + z) % 2 == 0                         # (by the world cell: the 垛口 keep step across the pieces)
        for v in range(-hz, hz + 1):
            f.put(u, y + 1, v, base)                      # the footing course
            for kk in range(2, H + 1):
                e = hz - inset(kk)
                if abs(v) > e:
                    continue
                f.put(u, y + kk, v, pave if kk == H and abs(v) < ht else body)
                if lean and abs(v) == e and kk < H and inset(kk + 1) > inset(kk):   # 收分: the course before a step in,
                    f.put(u, y + kk, v, B(lean, facing="north" if v > 0 else "south", half="bottom"))   # sloped
        f.put(u, y + H + 1, ht, body)                     # 垛口: a parapet, every other cell a merlon, slabs on top
        if merlon:
            f.put(u, y + H + 2, ht, body)
        f.put(u, y + H + (3 if merlon else 2), ht, cap)
        f.put(u, y + H + 1, -ht, body)                    # 女墙: a low wall on the inner edge
        f.put(u, y + H + 2, -ht, cap)
    if shape == "wall":
        run = arch.step_run(spec.get("_step_slope"))
        steps = arch.flight(H, run)                       # (from the walk's layer down to the course over the ground)
        L = len(steps)
        if ht >= 3 and L // 2 + 1 <= hx and L - L // 2 <= hx and spec.get("_tunnels") != 1:
            st = stairs_of(base, "minecraft:stone_brick_stairs")
            sl = arch.slab_for(st)
            u_top = L - L // 2 - 1                        # the top step; the walk goes on past it (+u)
            for k, dn, kind in steps:
                u = u_top - k
                ly = y + H - dn
                for v in range(-hz, -ht + 2):             # the flight: the inner face's batter and two lanes behind it
                    for yy in range(y + 2, ly):
                        f.put(u, yy, v, body)
                    f.put(u, ly, v, arch.step_block(kind, st, sl, "east"))
                    for yy in range(ly + 1, y + H + 4):
                        f.put(u, yy, v, AIR)
                if ly <= y + H - 2:                       # a 女墙 between it and the walk while it is a drop down to it
                    f.put(u, y + H + 1, -ht + 2, body)
                    f.put(u, y + H + 2, -ht + 2, cap)
            f.put(u_top - L, y + H + 1, -ht + 1, body)  # (and across the walk's lane where the cut ends below it)
            f.put(u_top - L, y + H + 2, -ht + 1, cap)
        return dict(floor=y, sh=H, top=y + H + 3)
    w = 5 if hx >= 7 else 3                               # 门洞
    half = w // 2
    th = max(3, min(H - 3, 4 + half))                     # to the crown of the arch (the walk stays over it)
    for du in range(-half, half + 1):
        rise = th - (1 if abs(du) == half else 0)         # the sides a step lower: an arch
        for v in range(-hz, hz + 1):
            f.put(du, y, v, B("stone_bricks"))            # the paved way through
            for yy in range(y + 1, y + rise + 1):
                f.put(du, yy, v, AIR)
    for v in range(-hz, hz + 1):                          # the arch's shoulders: upside-down stairs all through
        if abs(v) <= hz - inset(th):
            for side in (-1, 1):
                f.put(side * half, y + th, v, stair("stone_brick", "east" if side < 0 else "west", "top"))
    if shape == "glazedgate":                             # 琉璃门: a glazed frame round both mouths
        glaze = B("yellow_glazed_terracotta") if materials.present("minecraft:yellow_glazed_terracotta") else body
        for kk in range(1, th + 2):
            e = hz - inset(kk)
            for u in range(-half - 1, half + 2):
                if abs(u) == half + 1 or kk == th + 1:
                    for v in (-e, e):
                        f.put(u, y + kk, v, glaze)
    return dict(floor=y, sh=H, top=y + H + 3)


def podium_tiers(pod, style, colon=0, step=2):
    """The tiers of a 须弥座 / 三层 platform, outermost first: (how far out from the walls, height). The heights add up
    to pod, the platform's height, whatever the style (三层 with less than three blocks has fewer tiers); each tier
    step cells out past the one over it (tier_step)."""
    m0 = max(2, colon + 1)
    if pod <= 0:
        return []
    if style == "xumizuo":
        return [(m0, pod)]
    n = min(3, pod)
    hs = [pod // n + (1 if i < pod % n else 0) for i in range(n)]          # the bottom tiers take the spare blocks
    return [(m0 + step * (n - 1 - i), h) for i, h in enumerate(hs)]


def tier_flights(tiers, y):
    """A flight down each tier of a platform (tiers: podium_tiers, outermost first, standing on y): (how far out from
    the walls its edge is, its top, the top of the tier under it - or y, the ground)."""
    out, top = [], y
    for m, h in tiers:
        out.append((m, top + h, top))
        top += h
    return out


def tier_step(pod, margin, run=1):
    """三层: how far a tier reaches out past the one over it. Two cells (one flight from the top down over all of them);
    with a 台基边距 wider than 4 (a 坛 or a 三台 much bigger than the hall) a third of the margin, at least a tier's own
    flight (its height times run, the 坡度's cells a block) and a cell to land on - each tier then has its own flight."""
    if not margin or margin <= 4:
        return 2
    return max(2, margin // 3, -(-max(1, pod) // min(3, max(1, pod))) * run + 1)


def chengtai_size(pod, colon=0, margin=None):
    """城台: (how far out from the walls of the tower on it, how high - the platform height as it is). margin: the
    台基边距 asked for (a 城台 much bigger than its tower: 钟楼, 佛香阁's terrace)."""
    return max(3, colon + 2, margin or 0), max(1, pod)


TUNNELS = {1: 0, 2: 1, 3: 3, 4: 5}                   # 单体建造 门洞 through a 城台: 无, 一个, 三个, 五个 (自动: by its width)


def chengtai(f, hx, hz, y, pod, colon, base=None, tunnels=0, margin=None, cross=False):
    """城台 under a gate tower: a red body (or the room's base material) on a white stone base course with a white coping,
    arched tunnels through it front to back (one, three or five by its width, the middle one the widest; cross: 四面门,
    as many again side to side, crossing them - a 钟楼's), a paved floor in them, crenellated parapets round the top (a
    low plain one along the front and back, 垛口 on the sides), and a flight up the back on the west side. margin: the
    台基边距 asked for (chengtai_size). Returns the height of its top (the tower's floor)."""
    m, h = chengtai_size(pod, colon, margin)
    ex, ez = hx + m, hz + m
    top = y + h
    body = base or B("red_terracotta")
    stone = Pal.JADE
    for u in range(-ex, ex + 1):
        for v in range(-ez, ez + 1):
            for yy in range(y + 1, top + 1):
                f.put(u, yy, v, stone if (yy <= y + 2 or yy == top) and (abs(u) == ex or abs(v) == ez) else body)
            f.put(u, top, v, stone if (abs(u) == ex or abs(v) == ez) else B("polished_andesite"))
    _chengtai_tunnels(f, ex, ez, y, h, tunnels)
    if cross:
        _chengtai_tunnels(f.sub(0, 0, "east"), ez, ex, y, h, tunnels)
    # parapets: along the front and back a low wall, on the sides crenellated
    for u in range(-ex, ex + 1):
        for v in range(-ez, ez + 1):
            if abs(u) != ex and abs(v) != ez:
                continue
            f.put(u, top + 1, v, body)
            if abs(u) == ex and (v % 2 == 0):
                f.put(u, top + 2, v, body)
            f.put(u, top + 2 if not (abs(u) == ex and v % 2 == 0) else top + 3, v, slab("stone_brick"))
    # a low one: steps up to its back, the parapet open over them (a taller one gets a stair inside: chengtai_well)
    if h <= CHENGTAI_STEPS:
        flight = stairs_of(base, "minecraft:stone_brick_stairs")
        for u in (-ex + 2, -ex + 3):
            for k in range(h):
                vv = -ez - h + k
                f.put(u, y + 1 + k, vv, B(flight, facing="south", half="bottom"))
                for yy in range(y + 1, y + 1 + k):
                    f.put(u, yy, vv, base or stone)
            f.put(u, top + 1, -ez, AIR)
            f.put(u, top + 2, -ez, AIR)
            f.put(u, top + 3, -ez, AIR)
    return top


def _chengtai_tunnels(f, ex, ez, y, h, tunnels):
    """The tunnels through a 城台 (its top ex, ez out from the middle, h high) front to back: the middle one 5 wide (3
    on a small one), the others 3, spread across the front."""
    width = 2 * ex + 1
    count = 1 if width < 21 else 3 if width < 41 else 5
    if tunnels in TUNNELS:                               # 门洞 the player set (a 阙 or 雁翅楼 on the wings has none)
        count = TUNNELS[tunnels]
    mid_w = 5 if width >= 21 else 3
    centres = [0]
    gap = max(mid_w + 4, width // (count + 1))
    for i in range(1, count // 2 + 1):
        centres += [i * gap, -i * gap]
    th = h - 3                                           # tunnel height to the crown of the arch
    if th < 3 or count == 0:                             # too low a platform for a way through (or none asked for)
        centres = []
    for c in centres:
        w = mid_w if c == 0 else 3
        half = w // 2
        for v in range(-ez, ez + 1):
            for du in range(-half, half + 1):
                u = c + du
                f.put(u, y, v, B("stone_bricks"))       # the paved way through
                rise = th - (1 if abs(du) == half and half >= 1 else 0)   # a little arch: the sides a step lower
                for yy in range(y + 1, y + rise + 1):
                    f.put(u, yy, v, AIR)
            if abs(v) == ez or abs(v) == ez - 1:       # the arch's face: upside-down stairs at the shoulders
                for side in (-1, 1):
                    f.put(c + side * half, y + th, v, stair("stone_brick", "east" if side < 0 else "west", "top"))


CHENGTAI_STEPS = 4                                       # a 城台 up to this high has steps outside, a taller one a stair in it


def _walks_in(f, start, hx, hz, yb, m, neighbours=None):
    """Whether from start (u, y, v: where a 城台's stair comes up) a player walks along the top (floor yb) into the
    tower on it (frame +-hx, +-hz) through a door or an opening. neighbours: frames of rooms joined later (a wall
    running onto the 城台): their cells are not counted on."""
    def free(u, y, v):
        x, z = f.w(u, v)
        if any(fp[0] <= x <= fp[2] and fp[1] <= z <= fp[3] for fp, _ in neighbours or ()):
            return False
        b = f.get(u, y, v)
        return not arch.solid_at(f, u, y, v) or "_door" in (b or "") or "carpet" in (b or "")
    seen, todo = set(), [(start[0], start[2])]
    while todo:
        u, v = todo.pop()
        if (u, v) in seen or abs(u) > hx + m or abs(v) > hz + m:
            continue
        seen.add((u, v))
        if abs(u) < hx and abs(v) < hz:
            return True
        if not (free(u, yb + 1, v) and free(u, yb + 2, v)):
            continue
        todo += [(u + 1, v), (u - 1, v), (u, v + 1), (u, v - 1)]
    return False


def chengtai_well(f, hx, hz, y, pod, colon, base=None, neighbours=None, margin=None):
    """The stair up a tall 城台, cut into it once the tower on it is built (so it comes up where the top is free):
    from a doorway in the back face, a switchback inside, through a hole in the top floor beside the tower."""
    m, h = chengtai_size(pod, colon, margin)
    if h <= CHENGTAI_STEPS:
        return None
    (ax, az), (bx, bz) = f.w(-hx, -hz), f.w(hx, hz)
    own = (min(ax, bx), min(az, bz), max(ax, bx), max(az, bz))

    def dist(fp, x, z):
        return max(fp[0] - x, x - fp[2], 0) + max(fp[1] - z, z - fp[3], 0)

    def avoid(u, v, ground=False):                        # (单体建造) a neighbour's column, or nearer it than to this room:
        x, z = f.w(u, v)                                  # joinparts gives those cells to the neighbour
        return any(dist(fp, x, z) == 0 or (room and not ground and dist(fp, x, z) < dist(own, x, z)) for fp, room in neighbours or ())
    return arch.stair_well(f, y, y + h, (-hx - m + 1, hx + m - 1), -hz - m, stairs_of(base, "minecraft:stone_brick_stairs"),
                           base or B("stone_bricks"), avoid=avoid if neighbours else None)


def podium_height(pod, style, colon=0):
    """How high the floor stands above the ground: the platform height, for every style."""
    return max(0, pod)


def _part(f, hx, hz, y, wall_top):
    """A built part for joinparts: its scratch canvas, world footprint, ground y, wall top and top inside the footprint."""
    (ax, az), (bx, bz) = f.w(-hx, -hz), f.w(hx, hz)
    fp = (min(ax, bx), min(az, bz), max(ax, bx), max(az, bz))
    top = max([yy for (x, yy, z) in f.cv.blocks if fp[0] <= x <= fp[2] and fp[1] <= z <= fp[3]] or [wall_top])
    return dict(blocks=f.cv.blocks, cols=f.cv.cols, fp=fp, y=y, wall_top=wall_top, top=top)


def veranda(f, rng, hx, hz, yb, sh, W, keep_floor=False):
    """前廊: a row of posts in front of the facade under a lean-to roof, with a raised floor and a bench rail.
    keep_floor: where a platform is already under it, its top is the gallery's floor."""
    depth = 3 if hx < 7 else 4
    for u in range(-hx, hx + 1):
        for v in range(hz + 1, hz + depth + 1):
            if not (keep_floor and f.get(u, yb, v) is not None):
                f.put(u, yb, v, W["floor"])
    # posts on the wall's bay lines, counted from the middle (so the two halves mirror), none in line with the door;
    # the bench rail between them, never under a post
    bay = 3 if hx <= 5 else 4 if hx <= 9 else 5
    posts = {u for u in range(-hx, hx + 1) if abs(u) == hx or (u % bay == 0 and abs(u) > 2)}
    bt = max(yb + sh - 1, yb + 4)                   # the beam: over the door heads (floor + 4) even on a low storey
    for u in posts:
        for y_ in range(yb + 1, bt):
            f.put(u, y_, hz + depth, W["post"])
    for u in range(-hx, hx + 1):
        f.put(u, bt, hz + depth, W["beam"])
        if abs(u) > 2 and u not in posts:
            f.put(u, yb + 1, hz + depth, B(W["rail"], east=True, west=True))
    h = {}
    for u in range(-hx - 1, hx + 2):
        for v in range(hz + 1, hz + depth + 2):
            h[(u, v)] = bt + 1.8 - 0.5 * (v - hz - 1)
    raster_roof(f, h, lambda u, v, val: bt, min_y=yb + 4)   # (its lip and rafter ends no lower: it runs flat there)


# window cells of the storey being built: (u, v, storey y, row k, top row); house() rewrites them once it knows
# which room is behind each one (bedrooms get a high strip only, stores and seclusion rooms none)
WINDOWS = []
PRIVATE_ROOMS = {"bedroom", "bedroom_north", "maiden_room", "guest_room", "inner_disciple_room", "elder_quarters",
                 "cultivator_inn_room", "outer_dorm"}
CLOSED_ROOMS = {"storeroom", "treasury", "seclusion", "herb_store", "granary", "spirit_stone_vault", "auction_box"}


def pick_bay(rng, role, width, h, ctx, face="front"):
    """A wall bay part for a segment (role, width, height, front or not); one choice per kind of segment in a building
    (as TMA picks one design per layer/type/size), so a building stays of a piece. Returns (part, rows) or None."""
    key = (role, width, h, face == "front")
    if key in ctx["chosen"]:
        return ctx["chosen"][key]
    if not _parts_mod.BAYS:
        _parts_mod.reload_bays()
    style = ctx.get("style")
    if style:                                   # 单体建造 picked a style for this room: only its bays (any tier or wall)
        cands = [(e, rows) for e in _parts_mod.BAYS.values() if e.get("role") == role and e.get("style") == style
                 and (not e.get("faces") or face in e["faces"])
                 for rows in [_parts_mod.bay_rows(e, width, h)] if rows is not None]
        if cands:
            res = cands[0] if len(cands) == 1 else rng.choices(cands, weights=[c[0].get("weight", 1) for c in cands])[0]
            ctx["chosen"][key] = res
            return res
    cands = []
    for e in _parts_mod.BAYS.values():
        if e.get("role") != role:
            continue
        if e.get("tiers") and ctx.get("tier") not in e["tiers"]:
            continue
        if e.get("walls") and ctx.get("walls") not in e["walls"]:
            continue
        if e.get("faces") and face not in e["faces"]:
            continue
        rows = _parts_mod.bay_rows(e, width, h)
        if rows is not None:
            cands.append((e, rows))
    if not cands:
        res = None
    elif len(cands) == 1:
        res = cands[0]
    else:
        res = rng.choices(cands, weights=[c[0].get("weight", 1) for c in cands])[0]
    ctx["chosen"][key] = res
    return res


def draw_walls(f, rng, W, hx, hz, ys, sh, bay, s, n, spec, door_w, ctx):
    """The four walls of one storey. Posts and the wall plate are drawn here; everything between two posts comes from
    a wall bay part (data/parts/bays.json): the front door (and the doors out onto a balcony), shop fronts, windows,
    plain wall. Bays are counted from the middle of each wall outwards and the two halves mirror."""
    dw = 0 if door_w <= 1 else 1                    # door cells: |u| <= dw on the front wall
    no_win = spec.get("_no_window_cells", ())
    h = sh - 1
    ch = spec.get("_caihua") if spec.get("_caihua") in CAIHUA else None

    def paint(segs, at, y, keep=None):              # 彩画 on one row of the wall between the posts, bay by bay
        for i, seg in enumerate(segs):
            par = int(abs(i - (len(segs) - 1) / 2)) % 2
            for j, a in enumerate(seg):
                u, v = at(a)
                if keep is not None and f.get(u, y, v) not in keep:
                    continue
                blk = caihua_cell(ch, j, len(seg), par)
                if materials.present("minecraft:" + blk):
                    f.put(u, y, v, B(blk))

    faces = (("front", hx, lambda a: (a, hz), (0, 1)), ("back", hx, lambda a: (a, -hz), (0, -1)),
             ("west", hz, lambda a: (-hx, a), (-1, 0)), ("east", hz, lambda a: (hx, a), (1, 0)))
    for face, limit, at, (ou, ov) in faces:
        front = face == "front"
        # an upper storey with a balcony walk in front of it gets the same doors out onto it
        balc = front and s > 0 and spec["balcony"] and f.get(0, ys, hz + 1) not in (None, AIR)
        has_door = front and (balc or s == 0 and not spec.get("_no_front_door"))
        if face == "back" and s == 0 and (spec.get("_back_door") or spec.get("_through")):   # a gate house: through to the yard behind
            has_door = True
        if face in ("west", "east") and s == 0 and spec.get("_four"):   # 四面门: a square hall opens on all four sides
            has_door = True

        def is_post(a):
            if has_door and abs(a) <= dw:
                return False
            return abs(a) == limit or a % bay == 0 or (has_door and abs(a) == dw + 1)

        segs, cur = [], []
        for a in range(-limit, limit + 1):
            u, v = at(a)
            if is_post(a):
                for k in range(1, sh + 1):
                    f.put(u, ys + k, v, W["post"])
                if cur:
                    segs.append(cur)
                    cur = []
            else:
                f.put(u, ys + sh, v, W["beam"])
                cur.append(a)
        if cur:
            segs.append(cur)
        if ch:                                      # 彩画 on the wall plate (under the eaves it is hidden: the 额枋 below too)
            paint(segs, at, ys + sh)
        along_u = face in ("front", "back")
        out_dir = "south" if ov > 0 else "north" if ov < 0 else "east" if ou > 0 else "west"
        rail = B(W["rail"], **({"east": True, "west": True} if along_u else {"north": True, "south": True}))
        if spec.get("_open") or (spec.get("_open_front") and face == "front"):   # 敞开 (正面敞开: only the front): open between the posts, a low rail but at the way in
            for seg in segs:
                door = has_door and all(abs(a) <= dw for a in seg)
                for a in seg:
                    u, v = at(a)
                    for k in range(1, sh):
                        f.put(u, ys + k, v, AIR)
                    if not door:
                        f.put(u, ys + 1, v, rail)
            if ch:                                  # an open bay: the painted 额枋 spans it under the plate
                paint(segs, at, ys + sh - 1)
            continue
        for seg in segs:
            door = has_door and all(abs(a) <= dw for a in seg)
            if door and s == 0 and spec.get("_through") and face == "back":   # 穿堂: open up to the plate at the back; the
                # front keeps its doors (a gate house in a wall round a yard: open there, the yard was open to the street)
                for a in seg:
                    u, v = at(a)
                    for k in range(1, sh):
                        f.put(u, ys + k, v, AIR)
                continue
            cols = seg if (door or seg[0] >= 0) else seg[::-1]      # from the middle of the wall outwards
            m = len(seg)
            if door:
                role = "door"
            elif front and s == 0 and spec["shop"]:
                role = "shop"
            else:
                role = "plain"
                if m >= 2:                              # a bay of at least 3 (post to post) can have a window
                    b0 = (abs(cols[0]) // bay) * bay
                    if face == "back":
                        ok = (b0 // bay) % 2 == 0       # back wall: every other bay
                    elif face in ("west", "east"):
                        ok = b0 == 0                    # gables: only the middle bay
                    else:
                        ok = True
                    if ok:
                        role = "window"
            got = pick_bay(rng, role, m, h, ctx, face)
            if got and role == "window":                # behind the stairs, or into a room built against it
                wcols = {j for wall, _ in got[1] for j, c in enumerate(wall) if c == "W"}
                if any(at(cols[j]) in no_win for j in wcols):
                    got = None
            if got is None and role != "plain":
                got = pick_bay(rng, "plain", m, h, ctx, face)
            if got is None:
                for a in seg:
                    u, v = at(a)
                    for k in range(1, sh):
                        f.put(u, ys + k, v, W["fill"])
                continue
            part, rows = got
            key = part.get("key") or {}
            for j, a in enumerate(cols):
                u, v = at(a)
                wks = [k for k, (wall, _) in enumerate(rows, 1) if wall[j] == "W"]
                wtop = max(wks) if wks else 0
                first_door = True
                for k, (wall, out) in enumerate(rows, 1):
                    y = ys + k
                    c = wall[j]
                    if c == "F":
                        f.put(u, y, v, W["fill"])
                    elif c == "b":
                        f.put(u, y, v, W["base"])
                    elif c == "d":
                        f.put(u, y, v, W.get("dado") or W["fill"])
                    elif c == "B":
                        f.put(u, y, v, W["beam"])
                    elif c == "P":
                        f.put(u, y, v, W["post"])
                    elif c == "A":
                        f.put(u, y, v, AIR)
                    elif c == "W":
                        WINDOWS.append((u, v, ys, k, wtop))
                        f.put(u, y, v, B(W.get("pane", "white_stained_glass_pane"),
                                         **({"east": True, "west": True} if along_u else {"north": True, "south": True})))
                    elif c == "D":
                        f.put(u, y, v, B(W.get("door") or DOOR_OF.get(W["win"], "spruce_door"),
                                         facing={"front": "north", "back": "south", "west": "east", "east": "west"}[face],
                                         half="lower" if first_door else "upper", hinge="right" if a > 0 else "left",
                                         open=False, powered=False))
                        first_door = False
                    elif c == "S":
                        # a trapdoor window stands in the wall's plane on its inner face (an open trapdoor lies on the
                        # side away from where it faces), on every wall - not only the front
                        f.put(u, y, v, B(W["win"], facing=out_dir, half="bottom", open=True) if W["win"].endswith("trapdoor") else AIR)
                    elif c in key:                  # a block of the part's own, turned to face out of this wall
                        f.put(u, y, v, arch.rotate_state(key[c], out_dir))
                    o = out[j]
                    if o == "h" and W.get("trim_trapdoor") and f.get(u + ou, y, v + ov) is None and c != "D":   # a little hood
                        # (not beside a door's own cell: on a low storey that is head height in front of it)
                        f.put(u + ou, y, v + ov, B(W["trim_trapdoor"], facing=out_dir, half="top", open=False))
                    elif o == "v" and s == 0 and W.get("trim_slab") and f.get(u + ou, y - 1, v + ov) is None:   # sill
                        f.put(u + ou, y - 1, v + ov, B(W["trim_slab"], type="top"))
                    elif o in key and f.get(u + ou, y, v + ov) is None and not (c == "D" and "trapdoor" in str(key[o])):
                        f.put(u + ou, y, v + ov, arch.rotate_state(key[o], out_dir))   # (no board at head height by a door)
        if ch:                                      # the 额枋: the wall's top row under the plate, where it is wall
            paint(segs, at, ys + sh - 1, keep=(W["fill"], W["beam"]))

def eave_brackets(f, W, hx, hz, top_y, bay, levels=1):
    """斗拱 in miniature: an upside-down stair under the eave at every post, the solid side against the wall (one
    below the wall plate: the eave tiles come down to the plate right outside the wall). levels > 1 (单体建造 斗拱):
    that many rows instead, each a cell further out and one higher, solid wood behind each step (the eaves were
    lifted levels - 1 over the walls to make room)."""
    if not W.get("trim_stairs"):
        return
    top_y -= 1

    def one(u, v, du, dv, facing, side):
        if any("_door" in (f.get(u - du, top_y - k, v - dv) or "") for k in (1, 2)):
            return                                      # (a low storey: right over a door's head - none there)
        if levels > 1:
            _bracket_rows(f, W, u, v, du, dv, top_y, levels, facing)
        elif f.get(u, top_y, v) is None and not arch.place_ornament(f, "bracket", u, top_y, v, face=side, slots=W):
            f.put(u, top_y, v, B(W["trim_stairs"], facing=facing, half="top"))

    for u in range(-hx, hx + 1):
        if u % bay == 0 or abs(u) == hx:
            for v, facing, side in ((hz + 1, "north", "south"), (-hz - 1, "south", "north")):
                one(u, v, 0, 1 if v > 0 else -1, facing, side)
    for v in range(-hz + 1, hz):
        if v % bay == 0:
            for u, facing, side in ((hx + 1, "west", "east"), (-hx - 1, "east", "west")):
                one(u, v, 1 if u > 0 else -1, 0, facing, side)


def _bracket_rows(f, W, u, v, du, dv, y_top, levels, facing):
    """A taller bracket set at (u, v), the cell right outside the wall, y_top the row under the eaves: row j (1 =
    the lowest) steps out to j cells from the wall, solid wood behind the step; nothing out past the eaves."""
    wood = W["trim_stairs"].replace("_stairs", "_planks")
    wood = B(wood) if materials.present(wood if ":" in wood else "minecraft:" + wood) else W["beam"]
    for j in range(1, levels + 1):
        y = y_top - (levels - j)
        for o in range(j):
            cu, cv = u + du * o, v + dv * o
            if f.get(cu, y, cv) not in (None, AIR):
                continue                                # the eaves (or something else) already there
            if not any(f.get(cu, yy, cv) not in (None, AIR) for yy in range(y + 1, y + levels + 2)):
                break                                   # out past the eaves
            f.put(cu, y, cv, B(W["trim_stairs"], facing=facing, half="top") if o == j - 1 else wood)


def eave_fascia(f, W, hx, hz, top_y, reach=4, min_y=None):
    """封檐板: under the outermost row of eave tiles, a closed trapdoor of the trim wood at the top of the cell - not
    below min_y (a low eave over a door: the board would be at head height in front of it)."""
    if not W.get("trim_trapdoor"):
        return
    def lowest(u, v):
        for yy in range(top_y - 1, top_y + 12):
            st = f.get(u, yy, v)
            if st and st != AIR:
                return yy
        return None
    for u in range(-hx - reach, hx + reach + 1):
        for v in range(-hz - reach, hz + reach + 1):
            if abs(u) <= hx and abs(v) <= hz:
                continue
            lo = lowest(u, v)
            if lo is None:
                continue
            du = (1 if u > hx else -1 if u < -hx else 0)
            dv = (1 if v > hz else -1 if v < -hz else 0)
            if lowest(u + du, v + dv) is not None:     # not the outermost row
                continue
            if f.get(u, lo - 1, v) is None and (min_y is None or lo - 1 >= min_y):
                out = "south" if dv > 0 else "north" if dv < 0 else "east" if du > 0 else "west"
                f.put(u, lo - 1, v, B(W["trim_trapdoor"], facing=out, half="top", open=False))


def horsehead_walls(f, hx, hz, top_y, ry, W, sides=(-1, 1)):
    """徽派马头墙: the side walls rise above the roof in steps, each step capped with a little tile ridge. sides: the
    gables that get one (not one a taller room is built against: a cell out from the gable it stood in that room - a
    厢's stood in the main block, over the doorway between them)."""
    steps = 3
    for sgn in sides:
        u = sgn * (hx + 1)
        for v in range(-hz - 1, hz + 2):
            t = abs(v) / (hz + 1)
            level = steps - int(t * steps)
            hgt = top_y + 2 + level * max(2, (ry - top_y) // steps)
            for y in range(top_y - 1, hgt + 1):
                f.put(u, y, v, B("white_concrete"))
            f.put(u, hgt + 1, v, B(ROOF["full"]))
            f.put(u + 1, hgt + 1, v, stair(ROOF["tile"], "west"))
            f.put(u - 1, hgt + 1, v, stair(ROOF["tile"], "east"))
            f.put(u, hgt + 2, v, slab(ROOF["tile"]))


# ------------------------------------------------------------------------------------------------ decor

def decorate(f, rng, spec, hx, hz, yb, n, sh, W, tier="mortal"):
    import decor as DC
    for d in spec.get("decor", ()):
        fn = DC.ALL.get(DECOR_ALIAS.get(d, d)) or DECOR.get(d)
        if fn:
            fn(f, rng, hx, hz, yb, n, sh, W)
    # a couple of extras by tier, so neighbours of one kind do not look the same
    if not spec.get("_wing") and not spec.get("_no_extra_decor"):
        pool = DC.BY_TIER.get(tier, DC.BY_TIER["mortal"])
        for name in rng.sample(pool, k=min(len(pool), rng.randint(1, 3))):
            DC.ALL[name](f, rng, hx, hz, yb, n, sh, W)
    # every door gets lanterns or a plaque by chance
    if rng.random() < 0.6:                    # a lantern on a post either side of the door
        for s in (-2, 2):
            if f.get(s, yb + 1, hz + 1) is None:
                put_lamp(f, "door", s, yb, hz + 1, slots=dict(rail=W.get("rail") or "spruce_fence"))
    if rng.random() < 0.5 and sh >= 5:              # (a storey of 4: the board would be right over the door's head)
        for u in (-1, 0, 1):
            f.put(u, yb + 5 if sh >= 6 else yb + sh - 1, hz + 1, B("dark_oak_planks") if abs(u) == 1 else B("gold_block"))


def d_lanterns(f, rng, hx, hz, yb, n, sh, W):
    for u in (-hx, hx):                     # a lantern post at each front corner, on the ground
        if f.get(u, yb + 1, hz + 2) is None:
            put_lamp(f, "corner", u, yb, hz + 2, slots=dict(rail=W.get("rail") or "spruce_fence"))


def d_signpole(f, rng, hx, hz, yb, n, sh, W):
    u = hx + 2
    for k in range(1, 8):
        f.put(u, yb + k, hz + 1, B("spruce_fence"))
    f.put(u, yb + 8, hz + 1, B("spruce_fence"))
    for k in (5, 6, 7):
        f.put(u, yb + k, hz + 2, B(rng.choice(["red", "white", "yellow", "blue"]) + "_wool"))


def d_woodpile(f, rng, hx, hz, yb, n, sh, W):
    for v in range(-hz, -hz + 4):
        for k in (1, 2):
            f.put(hx + 1, yb + k, v, B("spruce_log", axis="z"))


def d_vat(f, rng, hx, hz, yb, n, sh, W):
    f.put(-hx - 1, yb + 1, hz, B("water_cauldron", level=3))


def d_fence_yard(f, rng, hx, hz, yb, n, sh, W):
    for u in range(-hx - 3, hx + 4):
        for v in (hz + 6,):
            if abs(u) > 1:
                f.put(u, yb + 1, v, B("oak_fence", east=True, west=True))
    for v in range(-hz, hz + 7):
        for u in (-hx - 3, hx + 3):
            f.put(u, yb + 1, v, B("oak_fence", north=True, south=True))


def d_drying(f, rng, hx, hz, yb, n, sh, W):
    for u in range(-2, 3):
        f.put(u, yb + 1, hz + 4, B("hay_block") if u % 2 else B("spruce_fence"))


def d_chimney(f, rng, hx, hz, yb, n, sh, W):
    u, v = hx - 1, -hz + 1
    for k in range(1, n * sh + 9):
        f.put(u, yb + k, v, B("bricks"))
    f.put(u, yb + n * sh + 9, v, B("campfire", lit=True))


def d_well(f, rng, hx, hz, yb, n, sh, W):
    FU.p_well(FU._Lift(f.sub(-hx - 5, hz + 2, "south"), yb), rng)


def d_lions(f, rng, hx, hz, yb, n, sh, W):
    for s in (-1, 1):
        u = s * (max(3, hx // 2))
        f.put(u, yb + 1, hz + 3, B("polished_andesite"))
        f.put(u, yb + 2, hz + 3, B("chiseled_stone_bricks"))
        f.put(u, yb + 3, hz + 3, stair("stone_brick", "north"))


def d_incense(f, rng, hx, hz, yb, n, sh, W):
    FU.p_small_furnace(FU._Lift(f.sub(0, hz + 5, "south"), yb), rng)
    f.put(0, yb + 1, hz + 5, B("polished_blackstone"))
    f.put(0, yb + 2, hz + 5, B("campfire", lit=True))


def d_plants(f, rng, hx, hz, yb, n, sh, W):
    for u in (-hx - 1, hx + 1):
        for v in (hz - 1, hz + 1):
            FU.p_plant(FU._Lift(f.sub(u, v, "south"), yb), rng)


def d_herb_racks(f, rng, hx, hz, yb, n, sh, W):
    FU.p_herb_rack(FU._Lift(f.sub(-hx, hz + 3, "south"), yb), rng)


def d_crates_out(f, rng, hx, hz, yb, n, sh, W):
    FU.p_crates(FU._Lift(f.sub(hx - 1, hz + 2, "south"), yb), rng)


def d_anvil_out(f, rng, hx, hz, yb, n, sh, W):
    f.put(hx - 2, yb + 1, hz + 2, B("anvil", facing="east"))
    f.put(hx - 3, yb + 1, hz + 2, B("water_cauldron", level=3))


def d_spirit_field(f, rng, hx, hz, yb, n, sh, W):
    import xianxia as X
    X.spirit_field(f, -hx, hz + 3, hx, hz + 14, yb, seed=rng.randint(0, 99))


def d_jars(f, rng, hx, hz, yb, n, sh, W):
    for u in range(-hx, -hx + 4):
        f.put(u, yb + 1, hz + 2, B("decorated_pot"))


def d_beacon_top(f, rng, hx, hz, yb, n, sh, W):
    pass


DECOR_ALIAS = {"mill_stone": "millstone", "hunter_rack": "drying_racks", "nets": "nets", "dye_racks": "drying_racks",
               "flags": "prayer_flags", "drum": "drum_stones", "plaque": "plaque", "bonsai_out": "bonsai_row",
               "watch_corners": "watch_corners", "lions": "lions", "waterwheel": "waterwheel", "sacks_out": "cart",
               "drying_rack": "drying_racks"}

DECOR = {"lanterns": d_lanterns, "signpole": d_signpole, "woodpile": d_woodpile, "vat": d_vat, "fence_yard": d_fence_yard,
         "drying_rack": d_drying, "chimney": d_chimney, "well": d_well, "lions": d_lions, "incense_burner": d_incense,
         "plants": d_plants, "herb_racks": d_herb_racks, "crates_out": d_crates_out, "anvil_out": d_anvil_out,
         "spirit_field": d_spirit_field, "jars": d_jars, "beacon_top": d_beacon_top, "mill_stone": d_drying,
         "hunter_rack": d_drying, "nets": d_drying, "sacks_out": d_crates_out, "dye_racks": d_drying, "stable_out": d_fence_yard,
         "flags": d_signpole, "drum": d_lions, "plaque": d_lanterns, "bonsai_out": d_plants, "pagoda_small": d_plants,
         "watch_corners": d_lanterns}


# ------------------------------------------------------------------------------------------------ other archetypes

def tower_kind(f, y, rng, spec, tier):
    """Multi-storey 楼/塔: storeys shrink every one or two levels, balcony + skirt roof each storey, pyramid or
    hip-gable top; one room per storey with a stair along the wall."""
    k = TIER_K[tier]
    tw_name = pick(rng, spec["walls"])
    W = set_walls(tw_name)
    bay_ctx = dict(chosen={}, tier=tier, walls=tw_name)
    WALL_FILL[0] = W["fill"]
    roof_theme(pick(rng, spec["themes"]))
    w = odd(rng.randint(*spec["size"][0]) * (1 + (k - 1) * 0.7))
    n = rng.randint(*spec["storeys"]) + (1 if k > 2 else 0)
    sh = int(round(rng.randint(*spec["sh"]) * (1 + (k - 1) * 0.3)))
    hx = w // 2
    form = pick(rng, spec["roofs"])
    xumizuo(f, hx + 3, hx + 3, y + 2, 2)
    yb = y + 2
    well = set()                            # where the stairs from below come up through this floor
    for s in range(n):
        ys = yb
        for u in range(-hx, hx + 1):
            for v in range(-hx, hx + 1):
                if (u, v) not in well:
                    f.put(u, ys, v, W["floor"])
        draw_walls(f, rng, W, hx, hx, ys, sh, 3 if hx < 6 else 4, s, n, spec, 1, bay_ctx)
        # spiral-ish stair along the inner walls
        next_well = set()
        if s < n - 1:
            cells = []
            # the storey above is one narrower every second storey: keep the stairs one further in, so they do not
            # come up inside its wall
            ins = 2 if (s % 2 == 1 and hx > 3) else 1
            run = max(1, 2 * (hx - ins) - 1)
            for i in range(sh):
                side = (i // run) % 4
                t = i % run
                a = hx - ins
                uu, vv, face = [(-a + t, -a, "east"), (a, -a + t, "south"), (a - t, a, "west"), (-a, a - t, "north")][side]
                f.put(uu, ys + 1 + i, vv, B(W["inner_stairs"], facing=face, half="bottom"))
                for kk in range(ys + 2 + i, ys + 5 + i):
                    if kk >= ys + sh:
                        f.put(uu, kk, vv, AIR)
                        next_well.add((uu, vv))     # the floor above stays open here (it used to close the stairs)
                cells.append((uu, vv))
        else:
            cells = []
        well = next_well
        rt = (spec["ground"] if s == 0 else spec["upper"])
        FU.furnish(f, rng, rt[s % len(rt)], -hx + 2, -hx + 2, hx - 2, hx - 2, ys, doors=cells + [(0, hx - 1)], sh=sh)
        light_room(f, -hx + 1, -hx + 1, hx - 1, hx - 1, ys, sh, set(cells), mirror=True)
        top = ys + sh
        if s < n - 1:
            for u in range(-hx - 2, hx + 3):
                for v in range(-hx - 2, hx + 3):
                    if abs(u) > hx or abs(v) > hx:
                        f.put(u, top, v, W["beam"])
                        if abs(u) == hx + 2 or abs(v) == hx + 2:
                            f.put(u, top + 1, v, B(W["rail"], **({"east": True, "west": True} if abs(v) == hx + 2 else {"north": True, "south": True})))
            # the skirt roof starts level with the balcony floor, so it hangs on the balcony instead of floating below it
            h = roof_surface(hx + 4, hx + 4, top + 1, 2, 2, 1.5, None, (2, 1) if hx >= 5 else (1,), (1, 0), inner=(hx + 2, hx + 2))
            raster_roof(f, h, lambda u, v, val: top - 1)
            if s % 2 == 1 and hx > 3:
                hx -= 1
        yb = top
    for u in range(-hx, hx + 1):
        for v in range(-hx, hx + 1):
            f.put(u, yb, v, B("spruce_planks"))
    ry = roof_for(f, hx, hx, yb, form, rng, overhang=2 + (1 if k > 1.2 else 0), steep=0.6, tier_k=k)
    if "beacon_top" in spec.get("decor", ()):
        f.put(0, ry + 8, 0, B("beacon"))
    return dict(top=ry, hx=hx)


def stilt_kind(f, y, rng, spec, tier):
    """吊脚楼 / 悬空崖殿 / 渔棚: a house raised on posts with braces and a ladder."""
    lift = rng.randint(4, 8)
    k = TIER_K[tier]
    w = odd(rng.randint(*spec["size"][0]) * (1 + (k - 1) * 0.6))
    d = odd(rng.randint(*spec["size"][1]) * (1 + (k - 1) * 0.6))
    hx, hz = w // 2, d // 2
    for u in range(-hx, hx + 1, 3):
        for v in range(-hz, hz + 1, 3):
            for yy in range(y + 1, y + lift + 1):
                f.put(u, yy, v, B("stripped_spruce_log", axis="y"))
            f.put(u, y + lift - 1, v + 1 if v < hz else v - 1, stair("spruce", "north" if v < hz else "south", "top"))
    for k2 in range(lift):
        f.put(hx + 1, y + 1 + k2, hz + 1 - k2 if hz + 1 - k2 > -hz else -hz, stair("spruce", "north"))
    return house(f, y + lift, rng, spec, tier, hx=hx, hz=hz)


def cave_kind(f, y, rng, spec, tier):
    """窑洞 / 洞府: an earth or rock mound with arched openings; rooms carved inside, furnished."""
    k = TIER_K[tier]
    W = set_walls(pick(rng, spec["walls"]))
    w = odd(rng.randint(*spec["size"][0]) * (1 + (k - 1) * 0.6))
    d = odd(rng.randint(*spec["size"][1]) * (1 + (k - 1) * 0.6))
    hx, hz = w // 2, d // 2
    mound_h = int(8 * (1 + (k - 1) * 0.6))
    rock = B("packed_mud") if spec is K["cave_dwelling"] else B("stone")
    top = B("grass_block")
    for u in range(-hx - 6, hx + 7):
        for v in range(-hz - 6, hz + 2):
            dist = max(0, abs(u) - hx, -hz - v)
            hgt = mound_h + 2 - dist
            if hgt <= 0:
                continue
            for yy in range(y + 1, y + hgt):
                f.put(u, yy, v, rock)
            f.put(u, y + hgt, v, top)
    n_arch = 3 if hx >= 5 else 1
    xs = [(-hx // 2) * (n_arch == 3), 0, (hx // 2) * (n_arch == 3)] if n_arch == 3 else [0]
    for i, cx in enumerate(xs):
        rw = 2 if n_arch == 3 else hx - 1
        # carve the room behind
        for u in range(cx - rw, cx + rw + 1):
            for v in range(-hz + 1, hz + 2):
                for kk in range(1, 5 + (1 if k > 1 else 0)):
                    arc = kk <= 3 or abs(u - cx) < rw
                    if arc:
                        f.put(u, y + kk, v, AIR)
                f.put(u, y, v, W["floor"])
        # facade: arched frame and lattice
        for u in range(cx - rw - 1, cx + rw + 2):
            f.put(u, y + 5, hz + 1, W["base"])
        for kk in range(1, 5):
            f.put(cx - rw - 1, y + kk, hz + 1, W["post"])
            f.put(cx + rw + 1, y + kk, hz + 1, W["post"])
        for u in range(cx - rw, cx + rw + 1):
            if u != cx:
                f.put(u, y + 3, hz + 1, B("spruce_trapdoor", facing="south", half="bottom", open=True))
        room = spec["ground"][i % len(spec["ground"])]
        FU.furnish(f, rng, room, cx - rw, -hz + 1, cx + rw, hz, y, doors=[(cx, hz)])
        f.put(cx, y + 2, 0, light_block())
    if k > 1.2:   # cultivator cave: carved gate, steles and lamps
        for s in (-1, 1):
            lantern_post(f, s * (hx + 2), y, hz + 3)
    return dict(top=y + mound_h, hx=hx + 6, hz=hz + 6)


ROUND_ROOFS = ("cone", "helmet", "dome", "luding", "flat")   # 圆塔的屋顶: 攒尖, 盔顶, 圆顶, 盝顶, 平顶 (垛口)


def round_radii(R, n, taper):
    """圆塔 收分: the radius of each storey, set in by taper a storey (never under 2 - as compose's storeys has it)."""
    return [max(min(R, 2), R - s * taper) for s in range(n)]


def round_room(f, y, rng, spec, tier, R, n, sh=None):
    """圆塔 (单体建造's round room, TMA's Cylinder): a round tower of n storeys and radius R standing on a low plinth,
    posts all round, windows between them, the door at the front (+v), a skirt roof round every storey but the last,
    a round 攒尖 roof with a finial on top, a ladder up the back wall from storey to storey.
    收分 (the room's taper): every storey up a circle set in that much, its skirt at its own wall and on up over the ring
    to the wall above (祈年殿's eaves stepping in). 平座: round every upper storey at its floor a railed walk on the eave
    under it, a door out onto it at the front (应县木塔)."""
    k = TIER_K[tier]
    walls_name = pick(rng, spec["walls"])
    W = set_walls(walls_name)
    WALL_FILL[0] = W["fill"]
    roof_theme(pick(rng, spec["themes"]))
    sh = sh or spec.get("_sh") or int(round(rng.randint(*spec["sh"]) * (1 + (k - 1) * 0.35)))
    # posts at every 45 degrees round the wall, windows half way between them, the door at the front
    def ang(c):
        return math.degrees(math.atan2(c[0], c[1])) % 360          # 0 = front (+v), 90 = +u
    def near(c, target, tol):
        d = abs((ang(c) - target + 180) % 360 - 180)
        return d <= tol

    def circle(Rc):                     # a storey's disc, its wall ring, the inside, the posts and their spacing
        rc = Rc + 0.5
        d = {(u, v) for u in range(-Rc, Rc + 1) for v in range(-Rc, Rc + 1) if math.hypot(u, v) <= rc}
        w = {c for c in d if any((c[0] + du, c[1] + dv) not in d for du, dv in ((1, 0), (-1, 0), (0, 1), (0, -1)))}
        st = 45 if Rc >= 4 else 90
        return d, w, d - w, {c for c in w if any(near(c, a, 360 / (8 * Rc)) for a in range(st // 2, 360, st))}, st
    taper = max(0, int(spec.get("_taper") or 0))
    pz = bool(spec.get("_pingzuo")) and n > 1                       # 平座 round the upper storeys
    radii = round_radii(R, n, taper)
    circles = {Rc: circle(Rc) for Rc in set(radii)}
    lad_c = (0, -radii[-1] + 1)
    rr = R + 0.5
    disc, wall, inside, posts, step = circles[R]

    def plat_edge(s):                   # 平座 of storey s: from its wall out to here (rail on the outer cells) - out to
        return max(radii[s - 1] + 0.5, radii[s] + 2.5) + 1     # the wall under it and a cell more, two cells of walk at least
    pod = spec["_pod"] if spec.get("_pod") is not None else 1     # 单体建造: the same platform setting as a house
    yb = y + pod
    no_front = bool(spec.get("_no_front_door"))                     # 单体建造: the front would open into another room
    side_doors = {tuple(c): lv for c, lv in (spec.get("_side_doors") or {}).items() if tuple(c) in wall}
    no_win = set(spec.get("_no_window_cells") or ())
    side_inner = {}
    for (u, v) in side_doors:                                        # the cell just inside each side door, kept free
        out = [(du, dv) for du, dv in ((1, 0), (-1, 0), (0, 1), (0, -1)) if (u + du, v + dv) not in disc]
        if out:
            side_inner[(u, v)] = (u - out[0][0], v - out[0][1])
    _DOOR_FACING = {(0, 1): "north", (0, -1): "south", (1, 0): "west", (-1, 0): "east"}
    if spec.get("_podium_shape") == "square" and pod > 0:          # 台基形状 方: a square 台 or 城台 under the round tower
        square_platform(f, R, y, pod, spec, W)
    else:                                                           # 台基: height, style, material, 坡度, 边距
        round_platform(f, R, y, pod, spec.get("_podium_style"), spec.get("_base"), no_front, spec.get("_step_slope"),
                       spec.get("_podium_margin"))
    # 最上一层 (as in house): the round roof's eave stood at the ceiling, three rows over where the skirts below end
    # (theirs droop from the floor line, it does not) - the top storey read taller. Its ceiling comes down that much.
    flat_top = (spec.get("_round_roof") or "cone") == "flat"            # (平顶: no eaves, its parapet - left as it was)
    want = max(0, 3 - max(0, 7 - sh)) if n > 1 and not flat_top and not spec.get("_extra_eaves", 1 if spec.get("_double_eave") else 0) else 0
    cut = min(want, max(0, sh - 5))
    droop = want - cut                  # (a low storey cannot come down all of it: the eave hangs the rest below the ceiling)
    for s in range(n):
        ys = yb + s * sh
        shs = sh - cut if s == n - 1 else sh
        Rs = radii[s]
        disc_s, wall_s, inside_s, posts_s, step_s = circles[Rs]
        # the ladders up, one over the other inside the top storey's back wall (set in: on a post in the storeys under it)
        lad_more = [(0, lad_c[1] - 1), (0, lad_c[1] + 1)] if Rs != radii[-1] else []
        for c in disc_s:
            if not (s > 0 and c == lad_c):
                f.put(c[0], ys, c[1], W["floor"])
        for c in wall_s:
            u, v = c
            out = [(du, dv) for du, dv in ((1, 0), (-1, 0), (0, 1), (0, -1)) if (u + du, v + dv) not in disc_s]
            along_u = any(dv for du, dv in out)                     # the wall runs along u here
            door = ((s == 0 and not no_front) or (pz and s > 0)) and near(c, 0, 12) and v > 0 and abs(u) <= (1 if Rs >= 5 else 0)
            facing = "north"
            if not door and side_doors.get(c, 0) > s:                # a door into the room next to it (单体建造)
                door, facing = True, _DOOR_FACING.get(out[0], "north") if len(out) == 1 else "north"
            win = (not door and c not in posts_s and c not in no_win
                   and any(near(c, a, 360 / (6 * Rs)) for a in range(0, 360, step_s)) and not near(c, 180, 20))
            for kk in range(1, shs + 1):
                yy = ys + kk
                if door and kk < shs:
                    if kk <= 2:
                        f.put(u, yy, v, B(W.get("door") or "spruce_door", facing=facing, half="lower" if kk == 1 else "upper",
                                         hinge="right" if u > 0 else "left", open=False, powered=False))
                    else:
                        f.put(u, yy, v, W["beam"] if kk == 3 else W["fill"])
                elif c in posts_s:
                    f.put(u, yy, v, W["post"])
                elif kk == shs:
                    f.put(u, yy, v, W["beam"])
                elif kk == 1:
                    f.put(u, yy, v, W["base"])
                elif win and 2 <= kk <= min(sh - 2, 4, shs - 1):   # (the windows of the storeys below)
                    f.put(u, yy, v, B(W.get("pane", "white_stained_glass_pane"),
                                     **({"east": True, "west": True} if along_u else {"north": True, "south": True})))
                else:
                    f.put(u, yy, v, W["fill"])
        for kk in range(1, sh + 1):                                  # ladder up the back wall, a hole in the floor above
            if s < n - 1:
                f.put(lad_c[0], ys + kk, lad_c[1], B(W["ladder"], facing="south"))
                if lad_more:                                         # (set in: on a post standing behind it)
                    f.put(0, ys + kk, lad_c[1] - 1, W["post"])
        if pz and s > 0:                # 平座: a walk round the storey at its floor, on the eave under it, a rail round it
            pe, rs_ = plat_edge(s), Rs + 0.5
            ring = {(u, v) for u in range(-int(pe) - 1, int(pe) + 2) for v in range(-int(pe) - 1, int(pe) + 2)
                    if rs_ < math.hypot(u, v) <= pe}
            below_wall = circles[radii[s - 1]][1]
            for c in ring:
                if c not in below_wall:                              # (the wall plate under it stays)
                    f.put(c[0], ys, c[1], W["beam"])
            rail = {c for c in ring if any((c[0] + du, c[1] + dv) not in ring and (c[0] + du, c[1] + dv) not in disc_s
                                           for du in (-1, 0, 1) for dv in (-1, 0, 1))}
            for (u, v) in rail:
                con = {d: True for d, (du, dv) in (("east", (1, 0)), ("west", (-1, 0)), ("south", (0, 1)), ("north", (0, -1)))
                       if (u + du, v + dv) in rail}
                f.put(u, ys + 1, v, B(W["rail"], **con))
        # furniture in the square that fits inside the round wall, by the use of this storey
        floors = spec.get("_floors")
        rt = (floors[min(s, len(floors) - 1)]["program"][0] if floors else
              (spec["ground"] if s == 0 else spec["upper"])[0])
        q = max(1, int((Rs - 1) / 1.42))
        here = [side_inner[c] for c, lv in side_doors.items() if lv > s and c in side_inner]
        if rt != EMPTY_ROOM:                                          # (空房: nothing; 内饰：无: drawn, not kept)
            FU.furnish(scratch(f) if spec.get("_bare") else f, rng, rt, -q, -q, q, q, ys,
                       doors=[(0, Rs - 1), (0, q), lad_c, (0, -q)] + lad_more + here, sh=shs)
            fixture = pick_light(rng, rt, tier)
            if not spec.get("_bare"):
                light_room(f, -Rs + 1, -Rs + 1, Rs - 1, Rs - 1, ys, shs, {lad_c, (0, Rs - 1)} | set(lad_more) | set(here),
                           fixture=fixture, mask=inside_s)
        if s < n - 1:                                                 # skirt roof round the storey
            top = ys + sh + max(0, 7 - sh)                            # (a low storey: lifted, its lowest tiles clear of the door)
            rs_ = Rs + 0.5
            h = {}
            if pz:                      # 平座 over it: the eave under the walk (flat there, its top a row under the walk), on
                pe = plat_edge(s + 1)   # out past it as far as a skirt reaches past a wall
                for u in range(-int(pe) - 4, int(pe) + 5):
                    for v in range(-int(pe) - 4, int(pe) + 5):
                        r = math.hypot(u, v)
                        if rs_ < r <= pe + 2.5:
                            h[(u, v)] = ys + sh - 0.5 - 0.6 * max(0.0, r - pe)
                raster_roof(f, h, lambda u, v, val: math.floor(val) - 1, min_y=min(ys + 4, ys + sh - 1))   # (the walk is laid
                continue                                                                                        # over it after)
            for u in range(-Rs - 3, Rs + 4):
                for v in range(-Rs - 3, Rs + 4):
                    r = math.hypot(u, v)
                    if rs_ < r <= rs_ + 2.5:
                        h[(u, v)] = top - 0.6 * (r - rs_)
            raster_roof(f, h, lambda u, v, val: math.floor(val) - 1, min_y=ys + 4)   # (its lip clear of the door heads)
            disc_n = circles[radii[s + 1]][0]
            up_ring = {c: top + 0.6 * (rs_ - math.hypot(*c)) for c in disc_s if c not in disc_n}   # (over its wall top too)
            if up_ring:                 # 收分: the skirt on up over the ring to the wall above, on this storey's ceiling
                raster_roof(f, up_ring, lambda u, v, val: ys + sh, rafters=False)
    Rl = radii[-1]                                                   # the top storey (set in: smaller)
    disc, wall, inside, posts, step = circles[Rl]
    rr = Rl + 0.5
    top_y = yb + n * sh - cut
    for _eave in range(spec.get("_extra_eaves", 1 if spec.get("_double_eave") else 0)):   # 重檐 / 三重檐: a lower eave round
        h = {}                                                                                 # the drum, the wall up a band
        up = max(0, 7 - sh)                                                                    # (clear of the door heads)
        if up:
            for c in wall:
                for kk in range(1, up + 1):
                    f.put(c[0], top_y + kk, c[1], W["post"] if c in posts else W["fill"])
            top_y += up
        for u in range(-Rl - 3, Rl + 4):
            for v in range(-Rl - 3, Rl + 4):
                r = math.hypot(u, v)
                if rr < r <= rr + 2.5:
                    h[(u, v)] = top_y - 0.6 * (r - rr)
        raster_roof(f, h, lambda u, v, val: math.floor(val) - 1, min_y=yb + (n - 1) * sh + 4)
        band = max(3, min(5, sh // 2 + 1))
        for c in wall:
            for kk in range(1, band + 1):
                f.put(c[0], top_y + kk, c[1], W["beam"] if kk == band else W["post"] if c in posts else W["fill"])
        top_y += band
    for c in inside:                                                 # ceiling
        f.put(c[0], top_y, c[1], B("spruce_planks"))
    form = spec.get("_round_roof") or "cone"
    if form == "flat":                  # 平顶: a terrace on top, a parapet round it with 垛口, no eaves
        par = spec.get("_base") or W["base"]
        for c in inside:
            f.put(c[0], top_y, c[1], W["floor"])
        for c in wall:
            f.put(c[0], top_y, c[1], par)
            f.put(c[0], top_y + 1, c[1], par)
            if (c[0] * 3 + c[1] * 5) % 4 < 2 or c == (0, Rl):          # merlons every other pair round
                f.put(c[0], top_y + 2, c[1], par)
        return dict(floor=yb, sh=sh, storeys=n, top=top_y + 3, hx=R, hz=R, wall_top=top_y, top_sh=sh - cut)
    ov = 2 + (1 if k > 1.2 else 0)
    if form == "dome":
        ov = 1                          # a dome sits close on the drum
    ext = rr + ov
    if pz:                              # (out over the top storey's 平座 too)
        ov = max(ov, math.ceil(plat_edge(n - 1) + 1.5 - rr))
        ext = max(ext, plat_edge(n - 1) + 1.5)
    rise = max(4, Rl * (0.9 + (PITCH.get(spec.get("_pitch", 0)) or spec.get("steep", 0.5)) * 0.4))
    if form == "dome":
        rise = max(4, rr * (0.8 + (PITCH.get(spec.get("_pitch", 0)) or 0.5) * 0.4))

    def prof(t):                        # height over the eave, 0 at the eave (t = 0) to 1 at the top (t = 1)
        if form == "helmet":            # 盔顶: a flared skirt (concave) up to half the height, then a bulging crown
            if t < 0.45:
                return 0.45 * (t / 0.45) ** 1.6
            return 0.45 + 0.55 * math.sqrt(max(0.0, 1 - ((1 - t) / 0.55) ** 2))
        if form == "dome":              # 圆顶: steep at the drum, rounding over
            return math.sqrt(max(0.0, 1 - (1 - t) ** 2))
        return t ** 1.3                 # 攒尖 (and 盝顶 below its flat): a little concave
    h = {}
    for u in range(-Rl - ov - 1, Rl + ov + 2):
        for v in range(-Rl - ov - 1, Rl + ov + 2):
            r = math.hypot(u, v)
            if r <= ext:
                h[(u, v)] = top_y + 1 + rise * prof(1 - r / ext)
    if form == "luding":                # 盝顶: cut off flat a little way up, a ridge round the flat
        flat = top_y + 1 + max(2, round(rise * 0.45))
        h = {kk: min(vv, flat) for kk, vv in h.items()}
    def eave_lo(val):                   # the roof's lower edge outside the drum: at the ceiling (minus the droop);
        if form == "dome":              # 圆顶 rises steep off the drum - its rim (as high as R) came down to the ceiling
            return top_y - droop        # only on a small tower, the top storey read up to 3 rows taller: a full rim
        return max(top_y - droop, math.floor(val) - 2)
    if spec.get("_cap"):                # 叠放: cut flat where the room stacked on it stands, a railed terrace round it
        h = capped(h, spec["_cap"])
        raster_roof(f, h, lambda u, v, val: top_y if (u, v) in disc else eave_lo(val))
        flat = cap_roof(f, h, spec["_cap"], W["rail"])
        return dict(floor=yb, sh=sh, storeys=n, top=flat + 2, hx=R, hz=R, wall_top=top_y, top_sh=sh - cut, cap_y=flat)
    raster_roof(f, h, lambda u, v, val: top_y if (u, v) in disc else eave_lo(val))
    ry = math.floor(max(h.values()))
    if form == "luding":
        cells = {kk for kk, vv in h.items() if vv >= flat}
        for (u, v) in cells:                # the flat tiled over, a ridge round its edge
            edge = any((u + du, v + dv) not in cells for du, dv in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            f.put(u, ry, v, B(ROOF["ridge"]) if edge else B(ROOF["full"]))
            if edge:
                f.put(u, ry + 1, v, slab(ROOF["tile"]))
        return dict(floor=yb, sh=sh, storeys=n, top=ry + 3, hx=R, hz=R, wall_top=top_y, top_sh=sh - cut)
    finial(f, ry, 4 if k < 1.5 else 7)
    return dict(floor=yb, sh=sh, storeys=n, top=ry + 8, hx=R, hz=R, wall_top=top_y, top_sh=sh - cut)


def round_kind(f, y, rng, spec, tier):
    """土楼: a ring of storeys round a courtyard, rooms all round, ancestral hall in the middle."""
    W = set_walls(pick(rng, spec["walls"]))
    roof_theme(pick(rng, spec["themes"]))
    R = rng.randint(*spec["size"][0]) // 2
    ring = 7
    n = rng.randint(*spec["storeys"])
    sh = 5
    for s in range(n):
        ys = y + s * sh
        for u in range(-R, R + 1):
            for v in range(-R, R + 1):
                r = math.hypot(u, v)
                if R - ring <= r <= R:
                    f.put(u, ys, v, W["floor"] if s else B("packed_mud"))
                    outer = r > R - 1
                    inner = r < R - ring + 1
                    for kk in range(1, sh):
                        if outer:
                            win = s >= 1 and kk == 2 and (int(math.degrees(math.atan2(v, u))) % 15 == 0)
                            f.put(u, ys + kk, v, B("iron_bars") if win else W["fill"] if s or kk > 1 else W["base"])
                        elif inner:
                            f.put(u, ys + kk, v, B("spruce_fence") if kk == 1 and s > 0 else (B("spruce_planks") if kk == sh - 1 else AIR))
                        elif int(math.degrees(math.atan2(v, u))) % 20 == 0 and r > R - ring + 2:
                            f.put(u, ys + kk, v, B("spruce_planks"))     # radial partitions
                    if R - ring + 2 < r < R - 1 and s >= 0 and rng.random() < 0.02:
                        FU.p_chest(FU._Lift(f.sub(u, v, "south"), ys), rng)
    # roof ring
    h = {}
    for u in range(-R - 3, R + 4):
        for v in range(-R - 3, R + 4):
            r = math.hypot(u, v)
            mid = R - ring / 2
            if R - ring - 2 <= r <= R + 3:
                h[(u, v)] = y + n * sh + 4 - 0.55 * abs(r - mid)
    raster_roof(f, h, lambda u, v, val: y + n * sh)
    # gate and inner hall
    for kk in range(1, 5):
        for u in (-1, 0, 1):
            f.put(u, y + kk, R, AIR)
            f.put(u, y + kk, R - 1, AIR)
    # the 祠堂 in the middle: all of it in the courtyard (a big one, or its side wing, stood out under the gallery round
    # it - doors and eaves under the gallery's low ceiling); no wing, as big as the courtyard takes with its eaves
    ri = R - ring
    spec_h = dict(K["ancestral_hall"], _wing=True)
    hw, hd = (odd(rng.randint(*sz)) // 2 for sz in spec_h["size"])
    while max(hw, hd) > 2 and math.hypot(hw, hd + 2) > ri - 3.5:
        hw, hd = max(2, hw - 1), max(2, hd - 1)
    house(f.sub(0, -2), y, rng, spec_h, "mortal", hx=hw, hz=hd)
    return dict(top=y + n * sh + 5, hx=R + 3, hz=R + 3)


def pavilion_kind(f, y, rng, spec, tier):
    """Open buildings: shrine, stage, bridge house."""
    k = TIER_K[tier]
    roof_theme(pick(rng, spec["themes"]))
    if spec is K["opera_stage"]:
        w = odd(rng.randint(*spec["size"][0]))
        d = odd(rng.randint(*spec["size"][1]))
        hx, hz = w // 2, d // 2
        for u in range(-hx, hx + 1):
            for v in range(-hz, hz + 1):
                for yy in range(y + 1, y + 5):
                    f.put(u, yy, v, B("stone_bricks") if yy < y + 4 else B("spruce_planks"))
        for u in (-hx, hx):
            for v in (-hz, hz):
                for yy in range(y + 5, y + 12):
                    f.put(u, yy, v, Pal.COL)
        for u in range(-hx, hx + 1):
            for yy in range(y + 5, y + 12):
                f.put(u, yy, -hz, B("red_wool") if yy < y + 11 else Pal.RED)
        FU.furnish(f, rng, "backstage", -hx + 1, -hz + 1, hx - 1, -hz + 3, y + 4, doors=[])
        return dict(top=roof_for(f, hx, hz, y + 12, pick(rng, spec["roofs"]), rng, 2, 0.5, tier_k=k))
    if spec is K["bridge_house"]:
        hx, hz = 2, odd(rng.randint(*spec["size"][1])) // 2
        for v in range(-hz - 3, hz + 4):
            arch_h = int(3 * math.cos(v / (hz + 3) * math.pi / 2))
            for u in range(-hx - 1, hx + 2):
                f.put(u, y + 1 + arch_h, v, B("stone_bricks") if abs(u) > hx else B("spruce_planks"))
                if abs(u) == hx + 1:
                    f.put(u, y + 2 + arch_h, v, B("spruce_fence", north=True, south=True))
        for v in range(-hz, hz + 1, 3):
            arch_h = int(3 * math.cos(v / (hz + 3) * math.pi / 2))
            for u in (-hx, hx):
                for yy in range(y + 2 + arch_h, y + 9):      # the posts stand on the arched deck, not above it
                    f.put(u, yy, v, Pal.COL)
        return dict(top=roof_for(f, hx, hz, y + 9, "xuanshan", rng, 1, 0.6, tier_k=k))
    # small shrine
    import arch as A
    return A.pavilion(f, y, size=odd(rng.randint(5, 7)), double=False)


def compound_kind(f, y, rng, spec, tier):
    """A walled compound: gate, main hall at the back, side halls, rear building; furnished from the kind's
    programme. Uses house() for every building with its own seed."""
    W = set_walls(pick(rng, spec["walls"]))
    roof_theme(pick(rng, spec["themes"]))
    k = TIER_K[tier]
    w = odd(rng.randint(*spec["size"][0]) * (1 + (k - 1) * 0.5))
    d = odd(rng.randint(*spec["size"][1]) * (1 + (k - 1) * 0.5))
    hx, hz = w // 2, d // 2
    arch.wall_ring(f, y, hx, hz, height=4 + int(k), gaps=[("front", -2, 2)])
    for u in range(-hx + 1, hx):
        for v in range(-hz + 1, hz):
            f.put(u, y, v, B("stone_bricks") if (u + v) % 4 else W["floor"])
    sub = dict(spec, size=((7, 11), (7, 9)), shop=False, balcony=False, decor=(), _no_extra_decor=True)
    main = dict(sub, ground=spec["ground"], storeys=(1, 2), podium=(1, 2), roofs=spec["roofs"])
    mhz = max(4, min(7, hz // 4))
    house(f.sub(0, -hz + mhz + 2), y, random.Random(rng.random()), main, tier, hx=max(4, min(hx - 3, int(hx * 0.7))), hz=mhz)
    for s in (-1, 1):
        side = dict(sub, ground=spec["ground"][1:] or spec["ground"], storeys=(1, 1 + (k > 1.2)), roofs=("yingshan", "xuanshan"))
        shz = max(3, min(5, hx // 5))
        house(f.sub(s * (hx - shz - 2), 2, "east" if s < 0 else "west"), y, random.Random(rng.random()), side, tier,
              hx=max(3, min(hz - mhz * 2 - 6, 9)), hz=shz)
    gate = dict(sub, ground=("guardroom",) if "guardroom" in str(spec) else ("stair_hall",), storeys=(1, 1), roofs=("xieshan", "yingshan"))
    gate["_back_door"] = True                     # 门屋: in at the front, out at the back into the yard
    house(f.sub(0, hz - 2), y, random.Random(rng.random()), gate, tier, hx=3, hz=2)
    for (u, v) in ((-hx // 3, hz // 3), (hx // 3, hz // 3)):
        (pine if rng.random() < 0.5 else round_tree)(f, u, y, v, 9 + rng.randint(0, 4), seed=rng.randint(0, 999))
    for d_ in spec.get("decor", ()):
        if d_ in DECOR:
            DECOR[d_](f, rng, 3, hz, y, 1, 6, W)
    return dict(top=y + 20, hx=hx, hz=hz)


def hall_kind(f, y, rng, spec, tier):
    """Ceremonial hall (arch.hall) scaled by tier, then its inside furnished as one big room."""
    k = TIER_K[tier]
    roof_theme(pick(rng, spec["themes"]))
    set_walls(pick(rng, spec["walls"]))
    base = rng.randint(*spec["size"][0])
    nb = 5 if base < 30 else 7 if base < 45 else 9
    bw = max(5, int(base * (1 + (k - 1) * 0.8) / nb))
    bays_u = [max(4, bw - 2)] + [bw] * (nb - 2) + [max(4, bw - 2)]
    bays_u[nb // 2] = bw + 2
    dv = 3 if base < 30 else 5
    bd = max(5, int(rng.randint(*spec["size"][1]) * (1 + (k - 1) * 0.8) / dv))
    bays_v = [max(4, bd - 2)] + [bd] * (dv - 2) + [max(4, bd - 2)]
    fujie = k >= 1.7 and rng.random() < 0.8
    col_low = int(min(bays_u[0], 8 + 4 * k))
    res = arch.hall(f, y, bays_u, bays_v, fujie=fujie, roof=pick(rng, spec["roofs"]), col_low=col_low,
                    col_up=col_low + int(10 + 6 * k) if fujie else None,
                    tiers=tuple((int(m * k), max(2, int(2 * k))) for m in (6, 3)[: 1 + (k > 1.3)]),
                    levels_low=2 + int(k), levels_up=3 + int(k), rafters_low=2 + int(k), rafters_up=3 + int(k))
    xs, zs = arch.positions(bays_u), arch.positions(bays_v)
    ix, iz = (xs[-2], zs[-2]) if fujie else (xs[-1], zs[-1])
    FU.furnish(f, rng, spec["ground"][0], -ix + 2, -iz + 2, ix - 2, iz - 2, res["floor"], doors=[(0, iz - 1)], sh=col_low)
    # light the hall where people walk: lamps hung low on long chains (宫灯) or standing, not set into a ceiling that
    # is a dozen blocks up (the old lanterns hung at column height gave the floor about light 3)
    light_room(f, -ix + 2, -iz + 2, ix - 2, iz - 2, res["floor"], col_low, {(0, iz - 2), (0, iz - 3)},
               fixture=pick_light(rng, spec["ground"][0], tier, mounts=("ceiling", "inlay", "floor")), mirror=True)
    return res


def gate_kind(f, y, rng, spec, tier):
    k = TIER_K[tier]
    roof_theme(pick(rng, spec["themes"]))
    set_walls(pick(rng, spec["walls"]))
    w = odd(rng.randint(*spec["size"][0]) * (1 + (k - 1) * 0.6))
    d = odd(rng.randint(*spec["size"][1]) * (1 + (k - 1) * 0.4))
    n_arch = 1 if w < 30 else 3
    nb = 5 if w < 35 else 7
    bw = max(5, (w - 10) // nb)
    return arch.gate_tower(f, y, w, d, int(10 + 5 * k), n_arch=n_arch, arch_w=5 + 2 * int(k), arch_h=7 + 3 * int(k),
                           hall_bays=tuple([bw] * nb), hall_depth=(max(4, d // 4),) * 3)


def paifang_kind(f, y, rng, spec, tier):
    k = TIER_K[tier]
    roof_theme(pick(rng, ("grey", "teal", "gold")))
    return arch.paifang(f, y, span=int(rng.randint(12, 18) * (1 + (k - 1) * 0.5)), h_in=int(14 * (1 + (k - 1) * 0.5)), h_out=int(10 * (1 + (k - 1) * 0.5)))


def star_kind(f, y, rng, spec, tier):
    import xianxia as X
    k = TIER_K[tier]
    X.star_altar(f, y, r=int(rng.randint(14, 20) * (1 + (k - 1) * 0.4)))
    return dict(top=y + 30)


def floating_kind(f, y, rng, spec, tier):
    """浮空阁: an inverted rock cone with hanging roots, a pavilion house on its flat top."""
    k = TIER_K[tier]
    lift = int(30 * k)
    R = int(rng.randint(10, 16) * (1 + (k - 1) * 0.4))
    yt = y + lift
    for u in range(-R, R + 1):
        for v in range(-R, R + 1):
            r = math.hypot(u, v) / R + 0.12 * math.sin(u * 0.7 + v * 0.4)
            if r > 1:
                continue
            depth = int(R * 1.5 * (1 - r) ** 1.3) + 2
            for yy in range(yt - depth, yt):
                f.put(u, yy, v, B("stone") if yy > yt - depth * 0.6 else B("deepslate"))
            f.put(u, yt, v, B("grass_block"))
            if rng.random() < 0.04:
                for kk in range(1, rng.randint(3, 9)):
                    f.put(u, yt - depth - kk, v, B("hanging_roots") if kk > 1 else B("rooted_dirt"))
    sub = dict(spec, size=((max(7, R - 3), max(7, R - 1)), (max(7, R - 3), max(7, R - 1))))
    res = house(f, yt, rng, sub, tier)
    for s in range(4):
        a = s * math.pi / 2 + 0.6
        pine(f, int(R * 0.75 * math.cos(a)), yt, int(R * 0.75 * math.sin(a)), 8 + s, seed=s)
    return res


def street_kind(f, y, rng, spec, tier):
    """坊市街: two rows of shops facing a paved street, a gate at each end."""
    k = TIER_K[tier]
    L = rng.randint(*spec["size"][0])
    street_w = 7
    for u in range(-L // 2, L // 2 + 1):
        for v in range(-street_w // 2, street_w // 2 + 1):
            f.put(u, y, v, B("polished_andesite") if abs(v) < street_w // 2 else B("stone_bricks"))
    # each row of shops from one 牌坊 to the other, the shops side by side an eave's reach apart (no side wing), each
    # set back from the street by its own depth. (They were 13 apart on a line 7 back: they grow with the tier - a 元婴
    # shop is 15-21 a side - and stood across the street into the row opposite, into each other and under the 牌坊's
    # roofs: doors against walls, eaves through the next shop at its doors' heads.)
    ov = 2 + (1 if k > 1.2 else 0)                       # (house's eaves: this far out from the walls)
    for side in (-1, 1):
        u = -L // 2 + 8
        while True:
            sp = dict(K["market_shop"], _no_wing=True)
            rs = random.Random(rng.random())
            hx = odd(rs.randint(*sp["size"][0]) * (1 + (k - 1) * 0.6)) // 2
            hz = odd(rs.randint(*sp["size"][1]) * (1 + (k - 1) * 0.6)) // 2
            if u + 2 * hx > L // 2 - 8:
                break
            house(f.sub(u + hx, side * (street_w // 2 + 3 + hz), "south" if side < 0 else "north"), y, rs, sp, tier, hx=hx, hz=hz)
            u += 2 * hx + 1 + ov
    for side in (-1, 1):
        arch.paifang(f.sub(side * (L // 2), 0, "east" if side < 0 else "west"), y, span=8, h_in=12, h_out=9)
    return dict(top=y + 30)


ARCHETYPES = {"house": house, "tower": tower_kind, "stilt": stilt_kind, "cave": cave_kind, "round": round_kind,
              "pavilion": pavilion_kind, "compound": compound_kind, "hall": hall_kind, "gate": gate_kind,
              "paifang": paifang_kind, "star": star_kind, "floating": floating_kind, "street": street_kind}


# small things that may stand in front of a door and are cleared out of the way (never walls)
_DOORWAY_JUNK = ("fence", "_wall", "potted_", "flower_pot", "decorated_pot", "hay_block", "barrel", "lantern", "candle",
                 "campfire", "chest", "cauldron", "composter", "crafting_table", "bookshelf", "grindstone", "anvil",
                 "lectern", "loom", "smoker", "furnace", "bell", "button", "chain", "sign", "banner", "bed", "leaves",
                 "trapdoor")                    # (an eave board or a hood of the next room's, at head height in front of a door)


_SOLID_MEMO = {}


def _solid_wall(st):
    if st in _SOLID_MEMO:
        return _SOLID_MEMO[st]
    r = _SOLID_MEMO[st] = _solid_wall_(st)
    return r


def _solid_wall_(st):
    return bool(st) and st != AIR and not any(t in st for t in (
        "lantern", "candle", "fence", "torch", "chain", "pane", "bars", "door", "potted", "flower", "banner", "sign",
        "trapdoor", "slab", "stairs", "carpet", "plate", "light[", "spirit_light", "button", "ladder", "leaves"))


_NOT_A_SILL = ("composter", "barrel", "chest", "cauldron", "bookshelf", "furnace", "smoker", "crafting_table", "loom",
               "lectern", "grindstone", "anvil", "hay_block", "beehive", "jukebox", "note_block", "_bed", "cartography",
               "fletching", "smithing", "stonecutter", "brewing_stand", "decorated_pot", "bell")


def _openings(cv):
    """Cells you walk through a wall at: door blocks, and gaps two or three high in a straight wall with something
    solid over them (doorways in partitions and between rooms). (cell, the way through: (dx, dz))."""
    B = cv.blocks
    out = []
    for (x, y, z), b in B.items():
        if "_door[" in b and "half=lower" in b and "trapdoor" not in b:
            f = b.split("facing=")[1].split(",")[0].split("]")[0]
            out.append(((x, y, z), (0, 1) if f in ("north", "south") else (1, 0)))
            continue
        if not _solid_wall(b) or any(t in b for t in _NOT_A_SILL):
            continue                                     # (a gap over a chest or a barrel in a row of cabinets: a niche)
        x1, y1 = x, y + 1
        if any(_solid_wall(B.get((x, y1 + k, z))) or (B.get((x, y1 + k, z)) or "").find("_door") >= 0 for k in (0, 1)):
            continue
        if not (_solid_wall(B.get((x, y1 + 2, z))) or _solid_wall(B.get((x, y1 + 3, z)))):
            continue
        for (dx, dz) in ((1, 0), (0, 1)):
            if all(_solid_wall(B.get((x + s * dx, y1 + k, z + s * dz))) for s in (1, -1) for k in (0, 1)):
                out.append(((x, y1, z), (dz, dx)))      # the wall runs along (dx, dz): the way through is across it
    return out


_OVER_SOFT = ("air", "light", "carpet", "pressure_plate", "_door", "button", "torch", "flower", "sapling", "grass[", "fern",
              "snow[", "rail", "lever", "sign", "banner", "vine", "tripwire", "lantern", "chain", "candle", "potted", "leaves")


def walkout(blocks, cols, ground, reach=4):
    """Low eaves at the ways in: from every door (and every open doorway two or three high in a wall), walking out
    either way - straight out or a cell to the side, up to `reach` cells, stepping down with the ground - whatever hangs
    two over your feet (an eave, a rafter end, a fascia board, a beam: as low as the door's own head), or one over them
    further out. The walk stops at the next door or passage through a wall (that one is a way of its own); from an open
    doorway only straight out (a 城台's tunnel vaults down at its sides). Below `ground` is the world's ground.
    Returns [(the way's foot cell, [the low cells])]."""
    def solid(c):
        s = blocks.get(c)
        if s is None:
            if c[1] <= ground:
                return True
            return any(y0 <= c[1] <= y1 and st != AIR for (y0, y1, st) in cols.get((c[0], c[2]), ()))
        return not any(t in s for t in _OVER_SOFT)

    ways = []
    for (x, y, z), b in blocks.items():
        if "_door[" in b and "half=lower" in b and "trapdoor" not in b:
            f = b.split("facing=")[1].split(",")[0].split("]")[0]
            ways.append(((x, y, z), (1, 0) if f in ("east", "west") else (0, 1), (0, 1, -1)))
    for (x, y, z), (dx, dz) in _openings(type("C", (), dict(blocks=blocks))()):
        if "_door" not in (blocks.get((x, y, z)) or "") and not solid((x, y, z)) and not solid((x, y + 1, z)) \
                and not any(t in (blocks.get((x, y + k, z)) or "") for t in ("pane", "bars") for k in (0, 1)):
            ways.append(((x, y, z), (dx, dz), (0,)))
    out = []
    for (x, y, z), ax, ks in ways:
        sx = (ax[1], ax[0])
        low = []
        for sgn in (1, -1):
            for k in ks:
                for d in range(1 if k == 0 else 2, reach + 1):     # (aside: from 2 out - a couplet, a lamp on the
                    cx, cz = x + sgn * d * ax[0] + k * sx[0], z + sgn * d * ax[1] + k * sx[1]   # wall by the door is no eave)
                    if solid((cx, y, cz)) or "_door" in (blocks.get((cx, y, cz)) or ""):
                        break                           # a wall, a step up, a rail, the next door: no way on there
                    s = y
                    while s > y - 4 and not solid((cx, s - 1, cz)):
                        s -= 1                          # (the ground steps down: where you stand there)
                    if solid((cx, s, cz)):
                        break
                    if solid((cx + sx[0], s, cz + sx[1])) and solid((cx - sx[0], s, cz - sx[1])):
                        break                           # through the next wall: a way of its own
                    if not solid((cx, s + 1, cz)) and solid((cx, s + 2, cz)):
                        low.append((cx, s + 2, cz))
                    elif solid((cx, s + 1, cz)):
                        if d >= 2 and ("slab" in (blocks.get((cx, s + 1, cz)) or "") or not solid((cx, s + 2, cz))):
                            low += [(cx, s + 1, cz)] + ([(cx, s + 2, cz)] if solid((cx, s + 2, cz)) else [])
                        else:
                            break                       # (a post, a wall: no way on there)
        if low:
            out.append(((x, y, z), low))
    return out


def clear_doorways(cv):
    """Every door gets a way through: whatever small thing stands right in front of or behind it (a pot on a pedestal,
    a fence, a hay bale, a lamp, furniture) at door height is taken away, with the pedestal it stands on. Run on the
    finished building (world coordinates). Returns how many cells were cleared."""
    n = 0
    for (x, y, z), (dx, dz) in _openings(cv):
        for sgn in (1, -1):
            cx, cz = x + sgn * dx, z + sgn * dz
            for k in (0, 1):
                c = cv.blocks.get((cx, y + k, cz))
                if not c or c == AIR or "carpet" in c or "pressure_plate" in c or "_door" in c:
                    continue
                above = cv.blocks.get((cx, y + k + 1, cz)) or ""
                junk = any(j in c for j in _DOORWAY_JUNK)
                pedestal = k == 0 and any(j in above for j in ("potted_", "flower_pot", "decorated_pot", "lantern", "candle"))
                if junk or pedestal:
                    cv.blocks[(cx, y + k, cz)] = AIR
                    n += 1
                    if pedestal or (k == 0 and any(j in above for j in _DOORWAY_JUNK)):
                        cv.blocks[(cx, y + k + 1, cz)] = AIR
                        n += 1
            door = cv.blocks.get((x, y, z)) or ""
            in_run = any(a0 <= y + 1 <= a1 and st != AIR for a0, a1, st in cv.cols.get((cx, cz), ()))   # (a platform drum)
            if "_door[" in door and "half=lower" in door and (_solid_wall(cv.blocks.get((cx, y + 1, cz))) or
                                                              (cv.blocks.get((cx, y + 1, cz)) is None and in_run)):   # still blocked at head
                # height by something that is not junk (a post, the next house's wall, a roof over a balcony door):
                along = (dz, dx)                              # the door goes - walled up like the wall beside it
                fill = next((cv.blocks.get((x + s * along[0], y + k, z + s * along[1])) for s in (1, -1) for k in (0, 1)
                             if _solid_wall(cv.blocks.get((x + s * along[0], y + k, z + s * along[1])))), B("stone_bricks"))
                cv.blocks[(x, y, z)] = fill
                if "_door[" in (cv.blocks.get((x, y + 1, z)) or ""):
                    cv.blocks[(x, y + 1, z)] = fill
                n += 1
            for yy in range(y + 1, y + 7):            # and whatever stood on what was taken (a lamp on a tall post):
                c = cv.blocks.get((cx, yy, cz)) or ""  # it would hang in the air
                if cv.blocks.get((cx, yy - 1, cz)) == AIR and "hanging=true" not in c and any(j in c for j in _DOORWAY_JUNK + ("torch",)):
                    cv.blocks[(cx, yy, cz)] = AIR
                    n += 1
    return n


_STANDS = ("lantern[hanging=false", "candle", "flower_pot", "potted_", "_button[face=floor")


def settle_loose(cv):
    """A lamp, candle or pot standing on nothing (what was under it was taken away later) comes down onto what is one
    lower, or goes. The game drops them at the first block update otherwise. Returns how many were moved or taken."""
    n = 0
    for (x, y, z), s in list(cv.blocks.items()):
        if not s or not any(t in s for t in _STANDS) or "hanging=true" in s:
            continue
        below = cv.blocks.get((x, y - 1, z))
        if below not in (None, AIR) or (x, z) in cv.cols and any(a <= y - 1 <= b for a, b, _ in cv.cols[(x, z)]):
            continue
        cv.blocks[(x, y, z)] = AIR
        if cv.blocks.get((x, y - 2, z)) not in (None, AIR):
            cv.blocks[(x, y - 1, z)] = s
        n += 1
    return n


def drop_floating(cv, ground, most=12, fall=4):
    """自检's floating pieces that are small (a statue whose plinth went, a campfire, a slab, a banner): each comes down as
    one piece onto what is under it if that is at most `fall` cells, else (when it is tiny) goes. Bigger pieces stay
    for a look. Returns (moved, removed)."""
    import selfcheck                                   # (lazy: selfcheck imports the daemon, which imports this module)
    loose = set(selfcheck.check(cv.blocks, ground, cv.cols).get("floating", []))
    moved = removed = 0
    while loose:
        seed = loose.pop()
        piece, todo = {seed}, [seed]
        while todo:
            x, y, z = todo.pop()
            for d in selfcheck.NB18:
                p = (x + d[0], y + d[1], z + d[2])
                if p in loose:
                    loose.discard(p)
                    piece.add(p)
                    todo.append(p)
        if len(piece) > most:
            continue
        blocks = {p: cv.blocks[p] for p in piece if p in cv.blocks}
        for k in range(1, fall + 1):
            if any((x, y - k, z) not in piece and cv.blocks.get((x, y - k, z)) not in (None, AIR) for (x, y, z) in blocks):
                break                                  # it would sink into something: it rests one higher
        else:
            k = fall + 1
        k -= 1
        rests = k > 0 and any(cv.blocks.get((x, y - k - 1, z)) not in (None, AIR) and (x, y - k - 1, z) not in piece for (x, y, z) in blocks)
        for p in blocks:
            cv.blocks[p] = AIR
        if rests:
            for (x, y, z), st in blocks.items():
                cv.blocks[(x, y - k, z)] = st
            moved += 1
        elif len(blocks) > 4:                          # (nothing to rest on near: put it back, it is for a look)
            cv.blocks.update(blocks)
        else:
            removed += 1
    return moved, removed


def build(f, y, kind_name, tier=None, seed=0):
    """Generate one building of a kind at a tier with a seed into frame f (origin = footprint centre, ground y)."""
    spec = K[kind_name]
    rng = random.Random(f"{kind_name}/{tier}/{seed}")
    tier = tier or spec["tiers"][0]
    arch.begin_ornaments(f"{kind_name}/{tier}/{seed}", tier)
    try:
        res = ARCHETYPES[spec["arch"]](f, y, rng, spec, tier)
        clear_doorways(f.cv)
        settle_loose(f.cv)
        if spec["arch"] != "floating":             # (a floating pavilion floats on purpose)
            drop_floating(f.cv, y)
        from compose import connect_panes          # glass panes joined to what is round them, the unjoinable ones whole glass
        connect_panes(f.cv.blocks)
        return res
    finally:
        roof_theme("grey")
        arch.style("red")
