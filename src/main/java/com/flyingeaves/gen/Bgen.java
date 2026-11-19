package com.flyingeaves.gen;

import com.flyingeaves.gen.py.IntPair;
import com.flyingeaves.gen.py.Py;
import com.flyingeaves.gen.py.PyRandom;
import com.flyingeaves.gen.py.PySet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.flyingeaves.gen.Arch.H;
import static com.flyingeaves.gen.Arch.Pal;
import static com.flyingeaves.gen.Arch.roof;
import static com.flyingeaves.gen.Blocks.AIR;
import static com.flyingeaves.gen.Blocks.B;
import static com.flyingeaves.gen.Blocks.slab;
import static com.flyingeaves.gen.Blocks.stair;

/**
 * bgen.py: the building generator - roofs (roof_for), storeys, walls from wall-bay parts, stairs, rooms, lights,
 * platforms, and the finishing passes (walkout, clear_doorways, settle_loose). Ported in pieces; see PORTING.md.
 */
public final class Bgen {
    private Bgen() {
    }

    public static final List<String> TIERS = List.of("mortal", "lianqi", "zhuji", "jindan", "yuanying", "huashen");
    public static final Map<String, Double> TIER_K = Map.of("mortal", 1.0, "lianqi", 1.1, "zhuji", 1.3, "jindan", 1.7, "yuanying", 2.5, "huashen", 3.6);
    public static final LinkedHashMap<String, Map<String, Object>> WALLS = new LinkedHashMap<>();
    public static final Map<String, Object> K = Tables.KINDS;

    static void load() {
        WALLS.clear();
        WALLS.putAll(Materials.walls());
    }

    // ------------------------------------------------------------------------------------------------ helpers

    static int odd(double n) {
        int r = (int) Py.round(n);
        return Math.floorMod(r, 2) != 0 ? r : r + 1;
    }

    @SuppressWarnings("unchecked")
    static <T> T pick(PyRandom rng, Object seq) {
        List<Object> l = (List<Object>) seq;
        return (T) l.get((int) rng.randrange(l.size()));
    }

    static Map<String, Object> setWalls(String name) {
        Map<String, Object> w = WALLS.get(name);
        if (w == null) throw new IllegalArgumentException("KeyError: '" + name + "'");
        Pal.COL = (String) w.get("post");
        return w;
    }

    static String ws(Map<String, Object> W, String k) {
        return (String) W.get(k);
    }

    static final Map<Integer, Double> PITCH = Map.of(1, 0.35, 2, 0.55, 3, 0.8, 4, 1.0);

    static int band(int sh) {
        return 2;
    }

    static final Map<Integer, Integer> EAVE_OUT = Map.of(1, 1, 2, 2, 3, 3, 4, 4);
    static final Map<Integer, Integer> LIFT = Map.of(1, 0, 2, 1, 3, 2, 4, 3);
    static final Map<Integer, Integer> BEAST_N = Map.of(1, 0, 2, 3, 3, 5, 4, 7, 5, 9);
    static final Map<Integer, Integer> BRACKET_L = Map.of(1, 0, 2, 1, 3, 2, 4, 3);

    /** CAIHUA: style -> (body pair, extra key, extra block). */
    static final Map<String, String[]> CAIHUA = new LinkedHashMap<>();

    static {
        CAIHUA.put("xuanzi", new String[]{"blue_concrete", "cyan_concrete", "flower", "light_blue_glazed_terracotta"});
        CAIHUA.put("hexi", new String[]{"blue_concrete", "cyan_concrete", "gold", "gold_block"});
        CAIHUA.put("sushi", new String[]{"blue_concrete", "cyan_concrete", "panel", "smooth_quartz"});
    }

    /** bgen.caihua_cell. */
    static String caihuaCell(String style, int j, int m, int par) {
        String[] st = CAIHUA.get(style);
        String body = st[par], other = st[1 - par];
        if (style.equals("xuanzi")) {
            if (j == 0 || j == m - 1) return other;
            if (m >= 5 && (j == 1 || j == m - 2)) return st[3];
            return body;
        }
        if (style.equals("hexi")) {
            if (j == 0 || j == m - 1 || (m >= 5 && j == Math.floorDiv(m, 2))) return st[3];
            return body;
        }
        int third = Math.max(1, Math.floorDiv(m, 3));
        int lo = Math.floorDiv(m - third, 2);
        if (m >= 3 && lo <= j && j < lo + third) return st[3];
        return (j == 0 || j == m - 1) && m >= 4 ? other : body;
    }

    interface At {
        int[] at(int a);
    }

    /** bgen.paint_ring. */
    static void paintRing(Fr f, String style, int hx, int hz, int bay, int y) {
        Object[][] sides = {{hx, (At) a -> new int[]{a, hz}}, {hx, (At) a -> new int[]{a, -hz}}, {hz, (At) a -> new int[]{-hx, a}},
                {hz, (At) a -> new int[]{hx, a}}};
        for (Object[] sd : sides) {
            int limit = (int) sd[0];
            At at = (At) sd[1];
            List<List<Integer>> segs = new ArrayList<>();
            List<Integer> cur = new ArrayList<>();
            for (int a = -limit; a <= limit; a++) {
                if (Math.abs(a) == limit || Math.floorMod(a, bay) == 0) {
                    if (!cur.isEmpty()) segs.add(cur);
                    cur = new ArrayList<>();
                } else cur.add(a);
            }
            for (int i = 0; i < segs.size(); i++) {
                List<Integer> seg = segs.get(i);
                int par = Math.floorMod((int) Math.abs(i - (segs.size() - 1) / 2.0), 2);
                for (int j = 0; j < seg.size(); j++) {
                    String blk = caihuaCell(style, j, seg.size(), par);
                    if (Env.present("minecraft:" + blk)) {
                        int[] uv = at.at(seg.get(j));
                        f.put(uv[0], y, uv[1], B(blk));
                    }
                }
            }
        }
    }

