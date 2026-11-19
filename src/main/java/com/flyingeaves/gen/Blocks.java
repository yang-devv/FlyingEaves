package com.flyingeaves.gen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Block states as canonical strings {@code minecraft:x[k=v,...]} (arch.B, arch.parse, arch.rotate_state,
 * arch.mirror_state, arch.stair, arch.slab). Pure strings: nothing here knows Minecraft's classes.
 */
public final class Blocks {
    private Blocks() {
    }

    public static final Map<String, int[]> DIRV = new LinkedHashMap<>();
    public static final Map<String, String> OPP = Map.of("north", "south", "south", "north", "west", "east", "east", "west");
    /** world x = a*u + b*v, z = c*u + d*v. */
    public static final Map<String, int[]> ROT = Map.of("south", new int[]{1, 0, 0, 1}, "north", new int[]{-1, 0, 0, -1},
            "east", new int[]{0, 1, -1, 0}, "west", new int[]{0, -1, 1, 0});

    static {
        DIRV.put("north", new int[]{0, -1});
        DIRV.put("south", new int[]{0, 1});
        DIRV.put("west", new int[]{-1, 0});
        DIRV.put("east", new int[]{1, 0});
    }

    /** Python's {@code str(v).lower() if isinstance(v, bool) else v} in an f-string. */
    static String val(Object v) {
        if (v instanceof Boolean b) return b ? "true" : "false";
        if (v instanceof Double d) return com.flyingeaves.gen.py.Py.repr(d);
        return String.valueOf(v);
    }

