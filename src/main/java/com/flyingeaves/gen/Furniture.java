package com.flyingeaves.gen;

import com.flyingeaves.gen.py.IntPair;
import com.flyingeaves.gen.py.PyRandom;
import com.flyingeaves.gen.py.PySet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.flyingeaves.gen.Blocks.B;
import static com.flyingeaves.gen.Blocks.slab;
import static com.flyingeaves.gen.Blocks.stair;

/** furniture.py and furniture_big.py: furnishing a room by its use. Pieces come from data/parts (Parts.install). */
public final class Furniture {
    private Furniture() {
    }

    public static final LinkedHashMap<String, Parts.PieceDef> PIECES = new LinkedHashMap<>();

    /** Module import: the code pieces, then the templates over them. */
    static void load() {
        PIECES.clear();
        for (Map.Entry<String, Object> e : Tables.CODE_PIECES.entrySet()) {
            List<Object> wd = Dyn.list(e.getValue());
            Parts.Piece fn = switch (e.getKey()) {
                case "stele" -> Furniture::pStele;
                default -> (f, rng) -> {
                    throw new UnsupportedOperationException("code piece not ported: " + e.getKey());
                };
            };
            PIECES.put(e.getKey(), new Parts.PieceDef(fn, Dyn.toInt(wd.get(0)), Dyn.toInt(wd.get(1))));
        }
        Parts.install(PIECES);
    }

    /** furniture.p_stele: 碑 on a tortoise. */
    static void pStele(Fr f, PyRandom rng) {
        int w = 3;
        for (int u = 0; u < w; u++) for (int v = 0; v < 2; v++) f.put(u, 0, v, B("polished_andesite"));
        f.put(1, 0, 2, stair("andesite", "north"));
        for (int u = 0; u < w; u++)
            for (int y : new int[]{1, 2, 3}) f.put(u, y, 1, u == 1 && (y == 2 || y == 3) ? B("chiseled_stone_bricks") : B("smooth_stone"));
        f.put(0, 4, 1, stair("stone_brick", "east"));
        f.put(1, 4, 1, B("chiseled_stone_bricks"));
        f.put(2, 4, 1, stair("stone_brick", "west"));
        f.put(1, 5, 1, slab("stone_brick"));
    }

    // ------------------------------------------------------------------------------------------ frames

    /** furniture.Keep: writes nothing into some world columns (stairs, cells in front of doorways). */
    static final class Keep implements Fr {
        final Fr f;
        final Set<Long> cols;

        Keep(Fr f, Set<Long> cols) {
            this.f = f;
            this.cols = cols;
        }

        @Override
        public void put(int u, int y, int v, String s) {
            if (!cols.contains(f.wkey(u, v))) f.put(u, y, v, s);
        }

        @Override
        public String get(int u, int y, int v) {
            return f.get(u, y, v);
        }

        @Override
        public Fr sub(int du, int dv, String front) {
            return new Keep(f.sub(du, dv, front), cols);
        }

        @Override
        public int wx(int u, int v) {
            return f.wx(u, v);
        }

        @Override
        public int wz(int u, int v) {
            return f.wz(u, v);
        }

        boolean clearOf(int w, int d) {
            for (int u = 0; u < w; u++) for (int v = 0; v < d; v++) if (cols.contains(f.wkey(u, v))) return false;
            return true;
        }
    }

    /** furniture._Lift: adds a fixed y (pieces are written with y = 0 at the floor they stand on). */
    static final class Lift implements Fr {
        final Fr f;
        final int y;

        Lift(Fr f, int y) {
            this.f = f;
            this.y = y;
        }

        @Override
        public void put(int u, int k, int v, String s) {
            if (s == null) return;
            f.put(u, y + k, v, s);
        }

        @Override
        public String get(int u, int k, int v) {
            return f.get(u, y + k, v);
        }

        @Override
        public Fr sub(int du, int dv, String front) {
            return new Lift(f.sub(du, dv, front), y);
        }

