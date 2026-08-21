package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.dce.ed.ui.tabdock.FloatingTabFrame;

class OverlayNativePassThroughPolicyTest {

    @Test
    void selectiveNeverUsesWholeWindowNativePassThrough() {
        assertFalse(OverlayFrame.shouldStampNativeMousePassThrough(
                MouseInteractionMode.SELECTIVE, false));
        assertFalse(OverlayFrame.shouldStampNativeMousePassThrough(
                MouseInteractionMode.SELECTIVE, true));
    }

    @Test
    void fullPassThroughUsesNativeFlagExceptOverInteractiveEscapeControls() {
        assertTrue(OverlayFrame.shouldStampNativeMousePassThrough(
                MouseInteractionMode.FULL_PASS_THROUGH, false));
        assertFalse(OverlayFrame.shouldStampNativeMousePassThrough(
                MouseInteractionMode.FULL_PASS_THROUGH, true));
    }

    @Test
    void normalNeverUsesWholeWindowNativePassThrough() {
        assertFalse(OverlayFrame.shouldStampNativeMousePassThrough(
                MouseInteractionMode.NORMAL, false));
    }

    @Test
    void detachedWindowCardHitRegionsApplyOnlyInSelectiveMode() {
        assertTrue(FloatingTabFrame.shouldUseCardSpecificHitRegion(MouseInteractionMode.SELECTIVE));
        assertFalse(FloatingTabFrame.shouldUseCardSpecificHitRegion(MouseInteractionMode.FULL_PASS_THROUGH));
        assertFalse(FloatingTabFrame.shouldUseCardSpecificHitRegion(MouseInteractionMode.NORMAL));
    }

    @Test
    void titleBarDwellActivationAppliesOnlyInFullPassThrough() {
        assertTrue(OverlayFrame.shouldUseTitleBarDwellController(MouseInteractionMode.FULL_PASS_THROUGH));
        assertFalse(OverlayFrame.shouldUseTitleBarDwellController(MouseInteractionMode.SELECTIVE));
        assertFalse(OverlayFrame.shouldUseTitleBarDwellController(MouseInteractionMode.NORMAL));
    }
}
