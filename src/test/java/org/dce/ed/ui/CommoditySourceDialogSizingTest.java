package org.dce.ed.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.TableCellRenderer;

import org.junit.jupiter.api.Test;

class CommoditySourceDialogSizingTest {
    @Test
    void scheduleInitialSearch_runsForPopulatedCurrentSystem() throws Exception {
        AtomicInteger searches = new AtomicInteger();

        CommoditySourceDialog.scheduleInitialSearch("Sol", searches::incrementAndGet);
        SwingUtilities.invokeAndWait(() -> { });

        assertEquals(1, searches.get());
    }

    @Test
    void configureResultsTable_sizesRowsForActiveFont() {
        JTable table = new JTable(new Object[][] { { "Gutenberg Vision", "Col 285 Sector OK-P a35-2" } },
                new Object[] { "Station", "System" });
        Font font = new Font(Font.MONOSPACED, Font.PLAIN, 22);

        CommoditySourceDialog.configureResultsTable(table, font);

        assertTrue(table.getRowHeight() >= table.getFontMetrics(font).getHeight() + 4);
        assertTrue(table.getTableHeader().getPreferredSize().height
                >= table.getTableHeader().getFontMetrics(font.deriveFont(Font.BOLD)).getHeight() + 4);
    }

    @Test
    void configureSupplyRenderer_colorsOnlyInsufficientUnselectedSupplyRed() {
        JTable table = new JTable(
                new Object[][] { { "Low", 40 }, { "Enough", 42 }, { "High", 100 } },
                new Object[] { "Station", "Supply" });
        CommoditySourceDialog.configureSupplyRenderer(table, 1, 42);
        TableCellRenderer renderer = table.getColumnModel().getColumn(1).getCellRenderer();

        Component low = renderer.getTableCellRendererComponent(table, 40, false, false, 0, 1);
        assertEquals(Color.RED, low.getForeground());
        Component enough = renderer.getTableCellRendererComponent(table, 42, false, false, 1, 1);
        assertNotEquals(Color.RED, enough.getForeground());
        Component selectedLow = renderer.getTableCellRendererComponent(table, 40, true, false, 0, 1);
        assertEquals(table.getSelectionForeground(), selectedLow.getForeground());
    }
}
