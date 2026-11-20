package com.flyingeaves.gen;

import com.flyingeaves.gen.py.IntPair;
import com.flyingeaves.gen.py.Py;
import com.flyingeaves.gen.py.PyRandom;
import com.flyingeaves.gen.py.PySet;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.flyingeaves.gen.Blocks.AIR;

/**
 * compose.py: a building from the rooms the player drew (单体建造) - rooms lined up (odd widths, shared floors),
 * doorways where they touch, every room built on its own canvas (bgen.house) and the parts joined, then the
 * finishing passes. Rooms are maps as in Python (the request's fields plus the ones computed here).
 */
public final class Compose {
    private Compose() {
    }

    static final List<String> OPENS = java.util.Arrays.asList("closed", "front", "open", "through");
    static final List<String> DOORS = java.util.Arrays.asList("front", "front_back", "four");
    static final Map<String, String[]> OLD_OPEN = Map.of("closed", new String[]{"front", "closed"}, "open", new String[]{"front", "open"},
            "front", new String[]{"front", "front"}, "through", new String[]{"front_back", "through"}, "back", new String[]{"front_back", "closed"},
            "four", new String[]{"four", "closed"});
    static final int MAX_SIDE = 256;
    static final List<String> WALL_SLOTS = List.of("fill", "post", "beam", "base", "dado", "pane", "door", "win", "trim_stairs", "trim_slab", "trim_trapdoor",
            "base_stairs", "floor", "rail", "steps", "inner_stairs", "ladder");
    static final List<String> ROOF_SLOTS = List.of("full", "stairs", "slab", "stripe_stairs", "stripe_slab", "ridge", "cap");
    static final int MAX_MARGIN = 32;
    static final int LEVEL_UP = 4;
    static final List<String> FLAT_SHAPES = java.util.Arrays.asList("paving", "river", "terrace", "street", "stairs", "bridge");
    static final List<String> GROUND_SHAPES = java.util.Arrays.asList("terrace", "street", "stairs");
    static final List<String> COMPOUND_SHAPES = java.util.Arrays.asList("wall", "gate", "glazedgate", "chuihua", "paving", "river", "terrace", "street", "stairs");
    static final List<String> PALACE_WALLS = java.util.Arrays.asList("palace", "yellow", "jade");
    public static final List<String> SPECIAL_SHAPES = java.util.Arrays.asList("paifang", "screen", "gate", "glazedgate", "chuihua", "wall", "paving", "river", "ornament",
            "terrace", "street", "stairs", "bridge");
    static final List<String> PATCH_KEYS = java.util.Arrays.asList("variant", "walls", "theme", "base", "seed", "sh", "roof_variant");
    static final Map<String, Integer> FRONT_I = Map.of("south", 0, "east", 1, "north", 2, "west", 3);
    static final List<String> I_FRONT = java.util.Arrays.asList("south", "east", "north", "west");
    static final int THICK = 3;

    /** The daemon sets this while it builds a preview: throws Superseded when a newer one waits. */
    public static Runnable CANCEL = () -> {
    };
    public static java.util.function.BiConsumer<Integer, Integer> PROGRESS = (done, total) -> {
    };

    public static final class Superseded extends RuntimeException {
    }

    // ------------------------------------------------------------------------------------------------ room helpers

    static String shape(Map<String, Object> r) {
        Object s = r.get("shape");
        return s == null ? null : (String) s;
    }

    static boolean special(Map<String, Object> r) {
        return SPECIAL_SHAPES.contains(shape(r));
    }

    static int gi(Map<String, Object> r, String k) {
        return Dyn.toInt(r.get(k));
    }

    /** compose.doors_open: (doors, open). */
    static String[] doorsOpen(Map<String, Object> r) {
        String o = Dyn.truthy(r.get("open")) ? Dyn.str(r.get("open")) : "closed";
        if (o.equals("back") || o.equals("four")) return OLD_OPEN.get(o);
        String d = r.get("doors") != null && DOORS.contains(r.get("doors")) ? (String) r.get("doors") : "front";
        o = OPENS.contains(o) ? o : "closed";
        return new String[]{o.equals("through") && d.equals("front") ? "front_back" : d, o};
    }

    static final Object[] BASES = {null};

    /** compose.load_bases. */
    static LinkedHashMap<String, Map<String, Object>> loadBases() {
        LinkedHashMap<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (Path d : Env.dirs("materials")) {
            for (Path p : Env.glob(d, "bases")) {
                Object bs = Json.parseObject(Env.read(p)).get("bases");
                if (!(bs instanceof Map)) continue;
                for (Map.Entry<String, Object> en : Dyn.map(bs).entrySet()) {
                    Map<String, Object> e = Dyn.map(en.getValue());
                    List<Object> blocks = new ArrayList<>();
                    for (Object b : e.get("blocks") == null ? List.of() : Dyn.list(e.get("blocks"))) if (Env.present((String) b)) blocks.add(b);
                    if (en.getKey().equals("auto") || en.getKey().equals("wall") || !blocks.isEmpty()) {
                        Map<String, Object> m = new LinkedHashMap<>(e);
                        m.put("blocks", blocks);
                        out.put(en.getKey(), m);
                    }
                }
            }
        }
        return out;
    }

    /** compose.base_block: the room's base block, or null for the default stone. */
    @SuppressWarnings("unchecked")
    static String baseBlock(Map<String, Object> r, String walls) {
        String bid = Dyn.truthy(r.get("base")) ? (String) r.get("base") : "auto";
        if (bid.equals("auto")) return null;
        if (bid.equals("wall")) {
            Map<String, Object> w = Bgen.WALLS.get(walls);
            return w == null ? null : (String) w.get("base");
        }
        if (BASES[0] == null) BASES[0] = loadBases();
        Map<String, Object> e = ((Map<String, Map<String, Object>>) BASES[0]).get(bid);
        if (e != null && Dyn.truthy(e.get("blocks"))) return Blocks.B((String) Dyn.list(e.get("blocks")).get(0));
        return null;
    }

    /** compose._override: a style with some slots changed, registered under a name of its own. */
    static String override(String name, Object over, Map<String, Map<String, Object>> table, boolean roof) {
        if (!Dyn.truthy(over) || !table.containsKey(name)) return name;
        // (Python names it by hash(tuple(sorted(over.items()))), which follows PYTHONHASHSEED; the name never reaches
        // the blocks, so any name unique to these slots will do)
        java.util.TreeMap<String, Object> sorted = new java.util.TreeMap<>(Dyn.map(over));
        String key = "_board_" + name + "_" + Integer.toHexString(sorted.toString().hashCode());
        if (!table.containsKey(key)) {
            Map<String, Object> e = new LinkedHashMap<>(table.get(name));
            List<String> slots = roof ? ROOF_SLOTS : WALL_SLOTS;
            for (Map.Entry<String, Object> kv : Dyn.map(over).entrySet()) if (slots.contains(kv.getKey())) e.put(kv.getKey(), kv.getValue());
            if (roof) {
                e.put("tile", "roof:" + key);
                Arch.registerRoofSets(Map.of(key, e));
            }
            table.put(key, e);
        }
        return key;
    }

    static boolean overlap(Map<String, Object> a, Map<String, Object> b, String lo, String hi) {
        return Math.min(gi(a, hi), gi(b, hi)) - Math.max(gi(a, lo), gi(b, lo)) >= 2;
    }

    /** compose._touch. */
    static boolean touch(Map<String, Object> a, Map<String, Object> b, String lo, String hi, String olo, String ohi) {
        int d = gi(b, lo) - gi(a, hi);
        return 0 <= d && d <= 1 && overlap(a, b, olo, ohi);
    }

