package com.flyingeaves.gen;

import com.flyingeaves.gen.py.IntPair;
import com.flyingeaves.gen.py.PySet;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.flyingeaves.gen.Blocks.AIR;
import static com.flyingeaves.gen.Blocks.B;

/**
 * bgen_daemon.build as a library call: a request (a kind, or a composed building) -> a canvas with its ground work
 * (plinths, the footing under things standing near the ground, the clearing of the bounding box), exported as the
 * .dvcr the game stamps, and the result dict. No files are watched here: the in-game backend calls {@link #build}.
 */
public final class Generator {
    private Generator() {
    }

    private static boolean loaded;

    /** Load the data (the Python modules' import time). Call again after Env.init to reload. */
    public static synchronized void init() {
        Arch.load();
        Bgen.load();
        Parts.LIGHTS.clear();
        Parts.BAYS.clear();
        Parts.ORNAMENTS.clear();
        Furniture.load();
        loaded = true;
    }

    static final String CLEAR_NATURAL = "minecraft:structure_void";

    static boolean plant(String st) {
        return Blocks.containsAny(st, "bamboo", "sapling", "flower", "fern", "grass", "sugar_cane", "tulip", "dandelion", "poppy",
                "orchid", "allium", "bluet", "daisy", "lily_of", "rose", "peony", "lilac", "_bush");
    }

    static boolean standing(String st) {
        String name = Blocks.name(st);
        if (Blocks.containsAny(name, "wall_banner", "wall_sign", "wall_torch", "wall_head", "wall_hanging", "trapdoor", "fence_gate")) return false;
        if (name.contains("lantern")) return !st.contains("hanging=true");
        return plant(st) || name.contains("fence") || name.endsWith("_wall") || name.endsWith("_sign") || name.endsWith("_banner")
                || name.contains("bamboo") || name.endsWith("_post");
    }

    /** A base: ((x0, z0, x1, z1), ground y, base block or null). */
    record Base(int[] fp, int y, String blk) {
    }

    /**
     * bgen_daemon.ground_grid: rows by z. A town (ground given) or no rooms: y everywhere; else the city-block distance
     * transform over (distance, -floor) packed as distance * K + OFF - floor, as Python does it.
     */
    static int[][] groundGrid(int x0, int z0, int x1, int z1, Map<Long, Integer> ground, List<Base> bases, int y) {
        int w = x1 - x0 + 1, d = z1 - z0 + 1;
        int[][] g = new int[d][w];
        if (!ground.isEmpty() || bases == null || bases.isEmpty()) {
            for (int[] row : g) java.util.Arrays.fill(row, y);
        } else {
            final long K = 1L << 16, OFF = 1L << 15, INF = 1L << 62;
            int ax0 = x0, az0 = z0, ax1 = x1, az1 = z1;
            for (Base b : bases) {
                ax0 = Math.min(ax0, b.fp[0]);
                az0 = Math.min(az0, b.fp[1]);
                ax1 = Math.max(ax1, b.fp[2]);
                az1 = Math.max(az1, b.fp[3]);
            }
            int W = ax1 - ax0 + 1, D = az1 - az0 + 1;
            long[][] rows = new long[D][W];
            for (long[] r : rows) java.util.Arrays.fill(r, INF);
            for (Base b : bases) {
                long c = OFF - b.y;
                int a = b.fp[0] - ax0, e = b.fp[2] - ax0 + 1;
                for (int k = b.fp[1] - az0; k <= b.fp[3] - az0; k++) {
                    long[] r = rows[k];
                    for (int i = a; i < e; i++) if (c < r[i]) r[i] = c;
                }
            }
            for (int k = 0; k < D; k++) {
                long[] r = rows[k];
                for (int i = 1; i < W; i++) if (r[i - 1] + K < r[i]) r[i] = r[i - 1] + K;
                for (int i = W - 2; i >= 0; i--) if (r[i + 1] + K < r[i]) r[i] = r[i + 1] + K;
            }
            for (int k = 1; k < D; k++) for (int i = 0; i < W; i++) rows[k][i] = Math.min(rows[k][i], rows[k - 1][i] + K);
            for (int k = D - 2; k >= 0; k--) for (int i = 0; i < W; i++) rows[k][i] = Math.min(rows[k][i], rows[k + 1][i] + K);
            for (int k = z0 - az0; k <= z1 - az0; k++)
                for (int i = x0 - ax0; i <= x1 - ax0; i++) g[k - (z0 - az0)][i - (x0 - ax0)] = (int) (OFF - Math.floorMod(rows[k][i], K));
        }
        for (Map.Entry<Long, Integer> e : ground.entrySet()) {
            int gx = Canvas.cx(e.getKey()), gz = Canvas.cz(e.getKey());
            if (x0 <= gx && gx <= x1 && z0 <= gz && gz <= z1) g[gz - z0][gx - x0] = e.getValue();
        }
        return g;
    }

