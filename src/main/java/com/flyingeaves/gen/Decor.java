package com.flyingeaves.gen;

import com.flyingeaves.gen.py.IntPair;
import com.flyingeaves.gen.py.Py;
import com.flyingeaves.gen.py.PyRandom;
import com.flyingeaves.gen.py.PySet;
import com.flyingeaves.gen.py.UcrtMath;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.flyingeaves.gen.Arch.roof;
import static com.flyingeaves.gen.Blocks.AIR;
import static com.flyingeaves.gen.Blocks.B;
import static com.flyingeaves.gen.Blocks.stair;

/**
 * decor.py and bgen's d_* decor functions (外饰): what a kind's spec names in "decor", and the tier extras
 * (decor.BY_TIER). Every function: (f, rng, hx, hz, yb, n, sh, W), the building's local frame (front = +v).
 * Rooms of 单体建造 have neither; the kinds of the build menu do.
 */
public final class Decor {
    private Decor() {
    }

    interface Fn {
        void draw(Fr f, PyRandom rng, int hx, int hz, int yb, int n, int sh, Map<String, Object> W);
    }

    static int front(int hz, int k) {
        return hz + k;
    }

    static String rail(Map<String, Object> W) {
        Object r = W.get("rail");
        return Dyn.truthy(r) ? (String) r : "spruce_fence";
    }

    // ------------------------------------------------------------------------------------------------ decor.py

    static void zhaobi(Fr f, PyRandom rng, int hx, int hz, int yb, int n, int sh, Map<String, Object> W) {
        int v = front(hz, 7);
        int w = Math.min(hx + 1, 7);
        for (int u = -w; u <= w; u++) {
            for (int k = 1; k < 6; k++)
                f.put(u, yb + k, v, k == 1 ? B("stone_bricks") : (Math.abs(u) <= 1 && k == 3 ? B("chiseled_stone_bricks") : B("white_terracotta")));
            f.put(u, yb + 6, v, B(roof("full")));
            f.put(u, yb + 6, v - 1, stair(roof("tile"), "south"));
            f.put(u, yb + 6, v + 1, stair(roof("tile"), "north"));
        }
    }

    static void drumStones(Fr f, PyRandom rng, int hx, int hz, int yb, int n, int sh, Map<String, Object> W) {
        for (int s : new int[]{-2, 2}) {
            f.put(s, yb + 1, hz + 1, B("chiseled_stone_bricks"));
            f.put(s, yb + 2, hz + 1, stair("stone_brick", "north"));
        }
    }

    static void mountingBlock(Fr f, PyRandom rng, int hx, int hz, int yb, int n, int sh, Map<String, Object> W) {
        f.put(hx - 1, yb + 1, front(hz, 2), stair("stone_brick", "west"));
        f.put(hx, yb + 1, front(hz, 2), B("stone_bricks"));
        for (int k = 1; k <= 3; k++) f.put(-hx + 1, yb + k, front(hz, 2), k < 3 ? B("stone_brick_wall") : B("chiseled_stone_bricks"));
    }

    static void plaque(Fr f, PyRandom rng, int hx, int hz, int yb, int n, int sh, Map<String, Object> W) {
        int y = yb + Math.min(sh - 1, 5);
        for (int u = -2; u <= 2; u++) f.put(u, y, hz + 1, Math.abs(u) == 2 ? B("gold_block") : B("polished_blackstone"));
        f.put(0, y + 1, hz + 1, B("gold_block"));
    }

    static void couplets(Fr f, PyRandom rng, int hx, int hz, int yb, int n, int sh, Map<String, Object> W) {
        for (int side : new int[]{-1, 1}) {
            Integer s = null;
            for (int a : new int[]{2, 1, 3}) {
                boolean wall = true;
                for (int k = 1; k < 5 && wall; k++) {
                    String b = f.get(side * a, yb + k, hz);
                    if (b == null || b.equals(AIR) || Blocks.containsAny(b, "_door", "pane", "bars", "fence")) wall = false;
                }
                if (wall) {
                    s = side * a;
                    break;
                }
            }
            if (s == null) continue;
            for (int k = 2; k < 5; k++) f.put(s, yb + k, hz + 1, k == 4 ? B("red_wall_banner", "facing", "south") : B("red_terracotta"));
        }
    }