    /** compose._local. */
    static int[] local(Map<String, Object> room, int x, int z) {
        int[] r = Blocks.ROT.get((String) room.get("front"));
        int dx = x - gi(room, "cx"), dz = z - gi(room, "cz");
        return new int[]{r[0] * dx + r[2] * dz, r[1] * dx + r[3] * dz};
    }

    /** compose._podium_margin. */
    static Integer podiumMargin(Map<String, Object> r) {
        String m = (r.containsKey("podium_margin") ? Dyn.str(r.get("podium_margin")) : "").strip();
        if (!m.isEmpty() && m.chars().allMatch(Character::isDigit) && Integer.parseInt(m) >= 1) return Math.min(Integer.parseInt(m), MAX_MARGIN);
        return null;
    }

    /** compose._pod. */
    static int pod(Map<String, Object> r) {
        return Math.max(0, Math.min(16, Dyn.truthy(r.get("podium")) ? Dyn.toInt(r.get("podium")) : 0));
    }

    static int taper(Map<String, Object> r) {
        return Math.max(0, Math.min(3, Dyn.truthy(r.get("taper")) ? Dyn.toInt(r.get("taper")) : 0));
    }

    /** compose._floor. */
    static int floor(Map<String, Object> r) {
        if ("round".equals(shape(r))) return gi(r, "y") + pod(r);
        return gi(r, "y") + Math.max(0, pod(r));
    }

    static final int[][] DIRS4 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    @SuppressWarnings("unchecked")
    static Map<IntPair, Integer> sideDoors(Map<String, Object> r) {
        return (Map<IntPair, Integer>) r.get("side_doors");
    }

    /** compose._door_ok. */
    static boolean doorOk(Map<String, Object> room, int u, int v) {
        int hx = gi(room, "hx"), hz = gi(room, "hz"), n = gi(room, "n"), sh = gi(room, "sh");
        if (Math.abs(u) == hx && Math.abs(v) == hz) return false;
        if (v == hz && Math.abs(u) <= 2 && !Dyn.truthy(room.get("no_front"))) return false;
        int iu, iv;
        if (Math.abs(u) == hx) {
            iu = u - (u > 0 ? 1 : -1);
            iv = v;
        } else {
            iu = u;
            iv = v - (v > 0 ? 1 : -1);
        }
        return !(n > 1 && Bgen.stairPlan(hx, hz, sh).reserve.contains(IntPair.of(iu, iv)));
    }

    /** compose._tower_doors (round rooms: not ported with round_room yet). */
    static void towerDoors(List<Map<String, Object>> rooms) {
        RoundRoom.towerDoors(rooms);
    }

    /** compose._wall_walks. */
    static List<Object[]> wallWalks(List<Map<String, Object>> rooms) {
        List<Map<String, Object>> towers = new ArrayList<>();
        for (Map<String, Object> t : rooms)
            if ("chengtai".equals(t.get("podium_style")) && pod(t) > 0 && t.get("_on") == null && !"round".equals(shape(t)) && !special(t)) towers.add(t);
        List<Object[]> out = new ArrayList<>();
        for (Map<String, Object> r : rooms) {
            String s = shape(r);
            if (!("wall".equals(s) || "gate".equals(s) || "glazedgate".equals(s)) || gi(r, "hz") < THICK || r.get("_on") != null) continue;
            int ht = gi(r, "hz") - Math.max(0, Math.min(taper(r), gi(r, "hz") - 3));
            int y = gi(r, "y") + gi(r, "sh");
            int[] R = Blocks.ROT.get((String) r.get("front"));
            for (int sg : new int[]{-1, 1}) {
                for (Map<String, Object> t : towers) {
                    int m = Platforms.chengtaiSize(pod(t), 0, podiumMargin(t))[0];
                    int[] rim = {gi(t, "x0") - m, gi(t, "z0") - m, gi(t, "x1") + m, gi(t, "z1") + m};
                    List<int[]> cells = null;
                    for (int k = 0; k < 2; k++) {
                        int u = sg * (gi(r, "hx") + k);
                        cells = new ArrayList<>();
                        for (int v = -ht + 1; v < ht; v++) cells.add(new int[]{gi(r, "cx") + R[0] * u + R[1] * v, gi(r, "cz") + R[2] * u + R[3] * v});
                        boolean allRim = true;
                        for (int[] c : cells) if (!(c[0] == rim[0] || c[0] == rim[2] || c[1] == rim[1] || c[1] == rim[3])) allRim = false;
                        if (k == 1 || allRim) break;
                    }
                    boolean inside = true;
                    for (int[] c : cells)
                        if (!(rim[0] <= c[0] && c[0] <= rim[2] && rim[1] <= c[1] && c[1] <= rim[3]
                                && !(gi(t, "x0") <= c[0] && c[0] <= gi(t, "x1") && gi(t, "z0") <= c[1] && c[1] <= gi(t, "z1")))) inside = false;
                    if (floor(t) != y || !inside) continue;
                    int[] mid = cells.get(Math.floorDiv(cells.size(), 2));
                    int[] l = local(t, mid[0], mid[1]);
                    int lu = l[0], lv = l[1];
                    int thx = gi(t, "hx"), thz = gi(t, "hz");
                    List<int[]> face = new ArrayList<>();
                    for (int k : new int[]{0, 1, -1, 2, -2}) {
                        if (Math.abs(lu) > thx) face.add(new int[]{(lu > 0 ? 1 : -1) * thx, Math.max(-thz + 2, Math.min(thz - 2, lv + k))});
                        else face.add(new int[]{Math.max(-thx + 2, Math.min(thx - 2, lu + k)), (lv > 0 ? 1 : -1) * thz});
                    }
                    Object[] door = null;
                    if (!(face.get(0)[1] == thz && Math.abs(face.get(0)[0]) <= 2 && !Dyn.truthy(t.get("no_front")))) {
                        int[] T = Blocks.ROT.get((String) t.get("front"));
                        for (int[] fc : face) {
                            int fu = fc[0], fv = fc[1];
                            if (doorOk(t, fu, fv)) {
                                Map<IntPair, Integer> sd = sideDoors(t);
                                sd.put(IntPair.of(fu, fv), Math.max(1, sd.getOrDefault(IntPair.of(fu, fv), 0)));
                                int du, dv;
                                if (Math.abs(fu) == thx) {
                                    du = fu > 0 ? 1 : -1;
                                    dv = 0;
                                } else {
                                    du = 0;
                                    dv = fv > 0 ? 1 : -1;
                                }
                                door = new Object[]{gi(t, "cx") + T[0] * fu + T[1] * fv, gi(t, "cz") + T[2] * fu + T[3] * fv, T[0] * du + T[1] * dv,
                                        T[2] * du + T[3] * dv, new int[]{gi(t, "x0"), gi(t, "z0"), gi(t, "x1"), gi(t, "z1")}};
                                break;
                            }
                        }
                    }
                    out.add(new Object[]{cells, y, door});
                    break;
                }
            }
        }
        return out;
    }

    /** compose._ladder_corner. */
    static int[] ladderCorner(Map<String, Object> r) {
        int hx = gi(r, "hx"), hz = gi(r, "hz");
        Set<IntPair> busy = new HashSet<>();
        if (gi(r, "n") > 1) {
            Bgen.StairPlan sp = Bgen.stairPlan(hx, hz, gi(r, "sh"));
            busy.addAll(sp.reserve);
            busy.addAll(sp.well);
            busy.addAll(sp.above);
        }
        int[][] cs = {{-hx + 1, -hz + 1}, {hx - 1, -hz + 1}, {hx - 1, hz - 1}, {-hx + 1, hz - 1}};
        for (int[] c : cs) if (!busy.contains(IntPair.of(c[0], c[1]))) return c;
        return cs[0];
    }