    /** bgen_daemon.clear_box. */
    static void clearBox(Canvas cv, PySet<IntPair> cols, Set<IntPair> frames, int top, Map<Long, Integer> ground, List<Base> bases, int y) {
        int[] bb = cv.bbox();
        int x0 = bb[0], z0 = bb[1], x1 = bb[2], z1 = bb[3];
        if (!frames.isEmpty()) {
            for (IntPair f : frames) {
                x0 = Math.min(x0, f.a());
                z0 = Math.min(z0, f.b());
                x1 = Math.max(x1, f.a());
                z1 = Math.max(z1, f.b());
            }
        }
        int[][] g = groundGrid(x0, z0, x1, z1, ground, bases, y);
        String air = B("air");
        for (int bz = z0; bz <= z1; bz++) {
            int[] row = g[bz - z0];
            for (int i = 0; i < row.length; i++) {
                int yb = row[i];
                IntPair key = IntPair.of(x0 + i, bz);
                if (!ground.isEmpty() && !ground.containsKey(Canvas.ckey(x0 + i, bz))) continue;
                if (!cols.contains(key)) cv.colsAt(Canvas.ckey(x0 + i, bz)).add(0, new Canvas.Run(yb + 1, Math.max(yb + 1, top), frames.contains(key) ? air : CLEAR_NATURAL));
            }
        }
    }

    /** The result key under which {@link #build} with no outDir puts the .dvcr payload (a byte[]: take it out before
     *  writing the result as JSON). */
    public static final String PAYLOAD = "_payload";

    /** The in-game backend's progress line: the stage after the buildings (as the daemon's PROG.set). With
     *  {@link Compose#CANCEL} also asked between these stages: a superseded preview stops in the ground work too. */
    public static java.util.function.Consumer<String> STAGE = s -> {
    };

