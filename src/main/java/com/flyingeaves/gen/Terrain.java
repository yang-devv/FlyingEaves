package com.flyingeaves.gen;

import com.flyingeaves.gen.py.IntPair;
import com.flyingeaves.gen.py.Py;
import com.flyingeaves.gen.py.PyRandom;

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
 * compose's town ground: terrain_ground (台地, 街道, 石阶 at many levels, retaining walls, steps and flights between
 * them), build_bridges (石桥), plant_groves (林), expand_rooms (地块); selfcheck's floating check (what compose clears).
 */
public final class Terrain {
    private Terrain() {
    }

    // street styles: field (block, weight) and the strip down the middle
    static final Object[][][] STREET_FIELDS = {
            {{"stone_bricks", 50}, {"polished_andesite", 20}, {"cracked_stone_bricks", 12}, {"andesite", 10}, {"mossy_stone_bricks", 8}},
            {{"cobblestone", 45}, {"mossy_cobblestone", 15}, {"gravel", 12}, {"andesite", 18}, {"stone", 10}},
            {{"bricks", 55}, {"mud_bricks", 25}, {"packed_mud", 10}, {"terracotta", 10}},
            {{"dirt_path", 55}, {"coarse_dirt", 25}, {"gravel", 12}, {"rooted_dirt", 8}}};
    static final String[] STREET_STRIPS = {"smooth_stone", "stone_bricks", "stone_bricks", "cobblestone"};
    static final Object[][][] TERRACE_STYLES = {
            {{"dirt_path", 45}, {"coarse_dirt", 30}, {"packed_mud", 15}, {"gravel", 10}},
            {{"grass_block", 78}, {"coarse_dirt", 10}, {"dirt_path", 8}, {"moss_block", 4}},
            {{"cobblestone", 40}, {"andesite", 25}, {"gravel", 20}, {"mossy_cobblestone", 15}},
            {{"bricks", 70}, {"mud_bricks", 15}, {"packed_mud", 15}},
            {{"grass_block", 85}, {"coarse_dirt", 8}, {"moss_block", 7}},
            {{"grass_block", 80}, {"podzol", 8}, {"coarse_dirt", 6}, {"moss_block", 6}}};
    static final int BANK = 4, GROVE = 5, BRIDGE_PIER = 9;
    static final Map<String, Integer> GROUND_PRIO = new HashMap<>(Map.of("terrace", 1, "paving", 2, "river", 2, "street", 3, "stairs", 4));
    static final int[][] NAME4 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    static String name4(int dx, int dz) {
        return Finish.dirOf(dx, dz);
    }

    static int variant(Map<String, Object> r) {
        return Dyn.truthy(r.get("variant")) ? Dyn.toInt(r.get("variant")) : 0;
    }

    static boolean bank(Map<String, Object> r) {
        return "terrace".equals(Compose.shape(r)) && (Math.floorMod(variant(r), TERRACE_STYLES.length) == BANK || Math.floorMod(variant(r), TERRACE_STYLES.length) == GROVE);
    }

    static long hash(int x, int z, int salt) {
        return (((long) x * 73856093L) ^ ((long) z * 83492791L) ^ ((long) salt * 19349663L)) & 0x7fffffffL;
    }

    static String pick(Object[][] table, int x, int z, int salt) {
        long tot = 0;
        for (Object[] e : table) tot += (int) e[1];
        long h = Math.floorMod(hash(x, z, salt), tot);
        for (Object[] e : table) {
            if (h < (int) e[1]) return "minecraft:" + e[0];
            h -= (int) e[1];
        }
        return "minecraft:" + table[0][0];
    }

    /** compose._RISE: rows up the flight from its low (front) edge, and which way its steps face. */
    static int rise(String front, int x, int z, int[] fp) {
        return switch (front) {
            case "south" -> fp[3] - z;
            case "north" -> z - fp[1];
            case "east" -> fp[2] - x;
            default -> x - fp[0];
        };
    }

    static String riseFace(String front) {
        return switch (front) {
            case "south" -> "north";
            case "north" -> "south";
            case "east" -> "west";
            default -> "east";
        };
    }

