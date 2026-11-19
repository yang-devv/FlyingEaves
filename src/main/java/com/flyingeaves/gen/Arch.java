package com.flyingeaves.gen;

import com.flyingeaves.gen.py.PyRandom;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.flyingeaves.gen.Blocks.AIR;
import static com.flyingeaves.gen.Blocks.B;
import static com.flyingeaves.gen.Blocks.slab;
import static com.flyingeaves.gen.Blocks.stair;

/**
 * arch.py: the shared parts of every building - palette, roof themes, masonry, roof height fields and their
 * rasteriser, ridges, ornaments and lamps (data/parts), platforms and flights. Global state as in Python (the
 * current roof theme ROOF, the palette Pal, the ornament choices ORN): one generation at a time.
 */
public final class Arch {
    private Arch() {
    }

    // ============================================================================================ palette

    /** arch.Pal (mutable: set_walls and style change it). */
    public static final class Pal {
        public static String TILE = "deepslate_tile", TILE_FULL = "deepslate_tiles", RIDGE = "polished_deepslate";
        public static String COL = B("stripped_mangrove_log", "axis", "y");
        public static String RED = B("mangrove_planks");
        public static String WHITE = B("calcite");
        public static String WOOD = "spruce";
        public static String JADE = B("smooth_quartz");
        public static String JADE_BRICK = B("quartz_bricks");
        public static String FLOOR = B("polished_andesite");
        public static String STONE = B("stone_bricks");
        public static String GOLD = B("gold_block");
    }

    /** arch.STYLES / style(name): columns, beam / board colour, wall colour. */
    public static void style(String name) {
        switch (name) {
            case "red" -> {
                Pal.COL = B("stripped_mangrove_log", "axis", "y");
                Pal.RED = B("mangrove_planks");
                Pal.WHITE = B("calcite");
            }
            case "dark" -> {
                Pal.COL = B("stripped_dark_oak_log", "axis", "y");
                Pal.RED = B("dark_oak_planks");
                Pal.WHITE = B("smooth_sandstone");
            }
            case "plain" -> {
                Pal.COL = B("stripped_spruce_log", "axis", "y");
                Pal.RED = B("spruce_planks");
                Pal.WHITE = B("white_terracotta");
            }
            case "grey" -> {
                Pal.COL = B("stripped_mangrove_log", "axis", "y");
                Pal.RED = B("mangrove_planks");
                Pal.WHITE = B("light_gray_terracotta");
            }
            default -> throw new IllegalArgumentException("KeyError: " + name);
        }
    }

    // ============================================================================================ roof themes

    public static final LinkedHashMap<String, Map<String, Object>> ROOFS = new LinkedHashMap<>();
    public static final Map<String, Object> ROOF = new LinkedHashMap<>();
    private static boolean loaded;

    /** Load the roof themes (module import time in Python). Called by Gen.init. */
    static void load() {
        ROOFS.clear();
        ROOFS.putAll(Materials.roofs());
        registerRoofSets(ROOFS);
        ROOF.clear();
        ROOF.putAll(ROOFS.get("grey"));
        // bgen: ROOFS.setdefault("jade", dict(tile="prismarine_brick", ...)) - "jade" is in roofs.json already
        if (!ROOFS.containsKey("jade")) {
            Map<String, Object> j = new LinkedHashMap<>();
            j.put("tile", "prismarine_brick");
            j.put("full", "prismarine_bricks");
            j.put("ridge", "dark_prismarine");
            j.put("cap", "dark_prismarine");
            ROOFS.put("jade", j);
        }
        loaded = true;
    }

    /** arch.register_roof_sets. */
    public static void registerRoofSets(Map<String, Map<String, Object>> roofs) {
        for (Map<String, Object> r : roofs.values()) {
            Blocks.SETS.put((String) r.get("tile"), new String[]{(String) r.get("stairs"), (String) r.get("slab")});
            if (Dyn.truthy(r.get("stripe_stairs")) && Dyn.truthy(r.get("stripe_slab")))
                Blocks.SETS.put(r.get("tile") + ":stripe", new String[]{(String) r.get("stripe_stairs"), (String) r.get("stripe_slab")});
        }
    }

