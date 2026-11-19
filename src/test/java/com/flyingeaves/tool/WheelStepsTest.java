package com.flyingeaves.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** The value cycling of the 单体建造 wheel tools (no game needed). */
class WheelStepsTest {
    @Test
    void cycleWrapsBothWays() {
        assertEquals(1, WheelSteps.cycle(0, 1, 4));
        assertEquals(0, WheelSteps.cycle(3, 1, 4));          // 14° -> 45°
        assertEquals(3, WheelSteps.cycle(0, -1, 4));         // 45° -> 14°
        assertEquals(9, WheelSteps.cycle(0, -1, 10));        // margin 自动 -> 20
        assertEquals(0, WheelSteps.cycle(5, 1, 0));          // (no options yet)
        int v = 0;                                           // a whole turn comes back
        for (int i = 0; i < 16; i++) v = WheelSteps.cycle(v, 1, 16);
        assertEquals(0, v);
    }

    @Test
    void shapesRoundTripAsTheRoomTable() {
        assertEquals(0, WheelSteps.shapeIndex(false, 0));    // 矩形
        assertEquals(1, WheelSteps.shapeIndex(true, 0));     // 圆塔
        assertEquals(2, WheelSteps.shapeIndex(false, 1));    // 牌楼
        assertEquals(2, WheelSteps.shapeIndex(true, 1));     // (a special is not round)
        for (int v = 0; v < 16; v++) {
            int[] s = WheelSteps.shapeOf(v);
            assertEquals(v, WheelSteps.shapeIndex(s[0] == 1, s[1]));
        }
        assertArrayEquals(new int[]{1, 0}, WheelSteps.shapeOf(1));
        assertArrayEquals(new int[]{0, 14}, WheelSteps.shapeOf(15));   // 地块
        assertArrayEquals(new int[]{0, 0}, WheelSteps.shapeOf(WheelSteps.cycle(15, 1, 16)));
    }

    @Test
    void floorUnderTheCrosshair() {
        // frame from y + 1 = 65, 3 storeys of 6
        assertEquals(0, WheelSteps.floorAt(65.0, 64, 6, 3));
        assertEquals(0, WheelSteps.floorAt(70.9, 64, 6, 3));
        assertEquals(1, WheelSteps.floorAt(71.0, 64, 6, 3));
        assertEquals(2, WheelSteps.floorAt(83.0, 64, 6, 3));   // the frame's top: the top floor
        assertEquals(2, WheelSteps.floorAt(99.0, 64, 6, 3));
        assertEquals(0, WheelSteps.floorAt(10.0, 64, 6, 3));
        assertEquals(0, WheelSteps.floorAt(70.0, 64, 6, 1));
    }

    @Test
    void storeyHeightReachesAuto() {
        assertEquals(7, WheelSteps.storeyHeight(0, 6, 1));      // 自动 (6) up
        assertEquals(5, WheelSteps.storeyHeight(0, 6, -1));
        assertEquals(4, WheelSteps.storeyHeight(5, 6, -1));
        assertEquals(0, WheelSteps.storeyHeight(4, 6, -1));     // set to 4, down once more: 自动 again
        assertEquals(4, WheelSteps.storeyHeight(0, 4, -1));     // (自动 already at 4: stays at 4, set)
        assertEquals(16, WheelSteps.storeyHeight(16, 6, 1));
    }
}