    static void shopSign(Fr f, PyRandom rng, int hx, int hz, int yb, int n, int sh, Map<String, Object> W) {
        int side = rng.choice(new int[]{-1, 1});
        int u = side * (hx - 1);
        int y = yb + sh - 1;
        for (int t = 1; t < 4; t++) f.put(u, y, hz + t, B("spruce_fence", "north", true, "south", true));
        String col = rng.choice("red", "blue", "white", "yellow", "black", "green");
        for (int k = 1; k < 4; k++) f.put(u, y - k, hz + 3, k < 3 ? B(col + "_wool") : B(col + "_carpet"));
    }

    static void prayerFlags(Fr f, PyRandom rng, int hx, int hz, int yb, int n, int sh, Map<String, Object> W) {
        String col = rng.choice("red", "yellow", "white", "blue");
        for (int s : new int[]{-1, 1}) {
            int u = s * (hx + 2);
            for (int k = 1; k < 12; k++) f.put(u, yb + k, front(hz, 2), B("spruce_fence"));
            f.put(u, yb + 12, front(hz, 2), B("gold_block"));
            for (int k = 6; k < 11; k++) f.put(u, yb + k, front(hz, 3), B(col + "_wool"));
        }
    }

    static void windChimes(Fr f, PyRandom rng, int hx, int hz, int yb, int n, int sh, Map<String, Object> W) {
        int y = yb + n * sh;
        for (int su : new int[]{-1, 1})
            for (int sv : new int[]{-1, 1}) {
                int u = su * (hx + 2), v = sv * (hz + 2);
                f.put(u, y, v, B("chain", "axis", "y"));
                f.put(u, y - 1, v, B("bell", "attachment", "ceiling", "facing", "south"));
            }
    }

    static void ridgeBeasts(Fr f, PyRandom rng, int hx, int hz, int yb, int n, int sh, Map<String, Object> W) {
        int y = yb + n * sh + 1;
        for (int su : new int[]{-1, 1})
            for (int sv : new int[]{-1, 1}) {
                int u = su * (hx + 2), v = sv * (hz + 2);
                for (int yy = y + 12; yy > y - 6; yy--) {
                    String st = f.get(u, yy, v);
                    if (st != null && !st.isEmpty() && !st.equals("minecraft:air")) {
                        f.put(u, yy + 1, v, B("polished_blackstone_wall"));
                        f.put(u, yy + 2, v, B("polished_blackstone_button", "face", "floor", "facing", "south"));
                        break;
                    }
                }
            }
    }

    static void stoneLanterns(Fr f, PyRandom rng, int hx, int hz, int yb, int n, int sh, Map<String, Object> W) {
        for (int s : new int[]{-1, 1}) Arch.putLamp(f, "garden", s * 3, yb, front(hz, 4), 1, null);
    }

    static void waterVat(Fr f, PyRandom rng, int hx, int hz, int yb, int n, int sh, Map<String, Object> W) {
        for (int s : new int[]{-1, 1})
            f.put(s * (hx - 1), yb + 1, front(hz, 2), rng.random() < 0.5 ? B("water_cauldron", "level", 3) : B("decorated_pot"));
    }

    static void millstone(Fr f, PyRandom rng, int hx, int hz, int yb, int n, int sh, Map<String, Object> W) {
        int u = -hx - 3, v = hz - 1;
        f.put(u, yb + 1, v, B("smooth_stone"));
        f.put(u, yb + 2, v, B("grindstone", "face", "floor", "facing", "east"));
        f.put(u + 1, yb + 2, v, B("spruce_fence", "west", true));
    }

    static void bamboo(Fr f, PyRandom rng, int hx, int hz, int yb, int n, int sh, Map<String, Object> W) {
        int side = rng.choice(new int[]{-1, 1});
        for (int v = -hz; v < hz + 1; v += 2) {
            if (rng.random() < 0.6) {
                int hgt = rng.randint(5, 10);
                for (int k = 1; k < hgt; k++)
                    f.put(side * (hx + 2), yb + k, v, B("bamboo", "age", 1, "leaves", k > hgt - 3 ? "large" : "none", "stage", 0));
            }
        }
    }

