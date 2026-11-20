package com.flyingeaves.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 材料板（照 TMA 的「在世界里摆方块定材料」）：单体建造里用「材料」工具右键一间房，在你面前的地上摆出两排格子，前排是墙的各部分，
 * 后排是屋顶的各部分。每格里有个缩小的方块，是这一格现在用的材料，头上写着是哪一部分。往格子里放一个方块（什么方块都行，别的模组的也行），
 * 这一部分就换成它，预览马上跟着变；打掉就换回原来的。再右键这间房存下来：写进 flyingeaves/materials/custom.json（新的墙样式、瓦样式，
 * 只记改了的格子，其余照原样式），这间房改用新样式，板上放的方块收掉。左键取消。
 * <p>瓦只放了整块、没放台阶和半砖时，按名字找同一种方块的台阶、半砖（deepslate_tiles → deepslate_tile_stairs）。
 */
final class PaletteBoard {
    /** 一格：材料里的名字、中文、要什么样的方块。 */
    record Slot(String section, String key, Kind kind) {
        /** The cell's name (flyingeaves.slot.&lt;section&gt;.&lt;key&gt;). */
        String cn() {
            return L.t("slot." + section + "." + key);
        }

    }

    enum Kind {
        ANY, STAIRS, SLAB, TRAPDOOR, DOOR, PANE;

        boolean ok(Block b) {
            return switch (this) {
                case ANY -> true;
                case STAIRS -> b instanceof StairBlock;
                case SLAB -> b instanceof SlabBlock;
                case TRAPDOOR -> b instanceof TrapDoorBlock;
                case DOOR -> b instanceof DoorBlock;
                case PANE -> b instanceof IronBarsBlock;
            };
        }

        String need() {
            return switch (this) {
                case STAIRS -> L.t("board.need_stairs");
                case SLAB -> L.t("board.need_slab");
                case TRAPDOOR -> L.t("board.need_trapdoor");
                case DOOR -> L.t("board.need_door");
                case PANE -> L.t("board.need_pane");
                default -> "";
            };
        }
    }

    static final List<Slot> WALL = List.of(
            new Slot("walls", "fill", Kind.ANY), new Slot("walls", "post", Kind.ANY),
            new Slot("walls", "beam", Kind.ANY), new Slot("walls", "base", Kind.ANY),
            new Slot("walls", "dado", Kind.ANY), new Slot("walls", "pane", Kind.PANE),
            new Slot("walls", "door", Kind.DOOR), new Slot("walls", "win", Kind.TRAPDOOR),
            new Slot("walls", "trim_stairs", Kind.STAIRS), new Slot("walls", "trim_slab", Kind.SLAB),
            new Slot("walls", "trim_trapdoor", Kind.TRAPDOOR), new Slot("walls", "base_stairs", Kind.STAIRS),
            new Slot("walls", "floor", Kind.ANY), new Slot("walls", "rail", Kind.ANY),
            new Slot("walls", "steps", Kind.STAIRS), new Slot("walls", "inner_stairs", Kind.STAIRS),
            new Slot("walls", "ladder", Kind.ANY));
    static final List<Slot> ROOF = List.of(
            new Slot("roofs", "full", Kind.ANY), new Slot("roofs", "stairs", Kind.STAIRS),
            new Slot("roofs", "slab", Kind.SLAB), new Slot("roofs", "stripe_stairs", Kind.STAIRS),
            new Slot("roofs", "stripe_slab", Kind.SLAB), new Slot("roofs", "ridge", Kind.ANY),
            new Slot("roofs", "cap", Kind.ANY));

    static Compose.Room room;
    private static BlockPos[] wallCells = new BlockPos[0], roofCells = new BlockPos[0];
    /** 这间房现在的墙、瓦样式每一格用的方块（kinds.json 的 wall_slots / roof_slots）。 */
    private static Map<String, String> baseWall = Map.of(), baseRoof = Map.of();
    private static final Map<String, BlockState> STATES = new HashMap<>();

    private PaletteBoard() {
    }

    static boolean active() {
        return room != null;
    }

    /** 在玩家面前的地上摆出材料板（格子找地面上的第一格空气）。 */
    static void start(Compose.Room r, Minecraft mc) {
        if (room != null) cancel();
        room = r;
        Direction fwd = mc.player.getDirection(), right = fwd.getClockWise();
        BlockPos base = mc.player.blockPosition();
        wallCells = row(mc, base.relative(fwd, 3), right, WALL.size());
        roofCells = row(mc, base.relative(fwd, 5), right, ROOF.size());
        loadBase();
        BuildTool.say(L.t("board.started"));
    }

