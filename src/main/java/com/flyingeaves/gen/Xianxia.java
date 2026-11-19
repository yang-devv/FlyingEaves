package com.flyingeaves.gen;

import com.flyingeaves.gen.py.Py;
import com.flyingeaves.gen.py.UcrtMath;

import java.util.List;

import static com.flyingeaves.gen.Arch.Pal;
import static com.flyingeaves.gen.Blocks.AIR;
import static com.flyingeaves.gen.Blocks.B;
import static com.flyingeaves.gen.Blocks.stair;

/**
 * xianxia.py: the cultivation sect's own structures, as the build menu's kinds and sites use them - star_altar
 * (观星台), pier (云舟码头), cloud_ship (云舟), formation_pillar (护山大阵), stele (道心碑), spirit_field (灵田).
 * All take a frame (front = +v); none with a ground function (the kinds stand on flat ground).
 */
public final class Xianxia {
    private Xianxia() {
    }

    static boolean empty(String s) {
        return s == null || s.equals(AIR);
    }

    /** xianxia.star_altar (ground None). */
    static void starAltar(Frame f, int y, int r) {
        int[][] tiers = {{r, 3}, {r - 7, 3}, {r - 13, 3}};
        for (int tier = 0; tier < 3; tier++) {
            int rr = tiers[tier][0], hh = tiers[tier][1];
            int top = y + 3 * (tier + 1);
            for (int u = -rr; u <= rr; u++)
                for (int v = -rr; v <= rr; v++) {
                    double d = UcrtMath.hypotInt(u, v);
                    if (d > rr) continue;
                    for (int k = top - hh + 1; k <= top; k++) f.put(u, k, v, k == top ? Pal.JADE : B("quartz_bricks"));
                    if (rr - 1 <= d && d <= rr && (Math.abs(u) > 2 && Math.abs(v) > 2 || (Math.abs(u) > 2) != (Math.abs(v) > 2))) {
                        if (!(Math.abs(u) <= 2 || Math.abs(v) <= 2))
                            f.put(u, top + 1, v, Math.floorMod(u + v, 4) == 0 ? B("quartz_pillar", "axis", "y") : B("diorite_wall"));
                    }
                }
            int[][] dirs = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
            String[] faces = {"north", "west", "south", "east"};
            for (int a = 0; a < 4; a++)
                for (int k = 0; k < 3; k++) {
                    int d = rr + 1 + k;
                    int du = dirs[a][0], dv = dirs[a][1];
                    for (int w = -2; w <= 2; w++) {
                        int u = du * d + dv * w, v = dv * d + du * w;
                        f.put(u, top - 1 - k, v, stair("quartz", faces[a]));
                        for (int yy = top - 2 - k; yy > y; yy--)
                            if (empty(f.get(u, yy, v))) f.put(u, yy, v, B("quartz_bricks"));
                    }
                }
        }
        int cy = y + 9 + 9;
        String[] mats = {B("gold_block"), B("cut_copper"), B("cut_copper")};
        for (int t = 0; t < 360; t += 3) {
            double a = Math.toRadians(t);
            for (int ring = 0; ring < 3; ring++) {
                int R = 7 - ring;
                int c = (int) Py.round(R * StrictMath.cos(a)), s = (int) Py.round(R * StrictMath.sin(a));
                if (ring == 0) f.put(c, cy, s, mats[ring]);
                else if (ring == 1) f.put(0, cy + s, c, mats[ring]);
                else f.put(c, cy + s, 0, mats[ring]);
            }
        }
        for (int k = 9; k < 18; k++) f.put(0, y + k, 0, B("chain", "axis", "y"));
        f.put(0, cy, 0, B("sea_lantern"));
    }