    static void koiPond(Fr f, PyRandom rng, int hx, int hz, int yb, int n, int sh, Map<String, Object> W) {
        int side = rng.choice(new int[]{-1, 1});
        int cu = side * (hx + 7);
        PySet<IntPair> water = new PySet<>();
        for (int u = cu - 4; u < cu + 5; u++)
            for (int v = -3; v < 4; v++)
                if ((u - cu) * (u - cu) / 20.0 + v * v / 9.0 <= 1) water.add(IntPair.of(u, v));
        for (IntPair p : water) {
            f.put(p.a(), yb, p.b(), B("water"));
            f.put(p.a(), yb - 1, p.b(), B("dark_prismarine"));
        }
        for (IntPair p : water)
            for (int du = -1; du <= 1; du++)
                for (int dv = -1; dv <= 1; dv++) {
                    IntPair c = IntPair.of(p.a() + du, p.b() + dv);
                    if (!water.contains(c)) {
                        f.put(c.a(), yb, c.b(), B("mossy_cobblestone"));
                        f.put(c.a(), yb - 1, c.b(), B("mossy_cobblestone"));
                    }
                }
        for (int v = -3; v < 4; v++)
            f.put(cu, yb + (Math.abs(v) < 2 ? 1 : 0), v, B("spruce_slab", "type", Math.abs(v) >= 2 ? "bottom" : "top"));
    }

    static void laundry(Fr f, PyRandom rng, int hx, int hz, int yb, int n, int sh, Map<String, Object> W) {
        int u0 = hx + 2;
        for (int v : new int[]{-2, 3}) for (int k = 1; k < 4; k++) f.put(u0, yb + k, v, B("spruce_fence"));
        for (int v = -1; v < 3; v++) {
            f.put(u0, yb + 3, v, B("tripwire"));
            f.put(u0, yb + 2, v, B(rng.choice("white", "light_blue", "red", "blue") + "_stained_glass_pane", "north", true, "south", true));
        }
    }

    static void farmTools(Fr f, PyRandom rng, int hx, int hz, int yb, int n, int sh, Map<String, Object> W) {
        f.put(-hx - 1, yb + 1, -1, B("hay_block"));
        f.put(-hx - 1, yb + 1, 0, B("composter"));
        f.put(-hx - 1, yb + 1, 1, B("spruce_trapdoor", "facing", "west", "half", "bottom", "open", true));
    }

    static void cart(Fr f, PyRandom rng, int hx, int hz, int yb, int n, int sh, Map<String, Object> W) {
        int u = hx + 3, v = -1;
        for (int t = 0; t < 3; t++) f.put(u, yb + 1, v + t, B("spruce_slab", "type", "top"));
        f.put(u - 1, yb + 1, v + 1, B("spruce_trapdoor", "facing", "west", "half", "bottom", "open", true));
        f.put(u + 1, yb + 1, v + 1, B("spruce_trapdoor", "facing", "east", "half", "bottom", "open", true));
        f.put(u, yb + 1, v + 3, B("spruce_fence"));
        f.put(u, yb + 2, v, B("barrel", "facing", "up"));
    }

    static void stall(Fr f, PyRandom rng, int hx, int hz, int yb, int n, int sh, Map<String, Object> W) {
        int v = front(hz, 3);
        for (int u = -2; u < 3; u++) {
            f.put(u, yb + 1, v, Math.floorMod(u, 2) != 0 ? B("barrel", "facing", "up") : B("spruce_planks"));
            f.put(u, yb + 2, v, Math.floorMod(u, 2) == 0 ? B(rng.choice("flower_pot", "decorated_pot", "candle")) : AIR);
        }
        String col = rng.choice("red", "white", "yellow", "blue");
        for (int u = -3; u < 4; u++) {
            for (int t = 0; t <= 1; t++) f.put(u, yb + 4, v - t, Math.floorMod(u + t, 2) != 0 ? B(col + "_carpet") : B("white_carpet"));
            f.put(u, yb + 4, v + 1, AIR);
        }
        for (int u : new int[]{-3, 3}) for (int k = 1; k < 4; k++) f.put(u, yb + k, v, B("spruce_fence"));
    }

