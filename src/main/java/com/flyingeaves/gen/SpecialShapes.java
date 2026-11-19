package com.flyingeaves.gen;

import com.flyingeaves.gen.py.IntPair;
import com.flyingeaves.gen.py.Py;
import com.flyingeaves.gen.py.PyRandom;
import com.flyingeaves.gen.py.PySet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.flyingeaves.gen.Arch.Pal;
import static com.flyingeaves.gen.Blocks.AIR;
import static com.flyingeaves.gen.Blocks.B;
import static com.flyingeaves.gen.Blocks.slab;
import static com.flyingeaves.gen.Blocks.stair;
import static com.flyingeaves.gen.Bgen.ws;

/** bgen.special_shape (牌楼, 影壁, 随墙门, 琉璃门, 垂花门, 宫墙 / 城墙, 铺地, 河桥, 陈设), thick_wall, town_ornament; arch.paifang. */
public final class SpecialShapes {
    private SpecialShapes() {
    }

    static final List<String> ORNAMENTS = List.of("ding", "sundial", "measure", "vat", "well", "stall", "bamboo", "hay", "tree", "veg", "woodpile",
            "lion", "flowers", "pond", "rack", "lamp");
    static final Map<String, Object[]> TREES = new LinkedHashMap<>();

    static {
        TREES.put("huai", new Object[]{"oak_log", "oak_leaves", 4, new int[]{2, 2, 2, 1}});
        TREES.put("liu", new Object[]{"oak_log", "jungle_leaves", 4, new int[]{2, 2, 1}});
        TREES.put("tao", new Object[]{"cherry_log", "cherry_leaves", 3, new int[]{2, 2, 1}});
        TREES.put("song", new Object[]{"spruce_log", "spruce_leaves", 3, new int[]{2, 1, 2, 1, 1, 0}});
        TREES.put("zao", new Object[]{"dark_oak_log", "dark_oak_leaves", 4, new int[]{2, 2, 1}});
        TREES.put("yin", new Object[]{"birch_log", "birch_leaves", 5, new int[]{1, 2, 2, 1}});
    }

