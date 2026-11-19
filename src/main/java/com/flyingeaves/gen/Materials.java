package com.flyingeaves.gen;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** materials.py: wall styles and roof themes as data (data/materials/*.json, then the player's run/materials). */
public final class Materials {
    private Materials() {
    }

    /** materials.pick: the first candidate the game has; the last one if none is. */
    @SuppressWarnings("unchecked")
    static String pick(Object cands) {
        if (cands instanceof String s) return s;
        List<Object> l = (List<Object>) cands;
        for (Object c : l) if (Env.present((String) c)) return (String) c;
        return (String) l.get(l.size() - 1);
    }

    @SuppressWarnings("unchecked")
    static LinkedHashMap<String, Map<String, Object>> load(String section) {
        LinkedHashMap<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (Path d : Env.dirs("materials")) {
            for (Path p : Env.glob(d, "")) {
                Map<String, Object> data = Json.parseObject(Env.read(p));
                Object sec = data.get(section);
                if (!(sec instanceof Map)) continue;
                for (Map.Entry<String, Object> e : ((Map<String, Object>) sec).entrySet()) {
                    Map<String, Object> merged = new LinkedHashMap<>(out.getOrDefault(e.getKey(), new LinkedHashMap<>()));
                    merged.putAll((Map<String, Object>) e.getValue());
                    out.put(e.getKey(), merged);
                }
            }
        }
        for (String name : new ArrayList<>(out.keySet())) {
            Set<Object> seen = new HashSet<>();
            seen.add(name);
            Map<String, Object> e = out.get(name);
            while (e.get("inherit") != null && out.containsKey(e.get("inherit")) && !seen.contains(e.get("inherit"))) {
                Object inh = e.get("inherit");
                seen.add(inh);
                Map<String, Object> base = new LinkedHashMap<>(out.get(inh));
                for (Map.Entry<String, Object> kv : e.entrySet()) if (!kv.getKey().equals("inherit")) base.put(kv.getKey(), kv.getValue());
                base.put("inherit", out.get(inh).get("inherit"));
                e = base;
            }
            e = new LinkedHashMap<>(e);
            e.remove("inherit");
            out.put(name, e);
        }
        return out;
    }

    private static Object or(Map<String, Object> e, String k, Object dflt) {
        Object v = e.get(k);
        return Dyn.truthy(v) ? v : dflt;
    }

    /** materials.walls(). */
    public static LinkedHashMap<String, Map<String, Object>> walls() {
        LinkedHashMap<String, Map<String, Object>> res = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> en : load("walls").entrySet()) {
            Map<String, Object> e = en.getValue();
            Map<String, Object> w = new LinkedHashMap<>();
            w.put("post", pick(e.get("post")));
            w.put("fill", pick(e.get("fill")));
            w.put("base", pick(e.get("base")));
            w.put("beam", pick(e.get("beam")));
            w.put("floor", pick(e.get("floor")));
            w.put("win", pick(e.get("shutter")));
            w.put("door", pick(e.get("door")));
            w.put("pane", pick(e.get("pane")));
            w.put("cn", e.getOrDefault("cn", en.getKey()));
            w.put("swatch", e.getOrDefault("swatch", ""));
            w.put("dado", pick(or(e, "dado", e.get("base"))));
            w.put("trim_stairs", pick(or(e, "trim_stairs", List.of("minecraft:spruce_stairs"))));
            w.put("trim_slab", pick(or(e, "trim_slab", List.of("minecraft:spruce_slab"))));
            w.put("trim_trapdoor", pick(or(e, "trim_trapdoor", List.of("minecraft:spruce_trapdoor"))));
            w.put("base_stairs", pick(or(e, "base_stairs", List.of("minecraft:stone_brick_stairs"))));
            w.put("rail", pick(or(e, "rail", List.of("minecraft:spruce_fence"))));
            w.put("steps", pick(or(e, "steps", List.of("minecraft:stone_brick_stairs"))));
            w.put("inner_stairs", pick(or(e, "inner_stairs", List.of("minecraft:spruce_stairs"))));
            w.put("ladder", pick(or(e, "ladder", List.of("minecraft:ladder"))));
            res.put(en.getKey(), w);
        }
        return res;
    }

    /** materials.roofs(). */
    public static LinkedHashMap<String, Map<String, Object>> roofs() {
        LinkedHashMap<String, Map<String, Object>> res = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> en : load("roofs").entrySet()) {
            Map<String, Object> e = en.getValue();
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("tile", "roof:" + en.getKey());
            r.put("stairs", pick(e.get("stairs")));
            r.put("slab", pick(e.get("slab")));
            r.put("full", pick(e.get("full")));
            r.put("ridge", pick(e.get("ridge")));
            r.put("cap", pick(e.get("cap")));
            r.put("corner_lift", e.getOrDefault("corner_lift", Boolean.TRUE));
            r.put("cn", e.getOrDefault("cn", en.getKey()));
            r.put("swatch", e.getOrDefault("swatch", ""));
            r.put("stripe_stairs", Dyn.truthy(e.get("stripe_stairs")) ? pick(e.get("stripe_stairs")) : null);
            r.put("stripe_slab", Dyn.truthy(e.get("stripe_slab")) ? pick(e.get("stripe_slab")) : null);
            res.put(en.getKey(), r);
        }
        return res;
    }
}
