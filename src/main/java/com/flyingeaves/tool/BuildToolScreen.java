package com.flyingeaves.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * 建造菜单（开发版，O）：选一种建筑，选境界和种子，放到准星指着的地方。居中的深色卡片：左边搜索、分组、建筑列表，
 * 右边选中的建筑和放置按钮。位置都按卡片大小算，文字放不下就截断，所以界面放大也不溢出。
 */
public class BuildToolScreen extends Screen {
    /** name: the kind's id; cn: its name in the game's language (kinds.json's Chinese, or the lang file's English); zh: the Chinese. */
    record Kind(String name, String cn, String zh, List<String> tiers, String group) {
    }

    /** {kinds.json 的分组, 短名字}。 */
    private static final String[][] GROUPS = L.pairs("menu.group", "", "凡人·住宅", "凡人·城镇", "修士", "宗门·特殊地点");
    private static final Random RNG = new Random();
    private static String rememberedKind;
    private static String rememberedTier;
    private static String rememberedGroup = "";
    private static int listScroll;

    private final Ui ui = new Ui();
    private final List<Kind> kinds = new ArrayList<>();
    private final List<Kind> shown = new ArrayList<>();
    private List<String> tierIds = List.of();
    private List<String> tierCn = List.of();
    private EditBox search;
    private EditBox seedBox;
    private Kind selected;
    private String tier;
    private String loadError;
    // 布局
    private int cx, cy, cw, ch, lx, lw, rx, rw, bodyTop, bodyBottom, listTop;

    public BuildToolScreen() {
        super(Component.literal(L.t("menu.title")));
        L.sync();
        try {
            JsonObject root = JsonParser.parseString(Files.readString(BuildTool.RUN.resolve("kinds.json"), StandardCharsets.UTF_8)).getAsJsonObject();
            tierIds = strings(root.getAsJsonArray("tiers"));
            tierCn = new ArrayList<>(strings(root.getAsJsonArray("tier_cn")));
            for (int i = 0; i < tierCn.size() && i < tierIds.size(); i++) tierCn.set(i, L.name("tier", tierIds.get(i), tierCn.get(i)));
            for (JsonElement e : root.getAsJsonArray("kinds")) {
                JsonObject k = e.getAsJsonObject();
                String id = k.get("name").getAsString(), cn = k.get("cn").getAsString();
                kinds.add(new Kind(id, L.name("kind", id, cn), cn, strings(k.getAsJsonArray("tiers")), k.get("group").getAsString()));
            }
            kinds.sort((a, b) -> a.group.equals(b.group) ? 0 : a.group.compareTo(b.group));
        } catch (Exception e) {
            loadError = L.t("menu.no_kinds", com.flyingeaves.backend.GeneratorProcess.hint());
        }
    }

    private static List<String> strings(JsonArray a) {
        List<String> out = new ArrayList<>();
        for (JsonElement e : a) out.add(e.getAsString());
        return out;
    }

    private String tierName(String id) {
        int i = tierIds.indexOf(id);
        return i >= 0 && i < tierCn.size() ? tierCn.get(i) : id;
    }

    private static String shortGroup(String g) {
        for (String[] s : GROUPS) if (s[0].equals(g)) return s[1];
        return g;
    }

    @Override
    protected void init() {
        cw = Math.min(width - 12, 560);
        ch = Math.min(height - 12, 330);
        cx = (width - cw) / 2;
        cy = (height - ch) / 2;
        bodyTop = cy + 26;
        bodyBottom = cy + ch - 8;
        lx = cx + 8;
        lw = Math.max(110, Math.min(240, (cw - 26) * 44 / 100));
        rx = lx + lw + 10;
        rw = cx + cw - 8 - rx;
        String old = search == null ? "" : search.getValue();
        search = new EditBox(font, lx + 6, bodyTop + 4, lw - 12, 10, Component.literal(L.t("menu.search")));
        search.setBordered(false);
        search.setTextColor(Ui.TEXT);
        search.setHint(Component.literal(L.t("menu.search_hint")));
        search.setValue(old);
        search.setResponder(s -> refill());
        addRenderableWidget(search);
        listTop = bodyTop + 22 + 20;
        String oldSeed = seedBox == null ? Integer.toString(RNG.nextInt(100000)) : seedBox.getValue();
        int sl = font.width(L.t("menu.seed")) + 8, rnd = font.width(L.t("menu.random")) + 16;
        seedBox = new EditBox(font, rx + sl + 6, 0, Math.max(30, rw - sl - rnd - 4 - 12), 10, Component.literal(L.t("menu.seed")));
        seedBox.setBordered(false);
        seedBox.setTextColor(Ui.TEXT);
        seedBox.setMaxLength(9);
        seedBox.setValue(oldSeed);
        addRenderableWidget(seedBox);
        refill();
        if (selected == null && rememberedKind != null) {
            for (Kind k : kinds) if (k.name.equals(rememberedKind)) select(k);
        }
    }

