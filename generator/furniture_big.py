"""Big rooms: a hall of a few hundred cells furnished like a small room is empty. Big rooms get a layout - a carpet
runner from the door to the seat of honour, a dais at the back, the room's main pieces repeated in rows, its wall
pieces repeated along every wall, braziers in the corners, banners on the walls and a grid of hanging lanterns."""
from arch import B
from furniture import PIECES, ROOMS, _Lift

LAYOUTS = {
    "main_room": "parlor", "guest_hall": "parlor",
    "throne_hall": "axis", "council_hall": "axis", "ancestor_tablets": "axis", "temple_hall": "axis", "ancestral_hall": "axis",
    "courtroom": "axis", "discipline_hall": "axis", "altar_room": "axis",
    "lecture_hall": "rows", "auction_hall": "rows", "classroom": "rows", "task_hall": "rows",
    "scripture_store": "stacks", "herb_store": "stacks", "storeroom": "stacks", "granary": "stacks", "treasury": "stacks",
    "spirit_stone_vault": "stacks", "sword_gallery": "stacks", "bookshop": "stacks",
    "refectory": "grid", "inn_hall": "grid", "teahouse": "grid", "gambling_den": "grid", "outer_dorm": "bunks",
    "barracks": "bunks", "training_room": "grid", "greenhouse": "grid", "beast_stable": "stalls", "stable": "stalls",
    "alchemy_room": "workshop", "forging_room": "workshop", "smithy": "workshop", "talisman_room": "workshop",
    "formation_room": "center", "teleport_room": "center", "array_control": "center", "seclusion": "center",
    "meditation_room": "center", "star_room": "center", "stele": "center",
}
CENTER_PIECE = {
    "rows": {"lecture_hall": "cushion", "auction_hall": "seats", "classroom": "seats", "task_hall": "seats"},
    "grid": {"refectory": "dining", "inn_hall": "dining", "teahouse": "tea_table", "gambling_den": "gambling",
             "training_room": "dummy", "greenhouse": "plant"},
    "stacks": {"scripture_store": "scroll_rack", "herb_store": "medicine_wall", "storeroom": "crates", "granary": "sacks",
               "treasury": "chest", "spirit_stone_vault": "spirit_stones", "sword_gallery": "weapon_rack", "bookshop": "bookshelf"},
    "workshop": {"alchemy_room": "alchemy_furnace", "forging_room": "forge", "smithy": "forge", "talisman_room": "desk"},
    "center": {"formation_room": "formation_disk", "teleport_room": "formation_disk", "array_control": "formation_disk",
               "seclusion": "meditation", "meditation_room": "meditation", "star_room": "map_table", "stele": "stele"},
    "axis": {"throne_hall": "throne", "council_hall": "throne", "ancestor_tablets": "shrine", "temple_hall": "statue",
             "ancestral_hall": "shrine", "courtroom": "throne", "discipline_hall": "throne", "altar_room": "shrine"},
}


def _place(f, rng, name, cu, cv, y, front="south"):
    if name in PIECES:
        sub = f.sub(cu, cv, front)
        if hasattr(sub, "clear_of") and not sub.clear_of(PIECES[name][1], PIECES[name][2]):   # (stairs, doorways)
            return
        PIECES[name][0](_Lift(sub, y + 1), rng)


