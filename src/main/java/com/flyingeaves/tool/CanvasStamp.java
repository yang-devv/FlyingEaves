package com.flyingeaves.tool;

import com.flyingeaves.FlyingEaves;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;

/**
 * 把生成器写的「列段」文件（格式见 {@link #read}）直接写进区块：一格一格 setBlock 太慢，这里直接写区块段，写完重算高度图、
 * 整区块重新打光，再把区块发给看得见它的玩家。每刻只做一部分区块（时间预算），不卡住服务器。
 * 建造工具放的每一栋都记下原来的方块，好撤销（{@link #undoLast}），也能存成原版结构文件（{@link #exportLast}）。
 */
@Mod.EventBusSubscriber(modid = FlyingEaves.MODID)
public final class CanvasStamp {
    /** (a safety net only: every ticket is taken off when its chunk is done) */
    private static final TicketType<ChunkPos> TICKET = TicketType.create("flyingeaves_stamp", Comparator.comparingLong(ChunkPos::toLong), 20 * 600);
    private static final long BUDGET_NANOS = 35_000_000L;
    private static final int MAX_PENDING = 256;
    private static final Deque<Task> TASKS = new ArrayDeque<>();

    private CanvasStamp() {
    }

    /** 一份「列段」：x0..x0+w-1、z0..z0+d-1 的每一列有若干段 [y0,y1]（含两端）→ 调色板里的方块。 */
    public record Runs(String name, int x0, int z0, int w, int d, BlockState[] palette, int[] start, short[] y0, short[] y1, short[] pal) {
    }

    /** What a task does: load chunks only (terrain), write a building (and record its undo), or put an undo record back. */
    private enum Mode { PREGEN, STAMP, RESTORE }

    // a written chunk's way out (STAMP and RESTORE): written -> lighting (once its neighbours are written and cleared)
    // -> lit -> gone (sent to the players, ticket off: once its neighbours are lit too, so no light from them is lost)
    private static final byte APPLIED = 1, LIGHTING = 2, LIT = 3, RELEASED = 4;

    private static final class Task {
        final Mode mode;
        final Runs runs;
        final String name;
        int loaded;
        final ServerLevel level;
        final Deque<ChunkPos> chunks = new ArrayDeque<>();
        /** 已经挂了票、还没好的区块（生成在工作线程上跑，每刻看一眼 getChunkNow，不在服务器线程上等）。 */
        final java.util.Set<ChunkPos> waiting = new java.util.LinkedHashSet<>();
        final int total;
        final long t0 = System.nanoTime();
        long editNanos;
        long blocks;
        /** STAMP: the cells written and what they held (filled as it goes); RESTORE: the record being put back. */
        CellLog<BlockState> undo;
        java.util.function.Consumer<Task> onDone;
        /** 预生成时每块好了调一次（服务器线程上，块还挂着票）：取地形用。不用时为 null。 */
        java.util.function.Consumer<LevelChunk> onChunk;

        // ---- streaming (STAMP, RESTORE): only a band of chunks is held at a time, not the whole building
        /** The chunks this task writes (ChunkPos.asLong). */
        final it.unimi.dsi.fastutil.longs.LongOpenHashSet inTask = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        /** Their state once written (APPLIED .. RELEASED). */
        final it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap state = new it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap();
        /** Written, not yet let go. */
        final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<LevelChunk> held = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
        /** Their light being cleared (light thread). */
        final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<CompletableFuture<?>> inits = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
        /** Loaded chunks around the task (not written): lit again so the light from outside comes in (LIGHTING, LIT, RELEASED = sent or not loaded). */
        final it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap ring = new it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap();
        /** Chunks whose lighting finished (filled on the light thread, read on the server thread). */
        final java.util.concurrent.ConcurrentLinkedQueue<Long> lit = new java.util.concurrent.ConcurrentLinkedQueue<>();
        int applied, released, ringOut, ringLit, mostHeld;
        long linked;
        boolean allApplied;