    /** {@code B(id, k1=v1, k2=v2, ...)}: kv alternates keys and values. */
    public static String B(String id, Object... kv) {
        if (!id.contains(":")) id = "minecraft:" + id;
        if (kv.length == 0) return id;
        TreeMap<String, String> m = new TreeMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], val(kv[i + 1]));
        return join(id, m);
    }

    /** {@code B(id, **props)}. */
    public static String B(String id, Map<String, ?> props) {
        if (!id.contains(":")) id = "minecraft:" + id;
        if (props.isEmpty()) return id;
        TreeMap<String, String> m = new TreeMap<>();
        for (Map.Entry<String, ?> e : props.entrySet()) m.put(e.getKey(), val(e.getValue()));
        return join(id, m);
    }

    static String join(String name, Map<String, String> sorted) {
        StringBuilder b = new StringBuilder(name).append('[');
        boolean first = true;
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            if (!first) b.append(',');
            first = false;
            b.append(e.getKey()).append('=').append(e.getValue());
        }
        return b.append(']').toString();
    }

    /** {@code name + "[" + ",".join(f"{k}={v}" for k, v in sorted(props.items())) + "]"}. */
    public static String withProps(String name, Map<String, String> props) {
        return join(name, new TreeMap<>(props));
    }

    /** {@code arch.parse}: (name, props in their order). */
    public static Parsed parse(String s) {
        int i = s.indexOf('[');
        if (i < 0) return new Parsed(s, new LinkedHashMap<>());
        LinkedHashMap<String, String> p = new LinkedHashMap<>();
        for (String kv : s.substring(i + 1, s.length() - 1).split(",", -1)) {
            String[] t = kv.split("=", -1);
            if (t.length != 2) throw new IllegalArgumentException("dictionary update sequence element has length " + t.length + ": " + s);
            p.put(t[0], t[1]);
        }
        return new Parsed(s.substring(0, i), p);
    }

    public record Parsed(String name, LinkedHashMap<String, String> props) {
    }

    /** Python {@code s.split("[")[0]}. */
    public static String name(String s) {
        int i = s.indexOf('[');
        return i < 0 ? s : s.substring(0, i);
    }

    // ------------------------------------------------------------------------------------------ stairs / slabs

    /** Named material sets whose stairs and slab are not "<name>_stairs" / "<name>_slab" (roof themes). */
    public static final Map<String, String[]> SETS = new HashMap<>();

    public static String stair(String mat, String facing) {
        return stair(mat, facing, "bottom", null);
    }

    public static String stair(String mat, String facing, String half) {
        return stair(mat, facing, half, null);
    }

    public static String stair(String mat, String facing, String half, String shape) {
        String id = SETS.containsKey(mat) ? SETS.get(mat)[0] : mat + "_stairs";
        if (shape != null && !shape.isEmpty()) return B(id, "facing", facing, "half", half, "shape", shape);
        return B(id, "facing", facing, "half", half);
    }

    public static String slab(String mat) {
        return slab(mat, "bottom");
    }

    public static String slab(String mat, String typ) {
        return B(SETS.containsKey(mat) ? SETS.get(mat)[1] : mat + "_slab", "type", typ);
    }

    public static final String AIR = "minecraft:air";

    // ------------------------------------------------------------------------------------------ rotation

    static final Map<Long, String> DIR_NAME = new HashMap<>();

    static {
        DIR_NAME.put(pk(0, -1), "north");
        DIR_NAME.put(pk(0, 1), "south");
        DIR_NAME.put(pk(-1, 0), "west");
        DIR_NAME.put(pk(1, 0), "east");
    }

    private static long pk(int a, int b) {
        return ((long) a << 32) ^ (b & 0xffffffffL);
    }

    /** {@code arch._world_dir(front, d)}. */
    public static String worldDir(String front, String d) {
        int[] r = ROT.get(front);
        int[] dv = DIRV.get(d);
        int x = r[0] * dv[0] + r[1] * dv[1], z = r[2] * dv[0] + r[3] * dv[1];
        return DIR_NAME.get(pk(x, z));
    }

    private static final Map<String, String> ROT_CACHE = new HashMap<>();

    /** {@code arch.rotate_state(s, front)}. */
    public static String rotateState(String s, String front) {
        if (front.equals("south")) return s;
        String key = s + "\u0000" + front;
        String r = ROT_CACHE.get(key);
        if (r != null) return r;
        Parsed p = parse(s);
        if (p.props.isEmpty()) {
            ROT_CACHE.put(key, s);
            return s;
        }
        LinkedHashMap<String, String> q = new LinkedHashMap<>();
        LinkedHashMap<String, String> sides = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : p.props.entrySet()) {
            String k = e.getKey(), v = e.getValue();
            if (k.equals("facing") && DIRV.containsKey(v)) q.put(k, worldDir(front, v));
            else if (k.equals("axis") && (v.equals("x") || v.equals("z")) && (front.equals("east") || front.equals("west")))
                q.put(k, v.equals("x") ? "z" : "x");
            else if (DIRV.containsKey(k)) sides.put(worldDir(front, k), v);
            else q.put(k, v);
        }
        q.putAll(sides);
        r = withProps(p.name, q);
        ROT_CACHE.put(key, r);
        return r;
    }

    private static final Map<String, String> SWAP_X = Map.of("east", "west", "west", "east");
    private static final Map<String, String> SWAP_Z = Map.of("north", "south", "south", "north");
    private static final Map<String, String> SWAP_SHAPE = Map.of("inner_left", "inner_right", "inner_right", "inner_left",
            "outer_left", "outer_right", "outer_right", "outer_left");

    /** {@code arch.mirror_state(s, mx, mz)}. */
    public static String mirrorState(String s, boolean mx, boolean mz) {
        if (!(mx || mz) || !s.contains("[")) return s;
        Parsed p = parse(s);
        LinkedHashMap<String, String> q = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : p.props.entrySet()) {
            String k = e.getKey(), v = e.getValue();
            if (k.equals("facing")) {
                if (mx) v = SWAP_X.getOrDefault(v, v);
                if (mz) v = SWAP_Z.getOrDefault(v, v);
            } else if (k.equals("shape") && mx != mz) v = SWAP_SHAPE.getOrDefault(v, v);
            else if (k.equals("hinge") && mx != mz) v = v.equals("left") ? "right" : v.equals("right") ? "left" : v;
            if (mx && SWAP_X.containsKey(k)) k = SWAP_X.get(k);
            else if (mz && SWAP_Z.containsKey(k)) k = SWAP_Z.get(k);
            q.put(k, v);
        }
        return withProps(p.name, q);
    }

    /** Python {@code any(t in s for t in ts)}. */
    public static boolean containsAny(String s, String... ts) {
        if (s == null) return false;
        for (String t : ts) if (s.contains(t)) return true;
        return false;
    }

    public static boolean startsWithAny(String s, String... ts) {
        for (String t : ts) if (s.startsWith(t)) return true;
        return false;
    }

    public static boolean endsWithAny(String s, String... ts) {
        for (String t : ts) if (s.endsWith(t)) return true;
        return false;
    }

    public static List<String> list(String... a) {
        return new ArrayList<>(List.of(a));
    }
}
