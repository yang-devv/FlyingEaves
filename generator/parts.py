"""Parts as templates: furniture and other small pieces are read from JSON instead of being drawn by code.

Files (later ones add to or replace earlier ones):
  dev/buildings_sect/data/parts/*.json   the defaults (exported from the old piece functions, see export())
  run/bgen/parts/*.json                  the player's own: more variants of a piece, a replacement, or a new piece
                                         (also what the in-game "save as part" command writes)

{"parts": {"bed": {"cn": "架子床", "size": [w, d], "replace": false,
                   "variants": [[[u, k, v, "minecraft:state"], ...], ...]}}}

A piece is drawn in its own frame: u across (0..w-1), v from the wall into the room (0..d-1), k up from the floor it
stands on (0 = the first block above the floor); the frame faces the room ("south"), and the building code turns it.
Each placement picks one variant at random. With "replace": true a file's variants replace the ones loaded before it
instead of adding to them. Block states may name blocks from other mods; a variant that uses a block the game does not
have is skipped (materials.present).
"""
import json
import re
import random
from pathlib import Path

import materials
import paths

HERE = Path(__file__).resolve().parent
DIRS = [HERE / "data" / "parts", paths.RUN / "parts"]


def stamp():
    out = []
    for d in DIRS:
        if d.exists():
            out += [(str(p), p.stat().st_mtime) for p in sorted(d.glob("*.json"))]
    return tuple(out)


def load():
    """{name: dict(cn, w, d, variants)} from all part files."""
    out = {}
    for d in DIRS:
        if not d.exists():
            continue
        for p in sorted(d.glob("*.json")):
            data = json.loads(p.read_text(encoding="utf-8"))
            for name, e in data.get("parts", {}).items():
                w, dd = e["size"]
                variants = [v for v in e.get("variants", []) if all(materials.present(b[3]) for b in v)]
                if not variants:
                    continue
                if name in out and not e.get("replace"):
                    out[name]["variants"] += variants
                    continue
                out[name] = dict(cn=e.get("cn", name), w=w, d=dd, variants=list(variants))
    return out


def load_lights():
    """Light fixtures {name: dict(cn, mount, light, blocks, tiers, rooms, weight)} from lights*.json in the part folders
    (data/parts/lights.json and the player's run/bgen/parts/lights*.json; same name replaces)."""
    out = {}
    for d in DIRS:
        if not d.exists():
            continue
        for p in sorted(d.glob("lights*.json")):
            data = json.loads(p.read_text(encoding="utf-8"))
            for name, e in data.get("lights", {}).items():
                if all(b[1].startswith("$") or materials.present(b[1]) for b in e.get("blocks", [])):   # ($rail etc.: the building's own)
                    out[name] = dict(e)
    return out


LIGHTS = {}


def load_bays():
    """Wall bays {name: dict(cn, role, rows | widths, key, tiers, walls, weight)} from bays*.json in the part folders
    (data/parts/bays.json and the player's run/bgen/parts/bays*.json; same name replaces). See data/parts/bays.json."""
    out = {}
    for d in DIRS:
        if not d.exists():
            continue
        for p in sorted(d.glob("bays*.json")):
            data = json.loads(p.read_text(encoding="utf-8"))
            for name, e in data.get("bays", {}).items():
                if all(materials.present(v) for v in (e.get("key") or {}).values()):
                    out[name] = dict(e)
    return out


BAYS = {}


def load_ornaments():
    """Roof and eave ornaments {name: dict(cn, point, cells, tiers, forms, min_size, max_size, weight)} from
    ornaments*.json in the part folders (data/parts/ornaments.json and the player's run/bgen/parts/ornaments*.json)."""
    out = {}
    for d in DIRS:
        if not d.exists():
            continue
        for p in sorted(d.glob("ornaments*.json")):
            data = json.loads(p.read_text(encoding="utf-8"))
            for name, e in data.get("ornaments", {}).items():
                if all(c[3].startswith("$") or materials.present(c[3]) for c in e.get("cells", [])):
                    out[name] = dict(e)
    return out


ORNAMENTS = {}


