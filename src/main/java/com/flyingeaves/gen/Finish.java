package com.flyingeaves.gen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.flyingeaves.gen.Blocks.AIR;
import static com.flyingeaves.gen.Blocks.B;
import static com.flyingeaves.gen.Blocks.containsAny;

/**
 * The finishing passes over a whole canvas (world coordinates): bgen._openings, walkout, clear_doorways,
 * settle_loose; compose.connect_panes, _in_wall.
 */
public final class Finish {
    private Finish() {
    }

    static final String[] DOORWAY_JUNK = {"fence", "_wall", "potted_", "flower_pot", "decorated_pot", "hay_block", "barrel", "lantern", "candle",
            "campfire", "chest", "cauldron", "composter", "crafting_table", "bookshelf", "grindstone", "anvil", "lectern", "loom", "smoker", "furnace",
            "bell", "button", "chain", "sign", "banner", "bed", "leaves", "trapdoor"};
    static final String[] DOORWAY_JUNK_TORCH;

    static {
        DOORWAY_JUNK_TORCH = java.util.Arrays.copyOf(DOORWAY_JUNK, DOORWAY_JUNK.length + 1);
        DOORWAY_JUNK_TORCH[DOORWAY_JUNK.length] = "torch";
    }

    private static final Map<String, Boolean> SOLID_MEMO = new java.util.HashMap<>();
    static final String[] NOT_SOLID_WALL = {"lantern", "candle", "fence", "torch", "chain", "pane", "bars", "door", "potted", "flower", "banner", "sign",
            "trapdoor", "slab", "stairs", "carpet", "plate", "light[", "spirit_light", "button", "ladder", "leaves"};

    /** bgen._solid_wall. */
    static boolean solidWall(String st) {
        if (st == null || st.isEmpty()) return false;
        Boolean r = SOLID_MEMO.get(st);
        if (r == null) {
            r = !st.equals(AIR) && !containsAny(st, NOT_SOLID_WALL);
            SOLID_MEMO.put(st, r);
        }
        return r;
    }

    static final String[] NOT_A_SILL = {"composter", "barrel", "chest", "cauldron", "bookshelf", "furnace", "smoker", "crafting_table", "loom",
            "lectern", "grindstone", "anvil", "hay_block", "beehive", "jukebox", "note_block", "_bed", "cartography", "fletching", "smithing",
            "stonecutter", "brewing_stand", "decorated_pot", "bell"};

    /** A way through a wall: the cell and the way through (dx, dz). */
    record Opening(int x, int y, int z, int dx, int dz) {
    }

    static String dirOf(int dx, int dz) {
        return dx == 1 ? "east" : dx == -1 ? "west" : dz == 1 ? "south" : "north";
    }

    static boolean isLowerDoor(String b) {
        return b.contains("_door[") && b.contains("half=lower") && !b.contains("trapdoor");
    }

    static String facingOf(String b) {
        return b.split("facing=", -1)[1].split(",", -1)[0].split("]", -1)[0];
    }

    /** bgen._openings. */
    static List<Opening> openings(Map<Long, String> B) {
        List<Opening> out = new ArrayList<>();
        for (Map.Entry<Long, String> e : B.entrySet()) {
            long k = e.getKey();
            String b = e.getValue();
            int x = Canvas.kx(k), y = Canvas.ky(k), z = Canvas.kz(k);
            if (isLowerDoor(b)) {
                String f = facingOf(b);
                out.add(f.equals("north") || f.equals("south") ? new Opening(x, y, z, 0, 1) : new Opening(x, y, z, 1, 0));
                continue;
            }
            if (!solidWall(b) || containsAny(b, NOT_A_SILL)) continue;
            int y1 = y + 1;
            boolean blocked = false;
            for (int kk = 0; kk < 2; kk++) {
                String s = B.get(Canvas.key(x, y1 + kk, z));
                if (solidWall(s) || (s != null && s.contains("_door"))) blocked = true;
            }
            if (blocked) continue;
            if (!(solidWall(B.get(Canvas.key(x, y1 + 2, z))) || solidWall(B.get(Canvas.key(x, y1 + 3, z))))) continue;
            for (int[] d : new int[][]{{1, 0}, {0, 1}}) {
                boolean all = true;
                for (int s : new int[]{1, -1})
                    for (int kk = 0; kk < 2; kk++) if (!solidWall(B.get(Canvas.key(x + s * d[0], y1 + kk, z + s * d[1])))) all = false;
                if (all) out.add(new Opening(x, y1, z, d[1], d[0]));
            }
        }
        return out;
    }

