package com.flyingeaves.tool;

/**
 * 滚轮工具改值的算法（不碰游戏，单元测试直接测）：一格一格地转、形状的序号、准星指着第几层、层高。
 */
final class WheelSteps {
    private WheelSteps() {
    }

    /** n 个选项里往上（d = 1）或往下（d = -1）转一格，转到头接回另一头。 */
    static int cycle(int cur, int d, int n) {
        return n <= 0 ? 0 : Math.floorMod(cur + d, n);
    }

    /** 形状一栏（{@link Compose#shapes()}）的序号：0 矩形、1 圆塔、2.. 是 SPECIALS[1..]。 */
    static int shapeIndex(boolean round, int special) {
        return special > 0 ? special + 1 : round ? 1 : 0;
    }

    /** 形状序号 v 对应的 {round ? 1 : 0, special}（和房间表的「形状」一样：圆塔不是 special，special 不算圆）。 */
    static int[] shapeOf(int v) {
        return new int[]{v == 1 ? 1 : 0, v >= 2 ? v - 1 : 0};
    }

    /**
     * 准星碰到线框的高度 py 是第几层（0 = 一楼）：按线框量（线框从 y + 1 起，每层 sh 格高，不含台基），
     * 所以每一层都指得到；超出的算最上、最下一层。
     */
    static int floorAt(double py, int y, int sh, int storeys) {
        int f = (int) Math.floor((py - (y + 1)) / Math.max(1, sh));
        return Math.max(0, Math.min(storeys - 1, f));
    }

    /**
     * 层高滚一格：4–16 格；单独调过的（sh &gt; 0）从 4 再往下滚回「自动」（0 = 跟整栋），
     * 自动的从整栋的层高（autoSh）起往上、往下滚。
     */
    static int storeyHeight(int sh, int autoSh, int d) {
        int v = (sh > 0 ? sh : autoSh) + d;
        if (sh > 0 && v < 4) return 0;
        return Math.max(4, Math.min(16, v));
    }
}
