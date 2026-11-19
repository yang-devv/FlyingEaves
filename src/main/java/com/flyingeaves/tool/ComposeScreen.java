package com.flyingeaves.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * 房间表（单体建造，H）。居中的深色卡片：左边是画好的房间（颜色点和世界里的线框同色，选中的在世界里是白框），右边改选中房间：
 * 层数、屋顶、正面直接点；墙、瓦、用途点开选择面板挑。底下是境界和生成、换一个、撤销。位置都按卡片大小算，
 * 放不下就截断、换行或用滚轮翻，所以界面放大也不溢出。
 */
public class ComposeScreen extends Screen {
    static final class Options {
        final List<String[]> roofs = new ArrayList<>(), walls = new ArrayList<>(), themes = new ArrayList<>(), uses = new ArrayList<>(),
                fronts = new ArrayList<>();
        /** 墙样（开间的样式），第一个是随机。 */
        final List<String[]> bayStyles = new ArrayList<>();
        /** 基座材料，第一个是默认石砖。 */
        final List<String[]> bases = new ArrayList<>();
        final List<String> tiers = new ArrayList<>(), tierCn = new ArrayList<>();
    }

    /** 墙和瓦的示意色。 */
    private static final Map<String, Integer> SWATCH = Map.ofEntries(
            Map.entry("palace", 0xB8402F), Map.entry("darkwood", 0x4E3828), Map.entry("whitewall", 0xE8E4DA), Map.entry("greybrick", 0x7C828A),
            Map.entry("redbrick", 0x9E4C3C), Map.entry("wattle", 0xDAD1BE), Map.entry("log", 0x7C5C3C), Map.entry("stone", 0x8C8C8C),
            Map.entry("mud", 0x9C7C5A), Map.entry("yellow", 0xCBA96C), Map.entry("jade", 0xDDEEE7), Map.entry("cave", 0x706C68),
            Map.entry("grey", 0x6D7582), Map.entry("teal", 0x3CA78E), Map.entry("gold", 0xE2B24C), Map.entry("black", 0x2C2F35),
            Map.entry("red", 0xB2463C), Map.entry("slate", 0x5B6570), Map.entry("wood", 0x8C6C47), Map.entry("thatch", 0xCBB06C));

    private static final Random RNG = new java.security.SecureRandom();   // (the OS's randomness, not a clock)
    private static int listScroll;
    private static int panelScroll;

    private final Ui ui = new Ui();
    private Options opts;
    private String loadError;
    private Ui.Picker picker;
    /** 「存档」面板：盖住右边的房间面板。 */
    private static boolean designsOpen;
    private static String lastName = "";
    private static int designScroll;
    private EditBox nameBox;
    /** 一句话：照着一句话搭（「重檐庑殿的大殿，九开间，三层台基」「二进四合院」「绿瓦盔顶的圆亭」……）。 */
    private EditBox sayBox;
    private static String lastSay = "";
    /** 随机城镇的规模（随机 村 镇 大镇，点一下换下一个）和宽 × 深（格，空着就按规模自己定；只填一个就是见方）。 */
    private static int townSize;
    private static final String[] TOWN_SIZE_CN = {"随机", "村", "镇", "大镇"};
    private static final String[] TOWN_SIZE_ID = {"", "village", "town", "big"};
    private EditBox townWBox, townDBox;
    private static String lastTownW = "", lastTownD = "";
    private List<Compose.Entry> saved = List.of();
    /** 存档面板看的是「预设库」还是「我的存档」，各自在哪个文件夹里。 */
    private static boolean libTab;
    private static String relMine = "", relLib = "";
    private java.nio.file.Path confirmDelete;
    private String designMsg = "";
    private int cx, cy, cw, ch, bodyTop, footTop, lx, lw, rx, rw;
    /** The table is laid out on a screen at least 600 x 340 GUI units: with a large GUI scale it is drawn smaller. */
    private float k = 1f;
    private int vw, vh;
    private static int contentH = 800;                    // the room panel's height last frame (for its scroll)

    public ComposeScreen() {
        super(Component.literal("单体建造"));
        try {
            opts = load();
        } catch (Exception e) {
            loadError = "读不到选项（flyingeaves/kinds.json）：" + com.flyingeaves.backend.GeneratorProcess.hint();
        }
    }

