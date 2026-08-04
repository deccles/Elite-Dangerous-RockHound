package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Point;
import java.awt.Rectangle;

import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

import org.dce.ed.ui.DestinationCopySupport;
import org.junit.jupiter.api.Test;

class MissionsPlacesCopyTest {

    @Test
    void selectPlacesCopyLine_upperHalfPrefersFrom() {
        assertEquals("Sol", MissionsTabPanel.selectPlacesCopyLine("Sol", "Tenjin", true, true));
        assertEquals("Tenjin", MissionsTabPanel.selectPlacesCopyLine("Sol", "Tenjin", true, false));
    }

    @Test
    void selectPlacesCopyLine_courierHidesFrom_alwaysTo() {
        assertEquals("Tenjin", MissionsTabPanel.selectPlacesCopyLine("Sol", "Tenjin", false, true));
        assertEquals("Tenjin", MissionsTabPanel.selectPlacesCopyLine("Sol", "Tenjin", false, false));
    }

    @Test
    void selectPlacesCopyLine_fallsBackWhenPreferredMissing() {
        assertEquals("Tenjin", MissionsTabPanel.selectPlacesCopyLine("", "Tenjin", true, true));
        assertEquals("Sol", MissionsTabPanel.selectPlacesCopyLine("Sol", "", true, false));
        assertEquals("", MissionsTabPanel.selectPlacesCopyLine("—", "—", true, true));
    }

    @Test
    void preferFromHalf_usesUpperCellHalf() {
        JTable table = new JTable(new DefaultTableModel(1, 1));
        table.setRowHeight(40);
        table.setSize(200, 40);
        Rectangle cell = table.getCellRect(0, 0, true);
        assertTrue(DestinationCopySupport.preferFromHalf(table, 0, 0,
                new Point(cell.x + 1, cell.y + 5)));
        assertFalse(DestinationCopySupport.preferFromHalf(table, 0, 0,
                new Point(cell.x + 1, cell.y + cell.height - 5)));
    }
}
