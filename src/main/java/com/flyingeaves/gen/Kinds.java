package com.flyingeaves.gen;

import com.flyingeaves.gen.py.IntPair;
import com.flyingeaves.gen.py.IntTriple;
import com.flyingeaves.gen.py.Py;
import com.flyingeaves.gen.py.PyRandom;
import com.flyingeaves.gen.py.PySet;
import com.flyingeaves.gen.py.UcrtMath;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.flyingeaves.gen.Arch.H;
import static com.flyingeaves.gen.Arch.Pal;
import static com.flyingeaves.gen.Blocks.AIR;
import static com.flyingeaves.gen.Blocks.B;
import static com.flyingeaves.gen.Blocks.stair;

/**
 * bgen.build and its archetypes: the build menu's kinds (bgen.K, sites.py's included) - kind x tier x seed -> a
 * building. house is House.house; the others are here (tower, stilt, cave, round, pavilion, compound, hall, gate,
 * paifang, star, floating, street) and in Sites (the sect's special sites). Then the finishing passes of bgen.build
 * (clear_doorways, settle_loose, drop_floating, connect_panes).
 */
public final class Kinds {
    private Kinds() {
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> spec(String name) {
        Map<String, Object> s = (Map<String, Object>) Tables.KINDS.get(name);
        if (s == null) throw new IllegalArgumentException("KeyError: '" + name + "'");
        return s;
    }

    static double tk(String tier) {
        Double k = Bgen.TIER_K.get(tier);
        if (k == null) throw new IllegalArgumentException("KeyError: '" + tier + "'");
        return k;
    }

    static int[] range(Map<String, Object> spec, String key, int i) {
        List<Object> r = Dyn.list(Dyn.list(spec.get(key)).get(i));
        return new int[]{Dyn.toInt(r.get(0)), Dyn.toInt(r.get(1))};
    }

    static int[] pair(Map<String, Object> spec, String key) {
        List<Object> r = Dyn.list(spec.get(key));
        return new int[]{Dyn.toInt(r.get(0)), Dyn.toInt(r.get(1))};
    }

    static int randint(PyRandom rng, int[] r) {
        return rng.randint(r[0], r[1]);
    }

    /** dict(spec, k=v, ...). */
    static Map<String, Object> with(Map<String, Object> spec, Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>(spec);
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    static String W(Map<String, Object> W, String k) {
        return (String) W.get(k);
    }

    /** random.Random(rng.random()): a float seed hashes (hash(x) as an unsigned 64-bit key). */
    static PyRandom child(PyRandom rng) {
        return PyRandom.ofUnsigned64(floatHash(rng.random()));
    }

    /** CPython's hash of a finite float >= 0: v = M * 2^E -> M * 2^(E mod 61) mod (2^61 - 1). */
    static long floatHash(double v) {
        if (v == 0) return 0;
        if (!(v > 0) || Double.isInfinite(v)) throw new IllegalArgumentException("floatHash of " + v);
        long bits = Double.doubleToLongBits(v);
        int exp = (int) ((bits >> 52) & 0x7FF);
        long man = bits & ((1L << 52) - 1);
        int e;
        if (exp == 0) e = -1074;
        else {
            man |= 1L << 52;
            e = exp - 1075;
        }
        java.math.BigInteger P = java.math.BigInteger.ONE.shiftLeft(61).subtract(java.math.BigInteger.ONE);
        long h = java.math.BigInteger.valueOf(man).multiply(java.math.BigInteger.TWO.modPow(java.math.BigInteger.valueOf(Math.floorMod(e, 61)), P)).mod(P).longValue();
        return h;
    }

    interface Archetype {
        Map<String, Object> build(Frame f, int y, PyRandom rng, Map<String, Object> spec, String tier);
    }

    static final Map<String, Archetype> ARCHETYPES = new LinkedHashMap<>();

    static {
        ARCHETYPES.put("house", (f, y, rng, spec, tier) -> House.house(f, y, rng, spec, tier, null, null, null));
        ARCHETYPES.put("tower", Kinds::towerKind);
        ARCHETYPES.put("stilt", Kinds::stiltKind);
        ARCHETYPES.put("cave", Kinds::caveKind);
        ARCHETYPES.put("round", Kinds::roundKind);
        ARCHETYPES.put("pavilion", Kinds::pavilionKind);
        ARCHETYPES.put("compound", Kinds::compoundKind);
        ARCHETYPES.put("hall", Kinds::hallKind);
        ARCHETYPES.put("gate", Kinds::gateKind);
        ARCHETYPES.put("paifang", Kinds::paifangKind);
        ARCHETYPES.put("star", Kinds::starKind);
        ARCHETYPES.put("floating", Kinds::floatingKind);
        ARCHETYPES.put("street", Kinds::streetKind);
        ARCHETYPES.put("cloud_port", Sites::cloudPort);
        ARCHETYPES.put("spirit_mine", Sites::spiritMine);
        ARCHETYPES.put("beast_hill", Sites::beastHill);
        ARCHETYPES.put("trial_ground", Sites::trialGround);
        ARCHETYPES.put("forbidden", Sites::forbidden);
        ARCHETYPES.put("tomb", Sites::tomb);
    }

    /** bgen.build: one building of a kind at a tier (null: the kind's first) with a seed into frame f (origin = footprint centre, ground y). */
    public static Map<String, Object> build(Frame f, int y, String kindName, String tier, int seed) {
        Map<String, Object> spec = spec(kindName);
        PyRandom rng = PyRandom.ofString(kindName + "/" + (tier == null ? "None" : tier) + "/" + seed);
        if (tier == null || tier.isEmpty()) tier = (String) Dyn.list(spec.get("tiers")).get(0);
        Arch.beginOrnaments(kindName + "/" + tier + "/" + seed, tier);
        try {
            Archetype a = ARCHETYPES.get((String) spec.get("arch"));
            if (a == null) throw new IllegalArgumentException("KeyError: '" + spec.get("arch") + "'");
            Map<String, Object> res = a.build(f, y, rng, spec, tier);
            Finish.clearDoorways(f.cv);
            Finish.settleLoose(f.cv);
            if (!"floating".equals(spec.get("arch"))) dropFloating(f.cv, y, 12, 4);
            Finish.connectPanes(f.cv.blocks);
            return res;
        } finally {
            Arch.roofTheme("grey");
            Arch.style("red");
        }
    }

    // ------------------------------------------------------------------------------------------------ drop_floating

    static final int[][] NB18 = new int[18][];

    static {
        int i = 0;
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    int s = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                    if (0 < s && s <= 2) NB18[i++] = new int[]{dx, dy, dz};
                }
    }

    static boolean empty(String s) {
        return s == null || s.equals(AIR);
    }

    /** bgen.drop_floating: returns (moved, removed). */
    static int[] dropFloating(Canvas cv, int ground, int most, int fall) {
        PySet<IntTriple> loose = new PySet<>();
        for (long k : Terrain.floating(cv.blocks, ground, cv.cols)) loose.add(IntTriple.of(Canvas.kx(k), Canvas.ky(k), Canvas.kz(k)));
        int moved = 0, removed = 0;
        while (!loose.isEmpty()) {
            IntTriple seed = loose.pop();
            PySet<IntTriple> piece = new PySet<>();
            piece.add(seed);
            List<IntTriple> todo = new ArrayList<>();
            todo.add(seed);
            while (!todo.isEmpty()) {
                IntTriple p0 = todo.remove(todo.size() - 1);
                for (int[] d : NB18) {
                    IntTriple p = IntTriple.of(p0.a() + d[0], p0.b() + d[1], p0.c() + d[2]);
                    if (loose.contains(p)) {
                        loose.discard(p);
                        piece.add(p);
                        todo.add(p);
                    }
                }
            }
            if (piece.size() > most) continue;
            LinkedHashMap<IntTriple, String> blocks = new LinkedHashMap<>();
            for (IntTriple p : piece) {
                String s = cv.blocks.get(Canvas.key(p.a(), p.b(), p.c()));
                if (s != null) blocks.put(p, s);
            }
            int k;
            boolean broke = false;
            for (k = 1; k <= fall; k++) {
                boolean hit = false;
                for (IntTriple p : blocks.keySet()) {
                    IntTriple q = IntTriple.of(p.a(), p.b() - k, p.c());
                    if (!piece.contains(q) && !empty(cv.blocks.get(Canvas.key(q.a(), q.b(), q.c())))) {
                        hit = true;
                        break;
                    }
                }
                if (hit) {
                    broke = true;
                    break;
                }
            }
            if (!broke) k = fall + 1;
            k -= 1;
            boolean rests = false;
            if (k > 0) {
                for (IntTriple p : blocks.keySet()) {
                    IntTriple q = IntTriple.of(p.a(), p.b() - k - 1, p.c());
                    if (!empty(cv.blocks.get(Canvas.key(q.a(), q.b(), q.c()))) && !piece.contains(q)) {
                        rests = true;
                        break;
                    }
                }
            }
            for (IntTriple p : blocks.keySet()) cv.blocks.put(Canvas.key(p.a(), p.b(), p.c()), AIR);
            if (rests) {
                for (Map.Entry<IntTriple, String> e : blocks.entrySet()) {
                    IntTriple p = e.getKey();
                    cv.blocks.put(Canvas.key(p.a(), p.b() - k, p.c()), e.getValue());
                }
                moved++;
            } else if (blocks.size() > 4) {
                for (Map.Entry<IntTriple, String> e : blocks.entrySet()) {
                    IntTriple p = e.getKey();
                    cv.blocks.put(Canvas.key(p.a(), p.b(), p.c()), e.getValue());
                }
            } else removed++;
        }
        return new int[]{moved, removed};
    }

    // ------------------------------------------------------------------------------------------------ archetypes

    /** bgen.tower_kind. */
    static Map<String, Object> towerKind(Frame f, int y, PyRandom rng, Map<String, Object> spec, String tier) {
        double k = tk(tier);
        String twName = Bgen.pick(rng, spec.get("walls"));
        Map<String, Object> W = Bgen.setWalls(twName);
        Map<String, Object> bayCtx = new LinkedHashMap<>();
        bayCtx.put("chosen", new LinkedHashMap<String, Object>());
        bayCtx.put("tier", tier);
        bayCtx.put("walls", twName);
        Bgen.WALL_FILL[0] = W(W, "fill");
        Arch.roofTheme(Bgen.pick(rng, spec.get("themes")));
        int w = Bgen.odd(randint(rng, range(spec, "size", 0)) * (1 + (k - 1) * 0.7));
        int n = randint(rng, pair(spec, "storeys")) + (k > 2 ? 1 : 0);
        int sh = (int) Py.round(randint(rng, pair(spec, "sh")) * (1 + (k - 1) * 0.3));
        int hx = Math.floorDiv(w, 2);
        String form = Bgen.pick(rng, spec.get("roofs"));
        Arch.xumizuo(f, hx + 3, hx + 3, y + 2, 2, null, null);
        int yb = y + 2;
        Set<IntPair> well = new HashSet<>();
        for (int s = 0; s < n; s++) {
            int ys = yb;
            for (int u = -hx; u <= hx; u++)
                for (int v = -hx; v <= hx; v++)
                    if (!well.contains(IntPair.of(u, v))) f.put(u, ys, v, W(W, "floor"));
            House.drawWalls(f, rng, W, hx, hx, ys, sh, hx < 6 ? 3 : 4, s, n, spec, 1, bayCtx);
            Set<IntPair> nextWell = new HashSet<>();
            List<int[]> cells = new ArrayList<>();
            if (s < n - 1) {
                int ins = (s % 2 == 1 && hx > 3) ? 2 : 1;
                int run = Math.max(1, 2 * (hx - ins) - 1);
                for (int i = 0; i < sh; i++) {
                    int side = Math.floorMod(Math.floorDiv(i, run), 4);
                    int t = i % run;
                    int a = hx - ins;
                    int uu, vv;
                    String face;
                    switch (side) {
                        case 0 -> {
                            uu = -a + t;
                            vv = -a;
                            face = "east";
                        }
                        case 1 -> {
                            uu = a;
                            vv = -a + t;
                            face = "south";
                        }
                        case 2 -> {
                            uu = a - t;
                            vv = a;
                            face = "west";
                        }
                        default -> {
                            uu = -a;
                            vv = a - t;
                            face = "north";
                        }
                    }
                    f.put(uu, ys + 1 + i, vv, B(W(W, "inner_stairs"), "facing", face, "half", "bottom"));
                    for (int kk = ys + 2 + i; kk < ys + 5 + i; kk++) {
                        if (kk >= ys + sh) {
                            f.put(uu, kk, vv, AIR);
                            nextWell.add(IntPair.of(uu, vv));
                        }
                    }
                    cells.add(new int[]{uu, vv});
                }
            }
            well = nextWell;
            List<Object> rt = Dyn.list(s == 0 ? spec.get("ground") : spec.get("upper"));
            List<int[]> doors = new ArrayList<>(cells);
            doors.add(new int[]{0, hx - 1});
            Furniture.furnish(f, rng, (String) rt.get(s % rt.size()), -hx + 2, -hx + 2, hx - 2, hx - 2, ys, doors, "north", sh);
            Set<IntPair> keep = new HashSet<>();
            for (int[] c : cells) keep.add(IntPair.of(c[0], c[1]));
            Bgen.lightRoom(f, -hx + 1, -hx + 1, hx - 1, hx - 1, ys, sh, keep, null, true, null);
            int top = ys + sh;
            if (s < n - 1) {
                for (int u = -hx - 2; u < hx + 3; u++)
                    for (int v = -hx - 2; v < hx + 3; v++)
                        if (Math.abs(u) > hx || Math.abs(v) > hx) {
                            f.put(u, top, v, W(W, "beam"));
                            if (Math.abs(u) == hx + 2 || Math.abs(v) == hx + 2)
                                f.put(u, top + 1, v, Math.abs(v) == hx + 2 ? B(W(W, "rail"), "east", true, "west", true) : B(W(W, "rail"), "north", true, "south", true));
                        }
                H h = Arch.roofSurface(hx + 4, hx + 4, top + 1, 2, 2, 1.5, null, hx >= 5 ? new int[]{2, 1} : new int[]{1}, new int[]{1, 0}, new int[]{hx + 2, hx + 2});
                Arch.rasterRoof(f, h, (u, v, val) -> top - 1, null);
                if (s % 2 == 1 && hx > 3) hx -= 1;
            }
            yb = top;
        }
        for (int u = -hx; u <= hx; u++)
            for (int v = -hx; v <= hx; v++) f.put(u, yb, v, B("spruce_planks"));
        int ry = Bgen.roofFor(f, hx, hx, yb, form, rng, 2 + (k > 1.2 ? 1 : 0), 0.6, true, k, null, 0, 0, 0, 0, 0, null, null);
        if (spec.get("decor") != null && Dyn.list(spec.get("decor")).contains("beacon_top")) f.put(0, ry + 8, 0, B("beacon"));
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("top", ry);
        res.put("hx", hx);
        return res;
    }

    /** bgen.stilt_kind. */
    static Map<String, Object> stiltKind(Frame f, int y, PyRandom rng, Map<String, Object> spec, String tier) {
        int lift = rng.randint(4, 8);
        double k = tk(tier);
        int w = Bgen.odd(randint(rng, range(spec, "size", 0)) * (1 + (k - 1) * 0.6));
        int d = Bgen.odd(randint(rng, range(spec, "size", 1)) * (1 + (k - 1) * 0.6));
        int hx = Math.floorDiv(w, 2), hz = Math.floorDiv(d, 2);
        for (int u = -hx; u < hx + 1; u += 3)
            for (int v = -hz; v < hz + 1; v += 3) {
                for (int yy = y + 1; yy < y + lift + 1; yy++) f.put(u, yy, v, B("stripped_spruce_log", "axis", "y"));
                f.put(u, y + lift - 1, v < hz ? v + 1 : v - 1, stair("spruce", v < hz ? "north" : "south", "top"));
            }
        for (int k2 = 0; k2 < lift; k2++)
            f.put(hx + 1, y + 1 + k2, hz + 1 - k2 > -hz ? hz + 1 - k2 : -hz, stair("spruce", "north"));
        return House.house(f, y + lift, rng, spec, tier, hx, hz, null);
    }

    /** bgen.cave_kind. */
    static Map<String, Object> caveKind(Frame f, int y, PyRandom rng, Map<String, Object> spec, String tier) {
        double k = tk(tier);
        Map<String, Object> W = Bgen.setWalls(Bgen.pick(rng, spec.get("walls")));
        int w = Bgen.odd(randint(rng, range(spec, "size", 0)) * (1 + (k - 1) * 0.6));
        int d = Bgen.odd(randint(rng, range(spec, "size", 1)) * (1 + (k - 1) * 0.6));
        int hx = Math.floorDiv(w, 2), hz = Math.floorDiv(d, 2);
        int moundH = (int) (8 * (1 + (k - 1) * 0.6));
        String rock = spec == Tables.KINDS.get("cave_dwelling") ? B("packed_mud") : B("stone");
        String top = B("grass_block");
        for (int u = -hx - 6; u < hx + 7; u++)
            for (int v = -hz - 6; v < hz + 2; v++) {
                int dist = Math.max(0, Math.max(Math.abs(u) - hx, -hz - v));
                int hgt = moundH + 2 - dist;
                if (hgt <= 0) continue;
                for (int yy = y + 1; yy < y + hgt; yy++) f.put(u, yy, v, rock);
                f.put(u, y + hgt, v, top);
            }
        int nArch = hx >= 5 ? 3 : 1;
        int[] xs = nArch == 3 ? new int[]{Math.floorDiv(-hx, 2), 0, Math.floorDiv(hx, 2)} : new int[]{0};
        List<Object> ground = Dyn.list(spec.get("ground"));
        for (int i = 0; i < xs.length; i++) {
            int cx = xs[i];
            int rw = nArch == 3 ? 2 : hx - 1;
            for (int u = cx - rw; u < cx + rw + 1; u++)
                for (int v = -hz + 1; v < hz + 2; v++) {
                    for (int kk = 1; kk < 5 + (k > 1 ? 1 : 0); kk++) {
                        boolean arc = kk <= 3 || Math.abs(u - cx) < rw;
                        if (arc) f.put(u, y + kk, v, AIR);
                    }
                    f.put(u, y, v, W(W, "floor"));
                }
            for (int u = cx - rw - 1; u < cx + rw + 2; u++) f.put(u, y + 5, hz + 1, W(W, "base"));
            for (int kk = 1; kk < 5; kk++) {
                f.put(cx - rw - 1, y + kk, hz + 1, W(W, "post"));
                f.put(cx + rw + 1, y + kk, hz + 1, W(W, "post"));
            }
            for (int u = cx - rw; u < cx + rw + 1; u++)
                if (u != cx) f.put(u, y + 3, hz + 1, B("spruce_trapdoor", "facing", "south", "half", "bottom", "open", true));
            String room = (String) ground.get(i % ground.size());
            Furniture.furnish(f, rng, room, cx - rw, -hz + 1, cx + rw, hz, y, List.of(new int[]{cx, hz}), "north", 6);
            f.put(cx, y + 2, 0, Bgen.lightBlock());
        }
        if (k > 1.2) for (int s : new int[]{-1, 1}) Arch.lanternPost(f, s * (hx + 2), y, hz + 3);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("top", y + moundH);
        res.put("hx", hx + 6);
        res.put("hz", hz + 6);
        return res;
    }

    static int deg(int v, int u) {
        return (int) Math.toDegrees(UcrtMath.atan2(v, u));
    }

    /** bgen.round_kind: 土楼. */
    static Map<String, Object> roundKind(Frame f, int y, PyRandom rng, Map<String, Object> spec, String tier) {
        Map<String, Object> W = Bgen.setWalls(Bgen.pick(rng, spec.get("walls")));
        Arch.roofTheme(Bgen.pick(rng, spec.get("themes")));
        int R = Math.floorDiv(randint(rng, range(spec, "size", 0)), 2);
        int ring = 7;
        int n = randint(rng, pair(spec, "storeys"));
        int sh = 5;
        for (int s = 0; s < n; s++) {
            int ys = y + s * sh;
            for (int u = -R; u <= R; u++)
                for (int v = -R; v <= R; v++) {
                    double r = UcrtMath.hypotInt(u, v);
                    if (R - ring <= r && r <= R) {
                        f.put(u, ys, v, s != 0 ? W(W, "floor") : B("packed_mud"));
                        boolean outer = r > R - 1;
                        boolean inner = r < R - ring + 1;
                        for (int kk = 1; kk < sh; kk++) {
                            if (outer) {
                                boolean win = s >= 1 && kk == 2 && Math.floorMod(deg(v, u), 15) == 0;
                                f.put(u, ys + kk, v, win ? B("iron_bars") : (s != 0 || kk > 1) ? W(W, "fill") : W(W, "base"));
                            } else if (inner) {
                                f.put(u, ys + kk, v, kk == 1 && s > 0 ? B("spruce_fence") : (kk == sh - 1 ? B("spruce_planks") : AIR));
                            } else if (Math.floorMod(deg(v, u), 20) == 0 && r > R - ring + 2) {
                                f.put(u, ys + kk, v, B("spruce_planks"));
                            }
                        }
                        if (R - ring + 2 < r && r < R - 1 && s >= 0 && rng.random() < 0.02)
                            Decor.pChest(Decor.lift(f.sub(u, v, "south"), ys), rng);
                    }
                }
        }
        H h = new H();
        double mid = R - ring / 2.0;
        for (int u = -R - 3; u < R + 4; u++)
            for (int v = -R - 3; v < R + 4; v++) {
                double r = UcrtMath.hypotInt(u, v);
                if (R - ring - 2 <= r && r <= R + 3) h.put(u, v, y + n * sh + 4 - 0.55 * Math.abs(r - mid));
            }
        int base = y + n * sh;
        Arch.rasterRoof(f, h, (u, v, val) -> base, null);
        for (int kk = 1; kk < 5; kk++)
            for (int u = -1; u <= 1; u++) {
                f.put(u, y + kk, R, AIR);
                f.put(u, y + kk, R - 1, AIR);
            }
        int ri = R - ring;
        Map<String, Object> specH = with(spec("ancestral_hall"), "_wing", true);
        int hw = Math.floorDiv(Bgen.odd(randint(rng, range(specH, "size", 0))), 2);
        int hd = Math.floorDiv(Bgen.odd(randint(rng, range(specH, "size", 1))), 2);
        while (Math.max(hw, hd) > 2 && UcrtMath.hypotInt(hw, hd + 2) > ri - 3.5) {
            hw = Math.max(2, hw - 1);
            hd = Math.max(2, hd - 1);
        }
        House.house(f.sub(0, -2), y, rng, specH, "mortal", hw, hd, null);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("top", y + n * sh + 5);
        res.put("hx", R + 3);
        res.put("hz", R + 3);
        return res;
    }

    /** bgen.pavilion_kind: 戏台, 廊桥, or a small shrine. */
    static Map<String, Object> pavilionKind(Frame f, int y, PyRandom rng, Map<String, Object> spec, String tier) {
        double k = tk(tier);
        Arch.roofTheme(Bgen.pick(rng, spec.get("themes")));
        Map<String, Object> res = new LinkedHashMap<>();
        if (spec == Tables.KINDS.get("opera_stage")) {
            int w = Bgen.odd(randint(rng, range(spec, "size", 0)));
            int d = Bgen.odd(randint(rng, range(spec, "size", 1)));
            int hx = Math.floorDiv(w, 2), hz = Math.floorDiv(d, 2);
            for (int u = -hx; u <= hx; u++)
                for (int v = -hz; v <= hz; v++)
                    for (int yy = y + 1; yy < y + 5; yy++) f.put(u, yy, v, yy < y + 4 ? B("stone_bricks") : B("spruce_planks"));
            for (int u : new int[]{-hx, hx})
                for (int v : new int[]{-hz, hz})
                    for (int yy = y + 5; yy < y + 12; yy++) f.put(u, yy, v, Pal.COL);
            for (int u = -hx; u <= hx; u++)
                for (int yy = y + 5; yy < y + 12; yy++) f.put(u, yy, -hz, yy < y + 11 ? B("red_wool") : Pal.RED);
            Furniture.furnish(f, rng, "backstage", -hx + 1, -hz + 1, hx - 1, -hz + 3, y + 4, List.of(), "north", 6);
            res.put("top", Bgen.roofFor(f, hx, hz, y + 12, Bgen.pick(rng, spec.get("roofs")), rng, 2, 0.5, true, k, null, 0, 0, 0, 0, 0, null, null));
            return res;
        }
        if (spec == Tables.KINDS.get("bridge_house")) {
            int hx = 2, hz = Math.floorDiv(Bgen.odd(randint(rng, range(spec, "size", 1))), 2);
            for (int v = -hz - 3; v < hz + 4; v++) {
                int archH = (int) (3 * StrictMath.cos((double) v / (hz + 3) * Math.PI / 2));
                for (int u = -hx - 1; u < hx + 2; u++) {
                    f.put(u, y + 1 + archH, v, Math.abs(u) > hx ? B("stone_bricks") : B("spruce_planks"));
                    if (Math.abs(u) == hx + 1) f.put(u, y + 2 + archH, v, B("spruce_fence", "north", true, "south", true));
                }
            }
            for (int v = -hz; v < hz + 1; v += 3) {
                int archH = (int) (3 * StrictMath.cos((double) v / (hz + 3) * Math.PI / 2));
                for (int u : new int[]{-hx, hx})
                    for (int yy = y + 2 + archH; yy < y + 9; yy++) f.put(u, yy, v, Pal.COL);
            }
            res.put("top", Bgen.roofFor(f, hx, hz, y + 9, "xuanshan", rng, 1, 0.6, true, k, null, 0, 0, 0, 0, 0, null, null));
            return res;
        }
        return ArchBuildings.pavilion(f, y, Bgen.odd(rng.randint(5, 7)), false);
    }

    static boolean reprContains(Object o, String needle) {
        if (o instanceof String s) return s.contains(needle);
        if (o instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) if (reprContains(e.getKey(), needle) || reprContains(e.getValue(), needle)) return true;
            return false;
        }
        if (o instanceof List<?> l) {
            for (Object x : l) if (reprContains(x, needle)) return true;
        }
        return false;
    }