    private void refill() {
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        shown.clear();
        for (Kind k : kinds) {
            if (!rememberedGroup.isEmpty() && !k.group.equals(rememberedGroup)) continue;
            if (q.isEmpty() || k.cn.toLowerCase(Locale.ROOT).contains(q) || k.zh.contains(q) || k.name.contains(q) || k.group.contains(q)) shown.add(k);
        }
    }

    /** Development (dev pipe {@code screen menu <kind>}): the kind shown when the menu opens. */
    static void remember(String kind) {
        rememberedKind = kind;
    }

    void select(Kind k) {
        selected = k;
        tier = rememberedTier != null && k.tiers.contains(rememberedTier) ? rememberedTier : k.tiers.get(0);
    }

    private void place() {
        if (selected == null) {
            BuildTool.say(L.t("menu.pick_first"));
            return;
        }
        int seed;
        try {
            seed = Integer.parseInt(seedBox.getValue().trim());
        } catch (NumberFormatException e) {
            seed = RNG.nextInt(100000);
        }
        BlockPos ground = BuildTool.target(minecraft);
        if (ground == null) {
            BuildTool.say(L.t("menu.no_ground"));
            return;
        }
        rememberedKind = selected.name;
        rememberedTier = tier;
        String facing = BuildTool.facingToPlayer(minecraft);
        onClose();
        BuildTool.place(selected.name, tier, seed, ground, facing);
    }