    /** bgen.cap_roof: returns the flat's y. rect = (u0, v0, u1, v1). */
    static int capRoof(Fr f, H h, int[] rect, String rail) {
        int u0 = rect[0], v0 = rect[1], u1 = rect[2], v1 = rect[3];
        Double mn = null;
        for (int u = u0 - 1; u < u1 + 2; u++)
            for (int v = v0 - 1; v < v1 + 2; v++)
                if ((u == u0 - 1 || u == u1 + 1 || v == v0 - 1 || v == v1 + 1) && h.at(u, v) != null) {
                    double val = h.at(u, v);
                    if (mn == null || val < mn) mn = val;
                }
        int flat = (int) Math.floor(mn != null ? mn : h.max());
        PySet<IntPair> high = new PySet<>();
        for (Map.Entry<Long, Double> e : h.entrySet())
            if (Math.floor(e.getValue()) >= flat) high.add(IntPair.of(Canvas.cx(e.getKey()), Canvas.cz(e.getKey())));
        List<IntPair> todo = new ArrayList<>();
        for (IntPair c : high) if (u0 <= c.a() && c.a() <= u1 && v0 <= c.b() && c.b() <= v1) todo.add(c);
        PySet<IntPair> cells = PySet.of(todo);
        while (!todo.isEmpty()) {
            IntPair c0 = todo.remove(todo.size() - 1);
            for (int[] d : N4) {
                IntPair c = IntPair.of(c0.a() + d[0], c0.b() + d[1]);
                if (high.contains(c) && !cells.contains(c)) {
                    cells.add(c);
                    todo.add(c);
                }
            }
        }
        for (IntPair c : cells) {
            int u = c.a(), v = c.b();
            f.put(u, flat, v, B(roof("full")));
            boolean edge = false;
            for (int[] d : N4) if (!cells.contains(IntPair.of(u + d[0], v + d[1]))) edge = true;
            if (edge && !(u0 <= u && u <= u1 && v0 <= v && v <= v1)) f.put(u, flat + 1, v, B(rail));
        }
        return flat;
    }

    static final int[][] N4 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** bgen.capped. */
    static H capped(H h, int[] rect) {
        int u0 = rect[0], v0 = rect[1], u1 = rect[2], v1 = rect[3];
        Double mn = null;
        for (int u = u0 - 1; u < u1 + 2; u++)
            for (int v = v0 - 1; v < v1 + 2; v++)
                if ((u == u0 - 1 || u == u1 + 1 || v == v0 - 1 || v == v1 + 1) && h.at(u, v) != null) {
                    double val = h.at(u, v);
                    if (mn == null || val < mn) mn = val;
                }
        if (mn == null) return h;
        int flat = (int) Math.floor(mn);
        H out = new H();
        for (Map.Entry<Long, Double> e : h.entrySet()) out.put(e.getKey(), Math.min(e.getValue(), flat + 0.99));
        return out;
    }

    /** bgen.ride_beasts. */
    static void rideBeasts(Fr f, H h, List<List<int[]>> hips, int n) {
        String beast = Dyn.truthy(Arch.ROOF.get("beast")) ? roof("beast") : "polished_blackstone_button";
        for (List<int[]> cells0 : hips) {
            List<int[]> cells = new ArrayList<>();
            for (int[] c : cells0) if (h.at(c[0], c[1]) != null) cells.add(c);
            for (int i = 0; i < Math.min(n + 1, cells.size() - 1); i++) {
                int[] c = cells.get(i);
                f.put(c[0], (int) Math.floor(h.at(c[0], c[1])) + 2, c[1], B(beast, "face", "floor", "facing", "south"));
            }
        }
    }

    /** A height field's keys as (u, v) pairs in its order (for set(h): CPython's dict-keys construction). */
    static LinkedHashMap<IntPair, Double> pairKeys(H h) {
        LinkedHashMap<IntPair, Double> m = new LinkedHashMap<>();
        for (Map.Entry<Long, Double> e : h.entrySet()) m.put(IntPair.of(Canvas.cx(e.getKey()), Canvas.cz(e.getKey())), e.getValue());
        return m;
    }