    /** arch.roof_theme. */
    public static void roofTheme(String name) {
        Map<String, Object> r = ROOFS.get(name);
        if (r == null) throw new IllegalArgumentException("KeyError: " + name);
        ROOF.clear();
        ROOF.putAll(r);
    }

    static String roof(String k) {
        return (String) ROOF.get(k);
    }

    // ============================================================================================ masonry

    static final String[] MASONRY = new String[10];

    static {
        for (int i = 0; i < 6; i++) MASONRY[i] = B("stone_bricks");
        MASONRY[6] = MASONRY[7] = B("mossy_stone_bricks");
        MASONRY[8] = B("cracked_stone_bricks");
        MASONRY[9] = B("andesite");
    }

    public static String masonry(int x, int y, int z) {
        long h = ((long) x * 73856093L) ^ ((long) y * 19349663L) ^ ((long) z * 83492791L);
        return MASONRY[(int) Math.floorMod(h, (long) MASONRY.length)];
    }

    // ============================================================================================ shared parts

    /** arch.positions. */
    public static List<Integer> positions(List<Integer> bays) {
        int total = 0;
        for (int b : bays) total += b;
        List<Integer> xs = new ArrayList<>();
        xs.add(Math.floorDiv(-total, 2));
        for (int bw : bays) xs.add(xs.get(xs.size() - 1) + bw);
        int off = Math.floorDiv(xs.get(0) + xs.get(xs.size() - 1), 2);
        List<Integer> out = new ArrayList<>();
        for (int x : xs) out.add(x - off);
        return out;
    }

    /** arch.ring_cells: (u, v, face). */
    public static List<Object[]> ringCells(int hx, int hz) {
        List<Object[]> cells = new ArrayList<>();
        for (int x = -hx; x <= hx; x++) {
            cells.add(new Object[]{x, -hz, "north"});
            cells.add(new Object[]{x, hz, "south"});
        }
        for (int z = -hz + 1; z < hz; z++) {
            cells.add(new Object[]{-hx, z, "west"});
            cells.add(new Object[]{hx, z, "east"});
        }
        return cells;
    }

    /** A platform material {core, stairs, slab} (bgen.platform_mat), or null for white jade. */
    public record Mat(String core, String stairs, String slab) {
    }

    public interface Ground {
        int at(int x, int z);
    }

    /** arch.xumizuo. */
    public static void xumizuo(Frame f, int hx, int hz, int top, int height, Ground ground, Mat mat) {
        String core = mat != null ? mat.core : Pal.JADE, band = mat != null ? mat.core : Pal.JADE_BRICK;
        for (int u = -hx; u <= hx; u++)
            for (int v = -hz; v <= hz; v++) {
                int base = top - height;
                if (ground != null) {
                    int g = ground.at(f.wx(u, v), f.wz(u, v));
                    if (g < base) f.col(u, v, g + 1, base - 1, Pal.STONE);
                }
                f.col(u, v, base, top, core);
            }
        if (height >= 3) {
            for (Object[] c : ringCells(hx, hz)) {
                int u = (int) c[0], v = (int) c[1];
                String inward = Blocks.OPP.get((String) c[2]);
                f.put(u, top - 2, v, mat != null ? B(mat.stairs, "facing", inward, "half", "bottom") : stair("quartz", inward, "bottom"));
                f.put(u, top - 1, v, AIR);
                f.put(u, top, v, mat != null ? B(mat.stairs, "facing", inward, "half", "top") : stair("quartz", inward, "top"));
            }
            for (Object[] c : ringCells(hx - 1, hz - 1)) f.put((int) c[0], top - 1, (int) c[1], band);
        }
    }

