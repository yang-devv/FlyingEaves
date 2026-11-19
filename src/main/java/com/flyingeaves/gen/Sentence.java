package com.flyingeaves.gen;

import com.flyingeaves.gen.py.PyRandom;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.flyingeaves.gen.Siteplan.kw;

/**
 * sentence.py: 一句话出建筑 without a model - the words of a Chinese sentence picked out and turned into a 单体建造
 * design; a height asked for ("高 30 格") hit by the pipeline's search (pipeline.build / measure: compose on a canvas,
 * the ridge over the frame).
 */
public final class Sentence {
    private Sentence() {
    }

    /** Python's TypeError (a number word _num could not read, used as a number): the daemon reports its name. */
    public static final class TypeError extends RuntimeException {
        TypeError(String m) {
            super(m);
        }
    }

    static final Map<String, Integer> DIG = new LinkedHashMap<>();

    static {
        String[] k = {"零", "一", "二", "两", "三", "四", "五", "六", "七", "八", "九"};
        int[] v = {0, 1, 2, 2, 3, 4, 5, 6, 7, 8, 9};
        for (int i = 0; i < k.length; i++) DIG.put(k[i], v[i]);
    }

    static final String N = "(\\d+|[零一二两三四五六七八九十]+)";

    static Pattern re(String p) {
        return Pattern.compile(p, Pattern.UNICODE_CHARACTER_CLASS);
    }

    /** _num: '3', '三', '十一', '二十' -> int; null if it is not a number. */
    static Long num(String s) {
        if (s == null) return null;
        if (!s.isEmpty() && s.codePoints().allMatch(Character::isDigit)) {
            StringBuilder b = new StringBuilder();
            s.codePoints().forEach(c -> b.append((char) ('0' + Character.digit(c, 10))));
            BigInteger v = new BigInteger(b.toString());
            if (v.bitLength() > 40) throw new Platforms.NotPorted("a number that long in a sentence");
            return v.longValue();
        }
        int k = s.indexOf("十");
        if (k >= 0) {
            String a = s.substring(0, k), b = s.substring(k + 1);
            return (long) ((!a.isEmpty() ? DIG.getOrDefault(a, 1) : 1) * 10 + (!b.isEmpty() ? DIG.getOrDefault(b, 0) : 0));
        }
        Integer d = DIG.get(s);
        return d == null ? null : (long) d;
    }

    static long clamp(long lo, long hi, Long v) {
        if (v == null) throw new TypeError("'<' not supported between instances of 'NoneType' and 'int'");
        return Math.max(lo, Math.min(hi, v));
    }

    static long times(Long v, int k) {
        if (v == null) throw new TypeError("unsupported operand type(s) for *: 'NoneType' and 'int'");
        return v * k;
    }

    static final String[][] ROOFS = {{"十字脊", "shizi"}, {"盝顶", "luding"}, {"庑殿", "wudian"}, {"歇山", "xieshan"}, {"悬山", "xuanshan"},
            {"硬山", "yingshan"}, {"卷棚", "juanpeng"}, {"攒尖", "pyramid"}};
    static final String[][] ROUND_ROOFS = {{"盔顶", "helmet"}, {"圆顶", "dome"}, {"穹顶", "dome"}, {"平顶", "flat"}, {"盝顶", "luding"}, {"攒尖", "cone"}};
    static final String[][] THEMES = {{"黄瓦", "gold"}, {"金瓦", "gold"}, {"琉璃", "gold"}, {"绿瓦", "jade"}, {"青瓦", "grey"}, {"灰瓦", "grey"},
            {"黑瓦", "black"}, {"红瓦", "red"}, {"茅草", "thatch"}, {"草顶", "thatch"}};
    static final String[][] WALLS = {{"红墙", "palace"}, {"宫殿", "palace"}, {"白墙", "whitewall"}, {"粉墙", "whitewall"}, {"青砖", "greybrick"},
            {"灰墙", "greybrick"}, {"木", "darkwood"}, {"土墙", "mud"}, {"石", "stone"}};
    static final String[][] SPECIAL = {{"牌楼", "paifang"}, {"牌坊", "paifang"}, {"影壁", "screen"}, {"照壁", "screen"}, {"垂花门", "chuihua"},
            {"琉璃门", "glazedgate"}, {"随墙门", "gate"}, {"宫墙", "wall"}, {"围墙", "wall"}, {"河桥", "river"}, {"金水河", "river"},
            {"铜鼎", "ornament"}, {"日晷", "ornament"}, {"铜缸", "ornament"}, {"嘉量", "ornament"}};
    static final Map<String, int[]> SPECIAL_SIZE = Map.of("paifang", new int[]{15, 5}, "screen", new int[]{13, 3}, "chuihua", new int[]{9, 7},
            "glazedgate", new int[]{13, 3}, "gate", new int[]{13, 3}, "wall", new int[]{31, 3}, "river", new int[]{31, 9}, "ornament", new int[]{3, 3});

