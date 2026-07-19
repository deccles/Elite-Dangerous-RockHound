package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MouseInteractionModeTest {

    @Test
    void cyclesNormalSelectiveFull() {
        assertEquals(MouseInteractionMode.SELECTIVE, MouseInteractionMode.NORMAL.next());
        assertEquals(MouseInteractionMode.FULL_PASS_THROUGH, MouseInteractionMode.SELECTIVE.next());
        assertEquals(MouseInteractionMode.NORMAL, MouseInteractionMode.FULL_PASS_THROUGH.next());
    }

    @Test
    void passThroughLike() {
        assertFalse(MouseInteractionMode.NORMAL.isPassThroughLike());
        assertTrue(MouseInteractionMode.SELECTIVE.isPassThroughLike());
        assertTrue(MouseInteractionMode.FULL_PASS_THROUGH.isPassThroughLike());
    }

    @Test
    void hoverControlsOnlyRunInFullPassThrough() {
        MouseInteractionMode original = OverlayPreferences.getOverlayMouseInteractionMode();
        try {
            OverlayPreferences.setOverlayMouseInteractionMode(MouseInteractionMode.NORMAL);
            assertFalse(OverlayPreferences.isOverlayFullMousePassThrough());
            OverlayPreferences.setOverlayMouseInteractionMode(MouseInteractionMode.SELECTIVE);
            assertTrue(OverlayPreferences.isOverlayMousePassThroughToGame());
            assertFalse(OverlayPreferences.isOverlayFullMousePassThrough());
            OverlayPreferences.setOverlayMouseInteractionMode(MouseInteractionMode.FULL_PASS_THROUGH);
            assertTrue(OverlayPreferences.isOverlayFullMousePassThrough());
        } finally {
            OverlayPreferences.setOverlayMouseInteractionMode(original);
        }
    }

    @Test
    void prefsRoundTrip() {
        assertEquals(MouseInteractionMode.NORMAL,
                MouseInteractionMode.fromPrefsValue("normal", MouseInteractionMode.SELECTIVE));
        assertEquals(MouseInteractionMode.SELECTIVE,
                MouseInteractionMode.fromPrefsValue("selective", MouseInteractionMode.NORMAL));
        assertEquals(MouseInteractionMode.FULL_PASS_THROUGH,
                MouseInteractionMode.fromPrefsValue("full", MouseInteractionMode.NORMAL));
        assertEquals(MouseInteractionMode.FULL_PASS_THROUGH,
                MouseInteractionMode.fromPrefsValue("true", MouseInteractionMode.NORMAL));
        assertEquals(MouseInteractionMode.NORMAL,
                MouseInteractionMode.fromPrefsValue("false", MouseInteractionMode.FULL_PASS_THROUGH));
    }
}
