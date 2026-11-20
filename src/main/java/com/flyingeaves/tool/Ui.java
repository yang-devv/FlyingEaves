package com.flyingeaves.tool;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * 建造工具界面用的一套小控件（即时模式）：每帧画的时候登记可点的区域，点击、滚轮按这一帧登记的区域分发。
 * 样式是扁平的深色卡片 + 玉绿色强调色，不用原版的石头按钮。圆角用三段填充拼。
 */
final class Ui {
    // 颜色
    static final int DIM = 0x8A06080B;
    static final int CARD = 0xF2141920, CARD_EDGE = 0xFF2B333E, LINE = 0xFF262D37;
    static final int SURFACE = 0xFF1A2028, ITEM = 0xFF222932, ITEM_HOVER = 0xFF2D3540, ITEM_PRESSED = 0xFF36404D;
    static final int ACCENT = 0xFF36B78F, ACCENT_HOVER = 0xFF4BCBA3, ACCENT_SOFT = 0x3036B78F, ACCENT_TEXT = 0xFF0C1A15;
    static final int TEXT = 0xFFE9EDF2, SUB = 0xFF9EA8B6, MUTED = 0xFF636E7D, GOLD = 0xFFE6C47A, DANGER = 0xFFE8737C;

    enum Kind { NORMAL, PRIMARY, GHOST, DANGER }

    record Hit(int x0, int y0, int x1, int y1, Runnable left, Runnable right) {
    }

    record Scroll(int x0, int y0, int x1, int y1, IntConsumer by) {
    }

    final List<Hit> hits = new ArrayList<>();
    final List<Scroll> scrolls = new ArrayList<>();
    private final Deque<int[]> clips = new ArrayDeque<>();
    GuiGraphics g;
    Font font;
    int mx, my;
    /** The pose scale the screen draws at (a large GUI scale is drawn smaller): scissor boxes are in real GUI units. */
    float scale = 1f;

    void begin(GuiGraphics g, Font font, int mx, int my) {
        this.g = g;
        this.font = font;
        this.mx = mx;
        this.my = my;
        hits.clear();
        scrolls.clear();
        clips.clear();
    }

    // ------------------------------------------------------------------ 形状

