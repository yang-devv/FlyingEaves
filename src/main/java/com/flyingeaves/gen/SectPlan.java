package com.flyingeaves.gen;

import com.flyingeaves.gen.py.Py;
import com.flyingeaves.gen.py.PyRandom;
import com.flyingeaves.gen.py.UcrtMath;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static com.flyingeaves.gen.Siteplan.kw;
import static com.flyingeaves.gen.Siteplan.odd;
import static com.flyingeaves.gen.Siteplan.room;

/**
 * sect_plan.py: 宗门 - a cultivation sect laid out on a mountain, written as one 单体建造 design (the same rooms, keys
 * and values as the Python writes, in the same order). Building footprints come from building each one with compose
 * (cached by its settings, as sect_plan.footprint: in memory for the process, and the daemon's cache file when there is
 * a run folder).
 */
@SuppressWarnings("unchecked")
public final class SectPlan {

    static final boolean GROUP = true;
    static final List<String> TIERS = List.of("lianqi", "zhuji", "jindan", "yuanying", "huashen");
    static final Map<String, String> TIER_CN = Map.of("lianqi", "练气", "zhuji", "筑基", "jindan", "金丹", "yuanying", "元婴", "huashen", "化神");

    record Fam(String cn, String[] walls, String[] others, String[] themes, String[] lesser, String[] top, String base, int path,
               LinkedHashMap<String, Integer> roofs) {
    }

    static String[] S(String... s) {
        return s;
    }

    static int[] I(int... v) {
        return v;
    }

    static final Fam[] FAMILIES = {
            new Fam("青云", S("palace"), S("palace", "darkwood", "whitewall"), S("teal"), S("grey", "teal"), S("gold", "teal"), "white", 0,
                    Town.rf("xieshan", 4, "yingshan", 2, "juanpeng", 1)),
            new Fam("玄都", S("darkwood"), S("darkwood", "whitewall", "greybrick"), S("black", "grey"), S("black", "grey"), S("black", "teal"), "greybrick", 0,
                    Town.rf("yingshan", 3, "xieshan", 2, "xuanshan", 1)),
            new Fam("剑冢", S("stone", "darkwood"), S("stone", "darkwood", "log"), S("slate", "black"), S("slate", "black"), S("black"), "blackstone", 1,
                    Town.rf("xuanshan", 3, "yingshan", 2, "pyramid", 1)),
            new Fam("丹霞", S("palace", "redbrick"), S("redbrick", "palace", "wattle"), S("red", "grey"), S("grey", "red"), S("red", "gold"), "sandstone", 0,
                    Town.rf("xieshan", 3, "juanpeng", 2, "yingshan", 2)),
            new Fam("瑶池", S("jade", "palace"), S("palace", "whitewall", "jade"), S("jade", "teal"), S("teal", "grey"), S("gold", "jade"), "white", 0,
                    Town.rf("xieshan", 4, "luding", 1, "juanpeng", 1)),
            new Fam("云台", S("palace", "darkwood"), S("wattle", "darkwood", "whitewall"), S("grey", "teal"), S("grey"), S("teal", "gold"), "rubble", 1,
                    Town.rf("xuanshan", 3, "xieshan", 2, "yingshan", 1)),
            new Fam("雪岭", S("stone", "greybrick"), S("greybrick", "stone", "whitewall"), S("slate", "grey"), S("slate", "grey"), S("teal", "black"), "white", 1,
                    Town.rf("yingshan", 3, "xuanshan", 2, "luding", 1)),
            new Fam("竹海", S("darkwood", "wattle"), S("wattle", "log", "darkwood"), S("grey", "wood"), S("thatch", "wood", "grey"), S("teal", "grey"), "rubble", 3,
                    Town.rf("xuanshan", 4, "juanpeng", 2, "xieshan", 1)),
    };

    record Fin(int[] lift, int[] over, double dbl) {
    }

    static final Fin[] TIER_FINISH = {new Fin(I(0, 0, 1), I(1), 0.0), new Fin(I(0, 1, 1), I(1, 1, 2), 0.15), new Fin(I(1, 2), I(1, 2), 0.3),
            new Fin(I(1, 2, 3), I(2), 0.45), new Fin(I(2, 3), I(2, 3), 0.6)};
    static final Set<String> LESSER = Set.of("discipline", "alchemy", "talisman", "dorm", "inner_res", "elder", "refectory", "guard", "forge", "treasury");
    static final String[] SECT_NAMES = {"青云", "太虚", "玄天", "紫霄", "凌霄", "天衍", "归元", "落霞", "玉清", "上清", "无极", "昆仑", "蜀山", "天剑", "丹鼎",
            "万法", "清微", "神霄", "合欢", "天机"};
    static final Set<String> BIG = Set.of("main_hall", "ancestor", "teleport", "teaching", "task", "gate_hall");

    // ------------------------------------------------------------------------------------------ the buildings

    /** A building template: w across, d deep, rooms [(a, b, settings)]. */
    static final class Tpl {
        int w, d;
        List<Object[]> rooms = new ArrayList<>();

        Tpl(int w, int d, Object[]... rooms) {
            this.w = w;
            this.d = d;
            this.rooms.addAll(Arrays.asList(rooms));
        }
    }

    final class Mat {
        String wall, other, theme, lesser, top, base;
        int path, court;
        LinkedHashMap<String, Integer> roofs;
    }

    static Object[] at(int a, int b, Map<String, Object> s) {
        return new Object[]{a, b, s};
    }

    int sz(int lo, int hi) {
        return odd(rng.randint(lo, hi));
    }

    <T> T pick(T[] seq) {
        return seq[(int) rng.randrange(seq.length)];
    }

    int pick(int[] seq) {
        return seq[(int) rng.randrange(seq.length)];
    }

    Map<String, Object> r(Object... a) {
        Map<String, Object> d = kw("storeys", 1, "roof", "xieshan", "walls", mat.wall, "theme", mat.theme, "base", "auto", "podium", 0,
                "podium_style", "plain", "open", "closed", "uses", "main_room", "sh", 0, "overhang", 1);
        d.putAll(kw(a));
        if (d.get("uses") instanceof String) d.put("uses", Siteplan.rep(d.get("uses"), 8));
        return d;
    }

    Tpl tpl(String kind) {
        Fin fin = TIER_FINISH[ti];
        if (LESSER.contains(kind)) {
            Tpl out = tpl0(kind);
            LinkedHashMap<String, Integer> roofs = new LinkedHashMap<>(mat.roofs != null && !mat.roofs.isEmpty() ? mat.roofs : Town.rf("xieshan", 1));
            if (ti >= 3) roofs.put("xieshan", roofs.getOrDefault("xieshan", 0) + 3);
            List<String> keys = new ArrayList<>(roofs.keySet());
            Collections.sort(keys);
            long[] w = new long[keys.size()];
            for (int i = 0; i < w.length; i++) w[i] = roofs.get(keys.get(i));
            for (Object[] q : out.rooms) {
                Map<String, Object> s = (Map<String, Object>) q[2];
                Object rf = s.get("roof");
                if ((rf == null || List.of("xieshan", "yingshan", "xuanshan", "juanpeng").contains(rf)) && !Dyn.truthy(s.get("stack")) && rng.random() < 0.75)
                    s.put("roof", keys.get(rng.choicesIndex(w, 1)[0]));
                if (List.of("xieshan", "wudian", "luding").contains(s.get("roof"))) s.put("lift", rng.choice(fin.lift));
                if (ti >= 3 && rng.random() < 0.35) s.put("theme", mat.theme);
            }
            return out;
        }
        Tpl out = tpl0(kind);
        if (BIG.contains(kind) || kind.equals("library") || kind.equals("bell") || kind.equals("teleport")) {
            for (Object[] q : out.rooms) {
                Map<String, Object> s = (Map<String, Object>) q[2];
                Object rf = s.get("roof");
                if (List.of("xieshan", "wudian", "pyramid").contains(rf)) {
                    s.put("lift", rng.choice(fin.lift));
                    s.put("overhang", rng.choice(fin.over));
                }
                if (out.rooms.size() == 1 && ("xieshan".equals(rf) || "wudian".equals(rf)) && rng.random() < fin.dbl && !kind.equals("gate_hall"))
                    s.put("double_eave", true);
                if (ti >= 2 && !Dyn.truthy(s.get("caihua"))) s.put("caihua", rng.choice("xuanzi", "hexi", "sushi", ""));
            }
        }
        return out;
    }

    Tpl tpl0(String kind) {
        String W = mat.wall, O = mat.other, T = mat.theme, L = mat.lesser, TOP = mat.top, base = mat.base;
        switch (kind) {
            case "main_hall": {
                if (ti == 0) {
                    int w = sz(19, 23);
                    return new Tpl(w, 13, at(0, 0, r("roof", "xieshan", "theme", TOP, "podium", 1, "base", base, "sh", 7, "uses", "council_hall", "brackets", 1)));
                }
                if (ti == 1) {
                    int w = sz(25, 29), d = sz(15, 17);
                    return new Tpl(w, d, at(0, 0, r("roof", "xieshan", "theme", TOP, "podium", 2, "podium_style", "xumizuo", "base", base, "sh", 8,
                            "double_eave", true, "brackets", 2, "uses", "throne_hall")));
                }
                int[][] wd = {{sz(37, 43), sz(21, 23)}, {sz(47, 53), sz(25, 27)}, {sz(57, 63), sz(29, 31)}};
                int w = wd[ti - 2][0], d = wd[ti - 2][1];
                String roof = pick(S("wudian", "wudian", "xieshan"));
                return new Tpl(w, d, at(0, 0, r("roof", roof, "theme", TOP, "podium", 4 + ti / 2, "podium_style", "tiers", "base", base, "sh", 8,
                        "colonnade", true, "brackets", 4, "beasts", 2 + ti / 2, "caihua", "hexi", "pitch", 3, "uses", "throne_hall")),
                        at(4, 4, r("roof", roof, "theme", TOP, "sh", 10 + ti, "brackets", 4, "beasts", 2 + ti / 2, "caihua", "hexi", "pitch", 4,
                                "uses", "council_hall", "stack", true)));
            }
            case "ancestor": {
                if (ti <= 1) {
                    int w = sz(17, 21), d = sz(11, 13);
                    return new Tpl(w, d, at(0, 0, r("theme", TOP, "podium", 2, "podium_style", "xumizuo", "base", base, "sh", 7,
                            "uses", "ancestor_tablets", "brackets", 2)));
                }
                int w = sz(25, 31), d = sz(17, 21);
                return new Tpl(w, d, at(0, 0, r("theme", TOP, "podium", 4, "podium_style", "tiers", "base", base, "sh", 7, "colonnade", true, "brackets", 4,
                        "caihua", "xuanzi", "uses", "ancestor_tablets")),
                        at(4, 4, r("theme", TOP, "sh", 10, "brackets", 4, "uses", "ancestor_tablets", "stack", true)));
            }
            case "teleport": {
                int w = sz(21, 25);
                String w1 = rng.random() < 0.5 ? "jade" : W;
                Map<String, Object> a = r("roof", "pyramid", "walls", w1, "theme", T, "podium", 4, "podium_style", "tiers", "base", base, "sh", 7,
                        "colonnade", true, "brackets", 4, "uses", "teleport_room");
                String w2 = rng.random() < 0.5 ? "jade" : W;
                return new Tpl(w, w, at(0, 0, a), at(4, 4, r("roof", "pyramid", "walls", w2, "theme", T, "sh", 10, "brackets", 4,
                        "uses", "teleport_room", "stack", true)));
            }
            case "treasury": {
                int w = sz(13, 15 + 2 * ti);
                int d = sz(11, w);
                return new Tpl(w, d, at(0, 0, r("roof", "yingshan", "walls", "stone", "theme", L, "podium", 3, "base", base, "sh", 6, "uses", "treasury")));
            }
            case "teaching": {
                int w = sz(23, 25 + 2 * ti), d = sz(15, 17 + ti);
                return new Tpl(w, d, at(0, 0, r("theme", T, "podium", 2, "podium_style", "xumizuo", "base", base, "sh", 7, "brackets", 3, "uses", "lecture_hall")));
            }
            case "task": {
                int w = sz(21, 23 + 2 * ti), d = sz(13, 15);
                return new Tpl(w, d, at(0, 0, r("walls", pick(S(W, O)), "theme", L, "podium", 2, "podium_style", "xumizuo", "base", base, "sh", 6,
                        "brackets", 2, "uses", "task_hall")));
            }
            case "discipline": {
                int w = sz(17, 19), d = sz(13, 15);
                String wl = pick(S("darkwood", "stone", O));
                String th = pick(S("black", L));
                return new Tpl(w, d, at(0, 0, r("roof", "yingshan", "walls", wl, "theme", th, "podium", 1, "sh", 6, "uses", "discipline_hall")));
            }
            case "library": {
                int w = sz(17, 19 + 2 * Math.min(ti, 2));
                return new Tpl(w, w, at(0, 0, r("theme", T, "podium", 2, "podium_style", "xumizuo", "base", base, "storeys", 2, "sh", 6,
                        "uses", new ArrayList<>(List.of("scripture_store", "master_study")))),
                        at(1, 1, r("theme", T, "storeys", 2, "sh", 6, "uses", "scripture_store", "stack", true)));
            }
            case "alchemy":
                return new Tpl(13, 13, at(0, 0, r("walls", pick(S(O, "darkwood")), "theme", T, "storeys", 2, "sh", 6, "uses", "alchemy_room")));
            case "forge": {
                int w = sz(13, 15);
                return new Tpl(w, 13, at(0, 0, r("roof", "yingshan", "walls", "stone", "theme", pick(S("slate", L)), "sh", 7, "uses", "forging_room")));
            }
            case "talisman":
                return new Tpl(13, 11, at(0, 0, r("walls", O, "theme", T, "storeys", 2, "sh", 6, "uses", "talisman_room")));
            case "formation": {
                String wl = pick(S("darkwood", O));
                String th = pick(S("black", T));
                return new Tpl(15, 15, at(0, 0, r("roof", "pyramid", "walls", wl, "theme", th, "podium", 1, "sh", 7, "uses", "formation_room")));
            }
            case "dorm": {
                int w = sz(17, 21);
                String rf = pick(S("yingshan", "xuanshan"));
                String wl = pick(S("wattle", O));
                return new Tpl(w, 13, at(0, 0, r("roof", rf, "walls", wl, "theme", L, "storeys", 2, "sh", 5, "uses", "outer_dorm")));
            }
            case "inner_res":
                return new Tpl(13, 13, at(0, 0, r("walls", O, "theme", T, "podium", 1, "storeys", 2, "sh", 5, "uses", "inner_disciple_room")));
            case "elder": {
                int w = sz(15, 17);
                String wl = pick(S(W, O));
                boolean ver = rng.random() < 0.5;
                return new Tpl(w, 13, at(0, 0, r("walls", wl, "theme", T, "podium", 1, "storeys", 2, "sh", 6, "uses", "elder_quarters", "veranda", ver)));
            }
            case "refectory": {
                int w = sz(19, 23);
                return new Tpl(w, 13, at(0, 0, r("roof", "yingshan", "walls", O, "theme", L, "sh", 6, "uses", "refectory")));
            }
            case "stable": {
                int w = sz(15, 19);
                return new Tpl(w, 13, at(0, 0, r("roof", "xuanshan", "walls", "log", "theme", "wood", "sh", 6, "uses", "beast_stable")));
            }
            case "hut":
                return new Tpl(9, 7, at(0, 0, r("roof", "xuanshan", "walls", "wattle", "theme", "thatch", "sh", 5, "uses", "herb_store")));
            case "guard": {
                int w = sz(9, 11);
                return new Tpl(w, 9, at(0, 0, r("roof", "yingshan", "walls", O, "theme", L, "sh", 5, "uses", "guardroom")));
            }
            case "bell":
                return new Tpl(11, 11, at(0, 0, r("theme", T, "podium", 2, "base", base, "storeys", 2, "sh", 6, "uses", "lookout", "double_eave", ti >= 3)));
            case "gate_hall": {
                int[][] wd = {{15, 9}, {21, 11}, {sz(25, 29), 11}, {sz(29, 33), 13}, {sz(33, 35), 13}};
                int w = wd[ti][0], d = wd[ti][1];
                return new Tpl(w, d, at(0, 0, r("theme", T, "podium", 1 + (ti >= 2 ? 1 : 0), "base", base, "sh", 7, "open", "through", "double_eave", ti >= 1,
                        "brackets", ti >= 2 ? 2 : 0, "uses", "guardroom")));
            }
            case "trial_tower": {
                int w = sz(15, 17 + 2 * Math.min(ti, 1));
                int n = 2 + Math.min(ti, 2);
                List<Object[]> rooms = new ArrayList<>();
                rooms.add(at(0, 0, r("roof", "pyramid", "walls", "stone", "theme", pick(S("black", L)), "podium", 2, "podium_style", "xumizuo", "base", base,
                        "storeys", 2, "sh", 5, "uses", "training_room")));
                for (int k = 1; k < n; k++)
                    rooms.add(at(k, k, r("roof", "pyramid", "walls", "stone", "theme", ((Map<String, Object>) rooms.get(0)[2]).get("theme"),
                            "storeys", k < n - 1 || w - 2 * k >= 13 ? 2 : 1, "sh", 5, "uses", "training_room", "stack", true)));
                Tpl t = new Tpl(w, w);
                t.rooms = rooms;
                return t;
            }
            case "sword_pav": {
                int w = sz(15, 17);
                return new Tpl(w, w, at(0, 0, r("walls", "darkwood", "theme", pick(S("black", T)), "podium", 2, "podium_style", "xumizuo", "base", base,
                        "storeys", 2, "sh", 5, "uses", "sword_gallery")),
                        at(1, 1, r("walls", "darkwood", "theme", "black", "storeys", 2, "sh", 5, "uses", "meditation_room", "stack", true)),
                        at(2, 2, r("walls", "darkwood", "theme", "black", "storeys", 1, "sh", 5, "uses", "training_room", "stack", true)));
            }
            case "alchemy_tower":
                return new Tpl(15, 15, at(0, 0, r("roof", "pyramid", "theme", T, "podium", 2, "podium_style", "xumizuo", "base", base, "storeys", 2, "sh", 5,
                        "uses", "alchemy_room")),
                        at(1, 1, r("roof", "pyramid", "theme", T, "storeys", 2, "sh", 5, "uses", "meditation_room", "stack", true)),
                        at(2, 2, r("roof", "pyramid", "theme", T, "storeys", 2, "sh", 5, "uses", "alchemy_room", "stack", true)));
            case "array_tower":
                return new Tpl(13, 13, at(0, 0, r("roof", "pyramid", "walls", "darkwood", "theme", "black", "podium", 2, "podium_style", "xumizuo", "base", base,
                        "storeys", 2, "sh", 5, "uses", "array_control")),
                        at(1, 1, r("roof", "pyramid", "walls", "darkwood", "theme", "black", "storeys", 2, "sh", 5, "uses", "formation_room", "stack", true)));
            case "pavilion": {
                int w = pick(I(7, 9, 9));
                if (rng.random() < 0.35)
                    return new Tpl(w, w, at(0, 0, r("shape", "round", "round", true, "round_roof", "cone", "theme", T, "open", "open", "podium", 1, "sh", 5,
                            "uses", "lookout")));
                boolean de = rng.random() < 0.3;
                return new Tpl(w, w, at(0, 0, r("roof", "pyramid", "theme", T, "open", "open", "podium", 1, "sh", 5, "uses", "lookout", "double_eave", de)));
            }
            case "pagoda":
                return new Tpl(11, 11, at(0, 0, r("shape", "round", "round", true, "round_roof", pick(S("cone", "helmet")), "theme", T,
                        "podium", 1, "storeys", 3 + Math.min(ti, 2), "sh", 5, "uses", "lookout")));
            default:
                throw new IllegalArgumentException("KeyError: " + kind);
        }
    }

