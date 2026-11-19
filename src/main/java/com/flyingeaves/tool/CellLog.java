package com.flyingeaves.tool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The undo record of one placement, compact: the cells a stamp changed and what was there before, grouped by chunk. A
 * cell is an int (its place in the chunk: x, z and y from the bottom of the world) and a palette index (a char, plus a
 * second char only in the rare chunk whose states go past 65 536 palette entries) - 6 bytes a cell where a map of
 * BlockPos to BlockState took about 45 (the 1:1 紫禁城's 26 M cells: ~160 MB instead of ~1.2 GB).
 *
 * <p>Writes append; the first value put for a cell wins (as {@code Map.putIfAbsent}): a chunk is sorted and its repeats
 * dropped when it is {@link #seal sealed} (after the stamp wrote it). Cells put after that (the door steps) go on an
 * unsorted tail that {@link #contains} scans, until the next seal. Not thread-safe (the server thread only).
 *
 * @param <S> what a cell held (a BlockState in the game; anything with equals/hashCode in tests)
 */
public final class CellLog<S> {
    /** The cells of one chunk: keys[0..sorted) sorted and unique, keys[sorted..n) in the order put. */
    private static final class Chunk {
        int n, sorted;
        int[] keys = new int[64];
        char[] lo = new char[64];
        /** The high half of the palette index, only once one past 0xFFFF lands in this chunk. */
        char[] hi;

        void add(int key, int idx) {
            if (n == keys.length) grow(n + (n >> 1) + 16);
            keys[n] = key;
            lo[n] = (char) idx;
            if (idx > 0xFFFF && hi == null) hi = new char[keys.length];
            if (hi != null) hi[n] = (char) (idx >>> 16);
            n++;
        }

        int idx(int i) {
            return hi == null ? lo[i] : lo[i] | hi[i] << 16;
        }

        void grow(int cap) {
            keys = Arrays.copyOf(keys, cap);
            lo = Arrays.copyOf(lo, cap);
            if (hi != null) hi = Arrays.copyOf(hi, cap);
        }

        boolean contains(int key) {
            if (Arrays.binarySearch(keys, 0, sorted, key) >= 0) return true;
            for (int i = sorted; i < n; i++) if (keys[i] == key) return true;
            return false;
        }

        /** Sort by key, the first put of a key kept; trimmed to size. */
        void seal() {
            if (sorted == n && keys.length == n) return;
            long[] order = new long[n];
            for (int i = 0; i < n; i++) order[i] = (long) keys[i] << 32 | i;   // (keys are >= 0: by key, then by when put)
            Arrays.sort(order);
            int m = 0;
            for (int i = 0; i < n; i++) if (i == 0 || order[i] >>> 32 != order[i - 1] >>> 32) m++;
            int[] k2 = new int[m];
            char[] lo2 = new char[m], hi2 = hi == null ? null : new char[m];
            int j = 0;
            for (int i = 0; i < n; i++) {
                if (i > 0 && order[i] >>> 32 == order[i - 1] >>> 32) continue;      // a later put of the same cell
                int at = (int) order[i];
                k2[j] = keys[at];
                lo2[j] = lo[at];
                if (hi2 != null) hi2[j] = hi[at];
                j++;
            }
            keys = k2;
            lo = lo2;
            hi = hi2;
            n = sorted = m;
        }
    }

    /** A cell and what it held. */
    @FunctionalInterface
    public interface Visitor<S> {
        void cell(int x, int y, int z, S old);
    }

    private final int minY;
    private final List<S> palette = new ArrayList<>();
    private final Map<S, Integer> index = new HashMap<>();
    /** By chunk (ChunkPos.asLong), in the order first touched. */
    private final it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap<Chunk> chunks = new it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap<>();

    /** minY: the bottom of the world (cells keep their height above it). */
    public CellLog(int minY) {
        this.minY = minY;
    }

    public static long chunkKey(int cx, int cz) {
        return (long) cx & 0xFFFFFFFFL | ((long) cz & 0xFFFFFFFFL) << 32;      // (as ChunkPos.asLong)
    }

    private int key(int x, int y, int z) {
        return (y - minY) << 8 | (z & 15) << 4 | (x & 15);
    }

    private Chunk chunk(int x, int z, boolean make) {
        long ck = chunkKey(x >> 4, z >> 4);
        Chunk c = chunks.get(ck);
        if (c == null && make) chunks.put(ck, c = new Chunk());
        return c;
    }

    private int paletteIndex(S s) {
        Integer i = index.get(s);
        if (i == null) {
            i = palette.size();
            palette.add(s);
            index.put(s, i);
        }
        return i;
    }

    /** Record what the cell held, unless it already has a record (the first put wins once sealed). */
    public void put(int x, int y, int z, S old) {
        chunk(x, z, true).add(key(x, y, z), paletteIndex(old));
    }

    /** As {@link #put}, but looks first: for cells put after their chunk was sealed (keeps the tail free of repeats). */
    public void putIfAbsent(int x, int y, int z, S old) {
        Chunk c = chunk(x, z, true);
        int k = key(x, y, z);
        if (c.n - c.sorted > 4096) c.seal();
        if (!c.contains(k)) c.add(k, paletteIndex(old));
    }

    public boolean contains(int x, int y, int z) {
        Chunk c = chunk(x, z, false);
        if (c == null) return false;
        if (c.n - c.sorted > 4096) c.seal();
        return c.contains(key(x, y, z));
    }

    /** Sort one chunk's cells and drop the repeats (after the stamp is done with that chunk). */
    public void seal(int cx, int cz) {
        Chunk c = chunks.get(chunkKey(cx, cz));
        if (c != null) c.seal();
    }

    public void sealAll() {
        for (Chunk c : chunks.values()) c.seal();
    }

    /** The chunks with cells (ChunkPos.asLong), in the order first touched. */
    public long[] chunkKeys() {
        return chunks.keySet().toLongArray();
    }

    /** The cells of a chunk, in key order once sealed (call {@link #sealAll} first for the exact set). */
    public void forEach(int cx, int cz, Visitor<S> v) {
        Chunk c = chunks.get(chunkKey(cx, cz));
        if (c == null) return;
        int bx = cx << 4, bz = cz << 4;
        for (int i = 0; i < c.n; i++) {
            int k = c.keys[i];
            v.cell(bx + (k & 15), (k >>> 8) + minY, bz + (k >>> 4 & 15), palette.get(c.idx(i)));
        }
    }

    public void forEach(Visitor<S> v) {
        for (long ck : chunkKeys()) forEach((int) ck, (int) (ck >>> 32), v);
    }

    /** Forget a chunk's cells (restored: the memory goes as the undo goes). */
    public void drop(int cx, int cz) {
        chunks.remove(chunkKey(cx, cz));
    }

    /** The cells in x0..x1, z0..z1 go to v and out of the record (sealed first). Returns how many. */
    public long removeIn(int x0, int z0, int x1, int z1, Visitor<S> v) {
        long n = 0;
        for (long ck : chunkKeys()) {
            int cx = (int) ck, cz = (int) (ck >>> 32);
            if ((cx << 4) + 15 < x0 || cx << 4 > x1 || (cz << 4) + 15 < z0 || cz << 4 > z1) continue;
            Chunk c = chunks.get(ck);
            c.seal();
            int m = 0;
            for (int i = 0; i < c.n; i++) {
                int k = c.keys[i], x = (cx << 4) + (k & 15), z = (cz << 4) + (k >>> 4 & 15);
                if (x >= x0 && x <= x1 && z >= z0 && z <= z1) {
                    v.cell(x, (k >>> 8) + minY, z, palette.get(c.idx(i)));
                    n++;
                    continue;
                }
                c.keys[m] = k;
                c.lo[m] = c.lo[i];
                if (c.hi != null) c.hi[m] = c.hi[i];
                m++;
            }
            c.n = c.sorted = m;
            if (m == 0) chunks.remove(ck);
        }
        return n;
    }

    /** Cells recorded (exact once sealed; before that repeats count too). */
    public long size() {
        long n = 0;
        for (Chunk c : chunks.values()) n += c.n;
        return n;
    }

    public boolean isEmpty() {
        return chunks.isEmpty();
    }

    /** Bytes held by the cell arrays (for the log). */
    public long bytes() {
        long b = 0;
        for (Chunk c : chunks.values()) b += 16 + 4L * c.keys.length + 2L * c.lo.length + (c.hi == null ? 0 : 2L * c.hi.length);
        return b;
    }
}
