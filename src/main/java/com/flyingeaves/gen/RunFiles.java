package com.flyingeaves.gen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * What the daemon writes into its run folder when it starts and when material or part files change
 * (bgen_daemon.write_kinds): kinds.json (the O menu's kinds and compose.options(): the room table's lists and the
 * material board's slots), presets/ (the 做法), library/ (预设库) and sites/ (the 随机一院 plans). The same files, byte for
 * byte (oracle: tools/oracle/capture.py startup), so the game reads them as it did with Python.
 *
 * <p>Call on the generator's thread after {@link Generator#init} (the tables come from the loaded materials and parts).
 */
public final class RunFiles {
    private RunFiles() {
    }

    // compose.py's option lists (the room table's choices)
    static final List<List<String>> ROOFS = rows("xieshan", "歇山", "wudian", "庑殿", "xuanshan", "悬山", "yingshan", "硬山",
            "juanpeng", "卷棚", "pyramid", "攒尖", "luding", "盝顶", "shizi", "十字脊", "yuanzan", "圆攒尖");
    static final List<List<String>> USES = rows("main_room", "堂屋", "bedroom", "卧房", "study", "书房", "kitchen", "厨房",
            "storeroom", "库房", "guest_room", "客房", "inn_hall", "客栈大堂", "teahouse", "茶馆", "private_dining", "雅间",
            "pharmacy", "药铺", "general_store", "杂货铺", "smithy", "铁匠铺", "ancestral_hall", "祠堂", "temple_hall", "神殿",
            "meditation_room", "静室", "seclusion", "闭关室", "alchemy_room", "丹房", "forging_room", "炼器室",
            "talisman_room", "符室", "formation_room", "阵室", "scripture_store", "藏经", "sword_gallery", "剑阁",
            "treasury", "宝库", "lecture_hall", "讲堂", "council_hall", "议事厅", "task_hall", "执事大厅",
            "throne_hall", "大殿（宝座）", "ancestor_tablets", "祖师牌位", "teleport_room", "传送阵室", "auction_hall", "拍卖厅",
            "refectory", "食堂", "outer_dorm", "弟子通铺", "inner_disciple_room", "弟子居室", "elder_quarters", "长老居所",
            "training_room", "演武室", "herb_store", "药材库", "beast_stable", "灵兽舍", "lookout", "观景台",
            "stele", "碑（碑亭）",
            "bedroom_north", "炕房", "maiden_room", "闺房", "master_study", "主人书斋", "accounts", "账房",
            "granary", "粮仓", "mill_room", "磨坊", "dyeing", "染坊", "brewery", "酿酒坊", "weaving", "织坊",
            "pottery", "陶坊", "tofu", "豆腐坊", "cloth_shop", "布庄", "pawnshop", "当铺", "bookshop", "书肆",
            "clinic", "医馆", "coffin_shop", "棺材铺", "gambling_den", "赌坊", "stable", "马厩", "backstage", "后台",
            "guardroom", "门房（守卫）", "barracks", "营房", "courtroom", "公堂", "jail", "牢房", "classroom", "学堂",
            "discipline_hall", "戒律堂", "array_control", "阵枢", "spirit_stone_vault", "灵石库",
            "artifact_shop", "法器铺", "pill_shop", "丹药铺", "talisman_shop", "符箓铺", "material_shop", "材料铺",
            "cultivator_inn_room", "修士客房", "auction_box", "拍卖雅间", "ship_cabin", "船舱");
    static final List<List<String>> FRONTS = rows("south", "南", "east", "东", "north", "北", "west", "西");
    static final List<String> TIER_CN = List.of("凡人", "练气", "筑基", "金丹", "元婴", "化神");
    static final Set<String> MORTAL_ARCH = Set.of("thatched_hut", "mud_house", "stone_cottage", "log_cabin", "stilt_house",
            "cave_dwelling", "north_courtyard", "jiangnan_house", "huizhou_house", "tulou", "fisher_shack");
    static final Set<String> SITE_ARCHES = Set.of("cloud_port", "spirit_mine", "beast_hill", "trial_ground", "forbidden", "tomb");

    private static List<List<String>> rows(String... kv) {
        List<List<String>> out = new ArrayList<>();
        for (int i = 0; i < kv.length; i += 2) out.add(List.of(kv[i], kv[i + 1]));
        return out;
    }

    /** bgen_daemon.group: the O menu's groups. */
    static String group(String name, Map<String, Object> spec) {
        if ("mortal".equals(Dyn.list(spec.get("tiers")).get(0))) return MORTAL_ARCH.contains(name) ? "凡人·住宅" : "凡人·城镇";
        if (SITE_ARCHES.contains(spec.get("arch"))) return "宗门·特殊地点";
        return "修士";
    }

    /** (name, cn, swatch) of a style table, as compose.WALLS / THEMES (the material board's temporary styles left out). */
    private static List<List<Object>> styles(Map<String, Map<String, Object>> table) {
        List<List<Object>> out = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> e : table.entrySet()) {
            if (e.getKey().startsWith("_board_")) continue;
            Map<String, Object> v = e.getValue();
            out.add(java.util.Arrays.asList(e.getKey(), v.get("cn"), v.containsKey("swatch") ? v.get("swatch") : ""));
        }
        return out;
    }

    private static Map<String, Object> slots(Map<String, Map<String, Object>> table, List<String> slots) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> e : table.entrySet()) {
            if (e.getKey().startsWith("_board_")) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            for (String s : slots) if (Dyn.truthy(e.getValue().get(s))) m.put(s, e.getValue().get(s));
            out.put(e.getKey(), m);
        }
        return out;
    }

    /** compose.options(). */
    public static Map<String, Object> options() {
        if (Parts.BAYS.isEmpty()) Parts.reloadBays();
        List<String> styles = new ArrayList<>();
        for (Map<String, Object> e : Parts.BAYS.values()) {
            Object s = e.get("style");
            if (Dyn.truthy(s) && !styles.contains((String) s)) styles.add((String) s);
        }
        List<List<String>> bayStyles = new ArrayList<>();
        bayStyles.add(List.of("auto", "随机"));
        for (String s : styles) bayStyles.add(List.of(s, s));
        List<List<Object>> bases = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> e : Compose.loadBases().entrySet()) {
            Map<String, Object> v = e.getValue();
            bases.add(java.util.Arrays.asList(e.getKey(), v.containsKey("cn") ? v.get("cn") : e.getKey(), v.containsKey("swatch") ? v.get("swatch") : ""));
        }
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("roofs", ROOFS);
        o.put("walls", styles(Bgen.WALLS));
        o.put("themes", styles(Arch.ROOFS));
        o.put("uses", USES);
        o.put("fronts", FRONTS);
        o.put("bay_styles", bayStyles);
        o.put("bases", bases);
        o.put("wall_slots", slots(Bgen.WALLS, Compose.WALL_SLOTS));
        o.put("roof_slots", slots(Arch.ROOFS, Compose.ROOF_SLOTS));
        return o;
    }

    /** kinds.json's text. */
    @SuppressWarnings("unchecked")
    public static String kindsJson() {
        List<Object> kinds = new ArrayList<>();
        for (Map.Entry<String, Object> e : Bgen.K.entrySet()) {
            Map<String, Object> s = (Map<String, Object>) e.getValue();
            Map<String, Object> k = new LinkedHashMap<>();
            k.put("name", e.getKey());
            k.put("cn", s.get("cn"));
            k.put("tiers", new ArrayList<>(Dyn.list(s.get("tiers"))));
            k.put("group", group(e.getKey(), s));
            kinds.add(k);
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("tiers", Bgen.TIERS);
        d.put("tier_cn", TIER_CN);
        d.put("kinds", kinds);
        d.put("compose", options());
        return Json.write(d, false);
    }

    /**
     * bgen_daemon.write_kinds: kinds.json (whole, via .tmp), presets/, sites/ (siteplan.write_all: {@link Siteplan#writeAll}),
     * library/ under run. Returns how many kinds.
     */
    public static int write(Path run) throws IOException {
        Files.createDirectories(run);
        Path tmp = run.resolve("kinds.json.java.tmp");    // (not the daemon's .tmp: it may be writing the same file)
        Files.writeString(tmp, kindsJson(), StandardCharsets.UTF_8);
        Files.move(tmp, run.resolve("kinds.json"), StandardCopyOption.REPLACE_EXISTING);
        writePresets(run);
        try {                                             // (write_sites: a failure is printed, the rest goes on)
            Siteplan.writeAll(run.resolve("sites"), 4);
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
        writeLibrary(run);
        return Bgen.K.size();
    }

    /** write_presets: data/presets/*.json into run/presets (ones gone from data go there too). */
    static void writePresets(Path run) throws IOException {
        Path out = run.resolve("presets"), src = Env.DATA.resolve("presets");
        Files.createDirectories(out);
        Set<String> have = new java.util.HashSet<>();
        for (Path p : Env.glob(src, "")) have.add(p.getFileName().toString());
        for (Path p : list(out)) {
            String n = p.getFileName().toString();
            if (n.endsWith(".json") && !have.contains(n)) Files.deleteIfExists(p);
        }
        for (Path p : Env.glob(src, "")) Files.write(out.resolve(p.getFileName().toString()), Files.readAllBytes(p));
    }

    /** write_library: run/library replaced by a copy of data/library (a folder tree). */
    static void writeLibrary(Path run) throws IOException {
        Path out = run.resolve("library"), src = Env.DATA.resolve("library");
        if (Files.exists(out)) {
            try (Stream<Path> old = Files.walk(out)) {
                for (Path p : old.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
            }
        }
        if (!Files.isDirectory(src)) return;
        try (Stream<Path> all = Files.walk(src)) {
            for (Path p : all.toList()) {
                Path to = out;
                for (Path part : src.relativize(p)) if (!part.toString().isEmpty()) to = to.resolve(part.toString());
                if (Files.isDirectory(p)) Files.createDirectories(to);
                else {
                    Files.createDirectories(to.getParent());
                    Files.write(to, Files.readAllBytes(p));
                }
            }
        }
    }

    private static List<Path> list(Path d) throws IOException {
        if (!Files.isDirectory(d)) return List.of();
        try (Stream<Path> s = Files.list(d)) {
            return s.filter(Files::isRegularFile).toList();
        }
    }
}
