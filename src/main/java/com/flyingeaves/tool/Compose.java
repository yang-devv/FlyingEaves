package com.flyingeaves.tool;

import com.flyingeaves.FlyingEaves;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * 单体建造（参考 The Mighty Architect）：按 H 进入，一次搭一栋。
 * <ul>
 * <li>工具（Alt + 滚轮换）：画房间、挪动、形状、叠层、层高、台基、用途、屋顶、墙、瓦、开敞、正面、换样子、照搬、删房间……房间表里的
 * 每一项都有一个滚轮工具。除了画房间，其余工具都是对准星指着的那个房间用（用途、每层开敞对着的是那一层）；能用滚轮的工具，
 * 右键等于往上滚一格、左键等于往下滚一格；对这间用不上的工具在工具栏里变暗（{@link #whyNot}）。</li>
 * <li>换样子像 TMA 那样用计数，不换种子：往回滚就回到刚才那个样子。不指着房间滚是整栋换；指着房间滚只换这一间；按住 Ctrl 只换它的屋顶。</li>
 * <li>Ctrl+Z 撤销、Ctrl+Y（或 Ctrl+Shift+Z）重做：房间表的每一次变化都记下来（画、挪、改选项、换样子都算）。</li>
 * <li>边改边看：房间或选项一变，就向生成器要一份新的预览，画成半透明方块（{@link Preview}），不写进世界。</li>
 * <li>按 H 打开房间表细调（每层用途、境界……），「生成」按预览的样子放下去，然后退出单体建造。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = FlyingEaves.MODID, value = Dist.CLIENT)
public final class Compose {
    enum Tool {
        DRAW(false),
        ROUND(false),
        MOVE(true),
        SHAPE(true),
        STACK(true),
        GATEWAY(true),
        STACKON(true),
        HEIGHT(true),
        TAPER(true),
        PINGZUO(true),
        PODIUM(true),
        PODSHAPE(true),
        STEPS(true),
        MARGIN(true),
        BASE(true),
        USE(true),
        INTERIOR(true),
        ROOF(true),
        EAVE(true),
        PITCH(true),
        EAVEOUT(true),
        LIFT(true),
        ZOUSHOU(true),
        DOUGONG(true),
        CAIHUA(true),
        WALLS(true),
        BAYSTYLE(true),
        THEME(true),
        GALLERY(true),
        GABLE(true),
        OPEN(true),
        FLOOROPEN(true),
        PALETTE(false),
        FRONT(true),
        REROLL(true),
        COPY(false),
        DELETE(false);

        /** 能用滚轮（右键、左键等于滚一格）。 */
        final boolean wheel;

        Tool(boolean wheel) {
            this.wheel = wheel;
        }

        /** 工具的名字（lang：flyingeaves.tool.&lt;名字&gt;）。 */
        String cn() {
            return L.t("tool." + name().toLowerCase(java.util.Locale.ROOT));
        }

        /** 工具的说明（flyingeaves.tool.&lt;名字&gt;.hint）。 */
        String hint() {
            return L.t("tool." + name().toLowerCase(java.util.Locale.ROOT) + ".hint");
        }
    }

    private static final Random RNG = new java.security.SecureRandom();   // (the OS's randomness, not a clock)
    static boolean drawing;
    static BlockPos cornerA;
    /** 房间表里选中的房间（世界里画成白框）。 */
    static int selected;
    static final List<Room> ROOMS = new ArrayList<>();
    static Tool tool = Tool.DRAW;
    /** 境界（kinds.json 里 tiers 的下标）和预览用的种子；「生成」用同一个种子，所以放下去的和预览一样。 */
    static int tier;
    /** 内饰：整栋屋里摆不摆家具、灯（false = 无：墙、隔断、楼梯、梯子照有）。和境界一样是整栋的，不进撤销。 */
    static boolean interior = true;
    static int seed = RNG.nextInt(100000);
    /** 整栋的样子（计数，往回减就回到原来的样子）。 */
    static int look;
    /** 没单独调过层高的房间用的层高（生成器在预览结果里报回来）。 */
    static int autoSh = 6;
    /** 照搬工具记下的样子。 */
    private static Room clip;
    private static ComposeScreen.Options opts;
    private static long optsStamp;
    private static int optsLang;
    private static String lastRequest = "";
    private static String pendingRequest = "";
    private static long changedAt;

    /** 一个房间：地面上的矩形 + 选项（选项存的是下标，对应 kinds.json 里 compose 的各个列表）。 */
    static final class Room {
        int x0, z0, x1, z1, y;
        int storeys = 1, roof, walls, theme, front;
        /** 画的时候定下的种子（房间换了顺序也不变）、这间的样子、它屋顶的样子（都是计数）、层高（0 = 跟整栋）。 */
        int seed = RNG.nextInt(1 << 30), variant, roofVariant, sh;
        /** 墙样（kinds.json 里 bay_styles 的下标，0 = 随机）。 */
        int bayStyle;
        /** 圆塔（TMA 的 Cylinder）：在画的框里放一座圆的。 */
        boolean round;
        /** 房间设置组合不出来的形状（SPECIALS 的序号，0 = 不是）：牌楼、影壁、随墙门、琉璃门、垂花门。 */
        int special;
        /** 叠放：站在下面那间（前面画的、墙围住这间中心的最后一间）的顶上——那间的屋顶在这间墙边截平成带栏杆的平座。 */
        boolean stacked;
        /** 地块：框里的一块块地面（台地、街道、石阶……，JSON 文本，按框自己的坐标），生成器拆开来铺；别的房间是空的。 */
        String parts = "";
        /** 台基高几格（0 = 一楼直接落在地上）。 */
        int podium;
        /** 基座材料（kinds.json 里 bases 的下标，0 = 默认石砖）：台基、石台、坡上的地基。 */
        int base;
        /** 收分：每往上一层，四边各往里收几格（0 = 每层一样大；塔、楼阁往上变小）。 */
        int taper;
        /** 平座：圆塔上面每层楼板高度一圈带栏杆的走廊，搭在下层檐上，正面有门出去（应县木塔）。 */
        boolean pingzuo;
        /** 前廊（正面一排檐柱、披檐）、后廊（背面同样一排），马头墙（两山墙高出屋顶，徽派）。 */
        boolean veranda, backVeranda, horsehead;
        /** 重檐：屋檐下面再加一圈下檐，墙往上接一截，主屋顶盖在上面（殿、城楼）。 */
        boolean doubleEave;
        /** 三重檐（doubleEave 也是 true）。 */
        boolean tripleEave;
        /** 每层各自的开敞（FLOOR_OPEN 的序号，0 = 跟整间的「开敞」）。 */
        int[] floorOpen = new int[8];
        /** 周围廊：四面一圈檐柱和走廊（和前廊二选一）。 */
        boolean colonnade;
        /** 台基样式：0 平台、1 须弥座、2 三层（PODIUM_STYLES）。 */
        int podiumStyle;
        /** 台阶坡度：台基、台地前那道台阶多陡（STEP_SLOPES 的序号，0 = 45° 一格一级）。 */
        int stepSlope;
        /** 台基边距：墙脚到台基边上走得过去的格数（PODIUM_MARGINS 的序号，0 = 自动：一格，窗台、门灯挡着的那边多一格）。 */
        int podiumMargin;
        /** 台基形状：0 跟随（房间方台基方、圆塔台基圆）、1 方、2 圆（PODIUM_SHAPES）：圆塔下一座方城台、方殿下一座圆坛。 */
        int podiumShape;
        /** 开敞：0 封闭、1 正面敞开、2 全敞开、3 穿堂（OPENNESS；穿堂配「前」按前后盖）。 */
        int openness;
        /** 出入口：0 前、1 前后、2 四面（DOORS）：哪几面有门（台基那面有台阶、栏杆留口）。 */
        int doors;
        /** 屋顶的坡度、出檐、翘角（0 = 自动，跟着「屋顶样子」变；1–4 = 缓…很陡、短…很长、无…大）。 */
        int pitch, overhang, lift;
        /** 走兽（BEAST_COUNTS 的序号，0 = 自动）、斗拱（BRACKET_ROWS，0 = 自动）、彩画（CAIHUAS，0 = 无）。 */
        int beasts, brackets, caihua;
        /** 城台的门洞（TUNNEL_COUNTS 的序号，0 = 自动按台宽）。 */
        int tunnels;
        /** 圆塔的屋顶（ROUND_ROOFS 的序号：攒尖、盔顶、圆顶、盝顶、平顶）。 */
        int roundRoof;
        /** 套的做法（recipeData 的序号，-1 = 自己配的）；做法加的抱厦记着是哪一间的（那一间的种子）。 */
        int recipe = -1;
        long wingOf;

        /** 廊：0 无、1 前廊、2 后廊、3 前后廊、4 周围廊（GALLERIES）。 */
        int gallery() {
            return colonnade ? 4 : veranda && backVeranda ? 3 : backVeranda ? 2 : veranda ? 1 : 0;
        }

        void setGallery(int g) {
            veranda = g == 1 || g == 3;
            backVeranda = g == 2 || g == 3;
            colonnade = g == 4;
        }
        /** 每一层的用途（下标），最多 8 层；默认一楼堂屋、楼上卧房。 */
        final int[] uses = {0, 1, 1, 1, 1, 1, 1, 1};
        /** 材料板上正在换的格子（{材料名: 方块 id}），存下来以前预览就按它们画。 */
        final java.util.Map<String, String> wallOverride = new java.util.TreeMap<>(), roofOverride = new java.util.TreeMap<>();

        Room copy() {
            Room c = new Room();
            c.x0 = x0; c.z0 = z0; c.x1 = x1; c.z1 = z1; c.y = y;
            c.storeys = storeys; c.roof = roof; c.walls = walls; c.theme = theme; c.front = front;
            c.seed = seed; c.variant = variant; c.roofVariant = roofVariant; c.sh = sh; c.bayStyle = bayStyle; c.round = round; c.special = special; c.stacked = stacked; c.podium = podium; c.base = base; c.taper = taper; c.pingzuo = pingzuo; c.veranda = veranda; c.backVeranda = backVeranda; c.horsehead = horsehead; c.doubleEave = doubleEave; c.tripleEave = tripleEave; c.floorOpen = floorOpen.clone(); c.colonnade = colonnade; c.podiumStyle = podiumStyle; c.stepSlope = stepSlope; c.podiumMargin = podiumMargin; c.podiumShape = podiumShape; c.openness = openness; c.doors = doors; c.recipe = recipe; c.wingOf = wingOf; c.pitch = pitch; c.overhang = overhang; c.lift = lift; c.beasts = beasts; c.brackets = brackets; c.caihua = caihua; c.tunnels = tunnels; c.roundRoof = roundRoof;
            System.arraycopy(uses, 0, c.uses, 0, uses.length);
            c.wallOverride.putAll(wallOverride);
            c.roofOverride.putAll(roofOverride);
            c.parts = parts;
            return c;
        }

        String key() {
            return x0 + "," + z0 + "," + x1 + "," + z1 + "," + y + "," + storeys + "," + roof + "," + walls + "," + theme + "," + front
                    + "," + seed + "," + variant + "," + roofVariant + "," + sh + "," + bayStyle + "," + round + "," + special + "," + stacked + "," + podium + "," + base + "," + taper + "," + pingzuo + "," + veranda + "," + backVeranda + "," + horsehead + "," + doubleEave + "," + tripleEave + "," + java.util.Arrays.toString(floorOpen) + "," + colonnade + "," + podiumStyle + "," + stepSlope + "," + podiumMargin + "," + podiumShape + "," + openness + "," + doors + "," + recipe + "," + pitch + "," + overhang + "," + lift + "," + beasts + "," + brackets + "," + caihua + "," + tunnels + "," + roundRoof + "," + java.util.Arrays.toString(uses) + wallOverride + roofOverride + parts.hashCode();
        }

        int storeyHeight() {
            return sh > 0 ? sh : autoSh;
        }

        JsonObject json(ComposeScreen.Options o) {
            JsonObject r = new JsonObject();
            r.addProperty("x0", x0);
            r.addProperty("z0", z0);
            r.addProperty("x1", x1);
            r.addProperty("z1", z1);
            r.addProperty("y", y);
            r.addProperty("storeys", storeys);
            r.addProperty("roof", o.roofs.get(Math.min(roof, o.roofs.size() - 1))[0]);
            if (round && roundRoof > 0) r.addProperty("round_roof", ROUND_ROOFS[Math.min(roundRoof, ROUND_ROOFS.length - 1)][0]);
            if (stacked) r.addProperty("stack", true);
            r.addProperty("walls", o.walls.get(Math.min(walls, o.walls.size() - 1))[0]);
            r.addProperty("theme", o.themes.get(Math.min(theme, o.themes.size() - 1))[0]);
            JsonArray us = new JsonArray();
            for (int s = 0; s < storeys; s++) us.add(o.uses.get(uses[s])[0]);
            r.add("uses", us);
            r.addProperty("use", o.uses.get(uses[0])[0]);
            r.addProperty("use_upper", o.uses.get(uses[Math.min(1, storeys - 1)])[0]);
            r.addProperty("front", o.fronts.get(front)[0]);
            r.addProperty("seed", seed);
            r.addProperty("variant", variant);
            r.addProperty("roof_variant", roofVariant);
            if (sh > 0) r.addProperty("sh", sh);
            if (bayStyle > 0 && bayStyle < o.bayStyles.size()) r.addProperty("bay_style", o.bayStyles.get(bayStyle)[0]);
            if (special > 0 && special < SPECIALS.length) r.addProperty("shape", SPECIALS[special][0]);
            else if (round) r.addProperty("shape", "round");
            if (!parts.isEmpty()) r.add("parts", com.google.gson.JsonParser.parseString(parts));
            r.addProperty("podium", podium);
            if (base > 0 && base < o.bases.size()) r.addProperty("base", o.bases.get(base)[0]);
            if (taper > 0) r.addProperty("taper", taper);
            if (pingzuo && round) r.addProperty("pingzuo", true);
            if (veranda) r.addProperty("veranda", true);
            if (backVeranda) r.addProperty("back_veranda", true);
            if (horsehead) r.addProperty("horsehead", true);
            if (doubleEave) r.addProperty("double_eave", true);
            if (tripleEave) r.addProperty("triple_eave", true);
            boolean anyOpen = false;
            for (int i = 0; i < storeys; i++) anyOpen |= floorOpen[i] > 0;
            if (anyOpen) {
                JsonArray fo = new JsonArray();
                for (int i = 0; i < storeys; i++) fo.add(FLOOR_OPEN[Math.min(floorOpen[i], FLOOR_OPEN.length - 1)][0]);
                r.add("floor_open", fo);
            }
            if (colonnade) r.addProperty("colonnade", true);
            if (podiumStyle > 0) r.addProperty("podium_style", PODIUM_STYLES[podiumStyle][0]);
            if (stepSlope > 0) r.addProperty("step_slope", STEP_SLOPES[Math.min(stepSlope, STEP_SLOPES.length - 1)][0]);
            if (podiumMargin > 0) r.addProperty("podium_margin", PODIUM_MARGINS[Math.min(podiumMargin, PODIUM_MARGINS.length - 1)][0]);
            if (podiumShape > 0) r.addProperty("podium_shape", PODIUM_SHAPES[Math.min(podiumShape, PODIUM_SHAPES.length - 1)][0]);
            if (openness > 0) r.addProperty("open", OPENNESS[openness][0]);
            if (doors > 0) r.addProperty("doors", DOORS[doors][0]);
            if (pitch > 0) r.addProperty("pitch", pitch);
            if (overhang > 0) r.addProperty("overhang", overhang);
            if (lift > 0) r.addProperty("lift", lift);
            if (beasts > 0) r.addProperty("beasts", beasts);
            if (tunnels > 0) r.addProperty("tunnels", tunnels);
            if (brackets > 0) r.addProperty("brackets", brackets);
            if (caihua > 0 && caihua < CAIHUAS.length) r.addProperty("caihua", CAIHUAS[caihua][0]);
            if (!wallOverride.isEmpty()) r.add("walls_override", obj(wallOverride));
            if (!roofOverride.isEmpty()) r.add("roof_override", obj(roofOverride));
            return r;
        }