    static boolean any(String t, String... ks) {
        for (String k : ks) if (t.contains(k)) return true;
        return false;
    }

    static Object n(long v) {
        return v == (int) v ? (Object) (int) v : (Object) v;
    }

    /** parse(text) -> {design, said}. */
    public static Object[] parse(String text) {
        String t = text.replace(" ", "");
        long seed = Math.floorMod(t.codePoints().asLongStream().sum(), 9973L) + 1;
        PyRandom rng = PyRandom.ofInt(seed);
        List<Object> said = new ArrayList<>();
        if (any(t, "宫城", "故宫", "紫禁城")) {
            said.add("宫城");
            return new Object[]{Siteplan.city(1), said};
        }
        Matcher m = re(N + "进").matcher(t);
        boolean found = m.find();
        if (found || any(t, "四合院", "院落", "宫院", "大院")) {
            int jin = (int) clamp(1, 3, found ? num(m.group(1)) : Long.valueOf(2));
            String kind = any(t, "宫", "殿", "皇") ? "palace" : "house";
            said.add((kind.equals("palace") ? "宫院" : "住宅") + jin + "进");
            return new Object[]{Siteplan.site(kind, jin, seed), said};
        }
        for (String[] ws : SPECIAL) {
            String word = ws[0], shape = ws[1];
            if (t.contains(word)) {
                said.add(word);
                int[] wd = SPECIAL_SIZE.get(shape);
                int variant = shape.equals("ornament") ? List.of("铜鼎", "日晷", "嘉量", "铜缸").indexOf(word) : 0;
                Map<String, Object> r = Siteplan.room(0, 0, wd[0] - 1, wd[1] - 1, rng, "special", shape, "walls", pick(t, WALLS, "palace", null),
                        "theme", pick(t, THEMES, "gold", null), "variant", variant);
                return new Object[]{design(text, new ArrayList<>(List.of(r))), said};
            }
        }
        Map<String, Object> s = kw("w", 15, "d", 11, "storeys", 1, "roof", "xieshan", "walls", "palace", "theme", "gold", "podium", 0, "podium_style", "plain");
        if (any(t, "大殿", "宝殿", "正殿", "主殿")) {
            s.putAll(kw("w", 31, "d", 17, "podium", 3, "podium_style", "xumizuo", "base", "white", "caihua", "hexi", "beasts", 3, "brackets", 2, "uses", "throne_hall"));
            said.add("大殿");
        } else if (t.contains("殿") || t.contains("宫")) {
            s.putAll(kw("w", 21, "d", 13, "podium", 2, "podium_style", "xumizuo", "base", "white", "uses", "council_hall"));
            said.add("殿");
        }
        if (t.contains("亭")) {
            s.putAll(kw("w", 9, "d", 9, "roof", "pyramid", "open", "open", "uses", "lookout"));
            said.add("亭");
        }
        if (t.contains("塔")) {
            s.putAll(kw("w", 13, "d", 13, "storeys", 5, "taper", 1, "roof", "pyramid", "uses", "lookout"));
            said.add("塔");
        } else if (t.contains("楼") || t.contains("阁")) {
            s.putAll(kw("storeys", 2, "uses", "study"));
            said.add("楼阁");
        }
        if (t.contains("戏台")) {
            s.putAll(kw("open", "front", "uses", "lookout"));
            said.add("戏台");
        } else if (t.contains("门")) {
            s.putAll(kw("open", "through", "uses", "lookout", "d", Math.min(gi(s, "d"), 9)));
            said.add("门");
        }
        if (any(t, "民居", "民房", "住宅", "小屋", "房子", "农家")) {
            s.putAll(kw("walls", "greybrick", "theme", "grey", "roof", "yingshan", "uses", "main_room"));
            said.add("民居");
        }
        if (t.contains("圆")) {
            int mx = Math.max(gi(s, "w"), gi(s, "d"));
            s.putAll(kw("shape", "round", "w", mx, "d", mx));
            for (String[] wr : ROUND_ROOFS) {
                if (t.contains(wr[0])) {
                    s.put("round_roof", wr[1]);
                    said.add(wr[0]);
                    break;
                }
            }
            said.add("圆");
        }
        if (!s.containsKey("shape")) {
            for (String[] wr : ROOFS) {
                if (t.contains(wr[0])) {
                    s.put("roof", wr[1]);
                    said.add(wr[0]);
                    break;
                }
            }
        }
        if (t.contains("重檐")) {
            s.put("double_eave", true);
            said.add("重檐");
        }
        if (re(N + "层台").matcher(t).find() || t.contains("三台")) {
            s.putAll(kw("podium", Math.max(gi(s, "podium"), 6), "podium_style", "tiers", "base", "white"));
            said.add("三层台基");
            t = re(N + "层台(基)?").matcher(t).replaceAll("").replace("三台", "");
        }
        if (t.contains("须弥座")) {
            s.putAll(kw("podium", Math.max(gi(s, "podium"), 3), "podium_style", "xumizuo", "base", "white"));
            said.add("须弥座");
        }
        if (t.contains("城台")) {
            s.putAll(kw("podium", Math.max(gi(s, "podium"), 8), "podium_style", "chengtai"));
            said.add("城台");
        }
        String last = null;
        boolean podiumSaid = false;
        m = re("台基(高)?" + N + "格").matcher(t);
        if (m.find()) {
            last = m.group(2);
            podiumSaid = true;
        } else {
            m = re(N + "格高?的?台基").matcher(t);
            if (m.find()) {
                last = m.group(1);
                podiumSaid = true;
            }
        }
        if (podiumSaid) {
            s.put("podium", (int) clamp(0, 12, num(last)));
            if ("plain".equals(s.get("podium_style")) && gi(s, "podium") >= 3) s.put("podium_style", "xumizuo");
            said.add("台基" + s.get("podium") + "格");
        }
        m = re(N + "层").matcher(t);
        if (m.find() && !t.substring(m.end(), Math.min(t.length(), m.end() + 1)).contains("台")) {
            s.put("storeys", (int) clamp(1, 8, num(m.group(1))));
            said.add(s.get("storeys") + "层");
        }
        m = re("(面阔)?" + N + "开?间").matcher(t);
        if (m.find() && !t.substring(Math.max(0, m.start() - 2), m.start()).contains("进深")) {
            s.put("w", n(Math.max(5, times(num(m.group(2)), 4) + 1)));
            said.add(num(m.group(2)) + "开间");
        }
        m = re("进深" + N + "间").matcher(t);
        if (m.find()) {
            s.put("d", n(Math.max(5, times(num(m.group(1)), 4) + 1)));
            said.add("进深" + num(m.group(1)) + "间");
        }
        s.put("theme", pick(t, THEMES, (String) s.get("theme"), said));
        s.put("walls", pick(t, WALLS, (String) s.get("walls"), said));
        if (t.contains("走兽")) {
            m = re(N + "只?走兽|走兽" + N).matcher(t);
            Long nb = null;
            if (m.find()) nb = num(m.group(1) != null ? m.group(1) : m.group(2));
            long nn = nb == null || nb == 0 ? 5 : nb;
            s.put("beasts", nn == 3 ? 2 : nn == 5 ? 3 : nn == 7 ? 4 : nn == 9 ? 5 : 3);
            said.add("走兽");
        }
        String ch0 = Dyn.truthy(s.get("caihua")) ? (String) s.get("caihua") : "xuanzi";
        for (String[] wc : new String[][]{{"和玺", "hexi"}, {"旋子", "xuanzi"}, {"苏式", "sushi"}, {"彩画", ch0}}) {
            if (t.contains(wc[0])) {
                s.put("caihua", wc[1]);
                said.add(wc[0]);
                break;
            }
        }
        if (t.contains("斗拱") || t.contains("斗栱")) {
            s.put("brackets", 3);
            said.add("斗拱");
        }
        if (any(t, "回廊", "周围廊", "围廊")) {
            s.put("colonnade", true);
            said.add("周围廊");
        } else if (t.contains("前廊")) {
            s.put("veranda", true);
            said.add("前廊");
        }
        if (t.contains("马头墙")) {
            s.putAll(kw("horsehead", true, "roof", "yingshan", "walls", "whitewall", "theme", "black"));
            said.add("马头墙");
        }
        if (t.contains("敞")) s.put("open", "open");
        long w = Planners.lg(s.remove("w")) | 1, dd = Planners.lg(s.remove("d")) | 1;
        Object shape = s.remove("shape");
        if (w - 1 != (int) (w - 1) || dd - 1 != (int) (dd - 1)) throw new Platforms.NotPorted("a frame that big");
        Map<String, Object> r = Siteplan.room(0, 0, (int) w - 1, (int) dd - 1, rng, s);
        if ("round".equals(shape)) {
            r.put("round", true);
            r.put("shape", "round");
        }
        m = re("高" + N + "格|" + N + "格高").matcher(t);
        if (m.find() && !re("台基(高)?" + N + "格").matcher(t).find()) {
            Long want = num(m.group(1) != null ? m.group(1) : m.group(2));
            said.add("高" + (want == null ? "None" : want) + "格");
            r = fitHeight(r, want);
        }
        return new Object[]{design(text, new ArrayList<>(List.of(r))), said};
    }

