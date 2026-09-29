package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;

import javax.swing.JFrame;
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

    /**
     * A tall engineering dialog must give the spare height to Trade Suggestions. Pinning the
     * list to its content height left a scrollbar that dragging the Goals separator could not clear.
     */
    @Test
    void collapsedTradeSectionFillsSpareDialogHeightWithoutAScrollbar() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a window for real split-pane layout");
        SwingUtilities.invokeAndWait(() -> {
            EngineeringTabPanel panel = new EngineeringTabPanel(() -> false);
            panel.installSampleTradeRowsForTest();
            JFrame frame = panel.layoutCollapsedTradeSectionForTest(900, 800);
            try {
                int paneH = panel.collapsedTradePaneHeightForTest();
                int max = panel.collapsedLowerSplitMaxDividerForTest();
                assertTrue(paneH >= max - 2,
                        "trade section should fill down to the Show button, pane=" + paneH + " max=" + max);

                JTable trade = panel.tradeTableForTest();
                int rowsH = trade.getPreferredSize().height;
                int viewportH = panel.tradeViewportHeightForTest();
                assertTrue(viewportH > 140,
                        "viewport must grow past the old 140px preferred height, was " + viewportH);
                assertTrue(viewportH >= rowsH,
                        "viewport " + viewportH + " must cover row stack " + rowsH);
                assertFalse(panel.tradeVerticalBarVisibleForTest(),
                        "a short trade list in a tall dialog must not show a vertical scrollbar");
            } finally {
                panel.disposeCollapsedTradeLayoutForTest(frame);
            }
        });
    }

    /** Hybrid mode: rows take clicks; the empty space under a short list passes through to the game. */
    @Test
    void emptySpaceUnderShortTradeListIsNotInteractive() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a window to measure screen positions");
        SwingUtilities.invokeAndWait(() -> {
            EngineeringTabPanel panel = new EngineeringTabPanel(() -> false);
            panel.installSampleTradeRowsForTest();
            JFrame frame = panel.layoutCollapsedTradeSectionForTest(900, 800);
            try {
                JTable trade = panel.tradeTableForTest();
                Rectangle dataRow = trade.getCellRect(2, 0, true);
                Point onRow = new Point(dataRow.x + 5, dataRow.y + dataRow.height / 2);
                SwingUtilities.convertPointToScreen(onRow, trade);
                assertTrue(panel.isPointerOverInteractiveRegion(onRow), "a trade row takes clicks");

                Rectangle last = trade.getCellRect(trade.getRowCount() - 1, 0, true);
                int rowsBottom = last.y + last.height;
                assertTrue(panel.tradeViewportHeightForTest() > rowsBottom + 40,
                        "the viewport extends below the last row in this layout");
                Point belowRows = new Point(5, rowsBottom + 40);
                SwingUtilities.convertPointToScreen(belowRows, trade);
                assertFalse(panel.isPointerOverInteractiveRegion(belowRows),
                        "empty space under the rows must pass clicks through");
            } finally {
                panel.disposeCollapsedTradeLayoutForTest(frame);
            }
        });
    }
}
