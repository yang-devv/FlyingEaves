package com.flyingeaves.gen;

import com.flyingeaves.gen.py.Py;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static com.flyingeaves.gen.Arch.H;
import static com.flyingeaves.gen.Arch.Pal;
import static com.flyingeaves.gen.Arch.roof;
import static com.flyingeaves.gen.Blocks.AIR;
import static com.flyingeaves.gen.Blocks.B;
import static com.flyingeaves.gen.Blocks.slab;
import static com.flyingeaves.gen.Blocks.stair;

/**
 * arch.py's whole buildings, as the build menu's kinds use them: hall (殿 on a 须弥座, 重檐 or not), pavilion (亭),
 * gate_tower (城门楼), wall_ring (院墙), with beams_and_brackets / columns / walls; and the trees (pine, round_tree).
 */
public final class ArchBuildings {
    private ArchBuildings() {
    }

    /** arch._h: FNV-1a over 32-bit ints. */
    static long h(long... a) {
        long h = 2166136261L;
        for (long v : a) h = ((h ^ (v & 0xFFFFFFFFL)) * 16777619L) & 0xFFFFFFFFL;
        return h;
    }

    static int floor(double d) {
        return (int) Math.floor(d);
    }

    /** arch.beams_and_brackets: returns the eave purlin y. */
    static int beamsAndBrackets(Fr f, int hx, int hz, int ytop, List<Integer> colsX, List<Integer> colsZ, int reach, int levels) {
        for (Object[] c : Arch.ringCells(hx, hz)) {
            int u = (int) c[0], v = (int) c[1];
            String face = (String) c[2];
            int k = face.equals("north") || face.equals("south") ? u : v;
            f.put(u, ytop, v, Math.floorMod(k, 3) == 1 ? Pal.WHITE : Pal.RED);
            f.put(u, ytop + 1, v, B(Pal.WOOD + "_planks"));
        }
        List<Object[]> pos = new ArrayList<>();
        List<Integer> cx = new ArrayList<>();
        for (int c : colsX) if (Math.abs(c) <= hx) cx.add(c);
        for (int x : spots(cx)) {
            pos.add(new Object[]{x, -hz, "north"});
            pos.add(new Object[]{x, hz, "south"});
        }
        List<Integer> cz = new ArrayList<>();
        for (int c : colsZ) if (Math.abs(c) <= hz) cz.add(c);
        List<Integer> sz = spots(cz);
        for (int i = 1; i < sz.size() - 1; i++) {
            int z = sz.get(i);
            pos.add(new Object[]{-hx, z, "west"});
            pos.add(new Object[]{hx, z, "east"});
        }
        int y0 = ytop + 2;
        for (Object[] p : pos) {
            int u = (int) p[0], v = (int) p[1];
            String face = (String) p[2];
            int[] d = Blocks.DIRV.get(face);
            int du = d[0], dv = d[1];
            int su = face.equals("north") || face.equals("south") ? 1 : 0, sv = 1 - su;
            f.put(u, y0, v, B(Pal.WOOD + "_planks"));
            for (int lv = 1; lv <= levels; lv++) {
                int ou = u + du * lv, ov = v + dv * lv;
                f.put(ou, y0 + lv - 1, ov, stair(Pal.WOOD, Blocks.OPP.get(face), "top"));
                if (lv == 1 || lv == levels) {
                    f.put(ou + su, y0 + lv - 1, ov + sv, B(Pal.WOOD + "_trapdoor", "facing", face, "half", "top", "open", false));
                    f.put(ou - su, y0 + lv - 1, ov - sv, B(Pal.WOOD + "_trapdoor", "facing", face, "half", "top", "open", false));
                }
            }
        }
        for (Object[] c : Arch.ringCells(hx, hz)) {
            String face = (String) c[2];
            int[] d = Blocks.DIRV.get(face);
            f.put((int) c[0] + d[0] * reach, y0 + levels - 1, (int) c[1] + d[1] * reach,
                    B("dark_oak_log", "axis", face.equals("north") || face.equals("south") ? "x" : "z"));
        }
        for (Object[] c : Arch.ringCells(hx, hz)) {
            int u = (int) c[0], v = (int) c[1];
            for (int lv = 0; lv < levels; lv++)
                if (f.get(u, y0 + lv, v) == null) f.put(u, y0 + lv, v, lv == 0 ? B(Pal.WOOD + "_planks") : Pal.RED);
        }
        return y0 + levels - 1;
    }

