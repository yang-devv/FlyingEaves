package com.flyingeaves.gen;

import com.flyingeaves.gen.py.PyRandom;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The planners' entry points: what bgen_daemon.main does for a "site" (随机一院 / 宫城 / 紫禁城 1:1 / 随机城镇 / 随机宗门)
 * or "sentence" (一句话出建筑) request - a design back, no blocks - and the oracle's plan requests (tools/oracle).
 */
@SuppressWarnings("unchecked")
public final class Planners {
    private Planners() {
    }

    /**
     * bgen_daemon.main's "site" / "sentence" branch: {ok, design, said, site} (or {ok: false, error}). rs: the random
     * source the seeds are drawn from (the daemon: the OS's randomness).
     */
    public static Map<String, Object> daemon(Map<String, Object> req, PyRandom rs) {
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            Map<String, Object> d;
            Object said;
            if ("site".equals(req.get("type"))) {
                Object which = req.get("which");
                if ("city".equals(which)) d = Siteplan.city(rs.randrange(1, 1L << 30));
                else if ("city_real".equals(which)) d = cityRealSite(rs.randrange(1, 1L << 30));
                else if ("town".equals(which)) {
                    long seed = rs.randrange(1, 1L << 62);
                    Object size = Dyn.truthy(req.get("size")) ? req.get("size") : null;
                    d = Town.town(seed, req.get("heights"), (String) size, null, req.get("box"));
                } else if ("sect".equals(which)) {
                    long seed = rs.randrange(1, 1L << 62);
                    String tier = Dyn.truthy(req.get("tier")) ? Dyn.str(req.get("tier")) : "jindan";
                    d = SectPlan.sect(seed, tier, req.get("heights"));
                } else {
                    d = Siteplan.randomize(Siteplan.siteFree(rs.randrange(1, 1L << 62)), rs);
                }
                said = new ArrayList<>(List.of(d.get("name")));
            } else {
                Object[] ds = Sentence.parse(req.containsKey("text") ? Dyn.str(req.get("text")) : "");
                d = (Map<String, Object>) ds[0];
                said = ds[1];
            }
            out.put("ok", true);
            out.put("design", d);
            out.put("said", said);
            out.put("site", "site".equals(req.get("type")));
        } catch (Platforms.NotPorted e) {
            throw e;                                         // (the game's backend sends it to Python)
        } catch (RuntimeException | StackOverflowError e) {
            out.clear();
            out.put("ok", false);
            out.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return out;
    }

    /** bgen_daemon.city_real_site. */
    public static Map<String, Object> cityRealSite(long seed) {
        Map<String, Object> d = Siteplan.cityReal(seed);
        int mz = Integer.MIN_VALUE;
        for (Map<String, Object> r : (List<Map<String, Object>>) d.get("rooms")) mz = Math.max(mz, Dyn.toInt(r.get("z1")));
        d.put("anchor", new ArrayList<>(List.of(376, mz + 1)));
        d.put("level", true);
        return d;
    }

    /** The oracle's plan request (oracle_dump.plan): fn site / city / city_real / free / site_free / crowded / town / sect / sentence / daemon. */
    public static Object plan(Map<String, Object> req) {
        String fn = (String) req.get("fn");
        switch (fn) {
            case "site":
                return Siteplan.site((String) req.get("kind"), Dyn.toInt(req.get("jin")), lg(req.get("seed")),
                        !req.containsKey("river") || Dyn.truthy(req.get("river")));
            case "city":
                return Siteplan.city(lg(req.get("seed")));
            case "city_real":
                return Siteplan.cityReal(lg(req.get("seed")), Dyn.truthy(req.get("moat")));
            case "free":
                return Siteplan.randomize(Siteplan.siteFree(lg(req.get("seed"))), PyRandom.ofInt(lg(req.get("seed"))));
            case "site_free":
                return Siteplan.siteFree(lg(req.get("seed")));
            case "crowded": {
                Map<String, Object> q = new LinkedHashMap<>(req);
                q.put("fn", req.get("of"));
                Map<String, Object> d = (Map<String, Object>) plan(q);
                int strip = req.containsKey("strip") ? Dyn.toInt(req.get("strip")) : Siteplan.WALK;
                List<Object> out = new ArrayList<>();
                for (Object[] c : Siteplan.crowded((List<Map<String, Object>>) d.get("rooms"), strip)) out.add(new ArrayList<>(List.of(c)));
                return out;
            }
            case "town":
                return Town.town(lg(req.get("seed")), req.get("heights"), (String) req.get("size"), (String) req.get("layout"), req.get("box"));
            case "sect":
                return SectPlan.sect(lg(req.get("seed")), req.containsKey("tier") ? (String) req.get("tier") : "jindan", req.get("heights"));
            case "sentence": {
                Object[] ds = Sentence.parse((String) req.get("text"));
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("design", ds[0]);
                m.put("said", ds[1]);
                return m;
            }
            case "daemon":
                return daemon((Map<String, Object>) req.get("req"), PyRandom.ofInt(lg(req.get("rs"))));
            default:
                throw new IllegalArgumentException("unknown plan fn " + fn);
        }
    }

    static long lg(Object v) {
        if (v instanceof Number n) return n.longValue();
        return Long.parseLong(String.valueOf(v).strip());
    }
}