    // ------------------------------------------------------------------ 画

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        ui.begin(g, font, mouseX, mouseY);
        g.fill(0, 0, width, height, Ui.DIM);
        ui.card(cx, cy, cw, ch);
        ui.hit(cx, cy, cw, ch, () -> {
        }, null);
        // 标题栏
        String title = L.t("menu.title"), count = L.t("menu.kinds_count", kinds.size());
        ui.text(title, cx + 10, cy + 9, Ui.TEXT);
        String keyHint = L.t("menu.key_hint", BuildTool.COMPOSE.getTranslatedKeyMessage().getString());
        int closeX = cx + cw - 20;
        int hintW = Math.min(ui.textW(keyHint), closeX - (cx + 22 + ui.textW(title) + ui.textW(count)) - 8);
        ui.text(count, cx + 14 + ui.textW(title), cy + 9, Ui.MUTED);
        if (hintW > 30) ui.text(keyHint, closeX - 6 - hintW, cy + 9, hintW, Ui.MUTED);
        ui.button(closeX, cy + 5, 14, 14, "×", Ui.Kind.GHOST, true, this::onClose);
        ui.hline(cx + 1, cy + 22, cw - 2);
        if (loadError != null) {
            ui.wrap(loadError, cx + 10, bodyTop + 4, cw - 20, bodyBottom, Ui.DANGER);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }
        renderLeft();
        renderRight();
        super.render(g, mouseX, mouseY, partialTick);           // the two text fields on top
    }

    private void renderLeft() {
        ui.field(lx, bodyTop, lw, 18, search.isFocused());
        // 分组
        int x = lx, y = bodyTop + 22;
        for (String[] gp : GROUPS) {
            int w = ui.chipW(gp[1]);
            if (x + w > lx + lw) break;
            ui.chip(x, y, w, 15, gp[1], rememberedGroup.equals(gp[0]), true, 0, () -> {
                rememberedGroup = gp[0];
                listScroll = 0;
                refill();
            });
            x += w + 3;
        }
        // 列表
        int top = listTop, h = bodyBottom - top, rowH = 15;
        int visible = Math.max(1, h / rowH);
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, shown.size() - visible)));
        ui.scroll(lx, top, lw, h, d -> listScroll = Math.max(0, Math.min(Math.max(0, shown.size() - visible), listScroll + d * 3)));
        ui.pushClip(lx, top, lw, h);
        for (int i = listScroll; i < Math.min(shown.size(), listScroll + visible + 1); i++) {
            Kind k = shown.get(i);
            int ry = top + (i - listScroll) * rowH;
            boolean sel = k == selected, hov = ui.over(lx, ry, lw - 4, rowH);
            if (sel) {
                Ui.round(ui.g, lx, ry, lw - 4, rowH - 1, Ui.ACCENT_SOFT);
                ui.g.fill(lx, ry + 2, lx + 2, ry + rowH - 3, Ui.ACCENT);
            } else if (hov) {
                Ui.round(ui.g, lx, ry, lw - 4, rowH - 1, Ui.ITEM);
            }
            String tag = rememberedGroup.isEmpty() ? shortGroup(k.group) : "";
            int tagW = tag.isEmpty() ? 0 : ui.textW(tag) + 6;
            ui.text(k.cn, lx + 7, ry + 4, lw - 18 - tagW, sel ? Ui.GOLD : hov ? Ui.TEXT : 0xFFC9D0D9);
            if (!tag.isEmpty()) ui.text(tag, lx + lw - 8 - tagW + 2, ry + 4, Ui.MUTED);
            ui.hit(lx, ry, lw - 4, rowH, () -> select(k), null);
        }
        ui.popClip();
        if (shown.isEmpty()) ui.text(L.t("menu.no_match"), lx + 7, top + 6, lw - 10, Ui.MUTED);
        ui.scrollbar(lx + lw - 2, top, h, shown.size(), visible, listScroll);
    }

    private void renderRight() {
        int x = rx, y = bodyTop, w = rw;
        if (selected == null) {
            ui.text(L.t("menu.choose"), x, y + 2, w, Ui.SUB);
            ui.wrap(L.t("menu.choose_text"), x, y + 16, w, bodyBottom, Ui.MUTED);
            seedBox.visible = false;
            return;
        }
        seedBox.visible = true;
        ui.text(selected.cn, x, y + 2, w, Ui.GOLD);
        ui.text(shortGroup(selected.group) + " · " + selected.name, x, y + 13, w, Ui.MUTED);
        y += 28;
        // 境界
        ui.text(L.t("footer.tier"), x, y, Ui.SUB);
        y += 11;
        int cx0 = x;
        for (int i = 0; i < tierIds.size(); i++) {
            String id = tierIds.get(i), cn = tierCn.get(i);
            int cwid = ui.chipW(cn);
            if (cx0 + cwid > x + w) {
                cx0 = x;
                y += 18;
            }
            boolean can = selected.tiers.contains(id);
            ui.chip(cx0, y, cwid, 15, cn, id.equals(tier), can, 0, () -> tier = id);
            cx0 += cwid + 3;
        }
        y += 22;
        // 种子
        int sl = ui.textW(L.t("menu.seed")) + 8, rnd = ui.textW(L.t("menu.random")) + 16;
        ui.text(L.t("menu.seed"), x, y + 5, Ui.SUB);
        ui.field(x + sl, y, w - sl - rnd - 4, 18, seedBox.isFocused());
        seedBox.setX(x + sl + 6);
        seedBox.setY(y + 5);
        ui.button(x + w - rnd, y, rnd, 18, L.t("menu.random"), Ui.Kind.NORMAL, true, () -> seedBox.setValue(Integer.toString(RNG.nextInt(100000))));
        y += 26;
        ui.button(x, y, w, 20, L.t("menu.place"), Ui.Kind.PRIMARY, true, this::place);
        y += 24;
        int half = (w - 4) / 2;
        ui.button(x, y, half, 18, L.t("menu.reroll"), Ui.Kind.NORMAL, true, () -> {
            onClose();
            BuildTool.reroll();
        });
        ui.button(x + half + 4, y, w - half - 4, 18, L.t("menu.undo_last"), Ui.Kind.NORMAL, true, () -> {
            onClose();
            BuildTool.undo();
        });
        y += 26;
        BlockPos t = BuildTool.target(minecraft);
        if (y + 9 <= bodyBottom) {
            ui.text(t == null ? L.t("compose.no_ground") : L.t("menu.place_at", t.getX(), t.getY(), t.getZ()), x, y, w,
                    t == null ? Ui.DANGER : Ui.SUB);
        }
        ui.wrap(L.t("menu.place_note"), x, y + 13, w, bodyBottom, Ui.MUTED);
    }

    // ------------------------------------------------------------------ 输入

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;      // text fields first
        setFocused(null);
        if (ui.click(mouseX, mouseY, button)) return true;
        onClose();                                                          // a click outside the card closes it
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return ui.wheel(mouseX, mouseY, delta) || super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            place();
            return true;
        }
        if (!(getFocused() instanceof EditBox) && BuildTool.OPEN.matches(key, scan)) {   // O again closes the menu
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