    /**
     * bgen.roof_for: the main roof over |u| <= hx, |v| <= hz with wall plates at topY; returns the ridge y.
     * cap: null or (rect, rail).
     */
    static int roofFor(Fr f, int hx, int hz, int topY, String form, PyRandom rng, int overhang, double steep, boolean corner, double tierK,
                       PyRandom vary, int pitch, int eaveOut, int lift, int extraOut, int beasts, Object[] cap, Integer minY) {
        List<List<int[]>> hips = new ArrayList<>();
        int ridgeH = tierK > 1 ? 3 : 2;
        boolean liftLess = false;
        if (vary != null) {
            double dsteep = vary.choice(List.of(-0.1, 0.0, 0.1, 0.2));
            int dout = vary.choice(List.of(-1, 0, 0, 1));
            if (pitch == 0) steep = Math.max(0.3, steep + dsteep);
            if (eaveOut == 0) overhang = Math.max(1, overhang + dout);
            ridgeH = vary.choice(List.of(2, 3));
            liftLess = vary.random() < 0.3;
        }
        if (pitch != 0) steep = PITCH.getOrDefault(pitch, steep);
        if (eaveOut != 0) overhang = EAVE_OUT.getOrDefault(eaveOut, overhang);
        overhang += extraOut;
        int ex = hx + overhang, ez = hz + overhang;
        boolean big = Math.max(ex, ez) >= 14;
        int[] layers = big ? new int[]{3, 2, 1} : Math.max(ex, ez) >= 6 ? new int[]{2, 1} : new int[]{1};
        if (liftLess && lift == 0) layers = layers.length > 1 ? java.util.Arrays.copyOfRange(layers, 1, layers.length) : layers;
        if (lift != 0) {
            Integer L = LIFT.get(lift);
            layers = L != null && L != 0 ? java.util.Arrays.copyOfRange(new int[]{3, 2, 1}, 3 - L, 3) : new int[0];
        }
        if (!corner || !Dyn.truthy(Arch.ROOF.getOrDefault("corner_lift", Boolean.TRUE))) layers = new int[0];
        int run = Math.max(2, ez - overhang);
        double rise = Math.max(run >= 4 ? 4 : 3, run * (run < 12 && pitch == 0 ? steep + 0.25 : steep) * (!form.equals("pyramid") ? 1.0 : 1.2));
        H h;
        H full = null;
        double flat = 0;
        int gx = 0, gz = 0;
        switch (form) {
            case "pyramid" -> h = Arch.roofSurface(ex, hx == hz ? ex : ez, topY + 1, overhang, run, rise, null, layers, new int[]{1, 0}, null);
            case "wudian" -> h = Arch.roofSurface(ex, ez, topY + 1, overhang, run, rise, null, layers, big ? new int[]{2, 0} : new int[]{1, 0}, null);
            case "xieshan" -> h = Arch.roofSurface(ex, ez, topY + 1, overhang, run, rise, Math.max(1, hx - 2), layers, big ? new int[]{2, 1} : new int[]{1, 0}, null);
            case "luding" -> {
                full = Arch.roofSurface(ex, ez, topY + 1, overhang, run, rise, null, layers, big ? new int[]{2, 0} : new int[]{1, 0}, null);
                flat = topY + 1 + Math.max(2, Py.round(rise * 0.45));
                h = new H();
                for (Map.Entry<Long, Double> e : full.entrySet()) h.put(e.getKey(), Math.min(e.getValue(), flat));
            }
            case "shizi" -> {
                gx = Math.max(1, hx - 2);
                gz = Math.max(1, hz - 2);
                int run2 = Math.max(2, ex - overhang);
                double rise2 = Math.max(run2 >= 4 ? 4 : 3, run2 * (run2 < 12 ? steep + 0.25 : steep));
                H h1 = Arch.roofSurface(ex, ez, topY + 1, overhang, run, rise, gx, layers, new int[]{1, 0}, null);
                H h2r = Arch.roofSurface(ez, ex, topY + 1, overhang, run2, rise2, gz, layers, new int[]{1, 0}, null);
                H h2 = new H();
                for (Map.Entry<Long, Double> e : h2r.entrySet()) h2.put(Canvas.cz(e.getKey()), Canvas.cx(e.getKey()), e.getValue());
                PySet<IntPair> keys = PySet.fromDictKeys(pairKeys(h1)).or(PySet.fromDictKeys(pairKeys(h2)));
                h = new H();
                for (IntPair c : keys) {
                    Double a = h1.at(c.a(), c.b()), b = h2.at(c.a(), c.b());
                    h.put(c.a(), c.b(), Math.max(a != null ? a : -1e9, b != null ? b : -1e9));
                }
            }
            case "yuanzan" -> {
                double ext = com.flyingeaves.gen.py.UcrtMath.hypotInt(hx, hz) + 0.7 * overhang;
                Double pp = pitch != 0 ? PITCH.get(pitch) : null;
                double rr = Math.max(4, ext * ((pp != null && pp != 0 ? pp : steep) + 0.2));
                h = new H();
                for (int u = -(int) ext - 1; u < (int) ext + 2; u++)
                    for (int v = -(int) ext - 1; v < (int) ext + 2; v++) {
                        double r = com.flyingeaves.gen.py.UcrtMath.hypotInt(u, v);
                        if (r <= ext) h.put(u, v, topY + 1 + rr * com.flyingeaves.gen.py.UcrtMath.floatPow(1 - r / ext, 1.3));
                    }
            }
            case "yingshan" -> h = Arch.roofSurface(hx + 1, ez, topY + 1, overhang, run, rise, hx + 1, new int[0], new int[]{0, 0}, null);
            default -> {
                h = Arch.roofSurface(ex, ez, topY + 1, overhang, run, rise, ex, new int[0], new int[]{0, 0}, null);
                if (form.equals("juanpeng")) {
                    double top = h.max();
                    H h2 = new H();
                    for (Map.Entry<Long, Double> e : h.entrySet()) h2.put(e.getKey(), e.getValue() < top - 0.75 ? e.getValue() : top - 0.5);
                    h = h2;
                }
            }
        }
        if (cap != null) h = capped(h, (int[]) cap[0]);
        if (minY != null) {
            H h2 = new H();
            for (Map.Entry<Long, Double> e : h.entrySet()) h2.put(e.getKey(), Math.max(e.getValue(), minY + 1));
            h = h2;
        }
        final int fhx = hx, fhz = hz, ftop = topY;
        Arch.rasterRoof(f, h, (u, v, val) -> (Math.abs(u) <= fhx && Math.abs(v) <= fhz) ? ftop : Math.max(ftop, (int) Math.floor(val) - 2), minY);
        if (cap != null) return capRoof(f, h, (int[]) cap[0], (String) cap[1]);
        int ry = (int) Math.floor(h.max());
        if (form.equals("xuanshan") || form.equals("yingshan")) {
            Arch.mainRidge(f, form.equals("xuanshan") ? ex : hx + 1, ry, ridgeH);
            for (int sgn : new int[]{-1, 1}) {
                int u = sgn * hx;
                for (int v = -hz; v <= hz; v++) {
                    Double t = h.at(u, v);
                    if (t == null) continue;
                    for (int y = topY + 1; y < (int) Math.floor(t); y++)
                        if (f.get(u, y, v) == null) f.put(u, y, v, form.equals("xuanshan") ? Pal.WHITE : WALL_FILL[0]);
                }
            }
        } else if (form.equals("xieshan")) {
            gx = Math.max(1, hx - 2);
            double mx = Double.NEGATIVE_INFINITY;
            for (int u = -gx; u <= gx; u++) {
                Double t = h.at(u, 0);
                mx = Math.max(mx, t != null ? t : 0);
            }
            ry = (int) Math.floor(mx);
            Arch.mainRidge(f, gx, ry, ridgeH);
            for (int sgn : new int[]{-1, 1}) {
                int u = sgn * gx;
                for (int v = -ez; v <= ez; v++) {
                    Double t = h.at(u, v), s = h.at(u + sgn, v);
                    if (t == null || s == null) continue;
                    for (int y = (int) Math.floor(s) + 1; y < (int) Math.floor(t); y++) f.put(u, y, v, Pal.RED);
                }
            }
            for (int sgn : new int[]{-1, 1}) {
                List<int[]> c1 = new ArrayList<>();
                for (int v = -ez + (ex - gx) + 1; v < ez - (ex - gx); v++) c1.add(new int[]{sgn * gx, v});
                Arch.ridgeCells(f, h, c1);
                for (int sv : new int[]{-1, 1}) {
                    int v0 = ez - (ex - gx);
                    List<int[]> c2 = new ArrayList<>();
                    for (int i = 0; i < ex - gx + 1; i++) c2.add(new int[]{sgn * (gx + i), sv * (v0 + i)});
                    Arch.ridgeCells(f, h, c2);
                    List<int[]> hp = new ArrayList<>();
                    for (int i = ex - gx; i >= 0; i--) hp.add(new int[]{sgn * (gx + i), sv * (v0 + i)});
                    hips.add(hp);
                }
            }
            if (beasts == 0 && tierK > 1.05 && Math.max(ex, ez) >= 7) {
                for (int sgn : new int[]{-1, 1})
                    for (int sv : new int[]{-1, 1}) {
                        int cu = sgn * (ex - 1), cv = sv * (ez - 1);
                        Double val = h.at(cu, cv);
                        if (val != null) f.put(cu, (int) Math.floor(val) + 2, cv, B("polished_blackstone_button", "face", "floor", "facing", "south"));
                    }
            }
        } else if (form.equals("wudian")) {
            int half = Math.max(0, ex - ez);
            Arch.mainRidge(f, half, ry, ridgeH);
            for (int sgn : new int[]{-1, 1})
                for (int sv : new int[]{-1, 1}) {
                    List<int[]> c = new ArrayList<>(), hp = new ArrayList<>();
                    for (int t = 1; t <= ez; t++) c.add(new int[]{sgn * (half + t), sv * t});
                    Arch.ridgeCells(f, h, c);
                    for (int t = ez; t > 0; t--) hp.add(new int[]{sgn * (half + t), sv * t});
                    hips.add(hp);
                }
        } else if (form.equals("yuanzan")) {
            Arch.finial(f, ry, tierK < 1.5 ? 4 : 7);
        } else if (form.equals("pyramid")) {
            for (int sgn : new int[]{-1, 1})
                for (int sv : new int[]{-1, 1}) {
                    List<int[]> c = new ArrayList<>(), hp = new ArrayList<>();
                    for (int t = 1; t <= ex; t++) c.add(new int[]{sgn * t, sv * t});
                    Arch.ridgeCells(f, h, c);
                    for (int t = ex; t > 0; t--) hp.add(new int[]{sgn * t, sv * t});
                    hips.add(hp);
                }
            Arch.finial(f, ry, tierK < 1.5 ? 4 : 7);
        } else if (form.equals("luding")) {
            ry = (int) Math.floor(flat);
            List<IntPair> cells = new ArrayList<>();
            for (Map.Entry<Long, Double> e : full.entrySet()) if (e.getValue() >= flat) cells.add(IntPair.of(Canvas.cx(e.getKey()), Canvas.cz(e.getKey())));
            Set<IntPair> cellset = new HashSet<>(cells);
            for (IntPair c : cells) {
                boolean edge = false;
                for (int[] d : N4) if (!cellset.contains(IntPair.of(c.a() + d[0], c.b() + d[1]))) edge = true;
                if (edge) f.put(c.a(), ry + 1, c.b(), B(roof("ridge")));
            }
            int half = Math.max(0, ex - ez);
            for (int sgn : new int[]{-1, 1})
                for (int sv : new int[]{-1, 1}) {
                    List<int[]> c = new ArrayList<>(), hp = new ArrayList<>();
                    for (int t = 1; t <= ez; t++) {
                        Double fv = full.at(sgn * (half + t), sv * t);
                        if ((fv != null ? fv : 0) < flat) c.add(new int[]{sgn * (half + t), sv * t});
                    }
                    Arch.ridgeCells(f, h, c);
                    for (int t = ez; t > 0; t--) {
                        Double fv = full.at(sgn * (half + t), sv * t);
                        if ((fv != null ? fv : 0) < flat) hp.add(new int[]{sgn * (half + t), sv * t});
                    }
                    hips.add(hp);
                }
        } else if (form.equals("shizi")) {
            ry = (int) Math.floor(h.max());
            double m1 = Double.NEGATIVE_INFINITY, m2 = Double.NEGATIVE_INFINITY;
            for (int u = -gx; u <= gx; u++) {
                Double t = h.at(u, 0);
                m1 = Math.max(m1, t != null ? t : 0);
            }
            for (int v = -gz; v <= gz; v++) {
                Double t = h.at(0, v);
                m2 = Math.max(m2, t != null ? t : 0);
            }
            int ry1 = (int) Math.floor(m1), ry2 = (int) Math.floor(m2);
            Arch.mainRidge(f, gx, ry1, ridgeH);
            Arch.mainRidge(f.sub(0, 0, "east"), gz, ry2, ridgeH);
            for (int sgn : new int[]{-1, 1}) {
                int u = sgn * gx;
                for (int v = -ez; v <= ez; v++) {
                    Double t = h.at(u, v), s = h.at(u + sgn, v);
                    if (t != null && s != null) for (int y = (int) Math.floor(s) + 1; y < (int) Math.floor(t); y++) f.put(u, y, v, Pal.RED);
                }
                int v = sgn * gz;
                for (int uu = -ex; uu <= ex; uu++) {
                    Double t = h.at(uu, v), s = h.at(uu, v + sgn);
                    if (t != null && s != null) for (int y = (int) Math.floor(s) + 1; y < (int) Math.floor(t); y++) f.put(uu, y, v, Pal.RED);
                }
            }
            for (int sgn : new int[]{-1, 1})
                for (int sv : new int[]{-1, 1}) {
                    List<int[]> c = new ArrayList<>();
                    for (int i = 0; i < Math.min(ex, ez) - Math.max(gx, gz) + 1; i++) c.add(new int[]{sgn * (ex - i), sv * (ez - i)});
                    Arch.ridgeCells(f, h, c);
                    hips.add(new ArrayList<>(c));
                }
            Arch.finial(f, ry + 1, tierK < 1.5 ? 4 : 7);
        }
        Integer bn = BEAST_N.get(beasts);
        if (bn != null && bn != 0) rideBeasts(f, h, hips, bn);
        return ry;
    }

