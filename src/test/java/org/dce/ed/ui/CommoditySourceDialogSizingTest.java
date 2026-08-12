package org.dce.ed.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;

import javax.swing.JTable;

import org.junit.jupiter.api.Test;

class CommoditySourceDialogSizingTest {
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
}
