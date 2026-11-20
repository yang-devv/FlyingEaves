package com.flyingeaves.tool;

import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The player-visible text: assets/flyingeaves/lang/*.json, by the game's language (zh_cn / zh_tw / zh_hk: Chinese,
 * anything else: English, the game's fallback). Keys are "flyingeaves." + the key given here.
 *
 * <p>The generator's data (kinds.json's option lists, the O menu's kinds, the presets and the library) stay Chinese,
 * byte for byte: {@link #name} shows them in English from the lang file's tables keyed by their ids, and the Chinese
 * itself under a Chinese language. The option tables in code ({@link #pairs}, {@link #list}) are relabelled in place
 * when the language changes ({@link #sync}, every client tick), so the places that read them stay as they were.
 */
public final class L {
    private L() {
    }

    private static final String P = "flyingeaves.";

    /** The text of a key, with its arguments (%s, %1$s ...). */
    public static String t(String key, Object... args) {
        return I18n.get(P + key, args);
    }

    /** The key is in the language loaded (or the en_us fallback). */
    public static boolean has(String key) {
        return I18n.exists(P + key);
    }

    /** The game shows Chinese (the lang file says so: zh_cn and the copies of it for zh_tw, zh_hk). */
    public static boolean zh() {
        return "zh".equals(I18n.get(P + "lang"));
    }

    /**
     * A label of the generator's data in the game's language: the Chinese label as the data has it under Chinese,
     * else the lang file's "flyingeaves.&lt;cat&gt;.&lt;id&gt;" (the Chinese again when there is none: a player's own
     * materials, a part file of theirs).
     */
    public static String name(String cat, String id, String cn) {
        if (zh() || id == null) return cn;
        String k = cat + "." + id;
        if (has(k)) return t(k);
        if (cat.equals("wall") || cat.equals("theme")) {         // the material board's own (custom_w3, custom_r1 ...)
            Matcher m = CUSTOM.matcher(id);
            if (m.matches()) return t(m.group(1).equals("w") ? "custom_wall" : "custom_tiles", m.group(2));
        }
        return cn;
    }

    private static final Pattern CUSTOM = Pattern.compile("custom_([wr])(\\d+)");

    // ------------------------------------------------------------------ tables relabelled when the language changes

    private interface Table {
        void relabel();
    }

    private static final List<Table> TABLES = new ArrayList<>();
    private static String lang = "";
    private static int version;

    /** {id, label} rows; label i is "flyingeaves.&lt;cat&gt;.&lt;i&gt;". */
    static String[][] pairs(String cat, String... ids) {
        String[][] a = new String[ids.length][];
        for (int i = 0; i < ids.length; i++) a[i] = new String[]{ids[i], ""};
        Table tb = () -> {
            for (int i = 0; i < a.length; i++) a[i][1] = t(cat + "." + i);
        };
        tb.relabel();
        TABLES.add(tb);
        return a;
    }

    /** n labels: "flyingeaves.&lt;cat&gt;.0" ... */
    static String[] list(String cat, int n) {
        String[] a = new String[n];
        Table tb = () -> {
            for (int i = 0; i < n; i++) a[i] = t(cat + "." + i);
        };
        tb.relabel();
        TABLES.add(tb);
        return a;
    }

    /** Bumped when the language changed (what caches translated data checks it). */
    static int version() {
        sync();
        return version;
    }

    /** The language loaded changed: relabel the tables. Cheap; called every client tick and when screens open. */
    public static void sync() {
        String now = I18n.get(P + "lang");
        if (now.equals(lang)) return;
        lang = now;
        version++;
        for (Table tb : TABLES) tb.relabel();
    }

    // ------------------------------------------------------------------ what the generator says (it stays Chinese)

    private static final Pattern ROOM_TOO_BIG = Pattern.compile("房间 (\\d+) 太大（(\\d+)×(\\d+)）：每边最多 (\\d+) 格");

    /** A message of the generator's (an error) in the game's language where it is one we know. */
    static String gen(String s) {
        if (s == null || zh()) return s;
        Matcher m = ROOM_TOO_BIG.matcher(s);
        if (m.find()) return m.replaceFirst(Matcher.quoteReplacement(t("gen.room_too_big", m.group(1), m.group(2), m.group(3), m.group(4))));
        return s;
    }

    /** A progress stage (排队 规划 建筑 地基 写出). */
    static String stage(String s) {
        if (s == null || s.isEmpty()) return s;
        String k = switch (s) {
            case "排队" -> "queued";
            case "规划" -> "planning";
            case "建筑" -> "buildings";
            case "地基" -> "ground";
            case "写出" -> "writing";
            default -> null;
        };
        return k == null ? s : t("stage." + k);
    }

    private static final Pattern TOWN = Pattern.compile("凡人城镇·(.+)([村镇城])");
    private static final Pattern SECT = Pattern.compile("宗门·(.+?)（(.+)·(.+)）");
    private static final Pattern COURT = Pattern.compile("(宫院|住宅)·([一二三])进");

    /**
     * A site's name as the generator writes it (凡人城镇·江南镇, 宗门·青云宗（练气·青云）, 住宅·二进, 宫城 ...) in the
     * game's language: its parts from the lang file's tables, proper names in pinyin.
     */
    static String siteName(String s) {
        if (s == null || zh()) return s;
        String suffix = "";
        if (s.endsWith("·全参数随机")) {
            s = s.substring(0, s.length() - "·全参数随机".length());
            suffix = t("site.all_random");
        }
        Matcher m;
        String out;
        if ((m = TOWN.matcher(s)).matches())
            out = t("site.town", part("town_family", m.group(1)), t("site.town_size." + m.group(2)));
        else if ((m = SECT.matcher(s)).matches())
            out = t("site.sect", sectName(m.group(1)), part("tier_cn", m.group(2)), part("sect_family", m.group(3)));
        else if ((m = COURT.matcher(s)).matches())
            out = t(m.group(1).equals("宫院") ? "site.palace_court" : "site.house", "一二三".indexOf(m.group(2)) + 1);
        else out = part("site_name", s);
        return out + suffix;
    }

    private static String part(String cat, String cn) {
        return has(cat + "." + cn) ? t(cat + "." + cn) : cn;
    }

    /** 青云宗, 太虚剑宗: the name in pinyin and the kind of sect. */
    private static String sectName(String cn) {
        for (String suf : new String[]{"剑宗", "仙门", "道宫", "宗", "门", "派", "阁"}) {
            if (cn.endsWith(suf) && cn.length() > suf.length()) {
                String n = cn.substring(0, cn.length() - suf.length());
                return t("sect_suffix." + suf, part("sect_family", n));
            }
        }
        return part("sect_family", cn);
    }
}
