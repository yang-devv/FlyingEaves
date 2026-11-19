package com.flyingeaves.gen;

import com.flyingeaves.gen.py.IntPair;
import com.flyingeaves.gen.py.PySet;
import com.flyingeaves.gen.py.UcrtMath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

/**
 * bgen's platforms beyond the plain one: 须弥座 / 三层 tier maths (podium_tiers, tier_flights, tier_step), the round
 * 坛 (round_platform) and the 城台 (chengtai, chengtai_well, _walks_in). Not yet ported parts throw
 * {@link NotPorted}, so the oracle reports them instead of drawing something else.
 */
public final class Platforms {
    private Platforms() {
    }

    /** bgen.podium_tiers: (how far out from the walls, height), outermost first. */
    static List<int[]> podiumTiers(int pod, String style, int colon, int step) {
        int m0 = Math.max(2, colon + 1);
        List<int[]> out = new ArrayList<>();
        if (pod <= 0) return out;
        if (style.equals("xumizuo")) {
            out.add(new int[]{m0, pod});
            return out;
        }
        int n = Math.min(3, pod);
        for (int i = 0; i < n; i++) {
            int h = Math.floorDiv(pod, n) + (i < Math.floorMod(pod, n) ? 1 : 0);
            out.add(new int[]{m0 + step * (n - 1 - i), h});
        }
        return out;
    }

    /** bgen.tier_flights: (edge out from the walls, its top, the top under it). */
    static List<int[]> tierFlights(List<int[]> tiers, int y) {
        List<int[]> out = new ArrayList<>();
        int top = y;
        for (int[] mh : tiers) {
            out.add(new int[]{mh[0], top + mh[1], top});
            top += mh[1];
        }
        return out;
    }

    /** bgen.tier_step. */
    static int tierStep(int pod, Integer margin, int run) {
        if (margin == null || margin == 0 || margin <= 4) return 2;
        int p = Math.max(1, pod);
        return Math.max(Math.max(2, Math.floorDiv(margin, 3)), -Math.floorDiv(-p, Math.min(3, p)) * run + 1);
    }

    /** bgen.chengtai_size: (out from the walls, height). */
    static int[] chengtaiSize(int pod, int colon, Integer margin) {
        return new int[]{Math.max(Math.max(3, colon + 2), margin == null ? 0 : margin), Math.max(1, pod)};
    }

    static final int CHENGTAI_STEPS = 4;
    static final Map<Integer, Integer> TUNNELS = Map.of(1, 0, 2, 1, 3, 3, 4, 5);

    /** A disc of radius r round (0, 0): bgen's {(u, v) ... if math.hypot(u, v) <= r} (sequential adds). */
    static PySet<IntPair> disc(double r) {
        PySet<IntPair> out = new PySet<>();
        int ir = (int) r;
        for (int u = -ir - 1; u < ir + 2; u++)
            for (int v = -ir - 1; v < ir + 2; v++) if (UcrtMath.hypotInt(u, v) <= r) out.add(IntPair.of(u, v));
        return out;
    }