    /** compose._ladder_free. */
    @SuppressWarnings("unchecked")
    static void ladderFree(List<Map<String, Object>> rooms, Map<Integer, Map<String, Object>> byI) {
        for (Map<String, Object> r : rooms) {
            if (r.get("_on") == null || special(r) || "round".equals(shape(r))) continue;
            int[] c = ladderCorner(r);
            int cu = c[0], cvv = c[1];
            int[] R = Blocks.ROT.get((String) r.get("front"));
            int[][] cells = {{cu, cvv}, {cu, cvv + (cvv < 0 ? 1 : -1)}, {cu + (cu < 0 ? 1 : -1), cvv}};
            for (int[] uv : cells) {
                int u = uv[0], v = uv[1];
                Map<IntPair, Integer> free = (Map<IntPair, Integer>) r.computeIfAbsent("free", k -> new LinkedHashMap<IntPair, Integer>());
                free.put(IntPair.of(u, v), Math.max(1, free.getOrDefault(IntPair.of(u, v), 0)));
                Map<String, Object> low = byI.get(gi(r, "_on"));
                int[] l = local(low, gi(r, "cx") + R[0] * u + R[1] * v, gi(r, "cz") + R[2] * u + R[3] * v);
                if (Math.abs(l[0]) < gi(low, "hx") && Math.abs(l[1]) < gi(low, "hz") && gi(low, "n") == 1) {
                    Map<IntPair, Integer> lf = (Map<IntPair, Integer>) low.computeIfAbsent("free", k -> new LinkedHashMap<IntPair, Integer>());
                    lf.put(IntPair.of(l[0], l[1]), Math.max(gi(low, "n"), lf.getOrDefault(IntPair.of(l[0], l[1]), 0)));
                }
            }
        }
    }

    /** compose._stack_ladder. */
    @SuppressWarnings("unchecked")
    static void stackLadder(Canvas cv, Map<String, Object> r, Map<String, Object> low, Map<String, Object> up) {
        Map<String, Object> lres = (Map<String, Object>) low.get("res"), ures = (Map<String, Object>) up.get("res");
        int lo = gi(lres, "floor") + (gi(low, "n") - 1) * gi(lres, "sh") + 1;
        int hi = gi(ures, "floor");
        if (hi < lo) return;
        int x, z, px, pz;
        String facing;
        if ("round".equals(shape(r))) {
            x = gi(r, "cx");
            z = gi(r, "cz") - gi(r, "hx") + 1;
            px = gi(r, "cx");
            pz = gi(r, "cz") - gi(r, "hx");
            facing = "south";
        } else {
            int[] c = ladderCorner(r);
            int cu = c[0], cvv = c[1];
            int[] R = Blocks.ROT.get((String) r.get("front"));
            x = gi(r, "cx") + R[0] * cu + R[1] * cvv;
            z = gi(r, "cz") + R[2] * cu + R[3] * cvv;
            int pu = cu + (cu > 0 ? 1 : -1);
            px = gi(r, "cx") + R[0] * pu + R[1] * cvv;
            pz = gi(r, "cz") + R[2] * pu + R[3] * cvv;
            facing = Blocks.worldDir((String) r.get("front"), cu > 0 ? "west" : "east");
        }
        Map<String, Object> lw = Bgen.WALLS.get((String) low.get("walls"));
        String post = lw != null && Dyn.truthy(lw.get("post")) ? (String) lw.get("post") : "minecraft:stripped_spruce_log[axis=y]";
        for (int y = lo; y <= hi; y++) {
            String b = cv.blocks.get(Canvas.key(px, y, pz));
            if (b == null || b.equals("minecraft:air")) cv.set(px, y, pz, post);
            cv.set(x, y, z, "minecraft:ladder[facing=" + facing + ",waterlogged=false]");
        }
        for (int y : new int[]{hi + 1, hi + 2}) {
            String b = cv.blocks.get(Canvas.key(x, y, z));
            if (!(b == null || b.equals("minecraft:air")) && !String.valueOf(b).contains("wall")) cv.set(x, y, z, "minecraft:air");
        }
    }

