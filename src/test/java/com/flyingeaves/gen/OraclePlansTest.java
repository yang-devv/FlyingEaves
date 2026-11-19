package com.flyingeaves.gen;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The planners (designs back) against the frozen Python ones (tools/oracle/capture.py plans): per request the sha256 of
 * the design JSON text oracle_dump.plan wrote ({"ok": ..., "design": ...}, json.dumps(ensure_ascii=False)). The
 * requests run in the order Python ran them (sect_plan's footprint memo carries over, as in the daemon).
 */
class OraclePlansTest {
    @TestFactory
    @SuppressWarnings("unchecked")
    Stream<DynamicTest> plans() {
        Env.init(Path.of(System.getProperty("flyingeaves.genData", "generator/data")), null);
        Generator.init();
        List<DynamicTest> out = new ArrayList<>();
        for (Map<String, Object> c : OracleCases.load("plans")) {
            Map<String, Object> req = (Map<String, Object>) c.get("req");
            String id = (String) req.get("id");
            out.add(DynamicTest.dynamicTest(id, () -> {
                Map<String, Object> mine = Json.parseObject(Json.write(req, false));
                Map<String, Object> res = new LinkedHashMap<>();
                try {
                    res.put("ok", true);
                    res.put("design", Planners.plan(mine));
                } catch (RuntimeException e) {
                    res.clear();
                    res.put("ok", false);
                    res.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
                }
                String text = Json.write(res, false);
                String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
                assertEquals(c.get("sha256"), sha, id + ": the design differs from the Python planner's (tools/oracle/oracle.py plans to see where)");
            }));
        }
        return out.stream();
    }
}
