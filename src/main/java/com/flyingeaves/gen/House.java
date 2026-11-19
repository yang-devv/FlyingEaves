package com.flyingeaves.gen;

import com.flyingeaves.gen.py.IntPair;
import com.flyingeaves.gen.py.Py;
import com.flyingeaves.gen.py.PyRandom;
import com.flyingeaves.gen.py.PySet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.flyingeaves.gen.Arch.H;
import static com.flyingeaves.gen.Arch.Pal;
import static com.flyingeaves.gen.Arch.roof;
import static com.flyingeaves.gen.Blocks.AIR;
import static com.flyingeaves.gen.Blocks.B;
import static com.flyingeaves.gen.Blocks.slab;
import static com.flyingeaves.gen.Blocks.stair;
import static com.flyingeaves.gen.Bgen.BEAST_N;
import static com.flyingeaves.gen.Bgen.BRACKET_L;
import static com.flyingeaves.gen.Bgen.CAIHUA;
import static com.flyingeaves.gen.Bgen.EAVE_OUT;
import static com.flyingeaves.gen.Bgen.LIFT;
import static com.flyingeaves.gen.Bgen.ws;

/**
 * bgen.house and what it draws with: the 'house' archetype (every room of 单体建造 but round towers and the special
 * shapes), its platforms (podium, widen_podium), walls from wall-bay parts (draw_walls, pick_bay), eaves, decor.
 */
public final class House {
    private House() {
    }

    /** bgen.WINDOWS: window cells of the storey being built: (u, v, storey y, row k, top row). */
    static final List<int[]> WINDOWS = new ArrayList<>();

    static Object g(Map<String, Object> m, String k) {
        return m.get(k);
    }

    static boolean t(Map<String, Object> m, String k) {
        return Dyn.truthy(m.get(k));
    }

    static int gi(Map<String, Object> m, String k, int dflt) {
        Object v = m.get(k);
        return v == null ? dflt : Dyn.toInt(v);
    }

    @SuppressWarnings("unchecked")
    static <T> T cast(Object o) {
        return (T) o;
    }

