package com.flyingeaves.tool;

import com.flyingeaves.FlyingEaves;
import com.flyingeaves.backend.GeneratorProcess;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Development only (-Dflyingeaves.dev=true, gradlew runClient -Pdev=true): commands appended to
 * &lt;game dir&gt;/flyingeaves-commands.txt run on the client thread, one a tick (the file is emptied when read);
 * what happened goes to &lt;game dir&gt;/flyingeaves-dev.log (and the messages the tool shows the player too).
 * <ul>
 *   <li>{@code status} - the generator, the tool's state</li>
 *   <li>{@code place <kind> <tier> <seed>} - one building of the O menu at the crosshair (or under the player)</li>
 *   <li>{@code random} - 随机一栋 (as the random-preview key); {@code generate [tier]} - the room table's 生成</li>
 *   <li>{@code site court|city|city_real|town|sect} - 随机一院 and the others; {@code town <size|any> <w> <d>} - 随机城镇
 *   (a size, a box of w x d; 0 0: no box); {@code sect <tier>} - 随机宗门 of a tier</li>
 *   <li>{@code room <w> <d>} - start 单体建造 with one w x d room at the crosshair (its preview follows);
 *   {@code preset <folder/name>} - a design of the preset library (data/library, without .json) at the crosshair</li>
 *   <li>{@code mem [reset|gc]} - heap now / peak since the last {@code mem reset} / max, and the heap left after
 *   collections (now and its peak: what is really kept); {@code mem gc} collects first (the live heap)</li>
 *   <li>{@code hash [rooms|x0 z0 x1 z1]} - a hash of every block in a box (all heights; chunks loaded or generated as
 *   needed), per chunk; the next {@code hash} of the same box says which chunks differ (undo restores exactly?).
 *   {@code rooms}: the rooms of 单体建造 and 48 around; no box: the last one</li>
 *   <li>{@code save} - save every level now, flushed (what quitting does; single player has no save-all)</li>
 *   <li>{@code undo}; {@code cmd <command without />} - a chat command; {@code newworld <name> [flat]} - a fresh creative
 *   world (from the title screen); {@code quit} - close the game</li>
 * </ul>
 */
public final class DevPipe {
    private static final Path PIPE = FMLPaths.GAMEDIR.get().resolve("flyingeaves-commands.txt");
    private static final Path LOG = FMLPaths.GAMEDIR.get().resolve("flyingeaves-dev.log");
    private static final Deque<String> JOBS = new ArrayDeque<>();
    /** The most heap in use seen at a tick (bytes). */
    private static long peak;
    /** The most heap left after collections seen at a tick since {@code mem reset}. */
    private static long afterGcPeak;