    static final String[] OVER_SOFT = {"air", "light", "carpet", "pressure_plate", "_door", "button", "torch", "flower", "sapling", "grass[", "fern",
            "snow[", "rail", "lever", "sign", "banner", "vine", "tripwire", "lantern", "chain", "candle", "potted", "leaves"};

    /** A way out found by walkout: its foot cell (Canvas.key) and the low cells over it. */
    record Low(long foot, List<Long> low) {
    }

    /** bgen.walkout. */
    static List<Low> walkout(Map<Long, String> blocks, Map<Long, List<Canvas.Run>> cols, int ground, int reach) {
        class S {
            boolean solid(int x, int y, int z) {
                String s = blocks.get(Canvas.key(x, y, z));
                if (s == null) {
                    if (y <= ground) return true;
                    List<Canvas.Run> rs = cols.get(Canvas.ckey(x, z));
                    if (rs != null) for (Canvas.Run r : rs) if (r.y0() <= y && y <= r.y1() && !r.s().equals(AIR)) return true;
                    return false;
                }
                return !containsAny(s, OVER_SOFT);
            }
        }
        S S = new S();
        List<Object[]> ways = new ArrayList<>();      // (x, y, z, ax0, ax1, ks)
        for (Map.Entry<Long, String> e : blocks.entrySet()) {
            String b = e.getValue();
            if (isLowerDoor(b)) {
                String f = facingOf(b);
                long k = e.getKey();
                ways.add(new Object[]{Canvas.kx(k), Canvas.ky(k), Canvas.kz(k), (f.equals("east") || f.equals("west")) ? 1 : 0,
                        (f.equals("east") || f.equals("west")) ? 0 : 1, new int[]{0, 1, -1}});
            }
        }
        for (Opening o : openings(blocks)) {
            String here = blocks.get(Canvas.key(o.x, o.y, o.z));
            if ((here == null || !here.contains("_door")) && !S.solid(o.x, o.y, o.z) && !S.solid(o.x, o.y + 1, o.z)) {
                boolean glass = false;
                for (int kk = 0; kk < 2; kk++) {
                    String s = blocks.get(Canvas.key(o.x, o.y + kk, o.z));
                    if (s != null && (s.contains("pane") || s.contains("bars"))) glass = true;
                }
                if (!glass) ways.add(new Object[]{o.x, o.y, o.z, o.dx, o.dz, new int[]{0}});
            }
        }
        List<Low> out = new ArrayList<>();
        for (Object[] w : ways) {
            int x = (int) w[0], y = (int) w[1], z = (int) w[2], ax0 = (int) w[3], ax1 = (int) w[4];
            int[] ks = (int[]) w[5];
            int sx0 = ax1, sx1 = ax0;
            List<Long> low = new ArrayList<>();
            for (int sgn : new int[]{1, -1})
                for (int kk : ks)
                    for (int d = kk == 0 ? 1 : 2; d < reach + 1; d++) {
                        int cx = x + sgn * d * ax0 + kk * sx0, cz = z + sgn * d * ax1 + kk * sx1;
                        String here = blocks.get(Canvas.key(cx, y, cz));
                        if (S.solid(cx, y, cz) || (here != null && here.contains("_door"))) break;
                        int s = y;
                        while (s > y - 4 && !S.solid(cx, s - 1, cz)) s--;
                        if (S.solid(cx, s, cz)) break;
                        if (S.solid(cx + sx0, s, cz + sx1) && S.solid(cx - sx0, s, cz - sx1)) break;
                        if (!S.solid(cx, s + 1, cz) && S.solid(cx, s + 2, cz)) low.add(Canvas.key(cx, s + 2, cz));
                        else if (S.solid(cx, s + 1, cz)) {
                            String b1 = blocks.get(Canvas.key(cx, s + 1, cz));
                            if (d >= 2 && ((b1 != null && b1.contains("slab")) || !S.solid(cx, s + 2, cz))) {
                                low.add(Canvas.key(cx, s + 1, cz));
                                if (S.solid(cx, s + 2, cz)) low.add(Canvas.key(cx, s + 2, cz));
                            } else break;
                        }
                    }
            if (!low.isEmpty()) out.add(new Low(Canvas.key(x, y, z), low));
        }
        return out;
    }