def furnish_big(f, rng, rt, u0, v0, u1, v1, y, doors=(), sh=6):
    lay = LAYOUTS.get(rt, "grid")
    W, D = u1 - u0 + 1, v1 - v0 + 1
    cu = (u0 + u1) // 2
    door_cells = set()
    for du, dv in doors:
        for a in range(-2, 3):
            for b in range(-2, 3):
                door_cells.add((du + a, dv + b))
    spec = ROOMS.get(rt, [])
    wall_pieces = [n for n, where, _ in spec if where == "wall" and n in PIECES]
    if wall_pieces and lay != "parlor":   # wall pieces repeated along the side and back walls; the front wall (doors) stays free
        for side in ("west", "east", "north"):
            t = 1
            L = D if side in ("west", "east") else W
            while t < L - 2:
                name = wall_pieces[(t // 5) % len(wall_pieces)]
                w = PIECES[name][1]
                if side == "west":
                    ou, ov, fr = u0, v1 - t, "east"
                elif side == "east":
                    ou, ov, fr = u1, v0 + t, "west"
                else:
                    ou, ov, fr = u0 + t, v0, "south"
                if (ou, ov) not in door_cells and not (side == "north" and abs(ou - cu) < 7):
                    _place(f, rng, name, ou, ov, y, fr)
                t += w + 2
    piece = CENTER_PIECE.get(lay, {}).get(rt)
    if lay == "axis":
        for v in range(v0 + 4, v1 + 1):
            for u in (cu - 1, cu, cu + 1):
                f.put(u, y + 1, v, B("red_carpet") if u == cu else B("yellow_carpet"))
        for du in range(-5, 6):
            for dv in range(0, 4):
                f.put(cu + du, y + 1, v0 + dv, B("quartz_bricks") if dv < 3 else B("quartz_slab", type="bottom"))
        _place(f, rng, piece or "throne", cu - 1, v0 + 1, y + 1)
        for s in (-1, 1):
            _place(f, rng, "brazier", cu + s * 6, v0 + 1, y)
            _place(f, rng, "floor_lamp", cu + s * 4, v0 + 4, y)
        side_piece = "chair" if rt in ("council_hall", "courtroom", "throne_hall") else "cushion"
        for v in range(v0 + 7, v1 - 2, 3):
            for s in (-1, 1):
                for k in range(2, max(3, (W // 2) - 4), 3):
                    _place(f, rng, side_piece, cu + s * (2 + k), v, y, "west" if s > 0 else "east")
    elif lay in ("rows", "grid", "stacks", "bunks", "stalls"):
        name = piece or {"bunks": "bunk", "stalls": "beast_pen"}.get(lay, "table")
        w, d = PIECES[name][1], PIECES[name][2]
        gap_u = 2 if lay in ("stacks", "bunks", "stalls") else 3
        gap_v = 3 if lay != "rows" else 2
        if lay == "rows":
            _place(f, rng, "counter" if rt == "task_hall" else "lectern_dais", cu - 1, v0 + 1, y)
        start_v = v0 + (4 if lay == "rows" else 2)
        for v in range(start_v, v1 - d - 1, d + gap_v):
            for u in range(u0 + 2, u1 - w - 1, w + gap_u):
                if any((u + a, v + b) in door_cells for a in range(w) for b in range(d)):
                    continue
                if lay == "stacks" or abs(u + w // 2 - cu) > 1:        # keep a central aisle
                    _place(f, rng, name, u, v, y)
    elif lay == "parlor":
        # 堂屋: a long altar table against the back wall with a scroll over it, the square table and chairs in front,
        # chairs with little tables down both side walls facing the middle, plants and lamps in the corners
        _place(f, rng, "incense_table", cu - 1, v0, y, "south")
        _place(f, rng, "scroll", cu, v0, y + 2, "south")
        _place(f, rng, "dining", cu - 2, v0 + 3, y)
        for v in range(v0 + 3, v1 - 2, 3):
            for s, (uu, fr) in ((-1, (u0, "east")), (1, (u1, "west"))):
                if (uu, v) in door_cells:
                    continue
                _place(f, rng, "chair", uu, v, y, fr)
                if v + 1 < v1 - 2:
                    _place(f, rng, "table", uu, v + 1, y, fr)
        for cuu, cvv in ((u0, v1), (u1, v1)):
            if (cuu, cvv) not in door_cells:
                _place(f, rng, "plant", cuu, cvv, y)
    elif lay == "workshop":
        name = piece or "workbench"
        for u in range(u0 + 3, u1 - 3, 8):
            for v in range(v0 + 3, v1 - 4, 8):
                _place(f, rng, name, u, v, y)
                _place(f, rng, "vat" if rt != "talisman_room" else "jade_slips", u + 4, v, y)
    else:
        name = piece or "rug"
        _place(f, rng, name, cu - 1, (v0 + v1) // 2 - 1, y)
        for s in (-1, 1):
            for t in (-1, 1):
                _place(f, rng, "crystal" if rt in ("formation_room", "teleport_room", "array_control") else "plant",
                       cu + s * (W // 3), (v0 + v1) // 2 + t * (D // 3), y)
    for cuu, cvv in ((u0 + 1, v0 + 1), (u1 - 1, v0 + 1)):
        _place(f, rng, "brazier", cuu, cvv, y)
    colour = rng.choice(["red", "yellow", "blue", "black", "green"])
    def solid(u, v):                                   # a wall banner needs a solid wall behind it (not a window)
        st = f.get(u, y + 4, v) if hasattr(f, "get") else None
        return bool(st) and st != "minecraft:air" and not any(t in st for t in ("pane", "bars", "fence", "door", "lantern"))
    for v in range(v0 + 3, v1 - 2, 6):                 # not over stairs or doorways
        if (u0, v) not in door_cells and solid(u0 - 1, v):
            f.put(u0, y + 4, v, B(colour + "_wall_banner", facing="east"))
        if (u1, v) not in door_cells and solid(u1 + 1, v):
            f.put(u1, y + 4, v, B(colour + "_wall_banner", facing="west"))
    # (lamps: bgen.light_room lights every room with the kind of lamp chosen for it)
