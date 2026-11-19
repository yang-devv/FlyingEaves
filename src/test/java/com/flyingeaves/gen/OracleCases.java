package com.flyingeaves.gen;

import org.junit.jupiter.api.DynamicTest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The oracle's checked-in answers (tools/oracle/capture.py: src/test/resources/oracle/&lt;set&gt;.json.gz): per request
 * the sha256 of the .dvcr payload the frozen Python generator wrote, and its result dict. The requests of a set ran in
 * one Python process in order (module state carries over, as in the daemon), so they run here in that order too, in a
 * JVM of their own (build.gradle: forkEvery 1).
 */
final class OracleCases {
    private OracleCases() {
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> load(String set) {
        try (InputStream raw = OracleCases.class.getResourceAsStream("/oracle/" + set + ".json.gz")) {
            assertNotNull(raw, "no fixture " + set + " (python -I tools/oracle/capture.py " + set + ")");
            String text = new String(new GZIPInputStream(raw).readAllBytes(), StandardCharsets.UTF_8);
            return (List<Map<String, Object>>) Json.parse(text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** One dynamic test per case, run in order: build it (no file), compare the payload's hash and the result. */
    @SuppressWarnings("unchecked")
    static Stream<DynamicTest> run(List<Map<String, Object>> cases) {
        Generator.init();
        List<DynamicTest> out = new ArrayList<>();
        for (Map<String, Object> c : cases) {
            Map<String, Object> req = (Map<String, Object>) c.get("req");
            String id = (String) req.get("id");
            out.add(DynamicTest.dynamicTest(id, () -> check(c, req, id)));
        }
        return out.stream();
    }

    @SuppressWarnings("unchecked")
    private static void check(Map<String, Object> c, Map<String, Object> req, String id) throws Exception {
        // (a copy, as the daemon parses each request afresh: the generator may change what it is given)
        Map<String, Object> mine = Json.parseObject(Json.write(req, false));
        Map<String, Object> res = Generator.build(mine, null);
        byte[] payload = (byte[]) res.remove(Generator.PAYLOAD);
        Map<String, Object> expected = (Map<String, Object>) c.get("result");
        assertEquals(Json.write(expected, true), Json.write(Json.parse(Json.write(res, true)), true), id + ": result");
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        assertEquals(c.get("sha256"), sha, id + ": the .dvcr payload differs from the Python generator's (tools/oracle/oracle.py run to see where)");
    }
}
