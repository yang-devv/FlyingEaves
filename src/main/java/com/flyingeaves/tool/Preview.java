package com.flyingeaves.tool;

import com.flyingeaves.FlyingEaves;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.AbstractGlassBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 半透明预览（像 TMA 放下之前的预览）：把生成器给的一栋建筑（列段文件）画成半透明的方块，不写进世界。
 * 只画从外面看得见的面：从包围盒外面往里灌一遍空气，只留贴着这片空气的面（屋里的家具、内墙、楼板都不画，楼层多时面数
 * 少几倍），全亮度，透明度 0.55。面是在后台线程算的；算好以后一次传进显卡（一个顶点缓冲），之后每帧只是让显卡把它画出来，
 * 不再每帧把每个面重新交一遍、重新排序（楼高了就是那样卡的）。改了房间或选项，单体建造会重新要一份预览。
 */
@Mod.EventBusSubscriber(modid = FlyingEaves.MODID, value = Dist.CLIENT)
public final class Preview {
    private record Quad(BlockPos pos, BakedQuad quad, float r, float g, float b) {
    }

    /** A preview is up (its faces are in the vertex buffer; the list of them is not kept). */
    private static volatile boolean shown;
    private static volatile int blocks;
    /** 每要一份新的就加一；后台算好的结果只在还是最新的那份时才换上。 */
    private static volatile int gen;
    /** 也画屋里（家具、内墙、楼板）：慢一点，房间表里可以开关。 */
    static volatile boolean interior;
    /** 最近一份读进来的列段，开关「看室内」时用它重算，不用再要一份。 */
    private static CanvasStamp.Runs last;

    private Preview() {
    }

    static boolean shown() {
        return shown;
    }

    static int blocks() {
        return blocks;
    }

    /** 开发管道用：最近一份列段的指纹（换了设置，预览变没变）。 */
    static String fingerprint() {
        CanvasStamp.Runs r = last;
        if (r == null) return "-";
        long h = java.util.Objects.hash(r.x0(), r.z0(), r.w(), r.d());
        h = h * 31 + java.util.Arrays.hashCode(r.start());
        h = h * 31 + java.util.Arrays.hashCode(r.y0());
        h = h * 31 + java.util.Arrays.hashCode(r.y1());
        h = h * 31 + java.util.Arrays.hashCode(r.pal());
        h = h * 31 + java.util.Arrays.hashCode(r.palette());
        return Long.toHexString(h);
    }

    /** 传进显卡的预览（以 origin 为原点），和画它用的一个缓冲。 */
    private static VertexBuffer vbo;
    private static BlockPos origin = BlockPos.ZERO;
    private static BufferBuilder builder;

    static void clear() {
        gen++;
        shown = false;
        last = null;
        blocks = 0;
        if (vbo != null) {
            vbo.close();
            vbo = null;
        }
    }

    /** 在客户端（渲染）线程上：把面写进一个顶点缓冲传给显卡。 */
    private static void upload(List<Quad> qs) {
        if (vbo != null) {
            vbo.close();
            vbo = null;
        }
        if (qs.isEmpty()) return;
        origin = qs.get(0).pos;
        if (builder == null) builder = new BufferBuilder(1 << 20);
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        PoseStack ps = new PoseStack();
        BlockPos last = null;
        for (Quad q : qs) {
            if (!q.pos.equals(last)) {
                if (last != null) ps.popPose();
                ps.pushPose();
                ps.translate(q.pos.getX() - origin.getX(), q.pos.getY() - origin.getY(), q.pos.getZ() - origin.getZ());
                last = q.pos;
            }
            builder.putBulkData(ps.last(), q.quad, q.r, q.g, q.b, 0.55f, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, false);
        }
        vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vbo.bind();
        vbo.upload(builder.end());
        VertexBuffer.unbind();
    }

    /** 读一个列段文件做成预览：文件在这里读完（之后就能删），面在后台线程算，算好了在客户端线程换上。 */
    static void load(Path dvcr) throws Exception {
        rebuild(CanvasStamp.read(dvcr));
    }

    /** The same for runs already read (the in-game Java generator's, or {@link #load(Path)}'s). */
    static void load(CanvasStamp.Runs runs) {
        rebuild(runs);
    }

    /** 开关「看室内」以后按最近那份重算。 */
    static void refresh() {
        if (last != null) rebuild(last);
    }