    /** bgen.house. hx, hz, n: null to draw them. Returns the result dict. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> house(Frame f, int y, PyRandom rng, Map<String, Object> spec, String tier, Integer hx0, Integer hz0, Integer n0) {
        double k = Bgen.TIER_K.get(tier);
        Frame outer = f;
        boolean mayWing = !t(spec, "_wing") && !t(spec, "_no_wing") && "house".equals(spec.get("arch"));
        if (mayWing) f = new Frame(new Canvas(), outer.ox, outer.oz, outer.front);
        String wallsName = Bgen.pick(rng, spec.get("walls"));
        Map<String, Object> W = Bgen.setWalls(wallsName);
        Map<String, Object> bayCtx = new LinkedHashMap<>();
        bayCtx.put("chosen", new LinkedHashMap<String, Object>());
        bayCtx.put("tier", tier);
        bayCtx.put("walls", wallsName);
        bayCtx.put("style", spec.get("_bay_style"));
        Bgen.WALL_FILL[0] = ws(W, "fill");
        Arch.roofTheme(Bgen.pick(rng, spec.get("themes")));
        int hx, hz;
        if (hx0 == null) {
            List<Object> size = Dyn.list(spec.get("size"));
            List<Object> s0 = Dyn.list(size.get(0)), s1 = Dyn.list(size.get(1));
            int w = Bgen.odd(rng.randint(Dyn.toInt(s0.get(0)), Dyn.toInt(s0.get(1))) * (1 + (k - 1) * 0.6));
            int d = Bgen.odd(rng.randint(Dyn.toInt(s1.get(0)), Dyn.toInt(s1.get(1))) * (1 + (k - 1) * 0.6));
            hx = Math.floorDiv(w, 2);
            hz = Math.floorDiv(d, 2);
        } else {
            hx = hx0;
            hz = hz0;
        }
        int n;
        if (n0 != null && n0 != 0) n = n0;
        else {
            List<Object> st = Dyn.list(spec.get("storeys"));
            n = rng.randint(Dyn.toInt(st.get(0)), Dyn.toInt(st.get(1)));
        }
        List<Object> shr = Dyn.list(spec.get("sh"));
        int sh = (int) Py.round(rng.randint(Dyn.toInt(shr.get(0)), Dyn.toInt(shr.get(1))) * (1 + (k - 1) * 0.35));
        if (t(spec, "_sh")) sh = Dyn.toInt(spec.get("_sh"));
        List<Object> pr = Dyn.list(spec.get("podium"));
        int pod = rng.randint(Dyn.toInt(pr.get(0)), Dyn.toInt(pr.get(1))) + (k > 1.2 ? 1 : 0);
        if (spec.get("_pod") != null) pod = Dyn.toInt(spec.get("_pod"));
        String form = Bgen.pick(rng, spec.get("roofs"));
        int yb = y;
        String pstyle = t(spec, "_podium_style") ? (String) spec.get("_podium_style") : "plain";
        int colon = t(spec, "_colonnade") ? 2 : 0;
        Integer margin = spec.get("_podium_margin") == null ? null : Dyn.toInt(spec.get("_podium_margin"));
        Object[] plat = null;
        Canvas platCv = null;
        int vdepth = t(spec, "_veranda") ? (hx < 7 ? 3 : 4) : 0;
        int bdepth = t(spec, "_back_veranda") ? (hx < 7 ? 3 : 4) : 0;
        List<String> backs = new ArrayList<>();
        if (t(spec, "_back_door") || t(spec, "_through")) backs.add("north");
        if (t(spec, "_four")) {
            backs.add("east");
            backs.add("west");
        }
        if ("round".equals(spec.get("_podium_shape")) && pod > 0) {
            pstyle = pstyle.equals("chengtai") ? "plain" : pstyle;
            int out = Math.max(colon, Math.max(vdepth, bdepth));
            int rc = (int) Math.ceil(com.flyingeaves.gen.py.UcrtMath.hypotInt(hx + colon, hz + out) - 0.5);
            Platforms.roundPlatform(f, rc, y, pod, pstyle, (String) spec.get("_base"), t(spec, "_no_front_door"), spec.get("_step_slope"), margin,
                    backs, hx <= 4 ? 2 : hx <= 8 ? 3 : 4);
            yb = y + pod;
        } else if (pstyle.equals("chengtai") && pod > 0) {
            yb = Platforms.chengtai(f, hx, hz, y, pod, Math.max(colon, Math.max(vdepth, bdepth)), (String) spec.get("_base"), gi(spec, "_tunnels", 0), margin,
                    t(spec, "_four"));
            spec = new LinkedHashMap<>(spec);
            spec.put("_back_door", true);
        } else if ((pstyle.equals("xumizuo") || pstyle.equals("tiers")) && pod > 0) {
            plat = new Object[]{pstyle, Math.max(Math.max(colon, vdepth), Math.max(bdepth, margin != null && margin != 0 ? margin : 1)), 0, 0};
            Object[] r = podium(f, hx, hz, y, pod, plat, spec, W, backs, false);
            yb = (int) r[0];
            platCv = (Canvas) r[1];
        } else if (pod != 0) {
            int ring = t(spec, "_no_podium_ring") ? 0 : (margin != null && margin != 0 ? margin : 1);
            ring = Math.max(ring, colon);
            plat = new Object[]{"plain", ring, Math.max(ring, vdepth), Math.max(ring, bdepth)};
            Object[] r = podium(f, hx, hz, y, pod, plat, spec, W, backs, false);
            yb = (int) r[0];
            platCv = (Canvas) r[1];
        }
        int bay;
        int doorW;
        int taper = Math.max(0, t(spec, "_taper") ? Dyn.toInt(spec.get("_taper")) : 0);
        LinkedHashMap<IntPair, Integer> sideDoors = new LinkedHashMap<>();
        if (t(spec, "_side_doors")) sideDoors.putAll((Map<IntPair, Integer>) spec.get("_side_doors"));
        int[] wingPlan = null;
        if (mayWing && hx >= 4 && rng.random() < 0.4) {
            int side = n == 1 ? rng.choice(List.of(-1, 1)) : 1;
            int whz = Math.max(3, hz - 1 - rng.randint(0, 2));
            int whx = Math.max(3, Math.min(hx, rng.randint(3, 6)));
            wingPlan = new int[]{side, whx, whz};
            sideDoors.put(IntPair.of(side * hx, -(hz - whz)), 1);
        }
        int rows = BRACKET_L.getOrDefault(gi(spec, "_brackets", 0), 1);
        int extra = spec.containsKey("_extra_eaves") ? Dyn.toInt(spec.get("_extra_eaves")) : (t(spec, "_double_eave") ? 1 : 0);
        int cut = 0;
        if (n > 1 && extra == 0 && t(spec, "_storey_eaves"))
            cut = Math.max(0, Math.min(2 - Math.max(0, 6 - sh) + Math.max(0, rows - 1), sh - 5));
        Set<IntPair> stairsCells = new HashSet<>();
        Set<IntPair> well = new HashSet<>();
        Set<IntPair> aboveFree = new HashSet<>();
        Set<IntPair> belowFurnFree = new HashSet<>();
        for (int s = 0; s < n; s++) {
            int ys = yb + s * sh;
            int shs = s == n - 1 ? sh - cut : sh;
            int hxs = Math.max(2, hx - s * taper), hzs = Math.max(2, hz - s * taper);
            int hxn, hzn;
            if (s < n - 1) {
                hxn = Math.max(2, hx - (s + 1) * taper);
                hzn = Math.max(2, hz - (s + 1) * taper);
            } else {
                hxn = hxs;
                hzn = hzs;
            }
            bay = hxs <= 5 ? 3 : hxs <= 9 ? 4 : 5;
            doorW = hxs <= 4 ? 1 : hxs <= 8 ? 2 : 3;
            for (int u = -hxs; u <= hxs; u++)
                for (int v = -hzs; v <= hzs; v++)
                    if (!well.contains(IntPair.of(u, v)) && !Bgen.isLamp(f.get(u, ys, v))) f.put(u, ys, v, ws(W, "floor"));
            Set<IntPair> noWin = new HashSet<>();
            if (spec.get("_no_window_cells") != null) noWin.addAll((Set<IntPair>) spec.get("_no_window_cells"));
            if (wingPlan != null) {
                int wside = wingPlan[0], wwhz = wingPlan[2];
                for (int vv = -hzs; vv < -hzs + 2 * wwhz + 1; vv++) noWin.add(IntPair.of(wside * hxs, vv));
            }
            if (s < n - 1) {
                for (IntPair c : Bgen.stairPlan(hxn, hzn, sh).reserve)
                    for (int[] d : Bgen.N4) {
                        noWin.add(IntPair.of(c.a() + d[0], c.b() + d[1]));
                        noWin.add(IntPair.of(-(c.a() + d[0]), c.b() + d[1]));
                    }
            }
            Map<String, Object> wspec = new LinkedHashMap<>(spec);
            wspec.put("_no_window_cells", noWin);
            List<Object> floorsL = t(spec, "_floors") ? Dyn.list(spec.get("_floors")) : null;
            Object fo;
            {
                int len = floorsL != null ? floorsL.size() : 1;
                Object fl = floorsL != null ? floorsL.get(Math.min(s, len - 1)) : null;
                fo = Dyn.truthy(fl) ? Dyn.map(fl).get("open") : null;
            }
            if (Dyn.truthy(fo)) {
                wspec.put("_open", "open".equals(fo));
                wspec.put("_open_front", "front".equals(fo));
                wspec.put("_through", "through".equals(fo));
            }
            WINDOWS.clear();
            drawWalls(f, rng, W, hxs, hzs, ys, sh, bay, s, n, wspec, doorW, bayCtx);
            if (shs < sh) {
                for (int u = -hxs - 1; u < hxs + 2; u++)
                    for (int v = -hzs - 1; v < hzs + 2; v++)
                        if (Math.abs(u) >= hxs || Math.abs(v) >= hzs) {
                            int x = f.wx(u, v), z = f.wz(u, v);
                            for (int yy = ys + shs + 1; yy < ys + sh + 1; yy++) f.cv.blocks.remove(Canvas.key(x, yy, z));
                        }
            }
            List<IntPair> inner = new ArrayList<>();
            for (Map.Entry<IntPair, Integer> e : sideDoors.entrySet()) {
                int du = e.getKey().a(), dv = e.getKey().b();
                if (s < e.getValue()) {
                    int tall = Math.min(3, shs - 1);
                    for (int kk = 1; kk <= tall; kk++) f.put(du, ys + kk, dv, AIR);
                    if (shs > tall) f.put(du, ys + tall + 1, dv, ws(W, "beam"));
                    inner.add(Math.abs(du) == hxs ? IntPair.of(du - (du > 0 ? 1 : -1), dv) : IntPair.of(du, dv - (dv > 0 ? 1 : -1)));
                }
            }
            if (t(spec, "_free_cells"))
                for (Map.Entry<IntPair, Integer> e : ((Map<IntPair, Integer>) spec.get("_free_cells")).entrySet())
                    if (s < e.getValue() && !inner.contains(e.getKey())) inner.add(e.getKey());
            for (int u = -hxs + 1; u < hxs; u++) {
                String a = f.get(u, ys + 1, hzs);
                if (a != null && a.contains("_door") && !inner.contains(IntPair.of(u, hzs - 1))) inner.add(IntPair.of(u, hzs - 1));
                String b = f.get(u, ys + 1, -hzs);
                if (b != null && b.contains("_door") && !inner.contains(IntPair.of(u, -hzs + 1))) inner.add(IntPair.of(u, -hzs + 1));
            }
            Set<IntPair> belowFree = aboveFree;
            Set<IntPair> nextWell = new HashSet<>(), nextFree = new HashSet<>();
            Set<IntPair> furnFree, nextFurnFree;
            if (s < n - 1) {
                Bgen.StairPlan sp = Bgen.stairPlan(hxn, hzn, sh);
                String ladFace = "east";
                int[] ladPost = {-1, 0};
                if (sp.ladder) {
                    int su0 = (int) sp.steps.get(0)[0], sv0 = (int) sp.steps.get(0)[1];
                    String a = f.get(su0 - 1, ys + 1, sv0);
                    String b = f.get(su0, ys + 1, sv0 - 1);
                    if ((a == null || a.equals(AIR)) && (well.contains(IntPair.of(su0 - 1, sv0)) || !(b == null || b.equals(AIR)))) {
                        ladFace = "south";
                        ladPost = new int[]{0, -1};
                    }
                }
                for (Object[] st : sp.steps) {
                    int su = (int) st[0], sv = (int) st[1], rise = (int) st[2];
                    String facing = (String) st[3];
                    f.put(su, ys + rise, sv, sp.ladder ? B(ws(W, "ladder"), "facing", ladFace) : B(ws(W, "inner_stairs"), "facing", facing, "half", "bottom"));
                    if (sp.ladder) {
                        String p = f.get(su + ladPost[0], ys + rise, sv + ladPost[1]);
                        if (p == null || p.equals(AIR)) f.put(su + ladPost[0], ys + rise, sv + ladPost[1], ws(W, "post"));
                    }
                    for (int kk = ys + rise + 1; kk < ys + rise + 4; kk++) if (kk >= ys + sh) f.put(su, kk, sv, AIR);
                }
                for (int[] l : sp.landing) {
                    f.put(l[0], ys + l[2], l[1], B("spruce_planks"));
                    for (int kk = ys + l[2] + 1; kk < ys + l[2] + 4; kk++) if (kk >= ys + sh) f.put(l[0], kk, l[1], AIR);
                }
                stairsCells = new HashSet<>(sp.reserve);
                nextWell = sp.well;
                nextFree = sp.above;
                furnFree = new HashSet<>(sp.clear);
                nextFurnFree = new HashSet<>(sp.clearAbove);
            } else {
                furnFree = new HashSet<>();
                nextFurnFree = new HashSet<>();
            }
            furnFree.addAll(belowFurnFree);
            belowFurnFree = nextFurnFree;
            well = nextWell;
            aboveFree = nextFree;
            List<String> program;
            Object split, plan;
            if (floorsL != null) {
                Map<String, Object> fl = Dyn.map(floorsL.get(Math.min(s, floorsL.size() - 1)));
                program = new ArrayList<>();
                for (Object o : Dyn.list(fl.get("program"))) program.add((String) o);
                split = fl.get("split");
                plan = fl.get("plan");
            } else {
                program = new ArrayList<>();
                for (Object o : Dyn.list(spec.get(s == 0 ? "ground" : "upper"))) program.add((String) o);
                split = spec.get("_split");
                plan = spec.get("_plan");
            }
            Set<IntPair> keepFree = new HashSet<>(s < n - 1 ? stairsCells : Set.of());
            keepFree.addAll(belowFree);
            if (s == 0 && (t(spec, "_through") || t(spec, "_back_door") || t(spec, "_four"))) {
                int dwp = doorW <= 1 ? 0 : 1;
                for (int u = -dwp; u <= dwp; u++) for (int v = -hzs + 1; v < hzs; v++) keepFree.add(IntPair.of(u, v));
                if (t(spec, "_four")) for (int u = -hxs + 1; u < hxs; u++) for (int v = -dwp; v <= dwp; v++) keepFree.add(IntPair.of(u, v));
            }
            List<int[]> windows = new ArrayList<>();
            for (int[] w : WINDOWS) if (w[3] < shs) windows.add(w);
            Object partitions;
            if (t(wspec, "_open") || (s == 0 && t(wspec, "_through"))) partitions = Boolean.FALSE;
            else if (!(hxs >= 5 || hzs >= 5)) partitions = Boolean.FALSE;
            else {
                Object second = new HashSet<>(program).size() > 1 ? Boolean.TRUE : split;
                if (!Dyn.truthy(second)) partitions = second;
                else partitions = Dyn.truthy(plan) ? plan : Boolean.TRUE;
            }
            int maxSide = t(spec, "_max_side") ? Dyn.toInt(spec.get("_max_side")) : (int) (8 * Math.min(k, 2));
            List<Bgen.Used> assigned = Bgen.storeyInterior(f, rng, hxs, hzs, ys, shs, program, ws(W, "fill"), keepFree, 0, maxSide, partitions,
                    inner, tier, furnFree);
            Map<IntPair, Integer> level = new LinkedHashMap<>();
            for (int[] w : windows) {
                int wu = w[0], wv = w[1];
                if (wv == hzs && Math.abs(wu) != hxs) continue;
                String rt = behind(assigned, wu, wv, hxs);
                int lv = rt != null && Tables.CLOSED_ROOMS.contains(rt) ? 2 : rt != null && Tables.PRIVATE_ROOMS.contains(rt) ? 1 : 0;
                IntPair key = IntPair.of(Math.abs(wu), wv);
                level.put(key, Math.max(level.getOrDefault(key, 0), lv));
            }
            for (int[] w : windows) {
                int lv = level.getOrDefault(IntPair.of(Math.abs(w[0]), w[1]), 0);
                if (lv == 2 || (lv == 1 && w[3] < w[4])) f.put(w[0], w[2] + w[3], w[1], ws(W, "fill"));
            }
            stairsCells = new HashSet<>();
            if (s < n - 1) {
                int top = ys + sh;
                boolean want = t(spec, "balcony") || rng.random() < 0.4;
                if (hxn < hxs) {
                    for (int u = -hxs; u <= hxs; u++)
                        for (int v = -hzs; v <= hzs; v++)
                            if (Math.abs(u) > hxn || Math.abs(v) > hzn) {
                                String c = f.get(u, top, v);
                                if (c == null || c.equals(AIR)) f.put(u, top, v, ws(W, "floor"));
                            }
                    final int fhxs = hxs, fhzs = hzs;
                    java.util.function.BiPredicate<Integer, Integer> ring = (uu, vv) ->
                            (Math.abs(uu) == fhxs && Math.abs(vv) <= fhzs) || (Math.abs(vv) == fhzs && Math.abs(uu) <= fhxs);
                    for (int u = -hxs; u <= hxs; u++)
                        for (int v = -hzs; v <= hzs; v++)
                            if (ring.test(u, v)) {
                                Map<String, Object> con = new LinkedHashMap<>();
                                if (ring.test(u + 1, v)) con.put("east", true);
                                if (ring.test(u - 1, v)) con.put("west", true);
                                if (ring.test(u, v + 1)) con.put("south", true);
                                if (ring.test(u, v - 1)) con.put("north", true);
                                f.put(u, top + 1, v, B(ws(W, "rail"), con));
                            }
                } else if (want) {
                    int bw = t(spec, "balcony") ? 2 : 1;
                    for (int u = -hxs - bw; u < hxs + bw + 1; u++)
                        for (int v = -hzs - bw; v < hzs + bw + 1; v++)
                            if (Math.abs(u) > hxs || Math.abs(v) > hzs) {
                                f.put(u, top, v, ws(W, "beam"));
                                if (Math.abs(u) == hxs + bw || Math.abs(v) == hzs + bw)
                                    f.put(u, top + 1, v, Math.abs(v) == hzs + bw ? B(ws(W, "rail"), "east", true, "west", true) : B(ws(W, "rail"), "north", true, "south", true));
                            }
                }
                if (t(spec, "_storey_eaves")) {
                    Integer eo = EAVE_OUT.get(gi(spec, "_eave_out", 0));
                    int out = eo != null && eo != 0 ? eo : 2;
                    int liftK = gi(spec, "_lift", 0);
                    Integer L = LIFT.get(liftK);
                    int[] lyr = L != null && L != 0 ? java.util.Arrays.copyOfRange(new int[]{3, 2, 1}, 3 - L, 3) : (hxs >= 6 ? new int[]{2, 1} : new int[]{1});
                    int up = Math.max(0, 6 - sh);
                    boolean liftOn = LIFT.containsKey(liftK) ? LIFT.get(liftK) != 0 : true;
                    H h = Arch.roofSurface(hxs + out + 1, hzs + out + 1, top - 1 + up, out, out, 0.7 * out, null, liftOn ? lyr : new int[0], new int[]{0, 0},
                            new int[]{hxs, hzs});
                    H h2 = new H();
                    for (Map.Entry<Long, Double> e : h.entrySet()) h2.put(e.getKey(), Math.max(e.getValue(), ys + 5));
                    final int base = top - 1 + up;
                    Arch.rasterRoof(f, h2, (u, v, val) -> Math.max(base, (int) Math.floor(val) - 1), ys + 4);
                    if (BRACKET_L.getOrDefault(gi(spec, "_brackets", 0), 1) != 0) eaveBrackets(f, W, hxs, hzs, top, hxs <= 5 ? 3 : hxs <= 9 ? 4 : 5, 1);
                    Integer bn = BEAST_N.get(gi(spec, "_beasts", 0));
                    if (bn != null && bn != 0) {
                        List<List<int[]>> hips = new ArrayList<>();
                        for (int sgn : new int[]{-1, 1})
                            for (int sv : new int[]{-1, 1}) {
                                List<int[]> c = new ArrayList<>();
                                for (int i = out + 1; i > 0; i--) c.add(new int[]{sgn * (hxs + i), sv * (hzs + i)});
                                hips.add(c);
                            }
                        for (List<int[]> c : hips) Arch.ridgeCells(f, h2, c);
                        Bgen.rideBeasts(f, h2, hips, bn);
                    }
                } else {
                    int up = Math.max(0, 5 - sh);
                    H h = Arch.roofSurface(hxs + 3, hzs + 3, top - 1 + up, 2, 2, 1.4, null, new int[0], new int[]{0, 0}, new int[]{hxs, hzs});
                    final int base = top - 1 + up;
                    Arch.rasterRoof(f, h, (u, v, val) -> base, ys + 4);
                }
            }
        }
        int topY = yb + n * sh - cut;
        int head = yb + (n - 1) * sh + 4;
        int hxl = Math.max(2, hx - (n - 1) * taper), hzl = Math.max(2, hz - (n - 1) * taper);
        int bayL = hxl <= 5 ? 3 : hxl <= 9 ? 4 : 5;
        String caihua = (String) spec.get("_caihua");
        for (int eave = 0; eave < extra; eave++) {
            eaveBrackets(f, W, hxl, hzl, topY, bayL, 1);
            int up = t(spec, "_storey_eaves") ? Math.max(0, 6 - sh) : Math.max(0, 5 - sh);
            H h = Arch.roofSurface(hxl + 3, hzl + 3, topY - 1 + up, 2, 2, 1.4, null, new int[0], new int[]{0, 0}, new int[]{hxl, hzl});
            H h2 = new H();
            for (Map.Entry<Long, Double> e : h.entrySet()) h2.put(e.getKey(), Math.max(e.getValue(), head + 1));
            final int base = topY - 1 + up;
            Arch.rasterRoof(f, h2, (u, v, val) -> base, head);
            Integer bn = BEAST_N.get(gi(spec, "_beasts", 0));
            if (bn != null && bn != 0) {
                List<List<int[]>> lowHips = new ArrayList<>();
                for (int sgn : new int[]{-1, 1})
                    for (int sv : new int[]{-1, 1}) {
                        List<int[]> c = new ArrayList<>();
                        for (int i : new int[]{3, 2, 1}) c.add(new int[]{sgn * (hxl + i), sv * (hzl + i)});
                        lowHips.add(c);
                    }
                for (List<int[]> c : lowHips) Arch.ridgeCells(f, h2, c);
                Bgen.rideBeasts(f, h2, lowHips, bn);
            }
            int band = Bgen.band(sh);
            for (int u = -hxl; u <= hxl; u++)
                for (int v = -hzl; v <= hzl; v++) {
                    if (Math.abs(u) != hxl && Math.abs(v) != hzl) continue;
                    boolean post = (Math.abs(u) == hxl && Math.abs(v) == hzl) || (Math.abs(v) == hzl && Math.floorMod(u, bayL) == 0)
                            || (Math.abs(u) == hxl && Math.floorMod(v, bayL) == 0);
                    for (int kk = 1; kk <= band; kk++) f.put(u, topY + kk, v, kk == band ? ws(W, "beam") : post ? ws(W, "post") : ws(W, "fill"));
                }
            if (caihua != null && CAIHUA.containsKey(caihua)) Bgen.paintRing(f, caihua, hxl, hzl, bayL, topY + band - 1);
            topY += band;
        }
        for (int u = -hxl; u <= hxl; u++)
            for (int v = -hzl; v <= hzl; v++) {
                String c = f.get(u, topY, v);
                if (Dyn.truthy(caihua) && cut == 0 && (Math.abs(u) == hxl || Math.abs(v) == hzl) && !(c == null || c.equals(AIR))) continue;
                if (!Bgen.isLamp(c)) f.put(u, topY, v, Math.abs(u) == hxl || Math.abs(v) == hzl ? ws(W, "beam") : B("spruce_planks"));
            }
        if (cut != 0 && caihua != null && CAIHUA.containsKey(caihua)) Bgen.paintRing(f, caihua, hxl, hzl, bayL, topY);
        if (rows > 1) {
            for (int u = -hxl; u <= hxl; u++)
                for (int v = -hzl; v <= hzl; v++)
                    if (Math.abs(u) == hxl || Math.abs(v) == hzl) for (int kk = 1; kk < rows; kk++) f.put(u, topY + kk, v, ws(W, "beam"));
            topY += rows - 1;
        }
        Object[] cap = t(spec, "_cap") ? new Object[]{spec.get("_cap"), ws(W, "rail")} : null;
        int ry = Bgen.roofFor(f, hxl, hzl, topY, form, rng, 2 + (k > 1.2 ? 1 : 0), ((Number) spec.get("steep")).doubleValue(), true, k,
                (PyRandom) spec.get("_roof_rng"), gi(spec, "_pitch", 0), gi(spec, "_eave_out", 0), gi(spec, "_lift", 0),
                (colon != 0 && n == 1 && !t(spec, "_double_eave") ? 1 : 0) + Math.max(0, rows - 1), gi(spec, "_beasts", 0), cap, head);
        if (rows != 0) eaveBrackets(f, W, hxl, hzl, topY, bayL, rows);
        eaveFascia(f, W, hxl, hzl, topY, 4, head);
        if (colon != 0) {
            int bay0 = hx <= 5 ? 3 : hx <= 9 ? 4 : 5;
            int dw = hx <= 4 ? 1 : hx <= 8 ? 2 : 3;
            for (int u = -hx - colon; u < hx + colon + 1; u++)
                for (int v = -hz - colon; v < hz + colon + 1; v++)
                    if ((Math.abs(u) > hx || Math.abs(v) > hz) && f.get(u, yb, v) == null) f.put(u, yb, v, ws(W, "floor"));
            int ex = hx + colon, ez = hz + colon;
            for (int u = -ex; u <= ex; u++)
                for (int v = -ez; v <= ez; v++) {
                    if (Math.abs(u) != ex && Math.abs(v) != ez) continue;
                    boolean corner = Math.abs(u) == ex && Math.abs(v) == ez;
                    int along = Math.abs(v) == ez ? u : v;
                    if (!corner && Math.floorMod(along, bay0) != 0) continue;
                    if (v == ez && Math.abs(u) <= dw && !corner) continue;
                    if (!corner) {
                        int wu, wv;
                        if (Math.abs(v) == ez) {
                            wu = u;
                            wv = v > 0 ? hz : -hz;
                        } else {
                            wu = u > 0 ? hx : -hx;
                            wv = v;
                        }
                        String w1 = f.get(wu, yb + 1, wv);
                        if (w1 == null) w1 = "";
                        if (w1.isEmpty() || w1.equals(AIR) || w1.contains("_door")) continue;
                    }
                    Integer hit = null;
                    for (int yy = yb + 1; yy < yb + sh + 8; yy++)
                        if (f.get(u, yy, v) != null) {
                            hit = yy;
                            break;
                        }
                    if (hit == null) continue;
                    for (int yy = yb + 1; yy < hit; yy++) f.put(u, yy, v, ws(W, "post"));
                }
        }
        boolean wingOk = !t(spec, "_wing") && "house".equals(spec.get("arch")) && hx >= 4;
        if (wingOk && rng.random() < 0.35) veranda(f, rng, hx, hz, yb, sh, W, false);
        else if (t(spec, "_veranda")) veranda(f, rng, hx, hz, yb, sh, W, true);
        if (t(spec, "_back_veranda")) veranda(f.sub(0, 0, "north"), rng, hx, hz, yb, sh, W, true);
        Object[] wingPart = null;
        if (wingPlan != null) {
            int side = wingPlan[0], whx = wingPlan[1], whz = wingPlan[2];
            int basePod = pod - (k > 1.2 ? 1 : 0);
            Map<String, Object> wspec = new LinkedHashMap<>(spec);
            wspec.put("storeys", List.of(1, 1));
            wspec.put("_wing", true);
            wspec.put("decor", List.of());
            wspec.put("shop", false);
            wspec.put("balcony", false);
            wspec.put("podium", List.of(basePod, basePod));
            wspec.put("roofs", !form.equals("pyramid") ? List.of("yingshan", "xuanshan") : List.of("pyramid"));
            LinkedHashMap<IntPair, Integer> wsd = new LinkedHashMap<>();
            wsd.put(IntPair.of(-side * whx, 0), 1);
            wspec.put("_side_doors", wsd);
            wspec.put("_floors", null);
            wspec.put("_tall_beside", List.of(-side));
            Frame wf = new Frame(new Canvas(), f.ox, f.oz, f.front).sub(side * (hx + whx), -(hz - whz));
            Map<String, Object> wres = house(wf, y, rng, wspec, tier, whx, whz, 1);
            wingPart = new Object[]{wf, whx, whz, wres};
        }
        if (t(spec, "horsehead")) {
            List<Integer> sides = new ArrayList<>();
            List<Object> tb = t(spec, "_tall_beside") ? Dyn.list(spec.get("_tall_beside")) : List.of();
            for (int s : new int[]{-1, 1}) {
                boolean in = false;
                for (Object o : tb) if (Dyn.toInt(o) == s) in = true;
                if (!in) sides.add(s);
            }
            horseheadWalls(f, hxl, hzl, topY, ry, W, sides);
        }
        decorate(f, rng, spec, hx, hz, yb, n, sh, W, tier);
        if (plat != null && Dyn.truthy(plat[1])) widenPodium(f, hx, hz, y, pod, yb, plat, platCv, spec, W, backs);
        if (pstyle.equals("chengtai") && pod > 0) Platforms.chengtaiAfter(f, hx, hz, y, pod, yb, Math.max(colon, Math.max(vdepth, bdepth)), spec, margin, W);
        if (mayWing) {
            List<Map<String, Object>> parts = new ArrayList<>();
            if (wingPart != null) {
                Frame wf = (Frame) wingPart[0];
                Map<String, Object> wres = (Map<String, Object>) wingPart[3];
                parts.add(part(wf, (int) wingPart[1], (int) wingPart[2], y, Dyn.toInt(wres.get("floor")) + Dyn.toInt(wres.get("sh"))));
            }
            parts.add(part(f, hx, hz, y, topY));
            Joinparts.join(outer.cv, parts, null);
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("hx", hx);
        res.put("hz", hz);
        res.put("top", ry);
        res.put("floor", yb);
        res.put("storeys", n);
        res.put("sh", sh);
        res.put("wall_top", topY);
        res.put("cap_y", t(spec, "_cap") ? ry : null);
        res.put("top_sh", sh - cut);
        return res;
    }

    static String behind(List<Bgen.Used> assigned, int wu, int wv, int hxs) {
        int iu = Math.abs(wu) == hxs ? wu - (wu > 0 ? 1 : -1) : wu;
        int iv = Math.abs(wu) != hxs ? wv - (wv > 0 ? 1 : -1) : wv;
        for (Bgen.Used u : assigned) if (u.a() <= iu && iu <= u.c() && u.b() <= iv && iv <= u.d()) return u.rt();
        return null;
    }

    /** bgen._part: a built part for joinparts. */
    static Map<String, Object> part(Frame f, int hx, int hz, int y, int wallTop) {
        int ax = f.wx(-hx, -hz), az = f.wz(-hx, -hz), bx = f.wx(hx, hz), bz = f.wz(hx, hz);
        int[] fp = {Math.min(ax, bx), Math.min(az, bz), Math.max(ax, bx), Math.max(az, bz)};
        Integer top = null;
        for (long kk : f.cv.blocks.keySet()) {
            int x = Canvas.kx(kk), z = Canvas.kz(kk);
            if (fp[0] <= x && x <= fp[2] && fp[1] <= z && z <= fp[3]) top = top == null ? Canvas.ky(kk) : Math.max(top, Canvas.ky(kk));
        }
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("blocks", f.cv.blocks);
        p.put("cols", f.cv.cols);
        p.put("fp", fp);
        p.put("y", y);
        p.put("wall_top", wallTop);
        p.put("top", top == null ? wallTop : top);
        return p;
    }