    private static List<Integer> spots(List<Integer> cs) {
        TreeSet<Integer> out = new TreeSet<>(cs);
        for (int i = 0; i + 1 < cs.size(); i++) {
            int a = cs.get(i), c = cs.get(i + 1);
            int span = c - a;
            int n = span >= 10 ? 2 : span >= 5 ? 1 : 0;
            for (int j = 1; j <= n; j++) out.add(a + (int) Py.round(span * (double) j / (n + 1)));
        }
        return new ArrayList<>(out);
    }

    interface TopFn {
        int at(int u, int v);
    }

    interface KeepFn {
        boolean at(int u, int v);
    }

    /** arch.columns. */
    static void columns(Fr f, List<Integer> xs, List<Integer> zs, int yFloor, TopFn topFn, KeepFn keepFn) {
        for (int u : xs)
            for (int v : zs) {
                if (keepFn != null && !keepFn.at(u, v)) continue;
                for (int du = -1; du <= 1; du++)
                    for (int dv = -1; dv <= 1; dv++)
                        if (du != 0 || dv != 0) f.put(u + du, yFloor + 1, v + dv, slab("smooth_stone"));
                for (int y = yFloor + 1; y <= topFn.at(u, v); y++) f.put(u, y, v, Pal.COL);
            }
    }

    /** arch.walls (door_half, window_from: defaults; back_door True). */
    static void walls(Fr f, int hx, int hz, int yFloor, int yTop, List<Integer> xs, List<Integer> zs) {
        int doorHalf = Math.floorDiv(hx, 3);
        boolean backDoor = true;
        for (Object[] c : Arch.ringCells(hx, hz)) {
            int u = (int) c[0], v = (int) c[1];
            String face = (String) c[2];
            if (((face.equals("north") || face.equals("south")) && xs.contains(u)) || ((face.equals("west") || face.equals("east")) && zs.contains(v))) continue;
            int h = yTop - yFloor;
            for (int y = yFloor + 1; y <= yTop; y++) {
                int k = y - yFloor;
                if (face.equals("south") && Math.abs(u) <= Math.max(1, Math.floorDiv(doorHalf, 3)) && k <= Math.max(4, (int) (h * 0.55))) {
                    f.put(u, y, v, AIR);
                    f.put(u, y, v - 1, AIR);
                    continue;
                }
                if (face.equals("south") && Math.abs(u) < hx - 1) {
                    if (Math.abs(u) > doorHalf && k <= Math.max(3, Math.floorDiv(h, 4))) {
                        f.put(u, y, v, k < Math.max(3, Math.floorDiv(h, 4)) ? B("bricks") : Pal.RED);
                        continue;
                    }
                    if (k <= h * 0.62) {
                        if (k == 1 || k == Py.round(h * 0.3) || k == Py.round(h * 0.62)) f.put(u, y, v, Pal.RED);
                        else {
                            f.put(u, y, v, B("spruce_trapdoor", "facing", "south", "half", "bottom", "open", true));
                            f.put(u, y, v - 1, B("white_stained_glass_pane", "east", true, "west", true));
                        }
                        continue;
                    }
                    f.put(u, y, v, k % 7 != 0 ? Pal.WHITE : Pal.RED);
                } else if (face.equals("north") && backDoor && Math.abs(u) <= Math.max(2, Math.floorDiv(doorHalf, 3)) && k <= h * 0.55) {
                    f.put(u, y, v, k == 1 || k >= h * 0.55 - 1 ? Pal.RED : B("spruce_trapdoor", "facing", "north", "half", "bottom", "open", true));
                } else f.put(u, y, v, k != 1 && k != 2 && k != h - 1 ? Pal.WHITE : Pal.RED);
            }
        }
    }

    static int[][] gaps(int a, int b) {
        return new int[][]{{a, b}};
    }

    static final int[][] NONE = new int[0][];

