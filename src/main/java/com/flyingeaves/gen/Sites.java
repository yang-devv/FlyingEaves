package com.flyingeaves.gen;

import com.flyingeaves.gen.py.Py;
import com.flyingeaves.gen.py.PyRandom;
import com.flyingeaves.gen.py.UcrtMath;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.flyingeaves.gen.Arch.Pal;
import static com.flyingeaves.gen.Blocks.AIR;
import static com.flyingeaves.gen.Blocks.B;
import static com.flyingeaves.gen.Blocks.stair;

/**
 * sites.py: the sect's special sites (one archetype each): cloud_port 云舟港, spirit_mine 灵矿, beast_hill 灵兽山,
 * trial_ground 试炼场, forbidden 禁地, tomb 祖师陵·碑林. Some build other kinds into the same canvas (bgen.build).
 */
public final class Sites {
    private Sites() {
    }

    static Map<String, Object> res(int hx, int hz, int top) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hx", hx);
        m.put("hz", hz);
        m.put("top", top);
        return m;
    }

    /** sites._noise: cheap smooth value noise in [-1, 1]. */
    static double noise(int u, int v, long seed, double cell) {
        double fu = u / cell, fv = v / cell;
        long i = (long) Math.floor(fu), k = (long) Math.floor(fv);
        double tu = fu - i, tv = fv - k;
        tu = tu * tu * (3 - 2 * tu);
        tv = tv * tv * (3 - 2 * tv);
        double a = r(i, k, seed) * (1 - tu) + r(i + 1, k, seed) * tu;
        double c = r(i, k + 1, seed) * (1 - tu) + r(i + 1, k + 1, seed) * tu;
        return a * (1 - tv) + c * tv;
    }

    private static double r(long i, long k, long seed) {
        long x = (i * 374761393L + k * 668265263L + seed * 2654435761L) & 0xFFFFFFFFL;   // (only the low 32 bits count)
        x = ((x ^ (x >> 13)) * 1274126177L) & 0xFFFFFFFFL;
        return (x & 0xFFFF) / 32767.5 - 1.0;
    }

    /** math.hypot of two floats: correctly rounded (CPython's vector_norm is, but for extreme cases). */
    static double hypot(double a, double b) {
        java.math.BigDecimal x = new java.math.BigDecimal(a), y = new java.math.BigDecimal(b);
        java.math.BigDecimal s = x.multiply(x).add(y.multiply(y));
        return s.sqrt(new java.math.MathContext(60)).doubleValue();
    }

    /** sites.crag: a craggy rock mass. top: null for none. */
    static void crag(Frame f, int y, int cu, int cv, int R, int H, int seed, String[] mats, String top, double squash) {
        int lim = (int) (R * 1.3);
        for (int u = -lim; u <= lim; u++)
            for (int v = -lim; v <= lim; v++) {
                double n1 = noise(u, v, seed, 9.0);
                double n2 = noise(u, v, seed + 7, 3.5);
                double d = hypot(u, v * squash) / (R * (1 + 0.25 * n1));
                if (d > 1) continue;
                double h = H * UcrtMath.floatPow(1 - d, 0.55) * (0.8 + 0.25 * n2);
                int hi = (int) (Py.floorDiv(h, 3) * 3 + (n2 > 0.3 ? 2 : 0));
                for (int t = 1; t <= hi; t++) {
                    String m = t < hi * 0.4 ? mats[0] : mats[Math.floorMod(Math.floorDiv(t, 4) + Math.floorDiv(u + v, 5), mats.length)];
                    f.put(cu + u, y + t, cv + v, m);
                }
                if (top != null && hi > 2 && noise(u, v, seed + 3, 5.0) > -0.2) f.put(cu + u, y + hi, cv + v, top);
            }
    }

    static void floor(Frame f, int hx, int hz, int y) {
        for (int u = -hx; u <= hx; u++)
            for (int v = -hz; v <= hz; v++)
                f.put(u, y, v, Math.floorMod(u, 8) != 0 && Math.floorMod(v, 8) != 0 ? B("stone_bricks") : B("polished_andesite"));
    }

    static String tierOr(String tier, String kind, String dflt) {
        return Dyn.list(Kinds.spec(kind).get("tiers")).contains(tier) ? tier : dflt;
    }

    static Map<String, Object> cloudPort(Frame f, int y, PyRandom rng, Map<String, Object> spec, String tier) {
        double k = Kinds.tk(tier);
        int hx = (int) (45 * (1 + (k - 1) * 0.3)), hz = (int) (30 * (1 + (k - 1) * 0.3));
        floor(f, hx, hz, y);
        for (int u = -hx; u <= hx; u++)
            for (int t = 1; t < 7; t++) f.put(u, y - t, hz, Arch.masonry(u, y - t, hz));
        int n = rng.randint(2, 4);
        for (int i = 0; i < n; i++) {
            int u = (int) (-hx + (i + 0.5) * (2 * hx) / n);
            int L = rng.randint(45, 80);
            Xianxia.pier(f.sub(u, hz, "south"), y, L, 9);
            int side = rng.choice(new int[]{-1, 1});
            int length = rng.randint(36, 56), beam = rng.randint(9, 13), seed = rng.randint(0, 999);
            Xianxia.cloudShip(f.sub(u + side * 12, hz + Math.floorDiv(L, 2), "south"), y + 3, length, beam, seed);
        }
        Kinds.build(f.sub(-hx + 12, -hz + 12), y, "dock_tower", tierOr(tier, "dock_tower", "jindan"), rng.randint(0, 999));
        int nw = rng.randint(2, 4);
        for (int j = 0; j < nw; j++) {
            Map<String, Object> wspec = Kinds.with(Kinds.spec("granary"), "decor", List.of());
            House.house(f.sub(-hx + 32 + j * 22, -hz + 8), y, Kinds.child(rng), wspec, "mortal", 8, 6, null);
        }
        for (int i = 0; i < n; i++) {
            int u = (int) (-hx + (i + 0.5) * (2 * hx) / n) + 6;
            for (int t = 1; t < 14; t++) f.put(u, y + t, hz - 2, B("stripped_spruce_log", "axis", "y"));
            for (int t = 0; t < 9; t++) f.put(u, y + 13, hz - 2 + t, t < 8 ? B("spruce_planks") : B("spruce_fence"));
            for (int t = 1; t < 6; t++) f.put(u, y + 13 - t, hz + 6, B("chain", "axis", "y"));
            f.put(u, y + 7, hz + 6, B("barrel", "facing", "up"));
        }
        for (int u = -hx + 6; u < hx - 5; u += 10) {
            if (Math.floorMod(u, 30) == 0) Xianxia.formationPillar(f.sub(u, -hz - 3), y - 1, y, 10, "cyan");
            else Arch.lanternPost(f, u, y, -hz + 2);
        }
        return res(hx, hz + 80, y + 30);
    }

    static Map<String, Object> spiritMine(Frame f, int y, PyRandom rng, Map<String, Object> spec, String tier) {
        double k = Kinds.tk(tier);
        int R = (int) (rng.randint(22, 30) * (1 + (k - 1) * 0.3));
        int H = (int) (R * 1.2);
        String[] rock = {B("deepslate"), B("stone"), B("andesite"), B("tuff"), B("stone")};
        crag(f, y, 0, (int) -(R * 0.35), R, H, rng.randint(0, 999), rock, B("moss_block"), 1.25);
        int aw = 3, ah = 6;
        for (int v = -R; v < 6; v++) {
            for (int u = -aw; u <= aw; u++) {
                for (int t = 1; t <= ah; t++) f.put(u, y + t, v, AIR);
                f.put(u, y, v, B("gravel"));
            }
            if (Math.floorMod(v, 4) == 0) {
                for (int t = 1; t <= ah; t++) {
                    f.put(-aw - 1, y + t, v, B("stripped_spruce_log", "axis", "y"));
                    f.put(aw + 1, y + t, v, B("stripped_spruce_log", "axis", "y"));
                }
                for (int u = -aw - 1; u < aw + 2; u++) f.put(u, y + ah + 1, v, B("stripped_spruce_log", "axis", "x"));
                f.put(0, y + ah, v, B("light", "level", 15, "waterlogged", false));
            }
            for (int u : new int[]{-1, 1}) f.put(u, y + 1, v, B("rail", "shape", "north_south"));
        }
        for (int u = -aw - 2; u < aw + 3; u++) {
            f.put(u, y + ah + 2, 6, B("dark_oak_planks"));
            f.put(u, y + ah + 3, 7, stair(Arch.roof("tile"), "north"));
        }
        for (int t = 1; t < ah + 2; t++) {
            f.put(-aw - 2, y + t, 6, B("stripped_dark_oak_log", "axis", "y"));
            f.put(aw + 2, y + t, 6, B("stripped_dark_oak_log", "axis", "y"));
        }
        for (int v = 6; v < 30; v++) for (int u : new int[]{-1, 1}) f.put(u, y + 1, v, B("rail", "shape", "north_south"));
        String[] yard = {B("raw_iron_block"), B("raw_copper_block"), B("amethyst_block"), B("raw_gold_block"), B("coal_block")};
        for (int i = 0; i < 6; i++) {
            int cu = rng.randint(-R, R), cv = rng.randint(14, 34);
            String mat = yard[(int) rng.randrange(yard.length)];
            for (int u = -3; u < 4; u++)
                for (int v = -3; v < 4; v++) {
                    int hgt = 3 - Math.max(Math.abs(u), Math.abs(v));
                    for (int t = 1; t <= hgt; t++) f.put(cu + u, y + t, cv + v, Math.floorMod(u + v + t, 3) != 0 ? mat : B("gravel"));
                }
        }
        int hu = R + 8;
        for (int t = 1; t < 22; t++) {
            for (int[] d : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) f.put(hu + d[0], y + t, 10 + d[1], B("stripped_spruce_log", "axis", "y"));
            if (t % 5 == 0)
                for (int d = -2; d < 3; d++)
                    for (int[] ab : new int[][]{{d, -2}, {d, 2}, {-2, d}, {2, d}}) f.put(hu + ab[0], y + t, 10 + ab[1], B("spruce_planks"));
        }
        for (int a = 0; a < 360; a += 20) {
            double r = Math.toRadians(a);
            f.put(hu, y + 22 + (int) Py.round(3 * StrictMath.sin(r)), 10 + (int) Py.round(3 * StrictMath.cos(r)), B("dark_oak_planks"));
        }
        for (int u = -R - 14; u < -R - 2; u++)
            for (int v = 8; v < 22; v++) {
                double d = UcrtMath.hypotInt(u + R + 8, v - 15) / 6;
                for (int t = 1; t < (int) (5 * Math.max(0, 1 - d)) + 1; t++) f.put(u, y + t, v, Math.floorMod(u + v, 2) != 0 ? B("tuff") : B("gravel"));
            }
        Kinds.build(f.sub(-R + 2, 36), y, "forge_house", "zhuji", rng.randint(0, 999));
        Kinds.build(f.sub(R + 4, 36), y, "outer_dorm", "lianqi", rng.randint(0, 999));
        Kinds.build(f.sub(-R - 16, -6), y, "watchtower", "zhuji", rng.randint(0, 999));
        return res(R + 20, R + 40, y + H);
    }

    static Map<String, Object> beastHill(Frame f, int y, PyRandom rng, Map<String, Object> spec, String tier) {
        double k = Kinds.tk(tier);
        int hx = (int) (40 * (1 + (k - 1) * 0.3)), hz = (int) (32 * (1 + (k - 1) * 0.3));
        for (int u = -hx; u <= hx; u++)
            for (int v = -hz; v <= hz; v++) f.put(u, y, v, Math.floorMod(u * 3 + v, 11) != 0 ? B("grass_block") : B("coarse_dirt"));
        for (int i = 0; i < 4; i++) {
            int cu = i % 2 != 0 ? Math.floorDiv(-hx, 2) : Math.floorDiv(hx, 2), cv = i < 2 ? Math.floorDiv(-hz, 2) : Math.floorDiv(hz, 2);
            int w = rng.randint(10, 16), d = rng.randint(8, 12);
            for (int u = -w; u <= w; u++)
                for (int v : new int[]{-d, d}) f.put(cu + u, y + 1, cv + v, Math.abs(u) > 1 || v < 0 ? B("spruce_fence", "east", true, "west", true) : AIR);
            for (int v = -d + 1; v < d; v++)
                for (int u : new int[]{-w, w}) f.put(cu + u, y + 1, cv + v, B("spruce_fence", "north", true, "south", true));
            for (int u = -2; u < 3; u++) f.put(cu + u, y + 1, cv - d + 2, Math.floorMod(u, 2) != 0 ? B("composter") : B("water_cauldron", "level", 3));
            f.put(cu + w - 2, y + 1, cv, B("hay_block"));
            f.put(cu + w - 2, y + 2, cv, B("hay_block"));
        }
        for (int i = 0; i < 5; i++) {
            int cu = rng.randint(-hx + 4, hx - 4), cv = rng.randint(-hz + 4, hz - 4);
            int hgt = rng.randint(10, 18);
            for (int t = 1; t < hgt; t++) f.put(cu, y + t, cv, B("stripped_spruce_log", "axis", "y"));
            for (int u = -2; u < 3; u++)
                for (int v = -2; v < 3; v++)
                    if (Math.abs(u) + Math.abs(v) <= 3)
                        f.put(cu + u, y + hgt, cv + v, Math.abs(u) + Math.abs(v) < 2 ? B("hay_block") : B("spruce_slab", "type", "bottom"));
        }
        for (int i = 0; i < 3; i++) {
            int cu = -hx + 12 + i * (hx - 6);
            crag(f, y, cu, -hz - 5, 8, 10, rng.randint(0, 999), new String[]{B("stone"), B("mossy_cobblestone"), B("andesite")}, B("grass_block"), 1.0);
            for (int u = -2; u < 3; u++)
                for (int v = -hz - 8; v < -hz + 3; v++)
                    for (int t = 1; t < 5; t++) f.put(cu + u, y + t, v, AIR);
            for (int v = -hz - 8; v < -hz + 3; v++) {
                boolean held = false;
                for (int side : new int[]{-3, 3}) {
                    String s = f.get(cu + side, y + 5, v);
                    if (s != null && !s.equals(AIR)) held = true;
                }
                if (!held)
                    for (int u = -2; u < 3; u++)
                        for (int t = 5; t < 25; t++) f.put(cu + u, y + t, v, AIR);
            }
        }
        Kinds.build(f.sub(0, hz + 12), y, "beast_stable", tierOr(tier, "beast_stable", "zhuji"), rng.randint(0, 999));
        return res(hx, hz + 25, y + 20);
    }

    static Map<String, Object> trialGround(Frame f, int y, PyRandom rng, Map<String, Object> spec, String tier) {
        double k = Kinds.tk(tier);
        int R = (int) (rng.randint(30, 40) * (1 + (k - 1) * 0.3));
        for (int u = -R - 10; u < R + 11; u++)
            for (int v = -R - 10; v < R + 11; v++) {
                double d = UcrtMath.hypotInt(u, v);
                if (d <= R) f.put(u, y, v, Math.floorMod((int) d, 6) != 0 ? B("polished_andesite") : B("chiseled_stone_bricks"));
                else if (d <= R + 10) {
                    int t = (int) ((d - R) / 2) + 1;
                    for (int s = 1; s <= t; s++) f.put(u, y + s, v, B("stone_bricks"));
                    String face = Math.abs(v) >= Math.abs(u) && v > 0 ? "north" : Math.abs(v) >= Math.abs(u) ? "south" : u > 0 ? "west" : "east";
                    f.put(u, y + t + 1, v, Math.floorMod((int) d, 2) != 0 ? stair("stone_brick", face) : AIR);
                }
            }
        for (int a = 0; a < 360; a += 90) {
            double r = Math.toRadians(a);
            double c = StrictMath.cos(r), sn = StrictMath.sin(r);
            for (int t = R - 1; t < R + 12; t++)
                for (int w = -3; w < 4; w++) {
                    int u = (int) Py.round(t * c - w * sn), v = (int) Py.round(t * sn + w * c);
                    for (int s = 1; s < 8; s++) f.put(u, y + s, v, AIR);
                    f.put(u, y, v, B("polished_andesite"));
                }
        }
        int r0 = Math.floorDiv(R, 3);
        for (int u = -r0; u <= r0; u++)
            for (int v = -r0; v <= r0; v++)
                if (UcrtMath.hypotInt(u, v) <= r0) for (int s = 1; s <= 3; s++) f.put(u, y + s, v, s == 3 ? Pal.JADE : B("quartz_bricks"));
        for (int a = 0; a < 360; a += 30) {
            double r = Math.toRadians(a);
            int u = (int) Py.round((R + 11) * StrictMath.cos(r)), v = (int) Py.round((R + 11) * StrictMath.sin(r));
            for (int s = 1; s < 21; s++) f.put(u, y + s, v, B("spruce_fence"));
            f.put(u, y + 21, v, B(rng.choice("red", "yellow", "blue", "white") + "_banner", "rotation", 0));
        }
        ArchBuildings.pavilion(f.sub(0, -R - 16), y + 6, 11, true);
        return res(R + 12, R + 22, y + 25);
    }

    static Map<String, Object> forbidden(Frame f, int y, PyRandom rng, Map<String, Object> spec, String tier) {
        int R = rng.randint(24, 30);
        crag(f, y, 0, (int) -(R * 0.45), R, (int) (R * 1.5), rng.randint(0, 999),
                new String[]{B("blackstone"), B("basalt", "axis", "y"), B("blackstone"), B("polished_basalt", "axis", "y")}, null, 1.2);
        for (int u = -4; u < 5; u++) {
            for (int v = -R; v < 4; v++)
                for (int t = 1; t < 9; t++)
                    if (Math.abs(u) < 4 || t < 7) f.put(u, y + t, v, AIR);
            for (int t = 1; t < 9; t++) f.put(u, y + t, 3, t % 2 != 0 ? B("chain", "axis", "x") : AIR);
        }
        int cr = 14;
        for (int u = -cr; u <= cr; u++)
            for (int v = 4; v < 4 + 2 * cr + 1; v++) {
                double d = UcrtMath.hypotInt(u, v - 4 - cr);
                if (d <= cr)
                    f.put(u, y, v, Math.abs(d - cr + 1) < 0.8 || Math.abs(d - cr / 2.0) < 0.6 ? B("crying_obsidian") : B("polished_blackstone"));
            }
        for (int a = 0; a < 360; a += 45) {
            double r = Math.toRadians(a);
            int u = (int) Py.round((cr + 3) * StrictMath.cos(r)), v = 4 + cr + (int) Py.round((cr + 3) * StrictMath.sin(r));
            for (int t = 1; t < 13; t++) f.put(u, y + t, v, t % 4 != 0 ? B("polished_blackstone_bricks") : B("redstone_lamp", "lit", true));
            f.put(u, y + 13, v, B("soul_campfire", "lit", true));
        }
        for (int s : new int[]{-1, 1}) Xianxia.stele(f.sub(s * (cr + 9), 4 + 2 * cr + 4), y, 12, 5);
        return res(R, R + 2 * cr, y + R);
    }

    static Map<String, Object> tomb(Frame f, int y, PyRandom rng, Map<String, Object> spec, String tier) {
        double k = Kinds.tk(tier);
        int L = (int) (rng.randint(50, 70) * (1 + (k - 1) * 0.2));
        for (int v = 0; v < L; v++) {
            for (int u = -3; u < 4; u++) f.put(u, y, v, Math.abs(u) < 3 ? B("polished_andesite") : B("stone_bricks"));
            if (v % 10 == 5)
                for (int s : new int[]{-1, 1}) {
                    int u = s * 7;
                    f.put(u, y + 1, v, B("stone_bricks"));
                    f.put(u, y + 2, v, B("chiseled_stone_bricks"));
                    f.put(u, y + 3, v, stair("stone_brick", s > 0 ? "west" : "east"));
                    f.put(u, y + 4, v, B("stone_brick_wall"));
                }
            if (v % 10 == 0 && v != 0) for (int s : new int[]{-1, 1}) ArchBuildings.pine(f, s * 12, y, v, 11, v * s);
        }
        SpecialShapes.paifang(f.sub(0, L + 4), y, 16, 16, 12);
        int rows = rng.randint(4, 6);
        for (int s : new int[]{-1, 1})
            for (int r = 0; r < rows; r++)
                for (int c = 0; c < 4; c++) Xianxia.stele(f.sub(s * (22 + c * 9), -8 - r * 10), y, rng.randint(7, 11), 3);
        ArchBuildings.hall(f.sub(0, -12), y, List.of(7, 9, 7), List.of(7, 9, 7), false, "xieshan", 0, 0, new int[][]{{3, 2}, {1, 2}}, 3, 4, 3, 4);
        int R = 20;
        for (int u = -R; u <= R; u++)
            for (int v = -R; v <= R; v++) {
                double d = UcrtMath.hypotInt(u, v) / R;
                if (d > 1) continue;
                int h = (int) (14 * (1 - d * d));
                int cv = v - 50;
                for (int t = 1; t <= h; t++) f.put(u, y + t, cv, t == h ? B("grass_block") : B("dirt"));
                if (Math.abs(d - 1) < 0.06) for (int t = 1; t <= 2; t++) f.put(u, y + t, cv, B("stone_bricks"));
            }
        return res(60, L + 10, y + 20);
    }
}
