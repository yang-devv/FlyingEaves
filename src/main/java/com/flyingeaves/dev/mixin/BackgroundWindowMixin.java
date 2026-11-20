package com.flyingeaves.dev.mixin;

import net.minecraftforge.client.loading.NoVizFallback;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.system.Platform;
import org.lwjgl.system.windows.User32;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Background dev client (-Dflyingeaves.background=true; gradlew runClient turns it on by default, -Pbg=false turns it
 * off, see build.gradle): the game window opens without taking the focus and goes behind the other windows.
 *
 * Development only: loaded solely by build.gradle's {@code --mixin.config flyingeaves.dev.mixins.json}. The release jar
 * declares no mixins (no MixinConfigs in the manifest, no [[mixins]] in mods.toml) and leaves this class and the config out.
 * The hook is where Minecraft creates its window when Forge's early loading window is off (fml.toml
 * earlyWindowControl=false, which build.gradle writes for the background mode): Window's constructor sets the window hints,
 * calls NoVizFallback.windowHandoff, then glfwCreateWindow. Two more hints go in first - created unfocused (GLFW_FOCUSED,
 * GLFW shows it with SW_SHOWNA) and later shows don't take the focus either (GLFW_FOCUS_ON_SHOW) - and once created the
 * window goes to the bottom of the z-order without being activated. The targets are Forge's own class and GLFW: no
 * remapping, no refmap. (Ported from Daoverse.)
 */
@Mixin(value = NoVizFallback.class, remap = false)
public abstract class BackgroundWindowMixin {
    @Inject(method = "windowHandoff", at = @At("RETURN"), cancellable = true, remap = false)
    private static void flyingeaves$background(IntSupplier width, IntSupplier height, Supplier<String> title, LongSupplier monitor,
                                               CallbackInfoReturnable<LongSupplier> cir) {
        if (!Boolean.getBoolean("flyingeaves.background")) {
            return;
        }
        LongSupplier create = cir.getReturnValue();
        cir.setReturnValue(() -> {
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
            long window = create.getAsLong();
            if (window != 0L) {
                GLFW.glfwSetWindowAttrib(window, GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
                if (Platform.get() == Platform.WINDOWS) {
                    User32.SetWindowPos(GLFWNativeWin32.glfwGetWin32Window(window), User32.HWND_BOTTOM, 0, 0, 0, 0,
                            User32.SWP_NOMOVE | User32.SWP_NOSIZE | User32.SWP_NOACTIVATE);
                }
                System.setProperty("flyingeaves.background.applied", "true");
            }
            return window;
        });
    }
}