    static Options load() throws Exception {
        JsonObject root = JsonParser.parseString(Files.readString(BuildTool.RUN.resolve("kinds.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject c = root.getAsJsonObject("compose");
        Options o = new Options();
        fill(o.roofs, c.getAsJsonArray("roofs"));
        fill(o.walls, c.getAsJsonArray("walls"));
        fill(o.themes, c.getAsJsonArray("themes"));
        fill(o.uses, c.getAsJsonArray("uses"));
        fill(o.fronts, c.getAsJsonArray("fronts"));
        if (c.has("bay_styles")) fill(o.bayStyles, c.getAsJsonArray("bay_styles"));
        else o.bayStyles.add(new String[]{"auto", "随机", ""});
        if (c.has("bases")) fill(o.bases, c.getAsJsonArray("bases"));
        else o.bases.add(new String[]{"auto", "石砖（默认）", ""});
        for (JsonElement e : root.getAsJsonArray("tiers")) o.tiers.add(e.getAsString());
        for (JsonElement e : root.getAsJsonArray("tier_cn")) o.tierCn.add(e.getAsString());
        return o;
    }

    private static void fill(List<String[]> out, JsonArray a) {
        for (JsonElement e : a) {
            JsonArray p = e.getAsJsonArray();
            out.add(new String[]{p.get(0).getAsString(), p.get(1).getAsString(), p.size() > 2 ? p.get(2).getAsString() : ""});
        }
    }

    /** 示意色：数据里给了就用（"#RRGGBB"），没给就用内置的。 */
    static int swatchOf(String[] e) {
        if (e.length > 2 && e[2].startsWith("#") && e[2].length() == 7) {
            try {
                return Integer.parseInt(e[2].substring(1), 16);
            } catch (NumberFormatException ignored) {
            }
        }
        return SWATCH.getOrDefault(e[0], 0);
    }

    static int roomColor(int i) {
        return java.awt.Color.HSBtoRGB((i * 0.17f) % 1f, 0.6f, 1f) & 0xFFFFFF;
    }

    @Override
    protected void init() {
        k = Math.min(1f, Math.min(width / 600f, height / 340f));
        vw = Math.round(width / k);
        vh = Math.round(height / k);
        cw = Math.min(vw - 12, 640);
        ch = Math.min(vh - 12, 360);
        cx = (vw - cw) / 2;
        cy = (vh - ch) / 2;
        bodyTop = cy + 26;
        footTop = cy + ch - 28;
        lx = cx + 8;
        lw = Math.max(96, Math.min(170, (cw - 26) * 30 / 100));
        rx = lx + lw + 12;
        rw = cx + cw - 8 - rx;
        nameBox = new EditBox(font, rx, bodyTop + 16, Math.max(40, rw - 70), 16, Component.literal("名字"));
        nameBox.setMaxLength(40);
        nameBox.setValue(lastName.isEmpty() ? defaultName() : lastName);
        nameBox.setResponder(s -> lastName = s);
        nameBox.setVisible(designsOpen);
        addRenderableWidget(nameBox);
        sayBox = new EditBox(font, lx, footTop - 136, lw - 30, 16, Component.literal("一句话"));
        sayBox.setMaxLength(80);
        sayBox.setValue(lastSay);
        sayBox.setHint(Component.literal("一句话…"));
        sayBox.setResponder(s -> lastSay = s);
        addRenderableWidget(sayBox);
        int sw = townSizeW(), bw = (lw - sw - 4 - 10 - 20) / 2;   // 后面留 4 + 16 给「×」清空
        townWBox = sizeBox(lx + sw + 4, bw, "宽", lastTownW, v -> lastTownW = v);
        townDBox = sizeBox(lx + sw + 4 + bw + 10, bw, "深", lastTownD, v -> lastTownD = v);
        saved = listNow();
    }

    private int townSizeW() {
        return Math.max(36, lw * 34 / 100);
    }

    /** 城镇的宽或深：只收数字。 */
    private EditBox sizeBox(int x, int w, String hint, String value, java.util.function.Consumer<String> keep) {
        EditBox b = new EditBox(font, x, footTop - 48, w, 16, Component.literal(hint));
        b.setMaxLength(9);
        b.setFilter(v -> v.chars().allMatch(Character::isDigit));
        b.setValue(value);
        b.setHint(Component.literal(hint));
        b.setResponder(keep);
        addRenderableWidget(b);
        return b;
    }

    /** 随机城镇：规模和宽深照选的、填的；宽深填了 0 不排，说一声。 */
    private void requestTown() {
        String w = townWBox.getValue().trim(), d = townDBox.getValue().trim();
        if (w.isEmpty()) w = d;
        if (d.isEmpty()) d = w;
        int iw = 0, id = 0;
        if (!w.isEmpty()) {
            try {
                iw = Integer.parseInt(w);
                id = Integer.parseInt(d);
            } catch (NumberFormatException e) {
                iw = id = -1;
            }
            if (iw <= 0 || id <= 0) {
                BuildTool.say("城镇的宽、深要填大于 0 的整数（格），或者都空着按规模自己定");
                return;
            }
        }
        onClose();
        BuildTool.requestTown(TOWN_SIZE_ID[townSize], iw, id);
    }

    private List<Compose.Entry> listNow() {
        return libTab ? Compose.entries(Compose.LIBRARY, relLib) : Compose.entries(Compose.DESIGNS, relMine);
    }

    static void showDesigns(boolean open) {
        designsOpen = open;
    }

    private static String defaultName() {
        return "设计 " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HHmm"));
    }

    // ------------------------------------------------------------------ 画

    @Override
    public void render(GuiGraphics g, int realX, int realY, float partialTick) {
        g.fill(0, 0, width, height, Ui.DIM);
        g.pose().pushPose();
        g.pose().scale(k, k, 1f);
        int mouseX = Math.round(realX / k), mouseY = Math.round(realY / k);
        ui.begin(g, font, mouseX, mouseY);
        ui.scale = k;
        try {
            renderAll(g, mouseX, mouseY, partialTick);
        } finally {
            g.pose().popPose();
        }
    }

    private void renderAll(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        ui.card(cx, cy, cw, ch);
        ui.hit(cx, cy, cw, ch, () -> {
        }, null);
        int n = Compose.ROOMS.size();
        ui.text(n + " 个房间", cx + 10, cy + 9, Ui.MUTED);
        int closeX = cx + cw - 20;
        String stop = "放弃这栋";
        int sw = ui.textW(stop) + 12;
        ui.button(closeX - 4 - sw, cy + 5, sw, 14, stop, Ui.Kind.GHOST, true, () -> {
            Compose.stop();
            Compose.ROOMS.clear();
            onClose();
        });
        ui.button(closeX, cy + 5, 14, 14, "×", Ui.Kind.GHOST, true, this::onClose);
        // 房间表的撤销、重做（同 Ctrl+Z / Ctrl+Y），放在标题行，底下留给境界和生成
        String undo = "撤销", redo = "重做";
        int udw = ui.textW(undo) + 12, rdw = ui.textW(redo) + 12;
        int ux = closeX - 4 - sw - 8 - rdw - 4 - udw;
        ui.button(ux, cy + 5, udw, 14, undo, Ui.Kind.GHOST, true, Compose::undo);
        ui.button(ux + udw + 4, cy + 5, rdw, 14, redo, Ui.Kind.GHOST, true, Compose::redo);
        // 预览要不要画屋里（默认只画外面看得见的，快）
        boolean narrow = cw < 560;
        String inner = Preview.interior ? (narrow ? "含室内" : "预览：含室内") : (narrow ? "只看外观" : "预览：只看外观");
        int iw = ui.textW(inner) + 12;
        ui.button(ux - 8 - iw, cy + 5, iw, 14, inner, Ui.Kind.GHOST, true, () -> {
            Preview.interior = !Preview.interior;
            Preview.refresh();
        });
        // 存档：存、载入、删这一栋的设计（面板盖住右边）
        String ds = designsOpen ? "回到房间" : narrow ? "存档" : "存档 / 载入";
        int dw = ui.textW(ds) + 12;
        ui.button(ux - 8 - iw - 6 - dw, cy + 5, dw, 14, ds, designsOpen ? Ui.Kind.PRIMARY : Ui.Kind.NORMAL, true, () -> {
            designsOpen = !designsOpen;
            confirmDelete = null;
            designMsg = "";
            saved = listNow();
        });
        ui.hline(cx + 1, cy + 22, cw - 2);
        if (opts == null) {
            ui.wrap(loadError, cx + 10, bodyTop + 4, cw - 20, cy + ch - 8, Ui.DANGER);
            return;
        }
        nameBox.setVisible(designsOpen && !libTab && picker == null);
        if (!nameBox.isVisible()) nameBox.setFocused(false);
        renderList();
        if (designsOpen) renderDesigns();
        else renderRoom();
        renderFooter();
        super.render(g, mouseX, mouseY, partialTick);     // (the text boxes, under the picker)
        if (picker != null) picker.render(ui, vw, vh, () -> picker = null);
    }

    private void renderList() {
        int n = Compose.ROOMS.size();
        Compose.selected = Math.max(0, Math.min(Compose.selected, Math.max(0, n - 1)));
        ui.text("房间", lx, bodyTop + 2, Ui.SUB);
        int top = bodyTop + 14, bottom = footTop - 140, rowH = 18;
        int visible = Math.max(1, (bottom - top) / rowH);
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, n - visible)));
        ui.scroll(lx, top, lw, bottom - top, d -> listScroll = Math.max(0, Math.min(Math.max(0, n - visible), listScroll + d)));
        ui.pushClip(lx, top, lw, bottom - top);
        for (int i = listScroll; i < Math.min(n, listScroll + visible + 1); i++) {
            Compose.Room r = Compose.ROOMS.get(i);
            int y = top + (i - listScroll) * rowH;
            boolean sel = i == Compose.selected, hov = ui.over(lx, y, lw - 4, rowH - 2);
            Ui.round(ui.g, lx, y, lw - 4, rowH - 2, sel ? Ui.ACCENT_SOFT : hov ? Ui.ITEM_HOVER : Ui.ITEM);
            if (sel) ui.g.fill(lx, y + 3, lx + 2, y + rowH - 5, Ui.ACCENT);
            int dot = sel ? 0xFFFFFF : roomColor(i);
            ui.g.fill(lx + 7, y + 5, lx + 13, y + 11, 0xFF000000 | dot);
            String what = r.special > 0 ? Compose.SPECIALS[r.special][1] + " " : r.round ? "圆塔 " : r.stacked ? "叠 " : "";
            String s = (i + 1) + "  " + what + (r.x1 - r.x0 + 1) + "×" + (r.z1 - r.z0 + 1);
            ui.text(s, lx + 18, y + 4, lw - 22 - ui.textW(r.storeys + "层") - 6, sel ? Ui.GOLD : Ui.TEXT);
            String st = r.storeys + "层";
            ui.text(st, lx + lw - 10 - ui.textW(st), y + 4, Ui.MUTED);
            final int idx = i;
            ui.hit(lx, y, lw - 4, rowH - 2, () -> Compose.selected = idx, null);
        }
        ui.popClip();
        ui.scrollbar(lx + lw - 2, top, bottom - top, n, visible, listScroll);
        if (n == 0) ui.wrap("还没有房间", lx + 2, top + 4, lw - 4, bottom, Ui.MUTED);
        ui.button(lx, footTop - 26, lw, 18, n == 0 ? "＋ 画第一间" : "＋ 加一间", Ui.Kind.NORMAL, true, this::onClose);
        // a random building of several frames at the crosshair; the screen closes so its preview shows
        sayBox.setVisible(picker == null);
        ui.button(lx + lw - 26, footTop - 136, 26, 16, "搭", Ui.Kind.PRIMARY, !lastSay.isBlank(), this::submitSay);
        int half = (lw - 4) / 2;                            // 三行：随机宗门 / 随机一栋 随机一院 / 随机城镇（宫城、紫禁城 1:1 在预设库「宫城」里）
        ui.button(lx, footTop - 114, lw, 18, "随机宗门", Ui.Kind.NORMAL, true, () -> {   // a sect up the mountain here, of the tier chosen below
            onClose();
            BuildTool.requestSect(opts.tiers.get(Math.min(Compose.tier, opts.tiers.size() - 1)));
        });
        ui.button(lx, footTop - 92, half, 18, "随机一栋", Ui.Kind.NORMAL, true, () -> {
            String err = Compose.randomBuilding();
            if (err != null) BuildTool.say(err);
            else onClose();
        });
        ui.button(lx + half + 4, footTop - 92, lw - half - 4, 18, "随机一院", Ui.Kind.NORMAL, true, () -> {   // 总平面图: a whole compound
            onClose();
            BuildTool.requestSite(false);
        });
        ui.button(lx, footTop - 70, lw, 18, "随机城镇", Ui.Kind.NORMAL, true, this::requestTown);   // a market town on the ground here
        // 城镇的规模（点一下换：随机 村 镇 大镇）和宽 × 深（格；空着按规模）
        int sw = townSizeW(), bw = (lw - sw - 4 - 10 - 20) / 2;   // 后面留 4 + 16 给「×」清空
        ui.select(lx, footTop - 48, sw, 16, TOWN_SIZE_CN[townSize], 0, true, () -> townSize = (townSize + 1) % TOWN_SIZE_CN.length);
        ui.text("×", lx + sw + 4 + bw + 2, footTop - 44, Ui.MUTED);
        townWBox.setVisible(picker == null);
        townDBox.setVisible(picker == null);
        // 「×」：宽、深一起清空（空着＝按规模自己定）
        ui.button(lx + lw - 16, footTop - 48, 16, 16, "×", Ui.Kind.GHOST, !lastTownW.isEmpty() || !lastTownD.isEmpty(), () -> {
            townWBox.setValue("");
            townDBox.setValue("");
            lastTownW = lastTownD = "";
        });
        if (n > 0) {
            // 清空：放在列表标题右边，免得误点
            String clear = "清空";
            int cwid = ui.textW(clear) + 10;
            ui.button(lx + lw - cwid - 2, bodyTop, cwid, 12, clear, Ui.Kind.GHOST, true, () -> {
                Compose.ROOMS.clear();
                Compose.selected = 0;
            });
        }
    }