    /** arch.balustrade: gaps (u0, u1) in front, back_gaps behind, side_gaps (v0, v1) on both sides. */
    public static void balustrade(Fr f, int hx, int hz, int y, int[][] gaps, int[][] backGaps, int step, boolean sides, int[][] sideGaps) {
        List<int[]> ring = new ArrayList<>();          // (u, v, along_u)
        for (int u = -hx; u <= hx; u++) ring.add(new int[]{u, hz, 1});
        for (int u = -hx; u <= hx; u++) ring.add(new int[]{u, -hz, 1});
        if (sides) {
            for (int v = -hz + 1; v < hz; v++) ring.add(new int[]{-hx, v, 0});
            for (int v = -hz + 1; v < hz; v++) ring.add(new int[]{hx, v, 0});
        }
        for (int[] c : ring) {
            int u = c[0], v = c[1];
            boolean alongU = c[2] == 1;
            int[][] g = v == hz ? gaps : v == -hz ? backGaps : new int[0][];
            boolean inG = false, inS = false, edgeG = false, edgeS = false;
            for (int[] ac : g) {
                if (ac[0] <= u && u <= ac[1]) inG = true;
                if (u == ac[0] - 1 || u == ac[1] + 1) edgeG = true;
            }
            for (int[] ac : sideGaps) {
                if (ac[0] <= v && v <= ac[1]) inS = true;
                if (v == ac[0] - 1 || v == ac[1] + 1) edgeS = true;
            }
            if ((alongU && inG) || (!alongU && inS)) continue;
            boolean post = alongU ? Math.floorMod(u, step) == 0 : Math.floorMod(v, step) == 0;
            if (Math.abs(u) == hx && Math.abs(v) == hz) post = true;
            if ((alongU && edgeG) || (!alongU && edgeS)) post = true;
            if (post) {
                f.put(u, y + 1, v, B("quartz_pillar", "axis", "y"));
                f.put(u, y + 2, v, slab("quartz"));
            } else
                f.put(u, y + 1, v, alongU ? B("diorite_wall", "east", "low", "west", "low", "up", false) : B("diorite_wall", "north", "low", "south", "low", "up", false));
        }
    }

    public static void lanternPost(Fr f, int u, int y, int v) {
        putLamp(f, "post", u, y, v, 1, null);
    }

    // 台阶的坡度
    static final Map<String, Integer> STEP_SLOPES = Map.of("45", 1, "27", 2, "18", 3, "14", 4);
    static final Map<Integer, String[]> STEP_PATTERN = Map.of(1, new String[]{"stair"}, 2, new String[]{"slab", "full"},
            3, new String[]{"slab", "stair", "full"}, 4, new String[]{"slab", "slab", "full", "full"});

    /** arch.step_run. */
    public static int stepRun(Object slope) {
        String s = Dyn.truthy(slope) ? Dyn.str(slope) : "45";
        return STEP_SLOPES.getOrDefault(s, 1);
    }

    /** One cell of a flight: k cells out, dt blocks under the top step's layer, kind. */
    public record Step(int k, int dt, String kind) {
    }

    /** arch.flight. */
    public static List<Step> flight(int rise, int run) {
        String[] pat = STEP_PATTERN.getOrDefault(run, STEP_PATTERN.get(1));
        int n = pat.length;
        List<Step> out = new ArrayList<>();
        for (int k = 0; k < rise * n; k++) out.add(new Step(k, Math.floorDiv(k, n), pat[n - 1 - Math.floorMod(k, n)]));
        return out;
    }

    /** arch.slab_for. */
    public static String slabFor(String stairs, String fallback) {
        String s = Blocks.name(stairs).replace("_stairs", "_slab");
        return Env.present(s) ? s : fallback;
    }

    public static String slabFor(String stairs) {
        return slabFor(stairs, "minecraft:stone_brick_slab");
    }

    /** arch.step_block. */
    public static String stepBlock(String kind, String stairs, String slab, String facing) {
        if (kind.equals("stair")) return B(stairs, "facing", facing, "half", "bottom");
        return B(slab, "type", kind.equals("slab") ? "bottom" : "double");
    }