    /** xianxia.formation_pillar (ground_fill unused). */
    static void formationPillar(Frame f, int yGround, int y, int height, String color) {
        for (int u = -3; u < 4; u++)
            for (int v = -3; v < 4; v++) {
                if (Math.abs(u) + Math.abs(v) > 5) continue;
                f.col(u, v, yGround, y, B("polished_blackstone_bricks"));
            }
        for (int k = 1; k < height; k++)
            for (int u = -2; u < 3; u++)
                for (int v = -2; v < 3; v++) {
                    if (Math.abs(u) == 2 && Math.abs(v) == 2) continue;
                    boolean edge = Math.abs(u) == 2 || Math.abs(v) == 2;
                    if (!edge) {
                        f.put(u, y + k, v, B("polished_blackstone"));
                        continue;
                    }
                    boolean band = k % 12 == 0 || k % 12 == 1;
                    boolean rune = k % 12 == 6 && (u == 0 || v == 0);
                    f.put(u, y + k, v, band ? B("gold_block") : rune ? B("sea_lantern") : B("polished_blackstone_bricks"));
                }
        int top = y + height;
        for (int u = -2; u < 3; u++)
            for (int v = -2; v < 3; v++)
                f.put(u, top, v, Math.abs(u) <= 1 && Math.abs(v) <= 1 ? B("iron_block") : B("chiseled_polished_blackstone"));
        f.put(0, top + 1, 0, B("beacon"));
        f.put(0, top + 2, 0, B(color + "_stained_glass"));
        for (int[] uv : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) f.put(uv[0], top + 1, uv[1], B("end_rod", "facing", "up"));
    }

    /** xianxia.cloud_ship. */
    static void cloudShip(Frame f, int y, int length, int beam, int seed) {
        int hl = Math.floorDiv(length, 2), hb = Math.floorDiv(beam, 2);
        for (int u = -hl; u <= hl; u++) {
            double t = (double) u / hl;
            double half = t > 0 ? hb * Math.sqrt(Math.max(0.0, 1 - UcrtMath.floatPow(Math.max(0.0, t), 2) * 0.95))
                    : hb * Math.sqrt(Math.max(0.0, 1 - t * t * 0.6));
            double depth = 6 * Math.sqrt(Math.max(0.0, 1 - t * t * 0.8));
            for (int v = -hb - 1; v < hb + 2; v++) {
                if (Math.abs(v) > half) continue;
                double q = v / Math.max(half, 1);
                int bottom = y - (int) Py.round(depth * UcrtMath.floatPow(1 - UcrtMath.floatPow(q, 2), 0.5)) - 1;
                f.col(u, v, bottom, y - 1, B("dark_oak_planks"));
                boolean edge = Math.abs(v) > half - 1.2;
                f.put(u, y, v, edge ? B("dark_oak_planks") : B("spruce_planks"));
                if (edge) {
                    f.put(u, y + 1, v, B("dark_oak_fence", "east", true, "west", true));
                    f.put(u, y - 1, v, Math.floorMod(u, 4) == 0 ? B("gold_block") : B("stripped_dark_oak_wood", "axis", "x"));
                }
                if (Math.abs(v) < half - 1 && ArchBuildings.h(u, v, seed) % 3 != 0) {
                    for (int k = 0; k < 2 + ArchBuildings.h(u, v) % 3; k++)
                        f.put(u, bottom - 2 - k, v, k < 2 ? B("white_wool") : B("white_stained_glass"));
                }
            }
        }
        for (int k = 0; k < 6; k++) f.put(hl + Math.floorDiv(k, 2), y + 1 + k, 0, k > 3 ? B("gold_block") : B("dark_oak_log", "axis", "y"));
        ArchBuildings.hall(f.sub(Math.floorDiv(-hl, 2), 0, "west"), y, List.of(5, 6, 5), List.of(4, 5), false, "xieshan", 6, 0, new int[0][], 3, 4, 3, 4);
        for (int[] m : new int[][]{{Math.floorDiv(hl, 4), 28}, {Math.floorDiv(-hl, 6), 22}}) {
            int mu = m[0], mh = m[1];
            for (int k = 1; k < mh; k++) f.put(mu, y + k, 0, B("spruce_log", "axis", "y"));
            for (int k = Math.floorDiv(mh, 3); k < mh - 3; k++) {
                int w = 2 + Math.floorDiv(k - Math.floorDiv(mh, 3), 2);
                w = Math.min(w, hb + 3);
                for (int v = -w; v <= w; v++) f.put(mu + 1, y + k, v, Math.floorMod(k + v, 7) != 0 ? B("white_wool") : B("light_gray_wool"));
            }
            f.put(mu, y + mh, 0, B("red_wool"));
            f.put(mu, y + mh + 1, 0, B("red_wool"));
        }
    }