    static final String[] WALL_FILL = {B("white_terracotta")};
    static final Map<String, String> DOOR_OF = Map.of("oak_trapdoor", "oak_door", "spruce_trapdoor", "spruce_door", "dark_oak_trapdoor", "dark_oak_door",
            "birch_trapdoor", "birch_door", "iron_bars", "spruce_door");

    // ------------------------------------------------------------------------------------------------ rooms

    /** bgen.stair_plan. */
    public static final class StairPlan {
        public final List<Object[]> steps = new ArrayList<>();       // (u, v, rise, facing)
        public final List<int[]> landing = new ArrayList<>();        // (u, v, rise)
        public boolean ladder;
        public Set<IntPair> reserve, well, above, clear, clearAbove;
        public List<IntPair> approach;
    }

    static StairPlan stairPlan(int hx, int hz, int sh) {
        StairPlan p = new StairPlan();
        int u0 = -hx + 1;
        List<IntPair> approach = new ArrayList<>();
        if (2 * hz - 3 >= sh) {
            for (int i = 0; i < sh; i++) p.steps.add(new Object[]{u0, -hz + 2 + i, i + 1, "south"});
            approach.add(IntPair.of(u0, -hz + 1));
        } else if (2 * hz - 2 >= sh) {
            for (int i = 0; i < sh; i++) p.steps.add(new Object[]{u0, -hz + 1 + i, i + 1, "south"});
            approach.add(IntPair.of(u0 + 1, -hz + 1));
        } else {
            int l1 = 2 * hz - 2;
            int rest = sh - l1;
            if (hx >= 3 && l1 >= 2 && rest <= l1 - 1) {
                for (int i = 0; i < l1; i++) p.steps.add(new Object[]{u0, -hz + 1 + i, i + 1, "south"});
                p.landing.add(new int[]{u0 + 1, -hz + l1, l1});
                for (int j = 0; j < rest; j++) p.steps.add(new Object[]{u0 + 1, -hz + l1 - 1 - j, l1 + 1 + j, "north"});
                approach.add(rest < l1 - 1 ? IntPair.of(u0 + 1, -hz + 1) : IntPair.of(u0 + 2, -hz + 1));
            } else {
                p.ladder = true;
                for (int r = 1; r <= sh; r++) p.steps.add(new Object[]{u0, -hz + 1, r, "east"});
                approach.add(IntPair.of(u0 + 1, -hz + 1));
            }
        }
        Set<IntPair> cells = new HashSet<>();
        for (Object[] s : p.steps) cells.add(IntPair.of((int) s[0], (int) s[1]));
        for (int[] l : p.landing) cells.add(IntPair.of(l[0], l[1]));
        Set<IntPair> well = new HashSet<>();
        for (Object[] s : p.steps) if ((int) s[2] + 3 >= sh) well.add(IntPair.of((int) s[0], (int) s[1]));
        for (int[] l : p.landing) if (l[2] + 3 >= sh) well.add(IntPair.of(l[0], l[1]));
        Object[] top = p.steps.get(0);
        for (Object[] s : p.steps) if ((int) s[2] > (int) top[2]) top = s;
        Set<IntPair> above = new HashSet<>(well);
        for (int[] d : N4) above.add(IntPair.of((int) top[0] + d[0], (int) top[1] + d[1]));
        Set<IntPair> reserve = new HashSet<>(cells);
        reserve.addAll(approach);
        for (IntPair c : cells) reserve.add(IntPair.of(c.a() + 1, c.b()));
        Set<IntPair> clear = new HashSet<>();
        for (IntPair a : approach) for (int[] d : N4) clear.add(IntPair.of(a.a() + d[0], a.b() + d[1]));
        clear.removeAll(cells);
        Set<IntPair> clearAbove = new HashSet<>();
        if (!p.ladder) {
            int[] d = ahead((String) p.steps.get(0)[3]);
            for (int k : new int[]{1, 2}) clear.add(IntPair.of((int) p.steps.get(0)[0] - d[0] * k, (int) p.steps.get(0)[1] - d[1] * k));
            d = ahead((String) top[3]);
            for (int k : new int[]{1, 2}) clearAbove.add(IntPair.of((int) top[0] + d[0] * k, (int) top[1] + d[1] * k));
        }
        p.reserve = reserve;
        p.well = well;
        p.above = above;
        p.approach = approach;
        p.clear = clear;
        p.clearAbove = clearAbove;
        return p;
    }

