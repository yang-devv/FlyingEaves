package com.flyingeaves.gen;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * arch.Canvas: world-space block store - single blocks (a dict in insertion order, last write wins) plus column runs
 * (written first, under the blocks). Keys are packed longs ({@link #key}, {@link #ckey}); the maps keep Python's dict
 * order (a LinkedHashMap keeps a key's first position when it is written again, as a dict does).
 */
public final class Canvas {
    public final LinkedHashMap<Long, String> blocks = new LinkedHashMap<>();
    /** defaultdict(list): {@link #colsAt} creates the entry as {@code cv.cols[key]} does; {@code get} does not. */
    public final LinkedHashMap<Long, List<Run>> cols = new LinkedHashMap<>();

    public record Run(int y0, int y1, String s) {
    }

    // ------------------------------------------------------------------------------------------ keys
    // x, z: 26 bits each (+-33M), y: 12 bits (+-2048)

    public static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    public static int kx(long k) {
        return (int) (k >> 38);
    }

    public static int kz(long k) {
        return (int) ((k << 26) >> 38);
    }

    public static int ky(long k) {
        return (int) ((k << 52) >> 52);
    }

    public static long ckey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    public static int cx(long k) {
        return (int) (k >> 32);
    }

    public static int cz(long k) {
        return (int) k;
    }

    // ------------------------------------------------------------------------------------------ access

    public void set(int x, int y, int z, String s) {
        blocks.put(key(x, y, z), s);
    }

    public String get(int x, int y, int z) {
        return blocks.get(key(x, y, z));
    }

    public List<Run> colsAt(long ck) {
        return cols.computeIfAbsent(ck, k -> new ArrayList<>());
    }

    public void run(int x, int z, int y0, int y1, String s) {
        if (y1 >= y0) colsAt(ckey(x, z)).add(new Run(y0, y1, s));
    }

    /** (x0, z0, x1, z1). */
    public int[] bbox() {
        int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (long k : blocks.keySet()) {
            int x = kx(k), z = kz(k);
            x0 = Math.min(x0, x);
            x1 = Math.max(x1, x);
            z0 = Math.min(z0, z);
            z1 = Math.max(z1, z);
        }
        for (long k : cols.keySet()) {
            int x = cx(k), z = cz(k);
            x0 = Math.min(x0, x);
            x1 = Math.max(x1, x);
            z0 = Math.min(z0, z);
            z1 = Math.max(z1, z);
        }
        if (x0 == Integer.MAX_VALUE) throw new IllegalArgumentException("min() arg is an empty sequence");
        return new int[]{x0, z0, x1, z1};
    }

    // ------------------------------------------------------------------------------------------ supports

    static boolean startsWithHang(String b) {
        return b.startsWith("minecraft:chain") || b.startsWith("minecraft:lantern[hanging=true") || b.startsWith("minecraft:soul_lantern[hanging=true");
    }

    static final String CHAIN = "minecraft:chain[axis=y]";
    private static final String[] FLOOR_PREFIX = {"minecraft:lantern[hanging=false", "minecraft:soul_lantern[hanging=false", "minecraft:candle",
            "minecraft:flower_pot", "minecraft:potted_", "minecraft:torch", "minecraft:rail", "minecraft:lily_pad", "minecraft:redstone_torch"};

    static boolean needsFloor(String b) {
        if (Blocks.startsWithAny(b, FLOOR_PREFIX)) return true;
        String name = b.split("\\[", 2)[0];
        if (name.endsWith("_carpet") || name.endsWith("_pressure_plate")) return true;
        if (name.endsWith("_banner") && !name.endsWith("_wall_banner")) return true;
        return name.endsWith("_button") && b.contains("face=floor");
    }

    private boolean solidAt(int x, int y, int z) {
        String b = blocks.get(key(x, y, z));
        if (b != null) return !b.equals(Blocks.AIR) && !startsWithHang(b) && !needsFloor(b);
        List<Run> rs = cols.get(ckey(x, z));
        if (rs != null) for (Run r : rs) if (r.y0 <= y && y <= r.y1 && !r.s.equals(Blocks.AIR)) return true;
        return false;
    }

    /** arch.Canvas.fix_supports: (chained, removed). */
    public int[] fixSupports() {
        String air = Blocks.AIR;
        int chained = 0, removed = 0;
        for (Map.Entry<Long, String> e : new ArrayList<>(blocks.entrySet())) {
            long k = e.getKey();
            String b = e.getValue();
            int x = kx(k), y = ky(k), z = kz(k);
            if (b.startsWith("minecraft:lantern[hanging=true") || b.startsWith("minecraft:soul_lantern[hanging=true")
                    || (b.startsWith("minecraft:bell") && b.contains("attachment=ceiling"))) {
                List<Integer> gap = new ArrayList<>();
                boolean found = false;
                for (int yy = y + 1; yy < y + 61; yy++) {
                    String c = blocks.get(key(x, yy, z));
                    if (c != null && c.startsWith("minecraft:chain")) continue;
                    if (solidAt(x, yy, z)) {
                        for (int g : gap) blocks.put(key(x, g, z), CHAIN);
                        if (!gap.isEmpty()) chained++;
                        found = true;
                        break;
                    }
                    gap.add(yy);
                }
                if (!found) {
                    blocks.put(k, air);
                    int yy = y + 1;
                    while (blocks.getOrDefault(key(x, yy, z), "").startsWith("minecraft:chain")) {
                        blocks.put(key(x, yy, z), air);
                        yy++;
                    }
                    removed++;
                }
            } else if (needsFloor(b) && air.equals(blocks.get(key(x, y - 1, z)))) {
                blocks.put(k, air);
                removed++;
            }
        }
        return new int[]{chained, removed};
    }

    // ------------------------------------------------------------------------------------------ export

    /** The DVCR payload (what the .dvcr file holds, before gzip), with its counts. */
    public record Encoded(byte[] payload, int runs, int blocks) {
    }

    /** arch.Canvas.export: write a DVCR file (gzip, as Python's gzip.open(tmp, "wb", compresslevel=3)); (runs, blocks). */
    public int[] export(Path path) throws IOException {
        Encoded e = encode();
        write(e, path);
        return new int[]{e.runs, e.blocks};
    }

    /** export without the file: fixSupports, then the uncompressed payload (the in-game backend hands it to the game). */
    public Encoded encode() {
        fixSupports();
        int[] bb = bbox();
        int x0 = bb[0], z0 = bb[1], x1 = bb[2], z1 = bb[3];
        int w = x1 - x0 + 1, d = z1 - z0 + 1;
        HashMap<Long, List<long[]>> perCol = new HashMap<>();      // (y, index into strs)
        List<String> strs = new ArrayList<>();
        for (Map.Entry<Long, String> e : blocks.entrySet()) {
            long k = e.getKey();
            perCol.computeIfAbsent(ckey(kx(k), kz(k)), q -> new ArrayList<>()).add(new long[]{ky(k), strs.size()});
            strs.add(e.getValue());
        }
        TreeMap<Integer, List<Run>> rows = new TreeMap<>();
        java.util.LinkedHashSet<Long> keys = new java.util.LinkedHashSet<>(perCol.keySet());
        keys.addAll(cols.keySet());
        for (long ck : keys) {
            List<Run> runs = new ArrayList<>(cols.getOrDefault(ck, List.of()));
            List<long[]> ys = perCol.getOrDefault(ck, new ArrayList<>());
            ys.sort((a, b) -> a[0] != b[0] ? Long.compare(a[0], b[0]) : strs.get((int) a[1]).compareTo(strs.get((int) b[1])));
            int i = 0;
            while (i < ys.size()) {
                int y = (int) ys.get(i)[0];
                String s = strs.get((int) ys.get(i)[1]);
                int j = i;
                while (j + 1 < ys.size() && ys.get(j + 1)[0] == ys.get(j)[0] + 1 && strs.get((int) ys.get(j + 1)[1]).equals(s)) j++;
                runs.add(new Run(y, (int) ys.get(j)[0], s));
                i = j + 1;
            }
            int col = (cz(ck) - z0) * w + (cx(ck) - x0);
            rows.put(col, runs);
        }
        byte[] counts = new byte[w * d];
        LinkedHashMap<String, Integer> palette = new LinkedHashMap<>();
        List<int[]> vals = new ArrayList<>();
        int n = 0;
        for (Map.Entry<Integer, List<Run>> e : rows.entrySet()) {
            List<Run> runs = e.getValue();
            if (runs.size() > 255) runs = runs.subList(runs.size() - 255, runs.size());
            counts[e.getKey()] = (byte) runs.size();
            for (Run r : runs) {
                Integer p = palette.get(r.s);
                if (p == null) {
                    p = palette.size();
                    palette.put(r.s, p);
                }
                vals.add(new int[]{r.y0, r.y1, p});
            }
            n += runs.size();
        }
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try {
            java.io.DataOutputStream o = new java.io.DataOutputStream(raw);
            o.write("DVCR".getBytes(StandardCharsets.US_ASCII));
            o.writeInt(1);
            o.writeInt(x0);
            o.writeInt(z0);
            o.writeInt(w);
            o.writeInt(d);
            o.writeInt(palette.size());
            for (String s : palette.keySet()) {
                byte[] bs = s.getBytes(StandardCharsets.UTF_8);
                o.writeShort(bs.length);
                o.write(bs);
            }
            o.writeInt(n);
            o.write(counts);
            for (int[] v : vals) o.writeShort(v[0]);
            for (int[] v : vals) o.writeShort(v[1]);
            for (int[] v : vals) o.writeShort(v[2]);
            o.flush();
        } catch (IOException ex) {                     // (a byte array: never)
            throw new java.io.UncheckedIOException(ex);
        }
        return new Encoded(raw.toByteArray(), n, blocks.size());
    }

    /** Write a payload as the .dvcr file export writes (via a .tmp sibling, then moved into place). */
    public static void write(Encoded e, Path path) throws IOException {
        String fname = path.getFileName().toString();
        int dot = fname.lastIndexOf('.');
        String tmpName = (dot > 0 ? fname.substring(0, dot) : fname) + ".tmp";
        Path tmp = path.resolveSibling(tmpName);
        try (OutputStream out = Files.newOutputStream(tmp)) {
            out.write(gzip(e.payload, tmpName, System.currentTimeMillis() / 1000));
        }
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /** The bytes Python's {@code gzip.open(<dir>/<fname>, "wb", compresslevel=3)} writes for data. */
    static byte[] gzip(byte[] data, String fname, long mtime) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(0x1f);
        b.write(0x8b);
        b.write(8);
        b.write(8);                                    // FNAME
        for (int i = 0; i < 4; i++) b.write((int) (mtime >>> (8 * i)) & 0xff);
        b.write(0);                                    // xfl (level 3: neither best nor fast)
        b.write(255);                                  // os: unknown
        byte[] fn = fname.getBytes(StandardCharsets.ISO_8859_1);
        b.write(fn, 0, fn.length);
        b.write(0);
        Deflater def = new Deflater(3, true);
        def.setInput(data);
        def.finish();
        byte[] buf = new byte[65536];
        while (!def.finished()) {
            int k = def.deflate(buf);
            b.write(buf, 0, k);
        }
        def.end();
        CRC32 crc = new CRC32();
        crc.update(data);
        long c = crc.getValue();
        for (int i = 0; i < 4; i++) b.write((int) (c >>> (8 * i)) & 0xff);
        long sz = data.length & 0xffffffffL;
        for (int i = 0; i < 4; i++) b.write((int) (sz >>> (8 * i)) & 0xff);
        return b.toByteArray();
    }
}
