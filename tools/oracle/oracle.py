"""Oracle harness for the Java port of the generator (com.flyingeaves.gen): the Python generator (generator/) is the
reference; the Java port must write the same .dvcr (block by block, byte by byte but for the gzip time stamp).

  python -I tools/oracle/oracle.py run REQ.json [REQ.json ...] [--out DIR] [--keep]
      run both generators on the requests (a file may hold one request or a list), compare
  python -I tools/oracle/oracle.py rooms N [--seed S] [--profile plain|full|...] [--set k=v,...] [--out DIR]
      N random single rooms (随机一栋-style: one frame of a random size, settings by the profile) - write them to
      DIR/requests.json and run them
  python -I tools/oracle/oracle.py library | sites N | towns N [SECTS]
      every saved design (generator/data/library); 随机一院 plans; 随机城镇 / 随机宗门 designs (planned by the Python side)
  python -I tools/oracle/oracle.py kinds [--seed S] [--only a,b] [--tiers all|ends]
      the build menu's kinds (bgen.build): every kind at every tier it lists (or only its first and last)
  python -I tools/oracle/oracle.py suite [N]
      the standard regression: N rooms of every profile, the library, 2 seeds of sites, a town and a sect, every
      kind at its first and last tier, the planners (plans all 1)
  python -I tools/oracle/oracle.py plans GROUP [N]
      the planners (designs back, no blocks): GROUP sites | towns | sects | sentences | daemon | all; each design's
      JSON text compared as is (json.dumps(ensure_ascii=False): key order, float formatting)
  python -I tools/oracle/oracle.py compare DIR_A DIR_B
      compare the .dvcr files of two output folders (ids present in both)

Output: one line per request (same / DIFF with the first differing column and its runs either side), then a summary.
The Java side is compiled from src/main/java/com/flyingeaves/gen (plain javac, no Minecraft) into build/gen-classes.
Profiles (see PROFILES): plain = 1 storey, no podium, 硬山, closed, front door only; full = every setting random, as the
game's 随机一栋 (Compose.randomizeAll); others fix one feature on top of plain.
"""
import gzip
import json
import os
import random
import shutil
import struct
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
SRC = ROOT / "src" / "main" / "java" / "com" / "flyingeaves" / "gen"
CLASSES = ROOT / "build" / "gen-classes"
DEFAULT_OUT = ROOT / "build" / "oracle"


# ------------------------------------------------------------------------------------------------ building the Java side

def build_java():
    srcs = sorted(SRC.rglob("*.java"))
    newest = max(p.stat().st_mtime for p in srcs)
    stamp = CLASSES / ".stamp"
    if stamp.exists() and stamp.stat().st_mtime >= newest:
        return
    if CLASSES.exists():
        shutil.rmtree(CLASSES)
    CLASSES.mkdir(parents=True)
    argfile = CLASSES / "sources.txt"
    argfile.write_text("\n".join(str(p).replace("\\", "/") for p in srcs), encoding="utf-8")
    r = subprocess.run(["javac", "-encoding", "UTF-8", "--release", "17", "-nowarn", "-d", str(CLASSES), "@" + str(argfile)],
                       capture_output=True, text=True)
    if r.returncode:
        sys.stderr.write(r.stdout + r.stderr)
        raise SystemExit("javac failed")
    stamp.write_text(str(time.time()))


def run_python(out, req_files, trace=False):
    out.mkdir(parents=True, exist_ok=True)
    cmd = [sys.executable, "-I", str(HERE / "oracle_dump.py"), str(out)] + [str(p) for p in req_files] + (["--trace"] if trace else [])
    t = time.time()
    r = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if r.returncode:
        sys.stderr.write(r.stdout[-3000:] + r.stderr[-3000:])
        raise SystemExit("python oracle failed")
    if r.stderr.strip():
        sys.stderr.write("[python] " + r.stderr[-2000:] + "\n")
    return time.time() - t


def run_java(out, req_files):
    build_java()
    out.mkdir(parents=True, exist_ok=True)
    cmd = ["java", "-Xss16m", "-cp", str(CLASSES), "com.flyingeaves.gen.tools.Dump", str(out)] + [str(p) for p in req_files]
    t = time.time()
    r = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if r.returncode:
        sys.stderr.write(r.stdout[-3000:] + r.stderr[-6000:])
        raise SystemExit("java dump failed")
    if r.stderr.strip():
        sys.stderr.write("[java] " + r.stderr[-4000:] + "\n")
    return time.time() - t


# ------------------------------------------------------------------------------------------------ reading .dvcr