    // ------------------------------------------------------------------------------------------------ platforms

    /** bgen.stairs_of. */
    static String stairsOf(String block, String fallback) {
        if (block == null || block.isEmpty()) return fallback;
        String[] parts = Blocks.name(block).split(":");
        String name = parts[parts.length - 1];
        Map<String, String> special = Map.of("packed_mud", "mud_brick_stairs", "red_terracotta", "brick_stairs", "calcite", "polished_diorite_stairs",
                "andesite", "andesite_stairs", "stone", "stone_stairs");
        String[] cs = {special.get(name), name + "_stairs", name.replace("_bricks", "_brick") + "_stairs", name.replace("_tiles", "_tile") + "_stairs",
                name.replace("smooth_", "") + "_stairs", name.replace("polished_", "") + "_stairs", name.endsWith("s") ? name.substring(0, name.length() - 1) + "_stairs" : null};
        for (String c : cs) if (c != null && !c.isEmpty() && Env.present("minecraft:" + c)) return "minecraft:" + c;
        return fallback;
    }

    /** bgen.slab_of. */
    static String slabOf(String block, String fallback) {
        String st = stairsOf(block, null);
        if (st != null && Env.present(st.replace("_stairs", "_slab"))) return st.replace("_stairs", "_slab");
        return fallback;
    }