    static int[] ahead(String facing) {
        return switch (facing) {
            case "south" -> new int[]{0, 1};
            case "north" -> new int[]{0, -1};
            case "east" -> new int[]{1, 0};
            default -> new int[]{-1, 0};
        };
    }

    /** A partition wall: axis 'u' or 'v', coord, a0, a1, door. */
    record PWall(char axis, int c, int a0, int a1, int door) {
    }

    /** bgen.bays: (rooms, walls). */
    static Object[] bays(int[] rect) {
        int u0 = rect[0], v0 = rect[1], u1 = rect[2], v1 = rect[3];
        int W = u1 - u0 + 1, D = v1 - v0 + 1;
        int mid = Math.floorDiv(v0 + v1, 2);
        List<int[]> rooms = new ArrayList<>();
        List<PWall> walls = new ArrayList<>();
        if (W >= 11) {
            int cw = Math.max(5, Math.floorDiv(W, 3) | 1);
            int c0 = -Math.floorDiv(cw, 2), c1 = Math.floorDiv(cw, 2);
            if (c0 - 1 - u0 < 3 || u1 - (c1 + 1) < 3) {
                rooms.add(rect);
                return new Object[]{rooms, walls};
            }
            rooms.add(new int[]{c0, v0, c1, v1});
            for (int side : new int[]{-1, 1}) {
                int a = side < 0 ? u0 : c1 + 2, b = side < 0 ? c0 - 2 : u1;
                int wu = side < 0 ? c0 - 1 : c1 + 1;
                if (D >= 9) {
                    rooms.add(new int[]{a, mid + 1, b, v1});
                    rooms.add(new int[]{a, v0, b, mid - 1});
                    walls.add(new PWall('v', mid, a, b, u0 - 99));
                    walls.add(new PWall('u', wu, mid + 1, v1, Math.floorDiv(mid + 1 + v1, 2)));
                    walls.add(new PWall('u', wu, v0, mid - 1, Math.floorDiv(v0 + mid - 1, 2)));
                    walls.add(new PWall('u', wu, mid, mid, u0 - 99));
                } else {
                    rooms.add(new int[]{a, v0, b, v1});
                    walls.add(new PWall('u', wu, v0, v1, Math.floorDiv(v0 + v1, 2)));
                }
            }
            return new Object[]{rooms, walls};
        }
        if (D >= 9) {
            rooms.add(new int[]{u0, mid + 1, u1, v1});
            rooms.add(new int[]{u0, v0, u1, mid - 1});
            walls.add(new PWall('v', mid, u0, u1, 0));
            return new Object[]{rooms, walls};
        }
        rooms.add(rect);
        return new Object[]{rooms, walls};
    }