def read_dvcr(path):
    raw = path.read_bytes()
    data = gzip.decompress(raw)
    i = 4
    assert data[:4] == b"DVCR"
    ver, x0, z0, w, d, npal = struct.unpack(">iiiiii", data[i:i + 24])
    i += 24
    pal = []
    for _ in range(npal):
        (ln,) = struct.unpack(">H", data[i:i + 2])
        i += 2
        pal.append(data[i:i + ln].decode())
        i += ln
    (n,) = struct.unpack(">i", data[i:i + 4])
    i += 4
    counts = data[i:i + w * d]
    i += w * d
    y0s = struct.unpack(">%dh" % n, data[i:i + 2 * n])
    i += 2 * n
    y1s = struct.unpack(">%dh" % n, data[i:i + 2 * n])
    i += 2 * n
    ps = struct.unpack(">%dH" % n, data[i:i + 2 * n])
    cols = {}
    k = 0
    for col in range(w * d):
        c = counts[col]
        if c:
            cols[(x0 + col % w, z0 + col // w)] = [(y0s[j], y1s[j], pal[ps[j]]) for j in range(k, k + c)]
            k += c
    return dict(raw=raw, data=data, bbox=(x0, z0, w, d), cols=cols, runs=n)


def expand(cols):
    """(x, y, z) -> block, the runs of each column applied in order (a later run over an earlier one)."""
    out = {}
    for (x, z), runs in cols.items():
        for a, b, s in runs:
            for y in range(a, b + 1):
                out[(x, y, z)] = s
    return out


def compare_one(pa, pb, verbose=True):
    """'same' (bytes equal but the gzip time stamp), 'payload' (same blocks, other compressed bytes), or a diff text."""
    A, B = read_dvcr(pa), read_dvcr(pb)
    ra, rb = A["raw"], B["raw"]
    if ra[:4] + ra[8:] == rb[:4] + rb[8:]:
        return "same", ""
    if A["data"] == B["data"]:
        return "payload", "decompressed payload identical, gzip bytes differ"
    lines = []
    if A["bbox"] != B["bbox"]:
        lines.append(f"  bbox python {A['bbox']} java {B['bbox']}")
    ea, eb = expand(A["cols"]), expand(B["cols"])
    diff = sorted(c for c in set(ea) | set(eb) if ea.get(c) != eb.get(c))
    lines.append(f"  runs python {A['runs']} java {B['runs']}; cells python {len(ea)} java {len(eb)}; differing cells {len(diff)}")
    if diff:
        c = min(diff, key=lambda c: (c[2], c[0], c[1]))
        lines.append(f"  first differing cell {c}: python {ea.get(c)}  java {eb.get(c)}")
        x, y, z = c
        for yy in range(y - 2, y + 3):
            lines.append(f"    y={yy}: python {ea.get((x, yy, z))}  java {eb.get((x, yy, z))}")
        if verbose:
            for nm, E in (("python", ea), ("java", eb)):
                lines.append(f"    {nm} around (x-1..x+1, y, z-1..z+1):")
                for dz in (-1, 0, 1):
                    lines.append("      " + " | ".join(str(E.get((x + dx, y, z + dz)) or "-").replace("minecraft:", "")
                                                       for dx in (-1, 0, 1)))
    else:
        cd = sorted(k for k in set(A["cols"]) | set(B["cols"]) if A["cols"].get(k) != B["cols"].get(k))
        if cd:
            k = cd[0]
            lines.append(f"  same cells, other runs; first column {k}:\n    python {A['cols'].get(k)}\n    java   {B['cols'].get(k)}")
        else:
            lines.append("  same columns, other palette order or header")
    return "diff", "\n".join(lines)


def compare_dirs(da, db, ids=None, quiet=False):
    ids = ids or sorted(p.stem for p in da.glob("*.dvcr") if (db / p.name).exists())
    tally = {"same": 0, "payload": 0, "diff": 0, "missing": 0, "both_failed": 0}
    bad = []
    for i in ids:
        pa, pb = da / f"{i}.dvcr", db / f"{i}.dvcr"
        ja, jb = da / f"{i}.json", db / f"{i}.json"
        if not pa.exists() or not pb.exists():
            ea = json.loads(ja.read_text(encoding="utf-8")) if ja.exists() else {}
            eb = json.loads(jb.read_text(encoding="utf-8")) if jb.exists() else {}
            if not pa.exists() and not pb.exists() and not ea.get("ok", True) and not eb.get("ok", True):
                tally["both_failed"] += 1
                if not quiet:
                    print(f"{i}: both failed (python: {ea.get('error')}; java: {eb.get('error')})")
                continue
            tally["missing"] += 1
            bad.append(i)
            print(f"{i}: MISSING python {pa.exists()} ({ea.get('error')}) java {pb.exists()} ({eb.get('error')})")
            continue
        kind, text = compare_one(pa, pb)
        res_a = json.loads(ja.read_text(encoding="utf-8")) if ja.exists() else None
        res_b = json.loads(jb.read_text(encoding="utf-8")) if jb.exists() else None
        if kind == "diff":
            bad.append(i)
            print(f"{i}: DIFF\n{text}")
        elif res_a != res_b:
            print(f"{i}: {kind}, result json differs: python {res_a} java {res_b}")
        elif not quiet:
            print(f"{i}: {kind}")
        tally[kind] += 1
    total = sum(tally.values())
    print(f"== {tally['same'] + tally['payload']}/{total} identical (bytes {tally['same']}, payload only {tally['payload']}), "
          f"{tally['diff']} differ, {tally['missing']} missing, {tally['both_failed']} failed on both")
    return tally, bad


# ------------------------------------------------------------------------------------------------ random rooms

ROOFS = ["xieshan", "wudian", "xuanshan", "yingshan", "juanpeng", "pyramid", "luding", "shizi", "yuanzan"]
WALLS = ["palace", "darkwood", "whitewall", "greybrick", "redbrick", "wattle", "mud", "yellow", "stone", "log", "jade", "cave"]
THEMES = ["grey", "teal", "gold", "jade", "black", "red", "slate", "thatch", "wood", "mud"]
USES = ["main_room", "bedroom", "study", "kitchen", "storeroom", "guest_room", "inn_hall", "teahouse", "private_dining", "pharmacy",
        "general_store", "smithy", "ancestral_hall", "temple_hall", "meditation_room", "seclusion", "alchemy_room", "forging_room",
        "talisman_room", "formation_room", "scripture_store", "sword_gallery", "treasury", "lecture_hall", "council_hall", "task_hall",
        "throne_hall", "ancestor_tablets", "teleport_room", "auction_hall", "refectory", "outer_dorm", "inner_disciple_room",
        "elder_quarters", "training_room", "herb_store", "beast_stable", "lookout", "stele"]
BASES = ["auto", "wall", "greybrick", "white", "rammed", "mudbrick", "rubble", "sandstone", "blackstone"]
FRONTS = ["south", "east", "north", "west"]
STOREY_HEIGHTS = [0, 4, 5, 6, 7, 8, 9, 10, 12, 14, 16]
PODIUM_HEIGHTS = [0, 1, 2, 3, 4, 5, 6, 8, 10, 12]
PODIUM_STYLES = ["plain", "xumizuo", "tiers", "chengtai"]
STEP_SLOPES = ["45", "27", "18", "14"]
PODIUM_MARGINS = ["auto", "1", "2", "3", "4", "6", "8", "12", "16", "20"]
PODIUM_SHAPES = ["", "square", "round"]
OPENNESS = ["closed", "front", "open", "through"]
DOORS = ["front", "front_back", "four"]
ROUND_ROOFS = ["cone", "helmet", "dome", "luding", "flat"]
CAIHUAS = ["", "xuanzi", "hexi", "sushi"]
FLOOR_OPEN = ["", "closed", "front", "open", "through"]
SPECIALS = ["paifang", "screen", "gate", "glazedgate", "chuihua", "wall", "paving", "river", "ornament"]
TIERS = ["mortal", "lianqi", "zhuji", "jindan", "yuanying", "huashen"]


def full_room(rng):
    """Every setting random, as Compose.randomizeAll (the in-game 随机一栋), in the shape Room.json writes."""
    W, D = (7 + rng.randrange(36)) | 1, (7 + rng.randrange(26)) | 1
    r = dict(x0=-(W // 2), z0=-(D // 2), y=64)
    r["x1"], r["z1"] = r["x0"] + W - 1, r["z0"] + D - 1
    rnd = rng.random() < 0.15
    storeys = 1 + rng.randrange(5)
    r.update(storeys=storeys, roof=rng.choice(ROOFS), walls=rng.choice(WALLS), theme=rng.choice(THEMES),
             front=rng.choice(FRONTS), seed=rng.randrange(100000), variant=rng.randrange(4), roof_variant=rng.randrange(4))
    uses = [rng.choice(USES) for _ in range(storeys)]
    r.update(uses=uses, use=uses[0], use_upper=uses[min(1, storeys - 1)])
    sh = rng.choice(STOREY_HEIGHTS)
    if sh:
        r["sh"] = sh
    if rnd:
        r["shape"] = "round"
        rr = rng.randrange(len(ROUND_ROOFS))
        if rr:
            r["round_roof"] = ROUND_ROOFS[rr]
        if storeys > 1 and rng.random() < 0.5:
            r["pingzuo"] = True
    r["podium"] = rng.choice(PODIUM_HEIGHTS)
    b = rng.randrange(len(BASES))
    if b:
        r["base"] = BASES[b]
    taper = rng.randrange(4) if storeys > 1 else 0
    if taper:
        r["taper"] = taper
    g = rng.randrange(5)
    if g in (1, 3):
        r["veranda"] = True
    if g in (2, 3):
        r["back_veranda"] = True
    if g == 4:
        r["colonnade"] = True
    if rng.random() < 0.5:
        r["horsehead"] = True
    ev = rng.randrange(3)
    if ev >= 1:
        r["double_eave"] = True
    if ev == 2:
        r["triple_eave"] = True
    fo = [FLOOR_OPEN[rng.randrange(len(FLOOR_OPEN))] if rng.random() < 0.25 else "" for _ in range(storeys)]
    if any(fo):
        r["floor_open"] = fo
    for key, table in (("podium_style", PODIUM_STYLES), ("step_slope", STEP_SLOPES), ("podium_margin", PODIUM_MARGINS),
                       ("podium_shape", PODIUM_SHAPES), ("caihua", CAIHUAS)):
        i = rng.randrange(len(table))
        if i:
            r[key] = table[i]
    o = 3 if rng.random() < 0.1 else rng.randrange(3)
    if o:
        r["open"] = OPENNESS[o]
    df = rng.random()
    d = 0 if df < 0.7 else 1 if df < 0.9 else 2
    if d:
        r["doors"] = DOORS[d]
    for key, n in (("pitch", 5), ("overhang", 5), ("lift", 5), ("beasts", 6), ("tunnels", 5), ("brackets", 5)):
        v = rng.randrange(n)
        if v:
            r[key] = v
    return r


def plain_room(rng):
    """A plain rectangular room: 1 storey, no podium, 硬山, closed, front door only, automatic everything; random size,
    walls, tiles, use, front and seed."""
    W, D = (7 + rng.randrange(36)) | 1, (7 + rng.randrange(26)) | 1
    if rng.random() < 0.3:                      # (now and then an even side: compose drops a column)
        W += rng.choice((-1, 1))
    r = dict(x0=-(W // 2), z0=-(D // 2), y=64)
    r["x1"], r["z1"] = r["x0"] + W - 1, r["z0"] + D - 1
    use = rng.choice(USES)
    r.update(storeys=1, roof="yingshan", walls=rng.choice(WALLS), theme=rng.choice(THEMES), uses=[use], use=use, use_upper=use,
             front=rng.choice(FRONTS), seed=rng.randrange(100000), variant=rng.randrange(4), roof_variant=rng.randrange(4), podium=0)
    return r


def _with(**kw):
    def gen(rng):
        r = plain_room(rng)
        for k, v in kw.items():
            r[k] = v(rng, r) if callable(v) else v
        return r
    return gen


def _storeys(rng, r):
    n = rng.randint(2, 5)
    uses = [rng.choice(USES) for _ in range(n)]
    r.update(uses=uses, use=uses[0], use_upper=uses[1])
    return n


PROFILES = {
    "plain": plain_room,
    "full": full_room,
    "roofs": _with(roof=lambda rng, r: rng.choice(ROOFS)),
    "storeys": _with(storeys=_storeys),
    "sh": _with(sh=lambda rng, r: rng.choice(STOREY_HEIGHTS[1:])),
    "podium": _with(podium=lambda rng, r: rng.choice(PODIUM_HEIGHTS[1:])),
    "podium_style": _with(podium=lambda rng, r: rng.choice(PODIUM_HEIGHTS[1:]), podium_style=lambda rng, r: rng.choice(PODIUM_STYLES)),
    "eaves": _with(roof=lambda rng, r: rng.choice(ROOFS), pitch=lambda rng, r: rng.randrange(5), overhang=lambda rng, r: rng.randrange(5),
                   lift=lambda rng, r: rng.randrange(5), beasts=lambda rng, r: rng.randrange(6), brackets=lambda rng, r: rng.randrange(5)),
    "double_eave": _with(roof=lambda rng, r: rng.choice(ROOFS), double_eave=True),
    "platforms": _with(podium=lambda rng, r: rng.choice(PODIUM_HEIGHTS[1:]), podium_style=lambda rng, r: rng.choice(PODIUM_STYLES),
                       podium_shape=lambda rng, r: rng.choice(PODIUM_SHAPES), podium_margin=lambda rng, r: rng.choice(PODIUM_MARGINS),
                       step_slope=lambda rng, r: rng.choice(STEP_SLOPES), doors=lambda rng, r: rng.choice(DOORS),
                       tunnels=lambda rng, r: rng.randrange(5), base=lambda rng, r: rng.choice(BASES)),
    "specials": lambda rng: dict(full_room(rng), shape=rng.choice(SPECIALS), variant=rng.randrange(16), storeys=1),
    "board": lambda rng: dict(full_room(rng), walls_override=dict(rng.sample([("fill", "minecraft:oak_planks"), ("post", "minecraft:birch_log[axis=y]"),
                                                                          ("beam", "minecraft:cherry_planks"), ("floor", "minecraft:smooth_stone"),
                                                                          ("rail", "minecraft:birch_fence"), ("door", "minecraft:birch_door")], 3)),
                              roof_override=dict(rng.sample([("stairs", "minecraft:prismarine_stairs"), ("slab", "minecraft:prismarine_slab"),
                                                              ("full", "minecraft:prismarine"), ("ridge", "minecraft:dark_prismarine")], 2))),
    "round": _with(shape="round", roof="xieshan"),
    "round_full": lambda rng: dict(full_room(rng), shape="round"),
    "open": _with(open=lambda rng, r: rng.choice(OPENNESS), doors=lambda rng, r: rng.choice(DOORS)),
    "gallery": _with(veranda=lambda rng, r: rng.random() < 0.5, back_veranda=lambda rng, r: rng.random() < 0.5,
                     colonnade=lambda rng, r: rng.random() < 0.3),
}


def multi_rooms(rng, base=plain_room):
    """2-4 rooms: a first one, the others built against one of its sides (a wing, sharing a wall line or one cell
    apart), or stacked on it (叠放)."""
    first = base(rng)
    rooms = [first]
    for _ in range(rng.randint(1, 3)):
        r = base(rng)
        o = rng.choice(rooms)
        if rng.random() < 0.2 and not o.get("stack"):            # stacked on o, smaller
            w, d = max(5, (o["x1"] - o["x0"]) // 2) | 1, max(5, (o["z1"] - o["z0"]) // 2) | 1
            cx, cz = (o["x0"] + o["x1"]) // 2, (o["z0"] + o["z1"]) // 2
            r.update(x0=cx - w // 2, x1=cx + w // 2, z0=cz - d // 2, z1=cz + d // 2, stack=True)
        else:
            w, d = r["x1"] - r["x0"], r["z1"] - r["z0"]
            gap = rng.choice((0, 1))                              # (a shared wall line, or walls side by side)
            side = rng.choice("nsew")
            if side in "ns":
                x0 = rng.randint(o["x0"] - w // 2, o["x1"] - w // 2)
                z0 = o["z1"] + gap if side == "s" else o["z0"] - gap - d
                r.update(x0=x0, x1=x0 + w, z0=z0, z1=z0 + d)
            else:
                z0 = rng.randint(o["z0"] - d // 2, o["z1"] - d // 2)
                x0 = o["x1"] + gap if side == "e" else o["x0"] - gap - w
                r.update(x0=x0, x1=x0 + w, z0=z0, z1=z0 + d)
        rooms.append(r)
    return rooms


def random_rooms(n, seed=1, profile="plain", sets=None, tier=None):
    rng = random.Random(f"oracle/{seed}/{profile}")
    reqs = []
    for i in range(n):
        if profile == "multi":
            rooms = multi_rooms(rng)
        elif profile == "multi_full":
            rooms = multi_rooms(rng, full_room)
        elif profile == "mixed":                      # houses, round towers and special shapes against each other
            rooms = multi_rooms(rng, lambda g: PROFILES["specials"](g) if g.random() < 0.35 else full_room(g))
        else:
            rooms = [PROFILES[profile](rng)]
        for room in rooms:
            for k, v in (sets or {}).items():
                room[k] = v
        reqs.append(dict(id=f"{profile}_{seed}_{i:03d}", type="compose", tier=tier or rng.choice(TIERS),
                         seed=rng.randrange(100000), look=rng.randrange(3), rooms=rooms))
    return reqs


def design_request(d, rid, y=64, name=""):
    """A design (a saved one, a 随机一院 plan: rooms as Room.json writes them) as a compose request."""
    rooms = []
    for r in d.get("rooms", []):
        r = dict(r)
        sp = r.pop("special", "") or ""
        if sp:
            r["shape"] = sp
        elif r.pop("round", False):
            r["shape"] = "round"
        r["y"] = y + int(r.get("y", 0))
        rooms.append(r)
    return dict(id=rid, name=name or str(d.get("name", "")), type="compose", tier=d.get("tier", "mortal"),
                seed=int(d.get("seed", 0) or 0), look=int(d.get("look", 0) or 0), rooms=rooms) if rooms else None


def site_requests(n):
    """随机一院 plans from the Python siteplan (house and palace, one to three courts, seeds 1..n; free plans
    randomized, seeds 1..n; the 宫城 city(1) when n >= 4) as compose requests."""
    import tempfile
    os.environ["FLYINGEAVES_RUN"] = tempfile.mkdtemp(prefix="fe_sites_")
    sys.path.insert(0, str(ROOT / "generator"))
    import siteplan
    reqs = []
    for kind in ("house", "palace"):
        for jin in (1, 2, 3):
            for s in range(1, n + 1):
                reqs.append(design_request(siteplan.site(kind, jin, s), f"site_{kind}_{jin}_{s}"))
    for s in range(1, n + 1):
        reqs.append(design_request(siteplan.randomize(siteplan.site_free(s), random.Random(s)), f"site_free_{s}"))
    if n >= 4:
        reqs.append(design_request(siteplan.city(1), "site_city_1"))
    return [r for r in reqs if r]


def town_requests(n, sects=0):
    """随机城镇 (town.town, seeds 1..n) and 随机宗门 (sect_plan.sect, seeds 1..sects, tiers in turn) designs from the
    Python planners, as compose requests."""
    import tempfile
    os.environ["FLYINGEAVES_RUN"] = tempfile.mkdtemp(prefix="fe_towns_")
    sys.path.insert(0, str(ROOT / "generator"))
    import town
    import sect_plan
    reqs = []
    for s in range(1, n + 1):
        reqs.append(design_request(town.town(s), f"town_{s}"))
    for s in range(1, sects + 1):
        tier = ("zhuji", "jindan", "yuanying", "huashen")[(s - 1) % 4]
        reqs.append(design_request(sect_plan.sect(s, tier=tier), f"sect_{tier}_{s}"))
    return [r for r in reqs if r]


def library_requests(y=64):
    """Every design of generator/data/library as a compose request (design rooms as Room.json writes them: special ->
    shape, round -> shape round; heights from y)."""
    reqs = []
    lib = ROOT / "generator" / "data" / "library"
    for i, p in enumerate(sorted(lib.rglob("*.json"))):
        d = json.loads(p.read_text(encoding="utf-8"))
        rooms = []
        for r in d.get("rooms", []):
            r = dict(r)
            sp = r.pop("special", "") or ""
            if sp:
                r["shape"] = sp
            elif r.pop("round", False):
                r["shape"] = "round"
            r["y"] = y + int(r.get("y", 0))
            rooms.append(r)
        if rooms:
            reqs.append(dict(id=f"lib_{i:03d}", name=str(p.relative_to(lib)), type="compose", tier=d.get("tier", "mortal"),
                             seed=int(d.get("seed", 0) or 0), look=int(d.get("look", 0) or 0), rooms=rooms))
    return reqs


TOWN_SIZES = ("village", "town", "big")
TOWN_LAYOUTS = {"village": ("line", "T", "Y", "cross", "bank"), "town": ("line", "T", "cross", "Y", "bank"), "big": ("grid", "T", "bank")}
TOWN_BOXES = ([90, 90], [120, 200], [200, 160], [260, 260], [320, 240], [160, 360], [400, 400])
SECT_TIERS = ("zhuji", "jindan", "yuanying", "huashen")
SENTENCES = ["一座三层的塔", "住宅二进", "宫院三进", "宫城", "一座歇山顶的大殿 宽31 深17", "茶馆", "两层的客栈 青瓦", "一座八角亭",
             "一座重檐庑殿顶的金色宫殿，宽 41 深 21，台基 3 层须弥座", "随便来一座房子", "城门楼", "一座带回廊的书房",
             "一座红墙黄瓦的大殿", "一座白墙黑瓦的民居", "五开间硬山顶", "高 30 格的楼阁", "藏经阁", "炼丹房", "一座牌坊",
             "宗门大殿 宽 25", "一座小庙", "三进的宫院 七号", "住宅一进 3", "一座圆形的塔", "钟楼", "", "abc",
             "高三十格的塔", "20格高的亭", "二三进", "台基5格的殿", "5格高的台基", "七只走兽的大殿 和玺彩画 斗拱", "进深三间 面阔五间",
             "圆形盔顶的亭", "马头墙民居 前廊", "三层台基的大殿", "十一开间", "敞开的戏台", "城台上的门楼", "九开间重檐庑殿 黄瓦 三台",
             "八层的塔 高40格", "１２开间", "一二层", "二三开间", "高二三格", "走兽九 苏式", "日晷", "铜缸 红墙", "一座卷棚彩画的书房 高 12 格"]


def _heights(seed, R=40, step=1):
    """A ground the game might sample round the crosshair: {R, h, step} (some holes: -9999)."""
    import math
    rng = random.Random(f"heights/{seed}")
    a, b, c = rng.uniform(-0.2, 0.2), rng.uniform(-0.2, 0.2), rng.uniform(2, 12)
    n = 2 * (R // step) + 1
    h = []
    for k in range(n):
        for i in range(n):
            v = 64 + int(a * (i - n // 2) * step + b * (k - n // 2) * step + c * math.sin(i * 0.17 + k * 0.11))
            h.append(-9999 if rng.random() < 0.01 else v)
    return dict(R=R, h=h, step=step)


def plan_requests(group, n):
    reqs = []
    if group in ("sites", "all"):
        for kind in ("house", "palace"):
            for jin in (1, 2, 3):
                for s in range(1, n + 1):
                    reqs.append(dict(id=f"site_{kind}_{jin}_{s}", fn="site", kind=kind, jin=jin, seed=s))
        for s in range(1, n + 1):
            reqs.append(dict(id=f"free_{s}", fn="free", seed=s))
            reqs.append(dict(id=f"site_free_{s}", fn="site_free", seed=s))
            reqs.append(dict(id=f"crowded_free_{s}", fn="crowded", of="free", seed=s, strip=s % 4))
        for s in range(1, min(n, 3) + 1):
            reqs.append(dict(id=f"city_{s}", fn="city", seed=s))
            reqs.append(dict(id=f"crowded_city_{s}", fn="crowded", of="city", seed=s))
        for s in (1, 2):
            reqs.append(dict(id=f"city_real_{s}", fn="city_real", seed=s))
        reqs.append(dict(id="city_real_moat", fn="city_real", seed=3, moat=True))
        for kind in ("house", "palace"):
            for s in range(1, n + 1):
                reqs.append(dict(id=f"crowded_{kind}_{s}", fn="crowded", of="site", kind=kind, jin=1 + s % 3, seed=s))
    if group in ("towns", "all"):
        for s in range(1, n + 1):
            reqs.append(dict(id=f"town_{s}", fn="town", seed=s))
            for size in TOWN_SIZES:
                reqs.append(dict(id=f"town_{size}_{s}", fn="town", seed=s, size=size))
                lay = TOWN_LAYOUTS[size][s % len(TOWN_LAYOUTS[size])]
                reqs.append(dict(id=f"town_{size}_{lay}_{s}", fn="town", seed=s, size=size, layout=lay))
            box = TOWN_BOXES[s % len(TOWN_BOXES)]
            reqs.append(dict(id=f"town_box{box[0]}x{box[1]}_{s}", fn="town", seed=s, box=box))
            reqs.append(dict(id=f"town_heights_{s}", fn="town", seed=s, size=TOWN_SIZES[s % 3], heights=_heights(s, 60, 2)))
    if group in ("sects", "all"):
        for s in range(1, n + 1):
            for tier in SECT_TIERS:
                reqs.append(dict(id=f"sect_{tier}_{s}", fn="sect", seed=s, tier=tier))
            reqs.append(dict(id=f"sect_heights_{s}", fn="sect", seed=s, tier=SECT_TIERS[s % 4], heights=_heights(s + 100, 90, 1)))
    if group in ("sentences", "all"):
        for i, t in enumerate(SENTENCES):
            reqs.append(dict(id=f"sentence_{i:02d}", fn="sentence", text=t))
    if group in ("daemon", "all"):
        for s in range(1, n + 1):
            for which in ("", "city", "city_real"):
                reqs.append(dict(id=f"daemon_{which or 'free'}_{s}", fn="daemon", rs=s, req=dict(type="site", which=which)))
            reqs.append(dict(id=f"daemon_town_{s}", fn="daemon", rs=s,
                             req=dict(type="site", which="town", size=TOWN_SIZES[s % 3], box=TOWN_BOXES[s % len(TOWN_BOXES)] if s % 2 else None,
                                      heights=_heights(s + 7, 40, 1))))
            reqs.append(dict(id=f"daemon_sect_{s}", fn="daemon", rs=s, req=dict(type="site", which="sect", tier=SECT_TIERS[s % 4],
                                                                                heights=_heights(s + 9, 40, 1))))
            reqs.append(dict(id=f"daemon_sentence_{s}", fn="daemon", rs=s, req=dict(type="sentence", text=SENTENCES[s % len(SENTENCES)])))
    for r in reqs:
        r["type"] = "plan"
    return reqs


def _first_diff(a, b, path="$"):
    if type(a) is not type(b):
        return f"{path}: python {json.dumps(a, ensure_ascii=False)[:200]} java {json.dumps(b, ensure_ascii=False)[:200]}"
    if isinstance(a, dict):
        if list(a) != list(b):
            return f"{path}: keys python {list(a)} java {list(b)}"
        for k in a:
            d = _first_diff(a[k], b[k], f"{path}.{k}")
            if d:
                return d
        return None
    if isinstance(a, list):
        for i, (x, y) in enumerate(zip(a, b)):
            d = _first_diff(x, y, f"{path}[{i}]")
            if d:
                return d
        if len(a) != len(b):
            return f"{path}: length python {len(a)} java {len(b)}"
        return None
    return None if a == b else f"{path}: python {a!r} java {b!r}"


def compare_plans(da, db, ids, quiet=False):
    same = diff = failed = 0
    bad = []
    for i in ids:
        pa, pb = da / f"{i}.design.json", db / f"{i}.design.json"
        ta = pa.read_text(encoding="utf-8") if pa.exists() else None
        tb = pb.read_text(encoding="utf-8") if pb.exists() else None
        if ta is not None and ta == tb:
            same += 1
            if not quiet:
                print(f"{i}: same ({len(ta)} chars{'' if json.loads(ta).get('ok') else ', the same error'})")
            continue
        ja, jb = json.loads(ta) if ta else {}, json.loads(tb) if tb else {}
        if not ja.get("ok") or not jb.get("ok"):
            failed += 1
            bad.append(i)
            print(f"{i}: FAILED python {ja.get('ok')} ({ja.get('error')}) java {jb.get('ok')} ({jb.get('error')})")
            continue
        diff += 1
        bad.append(i)
        d = _first_diff(ja, jb)
        if d is None:
            m = min(len(ta), len(tb))
            k = next((k for k in range(m) if ta[k] != tb[k]), m)
            d = f"same values, other text at {k}: python ...{ta[max(0, k - 60):k + 60]}... java ...{tb[max(0, k - 60):k + 60]}..."
        print(f"{i}: DIFF {d}")
    print(f"== {same}/{len(ids)} identical, {diff} differ, {failed} failed")
    return bad


def kind_requests(seed=1, only=None, tiers="all"):
    """The build menu (O / 建造菜单): every kind of bgen.K (sites.py's included) at every tier it lists (tiers="ends":
    only its first and last), as daemon kind requests; facings in turn."""
    import tempfile
    os.environ["FLYINGEAVES_RUN"] = tempfile.mkdtemp(prefix="fe_kinds_")
    sys.path.insert(0, str(ROOT / "generator"))
    import bgen
    import sites  # noqa: F401
    reqs = []
    for name, spec in bgen.K.items():
        if only and name not in only:
            continue
        ts = list(spec["tiers"])
        if tiers == "ends":
            ts = sorted({ts[0], ts[-1]}, key=ts.index)
        for t in ts:
            reqs.append(dict(id=f"kind_{name}_{t}_{seed}", kind=name, tier=t, seed=seed, x=0, y=64, z=0,
                             facing=FRONTS[len(reqs) % 4]))
    return reqs


def _parse_set(s):
    out = {}
    for kv in (s or "").split(","):
        if "=" in kv:
            k, v = kv.split("=", 1)
            try:
                v = json.loads(v)
            except ValueError:
                pass
            out[k] = v
    return out


def main(argv):
    if not argv:
        print(__doc__)
        return 2
    opts, pos = {}, []
    i = 0
    while i < len(argv):
        a = argv[i]
        if a.startswith("--"):
            if a in ("--keep", "--trace", "--quiet"):
                opts[a[2:]] = True
            else:
                opts[a[2:]] = argv[i + 1]
                i += 1
        else:
            pos.append(a)
        i += 1
    out = Path(opts.get("out") or DEFAULT_OUT)
    cmd = pos[0]
    if cmd == "suite":                                 # the standard regression: every profile, the library, sites, towns
        n = int(pos[1]) if len(pos) > 1 else 20
        plan = [("rooms", p) for p in ("plain", "full", "roofs", "storeys", "podium", "platforms", "eaves", "double_eave", "open",
                                       "gallery", "round_full", "specials", "board", "multi", "multi_full", "mixed")] +                [("library", None), ("sites", None), ("towns", None), ("kinds", None), ("plans", None)]
        rows = []
        for kind, prof in plan:
            sub = out / (prof or kind)
            args = [kind] + ([str(n)] if kind == "rooms" else ["2"] if kind == "sites" else ["1", "1"] if kind == "towns" else ["--tiers", "ends"] if kind == "kinds" else ["all", "1"] if kind == "plans" else []) +                 (["--profile", prof] if prof else []) + ["--out", str(sub), "--quiet"]
            r = subprocess.run([sys.executable, "-I", __file__] + args, capture_output=True, text=True, encoding="utf-8", errors="replace")
            last = [ln for ln in r.stdout.splitlines() if ln.startswith("==")]
            rows.append(f"{prof or kind:12s} {last[-1] if last else 'FAILED ' + r.stderr[-300:]}")
            print(rows[-1], flush=True)
        return 0
    if cmd == "plans":
        reqs = plan_requests(pos[1] if len(pos) > 1 else "all", int(pos[2]) if len(pos) > 2 else 3)
        if opts.get("only"):
            reqs = [r for r in reqs if opts["only"] in r["id"]]
        out.mkdir(parents=True, exist_ok=True)
        rf = out / "plans.json"
        rf.write_text(json.dumps(reqs, ensure_ascii=False, indent=1), encoding="utf-8")
        for sub in ("python", "java"):
            if (out / sub).exists() and not opts.get("keep"):
                shutil.rmtree(out / sub)
        tp = run_python(out / "python", [rf])
        tj = run_java(out / "java", [rf])
        print(f"python {tp:.1f} s, java {tj:.1f} s (both with start-up)")
        return 1 if compare_plans(out / "python", out / "java", [r["id"] for r in reqs], quiet=opts.get("quiet")) else 0
    if cmd == "compare":
        tally, bad = compare_dirs(Path(pos[1]), Path(pos[2]), quiet=opts.get("quiet"))
        return 1 if bad else 0
    if cmd == "rooms":
        reqs = random_rooms(int(pos[1]), int(opts.get("seed", 1)), opts.get("profile", "plain"), _parse_set(opts.get("set")),
                            opts.get("tier"))
        out.mkdir(parents=True, exist_ok=True)
        rf = out / "requests.json"
        rf.write_text(json.dumps(reqs, ensure_ascii=False, indent=1), encoding="utf-8")
        files = [rf]
        ids = [r["id"] for r in reqs]
    elif cmd in ("library", "sites", "towns", "kinds"):
        if cmd == "kinds":
            reqs = kind_requests(int(opts.get("seed", 1)), set(opts["only"].split(",")) if opts.get("only") else None,
                                 opts.get("tiers", "all"))
        elif cmd == "library":
            reqs = library_requests()
        elif cmd == "sites":
            reqs = site_requests(int(pos[1]) if len(pos) > 1 else 2)
        else:
            reqs = town_requests(int(pos[1]) if len(pos) > 1 else 1, int(pos[2]) if len(pos) > 2 else 0)
        out.mkdir(parents=True, exist_ok=True)
        rf = out / "requests.json"
        rf.write_text(json.dumps(reqs, ensure_ascii=False, indent=1), encoding="utf-8")
        files = [rf]
        ids = [r["id"] for r in reqs]
    elif cmd == "run":
        files = [Path(p) for p in pos[1:]]
        ids = []
        for p in files:
            d = json.loads(p.read_text(encoding="utf-8"))
            ids += [r["id"] for r in (d if isinstance(d, list) else [d])]
    else:
        print(__doc__)
        return 2
    for sub in ("python", "java"):
        if (out / sub).exists() and not opts.get("keep"):
            shutil.rmtree(out / sub)
    tp = run_python(out / "python", files, trace=opts.get("trace"))
    tj = run_java(out / "java", files)
    print(f"python {tp:.1f} s, java {tj:.1f} s (both with start-up)")
    tally, bad = compare_dirs(out / "python", out / "java", ids, quiet=opts.get("quiet"))
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
