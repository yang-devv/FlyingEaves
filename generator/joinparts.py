"""Join separately generated building parts (rooms of a composed building, a house and its side wing) into one canvas.

Each part was generated on its own canvas. Rules, so roofs and eaves of different parts do not cut through each other:

1. A part owns its column: inside its walls (footprint), from just above its ground y up to its top, only its own blocks
   stay. A wing's roof stops at the hall's wall; the hall's skirt roof and balcony stop over the wing.
2. Outside every footprint, the roof zone of a column (anything above a part's wall top) belongs to one part only: the
   one reaching highest there. Two eaves of different shapes or heights never sit on top of each other.
3. Everything else (plinths, steps, things on the ground) - later parts win cell by cell.
4. (Parts that say where their floor is - 单体建造 rooms.) Outside the footprints, the platform zone of a column belongs
   to the nearest part; where a platform so stops halfway to a lower one (or to a room standing on the ground), its
   edge there gets the rail its other edges have (none when it has none).

part = dict(blocks={(x, y, z): state}, cols={(x, z): [runs]}, fp=(x0, z0, x1, z1), y=ground_y, wall_top=y, top=y,
            mask={(x, z), ...} optional: the columns the part really stands on, when that is not its whole rectangle
            (a round tower owns its circle, not the square round it, so a house wall next to it keeps its corners))
Parts are given in priority order: later parts win ties (put the taller ones last).
"""


def join(cv, parts, who=None):
    """who: a dict to fill with the part (its index) each block of the joined canvas came from."""
    def inside(p, x, z):
        if p.get("mask") is not None:
            return (x, z) in p["mask"]
        x0, z0, x1, z1 = p["fp"]
        return x0 <= x <= x1 and z0 <= z <= z1

    colparts = {}                                # (x, z) -> the parts whose column it is, in order (a town has hundreds)
    for i, p in enumerate(parts):
        if p.get("mask") is not None:
            cells = p["mask"]
        else:
            x0, z0, x1, z1 = p["fp"]
            cells = [(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1)]
        for c in cells:
            colparts.setdefault(c, []).append(i)

    def owner(x, y, z):
        best = None
        for i in colparts.get((x, z), ()):
            p = parts[i]
            if p["y"] + 1 <= y <= p["top"]:
                best = i
        return best

    def outside_all(x, z):
        return (x, z) not in colparts

    # rule 2: who has the highest roof-zone block in each column outside the footprints
    roof_owner = {}
    for i, p in enumerate(parts):
        for (x, y, z), s in p["blocks"].items():
            if y > p["wall_top"] and outside_all(x, z):
                cur = roof_owner.get((x, z))
                if cur is None or y >= cur[0]:
                    roof_owner[(x, z)] = (y, i)
    # rule 4 (parts that say where their floor is - 单体建造 rooms): outside the footprints, the platform zone of a column
    # (up to just above the floors) belongs to the nearest part, so one room's platform, rails and front steps stop
    # halfway to the next room's instead of running round it (two platforms of different heights or styles side by side)
    def dist(p, x, z):
        x0, z0, x1, z1 = p["fp"]
        return max(x0 - x, x - x1, 0) + max(z0 - z, z - z1, 0)
    nearest = {}

    def near_ok(i, x, y, z):
        p = parts[i]
        if "floor" not in p or y > p["floor"] + 2 or not outside_all(x, z):
            return True
        key = (x, z)
        if key not in nearest:
            ds = [dist(q, x, z) if "floor" in q else 1 << 30 for q in parts]
            m = min(ds)
            nearest[key] = {j for j, d in enumerate(ds) if d == m}
        return i in nearest[key]

    # (and what stood on that platform goes with it: a gallery's post, a lamp post - it hung in the air in front of the
    # next room, its foot cut away, a beam at the head of that room's door)
    lost = set()
    for i, p in enumerate(parts):
        if "floor" not in p:
            continue
        fl = p["floor"]
        for (x, y, z) in [c for c in p["blocks"] if c[1] == fl + 3]:
            if outside_all(x, z) and not near_ok(i, x, fl + 2, z):
                while p["blocks"].get((x, y, z)) not in (None, "minecraft:air"):
                    lost.add((i, x, y, z))
                    y += 1
    for i, p in enumerate(parts):
        for (x, y, z), s in p["blocks"].items():
            o = owner(x, y, z)
            if o is not None and o != i:
                continue
            if not near_ok(i, x, y, z) or (i, x, y, z) in lost:
                continue
            if y > p["wall_top"] and outside_all(x, z) and roof_owner[(x, z)][1] != i:
                continue
            cv.set(x, y, z, s)
            if who is not None:
                who[(x, y, z)] = i
        for key, runs in p.get("cols", {}).items():
            x, z = key
            keep = []
            for (y0, y1, st) in runs:            # column runs (a 须弥座's drum) follow rules 1 and 4 too
                lo = y0
                if "floor" in p:
                    if not near_ok(i, x, p["floor"] + 2, z):
                        lo = max(lo, p["floor"] + 3)
                    for j in colparts.get((x, z), ()):
                        q = parts[j]
                        if j != i and lo <= q["top"] and y1 >= q["y"] + 1:
                            if lo >= q["y"] + 1:
                                lo = q["top"] + 1          # wholly inside the other part's column: dropped (or above it)
                            else:
                                y1 = min(y1, q["y"])      # the part below the other's ground stays
                if lo <= y1:
                    keep.append((lo, y1, st))
            cv.cols[key].extend(keep)
    seam_rails(cv, parts, outside_all, near_ok)