    static int gi(Map<String, Object> s, String k) {
        return Dyn.toInt(s.get(k));
    }

    static String pick(String t, String[][] table, String dflt, List<Object> said) {
        for (String[] wv : table) {
            if (t.contains(wv[0])) {
                if (said != null) said.add(wv[0]);
                return wv[1];
            }
        }
        return dflt;
    }

    static Map<String, Object> design(String text, List<Object> rooms) {
        int[] cps = text.codePoints().limit(16).toArray();
        return kw("name", "一句话·" + new String(cps, 0, cps.length), "tier", "mortal", "look", 0, "seed", 1, "rooms", rooms);
    }

    static final int GROUND = 64;

    /** _fit_height: the pipeline's search for the ridge height asked for (层高 and 坡度). */
    static Map<String, Object> fitHeight(Map<String, Object> r, Long want) {
        long[] best = null;
        for (int sh : new int[]{5, 6, 7, 8, 9, 10, 12, 14, 16}) {
            for (int pitch : new int[]{1, 2, 3, 4}) {
                Map<String, Object> q = Siteplan.with(r, "sh", sh, "pitch", pitch, "y", GROUND);
                int ridge = ridge(q);
                if (want == null) throw new TypeError("unsupported operand type(s) for -: 'int' and 'NoneType'");
                long e = Math.abs(ridge - want);
                if (best == null || e < best[0]) best = new long[]{e, sh, pitch};
                if (e == 0) break;
            }
            if (best[0] == 0) break;
        }
        return Siteplan.with(r, "sh", (int) best[1], "pitch", (int) best[2]);
    }

