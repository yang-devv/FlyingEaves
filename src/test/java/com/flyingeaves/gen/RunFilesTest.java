package com.flyingeaves.gen;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * What the Java side writes into the run folder at start-up ({@link RunFiles}: kinds.json, presets/, library/, sites/)
 * against what the Python daemon wrote (tools/oracle/capture.py startup): the same files with the same text, for an
 * empty run folder and for one with the game's block list, a material file, a bases file and bay parts of the player's.
 */
class RunFilesTest {
    @SuppressWarnings("unchecked")
    @TestFactory
    Stream<DynamicTest> startup() throws IOException {
        Map<String, Object> fix;
        try (InputStream raw = RunFilesTest.class.getResourceAsStream("/oracle/startup.json.gz")) {
            assertNotNull(raw, "no fixture (python -I tools/oracle/capture.py startup)");
            fix = Json.parseObject(new String(new GZIPInputStream(raw).readAllBytes(), StandardCharsets.UTF_8));
        }
        List<DynamicTest> out = new ArrayList<>();
        for (Map.Entry<String, Object> sc : fix.entrySet()) {
            Map<String, Object> s = (Map<String, Object>) sc.getValue();
            out.add(DynamicTest.dynamicTest(sc.getKey(), () -> check((Map<String, Object>) s.get("inputs"), (Map<String, Object>) s.get("files"))));
        }
        return out.stream();
    }

    private static void check(Map<String, Object> inputs, Map<String, Object> expected) throws Exception {
        Path run = Files.createTempDirectory("fe_runfiles");
        for (Map.Entry<String, Object> e : inputs.entrySet()) {
            Path p = run.resolve(e.getKey());
            Files.createDirectories(p.getParent());
            Files.writeString(p, (String) e.getValue(), StandardCharsets.UTF_8);
        }
        Env.init(Path.of(System.getProperty("flyingeaves.genData", "generator/data")), run);
        Generator.init();
        RunFiles.write(run);
        Map<String, String> got = new TreeMap<>();
        try (Stream<Path> all = Files.walk(run)) {
            for (Path p : all.filter(Files::isRegularFile).toList()) {
                String rel = run.relativize(p).toString().replace('\\', '/');
                if (!inputs.containsKey(rel)) got.put(rel, text(p));
            }
        }
        Map<String, String> want = new TreeMap<>();
        for (Map.Entry<String, Object> e : expected.entrySet()) want.put(e.getKey(), ((String) e.getValue()).replace("\r\n", "\n"));
        assertEquals(want.keySet(), got.keySet(), "the files");
        for (String k : want.keySet()) assertEquals(want.get(k), got.get(k), k);
    }

    private static String text(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n");   // (as Python's read_text)
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