    static void steleSmall(Fr f, PyRandom rng, int hx, int hz, int yb, int n, int sh, Map<String, Object> W) {
        int u = hx + 2, v = front(hz, 3);
        f.put(u, yb + 1, v, B("polished_andesite"));
        for (int k = 2; k <= 4; k++) f.put(u, yb + k, v, B("polished_blackstone"));
        f.put(u, yb + 5, v, B("polished_blackstone_slab", "type", "bottom"));
    }

    static void runeFloor(Fr f, PyRandom rng, int hx, int hz, int yb, int n, int sh, Map<String, Object> W) {
        double rad = Math.min(hx, hz) - 2.6;
        if (rad < 2.4) return;
        for (int u = -hx + 1; u < hx; u++)
            for (int v = -hz + 1; v < hz; v++) {
                double r = UcrtMath.hypotInt(u, v);
                if (rad - 1 <= r && r <= rad) f.put(u, yb, v, Math.floorMod(u + v, 3) == 0 ? B("sea_lantern") : B("chiseled_polished_blackstone"));
                else if (r < 1) f.put(u, yb, v, B("glowstone"));
            }
    }

    static void flyingSword(Fr f, PyRandom rng, int hx, int hz, int yb, int n, int sh, Map<String, Object> W) {
        int v = front(hz, 5);
        int y = yb + 6 + rng.randint(0, 4);
        f.put(0, y, v, B("end_rod", "facing", "up"));
        f.put(0, y + 1, v, B("end_rod", "facing", "up"));
        f.put(0, y + 2, v, B("gold_block"));
        f.put(0, y + 3, v, B("lightning_rod", "facing", "up"));
    }

    static void spiritLamps(Fr f, PyRandom rng, int hx, int hz, int yb, int n, int sh, Map<String, Object> W) {
        for (int s : new int[]{-1, 1})
            for (int v : new int[]{hz - 2, -hz + 2}) {
                int u = s * (hx + 2);
                f.put(u, yb + 1, v, B("polished_blackstone"));
                f.put(u, yb + 2, v, B("polished_blackstone_wall"));
                f.put(u, yb + 3, v, B("amethyst_block"));
                f.put(u, yb + 4, v, B("amethyst_cluster", "facing", "up"));
            }
    }

    static void lions(Fr f, PyRandom rng, int hx, int hz, int yb, int n, int sh, Map<String, Object> W) {
        String mat = rng.choice("stone_brick", "quartz", "polished_blackstone", "cut_copper");
        for (int s : new int[]{-1, 1}) {
            int u = s * Math.max(3, Math.floorDiv(hx, 2) + 1);
            int v = front(hz, 3);
            f.put(u, yb + 1, v, B("polished_andesite"));
            f.put(u, yb + 2, v, !mat.equals("cut_copper") ? B(mat + (mat.equals("quartz") ? "_bricks" : mat.equals("stone_brick") ? "s" : "")) : B("cut_copper"));
            f.put(u, yb + 3, v, stair(mat, "north"));
        }
    }

    static void bonsaiRow(Fr f, PyRandom rng, int hx, int hz, int yb, int n, int sh, Map<String, Object> W) {
        for (int u = -hx; u < hx + 1; u += 3) {
            if (Math.abs(u) < 2) continue;
            f.put(u, yb + 1, front(hz, 1), B("polished_andesite"));
            f.put(u, yb + 2, front(hz, 1), B(rng.choice("potted_azalea_bush", "potted_flowering_azalea_bush", "potted_spruce_sapling", "potted_bamboo")));
        }
    }

    static void nets(Fr f, PyRandom rng, int hx, int hz, int yb, int n, int sh, Map<String, Object> W) {
        for (int v = -hz; v < hz + 1; v += 3)
            for (int k = 1; k <= 3; k++) f.put(hx + 2, yb + k, v, k < 3 ? B("spruce_fence") : B("cobweb"));
    }