def reload_ornaments():
    ORNAMENTS.clear()
    ORNAMENTS.update(load_ornaments())


def reload_bays():
    BAYS.clear()
    BAYS.update(load_bays())


def bay_rows(e, width, h):
    """The rows of a bay for a segment `width` wide and `h` high: list of (wall codes, out codes), bottom up, each
    `width` long; None when the part has no rows for that width or cannot be fitted to that height."""
    rows = e.get("rows") if "rows" in e else (e.get("widths") or {}).get(str(width))
    if rows is None:
        return None
    parsed = []
    for r in rows:
        rep = False
        need = 0
        if "@" in r:
            r, n = r.rsplit("@", 1)
            need = int(n)
        if r.endswith("*"):
            r, rep = r[:-1], True
        wall, _, out = r.partition("|")
        if need > h:
            continue
        if len(wall) == 1:
            wall = wall * width
        if out and len(out) == 1:
            out = out * width
        if len(wall) != width or (out and len(out) != width):
            return None
        parsed.append((wall, out or "." * width, rep))
    fixed = [p for p in parsed if not p[2]]
    reps = [i for i, p in enumerate(parsed) if p[2]]
    extra = h - len(fixed)
    if extra < 0 or (extra > 0 and not reps):
        return None
    counts = {i: 0 for i in reps}
    for t in range(extra):                  # extra rows shared out over the repeatable rows, top one first
        counts[reps[len(reps) - 1 - t % len(reps)]] += 1
    res = []
    for i, (wall, out, rep) in enumerate(parsed):
        res += [(wall, out)] * (counts[i] if rep else 1)
    return res


def reload_lights():
    LIGHTS.clear()
    LIGHTS.update(load_lights())


def lamp_of(s):
    """A lamp inside a furniture template - a lit candle (on a desk, an altar, a stand) or a standing lantern - as the
    kind of place it is at ('table' with the number of candles, or 'stand'), so the building's own lamp of that kind
    (data/parts/lights.json) is drawn there. Anything else: None."""
    if s.startswith("minecraft:candle") and "lit=true" in s:
        m = re.search(r"candles=(\d)", s)
        return "table", int(m.group(1)) if m else 1
    if s in ("minecraft:lantern", "minecraft:lantern[hanging=false]"):
        return "stand", 1
    return None


def piece_fn(variants):
    """A piece function in the shape furniture.PIECES expects: draws one of the variants (its lamps are the building's
    table and stand lamps)."""
    lamps = {s: lamp_of(s) for var in variants for (_, _, _, s) in var}

    def draw(f, rng, w=None, d=None):
        import arch
        for u, k, v, s in variants[rng.randrange(len(variants))]:
            if lamps.get(s):
                arch.put_lamp(f, lamps[s][0], u, k, v, n=lamps[s][1])
            else:
                f.put(u, k, v, s)
    return draw


def install(pieces):
    """Put the template pieces into furniture.PIECES (templates win over the old functions of the same name)."""
    for name, e in load().items():
        pieces[name] = (piece_fn(e["variants"]), e["w"], e["d"])


class _Recorder:
    def __init__(self):
        self.cells = {}

    def put(self, u, k, v, s):
        if s is not None:
            self.cells[(u, k, v)] = s

    def get(self, u, k, v):
        return self.cells.get((u, k, v))


def export(pieces, out_file, tries=12):
    """Draw every code piece with several random seeds and write the distinct results as template variants."""
    res = {}
    for name, (fn, w, d) in pieces.items():
        seen, variants = set(), []
        for seed in range(tries):
            rec = _Recorder()
            fn(rec, random.Random(f"{name}/{seed}"))
            key = tuple(sorted(rec.cells.items()))
            if key in seen:
                continue
            seen.add(key)
            variants.append([[u, k, v, s] for (u, k, v), s in sorted(rec.cells.items())])
        res[name] = dict(size=[w, d], variants=variants)
    out_file.parent.mkdir(parents=True, exist_ok=True)
    out_file.write_text(json.dumps({"_说明": __doc__.strip().splitlines()[0], "parts": res}, ensure_ascii=False), encoding="utf-8")
    return res
