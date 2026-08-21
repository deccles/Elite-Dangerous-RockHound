package org.dce.ed.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Point;

import javax.swing.JPanel;

import org.junit.jupiter.api.Test;

class PassThroughTooltipSupportTest {

    @Test
    void stationaryPointerDoesNotRepeatedlyResetSwingTooltipDelay() {
        JPanel target = new JPanel();
        Point position = new Point(40, 25);

        assertFalse(PassThroughTooltipSupport.shouldDispatchMouseMoved(
                target, position, target, new Point(position)));
    }

    @Test
    void movementOrTargetChangeDispatchesMouseMoved() {
        JPanel first = new JPanel();
        JPanel second = new JPanel();

        assertTrue(PassThroughTooltipSupport.shouldDispatchMouseMoved(
                first, new Point(40, 25), first, new Point(41, 25)));
        assertTrue(PassThroughTooltipSupport.shouldDispatchMouseMoved(
                first, new Point(40, 25), second, new Point(40, 25)));
        assertTrue(PassThroughTooltipSupport.shouldDispatchMouseMoved(
                null, null, first, new Point(40, 25)));
    }
}
