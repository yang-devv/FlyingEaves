package com.flyingeaves.tool;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 一个请求在生成器那边的进度：flyingeaves/out/&lt;id&gt;.progress.json（bgen_daemon.py 每秒重写一次，排着队的写「排队」，
 * 出结果就删掉）。大城镇、宗门、1:1 宫城本来就要一两分钟，所以「生成器开着吗」只在这个文件<b>一段时间没变</b>时才问，
 * 不按总共等了多久。
 */
final class GenProgress {
    /** 这么久进度文件没动静才提醒。 */
    static final long SILENT_MS = 20_000;

    final String id;
    final long since;
    private long lastSeen, lastCheck, lastMtime = Long.MIN_VALUE, lastWarn;
    private String text;
    private int pct = -1;

    /** The request, when it may be built in the game (the Java generator keeps its progress in memory, not in a file). */
    private final GenRequest req;

    GenProgress(String id) {
        this(id, System.currentTimeMillis());
    }

    GenProgress(String id, GenRequest req) {
        this(id, System.currentTimeMillis(), req);
    }

    GenProgress(String id, long now) {
        this(id, now, null);
    }

    private GenProgress(String id, long now, GenRequest req) {
        this.id = id;
        this.since = now;
        this.lastSeen = now;
        this.req = req;
    }

    static Path file(String id) {
        return BuildTool.RUN.resolve("out").resolve(id + ".progress.json");
    }

    /** 每 tick 调，一秒看一次文件；看到新进度时返回 true（{@link #text()} 换了）。 */
    boolean poll(long now) {
        if (now - lastCheck < 1000) return false;
        lastCheck = now;
        if (req != null && req.java()) {                     // in the game: alive while queued or running, a beat a second
            lastSeen = now;                                  // (as the daemon rewrites its file every second)
            JsonObject o = req.job().progress();
            pct = o.get("pct").getAsInt();
            text = text(o, now - since);
            return true;
        }
        Path p = file(id);
        try {
            long m = Files.getLastModifiedTime(p).toMillis();
            if (m == lastMtime) return false;
            lastMtime = m;
            lastSeen = now;                                  // the file changed: the daemon is alive
            JsonObject o = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
            pct = o.has("pct") ? o.get("pct").getAsInt() : -1;
            text = text(o, now - since);
            return true;
        } catch (Exception e) {
            return false;                                    // not there yet, or being replaced
        }
    }

    /** 多久没看到进度变了（还没出现过就是从发请求算起）。 */
    long silentMs(long now) {
        return now - lastSeen;
    }

    /** 没动静超过 {@link #SILENT_MS} 时，每 10 秒返回一次 true。 */
    boolean warnNow(long now) {
        if (silentMs(now) < SILENT_MS || now - lastWarn < 10_000) return false;
        lastWarn = now;
        return true;
    }

    String warning(long now) {
        return "生成器 " + silentMs(now) / 1000 + " 秒没动静……（" + com.flyingeaves.backend.GeneratorProcess.hint() + "）";
    }

    String text() {
        return text;
    }

    int pct() {
        return pct;
    }

    /** 结果到了：进度文件生成器自己会删，这里也删一下（旧生成器、出错时留下的）。 */
    void delete() {
        try {
            Files.deleteIfExists(file(id));
        } catch (Exception ignored) {
        }
    }

    /** 「生成中 45% · 建筑 37/97 · 23 秒」；排着队是「排队中（生成器在忙前一个）· 5 秒」。 */
    static String text(JsonObject o, long elapsedMs) {
        String stage = o.has("stage") ? o.get("stage").getAsString() : "";
        String secs = " · " + elapsedMs / 1000 + " 秒";
        if (stage.equals("排队")) return "排队中（生成器在忙前一个）" + secs;
        StringBuilder sb = new StringBuilder("生成中");
        if (o.has("pct")) sb.append(' ').append(o.get("pct").getAsInt()).append('%');
        if (!stage.isEmpty()) sb.append(" · ").append(stage);
        if (o.has("done") && o.has("total")) sb.append(' ').append(o.get("done").getAsInt()).append('/').append(o.get("total").getAsInt());
        return sb.append(secs).toString();
    }
}
