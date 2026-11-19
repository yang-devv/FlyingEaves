package com.flyingeaves.gen.tools;

import com.flyingeaves.gen.Env;
import com.flyingeaves.gen.Generator;
import com.flyingeaves.gen.Json;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Java side of the oracle harness (tools/oracle/oracle.py): run the port on daemon requests and write what the
 * daemon writes - OUT/&lt;id&gt;.dvcr and OUT/&lt;id&gt;.json (sorted keys, no "seconds").
 *
 * <pre>java -cp build/gen-classes com.flyingeaves.gen.tools.Dump OUT_DIR REQ.json [REQ.json ...]</pre>
 * Data: -Dflyingeaves.genData (default generator/data), no run folder (only minecraft: blocks count as present).
 */
public final class Dump {
    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]);
        Files.createDirectories(out);
        Env.init(Path.of(System.getProperty("flyingeaves.genData", "generator/data")), null);
        Generator.init();
        List<Object> reqs = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            Object d = Json.parse(Files.readString(Path.of(args[i]), StandardCharsets.UTF_8));
            if (d instanceof List<?> l) reqs.addAll(l);
            else reqs.add(d);
        }
        for (Object o : reqs) {
            String text = Json.write(o, false);
            @SuppressWarnings("unchecked") Map<String, Object> req = (Map<String, Object>) Json.parse(text);
            Map<String, Object> res;
            long t0 = System.nanoTime();
            if ("plan".equals(req.get("type"))) {             // a planner: the design back (oracle_dump.plan)
                res = new LinkedHashMap<>();
                try {
                    Object d = com.flyingeaves.gen.Planners.plan(req);
                    res.put("ok", true);
                    res.put("design", d);
                } catch (Exception | StackOverflowError e) {
                    res.put("ok", false);
                    res.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
                    if (!(e instanceof com.flyingeaves.gen.Platforms.NotPorted)) e.printStackTrace();
                }
                Files.writeString(out.resolve(req.get("id") + ".design.json"), Json.write(res, false), StandardCharsets.UTF_8);
                System.out.println(req.get("id") + " " + res.get("ok") + " " + (System.nanoTime() - t0) / 1_000_000 + " ms"
                        + (res.get("error") != null ? " " + res.get("error") : ""));
                continue;
            }
            try {
                res = Generator.build(req, out);
            } catch (Exception | StackOverflowError e) {
                res = new LinkedHashMap<>();
                res.put("ok", false);
                res.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
                if (!(e instanceof com.flyingeaves.gen.Platforms.NotPorted)) e.printStackTrace();
            }
            Files.writeString(out.resolve(req.get("id") + ".json"), Json.write(res, true), StandardCharsets.UTF_8);
            System.out.println(req.get("id") + " " + res.get("ok") + " " + res.get("blocks") + " " + (System.nanoTime() - t0) / 1_000_000 + " ms"
                    + (res.get("error") != null ? " " + res.get("error") : ""));
        }
    }
}