    /** bgen.platform_mat. */
    static Arch.Mat platformMat(String base) {
        if (base == null || base.isEmpty()) return null;
        return new Arch.Mat(base, stairsOf(base, "minecraft:stone_brick_stairs"), slabOf(base, "minecraft:stone_brick_slab"));
    }

    /** bgen.podium: (top, the platform's own canvas). plat = [style, ring, front, back]. */
    static Object[] podium(Frame f, int hx, int hz, int y, int pod, Object[] plat, Map<String, Object> spec, Map<String, Object> W, List<String> backs, boolean keep) {
        Canvas pc = new Canvas();
        Frame g = new Frame(pc, f.ox, f.oz, f.front);
        String style = (String) plat[0];
        int ring = (int) plat[1], front = (int) plat[2], back = (int) plat[3];
        int top;
        if (style.equals("xumizuo") || style.equals("tiers")) {
            int run = Arch.stepRun(spec.get("_step_slope"));
            Integer pm = spec.get("_podium_margin") == null ? null : Dyn.toInt(spec.get("_podium_margin"));
            int step = Platforms.tierStep(pod, pm, run);
            List<int[]> tiers = Platforms.podiumTiers(pod, style, ring, step);
            int shHalf = hx <= 4 ? 2 : hx <= 8 ? 3 : 4;
            top = y;
            Arch.Mat mat = platformMat((String) spec.get("_base"));
            for (int[] mt : tiers) {
                top += mt[1];
                Arch.xumizuo(g, hx + mt[0], hz + mt[0], top, mt[1], null, mat);
            }
            int svHalf = hz <= 4 ? 2 : hz <= 8 ? 3 : 4;
            top = y;
            for (int[] mt : tiers) {
                top += mt[1];
                Arch.balustrade(g, hx + mt[0], hz + mt[0], top, new int[][]{{-shHalf, shHalf}}, backs.contains("north") ? new int[][]{{-shHalf, shHalf}} : new int[0][],
                        4, true, backs.contains("east") ? new int[][]{{-svHalf, svHalf}} : new int[0][]);
            }
            List<int[]> flights = step <= 2 ? List.of(new int[]{tiers.get(tiers.size() - 1)[0], top, y}) : Platforms.tierFlights(tiers, y);
            for (int[] fl : flights) {
                int m = fl[0], ft = fl[1], fb = fl[2];
                if (!t(spec, "_no_front_door")) Arch.stairsDown(g, shHalf, hz + m + 1, ft, fb, true, 0, mat, run);
                for (String d : backs) {
                    boolean side = !d.equals("north");
                    Arch.stairsDown(g.sub(0, 0, d), side ? svHalf : shHalf, (side ? hx : hz) + m + 1, ft, fb, true, 0, mat, run);
                }
            }
        } else {
            String base = t(spec, "_base") ? (String) spec.get("_base") : ws(W, "base");
            for (int u = -hx - ring; u < hx + 1 + ring; u++)
                for (int v = -hz - back; v < hz + 1 + front; v++)
                    for (int yy = y + 1; yy < y + pod + 1; yy++) g.put(u, yy, v, base);
            if (ring != 0 && Dyn.truthy(W.get("base_stairs"))) {
                for (int u = -hx - ring; u < hx + 1 + ring; u++)
                    for (int v = -hz - back; v < hz + 1 + front; v++) {
                        boolean side = Math.abs(u) == hx + ring;
                        boolean end = v == hz + front || v == -hz - back;
                        if (!(side || end) || (side && end)) continue;
                        String facing = v == hz + front ? "north" : v == -hz - back ? "south" : u == hx + ring ? "west" : "east";
                        g.put(u, y + pod, v, B(ws(W, "base_stairs"), "facing", facing, "half", "top"));
                    }
            }
            String pblock = base;
            String psteps = stairsOf(pblock, ws(W, "steps"));
            String pslab = Arch.slabFor(psteps);
            List<Object[]> flights = new ArrayList<>();
            if (!t(spec, "_no_front_door")) flights.add(new Object[]{g, hz + front});
            for (String d : backs) flights.add(new Object[]{g.sub(0, 0, d), d.equals("north") ? hz + back : hx + ring});
            for (Object[] fe : flights) {
                Frame ff = (Frame) fe[0];
                int edge = (int) fe[1];
                for (Arch.Step st : Arch.flight(pod, Arch.stepRun(spec.get("_step_slope")))) {
                    for (int u : new int[]{-1, 0, 1}) {
                        ff.put(u, y + pod - st.dt(), edge + 1 + st.k(), Arch.stepBlock(st.kind(), psteps, pslab, "north"));
                        for (int yy = y + 1; yy < y + pod - st.dt(); yy++) ff.put(u, yy, edge + 1 + st.k(), pblock);
                    }
                }
            }
            top = y + pod;
        }
        for (Map.Entry<Long, String> e : pc.blocks.entrySet())
            if (!(keep && f.cv.blocks.containsKey(e.getKey()))) f.cv.blocks.put(e.getKey(), e.getValue());
        for (Map.Entry<Long, List<Canvas.Run>> e : pc.cols.entrySet()) f.cv.colsAt(e.getKey()).addAll(e.getValue());
        return new Object[]{top, pc};
    }

