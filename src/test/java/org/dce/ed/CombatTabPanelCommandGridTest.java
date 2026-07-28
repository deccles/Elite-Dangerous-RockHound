package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CombatTabPanelCommandGridTest {

    @Test
    void usesMaxColumnsWhenCellsFit() {
        // 4 * 100 + 3 * 4 = 412
        assertEquals(4, CombatTabPanel.commandGridColumns(412, 100, 8, 4));
    }

    @Test
    void dropsColumnsUntilBindingTextFits() {
        // 3 cols need 100*3 + 4*2 = 308; only 250 available → 2 cols (100*2+4=204)
        assertEquals(2, CombatTabPanel.commandGridColumns(250, 100, 8, 4));
    }

    @Test
    void fallsBackToSingleColumnWhenTight() {
        assertEquals(1, CombatTabPanel.commandGridColumns(90, 100, 8, 4));
    }

    @Test
    void unknownWidthUsesSingleColumnSoBindingsStayReadable() {
        assertEquals(1, CombatTabPanel.commandGridColumns(0, 120, 8, 4));
    }

    @Test
    void neverExceedsButtonCount() {
        assertEquals(2, CombatTabPanel.commandGridColumns(800, 50, 2, 4));
    }
}
