"""Xianxia-specific structures for the sect (exterior): things a cultivation sect has that a palace does not.

- cliff_cave:     a cave dwelling (洞府) cut into a cliff, with a red door frame, a little tiled hood and a balcony
- formation_pillar: a 护山大阵 anchor: tall dark pillar with gold bands and a beacon on top (light column)
- dan_furnace:    a giant three-legged alchemy furnace (丹炉) over a fire pit
- forge_hall:     a forge with tall chimneys, magma hearths and anvils (器坊)
- cloud_ship:     a flying ship (云舟) with a cabin, masts and sails, floating on a cushion of cloud
- pier:           a stone pier sticking out over a cliff, for the ships (云舟码头)
- giant_sword:    a huge sword stuck in the ground (剑修)
- stele:          a stele on a tortoise base (道心碑)
- star_altar:     a round three-tier altar with an armillary sphere (观星台)
- spirit_field:   spirit-herb beds with glowing plants (灵田)
All take a Frame from arch.py (front = +v) unless noted.
"""
import math

from arch import AIR, B, Pal, ROOF, _h, balustrade, hall, lamp_cells, lantern_post, masonry, pavilion, put_lamp, slab, stair, xumizuo


def cliff_cave(f, y, depth=10, width=7, height=6, seed=0, balcony=4):
    """Carve a dwelling into the rock behind the cliff face. The frame's origin is on the face; +v points out of
    the cliff. Room goes back to v = -depth."""
    hw = width // 2
    for u in range(-hw, hw + 1):
        for v in range(-depth, 1):
            for k in range(1, height + 1):
                f.put(u, y + k, v, AIR)
            f.put(u, y, v, B("polished_andesite") if (u + v) % 2 else B("stone_bricks"))
            f.put(u, y + height + 1, v, B("stone_bricks") if f.get(u, y + height + 1, v) in (None, AIR) else f.get(u, y + height + 1, v))
    # inner lining so the cave reads as a room and not a hole: red pillars at the mouth, lintel, side lamps
    for s in (-1, 1):
        for k in range(1, height + 1):
            f.put(s * (hw + 1), y + k, 0, Pal.COL)
    for u in range(-hw - 1, hw + 2):
        f.put(u, y + height + 1, 0, Pal.RED)
        f.put(u, y + height + 1, 1, stair(ROOF["tile"], "north"))                        # tiled hood
        f.put(u, y + height + 2, 0, slab(ROOF["tile"]))

    for s in (-1, 1):
        put_lamp(f, "stand", s * hw, y + 2, -depth + 1)
    # door: half-open lattice leaves
    for k in range(1, height):
        f.put(-hw, y + k, 0, B("spruce_trapdoor", facing="south", half="bottom", open=True))
        f.put(hw, y + k, 0, B("spruce_trapdoor", facing="south", half="bottom", open=True))
    # balcony (平台) sticking out, with a fence and two brackets under it
    for u in range(-hw - 1, hw + 2):
        for v in range(1, balcony + 1):
            f.put(u, y, v, B("spruce_planks"))
            for k in range(1, 4):
                f.put(u, y + k, v, AIR)
        f.put(u, y + 1, balcony, B("spruce_fence", east=True, west=True) if abs(u) > 1 else AIR)
    for s in (-1, 1):
        f.put(s * (hw + 1), y + 1, balcony, B("spruce_fence", west=True) if s > 0 else B("spruce_fence", east=True))
        for v in range(1, balcony + 1):
            f.put(s * (hw + 1), y + 1, v, B("spruce_fence", north=True, south=True))
        f.put(s * hw, y - 1, 2, stair("spruce", "south", "top"))
        f.put(s * hw, y - 2, 1, stair("spruce", "south", "top"))
    if _h(seed) % 3 == 0:
        f.put(hw - 1, y + 1, 3, B("potted_azalea_bush"))