    static void round(GuiGraphics g, int x, int y, int w, int h, int c) {
        if (w <= 0 || h <= 0) return;
        if (w < 4 || h < 4) {
            g.fill(x, y, x + w, y + h, c);
            return;
        }
        g.fill(x + 2, y, x + w - 2, y + h, c);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, c);
        g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, c);
        g.fill(x, y + 2, x + 1, y + h - 2, c);
        g.fill(x + w - 1, y + 2, x + w, y + h - 2, c);
    }

    void card(int x, int y, int w, int h) {
        round(g, x - 1, y - 1, w + 2, h + 2, CARD_EDGE);
        round(g, x, y, w, h, CARD);
    }

    void hline(int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, LINE);
    }

    // ------------------------------------------------------------------ 裁剪、点击

    void pushClip(int x, int y, int w, int h) {
        int[] r = {x, y, x + w, y + h};
        if (!clips.isEmpty()) {
            int[] p = clips.peek();
            r = new int[]{Math.max(r[0], p[0]), Math.max(r[1], p[1]), Math.min(r[2], p[2]), Math.min(r[3], p[3])};
        }
        clips.push(r);
        enable(r);
    }

    void popClip() {
        clips.pop();
        g.disableScissor();
        if (!clips.isEmpty()) enable(clips.peek());       // (back to the clip outside it)
    }

    private void enable(int[] r) {
        g.enableScissor(Math.round(r[0] * scale), Math.round(r[1] * scale), Math.round(Math.max(r[0], r[2]) * scale),
                Math.round(Math.max(r[1], r[3]) * scale));
    }

    boolean over(int x, int y, int w, int h) {
        if (mx < x || my < y || mx >= x + w || my >= y + h) return false;
        if (!clips.isEmpty()) {
            int[] c = clips.peek();
            return mx >= c[0] && my >= c[1] && mx < c[2] && my < c[3];
        }
        return true;
    }

    void hit(int x, int y, int w, int h, Runnable left, Runnable right) {
        int x0 = x, y0 = y, x1 = x + w, y1 = y + h;
        if (!clips.isEmpty()) {
            int[] c = clips.peek();
            x0 = Math.max(x0, c[0]);
            y0 = Math.max(y0, c[1]);
            x1 = Math.min(x1, c[2]);
            y1 = Math.min(y1, c[3]);
        }
        if (x1 > x0 && y1 > y0) hits.add(new Hit(x0, y0, x1, y1, left, right));
    }

    void scroll(int x, int y, int w, int h, IntConsumer by) {
        scrolls.add(new Scroll(x, y, x + w, y + h, by));
    }

    /** 鼠标按下：从最上层（最后登记的）往下找。button 0 左键，1 右键。 */
    boolean click(double x, double y, int button) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (x >= h.x0 && y >= h.y0 && x < h.x1 && y < h.y1) {
                Runnable r = button == 1 ? h.right : h.left;
                if (r != null) r.run();
                return true;
            }
        }
        return false;
    }

    boolean wheel(double x, double y, double delta) {
        for (int i = scrolls.size() - 1; i >= 0; i--) {
            Scroll s = scrolls.get(i);
            if (x >= s.x0 && y >= s.y0 && x < s.x1 && y < s.y1) {
                s.by.accept(delta > 0 ? -1 : 1);
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ 文字

    /** Development (dev pipe {@code texts}): every string drawn in the next frame, " [cut]" where it did not fit. */
    static java.util.List<String> CAPTURE;

    static void cap(String s, boolean cut) {
        java.util.List<String> c = CAPTURE;
        if (c != null && s != null && !s.isEmpty()) c.add(cut ? s + " [cut]" : s);
    }

    String fit(String s, int w) {
        if (w <= 0) return "";
        if (font.width(s) <= w) return s;
        return font.plainSubstrByWidth(s, Math.max(0, w - font.width("…"))) + "…";
    }

    void text(String s, int x, int y, int color) {
        cap(s, false);
        g.drawString(font, s, x, y, color, false);
    }

    void text(String s, int x, int y, int w, int color) {
        String f = fit(s, w);
        cap(s, !f.equals(s));
        g.drawString(font, f, x, y, color, false);
    }

    void center(String s, int cx, int y, int w, int color) {
        String f = fit(s, w);
        cap(s, !f.equals(s));
        g.drawString(font, f, cx - font.width(f) / 2, y, color, false);
    }

    /** 自动换行，只画放得下的行；返回画到的 y。 */
    int wrap(String s, int x, int y, int w, int bottom, int color) {
        if (w < 30) return y;
        List<FormattedCharSequence> lines = font.split(Component.literal(s), w);
        cap(s, lines.size() * 10 > bottom - y + 1);
        for (FormattedCharSequence line : lines) {
            if (y + 9 > bottom) break;
            g.drawString(font, line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    int textW(String s) {
        return font.width(s);
    }

    // ------------------------------------------------------------------ 控件

    /** 扁平按钮。 */
    void button(int x, int y, int w, int h, String label, Kind kind, boolean enabled, Runnable action) {
        boolean hov = enabled && over(x, y, w, h);
        int bg, fg;
        switch (kind) {
            case PRIMARY -> {
                bg = !enabled ? ITEM : hov ? ACCENT_HOVER : ACCENT;
                fg = enabled ? ACCENT_TEXT : MUTED;
            }
            case GHOST -> {
                bg = hov ? ITEM_HOVER : 0;
                fg = enabled ? (hov ? TEXT : SUB) : MUTED;
            }
            case DANGER -> {
                bg = hov ? 0x40E8737C : ITEM;
                fg = enabled ? DANGER : MUTED;
            }
            default -> {
                bg = hov ? ITEM_HOVER : ITEM;
                fg = enabled ? TEXT : MUTED;
            }
        }
        if (bg != 0) round(g, x, y, w, h, bg);
        center(label, x + w / 2, y + (h - 8) / 2, w - 6, fg);
        if (enabled && action != null) hit(x, y, w, h, action, null);
    }

    /** A section title in the panel: the accent colour, a line on to the right. Returns its height. */
    int section(String title, int x, int y, int w) {
        text(title, x, y + 3, w, ACCENT);
        int tw = Math.min(w, font.width(title) + 6);
        g.fill(x + tw, y + 7, x + w, y + 8, LINE);
        return 14;
    }

    /** 选项小块：选中的是强调色。swatch 不为 0 时左边画一个色块。 */
    void chip(int x, int y, int w, int h, String label, boolean selected, boolean enabled, int swatch, Runnable action) {
        boolean hov = enabled && over(x, y, w, h);
        int bg = selected ? (enabled ? ACCENT : ITEM_PRESSED) : hov ? ITEM_HOVER : ITEM;
        int fg = selected ? (enabled ? ACCENT_TEXT : SUB) : enabled ? (hov ? TEXT : SUB) : MUTED;
        round(g, x, y, w, h, bg);
        int tx = x, tw = w;
        if (swatch != 0) {
            int s = Math.min(8, h - 6);
            g.fill(x + 5, y + (h - s) / 2, x + 5 + s, y + (h - s) / 2 + s, 0xFF000000 | swatch);
            tx = x + 5 + s;
            tw = w - 5 - s;
        }
        center(label, tx + tw / 2, y + (h - 8) / 2, tw - 6, fg);
        if (enabled && action != null) hit(x, y, w, h, action, null);
    }

    int chipW(String label) {
        return font.width(label) + 12;
    }

    /** 下拉选择框的样子：色块 + 当前值 + ▾。 */
    void select(int x, int y, int w, int h, String value, int swatch, boolean enabled, Runnable open) {
        boolean hov = enabled && over(x, y, w, h);
        round(g, x, y, w, h, hov ? ITEM_HOVER : SURFACE);
        if (hov) {
            g.fill(x + 2, y + h - 1, x + w - 2, y + h, ACCENT);
        }
        int tx = x + 6;
        if (swatch != 0) {
            int s = Math.min(8, h - 6);
            g.fill(tx, y + (h - s) / 2, tx + s, y + (h - s) / 2 + s, 0xFF000000 | swatch);
            tx += s + 5;
        }
        text(value, tx, y + (h - 8) / 2, x + w - 14 - tx, enabled ? TEXT : MUTED);
        text("▾", x + w - 11, y + (h - 8) / 2, enabled ? SUB : MUTED);
        if (enabled && open != null) hit(x, y, w, h, open, null);
    }

    /** 输入框的底（EditBox 不画边框，放在这上面）。 */
    void field(int x, int y, int w, int h, boolean focused) {
        round(g, x, y, w, h, SURFACE);
        g.fill(x + 2, y + h - 1, x + w - 2, y + h, focused ? ACCENT : LINE);
    }

    /** 弹出的选择面板：一格一个选项，点了就选上并关掉；点外面或按 Esc 关掉。 */
    static final class Picker {
        final String title;
        final List<String[]> opts;
        final java.util.function.IntSupplier get;
        final IntConsumer set;
        final java.util.function.IntUnaryOperator swatch;
        int scroll;

        Picker(String title, List<String[]> opts, java.util.function.IntSupplier get, IntConsumer set, java.util.function.IntUnaryOperator swatch) {
            this.title = title;
            this.opts = opts;
            this.get = get;
            this.set = set;
            this.swatch = swatch;
        }

        void render(Ui ui, int sw, int sh, Runnable close) {
            ui.hits.clear();
            ui.scrolls.clear();
            ui.g.fill(0, 0, sw, sh, 0x70000000);
            ui.hit(0, 0, sw, sh, close, close);
            boolean sws = swatch.applyAsInt(0) != 0;
            int cw = 40;
            for (String[] o : opts) cw = Math.max(cw, ui.chipW(o[1]) + (sws ? 13 : 0));
            int gap = 4, ch = 18;
            int maxW = sw - 24;
            int cols = Math.max(1, Math.min(opts.size() > 12 ? 4 : 3, (maxW - 16 + gap) / (cw + gap)));
            cw = Math.min(cw, (maxW - 16 - (cols - 1) * gap) / cols);
            int rows = (opts.size() + cols - 1) / cols;
            int w = cols * cw + (cols - 1) * gap + 16;
            int gridH = rows * (ch + gap) - gap;
            int h = Math.min(sh - 24, 24 + gridH + 10);
            int x = (sw - w) / 2, y = (sh - h) / 2;
            ui.card(x, y, w, h);
            ui.hit(x, y, w, h, () -> {
            }, () -> {
            });                                             // clicks on the card itself do not close it
            ui.text(title, x + 8, y + 8, w - 16, Ui.SUB);
            int viewY = y + 22, viewH = h - 30;
            int maxScroll = Math.max(0, gridH - viewH);
            scroll = Math.max(0, Math.min(scroll, maxScroll));
            ui.scroll(x, y, w, h, d -> scroll = Math.max(0, Math.min(maxScroll, scroll + d * (ch + gap))));
            ui.pushClip(x + 4, viewY, w - 8, viewH);
            int cur = get.getAsInt();
            for (int i = 0; i < opts.size(); i++) {
                int cx = x + 8 + (i % cols) * (cw + gap), cy = viewY - scroll + (i / cols) * (ch + gap);
                if (cy + ch < viewY || cy > viewY + viewH) continue;
                final int idx = i;
                ui.chip(cx, cy, cw, ch, opts.get(i)[1], i == cur, true, swatch.applyAsInt(i), () -> {
                    set.accept(idx);
                    close.run();
                });
            }
            ui.popClip();
            ui.scrollbar(x + w - 5, viewY, viewH, gridH, viewH, scroll);
        }
    }

    /** 竖滚动条。 */
    void scrollbar(int x, int y, int h, int total, int visible, int offset) {
        if (total <= visible || h <= 4) return;
        int bh = Math.max(10, h * visible / total);
        int by = y + (h - bh) * offset / Math.max(1, total - visible);
        g.fill(x, y, x + 2, y + h, 0x30FFFFFF);
        g.fill(x, by, x + 2, by + bh, 0xA0FFFFFF);
    }
}
