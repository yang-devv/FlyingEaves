package com.flyingeaves.tool;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The compact undo record against the map it replaced (a BlockPos -> state map filled with putIfAbsent). */
class CellLogTest {
    private static long pos(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (y & 0xFFF);
    }

    private static Map<Long, String> dump(CellLog<String> log) {
        Map<Long, String> out = new HashMap<>();
        log.forEach((x, y, z, s) -> assertTrue(out.put(pos(x, y, z), s) == null, "a cell twice"));
        return out;
    }

    @Test
    void firstPutWinsAndMatchesTheMap() {
        Random rnd = new Random(7);
        CellLog<String> log = new CellLog<>(-64);
        Map<Long, String> model = new HashMap<>();
        for (int cz = -3; cz <= 2; cz++) {
            for (int cx = -2; cx <= 3; cx++) {
                for (int i = 0; i < 3000; i++) {             // (a stamp writing one chunk: repeats, then sealed)
                    int x = (cx << 4) + rnd.nextInt(16), z = (cz << 4) + rnd.nextInt(16), y = -64 + rnd.nextInt(384);
                    String s = "s" + rnd.nextInt(40);
                    log.put(x, y, z, s);
                    model.putIfAbsent(pos(x, y, z), s);
                }
                log.seal(cx, cz);
            }
        }
        for (int i = 0; i < 20000; i++) {                    // (door steps: after the chunks were sealed, anywhere)
            int x = -40 + rnd.nextInt(110), z = -56 + rnd.nextInt(100), y = -64 + rnd.nextInt(384);
            assertEquals(model.containsKey(pos(x, y, z)), log.contains(x, y, z));
            String s = "t" + rnd.nextInt(5);
            log.putIfAbsent(x, y, z, s);
            model.putIfAbsent(pos(x, y, z), s);
        }
        log.sealAll();
        assertEquals(model.size(), log.size());
        assertEquals(model, dump(log));
        for (Map.Entry<Long, String> e : model.entrySet()) {
            long p = e.getKey();
            int x = (int) (p >> 38), y = (int) (p << 52 >> 52), z = (int) (p << 26 >> 38);
            assertTrue(log.contains(x, y, z));
        }
        assertFalse(log.contains(1000, 0, 1000));
    }

    @Test
    void bigPalettes() {
        CellLog<Integer> log = new CellLog<>(0);
        for (int i = 0; i < 70000; i++) log.put(i % 16, i / 256, i / 16 % 16, i);   // (palette past 0xFFFF)
        log.put(3, 0, 0, -1);                                                    // a repeat: the first stays
        log.sealAll();
        assertEquals(70000, log.size());
        long[] sum = {0};
        log.forEach((x, y, z, v) -> {
            assertEquals(y * 256 + z * 16 + x, (int) v);
            sum[0] += v;
        });
        assertEquals(70000L * 69999 / 2, sum[0]);
    }

    @Test
    void removeInAndDrop() {
        CellLog<String> log = new CellLog<>(-64);
        for (int x = -20; x < 20; x++) for (int z = -20; z < 20; z++) log.put(x, 5, z, x + "," + z);
        log.sealAll();
        int[] n = {0};
        long removed = log.removeIn(-5, -5, 4, 4, (x, y, z, s) -> {
            assertEquals(x + "," + z, s);
            n[0]++;
        });
        assertEquals(100, removed);
        assertEquals(100, n[0]);
        assertEquals(1600 - 100, log.size());
        assertFalse(log.contains(0, 5, 0));
        assertTrue(log.contains(-6, 5, 0));
        assertEquals(16, log.chunkKeys().length);
        log.drop(-1, -1);                                   // (x, z -16..-1: 256 cells, 25 of them removed above)
        assertEquals(1600 - 100 - 231, log.size());
        assertEquals(15, log.chunkKeys().length);
    }
}
