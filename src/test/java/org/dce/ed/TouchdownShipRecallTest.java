package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TouchdownShipRecallTest {

    private static final double RADIUS_M = 1_700_000.0;

    @Test
    void onFootRecallTouchdownIsShip() {
        assertTrue(BiologyTabPanel.isUnoccupiedShipTouchdown(
                true,
                false,
                10.0,
                20.0,
                10.001,
                20.001,
                RADIUS_M));
    }

    @Test
    void srvTouchdownAtCommanderIsNotShip() {
        assertFalse(BiologyTabPanel.isUnoccupiedShipTouchdown(
                true,
                true,
                10.0,
                20.0,
                10.0,
                20.0,
                RADIUS_M));
    }

    @Test
    void shipRecallWhileInSrvIsSeparatedFromSrv() {
        assertTrue(BiologyTabPanel.isUnoccupiedShipTouchdown(
                true,
                true,
                10.0,
                20.0,
                10.05,
                20.0,
                RADIUS_M));
    }

    @Test
    void inShipTouchdownIsNotUnoccupiedShipRecall() {
        assertFalse(BiologyTabPanel.isUnoccupiedShipTouchdown(
                false,
                false,
                10.0,
                20.0,
                11.0,
                21.0,
                RADIUS_M));
    }
}