    /** bgen.bsp: (rooms, walls). */
    static Object[] bsp(int[] rect, int maxSide, PyRandom rng, int depth) {
        int u0 = rect[0], v0 = rect[1], u1 = rect[2], v1 = rect[3];
        int W = u1 - u0 + 1, D = v1 - v0 + 1;
        List<int[]> rooms = new ArrayList<>();
        List<PWall> walls = new ArrayList<>();
        if ((W <= maxSide && D <= maxSide) || depth > 5 || (W < 7 && D < 7) || (W >= D && W < 7) || (D > W && D < 7)) {
            rooms.add(rect);
            return new Object[]{rooms, walls};
        }
        if (W >= D) {
            int cut = rng.randint(u0 + 3, u1 - 3);
            if (Math.abs(cut) <= 2)
                for (int alt : new int[]{3, -3, 4, -4})
                    if (u0 + 3 <= alt && alt <= u1 - 3) {
                        cut = alt;
                        break;
                    }
            int door = rng.randint(v0 + 1, v1 - 1);
            Object[] a = bsp(new int[]{u0, v0, cut - 1, v1}, maxSide, rng, depth + 1);
            Object[] b = bsp(new int[]{cut + 1, v0, u1, v1}, maxSide, rng, depth + 1);
            return join(a, b, new PWall('u', cut, v0, v1, door));
        }
        int cut = rng.randint(v0 + 3, v1 - 3);
        int door = rng.randint(u0 + 1, u1 - 1);
        Object[] a = bsp(new int[]{u0, v0, u1, cut - 1}, maxSide, rng, depth + 1);
        Object[] b = bsp(new int[]{u0, cut + 1, u1, v1}, maxSide, rng, depth + 1);
        return join(a, b, new PWall('v', cut, u0, u1, door));
    }

    @SuppressWarnings("unchecked")
    private static Object[] join(Object[] a, Object[] b, PWall w) {
        List<int[]> rooms = new ArrayList<>((List<int[]>) a[0]);
        rooms.addAll((List<int[]>) b[0]);
        List<PWall> walls = new ArrayList<>((List<PWall>) a[1]);
        walls.addAll((List<PWall>) b[1]);
        walls.add(w);
        return new Object[]{rooms, walls};
    }

    /** A room storey_interior handed out: (a, b, c, d, room type). */
    record Used(int a, int b, int c, int d, String rt) {
    }