    private void renderRoom() {
        int x = rx, w = rw, bottom = footTop - 4;
        int n = Compose.ROOMS.size();
        if (n == 0) {
            ui.text("怎么画", x, bodyTop + 2, Ui.SUB);
            ui.wrap("一次搭一栋。关掉这个面板回到世界：右键点地面上一个角，再右键点对角，就是一个房间（线框）；「画圆塔」工具画的是圆塔。"
                    + "后面的房间要贴着已有房间的墙或叠在一起，挨着的自动开门。Alt+滚轮换工具，对着房间滚轮或点；Ctrl+Z 撤销、Ctrl+Y 重做。"
                    + "也可以在左边打一句话，或点「随机一栋」（世界里按" + Compose.randomKey() + "也是，再按换一栋）、「存档」里的预设（随机宫城、紫禁城 1:1 在预设库「宫城」里）。画好按" + Compose.key() + "回来细调，点「生成」放下去。",
                    x, bodyTop + 16, w, bottom, Ui.MUTED);
            return;
        }
        Compose.Room r = Compose.ROOMS.get(Compose.selected);
        boolean sp = r.special > 0;                       // 牌楼, 影壁, 门: most of the house's settings do nothing for them
        String head = "房间 " + (Compose.selected + 1);
        ui.text(head, x, bodyTop + 2, Ui.TEXT);
        ui.text((sp ? Compose.SPECIALS[r.special][1] + " · " : "") + (r.round ? "圆塔 直径 " + (r.x1 - r.x0 + 1) : (r.x1 - r.x0 + 1) + "×" + (r.z1 - r.z0 + 1)) + " · 世界里是白框", x + 8 + ui.textW(head), bodyTop + 2, w - 12 - ui.textW(head), Ui.MUTED);
        labW = 0;
        for (String s : new String[]{"形状", "叠放", "门洞", "层数", "层高", "收分", "平座", "台基", "样式", "基座", "檐", "坡度", "出檐", "翘角", "走兽", "斗拱",
                "彩画", "廊", "山墙", "开敞", "出入口", "台阶", "边距", "屋顶", "墙样", "8 楼", "正面", "样子"}) labW = Math.max(labW, ui.textW(s) + 10);
        int vx = x + labW, vwid = w - labW - 6;
        int top = bodyTop + 16, rowH = 20;
        int maxScroll = Math.max(0, contentH - (bottom - top));
        panelScroll = Math.max(0, Math.min(panelScroll, maxScroll));
        ui.scroll(x, top, w, bottom - top, d -> panelScroll = Math.max(0, Math.min(maxScroll, panelScroll + d * rowH)));
        ui.pushClip(x, top, w, bottom - top);
        int y = top - panelScroll;
        boolean on;

        y += ui.section("形体", x, y, w - 6);
        label("形状", x, y, true);
        pick(vx, y, vwid, "形状（牌楼、门、墙、铺地、河桥、陈设这些房间设置组合不出来）", Compose.shapes(),
                () -> r.special > 0 ? r.special + 1 : r.round ? 1 : 0, v -> {
                    r.round = v == 1;
                    r.special = v >= 2 ? v - 1 : 0;
                }, true);
        y += adv(rowH);
        Compose.Room under = Compose.stackBase(r);
        on = !sp && (under != null || r.stacked);
        label("叠放", x, y, on);                           // (only when it is drawn inside a room before it)
        segment(vx, y, Math.min(vwid, 2 * 110), 2, i -> i == 0 ? "地上" : under == null ? "叠在下面那间上" : "叠在房间 " + (Compose.ROOMS.indexOf(under) + 1) + " 上",
                () -> r.stacked ? 1 : 0, v -> {
                    r.stacked = v == 1;
                    if (v == 1) r.podium = 0;
                    else r.y = Compose.groundUnder(r.x0, r.z0, r.x1, r.z1, r.round, r.y);
                    Compose.restack();
                }, on);
        y += adv(rowH);
        label("层数", x, y, !sp);
        segment(vx, y, vwid, 8, i -> (i + 1) + "", () -> r.storeys - 1, v -> r.storeys = v + 1, !sp);
        y += adv(rowH);
        label("层高", x, y, true);                         // 0 = the building's own; else that many blocks a storey (a great hall 12-16)
        segment(vx, y, vwid, Compose.STOREY_HEIGHTS.length, i -> i == 0 ? "自动" : "" + Compose.STOREY_HEIGHTS[i], () -> {
            int pi = 0;
            while (pi < Compose.STOREY_HEIGHTS.length - 1 && Compose.STOREY_HEIGHTS[pi] < r.sh) pi++;
            return pi;
        }, v -> r.sh = Compose.STOREY_HEIGHTS[v], true);
        y += adv(rowH);
        on = !sp && r.storeys > 1;
        label("收分", x, y, on);                           // every storey up set in this much on each side (塔、楼阁; 圆塔: its radius)
        segment(vx, y, Math.min(vwid, 4 * 44), 4, i -> i == 0 ? "不收" : "收" + i, () -> r.taper, v -> r.taper = v, on);
        y += adv(rowH);
        on = !sp && r.round && r.storeys > 1;
        label("平座", x, y, on);                           // 圆塔: a railed walk round every upper storey on the eave under it
        segment(vx, y, Math.min(vwid, 2 * 56), 2, i -> i == 0 ? "无" : "每层一圈", () -> r.pingzuo ? 1 : 0, v -> r.pingzuo = v == 1, on);
        y += adv(rowH);

        y += ui.section("台基", x, y + 2, w - 6) + 2;
        label("台基", x, y, !sp);
        segment(vx, y, vwid, Compose.PODIUM_HEIGHTS.length, i -> i == 0 ? "无" : "" + Compose.PODIUM_HEIGHTS[i], () -> {
            int pi = 0;
            while (pi < Compose.PODIUM_HEIGHTS.length - 1 && Compose.PODIUM_HEIGHTS[pi] < r.podium) pi++;
            return pi;
        }, v -> r.podium = Compose.PODIUM_HEIGHTS[v], !sp);
        y += adv(rowH);
        on = !sp && r.podium > 0;
        label("样式", x, y, on);                           // 平台 / 须弥座 / 三层 / 城台
        segment(vx, y, Math.min(vwid, 4 * 56), Compose.PODIUM_STYLES.length, i -> Compose.PODIUM_STYLES[i][1], () -> r.podiumStyle, v -> r.podiumStyle = v, on);
        y += adv(rowH);
        label("形状", x, y, on);                           // 台基形状: 跟随房间, 方 (圆塔下一座方台、城台), 圆 (方殿下一座圆坛)
        segment(vx, y, Math.min(vwid, 3 * 56), Compose.PODIUM_SHAPES.length, i -> Compose.PODIUM_SHAPES[i][1], () -> r.podiumShape, v -> r.podiumShape = v, on);
        y += adv(rowH);
        on = ((!sp && r.podium > 0 && r.podiumStyle != 3) || Compose.terrace(r));
        label("台阶", x, y, on);                           // 坡度: 45° 一格一级, 缓的 2-4 格一级（半砖），台阶也长出去那么多
        segment(vx, y, Math.min(vwid, 4 * 56), Compose.STEP_SLOPES.length, i -> Compose.STEP_SLOPES[i][1], () -> r.stepSlope, v -> r.stepSlope = v, on);
        y += adv(rowH);
        on = !sp && r.podium > 0;
        label("边距", x, y, on);                           // 台基边距: 墙脚到台基边走得过去几格（自动: 一格，窗台、门灯挡着就加宽；6–20: 三台、坛、大城台）
        segment(vx, y, Math.min(vwid, Compose.PODIUM_MARGINS.length * 34), Compose.PODIUM_MARGINS.length, i -> Compose.PODIUM_MARGINS[i][1], () -> r.podiumMargin, v -> r.podiumMargin = v, on);
        y += adv(rowH);
        on = !sp && (!r.round || r.podiumShape == 1) && r.podium > 0 && r.podiumStyle == 3;
        label("门洞", x, y, on);                           // (城台 only: the ways through it)
        segment(vx, y, Math.min(vwid, 5 * 52), Compose.TUNNEL_COUNTS.length, i -> Compose.TUNNEL_COUNTS[i], () -> r.tunnels, v -> r.tunnels = v, on);
        y += adv(rowH);
        label("基座", x, y, true);                         // what the platform and the stone under the house are made of
        pick(vx, y, vwid, "基座材料（台基、房子底下的石台、坡上的地基）", opts.bases, () -> Math.min(r.base, opts.bases.size() - 1), v -> r.base = v, true);
        y += adv(rowH);

        y += ui.section("屋顶", x, y + 2, w - 6) + 2;
        label("屋顶", x, y, !sp);
        if (r.round)                                       // 圆塔: roofs of its own
            segment(vx, y, Math.min(vwid, Compose.ROUND_ROOFS.length * 56), Compose.ROUND_ROOFS.length, i -> Compose.ROUND_ROOFS[i][1],
                    () -> r.roundRoof, v -> r.roundRoof = v, !sp);
        else
            segment(vx, y, vwid, opts.roofs.size(), i -> opts.roofs.get(i)[1], () -> r.roof, v -> r.roof = v, !sp);
        y += adv(rowH);
        label("瓦", x, y, true);
        pick(vx, y, vwid, "瓦", opts.themes, () -> r.theme, v -> r.theme = v, true);
        y += adv(rowH);
        label("檐", x, y, !sp);                   // 重檐 / 三重檐: one or two lower eaves under the roof, the walls up a band each
        segment(vx, y, Math.min(vwid, 3 * 60), 3, i -> new String[]{"单檐", "重檐", "三重檐"}[i], () -> r.tripleEave ? 2 : r.doubleEave ? 1 : 0, v -> {
            r.doubleEave = v >= 1;
            r.tripleEave = v == 2;
        }, !sp);
        y += adv(rowH);
        on = !sp && !(r.round && r.roundRoof == 4);
        label("坡度", x, y, on);                           // 自动 = as the roof's look has it; the others set it outright
        segment(vx, y, Math.min(vwid, 5 * 46), 5, i -> Compose.PITCHES[i], () -> r.pitch, v -> r.pitch = v, on);
        y += adv(rowH);
        on = !r.round && !sp;
        label("出檐", x, y, on);
        segment(vx, y, Math.min(vwid, 5 * 46), 5, i -> Compose.EAVE_OUTS[i], () -> r.overhang, v -> r.overhang = v, on);
        y += adv(rowH);
        label("翘角", x, y, on);
        segment(vx, y, Math.min(vwid, 5 * 46), 5, i -> Compose.LIFTS[i], () -> r.lift, v -> r.lift = v, on);
        y += adv(rowH);
        boolean beastsOn = on && (Compose.hipRoof(r, opts) || r.doubleEave);
        label("走兽", x, y, beastsOn);                     // (rides the hips: not on 悬山, 硬山, 卷棚 unless the lower eave of a 重檐 has them)
        segment(vx, y, Math.min(vwid, 6 * 44), Compose.BEAST_COUNTS.length, i -> Compose.BEAST_COUNTS[i], () -> r.beasts, v -> r.beasts = v, beastsOn);
        y += adv(rowH);
        label("斗拱", x, y, on);
        segment(vx, y, Math.min(vwid, 5 * 46), Compose.BRACKET_ROWS.length, i -> Compose.BRACKET_ROWS[i], () -> r.brackets, v -> r.brackets = v, on);
        y += adv(rowH);

        y += ui.section("墙身", x, y + 2, w - 6) + 2;
        label("墙", x, y, true);
        java.util.List<Integer> wi = Compose.wallChoices(opts, r.walls);
        java.util.List<String[]> wl = new ArrayList<>();
        for (int i : wi) wl.add(opts.walls.get(i));
        pick(vx, y, vwid, "墙", wl, () -> Math.max(0, wi.indexOf(r.walls)), v -> r.walls = wi.get(v), true);
        y += adv(rowH);
        on = !sp && r.openness != 2;               // (2 全敞开)
        label("墙样", x, y, on);                           // (敞开: no wall bays to style)
        pick(vx, y, vwid, "墙样（窗和墙段的样式）", opts.bayStyles, () -> Math.min(r.bayStyle, opts.bayStyles.size() - 1), v -> r.bayStyle = v, on);
        y += adv(rowH);
        on = !r.round && !sp;
        label("彩画", x, y, on);
        segment(vx, y, Math.min(vwid, 4 * 56), Compose.CAIHUAS.length, i -> Compose.CAIHUAS[i][1], () -> r.caihua, v -> r.caihua = v, on);
        y += adv(rowH);

        y += ui.section("廊与开敞", x, y + 2, w - 6) + 2;
        label("廊", x, y, on);                             // 前廊: posts along the front; 周围廊: a walk all round under the eaves
        segment(vx, y, vwid, Compose.GALLERIES.length, i -> Compose.GALLERIES[i], r::gallery, r::setGallery, on);
        y += adv(rowH);
        boolean gable = on && Compose.gableRoof(r, opts);
        label("山墙", x, y, gable);                         // 马头墙: the gable walls stepped up above the roof (徽派); 硬山, 悬山 only
        segment(vx, y, Math.min(vwid, 2 * 60), 2, i -> i == 0 ? "普通" : "马头墙", () -> r.horsehead ? 1 : 0, v -> r.horsehead = v == 1, gable);
        y += adv(rowH);
        label("出入口", x, y, on);                         // the sides with doors, each with its steps down the 台基 (前后: halls on an axis, gate halls)
        segment(vx, y, Math.min(vwid, 3 * 56), Compose.DOORS.length, i -> Compose.DOORS[i][1], () -> r.doors, v -> r.doors = v, on);
        y += adv(rowH);
        label("开敞", x, y, on);                           // 正面敞开: a stage; 全敞开: posts only (pavilions); 穿堂: the back a passage (gate halls; with 前 built as 前后)
        segment(vx, y, vwid, Compose.OPENNESS.length, i -> Compose.OPENNESS[i][1], () -> r.openness, v -> r.openness = v, on);
        y += adv(rowH);

        y += ui.section("每层：用途、开敞", x, y + 2, w - 6) + 2;
        java.util.List<String[]> fopen = java.util.Arrays.asList(Compose.FLOOR_OPEN);
        int useW = vwid * 3 / 5;
        for (int fl = 0; fl < r.storeys; fl++) {          // 每一层各选用途，开敞也能一层一层设（戏台一楼正面敞开、楼上封闭）
            final int idx = fl;
            label((fl + 1) + " 楼", x, y, !sp);
            pick(vx, y, useW, (fl + 1) + " 楼用途", opts.uses, () -> r.uses[idx], v -> r.uses[idx] = v, !sp);
            pick(vx + useW + 4, y, vwid - useW - 4, (fl + 1) + " 楼开敞（同整间 = 跟上面的「开敞」）", fopen, () -> r.floorOpen[idx],
                    v -> r.floorOpen[idx] = v, !sp && !r.round);
            y += adv(rowH);
        }

        y += ui.section("朝向与样子", x, y + 2, w - 6) + 2;
        label("正面", x, y, true);
        segment(vx, y, Math.min(vwid, 4 * 34), opts.fronts.size(), i -> opts.fronts.get(i)[1], () -> r.front, v -> r.front = v, true);
        y += adv(rowH);
        label("样子", x, y, true);                         // 换样子（计数，‹ 往回）：这一间整个换，或只换它的屋顶
        int half = (vwid - 6) / 2;
        stepper(vx, y, half, "这间", r.variant, v -> r.variant = v, true);
        stepper(vx + half + 6, y, vwid - half - 6, "屋顶", r.roofVariant, v -> r.roofVariant = v, true);
        y += rowH + 6;
        int third = (w - 14) / 3;
        ui.button(x, y, third, 18, "摆材料板", Ui.Kind.NORMAL, true, () -> {
            onClose();                                     // the board goes in front of the player, in the world
            PaletteBoard.start(r, Minecraft.getInstance());
        });
        ui.button(x + third + 4, y, third, 18, "删掉这间", Ui.Kind.DANGER, true, () -> Compose.ROOMS.remove(Compose.selected));
        ui.button(x + 2 * third + 8, y, third, 18, "样子套用到全部", Ui.Kind.NORMAL, n > 1, () -> {
            for (Compose.Room o : Compose.ROOMS) {
                o.roof = r.roof;
                o.walls = r.walls;
                o.theme = r.theme;
                o.bayStyle = r.bayStyle;
                o.sh = r.sh;
                o.podium = r.podium;
                o.base = r.base;
            }
            BuildTool.say("所有房间的屋顶、墙、墙样、瓦、层高、台基、基座都改成和房间 " + (Compose.selected + 1) + " 一样");
        });
        y += 22;
        // 新画的第一间房一开始就是这个样子；没设过就从 1 层、默认材料开始（不会自己带上次的）
        ui.button(x, y, third, 18, "设为默认样子", Ui.Kind.NORMAL, true, () -> {
            Compose.saveDefaultStyle(r);
            BuildTool.say("以后新画的第一间房就是房间 " + (Compose.selected + 1) + " 的样子（屋顶、墙、墙样、瓦、层数、层高、台基、用途）");
        });
        boolean has = Compose.hasDefaultStyle();
        ui.button(x + third + 4, y, third, 18, "清除默认", Ui.Kind.GHOST, has, () -> {
            Compose.clearDefaultStyle();
            BuildTool.say("清掉了默认样子：新画的第一间从 1 层、默认材料开始");
        });
        ui.text(has ? "新画的第一间用它" : "没设默认", x + 2 * third + 12, y + 5, third - 4, Ui.MUTED);
        y += 22;
        contentH = y + panelScroll - top;
        ui.popClip();
        ui.scrollbar(x + w - 2, top, bottom - top, contentH, bottom - top, panelScroll);
    }