    /** bgen.round_platform. */
    static void roundPlatform(Frame f, int R, int y, int pod, String style, String base, boolean noFront, Object slope, Integer margin, List<String> backs, int half) {
        if (pod <= 0) return;
        double rr = R + 0.5;
        int run = Arch.stepRun(slope);
        List<Frame> faces = new ArrayList<>();
        if (!noFront) faces.add(f);
        for (String d : backs) faces.add(f.sub(0, 0, d));
        List<int[]> dirs = new ArrayList<>();
        List<Frame> all = new ArrayList<>();
        all.add(f);
        for (String d : backs) all.add(f.sub(0, 0, d));
        for (Frame g : all) {
            int x0 = g.wx(0, 0), z0 = g.wz(0, 0), x1 = g.wx(0, 1), z1 = g.wz(0, 1);
            for (int[] d : new int[][]{{0, 1}, {0, -1}, {1, 0}, {-1, 0}})
                if (f.wx(d[0], d[1]) == f.ox + x1 - x0 && f.wz(d[0], d[1]) == f.oz + z1 - z0) {
                    dirs.add(d);
                    break;
                }
        }
        boolean hasBase = base != null && !base.isEmpty();
        if (!"xumizuo".equals(style) && !"tiers".equals(style)) {
            int m = margin != null && margin != 0 ? margin : 1;
            for (IntPair c : disc(rr + m))
                for (int yy = y + 1; yy < y + pod + 1; yy++) f.put(c.a(), yy, c.b(), hasBase ? base : Arch.masonry(c.a(), yy, c.b()));
            String st = House.stairsOf(base, "minecraft:stone_brick_stairs");
            String sl = Arch.slabFor(st);
            for (Frame g : faces)
                for (Arch.Step s : Arch.flight(pod, run))
                    for (int u = -half; u < half + 1; u++) {
                        int v = R + m + 1 + s.k();
                        g.put(u, y + pod - s.dt(), v, Arch.stepBlock(s.kind(), st, sl, "north"));
                        for (int yy = y + 1; yy < y + pod - s.dt(); yy++) g.put(u, yy, v, hasBase ? base : Arch.masonry(u, yy, v));
                    }
            return;
        }
        Arch.Mat mat = House.platformMat(base);
        String core = hasBase ? base : Arch.Pal.JADE;
        int step = tierStep(pod, margin, run);
        List<int[]> tiers = podiumTiers(pod, style, margin == null ? 0 : margin, step);
        int top = y;
        for (int[] mh : tiers) {
            int m = mh[0], h = mh[1];
            top += h;
            PySet<IntPair> cells = disc(rr + m);
            for (IntPair c : cells) for (int yy = top - h + 1; yy < top + 1; yy++) f.put(c.a(), yy, c.b(), core);
            PySet<IntPair> ring = new PySet<>();
            for (IntPair c : cells) {
                boolean edge = false;
                for (int[] d : Bgen.N4) if (!cells.contains(IntPair.of(c.a() + d[0], c.b() + d[1]))) edge = true;
                if (edge) ring.add(c);
            }
            for (IntPair c : ring) {
                int u = c.a(), v = c.b();
                boolean open = false;
                for (int[] d : dirs) if (u * d[0] + v * d[1] > 0 && Math.abs(u * d[1] - v * d[0]) <= half) open = true;
                if (open) continue;
                Map<String, Object> con = new LinkedHashMap<>();
                con.put("east", ring.contains(IntPair.of(u + 1, v)) ? "low" : "none");
                con.put("west", ring.contains(IntPair.of(u - 1, v)) ? "low" : "none");
                con.put("south", ring.contains(IntPair.of(u, v + 1)) ? "low" : "none");
                con.put("north", ring.contains(IntPair.of(u, v - 1)) ? "low" : "none");
                boolean straight = (con.get("east").equals("low") && con.get("west").equals("low") && con.get("north").equals("none") && con.get("south").equals("none"))
                        || (con.get("north").equals("low") && con.get("south").equals("low") && con.get("east").equals("none") && con.get("west").equals("none"));
                if (Math.floorMod(u * 7 + v * 13, 5) == 0) {
                    f.put(u, top + 1, v, Blocks.B("quartz_pillar", "axis", "y"));
                    f.put(u, top + 2, v, Blocks.slab("quartz"));
                } else {
                    con.put("up", !straight);
                    f.put(u, top + 1, v, Blocks.B("diorite_wall", con));
                }
            }
        }
        List<int[]> flights = step <= 2 ? List.of(new int[]{tiers.get(tiers.size() - 1)[0], top, y}) : tierFlights(tiers, y);
        for (int[] fl : flights) for (Frame g : faces) Arch.stairsDown(g, half, (int) rr + fl[0] + 1, fl[1], fl[2], true, 0, mat, run);
    }

