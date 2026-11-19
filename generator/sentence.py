"""一句话出建筑, without a model: the words of a Chinese sentence picked out (庑殿、重檐、三层、五开间、须弥座、黄瓦、
走兽、宫城、二进院……) and turned into a 单体建造 design - the same rooms and settings as the room table, so everything
comes out editable. A height asked for ("高 30 格") is hit by the pipeline's search.

parse(text) -> (design, what was understood). python sentence.py "<句子>" prints both.
"""
import random
import re
import sys

import siteplan
from siteplan import room

_DIG = {"零": 0, "一": 1, "二": 2, "两": 2, "三": 3, "四": 4, "五": 5, "六": 6, "七": 7, "八": 8, "九": 9}


def _num(s):
    """'3', '三', '十一', '二十' -> int; None if it is not a number."""
    if s is None:
        return None
    if s.isdigit():
        return int(s)
    if "十" in s:
        a, _, b = s.partition("十")
        return (_DIG.get(a, 1) if a else 1) * 10 + (_DIG.get(b, 0) if b else 0)
    return _DIG.get(s)


N = r"(\d+|[零一二两三四五六七八九十]+)"

ROOFS = (("十字脊", "shizi"), ("盝顶", "luding"), ("庑殿", "wudian"), ("歇山", "xieshan"), ("悬山", "xuanshan"), ("硬山", "yingshan"),
         ("卷棚", "juanpeng"), ("攒尖", "pyramid"))
ROUND_ROOFS = (("盔顶", "helmet"), ("圆顶", "dome"), ("穹顶", "dome"), ("平顶", "flat"), ("盝顶", "luding"), ("攒尖", "cone"))
THEMES = (("黄瓦", "gold"), ("金瓦", "gold"), ("琉璃", "gold"), ("绿瓦", "jade"), ("青瓦", "grey"), ("灰瓦", "grey"), ("黑瓦", "black"),
          ("红瓦", "red"), ("茅草", "thatch"), ("草顶", "thatch"))
WALLS = (("红墙", "palace"), ("宫殿", "palace"), ("白墙", "whitewall"), ("粉墙", "whitewall"), ("青砖", "greybrick"), ("灰墙", "greybrick"),
         ("木", "darkwood"), ("土墙", "mud"), ("石", "stone"))
SPECIAL = (("牌楼", "paifang"), ("牌坊", "paifang"), ("影壁", "screen"), ("照壁", "screen"), ("垂花门", "chuihua"), ("琉璃门", "glazedgate"),
           ("随墙门", "gate"), ("宫墙", "wall"), ("围墙", "wall"), ("河桥", "river"), ("金水河", "river"), ("铜鼎", "ornament"), ("日晷", "ornament"),
           ("铜缸", "ornament"), ("嘉量", "ornament"))