    /** 存档面板：两页——「我的存档」（起名字存当前这栋；名字里写「文件夹/名字」就存进文件夹）和「预设库」（内置的单体，
     *  一层层文件夹）；上面一条路径点了回上一级，文件夹点了进去，设计点「载入」放到准星处。 */
    private void renderDesigns() {
        int x = rx, w = rw, bottom = footTop - 4;
        int y = bodyTop;
        int tw = Math.min(90, (w - 4) / 2);
        ui.chip(x, y, tw, 16, "我的存档", !libTab, true, 0, () -> {
            libTab = false;
            confirmDelete = null;
            saved = listNow();
        });
        ui.chip(x + tw + 4, y, tw, 16, "预设库", libTab, true, 0, () -> {
            libTab = true;
            confirmDelete = null;
            saved = listNow();
        });
        y += 22;
        // the path: the root and each folder down to here, each a way back up
        String rel = libTab ? relLib : relMine;
        String[] parts = rel.isEmpty() ? new String[0] : rel.split("/");
        int px = x;
        String rootName = libTab ? "预设库" : "我的存档";
        for (int i = -1; i < parts.length; i++) {
            String seg = i < 0 ? rootName : parts[i];
            final String to = i < 0 ? "" : String.join("/", java.util.Arrays.copyOfRange(parts, 0, i + 1));
            int sw = Math.min(ui.textW(seg) + 10, Math.max(30, x + w - px));
            boolean last = i == parts.length - 1;
            ui.button(px, y, sw, 14, seg, last ? Ui.Kind.NORMAL : Ui.Kind.GHOST, !last, () -> enter(to));
            px += sw;
            if (!last && px < x + w - 10) {
                ui.text("›", px + 2, y + 3, Ui.MUTED);
                px += 10;
            }
            if (px > x + w - 30) break;
        }
        y += 18;
        if (!libTab) {
            int bw = ui.textW("存下这栋") + 16;
            nameBox.setX(x + 1);
            nameBox.setY(y + 1);
            nameBox.setWidth(w - bw - 8);
            ui.hit(x, y, w - bw - 6, 18, () -> {
            }, null);
            ui.button(x + w - bw, y, bw, 18, "存下这栋", Ui.Kind.PRIMARY, !Compose.ROOMS.isEmpty(), this::saveCurrent);
            y += 22;
        }
        nameBox.setVisible(designsOpen && !libTab && picker == null);
        renderList(x, w, y, bottom, libTab ? "预设：每栋的设置都在里面，载入以后能接着改" : "名字里写「文件夹/名字」就存进文件夹；都自动存：正在画的是「自动存档」");
    }

