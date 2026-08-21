package org.dce.ed.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OverlayForegroundPolicyTest {

    @Test
    void keepsOverlayTopmostForEliteOrRockHoundForeground() {
        assertTrue(OverlayForegroundPolicy.keepOverlayTopmost(true, false));
        assertTrue(OverlayForegroundPolicy.keepOverlayTopmost(false, true));
        assertFalse(OverlayForegroundPolicy.keepOverlayTopmost(false, false));
    }
}