    private static void rebuild(CanvasStamp.Runs r) {
        last = r;
        boolean in = interior;
        int my = ++gen;
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                Built b = build(r, in);
                Minecraft.getInstance().execute(() -> {
                    if (my == gen) {
                        shown = !b.quads.isEmpty();
                        blocks = b.blocks;
                        upload(b.quads);
                    }
                });
            } catch (Exception e) {
                FlyingEaves.LOGGER.warn("preview build failed", e);
            }
        });
    }

    private record Built(List<Quad> quads, int blocks) {
    }

    /**
     * The blocks of a preview, read straight from the runs (no map of millions of cells: a column's runs are few): a cell
     * is the last run over it that draws something (air and blocks without a model do not count, as before).
     */
    private static final class Cells {
        final CanvasStamp.Runs r;
        final boolean[] draws;

        Cells(CanvasStamp.Runs r) {
            this.r = r;
            draws = new boolean[r.palette().length];
            for (int i = 0; i < draws.length; i++) {
                BlockState st = r.palette()[i];
                draws[i] = !st.isAir() && st.getRenderShape() == RenderShape.MODEL;
            }
        }

        /** The block drawn at a cell, or null. */
        BlockState at(int x, int y, int z) {
            int i = x - r.x0(), k = z - r.z0();
            if (i < 0 || k < 0 || i >= r.w() || k >= r.d()) return null;
            int col = k * r.w() + i;
            for (int s = r.start()[col + 1] - 1; s >= r.start()[col]; s--) {
                int p = r.pal()[s] & 0xFFFF;
                if (draws[p] && r.y0()[s] <= y && y <= r.y1()[s]) return r.palette()[p];
            }
            return null;
        }

        interface CellFn {
            void cell(int x, int y, int z, BlockState state);
        }

        /** Every drawn cell once (column by column). */
        void forEach(CellFn f) {
            for (int k = 0; k < r.d(); k++) {
                for (int i = 0; i < r.w(); i++) {
                    int col = k * r.w() + i, s0 = r.start()[col], s1 = r.start()[col + 1];
                    for (int s = s0; s < s1; s++) {
                        int p = r.pal()[s] & 0xFFFF;
                        if (!draws[p]) continue;
                        for (int y = r.y0()[s]; y <= r.y1()[s]; y++) {
                            boolean later = false;                  // (a later run over the cell has it)
                            for (int t = s + 1; t < s1 && !later; t++) later = draws[r.pal()[t] & 0xFFFF] && r.y0()[t] <= y && y <= r.y1()[t];
                            if (!later) f.cell(r.x0() + i, y, r.z0() + k, r.palette()[p]);
                        }
                    }
                }
            }
        }
    }

    private static Built build(CanvasStamp.Runs r, boolean in) {
        Cells cells = new Cells(r);
        Minecraft mc = Minecraft.getInstance();
        Outside out0 = in ? null : Outside.of(cells);        // 看室内：不剔屋里的面
        RandomSource rand = RandomSource.create(42);
        List<Quad> out = new ArrayList<>();
        Direction[] faces = Direction.values();
        int[] count = {0};
        cells.forEach((x, y, z, state) -> {
            count[0]++;
            BlockPos pos = new BlockPos(x, y, z);
            if (out0 != null && !out0.touches(pos)) return;              // nothing of it can be seen from outside
            BakedModel model = mc.getBlockRenderer().getBlockModel(state);
            for (int f = 0; f <= faces.length; f++) {
                Direction dir = f < faces.length ? faces[f] : null;
                if (dir != null) {
                    if (out0 != null && !out0.is(pos.relative(dir))) continue;   // this face looks into the building
                    BlockPos np = pos.relative(dir);
                    BlockState nb = cells.at(np.getX(), np.getY(), np.getZ());
                    if (nb != null && nb.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) && state.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) continue;
                }
                rand.setSeed(42);
                for (BakedQuad q : model.getQuads(state, dir, rand, ModelData.EMPTY, null)) {
                    float rr = 1, gg = 1, bb = 1;
                    if (q.isTinted()) {
                        int c = mc.getBlockColors().getColor(state, null, null, q.getTintIndex());
                        if (c != -1) {
                            rr = (c >> 16 & 255) / 255f;
                            gg = (c >> 8 & 255) / 255f;
                            bb = (c & 255) / 255f;
                        }
                    }
                    out.add(new Quad(pos, q, rr, gg, bb));
                }
            }
        });
        return new Built(out, count[0]);
    }

    /**
     * 从外面够得着的格子：包围盒外扩一格，从角上往里灌。整格不透光的方块挡住；台阶、半砖和别的不满一格的东西（灯笼、地毯、
     * 活板门……）灌得进去也穿得过去：屋顶的瓦垄是台阶和半砖，贴着它们的面从外面看得见，以前把它们当实心挡住，那些面就不画，
     * 瓦垄之间漏天。门、窗、玻璃、栅栏、墙、铁栏灌得进去（贴着它们的面照画）但不往里穿，屋里还是灌不进去（看外观时不画屋里，省面数）。
     * 包围盒太大（超过 400 万格）时不算，返回 null，就照旧只按贴着的实心方块剔面。
     */
    private record Outside(int x0, int y0, int z0, int w, int h, int d, java.util.BitSet bits) {
        static Outside of(Cells cells) {
            int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
            cells.forEach((x, y, z, st) -> {
                b[0] = Math.min(b[0], x); b[1] = Math.min(b[1], y); b[2] = Math.min(b[2], z);
                b[3] = Math.max(b[3], x); b[4] = Math.max(b[4], y); b[5] = Math.max(b[5], z);
            });
            if (b[0] > b[3]) return null;
            int x0 = b[0] - 1, y0 = b[1] - 1, z0 = b[2] - 1, w = b[3] - b[0] + 3, h = b[4] - b[1] + 3, d = b[5] - b[2] + 3;
            if ((long) w * h * d > 4_000_000L) return null;
            java.util.BitSet solid = new java.util.BitSet(w * h * d), stop = new java.util.BitSet(w * h * d), seen = new java.util.BitSet(w * h * d);
            cells.forEach((x, y, z, st) -> {
                int j = ((x - x0) * h + (y - y0)) * d + (z - z0);
                if (st.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) solid.set(j);
                else if (opening(st)) stop.set(j);
            });
            int[] stack = new int[w * h * d];
            int sp = 0;
            stack[sp++] = 0;
            seen.set(0);
            while (sp > 0) {
                int i = stack[--sp];
                if (stop.get(i)) continue;                               // a door, window or railing: seen, not gone through
                int x = i / (h * d), y = (i / d) % h, z = i % d;
                int[][] nb = {{x + 1, y, z}, {x - 1, y, z}, {x, y + 1, z}, {x, y - 1, z}, {x, y, z + 1}, {x, y, z - 1}};
                for (int[] n : nb) {
                    if (n[0] < 0 || n[1] < 0 || n[2] < 0 || n[0] >= w || n[1] >= h || n[2] >= d) continue;
                    int j = (n[0] * h + n[1]) * d + n[2];
                    if (seen.get(j) || solid.get(j)) continue;
                    seen.set(j);
                    stack[sp++] = j;
                }
            }
            return new Outside(x0, y0, z0, w, h, d, seen);
        }

        /** 填门洞、窗洞、栏杆的东西：看得见，但从它往里就是屋里了。 */
        private static boolean opening(BlockState s) {
            Block b = s.getBlock();
            return b instanceof DoorBlock || b instanceof IronBarsBlock || b instanceof AbstractGlassBlock || b instanceof FenceBlock
                    || b instanceof FenceGateBlock || b instanceof WallBlock;
        }

        boolean is(BlockPos p) {
            int x = p.getX() - x0, y = p.getY() - y0, z = p.getZ() - z0;
            if (x < 0 || y < 0 || z < 0 || x >= w || y >= h || z >= d) return true;
            return bits.get((x * h + y) * d + z);
        }

        boolean touches(BlockPos p) {
            for (Direction dir : Direction.values()) if (is(p.relative(dir))) return true;
            return false;
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (!shown || event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (event.getProjectionMatrix().m33() != 0f) return;            // shader shadow pass
        if (vbo == null) return;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(origin.getX() - cam.x, origin.getY() - cam.y, origin.getZ() - cam.z);
        RenderType rt = RenderType.translucent();
        rt.setupRenderState();
        vbo.bind();
        vbo.drawWithShader(ps.last().pose(), event.getProjectionMatrix(), GameRenderer.getRendertypeTranslucentShader());
        VertexBuffer.unbind();
        rt.clearRenderState();
        ps.popPose();
    }
}
