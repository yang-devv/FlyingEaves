package com.flyingeaves;

import com.flyingeaves.backend.GeneratorProcess;
import com.flyingeaves.tool.DevPipe;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.nio.file.Path;

/**
 * 营造 / Flying Eaves：在世界里画房间，游戏内的生成器（纯 Java）把它搭成中式古建。
 * 只能单人（或开局域网的那台主机）用：放方块、取地形都直接走单人游戏的内置服务器。
 */
@Mod(FlyingEaves.MODID)
public final class FlyingEaves {
    public static final String MODID = "flyingeaves";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FlyingEaves(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        if (FMLEnvironment.dist.isClient()) {
            context.getModEventBus().addListener((FMLClientSetupEvent e) -> GeneratorProcess.startAtLaunch());
            MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.GameShuttingDownEvent e) -> GeneratorProcess.stop());
            if (Boolean.getBoolean("flyingeaves.dev")) DevPipe.init();
        }
    }

    /** 模组的工作目录 &lt;游戏目录&gt;/flyingeaves：和生成器之间的请求、结果，玩家存的设计、材料、部件、导出的结构。和存档无关。 */
    public static Path dataDir() {
        return FMLPaths.GAMEDIR.get().resolve(MODID);
    }
}