    static String front(Map<String, Object> r) {
        return r.containsKey("front") ? (String) r.get("front") : "south";
    }

    static String baseOf(Map<String, Object> r, int x, int y, int z) {
        Object b = r.get("base_blk");
        return Dyn.truthy(b) ? (String) b : Arch.masonry(x, y, z);
    }

    static String stairBlock(Map<String, Object> r, String facing, String kind) {
        String st;
        String s = Compose.shape(r);
        if ("street".equals(s) || "stairs".equals(s)) {
            Object[][] field = STREET_FIELDS[Math.floorMod(variant(r), STREET_FIELDS.length)];
            st = House.stairsOf("minecraft:" + field[0][0], "minecraft:stone_brick_stairs");
        } else st = House.stairsOf((String) r.get("base_blk"), "minecraft:stone_brick_stairs");
        return Arch.stepBlock(kind, st, Arch.slabFor(st), facing);
    }

    /** compose.terrain_ground: returns (ground {(x, z): y} as Canvas.ckey -> y, floor). */
    @SuppressWarnings("unchecked")
    static Object[] terrainGround(Canvas cv, List<Map<String, Object>> rooms) {
        LinkedHashMap<Long, Integer> level = new LinkedHashMap<>();
        Map<Long, Map<String, Object>> owner = new HashMap<>();
        List<Map<String, Object>> grounds = new ArrayList<>();
        for (Map<String, Object> r : rooms)
            if (r.get("_on") == null && GROUND_PRIO.containsKey(Compose.shape(r)) && r.containsKey("fp")) grounds.add(r);
        grounds.sort(Comparator.<Map<String, Object>>comparingInt(r -> GROUND_PRIO.get(Compose.shape(r))).thenComparingLong(r -> {
            int[] fp = (int[]) r.get("fp");
            return -((long) (fp[2] - fp[0] + 1) * (fp[3] - fp[1] + 1));
        }));
        for (Map<String, Object> r : grounds) {
            int[] fp = (int[]) r.get("fp");
            boolean st = "stairs".equals(Compose.shape(r));
            for (int x = fp[0]; x <= fp[2]; x++)
                for (int z = fp[1]; z <= fp[3]; z++) {
                    long k = Canvas.ckey(x, z);
                    level.put(k, Compose.gi(r, "y") + (st ? rise(front(r), x, z, fp) : 0));
                    owner.put(k, r);
                }
        }
        for (Map<String, Object> r : rooms) {
            if (r.get("_on") != null || Compose.FLAT_SHAPES.contains(Compose.shape(r)) || !r.containsKey("fp")) continue;
            int[] fp = (int[]) r.get("fp");
            for (int x = fp[0]; x <= fp[2]; x++)
                for (int z = fp[1]; z <= fp[3]; z++) {
                    long k = Canvas.ckey(x, z);
                    if (level.containsKey(k)) level.put(k, Compose.gi(r, "y"));
                }
        }
        if (level.isEmpty()) {
            int m = Integer.MAX_VALUE;
            for (Map<String, Object> r : rooms) m = Math.min(m, Compose.gi(r, "y"));
            return new Object[]{new LinkedHashMap<Long, Integer>(), m - 3};
        }
        int lmin = Integer.MAX_VALUE, lmax = Integer.MIN_VALUE;
        for (int v : level.values()) {
            lmin = Math.min(lmin, v);
            lmax = Math.max(lmax, v);
        }
        final int floor = lmin - 3;
        class S {
            boolean solidAt(int x, int y, int z) {
                String b = cv.blocks.get(Canvas.key(x, y, z));
                if (b != null) return !b.equals(AIR);
                List<Canvas.Run> rs = cv.cols.get(Canvas.ckey(x, z));
                if (rs != null) for (Canvas.Run r : rs) if (r.y0() <= y && y <= r.y1() && !r.s().equals(AIR)) return true;
                return false;
            }

            boolean free(int x, int z, int y0, int y1) {
                for (int y = y0; y <= y1; y++) if (solidAt(x, y, z)) return false;
                return true;
            }
        }
        S S = new S();
        LinkedHashMap<Long, Object[]> stairAt = new LinkedHashMap<>();     // (y, block, fill)
        LinkedHashMap<Long, Integer> cut = new LinkedHashMap<>();
        Set<Long> used = new HashSet<>();
        for (Map.Entry<Long, Map<String, Object>> e : orderedOwner(level, owner)) {
            Map<String, Object> r = e.getValue();
            if (!"stairs".equals(Compose.shape(r))) continue;
            long k = e.getKey();
            int x = Canvas.cx(k), z = Canvas.cz(k);
            int[] fp = (int[]) r.get("fp");
            String fr = front(r);
            int top = fr.equals("south") || fr.equals("north") ? fp[3] - fp[1] : fp[2] - fp[0];
            int rs = rise(fr, x, z, fp);
            int lv = level.get(k);
            if (rs < top && lv == Compose.gi(r, "y") + rs && S.free(x, z, lv + 1, lv + 2)) {
                stairAt.put(k, new Object[]{lv + 1, stairBlock(r, riseFace(fr), "stair"), null});
                used.add(k);
            }
        }
        LinkedHashMap<List<Integer>, List<int[]>> edges = new LinkedHashMap<>();
        for (Map.Entry<Long, Integer> e : level.entrySet()) {
            long k = e.getKey();
            int x = Canvas.cx(k), z = Canvas.cz(k), ga = e.getValue();
            for (int[] d : NAME4) {
                long b = Canvas.ckey(x + d[0], z + d[1]);
                Integer gb = level.get(b);
                if (gb == null || ga - gb < 2 || "river".equals(Compose.shape(owner.get(b))) || "river".equals(Compose.shape(owner.get(k)))
                        || bank(owner.get(b)) || bank(owner.get(k))) continue;
                edges.computeIfAbsent(List.of(Compose.gi(owner.get(k), "_i"), Compose.gi(owner.get(b), "_i"), d[0], d[1]), q -> new ArrayList<>()).add(new int[]{x, z});
            }
        }
        for (Map.Entry<List<Integer>, List<int[]>> e : edges.entrySet()) {
            int dx = e.getKey().get(2), dz = e.getKey().get(3);
            int[] lat = dx == 0 ? new int[]{1, 0} : new int[]{0, 1};
            List<int[]> cells = new ArrayList<>(e.getValue());
            cells.sort(Comparator.<int[]>comparingInt(c -> c[0] * lat[1] + c[1] * lat[0]).thenComparingInt(c -> c[0] * lat[0] + c[1] * lat[1]));
            List<List<int[]>> stretches = new ArrayList<>();
            List<int[]> cur = new ArrayList<>();
            cur.add(cells.get(0));
            for (int[] c : cells.subList(1, cells.size())) {
                int[] last = cur.get(cur.size() - 1);
                if (c[0] - last[0] == lat[0] && c[1] - last[1] == lat[1]) cur.add(c);
                else {
                    stretches.add(cur);
                    cur = new ArrayList<>();
                    cur.add(c);
                }
            }
            stretches.add(cur);
            for (List<int[]> st : stretches) {
                if (st.size() < 3) continue;
                double mid = (st.size() - 1) / 2.0;
                List<Integer> order = new ArrayList<>();
                for (int i = 1; i < st.size() - 1; i++) order.add(i);
                order.sort(Comparator.<Integer>comparingDouble(i -> Math.abs(i - mid)).thenComparingInt(i -> i));
                for (int i : order) {
                    int[] a0 = st.get(i);
                    long ka = Canvas.ckey(a0[0], a0[1]), kb = Canvas.ckey(a0[0] + dx, a0[1] + dz);
                    int ga = level.get(ka), gb = level.get(kb);
                    int d = ga - gb;
                    Map<String, Object> ra = owner.get(ka), rb = owner.get(kb);
                    int[][] trio = new int[3][];
                    for (int t = -1; t <= 1; t++) trio[t + 1] = new int[]{a0[0] + t * lat[0], a0[1] + t * lat[1]};
                    List<Arch.Step> flt = Arch.flight(d, Arch.stepRun(ra.get("step_slope")));
                    int L = flt.size();
                    String face = name4(-dx, -dz);
                    boolean okLow = true;
                    outer:
                    for (int[] a : trio) {
                        Integer la = level.get(Canvas.ckey(a[0], a[1]));
                        if (la == null || la != ga || !S.free(a[0], a[1], ga + 1, ga + 2)) {
                            okLow = false;
                            break;
                        }
                        for (int kk = 0; kk < L + 1; kk++) {
                            int cx = a[0] + (kk + 1) * dx, cz = a[1] + (kk + 1) * dz;
                            long c = Canvas.ckey(cx, cz);
                            Integer lc = level.get(c);
                            if (used.contains(c) || lc == null || lc != gb || owner.get(c) != rb) {
                                okLow = false;
                                break outer;
                            }
                            if (!S.free(cx, cz, gb + 1, (kk < L ? ga - flt.get(kk).dt() : gb) + 2)) {
                                okLow = false;
                                break outer;
                            }
                        }
                    }
                    if (okLow) {
                        for (int[] a : trio) {
                            for (Arch.Step s : flt) {
                                int cx = a[0] + (s.k() + 1) * dx, cz = a[1] + (s.k() + 1) * dz;
                                stairAt.put(Canvas.ckey(cx, cz), new Object[]{ga - s.dt(), stairBlock(rb, face, s.kind()), baseOf(rb, cx, 0, cz)});
                                used.add(Canvas.ckey(cx, cz));
                            }
                            used.add(Canvas.ckey(a[0] + (L + 1) * dx, a[1] + (L + 1) * dz));
                            used.add(Canvas.ckey(a[0], a[1]));
                        }
                        break;
                    }
                    boolean okCut = true;
                    outer2:
                    for (int[] a : trio) {
                        int cx = a[0] + dx, cz = a[1] + dz;
                        long c = Canvas.ckey(cx, cz);
                        Integer lc = level.get(c);
                        if (used.contains(c) || lc == null || lc != gb || !S.free(cx, cz, gb + 1, gb + 2)) {
                            okCut = false;
                            break;
                        }
                        for (int kk = 0; kk < L + 1; kk++) {
                            int ux = a[0] - kk * dx, uz = a[1] - kk * dz;
                            long u = Canvas.ckey(ux, uz);
                            Integer lu = level.get(u);
                            if (used.contains(u) || lu == null || lu != ga || owner.get(u) != ra || !S.free(ux, uz, ga + 1, ga + 2)) {
                                okCut = false;
                                break outer2;
                            }
                        }
                    }
                    if (okCut) {
                        for (int[] a : trio) {
                            for (Arch.Step s : flt) {
                                int cx = a[0] - (L - 1 - s.k()) * dx, cz = a[1] - (L - 1 - s.k()) * dz;
                                long c = Canvas.ckey(cx, cz);
                                cut.put(c, ga - s.dt());
                                stairAt.put(c, new Object[]{ga - s.dt(), stairBlock(ra, face, s.kind()), null});
                                used.add(c);
                            }
                            used.add(Canvas.ckey(a[0] + dx, a[1] + dz));
                            used.add(Canvas.ckey(a[0] - L * dx, a[1] - L * dz));
                        }
                        break;
                    }
                }
            }
        }
        for (Map.Entry<Long, Integer> e : level.entrySet()) {
            long k = e.getKey();
            if (cut.containsKey(k)) continue;
            int x = Canvas.cx(k), z = Canvas.cz(k), ga = e.getValue();
            for (int[] d : NAME4) {
                long b = Canvas.ckey(x + d[0], z + d[1]);
                Integer lb = level.get(b);
                if (lb == null || lb != ga - 1 || used.contains(b) || stairAt.containsKey(b) || "river".equals(Compose.shape(owner.get(b))) || bank(owner.get(b))
                        || bank(owner.get(k))) continue;
                int gb = ga - 1;
                if (!S.free(x + d[0], z + d[1], gb + 1, gb + 2) || !S.free(x, z, ga + 1, ga + 1)) continue;
                stairAt.put(b, new Object[]{gb + 1, stairBlock(owner.get(b), name4(-d[0], -d[1]), "stair"), null});
            }
        }
        boolean deep = lmax - lmin > 24;
        Set<Long> down = new HashSet<>();
        if (deep) {
            Set<Long> seen = new HashSet<>();
            for (long c0 : level.keySet()) {
                if (seen.contains(c0)) continue;
                List<Long> comp = new ArrayList<>();
                comp.add(c0);
                List<Long> todo = new ArrayList<>();
                todo.add(c0);
                seen.add(c0);
                while (!todo.isEmpty()) {
                    long c = todo.remove(todo.size() - 1);
                    int x = Canvas.cx(c), z = Canvas.cz(c);
                    for (long n : new long[]{Canvas.ckey(x + 1, z), Canvas.ckey(x - 1, z), Canvas.ckey(x, z + 1), Canvas.ckey(x, z - 1)})
                        if (level.containsKey(n) && !seen.contains(n)) {
                            seen.add(n);
                            comp.add(n);
                            todo.add(n);
                        }
                }
                long best = comp.get(0);
                for (long c : comp) {
                    int lc = level.get(c), lb = level.get(best);
                    if (lc < lb || (lc == lb && (Canvas.cx(c) < Canvas.cx(best) || (Canvas.cx(c) == Canvas.cx(best) && Canvas.cz(c) < Canvas.cz(best))))) best = c;
                }
                down.add(best);
            }
        }
        for (Map.Entry<Long, Integer> e : level.entrySet()) {
            long k = e.getKey();
            int x = Canvas.cx(k), z = Canvas.cz(k), g = e.getValue();
            Map<String, Object> r = owner.get(k);
            int fl;
            if (!deep || down.contains(k)) fl = floor;
            else {
                int m = g;
                for (long n : new long[]{Canvas.ckey(x + 1, z), Canvas.ckey(x - 1, z), Canvas.ckey(x, z + 1), Canvas.ckey(x, z - 1)})
                    if (level.containsKey(n)) m = Math.min(m, level.get(n));
                fl = m - 3;
            }
            if ("river".equals(Compose.shape(r))) {
                cv.run(x, z, Math.min(fl, g - 7), g - 4, baseOf(r, x, 0, z));
                continue;
            }
            if (cut.containsKey(k)) {
                int cy = cut.get(k);
                for (int y = cy + 1; y < g + 1; y++) cv.set(x, y, z, AIR);
                cv.run(x, z, Math.min(fl, cy - 4), cy - 1, baseOf(r, x, 0, z));
                continue;
            }
            if (S.solidAt(x, g, z)) {
                cv.run(x, z, fl, g - 1, baseOf(r, x, 0, z));
                continue;
            }
            String shape = Compose.shape(r);
            int var = variant(r);
            String sb;
            if ("stairs".equals(shape)) sb = pick(STREET_FIELDS[Math.floorMod(var, STREET_FIELDS.length)], x, z, 1);
            else if ("street".equals(shape)) {
                int si = Math.floorMod(var, STREET_FIELDS.length);
                int[] fp = (int[]) r.get("fp");
                int narrow = Math.min(fp[2] - fp[0], fp[3] - fp[1]) + 1;
                int off = (fp[2] - fp[0]) >= (fp[3] - fp[1]) ? Math.abs(z - Math.floorDiv(fp[1] + fp[3], 2)) : Math.abs(x - Math.floorDiv(fp[0] + fp[2], 2));
                String strip = STREET_STRIPS[si];
                sb = strip != null && narrow >= 5 && off <= (narrow >= 7 ? 1 : 0) ? "minecraft:" + strip : pick(STREET_FIELDS[si], x, z, 1);
            } else sb = pick(TERRACE_STYLES[Math.floorMod(var, TERRACE_STYLES.length)], x, z, 2);
            cv.set(x, g, z, sb);
            int top = g - 1;
            if (sb.endsWith("grass_block") || sb.endsWith("moss_block")) {
                cv.set(x, g - 1, z, "minecraft:dirt");
                top = g - 2;
            }
            int lo = Integer.MAX_VALUE;
            for (int[] d : NAME4) {
                Integer l = level.get(Canvas.ckey(x + d[0], z + d[1]));
                lo = Math.min(lo, l != null ? l : g);
            }
            if (lo < top) {
                cv.run(x, z, fl, lo, baseOf(r, x, 0, z));
                for (int y = lo + 1; y < top + 1; y++) cv.set(x, y, z, baseOf(r, x, y, z));
            } else cv.run(x, z, fl, top, baseOf(r, x, 0, z));
        }
        for (Map.Entry<Long, Object[]> e : stairAt.entrySet()) {
            long k = e.getKey();
            int x = Canvas.cx(k), z = Canvas.cz(k);
            int y = (int) e.getValue()[0];
            String blk = (String) e.getValue()[1], fill = (String) e.getValue()[2];
            if (!cut.containsKey(k)) for (int yy = level.get(k) + 1; yy < y; yy++) cv.set(x, yy, z, fill != null ? fill : baseOf(owner.get(k), x, yy, z));
            cv.set(x, y, z, blk);
        }
        plantGroves(cv, rooms, level, owner);
        return new Object[]{level, floor};
    }