    /**
     * arch.hall (ground None, stair_half None, back_stairs False). tiers: (margin, height) outermost first. colLow,
     * colUp: 0 for the default. Returns floor, out, ridge, eave, base, inner.
     */
    static Map<String, Object> hall(Frame f, int yBase, List<Integer> baysU, List<Integer> baysV, boolean fujie, String roof, int colLow, int colUp,
                                    int[][] tiers, int levelsLow, int levelsUp, int raftersLow, int raftersUp) {
        List<Integer> XS = Arch.positions(baysU), ZS = Arch.positions(baysV);
        int OUT_X = XS.get(XS.size() - 1), OUT_Z = ZS.get(ZS.size() - 1);
        int IN_X = fujie ? XS.get(XS.size() - 2) : OUT_X, IN_Z = fujie ? ZS.get(ZS.size() - 2) : OUT_Z;
        if (colLow == 0) {
            int mx = baysU.get(0);
            if (!fujie) for (int b : baysU) mx = Math.max(mx, b);
            colLow = Math.min(mx, 14);
        }
        if (colUp == 0) colUp = colLow + 11 + levelsLow;
        int totalH = 0;
        for (int[] t : tiers) totalH += t[1];
        int Y0 = yBase + totalH;
        int top = yBase;
        for (int[] t : tiers) {
            top += t[1];
            Arch.xumizuo(f, OUT_X + t[0], OUT_Z + t[0], top, t[1], null, null);
        }
        int sh = Math.max(3, Math.floorDiv(baysU.get(baysU.size() / 2), 2));
        top = yBase;
        for (int[] t : tiers) {
            top += t[1];
            Arch.balustrade(f, OUT_X + t[0], OUT_Z + t[0], top, gaps(-sh, sh), NONE, 4, true, NONE);
        }
        if (tiers.length > 0) Arch.stairsDown(f, sh, OUT_Z + tiers[tiers.length - 1][0] + 1, Y0, yBase, true, 0, null, 1);
        for (int u = -OUT_X - 2; u < OUT_X + 3; u++)
            for (int v = -OUT_Z - 2; v < OUT_Z + 3; v++) f.put(u, Y0, v, Pal.FLOOR);
        int lowTop = Y0 + colLow, upTop = Y0 + colUp;
        final int ox = OUT_X, oz = OUT_Z, ix = IN_X, iz = IN_Z;
        columns(f, XS, ZS, Y0, (u, v) -> Math.abs(u) == ox || Math.abs(v) == oz ? lowTop : upTop, (u, v) -> {
            boolean outer = Math.abs(u) == ox || Math.abs(v) == oz;
            boolean inner = Math.abs(u) <= ix && Math.abs(v) <= iz && (Math.abs(u) == ix || Math.abs(v) == iz);
            return outer || inner;
        });
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("floor", Y0);
        res.put("out", List.of(OUT_X, OUT_Z));
        int purlin, hx, hz, lv, raf;
        if (fujie) {
            int lowP = beamsAndBrackets(f, OUT_X, OUT_Z, lowTop + 1, XS, ZS, levelsLow, levelsLow);
            if (colLow >= 14) {
                for (Object[] c : Arch.ringCells(IN_X, IN_Z)) {
                    int u = (int) c[0], v = (int) c[1];
                    String face = (String) c[2];
                    int along = face.equals("north") || face.equals("south") ? u : v;
                    if (Math.floorMod(along, 9) == 4 && face.equals("south")) Arch.putLamp(f, "stand", u, Y0 + 2, v + 1, 1, null);
                }
            }
            int upP = beamsAndBrackets(f, IN_X, IN_Z, upTop + 1, XS, ZS, levelsUp, levelsUp);
            walls(f, IN_X, IN_Z, Y0, upTop, XS, ZS);
            int lex = OUT_X + levelsLow + raftersLow, lez = OUT_Z + levelsLow + raftersLow;
            int lrun = lex - IN_X - 3;
            double kl = Math.max(1.0, Math.max(lex, lez) / 45.0);
            int[] layers = {Math.max(1, (int) Py.round(5 * kl)), Math.max(1, (int) Py.round(3 * kl)), Math.max(1, (int) Py.round(1 * kl))};
            H lowH = Arch.roofSurface(lex, lez, lowP + 1, raftersLow, lrun, lrun * 0.5, null, layers,
                    new int[]{(int) Py.round(2 * kl), (int) Py.round(kl) - 1}, new int[]{IN_X, IN_Z});
            Arch.rasterRoof(f, lowH, (u, v, val) -> Math.max(lowP + 1, floor(val) - 2), null);
            for (Object[] c : Arch.ringCells(IN_X + 1, IN_Z + 1)) {
                int u = (int) c[0], v = (int) c[1];
                double val = 0;
                boolean first = true;
                for (int a = -1; a <= 1; a++)
                    for (int cc = -1; cc <= 1; cc++) {
                        Double x = lowH.at(u + a, v + cc);
                        double xv = x == null ? 0 : x;
                        if (first || xv > val) val = xv;
                        first = false;
                    }
                f.put(u, floor(val) + 1, v, B(roof("ridge")));
                f.put(u, floor(val) + 2, v, slab(roof("tile")));
            }
            purlin = upP;
            hx = IN_X;
            hz = IN_Z;
            lv = levelsUp;
            raf = raftersUp;
        } else {
            purlin = beamsAndBrackets(f, OUT_X, OUT_Z, lowTop + 1, XS, ZS, levelsLow, levelsLow);
            walls(f, OUT_X, OUT_Z, Y0, lowTop, XS, ZS);
            hx = OUT_X;
            hz = OUT_Z;
            lv = levelsLow;
            raf = raftersLow;
        }
        int ex = hx + lv + raf, ez = hz + lv + raf;
        int run = ez - raf;
        int rise = (int) Py.round(2 * (hz + lv) / 3.0);
        boolean big = Math.max(ex, ez) >= 30;
        double kScale = Math.max(1.0, Math.max(ex, ez) / 45.0);
        int[] base = big ? new int[]{8, 5, 3, 2, 1} : Math.max(ex, ez) >= 14 ? new int[]{5, 3, 1} : new int[]{2, 1};
        List<Integer> ll = new ArrayList<>();
        for (int L : base) ll.add(Math.max(1, (int) Py.round(L * kScale)));
        if (kScale > 1.5) for (int L : new int[]{8, 5, 3}) ll.add(Math.max(1, (int) Py.round(L * kScale * 0.5)));
        int[] layers = ll.stream().mapToInt(Integer::intValue).toArray();
        int[] push = big ? new int[]{(int) Py.round(3 * kScale), (int) Py.round(1 * kScale)} : new int[]{2, 0};
        H h;
        switch (roof) {
            case "pyramid" -> {
                rise = (int) Py.round((hz + lv) * 0.9);
                h = Arch.roofSurface(ex, ez, purlin + 1, raf, run, rise, null, layers, push, null);
            }
            case "wudian" -> h = Arch.roofSurface(ex, ez, purlin + 1, raf, run, rise, null, layers, push, null);
            case "xuanshan", "juanpeng" -> {
                h = Arch.roofSurface(ex, ez, purlin + 1, raf, run, rise, ex, layers, push, null);
                if (roof.equals("juanpeng")) {
                    double t = h.max();
                    H h2 = new H();
                    for (Map.Entry<Long, Double> e : h.entrySet()) h2.put(e.getKey(), e.getValue() < t - 0.75 ? e.getValue() : t - 0.5);
                    h = h2;
                }
            }
            case "yingshan" -> h = Arch.roofSurface(OUT_X + 1, ez, purlin + 1, raf, run, rise, OUT_X + 1, layers, push, null);
            default -> h = Arch.roofSurface(ex, ez, purlin + 1, raf, run, rise, hx - 2, layers, push, null);
        }
        final int fhx = hx, fhz = hz, fp = purlin;
        Arch.rasterRoof(f, h, (u, v, val) -> Math.abs(u) <= fhx && Math.abs(v) <= fhz ? fp : Math.max(fp, floor(val) - 2), null);
        int ridgeY = floor(h.max());
        if (roof.equals("xieshan")) {
            int gx = hx - 2;
            for (int sgn : new int[]{-1, 1}) {
                int u = sgn * gx;
                for (int v = -ez; v <= ez; v++) {
                    Double t = h.at(u, v), s = h.at(u + sgn, v);
                    if (t == null || s == null) continue;
                    for (int y = floor(s) + 1; y < floor(t); y++) f.put(u, y, v, Pal.RED);
                }
                f.put(u, floor(h.at(u, 0)) - 3, 0, Pal.WHITE);
                f.put(u, floor(h.at(u, 0)) - 4, 0, Pal.WHITE);
            }
            double m = Double.NEGATIVE_INFINITY;
            for (int u = -gx; u <= gx; u++) m = Math.max(m, h.at(u, 0));
            ridgeY = floor(m);
            Arch.mainRidge(f, gx, ridgeY, big ? (int) Py.round(7 * kScale) : 4);
            for (int sgn : new int[]{-1, 1}) {
                List<int[]> cells = new ArrayList<>();
                for (int v = -ez + lv + 4; v < ez - lv - 3; v++) cells.add(new int[]{sgn * gx, v});
                Arch.ridgeCells(f, h, cells);
                for (int sv : new int[]{-1, 1}) {
                    int u0 = sgn * (gx + 1), v0 = sv * (ez - lv - 4);
                    List<int[]> cs = new ArrayList<>();
                    for (int i = 0; i < lv + raf + 5; i++) cs.add(new int[]{u0 + sgn * i, v0 + sv * i});
                    Arch.ridgeCells(f, h, cs);
                }
            }
        } else if (roof.equals("wudian")) {
            int half = ex - ez;
            double m = Double.NEGATIVE_INFINITY;
            for (int u = -half; u <= half; u++) m = Math.max(m, h.at(u, 0));
            ridgeY = floor(m);
            Arch.mainRidge(f, half, ridgeY, big ? (int) Py.round(8 * kScale) : 4);
            for (int sgn : new int[]{-1, 1})
                for (int sv : new int[]{-1, 1}) {
                    List<int[]> cs = new ArrayList<>();
                    for (int t = 1; t <= ez; t++) cs.add(new int[]{sgn * (half + t), sv * t});
                    Arch.ridgeCells(f, h, cs);
                }
        } else if (roof.equals("xuanshan") || roof.equals("juanpeng") || roof.equals("yingshan")) {
            if (!roof.equals("juanpeng")) Arch.mainRidge(f, roof.equals("xuanshan") ? ex : OUT_X + 1, ridgeY, 3);
            for (int sgn : new int[]{-1, 1}) {
                int u = sgn * OUT_X;
                for (int v = -OUT_Z; v <= OUT_Z; v++) {
                    Double t = h.at(u, v);
                    if (t == null) continue;
                    for (int y = purlin + 1; y < floor(t); y++) if (f.get(u, y, v) == null) f.put(u, y, v, Pal.WHITE);
                }
            }
        } else if (roof.equals("pyramid")) {
            for (int sgn : new int[]{-1, 1})
                for (int sv : new int[]{-1, 1}) {
                    List<int[]> cs = new ArrayList<>();
                    for (int t = 1; t <= ex; t++) cs.add(new int[]{sgn * t, sv * t});
                    Arch.ridgeCells(f, h, cs);
                }
            Arch.finial(f, ridgeY, big ? 6 : 4);
        }
        res.put("ridge", ridgeY);
        res.put("eave", List.of(ex, ez));
        res.put("base", yBase);
        res.put("inner", List.of(IN_X, IN_Z));
        return res;
    }