    /** bgen.square_platform: returns its top. */
    static int squarePlatform(Frame f, int R, int y, int pod, Map<String, Object> spec, Map<String, Object> W) {
        String style = Dyn.truthy(spec.get("_podium_style")) ? (String) spec.get("_podium_style") : "plain";
        Integer margin = spec.get("_podium_margin") == null ? null : Dyn.toInt(spec.get("_podium_margin"));
        if (style.equals("chengtai"))
            return chengtai(f, R, R, y, pod, 0, (String) spec.get("_base"), spec.containsKey("_tunnels") ? Dyn.toInt(spec.get("_tunnels")) : 0, margin,
                    Dyn.truthy(spec.get("_four")));
        int ring = margin != null && margin != 0 ? margin : 1;
        Object[] plat = style.equals("xumizuo") || style.equals("tiers") ? new Object[]{style, ring, 0, 0} : new Object[]{"plain", ring, ring, ring};
        return (int) House.podium(f, R, R, y, pod, plat, spec, W, List.of(), false)[0];
    }

    /** bgen.chengtai: returns the top. */
    static int chengtai(Frame f, int hx, int hz, int y, int pod, int colon, String base, int tunnels, Integer margin, boolean cross) {
        int[] mh = chengtaiSize(pod, colon, margin);
        int m = mh[0], h = mh[1];
        int ex = hx + m, ez = hz + m;
        int top = y + h;
        boolean hasBase = base != null && !base.isEmpty();
        String body = hasBase ? base : Blocks.B("red_terracotta");
        String stone = Arch.Pal.JADE;
        for (int u = -ex; u <= ex; u++)
            for (int v = -ez; v <= ez; v++) {
                for (int yy = y + 1; yy < top + 1; yy++)
                    f.put(u, yy, v, (yy <= y + 2 || yy == top) && (Math.abs(u) == ex || Math.abs(v) == ez) ? stone : body);
                f.put(u, top, v, Math.abs(u) == ex || Math.abs(v) == ez ? stone : Blocks.B("polished_andesite"));
            }
        chengtaiTunnels(f, ex, ez, y, h, tunnels);
        if (cross) chengtaiTunnels(f.sub(0, 0, "east"), ez, ex, y, h, tunnels);
        for (int u = -ex; u <= ex; u++)
            for (int v = -ez; v <= ez; v++) {
                if (Math.abs(u) != ex && Math.abs(v) != ez) continue;
                f.put(u, top + 1, v, body);
                boolean merlon = Math.abs(u) == ex && Math.floorMod(v, 2) == 0;
                if (merlon) f.put(u, top + 2, v, body);
                f.put(u, merlon ? top + 3 : top + 2, v, Blocks.slab("stone_brick"));
            }
        if (h <= CHENGTAI_STEPS) {
            String flight = House.stairsOf(base, "minecraft:stone_brick_stairs");
            for (int u : new int[]{-ex + 2, -ex + 3}) {
                for (int k = 0; k < h; k++) {
                    int vv = -ez - h + k;
                    f.put(u, y + 1 + k, vv, Blocks.B(flight, "facing", "south", "half", "bottom"));
                    for (int yy = y + 1; yy < y + 1 + k; yy++) f.put(u, yy, vv, hasBase ? base : stone);
                }
                f.put(u, top + 1, -ez, Blocks.AIR);
                f.put(u, top + 2, -ez, Blocks.AIR);
                f.put(u, top + 3, -ez, Blocks.AIR);
            }
        }
        return top;
    }