    /** bgen.walk_cut. */
    static Set<String> walkCut(Frame f, int hx, int hz, int yb) {
        Set<String> cut = new HashSet<>();
        Object[][] sides = {{"front", 0, 1}, {"back", 0, -1}, {"east", 1, 0}, {"west", -1, 0}};
        for (Object[] sd : sides) {
            String side = (String) sd[0];
            int du = (int) sd[1], dv = (int) sd[2];
            List<int[]> cells = new ArrayList<>();
            switch (side) {
                case "front" -> {
                    for (int a = -hx; a <= hx; a++) cells.add(new int[]{a, hz});
                }
                case "back" -> {
                    for (int a = -hx; a <= hx; a++) cells.add(new int[]{a, -hz});
                }
                case "east" -> {
                    for (int a = -hz; a <= hz; a++) cells.add(new int[]{hx, a});
                }
                default -> {
                    for (int a = -hz; a <= hz; a++) cells.add(new int[]{-hx, a});
                }
            }
            for (int[] c : cells) {
                boolean hit = false;
                for (int d = 1; d < 9; d++) {
                    int cu = c[0] + du * d, cv = c[1] + dv * d;
                    if (Bgen.passable(atWalk(f, cu, yb, cv))) break;
                    if (Bgen.passable(atWalk(f, cu, yb + 1, cv)) && Bgen.passable(atWalk(f, cu, yb + 2, cv))) {
                        hit = false;
                        break;
                    }
                    hit = true;
                }
                if (hit) {
                    cut.add(side);
                    break;
                }
            }
        }
        return cut;
    }