    /** bgen_daemon.build: generate one request, export it to outDir/<id>.dvcr, return the result dict. outDir null:
     *  no file, the uncompressed payload in the result under {@link #PAYLOAD} (the in-game backend). */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> build(Map<String, Object> req, Path outDir) throws IOException {
        if (!loaded) init();
        Canvas cv = new Canvas();
        int x = req.containsKey("x") ? Dyn.toInt(req.get("x")) : 0, y = req.containsKey("y") ? Dyn.toInt(req.get("y")) : 0,
                z = req.containsKey("z") ? Dyn.toInt(req.get("z")) : 0;
        List<Base> bases = null;
        Map<Long, Integer> ground = new HashMap<>();
        Set<Long> nofoot = new java.util.HashSet<>();
        Map<String, Object> res;
        boolean compose = "compose".equals(req.get("type"));
        if (compose) {
            res = Compose.buildCompose(req, cv);
            bases = new ArrayList<>();
            for (Object[] b : (List<Object[]>) res.get("bases")) bases.add(new Base((int[]) b[0], (int) b[1], (String) b[2]));
            if (res.get("ground") != null) ground = (Map<Long, Integer>) res.get("ground");
            if (res.get("nofoot") != null) nofoot = (Set<Long>) res.get("nofoot");
            y = Integer.MAX_VALUE;
            for (Base b : bases) y = Math.min(y, b.y);
        } else {
            Frame f = new Frame(cv, x, z, req.containsKey("facing") ? (String) req.get("facing") : "south");
            Object t = req.get("tier");
            res = Kinds.build(f, y, (String) req.get("kind"), t == null ? null : (String) t, req.containsKey("seed") ? Dyn.toInt(req.get("seed")) : 0);
            if (res == null) res = new LinkedHashMap<>();
        }
        if (cv.blocks.isEmpty()) throw new IllegalArgumentException("nothing generated");
        Compose.CANCEL.run();
        STAGE.accept("地基");
        final List<Base> fb = bases;
        final Map<Long, Integer> fground = ground;
        final int fy = y;
        Map<Long, List<Integer>> cell = new HashMap<>();
        for (int i = 0; i < (bases == null ? 0 : bases.size()); i++) {
            int[] fp = bases.get(i).fp;
            for (int gx = fp[0] >> 4; gx < (fp[2] >> 4) + 1; gx++)
                for (int gz = fp[1] >> 4; gz < (fp[3] >> 4) + 1; gz++) cell.computeIfAbsent(Canvas.ckey(gx, gz), k -> new ArrayList<>()).add(i);
        }
        class Near {
            Base nearest(int bx, int bz) {
                Base best = null;
                long bestK0 = 0, bestK1 = 0;
                for (int i : cell.getOrDefault(Canvas.ckey(bx >> 4, bz >> 4), List.of())) {
                    Base b = fb.get(i);
                    if (b.fp[0] <= bx && bx <= b.fp[2] && b.fp[1] <= bz && bz <= b.fp[3] && (best == null || -b.y < bestK1)) {
                        best = b;
                        bestK0 = 0;
                        bestK1 = -b.y;
                    }
                }
                if (best != null) return best;
                for (Base b : fb) {
                    long d = Math.max(Math.max(b.fp[0] - bx, bx - b.fp[2]), 0) + Math.max(Math.max(b.fp[1] - bz, bz - b.fp[3]), 0);
                    if (best == null || d < bestK0 || (d == bestK0 && -b.y < bestK1)) {
                        best = b;
                        bestK0 = d;
                        bestK1 = -b.y;
                    }
                }
                return best;
            }

            int baseY(int bx, int bz) {
                Integer g = fground.get(Canvas.ckey(bx, bz));
                if (g != null) return g;
                return fb != null && !fb.isEmpty() ? nearest(bx, bz).y : fy;
            }

            String stone(int bx, int yy, int bz) {
                String blk = fb != null && !fb.isEmpty() ? nearest(bx, bz).blk : null;
                return blk != null && !blk.isEmpty() ? blk : Arch.masonry(bx, yy, bz);
            }
        }
        Near N = new Near();
        PySet<IntPair> cols = new PySet<>();
        int top = Integer.MIN_VALUE;
        for (long k : cv.blocks.keySet()) {
            cols.add(IntPair.of(Canvas.kx(k), Canvas.kz(k)));
            top = Math.max(top, Canvas.ky(k));
        }
        LinkedHashMap<Long, Object[]> low = new LinkedHashMap<>();
        for (Map.Entry<Long, String> e : cv.blocks.entrySet()) {
            long k = e.getKey();
            String st = e.getValue();
            long ck = Canvas.ckey(Canvas.kx(k), Canvas.kz(k));
            Object[] cur = low.get(ck);
            int by = Canvas.ky(k);
            if (!st.equals(AIR) && by < (cur == null ? 1 << 30 : (int) cur[0])) low.put(ck, new Object[]{by, st});
        }
        for (Map.Entry<Long, Object[]> e : low.entrySet()) {
            long ck = e.getKey();
            if (nofoot.contains(ck)) continue;
            int bx = Canvas.cx(ck), bz = Canvas.cz(ck);
            int by = (int) e.getValue()[0];
            String st = (String) e.getValue()[1];
            int yb = N.baseY(bx, bz);
            if (yb + 1 < by && by <= yb + 5 && standing(st)) {
                boolean pl = plant(st);
                for (int yy = yb + 1; yy < by; yy++)
                    if (!cv.blocks.containsKey(Canvas.key(bx, yy, bz)))
                        cv.blocks.put(Canvas.key(bx, yy, bz), pl ? (yy == by - 1 ? B("grass_block") : B("dirt")) : N.stone(bx, yy, bz));
            }
        }
        Compose.CANCEL.run();
        Set<Long> grounded = new java.util.HashSet<>();
        for (long k : cv.blocks.keySet()) {
            int bx = Canvas.kx(k), by = Canvas.ky(k), bz = Canvas.kz(k);
            long ck = Canvas.ckey(bx, bz);
            if (nofoot.contains(ck)) continue;
            int yb = N.baseY(bx, bz);
            if (yb <= by && by <= yb + 2) grounded.add(ck);
        }
        for (IntPair c : cols) {
            int bx = c.a(), bz = c.b();
            long ck = Canvas.ckey(bx, bz);
            int yb = N.baseY(bx, bz);
            List<Canvas.Run> rs = cv.colsAt(ck);
            rs.add(0, new Canvas.Run(yb + 1, Math.max(yb + 1, top), B("air")));
            if (grounded.contains(ck)) {
                Object[] lw = low.get(ck);
                if (plant(lw == null ? "" : (String) lw[1])) {
                    rs.add(0, new Canvas.Run(yb, yb, B("grass_block")));
                    rs.add(0, new Canvas.Run(yb - 3, yb - 1, B("dirt")));
                } else {
                    boolean covered = false;
                    for (int yy : new int[]{yb, yb + 1}) if (!cv.blocks.getOrDefault(Canvas.key(bx, yy, bz), AIR).equals(AIR)) covered = true;
                    rs.add(0, new Canvas.Run(yb - 3, covered ? yb : yb - 1, N.stone(bx, 0, bz)));
                }
            }
        }
        Set<IntPair> frames = new java.util.HashSet<>();
        if (compose) {
            for (Object o : Dyn.list(req.get("rooms"))) {
                Map<String, Object> rm = Dyn.map(o);
                int ax0 = Math.min(Dyn.toInt(rm.get("x0")), Dyn.toInt(rm.get("x1"))), ax1 = Math.max(Dyn.toInt(rm.get("x0")), Dyn.toInt(rm.get("x1")));
                int az0 = Math.min(Dyn.toInt(rm.get("z0")), Dyn.toInt(rm.get("z1"))), az1 = Math.max(Dyn.toInt(rm.get("z0")), Dyn.toInt(rm.get("z1")));
                for (int bx = ax0; bx <= ax1; bx++) for (int bz = az0; bz <= az1; bz++) frames.add(IntPair.of(bx, bz));
            }
        }
        Compose.CANCEL.run();
        clearBox(cv, cols, frames, top, ground, bases, y);
        Compose.CANCEL.run();
        STAGE.accept("写出");
        Canvas.Encoded enc = cv.encode();
        if (outDir != null) Canvas.write(enc, outDir.resolve(req.get("id") + ".dvcr"));
        int[] nb = {enc.runs(), enc.blocks()};
        int[] bb = cv.bbox();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("blocks", nb[1]);
        out.put("runs", nb[0]);
        out.put("bbox", List.of(bb[0], y - 3, bb[1], bb[2], top, bb[3]));
        out.put("top", res.containsKey("top") ? Dyn.toInt(res.get("top")) : top);
        if (Dyn.truthy(res.get("sh"))) out.put("sh", res.get("sh"));
        if (outDir == null) out.put(PAYLOAD, enc.payload());
        return out;
    }
}