    /** bgen._chengtai_tunnels. */
    static void chengtaiTunnels(Frame f, int ex, int ez, int y, int h, int tunnels) {
        int width = 2 * ex + 1;
        int count = width < 21 ? 1 : width < 41 ? 3 : 5;
        if (TUNNELS.containsKey(tunnels)) count = TUNNELS.get(tunnels);
        int midW = width >= 21 ? 5 : 3;
        List<Integer> centres = new ArrayList<>();
        centres.add(0);
        int gap = Math.max(midW + 4, Math.floorDiv(width, count + 1));
        for (int i = 1; i < Math.floorDiv(count, 2) + 1; i++) {
            centres.add(i * gap);
            centres.add(-i * gap);
        }
        int th = h - 3;
        if (th < 3 || count == 0) centres.clear();
        for (int c : centres) {
            int w = c == 0 ? midW : 3;
            int half = Math.floorDiv(w, 2);
            for (int v = -ez; v <= ez; v++) {
                for (int du = -half; du <= half; du++) {
                    int u = c + du;
                    f.put(u, y, v, Blocks.B("stone_bricks"));
                    int rise = th - (Math.abs(du) == half && half >= 1 ? 1 : 0);
                    for (int yy = y + 1; yy < y + rise + 1; yy++) f.put(u, yy, v, Blocks.AIR);
                }
                if (Math.abs(v) == ez || Math.abs(v) == ez - 1)
                    for (int side : new int[]{-1, 1}) f.put(c + side * half, y + th, v, Blocks.stair("stone_brick", side < 0 ? "east" : "west", "top"));
            }
        }
    }

    /** A neighbour for a 城台's stair: (frame x0, z0, x1, z1), whether it is a room. */
    record Neighbour(int[] fp, boolean room) {
    }

    @SuppressWarnings("unchecked")
    static List<Neighbour> neighbours(Object o) {
        List<Neighbour> out = new ArrayList<>();
        if (o == null) return out;
        for (Object e : (List<Object>) o) {
            Object[] a = (Object[]) e;
            out.add(new Neighbour((int[]) a[0], (boolean) a[1]));
        }
        return out;
    }

    static int dist(int[] fp, int x, int z) {
        return Math.max(Math.max(fp[0] - x, x - fp[2]), 0) + Math.max(Math.max(fp[1] - z, z - fp[3]), 0);
    }

    /** bgen._walks_in. */
    static boolean walksIn(Frame f, int[] start, int hx, int hz, int yb, int m, List<Neighbour> nbs) {
        BiPredicate<int[], Integer> free = (uv, yy) -> {
            int x = f.wx(uv[0], uv[1]), z = f.wz(uv[0], uv[1]);
            for (Neighbour n : nbs) if (n.fp[0] <= x && x <= n.fp[2] && n.fp[1] <= z && z <= n.fp[3]) return false;
            String b = f.get(uv[0], yy, uv[1]);
            return !Arch.solidAt(f, uv[0], yy, uv[1]) || (b != null && (b.contains("_door") || b.contains("carpet")));
        };
        Set<IntPair> seen = new HashSet<>();
        List<int[]> todo = new ArrayList<>();
        todo.add(new int[]{start[0], start[2]});
        while (!todo.isEmpty()) {
            int[] c = todo.remove(todo.size() - 1);
            int u = c[0], v = c[1];
            if (seen.contains(IntPair.of(u, v)) || Math.abs(u) > hx + m || Math.abs(v) > hz + m) continue;
            seen.add(IntPair.of(u, v));
            if (Math.abs(u) < hx && Math.abs(v) < hz) return true;
            if (!(free.test(c, yb + 1) && free.test(c, yb + 2))) continue;
            todo.add(new int[]{u + 1, v});
            todo.add(new int[]{u - 1, v});
            todo.add(new int[]{u, v + 1});
            todo.add(new int[]{u, v - 1});
        }
        return false;
    }

    interface Avoid {
        boolean test(int u, int v, boolean ground);
    }

