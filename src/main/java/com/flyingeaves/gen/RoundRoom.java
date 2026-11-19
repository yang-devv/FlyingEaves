package com.flyingeaves.gen;

import com.flyingeaves.gen.py.IntPair;
import com.flyingeaves.gen.py.Py;
import com.flyingeaves.gen.py.PyRandom;
import com.flyingeaves.gen.py.PySet;
import com.flyingeaves.gen.py.UcrtMath;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.flyingeaves.gen.Arch.H;
import static com.flyingeaves.gen.Arch.roof;
import static com.flyingeaves.gen.Blocks.B;
import static com.flyingeaves.gen.Blocks.slab;
import static com.flyingeaves.gen.Bgen.ws;

/** bgen.round_room (圆塔) and compose's tower helpers (tower_cells, _tower_doors). */
public final class RoundRoom {
    private RoundRoom() {
    }

    static final double RAD_TO_DEG = 180.0 / Math.PI;

    /** degrees(atan2(u, v)) % 360: 0 = front (+v), 90 = +u. */
    static double ang(IntPair c) {
        return Py.floorMod(RAD_TO_DEG * UcrtMath.atan2(c.a(), c.b()), 360.0);
    }

    static boolean near(IntPair c, double target, double tol) {
        double d = Math.abs(Py.floorMod(ang(c) - target + 180, 360.0) - 180);
        return d <= tol;
    }

    /** A storey's circle: disc, wall ring, inside, posts, post spacing. */
    record Circle(PySet<IntPair> d, PySet<IntPair> w, PySet<IntPair> inside, PySet<IntPair> posts, int st) {
    }

    static Circle circle(int Rc) {
        double rc = Rc + 0.5;
        PySet<IntPair> d = new PySet<>();
        for (int u = -Rc; u <= Rc; u++) for (int v = -Rc; v <= Rc; v++) if (UcrtMath.hypotInt(u, v) <= rc) d.add(IntPair.of(u, v));
        PySet<IntPair> w = ringOf(d);
        int st = Rc >= 4 ? 45 : 90;
        PySet<IntPair> posts = new PySet<>();
        for (IntPair c : w) {
            boolean any = false;
            for (int a = st / 2; a < 360; a += st) if (near(c, a, 360.0 / (8 * Rc))) {
                any = true;
                break;
            }
            if (any) posts.add(c);
        }
        return new Circle(d, w, d.minus(w), posts, st);
    }

    static PySet<IntPair> ringOf(PySet<IntPair> d) {
        PySet<IntPair> w = new PySet<>();
        for (IntPair c : d) {
            boolean edge = false;
            for (int[] dd : Bgen.N4) if (!d.contains(IntPair.of(c.a() + dd[0], c.b() + dd[1]))) {
                edge = true;
                break;
            }
            if (edge) w.add(c);
        }
        return w;
    }

    /** bgen.round_radii. */
    static int[] roundRadii(int R, int n, int taper) {
        int[] r = new int[n];
        for (int s = 0; s < n; s++) r[s] = Math.max(Math.min(R, 2), R - s * taper);
        return r;
    }

    static final Map<Long, String> DOOR_FACING = Map.of(Canvas.ckey(0, 1), "north", Canvas.ckey(0, -1), "south", Canvas.ckey(1, 0), "west", Canvas.ckey(-1, 0), "east");