    private void enter(String rel) {
        if (libTab) relLib = rel;
        else relMine = rel;
        confirmDelete = null;
        designScroll = 0;
        saved = listNow();
    }

    /** 文件夹和设计的列表：文件夹点了进去；设计一行名字、说明，「载入」，自己的存档还有「删」。 */
    private void renderList(int x, int w, int y, int bottom, String hint) {
        if (!designMsg.isEmpty()) ui.text(designMsg, x, y, w, Ui.GOLD);
        else ui.text(hint, x, y, w, Ui.MUTED);
        y += 14;
        int top = y, rowH = 22, n = saved.size();
        int visible = Math.max(1, (bottom - top) / rowH);
        designScroll = Math.max(0, Math.min(designScroll, Math.max(0, n - visible)));
        ui.scroll(x, top, w, bottom - top, d -> designScroll = Math.max(0, Math.min(Math.max(0, n - visible), designScroll + d)));
        ui.pushClip(x, top, w, bottom - top);
        int lw2 = ui.textW("载入") + 14, dw2 = libTab ? 0 : ui.textW("确定删") + 12;
        for (int i = designScroll; i < Math.min(n, designScroll + visible + 1); i++) {
            Compose.Entry s = saved.get(i);
            int ry = top + (i - designScroll) * rowH;
            boolean hov = ui.over(x, ry, w - 4, rowH - 3);
            Ui.round(ui.g, x, ry, w - 4, rowH - 3, hov ? Ui.ITEM_HOVER : Ui.ITEM);
            if (s.dir()) {                                 // a folder: its name, how many in it, click to go in
                ui.g.fill(x + 6, ry + 6, x + 14, ry + 13, 0xFFE6C47A);
                ui.g.fill(x + 6, ry + 4, x + 10, ry + 6, 0xFFE6C47A);
                ui.text(s.name(), x + 18, ry + 6, w - 40, Ui.TEXT);
                ui.text("›", x + w - 14, ry + 6, Ui.SUB);
                String rel = libTab ? relLib : relMine;
                String to = rel.isEmpty() ? s.name() : rel + "/" + s.name();
                ui.hit(x, ry, w - 4, rowH - 3, () -> enter(to), null);
                continue;
            }
            boolean special = s.name().equals("自动存档") || s.name().equals("上次生成") || s.name().startsWith(Compose.AUTO_KEPT);
            int textW = w - 4 - lw2 - dw2 - 16;
            ui.text(s.name(), x + 6, ry + 2, textW, special ? Ui.GOLD : Ui.TEXT);
            String sub = !s.site().isEmpty() ? s.note() : s.rooms() + " 间" + (!s.note().isEmpty() ? " · " + s.note() : s.time().length() >= 16 ? " · " + s.time().substring(5, 16) : "");
            ui.text(sub, x + 6, ry + 11, textW, Ui.MUTED);
            int bx = x + w - 4 - dw2 - (dw2 > 0 ? 4 : 0) - lw2 - 4;
            ui.button(bx, ry + 2, lw2, 15, "载入", Ui.Kind.PRIMARY, true, () -> load(s));
            if (!libTab) {
                boolean confirm = s.path().equals(confirmDelete);
                ui.button(bx + lw2 + 4, ry + 2, dw2, 15, confirm ? "确定删" : "删", confirm ? Ui.Kind.DANGER : Ui.Kind.GHOST, true, () -> {
                    if (s.path().equals(confirmDelete)) {
                        Compose.deleteDesign(s.path());
                        confirmDelete = null;
                        designMsg = "删了「" + s.name() + "」";
                        saved = listNow();
                    } else {
                        confirmDelete = s.path();
                    }
                });
            }
        }
        ui.popClip();
        ui.scrollbar(x + w - 2, top, bottom - top, n, visible, designScroll);
        if (n == 0) ui.wrap(libTab ? "预设库是空的（" + com.flyingeaves.backend.GeneratorProcess.hint() + "）" : "这里还没有存档。画几间房，起个名字点「存下这栋」。", x + 2, top + 4, w - 4, bottom, Ui.MUTED);
    }