    static void dryingRacks(Fr f, PyRandom rng, int hx, int hz, int yb, int n, int sh, Map<String, Object> W) {
        int v = front(hz, 4);
        String mat = rng.choice("hay_block", "yellow_wool", "blue_wool", "red_wool", "flowering_azalea_leaves");
        for (int u = -4; u < 5; u++) {
            f.put(u, yb + 1, v, Math.floorMod(u, 2) != 0 ? B("spruce_fence") : (mat.contains("leaves") ? B(mat, "persistent", true) : B(mat)));
            f.put(u, yb + 2, v, B("spruce_slab", "type", "bottom"));
        }
    }

    static void waterwheel(Fr f, PyRandom rng, int hx, int hz, int yb, int n, int sh, Map<String, Object> W) {
        int u0 = hx + 1;
        int R = 4;
        for (int a = 0; a < 360; a += 10) {
            double r = Math.toRadians(a);
            int v = (int) Py.round(R * StrictMath.cos(r)), k = (int) Py.round(R * StrictMath.sin(r));
            f.put(u0, yb + R + k, v, B("spruce_planks"));
        }
        for (int t = -R; t <= R; t++) {
            f.put(u0, yb + R, t, B("stripped_spruce_log", "axis", "z"));
            f.put(u0, yb + R + t, 0, B("stripped_spruce_log", "axis", "y"));
        }
        f.put(u0 - 1, yb + R, 0, B("stripped_spruce_log", "axis", "x"));
    }

    static void watchCorners(Fr f, PyRandom rng, int hx, int hz, int yb, int n, int sh, Map<String, Object> W) {
        for (int su : new int[]{-1, 1})
            for (int sv : new int[]{-1, 1}) {
                int u = su * hx, v = sv * hz;
                for (int k = 1; k < sh + 4; k++) f.put(u, yb + k, v, B("stone_bricks"));
                f.put(u, yb + sh + 4, v, B("campfire", "lit", true));
            }
    }

    static final Map<String, Fn> ALL = new LinkedHashMap<>();
    static final Map<String, List<String>> BY_TIER = new LinkedHashMap<>();

    static {
        ALL.put("zhaobi", Decor::zhaobi);
        ALL.put("drum_stones", Decor::drumStones);
        ALL.put("mounting_block", Decor::mountingBlock);
        ALL.put("plaque", Decor::plaque);
        ALL.put("couplets", Decor::couplets);
        ALL.put("shop_sign", Decor::shopSign);
        ALL.put("prayer_flags", Decor::prayerFlags);
        ALL.put("wind_chimes", Decor::windChimes);
        ALL.put("ridge_beasts", Decor::ridgeBeasts);
        ALL.put("stone_lanterns", Decor::stoneLanterns);
        ALL.put("water_vat", Decor::waterVat);
        ALL.put("millstone", Decor::millstone);
        ALL.put("bamboo", Decor::bamboo);
        ALL.put("koi_pond", Decor::koiPond);
        ALL.put("laundry", Decor::laundry);
        ALL.put("farm_tools", Decor::farmTools);
        ALL.put("cart", Decor::cart);
        ALL.put("stall", Decor::stall);
        ALL.put("stele_small", Decor::steleSmall);
        ALL.put("rune_floor", Decor::runeFloor);
        ALL.put("flying_sword", Decor::flyingSword);
        ALL.put("spirit_lamps", Decor::spiritLamps);
        ALL.put("lions", Decor::lions);
        ALL.put("bonsai_row", Decor::bonsaiRow);
        ALL.put("nets", Decor::nets);
        ALL.put("drying_racks", Decor::dryingRacks);
        ALL.put("waterwheel", Decor::waterwheel);
        ALL.put("watch_corners", Decor::watchCorners);
        BY_TIER.put("mortal", List.of("laundry", "farm_tools", "cart", "millstone", "bamboo", "water_vat", "couplets", "drum_stones"));
        BY_TIER.put("lianqi", List.of("water_vat", "stone_lanterns", "bonsai_row", "bamboo", "couplets", "plaque", "stele_small"));
        BY_TIER.put("zhuji", List.of("stone_lanterns", "bonsai_row", "plaque", "wind_chimes", "koi_pond", "spirit_lamps", "stele_small"));
        BY_TIER.put("jindan", List.of("plaque", "wind_chimes", "ridge_beasts", "koi_pond", "spirit_lamps", "rune_floor", "lions", "prayer_flags"));
        BY_TIER.put("yuanying", List.of("plaque", "wind_chimes", "ridge_beasts", "spirit_lamps", "rune_floor", "flying_sword", "lions", "zhaobi"));
        BY_TIER.put("huashen", List.of("wind_chimes", "ridge_beasts", "spirit_lamps", "rune_floor", "flying_sword", "lions", "zhaobi", "prayer_flags"));
    }

