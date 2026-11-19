package com.flyingeaves.gen;

import com.flyingeaves.gen.py.IntPair;
import com.flyingeaves.gen.py.PySet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * joinparts.py: separately generated parts (rooms of a composed building, a house and its wing) joined into one
 * canvas - a part owns its column inside its footprint; outside, the highest roof wins; platforms go to the nearest
 * part; seams get rails. A part is a map: blocks, cols, fp (x0, z0, x1, z1), y, wall_top, top, optional mask (a set of
 * (x, z)) and floor.
 */
public final class Joinparts {
    private Joinparts() {
    }

    @SuppressWarnings("unchecked")
    static LinkedHashMap<Long, String> blocks(Map<String, Object> p) {
        return (LinkedHashMap<Long, String>) p.get("blocks");
    }

    @SuppressWarnings("unchecked")
    static Map<Long, List<Canvas.Run>> cols(Map<String, Object> p) {
        Object c = p.get("cols");
        return c == null ? Map.of() : (Map<Long, List<Canvas.Run>>) c;
    }

    static int gi(Map<String, Object> p, String k) {
        return Dyn.toInt(p.get(k));
    }

    /** joinparts.join. who: filled with the part index each block came from (keys: Canvas.key). */
    @SuppressWarnings("unchecked")
    public static void join(Canvas cv, List<Map<String, Object>> parts, Map<Long, Integer> who) {
        Map<Long, List<Integer>> colparts = new HashMap<>();
        for (int i = 0; i < parts.size(); i++) {
            Map<String, Object> p = parts.get(i);
            if (p.get("mask") != null) {
                for (IntPair c : (Iterable<IntPair>) p.get("mask")) colparts.computeIfAbsent(Canvas.ckey(c.a(), c.b()), k -> new ArrayList<>()).add(i);
            } else {
                int[] fp = (int[]) p.get("fp");
                for (int x = fp[0]; x <= fp[2]; x++)
                    for (int z = fp[1]; z <= fp[3]; z++) colparts.computeIfAbsent(Canvas.ckey(x, z), k -> new ArrayList<>()).add(i);
            }
        }
        Ctx c = new Ctx(parts, colparts);
        Map<Long, int[]> roofOwner = new HashMap<>();
        for (int i = 0; i < parts.size(); i++) {
            Map<String, Object> p = parts.get(i);
            int wt = gi(p, "wall_top");
            for (long k : blocks(p).keySet()) {
                int x = Canvas.kx(k), y = Canvas.ky(k), z = Canvas.kz(k);
                if (y > wt && c.outsideAll(x, z)) {
                    long ck = Canvas.ckey(x, z);
                    int[] cur = roofOwner.get(ck);
                    if (cur == null || y >= cur[0]) roofOwner.put(ck, new int[]{y, i});
                }
            }
        }
        Set<List<Integer>> lost = new HashSet<>();
        for (int i = 0; i < parts.size(); i++) {
            Map<String, Object> p = parts.get(i);
            if (!p.containsKey("floor")) continue;
            int fl = gi(p, "floor");
            LinkedHashMap<Long, String> bl = blocks(p);
            List<Long> at3 = new ArrayList<>();
            for (long k : bl.keySet()) if (Canvas.ky(k) == fl + 3) at3.add(k);
            for (long k : at3) {
                int x = Canvas.kx(k), y = Canvas.ky(k), z = Canvas.kz(k);
                if (c.outsideAll(x, z) && !c.nearOk(i, x, fl + 2, z)) {
                    while (true) {
                        String s = bl.get(Canvas.key(x, y, z));
                        if (s == null || s.equals(Blocks.AIR)) break;
                        lost.add(List.of(i, x, y, z));
                        y++;
                    }
                }
            }
        }
        for (int i = 0; i < parts.size(); i++) {
            Map<String, Object> p = parts.get(i);
            int wt = gi(p, "wall_top");
            for (Map.Entry<Long, String> e : blocks(p).entrySet()) {
                long k = e.getKey();
                int x = Canvas.kx(k), y = Canvas.ky(k), z = Canvas.kz(k);
                Integer o = c.owner(x, y, z);
                if (o != null && o != i) continue;
                if (!c.nearOk(i, x, y, z) || (!lost.isEmpty() && lost.contains(List.of(i, x, y, z)))) continue;
                if (y > wt && c.outsideAll(x, z) && roofOwner.get(Canvas.ckey(x, z))[1] != i) continue;
                cv.set(x, y, z, e.getValue());
                if (who != null) who.put(Canvas.key(x, y, z), i);
            }
            for (Map.Entry<Long, List<Canvas.Run>> e : cols(p).entrySet()) {
                long key = e.getKey();
                int x = Canvas.cx(key), z = Canvas.cz(key);
                List<Canvas.Run> keep = new ArrayList<>();
                for (Canvas.Run r : e.getValue()) {
                    int lo = r.y0(), y1 = r.y1();
                    if (p.containsKey("floor")) {
                        int fl = gi(p, "floor");
                        if (!c.nearOk(i, x, fl + 2, z)) lo = Math.max(lo, fl + 3);
                        for (int j : colparts.getOrDefault(key, List.of())) {
                            Map<String, Object> q = parts.get(j);
                            if (j != i && lo <= gi(q, "top") && y1 >= gi(q, "y") + 1) {
                                if (lo >= gi(q, "y") + 1) lo = gi(q, "top") + 1;
                                else y1 = Math.min(y1, gi(q, "y"));
                            }
                        }
                    }
                    if (lo <= y1) keep.add(new Canvas.Run(lo, y1, r.s()));
                }
                cv.colsAt(key).addAll(keep);
            }
        }
        seamRails(cv, parts, c);
    }

