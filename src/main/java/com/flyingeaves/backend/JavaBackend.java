package com.flyingeaves.backend;

import com.flyingeaves.Build;
import com.flyingeaves.Config;
import com.flyingeaves.FlyingEaves;
import com.flyingeaves.gen.Compose;
import com.flyingeaves.gen.Env;
import com.flyingeaves.gen.Generator;
import com.flyingeaves.gen.Json;
import com.flyingeaves.gen.Planners;
import com.flyingeaves.gen.Platforms;
import com.flyingeaves.gen.RunFiles;
import com.flyingeaves.gen.py.PyRandom;
import com.flyingeaves.tool.CanvasStamp;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.fml.ModList;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The generator in the game's own process (the Java port, {@link Generator}): what the Python daemon does for a request,
 * on one worker thread (the generator's state is static: one request at a time, in the daemon's order - placements
 * first, then only the newest preview; a preview stops the moment a newer one is asked for).
 *
 * <p>The result is the daemon's result dict (as gson) and the column runs already read ({@link CanvasStamp.Runs}): no
 * .dvcr file, no polling of out/. Progress is kept on the {@link Job} (the same stages the daemon writes into
 * &lt;id&gt;.progress.json). The data (materials, parts, presets) come from the jar's flyingeaves/generator/data (in dev
 * the project's generator/data), the player's own from &lt;game dir&gt;/flyingeaves as for the daemon; edited material or
 * part files and the game's block list are picked up before the next request (the daemon's reload).
 *
 * <p>Everything the daemon answers is ported (compose, the build menu's kinds, the planners behind 随机一院 / 宫城 /
 * 紫禁城 / 城镇 / 宗门 / 一句话). In a release build ({@link Build#PYTHON} false) that is all there is: a request too big
 * for the free heap fails with a message (how much memory it wants). Only a dev build (-PwithPython=true) hands what
 * Java cannot do ({@link Platforms.NotPorted}, too big for the heap) to the Python daemon: the request is written into
 * flyingeaves/requests/ as before, with a log line, and the job says {@link State#PYTHON}.
 */
public final class JavaBackend {
    public enum State { QUEUED, RUNNING, DONE, PYTHON, DROPPED }

    /** One request on its way. Read from any thread. */
    public static final class Job {
        public final String id;
        final JsonObject req;
        final boolean preview;
        final long since = System.currentTimeMillis();
        volatile State state = State.QUEUED;
        volatile JsonObject result;
        volatile CanvasStamp.Runs runs;
        volatile String stage = "排队";
        volatile int pct, done, total;
        /** Bumped on every progress change. */
        volatile int beat;

        Job(JsonObject req) {
            this.req = req;
            this.id = req.get("id").getAsString();
            this.preview = id.startsWith("pv_");
        }

        public State state() {
            return state;
        }

        /** The daemon's result ({"ok": ..., "blocks", "bbox", "sh" ... or "error"}), once {@link State#DONE}. */
        public JsonObject result() {
            return result;
        }

        /** The building, once DONE and ok. */
        public CanvasStamp.Runs runs() {
            return runs;
        }

        public int beat() {
            return beat;
        }

        /** As the daemon's progress file: {"stage", "pct", "done", "total"}. */
        public JsonObject progress() {
            JsonObject o = new JsonObject();
            o.addProperty("stage", stage);
            o.addProperty("pct", pct);
            if (total > 0) {
                o.addProperty("done", done);
                o.addProperty("total", total);
            }
            return o;
        }

        void progress(String stage, int done, int total) {
            this.stage = stage;
            this.done = done;
            this.total = total;
            int p = PCT.getOrDefault(stage, 0);
            if (stage.equals("建筑") && total > 0) p = PCT.get("建筑") + (PCT.get("地基") - PCT.get("建筑")) * done / total;
            this.pct = p;
            beat++;
        }
    }

    /** progress.py's stages. */
    private static final Map<String, Integer> PCT = Map.of("排队", 0, "规划", 5, "建筑", 10, "地基", 85, "写出", 92);

    /** Request types the Java side answers (others go to Python at once): compose, and the planners' "site" /
     *  "sentence" (a design back: {@link Planners#daemon}). A request without a type is a kind of the build menu:
     *  tried here, {@link Platforms.NotPorted} sends it on (a planner not ported yet too). */
    static final Set<String> TYPES = Set.of("compose", "site", "sentence");

    /** Heap a request needs, per column of its rooms' bounding box (measured: the 1:1 紫禁城, 830k columns, peaks at
     *  about 1.1 GB live; with room to spare). */
    private static final long BYTES_PER_COLUMN = 1600;

    private static final List<Job> QUEUE = new ArrayList<>();
    private static volatile String latestPreview;
    private static Thread worker;
    private static boolean initialised;
    private static String stamp = "";

    private JavaBackend() {
    }

    /** config backend = java (the default; a release build has no other). */
    public static boolean enabled() {
        if (!Build.PYTHON) return true;
        try {
            return !"python".equalsIgnoreCase(Config.BACKEND.get().trim());
        } catch (IllegalStateException e) {           // (config not loaded yet)
            return true;
        }
    }

    /** Take a request (it has its "id"): queued for the worker, or (a type Java does not do) handed to Python now. */
    public static Job submit(JsonObject req) {
        Job job = new Job(req.deepCopy());
        String type = req.has("type") ? req.get("type").getAsString() : null;
        if (type != null && !TYPES.contains(type)) {
            toPython(job, "type " + type + " is not ported");
            return job;
        }
        synchronized (QUEUE) {
            if (job.preview) latestPreview = job.id;
            QUEUE.add(job);
            QUEUE.notifyAll();
            startWorker();
        }
        return job;
    }

    /** Game start (backend = java): the worker comes up and writes what the daemon wrote when it started - kinds.json,
     *  presets/, library/, sites/ ({@link RunFiles}) - so the menus have their lists without Python. */
    public static void start() {
        synchronized (QUEUE) {
            startWorker();
        }
    }

    private static void startWorker() {
        if (worker != null) return;
        // (a big stack: the generator recurses deep, the oracle runs it with -Xss16m)
        worker = new Thread(null, JavaBackend::work, "flyingeaves-java-generator", 64L << 20);
        worker.setDaemon(true);
        worker.setPriority(Thread.NORM_PRIORITY - 1);
        worker.start();
    }

    /** For the player's messages when something is missing: how the in-game generator is. */
    public static String status() {
        return status;
    }

    private static volatile String status = "游戏内生成器还没起来";

    /** Requests waiting or being built (for status lines). */
    public static int pending() {
        synchronized (QUEUE) {
            return QUEUE.size();
        }
    }

    // ------------------------------------------------------------------ the worker

    /** The daemon's queue order: placements (and the rest) first in the order sent, then the newest preview only. */
    private static Job next() throws InterruptedException {
        synchronized (QUEUE) {
            while (true) {
                Job newestPv = null;
                for (Iterator<Job> it = QUEUE.iterator(); it.hasNext(); ) {
                    Job j = it.next();
                    if (!j.preview) {
                        it.remove();
                        return j;
                    }
                    newestPv = j;
                }
                if (newestPv != null) {
                    for (Job j : QUEUE)
                        if (j != newestPv) {
                            j.state = State.DROPPED;
                            FlyingEaves.LOGGER.info("java generator: {} dropped (a newer preview waits)", j.id);
                        }
                    QUEUE.clear();
                    return newestPv;
                }
                QUEUE.wait(1000);
                if (QUEUE.isEmpty()) return null;               // (idle: a look at the material and part files)
            }
        }
    }

    private static void work() {
        while (true) {
            Job job;
            try {
                job = next();
            } catch (InterruptedException e) {
                return;
            }
            if (job == null) {
                try {
                    prepare();
                } catch (Throwable t) {
                    status = "游戏内生成器读不了数据：" + t;
                    FlyingEaves.LOGGER.warn("java generator: could not load its data", t);
                }
                continue;
            }
            try {
                run(job);
            } catch (Throwable t) {                         // (never lose the worker)
                FlyingEaves.LOGGER.error("java generator: {} failed", job.id, t);
                fail(job, t);
            }
        }
    }

    private static void run(Job job) throws IOException {
        job.state = State.RUNNING;
        job.progress("规划", 0, 0);
        prepare();
        Map<String, Object> req = Json.parseObject(job.req.toString());
        long need = need(req);
        long free = freeHeap();
        if (need > free && !GeneratorProcess.failed()) {
            toPython(job, String.format("needs about %d MB of heap, %d MB free", need >> 20, free >> 20));
            return;
        }
        if (need > free) {                                  // (no Python to hand it to: what a collection gives back)
            System.gc();
            free = freeHeap();
            if (need > free) {
                FlyingEaves.LOGGER.warn("java generator: {} needs about {} MB of heap, {} MB free (max {} MB)", job.id, need >> 20,
                        free >> 20, Runtime.getRuntime().maxMemory() >> 20);
                fail(job, String.format("内存不够：这个要约 %d MB，游戏只剩 %d MB（最多 %d MB）。在启动器里把 -Xmx 调大（紫禁城 1:1 建议 -Xmx4G）",
                        need >> 20, free >> 20, Runtime.getRuntime().maxMemory() >> 20));
                return;
            }
        }
        if (!job.preview) writeAsked(job);
        if ("site".equals(req.get("type")) || "sentence".equals(req.get("type"))) {   // a planner: a design back
            Map<String, Object> out;
            try {               // (a new plan every time: the OS's randomness, as the daemon's secrets.randbits(62))
                out = Planners.daemon(req, PyRandom.ofInt(new java.security.SecureRandom().nextLong() >>> 2));
            } catch (Platforms.NotPorted e) {
                toPython(job, e.getMessage());
                return;
            }
            job.result = JsonParser.parseString(Json.write(out, false)).getAsJsonObject();
            job.state = State.DONE;
            FlyingEaves.LOGGER.info("java generator: {} {} {} {}", job.id, req.get("type"), str(req.get("which"), ""), out.get("ok"));
            return;
        }
        Compose.PROGRESS = (d, t) -> job.progress("建筑", d, t);
        Generator.STAGE = st -> job.progress(st, 0, 0);
        Compose.CANCEL = job.preview ? () -> {
            if (!job.id.equals(latestPreview)) throw new Compose.Superseded();
        } : () -> {
        };
        long t0 = System.nanoTime();
        Map<String, Object> res;
        try {
            res = Generator.build(req, null);
        } catch (Compose.Superseded e) {
            job.state = State.DROPPED;
            FlyingEaves.LOGGER.info("java generator: {} superseded", job.id);
            return;
        } catch (Platforms.NotPorted e) {
            toPython(job, e.getMessage());
            return;
        } catch (OutOfMemoryError e) {
            res = null;
            System.gc();
            if (!GeneratorProcess.failed()) {
                toPython(job, "out of memory");
                return;
            }
            FlyingEaves.LOGGER.warn("java generator: {} ran out of memory (max {} MB)", job.id, Runtime.getRuntime().maxMemory() >> 20);
            fail(job, "内存不够（游戏最多 " + (Runtime.getRuntime().maxMemory() >> 20) + " MB）：在启动器里把 -Xmx 调大（紫禁城 1:1 建议 -Xmx4G）");
            return;
        } catch (Exception | StackOverflowError e) {
            FlyingEaves.LOGGER.warn("java generator: {} failed", job.id, e);
            fail(job, e);
            return;
        } finally {
            Compose.CANCEL = () -> {
            };
            Compose.PROGRESS = (d, t) -> {
            };
            Generator.STAGE = st -> {
            };
        }
        if (job.preview && !job.id.equals(latestPreview)) {     // (the rooms were done before a newer one came: the
            job.state = State.DROPPED;                          // ground work after them does not look; skip the rest)
            FlyingEaves.LOGGER.info("java generator: {} superseded (after building)", job.id);
            return;
        }
        job.progress("写出", 0, 0);
        byte[] payload = (byte[]) res.remove(Generator.PAYLOAD);
        try {
            job.runs = CanvasStamp.parse(payload, job.id + ".dvcr");
        } catch (Exception e) {
            FlyingEaves.LOGGER.warn("java generator: {} gave blocks the game does not know", job.id, e);
            fail(job, e);
            return;
        }
        double secs = Math.round((System.nanoTime() - t0) / 1e7) / 100.0;
        res.put("seconds", secs);
        job.result = JsonParser.parseString(Json.write(res, false)).getAsJsonObject();
        job.state = State.DONE;
        FlyingEaves.LOGGER.info("java generator: {} {} {} {} ok blocks {} {} s", job.id, str(req.get("kind"), "compose"), req.get("tier"),
                req.get("seed"), res.get("blocks"), secs);
    }

    private static String str(Object o, String dflt) {
        return o == null ? dflt : o.toString();
    }

    private static void fail(Job job, Throwable t) {
        fail(job, t.getClass().getSimpleName() + ": " + t.getMessage());
    }

    private static void fail(Job job, String error) {
        JsonObject r = new JsonObject();
        r.addProperty("ok", false);
        r.addProperty("error", error);
        job.result = r;
        job.state = State.DONE;
    }

    /** The heap a request needs, roughly: its rooms' bounding box (a kind of the build menu: small). */
    static long need(Map<String, Object> req) {
        Object rooms = req.get("rooms");
        if (!(rooms instanceof List<?> l) || l.isEmpty()) return 64L << 20;
        long x0 = Long.MAX_VALUE, z0 = Long.MAX_VALUE, x1 = Long.MIN_VALUE, z1 = Long.MIN_VALUE;
        for (Object o : l) {
            if (!(o instanceof Map<?, ?> m)) continue;
            for (String k : new String[]{"x0", "x1"}) if (m.get(k) instanceof Number n) {
                x0 = Math.min(x0, n.longValue());
                x1 = Math.max(x1, n.longValue());
            }
            for (String k : new String[]{"z0", "z1"}) if (m.get(k) instanceof Number n) {
                z0 = Math.min(z0, n.longValue());
                z1 = Math.max(z1, n.longValue());
            }
        }
        if (x0 > x1 || z0 > z1) return 64L << 20;
        long cols = (x1 - x0 + 41) * (z1 - z0 + 41);         // (platforms, eaves and steps reach past the frames)
        return cols * BYTES_PER_COLUMN;
    }

    private static long freeHeap() {
        Runtime rt = Runtime.getRuntime();
        return rt.maxMemory() - (rt.totalMemory() - rt.freeMemory());
    }

    /** The daemon keeps every request but the previews as out/&lt;id&gt;.req.json (to rebuild it when something looks wrong). */
    private static void writeAsked(Job job) {
        try {
            Path out = FlyingEaves.dataDir().resolve("out");
            Files.createDirectories(out);
            Files.writeString(out.resolve(job.id + ".req.json"), job.req.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            FlyingEaves.LOGGER.debug("could not keep the request {}", job.id, e);
        }
    }

    /** Hand a request to the Python daemon (its file protocol, as before); a release build fails it instead. */
    private static void toPython(Job job, String why) {
        if (!Build.PYTHON) {                                // (a release build: there is no Python generator)
            FlyingEaves.LOGGER.warn("java generator: {} cannot be built ({})", job.id, why);
            fail(job, "游戏内生成器做不了这个：" + why);
            return;
        }
        FlyingEaves.LOGGER.info("java generator: {} -> python ({})", job.id, why);
        GeneratorProcess.startForFallback();
        try {
            writeRequest(job.req);
            job.state = State.PYTHON;
        } catch (IOException e) {
            fail(job, e);
        }
    }

    /** flyingeaves/requests/&lt;id&gt;.json, written whole (via .tmp) so the daemon never reads half of it. */
    public static void writeRequest(JsonObject req) throws IOException {
        Path dir = FlyingEaves.dataDir().resolve("requests");
        Files.createDirectories(dir);
        String id = req.get("id").getAsString();
        Path tmp = dir.resolve(id + ".tmp");
        Files.writeString(tmp, req.toString(), StandardCharsets.UTF_8);
        Files.move(tmp, dir.resolve(id + ".json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    // ------------------------------------------------------------------ data

    /** Point the generator at its data the first time; reload when the player's materials, parts or the game's block
     *  list changed (the daemon's materials.stamp / parts.stamp). */
    private static void prepare() throws IOException {
        Path run = FlyingEaves.dataDir();
        String now = stamp(run);
        if (initialised && now.equals(stamp)) return;
        if (!initialised) {
            Path data = dataDir();
            FlyingEaves.LOGGER.info("java generator: data from {}, run folder {}", data.toUri(), run);
            Env.init(data, run);
        } else {
            FlyingEaves.LOGGER.info("java generator: materials, parts or the block list changed: reloading");
            Env.forgetRegistry();
        }
        Generator.init();
        initialised = true;
        stamp = now;
        try {
            int n = RunFiles.write(run);
            FlyingEaves.LOGGER.info("java generator: wrote kinds.json ({} kinds), presets, library, sites", n);
            status = "游戏内生成器在运行";
        } catch (IOException e) {
            status = "游戏内生成器写不了 " + run + "：" + e;
            FlyingEaves.LOGGER.warn("java generator: could not write kinds.json and the rest", e);
        }
    }

    /** generator/data: -Dflyingeaves.generatorDir (the dev client), config generatorDir, else inside the mod jar. */
    static Path dataDir() throws IOException {
        String prop = System.getProperty("flyingeaves.generatorDir");
        if (prop != null && !prop.isBlank()) return Path.of(prop).resolve("data");
        String cfg = Config.GENERATOR_DIR.get();
        if (cfg != null && !cfg.isBlank()) return Path.of(cfg).resolve("data");
        Path p = ModList.get().getModFileById(FlyingEaves.MODID).getFile().findResource("flyingeaves", "generator", "data");
        if (!Files.isDirectory(p)) throw new IOException("the generator's data is not in the mod jar");
        return p;
    }

    private static String stamp(Path run) {
        StringBuilder sb = new StringBuilder();
        for (String sub : new String[]{"materials", "parts"}) {
            Path d = run.resolve(sub);
            if (!Files.isDirectory(d)) continue;
            try (Stream<Path> s = Files.list(d)) {
                for (Path p : s.filter(q -> q.toString().endsWith(".json")).sorted().toList())
                    sb.append(p.getFileName()).append(' ').append(Files.getLastModifiedTime(p).toMillis()).append('\n');
            } catch (IOException e) {
                sb.append(sub).append(" ?\n");
            }
        }
        Path reg = run.resolve("registry_blocks.txt");
        try {
            if (Files.exists(reg)) sb.append("registry ").append(Files.getLastModifiedTime(reg).toMillis());
        } catch (IOException ignored) {
        }
        return sb.toString();
    }
}