    private DevPipe() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) tick();
        });
        FlyingEaves.LOGGER.info("dev pipe: {}", PIPE);
    }

    static void log(String text) {
        FlyingEaves.LOGGER.info("dev: {}", text);
        try {
            Files.writeString(LOG, System.currentTimeMillis() + " " + text + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }

    private static long used() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    /** Heap in use right after the latest collection of each heap pool (eden: 0): what the collector could not free. */
    private static long afterGc() {
        long n = 0;
        for (var pool : java.lang.management.ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() != java.lang.management.MemoryType.HEAP) continue;
            var u = pool.getCollectionUsage();
            if (u != null) n += u.getUsed();
        }
        return n;
    }

    private static void tick() {
        peak = Math.max(peak, used());
        afterGcPeak = Math.max(afterGcPeak, afterGc());
        try {
            if (Files.exists(PIPE) && Files.size(PIPE) > 0) {
                for (String line : Files.readAllLines(PIPE, StandardCharsets.UTF_8)) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) JOBS.add(line);
                }
                Files.writeString(PIPE, "", StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return;                                                 // (being written: next tick)
        }
        String job = JOBS.poll();
        if (job == null) return;
        Minecraft mc = Minecraft.getInstance();
        String[] a = job.split("\s+");
        try {
            switch (a[0]) {
                case "newworld" -> {                             // newworld <name> [flat]: a fresh creative world, joined
                    String name = a.length > 1 ? a[1] : "flyingeaves_test";
                    boolean flat = a.length > 2 && a[2].equals("flat");
                    var settings = new net.minecraft.world.level.LevelSettings(name, net.minecraft.world.level.GameType.CREATIVE, false,
                            net.minecraft.world.Difficulty.PEACEFUL, true, new net.minecraft.world.level.GameRules(),
                            net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
                    var options = new net.minecraft.world.level.levelgen.WorldOptions(20261119L, false, false);
                    mc.createWorldOpenFlows().createFreshLevel(name, settings, options, reg -> reg
                            .registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                            .getHolderOrThrow(flat ? net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT
                                    : net.minecraft.world.level.levelgen.presets.WorldPresets.NORMAL)
                            .value().createWorldDimensions());
                    log("newworld " + name + (flat ? " flat" : ""));
                }
                case "quit" -> {
                    log("quit");
                    mc.stop();
                }
                case "mem" -> {
                    if (a.length > 1 && a[1].equals("gc")) System.gc();
                    if (a.length > 1 && a[1].equals("reset")) {
                        peak = used();
                        afterGcPeak = afterGc();
                    }
                    log(String.format("mem: used %d MB, peak %d MB, after GC %d MB (peak %d MB), committed %d MB, max %d MB%s", used() >> 20, peak >> 20,
                            afterGc() >> 20, afterGcPeak >> 20, Runtime.getRuntime().totalMemory() >> 20, Runtime.getRuntime().maxMemory() >> 20,
                            a.length > 1 && a[1].equals("gc") ? " (after System.gc)" : ""));
                }
                case "hash" -> hash(mc, a);
                case "save" -> {
                    var server = mc.getSingleplayerServer();
                    if (server == null) {
                        log("save: no server");
                        return;
                    }
                    long t0 = System.nanoTime();
                    server.execute(() -> {
                        boolean ok = server.saveEverything(false, true, true);
                        String msg = String.format("save: %s, %.1f s", ok ? "ok" : "FAILED", (System.nanoTime() - t0) / 1e9);
                        mc.execute(() -> log(msg));
                    });
                }
                case "room" -> {
                    BlockPos at = BuildTool.target(mc);
                    if (at == null) at = mc.player.blockPosition().below();
                    int w = Integer.parseInt(a[1]), d = Integer.parseInt(a[2]);
                    Compose.start();
                    String err = Compose.addRoom(at.above(), at.above().offset(w - 1, 0, d - 1), BuildTool.facingToPlayer(mc));
                    log("room " + w + "x" + d + " at " + at.above().toShortString() + ": " + (err == null ? Compose.describe() : err));
                }
                case "preset" -> {
                    BlockPos at = BuildTool.target(mc);
                    if (at == null) at = mc.player.blockPosition().below();
                    String name = job.substring(7).trim();
                    String err = Compose.loadDesign(Compose.LIBRARY.resolve(name + ".json"), at);
                    log("preset " + name + ": " + (err == null ? Compose.ROOMS.size() + " rooms, " + Compose.describe() : err));
                }
                case "town" -> {
                    String size = a.length > 1 && !a[1].equals("any") ? a[1] : "";
                    int w = a.length > 3 ? Integer.parseInt(a[2]) : 0, d = a.length > 3 ? Integer.parseInt(a[3]) : 0;
                    BuildTool.requestTown(size, w, d);
                    log("town " + (size.isEmpty() ? "any" : size) + " " + w + "x" + d);
                }
                case "sect" -> {
                    BuildTool.requestSect(a.length > 1 ? a[1] : "lianqi");
                    log("sect " + (a.length > 1 ? a[1] : "lianqi"));
                }
                case "status" -> log("status: backend " + (com.flyingeaves.backend.JavaBackend.enabled() ? "java" : "python") + " (java queue "
                        + com.flyingeaves.backend.JavaBackend.pending() + "), python " + (com.flyingeaves.Build.PYTHON ? "in this build" : "not in this build") + ", python generator " + (GeneratorProcess.running() ? "running" : "not running") + " (" + GeneratorProcess.hint()
                        + "), player " + (mc.player == null ? "none" : mc.player.blockPosition().toShortString()) + ", compose " + Compose.describe()
                        + ", stamp queue " + CanvasStamp.queued());
                case "cmd" -> {
                    if (mc.player != null) mc.player.connection.sendCommand(job.substring(4).trim());
                    log("cmd " + job.substring(4).trim());
                }
                case "place" -> {
                    if (mc.player == null) {
                        log("place: no player");
                        return;
                    }
                    BlockPos at = BuildTool.target(mc);
                    if (at == null) at = mc.player.blockPosition().below();
                    BuildTool.place(a[1], a[2], Integer.parseInt(a[3]), at, BuildTool.facingToPlayer(mc));
                    log("place " + a[1] + " " + a[2] + " " + a[3] + " at " + at.toShortString());
                }
                case "random" -> {
                    String err = Compose.randomBuilding();
                    log("random: " + (err == null ? Compose.describe() : err));
                }
                case "generate" -> {
                    Compose.generate(ComposeScreen.load(), a.length > 1 ? a[1] : "mortal", 7);
                    log("generate sent: rooms " + Compose.ROOMS.size());
                }
                case "site" -> {
                    BuildTool.requestSite(a.length > 1 ? a[1] : "court");
                    log("site " + (a.length > 1 ? a[1] : "court"));
                }
                case "undo" -> {
                    BuildTool.undo();
                    log("undo");
                }
                default -> log("unknown: " + job);
            }
        } catch (Throwable t) {
            log("failed: " + job + ": " + t);
        }
    }

    /** The box of the last {@code hash} and its per-chunk hashes. */
    private static int[] hashBox;
    private static it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap lastHash;

    /** hash [rooms | x0 z0 x1 z1]: every non-air block of the box (all heights), per chunk, compared with the last. */
    private static void hash(Minecraft mc, String[] a) {
        int[] box;
        if (a.length > 1 && a[1].equals("rooms")) {
            if (Compose.ROOMS.isEmpty()) {
                log("hash: no rooms");
                return;
            }
            box = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
            for (Compose.Room r : Compose.ROOMS) {
                box[0] = Math.min(box[0], r.x0 - 48);
                box[1] = Math.min(box[1], r.z0 - 48);
                box[2] = Math.max(box[2], r.x1 + 48);
                box[3] = Math.max(box[3], r.z1 + 48);
            }
        } else if (a.length > 4) {
            box = new int[]{Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3]), Integer.parseInt(a[4])};
        } else if (hashBox != null) {
            box = hashBox;
        } else {
            log("hash: no box");
            return;
        }
        var server = mc.getSingleplayerServer();
        if (server == null || mc.level == null) {
            log("hash: no server");
            return;
        }
        var dim = mc.level.dimension();
        boolean same = java.util.Arrays.equals(box, hashBox);
        var before = same ? lastHash : null;
        hashBox = box;
        var now = new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();
        long[] cells = {0};
        long t0 = System.nanoTime();
        log(String.format("hash: box x %d..%d z %d..%d ...", box[0], box[2], box[1], box[3]));
        server.execute(() -> {
            var level = server.getLevel(dim);
            CanvasStamp.loadForTool(level, box[0], box[1], box[2], box[3], (box[0] + box[2]) / 2, (box[1] + box[3]) / 2, chunk -> {
                int bx = chunk.getPos().getMinBlockX(), bz = chunk.getPos().getMinBlockZ();
                for (int si = 0; si < chunk.getSectionsCount(); si++) {
                    var sec = chunk.getSection(si);
                    if (sec.hasOnlyAir()) continue;
                    int by = chunk.getMinBuildHeight() + (si << 4);
                    long h = 0;
                    for (int ly = 0; ly < 16; ly++) {
                        for (int lz = 0; lz < 16; lz++) {
                            int z = bz + lz;
                            if (z < box[1] || z > box[3]) continue;
                            for (int lx = 0; lx < 16; lx++) {
                                int x = bx + lx;
                                if (x < box[0] || x > box[2]) continue;
                                var st = sec.getBlockState(lx, ly, lz);
                                if (st.isAir()) continue;
                                long k = BlockPos.asLong(x, by + ly, z) * 0x9E3779B97F4A7C15L + net.minecraft.world.level.block.Block.getId(st);
                                h += it.unimi.dsi.fastutil.HashCommon.mix(k);
                                cells[0]++;
                            }
                        }
                    }
                    if (h != 0) now.put(net.minecraft.core.SectionPos.asLong(chunk.getPos().x, by >> 4, chunk.getPos().z), h);
                }
            }, () -> {
                long all = 0;
                long[] keys0 = now.keySet().toLongArray();
                java.util.Arrays.sort(keys0);
                for (long k : keys0) all = all * 31 + now.get(k);
                // what differs: sections, the chunks they are in, their heights, how far from the player (ticking?)
                StringBuilder diff = new StringBuilder();
                var chunks = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
                int n = 0, ylo = Integer.MAX_VALUE, yhi = Integer.MIN_VALUE, far = 0;
                var players = server.getPlayerList().getPlayers();
                var pc = players.isEmpty() ? null : players.get(0).chunkPosition();
                if (before != null) {
                    var keys = new it.unimi.dsi.fastutil.longs.LongOpenHashSet(before.keySet());
                    keys.addAll(now.keySet());
                    for (long k : keys) {
                        if (before.get(k) == now.get(k)) continue;
                        n++;
                        int cx = net.minecraft.core.SectionPos.x(k), sy = net.minecraft.core.SectionPos.y(k), cz = net.minecraft.core.SectionPos.z(k);
                        ylo = Math.min(ylo, sy << 4);
                        yhi = Math.max(yhi, (sy << 4) + 15);
                        if (pc != null) far = Math.max(far, Math.max(Math.abs(cx - pc.x), Math.abs(cz - pc.z)));
                        if (chunks.add(net.minecraft.world.level.ChunkPos.asLong(cx, cz)) && chunks.size() <= 12) diff.append(" [").append(cx).append(", ").append(cz).append(']');
                    }
                }
                lastHash = now;
                String what = n == 0 ? " - same as before"
                        : String.format(" - %d sections in %d chunks differ (y %d..%d, at most %d chunks from the player at %s):%s", n, chunks.size(), ylo, yhi, far, pc, diff);
                String msg = String.format("hash: %d sections, %d blocks, %016x, %.1f s%s", now.size(), cells[0], all, (System.nanoTime() - t0) / 1e9,
                        before == null ? " (first of this box)" : what);
                mc.execute(() -> log(msg));
            });
        });
    }
}