    /** bgen.chengtai_well: the walking path (u, y, v) or null. */
    static List<int[]> chengtaiWell(Frame f, int hx, int hz, int y, int pod, int colon, String base, List<Neighbour> nbs, Integer margin) {
        int[] mh = chengtaiSize(pod, colon, margin);
        int m = mh[0], h = mh[1];
        if (h <= CHENGTAI_STEPS) return null;
        int ax = f.wx(-hx, -hz), az = f.wz(-hx, -hz), bx = f.wx(hx, hz), bz = f.wz(hx, hz);
        int[] own = {Math.min(ax, bx), Math.min(az, bz), Math.max(ax, bx), Math.max(az, bz)};
        Avoid avoid = null;
        if (nbs != null && !nbs.isEmpty()) avoid = (u, v, ground) -> {
            int x = f.wx(u, v), z = f.wz(u, v);
            for (Neighbour n : nbs) if (dist(n.fp, x, z) == 0 || (n.room && !ground && dist(n.fp, x, z) < dist(own, x, z))) return true;
            return false;
        };
        boolean hasBase = base != null && !base.isEmpty();
        return stairWell(f, y, y + h, new int[]{-hx - m + 1, hx + m - 1}, -hz - m, House.stairsOf(base, "minecraft:stone_brick_stairs"),
                hasBase ? base : Blocks.B("stone_bricks"), null, avoid);
    }

    /** arch._well_path: (t, row, level, kind, up); kind 0 floor, 1 stair, 2 arrive. */
    static List<int[]> wellPath(int n, int rise) {
        int F = -Math.floorDiv(-rise, n);
        int r = rise - n * (F - 1);
        List<int[]> path = new ArrayList<>();
        for (int t = 0; t < n - r + 1; t++) path.add(new int[]{t, 0, 0, 0, 0});
        int lvl = 0;
        for (int i = 0; i < F; i++) {
            int row = Math.floorMod(i, 2);
            int k = i == 0 ? r : n;
            if (row == 0) for (int t = n - k + 1; t < n + 1; t++) path.add(new int[]{t, row, ++lvl, 1, 1});
            else for (int t = n; t > 0; t--) path.add(new int[]{t, row, ++lvl, 1, -1});
            int end = row == 0 ? n + 1 : 0;
            if (i < F - 1) {
                path.add(new int[]{end, row, lvl, 0, 0});
                path.add(new int[]{end, 1 - row, lvl, 0, 0});
            } else path.add(new int[]{end, row, lvl, 2, 0});
        }
        return path;
    }

    static String dirName(int du, int dv) {
        return du == 1 ? "east" : du == -1 ? "west" : dv == 1 ? "south" : "north";
    }

