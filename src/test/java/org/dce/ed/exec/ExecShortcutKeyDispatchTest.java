package org.dce.ed.exec;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.JTextField;

import org.junit.jupiter.api.Test;

class ExecShortcutKeyDispatchTest {

    @Test
    void dispatchesWhenEliteIsForegroundEvenIfOverlayReportsFocus() {
        assertTrue(ExecShortcutKeyDispatch.shouldDispatch(true, true, new JTextField()));
    }

    @Test
    void blocksWhenOverlayHasFocusAndEliteDoesNot() {
        assertFalse(ExecShortcutKeyDispatch.shouldDispatch(false, true, new JTextField()));
    }

    @Test
    void dispatchesWhenNeitherEliteNorOverlayHasFocus() {
        assertTrue(ExecShortcutKeyDispatch.shouldDispatch(false, false, null));
    }
}