    private static BlockPos[] row(Minecraft mc, BlockPos mid, Direction right, int n) {
        BlockPos[] out = new BlockPos[n];
        for (int i = 0; i < n; i++) {
            BlockPos col = mid.relative(right, i - n / 2);
            BlockPos cell = col;
            for (int dy = 2; dy >= -6; dy--) {           // the first air cell resting on something
                BlockPos p = col.above(dy);
                if (mc.level.getBlockState(p).isAir() && !mc.level.getBlockState(p.below()).isAir()) {
                    cell = p;
                    break;
                }
            }
            out[i] = cell;
        }
        return out;
    }

    private static void loadBase() {
        ComposeScreen.Options o = Compose.options();
        baseWall = Map.of();
        baseRoof = Map.of();
        try {
            JsonObject c = JsonParser.parseString(Files.readString(BuildTool.RUN.resolve("kinds.json"), StandardCharsets.UTF_8)).getAsJsonObject()
                    .getAsJsonObject("compose");
            String w = o.walls.get(Math.min(room.walls, o.walls.size() - 1))[0], t = o.themes.get(Math.min(room.theme, o.themes.size() - 1))[0];
            baseWall = strings(c.getAsJsonObject("wall_slots").getAsJsonObject(w));
            baseRoof = strings(c.getAsJsonObject("roof_slots").getAsJsonObject(t));
        } catch (Exception ignored) {
        }
    }

    private static Map<String, String> strings(JsonObject o) {
        Map<String, String> m = new HashMap<>();
        if (o != null) o.entrySet().forEach(e -> m.put(e.getKey(), e.getValue().getAsString()));
        return m;
    }

    /** 格子里放的方块（空气返回 null）。 */
    private static Block placed(Minecraft mc, BlockPos p) {
        BlockState s = mc.level.getBlockState(p);
        return s.isAir() ? null : s.getBlock();
    }

    private static String id(Block b) {
        return BuiltInRegistries.BLOCK.getKey(b).toString();
    }

    /** 板上放了的、合要求的格子：{材料名: 方块 id}；瓦只放了整块时按名字补上台阶、半砖。 */
    static Map<String, String> overrides(Minecraft mc, List<Slot> slots, BlockPos[] cells) {
        Map<String, String> m = new TreeMap<>();
        for (int i = 0; i < slots.size() && i < cells.length; i++) {
            Block b = placed(mc, cells[i]);
            if (b != null && slots.get(i).kind().ok(b)) m.put(slots.get(i).key(), id(b));
        }
        if (slots == ROOF && m.containsKey("full")) {
            String full = m.get("full");
            for (String[] p : new String[][]{{"stairs", "_stairs"}, {"slab", "_slab"}}) {
                if (m.containsKey(p[0])) continue;
                String d = derive(full, p[1]);
                if (d != null) m.put(p[0], d);
            }
        }
        if (slots == ROOF) {                          // new tiles without their own tile-row stripe: no stripe in the old tiles
            if (m.containsKey("stairs") && !m.containsKey("stripe_stairs")) m.put("stripe_stairs", m.get("stairs"));
            if (m.containsKey("slab") && !m.containsKey("stripe_slab")) m.put("stripe_slab", m.get("slab"));
        }
        return m;
    }

    /** 同一种方块的台阶或半砖：deepslate_tiles → deepslate_tile_stairs，oak_planks → oak_stairs，stone → stone_stairs。 */
    static String derive(String full, String suffix) {
        String ns = full.contains(":") ? full.substring(0, full.indexOf(':') + 1) : "minecraft:";
        String path = full.substring(ns.length());
        String[] tries = {path + suffix, path.endsWith("s") ? path.substring(0, path.length() - 1) + suffix : null,
                path.endsWith("_planks") ? path.substring(0, path.length() - 7) + suffix : null,
                path.endsWith("_block") ? path.substring(0, path.length() - 6) + suffix : null};
        for (String t : tries) {
            if (t == null) continue;
            ResourceLocation rl = ResourceLocation.tryParse(ns + t);
            if (rl != null && BuiltInRegistries.BLOCK.containsKey(rl)) return rl.toString();
        }
        return null;
    }

    /** 每个 tick：把板上放的方块交给这间房，预览就按它们画。 */
    static void tick(Minecraft mc) {
        if (room == null || mc.level == null) return;
        if (!Compose.ROOMS.contains(room)) {
            cancel();
            return;
        }
        Map<String, String> w = overrides(mc, WALL, wallCells), r = overrides(mc, ROOF, roofCells);
        if (!w.equals(room.wallOverride)) {
            room.wallOverride.clear();
            room.wallOverride.putAll(w);
        }
        if (!r.equals(room.roofOverride)) {
            room.roofOverride.clear();
            room.roofOverride.putAll(r);
        }
    }