    static final class Ctx {
        final List<Map<String, Object>> parts;
        final Map<Long, List<Integer>> colparts;
        final Map<Long, Set<Integer>> nearest = new HashMap<>();

        Ctx(List<Map<String, Object>> parts, Map<Long, List<Integer>> colparts) {
            this.parts = parts;
            this.colparts = colparts;
        }

        Integer owner(int x, int y, int z) {
            Integer best = null;
            for (int i : colparts.getOrDefault(Canvas.ckey(x, z), List.of())) {
                Map<String, Object> p = parts.get(i);
                if (gi(p, "y") + 1 <= y && y <= gi(p, "top")) best = i;
            }
            return best;
        }

        boolean outsideAll(int x, int z) {
            return !colparts.containsKey(Canvas.ckey(x, z));
        }

        static int dist(Map<String, Object> p, int x, int z) {
            int[] fp = (int[]) p.get("fp");
            return Math.max(Math.max(fp[0] - x, x - fp[2]), 0) + Math.max(Math.max(fp[1] - z, z - fp[3]), 0);
        }

        boolean nearOk(int i, int x, int y, int z) {
            Map<String, Object> p = parts.get(i);
            if (!p.containsKey("floor") || y > gi(p, "floor") + 2 || !outsideAll(x, z)) return true;
            long key = Canvas.ckey(x, z);
            Set<Integer> s = nearest.get(key);
            if (s == null) {
                int[] ds = new int[parts.size()];
                int m = Integer.MAX_VALUE;
                for (int j = 0; j < parts.size(); j++) {
                    Map<String, Object> q = parts.get(j);
                    ds[j] = q.containsKey("floor") ? dist(q, x, z) : 1 << 30;
                    m = Math.min(m, ds[j]);
                }
                s = new HashSet<>();
                for (int j = 0; j < ds.length; j++) if (ds[j] == m) s.add(j);
                nearest.put(key, s);
            }
            return s.contains(i);
        }
    }

    /** joinparts._rail_name. */
    static String railName(Map<String, Object> p, Ctx c) {
        LinkedHashMap<String, Integer> cnt = new LinkedHashMap<>();
        int py = gi(p, "y"), fl = gi(p, "floor");
        for (Map.Entry<Long, String> e : blocks(p).entrySet()) {
            long k = e.getKey();
            int y = Canvas.ky(k);
            if (py < y && y <= fl + 1 && c.outsideAll(Canvas.kx(k), Canvas.kz(k))) {
                String name = Blocks.name(e.getValue());
                if (name.endsWith("_wall") || (name.endsWith("_fence") && !name.contains("nether"))) cnt.merge(name, 1, Integer::sum);
            }
        }
        if (cnt.isEmpty()) return null;
        String best = null;
        int bv = 0;
        for (Map.Entry<String, Integer> e : cnt.entrySet())
            if (best == null || e.getValue() > bv) {
                best = e.getKey();
                bv = e.getValue();
            }
        return bv >= 8 ? best : null;
    }