def plank_walk(cv, pts, y, width=3):
    """栈道: a plank walkway along a list of world (x, z) points at height y, held by beams into the rock."""
    for (x0, z0), (x1, z1) in zip(pts, pts[1:]):
        n = max(abs(x1 - x0), abs(z1 - z0), 1)
        for i in range(n + 1):
            x = round(x0 + (x1 - x0) * i / n)
            z = round(z0 + (z1 - z0) * i / n)
            for dz in range(0, width):
                for dx in range(-(width // 2), width // 2 + 1):
                    if cv.get(x + dx, y, z + dz) in (None, AIR):
                        cv.set(x + dx, y, z + dz, B("spruce_slab", type="top"))
            if i % 6 == 0:
                cv.set(x, y - 1, z + width - 1, B("spruce_fence"))
                cv.set(x, y - 2, z + width - 1, B("spruce_fence"))
                cv.set(x, y + 1, z + width - 1, B("spruce_fence"))
                if i % 18 == 0:                       # a lamp on the rail every third post
                    for dy, st in lamp_cells("rail"):
                        cv.set(x, y + 2 + dy, z + width - 1, st)
                else:
                    cv.set(x, y + 2, z + width - 1, B("spruce_fence"))
            elif cv.get(x, y + 1, z + width - 1) in (None, AIR):
                cv.set(x, y + 1, z + width - 1, B("spruce_fence", east=True, west=True))


def formation_pillar(f, y_ground, y, height=48, color="light_blue", ground_fill=True):
    """护山大阵 anchor: a 5x5 dark stone pillar with gold bands, runes (glowing) and a beacon on a 3x3 iron base on
    top, so a light column rises into the sky. y_ground: where the foundation starts; y: base of the visible pillar."""
    for u in range(-3, 4):
        for v in range(-3, 4):
            if abs(u) + abs(v) > 5:
                continue
            f.col(u, v, y_ground, y, B("polished_blackstone_bricks"))
    for k in range(1, height):
        for u in range(-2, 3):
            for v in range(-2, 3):
                if abs(u) == 2 and abs(v) == 2:
                    continue
                edge = abs(u) == 2 or abs(v) == 2
                if not edge:
                    f.put(u, y + k, v, B("polished_blackstone"))
                    continue
                band = k % 12 in (0, 1)
                rune = (k % 12 == 6) and (u == 0 or v == 0)
                f.put(u, y + k, v, B("gold_block") if band else B("sea_lantern") if rune else B("polished_blackstone_bricks"))
    top = y + height
    for u in range(-2, 3):
        for v in range(-2, 3):
            f.put(u, top, v, B("iron_block") if abs(u) <= 1 and abs(v) <= 1 else B("chiseled_polished_blackstone"))
    f.put(0, top + 1, 0, B("beacon"))
    f.put(0, top + 2, 0, B(color + "_stained_glass"))
    for u, v in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        f.put(u, top + 1, v, B("end_rod", facing="up"))


def dan_furnace(f, y, r=9, seed=0):
    """丹炉: three legs over a fire pit, a round belly, a stepped lid with a knob, two side handles. Copper body."""
    body = B("cut_copper")
    dark = B("exposed_cut_copper")
    for u in range(-r - 2, r + 3):          # fire pit
        for v in range(-r - 2, r + 3):
            d = math.hypot(u, v)
            if d <= r + 2:
                f.put(u, y, v, B("magma_block") if d < r - 1 else B("polished_blackstone_bricks"))
                if d < r - 2 and _h(u, v, seed) % 5 == 0:
                    f.put(u, y + 1, v, B("campfire", lit=True))
    for k in range(3):                      # legs
        a = k * 2 * math.pi / 3 + 0.5
        lu, lv = round((r - 1) * math.cos(a)), round((r - 1) * math.sin(a))
        for dy in range(1, 8):
            for du in (-1, 0, 1):
                for dv in (-1, 0, 1):
                    f.put(lu + du, y + dy, lv + dv, dark)
    cy = y + 8 + r
    for u in range(-r - 1, r + 2):          # belly: sphere flattened at the top
        for v in range(-r - 1, r + 2):
            for dy in range(-r, r // 2 + 1):
                d = math.sqrt(u * u + v * v + (dy * 1.1) ** 2)
                if r - 1.2 <= d <= r + 0.4:
                    f.put(u, cy + dy, v, body if (dy % 4) else dark)
    for u in range(-r, r + 1):              # lid, stepped
        for v in range(-r, r + 1):
            d = math.hypot(u, v)
            for step, rad in enumerate((r * 0.8, r * 0.55, r * 0.3)):
                if d <= rad:
                    f.put(u, cy + r // 2 + 1 + step, v, dark if step % 2 else body)
    f.put(0, cy + r // 2 + 4, 0, B("gold_block"))
    f.put(0, cy + r // 2 + 5, 0, B("campfire", lit=True))   # smoke from the knob
    for s in (-1, 1):                       # handles
        for dy in range(0, 5):
            f.put(s * (r + 1), cy + r // 2 + dy - 2, 0, dark)
        f.put(s * (r + 2), cy + r // 2 + 2, 0, dark)


def forge_hall(f, y, seed=0):
    """器坊: a long hall with three tall brick chimneys, magma hearths in front and anvils."""
    hall(f, y, [7, 9, 9, 9, 7], [7, 9, 7], fujie=False, roof="yingshan", tiers=((2, 2),), col_low=10)
    for k, u in enumerate((-14, 0, 14)):
        for dy in range(0, 34 + 4 * (k == 1)):
            for du in (-1, 0, 1):
                for dv in (-1, 0, 1):
                    f.put(u + du, y + 3 + dy, -6 + dv, B("bricks") if (du or dv) else AIR)
        f.put(u, y + 37 + 4 * (k == 1), -6, B("campfire", lit=True))
    for u in range(-18, 19, 6):
        f.put(u, y + 1, 22, B("magma_block"))
        f.put(u, y + 1, 24, B("anvil", facing="east"))
        f.put(u + 2, y + 1, 22, B("blast_furnace", facing="south", lit=True))


def cloud_ship(f, y, length=48, beam=12, seed=0):
    """云舟: a boat-shaped hull (dark oak, gold trim), a deck, a two-storey cabin, two masts with sails, prow and
    stern ornaments, and a cushion of cloud under the keel. Local u = along the ship (bow at +u)."""
    hl, hb = length // 2, beam // 2
    for u in range(-hl, hl + 1):
        t = u / hl
        half = hb * math.sqrt(max(0.0, 1 - max(0.0, t) ** 2 * 0.95)) if t > 0 else hb * math.sqrt(max(0.0, 1 - t * t * 0.6))
        depth = 6 * math.sqrt(max(0.0, 1 - t * t * 0.8))
        for v in range(-hb - 1, hb + 2):
            if abs(v) > half:
                continue
            bottom = y - round(depth * (1 - (v / max(half, 1)) ** 2) ** 0.5) - 1
            f.col(u, v, bottom, y - 1, B("dark_oak_planks"))
            edge = abs(v) > half - 1.2
            f.put(u, y, v, B("dark_oak_planks") if edge else B("spruce_planks"))
            if edge:
                f.put(u, y + 1, v, B("dark_oak_fence", east=True, west=True))
                f.put(u, y - 1, v, B("gold_block") if u % 4 == 0 else B("stripped_dark_oak_wood", axis="x"))
            # cloud cushion
            if abs(v) < half - 1 and _h(u, v, seed) % 3:
                for k in range(2 + _h(u, v) % 3):
                    f.put(u, bottom - 2 - k, v, B("white_wool") if k < 2 else B("white_stained_glass"))
    # prow ornament and stern castle
    for k in range(6):
        f.put(hl + k // 2, y + 1 + k, 0, B("gold_block") if k > 3 else B("dark_oak_log", axis="y"))
    hall(f.sub(-hl // 2, 0, "west"), y, [5, 6, 5], [4, 5], fujie=False, roof="xieshan", tiers=(), col_low=6)
    # masts and sails
    for mu, mh in ((hl // 4, 28), (-hl // 6, 22)):
        for k in range(1, mh):
            f.put(mu, y + k, 0, B("spruce_log", axis="y"))
        for k in range(mh // 3, mh - 3):
            w = 2 + (k - mh // 3) // 2
            w = min(w, hb + 3)
            for v in range(-w, w + 1):
                f.put(mu + 1, y + k, v, B("white_wool") if (k + v) % 7 else B("light_gray_wool"))
        f.put(mu, y + mh, 0, B("red_wool"))
        f.put(mu, y + mh + 1, 0, B("red_wool"))


def pier(f, y, length=70, width=11, ground=None):
    """云舟码头: a stone pier out over the cliff along +v, on corbels; lanterns and mooring posts."""
    hw = width // 2
    for v in range(0, length + 1):
        for u in range(-hw, hw + 1):
            f.put(u, y, v, Pal.JADE if abs(u) <= 1 else Pal.FLOOR)
            f.put(u, y - 1, v, B("stone_bricks"))
            f.put(u, y - 2, v, B("stone_brick_slab", type="top") if abs(u) < hw else B("stone_bricks"))
        for s in (-1, 1):
            f.put(s * hw, y + 1, v, B("quartz_pillar", axis="y") if v % 6 == 0 else B("diorite_wall", north="low", south="low", up=False))
            if v % 12 == 0:
                lantern_post(f, s * (hw - 1), y, v)
        if v % 9 == 0:                       # corbel arches under the deck
            for k in range(3, 12):
                for u in (-hw, hw):
                    f.put(u, y - k, v, B("stone_bricks"))
    if ground is not None:
        for u in range(-hw, hw + 1):
            for v in range(-6, 1):
                x, z = f.w(u, v)
                g = ground(x, z)
                if g < y - 1:
                    f.col(u, v, g + 1, y - 1, masonry(x, 0, z))


def giant_sword(f, y, length=60, width=5, seed=0):
    """A huge sword stuck point-down into the ground, leaning a little; guard, grip and pommel above."""
    hw = width // 2
    lean = (_h(seed) % 3 - 1) * 0.12
    tip = y - 8
    for k in range(length):
        yy = tip + k
        off = round(lean * k)
        w = min(hw, k // 3)                  # tapered point
        for u in range(-w, w + 1):
            f.put(u + off, yy, 0, B("iron_block") if abs(u) < w or w == 0 else B("smooth_quartz"))
        f.put(off, yy, 1, B("iron_block") if k > 3 else B("smooth_quartz"))
        f.put(off, yy, -1, B("iron_block") if k > 3 else B("smooth_quartz"))
    gy = tip + length
    goff = round(lean * length)
    for u in range(-hw - 4, hw + 5):          # guard
        f.put(u + goff, gy, 0, B("gold_block"))
        f.put(u + goff, gy, 1, B("gold_block") if abs(u) <= hw + 1 else AIR)
        f.put(u + goff, gy, -1, B("gold_block") if abs(u) <= hw + 1 else AIR)
    for k in range(1, 12):                   # grip
        f.put(goff, gy + k, 0, B("dark_oak_log", axis="y") if k % 3 else B("red_wool"))
    f.put(goff, gy + 12, 0, B("gold_block"))
    f.put(goff, gy + 13, 0, B("gold_block"))
    for k in range(1, 6):                    # tassel
        f.put(goff + 1, gy + 12 - k, 0, B("red_wool") if k < 5 else B("red_carpet"))


def stele(f, y, height=22, width=9):
    """道心碑: a tablet of polished blackstone with gold inscription dots on a tortoise (赑屃) base."""
    for u in range(-width // 2 - 3, width // 2 + 4):
        for v in range(-5, 6):
            d = (u / (width / 2 + 3)) ** 2 + (v / 5.5) ** 2
            if d <= 1:
                for k in range(1, 4 if d < 0.6 else 3):
                    f.put(u, y + k, v, B("tuff") if k < 3 else B("mossy_cobblestone"))
    for u in range(-1, 2):
        f.put(u, y + 3, 6, B("tuff"))
        f.put(u, y + 4, 6, B("tuff"))
    hw = width // 2
    for u in range(-hw, hw + 1):
        for k in range(4, 4 + height):
            for v in (-1, 0, 1):
                f.put(u, y + k, v, B("polished_blackstone"))
            if abs(u) < hw - 1 and 6 < k < height and (u + k) % 3 == 0:
                f.put(u, y + k, 2, B("gold_block") if k % 2 else B("polished_blackstone_button", face="wall", facing="south"))
    for u in range(-hw - 1, hw + 2):          # cap
        for v in (-2, -1, 0, 1, 2):
            f.put(u, y + 4 + height, v, B("polished_blackstone_bricks"))
        f.put(u, y + 5 + height, 0, B("polished_blackstone_brick_slab", type="bottom"))


def star_altar(f, y, r=24, ground=None):
    """观星台: three round tiers (like 天坛's 圜丘) with balustrades, and an armillary sphere of gold and copper rings."""
    for tier, (rr, hh) in enumerate(((r, 3), (r - 7, 3), (r - 13, 3))):
        top = y + 3 * (tier + 1)
        for u in range(-rr, rr + 1):
            for v in range(-rr, rr + 1):
                d = math.hypot(u, v)
                if d > rr:
                    continue
                if tier == 0 and ground is not None:
                    x, z = f.w(u, v)
                    g = ground(x, z)
                    if g < top - hh:
                        f.col(u, v, g + 1, top - hh, B("stone_bricks"))
                for k in range(top - hh + 1, top + 1):
                    f.put(u, k, v, Pal.JADE if k == top else B("quartz_bricks"))
                if rr - 1 <= d <= rr and (abs(u) > 2 and abs(v) > 2 or (abs(u) > 2) != (abs(v) > 2)):
                    if not (abs(u) <= 2 or abs(v) <= 2):
                        f.put(u, top + 1, v, B("quartz_pillar", axis="y") if (u + v) % 4 == 0 else B("diorite_wall"))
        for a in range(4):                    # stairs on the four axes
            for k in range(3):
                d = rr + 1 + k
                du, dv = [(0, 1), (1, 0), (0, -1), (-1, 0)][a]
                for w in range(-2, 3):
                    u, v = du * d + dv * w, dv * d + du * w
                    face = ["north", "west", "south", "east"][a]
                    f.put(u, top - 1 - k, v, stair("quartz", face))
                    for yy in range(top - 2 - k, y, -1):   # filled under (over a lower tier: that stays)
                        if f.get(u, yy, v) in (None, AIR):
                            f.put(u, yy, v, B("quartz_bricks"))
    cy = y + 9 + 9
    for t in range(0, 360, 3):                # armillary sphere: three rings
        a = math.radians(t)
        for ring, (ax, mat) in enumerate((("y", B("gold_block")), ("x", B("cut_copper")), ("z", B("cut_copper")))):
            R = 7 - ring
            c, s = round(R * math.cos(a)), round(R * math.sin(a))
            if ax == "y":
                f.put(c, cy, s, mat)
            elif ax == "x":
                f.put(0, cy + s, c, mat)
            else:
                f.put(c, cy + s, 0, mat)
    for k in range(9, 18):
        f.put(0, y + k, 0, B("chain", axis="y"))
    f.put(0, cy, 0, B("sea_lantern"))


def spirit_field(f, u0, v0, u1, v1, y, seed=0):
    """灵田: terraced beds of glowing / rare plants with water channels lit from below."""
    plants = [B("torchflower"), B("pitcher_plant", half="lower"), B("allium"), B("blue_orchid"), B("lily_of_the_valley")]
    for u in range(u0, u1 + 1):
        for v in range(v0, v1 + 1):
            k = (v - v0) % 6
            if k == 0:
                f.put(u, y, v, B("water"))
                f.put(u, y - 1, v, B("sea_lantern") if u % 5 == 0 else B("prismarine_bricks"))
            elif k == 5:
                f.put(u, y, v, B("moss_block"))
                if u % 7 == 0:
                    f.put(u, y + 1, v, B("end_rod", facing="up"))
            else:
                f.put(u, y, v, B("farmland", moisture=7))
                p = plants[(u // 5 + v // 6 + seed) % len(plants)]
                f.put(u, y + 1, v, p)
                if p.endswith("half=lower]"):
                    f.put(u, y + 2, v, B("pitcher_plant", half="upper"))
