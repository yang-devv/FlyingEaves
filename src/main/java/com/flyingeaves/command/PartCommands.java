package com.flyingeaves.command;

import com.flyingeaves.FlyingEaves;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 把游戏里搭好的一小块存成部件模板（家具、窗、门、摆设……），生成器以后就用它：
 * {@code /flyingeaves part pos1}、{@code /flyingeaves part pos2} 用准星点两个对角；{@code /flyingeaves part save <名字> [中文名] [replace]} 存下来。
 * 存的时候人站在部件正面、面朝它，正面就算朝着人。写进 flyingeaves/parts/<名字>.json，同名的再存一次就多一个样子
 * （加 replace 则只留这一个）。名字和已有的家具同名（bed、desk、cabinet……）就是给它加样子；生成器自己发现文件变了。
 */
@Mod.EventBusSubscriber(modid = FlyingEaves.MODID)
public final class PartCommands {
    private static final Map<UUID, BlockPos[]> CORNERS = new HashMap<>();
    private static final Path DIR = FlyingEaves.dataDir().resolve("parts");

    private PartCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("flyingeaves").requires(source -> source.hasPermission(2))
                .then(Commands.literal("part")
                        .then(Commands.literal("pos1").executes(c -> corner(c, 0)))
                        .then(Commands.literal("pos2").executes(c -> corner(c, 1)))
                        .then(Commands.literal("saveornament")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .then(Commands.argument("point", StringArgumentType.word())
                                                .executes(PartCommands::saveOrnament))))
                        .then(Commands.literal("savebay")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .then(Commands.argument("role", StringArgumentType.word())
                                                .executes(c -> saveBay(c, "palace"))
                                                .then(Commands.argument("style", StringArgumentType.word())
                                                        .executes(c -> saveBay(c, StringArgumentType.getString(c, "style")))))))
                        .then(Commands.literal("save")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(c -> save(c, null, false))
                                        .then(Commands.argument("cn", StringArgumentType.string())
                                                .executes(c -> save(c, StringArgumentType.getString(c, "cn"), false))
                                                .then(Commands.literal("replace")
                                                        .executes(c -> save(c, StringArgumentType.getString(c, "cn"), true))))))));
    }

    /**
     * 存一个墙的开间（两根柱子之间的一段墙）：{@code /flyingeaves part savebay <名字> <用途> [墙样式]}，用途是 window、door、plain、shop。
     * 两个对角框住这段墙（不含两边的柱子，从地板上一格到梁下一格），人站在墙外面朝墙。框可以是一层厚（只有墙）或两层厚
     * （靠人这层是墙外：窗檐、窗台、花架……）。和这个墙样式（默认 palace，宫殿红墙）一样的方块记成墙心、墙基、梁、柱、窗、门这些
     * 材料格，换了墙样式会跟着换；别的方块原样记下（别的模组的也行）。照右半边墙的样子搭：从里（靠墙中间）往外，左半边自动镜像。
     * 写进 flyingeaves/parts/bays_player.json，生成器发现文件变了自己重读；同名替换。
     */
    private static int saveBay(CommandContext<CommandSourceStack> c, String style) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        BlockPos[] cs = CORNERS.get(p.getUUID());
        if (cs == null || cs[0] == null || cs[1] == null) {
            c.getSource().sendFailure(Component.translatable("flyingeaves.cmd.pos_first"));
            return 0;
        }
        String name = StringArgumentType.getString(c, "name"), role = StringArgumentType.getString(c, "role");
        if (!java.util.Set.of("window", "door", "plain", "shop").contains(role)) {
            c.getSource().sendFailure(Component.translatable("flyingeaves.cmd.bad_role"));
            return 0;
        }
        ServerLevel level = p.serverLevel();
        BlockPos lo = new BlockPos(Math.min(cs[0].getX(), cs[1].getX()), Math.min(cs[0].getY(), cs[1].getY()), Math.min(cs[0].getZ(), cs[1].getZ()));
        BlockPos hi = new BlockPos(Math.max(cs[0].getX(), cs[1].getX()), Math.max(cs[0].getY(), cs[1].getY()), Math.max(cs[0].getZ(), cs[1].getZ()));
        Direction look = p.getDirection();                       // toward the wall; the wall's outside faces the player
        Direction right = look.getClockWise();                   // columns left to right as seen from outside
        boolean alongX = right.getAxis() == Direction.Axis.X;
        int width = alongX ? hi.getX() - lo.getX() + 1 : hi.getZ() - lo.getZ() + 1;
        int depth = alongX ? hi.getZ() - lo.getZ() + 1 : hi.getX() - lo.getX() + 1;
        int height = hi.getY() - lo.getY() + 1;
        if (depth > 2 || width > 8 || height > 12) {
            c.getSource().sendFailure(Component.translatable("flyingeaves.cmd.bay_too_big"));
            return 0;
        }
        // the material slots of the style (kinds.json, written by the generator)
        Map<String, String> slotOf = new HashMap<>();
        String hood = null;
        try {
            JsonObject slots = JsonParser.parseString(Files.readString(FlyingEaves.dataDir().resolve("kinds.json"), StandardCharsets.UTF_8)).getAsJsonObject()
                    .getAsJsonObject("compose").getAsJsonObject("wall_slots").getAsJsonObject(style);
            String[][] codes = {{"fill", "F"}, {"base", "b"}, {"dado", "d"}, {"beam", "B"}, {"post", "P"}, {"pane", "W"}, {"door", "D"}, {"win", "S"}};
            for (String[] cd : codes) {
                if (slots.has(cd[0])) slotOf.putIfAbsent(blockId(slots.get(cd[0]).getAsString()), cd[1]);
            }
            if (slots.has("trim_trapdoor")) hood = blockId(slots.get("trim_trapdoor").getAsString());
        } catch (Exception e) {
            c.getSource().sendFailure(Component.translatable("flyingeaves.cmd.no_style", style, String.valueOf(e)));
            return 0;
        }
        // outward (toward the player) becomes south, as every part is drawn
        Rotation rot = switch (look.getOpposite()) {
            case EAST -> Rotation.CLOCKWISE_90;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            case NORTH -> Rotation.CLOCKWISE_180;
            default -> Rotation.NONE;
        };
        Map<String, String> letterOf = new java.util.LinkedHashMap<>();
        JsonObject key = new JsonObject();
        JsonArray rows = new JsonArray();
        // the far layer is the wall, the near one (when the frame is 2 thick) the outside; columns from the player's left
        int fx = look.getStepX(), fz = look.getStepZ();
        int sx = alongX ? (right.getStepX() > 0 ? lo.getX() : hi.getX()) : (fx > 0 ? hi.getX() : lo.getX());
        int sz = alongX ? (fz > 0 ? hi.getZ() : lo.getZ()) : (right.getStepZ() > 0 ? lo.getZ() : hi.getZ());
        BlockPos start = new BlockPos(sx, lo.getY(), sz);
        for (int k = 0; k < height; k++) {
            StringBuilder wall = new StringBuilder(), out = new StringBuilder();
            boolean anyOut = false;
            for (int j = 0; j < width; j++) {
                BlockPos far = start.relative(right, j).above(k);
                wall.append(code(level.getBlockState(far), slotOf, letterOf, key, rot, true, null));
                if (depth == 2) {
                    String oc = code(level.getBlockState(far.relative(look.getOpposite())), Map.of(), letterOf, key, rot, false, hood);
                    if (!oc.equals(".")) anyOut = true;
                    out.append(oc);
                }
            }
            rows.add(wall + (anyOut ? "|" + out : ""));
        }
        if (key.size() > POOL.length()) {
            c.getSource().sendFailure(Component.translatable("flyingeaves.cmd.too_many_kinds"));
            return 0;
        }
        try {
            Files.createDirectories(DIR);
            Path file = DIR.resolve("bays_player.json");
            JsonObject root = Files.exists(file) ? JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject() : new JsonObject();
            if (!root.has("_说明")) root.addProperty("_说明", "游戏里 /flyingeaves part savebay 存的墙开间（格式见 generator/data/parts/bays.json）。");
            JsonObject bays = root.has("bays") ? root.getAsJsonObject("bays") : new JsonObject();
            JsonObject e = new JsonObject();
            e.addProperty("cn", name);
            e.addProperty("role", role);
            e.addProperty("style", name);                    // shows up in the 墙样 tool of 单体建造
            JsonObject widths = new JsonObject();
            widths.add(String.valueOf(width), rows);
            e.add("widths", widths);
            if (key.size() > 0) e.add("key", key);
            e.addProperty("weight", 3);
            bays.add(name, e);
            root.add("bays", bays);
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root), StandardCharsets.UTF_8);
            int w = width;
            c.getSource().sendSuccess(() -> Component.translatable("flyingeaves.cmd.bay_saved", name, role, w, height, String.valueOf(file)), false);
            return 1;
        } catch (Exception ex) {
            c.getSource().sendFailure(Component.translatable("flyingeaves.cmd.save_failed", String.valueOf(ex)));
            return 0;
        }
    }

    /**
     * 存一个屋顶或檐下的装饰：{@code /flyingeaves part saveornament <名字> <位置>}，位置是 ridge_end（正脊两头的鸱吻）、ridge_mid（正脊正中）、
     * finial（攒尖顶尖的宝顶）、bracket（檐下柱头上的斗拱）。pos1 点装饰坐在上面（挂在上面）的那一格（正脊最后一格、屋脊正中、
     * 顶尖、檐下那一格的位置），pos2 点框住整个装饰的对角。人面朝外站：鸱吻面朝顺着正脊往外，斗拱背对墙。
     * pos1 那一格不存（斗拱除外：它就是第一格）。写进 flyingeaves/parts/ornaments_player.json，同名替换。
     */
    private static int saveOrnament(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        BlockPos[] cs = CORNERS.get(p.getUUID());
        if (cs == null || cs[0] == null || cs[1] == null) {
            c.getSource().sendFailure(Component.translatable("flyingeaves.cmd.pos_first_ornament"));
            return 0;
        }
        String name = StringArgumentType.getString(c, "name"), point = StringArgumentType.getString(c, "point");
        if (!java.util.Set.of("ridge_end", "ridge_mid", "finial", "bracket").contains(point)) {
            c.getSource().sendFailure(Component.translatable("flyingeaves.cmd.bad_point"));
            return 0;
        }
        ServerLevel level = p.serverLevel();
        BlockPos anchor = cs[0];
        BlockPos lo = new BlockPos(Math.min(cs[0].getX(), cs[1].getX()), Math.min(cs[0].getY(), cs[1].getY()), Math.min(cs[0].getZ(), cs[1].getZ()));
        BlockPos hi = new BlockPos(Math.max(cs[0].getX(), cs[1].getX()), Math.max(cs[0].getY(), cs[1].getY()), Math.max(cs[0].getZ(), cs[1].getZ()));
        if ((hi.getX() - lo.getX() + 1) * (hi.getY() - lo.getY() + 1) * (hi.getZ() - lo.getZ() + 1) > 1024) {
            c.getSource().sendFailure(Component.translatable("flyingeaves.cmd.too_big", 1024));
            return 0;
        }
        // the player faces out: that direction becomes +du (east) for ridge ends, +dv (south, out of the wall) for brackets
        Direction target = point.equals("bracket") ? Direction.SOUTH : Direction.EAST;
        Direction d = p.getDirection();
        int steps = 0;
        while (d != target) {
            d = d.getClockWise();
            steps++;
        }
        Rotation rot = switch (steps) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
        JsonArray cells = new JsonArray();
        for (BlockPos pos : BlockPos.betweenClosed(lo, hi)) {
            if (pos.equals(anchor) && !point.equals("bracket")) continue;
            BlockState st = level.getBlockState(pos);
            if (st.isAir()) continue;
            BlockPos rel = pos.subtract(anchor).rotate(rot);
            JsonArray cell = new JsonArray();
            cell.add(rel.getX());
            cell.add(rel.getY());
            cell.add(rel.getZ());
            cell.add(BlockStateParser.serialize(st.rotate(rot)));
            cells.add(cell);
        }
        if (cells.isEmpty()) {
            c.getSource().sendFailure(Component.translatable("flyingeaves.cmd.empty_box"));
            return 0;
        }
        try {
            Files.createDirectories(DIR);
            Path file = DIR.resolve("ornaments_player.json");
            JsonObject root = Files.exists(file) ? JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject() : new JsonObject();
            if (!root.has("_说明")) root.addProperty("_说明", "游戏里 /flyingeaves part saveornament 存的屋顶、檐下装饰（格式见 generator/data/parts/ornaments.json）。");
            JsonObject orns = root.has("ornaments") ? root.getAsJsonObject("ornaments") : new JsonObject();
            JsonObject e = new JsonObject();
            e.addProperty("cn", name);
            e.addProperty("point", point);
            e.addProperty("weight", 3);
            e.add("cells", cells);
            orns.add(name, e);
            root.add("ornaments", orns);
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root), StandardCharsets.UTF_8);
            int n = cells.size();
            c.getSource().sendSuccess(() -> Component.translatable("flyingeaves.cmd.ornament_saved", name, point, n, String.valueOf(file)), false);
            return 1;
        } catch (Exception ex) {
            c.getSource().sendFailure(Component.translatable("flyingeaves.cmd.save_failed", String.valueOf(ex)));
            return 0;
        }
    }

    private static final String POOL = "acegijklmnopqrtuwxyzCEGHIJKLMNOQRTUVXYZ0123456789";

    private static String blockId(String state) {
        int i = state.indexOf('[');
        return i < 0 ? state : state.substring(0, i);
    }

    /** 一格的字母：空气（墙里是 A 开口，墙外是 . 不放）、样式里的材料格、窗檐，别的方块给一个新字母记进 key。 */
    private static String code(BlockState s, Map<String, String> slotOf, Map<String, String> letterOf, JsonObject key,
                               Rotation rot, boolean wall, String hood) {
        if (s.isAir()) return wall ? "A" : ".";
        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
        if (slotOf.containsKey(id)) return slotOf.get(id);
        if (!wall && id.equals(hood)) return "h";
        String st = BlockStateParser.serialize(s.rotate(rot));
        String l = letterOf.get(st);
        if (l == null) {
            l = letterOf.size() < POOL.length() ? String.valueOf(POOL.charAt(letterOf.size())) : "?";
            letterOf.put(st, l);
            key.addProperty(l, st);
        }
        return l;
    }

    private static int corner(CommandContext<CommandSourceStack> c, int which) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        HitResult hit = p.pick(20, 0, false);
        if (!(hit instanceof BlockHitResult bh) || hit.getType() == HitResult.Type.MISS) {
            c.getSource().sendFailure(Component.translatable("flyingeaves.cmd.no_block"));
            return 0;
        }
        BlockPos pos = bh.getBlockPos().immutable();
        CORNERS.computeIfAbsent(p.getUUID(), k -> new BlockPos[2])[which] = pos;
        c.getSource().sendSuccess(() -> Component.translatable("flyingeaves.cmd.corner", which + 1, pos.toShortString()), false);
        return 1;
    }

    private static int save(CommandContext<CommandSourceStack> c, String cn, boolean replace) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        BlockPos[] cs = CORNERS.get(p.getUUID());
        if (cs == null || cs[0] == null || cs[1] == null) {
            c.getSource().sendFailure(Component.translatable("flyingeaves.cmd.pos_first"));
            return 0;
        }
        String name = StringArgumentType.getString(c, "name");
        ServerLevel level = p.serverLevel();
        BlockPos lo = new BlockPos(Math.min(cs[0].getX(), cs[1].getX()), Math.min(cs[0].getY(), cs[1].getY()), Math.min(cs[0].getZ(), cs[1].getZ()));
        BlockPos hi = new BlockPos(Math.max(cs[0].getX(), cs[1].getX()), Math.max(cs[0].getY(), cs[1].getY()), Math.max(cs[0].getZ(), cs[1].getZ()));
        int w = hi.getX() - lo.getX() + 1, d = hi.getZ() - lo.getZ() + 1;
        if (w * d * (hi.getY() - lo.getY() + 1) > 4096) {
            c.getSource().sendFailure(Component.translatable("flyingeaves.cmd.too_big", 4096));
            return 0;
        }
        // the piece faces the player; turn it so it faces south (+v into the room) like every piece
        Direction front = p.getDirection().getOpposite();
        Rotation rot = switch (front) {
            case EAST -> Rotation.CLOCKWISE_90;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            case NORTH -> Rotation.CLOCKWISE_180;
            default -> Rotation.NONE;
        };
        JsonArray variant = new JsonArray();
        int pw = (rot == Rotation.CLOCKWISE_90 || rot == Rotation.COUNTERCLOCKWISE_90) ? d : w;
        int pd = (rot == Rotation.CLOCKWISE_90 || rot == Rotation.COUNTERCLOCKWISE_90) ? w : d;
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(lo, hi)) {
            BlockState s = level.getBlockState(pos);
            if (s.isAir()) continue;
            int dx = pos.getX() - lo.getX(), dz = pos.getZ() - lo.getZ(), k = pos.getY() - lo.getY();
            int u, v;
            switch (rot) {
                case CLOCKWISE_90 -> {
                    u = d - 1 - dz;
                    v = dx;
                }
                case COUNTERCLOCKWISE_90 -> {
                    u = dz;
                    v = w - 1 - dx;
                }
                case CLOCKWISE_180 -> {
                    u = w - 1 - dx;
                    v = d - 1 - dz;
                }
                default -> {
                    u = dx;
                    v = dz;
                }
            }
            JsonArray cell = new JsonArray();
            cell.add(u);
            cell.add(k);
            cell.add(v);
            cell.add(BlockStateParser.serialize(s.rotate(rot)));
            variant.add(cell);
            count++;
        }
        if (count == 0) {
            c.getSource().sendFailure(Component.translatable("flyingeaves.cmd.empty_box"));
            return 0;
        }
        try {
            Files.createDirectories(DIR);
            Path file = DIR.resolve(name + ".json");
            JsonObject root = Files.exists(file) ? JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject() : new JsonObject();
            JsonObject partsObj = root.has("parts") ? root.getAsJsonObject("parts") : new JsonObject();
            JsonObject part = partsObj.has(name) && !replace ? partsObj.getAsJsonObject(name) : new JsonObject();
            JsonArray variants = part.has("variants") ? part.getAsJsonArray("variants") : new JsonArray();
            variants.add(variant);
            JsonArray size = new JsonArray();
            size.add(pw);
            size.add(pd);
            part.add("size", size);
            part.add("variants", variants);
            if (cn != null) part.addProperty("cn", cn);
            if (replace) part.addProperty("replace", true);
            partsObj.add(name, part);
            root.add("parts", partsObj);
            Gson gson = new GsonBuilder().disableHtmlEscaping().create();
            Files.writeString(file, gson.toJson(root), StandardCharsets.UTF_8);
            int nv = variants.size();
            c.getSource().sendSuccess(() -> Component.translatable("flyingeaves.cmd.part_saved", name, pw, pd, nv, String.valueOf(file)), false);
            return 1;
        } catch (Exception e) {
            c.getSource().sendFailure(Component.translatable("flyingeaves.cmd.save_failed", String.valueOf(e)));
            return 0;
        }
    }
}
