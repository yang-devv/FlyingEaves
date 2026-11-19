"""Materials as data: wall styles and roof themes are read from JSON instead of being written into the code.

Files (later ones override earlier ones key by key, new keys add new styles):
  dev/buildings_sect/data/materials/*.json   the defaults that ship with the generator
  run/bgen/materials/*.json                  the player's own (add a style, or change one, without touching code)

Each material slot is a list of candidate block states; the first one the game has is used, so a style can name blocks
from other mods and keep a vanilla block last as the fallback. The game writes the ids of every registered block to
run/bgen/registry_blocks.txt when it starts (BuildTool); without that file only minecraft: blocks count as present.

walls: {name: {cn, post, fill, base, beam, floor, shutter, door, pane, dado, trim_stairs, trim_slab, trim_trapdoor,
       base_stairs, rail, steps, inner_stairs, ladder}}  (post/fill/base/beam/floor/dado are full states, the rest plain
       ids that the code adds properties to; the later ones have defaults)
roofs: {name: {cn, stairs, slab, full, ridge, cap, corner_lift}}
An entry with "inherit": <other style> starts from that style and changes only the slots it names (what the in-game
material board writes: run/bgen/materials/custom.json).
"""
import json
from pathlib import Path

import paths

HERE = Path(__file__).resolve().parent
DIRS = [HERE / "data" / "materials", paths.RUN / "materials"]
REGISTRY = paths.RUN / "registry_blocks.txt"

_registry = None


def registry():
    """Block ids the game has (None when the game has not written the list yet)."""
    global _registry
    if _registry is None and REGISTRY.exists():
        _registry = set(REGISTRY.read_text(encoding="utf-8").split())
    return _registry


def present(state):
    bid = state.split("[")[0]
    if ":" not in bid:
        bid = "minecraft:" + bid
    reg = registry()
    if reg is None:
        return bid.startswith("minecraft:")
    return bid in reg


def pick(cands):
    """First candidate the game has; the last one if none is (it should be vanilla)."""
    if isinstance(cands, str):
        return cands
    for c in cands:
        if present(c):
            return c
    return cands[-1]


def stamp():
    """Something to compare to notice edited or added material files (or a new block list from the game)."""
    out = []
    for d in DIRS:
        if d.exists():
            out += [(str(p), p.stat().st_mtime) for p in sorted(d.glob("*.json"))]
    if REGISTRY.exists():
        out.append((str(REGISTRY), REGISTRY.stat().st_mtime))
    return tuple(out)


def forget_registry():
    global _registry
    _registry = None


def load(section):
    out = {}
    for d in DIRS:
        if not d.exists():
            continue
        for p in sorted(d.glob("*.json")):
            data = json.loads(p.read_text(encoding="utf-8"))
            for name, entry in data.get(section, {}).items():
                merged = dict(out.get(name, {}))
                merged.update(entry)
                out[name] = merged
    for name in list(out):                  # "inherit": start from another style
        seen, e = {name}, out[name]
        while e.get("inherit") in out and e["inherit"] not in seen:
            seen.add(e["inherit"])
            base = dict(out[e["inherit"]])
            base.update({k: v for k, v in e.items() if k != "inherit"})
            base["inherit"] = out[e["inherit"]].get("inherit")
            e = base
        e.pop("inherit", None)
        out[name] = e
    return out


def walls():
    """Wall styles in the shape bgen uses: {name: dict(post, fill, base, beam, floor, win, door, pane, cn)}."""
    res = {}
    for name, e in load("walls").items():
        res[name] = dict(post=pick(e["post"]), fill=pick(e["fill"]), base=pick(e["base"]), beam=pick(e["beam"]),
                         floor=pick(e["floor"]), win=pick(e["shutter"]), door=pick(e["door"]), pane=pick(e["pane"]),
                         cn=e.get("cn", name), swatch=e.get("swatch", ""),
                         dado=pick(e.get("dado") or e["base"]),
                         trim_stairs=pick(e.get("trim_stairs") or ["minecraft:spruce_stairs"]),
                         trim_slab=pick(e.get("trim_slab") or ["minecraft:spruce_slab"]),
                         trim_trapdoor=pick(e.get("trim_trapdoor") or ["minecraft:spruce_trapdoor"]),
                         base_stairs=pick(e.get("base_stairs") or ["minecraft:stone_brick_stairs"]),
                         rail=pick(e.get("rail") or ["minecraft:spruce_fence"]),             # 栏杆: balconies, verandas
                         steps=pick(e.get("steps") or ["minecraft:stone_brick_stairs"]),     # 台阶 up to the door
                         inner_stairs=pick(e.get("inner_stairs") or ["minecraft:spruce_stairs"]),   # 楼梯 inside
                         ladder=pick(e.get("ladder") or ["minecraft:ladder"]))
    return res


def roofs():
    """Roof themes: {name: dict(tile=<set name for stairs/slab>, stairs, slab, full, ridge, cap, corner_lift, cn)}."""
    res = {}
    for name, e in load("roofs").items():
        res[name] = dict(tile="roof:" + name, stairs=pick(e["stairs"]), slab=pick(e["slab"]), full=pick(e["full"]),
                         ridge=pick(e["ridge"]), cap=pick(e["cap"]), corner_lift=e.get("corner_lift", True),
                         cn=e.get("cn", name), swatch=e.get("swatch", ""),
                         stripe_stairs=pick(e["stripe_stairs"]) if e.get("stripe_stairs") else None,
                         stripe_slab=pick(e["stripe_slab"]) if e.get("stripe_slab") else None)
    return res