    /** compose.expand_rooms. */
    static List<Map<String, Object>> expandRooms(List<Object> rooms) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : rooms) {
            Map<String, Object> r = Dyn.map(o);
            Object parts = r.get("parts");
            String sh = Dyn.truthy(r.get("shape")) ? shape(r) : (String) r.get("special");
            if (!"patch".equals(sh) || !Dyn.truthy(parts)) {
                out.add(r);
                continue;
            }
            out.addAll(Terrain.expandPatch(r));
        }
        return out;
    }

    /** compose.compound_ground. */
    static Object[] compoundGround(Canvas cv, List<Map<String, Object>> rooms) {
        List<Map<String, Object>> ground = new ArrayList<>();
        for (Map<String, Object> r : rooms) if (r.get("_on") == null) ground.add(r);
        boolean any = false;
        for (Map<String, Object> r : ground) if (COMPOUND_SHAPES.contains(shape(r))) any = true;
        if (!any) return null;
        List<Integer> flat = new ArrayList<>();
        for (Map<String, Object> r : ground) if (special(r)) flat.add(gi(r, "y"));
        int g;
        if (!flat.isEmpty()) {
            Integer best = null;
            int bc = -1;
            for (int v : PySet.of(flat)) {
                int c = java.util.Collections.frequency(flat, v);
                if (best == null || c > bc) {
                    best = v;
                    bc = c;
                }
            }
            g = best;
        } else {
            g = Integer.MAX_VALUE;
            for (Map<String, Object> r : ground) g = Math.min(g, gi(r, "y"));
        }
        int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z0 = Integer.MAX_VALUE, z1 = Integer.MIN_VALUE;
        for (Map<String, Object> r : ground) {
            x0 = Math.min(x0, gi(r, "x0"));
            x1 = Math.max(x1, gi(r, "x1"));
            z0 = Math.min(z0, gi(r, "z0"));
            z1 = Math.max(z1, gi(r, "z1"));
        }
        List<Object> walls = new ArrayList<>();
        for (Map<String, Object> r : ground) if ("wall".equals(shape(r))) walls.add(r.get("walls"));
        if (walls.isEmpty()) for (Map<String, Object> r : ground) walls.add(r.get("walls"));
        // max(set(walls), key=walls.count): a set of strings - on a tie Python's pick depends on PYTHONHASHSEED; here
        // the first in the list wins the tie
        Object most = null;
        int mc = -1;
        for (Object w : new java.util.LinkedHashSet<>(walls)) {
            int c = java.util.Collections.frequency(walls, w);
            if (c > mc) {
                most = w;
                mc = c;
            }
        }
        boolean paved = PALACE_WALLS.contains(most);
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                if (cv.blocks.containsKey(Canvas.key(x, g, z))) continue;
                boolean lies = false;
                List<Canvas.Run> rs = cv.cols.get(Canvas.ckey(x, z));
                if (rs != null) for (Canvas.Run r : rs) if (r.y0() <= g && g <= r.y1() && !r.s().equals(AIR)) lies = true;
                if (lies) continue;
                long h = Math.floorMod(((long) x * 73856093L) ^ ((long) z * 83492791L), 100L);
                String s;
                if (paved) s = h < 8 ? "minecraft:cracked_stone_bricks" : h < 14 ? "minecraft:polished_andesite" : "minecraft:stone_bricks";
                else s = h < 12 ? "minecraft:coarse_dirt" : "minecraft:dirt_path";
                cv.set(x, g, z, s);
                cv.run(x, z, g - 3, g - 1, Arch.masonry(x, 0, z));
            }
        return new Object[]{new int[]{x0, z0, x1, z1}, g, null};
    }

    // ------------------------------------------------------------------------------------------------ build

    static final Map<String, List<Object>> COMPANIONS = new LinkedHashMap<>();

    static {
        for (Map.Entry<String, Object> e : Tables.COMPANIONS.entrySet()) COMPANIONS.put(e.getKey(), Dyn.list(e.getValue()));
    }

    private static int depth(Map<String, Object> r, Map<Integer, Map<String, Object>> byI) {
        return r.get("_on") == null ? 0 : 1 + depth(byI.get(gi(r, "_on")), byI);
    }

    /** compose.build_compose. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> buildCompose(Map<String, Object> req, Canvas cv) {
        String tier = req.containsKey("tier") ? (String) req.get("tier") : "mortal";
        int seed = req.containsKey("seed") ? Dyn.toInt(req.get("seed")) : 0;
        int look = req.containsKey("look") ? Dyn.toInt(req.get("look")) : 0;
        BASES[0] = null;
        double k = Bgen.TIER_K.get(tier);
        PyRandom rng0 = PyRandom.ofString("compose/" + seed);
        Map<String, Object> base = Dyn.map(Bgen.K.get("inner_residence"));
        List<Object> bsh = Dyn.list(base.get("sh"));
        int sh0 = rng0.randint(Dyn.toInt(bsh.get(0)), Dyn.toInt(bsh.get(1)));
        int sh = (int) Py.round(sh0 * (1 + (k - 1) * 0.35));
        List<Map<String, Object>> expanded = expandRooms(Dyn.list(req.get("rooms")));
        req.put("rooms", new ArrayList<Object>(expanded));
        List<Map<String, Object>> rooms = new ArrayList<>();
        for (int i = 0; i < expanded.size(); i++) {
            Map<String, Object> r0 = expanded.get(i);
            int x0 = Dyn.toInt(r0.get("x0")), x1 = Dyn.toInt(r0.get("x1")), z0 = Dyn.toInt(r0.get("z0")), z1 = Dyn.toInt(r0.get("z1"));
            if (x0 > x1) {
                int t = x0;
                x0 = x1;
                x1 = t;
            }
            if (z0 > z1) {
                int t = z0;
                z0 = z1;
                z1 = t;
            }
            if (x1 - x0 + 1 > MAX_SIDE || z1 - z0 + 1 > MAX_SIDE)
                throw new IllegalArgumentException("房间 " + (i + 1) + " 太大（" + (x1 - x0 + 1) + "×" + (z1 - z0 + 1) + "）：每边最多 " + MAX_SIDE + " 格");
            Map<String, Object> r = new LinkedHashMap<>(r0);
            r.put("x0", x0);
            r.put("x1", x1);
            r.put("z0", z0);
            r.put("z1", z1);
            r.put("y", Dyn.toInt(r0.get("y")));
            r.put("n", Math.max(1, Math.min(8, Dyn.toInt(r0.containsKey("storeys") ? r0.get("storeys") : 1))));
            r.put("front", r0.containsKey("front") ? r0.get("front") : "south");
            r.put("_i", i);
            rooms.add(r);
        }
        for (Map<String, Object> r : rooms) {
            r.put("_on", null);
            if (Dyn.truthy(r.get("stack")) && !special(r)) {
                double mx = (gi(r, "x0") + gi(r, "x1")) / 2.0, mz = (gi(r, "z0") + gi(r, "z1")) / 2.0;
                List<Map<String, Object>> under = new ArrayList<>();
                for (Map<String, Object> o : rooms.subList(0, gi(r, "_i")))
                    if (gi(o, "x0") <= mx && mx <= gi(o, "x1") && gi(o, "z0") <= mz && mz <= gi(o, "z1") && !special(o)) under.add(o);
                if (!under.isEmpty()) r.put("_on", under.get(under.size() - 1).get("_i"));
            }
        }
        Map<Integer, Map<String, Object>> byI = new HashMap<>();
        for (Map<String, Object> r : rooms) byI.put(gi(r, "_i"), r);
        rooms.sort(Comparator.<Map<String, Object>>comparingInt(r -> depth(r, byI)).thenComparing(r -> !special(r)).thenComparingInt(r -> gi(r, "n")));
        String[][] axes = {{"x0", "x1", "z0", "z1"}, {"z0", "z1", "x0", "x1"}};
        for (Map<String, Object> r : rooms) {
            if (GROUND_SHAPES.contains(shape(r))) continue;
            for (String[] ax : axes) {
                String lo = ax[0], hi = ax[1], olo = ax[2], ohi = ax[3];
                if (Math.floorMod(gi(r, hi) - gi(r, lo), 2) == 0) continue;
                if ("wall".equals(shape(r))) {
                    final String flo = lo, fhi = hi, folo = olo, fohi = ohi;
                    java.util.function.IntFunction<List<Map<String, Object>>> near = c -> {
                        List<Map<String, Object>> l = new ArrayList<>();
                        for (Map<String, Object> o : rooms)
                            if (o != r && !FLAT_SHAPES.contains(shape(o)) && gi(o, flo) <= c && c <= gi(o, fhi)
                                    && Math.min(gi(o, fohi), gi(r, fohi)) - Math.max(gi(o, folo), gi(r, folo)) >= -1) l.add(o);
                        return l;
                    };
                    java.util.function.IntPredicate covers = c -> {
                        for (Map<String, Object> o : near.apply(c)) if (gi(o, folo) <= gi(r, folo) && gi(r, fohi) <= gi(o, fohi)) return true;
                        return false;
                    };
                    if (covers.test(gi(r, hi))) r.put(hi, gi(r, hi) - 1);
                    else if (covers.test(gi(r, lo))) r.put(lo, gi(r, lo) + 1);
                    else {
                        Object[] best = null;                 // min over (all special, i, k, step)
                        Object[][] opts = {{hi, gi(r, hi) + 1, 1}, {lo, gi(r, lo) - 1, -1}};
                        for (int i = 0; i < 2; i++) {
                            List<Map<String, Object>> nb = near.apply((int) opts[i][1]);
                            if (nb.isEmpty()) continue;
                            boolean allSpecial = true;
                            for (Map<String, Object> o : nb) if (!special(o)) allSpecial = false;
                            if (best == null || (!allSpecial && (boolean) best[0])) best = new Object[]{allSpecial, opts[i][0], opts[i][2]};
                        }
                        if (best != null) r.put((String) best[1], gi(r, (String) best[1]) + (int) best[2]);
                        else r.put(hi, gi(r, hi) - 1);
                    }
                    continue;
                }
                List<Integer> gHi = new ArrayList<>(), gLo = new ArrayList<>();
                for (Map<String, Object> o : rooms) if (o != r && touch(r, o, lo, hi, olo, ohi)) gHi.add(gi(o, lo) - gi(r, hi));
                for (Map<String, Object> o : rooms) if (o != r && touch(o, r, lo, hi, olo, ohi)) gLo.add(gi(r, lo) - gi(o, hi));
                if (!gHi.isEmpty() && !gLo.isEmpty()) {
                    if (java.util.Collections.min(gHi) == 1) r.put(hi, gi(r, hi) + 1);
                    else if (java.util.Collections.min(gLo) == 1) r.put(lo, gi(r, lo) - 1);
                    else r.put(hi, gi(r, hi) - 1);
                } else if (!gHi.isEmpty()) r.put(lo, gi(r, lo) + 1);
                else r.put(hi, gi(r, hi) - 1);
            }
        }
        for (Map<String, Object> r : rooms) {
            if (special(r) || r.get("_on") != null) continue;
            for (String[] ax : axes) {
                String lo = ax[0], hi = ax[1], olo = ax[2], ohi = ax[3];
                while (gi(r, hi) - gi(r, lo) < 4) {
                    boolean any = false;
                    for (Map<String, Object> o : rooms) if (o != r && touch(r, o, lo, hi, olo, ohi)) any = true;
                    if (any) r.put(lo, gi(r, lo) - 1);
                    else r.put(hi, gi(r, hi) + 1);
                }
            }
        }
        int[] parent = new int[rooms.size()];
        for (int i = 0; i < parent.length; i++) parent[i] = i;
        java.util.function.IntUnaryOperator find = new java.util.function.IntUnaryOperator() {
            public int applyAsInt(int i) {
                while (parent[i] != i) {
                    parent[i] = parent[parent[i]];
                    i = parent[i];
                }
                return i;
            }
        };
        List<Object[]> pairs = new ArrayList<>();
        for (int i = 0; i < rooms.size(); i++) {
            Map<String, Object> a = rooms.get(i);
            for (int j = i + 1; j < rooms.size(); j++) {
                Map<String, Object> b = rooms.get(j);
                if (a.get("_on") != null || b.get("_on") != null) continue;
                if (special(a) || special(b)) continue;
                if (gi(a, "x0") <= gi(b, "x1") && gi(b, "x0") <= gi(a, "x1") && gi(a, "z0") <= gi(b, "z1") && gi(b, "z0") <= gi(a, "z1")
                        && Math.abs(floor(a) - floor(b)) <= LEVEL_UP) parent[find.applyAsInt(i)] = find.applyAsInt(j);
                for (String[] ax : axes) {
                    String lo = ax[0], hi = ax[1], olo = ax[2], ohi = ax[3];
                    for (Map<String, Object>[] pq : new Map[][]{{a, b}, {b, a}}) {
                        if (touch(pq[0], pq[1], lo, hi, olo, ohi)) {
                            pairs.add(new Object[]{pq[0], pq[1], lo, hi, olo, ohi});
                            if (Math.abs(floor(a) - floor(b)) <= LEVEL_UP) parent[find.applyAsInt(i)] = find.applyAsInt(j);
                        }
                    }
                }
            }
        }
        int[] fls = new int[rooms.size()];
        for (int i = 0; i < rooms.size(); i++) {
            int m = Integer.MIN_VALUE;
            for (int j = 0; j < rooms.size(); j++) if (find.applyAsInt(j) == find.applyAsInt(i)) m = Math.max(m, floor(rooms.get(j)));
            fls[i] = m;
        }
        for (int i = 0; i < rooms.size(); i++) {
            Map<String, Object> r = rooms.get(i);
            int up = fls[i] - floor(r);
            if (up > 0) {
                r.put("podium", pod(r) + up);
                if (!Dyn.truthy(r.get("podium_style"))) r.put("podium_style", "plain");
            } else r.put("y", gi(r, "y") - (floor(r) - fls[i]));
        }
        for (Map<String, Object> r : rooms) {
            r.put("cx", Math.floorDiv(gi(r, "x0") + gi(r, "x1"), 2));
            r.put("cz", Math.floorDiv(gi(r, "z0") + gi(r, "z1"), 2));
            int halfX = Math.max(2, Math.floorDiv(gi(r, "x1") - gi(r, "x0"), 2)), halfZ = Math.max(2, Math.floorDiv(gi(r, "z1") - gi(r, "z0"), 2));
            boolean ns = r.get("front").equals("south") || r.get("front").equals("north");
            r.put("hx", ns ? halfX : halfZ);
            r.put("hz", ns ? halfZ : halfX);
            r.put("sh", Dyn.truthy(r.get("sh")) ? Math.max(4, Math.min(16, Dyn.toInt(r.get("sh")))) : sh);
            r.put("side_doors", new LinkedHashMap<IntPair, Integer>());
            r.put("tall_beside", new ArrayList<Integer>());
            if ("round".equals(shape(r))) {
                int m = Math.max(2, Math.min(gi(r, "hx"), gi(r, "hz")));
                r.put("hx", m);
                r.put("hz", m);
            }
        }
        for (Map<String, Object> r : rooms) {
            int[] R = Blocks.ROT.get((String) r.get("front"));
            int fx = gi(r, "cx") + R[1] * (gi(r, "hz") + 1), fz = gi(r, "cz") + R[3] * (gi(r, "hz") + 1);
            boolean nf = false;
            for (Map<String, Object> o : rooms)
                if (o != r && gi(o, "x0") <= fx && fx <= gi(o, "x1") && gi(o, "z0") <= fz && fz <= gi(o, "z1") && java.util.Objects.equals(o.get("_on"), r.get("_on"))
                        && !FLAT_SHAPES.contains(shape(o))) nf = true;
            r.put("no_front", nf);
        }
        for (Object[] pr : pairs) {
            Map<String, Object> p = (Map<String, Object>) pr[0], q = (Map<String, Object>) pr[1];
            String lo = (String) pr[2], hi = (String) pr[3], olo = (String) pr[4], ohi = (String) pr[5];
            if (floor(p) != floor(q) || "round".equals(shape(p)) || special(p) || "round".equals(shape(q)) || special(q)) continue;
            int levels = gi(p, "sh") == gi(q, "sh") && taper(p) == 0 && taper(q) == 0 ? Math.min(gi(p, "n"), gi(q, "n")) : 1;
            int e1 = gi(p, hi), e2 = gi(q, lo);
            int a0 = Math.max(gi(p, olo), gi(q, olo)) + 1, a1 = Math.min(gi(p, ohi), gi(q, ohi)) - 1;
            double mid = (a0 + a1) / 2.0;
            List<Integer> ts = new ArrayList<>();
            for (int t = a0; t <= a1; t++) ts.add(t);
            ts.sort(Comparator.<Integer>comparingDouble(t -> Math.abs(t - mid)).thenComparingInt(t -> t));
            for (int t : ts) {
                int[][] cells = lo.equals("x0") ? new int[][]{{e1, t}, {e2, t}} : new int[][]{{t, e1}, {t, e2}};
                Map<String, Object>[] rms = new Map[]{p, q};
                int[][] loc = new int[2][];
                boolean ok = true;
                for (int s = 0; s < 2; s++) {
                    loc[s] = local(rms[s], cells[s][0], cells[s][1]);
                    if (!doorOk(rms[s], loc[s][0], loc[s][1])) ok = false;
                }
                if (ok) {
                    for (int s = 0; s < 2; s++) {
                        Map<IntPair, Integer> sd = sideDoors(rms[s]);
                        IntPair key = IntPair.of(loc[s][0], loc[s][1]);
                        sd.put(key, Math.max(levels, sd.getOrDefault(key, 0)));
                    }
                    break;
                }
            }
        }
        for (Object[] pr : pairs) {
            Map<String, Object> p = (Map<String, Object>) pr[0], q = (Map<String, Object>) pr[1];
            String lo = (String) pr[2], hi = (String) pr[3];
            Object[][] rr = {{p, q, gi(p, hi)}, {q, p, gi(q, lo)}};
            for (Object[] x : rr) {
                Map<String, Object> r = (Map<String, Object>) x[0], o = (Map<String, Object>) x[1];
                int e = (int) x[2];
                if (Dyn.truthy(r.get("horsehead")) && floor(o) + gi(o, "n") * gi(o, "sh") > floor(r) + gi(r, "n") * gi(r, "sh")) {
                    int u = lo.equals("x0") ? local(r, e, gi(r, "cz"))[0] : local(r, gi(r, "cx"), e)[0];
                    if (Math.abs(u) >= gi(r, "hx")) {
                        java.util.TreeSet<Integer> s = new java.util.TreeSet<>((List<Integer>) r.get("tall_beside"));
                        s.add(u > 0 ? 1 : -1);
                        r.put("tall_beside", new ArrayList<>(s));
                    }
                }
            }
        }
        Map<Long, List<Map<String, Object>>> solidCols = new HashMap<>();
        for (Map<String, Object> o : rooms)
            if (!FLAT_SHAPES.contains(shape(o)))
                for (int x = gi(o, "x0"); x <= gi(o, "x1"); x++)
                    for (int z = gi(o, "z0"); z <= gi(o, "z1"); z++) solidCols.computeIfAbsent(Canvas.ckey(x, z), kk -> new ArrayList<>()).add(o);
        for (Map<String, Object> r : rooms) {
            int[] R = Blocks.ROT.get((String) r.get("front"));
            Set<IntPair> cells = new HashSet<>();
            int hx = gi(r, "hx"), hz = gi(r, "hz");
            for (int u = -hx; u <= hx; u++)
                for (int v = -hz; v <= hz; v++) {
                    if (Math.abs(u) != hx && Math.abs(v) != hz) continue;
                    for (int[] d : DIRS4) {
                        int ou = u + d[0], ov = v + d[1];
                        if (Math.abs(ou) <= hx && Math.abs(ov) <= hz) continue;
                        int x = gi(r, "cx") + R[0] * ou + R[1] * ov, z = gi(r, "cz") + R[2] * ou + R[3] * ov;
                        for (Map<String, Object> o : solidCols.getOrDefault(Canvas.ckey(x, z), List.of()))
                            if (o != r) {
                                cells.add(IntPair.of(u, v));
                                break;
                            }
                    }
                }
            r.put("no_win", cells);
        }
        towerDoors(rooms);
        List<Object[]> walks = wallWalks(rooms);
        ladderFree(rooms, byI);
        List<Map<String, Object>> parts = new ArrayList<>();
        Map<Integer, Map<String, Object>> built = new HashMap<>();
        for (int i = 0; i < rooms.size(); i++) {
            Map<String, Object> r = rooms.get(i);
            CANCEL.run();
            PROGRESS.accept(i, rooms.size());
            int n = gi(r, "n");
            List<String> uses = new ArrayList<>();
            if (Dyn.truthy(r.get("uses"))) for (Object o : Dyn.list(r.get("uses"))) uses.add((String) o);
            if (uses.isEmpty()) {
                String use = r.containsKey("use") ? (String) r.get("use") : "main_room";
                uses.add(use);
                String upper = r.containsKey("use_upper") ? (String) r.get("use_upper") : (r.containsKey("use") ? (String) r.get("use") : "bedroom");
                for (int s = 0; s < n - 1; s++) uses.add(upper);
            }
            String last = uses.get(uses.size() - 1);
            for (int s = 0; s < n; s++) uses.add(last);
            uses = new ArrayList<>(uses.subList(0, n));
            List<Object> fopen = Dyn.truthy(r.get("floor_open")) ? Dyn.list(r.get("floor_open")) : List.of();
            List<Object> floors = new ArrayList<>();
            for (int s = 0; s < uses.size(); s++) {
                String u = uses.get(s);
                List<Object> program = new ArrayList<>();
                program.add(u);
                List<Object> comp = COMPANIONS.get(u);
                if (comp != null) for (int rep = 0; rep < 6; rep++) program.addAll(comp);
                Map<String, Object> fl = new LinkedHashMap<>();
                fl.put("program", program);
                fl.put("split", comp != null);
                fl.put("plan", comp != null ? "bays" : null);
                fl.put("open", s < fopen.size() && OPENS.contains(fopen.get(s)) ? fopen.get(s) : null);
                floors.add(fl);
            }
            String[] dop = doorsOpen(r);
            String doors = dop[0], opens = dop[1];
            Map<String, Object> spec = new LinkedHashMap<>(base);
            String walls = override(r.containsKey("walls") ? (String) r.get("walls") : "palace", r.get("walls_override"), Bgen.WALLS, false);
            String theme = override(r.containsKey("theme") ? (String) r.get("theme") : "grey", r.get("roof_override"), Arch.ROOFS, true);
            spec.put("walls", List.of(walls));
            spec.put("themes", List.of(theme));
            spec.put("roofs", List.of(r.containsKey("roof") ? (String) r.get("roof") : "xieshan"));
            spec.put("storeys", List.of(n, n));
            spec.put("sh", List.of(sh0, sh0));
            spec.put("ground", List.of(uses.get(0)));
            spec.put("upper", List.of(uses.get(uses.size() - 1)));
            spec.put("_floors", floors);
            spec.put("decor", List.of());
            spec.put("shop", Tables.SHOP_USES.contains(uses.get(0)));
            spec.put("balcony", n > 1 && !Dyn.truthy(r.get("no_front")) && taper(r) != 1);
            spec.put("podium", List.of(1, 1));
            spec.put("_wing", true);
            spec.put("steep", 0.5);
            spec.put("_side_doors", r.get("side_doors"));
            spec.put("_no_podium_ring", (r.containsKey("podium") ? Dyn.toInt(r.get("podium")) : 0) == 0);
            spec.put("_no_front_door", r.get("no_front"));
            spec.put("_no_window_cells", r.get("no_win"));
            spec.put("_sh", r.get("sh"));
            spec.put("_free_cells", r.get("free"));
            spec.put("_pod", pod(r));
            spec.put("_base", baseBlock(r, walls));
            spec.put("_taper", taper(r));
            spec.put("_pingzuo", Dyn.truthy(r.get("pingzuo")));
            spec.put("_veranda", Dyn.truthy(r.get("veranda")));
            spec.put("horsehead", Dyn.truthy(r.get("horsehead")) && ("yingshan".equals(r.get("roof")) || "xuanshan".equals(r.get("roof"))));
            spec.put("_tall_beside", r.get("tall_beside"));
            spec.put("_pitch", Dyn.truthy(r.get("pitch")) ? Dyn.toInt(r.get("pitch")) : 0);
            spec.put("_eave_out", Dyn.truthy(r.get("overhang")) ? Dyn.toInt(r.get("overhang")) : 0);
            spec.put("_lift", Dyn.truthy(r.get("lift")) ? Dyn.toInt(r.get("lift")) : 0);
            spec.put("_double_eave", Dyn.truthy(r.get("double_eave")) || Dyn.truthy(r.get("triple_eave")));
            spec.put("_extra_eaves", Dyn.truthy(r.get("triple_eave")) ? 2 : Dyn.truthy(r.get("double_eave")) ? 1 : 0);
            Object ps = r.get("podium_style");
            spec.put("_podium_style", "xumizuo".equals(ps) || "tiers".equals(ps) || "chengtai".equals(ps) ? ps : null);
            spec.put("_step_slope", r.get("step_slope"));
            spec.put("_podium_margin", podiumMargin(r));
            Object pshape = r.get("podium_shape");
            spec.put("_podium_shape", "square".equals(pshape) || "round".equals(pshape) ? pshape : null);
            spec.put("_colonnade", Dyn.truthy(r.get("colonnade")));
            spec.put("_open", opens.equals("open"));
            spec.put("_through", opens.equals("through"));
            spec.put("_open_front", opens.equals("front"));
            spec.put("_back_door", (doors.equals("front_back") || doors.equals("four")) && !opens.equals("through"));
            spec.put("_four", doors.equals("four"));
            spec.put("_back_veranda", Dyn.truthy(r.get("back_veranda")));
            spec.put("_beasts", Dyn.truthy(r.get("beasts")) ? Dyn.toInt(r.get("beasts")) : 0);
            spec.put("_storey_eaves", true);
            spec.put("_variant", Dyn.truthy(r.get("variant")) ? Dyn.toInt(r.get("variant")) : 0);
            spec.put("_tunnels", Dyn.truthy(r.get("tunnels")) ? Dyn.toInt(r.get("tunnels")) : 0);
            if ("chengtai".equals(ps)) {
                List<Object> nb = new ArrayList<>();
                for (Map<String, Object> o : rooms)
                    if (o != r && !FLAT_SHAPES.contains(shape(o)) && o.get("_on") == null)
                        nb.add(new Object[]{new int[]{gi(o, "x0"), gi(o, "z0"), gi(o, "x1"), gi(o, "z1")}, !special(o)});
                spec.put("_neighbours", nb);
            } else spec.put("_neighbours", null);
            spec.put("_brackets", Dyn.truthy(r.get("brackets")) ? Dyn.toInt(r.get("brackets")) : 0);
            Object ch = r.get("caihua");
            spec.put("_caihua", ch != null && Bgen.CAIHUA.containsKey(Dyn.str(ch)) ? ch : null);
            Object rr = r.get("round_roof");
            spec.put("_round_roof", rr != null && List.of("cone", "helmet", "dome", "luding", "flat").contains(rr) ? rr : null);
            Object bs = r.get("bay_style");
            spec.put("_bay_style", bs != null && !"auto".equals(bs) ? bs : null);
            if (Boolean.FALSE.equals(req.get("interior"))) spec.put("_bare", true);   // 内饰：无 - no furniture, no lamps inside
            if (r.get("_on") != null && built.containsKey(gi(r, "_on"))) r.put("y", built.get(gi(r, "_on")).get("wall_top"));
            List<Map<String, Object>> ups = new ArrayList<>();
            for (Map<String, Object> o : rooms) if (o.get("_on") != null && gi(o, "_on") == gi(r, "_i")) ups.add(o);
            if (!ups.isEmpty() && !special(r)) {
                Map<String, Object> o = ups.get(0);
                boolean ns = o.get("front").equals("south") || o.get("front").equals("north");
                int oex = ns ? gi(o, "hx") : gi(o, "hz"), oez = ns ? gi(o, "hz") : gi(o, "hx");
                int u0 = Integer.MAX_VALUE, v0 = Integer.MAX_VALUE, u1 = Integer.MIN_VALUE, v1 = Integer.MIN_VALUE;
                for (int sx : new int[]{-1, 1})
                    for (int sz : new int[]{-1, 1}) {
                        int[] c = local(r, gi(o, "cx") + sx * oex, gi(o, "cz") + sz * oez);
                        u0 = Math.min(u0, c[0]);
                        v0 = Math.min(v0, c[1]);
                        u1 = Math.max(u1, c[0]);
                        v1 = Math.max(v1, c[1]);
                    }
                spec.put("_cap", new int[]{u0, v0, u1, v1});
            }
            String rs = r.containsKey("seed") ? Dyn.str(r.get("seed")) : String.valueOf(i);
            int rv = r.containsKey("roof_variant") ? Dyn.toInt(r.get("roof_variant")) : 0;
            Arch.beginOrnaments("compose/" + seed + "/" + look + "/" + rs + "/" + rv, tier);
            spec.put("_roof_rng", PyRandom.ofString("compose/" + seed + "/" + look + "/" + rs + "/roof/" + rv));
            Canvas rcv = new Canvas();
            int vv = r.containsKey("variant") ? Dyn.toInt(r.get("variant")) : 0;
            PyRandom rrng = PyRandom.ofString("compose/" + seed + "/" + look + "/" + rs + "/" + vv);
            if (GROUND_SHAPES.contains(shape(r)) || "bridge".equals(shape(r))) {
                r.put("fp", new int[]{gi(r, "x0"), gi(r, "z0"), gi(r, "x1"), gi(r, "z1")});
                r.put("top", gi(r, "y"));
                r.put("base_blk", baseBlock(r, (String) r.get("walls")));
                continue;
            }
            if (special(r)) {
                Map<String, Object> res = SpecialShapes.specialShape(new Frame(rcv, gi(r, "cx"), gi(r, "cz"), (String) r.get("front")), gi(r, "y"), rrng, spec, tier,
                        shape(r), gi(r, "hx"), gi(r, "hz"));
                boolean flat = FLAT_SHAPES.contains(shape(r));
                int dz = "wall".equals(shape(r)) && gi(r, "hz") < THICK ? 1 : gi(r, "hz");
                boolean ns = r.get("front").equals("south") || r.get("front").equals("north");
                int ex = ns ? gi(r, "hx") : dz, ez = ns ? dz : gi(r, "hx");
                int[] fp = {gi(r, "cx") - ex, gi(r, "cz") - ez, gi(r, "cx") + ex, gi(r, "cz") + ez};
                r.put("fp", fp);
                r.put("top", res.get("top"));
                r.put("base_blk", spec.get("_base"));
                int rf = gi(res, "floor"), rsh = gi(res, "sh");
                Map<String, Object> part = new LinkedHashMap<>();
                part.put("blocks", rcv.blocks);
                part.put("cols", rcv.cols);
                part.put("fp", fp);
                part.put("y", gi(r, "y"));
                part.put("wall_top", rf + rsh);
                part.put("top", flat ? gi(r, "y") - 1 : "wall".equals(shape(r)) ? rf + rsh : gi(res, "top"));
                parts.add(part);
                Map<String, Object> b = new LinkedHashMap<>();
                b.put("wall_top", rf + rsh);
                b.put("part", parts.size() - 1);
                b.put("res", res);
                b.put("n", n);
                b.put("walls", r.get("walls"));
                built.put(gi(r, "_i"), b);
                continue;
            }
            Map<String, Object> res;
            if ("round".equals(shape(r))) {
                int R = gi(r, "hx");
                res = RoundRoom.roundRoom(new Frame(rcv, gi(r, "cx"), gi(r, "cz"), (String) r.get("front")), gi(r, "y"), rrng, spec, tier, R, n, gi(r, "sh"));
                if ("square".equals(spec.get("_podium_shape")) && "chengtai".equals(r.get("podium_style")) && pod(r) != 0)
                    Platforms.chengtaiWell(new Frame(rcv, gi(r, "cx"), gi(r, "cz"), (String) r.get("front")), R, R, gi(r, "y"), pod(r), 0,
                            (String) spec.get("_base"), Platforms.neighbours(spec.get("_neighbours")), (Integer) spec.get("_podium_margin"));
                PySet<IntPair> mask = new PySet<>();
                double rr2 = (R + 0.5) * (R + 0.5);
                for (int u = -R - 1; u < R + 2; u++)
                    for (int v = -R - 1; v < R + 2; v++) if (u * u + v * v <= rr2) mask.add(IntPair.of(gi(r, "cx") + u, gi(r, "cz") + v));
                r.put("mask", mask);
            } else res = House.house(new Frame(rcv, gi(r, "cx"), gi(r, "cz"), (String) r.get("front")), gi(r, "y"), rrng, spec, tier, gi(r, "hx"), gi(r, "hz"), n);
            Arch.roofTheme("grey");
            Arch.style("red");
            boolean ns = r.get("front").equals("south") || r.get("front").equals("north");
            int ex = ns ? gi(r, "hx") : gi(r, "hz"), ez = ns ? gi(r, "hz") : gi(r, "hx");
            int[] fp = {gi(r, "cx") - ex, gi(r, "cz") - ez, gi(r, "cx") + ex, gi(r, "cz") + ez};
            Integer top = null;
            for (long kk : rcv.blocks.keySet()) {
                int x = Canvas.kx(kk), z = Canvas.kz(kk);
                if (fp[0] <= x && x <= fp[2] && fp[1] <= z && z <= fp[3]) top = top == null ? Canvas.ky(kk) : Math.max(top, Canvas.ky(kk));
            }
            if (top == null) top = Dyn.toInt(res.get("top"));
            r.put("fp", fp);
            r.put("top", top);
            r.put("base_blk", spec.get("_base"));
            Map<String, Object> part = new LinkedHashMap<>();
            part.put("blocks", rcv.blocks);
            part.put("cols", rcv.cols);
            part.put("fp", fp);
            part.put("y", gi(r, "y"));
            int rfloor = Dyn.toInt(res.get("floor")), rsh = Dyn.toInt(res.get("sh"));
            part.put("wall_top", rfloor + (n - 1) * rsh + (res.containsKey("top_sh") ? Dyn.toInt(res.get("top_sh")) : rsh));
            part.put("top", top);
            part.put("mask", r.get("mask"));
            part.put("floor", rfloor);
            parts.add(part);
            Map<String, Object> b = new LinkedHashMap<>();
            Object capY = res.get("cap_y");
            b.put("wall_top", Dyn.truthy(capY) ? capY : (res.containsKey("wall_top") ? res.get("wall_top") : rfloor + n * rsh));
            b.put("part", parts.size() - 1);
            b.put("res", res);
            b.put("n", n);
            b.put("walls", r.get("walls"));
            built.put(gi(r, "_i"), b);
        }
        for (Map<String, Object> r : rooms) {
            if (r.get("_on") != null && built.containsKey(gi(r, "_on")) && built.containsKey(gi(r, "_i"))) {
                Map<String, Object> low = parts.get(gi(built.get(gi(r, "_on")), "part"));
                low.put("top", Math.min(gi(low, "top"), gi(built.get(gi(r, "_on")), "wall_top")));
            }
        }
        Map<Long, Integer> who = new HashMap<>();
        CANCEL.run();                                  // (also between the steps after the rooms: a big compound spends
        Joinparts.join(cv, parts, who);                //  most of its time there; no-op but for a game preview)
        for (Object[] wk : walks) {                       // 城墙 onto 城台: the 城台's parapet across the walk goes
            @SuppressWarnings("unchecked") List<int[]> cells = (List<int[]>) wk[0];
            int y = (int) wk[1];
            for (int[] c : cells)
                for (int yy = y + 1; yy < y + 4; yy++) if (cv.blocks.containsKey(Canvas.key(c[0], yy, c[1]))) cv.blocks.put(Canvas.key(c[0], yy, c[1]), AIR);
            if (wk[2] != null) {
                Object[] door = (Object[]) wk[2];
                int x = (int) door[0], z = (int) door[1], dx = (int) door[2], dz = (int) door[3];
                int[] bx = (int[]) door[4];
                List<int[]> ring = new ArrayList<>();
                for (int u = bx[0]; u <= bx[2]; u++) ring.add(new int[]{u, bx[1]});
                for (int u = bx[0]; u <= bx[2]; u++) ring.add(new int[]{u, bx[3]});
                for (int w = bx[1]; w <= bx[3]; w++) ring.add(new int[]{bx[0], w});
                for (int w = bx[1]; w <= bx[3]; w++) ring.add(new int[]{bx[2], w});
                String kind = "minecraft:spruce_door";
                for (int[] c : ring) {
                    String b = cv.blocks.get(Canvas.key(c[0], y + 1, c[1]));
                    String nm = Blocks.name(b == null ? "" : b);
                    if (nm.endsWith("_door")) {
                        kind = nm;
                        break;
                    }
                }
                String facing = Finish.dirOf(-dx, -dz);
                cv.blocks.put(Canvas.key(x, y + 1, z), kind + "[facing=" + facing + ",half=lower,hinge=left,open=false,powered=false]");
                cv.blocks.put(Canvas.key(x, y + 2, z), kind + "[facing=" + facing + ",half=upper,hinge=left,open=false,powered=false]");
                String side = cv.blocks.get(Canvas.key(x + dz, y + 3, z + dx));
                if (side != null && !side.isEmpty() && !side.endsWith(":air") && !side.contains("_door")) cv.blocks.put(Canvas.key(x, y + 3, z), side);
            }
        }
        for (Map<String, Object> r : rooms)
            if (r.get("_on") != null && built.containsKey(gi(r, "_on")) && built.containsKey(gi(r, "_i")))
                stackLadder(cv, r, built.get(gi(r, "_on")), built.get(gi(r, "_i")));
        Object[] terrain = null;
        Set<Long> nofoot = null;
        boolean anyGround = false;
        for (Map<String, Object> r : rooms) if (GROUND_SHAPES.contains(shape(r))) anyGround = true;
        Object[] court;
        if (anyGround) {
            terrain = Terrain.terrainGround(cv, rooms);
            nofoot = Terrain.buildBridges(cv, rooms, (int) terrain[1], (Map<Long, Integer>) terrain[0]);
            court = null;
        } else court = compoundGround(cv, rooms);
        int minY = Integer.MAX_VALUE;
        for (Map<String, Object> r : rooms) minY = Math.min(minY, gi(r, "y"));
        final int groundY = terrain != null ? (int) terrain[1] : minY;
        Runnable trimLow = () -> {
            for (Finish.Low w : Finish.walkout(cv.blocks, cv.cols, groundY, 4)) {
                Integer mine = who.get(w.foot());
                for (long c : w.low()) {
                    Integer wc = who.get(c);
                    String b = cv.blocks.get(c);
                    if (wc != null && (!wc.equals(mine) || (b != null && b.contains("_trapdoor")))) cv.blocks.put(c, AIR);
                }
            }
        };
        CANCEL.run();
        trimLow.run();
        Finish.connectPanes(cv.blocks);
        CANCEL.run();
        if (Finish.clearDoorways(cv) != 0) trimLow.run();
        Finish.settleLoose(cv);
        CANCEL.run();
        if (terrain != null) {
            List<Long> loose = Terrain.floating(cv.blocks, (int) terrain[1], cv.cols);
            if (loose.size() <= 200) for (long c : loose) cv.blocks.put(c, AIR);
        }
        Finish.connectPanes(cv.blocks);
        List<Object> storeys = new ArrayList<>();
        for (Map<String, Object> r : rooms) {
            Map<String, Object> b = built.get(gi(r, "_i"));
            if (b == null || special(r)) continue;
            Map<String, Object> res = (Map<String, Object>) b.get("res");
            int[] fp = (int[]) r.get("fp");
            int tp = taper(r);
            int cx = Math.floorDiv(fp[0] + fp[2], 2), cz = Math.floorDiv(fp[1] + fp[3], 2), ex = Math.floorDiv(fp[2] - fp[0], 2), ez = Math.floorDiv(fp[3] - fp[1], 2);
            List<Object> st = new ArrayList<>();
            for (int s = 0; s < gi(b, "n"); s++)
                st.add(List.of(cx - Math.max(2, ex - s * tp) + 1, cz - Math.max(2, ez - s * tp) + 1, cx + Math.max(2, ex - s * tp) - 1,
                        cz + Math.max(2, ez - s * tp) - 1, Dyn.toInt(res.get("floor")) + s * Dyn.toInt(res.get("sh"))));
            storeys.add(st);
        }
        List<Object[]> bases = new ArrayList<>();
        for (Map<String, Object> r : rooms) if (r.get("_on") == null) bases.add(new Object[]{r.get("fp"), gi(r, "y"), r.get("base_blk")});
        if (bases.isEmpty()) for (Map<String, Object> r : rooms) bases.add(new Object[]{r.get("fp"), gi(r, "y"), r.get("base_blk")});
        if (court != null) bases.add(court);
        int sides = 0;
        for (Map<String, Object> r : rooms) sides += sideDoors(r).size();
        int top = Integer.MIN_VALUE;
        for (Map<String, Object> r : rooms) top = Math.max(top, gi(r, "top"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rooms", rooms.size());
        out.put("bases", bases);
        out.put("top", top);
        out.put("doors", sides);
        out.put("sh", sh);
        out.put("storeys", storeys);
        if (terrain != null) {
            out.put("ground", terrain[0]);
            out.put("floor", terrain[1]);
            out.put("nofoot", nofoot);
        }
        return out;
    }

    static {
        // (PySet: used by the ported parts of round rooms and towns that follow)
        PySet.class.getName();
    }
}