def _rail_name(p, outside_all):
    """The rail a part's platform has round it (a wall or fence block on the platform, outside the walls), or None."""
    cnt = {}
    for (x, y, z), s in p["blocks"].items():
        if p["y"] < y <= p["floor"] + 1 and outside_all(x, z):
            name = s.split("[")[0]
            if name.endswith("_wall") or (name.endswith("_fence") and "nether" not in name):
                cnt[name] = cnt.get(name, 0) + 1
    if not cnt:
        return None
    name = max(cnt, key=cnt.get)
    return name if cnt[name] >= 8 else None          # (a lamp post or two is not a rail)


def seam_rails(cv, parts, outside_all, near_ok):
    def top(x, z, below):
        """The highest block of the joined column at or under y = below (column runs counted), or None."""
        best = None
        for y in range(below, below - 16, -1):
            s = cv.blocks.get((x, y, z))
            if s is not None and not s.endswith(":air"):
                best = y
                break
        for (y0, y1, st) in cv.cols.get((x, z), ()):
            if y0 <= below:
                t = min(y1, below)
                if best is None or t > best:
                    best = t
        return best

    placed = {}
    for i, p in enumerate(parts):
        if "floor" not in p:
            continue
        rail = _rail_name(p, outside_all)
        if rail is None:
            continue
        fl = p["floor"]
        cols = {(x, z) for (x, y, z) in p["blocks"] if y <= fl and outside_all(x, z)} | set(p.get("cols", {}))
        for (x, z) in cols:
            if not outside_all(x, z) or not near_ok(i, x, fl, z):
                continue
            t = top(x, z, fl)
            if t is None or t < p["y"] + 2:
                continue
            s = cv.blocks.get((x, t, z), "")
            if "stairs" in s or "slab" in s or cv.blocks.get((x, t + 1, z)) not in (None, "minecraft:air"):
                continue                               # a step, or something standing there already
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, nz = x + dx, z + dz
                if not outside_all(nx, nz) or near_ok(i, nx, fl, nz):
                    continue                           # its own platform, or a room's wall
                tn = top(nx, nz, t)
                if (tn if tn is not None else p["y"]) <= t - 2:
                    placed[(x, t + 1, z)] = rail
                    break
    for (x, y, z), name in placed.items():          # joined to the rails (theirs and the seam's) beside them
        con = {}
        for d, (dx, dz) in (("east", (1, 0)), ("west", (-1, 0)), ("south", (0, 1)), ("north", (0, -1))):
            nb = placed.get((x + dx, y, z + dz)) or (cv.blocks.get((x + dx, y, z + dz)) or "").split("[")[0]
            same = nb == name
            con[d] = ("low" if same else "none") if name.endswith("_wall") else ("true" if same else "false")
        props = dict(con, up="true") if name.endswith("_wall") else dict(con, waterlogged="false")
        cv.set(x, y, z, name + "[" + ",".join(f"{k}={v}" for k, v in sorted(props.items())) + "]")