    // ------------------------------------------------------------------------------------------------ furniture code pieces

    /** furniture.p_well. */
    static void pWell(Fr f, PyRandom rng) {
        for (int u = 0; u < 3; u++)
            for (int v = 0; v < 3; v++) {
                boolean mid = u == 1 && v == 1;
                f.put(u, 0, v, mid ? B("water") : B("stone_bricks"));
                f.put(u, 1, v, mid ? AIR : B("stone_brick_wall"));
            }
        f.put(1, 2, 1, B("chain", "axis", "y"));
    }

    /** furniture.p_small_furnace. */
    static void pSmallFurnace(Fr f, PyRandom rng) {
        f.put(0, 0, 0, B("campfire", "lit", true));
        f.put(0, 1, 0, B("cauldron"));
    }

    /** furniture.p_plant. */
    static void pPlant(Fr f, PyRandom rng) {
        f.put(0, 0, 0, B(rng.choice("potted_azalea_bush", "potted_flowering_azalea_bush", "potted_fern", "potted_bamboo",
                "potted_red_tulip", "potted_lily_of_the_valley", "potted_spruce_sapling", "potted_cherry_sapling")));
    }

    /** furniture.p_herb_rack. */
    static void pHerbRack(Fr f, PyRandom rng) {
        for (int u = 0; u < 3; u++) {
            f.put(u, 0, 0, B("spruce_fence"));
            f.put(u, 1, 0, u % 2 != 0 ? B("flowering_azalea_leaves", "persistent", true) : B("azalea_leaves", "persistent", true));
            f.put(u, 2, 0, B("spruce_trapdoor", "facing", "south", "half", "bottom", "open", false));
        }
    }

    /** furniture.p_crates (2 x 2; the choice in it is never drawn: "... if False else ..."). */
    static void pCrates(Fr f, PyRandom rng) {
        for (int u = 0; u < 2; u++)
            for (int v = 0; v < 2; v++) {
                f.put(u, 0, v, B("barrel", "facing", "up"));
                if (rng.random() < 0.5) f.put(u, 1, v, B("barrel", "facing", "up"));
            }
    }

    /** furniture.p_chest. */
    static void pChest(Fr f, PyRandom rng) {
        f.put(0, 0, 0, B("chest", "facing", "south"));
    }

    static Fr lift(Fr f, int y) {
        return new Furniture.Lift(f, y);
    }

    // ------------------------------------------------------------------------------------------------ bgen d_*

    static void dLanterns(Fr f, PyRandom rng, int hx, int hz, int yb, int n, int sh, Map<String, Object> W) {
        for (int u : new int[]{-hx, hx})
            if (f.get(u, yb + 1, hz + 2) == null) Arch.putLamp(f, "corner", u, yb, hz + 2, 1, Map.of("rail", rail(W)));
    }

    static void dSignpole(Fr f, PyRandom rng, int hx, int hz, int yb, int n, int sh, Map<String, Object> W) {
        int u = hx + 2;
        for (int k = 1; k < 8; k++) f.put(u, yb + k, hz + 1, B("spruce_fence"));
        f.put(u, yb + 8, hz + 1, B("spruce_fence"));
        for (int k = 5; k <= 7; k++) f.put(u, yb + k, hz + 2, B(rng.choice("red", "white", "yellow", "blue") + "_wool"));
    }

