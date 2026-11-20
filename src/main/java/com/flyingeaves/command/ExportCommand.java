package com.flyingeaves.command;

import com.flyingeaves.FlyingEaves;
import com.flyingeaves.tool.CanvasStamp;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * {@code /flyingeaves export <名字>}：最近用建造工具放的那栋存成原版结构文件（flyingeaves/exports/<名字>.nbt，
 * 再拷一份进存档，结构方块里填 flyingeaves:<名字> 就能载入）。
 */
@Mod.EventBusSubscriber(modid = FlyingEaves.MODID)
public final class ExportCommand {
    private ExportCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("flyingeaves").requires(source -> source.hasPermission(2))
                .then(Commands.literal("export")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> {
                                    String name = StringArgumentType.getString(context, "name");
                                    try {
                                        Component r = CanvasStamp.exportLast(context.getSource().getServer(), name);
                                        if (r == null) {
                                            context.getSource().sendFailure(Component.translatable("flyingeaves.cmd.export_none"));
                                            return 0;
                                        }
                                        context.getSource().sendSuccess(() -> Component.translatable("flyingeaves.cmd.exported", r), false);
                                        return 1;
                                    } catch (Exception e) {
                                        context.getSource().sendFailure(Component.translatable("flyingeaves.cmd.export_failed", String.valueOf(e)));
                                        return 0;
                                    }
                                }))));
    }
}