    /** arch.wall_ring: gaps (side, a0, a1). */
    static void wallRing(Fr f, int y, int hx, int hz, int height, List<Object[]> gaps) {
        for (Object[] c : Arch.ringCells(hx, hz)) {
            int u = (int) c[0], v = (int) c[1];
            String face = (String) c[2];
            int along = face.equals("north") || face.equals("south") ? u : v;
            String side = switch (face) {
                case "south" -> "front";
                case "north" -> "back";
                case "west" -> "left";
                default -> "right";
            };
            boolean skip = false;
            for (Object[] g : gaps) if (g[0].equals(side) && (int) g[1] <= along && along <= (int) g[2]) skip = true;
            if (skip) continue;
            for (int yy = y + 1; yy < y + height; yy++) f.put(u, yy, v, yy - y <= 1 ? B("stone_bricks") : B("red_terracotta"));
            f.put(u, y + height, v, B(roof("full")));
            int[] out = Blocks.DIRV.get(face);
            f.put(u + out[0], y + height, v + out[1], stair(roof("tile"), Blocks.OPP.get(face)));
            f.put(u - out[0], y + height, v - out[1], stair(roof("tile"), face));
            f.put(u, y + height + 1, v, B(roof("ridge")));
        }
    }

    /** arch.pavilion (ground None): returns {top}. */
    static Map<String, Object> pavilion(Frame f, int yBase, int size, boolean dbl) {
        int hx = Math.floorDiv(size, 2);
        Arch.xumizuo(f, hx + 2, hx + 2, yBase + 2, 2, null, null);
        Arch.balustrade(f, hx + 2, hx + 2, yBase + 2, gaps(-1, 1), NONE, 3, true, NONE);
        int y = yBase + 2;
        List<Integer> xs = size < 11 ? List.of(-hx, hx) : List.of(-hx, 0, hx);
        int ch = y + Math.max(5, Math.floorDiv(size, 2) + 2);
        columns(f, xs, xs, y, (u, v) -> ch, (u, v) -> Math.abs(u) == hx || Math.abs(v) == hx);
        int p = beamsAndBrackets(f, hx, hx, ch + 1, xs, xs, 1, 1);
        int ex = hx + 3;
        if (dbl) {
            H h = Arch.roofSurface(ex, ex, p + 1, 2, 3, 1.8, null, new int[]{2, 1}, new int[]{1, 0}, new int[]{hx - 1, hx - 1});
            final int pp = p;
            Arch.rasterRoof(f, h, (u, v, val) -> pp + 1, null);
            int y2 = floor(h.max());
            for (int u = -hx + 1; u < hx; u++)
                for (int v = -hx + 1; v < hx; v++)
                    for (int yy = p + 1; yy < y2 + 4; yy++)
                        if (Math.abs(u) == hx - 1 || Math.abs(v) == hx - 1) f.put(u, yy, v, Pal.RED);
            p = y2 + 3;
            ex = hx + 1;
        }
        H h = Arch.roofSurface(ex, ex, p + 1, 2, ex - 2, Py.round(ex * 0.95), null, ex >= 6 ? new int[]{3, 2, 1} : new int[]{1}, new int[]{1, 0}, null);
        final int pp = p;
        Arch.rasterRoof(f, h, (u, v, val) -> pp, null);
        int ry = floor(h.max());
        for (int sgn : new int[]{-1, 1})
            for (int sv : new int[]{-1, 1}) {
                List<int[]> cs = new ArrayList<>();
                for (int t = 1; t <= ex; t++) cs.add(new int[]{sgn * t, sv * t});
                Arch.ridgeCells(f, h, cs);
            }
        Arch.finial(f, ry, 4);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("top", ry);
        return res;
    }