    /** arch.stairs_down: returns v after the last step. */
    public static int stairsDown(Fr f, int uHalf, int vStart, int yTop, int yBottom, boolean yulu, int lanternsEvery, Mat mat, int run) {
        String edge = mat != null ? mat.core : Pal.JADE;
        String road = mat != null ? mat.core : B("chiseled_quartz_block");
        String cap = mat != null ? B(mat.slab, "type", "bottom") : slab("quartz");
        String[] pat = STEP_PATTERN.getOrDefault(run, STEP_PATTERN.get(1));
        int v = vStart, y = yTop, steps = 0;
        while (y > yBottom) {
            if (steps != 0 && steps % 12 == 0) {
                for (int dv = 0; dv < 4; dv++)
                    for (int u = -uHalf; u <= uHalf; u++) {
                        f.put(u, y, v + dv, Math.abs(u) == uHalf ? edge : Pal.FLOOR);
                        under(f, u, y, v + dv, yBottom, edge);
                        for (int yy = y + 1; yy < y + 5; yy++) f.put(u, yy, v + dv, AIR);
                    }
                if (lanternsEvery != 0) {
                    lanternPost(f, -uHalf - 1, y, v);
                    lanternPost(f, uHalf + 1, y, v);
                }
                v += 4;
            }
            for (int pi = pat.length - 1; pi >= 0; pi--) {
                String kind = pat[pi];
                for (int u = -uHalf; u <= uHalf; u++) {
                    if (yulu && Math.abs(u) <= Math.max(1, Math.floorDiv(uHalf, 5))) f.put(u, y, v, Math.floorMod(v, 2) == 0 ? road : edge);
                    else if (Math.abs(u) == uHalf) {
                        f.put(u, y, v, edge);
                        f.put(u, y + 1, v, cap);
                    } else {
                        String st;
                        if (mat != null) st = stepBlock(kind, mat.stairs, mat.slab, "north");
                        else st = kind.equals("stair") ? stair("quartz", "north") : slab("quartz", kind.equals("slab") ? "bottom" : "double");
                        f.put(u, y, v, st);
                    }
                    under(f, u, y, v, yBottom, edge);
                    for (int yy = y + (Math.abs(u) == uHalf ? 2 : 1); yy < y + 5; yy++) f.put(u, yy, v, AIR);
                }
                v++;
            }
            y--;
            steps++;
        }
        return v;
    }

    private static void under(Fr f, int u, int y, int v, int yBottom, String edge) {
        for (int yy = y - 1; yy > yBottom; yy--) {
            String s = f.get(u, yy, v);
            if (s == null || s.equals(AIR)) f.put(u, yy, v, edge);
        }
    }

    // ============================================================================================ roofs

    /** A roof height field: (u, v) -> height, in insertion order. */
    public static final class H extends LinkedHashMap<Long, Double> {
        public Double at(int u, int v) {
            return get(Canvas.ckey(u, v));
        }

        public void put(int u, int v, double val) {
            put(Canvas.ckey(u, v), val);
        }

        public double max() {
            double m = Double.NEGATIVE_INFINITY;
            boolean any = false;
            for (double d : values()) {
                if (!any || d > m) m = d;
                any = true;
            }
            if (!any) throw new IllegalArgumentException("max() arg is an empty sequence");
            return m;
        }
    }

    /** arch.zhewu: (ds, hs). */
    static double[][] zhewu(double run, double rise) {
        int n = 4;
        double[] hs = new double[n + 1];
        hs[n] = rise;
        double drop = rise / 10.0;
        for (int k = n - 1; k > 0; k--) {
            hs[k] = hs[k + 1] * k / (k + 1) - drop;
            drop /= 2;
        }
        double[] ds = new double[n + 1];
        for (int i = 0; i <= n; i++) ds[i] = run * i / n;
        return new double[][]{ds, hs};
    }

    static double profile(double[] ds, double[] hs, double d) {
        if (d <= 0) return hs[0] + d * (hs[1] - hs[0]) / (ds[1] - ds[0]);
        for (int i = 0; i < ds.length - 1; i++) {
            if (d <= ds[i + 1]) {
                double t = (d - ds[i]) / (ds[i + 1] - ds[i]);
                return hs[i] + t * (hs[i + 1] - hs[i]);
            }
        }
        return hs[hs.length - 1];
    }