    /** bgen.clear_doorways: returns how many cells were cleared. */
    static int clearDoorways(Canvas cv) {
        int n = 0;
        for (Opening o : openings(cv.blocks)) {
            int x = o.x, y = o.y, z = o.z, dx = o.dx, dz = o.dz;
            for (int sgn : new int[]{1, -1}) {
                int cx = x + sgn * dx, cz = z + sgn * dz;
                for (int kk = 0; kk < 2; kk++) {
                    String c = cv.blocks.get(Canvas.key(cx, y + kk, cz));
                    if (c == null || c.isEmpty() || c.equals(AIR) || c.contains("carpet") || c.contains("pressure_plate") || c.contains("_door")) continue;
                    String above = cv.blocks.get(Canvas.key(cx, y + kk + 1, cz));
                    if (above == null) above = "";
                    boolean junk = containsAny(c, DOORWAY_JUNK);
                    boolean pedestal = kk == 0 && containsAny(above, "potted_", "flower_pot", "decorated_pot", "lantern", "candle");
                    if (junk || pedestal) {
                        cv.blocks.put(Canvas.key(cx, y + kk, cz), AIR);
                        n++;
                        if (pedestal || (kk == 0 && containsAny(above, DOORWAY_JUNK))) {
                            cv.blocks.put(Canvas.key(cx, y + kk + 1, cz), AIR);
                            n++;
                        }
                    }
                }
                String door = cv.blocks.get(Canvas.key(x, y, z));
                if (door == null) door = "";
                boolean inRun = false;
                List<Canvas.Run> rs = cv.cols.get(Canvas.ckey(cx, cz));
                if (rs != null) for (Canvas.Run r : rs) if (r.y0() <= y + 1 && y + 1 <= r.y1() && !r.s().equals(AIR)) inRun = true;
                String head = cv.blocks.get(Canvas.key(cx, y + 1, cz));
                if (door.contains("_door[") && door.contains("half=lower") && (solidWall(head) || (head == null && inRun))) {
                    int al0 = dz, al1 = dx;
                    String fill = null;
                    outer:
                    for (int s : new int[]{1, -1})
                        for (int kk = 0; kk < 2; kk++) {
                            String c = cv.blocks.get(Canvas.key(x + s * al0, y + kk, z + s * al1));
                            if (solidWall(c)) {
                                fill = c;
                                break outer;
                            }
                        }
                    if (fill == null) fill = B("stone_bricks");
                    cv.blocks.put(Canvas.key(x, y, z), fill);
                    String up = cv.blocks.get(Canvas.key(x, y + 1, z));
                    if (up != null && up.contains("_door[")) cv.blocks.put(Canvas.key(x, y + 1, z), fill);
                    n++;
                }
                for (int yy = y + 1; yy < y + 7; yy++) {
                    String c = cv.blocks.get(Canvas.key(cx, yy, cz));
                    if (c == null) c = "";
                    if (AIR.equals(cv.blocks.get(Canvas.key(cx, yy - 1, cz))) && !c.contains("hanging=true") && containsAny(c, DOORWAY_JUNK_TORCH)) {
                        cv.blocks.put(Canvas.key(cx, yy, cz), AIR);
                        n++;
                    }
                }
            }
        }
        return n;
    }