    /** bgen.round_room. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> roundRoom(Frame f, int y, PyRandom rng, Map<String, Object> spec, String tier, int R, int n, Integer sh0) {
        double k = Bgen.TIER_K.get(tier);
        String wallsName = Bgen.pick(rng, spec.get("walls"));
        Map<String, Object> W = Bgen.setWalls(wallsName);
        Bgen.WALL_FILL[0] = ws(W, "fill");
        Arch.roofTheme(Bgen.pick(rng, spec.get("themes")));
        int sh;
        if (sh0 != null && sh0 != 0) sh = sh0;
        else if (Dyn.truthy(spec.get("_sh"))) sh = Dyn.toInt(spec.get("_sh"));
        else {
            List<Object> r = Dyn.list(spec.get("sh"));
            sh = (int) Py.round(rng.randint(Dyn.toInt(r.get(0)), Dyn.toInt(r.get(1))) * (1 + (k - 1) * 0.35));
        }
        int taper = Math.max(0, Dyn.truthy(spec.get("_taper")) ? Dyn.toInt(spec.get("_taper")) : 0);
        boolean pz = Dyn.truthy(spec.get("_pingzuo")) && n > 1;
        int[] radii = roundRadii(R, n, taper);
        Map<Integer, Circle> circles = new HashMap<>();
        for (int Rc : radii) circles.computeIfAbsent(Rc, RoundRoom::circle);
        IntPair ladC = IntPair.of(0, -radii[n - 1] + 1);
        Circle c0 = circles.get(R);
        PySet<IntPair> wall0 = c0.w;
        java.util.function.IntToDoubleFunction platEdge = s -> Math.max(radii[s - 1] + 0.5, radii[s] + 2.5) + 1;
        int pod = spec.get("_pod") != null ? Dyn.toInt(spec.get("_pod")) : 1;
        int yb = y + pod;
        boolean noFront = Dyn.truthy(spec.get("_no_front_door"));
        LinkedHashMap<IntPair, Integer> sideDoors = new LinkedHashMap<>();
        if (Dyn.truthy(spec.get("_side_doors")))
            for (Map.Entry<IntPair, Integer> e : ((Map<IntPair, Integer>) spec.get("_side_doors")).entrySet())
                if (wall0.contains(e.getKey())) sideDoors.put(e.getKey(), e.getValue());
        Set<IntPair> noWin = spec.get("_no_window_cells") == null ? Set.of() : (Set<IntPair>) spec.get("_no_window_cells");
        LinkedHashMap<IntPair, IntPair> sideInner = new LinkedHashMap<>();
        for (IntPair c : sideDoors.keySet()) {
            for (int[] d : Bgen.N4)
                if (!c0.d.contains(IntPair.of(c.a() + d[0], c.b() + d[1]))) {
                    sideInner.put(c, IntPair.of(c.a() - d[0], c.b() - d[1]));
                    break;
                }
        }
        if ("square".equals(spec.get("_podium_shape")) && pod > 0) Platforms.squarePlatform(f, R, y, pod, spec, W);
        else {
            Integer pm = spec.get("_podium_margin") == null ? null : Dyn.toInt(spec.get("_podium_margin"));
            Platforms.roundPlatform(f, R, y, pod, (String) spec.get("_podium_style"), (String) spec.get("_base"), noFront, spec.get("_step_slope"), pm, List.of(), 1);
        }
        String roundRoof = Dyn.truthy(spec.get("_round_roof")) ? (String) spec.get("_round_roof") : "cone";
        boolean flatTop = roundRoof.equals("flat");
        int extraEaves = spec.containsKey("_extra_eaves") ? Dyn.toInt(spec.get("_extra_eaves")) : (Dyn.truthy(spec.get("_double_eave")) ? 1 : 0);
        int want = n > 1 && !flatTop && extraEaves == 0 ? Math.max(0, 3 - Math.max(0, 7 - sh)) : 0;
        int cut = Math.min(want, Math.max(0, sh - 5));
        int droop = want - cut;
        for (int s = 0; s < n; s++) {
            int ys = yb + s * sh;
            int shs = s == n - 1 ? sh - cut : sh;
            int Rs = radii[s];
            Circle cs = circles.get(Rs);
            List<IntPair> ladMore = Rs != radii[n - 1] ? List.of(IntPair.of(0, ladC.b() - 1), IntPair.of(0, ladC.b() + 1)) : List.of();
            for (IntPair c : cs.d) if (!(s > 0 && c.equals(ladC))) f.put(c.a(), ys, c.b(), ws(W, "floor"));
            for (IntPair c : cs.w) {
                int u = c.a(), v = c.b();
                List<int[]> out = new ArrayList<>();
                for (int[] d : Bgen.N4) if (!cs.d.contains(IntPair.of(u + d[0], v + d[1]))) out.add(d);
                boolean alongU = false;
                for (int[] d : out) if (d[1] != 0) alongU = true;
                boolean door = ((s == 0 && !noFront) || (pz && s > 0)) && near(c, 0, 12) && v > 0 && Math.abs(u) <= (Rs >= 5 ? 1 : 0);
                String facing = "north";
                if (!door && sideDoors.getOrDefault(c, 0) > s) {
                    door = true;
                    facing = out.size() == 1 ? DOOR_FACING.getOrDefault(Canvas.ckey(out.get(0)[0], out.get(0)[1]), "north") : "north";
                }
                boolean win = false;
                if (!door && !cs.posts.contains(c) && !noWin.contains(c)) {
                    for (int a = 0; a < 360; a += cs.st) if (near(c, a, 360.0 / (6 * Rs))) {
                        win = true;
                        break;
                    }
                    win = win && !near(c, 180, 20);
                }
                for (int kk = 1; kk <= shs; kk++) {
                    int yy = ys + kk;
                    if (door && kk < shs) {
                        if (kk <= 2)
                            f.put(u, yy, v, B(Dyn.truthy(W.get("door")) ? ws(W, "door") : "spruce_door", "facing", facing, "half", kk == 1 ? "lower" : "upper",
                                    "hinge", u > 0 ? "right" : "left", "open", false, "powered", false));
                        else f.put(u, yy, v, kk == 3 ? ws(W, "beam") : ws(W, "fill"));
                    } else if (cs.posts.contains(c)) f.put(u, yy, v, ws(W, "post"));
                    else if (kk == shs) f.put(u, yy, v, ws(W, "beam"));
                    else if (kk == 1) f.put(u, yy, v, ws(W, "base"));
                    else if (win && 2 <= kk && kk <= Math.min(Math.min(sh - 2, 4), shs - 1)) {
                        String pane = W.containsKey("pane") ? ws(W, "pane") : "white_stained_glass_pane";
                        f.put(u, yy, v, alongU ? B(pane, "east", true, "west", true) : B(pane, "north", true, "south", true));
                    } else f.put(u, yy, v, ws(W, "fill"));
                }
            }
            for (int kk = 1; kk <= sh; kk++) {
                if (s < n - 1) {
                    f.put(ladC.a(), ys + kk, ladC.b(), B(ws(W, "ladder"), "facing", "south"));
                    if (!ladMore.isEmpty()) f.put(0, ys + kk, ladC.b() - 1, ws(W, "post"));
                }
            }
            if (pz && s > 0) {
                double pe = platEdge.applyAsDouble(s), rs = Rs + 0.5;
                PySet<IntPair> ring = new PySet<>();
                for (int u = -(int) pe - 1; u < (int) pe + 2; u++)
                    for (int v = -(int) pe - 1; v < (int) pe + 2; v++) {
                        double h = UcrtMath.hypotInt(u, v);
                        if (rs < h && h <= pe) ring.add(IntPair.of(u, v));
                    }
                PySet<IntPair> belowWall = circles.get(radii[s - 1]).w;
                for (IntPair c : ring) if (!belowWall.contains(c)) f.put(c.a(), ys, c.b(), ws(W, "beam"));
                PySet<IntPair> rail = new PySet<>();
                for (IntPair c : ring) {
                    boolean any = false;
                    for (int du = -1; du <= 1 && !any; du++)
                        for (int dv = -1; dv <= 1; dv++) {
                            IntPair nb = IntPair.of(c.a() + du, c.b() + dv);
                            if (!ring.contains(nb) && !cs.d.contains(nb)) {
                                any = true;
                                break;
                            }
                        }
                    if (any) rail.add(c);
                }
                for (IntPair c : rail) {
                    int u = c.a(), v = c.b();
                    Map<String, Object> con = new LinkedHashMap<>();
                    if (rail.contains(IntPair.of(u + 1, v))) con.put("east", true);
                    if (rail.contains(IntPair.of(u - 1, v))) con.put("west", true);
                    if (rail.contains(IntPair.of(u, v + 1))) con.put("south", true);
                    if (rail.contains(IntPair.of(u, v - 1))) con.put("north", true);
                    f.put(u, ys + 1, v, B(ws(W, "rail"), con));
                }
            }
            String rt;
            if (Dyn.truthy(spec.get("_floors"))) {
                List<Object> floors = Dyn.list(spec.get("_floors"));
                rt = (String) Dyn.list(Dyn.map(floors.get(Math.min(s, floors.size() - 1))).get("program")).get(0);
            } else rt = (String) Dyn.list(spec.get(s == 0 ? "ground" : "upper")).get(0);
            int q = Math.max(1, (int) ((Rs - 1) / 1.42));
            List<IntPair> here = new ArrayList<>();
            for (Map.Entry<IntPair, Integer> e : sideDoors.entrySet()) if (e.getValue() > s && sideInner.containsKey(e.getKey())) here.add(sideInner.get(e.getKey()));
            List<int[]> doors = new ArrayList<>();
            doors.add(new int[]{0, Rs - 1});
            doors.add(new int[]{0, q});
            doors.add(new int[]{ladC.a(), ladC.b()});
            doors.add(new int[]{0, -q});
            for (IntPair c : ladMore) doors.add(new int[]{c.a(), c.b()});
            for (IntPair c : here) doors.add(new int[]{c.a(), c.b()});
            if (!rt.equals(Bgen.EMPTY_ROOM)) {                       // (空房: nothing; 内饰：无: drawn, not kept)
                boolean bare = House.t(spec, "_bare");
                Furniture.furnish(bare ? Bgen.scratch(f) : f, rng, rt, -q, -q, q, q, ys, doors, "north", shs);
                Set<IntPair> keep = new HashSet<>();
                keep.add(ladC);
                keep.add(IntPair.of(0, Rs - 1));
                keep.addAll(ladMore);
                keep.addAll(here);
                Map<String, Object> fixture = Bgen.pickLight(rng, rt, tier, null);
                if (!bare) Bgen.lightRoom(f, -Rs + 1, -Rs + 1, Rs - 1, Rs - 1, ys, shs, keep, fixture, false, new HashSet<>(cs.inside.toList()));
            }
            if (s < n - 1) {
                int top = ys + sh + Math.max(0, 7 - sh);
                double rs = Rs + 0.5;
                H h = new H();
                if (pz) {
                    double pe = platEdge.applyAsDouble(s + 1);
                    for (int u = -(int) pe - 4; u < (int) pe + 5; u++)
                        for (int v = -(int) pe - 4; v < (int) pe + 5; v++) {
                            double r = UcrtMath.hypotInt(u, v);
                            if (rs < r && r <= pe + 2.5) h.put(u, v, ys + sh - 0.5 - 0.6 * Math.max(0.0, r - pe));
                        }
                    Arch.rasterRoof(f, h, (u, v, val) -> (int) Math.floor(val) - 1, Math.min(ys + 4, ys + sh - 1));
                    continue;
                }
                for (int u = -Rs - 3; u < Rs + 4; u++)
                    for (int v = -Rs - 3; v < Rs + 4; v++) {
                        double r = UcrtMath.hypotInt(u, v);
                        if (rs < r && r <= rs + 2.5) h.put(u, v, top - 0.6 * (r - rs));
                    }
                Arch.rasterRoof(f, h, (u, v, val) -> (int) Math.floor(val) - 1, ys + 4);
                PySet<IntPair> discN = circles.get(radii[s + 1]).d;
                H upRing = new H();
                for (IntPair c : cs.d) if (!discN.contains(c)) upRing.put(c.a(), c.b(), top + 0.6 * (rs - UcrtMath.hypotInt(c.a(), c.b())));
                if (!upRing.isEmpty()) {
                    final int ceil = ys + sh;
                    Arch.rasterRoof(f, upRing, (u, v, val) -> ceil, null, null, false, null);
                }
            }
        }
        int Rl = radii[n - 1];
        Circle cl = circles.get(Rl);
        double rr = Rl + 0.5;
        int topY = yb + n * sh - cut;
        for (int eave = 0; eave < extraEaves; eave++) {
            H h = new H();
            int up = Math.max(0, 7 - sh);
            if (up != 0) {
                for (IntPair c : cl.w) for (int kk = 1; kk <= up; kk++) f.put(c.a(), topY + kk, c.b(), cl.posts.contains(c) ? ws(W, "post") : ws(W, "fill"));
                topY += up;
            }
            for (int u = -Rl - 3; u < Rl + 4; u++)
                for (int v = -Rl - 3; v < Rl + 4; v++) {
                    double r = UcrtMath.hypotInt(u, v);
                    if (rr < r && r <= rr + 2.5) h.put(u, v, topY - 0.6 * (r - rr));
                }
            Arch.rasterRoof(f, h, (u, v, val) -> (int) Math.floor(val) - 1, yb + (n - 1) * sh + 4);
            int band = Math.max(3, Math.min(5, Math.floorDiv(sh, 2) + 1));
            for (IntPair c : cl.w)
                for (int kk = 1; kk <= band; kk++) f.put(c.a(), topY + kk, c.b(), kk == band ? ws(W, "beam") : cl.posts.contains(c) ? ws(W, "post") : ws(W, "fill"));
            topY += band;
        }
        for (IntPair c : cl.inside) f.put(c.a(), topY, c.b(), B("spruce_planks"));
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("floor", yb);
        res.put("sh", sh);
        res.put("storeys", n);
        res.put("hx", R);
        res.put("hz", R);
        res.put("wall_top", topY);
        res.put("top_sh", sh - cut);
        if (flatTop) {
            String par = Dyn.truthy(spec.get("_base")) ? (String) spec.get("_base") : ws(W, "base");
            for (IntPair c : cl.inside) f.put(c.a(), topY, c.b(), ws(W, "floor"));
            for (IntPair c : cl.w) {
                f.put(c.a(), topY, c.b(), par);
                f.put(c.a(), topY + 1, c.b(), par);
                if (Math.floorMod(c.a() * 3 + c.b() * 5, 4) < 2 || c.equals(IntPair.of(0, Rl))) f.put(c.a(), topY + 2, c.b(), par);
            }
            res.put("top", topY + 3);
            return res;
        }
        String form = roundRoof;
        int ov = 2 + (k > 1.2 ? 1 : 0);
        if (form.equals("dome")) ov = 1;
        double ext = rr + ov;
        if (pz) {
            ov = Math.max(ov, (int) Math.ceil(platEdge.applyAsDouble(n - 1) + 1.5 - rr));
            ext = Math.max(ext, platEdge.applyAsDouble(n - 1) + 1.5);
        }
        int pitch = spec.containsKey("_pitch") ? Dyn.toInt(spec.get("_pitch")) : 0;
        Double pp = Bgen.PITCH.get(pitch);
        double steep = spec.containsKey("steep") ? ((Number) spec.get("steep")).doubleValue() : 0.5;
        double rise = Math.max(4, Rl * (0.9 + (pp != null && pp != 0 ? pp : steep) * 0.4));
        if (form.equals("dome")) rise = Math.max(4, rr * (0.8 + (pp != null && pp != 0 ? pp : 0.5) * 0.4));
        H h = new H();
        for (int u = -Rl - ov - 1; u < Rl + ov + 2; u++)
            for (int v = -Rl - ov - 1; v < Rl + ov + 2; v++) {
                double r = UcrtMath.hypotInt(u, v);
                if (r <= ext) h.put(u, v, topY + 1 + rise * prof(form, 1 - r / ext));
            }
        double flat = 0;
        if (form.equals("luding")) {
            flat = topY + 1 + Math.max(2, Py.round(rise * 0.45));
            H h2 = new H();
            for (Map.Entry<Long, Double> e : h.entrySet()) h2.put(e.getKey(), Math.min(e.getValue(), flat));
            h = h2;
        }
        final int ftop = topY, fdroop = droop;
        final boolean dome = form.equals("dome");
        final PySet<IntPair> disc = cl.d;
        Arch.BaseFn base = (u, v, val) -> disc.contains(IntPair.of(u, v)) ? ftop : dome ? ftop - fdroop : Math.max(ftop - fdroop, (int) Math.floor(val) - 2);
        if (Dyn.truthy(spec.get("_cap"))) {
            h = Bgen.capped(h, (int[]) spec.get("_cap"));
            Arch.rasterRoof(f, h, base, null);
            int fl = Bgen.capRoof(f, h, (int[]) spec.get("_cap"), ws(W, "rail"));
            res.put("top", fl + 2);
            res.put("cap_y", fl);
            return res;
        }
        Arch.rasterRoof(f, h, base, null);
        int ry = (int) Math.floor(h.max());
        if (form.equals("luding")) {
            PySet<IntPair> cells = new PySet<>();
            for (Map.Entry<Long, Double> e : h.entrySet()) if (e.getValue() >= flat) cells.add(IntPair.of(Canvas.cx(e.getKey()), Canvas.cz(e.getKey())));
            for (IntPair c : cells) {
                boolean edge = false;
                for (int[] d : Bgen.N4) if (!cells.contains(IntPair.of(c.a() + d[0], c.b() + d[1]))) edge = true;
                f.put(c.a(), ry, c.b(), edge ? B(roof("ridge")) : B(roof("full")));
                if (edge) f.put(c.a(), ry + 1, c.b(), slab(roof("tile")));
            }
            res.put("top", ry + 3);
            return res;
        }
        Arch.finial(f, ry, k < 1.5 ? 4 : 7);
        res.put("top", ry + 8);
        return res;
    }

    static double prof(String form, double t) {
        if (form.equals("helmet")) {
            if (t < 0.45) return 0.45 * UcrtMath.floatPow(t / 0.45, 1.6);
            return 0.45 + 0.55 * Math.sqrt(Math.max(0.0, 1 - UcrtMath.floatPow((1 - t) / 0.55, 2)));
        }
        if (form.equals("dome")) return Math.sqrt(Math.max(0.0, 1 - UcrtMath.floatPow(1 - t, 2)));
        return UcrtMath.floatPow(t, 1.3);
    }

    // ------------------------------------------------------------------------------------------------ compose

    /** compose.tower_cells: (disc, wall). */
    static PySet<IntPair>[] towerCells(int R) {
        double rr = R + 0.5;
        PySet<IntPair> disc = new PySet<>();
        for (int u = -R; u <= R; u++) for (int v = -R; v <= R; v++) if (UcrtMath.hypotInt(u, v) <= rr) disc.add(IntPair.of(u, v));
        @SuppressWarnings("unchecked") PySet<IntPair>[] out = new PySet[]{disc, ringOf(disc)};
        return out;
    }

