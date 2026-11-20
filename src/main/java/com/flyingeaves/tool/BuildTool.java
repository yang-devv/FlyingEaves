package com.flyingeaves.tool;

import com.flyingeaves.FlyingEaves;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * 按 O 打开建造菜单，选一种建筑（凡人小屋到化神主殿、宗门特殊地点）和境界，对着地面放下去；
 * 「换一个」撤掉上一栋、换个种子在原地再生成；「撤销」恢复原来的方块。按 H 是「单体建造」：在地上画房间、定样子、生成（{@link Compose}）。
 *
 * <p>生成器在游戏里跑（Java 版，{@link com.flyingeaves.backend.JavaBackend}，一个后台线程）：建造菜单的种类、单体建造、
 * 排院子/城镇/宗门/一句话都是它，发行版不需要 Python。开发版（-PwithPython=true）才能退回 Python 版（generator/bgen_daemon.py，
 * 模组自己启动、关掉：{@link com.flyingeaves.backend.GeneratorProcess}；请求写进 &lt;游戏目录&gt;/flyingeaves/requests/，结果放进
 * flyingeaves/out/）。都经 {@link GenRequest}，结果再用 {@link CanvasStamp} 写进世界。
 */
public final class BuildTool {
    static final Path RUN = FlyingEaves.dataDir();
    static final KeyMapping OPEN = new KeyMapping("key.flyingeaves.build_tool", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, "key.categories.flyingeaves");
    static final KeyMapping COMPOSE = new KeyMapping("key.flyingeaves.compose", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.flyingeaves");
    /** 随机一栋（默认 K；原版的 P 是「社交互动」）：准星处随机一栋、出预览；再按换一栋。放下去照常在房间表里点「生成」。 */
    static final KeyMapping RANDOM = new KeyMapping("key.flyingeaves.compose_random", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.flyingeaves");

    /** 最近一次放置（换一个时原地重来）。 */
    static String lastKind, lastTier;
    static int lastSeed;
    static BlockPos lastPos;
    static String lastFacing;
    private static GenRequest pending;
    private static int counter;

    private BuildTool() {
    }

    @Mod.EventBusSubscriber(modid = FlyingEaves.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Keys {
        @SubscribeEvent
        public static void register(RegisterKeyMappingsEvent event) {
            event.register(OPEN);
            event.register(COMPOSE);
            event.register(RANDOM);
        }
    }

    @Mod.EventBusSubscriber(modid = FlyingEaves.MODID, value = Dist.CLIENT)
    public static final class Ticks {
        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            L.sync();                                        // (the language changed: relabel the option tables)
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            if (!registryWritten) writeRegistry();
            while (OPEN.consumeClick()) {
                if (mc.screen == null) mc.setScreen(new BuildToolScreen());
            }
            while (COMPOSE.consumeClick()) {                 // 单体建造：没在搭就开始搭一栋（直接回到世界画第一间），在搭就打开房间表
                if (mc.screen == null) {
                    if (!Compose.drawing) Compose.start();
                    else mc.setScreen(new ComposeScreen());
                }
            }
            while (RANDOM.consumeClick()) {                   // 随机一栋：和房间表里「随机一栋」一样，准星处、正面朝你；再按就换一栋
                if (mc.screen == null) {
                    String err = Compose.randomBuilding();
                    if (err != null) say(err);
                }
            }
            pollResult(mc);
            pollPreview();
            pollSentence();
        }
    }

    private static boolean registryWritten;

    /** 把游戏里所有方块的 id 写给生成器（flyingeaves/registry_blocks.txt）：材料数据里写了别的模组的方块时，它据此挑有的那个。 */
    private static void writeRegistry() {
        registryWritten = true;
        try {
            Files.createDirectories(RUN);
            StringBuilder sb = new StringBuilder();
            for (var id : net.minecraft.core.registries.BuiltInRegistries.BLOCK.keySet()) sb.append(id).append(System.lineSeparator());
            Path tmp = RUN.resolve("registry_blocks.txt.tmp");
            Files.writeString(tmp, sb.toString(), StandardCharsets.UTF_8);
            Files.move(tmp, RUN.resolve("registry_blocks.txt"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            FlyingEaves.LOGGER.warn("could not write the block list for the generator", e);
        }
    }

    static void say(String text) {
        if (Boolean.getBoolean("flyingeaves.dev")) DevPipe.log("say: " + text);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(Component.literal(text), true);
    }

    /** 准星指向的方块（最远 300 格），建筑的地面就是它的上面一格的地板。 */
    static BlockPos target(Minecraft mc) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 end = eye.add(mc.player.getViewVector(1f).scale(300));
        BlockHitResult hit = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, mc.player));
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        return hit.getBlockPos().immutable();
    }

    /** 建筑正面朝向玩家。 */
    static String facingToPlayer(Minecraft mc) {
        float yaw = Mth.wrapDegrees(mc.player.getYRot());
        // the player looks along yaw; the building should face back toward the player
        if (yaw >= -45 && yaw < 45) return "north";       // player looks south (+z), front faces north (-z)
        if (yaw >= 45 && yaw < 135) return "east";        // looks west
        if (yaw >= -135 && yaw < -45) return "west";      // looks east
        return "south";                                   // looks north
    }

    static void place(String kind, String tier, int seed, BlockPos ground, String facing) {
        if (pending != null) {
            say(L.t("build.busy"));
            return;
        }
        try {
            String id = String.format(Locale.ROOT, "%d_%d", System.currentTimeMillis(), counter++);
            JsonObject req = new JsonObject();
            req.addProperty("id", id);
            req.addProperty("kind", kind);
            req.addProperty("tier", tier);
            req.addProperty("seed", seed);
            req.addProperty("x", ground.getX());
            req.addProperty("y", ground.getY());
            req.addProperty("z", ground.getZ());
            req.addProperty("facing", facing);
            pending = GenRequest.send(req);
            lastKind = kind;
            lastTier = tier;
            lastSeed = seed;
            lastPos = ground;
            lastFacing = facing;
            say(L.t("build.generating", kind, tier, seed));
        } catch (Exception e) {
            say(L.t("build.request_failed", e));
        }
    }

    /** 单体建造：把画好的房间交给生成器。种子不同，墙、窗、屋顶细节和房间布置就不同。 */
    static void placeCompose(JsonObject req, int seed) {
        if (pending != null) {
            say(L.t("build.busy"));
            return;
        }
        try {
            String id = String.format(Locale.ROOT, "%d_%d", System.currentTimeMillis(), counter++);
            req.addProperty("id", id);
            req.addProperty("type", "compose");
            req.addProperty("seed", seed);
            pending = GenRequest.send(req);
            lastKind = null;
            lastCompose = req;
            lastSeed = seed;
            say(L.t("build.generating_compose", req.getAsJsonArray("rooms").size(), seed));
        } catch (Exception e) {
            say(L.t("build.request_failed", e));
        }
    }

    static JsonObject lastCompose;

    // ------------------------------------------------------------------ 单体建造的预览：同一套生成器，结果只画不写

    private static GenRequest preview;

    /** 预览在生成（生成器两分钟没动静就不算了）。 */
    static boolean previewPending() {
        return preview != null && preview.prog.silentMs(System.currentTimeMillis()) < 120_000;
    }

    /** 单体建造状态栏上的预览进度。 */
    static String previewLabel() {
        int p = preview == null ? -1 : preview.prog.pct();
        return p > 0 ? L.t("build.preview_pct", p) : L.t("build.preview_wait");
    }

    // ------------------------------------------------------------------ 一句话：句子交给生成器认，认出来的设计放在准星处

    private static GenRequest sayReq;

    /** 随机一院 / 宫城：生成器用一个随机种子现排一个（不是从几份文件里挑，所以不重样）。 */
    static void requestSite(boolean city) {
        requestSite(city ? "city" : "court");
    }

    /** 随机城镇：取地形时准星在哪、朝哪（设计照着那里的地面排），载入时就放回那里。 */
    static BlockPos townAt;
    static int townTurn;
    /** 城镇取地形的半径：设计坐标里 (2R+1)² 列。 */
    static final int TOWN_R = 160;                     // (a big town on its street grid, its wall and gates: 300 across)

    /** 随机宗门取地形的半径（按境界：练气 筑基 金丹 元婴 化神）：宗门比城镇大得多，化神的几座峰要四百格开外。 */
    static int sectR(String tier) {
        return switch (tier) {
            case "zhuji" -> 180;
            case "jindan" -> 240;
            case "yuanying" -> 300;
            case "huashen" -> 400;
            default -> 150;
        };
    }

    /** 随机宗门：境界（练气到化神；凡人当练气）。 */
    static void requestSect(String tier) {
        if (tier == null || !java.util.Set.of("lianqi", "zhuji", "jindan", "yuanying", "huashen").contains(tier)) tier = "lianqi";
        sectTier = tier;
        requestSite("sect");
    }

    private static String sectTier = "lianqi";

    /** 随机城镇的规模（""：随机，village 村，town 镇，big 大镇）和自定的宽 × 深（格；0：按规模自己定）。 */
    private static String townSize = "";
    private static int townW, townD;

    /** 随机城镇：规模，和要它铺满的宽 × 深（都大于 0；0 就是不定，城镇按规模自己排）。取地形的半径跟着宽深走。 */
    static void requestTown(String size, int w, int d) {
        townSize = size == null ? "" : size;
        townW = Math.max(0, w);
        townD = Math.max(0, d);
        requestSite("town");
    }

    /** 城镇取地形的半径：不定宽深就是 TOWN_R，定了就盖住整块（加上城墙和城门外的一截）。 */
    static int townR() {
        if (townW <= 0 || townD <= 0) return TOWN_R;
        return Math.max(TOWN_R, Math.max(townW, townD) / 2 + 24);
    }

    /** which：court（随机一院）、city（宫城）、city_real（紫禁城 1:1：不取地形，午门放在准星处、整片按准星的地面找平）、
     *  town（随机城镇：连同准星周围的地面高度一起交给生成器，照着地形排）、sect（随机宗门：同样带地形，按境界）。 */
    static void requestSite(String which) {
        JsonObject r = new JsonObject();
        r.addProperty("type", "site");
        r.addProperty("which", which);
        r.addProperty("seed", new java.security.SecureRandom().nextInt(Integer.MAX_VALUE));
        if (which.equals("sect")) r.addProperty("tier", sectTier);
        if (which.equals("town")) {
            if (!townSize.isEmpty()) r.addProperty("size", townSize);
            if (townW > 0 && townD > 0) {
                JsonArray box = new JsonArray();
                box.add(townW);
                box.add(townD);
                r.add("box", box);
            }
        }
        if (!which.equals("town") && !which.equals("sect")) {
            sendSite(r, which);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        BlockPos at = target(mc);
        if (at == null) at = mc.player.blockPosition().below();
        int k = Compose.turnOf(facingToPlayer(mc));
        townAt = at;
        townTurn = k;
        int R = which.equals("sect") ? sectR(sectTier) : townR();
        if (mc.getSingleplayerServer() != null) say(L.t("build.sampling", 2 * R + 1, 2 * R + 1));
        Compose.sampleHeights(at, k, R, h -> {          // (single player: once the server has every chunk of it)
            r.add("heights", h);
            sendSite(r, which);
        });
    }

    private static void sendSite(JsonObject r, String which) {
        try {
            String id = String.format(Locale.ROOT, "say_%d_%d", System.currentTimeMillis(), counter++);
            r.addProperty("id", id);
            sayReq = GenRequest.send(r);
            say(switch (which) {
                case "city" -> L.t("build.site_city");
                case "city_real" -> L.t("build.site_city_real");
                case "town" -> townW > 0 && townD > 0 ? L.t("build.site_town_sized", townW, townD) : L.t("build.site_town");
                case "sect" -> L.t("build.site_sect");
                default -> L.t("build.site_court");
            });
        } catch (Exception e) {
            say(L.t("build.request_failed", e));
        }
    }

    static void requestSentence(String text) {
        try {
            String id = String.format(Locale.ROOT, "say_%d_%d", System.currentTimeMillis(), counter++);
            JsonObject r = new JsonObject();
            r.addProperty("id", id);
            r.addProperty("type", "sentence");
            r.addProperty("text", text);
            sayReq = GenRequest.send(r);
            say(L.t("build.sentence", text));
        } catch (Exception e) {
            say(L.t("build.request_failed", e));
        }
    }

    private static void pollSentence() {
        if (sayReq == null) return;
        GenRequest q = sayReq;
        JsonObject res;
        try {
            res = q.result();
        } catch (Exception e) {
            return;                                                  // (being written: next tick)
        }
        if (res == null) {
            long now = System.currentTimeMillis();
            if (q.prog.poll(now)) say(q.prog.text());
            if (q.prog.silentMs(now) > 60_000) {
                sayReq = null;
                say(L.t("build.silent_minute", com.flyingeaves.backend.GeneratorProcess.hint()));
            } else if (q.prog.warnNow(now)) {
                say(q.prog.warning(now));
            }
            return;
        }
        sayReq = null;
        try {
            if (!res.get("ok").getAsBoolean()) {
                say(L.t("build.sentence_failed", L.gen(res.get("error").getAsString())));
                return;
            }
            StringBuilder said = new StringBuilder();
            for (var e : res.getAsJsonArray("said")) said.append(said.length() > 0 ? L.t("build.list_sep") : "").append(e.getAsString());
            String err = Compose.loadSite(res.getAsJsonObject("design"));
            boolean site = res.has("site") && res.get("site").getAsBoolean();
            if (err != null) say(err);
            else if (!site) say(L.t("build.recognized", said.length() > 0 ? said.toString() : L.t("build.recognized_none")));
        } catch (Exception e) {
            FlyingEaves.LOGGER.warn("sentence load failed", e);
        } finally {
            q.delete(false);
        }
    }

    static void requestPreview(JsonObject req) {
        try {
            String id = String.format(Locale.ROOT, "pv_%d_%d", System.currentTimeMillis(), counter++);
            JsonObject r = req.deepCopy();
            r.addProperty("id", id);
            r.addProperty("type", "compose");
            preview = GenRequest.send(r);
        } catch (Exception e) {
            FlyingEaves.LOGGER.warn("preview request failed", e);
        }
    }

    private static void pollPreview() {
        if (preview == null) return;
        GenRequest q = preview;
        JsonObject res;
        try {
            res = q.result();
        } catch (Exception e) {
            return;                                                  // (being written: next tick)
        }
        if (res == null) {
            if (q.dropped()) preview = null;
            else q.prog.poll(System.currentTimeMillis());
            return;
        }
        preview = null;
        try {
            if (res.has("sh")) Compose.autoSh = res.get("sh").getAsInt();   // storey height of rooms not set by hand
            if (res.get("ok").getAsBoolean() && Compose.drawing) Preview.load(q.runs());
            else if (!res.get("ok").getAsBoolean()) say(L.t("build.preview_failed", L.gen(res.get("error").getAsString())));
        } catch (Exception e) {
            FlyingEaves.LOGGER.warn("preview load failed", e);
        } finally {
            q.delete(true);
        }
    }

    static void undo() {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> {
            boolean any = CanvasStamp.undoLast(n -> Minecraft.getInstance().execute(() -> say(L.t("build.undone", n))));
            int ahead = CanvasStamp.queued() - 1;
            Minecraft.getInstance().execute(() -> say(!any ? L.t("build.undo_none") : ahead > 0 ? L.t("build.undo_queued", ahead) : L.t("build.undoing")));
        });
    }

    /** 换一个：撤掉上一栋，种子加一，原地再生成。 */
    static void reroll() {
        if (lastKind == null && lastCompose == null) {
            say(L.t("build.nothing_placed"));
            return;
        }
        undo();
        if (lastKind != null) {
            place(lastKind, lastTier, lastSeed + 1, lastPos, lastFacing);
        } else {
            JsonObject again = lastCompose.deepCopy();
            again.remove("id");
            placeCompose(again, lastSeed + 1);
        }
    }

    private static void pollResult(Minecraft mc) {
        if (pending == null) return;
        GenRequest q = pending;
        JsonObject res;
        try {
            res = q.result();
        } catch (Exception e) {
            return;                                                  // (being written: next tick)
        }
        if (res == null) {
            long now = System.currentTimeMillis();
            if (q.prog.poll(now)) say(q.prog.text());
            if (q.prog.warnNow(now)) say(q.prog.warning(now));   // only when the daemon stopped writing progress
            return;
        }
        pending = null;
        q.prog.delete();
        try {
            if (!res.get("ok").getAsBoolean()) {
                say(L.t("build.failed", L.gen(res.get("error").getAsString())));
                return;
            }
            MinecraftServer server = mc.getSingleplayerServer();
            if (server == null) return;
            var dim = mc.level.dimension();
            server.execute(() -> {
                try {
                    ServerLevel level = server.getLevel(dim);
                    CanvasStamp.stampForTool(level, q.runs(), n -> Minecraft.getInstance().execute(() -> say(L.t("build.placed", n, OPEN.getTranslatedKeyMessage().getString()))));
                    int ahead = CanvasStamp.queued() - 1;
                    if (ahead > 0) Minecraft.getInstance().execute(() -> say(L.t("build.write_queued", ahead)));
                } catch (Exception e) {
                    FlyingEaves.LOGGER.warn("build tool stamp failed", e);
                    Minecraft.getInstance().execute(() -> say(L.t("build.write_failed", e)));
                }
            });
        } catch (Exception e) {
            say(L.t("build.read_failed", e));
        }
    }
}