    private static String atWalk(Frame f, int u, int yy, int v) {
        int x = f.wx(u, v), z = f.wz(u, v);
        String st = f.cv.blocks.get(Canvas.key(x, yy, z));
        if (st == null) {
            List<Canvas.Run> rs = f.cv.cols.get(Canvas.ckey(x, z));
            if (rs != null) for (Canvas.Run r : rs) if (r.y0() <= yy && yy <= r.y1()) return r.s();
        }
        return st;
    }

    /** bgen.widen_podium. */
    static void widenPodium(Frame f, int hx, int hz, int y, int pod, int yb, Object[] plat, Canvas pc, Map<String, Object> spec, Map<String, Object> W, List<String> backs) {
        for (int it = 0; it < 3; it++) {
            Set<String> cut = walkCut(f, hx, hz, yb);
            if (cut.isEmpty()) return;
            String style = (String) plat[0];
            int ring = (int) plat[1], front = (int) plat[2], back = (int) plat[3];
            if (style.equals("plain")) {
                ring += cut.contains("west") || cut.contains("east") ? 1 : 0;
                plat = new Object[]{style, ring, Math.max(ring, front + (cut.contains("front") ? 1 : 0)), Math.max(ring, back + (cut.contains("back") ? 1 : 0))};
            } else plat = new Object[]{style, ring + 1, 0, 0};
            for (Map.Entry<Long, String> e : pc.blocks.entrySet())
                if (e.getValue().equals(f.cv.blocks.get(e.getKey()))) f.cv.blocks.remove(e.getKey());
            for (Map.Entry<Long, List<Canvas.Run>> e : pc.cols.entrySet())
                for (Canvas.Run r : e.getValue()) {
                    List<Canvas.Run> have = f.cv.cols.get(e.getKey());
                    if (have != null && have.contains(r)) have.remove(r);
                }
            pc = (Canvas) podium(f, hx, hz, y, pod, plat, spec, W, backs, true)[1];
        }
    }

    /** bgen.veranda: 前廊. */
    static void veranda(Frame f, PyRandom rng, int hx, int hz, int yb, int sh, Map<String, Object> W, boolean keepFloor) {
        int depth = hx < 7 ? 3 : 4;
        for (int u = -hx; u <= hx; u++)
            for (int v = hz + 1; v < hz + depth + 1; v++)
                if (!(keepFloor && f.get(u, yb, v) != null)) f.put(u, yb, v, ws(W, "floor"));
        int bay = hx <= 5 ? 3 : hx <= 9 ? 4 : 5;
        PySet<Integer> posts = new PySet<>();
        for (int u = -hx; u <= hx; u++) if (Math.abs(u) == hx || (Math.floorMod(u, bay) == 0 && Math.abs(u) > 2)) posts.add(u);
        int bt = Math.max(yb + sh - 1, yb + 4);
        for (int u : posts) for (int y = yb + 1; y < bt; y++) f.put(u, y, hz + depth, ws(W, "post"));
        for (int u = -hx; u <= hx; u++) {
            f.put(u, bt, hz + depth, ws(W, "beam"));
            if (Math.abs(u) > 2 && !posts.contains(u)) f.put(u, yb + 1, hz + depth, B(ws(W, "rail"), "east", true, "west", true));
        }
        H h = new H();
        for (int u = -hx - 1; u < hx + 2; u++)
            for (int v = hz + 1; v < hz + depth + 2; v++) h.put(u, v, bt + 1.8 - 0.5 * (v - hz - 1));
        Arch.rasterRoof(f, h, (u, v, val) -> bt, yb + 4);
    }

    // ------------------------------------------------------------------------------------------------ walls

    /** bgen.pick_bay: (part, rows) or null. */
    @SuppressWarnings("unchecked")
    static Object[] pickBay(PyRandom rng, String role, int width, int h, Map<String, Object> ctx, String face) {
        String key = role + "/" + width + "/" + h + "/" + face.equals("front");
        Map<String, Object> chosen = (Map<String, Object>) ctx.get("chosen");
        if (chosen.containsKey(key)) return (Object[]) chosen.get(key);
        if (Parts.BAYS.isEmpty()) Parts.reloadBays();
        Object style = ctx.get("style");
        if (Dyn.truthy(style)) {
            List<Object[]> cands = new ArrayList<>();
            for (Map<String, Object> e : Parts.BAYS.values()) {
                if (!role.equals(e.get("role")) || !style.equals(e.get("style"))) continue;
                if (Dyn.truthy(e.get("faces")) && !Dyn.list(e.get("faces")).contains(face)) continue;
                List<Parts.Row> rows = Parts.bayRows(e, width, h);
                if (rows != null) cands.add(new Object[]{e, rows});
            }
            if (!cands.isEmpty()) {
                Object[] res = cands.size() == 1 ? cands.get(0) : cands.get(choose(rng, cands));
                chosen.put(key, res);
                return res;
            }
        }
        List<Object[]> cands = new ArrayList<>();
        for (Map<String, Object> e : Parts.BAYS.values()) {
            if (!role.equals(e.get("role"))) continue;
            if (Dyn.truthy(e.get("tiers")) && !Dyn.list(e.get("tiers")).contains(ctx.get("tier"))) continue;
            if (Dyn.truthy(e.get("walls")) && !Dyn.list(e.get("walls")).contains(ctx.get("walls"))) continue;
            if (Dyn.truthy(e.get("faces")) && !Dyn.list(e.get("faces")).contains(face)) continue;
            List<Parts.Row> rows = Parts.bayRows(e, width, h);
            if (rows != null) cands.add(new Object[]{e, rows});
        }
        Object[] res;
        if (cands.isEmpty()) res = null;
        else if (cands.size() == 1) res = cands.get(0);
        else res = cands.get(choose(rng, cands));
        chosen.put(key, res);
        return res;
    }

    @SuppressWarnings("unchecked")
    private static int choose(PyRandom rng, List<Object[]> cands) {
        double[] w = new double[cands.size()];
        for (int i = 0; i < w.length; i++) w[i] = Arch.weight((Map<String, Object>) cands.get(i)[0]);
        return rng.choicesIndex(w, 1)[0];
    }

    interface At {
        int[] at(int a);
    }