    /** owner.items() in Python: owner was filled in the same order as level (one write per cell each time). */
    private static List<Map.Entry<Long, Map<String, Object>>> orderedOwner(LinkedHashMap<Long, Integer> level, Map<Long, Map<String, Object>> owner) {
        List<Map.Entry<Long, Map<String, Object>>> out = new ArrayList<>();
        for (long k : level.keySet()) out.add(Map.entry(k, owner.get(k)));
        return out;
    }

    /** compose.plant_groves. */
    static void plantGroves(Canvas cv, List<Map<String, Object>> rooms, Map<Long, Integer> level, Map<Long, Map<String, Object>> owner) {
        for (Map<String, Object> r : rooms) {
            if (!"terrace".equals(Compose.shape(r)) || Math.floorMod(variant(r), TERRACE_STYLES.length) != GROVE || !r.containsKey("fp")) continue;
            int[] fp = (int[]) r.get("fp");
            int g = Compose.gi(r, "y");
            for (int x = fp[0] + 2; x < fp[2] - 1; x += 5)
                for (int z = fp[1] + 2; z < fp[3] - 1; z += 5) {
                    long h = hash(x, z, 9);
                    if (h % 100 < 22) continue;
                    int cx = Math.max(fp[0] + 2, Math.min(fp[2] - 2, x + (int) (h % 3) - 1));
                    int cz = Math.max(fp[1] + 2, Math.min(fp[3] - 2, z + (int) ((h / 3) % 3) - 1));
                    boolean bad = false;
                    for (int du = -2; du < 3 && !bad; du++)
                        for (int dv = -2; dv < 3; dv++) {
                            long c = Canvas.ckey(cx + du, cz + dv);
                            Integer l = level.get(c);
                            if (l == null || l != g || owner.get(c) != r) {
                                bad = true;
                                break;
                            }
                        }
                    if (bad) continue;
                    for (int yy = g + 1; yy < g + 4; yy++) {
                        String b = cv.blocks.get(Canvas.key(cx, yy, cz));
                        if (b != null && !b.equals(AIR)) bad = true;
                    }
                    if (bad) continue;
                    SpecialShapes.townOrnament(new Frame(cv, cx, cz, "south"), g, PyRandom.ofInt(h), "tree", 2, 2, null);
                }
        }
    }