    static boolean insideRect(Map<String, Object> o, int x, int z) {
        return Compose.gi(o, "x0") <= x && x <= Compose.gi(o, "x1") && Compose.gi(o, "z0") <= z && z <= Compose.gi(o, "z1");
    }

    /** compose._tower_doors. */
    @SuppressWarnings("unchecked")
    static void towerDoors(List<Map<String, Object>> rooms) {
        for (Map<String, Object> t : rooms) {
            if (!"round".equals(Compose.shape(t))) continue;
            int R = Compose.gi(t, "hx");
            PySet<IntPair>[] dw = towerCells(R);
            PySet<IntPair> disc = dw[0], wall = dw[1];
            int[] rot = Blocks.ROT.get((String) t.get("front"));
            int tcx = Compose.gi(t, "cx"), tcz = Compose.gi(t, "cz");
            java.util.function.BiFunction<Integer, Integer, int[]> world = (u, v) -> new int[]{tcx + rot[0] * u + rot[1] * v, tcz + rot[2] * u + rot[3] * v};
            List<Map<String, Object>> others = new ArrayList<>();
            for (Map<String, Object> o : rooms) if (o != t) others.add(o);
            PySet<IntPair> noWin = new PySet<>();
            for (IntPair c : wall)
                for (int[] d : Compose.DIRS4) {
                    IntPair nb = IntPair.of(c.a() + d[0], c.b() + d[1]);
                    if (disc.contains(nb)) continue;
                    int[] xz = world.apply(nb.a(), nb.b());
                    boolean any = false;
                    for (Map<String, Object> o : others) if (insideRect(o, xz[0], xz[1])) any = true;
                    if (any) noWin.add(c);
                }
            t.put("no_win", new HashSet<>(noWin.toList()));
            boolean found = false;
            for (Map<String, Object> h : others) {
                if ("round".equals(Compose.shape(h))) continue;
                if (Compose.floor(t) != Compose.floor(h)) continue;
                int levels = Compose.gi(t, "sh") == Compose.gi(h, "sh") && Compose.taper(h) == 0 && Compose.taper(t) == 0
                        ? Math.min(Compose.gi(t, "n"), Compose.gi(h, "n")) : 1;
                Set<IntPair> reserve = Compose.gi(h, "n") > 1 ? Bgen.stairPlan(Compose.gi(h, "hx"), Compose.gi(h, "hz"), Compose.gi(h, "sh")).reserve : Set.of();
                Object[] best = null;
                for (IntPair c : wall) {
                    List<int[]> outs = new ArrayList<>();
                    for (int[] d : Compose.DIRS4) if (!disc.contains(IntPair.of(c.a() + d[0], c.b() + d[1]))) outs.add(d);
                    if (outs.size() != 1) continue;
                    int du = outs.get(0)[0], dv = outs.get(0)[1];
                    if (!wall.contains(IntPair.of(c.a() + dv, c.b() + du)) || !wall.contains(IntPair.of(c.a() - dv, c.b() - du))) continue;
                    if (c.a() - du == 0 && c.b() - dv == -R + 1) continue;
                    if (c.b() == R && Math.abs(c.a()) <= 1 && !Dyn.truthy(t.get("no_front"))) continue;
                    int[] e = world.apply(c.a() + du, c.b() + dv);
                    int ex = e[0], ez = e[1];
                    if (!insideRect(h, ex, ez)) continue;
                    int[] hl = Compose.local(h, ex, ez);
                    int hu = hl[0], hv = hl[1];
                    String kind;
                    IntPair cell;
                    if (Math.abs(hu) < Compose.gi(h, "hx") && Math.abs(hv) < Compose.gi(h, "hz")) {
                        if (reserve.contains(IntPair.of(hu, hv))) continue;
                        kind = "free";
                        cell = IntPair.of(hu, hv);
                    } else {
                        int[] x2 = world.apply(c.a() + 2 * du, c.b() + 2 * dv);
                        int[] il = Compose.local(h, x2[0], x2[1]);
                        if (!(Math.abs(il[0]) < Compose.gi(h, "hx") && Math.abs(il[1]) < Compose.gi(h, "hz")) || !Compose.doorOk(h, hu, hv)) continue;
                        kind = "wall";
                        cell = IntPair.of(hu, hv);
                    }
                    long score = (long) (ex - Compose.gi(h, "cx")) * (ex - Compose.gi(h, "cx")) + (long) (ez - Compose.gi(h, "cz")) * (ez - Compose.gi(h, "cz"));
                    if (best == null || score < (long) best[0]) best = new Object[]{score, c, kind, cell};
                }
                if (best != null) {
                    IntPair c = (IntPair) best[1];
                    Map<IntPair, Integer> tsd = Compose.sideDoors(t);
                    tsd.put(c, Math.max(levels, tsd.getOrDefault(c, 0)));
                    Map<IntPair, Integer> book = best[2].equals("free")
                            ? (Map<IntPair, Integer>) h.computeIfAbsent("free", kk -> new LinkedHashMap<IntPair, Integer>()) : Compose.sideDoors(h);
                    IntPair cell = (IntPair) best[3];
                    book.put(cell, Math.max(levels, book.getOrDefault(cell, 0)));
                    found = true;
                }
            }
            if (Dyn.truthy(t.get("no_front")) && !found) t.put("no_front", false);
        }
    }
}
