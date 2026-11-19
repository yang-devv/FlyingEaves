"""宗门 v1: a cultivation sect laid out on a mountain, written as one 单体建造 design (its rooms: halls, towers, pavilions,
a 牌坊, and the ground itself as 台地 terraces, 街道 landings and 石阶 flights, each at its own y), so it loads into the
room table like 随机城镇 and every piece stays editable. Every building is rooms of the room table (storeys, roofs,
台基 styles, 重檐, 叠放 rooms set in a little, round rooms); nothing is a bespoke generator.

Layout (planned in a frame of its own: the mountain falls towards +v; turned at the end into the design's x, z):
- the top court on the summit, the courts below it one after another down the axis to the 山门 plaza at the foot
  (a 牌坊 at its front, the gate hall 山门殿 open through behind it);
- each court a terrace at its own level: its main hall at the back facing down the mountain, side halls down both
  sides facing in, a paved way and a 鼎 on the axis, a strip behind the hall (the way round it to the stair up) and one
  in front (where the stair from below comes up);
- between two courts a long stone stair (石阶 flights, a landing every ten to fourteen steps), the courts' retaining
  walls where it meets them;
- outer courts: 执事堂, 戒律堂, 弟子舍; middle: 传功殿, 藏经阁, 丹房, 器坊, 符堂, 阵堂; top: 宗门主殿, 祖师堂, 宝库;
- side terraces off the courts (dorm rows, workshops, 灵兽厩 and 灵田, elders' houses), each on its own level, reached
  by a path out of a court's front or back strip (flat where the ground lets it, stairs where it climbs);
- towers and pavilions (试炼塔, 剑阁, 亭) on spurs - the high points round the courts - each on a small terrace, a
  path out to it;
- 化神: several peaks, each with its own compound (a few courts down from its top), paths across the saddles to the
  main peak's courts.

The axis follows the mountain: each court sits where the ground across it is highest near the one above (the ridge).
Every building keeps its eaves, steps and platforms inside its court, and no two reach into each other.

sect(seed, tier, heights=None) -> design dict. tier: lianqi zhuji jindan yuanying huashen. heights: None (a mountain made
from the seed), a function (x, z) -> y in the design's coordinates, or {"R": R, "h": [...]} as the game samples it
round the crosshair (the design then carries world heights, "anchored").
python sect_plan.py SEED TIER [out.json]
"""
import json
import math
import random
import sys
from pathlib import Path

import paths
from siteplan import odd, room

GROUP = True       # 地块: the design's ground pieces gathered into a few rooms (compose.group_ground)
TIERS = ("lianqi", "zhuji", "jindan", "yuanying", "huashen")
TIER_CN = {"lianqi": "练气", "zhuji": "筑基", "jindan": "金丹", "yuanying": "元婴", "huashen": "化神"}

# one look per sect: hall walls, other walls, tiles (halls / others / the top hall), base, path stone, the roofs of its
# lesser buildings (weights), names
FAMILIES = [
    dict(cn="青云", walls=("palace",), others=("palace", "darkwood", "whitewall"), themes=("teal",), lesser=("grey", "teal"),
         top=("gold", "teal"), base="white", path=0, roofs={"xieshan": 4, "yingshan": 2, "juanpeng": 1}),
    dict(cn="玄都", walls=("darkwood",), others=("darkwood", "whitewall", "greybrick"), themes=("black", "grey"), lesser=("black", "grey"),
         top=("black", "teal"), base="greybrick", path=0, roofs={"yingshan": 3, "xieshan": 2, "xuanshan": 1}),
    dict(cn="剑冢", walls=("stone", "darkwood"), others=("stone", "darkwood", "log"), themes=("slate", "black"), lesser=("slate", "black"),
         top=("black",), base="blackstone", path=1, roofs={"xuanshan": 3, "yingshan": 2, "pyramid": 1}),
    dict(cn="丹霞", walls=("palace", "redbrick"), others=("redbrick", "palace", "wattle"), themes=("red", "grey"), lesser=("grey", "red"),
         top=("red", "gold"), base="sandstone", path=0, roofs={"xieshan": 3, "juanpeng": 2, "yingshan": 2}),
    dict(cn="瑶池", walls=("jade", "palace"), others=("palace", "whitewall", "jade"), themes=("jade", "teal"), lesser=("teal", "grey"),
         top=("gold", "jade"), base="white", path=0, roofs={"xieshan": 4, "luding": 1, "juanpeng": 1}),
    dict(cn="云台", walls=("palace", "darkwood"), others=("wattle", "darkwood", "whitewall"), themes=("grey", "teal"), lesser=("grey",),
         top=("teal", "gold"), base="rubble", path=1, roofs={"xuanshan": 3, "xieshan": 2, "yingshan": 1}),
    dict(cn="雪岭", walls=("stone", "greybrick"), others=("greybrick", "stone", "whitewall"), themes=("slate", "grey"), lesser=("slate", "grey"),
         top=("teal", "black"), base="white", path=1, roofs={"yingshan": 3, "xuanshan": 2, "luding": 1}),
    dict(cn="竹海", walls=("darkwood", "wattle"), others=("wattle", "log", "darkwood"), themes=("grey", "wood"), lesser=("thatch", "wood", "grey"),
         top=("teal", "grey"), base="rubble", path=3, roofs={"xuanshan": 4, "juanpeng": 2, "xieshan": 1}),
]
# halls of a higher tier: more of them under 歇山 and 重檐, with 斗拱 and 彩画, eaves lifted at the corners
TIER_FINISH = [dict(lift=(0, 0, 1), over=(1,), dbl=0.0), dict(lift=(0, 1, 1), over=(1, 1, 2), dbl=0.15),
               dict(lift=(1, 2), over=(1, 2), dbl=0.3), dict(lift=(1, 2, 3), over=(2,), dbl=0.45),
               dict(lift=(2, 3), over=(2, 3), dbl=0.6)]
LESSER = {"discipline", "alchemy", "talisman", "dorm", "inner_res", "elder", "refectory", "guard", "forge", "treasury"}
SECT_NAMES = ("青云", "太虚", "玄天", "紫霄", "凌霄", "天衍", "归元", "落霞", "玉清", "上清", "无极", "昆仑", "蜀山", "天剑", "丹鼎",
              "万法", "清微", "神霄", "合欢", "天机")

# ------------------------------------------------------------------------------------------------ the buildings

# what each kind of building is: its rooms (from the presets in data/library/O模板/修士, sized by tier)
# tpl(kind, ti, mat, rng) -> dict(w, d, rooms=[(inset across, inset deep, settings)]) in its own frame, front south


def _pick(rng, seq):
    return seq[rng.randrange(len(seq))]


def tpl(kind, ti, mat, rng):
    """A building of a kind for tier index ti (0 练气 .. 4 化神): its frame (w across the front, d deep, both odd) and
    its rooms: the first stands on the ground, the others are 叠放 on it, set in (a across, b deep) on each side."""
    W, O, T, L, TOP = mat["wall"], mat["other"], mat["theme"], mat["lesser"], mat["top"]
    base = mat["base"]

    def r(**kw):
        d = dict(storeys=1, roof="xieshan", walls=W, theme=T, base="auto", podium=0, podium_style="plain", open="closed",
                 uses="main_room", sh=0, overhang=1)
        d.update(kw)
        if isinstance(d["uses"], str):
            d["uses"] = [d["uses"]] * 8
        return d

    def sz(lo, hi):
        return odd(rng.randint(lo, hi))

    fin = TIER_FINISH[ti]
    if kind in LESSER:                                   # the sect's own roof mix, grander as the tier rises
        out = tpl0(kind, ti, mat, rng, r, sz)
        roofs = dict(mat.get("roofs") or {"xieshan": 1})
        if ti >= 3:
            roofs["xieshan"] = roofs.get("xieshan", 0) + 3
        keys = sorted(roofs)
        for a_, b_, s_ in out["rooms"]:
            if s_.get("roof") in (None, "xieshan", "yingshan", "xuanshan", "juanpeng") and not s_.get("stack") and rng.random() < 0.75:
                s_["roof"] = rng.choices(keys, weights=[roofs[k] for k in keys])[0]
            if s_.get("roof") in ("xieshan", "wudian", "luding"):
                s_["lift"] = rng.choice(fin["lift"])
            if ti >= 3 and rng.random() < 0.35:
                s_["theme"] = mat["theme"]
        return out
    out = tpl0(kind, ti, mat, rng, r, sz)
    if kind in BIG or kind in ("library", "bell", "teleport"):   # halls: the tier's finish
        for a_, b_, s_ in out["rooms"]:
            if s_.get("roof") in ("xieshan", "wudian", "pyramid"):
                s_["lift"] = rng.choice(fin["lift"])
                s_["overhang"] = rng.choice(fin["over"])
            if len(out["rooms"]) == 1 and s_.get("roof") in ("xieshan", "wudian") and rng.random() < fin["dbl"] and kind != "gate_hall":
                # (not under a room stacked on it: its skirt roof came loose round the upper hall)
                s_["double_eave"] = True
            if ti >= 2 and not s_.get("caihua"):
                s_["caihua"] = rng.choice(("xuanzi", "hexi", "sushi", ""))
    return out


