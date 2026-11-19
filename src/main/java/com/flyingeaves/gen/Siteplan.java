package com.flyingeaves.gen;

import com.flyingeaves.gen.py.PyRandom;
import com.flyingeaves.gen.py.UcrtMath;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * siteplan.py: 总平面图 - a compound of several buildings laid out by rule, written as one 单体建造 design (rooms as
 * dicts, exactly as the Python writes them: same keys in the same order, same values). site (住宅 / 宫院), city (宫城),
 * city_real (紫禁城 1:1), site_free + randomize (随机一院 全参数随机), and the reach / steps / crowded helpers town.py
 * and sect_plan.py share.
 */
@SuppressWarnings("unchecked")
public final class Siteplan {
    private Siteplan() {
    }

    public static final int T = 3;
    public static final int WALK = 2;
    public static final int TW = 9;
    public static final int WH = 10;

    // ------------------------------------------------------------------------------------------ dict helpers

    /** Keyword arguments in Python order: key, value pairs; a Map among them is spread in (**d). */
    public static Map<String, Object> kw(Object... a) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < a.length; i++) {
            if (a[i] instanceof Map<?, ?> d) {
                m.putAll((Map<String, Object>) d);
            } else {
                m.put((String) a[i], a[i + 1]);
                i++;
            }
        }
        return m;
    }

    /** dict(d, k=v, ...) */
    public static Map<String, Object> with(Map<String, Object> d, Object... a) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>(d);
        m.putAll(kw(a));
        return m;
    }

    public static int odd(int n) {
        return n | 1;
    }

    static int gi(Map<String, Object> r, String k) {
        return Dyn.toInt(r.get(k));
    }

    /** int(r.get(k, 0) or 0) */
    static int pod(Map<String, Object> r, String k) {
        return Dyn.iOr0(r, k);
    }

    static boolean t(Map<String, Object> r, String k) {
        return Dyn.truthy(r.get(k));
    }

    static String sget(Map<String, Object> r, String k, String dflt) {
        return r.containsKey(k) ? (String) r.get(k) : dflt;
    }

    public static List<Object> rep(Object v, int n) {
        List<Object> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) l.add(v);
        return l;
    }

    /** room(x0, z0, x1, z1, rng, **kw) */
    public static Map<String, Object> room(int x0, int z0, int x1, int z1, PyRandom rng, Map<String, Object> kw) {
        LinkedHashMap<String, Object> d = new LinkedHashMap<>();
        d.put("x0", x0);
        d.put("z0", z0);
        d.put("x1", x1);
        d.put("z1", z1);
        d.put("y", 0);
        d.put("storeys", 1);
        d.put("roof", "yingshan");
        d.put("walls", "greybrick");
        d.put("theme", "grey");
        d.put("bay_style", "auto");
        d.put("base", "auto");
        d.put("front", "south");
        d.put("sh", 0);
        d.put("podium", 0);
        d.put("podium_style", "plain");
        d.put("open", "closed");
        d.put("uses", "main_room");
        d.put("special", "");
        d.put("round", false);
        d.put("seed", (int) rng.randrange(1 << 30));
        d.put("variant", 0);
        d.put("roof_variant", 0);
        d.put("stack", false);
        d.putAll(kw);
        if (d.get("uses") instanceof String) d.put("uses", rep(d.get("uses"), 8));
        if (Dyn.truthy(d.get("special"))) d.put("shape", d.get("special"));
        return d;
    }

    public static Map<String, Object> room(int x0, int z0, int x1, int z1, PyRandom rng, Object... a) {
        return room(x0, z0, x1, z1, rng, kw(a));
    }

    static int[] r4(Map<String, Object> r) {
        return new int[]{gi(r, "x0"), gi(r, "z0"), gi(r, "x1"), gi(r, "z1")};
    }

    static final Comparator<int[]> LEX = (a, b) -> {
        for (int i = 0; i < Math.min(a.length, b.length); i++) if (a[i] != b[i]) return Integer.compare(a[i], b[i]);
        return Integer.compare(a.length, b.length);
    };

    static List<int[]> sorted(List<int[]> l) {
        List<int[]> o = new ArrayList<>(l);
        o.sort(LEX);
        return o;
    }

    // ------------------------------------------------------------------------------------------ wall pieces

    public static List<int[]> runs(int a0, int a1, List<int[]> gaps, int most) {
        List<int[]> out = new ArrayList<>();
        int a = a0;
        List<int[]> gs = sorted(gaps);
        gs.add(new int[]{a1 + 1, a1 + 1});
        for (int[] g : gs) {
            if (g[0] > a) out.addAll(split(a, g[0] - 1, most));
            a = g[1] + 1;
        }
        return out;
    }

    static List<int[]> runs(int a0, int a1, List<int[]> gaps) {
        return runs(a0, a1, gaps, 125);
    }

    public static List<int[]> split(int a0, int a1, int most) {
        int n = a1 - a0 + 1;
        if (n <= most) return new ArrayList<>(List.of(new int[]{a0, a1}));
        int k = -Math.floorDiv(-n, most);
        if (n % 2 != 0 && k % 2 == 0) k += 1;
        int p = odd(Math.floorDiv(n, k));
        if (p > most) p -= 2;
        List<int[]> out = new ArrayList<>();
        int a = a0;
        for (int i = 0; i < k; i++) {
            int c = i < k - 1 ? p : n - p * (k - 1);
            out.add(new int[]{a, a + c - 1});
            a += c;
        }
        return out;
    }

    public static List<int[]> wallPieces(int a0, int a1, List<int[]> gaps) {
        List<int[]> out = new ArrayList<>();
        for (int[] p : gapPieces(a0, a1, gaps)) out.addAll(split(p[0], p[1] + ((p[1] - p[0]) % 2 == 0 ? 0 : 1), 125));
        return out;
    }

    static List<int[]> wallPieces(int a0, int a1) {
        return wallPieces(a0, a1, List.of());
    }

    public static List<int[]> gapPieces(int a0, int a1, List<int[]> gaps) {
        List<int[]> out = new ArrayList<>();
        int a = a0;
        for (int[] g : sorted(gaps)) {
            if (g[0] > a) out.add(new int[]{a, g[0] - 1});
            a = g[1] + 1;
        }
        if (a <= a1) out.add(new int[]{a, a1});
        return out;
    }

    static List<int[]> gl(int... ab) {
        List<int[]> l = new ArrayList<>();
        for (int i = 0; i < ab.length; i += 2) l.add(new int[]{ab[i], ab[i + 1]});
        return l;
    }

    public static List<int[]> oddPieces(int a0, int a1, int most) {
        int n = a1 - a0 + 1;
        int k = -Math.floorDiv(-n, most);
        if (Math.floorMod(k, 2) != Math.floorMod(n, 2)) k += 1;
        int q = Math.floorDiv(n, k);
        int s = q - (1 - Math.floorMod(q, 2));
        int[] sizes = new int[k];
        Arrays.fill(sizes, s);
        int i = 0;
        while (Arrays.stream(sizes).sum() < n) {
            sizes[i % k] += 2;
            i++;
        }
        List<int[]> out = new ArrayList<>();
        int a = a0;
        for (int c : sizes) {
            out.add(new int[]{a, a + c - 1});
            a += c;
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------ site

    public static Map<String, Object> site(String kind, int jin, long seed) {
        return site(kind, jin, seed, true);
    }

    public static Map<String, Object> site(String kind, int jin, long seed, boolean river) {
        PyRandom rng = PyRandom.ofString("site/" + kind + "/" + jin + "/" + seed);
        boolean palace = kind.equals("palace");
        int CW = palace ? rng.choice(new int[]{37, 41, 45, 49, 53}) : rng.choice(new int[]{25, 29, 33, 37, 41});
        int c = CW / 2;
        Map<String, Object> mat;
        if (palace) mat = kw("walls", rng.choice("palace", "palace", "jade", "yellow"), "theme", rng.choice("gold", "gold", "jade", "teal"));
        else mat = kw("walls", rng.choice("greybrick", "greybrick", "whitewall", "darkwood", "redbrick", "mud"),
                "theme", rng.choice("grey", "grey", "black", "slate", "wood", "thatch"));
        List<Map<String, Object>> rooms = new ArrayList<>();
        int z = T + 2;
        int courts = palace ? jin : 1;
        if (!palace && jin >= 3) {
            rooms.add(room(T, z, CW - T - 1, z + 6, rng, "front", "south", "storeys", rng.choice(new int[]{1, 1, 2}), "uses", "storeroom", mat));
            z += 7;
            rooms.add(room(T, z, CW - T - 1, z + 2, rng, "special", "paving", mat));
            z += 3;
        }
        List<Map<String, Object>> wings = null;
        for (int k = 0; k < courts; k++) {
            boolean frontCourt = k == courts - 1;
            int MD = palace ? rng.choice(new int[]{9, 11, 13}) : rng.choice(new int[]{7, 9, 11});
            int MW = odd((int) com.flyingeaves.gen.py.Py.round(CW * rng.uniform(0.38, 0.56)));
            int x0 = c - MW / 2, x1 = c + MW / 2;
            Map<String, Object> hall;
            if (palace) {
                hall = kw("roof", rng.choice("xieshan", "xieshan", "wudian", "luding", MW < 20 ? "pyramid" : "xieshan"),
                        "double_eave", rng.random() < 0.45, "podium", rng.choice(new int[]{2, 3, 4, 5}),
                        "podium_style", rng.choice("xumizuo", "xumizuo", "tiers"),
                        "base", "white", "caihua", rng.choice("hexi", "xuanzi", "sushi"), "beasts", rng.choice(new int[]{2, 3, 4}),
                        "brackets", rng.choice(new int[]{0, 2, 3}),
                        "pitch", rng.choice(new int[]{2, 3, 3, 4}), "colonnade", rng.random() < 0.25, "veranda", rng.random() < 0.3,
                        "uses", frontCourt ? "council_hall" : "main_room");
            } else {
                hall = kw("roof", rng.choice("yingshan", "yingshan", "xuanshan", "juanpeng", "xieshan"), "podium", rng.choice(new int[]{0, 1, 1, 2}),
                        "podium_style", "plain", "storeys", rng.choice(new int[]{1, 1, 1, 2}), "veranda", rng.random() < 0.4,
                        "horsehead", mat.get("walls").equals("whitewall") && rng.random() < 0.6, "uses", "main_room");
                if (t(hall, "horsehead")) hall.put("roof", "yingshan");
            }
            int kHall = rooms.size();
            for (Map<String, Object> p : rooms) {
                if (t(p, "special")) continue;
                if (hit(new int[]{x0 - reach(hall), 0, x1 + reach(hall), 0}, new int[]{gi(p, "x0") - reach(p), 0, gi(p, "x1") + reach(p), 0})) {
                    z = Math.max(z, gi(p, "z1") + 1 + Math.max(reach(p) + reach(hall) + 1,
                            p.get("front").equals("south") ? steps(p) + WALK + plat(hall) + 1 : 0));
                }
            }
            rooms.add(room(x0, z, x1, z + MD - 1, rng, "front", "south", "_role", "main", with(mat, hall)));
            int roomW = x0 - T;
            if (roomW >= 6 && rng.random() < 0.75) {
                int ew = Math.min(7, roomW - 1);
                ew -= ew % 2 != 0 ? 0 : 1;
                Map<String, Object> side = kw("roof", !palace ? "yingshan" : "xuanshan", "uses", !palace ? "storeroom" : "study",
                        "caihua", palace ? "xuanzi" : "");
                rooms.add(room(x0 - ew, z + 1, x0 - 1, z + MD - 2, rng, "front", "south", "_ear", new int[]{-1, T}, with(mat, side)));
                rooms.add(room(x1 + 1, z + 1, x1 + ew, z + MD - 2, rng, "front", "south", "_ear", new int[]{1, CW - T - 1}, with(mat, side)));
                partEars(rooms);
            }
            int z0 = z + MD;
            int CD = odd(rng.randint(11, 23));
            int SW = palace ? rng.choice(new int[]{7, 9, 11}) : rng.choice(new int[]{5, 7, 9});
            Map<String, Object> wing = new LinkedHashMap<>();
            wing.put("roof", palace ? rng.choice("xuanshan", "yingshan", "xieshan") : rng.choice("yingshan", "yingshan", "xuanshan", "juanpeng"));
            wing.put("uses", palace ? "study" : "bedroom");
            wing.put("caihua", palace ? rng.choice("xuanzi", "sushi", "") : "");
            wing.put("podium", palace ? rng.choice(new int[]{0, 1, 2}) : rng.choice(new int[]{0, 0, 1}));
            wing.put("storeys", rng.choice(new int[]{1, 1, 1, 2}));
            wing.put("veranda", rng.random() < 0.35);
            java.util.function.IntSupplier need = () -> Math.max(2 * steps(wing) + WALK, 2 * reach(wing) + 1);
            while (SW > 5 && CW - 2 * (T + SW) < need.getAsInt()) SW -= 2;
            if (CW - 2 * (T + SW) < need.getAsInt()) wing.put("veranda", false);
            while (CW - 2 * (T + SW) < need.getAsInt() && Dyn.truthy(wing.get("podium"))) wing.put("podium", gi(wing, "podium") - 1);
            wings = new ArrayList<>(List.of(
                    room(T, z0 + 2, T + SW - 1, z0 + CD - 3, rng, "front", "east", with(mat, wing)),
                    room(CW - T - SW, z0 + 2, CW - T - 1, z0 + CD - 3, rng, "front", "west", with(mat, wing))));
            List<int[]> ahead = new ArrayList<>();
            for (Map<String, Object> r : rooms.subList(kHall, rooms.size())) {
                if (t(r, "special")) continue;
                ahead.add(zone(r));
                ahead.addAll(walkRects(r));
            }
            int m = Math.max(reach(wings.get(0)), steps(wings.get(0)) + WALK);
            int start = z0 + 2;
            for (Map<String, Object> w : wings)
                for (int[] b : ahead)
                    if (b[0] <= gi(w, "x1") + m && gi(w, "x0") - m <= b[2]) start = Math.max(start, b[3] + m + 2);
            start += Math.floorMod(start - z0, 2);
            CD += start - (z0 + 2);
            Map<String, Object> hr = rooms.get(kHall);
            CD = Math.max(CD, (gi(hr, "z1") + steps(hr) + WALK + 2 - z0) | 1);
            for (Map<String, Object> w : wings) {
                w.put("z0", start);
                w.put("z1", z0 + CD - 3);
            }
            rooms.addAll(wings);
            rooms.add(room(T + SW, z0, CW - T - SW - 1, z0 + CD - 1, rng, "special", "paving", mat));
            z = z0 + CD;
            if (!frontCourt || (!palace && jin >= 2)) {
                int gw = 9;
                String gate = palace ? "glazedgate" : "chuihua";
                rooms.add(room(c - gw / 2, z - (palace ? 0 : 1), c + gw / 2, z + (palace ? 2 : 3), rng, "special", gate,
                        "front", "south", "sh", 6, mat));
                for (int[] p : runs(T, CW - T - 1, gl(c - gw / 2, c + gw / 2)))
                    rooms.add(room(p[0], z, p[1], z + 2, rng, "special", "wall", "front", "south", "sh", !palace ? 5 : 7, mat));
                z += T;
            }
        }
        int OD = 0;
        if (palace && river) {
            rooms.add(room(T, z, CW - T - 1, z + 6, rng, "special", "river", mat));
            z += 7;
            OD = 5;
        } else if (palace) {
            OD = 7;
        } else if (jin >= 2) {
            OD = rng.choice(new int[]{7, 9});
        }
        int gw = palace ? 11 : 7;
        Map<String, Object> gate = kw("front", "south", "roof", "xieshan", "open", "through", "podium", 2, "podium_style", "xumizuo",
                "base", "white", "caihua", "xuanzi", "uses", "lookout", mat);
        if (palace) {
            List<Integer> ends = new ArrayList<>();
            if (river) ends.add(z - 1);
            for (Map<String, Object> r : rooms) if (!t(r, "special")) for (int[] b : walkRects(r)) ends.add(b[3]);
            int[] fl = walkRects(with(gate, "x0", c - gw / 2, "z0", 0, "x1", c + gw / 2, "z1", 6)).get(0);
            for (Map<String, Object> r : rooms) {
                if (t(r, "special")) continue;
                int[] f = foot(r);
                if (f[0] <= fl[2] && fl[0] <= f[2]) ends.add(f[3]);
            }
            int best = OD;
            for (int e : ends) best = Math.max(best, e + steps(gate) + 3 - z + WALK);
            for (Map<String, Object> w : wings) best = Math.max(best, gi(w, "z1") + reach(w) + reach(gate) + 3 - z);
            OD = best;
        }
        int total = z + OD + T;
        if (total % 2 == 0) OD += 1;
        if (OD != 0) {
            rooms.add(room(T, z, CW - T - 1, z + OD - 1, rng, "special", "paving", mat));
            z += OD;
        }
        int sz = z + T + 4;
        if (palace) {
            rooms.add(room(c - gw / 2, z - 2, c + gw / 2, z + 4, rng, gate));
            Map<String, Object> last = rooms.get(rooms.size() - 1);
            sz = Math.max(sz, gi(last, "z1") + steps(last) + WALK + 2);
        } else {
            rooms.add(room(c - gw / 2, z, c + gw / 2, z + 2, rng, "special", "gate", "front", "south", "sh", 6, mat));
        }
        for (int[] p : runs(0, CW - 1, gl(c - gw / 2, c + gw / 2)))
            rooms.add(room(p[0], z, p[1], z + 2, rng, "special", "wall", "front", "south", "sh", palace ? 7 : 5, mat));
        int D = z + T;
        for (int[] p : runs(0, CW - 1, List.of()))
            rooms.add(room(p[0], 0, p[1], 2, rng, "special", "wall", "front", "south", "sh", palace ? 7 : 5, mat));
        for (int xa : new int[]{0, CW - T})
            for (int[] p : runs(T, D - T - 1, List.of()))
                rooms.add(room(xa, p[0], xa + 2, p[1], rng, "special", "wall", "front", "east", "sh", palace ? 7 : 5, mat));
        int sw = palace ? 11 : 9;
        rooms.add(room(c - sw / 2, sz, c + sw / 2, sz + 2, rng, "special", "screen", "front", "north", mat));
        for (Map<String, Object> r : rooms) r.remove("_role");
        String name = (palace ? "宫院" : "住宅") + "·" + "一二三".charAt(jin - 1) + "进";
        return kw("name", name, "kind", kind, "jin", jin, "seed", num(seed), "tier", "mortal", "look", 0,
                "size", new ArrayList<>(List.of(CW, sz + 3)), "rooms", rooms);
    }

    /** A Python int as the JSON writer wants it (Integer when it fits). */
    static Object num(long v) {
        return v == (int) v ? (Object) (int) v : (Object) v;
    }

    // ------------------------------------------------------------------------------------------ write_all

    /**
     * write_all: the plans for 随机一院 (house and palace, one to three courts, seeds 1..seeds, and the 宫城 city(1)) as
     * out_dir/&lt;kind&gt;_&lt;jin&gt;_&lt;seed&gt;.json, json.dumps(d, ensure_ascii=False, indent=1) with the platform's line ends (as
     * Path.write_text); the old ones go first. Returns how many.
     */
    public static int writeAll(java.nio.file.Path out, int seeds) throws java.io.IOException {
        java.nio.file.Files.createDirectories(out);
        try (java.util.stream.Stream<java.nio.file.Path> old = java.nio.file.Files.list(out)) {
            for (java.nio.file.Path q : old.toList()) if (q.getFileName().toString().endsWith(".json")) java.nio.file.Files.deleteIfExists(q);
        }
        int n = 0;
        for (String kind : new String[]{"house", "palace"})
            for (int jin = 1; jin <= 3; jin++)
                for (int s = 1; s <= seeds; s++) {
                    java.nio.file.Files.writeString(out.resolve(kind + "_" + jin + "_" + s + ".json"), indent1(site(kind, jin, s)), StandardCharsets.UTF_8);
                    n++;
                }
        java.nio.file.Files.writeString(out.resolve("city_1.json"), indent1(city(1)), StandardCharsets.UTF_8);
        return n + 1;
    }

    /** json.dumps(v, ensure_ascii=False, indent=1), "\n" written as the platform's line end (text mode). */
    public static String indent1(Object v) {
        StringBuilder b = new StringBuilder();
        indent(b, v, 0);
        return b.toString().replace("\n", System.lineSeparator());
    }

    private static void indent(StringBuilder b, Object v, int level) {
        if (v instanceof Map<?, ?> m) {
            if (m.isEmpty()) {
                b.append("{}");
                return;
            }
            b.append("{\n");
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) b.append(",\n");
                first = false;
                b.append(" ".repeat(level + 1)).append(Json.quote(String.valueOf(e.getKey()))).append(": ");
                indent(b, e.getValue(), level + 1);
            }
            b.append("\n").append(" ".repeat(level)).append('}');
        } else if (v instanceof List<?> l) {
            if (l.isEmpty()) {
                b.append("[]");
                return;
            }
            b.append("[\n");
            boolean first = true;
            for (Object o : l) {
                if (!first) b.append(",\n");
                first = false;
                b.append(" ".repeat(level + 1));
                indent(b, o, level + 1);
            }
            b.append("\n").append(" ".repeat(level)).append(']');
        } else if (v instanceof int[] a) {
            List<Object> l = new ArrayList<>();
            for (int x : a) l.add(x);
            indent(b, l, level);
        } else b.append(Json.write(v, false));
    }

    // ------------------------------------------------------------------------------------------ 宫城

    public static Map<String, Object> city(long seed) {
        PyRandom rng = PyRandom.ofString("city/" + seed);
        Map<String, Object> gal = kw("podium", 1, "podium_style", "plain");
        int W = 151;
        for (Object[] t : new Object[][]{{7, 3, "xumizuo"}, {10, 8, "tiers"}}) {
            W = Math.max(W, 2 * (23 + steps(gal) + WALK + steps(kw("podium", t[1], "podium_style", t[2])) + (int) t[0]) + 1);
        }
        W |= 1;
        final int Wf = W;
        int c = W / 2;
        Map<String, Object> gold = kw("walls", "palace", "theme", "gold");
        List<Map<String, Object>> rooms = new ArrayList<>(), walls = new ArrayList<>();

        java.util.function.BiFunction<int[], Map<String, Object>, Map<String, Object>> hall = (xzd, kwm) -> {
            Map<String, Object> d = with(gold, "front", "south", "podium", 3, "podium_style", "xumizuo", "base", "white", "caihua", "hexi",
                    "beasts", 3, "brackets", 3, "pitch", 3, "uses", "council_hall");
            d.putAll(kwm);
            Map<String, Object> r = room(c - xzd[0], xzd[1], c + xzd[0], xzd[1] + xzd[2] - 1, rng, d);
            rooms.add(r);
            String[] ao = axisOpen(r, c);
            r.put("doors", ao[0]);
            r.put("open", ao[1]);
            return r;
        };
        java.util.function.BiFunction<int[], Map<String, Object>, int[]> gateHouse = (xzd, kwm) -> {
            Map<String, Object> d = with(gold, "front", "south", "open", "through", "roof", "xieshan", "podium", 3, "podium_style", "xumizuo",
                    "base", "white", "caihua", "xuanzi", "beasts", 2, "pitch", 3, "uses", "lookout");
            d.putAll(kwm);
            int z0 = xzd[1] + 1 - xzd[2] / 2;
            rooms.add(room(c - xzd[0], z0, c + xzd[0], z0 + xzd[2] - 1, rng, d));
            return new int[]{c - xzd[0], c + xzd[0]};
        };
        java.util.function.BiConsumer<int[], Integer> galleries = (zz, inner) -> {
            int z0 = zz[0], z1 = zz[1];
            for (Object[] xf : new Object[][]{{inner, "east"}, {Wf - inner - 9, "west"}}) {
                int x0 = (int) xf[0];
                for (int[] a : split(z0, (z1 - z0) % 2 == 0 ? z1 : z1 - 1, 125))
                    rooms.add(room(x0, a[0], x0 + 8, a[1], rng, "front", xf[1], "roof", "xuanshan", "podium", 1, "caihua", "xuanzi",
                            "uses", "storeroom", gold));
            }
        };
        java.util.function.Consumer<int[]> paving = q -> {
            int x0 = q[0], z0 = q[1], x1 = q[2], z1 = q[3];
            if ((x1 - x0) % 2 != 0) x1 -= 1;
            if ((z1 - z0) % 2 != 0) z1 -= 1;
            rooms.add(room(x0, z0, x1, z1, rng, "special", "paving", gold));
        };
        java.util.function.BiConsumer<Integer, int[]> crossWall = (z, gap) -> {
            for (int[] p : wallPieces(3, Wf - 4, List.of(gap)))
                walls.add(room(p[0], z, p[1], z + 2, rng, "special", "wall", "front", "south", "sh", 9, gold));
        };

        rooms.add(room(c - 13, -5, c + 13, 7, rng, "front", "north", "roof", "wudian", "double_eave", true, "podium", WH, "podium_style", "chengtai",
                "tunnels", 3, "caihua", "hexi", "beasts", 3, "pitch", 3, "uses", "lookout", gold));
        Map<String, Object> gateN = rooms.get(rooms.size() - 1);
        Map<String, Object> kn = after(gateN, hall.apply(new int[]{22, 0, 15}, kw("roof", "wudian", "double_eave", true)));
        Map<String, Object> jt = after(kn, hall.apply(new int[]{7, 0, 13}, kw("roof", "pyramid", "beasts", 2)));
        Map<String, Object> qq = after(jt, hall.apply(new int[]{22, 0, 17}, kw("roof", "wudian", "double_eave", true, "podium", 5)));
        Map<String, Object> probe = with(gold, "front", "south", "podium", 3, "podium_style", "xumizuo", "open", "through", "x0", 0, "x1", 0, "z0", 0, "z1", 10);
        int wq = gi(after(qq, probe), "z0") + 4;
        galleries.accept(new int[]{6, wq - 4}, 14);
        paving.accept(new int[]{23, wq - 10, W - 24, wq - 4});
        crossWall.accept(wq, gateHouse.apply(new int[]{15, wq, 11}, kw("double_eave", false)));
        Map<String, Object> gateQ = rooms.get(rooms.size() - 1);
        Map<String, Object> tiers = kw("podium", 8, "podium_style", "tiers", "base", "white");
        Map<String, Object> bh = after(gateQ, hall.apply(new int[]{25, 0, 21}, kw("roof", "xieshan", "double_eave", true, tiers)));
        Map<String, Object> zh = after(bh, hall.apply(new int[]{10, 0, 21}, kw("roof", "pyramid", tiers)));
        Map<String, Object> th = after(zh, hall.apply(new int[]{31, 0, 31}, kw("roof", "wudian", "double_eave", true, "beasts", 5, "brackets", 4,
                "uses", "throne_hall", tiers)));
        int wt = gi(th, "z1") + Math.max(56, steps(th) + WALK + steps(kw("podium", 3, "podium_style", "xumizuo")) + 9);
        galleries.accept(new int[]{wq + 6, wt - 4}, 14);
        int tx = Math.max(30, 23 + steps(gal) + WALK + plat(kw("podium", 2, "podium_style", "xumizuo")));
        for (Object[] xf : new Object[][]{{tx, "east"}, {W - tx - 15, "west"}}) {
            int x0 = (int) xf[0];
            rooms.add(room(x0, wt - 42, x0 + 14, wt - 22, rng, "front", xf[1], "storeys", 2, "roof", "wudian", "podium", 2, "podium_style", "xumizuo",
                    "base", "white", "caihua", "xuanzi", "beasts", 2, "uses", "scripture_store", gold));
        }
        paving.accept(new int[]{47, wt - 38, W - 48, wt - 8});
        crossWall.accept(wt, gateHouse.apply(new int[]{22, wt, 17}, kw("double_eave", true)));
        Map<String, Object> gateT = rooms.get(rooms.size() - 1);
        int zr = gi(gateT, "z1") + steps(gateT) + WALK + 1;
        zr += Math.floorMod(zr - wt, 2);
        rooms.add(room(3, zr, W - 4, zr + 8, rng, "special", "river", gold));
        paving.accept(new int[]{3, wt + 4, W - 4, zr - 2});
        paving.accept(new int[]{3, zr + 10, W - 4, zr + 18});
        int wf = zr + 20;
        Map<String, Object> ct = kw("podium", WH, "podium_style", "chengtai", "caihua", "hexi", "beasts", 3, "pitch", 3, "uses", "lookout", gold);
        rooms.add(room(c - 31, wf - 6, c + 31, wf + 10, rng, "front", "south", "roof", "wudian", "double_eave", true, "tunnels", 4, ct));
        for (Object[] xf : new Object[][]{{c - 43, "east"}, {c + 33, "west"}}) {
            int x0 = (int) xf[0];
            rooms.add(room(x0, wf + 14, x0 + 10, wf + 66, rng, "front", xf[1], "roof", "xieshan", "tunnels", 1, ct));
        }
        for (int x0 : new int[]{c - 45, c + 31})
            rooms.add(room(x0, wf + 68, x0 + 14, wf + 82, rng, "front", "south", "roof", "pyramid", "double_eave", true, "tunnels", 1, ct));
        int[] sideGap = {wt - 38, wt - 16};
        for (Object[] xf : new Object[][]{{0, "west"}, {W - 3, "east"}}) {
            int xa = (int) xf[0];
            String front = (String) xf[1];
            int x0 = front.equals("west") ? xa - 5 : xa - 4;
            rooms.add(room(x0, sideGap[0], x0 + 12, sideGap[1], rng, "front", front, "roof", "xieshan", "double_eave", true, "podium", WH,
                    "podium_style", "chengtai", "tunnels", 2, "caihua", "xuanzi", "beasts", 2, "pitch", 3, "uses", "lookout", "_tag", "sidegate", gold));
            for (int[] p : wallPieces(3, wf - 1, List.of(sideGap)))
                walls.add(room(xa, p[0], xa + 2, p[1], rng, "special", "wall", "front", "east", "sh", 10, gold));
        }
        for (int[] cc : new int[][]{{1, 1}, {W - 2, 1}, {1, wf + 1}, {W - 2, wf + 1}})
            rooms.add(room(cc[0] - 7, cc[1] - 7, cc[0] + 7, cc[1] + 7, rng, "front", "south", "roof", "shizi", "double_eave", true, "podium", WH,
                    "podium_style", "chengtai", "tunnels", 1, "beasts", 3, "caihua", "xuanzi", "uses", "lookout", "_tag", "corner", gold));
        int[] zzs = {wt - 36, wt - 28, wt - 20};
        for (int i = 0; i < 3; i++)
            for (int side : new int[]{-1, 1})
                rooms.add(room(c + side * 9 - 1, zzs[i], c + side * 9 + 1, zzs[i] + 2, rng, "special", "ornament",
                        "variant", Math.floorMod(i * 2 + (side > 0 ? 1 : 0), 4), gold));
        List<Map<String, Object>> sides = new ArrayList<>();
        for (int k = 0; k < 8; k++) sides.add(site("palace", 1, seed * 10 + k, false));
        List<List<Object[]>> cols = new ArrayList<>();
        Map<String, Object> corner = kw("podium", WH, "podium_style", "chengtai");
        for (int k = 0; k < 2; k++) {
            int mx = Integer.MIN_VALUE;
            for (Map<String, Object> r : (List<Map<String, Object>>) sides.get(k * 4).get("rooms"))
                if (!t(r, "special")) mx = Math.max(mx, reach(r) - gi(r, "z0"));
            int z = Math.max(T + 1, 8 + reach(corner) + 1 + mx);
            List<Object[]> col = new ArrayList<>();
            for (Map<String, Object> d : sides.subList(k * 4, (k + 1) * 4)) {
                int dd = (int) ((List<Object>) d.get("size")).get(1);
                if (z + dd > sideGap[0] - 4) break;
                col.add(new Object[]{z, d});
                z += dd + 2;
            }
            cols.add(col);
        }
        int SW = Integer.MIN_VALUE;
        for (List<Object[]> col : cols)
            for (Object[] zd : col) SW = Math.max(SW, (int) ((List<Object>) ((Map<String, Object>) zd[1]).get("size")).get(0));
        int off = T + SW + 2;
        for (List<Map<String, Object>> l : List.of(walls, rooms))
            for (Map<String, Object> r : l) {
                r.put("x0", gi(r, "x0") + off);
                r.put("x1", gi(r, "x1") + off);
            }
        int W2 = W + 2 * off;
        List<Map<String, Object>> moved = new ArrayList<>(), keep = new ArrayList<>();
        for (Map<String, Object> r : rooms) {
            Object tg = r.get("_tag");
            if ("corner".equals(tg) || "sidegate".equals(tg)) moved.add(r);
            else keep.add(r);
        }
        rooms.clear();
        rooms.addAll(keep);
        for (int xa : new int[]{off, off + W - 3})
            rooms.add(room(xa, sideGap[0] + 6, xa + 2, sideGap[1] - 6, rng, "special", "gate", "front", "east", "sh", 7, gold));
        int[] xas = {T + 1, W2 - T - 1 - SW};
        for (int q = 0; q < 2; q++) {
            int xa = xas[q];
            for (Object[] zd : cols.get(q)) {
                int z = (int) zd[0];
                Map<String, Object> d = (Map<String, Object>) zd[1];
                int cw = (int) ((List<Object>) d.get("size")).get(0);
                for (Map<String, Object> r0 : (List<Map<String, Object>>) d.get("rooms")) {
                    Map<String, Object> r = new LinkedHashMap<>(r0);
                    r.put("x0", gi(r, "x0") + xa + (SW - cw) / 2);
                    r.put("x1", gi(r, "x1") + xa + (SW - cw) / 2);
                    r.put("z0", gi(r, "z0") + z);
                    r.put("z1", gi(r, "z1") + z);
                    rooms.add(r);
                }
            }
        }
        for (Map<String, Object> r0 : moved) {
            Map<String, Object> r = new LinkedHashMap<>(r0);
            if (r.get("_tag").equals("corner")) {
                int cx = gi(r, "x0") < W2 / 2 ? 2 - TW / 2 : W2 - 3 + TW / 2;
                int cz = gi(r, "z0") < wf / 2 ? 2 - TW / 2 : wf + TW / 2;
                r.put("x0", cx - 7);
                r.put("x1", cx + 7);
                r.put("z0", cz - 7);
                r.put("z1", cz + 7);
            } else {
                boolean west = gi(r, "x0") < W2 / 2;
                r.put("x0", west ? -5 : W2 - 8);
                r.put("x1", west ? 7 : W2 + 4);
            }
            rooms.add(r);
        }
        for (List<Map<String, Object>> l : List.of(walls, rooms)) for (Map<String, Object> r : l) r.remove("_tag");
        List<Map<String, Object>> all = new ArrayList<>(walls);
        all.addAll(rooms);
        walls.addAll(cityWall(all, W2, wf, rng, gold));
        List<Map<String, Object>> out = new ArrayList<>(walls);
        out.addAll(rooms);
        return kw("name", "宫城", "kind", "city", "jin", 0, "seed", num(seed), "tier", "mortal", "look", 0,
                "size", new ArrayList<>(List.of(W2 + 2 * TW + 4, wf + 92)), "rooms", out);
    }

    static Map<String, Object> after(Map<String, Object> a, Map<String, Object> r) {
        int fa = a.get("front").equals("south") && !"chengtai".equals(a.get("podium_style")) ? steps(a) : plat(a);
        int fb = opensBack(r) ? steps(r) : plat(r);
        int gap = Math.max(fa + fb + WALK, reach(a) + reach(r) + 1);
        int nz0 = gi(a, "z1") + gap + 1, nz1 = gi(a, "z1") + gap + 1 + gi(r, "z1") - gi(r, "z0");
        r.put("z0", nz0);
        r.put("z1", nz1);
        return r;
    }

    static List<Map<String, Object>> cityWall(List<Map<String, Object>> rooms, int W2, int wf, PyRandom rng, Map<String, Object> gold) {
        int o = TW - 3;
        List<Map<String, Object>> towers = new ArrayList<>();
        for (Map<String, Object> r : rooms)
            if ("chengtai".equals(r.get("podium_style")) && pod(r, "podium") == WH) towers.add(r);
        Object[][] lines = {{"north", true, -o, 2}, {"south", true, wf, wf + o + 2}, {"west", false, -o, 2}, {"east", false, W2 - 3, W2 - 1 + o}};
        int L = WH * 4;
        int need = 2 * (L / 2 + 1) + 1;
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] ln : lines) {
            String front = (String) ln[0];
            boolean alongX = (boolean) ln[1];
            int d0 = (int) ln[2], d1 = (int) ln[3];
            java.util.function.Function<int[], Map<String, Object>> piece = q -> {
                int a0 = q[0], a1 = q[1];
                boolean madao = q[2] != 0;
                int x0 = alongX ? a0 : d0, z0 = alongX ? d0 : a0, x1 = alongX ? a1 : d1, z1 = alongX ? d1 : a1;
                return room(x0, z0, x1, z1, rng, "special", "wall", "front", front, "sh", WH, "taper", 1, "step_slope", "27",
                        "tunnels", madao ? 0 : 1, gold);
            };
            List<int[]> on = new ArrayList<>();
            for (Map<String, Object> tw : towers) {
                int[] f = foot(tw);
                int a0, a1, b0, b1;
                if (alongX) {
                    a0 = f[0];
                    a1 = f[2];
                    b0 = f[1];
                    b1 = f[3];
                } else {
                    a0 = f[1];
                    a1 = f[3];
                    b0 = f[0];
                    b1 = f[2];
                }
                if (b0 <= d0 && d1 <= b1) on.add(new int[]{a0, a1});
            }
            int mn = Integer.MAX_VALUE, mx = Integer.MIN_VALUE;
            for (int[] p : on) {
                mn = Math.min(mn, p[0]);
                mx = Math.max(mx, p[1]);
            }
            List<int[]> spans = gapPieces(mn, mx, on);
            int[] gate = sorted(on).get(on.size() / 2);
            int[] best = null;
            int mw = Integer.MIN_VALUE;
            for (int[] s : spans) mw = Math.max(mw, s[1] - s[0]);
            outer:
            for (int k = 0; k < mw + 1; k += 2) {
                for (int[] s : spans) {
                    int a0 = s[0], a1 = s[1];
                    Integer p0 = a0 == gate[1] + 1 ? (Integer) (gate[1] + 1 + k) : a1 == gate[0] - 1 ? (Integer) (gate[0] - need - k) : null;
                    if (p0 != null && a0 <= p0 && p0 + need - 1 <= a1 && madaoClear(piece.apply(new int[]{p0, p0 + need - 1, 1}), rooms)) {
                        best = new int[]{a0, a1, p0};
                        break outer;
                    }
                }
            }
            for (int[] s : spans) {
                int a0 = s[0], a1 = s[1];
                List<int[]> cuts = best == null || best[0] != a0 ? gl(a0, a1)
                        : gl(a0, best[2] - 1, best[2], best[2] + need - 1, best[2] + need, a1);
                for (int[] cc : cuts) {
                    if (cc[0] > cc[1]) continue;
                    if (best != null && cc[0] == best[2] && cc[1] == best[2] + need - 1) out.add(piece.apply(new int[]{cc[0], cc[1], 1}));
                    else for (int[] p : oddPieces(cc[0], cc[1], 125)) out.add(piece.apply(new int[]{p[0], p[1], 0}));
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------ 紫禁城 1:1

    public static Map<String, Object> cityReal(long seed) {
        return cityReal(seed, false);
    }

    public static Map<String, Object> cityReal(long seed, boolean moat) {
        PyRandom rng = PyRandom.ofString("city_real/" + seed);
        Map<String, Object> gold = kw("walls", "palace", "theme", "gold");
        List<Map<String, Object>> rooms = new ArrayList<>(), walls = new ArrayList<>();
        final int CX = 376;
        Real R = new Real(rng, gold, rooms, walls);
        Map<String, Object> HALL = kw("podium", 2, "podium_style", "xumizuo", "base", "white", "caihua", "xuanzi", "beasts", 2, "pitch", 3, "uses", "council_hall");
        Map<String, Object> GREAT = kw("podium", 8, "podium_style", "tiers", "base", "white", "caihua", "hexi", "beasts", 3, "pitch", 1, "overhang", 2,
                "uses", "council_hall");
        Map<String, Object> CT = kw("podium_style", "chengtai", "caihua", "hexi", "beasts", 3, "pitch", 3, "uses", "lookout");
        int E = 752, S = 960;
        int[] swGap = {315, 437};
        Map<String, Object> OUT = kw("sh", 10, "t", 9, "taper", 1, "step_slope", "27", "tunnels", 1);
        int nOut = walls.size();
        R.wallX(0, 12, E - 12, gl(352, 400), with(OUT, "front", "north"));
        R.wallX(S - 8, 12, E - 12, List.of(swGap), with(OUT, "front", "south"));
        int[] sideGate = {769, 811};
        R.wallZ(0, 12, S - 12, gl(sideGate[0] - 3, sideGate[1] + 3), with(OUT, "front", "west"));
        R.wallZ(E - 8, 12, S - 12, gl(sideGate[0] - 3, sideGate[1] + 3), with(OUT, "front", "east"));
        List<Map<String, Object>> outer = new ArrayList<>(walls.subList(nOut, walls.size()));
        for (int[] cc : new int[][]{{1, 1}, {E - 1, 1}, {1, S - 1}, {E - 1, S - 1}})
            R.at(cc[0], cc[1] - 7, 15, 15, "角楼", kw("roof", "shizi", "double_eave", true, "podium", 10, "tunnels", 1, with(CT, "caihua", "xuanzi")));
        R.at(CX, -6, 43, 21, "神武门", kw("front", "north", "roof", "wudian", "double_eave", true, "podium", 10, "tunnels", 3, CT));
        R.add(-9, sideGate[0], 21, 43, "西华门", kw("front", "west", "roof", "wudian", "double_eave", true, "podium", 10, "tunnels", 3, CT));
        R.add(E - 10, sideGate[0], 21, 43, "东华门", kw("front", "east", "roof", "wudian", "double_eave", true, "podium", 10, "tunnels", 3, CT));
        R.at(CX, 937, 61, 25, "午门", kw("roof", "wudian", "double_eave", true, "podium", 12, "tunnels", 3, CT));
        for (int wx : new int[]{329, 2 * CX - 329}) {
            R.at(wx, 937, 23, 21, "", kw("roof", "pyramid", "double_eave", true, "podium", 12, "tunnels", 1, CT));
            R.at(wx, 964, 15, 63, "", kw("front", wx < CX ? "east" : "west", "roof", "xieshan", "podium", 12, "tunnels", 1, CT));
            R.at(wx, 1032, 23, 23, "", kw("roof", "pyramid", "double_eave", true, "podium", 12, "tunnels", 1, CT));
        }
        for (Object[] xfn : new Object[][]{{285, "east", "熙和门"}, {458, "west", "协和门"}}) {
            int x0 = (int) xfn[0];
            R.gallery(x0, 786, 931, (String) xfn[1], gl(845, 871), 9);
            R.add(x0 - 2, 845, 13, 27, (String) xfn[2], kw("front", xfn[1], "roof", "xieshan", "open", "through", HALL));
        }
        R.at(CX, 812, 67, 11, "内金水桥", kw("special", "river"));
        R.pave(CX, 791, 810, 21);
        R.pave(CX, 824, 931, 21);
        R.at(CX, 764, 41, 21, "太和门", kw("roof", "xieshan", "double_eave", true, "sh", 9, "open", "through", with(HALL, "podium", 3, "caihua", "hexi", "beasts", 3)));
        for (Object[] cn : new Object[][]{{CX - 70, "贞度门"}, {CX + 70, "昭德门"}})
            R.at((int) cn[0], 769, 21, 11, (String) cn[1], kw("roof", "xieshan", "open", "through", HALL));
        R.wallX(773, 269, 483, gl(356, 396, CX - 80, CX - 60, CX + 60, CX + 80), kw("sh", 9));
        for (Object[] xf : new Object[][]{{259, "east"}, {484, "west"}})
            R.gallery((int) xf[0], 405, 769, (String) xf[1], gl(525, 539, 679, 721, 753, 769), 9);
        for (Object[] xf : new Object[][]{{259, "east"}, {479, "west"}})
            for (int z0 : new int[]{525, 753})
                R.add((int) xf[0], z0, 15, 15, "", kw("front", xf[1], "roof", "xieshan", "double_eave", true, HALL));
        for (Object[] xfn : new Object[][]{{259, "east", "弘义阁"}, {477, "west", "体仁阁"}})
            R.add((int) xfn[0], 679, 17, 43, (String) xfn[2], kw("front", xfn[1], "storeys", 2, "roof", "wudian", with(HALL, "uses", "scripture_store")));
        R.at(CX, 547, 63, 37, "太和殿", kw("roof", "wudian", "double_eave", true, "sh", 12, "brackets", 4, with(GREAT, "beasts", 5, "uses", "throne_hall")));
        R.at(CX, 493, 25, 25, "中和殿", kw("roof", "pyramid", "sh", 9, GREAT));
        R.at(CX, 439, 49, 25, "保和殿", kw("roof", "xieshan", "double_eave", true, "sh", 10, GREAT));
        R.pave(CX, 598, 762, 21);
        for (int[] dv : new int[][]{{-26, 2}, {26, 1}}) R.at(CX + dv[0], 600, 3, 3, "", kw("special", "ornament", "variant", dv[1]));
        for (int dx : new int[]{-40, 40})
            for (int zz : new int[]{612, 640, 668}) R.at(CX + dx, zz, 3, 3, "", kw("special", "ornament", "variant", 3));
        for (int[] za : new int[][]{{538, 274}, {403, 269}}) {
            int z = za[0], a = za[1];
            R.wallX(z, a, 311, gl(286, 294), kw("sh", 7));
            R.wallX(z, 441, 2 * CX - a, gl(2 * CX - 294, 2 * CX - 286), kw("sh", 7));
            R.gateX(290, z, 9, "gate", "");
            R.gateX(2 * CX - 290, z, 9, "gate", "");
        }
        R.wallZ(259, 348, 402, gl(369, 381), kw("sh", 8));
        R.gateZ(259, 375, 13, "gate", "west", "隆宗门");
        R.wallZ(491, 348, 402, gl(369, 381), kw("sh", 8));
        R.gateZ(491, 375, 13, "gate", "east", "景运门");
        R.wallX(345, 262, 316, gl(308, 316), kw());
        R.wallX(345, 436, 490, gl(436, 444), kw());
        R.gateX(312, 345, 9, "gate", "");
        R.gateX(440, 345, 9, "gate", "");
        for (int dx : new int[]{-30, 30}) R.at(CX + dx, 352, 3, 3, "", kw("special", "ornament", "variant", 3));
        R.at(CX, 335, 29, 13, "乾清门", kw("roof", "xieshan", "open", "through", HALL));
        R.wallX(339, 317, 435, gl(362, 390), kw("sh", 8));
        for (Object[] xf : new Object[][]{{317, "east"}, {427, "west"}}) {
            R.gallery((int) xf[0], 135, 337, (String) xf[1], gl(262, 274), 9);
            R.gateZ((int) xf[0] + 3, 268, 9, "glazedgate", (String) xf[1], "");
        }
        R.at(CX, 256, 45, 25, "乾清宫", kw("roof", "wudian", "double_eave", true, "sh", 8, with(HALL, "podium", 5, "caihua", "hexi", "beasts", 3, "pitch", 1, "overhang", 2)));
        R.at(CX, 220, 15, 15, "交泰殿", kw("roof", "pyramid", with(HALL, "podium", 3, "caihua", "hexi")));
        R.at(CX, 178, 45, 21, "坤宁宫", kw("roof", "wudian", "double_eave", true, with(HALL, "podium", 3, "caihua", "hexi", "beasts", 3)));
        R.at(CX, 129, 17, 11, "坤宁门", kw("roof", "xieshan", "open", "through", with(HALL, "podium", 1, "podium_style", "plain")));
        R.wallX(132, 309, 443, gl(368, 384), kw("sh", 8));
        R.pave(CX, 289, 333, 21);
        R.pave(CX, 205, 219, 9);
        R.pave(CX, 141, 175, 9);
        R.wallX(49, 309, 443, gl(368, 384), kw("sh", 7));
        R.gateX(CX, 49, 17, "glazedgate", "顺贞门");
        R.wallZ(309, 52, 131, List.of(), kw());
        R.wallZ(441, 52, 131, List.of(), kw());
        R.at(CX, 60, 25, 17, "钦安殿", kw("roof", "luding", "double_eave", true, HALL));
        for (Object[] cn : new Object[][]{{CX - 40, "千秋亭"}, {CX + 40, "万春亭"}}) {
            rooms.addAll(presetRooms("故宫/亭/千秋亭.json", (int) cn[0] - 9, 97, rng));
            rooms.get(rooms.size() - 5).put("label", cn[1]);
        }
        for (Object[] cn : new Object[][]{{CX - 48, "澄瑞亭"}, {CX + 48, "浮碧亭"}})
            R.at((int) cn[0], 74, 9, 9, (String) cn[1], kw("roof", "pyramid", "open", "open", "podium", 1, "base", "white"));
        R.at(323, 54, 15, 11, "延晖阁", kw("storeys", 2, "roof", "xieshan", with(HALL, "podium", 1, "podium_style", "plain")));
        R.at(428, 56, 9, 9, "堆秀山", kw("roof", "pyramid", "open", "open", "podium", 10, "podium_style", "chengtai", "base", "rubble", "tunnels", 1, "uses", "lookout"));
        R.pave(CX, 81, 127, 21);
        R.pave(CX, 19, 47, 21);
        int[] rows = {221, 167, 113};
        Object[][][] six = {{{445, new String[]{"景仁宫", "承乾宫", "钟粹宫"}}, {501, new String[]{"延禧宫", "永和宫", "景阳宫"}}},
                {{259, new String[]{"永寿宫", "翊坤宫", "储秀宫"}}, {203, new String[]{"太极殿", "长春宫", "咸福宫"}}}};
        for (Object[][] side : six)
            for (Object[] xn : side) {
                String[] names = (String[]) xn[1];
                for (int i = 0; i < 3; i++)
                    sixPalace((int) xn[0], rows[i], names[i], rng, rooms, walls, names[i].equals("景阳宫") || names[i].equals("咸福宫"));
            }
        fiveCourts(445, 57, "乾东五所", rng, rooms, walls);
        fiveCourts(203, 57, "乾西五所", rng, rooms, walls);
        Court C = new Court(rng, rooms, walls);
        C.reset();
        C.back = null;
        C.run(445, 277, 49, 63, "斋宫", kw("w", 25, "d", 13, "roof", "xieshan"));
        C.reset();
        C.back = kw("w", 21, "d", 11, "roof", "yingshan");
        C.link = true;
        C.run(501, 277, 49, 69, "毓庆宫", kw("w", 21, "d", 11, "roof", "xieshan"));
        C.reset();
        C.back = kw("w", 33, "d", 11, "roof", "yingshan");
        C.link = true;
        C.wings = true;
        C.run(226, 277, 81, 63, "养心殿", kw("w", 37, "d", 13, "roof", "xieshan"));
        C.reset();
        C.back = kw("w", 45, "d", 11, "roof", "xieshan");
        C.link = true;
        C.run(557, 281, 65, 119, "奉先殿", kw("w", 45, "d", 23, "roof", "wudian", "double_eave", true, "podium", 3));
        R.at(555, 440, 29, 15, "箭亭", kw("roof", "xieshan", HALL));
        int[] x3 = {600, 631, 662};
        for (int k = 0; k < 3; k++) {
            C.reset();
            C.back = kw("w", 19, "d", 9, "roof", "yingshan");
            C.middle = kw("w", 19, "d", 9, "roof", "yingshan");
            C.mat = kw("walls", "palace", "theme", "jade");
            C.run(x3[k], 520, 29, 101, k == 1 ? "南三所" : "", kw("w", 13, "d", 9, "roof", "xieshan"));
        }
        C.reset();
        C.back = kw("w", 29, "d", 15, "roof", "xieshan");
        C.link = true;
        C.extra = List.of(kw("z", 652, "w", 35, "d", 15, "label", "文渊阁", "storeys", 2, "roof", "xieshan", "walls", "darkwood", "theme", "black", "pond", true));
        C.run(520, 650, 91, 131, "文华殿", kw("w", 29, "d", 15, "roof", "xieshan"));
        C.reset();
        C.back = kw("w", 29, "d", 15, "roof", "xieshan");
        C.link = true;
        C.run(142, 680, 71, 101, "武英殿", kw("w", 29, "d", 15, "roof", "xieshan"));
        R.at(177, 784, 19, 7, "", kw("special", "river"));
        R.at(95, 695, 41, 9, "宝蕴楼", kw("storeys", 2, "roof", "wudian", "walls", "greybrick", "theme", "grey", "podium", 1, "uses", "treasury"));
        for (int x0 : new int[]{75, 107})
            R.add(x0, 704, 9, 13, "", kw("storeys", 2, "roof", "wudian", "walls", "greybrick", "theme", "grey", "podium", 1, "front", x0 < 95 ? "east" : "west",
                    "uses", "treasury"));
        C.reset();
        C.run(100, 820, 41, 51, "南薰殿", kw("w", 21, "d", 11, "roof", "xieshan"));
        C.reset();
        C.back = kw("w", 31, "d", 13, "roof", "xieshan");
        C.gatehall = true;
        C.run(151, 349, 75, 99, "慈宁宫", kw("w", 35, "d", 17, "roof", "xieshan", "double_eave", true));
        C.reset();
        C.pavilions = new Object[][]{{188, 540, 11, "临溪亭"}, {175, 570, 9, ""}, {201, 570, 9, ""}};
        C.gate = "gate";
        C.run(163, 460, 51, 131, "慈宁宫花园", kw("w", 21, "d", 13, "roof", "xieshan", "z", 480));
        C.reset();
        C.back = kw("w", 25, "d", 11, "roof", "yingshan");
        C.run(81, 341, 61, 111, "寿康宫", kw("w", 25, "d", 13, "roof", "xieshan"));
        C.reset();
        C.back = kw("w", 33, "d", 11, "roof", "yingshan");
        C.run(63, 225, 79, 107, "寿安宫", kw("w", 33, "d", 13, "roof", "xieshan"));
        C.reset();
        C.run(70, 140, 65, 77, "英华殿", kw("w", 25, "d", 13, "roof", "xieshan"));
        C.reset();
        C.back = kw("w", 25, "d", 11, "roof", "xieshan");
        C.run(151, 180, 49, 91, "", kw("w", 25, "d", 17, "roof", "pyramid", "storeys", 3, "z", 225, "label", "雨花阁", "caihua", "hexi", "beasts", 3));
        C.reset();
        C.pavilions = new Object[][]{{125, 92, 9, ""}, {155, 92, 9, ""}};
        C.run(110, 45, 61, 67, "建福宫花园", kw("w", 17, "d", 17, "roof", "pyramid", "double_eave", true, "storeys", 2, "label", "延春阁", "z", 62));
        int NX = 686;
        R.wallZ(627, 9, 406, List.of(), kw("sh", 8));
        R.wallZ(743, 9, 406, List.of(), kw("sh", 8));
        R.wallX(404, 630, 742, gl(676, 696), kw("sh", 8));
        R.gateX(NX, 404, 21, "glazedgate", "皇极门");
        R.at(NX, 412, 29, 3, "九龙壁", kw("special", "screen", "front", "north"));
        R.at(NX, 360, 27, 13, "宁寿门", kw("roof", "xieshan", "open", "through", HALL));
        R.wallX(365, 630, 742, gl(673, 699), kw("sh", 8));
        R.at(NX, 300, 45, 25, "皇极殿", kw("roof", "wudian", "double_eave", true, "sh", 8, with(HALL, "podium", 4, "caihua", "hexi", "beasts", 3)));
        R.at(NX, 250, 35, 17, "宁寿宫", kw("roof", "xieshan", HALL));
        R.pave(NX, 331, 357, 21);
        R.wallX(238, 630, 742, gl(642, 650, 680, 692, 722, 730), kw("sh", 8));
        R.gateX(NX, 238, 13, "glazedgate", "养性门");
        R.gateX(646, 238, 9, "gate", "");
        R.gateX(726, 238, 9, "gate", "");
        R.wallZ(662, 30, 237, List.of(), kw("sh", 7));
        R.wallZ(708, 30, 237, List.of(), kw("sh", 7));
        R.at(NX, 205, 31, 17, "养性殿", kw("roof", "xieshan", HALL));
        R.at(NX, 160, 37, 23, "乐寿堂", kw("roof", "xieshan", with(HALL, "uses", "study")));
        R.at(NX, 128, 31, 9, "颐和轩", kw("roof", "xieshan", with(HALL, "podium", 1, "podium_style", "plain")));
        R.at(NX, 80, 31, 15, "景祺阁", kw("storeys", 2, "roof", "xieshan", with(HALL, "podium", 1, "podium_style", "plain")));
        R.at(645, 205, 13, 11, "古华轩", kw("roof", "xieshan", "open", "open", "podium", 1));
        R.at(645, 172, 19, 11, "遂初堂", kw("roof", "xieshan", with(HALL, "podium", 1, "podium_style", "plain")));
        R.at(645, 140, 19, 11, "萃赏楼", kw("storeys", 2, "roof", "xieshan", with(HALL, "podium", 1, "podium_style", "plain")));
        R.at(645, 100, 21, 21, "符望阁", kw("storeys", 2, "roof", "pyramid", with(HALL, "caihua", "hexi")));
        R.at(645, 80, 27, 9, "倦勤斋", kw("roof", "juanpeng", "podium", 1));
        R.at(726, 198, 17, 17, "畅音阁", kw("front", "north", "storeys", 3, "roof", "xieshan", "open", "front", "podium", 1, "caihua", "hexi", "uses", "lookout"));
        R.at(726, 215, 21, 9, "", kw("storeys", 2, "front", "north", "roof", "juanpeng", "podium", 1, "uses", "backstage"));
        R.at(726, 160, 25, 13, "阅是楼", kw("storeys", 2, "roof", "juanpeng", with(HALL, "podium", 1, "podium_style", "plain", "uses", "lookout")));
        R.at(726, 55, 23, 23, "景福宫", kw("roof", "xieshan", HALL));
        R.at(725, 15, 31, 11, "梵华楼", kw("storeys", 2, "roof", "xieshan", "podium", 1, "caihua", "xuanzi", "uses", "temple_hall"));
        R.at(703, 15, 13, 11, "佛日楼", kw("storeys", 2, "roof", "xieshan", "podium", 1, "caihua", "xuanzi", "uses", "temple_hall"));
        if (moat) {
            Object[][] runs = {{-72, gl(-72, E + 72)}, {S + 21, gl(-72, swGap[0] - 13, swGap[1] + 13, E + 72)}};
            for (Object[] zs : runs) {
                int z0 = (int) zs[0];
                for (int[] a : (List<int[]>) zs[1])
                    for (int[] p : split(a[0], a[1] - Math.floorMod(a[1] - a[0], 2), 251))
                        R.add(p[0], z0, p[1] - p[0] + 1, 51, p[0] == -72 && z0 < 0 ? "筒子河" : "", kw("special", "river"));
            }
            for (int x0 : new int[]{-72, E + 21})
                for (int[] p : split(-21, S + 20 - Math.floorMod(S + 41, 2), 251))
                    R.add(x0, p[0], 51, p[1] - p[0] + 1, "", kw("special", "river", "front", "east"));
        }
        List<Map<String, Object>> allr = new ArrayList<>(walls);
        allr.addAll(rooms);
        Map<String, int[]> gates = new LinkedHashMap<>();
        gates.put("north", new int[]{352, 400});
        gates.put("south", swGap);
        gates.put("west", sideGate);
        gates.put("east", sideGate);
        realMadao(outer, allr, gates);
        for (Map<String, Object> r : allr) {
            if (!(((gi(r, "x1") - gi(r, "x0")) % 2 == 0 && (gi(r, "z1") - gi(r, "z0")) % 2 == 0) || t(r, "_preset")))
                throw new AssertionError(r.get("label"));
            r.remove("_preset");
        }
        int mnx = Integer.MAX_VALUE, mxx = Integer.MIN_VALUE, mnz = Integer.MAX_VALUE, mxz = Integer.MIN_VALUE;
        for (Map<String, Object> r : allr) {
            mnx = Math.min(mnx, Math.min(gi(r, "x0"), gi(r, "x1")));
            mxx = Math.max(mxx, Math.max(gi(r, "x0"), gi(r, "x1")));
            mnz = Math.min(mnz, Math.min(gi(r, "z0"), gi(r, "z1")));
            mxz = Math.max(mxz, Math.max(gi(r, "z0"), gi(r, "z1")));
        }
        for (Map<String, Object> r : allr) {
            String[] ao = axisOpen(r, CX);
            r.put("doors", ao[0]);
            r.put("open", ao[1]);
        }
        return kw("name", "紫禁城·1:1", "kind", "city_real", "jin", 0, "seed", num(seed), "tier", "mortal", "look", 0,
                "size", new ArrayList<>(List.of(mxx - mnx + 1, mxz - mnz + 1)), "origin", new ArrayList<>(List.of(mnx, mnz)), "rooms", allr);
    }

    /** city_real's local helpers (add, at, wall_x, wall_z, gate_x, gate_z, pave, gallery). */
    static final class Real {
        final PyRandom rng;
        final Map<String, Object> gold;
        final List<Map<String, Object>> rooms, walls;

        Real(PyRandom rng, Map<String, Object> gold, List<Map<String, Object>> rooms, List<Map<String, Object>> walls) {
            this.rng = rng;
            this.gold = gold;
            this.rooms = rooms;
            this.walls = walls;
        }

        Map<String, Object> add(int x0, int z0, int w, int d, String label, Map<String, Object> kw) {
            if (w % 2 == 0 || d % 2 == 0) throw new AssertionError(label + " " + w + " " + d);
            Map<String, Object> r = room(x0, z0, x0 + w - 1, z0 + d - 1, rng, with(gold, kw));
            if (!label.isEmpty()) r.put("label", label);
            rooms.add(r);
            return r;
        }

        Map<String, Object> at(int cx, int z0, int w, int d, String label, Map<String, Object> kw) {
            return add(cx - w / 2, z0, w, d, label, kw);
        }

        /** kw may carry sh, t, front (popped as the Python's named parameters). */
        void wallX(int z, int a0, int a1, List<int[]> gaps, Map<String, Object> kw0) {
            Map<String, Object> kw = new LinkedHashMap<>(kw0);
            Object sh = kw.containsKey("sh") ? kw.remove("sh") : 7;
            int t = kw.containsKey("t") ? (int) kw.remove("t") : 3;
            Object front = kw.containsKey("front") ? kw.remove("front") : "south";
            for (int[] p : wallPieces(a0, a1, gaps))
                walls.add(room(p[0], z, p[1], z + t - 1, rng, Siteplan.kw("special", "wall", "front", front, "sh", sh, with(gold, kw))));
        }

        void wallZ(int x, int a0, int a1, List<int[]> gaps, Map<String, Object> kw0) {
            Map<String, Object> kw = new LinkedHashMap<>(kw0);
            Object sh = kw.containsKey("sh") ? kw.remove("sh") : 7;
            int t = kw.containsKey("t") ? (int) kw.remove("t") : 3;
            Object front = kw.containsKey("front") ? kw.remove("front") : "east";
            for (int[] p : wallPieces(a0, a1, gaps))
                walls.add(room(x, p[0], x + t - 1, p[1], rng, Siteplan.kw("special", "wall", "front", front, "sh", sh, with(gold, kw))));
        }

        Map<String, Object> gateX(int cx, int z, int w, String kind, String label) {
            return at(cx, z, w, 3, label, kw("special", kind, "front", "south", "sh", 6));
        }

        Map<String, Object> gateZ(int x, int cz, int w, String kind, String front, String label) {
            return add(x, cz - w / 2, 3, w, label, kw("special", kind, "front", front, "sh", 6));
        }

        void pave(int cx, int z0, int z1, int w) {
            if ((z1 - z0) % 2 != 0) z1 -= 1;
            if (z1 - z0 >= 4) at(cx, z0, w, z1 - z0 + 1, "", kw("special", "paving"));
        }

        void gallery(int x0, int z0, int z1, String front, List<int[]> gaps, int depth) {
            for (int[] p : gapPieces(z0, z1, gaps)) {
                if (p[1] - p[0] < 4) continue;
                for (int[] a : oddPieces(p[0], p[1], 125))
                    add(x0, a[0], depth, a[1] - a[0] + 1, "", kw("front", front, "roof", "xuanshan", "podium", 1, "caihua", "xuanzi", "uses", "storeroom"));
            }
        }
    }

    static void realMadao(List<Map<String, Object>> outer, List<Map<String, Object>> rooms, Map<String, int[]> gates) {
        for (Map.Entry<String, int[]> e : gates.entrySet()) {
            String front = e.getKey();
            int g0 = e.getValue()[0], g1 = e.getValue()[1];
            boolean ns = front.equals("north") || front.equals("south");
            List<Map<String, Object>> side = new ArrayList<>();
            for (Map<String, Object> r : outer) if (r.get("front").equals(front)) side.add(r);
            side.sort(Comparator.comparingInt(r -> gi(r, ns ? "x0" : "z0")));
            for (boolean before : new boolean[]{true, false}) {
                List<Map<String, Object>> near = new ArrayList<>();
                for (Map<String, Object> r : side)
                    if (before ? gi(r, ns ? "x1" : "z1") < g0 : gi(r, ns ? "x0" : "z0") > g1) near.add(r);
                if (before) java.util.Collections.reverse(near);
                for (Map<String, Object> r : near) {
                    r.put("tunnels", 0);
                    if (madaoClear(r, rooms)) break;
                    r.put("tunnels", 1);
                }
            }
        }
    }

    static List<Map<String, Object>> presetRooms(String rel, int x0, int z0, PyRandom rng) {
        String text;
        try {
            text = Files.readString(Env.DATA.resolve("library").resolve(rel), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        Map<String, Object> d = Json.parseObject(text);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : (List<Object>) d.get("rooms")) {
            Map<String, Object> r = new LinkedHashMap<>((Map<String, Object>) o);
            r.put("x0", gi(r, "x0") + x0);
            r.put("x1", gi(r, "x1") + x0);
            r.put("z0", gi(r, "z0") + z0);
            r.put("z1", gi(r, "z1") + z0);
            r.put("seed", (int) rng.randrange(1 << 30));
            r.put("_preset", true);
            out.add(r);
        }
        return out;
    }

    static void sixPalace(int x0, int z0, String name, PyRandom rng, List<Map<String, Object>> rooms, List<Map<String, Object>> walls, boolean small) {
        Map<String, Object> gold = kw("walls", "palace", "theme", "gold");
        int c = x0 + 24;
        for (Object[] zg : new Object[][]{{z0, List.<int[]>of()}, {z0 + 46, gl(c - 4, c + 4)}})
            for (int[] p : wallPieces(x0, x0 + 48, (List<int[]>) zg[1]))
                walls.add(room(p[0], (int) zg[0], p[1], (int) zg[0] + 2, rng, "special", "wall", "front", "south", "sh", 7, gold));
        for (int x : new int[]{x0, x0 + 46})
            for (int[] p : wallPieces(z0 + 3, z0 + 45))
                walls.add(room(x, p[0], x + 2, p[1], rng, "special", "wall", "front", "east", "sh", 7, gold));
        rooms.add(room(c - 4, z0 + 46, c + 4, z0 + 48, rng, "special", "glazedgate", "front", "south", "sh", 6, gold));
        rooms.add(room(c - 10, z0 + 4, c + 10, z0 + 10, rng, "roof", "yingshan", "podium", 1, "uses", "bedroom", "caihua", "xuanzi", gold));
        int fw = small ? 7 : 10;
        rooms.add(room(c - fw, z0 + 19, c + fw, z0 + 29, rng, "roof", small ? "wudian" : "xieshan", "podium", 2, "podium_style", "xumizuo",
                "base", "white", "caihua", "xuanzi", "beasts", 2, "uses", "main_room", "label", name, gold));
        int e = small ? 2 : 0;
        for (int[] a : new int[][]{{x0 + 3, x0 + 11 + e}, {x0 + 37 - e, x0 + 45}})
            walls.add(room(a[0], z0 + 23, a[1], z0 + 25, rng, "special", "wall", "front", "south", "sh", 6, gold));
        rooms.add(room(x0 + 3, z0 + 36, x0 + 9, z0 + 44, rng, "front", "east", "roof", "yingshan", "podium", 1, "caihua", "xuanzi", "uses", "study", gold));
        rooms.add(room(x0 + 39, z0 + 36, x0 + 45, z0 + 44, rng, "front", "west", "roof", "yingshan", "podium", 1, "caihua", "xuanzi", "uses", "study", gold));
    }

    static void fiveCourts(int x0, int z0, String name, PyRandom rng, List<Map<String, Object>> rooms, List<Map<String, Object>> walls) {
        Map<String, Object> gold = kw("walls", "palace", "theme", "gold");
        int W = 105, D = 51;
        List<int[]> gates = new ArrayList<>();
        for (int k = 0; k < 5; k++) gates.add(new int[]{x0 + 21 * k + 7, x0 + 21 * k + 13});
        for (Object[] zg : new Object[][]{{z0, List.<int[]>of()}, {z0 + D - 3, gates}})
            for (int[] p : wallPieces(x0, x0 + W - 1, (List<int[]>) zg[1]))
                walls.add(room(p[0], (int) zg[0], p[1], (int) zg[0] + 2, rng, "special", "wall", "front", "south", "sh", 6, gold));
        for (int k = 0; k < 6; k++) {
            int xw = Math.min(x0 + 21 * k, x0 + W - 3);
            for (int[] p : wallPieces(z0 + 3, z0 + D - 4))
                walls.add(room(xw, p[0], xw + 2, p[1], rng, "special", "wall", "front", "east", "sh", 6, gold));
        }
        for (int k = 0; k < gates.size(); k++) {
            int g0 = gates.get(k)[0], g1 = gates.get(k)[1];
            rooms.add(room(g0, z0 + D - 3, g1, z0 + D - 1, rng, "special", "gate", "front", "south", "sh", 6, gold));
            int c = (g0 + g1) / 2;
            int[] zz = {z0 + 5, z0 + 19, z0 + 33};
            for (int j = 0; j < 3; j++) {
                Map<String, Object> r = room(c - 6, zz[j], c + 6, zz[j] + 6, rng, "roof", j < 2 ? "yingshan" : "xieshan", "podium", 1, "caihua", "xuanzi",
                        "uses", j < 2 ? "bedroom" : "main_room", gold);
                if (k == 2 && j == 2) r.put("label", name);
                rooms.add(r);
            }
        }
    }

    /** _court with its keyword arguments as fields (reset() to the defaults before each call). */
    static final class Court {
        final PyRandom rng;
        final List<Map<String, Object>> rooms, walls;
        Map<String, Object> back, middle, mat;
        boolean link, wings, gatehall;
        List<Map<String, Object>> extra;
        Object[][] pavilions;
        String gate;

        Court(PyRandom rng, List<Map<String, Object>> rooms, List<Map<String, Object>> walls) {
            this.rng = rng;
            this.rooms = rooms;
            this.walls = walls;
        }

        void reset() {
            back = middle = mat = null;
            link = wings = gatehall = false;
            extra = List.of();
            pavilions = new Object[0][];
            gate = "glazedgate";
        }

        void run(int x0, int z0, int W, int D, String name, Map<String, Object> hall) {
            Map<String, Object> mat = this.mat != null ? this.mat : kw("walls", "palace", "theme", "gold");
            int c = x0 + W / 2;
            int gw = gatehall ? 21 : 9;
            for (Object[] zg : new Object[][]{{z0, List.<int[]>of()}, {z0 + D - 3, gl(c - gw / 2, c + gw / 2)}})
                for (int[] p : wallPieces(x0, x0 + W - 1, (List<int[]>) zg[1]))
                    walls.add(room(p[0], (int) zg[0], p[1], (int) zg[0] + 2, rng, "special", "wall", "front", "south", "sh", 7, mat));
            for (int x : new int[]{x0, x0 + W - 3})
                for (int[] p : wallPieces(z0 + 3, z0 + D - 4))
                    walls.add(room(x, p[0], x + 2, p[1], rng, "special", "wall", "front", "east", "sh", 7, mat));
            if (gatehall)
                rooms.add(room(c - 10, z0 + D - 8, c + 10, z0 + D + 2, rng, "roof", "xieshan", "open", "through", "podium", 2, "podium_style", "xumizuo",
                        "base", "white", "caihua", "xuanzi", "beasts", 2, "uses", "lookout", mat));
            else
                rooms.add(room(c - gw / 2, z0 + D - 3, c + gw / 2, z0 + D - 1, rng, "special", gate, "front", "south", "sh", 6, mat));
            int zb = z0 + 5;
            for (Map<String, Object> e : extra) {
                bld(e, gi(e, "z"), c, mat, kw());
                zb = Math.max(zb, gi(e, "z") + gi(e, "d") + (t(e, "pond") ? 14 : 6));
            }
            Map<String, Object> b = null;
            if (back != null) b = bld(back, zb, c, mat, kw("podium", 1, "podium_style", "plain"));
            if (middle != null) {
                b = bld(middle, back != null ? zb + gi(back, "d") + 8 : zb, c, mat, kw("podium", 1, "podium_style", "plain"));
            }
            Object hzo = hall.get("z");
            int hz;
            if (hzo == null) hz = b != null ? gi(b, "z1") + (link ? 16 : 9) : z0 + Math.floorDiv(2 * D, 5) - gi(hall, "d") / 2;
            else hz = Dyn.toInt(hzo);
            Map<String, Object> h = bld(hall, hz, c, mat, kw());
            if (!t(hall, "label") && !name.isEmpty()) h.put("label", name);
            if (link && b != null)
                rooms.add(room(c - 4, gi(b, "z1") + 1, c + 4, gi(h, "z0") - 1, rng, "roof", "juanpeng", "podium", 1, "uses", "main_room", mat));
            if (wings) {
                int fz0 = gi(h, "z1") + 8;
                int fz1 = z0 + D - 6;
                if ((fz1 - fz0) % 2 != 0) fz1 -= 1;
                if (fz1 - fz0 >= 6) {
                    for (Object[] xx : new Object[][]{{x0 + 5, x0 + 15, "east"}, {x0 + W - 16, x0 + W - 6, "west"}})
                        rooms.add(room((int) xx[0], fz0, (int) xx[1], fz1, rng, "front", xx[2], "roof", "yingshan", "podium", 1, "caihua", "xuanzi",
                                "uses", "study", mat));
                }
            }
            for (Object[] pv : pavilions) {
                int cx = (int) pv[0], pz = (int) pv[1], s = (int) pv[2];
                Map<String, Object> r = room(cx - s / 2, pz, cx + s / 2, pz + s - 1, rng, "roof", "pyramid", "open", "open", "podium", 1, mat);
                if (!((String) pv[3]).isEmpty()) r.put("label", pv[3]);
                rooms.add(r);
            }
        }

        Map<String, Object> bld(Map<String, Object> spec, int z, int c, Map<String, Object> mat, Map<String, Object> dflt) {
            Map<String, Object> s = new LinkedHashMap<>(dflt);
            s.putAll(spec);
            int w = Dyn.toInt(s.remove("w")), d = Dyn.toInt(s.remove("d"));
            s.remove("z");
            Object label = s.containsKey("label") ? s.remove("label") : "";
            Object pond = s.containsKey("pond") ? s.remove("pond") : false;
            Map<String, Object> kw = new LinkedHashMap<>(mat);
            kw.putAll(Siteplan.kw("podium", 2, "podium_style", "xumizuo", "base", "white", "caihua", "xuanzi", "beasts", 2, "uses", "main_room"));
            kw.putAll(s);
            if (pod(kw, "podium") == 0) kw.put("podium_style", "plain");
            Map<String, Object> r = room(c - w / 2, z, c + w / 2, z + d - 1, rng, kw);
            if (Dyn.truthy(label)) r.put("label", label);
            rooms.add(r);
            if (Dyn.truthy(pond)) rooms.add(room(c - 9, z + d + 3, c + 9, z + d + 9, rng, "special", "river", mat));
            return r;
        }
    }

    // ------------------------------------------------------------------------------------------ 随机院落

    public static Map<String, Object> siteFree(long seed) {
        return new Free(seed).run();
    }

    static final class Free {
        final long seed;
        final PyRandom rng;
        final int CW, c;
        final Map<String, Object> mat = kw("walls", "palace", "theme", "gold");
        final List<Map<String, Object>> rooms = new ArrayList<>(), walls = new ArrayList<>();
        final boolean walled;
        final int inner0, inner1;

        Free(long seed) {
            this.seed = seed;
            rng = PyRandom.ofString("free/" + seed);
            CW = odd(rng.randint(25, 61));
            c = CW / 2;
            walled = rng.random() < 0.9;
            inner0 = walled ? T : 0;
            inner1 = walled ? CW - T - 1 : CW - 1;
        }

        int court(int z) {
            int depth = odd(rng.randint(15, 31));
            int hallD = odd(rng.randint(7, 13));
            if (rng.random() < 0.9) {
                int hw = odd(rng.randint(9, Math.max(9, inner1 - inner0 - 6)));
                int hx0 = rng.random() < 0.3 ? rng.randint(inner0, inner1 - hw + 1) : c - hw / 2;
                rooms.add(room(hx0, z, hx0 + hw - 1, z + hallD - 1, rng, "front", "south", "_role", "main", mat));
                int roomW = Math.min(hx0 - inner0, inner1 - (hx0 + hw - 1));
                if (rng.random() < 0.5 && roomW >= 6) {
                    int ew = Math.min(7, roomW - 1);
                    ew -= ew % 2 != 0 ? 0 : 1;
                    if (ew >= 5) {
                        rooms.add(room(hx0 - ew, z + 1, hx0 - 1, z + hallD - 2, rng, "front", "south", "_ear", new int[]{-1, inner0}, mat));
                        rooms.add(room(hx0 + hw, z + 1, hx0 + hw + ew - 1, z + hallD - 2, rng, "front", "south", "_ear", new int[]{1, inner1}, mat));
                    }
                }
            } else {
                hallD = 0;
            }
            int z0 = z + hallD + 1;
            int z1 = z + depth - 1;
            String sides = rng.choice("none", "west", "east", "both", "both");
            int ww = odd(rng.randint(5, 11));
            boolean w = sides.equals("west") || sides.equals("both"), e = sides.equals("east") || sides.equals("both");
            if (z1 - z0 >= 8) {
                int wl0 = z0 + 1, wl1 = z1 - 1;
                wl1 -= (wl1 - wl0) % 2 == 0 ? 0 : 1;
                if (w) rooms.add(room(inner0, wl0, inner0 + ww - 1, wl1, rng, "front", "east", mat));
                if (e) rooms.add(room(inner1 - ww + 1, wl0, inner1, wl1, rng, "front", "west", mat));
            }
            int px0 = inner0 + (w ? ww : 0);
            int px1 = inner1 - (e ? ww : 0);
            if (rng.random() < 0.7 && px1 - px0 >= 4 && z1 - z0 >= 4)
                rooms.add(room(px0, z0, px0 + ((px1 - px0) / 2) * 2, z0 + ((z1 - z0) / 2) * 2, rng, "special", "paving", mat));
            if (rng.random() < 0.35 && px1 - px0 >= 15 && z1 - z0 >= 13) {
                int pc = odd(rng.randint(7, 9));
                int px = rng.randint(px0 + 2, px1 - pc - 1);
                int pz = rng.randint(z0 + 2, z1 - pc - 1);
                rooms.add(room(px, pz, px + pc - 1, pz + pc - 1, rng, "front", "south", mat));
            } else if (rng.random() < 0.3 && px1 - px0 >= 9) {
                int n = rng.randint(1, 3);
                for (int k = 0; k < n; k++) {
                    int oz = z0 + 2 + k * 5;
                    if (oz + 2 < z1) {
                        rooms.add(room(c - 5, oz, c - 3, oz + 2, rng, "special", "ornament", "variant", rng.randint(0, 3), mat));
                        rooms.add(room(c + 3, oz, c + 5, oz + 2, rng, "special", "ornament", "variant", rng.randint(0, 3), mat));
                    }
                }
            }
            return z + depth;
        }

        int divider(int z) {
            String kind = rng.choice("chuihua", "glazedgate", "gate", "hall", "none");
            if (kind.equals("none")) return z;
            int gw = !kind.equals("hall") ? 9 : odd(rng.randint(11, 17));
            int gx = rng.random() < 0.7 ? c - gw / 2 : rng.randint(inner0 + 2, inner1 - gw - 1);
            int wz = z;
            if (kind.equals("hall")) {
                rooms.add(room(gx, z, gx + gw - 1, z + 8, rng, "front", "south", "open", "through", mat));
                wz = z + 3;
            } else {
                rooms.add(room(gx, z - (kind.equals("chuihua") ? 1 : 0), gx + gw - 1, z + (kind.equals("chuihua") ? 3 : 2), rng,
                        "special", kind, "front", "south", "sh", 6, mat));
            }
            for (int[] a : wallPieces(inner0, inner1, gl(gx, gx + gw - 1)))
                walls.add(room(a[0], wz, a[1], wz + 2, rng, "special", "wall", "front", "south", "sh", rng.choice(new int[]{5, 6, 7}), mat));
            return wz + T + (kind.equals("hall") ? 3 : 0);
        }

        Map<String, Object> run() {
            int z = (walled ? T : 0) + 2;
            if (rng.random() < 0.35) {
                rooms.add(room(inner0, z, inner1 - ((inner1 - inner0) % 2 == 0 ? 0 : 1), z + 6, rng, "front", "south", mat));
                z += 9;
            }
            int n = rng.randint(1, 3);
            for (int k = 0; k < n; k++) {
                if (k != 0) z = divider(z) + 1;
                z = court(z);
            }
            String yard = rng.choice("plain", "daozuo", "river", "plain");
            String gateKind = rng.choice("gate", "glazedgate", "hall", "paifang_out");
            int gw = gateKind.equals("gate") ? 7 : gateKind.equals("glazedgate") ? 13 : odd(rng.randint(11, 15));
            int gx = rng.random() < 0.6 ? c - gw / 2 : (rng.random() < 0.5 ? inner1 - gw - 1 : inner0 + 1);
            if (yard.equals("river") && inner1 - inner0 >= 12) {
                rooms.add(room(inner0, z + 1, inner1 - ((inner1 - inner0) % 2 == 0 ? 0 : 1), z + 7, rng, "special", "river", mat));
                z += 9;
            } else if (yard.equals("daozuo")) {
                z += 3;
                for (int[] p : gapPieces(inner0, inner1, gl(gx - 1, gx + gw)))
                    if (p[1] - p[0] >= 4) rooms.add(room(p[0], z, p[0] + ((p[1] - p[0]) / 2) * 2, z + 6, rng, "front", "north", mat));
                z += 7;
            } else {
                z += rng.choice(new int[]{3, 5, 7});
            }
            int D;
            if (walled) {
                if (gateKind.equals("hall"))
                    rooms.add(room(gx, z - 2, gx + gw - 1, z + 4, rng, "front", "south", "open", "through", mat));
                else if (gateKind.equals("gate") || gateKind.equals("glazedgate"))
                    rooms.add(room(gx, z, gx + gw - 1, z + 2, rng, "special", gateKind, "front", "south", "sh", 6, mat));
                List<int[]> gap = !gateKind.equals("paifang_out") ? gl(gx, gx + gw - 1) : gl(c - 3, c + 3);
                for (int[] a : wallPieces(0, CW - 1, gap))
                    walls.add(room(a[0], z, a[1], z + 2, rng, "special", "wall", "front", "south", "sh", rng.choice(new int[]{5, 7, 9}), mat));
                D = z + T;
                for (int[] a : wallPieces(0, CW - 1))
                    walls.add(room(a[0], 0, a[1], 2, rng, "special", "wall", "front", "south", "sh", rng.choice(new int[]{5, 7, 9}), mat));
                for (int xa : new int[]{0, CW - T})
                    for (int[] a : wallPieces(T, D - T - 1))
                        walls.add(room(xa, a[0], xa + 2, a[1], rng, "special", "wall", "front", "east", "sh", 7, mat));
                if (gateKind.equals("paifang_out"))
                    rooms.add(room(c - 8, D + 3, c + 8, D + 7, rng, "special", "paifang", "sh", 7, mat));
                if (rng.random() < 0.5) {
                    int sx = !gateKind.equals("paifang_out") ? gx + gw / 2 : c;
                    boolean pf = gateKind.equals("paifang_out");
                    rooms.add(room(sx - 5, D + (pf ? 10 : 4), sx + 5, D + (pf ? 12 : 6), rng, "special", "screen", "front", "north", mat));
                }
            } else {
                D = z;
            }
            List<Map<String, Object>> all = new ArrayList<>(walls);
            all.addAll(rooms);
            return kw("name", "随机院落", "kind", "free", "jin", 0, "seed", num(seed), "tier", "mortal", "look", 0,
                    "size", new ArrayList<>(List.of(CW, D + 12)), "rooms", all);
        }
    }

    // ------------------------------------------------------------------------------------------ randomize

    /** compose.ROOFS (the room table's roof forms). */
    static final List<String> ROOF_FORMS = List.of("xieshan", "wudian", "xuanshan", "yingshan", "juanpeng", "pyramid", "luding", "shizi", "yuanzan");
    /** compose.USES (ids). */
    static final List<String> USES = List.of("main_room", "bedroom", "study", "kitchen", "storeroom", "guest_room", "inn_hall", "teahouse",
            "private_dining", "pharmacy", "general_store", "smithy", "ancestral_hall", "temple_hall", "meditation_room", "seclusion", "alchemy_room",
            "forging_room", "talisman_room", "formation_room", "scripture_store", "sword_gallery", "treasury", "lecture_hall", "council_hall",
            "task_hall", "throne_hall", "ancestor_tablets", "teleport_room", "auction_hall", "refectory", "outer_dorm", "inner_disciple_room",
            "elder_quarters", "training_room", "herb_store", "beast_stable", "lookout", "stele", "bedroom_north", "maiden_room", "master_study",
            "accounts", "granary", "mill_room", "dyeing", "brewery", "weaving", "pottery", "tofu", "cloth_shop", "pawnshop", "bookshop", "clinic",
            "coffin_shop", "gambling_den", "stable", "backstage", "guardroom", "barracks", "courtroom", "jail", "classroom", "discipline_hall",
            "array_control", "spirit_stone_vault", "artifact_shop", "pill_shop", "talisman_shop", "material_shop", "cultivator_inn_room",
            "auction_box", "ship_cabin");

    /** compose.options()["bay_styles"] ids: auto, then the styles the bay parts name. */
    public static List<String> bayStyles() {
        if (Parts.BAYS.isEmpty()) Parts.reloadBays();
        List<String> out = new ArrayList<>(List.of("auto"));
        List<String> styles = new ArrayList<>();
        for (Map<String, Object> e : Parts.BAYS.values()) {
            Object st = e.get("style");
            if (Dyn.truthy(st) && !styles.contains(st)) styles.add((String) st);
        }
        out.addAll(styles);
        return out;
    }

    public static Map<String, Object> randomize(Map<String, Object> d, PyRandom rng) {
        List<String> wallsL = new ArrayList<>(Bgen.WALLS.keySet());
        String wall = rng.choice(wallsL);
        String theme = rng.choice(new ArrayList<>(Arch.ROOFS.keySet()));
        String bay = rng.choice(bayStyles());
        List<String> bases = new ArrayList<>(Compose.loadBases().keySet());
        int p0 = rng.choice(new int[]{0, 0, 1, 1, 2});
        int lift = rng.choice(new int[]{1, 2, 2, 3});
        String mainStyle = rng.choice("xumizuo", "tiers", "plain");
        List<Map<String, Object>> rooms = (List<Map<String, Object>>) d.get("rooms");
        List<Object[]> given = new ArrayList<>();
        for (Map<String, Object> r : rooms) if (!t(r, "special")) given.add(new Object[]{r, r4(r)});
        for (Map<String, Object> r : rooms) {
            r.put("walls", wall);
            r.put("theme", theme);
            if (t(r, "special")) continue;
            int st = rng.randint(1, 5);
            int ev = rng.randint(0, 2);
            r.put("storeys", st);
            r.put("sh", rng.choice(new int[]{0, 4, 5, 6, 7, 8, 9, 10, 12, 14, 16}));
            r.put("taper", st > 1 ? rng.randint(0, 3) : 0);
            r.put("podium", rng.choice(new int[]{0, 1, 2, 3, 4, 5, 6, 8, 10, 12}));
            r.put("podium_style", rng.choice("plain", "xumizuo", "tiers", "chengtai"));
            r.put("tunnels", rng.randint(0, 4));
            r.put("base", rng.choice(bases));
            r.put("roof", rng.choice(ROOF_FORMS));
            r.put("bay_style", bay);
            r.put("double_eave", ev >= 1);
            r.put("triple_eave", ev == 2);
            r.put("pitch", rng.randint(0, 4));
            r.put("overhang", rng.randint(0, 4));
            r.put("lift", rng.randint(0, 4));
            r.put("beasts", rng.randint(0, 5));
            r.put("brackets", rng.randint(0, 4));
            r.put("caihua", rng.choice("", "xuanzi", "hexi", "sushi"));
            r.put("horsehead", rng.random() < 0.5);
            r.put("open", new String[]{"closed", "open", "front", "through"}[rng.choicesIndex(new long[]{3, 3, 3, 1}, 1)[0]]);
            r.put("doors", new String[]{"front", "front_back", "four"}[rng.choicesIndex(new long[]{7, 2, 1}, 1)[0]]);
            List<Object> uses = new ArrayList<>();
            for (int i = 0; i < 8; i++) uses.add(rng.choice(USES));
            r.put("uses", uses);
            List<Object> fo = new ArrayList<>();
            for (int i = 0; i < 8; i++) fo.add(rng.random() < 0.25 ? rng.choice("", "closed", "through", "open", "front") : "");
            r.put("floor_open", fo);
            r.put("variant", rng.randint(0, 3));
            r.put("roof_variant", rng.randint(0, 3));
            if ("main".equals(r.get("_role"))) {
                r.put("podium", p0 + lift);
                r.put("podium_style", mainStyle);
            } else {
                r.put("podium", p0);
                r.put("podium_style", "plain");
            }
            int g = rng.randint(0, 4);
            r.put("veranda", g == 1 || g == 3);
            r.put("back_veranda", g == 2 || g == 3);
            r.put("colonnade", g == 4);
            if (rng.random() < 0.15) {
                r.put("round", true);
                r.put("shape", "round");
                r.put("round_roof", rng.choice("cone", "helmet", "dome", "luding", "flat"));
            }
            if ("yuanzan".equals(r.get("roof")) && Math.abs((gi(r, "x1") - gi(r, "x0")) - (gi(r, "z1") - gi(r, "z0"))) > 2) r.put("roof", "pyramid");
            fitFrame(r);
        }
        partEars(rooms);
        closeWalls(rooms, given);
        clearSteps(rooms);
        d.put("name", d.get("name") + "·全参数随机");
        return d;
    }

    static boolean inside(int[] f, int x, int z) {
        return f[0] <= x && x <= f[2] && f[1] <= z && z <= f[3];
    }

    static void closeWalls(List<Map<String, Object>> rooms, List<Object[]> given) {
        for (Map<String, Object> w : rooms) {
            if (!"wall".equals(w.get("special"))) continue;
            boolean alongX = gi(w, "x1") - gi(w, "x0") >= gi(w, "z1") - gi(w, "z0");
            String lo = alongX ? "x0" : "z0", hi = alongX ? "x1" : "z1";
            int mid = alongX ? Math.floorDiv(gi(w, "z0") + gi(w, "z1"), 2) : Math.floorDiv(gi(w, "x0") + gi(w, "x1"), 2);
            for (Object[] es : new Object[][]{{hi, 1}, {lo, -1}}) {
                String end = (String) es[0];
                int step = (int) es[1];
                while (true) {
                    int a = gi(w, end) + step;
                    int x = alongX ? a : mid, z = alongX ? mid : a;
                    boolean gave = false;
                    for (Object[] rf : given) {
                        Map<String, Object> r = (Map<String, Object>) rf[0];
                        if (inside((int[]) rf[1], x, z) && !inside(r4(r), x, z)) {
                            gave = true;
                            break;
                        }
                    }
                    boolean other = false;
                    if (gave) for (Map<String, Object> o : rooms)
                        if (o != w && inside(r4(o), x, z)) {
                            other = true;
                            break;
                        }
                    if (!gave || other) break;
                    w.put(end, a);
                }
            }
        }
    }

    static int sideReach(Map<String, Object> r) {
        int eave = EAVE[pod(r, "overhang")] + (pod(r, "brackets") >= 3 ? 2 : 0) + (t(r, "double_eave") || t(r, "triple_eave") ? 1 : 0) + 1;
        return Math.max(eave, podiumOut(r)) + (t(r, "colonnade") ? 4 : 0);
    }

    static final int[] EAVE = {3, 2, 3, 4, 5};

    static int[] stepRect(Map<String, Object> r) {
        int n = steps(r);
        Object ps = r.get("podium_style");
        int hw = !("xumizuo".equals(ps) || "tiers".equals(ps)) ? 1 : 4;
        int cx = Math.floorDiv(gi(r, "x0") + gi(r, "x1"), 2), cz = Math.floorDiv(gi(r, "z0") + gi(r, "z1"), 2);
        String fr = sget(r, "front", "south");
        if (fr.equals("south")) return new int[]{cx - hw, gi(r, "z1") + 1, cx + hw, gi(r, "z1") + n};
        if (fr.equals("north")) return new int[]{cx - hw, gi(r, "z0") - n, cx + hw, gi(r, "z0") - 1};
        if (fr.equals("east")) return new int[]{gi(r, "x1") + 1, cz - hw, gi(r, "x1") + n, cz + hw};
        return new int[]{gi(r, "x0") - n, cz - hw, gi(r, "x0") - 1, cz + hw};
    }

    static void clearSteps(List<Map<String, Object>> rooms) {
        List<Map<String, Object>> blds = new ArrayList<>();
        List<int[]> solid = new ArrayList<>();
        for (Map<String, Object> r : rooms) if (!t(r, "special")) blds.add(r);
        for (Map<String, Object> r : rooms)
            if (t(r, "special") && !"paving".equals(r.get("special")) && !"river".equals(r.get("special"))) solid.add(r4(r));
        for (Map<String, Object> r : blds) {
            while (pod(r, "podium") > 0) {
                int[] st = stepRect(r);
                List<int[]> others = new ArrayList<>();
                for (Map<String, Object> o : blds)
                    if (o != r) {
                        int m = reach(o);
                        others.add(new int[]{gi(o, "x0") - m, gi(o, "z0") - m, gi(o, "x1") + m, gi(o, "z1") + m});
                    }
                for (Map<String, Object> o : blds) if (o != r) others.add(stepRect(o));
                others.addAll(solid);
                boolean any = false;
                for (int[] b : others)
                    if (hit(st, b)) {
                        any = true;
                        break;
                    }
                if (!any) break;
                r.put("podium", Dyn.toInt(r.get("podium")) - 1);
                if (gi(r, "podium") == 0) r.put("podium_style", "plain");
            }
        }
    }

    public static void partEars(List<Map<String, Object>> rooms) {
        Map<String, Object> hall = null;
        List<Map<String, Object>> drop = new ArrayList<>();
        for (Map<String, Object> r : rooms) {
            if ("main".equals(r.get("_role"))) hall = r;
            Object ear = r.remove("_ear");
            if (ear == null || hall == null) continue;
            int side = ((int[]) ear)[0], lim = ((int[]) ear)[1];
            int sh = sideReach(r) + sideReach(hall) - (side < 0 ? (gi(hall, "x0") - gi(r, "x1") - 1) : (gi(r, "x0") - gi(hall, "x1") - 1));
            if (sh > 0) {
                int mv = Math.max(0, Math.min(sh, side < 0 ? (gi(r, "x0") - lim) : (lim - gi(r, "x1"))));
                r.put("x0", gi(r, "x0") + side * mv);
                r.put("x1", gi(r, "x1") + side * mv);
                sh -= mv;
            }
            if (sh > 0) {
                int cut = sh + sh % 2;
                if (gi(r, "x1") - gi(r, "x0") + 1 - cut < 5) drop.add(r);
                else if (side < 0) r.put("x1", gi(r, "x1") - cut);
                else r.put("x0", gi(r, "x0") + cut);
            }
        }
        for (Map<String, Object> r : drop) rooms.remove(r);
    }

    static int[] eaveZone(Map<String, Object> r) {
        int m = reach(r) - ((t(r, "veranda") || t(r, "back_veranda")) && !t(r, "colonnade") ? 4 : 0);
        Map<String, Integer> ext = new LinkedHashMap<>();
        for (String d : new String[]{"south", "north", "east", "west"}) ext.put(d, m);
        String fr = sget(r, "front", "south");
        ext.put(fr, ext.get(fr) + (t(r, "veranda") && !t(r, "colonnade") ? 4 : 0));
        String bk = BACK.get(fr);
        ext.put(bk, ext.get(bk) + (t(r, "back_veranda") && !t(r, "colonnade") ? 4 : 0));
        return new int[]{gi(r, "x0") - ext.get("west"), gi(r, "z0") - ext.get("north"), gi(r, "x1") + ext.get("east"), gi(r, "z1") + ext.get("south")};
    }

    static final Map<String, String> BACK = Map.of("south", "north", "north", "south", "east", "west", "west", "east");

    static int[] zone(Map<String, Object> r) {
        int m = reach(r);
        return new int[]{gi(r, "x0") - m, gi(r, "z0") - m, gi(r, "x1") + m, gi(r, "z1") + m};
    }

    public static int reach(Map<String, Object> r) {
        int eave = EAVE[pod(r, "overhang")] + (pod(r, "brackets") >= 3 ? 2 : 0);
        int plat = podiumOut(r);
        int gal = t(r, "colonnade") || t(r, "veranda") || t(r, "back_veranda") ? 4 : 0;
        return Math.max(eave, plat) + gal;
    }

    public static int podiumOut(Map<String, Object> r) {
        int pod = pod(r, "podium");
        if (pod == 0) return 0;
        Integer given = Compose.podiumMargin(r);
        int g = given == null ? 0 : given;
        int m = Math.max(g != 0 ? g : 1, 2);
        int step = Platforms.tierStep(pod, given, Arch.stepRun(r.get("step_slope")));
        Object style = r.get("podium_style");
        int out;
        if ("xumizuo".equals(style)) out = m + 1;
        else if ("tiers".equals(style)) out = m + 1 + 2 * step;
        else if ("chengtai".equals(style)) out = Math.max(3, g);
        else out = m;
        if ("round".equals(r.get("podium_shape")) && !"round".equals(r.get("shape"))) {
            int hx = Math.floorDiv(gi(r, "x1") - gi(r, "x0"), 2), hz = Math.floorDiv(gi(r, "z1") - gi(r, "z0"), 2);
            int gal = t(r, "colonnade") || t(r, "veranda") || t(r, "back_veranda") ? 4 : 0;
            out = ("tiers".equals(style) ? m + 1 + 2 * step : "xumizuo".equals(style) ? m + 1 : m)
                    + (int) Math.ceil(UcrtMath.hypotInt(hx + gal, hz + gal)) - Math.min(hx, hz);
        }
        return out;
    }

    public static int steps(Map<String, Object> r) {
        int pod = pod(r, "podium");
        int extra = podiumOut(r);
        int run = !"chengtai".equals(r.get("podium_style")) ? Arch.stepRun(r.get("step_slope")) : 1;
        return (pod != 0 ? pod * run + extra : 1) + (t(r, "veranda") || t(r, "colonnade") ? 4 : 0);
    }

    static final List<String> SOLID_SPECIALS = List.of("wall", "gate", "glazedgate", "chuihua", "screen", "river", "paifang", "ornament");

    public static int plat(Map<String, Object> r) {
        return podiumOut(r);
    }

    public static int[] foot(Map<String, Object> r) {
        int p = plat(r);
        return new int[]{gi(r, "x0") - p, gi(r, "z0") - p, gi(r, "x1") + p, gi(r, "z1") + p};
    }

    public static List<int[]> walkRects(Map<String, Object> r) {
        return walkRects(r, WALK);
    }

    public static List<int[]> walkRects(Map<String, Object> r, int strip) {
        if ("chengtai".equals(r.get("podium_style"))) return new ArrayList<>();
        int n = steps(r) + strip;
        Object ps = r.get("podium_style");
        int hw = !("xumizuo".equals(ps) || "tiers".equals(ps)) ? 1 : 4;
        int cx = Math.floorDiv(gi(r, "x0") + gi(r, "x1"), 2), cz = Math.floorDiv(gi(r, "z0") + gi(r, "z1"), 2);
        Map<String, int[]> rect = new LinkedHashMap<>();
        rect.put("south", new int[]{cx - hw, gi(r, "z1") + 1, cx + hw, gi(r, "z1") + n});
        rect.put("north", new int[]{cx - hw, gi(r, "z0") - n, cx + hw, gi(r, "z0") - 1});
        rect.put("east", new int[]{gi(r, "x1") + 1, cz - hw, gi(r, "x1") + n, cz + hw});
        rect.put("west", new int[]{gi(r, "x0") - n, cz - hw, gi(r, "x0") - 1, cz + hw});
        String fr = sget(r, "front", "south");
        String bk = BACK.get(fr);
        List<int[]> out = new ArrayList<>();
        out.add(rect.get(fr));
        if (opensBack(r)) out.add(rect.get(bk));
        if (doors(r).equals("four"))
            for (Map.Entry<String, int[]> e : rect.entrySet()) if (!e.getKey().equals(fr) && !e.getKey().equals(bk)) out.add(e.getValue());
        return out;
    }

    static String kind(Map<String, Object> r) {
        Object s = r.get("special");
        if (Dyn.truthy(s)) return (String) s;
        Object sh = r.get("shape");
        return Dyn.truthy(sh) ? (String) sh : "";
    }

    static int[] madao(Map<String, Object> r, Integer run0) {
        if (!kind(r).equals("wall") || pod(r, "tunnels") == 1) return null;
        String fr = sget(r, "front", "south");
        boolean ns = fr.equals("south") || fr.equals("north");
        int hx = Math.floorDiv(ns ? gi(r, "x1") - gi(r, "x0") : gi(r, "z1") - gi(r, "z0"), 2);
        int hz = Math.floorDiv(ns ? gi(r, "z1") - gi(r, "z0") : gi(r, "x1") - gi(r, "x0"), 2);
        int run = run0 != null && run0 != 0 ? run0 : Arch.stepRun(r.get("step_slope"));
        int shv = pod(r, "sh");
        int L = Math.max(4, Math.min(16, shv != 0 ? shv : 6)) * run;
        if (hz < 3 || L / 2 + 1 > hx || L - L / 2 > hx) return null;
        int lo = -(L / 2);
        int[] rot = Blocks.ROT.get(fr);
        int a = rot[0], b = rot[1], c = rot[2], d = rot[3];
        int cx = Math.floorDiv(gi(r, "x0") + gi(r, "x1"), 2), cz = Math.floorDiv(gi(r, "z0") + gi(r, "z1"), 2);
        int mnx = Integer.MAX_VALUE, mnz = Integer.MAX_VALUE, mxx = Integer.MIN_VALUE, mxz = Integer.MIN_VALUE;
        for (int u : new int[]{lo - 1, lo + 2 * run - 1})
            for (int v : new int[]{-hz - 1, -hz - WALK}) {
                int px = cx + a * u + b * v, pz = cz + c * u + d * v;
                mnx = Math.min(mnx, px);
                mnz = Math.min(mnz, pz);
                mxx = Math.max(mxx, px);
                mxz = Math.max(mxz, pz);
            }
        return new int[]{mnx, mnz, mxx, mxz};
    }

    static List<Map<String, Object>> madaoHits(Map<String, Object> w, int[] m, List<Map<String, Object>> rooms) {
        boolean ns = sget(w, "front", "south").equals("south") || sget(w, "front", "south").equals("north");
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> b : rooms) {
            String k = kind(b);
            if (b == w || (k.equals("wall") && java.util.Objects.equals(b.get("front"), w.get("front"))
                    && (ns ? gi(b, "z0") == gi(w, "z0") && gi(b, "z1") == gi(w, "z1") : gi(b, "x0") == gi(w, "x0") && gi(b, "x1") == gi(w, "x1"))))
                continue;
            if ((k.isEmpty() || k.equals("round")) && !t(b, "stack") && b.get("_on") == null) {
                boolean h = hit(m, foot(b));
                if (!h) for (int[] f : walkRects(b, 0))
                    if (hit(m, f)) {
                        h = true;
                        break;
                    }
                if (h) out.add(b);
            } else if (SOLID_SPECIALS.contains(k)) {
                int g = k.equals("river") ? 0 : 1;
                if (hit(m, new int[]{gi(b, "x0") - g, gi(b, "z0") - g, gi(b, "x1") + g, gi(b, "z1") + g})) out.add(b);
            }
        }
        return out;
    }

    static boolean madaoClear(Map<String, Object> w, List<Map<String, Object>> rooms) {
        List<int[]> ms = new ArrayList<>();
        for (int run : new int[]{1, 2, 3, 4}) ms.add(madao(w, run));
        for (int[] m : ms) if (m == null) return false;
        for (int[] m : ms) if (!madaoHits(w, m, rooms).isEmpty()) return false;
        return true;
    }

    static String doors(Map<String, Object> r) {
        return Compose.doorsOpen(r)[0];
    }

    static boolean opensBack(Map<String, Object> r) {
        String d = doors(r);
        return (d.equals("front_back") || d.equals("four")) && !"chengtai".equals(r.get("podium_style"));
    }

    public static String[] axisOpen(Map<String, Object> r, int cx) {
        String[] pair = Compose.doorsOpen(r);
        if (t(r, "special") || !(pair[0].equals("front") && pair[1].equals("closed")) || "chengtai".equals(r.get("podium_style"))
                || Math.floorDiv(gi(r, "x0") + gi(r, "x1"), 2) != cx || !("south".equals(r.get("front")) || "north".equals(r.get("front"))))
            return pair;
        return new String[]{gi(r, "x1") - gi(r, "x0") == gi(r, "z1") - gi(r, "z0") || "pyramid".equals(r.get("roof")) ? "four" : "front_back", "closed"};
    }

    public static boolean hit(int[] a, int[] b) {
        return a[0] <= b[2] && b[0] <= a[2] && a[1] <= b[3] && b[1] <= a[3];
    }

    static boolean joined(Map<String, Object> a, Map<String, Object> b) {
        if ("chengtai".equals(a.get("podium_style")) && "chengtai".equals(b.get("podium_style")) && pod(a, "podium") != 0
                && pod(a, "podium") == pod(b, "podium")) {
            int[] fa = foot(a), fb = foot(b);
            return fa[0] <= fb[2] + 1 && fb[0] <= fa[2] + 1 && fa[1] <= fb[3] + 1 && fb[1] <= fa[3] + 1;
        }
        return java.util.Objects.equals(a.get("front"), b.get("front")) && (
                gi(a, "x0") == gi(b, "x0") && gi(a, "x1") == gi(b, "x1") && (gi(a, "z1") + 1 == gi(b, "z0") || gi(b, "z1") + 1 == gi(a, "z0"))
                        || gi(a, "z0") == gi(b, "z0") && gi(a, "z1") == gi(b, "z1") && (gi(a, "x1") + 1 == gi(b, "x0") || gi(b, "x1") + 1 == gi(a, "x0")));
    }

    static boolean abut(Map<String, Object> a, Map<String, Object> b) {
        return in(gi(a, "x0"), gi(a, "x1"), gi(b, "x0"), gi(b, "x1")) && (gi(a, "z1") + 1 == gi(b, "z0") || gi(b, "z1") + 1 == gi(a, "z0"))
                || in(gi(a, "z0"), gi(a, "z1"), gi(b, "z0"), gi(b, "z1")) && (gi(a, "x1") + 1 == gi(b, "x0") || gi(b, "x1") + 1 == gi(a, "x0"));
    }

    private static boolean in(int p0, int p1, int q0, int q1) {
        return q0 <= p0 && p1 <= q1 || p0 <= q0 && q1 <= p1;
    }

    /** crowded: ("steps" | "touch", frame a, frame b) - as lists [kind, [x0, z0, x1, z1], [...]]. */
    public static List<Object[]> crowded(List<Map<String, Object>> rooms, int strip) {
        List<Map<String, Object>> blds = new ArrayList<>(), solid = new ArrayList<>();
        for (Map<String, Object> r : rooms) {
            String k = kind(r);
            if ((k.isEmpty() || k.equals("round")) && !t(r, "stack") && r.get("_on") == null) blds.add(r);
        }
        for (Map<String, Object> r : rooms) if (SOLID_SPECIALS.contains(kind(r))) solid.add(r);
        int[] grp = new int[blds.size()];
        for (int i = 0; i < grp.length; i++) grp[i] = i;
        java.util.function.IntUnaryOperator top = i0 -> {
            int i = i0;
            while (grp[i] != i) {
                grp[i] = grp[grp[i]];
                i = grp[i];
            }
            return i;
        };
        for (int i = 0; i < blds.size(); i++)
            for (int j = i + 1; j < blds.size(); j++)
                if (abut(blds.get(i), blds.get(j))) grp[top.applyAsInt(i)] = top.applyAsInt(j);
        IdentityHashMap<Map<String, Object>, Integer> gid = new IdentityHashMap<>();
        for (int i = 0; i < blds.size(); i++) gid.put(blds.get(i), top.applyAsInt(i));
        IdentityHashMap<Map<String, Object>, List<int[]>> flights = new IdentityHashMap<>();
        for (Map<String, Object> r : blds) flights.put(r, walkRects(r, 0));
        List<Object[]> out = new ArrayList<>();
        for (int i = 0; i < blds.size(); i++) {
            Map<String, Object> a = blds.get(i);
            for (int[] w : walkRects(a, strip)) {
                for (Map<String, Object> b : blds) {
                    if (b == a || gid.get(a).equals(gid.get(b))) continue;
                    boolean h = hit(w, foot(b));
                    if (!h) for (int[] f : flights.get(b))
                        if (hit(w, f)) {
                            h = true;
                            break;
                        }
                    if (h) out.add(new Object[]{"steps", r4(a), r4(b)});
                }
                for (Map<String, Object> b : solid) {
                    int g = kind(b).equals("river") ? 0 : 1;
                    if (hit(w, new int[]{gi(b, "x0") - g, gi(b, "z0") - g, gi(b, "x1") + g, gi(b, "z1") + g}) && !hit(r4(a), r4(b)))
                        out.add(new Object[]{"steps", r4(a), r4(b)});
                }
            }
            for (Map<String, Object> b : blds.subList(i + 1, blds.size()))
                if (!joined(a, b) && !gid.get(a).equals(gid.get(b)) && hit(eaveZone(a), eaveZone(b))) out.add(new Object[]{"touch", r4(a), r4(b)});
        }
        for (Map<String, Object> w : rooms) {
            int[] m = madao(w, null);
            if (m != null) for (Map<String, Object> b : madaoHits(w, m, rooms)) out.add(new Object[]{"steps", r4(w), r4(b)});
        }
        return out;
    }

    static void fitFrame(Map<String, Object> r) {
        for (int i = 0; i < 3; i++) {
            int m = reach(r);
            int w = gi(r, "x1") - gi(r, "x0") + 1, d = gi(r, "z1") - gi(r, "z0") + 1;
            if (w - 2 * m >= 5 && d - 2 * m >= 5) break;
            if (t(r, "colonnade") || t(r, "veranda") || t(r, "back_veranda")) {
                r.put("colonnade", false);
                r.put("veranda", false);
                r.put("back_veranda", false);
            } else if ("tiers".equals(r.get("podium_style")) || "chengtai".equals(r.get("podium_style"))) {
                r.put("podium_style", "plain");
            } else if (pod(r, "overhang") != 1) {
                r.put("overhang", 1);
                r.put("brackets", 0);
            }
        }
        String fr = sget(r, "front", "south");
        boolean ns = fr.equals("south") || fr.equals("north");
        String a = ns ? "z0" : "x0", b = ns ? "z1" : "x1";
        String o0 = a.equals("z0") ? "x0" : "z0", o1 = a.equals("z0") ? "x1" : "z1";
        int across = gi(r, o1) - gi(r, o0) + 1, deep = gi(r, b) - gi(r, a) + 1;
        int side = Math.min(reach(r), Math.floorDiv(across - 5, 2));
        int back = Math.max(0, Math.min(reach(r), Math.floorDiv(deep - 5, 2)));
        int front = Math.max(0, Math.min(Math.max(reach(r), steps(r)), deep - 5 - back));
        if (Math.floorMod(deep - back - front, 2) == 0) {
            if (deep - back - front - 1 >= 5) front += 1;
            else back -= 1;
        }
        if (side > 0) {
            r.put(o0, gi(r, o0) + side);
            r.put(o1, gi(r, o1) - side);
        }
        boolean se = fr.equals("south") || fr.equals("east");
        int nb = se ? back : front, nf = se ? front : back;
        r.put(a, gi(r, a) + nb);
        r.put(b, gi(r, b) - nf);
    }

}