    /** arch.roof_surface. gableX: null for none; push: (p0, p1); inner: null or (iu, iv). */
    public static H roofSurface(int ex, int ez, int purlinY, int overhang, double run, double rise, Integer gableX, int[] layers, int[] push, int[] inner) {
        double[][] z = zhewu(run, rise);
        double[] ds = z[0], hs = z[1];
        H h = new H();
        for (int u = -ex - 3; u < ex + 4; u++)
            for (int v = -ez - 3; v < ez + 4; v++) {
                int eZ = ez - Math.abs(v), eX = ex - Math.abs(u);
                if (inner != null && Math.abs(u) <= inner[0] && Math.abs(v) <= inner[1]) continue;
                int kz = Math.max(eX, 0), kx = Math.max(eZ, 0);
                if (eZ < 0 && !((eZ == -1 && push[0] != 0 && kz <= push[0]) || (eZ == -2 && push[1] != 0 && kz <= push[1]))) continue;
                if (eX < 0 && !((eX == -1 && push[0] != 0 && kx <= push[0]) || (eX == -2 && push[1] != 0 && kx <= push[1]))) continue;
                int e;
                if (gableX != null && Math.abs(u) <= gableX) {
                    if (gableX >= ex && eX < 0) continue;
                    e = eZ;
                } else e = Math.min(eZ, eX);
                int dd = Math.max(e, 0) - overhang;
                double y = dd < 0 ? purlinY + 0.35 * dd : purlinY + profile(ds, hs, dd);
                if (!(gableX != null && gableX >= ex)) {
                    int k = Math.max(Math.max(eZ, eX), 0);
                    int cnt = 0;
                    for (int L : layers) if (k <= L) cnt++;
                    y += 0.5 * cnt;
                }
                h.put(u, v, y);
            }
        return h;
    }

    public interface BaseFn {
        int at(int u, int v, double val);
    }