    static void dWoodpile(Fr f, PyRandom rng, int hx, int hz, int yb, int n, int sh, Map<String, Object> W) {
        for (int v = -hz; v < -hz + 4; v++)
            for (int k = 1; k <= 2; k++) f.put(hx + 1, yb + k, v, B("spruce_log", "axis", "z"));
    }

    static void dVat(Fr f, PyRandom rng, int hx, int hz, int yb, int n, int sh, Map<String, Object> W) {
        f.put(-hx - 1, yb + 1, hz, B("water_cauldron", "level", 3));
    }

    static void dFenceYard(Fr f, PyRandom rng, int hx, int hz, int yb, int n, int sh, Map<String, Object> W) {
        for (int u = -hx - 3; u < hx + 4; u++)
            if (Math.abs(u) > 1) f.put(u, yb + 1, hz + 6, B("oak_fence", "east", true, "west", true));
        for (int v = -hz; v < hz + 7; v++)
            for (int u : new int[]{-hx - 3, hx + 3}) f.put(u, yb + 1, v, B("oak_fence", "north", true, "south", true));
    }

    static void dDrying(Fr f, PyRandom rng, int hx, int hz, int yb, int n, int sh, Map<String, Object> W) {
        for (int u = -2; u < 3; u++) f.put(u, yb + 1, hz + 4, Math.floorMod(u, 2) != 0 ? B("hay_block") : B("spruce_fence"));
    }

    static void dChimney(Fr f, PyRandom rng, int hx, int hz, int yb, int n, int sh, Map<String, Object> W) {
        int u = hx - 1, v = -hz + 1;
        for (int k = 1; k < n * sh + 9; k++) f.put(u, yb + k, v, B("bricks"));
        f.put(u, yb + n * sh + 9, v, B("campfire", "lit", true));
    }

    static void dWell(Fr f, PyRandom rng, int hx, int hz, int yb, int n, int sh, Map<String, Object> W) {
        pWell(lift(f.sub(-hx - 5, hz + 2, "south"), yb), rng);
    }

    static void dLions(Fr f, PyRandom rng, int hx, int hz, int yb, int n, int sh, Map<String, Object> W) {
        for (int s : new int[]{-1, 1}) {
            int u = s * Math.max(3, Math.floorDiv(hx, 2));
            f.put(u, yb + 1, hz + 3, B("polished_andesite"));
            f.put(u, yb + 2, hz + 3, B("chiseled_stone_bricks"));
            f.put(u, yb + 3, hz + 3, stair("stone_brick", "north"));
        }
    }

    static void dIncense(Fr f, PyRandom rng, int hx, int hz, int yb, int n, int sh, Map<String, Object> W) {
        pSmallFurnace(lift(f.sub(0, hz + 5, "south"), yb), rng);
        f.put(0, yb + 1, hz + 5, B("polished_blackstone"));
        f.put(0, yb + 2, hz + 5, B("campfire", "lit", true));
    }

    static void dPlants(Fr f, PyRandom rng, int hx, int hz, int yb, int n, int sh, Map<String, Object> W) {
        for (int u : new int[]{-hx - 1, hx + 1})
            for (int v : new int[]{hz - 1, hz + 1}) pPlant(lift(f.sub(u, v, "south"), yb), rng);
    }

    static void dHerbRacks(Fr f, PyRandom rng, int hx, int hz, int yb, int n, int sh, Map<String, Object> W) {
        pHerbRack(lift(f.sub(-hx, hz + 3, "south"), yb), rng);
    }

    static void dCratesOut(Fr f, PyRandom rng, int hx, int hz, int yb, int n, int sh, Map<String, Object> W) {
        pCrates(lift(f.sub(hx - 1, hz + 2, "south"), yb), rng);
    }

    static void dAnvilOut(Fr f, PyRandom rng, int hx, int hz, int yb, int n, int sh, Map<String, Object> W) {
        f.put(hx - 2, yb + 1, hz + 2, B("anvil", "facing", "east"));
        f.put(hx - 3, yb + 1, hz + 2, B("water_cauldron", "level", 3));
    }