    /** bgen.draw_walls. */
    @SuppressWarnings("unchecked")
    static void drawWalls(Frame f, PyRandom rng, Map<String, Object> W, int hx, int hz, int ys, int sh, int bay, int s, int n, Map<String, Object> spec,
                          int doorW, Map<String, Object> ctx) {
        int dw = doorW <= 1 ? 0 : 1;
        Set<IntPair> noWin = spec.get("_no_window_cells") == null ? Set.of() : (Set<IntPair>) spec.get("_no_window_cells");
        int h = sh - 1;
        String ch = spec.get("_caihua") != null && CAIHUA.containsKey((String) spec.get("_caihua")) ? (String) spec.get("_caihua") : null;
        Object[][] faces = {{"front", hx, (At) a -> new int[]{a, hz}, 0, 1}, {"back", hx, (At) a -> new int[]{a, -hz}, 0, -1},
                {"west", hz, (At) a -> new int[]{-hx, a}, -1, 0}, {"east", hz, (At) a -> new int[]{hx, a}, 1, 0}};
        for (Object[] fc : faces) {
            String face = (String) fc[0];
            int limit = (int) fc[1];
            At at = (At) fc[2];
            int ou = (int) fc[3], ov = (int) fc[4];
            boolean front = face.equals("front");
            String fb = f.get(0, ys, hz + 1);
            boolean balc = front && s > 0 && t(spec, "balcony") && !(fb == null || fb.equals(AIR));
            boolean hasDoor = front && (balc || (s == 0 && !t(spec, "_no_front_door")));
            if (face.equals("back") && s == 0 && (t(spec, "_back_door") || t(spec, "_through"))) hasDoor = true;
            if ((face.equals("west") || face.equals("east")) && s == 0 && t(spec, "_four")) hasDoor = true;
            final boolean hd = hasDoor;
            java.util.function.IntPredicate isPost = a -> {
                if (hd && Math.abs(a) <= dw) return false;
                return Math.abs(a) == limit || Math.floorMod(a, bay) == 0 || (hd && Math.abs(a) == dw + 1);
            };
            List<List<Integer>> segs = new ArrayList<>();
            List<Integer> cur = new ArrayList<>();
            for (int a = -limit; a <= limit; a++) {
                int[] uv = at.at(a);
                if (isPost.test(a)) {
                    for (int kk = 1; kk <= sh; kk++) f.put(uv[0], ys + kk, uv[1], ws(W, "post"));
                    if (!cur.isEmpty()) {
                        segs.add(cur);
                        cur = new ArrayList<>();
                    }
                } else {
                    f.put(uv[0], ys + sh, uv[1], ws(W, "beam"));
                    cur.add(a);
                }
            }
            if (!cur.isEmpty()) segs.add(cur);
            if (ch != null) paint(f, ch, segs, at, ys + sh, null);
            boolean alongU = face.equals("front") || face.equals("back");
            String outDir = ov > 0 ? "south" : ov < 0 ? "north" : ou > 0 ? "east" : "west";
            String rail = alongU ? B(ws(W, "rail"), "east", true, "west", true) : B(ws(W, "rail"), "north", true, "south", true);
            if (t(spec, "_open") || (t(spec, "_open_front") && face.equals("front"))) {
                for (List<Integer> seg : segs) {
                    boolean door = hasDoor;
                    for (int a : seg) if (Math.abs(a) > dw) door = false;
                    for (int a : seg) {
                        int[] uv = at.at(a);
                        for (int kk = 1; kk < sh; kk++) f.put(uv[0], ys + kk, uv[1], AIR);
                        if (!door) f.put(uv[0], ys + 1, uv[1], rail);
                    }
                }
                if (ch != null) paint(f, ch, segs, at, ys + sh - 1, null);
                continue;
            }
            for (List<Integer> seg : segs) {
                boolean door = hasDoor;
                for (int a : seg) if (Math.abs(a) > dw) door = false;
                if (door && s == 0 && t(spec, "_through") && face.equals("back")) {
                    for (int a : seg) {
                        int[] uv = at.at(a);
                        for (int kk = 1; kk < sh; kk++) f.put(uv[0], ys + kk, uv[1], AIR);
                    }
                    continue;
                }
                List<Integer> cols = (door || seg.get(0) >= 0) ? seg : reversed(seg);
                int m = seg.size();
                String role;
                if (door) role = "door";
                else if (front && s == 0 && t(spec, "shop")) role = "shop";
                else {
                    role = "plain";
                    if (m >= 2) {
                        int b0 = Math.floorDiv(Math.abs(cols.get(0)), bay) * bay;
                        boolean ok;
                        if (face.equals("back")) ok = Math.floorMod(Math.floorDiv(b0, bay), 2) == 0;
                        else if (face.equals("west") || face.equals("east")) ok = b0 == 0;
                        else ok = true;
                        if (ok) role = "window";
                    }
                }
                Object[] got = pickBay(rng, role, m, h, ctx, face);
                if (got != null && role.equals("window")) {
                    List<Parts.Row> rws = (List<Parts.Row>) got[1];
                    Set<Integer> wcols = new HashSet<>();
                    for (Parts.Row r : rws) for (int j = 0; j < r.wall().length(); j++) if (r.wall().charAt(j) == 'W') wcols.add(j);
                    boolean blocked = false;
                    for (int j : wcols) {
                        int[] uv = at.at(cols.get(j));
                        if (noWin.contains(IntPair.of(uv[0], uv[1]))) blocked = true;
                    }
                    if (blocked) got = null;
                }
                if (got == null && !role.equals("plain")) got = pickBay(rng, "plain", m, h, ctx, face);
                if (got == null) {
                    for (int a : seg) {
                        int[] uv = at.at(a);
                        for (int kk = 1; kk < sh; kk++) f.put(uv[0], ys + kk, uv[1], ws(W, "fill"));
                    }
                    continue;
                }
                Map<String, Object> part = (Map<String, Object>) got[0];
                List<Parts.Row> rws = (List<Parts.Row>) got[1];
                Map<String, Object> key = Dyn.truthy(part.get("key")) ? Dyn.map(part.get("key")) : Map.of();
                for (int j = 0; j < cols.size(); j++) {
                    int a = cols.get(j);
                    int[] uv = at.at(a);
                    int u = uv[0], v = uv[1];
                    int wtop = 0;
                    for (int kk = 1; kk <= rws.size(); kk++) if (rws.get(kk - 1).wall().charAt(j) == 'W') wtop = Math.max(wtop, kk);
                    boolean firstDoor = true;
                    for (int kk = 1; kk <= rws.size(); kk++) {
                        Parts.Row row = rws.get(kk - 1);
                        int y = ys + kk;
                        char c = row.wall().charAt(j);
                        String cs = String.valueOf(c);
                        switch (c) {
                            case 'F' -> f.put(u, y, v, ws(W, "fill"));
                            case 'b' -> f.put(u, y, v, ws(W, "base"));
                            case 'd' -> f.put(u, y, v, Dyn.truthy(W.get("dado")) ? ws(W, "dado") : ws(W, "fill"));
                            case 'B' -> f.put(u, y, v, ws(W, "beam"));
                            case 'P' -> f.put(u, y, v, ws(W, "post"));
                            case 'A' -> f.put(u, y, v, AIR);
                            case 'W' -> {
                                WINDOWS.add(new int[]{u, v, ys, kk, wtop});
                                String pane = W.containsKey("pane") ? ws(W, "pane") : "white_stained_glass_pane";
                                f.put(u, y, v, alongU ? B(pane, "east", true, "west", true) : B(pane, "north", true, "south", true));
                            }
                            case 'D' -> {
                                String door0 = Dyn.truthy(W.get("door")) ? ws(W, "door") : Bgen.DOOR_OF.getOrDefault(ws(W, "win"), "spruce_door");
                                String facing = switch (face) {
                                    case "front" -> "north";
                                    case "back" -> "south";
                                    case "west" -> "east";
                                    default -> "west";
                                };
                                f.put(u, y, v, B(door0, "facing", facing, "half", firstDoor ? "lower" : "upper", "hinge", a > 0 ? "right" : "left",
                                        "open", false, "powered", false));
                                firstDoor = false;
                            }
                            case 'S' -> f.put(u, y, v, ws(W, "win").endsWith("trapdoor") ? B(ws(W, "win"), "facing", outDir, "half", "bottom", "open", true) : AIR);
                            default -> {
                                if (key.containsKey(cs)) f.put(u, y, v, Blocks.rotateState((String) key.get(cs), outDir));
                            }
                        }
                        char o = row.out().charAt(j);
                        String os = String.valueOf(o);
                        if (o == 'h' && Dyn.truthy(W.get("trim_trapdoor")) && f.get(u + ou, y, v + ov) == null && c != 'D')
                            f.put(u + ou, y, v + ov, B(ws(W, "trim_trapdoor"), "facing", outDir, "half", "top", "open", false));
                        else if (o == 'v' && s == 0 && Dyn.truthy(W.get("trim_slab")) && f.get(u + ou, y - 1, v + ov) == null)
                            f.put(u + ou, y - 1, v + ov, B(ws(W, "trim_slab"), "type", "top"));
                        else if (key.containsKey(os) && f.get(u + ou, y, v + ov) == null && !(c == 'D' && String.valueOf(key.get(os)).contains("trapdoor")))
                            f.put(u + ou, y, v + ov, Blocks.rotateState((String) key.get(os), outDir));
                    }
                }
            }
            if (ch != null) paint(f, ch, segs, at, ys + sh - 1, new HashSet<>(List.of(ws(W, "fill"), ws(W, "beam"))));
        }
    }