    /** pipeline.measure(pipeline.build(q).blocks, q)["ridge"]: the highest block over the frame, over the ground. */
    static int ridge(Map<String, Object> q) {
        Canvas cv = new Canvas();
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("tier", "mortal");
        req.put("seed", 3);
        req.put("rooms", new ArrayList<>(List.of(copy(q))));
        Compose.buildCompose(req, cv);
        int x0 = gi(q, "x0"), x1 = gi(q, "x1"), z0 = gi(q, "z0"), z1 = gi(q, "z1");
        int top = Integer.MIN_VALUE;
        for (long k : cv.blocks.keySet()) {
            int x = Canvas.kx(k), z = Canvas.kz(k);
            if (x0 <= x && x <= x1 && z0 <= z && z <= z1) top = Math.max(top, Canvas.ky(k));
        }
        if (top == Integer.MIN_VALUE) throw new IllegalArgumentException("max() arg is an empty sequence");
        return top - GROUND;
    }

    /** A copy of a room (its lists too): compose may change what it is given. */
    static Map<String, Object> copy(Map<String, Object> r) {
        Map<String, Object> o = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : r.entrySet())
            o.put(e.getKey(), e.getValue() instanceof List<?> l ? new ArrayList<>(l) : e.getValue());
        return o;
    }
}