    static void dSpiritField(Fr f, PyRandom rng, int hx, int hz, int yb, int n, int sh, Map<String, Object> W) {
        Xianxia.spiritField(f, -hx, hz + 3, hx, hz + 14, yb, rng.randint(0, 99));
    }

    static void dJars(Fr f, PyRandom rng, int hx, int hz, int yb, int n, int sh, Map<String, Object> W) {
        for (int u = -hx; u < -hx + 4; u++) f.put(u, yb + 1, hz + 2, B("decorated_pot"));
    }

    static void dBeaconTop(Fr f, PyRandom rng, int hx, int hz, int yb, int n, int sh, Map<String, Object> W) {
    }

    static final Map<String, String> DECOR_ALIAS = new LinkedHashMap<>();
    static final Map<String, Fn> DECOR = new LinkedHashMap<>();

    static {
        String[] al = {"mill_stone", "millstone", "hunter_rack", "drying_racks", "nets", "nets", "dye_racks", "drying_racks", "flags", "prayer_flags",
                "drum", "drum_stones", "plaque", "plaque", "bonsai_out", "bonsai_row", "watch_corners", "watch_corners", "lions", "lions",
                "waterwheel", "waterwheel", "sacks_out", "cart", "drying_rack", "drying_racks"};
        for (int i = 0; i < al.length; i += 2) DECOR_ALIAS.put(al[i], al[i + 1]);
        DECOR.put("lanterns", Decor::dLanterns);
        DECOR.put("signpole", Decor::dSignpole);
        DECOR.put("woodpile", Decor::dWoodpile);
        DECOR.put("vat", Decor::dVat);
        DECOR.put("fence_yard", Decor::dFenceYard);
        DECOR.put("drying_rack", Decor::dDrying);
        DECOR.put("chimney", Decor::dChimney);
        DECOR.put("well", Decor::dWell);
        DECOR.put("lions", Decor::dLions);
        DECOR.put("incense_burner", Decor::dIncense);
        DECOR.put("plants", Decor::dPlants);
        DECOR.put("herb_racks", Decor::dHerbRacks);
        DECOR.put("crates_out", Decor::dCratesOut);
        DECOR.put("anvil_out", Decor::dAnvilOut);
        DECOR.put("spirit_field", Decor::dSpiritField);
        DECOR.put("jars", Decor::dJars);
        DECOR.put("beacon_top", Decor::dBeaconTop);
        DECOR.put("mill_stone", Decor::dDrying);
        DECOR.put("hunter_rack", Decor::dDrying);
        DECOR.put("nets", Decor::dDrying);
        DECOR.put("sacks_out", Decor::dCratesOut);
        DECOR.put("dye_racks", Decor::dDrying);
        DECOR.put("stable_out", Decor::dFenceYard);
        DECOR.put("flags", Decor::dSignpole);
        DECOR.put("drum", Decor::dLions);
        DECOR.put("plaque", Decor::dLanterns);
        DECOR.put("bonsai_out", Decor::dPlants);
        DECOR.put("pagoda_small", Decor::dPlants);
        DECOR.put("watch_corners", Decor::dLanterns);
    }

    /** bgen.decorate's lookup: decor.ALL (through DECOR_ALIAS) first, else bgen's own d_*; nothing for an unknown name. */
    static void run(String name, Fr f, PyRandom rng, int hx, int hz, int yb, int n, int sh, Map<String, Object> W) {
        Fn fn = ALL.get(DECOR_ALIAS.getOrDefault(name, name));
        if (fn == null) fn = DECOR.get(name);
        if (fn != null) fn.draw(f, rng, hx, hz, yb, n, sh, W);
    }

    /** bgen.decorate's tier extras: 1 to 3 of decor.BY_TIER[tier]. */
    static void extras(Fr f, PyRandom rng, String tier, int hx, int hz, int yb, int n, int sh, Map<String, Object> W) {
        List<String> pool = BY_TIER.getOrDefault(tier, BY_TIER.get("mortal"));
        int k = Math.min(pool.size(), rng.randint(1, 3));
        for (String name : rng.sample(pool, k)) ALL.get(name).draw(f, rng, hx, hz, yb, n, sh, W);
    }
}
