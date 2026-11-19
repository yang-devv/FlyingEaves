package com.flyingeaves.gen;

import com.flyingeaves.gen.py.PyRandom;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** parts.py: furniture pieces, light fixtures, wall bays and roof ornaments as data (data/parts, run/parts). */
public final class Parts {
    private Parts() {
    }

    /** A furniture piece: draws itself into a frame (local u across, k up from the floor, v into the room). */
    public interface Piece {
        void draw(Fr f, PyRandom rng);
    }

    public record PieceDef(Piece fn, int w, int d) {
    }

    /** A variant cell: (u, k, v, state). */
    record VCell(int u, int k, int v, String s) {
    }

    /** parts.load: {name: (cn, w, d, variants)}. */
    static LinkedHashMap<String, Object[]> load() {
        LinkedHashMap<String, Object[]> out = new LinkedHashMap<>();
        for (Path d : Env.dirs("parts")) {
            for (Path p : Env.glob(d, "")) {
                Map<String, Object> data = Json.parseObject(Env.read(p));
                Object ps = data.get("parts");
                if (!(ps instanceof Map)) continue;
                for (Map.Entry<String, Object> en : Dyn.map(ps).entrySet()) {
                    Map<String, Object> e = Dyn.map(en.getValue());
                    List<Object> size = Dyn.list(e.get("size"));
                    int w = Dyn.toInt(size.get(0)), dd = Dyn.toInt(size.get(1));
                    List<List<VCell>> variants = new ArrayList<>();
                    for (Object vo : e.get("variants") == null ? List.of() : Dyn.list(e.get("variants"))) {
                        List<VCell> var = new ArrayList<>();
                        boolean ok = true;
                        for (Object co : Dyn.list(vo)) {
                            List<Object> c = Dyn.list(co);
                            String s = (String) c.get(3);
                            if (!Env.present(s)) ok = false;
                            var.add(new VCell(Dyn.toInt(c.get(0)), Dyn.toInt(c.get(1)), Dyn.toInt(c.get(2)), s));
                        }
                        if (ok) variants.add(var);
                    }
                    if (variants.isEmpty()) continue;
                    String name = en.getKey();
                    if (out.containsKey(name) && !Dyn.truthy(e.get("replace"))) {
                        @SuppressWarnings("unchecked") List<List<VCell>> have = (List<List<VCell>>) out.get(name)[3];
                        have.addAll(variants);
                        continue;
                    }
                    out.put(name, new Object[]{e.getOrDefault("cn", name), w, dd, new ArrayList<>(variants)});
                }
            }
        }
        return out;
    }

    private static LinkedHashMap<String, Map<String, Object>> loadSection(String prefix, String section, java.util.function.Predicate<Map<String, Object>> keep) {
        LinkedHashMap<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (Path d : Env.dirs("parts")) {
            for (Path p : Env.glob(d, prefix)) {
                Map<String, Object> data = Json.parseObject(Env.read(p));
                Object sec = data.get(section);
                if (!(sec instanceof Map)) continue;
                for (Map.Entry<String, Object> en : Dyn.map(sec).entrySet()) {
                    Map<String, Object> e = Dyn.map(en.getValue());
                    if (keep.test(e)) out.put(en.getKey(), new LinkedHashMap<>(e));
                }
            }
        }
        return out;
    }

    public static final LinkedHashMap<String, Map<String, Object>> LIGHTS = new LinkedHashMap<>();
    public static final LinkedHashMap<String, Map<String, Object>> BAYS = new LinkedHashMap<>();
    public static final LinkedHashMap<String, Map<String, Object>> ORNAMENTS = new LinkedHashMap<>();

    /** parts.load_lights. */
    static LinkedHashMap<String, Map<String, Object>> loadLights() {
        return loadSection("lights", "lights", e -> {
            for (Object o : e.get("blocks") == null ? List.of() : Dyn.list(e.get("blocks"))) {
                String s = (String) Dyn.list(o).get(1);
                if (!(s.startsWith("$") || Env.present(s))) return false;
            }
            return true;
        });
    }

    /** parts.load_bays. */
    static LinkedHashMap<String, Map<String, Object>> loadBays() {
        return loadSection("bays", "bays", e -> {
            Object key = e.get("key");
            if (Dyn.truthy(key)) for (Object v : Dyn.map(key).values()) if (!Env.present((String) v)) return false;
            return true;
        });
    }

    /** parts.load_ornaments. */
    static LinkedHashMap<String, Map<String, Object>> loadOrnaments() {
        return loadSection("ornaments", "ornaments", e -> {
            for (Object o : e.get("cells") == null ? List.of() : Dyn.list(e.get("cells"))) {
                String s = (String) Dyn.list(o).get(3);
                if (!(s.startsWith("$") || Env.present(s))) return false;
            }
            return true;
        });
    }