    /** compose.build_bridges: returns the columns with no footing (Canvas.ckey). */
    static Set<Long> buildBridges(Canvas cv, List<Map<String, Object>> rooms, int floor, Map<Long, Integer> ground) {
        Set<Long> nofoot = new HashSet<>();
        List<Map<String, Object>> halls = new ArrayList<>();
        for (Map<String, Object> o : rooms) if (!Dyn.truthy(o.get("shape")) || "round".equals(o.get("shape"))) halls.add(o);
        for (Map<String, Object> r : rooms) {
            if (!"bridge".equals(Compose.shape(r))) continue;
            int x0 = Compose.gi(r, "x0"), z0 = Compose.gi(r, "z0"), x1 = Compose.gi(r, "x1"), z1 = Compose.gi(r, "z1");
            int y = Compose.gi(r, "y");
            boolean alongX = front(r).equals("east") || front(r).equals("west");
            int L = alongX ? x1 - x0 : z1 - z0;
            int Wd = alongX ? z1 - z0 : x1 - x0;
            String base = Dyn.truthy(r.get("base_blk")) ? (String) r.get("base_blk") : "minecraft:stone_bricks";
            Object[][] field = STREET_FIELDS[Math.floorMod(variant(r), STREET_FIELDS.length)];
            String rail = "minecraft:stone_brick_wall[up=true," + (alongX ? "east=low,west=low,north=none,south=none" : "north=low,south=low,east=none,west=none") + "]";
            int np = (int) Math.max(1, Py.round(L / (double) BRIDGE_PIER));
            Set<Integer> piers = new HashSet<>(List.of(0, L));
            for (int k = 1; k < np; k++) piers.add((int) Py.round(L * k / (double) np));
            for (int x = x0; x <= x1; x++)
                for (int z = z0; z <= z1; z++) {
                    int t = alongX ? x - x0 : z - z0;
                    int s = alongX ? z - z0 : x - x0;
                    cv.set(x, y, z, pick(field, x, z, 5));
                    ground.put(Canvas.ckey(x, z), y);
                    if (piers.contains(t)) cv.run(x, z, floor, y - 1, base);
                    else {
                        nofoot.add(Canvas.ckey(x, z));
                        cv.set(x, y - 1, z, "minecraft:stone_brick_slab[type=top,waterlogged=false]");
                        for (int d : new int[]{-1, 1}) {
                            if (piers.contains(t + d) && 0 < t + d && t + d < L) {
                                String face = alongX ? (d > 0 ? "west" : "east") : (d > 0 ? "north" : "south");
                                face = Blocks.OPP.get(face);
                                cv.set(x, y - 2, z, "minecraft:stone_brick_stairs[facing=" + face + ",half=top,shape=straight,waterlogged=false]");
                            }
                        }
                    }
                    boolean covered = false;
                    for (Map<String, Object> o : halls)
                        if (Compose.gi(o, "x0") <= x && x <= Compose.gi(o, "x1") && Compose.gi(o, "z0") <= z && z <= Compose.gi(o, "z1")) covered = true;
                    if ((s == 0 || s == Wd) && Wd >= 4 && !covered) {
                        if (piers.contains(t) && 0 < t && t < L) {
                            cv.set(x, y + 1, z, base);
                            cv.set(x, y + 2, z, "minecraft:lantern[hanging=false,waterlogged=false]");
                        } else cv.set(x, y + 1, z, rail);
                    }
                }
        }
        return nofoot;
    }