def parse(text, seed=None):
    t = text.replace(" ", "")
    seed = seed if seed is not None else (sum(map(ord, t)) % 9973) + 1
    rng = random.Random(seed)
    said = []
    # a whole compound
    if any(k in t for k in ("宫城", "故宫", "紫禁城")):
        said.append("宫城")
        return siteplan.city(1), said
    m = re.search(N + "进", t)
    if m or any(k in t for k in ("四合院", "院落", "宫院", "大院")):
        jin = max(1, min(3, _num(m.group(1)) if m else 2))
        kind = "palace" if any(k in t for k in ("宫", "殿", "皇")) else "house"
        said.append(("宫院" if kind == "palace" else "住宅") + f"{jin}进")
        d = siteplan.site(kind, jin, seed)
        return d, said
    # one of the pieces the room settings cannot make
    for word, shape in SPECIAL:
        if word in t:
            said.append(word)
            w, dd = {"paifang": (15, 5), "screen": (13, 3), "chuihua": (9, 7), "glazedgate": (13, 3), "gate": (13, 3), "wall": (31, 3),
                     "river": (31, 9), "ornament": (3, 3)}[shape]
            r = room(0, 0, w - 1, dd - 1, rng, special=shape, walls=_pick(t, WALLS, "palace"), theme=_pick(t, THEMES, "gold"),
                     variant=("铜鼎", "日晷", "嘉量", "铜缸").index(word) if shape == "ornament" else 0)
            return _design(text, [r]), said
    # a building
    s = dict(w=15, d=11, storeys=1, roof="xieshan", walls="palace", theme="gold", podium=0, podium_style="plain")
    if any(k in t for k in ("大殿", "宝殿", "正殿", "主殿")):
        s.update(w=31, d=17, podium=3, podium_style="xumizuo", base="white", caihua="hexi", beasts=3, brackets=2, uses="throne_hall")
        said.append("大殿")
    elif "殿" in t or "宫" in t:
        s.update(w=21, d=13, podium=2, podium_style="xumizuo", base="white", uses="council_hall")
        said.append("殿")
    if "亭" in t:
        s.update(w=9, d=9, roof="pyramid", open="open", uses="lookout")
        said.append("亭")
    if "塔" in t:
        s.update(w=13, d=13, storeys=5, taper=1, roof="pyramid", uses="lookout")
        said.append("塔")
    elif "楼" in t or "阁" in t:
        s.update(storeys=2, uses="study")
        said.append("楼阁")
    if "戏台" in t:
        s.update(open="front", uses="lookout")
        said.append("戏台")
    elif "门" in t:
        s.update(open="through", uses="lookout", d=min(s["d"], 9))
        said.append("门")
    if any(k in t for k in ("民居", "民房", "住宅", "小屋", "房子", "农家")):
        s.update(walls="greybrick", theme="grey", roof="yingshan", uses="main_room")
        said.append("民居")
    if "圆" in t:
        s.update(shape="round", w=max(s["w"], s["d"]), d=max(s["w"], s["d"]))
        for word, rr in ROUND_ROOFS:
            if word in t:
                s["round_roof"] = rr
                said.append(word)
                break
        said.append("圆")
    for word, roof in ROOFS if "shape" not in s else ():
        if word in t:
            s["roof"] = roof
            said.append(word)
            break
    if "重檐" in t:
        s["double_eave"] = True
        said.append("重檐")
    # platform before storeys ("三层台基" is a platform, not three storeys)
    if re.search(N + "层台", t) or "三台" in t:
        s.update(podium=max(s["podium"], 6), podium_style="tiers", base="white")
        said.append("三层台基")
        t = re.sub(N + "层台(基)?", "", t).replace("三台", "")
    if "须弥座" in t:
        s.update(podium=max(s["podium"], 3), podium_style="xumizuo", base="white")
        said.append("须弥座")
    if "城台" in t:
        s.update(podium=max(s["podium"], 8), podium_style="chengtai")
        said.append("城台")
    m = re.search(r"台基(高)?" + N + "格", t) or re.search(N + "格高?的?台基", t)
    if m:
        s["podium"] = max(0, min(12, _num(m.groups()[-1])))
        if s["podium_style"] == "plain" and s["podium"] >= 3:
            s["podium_style"] = "xumizuo"
        said.append(f"台基{s['podium']}格")
    m = re.search(N + "层", t)
    if m and "台" not in t[m.end():m.end() + 1]:
        s["storeys"] = max(1, min(8, _num(m.group(1))))
        said.append(f"{s['storeys']}层")
    m = re.search(r"(面阔)?" + N + "开?间", t)
    if m and "进深" not in t[max(0, m.start() - 2):m.start()]:
        s["w"] = max(5, _num(m.group(2)) * 4 + 1)
        said.append(f"{_num(m.group(2))}开间")
    m = re.search(r"进深" + N + "间", t)
    if m:
        s["d"] = max(5, _num(m.group(1)) * 4 + 1)
        said.append(f"进深{_num(m.group(1))}间")
    s["theme"] = _pick(t, THEMES, s["theme"], said)
    s["walls"] = _pick(t, WALLS, s["walls"], said)
    if "走兽" in t:
        m = re.search(N + "只?走兽|走兽" + N, t)
        n = _num((m.group(1) or m.group(2)) if m else None) or 5
        s["beasts"] = {3: 2, 5: 3, 7: 4, 9: 5}.get(n, 3)
        said.append("走兽")
    for word, ch in (("和玺", "hexi"), ("旋子", "xuanzi"), ("苏式", "sushi"), ("彩画", s.get("caihua") or "xuanzi")):
        if word in t:
            s["caihua"] = ch
            said.append(word)
            break
    if "斗拱" in t or "斗栱" in t:
        s["brackets"] = 3
        said.append("斗拱")
    if any(k in t for k in ("回廊", "周围廊", "围廊")):
        s["colonnade"] = True
        said.append("周围廊")
    elif "前廊" in t:
        s["veranda"] = True
        said.append("前廊")
    if "马头墙" in t:
        s.update(horsehead=True, roof="yingshan", walls="whitewall", theme="black")
        said.append("马头墙")
    if "敞" in t:
        s["open"] = "open"
    w, dd = s.pop("w") | 1, s.pop("d") | 1
    shape = s.pop("shape", None)
    r = room(0, 0, w - 1, dd - 1, rng, **s)
    if shape == "round":
        r["round"] = True
        r["shape"] = "round"
    m = re.search(r"高" + N + "格|" + N + "格高", t)
    if m and not re.search(r"台基(高)?" + N + "格", t):
        want = _num(m.group(1) or m.group(2))
        said.append(f"高{want}格")
        r = _fit_height(r, want)
    return _design(text, [r]), said


def _pick(t, table, default, said=None):
    for word, v in table:
        if word in t:
            if said is not None:
                said.append(word)
            return v
    return default


def _design(text, rooms):
    return dict(name="一句话·" + text[:16], tier="mortal", look=0, seed=1, rooms=rooms)


def _fit_height(r, want):
    """The pipeline's search for the ridge height asked for (层高 and 坡度; the rest as said)."""
    import pipeline
    best = None
    for sh in (5, 6, 7, 8, 9, 10, 12, 14, 16):
        for pitch in (1, 2, 3, 4):
            q = dict(r, sh=sh, pitch=pitch, y=pipeline.GROUND)
            m = pipeline.measure(pipeline.build(q).blocks, q)
            e = abs(m["ridge"] - want)
            if best is None or e < best[0]:
                best = (e, sh, pitch)
            if e == 0:
                break
        if best[0] == 0:
            break
    return dict(r, sh=best[1], pitch=best[2])


if __name__ == "__main__":
    import json
    d, said = parse(sys.argv[1])
    print("understood:", "、".join(said) or "（没认出来，按默认）")
    print(json.dumps(d["rooms"][:3], ensure_ascii=False)[:600])