    /** arch.gate_tower (ground None). */
    static Map<String, Object> gateTower(Frame f, int yBase, int width, int depth, int height, int nArch, int archW, int archH, List<Integer> hallBays,
                                         List<Integer> hallDepth) {
        int hx = Math.floorDiv(width, 2), hz = Math.floorDiv(depth, 2);
        int top = yBase + height;
        for (int u = -hx; u <= hx; u++)
            for (int v = -hz; v <= hz; v++) {
                int g = yBase;
                int batter = Math.floorDiv(top - yBase, 10);
                if (Math.max(Math.max(Math.abs(u) - (hx - batter), Math.abs(v) - (hz - batter)), 0) > 0) {
                    int inset = Math.max(Math.abs(u) - hx + batter, Math.abs(v) - hz + batter);
                    int yb = yBase + inset * 10;
                    f.col(u, v, Math.min(g + 1, yBase), yb, B("stone_bricks"));
                    continue;
                }
                f.col(u, v, Math.min(g + 1, yBase), top - 1, B("stone_bricks"));
                f.put(u, top, v, Pal.FLOOR);
            }
        int batter = Math.floorDiv(top - yBase, 10);
        for (Object[] c : Arch.ringCells(hx - batter, hz - batter)) f.put((int) c[0], top - 1, (int) c[1], Pal.JADE);
        int spacing = archW + 6;
        for (int i = 0; i < nArch; i++) {
            int c = (int) Py.round((i - (nArch - 1) / 2.0) * spacing);
            int aw = c == 0 ? archW : archW - 2;
            int ah = c == 0 ? archH : archH - 3;
            double r = aw / 2.0;
            for (int u = c - Math.floorDiv(aw, 2); u < c + Math.floorDiv(aw, 2) + 1; u++) {
                int du = Math.abs(u - c);
                double hh = ah - r + Math.sqrt(Math.max(0.0, r * r - du * du));
                for (int v = -hz - 1; v < hz + 2; v++) {
                    for (int yy = yBase + 1; yy < yBase + (int) hh + 1; yy++) f.put(u, yy, v, AIR);
                    f.put(u, yBase, v, Pal.FLOOR);
                }
            }
            for (int v : new int[]{-hz, hz})
                for (int u = c - Math.floorDiv(aw, 2) - 1; u < c + Math.floorDiv(aw, 2) + 2; u++) {
                    int du = Math.abs(u - c);
                    double hh = ah - r + Math.sqrt(Math.max(0.0, (r + 1) * (r + 1) - du * du));
                    f.put(u, yBase + (int) hh + 1, v, Pal.JADE);
                }
        }
        Arch.balustrade(f, hx - 1, hz - 1, top, NONE, NONE, 4, true, NONE);
        Map<String, Object> res = hall(f, top, hallBays, hallDepth, true, "xieshan", 8, 19, new int[][]{{2, 2}}, 2, 3, 3, 3);
        res.put("well", Platforms.stairWell(f, yBase, (int) res.get("floor"), new int[]{-hx + 1, hx - 1}, -hz, "minecraft:stone_brick_stairs", B("stone_bricks"), null, null));
        return res;
    }