def tpl0(kind, ti, mat, rng, r, sz):
    W, O, T, L, TOP = mat["wall"], mat["other"], mat["theme"], mat["lesser"], mat["top"]
    base = mat["base"]

    if kind == "main_hall":                              # 宗门主殿
        if ti == 0:
            return dict(w=sz(19, 23), d=13, rooms=[(0, 0, r(roof="xieshan", theme=TOP, podium=1, base=base, sh=7, uses="council_hall", brackets=1))])
        if ti == 1:
            return dict(w=sz(25, 29), d=sz(15, 17), rooms=[(0, 0, r(roof="xieshan", theme=TOP, podium=2, podium_style="xumizuo",
                                                                      base=base, sh=8, double_eave=True, brackets=2, uses="throne_hall"))])
        w, d = [(sz(37, 43), sz(21, 23)), (sz(47, 53), sz(25, 27)), (sz(57, 63), sz(29, 31))][ti - 2]
        roof = _pick(rng, ("wudian", "wudian", "xieshan"))
        return dict(w=w, d=d, rooms=[(0, 0, r(roof=roof, theme=TOP, podium=4 + ti // 2, podium_style="tiers", base=base, sh=8, colonnade=True,
                                              brackets=4, beasts=2 + ti // 2, caihua="hexi", pitch=3, uses="throne_hall")),
                                     (4, 4, r(roof=roof, theme=TOP, sh=10 + ti, brackets=4, beasts=2 + ti // 2, caihua="hexi", pitch=4,
                                              uses="council_hall", stack=True))])
    if kind == "ancestor":                               # 祖师堂
        if ti <= 1:
            return dict(w=sz(17, 21), d=sz(11, 13), rooms=[(0, 0, r(theme=TOP, podium=2, podium_style="xumizuo", base=base, sh=7,
                                                                      uses="ancestor_tablets", brackets=2))])
        w, d = sz(25, 31), sz(17, 21)
        return dict(w=w, d=d, rooms=[(0, 0, r(theme=TOP, podium=4, podium_style="tiers", base=base, sh=7, colonnade=True, brackets=4,
                                              caihua="xuanzi", uses="ancestor_tablets")),
                                     (4, 4, r(theme=TOP, sh=10, brackets=4, uses="ancestor_tablets", stack=True))])
    if kind == "teleport":                               # 传送殿
        w = sz(21, 25)
        return dict(w=w, d=w, rooms=[(0, 0, r(roof="pyramid", walls="jade" if rng.random() < 0.5 else W, theme=T, podium=4, podium_style="tiers",
                                              base=base, sh=7, colonnade=True, brackets=4, uses="teleport_room")),
                                     (4, 4, r(roof="pyramid", walls="jade" if rng.random() < 0.5 else W, theme=T, sh=10, brackets=4,
                                              uses="teleport_room", stack=True))])
    if kind == "treasury":                               # 宝库
        w = sz(13, 15 + 2 * ti)
        return dict(w=w, d=sz(11, w), rooms=[(0, 0, r(roof="yingshan", walls="stone", theme=L, podium=3, base=base, sh=6, uses="treasury"))])
    if kind == "teaching":                               # 传功殿
        return dict(w=sz(23, 25 + 2 * ti), d=sz(15, 17 + ti), rooms=[(0, 0, r(theme=T, podium=2, podium_style="xumizuo", base=base, sh=7,
                                                                              brackets=3, uses="lecture_hall"))])
    if kind == "task":                                   # 执事堂
        return dict(w=sz(21, 23 + 2 * ti), d=sz(13, 15), rooms=[(0, 0, r(walls=_pick(rng, (W, O)), theme=L, podium=2, podium_style="xumizuo",
                                                                          base=base, sh=6, brackets=2, uses="task_hall"))])
    if kind == "discipline":                             # 戒律堂
        return dict(w=sz(17, 19), d=sz(13, 15), rooms=[(0, 0, r(roof="yingshan", walls=_pick(rng, ("darkwood", "stone", O)), theme=_pick(rng, ("black", L)),
                                                                 podium=1, sh=6, uses="discipline_hall"))])
    if kind == "library":                                # 藏经阁: two rooms of two storeys, the upper set in
        w = sz(17, 19 + 2 * min(ti, 2))
        return dict(w=w, d=w, rooms=[(0, 0, r(theme=T, podium=2, podium_style="xumizuo", base=base, storeys=2, sh=6,
                                              uses=["scripture_store", "master_study"])),
                                     (1, 1, r(theme=T, storeys=2, sh=6, uses="scripture_store", stack=True))])
    if kind == "alchemy":                                # 丹房
        return dict(w=13, d=13, rooms=[(0, 0, r(walls=_pick(rng, (O, "darkwood")), theme=T, storeys=2, sh=6, uses="alchemy_room"))])
    if kind == "forge":                                  # 器坊
        return dict(w=sz(13, 15), d=13, rooms=[(0, 0, r(roof="yingshan", walls="stone", theme=_pick(rng, ("slate", L)), sh=7, uses="forging_room"))])
    if kind == "talisman":                               # 符堂
        return dict(w=13, d=11, rooms=[(0, 0, r(walls=O, theme=T, storeys=2, sh=6, uses="talisman_room"))])
    if kind == "formation":                              # 阵堂
        return dict(w=15, d=15, rooms=[(0, 0, r(roof="pyramid", walls=_pick(rng, ("darkwood", O)), theme=_pick(rng, ("black", T)), podium=1, sh=7,
                                                uses="formation_room"))])
    if kind == "dorm":                                   # 外门弟子舍
        return dict(w=sz(17, 21), d=13, rooms=[(0, 0, r(roof=_pick(rng, ("yingshan", "xuanshan")), walls=_pick(rng, ("wattle", O)), theme=L,
                                                         storeys=2, sh=5, uses="outer_dorm"))])
    if kind == "inner_res":                              # 内门弟子居
        return dict(w=13, d=13, rooms=[(0, 0, r(walls=O, theme=T, podium=1, storeys=2, sh=5, uses="inner_disciple_room"))])
    if kind == "elder":                                  # 长老居所
        return dict(w=sz(15, 17), d=13, rooms=[(0, 0, r(walls=_pick(rng, (W, O)), theme=T, podium=1, storeys=2, sh=6, uses="elder_quarters",
                                                         veranda=rng.random() < 0.5))])
    if kind == "refectory":                              # 食堂
        return dict(w=sz(19, 23), d=13, rooms=[(0, 0, r(roof="yingshan", walls=O, theme=L, sh=6, uses="refectory"))])
    if kind == "stable":                                 # 灵兽厩
        return dict(w=sz(15, 19), d=13, rooms=[(0, 0, r(roof="xuanshan", walls="log", theme="wood", sh=6, uses="beast_stable"))])
    if kind == "hut":                                    # 灵田小屋
        return dict(w=9, d=7, rooms=[(0, 0, r(roof="xuanshan", walls="wattle", theme="thatch", sh=5, uses="herb_store"))])
    if kind == "guard":                                  # 门房
        return dict(w=sz(9, 11), d=9, rooms=[(0, 0, r(roof="yingshan", walls=O, theme=L, sh=5, uses="guardroom"))])
    if kind == "bell":                                   # 钟楼 / 鼓楼
        return dict(w=11, d=11, rooms=[(0, 0, r(theme=T, podium=2, base=base, storeys=2, sh=6, uses="lookout", double_eave=ti >= 3))])
    if kind == "gate_hall":                              # 山门殿: open through, the way up runs through it
        w, d = [(15, 9), (21, 11), (sz(25, 29), 11), (sz(29, 33), 13), (sz(33, 35), 13)][ti]
        return dict(w=w, d=d, rooms=[(0, 0, r(theme=T, podium=1 + (ti >= 2), base=base, sh=7, open="through", double_eave=ti >= 1,
                                              brackets=2 if ti >= 2 else 0, uses="guardroom"))])
    if kind == "trial_tower":                            # 试炼塔: two storeys a room, each set in one
        w = sz(15, 17 + 2 * min(ti, 1))
        n = 2 + min(ti, 2)
        rooms = [(0, 0, r(roof="pyramid", walls="stone", theme=_pick(rng, ("black", L)), podium=2, podium_style="xumizuo", base=base,
                          storeys=2, sh=5, uses="training_room"))]
        for k in range(1, n):                            # (the top one a single storey: a narrow two-storey top room had
            rooms.append((k, k, r(roof="pyramid", walls="stone", theme=rooms[0][2]["theme"], storeys=2 if k < n - 1 or w - 2 * k >= 13 else 1,
                                  sh=5, uses="training_room", stack=True)))   # no room for its stair up)
        return dict(w=w, d=w, rooms=rooms)
    if kind == "sword_pav":                              # 剑阁
        w = sz(15, 17)
        return dict(w=w, d=w, rooms=[(0, 0, r(walls="darkwood", theme=_pick(rng, ("black", T)), podium=2, podium_style="xumizuo", base=base,
                                              storeys=2, sh=5, uses="sword_gallery")),
                                     (1, 1, r(walls="darkwood", theme="black", storeys=2, sh=5, uses="meditation_room", stack=True)),
                                     (2, 2, r(walls="darkwood", theme="black", storeys=1, sh=5, uses="training_room", stack=True))])
    if kind == "alchemy_tower":                          # 炼丹塔
        w = 15
        return dict(w=w, d=w, rooms=[(0, 0, r(roof="pyramid", theme=T, podium=2, podium_style="xumizuo", base=base, storeys=2, sh=5,
                                              uses="alchemy_room")),
                                     (1, 1, r(roof="pyramid", theme=T, storeys=2, sh=5, uses="meditation_room", stack=True)),
                                     (2, 2, r(roof="pyramid", theme=T, storeys=2, sh=5, uses="alchemy_room", stack=True))])
    if kind == "array_tower":                            # 阵眼塔
        return dict(w=13, d=13, rooms=[(0, 0, r(roof="pyramid", walls="darkwood", theme="black", podium=2, podium_style="xumizuo", base=base,
                                                storeys=2, sh=5, uses="array_control")),
                                       (1, 1, r(roof="pyramid", walls="darkwood", theme="black", storeys=2, sh=5, uses="formation_room", stack=True))])
    if kind == "pavilion":                               # 亭: open, a pyramid or round roof
        w = _pick(rng, (7, 9, 9))
        if rng.random() < 0.35:
            return dict(w=w, d=w, rooms=[(0, 0, r(shape="round", round=True, round_roof="cone", theme=T, open="open", podium=1, sh=5,
                                                  uses="lookout"))])
        return dict(w=w, d=w, rooms=[(0, 0, r(roof="pyramid", theme=T, open="open", podium=1, sh=5, uses="lookout",
                                              double_eave=rng.random() < 0.3))])
    if kind == "pagoda":                                 # 塔: a round tower of a few storeys
        return dict(w=11, d=11, rooms=[(0, 0, r(shape="round", round=True, round_roof=_pick(rng, ("cone", "helmet")), theme=T,
                                                podium=1, storeys=3 + min(ti, 2), sh=5, uses="lookout"))])
    raise KeyError(kind)


BIG = {"main_hall", "ancestor", "teleport", "teaching", "task", "gate_hall"}   # (the halls a court is built round)
RANK = {"main_hall": 9, "gate_hall": 8, "ancestor": 7, "teleport": 7, "teaching": 6, "task": 6, "library": 5}


# ------------------------------------------------------------------------------------------------ footprints

_FP = {}
_FP_FILE = paths.RUN / "cache" / "sect_fp_cache.json"
_FP_DIRTY = [False]


def _fp_load():
    if _FP or not _FP_FILE.exists():
        return
    try:
        _FP.update(json.loads(_FP_FILE.read_text(encoding="utf-8")))
    except Exception:
        pass


def fp_save():
    if not _FP_DIRTY[0]:
        return
    try:
        _FP_FILE.parent.mkdir(parents=True, exist_ok=True)
        _FP_FILE.write_text(json.dumps(_FP), encoding="utf-8")
        _FP_DIRTY[0] = False
    except Exception:
        pass


def footprint(rooms):
    """How far a building's blocks reach past its frame (west, north, east, south), the building built on its own:
    eaves, steps, platforms. Cached by its settings (not its seed or place)."""
    import compose
    from arch import Canvas
    _fp_load()
    f0 = rooms[0]
    key = json.dumps([{k: v for k, v in r.items() if k not in ("seed", "y", "variant", "roof_variant")} | dict(
        x0=r["x0"] - f0["x0"], x1=r["x1"] - f0["x0"], z0=r["z0"] - f0["z0"], z1=r["z1"] - f0["z0"]) for r in rooms], sort_keys=True)
    if key in _FP:
        return tuple(_FP[key])
    cv = Canvas()
    rs = []
    for r in rooms:
        rr = dict(r, x0=r["x0"] - f0["x0"], x1=r["x1"] - f0["x0"], z0=r["z0"] - f0["z0"], z1=r["z1"] - f0["z0"], y=64)
        rs.append(rr)
    compose.build_compose(dict(tier="mortal", seed=1, rooms=rs), cv)
    pts = [(x, z) for (x, y, z), s in cv.blocks.items() if s != "minecraft:air" and y > 64]
    X1, Z1 = f0["x1"] - f0["x0"], f0["z1"] - f0["z0"]
    out = (max(0, -min(p[0] for p in pts)), max(0, -min(p[1] for p in pts)), max(0, max(p[0] for p in pts) - X1),
           max(0, max(p[1] for p in pts) - Z1)) if pts else (0, 0, 0, 0)
    _FP[key] = list(out)
    _FP_DIRTY[0] = True
    return out


class Bld:
    """A building: its kind, its frame size (as placed: sx along x, sz along z), its front, its rooms relative to the
    frame's corner, and its footprint past the frame."""

    def __init__(self, plan, kind, front="south", t=None, seeds=None):
        self.kind = kind
        t = t or tpl(kind, plan.ti, plan.mat, plan.rng)
        self.t = t
        self.front = front
        across = front in ("south", "north")
        self.sx, self.sz = (t["w"], t["d"]) if across else (t["d"], t["w"])
        self.rel = []
        for a, b, s in t["rooms"]:
            ia, ib = (a, b) if across else (b, a)
            self.rel.append((ia, ib, dict(s, front=front)))
        self.seeds = seeds or [plan.rng.randrange(1 << 30) for _ in self.rel]
        self.fp = footprint(self.rooms(0, 0, 64))

    def turned(self, plan, front):
        """The same building facing another way."""
        return Bld(plan, self.kind, front, t=self.t, seeds=self.seeds)

    def rooms(self, x0, z0, y):
        out = []
        for (ia, ib, s), sd in zip(self.rel, self.seeds):
            r = dict(s, x0=x0 + ia, z0=z0 + ib, x1=x0 + self.sx - 1 - ia, z1=z0 + self.sz - 1 - ib, y=y, seed=sd, variant=0,
                     roof_variant=0, special="", stack=bool(s.get("stack")), round=bool(s.get("round")))
            if s.get("shape") == "round":
                r["shape"] = "round"
            out.append(r)
        return out

    def reach(self, x0, z0, spare=0):
        """The rectangle its blocks reach over when its frame's corner is at (x0, z0)."""
        w, n, e, s = self.fp
        return (x0 - w - spare, z0 - n - spare, x0 + self.sx - 1 + e + spare, z0 + self.sz - 1 + s + spare)


# ------------------------------------------------------------------------------------------------ the ground

def _synth(rng, ti, length, A):
    """A mountain falling towards +v: a peak at (0, v_top) with a ridge down the axis (bending a little), the slopes
    steeper to the sides, a few spurs on its flanks; 化神 several peaks round the main one."""
    sv = length / rng.uniform(1.45, 1.75)                 # (along the axis: the foot a tenth of the way up)
    su = sv * rng.uniform(0.45, 0.6)                      # (across it: steeper)
    vtop = -length * 0.42
    bend = rng.choice((-1, 1)) * rng.uniform(0.08, 0.2) * length, rng.uniform(0.6, 1.4) * length, rng.uniform(0, 2 * math.pi)
    spurs = []
    for _ in range(3 + ti * 2):
        side = rng.choice((-1, 1))
        v = rng.uniform(vtop - 0.1 * length, vtop + 0.95 * length)
        u = side * rng.uniform(0.25, 0.7) * su * 2.0
        spurs.append((u, v, rng.uniform(0.06, 0.16) * A, rng.uniform(14, 30)))
    peaks = [(0.0, vtop, A, su, sv)]
    if ti == 4:                                           # 化神: peaks round the main one
        n = rng.choice((3, 4))
        for k in range(n):
            side = -1 if k % 2 == 0 else 1
            off = (k // 2 + 1)
            peaks.append((side * rng.uniform(1.1, 1.35) * su * off ** 0.7, vtop + rng.uniform(0.05, 0.5) * length,
                          A * rng.uniform(0.7, 0.9), su * 0.75, sv * 0.55))
    waves = [(rng.uniform(0, 2 * math.pi), rng.uniform(25, 60), rng.uniform(0, math.pi), rng.uniform(1.0, 2.5)) for _ in range(3)]

    def ridge(v):
        return bend[0] * math.sin(2 * math.pi * v / bend[1] + bend[2])

    def h(u, v):
        best = 0.0
        for k, (pu, pv, a, s_u, s_v) in enumerate(peaks):
            du = u - pu - (ridge(v) if k == 0 else 0)
            dv = v - pv
            sv_ = s_v if dv > 0 else s_v * 0.6           # (steeper behind the peak)
            d2 = (du / s_u) ** 2 + (dv / sv_) ** 2
            best = max(best, a * math.exp(-d2))
        for pu, pv, a, r in spurs:
            d2 = ((u - pu - ridge(v)) ** 2 + (v - pv) ** 2) / (r * r)
            best += a * math.exp(-d2) * (best / A) ** 0.5
        for ph, lam, ang, amp in waves:
            best += amp * math.sin(2 * math.pi * (u * math.cos(ang) + v * math.sin(ang)) / lam + ph)
        return best
    return h, ridge


def _given(heights):
    """The game's samples (world y round the origin) as a function of the design's (x, z), clamped at the edge."""
    if callable(heights):
        return heights, None
    R = int(heights["R"])
    n = 2 * R + 1
    raw = list(heights["h"])
    good = sorted(v for v in raw if v is not None and v > -9000)
    fill = good[len(good) // 2] if good else 64
    vals = [v if v is not None and v > -9000 else fill for v in raw]

    def h(x, z):
        i = max(-R, min(R, int(round(x)))) + R
        k = max(-R, min(R, int(round(z)))) + R
        return vals[k * n + i]
    return h, R


ROT = {0: lambda u, v: (u, v), 1: lambda u, v: (-v, u), 2: lambda u, v: (-u, -v), 3: lambda u, v: (v, -u)}
_DIRV = {"south": (0, 1), "north": (0, -1), "east": (1, 0), "west": (-1, 0)}
_VDIR = {v: k for k, v in _DIRV.items()}


# ------------------------------------------------------------------------------------------------ the plan

class Sect:
    def __init__(self, seed, tier="jindan", heights=None):
        if tier not in TIERS:                             # (凡人, or a tier the game does not have: the smallest)
            tier = "lianqi"
        self.seed, self.tier, self.ti = seed, tier, TIERS.index(tier)
        rng = self.rng = random.Random(f"sect/{tier}/{seed}")
        fam = self.fam = rng.choice(FAMILIES)
        self.mat = dict(wall=rng.choice(fam["walls"]), other=rng.choice(fam["others"]), theme=rng.choice(fam["themes"]),
                        lesser=rng.choice(fam["lesser"]), top=rng.choice(fam["top"]), base=fam["base"], path=fam["path"],
                        court=rng.choice((2, 2, 2, 3)), roofs=fam.get("roofs"))
        self.name = rng.choice(SECT_NAMES) + rng.choice(("宗", "门", "派", "剑宗", "仙门", "道宫", "阁"))
        self.rooms = []                                   # (local coords; turned into the design's at the end)
        self.occ = {}                                     # (u, v) -> owner id of the ground claimed there
        self.nid = 0
        self.courts = []
        self.terraces = []                                # (every terrace: the courts, the side ones, the spurs')
        self.ports = []                                   # where a path can leave a court
        self.nb = 0
        self.heights = heights
        self.k = 0
        self.peak_names = []
        self.pending_walls = []
        self.path_pieces = []
        self.peak_more = []
        self.arrivals = []                                # (building, its frame corner, the last row of the path to it)

    # -------------------------------------------------------------------------------------------- ground helpers

    def setup_ground(self, length, A=100):
        if self.heights is None:
            self.anchored = False
            self.H, self.ridge = _synth(self.rng, self.ti, length, A)
            self.R = None
            self.k = 0
            return
        self.anchored = True
        g, R = _given(self.heights)
        self.R = R
        # the mountain falls towards +v: of the four ways to turn the plan, the one with the most fall from a summit
        # (with room below it for the sect) to the foot; towards the player (+z) when it is about as good
        best = None
        lim = (R or 200) - 8
        for k in range(4):
            rot = ROT[k]
            H = (lambda rot: lambda u, v: g(*rot(u, v)))(rot)
            for vs in range(-lim, max(-lim + 1, lim - int(length * 0.6) + 1), 8):   # (a long sect: from the top edge)
                for us in range(-lim + 30, lim - 29, 8):
                    vf = min(lim, vs + int(length))
                    fall = H(us, vs) - H(us, vf)
                    score = fall + (8 if k == 0 else 0) - 0.02 * abs(us)
                    if best is None or score > best[0]:
                        best = (score, k, us, vs)
        _, self.k, us, vs = best
        rot = ROT[self.k]
        self.H = lambda u, v: g(*rot(u, v))
        self.ridge = lambda v: us
        self.v_top = vs

    def hs(self, u, v, r=3):
        """The ground smoothed a little."""
        tot = n = 0
        for du in (-r, 0, r):
            for dv in (-r, 0, r):
                tot += self.H(u + du, v + dv)
                n += 1
        return tot / n

    def level_of(self, u0, v0, u1, v1):
        """The level of a terrace over a rectangle: the middle of the ground under it (cut at the back, filled in
        front)."""
        vals = sorted(self.H(u, v) for u in range(u0, u1 + 1, max(1, (u1 - u0) // 6)) for v in range(v0, v1 + 1, max(1, (v1 - v0) // 6)))
        return int(round(vals[len(vals) // 2]))

    def free(self, u0, v0, u1, v1, ok=()):
        for u in range(u0, u1 + 1):
            for v in range(v0, v1 + 1):
                o = self.occ.get((u, v))
                if o is not None and o not in ok:
                    return False
        return True

    def claim(self, u0, v0, u1, v1, owner, margin=1):
        for u in range(u0 - margin, u1 + margin + 1):
            for v in range(v0 - margin, v1 + margin + 1):
                self.occ.setdefault((u, v), owner)

    def new_id(self):
        self.nid += 1
        return self.nid

    def ground(self, kind, u0, v0, u1, v1, y, variant=0, front="south", **kw):
        kw = dict(dict(walls=self.mat["other"], theme=self.mat["lesser"], base=self.mat["base"]), **kw)
        r = room(min(u0, u1), min(v0, v1), max(u0, u1), max(v0, v1), self.rng, special=kind, y=y, variant=variant,
                 front=front, **kw)
        self.rooms.append(r)
        return r

    def put(self, b, x0, z0, y):
        self.rooms += b.rooms(x0, z0, y)
        self.nb += 1

    # -------------------------------------------------------------------------------------------- a run of ground

    def run(self, cells, ys, width, along, owner):
        """A path or stair: `cells` the (u, v) of its middle line, one a row, `ys` their levels (next to next at most
        one apart), `width` across, `along` "u" or "v" (or a list: each row's, "sq" a corner square). Cut into
        pieces: flat runs a 街道 landing, climbing runs a 石阶 flight (front: its low end), corners a square landing."""
        half = width // 2
        al = [along] * len(cells) if isinstance(along, str) else list(along)
        pieces, cur = [], [0]
        for i in range(1, len(cells)):
            d = ys[i] - ys[i - 1]
            if al[i] == al[cur[0]] != "sq" and (len(cur) == 1 or d == ys[cur[1]] - ys[cur[0]]) and len(cur) < 200:
                # (at most 200 rows a piece: the room table takes 256 a side)
                cur.append(i)
            else:
                pieces.append(cur)
                cur = [i]
        pieces.append(cur)
        for p in pieces:
            us = [cells[i][0] for i in p]
            vs = [cells[i][1] for i in p]
            a = al[p[0]]
            if a in ("bu", "bv"):                         # 石桥: one deck, a cell wider each side (its balustrade)
                w = half + 1
                if a == "bu":
                    u0, u1, v0, v1 = min(us), max(us), vs[0] - w, vs[0] + w
                else:
                    u0, u1, v0, v1 = us[0] - w, us[0] + w, min(vs), max(vs)
                self.ground("bridge", u0, v0, u1, v1, ys[p[0]], variant=self.mat["path"], front="east" if a == "bu" else "south",
                            walls=self.mat["other"], base=self.mat["base"])
                self.claim(u0, v0, u1, v1, owner, margin=1)   # (no other way right beside it, at another level)
                self.nbridge = getattr(self, "nbridge", 0) + 1
                if self.ti >= 2 and self.rng.random() < 0.45:   # 廊桥: a gallery the length of the deck, a door at each end
                    g0, g1 = (u0, u1) if a == "bu" else (v0, v1)
                    g0, g1 = g0 + 2, g1 - 2                # (a cell or two of open deck at each end, outside its doors)
                    if (g1 - g0) % 2:
                        g1 -= 1                             # (odd: compose drops a column off an even frame)
                    gu0, gu1, gv0, gv1 = (g0, g1, v0, v1) if a == "bu" else (u0, u1, g0, g1)
                    rr = room(gu0, gv0, gu1, gv1, self.rng, y=ys[p[0]], storeys=1, roof=self.rng.choice(("xuanshan", "juanpeng", "xieshan")),
                              walls=self.mat["other"] if self.mat["other"] not in ("stone", "greybrick", "redbrick") else "darkwood",
                              theme=self.mat["lesser"], open="through", podium=0, sh=5, uses="lookout", overhang=1,
                              front="east" if a == "bu" else "south")   # (its front along the way: a door each end)
                    self.rooms.append(rr)
                    self.nb += 1
                    self.ncovered = getattr(self, "ncovered", 0) + 1
                continue
            if a == "u":
                u0, u1, v0, v1 = min(us), max(us), vs[0] - half, vs[0] + half
            elif a == "v":
                u0, u1, v0, v1 = us[0] - half, us[0] + half, min(vs), max(vs)
            else:
                u0, u1, v0, v1 = us[0] - half, us[0] + half, vs[0] - half, vs[0] + half
            d = ys[p[1]] - ys[p[0]] if len(p) > 1 else 0
            if d == 0:
                self.ground("street", u0, v0, u1, v1, ys[p[0]], variant=self.mat["path"], front="south" if a == "v" else "east")
            else:
                ylow = min(ys[i] for i in p)
                low = cells[p[0]] if ys[p[0]] == ylow else cells[p[-1]]   # the low end's row
                other = cells[p[-1]] if low is cells[p[0]] else cells[p[0]]
                dv = (0, 1 if low[1] > other[1] else -1) if a == "v" else (1 if low[0] > other[0] else -1, 0)
                self.ground("stairs", u0, v0, u1, v1, ylow, variant=self.mat["path"], front=_VDIR[dv])
            self.claim(u0, v0, u1, v1, owner, margin=1)   # (no other way right beside it, at another level)
            if max(u1 - u0, v1 - v0) >= 12 and isinstance(owner, int):   # (a long piece: trees along it, later)
                self.path_pieces.append(dict(id=owner, u0=u0, v0=v0, u1=u1, v1=v1))

    def route(self, pa, pb, width=3, ok=()):
        """A path from port pa (leaving its court sideways) to port pb (coming into its court sideways): along u, a
        corner, along v, a corner, along u. Returns (rows, along, ys) or None where it cannot go."""
        sa, sb = pa["side"], pb["side"]
        dv = pb["v"] - pa["v"]
        half = width // 2
        if dv != 0 and abs(dv) < 2 * half + 2:
            return None
        lo = max(pa["u"] + sa, pb["u"] + sb) if sa > 0 and sb > 0 else None
        cands = []
        if dv == 0:
            if sa != -sb or (pb["u"] - pa["u"]) * sa < 0:
                return None
            rows = [(u, pa["v"]) for u in range(pa["u"], pb["u"] + sa, sa)]
            al = ["u"] * len(rows)
            cands.append((rows, al, [False] * len(rows)))
        else:
            ua, ub = pa["u"], pb["u"]
            options = []
            for um in range(min(ua, ub) - 60, max(ua, ub) + 61):
                if (um - ua) * sa >= half + 1 and (um - ub) * sb >= half + 1:
                    options.append(um)
            options.sort(key=lambda um: abs(um - ua) + abs(um - ub))
            for um in options[:40]:
                rows, al, hold = [], [], []
                for u in range(ua, um + sa * (half + 1), sa):  # along u to the corner, through it (held level)
                    rows.append((u, pa["v"]))
                    al.append("u")
                    hold.append((u - um) * sa > -half)
                s2 = 1 if dv > 0 else -1
                for v in range(pa["v"] + s2 * (half + 1), pb["v"] - s2 * half, s2):
                    rows.append((um, v))
                    al.append("v")
                    hold.append(False)
                s3 = -sb
                for u in range(um - s3 * half, ub + s3, s3):   # through the second corner (held level), on along u
                    rows.append((u, pb["v"]))
                    al.append("u")
                    hold.append(-half < (u - um) * s3 <= half)
                cands.append((rows, al, hold))
        okk = set(ok) | {pa["court"]["id"], pb["court"]["id"]}
        for rows, al, hold in cands:
            good = True
            for k, ((u, v), a) in enumerate(zip(rows, al)):
                cu = (u, u) if a == "u" else (u - half, u + half)
                cv_ = (v, v) if a == "v" else (v - half, v + half)
                # (only its first and last rows may run along the rim of the courts it joins: a corner there, at
                # another level, got a flight of steps down to it from the court that took its foot)
                if not self.free(cu[0], cv_[0], cu[1], cv_[1], ok=okk if k in (0, len(rows) - 1) else set(ok)):
                    good = False
                    break
            if not good:
                continue
            want = [self.hs(u, v) for u, v in rows]
            ys = self.profile(want, pa["y"], pb["y"], hold=hold)
            if ys is None:
                continue
            # a long way: one even climb - flights and landings - when the ground lets it (a causeway at most 40 over
            # the ground, a cutting at most 24 into it), so it is a few pieces, not one at every rise and fall
            if len(rows) > 8:
                free_rows = [i for i, h in enumerate(hold) if not h]
                ev0 = self.stair_profile(pa["y"], pb["y"], len(free_rows))
                if ev0:
                    ev, k, prev = [], 0, pa["y"]
                    for h in hold:
                        if h:
                            ev.append(prev)
                        else:
                            ev.append(ev0[k])
                            k += 1
                        prev = ev[-1]
                    if all(-24 <= y - w <= 40 for y, w in zip(ev, want)) and self.pieces(ev, al) < self.pieces(ys, al):
                        ys = ev
            al = self.bridges(rows, al, hold, ys, want, okk, half)
            return rows, al, ys
        return None

    def bridges(self, rows, al, hold, ys, want, okk, half):
        """Where a straight stretch of a way dips into a hollow (a saddle between two peaks, a gully): a 石桥 across
        it at the level of the way where it meets the hollow's sides - the highest level that spans it in at most 160
        cells with the hollow no more than 60 under it, when that saves six or more blocks of climbing down and up.
        Its rows (in ys, levelled) are marked "bu" / "bv" in the list it returns."""
        al = list(al)
        n = len(rows)
        i = 1
        while i < n - 1:
            a = al[i]
            if a not in ("u", "v") or hold[i]:
                i += 1
                continue
            b = i
            while b + 1 < n - 1 and al[b + 1] == a and not hold[b + 1]:
                b += 1
            done = False
            m = min(range(i, b + 1), key=lambda k: ys[k])
            for Y in range(max(ys[i:b + 1]), ys[m] + 5, -1):
                left = [k for k in range(i, m + 1) if ys[k] >= Y]
                right = [k for k in range(m, b + 1) if ys[k] >= Y]
                if not left or not right:
                    continue
                p, q = left[-1], right[0]
                if q - p < 10 or q - p > 160 or ys[p] != Y or ys[q] != Y:
                    continue
                if Y - min(want[p:q + 1]) > 60 or Y - min(ys[p:q + 1]) < 6:
                    continue
                w = half + 1                              # (a cell more each side for the balustrade)
                ok = True
                for k in range(p, q + 1):
                    u, v = rows[k]
                    cu = (u, u) if a == "u" else (u - w, u + w)
                    cv_ = (v, v) if a == "v" else (v - w, v + w)
                    if not self.free(cu[0], cv_[0], cu[1], cv_[1], ok=okk):
                        ok = False
                        break
                if not ok:
                    continue
                for k in range(p, q + 1):                 # (its two end rows stay ground: the step up off it is there)
                    ys[k] = Y
                    if p < k < q:
                        al[k] = "b" + a
                done = True
                break
            i = b + 1 if not done else i
            if done:
                continue
        return al

    @staticmethod
    def pieces(ys, al):
        """How many pieces run() would cut these rows into."""
        n, cur, d0 = 1, 1, None
        for i in range(1, len(ys)):
            d = ys[i] - ys[i - 1]
            if al[i] == al[i - 1] != "sq" and (cur == 1 or d == d0):
                d0 = d if cur == 1 else d0
                cur += 1
            else:
                n += 1
                cur, d0 = 1, None
        return n

    def ports_of(self, c):
        out = []
        for side in (-1, 1):                              # the ways out of its strips to the sides
            for strip in ("front", "back"):
                if strip == "back" and not c.get("bs"):
                    continue
                pv = c["v1"] - c["fs"] // 2 if strip == "front" else c["v0"] + c["bs"] // 2
                out.append(dict(court=c, side=side, v=pv, u=(c["u0"] - 1) if side < 0 else (c["u1"] + 1), y=c["y"], used=False,
                                strip=strip))
        return out

    @staticmethod
    def profile(want, y0, y1, flight=16, landing=3, hold=None):
        """Levels for the rows of a way from y0 (before its first row) to y1 (after its last): next to next at most one
        apart, following `want` (the ground) where it can; on a long climb a landing (three rows flat) every `flight`
        steps. None when it cannot be done in that many rows."""
        n = len(want)
        hold = hold or [False] * n
        after = [0] * n                                   # boundaries free to climb at after each row (to y1)
        k = 1
        for i in range(n - 1, -1, -1):
            after[i] = k
            if not hold[i]:
                k += 1
        if abs(y1 - y0) > k - 1:
            return None
        ys, prev, run, mode = [], y0, 0, 0
        for i, w in enumerate(want):
            left = after[i]
            if hold[i]:                                   # (a corner: level with the row before it)
                if abs(y1 - prev) > left:
                    return None
                ys.append(prev)
                continue
            lo, hi = max(prev - 1, y1 - left), min(prev + 1, y1 + left)
            if lo > hi:
                return None
            if not mode and abs(w - prev) > 9:            # (flat until the ground has gone well up or down, then a flight)
                mode = 1 if w > prev else -1
            t = prev + mode
            if mode and (t - w) * mode >= 0:
                mode = 0
            y = max(lo, min(hi, t))
            if y != prev:
                run += 1
            else:
                run = 0
            if run > flight and lo <= prev <= hi and abs(y1 - prev) <= left - 1:   # a landing when the climb lets it
                y = prev
                run = -landing
            elif run < 0 and y != prev and lo <= prev <= hi and abs(y1 - prev) <= left - 1:
                y = prev
                run += 1
            ys.append(y)
            prev = y
        if abs(ys[-1] - y1) > 1:
            return None
        return ys

    def stair_rows(self, ua, va, ub, vb, width):
        """The rows of the axis stair from under (ua, va) - the upper court's front edge - to over (ub, vb) - the lower
        court's back edge: straight down when the two line up, else down to a landing, along the slope to under the
        lower court's middle and down again (the landings held level). Returns (rows, along, hold)."""
        half = width // 2
        if abs(ub - ua) <= 2 or vb - va - 1 < width + 2:
            u = ua if abs(ub - ua) <= 2 else (ua + ub) // 2
            rows = [(u, v) for v in range(va + 1, vb)]
            return rows, ["v"] * len(rows), [False] * len(rows)
        vm = va + 1 + half + (vb - va - 1 - width) // 2   # (the landing half way down)
        sd = 1 if ub > ua else -1
        rows, al, hold = [], [], []
        for v in range(va + 1, vm + half + 1):            # down to the landing (its rows held level)
            rows.append((ua, v))
            al.append("v")
            hold.append(v > vm - half)
        for u in range(ua + sd * (half + 1), ub - sd * half, sd):   # along to under the lower court
            rows.append((u, vm))
            al.append("u")
            hold.append(False)
        for v in range(vm - half, vb):                    # the second landing, then down
            rows.append((ub, v))
            al.append("v")
            hold.append(vm - half < v <= vm + half)
        return rows, al, hold

    @staticmethod
    def row_rect(row, a, half):
        u, v = row
        return (u, v - half, u, v + half) if a == "u" else (u - half, v, u + half, v)

    def stair(self, us, va, vb, ya, yb, width, owner):
        """The axis stair from the upper court's front edge (va, its middle at us[0], ground at ya) to the lower court's
        back edge (vb, its middle at us[1], ground at yb): flights and landings, turning at a landing where the
        courts do not line up."""
        rows, al, hold = self.stair_rows(us[0], va, us[1], vb, width)
        if not rows:
            return abs(ya - yb) <= 1
        free_rows = [i for i, h in enumerate(hold) if not h]
        ys0 = self.stair_profile(ya, yb, len(free_rows))
        if ys0 is None:
            want = [ya + (yb - ya) * (i + 1) / (len(free_rows) + 1) for i in range(len(free_rows))]
            ys0 = self.profile(want, ya, yb)
        if ys0 is None:
            return False
        ys, k, prev = [], 0, ya
        for h in hold:
            if h:
                ys.append(prev)
            else:
                ys.append(ys0[k])
                k += 1
            prev = ys[-1]
        self.run(rows, ys, width, al, owner)
        return True

    @staticmethod
    def stair_profile(y0, y1, n, flight=16):
        """Rows of an axis stair: flights of at most `flight` steps, landings of three or more between them, any rows to
        spare at the two ends; None when it does not fit."""
        d = y1 - y0
        D = abs(d)
        if D > n + 1:
            return None
        nf = max(1, -(-D // flight))
        while nf > 1 and D + 3 * (nf - 1) > n + 1:
            nf -= 1
        if D + 3 * (nf - 1) > n + 1 and D > 0:
            return Sect.profile([y0 + d * (i + 1) / (n + 1) for i in range(n)], y0, y1)
        flats = n + 1 - D
        inner = 3 * (nf - 1)
        spare = flats - inner
        per = spare // (nf + 1)
        gaps = [per] * (nf + 1)                           # flats before, between and after the flights
        gaps[0] += spare - per * (nf + 1)
        for k in range(1, nf):
            gaps[k] += 3
        sizes = [D // nf + (1 if k < D % nf else 0) for k in range(nf)]
        steps = []
        for k in range(nf):
            steps += [0] * gaps[k] + [1] * sizes[k]
        steps += [0] * gaps[nf]
        assert len(steps) == n + 1, (len(steps), n, D)
        sgn = 1 if d >= 0 else -1
        ys, y = [], y0
        for s in steps[:-1]:
            y += sgn * s
            ys.append(y)
        return ys

    # -------------------------------------------------------------------------------------------- a court

    def court_layout(self, spec):
        """A court in its own coordinates (u across from its axis, v from its back edge down): its main hall at the
        back facing down, side halls down both sides facing in, a strip behind the hall and one in front. Returns
        dict(W (half width), D (depth), items=[(Bld, u0, v0)], strips) or None."""
        rng = self.rng
        T = 4 if spec.get("walled") else 0               # (a wall round it, three deep, a row in from its edge)
        bs = 5 + T                                        # the strip behind the hall: the way round it to the stair up
        fs = 7 + T                                        # the strip in front: where the stair from below comes up
        items = []
        half_needed = 6
        v = bs
        main = spec.get("main")
        style = spec.get("style", "classic")
        sides = list(spec.get("sides", ()))
        extra = {}
        if main:
            m = Bld(self, main)
            w_, n_, e_, s_ = m.fp
            vm0 = bs + n_ + 1
            items.append((m, -(m.sx // 2), vm0))
            v = vm0 + m.sz + s_ + 1
            half_needed = max(half_needed, m.sx // 2 + max(w_, e_) + 4)
            if style == "three" and len(sides) >= 2:      # 三殿: two lesser halls either side of the main one, all facing down
                left_edge, right_edge = -(m.sx // 2) - w_, -(m.sx // 2) + m.sx - 1 + e_
                for k, side in ((sides.pop(0), -1), (sides.pop(0), 1)):
                    f = Bld(self, k)
                    fw, fn, fe, fs_ = f.fp
                    fv = max(bs + fn + 1, vm0 + m.sz - f.sz)   # (fronts in line where they can be)
                    if side < 0:
                        fu = left_edge - 2 - fe - (f.sx - 1)
                        left_edge = fu - fw
                    else:
                        fu = right_edge + 2 + fw
                        right_edge = fu + f.sx - 1 + fe
                    items.append((f, fu, fv))
                    v = max(v, fv + f.sz + fs_ + 1)
                half_needed = max(half_needed, -left_edge + 4, right_edge + 4)
            if style == "pond":                           # 金水河: a channel across the court in front of the hall, a bridge on the axis
                extra["pond"] = v + 1
                v += 9
        mid = max(5, spec.get("mid", 6))
        if style == "wide":
            mid += rng.randint(6, 14)
        elif style == "narrow":
            mid = 5
        cols = {-1: [], 1: []}
        k = 0
        for kind in sides:                                # alternately left and right, in pairs where they match
            cols[-1 if k % 2 == 0 else 1].append(kind)
            k += 1
        col_items = {-1: [], 1: []}
        v_end = v
        depth_need = 0
        for side in (-1, 1):
            vv = v
            for kind in cols[side]:
                b = Bld(self, kind, front="east" if side < 0 else "west")
                w_, n_, e_, s_ = b.fp
                vv += n_
                col_items[side].append((b, vv))
                vv += b.sz + s_ + 1
                back, frontr = (w_, e_) if side < 0 else (e_, w_)
                depth_need = max(depth_need, back + 1 + b.sx + frontr + mid)
            v_end = max(v_end, vv)
        Wi = max(half_needed, depth_need, spec.get("min_half", 0))
        W = Wi + T
        for side in (-1, 1):                              # the columns against the court's sides (inside its walls)
            for b, vv in col_items[side]:
                w_, n_, e_, s_ = b.fp
                if side < 0:
                    u0 = -Wi + w_ + 1
                else:
                    u0 = Wi - e_ - 1 - (b.sx - 1)
                items.append((b, u0, vv))
        open_ = spec.get("open", 0)
        if not sides and main:
            open_ = max(open_, 6)
        if style == "wide":
            open_ += rng.randint(4, 10)
            if open_ >= 9 and Wi >= 16:                   # 池: a pond either side of the way in the open front of the court
                extra["ponds"] = (v_end + 1, v_end + open_ - 1)
        if style == "cauldrons":                          # 丹炉: rows of cauldrons in the open court, either side of the way
            rows = 2
            extra["dings"] = (v_end + 2, rows)
            v_end += 2 + rows * 6
        if style == "pavilion":                           # a 亭 in the middle of the court, on the axis
            pv = Bld(self, "pavilion")
            pw, pn, pe, ps = pv.fp
            vp = v_end + 2 + pn
            items.append((pv, -(pv.sx // 2), vp))
            v_end = vp + pv.sz + ps + 2
            extra["pavilion"] = True
            self.nb_extra = getattr(self, "nb_extra", 0)
        D = v_end + open_ + fs
        return dict(W=W, D=D, items=items, bs=bs, fs=fs, main=main, T=T, **extra)

    def place_court(self, spec, uc, v0, y, axis_w, ornaments=True):
        """The court's ground and buildings with its back edge at v0, centred on uc; returns the court record."""
        lay = spec["_lay"]
        W, D = lay["W"], lay["D"]
        cid = self.new_id()
        u0, u1, v1 = uc - W, uc + W, v0 + D - 1
        self.ground("terrace", u0, v0, u1, v1, y, variant=self.mat["court"])
        self.claim(u0, v0, u1, v1, cid)
        for b, bu, bv in lay["items"]:
            self.put(b, uc + bu, v0 + bv, y)
        if lay.get("dings"):                              # 丹炉: cauldrons in rows either side of the way
            a0, rows = lay["dings"]
            Wi = W - lay.get("T", 0)
            for k in range(rows):
                vc = v0 + a0 + 2 + 6 * k
                for sd in (-1, 1):
                    for j in range(3):
                        pu = uc + sd * (axis_w // 2 + 4 + 6 * j)
                        if abs(pu - uc) + 2 <= Wi - 3:
                            self.ground("ornament", pu - 1, vc - 1, pu + 1, vc + 1, y, variant=0)
        if lay.get("ponds"):                              # 池 either side of the paved way
            a0, a1 = lay["ponds"]
            R = max(2, min(3, (a1 - a0) // 2 - 1))
            vc = v0 + (a0 + a1) // 2
            for sd in (-1, 1):
                pu = uc + sd * (axis_w // 2 + R + 3)
                self.ground("ornament", pu - R, vc - R, pu + R, vc + R, y, variant=13)
        if lay.get("pond"):                               # 金水河 across the court, its bridge on the axis
            half = W - lay.get("T", 0) - 3
            self.ground("river", uc - half, v0 + lay["pond"], uc + half, v0 + lay["pond"] + 6, y, variant=0)
        if ornaments and lay["main"] and not lay.get("pavilion"):   # 甬路: a paved way down the axis from the hall's steps, a 鼎 on it
            m, mu, mv = lay["items"][0]
            pv0 = v0 + mv + m.sz + m.fp[3] + 1
            if lay.get("pond"):
                pv0 = v0 + lay["pond"] + 8
            hw = axis_w // 2
            if v1 - lay.get("T", 0) - pv0 >= 6:
                self.ground("paving", uc - hw, pv0, uc + hw, v1 - lay.get("T", 0), y)
                if v1 - pv0 >= 14 and self.ti >= 1:
                    oz = pv0 + (v1 - pv0) // 2
                    self.ground("ornament", uc - 1, oz - 1, uc + 1, oz + 1, y, variant=0)
        c = dict(id=cid, u0=u0, u1=u1, v0=v0, v1=v1, y=y, uc=uc, bs=lay["bs"], fs=lay["fs"], spec=spec, T=lay.get("T", 0))
        self.courts.append(c)
        self.terraces.append(c)
        self.ports += self.ports_of(c)
        return c

    # -------------------------------------------------------------------------------------------- the programme

    def programme(self):
        """The axis courts from the top down, and what goes off them (side terraces, spurs)."""
        ti, rng = self.ti, self.rng
        work = ["alchemy", "forge", "talisman", "formation"]
        rng.shuffle(work)
        if ti == 0:
            axis = [dict(main="main_hall", sides=[rng.choice(("alchemy", "library")), rng.choice(("teaching", "talisman"))]),
                    dict(main="task", sides=["dorm", "dorm"]),
                    dict(gate=True)]
            off = [("row", ["hut", "hut"], "field"), ("row", ["stable"], None)]
            spurs = ["pavilion"]
        elif ti == 1:
            axis = [dict(main="main_hall", sides=["treasury", "elder"]),
                    dict(main="teaching", sides=["library", work[0], work[1], "talisman"]),
                    dict(main="task", sides=["dorm", "dorm", "discipline", "refectory"]),
                    dict(gate=True)]
            off = [("row", ["dorm", "dorm"], None), ("row", ["hut", "hut"], "field"), ("row", ["stable", "stable"], None),
                   ("row", ["elder", "elder"], None)]
            spurs = ["pavilion", "trial_tower", "pavilion"]
        elif ti == 2:
            axis = [dict(main="ancestor", sides=["treasury", "treasury"]),
                    dict(main="main_hall", sides=["bell", "bell", "elder", "elder"]),
                    dict(main="library", sides=[work[0], work[1], work[2], work[3]]),
                    dict(main="teaching", sides=["inner_res", "inner_res", "inner_res", "inner_res"]),
                    dict(main="task", sides=["discipline", "refectory", "dorm", "dorm"]),
                    dict(gate=True, sides=["guard", "guard"])]
            off = [("row", ["dorm", "dorm", "dorm"], None), ("row", ["dorm", "dorm"], None), ("row", ["hut", "hut", "hut"], "field"),
                   ("row", ["stable", "stable"], None), ("row", ["elder", "elder", "elder"], None), ("row", ["alchemy_tower"], None),
                   ("row", ["inner_res", "inner_res", "inner_res"], None)]
            spurs = ["trial_tower", "sword_pav", "pavilion", "pavilion", "pagoda"]
        else:
            axis = [dict(main="ancestor", sides=["treasury", "treasury", "elder", "elder"]),
                    dict(main="main_hall", sides=["bell", "bell", "teleport", "elder"]),
                    dict(main="library", sides=[work[0], work[1], work[2], work[3]]),
                    dict(main="teaching", sides=["inner_res"] * 4 + ["talisman", "formation"]),
                    dict(main="task", sides=["discipline", "refectory", "dorm", "dorm"]),
                    dict(gate=True, sides=["guard", "guard", "bell", "bell"])]
            off = [("row", ["dorm", "dorm", "dorm"], None), ("row", ["dorm", "dorm", "dorm"], None), ("row", ["dorm", "dorm"], None),
                   ("row", ["hut", "hut", "hut"], "field"), ("row", ["stable", "stable", "stable"], None),
                   ("row", ["elder", "elder", "elder"], None), ("row", ["elder", "elder"], None), ("row", ["alchemy_tower"], None),
                   ("row", ["inner_res", "inner_res", "inner_res"], None), ("row", ["inner_res", "inner_res", "inner_res"], None),
                   ("row", ["array_tower"], None), ("row", ["forge", "forge"], None)]
            spurs = ["trial_tower", "sword_pav", "pavilion", "pavilion", "pagoda", "pavilion", "array_tower", "pagoda"]
        rng.shuffle(off)                                  # (not every sect has all of them)
        off = off[:max(1, round(len(off) * rng.uniform(0.6, 1.0)))]
        out = []
        for _, kinds, extra in off:                       # a row on one terrace, a chain of terraces, or a small court
            if extra == "field" or len(kinds) == 1:
                out.append(("row", kinds, extra))
            else:
                out.append((rng.choices(("row", "chain", "court"), weights=(2, 4, 3 if ti >= 1 else 0))[0], kinds, extra))
        off = out
        styles = ("classic", "three", "pond", "pavilion", "wide", "narrow")
        weights = ((5, 1, 0, 2, 2, 2), (4, 2, 1, 2, 2, 1), (3, 3, 2, 2, 2, 1), (3, 3, 3, 2, 2, 1), (3, 3, 3, 2, 2, 1))[ti]
        for sp in axis:
            if not sp.get("gate"):
                sp["style"] = rng.choices(styles, weights=weights)[0]
        rng.shuffle(spurs)
        spurs = spurs[:max(1, round(len(spurs) * rng.uniform(0.5, 1.0)))]
        return axis, off, spurs

    def gate_layout(self, spec):
        """The 山门 plaza: the gate hall open through on the axis, a 牌坊 in front of it, a guardhouse or a tower either
        side."""
        g = Bld(self, "gate_hall")
        T = 4                                             # (a low wall round it, a row in from its edge)
        lay = dict(items=[], bs=5 + T, fs=7 + T, main="gate_hall", T=T)
        w_, n_, e_, s_ = g.fp
        vg = lay["bs"] + n_ + 1
        lay["items"].append((g, -(g.sx // 2), vg))
        v = vg + g.sz + s_ + 2
        W = g.sx // 2 + max(w_, e_) + 5
        sides = spec.get("sides", [])
        Wi = W
        for i, kind in enumerate(sides):                  # guardhouses / towers either side of the way, facing in
            side = -1 if i % 2 == 0 else 1
            b = Bld(self, kind, front="east" if side < 0 else "west")
            bw, bn, be, bs_ = b.fp
            back, frontr = (bw, be) if side < 0 else (be, bw)
            W = max(W, back + 1 + b.sx + frontr + 6)
        Wi = W
        W = Wi + T
        vv = v
        rowv = {-1: v, 1: v}
        for i, kind in enumerate(sides):
            side = -1 if i % 2 == 0 else 1
            b = Bld(self, kind, front="east" if side < 0 else "west")
            bw, bn, be, bs_ = b.fp
            u0 = -Wi + bw + 1 if side < 0 else Wi - be - 1 - (b.sx - 1)
            lay["items"].append((b, u0, rowv[side] + bn))
            rowv[side] += bn + b.sz + bs_ + 1
            vv = max(vv, rowv[side])
        span = min(Wi - 3, max(9, g.sx // 2 + 2))
        lay["paifang"] = (vv + 3, span)
        lay["W"], lay["D"] = W, vv + 3 + 5 + 7 + T
        return lay

    # -------------------------------------------------------------------------------------------- the axis

    def axis(self, specs, u_top=None, v_top=None, dry=False, tag="axis", plan=None):
        """The courts down an axis from a summit, a stair between each two. dry: only where they would go - a list of
        (spec, uc, v0, y, stair u) - or None when some of it lands on ground already taken."""
        ti = self.ti
        aw = (5, 7, 9, 9, 11)[ti]                         # the stair's width
        for s in specs:
            if "_lay" not in s:
                s["_lay"] = self.gate_layout(s) if s.get("gate") else self.court_layout(s)
        v_top = self.v_top if v_top is None else v_top
        uc = int(round(self.ridge(v_top))) if u_top is None else u_top
        v0 = v_top - specs[0]["_lay"]["D"] // 3           # (the top court's middle a little in front of the summit)
        prev = None
        out = []
        for i, s in enumerate(specs if plan is None else ()):
            lay = s["_lay"]
            if prev is None:
                y = self.level_of(uc - lay["W"], v0, uc + lay["W"], v0 + lay["D"] - 1)
            else:
                gap = 4
                drift = self.rng.choice((-1, 1)) * self.rng.randint(aw + 2, aw + 16) if self.rng.random() < 0.5 else 0
                for _ in range(30):                       # a stair long enough for the fall to the next court
                    vb = prev[2] + gap + 1
                    ucn = self.follow(prev[0], vb + lay["D"] // 2, lay["W"], drift, aw)
                    if ucn != prev[0]:
                        gap = max(gap, aw + 4)            # (room for the landing where the stair turns)
                        vb = prev[2] + gap + 1
                    y = self.level_of(ucn - lay["W"], vb, ucn + lay["W"], vb + lay["D"] - 1)
                    fall = prev[3] - y
                    need = abs(fall) - 1 + 3 * max(0, -(-abs(fall) // 16) - 1)
                    if ucn != prev[0]:
                        need += 2 * aw                    # (the two landings where it turns are held level)
                    if need <= gap:
                        break
                    if self.R is not None:                # (on the game's ground: the stair may not carry the axis
                        # out of the square it sampled - a big fall, or ground read as -65 where the chunks were
                        # not loaded, made it hundreds of blocks long. The court below stands on a terrace at the
                        # level a stair that fits reaches: cut into the slope or built up)
                        cap = self.R + 30 - prev[2] - 1 - sum(t["_lay"]["D"] + 5 for t in specs[i:]) + 5
                        if need > max(gap, cap):
                            gap = max(gap, cap)
                            vb = prev[2] + gap + 1
                            f = abs(fall)
                            while f > 0 and f - 1 + 3 * max(0, -(-f // 16) - 1) + (2 * aw if ucn != prev[0] else 0) > gap:
                                f -= 1
                            y = prev[3] - (f if fall > 0 else -f)
                            break
                    gap = need
                else:
                    y = prev[3] - (gap + 1 - 3 * max(0, -(-gap // 14) - 1))
                v0 = vb
                uc = ucn
            W, D = lay["W"], lay["D"]
            us = None
            if prev is not None:                         # the stair down from the court above: from its middle to this one's
                us = (prev[0], uc)
            out.append((s, uc, v0, y, us))
            prev = (uc, v0, v0 + D - 1, y, W)
        if plan is not None:
            out = plan
        if dry:
            if self.R is not None:
                for s, uc, v0, y, us in out:
                    if max(abs(uc) + s["_lay"]["W"], abs(v0), abs(v0 + s["_lay"]["D"])) > self.R + 30:
                        return None
            for k, (s, uc, v0, y, us) in enumerate(out):
                W, D = s["_lay"]["W"], s["_lay"]["D"]
                if not self.free(uc - W - 2, v0 - 2, uc + W + 2, v0 + D + 1):
                    return None
                if us is not None:
                    rows, al, hold = self.stair_rows(us[0], out[k - 1][2] + out[k - 1][0]["_lay"]["D"] - 1, us[1], v0, aw)
                    if not all(self.free(*self.row_rect(r, a, aw // 2 + 1)) for r, a in zip(rows, al)):
                        return None
            return out
        courts = []
        prev = None
        for i, (s, uc, v0, y, us) in enumerate(out):
            c = self.place_court(s, uc, v0, y, aw) if not s.get("gate") else self.place_gate(s, uc, v0, y, aw)
            if prev is not None:
                ok = self.stair(us, prev["v1"], c["v0"], prev["y"], c["y"], aw, (tag, i))
                if not ok:
                    raise RuntimeError("axis stair does not fit")
            courts.append(c)
            prev = c
        for i, c in enumerate(courts):                    # the walls, the 仪门 where the stair from below comes in
            if c.get("T"):
                fu = c["uc"] if i + 1 < len(out) else None
                bu = c["uc"] if i > 0 else None
                if out[i][0].get("gate"):                 # (the 山门 plaza: a low wall, open in front to the approach)
                    self.pending_walls.append((c, c["uc"], bu, aw, False, 4))
                else:
                    self.pending_walls.append((c, fu, bu, aw))   # (after the paths: openings only where one goes out)
        return courts

    def court_walls(self, c, fu, bu, aw, gate=True, sh=None):
        """A court's wall (three deep, inside its edge): a 仪门 in the front wall where the stair from below comes up
        (fu), an opening in the back wall where the stair up leaves (bu), openings in the side walls where the paths
        out of its strips go."""
        from siteplan import wall_pieces
        # (the wall a row in from the edge: a 3-deep wall's footprint in compose is 5 deep, its ground at the court's
        # level - right on the edge it took the top step of the stair below)
        u0, u1, v0, v1 = c["u0"] + 1, c["u1"] - 1, c["v0"] + 1, c["v1"] - 1
        T = 3
        mat = dict(walls=self.mat["wall"], theme=self.mat["theme"], base=self.mat["base"], sh=sh or 5 + min(self.ti, 2))
        gw = odd(max(9, aw + 2)) if gate else aw + 2
        gaps = [(fu - gw // 2, fu + gw // 2)] if fu is not None else []
        for a0, a1 in wall_pieces(u0, u1, gaps):          # the front wall and its gate
            self.ground("wall", a0, v1 - T + 1, min(a1, u1), v1, c["y"], front="south", **mat)
        if fu is not None and gate:
            self.ground("gate", fu - gw // 2, v1 - T + 1, fu + gw // 2, v1, c["y"], front="south", **mat)
        gaps = [(bu - aw // 2 - 1, bu + aw // 2 + 1)] if bu is not None else []
        for a0, a1 in wall_pieces(u0, u1, gaps):          # the back wall
            self.ground("wall", a0, v0, min(a1, u1), v0 + T - 1, c["y"], front="south", **mat)
        for side, ua, fr in ((-1, u0, "east"), (1, u1 - T + 1, "west")):   # the side walls, open where a path goes out
            gaps = sorted({(p["v"] - 2, p["v"] + 2) for p in self.ports if p["court"] is c and p["used"] and p["side"] == side})
            for a0, a1 in wall_pieces(v0 + T, v1 - T, gaps):
                self.ground("wall", ua, a0, ua + T - 1, min(a1, v1 - T), c["y"], front=fr, **mat)

    def approach(self):
        """The way up to the 山门 from the foot: a stone path down the axis from the plaza's front, stairs where the
        ground falls; from 金丹 up a 牌坊 on a landing at its foot. Where it ends is where the sect starts (the walk
        test starts there)."""
        rng = self.rng
        c = self.gate
        aw = (5, 7, 9, 9, 11)[self.ti]
        L = odd(rng.randint(14, 22) + 6 * self.ti)
        u = c["uc"]
        if self.R is not None:
            L = max(6, min(L, self.R + 30 - c["v1"]))
        foot = 7 if self.ti >= 2 else 0                   # (the landing the 牌坊 stands on)
        cells = [(u, v) for v in range(c["v1"] + 1, c["v1"] + 1 + L)]
        if not self.free(u - aw // 2 - 4, c["v1"] + 1, u + aw // 2 + 4, c["v1"] + L + 1, ok=(c["id"],)):
            self.start = (u, c["v1"] - 2)
            return
        want = [self.hs(*p) for p in cells]
        y1 = int(round(self.hs(u, c["v1"] + L + 1)))
        y1 = max(c["y"] - (L - foot), min(c["y"] + (L - foot), y1))
        hold = [i >= L - foot for i in range(L)]
        ys = self.profile(want, c["y"], y1, hold=hold)
        if ys is None:
            ys = [c["y"]] * L
        aid = self.new_id()
        self.run(cells, ys, aw, "v", aid)
        if foot:
            yf = ys[-1]
            span = aw // 2 + 4
            vf0 = c["v1"] + 1 + L - foot
            self.ground("terrace", u - span - 1, vf0, u + span + 1, vf0 + foot - 1, yf, variant=self.mat["court"])
            self.claim(u - span - 1, vf0, u + span + 1, vf0 + foot - 1, aid, margin=0)
            self.ground("paifang", u - span, vf0 + 1, u + span, vf0 + 5, yf, sh=self.rng.choice((6, 7)) + self.ti,
                        walls=self.mat["wall"], theme=self.mat["theme"])
            self.nb += 1
        self.start = (u, c["v1"] + L)

    def follow(self, uprev, v, W, drift=0, aw=5):
        """Where the next court sits across: on the ridge (the highest ground across) near the court above, pulled a
        little towards `drift` (the axis steps aside now and then even on a straight ridge). Either in line with the
        one above or a stair's width and more aside (room for the landing where the stair turns)."""
        best = None
        for du in range(-36, 37, 3):
            if 0 < abs(du) <= aw + 1:
                continue
            u = uprev + du
            val = sum(self.hs(u + k, v) for k in (-W // 2, 0, W // 2)) / 3 - 0.06 * abs(du) - (0.08 * abs(du - drift) if drift else 0)
            if best is None or val > best[0]:
                best = (val, u)
        return best[1]

    def place_gate(self, spec, uc, v0, y, aw):
        lay = spec["_lay"]
        c = self.place_court(spec, uc, v0, y, aw, ornaments=False)
        pv, span = lay["paifang"]
        self.ground("paifang", uc - span, v0 + pv - 2, uc + span, v0 + pv + 2, y, sh=self.rng.choice((7, 8)) + self.ti,
                    walls=self.mat["wall"], theme=self.mat["theme"])
        self.nb += 1
        g, gu, gv = lay["items"][0]                       # a paved way up from the 牌坊 to the gate hall
        hw = aw // 2
        if pv - 3 - (gv + g.sz + g.fp[3]) >= 2:
            self.ground("paving", uc - hw, v0 + gv + g.sz + g.fp[3] + 1, uc + hw, v0 + pv - 3, y)
        self.gate = c
        return c

    # -------------------------------------------------------------------------------------------- off the axis

    def path_to(self, port, rect_fn, y_fn, owner, width=3, gap_range=(4, 30)):
        """A side terrace off a port: tries distances out from the court; rect_fn(u_near) -> (u0, v0, u1, v1) of the
        terrace when its near edge is at u_near; y_fn(rect) its level. Returns (rect, y) or None."""
        side = port["side"]
        for gap in range(gap_range[0], gap_range[1] + 1, 2):
            u_near = port["u"] + side * gap
            rect = rect_fn(u_near)
            u0, v0, u1, v1 = rect
            if self.R is not None and (max(abs(u0), abs(u1), abs(v0), abs(v1)) > self.R + 40):
                return None
            if not self.free(u0 - 1, v0 - 1, u1 + 1, v1 + 1):
                continue
            ua, ub = sorted((port["u"], u_near - side))
            half = width // 2
            if not self.free(ua, port["v"] - half, ub, port["v"] + half, ok=(port["court"]["id"],)):
                continue
            y = y_fn(rect)
            cells = [(u, port["v"]) for u in (range(port["u"], u_near, side))]
            want = [self.hs(u, port["v"]) for u, _ in cells]
            ys = self.profile(want, port["y"], y)
            if ys is None:
                yy = port["y"] + max(-len(cells), min(len(cells), y - port["y"]))
                if abs(yy - y) > 6:
                    continue
                y = yy
                ys = self.profile(want, port["y"], y)
                if ys is None:
                    continue
            return rect, y, cells, ys
        return None

    def side_row(self, kinds, extra):
        """A terrace of buildings in a row facing down the mountain, off a court's strip; a field round huts."""
        rng = self.rng
        blds = [Bld(self, k) for k in kinds]
        fs = 6
        gapb = 2
        width = 0
        for b in blds:
            w_, n_, e_, s_ = b.fp
            width += w_ + b.sx + e_ + gapb
        width += 2
        back = max(b.fp[1] for b in blds) + 2
        depth = back + max(b.sz + b.fp[3] for b in blds) + fs
        if extra == "field":
            depth += 10
        ports = [p for p in self.ports if not p["used"]]
        rng.shuffle(ports)
        ports.sort(key=lambda p: (p["strip"] != "front") * 0.5 + rng.random())
        for port in ports:
            side = port["side"]
            pv = port["v"]

            def rect_fn(u_near, side=side, pv=pv):
                ua = u_near if side > 0 else u_near - width + 1
                v1 = pv + fs // 2                         # its front strip on the path's line
                return (ua, v1 - depth + 1, ua + width - 1, v1)

            res = self.path_to(port, rect_fn, lambda r: self.level_of(*r), ("path", self.nid + 1))
            if not res:
                continue
            rect, y, cells, ys = res
            tid = self.new_id()
            u0, v0, u1, v1 = rect
            self.ground("terrace", u0, v0, u1, v1, y, variant=1 if extra == "field" else self.mat["court"])
            self.claim(u0, v0, u1, v1, tid)
            self.run(cells, ys, 3, "u", tid)
            u = u0 + 1
            for b in blds:
                w_, n_, e_, s_ = b.fp
                u += w_
                self.put(b, u, v0 + back, y)
                self.arrivals.append((b, u, v0 + back, cells[-1]))
                u += b.sx + e_ + gapb
            port["used"] = True
            t = dict(id=tid, u0=u0, u1=u1, v0=v0, v1=v1, y=y, uc=(u0 + u1) // 2, bs=0, fs=fs, spec={})
            self.terraces.append(t)
            if extra == "field":
                self.fields(t)
            for s2 in (-1, 1):                            # its front strip leads on outwards
                if s2 == side:
                    self.ports.append(dict(court=t, side=s2, v=pv, u=(u0 - 1) if s2 < 0 else (u1 + 1), y=y, used=False, strip="front"))
            return True
        return False

    def side_court(self, kinds):
        """A small court off a court's strip, turned to face it (its depth along the contour): a hall at its back,
        the others down its sides facing in, a paved way to its front, where the path comes in."""
        rng = self.rng
        spec = dict(main=kinds[0], sides=list(kinds[1:]), style=rng.choice(("classic", "classic", "wide", "pavilion")),
                    walled=False)
        lay = self.court_layout(spec)
        W, D = lay["W"], lay["D"]
        ports = [p for p in self.ports if not p["used"]]
        rng.shuffle(ports)
        for port in ports:
            side = port["side"]
            k = 1 if side > 0 else 3                      # (its front towards the port: west of it, or east)
            rot = ROT[k]
            for gap in range(4, 34, 3):
                u_near = port["u"] + side * gap
                cu = u_near + (D - 1) if side > 0 else u_near - (D - 1)
                cv_ = port["v"]

                def at(a, b):
                    x, z = rot(a, b)
                    return cu + x, cv_ + z
                c0, c1 = at(-W, 0), at(W, D - 1)
                rect = (min(c0[0], c1[0]), min(c0[1], c1[1]), max(c0[0], c1[0]), max(c0[1], c1[1]))
                if self.R is not None and max(map(abs, rect)) > self.R + 30:
                    break
                if not self.free(rect[0] - 2, rect[1] - 2, rect[2] + 2, rect[3] + 2):
                    continue
                y = self.level_of(*rect)
                tid = self.new_id()
                t = dict(id=tid, u0=rect[0], v0=rect[1], u1=rect[2], v1=rect[3], y=y, uc=(rect[0] + rect[2]) // 2, bs=0, fs=0)
                pb = dict(court=t, side=-side, u=u_near - side, v=port["v"], y=y)
                r = self.route(port, pb)
                if r is None:
                    continue
                rows, al, ys = r
                self.ground("terrace", *rect, y, variant=self.mat["court"])
                self.claim(*rect, tid)
                self.terraces.append(t)
                self.run(rows, ys, 3, al, tid)
                front = _VDIR[rot(0, 1)]
                for b, a0, b0 in lay["items"]:
                    nb = b.turned(self, _VDIR[rot(*_DIRV[b.front])])
                    p0, p1 = at(a0, b0), at(a0 + b.sx - 1, b0 + b.sz - 1)
                    self.put(nb, min(p0[0], p1[0]), min(p0[1], p1[1]), y)
                if lay["main"] and not lay.get("pavilion"):   # a paved way from the hall to the front
                    m, mu, mv = lay["items"][0]
                    b0 = mv + m.sz + m.fp[3] + 1
                    if D - 1 - b0 >= 4:
                        p0, p1 = at(-1, b0), at(1, D - 1)
                        self.ground("paving", min(p0[0], p1[0]), min(p0[1], p1[1]), max(p0[0], p1[0]), max(p0[1], p1[1]), y)
                port["used"] = True
                self.nside = getattr(self, "nside", 0) + 1
                return True
        return False

    def fields(self, t, steps=None):
        """灵田: beds stepping down the slope below a terrace (each a strip of its own a little lower, rows of crops
        and a ditch on it), as far as the ground lets them."""
        rng = self.rng
        steps = steps or 2 + rng.randint(1, 2 + min(self.ti, 2))
        u0, u1 = t["u0"] + 2, t["u1"] - 2
        if u1 - u0 < 6:
            return 0
        v, y, n = t["v1"] + 1, t["y"], 0
        prev_id = t["id"]
        for _ in range(steps):
            d = rng.choice((5, 7, 7))
            rect = (u0, v, u1, v + d - 1)
            if not self.free(rect[0] - 1, rect[1], rect[2] + 1, rect[3] + 1, ok=(t["id"], prev_id)):
                break
            yl = max(y - 4, min(y, self.level_of(*rect)))
            fid = self.new_id()
            self.ground("terrace", *rect, yl, variant=1)
            self.ground("ornament", rect[0] + 1, rect[1] + 1, rect[2] - 1, rect[3] - 1, yl, variant=9)
            self.claim(*rect, fid)
            v, y, n, prev_id = v + d, yl, n + 1, fid
        return n

    def groves(self, n, spots=None):
        """林: groves of trees round the courts and terraces and beside the long ways - patches of their own on
        the slope, each at the ground's level there (where it is not too steep to make one)."""
        rng = self.rng
        made, tries = 0, 0
        spots = spots or list(self.terraces) + list(self.path_pieces) * 2   # (round the terraces, and along the long ways)
        while made < n and tries < n * 80 and spots:
            tries += 1
            t = rng.choice(spots)
            w, d = odd(rng.randint(11, 21)), odd(rng.randint(9, 17))
            side = rng.choice(("w", "e", "n", "s"))
            g = 0                                         # (against it: its ground joins the terrace's, no island)
            cu, cv_ = rng.randint(t["u0"], t["u1"]), rng.randint(t["v0"], t["v1"])
            if side == "w":
                rect = (t["u0"] - g - w, cv_ - d // 2, t["u0"] - g - 1, cv_ + d // 2)
            elif side == "e":
                rect = (t["u1"] + g + 1, cv_ - d // 2, t["u1"] + g + w, cv_ + d // 2)
            elif side == "n":
                rect = (cu - w // 2, t["v0"] - g - d, cu + w // 2, t["v0"] - g - 1)
            else:
                rect = (cu - w // 2, t["v1"] + g + 1, cu + w // 2, t["v1"] + g + d)
            if self.R is not None and max(map(abs, rect)) > self.R + 30:
                continue
            if not self.free(rect[0] - 1, rect[1] - 1, rect[2] + 1, rect[3] + 1, ok=(t["id"],)):
                continue
            if any(r["x0"] <= rect[2] + 1 and rect[0] - 1 <= r["x1"] and r["z0"] <= rect[3] + 1 and rect[1] - 1 <= r["z1"]
                   and not (r["x0"] <= t["u0"] and r["x1"] >= t["u1"] and r["z0"] <= t["v0"] and r["z1"] >= t["v1"])
                   for r in self.rooms):                  # (nothing laid there or right beside it, but the terrace itself)
                continue
            hs = [self.H(u, v) for u in (rect[0], rect[2]) for v in (rect[1], rect[3])]
            if max(hs) - min(hs) > 12:                    # (too steep: a grove would be a tall platform)
                continue
            gid = self.new_id()
            self.ground("terrace", *rect, self.level_of(*rect), variant=5)
            self.claim(*rect, gid)
            made += 1
        return made

    def chain(self, kinds):
        """Buildings each on a terrace of its own, one after another out along the slope from a court's strip, each a
        few blocks up or down from the last (the ground there), a path from one to the next."""
        rng = self.rng
        ports = [p for p in self.ports if not p["used"]]
        rng.shuffle(ports)
        for port in ports[:6]:
            prev, side, placed = port, port["side"], 0
            for kind in kinds:
                b = Bld(self, kind)
                w_, n_, e_, s_ = b.fp
                tw = odd(w_ + b.sx + e_ + 4)
                td = n_ + b.sz + s_ + 7
                done = False
                for _ in range(8):
                    gap = rng.randint(3, 9)
                    pv = prev["v"] + rng.randint(-12, 12)
                    u_near = prev["u"] + side * gap
                    u0 = u_near if side > 0 else u_near - tw + 1
                    rect = (u0, pv - (td - 4), u0 + tw - 1, pv + 3)
                    if self.R is not None and max(map(abs, rect)) > self.R + 30:
                        continue
                    if not self.free(rect[0] - 2, rect[1] - 2, rect[2] + 2, rect[3] + 2):
                        continue
                    y = self.level_of(*rect)
                    tid = self.new_id()
                    t = dict(id=tid, u0=rect[0], v0=rect[1], u1=rect[2], v1=rect[3], y=y, uc=(rect[0] + rect[2]) // 2, bs=0, fs=0)
                    pb = dict(court=t, side=-side, u=(rect[0] - 1) if side > 0 else (rect[2] + 1), v=pv, y=y)
                    r = self.route(prev, pb)
                    if r is None:
                        continue
                    rows, al, ys = r
                    self.ground("terrace", *rect, y, variant=rng.choice((1, self.mat["court"])))
                    self.claim(*rect, tid)
                    self.terraces.append(t)
                    self.run(rows, ys, 3, al, tid)
                    self.put(b, rect[0] + 2 + w_, rect[1] + 1 + n_, y)
                    self.arrivals.append((b, rect[0] + 2 + w_, rect[1] + 1 + n_, rows[-1]))
                    prev["used"] = True
                    prev = dict(court=t, side=side, u=(rect[2] + 1) if side > 0 else (rect[0] - 1), v=pv, y=y, used=False,
                                strip="front")
                    placed += 1
                    done = True
                    break
                if not done:
                    break
            if placed:
                self.ports.append(prev)                   # (the last one leads on)
                return True
        return False

    def spur(self, kind, reach=70, ports=None):
        """A tower or pavilion on a high point near the courts: a small terrace on it, a path out to it from a court
        (along, round a corner, up)."""
        rng = self.rng
        b = Bld(self, kind)
        # (turned to face the court it is reached from: the path comes in at the side of its terrace towards it, so
        # its door is there, not a blank wall; the terrace big enough for it either way)
        turn = {sd: b.turned(self, "east" if sd > 0 else "west") for sd in (-1, 1)}
        m = 2
        tw = odd(max(t.fp[0] + t.sx + t.fp[2] for t in turn.values()) + 2 * m)
        td = odd(max(t.fp[1] + t.sz + t.fp[3] for t in turn.values()) + 2 * m)
        cands = []
        for port in [p for p in (ports if ports is not None else self.ports) if not p["used"]]:
            side = port["side"]
            for du in range(tw // 2 + 6, reach, 5):
                for dv in range(-50, 31, 5):
                    u, v = port["u"] + side * du, port["v"] + dv
                    h = self.hs(u, v, 4)
                    cands.append((h - port["y"] - 0.12 * (du + abs(dv)) + rng.random() * 3, u, v, port))
        cands.sort(key=lambda t: -t[0])
        tried = 0
        for score, u, v, port in cands:
            if tried > 60:
                break
            rect = (u - tw // 2, v - td // 2, u + tw // 2, v + td // 2)
            if not self.free(rect[0] - 2, rect[1] - 2, rect[2] + 2, rect[3] + 2):
                continue
            tried += 1
            y = self.level_of(*rect)
            t = dict(id=self.new_id(), u0=rect[0], v0=rect[1], u1=rect[2], v1=rect[3], y=y, uc=u, bs=0, fs=td // 2 * 2 + 1)
            side_in = -port["side"]                       # (its port on the side towards the court)
            tp = dict(court=t, side=side_in, v=v, u=(rect[0] - 1) if side_in < 0 else (rect[2] + 1), y=y)
            r = self.route(port, tp)
            if r is None:
                continue
            rows, al, ys = r
            self.ground("terrace", *rect, y, variant=rng.choice((1, self.mat["court"])))
            self.claim(*rect, t["id"])
            self.terraces.append(t)
            self.run(rows, ys, 3, al, t["id"])
            nb = turn[side_in]
            x0, z0 = rect[0] + m + nb.fp[0], rect[1] + m + nb.fp[1]
            self.put(nb, x0, z0, y)
            self.arrivals.append((nb, x0, z0, rows[-1]))
            port["used"] = True
            return True
        return False

    # -------------------------------------------------------------------------------------------- 化神: more peaks

    PEAKS_OLD = [("剑峰", [dict(main="sword_pav", sides=["elder", "elder"]), dict(main="teaching", sides=["inner_res"] * 4)]),
             ("丹峰", [dict(main="alchemy_tower", sides=["alchemy", "alchemy"]),
                      dict(main="task", sides=["alchemy", "inner_res", "inner_res", "hut"])]),
             ("器峰", [dict(main="trial_tower", sides=["forge", "forge"]), dict(main="task", sides=["forge", "forge", "dorm", "dorm"])]),
             ("阵峰", [dict(main="array_tower", sides=["formation", "formation"]),
                      dict(main="teaching", sides=["talisman", "talisman", "inner_res", "inner_res"])]),
             ("灵兽峰", [dict(main="elder", sides=["stable", "stable"]), dict(main="refectory", sides=["stable", "stable", "hut", "hut"])]),
             ("长老峰", [dict(main="ancestor", sides=["elder", "elder"]), dict(main="library", sides=["elder", "elder", "elder", "elder"])])]

    # each side peak's courts, and what goes round them: towers on its spurs, beds stepping down, pens, groves
    PEAKS = [("剑峰", [dict(main="sword_pav", sides=["trial_tower", "trial_tower"], style="wide"),
                      dict(main="teaching", sides=["sword_pav", "inner_res", "inner_res", "sword_pav"])],
              dict(spurs=["sword_pav", "pagoda", "trial_tower"])),
             ("丹峰", [dict(main="alchemy_tower", sides=["alchemy", "alchemy"], style="cauldrons"),
                      dict(main="task", sides=["alchemy", "alchemy", "inner_res", "inner_res"])],
              dict(fields=2, spurs=["alchemy_tower"])),
             ("器峰", [dict(main="trial_tower", sides=["forge", "forge"], style="cauldrons"),
                      dict(main="task", sides=["forge", "forge", "dorm", "dorm"])],
              dict(spurs=["pagoda"])),
             ("阵峰", [dict(main="array_tower", sides=["formation", "formation"]),
                      dict(main="teaching", sides=["talisman", "talisman", "inner_res", "inner_res"])],
              dict(spurs=["array_tower", "array_tower", "pavilion"])),
             ("灵兽峰", [dict(main="elder", sides=["stable", "stable"], style="wide"),
                       dict(main="refectory", sides=["stable", "stable", "hut", "hut"])],
              dict(pens=2, fields=1)),
             ("长老峰", [dict(main="ancestor", sides=["elder", "elder"], style="pond"),
                       dict(main="library", sides=["elder", "elder", "elder", "elder"])],
              dict(spurs=["pavilion", "pavilion"], groves=4))]

    def pen(self, ports):
        """灵兽圈: a fenced pen on the slope by a court - a grass terrace, a low log fence round it open where its path
        comes in, hay and a feeding trough inside."""
        rng = self.rng
        for port in [p for p in ports if not p["used"]]:
            side = port["side"]
            for gap in range(4, 30, 3):
                w, d = odd(rng.randint(17, 23)), odd(rng.randint(13, 17))
                u_near = port["u"] + side * gap
                u0 = u_near if side > 0 else u_near - w + 1
                pv = port["v"] + rng.randint(-4, 4)
                rect = (u0, pv - d // 2, u0 + w - 1, pv + d // 2)
                if self.R is not None and max(map(abs, rect)) > self.R + 30:
                    break
                if not self.free(rect[0] - 2, rect[1] - 2, rect[2] + 2, rect[3] + 2):
                    continue
                y = self.level_of(*rect)
                tid = self.new_id()
                t = dict(id=tid, u0=rect[0], v0=rect[1], u1=rect[2], v1=rect[3], y=y, uc=(rect[0] + rect[2]) // 2, bs=0, fs=0)
                pb = dict(court=t, side=-side, u=(rect[0] - 1) if side > 0 else (rect[2] + 1), v=pv, y=y)
                r = self.route(port, pb)
                if r is None:
                    continue
                rows, al, ys = r
                self.ground("terrace", *rect, y, variant=1)
                self.claim(*rect, tid)
                self.terraces.append(t)
                self.run(rows, ys, 3, al, tid)
                from siteplan import wall_pieces
                mat = dict(walls="log", theme="wood", base=self.mat["base"], sh=4)
                a0, a1, b0, b1 = rect[0] + 1, rect[2] - 1, rect[1] + 1, rect[3] - 1
                for x0, x1 in wall_pieces(a0, a1):        # the fence: a row in from the edge, three deep
                    self.ground("wall", x0, b0, min(x1, a1), b0 + 2, y, front="south", **mat)
                    self.ground("wall", x0, b1 - 2, min(x1, a1), b1, y, front="south", **mat)
                near = a0 if side > 0 else a1 - 2         # (open on the side its path comes in)
                far = a1 - 2 if side > 0 else a0
                for z0, z1 in wall_pieces(b0 + 3, b1 - 3, [(pv - 2, pv + 2)]):
                    self.ground("wall", near, z0, near + 2, min(z1, b1 - 3), y, front="east", **mat)
                for z0, z1 in wall_pieces(b0 + 3, b1 - 3):
                    self.ground("wall", far, z0, far + 2, min(z1, b1 - 3), y, front="east", **mat)
                cu = (a0 + a1) // 2
                for k, var in enumerate((7, 3)):          # 草垛, a trough (缸)
                    hu = cu + (k * 2 - 1) * 2
                    self.ground("ornament", hu - 1, b0 + 4, hu + 1, b0 + 6, y, variant=var)
                port["used"] = True
                return True
        return False

    def peaks(self, n):
        """Summits away from the courts: the highest points of a coarse grid round the sect, each the highest within
        sixty blocks, nothing taken near it."""
        us = [u for u, v in self.occ]
        vs = [v for u, v in self.occ]
        u0, u1, v0, v1 = min(us) - 420, max(us) + 420, min(vs) - 60, max(vs) + 40
        if self.R is not None:
            u0, u1, v0, v1 = max(u0, -self.R), min(u1, self.R), max(v0, -self.R), min(v1, self.R)
        grid = {}
        for u in range(u0, u1 + 1, 12):
            for v in range(v0, v1 + 1, 12):
                grid[(u, v)] = self.hs(u, v, 5)
        out = []
        for (u, v), h in sorted(grid.items(), key=lambda t: -t[1]):
            if any(abs(u - a) < 60 and abs(v - b) < 60 for a, b, _ in out):
                continue
            if any(grid.get((u + du, v + dv), -1e9) > h for du in (-12, 0, 12) for dv in (-12, 0, 12)):
                continue
            if not self.free(u - 40, v - 40, u + 40, v + 40):
                continue
            out.append((u, v, h))
        return out[:n + 3]

    def satellites(self):
        """化神: a compound on each of the peaks round the main one - two courts down from its top - and a path from
        its lower court to a court of the main peak's."""
        rng = self.rng
        progs = list(self.PEAKS)
        rng.shuffle(progs)
        done = 0
        want = rng.choice((3, 4))
        for u, v, h in self.peaks(want):
            if done >= want or not progs:
                break
            name, specs, more = progs[0]
            specs = [dict(sp, walled=False) if sp.get("style") else dict(sp, walled=False, style=rng.choice(("classic", "three", "wide")))
                     for sp in specs]
            plan = self.axis(specs, u_top=u, v_top=v, dry=True)
            if plan is None:
                specs = specs[:1]
                plan = self.axis(specs, u_top=u, v_top=v, dry=True)
            if plan is None:
                continue
            # a path from the lowest court there to a court of the main peak (a port on the side facing it)
            s, uc, v0, y, us = plan[-1]
            W, D = s["_lay"]["W"], s["_lay"]["D"]
            mains = [p for p in self.ports if not p["used"] and (uc - p["u"]) * p["side"] > 0]
            mains.sort(key=lambda p: abs(p["u"] - uc) + abs(p["v"] - (v0 + D)) + 2 * abs(p["y"] - y))
            fake = dict(id=-1, u0=uc - W, u1=uc + W, v0=v0, v1=v0 + D - 1, y=y, bs=s["_lay"]["bs"], fs=s["_lay"]["fs"])
            found = None
            for mp in mains[:8]:
                for sp in self.ports_of(fake):
                    if sp["side"] != -mp["side"]:
                        continue
                    r = self.route(mp, sp, ok=(-1,))
                    if r:
                        found = (mp, sp, r)
                        break
                if found:
                    break
            if not found:
                continue
            courts = self.axis(specs, u_top=u, v_top=v, tag=("peak", done), plan=plan)
            mp, sp, (rows, al, ys) = found
            low = courts[-1]
            self.run(rows, ys, 3, al, self.new_id())
            mp["used"] = True
            for p in self.ports:
                if p["court"] is low and p["side"] == sp["side"] and p["strip"] == sp["strip"]:
                    p["used"] = True
            self.peak_names.append(name)
            progs.pop(0)
            done += 1
            mine = [p for p in self.ports if p["court"] in courts]   # (its own character: round its own courts)
            for kind in more.get("spurs", ()):
                self.spur(kind, reach=60, ports=mine)
            for _ in range(more.get("pens", 0)):
                self.pen(mine)
            for _ in range(more.get("fields", 0)):
                for c in courts[::-1]:
                    if self.fields(c):
                        break
            self.peak_more.append((courts, more.get("groves", 0)))
        return done

    # -------------------------------------------------------------------------------------------- all of it

    def plan(self):
        axis, off, spurs = self.programme()
        if self.ti >= 2:
            for s in axis:
                if not s.get("gate"):
                    s["walled"] = True
        for s in axis:                                    # (sizes first: how long the axis is)
            s["_lay"] = self.gate_layout(s) if s.get("gate") else self.court_layout(s)
        A = (70, 100, 150, 185, 210)[self.ti] * self.rng.uniform(0.85, 1.15)   # (how high the mountain rises)
        length = sum(s["_lay"]["D"] for s in axis) + 1.25 * A + 40
        self.setup_ground(length, A)
        if not self.anchored:
            self.v_top = -int(length * 0.42)
        self.rng = random.Random(f"sect/{self.tier}/{self.seed}/plan")   # (the layout's own stream)
        self.axis(axis)
        self.approach()
        if self.ti == 4:
            self.satellites()
        for kind in spurs:
            self.spur(kind)
        for how, kinds, extra in off:
            done = False
            if how == "chain":
                done = self.chain(kinds)
            elif how == "court":
                done = self.side_court(kinds)
            if not done:
                self.side_row(kinds, extra)
        for w in self.pending_walls:
            self.court_walls(*w)
        self.ngroves = self.groves(3 + 3 * self.ti + self.rng.randint(0, 3))
        for courts, n in self.peak_more:
            if n:
                self.ngroves += self.groves(n, spots=courts)
        self.banks()
        fp_save()
        return self.design()

    def banks(self, seg=41, deep=2):
        """Where a terrace is cut into the slope (the ground beside it three or more higher), a strip of grass along
        that side at the height of the ground there: terrain_ground faces the drop between with masonry - a retaining
        wall up the cut, instead of a bare bank of earth. Kept off every other piece of ground and its neighbours."""
        used = set()
        n = 0
        for t in self.terraces:
            if t not in self.courts and (t["u1"] - t["u0"]) * (t["v1"] - t["v0"]) < 900:
                continue                                  # (the small terraces keep their earth banks: fewer pieces)
            y = t["y"]
            edges = (("v", t["v0"], -1, t["u0"], t["u1"]), ("v", t["v1"], 1, t["u0"], t["u1"]),
                     ("u", t["u0"], -1, t["v0"], t["v1"]), ("u", t["u1"], 1, t["v0"], t["v1"]))
            for axis, e, out, a0, a1 in edges:
                for s0 in range(a0, a1 + 1, seg):
                    s1 = min(a1, s0 + seg - 1)
                    cells = []
                    for a in range(s0, s1 + 1):
                        for k in range(1, deep + 1):
                            cells.append((a, e + out * k) if axis == "v" else (e + out * k, a))
                    ok = []
                    for c in cells:
                        if c in used:
                            continue
                        if c in self._covered():
                            continue
                        near = [(c[0] + du, c[1] + dv) for du in (-1, 0, 1) for dv in (-1, 0, 1)]
                        if any(q in self._covered() and not (t["u0"] <= q[0] <= t["u1"] and t["v0"] <= q[1] <= t["v1"]) for q in near):
                            continue
                        ok.append(c)
                    if len(ok) < deep * 6:
                        continue
                    h = int(min(self.H(*c) for c in ok))
                    if h - y < 3:
                        continue
                    h = min(h, y + 30)
                    # the strip: the longest straight run of good cells along the edge, both rows of it
                    along = sorted({c[0] if axis == "v" else c[1] for c in ok if all(
                        ((c[0] if axis == "v" else e + out * k), (e + out * k if axis == "v" else c[1])) in ok for k in range(1, deep + 1))})
                    runs, cur = [], []
                    for a in along:
                        if cur and a != cur[-1] + 1:
                            runs.append(cur)
                            cur = []
                        cur.append(a)
                    if cur:
                        runs.append(cur)
                    for rn in runs:
                        if len(rn) < 3:
                            continue
                        if axis == "v":
                            u0, u1, v0, v1 = rn[0], rn[-1], min(e + out, e + out * deep), max(e + out, e + out * deep)
                        else:
                            u0, u1, v0, v1 = min(e + out, e + out * deep), max(e + out, e + out * deep), rn[0], rn[-1]
                        self.ground("terrace", u0, v0, u1, v1, h, variant=4)   # (compose.BANK: no steps up onto it)
                        for u in range(u0, u1 + 1):
                            for v in range(v0, v1 + 1):
                                used.add((u, v))
                        n += 1
        return n

    def _covered(self):
        """The cells some piece of the design covers (computed once, after the layout)."""
        if getattr(self, "_cov", None) is None:
            self._cov = set()
            for r in self.rooms:
                for u in range(r["x0"], r["x1"] + 1):
                    for v in range(r["z0"], r["z1"] + 1):
                        self._cov.add((u, v))
        return self._cov

    def design(self):
        """The rooms turned into the design's coordinates (the mountain falling towards the design's front, +z, unless
        the given ground falls another way), as a design for the room table."""
        rot = ROT[self.k]
        out = []
        for r in self.rooms:
            r = dict(r)
            a = rot(r["x0"], r["z0"])
            b = rot(r["x1"], r["z1"])
            r["x0"], r["x1"] = min(a[0], b[0]), max(a[0], b[0])
            r["z0"], r["z1"] = min(a[1], b[1]), max(a[1], b[1])
            dv = _DIRV[r.get("front", "south")]
            r["front"] = _VDIR[rot(*dv)]
            for k in [k for k in r if k.startswith("_")]:
                r.pop(k)
            out.append(r)
        if GROUP:                                       # 地块: the ground pieces a few rooms (compose takes them apart)
            import compose
            out = compose.group_ground(out)
        x0 = min(r["x0"] for r in out)
        x1 = max(r["x1"] for r in out)
        z0 = min(r["z0"] for r in out)
        z1 = max(r["z1"] for r in out)
        g = self.gate
        gx, gz = rot((g["u0"] + g["u1"]) // 2, g["v1"])
        st = rot(*getattr(self, "start", ((g["u0"] + g["u1"]) // 2, g["v1"] - 2)))
        return dict(name=f"宗门·{self.name}（{TIER_CN[self.tier]}·{self.fam['cn']}）", kind="sect", jin=0, seed=self.seed, tier=self.tier,
                    look=0, size=[x1 - x0 + 1, z1 - z0 + 1], rooms=out, terrain=True, anchored=self.anchored,
                    origin=[0, 0] if self.anchored else [gx, gz], y_ref=g["y"], buildings=self.nb,
                    courts=len(self.courts), start=list(st), bridges=getattr(self, "nbridge", 0))


def sect(seed, tier="jindan", heights=None):
    return Sect(seed, tier, heights).plan()


if __name__ == "__main__":
    import time
    t0 = time.time()
    d = sect(int(sys.argv[1]) if len(sys.argv) > 1 else 1, sys.argv[2] if len(sys.argv) > 2 else "jindan")
    print(d["name"], len(d["rooms"]), "rooms,", d["buildings"], "buildings,", d["courts"], "courts, size", d["size"],
          f"{time.time() - t0:.1f}s")
    if len(sys.argv) > 3:
        with open(sys.argv[3], "w", encoding="utf-8") as f:
            json.dump(d, f, ensure_ascii=False, indent=1)
