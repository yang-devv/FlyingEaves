package com.flyingeaves.tool;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;

/** 只有一个选择面板的界面：在世界里用工具点房间时弹出来（选用途、墙、瓦、屋顶），选了就关。 */
public class PickScreen extends Screen {
    private final Ui ui = new Ui();
    private final Ui.Picker picker;
    private boolean done;

    PickScreen(String title, List<String[]> opts, IntSupplier get, IntConsumer set, IntUnaryOperator swatch) {
        super(Component.literal(title));
        picker = new Ui.Picker(title, opts, get, v -> {
            set.accept(v);
            done = true;
        }, swatch);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        ui.begin(g, font, mouseX, mouseY);
        picker.render(ui, width, height, () -> done = true);
        if (done) onClose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        ui.click(mouseX, mouseY, button);
        if (done) onClose();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return ui.wheel(mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