    static Map<String, Object> res(int floor, int sh, int top) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("floor", floor);
        r.put("sh", sh);
        r.put("top", top);
        return r;
    }

    /** bgen.town_ornament. */
    static Map<String, Object> townOrnament(Fr f, int y, PyRandom rng, String kind, int hx, int hz, String base) {
        int R = Math.max(1, Math.min(hx, hz));
        boolean hasBase = base != null && !base.isEmpty();
        switch (kind) {
            case "tree" -> {
                String sp = R >= 2 ? rng.choice(List.of("huai", "huai", "liu", "tao", "song", "zao", "yin")) : "tao";
                Object[] t = TREES.get(sp);
                String log = (String) t[0], leaf = (String) t[1];
                int trunk = (int) t[2];
                int[] crown = (int[]) t[3];
                trunk += rng.choice(List.of(0, 0, 1));
                String lv = B(leaf, "persistent", true);
                f.put(0, y, 0, B("grass_block"));
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) f.put(d[0], y, d[1], rng.random() < 0.5 ? B("rooted_dirt") : B("coarse_dirt"));
                int top = y + trunk;
                for (int k = 1; k <= trunk; k++) f.put(0, y + k, 0, B(log, "axis", "y"));
                int yy = top;
                for (int i = 0; i < crown.length; i++) {
                    int rad = Math.min(crown[i], R);
                    yy = top + i;
                    for (int u = -rad; u <= rad; u++)
                        for (int v = -rad; v <= rad; v++) {
                            if (rad >= 2 && Math.abs(u) == rad && Math.abs(v) == rad) continue;
                            if (u == 0 && v == 0 && i < crown.length - 1 && !sp.equals("song")) {
                                f.put(0, yy, 0, B(log, "axis", "y"));
                                continue;
                            }
                            f.put(u, yy, v, lv);
                        }
                }
                if (sp.equals("song")) for (int k = trunk + 1; k < trunk + crown.length - 1; k++) f.put(0, y + k, 0, B(log, "axis", "y"));
                if (sp.equals("liu"))
                    for (int[] d : new int[][]{{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {2, 1}, {-1, 2}, {1, -2}, {-2, -1}})
                        if (rng.random() < 0.7 && Math.abs(d[0]) <= R && Math.abs(d[1]) <= R) {
                            int n = rng.randint(2, 3);
                            for (int k = 1; k <= n; k++) f.put(d[0], top - k, d[1], B("vine", "up", true));
                        }
                return res(y, yy - y, yy + 1);
            }
            case "veg" -> {
                Object[][] crops = {{"wheat", 7}, {"carrots", 7}, {"potatoes", 7}, {"beetroots", 3}};
                for (int v = -hz; v <= hz; v++) {
                    Object[] c = crops[(int) rng.randrange(crops.length)];
                    String crop = (String) c[0];
                    int most = (int) c[1];
                    for (int u = -hx; u <= hx; u++) {
                        if (v == 0 && hz >= 2) {
                            f.put(u, y, v, B("water", "level", 0));
                            f.put(u, y - 1, v, B("dirt"));
                            continue;
                        }
                        f.put(u, y, v, B("farmland", "moisture", 7));
                        f.put(u, y - 1, v, B("dirt"));
                        f.put(u, y + 1, v, B(crop, "age", rng.randint(Math.max(1, most - 4), most)));
                    }
                }
                return res(y, 1, y + 2);
            }
            case "woodpile" -> {
                String log = rng.choice(List.of("oak_log", "spruce_log", "birch_log"));
                for (int u : new int[]{-1, 0, 1}) {
                    for (int v : new int[]{-1, 0}) for (int k : new int[]{1, 2}) f.put(u, y + k, v, B(log, "axis", "x"));
                    f.put(u, y + 3, -1, B("spruce_slab", "type", "bottom"));
                    f.put(u, y + 3, 0, B("spruce_slab", "type", "bottom"));
                }
                f.put(1, y + 1, 1, B("stripped_oak_log", "axis", "y"));
                return res(y, 3, y + 4);
            }
            case "lion" -> {
                f.put(0, y + 1, 0, B("polished_andesite"));
                f.put(0, y + 2, 0, B("chiseled_stone_bricks"));
                f.put(0, y + 3, 0, B("stone_brick_stairs", "facing", "north", "half", "bottom"));
                f.put(0, y + 2, 1, B("stone_brick_stairs", "facing", "north", "half", "bottom"));
                return res(y, 3, y + 4);
            }
            case "flowers" -> {
                String edge = hasBase ? base : B("stone_bricks");
                List<String> flowers = List.of("poppy", "allium", "azure_bluet", "oxeye_daisy", "cornflower", "pink_tulip", "red_tulip", "lily_of_the_valley");
                for (int u = -R; u <= R; u++)
                    for (int v = -R; v <= R; v++) {
                        if (Math.abs(u) == R || Math.abs(v) == R) f.put(u, y + 1, v, edge);
                        else {
                            f.put(u, y, v, B("dirt"));
                            f.put(u, y + 1, v, B("grass_block"));
                            if (u == 0 && v == 0 && R >= 2) f.put(u, y + 2, v, B(rng.choice(List.of("azalea", "flowering_azalea"))));
                            else if (rng.random() < 0.8) f.put(u, y + 2, v, B(rng.choice(flowers)));
                        }
                    }
                return res(y, 2, y + 3);
            }
            case "pond" -> {
                String edge = hasBase ? base : B("stone_bricks");
                for (int u = -R; u <= R; u++)
                    for (int v = -R; v <= R; v++) {
                        if (Math.abs(u) == R || Math.abs(v) == R) {
                            f.put(u, y, v, edge);
                            f.put(u, y - 1, v, edge);
                            f.put(u, y - 2, v, edge);
                        } else {
                            f.put(u, y - 2, v, B("clay"));
                            f.put(u, y - 1, v, B("water", "level", 0));
                            f.put(u, y, v, B("water", "level", 0));
                            if (rng.random() < 0.35) f.put(u, y + 1, v, B("lily_pad"));
                        }
                    }
                return res(y, 1, y + 2);
            }
            case "rack" -> {
                for (int u : new int[]{-1, 1}) for (int k : new int[]{1, 2, 3}) f.put(u, y + k, 0, B("spruce_fence"));
                f.put(0, y + 3, 0, B("spruce_fence", "east", true, "west", true));
                f.put(-1, y + 3, 0, B("spruce_fence", "east", true));
                f.put(1, y + 3, 0, B("spruce_fence", "west", true));
                f.put(0, y + 2, 0, B(rng.choice(List.of("white", "light_blue", "blue", "red", "yellow")) + "_stained_glass_pane", "east", true, "west", true));
                return res(y, 3, y + 4);
            }
            default -> {
                f.put(0, y + 1, 0, B("polished_andesite"));
                f.put(0, y + 2, 0, B("stone_brick_wall", "up", true));
                f.put(0, y + 3, 0, B("lantern", "hanging", false));
                f.put(0, y + 4, 0, B("stone_brick_slab", "type", "bottom"));
                return res(y, 4, y + 5);
            }
        }
    }

    static int roofFor(Fr f, int hx, int hz, int topY, String form, PyRandom rng, int overhang, double steep, double tierK) {
        return Bgen.roofFor(f, hx, hz, topY, form, rng, overhang, steep, true, tierK, null, 0, 0, 0, 0, 0, null, null);
    }

    /** bgen.special_shape. */
    public static Map<String, Object> specialShape(Frame f, int y, PyRandom rng, Map<String, Object> spec, String tier, String shape, int hx, int hz) {
        double k = Bgen.TIER_K.get(tier);
        String wname = Bgen.pick(rng, spec.get("walls"));
        Map<String, Object> W = Bgen.setWalls(wname);
        Arch.roofTheme(Bgen.pick(rng, spec.get("themes")));
        int sh = Dyn.truthy(spec.get("_sh")) ? Dyn.toInt(spec.get("_sh")) : 6;
        String base = Dyn.truthy(spec.get("_base")) ? (String) spec.get("_base") : ws(W, "base");
        boolean palace = wname.equals("palace") || wname.equals("yellow") || wname.equals("jade");
        if ((shape.equals("wall") || shape.equals("gate") || shape.equals("glazedgate")) && hz >= Compose.THICK)
            return thickWall(f, y, spec, shape, hx, hz, base, palace ? B("red_terracotta") : ws(W, "fill"), Math.max(4, Math.min(16, sh)));
        if (shape.equals("wall")) {
            int d = hz < 2 ? 0 : 1;
            int wallH = Math.max(4, Math.min(10, sh));
            String body = palace ? B("red_terracotta") : ws(W, "fill");
            for (int u = -hx; u <= hx; u++)
                for (int v = -d; v <= d; v++) {
                    f.put(u, y + 1, v, base);
                    for (int kk = 2; kk <= wallH; kk++) f.put(u, y + kk, v, body);
                }
            int ry = roofFor(f, hx, d, y + wallH, "xuanshan", rng, 1, 0.45, 1.0);
            return res(y, wallH, ry + 3);
        }
        if (shape.equals("terrace") || shape.equals("street") || shape.equals("stairs") || shape.equals("bridge")) return res(y, 1, y);
        if (shape.equals("ornament")) {
            String kind = ORNAMENTS.get(Math.floorMod(Dyn.truthy(spec.get("_variant")) ? Dyn.toInt(spec.get("_variant")) : 0, ORNAMENTS.size()));
            if (List.of("tree", "veg", "woodpile", "lion", "flowers", "pond", "rack", "lamp").contains(kind)) return townOrnament(f, y, rng, kind, hx, hz, base);
            if (kind.equals("stall")) {
                List<String[]> cloths = List.of(new String[]{"red_wool", "white_wool"}, new String[]{"blue_wool", "white_wool"}, new String[]{"yellow_wool", "brown_wool"});
                String[] cloth = rng.choice(cloths);
                for (int u : new int[]{-1, 1}) for (int yy : new int[]{y + 1, y + 2, y + 3}) f.put(u, yy, -1, B("spruce_fence"));
                f.put(0, y + 1, -1, B("barrel", "facing", "up"));
                for (int u : new int[]{-1, 0, 1}) {
                    f.put(u, y + 1, 0, B("spruce_slab", "type", "top"));
                    for (int v : new int[]{-1, 0}) f.put(u, y + 4, v, B(cloth[Math.floorMod(u + 1, 2)]));
                }
                int su = rng.choice(List.of(-1, 1));
                f.put(su, y + 2, 0, B(rng.choice(List.of("flower_pot", "potted_red_tulip", "decorated_pot", "potted_fern"))));
                return res(y, 4, y + 5);
            }
            if (kind.equals("bamboo")) {
                for (int[] c : new int[][]{{0, 0, 6}, {1, -1, 5}, {-1, 1, 4}, {-1, -1, 3}}) {
                    f.put(c[0], y, c[1], B("grass_block"));
                    for (int kk = 1; kk <= c[2]; kk++)
                        f.put(c[0], y + kk, c[1], B("bamboo", "age", 1, "stage", 0, "leaves", kk >= c[2] - 1 ? "large" : kk == c[2] - 2 ? "small" : "none"));
                }
                return res(y, 6, y + 7);
            }
            if (kind.equals("hay")) {
                for (int[] c : new int[][]{{0, 0, 1}, {1, 0, 1}, {0, 1, 1}, {0, 0, 2}, {-1, -1, 1}}) f.put(c[0], y + c[2], c[1], B("hay_block", "axis", "y"));
                f.put(1, y + 1, -1, B("composter"));
                return res(y, 3, y + 3);
            }
            if (kind.equals("well")) {
                String curb = !palace ? base : B("stone_bricks");
                for (int u : new int[]{-1, 0, 1})
                    for (int v : new int[]{-1, 0, 1})
                        if (u != 0 || v != 0) for (int yy = y - 3; yy < y + 2; yy++) f.put(u, yy, v, curb);
                f.put(0, y - 4, 0, curb);
                for (int yy = y - 3; yy < y + 1; yy++) f.put(0, yy, 0, B("water", "level", 0));
                for (int u : new int[]{-1, 1}) {
                    f.put(u, y + 2, 0, B("spruce_fence"));
                    f.put(u, y + 3, 0, B("spruce_fence"));
                }
                for (int u : new int[]{-1, 0, 1}) f.put(u, y + 4, 0, B("spruce_slab", "type", "bottom"));
                f.put(0, y + 3, 0, B("chain", "axis", "y"));
                return res(y, 4, y + 5);
            }
            String stone = palace ? B("smooth_quartz") : B("polished_andesite");
            for (int u : new int[]{-1, 0, 1}) for (int v : new int[]{-1, 0, 1}) f.put(u, y + 1, v, u != 0 || v != 0 ? B("smooth_stone_slab", "type", "top") : stone);
            switch (kind) {
                case "ding" -> {
                    f.put(0, y + 2, 0, B("cauldron"));
                    for (int u : new int[]{-1, 1}) f.put(u, y + 2, 0, B("lightning_rod", "facing", "up"));
                }
                case "sundial" -> {
                    f.put(0, y + 2, 0, B("quartz_pillar", "axis", "y"));
                    f.put(0, y + 3, 0, B("smooth_quartz_stairs", "facing", "north", "half", "bottom"));
                    f.put(0, y + 4, 0, B("lightning_rod", "facing", "north"));
                }
                case "measure" -> {
                    f.put(0, y + 2, 0, B("quartz_pillar", "axis", "y"));
                    f.put(0, y + 3, 0, B("cut_copper"));
                    f.put(0, y + 4, 0, B("cut_copper_slab", "type", "bottom"));
                }
                default -> {
                    f.put(0, y + 2, 0, B("water_cauldron", "level", 3));
                    for (Object[] c : new Object[][]{{-1, 0, "east"}, {1, 0, "west"}, {0, -1, "south"}, {0, 1, "north"}})
                        f.put((int) c[0], y + 2, (int) c[1], B("cut_copper_stairs", "facing", c[2], "half", "bottom"));
                }
            }
            return res(y, 4, y + 5);
        }
        if (shape.equals("paving")) {
            int road = Math.max(1, Math.floorDiv(Math.min(hx, hz), 4));
            String[] field = {B("stone_bricks"), B("cracked_stone_bricks"), B("stone_bricks"), B("polished_andesite")};
            for (int u = -hx; u <= hx; u++)
                for (int v = -hz; v <= hz; v++) {
                    String blk;
                    if (Math.abs(u) < road) blk = palace ? B("smooth_quartz") : B("smooth_stone");
                    else if (Math.abs(u) == road) blk = Math.floorMod(v, 4) == 0 ? B("chiseled_stone_bricks") : B("polished_andesite");
                    else blk = rng.random() < 0.15 ? field[(int) rng.randrange(field.length)] : field[0];
                    f.put(u, y, v, blk);
                }
            return res(y, 1, y + 1);
        }
        if (shape.equals("river")) {
            int bank = hz >= 4 ? 1 : 0;
            int wv = hz - bank;
            String deck = palace ? B("smooth_quartz") : B("stone_bricks");
            String railU = B("diorite_wall", "east", "low", "west", "low", "north", "none", "south", "none", "up", false);
            String railV = B("diorite_wall", "north", "low", "south", "low", "east", "none", "west", "none", "up", false);
            int width = 2 * hx + 1;
            int count = width < 21 ? 1 : width < 41 ? 3 : 5;
            int gap = Math.max(7, Math.floorDiv(width, count + 1));
            List<Integer> centres = new ArrayList<>();
            centres.add(0);
            for (int i = 1; i < Math.floorDiv(count, 2) + 1; i++) for (int s : new int[]{1, -1}) centres.add(s * i * gap);
            PySet<Integer> bridge = new PySet<>();
            for (int c : centres) for (int du : new int[]{-1, 0, 1}) if (Math.abs(c + du) <= hx) bridge.add(c + du);
            int rvar = Dyn.truthy(spec.get("_variant")) ? Dyn.toInt(spec.get("_variant")) : 0;
            if (rvar == 1 || rvar == 3 || rvar == 4 || rvar == 5) {
                bridge = new PySet<>();
                centres = new ArrayList<>();
            } else if (rvar == 2) {
                List<Integer> all = new ArrayList<>();
                for (int u = -hx; u <= hx; u++) all.add(u);
                bridge = PySet.of(all);
                centres = new ArrayList<>(List.of(0));
            }
            List<Integer> ends = rvar == 3 ? List.of(-hx) : rvar == 4 ? List.of(hx) : rvar == 5 ? List.of(-hx, hx) : List.of();
            for (int u = -hx; u <= hx; u++)
                for (int v = -hz; v <= hz; v++) {
                    f.put(u, y - 3, v, base);
                    if (Math.abs(v) < wv && !ends.contains(u)) {
                        f.put(u, y - 2, v, B("water", "level", 0));
                        f.put(u, y - 1, v, B("water", "level", 0));
                        f.put(u, y, v, AIR);
                    } else {
                        for (int yy : new int[]{y - 2, y - 1, y}) f.put(u, yy, v, base);
                        if (Math.abs(v) == wv && !bridge.contains(u) && !ends.contains(u)) f.put(u, y + 1, v, railU);
                    }
                }
            for (int u : bridge) {
                int c = centres.get(0);
                for (int cc : centres) if (Math.abs(u - cc) < Math.abs(u - c)) c = cc;
                boolean side = rvar != 2 ? Math.abs(u - c) == 1 : Math.abs(u) == hx;
                for (int v = -hz; v <= hz; v++) {
                    if (Math.abs(v) >= wv)
                        f.put(u, y + 1, v, Math.abs(v) == hz ? B(House.stairsOf(deck, "minecraft:stone_brick_stairs"), "facing", v > 0 ? "north" : "south", "half", "bottom") : deck);
                    else {
                        f.put(u, y + 1, v, deck);
                        f.put(u, y, v, Math.abs(v) >= wv - 1 ? deck : AIR);
                    }
                    if (side && Math.abs(v) < hz) f.put(u, y + 2, v, railV);
                }
            }
            return res(y, 2, y + 3);
        }
        if (shape.equals("paifang")) {
            int span = Math.max(5, hx);
            int hOut = Math.max(6, sh + 3);
            paifang(f, y, span, hOut + 4, hOut);
            return res(y, sh, y + hOut + 12);
        }
        int d = hz < 2 ? 0 : 1;
        if (shape.equals("screen")) {
            for (int u = -hx; u <= hx; u++) for (int v = -d - 1; v < d + 2; v++) for (int yy : new int[]{y + 1, y + 2}) f.put(u, yy, v, base);
            int body = Math.max(4, Math.min(8, Math.floorDiv(2 * hx, 3)));
            int y0 = y + 2;
            for (int u = -hx + 1; u < hx; u++)
                for (int v = -d; v <= d; v++)
                    for (int kk = 1; kk <= body; kk++)
                        f.put(u, y0 + kk, v, Math.abs(u) == hx - 1 ? ws(W, "post") : kk == 1 || kk == body ? ws(W, "beam") : ws(W, "fill"));
            int pw = Math.max(1, Math.floorDiv(hx, 3)), ph = Math.max(1, Math.floorDiv(body, 3));
            String glaze = Env.present("minecraft:cyan_glazed_terracotta") ? B("cyan_glazed_terracotta") : ws(W, "fill");
            for (int u = -pw; u <= pw; u++)
                for (int kk = Math.floorDiv(body, 2) - Math.floorDiv(ph, 2) + 1; kk < Math.floorDiv(body, 2) + Math.floorDiv(ph, 2) + 2; kk++)
                    for (int v : new int[]{-d, d}) f.put(u, y0 + kk, v, glaze);
            int ry = roofFor(f, hx - 1, d, y0 + body, "wudian", rng, 1, 0.5, 1.0);
            return res(y, body, ry + 3);
        }
        if (shape.equals("gate") || shape.equals("glazedgate")) {
            int wallH = Math.max(5, Math.min(8, 4 + Math.floorDiv(hx, 4)));
            for (int u = -hx; u <= hx; u++)
                for (int v = -d; v <= d; v++) {
                    f.put(u, y + 1, v, base);
                    for (int kk = 2; kk <= wallH; kk++) f.put(u, y + kk, v, Math.abs(u) <= 1 && kk <= 5 ? AIR : ws(W, "fill"));
                }
            for (int u : new int[]{-2, 2}) for (int v = -d; v <= d; v++) for (int kk = 2; kk < 7; kk++) f.put(u, y + kk, v, ws(W, "post"));
            for (int u = -2; u < 3; u++) for (int v = -d; v <= d; v++) f.put(u, y + 6, v, ws(W, "beam"));
            int ry = roofFor(f, hx, d, y + wallH, "xuanshan", rng, 1, 0.45, 1.0);
            int top = ry;
            if (shape.equals("glazedgate")) {
                String glaze = Env.present("minecraft:yellow_glazed_terracotta") ? B("yellow_glazed_terracotta") : ws(W, "beam");
                for (int u = -3; u < 4; u++)
                    for (int v : new int[]{-d - 1, d + 1})
                        for (int kk = 1; kk < wallH + 2; kk++)
                            if (Math.abs(u) == 3 || kk == wallH || kk == wallH + 1) f.put(u, y + kk, v, kk < wallH ? glaze : ws(W, "beam"));
                int ry2 = roofFor(f.sub(0, 0, "south"), 3, d + 1, y + wallH + 2, "xieshan", rng, 1, 0.55, 1.0);
                top = Math.max(top, ry2);
            }
            return res(y, wallH, top + 3);
        }
        int hz2 = Math.max(2, hz);
        int topY = y + 1 + Math.max(5, sh);
        for (int u = -hx; u <= hx; u++) for (int v = -hz2; v <= hz2; v++) f.put(u, y + 1, v, base);
        for (int v = -hz2; v <= hz2; v++)
            for (int sgn : new int[]{-1, 1})
                for (int yy = y + 2; yy < topY; yy++) f.put(sgn * hx, yy, v, Math.abs(v) == hz2 || v == -1 ? ws(W, "post") : ws(W, "fill"));
        for (int u = -hx; u <= hx; u++)
            for (int yy = y + 2; yy < topY; yy++)
                f.put(u, yy, -1, Math.abs(u) <= 1 && yy <= y + 5 ? AIR : Math.abs(u) != hx ? ws(W, "fill") : ws(W, "post"));
        for (int u = -hx; u <= hx; u++) {
            f.put(u, topY, -1, ws(W, "beam"));
            f.put(u, topY, hz2, ws(W, "beam"));
        }
        for (int sgn : new int[]{-1, 1}) {
            int u = sgn * Math.max(2, hx - 1);
            for (int yy = topY - 2; yy < topY; yy++) f.put(u, yy, hz2, ws(W, "post"));
            f.put(u, topY - 3, hz2, B("dark_oak_fence"));
        }
        for (int u = -hx + 1; u < hx; u++)
            if (f.get(u, topY - 1, hz2) == null) f.put(u, topY - 1, hz2, B("spruce_trapdoor", "facing", "north", "half", "top", "open", false));
        for (int u : new int[]{-1, 0, 1}) f.put(u, y + 1, hz2 + 1, B(House.stairsOf(base, "minecraft:stone_brick_stairs"), "facing", "north", "half", "bottom"));
        int ry = roofFor(f, hx, hz2, topY, "juanpeng", rng, 1, 0.55, k);
        return res(y + 1, topY - y - 1, ry + 3);
    }

    /** bgen.thick_wall: 城墙 (or a gate in one) as thick as its frame. */
    static Map<String, Object> thickWall(Frame f, int y, Map<String, Object> spec, String shape, int hx, int hz, String base, String body, int H) {
        int t = Math.max(0, Math.min(Dyn.truthy(spec.get("_taper")) ? Dyn.toInt(spec.get("_taper")) : 0, hz - 3));
        java.util.function.IntUnaryOperator inset = kk -> H > 1 ? (int) Py.round(t * (kk - 1) / (double) (H - 1)) : 0;
        int ht = hz - t;
        String pave = B("polished_andesite"), cap = slab("stone_brick");
        String lean = !body.contains("terracotta") ? House.stairsOf(body, null) : null;
        for (int u = -hx; u <= hx; u++) {
            int x = f.wx(u, 0), z = f.wz(u, 0);
            boolean merlon = Math.floorMod(x + z, 2) == 0;
            for (int v = -hz; v <= hz; v++) {
                f.put(u, y + 1, v, base);
                for (int kk = 2; kk <= H; kk++) {
                    int e = hz - inset.applyAsInt(kk);
                    if (Math.abs(v) > e) continue;
                    f.put(u, y + kk, v, kk == H && Math.abs(v) < ht ? pave : body);
                    if (lean != null && Math.abs(v) == e && kk < H && inset.applyAsInt(kk + 1) > inset.applyAsInt(kk))
                        f.put(u, y + kk, v, B(lean, "facing", v > 0 ? "north" : "south", "half", "bottom"));
                }
            }
            f.put(u, y + H + 1, ht, body);
            if (merlon) f.put(u, y + H + 2, ht, body);
            f.put(u, y + H + (merlon ? 3 : 2), ht, cap);
            f.put(u, y + H + 1, -ht, body);
            f.put(u, y + H + 2, -ht, cap);
        }
        if (shape.equals("wall")) {
            int run = Arch.stepRun(spec.get("_step_slope"));
            List<Arch.Step> steps = Arch.flight(H, run);
            int L = steps.size();
            Object tun = spec.get("_tunnels");
            if (ht >= 3 && Math.floorDiv(L, 2) + 1 <= hx && L - Math.floorDiv(L, 2) <= hx && !(tun != null && Dyn.eq(tun, 1))) {
                String st = House.stairsOf(base, "minecraft:stone_brick_stairs");
                String sl = Arch.slabFor(st);
                int uTop = L - Math.floorDiv(L, 2) - 1;
                for (Arch.Step s : steps) {
                    int u = uTop - s.k();
                    int ly = y + H - s.dt();
                    for (int v = -hz; v < -ht + 2; v++) {
                        for (int yy = y + 2; yy < ly; yy++) f.put(u, yy, v, body);
                        f.put(u, ly, v, Arch.stepBlock(s.kind(), st, sl, "east"));
                        for (int yy = ly + 1; yy < y + H + 4; yy++) f.put(u, yy, v, AIR);
                    }
                    if (ly <= y + H - 2) {
                        f.put(u, y + H + 1, -ht + 2, body);
                        f.put(u, y + H + 2, -ht + 2, cap);
                    }
                }
                f.put(uTop - L, y + H + 1, -ht + 1, body);
                f.put(uTop - L, y + H + 2, -ht + 1, cap);
            }
            return res(y, H, y + H + 3);
        }
        int w = hx >= 7 ? 5 : 3;
        int half = Math.floorDiv(w, 2);
        int th = Math.max(3, Math.min(H - 3, 4 + half));
        for (int du = -half; du <= half; du++) {
            int rise = th - (Math.abs(du) == half ? 1 : 0);
            for (int v = -hz; v <= hz; v++) {
                f.put(du, y, v, B("stone_bricks"));
                for (int yy = y + 1; yy < y + rise + 1; yy++) f.put(du, yy, v, AIR);
            }
        }
        for (int v = -hz; v <= hz; v++)
            if (Math.abs(v) <= hz - inset.applyAsInt(th))
                for (int side : new int[]{-1, 1}) f.put(side * half, y + th, v, stair("stone_brick", side < 0 ? "east" : "west", "top"));
        if (shape.equals("glazedgate")) {
            String glaze = Env.present("minecraft:yellow_glazed_terracotta") ? B("yellow_glazed_terracotta") : body;
            for (int kk = 1; kk < th + 2; kk++) {
                int e = hz - inset.applyAsInt(kk);
                for (int u = -half - 1; u < half + 2; u++)
                    if (Math.abs(u) == half + 1 || kk == th + 1) for (int v : new int[]{-e, e}) f.put(u, y + kk, v, glaze);
            }
        }
        return res(y, H, y + H + 3);
    }

    /** arch.paifang: 牌坊 三间四柱 with three little hip roofs. */
    static void paifang(Frame f, int y, int span, int hIn, int hOut) {
        int cin = Math.floorDiv(span, 2) - 2, cout = span;
        for (int[] ch : new int[][]{{cin, hIn}, {cout, hOut}}) {
            int c = ch[0], hh = ch[1];
            for (int sgn : new int[]{-1, 1}) {
                int u = sgn * c;
                for (int du = -1; du <= 1; du++) for (int dv = -1; dv <= 1; dv++) f.put(u + du, y + 1, dv, Pal.JADE);
                f.put(u, y + 2, -2, stair("quartz", "south"));
                f.put(u, y + 2, 2, stair("quartz", "north"));
                f.put(u, y + 1, -2, Pal.JADE);
                f.put(u, y + 1, 2, Pal.JADE);
                for (int yy = y + 2; yy < y + hh + 1; yy++) f.put(u, yy, 0, yy < y + hh - 2 ? Pal.JADE : Pal.RED);
            }
        }
        for (int u = -cin; u <= cin; u++) {
            f.put(u, y + hIn - 4, 0, Pal.RED);
            f.put(u, y + hIn - 1, 0, Pal.RED);
            if (Math.abs(u) <= 2) {
                f.put(u, y + hIn - 2, 0, Math.abs(u) < 2 ? Pal.GOLD : B("polished_blackstone"));
                f.put(u, y + hIn - 3, 0, Math.abs(u) < 2 ? Pal.GOLD : B("polished_blackstone"));
            }
        }
        for (int sgn : new int[]{-1, 1})
            for (int u = cin; u <= cout; u++) {
                f.put(sgn * u, y + hOut - 2, 0, Pal.RED);
                f.put(sgn * u, y + hOut - 1, 0, Pal.RED);
            }
        littleRoof(f, -cin - 1, cin + 1, y + hIn + 1, 3);
        for (int sgn : new int[]{-1, 1}) {
            int a = Math.min(sgn * (cin + 2), sgn * (cout + 1)), c = Math.max(sgn * (cin + 2), sgn * (cout + 1));
            littleRoof(f, a, c, y + hOut + 1, 2);
        }
    }

    private static void littleRoof(Frame f, int u0, int u1, int yb, int depth) {
        Frame sub = f.sub(Math.floorDiv(u0 + u1, 2), 0, "south");
        int ex = Math.floorDiv(u1 - u0, 2) + 2;
        Arch.H h = Arch.roofSurface(ex, depth, yb, 1, depth - 1, depth * 0.55, null, new int[]{2, 1}, new int[]{1, 0}, null);
        Arch.rasterRoof(sub, h, (u, v, val) -> yb, null);
        Arch.mainRidge(sub, ex - depth, (int) Math.floor(h.max()), 3);
    }
}