        private static JsonObject obj(java.util.Map<String, String> m) {
            JsonObject o = new JsonObject();
            m.forEach(o::addProperty);
            return o;
        }

        AABB box() {
            return new AABB(x0, y + 1, z0, x1 + 1, y + 1 + Math.max(4, storeys * storeyHeight()), z1 + 1);
        }
    }

    private Compose() {
    }

    /** 选项（kinds.json 的 compose 部分）；文件变了就重读（生成器发现新材料时会重写它）。 */
    static ComposeScreen.Options options() {
        try {
            long m = Files.getLastModifiedTime(BuildTool.RUN.resolve("kinds.json")).toMillis();
            if (opts == null || m != optsStamp || optsLang != L.version()) {
                opts = ComposeScreen.load();
                optsStamp = m;
                optsLang = L.version();
            }
        } catch (Exception e) {
            return opts;
        }
        return opts;
    }

    /** 选单、滚轮里给出的墙（o.walls 的序号）：全部。 */
    static List<Integer> wallChoices(ComposeScreen.Options o, int current) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < o.walls.size(); i++) out.add(i);
        return out;
    }

    /** 开始搭一栋：房间表清空。 */
    static void start() {
        drawing = true;
        cornerA = null;
        ROOMS.clear();
        selected = 0;
        tool = Tool.DRAW;
        seed = RNG.nextInt(100000);
        look = 0;
        lastRequest = "";
        UNDO.clear();
        REDO.clear();
        histKey = null;
        Preview.clear();
        BuildTool.say(L.t("compose.start", key()));
    }

    /**
     * 新房间能不能并进这一栋：和某个已有房间叠在一起，或者挨着（墙贴着或共用一道墙）且共用的一段至少 3 格。
     * 第一个房间随便画。
     */
    static boolean connects(int x0, int z0, int x1, int z1) {
        if (ROOMS.isEmpty()) return true;
        for (Room o : ROOMS) {
            int gx = Math.max(x0 - o.x1, o.x0 - x1), gz = Math.max(z0 - o.z1, o.z0 - z1);   // <= 0: overlapping on that axis
            if ((gx <= 0 && gz <= 0) || (gx <= 1 && gz <= -2) || (gz <= 1 && gx <= -2)) return true;
        }
        return false;
    }

    static String key() {
        return BuildTool.COMPOSE.getTranslatedKeyMessage().getString();
    }

    /** 随机一栋的键（默认 K）：在准星处随机一栋出预览，再按换一栋。 */
    static String randomKey() {
        return BuildTool.RANDOM.getTranslatedKeyMessage().getString();
    }

    static void stop() {
        PaletteBoard.cancel();
        drawing = false;
        cornerA = null;
        Preview.clear();
        lastRequest = "";
    }

    /** 叠放的房间站在哪间上：前面画的、墙围住它中心的最后一间（牌楼这类不算）；没有返回 null。 */
    static Room stackBase(Room r) {
        int idx = ROOMS.indexOf(r);
        double mx = (r.x0 + r.x1) / 2.0, mz = (r.z0 + r.z1) / 2.0;
        Room best = null;
        for (int i = 0; i < (idx < 0 ? ROOMS.size() : idx); i++) {
            Room o = ROOMS.get(i);
            if (o != r && o.special == 0 && o.x0 <= mx && mx <= o.x1 && o.z0 <= mz && mz <= o.z1) best = o;
        }
        return best;
    }

    /** 叠放的房间的线框放到下面那间顶上（估的：墙顶再上两格；真正的高度生成器按那间屋顶截平的地方算）。 */
    static void restack() {
        for (Room r : ROOMS) {
            if (!r.stacked) continue;
            Room b = stackBase(r);
            if (b != null) r.y = b.y + b.podium + b.storeys * b.storeyHeight() + (b.doubleEave ? 4 : 0) + 2;
        }
    }

    static JsonObject request(ComposeScreen.Options o, String tierId) {
        restack();
        JsonObject req = new JsonObject();
        req.addProperty("tier", tierId);
        req.addProperty("look", look);
        if (!interior) req.addProperty("interior", false);
        JsonArray rooms = new JsonArray();
        for (Room r : ROOMS) rooms.add(r.json(o));
        req.add("rooms", rooms);
        return req;
    }

    /** 两个对角加一个房间（新房间沿用上一间的屋顶、墙、瓦、正面、层高）。成了返回 null，不成返回原因。 */
    static String addRoom(BlockPos a, BlockPos b, String facing) {
        return addRoom(a, b, facing, false);
    }

    static String addRoom(BlockPos a, BlockPos b, String facing, boolean round) {
        Room r = new Room();
        r.round = round;
        r.x0 = Math.min(a.getX(), b.getX());
        r.x1 = Math.max(a.getX(), b.getX());
        r.z0 = Math.min(a.getZ(), b.getZ());
        r.z1 = Math.max(a.getZ(), b.getZ());
        r.y = Math.min(a.getY(), b.getY());
        if (r.x1 - r.x0 < 4 || r.z1 - r.z0 < 4) return L.t(round ? "compose.too_small_tower" : "compose.too_small_room");
        if (r.x1 - r.x0 >= MAX_SIDE || r.z1 - r.z0 >= MAX_SIDE) return L.t("compose.too_big", MAX_SIDE);
        if (round) {                                      // the tower's square: the shorter side, centred in the frame
            int d = Math.min(r.x1 - r.x0, r.z1 - r.z0);
            if (d % 2 == 1) d--;
            int cx = (r.x0 + r.x1) / 2, cz = (r.z0 + r.z1) / 2;
            r.x0 = cx - d / 2;
            r.x1 = cx + d / 2;
            r.z0 = cz - d / 2;
            r.z1 = cz + d / 2;
        }
        int clicked = r.y;
        r.y = groundUnder(r.x0, r.z0, r.x1, r.z1, r.round, clicked);
        int low = lowestGround;
        if (!connects(r.x0, r.z0, r.x1, r.z1)) {       // one building at a time
            return L.t("compose.not_touching");
        }
        Room under = null;                              // drawn inside another room's walls (and smaller): it stands on top of it
        for (Room p : ROOMS)
            if (p.special == 0 && p.x0 <= r.x0 && r.x1 <= p.x1 && p.z0 <= r.z0 && r.z1 <= p.z1
                    && (r.x1 - r.x0 < p.x1 - p.x0 || r.z1 - r.z0 < p.z1 - p.z0)) under = p;
        if (!ROOMS.isEmpty()) {
            Room p = ROOMS.get(ROOMS.size() - 1);
            r.roof = p.roof;
            r.walls = p.walls;
            r.theme = p.theme;
            r.front = p.front;
            r.sh = p.sh;
            r.podium = p.podium;
            r.base = p.base;
        } else {
            applyDefaultStyle(r);                       // 只有点过「设为默认样子」才套（没设过就从 1 层、默认材料开始）
            r.front = switch (facing) {
                case "east" -> 1;
                case "north" -> 2;
                case "west" -> 3;
                default -> 0;
            };
        }
        ROOMS.add(r);
        selected = ROOMS.size() - 1;
        if (under != null) {
            r.stacked = true;
            r.podium = 0;                               // (it stands on the lower room's terrace)
            restack();
            BuildTool.say(L.t(round ? "compose.stacked_tower" : "compose.stacked_room", ROOMS.size(), ROOMS.indexOf(under) + 1));
            return null;
        }
        BuildTool.say((round ? L.t("compose.added_tower", ROOMS.size(), r.x1 - r.x0 + 1) : L.t("compose.added_room", ROOMS.size(), r.x1 - r.x0 + 1, r.z1 - r.z0 + 1))
                + (r.y - low > 0 ? L.t("compose.added_slope", r.y - low) : ""));
        return null;
    }

    /** 台基样式 {生成器里的名字, 中文}；廊的三种。 */
    static final String[][] PODIUM_STYLES = L.pairs("opt.podium_style", "plain", "xumizuo", "tiers", "chengtai");
    /** 台阶坡度（step_slope）：45° 一格高一格；27° 一格半格（半砖、双半砖相间）；18° 三格一格；14° 两格半格。 */
    static final String[][] STEP_SLOPES = {{"45", "45°"}, {"27", "27°"}, {"18", "18°"}, {"14", "14°"}};
    /** 台基边距（podium_margin）：墙脚到台基边（须弥座：到栏杆）走得过去的格数；自动 = 一格，窗台、门灯伸出来挡着的那边自己加宽。
     *  4 以上是比房子大得多的台：三台、坛、钟楼的城台（三层的每层往外跨边距的三分之一，每层一道台阶）。 */
    static final String[][] PODIUM_MARGINS = L.pairs("opt.podium_margin", "auto", "1", "2", "3", "4", "6", "8", "12", "16", "20");
    /** 台基形状（podium_shape）{生成器里的名字, 中文}：跟随房间，或者方、圆（圆塔下方台、方殿下圆坛）。 */
    static final String[][] PODIUM_SHAPES = L.pairs("opt.podium_shape", "", "square", "round");
    /** 形状里房间设置组合不出来的那几种 {生成器里的名字, 中文}。 */
    static final String[][] SPECIALS = L.pairs("opt.special", "", "paifang", "screen", "gate", "glazedgate",
            "chuihua", "wall", "paving", "river", "ornament",
            "terrace", "street", "stairs", "bridge", "patch");   // (石阶：一排高一格的地面，正面朝低的那头)   // (台地、街道：只是一块地面，各在自己的高度；城镇用，高低之间生成器砌挡土墙、接台阶)

    /** 形状一栏能选的：矩形、圆塔，再加 SPECIALS 里的。 */
    static List<String[]> shapes() {
        List<String[]> out = new ArrayList<>();
        out.add(new String[]{"rect", L.t("opt.shape.rect")});
        out.add(new String[]{"round", L.t("opt.shape.round")});
        for (int i = 1; i < SPECIALS.length; i++) out.add(SPECIALS[i]);
        return out;
    }
    static final String[] GALLERIES = L.list("opt.gallery", 5);
    static final String[][] OPENNESS = L.pairs("opt.open", "closed", "front", "open", "through");
    static final String[][] DOORS = L.pairs("opt.doors", "front", "front_back", "four");
    static final String[] PITCHES = L.list("opt.pitch", 5), EAVE_OUTS = L.list("opt.overhang", 5),
            LIFTS = L.list("opt.lift", 5);
    /** 单檐、重檐、三重檐。 */
    static final String[] EAVES = L.list("opt.eaves", 3);
    /** 一间房每边最多几格（生成器那边 compose.MAX_SIDE 一样）：只防画错框；200 格见方的一间生成也只要两三秒。 */
    static final int MAX_SIDE = 256;
    /** 每层的开敞：第一个是「跟整间」。 */
    static final String[][] FLOOR_OPEN = L.pairs("opt.floor_open", "", "closed", "front", "open", "through");
    static final String[] TUNNEL_COUNTS = L.list("opt.tunnels", 5);
    static final String[] BEAST_COUNTS = L.list("opt.beasts", 6), BRACKET_ROWS = L.list("opt.brackets", 5);
    /** 圆塔的屋顶 {生成器里的名字, 中文}。 */
    static final String[][] ROUND_ROOFS = L.pairs("opt.round_roof", "cone", "helmet", "dome", "luding", "flat");
    static final String[][] CAIHUAS = L.pairs("opt.caihua", "", "xuanzi", "hexi", "sushi");
    /** 台基高能选的格数（各种台基样式都照这个高度）。 */
    static final int[] PODIUM_HEIGHTS = {0, 1, 2, 3, 4, 5, 6, 8, 10, 12};
    /** 层高能选的格数（0 = 自动）：大殿一层要 12 到 16 格。 */
    static final int[] STOREY_HEIGHTS = {0, 4, 5, 6, 7, 8, 9, 10, 12, 14, 16};

    /** 台地：它高出旁边的地面时，接下去的台阶按它的「台阶」坡度。 */
    static boolean terrace(Room r) {
        return r.special > 0 && r.special < SPECIALS.length && "terrace".equals(SPECIALS[r.special][0]);
    }

    /** 城墙：宫墙、随墙门、琉璃门的墙框 7 格以上深时，墙跟墙框一样厚；它的「收分」是墙两面往上各收几格。 */
    static boolean wallish(Room r) {
        return r.special > 0 && r.special < SPECIALS.length && List.of("wall", "gate", "glazedgate").contains(SPECIALS[r.special][0]);
    }

    /** 屋顶两头是山墙（硬山、悬山）：马头墙只在这种屋顶上有。 */
    /** 有戗脊（垂脊、角脊）的屋顶：走兽骑在这上面（歇山、庑殿、攒尖、盝顶、十字脊）。 */
    static boolean hipRoof(Room r, ComposeScreen.Options o) {
        String id = o.roofs.get(Math.min(r.roof, o.roofs.size() - 1))[0];
        return id.equals("xieshan") || id.equals("wudian") || id.equals("pyramid") || id.equals("luding") || id.equals("shizi");
    }

    static boolean gableRoof(Room r, ComposeScreen.Options o) {
        String id = o.roofs.get(Math.min(r.roof, o.roofs.size() - 1))[0];
        return id.equals("yingshan") || id.equals("xuanshan");
    }

    /** 上一次 groundUnder 看到的最低的地面。 */
    private static int lowestGround;

    /**
     * 框里的地面：每一列从地表往下，跳过草、花、树叶、树干这些，找到第一块实地（水面也算地面）；取几乎最高的那一列
     * （97% 的列不比它高，个别凸起的石头不算）。房子就坐在坡的高处，不会埋进坡里；低的那边放下时由石台垫到地面，门口接台阶。
     */
    static int groundUnder(int x0, int z0, int x1, int z1, boolean round, int fallback) {
        Minecraft mc = Minecraft.getInstance();
        lowestGround = fallback;
        if (mc.level == null) return fallback;
        var lv = mc.level;
        List<Integer> hs = new ArrayList<>();
        double cx = (x0 + x1) / 2.0, cz = (z0 + z1) / 2.0, rr = (x1 - x0 + 1) / 2.0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (round && (x - cx) * (x - cx) + (z - cz) * (z - cz) > rr * rr) continue;
                if (!lv.hasChunk(x >> 4, z >> 4)) continue;
                int y = surfaceY(lv, m, x, z);
                if (y >= lv.getMinBuildHeight()) hs.add(y);        // (an empty column: a chunk the client does not have)
            }
        }
        if (hs.isEmpty()) return fallback;
        java.util.Collections.sort(hs);
        lowestGround = hs.get(0);
        return hs.get(Math.min(hs.size() - 1, (int) Math.floor(hs.size() * 0.97)));
    }

    /** 一列的地面：从地表往下，跳过草、花、树叶、树干、竹子、仙人掌，第一块实地（水面也算地面）。 */
    static int surfaceY(net.minecraft.world.level.Level lv, BlockPos.MutableBlockPos m, int x, int z) {
        int y = lv.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        int stop = Math.max(lv.getMinBuildHeight(), y - 48);
        for (; y > stop; y--) {
            var s = lv.getBlockState(m.set(x, y, z));
            if (!s.getFluidState().isEmpty()) break;            // water: its surface is the ground
            if (s.isAir() || !s.blocksMotion() || s.canBeReplaced() || s.is(net.minecraft.tags.BlockTags.LEAVES)
                    || s.is(net.minecraft.tags.BlockTags.LOGS) || s.is(net.minecraft.world.level.block.Blocks.BAMBOO)
                    || s.is(net.minecraft.world.level.block.Blocks.CACTUS)) continue;
            break;
        }
        return y;
    }

    /** 朝向（正面朝玩家的那面）在 fronts 里的序号，也是设计转进世界时转几个直角。 */
    static int turnOf(String facing) {
        return switch (facing) {
            case "east" -> 1;
            case "north" -> 2;
            case "west" -> 3;
            default -> 0;
        };
    }

    /**
     * 随机城镇的地形：准星周围 (2R+1)² 列的地面高度，按设计的坐标排（设计里的 (u, v) 转 k 个直角后加到 at 上就是世界里的那一列），
     * 一行行（v 从 -R 起，每行 u 从 -R 起）；不知道的列记 -9999（生成器拿中位数补）。
     * 单人游戏：在服务器上把整块地的区块都要上（没生成的现生成），取完了在客户端线程上调 done——客户端只有视距内的区块，
     * 视距外的列从前读成世界底（-65），宗门就照着「山脚下几百格深的沟」排，大门、围墙排到了虚空里。
     * 没有内置服务器（联机）时只取客户端有的，其余记 -9999。
     * 很大的城镇（自定宽深）交给生成器时每 step 列取一列，一边最多 801 个（和化神宗门一样多），请求不至于几十兆。
     */
    static void sampleHeights(BlockPos at, int k, int R, java.util.function.Consumer<JsonObject> done) {
        Minecraft mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        var level = server == null || mc.level == null ? null : server.getLevel(mc.level.dimension());
        if (level == null) {
            var lv = mc.level;
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            done.accept(packHeights(at, k, R, (x, z) -> lv != null && lv.hasChunk(x >> 4, z >> 4) ? groundY(lv, m, x, z) : UNKNOWN));
            return;
        }
        int n = 2 * R + 1, x0 = at.getX() - R, z0 = at.getZ() - R;
        int[] world = new int[n * n];
        java.util.Arrays.fill(world, UNKNOWN);
        server.execute(() -> CanvasStamp.loadForTool(level, x0, z0, x0 + n - 1, z0 + n - 1, at.getX(), at.getZ(), chunk -> {
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            int cx = chunk.getPos().getMinBlockX(), cz = chunk.getPos().getMinBlockZ();
            for (int x = Math.max(x0, cx); x <= Math.min(x0 + n - 1, cx + 15); x++) {
                for (int z = Math.max(z0, cz); z <= Math.min(z0 + n - 1, cz + 15); z++) {
                    world[(z - z0) * n + (x - x0)] = groundY(level, m, x, z);
                }
            }
        }, () -> mc.execute(() -> done.accept(packHeights(at, k, R, (x, z) -> world[(z - z0) * n + (x - x0)])))));
    }

    /** 高度里「不知道」的记号（生成器拿已知的中位数补）。 */
    static final int UNKNOWN = -9999;

    /** 一列的地面，整列是空的（没加载、没生成的区块读出来就是这样：世界底下一格）记不知道。 */
    private static int groundY(net.minecraft.world.level.Level lv, BlockPos.MutableBlockPos m, int x, int z) {
        int y = surfaceY(lv, m, x, z);
        return y < lv.getMinBuildHeight() ? UNKNOWN : y;
    }

    private static JsonObject packHeights(BlockPos at, int k, int R, java.util.function.IntBinaryOperator height) {
        JsonArray h = new JsonArray();
        int step = Math.max(1, (2 * R + 1 + 800) / 801);
        int half = R / step;
        for (int v = -half * step; v <= half * step; v += step) {
            for (int u = -half * step; u <= half * step; u += step) {
                int[] w = turn(u, v, k);
                h.add(height.applyAsInt(at.getX() + w[0], at.getZ() + w[1]));
            }
        }
        JsonObject o = new JsonObject();
        o.addProperty("R", half * step);
        if (step > 1) o.addProperty("step", step);
        o.add("h", h);
        return o;
    }

    /** 生成这一栋（和预览同一个种子），然后单体建造就结束（房间表清空，下次按 H 搭下一栋）；不满意可以「换一个」或「撤销」。 */
    static void generate(ComposeScreen.Options o, String tierId, int seedToUse) {
        if (ROOMS.isEmpty()) return;
        JsonObject req = request(o, tierId);
        autoKeep(o);                                   // every building generated is kept, without asking
        stop();
        ROOMS.clear();
        BuildTool.placeCompose(req, seedToUse);
    }

    static boolean isPatch(Room r) {
        return r.special > 0 && r.special < SPECIALS.length && SPECIALS[r.special][0].equals("patch");
    }

    /** 准星指着的房间（沿视线最近的那个线框，地块只在别的都没指着时），没有返回 null。 */
    static Room aimed(Minecraft mc) {
        Object[] h = aimedHit(mc);
        return h == null ? null : (Room) h[0];
    }

    /** {房间, 视线碰到线框的点}。先看地块以外的房间：沿视线最近的那个碰到点，在框里含着这个点的房间中挑最小的
     *  （套在别的框里的小房间、陈设优先）；只有什么房间都没碰到才是地块。地块的框围着整个城镇的地面，以前对着房子、
     *  墙、地面滚轮或按住右键常常先碰到它，一挪就把全城的地面挪开了，房子陷进别处的地里、门被挡死。 */
    private static Object[] aimedHit(Minecraft mc) {
        Vec3 eye = mc.player.getEyePosition(1f);
        Vec3 end = eye.add(mc.player.getViewVector(1f).scale(300));
        Object[] best = null, patch = null;
        double bd = Double.MAX_VALUE, pd = Double.MAX_VALUE;
        for (Room r : ROOMS) {
            var hit = r.box().clip(eye, end);
            if (r.box().contains(eye)) hit = java.util.Optional.of(eye);
            if (hit.isEmpty()) continue;
            double d = hit.get().distanceToSqr(eye);
            if (isPatch(r)) {
                if (d < pd) {
                    pd = d;
                    patch = new Object[]{r, hit.get()};
                }
            } else if (d < bd) {
                bd = d;
                best = new Object[]{r, hit.get()};
            }
        }
        if (best == null) return patch;
        Vec3 p = (Vec3) best[1];
        long area = Long.MAX_VALUE;
        for (Room r : ROOMS) {                               // the smallest room whose frame holds that point
            if (isPatch(r) || !r.box().inflate(1e-3).contains(p)) continue;
            long a = (long) (r.x1 - r.x0 + 1) * (r.z1 - r.z0 + 1);
            if (a < area) {
                area = a;
                best = new Object[]{r, p};
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ 材料板存下的新样式：等生成器重读材料、选项里有了再换上

    private static Room pendRoom;
    private static String pendWalls, pendTheme;

    static void assignWhenListed(Room r, String wallId, String themeId) {
        pendRoom = r;
        pendWalls = wallId;
        pendTheme = themeId;
    }

    private static void assignPending(ComposeScreen.Options o) {
        if (pendRoom == null) return;
        if (pendWalls != null) {
            for (int i = 0; i < o.walls.size(); i++) {
                if (o.walls.get(i)[0].equals(pendWalls)) {
                    pendRoom.walls = i;
                    pendRoom.wallOverride.clear();
                    pendWalls = null;
                    break;
                }
            }
        }
        if (pendTheme != null) {
            for (int i = 0; i < o.themes.size(); i++) {
                if (o.themes.get(i)[0].equals(pendTheme)) {
                    pendRoom.theme = i;
                    pendRoom.roofOverride.clear();
                    pendTheme = null;
                    break;
                }
            }
        }
        if (pendWalls == null && pendTheme == null) pendRoom = null;
    }

    // ------------------------------------------------------------------ 存档：整栋设计、默认样子、自动存档

    /** flyingeaves/designs/：每份设计一个文件；_默认样子.json 是新房间用的样子；_自动存档.json 每次改动后自动写。 */
    static final java.nio.file.Path DESIGNS = BuildTool.RUN.resolve("designs");
    private static final String AUTO = "_自动存档", DEFAULT_STYLE = "_默认样子";
    private static boolean dirty;
    private static long dirtyAt;

    private static String idOf(java.util.List<String[]> list, int i) {
        return list.isEmpty() ? "" : list.get(Math.max(0, Math.min(i, list.size() - 1)))[0];
    }

    private static int indexOf(java.util.List<String[]> list, JsonObject o, String key, int dflt) {
        if (o == null || !o.has(key)) return dflt;
        String id = o.get(key).getAsString();
        for (int i = 0; i < list.size(); i++) if (list.get(i)[0].equals(id)) return i;
        return dflt;
    }

    /** 一间房的样子（不含位置）：存的是选项的名字不是下标，加了新材料以后也读得回来。 */
    static JsonObject style(Room r, ComposeScreen.Options o) {
        JsonObject s = new JsonObject();
        s.addProperty("roof", idOf(o.roofs, r.roof));
        s.addProperty("walls", idOf(o.walls, r.walls));
        s.addProperty("theme", idOf(o.themes, r.theme));
        s.addProperty("bay_style", idOf(o.bayStyles, r.bayStyle));
        s.addProperty("base", idOf(o.bases, r.base));
        s.addProperty("taper", r.taper);
        s.addProperty("pingzuo", r.pingzuo);
        s.addProperty("veranda", r.veranda);
        s.addProperty("back_veranda", r.backVeranda);
        s.addProperty("horsehead", r.horsehead);
        s.addProperty("double_eave", r.doubleEave);
        s.addProperty("triple_eave", r.tripleEave);
        JsonArray fos = new JsonArray();
        for (int i = 0; i < 8; i++) fos.add(FLOOR_OPEN[Math.min(r.floorOpen[i], FLOOR_OPEN.length - 1)][0]);
        s.add("floor_open", fos);
        s.addProperty("colonnade", r.colonnade);
        s.addProperty("podium_style", PODIUM_STYLES[r.podiumStyle][0]);
        s.addProperty("step_slope", STEP_SLOPES[Math.min(r.stepSlope, STEP_SLOPES.length - 1)][0]);
        s.addProperty("podium_margin", PODIUM_MARGINS[Math.min(r.podiumMargin, PODIUM_MARGINS.length - 1)][0]);
        s.addProperty("podium_shape", PODIUM_SHAPES[Math.min(r.podiumShape, PODIUM_SHAPES.length - 1)][0]);
        s.addProperty("open", OPENNESS[r.openness][0]);
        s.addProperty("doors", DOORS[r.doors][0]);
        s.addProperty("pitch", r.pitch);
        s.addProperty("overhang", r.overhang);
        s.addProperty("lift", r.lift);
        s.addProperty("beasts", r.beasts);
        s.addProperty("tunnels", r.tunnels);
        s.addProperty("brackets", r.brackets);
        s.addProperty("caihua", CAIHUAS[Math.min(r.caihua, CAIHUAS.length - 1)][0]);
        s.addProperty("round_roof", ROUND_ROOFS[Math.min(r.roundRoof, ROUND_ROOFS.length - 1)][0]);
        s.addProperty("front", idOf(o.fronts, r.front));
        s.addProperty("storeys", r.storeys);
        s.addProperty("sh", r.sh);
        s.addProperty("podium", r.podium);
        s.addProperty("round", r.round);
        s.addProperty("special", SPECIALS[Math.min(r.special, SPECIALS.length - 1)][0]);
        JsonArray us = new JsonArray();
        for (int u : r.uses) us.add(idOf(o.uses, u));
        s.add("uses", us);
        return s;
    }

    static void applyStyle(Room r, JsonObject s, ComposeScreen.Options o, boolean withShape) {
        r.roof = indexOf(o.roofs, s, "roof", r.roof);
        r.walls = indexOf(o.walls, s, "walls", r.walls);
        r.theme = indexOf(o.themes, s, "theme", r.theme);
        r.bayStyle = indexOf(o.bayStyles, s, "bay_style", r.bayStyle);
        r.base = indexOf(o.bases, s, "base", r.base);
        if (s.has("taper")) r.taper = Math.max(0, Math.min(3, s.get("taper").getAsInt()));
        if (s.has("pingzuo")) r.pingzuo = s.get("pingzuo").getAsBoolean();
        if (s.has("veranda")) r.veranda = s.get("veranda").getAsBoolean();
        if (s.has("back_veranda")) r.backVeranda = s.get("back_veranda").getAsBoolean();
        if (s.has("horsehead")) r.horsehead = s.get("horsehead").getAsBoolean();
        if (s.has("double_eave")) r.doubleEave = s.get("double_eave").getAsBoolean();
        if (s.has("triple_eave")) r.tripleEave = s.get("triple_eave").getAsBoolean();
        if (s.has("floor_open") && s.get("floor_open").isJsonArray()) {
            JsonArray fo = s.getAsJsonArray("floor_open");
            for (int i = 0; i < 8; i++) {
                r.floorOpen[i] = 0;
                if (i < fo.size()) for (int j = 1; j < FLOOR_OPEN.length; j++) if (FLOOR_OPEN[j][0].equals(fo.get(i).getAsString())) r.floorOpen[i] = j;
            }
        }
        if (s.has("colonnade")) r.colonnade = s.get("colonnade").getAsBoolean();
        if (s.has("pitch")) r.pitch = Math.max(0, Math.min(4, s.get("pitch").getAsInt()));
        if (s.has("overhang")) r.overhang = Math.max(0, Math.min(4, s.get("overhang").getAsInt()));
        if (s.has("lift")) r.lift = Math.max(0, Math.min(4, s.get("lift").getAsInt()));
        if (s.has("beasts")) r.beasts = Math.max(0, Math.min(BEAST_COUNTS.length - 1, s.get("beasts").getAsInt()));
        if (s.has("tunnels")) r.tunnels = Math.max(0, Math.min(TUNNEL_COUNTS.length - 1, s.get("tunnels").getAsInt()));
        if (s.has("brackets")) r.brackets = Math.max(0, Math.min(BRACKET_ROWS.length - 1, s.get("brackets").getAsInt()));
        if (s.has("round_roof")) {
            r.roundRoof = 0;
            for (int i = 1; i < ROUND_ROOFS.length; i++) if (ROUND_ROOFS[i][0].equals(s.get("round_roof").getAsString())) r.roundRoof = i;
        }
        if (s.has("caihua")) {
            r.caihua = 0;
            for (int i = 1; i < CAIHUAS.length; i++) if (CAIHUAS[i][0].equals(s.get("caihua").getAsString())) r.caihua = i;
        }
        // 开敞、出入口。旧存档只有一个 open：前后门读成 前后 + 封闭，四面门读成 四面 + 封闭，穿堂读成 前后 + 穿堂
        String ov = s.has("open") ? s.get("open").getAsString() : null;
        int oldDoors = "back".equals(ov) ? 1 : "four".equals(ov) ? 2 : -1;
        if (oldDoors >= 0) {
            r.doors = oldDoors;
            r.openness = 0;
        } else {
            if (ov != null) for (int i = 0; i < OPENNESS.length; i++) if (OPENNESS[i][0].equals(ov)) r.openness = i;
            if (s.has("doors") || ov != null) {
                r.doors = 0;
                String dv = s.has("doors") ? s.get("doors").getAsString() : "";
                for (int i = 0; i < DOORS.length; i++) if (DOORS[i][0].equals(dv)) r.doors = i;
            }
            if (r.openness == 3 && r.doors == 0) r.doors = 1;   // 穿堂配「前」按前后盖（生成器 compose.doors_open 一样）
        }
        if (s.has("podium_style")) {
            for (int i = 0; i < PODIUM_STYLES.length; i++) if (PODIUM_STYLES[i][0].equals(s.get("podium_style").getAsString())) r.podiumStyle = i;
        }
        if (s.has("step_slope")) {
            r.stepSlope = 0;
            for (int i = 0; i < STEP_SLOPES.length; i++) if (STEP_SLOPES[i][0].equals(s.get("step_slope").getAsString())) r.stepSlope = i;
        }
        if (s.has("podium_margin")) {
            r.podiumMargin = 0;
            for (int i = 0; i < PODIUM_MARGINS.length; i++) if (PODIUM_MARGINS[i][0].equals(s.get("podium_margin").getAsString())) r.podiumMargin = i;
        }
        if (s.has("podium_shape")) {
            r.podiumShape = 0;
            for (int i = 1; i < PODIUM_SHAPES.length; i++) if (PODIUM_SHAPES[i][0].equals(s.get("podium_shape").getAsString())) r.podiumShape = i;
        }
        if (s.has("storeys")) r.storeys = Math.max(1, Math.min(8, s.get("storeys").getAsInt()));
        if (s.has("sh")) r.sh = Math.max(0, Math.min(16, s.get("sh").getAsInt()));
        if (s.has("podium")) r.podium = Math.max(0, Math.min(12, s.get("podium").getAsInt()));
        if (withShape) {
            r.front = indexOf(o.fronts, s, "front", r.front);
            if (s.has("round")) r.round = s.get("round").getAsBoolean();
            if (s.has("special")) {
                r.special = 0;
                for (int i = 1; i < SPECIALS.length; i++) if (SPECIALS[i][0].equals(s.get("special").getAsString())) r.special = i;
            }
        }
        if (s.has("uses")) {
            JsonArray us = s.getAsJsonArray("uses");
            for (int i = 0; i < Math.min(us.size(), r.uses.length); i++) {
                String id = us.get(i).getAsString();
                for (int j = 0; j < o.uses.size(); j++) if (o.uses.get(j)[0].equals(id)) r.uses[i] = j;
            }
        }
    }

    /** 这一栋存成一份设计：位置记成相对最小的那个角，载入时放到准星指的地方。 */
    static JsonObject design(String name, ComposeScreen.Options o) {
        int ax = Integer.MAX_VALUE, ay = Integer.MAX_VALUE, az = Integer.MAX_VALUE;
        for (Room r : ROOMS) {
            ax = Math.min(ax, r.x0);
            ay = Math.min(ay, r.y);
            az = Math.min(az, r.z0);
        }
        JsonObject d = new JsonObject();
        d.addProperty("name", name);
        d.addProperty("time", java.time.LocalDateTime.now().withNano(0).toString().replace('T', ' '));
        d.addProperty("tier", idOf(o.tiers.stream().map(t -> new String[]{t}).toList(), tier));
        d.addProperty("look", look);
        d.addProperty("seed", seed);
        if (!interior) d.addProperty("interior", false);      // (only when 无: older saves read as 有)
        JsonArray rooms = new JsonArray();
        for (Room r : ROOMS) {
            JsonObject j = style(r, o);
            j.addProperty("x0", r.x0 - ax);
            j.addProperty("z0", r.z0 - az);
            j.addProperty("x1", r.x1 - ax);
            j.addProperty("z1", r.z1 - az);
            j.addProperty("y", r.y - ay);
            j.addProperty("seed", r.seed);
            j.addProperty("variant", r.variant);
            j.addProperty("roof_variant", r.roofVariant);
            j.addProperty("stack", r.stacked);
            rooms.add(j);
        }
        d.add("rooms", rooms);
        return d;
    }

    /** 存档文件：名字里的「/」是文件夹（「民居/我的四合院」存进 民居 文件夹）。 */
    private static java.nio.file.Path file(String name) {
        java.nio.file.Path p = DESIGNS;
        String[] parts = name.split("/");
        for (int i = 0; i < parts.length; i++) {
            String safe = parts[i].replaceAll("[\\\\:*?\"<>|]", "_").trim();
            if (safe.isEmpty() || safe.equals(".") || safe.equals("..")) safe = "设计";
            p = p.resolve(i == parts.length - 1 ? safe + ".json" : safe);
        }
        return p;
    }

    /** 预设库：生成器从 data/library/ 放进来的一棵文件夹树（故宫/前朝/太和殿……）。 */
    static final java.nio.file.Path LIBRARY = BuildTool.RUN.resolve("library");

    /** 存档面板里的一项：文件夹，或一份设计（几间房、时间、说明）；site 不空的是预设库里「宫城」那种：不是房间表，
     *  选了就像以前那几个按钮一样叫生成器排（city 随机宫城，city_real 紫禁城 1:1）。 */
    record Entry(String name, String label, java.nio.file.Path path, boolean dir, int rooms, String time, String note, String site) {
    }

    /** 存档的文件名给玩家看的样子：「_自动存档」「生成 …」照游戏的语言。 */
    static String shownName(String fileName) {
        String s = fileName.replace(AUTO, L.t("compose.autosave"));
        if (s.startsWith(AUTO_KEPT)) s = L.t("compose.generated", s.substring(AUTO_KEPT.length()));
        return s;
    }

    /** 自动存的那几种（存档列表里用金色）。 */
    static boolean isAuto(java.nio.file.Path p) {
        String fn = p.getFileName().toString();
        return fn.startsWith(AUTO) || fn.startsWith("上次生成") || fn.startsWith(AUTO_KEPT);
    }

    /** root 下 rel 这个文件夹里有什么：文件夹在前（按名字），设计在后（我的存档新的在前，预设按名字）。 */
    static java.util.List<Entry> entries(java.nio.file.Path root, String rel) {
        java.util.List<Entry> dirs = new ArrayList<>(), files = new ArrayList<>();
        java.nio.file.Path dir = rel.isEmpty() ? root : root.resolve(rel);
        try (var st = java.nio.file.Files.list(dir)) {
            for (java.nio.file.Path p : st.toList()) {
                String fn = p.getFileName().toString();
                if (java.nio.file.Files.isDirectory(p)) {
                    dirs.add(new Entry(fn, root.equals(LIBRARY) ? L.name("lib_dir", fn, fn) : fn, p, true, 0, "", "", ""));
                    continue;
                }
                if (!fn.endsWith(".json") || fn.startsWith(DEFAULT_STYLE)) continue;
                try {
                    JsonObject d = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(p, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                    String site = d.has("site") ? d.get("site").getAsString() : "";
                    String nm = !site.isEmpty() && d.has("name") ? d.get("name").getAsString() : fn.substring(0, fn.length() - 5).replace(AUTO, "自动存档");
                    String note = d.has("note") ? d.get("note").getAsString() : "";
                    String label = shownName(fn.substring(0, fn.length() - 5)), id = fn.substring(0, fn.length() - 5);
                    if (root.equals(LIBRARY)) {                  // the library's names and notes: the lang file's tables in English
                        label = L.name("lib", site.isEmpty() ? id : nm, nm);
                        String rp = (rel.isEmpty() ? "" : rel + "/") + id;
                        note = L.name("lib_note", rp, note);
                    }
                    files.add(new Entry(nm, label, p, false, site.isEmpty() ? d.getAsJsonArray("rooms").size() : 0,
                            d.has("time") ? d.get("time").getAsString() : "", note, site));
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        dirs.sort(java.util.Comparator.comparing(Entry::label));          // (by what is shown: the same as the name in Chinese)
        if (root.equals(DESIGNS)) files.sort((a, b) -> b.time().compareTo(a.time()));
        else files.sort(java.util.Comparator.comparing(Entry::label));
        dirs.addAll(files);
        return dirs;
    }

    static void write(String name, JsonObject d) {
        try {
            java.nio.file.Files.createDirectories(file(name).getParent());
            java.nio.file.Files.writeString(file(name), new com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(d),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            FlyingEaves.LOGGER.warn("design save failed", e);
        }
    }

    /** 存当前这一栋（名字就是文件名）。 */
    static String saveDesign(String name) {
        ComposeScreen.Options o = options();
        if (o == null || ROOMS.isEmpty()) return L.t("compose.nothing_to_save");
        write(name, design(name, o));
        return null;
    }

    /** 生成时自动存的那些（名字「生成 月-日 时:分:秒」），最多留这么多份，旧的删掉。 */
    static final String AUTO_KEPT = "生成 ";
    private static final int AUTO_KEEP_MAX = 60;

    /** 生成一栋就自动存一份（不用手动存）；只留最近 AUTO_KEEP_MAX 份自动存的，自己起名存的不动。 */
    private static void autoKeep(ComposeScreen.Options o) {
        String name = AUTO_KEPT + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"));
        write(name.replace(':', '.'), design(name, o));
        java.util.List<Saved> auto = new ArrayList<>();
        for (Saved s : designs()) if (s.file().getFileName().toString().startsWith(AUTO_KEPT)) auto.add(s);
        for (int i = AUTO_KEEP_MAX; i < auto.size(); i++) deleteDesign(auto.get(i).file());
    }

    /** 存档列表：{名字, 文件, 房间数, 时间}，新的在前；自动存档（正在画的）、每次生成自动存的也在里面。 */
    record Saved(String name, java.nio.file.Path file, int rooms, String time) {
    }

    static java.util.List<Saved> designs() {
        java.util.List<Saved> out = new ArrayList<>();
        try (var st = java.nio.file.Files.list(DESIGNS)) {
            for (java.nio.file.Path p : st.filter(f -> f.toString().endsWith(".json")).toList()) {
                String fn = p.getFileName().toString();
                if (fn.startsWith(DEFAULT_STYLE)) continue;
                try {
                    JsonObject d = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(p, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                    out.add(new Saved(shownName(fn.substring(0, fn.length() - 5)), p, d.getAsJsonArray("rooms").size(),
                            d.has("time") ? d.get("time").getAsString() : ""));
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        out.sort((a, b) -> b.time().compareTo(a.time()));
        return out;
    }

    /** 载入一份设计：最小的那个角放在 at（null 就放回存的时候的地方不可知，放在玩家脚下）。 */
    static String loadDesign(java.nio.file.Path p, BlockPos at) {
        ComposeScreen.Options o = options();
        if (o == null) return L.t("compose.no_options");
        try {
            JsonObject d = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(p, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            if (!drawing) start();
            ROOMS.clear();
            for (var e : d.getAsJsonArray("rooms")) {
                JsonObject j = e.getAsJsonObject();
                Room r = new Room();
                r.x0 = at.getX() + j.get("x0").getAsInt();
                r.z0 = at.getZ() + j.get("z0").getAsInt();
                r.x1 = at.getX() + j.get("x1").getAsInt();
                r.z1 = at.getZ() + j.get("z1").getAsInt();
                r.y = at.getY() + j.get("y").getAsInt();
                if (j.has("seed")) r.seed = j.get("seed").getAsInt();
                if (j.has("variant")) r.variant = j.get("variant").getAsInt();
                if (j.has("roof_variant")) r.roofVariant = j.get("roof_variant").getAsInt();
                if (j.has("stack")) r.stacked = j.get("stack").getAsBoolean();
                applyStyle(r, j, o, true);
                ROOMS.add(r);
            }
            int base = Integer.MIN_VALUE;                 // the rooms on the ground: on the highest ground under them
            for (Room r : ROOMS) if (r.y == at.getY()) base = Math.max(base, groundUnder(r.x0, r.z0, r.x1, r.z1, r.round, r.y));
            if (base != Integer.MIN_VALUE) for (Room r : ROOMS) r.y += base - at.getY();
            if (d.has("look")) look = d.get("look").getAsInt();
            if (d.has("seed")) seed = d.get("seed").getAsInt();
            interior = !d.has("interior") || d.get("interior").getAsBoolean();
            if (d.has("tier")) {
                String t = d.get("tier").getAsString();
                for (int i = 0; i < o.tiers.size(); i++) if (o.tiers.get(i).equals(t)) tier = i;
            }
            selected = 0;
            return null;
        } catch (Exception e) {
            return L.t("compose.cant_read", e.getMessage());
        }
    }

    // ------------------------------------------------------------------ 随机一栋用的搭配（data/presets/）
    // 它们都只是房间自己那些设置的组合，所以不是房间表里的一项（用户：做法里只能有默认模式组合不出来的，必须线性无关）；
    // 只给「随机一栋」挑着用。以后默认模式做不出的（牌楼、影壁、墙上的门……）作为新的「形状」加，不放这里。

    private static java.util.List<JsonObject> recipeCache = List.of();
    private static long recipeStamp = -1;

    /** 做法（生成器每次启动时从 data/presets/ 放进 flyingeaves/presets/），按 order 排。 */
    static java.util.List<JsonObject> recipeData() {
        java.nio.file.Path dir = BuildTool.RUN.resolve("presets");
        try {
            long m = java.nio.file.Files.getLastModifiedTime(dir).toMillis();
            if (m == recipeStamp) return recipeCache;
            java.util.List<JsonObject> out = new ArrayList<>();
            try (var st = java.nio.file.Files.list(dir)) {
                for (java.nio.file.Path p : st.filter(f -> f.toString().endsWith(".json")).toList()) {
                    try {
                        JsonObject d = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(p, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                        if (d.has("recipe")) {
                            String fn = p.getFileName().toString();
                            d.addProperty("id", fn.substring(0, fn.length() - 5));
                            out.add(d);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
            out.sort(java.util.Comparator.comparingInt(d -> d.has("order") ? d.get("order").getAsInt() : 999));
            recipeCache = out;
            recipeStamp = m;
        } catch (Exception ignored) {
        }
        return recipeCache;
    }

    /** 做法的列表，给选择面板：{序号, 名字, 一句说明}。 */
    static java.util.List<String[]> recipes() {
        java.util.List<String[]> out = new ArrayList<>();
        java.util.List<JsonObject> d = recipeData();
        for (int i = 0; i < d.size(); i++) out.add(new String[]{"" + i, recipeName(d.get(i)), "", d.get(i).has("note") ? L.name("recipe_note", d.get(i).get("id").getAsString(), d.get(i).get("note").getAsString()) : ""});
        return out;
    }

    /** 做法的名字（游戏的语言）。 */
    static String recipeName(JsonObject rec) {
        return L.name("recipe", rec.has("id") ? rec.get("id").getAsString() : null, rec.get("name").getAsString());
    }

    /** 一种做法按这个框的大小定下来的设置（和存档里一样的写法）：set 照抄，pick 挑一个，by_size 看短边。 */
    private static JsonObject recipeStyle(JsonObject rec, int w, int d, java.util.Random rng) {
        JsonObject s = rec.has("set") ? rec.getAsJsonObject("set").deepCopy() : new JsonObject();
        int shortSide = Math.min(w, d);
        if (rec.has("pick")) {
            for (var e : rec.getAsJsonObject("pick").entrySet()) {
                JsonArray a = e.getValue().getAsJsonArray();
                if (a.size() > 0) s.add(e.getKey(), a.get(rng.nextInt(a.size())));
            }
        }
        if (rec.has("by_size")) {
            for (var e : rec.getAsJsonObject("by_size").entrySet()) {
                JsonArray a = e.getValue().getAsJsonArray();
                if (e.getKey().equals("sh")) {
                    double div = a.get(0).getAsDouble();
                    s.addProperty("sh", Math.max(a.get(1).getAsInt(), Math.min(a.get(2).getAsInt(), (int) Math.round(shortSide / div))));
                    continue;
                }
                for (com.google.gson.JsonElement t : a) {
                    JsonArray p = t.getAsJsonArray();
                    if (shortSide >= p.get(0).getAsInt()) {
                        s.add(e.getKey(), p.get(1));
                        break;
                    }
                }
            }
        }
        return s;
    }

    private static int recipeIndex(String id) {
        java.util.List<JsonObject> d = recipeData();
        for (int i = 0; i < d.size(); i++) if (id.equals(d.get(i).get("id").getAsString())) return i;
        return -1;
    }

    /** 随机一栋用的一个框：在设计自己的坐标里（u 横，v 往正面），正面朝向（0 南 1 东 2 北 3 西，相对设计），套哪种做法。 */
    private record Frame0(int u0, int v0, int u1, int v1, int front, String recipe) {
    }

    private static int odd(int n) {
        return n | 1;
    }

    /**
     * 随机一栋：挑一种平面（单殿、凸字带抱厦、主殿两厢、前殿穿廊后殿、十字、塔院、城楼、楼阁、阙门、重楼），每个框随机大小、随机套一种合适的做法，
     * 整栋放在准星指的地方、正面朝玩家；整栋的瓦和墙有时换一套（青瓦灰墙、绿瓦）。原来的房间表 Ctrl+Z 能回来。
     */
    static String randomBuilding() {
        Minecraft mc = Minecraft.getInstance();
        ComposeScreen.Options o = options();
        if (o == null || recipeData().isEmpty()) return L.t("compose.no_recipes", com.flyingeaves.backend.GeneratorProcess.hint());
        java.util.Random rng = new java.security.SecureRandom();
        java.util.List<Frame0> fr = new ArrayList<>();
        String plan;
        // 随机一栋 = one frame (one bounding box) of a random size, every setting random
        int W = odd(7 + rng.nextInt(36)), D = odd(7 + rng.nextInt(26));
        plan = "一个框 " + W + "×" + D;
        fr.add(new Frame0(0, 0, W - 1, D - 1, 0, ""));
        BlockPos at = BuildTool.target(mc);
        if (at == null) at = mc.player.blockPosition().below();
        int k = switch (BuildTool.facingToPlayer(mc)) {
            case "east" -> 1;
            case "north" -> 2;
            case "west" -> 3;
            default -> 0;
        };
        int cu = 0, cv = 0;                                      // centre the plan on the crosshair
        for (Frame0 f : fr) {
            cu += f.u0() + f.u1();
            cv += f.v0() + f.v1();
        }
        cu /= 2 * fr.size();
        cv /= 2 * fr.size();
        if (!drawing) start();
        ROOMS.clear();
        for (Frame0 f : fr) {
            int[] a = turn(f.u0() - cu, f.v0() - cv, k), b = turn(f.u1() - cu, f.v1() - cv, k);
            Room r = new Room();
            r.x0 = at.getX() + Math.min(a[0], b[0]);
            r.x1 = at.getX() + Math.max(a[0], b[0]);
            r.z0 = at.getZ() + Math.min(a[1], b[1]);
            r.z1 = at.getZ() + Math.max(a[1], b[1]);
            r.y = at.getY();
            r.front = (f.front() + k) % 4;
            ROOMS.add(r);
            int ri = recipeIndex(f.recipe());
            if (ri >= 0) applyRecipe(r, ri);
        }
        int base = Integer.MIN_VALUE;                            // on the highest ground under it all, as when drawing
        for (Room r : ROOMS) base = Math.max(base, groundUnder(r.x0, r.z0, r.x1, r.z1, r.round, r.y));
        // 全参数随机: every setting of every room drawn at random (the walls, tiles and wall style one per building, so it
        // stays one building); the plan only gives the frames and which way they face
        java.util.List<Integer> wallIdx = new ArrayList<>();
        for (int i = 0; i < o.walls.size(); i++) wallIdx.add(i);
        int wall = wallIdx.get(rng.nextInt(wallIdx.size())), theme = rng.nextInt(o.themes.size()), bay = rng.nextInt(Math.max(1, o.bayStyles.size()));
        for (Room r : ROOMS) {
            r.y = base;
            randomizeAll(r, rng, o);
            r.walls = wall;
            r.theme = theme;
            r.bayStyle = bay;
        }
        if (plan.startsWith("阙门")) {                     // one 城台 height for all; only the gate has ways through
            Room g = ROOMS.get(0);
            g.podiumStyle = 3;
            g.podium = Math.max(g.podium, 8);
            g.round = false;
            for (int i = 1; i < ROOMS.size(); i++) {
                Room r = ROOMS.get(i);
                r.round = false;
                r.podiumStyle = 3;
                r.podium = g.podium;
                r.tunnels = 1;
                r.colonnade = false;
                JsonObject s = new JsonObject();
                s.addProperty("roof", i <= 2 ? "xieshan" : "pyramid");
                applyStyle(r, s, o, false);
                r.doubleEave = i > 2;
            }
        }
        if (plan.startsWith("重楼")) {
            Room u = ROOMS.get(ROOMS.size() - 1);
            u.stacked = true;
            u.podium = 0;
            restack();
        }
        seed = rng.nextInt(100000);
        look = 0;
        selected = 0;
        StringBuilder sb = new StringBuilder();
        for (Room r : ROOMS) if (r.recipe >= 0 && r.wingOf == 0) sb.append(sb.length() > 0 ? " + " : "").append(recipeName(recipeData().get(r.recipe)));
        BuildTool.say(L.t("compose.random_building", W, D, ROOMS.size(), randomKey()));
        return null;
    }

    /**
     * 随机一院（总平面图）：从生成器写好的院落里挑一个（flyingeaves/sites/：住宅一到三进、宫院一到三进），整组房间放在准星处、
     * 正门朝玩家——正房、厢房、宫墙、门、铺地、河桥都是房间表里的房间，载入以后每间都能改。
     */
    static String randomSite() {
        return randomSite(false);
    }

    /** city: 宫城（一条中轴的整座宫城，五十来个房间）；否则住宅、宫院。 */
    static String randomSite(boolean city) {
        Minecraft mc = Minecraft.getInstance();
        ComposeScreen.Options o = options();
        if (o == null) return L.t("compose.no_options_hint", com.flyingeaves.backend.GeneratorProcess.hint());
        List<java.nio.file.Path> files = new ArrayList<>();
        try (var st = java.nio.file.Files.list(BuildTool.RUN.resolve("sites"))) {
            st.filter(p -> p.toString().endsWith(".json") && p.getFileName().toString().startsWith("city") == city).forEach(files::add);
        } catch (Exception e) {
            return L.t("compose.no_sites", com.flyingeaves.backend.GeneratorProcess.hint());
        }
        if (files.isEmpty()) return L.t("compose.no_sites", com.flyingeaves.backend.GeneratorProcess.hint());
        JsonObject d;
        try {
            d = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(files.get(new java.security.SecureRandom().nextInt(files.size())),
                    java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            return L.t("compose.site_unreadable", e.getMessage());
        }
        return loadSite(d);
    }

    /** 一份设计（院落、宫城、一句话认出来的）放在准星处、正面朝玩家，进房间表。 */
    static String loadSite(JsonObject d) {
        Minecraft mc = Minecraft.getInstance();
        ComposeScreen.Options o = options();
        if (o == null) return L.t("compose.no_options_hint", com.flyingeaves.backend.GeneratorProcess.hint());
        BlockPos at = BuildTool.target(mc);
        if (at == null) at = mc.player.blockPosition().below();
        int k = turnOf(BuildTool.facingToPlayer(mc));
        // 城镇：每间房（台地、街道）有自己的地面高度。照着游戏取的地形排的（anchored）就放回取地形时的准星和朝向、
        // 高度就是世界里的高度；没有地形的（生成器自己造的起伏）放在准星处，高度相对准星
        boolean terrain = d.has("terrain") && d.get("terrain").getAsBoolean();
        boolean anchored = terrain && d.has("anchored") && d.get("anchored").getAsBoolean() && BuildTool.townAt != null;
        if (anchored) {
            at = BuildTool.townAt;
            k = BuildTool.townTurn;
        }
        int yRef = terrain && d.has("y_ref") ? d.get("y_ref").getAsInt() : 0;
        JsonArray rs = d.getAsJsonArray("rooms");
        int u0 = Integer.MAX_VALUE, v0 = Integer.MAX_VALUE, u1 = Integer.MIN_VALUE, v1 = Integer.MIN_VALUE;
        for (var e : rs) {
            JsonObject j = e.getAsJsonObject();
            u0 = Math.min(u0, j.get("x0").getAsInt());
            v0 = Math.min(v0, j.get("z0").getAsInt());
            u1 = Math.max(u1, j.get("x1").getAsInt());
            v1 = Math.max(v1, j.get("z1").getAsInt());
        }
        int cu = (u0 + u1) / 2, cv = (v0 + v1) / 2;             // the middle of the compound on the crosshair
        if (terrain && d.has("origin")) {                        // (a town: its origin there, as its ground was sampled)
            cu = d.getAsJsonArray("origin").get(0).getAsInt();
            cv = d.getAsJsonArray("origin").get(1).getAsInt();
        }
        if (!terrain && d.has("anchor")) {                       // (紫禁城 1:1: the 午门 end on the crosshair, not the middle -
            cu = d.getAsJsonArray("anchor").get(0).getAsInt();   // the middle would be half a kilometre off, the player inside it)
            cv = d.getAsJsonArray("anchor").get(1).getAsInt();
        }
        boolean level = !terrain && d.has("level") && d.get("level").getAsBoolean();
        if (!drawing) start();
        ROOMS.clear();
        for (var e : rs) {
            JsonObject j = e.getAsJsonObject();
            int[] a = turn(j.get("x0").getAsInt() - cu, j.get("z0").getAsInt() - cv, k), b = turn(j.get("x1").getAsInt() - cu, j.get("z1").getAsInt() - cv, k);
            Room r = new Room();
            r.x0 = at.getX() + Math.min(a[0], b[0]);
            r.x1 = at.getX() + Math.max(a[0], b[0]);
            r.z0 = at.getZ() + Math.min(a[1], b[1]);
            r.z1 = at.getZ() + Math.max(a[1], b[1]);
            r.y = at.getY();
            if (terrain && j.has("y")) r.y = anchored ? j.get("y").getAsInt() : at.getY() + j.get("y").getAsInt() - yRef;
            if (j.has("seed")) r.seed = j.get("seed").getAsInt();
            applyStyle(r, j, o, true);
            r.front = (r.front + k) % 4;
            if (j.has("stack")) r.stacked = j.get("stack").getAsBoolean();
            if (j.has("parts")) r.parts = j.get("parts").toString();   // 地块: its pieces, in its own coordinates (turned with it)
            ROOMS.add(r);
        }
        if (!terrain) {
            int base = Integer.MIN_VALUE;                        // on the highest ground under the buildings, as when drawing
            if (!level)                                          // (level: all on the crosshair's ground - the generator paves
                for (Room r : ROOMS) if (r.special == 0) base = Math.max(base, groundUnder(r.x0, r.z0, r.x1, r.z1, r.round, r.y));
            // the whole rectangle there and clears what is above it, the game fills dips under it down to solid ground)
            if (base == Integer.MIN_VALUE) base = at.getY();
            for (Room r : ROOMS) r.y = base;
        }
        restack();
        seed = new java.security.SecureRandom().nextInt(100000);
        look = 0;
        selected = 0;
        if (terrain) {
            int nb = 0;
            for (Room r : ROOMS) if (r.special == 0) nb++;
            boolean sect = d.has("kind") && d.get("kind").getAsString().equals("sect");
            BuildTool.say(L.t(sect ? "compose.sect_loaded" : "compose.town_loaded", L.siteName(d.get("name").getAsString()), nb, ROOMS.size(),
                    anchored ? L.t("compose.on_terrain") : ""));
        } else if (level) {
            BuildTool.say(L.t("compose.city_loaded", L.siteName(d.get("name").getAsString()), ROOMS.size(), u1 - u0 + 1, v1 - v0 + 1,
                    previewOff() ? L.t("compose.no_preview_frames") : "", key()));
        } else if (!d.get("name").getAsString().startsWith("一句话"))
            BuildTool.say(L.t("compose.court_loaded", L.siteName(d.get("name").getAsString()), ROOMS.size()));
        return null;
    }

    /** 一间房的每个设置都随机（形状只在矩形和圆塔里挑：牌楼、墙这些不是房子）。 */
    /** 随机一栋的用途：除了空房哪个都行。 */
    static int randomUse(java.util.Random rng, ComposeScreen.Options o) {
        int empty = -1;
        for (int i = 0; i < o.uses.size(); i++) if (o.uses.get(i)[0].equals("empty")) empty = i;
        if (empty < 0 || o.uses.size() < 2) return rng.nextInt(o.uses.size());
        int u = rng.nextInt(o.uses.size() - 1);
        return u >= empty ? u + 1 : u;
    }

    static void randomizeAll(Room r, java.util.Random rng, ComposeScreen.Options o) {
        r.special = 0;
        r.round = rng.nextFloat() < 0.15f;
        r.storeys = 1 + rng.nextInt(5);
        r.sh = STOREY_HEIGHTS[rng.nextInt(STOREY_HEIGHTS.length)];
        r.taper = r.storeys > 1 ? rng.nextInt(4) : 0;
        r.podium = PODIUM_HEIGHTS[rng.nextInt(PODIUM_HEIGHTS.length)];
        r.podiumStyle = rng.nextInt(PODIUM_STYLES.length);
        r.tunnels = rng.nextInt(TUNNEL_COUNTS.length);
        r.base = rng.nextInt(Math.max(1, o.bases.size()));
        r.roof = rng.nextInt(o.roofs.size());
        r.roundRoof = rng.nextInt(ROUND_ROOFS.length);
        int ev = rng.nextInt(3);
        r.doubleEave = ev >= 1;
        r.tripleEave = ev == 2;
        r.pitch = rng.nextInt(PITCHES.length);
        r.overhang = rng.nextInt(EAVE_OUTS.length);
        r.lift = rng.nextInt(LIFTS.length);
        r.beasts = rng.nextInt(BEAST_COUNTS.length);
        r.brackets = rng.nextInt(BRACKET_ROWS.length);
        r.caihua = rng.nextInt(CAIHUAS.length);
        r.setGallery(rng.nextInt(GALLERIES.length));
        r.horsehead = rng.nextBoolean();
        r.openness = rng.nextFloat() < 0.1f ? 3 : rng.nextInt(3);   // (穿堂 now and then: a gate hall's)
        float df = rng.nextFloat();
        r.doors = df < 0.7f ? 0 : df < 0.9f ? 1 : 2;         // (mostly the front only: 前后 / 四面 are for halls on an axis)
        for (int i = 0; i < 8; i++) {
            r.uses[i] = randomUse(rng, o);
            r.floorOpen[i] = rng.nextFloat() < 0.25f ? rng.nextInt(FLOOR_OPEN.length) : 0;
        }
        r.variant = rng.nextInt(4);
        r.roofVariant = rng.nextInt(4);
        r.stepSlope = rng.nextInt(STEP_SLOPES.length);
        r.podiumMargin = rng.nextInt(5);                     // (自动–4: a random room's platform no bigger than it was)
        r.podiumShape = rng.nextFloat() < 0.15f ? 1 + rng.nextInt(2) : 0;
        r.pingzuo = r.round && r.storeys > 1 && rng.nextBoolean();
        r.recipe = -1;
    }

    /** 设计坐标 (u, v) 转 k 个直角：0 正面朝南，1 朝东，2 朝北，3 朝西。 */
    private static int[] turn(int u, int v, int k) {
        return switch (k) {
            case 1 -> new int[]{v, -u};
            case 2 -> new int[]{-u, -v};
            case 3 -> new int[]{-v, u};
            default -> new int[]{u, v};
        };
    }

    /**
     * 给画好的这一间套一种做法：设置按框的大小定（层高、台基、层数……），能挑的随这间的种子和「换个样子」挑；
     * 带抱厦的做法在四面加上房间（大小按这间算，正面朝外），再套一次会先拿掉上次加的。都是普通的房间，之后照样能改。
     */
    static void applyRecipe(Room r, int idx) {
        ComposeScreen.Options o = options();
        java.util.List<JsonObject> recs = recipeData();
        if (o == null || idx < 0 || idx >= recs.size()) return;
        JsonObject rec = recs.get(idx);
        r.recipe = idx;
        java.util.Random rng = new java.util.Random(r.seed * 31L + r.variant * 131L + look * 7L + idx);
        int w = r.x1 - r.x0 + 1, d = r.z1 - r.z0 + 1;
        applyStyle(r, recipeStyle(rec, w, d, rng), o, false);
        ROOMS.removeIf(x -> x != r && x.wingOf == r.seed);
        if (rec.has("wings")) {
            for (com.google.gson.JsonElement we : rec.getAsJsonArray("wings")) {
                JsonObject wd = we.getAsJsonObject();
                String sides = wd.has("sides") ? wd.get("sides").getAsString() : "nsew";
                double wf = wd.has("w") ? wd.get("w").getAsDouble() : 0.6, df = wd.has("d") ? wd.get("d").getAsDouble() : 0.45;
                int minD = wd.has("min_d") ? wd.get("min_d").getAsInt() : 5;
                int cx = (r.x0 + r.x1) / 2, cz = (r.z0 + r.z1) / 2;
                for (char sd : sides.toCharArray()) {
                    boolean ns = sd == 'n' || sd == 's';
                    int along = ns ? w : d, across = ns ? d : w;
                    int ww = Math.max(5, (int) Math.round(along * wf)) | 1, dd = Math.max(minD, (int) Math.round(across * df));
                    int half = ww / 2;
                    Room x = new Room();
                    x.y = r.y;
                    x.wingOf = r.seed;
                    switch (sd) {
                        case 'n' -> { x.x0 = cx - half; x.x1 = cx + half; x.z1 = r.z0 - 1; x.z0 = x.z1 - dd + 1; x.front = 2; }
                        case 's' -> { x.x0 = cx - half; x.x1 = cx + half; x.z0 = r.z1 + 1; x.z1 = x.z0 + dd - 1; x.front = 0; }
                        case 'w' -> { x.z0 = cz - half; x.z1 = cz + half; x.x1 = r.x0 - 1; x.x0 = x.x1 - dd + 1; x.front = 3; }
                        default -> { x.z0 = cz - half; x.z1 = cz + half; x.x0 = r.x1 + 1; x.x1 = x.x0 + dd - 1; x.front = 1; }
                    }
                    applyStyle(x, recipeStyle(wd, ww, dd, rng), o, false);
                    ROOMS.add(x);
                }
            }
        }
        selected = ROOMS.indexOf(r);
        BuildTool.say(L.t("compose.recipe", selected + 1, recipeName(rec)) + (rec.has("wings") ? L.t("compose.recipe_wings") : ""));
    }

    static void deleteDesign(java.nio.file.Path p) {
        try {
            java.nio.file.Files.deleteIfExists(p);
        } catch (Exception ignored) {
        }
    }

    /** 把这一间的样子设为默认：以后新画的第一间房就用它。 */
    static void saveDefaultStyle(Room r) {
        ComposeScreen.Options o = options();
        if (o != null) write(DEFAULT_STYLE, style(r, o));
        hasDefault = null;
    }

    private static Boolean hasDefault;

    /** 设过默认样子没有。 */
    static boolean hasDefaultStyle() {
        if (hasDefault == null) hasDefault = java.nio.file.Files.exists(file(DEFAULT_STYLE));
        return hasDefault;
    }

    /** 不要默认样子了：新画的第一间又从 1 层、默认材料开始。 */
    static void clearDefaultStyle() {
        deleteDesign(file(DEFAULT_STYLE));
        hasDefault = null;
    }

    /**
     * 新的第一间房：用点「设为默认样子」存下的样子。没设过就什么也不套（用户：载入、生成以后再画新的，层数这些不要自己带过来）。
     */
    private static void applyDefaultStyle(Room r) {
        ComposeScreen.Options o = options();
        java.nio.file.Path p = file(DEFAULT_STYLE);
        if (o == null || !java.nio.file.Files.exists(p)) return;
        try {
            applyStyle(r, com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(p, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject(),
                    o, false);
        } catch (Exception ignored) {
        }
    }

    /** 每个 tick：改过了、而且停下 2 秒，就写一份自动存档（关游戏、闪退都不丢）。 */
    private static void autosave() {
        if (dirty && System.currentTimeMillis() - dirtyAt > 2000 && !ROOMS.isEmpty()) {
            dirty = false;
            ComposeScreen.Options o = options();
            if (o != null) write(AUTO, design("自动存档", o));
        }
    }

    // ------------------------------------------------------------------ 撤销 / 重做：房间表每变一次就记一份

    private record State(List<Room> rooms, int look, int selected) {
    }

    private static final Deque<State> UNDO = new ArrayDeque<>(), REDO = new ArrayDeque<>();
    private static String histKey;
    private static State histState;
    private static boolean restoring;

    private static State snapshot() {
        List<Room> rs = new ArrayList<>();
        for (Room r : ROOMS) rs.add(r.copy());
        return new State(rs, look, selected);
    }

    private static String stateKey() {
        StringBuilder sb = new StringBuilder().append(look).append('|');
        for (Room r : ROOMS) sb.append(r.key()).append(';');
        return sb.toString();
    }

    private static void restore(State s) {
        ROOMS.clear();
        for (Room r : s.rooms()) ROOMS.add(r.copy());
        look = s.look();
        selected = Math.max(0, Math.min(s.selected(), ROOMS.size() - 1));
        restoring = true;
    }

    /** 每个 tick 看房间表变了没有，变了就把变之前的那份压进撤销栈。 */
    private static void recordHistory() {
        String k = stateKey();
        if (histKey == null) {
            histKey = k;
            histState = snapshot();
        } else if (!k.equals(histKey)) {
            if (!restoring) {
                UNDO.push(histState);
                while (UNDO.size() > 200) UNDO.removeLast();
                REDO.clear();
            }
            restoring = false;
            histKey = k;
            histState = snapshot();
            dirty = true;
            dirtyAt = System.currentTimeMillis();
        }
    }

    static void undo() {
        if (UNDO.isEmpty()) {
            BuildTool.say(L.t("compose.undo_none"));
            return;
        }
        REDO.push(snapshot());
        restore(UNDO.pop());
        BuildTool.say(L.t("compose.undo", UNDO.size()));
    }

    static void redo() {
        if (REDO.isEmpty()) {
            BuildTool.say(L.t("compose.redo_none"));
            return;
        }
        UNDO.push(snapshot());
        restore(REDO.pop());
        BuildTool.say(L.t("compose.redo", REDO.size()));
    }

    @SubscribeEvent
    public static void keys(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (!drawing || mc.screen != null || event.getAction() != GLFW.GLFW_PRESS) return;
        if ((event.getModifiers() & GLFW.GLFW_MOD_CONTROL) == 0) return;
        boolean shift = (event.getModifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
        if (event.getKey() == GLFW.GLFW_KEY_Z) {
            if (shift) redo();
            else undo();
        } else if (event.getKey() == GLFW.GLFW_KEY_Y) {
            redo();
        }
    }

    // ------------------------------------------------------------------ 预览：房间或选项变了就要一份新的

    /** 房间的外接矩形超过这么多列就不要预览（紫禁城 1:1 约 82 万列：一份预览就是整个生成，一分多钟，几千万格客户端画不动）。 */
    static final long PREVIEW_MAX_AREA = 700_000;

    /** 这一栋太大，不出预览，只看线框。 */
    static boolean previewOff() {
        if (ROOMS.isEmpty()) return false;
        int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (Room r : ROOMS) {
            x0 = Math.min(x0, r.x0);
            z0 = Math.min(z0, r.z0);
            x1 = Math.max(x1, r.x1);
            z1 = Math.max(z1, r.z1);
        }
        return (long) (x1 - x0 + 1) * (z1 - z0 + 1) > PREVIEW_MAX_AREA;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (drawing) {
            PaletteBoard.tick(mc);
            ComposeScreen.Options po = options();
            if (po != null) assignPending(po);
            recordHistory();
            autosave();
        }
        if (!drawing || ROOMS.isEmpty()) {
            if (Preview.shown() && !drawing) Preview.clear();
            if (drawing && ROOMS.isEmpty() && Preview.shown()) Preview.clear();
            return;
        }
        ComposeScreen.Options o = options();
        if (o == null) return;
        if (previewOff()) {                                // (a 1:1 palace: no preview, the frames only)
            if (Preview.shown()) Preview.clear();
            lastRequest = "";
            return;
        }
        tier = Math.max(0, Math.min(tier, o.tiers.size() - 1));
        JsonObject req = request(o, o.tiers.get(tier));
        req.addProperty("seed", seed);
        String key = req.toString();
        if (!key.equals(pendingRequest)) {
            pendingRequest = key;
            changedAt = System.currentTimeMillis();
        }
        // wait until edits settle for a moment, one request in flight at a time
        if (!key.equals(lastRequest) && System.currentTimeMillis() - changedAt > 250) {   // (a newer preview replaces one still on its way)
            lastRequest = key;
            BuildTool.requestPreview(req);
        }
    }

    // ------------------------------------------------------------------ 工具：Alt + 滚轮换；能用滚轮的工具不按 Alt 滚就是改

    @SubscribeEvent
    public static void scroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!drawing || mc.screen != null || mc.player == null) return;
        int d = event.getScrollDelta() > 0 ? 1 : -1;
        if (Screen.hasAltDown()) {
            event.setCanceled(true);
            // in the order the tool column shows them (the groups), so Alt+wheel walks down the column
            Tool[] all = java.util.Arrays.stream(GROUPS).flatMap(java.util.Arrays::stream).toArray(Tool[]::new);
            int at = java.util.Arrays.asList(all).indexOf(tool);
            tool = all[Math.floorMod((at < 0 ? 0 : at) - d, all.length)];
            cornerA = null;
            return;
        }
        if (!tool.wheel) return;
        Object[] h = aimedHit(mc);
        if (h == null && tool != Tool.REROLL && tool != Tool.INTERIOR) return;   // not pointing at a room: the hotbar scrolls as usual
        event.setCanceled(true);
        step(mc, h, d, Screen.hasControlDown());
    }

    @SubscribeEvent
    public static void click(InputEvent.InteractionKeyMappingTriggered event) {
        if (!drawing) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        if (!event.isUseItem() && !event.isAttack()) return;
        if (PaletteBoard.active() && PaletteBoard.aimsAtBoard(mc)) return;     // placing into / breaking out of the board
        event.setCanceled(true);
        event.setSwingHand(false);
        boolean right = event.isUseItem();
        if (tool == Tool.PALETTE && !right) {
            if (PaletteBoard.active()) {
                PaletteBoard.cancel();
                BuildTool.say(L.t("compose.board_cancelled"));
            }
            return;
        }
        if (tool == Tool.DRAW || tool == Tool.ROUND) {
            draw(mc, right);
            return;
        }
        Object[] h = aimedHit(mc);
        ComposeScreen.Options o = options();
        // 用途：弹出列表挑准星指着的那一层；屋顶、墙、瓦 Shift+右键也弹列表
        if (h != null && right && (tool == Tool.USE || (Screen.hasShiftDown() && (tool == Tool.ROOF || tool == Tool.WALLS || tool == Tool.THEME)))) {
            Room r = (Room) h[0];
            selected = ROOMS.indexOf(r);
            if (tool == Tool.USE && whyNot(Tool.USE, r, o) != null) {
                BuildTool.say(L.t("compose.room_prefix", selected + 1) + whyNot(Tool.USE, r, o));
                return;
            }
            switch (tool) {
                case USE -> {
                    int fl = floorOf(r, (Vec3) h[1]);
                    mc.setScreen(new PickScreen(L.t("compose.pick_use", fl + 1, selected + 1), o.uses, () -> r.uses[fl], v -> r.uses[fl] = v, i -> 0));
                }
                case ROOF -> {
                    if (r.round) mc.setScreen(new PickScreen(L.t("compose.pick_round_roof"), java.util.Arrays.asList(ROUND_ROOFS), () -> r.roundRoof, v -> r.roundRoof = v, i -> 0));
                    else mc.setScreen(new PickScreen(L.t("compose.pick_roof"), o.roofs, () -> r.roof, v -> r.roof = v, i -> 0));
                }
                case WALLS -> {
                    List<Integer> wi = wallChoices(o, r.walls);
                    List<String[]> wl = new ArrayList<>();
                    for (int i : wi) wl.add(o.walls.get(i));
                    mc.setScreen(new PickScreen(L.t("compose.pick_walls"), wl, () -> Math.max(0, wi.indexOf(r.walls)), v -> r.walls = wi.get(v), i -> ComposeScreen.swatchOf(wl.get(i))));
                }
                default -> mc.setScreen(new PickScreen(L.t("compose.pick_tiles"), o.themes, () -> r.theme, v -> r.theme = v, i -> ComposeScreen.swatchOf(o.themes.get(i))));
            }
            return;
        }
        if (tool.wheel) {                                   // right = one notch up, left = one notch down
            if (h == null && tool != Tool.REROLL && tool != Tool.INTERIOR) {
                BuildTool.say(L.t("compose.no_room_aimed"));
                return;
            }
            step(mc, h, right ? 1 : -1, Screen.hasControlDown());
            return;
        }
        if (h == null) {
            BuildTool.say(L.t("compose.no_room_aimed"));
            return;
        }
        Room r = (Room) h[0];
        selected = ROOMS.indexOf(r);
        switch (tool) {
            case PALETTE -> {
                if (PaletteBoard.active() && PaletteBoard.room == r) PaletteBoard.save(mc);
                else PaletteBoard.start(r, mc);
            }
            case COPY -> {
                if (!right) return;
                if (Screen.hasShiftDown() || clip == null) {
                    clip = r.copy();
                    BuildTool.say(L.t("compose.copied", selected + 1));
                } else {
                    r.roof = clip.roof;
                    r.walls = clip.walls;
                    r.theme = clip.theme;
                    r.sh = clip.sh;
                    r.bayStyle = clip.bayStyle;
                    r.podium = clip.podium;
                    r.base = clip.base;
                    r.taper = clip.taper;
                    r.pingzuo = clip.pingzuo;
                    r.veranda = clip.veranda;
                    r.backVeranda = clip.backVeranda;
                    r.horsehead = clip.horsehead;
                    r.doubleEave = clip.doubleEave;
                    r.tripleEave = clip.tripleEave;
                    r.floorOpen = clip.floorOpen.clone();
                    r.colonnade = clip.colonnade;
                    r.podiumStyle = clip.podiumStyle;
                    r.stepSlope = clip.stepSlope;
                    r.podiumMargin = clip.podiumMargin;
                    r.podiumShape = clip.podiumShape;
                    r.openness = clip.openness;
                    r.doors = clip.doors;
                    r.pitch = clip.pitch;
                    r.overhang = clip.overhang;
                    r.lift = clip.lift;
                    r.beasts = clip.beasts;
                    r.tunnels = clip.tunnels;
                    r.brackets = clip.brackets;
                    r.caihua = clip.caihua;
                    r.roundRoof = clip.roundRoof;
                    r.seed = clip.seed;
                    r.variant = clip.variant;
                    r.roofVariant = clip.roofVariant;
                    BuildTool.say(L.t("compose.pasted", selected + 1));
                }
            }
            case DELETE -> {
                if (right) {
                    ROOMS.remove(r);
                    selected = Math.max(0, ROOMS.size() - 1);
                    BuildTool.say(L.t("compose.deleted", ROOMS.size()));
                }
            }
            default -> {
            }
        }
    }

    /** 准星碰到线框的点在这间的第几层（0 = 一楼；用途、每层开敞用，和准星信息里标出来的那层一样）。 */
    static int floorOf(Room r, Vec3 p) {
        return WheelSteps.floorAt(p.y, r.y, r.storeyHeight(), r.storeys);
    }

    /**
     * 这个工具对这间房用不上的原因（和房间表里那一行变灰的条件一样），用得上返回 null。用不上的工具在左边工具栏里变暗，
     * 滚了也不改，只说为什么。
     */
    static String whyNot(Tool t, Room r, ComposeScreen.Options o) {
        boolean sp = r.special > 0;
        String spn = sp && r.special < SPECIALS.length ? SPECIALS[r.special][1] : "";
        return switch (t) {
            case STACKON -> stackBase(r) == null && !r.stacked ? L.t("why.stackon") : null;
            case PINGZUO -> sp ? L.t("why.sp_no_pingzuo", spn) : !r.round ? L.t("why.pingzuo_round")
                    : r.storeys < 2 ? L.t("why.pingzuo_storeys") : null;
            case PODSHAPE, MARGIN -> sp ? L.t("why.sp_no_podium", spn) : r.podium == 0 ? L.t("why.no_podium") : null;
            case STEPS -> terrace(r) ? null : sp ? L.t("why.sp_no_steps", spn) : r.podium == 0 ? L.t("why.no_podium")
                    : r.podiumStyle == 3 ? L.t("why.chengtai_steps") : null;
            case GATEWAY -> r.podiumStyle != 3 || r.podium == 0 ? L.t("why.gateway") : null;
            case ZOUSHOU -> o != null && !hipRoof(r, o) && !r.doubleEave ? L.t("why.beasts") : null;
            case GABLE -> o != null && !gableRoof(r, o) ? L.t("why.gable") : null;
            case USE -> sp ? L.t("why.sp_no_use", spn) : null;
            case FLOOROPEN -> sp ? L.t("why.sp_no_floors", spn) : r.round ? L.t("why.floor_open_round") : null;
            default -> null;
        };
    }

    static String interiorSay() {
        return L.t(interior ? "compose.interior_on" : "compose.interior_off");
    }

    /** 能用滚轮的工具往上（d = 1）或往下（d = -1）一格。h：准星指着的房间和碰到线框的点（换样子可以没有）。 */
    static void step(Minecraft mc, Object[] h, int d, boolean ctrl) {
        ComposeScreen.Options o = options();
        if (tool == Tool.INTERIOR) {                        // 整栋的，指没指着房间都一样
            interior = !interior;
            BuildTool.say(interiorSay());
            return;
        }
        if (h == null) {                                    // 换样子，没指着房间：整栋换
            look = Math.max(0, look + d);
            BuildTool.say(L.t("compose.look_all", look + 1, look == 0 ? L.t("compose.look_first") : ""));
            return;
        }
        Room r = (Room) h[0];
        Vec3 p = (Vec3) h[1];
        selected = ROOMS.indexOf(r);
        String who = L.t("compose.room_prefix", selected + 1);
        String no = whyNot(tool, r, o);
        if (no != null) {
            BuildTool.say(who + no);
            return;
        }
        switch (tool) {
            case SHAPE -> {
                int[] sh = WheelSteps.shapeOf(WheelSteps.cycle(WheelSteps.shapeIndex(r.round, r.special), d, SPECIALS.length + 1));
                r.round = sh[0] == 1;
                r.special = sh[1];
                BuildTool.say(who + L.t("say.shape", shapes().get(WheelSteps.shapeIndex(r.round, r.special))[1]));
            }
            case PINGZUO -> {
                r.pingzuo = !r.pingzuo;
                BuildTool.say(who + L.t(r.pingzuo ? "say.pingzuo_on" : "say.pingzuo_off"));
            }
            case PODSHAPE -> {
                r.podiumShape = WheelSteps.cycle(r.podiumShape, d, PODIUM_SHAPES.length);
                BuildTool.say(who + L.t("say.podshape", PODIUM_SHAPES[r.podiumShape][1]) + (r.podiumShape == 0 ? L.t(r.round ? "say.follow_round" : "say.follow_square") : ""));
            }
            case STEPS -> {
                r.stepSlope = WheelSteps.cycle(r.stepSlope, d, STEP_SLOPES.length);
                BuildTool.say(who + L.t("say.steps", STEP_SLOPES[r.stepSlope][1]));
            }
            case MARGIN -> {
                r.podiumMargin = WheelSteps.cycle(r.podiumMargin, d, PODIUM_MARGINS.length);
                BuildTool.say(who + (r.podiumMargin == 0 ? L.t("say.margin_auto") : L.t("say.margin", PODIUM_MARGINS[r.podiumMargin][1])));
            }
            case USE -> {
                int fl = floorOf(r, p);
                r.uses[fl] = WheelSteps.cycle(r.uses[fl], d, o.uses.size());
                BuildTool.say(who + L.t("say.floor_use", fl + 1, o.uses.get(r.uses[fl])[1]));
            }
            case FLOOROPEN -> {
                int fl = floorOf(r, p);
                r.floorOpen[fl] = WheelSteps.cycle(r.floorOpen[fl], d, FLOOR_OPEN.length);
                BuildTool.say(who + L.t("say.floor_open", fl + 1, FLOOR_OPEN[r.floorOpen[fl]][1])
                        + (r.floorOpen[fl] == 0 ? L.t("say.paren", OPENNESS[r.openness][1]) : ""));
            }
            case MOVE -> {
                if (ctrl) {
                    r.y += d;
                    BuildTool.say(who + L.t("say.ground_y", r.y));
                } else {
                    Direction dir = mc.player.getDirection();
                    r.x0 += d * dir.getStepX();
                    r.x1 += d * dir.getStepX();
                    r.z0 += d * dir.getStepZ();
                    r.z1 += d * dir.getStepZ();
                    BuildTool.say(who + L.t("say.moved", cnDir(d > 0 ? dir : dir.getOpposite())));
                }
            }
            case STACK -> {
                r.storeys = Math.max(1, Math.min(8, r.storeys + d));
                BuildTool.say(who + L.t("say.storeys", r.storeys));
            }
            case GATEWAY -> {
                r.tunnels = Math.floorMod(r.tunnels + d, TUNNEL_COUNTS.length);
                BuildTool.say(who + L.t("say.gateways", TUNNEL_COUNTS[r.tunnels]));
            }
            case STACKON -> {
                Room b = stackBase(r);
                r.stacked = !r.stacked;
                if (!r.stacked) r.y = groundUnder(r.x0, r.z0, r.x1, r.z1, r.round, r.y);
                restack();
                BuildTool.say(who + (r.stacked ? L.t("say.stacked_on", ROOMS.indexOf(b) + 1) : L.t("say.on_ground")));
            }
            case HEIGHT -> {
                r.sh = WheelSteps.storeyHeight(r.sh, autoSh, d);
                BuildTool.say(who + (r.sh == 0 ? L.t("say.sh_auto", autoSh) : L.t("say.sh", r.sh)));
            }
            case PODIUM -> {
                if (ctrl) {
                    r.podiumStyle = Math.floorMod(r.podiumStyle + d, PODIUM_STYLES.length);
                    BuildTool.say(who + L.t("say.podium_style", PODIUM_STYLES[r.podiumStyle][1]));
                } else {
                    int pi = 0;
                    while (pi < PODIUM_HEIGHTS.length - 1 && PODIUM_HEIGHTS[pi] < r.podium) pi++;
                    r.podium = PODIUM_HEIGHTS[Math.max(0, Math.min(PODIUM_HEIGHTS.length - 1, pi + d))];
                    BuildTool.say(who + (r.podium == 0 ? L.t("say.no_podium") : L.t("say.podium", r.podium)));
                }
            }
            case TAPER -> {
                r.taper = Math.max(0, Math.min(3, r.taper + d));
                BuildTool.say(who + (wallish(r) ? (r.taper == 0 ? L.t("say.wall_no_taper") : L.t("say.wall_taper", r.taper))
                        : r.taper == 0 ? L.t("say.no_taper") : L.t(r.round ? "say.taper_round" : "say.taper", r.taper)));
            }
            case BASE -> {
                r.base = Math.floorMod(r.base + d, Math.max(1, o.bases.size()));
                BuildTool.say(who + L.t("say.base", o.bases.get(r.base)[1]));
            }
            case EAVE -> {
                int ev = Math.floorMod((r.tripleEave ? 2 : r.doubleEave ? 1 : 0) + d, 3);
                r.doubleEave = ev >= 1;
                r.tripleEave = ev == 2;
                BuildTool.say(who + EAVES[ev]);
            }
            case GALLERY -> {
                int g = Math.floorMod(r.gallery() + d, GALLERIES.length);
                r.setGallery(g);
                BuildTool.say(who + GALLERIES[g]);
            }
            case OPEN -> {
                if (ctrl) {
                    r.doors = Math.floorMod(r.doors + d, DOORS.length);
                    BuildTool.say(who + L.t("say.doors", DOORS[r.doors][1]));
                } else {
                    r.openness = Math.floorMod(r.openness + d, OPENNESS.length);
                    BuildTool.say(who + OPENNESS[r.openness][1]);
                }
            }
            case PITCH -> {
                r.pitch = Math.floorMod(r.pitch + d, PITCHES.length);
                BuildTool.say(who + L.t("say.pitch", PITCHES[r.pitch]));
            }
            case EAVEOUT -> {
                r.overhang = Math.floorMod(r.overhang + d, EAVE_OUTS.length);
                BuildTool.say(who + L.t("say.overhang", EAVE_OUTS[r.overhang]));
            }
            case LIFT -> {
                r.lift = Math.floorMod(r.lift + d, LIFTS.length);
                BuildTool.say(who + L.t("say.lift", LIFTS[r.lift]));
            }
            case ZOUSHOU -> {
                r.beasts = Math.floorMod(r.beasts + d, BEAST_COUNTS.length);
                BuildTool.say(who + L.t("say.beasts", BEAST_COUNTS[r.beasts]));
            }
            case DOUGONG -> {
                r.brackets = Math.floorMod(r.brackets + d, BRACKET_ROWS.length);
                BuildTool.say(who + L.t("say.brackets", BRACKET_ROWS[r.brackets]));
            }
            case CAIHUA -> {
                r.caihua = Math.floorMod(r.caihua + d, CAIHUAS.length);
                BuildTool.say(who + L.t("say.caihua", CAIHUAS[r.caihua][1]));
            }
            case GABLE -> {
                r.horsehead = !r.horsehead;
                BuildTool.say(who + L.t(r.horsehead ? "say.horsehead" : "say.plain_gable"));
            }
            case ROOF -> {
                if (r.round) {
                    r.roundRoof = Math.floorMod(r.roundRoof + d, ROUND_ROOFS.length);
                    BuildTool.say(who + L.t("say.round_roof", ROUND_ROOFS[r.roundRoof][1]));
                    return;
                }
                r.roof = Math.floorMod(r.roof + d, o.roofs.size());
                BuildTool.say(who + o.roofs.get(r.roof)[1]);
            }
            case WALLS -> {
                List<Integer> wi = wallChoices(o, r.walls);    
                r.walls = wi.get(Math.floorMod(Math.max(0, wi.indexOf(r.walls)) + d, wi.size()));
                BuildTool.say(who + o.walls.get(r.walls)[1]);
            }
            case THEME -> {
                r.theme = Math.floorMod(r.theme + d, o.themes.size());
                BuildTool.say(who + o.themes.get(r.theme)[1]);
            }
            case BAYSTYLE -> {
                r.bayStyle = Math.floorMod(r.bayStyle + d, Math.max(1, o.bayStyles.size()));
                BuildTool.say(who + L.t("say.bay", o.bayStyles.get(r.bayStyle)[1]));
            }
            case FRONT -> {
                r.front = Math.floorMod(r.front + d, 4);
                BuildTool.say(who + L.t("say.front", o.fronts.get(r.front)[1]));
            }
            case REROLL -> {
                if (ctrl) {
                    r.roofVariant = Math.max(0, r.roofVariant + d);
                    BuildTool.say(who + L.t("say.roof_look", r.roofVariant + 1, r.roofVariant == 0 ? L.t("compose.look_first") : ""));
                } else {
                    r.variant = Math.max(0, r.variant + d);
                    BuildTool.say(who + L.t("say.look", r.variant + 1, r.variant == 0 ? L.t("compose.look_first") : ""));
                }
            }
            default -> {
            }
        }
    }

    /** 测试用（开发管道 wheel …）：拿某个工具对第 i 个房间滚一格；face 是对着的墙（w e n s），i < 0 是没指着房间；
     *  floor >= 0：对着那一层的半腰（用途、每层开敞）。 */
    static void testStep(Tool t, int i, char face, int d, boolean ctrl, int floor) {
        Minecraft mc = Minecraft.getInstance();
        tool = t;
        if (i < 0 || i >= ROOMS.size()) {
            step(mc, null, d, ctrl);
            return;
        }
        Room r = ROOMS.get(i);
        AABB b = r.box();
        Vec3 c = b.getCenter();
        Vec3 p = switch (face) {
            case 'w' -> new Vec3(b.minX, c.y, c.z);
            case 'e' -> new Vec3(b.maxX, c.y, c.z);
            case 'n' -> new Vec3(c.x, c.y, b.minZ);
            case 's' -> new Vec3(c.x, c.y, b.maxZ);
            default -> c;
        };
        if (floor >= 0) p = new Vec3(p.x, r.y + 1 + (floor + 0.5) * r.storeyHeight(), p.z);
        step(mc, new Object[]{r, p}, d, ctrl);
    }

    static String describe() {
        StringBuilder sb = new StringBuilder("look " + look + " undo " + UNDO.size() + " redo " + REDO.size() + " autoSh " + autoSh);
        for (Room r : ROOMS) sb.append(" | ").append(r.key());
        return sb.toString();
    }

    private static String cnDir(Direction d) {
        return switch (d) {
            case NORTH -> L.t("dir.north");
            case SOUTH -> L.t("dir.south");
            case EAST -> L.t("dir.east");
            case WEST -> L.t("dir.west");
            default -> "";
        };
    }

    private static void draw(Minecraft mc, boolean right) {
        if (right) {
            BlockPos t = BuildTool.target(mc);
            if (t == null) {
                BuildTool.say(L.t("compose.no_ground"));
                return;
            }
            if (cornerA == null) {
                cornerA = t;
                BuildTool.say(L.t("compose.first_corner", t.getX(), t.getZ()));
            } else {
                BlockPos a = cornerA;
                cornerA = null;
                String err = addRoom(a, t, BuildTool.facingToPlayer(mc), tool == Tool.ROUND);
                if (err != null) BuildTool.say(err);
            }
        } else if (cornerA != null) {
            cornerA = null;
            BuildTool.say(L.t("compose.corner_cancelled"));
        } else if (!ROOMS.isEmpty()) {
            ROOMS.remove(ROOMS.size() - 1);
            selected = Math.max(0, ROOMS.size() - 1);
            BuildTool.say(L.t("compose.removed_last", ROOMS.size()));
        } else {                                           // nothing left to delete: leave the mode
            stop();
            BuildTool.say(L.t("compose.left_mode"));
        }
    }

    // ------------------------------------------------------------------ 画：线框和屏幕上方的工具条

    /** 左边工具栏里对准星指着的房间用不上的工具的颜色（比没选中的再暗一些）。 */
    private static final int DIMMED = 0xFF3A424D;

    /** 工具分组（左边工具栏里组和组之间空一点）。 */
    private static final Tool[][] GROUPS = {
            {Tool.DRAW, Tool.ROUND}, {Tool.MOVE}, {Tool.SHAPE, Tool.STACK, Tool.STACKON, Tool.HEIGHT, Tool.TAPER, Tool.PINGZUO},
            {Tool.PODIUM, Tool.PODSHAPE, Tool.STEPS, Tool.MARGIN, Tool.GATEWAY, Tool.BASE},
            {Tool.USE, Tool.INTERIOR, Tool.ROOF, Tool.EAVE, Tool.PITCH, Tool.EAVEOUT, Tool.LIFT, Tool.ZOUSHOU, Tool.DOUGONG, Tool.CAIHUA, Tool.WALLS, Tool.BAYSTYLE, Tool.THEME, Tool.GALLERY, Tool.GABLE, Tool.OPEN, Tool.FLOOROPEN, Tool.PALETTE, Tool.FRONT},
            {Tool.REROLL, Tool.COPY, Tool.DELETE}};

    /**
     * 屏幕上的提示：左边一列竖着的工具栏（全部工具，当前的高亮，上面是预览状态），屏幕正上方一个小框写当前工具的说明
     * （材料板摆出来时换成材料板的说明）。物品栏上方留给游戏自己的提示（每次操作的回话、物品名），不叠在一起；不挡右上角的小地图。
     */
    @SubscribeEvent
    public static void hud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!drawing || mc.screen != null || mc.options.hideGui) return;
        GuiGraphics g = event.getGuiGraphics();
        var font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        // ---- the tool column on the left
        int rowH = 11, groupGap = 4, pad = 6;
        int colW = 0;
        for (Tool t : Tool.values()) colW = Math.max(colW, font.width(t.cn()) + 22);
        colW = Math.max(colW, font.width(L.t("hud.preview_blocks", 88888)) + 2 * pad);
        String status = ROOMS.isEmpty() ? L.t("hud.no_rooms") : previewOff() ? L.t("hud.too_big") : BuildTool.previewPending() ? BuildTool.previewLabel() : Preview.shown() ? L.t("hud.preview_blocks", Preview.blocks()) : "";
        int rows = Tool.values().length;
        int fixed = pad * 2 + 1 + 10 + (GROUPS.length - 1) * groupGap + (look > 0 ? 10 : 0) + (interior ? 0 : 10);
        if (fixed + rows * rowH > h - 8) rowH = Math.max(9, (h - 8 - fixed) / rows);
        int split = GROUPS.length;                     // still too tall: the later groups in a second column, split where the two are most even
        int colH = fixed + rows * rowH;
        if (colH > h - 8) {
            int best = Integer.MAX_VALUE, a = 0;
            for (int gi = 1; gi < GROUPS.length; gi++) {
                a += GROUPS[gi - 1].length;
                if (Math.max(a, rows - a) < best) {
                    best = Math.max(a, rows - a);
                    split = gi;
                }
            }
            colH = fixed + best * rowH;
        }
        int x = 4, y = Math.max(4, (h - colH) / 2 - 10);
        if (split < GROUPS.length) colW = colW * 2;
        Ui.round(g, x, y, colW, colH, 0xB0101418);
        int ty = y + pad;
        Ui.cap(status, font.width(status) > colW - 2 * pad);
        g.drawString(font, font.width(status) > colW - 2 * pad ? font.plainSubstrByWidth(status, colW - 2 * pad - 4) + "…" : status,
                x + pad, ty, Ui.SUB, false);
        ty += 10;
        if (look > 0) {
            g.drawString(font, L.t("hud.look", look + 1), x + pad, ty, Ui.SUB, false);
            ty += 10;
        }
        if (!interior) {
            g.drawString(font, L.t("hud.interior_off"), x + pad, ty, Ui.GOLD, false);
            ty += 10;
        }
        g.fill(x + pad, ty, x + colW - pad, ty + 1, 0x40FFFFFF);
        ty += 3;
        int ty0 = ty, cx0 = x, cw = split < GROUPS.length ? colW / 2 : colW;
        Object[] aimHit = ROOMS.isEmpty() ? null : aimedHit(mc);   // the tools that do nothing for the room under the crosshair: dimmed
        Room aimRoom = aimHit == null ? null : (Room) aimHit[0];
        ComposeScreen.Options ao = options();
        for (int gi = 0; gi < GROUPS.length; gi++) {
            if (gi == split) {                         // the second column
                ty = ty0;
                cx0 = x + cw;
            }
            for (Tool t : GROUPS[gi]) {
                boolean cur = t == tool;
                if (cur) {
                    Ui.round(g, cx0 + 3, ty - 1, cw - 6, rowH, 0x9038B68D);
                    g.fill(cx0 + 3, ty, cx0 + 5, ty + rowH - 2, Ui.ACCENT);
                }
                boolean off = aimRoom != null && whyNot(t, aimRoom, ao) != null;
                Ui.cap(t.cn(), font.width(t.cn()) > cw - pad - 8);
                g.drawString(font, t.cn(), cx0 + pad + 4, ty + Math.max(0, (rowH - 9) / 2), cur ? (off ? Ui.SUB : 0xFFFFFFFF) : off ? DIMMED : Ui.MUTED, false);
                ty += rowH;
            }
            ty += groupGap;
        }
        // ---- the hint of the current tool, above the hotbar
        String board = PaletteBoard.hudLine(mc);
        String hint = board != null ? board : tool.hint();
        String keys = L.t("hud.keys", key(), randomKey());
        int maxW = Math.min(w - 16, 360);
        int indentW = font.width(tool.cn()) + 6;           // the first line starts after the tool's name: wrap it that much shorter
        List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>();
        if (board != null) lines.addAll(font.split(net.minecraft.network.chat.Component.literal(hint), maxW - 16));
        else {
            var first = font.split(net.minecraft.network.chat.Component.literal(hint), maxW - 16 - indentW);
            if (!first.isEmpty()) {
                lines.add(first.get(0));
                String rest = hint.substring(Math.min(hint.length(), first.get(0) == null ? 0 : plain(first.get(0)).length()));
                if (!rest.isBlank()) lines.addAll(font.split(net.minecraft.network.chat.Component.literal(rest.strip()), maxW - 16));
            }
        }
        Ui.cap(tool.cn(), false);
        Ui.cap(hint, lines.size() > 5);
        if (lines.size() > 5) lines = lines.subList(0, 5);   // (English runs longer than Chinese)
        String off = board == null && aimRoom != null ? whyNot(tool, aimRoom, ao) : null;   // why the current tool does nothing here
        List<net.minecraft.util.FormattedCharSequence> offLines = off == null ? List.of()
                : font.split(net.minecraft.network.chat.Component.literal(L.t("hud.not_here", off)), maxW - 16);
        List<net.minecraft.util.FormattedCharSequence> keyLines = font.split(net.minecraft.network.chat.Component.literal(keys), maxW - 16);
        int bw = 0;
        for (var l : lines) bw = Math.max(bw, font.width(l));
        for (var l : offLines) bw = Math.max(bw, font.width(l));
        for (var l : keyLines) bw = Math.max(bw, font.width(l));
        Ui.cap(keys, false);
        if (off != null) Ui.cap(L.t("hud.not_here", off), false);
        if (look > 0) Ui.cap(L.t("hud.look", look + 1), false);
        if (!interior) Ui.cap(L.t("hud.interior_off"), false);
        bw = Math.min(maxW, bw + 16);
        int bh = 8 + (lines.size() + offLines.size()) * 10 + 1 + keyLines.size() * 10;
        int bx = (w - bw) / 2, by = 4;      // at the top: the hotbar area is where the game's own messages and item names go
        Ui.round(g, bx, by, bw, bh, 0xB0101418);
        g.drawString(font, tool.cn(), bx + 8, by + 4, Ui.ACCENT, false);
        int ly = by + 4;
        int indent = font.width(tool.cn()) + 6;
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), bx + 8 + (i == 0 && board == null ? indent : 0), ly + (i == 0 || board != null ? 0 : 0), Ui.TEXT, false);
            ly += 10;
        }
        for (var l : offLines) {
            g.drawString(font, l, bx + 8, ly, Ui.GOLD, false);
            ly += 10;
        }
        for (var l : keyLines) {
            g.drawString(font, l, bx + 8, ly + 1, Ui.MUTED, false);
            ly += 10;
        }
        if (tool == Tool.USE || tool == Tool.FLOOROPEN) aimedInfo(g, font, w, by + bh + 3, maxW);     // only while setting the floors one by one
    }

    private static String plain(net.minecraft.util.FormattedCharSequence seq) {
        StringBuilder sb = new StringBuilder();
        seq.accept((i, style, cp) -> {
            sb.appendCodePoint(cp);
            return true;
        });
        return sb.toString();
    }

    /** 准星指着的房间是什么：大小、几层，每层做什么用（指着的那层标出来），屋顶、墙、瓦、基座。选错了一眼能看出来。 */
    private static void aimedInfo(GuiGraphics g, net.minecraft.client.gui.Font font, int w, int top, int maxW) {
        Minecraft mc = Minecraft.getInstance();
        ComposeScreen.Options o = options();
        Object[] hit = aimedHit(mc);
        if (o == null || hit == null) return;
        Room r = (Room) hit[0];
        int idx = ROOMS.indexOf(r);
        int shh = Math.max(1, r.storeyHeight());
        int fl = floorOf(r, (Vec3) hit[1]);              // (the floor the wheel and the list change)
        String head = L.t("info.room", idx + 1) + (r.special > 0 ? L.t("compose.quoted", SPECIALS[r.special][1]) : "") + (r.recipe >= 0 && r.recipe < recipeData().size() ? L.t("compose.quoted", recipeName(recipeData().get(r.recipe))) : "") + "  " + (r.round ? L.t("info.tower_d", r.x1 - r.x0 + 1) : (r.x1 - r.x0 + 1) + "×" + (r.z1 - r.z0 + 1))
                + " · " + L.t("info.storeys", r.storeys, shh) + (r.taper > 0 ? " · " + L.t(r.round ? "info.taper_r" : "info.taper", r.taper) : "") + (r.round && r.pingzuo && r.storeys > 1 ? " · " + L.t("info.pingzuo") : "")
                + (r.pitch > 0 ? " · " + L.t("info.pitch", PITCHES[r.pitch]) : "") + (r.overhang > 0 ? " · " + L.t("info.overhang", EAVE_OUTS[r.overhang]) : "")
                + (r.lift > 0 ? " · " + L.t("info.lift", LIFTS[r.lift]) : "")
                + (r.beasts > 0 ? " · " + L.t("info.beasts", BEAST_COUNTS[r.beasts]) : "") + (r.brackets > 0 ? " · " + L.t("info.brackets", BRACKET_ROWS[r.brackets]) : "")
                + (r.caihua > 0 ? " · " + L.t("info.caihua", CAIHUAS[r.caihua][1]) : "")
                + (r.podium > 0 ? " · " + L.t("info.podium", r.podium) : "")
                + (r.podium > 0 && r.podiumStyle == 3 && r.tunnels > 0 ? " · " + L.t("info.gateways", TUNNEL_COUNTS[Math.min(r.tunnels, TUNNEL_COUNTS.length - 1)]) : "");
        List<String> floors = new ArrayList<>();
        for (int s = 0; s < r.storeys; s++)
            floors.add(L.t("info.floor", s + 1, o.uses.get(Math.min(r.uses[s], o.uses.size() - 1))[1])
                    + (r.floorOpen[s] > 0 ? "·" + FLOOR_OPEN[Math.min(r.floorOpen[s], FLOOR_OPEN.length - 1)][1] : ""));
        String mat = L.t("info.roof", r.round ? ROUND_ROOFS[Math.min(r.roundRoof, ROUND_ROOFS.length - 1)][1] : o.roofs.get(Math.min(r.roof, o.roofs.size() - 1))[1]) + (r.tripleEave ? L.t("info.triple") : r.doubleEave ? L.t("info.double") : "")
                + " · " + L.t("info.walls", o.walls.get(Math.min(r.walls, o.walls.size() - 1))[1])
                + " · " + L.t("info.tiles", o.themes.get(Math.min(r.theme, o.themes.size() - 1))[1])
                + (r.podiumStyle > 0 ? " · " + PODIUM_STYLES[r.podiumStyle][1] : "")
                + (r.stepSlope > 0 ? " · " + L.t("info.steps", STEP_SLOPES[Math.min(r.stepSlope, STEP_SLOPES.length - 1)][1]) : "")
                + (r.podiumMargin > 0 ? " · " + L.t("info.margin", PODIUM_MARGINS[Math.min(r.podiumMargin, PODIUM_MARGINS.length - 1)][1]) : "")
                + (r.podiumShape > 0 ? " · " + L.t("info.podium_shape", PODIUM_SHAPES[Math.min(r.podiumShape, PODIUM_SHAPES.length - 1)][1]) : "")
                + (r.base > 0 && r.base < o.bases.size() ? " · " + L.t("info.base", o.bases.get(r.base)[1]) : "")
                + (r.gallery() > 0 ? " · " + GALLERIES[r.gallery()] : "") + (r.horsehead ? " · " + L.t("info.horsehead") : "")
                + (r.openness > 0 ? " · " + OPENNESS[r.openness][1] : "") + (r.doors > 0 ? " · " + L.t("info.doors", DOORS[r.doors][1]) : "");
        // the head and the materials wrap rather than run off (English runs longer)
        var headLines = font.split(net.minecraft.network.chat.Component.literal(head), maxW - 16);
        var matLines = font.split(net.minecraft.network.chat.Component.literal(mat), maxW - 16);
        int bw = 0;
        for (var l : headLines) bw = Math.max(bw, font.width(l));
        for (var l : matLines) bw = Math.max(bw, font.width(l));
        bw = Math.min(maxW, bw + 16);
        // the floors, wrapped into rows that fit; the aimed one in gold
        List<List<Integer>> rows = new ArrayList<>();
        List<Integer> cur = new ArrayList<>();
        int cw = 0;
        for (int s = 0; s < floors.size(); s++) {
            int fw = font.width(floors.get(s)) + 10;
            if (!cur.isEmpty() && cw + fw > maxW - 16) {
                rows.add(cur);
                cur = new ArrayList<>();
                cw = 0;
            }
            cur.add(s);
            cw += fw;
            bw = Math.max(bw, Math.min(maxW, cw + 16));
        }
        if (!cur.isEmpty()) rows.add(cur);
        int bh = 6 + headLines.size() * 10 + rows.size() * 10 + matLines.size() * 10 + 3;
        Ui.cap(head, false);
        for (String fs : floors) Ui.cap(fs, false);
        Ui.cap(mat, false);
        int bx = (w - bw) / 2;
        Ui.round(g, bx, top, bw, bh, 0xB0101418);
        int ly = top + 4;
        for (var l : headLines) {
            g.drawString(font, l, bx + 8, ly, Ui.GOLD, false);
            ly += 10;
        }
        for (List<Integer> row : rows) {
            int lx = bx + 8;
            for (int s : row) {
                String t = floors.get(s);
                if (s == fl) Ui.round(g, lx - 2, ly - 1, font.width(t) + 4, 10, 0x9038B68D);
                g.drawString(font, t, lx, ly, s == fl ? 0xFFFFFFFF : Ui.TEXT, false);
                lx += font.width(t) + 10;
            }
            ly += 10;
        }
        for (var l : matLines) {
            g.drawString(font, l, bx + 8, ly + 1, Ui.MUTED, false);
            ly += 10;
        }
    }

    private static void circle(PoseStack ps, VertexConsumer lines, double cx, double cz, double rad, double y, int rgb) {
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        var pose = ps.last();
        int n = 48;
        for (int i = 0; i < n; i++) {
            double a0 = 2 * Math.PI * i / n, a1 = 2 * Math.PI * (i + 1) / n;
            float x0 = (float) (cx + rad * Math.cos(a0)), z0 = (float) (cz + rad * Math.sin(a0));
            float x1 = (float) (cx + rad * Math.cos(a1)), z1 = (float) (cz + rad * Math.sin(a1));
            float nx = x1 - x0, nz = z1 - z0, len = (float) Math.sqrt(nx * nx + nz * nz);
            lines.vertex(pose.pose(), x0, (float) y, z0).color(r, g, b, 1f).normal(pose.normal(), nx / len, 0, nz / len).endVertex();
            lines.vertex(pose.pose(), x1, (float) y, z1).color(r, g, b, 1f).normal(pose.normal(), nx / len, 0, nz / len).endVertex();
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (!drawing || event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        // 光影（Oculus）画阴影时也会发这个事件，用的是正交投影；只在正常的透视那次画，否则线框会多出一份
        if (event.getProjectionMatrix().m33() != 0f) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buf.getBuffer(RenderType.lines());
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        restack();
        Object[] hit = tool == Tool.DRAW || tool == Tool.ROUND ? null : aimedHit(mc);
        Room aim = hit == null ? null : (Room) hit[0];
        int i = 0;
        for (Room r : ROOMS) {
            float hue = (i * 0.17f) % 1f;
            int rgb = r == aim ? 0xFFE080 : i == selected ? 0xFFFFFF : java.awt.Color.HSBtoRGB(hue, 0.6f, 1f);
            i++;
            LevelRenderer.renderLineBox(ps, lines, r.box(), ((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, 1f);
            if (r.round) {                                   // the tower's circle on the floor and at the top
                AABB b = r.box();
                circle(ps, lines, (b.minX + b.maxX) / 2, (b.minZ + b.maxZ) / 2, (b.maxX - b.minX) / 2, b.minY, rgb);
                circle(ps, lines, (b.minX + b.maxX) / 2, (b.minZ + b.maxZ) / 2, (b.maxX - b.minX) / 2, b.maxY, rgb);
            }
            // front side marked with a low bar
            String f = new String[]{"south", "east", "north", "west"}[r.front];
            AABB bar = switch (f) {
                case "north" -> new AABB(r.x0, r.y + 1, r.z0 - 0.2, r.x1 + 1, r.y + 1.6, r.z0);
                case "east" -> new AABB(r.x1 + 1, r.y + 1, r.z0, r.x1 + 1.2, r.y + 1.6, r.z1 + 1);
                case "west" -> new AABB(r.x0 - 0.2, r.y + 1, r.z0, r.x0, r.y + 1.6, r.z1 + 1);
                default -> new AABB(r.x0, r.y + 1, r.z1 + 1, r.x1 + 1, r.y + 1.6, r.z1 + 1.2);
            };
            LevelRenderer.renderLineBox(ps, lines, bar, 1f, 0.3f, 0.3f, 1f);
        }
        if (cornerA != null && (tool == Tool.DRAW || tool == Tool.ROUND)) {
            BlockPos t = BuildTool.target(mc);
            if (t != null) {
                int ax = Math.min(cornerA.getX(), t.getX()), az = Math.min(cornerA.getZ(), t.getZ());
                int bx = Math.max(cornerA.getX(), t.getX()), bz = Math.max(cornerA.getZ(), t.getZ());
                boolean ok = connects(ax, az, bx, bz) && bx - ax >= 4 && bz - az >= 4;   // red while it cannot be placed
                LevelRenderer.renderLineBox(ps, lines, new AABB(ax, Math.min(cornerA.getY(), t.getY()) + 1, az, bx + 1, Math.min(cornerA.getY(), t.getY()) + 7,
                        bz + 1), 1f, ok ? 1f : 0.35f, ok ? 1f : 0.35f, 1f);
            }
        }
        buf.endBatch(RenderType.lines());
        PaletteBoard.render(mc, ps, buf, event.getCamera());
        ps.popPose();
    }
}
