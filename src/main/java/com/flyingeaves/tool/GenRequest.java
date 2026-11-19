package com.flyingeaves.tool;

import com.flyingeaves.backend.JavaBackend;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * One request to the generator, whichever builds it: the Java one in the game ({@link JavaBackend}, config backend =
 * java) or the Python daemon (the file protocol: requests/&lt;id&gt;.json in, out/&lt;id&gt;.json and .dvcr back). A Java job
 * that turns out to be something the Java side does not have yet goes on to Python by itself; this follows it.
 */
final class GenRequest {
    final String id;
    /** null: straight to Python (backend = python). */
    private final JavaBackend.Job job;
    final GenProgress prog;

    private GenRequest(String id, JavaBackend.Job job) {
        this.id = id;
        this.job = job;
        this.prog = new GenProgress(id, this);
    }

    /** Send a request (it has its "id"). Throws when the request file cannot be written. */
    static GenRequest send(JsonObject req) throws Exception {
        String id = req.get("id").getAsString();
        if (JavaBackend.enabled()) return new GenRequest(id, JavaBackend.submit(req));
        JavaBackend.writeRequest(req);
        return new GenRequest(id, null);
    }

    /** Built in the game (not, or no longer, by Python). */
    boolean java() {
        return job != null && job.state() != JavaBackend.State.PYTHON;
    }

    JavaBackend.Job job() {
        return job;
    }

    /** Dropped for a newer preview (Java; the daemon just never answers such a one). */
    boolean dropped() {
        return job != null && job.state() == JavaBackend.State.DROPPED;
    }

    private Path json() {
        return BuildTool.RUN.resolve("out").resolve(id + ".json");
    }

    Path dvcr() {
        return BuildTool.RUN.resolve("out").resolve(id + ".dvcr");
    }

    /** The result once there ({"ok": true, "blocks" ...} / {"ok": false, "error"} / a design), else null. */
    JsonObject result() throws Exception {
        if (java()) return job.state() == JavaBackend.State.DONE ? job.result() : null;
        Path json = json();
        if (!Files.exists(json)) return null;
        return JsonParser.parseString(Files.readString(json, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    /** The building (after an ok result): handed over by the Java side, or read from the daemon's .dvcr. */
    CanvasStamp.Runs runs() throws Exception {
        if (java()) return job.runs();
        return CanvasStamp.read(dvcr());
    }

    /** Done with it: the daemon's files go (the Java side has none). */
    void delete(boolean dvcrToo) {
        prog.delete();
        if (java()) return;
        try {
            Files.deleteIfExists(json());
            if (dvcrToo) Files.deleteIfExists(dvcr());
        } catch (Exception ignored) {
        }
    }
}