    /** 存下来：新样式写进 flyingeaves/materials/custom.json，这间房改用它（生成器重读材料后），板上的方块收掉。 */
    static void save(Minecraft mc) {
        if (room == null) return;
        Map<String, String> w = overrides(mc, WALL, wallCells), r = overrides(mc, ROOF, roofCells);
        if (w.isEmpty() && r.isEmpty()) {
            BuildTool.say(L.t("board.empty"));
            cancel();
            return;
        }
        ComposeScreen.Options o = Compose.options();
        try {
            Path dir = BuildTool.RUN.resolve("materials");
            Files.createDirectories(dir);
            Path file = dir.resolve("custom.json");
            JsonObject root = Files.exists(file) ? JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject() : new JsonObject();
            if (!root.has("_说明")) root.addProperty("_说明", "单体建造的材料板存下的样式（inherit：从哪个样式改来的，只记改了的格子）。可以手改、删。");
            String wallId = null, roofId = null;
            if (!w.isEmpty()) {
                wallId = add(root, "walls", "custom_w", "自定义墙 ", o.walls.get(Math.min(room.walls, o.walls.size() - 1))[0], w);
            }
            if (!r.isEmpty()) {
                roofId = add(root, "roofs", "custom_r", "自定义瓦 ", o.themes.get(Math.min(room.theme, o.themes.size() - 1))[0], r);
            }
            Path tmp = dir.resolve("custom.json.tmp");
            Files.writeString(tmp, new com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root), StandardCharsets.UTF_8);
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Compose.assignWhenListed(room, wallId, roofId);
            BuildTool.say(L.t("board.saved", (wallId != null ? L.t("board.saved_wall", wallId) : "") + (roofId != null ? L.t("board.saved_roof", roofId) : "")));
        } catch (Exception e) {
            BuildTool.say(L.t("board.save_failed", e));
            return;
        }
        clearBoard(mc);
        room = null;
    }

    private static String add(JsonObject root, String section, String prefix, String cnPrefix, String inherit, Map<String, String> m) {
        JsonObject sec = root.has(section) ? root.getAsJsonObject(section) : new JsonObject();
        root.add(section, sec);
        int n = 1;
        while (sec.has(prefix + n)) n++;
        JsonObject e = new JsonObject();
        e.addProperty("cn", cnPrefix + n);
        e.addProperty("inherit", inherit);
        m.forEach((k, v) -> {
            JsonArray a = new JsonArray();
            a.add(v);
            e.add(k.equals("win") ? "shutter" : k, a);       // the file calls the shop shutter "shutter"
        });
        sec.add(prefix + n, e);
        return prefix + n;
    }

    /** 取消：板上放的方块收掉，这间房回到原来的材料。 */
    static void cancel() {
        if (room == null) return;
        room.wallOverride.clear();
        room.roofOverride.clear();
        clearBoard(Minecraft.getInstance());
        room = null;
    }

    private static void clearBoard(Minecraft mc) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || mc.level == null) return;
        var dim = mc.level.dimension();
        BlockPos[] all = new BlockPos[wallCells.length + roofCells.length];
        System.arraycopy(wallCells, 0, all, 0, wallCells.length);
        System.arraycopy(roofCells, 0, all, wallCells.length, roofCells.length);
        server.execute(() -> {
            ServerLevel level = server.getLevel(dim);
            if (level == null) return;
            for (BlockPos p : all) {
                if (!level.getBlockState(p).isAir()) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                BlockPos up = p.above();                     // the upper half of a door
                if (level.getBlockState(up).getBlock() instanceof DoorBlock) level.setBlock(up, Blocks.AIR.defaultBlockState(), 3);
            }
        });
        wallCells = new BlockPos[0];
        roofCells = new BlockPos[0];
    }

    /** 测试用：第 i 格的位置。 */
    static BlockPos cell(boolean roof, int i) {
        BlockPos[] cs = roof ? roofCells : wallCells;
        return i >= 0 && i < cs.length ? cs[i] : null;
    }

    private static boolean isCell(BlockPos p) {
        for (BlockPos c : wallCells) if (c.equals(p)) return true;
        for (BlockPos c : roofCells) if (c.equals(p)) return true;
        return false;
    }

    /** 准星对着板上的格子（要往里放或打掉）：这时点击照常交给游戏。 */
    static boolean aimsAtBoard(Minecraft mc) {
        if (room == null || !(mc.hitResult instanceof BlockHitResult bh) || bh.getType() != HitResult.Type.BLOCK) return false;
        BlockPos p = bh.getBlockPos();
        if (isCell(p)) return true;
        if (isCell(p.relative(bh.getDirection()))) return true;
        return mc.level.getBlockState(p).getBlock() instanceof DoorBlock && isCell(p.below());
    }

    /** 屏幕上方的说明：准星对着哪一格。 */
    static String hudLine(Minecraft mc) {
        if (room == null) return null;
        if (mc.hitResult instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK) {
            for (int pass = 0; pass < 2; pass++) {
                BlockPos p = pass == 0 ? bh.getBlockPos() : bh.getBlockPos().relative(bh.getDirection());
                String s = describe(mc, p, WALL, wallCells, baseWall);
                if (s == null) s = describe(mc, p, ROOF, roofCells, baseRoof);
                if (s != null) return s;
            }
        }
        return L.t("board.hud");
    }

    private static String describe(Minecraft mc, BlockPos p, List<Slot> slots, BlockPos[] cells, Map<String, String> base) {
        for (int i = 0; i < cells.length; i++) {
            if (!cells[i].equals(p)) continue;
            Slot s = slots.get(i);
            Block b = placed(mc, p);
            String now = base.getOrDefault(s.key(), "?").replace("minecraft:", "");
            if (b == null) return L.t("board.cell_now", s.cn(), now) + (s.kind().need().isEmpty() ? "" : L.t("say.paren", s.kind().need()));
            if (!s.kind().ok(b)) return L.t("board.cell_wrong", s.cn(), s.kind().need(), id(b).replace("minecraft:", ""));
            return L.t("board.cell_change", s.cn(), now, id(b).replace("minecraft:", ""));
        }
        return null;
    }

    private static BlockState state(String s) {
        return STATES.computeIfAbsent(s, k -> {
            try {
                return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), k, false).blockState();
            } catch (Exception e) {
                return Blocks.AIR.defaultBlockState();
            }
        });
    }

    /** 画：每格一个框（白：没放；绿：放了；红：放的不合要求），空格里一个缩小的现在用的方块，头上写是哪一部分。 */
    static void render(Minecraft mc, PoseStack ps, MultiBufferSource.BufferSource buf, Camera camera) {
        if (room == null) return;
        draw(mc, ps, buf, camera, WALL, wallCells, baseWall, room.wallOverride);
        draw(mc, ps, buf, camera, ROOF, roofCells, baseRoof, room.roofOverride);
        buf.endBatch();
    }

    private static void draw(Minecraft mc, PoseStack ps, MultiBufferSource.BufferSource buf, Camera camera, List<Slot> slots, BlockPos[] cells,
                             Map<String, String> base, Map<String, String> over) {
        Font font = mc.font;
        for (int i = 0; i < cells.length; i++) {
            BlockPos c = cells[i];
            Slot s = slots.get(i);
            Block b = placed(mc, c);
            float r = 1, g = 1, bl = 1;
            if (b != null) {
                boolean ok = s.kind().ok(b);
                r = ok ? 0.3f : 1f;
                g = ok ? 1f : 0.3f;
                bl = 0.3f;
            }
            VertexConsumer lines = buf.getBuffer(RenderType.lines());
            LevelRenderer.renderLineBox(ps, lines, new AABB(c).inflate(0.02), r, g, bl, 1f);
            if (b == null) {                                 // what this part is made of now (or what the board derived)
                String cur = over.getOrDefault(s.key(), base.get(s.key()));
                if (cur != null) {
                    BlockState st = state(cur);
                    if (!st.isAir()) {
                        ps.pushPose();
                        ps.translate(c.getX() + 0.25, c.getY() + 0.25, c.getZ() + 0.25);
                        ps.scale(0.5f, 0.5f, 0.5f);
                        mc.getBlockRenderer().renderSingleBlock(st, ps, buf, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                        ps.popPose();
                    }
                }
            }
            ps.pushPose();
            ps.translate(c.getX() + 0.5, c.getY() + 1.25 + (i % 2) * 0.3, c.getZ() + 0.5);
            ps.mulPose(camera.rotation());
            ps.scale(-0.02f, -0.02f, 0.02f);
            String label = s.cn();
            font.drawInBatch(label, -font.width(label) / 2f, 0, 0xFFFFFFFF, false, ps.last().pose(), buf, Font.DisplayMode.SEE_THROUGH,
                    0x60000000, LightTexture.FULL_BRIGHT);
            ps.popPose();
        }
    }
}