    static final String[] STANDS = {"lantern[hanging=false", "candle", "flower_pot", "potted_", "_button[face=floor"};

    /** bgen.settle_loose. */
    static int settleLoose(Canvas cv) {
        int n = 0;
        for (Map.Entry<Long, String> e : new ArrayList<>(cv.blocks.entrySet())) {
            String s = e.getValue();
            long k = e.getKey();
            int x = Canvas.kx(k), y = Canvas.ky(k), z = Canvas.kz(k);
            if (s == null || s.isEmpty() || !containsAny(s, STANDS) || s.contains("hanging=true")) continue;
            String below = cv.blocks.get(Canvas.key(x, y - 1, z));
            if (!(below == null || below.equals(AIR))) continue;
            List<Canvas.Run> rs = cv.cols.get(Canvas.ckey(x, z));
            if (rs != null) {
                boolean any = false;
                for (Canvas.Run r : rs) if (r.y0() <= y - 1 && y - 1 <= r.y1()) any = true;
                if (any) continue;
            }
            cv.blocks.put(k, AIR);
            String b2 = cv.blocks.get(Canvas.key(x, y - 2, z));
            if (!(b2 == null || b2.equals(AIR))) cv.blocks.put(Canvas.key(x, y - 1, z), s);
            n++;
        }
        return n;
    }

    // ------------------------------------------------------------------------------------------------ panes

    static final String[] NOT_SOLID = {"air", "stairs", "slab", "trapdoor", "door", "fence", "pane", "bars", "_wall", "button", "lantern", "chain",
            "torch", "carpet", "banner", "sign", "flower", "potted", "leaves", "ladder", "rail", "plate", "light["};

    static boolean empty(String c) {
        return c == null || c.equals("minecraft:air");
    }

    /** compose._in_wall. */
    static boolean inWall(Map<Long, String> blocks, int x, int y, int z) {
        for (int[] d : new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) {
            String nb = blocks.get(Canvas.key(x + d[0], y, z + d[1]));
            if (nb != null && !nb.isEmpty() && (Blocks.name(nb).endsWith("_pane") || !containsAny(nb, NOT_SOLID))
                    && empty(blocks.get(Canvas.key(x + d[0], y, z))) && empty(blocks.get(Canvas.key(x, y, z + d[1])))) return true;
        }
        return false;
    }

    /** compose.connect_panes. */
    static void connectPanes(Map<Long, String> blocks) {
        for (Map.Entry<Long, String> e : new ArrayList<>(blocks.entrySet())) {
            String st = e.getValue();
            String n = Blocks.name(st);
            if (!(n.endsWith("_pane") || n.endsWith("iron_bars"))) continue;
            long k = e.getKey();
            int x = Canvas.kx(k), y = Canvas.ky(k), z = Canvas.kz(k);
            LinkedHashMap<String, String> props = st.contains("[") ? Blocks.parse(st).props() : new LinkedHashMap<>();
            Object[][] ds = {{"north", 0, -1}, {"south", 0, 1}, {"east", 1, 0}, {"west", -1, 0}};
            for (Object[] d : ds) {
                String nb = blocks.get(Canvas.key(x + (int) d[1], y, z + (int) d[2]));
                String nn = Blocks.name(nb == null ? "" : nb);
                boolean j = nb != null && !nb.isEmpty() && (nn.endsWith("_pane") || nn.endsWith("iron_bars") || !containsAny(nb, NOT_SOLID));
                props.put((String) d[0], j ? "true" : "false");
            }
            if (n.endsWith("_pane")) {
                int cnt = 0;
                for (String d : new String[]{"north", "south", "east", "west"}) if ("true".equals(props.get(d))) cnt++;
                if (cnt < 2 && inWall(blocks, x, y, z)) {
                    blocks.put(k, n.substring(0, n.length() - "_pane".length()));
                    continue;
                }
            }
            blocks.put(k, Blocks.withProps(n, props));
        }
    }
}