    /** xianxia.pier (ground None). */
    static void pier(Frame f, int y, int length, int width) {
        int hw = Math.floorDiv(width, 2);
        for (int v = 0; v <= length; v++) {
            for (int u = -hw; u <= hw; u++) {
                f.put(u, y, v, Math.abs(u) <= 1 ? Pal.JADE : Pal.FLOOR);
                f.put(u, y - 1, v, B("stone_bricks"));
                f.put(u, y - 2, v, Math.abs(u) < hw ? B("stone_brick_slab", "type", "top") : B("stone_bricks"));
            }
            for (int s : new int[]{-1, 1}) {
                f.put(s * hw, y + 1, v, v % 6 == 0 ? B("quartz_pillar", "axis", "y") : B("diorite_wall", "north", "low", "south", "low", "up", false));
                if (v % 12 == 0) Arch.lanternPost(f, s * (hw - 1), y, v);
            }
            if (v % 9 == 0)
                for (int k = 3; k < 12; k++) for (int u : new int[]{-hw, hw}) f.put(u, y - k, v, B("stone_bricks"));
        }
    }

    /** xianxia.stele. */
    static void stele(Frame f, int y, int height, int width) {
        for (int u = Math.floorDiv(-width, 2) - 3; u < Math.floorDiv(width, 2) + 4; u++)
            for (int v = -5; v < 6; v++) {
                double a = u / (width / 2.0 + 3), b = v / 5.5;
                double d = UcrtMath.floatPow(a, 2) + UcrtMath.floatPow(b, 2);
                if (d <= 1)
                    for (int k = 1; k < (d < 0.6 ? 4 : 3); k++) f.put(u, y + k, v, k < 3 ? B("tuff") : B("mossy_cobblestone"));
            }
        for (int u = -1; u < 2; u++) {
            f.put(u, y + 3, 6, B("tuff"));
            f.put(u, y + 4, 6, B("tuff"));
        }
        int hw = Math.floorDiv(width, 2);
        for (int u = -hw; u <= hw; u++)
            for (int k = 4; k < 4 + height; k++) {
                for (int v = -1; v <= 1; v++) f.put(u, y + k, v, B("polished_blackstone"));
                if (Math.abs(u) < hw - 1 && 6 < k && k < height && Math.floorMod(u + k, 3) == 0)
                    f.put(u, y + k, 2, k % 2 != 0 ? B("gold_block") : B("polished_blackstone_button", "face", "wall", "facing", "south"));
            }
        for (int u = -hw - 1; u < hw + 2; u++) {
            for (int v = -2; v <= 2; v++) f.put(u, y + 4 + height, v, B("polished_blackstone_bricks"));
            f.put(u, y + 5 + height, 0, B("polished_blackstone_brick_slab", "type", "bottom"));
        }
    }

    /** xianxia.spirit_field. */
    static void spiritField(Fr f, int u0, int v0, int u1, int v1, int y, int seed) {
        String[] plants = {B("torchflower"), B("pitcher_plant", "half", "lower"), B("allium"), B("blue_orchid"), B("lily_of_the_valley")};
        for (int u = u0; u <= u1; u++)
            for (int v = v0; v <= v1; v++) {
                int k = Math.floorMod(v - v0, 6);
                if (k == 0) {
                    f.put(u, y, v, B("water"));
                    f.put(u, y - 1, v, Math.floorMod(u, 5) == 0 ? B("sea_lantern") : B("prismarine_bricks"));
                } else if (k == 5) {
                    f.put(u, y, v, B("moss_block"));
                    if (Math.floorMod(u, 7) == 0) f.put(u, y + 1, v, B("end_rod", "facing", "up"));
                } else {
                    f.put(u, y, v, B("farmland", "moisture", 7));
                    String p = plants[Math.floorMod(Math.floorDiv(u, 5) + Math.floorDiv(v, 6) + seed, plants.length)];
                    f.put(u, y + 1, v, p);
                    if (p.endsWith("half=lower]")) f.put(u, y + 2, v, B("pitcher_plant", "half", "upper"));
                }
            }
    }
}