    /** compose.expand_rooms for one 地块: its pieces where it is now. */
    static List<Map<String, Object>> expandPatch(Map<String, Object> r) {
        Object parts = r.get("parts");
        List<Object> ps = parts instanceof String s ? Dyn.list(Json.parse(s)) : Dyn.list(parts);
        int x0 = Math.min(Dyn.toInt(r.get("x0")), Dyn.toInt(r.get("x1"))), x1 = Math.max(Dyn.toInt(r.get("x0")), Dyn.toInt(r.get("x1")));
        int z0 = Math.min(Dyn.toInt(r.get("z0")), Dyn.toInt(r.get("z1"))), z1 = Math.max(Dyn.toInt(r.get("z0")), Dyn.toInt(r.get("z1")));
        int k = Compose.FRONT_I.getOrDefault(front(r), 0);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : ps) {
            Map<String, Object> q = Dyn.map(o);
            int[] p0 = at(k, x0, z0, x1, z1, Dyn.toInt(q.get("a0")), Dyn.toInt(q.get("b0")));
            int[] p1 = at(k, x0, z0, x1, z1, Dyn.toInt(q.get("a1")), Dyn.toInt(q.get("b1")));
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("x0", Math.min(p0[0], p1[0]));
            d.put("z0", Math.min(p0[1], p1[1]));
            d.put("x1", Math.max(p0[0], p1[0]));
            d.put("z1", Math.max(p0[1], p1[1]));
            d.put("y", Dyn.toInt(r.get("y")) + (q.containsKey("dy") ? Dyn.toInt(q.get("dy")) : 0));
            d.put("special", q.get("s"));
            d.put("shape", q.get("s"));
            d.put("front", Compose.I_FRONT.get(Math.floorMod((q.containsKey("f") ? Dyn.toInt(q.get("f")) : 0) + k, 4)));
            d.put("storeys", 1);
            d.put("roof", "xieshan");
            d.put("uses", new ArrayList<>(java.util.Collections.nCopies(8, "main_room")));
            d.put("podium", 0);
            d.put("podium_style", "plain");
            d.put("open", "closed");
            d.put("round", false);
            d.put("stack", false);
            d.put("variant", 0);
            d.put("roof_variant", 0);
            d.put("sh", 0);
            d.put("walls", "greybrick");
            d.put("theme", "grey");
            d.put("base", "auto");
            for (String kk : Compose.PATCH_KEYS) if (q.containsKey(kk)) d.put(kk, q.get(kk));
            out.add(d);
        }
        return out;
    }

    private static int[] at(int k, int x0, int z0, int x1, int z1, int a, int b) {
        return switch (k) {
            case 0 -> new int[]{x0 + a, z0 + b};
            case 1 -> new int[]{x0 + b, z1 - a};
            case 2 -> new int[]{x1 - a, z1 - b};
            default -> new int[]{x1 - b, z0 + a};
        };
    }

    // ------------------------------------------------------------------------------------------------ selfcheck

    /** selfcheck.check(..., only="floating")["floating"]: blocks the ground does not reach through touching blocks. */
    static List<Long> floating(Map<Long, String> blocks, int ground, Map<Long, List<Canvas.Run>> cols) {
        LinkedHashMap<Long, String> solid = new LinkedHashMap<>();
        for (Map.Entry<Long, String> e : blocks.entrySet()) if (e.getValue() != null && !e.getValue().isEmpty() && !e.getValue().equals(AIR)) solid.put(e.getKey(), e.getValue());
        for (Map.Entry<Long, List<Canvas.Run>> e : cols.entrySet()) {
            int x = Canvas.cx(e.getKey()), z = Canvas.cz(e.getKey());
            for (Canvas.Run r : e.getValue())
                if (!r.s().equals(AIR)) for (int y = r.y0(); y <= r.y1(); y++) solid.putIfAbsent(Canvas.key(x, y, z), r.s());
        }
        Set<Long> seen = new HashSet<>();
        List<Long> todo = new ArrayList<>();
        for (long k : solid.keySet()) if (Canvas.ky(k) <= ground + 1) todo.add(k);
        LinkedHashMap<Long, Object[]> low = new LinkedHashMap<>();
        for (Map.Entry<Long, String> e : solid.entrySet()) {
            long k = e.getKey();
            long ck = Canvas.ckey(Canvas.kx(k), Canvas.kz(k));
            Object[] cur = low.get(ck);
            if (Canvas.ky(k) < (cur == null ? 1 << 30 : (int) cur[0])) low.put(ck, new Object[]{Canvas.ky(k), e.getValue()});
        }
        for (Map.Entry<Long, Object[]> e : low.entrySet()) {
            int y = (int) e.getValue()[0];
            if (ground + 1 < y && y <= ground + 5 && Generator.standing((String) e.getValue()[1]))
                todo.add(Canvas.key(Canvas.cx(e.getKey()), y, Canvas.cz(e.getKey())));
        }
        seen.addAll(todo);
        while (!todo.isEmpty()) {
            long p = todo.remove(todo.size() - 1);
            int x = Canvas.kx(p), y = Canvas.ky(p), z = Canvas.kz(p);
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++) {
                        int s = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                        if (s == 0 || s > 2) continue;
                        long q = Canvas.key(x + dx, y + dy, z + dz);
                        if (solid.containsKey(q) && !seen.contains(q)) {
                            seen.add(q);
                            todo.add(q);
                        }
                    }
        }
        List<Long> out = new ArrayList<>();
        for (Map.Entry<Long, String> e : solid.entrySet()) {
            String n = e.getValue().split("\\[", 2)[0];
            n = n.substring(n.lastIndexOf(':') + 1);
            if (!seen.contains(e.getKey()) && !n.endsWith("light")) out.add(e.getKey());
        }
        return out;
    }
}