    /** arch.stair_well: the walking path [(u, y, v)] or null. */
    static List<int[]> stairWell(Frame f, int y, int top, int[] uRange, int vBack, String stairs, String floor, String light, Avoid avoid) {
        int rise = top - y;
        if (rise < 1) return null;
        String lt = light != null ? light : Arch.unseenLight();
        Map<IntPair, Boolean> bodyC = new HashMap<>(), exitC = new HashMap<>();
        BiPredicate<Integer, Integer> body = (u, v) -> bodyC.computeIfAbsent(IntPair.of(u, v), k -> {
            for (int yy = y + 1; yy < top + 1; yy++) if (!Arch.solidAt(f, u, yy, v)) return false;
            return true;
        });
        BiPredicate<Integer, Integer> exitOk = (u, v) -> exitC.computeIfAbsent(IntPair.of(u, v), k -> {
            if (!(Arch.solidAt(f, u, top, v) && !Arch.solidAt(f, u, top + 1, v) && !Arch.solidAt(f, u, top + 2, v))) return false;
            for (int[] d : Arch.DIRD) {
                String s = f.get(u + d[0], top + 1, v + d[1]);
                if (s != null && s.contains("_door")) return false;
            }
            return true;
        });
        List<Integer> u0s = new ArrayList<>();
        for (int u = uRange[0]; u <= uRange[1]; u++) u0s.add(u);
        List<Integer> u0sRev = new ArrayList<>(u0s);
        Collections.reverse(u0sRev);
        int[][][] orients = {{{1, 0}, {0, 1}}, {{-1, 0}, {0, 1}}, {{0, 1}, {1, 0}}, {{0, 1}, {-1, 0}}};
        for (int[][] o : orients) {
            int[] dl = o[0], dc = o[1];
            int[] pd = dl[1] == 0 ? new int[]{0, -1} : new int[]{-dc[0], 0};
            for (int n : new int[]{6, 5, 4, 3}) {
                List<int[]> path0 = wellPath(n, rise);
                int vLo = vBack + 1, vHi = dl[1] == 0 ? vBack + 4 : -vBack - n - 1;
                boolean rev = (dl[0] == -1 && dl[1] == 0) || (dc[0] == -1 && dc[1] == 0);
                for (int v0 = vLo; v0 < vHi; v0++) {
                    for (int u0 : rev ? u0sRev : u0s) {
                        final int fu0 = u0, fv0 = v0;
                        BiFunction<Integer, Integer, int[]> at = (t, row) -> new int[]{fu0 + dl[0] * t + dc[0] * row, fv0 + dl[1] * t + dc[1] * row};
                        Set<IntPair> well = new HashSet<>();
                        for (int t = 0; t < n + 2; t++)
                            for (int r : new int[]{0, 1}) {
                                int[] c = at.apply(t, r);
                                well.add(IntPair.of(c[0], c[1]));
                            }
                        Set<IntPair> ring = new HashSet<>();
                        for (int t = -1; t < n + 3; t++)
                            for (int r : new int[]{-1, 0, 1, 2}) {
                                int[] c = at.apply(t, r);
                                ring.add(IntPair.of(c[0], c[1]));
                            }
                        ring.removeAll(well);
                        List<IntPair> door = new ArrayList<>();
                        int[] p0 = at.apply(0, 0);
                        int pu = p0[0], pv = p0[1];
                        while (Arch.solidAt(f, pu + pd[0], y + 1, pv + pd[1]) && door.size() < 6) {
                            pu += pd[0];
                            pv += pd[1];
                            door.add(IntPair.of(pu, pv));
                        }
                        if (door.isEmpty() || Arch.solidAt(f, pu + pd[0], y + 1, pv + pd[1]) || Arch.solidAt(f, pu + pd[0], y + 2, pv + pd[1])) continue;
                        boolean ok = true;
                        for (IntPair c : well)
                            if (!body.test(c.a(), c.b())) {
                                ok = false;
                                break;
                            }
                        if (!ok) continue;
                        for (IntPair c : ring)
                            if (!door.contains(c) && !body.test(c.a(), c.b())) {
                                ok = false;
                                break;
                            }
                        if (!ok) continue;
                        if (avoid != null) {
                            boolean hit = false;
                            Set<IntPair> allc = new HashSet<>(well);
                            allc.addAll(ring);
                            allc.addAll(door);
                            for (IntPair c : allc) if (avoid.test(c.a(), c.b(), false)) hit = true;
                            if (hit || avoid.test(pu + pd[0], pv + pd[1], true)) continue;
                        }
                        for (int[] st : path0) {
                            if (st[3] == 2 || st[2] + 3 >= rise) {
                                int[] c = at.apply(st[0], st[1]);
                                if (!exitOk.test(c[0], c[1])) {
                                    ok = false;
                                    break;
                                }
                            }
                        }
                        if (!ok) continue;
                        Set<List<Integer>> keep = new HashSet<>();
                        List<int[]> out = new ArrayList<>();
                        for (int[] st : path0) {
                            int[] c = at.apply(st[0], st[1]);
                            int u = c[0], v = c[1], lv = st[2];
                            keep.add(List.of(u, y + lv, v));
                            if (st[3] == 1) f.put(u, y + lv, v, Blocks.B(stairs, "facing", dirName(dl[0] * st[4], dl[1] * st[4]), "half", "bottom"));
                            else if (st[3] == 0 || !Arch.solidAt(f, u, y + lv, v)) f.put(u, y + lv, v, floor);
                            out.add(new int[]{u, y + lv + 1, v});
                        }
                        for (int i = 0; i < path0.size(); i++) {
                            int[] st = path0.get(i);
                            if (st[3] == 2) continue;
                            int[] c = at.apply(st[0], st[1]);
                            int u = c[0], v = c[1], lv = st[2];
                            for (int yy = y + lv + 1; yy < Math.min(y + lv + 3, top) + 1; yy++)
                                if (!keep.contains(List.of(u, yy, v)))
                                    f.put(u, yy, v, lt != null && i % 6 == 3 && yy == y + lv + 2 && yy < top ? lt : Blocks.AIR);
                        }
                        for (IntPair c : door) {
                            f.put(c.a(), y + 1, c.b(), Blocks.AIR);
                            f.put(c.a(), y + 2, c.b(), Blocks.AIR);
                        }
                        return out;
                    }
                }
            }
        }
        return null;
    }