    // ------------------------------------------------------------------------------------------ footprints

    static final Map<String, int[]> FP = new HashMap<>();
    static final Map<String, int[]> FP_FILE = new LinkedHashMap<>();
    static boolean fpLoaded, fpDirty;

    static Path fpFile() {
        return Env.RUN == null ? null : Env.RUN.resolve("cache").resolve("sect_fp_cache.json");
    }

    /** json.dumps(x, sort_keys=True) (ensure_ascii: non-ASCII as \\uXXXX). */
    static String asciiJson(Object v) {
        String s = Json.write(v, true);
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x80) b.append(c);
            else b.append(String.format("\\u%04x", (int) c));
        }
        return b.toString();
    }

    static void fpLoad() {
        if (fpLoaded || !FP.isEmpty()) return;
        fpLoaded = true;
        Path p = fpFile();
        if (p == null || !Files.exists(p)) return;
        try {
            Map<String, Object> m = Json.parseObject(Files.readString(p, StandardCharsets.UTF_8));
            for (Map.Entry<String, Object> e : m.entrySet()) {
                List<Object> l = (List<Object>) e.getValue();
                int[] v = {Dyn.toInt(l.get(0)), Dyn.toInt(l.get(1)), Dyn.toInt(l.get(2)), Dyn.toInt(l.get(3))};
                FP.put(e.getKey(), v);
                FP_FILE.put(e.getKey(), v);
            }
        } catch (Exception ignored) {
        }
    }

    static void fpSave() {
        if (!fpDirty) return;
        Path p = fpFile();
        if (p == null) return;
        try {
            Files.createDirectories(p.getParent());
            Map<String, Object> m = new LinkedHashMap<>();
            for (Map.Entry<String, int[]> e : FP_FILE.entrySet())
                m.put(e.getKey(), new ArrayList<>(List.of(e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[3])));
            String s = Json.write(m, false);
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                b.append(c < 0x80 ? String.valueOf(c) : String.format("\\u%04x", (int) c));
            }
            Files.writeString(p, b.toString(), StandardCharsets.UTF_8);
            fpDirty = false;
        } catch (Exception ignored) {
        }
    }

    static int[] footprint(List<Map<String, Object>> rooms) {
        fpLoad();
        Map<String, Object> f0 = rooms.get(0);
        int fx0 = Dyn.toInt(f0.get("x0")), fz0 = Dyn.toInt(f0.get("z0"));
        List<Object> keyl = new ArrayList<>();
        for (Map<String, Object> r : rooms) {
            Map<String, Object> m = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : r.entrySet())
                if (!List.of("seed", "y", "variant", "roof_variant").contains(e.getKey())) m.put(e.getKey(), e.getValue());
            m.put("x0", Dyn.toInt(r.get("x0")) - fx0);
            m.put("x1", Dyn.toInt(r.get("x1")) - fx0);
            m.put("z0", Dyn.toInt(r.get("z0")) - fz0);
            m.put("z1", Dyn.toInt(r.get("z1")) - fz0);
            keyl.add(m);
        }
        String key = asciiJson(keyl);
        int[] hit = FP.get(key);
        if (hit != null) return hit;
        Canvas cv = new Canvas();
        List<Object> rs = new ArrayList<>();
        for (Map<String, Object> r : rooms) {
            Map<String, Object> rr = Sentence.copy(r);
            rr.put("x0", Dyn.toInt(r.get("x0")) - fx0);
            rr.put("x1", Dyn.toInt(r.get("x1")) - fx0);
            rr.put("z0", Dyn.toInt(r.get("z0")) - fz0);
            rr.put("z1", Dyn.toInt(r.get("z1")) - fz0);
            rr.put("y", 64);
            rs.add(rr);
        }
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("tier", "mortal");
        req.put("seed", 1);
        req.put("rooms", rs);
        Compose.buildCompose(req, cv);
        int X1 = Dyn.toInt(f0.get("x1")) - fx0, Z1 = Dyn.toInt(f0.get("z1")) - fz0;
        int mnx = Integer.MAX_VALUE, mnz = Integer.MAX_VALUE, mxx = Integer.MIN_VALUE, mxz = Integer.MIN_VALUE;
        boolean any = false;
        for (Map.Entry<Long, String> e : cv.blocks.entrySet()) {
            long k = e.getKey();
            if (e.getValue().equals("minecraft:air") || Canvas.ky(k) <= 64) continue;
            int x = Canvas.kx(k), z = Canvas.kz(k);
            any = true;
            mnx = Math.min(mnx, x);
            mnz = Math.min(mnz, z);
            mxx = Math.max(mxx, x);
            mxz = Math.max(mxz, z);
        }
        int[] out = any ? I(Math.max(0, -mnx), Math.max(0, -mnz), Math.max(0, mxx - X1), Math.max(0, mxz - Z1)) : I(0, 0, 0, 0);
        FP.put(key, out);
        FP_FILE.put(key, out);
        fpDirty = true;
        return out;
    }

    final class Bld {
        final String kind;
        final Tpl t;
        final String front;
        final int sx, sz;
        final List<Object[]> rel = new ArrayList<>();
        final List<Integer> seeds;
        final int[] fp;

        Bld(String kind) {
            this(kind, "south", null, null);
        }

        Bld(String kind, String front) {
            this(kind, front, null, null);
        }

        Bld(String kind, String front, Tpl t, List<Integer> seeds) {
            this.kind = kind;
            if (t == null) t = tpl(kind);
            this.t = t;
            this.front = front;
            boolean across = front.equals("south") || front.equals("north");
            sx = across ? t.w : t.d;
            sz = across ? t.d : t.w;
            for (Object[] q : t.rooms) {
                int a = (int) q[0], b = (int) q[1];
                Map<String, Object> s = new LinkedHashMap<>((Map<String, Object>) q[2]);
                s.put("front", front);
                rel.add(new Object[]{across ? a : b, across ? b : a, s});
            }
            if (seeds == null) {
                seeds = new ArrayList<>();
                for (int i = 0; i < rel.size(); i++) seeds.add((int) rng.randrange(1 << 30));
            }
            this.seeds = seeds;
            fp = footprint(rooms(0, 0, 64));
        }

        Bld turned(String front) {
            return new Bld(kind, front, t, seeds);
        }

        List<Map<String, Object>> rooms(int x0, int z0, int y) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (int i = 0; i < rel.size(); i++) {
                Object[] q = rel.get(i);
                int ia = (int) q[0], ib = (int) q[1];
                Map<String, Object> s = (Map<String, Object>) q[2];
                Map<String, Object> r = new LinkedHashMap<>(s);
                r.putAll(kw("x0", x0 + ia, "z0", z0 + ib, "x1", x0 + sx - 1 - ia, "z1", z0 + sz - 1 - ib, "y", y, "seed", seeds.get(i), "variant", 0,
                        "roof_variant", 0, "special", "", "stack", Dyn.truthy(s.get("stack")), "round", Dyn.truthy(s.get("round"))));
                if ("round".equals(s.get("shape"))) r.put("shape", "round");
                out.add(r);
            }
            return out;
        }
    }

    // ------------------------------------------------------------------------------------------ the ground

    interface H2 {
        double h(int u, int v);
    }

    interface Ridge {
        double at(double v);
    }

    static double pw(double x, double y) {
        return UcrtMath.floatPow(x, y);
    }

    void synth(double length, double A) {
        double sv = length / rng.uniform(1.45, 1.75);
        double su = sv * rng.uniform(0.45, 0.6);
        double vtop = -length * 0.42;
        double b0 = rng.choice(I(-1, 1)) * rng.uniform(0.08, 0.2) * length;
        double b1 = rng.uniform(0.6, 1.4) * length;
        double b2 = rng.uniform(0, 2 * Math.PI);
        List<double[]> spurs = new ArrayList<>();
        for (int i = 0; i < 3 + ti * 2; i++) {
            int side = rng.choice(I(-1, 1));
            double v = rng.uniform(vtop - 0.1 * length, vtop + 0.95 * length);
            double u = side * rng.uniform(0.25, 0.7) * su * 2.0;
            double a = rng.uniform(0.06, 0.16) * A;
            double r = rng.uniform(14, 30);
            spurs.add(new double[]{u, v, a, r});
        }
        List<double[]> peaks = new ArrayList<>();
        peaks.add(new double[]{0.0, vtop, A, su, sv});
        if (ti == 4) {
            int n = rng.choice(I(3, 4));
            for (int k = 0; k < n; k++) {
                int side = k % 2 == 0 ? -1 : 1;
                int off = k / 2 + 1;
                double pu = side * rng.uniform(1.1, 1.35) * su * pw(off, 0.7);
                double pv = vtop + rng.uniform(0.05, 0.5) * length;
                double pa = A * rng.uniform(0.7, 0.9);
                peaks.add(new double[]{pu, pv, pa, su * 0.75, sv * 0.55});
            }
        }
        double[][] waves = new double[3][];
        for (int i = 0; i < 3; i++) {
            double ph = rng.uniform(0, 2 * Math.PI), lam = rng.uniform(25, 60), ang = rng.uniform(0, Math.PI), amp = rng.uniform(1.0, 2.5);
            waves[i] = new double[]{ph, lam, ang, amp};
        }
        Ridge ridge = v -> b0 * StrictMath.sin(2 * Math.PI * v / b1 + b2);
        H = (u, v) -> {
            double best = 0.0;
            for (int k = 0; k < peaks.size(); k++) {
                double[] p = peaks.get(k);
                double du = k == 0 ? u - p[0] - ridge.at(v) : u - p[0] - 0;
                double dv = v - p[1];
                double svv = dv > 0 ? p[4] : p[4] * 0.6;
                double d2 = pw(du / p[3], 2) + pw(dv / svv, 2);
                double x = p[2] * UcrtMath.exp(-d2);
                if (x > best) best = x;
            }
            for (double[] s : spurs) {
                double d2 = (pw(u - s[0] - ridge.at(v), 2) + pw(v - s[1], 2)) / (s[3] * s[3]);
                best += s[2] * UcrtMath.exp(-d2) * pw(best / A, 0.5);
            }
            for (double[] w : waves)
                best += w[3] * StrictMath.sin(2 * Math.PI * (u * StrictMath.cos(w[2]) + v * StrictMath.sin(w[2])) / w[1] + w[0]);
            return best;
        };
        this.ridge = ridge;
    }

    static final int[][] ROT = {{1, 0, 0, 1}, {0, -1, 1, 0}, {-1, 0, 0, -1}, {0, 1, -1, 0}};

    /** ROT[k](u, v): 0 (u, v), 1 (-v, u), 2 (-u, -v), 3 (v, -u). */
    static int[] rot(int k, int u, int v) {
        return switch (k) {
            case 0 -> I(u, v);
            case 1 -> I(-v, u);
            case 2 -> I(-u, -v);
            default -> I(v, -u);
        };
    }

    static final Map<String, int[]> DIRV = Map.of("south", I(0, 1), "north", I(0, -1), "east", I(1, 0), "west", I(-1, 0));

    static String vdir(int[] d) {
        if (d[0] == 0) return d[1] > 0 ? "south" : "north";
        return d[0] > 0 ? "east" : "west";
    }

    // ------------------------------------------------------------------------------------------ records

    /** A court or terrace (or a long path piece: id, u0, v0, u1, v1). */
    static final class Terr {
        Object id;
        int u0, u1, v0, v1, y, uc, bs, fs, T;
        Spec spec;
        boolean hasY = true;
    }

    static final class Port {
        Terr court;
        int side, v, u, y;
        boolean used;
        String strip;

        Port(Terr court, int side, int v, int u, int y, String strip) {
            this.court = court;
            this.side = side;
            this.v = v;
            this.u = u;
            this.y = y;
            this.strip = strip;
        }
    }

    static final class Spec {
        String main;
        List<String> sides = new ArrayList<>();
        String style;
        boolean walled, gate;
        Lay lay;

        Spec(String main, List<String> sides, String style) {
            this.main = main;
            if (sides != null) this.sides = sides;
            this.style = style;
        }

        Spec copy() {
            Spec s = new Spec(main, sides, style);
            s.walled = walled;
            s.gate = gate;
            return s;
        }
    }

    static Spec spec(String main, List<String> sides) {
        return new Spec(main, sides, null);
    }

    static final class Lay {
        int W, D, bs, fs, T;
        List<Object[]> items = new ArrayList<>();   // (Bld, u0, v0)
        String main;
        Integer pond;
        int[] ponds, dings, paifang;
        boolean pavilion;
    }

    // ------------------------------------------------------------------------------------------ the plan

    final long seed;
    final String tier;
    final int ti;
    PyRandom rng;
    final Fam fam;
    final Mat mat = new Mat();
    final String name;
    List<Map<String, Object>> rooms = new ArrayList<>();
    final HashMap<Long, Object> occ = new HashMap<>();
    int nid = 0;
    final List<Terr> courts = new ArrayList<>(), terraces = new ArrayList<>();
    final List<Port> ports = new ArrayList<>();
    int nb = 0;
    final Object heights;
    int k = 0;
    final List<String> peakNames = new ArrayList<>();
    final List<Object[]> pendingWalls = new ArrayList<>();
    final List<Terr> pathPieces = new ArrayList<>();
    final List<Object[]> peakMore = new ArrayList<>();
    final List<Object[]> arrivals = new ArrayList<>();
    boolean anchored;
    H2 H;
    Ridge ridge;
    Integer R;
    int vTop;
    Terr gate;
    int[] start;
    int nbridge;
    Set<Long> cov;

    SectPlan(long seed, String tier, Object heights) {
        if (!TIERS.contains(tier)) tier = "lianqi";
        this.seed = seed;
        this.tier = tier;
        ti = TIERS.indexOf(tier);
        rng = PyRandom.ofString("sect/" + tier + "/" + seed);
        fam = FAMILIES[rng.choiceIndex(FAMILIES.length)];
        mat.wall = rng.choice(fam.walls);
        mat.other = rng.choice(fam.others);
        mat.theme = rng.choice(fam.themes);
        mat.lesser = rng.choice(fam.lesser);
        mat.top = rng.choice(fam.top);
        mat.base = fam.base;
        mat.path = fam.path;
        mat.court = rng.choice(I(2, 2, 2, 3));
        mat.roofs = fam.roofs;
        name = rng.choice(SECT_NAMES) + rng.choice("宗", "门", "派", "剑宗", "仙门", "道宫", "阁");
        this.heights = heights;
    }

    static long ck(int u, int v) {
        return Town.ck(u, v);
    }

    // -------------------------------------------------------------------------------------------- ground helpers

    void setupGround(double length, double A) {
        if (heights == null) {
            anchored = false;
            synth(length, A);
            R = null;
            k = 0;
            return;
        }
        anchored = true;
        Map<String, Object> hm = (Map<String, Object>) heights;
        int R = Dyn.toInt(hm.get("R"));
        int n = 2 * R + 1;
        List<Object> raw = (List<Object>) hm.get("h");
        List<Double> good = new ArrayList<>();
        for (Object v : raw) if (v != null && ((Number) v).doubleValue() > -9000) good.add(((Number) v).doubleValue());
        good.sort(null);
        double fill = good.isEmpty() ? 64 : good.get(good.size() / 2);
        double[] vals = new double[raw.size()];
        for (int i = 0; i < vals.length; i++) {
            Object v = raw.get(i);
            vals[i] = v != null && ((Number) v).doubleValue() > -9000 ? ((Number) v).doubleValue() : fill;
        }
        H2 g = (x, z) -> {
            int i = Math.max(-R, Math.min(R, x)) + R;
            int kk = Math.max(-R, Math.min(R, z)) + R;
            return vals[kk * n + i];
        };
        this.R = R;
        Double bestS = null;
        int bk = 0, bus = 0, bvs = 0;
        int lim = (R != 0 ? R : 200) - 8;
        for (int kk = 0; kk < 4; kk++) {
            final int kq = kk;
            H2 Hk = (u, v) -> {
                int[] p = rot(kq, u, v);
                return g.h(p[0], p[1]);
            };
            int stop = Math.max(-lim + 1, lim - (int) Py.intOf(length * 0.6) + 1);
            for (int vs = -lim; vs < stop; vs += 8)
                for (int us = -lim + 30; us < lim - 29; us += 8) {
                    int vf = (int) Math.min(lim, vs + Py.intOf(length));
                    double fall = Hk.h(us, vs) - Hk.h(us, vf);
                    double score = fall + (kk == 0 ? 8 : 0) - 0.02 * Math.abs(us);
                    if (bestS == null || score > bestS) {
                        bestS = score;
                        bk = kk;
                        bus = us;
                        bvs = vs;
                    }
                }
        }
        this.k = bk;
        final int kq = bk, us = bus;
        H = (u, v) -> {
            int[] p = rot(kq, u, v);
            return g.h(p[0], p[1]);
        };
        ridge = v -> us;
        vTop = bvs;
    }

    double hs(int u, int v, int r) {
        double tot = 0;
        int n = 0;
        boolean first = true;
        for (int du : I(-r, 0, r))
            for (int dv : I(-r, 0, r)) {
                double h = H.h(u + du, v + dv);
                tot = first ? 0 + h : tot + h;
                first = false;
                n++;
            }
        return tot / n;
    }

    double hs(int u, int v) {
        return hs(u, v, 3);
    }

    int levelOf(int u0, int v0, int u1, int v1) {
        List<Double> vals = new ArrayList<>();
        int su = Math.max(1, Math.floorDiv(u1 - u0, 6)), sv = Math.max(1, Math.floorDiv(v1 - v0, 6));
        for (int u = u0; u <= u1; u += su) for (int v = v0; v <= v1; v += sv) vals.add(H.h(u, v));
        vals.sort(null);
        return (int) Py.round(vals.get(vals.size() / 2));
    }

    int levelOf(int[] r) {
        return levelOf(r[0], r[1], r[2], r[3]);
    }

    boolean free(int u0, int v0, int u1, int v1, Set<Object> ok) {
        for (int u = u0; u <= u1; u++)
            for (int v = v0; v <= v1; v++) {
                Object o = occ.get(ck(u, v));
                if (o != null && (ok == null || !ok.contains(o))) return false;
            }
        return true;
    }

    boolean free(int u0, int v0, int u1, int v1) {
        return free(u0, v0, u1, v1, null);
    }

    void claim(int u0, int v0, int u1, int v1, Object owner, int margin) {
        for (int u = u0 - margin; u <= u1 + margin; u++) for (int v = v0 - margin; v <= v1 + margin; v++) occ.putIfAbsent(ck(u, v), owner);
    }

    void claim(int u0, int v0, int u1, int v1, Object owner) {
        claim(u0, v0, u1, v1, owner, 1);
    }

    int newId() {
        return ++nid;
    }

    Map<String, Object> ground(String kind, int u0, int v0, int u1, int v1, int y, int variant, String front, Object... kwa) {
        Map<String, Object> kw = kw("walls", mat.other, "theme", mat.lesser, "base", mat.base);
        kw.putAll(kw(kwa));
        Map<String, Object> r = room(Math.min(u0, u1), Math.min(v0, v1), Math.max(u0, u1), Math.max(v0, v1), rng,
                kw("special", kind, "y", y, "variant", variant, "front", front, kw));
        rooms.add(r);
        return r;
    }

    void put(Bld b, int x0, int z0, int y) {
        rooms.addAll(b.rooms(x0, z0, y));
        nb++;
    }

    // -------------------------------------------------------------------------------------------- a run of ground

    void run(List<int[]> cells, List<Integer> ys, int width, List<String> al, Object owner) {
        int half = width / 2;
        List<List<Integer>> pieces = new ArrayList<>();
        List<Integer> cur = new ArrayList<>(List.of(0));
        for (int i = 1; i < cells.size(); i++) {
            int d = ys.get(i) - ys.get(i - 1);
            if (al.get(i).equals(al.get(cur.get(0))) && !al.get(cur.get(0)).equals("sq")
                    && (cur.size() == 1 || d == ys.get(cur.get(1)) - ys.get(cur.get(0))) && cur.size() < 200) {
                cur.add(i);
            } else {
                pieces.add(cur);
                cur = new ArrayList<>(List.of(i));
            }
        }
        pieces.add(cur);
        for (List<Integer> p : pieces) {
            int mnu = Integer.MAX_VALUE, mxu = Integer.MIN_VALUE, mnv = Integer.MAX_VALUE, mxv = Integer.MIN_VALUE;
            for (int i : p) {
                mnu = Math.min(mnu, cells.get(i)[0]);
                mxu = Math.max(mxu, cells.get(i)[0]);
                mnv = Math.min(mnv, cells.get(i)[1]);
                mxv = Math.max(mxv, cells.get(i)[1]);
            }
            int us0 = cells.get(p.get(0))[0], vs0 = cells.get(p.get(0))[1];
            String a = al.get(p.get(0));
            int u0, u1, v0, v1;
            if (a.equals("bu") || a.equals("bv")) {
                int w = half + 1;
                if (a.equals("bu")) {
                    u0 = mnu;
                    u1 = mxu;
                    v0 = vs0 - w;
                    v1 = vs0 + w;
                } else {
                    u0 = us0 - w;
                    u1 = us0 + w;
                    v0 = mnv;
                    v1 = mxv;
                }
                ground("bridge", u0, v0, u1, v1, ys.get(p.get(0)), mat.path, a.equals("bu") ? "east" : "south", "walls", mat.other, "base", mat.base);
                claim(u0, v0, u1, v1, owner, 1);
                nbridge++;
                if (ti >= 2 && rng.random() < 0.45) {
                    int g0 = a.equals("bu") ? u0 : v0, g1 = a.equals("bu") ? u1 : v1;
                    g0 += 2;
                    g1 -= 2;
                    if ((g1 - g0) % 2 != 0) g1 -= 1;
                    int gu0 = a.equals("bu") ? g0 : u0, gu1 = a.equals("bu") ? g1 : u1, gv0 = a.equals("bu") ? v0 : g0, gv1 = a.equals("bu") ? v1 : g1;
                    String rf = rng.choice("xuanshan", "juanpeng", "xieshan");
                    Map<String, Object> rr = room(gu0, gv0, gu1, gv1, rng, "y", ys.get(p.get(0)), "storeys", 1, "roof", rf,
                            "walls", !List.of("stone", "greybrick", "redbrick").contains(mat.other) ? mat.other : "darkwood",
                            "theme", mat.lesser, "open", "through", "podium", 0, "sh", 5, "uses", "lookout", "overhang", 1,
                            "front", a.equals("bu") ? "east" : "south");
                    rooms.add(rr);
                    nb++;
                }
                continue;
            }
            if (a.equals("u")) {
                u0 = mnu;
                u1 = mxu;
                v0 = vs0 - half;
                v1 = vs0 + half;
            } else if (a.equals("v")) {
                u0 = us0 - half;
                u1 = us0 + half;
                v0 = mnv;
                v1 = mxv;
            } else {
                u0 = us0 - half;
                u1 = us0 + half;
                v0 = vs0 - half;
                v1 = vs0 + half;
            }
            int d = p.size() > 1 ? ys.get(p.get(1)) - ys.get(p.get(0)) : 0;
            if (d == 0) {
                ground("street", u0, v0, u1, v1, ys.get(p.get(0)), mat.path, a.equals("v") ? "south" : "east");
            } else {
                int ylow = Integer.MAX_VALUE;
                for (int i : p) ylow = Math.min(ylow, ys.get(i));
                int lowI = ys.get(p.get(0)) == ylow ? p.get(0) : p.get(p.size() - 1);
                int otherI = lowI == p.get(0) ? p.get(p.size() - 1) : p.get(0);
                int[] low = cells.get(lowI), other = cells.get(otherI);
                int[] dv = a.equals("v") ? I(0, low[1] > other[1] ? 1 : -1) : I(low[0] > other[0] ? 1 : -1, 0);
                ground("stairs", u0, v0, u1, v1, ylow, mat.path, vdir(dv));
            }
            claim(u0, v0, u1, v1, owner, 1);
            if (Math.max(u1 - u0, v1 - v0) >= 12 && owner instanceof Integer) {
                Terr t = new Terr();
                t.id = owner;
                t.u0 = u0;
                t.v0 = v0;
                t.u1 = u1;
                t.v1 = v1;
                t.hasY = false;
                pathPieces.add(t);
            }
        }
    }

    static List<String> rep(String a, int n) {
        List<String> l = new ArrayList<>();
        for (int i = 0; i < n; i++) l.add(a);
        return l;
    }

    record Route(List<int[]> rows, List<String> al, List<Integer> ys) {
    }

    Route route(Port pa, Port pb, int width, Set<Object> ok) {
        int sa = pa.side, sb = pb.side;
        int dv = pb.v - pa.v;
        int half = width / 2;
        if (dv != 0 && Math.abs(dv) < 2 * half + 2) return null;
        List<Object[]> cands = new ArrayList<>();
        if (dv == 0) {
            if (sa != -sb || (pb.u - pa.u) * sa < 0) return null;
            List<int[]> rows = new ArrayList<>();
            for (int u = pa.u; sa > 0 ? u < pb.u + sa : u > pb.u + sa; u += sa) rows.add(I(u, pa.v));
            List<Boolean> hold = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) hold.add(false);
            cands.add(new Object[]{rows, new ArrayList<>(rep("u", rows.size())), hold});
        } else {
            int ua = pa.u, ub = pb.u;
            List<Integer> options = new ArrayList<>();
            for (int um = Math.min(ua, ub) - 60; um < Math.max(ua, ub) + 61; um++)
                if ((um - ua) * sa >= half + 1 && (um - ub) * sb >= half + 1) options.add(um);
            options.sort(Comparator.comparingInt(um -> Math.abs(um - ua) + Math.abs(um - ub)));
            for (int um : options.subList(0, Math.min(40, options.size()))) {
                List<int[]> rows = new ArrayList<>();
                List<String> al = new ArrayList<>();
                List<Boolean> hold = new ArrayList<>();
                int end = um + sa * (half + 1);
                for (int u = ua; sa > 0 ? u < end : u > end; u += sa) {
                    rows.add(I(u, pa.v));
                    al.add("u");
                    hold.add((u - um) * sa > -half);
                }
                int s2 = dv > 0 ? 1 : -1;
                int vend = pb.v - s2 * half;
                for (int v = pa.v + s2 * (half + 1); s2 > 0 ? v < vend : v > vend; v += s2) {
                    rows.add(I(um, v));
                    al.add("v");
                    hold.add(false);
                }
                int s3 = -sb;
                int uend = ub + s3;
                for (int u = um - s3 * half; s3 > 0 ? u < uend : u > uend; u += s3) {
                    rows.add(I(u, pb.v));
                    al.add("u");
                    int q = (u - um) * s3;
                    hold.add(-half < q && q <= half);
                }
                cands.add(new Object[]{rows, al, hold});
            }
        }
        Set<Object> okk = new HashSet<>(ok);
        okk.add(pa.court.id);
        okk.add(pb.court.id);
        for (Object[] cd : cands) {
            List<int[]> rows = (List<int[]>) cd[0];
            List<String> al = (List<String>) cd[1];
            List<Boolean> hold = (List<Boolean>) cd[2];
            boolean good = true;
            for (int kk = 0; kk < rows.size(); kk++) {
                int u = rows.get(kk)[0], v = rows.get(kk)[1];
                String a = al.get(kk);
                int cu0 = a.equals("u") ? u : u - half, cu1 = a.equals("u") ? u : u + half;
                int cv0 = a.equals("v") ? v : v - half, cv1 = a.equals("v") ? v : v + half;
                if (!free(cu0, cv0, cu1, cv1, kk == 0 || kk == rows.size() - 1 ? okk : new HashSet<>(ok))) {
                    good = false;
                    break;
                }
            }
            if (!good) continue;
            double[] want = new double[rows.size()];
            for (int i = 0; i < want.length; i++) want[i] = hs(rows.get(i)[0], rows.get(i)[1]);
            List<Integer> ys = profile(want, pa.y, pb.y, 16, 3, hold);
            if (ys == null) continue;
            if (rows.size() > 8) {
                int nfree = 0;
                for (boolean h : hold) if (!h) nfree++;
                List<Integer> ev0 = stairProfile(pa.y, pb.y, nfree, 16);
                if (ev0 != null) {
                    List<Integer> ev = new ArrayList<>();
                    int kk = 0, prev = pa.y;
                    for (boolean h : hold) {
                        if (h) ev.add(prev);
                        else ev.add(ev0.get(kk++));
                        prev = ev.get(ev.size() - 1);
                    }
                    boolean all = true;
                    for (int i = 0; i < ev.size(); i++) {
                        double dd = ev.get(i) - want[i];
                        if (!(-24 <= dd && dd <= 40)) {
                            all = false;
                            break;
                        }
                    }
                    if (all && pieces(ev, al) < pieces(ys, al)) ys = ev;
                }
            }
            al = bridges(rows, al, hold, ys, want, okk, half);
            return new Route(rows, al, ys);
        }
        return null;
    }

    Route route(Port pa, Port pb) {
        return route(pa, pb, 3, Set.of());
    }

    List<String> bridges(List<int[]> rows, List<String> al0, List<Boolean> hold, List<Integer> ys, double[] want, Set<Object> okk, int half) {
        List<String> al = new ArrayList<>(al0);
        int n = rows.size();
        int i = 1;
        while (i < n - 1) {
            String a = al.get(i);
            if (!(a.equals("u") || a.equals("v")) || hold.get(i)) {
                i++;
                continue;
            }
            int b = i;
            while (b + 1 < n - 1 && al.get(b + 1).equals(a) && !hold.get(b + 1)) b++;
            boolean done = false;
            int m = i;
            for (int kk = i; kk <= b; kk++) if (ys.get(kk) < ys.get(m)) m = kk;
            int mx = Integer.MIN_VALUE;
            for (int kk = i; kk <= b; kk++) mx = Math.max(mx, ys.get(kk));
            for (int Y = mx; Y > ys.get(m) + 5; Y--) {
                int p = -1, q = -1;
                for (int kk = i; kk <= m; kk++) if (ys.get(kk) >= Y) p = kk;
                for (int kk = m; kk <= b; kk++) if (ys.get(kk) >= Y) {
                    q = kk;
                    break;
                }
                if (p < 0 || q < 0) continue;
                if (q - p < 10 || q - p > 160 || ys.get(p) != Y || ys.get(q) != Y) continue;
                double mw = Double.POSITIVE_INFINITY;
                int my = Integer.MAX_VALUE;
                for (int kk = p; kk <= q; kk++) {
                    mw = Math.min(mw, want[kk]);
                    my = Math.min(my, ys.get(kk));
                }
                if (Y - mw > 60 || Y - my < 6) continue;
                int w = half + 1;
                boolean ok = true;
                for (int kk = p; kk <= q; kk++) {
                    int u = rows.get(kk)[0], v = rows.get(kk)[1];
                    int cu0 = a.equals("u") ? u : u - w, cu1 = a.equals("u") ? u : u + w;
                    int cv0 = a.equals("v") ? v : v - w, cv1 = a.equals("v") ? v : v + w;
                    if (!free(cu0, cv0, cu1, cv1, okk)) {
                        ok = false;
                        break;
                    }
                }
                if (!ok) continue;
                for (int kk = p; kk <= q; kk++) {
                    ys.set(kk, Y);
                    if (p < kk && kk < q) al.set(kk, "b" + a);
                }
                done = true;
                break;
            }
            i = !done ? b + 1 : i;
        }
        return al;
    }

    static int pieces(List<Integer> ys, List<String> al) {
        int n = 1, cur = 1;
        Integer d0 = null;
        for (int i = 1; i < ys.size(); i++) {
            int d = ys.get(i) - ys.get(i - 1);
            if (al.get(i).equals(al.get(i - 1)) && !al.get(i - 1).equals("sq") && (cur == 1 || d0 != null && d == d0)) {
                d0 = cur == 1 ? (Integer) d : d0;
                cur++;
            } else {
                n++;
                cur = 1;
                d0 = null;
            }
        }
        return n;
    }

    List<Port> portsOf(Terr c) {
        List<Port> out = new ArrayList<>();
        for (int side : I(-1, 1))
            for (String strip : new String[]{"front", "back"}) {
                if (strip.equals("back") && c.bs == 0) continue;
                int pv = strip.equals("front") ? c.v1 - Math.floorDiv(c.fs, 2) : c.v0 + Math.floorDiv(c.bs, 2);
                out.add(new Port(c, side, pv, side < 0 ? c.u0 - 1 : c.u1 + 1, c.y, strip));
            }
        return out;
    }

    static List<Integer> profile(double[] want, int y0, int y1, int flight, int landing, List<Boolean> hold0) {
        int n = want.length;
        List<Boolean> hold = hold0 != null && !hold0.isEmpty() ? hold0 : new ArrayList<>(Collections.nCopies(n, false));
        int[] after = new int[n];
        int k = 1;
        for (int i = n - 1; i >= 0; i--) {
            after[i] = k;
            if (!hold.get(i)) k++;
        }
        if (Math.abs(y1 - y0) > k - 1) return null;
        List<Integer> ys = new ArrayList<>();
        int prev = y0, run = 0, mode = 0;
        for (int i = 0; i < n; i++) {
            double w = want[i];
            int left = after[i];
            if (hold.get(i)) {
                if (Math.abs(y1 - prev) > left) return null;
                ys.add(prev);
                continue;
            }
            int lo = Math.max(prev - 1, y1 - left), hi = Math.min(prev + 1, y1 + left);
            if (lo > hi) return null;
            if (mode == 0 && Math.abs(w - prev) > 9) mode = w > prev ? 1 : -1;
            int t = prev + mode;
            if (mode != 0 && (t - w) * mode >= 0) mode = 0;
            int y = Math.max(lo, Math.min(hi, t));
            if (y != prev) run++;
            else run = 0;
            if (run > flight && lo <= prev && prev <= hi && Math.abs(y1 - prev) <= left - 1) {
                y = prev;
                run = -landing;
            } else if (run < 0 && y != prev && lo <= prev && prev <= hi && Math.abs(y1 - prev) <= left - 1) {
                y = prev;
                run++;
            }
            ys.add(y);
            prev = y;
        }
        if (ys.isEmpty()) throw new IndexOutOfBoundsException("list index out of range");
        if (Math.abs(ys.get(ys.size() - 1) - y1) > 1) return null;
        return ys;
    }

    static List<Integer> profile(double[] want, int y0, int y1) {
        return profile(want, y0, y1, 16, 3, null);
    }

    Object[] stairRows(int ua, int va, int ub, int vb, int width) {
        int half = width / 2;
        List<int[]> rows = new ArrayList<>();
        List<String> al = new ArrayList<>();
        List<Boolean> hold = new ArrayList<>();
        if (Math.abs(ub - ua) <= 2 || vb - va - 1 < width + 2) {
            int u = Math.abs(ub - ua) <= 2 ? ua : Math.floorDiv(ua + ub, 2);
            for (int v = va + 1; v < vb; v++) {
                rows.add(I(u, v));
                al.add("v");
                hold.add(false);
            }
            return new Object[]{rows, al, hold};
        }
        int vm = va + 1 + half + Math.floorDiv(vb - va - 1 - width, 2);
        int sd = ub > ua ? 1 : -1;
        for (int v = va + 1; v < vm + half + 1; v++) {
            rows.add(I(ua, v));
            al.add("v");
            hold.add(v > vm - half);
        }
        int end = ub - sd * half;
        for (int u = ua + sd * (half + 1); sd > 0 ? u < end : u > end; u += sd) {
            rows.add(I(u, vm));
            al.add("u");
            hold.add(false);
        }
        for (int v = vm - half; v < vb; v++) {
            rows.add(I(ub, v));
            al.add("v");
            hold.add(vm - half < v && v <= vm + half);
        }
        return new Object[]{rows, al, hold};
    }

    static int[] rowRect(int[] row, String a, int half) {
        int u = row[0], v = row[1];
        return a.equals("u") ? I(u, v - half, u, v + half) : I(u - half, v, u + half, v);
    }

    boolean stair(int[] us, int va, int vb, int ya, int yb, int width, Object owner) {
        Object[] sr = stairRows(us[0], va, us[1], vb, width);
        List<int[]> rows = (List<int[]>) sr[0];
        List<String> al = (List<String>) sr[1];
        List<Boolean> hold = (List<Boolean>) sr[2];
        if (rows.isEmpty()) return Math.abs(ya - yb) <= 1;
        int nfree = 0;
        for (boolean h : hold) if (!h) nfree++;
        List<Integer> ys0 = stairProfile(ya, yb, nfree, 16);
        if (ys0 == null) {
            double[] want = new double[nfree];
            for (int i = 0; i < nfree; i++) want[i] = ya + (double) ((yb - ya) * (i + 1)) / (nfree + 1);
            ys0 = profile(want, ya, yb);
        }
        if (ys0 == null) return false;
        List<Integer> ys = new ArrayList<>();
        int kk = 0, prev = ya;
        for (boolean h : hold) {
            if (h) ys.add(prev);
            else ys.add(ys0.get(kk++));
            prev = ys.get(ys.size() - 1);
        }
        run(rows, ys, width, al, owner);
        return true;
    }

    static List<Integer> stairProfile(int y0, int y1, int n, int flight) {
        int d = y1 - y0;
        int D = Math.abs(d);
        if (D > n + 1) return null;
        int nf = Math.max(1, -Math.floorDiv(-D, flight));
        while (nf > 1 && D + 3 * (nf - 1) > n + 1) nf--;
        if (D + 3 * (nf - 1) > n + 1 && D > 0) {
            double[] want = new double[n];
            for (int i = 0; i < n; i++) want[i] = y0 + (double) (d * (i + 1)) / (n + 1);
            return profile(want, y0, y1);
        }
        int flats = n + 1 - D;
        int inner = 3 * (nf - 1);
        int spare = flats - inner;
        int per = Math.floorDiv(spare, nf + 1);
        int[] gaps = new int[nf + 1];
        Arrays.fill(gaps, per);
        gaps[0] += spare - per * (nf + 1);
        for (int q = 1; q < nf; q++) gaps[q] += 3;
        List<Integer> steps = new ArrayList<>();
        for (int q = 0; q < nf; q++) {
            int size = Math.floorDiv(D, nf) + (q < Math.floorMod(D, nf) ? 1 : 0);
            for (int i = 0; i < gaps[q]; i++) steps.add(0);
            for (int i = 0; i < size; i++) steps.add(1);
        }
        for (int i = 0; i < gaps[nf]; i++) steps.add(0);
        if (steps.size() != n + 1) throw new AssertionError(steps.size() + " " + n + " " + D);
        int sgn = d >= 0 ? 1 : -1;
        List<Integer> ys = new ArrayList<>();
        int y = y0;
        for (int i = 0; i < steps.size() - 1; i++) {
            y += sgn * steps.get(i);
            ys.add(y);
        }
        return ys;
    }

    // -------------------------------------------------------------------------------------------- a court

    Lay courtLayout(Spec spec) {
        int T = spec.walled ? 4 : 0;
        int bs = 5 + T, fs = 7 + T;
        Lay lay = new Lay();
        List<Object[]> items = lay.items;
        int halfNeeded = 6;
        int v = bs;
        String main = spec.main;
        String style = spec.style != null ? spec.style : "classic";
        List<String> sides = new ArrayList<>(spec.sides);
        if (main != null) {
            Bld m = new Bld(main);
            int w_ = m.fp[0], n_ = m.fp[1], e_ = m.fp[2], s_ = m.fp[3];
            int vm0 = bs + n_ + 1;
            items.add(new Object[]{m, -(m.sx / 2), vm0});
            v = vm0 + m.sz + s_ + 1;
            halfNeeded = Math.max(halfNeeded, m.sx / 2 + Math.max(w_, e_) + 4);
            if (style.equals("three") && sides.size() >= 2) {
                int leftEdge = -(m.sx / 2) - w_, rightEdge = -(m.sx / 2) + m.sx - 1 + e_;
                String k1 = sides.remove(0), k2 = sides.remove(0);
                for (Object[] ks : new Object[][]{{k1, -1}, {k2, 1}}) {
                    int side = (int) ks[1];
                    Bld f = new Bld((String) ks[0]);
                    int fw = f.fp[0], fn = f.fp[1], fe = f.fp[2], fs_ = f.fp[3];
                    int fv = Math.max(bs + fn + 1, vm0 + m.sz - f.sz);
                    int fu;
                    if (side < 0) {
                        fu = leftEdge - 2 - fe - (f.sx - 1);
                        leftEdge = fu - fw;
                    } else {
                        fu = rightEdge + 2 + fw;
                        rightEdge = fu + f.sx - 1 + fe;
                    }
                    items.add(new Object[]{f, fu, fv});
                    v = Math.max(v, fv + f.sz + fs_ + 1);
                }
                halfNeeded = Math.max(halfNeeded, Math.max(-leftEdge + 4, rightEdge + 4));
            }
            if (style.equals("pond")) {
                lay.pond = v + 1;
                v += 9;
            }
        }
        int mid = 6;
        if (style.equals("wide")) mid += rng.randint(6, 14);
        else if (style.equals("narrow")) mid = 5;
        Map<Integer, List<String>> cols = new HashMap<>();
        cols.put(-1, new ArrayList<>());
        cols.put(1, new ArrayList<>());
        int kq = 0;
        for (String kind : sides) {
            cols.get(kq % 2 == 0 ? -1 : 1).add(kind);
            kq++;
        }
        Map<Integer, List<Object[]>> colItems = new HashMap<>();
        colItems.put(-1, new ArrayList<>());
        colItems.put(1, new ArrayList<>());
        int vEnd = v;
        int depthNeed = 0;
        for (int side : I(-1, 1)) {
            int vv = v;
            for (String kind : cols.get(side)) {
                Bld b = new Bld(kind, side < 0 ? "east" : "west");
                int w_ = b.fp[0], n_ = b.fp[1], e_ = b.fp[2], s_ = b.fp[3];
                vv += n_;
                colItems.get(side).add(new Object[]{b, vv});
                vv += b.sz + s_ + 1;
                int back = side < 0 ? w_ : e_, frontr = side < 0 ? e_ : w_;
                depthNeed = Math.max(depthNeed, back + 1 + b.sx + frontr + mid);
            }
            vEnd = Math.max(vEnd, vv);
        }
        int Wi = Math.max(Math.max(halfNeeded, depthNeed), 0);
        int W = Wi + T;
        for (int side : I(-1, 1))
            for (Object[] bv : colItems.get(side)) {
                Bld b = (Bld) bv[0];
                int u0 = side < 0 ? -Wi + b.fp[0] + 1 : Wi - b.fp[2] - 1 - (b.sx - 1);
                items.add(new Object[]{b, u0, bv[1]});
            }
        int open = 0;
        if (sides.isEmpty() && main != null) open = Math.max(open, 6);
        if (style.equals("wide")) {
            open += rng.randint(4, 10);
            if (open >= 9 && Wi >= 16) lay.ponds = I(vEnd + 1, vEnd + open - 1);
        }
        if (style.equals("cauldrons")) {
            int rows = 2;
            lay.dings = I(vEnd + 2, rows);
            vEnd += 2 + rows * 6;
        }
        if (style.equals("pavilion")) {
            Bld pv = new Bld("pavilion");
            int vp = vEnd + 2 + pv.fp[1];
            items.add(new Object[]{pv, -(pv.sx / 2), vp});
            vEnd = vp + pv.sz + pv.fp[3] + 2;
            lay.pavilion = true;
        }
        lay.W = W;
        lay.D = vEnd + open + fs;
        lay.bs = bs;
        lay.fs = fs;
        lay.main = main;
        lay.T = T;
        return lay;
    }

    Terr placeCourt(Spec spec, int uc, int v0, int y, int axisW, boolean ornaments) {
        Lay lay = spec.lay;
        int W = lay.W, D = lay.D;
        int cid = newId();
        int u0 = uc - W, u1 = uc + W, v1 = v0 + D - 1;
        ground("terrace", u0, v0, u1, v1, y, mat.court, "south");
        claim(u0, v0, u1, v1, cid);
        for (Object[] it : lay.items) put((Bld) it[0], uc + (int) it[1], v0 + (int) it[2], y);
        if (lay.dings != null) {
            int a0 = lay.dings[0], rows = lay.dings[1];
            int Wi = W - lay.T;
            for (int kk = 0; kk < rows; kk++) {
                int vc = v0 + a0 + 2 + 6 * kk;
                for (int sd : I(-1, 1))
                    for (int j = 0; j < 3; j++) {
                        int pu = uc + sd * (axisW / 2 + 4 + 6 * j);
                        if (Math.abs(pu - uc) + 2 <= Wi - 3) ground("ornament", pu - 1, vc - 1, pu + 1, vc + 1, y, 0, "south");
                    }
            }
        }
        if (lay.ponds != null) {
            int a0 = lay.ponds[0], a1 = lay.ponds[1];
            int Rr = Math.max(2, Math.min(3, Math.floorDiv(a1 - a0, 2) - 1));
            int vc = v0 + Math.floorDiv(a0 + a1, 2);
            for (int sd : I(-1, 1)) {
                int pu = uc + sd * (axisW / 2 + Rr + 3);
                ground("ornament", pu - Rr, vc - Rr, pu + Rr, vc + Rr, y, 13, "south");
            }
        }
        if (lay.pond != null) {
            int half = W - lay.T - 3;
            ground("river", uc - half, v0 + lay.pond, uc + half, v0 + lay.pond + 6, y, 0, "south");
        }
        if (ornaments && lay.main != null && !lay.pavilion) {
            Object[] it = lay.items.get(0);
            Bld m = (Bld) it[0];
            int mv = (int) it[2];
            int pv0 = v0 + mv + m.sz + m.fp[3] + 1;
            if (lay.pond != null) pv0 = v0 + lay.pond + 8;
            int hw = axisW / 2;
            if (v1 - lay.T - pv0 >= 6) {
                ground("paving", uc - hw, pv0, uc + hw, v1 - lay.T, y, 0, "south");
                if (v1 - pv0 >= 14 && ti >= 1) {
                    int oz = pv0 + Math.floorDiv(v1 - pv0, 2);
                    ground("ornament", uc - 1, oz - 1, uc + 1, oz + 1, y, 0, "south");
                }
            }
        }
        Terr c = new Terr();
        c.id = cid;
        c.u0 = u0;
        c.u1 = u1;
        c.v0 = v0;
        c.v1 = v1;
        c.y = y;
        c.uc = uc;
        c.bs = lay.bs;
        c.fs = lay.fs;
        c.spec = spec;
        c.T = lay.T;
        courts.add(c);
        terraces.add(c);
        ports.addAll(portsOf(c));
        return c;
    }

    // -------------------------------------------------------------------------------------------- the programme

    Object[] programme() {
        List<String> work = new ArrayList<>(List.of("alchemy", "forge", "talisman", "formation"));
        rng.shuffle(work);
        List<Spec> axis;
        List<Object[]> off;
        List<String> spurs;
        if (ti == 0) {
            String a = rng.choice("alchemy", "library"), b = rng.choice("teaching", "talisman");
            axis = new ArrayList<>(List.of(spec("main_hall", new ArrayList<>(List.of(a, b))), spec("task", new ArrayList<>(List.of("dorm", "dorm"))), gateSpec(List.of())));
            off = new ArrayList<>(List.of(new Object[]{"row", List.of("hut", "hut"), "field"}, new Object[]{"row", List.of("stable"), null}));
            spurs = new ArrayList<>(List.of("pavilion"));
        } else if (ti == 1) {
            axis = new ArrayList<>(List.of(spec("main_hall", new ArrayList<>(List.of("treasury", "elder"))),
                    spec("teaching", new ArrayList<>(List.of("library", work.get(0), work.get(1), "talisman"))),
                    spec("task", new ArrayList<>(List.of("dorm", "dorm", "discipline", "refectory"))), gateSpec(List.of())));
            off = new ArrayList<>(List.of(new Object[]{"row", List.of("dorm", "dorm"), null}, new Object[]{"row", List.of("hut", "hut"), "field"},
                    new Object[]{"row", List.of("stable", "stable"), null}, new Object[]{"row", List.of("elder", "elder"), null}));
            spurs = new ArrayList<>(List.of("pavilion", "trial_tower", "pavilion"));
        } else if (ti == 2) {
            axis = new ArrayList<>(List.of(spec("ancestor", new ArrayList<>(List.of("treasury", "treasury"))),
                    spec("main_hall", new ArrayList<>(List.of("bell", "bell", "elder", "elder"))),
                    spec("library", new ArrayList<>(List.of(work.get(0), work.get(1), work.get(2), work.get(3)))),
                    spec("teaching", new ArrayList<>(List.of("inner_res", "inner_res", "inner_res", "inner_res"))),
                    spec("task", new ArrayList<>(List.of("discipline", "refectory", "dorm", "dorm"))), gateSpec(List.of("guard", "guard"))));
            off = new ArrayList<>(List.of(new Object[]{"row", List.of("dorm", "dorm", "dorm"), null}, new Object[]{"row", List.of("dorm", "dorm"), null},
                    new Object[]{"row", List.of("hut", "hut", "hut"), "field"}, new Object[]{"row", List.of("stable", "stable"), null},
                    new Object[]{"row", List.of("elder", "elder", "elder"), null}, new Object[]{"row", List.of("alchemy_tower"), null},
                    new Object[]{"row", List.of("inner_res", "inner_res", "inner_res"), null}));
            spurs = new ArrayList<>(List.of("trial_tower", "sword_pav", "pavilion", "pavilion", "pagoda"));
        } else {
            List<String> teach = new ArrayList<>(rep("inner_res", 4));
            teach.addAll(List.of("talisman", "formation"));
            axis = new ArrayList<>(List.of(spec("ancestor", new ArrayList<>(List.of("treasury", "treasury", "elder", "elder"))),
                    spec("main_hall", new ArrayList<>(List.of("bell", "bell", "teleport", "elder"))),
                    spec("library", new ArrayList<>(List.of(work.get(0), work.get(1), work.get(2), work.get(3)))),
                    spec("teaching", teach),
                    spec("task", new ArrayList<>(List.of("discipline", "refectory", "dorm", "dorm"))), gateSpec(List.of("guard", "guard", "bell", "bell"))));
            off = new ArrayList<>(List.of(new Object[]{"row", List.of("dorm", "dorm", "dorm"), null}, new Object[]{"row", List.of("dorm", "dorm", "dorm"), null},
                    new Object[]{"row", List.of("dorm", "dorm"), null},
                    new Object[]{"row", List.of("hut", "hut", "hut"), "field"}, new Object[]{"row", List.of("stable", "stable", "stable"), null},
                    new Object[]{"row", List.of("elder", "elder", "elder"), null}, new Object[]{"row", List.of("elder", "elder"), null},
                    new Object[]{"row", List.of("alchemy_tower"), null},
                    new Object[]{"row", List.of("inner_res", "inner_res", "inner_res"), null}, new Object[]{"row", List.of("inner_res", "inner_res", "inner_res"), null},
                    new Object[]{"row", List.of("array_tower"), null}, new Object[]{"row", List.of("forge", "forge"), null}));
            spurs = new ArrayList<>(List.of("trial_tower", "sword_pav", "pavilion", "pavilion", "pagoda", "pavilion", "array_tower", "pagoda"));
        }
        rng.shuffle(off);
        off = new ArrayList<>(off.subList(0, (int) Math.max(1, Py.round(off.size() * rng.uniform(0.6, 1.0)))));
        List<Object[]> out = new ArrayList<>();
        for (Object[] o : off) {
            List<String> kinds = (List<String>) o[1];
            Object extra = o[2];
            if ("field".equals(extra) || kinds.size() == 1) out.add(new Object[]{"row", kinds, extra});
            else out.add(new Object[]{new String[]{"row", "chain", "court"}[rng.choicesIndex(new long[]{2, 4, ti >= 1 ? 3 : 0}, 1)[0]], kinds, extra});
        }
        off = out;
        String[] styles = {"classic", "three", "pond", "pavilion", "wide", "narrow"};
        long[][] weights = {{5, 1, 0, 2, 2, 2}, {4, 2, 1, 2, 2, 1}, {3, 3, 2, 2, 2, 1}, {3, 3, 3, 2, 2, 1}, {3, 3, 3, 2, 2, 1}};
        for (Spec sp : axis) if (!sp.gate) sp.style = styles[rng.choicesIndex(weights[ti], 1)[0]];
        rng.shuffle(spurs);
        spurs = new ArrayList<>(spurs.subList(0, (int) Math.max(1, Py.round(spurs.size() * rng.uniform(0.5, 1.0)))));
        return new Object[]{axis, off, spurs};
    }

    static Spec gateSpec(List<String> sides) {
        Spec s = new Spec(null, new ArrayList<>(sides), null);
        s.gate = true;
        return s;
    }

    Lay gateLayout(Spec spec) {
        Bld g = new Bld("gate_hall");
        int T = 4;
        Lay lay = new Lay();
        lay.bs = 5 + T;
        lay.fs = 7 + T;
        lay.main = "gate_hall";
        lay.T = T;
        int w_ = g.fp[0], n_ = g.fp[1], e_ = g.fp[2], s_ = g.fp[3];
        int vg = lay.bs + n_ + 1;
        lay.items.add(new Object[]{g, -(g.sx / 2), vg});
        int v = vg + g.sz + s_ + 2;
        int W = g.sx / 2 + Math.max(w_, e_) + 5;
        List<String> sides = spec.sides;
        for (int i = 0; i < sides.size(); i++) {
            int side = i % 2 == 0 ? -1 : 1;
            Bld b = new Bld(sides.get(i), side < 0 ? "east" : "west");
            int back = side < 0 ? b.fp[0] : b.fp[2], frontr = side < 0 ? b.fp[2] : b.fp[0];
            W = Math.max(W, back + 1 + b.sx + frontr + 6);
        }
        int Wi = W;
        W = Wi + T;
        int vv = v;
        Map<Integer, Integer> rowv = new HashMap<>();
        rowv.put(-1, v);
        rowv.put(1, v);
        for (int i = 0; i < sides.size(); i++) {
            int side = i % 2 == 0 ? -1 : 1;
            Bld b = new Bld(sides.get(i), side < 0 ? "east" : "west");
            int bw = b.fp[0], bn = b.fp[1], be = b.fp[2], bs_ = b.fp[3];
            int u0 = side < 0 ? -Wi + bw + 1 : Wi - be - 1 - (b.sx - 1);
            lay.items.add(new Object[]{b, u0, rowv.get(side) + bn});
            rowv.put(side, rowv.get(side) + bn + b.sz + bs_ + 1);
            vv = Math.max(vv, rowv.get(side));
        }
        int span = Math.min(Wi - 3, Math.max(9, g.sx / 2 + 2));
        lay.paifang = I(vv + 3, span);
        lay.W = W;
        lay.D = vv + 3 + 5 + 7 + T;
        return lay;
    }

    // -------------------------------------------------------------------------------------------- the axis

    /** One planned court: (spec, uc, v0, y, stair us or null). */
    record Step(Spec s, int uc, int v0, int y, int[] us) {
    }

    Object axis(List<Spec> specs, Integer uTop, Integer vTop0, boolean dry, Object tag, List<Step> plan) {
        int aw = I(5, 7, 9, 9, 11)[ti];
        for (Spec s : specs) if (s.lay == null) s.lay = s.gate ? gateLayout(s) : courtLayout(s);
        int vTop = vTop0 == null ? this.vTop : vTop0;
        int uc = uTop == null ? (int) Py.round(ridge.at(vTop)) : uTop;
        int v0 = vTop - Math.floorDiv(specs.get(0).lay.D, 3);
        int[] prev = null;   // (uc, v0, v1, y, W)
        List<Step> out = new ArrayList<>();
        if (plan == null) {
            for (int i = 0; i < specs.size(); i++) {
                Spec s = specs.get(i);
                Lay lay = s.lay;
                int y;
                if (prev == null) y = levelOf(uc - lay.W, v0, uc + lay.W, v0 + lay.D - 1);
                else {
                    int gap = 4;
                    int drift = rng.random() < 0.5 ? rng.choice(I(-1, 1)) * rng.randint(aw + 2, aw + 16) : 0;
                    boolean broke = false;
                    int vb = 0, ucn = 0;
                    y = 0;
                    for (int it = 0; it < 30; it++) {
                        vb = prev[2] + gap + 1;
                        ucn = follow(prev[0], vb + Math.floorDiv(lay.D, 2), lay.W, drift, aw);
                        if (ucn != prev[0]) {
                            gap = Math.max(gap, aw + 4);
                            vb = prev[2] + gap + 1;
                        }
                        y = levelOf(ucn - lay.W, vb, ucn + lay.W, vb + lay.D - 1);
                        int fall = prev[3] - y;
                        int need = Math.abs(fall) - 1 + 3 * Math.max(0, -Math.floorDiv(-Math.abs(fall), 16) - 1);
                        if (ucn != prev[0]) need += 2 * aw;
                        if (need <= gap) {
                            broke = true;
                            break;
                        }
                        if (R != null) {
                            int sumD = 0;
                            for (Spec t : specs.subList(i, specs.size())) sumD += t.lay.D + 5;
                            int cap = R + 30 - prev[2] - 1 - sumD + 5;
                            if (need > Math.max(gap, cap)) {
                                gap = Math.max(gap, cap);
                                vb = prev[2] + gap + 1;
                                int f = Math.abs(fall);
                                while (f > 0 && f - 1 + 3 * Math.max(0, -Math.floorDiv(-f, 16) - 1) + (ucn != prev[0] ? 2 * aw : 0) > gap) f--;
                                y = prev[3] - (fall > 0 ? f : -f);
                                broke = true;
                                break;
                            }
                        }
                        gap = need;
                    }
                    if (!broke) y = prev[3] - (gap + 1 - 3 * Math.max(0, -Math.floorDiv(-gap, 14) - 1));
                    v0 = vb;
                    uc = ucn;
                }
                int W = lay.W, D = lay.D;
                int[] us = prev != null ? I(prev[0], uc) : null;
                out.add(new Step(s, uc, v0, y, us));
                prev = I(uc, v0, v0 + D - 1, y, W);
            }
        } else out = plan;
        if (dry) {
            if (R != null)
                for (Step st : out)
                    if (Math.max(Math.max(Math.abs(st.uc) + st.s.lay.W, Math.abs(st.v0)), Math.abs(st.v0 + st.s.lay.D)) > R + 30) return null;
            for (int kk = 0; kk < out.size(); kk++) {
                Step st = out.get(kk);
                int W = st.s.lay.W, D = st.s.lay.D;
                if (!free(st.uc - W - 2, st.v0 - 2, st.uc + W + 2, st.v0 + D + 1)) return null;
                if (st.us != null) {
                    Step pr = out.get(kk - 1);
                    Object[] sr = stairRows(st.us[0], pr.v0 + pr.s.lay.D - 1, st.us[1], st.v0, aw);
                    List<int[]> rows = (List<int[]>) sr[0];
                    List<String> al = (List<String>) sr[1];
                    for (int q = 0; q < rows.size(); q++) {
                        int[] rr = rowRect(rows.get(q), al.get(q), aw / 2 + 1);
                        if (!free(rr[0], rr[1], rr[2], rr[3])) return null;
                    }
                }
            }
            return out;
        }
        List<Terr> cs = new ArrayList<>();
        Terr pc = null;
        for (int i = 0; i < out.size(); i++) {
            Step st = out.get(i);
            Terr c = !st.s.gate ? placeCourt(st.s, st.uc, st.v0, st.y, aw, true) : placeGate(st.s, st.uc, st.v0, st.y, aw);
            if (pc != null) {
                boolean ok = stair(st.us, pc.v1, c.v0, pc.y, c.y, aw, List.of(tag, i));
                if (!ok) throw new IllegalStateException("RuntimeError: axis stair does not fit");
            }
            cs.add(c);
            pc = c;
        }
        for (int i = 0; i < cs.size(); i++) {
            Terr c = cs.get(i);
            if (c.T != 0) {
                Integer fu = i + 1 < out.size() ? c.uc : null;
                Integer bu = i > 0 ? c.uc : null;
                if (out.get(i).s.gate) pendingWalls.add(new Object[]{c, c.uc, bu, aw, false, 4});
                else pendingWalls.add(new Object[]{c, fu, bu, aw, true, null});
            }
        }
        return cs;
    }

    void courtWalls(Terr c, Integer fu, Integer bu, int aw, boolean gate, Integer sh) {
        int u0 = c.u0 + 1, u1 = c.u1 - 1, v0 = c.v0 + 1, v1 = c.v1 - 1;
        int T = 3;
        Object[] mt = {"walls", mat.wall, "theme", mat.theme, "base", mat.base, "sh", sh != null && sh != 0 ? sh : 5 + Math.min(ti, 2)};
        int gw = gate ? odd(Math.max(9, aw + 2)) : aw + 2;
        List<int[]> gaps = fu != null ? Siteplan.gl(fu - gw / 2, fu + gw / 2) : List.of();
        for (int[] a : Siteplan.wallPieces(u0, u1, gaps)) ground("wall", a[0], v1 - T + 1, Math.min(a[1], u1), v1, c.y, 0, "south", mt);
        if (fu != null && gate) ground("gate", fu - gw / 2, v1 - T + 1, fu + gw / 2, v1, c.y, 0, "south", mt);
        gaps = bu != null ? Siteplan.gl(bu - aw / 2 - 1, bu + aw / 2 + 1) : List.of();
        for (int[] a : Siteplan.wallPieces(u0, u1, gaps)) ground("wall", a[0], v0, Math.min(a[1], u1), v0 + T - 1, c.y, 0, "south", mt);
        for (Object[] sf : new Object[][]{{-1, u0, "east"}, {1, u1 - T + 1, "west"}}) {
            int side = (int) sf[0], ua = (int) sf[1];
            TreeSet<List<Integer>> gs = new TreeSet<>((x, y) -> x.get(0) != y.get(0).intValue() ? Integer.compare(x.get(0), y.get(0)) : Integer.compare(x.get(1), y.get(1)));
            for (Port p : ports) if (p.court == c && p.used && p.side == side) gs.add(List.of(p.v - 2, p.v + 2));
            List<int[]> gl = new ArrayList<>();
            for (List<Integer> g : gs) gl.add(I(g.get(0), g.get(1)));
            for (int[] a : Siteplan.wallPieces(v0 + T, v1 - T, gl)) ground("wall", ua, a[0], ua + T - 1, Math.min(a[1], v1 - T), c.y, 0, (String) sf[2], mt);
        }
    }

    void approach() {
        Terr c = gate;
        int aw = I(5, 7, 9, 9, 11)[ti];
        int L = odd(rng.randint(14, 22) + 6 * ti);
        int u = c.uc;
        if (R != null) L = Math.max(6, Math.min(L, R + 30 - c.v1));
        int foot = ti >= 2 ? 7 : 0;
        List<int[]> cells = new ArrayList<>();
        for (int v = c.v1 + 1; v < c.v1 + 1 + L; v++) cells.add(I(u, v));
        if (!free(u - aw / 2 - 4, c.v1 + 1, u + aw / 2 + 4, c.v1 + L + 1, Set.of(c.id))) {
            start = I(u, c.v1 - 2);
            return;
        }
        double[] want = new double[cells.size()];
        for (int i = 0; i < want.length; i++) want[i] = hs(cells.get(i)[0], cells.get(i)[1]);
        int y1 = (int) Py.round(hs(u, c.v1 + L + 1));
        y1 = Math.max(c.y - (L - foot), Math.min(c.y + (L - foot), y1));
        List<Boolean> hold = new ArrayList<>();
        for (int i = 0; i < L; i++) hold.add(i >= L - foot);
        List<Integer> ys = profile(want, c.y, y1, 16, 3, hold);
        if (ys == null) ys = new ArrayList<>(Collections.nCopies(L, c.y));
        int aid = newId();
        run(cells, ys, aw, rep("v", cells.size()), aid);
        if (foot != 0) {
            int yf = ys.get(ys.size() - 1);
            int span = aw / 2 + 4;
            int vf0 = c.v1 + 1 + L - foot;
            ground("terrace", u - span - 1, vf0, u + span + 1, vf0 + foot - 1, yf, mat.court, "south");
            claim(u - span - 1, vf0, u + span + 1, vf0 + foot - 1, aid, 0);
            ground("paifang", u - span, vf0 + 1, u + span, vf0 + 5, yf, 0, "south", "sh", rng.choice(I(6, 7)) + ti, "walls", mat.wall, "theme", mat.theme);
            nb++;
        }
        start = I(u, c.v1 + L);
    }

    int follow(int uprev, int v, int W, int drift, int aw) {
        Double bestV = null;
        int bestU = 0;
        for (int du = -36; du < 37; du += 3) {
            if (0 < Math.abs(du) && Math.abs(du) <= aw + 1) continue;
            int u = uprev + du;
            double s = Py.sum(new double[]{hs(u + Math.floorDiv(-W, 2), v), hs(u, v), hs(u + Math.floorDiv(W, 2), v)});
            double val = s / 3 - 0.06 * Math.abs(du) - (drift != 0 ? 0.08 * Math.abs(du - drift) : 0);
            if (bestV == null || val > bestV) {
                bestV = val;
                bestU = u;
            }
        }
        return bestU;
    }

    Terr placeGate(Spec spec, int uc, int v0, int y, int aw) {
        Lay lay = spec.lay;
        Terr c = placeCourt(spec, uc, v0, y, aw, false);
        int pv = lay.paifang[0], span = lay.paifang[1];
        ground("paifang", uc - span, v0 + pv - 2, uc + span, v0 + pv + 2, y, 0, "south", "sh", rng.choice(I(7, 8)) + ti, "walls", mat.wall, "theme", mat.theme);
        nb++;
        Object[] it = lay.items.get(0);
        Bld g = (Bld) it[0];
        int gv = (int) it[2];
        int hw = aw / 2;
        if (pv - 3 - (gv + g.sz + g.fp[3]) >= 2) ground("paving", uc - hw, v0 + gv + g.sz + g.fp[3] + 1, uc + hw, v0 + pv - 3, y, 0, "south");
        gate = c;
        return c;
    }

    // -------------------------------------------------------------------------------------------- off the axis

    Object[] pathTo(Port port, java.util.function.IntFunction<int[]> rectFn, java.util.function.Function<int[], Integer> yFn, int width) {
        int side = port.side;
        for (int gap = 4; gap <= 30; gap += 2) {
            int uNear = port.u + side * gap;
            int[] rect = rectFn.apply(uNear);
            int u0 = rect[0], v0 = rect[1], u1 = rect[2], v1 = rect[3];
            if (R != null && Math.max(Math.max(Math.abs(u0), Math.abs(u1)), Math.max(Math.abs(v0), Math.abs(v1))) > R + 40) return null;
            if (!free(u0 - 1, v0 - 1, u1 + 1, v1 + 1)) continue;
            int ua = Math.min(port.u, uNear - side), ub = Math.max(port.u, uNear - side);
            int half = width / 2;
            if (!free(ua, port.v - half, ub, port.v + half, Set.of(port.court.id))) continue;
            int y = yFn.apply(rect);
            List<int[]> cells = new ArrayList<>();
            for (int u = port.u; side > 0 ? u < uNear : u > uNear; u += side) cells.add(I(u, port.v));
            double[] want = new double[cells.size()];
            for (int i = 0; i < want.length; i++) want[i] = hs(cells.get(i)[0], port.v);
            List<Integer> ys = profile(want, port.y, y);
            if (ys == null) {
                int yy = port.y + Math.max(-cells.size(), Math.min(cells.size(), y - port.y));
                if (Math.abs(yy - y) > 6) continue;
                y = yy;
                ys = profile(want, port.y, y);
                if (ys == null) continue;
            }
            return new Object[]{rect, y, cells, ys};
        }
        return null;
    }

    boolean sideRow(List<String> kinds, Object extra) {
        List<Bld> blds = new ArrayList<>();
        for (String kd : kinds) blds.add(new Bld(kd));
        int fs = 6, gapb = 2;
        int width = 0;
        for (Bld b : blds) width += b.fp[0] + b.sx + b.fp[2] + gapb;
        width += 2;
        int back = Integer.MIN_VALUE, dmax = Integer.MIN_VALUE;
        for (Bld b : blds) {
            back = Math.max(back, b.fp[1]);
            dmax = Math.max(dmax, b.sz + b.fp[3]);
        }
        back += 2;
        int depth = back + dmax + fs;
        if ("field".equals(extra)) depth += 10;
        List<Port> ps = new ArrayList<>();
        for (Port p : ports) if (!p.used) ps.add(p);
        rng.shuffle(ps);
        Map<Port, Double> key = new java.util.IdentityHashMap<>();
        for (Port p : ps) key.put(p, (!p.strip.equals("front") ? 1 : 0) * 0.5 + rng.random());
        ps.sort(Comparator.comparingDouble(key::get));
        final int fWidth = width, fDepth = depth;
        for (Port port : ps) {
            int side = port.side, pv = port.v;
            Object[] res = pathTo(port, uNear -> {
                int ua = side > 0 ? uNear : uNear - fWidth + 1;
                int v1 = pv + fs / 2;
                return I(ua, v1 - fDepth + 1, ua + fWidth - 1, v1);
            }, this::levelOf, 3);
            if (res == null) continue;
            int[] rect = (int[]) res[0];
            int y = (int) res[1];
            List<int[]> cells = (List<int[]>) res[2];
            List<Integer> ys = (List<Integer>) res[3];
            int tid = newId();
            int u0 = rect[0], v0 = rect[1], u1 = rect[2], v1 = rect[3];
            ground("terrace", u0, v0, u1, v1, y, "field".equals(extra) ? 1 : mat.court, "south");
            claim(u0, v0, u1, v1, tid);
            run(cells, ys, 3, rep("u", cells.size()), tid);
            int u = u0 + 1;
            for (Bld b : blds) {
                u += b.fp[0];
                put(b, u, v0 + back, y);
                arrivals.add(new Object[]{b, u, v0 + back, cells.isEmpty() ? null : cells.get(cells.size() - 1)});
                u += b.sx + b.fp[2] + gapb;
            }
            port.used = true;
            Terr t = new Terr();
            t.id = tid;
            t.u0 = u0;
            t.u1 = u1;
            t.v0 = v0;
            t.v1 = v1;
            t.y = y;
            t.uc = Math.floorDiv(u0 + u1, 2);
            t.bs = 0;
            t.fs = fs;
            terraces.add(t);
            if ("field".equals(extra)) fields(t, null);
            for (int s2 : I(-1, 1))
                if (s2 == side) ports.add(new Port(t, s2, pv, s2 < 0 ? u0 - 1 : u1 + 1, y, "front"));
            return true;
        }
        return false;
    }

    boolean sideCourt(List<String> kinds) {
        Spec spec = new Spec(kinds.get(0), new ArrayList<>(kinds.subList(1, kinds.size())), rng.choice("classic", "classic", "wide", "pavilion"));
        Lay lay = courtLayout(spec);
        int W = lay.W, D = lay.D;
        List<Port> ps = new ArrayList<>();
        for (Port p : ports) if (!p.used) ps.add(p);
        rng.shuffle(ps);
        for (Port port : ps) {
            int side = port.side;
            int kk = side > 0 ? 1 : 3;
            for (int gap = 4; gap < 34; gap += 3) {
                int uNear = port.u + side * gap;
                int cu = side > 0 ? uNear + (D - 1) : uNear - (D - 1);
                int cv = port.v;
                java.util.function.BiFunction<Integer, Integer, int[]> at = (a, b) -> {
                    int[] xz = rot(kk, a, b);
                    return I(cu + xz[0], cv + xz[1]);
                };
                int[] c0 = at.apply(-W, 0), c1 = at.apply(W, D - 1);
                int[] rect = I(Math.min(c0[0], c1[0]), Math.min(c0[1], c1[1]), Math.max(c0[0], c1[0]), Math.max(c0[1], c1[1]));
                if (R != null && maxAbs(rect) > R + 30) break;
                if (!free(rect[0] - 2, rect[1] - 2, rect[2] + 2, rect[3] + 2)) continue;
                int y = levelOf(rect);
                int tid = newId();
                Terr t = terr(tid, rect, y);
                Port pb = new Port(t, -side, port.v, uNear - side, y, null);
                Route r = route(port, pb);
                if (r == null) continue;
                ground("terrace", rect[0], rect[1], rect[2], rect[3], y, mat.court, "south");
                claim(rect[0], rect[1], rect[2], rect[3], tid);
                terraces.add(t);
                run(r.rows, r.ys, 3, r.al, tid);
                for (Object[] it : lay.items) {
                    Bld b = (Bld) it[0];
                    int a0 = (int) it[1], b0 = (int) it[2];
                    Bld nbld = b.turned(vdir(rot(kk, DIRV.get(b.front)[0], DIRV.get(b.front)[1])));
                    int[] p0 = at.apply(a0, b0), p1 = at.apply(a0 + b.sx - 1, b0 + b.sz - 1);
                    put(nbld, Math.min(p0[0], p1[0]), Math.min(p0[1], p1[1]), y);
                }
                if (lay.main != null && !lay.pavilion) {
                    Object[] it = lay.items.get(0);
                    Bld m = (Bld) it[0];
                    int mv = (int) it[2];
                    int b0 = mv + m.sz + m.fp[3] + 1;
                    if (D - 1 - b0 >= 4) {
                        int[] p0 = at.apply(-1, b0), p1 = at.apply(1, D - 1);
                        ground("paving", Math.min(p0[0], p1[0]), Math.min(p0[1], p1[1]), Math.max(p0[0], p1[0]), Math.max(p0[1], p1[1]), y, 0, "south");
                    }
                }
                port.used = true;
                return true;
            }
        }
        return false;
    }

    static int maxAbs(int[] r) {
        int m = 0;
        for (int x : r) m = Math.max(m, Math.abs(x));
        return m;
    }

    static Terr terr(Object id, int[] rect, int y) {
        Terr t = new Terr();
        t.id = id;
        t.u0 = rect[0];
        t.v0 = rect[1];
        t.u1 = rect[2];
        t.v1 = rect[3];
        t.y = y;
        t.uc = Math.floorDiv(rect[0] + rect[2], 2);
        return t;
    }

    int fields(Terr t, Integer steps0) {
        int steps = steps0 != null && steps0 != 0 ? steps0 : 2 + rng.randint(1, 2 + Math.min(ti, 2));
        int u0 = t.u0 + 2, u1 = t.u1 - 2;
        if (u1 - u0 < 6) return 0;
        int v = t.v1 + 1, y = t.y, n = 0;
        Object prevId = t.id;
        for (int i = 0; i < steps; i++) {
            int d = rng.choice(I(5, 7, 7));
            int[] rect = I(u0, v, u1, v + d - 1);
            Set<Object> ok = new HashSet<>();
            ok.add(t.id);
            ok.add(prevId);
            if (!free(rect[0] - 1, rect[1], rect[2] + 1, rect[3] + 1, ok)) break;
            int yl = Math.max(y - 4, Math.min(y, levelOf(rect)));
            int fid = newId();
            ground("terrace", rect[0], rect[1], rect[2], rect[3], yl, 1, "south");
            ground("ornament", rect[0] + 1, rect[1] + 1, rect[2] - 1, rect[3] - 1, yl, 9, "south");
            claim(rect[0], rect[1], rect[2], rect[3], fid);
            v = v + d;
            y = yl;
            n++;
            prevId = fid;
        }
        return n;
    }

    int groves(int n, List<Terr> spots0) {
        int made = 0, tries = 0;
        List<Terr> spots;
        if (spots0 != null && !spots0.isEmpty()) spots = spots0;
        else {
            spots = new ArrayList<>(terraces);
            spots.addAll(pathPieces);
            spots.addAll(pathPieces);
        }
        while (made < n && tries < n * 80 && !spots.isEmpty()) {
            tries++;
            Terr t = rng.choice(spots);
            int w = odd(rng.randint(11, 21)), d = odd(rng.randint(9, 17));
            String side = rng.choice("w", "e", "n", "s");
            int g = 0;
            int cu = rng.randint(t.u0, t.u1), cv = rng.randint(t.v0, t.v1);
            int[] rect = switch (side) {
                case "w" -> I(t.u0 - g - w, cv - d / 2, t.u0 - g - 1, cv + d / 2);
                case "e" -> I(t.u1 + g + 1, cv - d / 2, t.u1 + g + w, cv + d / 2);
                case "n" -> I(cu - w / 2, t.v0 - g - d, cu + w / 2, t.v0 - g - 1);
                default -> I(cu - w / 2, t.v1 + g + 1, cu + w / 2, t.v1 + g + d);
            };
            if (R != null && maxAbs(rect) > R + 30) continue;
            if (!free(rect[0] - 1, rect[1] - 1, rect[2] + 1, rect[3] + 1, Set.of(t.id))) continue;
            boolean any = false;
            for (Map<String, Object> r : rooms) {
                int x0 = Dyn.toInt(r.get("x0")), x1 = Dyn.toInt(r.get("x1")), z0 = Dyn.toInt(r.get("z0")), z1 = Dyn.toInt(r.get("z1"));
                if (x0 <= rect[2] + 1 && rect[0] - 1 <= x1 && z0 <= rect[3] + 1 && rect[1] - 1 <= z1
                        && !(x0 <= t.u0 && x1 >= t.u1 && z0 <= t.v0 && z1 >= t.v1)) {
                    any = true;
                    break;
                }
            }
            if (any) continue;
            double mx = Double.NEGATIVE_INFINITY, mn = Double.POSITIVE_INFINITY;
            for (int u : I(rect[0], rect[2]))
                for (int v : I(rect[1], rect[3])) {
                    double h = H.h(u, v);
                    mx = Math.max(mx, h);
                    mn = Math.min(mn, h);
                }
            if (mx - mn > 12) continue;
            int gid = newId();
            ground("terrace", rect[0], rect[1], rect[2], rect[3], levelOf(rect), 5, "south");
            claim(rect[0], rect[1], rect[2], rect[3], gid);
            made++;
        }
        return made;
    }

    boolean chain(List<String> kinds) {
        List<Port> ps = new ArrayList<>();
        for (Port p : ports) if (!p.used) ps.add(p);
        rng.shuffle(ps);
        for (Port port : ps.subList(0, Math.min(6, ps.size()))) {
            Port prev = port;
            int side = port.side, placed = 0;
            for (String kind : kinds) {
                Bld b = new Bld(kind);
                int w_ = b.fp[0], n_ = b.fp[1], e_ = b.fp[2], s_ = b.fp[3];
                int tw = odd(w_ + b.sx + e_ + 4);
                int td = n_ + b.sz + s_ + 7;
                boolean done = false;
                for (int it = 0; it < 8; it++) {
                    int gap = rng.randint(3, 9);
                    int pv = prev.v + rng.randint(-12, 12);
                    int uNear = prev.u + side * gap;
                    int u0 = side > 0 ? uNear : uNear - tw + 1;
                    int[] rect = I(u0, pv - (td - 4), u0 + tw - 1, pv + 3);
                    if (R != null && maxAbs(rect) > R + 30) continue;
                    if (!free(rect[0] - 2, rect[1] - 2, rect[2] + 2, rect[3] + 2)) continue;
                    int y = levelOf(rect);
                    int tid = newId();
                    Terr t = terr(tid, rect, y);
                    Port pb = new Port(t, -side, pv, side > 0 ? rect[0] - 1 : rect[2] + 1, y, null);
                    Route r = route(prev, pb);
                    if (r == null) continue;
                    ground("terrace", rect[0], rect[1], rect[2], rect[3], y, rng.choice(I(1, mat.court)), "south");
                    claim(rect[0], rect[1], rect[2], rect[3], tid);
                    terraces.add(t);
                    run(r.rows, r.ys, 3, r.al, tid);
                    put(b, rect[0] + 2 + w_, rect[1] + 1 + n_, y);
                    arrivals.add(new Object[]{b, rect[0] + 2 + w_, rect[1] + 1 + n_, r.rows.get(r.rows.size() - 1)});
                    prev.used = true;
                    prev = new Port(t, side, pv, side > 0 ? rect[2] + 1 : rect[0] - 1, y, "front");
                    placed++;
                    done = true;
                    break;
                }
                if (!done) break;
            }
            if (placed != 0) {
                ports.add(prev);
                return true;
            }
        }
        return false;
    }

    boolean spur(String kind, int reach, List<Port> ports0) {
        Bld b = new Bld(kind);
        Map<Integer, Bld> turn = new LinkedHashMap<>();
        for (int sd : I(-1, 1)) turn.put(sd, b.turned(sd > 0 ? "east" : "west"));
        int m = 2;
        int mtw = Integer.MIN_VALUE, mtd = Integer.MIN_VALUE;
        for (Bld t : turn.values()) {
            mtw = Math.max(mtw, t.fp[0] + t.sx + t.fp[2]);
            mtd = Math.max(mtd, t.fp[1] + t.sz + t.fp[3]);
        }
        int tw = odd(mtw + 2 * m), td = odd(mtd + 2 * m);
        List<Object[]> cands = new ArrayList<>();
        for (Port port : ports0 != null ? ports0 : ports) {
            if (port.used) continue;
            int side = port.side;
            for (int du = tw / 2 + 6; du < reach; du += 5)
                for (int dv = -50; dv < 31; dv += 5) {
                    int u = port.u + side * du, v = port.v + dv;
                    double h = hs(u, v, 4);
                    cands.add(new Object[]{h - port.y - 0.12 * (du + Math.abs(dv)) + rng.random() * 3, u, v, port});
                }
        }
        cands.sort(Comparator.comparingDouble(t -> -(double) t[0]));
        int tried = 0;
        for (Object[] cd : cands) {
            if (tried > 60) break;
            int u = (int) cd[1], v = (int) cd[2];
            Port port = (Port) cd[3];
            int[] rect = I(u - tw / 2, v - td / 2, u + tw / 2, v + td / 2);
            if (!free(rect[0] - 2, rect[1] - 2, rect[2] + 2, rect[3] + 2)) continue;
            tried++;
            int y = levelOf(rect);
            Terr t = terr(newId(), rect, y);
            t.uc = u;
            t.fs = td / 2 * 2 + 1;
            int sideIn = -port.side;
            Port tp = new Port(t, sideIn, v, sideIn < 0 ? rect[0] - 1 : rect[2] + 1, y, null);
            Route r = route(port, tp);
            if (r == null) continue;
            ground("terrace", rect[0], rect[1], rect[2], rect[3], y, rng.choice(I(1, mat.court)), "south");
            claim(rect[0], rect[1], rect[2], rect[3], t.id);
            terraces.add(t);
            run(r.rows, r.ys, 3, r.al, t.id);
            Bld nbld = turn.get(sideIn);
            int x0 = rect[0] + m + nbld.fp[0], z0 = rect[1] + m + nbld.fp[1];
            put(nbld, x0, z0, y);
            arrivals.add(new Object[]{nbld, x0, z0, r.rows.get(r.rows.size() - 1)});
            port.used = true;
            return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------------------------- 化神: more peaks

    static Object[] peak(String name, List<Spec> specs, Map<String, Object> more) {
        return new Object[]{name, specs, more};
    }

    static Spec sp(String main, List<String> sides, String style) {
        return new Spec(main, sides, style);
    }

    static List<Object[]> PEAKS() {
        return List.of(
                peak("剑峰", List.of(sp("sword_pav", List.of("trial_tower", "trial_tower"), "wide"),
                        sp("teaching", List.of("sword_pav", "inner_res", "inner_res", "sword_pav"), null)), kw("spurs", List.of("sword_pav", "pagoda", "trial_tower"))),
                peak("丹峰", List.of(sp("alchemy_tower", List.of("alchemy", "alchemy"), "cauldrons"),
                        sp("task", List.of("alchemy", "alchemy", "inner_res", "inner_res"), null)), kw("fields", 2, "spurs", List.of("alchemy_tower"))),
                peak("器峰", List.of(sp("trial_tower", List.of("forge", "forge"), "cauldrons"),
                        sp("task", List.of("forge", "forge", "dorm", "dorm"), null)), kw("spurs", List.of("pagoda"))),
                peak("阵峰", List.of(sp("array_tower", List.of("formation", "formation"), null),
                        sp("teaching", List.of("talisman", "talisman", "inner_res", "inner_res"), null)), kw("spurs", List.of("array_tower", "array_tower", "pavilion"))),
                peak("灵兽峰", List.of(sp("elder", List.of("stable", "stable"), "wide"),
                        sp("refectory", List.of("stable", "stable", "hut", "hut"), null)), kw("pens", 2, "fields", 1)),
                peak("长老峰", List.of(sp("ancestor", List.of("elder", "elder"), "pond"),
                        sp("library", List.of("elder", "elder", "elder", "elder"), null)), kw("spurs", List.of("pavilion", "pavilion"), "groves", 4)));
    }

    boolean pen(List<Port> ps) {
        for (Port port : new ArrayList<>(ps)) {
            if (port.used) continue;
            int side = port.side;
            for (int gap = 4; gap < 30; gap += 3) {
                int w = odd(rng.randint(17, 23)), d = odd(rng.randint(13, 17));
                int uNear = port.u + side * gap;
                int u0 = side > 0 ? uNear : uNear - w + 1;
                int pv = port.v + rng.randint(-4, 4);
                int[] rect = I(u0, pv - d / 2, u0 + w - 1, pv + d / 2);
                if (R != null && maxAbs(rect) > R + 30) break;
                if (!free(rect[0] - 2, rect[1] - 2, rect[2] + 2, rect[3] + 2)) continue;
                int y = levelOf(rect);
                int tid = newId();
                Terr t = terr(tid, rect, y);
                Port pb = new Port(t, -side, pv, side > 0 ? rect[0] - 1 : rect[2] + 1, y, null);
                Route r = route(port, pb);
                if (r == null) continue;
                ground("terrace", rect[0], rect[1], rect[2], rect[3], y, 1, "south");
                claim(rect[0], rect[1], rect[2], rect[3], tid);
                terraces.add(t);
                run(r.rows, r.ys, 3, r.al, tid);
                Object[] mt = {"walls", "log", "theme", "wood", "base", mat.base, "sh", 4};
                int a0 = rect[0] + 1, a1 = rect[2] - 1, b0 = rect[1] + 1, b1 = rect[3] - 1;
                for (int[] x : Siteplan.wallPieces(a0, a1)) {
                    ground("wall", x[0], b0, Math.min(x[1], a1), b0 + 2, y, 0, "south", mt);
                    ground("wall", x[0], b1 - 2, Math.min(x[1], a1), b1, y, 0, "south", mt);
                }
                int near = side > 0 ? a0 : a1 - 2;
                int far = side > 0 ? a1 - 2 : a0;
                for (int[] z : Siteplan.wallPieces(b0 + 3, b1 - 3, Siteplan.gl(pv - 2, pv + 2)))
                    ground("wall", near, z[0], near + 2, Math.min(z[1], b1 - 3), y, 0, "east", mt);
                for (int[] z : Siteplan.wallPieces(b0 + 3, b1 - 3))
                    ground("wall", far, z[0], far + 2, Math.min(z[1], b1 - 3), y, 0, "east", mt);
                int cu = Math.floorDiv(a0 + a1, 2);
                int[] vars = {7, 3};
                for (int kk = 0; kk < 2; kk++) {
                    int hu = cu + (kk * 2 - 1) * 2;
                    ground("ornament", hu - 1, b0 + 4, hu + 1, b0 + 6, y, vars[kk], "south");
                }
                port.used = true;
                return true;
            }
        }
        return false;
    }

    List<double[]> peaks(int n) {
        int mnu = Integer.MAX_VALUE, mxu = Integer.MIN_VALUE, mnv = Integer.MAX_VALUE, mxv = Integer.MIN_VALUE;
        for (long c : occ.keySet()) {
            int u = Town.cx(c), v = Town.cz(c);
            mnu = Math.min(mnu, u);
            mxu = Math.max(mxu, u);
            mnv = Math.min(mnv, v);
            mxv = Math.max(mxv, v);
        }
        int u0 = mnu - 420, u1 = mxu + 420, v0 = mnv - 60, v1 = mxv + 40;
        if (R != null) {
            u0 = Math.max(u0, -R);
            u1 = Math.min(u1, R);
            v0 = Math.max(v0, -R);
            v1 = Math.min(v1, R);
        }
        LinkedHashMap<Long, Double> grid = new LinkedHashMap<>();
        for (int u = u0; u <= u1; u += 12) for (int v = v0; v <= v1; v += 12) grid.put(ck(u, v), hs(u, v, 5));
        List<Map.Entry<Long, Double>> items = new ArrayList<>(grid.entrySet());
        items.sort(Comparator.comparingDouble(e -> -e.getValue()));
        List<double[]> out = new ArrayList<>();
        for (Map.Entry<Long, Double> e : items) {
            int u = Town.cx(e.getKey()), v = Town.cz(e.getKey());
            double h = e.getValue();
            boolean near = false;
            for (double[] o : out) if (Math.abs(u - o[0]) < 60 && Math.abs(v - o[1]) < 60) near = true;
            if (near) continue;
            boolean higher = false;
            for (int du : I(-12, 0, 12))
                for (int dv : I(-12, 0, 12)) {
                    Double g = grid.get(ck(u + du, v + dv));
                    if ((g == null ? -1e9 : g) > h) higher = true;
                }
            if (higher) continue;
            if (!free(u - 40, v - 40, u + 40, v + 40)) continue;
            out.add(new double[]{u, v, h});
        }
        return out.subList(0, Math.min(out.size(), n + 3));
    }

    int satellites() {
        List<Object[]> progs = new ArrayList<>(PEAKS());
        rng.shuffle(progs);
        int done = 0;
        int want = rng.choice(I(3, 4));
        for (double[] pk : peaks(want)) {
            if (done >= want || progs.isEmpty()) break;
            int u = (int) pk[0], v = (int) pk[1];
            String name = (String) progs.get(0)[0];
            List<Spec> specs0 = (List<Spec>) progs.get(0)[1];
            Map<String, Object> more = (Map<String, Object>) progs.get(0)[2];
            List<Spec> specs = new ArrayList<>();
            for (Spec s : specs0) {
                Spec c = s.copy();
                c.walled = false;
                if (s.style == null) c.style = rng.choice("classic", "three", "wide");
                specs.add(c);
            }
            List<Step> plan = (List<Step>) axis(specs, u, v, true, "axis", null);
            if (plan == null) {
                specs = new ArrayList<>(specs.subList(0, 1));
                plan = (List<Step>) axis(specs, u, v, true, "axis", null);
            }
            if (plan == null) continue;
            Step last = plan.get(plan.size() - 1);
            Spec s = last.s;
            int uc = last.uc, v0 = last.v0, y = last.y;
            int W = s.lay.W, D = s.lay.D;
            List<Port> mains = new ArrayList<>();
            for (Port p : ports) if (!p.used && (uc - p.u) * p.side > 0) mains.add(p);
            mains.sort(Comparator.comparingInt(p -> Math.abs(p.u - uc) + Math.abs(p.v - (v0 + D)) + 2 * Math.abs(p.y - y)));
            Terr fake = new Terr();
            fake.id = -1;
            fake.u0 = uc - W;
            fake.u1 = uc + W;
            fake.v0 = v0;
            fake.v1 = v0 + D - 1;
            fake.y = y;
            fake.bs = s.lay.bs;
            fake.fs = s.lay.fs;
            Object[] found = null;
            for (Port mp : mains.subList(0, Math.min(8, mains.size()))) {
                for (Port spp : portsOf(fake)) {
                    if (spp.side != -mp.side) continue;
                    Route r = route(mp, spp, 3, Set.of(-1));
                    if (r != null) {
                        found = new Object[]{mp, spp, r};
                        break;
                    }
                }
                if (found != null) break;
            }
            if (found == null) continue;
            List<Terr> cs = (List<Terr>) axis(specs, u, v, false, List.of("peak", done), plan);
            Port mp = (Port) found[0], spp = (Port) found[1];
            Route r = (Route) found[2];
            Terr low = cs.get(cs.size() - 1);
            run(r.rows, r.ys, 3, r.al, newId());
            mp.used = true;
            for (Port p : ports) if (p.court == low && p.side == spp.side && p.strip.equals(spp.strip)) p.used = true;
            peakNames.add(name);
            progs.remove(0);
            done++;
            List<Port> mine = new ArrayList<>();
            for (Port p : ports) if (cs.contains(p.court)) mine.add(p);
            for (Object kd : (List<Object>) more.getOrDefault("spurs", List.of())) spur((String) kd, 60, mine);
            for (int i = 0; i < (int) more.getOrDefault("pens", 0); i++) pen(mine);
            for (int i = 0; i < (int) more.getOrDefault("fields", 0); i++) {
                for (int q = cs.size() - 1; q >= 0; q--) if (fields(cs.get(q), null) != 0) break;
            }
            peakMore.add(new Object[]{cs, more.getOrDefault("groves", 0)});
        }
        return done;
    }

    // -------------------------------------------------------------------------------------------- all of it

    Map<String, Object> plan() {
        Object[] pr = programme();
        List<Spec> axis = (List<Spec>) pr[0];
        List<Object[]> off = (List<Object[]>) pr[1];
        List<String> spurs = (List<String>) pr[2];
        if (ti >= 2) for (Spec s : axis) if (!s.gate) s.walled = true;
        for (Spec s : axis) s.lay = s.gate ? gateLayout(s) : courtLayout(s);
        double A = I(70, 100, 150, 185, 210)[ti] * rng.uniform(0.85, 1.15);
        double[] Ds = new double[axis.size()];
        long sumD = 0;
        for (Spec s : axis) sumD += s.lay.D;
        double length = sumD + 1.25 * A + 40;
        setupGround(length, A);
        if (!anchored) vTop = -(int) Py.intOf(length * 0.42);
        rng = PyRandom.ofString("sect/" + tier + "/" + seed + "/plan");
        axis(axis, null, null, false, "axis", null);
        approach();
        if (ti == 4) satellites();
        for (String kind : spurs) spur(kind, 70, null);
        for (Object[] o : off) {
            String how = (String) o[0];
            List<String> kinds = (List<String>) o[1];
            boolean done = false;
            if (how.equals("chain")) done = chain(kinds);
            else if (how.equals("court")) done = sideCourt(kinds);
            if (!done) sideRow(kinds, o[2]);
        }
        for (Object[] w : pendingWalls) courtWalls((Terr) w[0], (Integer) w[1], (Integer) w[2], (int) w[3], (boolean) w[4], (Integer) w[5]);
        groves(3 + 3 * ti + rng.randint(0, 3), null);
        for (Object[] pm : peakMore) {
            int n = (int) pm[1];
            if (n != 0) groves(n, (List<Terr>) pm[0]);
        }
        banks(41, 2);
        fpSave();
        return design();
    }

    Set<Long> covered() {
        if (cov == null) {
            cov = new HashSet<>();
            for (Map<String, Object> r : rooms)
                for (int u = Dyn.toInt(r.get("x0")); u <= Dyn.toInt(r.get("x1")); u++)
                    for (int v = Dyn.toInt(r.get("z0")); v <= Dyn.toInt(r.get("z1")); v++) cov.add(ck(u, v));
        }
        return cov;
    }

    int banks(int seg, int deep) {
        Set<Long> used = new HashSet<>();
        int n = 0;
        for (Terr t : new ArrayList<>(terraces)) {
            if (!courts.contains(t) && (long) (t.u1 - t.u0) * (t.v1 - t.v0) < 900) continue;
            int y = t.y;
            Object[][] edges = {{"v", t.v0, -1, t.u0, t.u1}, {"v", t.v1, 1, t.u0, t.u1}, {"u", t.u0, -1, t.v0, t.v1}, {"u", t.u1, 1, t.v0, t.v1}};
            for (Object[] ed : edges) {
                boolean av = ed[0].equals("v");
                int e = (int) ed[1], out = (int) ed[2], a0 = (int) ed[3], a1 = (int) ed[4];
                for (int s0 = a0; s0 <= a1; s0 += seg) {
                    int s1 = Math.min(a1, s0 + seg - 1);
                    List<int[]> cells = new ArrayList<>();
                    for (int a = s0; a <= s1; a++) for (int kk = 1; kk <= deep; kk++) cells.add(av ? I(a, e + out * kk) : I(e + out * kk, a));
                    List<int[]> ok = new ArrayList<>();
                    Set<Long> okSet = new HashSet<>();
                    for (int[] c : cells) {
                        long cc = ck(c[0], c[1]);
                        if (used.contains(cc)) continue;
                        if (covered().contains(cc)) continue;
                        boolean bad = false;
                        for (int du = -1; du <= 1 && !bad; du++)
                            for (int dv = -1; dv <= 1; dv++) {
                                int qu = c[0] + du, qv = c[1] + dv;
                                if (covered().contains(ck(qu, qv)) && !(t.u0 <= qu && qu <= t.u1 && t.v0 <= qv && qv <= t.v1)) {
                                    bad = true;
                                    break;
                                }
                            }
                        if (bad) continue;
                        ok.add(c);
                        okSet.add(cc);
                    }
                    if (ok.size() < deep * 6) continue;
                    double mh = Double.POSITIVE_INFINITY;
                    for (int[] c : ok) mh = Math.min(mh, H.h(c[0], c[1]));
                    int h = (int) Py.intOf(mh);
                    if (h - y < 3) continue;
                    h = Math.min(h, y + 30);
                    TreeSet<Integer> alongS = new TreeSet<>();
                    for (int[] c : ok) {
                        boolean all = true;
                        for (int kk = 1; kk <= deep; kk++) {
                            int qa = av ? c[0] : e + out * kk, qb = av ? e + out * kk : c[1];
                            if (!okSet.contains(ck(qa, qb))) {
                                all = false;
                                break;
                            }
                        }
                        if (all) alongS.add(av ? c[0] : c[1]);
                    }
                    List<List<Integer>> runs = new ArrayList<>();
                    List<Integer> cur = new ArrayList<>();
                    for (int a : alongS) {
                        if (!cur.isEmpty() && a != cur.get(cur.size() - 1) + 1) {
                            runs.add(cur);
                            cur = new ArrayList<>();
                        }
                        cur.add(a);
                    }
                    if (!cur.isEmpty()) runs.add(cur);
                    for (List<Integer> rn : runs) {
                        if (rn.size() < 3) continue;
                        int u0, u1, v0, v1;
                        int lo = Math.min(e + out, e + out * deep), hi = Math.max(e + out, e + out * deep);
                        if (av) {
                            u0 = rn.get(0);
                            u1 = rn.get(rn.size() - 1);
                            v0 = lo;
                            v1 = hi;
                        } else {
                            u0 = lo;
                            u1 = hi;
                            v0 = rn.get(0);
                            v1 = rn.get(rn.size() - 1);
                        }
                        ground("terrace", u0, v0, u1, v1, h, 4, "south");
                        for (int u = u0; u <= u1; u++) for (int v = v0; v <= v1; v++) used.add(ck(u, v));
                        n++;
                    }
                }
            }
        }
        return n;
    }

    Map<String, Object> design() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r0 : rooms) {
            Map<String, Object> r = new LinkedHashMap<>(r0);
            int[] a = rot(k, Dyn.toInt(r.get("x0")), Dyn.toInt(r.get("z0")));
            int[] b = rot(k, Dyn.toInt(r.get("x1")), Dyn.toInt(r.get("z1")));
            r.put("x0", Math.min(a[0], b[0]));
            r.put("x1", Math.max(a[0], b[0]));
            r.put("z0", Math.min(a[1], b[1]));
            r.put("z1", Math.max(a[1], b[1]));
            int[] dv = DIRV.get(r.containsKey("front") ? (String) r.get("front") : "south");
            r.put("front", vdir(rot(k, dv[0], dv[1])));
            r.keySet().removeIf(kk -> kk.startsWith("_"));
            out.add(r);
        }
        if (GROUP) out = Town.groupGround(out, 250);
        int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z0 = Integer.MAX_VALUE, z1 = Integer.MIN_VALUE;
        for (Map<String, Object> r : out) {
            x0 = Math.min(x0, Dyn.toInt(r.get("x0")));
            x1 = Math.max(x1, Dyn.toInt(r.get("x1")));
            z0 = Math.min(z0, Dyn.toInt(r.get("z0")));
            z1 = Math.max(z1, Dyn.toInt(r.get("z1")));
        }
        Terr g = gate;
        int[] gxz = rot(k, Math.floorDiv(g.u0 + g.u1, 2), g.v1);
        int[] st0 = start != null ? start : I(Math.floorDiv(g.u0 + g.u1, 2), g.v1 - 2);
        int[] st = rot(k, st0[0], st0[1]);
        return kw("name", "宗门·" + name + "（" + TIER_CN.get(tier) + "·" + fam.cn + "）", "kind", "sect", "jin", 0, "seed", Siteplan.num(seed), "tier", tier,
                "look", 0, "size", new ArrayList<>(List.of(x1 - x0 + 1, z1 - z0 + 1)), "rooms", out, "terrain", true, "anchored", anchored,
                "origin", anchored ? new ArrayList<>(List.of(0, 0)) : new ArrayList<>(List.of(gxz[0], gxz[1])), "y_ref", g.y, "buildings", nb,
                "courts", courts.size(), "start", new ArrayList<>(List.of(st[0], st[1])), "bridges", nbridge);
    }

    public static Map<String, Object> sect(long seed, String tier, Object heights) {
        return new SectPlan(seed, tier == null ? "jindan" : tier, heights).plan();
    }
}