    /** bgen.compound_kind: a walled compound. */
    static Map<String, Object> compoundKind(Frame f, int y, PyRandom rng, Map<String, Object> spec, String tier) {
        Map<String, Object> W = Bgen.setWalls(Bgen.pick(rng, spec.get("walls")));
        Arch.roofTheme(Bgen.pick(rng, spec.get("themes")));
        double k = tk(tier);
        int w = Bgen.odd(randint(rng, range(spec, "size", 0)) * (1 + (k - 1) * 0.5));
        int d = Bgen.odd(randint(rng, range(spec, "size", 1)) * (1 + (k - 1) * 0.5));
        int hx = Math.floorDiv(w, 2), hz = Math.floorDiv(d, 2);
        List<Object[]> gaps = new ArrayList<>();
        gaps.add(new Object[]{"front", -2, 2});
        ArchBuildings.wallRing(f, y, hx, hz, 4 + (int) k, gaps);
        for (int u = -hx + 1; u < hx; u++)
            for (int v = -hz + 1; v < hz; v++) f.put(u, y, v, Math.floorMod(u + v, 4) != 0 ? B("stone_bricks") : W(W, "floor"));
        Map<String, Object> sub = with(spec, "size", List.of(List.of(7, 11), List.of(7, 9)), "shop", false, "balcony", false, "decor", List.of(),
                "_no_extra_decor", true);
        Map<String, Object> main = with(sub, "ground", spec.get("ground"), "storeys", List.of(1, 2), "podium", List.of(1, 2), "roofs", spec.get("roofs"));
        int mhz = Math.max(4, Math.min(7, Math.floorDiv(hz, 4)));
        House.house(f.sub(0, -hz + mhz + 2), y, child(rng), main, tier, Math.max(4, Math.min(hx - 3, (int) (hx * 0.7))), mhz, null);
        List<Object> ground = Dyn.list(spec.get("ground"));
        for (int s : new int[]{-1, 1}) {
            List<Object> g1 = ground.size() > 1 ? new ArrayList<>(ground.subList(1, ground.size())) : ground;
            Map<String, Object> side = with(sub, "ground", g1, "storeys", List.of(1, 1 + (k > 1.2 ? 1 : 0)), "roofs", List.of("yingshan", "xuanshan"));
            int shz = Math.max(3, Math.min(5, Math.floorDiv(hx, 5)));
            House.house(f.sub(s * (hx - shz - 2), 2, s < 0 ? "east" : "west"), y, child(rng), side, tier, Math.max(3, Math.min(hz - mhz * 2 - 6, 9)), shz, null);
        }
        Map<String, Object> gate = with(sub, "ground", reprContains(spec, "guardroom") ? List.of("guardroom") : List.of("stair_hall"), "storeys", List.of(1, 1),
                "roofs", List.of("xieshan", "yingshan"));
        gate.put("_back_door", true);
        House.house(f.sub(0, hz - 2), y, child(rng), gate, tier, 3, 2, null);
        for (int[] uv : new int[][]{{Math.floorDiv(-hx, 3), Math.floorDiv(hz, 3)}, {Math.floorDiv(hx, 3), Math.floorDiv(hz, 3)}}) {
            boolean isPine = rng.random() < 0.5;
            int height = 9 + rng.randint(0, 4);
            int seed = rng.randint(0, 999);
            if (isPine) ArchBuildings.pine(f, uv[0], y, uv[1], height, seed);
            else ArchBuildings.roundTree(f, uv[0], y, uv[1], height, "dark_oak_leaves", seed);
        }
        for (Object o : spec.get("decor") == null ? List.of() : Dyn.list(spec.get("decor"))) {
            Decor.Fn fn = Decor.DECOR.get((String) o);
            if (fn != null) fn.draw(f, rng, 3, hz, y, 1, 6, W);
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("top", y + 20);
        res.put("hx", hx);
        res.put("hz", hz);
        return res;
    }

    /** bgen.hall_kind: a ceremonial hall (arch.hall) scaled by tier, furnished as one big room. */
    static Map<String, Object> hallKind(Frame f, int y, PyRandom rng, Map<String, Object> spec, String tier) {
        double k = tk(tier);
        Arch.roofTheme(Bgen.pick(rng, spec.get("themes")));
        Bgen.setWalls(Bgen.pick(rng, spec.get("walls")));
        int base = randint(rng, range(spec, "size", 0));
        int nb = base < 30 ? 5 : base < 45 ? 7 : 9;
        int bw = Math.max(5, (int) (base * (1 + (k - 1) * 0.8) / nb));
        List<Integer> baysU = new ArrayList<>();
        baysU.add(Math.max(4, bw - 2));
        for (int i = 0; i < nb - 2; i++) baysU.add(bw);
        baysU.add(Math.max(4, bw - 2));
        baysU.set(nb / 2, bw + 2);
        int dv = base < 30 ? 3 : 5;
        int bd = Math.max(5, (int) (randint(rng, range(spec, "size", 1)) * (1 + (k - 1) * 0.8) / dv));
        List<Integer> baysV = new ArrayList<>();
        baysV.add(Math.max(4, bd - 2));
        for (int i = 0; i < dv - 2; i++) baysV.add(bd);
        baysV.add(Math.max(4, bd - 2));
        boolean fujie = k >= 1.7 && rng.random() < 0.8;
        int colLow = (int) Math.min(baysU.get(0), 8 + 4 * k);
        int nt = 1 + (k > 1.3 ? 1 : 0);
        int[] ms = {6, 3};
        int[][] tiers = new int[nt][];
        for (int i = 0; i < nt; i++) tiers[i] = new int[]{(int) (ms[i] * k), Math.max(2, (int) (2 * k))};
        String roof = Bgen.pick(rng, spec.get("roofs"));
        Map<String, Object> res = ArchBuildings.hall(f, y, baysU, baysV, fujie, roof, colLow, fujie ? colLow + (int) (10 + 6 * k) : 0, tiers,
                2 + (int) k, 3 + (int) k, 2 + (int) k, 3 + (int) k);
        List<Integer> xs = Arch.positions(baysU), zs = Arch.positions(baysV);
        int ix = fujie ? xs.get(xs.size() - 2) : xs.get(xs.size() - 1), iz = fujie ? zs.get(zs.size() - 2) : zs.get(zs.size() - 1);
        int floor = (int) res.get("floor");
        String g0 = (String) Dyn.list(spec.get("ground")).get(0);
        Furniture.furnish(f, rng, g0, -ix + 2, -iz + 2, ix - 2, iz - 2, floor, List.of(new int[]{0, iz - 1}), "north", colLow);
        Set<IntPair> keep = new HashSet<>();
        keep.add(IntPair.of(0, iz - 2));
        keep.add(IntPair.of(0, iz - 3));
        Bgen.lightRoom(f, -ix + 2, -iz + 2, ix - 2, iz - 2, floor, colLow, keep, Bgen.pickLight(rng, g0, tier, List.of("ceiling", "inlay", "floor")), true, null);
        return res;
    }

    /** bgen.gate_kind: 城门楼 / 山门. */
    static Map<String, Object> gateKind(Frame f, int y, PyRandom rng, Map<String, Object> spec, String tier) {
        double k = tk(tier);
        Arch.roofTheme(Bgen.pick(rng, spec.get("themes")));
        Bgen.setWalls(Bgen.pick(rng, spec.get("walls")));
        int w = Bgen.odd(randint(rng, range(spec, "size", 0)) * (1 + (k - 1) * 0.6));
        int d = Bgen.odd(randint(rng, range(spec, "size", 1)) * (1 + (k - 1) * 0.4));
        int nArch = w < 30 ? 1 : 3;
        int nb = w < 35 ? 5 : 7;
        int bw = Math.max(5, Math.floorDiv(w - 10, nb));
        List<Integer> bays = new ArrayList<>();
        for (int i = 0; i < nb; i++) bays.add(bw);
        int dd = Math.max(4, Math.floorDiv(d, 4));
        return ArchBuildings.gateTower(f, y, w, d, (int) (10 + 5 * k), nArch, 5 + 2 * (int) k, 7 + 3 * (int) k, bays, List.of(dd, dd, dd));
    }

    /** bgen.paifang_kind (arch.paifang returns nothing: {}). */
    static Map<String, Object> paifangKind(Frame f, int y, PyRandom rng, Map<String, Object> spec, String tier) {
        double k = tk(tier);
        Arch.roofTheme(rng.choice("grey", "teal", "gold"));
        int span = (int) (rng.randint(12, 18) * (1 + (k - 1) * 0.5));
        SpecialShapes.paifang(f, y, span, (int) (14 * (1 + (k - 1) * 0.5)), (int) (10 * (1 + (k - 1) * 0.5)));
        return new LinkedHashMap<>();
    }

    /** bgen.star_kind: 观星台. */
    static Map<String, Object> starKind(Frame f, int y, PyRandom rng, Map<String, Object> spec, String tier) {
        double k = tk(tier);
        Xianxia.starAltar(f, y, (int) (rng.randint(14, 20) * (1 + (k - 1) * 0.4)));
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("top", y + 30);
        return res;
    }

    /** bgen.floating_kind: 浮空阁. */
    static Map<String, Object> floatingKind(Frame f, int y, PyRandom rng, Map<String, Object> spec, String tier) {
        double k = tk(tier);
        int lift = (int) (30 * k);
        int R = (int) (rng.randint(10, 16) * (1 + (k - 1) * 0.4));
        int yt = y + lift;
        for (int u = -R; u <= R; u++)
            for (int v = -R; v <= R; v++) {
                double r = UcrtMath.hypotInt(u, v) / R + 0.12 * StrictMath.sin(u * 0.7 + v * 0.4);
                if (r > 1) continue;
                int depth = (int) (R * 1.5 * UcrtMath.floatPow(1 - r, 1.3)) + 2;
                for (int yy = yt - depth; yy < yt; yy++) f.put(u, yy, v, yy > yt - depth * 0.6 ? B("stone") : B("deepslate"));
                f.put(u, yt, v, B("grass_block"));
                if (rng.random() < 0.04) {
                    int m = rng.randint(3, 9);
                    for (int kk = 1; kk < m; kk++) f.put(u, yt - depth - kk, v, kk > 1 ? B("hanging_roots") : B("rooted_dirt"));
                }
            }
        List<Integer> sz = List.of(Math.max(7, R - 3), Math.max(7, R - 1));
        Map<String, Object> sub = with(spec, "size", List.of(sz, sz));
        Map<String, Object> res = House.house(f, yt, rng, sub, tier, null, null, null);
        for (int s = 0; s < 4; s++) {
            double a = s * Math.PI / 2 + 0.6;
            ArchBuildings.pine(f, (int) (R * 0.75 * StrictMath.cos(a)), yt, (int) (R * 0.75 * StrictMath.sin(a)), 8 + s, s);
        }
        return res;
    }

    /** bgen.street_kind: 坊市街. */
    static Map<String, Object> streetKind(Frame f, int y, PyRandom rng, Map<String, Object> spec, String tier) {
        double k = tk(tier);
        int L = randint(rng, range(spec, "size", 0));
        int streetW = 7;
        for (int u = Math.floorDiv(-L, 2); u < Math.floorDiv(L, 2) + 1; u++)
            for (int v = Math.floorDiv(-streetW, 2); v < Math.floorDiv(streetW, 2) + 1; v++)
                f.put(u, y, v, Math.abs(v) < Math.floorDiv(streetW, 2) ? B("polished_andesite") : B("stone_bricks"));
        int ov = 2 + (k > 1.2 ? 1 : 0);
        for (int side : new int[]{-1, 1}) {
            int u = Math.floorDiv(-L, 2) + 8;
            while (true) {
                Map<String, Object> sp = with(spec("market_shop"), "_no_wing", true);
                PyRandom rs = child(rng);
                int hx = Math.floorDiv(Bgen.odd(randint(rs, range(sp, "size", 0)) * (1 + (k - 1) * 0.6)), 2);
                int hz = Math.floorDiv(Bgen.odd(randint(rs, range(sp, "size", 1)) * (1 + (k - 1) * 0.6)), 2);
                if (u + 2 * hx > Math.floorDiv(L, 2) - 8) break;
                House.house(f.sub(u + hx, side * (Math.floorDiv(streetW, 2) + 3 + hz), side < 0 ? "south" : "north"), y, rs, sp, tier, hx, hz, null);
                u += 2 * hx + 1 + ov;
            }
        }
        for (int side : new int[]{-1, 1})
            SpecialShapes.paifang(f.sub(side * Math.floorDiv(L, 2), 0, side < 0 ? "east" : "west"), y, 8, 12, 9);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("top", y + 30);
        return res;
    }
}
