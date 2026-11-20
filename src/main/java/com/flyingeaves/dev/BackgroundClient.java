package com.flyingeaves.dev;

import com.flyingeaves.FlyingEaves;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

/**
 * Background dev client (-Dflyingeaves.background=true, set by gradlew runClient unless -Pbg=false): the window opens
 * without taking the focus (dev/mixin/BackgroundWindowMixin); here the game keeps running when it loses the focus (no
 * pause menu) and never holds the mouse while the window isn't active (vanilla only grabs it in an active window; this
 * is a second guard). Clicking into the window grabs the mouse again as usual. Never on in a normal (release) game.
 */
public final class BackgroundClient {
    public static final String PROPERTY = "flyingeaves.background";

    private BackgroundClient() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean(PROPERTY);
    }

    public static void init() {
        Minecraft.getInstance().options.pauseOnLostFocus = false;
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e) -> {
            if (e.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.mouseHandler.isMouseGrabbed() && !mc.isWindowActive()) mc.mouseHandler.releaseMouse();
        });
        FlyingEaves.LOGGER.info("background dev client, window hints applied: {}", Boolean.getBoolean(PROPERTY + ".applied"));
    }
}