    static Integer top(Canvas cv, int x, int z, int below) {
        Integer best = null;
        for (int y = below; y > below - 16; y--) {
            String s = cv.blocks.get(Canvas.key(x, y, z));
            if (s != null && !s.endsWith(":air")) {
                best = y;
                break;
            }
        }
        List<Canvas.Run> rs = cv.cols.get(Canvas.ckey(x, z));
        if (rs != null)
            for (Canvas.Run r : rs)
                if (r.y0() <= below) {
                    int t = Math.min(r.y1(), below);
                    if (best == null || t > best) best = t;
                }
        return best;
    }

    /** joinparts.seam_rails. */
    static void seamRails(Canvas cv, List<Map<String, Object>> parts, Ctx c) {
        LinkedHashMap<Long, String> placed = new LinkedHashMap<>();
        for (int i = 0; i < parts.size(); i++) {
            Map<String, Object> p = parts.get(i);
            if (!p.containsKey("floor")) continue;
            String rail = railName(p, c);
            if (rail == null) continue;
            int fl = gi(p, "floor"), py = gi(p, "y");
            PySet<IntPair> a = new PySet<>();
            for (long k : blocks(p).keySet()) if (Canvas.ky(k) <= fl && c.outsideAll(Canvas.kx(k), Canvas.kz(k))) a.add(IntPair.of(Canvas.kx(k), Canvas.kz(k)));
            PySet<IntPair> b = new PySet<>();
            for (long k : cols(p).keySet()) b.add(IntPair.of(Canvas.cx(k), Canvas.cz(k)));
            PySet<IntPair> colsSet = a.or(b);
            for (IntPair xz : colsSet) {
                int x = xz.a(), z = xz.b();
                if (!c.outsideAll(x, z) || !c.nearOk(i, x, fl, z)) continue;
                Integer t = top(cv, x, z, fl);
                if (t == null || t < py + 2) continue;
                String s = cv.blocks.getOrDefault(Canvas.key(x, t, z), "");
                String above = cv.blocks.get(Canvas.key(x, t + 1, z));
                if (s.contains("stairs") || s.contains("slab") || !(above == null || above.equals(Blocks.AIR))) continue;
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int nx = x + d[0], nz = z + d[1];
                    if (!c.outsideAll(nx, nz) || c.nearOk(i, nx, fl, nz)) continue;
                    Integer tn = top(cv, nx, nz, t);
                    if ((tn != null ? tn : py) <= t - 2) {
                        placed.put(Canvas.key(x, t + 1, z), rail);
                        break;
                    }
                }
            }
        }
        for (Map.Entry<Long, String> e : placed.entrySet()) {
            long k = e.getKey();
            int x = Canvas.kx(k), y = Canvas.ky(k), z = Canvas.kz(k);
            String name = e.getValue();
            Map<String, String> props = new LinkedHashMap<>();
            Object[][] ds = {{"east", 1, 0}, {"west", -1, 0}, {"south", 0, 1}, {"north", 0, -1}};
            for (Object[] d : ds) {
                long nk = Canvas.key(x + (int) d[1], y, z + (int) d[2]);
                String nb = placed.get(nk);
                if (nb == null || nb.isEmpty()) {
                    String bb = cv.blocks.get(nk);
                    nb = Blocks.name(bb == null ? "" : bb);
                }
                boolean same = nb.equals(name);
                props.put((String) d[0], name.endsWith("_wall") ? (same ? "low" : "none") : (same ? "true" : "false"));
            }
            if (name.endsWith("_wall")) props.put("up", "true");
            else props.put("waterlogged", "false");
            cv.set(x, y, z, Blocks.withProps(name, props));
        }
    }
}
