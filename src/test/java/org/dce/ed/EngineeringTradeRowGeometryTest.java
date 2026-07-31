package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Rectangle;

import javax.swing.JTable;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

/**
 * Trade Suggestions mixes tall section rows, short gap rows, and normal data rows. {@link JTable}
 * only reports true row offsets once the per-row SizeSequence is populated, so without that the
 * section outline and column-title underline paint on top of neighbouring rows.
 */
class EngineeringTradeRowGeometryTest {

    @Test
    void cellRectMatchesStackedRowHeights() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            EngineeringTabPanel panel = new EngineeringTabPanel(() -> false);
            panel.installSampleTradeRowsForTest();
            JTable trade = panel.tradeTableForTest();

            int expectedY = 0;
            for (int row = 0; row < trade.getRowCount(); row++) {
                Rectangle r = trade.getCellRect(row, 0, true);
                assertEquals(expectedY, r.y,
                        "row " + row + " must start where the previous rows end");
                assertEquals(trade.getRowHeight(row), r.height,
                        "row " + row + " height must match the row model");
                expectedY += trade.getRowHeight(row);
            }
        });
    }

    @Test
    void sectionRowsAreTallerThanDataRowsAndGapsAreShorter() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            EngineeringTabPanel panel = new EngineeringTabPanel(() -> false);
            panel.installSampleTradeRowsForTest();
            JTable trade = panel.tradeTableForTest();

            int sectionH = trade.getRowHeight(0);
            int dataH = trade.getRowHeight(2);
            int gapH = trade.getRowHeight(3);

            assertTrue(sectionH > dataH,
                    "section titles need extra height so bold text is not clipped");
            assertTrue(gapH < dataH, "gap rows are spacers and must be shorter than data rows");
        });
    }

    /** The Encoded section outline must not paint over the column titles that follow it. */
    @Test
    void encodedSectionOutlineDoesNotOverlapItsColumnTitles() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            EngineeringTabPanel panel = new EngineeringTabPanel(() -> false);
            panel.installSampleTradeRowsForTest();
            JTable trade = panel.tradeTableForTest();

            int encodedRow = 4;
            int columnTitlesRow = 5;
            Rectangle encoded = trade.getCellRect(encodedRow, 0, true);
            Rectangle columnTitles = trade.getCellRect(columnTitlesRow, 0, true);

            assertEquals(encoded.y + encoded.height, columnTitles.y,
                    "column titles must begin exactly where the section outline ends");
        });
    }

    @Test
    void rowAtPointResolvesRowsAfterATallSection() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            EngineeringTabPanel panel = new EngineeringTabPanel(() -> false);
            panel.installSampleTradeRowsForTest();
            JTable trade = panel.tradeTableForTest();

            for (int row = 0; row < trade.getRowCount(); row++) {
                Rectangle r = trade.getCellRect(row, 0, true);
                if (r.height <= 0) {
                    continue;
                }
                int mid = r.y + r.height / 2;
                assertEquals(row, trade.rowAtPoint(new java.awt.Point(2, mid)),
                        "a click inside row " + row + " must resolve to that row");
            }
        });
    }
}
