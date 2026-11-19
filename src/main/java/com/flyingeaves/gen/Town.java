package com.flyingeaves.gen;

import com.flyingeaves.gen.py.Py;
import com.flyingeaves.gen.py.PyRandom;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleUnaryOperator;
import java.util.function.IntPredicate;

import static com.flyingeaves.gen.Siteplan.kw;
import static com.flyingeaves.gen.Siteplan.odd;
import static com.flyingeaves.gen.Siteplan.room;

/**
 * town.py: 凡人城镇 - a mortal town laid out by rule on the ground it stands on, written as one 单体建造 design (the
 * same rooms, keys and values as the Python writes, in the same order).
 */
@SuppressWarnings("unchecked")
public final class Town {

    // ------------------------------------------------------------------------------------------ tables

    record Fam(String cn, String[] walls, String[] themes, LinkedHashMap<String, Integer> roofs, String base, int[] street, int[] yard,
               double horse, String[] poorWalls, String[] poorThemes, String[] richWalls, String[] richThemes, String[] landmark) {
    }

    static LinkedHashMap<String, Integer> rf(Object... kv) {
        LinkedHashMap<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], (Integer) kv[i + 1]);
        return m;
    }

    static String[] S(String... s) {
        return s;
    }

    static int[] I(int... v) {
        return v;
    }

    static final Fam[] FAMILIES = {
            new Fam("江南", S("whitewall", "darkwood"), S("black", "grey"), rf("yingshan", 3, "xuanshan", 2, "xieshan", 1), "greybrick", I(0, 0, 1),
                    I(0, 3, 1), 0.35, S("wattle", "darkwood", "mud"), S("thatch", "thatch", "wood"), S("whitewall"), S("black"), null),
            new Fam("徽州", S("whitewall", "whitewall", "greybrick"), S("black"), rf("yingshan", 5, "xieshan", 1), "white", I(0, 1), I(3, 0), 0.8,
                    S("wattle", "mud", "whitewall"), S("thatch", "grey"), S("whitewall"), S("black"), null),
            new Fam("北方", S("greybrick", "redbrick"), S("grey", "grey", "black"), rf("yingshan", 3, "juanpeng", 2, "xuanshan", 1), "greybrick",
                    I(2, 0, 3), I(0, 3), 0.0, S("mud", "mud", "redbrick"), S("thatch", "mud"), S("greybrick"), S("grey", "black"), null),
            new Fam("山村", S("stone", "log", "wattle"), S("slate", "thatch", "wood"), rf("xuanshan", 3, "yingshan", 2), "rubble", I(1, 3),
                    I(1, 2, 0), 0.0, S("log", "wattle", "mud"), S("thatch"), S("stone", "darkwood"), S("slate", "grey"), null),
            new Fam("黄土", S("mud", "redbrick", "mud"), S("mud", "grey"), rf("yingshan", 3, "juanpeng", 2), "rammed", I(3, 2), I(0), 0.0,
                    S("mud"), S("mud", "thatch"), S("redbrick", "greybrick"), S("grey"), null),
            new Fam("川西", S("darkwood", "wattle", "whitewall"), S("grey", "black"), rf("xuanshan", 3, "xieshan", 2, "yingshan", 1), "rubble",
                    I(0, 1), I(1, 0, 2), 0.1, S("wattle", "darkwood", "log"), S("thatch", "wood"), S("whitewall", "darkwood"), S("black", "grey"), null),
            new Fam("富庶", S("redbrick", "darkwood", "greybrick"), S("grey", "black"), rf("xieshan", 3, "yingshan", 2, "xuanshan", 1), "greybrick",
                    I(0, 2), I(3, 2), 0.2, S("wattle", "mud", "darkwood"), S("thatch", "grey"), S("redbrick", "greybrick"), S("black", "grey"),
                    S("teal", "grey")),
    };
    static final String[] SHOPS = {"teahouse", "pharmacy", "general_store", "general_store", "smithy", "tofu", "cloth_shop", "pawnshop", "bookshop",
            "clinic", "coffin_shop", "gambling_den", "inn_hall"};
    static final String[] RARE_SHOPS = {"pill_shop", "talisman_shop", "material_shop"};
    static final String[] HOMES = {"main_room", "main_room", "bedroom", "kitchen", "study"};
    static final String[] POOR_HOMES = {"main_room", "main_room", "bedroom", "kitchen", "storeroom"};
    static final String[] WORKS = {"mill_room", "dyeing", "brewery", "weaving", "pottery", "granary", "storeroom"};
    static final Map<String, String> UPPER = Map.of("teahouse", "private_dining", "inn_hall", "guest_room", "gambling_den", "private_dining");
    static final List<String> DIRS = List.of("north", "east", "south", "west");
    static final boolean GROUP = true;
    static final Map<String, Integer> ORN = new HashMap<>();

    static {
        String[] k = {"ding", "sundial", "measure", "vat", "well", "stall", "bamboo", "hay", "tree", "veg", "woodpile", "lion", "flowers", "pond", "rack", "lamp"};
        for (int i = 0; i < k.length; i++) ORN.put(k[i], i);
    }

    static final class Cls {
        int[] L, W, lanes;
        double back, wall, river;
        int most;

        Cls(int[] L, int[] W, int[] lanes, double back, double wall, double river, int most) {
            this.L = L;
            this.W = W;
            this.lanes = lanes;
            this.back = back;
            this.wall = wall;
            this.river = river;
            this.most = most;
        }

        Cls copy() {
            return new Cls(L.clone(), W.clone(), lanes.clone(), back, wall, river, most);
        }
    }

    static final Map<String, Cls> CLASSES = Map.of(
            "village", new Cls(I(72, 100), I(3, 5, 5), I(0, 2), 0.0, 0.0, 0.35, 15),
            "town", new Cls(I(138, 170), I(5, 7, 7), I(2, 4), 0.6, 0.65, 0.4, 40),
            "big", new Cls(I(176, 200), I(7), I(4, 7), 1.0, 1.0, 0.4, 100));
    static final Map<String, LinkedHashMap<String, Integer>> LAYOUTS = Map.of(
            "village", rf("line", 4, "T", 2, "Y", 2, "cross", 1, "bank", 2),
            "town", rf("line", 2, "T", 3, "cross", 2, "Y", 2, "bank", 2),
            "big", rf("grid", 7, "T", 1, "bank", 1));
    static final Map<String, int[]> BASE = Map.of("village", I(95, 95), "town", I(165, 200), "big", I(270, 245));
    static final Map<String, int[]> PITCH = Map.of("village", I(48, 56), "town", I(42, 48), "big", I(38, 42));
    static final Map<String, Integer> KIND_ORDER = Map.of("main", 0, "cross", 1, "branch", 1, "bank", 1, "lane", 2, "back", 3);
    static final Map<String, Integer> LEAST = Map.of("village", 8, "town", 25, "big", 60);

    // ------------------------------------------------------------------------------------------ ground

    interface Ground {
        double h(int x, int z);
    }

    static Ground synth(PyRandom rng) {
        double relief = rng.uniform(3.0, 9.0);
        double[][] waves = new double[3][];
        for (int i = 0; i < 3; i++) {
            double a = rng.uniform(0, Math.PI);
            double c = StrictMath.cos(a), s = StrictMath.sin(a);
            waves[i] = new double[]{c, s, rng.uniform(70, 220), rng.uniform(0, 2 * Math.PI), rng.uniform(0.5, 1.0)};
        }
        double tot = Py.sum(new double[]{waves[0][4], waves[1][4], waves[2][4]});
        double tilt = rng.uniform(-0.025, 0.025);
        double lean = rng.uniform(-0.06, 0.06);
        return (x, z) -> {
            double[] t = new double[3];
            for (int i = 0; i < 3; i++) {
                double[] w = waves[i];
                t[i] = w[4] * StrictMath.sin(2 * Math.PI * ((double) x * w[0] + (double) z * w[1]) / w[2] + w[3]);
            }
            double v = Py.sum(t) / tot;
            return relief / 2 * v + tilt * z + lean * x;
        };
    }

    static Ground given(Object heights) {
        Map<String, Object> hm = (Map<String, Object>) heights;
        int step = hm.containsKey("step") ? Dyn.toInt(hm.get("step")) : 1;
        int R = Math.floorDiv(Dyn.toInt(hm.get("R")), step);
        int n = 2 * R + 1;
        List<Object> raw = (List<Object>) hm.get("h");
        List<Double> good = new ArrayList<>();
        for (Object v : raw) if (v != null && ((Number) v).doubleValue() > -9000) good.add(((Number) v).doubleValue());
        good.sort(null);
        double fill = good.isEmpty() ? 64 : good.get(good.size() / 2);
        double[] vals = new double[raw.size()];
        for (int i = 0; i < vals.length; i++) {
            Object v = raw.get(i);
            vals[i] = v != null && ((Number) v).doubleValue() > -9000 ? ((Number) v).doubleValue() : fill;
        }
        return (x, z) -> {
            int i = (int) Math.max(-R, Math.min(R, Py.round((double) x / step))) + R;
            int k = (int) Math.max(-R, Math.min(R, Py.round((double) z / step))) + R;
            return vals[k * n + i];
        };
    }

    // ------------------------------------------------------------------------------------------ buildings

    static int gi(Map<String, Object> r, String k) {
        return Dyn.toInt(r.get(k));
    }

    static int[] margins(Map<String, Object> r, int spare) {
        int st = r.containsKey("storeys") ? Dyn.toInt(r.get("storeys")) : 1;
        int eave = 2 + (st >= 2 ? 1 : 0) + (Dyn.truthy(r.get("double_eave")) ? 1 : 0);
        Object roof = r.get("roof");
        int side = Math.max(eave, "yingshan".equals(roof) || "xuanshan".equals(roof) ? 3 : 2) + (Dyn.truthy(r.get("horsehead")) ? 1 : 0);
        int pod = Dyn.iOr0(r, "podium");
        int steps = pod + 1 + ("xumizuo".equals(r.get("podium_style")) && pod != 0 ? 2 : 0);
        int front = Math.max(eave, steps) + (Dyn.truthy(r.get("veranda")) ? 4 : 0);
        int back = eave + (Dyn.truthy(r.get("back_veranda")) ? 4 : 0);
        if ("chengtai".equals(r.get("podium_style")) && pod != 0) {
            side = Math.max(side, 4);
            front = Math.max(eave, 4);
            back = Math.max(back, 4);
        }
        return new int[]{side + spare, front + spare, back + spare};
    }

    static int[] reachRect(Map<String, Object> r, int spare) {
        int[] m = margins(r, spare);
        int sm = m[0], fm = m[1], bm = m[2];
        String fr = r.containsKey("front") ? (String) r.get("front") : "south";
        int x0 = gi(r, "x0"), z0 = gi(r, "z0"), x1 = gi(r, "x1"), z1 = gi(r, "z1");
        return switch (fr) {
            case "south" -> new int[]{x0 - sm, z0 - bm, x1 + sm, z1 + fm};
            case "north" -> new int[]{x0 - sm, z0 - fm, x1 + sm, z1 + bm};
            case "east" -> new int[]{x0 - bm, z0 - sm, x1 + fm, z1 + sm};
            default -> new int[]{x0 - fm, z0 - sm, x1 + bm, z1 + sm};
        };
    }

    static int frontGap(Map<String, Object> r, int hw) {
        int f = margins(r, 0)[1];
        int pod = Dyn.iOr0(r, "podium");
        int steps = pod + 1 + ("xumizuo".equals(r.get("podium_style")) && pod != 0 ? 2 : 0) + (Dyn.truthy(r.get("veranda")) ? 4 : 0);
        return Math.max(f - hw, steps + 1);
    }

    static boolean hits(int[] a, int[] b) {
        return a[0] <= b[2] && b[0] <= a[2] && a[1] <= b[3] && b[1] <= a[3];
    }

    static boolean wallToWall(Map<String, Object> a, Map<String, Object> b) {
        int gx = Math.max(gi(a, "x0"), gi(b, "x0")) - Math.min(gi(a, "x1"), gi(b, "x1"));
        int gz = Math.max(gi(a, "z0"), gi(b, "z0")) - Math.min(gi(a, "z1"), gi(b, "z1"));
        return gx <= 1 && gz <= 1;
    }

    static int[] resolve(List<Map<String, Object>> rooms) {
        int shrunk = 0, dropped = 0;
        for (int it = 0; it < 4; it++) {
            List<Map<String, Object>> bl = new ArrayList<>();
            for (Map<String, Object> r : rooms) if (!Dyn.truthy(r.get("special"))) bl.add(r);
            List<int[]> rr = new ArrayList<>();
            for (Map<String, Object> r : bl) rr.add(reachRect(r, 0));
            boolean changed = false;
            for (int i = 0; i < bl.size(); i++) {
                Map<String, Object> a = bl.get(i);
                for (int j = i + 1; j < bl.size(); j++) {
                    Map<String, Object> b = bl.get(j);
                    if (!hits(rr.get(i), rr.get(j)) || !rooms.contains(a) || !rooms.contains(b) || wallToWall(a, b)) continue;
                    if (!hits(reachRect(a, 0), reachRect(b, 0))) continue;
                    long[] ka = keep(a), kb = keep(b);
                    boolean aSmall = ka[0] < kb[0] || ka[0] == kb[0] && ka[1] <= kb[1];
                    Map<String, Object> small = aSmall ? a : b, big = aSmall ? b : a;
                    int[] rs = reachRect(small, 0), rbg = reachRect(big, 0);
                    String[] sides = {"x0", "x1", "z0", "z1"};
                    int[] need = {rbg[2] - rs[0] + 1, rs[2] - rbg[0] + 1, rbg[3] - rs[1] + 1, rs[3] - rbg[1] + 1};
                    Integer[] order = {0, 1, 2, 3};
                    Arrays.sort(order, Comparator.comparingInt(q -> need[q]));
                    boolean done = false;
                    for (int q : order) {
                        String side = sides[q];
                        int n = need[q] + Math.floorMod(need[q], 2);
                        String lo = side.charAt(0) == 'x' ? "x0" : "z0", hi = side.charAt(0) == 'x' ? "x1" : "z1";
                        if (n > 0 && gi(small, hi) - gi(small, lo) + 1 - n >= 5 && !Dyn.truthy(small.get("_gate"))) {
                            small.put(side, gi(small, side) + (side.endsWith("0") ? n : -n));
                            shrunk++;
                            done = true;
                            break;
                        }
                    }
                    if (!done) {
                        rooms.remove(small);
                        dropped++;
                    }
                    changed = true;
                }
            }
            if (!changed) break;
        }
        return new int[]{shrunk, dropped};
    }

    static long[] keep(Map<String, Object> r) {
        long rank = r.containsKey("_rank") ? Dyn.toInt(r.get("_rank")) : 1;
        return new long[]{rank, (long) (gi(r, "x1") - gi(r, "x0")) * (gi(r, "z1") - gi(r, "z0"))};
    }

    static String turn(String d, int k) {
        return DIRS.get(Math.floorMod(DIRS.indexOf(d) + k, 4));
    }

    static int[] rotRect(int[] r, int k) {
        int[][] pts = {{r[0], r[1]}, {r[2], r[3]}};
        for (int i = 0; i < Math.floorMod(k, 4); i++)
            for (int[] p : pts) {
                int x = p[0], z = p[1];
                p[0] = -z;
                p[1] = x;
            }
        return new int[]{Math.min(pts[0][0], pts[1][0]), Math.min(pts[0][1], pts[1][1]), Math.max(pts[0][0], pts[1][0]), Math.max(pts[0][1], pts[1][1])};
    }

    // ------------------------------------------------------------------------------------------ streets

    /** A street piece (the Python dict: a0, a1, c, y, and fixed / bridge / ry / var where set). */
    static final class P {
        int a0, a1, c, y;
        boolean fixed;
        Boolean bridge;
        int ry, var;

        P(int a0, int a1, int c, int y) {
            this.a0 = a0;
            this.a1 = a1;
            this.c = c;
            this.y = y;
        }

        P copy() {
            P p = new P(a0, a1, c, y);
            p.fixed = fixed;
            p.bridge = bridge;
            p.ry = ry;
            p.var = var;
            return p;
        }

        boolean br() {
            return bridge != null && bridge;
        }
    }

    static final class Street {
        String axis;
        int lo, hi, w, hw, step = 1;
        DoubleUnaryOperator cfun;
        String kind;
        List<P> pieces;
        Street parent;
        int side, aj;

        Street() {
        }

        Street(String axis, int lo, int hi, DoubleUnaryOperator cfun, int w, String kind, List<int[]> fixed, List<int[]> holes, int minlen, int maxlen, int shift) {
            this.axis = axis;
            this.lo = lo;
            this.hi = hi;
            this.cfun = cfun;
            this.w = w;
            this.hw = Math.floorDiv(w, 2);
            this.kind = kind;
            this.pieces = cut(fixed, holes, minlen, maxlen, shift);
        }

        Street(String axis, int lo, int hi, DoubleUnaryOperator cfun, int w, String kind, List<int[]> fixed, List<int[]> holes, int minlen, int maxlen) {
            this(axis, lo, hi, cfun, w, kind, fixed, holes, minlen, maxlen, 1);
        }

        Street copyAttrs() {
            Street s = new Street();
            s.axis = axis;
            s.lo = lo;
            s.hi = hi;
            s.w = w;
            s.hw = hw;
            s.step = step;
            s.cfun = cfun;
            s.kind = kind;
            s.pieces = pieces;
            s.parent = parent;
            s.side = side;
            s.aj = aj;
            return s;
        }

        int target(double t) {
            return (int) Py.round(cfun.applyAsDouble(t));
        }

        List<P> cut(List<int[]> fixed, List<int[]> holes, int minlen, int maxlen, int shift) {
            List<int[]> marks = new ArrayList<>();
            for (int[] ab : fixed) if (ab[1] >= lo && ab[0] <= hi) marks.add(new int[]{Math.max(lo, ab[0]), Math.min(hi, ab[1]), 0});
            for (int[] ab : holes) if (ab[1] >= lo && ab[0] <= hi) marks.add(new int[]{Math.max(lo, ab[0]), Math.min(hi, ab[1]), 1});
            marks.sort(Siteplan.LEX);
            List<int[]> clean = new ArrayList<>();
            for (int[] m : marks) {
                if (!clean.isEmpty() && m[0] <= clean.get(clean.size() - 1)[1]) {
                    int[] last = clean.get(clean.size() - 1);
                    if (m[1] <= last[1]) continue;
                    m = new int[]{last[1] + 1, m[1], m[2]};
                }
                clean.add(m);
            }
            clean.add(new int[]{hi + 1, hi + 1, 0});
            List<P> out = new ArrayList<>();
            int a = lo;
            Integer prev = null;
            for (int[] m : clean) {
                int s0 = m[0], s1 = m[1];
                boolean hole = m[2] != 0;
                while (a <= s0 - 1) {
                    int c = target(a + 1);
                    if (prev != null) c = Math.max(prev - shift, Math.min(prev + shift, c));
                    int b = Math.min(s0 - 1, a + minlen - 1);
                    while (b + 1 <= s0 - 1 && b - a + 1 < maxlen && target(b + 1) == c) b++;
                    if (0 < s0 - 1 - b && s0 - 1 - b < minlen) b = s0 - 1;
                    out.add(new P(a, b, c, 0));
                    prev = c;
                    a = b + 1;
                }
                if (s0 > hi) break;
                int c = target((s0 + s1) / 2.0);
                if (prev != null) c = Math.max(prev - shift, Math.min(prev + shift, c));
                if (!hole) {
                    P p = new P(s0, s1, c, 0);
                    p.fixed = true;
                    out.add(p);
                }
                prev = c;
                a = s1 + 1;
            }
            return out;
        }

        int[] rect(P p, int extra) {
            int h = hw + extra;
            if (axis.equals("z")) return new int[]{p.c - h, p.a0, p.c + h, p.a1};
            return new int[]{p.a0, p.c - h, p.a1, p.c + h};
        }

        int[] rect(P p) {
            return rect(p, 0);
        }

        List<P> span(int a0, int a1) {
            List<P> o = new ArrayList<>();
            for (P p : pieces) if (p.a1 >= a0 && p.a0 <= a1) o.add(p);
            return o;
        }

        P pieceAt(int a) {
            for (P p : pieces) if (p.a0 <= a && a <= p.a1) return p;
            P best = null;
            int bd = Integer.MAX_VALUE;
            for (P p : pieces) {
                int d = Math.min(Math.abs(p.a0 - a), Math.abs(p.a1 - a));
                if (d < bd) {
                    bd = d;
                    best = p;
                }
            }
            if (best == null) throw new IllegalArgumentException("min() arg is an empty sequence");
            return best;
        }

        int[] xz(int a, int c) {
            return axis.equals("z") ? new int[]{c, a} : new int[]{a, c};
        }
    }

    static Street field() {
        Street s = new Street();
        s.axis = "x";
        s.hw = 0;
        s.kind = "field";
        s.pieces = new ArrayList<>();
        return s;
    }

    static final class Side {
        final Street st;
        final int side;
        final String facing;

        Side(Street st, int side) {
            this.st = st;
            this.side = side;
            this.facing = st.axis.equals("x") ? (side < 0 ? "south" : "north") : (side < 0 ? "east" : "west");
        }

        int[] lines(int u0, int u1) {
            List<P> ss = st.span(u0, u1);
            if (ss.isEmpty()) ss = List.of(st.pieceAt(u0));
            int hw = st.hw;
            int mn = Integer.MAX_VALUE, mx = Integer.MIN_VALUE;
            for (P p : ss) {
                mn = Math.min(mn, p.c);
                mx = Math.max(mx, p.c);
            }
            if (side < 0) return new int[]{mn - hw - 1, mx - hw - 1};
            return new int[]{mx + hw + 1, mn + hw + 1};
        }

        int[] rect(int u0, int u1, int F, int d0, int d1) {
            int a = F + side * d0, b = F + side * d1;
            int lo = Math.min(a, b), hi = Math.max(a, b);
            if (st.axis.equals("x")) return new int[]{Math.min(u0, u1), lo, Math.max(u0, u1), hi};
            return new int[]{lo, Math.min(u0, u1), hi, Math.max(u0, u1)};
        }

        int[] nearRect(int u0, int u1, int near, int F, int d1) {
            int a = near, b = F + side * d1;
            int lo = Math.min(a, b), hi = Math.max(a, b);
            if (st.axis.equals("x")) return new int[]{u0, lo, u1, hi};
            return new int[]{lo, u0, hi, u1};
        }

        int y(int u0, int u1) {
            return st.pieceAt(Math.floorDiv(u0 + u1, 2)).y;
        }

        boolean levelOk(int u0, int u1, int y) {
            for (P p : st.span(u0, u1)) if (Math.abs(p.y - y) > 1) return false;
            return true;
        }

        int[] xz(int u, int F, int d) {
            int a = F + side * d;
            return st.axis.equals("x") ? new int[]{u, a} : new int[]{a, u};
        }

        String[] ends() {
            return st.axis.equals("x") ? new String[]{"east", "west"} : new String[]{"south", "north"};
        }
    }

    static long ck(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    static int cx(long k) {
        return (int) (k >> 32);
    }

    static int cz(long k) {
        return (int) k;
    }

    static final Comparator<Long> CELL = (a, b) -> {
        int c = Integer.compare(cx(a), cx(b));
        return c != 0 ? c : Integer.compare(cz(a), cz(b));
    };

    static final class Yard {
        Side s;
        int F, u0, u1, d0, d1, y;
        double w;
        boolean garden, rich, few;
    }

    record End(String name, Street st, int i) {
    }

    // ------------------------------------------------------------------------------------------ the town

    final long seed;
    final PyRandom rng;
    final int[] box;
    final String size;
    Cls C;
    final String layout;
    final boolean walled;
    final Fam fam;
    // mat
    String mWall, mTheme, mBase, mBay;
    String[] mWalls;
    int mStreet, mYard, mPodium, mSh, mLane;
    double mTall, mVeranda;
    boolean mRare;
    final boolean anchored;
    final Ground H;
    List<Map<String, Object>> rooms = new ArrayList<>();
    final List<Map<String, Object>> buildings = new ArrayList<>();
    final HashMap<Long, String> claim = new HashMap<>();
    final List<Street> streets = new ArrayList<>();
    final List<Street> rivers = new ArrayList<>();
    Map<Integer, Integer> riverFix = new LinkedHashMap<>();
    final List<Runnable> later = new ArrayList<>();
    int[] centre = {0, 0};
    double radius = 80;
    final List<Yard> yards = new ArrayList<>();
    int W = 5;
    boolean gridded;
    int dmax;
    int k = 2;
    Integer G = null, outer = null;
    int xr = 0, pad = 0;
    int least;
    Street main;
    Integer zr;
    boolean zrSet;
    int riverPc;
    int[] wallRect;
    List<int[]> bands;
    List<Map<String, Object>> gates;
    List<End> ends = new ArrayList<>();
    Object[] head;           // (cross street, 0)
    Map<String, Object> fork; // rect, y
    int[] forkRect;
    int forkY;
    List<Object[]> gridSites;  // (kind, Side)
    int[] crossing;
    List<Street> arms, branches;
    List<Integer> gridZ;

    Town(long seed, Object heights, String size, String layout, int attempt, int[] box) {
        this.seed = seed;
        rng = PyRandom.ofString(attempt == 0 ? "town/" + seed : "town/" + seed + "/" + attempt);
        this.box = box;
        if (box != null && size == null) {
            double side = Math.sqrt((double) box[0] * box[1]);
            size = side < 120 ? "village" : side < 220 ? "town" : "big";
        }
        this.size = size != null ? size : new String[]{"village", "town", "big"}[rng.choicesIndex(new long[]{30, 45, 25}, 1)[0]];
        C = CLASSES.get(this.size).copy();
        LinkedHashMap<String, Integer> lay = LAYOUTS.get(this.size);
        if (layout != null) this.layout = layout;
        else {
            List<String> keys = new ArrayList<>(lay.keySet());
            long[] wts = new long[keys.size()];
            for (int i = 0; i < wts.length; i++) wts[i] = lay.get(keys.get(i));
            this.layout = keys.get(rng.choicesIndex(wts, 1)[0]);
        }
        walled = rng.random() < C.wall;
        fam = FAMILIES[rng.choiceIndex(FAMILIES.length)];
        List<String> bays = Siteplan.bayStyles();
        mWall = fam.walls[0];
        mWalls = fam.walls;
        mTheme = rng.choice(fam.themes);
        mBase = rng.random() < 0.8 ? fam.base : rng.choice("greybrick", "rubble", "auto");
        mStreet = rng.choice(fam.street);
        mBay = rng.choice(bays);
        mTall = rng.uniform(0.25, 0.7);
        mVeranda = rng.uniform(0.0, 0.4);
        mPodium = rng.choice(I(0, 0, 1));
        mSh = rng.choice(I(0, 0, 5, 6));
        mRare = rng.random() < 0.3;
        int[][] clash = {{0, 2}, {1, 2}, {2, 3}, {3, 0}};
        List<Integer> yardsL = new ArrayList<>();
        for (int v : fam.yard) {
            boolean bad = false;
            for (int[] c : clash) if (c[0] == mStreet && c[1] == v) bad = true;
            if (!bad) yardsL.add(v);
        }
        if (yardsL.isEmpty()) yardsL.add(1);
        mYard = rng.choice(yardsL);
        if (rng.random() < 0.2) mTheme = rng.choice("grey", "black", "slate", "wood", "thatch", "mud");
        mLane = (mStreet == 0 || mStreet == 2) && rng.random() < 0.4 ? 3 : mStreet;
        if (heights == null) {
            anchored = false;
            H = synth(rng);
        } else {
            anchored = true;
            H = given(heights);
        }
        gridded = this.size.equals("big");
        dmax = Map.of("village", 64, "town", 44, "big", 28).get(this.size);
        least = LEAST.get(this.size);
        if (box != null) fitBox(box[0], box[1]);
    }

    void fitBox(int bw, int bd) {
        Cls C = this.C.copy();
        pad = walled ? 17 : 4;
        int BW = BASE.get(size)[0], BD = BASE.get(size)[1];
        gridded = size.equals("big") || bw > BW * 1.2 || bd > BD * 1.4;
        int L = odd(Math.max(41, bd - 2 * pad - 12 - (layout.equals("T") ? 50 : layout.equals("bank") ? 24 : 0)));
        double Lb = (C.L[0] + C.L[1]) / 2.0;
        C.L = I(L, L);
        double area = (double) bw * bd / (double) (BW * BD);
        C.most = Math.max(C.most, (int) Py.intOf(C.most * area * 3));
        least = (int) Py.intOf(LEAST.get(size) * Math.min(1.0, area * 0.7));
        xr = Math.floorDiv(bw, 2) - pad;
        if (gridded) {
            G = rng.randint(PITCH.get(size)[0], PITCH.get(size)[1]);
            k = Math.max(1, (int) Py.intOf(Py.floorDiv(bw / 2.0 - pad - 22, G)));
            outer = (int) Py.intOf(bw / 2.0 - pad - k * G - 6);
            dmax = 28;
        } else {
            int[] lanes = new int[C.lanes.length];
            for (int i = 0; i < lanes.length; i++) lanes[i] = (int) Math.max(0, Py.round(C.lanes[i] * L / Lb));
            C.lanes = lanes;
            dmax = Math.max(14, Math.min(dmax, (int) Py.intOf(bw / 2.0 - pad - 6)));
            if (bw < BW * 0.8) C.back = 0.0;
        }
        this.C = C;
    }

    List<Integer> offs(int G) {
        List<Integer> o = new ArrayList<>();
        for (int j = k; j > 0; j--) o.add(-G * j);
        for (int j = 1; j <= k; j++) o.add(G * j);
        return o;
    }

    List<Integer> crossZs(int lo, int hi, int G, Integer zc0, int edge) {
        int zc = zc0 == null ? Math.floorDiv(lo + hi, 2) : zc0;
        int K = box == null ? 2 : Math.floorDiv(hi - lo, G) + 1;
        List<Integer> o = new ArrayList<>();
        for (int j = -K; j <= K; j++) if (j != 0 && lo + edge <= zc + j * G && zc + j * G <= hi - edge) o.add(zc + j * G);
        return o;
    }

    List<Integer> crossZs(int lo, int hi, int G) {
        return crossZs(lo, hi, G, null, 14);
    }

    static boolean jointed(Street st, int a) {
        for (P p : st.pieces) if (p.fixed && p.a0 <= a && a <= p.a1) return true;
        return false;
    }

    // -------------------------------------------------------------------------------------------- claims

    boolean free(int[] r) {
        for (int x = r[0]; x <= r[2]; x++) for (int z = r[1]; z <= r[3]; z++) if (claim.containsKey(ck(x, z))) return false;
        return true;
    }

    static List<Long> cells(int[] r) {
        List<Long> o = new ArrayList<>();
        for (int x = r[0]; x <= r[2]; x++) for (int z = r[1]; z <= r[3]; z++) o.add(ck(x, z));
        return o;
    }

    void take(int[] r, String tag) {
        for (int x = r[0]; x <= r[2]; x++) for (int z = r[1]; z <= r[3]; z++) claim.putIfAbsent(ck(x, z), tag);
    }

    void take(int[] r) {
        take(r, "plot");
    }

    boolean claimed(int[] xz) {
        return claim.containsKey(ck(xz[0], xz[1]));
    }

    int depthFree(Side s, int u0, int u1, int F, int dmax) {
        for (int d = 0; d <= dmax; d++) for (int u = u0; u <= u1; u++) if (claimed(s.xz(u, F, d))) return d - 1;
        return dmax;
    }

    int runFree(Side s, int u0, int umax, int rows) {
        int u = u0;
        while (u <= umax) {
            int F = s.lines(u, u)[0];
            boolean any = false;
            for (int d = 0; d < rows; d++) if (claimed(s.xz(u, F, d))) {
                any = true;
                break;
            }
            if (any) break;
            u++;
        }
        return u - 1;
    }

    int runFree(Side s, int u0, int umax) {
        return runFree(s, u0, umax, 3);
    }

    // -------------------------------------------------------------------------------------------- districts

    double wealth(int[] xz) {
        int x = xz[0], z = xz[1];
        double d = com.flyingeaves.gen.py.UcrtMath.hypotInt(x - centre[0], z - centre[1]) / Math.max(30.0, radius);
        long m = Math.floorMod(Math.floorMod((long) x * 7349 + (long) z * 9157, 100L) + Math.floorMod(seed, 100L) * 31, 100L);
        double n = m / 100.0 - 0.5;
        return Math.max(0.0, Math.min(1.0, 1.15 - 1.1 * d + 0.25 * n));
    }

    static String ward(double w) {
        return w > 0.66 ? "rich" : w < 0.3 ? "poor" : "mid";
    }

    String pickRoof(boolean big) {
        LinkedHashMap<String, Integer> roofs = fam.roofs;
        if (big && rng.random() < 0.6)
            return roofs.containsKey("xieshan") || rng.random() < 0.5 ? rng.choice("xieshan", "xieshan", "wudian") : "xieshan";
        List<String> keys = new ArrayList<>(roofs.keySet());
        long[] w = new long[keys.size()];
        for (int i = 0; i < w.length; i++) w[i] = roofs.get(keys.get(i));
        return keys.get(rng.choicesIndex(w, 1)[0]);
    }

    Map<String, Object> building(String use, int storeys, String roof, String upper, double wealth, Map<String, Object> kw) {
        String ward = ward(wealth);
        if (roof == null) roof = pickRoof(false);
        String walls = rng.random() < 0.7 ? mWall : rng.choice(mWalls);
        String theme = mTheme;
        int podium = mPodium;
        if (ward.equals("poor")) {
            walls = rng.choice(fam.poorWalls);
            theme = rng.choice(fam.poorThemes);
            podium = 0;
            if (roof.equals("xieshan") || roof.equals("wudian")) roof = "xuanshan";
        } else if (ward.equals("rich") && rng.random() < 0.7) {
            walls = rng.choice(fam.richWalls);
            theme = rng.choice(fam.richThemes);
        }
        boolean horse = roof.equals("yingshan") && (walls.equals("whitewall") || walls.equals("greybrick")) && rng.random() < fam.horse
                && !ward.equals("poor") && !use.equals("kitchen");
        if (upper == null) upper = UPPER.get(use);
        if (upper == null) upper = rng.choice("bedroom", "bedroom", "storeroom", "study");
        if (walls.equals("stone") && Tables.SHOP_USES.contains(use)) {
            String nw = "log";
            for (String w : mWalls) if (!w.equals("stone")) {
                nw = w;
                break;
            }
            walls = nw;
        }
        List<Object> uses = new ArrayList<>();
        uses.add(use);
        for (int i = 0; i < 7; i++) uses.add(upper);
        Map<String, Object> d = kw("storeys", storeys, "roof", roof, "walls", walls, "theme", theme, "bay_style", mBay, "base", mBase, "sh", mSh,
                "podium", podium, "podium_style", "plain", "open", "closed", "uses", uses, "horsehead", horse, "overhang", 1,
                "pitch", rng.choice(I(0, 0, 2, 3)), "lift", roof.equals("xieshan") || roof.equals("wudian") ? rng.choice(I(0, 0, 1, 2)) : 0,
                "veranda", false, "back_veranda", false);
        d.putAll(kw);
        if (ward.equals("poor")) {
            d.put("storeys", 1);
            d.put("veranda", false);
            d.put("horsehead", false);
        }
        return d;
    }

    Map<String, Object> building(String use, Object... a) {
        int storeys = 1;
        String roof = null, upper = null;
        double wealth = 0.5;
        Map<String, Object> kw = new LinkedHashMap<>();
        for (int i = 0; i < a.length; i += 2) {
            String key = (String) a[i];
            Object v = a[i + 1];
            switch (key) {
                case "storeys" -> storeys = (int) v;
                case "roof" -> roof = (String) v;
                case "upper" -> upper = (String) v;
                case "wealth" -> wealth = ((Number) v).doubleValue();
                default -> kw.put(key, v);
            }
        }
        return building(use, storeys, roof, upper, wealth, kw);
    }

    Map<String, Object> place(Map<String, Object> d, int[] rect, int y, String front, int rank) {
        Map<String, Object> r = room(rect[0], rect[1], rect[2], rect[3], rng, kw("front", front, "y", y, "_rank", rank, d));
        rooms.add(r);
        buildings.add(r);
        return r;
    }

    Map<String, Object> place(Map<String, Object> d, int[] rect, int y, String front) {
        return place(d, rect, y, front, 2);
    }

    Map<String, Object> ground(String kind, int[] rect, int y, int variant, Object... kwa) {
        Map<String, Object> kw = kw(kwa);
        Object walls = kw.containsKey("walls") ? kw.remove("walls") : mWall;
        Object theme = kw.containsKey("theme") ? kw.remove("theme") : mTheme;
        Object base = kw.containsKey("base") ? kw.remove("base") : mBase;
        Object front = kw.containsKey("front") ? kw.remove("front") : "south";
        Map<String, Object> r = room(Math.min(rect[0], rect[2]), Math.min(rect[1], rect[3]), Math.max(rect[0], rect[2]), Math.max(rect[1], rect[3]), rng,
                kw("special", kind, "y", y, "variant", variant, "walls", walls, "theme", theme, "base", base, "front", front, kw));
        rooms.add(r);
        return r;
    }

    Map<String, Object> ornament(String kind, int cx, int cz, int y, int half, String front) {
        return ground("ornament", I(cx - half, cz - half, cx + half, cz + half), y, ORN.get(kind), "front", front);
    }

    Map<String, Object> terrace(Side s, int u0, int u1, int F, int near, Integer d0, int d1, int y, Integer variant) {
        int[] rect = d0 == null ? s.nearRect(u0, u1, near, F, d1) : s.rect(u0, u1, F, d0, d1);
        return ground("terrace", rect, y, variant == null ? mYard : variant);
    }

    // -------------------------------------------------------------------------------------------- streets

    double[] targets(Street st) {
        int n = st.pieces.size();
        double[] raw = new double[n];
        for (int i = 0; i < n; i++) {
            P p = st.pieces.get(i);
            List<Double> vals = new ArrayList<>();
            for (int a = p.a0 - 2; a < p.a1 + 3; a += 2)
                for (int c = p.c - st.hw - 3; c < p.c + st.hw + 4; c += 3) {
                    int[] xz = st.xz(a, c);
                    vals.add(H.h(xz[0], xz[1]));
                }
            double[] v = new double[vals.size()];
            for (int j = 0; j < v.length; j++) v[j] = vals.get(j);
            raw[i] = Py.sum(v) / v.length;
        }
        double[] t = new double[n];
        for (int i = 0; i < n; i++) {
            int a = Math.max(0, i - 1), b = Math.min(n, i + 2);
            t[i] = Py.sum(raw, a, b) / (b - a);
        }
        return t;
    }

    Street grade(Street st, Map<Integer, Integer> fixed, int[][] bounds) {
        if (fixed == null) fixed = new LinkedHashMap<>();
        int n = st.pieces.size();
        double[] t = targets(st);
        boolean steep = fixed.size() <= 1 && !st.kind.equals("river");
        if (steep) for (P p : st.pieces) if (p.a1 - p.a0 < 2) {
            steep = false;
            break;
        }
        int s = 1;
        List<Integer> ks = new ArrayList<>(fixed.keySet());
        ks.sort(null);
        for (int q = 0; q + 1 < ks.size(); q++) {
            int i = ks.get(q), j = ks.get(q + 1);
            s = Math.max(s, (int) Math.ceil((double) Math.abs(fixed.get(j) - fixed.get(i)) / Math.max(1, j - i)));
        }
        st.step = s;
        int[] loB = new int[n], hiB = new int[n];
        Arrays.fill(loB, -(1 << 20));
        Arrays.fill(hiB, 1 << 20);
        int sb = steep ? 2 : s;
        for (Map.Entry<Integer, Integer> e : fixed.entrySet()) {
            int i = e.getKey(), y = e.getValue();
            for (int j = 0; j < n; j++) {
                loB[j] = Math.max(loB[j], y - Math.abs(j - i) * sb);
                hiB[j] = Math.min(hiB[j], y + Math.abs(j - i) * sb);
            }
        }
        int[] ys = new int[n];
        for (int i = 0; i < n; i++) {
            long y;
            if (fixed.containsKey(i)) y = fixed.get(i);
            else if (i == 0) y = Py.round(t[i]);
            else {
                int prev = ys[i - 1];
                if (s == 1 && Math.abs(t[i] - prev) > 2.5 && st.w >= 3 && steep) y = prev + (t[i] > prev ? 2 : -2);
                else if (s == 1) y = prev + (t[i] > prev + 0.75 ? 1 : t[i] < prev - 0.75 ? -1 : 0);
                else y = prev + Math.max(-s, Math.min(s, Py.round(t[i] - prev)));
            }
            y = Math.max(loB[i], Math.min(hiB[i], y));
            if (i > 0) {
                int m = Math.max(s, steep ? 2 : 1);
                y = Math.max(ys[i - 1] - m, Math.min(ys[i - 1] + m, y));
            }
            if (bounds != null && !fixed.containsKey(i)) y = Math.max(bounds[i][0], Math.min(bounds[i][1], y));
            ys[i] = (int) y;
        }
        for (int i = 0; i < n; i++) st.pieces.get(i).y = ys[i];
        return st;
    }

    Street grade(Street st, Map<Integer, Integer> fixed) {
        return grade(st, fixed, null);
    }

    Street grade(Street st) {
        return grade(st, null, null);
    }

    static Map<Integer, Integer> fx(int... kv) {
        Map<Integer, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    double climb(Street st) {
        double[] t = targets(st);
        if (t.length < 2) return 0;
        double[] d = new double[t.length - 1];
        for (int i = 1; i < t.length; i++) d[i - 1] = Math.abs(t[i] - t[i - 1]);
        return Py.sum(d);
    }

    Street curvy(String axis, int lo, int hi, int w, String kind, List<int[]> joints, List<int[]> holes, int[] pin, double[] amp, int base) {
        int L = hi - lo + 1;
        List<int[]> fixed = new ArrayList<>();
        for (int[] aw : joints) fixed.add(I(aw[0] - Math.floorDiv(aw[1], 2) - 1, aw[0] + Math.floorDiv(aw[1], 2) + 1));
        double bestC = 0;
        Street best = null;
        for (int it = 0; it < 10; it++) {
            double A = rng.uniform(amp[0], amp[1]), Pp = rng.uniform(0.7, 1.7) * L, ph = rng.uniform(0, 2 * Math.PI);
            DoubleUnaryOperator f = a -> base + A * StrictMath.sin(2 * Math.PI * (a - lo) / Pp + ph);
            DoubleUnaryOperator g = f;
            if (pin != null) {
                double off = pin[1] - f.applyAsDouble(pin[0]);
                g = a -> f.applyAsDouble(a) + off;
            }
            Street st = new Street(axis, lo, hi, g, w, kind, fixed, holes, 3, 9);
            double c = climb(st);
            if (best == null || c < bestC) {
                bestC = c;
                best = st;
            }
        }
        return best;
    }

    Street addStreet(Street st) {
        streets.add(st);
        for (P p : st.pieces) take(st.rect(p), "street");
        return st;
    }

    boolean freeStreet(Street st, int extra) {
        for (P p : st.pieces) if (!free(st.rect(p, extra))) return false;
        return true;
    }

    Street child(Street parent, int aj, int side, String kind, int w, int length, Street to, double wobble) {
        P p = parent.pieceAt(aj);
        String axis = parent.axis.equals("z") ? "x" : "z";
        int start = p.c + side * (parent.hw + 1);
        P q = null;
        int end;
        if (to != null) {
            q = to.pieceAt(aj);
            end = q.c - side * (to.hw + 1);
        } else end = start + side * (length - 1);
        int lo = Math.min(start, end), hi = Math.max(start, end);
        if (hi - lo < 4) return null;
        DoubleUnaryOperator cf;
        if (wobble != 0) {
            double A = rng.uniform(0, wobble), Pp = rng.uniform(30, 70), ph = rng.uniform(0, 2 * Math.PI);
            double f0 = A * StrictMath.sin(2 * Math.PI * (start - lo) / Pp + ph);
            cf = a -> aj + A * StrictMath.sin(2 * Math.PI * (a - lo) / Pp + ph) - f0;
        } else cf = a -> aj;
        Street st = new Street(axis, lo, hi, cf, w, kind, List.of(), List.of(), 3, kind.equals("lane") ? 5 : 9);
        if (st.pieces.isEmpty()) return null;
        boolean all = true;
        for (P pp : st.pieces) if (!free(st.rect(pp))) {
            all = false;
            break;
        }
        if (!all) {
            List<P> keep = new ArrayList<>();
            List<P> order = new ArrayList<>(st.pieces);
            if (side <= 0) java.util.Collections.reverse(order);
            for (P pp : order) {
                if (!free(st.rect(pp))) break;
                keep.add(pp);
            }
            if (keep.size() < 2 || q != null) return null;
            if (side <= 0) java.util.Collections.reverse(keep);
            st.pieces = keep;
            st.lo = keep.get(0).a0;
            st.hi = keep.get(keep.size() - 1).a1;
        }
        int ni = side > 0 ? 0 : st.pieces.size() - 1;
        Map<Integer, Integer> fixed = fx(ni, p.y);
        if (q != null) fixed.put(st.pieces.size() - 1 - ni, q.y);
        grade(st, fixed);
        st.parent = parent;
        st.side = side;
        st.aj = aj;
        return addStreet(st);
    }

    Street child(Street parent, int aj, int side, String kind, int w, int length, Street to) {
        return child(parent, aj, side, kind, w, length, to, 0.0);
    }

    Street childMid(Street parent, int a, int side, int length) {
        P p = parent.pieceAt(a);
        if (p.a1 - p.a0 < 4) return null;
        a = Math.max(p.a0 + 2, Math.min(p.a1 - 2, a));
        return child(parent, a, side, "lane", 3, length, null);
    }

    List<int[]> laneSpots(int a0, int a1, int n, int gap) {
        List<int[]> out = new ArrayList<>();
        for (int it = 0; it < n * 12; it++) {
            if (out.size() >= n || a1 <= a0) break;
            int a = rng.randint(a0, a1);
            boolean ok = true;
            for (int[] b : out) if (Math.abs(a - b[0]) < gap) {
                ok = false;
                break;
            }
            if (ok) out.add(I(a, rng.choice(I(-1, 1, 1, -1, 0))));
        }
        out.sort(Siteplan.LEX);
        return out;
    }

    List<int[]> laneSpots(int a0, int a1, int n) {
        return laneSpots(a0, a1, n, 26);
    }

    // -------------------------------------------------------------------------------------------- rivers

    int riverW() {
        int t = rng.choice(I(9, 11));
        return size.equals("village") ? 7 : size.equals("town") ? t : 11;
    }

    Street gradeRiver(Street rv) {
        double[] t = targets(rv);
        for (int i = 0; i < t.length; i++) t[i] = t[i] - 1.5;
        int n = t.length;
        int[] ys = new int[n];
        Long y = null;
        for (int q = 0; q < n; q++) {
            int i = t[0] >= t[n - 1] ? q : n - 1 - q;
            if (y == null) y = Py.round(t[i]);
            else if (t[i] < y - 1.5) y -= Math.min(2, Py.intOf(y - t[i] - 0.5));
            ys[i] = (int) (long) y;
        }
        for (int i = 0; i < n; i++)
            if (rv.pieces.get(i).br()) for (int j : I(i - 1, i + 1)) if (0 <= j && j < n) ys[j] = ys[i];
        for (int i = 0; i < n; i++) rv.pieces.get(i).y = ys[i];
        return rv;
    }

    Street river(String axis, int lo, int hi, DoubleUnaryOperator cf, int w, List<int[]> bridges, List<int[]> joints) {
        List<int[]> fixed = new ArrayList<>(bridges);
        fixed.addAll(joints);
        Street rv = new Street(axis, lo, hi, cf, w, "river", fixed, List.of(), 5, 11);
        for (P p : rv.pieces) {
            boolean b = false;
            for (int[] ab : bridges) if (p.a0 == ab[0] && p.a1 == ab[1]) b = true;
            p.bridge = b;
        }
        List<P> ps = rv.pieces;
        int i = 0;
        while (i < ps.size()) {
            P p = ps.get(i);
            if (p.a1 - p.a0 < 4 && !p.br()) {
                Integer j = null;
                for (int jj : I(i - 1, i + 1)) if (0 <= jj && jj < ps.size() && !ps.get(jj).br()) {
                    j = jj;
                    break;
                }
                if (j != null) {
                    P pj = ps.get(j);
                    pj.a0 = Math.min(pj.a0, p.a0);
                    pj.a1 = Math.max(pj.a1, p.a1);
                    ps.remove(i);
                    continue;
                }
            }
            i++;
        }
        oddPieces(ps);
        rv.lo = ps.get(0).a0;
        rv.hi = ps.get(ps.size() - 1).a1;
        gradeRiver(rv);
        rivers.add(rv);
        for (P p : rv.pieces) take(rv.rect(p), "river");
        return rv;
    }

    static void oddPieces(List<P> ps) {
        List<List<P>> runs = new ArrayList<>();
        List<P> cur = new ArrayList<>();
        List<P> all = new ArrayList<>(ps);
        all.add(null);
        for (P p : all) {
            if (p == null || p.br()) {
                if (!cur.isEmpty()) runs.add(cur);
                cur = new ArrayList<>();
            } else cur.add(p);
        }
        for (List<P> run : runs) {
            boolean openLo = run.get(0) == ps.get(0), openHi = run.get(run.size() - 1) == ps.get(ps.size() - 1);
            int T = run.get(run.size() - 1).a1 - run.get(0).a0 + 1;
            if (!(openLo || openHi) && run.size() % 2 != Math.floorMod(T, 2) && run.size() > 1) {
                int jb = 0, bv = Integer.MAX_VALUE;
                for (int j = 0; j < run.size() - 1; j++) {
                    int v = run.get(j + 1).a1 - run.get(j).a0;
                    if (v < bv) {
                        bv = v;
                        jb = j;
                    }
                }
                run.get(jb).a1 = run.get(jb + 1).a1;
                ps.remove(run.get(jb + 1));
                run.remove(jb + 1);
            }
            if (openLo && !openHi) {
                for (int j = run.size() - 1; j > 0; j--)
                    if (even(run.get(j))) {
                        run.get(j).a0 -= 1;
                        run.get(j - 1).a1 -= 1;
                    }
                if (even(run.get(0))) run.get(0).a0 -= 1;
            } else {
                for (int j = 0; j < run.size() - 1; j++)
                    if (even(run.get(j))) {
                        run.get(j).a1 += 1;
                        run.get(j + 1).a0 += 1;
                    }
                if (even(run.get(run.size() - 1)) && openHi) run.get(run.size() - 1).a1 += 1;
            }
        }
    }

    static boolean even(P p) {
        return Math.floorMod(p.a1 - p.a0, 2) == 1;
    }

    Street bank(Street rv, int side, int w, String kind, Integer a0o, Integer a1o, Map<Integer, Integer> fix) {
        int a0 = a0o == null ? rv.lo : a0o, a1 = a1o == null ? rv.hi : a1o;
        Street st = new Street();
        st.axis = rv.axis;
        st.lo = a0;
        st.hi = a1;
        st.w = w;
        st.hw = Math.floorDiv(w, 2);
        st.kind = kind;
        st.step = 1;
        int off = side * (rv.hw + 1 + Math.floorDiv(w, 2));
        DoubleUnaryOperator f = rv.cfun;
        st.cfun = a -> f.applyAsDouble(a) + off;
        st.pieces = new ArrayList<>();
        for (P p : rv.pieces) {
            if (!(p.a1 >= a0 && p.a0 <= a1)) continue;
            P q = new P(Math.max(a0, p.a0), Math.min(a1, p.a1), p.c + off, 0);
            q.ry = p.y;
            q.bridge = p.bridge;
            if (q.a1 >= q.a0) st.pieces.add(q);
        }
        if (st.pieces.isEmpty() || !freeStreet(st, 0)) return null;
        Map<Integer, Integer> fixed = new LinkedHashMap<>();
        for (int i = 0; i < st.pieces.size(); i++) if (st.pieces.get(i).br()) fixed.put(i, st.pieces.get(i).ry);
        if (fix != null) fixed.putAll(fix);
        int[][] bounds = new int[st.pieces.size()][];
        for (int i = 0; i < bounds.length; i++) bounds[i] = I(st.pieces.get(i).ry, st.pieces.get(i).ry + 2);
        grade(st, fixed, bounds);
        return addStreet(st);
    }

    Integer riverSpot(int a0, int a1, List<Integer> avoid, int xr) {
        Double bestM = null;
        Integer bestZ = null;
        for (int it = 0; it < 8; it++) {
            int z = a1 > a0 ? rng.randint(a0, a1) : a0;
            boolean bad = false;
            for (int b : avoid) if (Math.abs(z - b) < 16) bad = true;
            if (bad) continue;
            List<Double> hs = new ArrayList<>();
            for (int x = -xr; x < xr + 1; x += 6) hs.add(H.h(x, z));
            double m = Py.sum(hs, v -> v);
            if (bestM == null || m < bestM) {
                bestM = m;
                bestZ = z;
            }
        }
        return bestZ;
    }

    Street riverAcross(Street main, List<int[]> holes, int xr) {
        int h0 = holes.get(0)[0], h1 = holes.get(0)[1];
        int zr = Math.floorDiv(h0 + h1, 2);
        List<P> before = new ArrayList<>(), after = new ArrayList<>();
        for (P p : main.pieces) {
            if (p.a1 < h0) before.add(p);
            if (p.a0 > h1) after.add(p);
        }
        int pc = (!before.isEmpty() ? before.get(before.size() - 1) : after.get(0)).c;
        int w = h1 - h0 + 1;
        double A = rng.uniform(1.5, 6.0), Pp = rng.uniform(80, 160), ph = rng.uniform(0, 2 * Math.PI);
        double f0 = A * StrictMath.sin(2 * Math.PI * pc / Pp + ph);
        DoubleUnaryOperator cf = x -> zr + A * StrictMath.sin(2 * Math.PI * x / Pp + ph) - f0;
        int bh = Math.max(2, main.hw);
        Street rv = river("x", pc - xr, pc + xr, cf, w, List.of(I(pc - bh, pc + bh)), List.of(I(pc - bh - 5, pc - bh - 1), I(pc + bh + 1, pc + bh + 5)));
        P bp = null;
        for (P p : rv.pieces) if (p.br()) {
            bp = p;
            break;
        }
        if (bp == null) throw new java.util.NoSuchElementException("StopIteration");
        riverFix = new LinkedHashMap<>();
        if (!before.isEmpty()) riverFix.put(main.pieces.indexOf(before.get(before.size() - 1)), bp.y);
        if (!after.isEmpty()) riverFix.put(main.pieces.indexOf(after.get(0)), bp.y);
        riverPc = pc;
        return rv;
    }

    void bankLanes(Street rv, Street main, int pc) {
        for (int side : I(-1, 1))
            for (int way : I(-1, 1)) {
                if (rng.random() > (!size.equals("village") ? 0.55 : 0.35)) continue;
                P near = main.pieceAt(rv.pieceAt(pc).c + side * (rv.hw + 2));
                int a0, a1;
                if (way > 0) {
                    a0 = near.c + main.hw + 1;
                    a1 = Math.min(rv.hi - 4, a0 + rng.randint(24, 60));
                } else {
                    a1 = near.c - main.hw - 1;
                    a0 = Math.max(rv.lo + 4, a1 - rng.randint(24, 60));
                }
                if (a1 - a0 < 12) continue;
                bank(rv, side, 3, "bank", a0, a1, null);
            }
    }

    Street mainWithRiver(int lo, int hi, int W, List<int[]> joints, int[] pin, Map<Integer, Integer> fixed, Integer a0, Integer a1,
                         List<Integer> avoid, double[] amp) {
        List<int[]> holes = new ArrayList<>();
        Integer zr = null;
        int xr = Math.max(Map.of("village", 50, "town", 80, "big", 110).get(size), this.xr);
        if (rng.random() < C.river) {
            int rw = riverW();
            List<Integer> av = new ArrayList<>();
            for (int[] j : joints) av.add(j[0]);
            av.addAll(avoid);
            zr = riverSpot(a0 != null ? a0 : lo + 20, a1 != null ? a1 : hi - 20, av, xr);
            if (zr != null) {
                holes.add(I(zr - Math.floorDiv(rw, 2), zr + Math.floorDiv(rw, 2)));
                List<int[]> nj = new ArrayList<>();
                for (int[] j : joints) if (Math.abs(j[0] - zr) > Math.floorDiv(rw, 2) + Math.floorDiv(j[1], 2) + 4) nj.add(j);
                joints = nj;
            }
        }
        Street main = curvy("z", lo, hi, W, "main", joints, holes, pin, amp, 0);
        Street rv = !holes.isEmpty() ? riverAcross(main, holes, xr) : null;
        Map<Integer, Integer> f = new LinkedHashMap<>(fixed != null ? fixed : Map.of());
        f.putAll(riverFix);
        grade(main, f);
        addStreet(main);
        this.main = main;
        if (rv != null) bankLanes(rv, main, riverPc);
        this.zr = zr;
        zrSet = true;
        return main;
    }

    static final double[] AMP = {1.5, 8.0};

    List<int[]> offRiver(List<int[]> spots) {
        List<int[]> o = new ArrayList<>();
        for (int[] s : spots) if (zr == null || Math.abs(s[0] - zr) > 12) o.add(s);
        return o;
    }

    void clipRivers() {
        for (Street rv : rivers) {
            int lo, hi;
            if (wallRect != null) {
                lo = rv.axis.equals("x") ? wallRect[0] : wallRect[1];
                hi = rv.axis.equals("x") ? wallRect[2] : wallRect[3];
                lo -= 3;
                hi += 3;
            } else {
                int mn = Integer.MAX_VALUE, mx = Integer.MIN_VALUE;
                boolean xa = rv.axis.equals("x");
                for (Map.Entry<Long, String> e : claim.entrySet()) {
                    if (e.getValue().equals("river")) continue;
                    int v = xa ? cx(e.getKey()) : cz(e.getKey());
                    mn = Math.min(mn, v);
                    mx = Math.max(mx, v);
                }
                if (mn > mx) throw new IllegalArgumentException("min() arg is an empty sequence");
                lo = mn - 2;
                hi = mx + 2;
            }
            List<P> keep = new ArrayList<>();
            for (P p : rv.pieces) {
                if (p.a1 < lo || p.a0 > hi) continue;
                P q = p.copy();
                if (q.a0 < lo) q.a0 = lo + Math.floorMod(q.a1 - lo, 2);
                if (q.a1 > hi) q.a1 = hi - Math.floorMod(hi - q.a0, 2);
                if (q.a1 - q.a0 >= 4 || q.br()) keep.add(q);
            }
            if (!keep.isEmpty()) {
                rv.pieces = keep;
                rv.lo = keep.get(0).a0;
                rv.hi = keep.get(keep.size() - 1).a1;
            }
        }
    }

    void mergeGround() {
        List<Map<String, Object>> ts = new ArrayList<>(), rest = new ArrayList<>();
        for (Map<String, Object> r : rooms) ("terrace".equals(r.get("special")) ? ts : rest).add(r);
        boolean changed = true;
        while (changed) {
            changed = false;
            Map<List<Integer>, List<Map<String, Object>>> byX = new HashMap<>(), byZ = new HashMap<>();
            for (Map<String, Object> r : ts) {
                byX.computeIfAbsent(List.of(gi(r, "y"), gi(r, "variant"), gi(r, "z0"), gi(r, "z1"), gi(r, "x1") + 1), q -> new ArrayList<>()).add(r);
                byZ.computeIfAbsent(List.of(gi(r, "y"), gi(r, "variant"), gi(r, "x0"), gi(r, "x1"), gi(r, "z1") + 1), q -> new ArrayList<>()).add(r);
            }
            Set<Map<String, Object>> gone = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
            for (Map<String, Object> r : ts) {
                if (gone.contains(r)) continue;
                List<Map<String, Object>> nbs = new ArrayList<>(byX.getOrDefault(List.of(gi(r, "y"), gi(r, "variant"), gi(r, "z0"), gi(r, "z1"), gi(r, "x0")), List.of()));
                nbs.addAll(byZ.getOrDefault(List.of(gi(r, "y"), gi(r, "variant"), gi(r, "x0"), gi(r, "x1"), gi(r, "z0")), List.of()));
                for (Map<String, Object> nb : nbs) {
                    if (nb == r || gone.contains(nb) || Math.max(gi(r, "x1"), gi(nb, "x1")) - Math.min(gi(r, "x0"), gi(nb, "x0")) >= 120
                            || Math.max(gi(r, "z1"), gi(nb, "z1")) - Math.min(gi(r, "z0"), gi(nb, "z0")) >= 120) continue;
                    int nx0 = Math.min(gi(r, "x0"), gi(nb, "x0")), nz0 = Math.min(gi(r, "z0"), gi(nb, "z0"));
                    r.put("x0", nx0);
                    r.put("z0", nz0);
                    gone.add(nb);
                    changed = true;
                    break;
                }
            }
            List<Map<String, Object>> nts = new ArrayList<>();
            for (Map<String, Object> r : ts) if (!gone.contains(r)) nts.add(r);
            ts = nts;
        }
        rest.addAll(ts);
        rooms = rest;
    }

    void layRivers() {
        for (Street rv : rivers) {
            List<P> ps = rv.pieces;
            int n = ps.size();
            P run = null;
            for (int i = 0; i <= n; i++) {
                P p = i < n ? ps.get(i) : null;
                int var = 0;
                if (p != null) {
                    boolean loC = i == 0 || ps.get(i - 1).y < p.y;
                    boolean hiC = i == n - 1 || ps.get(i + 1).y < p.y;
                    if (p.br()) var = 2;
                    else if (loC && hiC) var = 5;
                    else if (loC) var = rv.axis.equals("x") ? 3 : 4;
                    else if (hiC) var = rv.axis.equals("x") ? 4 : 3;
                    else var = 1;
                    if (run != null && var == 1 && run.var == 1 && p.y == run.y && p.c == run.c && p.a1 - run.a0 < 60) {
                        run.a1 = p.a1;
                        continue;
                    }
                }
                if (run != null)
                    ground("river", rv.rect(run), run.y, run.var, "front", rv.axis.equals("z") ? "east" : "south", "walls", mWall);
                if (p != null) {
                    run = p.copy();
                    run.var = var;
                } else run = null;
            }
        }
    }

    // -------------------------------------------------------------------------------------------- street plans

    void netBank() {
        int L = odd(rng.randint(C.L[0], C.L[1]));
        int W = this.W = rng.choice(C.W);
        int lo = -Math.floorDiv(L, 2), hi = Math.floorDiv(L, 2);
        int rw = riverW();
        int rhw = Math.floorDiv(rw, 2);
        int wb = 5;
        int nb = size.equals("village") ? 1 : rng.randint(2, 3);
        if (box != null) nb = Math.max(nb, Math.floorDiv(L, 100));
        List<Integer> bz = new ArrayList<>();
        for (int[] s : laneSpots(lo + 18, hi - 18, nb, 30)) bz.add(s[0]);
        List<int[]> spots = new ArrayList<>();
        int nl = rng.randint(C.lanes[0], C.lanes[1]);
        for (int[] s : laneSpots(lo + 16, hi - 16, nl)) {
            boolean ok = true;
            for (int b : bz) if (Math.abs(s[0] - b) <= 12) ok = false;
            if (ok) spots.add(I(s[0], 1));
        }
        if (gridded) {
            int step = rng.randint(38, 44);
            spots = new ArrayList<>();
            int st = G != null ? G : step;
            for (int a = lo + 24; a < hi - 20; a += st) {
                boolean ok = true;
                for (int b : bz) if (Math.abs(a - b) <= 8) ok = false;
                if (ok) spots.add(I(a, 1));
            }
        }
        int xr0 = -(rhw + 1 + Math.floorDiv(W, 2));
        List<Integer> east = null;
        if (box != null && gridded) {
            int kb = Math.max(1, Math.floorDiv(box[0] - 2 * pad - rw - 62, G));
            east = new ArrayList<>();
            for (int j = 1; j <= kb; j++) east.add(G * j);
            xr0 -= Math.floorDiv(kb * G - rw - 18, 2);
        }
        final int xr = xr0;
        double A = rng.uniform(1.5, 5.0), Pp = rng.uniform(1.0, 2.0) * L, ph = rng.uniform(0, 2 * Math.PI);
        DoubleUnaryOperator cf = z -> xr + A * StrictMath.sin(2 * Math.PI * z / Pp + ph);
        List<int[]> brs = new ArrayList<>(), js = new ArrayList<>();
        for (int z : bz) brs.add(I(z - wb / 2, z + wb / 2));
        for (int[] s : spots) js.add(I(s[0] - 2, s[0] + 2));
        Street rv = river("z", lo - 12, hi + 12, cf, rw, brs, js);
        Street main = bank(rv, 1, W, "main", lo, hi, null);
        this.main = main;
        if (rng.random() < 0.6 || size.equals("village")) {
            int Ww = size.equals("village") ? 3 : rng.choice(I(3, 5));
            int wa0 = lo + rng.randint(0, 24);
            int wa1 = hi - rng.randint(0, 24);
            Street west = bank(rv, -1, Ww, "bank", wa0, wa1, null);
            if (west != null) {
                for (int z : bz) {
                    if (west.lo + 3 <= z && z <= west.hi - 3) {
                        if (west.pieceAt(z).a1 - west.pieceAt(z).a0 >= 4) childMid(west, z, -1, rng.randint(16, 30));
                        else child(west, z, -1, "lane", 3, rng.randint(16, 30), null);
                    }
                }
            }
        } else {
            int Ww = 5;
            int D = rng.randint(24, 32);
            int off = rhw + 1 + D + Math.floorDiv(Ww, 2) + 1;
            List<int[]> wf = new ArrayList<>();
            for (int z : bz) wf.add(I(z - wb / 2 - 1, z + wb / 2 + 1));
            int s0 = lo + rng.randint(4, 20);
            int s1 = hi - rng.randint(4, 20);
            Street ws = new Street("z", s0, s1, z -> cf.applyAsDouble(z) - off, Ww, "cross", wf, List.of(), 3, 9);
            if (freeStreet(ws, 0)) {
                grade(ws);
                addStreet(ws);
                for (int z : bz) {
                    if (!(ws.lo + 3 <= z && z <= ws.hi - 3)) continue;
                    P q = ws.pieceAt(z);
                    P rp = rv.pieceAt(z);
                    int a0 = q.c + ws.hw + 1, a1 = rp.c - rv.hw - 1;
                    if (a1 - a0 < 3) continue;
                    Street cn = new Street("x", a0, a1, a -> z, 3, "lane", List.of(), List.of(), 3, 5);
                    if (freeStreet(cn, 0)) {
                        grade(cn, fx(0, q.y, cn.pieces.size() - 1, rp.y));
                        addStreet(cn);
                    }
                }
            }
        }
        if (gridded) {
            int G2 = rng.randint(38, 42);
            List<Integer> zs = new ArrayList<>();
            for (int[] s : spots) zs.add(s[0]);
            blocks(main, east != null && !east.isEmpty() ? east : List.of(G2, 2 * G2), zs, List.of(), Set.of(), lo + 6, hi - 6, null);
        } else {
            List<int[]> sp = spots;
            later.add(() -> lanesAndBacks(main, sp, I(1), List.of()));
        }
        ends = new ArrayList<>(List.of(new End("entrance", main, main.pieces.size() - 1), new End("far", main, 0)));
        centre = I(main.pieceAt(0).c + 10, 0);
        radius = L / 2.0;
    }

    void netLine() {
        int L = odd(rng.randint(C.L[0], C.L[1]) + (size.equals("town") ? 12 : 0));
        int W = this.W = rng.choice(C.W);
        int lo = -Math.floorDiv(L, 2), hi = Math.floorDiv(L, 2);
        Street main;
        if (G != null) {
            List<Integer> zs = crossZs(lo, hi, G);
            List<int[]> js = new ArrayList<>();
            for (int z : zs) js.add(I(z, 5));
            main = mainWithRiver(lo, hi, W, js, null, null, null, null, zs, AMP);
            List<Integer> jz = new ArrayList<>();
            for (int z : zs) if (jointed(main, z)) jz.add(z);
            blocks(main, offs(G), jz, List.of(), Set.of(), lo + 8, hi - 8, null);
        } else {
            List<int[]> spots = laneSpots(lo + 18, hi - 18, rng.randint(C.lanes[0], C.lanes[1]));
            List<int[]> js = new ArrayList<>();
            for (int[] s : spots) js.add(I(s[0], 3));
            main = mainWithRiver(lo, hi, W, js, null, null, null, null, List.of(), AMP);
            List<int[]> sp = offRiver(spots);
            Street m = main;
            later.add(() -> lanesAndBacks(m, sp, I(-1, 1), List.of()));
        }
        ends = new ArrayList<>(List.of(new End("entrance", main, main.pieces.size() - 1), new End("far", main, 0)));
        centre = I(main.pieceAt(0).c, 0);
        radius = L / 2.0;
    }

    /** The stretches of street b that are free (each at least 4 pieces), as streets of their own, graded and added. */
    List<Street> freeStretches(Street b, List<int[]> joins) {
        List<List<P>> segs = new ArrayList<>();
        List<P> cur = new ArrayList<>();
        List<P> all = new ArrayList<>(b.pieces);
        all.add(null);
        for (P p : all) {
            if (p != null && free(b.rect(p, 2)) && (cur.isEmpty() || p.a0 == cur.get(cur.size() - 1).a1 + 1)) {
                cur.add(p);
                continue;
            }
            if (cur.size() >= 4) segs.add(cur);
            cur = p != null && free(b.rect(p, 2)) ? new ArrayList<>(List.of(p)) : new ArrayList<>();
        }
        List<Street> out = new ArrayList<>();
        for (List<P> ps : segs) {
            Street sb = b.copyAttrs();
            sb.pieces = ps;
            sb.lo = ps.get(0).a0;
            sb.hi = ps.get(ps.size() - 1).a1;
            Map<Integer, Integer> fxm = new LinkedHashMap<>();
            for (int[] j : joins)
                for (int i = 0; i < ps.size(); i++) {
                    P p = ps.get(i);
                    if (p.a1 == j[0] - 1 || p.a0 == j[1] + 1) fxm.put(i, j[2]);
                }
            grade(sb, fxm);
            addStreet(sb);
            out.add(sb);
        }
        return out;
    }

    Map<Integer, List<Street>> lanesAndBacks(Street main, List<int[]> spots, int[] sides, List<Street> crossers) {
        Map<Integer, List<Street>> backs = new LinkedHashMap<>();
        for (int side : sides) {
            List<Integer> zs = new ArrayList<>();
            for (int[] s : spots) if (s[1] == side || s[1] == 0) zs.add(s[0]);
            if (!zs.isEmpty() && rng.random() < C.back) {
                int O = rng.randint(34, 44) + main.hw;
                int a0 = Math.max(main.lo + 6, java.util.Collections.min(zs) - rng.randint(10, 24));
                int a1 = Math.min(main.hi - 6, java.util.Collections.max(zs) + rng.randint(10, 24));
                if (size.equals("big")) {
                    a0 = main.lo + 10;
                    a1 = main.hi - 10;
                }
                if (a1 - a0 < 24) continue;
                DoubleUnaryOperator mc = main.cfun;
                DoubleUnaryOperator cf = a -> mc.applyAsDouble(a) + side * O;
                List<int[]> holes = new ArrayList<>(), joins = new ArrayList<>();
                for (Street cr : crossers) {
                    int x = (int) Py.round(cf.applyAsDouble(cr.aj));
                    if (cr.lo <= x && x <= cr.hi) {
                        P q = cr.pieceAt(x);
                        holes.add(I(q.c - cr.hw, q.c + cr.hw));
                        joins.add(I(q.c - cr.hw, q.c + cr.hw, q.y));
                    }
                }
                List<int[]> fixed = new ArrayList<>();
                for (int a : zs) fixed.add(I(a - 2, a + 2));
                Street b = new Street(main.axis, a0, a1, cf, 3, "back", fixed, holes, 3, 7);
                backs.put(side, freeStretches(b, joins));
            }
        }
        for (int[] s : spots) {
            int a = s[0], sd = s[1];
            for (int side : sd == 0 ? I(-1, 1) : I(sd)) {
                boolean in = false;
                for (int x : sides) if (x == side) in = true;
                if (!in) continue;
                Street b = null;
                for (Street sb : backs.getOrDefault(side, List.of()))
                    if (sb.lo + 2 <= a && a <= sb.hi - 2) {
                        b = sb;
                        break;
                    }
                child(main, a, side, "lane", 3, rng.randint(18, 34), b);
            }
        }
        return backs;
    }

    void netT() {
        int L = odd(rng.randint(C.L[0], C.L[1]));
        int W = this.W = rng.choice(C.W);
        int lo = -Math.floorDiv(L, 2), hi = Math.floorDiv(L, 2);
        int Lc = odd(!size.equals("village") ? rng.randint((int) Py.intOf(L * 0.45), (int) Py.intOf(L * 0.7))
                : rng.randint((int) Py.intOf(L * 0.4), (int) Py.intOf(L * 0.55)));
        if (size.equals("big") && box == null) {
            L = odd((int) Py.intOf(L * 0.82));
            lo = -Math.floorDiv(L, 2) + 20;
            hi = Math.floorDiv(L, 2) + 20;
        }
        if (box != null) {
            lo = -Math.floorDiv(L, 2) + 25;
            hi = Math.floorDiv(L, 2) + 25;
            Lc = odd(Math.min(box[0] - 2 * pad - 20, G != null ? 2 * k * G + 50 : Lc));
        }
        int Wc = Math.max(3, W - rng.choice(I(0, 2)));
        List<int[]> cspots = new ArrayList<>();
        int ncs = rng.randint(0, 2);
        for (int[] s : laneSpots(-Math.floorDiv(Lc, 2) + 14, Math.floorDiv(Lc, 2) - 14, ncs, 22)) if (Math.abs(s[0]) > W + 10) cspots.add(I(s[0], 1));
        List<int[]> js = new ArrayList<>();
        js.add(I(0, W));
        for (int[] s : cspots) js.add(I(s[0], 3));
        Street cross = curvy("x", -Math.floorDiv(Lc, 2), Math.floorDiv(Lc, 2), Wc, "cross", js, List.of(), null, new double[]{0.0, 3.0}, lo);
        grade(cross);
        addStreet(cross);
        P jp = cross.pieceAt(0);
        int start = jp.c + cross.hw + 1;
        List<int[]> spots = laneSpots(start + 16, hi - 16, rng.randint(C.lanes[0], C.lanes[1]));
        if (gridded) {
            int step = rng.randint(38, 44);
            spots = new ArrayList<>();
            int st = G != null ? G : step;
            for (int a = start + 30; a < hi - 20; a += st) spots.add(I(a, 0));
        }
        List<int[]> mj = new ArrayList<>();
        for (int[] s : spots) mj.add(I(s[0], 3));
        Street main = mainWithRiver(start, hi, W, mj, I(start, 0), fx(0, jp.y), start + 22, hi - 22, List.of(), AMP);
        spots = offRiver(spots);
        for (int[] s : cspots) {
            int a = s[0];
            later.add(() -> child(cross, a, 1, "lane", 3, rng.randint(16, 28), null));
        }
        if (gridded) {
            int G2 = rng.randint(38, 42);
            List<Integer> zs = new ArrayList<>();
            for (int[] s : spots) zs.add(s[0]);
            blocks(main, offs(G != null ? G : G2), zs, List.of(), Set.of(), start + 6, hi - 8, null);
        } else {
            List<int[]> sp = spots;
            later.add(() -> lanesAndBacks(main, sp, I(-1, 1), List.of()));
        }
        ends = new ArrayList<>(List.of(new End("entrance", main, main.pieces.size() - 1), new End("cross_w", cross, 0),
                new End("cross_e", cross, cross.pieces.size() - 1)));
        head = new Object[]{cross, 0};
        centre = I(0, start + Math.floorDiv(hi - start, 3));
        radius = L / 2.0;
    }

    List<Street> parallel(Street main, int off, int w, String kind, List<Street> crossers, List<Integer> joints, Integer a0o, Integer a1o) {
        int a0 = a0o == null ? main.lo + 4 : a0o, a1 = a1o == null ? main.hi - 4 : a1o;
        DoubleUnaryOperator mc = main.cfun;
        DoubleUnaryOperator cf = a -> mc.applyAsDouble(a) + off;
        if (layout.equals("grid")) {
            int cm = main.pieceAt(Math.floorDiv(a0 + a1, 2)).c;
            cf = a -> cm + off;
        }
        List<int[]> holes = new ArrayList<>(), joins = new ArrayList<>();
        for (Street cr : crossers) {
            int x = (int) Py.round(cf.applyAsDouble(cr.aj));
            if (cr.lo <= x && x <= cr.hi) {
                P q = cr.pieceAt(x);
                holes.add(I(q.c - cr.hw, q.c + cr.hw));
                joins.add(I(q.c - cr.hw, q.c + cr.hw, q.y));
            }
        }
        List<int[]> fixed = new ArrayList<>();
        for (int a : joints) fixed.add(I(a - 2, a + 2));
        Street b = new Street(main.axis, a0, a1, cf, w, kind, fixed, holes, 3, 7);
        return freeStretches(b, joins);
    }

    void netGrid() {
        int L = odd(rng.randint(C.L[0], C.L[1]));
        int W = this.W = size.equals("big") ? 7 : rng.choice(C.W);
        int lo = -Math.floorDiv(L, 2), hi = Math.floorDiv(L, 2);
        int G = rng.randint(38, 42);
        if (this.G != null) G = this.G;
        int zc = rng.randint(-8, 8);
        List<Integer> xsOff = offs(G);
        List<Integer> zs = crossZs(lo, hi, G, zc, 14);
        List<int[]> js = new ArrayList<>();
        js.add(I(zc, W));
        for (int z : zs) js.add(I(z, 5));
        List<Integer> av = new ArrayList<>(List.of(zc - 16, zc + 16));
        av.addAll(zs);
        Street main = mainWithRiver(lo, hi, W, js, null, null, null, null, av, new double[]{0.0, 0.6});
        if (box != null) {
            List<Integer> z2 = new ArrayList<>();
            for (int z : zs) if (jointed(main, z)) z2.add(z);
            zs = z2;
        }
        gridZ = zs;
        P jp = main.pieceAt(zc);
        List<Street> arms = new ArrayList<>();
        int half = Math.floorDiv(L, 2) - rng.randint(0, 10);
        if (box != null) half = k * G + Math.max(10, Math.min(outer, 40));
        for (int side : I(-1, 1)) {
            Street st = child(main, zc, side, "cross", W, half, null, 0.0);
            if (st != null) arms.add(st);
        }
        this.arms = arms;
        Map<Integer, Street> bySide = new LinkedHashMap<>();
        for (Street st : arms) bySide.put(st.side, st);
        int yq = rng.choice(I(-1, 1));
        Set<List<Integer>> skip = Set.of(List.of(zc - G, yq), List.of(zc + G, -yq));
        gridSites = new ArrayList<>();
        if (bySide.containsKey(yq)) gridSites.add(new Object[]{"yamen", new Side(bySide.get(yq), -1)});
        if (bySide.containsKey(-yq)) gridSites.add(new Object[]{"temple", new Side(bySide.get(-yq), 1)});
        blocks(main, xsOff, zs, arms, skip, lo + 8, hi - 8, null);
        final int Gf = G;
        later.add(() -> {
            for (Street st : arms) {
                for (int[] s : laneSpots(st.lo + 10, st.hi - 10, rng.randint(1, 3), 20)) {
                    int a = s[0], sd = s[1];
                    boolean ok = true;
                    for (int o : prepend0(xsOff)) if (Math.abs(a - (jp.c + o)) <= 10) ok = false;
                    if (ok) for (int side : sd == 0 ? I(-1, 1) : I(sd)) childMid(st, a, side, rng.randint(14, Math.floorDiv(Gf, 2) + 4));
                }
            }
        });
        List<End> e = new ArrayList<>(List.of(new End("entrance", main, main.pieces.size() - 1), new End("far", main, 0)));
        for (Street st : arms) e.add(new End("arm", st, st.side > 0 ? st.pieces.size() - 1 : 0));
        ends = e;
        centre = I(jp.c, zc);
        radius = L * 0.75;
        crossing = I(jp.c, zc, jp.y);
    }

    static List<Integer> prepend0(List<Integer> l) {
        List<Integer> o = new ArrayList<>(List.of(0));
        o.addAll(l);
        return o;
    }

    Map<Integer, Object> blocks(Street main, List<Integer> xsOff, List<Integer> zs, List<Street> crossers, Set<List<Integer>> skip, Integer a0, Integer a1,
                                Integer outer0) {
        Integer outer = outer0 == null ? this.outer : outer0;
        java.util.TreeMap<Integer, Object> cols = new java.util.TreeMap<>();
        cols.put(0, main);
        for (int off : xsOff) cols.put(off, parallel(main, off, 5, "cross", crossers, zs, a0, a1));
        List<Integer> order = new ArrayList<>(cols.keySet());
        for (int z : zs) {
            for (int k = 0; k < order.size(); k++) {
                int o = order.get(k);
                Street src = o == 0 ? (Street) cols.get(o) : findAround((List<Street>) cols.get(o), z);
                if (src == null) continue;
                for (int side : I(1, -1)) {
                    int k2 = k + side;
                    if (0 <= k2 && k2 < order.size()) {
                        int o2 = order.get(k2);
                        if (o2 == 0 || skip.contains(List.of(z, o2 > 0 ? 1 : -1))) continue;
                        Street dst = findAround((List<Street>) cols.get(o2), z);
                        if (dst != null) child(src, z, side, "cross", 5, 24, dst);
                    } else if (o != 0 && (outer == null || outer >= 14)) {
                        Street s2 = src;
                        Integer ou = outer;
                        later.add(() -> child(s2, z, side, "lane", 3, ou == null ? rng.randint(24, 40) : ou, null));
                    }
                }
            }
        }
        return cols;
    }

    static Street findAround(List<Street> l, int z) {
        for (Street sb : l) if (sb.lo + 3 <= z && z <= sb.hi - 3) return sb;
        return null;
    }

    void netCross() {
        if (G != null) {
            netGrid();
            return;
        }
        int L = odd(rng.randint(C.L[0], C.L[1]));
        int W = this.W = rng.choice(C.W);
        int lo = -Math.floorDiv(L, 2), hi = Math.floorDiv(L, 2);
        int zc = rng.randint(Math.floorDiv(-L, 8), Math.floorDiv(L, 10));
        int Wc = Math.max(3, W - rng.choice(I(0, 0, 2)));
        List<int[]> spots = new ArrayList<>();
        int nl = rng.randint(C.lanes[0], C.lanes[1]);
        for (int[] s : laneSpots(lo + 16, hi - 16, nl)) if (Math.abs(s[0] - zc) > Wc + 14) spots.add(s);
        List<int[]> js = new ArrayList<>();
        js.add(I(zc, Wc));
        for (int[] s : spots) js.add(I(s[0], 3));
        Street main = mainWithRiver(lo, hi, W, js, null, null, null, null, List.of(zc - 16, zc + 16), AMP);
        spots = offRiver(spots);
        P jp = main.pieceAt(zc);
        List<Street> arms = new ArrayList<>();
        boolean big = size.equals("big");
        for (int side : I(-1, 1)) {
            int Lc = !big ? rng.randint((int) Py.intOf(L * 0.3), (int) Py.intOf(L * 0.45)) : rng.randint((int) Py.intOf(L * 0.42), (int) Py.intOf(L * 0.5));
            Street st = child(main, zc, side, "cross", Wc, Lc, null, !big ? 3.0 : 1.5);
            if (st != null) arms.add(st);
        }
        this.arms = arms;
        List<int[]> sp = spots;
        later.add(() -> lanesAndBacks(main, sp, I(-1, 1), big ? arms : List.of()));
        for (Street st : arms) {
            int n = !big ? rng.randint(0, !size.equals("village") ? 2 : 1) : rng.randint(2, 4);
            for (int[] s : laneSpots(st.lo + 12, st.hi - 12, n, 22)) {
                int a = s[0], sd = s[1];
                if (Math.abs(a - jp.c) < main.hw + 12) continue;
                for (int side : sd == 0 ? I(-1, 1) : I(sd))
                    later.add(() -> childMid(st, a, side, !big ? rng.randint(14, 26) : rng.randint(20, 36)));
            }
        }
        List<End> e = new ArrayList<>(List.of(new End("entrance", main, main.pieces.size() - 1), new End("far", main, 0)));
        for (Street st : arms) e.add(new End("arm", st, st.side > 0 ? st.pieces.size() - 1 : 0));
        ends = e;
        centre = I(jp.c, zc);
        radius = L / 2.0;
        crossing = I(jp.c, zc, jp.y);
    }

    void netY() {
        int L = odd(rng.randint(C.L[0], C.L[1]));
        int W = this.W = rng.choice(C.W);
        int lo = -Math.floorDiv(L, 2), hi = Math.floorDiv(L, 2);
        int zf = rng.randint(Math.floorDiv(-L, 10), Math.floorDiv(L, 8));
        int Wb = Math.max(3, W - rng.choice(I(0, 2)));
        int P_ = W + 3;
        int Dp = W + 2;
        List<int[]> spots = laneSpots(zf + 16, hi - 16, Math.max(0, rng.randint(C.lanes[0], C.lanes[1]) - 1));
        Street main;
        if (G != null) {
            List<Integer> zs = crossZs(zf + 2, hi, G);
            List<int[]> js = new ArrayList<>();
            for (int z : zs) js.add(I(z, 5));
            main = mainWithRiver(zf, hi, W, js, I(zf, 0), null, zf + 20, hi - 20, zs, AMP);
            List<Integer> jz = new ArrayList<>();
            for (int z : zs) if (jointed(main, z)) jz.add(z);
            blocks(main, offs(G), jz, List.of(), Set.of(), zf + 6, hi - 8, null);
            spots = new ArrayList<>();
        } else {
            List<int[]> js = new ArrayList<>();
            for (int[] s : spots) js.add(I(s[0], 3));
            main = mainWithRiver(zf, hi, W, js, I(zf, 0), null, zf + 20, hi - 20, List.of(), AMP);
            spots = offRiver(spots);
        }
        int y0 = main.pieces.get(0).y;
        int[] sq = I(-P_, zf - Dp, P_, zf - 1);
        forkRect = sq;
        forkY = y0;
        fork = new LinkedHashMap<>();
        take(sq, "street");
        double kk = rng.uniform(0.22, 0.34);
        int spread = !size.equals("village") ? rng.randint(14, 24) : rng.randint(11, 16);
        int bhi = zf - Dp - 1;
        branches = new ArrayList<>();
        for (int sgn : I(-1, 1)) {
            int o0 = P_ - Math.floorDiv(Wb, 2);
            DoubleUnaryOperator cf = z -> {
                double v = kk * (bhi - z);
                double m = spread <= v ? spread : v;       // (min(spread, k * (bhi - z)): the int when not greater)
                return sgn * (o0 + m);
            };
            int blo = lo + rng.randint(0, 8);
            List<Integer> zb = G != null ? crossZs(blo, bhi, G) : List.of();
            List<int[]> fixed = new ArrayList<>();
            for (int z : zb) fixed.add(I(z - 3, z + 3));
            Street st = new Street("z", blo, bhi, cf, Wb, "branch", fixed, List.of(), 2, 7);
            grade(st, fx(st.pieces.size() - 1, y0));
            addStreet(st);
            branches.add(st);
            int kb = G != null ? (int) Py.intOf(Py.floorDiv(box[0] / 2.0 - pad - 22 - P_ - spread, G)) : 0;
            if (kb > 0) {
                List<Integer> xo = new ArrayList<>();
                for (int j = 1; j <= kb; j++) xo.add(sgn * G * j);
                List<Integer> jz = new ArrayList<>();
                for (int z : zb) if (jointed(st, z)) jz.add(z);
                blocks(st, xo, jz, List.of(), Set.of(), blo + 4, bhi - 6, (int) Py.intOf(box[0] / 2.0 - pad - P_ - spread - kb * G - 6));
            }
        }
        int[] sg = I(-1, 1);
        for (int q = 0; q < 2; q++) {
            Street st = branches.get(q);
            int sgn = sg[q];
            if (G != null) continue;
            for (int[] s : laneSpots(st.lo + 12, st.hi - 18, rng.randint(0, !size.equals("village") ? 2 : 1), 22)) {
                int a = s[0];
                later.add(() -> childMid(st, a, sgn, rng.randint(14, 26)));
            }
        }
        List<int[]> sp = spots;
        later.add(() -> lanesAndBacks(main, sp, I(-1, 1), List.of()));
        List<End> e = new ArrayList<>(List.of(new End("entrance", main, main.pieces.size() - 1)));
        for (Street st : branches) e.add(new End("branch", st, 0));
        ends = e;
        centre = I(0, zf);
        radius = L / 2.0;
    }

    // -------------------------------------------------------------------------------------------- parcels

    Integer row(Side s, int u0, int umax, boolean homes) {
        int F0 = s.lines(u0, u0)[0];
        double w = wealth(s.xz(u0, F0, 8)) + (s.st.kind.equals("main") || s.st.kind.equals("cross") ? 0.15 : 0);
        String ward = ward(w);
        boolean lane = s.st.kind.equals("lane") || s.st.kind.equals("back");
        int n;
        if (homes || lane)
            n = I(1, 2, 3)[rng.choicesIndex(!ward.equals("rich") && !size.equals("big") ? new long[]{5, 3, 1} : new long[]{2, 3, 3}, 1)[0]];
        else n = I(1, 2, 3, 4, 5)[rng.choicesIndex(!ward.equals("rich") ? new long[]{1, 3, 4, 3, 1} : new long[]{1, 2, 4, 4, 3}, 1)[0]];
        String roof = pickRoof(false);
        int pod = mPodium;
        boolean gallery = !homes && !lane && !ward.equals("poor") && rng.random() < mVeranda + (ward.equals("rich") ? 0.2 : 0);
        int depth;
        if (ward.equals("poor")) depth = rng.choice(I(7, 7, 9));
        else depth = !(homes || lane) ? rng.choice(I(9, 11, 11, 13)) : rng.choice(I(7, 9, 9, 11));
        double tall = mTall + (ward.equals("rich") ? 0.25 : ward.equals("poor") ? -0.2 : 0);
        List<Object[]> units = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int st = rng.random() > tall ? 1 : 2;
            if (rng.random() < 0.05 && ward.equals("rich")) st = 3;
            String use;
            if (homes || lane) use = rng.random() < 0.78 ? rng.choice(!ward.equals("poor") ? HOMES : POOR_HOMES) : rng.choice(WORKS);
            else use = mRare && rng.random() < 0.12 ? rng.choice(RARE_SHOPS) : rng.choice(SHOPS);
            Map<String, Object> d = building(use, "storeys", st, "roof", roof, "podium", pod, "veranda", gallery, "wealth", w);
            int wd = rng.choice(ward.equals("poor") ? I(5, 7, 7, 9) : ward.equals("rich") ? I(9, 11, 11, 13) : I(7, 9, 9, 11));
            units.add(new Object[]{d, wd, depth});
        }
        List<int[]> sm = null;
        int u1 = 0, y = 0, width = 0, left = 0;
        while (!units.isEmpty()) {
            sm = new ArrayList<>();
            for (Object[] u : units) sm.add(margins(new LinkedHashMap<>((Map<String, Object>) u[0]), 0));
            left = sm.get(0)[0];
            int right = sm.get(sm.size() - 1)[0];
            int sw = 0;
            for (Object[] u : units) sw += (int) u[1];
            width = left + sw - (units.size() - 1) + right;
            u1 = u0 + width - 1;
            y = s.y(u0, u1);
            if (u1 <= umax && (s.levelOk(u0, u1, y) || units.size() == 1)) break;
            units.remove(units.size() - 1);
        }
        if (units.isEmpty()) return null;
        int[] Fn = s.lines(u0, u1);
        int F = Fn[0], near = Fn[1];
        int fm = Integer.MIN_VALUE;
        for (Object[] u : units) fm = Math.max(fm, frontGap((Map<String, Object>) u[0], s.st.hw));
        int bm = Integer.MIN_VALUE;
        for (int[] m : sm) bm = Math.max(bm, m[2]);
        bm += 1;
        int D = depthFree(s, u0, u1, F, dmax);
        if (D < fm + depth + bm) {
            if (D >= fm + 7 + bm) {
                depth = (D - fm - bm) < 9 ? 7 : 9;
                for (Object[] u : units) u[2] = depth;
            } else return null;
        }
        int u = u0 + left;
        int deepest = 0;
        for (Object[] un : units) {
            int wd = (int) un[1], dp = (int) un[2];
            place((Map<String, Object>) un[0], s.rect(u, u + wd - 1, F, fm, fm + dp - 1), y, s.facing);
            u += wd - 1;
            deepest = Math.max(deepest, dp);
        }
        int back0 = fm + deepest + bm;
        D = Math.min(D, back0 + 40);
        if (rng.random() < (!ward.equals("poor") ? 0.82 : 0.5) + (size.equals("big") ? 0.15 : 0)) {
            String use = rng.random() < 0.8 ? rng.choice(!ward.equals("poor") ? HOMES : POOR_HOMES) : rng.choice(WORKS);
            int storeys = rng.random() < (!ward.equals("rich") ? 0.75 : 0.45) ? 1 : 2;
            String hroof = rng.random() < 0.6 ? roof : null;
            Map<String, Object> hd = building(use, "storeys", storeys, "roof", hroof, "wealth", w - 0.1);
            int[] hm = margins(new LinkedHashMap<>(hd), 1);
            int hs = hm[0], hf = hm[1], hb = hm[2];
            int hw_ = odd(Math.min(width - 2 * hs, rng.choice(!ward.equals("poor") ? I(9, 11, 13, 15, 17) : I(5, 7, 9))));
            int hdp = rng.choice(I(7, 9, 9));
            int yard = !ward.equals("rich") ? rng.randint(2, 5) : rng.randint(4, 8);
            if (hw_ >= 5) {
                int[] xz = s.xz(Math.floorDiv(u0 + u1, 2), F, back0 + yard + hf + Math.floorDiv(hdp, 2));
                int yb = (int) Py.round(H.h(xz[0], xz[1]));
                yb = Math.max(y - 3, Math.min(y + 3, yb));
                if (Math.abs(yb - y) <= 1) yb = y;
                if (yb != y) yard = Math.max(yard, 2 * Math.abs(yb - y) + 4);
                int hb0 = back0 + yard + hf;
                int back = hb0 + hdp - 1 + hb;
                if (back <= D) {
                    int hx0 = rng.random() < 0.6 ? u0 + Math.floorDiv(width - hw_, 2) : (rng.random() < 0.5 ? u0 + hs : u1 - hs - hw_ + 1);
                    place(hd, s.rect(hx0, hx0 + hw_ - 1, F, hb0, hb0 + hdp - 1), yb, s.facing, 1);
                    int cutb = back0 + Math.floorDiv(yard, 2);
                    terrace(s, u0, u1, F, near, null, cutb, y, null);
                    terrace(s, u0, u1, F, near, cutb + 1, back, yb, null);
                    take(s.rect(u0, u1, F, 0, back));
                    if (yb == y) yardSpot(s, u0, u1, F, back0, hb0 - hf, y, w, false);
                    return u1;
                }
            }
        }
        int back;
        if (size.equals("big")) back = Math.min(D, back0 + rng.randint(1, 3));
        else back = Math.min(D, back0 + rng.randint(2, 6) + (ward.equals("poor") || homes ? rng.randint(3, 8) : 0));
        back = Math.max(back, back0);
        if (size.equals("village")) {
            terrace(s, u0, u1, F, near, null, back0 - 1, y, null);
            terrace(s, u0, u1, F, near, back0, back, y, mYard != 1 ? 1 : 0);
        } else terrace(s, u0, u1, F, near, null, back, y, null);
        take(s.rect(u0, u1, F, 0, back));
        yardSpot(s, u0, u1, F, back0 - 1, back, y, w, true);
        return u1;
    }

    Yard yardSpot(Side s, int u0, int u1, int F, int d0, int d1, int y, double w, boolean garden) {
        if (d1 - d0 >= 2) {
            Yard yd = new Yard();
            yd.s = s;
            yd.F = F;
            yd.u0 = u0;
            yd.u1 = u1;
            yd.d0 = d0;
            yd.d1 = d1;
            yd.y = y;
            yd.w = w;
            yd.garden = garden;
            yards.add(yd);
            return yd;
        }
        return null;
    }

    Integer hut(Side s, int u0, int umax) {
        int F0 = s.lines(u0, u0)[0];
        double w = Math.min(0.25, wealth(s.xz(u0, F0, 6)));
        String use = rng.choice(POOR_HOMES);
        String roof = rng.choice("xuanshan", "yingshan");
        Map<String, Object> d = building(use, "roof", roof, "wealth", w);
        int[] m = margins(new LinkedHashMap<>(d), 0);
        int sm = m[0], bm = m[2];
        int wd = rng.choice(I(5, 7, 7)), dp = rng.choice(I(5, 7));
        int garden = !size.equals("big") ? rng.choice(I(0, 5, 6, 7)) : 0;
        int width = 2 * sm + wd + garden;
        int u1 = u0 + width - 1;
        if (u1 > umax) {
            width -= garden;
            garden = 0;
            u1 = u0 + width - 1;
            if (u1 > umax) return null;
        }
        int[] Fn = s.lines(u0, u1);
        int F = Fn[0], near = Fn[1];
        int y = s.y(u0, u1);
        int fm = frontGap(d, s.st.hw);
        int back = fm + dp + bm + rng.randint(1, 5);
        int D = depthFree(s, u0, u1, F, back);
        if (D < fm + dp + bm) return null;
        back = Math.min(back, D);
        boolean left = rng.random() < 0.5 || garden == 0;
        int hx = left ? u0 + sm : u0 + sm + garden;
        place(d, s.rect(hx, hx + wd - 1, F, fm, fm + dp - 1), y, s.facing);
        terrace(s, u0, u1, F, near, null, back, y, rng.choice(I(mYard, 1)));
        take(s.rect(u0, u1, F, 0, back));
        if (garden != 0) {
            int gu0 = left ? hx + wd + sm - 1 : u0;
            yardSpot(s, gu0, gu0 + garden, F, 1, back - 1, y, w, true);
        }
        return u1;
    }

    Integer inn(Side s, int u0, int umax) {
        String use = rng.choice("inn_hall", "inn_hall", "teahouse");
        int storeys = rng.choice(I(2, 2, 3));
        String roof = pickRoof(true);
        boolean ver = rng.random() < 0.5;
        Map<String, Object> d = building(use, "storeys", storeys, "roof", roof, "podium", 1, "veranda", ver, "wealth", 0.8);
        if (fam.landmark != null) d.put("theme", rng.choice(fam.landmark));
        int w = rng.choice(I(13, 15, 17)), dp = rng.choice(I(11, 13));
        int[] m = margins(d, 0);
        int sm = m[0], bm = m[2];
        int fm = frontGap(d, s.st.hw);
        bm += 1;
        int width = w + 2 * sm;
        int u1 = u0 + width - 1;
        if (u1 > umax) return null;
        int y = s.y(u0, u1);
        int[] Fn = s.lines(u0, u1);
        int F = Fn[0], near = Fn[1];
        int D = depthFree(s, u0, u1, F, 60);
        if (D < fm + dp + bm) return null;
        place(d, s.rect(u0 + sm, u0 + sm + w - 1, F, fm, fm + dp - 1), y, s.facing, 3);
        int back = fm + dp + bm;
        if (rng.random() < 0.6) {
            Map<String, Object> sd = building(rng.choice("stable", "kitchen", "storeroom"), "roof", fam.roofs.containsKey("xuanshan") ? "xuanshan" : null);
            int[] sm2 = margins(sd, 1);
            int ss = sm2[0], sf = sm2[1], sb = sm2[2];
            int sw = odd(Math.min(width - 2 * ss, rng.choice(I(9, 11, 13))));
            int b0 = back + rng.randint(2, 4) + sf;
            if (b0 + 7 + sb <= D) {
                int x0 = u0 + Math.floorDiv(width - sw, 2);
                place(sd, s.rect(x0, x0 + sw - 1, F, b0, b0 + 6), y, s.facing, 1);
                back = b0 + 7 + sb;
            }
        }
        terrace(s, u0, u1, F, near, null, back, y, null);
        take(s.rect(u0, u1, F, 0, back));
        return u1;
    }

    Integer shrine(Side s, int u0, int umax) {
        String roof = rng.choice("xieshan", "xuanshan");
        String theme = rng.choice(fam.richThemes);
        Map<String, Object> d = building("temple_hall", "roof", roof, "podium", 1, "wealth", 0.7, "theme", theme);
        int[] m = margins(d, 0);
        int sm = m[0], bm = m[2];
        int wd = rng.choice(I(7, 9)), dp = rng.choice(I(7, 9));
        int width = 2 * sm + wd + 7;
        int u1 = u0 + width - 1;
        if (u1 > umax) return null;
        int[] Fn = s.lines(u0, u1);
        int F = Fn[0], near = Fn[1];
        int y = s.y(u0, u1);
        int fm = frontGap(d, s.st.hw) + 3;
        int back = fm + dp + bm;
        if (depthFree(s, u0, u1, F, back) < back) return null;
        place(d, s.rect(u0 + sm, u0 + sm + wd - 1, F, fm, fm + dp - 1), y, s.facing, 3);
        int[] t = s.xz(u0 + sm + wd + 3, F, fm + Math.floorDiv(dp, 2));
        ornament("tree", t[0], t[1], y, 2, "south");
        terrace(s, u0, u1, F, near, null, back, y, 2);
        take(s.rect(u0, u1, F, 0, back));
        return u1;
    }

    Object[] compoundRooms(boolean temple, boolean yamen) {
        int jin = yamen ? rng.choice(I(2, 3)) : temple ? 2 : rng.choice(I(1, 2));
        Map<String, Object> d = Siteplan.site("house", jin, rng.randrange(1 << 30));
        List<Map<String, Object>> rs = new ArrayList<>();
        for (Map<String, Object> r : (List<Map<String, Object>>) d.get("rooms"))
            if (!"screen".equals(r.get("special"))) rs.add(new LinkedHashMap<>(r));
        int CW = (int) ((List<Object>) d.get("size")).get(0);
        int D = Integer.MIN_VALUE;
        for (Map<String, Object> r : rs) D = Math.max(D, gi(r, "z1"));
        D += 1;
        String wall = yamen || temple || rng.random() < 0.6 ? rng.choice(fam.richWalls) : mWall;
        String theme = rng.choice(fam.richThemes);
        for (Map<String, Object> r : rs) {
            Object sp = r.get("special");
            r.put("walls", !Dyn.truthy(sp) || "wall".equals(sp) || "gate".equals(sp) || "chuihua".equals(sp) ? wall : r.get("walls"));
            r.put("theme", theme);
            r.put("base", mBase);
            r.put("bay_style", mBay);
            r.remove("_role");
            if (!Dyn.truthy(sp)) {
                int pd = Math.min(1, Dyn.iOr0(r, "podium"));
                r.put("overhang", 1);
                r.put("veranda", false);
                r.put("back_veranda", false);
                r.put("colonnade", false);
                r.put("podium", pd);
                if ("east".equals(r.get("front")) || "west".equals(r.get("front"))) r.put("storeys", 1);
            }
        }
        Map<String, Object> hall = null;
        long ba = Long.MIN_VALUE;
        for (Map<String, Object> r : rs)
            if (!Dyn.truthy(r.get("special")) && "south".equals(r.get("front"))) {
                long a = (long) (gi(r, "x1") - gi(r, "x0")) * (gi(r, "z1") - gi(r, "z0"));
                if (a > ba) {
                    ba = a;
                    hall = r;
                }
            }
        if (hall == null) throw new IllegalArgumentException("max() arg is an empty sequence");
        for (Map<String, Object> r : rs)
            if (!Dyn.truthy(r.get("special")))
                r.put("_rank", r == hall ? 3 : "east".equals(r.get("front")) || "west".equals(r.get("front")) ? 0 : 1);
        int c = CW / 2;
        if (temple) {
            if (fam.landmark != null) hall.put("theme", rng.choice(fam.landmark));
            String u = rng.choice("temple_hall", "ancestral_hall");
            String rf = rng.choice("xieshan", "xieshan", "wudian");
            int pd = rng.choice(I(1, 1, 2));
            boolean de = rng.random() < 0.3;
            String ch = rng.choice("", "xuanzi");
            hall.putAll(kw("uses", Siteplan.rep(u, 8), "roof", rf, "podium", pd, "podium_style", "plain", "storeys", 1, "double_eave", de,
                    "horsehead", false, "veranda", false, "back_veranda", false, "colonnade", false, "caihua", ch, "brackets", 0));
            for (Map<String, Object> r : rs)
                if (!Dyn.truthy(r.get("special")) && r != hall)
                    r.put("uses", Siteplan.rep(rng.choice("guest_room", "storeroom", "meditation_room", "study"), 8));
            int fore = rng.choice(I(7, 9, 11));
            rs.add(room(c - 6, D, c + 6, D + fore - 1 - (fore % 2 == 0 ? 1 : 0), rng, "special", "paving", "walls", wall, "theme", theme, "base", mBase));
            int oz = D + fore / 2;
            for (int sx : I(-1, 1)) {
                rs.add(room(c + sx * 5 - 1, oz - 1, c + sx * 5 + 1, oz + 1, rng, "special", "ornament", "variant", ORN.get("ding"),
                        "walls", wall, "theme", theme, "base", mBase));
                if (CW >= 33 && fore >= 7)
                    rs.add(room(c + sx * 11 - 2, oz - 2, c + sx * 11 + 2, oz + 2, rng, "special", "ornament", "variant", ORN.get("tree"),
                            "walls", wall, "theme", theme, "base", mBase));
            }
            D += fore;
        } else if (yamen) {
            for (Map<String, Object> r : rs) {
                if (Dyn.truthy(r.get("special"))) continue;
                if (r == hall)
                    r.putAll(kw("uses", Siteplan.rep("courtroom", 8), "roof", "xieshan", "podium", 1, "storeys", 1, "horsehead", false, "caihua", "xuanzi"));
                else if ("south".equals(r.get("front")))
                    r.putAll(kw("uses", Siteplan.rep(rng.choice("accounts", "main_room", "study"), 8), "horsehead", false));
                else r.put("uses", Siteplan.rep(rng.choice("guardroom", "jail", "accounts", "barracks", "storeroom"), 8));
            }
            int fore = 9;
            rs.add(room(c - 7, D, c + 7, D + fore - 1, rng, "special", "paving", "walls", wall, "theme", theme, "base", mBase));
            for (int sx : I(-1, 1))
                rs.add(room(c + sx * 5 - 1, D + 2, c + sx * 5 + 1, D + 4, rng, "special", "ornament", "variant", ORN.get("lion"),
                        "walls", wall, "theme", theme, "base", mBase, "front", "south"));
            D += fore;
        }
        return new Object[]{rs, CW, D};
    }

    List<Map<String, Object>> putGroup(List<Map<String, Object>> rs, String facing, int tx, int tz, int y) {
        int k = Math.floorMod(DIRS.indexOf(facing) - DIRS.indexOf("south"), 4);
        int CW = Integer.MIN_VALUE, D = Integer.MIN_VALUE;
        for (Map<String, Object> r : rs) {
            CW = Math.max(CW, gi(r, "x1"));
            D = Math.max(D, gi(r, "z1"));
        }
        CW += 1;
        D += 1;
        int[] b = rotRect(I(0, 0, CW - 1, D - 1), k);
        int dx = tx - b[0], dz = tz - b[1];
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r0 : rs) {
            Map<String, Object> r = new LinkedHashMap<>(r0);
            int[] q = rotRect(I(gi(r, "x0"), gi(r, "z0"), gi(r, "x1"), gi(r, "z1")), k);
            r.put("x0", q[0] + dx);
            r.put("x1", q[2] + dx);
            r.put("z0", q[1] + dz);
            r.put("z1", q[3] + dz);
            r.put("y", y);
            r.put("front", turn(r.containsKey("front") ? (String) r.get("front") : "south", k));
            rooms.add(r);
            if (!Dyn.truthy(r.get("special"))) buildings.add(r);
            out.add(r);
        }
        return out;
    }

    int spanRange(Street st, int u0, int u1) {
        List<P> sp = st.span(u0, u1);
        if (sp.isEmpty()) throw new IllegalArgumentException("max() arg is an empty sequence");
        int mx = Integer.MIN_VALUE, mn = Integer.MAX_VALUE;
        for (P p : sp) {
            mx = Math.max(mx, p.y);
            mn = Math.min(mn, p.y);
        }
        return mx - mn;
    }

    Integer compound(Side s, int u0, int umax, boolean temple, boolean yamen) {
        Object[] cr = compoundRooms(temple, yamen);
        List<Map<String, Object>> rs = (List<Map<String, Object>>) cr[0];
        int CW = (int) cr[1], D = (int) cr[2];
        int fore = 2;
        int garden = !(temple || yamen) ? rng.choice(I(0, 0, 9, 11, 13)) : 0;
        int width = CW + 4 + garden;
        int u1 = u0 + width - 1;
        if (u1 > umax && garden != 0) {
            width -= garden;
            garden = 0;
            u1 = u0 + width - 1;
        }
        if (u1 > umax) return null;
        int y = s.y(u0, u1);
        if (spanRange(s.st, u0, u1) > 2) return null;
        int[] Fn = s.lines(u0, u1);
        int F = Fn[0], near = Fn[1];
        int need = fore + D + 1;
        if (depthFree(s, u0, u1, F, need) < need) return null;
        int[] t = s.rect(u0 + 2, u0 + 2 + CW - 1, F, fore, fore + D - 1);
        putGroup(rs, s.facing, t[0], t[1], y);
        terrace(s, u0, u0 + CW + 3, F, near, null, need, y, temple || yamen ? 3 : 2);
        if (garden != 0) {
            int g0 = u0 + CW + 4;
            terrace(s, g0, u1, F, near, null, need, y, 1);
            Yard yd = yardSpot(s, g0 - 1, u1 + 1, F, 3, need - 2, y, 1.0, true);
            yards.get(yards.size() - 1).rich = true;
        }
        take(s.rect(u0, u1, F, 0, need));
        return u1;
    }

    boolean headCompound(Street st, int a, int side, boolean temple, boolean yamen) {
        Object[] cr = compoundRooms(temple, yamen);
        List<Map<String, Object>> rs = (List<Map<String, Object>>) cr[0];
        int CW = (int) cr[1], D = (int) cr[2];
        Side s = new Side(st, side);
        int u0 = a - CW / 2 - 2;
        int u1 = u0 + CW + 3;
        int[] Fn = s.lines(u0, u1);
        int F = Fn[0], near = Fn[1];
        int need = 2 + D + 1;
        if (depthFree(s, u0, u1, F, need) < need) return false;
        int y = s.y(u0, u1);
        int[] t = s.rect(u0 + 2, u0 + 2 + CW - 1, F, 2, 2 + D - 1);
        putGroup(rs, s.facing, t[0], t[1], y);
        terrace(s, u0, u1, F, near, null, need, y, 3);
        take(s.rect(u0, u1, F, 0, need));
        return true;
    }

    boolean endCompound(Street st, int i, boolean temple) {
        Object[] cr = compoundRooms(temple, false);
        List<Map<String, Object>> rs = (List<Map<String, Object>>) cr[0];
        int CW = (int) cr[1], D = (int) cr[2];
        P p = st.pieces.get(i);
        boolean last = i == st.pieces.size() - 1;
        String facing = st.axis.equals("z") ? (last ? "north" : "south") : (last ? "west" : "east");
        int c0 = p.c - CW / 2;
        int a0, a1, ap0, ap1;
        if (last) {
            a0 = p.a1 + 3;
            a1 = p.a1 + 3 + D - 1;
            ap0 = p.a1 + 1;
            ap1 = a1;
        } else {
            a0 = p.a0 - 3 - D + 1;
            a1 = p.a0 - 3;
            ap0 = a0;
            ap1 = p.a0 - 1;
        }
        int[] rect, apron;
        if (st.axis.equals("z")) {
            rect = I(c0, a0, c0 + CW - 1, a1);
            apron = I(c0 - 3, ap0, c0 + CW + 2, ap1);
        } else {
            rect = I(a0, c0, a1, c0 + CW - 1);
            apron = I(ap0, c0 - 3, ap1, c0 + CW + 2);
        }
        if (!free(apron)) return false;
        putGroup(rs, facing, rect[0], rect[1], p.y);
        ground("terrace", apron, p.y, 3);
        take(apron);
        return true;
    }

    Integer square(Side s, int u0, int umax, boolean small) {
        int w, dp;
        if (small) {
            w = odd(rng.randint(11, 15));
            dp = odd(rng.randint(9, 11));
        } else {
            w = odd(rng.randint(19, 27));
            dp = odd(rng.randint(13, 19));
        }
        int u1 = u0 + w + 3;
        if (u1 > umax) return null;
        int y = s.y(u0, u1);
        int[] Fn = s.lines(u0, u1);
        int F = Fn[0], near = Fn[1];
        if (spanRange(s.st, u0, u1) > 2) return null;
        int D = depthFree(s, u0, u1, F, dp + 20);
        if (D < dp + 2) return null;
        ground("paving", s.rect(u0 + 2, u0 + 2 + w - 1, F, 0, dp - 1), y, 0, "front", s.facing);
        int wu = u0 + 2 + w / 2 + rng.choice(!small ? I(-4, 4) : I(-3, 3));
        int[] wxz = s.xz(wu, F, dp / 2);
        ornament("well", wxz[0], wxz[1], y, 1, "south");
        int back = dp;
        if (small) {
            int tu = u0 + 2 + w / 2 - (wu - (u0 + 2 + w / 2));
            int[] txz = s.xz(tu, F, dp / 2);
            ornament("tree", txz[0], txz[1], y, 2, "south");
            terrace(s, u0, u1, F, near, null, back, y, 1);
            take(s.rect(u0, u1, F, 0, back));
            return u1;
        }
        String[] lh = s.ends();
        Object[][] uf = {{u0 + 2, lh[0]}, {u0 + w - 1, lh[1]}};
        for (Object[] q : uf) {
            int ua = (int) q[0];
            for (int b = 2; b < dp - 4; b += 4)
                if (rng.random() < 0.75) ground("ornament", s.rect(ua, ua + 2, F, b, b + 2), y, ORN.get("stall"), "front", q[1]);
        }
        String what = rng.choice("stage", "pavilion", "pavilion", "none");
        if (what.equals("stage") && D >= dp + 1 + 9 + 5) {
            Map<String, Object> sd = building("backstage", "roof", rng.choice("xieshan", "juanpeng"), "podium", 2, "open", "front", "wealth", 0.8);
            int[] m = margins(sd, 1);
            int ss = m[0], sb = m[2];
            int sw = odd(Math.min(w - 2 * ss + 4, rng.choice(I(11, 13))));
            int b0 = dp + 1;
            int sx0 = u0 + 2 + (w - sw) / 2;
            place(sd, s.rect(sx0, sx0 + sw - 1, F, b0, b0 + 8), y, s.facing);
            back = b0 + 9 + sb;
        } else if (what.equals("pavilion") && D >= dp + 1 + 9 + 4) {
            Map<String, Object> pd = building("lookout", "roof", "pyramid", "open", "open", "podium", 1, "wealth", 0.8);
            int[] m = margins(pd, 1);
            int ps = m[0], pb = m[2];
            int pc = rng.choice(I(7, 9));
            int pu = u0 + 2 + (w / 2 - pc / 2) + rng.choice(I(-1, 1)) * (w / 4);
            pu = Math.max(u0 + ps, Math.min(u1 - ps - pc + 1, pu));
            int b0 = dp + 1;
            place(pd, s.rect(pu, pu + pc - 1, F, b0, b0 + pc - 1), y, s.facing, 1);
            back = b0 + pc + pb;
        }
        terrace(s, u0, u1, F, near, null, back, y, rng.random() < 0.5 ? 1 : mYard);
        take(s.rect(u0, u1, F, 0, back));
        return u1;
    }

    Integer gap(Side s, int u0, int umax) {
        int w = rng.randint(5, 9);
        int u1 = Math.min(umax, u0 + w - 1);
        if (u1 - u0 < 2) return null;
        int y = s.y(u0, u1);
        int[] Fn = s.lines(u0, u1);
        int F = Fn[0], near = Fn[1];
        int D = depthFree(s, u0, u1, F, rng.randint(8, 16));
        if (D < 3) return null;
        terrace(s, u0, u1, F, near, null, D, y, 1);
        take(s.rect(u0, u1, F, 0, D));
        if (u1 - u0 >= 4 && D >= 6) yardSpot(s, u0, u1, F, 1, D, y, wealth(s.xz(u0, F, 3)), true);
        return u1;
    }

    // -------------------------------------------------------------------------------------------- filling

    void fill(Side s, IntPredicate homesFrom) {
        Street st = s.st;
        int a0 = st.lo, a1 = st.hi;
        int u = a0;
        boolean lane = st.kind.equals("lane") || st.kind.equals("back");
        while (u <= a1 - 4) {
            int umax = runFree(s, u, a1);
            if (umax - u < 4) {
                u = umax + 2;
                continue;
            }
            String kind = "row";
            boolean dense = size.equals("big");
            if (rng.random() < (!lane ? 0.07 : 0.1) * (dense ? 0.3 : 1) || buildings.size() >= C.most) kind = "gap";
            int F0 = s.lines(u, u)[0];
            double w = wealth(s.xz(u, F0, 8)) + (st.kind.equals("main") || st.kind.equals("cross") ? 0.15 : 0);
            boolean homes = lane || (homesFrom != null && homesFrom.test(u)) || w < 0.35;
            if (kind.equals("row") && ward(w).equals("poor") && rng.random() < 0.45) kind = "hut";
            if (kind.equals("row") && ward(w).equals("rich") && (lane || homes) && rng.random() < 0.3 && !size.equals("village")) kind = "house";
            Integer x1 = null;
            switch (kind) {
                case "row" -> x1 = row(s, u, umax, homes);
                case "hut" -> x1 = hut(s, u, umax);
                case "house" -> x1 = compound(s, u, umax, false, false);
                case "gap" -> x1 = gap(s, u, umax);
            }
            if (kind.equals("gap") && buildings.size() >= C.most) {
                u = x1 != null ? x1 + 1 : u + 2;
                continue;
            }
            if (x1 == null && !kind.equals("row") && !kind.equals("hut")) x1 = row(s, u, umax, homes);
            if (x1 == null) x1 = hut(s, u, umax);
            if (x1 == null) x1 = gap(s, u, umax);
            if (x1 == null) {
                u += 2;
                continue;
            }
            u = x1 + 1;
        }
    }

    // -------------------------------------------------------------------------------------------- yards

    int furnish() {
        Set<Long> busy = new HashSet<>();
        for (Map<String, Object> r : buildings) {
            int[] q = reachRect(r, 1);
            for (int x = q[0]; x <= q[2]; x++) for (int z = q[1]; z <= q[3]; z++) busy.add(ck(x, z));
        }
        for (Map<String, Object> r : rooms) {
            Object sp = r.get("special");
            if (List.of("ornament", "paving", "gate", "wall", "paifang", "river").contains(sp))
                for (int x = gi(r, "x0") - 1; x < gi(r, "x1") + 2; x++) for (int z = gi(r, "z0") - 1; z < gi(r, "z1") + 2; z++) busy.add(ck(x, z));
        }
        for (Map.Entry<Long, String> e : claim.entrySet()) {
            String v = e.getValue();
            if (v.equals("street") || v.equals("river") || v.equals("wall")) {
                int x = cx(e.getKey()), z = cz(e.getKey());
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) busy.add(ck(x + dx, z + dz));
            }
        }
        HashMap<Long, Integer> level = new HashMap<>();
        List<Map<String, Object>> ts = new ArrayList<>();
        for (Map<String, Object> r : rooms) if ("terrace".equals(r.get("special"))) ts.add(r);
        ts.sort(Comparator.comparingLong(r -> -(long) (gi(r, "x1") - gi(r, "x0") + 1) * (gi(r, "z1") - gi(r, "z0") + 1)));
        for (Map<String, Object> r : ts)
            for (int x = gi(r, "x0"); x <= gi(r, "x1"); x++) for (int z = gi(r, "z0"); z <= gi(r, "z1"); z++) level.put(ck(x, z), gi(r, "y"));
        List<Street> sr = new ArrayList<>(streets);
        sr.addAll(rivers);
        for (Street st : sr)
            for (P p : st.pieces) {
                int[] q = st.rect(p);
                for (int x = q[0]; x <= q[2]; x++) for (int z = q[1]; z <= q[3]; z++) level.put(ck(x, z), p.y - (st.kind.equals("river") ? 100 : 0));
            }
        Set<Long> edge1 = new HashSet<>(), edge3 = new HashSet<>();
        for (Map.Entry<Long, Integer> e : level.entrySet()) {
            int x = cx(e.getKey()), z = cz(e.getKey());
            int y = e.getValue();
            boolean any = false;
            for (int dx = -1; dx <= 1 && !any; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    Integer v = level.get(ck(x + dx, z + dz));
                    if (v == null || v != y) {
                        any = true;
                        break;
                    }
                }
            if (any) {
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) edge1.add(ck(x + dx, z + dz));
                for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) edge3.add(ck(x + dx, z + dz));
            }
        }
        int placed = 0;
        for (Yard yd : yards) {
            Side s = yd.s;
            String ward = ward(yd.w);
            Set<Long> ok = new HashSet<>();
            for (int u = yd.u0 + 2; u < yd.u1 - 1; u++)
                for (int d = yd.d0; d <= yd.d1; d++) {
                    int[] xz = s.xz(u, yd.F, d);
                    long c = ck(xz[0], xz[1]);
                    if (!busy.contains(c) && !edge1.contains(c)) ok.add(c);
                }
            Object[][] kinds = switch (ward) {
                case "poor" -> new Object[][]{{"veg", 4}, {"hay", 3}, {"woodpile", 2}, {"tree", 1}, {"rack", 1}};
                case "mid" -> new Object[][]{{"veg", 3}, {"tree", 2}, {"woodpile", 2}, {"well", 1}, {"hay", 1}, {"bamboo", 1}, {"rack", 1}};
                default -> new Object[][]{{"tree", 4}, {"flowers", 3}, {"bamboo", 2}, {"pond", 1}, {"well", 1}};
            };
            if (yd.rich) kinds = new Object[][]{{"tree", 4}, {"flowers", 3}, {"pond", 2}, {"bamboo", 2}, {"lamp", 1}};
            int times = yd.few || size.equals("big") ? 1 : yd.garden ? 3 : rng.choice(I(1, 2));
            long[] wts = new long[kinds.length];
            for (int i = 0; i < wts.length; i++) wts[i] = (int) kinds[i][1];
            for (int it = 0; it < times; it++) {
                String kind = (String) kinds[rng.choicesIndex(wts, 1)[0]][0];
                int half = List.of("tree", "pond", "veg", "flowers").contains(kind) ? 2 : 1;
                List<Long> cells = fits(ok, half, kind.equals("pond") || kind.equals("veg"), edge3);
                if (cells.isEmpty() && half == 2) {
                    kind = Map.of("tree", "bamboo", "pond", "well", "veg", "hay", "flowers", "bamboo").get(kind);
                    half = 1;
                    cells = fits(ok, 1, false, edge3);
                }
                if (cells.isEmpty()) break;
                long c = rng.choice(cells);
                int ccx = cx(c), ccz = cz(c);
                ornament(kind, ccx, ccz, yd.y, half, s.facing);
                placed++;
                for (int dx = -half - 2; dx < half + 3; dx++) for (int dz = -half - 2; dz < half + 3; dz++) ok.remove(ck(ccx + dx, ccz + dz));
            }
        }
        return placed;
    }

    static List<Long> fits(Set<Long> ok, int h, boolean dig, Set<Long> edge3) {
        List<Long> out = new ArrayList<>();
        for (long c : ok) {
            int x = cx(c), z = cz(c);
            boolean all = true;
            for (int dx = -h; dx <= h && all; dx++) for (int dz = -h; dz <= h; dz++) if (!ok.contains(ck(x + dx, z + dz))) {
                all = false;
                break;
            }
            if (all && !(dig && edge3.contains(c))) out.add(c);
        }
        out.sort(CELL);
        return out;
    }

    // -------------------------------------------------------------------------------------------- the whole town

    Map<String, Object> plan() {
        switch (layout) {
            case "line" -> netLine();
            case "T" -> netT();
            case "cross" -> netCross();
            case "Y" -> netY();
            case "bank" -> netBank();
            case "grid" -> netGrid();
            default -> throw new IllegalArgumentException("AttributeError: 'Town' object has no attribute 'net_" + layout + "'");
        }
        if (box != null) radius = Math.max(radius, 0.4 * Math.max(box[0], box[1]));
        landmarks();
        for (int i = 0; i < later.size(); i++) later.get(i).run();
        List<Street> ss = new ArrayList<>(streets);
        ss.sort(Comparator.comparingInt(st -> KIND_ORDER.getOrDefault(st.kind, 4)));
        for (Street st : ss) for (int side : I(-1, 1)) fill(new Side(st, side), homesRule(st));
        if (walled) townWall(12);
        else {
            int[] bb = claimBox();
            plug(bb, 3);
        }
        furnish();
        layStreets();
        clipRivers();
        layRivers();
        mergeGround();
        resolve(rooms);
        return design();
    }

    int[] claimBox() {
        int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (Map.Entry<Long, String> e : claim.entrySet()) {
            if (e.getValue().equals("river")) continue;
            int x = cx(e.getKey()), z = cz(e.getKey());
            x0 = Math.min(x0, x);
            x1 = Math.max(x1, x);
            z0 = Math.min(z0, z);
            z1 = Math.max(z1, z);
        }
        if (x0 > x1) throw new IllegalArgumentException("min() arg is an empty sequence");
        return I(x0, z0, x1, z1);
    }

    // -------------------------------------------------------------------------------------------- the wall

    int bandOf(int z, int Z0) {
        for (int k = 0; k < bands.size(); k++) if (bands.get(k)[0] <= z && z <= bands.get(k)[1]) return k;
        return z < Z0 + 10 ? 0 : bands.size() - 1;
    }

    void townWall(int margin) {
        int[] cb = claimBox();
        int X0 = cb[0] - margin, X1 = cb[2] + margin, Z0 = cb[1] - margin, Z1 = cb[3] + margin;
        if ((X1 - X0) % 2 != 0) X1 += 1;
        if ((Z1 - Z0) % 2 != 0) Z1 += 1;
        int nb = (int) Math.max(1, Py.round((Z1 - Z0) / 36.0));
        int[] cuts = new int[nb + 1];
        for (int k = 0; k <= nb; k++) cuts[k] = Z0 + (int) Py.round((double) (k * (Z1 - Z0)) / nb);
        List<int[]> bands = new ArrayList<>();
        int[] mins = new int[nb], maxs = new int[nb];
        Arrays.fill(mins, Integer.MAX_VALUE);
        Arrays.fill(maxs, Integer.MIN_VALUE);
        for (Map.Entry<Long, String> e : claim.entrySet()) {
            if (e.getValue().equals("river")) continue;
            int x = cx(e.getKey()), z = cz(e.getKey());
            for (int k = 0; k < nb; k++) {
                int zb0 = cuts[k] + (k != 0 ? 1 : 0), zb1 = cuts[k + 1];
                if (zb0 - 10 <= z && z <= zb1 + 10) {
                    mins[k] = Math.min(mins[k], x);
                    maxs[k] = Math.max(maxs[k], x);
                }
            }
        }
        for (int k = 0; k < nb; k++) {
            int zb0 = cuts[k] + (k != 0 ? 1 : 0), zb1 = cuts[k + 1];
            boolean has = mins[k] <= maxs[k];
            bands.add(I(zb0, zb1, has ? mins[k] - margin : X0, has ? maxs[k] + margin : X1));
        }
        for (int it = 0; it < 2; it++)
            for (int i = 0; i + 1 < bands.size(); i++) {
                int[] a = bands.get(i), b = bands.get(i + 1);
                if (Math.abs(a[2] - b[2]) < 8) a[2] = b[2] = Math.min(a[2], b[2]);
                if (Math.abs(a[3] - b[3]) < 8) a[3] = b[3] = Math.max(a[3], b[3]);
            }
        for (End e : ends) {
            if (e.st.axis.equals("x")) {
                int zc = e.st.pieces.get(e.i).c;
                List<int[]> over = new ArrayList<>();
                for (int[] b : bands) if (b[0] - 14 <= zc && zc <= b[1] + 14) over.add(b);
                int loW = Integer.MAX_VALUE, hiE = Integer.MIN_VALUE;
                for (int[] b : over) {
                    loW = Math.min(loW, b[2]);
                    hiE = Math.max(hiE, b[3]);
                }
                if (over.isEmpty()) throw new IllegalArgumentException("min() arg is an empty sequence");
                for (int[] b : over) {
                    b[2] = loW;
                    b[3] = hiE;
                }
            }
        }
        this.bands = bands;
        X0 = Integer.MAX_VALUE;
        X1 = Integer.MIN_VALUE;
        for (int[] b : bands) {
            X0 = Math.min(X0, b[2]);
            X1 = Math.max(X1, b[3]);
        }
        wallRect = I(X0, Z0, X1, Z1);
        boolean big = size.equals("big");
        String wWalls = !fam.cn.equals("黄土") ? rng.choice("greybrick", "greybrick", "stone") : "mud";
        String wTheme = rng.choice("grey", "black");
        String wBase = rng.choice("greybrick", "rubble");
        if (fam.cn.equals("山村")) wWalls = "stone";
        int sh = big ? 9 : 7;
        Map<String, List<int[]>> gaps = new HashMap<>();
        gaps.put("n", new ArrayList<>());
        gaps.put("s", new ArrayList<>());
        for (int k = 0; k < bands.size(); k++) {
            gaps.put("w" + k, new ArrayList<>());
            gaps.put("e" + k, new ArrayList<>());
        }
        gates = new ArrayList<>();
        for (End e : ends) {
            Street st = e.st;
            int i = e.i;
            P p = st.pieces.get(i);
            boolean last = i == st.pieces.size() - 1;
            int out = last ? 1 : -1;
            int end = last ? p.a1 : p.a0;
            int wallC;
            String side;
            if (st.axis.equals("z")) {
                wallC = out > 0 ? Z1 - 2 : Z0 + 2;
                side = out > 0 ? "s" : "n";
            } else {
                int bk = bandOf(p.c, Z0);
                wallC = out > 0 ? bands.get(bk)[3] - 2 : bands.get(bk)[2] + 2;
                side = (out > 0 ? "e" : "w") + bk;
            }
            if (Math.abs(wallC - end) > 48) continue;
            int g0 = wallC - 6, g1 = wallC + 6;
            int aLo = out > 0 ? end + 1 : wallC - 2 - 6, aHi = out > 0 ? wallC + 2 + 6 : end - 1;
            int pc = p.c;
            Street ext = new Street(st.axis, aLo, aHi, a -> pc, st.w, st.kind, List.of(I(g0, g1)), List.of(), 3, 7);
            if (ext.pieces.isEmpty() || !freeStreet(ext, 0)) continue;
            int near = out > 0 ? 0 : ext.pieces.size() - 1;
            int gix = -1;
            for (int q = 0; q < ext.pieces.size(); q++) if (ext.pieces.get(q).fixed) {
                gix = q;
                break;
            }
            if (gix < 0) throw new java.util.NoSuchElementException("StopIteration");
            grade(ext, fx(near, p.y, gix, p.y));
            P gp = ext.pieces.get(gix);
            int gy = gp.y;
            boolean tower = big;
            if (tower) for (long c : cells(ext.rect(gp, 10 - st.hw))) if ("river".equals(claim.get(c))) {
                tower = false;
                break;
            }
            int half = tower ? 10 : 5;
            boolean blocked = false;
            for (long c : cells(ext.rect(gp, half - st.hw))) {
                String v = claim.get(c);
                if (v != null && !v.equals("river")) {
                    blocked = true;
                    break;
                }
            }
            if (blocked) continue;
            addStreet(ext);
            int c = p.c;
            String front = switch (side.substring(0, 1)) {
                case "s" -> "south";
                case "n" -> "north";
                case "e" -> "east";
                default -> "west";
            };
            Map<String, Object> d;
            int[] fr;
            if (tower) {
                int stn = rng.choice(I(1, 2));
                String rf = rng.choice("xieshan", "xieshan", "wudian");
                boolean de = rng.random() < 0.5;
                d = building("lookout", "storeys", stn, "roof", rf, "wealth", 0.9, "walls", !wWalls.equals("stone") ? wWalls : "greybrick",
                        "theme", wTheme, "podium", 8, "podium_style", "chengtai", "tunnels", 2, "double_eave", de, "base", wBase,
                        "horsehead", false, "veranda", false);
                List<Object> us = new ArrayList<>();
                us.add("guardroom");
                for (int q = 0; q < 7; q++) us.add("lookout");
                d.put("uses", us);
                fr = st.axis.equals("z") ? I(c - 7, wallC - 3, c + 7, wallC + 3) : I(wallC - 3, c - 7, wallC + 3, c + 7);
            } else {
                int stn = rng.choice(I(1, 1, 2));
                String rf = rng.choice("xieshan", "xieshan", "yingshan");
                d = building("guardroom", "storeys", stn, "roof", rf, "wealth", 0.9, "walls", wWalls, "theme", wTheme, "podium", 1,
                        "open", "through", "base", wBase, "horsehead", false, "veranda", false);
                fr = st.axis.equals("z") ? I(c - 5, wallC - 4, c + 5, wallC + 4) : I(wallC - 4, c - 5, wallC + 4, c + 5);
            }
            Map<String, Object> r = place(d, fr, gy, front, 5);
            r.put("_gate", true);
            gates.add(r);
            gaps.get(side).add(I(c - half, c + half));
            for (int kk : I(-1, 1)) {
                int a0 = kk > 0 ? g1 + 1 : g0 - 2, a1 = kk > 0 ? g1 + 2 : g0 - 1;
                int[] rect = st.axis.equals("z") ? I(c - half, a0, c + half, a1) : I(a0, c - half, a1, c + half);
                ground("terrace", rect, gy, 2);
                take(rect, "street");
            }
            int[] gr = st.axis.equals("z") ? I(c - half, g0, c + half, g1) : I(g0, c - half, g1, c + half);
            ground("terrace", gr, gy, 2);
            take(gr, "wall");
        }
        List<Object[]> runs = new ArrayList<>();
        runs.add(new Object[]{"n", "x", Z0 + 2, bands.get(0)[2], bands.get(0)[3]});
        runs.add(new Object[]{"s", "x", Z1 - 2, bands.get(bands.size() - 1)[2], bands.get(bands.size() - 1)[3]});
        for (int k = 0; k < bands.size(); k++) {
            int[] b = bands.get(k);
            int zb0 = b[0], zb1 = b[1], w = b[2], e = b[3];
            int a0 = k == 0 ? Z0 + 3 : zb0 - 3;
            int a1 = k == bands.size() - 1 ? Z1 - 3 : zb1 + 3;
            runs.add(new Object[]{"w" + k, "z", w + 2, a0, a1});
            runs.add(new Object[]{"e" + k, "z", e - 2, a0, a1});
            if (k + 1 < bands.size()) {
                int[] nb_ = bands.get(k + 1);
                if (nb_[2] != w) {
                    gaps.put("sw" + k, new ArrayList<>());
                    runs.add(new Object[]{"sw" + k, "x", zb1 + 1, Math.min(w, nb_[2]), Math.max(w, nb_[2]) + 4});
                }
                if (nb_[3] != e) {
                    gaps.put("se" + k, new ArrayList<>());
                    runs.add(new Object[]{"se" + k, "x", zb1 + 1, Math.min(e, nb_[3]) - 4, Math.max(e, nb_[3])});
                }
            }
        }
        for (Object[] rn : runs) {
            String side = (String) rn[0], axis = (String) rn[1];
            int cc = (int) rn[2], a0 = (int) rn[3], a1 = (int) rn[4];
            java.util.function.BiFunction<Integer, Integer, int[]> at = (a, kk) -> axis.equals("x") ? I(a, cc + kk) : I(cc + kk, a);
            List<int[]> wet = new ArrayList<>();
            int a = a0;
            while (a <= a1) {
                if (wetAt(at, a)) {
                    int b = a;
                    while (b + 1 <= a1 && wetAt(at, b + 1)) b++;
                    Integer my = null;
                    for (int t = a; t <= b; t++)
                        for (int kk : I(-2, 0, 2)) {
                            Integer v = riverY(at.apply(t, kk));
                            if (v != null) my = my == null ? v : Math.min(my, v);
                        }
                    if (my == null) throw new IllegalArgumentException("min() arg is an empty sequence");
                    wet.add(I(a, b, my));
                    a = b + 1;
                } else a++;
            }
            List<int[]> pieces = new ArrayList<>();
            List<int[]> gl = new ArrayList<>(gaps.get(side));
            for (int[] wt : wet) gl.add(I(wt[0], wt[1]));
            gl.sort(Siteplan.LEX);
            for (int[] pp : Siteplan.gapPieces(a0, a1, gl)) {
                if (pp[1] - pp[0] < 2) continue;
                pieces.addAll(Siteplan.split(pp[0], pp[1] + ((pp[1] - pp[0]) % 2 == 0 ? 0 : 1), 31));
            }
            for (int[] wt : wet) pieces.add(I(wt[0], wt[1] + Math.floorMod(wt[1] - wt[0], 2), wt[2]));
            for (int[] pc : pieces) {
                int q0 = pc[0], q1 = pc[1];
                if (q1 < q0) continue;
                int[] rect = axis.equals("x") ? I(q0, cc - 2, q1, cc + 2) : I(cc - 2, q0, cc + 2, q1);
                int y;
                if (pc.length == 3) y = pc[2];
                else {
                    double[] hs = new double[(q1 - q0 + 1) * 3];
                    int n = 0;
                    for (int t = q0; t <= q1; t++)
                        for (int kk : I(-2, 0, 2)) {
                            int[] xz = at.apply(t, kk);
                            hs[n++] = H.h(xz[0], xz[1]);
                        }
                    y = (int) Py.round(Py.sum(hs) / hs.length - 0.5);
                    ground("terrace", rect, y, 0);
                }
                ground("wall", rect, y, 0, "front", axis.equals("x") ? "south" : "east", "sh", sh, "walls", wWalls, "theme", wTheme, "base", wBase);
                take(rect, "wall");
            }
        }
        final int fZ0 = Z0, fZ1 = Z1;
        java.util.function.BiPredicate<Integer, Integer> inside = (x, z) -> {
            int[] b = bands.get(bandOf(z, fZ0));
            if (!(fZ0 + 5 <= z && z <= fZ1 - 5 && b[2] + 5 <= x && x <= b[3] - 5)) return false;
            for (int q = 0; q < bands.size() - 1; q++) if (Math.abs(z - (bands.get(q)[1] + 1)) <= 2) return false;
            return true;
        };
        fillFree(I(X0 + 5, Z0 + 5, X1 - 5, Z1 - 5), 48, 1.5, inside);
        plug(I(X0 + 5, Z0 + 5, X1 - 5, Z1 - 5), 3);
    }

    boolean wetAt(java.util.function.BiFunction<Integer, Integer, int[]> at, int a) {
        for (int kk : I(-2, 0, 2)) {
            int[] c = at.apply(a, kk);
            if ("river".equals(claim.get(ck(c[0], c[1])))) return true;
        }
        return false;
    }

    void plug(int[] rect, int reach) {
        int X0 = rect[0], Z0 = rect[1], X1 = rect[2], Z1 = rect[3];
        HashMap<Long, Integer> level = new HashMap<>();
        for (Map<String, Object> r : rooms) {
            Object sp = r.get("special");
            if ("terrace".equals(sp) || "wall".equals(sp) || "paving".equals(sp))
                for (int x = gi(r, "x0"); x <= gi(r, "x1"); x++) for (int z = gi(r, "z0"); z <= gi(r, "z1"); z++) level.putIfAbsent(ck(x, z), gi(r, "y"));
        }
        List<Street> sr = new ArrayList<>(streets);
        sr.addAll(rivers);
        for (Street st : sr)
            for (P p : st.pieces) {
                int[] q = st.rect(p);
                for (int x = q[0]; x <= q[2]; x++) for (int z = q[1]; z <= q[3]; z++) level.put(ck(x, z), p.y);
            }
        java.util.TreeMap<Long, Integer> fill = new java.util.TreeMap<>(CELL);
        for (int x = X0; x <= X1; x++)
            for (int z = Z0; z <= Z1; z++) {
                long c = ck(x, z);
                if (level.containsKey(c) || claim.containsKey(c)) continue;
                Integer best = null;
                for (int[] d : new int[][]{{1, 0}, {0, 1}}) {
                    Integer a = null, b = null;
                    for (int k = 1; k <= reach; k++) {
                        Integer v = level.get(ck(x + k * d[0], z + k * d[1]));
                        if (v != null) {
                            a = v;
                            break;
                        }
                    }
                    for (int k = 1; k <= reach; k++) {
                        Integer v = level.get(ck(x - k * d[0], z - k * d[1]));
                        if (v != null) {
                            b = v;
                            break;
                        }
                    }
                    if (a != null && b != null) {
                        int y = Math.min(a, b);
                        best = best == null ? y : Math.min(best, y);
                    }
                }
                if (best != null) fill.put(c, best);
            }
        Set<Long> done = new HashSet<>();
        for (Map.Entry<Long, Integer> e : fill.entrySet()) {
            long c0 = e.getKey();
            if (done.contains(c0)) continue;
            int x = cx(c0), z = cz(c0), y = e.getValue();
            int z1 = z;
            while (eqY(fill.get(ck(x, z1 + 1)), y) && !done.contains(ck(x, z1 + 1))) z1++;
            int x1 = x;
            while (true) {
                boolean all = true;
                for (int zz = z; zz <= z1; zz++)
                    if (!(eqY(fill.get(ck(x1 + 1, zz)), y) && !done.contains(ck(x1 + 1, zz)))) {
                        all = false;
                        break;
                    }
                if (!all) break;
                x1++;
            }
            for (int xx = x; xx <= x1; xx++) for (int zz = z; zz <= z1; zz++) done.add(ck(xx, zz));
            ground("terrace", I(x, z, x1, z1), y, 0);
            take(I(x, z, x1, z1));
        }
    }

    static boolean eqY(Integer v, int y) {
        return v != null && v == y;
    }

    Integer riverY(int[] c) {
        for (Street rv : rivers)
            for (P p : rv.pieces) {
                int[] q = rv.rect(p);
                if (q[0] <= c[0] && c[0] <= q[2] && q[1] <= c[1] && c[1] <= q[3]) return p.y;
            }
        return null;
    }

    void fillFree(int[] rect, int most, double tol, java.util.function.BiPredicate<Integer, Integer> inside) {
        int X0 = rect[0], Z0 = rect[1], X1 = rect[2], Z1 = rect[3];
        Side fake = new Side(field(), -1);
        Set<Long> done = new HashSet<>();
        List<Long> placed = new ArrayList<>();
        for (int z = Z0; z <= Z1; z++)
            for (int x = X0; x <= X1; x++) {
                long c = ck(x, z);
                if (claim.containsKey(c) || done.contains(c) || (inside != null && !inside.test(x, z))) continue;
                double h0 = H.h(x, z);
                int fz = z;
                java.util.function.BiPredicate<Integer, Integer> ok = (xx, zz) -> X0 <= xx && xx <= X1 && zz <= Z1 && !claim.containsKey(ck(xx, zz))
                        && !done.contains(ck(xx, zz)) && Math.abs(H.h(xx, zz) - h0) <= tol && (inside == null || inside.test(xx, zz));
                int x1 = x;
                while (x1 + 1 - x < most && ok.test(x1 + 1, z)) x1++;
                int z1 = z;
                while (z1 + 1 - z < most) {
                    boolean all = true;
                    for (int xx = x; xx <= x1; xx++) if (!ok.test(xx, z1 + 1)) {
                        all = false;
                        break;
                    }
                    if (!all) break;
                    z1++;
                }
                int[] r = I(x, z, x1, z1);
                List<Long> cs = cells(r);
                done.addAll(cs);
                if (cs.size() < 40) continue;
                double[] hs = new double[cs.size()];
                for (int i = 0; i < hs.length; i++) hs[i] = H.h(cx(cs.get(i)), cz(cs.get(i)));
                int y = (int) Py.round(Py.sum(hs) / cs.size());
                placed.addAll(cs);
                ground("terrace", r, y, 1);
                if ((x1 - x) >= 6 && (z1 - z) >= 6 && rng.random() < 0.5) {
                    Yard yd = new Yard();
                    yd.s = fake;
                    yd.F = z1;
                    yd.u0 = x - 1;
                    yd.u1 = x1 + 1;
                    yd.d0 = 1;
                    yd.d1 = z1 - z - 1;
                    yd.y = y;
                    yd.w = rng.random() < 0.7 ? 0.2 : 0.5;
                    yd.few = true;
                    yards.add(yd);
                }
            }
        for (long c : placed) claim.putIfAbsent(c, "plot");
    }

    IntPredicate homesRule(Street st) {
        if (!st.kind.equals("main")) return null;
        int span = st.hi - st.lo;
        return u -> u < st.lo + span * 0.2 || u > st.hi - span * 0.12;
    }

    void landmarks() {
        Street main = this.main;
        String far = new String[]{"paifang", "temple", "open"}[rng.choicesIndex(new long[]{35, 40, 25}, 1)[0]];
        boolean templeDone = size.equals("village");
        if (head != null && !size.equals("village")) templeDone = headCompound((Street) head[0], 0, -1, true, false);
        else if (layout.equals("Y")) forkTree();
        if (!templeDone && far.equals("temple") && (layout.equals("line") || layout.equals("cross")) && !size.equals("big") && box == null)
            templeDone = endCompound(main, 0, true);
        int span = W <= 5 ? 12 : 14;
        for (End e : ends) {
            if (walled) break;
            if (e.name.equals("entrance") || (e.name.equals("far") && far.equals("paifang"))) {
                Street st = e.st;
                P p = st.pieces.get(e.i);
                int a = e.i == st.pieces.size() - 1 ? p.a1 - 3 : p.a0 + 3;
                P pc = st.pieceAt(a);
                int half = Math.min(span, st.hw + 9);
                while (half > st.hw + 4 && anyRiver(st.rect(new P(a - 3, a + 3, pc.c, 0), half + 3 - st.hw))) half--;
                int[] apron = st.rect(new P(a - 3, a + 3, pc.c, 0), half + 3 - st.hw);
                if (anyRiver(apron)) continue;
                ground("terrace", apron, pc.y, 2);
                take(apron, "street");
                ground("paifang", st.rect(new P(a - 2, a + 2, pc.c, 0), half - st.hw), pc.y, 0, "front", st.axis.equals("z") ? "south" : "east",
                        "sh", rng.choice(I(6, 7, 8)));
            }
        }
        int mid = Math.floorDiv(main.lo + main.hi, 2);
        if (layout.equals("cross")) mid = crossing[1];
        List<Integer> sides = new ArrayList<>(List.of(-1, 1));
        rng.shuffle(sides);
        if (layout.equals("bank")) sides = List.of(1, 1);
        Map<Integer, List<Object[]>> plans = new HashMap<>();
        plans.put(-1, new ArrayList<>());
        plans.put(1, new ArrayList<>());
        if (!templeDone) plans.get(sides.get(0)).add(new Object[]{mid + rng.randint(-25, 10), "temple"});
        else if (size.equals("village")) plans.get(sides.get(0)).add(new Object[]{mid + rng.randint(-20, 20), "shrine"});
        plans.get(sides.get(1)).add(new Object[]{mid + rng.randint(-15, 15), "square"});
        if (size.equals("big") || (size.equals("town") && rng.random() < 0.3)) yamen();
        if (!templeDone && gridSites != null)
            for (Object[] ks : gridSites)
                if (ks[0].equals("temple") && siteCompound((Side) ks[1], true, false)) templeDone = true;
        int ninn = !size.equals("village") ? rng.choice(I(1, 1, 2)) : rng.choice(I(0, 0, 1));
        for (int it = 0; it < ninn; it++) {
            List<Object[]> l = plans.get(rng.choice(I(-1, 1)));
            l.add(new Object[]{rng.randint(main.lo + 20, Math.max(main.lo + 21, main.hi - 20)), "inn"});
        }
        if (!size.equals("village"))
            for (int side : I(-1, 1)) if (rng.random() < 0.5) plans.get(side).add(new Object[]{main.lo + 4, "house"});
        for (int side : I(-1, 1)) {
            Side s = new Side(main, side);
            List<Object[]> pl = new ArrayList<>(plans.get(side));
            pl.sort((p1, p2) -> {
                int c = Integer.compare((int) p1[0], (int) p2[0]);
                return c != 0 ? c : ((String) p1[1]).compareTo((String) p2[1]);
            });
            for (Object[] uk : pl) {
                int u = (int) uk[0];
                String kind = (String) uk[1];
                for (int du : I(0, -8, 8, -16, 16, -24, 24, -32, 32)) {
                    int uu = Math.max(main.lo + 2, Math.min(main.hi - 10, u + du));
                    int umax = runFree(s, uu, main.hi);
                    if (umax - uu < 12) continue;
                    Integer r = switch (kind) {
                        case "temple" -> compound(s, uu, umax, true, false);
                        case "square" -> square(s, uu, umax, size.equals("village"));
                        case "inn" -> inn(s, uu, umax);
                        case "shrine" -> shrine(s, uu, umax);
                        default -> compound(s, uu, umax, false, false);
                    };
                    if (r != null) break;
                }
            }
        }
    }

    boolean anyRiver(int[] rect) {
        for (int x = rect[0]; x <= rect[2]; x++) for (int z = rect[1]; z <= rect[3]; z++) if ("river".equals(claim.get(ck(x, z)))) return true;
        return false;
    }

    boolean yamen() {
        List<Side> cands = new ArrayList<>();
        if (gridSites != null) for (Object[] ks : gridSites) if (ks[0].equals("yamen")) cands.add((Side) ks[1]);
        for (Street st : streets) if (st.axis.equals("x") && (st.kind.equals("cross") || st.kind.equals("main"))) cands.add(new Side(st, -1));
        cands.add(new Side(main, 1));
        cands.add(new Side(main, -1));
        for (Side s : cands) if (siteCompound(s, false, true)) return true;
        return false;
    }

    boolean siteCompound(Side s, boolean temple, boolean yamen) {
        Street st = s.st;
        int mid = st.axis.equals("x") ? centre[0] : centre[1];
        List<Integer> us = new ArrayList<>();
        for (int u = st.lo + 2; u < st.hi - 20; u += 2) us.add(u);
        us.sort(Comparator.comparingInt(u -> Math.abs(u + 20 - mid)));
        for (int u : us.subList(0, Math.min(40, us.size()))) {
            int umax = runFree(s, u, st.hi);
            if (umax - u < 28) continue;
            for (int it = 0; it < 3; it++) if (compound(s, u, umax, temple, yamen) != null) return true;
        }
        return false;
    }

    void forkTree() {
        int[] r0 = forkRect;
        int y = forkY;
        int cxv = Math.floorDiv(r0[0] + r0[2], 2);
        int[] rect = I(cxv - 3, r0[1] - 6, cxv + 3, r0[1] - 1);
        if (free(rect)) {
            ornament("tree", cxv, r0[1] - 3, y, 2, "south");
            ground("terrace", rect, y, 1);
            take(rect);
        }
    }

    void layStreets() {
        for (Street st : streets) {
            int var = List.of("main", "cross", "branch", "bank").contains(st.kind) ? mStreet : mLane;
            P run = null;
            List<P> all = new ArrayList<>(st.pieces);
            all.add(null);
            for (P p : all) {
                if (run != null && p != null && p.y == run.y && p.c == run.c && p.a1 - run.a0 < 80) {
                    run.a1 = p.a1;
                    continue;
                }
                if (run != null) ground("street", st.rect(run), run.y, var, "front", st.axis.equals("z") ? "south" : "east");
                run = p != null ? p.copy() : null;
            }
        }
        if (fork != null) ground("street", forkRect, forkY, mStreet);
    }

    Map<String, Object> design() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r0 : rooms) {
            Map<String, Object> r = new LinkedHashMap<>(r0);
            r.keySet().removeIf(k -> k.startsWith("_"));
            out.add(r);
        }
        int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z0 = Integer.MAX_VALUE, z1 = Integer.MIN_VALUE;
        for (Map<String, Object> r : out) {
            x0 = Math.min(x0, gi(r, "x0"));
            x1 = Math.max(x1, gi(r, "x1"));
            z0 = Math.min(z0, gi(r, "z0"));
            z1 = Math.max(z1, gi(r, "z1"));
        }
        int nb = 0;
        for (Map<String, Object> r : rooms) if (!Dyn.truthy(r.get("special")) && !Dyn.truthy(r.get("_gate"))) nb++;
        String cn = Map.of("village", "村", "town", "镇", "big", "城").get(size);
        List<Map<String, Object>> rs = GROUP ? groupGround(out, 250) : out;
        return kw("name", "凡人城镇·" + fam.cn + cn, "kind", "town", "jin", 0, "seed", Siteplan.num(seed), "tier", "mortal", "look", 0,
                "size", new ArrayList<>(List.of(x1 - x0 + 1, z1 - z0 + 1)), "rooms", rs, "terrain", true, "anchored", anchored,
                "origin", new ArrayList<>(List.of(0, 0)), "y_ref", main.pieceAt(0).y, "buildings", nb, "town_size", size, "layout", layout,
                "box", box != null ? new ArrayList<>(List.of(box[0], box[1])) : null);
    }

    // ------------------------------------------------------------------------------------------ compose.group_ground

    static final List<String> PATCH_SHAPES = List.of("terrace", "street", "stairs", "paving", "river", "bridge");

    /** compose.group_ground: the ground pieces gathered into 地块 rooms ("parts", each at most `most` a side). */
    public static List<Map<String, Object>> groupGround(List<Map<String, Object>> rooms, int most) {
        List<Object> out = new ArrayList<>();
        List<Object[]> groups = new ArrayList<>();   // {bb int[4], List rooms}
        for (Map<String, Object> r : rooms) {
            String k = Siteplan.kind(r);
            if (!PATCH_SHAPES.contains(k) || Dyn.truthy(r.get("parts"))) {
                out.add(r);
                continue;
            }
            int x0 = Math.min(gi(r, "x0"), gi(r, "x1")), z0 = Math.min(gi(r, "z0"), gi(r, "z1"));
            int x1 = Math.max(gi(r, "x0"), gi(r, "x1")), z1 = Math.max(gi(r, "z0"), gi(r, "z1"));
            boolean put = false;
            for (Object[] g : groups) {
                int[] bb = (int[]) g[0];
                int a0 = Math.min(bb[0], x0), b0 = Math.min(bb[1], z0), a1 = Math.max(bb[2], x1), b1 = Math.max(bb[3], z1);
                if (a1 - a0 < most && b1 - b0 < most) {
                    g[0] = I(a0, b0, a1, b1);
                    ((List<Map<String, Object>>) g[1]).add(r);
                    put = true;
                    break;
                }
            }
            if (!put) {
                Object[] g = {I(x0, z0, x1, z1), new ArrayList<>(List.of(r))};
                groups.add(g);
                out.add(g);
            }
        }
        List<Map<String, Object>> res = new ArrayList<>();
        for (Object item : out) {
            if (item instanceof Map<?, ?> m) {
                res.add((Map<String, Object>) m);
                continue;
            }
            Object[] g = (Object[]) item;
            List<Map<String, Object>> rs = (List<Map<String, Object>>) g[1];
            if (rs.size() == 1) {
                res.add(rs.get(0));
                continue;
            }
            int[] bb = (int[]) g[0];
            int X0 = bb[0], Z0 = bb[1], X1 = bb[2], Z1 = bb[3];
            int y = Integer.MAX_VALUE;
            for (Map<String, Object> p : rs) y = Math.min(y, gi(p, "y"));
            List<Object> parts = new ArrayList<>();
            for (Map<String, Object> p : rs) {
                Map<String, Object> q = kw("s", Siteplan.kind(p), "a0", Math.min(gi(p, "x0"), gi(p, "x1")) - X0, "b0", Math.min(gi(p, "z0"), gi(p, "z1")) - Z0,
                        "a1", Math.max(gi(p, "x0"), gi(p, "x1")) - X0, "b1", Math.max(gi(p, "z0"), gi(p, "z1")) - Z0, "dy", gi(p, "y") - y,
                        "f", Compose.FRONT_I.getOrDefault(p.containsKey("front") ? (String) p.get("front") : "south", 0));
                for (String kk : Compose.PATCH_KEYS) {
                    Object v = p.get(kk);
                    if (v != null && !"".equals(v) && !(v instanceof Number n && n.doubleValue() == 0) && !Boolean.FALSE.equals(v)) q.put(kk, v);
                }
                parts.add(q);
            }
            Map<String, Object> first = rs.get(0);
            res.add(kw("x0", X0, "z0", Z0, "x1", X1, "z1", Z1, "y", y, "special", "patch", "shape", "patch", "front", "south", "parts", parts,
                    "storeys", 1, "roof", "xieshan", "walls", first.containsKey("walls") ? first.get("walls") : "greybrick",
                    "theme", first.containsKey("theme") ? first.get("theme") : "grey", "base", first.containsKey("base") ? first.get("base") : "auto",
                    "seed", first.containsKey("seed") ? first.get("seed") : 0, "variant", 0, "roof_variant", 0, "uses", Siteplan.rep("main_room", 8),
                    "sh", 0, "podium", 0, "podium_style", "plain", "open", "closed", "round", false, "stack", false));
        }
        return res;
    }

    // ------------------------------------------------------------------------------------------ entry

    static int[] townBox(Object box) {
        if (!Dyn.truthy(box)) return null;
        List<Object> parts;
        if (box instanceof String s) {
            parts = new ArrayList<>(Arrays.asList((Object[]) s.toLowerCase().replace("×", "x").replace("*", "x").split("x", -1)));
        } else parts = (List<Object>) box;
        if (parts.size() != 2) throw new IllegalArgumentException("ValueError: town box: two sides");
        int w = Dyn.toInt(parts.get(0)), d = Dyn.toInt(parts.get(1));
        if (w <= 0 || d <= 0) throw new IllegalArgumentException("town box " + w + "x" + d + ": both sides above 0");
        return I(w, d);
    }

    public static Map<String, Object> town(long seed, Object heights, String size, String layout, Object box0) {
        int[] box = townBox(box0);
        if (size != null && size.isEmpty()) size = null;
        if (size != null && !CLASSES.containsKey(size)) throw new IllegalArgumentException("town size '" + size + "': one of village, town, big");
        Town t = new Town(seed, heights, size, layout, 0, box);
        Map<String, Object> best = t.plan();
        for (int k = 1; k < 6; k++) {
            if ((int) best.get("buildings") >= t.least) break;
            Town t2 = new Town(seed, heights, t.size, t.layout, k, box);
            Map<String, Object> d = t2.plan();
            if ((int) d.get("buildings") > (int) best.get("buildings")) best = d;
        }
        return best;
    }
}