    /**
     * bgen.storey_interior. partitions: "bays", a truthy value (true: bsp) or falsy (null / false: one room).
     * stairsCells, furnFree: sets of cells; sideDoors: list of cells.
     */
    @SuppressWarnings("unchecked")
    static List<Used> storeyInterior(Fr f, PyRandom rng, int hx, int hz, int y, int sh, List<String> program, String wallBlk,
                                     Set<IntPair> stairsCells, int frontDoorU, int maxSide, Object partitions, List<IntPair> sideDoors,
                                     String tier, Set<IntPair> furnFree) {
        int[] rect = {-hx + 1, -hz + 1, hx - 1, hz - 1};
        Object[] rw;
        if ("bays".equals(partitions)) rw = bays(rect);
        else if (Dyn.truthy(partitions)) rw = bsp(rect, maxSide, rng, 0);
        else {
            List<int[]> r = new ArrayList<>();
            r.add(rect);
            rw = new Object[]{r, new ArrayList<PWall>()};
        }
        List<int[]> rooms = (List<int[]>) rw[0];
        List<PWall> walls = (List<PWall>) rw[1];
        List<IntPair> doors = new ArrayList<>();
        for (PWall w : walls) {
            for (int t = w.a0; t <= w.a1; t++)
                for (int k = 1; k < sh; k++) {
                    IntPair cell = w.axis == 'u' ? IntPair.of(w.c, t) : IntPair.of(t, w.c);
                    if (stairsCells.contains(cell)) continue;
                    if (Math.abs(t - w.door) <= 0 && k <= 3) {
                        f.put(cell.a(), y + k, cell.b(), AIR);
                        continue;
                    }
                    f.put(cell.a(), y + k, cell.b(), wallBlk);
                }
            if (!(w.a0 <= w.door && w.door <= w.a1)) continue;
            IntPair cell = w.axis == 'u' ? IntPair.of(w.c, w.door) : IntPair.of(w.door, w.c);
            doors.add(cell);
            f.put(cell.a(), y + 3, cell.b(), AIR);
            f.put(cell.a(), y + 4, cell.b(), wallBlk);
        }
        for (IntPair c : sideDoors) for (int k = 1; k < 4; k++) f.put(c.a(), y + k, c.b(), AIR);
        rooms.sort(Comparator.<int[]>comparingInt(r -> -r[3]).thenComparingInt(r -> Math.abs(r[0] + r[2])));
        List<Used> used = new ArrayList<>();
        for (int i = 0; i < rooms.size(); i++) {
            int[] r = rooms.get(i);
            int a = r[0], b = r[1], c = r[2], d = r[3];
            String rt = program.get(Math.floorMod(i, program.size()));
            used.add(new Used(a, b, c, d, rt));
            List<int[]> dlist = new ArrayList<>();
            for (IntPair dd : doors) if (a - 1 <= dd.a() && dd.a() <= c + 1 && b - 1 <= dd.b() && dd.b() <= d + 1) dlist.add(new int[]{dd.a(), dd.b()});
            if (b <= hz - 1 && hz - 1 <= d + 1 && a <= frontDoorU && frontDoorU <= c) dlist.add(new int[]{frontDoorU, hz - 1});
            for (IntPair s : stairsCells) dlist.add(new int[]{s.a(), s.b()});
            for (IntPair s : furnFree) if (!stairsCells.contains(s)) dlist.add(new int[]{s.a(), s.b()});
            for (IntPair s : sideDoors) if (a - 1 <= s.a() && s.a() <= c + 1 && b - 1 <= s.b() && s.b() <= d + 1) dlist.add(new int[]{s.a(), s.b()});
            Furniture.furnish(f, rng, rt, a, b, c, d, y, dlist, "north", sh);
        }
        Set<IntPair> nearDoors = new HashSet<>();
        List<IntPair> dd = new ArrayList<>(doors);
        dd.addAll(sideDoors);
        for (IntPair p : dd) for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j++) nearDoors.add(IntPair.of(p.a() + i, p.b() + j));
        for (Used u : used) {
            boolean big = (u.c - u.a + 1) * (u.d - u.b + 1) >= 120;
            Set<IntPair> keep = new HashSet<>(stairsCells);
            keep.addAll(furnFree);
            keep.addAll(sideDoors);
            keep.addAll(nearDoors);
            lightRoom(f, u.a, u.b, u.c, u.d, y, sh, keep, pickLight(rng, u.rt, tier, big ? List.of("ceiling", "inlay", "floor") : null), u.a + u.c == 0, null);
        }
        return used;
    }

    static boolean passable(String st) {
        return st == null || st.isEmpty() || st.equals(AIR) || st.contains("carpet") || st.contains("pressure_plate");
    }

    static final Object[][] EMIT = {{"minecraft:light[", 15}, {"spirit_light", 15}, {"soul_lantern", 10}, {"sea_lantern", 15}, {"lantern", 15},
            {"campfire", 15}, {"glowstone", 15}, {"froglight", 15}, {"shroomlight", 15}, {"torch", 14}, {"end_rod", 14}, {"candle", 9}};

    static boolean isLamp(String st) {
        if (st == null || st.isEmpty()) return false;
        for (Object[] e : EMIT) if (st.contains((String) e[0])) return true;
        return false;
    }

    /** bgen.pick_light: a fixture dict, or null. */
    static Map<String, Object> pickLight(PyRandom rng, String roomType, String tier, List<String> mounts) {
        if (Parts.LIGHTS.isEmpty()) Parts.reloadLights();
        List<Map<String, Object>> cands = new ArrayList<>();
        List<String> ms = mounts != null ? mounts : Arch.INDOOR_MOUNTS;
        for (Map<String, Object> e : Parts.LIGHTS.values()) {
            if (Dyn.truthy(e.get("tiers")) && !Dyn.list(e.get("tiers")).contains(tier)) continue;
            if (Dyn.truthy(e.get("rooms")) && !Dyn.list(e.get("rooms")).contains(roomType)) continue;
            if (!ms.contains(e.containsKey("mount") ? (String) e.get("mount") : "hang")) continue;
            cands.add(e);
        }
        if (cands.isEmpty()) return null;
        double total = 0;
        for (Map<String, Object> e : cands) total += lightWeight(e, roomType);
        double r = rng.random() * total;
        for (Map<String, Object> e : cands) {
            r -= lightWeight(e, roomType);
            if (r <= 0) return e;
        }
        return cands.get(cands.size() - 1);
    }

    static double lightWeight(Map<String, Object> e, String roomType) {
        double w = e.get("weight") == null ? 1 : ((Number) e.get("weight")).doubleValue();
        boolean named = Dyn.truthy(e.get("rooms")) && Dyn.list(e.get("rooms")).contains(roomType);
        return w * (named ? 3 : 1);
    }

    static final int TARGET_LIGHT = 12;

    static String lightBlock() {
        return Arch.unseenLight();
    }

    private static final Pattern CANDLES = Pattern.compile("candles=(\\d)");
    private static final Pattern LEVEL = Pattern.compile("level=(\\d+)");

    /** bgen.light_room. mask: null or the cells to light. */
    static void lightRoom(Fr f, int a, int b, int c, int d, int y, int sh, Set<IntPair> keepFree, Map<String, Object> fixture, boolean mirror, Set<IntPair> mask) {
        String mount;
        int light;
        List<Object[]> fblocks = new ArrayList<>();               // (dk, state)
        if (fixture == null) {
            mount = "none";
            light = 0;
            fblocks.add(new Object[]{0, "minecraft:air"});
        } else {
            mount = fixture.containsKey("mount") ? (String) fixture.get("mount") : "hang";
            light = fixture.containsKey("light") ? Dyn.toInt(fixture.get("light")) : 15;
            for (Object o : Dyn.list(fixture.get("blocks"))) {
                List<Object> bl = Dyn.list(o);
                fblocks.add(new Object[]{Dyn.toInt(bl.get(0)), bl.get(1)});
            }
        }
        List<int[]> sources = new ArrayList<>();
        for (int u = a; u <= c; u++)
            for (int v = b; v <= d; v++)
                for (int k = 1; k < sh; k++) {
                    String st = f.get(u, y + k, v);
                    if (st == null || st.isEmpty()) continue;
                    for (Object[] em : EMIT) {
                        String key = (String) em[0];
                        int lv = (int) em[1];
                        if (st.contains(key) && !st.contains("lit=false")) {
                            if (key.equals("candle")) {
                                Matcher m = CANDLES.matcher(st);
                                lv = m.find() ? 3 * Integer.parseInt(m.group(1)) : 3;
                            } else if (key.equals("minecraft:light[") || key.equals("spirit_light")) {
                                Matcher m = LEVEL.matcher(st);
                                lv = m.find() ? Integer.parseInt(m.group(1)) : 15;
                            }
                            sources.add(new int[]{u, v, k, lv});
                            break;
                        }
                    }
                }
        int hangK = Math.min(3, sh - 1);
        int lampK = switch (mount) {
            case "hang" -> hangK;
            case "ceiling" -> sh;
            case "floor" -> 1;
            case "inlay" -> 0;
            default -> hangK;
        };
        int topBlock = (int) fblocks.get(0)[0];
        for (Object[] fb : fblocks) if ((int) fb[0] > topBlock) topBlock = (int) fb[0];
        final String fmount = mount;
        final int ftop = topBlock;
        java.util.function.BiPredicate<Integer, Integer> free = (u, v) -> {
            if (keepFree.contains(IntPair.of(u, v))) return false;
            if (fmount.equals("floor") && !(u == a || u == c || v == b || v == d)) return false;
            if (fmount.equals("floor")) {
                int[][] os = {{u - 1, v}, {u + 1, v}, {u, v - 1}, {u, v + 1}};
                for (int[] o : os)
                    if (!(a <= o[0] && o[0] <= c && b <= o[1] && o[1] <= d) && passable(f.get(o[0], y + 1, o[1])) && passable(f.get(o[0], y + 2, o[1])))
                        return false;
            }
            if (fmount.equals("inlay")) {
                String s1 = f.get(u, y + 1, v);
                return passable(s1) && passable(f.get(u, y + 2, v)) && (s1 == null || s1.isEmpty());
            }
            if (fmount.equals("ceiling")) {
                String st = f.get(u, y + sh - 1, v);
                return !(st != null && !st.isEmpty() && !st.equals(AIR) && !st.contains("lantern") && !st.contains("chain"));
            }
            if (fmount.equals("hang")) {
                for (int k = hangK; k < Math.min(sh, hangK + 2); k++) if (!passable(f.get(u, y + k, v))) return false;
                return true;
            }
            for (int k = 1; k < 1 + ftop + 1; k++) {
                String s = f.get(u, y + k, v);
                if (!passable(s) || (k == 1 && s != null && !s.isEmpty())) return false;
            }
            return true;
        };
        List<IntPair> cells = new ArrayList<>();
        for (int u = a; u <= c; u++) for (int v = b; v <= d; v++) if (mask == null || mask.contains(IntPair.of(u, v))) cells.add(IntPair.of(u, v));
        if (cells.isEmpty()) return;
        List<IntPair> targets = new ArrayList<>();
        for (IntPair t : cells) if (passable(f.get(t.a(), y + 1, t.b())) && passable(f.get(t.a(), y + 2, t.b()))) targets.add(t);
        if (targets.isEmpty()) targets = cells;
        double cu = (a + c) / 2.0, cv = (b + d) / 2.0;
        List<IntPair> placed = new ArrayList<>();
        int width = c - a + 1;
        java.util.function.BiPredicate<Integer, Integer> ok = (u, v) -> free.test(u, v) && (!mirror || free.test(a + c - u, v));
        List<int[]> spots = new ArrayList<>();
        if (mount.equals("floor")) {
            spots.add(new int[]{a, (int) Py.round(cv)});
            spots.add(new int[]{c, (int) Py.round(cv)});
        } else if (cells.size() >= 120) {
            for (int gu : spaced(a, c, 7)) for (int gv : spaced(b, d, 7)) spots.add(new int[]{gu, gv});
        } else if (mirror && width >= 7) spots.add(new int[]{(int) Py.round(cu - width / 4.0), (int) Py.round(cv)});
        else spots.add(new int[]{(int) Py.round(cu), (int) Py.round(cv)});
        if (!mount.equals("none")) {
            for (int[] sp : spots) {
                int su = sp[0], sv = sp[1];
                if (mirror && su > cu) continue;
                List<IntPair> cands = new ArrayList<>();
                for (IntPair t : cells) {
                    if (!(ok.test(t.a(), t.b()) && Math.abs(t.a() - su) + Math.abs(t.b() - sv) <= 2)) continue;
                    boolean far = true;
                    for (IntPair p : placed) if (Math.abs(t.a() - p.a()) + Math.abs(t.b() - p.b()) < 3) far = false;
                    if (far) cands.add(t);
                }
                cands.sort(Comparator.comparingInt(t -> Math.abs(t.a() - su) + Math.abs(t.b() - sv)));
                if (!cands.isEmpty()) {
                    int lu = cands.get(0).a(), lv = cands.get(0).b();
                    int baseK = lampK;
                    PySet<Integer> pus = mirror ? PySet.of(lu, a + c - lu) : PySet.of(lu);
                    for (int pu : pus) {
                        for (Object[] fb : fblocks) f.put(pu, y + baseK + (int) fb[0], lv, (String) fb[1]);
                        sources.add(new int[]{pu, lv, lampK + (mount.equals("floor") ? topBlock : 0), light});
                        placed.add(IntPair.of(pu, lv));
                    }
                }
            }
        }
        int lk = sh >= 4 ? 2 : 1;
        Map<IntPair, Integer> lvl = new java.util.HashMap<>();
        for (IntPair t : cells) lvl.put(t, 0);
        for (int[] src : sources) shine(lvl, src[0], src[1], src[2], src[3]);
        List<int[]> near = new ArrayList<>();
        for (int du = -2; du < 3; du++) for (int dv = -2; dv < 3; dv++) if (du != 0 || dv != 0) near.add(new int[]{du, dv});
        near.sort(Comparator.comparingInt(t -> Math.abs(t[0]) + Math.abs(t[1])));
        near.add(0, new int[]{0, 0});
        Set<IntPair> tset = new HashSet<>(targets);
        List<IntPair> order = new ArrayList<>(targets);
        order.sort(Comparator.<IntPair>comparingInt(IntPair::b).thenComparingInt(IntPair::a));
        for (IntPair t : order) {
            if (lvl.get(t) >= TARGET_LIGHT) continue;
            for (int[] dn : near) {
                IntPair cc = IntPair.of(t.a() + dn[0], t.b() + dn[1]);
                String g = f.get(cc.a(), y + lk, cc.b());
                if (tset.contains(cc) && !keepFree.contains(cc) && (g == null || g.isEmpty())) {
                    f.put(cc.a(), y + lk, cc.b(), lightBlock());
                    sources.add(new int[]{cc.a(), cc.b(), lk, 15});
                    shine(lvl, cc.a(), cc.b(), lk, 15);
                    break;
                }
            }
        }
    }

    static List<Integer> spaced(int lo, int hi, int step) {
        int n = (int) Math.max(1, Py.round((hi - lo) / (double) step));
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add((int) Py.round(lo + (hi - lo) * (i + 0.5) / n));
        return out;
    }

    private static void shine(Map<IntPair, Integer> lvl, int su, int sv, int sk, int lv) {
        int top = lv - Math.abs(1 - sk);
        int rad = top - TARGET_LIGHT;
        for (int du = -rad; du < rad + 1; du++) {
            int rem = rad - Math.abs(du);
            for (int dv = -rem; dv < rem + 1; dv++) {
                IntPair cc = IntPair.of(su + du, sv + dv);
                Integer cur = lvl.get(cc);
                if (cur != null) {
                    int val = top - Math.abs(du) - Math.abs(dv);
                    if (val > cur) lvl.put(cc, val);
                }
            }
        }
    }
}