        @Override
        public int wx(int u, int v) {
            return f.wx(u, v);
        }

        @Override
        public int wz(int u, int v) {
            return f.wz(u, v);
        }
    }

    // ------------------------------------------------------------------------------------------ furnish

    record Wall(int ou, int ov, String front, int[] a, int[] b) {
    }

    static Wall wallAt(String side, int t, int u0, int v0, int u1, int v1) {
        return switch (side) {
            case "north" -> new Wall(u0 + t, v0, "south", new int[]{1, 0}, new int[]{0, 1});
            case "south" -> new Wall(u1 - t, v1, "north", new int[]{-1, 0}, new int[]{0, -1});
            case "west" -> new Wall(u0, v1 - t, "east", new int[]{0, -1}, new int[]{1, 0});
            default -> new Wall(u1, v0 + t, "west", new int[]{0, 1}, new int[]{-1, 0});
        };
    }

    /** furniture.furnish: doors = interior cells to keep free (a list, in order). */
    @SuppressWarnings("unchecked")
    public static int furnish(Fr f0, PyRandom rng, String roomType, int u0, int v0, int u1, int v1, int y, List<int[]> doors, String backSide, int sh) {
        Set<Long> keepCols = new HashSet<>();
        for (int[] d : doors) keepCols.add(f0.wkey(d[0], d[1]));
        Keep f = new Keep(f0, keepCols);
        List<Object> spec = Dyn.list(Tables.FURN_ROOMS.containsKey(roomType) ? Tables.FURN_ROOMS.get(roomType) : Tables.FURN_ROOMS.get("storeroom"));
        if ((u1 - u0 + 1) * (v1 - v0 + 1) >= 150) {
            furnishBig(f, rng, roomType, u0, v0, u1, v1, y, doors, sh);
            return 1;
        }
        Set<IntPair> occ = new HashSet<>();
        for (int[] d : doors) for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) occ.add(IntPair.of(d[0] + a, d[1] + b));
        int W = u1 - u0 + 1, D = v1 - v0 + 1;
        if (W < 2 || D < 2) return 0;
        int placed = 0;
        String[] sidesAll = {"north", "south", "west", "east"};
        int area = W * D;
        for (Object so : spec) {
            List<Object> s = Dyn.list(so);
            String name = (String) s.get(0), where = (String) s.get(1);
            List<Object> lohi = Dyn.list(s.get(2));
            int lo = Dyn.toInt(lohi.get(0)), hi = Dyn.toInt(lohi.get(1));
            if (!PIECES.containsKey(name)) continue;
            Parts.PieceDef pd = PIECES.get(name);
            int w = pd.w(), d = pd.d();
            int n = rng.randint(lo, hi);
            if (area >= 40 && (where.equals("wall") || where.equals("corner")) && hi > 0) n += Math.min(2 * hi, Math.floorDiv(area - 20, 40));
            for (int it = 0; it < n; it++) {
                boolean ok = false;
                if (where.equals("back")) ok = tryWall(f, rng, backSide, name, w, d, u0, v0, u1, v1, y, occ, W, D);
                if (!ok && (where.equals("wall") || where.equals("back"))) {
                    List<String> sides = new ArrayList<>(Arrays.asList(sidesAll));
                    rng.shuffle(sides);
                    for (String sd : sides)
                        if (tryWall(f, rng, sd, name, w, d, u0, v0, u1, v1, y, occ, W, D)) {
                            ok = true;
                            break;
                        }
                }
                if (!ok && (where.equals("center") || where.equals("any") || where.equals("corner") || where.equals("rug"))) {
                    List<int[]> cands = new ArrayList<>();
                    if (where.equals("center")) cands.add(new int[]{Math.floorDiv(u0 + u1 - w + 1, 2), Math.floorDiv(v0 + v1 - d + 1, 2)});
                    else if (where.equals("corner")) {
                        cands.add(new int[]{u0, v0});
                        cands.add(new int[]{u1 - w + 1, v0});
                        cands.add(new int[]{u0, v1 - d + 1});
                        cands.add(new int[]{u1 - w + 1, v1 - d + 1});
                        rng.shuffle(cands);
                    } else
                        for (int k = 0; k < 12; k++) {
                            int cu = rng.randint(u0, Math.max(u0, u1 - w + 1));
                            int cv = rng.randint(v0, Math.max(v0, v1 - d + 1));
                            cands.add(new int[]{cu, cv});
                        }
                    for (int[] c : cands) {
                        List<IntPair> cells = new ArrayList<>();
                        for (int i = 0; i < w; i++) for (int j = 0; j < d; j++) cells.add(IntPair.of(c[0] + i, c[1] + j));
                        if (fits(cells, occ, u0, v0, u1, v1)) {
                            pd.fn().draw(new Lift(f.sub(c[0], c[1], "south"), y + 1), rng);
                            occ.addAll(cells);
                            ok = true;
                            break;
                        }
                    }
                }
                if (ok) placed++;
            }
        }
        return placed;
    }

    static boolean fits(List<IntPair> cells, Set<IntPair> occ, int u0, int v0, int u1, int v1) {
        for (IntPair c : cells) if (!(u0 <= c.a() && c.a() <= u1 && v0 <= c.b() && c.b() <= v1 && !occ.contains(c))) return false;
        return true;
    }

    static boolean tryWall(Keep f, PyRandom rng, String side, String name, int w, int d, int u0, int v0, int u1, int v1, int y,
                           Set<IntPair> occ, int W, int D) {
        int L = side.equals("north") || side.equals("south") ? W : D;
        List<Integer> starts = new ArrayList<>();
        for (int t = 0; t < Math.max(1, L - w + 1); t++) starts.add(t);
        rng.shuffle(starts);
        for (int t : starts) {
            Wall wl = wallAt(side, t, u0, v0, u1, v1);
            int ou = wl.ou, ov = wl.ov;
            int[] a = wl.a, b = wl.b;
            List<IntPair> cells = new ArrayList<>();
            for (int i = 0; i < w; i++) for (int j = 0; j < d; j++) cells.add(IntPair.of(ou + a[0] * i + b[0] * j, ov + a[1] * i + b[1] * j));
            if (fits(cells, occ, u0, v0, u1, v1)) {
                Fr sub = f.sub(ou, ov, wl.front);
                PIECES.get(name).fn().draw(new Lift(sub, y + 1), rng);
                occ.addAll(cells);
                int fr = 0, sd = 0;
                if (Tables.CLEARANCE.containsKey(name)) {
                    List<Object> c = Dyn.list(Tables.CLEARANCE.get(name));
                    fr = Dyn.toInt(c.get(0));
                    sd = Dyn.toInt(c.get(1));
                }
                for (int j = d; j < d + fr; j++)
                    for (int i = -sd; i < w + sd; i++) occ.add(IntPair.of(ou + a[0] * i + b[0] * j, ov + a[1] * i + b[1] * j));
                for (int j = 0; j < d; j++) {
                    for (int i = -sd; i < 0; i++) occ.add(IntPair.of(ou + a[0] * i + b[0] * j, ov + a[1] * i + b[1] * j));
                    for (int i = w; i < w + sd; i++) occ.add(IntPair.of(ou + a[0] * i + b[0] * j, ov + a[1] * i + b[1] * j));
                }
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------ furniture_big

    static void place(Fr f, PyRandom rng, String name, int cu, int cv, int y, String front) {
        if (!PIECES.containsKey(name)) return;
        Fr sub = f.sub(cu, cv, front);
        Parts.PieceDef pd = PIECES.get(name);
        if (sub instanceof Keep k && !k.clearOf(pd.w(), pd.d())) return;
        pd.fn().draw(new Lift(sub, y + 1), rng);
    }

    static void place(Fr f, PyRandom rng, String name, int cu, int cv, int y) {
        place(f, rng, name, cu, cv, y, "south");
    }

    /** furniture_big.furnish_big. */
    static void furnishBig(Fr f, PyRandom rng, String rt, int u0, int v0, int u1, int v1, int y, List<int[]> doors, int sh) {
        String lay = Tables.LAYOUTS.containsKey(rt) ? (String) Tables.LAYOUTS.get(rt) : "grid";
        int W = u1 - u0 + 1, D = v1 - v0 + 1;
        int cu = Math.floorDiv(u0 + u1, 2);
        Set<IntPair> doorCells = new HashSet<>();
        for (int[] dd : doors) for (int a = -2; a < 3; a++) for (int b = -2; b < 3; b++) doorCells.add(IntPair.of(dd[0] + a, dd[1] + b));
        List<Object> spec = Tables.FURN_ROOMS.containsKey(rt) ? Dyn.list(Tables.FURN_ROOMS.get(rt)) : List.of();
        List<String> wallPieces = new ArrayList<>();
        for (Object so : spec) {
            List<Object> s = Dyn.list(so);
            if ("wall".equals(s.get(1)) && PIECES.containsKey((String) s.get(0))) wallPieces.add((String) s.get(0));
        }
        if (!wallPieces.isEmpty() && !lay.equals("parlor")) {
            for (String side : new String[]{"west", "east", "north"}) {
                int t = 1;
                int L = side.equals("west") || side.equals("east") ? D : W;
                while (t < L - 2) {
                    String name = wallPieces.get(Math.floorMod(Math.floorDiv(t, 5), wallPieces.size()));
                    int w = PIECES.get(name).w();
                    int ou, ov;
                    String fr;
                    if (side.equals("west")) {
                        ou = u0;
                        ov = v1 - t;
                        fr = "east";
                    } else if (side.equals("east")) {
                        ou = u1;
                        ov = v0 + t;
                        fr = "west";
                    } else {
                        ou = u0 + t;
                        ov = v0;
                        fr = "south";
                    }
                    if (!doorCells.contains(IntPair.of(ou, ov)) && !(side.equals("north") && Math.abs(ou - cu) < 7)) place(f, rng, name, ou, ov, y, fr);
                    t += w + 2;
                }
            }
        }
        String piece = null;
        if (Tables.CENTER_PIECE.containsKey(lay)) {
            Map<String, Object> cp = Dyn.map(Tables.CENTER_PIECE.get(lay));
            piece = (String) cp.get(rt);
        }
        switch (lay) {
            case "axis" -> {
                for (int v = v0 + 4; v <= v1; v++)
                    for (int u : new int[]{cu - 1, cu, cu + 1}) f.put(u, y + 1, v, u == cu ? B("red_carpet") : B("yellow_carpet"));
                for (int du = -5; du < 6; du++)
                    for (int dv = 0; dv < 4; dv++) f.put(cu + du, y + 1, v0 + dv, dv < 3 ? B("quartz_bricks") : B("quartz_slab", "type", "bottom"));
                place(f, rng, piece != null ? piece : "throne", cu - 1, v0 + 1, y + 1);
                for (int s : new int[]{-1, 1}) {
                    place(f, rng, "brazier", cu + s * 6, v0 + 1, y);
                    place(f, rng, "floor_lamp", cu + s * 4, v0 + 4, y);
                }
                String sidePiece = rt.equals("council_hall") || rt.equals("courtroom") || rt.equals("throne_hall") ? "chair" : "cushion";
                for (int v = v0 + 7; v < v1 - 2; v += 3)
                    for (int s : new int[]{-1, 1})
                        for (int k = 2; k < Math.max(3, Math.floorDiv(W, 2) - 4); k += 3)
                            place(f, rng, sidePiece, cu + s * (2 + k), v, y, s > 0 ? "west" : "east");
            }
            case "rows", "grid", "stacks", "bunks", "stalls" -> {
                String name = piece != null ? piece : lay.equals("bunks") ? "bunk" : lay.equals("stalls") ? "beast_pen" : "table";
                int w = PIECES.get(name).w(), d = PIECES.get(name).d();
                int gapU = lay.equals("stacks") || lay.equals("bunks") || lay.equals("stalls") ? 2 : 3;
                int gapV = !lay.equals("rows") ? 3 : 2;
                if (lay.equals("rows")) place(f, rng, rt.equals("task_hall") ? "counter" : "lectern_dais", cu - 1, v0 + 1, y);
                int startV = v0 + (lay.equals("rows") ? 4 : 2);
                for (int v = startV; v < v1 - d - 1; v += d + gapV)
                    for (int u = u0 + 2; u < u1 - w - 1; u += w + gapU) {
                        boolean hit = false;
                        for (int a = 0; a < w && !hit; a++) for (int b = 0; b < d; b++) if (doorCells.contains(IntPair.of(u + a, v + b))) {
                            hit = true;
                            break;
                        }
                        if (hit) continue;
                        if (lay.equals("stacks") || Math.abs(u + Math.floorDiv(w, 2) - cu) > 1) place(f, rng, name, u, v, y);
                    }
            }
            case "parlor" -> {
                place(f, rng, "incense_table", cu - 1, v0, y, "south");
                place(f, rng, "scroll", cu, v0, y + 2, "south");
                place(f, rng, "dining", cu - 2, v0 + 3, y);
                for (int v = v0 + 3; v < v1 - 2; v += 3) {
                    for (int s : new int[]{-1, 1}) {
                        int uu = s < 0 ? u0 : u1;
                        String fr = s < 0 ? "east" : "west";
                        if (doorCells.contains(IntPair.of(uu, v))) continue;
                        place(f, rng, "chair", uu, v, y, fr);
                        if (v + 1 < v1 - 2) place(f, rng, "table", uu, v + 1, y, fr);
                    }
                }
                for (int[] c : new int[][]{{u0, v1}, {u1, v1}})
                    if (!doorCells.contains(IntPair.of(c[0], c[1]))) place(f, rng, "plant", c[0], c[1], y);
            }
            case "workshop" -> {
                String name = piece != null ? piece : "workbench";
                for (int u = u0 + 3; u < u1 - 3; u += 8)
                    for (int v = v0 + 3; v < v1 - 4; v += 8) {
                        place(f, rng, name, u, v, y);
                        place(f, rng, !rt.equals("talisman_room") ? "vat" : "jade_slips", u + 4, v, y);
                    }
            }
            default -> {
                String name = piece != null ? piece : "rug";
                place(f, rng, name, cu - 1, Math.floorDiv(v0 + v1, 2) - 1, y);
                for (int s : new int[]{-1, 1})
                    for (int t : new int[]{-1, 1})
                        place(f, rng, rt.equals("formation_room") || rt.equals("teleport_room") || rt.equals("array_control") ? "crystal" : "plant",
                                cu + s * Math.floorDiv(W, 3), Math.floorDiv(v0 + v1, 2) + t * Math.floorDiv(D, 3), y);
            }
        }
        for (int[] c : new int[][]{{u0 + 1, v0 + 1}, {u1 - 1, v0 + 1}}) place(f, rng, "brazier", c[0], c[1], y);
        String colour = rng.choice(List.of("red", "yellow", "blue", "black", "green"));
        for (int v = v0 + 3; v < v1 - 2; v += 6) {
            if (!doorCells.contains(IntPair.of(u0, v)) && solidBanner(f, u0 - 1, y, v)) f.put(u0, y + 4, v, B(colour + "_wall_banner", "facing", "east"));
            if (!doorCells.contains(IntPair.of(u1, v)) && solidBanner(f, u1 + 1, y, v)) f.put(u1, y + 4, v, B(colour + "_wall_banner", "facing", "west"));
        }
    }

    private static boolean solidBanner(Fr f, int u, int y, int v) {
        String st = f.get(u, y + 4, v);
        return st != null && !st.isEmpty() && !st.equals("minecraft:air") && !Blocks.containsAny(st, "pane", "bars", "fence", "door", "lantern");
    }
}