    private void saveCurrent() {
        String name = nameBox.getValue().trim();
        if (name.isEmpty()) name = defaultName();
        if (!relMine.isEmpty() && !name.contains("/")) name = relMine + "/" + name;   // (into the folder being looked at)
        String err = Compose.saveDesign(name);
        designMsg = err != null ? err : "存好了「" + name + "」（" + Compose.ROOMS.size() + " 间）";
        saved = listNow();
        lastName = defaultName();                         // the next save gets a fresh name
        nameBox.setValue(lastName);
        nameBox.setFocused(false);
        setFocused(null);
    }

    private void load(Compose.Entry s) {
        if (!s.site().isEmpty()) {                       // 宫城：not a room list - the same request the buttons sent (a new seed each time)
            designsOpen = false;
            onClose();
            BuildTool.requestSite(s.site());
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.core.BlockPos at = BuildTool.target(mc);
        if (at == null) at = mc.player.blockPosition().below();
        String err = Compose.loadDesign(s.path(), at);
        if (err != null) {
            designMsg = err;
            return;
        }
        designsOpen = false;
        onClose();
        BuildTool.say("载入了「" + s.name() + "」：" + Compose.ROOMS.size() + " 间房，放在准星指的地方；用「挪动」工具挪，按" + Compose.key() + "细调");
    }

    /** 「‹ 第 n 个 ›」：换样子的计数，往回退到 0（最初那个）为止。 */
    private void stepper(int x, int y, int w, String label, int value, IntConsumer set, boolean enabled) {
        int bw = 16;
        ui.button(x, y, bw, 16, "‹", Ui.Kind.NORMAL, enabled && value > 0, () -> set.accept(value - 1));
        String mid = label + " 第 " + (value + 1) + " 个";
        ui.text(mid, x + bw + 4, y + 4, w - 2 * bw - 8, enabled ? Ui.TEXT : Ui.MUTED);
        ui.button(x + w - bw, y, bw, 16, "›", Ui.Kind.NORMAL, enabled, () -> set.accept(value + 1));
    }

    /** 一排小块，平分宽度；字放不下就折成几行（不截断）。lastH 记下占了多高，行就往下挪那么多。 */
    private void segment(int x, int y, int w, int count, java.util.function.IntFunction<String> label, IntSupplier get, IntConsumer set, boolean enabled) {
        int gap = 3, need = 14;
        for (int i = 0; i < count; i++) need = Math.max(need, ui.chipW(label.apply(i)));
        int cols = Math.max(1, Math.min(count, (w + gap) / (need + gap)));
        int rows = (count + cols - 1) / cols;
        int cwid = Math.max(14, (w - (cols - 1) * gap) / cols);
        int cur = get.getAsInt();
        for (int i = 0; i < count; i++) {
            final int idx = i;
            ui.chip(x + (i % cols) * (cwid + gap), y + (i / cols) * (16 + gap), cwid, 16, label.apply(i), i == cur, enabled, 0, () -> set.accept(idx));
        }
        lastH = rows * (16 + gap) - gap;
    }

    private int lastH = 16;

    /** How far the next row starts: a row, or more when the chips wrapped. */
    private int adv(int rowH) {
        int a = Math.max(rowH, lastH + 4);
        lastH = 16;
        return a;
    }

    /** A row's label: dim when the row does nothing for this room. */
    private void label(String s, int x, int y, boolean enabled) {
        ui.text(s, x, y + 5, labW - 4, enabled ? Ui.SUB : Ui.MUTED);
    }

    private int labW = 60;

    /** 选择框：点开选择面板。 */
    private void pick(int x, int y, int w, String title, List<String[]> list, IntSupplier get, IntConsumer set, boolean enabled) {
        String[] cur = list.get(get.getAsInt());
        ui.select(x, y, w, 16, cur[1], swatchOf(cur), enabled,
                () -> picker = new Ui.Picker(title, list, get, set, i -> swatchOf(list.get(i))));
    }

    private void renderFooter() {
        ui.hline(cx + 1, footTop, cw - 2);
        int y = footTop + 6, x = cx + 10;
        ui.text("境界", x, y + 4, Ui.SUB);
        x += ui.textW("境界") + 6;
        String gen = "生成", re = "换个样子", back = "‹", un = "拆掉上一栋";
        int gw = ui.textW(gen) + 28, rew = ui.textW(re) + 16, bkw = ui.textW(back) + 14, uw = ui.textW(un) + 16;
        int right = cx + cw - 10;
        int btnStart = right - gw - rew - bkw - uw - 12;
        int avail = btnStart - 8 - x;
        int count = opts.tiers.size();
        int cwid = Math.max(16, Math.min(ui.chipW("金丹"), (avail - (count - 1) * 3) / count));
        for (int i = 0; i < count; i++) {
            final int idx = i;
            ui.chip(x + i * (cwid + 3), y, cwid, 16, opts.tierCn.get(i), i == Compose.tier, true, 0, () -> Compose.tier = idx);
        }
        boolean any = !Compose.ROOMS.isEmpty();
        int bx = btnStart;
        ui.button(bx, y - 1, uw, 18, un, Ui.Kind.NORMAL, true, () -> {     // 世界里：拆掉上一栋放下的
            onClose();
            BuildTool.undo();
        });
        bx += uw + 4;
        // 换样子用计数（像 TMA）：「‹」退回上一个样子，一直退到最初那个
        ui.button(bx, y - 1, bkw, 18, back, Ui.Kind.NORMAL, any && Compose.look > 0, () -> Compose.look = Math.max(0, Compose.look - 1));
        bx += bkw + 4;
        ui.button(bx, y - 1, rew, 18, re, Ui.Kind.NORMAL, any, () -> Compose.look++);
        ui.button(right - gw, y - 1, gw, 18, gen, Ui.Kind.PRIMARY, any, this::generate);
    }

    /** 生成这一栋，然后单体建造就结束了（房间表清空，下次按 H 搭下一栋）；不满意可以「换一个」或「撤销」。 */
    private void generate() {
        if (Compose.ROOMS.isEmpty()) return;
        onClose();
        Compose.generate(opts, opts.tiers.get(Compose.tier), Compose.seed);
    }

    // ------------------------------------------------------------------ 输入

    private void submitSay() {
        String text = sayBox.getValue().trim();
        if (text.isEmpty()) return;
        onClose();
        BuildTool.requestSentence(text);
    }

    @Override
    public boolean mouseClicked(double realX, double realY, int button) {
        double mouseX = realX / k, mouseY = realY / k;
        if (sayBox != null && sayBox.isVisible() && sayBox.isMouseOver(mouseX, mouseY)) {
            sayBox.setFocused(true);
            if (nameBox != null) nameBox.setFocused(false);
            if (townWBox != null) townWBox.setFocused(false);
            if (townDBox != null) townDBox.setFocused(false);
            setFocused(sayBox);
            sayBox.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        if (sayBox != null) sayBox.setFocused(false);
        for (EditBox b : new EditBox[]{townWBox, townDBox}) {
            if (b == null) continue;
            boolean in = b.isVisible() && b.isMouseOver(mouseX, mouseY);
            b.setFocused(in);
            if (in) {
                if (nameBox != null) nameBox.setFocused(false);
                setFocused(b);
                b.mouseClicked(mouseX, mouseY, button);
                return true;
            }
        }
        if (nameBox != null && nameBox.isVisible()) {
            boolean in = nameBox.isMouseOver(mouseX, mouseY);
            nameBox.setFocused(in);
            if (in) {
                setFocused(nameBox);
                nameBox.mouseClicked(mouseX, mouseY, button);
                return true;
            }
            setFocused(null);
        }
        if (ui.click(mouseX, mouseY, button)) return true;
        onClose();                       // a click outside the card goes back to drawing
        return true;
    }

    @Override
    public boolean mouseScrolled(double realX, double realY, double delta) {
        return ui.wheel(realX / k, realY / k, delta) || super.mouseScrolled(realX, realY, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (sayBox != null && sayBox.isVisible() && sayBox.isFocused()) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                sayBox.setFocused(false);
                setFocused(null);
            } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                submitSay();
            } else {
                sayBox.keyPressed(key, scan, mods);
            }
            return true;
        }
        for (EditBox b : new EditBox[]{townWBox, townDBox}) {
            if (b == null || !b.isVisible() || !b.isFocused()) continue;
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                b.setFocused(false);
                setFocused(null);
            } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                requestTown();
            } else {
                b.keyPressed(key, scan, mods);
            }
            return true;
        }
        if (nameBox != null && nameBox.isVisible() && nameBox.isFocused()) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                nameBox.setFocused(false);
                setFocused(null);
            } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                saveCurrent();
            } else {
                nameBox.keyPressed(key, scan, mods);
            }
            return true;
        }
        if (picker != null && key == GLFW.GLFW_KEY_ESCAPE) {
            picker = null;
            return true;
        }
        if (picker == null && BuildTool.COMPOSE.matches(key, scan)) {   // H again closes the table
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (sayBox != null && sayBox.isVisible() && sayBox.isFocused()) return sayBox.charTyped(c, mods);
        if (nameBox != null && nameBox.isVisible() && nameBox.isFocused()) return nameBox.charTyped(c, mods);
        for (EditBox b : new EditBox[]{townWBox, townDBox})
            if (b != null && b.isVisible() && b.isFocused()) return b.charTyped(c, mods);
        return super.charTyped(c, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