    static final String[] DIRS = {"north", "south", "west", "east"};
    static final int[][] DIRD = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};

    /** arch.raster_roof. */
    public static void rasterRoof(Fr f, H h, BaseFn baseFn, String mat, String full, boolean rafters, Integer minY) {
        if (minY != null) {
            H h2 = new H();
            int m = minY + (rafters ? 1 : 0);
            for (Map.Entry<Long, Double> e : h.entrySet()) h2.put(e.getKey(), Math.max(e.getValue(), m));
            h = h2;
        }
        if (mat == null || mat.isEmpty()) mat = roof("tile");
        String fullB = B(full != null && !full.isEmpty() ? full : roof("full"));
        String stripe = Blocks.SETS.containsKey(mat + ":stripe") ? mat + ":stripe" : null;
        for (Map.Entry<Long, Double> e : h.entrySet()) {
            int u = Canvas.cx(e.getKey()), v = Canvas.cz(e.getKey());
            double val = e.getValue();
            int y = (int) Math.floor(val);
            double fr = val - y;
            Double[] nb = new Double[4];
            for (int i = 0; i < 4; i++) nb[i] = h.at(u + DIRD[i][0], v + DIRD[i][1]);
            Double upV = null;
            String upD = null;
            for (int i = 0; i < 4; i++) {
                if (nb[i] == null) continue;
                if (upV == null || nb[i] > upV || (nb[i].doubleValue() == upV && DIRS[i].compareTo(upD) > 0)) {
                    upV = nb[i];
                    upD = DIRS[i];
                }
            }
            if (fr >= 0.5 && upV != null && upV >= val + 0.25) {
                int row = (upD.equals("north") || upD.equals("south")) ? u : v;
                f.put(u, y, v, stair(stripe != null && Math.floorMod(row, 2) != 0 ? stripe : mat, upD));
            } else if (fr >= 0.5) f.put(u, y, v, fr < 0.85 ? slab(rowMat(mat, stripe, u, v, upD), "top") : fullB);
            else f.put(u, y, v, slab(rowMat(mat, stripe, u, v, upD), "bottom"));
            double low = val;
            boolean any = false, edge = false;
            for (Double n : nb) {
                if (n == null) {
                    edge = true;
                    continue;
                }
                if (!any || n < low) low = n;
                any = true;
            }
            if (!any) low = val;
            int bottom = minY == null ? baseFn.at(u, v, val) : Math.max(minY, baseFn.at(u, v, val));
            int topFill = !edge ? y - 1 : Math.min(y - 1, (int) Math.floor(low));
            for (int yy = Math.min(bottom, (int) Math.floor(low) + 1); yy <= topFill; yy++)
                if (f.get(u, yy, v) == null) f.put(u, yy, v, fullB);
            if (edge && rafters && f.get(u, y - 1, v) == null) f.put(u, y - 1, v, slab(Pal.WOOD, "top"));
        }
    }

    public static void rasterRoof(Fr f, H h, BaseFn baseFn, Integer minY) {
        rasterRoof(f, h, baseFn, null, null, true, minY);
    }

    static String rowMat(String mat, String stripe, int u, int v, String upDir) {
        if (stripe == null || upDir == null) return mat;
        int row = (upDir.equals("north") || upDir.equals("south")) ? u : v;
        return Math.floorMod(row, 2) != 0 ? stripe : mat;
    }

    // ============================================================================================ ornaments and lamps

    /** arch.ORN: the ornament and lamp choices of the building being drawn. */
    static final Map<String, Object> ORN_CHOSEN = new LinkedHashMap<>();
    static PyRandom ORN_RNG;
    static String ORN_TIER;
    static Map<String, Map<String, Object>> ORN_LAMPS;     // null until begin_ornaments (the "lamps" key)
    static PyRandom ORN_LAMP_RNG;
    static final int DEFAULT_WEIGHT = 3;
    private static final Object NONE = new Object();

    public static void beginOrnaments(String key, String tier) {
        ORN_CHOSEN.clear();
        ORN_RNG = PyRandom.ofString("ornaments/" + key);
        ORN_TIER = tier;
        ORN_LAMPS = new LinkedHashMap<>();
        ORN_LAMP_RNG = PyRandom.ofString("lamps/" + key);
    }

    public static final List<String> INDOOR_MOUNTS = List.of("ceiling", "floor", "inlay");

    /** One cell of a lamp or an ornament: (dy, block). */
    public record Cell(int dy, String s) {
    }

    @SuppressWarnings("unchecked")
    static List<Object> blocksOf(Map<String, Object> e) {
        Object b = e.get("blocks");
        return b == null ? List.of() : (List<Object>) b;
    }

    static double weight(Map<String, Object> e) {
        Object w = e.get("weight");
        return w == null ? 1 : ((Number) w).doubleValue();
    }

    /** arch.lamp_cells. */
    public static List<Cell> lampCells(String mount, int n, Map<String, String> slots) {
        if (Parts.LIGHTS.isEmpty()) Parts.reloadLights();
        if (ORN_LAMPS == null) ORN_LAMPS = new LinkedHashMap<>();
        Map<String, Map<String, Object>> chosen = ORN_LAMPS;
        if (!chosen.containsKey(mount)) {
            List<Map<String, Object>> cands = new ArrayList<>();
            for (Map<String, Object> e : Parts.LIGHTS.values())
                if (mount.equals(e.get("mount")) && (!Dyn.truthy(e.get("tiers")) || Dyn.list(e.get("tiers")).contains(ORN_TIER))) cands.add(e);
            PyRandom rng = ORN_LAMP_RNG != null ? ORN_LAMP_RNG : PyRandom.ofString("lamps/" + mount);
            if (cands.isEmpty()) chosen.put(mount, null);
            else {
                double[] w = new double[cands.size()];
                for (int i = 0; i < w.length; i++) w[i] = weight(cands.get(i));
                chosen.put(mount, cands.get(rng.choicesIndex(w, 1)[0]));
            }
        }
        Map<String, Object> e = chosen.get(mount);
        if (e == null) return List.of();
        Map<String, String> sl = new LinkedHashMap<>();
        if (slots != null) sl.putAll(slots);
        sl.putIfAbsent("rail", "spruce_fence");
        List<Cell> out = new ArrayList<>();
        for (Object o : blocksOf(e)) {
            List<Object> c = Dyn.list(o);
            int dy = Dyn.toInt(c.get(0));
            String s = ((String) c.get(1)).replace("$n", String.valueOf(Math.max(1, Math.min(4, n))));
            if (s.equals("$jade")) s = Pal.JADE;
            else if (s.startsWith("$") && sl.containsKey(Blocks.parse(s.substring(1)).name())) {
                Blocks.Parsed p = Blocks.parse(s.substring(1));
                s = B(sl.get(p.name()), p.props());
            } else s = slotBlock(s, null);
            out.add(new Cell(dy, s));
        }
        return out;
    }

    /** arch.put_lamp. */
    public static void putLamp(Fr f, String mount, int u, int y, int v, int n, Map<String, String> slots) {
        for (Cell c : lampCells(mount, n, slots)) f.put(u, y + c.dy, v, c.s);
    }

    /** arch._slot_block. */
    static String slotBlock(String s, Map<String, String> slots) {
        if (!s.startsWith("$")) return s;
        Blocks.Parsed p = Blocks.parse(s.substring(1));
        String name = p.name();
        if (slots != null && Dyn.truthy(slots.get(name))) return B(slots.get(name), p.props());
        if (name.equals("tile_stairs"))
            return stair(roof("tile"), p.props().getOrDefault("facing", "north"), p.props().getOrDefault("half", "bottom"), p.props().get("shape"));
        if (name.equals("tile_slab")) return slab(roof("tile"), p.props().getOrDefault("type", "bottom"));
        if (name.equals("gold")) return Pal.GOLD;
        String id = Dyn.truthy(ROOF.get(name)) ? (String) ROOF.get(name) : roof("ridge");
        return B(id, p.props());
    }

    /** arch.place_ornament: draw an ornament part, or false when the code draws its own. */
    @SuppressWarnings("unchecked")
    public static boolean placeOrnament(Fr f, String point, int u0, int y0, int v0, int size, boolean mx, boolean mz, String face, Map<String, String> slots) {
        if (ORN_RNG == null) return false;
        if (Parts.ORNAMENTS.isEmpty()) Parts.reloadOrnaments();
        String key = point + "\u0000" + size;
        if (!ORN_CHOSEN.containsKey(key)) {
            List<Map<String, Object>> cands = new ArrayList<>();
            for (Map<String, Object> e : Parts.ORNAMENTS.values()) {
                if (!point.equals(e.get("point"))) continue;
                if (Dyn.truthy(e.get("tiers")) && !Dyn.list(e.get("tiers")).contains(ORN_TIER)) continue;
                int mn = e.get("min_size") == null ? 0 : Dyn.toInt(e.get("min_size"));
                int mxs = e.get("max_size") == null ? 999 : Dyn.toInt(e.get("max_size"));
                if (mn <= size && size <= mxs) cands.add(e);
            }
            if (cands.isEmpty()) ORN_CHOSEN.put(key, NONE);
            else {
                double[] w = new double[cands.size() + 1];
                for (int i = 0; i < cands.size(); i++) w[i] = weight(cands.get(i));
                w[cands.size()] = !point.equals("ridge_mid") ? DEFAULT_WEIGHT : DEFAULT_WEIGHT * 2;
                int i = ORN_RNG.choicesIndex(w, 1)[0];
                ORN_CHOSEN.put(key, i < cands.size() ? cands.get(i) : NONE);
            }
        }
        Object part = ORN_CHOSEN.get(key);
        if (part == NONE) return false;
        for (Object o : Dyn.list(((Map<String, Object>) part).get("cells"))) {
            List<Object> c = Dyn.list(o);
            int du = Dyn.toInt(c.get(0)), dy = Dyn.toInt(c.get(1)), dv = Dyn.toInt(c.get(2));
            String st = Blocks.mirrorState(slotBlock((String) c.get(3), slots), mx, mz);
            if (mx) du = -du;
            if (mz) dv = -dv;
            if (!face.equals("south")) {
                int nu, nv;
                switch (face) {
                    case "north" -> {
                        nu = -du;
                        nv = -dv;
                    }
                    case "east" -> {
                        nu = dv;
                        nv = -du;
                    }
                    case "west" -> {
                        nu = -dv;
                        nv = du;
                    }
                    default -> throw new IllegalArgumentException("KeyError: " + face);
                }
                du = nu;
                dv = nv;
                st = Blocks.rotateState(st, face);
            }
            f.put(u0 + du, y0 + dy, v0 + dv, st);
        }
        return true;
    }

    public static boolean placeOrnament(Fr f, String point, int u0, int y0, int v0, int size) {
        return placeOrnament(f, point, u0, y0, v0, size, false, false, "south", null);
    }

    /** arch.main_ridge. */
    public static void mainRidge(Fr f, int half, int ridgeY, int chiwei) {
        int extra = Math.max(0, Math.floorDiv(chiwei, 8));
        for (int u = -half; u <= half; u++) {
            for (int v = -1; v <= 1; v++) {
                f.put(u, ridgeY + 1, v, v == 0 ? B(roof("ridge")) : stair(roof("tile"), v == 1 ? "north" : "south"));
                if (v != 0) {
                    for (int yy = ridgeY; yy > ridgeY - 5; yy--) {
                        if (f.get(u, yy, v) != null) break;
                        f.put(u, yy, v, B(roof("full")));
                    }
                }
            }
            for (int e = 0; e < extra + 1; e++) {
                f.put(u, ridgeY + 2 + e, 0, B(roof("ridge")));
                if (e < extra) for (int v : new int[]{-1, 1}) f.put(u, ridgeY + 2 + e, v, stair(roof("tile"), v == 1 ? "north" : "south"));
            }
            f.put(u, ridgeY + 3 + extra, 0, slab(roof("tile")));
        }
        placeOrnament(f, "ridge_mid", 0, ridgeY + 2 + extra, 0, half);
        for (int sgn : new int[]{-1, 1}) {
            int u0 = sgn * (half + 1);
            if (placeOrnament(f, "ridge_end", u0, ridgeY, 0, chiwei, sgn < 0, false, "south", null)) continue;
            for (int dy = 1; dy <= chiwei; dy++) f.put(u0, ridgeY + dy, 0, B(roof("ridge")));
            f.put(u0 - sgn, ridgeY + chiwei, 0, stair(roof("tile"), sgn > 0 ? "east" : "west", "top"));
            if (chiwei >= 5) f.put(u0 - sgn * 2, ridgeY + chiwei - 1, 0, stair(roof("tile"), sgn > 0 ? "east" : "west", "top"));
            f.put(u0, ridgeY + chiwei + 1, 0, slab(roof("tile")));
            f.put(u0 + sgn, ridgeY + 2, 0, stair(roof("tile"), sgn > 0 ? "west" : "east"));
        }
    }

    /** arch.ridge_cells. */
    public static void ridgeCells(Fr f, H h, List<int[]> cells) {
        for (int[] c : cells) {
            Double val = h.at(c[0], c[1]);
            if (val != null) f.put(c[0], (int) Math.floor(val) + 1, c[1], B(roof("ridge")));
        }
    }

    /** arch.finial. */
    public static void finial(Fr f, int y, int height) {
        if (placeOrnament(f, "finial", 0, y, 0, height)) return;
        f.put(0, y + 1, 0, B(roof("ridge")));
        for (int dy = 2; dy < height; dy++) f.put(0, y + dy, 0, dy < height - 2 ? B("chain", "axis", "y") : Pal.GOLD);
        f.put(0, y + height, 0, B("lightning_rod", "facing", "up"));
    }

    /** arch.solid_at: something (not air) at local (u, y, v): its blocks over its column runs. */
    public static boolean solidAt(Frame f, int u, int y, int v) {
        String s = f.get(u, y, v);
        if (s != null) return !s.equals(AIR);
        List<Canvas.Run> rs = f.cv.cols.get(Canvas.ckey(f.wx(u, v), f.wz(u, v)));
        if (rs != null)
            for (int i = rs.size() - 1; i >= 0; i--) {
                Canvas.Run r = rs.get(i);
                if (r.y0() <= y && y <= r.y1()) return !r.s().equals(AIR);
            }
        return false;
    }

    /** arch.unseen_light. */
    public static String unseenLight() {
        return B("light", "level", 15, "waterlogged", false);
    }

    public static void chimney(Fr f, int u, int y, int v, int height) {
        for (int k = 0; k < height; k++) f.put(u, y + k, v, B("bricks"));
        f.put(u, y + height, v, B("campfire", "lit", true));
    }
}