    public static void reloadOrnaments() {
        ORNAMENTS.clear();
        ORNAMENTS.putAll(loadOrnaments());
    }

    public static void reloadBays() {
        BAYS.clear();
        BAYS.putAll(loadBays());
    }

    public static void reloadLights() {
        LIGHTS.clear();
        LIGHTS.putAll(loadLights());
    }

    /** One row of a bay: (wall codes, out codes). */
    public record Row(String wall, String out) {
    }

    /** parts.bay_rows: the rows for a segment width x h, bottom up, or null. */
    public static List<Row> bayRows(Map<String, Object> e, int width, int h) {
        List<Object> rows;
        if (e.containsKey("rows")) rows = e.get("rows") == null ? null : Dyn.list(e.get("rows"));
        else {
            Object ws = e.get("widths");
            Object r = Dyn.truthy(ws) ? Dyn.map(ws).get(String.valueOf(width)) : null;
            rows = r == null ? null : Dyn.list(r);
        }
        if (rows == null) return null;
        List<Object[]> parsed = new ArrayList<>();      // (wall, out, rep)
        for (Object ro : rows) {
            String r = (String) ro;
            boolean rep = false;
            int need = 0;
            if (r.contains("@")) {
                int at = r.lastIndexOf('@');
                need = Integer.parseInt(r.substring(at + 1).strip());
                r = r.substring(0, at);
            }
            if (r.endsWith("*")) {
                r = r.substring(0, r.length() - 1);
                rep = true;
            }
            int bar = r.indexOf('|');
            String wall = bar < 0 ? r : r.substring(0, bar), out = bar < 0 ? "" : r.substring(bar + 1);
            if (need > h) continue;
            if (wall.length() == 1) wall = wall.repeat(width);
            if (!out.isEmpty() && out.length() == 1) out = out.repeat(width);
            if (wall.length() != width || (!out.isEmpty() && out.length() != width)) return null;
            parsed.add(new Object[]{wall, out.isEmpty() ? ".".repeat(width) : out, rep});
        }
        int fixed = 0;
        List<Integer> reps = new ArrayList<>();
        for (int i = 0; i < parsed.size(); i++) {
            if ((boolean) parsed.get(i)[2]) reps.add(i);
            else fixed++;
        }
        int extra = h - fixed;
        if (extra < 0 || (extra > 0 && reps.isEmpty())) return null;
        Map<Integer, Integer> counts = new HashMap<>();
        for (int i : reps) counts.put(i, 0);
        for (int t = 0; t < extra; t++) {
            int i = reps.get(reps.size() - 1 - t % reps.size());
            counts.put(i, counts.get(i) + 1);
        }
        List<Row> res = new ArrayList<>();
        for (int i = 0; i < parsed.size(); i++) {
            Object[] p = parsed.get(i);
            int c = (boolean) p[2] ? counts.get(i) : 1;
            for (int k = 0; k < c; k++) res.add(new Row((String) p[0], (String) p[1]));
        }
        return res;
    }

    private static final Pattern CANDLES = Pattern.compile("candles=(\\d)");

    /** parts.lamp_of: (kind, n) or null. */
    static Object[] lampOf(String s) {
        if (s.startsWith("minecraft:candle") && s.contains("lit=true")) {
            Matcher m = CANDLES.matcher(s);
            return new Object[]{"table", m.find() ? Integer.parseInt(m.group(1)) : 1};
        }
        if (s.equals("minecraft:lantern") || s.equals("minecraft:lantern[hanging=false]")) return new Object[]{"stand", 1};
        return null;
    }

    /** parts.piece_fn. */
    static Piece pieceFn(List<List<VCell>> variants) {
        Map<String, Object[]> lamps = new HashMap<>();
        for (List<VCell> var : variants) for (VCell c : var) lamps.put(c.s, lampOf(c.s));
        return (f, rng) -> {
            for (VCell c : variants.get((int) rng.randrange(variants.size()))) {
                Object[] l = lamps.get(c.s);
                if (l != null) Arch.putLamp(f, (String) l[0], c.u, c.k, c.v, (int) l[1], null);
                else f.put(c.u, c.k, c.v, c.s);
            }
        };
    }

    /** parts.install. */
    @SuppressWarnings("unchecked")
    static void install(Map<String, PieceDef> pieces) {
        for (Map.Entry<String, Object[]> e : load().entrySet()) {
            Object[] v = e.getValue();
            pieces.put(e.getKey(), new PieceDef(pieceFn((List<List<VCell>>) v[3]), (int) v[1], (int) v[2]));
        }
    }
}
