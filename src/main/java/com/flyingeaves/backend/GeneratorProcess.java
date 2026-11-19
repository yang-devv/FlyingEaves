package com.flyingeaves.backend;

import com.flyingeaves.Build;
import com.flyingeaves.Config;
import com.flyingeaves.FlyingEaves;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Starts and stops the generator: {@code python bgen_daemon.py --run <game dir>/flyingeaves --watch-stdin}. The game
 * and the generator talk through files in that folder (requests/, out/, kinds.json …), as before.
 *
 * <ul>
 *   <li>Python: config {@code pythonPath}; else a bundled {@code python/} folder in {@code <game dir>/flyingeaves/} or
 *   {@code <game dir>/config/flyingeaves/}; else {@code python} on PATH (Windows also {@code py -3}), else
 *   {@code python3}. Each is asked for its version first (3.10+).</li>
 *   <li>Generator: {@code -Dflyingeaves.generatorDir} (the dev client), config {@code generatorDir}, else the copy
 *   unpacked from the jar into {@code <game dir>/flyingeaves/generator} (again when the mod changes).</li>
 *   <li>Stopping: on game shutdown (and a JVM shutdown hook) the process is ended; the daemon also exits on its own
 *   when its standard input closes, so it never outlives a crashed game.</li>
 *   <li>Its output goes to {@code logs/flyingeaves-generator.log}.</li>
 * </ul>
 *
 * <p>Only in a build made with {@code -PwithPython=true} ({@link Build#PYTHON}, development). A release build has no
 * Python generator: every entry point here is a no-op ({@link #start}, {@link #startForFallback}), nothing looks for
 * Python, and {@link #hint} speaks of the Java generator.
 */
public final class GeneratorProcess {
    private static volatile Process process;
    private static volatile String status = "生成器还没启动";
    private static volatile boolean stopping;
    private static boolean hooked;
    /** start() was called (backend = java: only once a request needs Python). */
    private static volatile boolean attempted;
    /** It could not be started (no Python, no daemon, autoStart off): requests are better kept in the game. */
    private static volatile boolean failed;

    private GeneratorProcess() {
    }

    /** One line for the player when the generator does not answer: why. */
    public static String hint() {
        if (!Build.PYTHON) return JavaBackend.status();
        Process p = process;
        if (p != null && p.isAlive()) return "生成器在运行，但没回应；看 logs/flyingeaves-generator.log";
        if (!attempted && JavaBackend.enabled()) return JavaBackend.status();
        return status;
    }

    public static boolean running() {
        Process p = process;
        return p != null && p.isAlive();
    }

    /** Called once on client setup: off the main thread, find Python, unpack the generator, launch it. */
    public static synchronized void start() {
        if (!Build.PYTHON) return;                         // (a release build: no Python generator at all)
        attempted = true;
        if (!Config.AUTO_START.get()) {
            status = "生成器没自动启动（config/flyingeaves-client.toml 里 autoStart = false）";
            failed = true;
            return;
        }
        if (running()) return;
        Thread t = new Thread(GeneratorProcess::launch, "flyingeaves-generator-start");
        t.setDaemon(true);
        t.start();
    }

    private static void launch() {
        try {
            Path gen = generatorDir();
            if (!Files.exists(gen.resolve("bgen_daemon.py"))) {
                status = "生成器没起来：" + gen + " 里没有 bgen_daemon.py";
                failed = true;
                FlyingEaves.LOGGER.warn("generator: no bgen_daemon.py in {}", gen);
                return;
            }
            List<String> python = findPython();
            if (python == null) {
                status = "生成器没起来：找不到 Python 3.10+（装 Python，或在 config/flyingeaves-client.toml 里填 pythonPath）";
                failed = true;
                FlyingEaves.LOGGER.warn("generator: no Python 3.10+ found");
                return;
            }
            Path run = FlyingEaves.dataDir();
            Files.createDirectories(run);
            Path log = FMLPaths.GAMEDIR.get().resolve("logs").resolve("flyingeaves-generator.log");
            Files.createDirectories(log.getParent());
            List<String> cmd = new ArrayList<>(python);
            cmd.addAll(List.of("-u", gen.resolve("bgen_daemon.py").toString(), "--run", run.toAbsolutePath().toString(), "--watch-stdin"));
            ProcessBuilder pb = new ProcessBuilder(cmd).directory(gen.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.to(log.toFile()));
            pb.environment().put("PYTHONIOENCODING", "utf-8");
            pb.environment().put("PYTHONUTF8", "1");
            pb.environment().remove("PYTHONHOME");
            pb.environment().remove("PYTHONPATH");
            synchronized (GeneratorProcess.class) {
                if (stopping) return;
                process = pb.start();     // (stdin stays a pipe we never close: the daemon exits when it does)
                if (!hooked) {
                    hooked = true;
                    Runtime.getRuntime().addShutdownHook(new Thread(GeneratorProcess::stop, "flyingeaves-generator-stop"));
                }
            }
            status = "生成器在运行";
            FlyingEaves.LOGGER.info("generator started: {} (pid {}), log {}", String.join(" ", cmd), process.pid(), log);
            Process p = process;
            p.onExit().thenAccept(q -> {
                if (!stopping) {
                    status = "生成器退出了（退出码 " + q.exitValue() + "）；看 logs/flyingeaves-generator.log";
                    FlyingEaves.LOGGER.warn("generator exited with {}", q.exitValue());
                }
            });
        } catch (Exception e) {
            status = "生成器没起来：" + e;
            failed = true;
            FlyingEaves.LOGGER.warn("generator failed to start", e);
        }
    }

    /** It was tried and could not be started (a release build: there is none, always). */
    public static boolean failed() {
        return !Build.PYTHON || failed;
    }

    /** backend = java: a request the game's generator does not do yet needs the Python one - start it if it is not
     *  (it picks up the request file once up; starting takes a few seconds). */
    public static void startForFallback() {
        if (Build.PYTHON && !attempted && !stopping) start();
    }

    /** At game start: the Python generator with backend = python; with java only the game's own (Python comes up
     *  when a request needs it). */
    public static void startAtLaunch() {
        if (JavaBackend.enabled()) JavaBackend.start();
        else start();
        FlyingEaves.LOGGER.info("flyingeaves: generator {}", !Build.PYTHON ? "java (no python)"
                : JavaBackend.enabled() ? "java, python fallback (python build)" : "python (backend = python)");
    }

    /** End the generator (game shutdown). */
    public static synchronized void stop() {
        stopping = true;
        Process p = process;
        if (p == null || !p.isAlive()) return;
        try {
            OutputStream in = p.getOutputStream();
            in.close();                                    // the daemon's stdin watch ends it
            if (!p.waitFor(2, TimeUnit.SECONDS)) {
                p.destroy();
                if (!p.waitFor(2, TimeUnit.SECONDS)) p.destroyForcibly();
            }
            FlyingEaves.LOGGER.info("generator stopped");
        } catch (Exception e) {
            p.destroyForcibly();
        }
    }

    // ------------------------------------------------------------------ the generator's folder

    static Path generatorDir() throws IOException {
        String prop = System.getProperty("flyingeaves.generatorDir");
        if (prop != null && !prop.isBlank()) return Path.of(prop);
        String cfg = Config.GENERATOR_DIR.get();
        if (cfg != null && !cfg.isBlank()) return Path.of(cfg);
        Path dir = FlyingEaves.dataDir().resolve("generator");
        unpack(dir);
        return dir;
    }

    /** Copy flyingeaves/generator/** out of the mod jar into dir, unless the copy there is of this very jar. */
    private static void unpack(Path dir) throws IOException {
        var file = ModList.get().getModFileById(FlyingEaves.MODID).getFile();
        Path src = file.findResource("flyingeaves", "generator");
        if (!Files.isDirectory(src)) throw new IOException("the generator is not in the mod jar");
        Path jar = file.getFilePath();
        String stamp = ModList.get().getModContainerById(FlyingEaves.MODID).map(c -> c.getModInfo().getVersion().toString()).orElse("?") + " " + jar.getFileName() + " "
                + (Files.isRegularFile(jar) ? Files.size(jar) + " " + Files.getLastModifiedTime(jar).toMillis() : "dev");
        Path stampFile = dir.resolve(".unpacked");
        if (Files.exists(stampFile) && Files.readString(stampFile, StandardCharsets.UTF_8).equals(stamp)) return;
        if (Files.exists(dir)) {
            try (Stream<Path> old = Files.walk(dir)) {
                for (Path p : old.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
            }
        }
        int n = 0;
        try (Stream<Path> all = Files.walk(src)) {
            for (Path p : all.toList()) {
                Path to = dir.resolve(src.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(to);
                } else {
                    Files.createDirectories(to.getParent());
                    Files.copy(p, to);
                    n++;
                }
            }
        }
        Files.writeString(stampFile, stamp, StandardCharsets.UTF_8);
        FlyingEaves.LOGGER.info("generator: unpacked {} files into {}", n, dir);
    }

    // ------------------------------------------------------------------ Python

    private static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /** The command that runs Python 3.10+, or null. */
    static List<String> findPython() {
        if (!Build.PYTHON) return null;
        List<List<String>> tries = new ArrayList<>();
        String cfg = Config.PYTHON_PATH.get();
        if (cfg != null && !cfg.isBlank()) {
            Path p = Path.of(cfg.trim());
            Path exe = Files.isDirectory(p) ? inFolder(p) : p;
            if (exe != null) tries.add(List.of(exe.toString()));
        } else {
            for (Path folder : List.of(FlyingEaves.dataDir().resolve("python"), FMLPaths.CONFIGDIR.get().resolve(FlyingEaves.MODID).resolve("python"))) {
                Path exe = inFolder(folder);
                if (exe != null) tries.add(List.of(exe.toString()));
            }
            tries.add(List.of("python"));
            if (windows()) tries.add(List.of("py", "-3"));
            tries.add(List.of("python3"));
        }
        for (List<String> cmd : tries) {
            if (works(cmd)) return cmd;
        }
        return null;
    }

    private static Path inFolder(Path folder) {
        for (String name : List.of("python.exe", "bin/python3", "bin/python", "python3", "python")) {
            Path p = folder.resolve(name);
            if (Files.isRegularFile(p)) return p;
        }
        return null;
    }

    /** It starts, and says it is 3.10 or newer. */
    private static boolean works(List<String> cmd) {
        try {
            List<String> c = new ArrayList<>(cmd);
            c.addAll(List.of("-c", "import sys; print(sys.version_info >= (3, 10))"));
            Process p = new ProcessBuilder(c).redirectErrorStream(true).start();
            p.getOutputStream().close();
            if (!p.waitFor(15, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return false;
            }
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            boolean ok = p.exitValue() == 0 && out.endsWith("True");
            FlyingEaves.LOGGER.info("generator: {} -> {} {}", String.join(" ", cmd), p.exitValue(), out);
            return ok;
        } catch (Exception e) {
            FlyingEaves.LOGGER.info("generator: {} does not run ({})", String.join(" ", cmd), e.toString());
            return false;
        }
    }
}