    /** arch.pine. */
    static void pine(Fr f, int u, int y, int v, int height, int seed) {
        long r = h(u, v, seed);
        int leanU = (int) (r % 3) - 1, leanV = (int) ((r >> 2) % 3) - 1;
        int tu = u, tv = v;
        for (int k = 0; k < height; k++) {
            if (k > height * 0.5 && k % 4 == 0) {
                if (leanU != 0) f.put(tu + leanU, y + k, tv, B("spruce_log", "axis", "x"));
                tu += leanU;
                if (leanV != 0) f.put(tu, y + k, tv + leanV, B("spruce_log", "axis", "z"));
                tv += leanV;
            }
            f.put(tu, y + 1 + k, tv, B("spruce_log", "axis", "y"));
        }
        int[][] tiers = {{height - 6, 3}, {height - 3, 4}, {height, 3}};
        for (int tier = 0; tier < 3; tier++) {
            int dy = tiers[tier][0], rad = tiers[tier][1];
            for (int du = -rad - 1; du < rad + 2; du++)
                for (int dv = -rad - 1; dv < rad + 2; dv++) {
                    double d = Math.abs(du) + Math.abs(dv) * 1.3;
                    if (d <= rad + ((h(du, dv, r, tier) % 3) - 1) * 0.6) {
                        for (int t = 0; t <= 1; t++)
                            if (f.get(tu + du, y + dy + t, tv + dv) == null) f.put(tu + du, y + dy + t, tv + dv, B("spruce_leaves", "persistent", true));
                    }
                }
        }
    }

    /** arch.round_tree. */
    static void roundTree(Fr f, int u, int y, int v, int height, String leaves, int seed) {
        for (int k = 0; k < height - 3; k++) f.put(u, y + 1 + k, v, B("dark_oak_log", "axis", "y"));
        int rad = 4;
        int cy = y + height - 2;
        for (int du = -rad; du <= rad; du++)
            for (int dv = -rad; dv <= rad; dv++)
                for (int dy = -2; dy < 4; dy++) {
                    double x = dy * 1.6;
                    double d = du * du + dv * dv + x * x;
                    if (d <= rad * rad + (h(du, dv, dy, seed) % 5) - 2 && f.get(u + du, cy + dy, v + dv) == null)
                        f.put(u + du, cy + dy, v + dv, B(leaves, "persistent", true));
                }
    }
}