    static List<Integer> reversed(List<Integer> l) {
        List<Integer> r = new ArrayList<>(l);
        java.util.Collections.reverse(r);
        return r;
    }

    private static void paint(Frame f, String ch, List<List<Integer>> segs, At at, int y, Set<String> keep) {
        for (int i = 0; i < segs.size(); i++) {
            List<Integer> seg = segs.get(i);
            int par = Math.floorMod((int) Math.abs(i - (segs.size() - 1) / 2.0), 2);
            for (int j = 0; j < seg.size(); j++) {
                int[] uv = at.at(seg.get(j));
                if (keep != null && !keep.contains(f.get(uv[0], y, uv[1]))) continue;
                String blk = Bgen.caihuaCell(ch, j, seg.size(), par);
                if (Env.present("minecraft:" + blk)) f.put(uv[0], y, uv[1], B(blk));
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ eaves

    /** bgen.eave_brackets. */
    static void eaveBrackets(Frame f, Map<String, Object> W, int hx, int hz, int topY0, int bay, int levels) {
        if (!Dyn.truthy(W.get("trim_stairs"))) return;
        int topY = topY0 - 1;
        for (int u = -hx; u <= hx; u++) {
            if (Math.floorMod(u, bay) == 0 || Math.abs(u) == hx) {
                bracketOne(f, W, u, hz + 1, 0, 1, "north", "south", topY, levels);
                bracketOne(f, W, u, -hz - 1, 0, -1, "south", "north", topY, levels);
            }
        }
        for (int v = -hz + 1; v < hz; v++) {
            if (Math.floorMod(v, bay) == 0) {
                bracketOne(f, W, hx + 1, v, 1, 0, "west", "east", topY, levels);
                bracketOne(f, W, -hx - 1, v, -1, 0, "east", "west", topY, levels);
            }
        }
    }

    private static void bracketOne(Frame f, Map<String, Object> W, int u, int v, int du, int dv, String facing, String side, int topY, int levels) {
        for (int kk : new int[]{1, 2}) {
            String s = f.get(u - du, topY - kk, v - dv);
            if (s != null && s.contains("_door")) return;
        }
        if (levels > 1) bracketRows(f, W, u, v, du, dv, topY, levels, facing);
        else if (f.get(u, topY, v) == null) {
            Map<String, String> slots = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : W.entrySet()) if (e.getValue() instanceof String sv) slots.put(e.getKey(), sv);
            if (!Arch.placeOrnament(f, "bracket", u, topY, v, 0, false, false, side, slots))
                f.put(u, topY, v, B(ws(W, "trim_stairs"), "facing", facing, "half", "top"));
        }
    }

    /** bgen._bracket_rows. */
    static void bracketRows(Frame f, Map<String, Object> W, int u, int v, int du, int dv, int yTop, int levels, String facing) {
        String wood = ws(W, "trim_stairs").replace("_stairs", "_planks");
        wood = Env.present(wood.contains(":") ? wood : "minecraft:" + wood) ? B(wood) : ws(W, "beam");
        for (int j = 1; j <= levels; j++) {
            int y = yTop - (levels - j);
            for (int o = 0; o < j; o++) {
                int cu = u + du * o, cv = v + dv * o;
                String c = f.get(cu, y, cv);
                if (!(c == null || c.equals(AIR))) continue;
                boolean any = false;
                for (int yy = y + 1; yy < y + levels + 2; yy++) {
                    String s = f.get(cu, yy, cv);
                    if (!(s == null || s.equals(AIR))) {
                        any = true;
                        break;
                    }
                }
                if (!any) break;
                f.put(cu, y, cv, o == j - 1 ? B(ws(W, "trim_stairs"), "facing", facing, "half", "top") : wood);
            }
        }
    }

    /** bgen.eave_fascia. */
    static void eaveFascia(Frame f, Map<String, Object> W, int hx, int hz, int topY, int reach, Integer minY) {
        if (!Dyn.truthy(W.get("trim_trapdoor"))) return;
        for (int u = -hx - reach; u < hx + reach + 1; u++)
            for (int v = -hz - reach; v < hz + reach + 1; v++) {
                if (Math.abs(u) <= hx && Math.abs(v) <= hz) continue;
                Integer lo = lowest(f, u, v, topY);
                if (lo == null) continue;
                int du = u > hx ? 1 : u < -hx ? -1 : 0;
                int dv = v > hz ? 1 : v < -hz ? -1 : 0;
                if (lowest(f, u + du, v + dv, topY) != null) continue;
                if (f.get(u, lo - 1, v) == null && (minY == null || lo - 1 >= minY)) {
                    String out = dv > 0 ? "south" : dv < 0 ? "north" : du > 0 ? "east" : "west";
                    f.put(u, lo - 1, v, B(ws(W, "trim_trapdoor"), "facing", out, "half", "top", "open", false));
                }
            }
    }

    private static Integer lowest(Frame f, int u, int v, int topY) {
        for (int yy = topY - 1; yy < topY + 12; yy++) {
            String st = f.get(u, yy, v);
            if (st != null && !st.isEmpty() && !st.equals(AIR)) return yy;
        }
        return null;
    }

    /** bgen.horsehead_walls. */
    static void horseheadWalls(Frame f, int hx, int hz, int topY, int ry, Map<String, Object> W, List<Integer> sides) {
        int steps = 3;
        for (int sgn : sides) {
            int u = sgn * (hx + 1);
            for (int v = -hz - 1; v < hz + 2; v++) {
                double tt = Math.abs(v) / (double) (hz + 1);
                int level = steps - (int) (tt * steps);
                int hgt = topY + 2 + level * Math.max(2, Math.floorDiv(ry - topY, steps));
                for (int y = topY - 1; y < hgt + 1; y++) f.put(u, y, v, B("white_concrete"));
                f.put(u, hgt + 1, v, B(roof("full")));
                f.put(u + 1, hgt + 1, v, stair(roof("tile"), "west"));
                f.put(u - 1, hgt + 1, v, stair(roof("tile"), "east"));
                f.put(u, hgt + 2, v, slab(roof("tile")));
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ decor

    /** bgen.decorate. */
    static void decorate(Frame f, PyRandom rng, Map<String, Object> spec, int hx, int hz, int yb, int n, int sh, Map<String, Object> W, String tier) {
        for (Object d : spec.get("decor") == null ? List.of() : Dyn.list(spec.get("decor"))) Decor.run((String) d, f, rng, hx, hz, yb, n, sh, W);
        if (!t(spec, "_wing") && !t(spec, "_no_extra_decor")) Decor.extras(f, rng, tier, hx, hz, yb, n, sh, W);
        if (rng.random() < 0.6) {
            for (int s : new int[]{-2, 2})
                if (f.get(s, yb + 1, hz + 1) == null)
                    Arch.putLamp(f, "door", s, yb, hz + 1, 1, Map.of("rail", Dyn.truthy(W.get("rail")) ? ws(W, "rail") : "spruce_fence"));
        }
        if (rng.random() < 0.5 && sh >= 5) {
            for (int u : new int[]{-1, 0, 1}) f.put(u, sh >= 6 ? yb + 5 : yb + sh - 1, hz + 1, Math.abs(u) == 1 ? B("dark_oak_planks") : B("gold_block"));
        }
    }
}