    /** The end of bgen.house for a 城台: chengtai_well, and a door when the walk from it is cut off. */
    static void chengtaiAfter(Frame f, int hx, int hz, int y, int pod, int yb, int colon, Map<String, Object> spec, Integer margin, Map<String, Object> W) {
        List<Neighbour> nbs = neighbours(spec.get("_neighbours"));
        List<int[]> way = chengtaiWell(f, hx, hz, y, pod, colon, (String) spec.get("_base"), nbs, margin);
        if (way == null || walksIn(f, way.get(way.size() - 1), hx, hz, yb, chengtaiSize(pod, colon, margin)[0], nbs)) return;
        int[] last = way.get(way.size() - 1);
        int au = last[0], av = last[2];
        List<int[]> wall = new ArrayList<>();
        for (int u = -hx + 2; u < hx - 1; u++) for (int v : new int[]{-hz, hz}) wall.add(new int[]{u, v});
        for (int u : new int[]{-hx, hx}) for (int v = -hz + 2; v < hz - 1; v++) wall.add(new int[]{u, v});
        wall.sort(Comparator.comparingInt(c -> Math.abs(c[0] - au) + Math.abs(c[1] - av)));
        for (int[] c : wall.subList(0, Math.min(12, wall.size()))) {
            int cu = c[0], cv = c[1];
            int du, dv;
            if (Math.abs(cu) == hx) {
                du = cu > 0 ? 1 : -1;
                dv = 0;
            } else {
                du = 0;
                dv = cv > 0 ? 1 : -1;
            }
            int ou = cu + du, ov = cv + dv, iu = cu - du, iv = cv - dv;
            if (Objects.equals(f.get(cu, yb + 1, cv), W.get("post")) || !Arch.solidAt(f, ou, yb, ov) || Arch.solidAt(f, ou, yb + 1, ov)
                    || Arch.solidAt(f, ou, yb + 2, ov)) continue;
            String facing = dv == 1 ? "north" : dv == -1 ? "south" : du == 1 ? "west" : "east";
            for (int kk : new int[]{1, 2}) {
                f.put(cu, yb + kk, cv, Blocks.B(Dyn.truthy(W.get("door")) ? (String) W.get("door") : "spruce_door", "facing", facing, "half",
                        kk == 1 ? "lower" : "upper", "hinge", "left", "open", false, "powered", false));
                String in = f.get(iu, yb + kk, iv);
                if (in == null || !in.contains("stairs")) f.put(iu, yb + kk, iv, Blocks.AIR);
            }
            break;
        }
    }

    /** A Python function the Java port does not have yet. */
    public static final class NotPorted extends RuntimeException {
        public NotPorted(String what) {
            super("not ported yet: " + what);
        }
    }
}