        /** 预生成：x0..x1、z0..z1 里的区块，从 (ox, oz) 往外一圈圈来。 */
        Task(String name, ServerLevel level, int x0, int z0, int x1, int z1, int ox, int oz) {
            this.mode = Mode.PREGEN;
            this.runs = null;
            this.name = name;
            this.level = level;
            java.util.List<ChunkPos> all = new java.util.ArrayList<>();
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
                for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
                    all.add(new ChunkPos(cx, cz));
                }
            }
            int ocx = ox >> 4, ocz = oz >> 4;
            all.sort(Comparator.comparingLong(c -> (long) (c.x - ocx) * (c.x - ocx) + (long) (c.z - ocz) * (c.z - ocz)));
            chunks.addAll(all);
            total = chunks.size();
        }

        /** Write a building: the chunks with runs, row by row (z, then x: the band held at a time stays narrow). */
        Task(Runs runs, ServerLevel level) {
            this.mode = Mode.STAMP;
            this.runs = runs;
            this.name = runs.name;
            this.level = level;
            int cx0 = runs.x0 >> 4, cz0 = runs.z0 >> 4, cx1 = (runs.x0 + runs.w - 1) >> 4, cz1 = (runs.z0 + runs.d - 1) >> 4;
            for (int cz = cz0; cz <= cz1; cz++) {
                for (int cx = cx0; cx <= cx1; cx++) {
                    if (hasRuns(runs, cx, cz)) chunks.add(new ChunkPos(cx, cz));   // skip chunks the file leaves alone
                }
            }
            for (ChunkPos p : chunks) inTask.add(p.toLong());
            total = chunks.size();
        }

        /** Put an undo record back, row by row. */
        Task(Undo rec) {
            this.mode = Mode.RESTORE;
            this.runs = null;
            this.name = "undo";
            this.level = rec.level();
            this.undo = rec.cells();
            long[] keys = rec.cells().chunkKeys();
            java.util.Arrays.sort(keys);                                  // (z is the high half: row by row)
            for (long k : keys) {
                chunks.add(new ChunkPos(k));
                inTask.add(k);
            }
            total = chunks.size();
        }
    }

    // ================================================================== 建造工具（BuildTool）用：写一栋、撤销

    /** One placement's undo: the level and, per cell it changed, what was there before. */
    private record Undo(ServerLevel level, CellLog<BlockState> cells) {
    }

    private static final Deque<Undo> UNDO = new ArrayDeque<>();

    /** 在服务器线程上调：把一栋建筑的列段文件写进世界，记下撤销用的原方块；写完调 done（参数是写了多少格）。 */
    static void stampForTool(ServerLevel level, Path file, java.util.function.LongConsumer done) throws Exception {
        stampForTool(level, read(file), done);
    }

    /** The same for runs already read (the in-game Java generator hands them over without a file). */
    static void stampForTool(ServerLevel level, Runs runs, java.util.function.LongConsumer done) {
        Task task = new Task(runs, level);
        task.undo = new CellLog<>(level.getMinBuildHeight());
        task.onDone = t -> {
            t.undo.sealAll();
            FlyingEaves.LOGGER.info(String.format(Locale.ROOT, "canvas: undo record of %s: %d cells in %d chunks, %.1f MB", t.name, t.undo.size(),
                    t.undo.chunkKeys().length, t.undo.bytes() / 1048576.0));
            UNDO.push(new Undo(t.level, t.undo));
            while (UNDO.size() > 20) UNDO.removeLast();
            done.accept(t.blocks);
        };
        TASKS.add(task);
    }

    /**
     * 在服务器线程上调：x0..x1、z0..z1 里的区块全要上（没生成的现生成，和 pregen 一样在工作线程上并行），
     * 每块好了调 each（服务器线程），全好了（或出错停下）调 done。随机城镇/宗门取地形用：客户端只有视距内的区块。
     */
    static void loadForTool(ServerLevel level, int x0, int z0, int x1, int z1, int ox, int oz,
                            java.util.function.Consumer<LevelChunk> each, Runnable done) {
        Task task = new Task(String.format(Locale.ROOT, "terrain %d,%d..%d,%d", x0, z0, x1, z1), level, x0, z0, x1, z1, ox, oz);
        task.onChunk = each;
        task.onDone = t -> done.run();
        TASKS.add(task);
    }

    /** 写进世界的队列里有几栋（排在前面的一栋没写完，后面的都等着）。 */
    static int queued() {
        return TASKS.size();
    }

    /**
     * 在服务器线程上调：撤销最近一次工具放的建筑。和写的时候一样一块块直接写区块段（按时间预算，不卡服务器），重新打光、发给玩家；
     * 恢复完调 done（参数是恢复了多少格）。没有可撤销的返回 false。
     */
    static boolean undoLast(java.util.function.LongConsumer done) {
        Undo rec = UNDO.poll();
        if (rec == null) return false;
        Task task = new Task(rec);
        task.onDone = t -> done.accept(t.blocks);
        TASKS.add(task);
        return true;
    }

    /**
     * 在服务器线程上调：工具放过的建筑（撤销记录里的，从新到旧）落在 x0..x1、z0..z1 里的部分恢复成放之前的样子，其余不动。
     * 返回恢复了多少格。
     */
    static long undoRegion(int x0, int z0, int x1, int z1) {
        long n = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (Undo e : UNDO) n += e.cells().removeIn(x0, z0, x1, z1, (x, y, z, old) -> e.level().setBlock(m.set(x, y, z), old, 2 | 16));
        return n;
    }

    /**
     * 在服务器线程上调：把最近一次工具放的建筑存成原版结构文件（结构方块能读的 .nbt）。只存这次写过的格子（包括清成空气的），
     * 别的格子不存，所以放回去时周围的地形不动。写到 flyingeaves/exports/<名字>.nbt，再拷一份到存档的
     * generated/flyingeaves/structures/，结构方块里填 flyingeaves:<名字> 就能载入。返回说明，没有可导出的返回 null。
     */
    public static net.minecraft.network.chat.Component exportLast(net.minecraft.server.MinecraftServer server, String name) throws Exception {
        Undo e = UNDO.peek();
        if (e == null) return null;
        ServerLevel level = e.level();
        int[] box = bounds(e.cells());
        int ax = box[0], ay = box[1], az = box[2], bx = box[3], by = box[4], bz = box[5];
        java.util.Map<BlockState, Integer> palette = new java.util.LinkedHashMap<>();
        net.minecraft.nbt.ListTag blocks = new net.minecraft.nbt.ListTag();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        e.cells().forEach((x, y, z, old) -> {
            BlockState s = level.getBlockState(m.set(x, y, z));
            int idx = palette.computeIfAbsent(s, k -> palette.size());
            net.minecraft.nbt.CompoundTag b = new net.minecraft.nbt.CompoundTag();
            net.minecraft.nbt.ListTag pos = new net.minecraft.nbt.ListTag();
            pos.add(net.minecraft.nbt.IntTag.valueOf(x - ax));
            pos.add(net.minecraft.nbt.IntTag.valueOf(y - ay));
            pos.add(net.minecraft.nbt.IntTag.valueOf(z - az));
            b.put("pos", pos);
            b.putInt("state", idx);
            blocks.add(b);
        });
        net.minecraft.nbt.ListTag pal = new net.minecraft.nbt.ListTag();
        for (BlockState s : palette.keySet()) pal.add(net.minecraft.nbt.NbtUtils.writeBlockState(s));
        net.minecraft.nbt.CompoundTag root = new net.minecraft.nbt.CompoundTag();
        net.minecraft.nbt.ListTag size = new net.minecraft.nbt.ListTag();
        size.add(net.minecraft.nbt.IntTag.valueOf(bx - ax + 1));
        size.add(net.minecraft.nbt.IntTag.valueOf(by - ay + 1));
        size.add(net.minecraft.nbt.IntTag.valueOf(bz - az + 1));
        root.put("size", size);
        root.put("palette", pal);
        root.put("blocks", blocks);
        root.put("entities", new net.minecraft.nbt.ListTag());
        root.putInt("DataVersion", net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        Path out = FlyingEaves.dataDir().resolve("exports").resolve(name + ".nbt");
        Files.createDirectories(out.getParent());
        try (var os = Files.newOutputStream(out)) {
            net.minecraft.nbt.NbtIo.writeCompressed(root, os);
        }
        Path gen = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.GENERATED_DIR).resolve(FlyingEaves.MODID).resolve("structures").resolve(name + ".nbt");
        Files.createDirectories(gen.getParent());
        Files.copy(out, gen, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return net.minecraft.network.chat.Component.translatable("flyingeaves.cmd.exported_info", blocks.size(), bx - ax + 1, by - ay + 1,
                bz - az + 1, palette.size(), String.valueOf(out.toAbsolutePath().normalize()), name);
    }

    /** 撤销记录的概况：每次放的格数和范围，从新到旧。 */
    static String undoInfo() {
        StringBuilder sb = new StringBuilder();
        for (Undo e : UNDO) {
            int[] b = bounds(e.cells());
            sb.append(String.format(Locale.ROOT, "[%s %d blocks x %d..%d z %d..%d, %.1f MB] ", e.level().dimension().location(), e.cells().size(),
                    b[0], b[3], b[2], b[5], e.cells().bytes() / 1048576.0));
        }
        return sb.toString();
    }

    /** {min x, y, z, max x, y, z} of a record's cells. */
    private static int[] bounds(CellLog<BlockState> cells) {
        int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        cells.forEach((x, y, z, old) -> {
            b[0] = Math.min(b[0], x);
            b[1] = Math.min(b[1], y);
            b[2] = Math.min(b[2], z);
            b[3] = Math.max(b[3], x);
            b[4] = Math.max(b[4], y);
            b[5] = Math.max(b[5], z);
        });
        return b;
    }

    /** 这个区块里有没有要写的列（文件的外接矩形里常有大片空的）。 */
    static boolean hasRuns(Runs r, int cx, int cz) {
        int bx = cx << 4, bz = cz << 4;
        for (int lz = 0; lz < 16; lz++) {
            int k = bz + lz - r.z0();
            if (k < 0 || k >= r.d()) continue;
            for (int lx = 0; lx < 16; lx++) {
                int i = bx + lx - r.x0();
                if (i < 0 || i >= r.w()) continue;
                int col = k * r.w() + i;
                if (r.start()[col + 1] > r.start()[col]) return true;
            }
        }
        return false;
    }

    // ================================================================== 文件

    /**
     * 列段文件（gzip，大端）：
     * "DVCR"，int 版本(1)，int x0 z0 w d，int 调色板数 + 每个（ushort 长度 + UTF-8 方块状态，比如 minecraft:stone_bricks、minecraft:water[level=0]），
     * int 段总数，byte[w*d] 每列段数（z 外层、x 内层），然后 short[段总数] y0、short[段总数] y1、short[段总数] 调色板序号（无符号）。
     * 段按列的顺序排；同一列里后面的段盖前面的。每列 0 段 = 这列不动。
     * 调色板里的 minecraft:structure_void 不写进世界：那一段只清掉天然地形（{@link #natural}），别的不动。
     */
    static Runs read(Path file) throws IOException, com.mojang.brigadier.exceptions.CommandSyntaxException {
        try (InputStream raw = Files.newInputStream(file);
             DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(raw, 1 << 16), 1 << 16))) {
            return read(in, file.getFileName().toString());
        }
    }

    /** The same from the uncompressed payload (what the in-game Java generator makes: no gzip, no file). */
    public static Runs parse(byte[] payload, String name) throws IOException, com.mojang.brigadier.exceptions.CommandSyntaxException {
        try (DataInputStream in = new DataInputStream(new java.io.ByteArrayInputStream(payload))) {
            return read(in, name);
        }
    }

    private static Runs read(DataInputStream in, String name) throws IOException, com.mojang.brigadier.exceptions.CommandSyntaxException {
        byte[] magic = new byte[4];
        in.readFully(magic);
        if (!new String(magic, StandardCharsets.US_ASCII).equals("DVCR")) throw new IOException("not a DVCR file: " + name);
        int version = in.readInt();
        if (version != 1) throw new IOException("DVCR version " + version);
        int x0 = in.readInt(), z0 = in.readInt(), w = in.readInt(), d = in.readInt();
        int np = in.readInt();
        BlockState[] palette = new BlockState[np];
        for (int i = 0; i < np; i++) {
            byte[] s = new byte[in.readUnsignedShort()];
            in.readFully(s);
            palette[i] = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), new String(s, StandardCharsets.UTF_8), false).blockState();
        }
        int n = in.readInt();
        byte[] counts = new byte[w * d];
        in.readFully(counts);
        int[] start = new int[w * d + 1];
        for (int i = 0; i < w * d; i++) start[i + 1] = start[i] + (counts[i] & 0xFF);
        if (start[w * d] != n) throw new IOException("run count mismatch: " + start[w * d] + " vs " + n);
        short[] y0 = shorts(in, n), y1 = shorts(in, n), pal = shorts(in, n);
        return new Runs(name, x0, z0, w, d, palette, start, y0, y1, pal);
    }

    private static short[] shorts(DataInputStream in, int n) throws IOException {
        byte[] b = new byte[n * 2];
        in.readFully(b);
        short[] out = new short[n];
        ByteBuffer.wrap(b).asShortBuffer().get(out);
        return out;
    }

    // ================================================================== 服务器线程：按预算写区块

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || TASKS.isEmpty()) return;
        long deadline = System.nanoTime() + BUDGET_NANOS;
        Task task = TASKS.peek();
        try {
            if (task.mode != Mode.PREGEN) {
                // chunks whose lighting finished: let go of those whose neighbours are lit too
                Long done;
                while (System.nanoTime() < deadline && (done = task.lit.poll()) != null) onLit(task, done);
            }
            // 先把后面要用的区块并行要上（生成在工作线程上跑），再处理已经好了的
            while (task.waiting.size() < (task.mode == Mode.PREGEN ? 2 * MAX_PENDING : MAX_PENDING) && !task.chunks.isEmpty()) {
                ChunkPos pos = task.chunks.poll();
                task.level.getChunkSource().addRegionTicket(TICKET, pos, 0, pos);   // loads/generates it off-thread
                task.waiting.add(pos);
            }
            var it = task.waiting.iterator();
            while (it.hasNext() && System.nanoTime() < deadline) {
                ChunkPos wp = it.next();
                LevelChunk ready = task.level.getChunkSource().getChunkNow(wp.x, wp.z);
                if (ready == null) continue;
                it.remove();
                if (task.mode == Mode.PREGEN) {
                    if (task.onChunk != null) task.onChunk.accept(ready);
                    task.level.getChunkSource().removeRegionTicket(TICKET, wp, 0, wp);
                    if (++task.loaded % 8192 == 0) {
                        FlyingEaves.LOGGER.info(String.format(Locale.ROOT, "canvas: %s: %d / %d chunks, %.0f s", task.name, task.loaded, task.total, (System.nanoTime() - task.t0) / 1e9));
                    }
                    continue;
                }
                long t = System.nanoTime();
                apply(task, ready);
                task.editNanos += System.nanoTime() - t;
                onApplied(task, ready);
            }
            if (task.chunks.isEmpty() && task.waiting.isEmpty() && task.mode == Mode.PREGEN) {
                TASKS.poll();
                FlyingEaves.LOGGER.info(String.format(Locale.ROOT, "canvas: lit %s: %d chunks generated or loaded, %.0f s", task.name, task.loaded, (System.nanoTime() - task.t0) / 1e9));
                if (task.onDone != null) task.onDone.accept(task);
                return;
            }
            if (task.chunks.isEmpty() && task.waiting.isEmpty() && !task.allApplied) {
                task.allApplied = true;
                FlyingEaves.LOGGER.info(String.format(Locale.ROOT, "canvas: done %s: %d chunks, %d blocks, edit %.1f s, total %.1f s (lighting continues; at most %d chunks held)",
                        task.name, task.total, task.blocks, task.editNanos / 1e9, (System.nanoTime() - task.t0) / 1e9, task.mostHeld));
            }
            if (task.allApplied && task.released == task.total && task.ringOut == 0) {
                TASKS.poll();
                FlyingEaves.LOGGER.info(String.format(Locale.ROOT, "canvas: lit %s: %d chunks + %d around, %d blocks (%d fences/walls/panes re-joined), %.1f s",
                        task.name, task.total, task.ringLit, task.blocks, task.linked, (System.nanoTime() - task.t0) / 1e9));
                if (task.onDone != null) task.onDone.accept(task);
            }
        } catch (Throwable e) {
            TASKS.poll();
            for (ChunkPos pos : task.waiting) task.level.getChunkSource().removeRegionTicket(TICKET, pos, 0, pos);
            for (long k : task.held.keySet()) {
                ChunkPos pos = new ChunkPos(k);
                task.level.getChunkSource().removeRegionTicket(TICKET, pos, 0, pos);
            }
            FlyingEaves.LOGGER.info("canvas: failed " + task.name + ": " + e);
            FlyingEaves.LOGGER.warn("canvas stamp failed", e);
            if (task.mode == Mode.PREGEN && task.onDone != null) task.onDone.accept(task);   // (取地形：没取到的列记成不知道)
        }
    }

    private static void apply(Task task, LevelChunk chunk) {
        long blocks;
        if (task.mode == Mode.RESTORE) {
            blocks = restore(task, chunk);
        } else {
            blocks = writeColumns(chunk, task.runs, task.level.getMinBuildHeight(), task.level.getMaxBuildHeight() - 1, task.undo);
            blocks += extendFoundation(task, chunk);
        }
        task.blocks += blocks;
        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.WORLD_SURFACE, Heightmap.Types.MOTION_BLOCKING,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Heightmap.Types.OCEAN_FLOOR));
        chunk.initializeLightSources();
        chunk.setUnsaved(true);
        clearLight(task, chunk);
    }

    /** Undo, one chunk: the recorded cells back as they were (straight into the sections, as the stamp wrote them). */
    private static long restore(Task task, LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        java.util.List<BlockPos> withEntity = new java.util.ArrayList<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        long[] n = {0};
        task.undo.forEach(pos.x, pos.z, (x, y, z, old) -> {
            LevelChunkSection sec = chunk.getSection(chunk.getSectionIndex(y));
            BlockState now = sec.setBlockState(x & 15, y & 15, z & 15, old, false);
            if (now != old) {
                if (now.hasBlockEntity()) chunk.removeBlockEntity(m.set(x, y, z));
                if (old.hasBlockEntity()) withEntity.add(new BlockPos(x, y, z));
            }
            n[0]++;
        });
        task.undo.drop(pos.x, pos.z);                       // (the record shrinks as the undo goes)
        addBlockEntities(chunk, withEntity);
        return n[0];
    }

    // ------------------------------------------------------------------ streaming: light a chunk once its neighbours are
    // written (their light cleared), let it go once its neighbours are lit (the light they send into it is in)

    private static void onApplied(Task task, LevelChunk chunk) {
        long k = chunk.getPos().toLong();
        task.held.put(k, chunk);
        task.state.put(k, APPLIED);
        task.applied++;
        task.mostHeld = Math.max(task.mostHeld, task.held.size());
        if (task.mode == Mode.STAMP) task.undo.seal(chunk.getPos().x, chunk.getPos().z);
        int cx = chunk.getPos().x, cz = chunk.getPos().z;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                long p = ChunkPos.asLong(cx + dx, cz + dz);
                if (task.inTask.contains(p)) tryLight(task, p);
                else tryLightRing(task, p);
            }
        }
    }

    /** Every chunk of the task next to p (and p itself) is at least at this state (chunks outside the task: ring at least). */
    private static boolean around(Task task, long p, byte inside, byte ring) {
        int cx = ChunkPos.getX(p), cz = ChunkPos.getZ(p);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                long q = ChunkPos.asLong(cx + dx, cz + dz);
                if (task.inTask.contains(q)) {
                    if (task.state.get(q) < inside) return false;
                } else if (ring > 0 && task.ring.get(q) < ring) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The light-clearing of p and its written neighbours (lighting waits for them). */
    private static CompletableFuture<?> inits(Task task, long p) {
        java.util.List<CompletableFuture<?>> fs = new java.util.ArrayList<>();
        int cx = ChunkPos.getX(p), cz = ChunkPos.getZ(p);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                CompletableFuture<?> f = task.inits.get(ChunkPos.asLong(cx + dx, cz + dz));
                if (f != null) fs.add(f);
            }
        }
        return CompletableFuture.allOf(fs.toArray(CompletableFuture[]::new));
    }

    private static void light(Task task, long p, ChunkPos pos, LevelChunk chunk) {
        ThreadedLevelLightEngine le = task.level.getChunkSource().getLightEngine();
        inits(task, p).thenCompose(v -> le.lightChunk(chunk, false)).whenComplete((c, e) -> {
            if (e != null) FlyingEaves.LOGGER.info("canvas: failed lighting " + task.name + " at " + pos + ": " + e);
            task.lit.add(p);
        });
    }

    private static void tryLight(Task task, long p) {
        if (task.state.get(p) != APPLIED || !around(task, p, APPLIED, (byte) 0)) return;
        task.state.put(p, LIGHTING);
        LevelChunk chunk = task.held.get(p);
        if (task.mode == Mode.STAMP) {                     // (its neighbours are all written: fences join, doors get steps)
            task.linked += connectShapes(task.level, linking(task.runs, chunk.getPos()));
            task.blocks += doorSteps(task, chunk.getPos());
        }
        light(task, p, chunk.getPos(), chunk);
    }

    private static void tryLightRing(Task task, long p) {
        if (task.ring.containsKey(p) || !around(task, p, APPLIED, (byte) 0)) return;
        LevelChunk chunk = task.level.getChunkSource().getChunkNow(ChunkPos.getX(p), ChunkPos.getZ(p));
        if (chunk == null) {                               // (not loaded: nothing to light or send)
            task.ring.put(p, RELEASED);
            int cx = ChunkPos.getX(p), cz = ChunkPos.getZ(p);
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    long q = ChunkPos.asLong(cx + dx, cz + dz);
                    if (task.inTask.contains(q)) tryRelease(task, q);
                }
            }
            return;
        }
        task.ring.put(p, LIGHTING);
        task.ringOut++;
        light(task, p, chunk.getPos(), chunk);
    }

    private static void onLit(Task task, long p) {
        if (task.inTask.contains(p)) task.state.put(p, LIT);
        else task.ring.put(p, LIT);
        int cx = ChunkPos.getX(p), cz = ChunkPos.getZ(p);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                long q = ChunkPos.asLong(cx + dx, cz + dz);
                if (task.inTask.contains(q)) tryRelease(task, q);
                else trySendRing(task, q);
            }
        }
    }

    private static void send(Task task, LevelChunk c) {
        ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(c, task.level.getChunkSource().getLightEngine(), null, null);
        for (ServerPlayer pl : task.level.getChunkSource().chunkMap.getPlayers(c.getPos(), false)) pl.connection.send(packet);
    }

    private static void tryRelease(Task task, long p) {
        if (task.state.get(p) != LIT || !around(task, p, LIT, LIT)) return;
        LevelChunk chunk = task.held.remove(p);
        send(task, chunk);
        task.level.getChunkSource().removeRegionTicket(TICKET, chunk.getPos(), 0, chunk.getPos());
        task.inits.remove(p);
        task.state.put(p, RELEASED);
        task.released++;
    }

    private static void trySendRing(Task task, long p) {
        if (task.ring.get(p) != LIT || !around(task, p, LIT, (byte) 0)) return;
        LevelChunk chunk = task.level.getChunkSource().getChunkNow(ChunkPos.getX(p), ChunkPos.getZ(p));
        if (chunk != null) send(task, chunk);
        task.ring.put(p, RELEASED);
        task.ringOut--;
        task.ringLit++;
    }

    private static boolean links(BlockState s) {
        return s.getBlock() instanceof net.minecraft.world.level.block.CrossCollisionBlock      // 栅栏、玻璃板、铁栏杆
                || s.getBlock() instanceof net.minecraft.world.level.block.WallBlock;
    }

    /** A chunk's columns of the runs: (column index, x, z), z then x. */
    @FunctionalInterface
    private interface ColumnFn {
        void col(int col, int x, int z);
    }

    private static void columns(Runs r, ChunkPos pos, ColumnFn f) {
        int bx = pos.getMinBlockX(), bz = pos.getMinBlockZ();
        for (int lz = 0; lz < 16; lz++) {
            int k = bz + lz - r.z0();
            if (k < 0 || k >= r.d()) continue;
            for (int lx = 0; lx < 16; lx++) {
                int i = bx + lx - r.x0();
                if (i < 0 || i >= r.w()) continue;
                f.col(k * r.w() + i, bx + lx, bz + lz);
            }
        }
    }

    /** 列段里这个区块的栅栏、墙、玻璃板、铁栏杆的位置。 */
    private static java.util.List<BlockPos> linking(Runs r, ChunkPos pos) {
        java.util.List<BlockPos> out = new java.util.ArrayList<>();
        columns(r, pos, (col, x, z) -> {
            for (int s = r.start()[col]; s < r.start()[col + 1]; s++) {
                if (!links(r.palette()[r.pal()[s] & 0xFFFF])) continue;
                for (int y = r.y0()[s]; y <= r.y1()[s]; y++) out.add(new BlockPos(x, y, z));
            }
        });
        return out;
    }

    /**
     * 栅栏、墙、玻璃板、铁栏杆按四周的方块重新连一遍，跟玩家放下时一样（原版的 updateShape）。列段是直接写进区块段的，不走方块更新，
     * 生成器给的连法常常不对（拐角、接墙的地方成了一根孤柱子，拆了重放才好）。只改这些方块自己，不惊动邻居。返回改了多少格。
     */
    public static long connectShapes(ServerLevel level, Iterable<BlockPos> where) {
        long n = 0;
        for (BlockPos p : where) {
            BlockState st = level.getBlockState(p);
            if (!links(st)) continue;
            BlockState now = net.minecraft.world.level.block.Block.updateFromNeighbourShapes(st, level, p);
            if (now != st) {
                level.setBlock(p, now, 2 | 16);
                n++;
            }
        }
        return n;
    }

    private static final java.util.Set<net.minecraft.world.level.block.Block> MASONRY = java.util.Set.of(
            Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.ANDESITE);
    private static final BlockState[] FOUNDATION = {Blocks.STONE_BRICKS.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState(),
            Blocks.STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), Blocks.CRACKED_STONE_BRICKS.defaultBlockState(),
            Blocks.ANDESITE.defaultBlockState()};

    /**
     * 建造工具：生成器在每列最底下垫了 3 格石台（石砖一类）；这里接着往下垫到实地为止（空气、水、草、花、树叶都算空），
     * 最多再垫 64 格。平地上一格也不多垫，坡上、水上垫到底。原来的方块记进撤销。返回垫了多少格。
     */
    private static long extendFoundation(Task task, LevelChunk chunk) {
        Runs r = task.runs;
        ChunkPos pos = chunk.getPos();
        int bx = pos.getMinBlockX(), bz = pos.getMinBlockZ();
        int minY = task.level.getMinBuildHeight();
        long n = 0;
        for (int lz = 0; lz < 16; lz++) {
            int k = bz + lz - r.z0();
            if (k < 0 || k >= r.d()) continue;
            for (int lx = 0; lx < 16; lx++) {
                int i = bx + lx - r.x0();
                if (i < 0 || i >= r.w()) continue;
                int col = k * r.w() + i;
                int low = Integer.MAX_VALUE;
                BlockState lowState = null;
                for (int s = r.start()[col]; s < r.start()[col + 1]; s++) {
                    if (r.y0()[s] < low) {
                        low = r.y0()[s];
                        lowState = r.palette()[r.pal()[s] & 0xFFFF];
                    }
                }
                // the stone under the house (any full block at the bottom of a column: the default stone, the room's own
                // base material, soil under plants) goes on down; the default stone as its mix, anything else as itself
                if (lowState == null || lowState.isAir() || lowState.is(Blocks.STRUCTURE_VOID)
                        || !lowState.isCollisionShapeFullBlock(task.level, BlockPos.ZERO)) continue;
                boolean mix = MASONRY.contains(lowState.getBlock());
                for (int y = low - 1; y >= Math.max(minY, low - 64); y--) {
                    LevelChunkSection sec = chunk.getSection(chunk.getSectionIndex(y));
                    BlockState old = sec.getBlockState(lx, y & 15, lz);
                    boolean soft = old.isAir() || !old.getFluidState().isEmpty() || old.canBeReplaced()
                            || old.is(net.minecraft.tags.BlockTags.LEAVES);
                    if (!soft) break;
                    int h = ((bx + lx) * 73856093) ^ (y * 19349663) ^ ((bz + lz) * 83492791);
                    sec.setBlockState(lx, y & 15, lz, mix ? FOUNDATION[Math.floorMod(h, FOUNDATION.length)] : lowState, false);
                    task.undo.put(bx + lx, y, bz + lz, old);      // (the chunk is sealed after: an earlier record of the cell wins)
                    if (old.hasBlockEntity()) chunk.removeBlockEntity(new BlockPos(bx + lx, y, bz + lz));
                    n++;
                }
            }
        }
        return n;
    }

    /** The stairs that go with a block (smooth_quartz → smooth_quartz_stairs, deepslate_bricks → deepslate_brick_stairs …), else stone brick stairs. */
    private static BlockState stairsFor(BlockState block) {
        var key = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block.getBlock());
        String n = key.getPath();
        String[] cands = {n.equals("packed_mud") ? "mud_brick_stairs" : null, n.equals("red_terracotta") ? "brick_stairs" : null,
                n + "_stairs", n.replace("_bricks", "_brick") + "_stairs", n.replace("_tiles", "_tile") + "_stairs",
                n.replace("smooth_", "") + "_stairs", n.replace("polished_", "") + "_stairs", n.endsWith("s") ? n.substring(0, n.length() - 1) + "_stairs" : null};
        for (String c : cands) {
            if (c == null) continue;
            var rl = new net.minecraft.resources.ResourceLocation(key.getNamespace(), c);
            if (net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(rl)
                    && net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(rl) instanceof net.minecraft.world.level.block.StairBlock sb) {
                return sb.defaultBlockState();
            }
        }
        return Blocks.STONE_BRICK_STAIRS.defaultBlockState();
    }

    private static boolean soft(BlockState s) {
        return s.isAir() || !s.getFluidState().isEmpty() || s.canBeReplaced() || s.is(net.minecraft.tags.BlockTags.LEAVES);
    }

    /**
     * 建造工具：房子坐在坡的高处时，低的那边一楼的门外面是悬空的（石台只垫在房子底下）。每扇一楼的门（门下面的地板底下还是实的），
     * 门外地板那一层是空的，就从门口往外一级一级放石砖台阶，每级底下垫到地面，直到碰到地面为止（最多 16 级）。
     * 碰到的是这栋自己的石台就接着往下走；碰到墙、柱子这些、或者头顶不空就停。原来的方块记进撤销。返回放了多少格。
     */
    private static long doorSteps(Task task, ChunkPos at) {
        Runs r = task.runs;
        ServerLevel lv = task.level;
        long n = 0;
        java.util.List<BlockPos> doors = new java.util.ArrayList<>();
        columns(r, at, (col, x, z) -> {                    // (this chunk's doors: done once its neighbours are written)
            for (int s = r.start()[col]; s < r.start()[col + 1]; s++) {
                BlockState st = r.palette()[r.pal()[s] & 0xFFFF];
                if (st.getBlock() instanceof net.minecraft.world.level.block.DoorBlock
                        && st.getValue(net.minecraft.world.level.block.DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) {
                    for (int y = r.y0()[s]; y <= r.y1()[s]; y++) doors.add(new BlockPos(x, y, z));
                }
            }
        });
        for (BlockPos p : doors) {
            BlockState under = lv.getBlockState(p.below(2));      // what the floor stands on: the steps are of that
            BlockState stairs = stairsFor(under);
            BlockState fill = under.isCollisionShapeFullBlock(lv, p.below(2)) && !under.hasBlockEntity() ? under : null;
            BlockState door = lv.getBlockState(p);
            if (!(door.getBlock() instanceof net.minecraft.world.level.block.DoorBlock)) continue;
            if (soft(lv.getBlockState(p.below())) || soft(lv.getBlockState(p.below(2)))) continue;   // an upper floor's door
            net.minecraft.core.Direction f = door.getValue(net.minecraft.world.level.block.DoorBlock.FACING);
            for (net.minecraft.core.Direction dir : new net.minecraft.core.Direction[]{f, f.getOpposite()}) {
                BlockPos out = p.relative(dir);
                if (!soft(lv.getBlockState(out.below())) || soft(lv.getBlockState(p.relative(dir.getOpposite()).below()))) continue;
                if (!soft(lv.getBlockState(out)) && !lv.getBlockState(out).isAir()) continue;
                for (int k = 1; k <= 16; k++) {
                    BlockPos sp = p.relative(dir, k).below(k);
                    BlockState old = lv.getBlockState(sp);
                    boolean ours = task.undo.contains(sp.getX(), sp.getY(), sp.getZ());
                    if (!soft(old) && !(ours && old.isCollisionShapeFullBlock(lv, sp) && sp.getY() < p.getY() - 1)) break;   // the ground (the house's own stone goes)
                    if (!soft(lv.getBlockState(sp.above())) || !soft(lv.getBlockState(sp.above(2)))) break;
                    task.undo.putIfAbsent(sp.getX(), sp.getY(), sp.getZ(), old);
                    lv.setBlock(sp, stairs.setValue(net.minecraft.world.level.block.StairBlock.FACING, dir.getOpposite()), 2);
                    n++;
                    for (int y = sp.getY() - 1; y >= Math.max(lv.getMinBuildHeight(), sp.getY() - 64); y--) {
                        BlockPos b = new BlockPos(sp.getX(), y, sp.getZ());
                        BlockState o = lv.getBlockState(b);
                        if (!soft(o)) break;
                        task.undo.putIfAbsent(b.getX(), y, b.getZ(), o);
                        int h = (b.getX() * 73856093) ^ (y * 19349663) ^ (b.getZ() * 83492791);
                        lv.setBlock(b, fill != null && !MASONRY.contains(fill.getBlock()) ? fill : FOUNDATION[Math.floorMod(h, FOUNDATION.length)], 2);
                        n++;
                    }
                }
                break;
            }
        }
        return n;
    }

    /** 把列段写进一个区块（已生成的或正在生成的都行），直接写区块段；返回写了多少格。 */
    static long writeColumns(net.minecraft.world.level.chunk.ChunkAccess chunk, Runs r, int minY, int maxY) {
        return writeColumns(chunk, r, minY, maxY, null);
    }

    /** 同上，undo 不是 null 时把改掉的格子原来的方块记进去（建造工具撤销用）。 */
    public static long writeColumns(net.minecraft.world.level.chunk.ChunkAccess chunk, Runs r, int minY, int maxY, CellLog<BlockState> undo) {
        ChunkPos pos = chunk.getPos();
        int bx = pos.getMinBlockX(), bz = pos.getMinBlockZ();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        java.util.List<BlockPos> withEntity = new java.util.ArrayList<>();
        long blocks = 0;
        for (int lz = 0; lz < 16; lz++) {
            int k = bz + lz - r.z0();
            if (k < 0 || k >= r.d()) continue;
            for (int lx = 0; lx < 16; lx++) {
                int i = bx + lx - r.x0();
                if (i < 0 || i >= r.w()) continue;
                int col = k * r.w() + i;
                for (int s = r.start()[col]; s < r.start()[col + 1]; s++) {
                    BlockState state = r.palette()[r.pal()[s] & 0xFFFF];
                    int ya = Math.max(r.y0()[s], minY), yb = Math.min(r.y1()[s], maxY);
                    if (state.is(Blocks.STRUCTURE_VOID)) {
                        // a run from the bottom of the world has no real ground under it (the daemon fell back to a
                        // 城镇 地块's own y): clearing it would dig the column out to bedrock - tens of millions of cells
                        if (ya <= minY + 1) continue;
                        blocks += clearNatural(chunk, lx, lz, bx + lx, bz + lz, ya, yb, maxY, undo);
                        continue;
                    }
                    int y = ya;
                    while (y <= yb) {
                        LevelChunkSection sec = chunk.getSection(chunk.getSectionIndex(y));
                        int end = Math.min(yb, (SectionPos.blockToSectionCoord(y) << 4) + 15);
                        if (state.isAir() && sec.hasOnlyAir()) {
                            y = end + 1;
                            continue;
                        }
                        for (; y <= end; y++) {
                            BlockState old = sec.setBlockState(lx, y & 15, lz, state, false);
                            if (undo != null && old != state) undo.put(bx + lx, y, bz + lz, old);
                            if (old.hasBlockEntity() && old != state) chunk.removeBlockEntity(m.set(bx + lx, y, bz + lz));
                            if (state.hasBlockEntity()) withEntity.add(new BlockPos(bx + lx, y, bz + lz));
                            blocks++;
                        }
                    }
                }
            }
        }
        addBlockEntities(chunk, withEntity);
        return blocks;
    }

    /** 方块实体（信标、旗帜、营火、告示牌……）直接写区块段时不会自动建，这里补上：已生成的区块登记进世界（会走 tick），生成中的区块先挂着。 */
    private static void addBlockEntities(net.minecraft.world.level.chunk.ChunkAccess chunk, java.util.List<BlockPos> withEntity) {
        for (BlockPos bp : withEntity) {
            BlockState state = chunk.getBlockState(bp);
            if (!(state.getBlock() instanceof net.minecraft.world.level.block.EntityBlock eb)) continue;
            net.minecraft.world.level.block.entity.BlockEntity be = eb.newBlockEntity(bp, state);
            if (be == null) continue;
            if (chunk instanceof LevelChunk lc) {
                lc.addAndRegisterBlockEntity(be);
            } else {
                chunk.setBlockEntity(be);
            }
        }
    }

    private static final java.util.Set<net.minecraft.world.level.block.Block> NATURAL = java.util.Set.of(
            Blocks.GRAVEL, Blocks.CLAY, Blocks.CALCITE, Blocks.SANDSTONE, Blocks.RED_SANDSTONE, Blocks.GRASS, Blocks.TALL_GRASS,
            Blocks.FERN, Blocks.LARGE_FERN, Blocks.DEAD_BUSH, Blocks.SWEET_BERRY_BUSH, Blocks.BROWN_MUSHROOM, Blocks.RED_MUSHROOM,
            Blocks.SUGAR_CANE, Blocks.CACTUS, Blocks.VINE, Blocks.GLOW_LICHEN, Blocks.MOSS_CARPET, Blocks.BAMBOO, Blocks.PUMPKIN,
            Blocks.MELON, Blocks.ICE, Blocks.PACKED_ICE);

    /** 天然地形：土、草方块、石头一类、沙、沙砾、黏土、雪、矿石、原木、树叶、花草、树苗……；水、有方块实体的、人造的方块都不算。 */
    static boolean natural(BlockState s) {
        if (s.isAir() || !s.getFluidState().isEmpty() || s.hasBlockEntity()) return false;
        return s.is(net.minecraft.tags.BlockTags.DIRT) || s.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)
                || s.is(net.minecraft.tags.BlockTags.SAND) || s.is(net.minecraft.tags.BlockTags.SNOW)
                || s.is(net.minecraft.tags.BlockTags.LOGS) || s.is(net.minecraft.tags.BlockTags.LEAVES)
                || s.is(net.minecraft.tags.BlockTags.FLOWERS) || s.is(net.minecraft.tags.BlockTags.SAPLINGS)
                || s.is(net.minecraftforge.common.Tags.Blocks.ORES) || NATURAL.contains(s.getBlock());
    }

    /**
     * 一段 structure_void（生成器给建筑外接矩形里它没占的列：院子、路、门前）：ya..yb 里的天然地形清成空气，别的不动；
     * 到 yb 还是天然的就接着往上清到不是为止，比建筑高的山包、树不留下悬空的一截。原来的方块记进撤销。返回清了多少格。
     */
    private static long clearNatural(net.minecraft.world.level.chunk.ChunkAccess chunk, int lx, int lz, int x, int z, int ya, int yb, int maxY,
                                     CellLog<BlockState> undo) {
        BlockState air = Blocks.AIR.defaultBlockState();
        long n = 0;
        int y = ya;
        while (y <= maxY) {
            LevelChunkSection sec = chunk.getSection(chunk.getSectionIndex(y));
            if (sec.hasOnlyAir()) {
                if (y > yb) break;
                y = (SectionPos.blockToSectionCoord(y) << 4) + 16;
                continue;
            }
            BlockState old = sec.getBlockState(lx, y & 15, lz);
            if (natural(old)) {
                sec.setBlockState(lx, y & 15, lz, air, false);
                if (undo != null) undo.put(x, y, z, old);
                n++;
            } else if (y > yb) {
                break;
            }
            y++;
        }
        return n;
    }

    /**
     * 清光：给这区块每个要存光的段排一份全 0 的光照数据（排队的数据会顶掉现有的，新建的段也先用它），再标出非空的段。
     * 只给本来有光照数据、或上下相邻有方块的段排，免得留下用不上的 0 数据，以后别处建到那么高时变黑。
     */
    private static void clearLight(Task task, LevelChunk chunk) {
        ThreadedLevelLightEngine le = task.level.getChunkSource().getLightEngine();
        ChunkPos pos = chunk.getPos();
        int minSec = task.level.getMinSection();
        for (int s = le.getMinLightSection(); s < le.getMaxLightSection(); s++) {
            SectionPos sp = SectionPos.of(pos, s);
            boolean near = false;
            for (int t = s - 1; t <= s + 1 && !near; t++) {
                int i = t - minSec;
                near = i >= 0 && i < chunk.getSectionsCount() && !chunk.getSection(i).hasOnlyAir();
            }
            for (LightLayer layer : LightLayer.values()) {
                if (near || le.getLayerListener(layer).getDataLayerData(sp) != null) le.queueSectionData(layer, sp, new DataLayer());
            }
        }
        task.inits.put(pos.toLong(), le.initializeLight(chunk, false));
    }
}
